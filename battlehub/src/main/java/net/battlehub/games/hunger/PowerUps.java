package net.battlehub.games.hunger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.lang.Lang;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;

/**
 * The power-ups of the Hunger Games: items with their own name and explanation, found in the better chests, at the
 * Feast, in crates and parachutes. Their mark is {@code battlehub_hg} in the custom data, not BattleHub's own item
 * tag, so they can be taken out of chests, moved and dropped like any item. Using one (right-click) does what it
 * says; the thrown ones (nightlock, the tracker jacker nest) are thrown as snowballs and act where they land; the
 * landmine is a pressure plate put down like a block.
 */
public final class PowerUps {
	public static final String TAG = "battlehub_hg";

	/** A power-up: its id, the item underneath, how it looks (an item model) and its colour. */
	public record Kind(String id, Item base, String model, String color) {}

	public static final Map<String, Kind> KINDS = new LinkedHashMap<>();

	static {
		add("salve", Items.HONEY_BOTTLE, "minecraft:honey_bottle", "&a");
		add("camo", Ids.item("minecraft:green_dye"), "minecraft:green_dye", "&2");
		add("nightlock", Items.SNOWBALL, "minecraft:sweet_berries", "&5");
		add("jackers", Items.SNOWBALL, "minecraft:bee_nest", "&e");
		add("mine", Items.STONE_PRESSURE_PLATE, "minecraft:stone_pressure_plate", "&c");
		add("parachute", Items.PHANTOM_MEMBRANE, "minecraft:phantom_membrane", "&f");
		add("tonic", Items.SUGAR, "minecraft:sugar", "&b");
		add("tracker", Items.COMPASS, "minecraft:compass", "&6");
		add("bread", Items.BREAD, "minecraft:bread", "&6");
		add("goggles", Items.SPYGLASS, "minecraft:spyglass", "&3");
		add("spear", Items.TRIDENT, "minecraft:trident", "&b");
		add("key", Items.TRIPWIRE_HOOK, "minecraft:tripwire_hook", "&e");
	}

	private static void add(String id, Item base, String model, String color) {
		KINDS.put(id, new Kind(id, base, model, color));
	}

	private final HungerHost host;
	/** Thrown power-ups in the air: the snowball and where it was last. */
	private final Map<Entity, Vec3> flying = new HashMap<>();
	private final Map<Entity, String> flyingKind = new HashMap<>();
	private final Map<Entity, UUID> flyingOwner = new HashMap<>();
	/** Landmines: where, and who put it down. */
	private final Map<BlockPos, UUID> mines = new LinkedHashMap<>();
	/** Tributes with a tracker running, until which tick. */
	private final Map<UUID, Integer> tracking = new HashMap<>();
	/** Swarms started by a thrown nest are run by the Gamemakers; this hands them over. */
	private final Gamemakers gamemakers;
	private int minesBlown, thrownLanded;

	PowerUps(HungerHost host, Gamemakers gamemakers) {
		this.host = host;
		this.gamemakers = gamemakers;
	}

	// ------------------------------------------------------------------ the items

	/** A stack of the power-up {@code id} (names in the server's language), or empty when the id is unknown. */
	public static ItemStack stack(net.minecraft.server.MinecraftServer server, String id, int count) {
		Kind k = KINDS.get(id);
		if (k == null) return ItemStack.EMPTY;
		ItemStack st = new ItemStack(k.base(), Math.max(1, Math.min(k.base().getDefaultMaxStackSize(), count)));
		CompoundTag t = new CompoundTag();
		t.putString(TAG, id);
		st.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
		st.set(DataComponents.ITEM_MODEL, Ids.id(k.model()));
		st.set(DataComponents.CUSTOM_NAME, Lang.colored(k.color()).append(Lang.tr("hg.power." + id)).withStyle(s -> s.withItalic(false)));
		List<Component> lore = new ArrayList<>();
		for (String line : Lang.plain("hg.power." + id + ".lore").split("\n")) {
			lore.add(Lang.colored("&7" + line).withStyle(s -> s.withItalic(false)));
		}
		st.set(DataComponents.LORE, new ItemLore(lore));
		if (!"key".equals(id) && !"spear".equals(id)) st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		if ("spear".equals(id) && server != null) {
			Ids.enchantment(server, "minecraft:loyalty").ifPresent(h -> st.enchant(h, 2));
		}
		return st;
	}

	/** The power-up id of a stack, or null. */
	public static String id(ItemStack st) {
		if (st == null || st.isEmpty()) return null;
		CustomData data = st.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) return null;
		String id = data.copyTag().getStringOr(TAG, "");
		return KINDS.containsKey(id) ? id : null;
	}

	public static boolean isKey(ItemStack st) {
		return "key".equals(id(st));
	}

	// ------------------------------------------------------------------ using one

	/**
	 * A right-click with {@code held}: true when it was a power-up that did its thing here (the item is used up). The
	 * landmine returns false: it is put down like a block, and acts later.
	 */
	public boolean use(ServerPlayer p, ItemStack held) {
		String id = id(held);
		if (id == null) return false;
		switch (id) {
			case "salve" -> {
				p.heal(4);
				effect(p, "minecraft:regeneration", 100, 2);
				p.removeEffect(Ids.effect("minecraft:poison").orElseThrow());
				p.removeEffect(Ids.effect("minecraft:wither").orElseThrow());
				p.clearFire();
				sound(p, SoundEvents.HONEY_DRINK);
			}
			case "camo" -> {
				effect(p, "minecraft:invisibility", 15 * 20, 0);
				sound(p, SoundEvents.BRUSH_GENERIC);
			}
			case "parachute" -> {
				effect(p, "minecraft:slow_falling", 10 * 20, 0);
				sound(p, SoundEvents.ARMOR_EQUIP_ELYTRA);
			}
			case "tonic" -> {
				effect(p, "minecraft:speed", 20 * 20, 1);
				sound(p, SoundEvents.GENERIC_DRINK);
			}
			case "tracker" -> {
				tracking.put(p.getUUID(), host.runTicks() + 60 * 20);
				sound(p, SoundEvents.LODESTONE_COMPASS_LOCK);
			}
			case "bread" -> {
				p.getFoodData().setFoodLevel(20);
				p.getFoodData().setSaturation(10);
				effect(p, "minecraft:regeneration", 6 * 20, 0);
				sound(p, SoundEvents.GENERIC_EAT);
			}
			case "goggles" -> {
				effect(p, "minecraft:night_vision", 120 * 20, 0);
				sound(p, SoundEvents.SPYGLASS_USE);
			}
			case "nightlock", "jackers" -> {
				if (!throwIt(p, id)) return false;
			}
			default -> {
				// the mine is put down, the spear is a trident, the key opens a vault
				return false;
			}
		}
		Msg.actionBar(p, "hg.power.used", Lang.tr(p, "hg.power." + id));
		held.shrink(1);
		return true;
	}

	/** Throws a nightlock or a nest as a snowball from {@code p}'s eyes; it acts where it lands. */
	private boolean throwIt(ServerPlayer p, String id) {
		ServerLevel level = host.level();
		Entity ball = net.battlehub.hub.ClientEntities.type("minecraft:snowball").create(level, EntitySpawnReason.COMMAND);
		if (ball == null) return false;
		Vec3 look = p.getLookAngle();
		Vec3 from = p.getEyePosition().add(look.scale(0.8));
		ball.snapTo(from.x, from.y, from.z);
		ball.setDeltaMovement(look.scale(1.5));
		level.addFreshEntity(ball);
		flying.put(ball, ball.position());
		flyingKind.put(ball, id);
		flyingOwner.put(ball, p.getUUID());
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6f, 0.5f);
		return true;
	}

	/** Thrown power-ups in the air now (self-test). */
	public int flying() {
		return flying.size();
	}

	private static void effect(ServerPlayer p, String id, int ticks, int amp) {
		Ids.effect(id).ifPresent(h -> p.addEffect(new MobEffectInstance(h, ticks, amp, false, true, true)));
	}

	private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent s) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, 0.8f, 1.0f);
	}

	private static void sound(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> s) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, 0.8f, 1.0f);
	}

	/** A landmine put down at {@code pos} by {@code owner}. */
	public void placed(BlockPos pos, UUID owner) {
		mines.put(pos.immutable(), owner);
	}

	public boolean isMine(BlockPos pos) {
		return mines.containsKey(pos);
	}

	/** A landmine was broken: it is gone. */
	public void broken(BlockPos pos) {
		mines.remove(pos);
	}

	// ------------------------------------------------------------------ every tick

	void tick() {
		ServerLevel level = host.level();
		// thrown power-ups: where they land they act
		for (Iterator<Map.Entry<Entity, Vec3>> it = flying.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Entity, Vec3> e = it.next();
			Entity s = e.getKey();
			if (!s.isRemoved()) {
				e.setValue(s.position());
				continue;
			}
			landed(flyingKind.remove(s), e.getValue(), flyingOwner.remove(s));
			it.remove();
		}
		// landmines: another tribute on it
		for (Iterator<Map.Entry<BlockPos, UUID>> it = mines.entrySet().iterator(); it.hasNext();) {
			Map.Entry<BlockPos, UUID> m = it.next();
			BlockPos pos = m.getKey();
			if (!level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.STONE_PRESSURE_PLATE)) {
				it.remove();
				continue;
			}
			for (ServerPlayer p : host.tributes()) {
				if (p.getUUID().equals(m.getValue())) continue;
				if (p.position().distanceToSqr(Vec3.atBottomCenterOf(pos)) > 1.3 * 1.3) continue;
				level.removeBlock(pos, false);
				blast(Vec3.atCenterOf(pos), 4.0, 14f, m.getValue());
				minesBlown++;
				it.remove();
				break;
			}
		}
		// trackers: an arrow to the nearest other tribute
		if (host.runTicks() % 5 == 0) {
			for (Iterator<Map.Entry<UUID, Integer>> it = tracking.entrySet().iterator(); it.hasNext();) {
				Map.Entry<UUID, Integer> t = it.next();
				ServerPlayer p = player(t.getKey());
				if (p == null || host.runTicks() > t.getValue()) {
					it.remove();
					continue;
				}
				pointer(p, "hg.tracker");
			}
		}
	}

	/** The arrow in the action bar to the nearest other tribute, with the distance. */
	void pointer(ServerPlayer p, String key) {
		ServerPlayer near = null;
		double best = Double.MAX_VALUE;
		for (ServerPlayer o : host.tributes()) {
			if (o == p) continue;
			double d = o.distanceTo(p);
			if (d < best) {
				best = d;
				near = o;
			}
		}
		if (near == null) return;
		double ang = Math.toDegrees(Math.atan2(near.getZ() - p.getZ(), near.getX() - p.getX())) - 90 - p.getYRot();
		ang = ((ang % 360) + 540) % 360 - 180;
		String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
		String arrow = arrows[(int) Math.round(((ang + 360) % 360) / 45.0) % 8];
		Msg.actionBar(p, key, arrow, (int) Math.round(best));
	}

	private ServerPlayer player(UUID id) {
		for (ServerPlayer p : host.tributes()) {
			if (p.getUUID().equals(id)) return p;
		}
		return null;
	}

	private void landed(String kind, Vec3 at, UUID owner) {
		if (kind == null || at == null) return;
		thrownLanded++;
		ServerLevel level = host.level();
		if ("nightlock".equals(kind)) {
			// a cloud of poison where it burst
			level.sendParticles(ParticleTypes.WITCH, at.x, at.y + 0.5, at.z, 40, 1.5, 0.6, 1.5, 0.02);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1f, 0.7f);
			gamemakers.cloud(at, 3.5, 8 * 20, owner);
		} else if ("jackers".equals(kind)) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BEEHIVE_SHEAR, SoundSource.PLAYERS, 1f, 0.8f);
			gamemakers.swarm(at, owner, 20 * 20);
		}
	}

	/** A blast that hurts and throws back tributes near {@code at} (the city takes no damage). */
	void blast(Vec3 at, double radius, float damage, UUID owner) {
		ServerLevel level = host.level();
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.2f, 0.9f);
		for (ServerPlayer p : host.tributes()) {
			double d = p.position().distanceTo(at);
			if (d > radius) continue;
			float dmg = (float) (damage * (1 - d / radius * 0.6));
			p.hurtServer(level, level.damageSources().explosion(null, null), dmg);
			Vec3 push = p.position().subtract(at).normalize().scale(0.9).add(0, 0.5, 0);
			p.setDeltaMovement(p.getDeltaMovement().add(push));
			p.connection.send(new ClientboundSetEntityMotionPacket(p));
		}
	}

	public int minesBlown() {
		return minesBlown;
	}

	public int thrownLanded() {
		return thrownLanded;
	}

	public boolean tracking(UUID id) {
		return tracking.containsKey(id);
	}

	void clear() {
		for (Entity s : flying.keySet()) Holo.remove(s);
		flying.clear();
		flyingKind.clear();
		flyingOwner.clear();
		mines.clear();
		tracking.clear();
	}
}
