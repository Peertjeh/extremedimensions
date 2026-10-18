package net.battlehub.games.duel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.battlehub.core.chaos.ChaosCatalog;
import net.battlehub.core.chaos.ChaosEvent;
import net.battlehub.core.chaos.ChaosPlan;
import net.battlehub.games.bedwars.BuildRules;
import net.battlehub.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;

/**
 * Runs the events of one Chaos match: plans them at the start ({@link ChaosPlan}), starts one every few seconds,
 * applies its actions to everyone in play and takes everything back when it ends (attribute changes, glass
 * bridges, chickens, elytra). A goal ends every running event. Effects and attribute changes of running events
 * are given again to a player who comes back after dying.
 */
public final class ChaosDirector {
	private static final String MODIFIER = "chaos_";
	private static final Set<String> INSTANT = Set.of("minecraft:instant_health", "minecraft:instant_damage", "minecraft:saturation");

	private final BridgeChaosMatch match;
	private final ChaosPlan plan;
	private final List<Active> active = new ArrayList<>();
	/** Ticks of play (the chaos clock); events end on this clock. */
	private int clock;
	private int goalMultiplier = 1;
	private int noBuildUntil = -1;
	private int noPvpUntil = -1;

	/** A running event and what it left in the world. */
	private static final class Active {
		final ChaosEvent event;
		final int end;
		final List<Entity> entities = new ArrayList<>();
		final List<BlockPos> blocks = new ArrayList<>();
		final Map<UUID, ItemStack> chest = new HashMap<>();

		Active(ChaosEvent event, int end) {
			this.event = event;
			this.end = end;
		}
	}

	ChaosDirector(BridgeChaosMatch match, long seed, int randomCount) {
		this.match = match;
		this.plan = ChaosPlan.create(seed, randomCount);
	}

	public ChaosPlan plan() {
		return plan;
	}

	/** How many events are running now. */
	public int running() {
		return active.size();
	}

	int goalMultiplier() {
		return goalMultiplier;
	}

	boolean noBuild() {
		return clock < noBuildUntil;
	}

	boolean noPvp() {
		return clock < noPvpUntil;
	}

	/** The next planned event. */
	ChaosEvent next() {
		return ChaosCatalog.get(plan.next());
	}

	/** One tick of play: events with something to do every tick, and events that are over. */
	void tick() {
		clock++;
		for (Active a : List.copyOf(active)) {
			during(a);
			if (clock >= a.end) {
				finish(a);
				active.remove(a);
			}
		}
	}

	// ------------------------------------------------------------------ starting an event

	/** Starts an event for everyone in play. */
	void start(ChaosEvent e) {
		Active a = new Active(e, clock + Math.max(1, e.ticks()));
		active.add(a);
		for (ServerPlayer p : match.inPlay()) {
			int n = 0;
			for (ChaosEvent.Action act : e.actions()) {
				switch (act.verb()) {
					case "effect" -> effect(p, act, e.ticks());
					case "attr" -> attribute(p, e, act, n++);
					case "give" -> give(p, act);
					case "potion" -> potion(p, act);
					default -> { }
				}
			}
			p.inventoryMenu.sendAllDataToRemote();
		}
		for (ChaosEvent.Action act : e.actions()) {
			if (act.verb().equals("do")) special(a, act.arg(0));
		}
	}

	private static void effect(ServerPlayer p, ChaosEvent.Action act, int eventTicks) {
		int ticks = act.args().size() > 2 ? Integer.parseInt(act.arg(2)) * 20 : eventTicks;
		int amp = Integer.parseInt(act.arg(1));
		boolean instant = INSTANT.contains(act.arg(0));
		effectHolder(act.arg(0)).ifPresent(h -> p.addEffect(new MobEffectInstance(h, instant ? 1 : Math.max(1, ticks), amp, false, true, true)));
	}

	private static void attribute(ServerPlayer p, ChaosEvent e, ChaosEvent.Action act, int n) {
		AttributeInstance inst = instance(p, act.arg(0));
		if (inst == null) return;
		AttributeModifier.Operation op = switch (act.arg(1)) {
			case "base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
			case "total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
			default -> AttributeModifier.Operation.ADD_VALUE;
		};
		inst.addOrUpdateTransientModifier(new AttributeModifier(modifierId(e, n), Double.parseDouble(act.arg(2)), op));
		if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
	}

	private void give(ServerPlayer p, ChaosEvent.Action act) {
		ItemStack stack = new ItemStack(Ids.item(act.arg(0)), Integer.parseInt(act.arg(1)));
		if (act.args().size() > 3) {
			int level = Integer.parseInt(act.arg(3));
			Ids.enchantment(match.srv(), act.arg(2)).ifPresent(h -> stack.enchant(h, level));
		}
		p.getInventory().add(stack);
	}

	private static void potion(ServerPlayer p, ChaosEvent.Action act) {
		Item item = switch (act.arg(0)) {
			case "splash" -> Items.SPLASH_POTION;
			case "lingering" -> Items.LINGERING_POTION;
			default -> Items.POTION;
		};
		Optional<Holder<Potion>> potion = potionHolder(act.arg(1));
		if (potion.isEmpty()) return;
		for (int i = 0; i < Integer.parseInt(act.arg(2)); i++) {
			p.getInventory().add(PotionContents.createItemStack(item, potion.get()));
		}
	}

	// ------------------------------------------------------------------ specials

	/** True for every special this director knows (the self-test checks the catalogue against it). */
	public static boolean knows(String special) {
		return switch (special) {
			case "crumble", "webs", "double_points", "triple_points", "lucky", "elytra", "launch", "swap", "magnet", "shockwave",
					"glass_bridge", "ice_bridge", "snowstorm", "arrow_rain", "erode", "bombs", "lightning", "no_build", "no_pvp",
					"heal", "home", "restock", "danger", "fireworks", "chickens", "shuffle_hotbar", "ignite" -> true;
			default -> false;
		};
	}

	private void special(Active a, String what) {
		ServerLevel level = match.level();
		BuildRules build = match.buildRules();
		List<ServerPlayer> players = match.inPlay();
		switch (what) {
			case "crumble" -> {
				// Everything built over the middle of the gap falls apart
				if (build != null) build.crumble(p -> match.fromMiddle(p) <= 7);
			}
			case "webs" -> {
				if (build == null) break;
				List<BlockPos> tops = new ArrayList<>();
				for (BlockPos p : build.placedPositions()) {
					BlockPos up = p.above();
					if (level.getBlockState(up).isAir() && build.mayPlace(up) == BuildRules.Verdict.OK) tops.add(up);
				}
				for (int i = 0; i < 6 && !tops.isEmpty(); i++) {
					BlockPos at = tops.remove(match.rng().nextInt(tops.size()));
					level.setBlock(at, Ids.state("minecraft:cobweb"), 3);
					build.markPlaced(at);
				}
			}
			case "double_points" -> goalMultiplier = Math.max(goalMultiplier, 2);
			case "triple_points" -> goalMultiplier = 3;
			case "no_build" -> noBuildUntil = a.end;
			case "no_pvp" -> noPvpUntil = a.end;
			case "lucky" -> {
				String[] goodies = {"minecraft:golden_apple 3", "minecraft:ender_pearl 2", "minecraft:wind_charge 4", "minecraft:totem_of_undying 1",
						"minecraft:slime_block 16", "minecraft:enchanted_golden_apple 1", "minecraft:cobweb 4", "minecraft:snowball 16",
						"minecraft:mace 1", "minecraft:trident 1"};
				for (ServerPlayer p : players) {
					give(p, ChaosEvent.Action.parse("give " + goodies[match.rng().nextInt(goodies.length)]));
					p.inventoryMenu.sendAllDataToRemote();
				}
			}
			case "elytra" -> {
				for (ServerPlayer p : players) {
					a.chest.put(p.getUUID(), p.getItemBySlot(EquipmentSlot.CHEST).copy());
					p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
				}
			}
			case "launch" -> {
				for (ServerPlayer p : players) push(p, new Vec3(p.getDeltaMovement().x, 1.25, p.getDeltaMovement().z));
			}
			case "magnet", "shockwave" -> {
				Vec3 mid = Vec3.atBottomCenterOf(match.middle());
				for (ServerPlayer p : players) {
					Vec3 d = new Vec3(mid.x - p.getX(), 0, mid.z - p.getZ());
					d = d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
					push(p, what.equals("magnet") ? d.scale(1.1).add(0, 0.4, 0) : d.scale(-1.0).add(0, 0.5, 0));
				}
				level.sendParticles(ParticleTypes.EXPLOSION, mid.x, mid.y + 1, mid.z, 3, 1, 0.5, 1, 0);
			}
			case "swap" -> {
				if (players.size() < 2) break;
				List<Vec3> spots = new ArrayList<>();
				List<float[]> looks = new ArrayList<>();
				for (ServerPlayer p : players) {
					spots.add(p.position());
					looks.add(new float[] {p.getYRot(), p.getXRot()});
				}
				for (int i = 0; i < players.size(); i++) {
					int j = (i + 1) % players.size();
					ServerPlayer p = players.get(i);
					p.teleportTo(level, spots.get(j).x, spots.get(j).y, spots.get(j).z, Set.of(), looks.get(j)[0], looks.get(j)[1], true);
					p.resetFallDistance();
				}
			}
			case "glass_bridge", "ice_bridge" -> {
				if (build == null) break;
				String block = what.equals("glass_bridge") ? "minecraft:glass" : "minecraft:packed_ice";
				for (BlockPos p : match.bridgeLine()) {
					if (!level.getBlockState(p).isAir() || build.mayPlace(p) != BuildRules.Verdict.OK) continue;
					level.setBlock(p, Ids.state(block), 3);
					build.markPlaced(p);
					if (what.equals("glass_bridge")) a.blocks.add(p);
				}
			}
			case "erode" -> {
				if (build != null) build.crumble(p -> match.rng().nextInt(3) == 0);
			}
			case "bombs" -> {
				List<BlockPos> spots = build == null ? new ArrayList<>() : build.placedPositions();
				if (spots.isEmpty()) spots = new ArrayList<>(match.bridgeLine());
				for (int i = 0; i < 3 && !spots.isEmpty(); i++) {
					BlockPos c = spots.remove(match.rng().nextInt(spots.size()));
					if (build != null) build.explode(c, 2.5);
					level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 1, 0, 0, 0, 0);
					Vec3 cv = Vec3.atCenterOf(c);
					for (ServerPlayer p : players) {
						Vec3 d = p.position().subtract(cv);
						if (d.lengthSqr() > 25) continue;
						d = d.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : d.normalize();
						push(p, d.scale(0.9).add(0, 0.45, 0));
					}
				}
				match.everyone(o -> net.battlehub.util.Msg.sound(o, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.1f));
			}
			case "heal" -> {
				for (ServerPlayer p : players) {
					p.setHealth(p.getMaxHealth());
					p.clearFire();
				}
			}
			case "danger" -> {
				for (ServerPlayer p : players) p.setHealth(Math.min(p.getHealth(), 6f));
			}
			case "home" -> {
				for (ServerPlayer p : players) match.sendHome(p);
			}
			case "restock" -> {
				for (ServerPlayer p : players) match.restock(p);
			}
			case "fireworks" -> {
				for (ServerPlayer p : players) {
					for (int i = 0; i < 4; i++) {
						double x = p.getX() + match.rng().nextInt(9) - 4, z = p.getZ() + match.rng().nextInt(9) - 4;
						level.sendParticles(ParticleTypes.FIREWORK, x, p.getY() + 6 + match.rng().nextInt(4), z, 40, 0.4, 0.4, 0.4, 0.15);
					}
				}
				match.everyone(o -> net.battlehub.util.Msg.sound(o, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.8f, 1f));
			}
			case "chickens" -> {
				for (ServerPlayer p : players) {
					for (int i = 0; i < 3; i++) {
						Entity c = EntityType.CHICKEN.create(level, EntitySpawnReason.COMMAND);
						if (c == null) continue;
						c.snapTo(p.getX() + match.rng().nextInt(7) - 3, p.getY() + 8 + i * 2, p.getZ() + match.rng().nextInt(7) - 3);
						level.addFreshEntity(c);
						a.entities.add(c);
					}
				}
			}
			case "shuffle_hotbar" -> {
				for (ServerPlayer p : players) {
					List<ItemStack> bar = new ArrayList<>();
					for (int i = 0; i < 9; i++) bar.add(p.getInventory().getItem(i));
					for (int i = bar.size() - 1; i > 0; i--) {
						int j = match.rng().nextInt(i + 1);
						ItemStack t = bar.get(i);
						bar.set(i, bar.get(j));
						bar.set(j, t);
					}
					for (int i = 0; i < 9; i++) p.getInventory().setItem(i, bar.get(i));
					p.inventoryMenu.sendAllDataToRemote();
				}
			}
			case "ignite" -> {
				for (ServerPlayer p : players) p.setRemainingFireTicks(60);
			}
			// snowstorm, arrow_rain and lightning work every few ticks (during)
			default -> { }
		}
	}

	/** Events that do something all the time they run. */
	private void during(Active a) {
		int age = clock - (a.end - Math.max(1, a.event.ticks()));
		for (ChaosEvent.Action act : a.event.actions()) {
			if (!act.verb().equals("do")) continue;
			switch (act.arg(0)) {
				case "snowstorm" -> {
					if (age % 3 == 0) rain(a, EntityType.SNOWBALL, 2, -0.6);
				}
				case "arrow_rain" -> {
					if (age % 4 == 0) rain(a, EntityType.ARROW, 2, -1.4);
				}
				case "lightning" -> {
					if (age % 25 != 0) break;
					ServerLevel level = match.level();
					for (ServerPlayer p : match.inPlay()) {
						LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.COMMAND);
						if (bolt == null) continue;
						double ang = match.rng().nextDouble() * Math.PI * 2, r = 3 + match.rng().nextInt(4);
						bolt.setVisualOnly(true);
						bolt.snapTo(p.getX() + Math.cos(ang) * r, p.getY(), p.getZ() + Math.sin(ang) * r);
						level.addFreshEntity(bolt);
					}
				}
				default -> { }
			}
		}
	}

	/** Projectiles falling from the sky over the bridge. */
	private void rain(Active a, EntityType<?> type, int count, double speed) {
		ServerLevel level = match.level();
		List<BlockPos> line = match.bridgeLine();
		if (line.isEmpty()) return;
		for (int i = 0; i < count; i++) {
			BlockPos base = line.get(match.rng().nextInt(line.size()));
			Entity e = type.create(level, EntitySpawnReason.COMMAND);
			if (e == null) continue;
			e.snapTo(base.getX() + 0.5 + match.rng().nextInt(7) - 3, base.getY() + 16, base.getZ() + 0.5 + match.rng().nextInt(7) - 3);
			e.setDeltaMovement(0, speed, 0);
			level.addFreshEntity(e);
			a.entities.add(e);
		}
	}

	// ------------------------------------------------------------------ ending

	private void finish(Active a) {
		for (ServerPlayer p : match.inPlay()) {
			removeModifiers(p, a.event);
			ItemStack chest = a.chest.get(p.getUUID());
			if (chest != null && p.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
				p.setItemSlot(EquipmentSlot.CHEST, chest);
			}
		}
		BuildRules build = match.buildRules();
		if (build != null && !a.blocks.isEmpty()) {
			// the glass bridge shatters
			Set<Long> mine = new java.util.HashSet<>();
			for (BlockPos p : a.blocks) mine.add(p.asLong());
			build.crumble(p -> mine.contains(p.asLong()) && match.level().getBlockState(p).is(net.minecraft.world.level.block.Blocks.GLASS));
		}
		for (Entity e : a.entities) {
			if (!e.isRemoved()) e.discard();
		}
	}

	/** Ends everything that runs (a goal, the end of the match); {@code rules}: also forget a pending double goal. */
	public void finishAll(boolean rules) {
		for (Active a : active) finish(a);
		active.clear();
		noBuildUntil = -1;
		noPvpUntil = -1;
		if (rules) goalMultiplier = 1;
	}

	/** A goal was scored: the next one counts normally again, and a new point starts clean. */
	void goalScored() {
		goalMultiplier = 1;
		finishAll(false);
	}

	/** A player came back after dying: the effects and body changes of running events again, for what is left. */
	void reapply(ServerPlayer p) {
		for (Active a : active) {
			int left = a.end - clock;
			if (left <= 0) continue;
			int n = 0;
			for (ChaosEvent.Action act : a.event.actions()) {
				if (act.verb().equals("attr")) {
					attribute(p, a.event, act, n++);
				} else if (act.verb().equals("effect") && act.args().size() == 2) {
					effect(p, act, left);
				}
			}
		}
	}

	// ------------------------------------------------------------------ helpers

	private static void push(ServerPlayer p, Vec3 v) {
		p.setDeltaMovement(v);
		p.connection.send(new ClientboundSetEntityMotionPacket(p));
		p.resetFallDistance();
	}

	private static Identifier modifierId(ChaosEvent e, int n) {
		return Ids.id("battlehub:" + MODIFIER + e.id() + "_" + n);
	}

	private static void removeModifiers(ServerPlayer p, ChaosEvent e) {
		int n = 0;
		for (ChaosEvent.Action act : e.actions()) {
			if (!act.verb().equals("attr")) continue;
			AttributeInstance inst = instance(p, act.arg(0));
			if (inst != null) inst.removeModifier(modifierId(e, n));
			n++;
		}
		if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
	}

	/** Takes back every chaos change to this player's body (end of the match, leaving, a new point). */
	public static void strip(ServerPlayer p) {
		for (String id : ChaosCatalog.attributes()) {
			AttributeInstance inst = instance(p, id);
			if (inst == null) continue;
			for (AttributeModifier m : List.copyOf(inst.getModifiers())) {
				if (m.id().getNamespace().equals("battlehub") && m.id().getPath().startsWith(MODIFIER)) inst.removeModifier(m.id());
			}
		}
		if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
	}

	/** Does this player carry any chaos change? (self-test) */
	public static boolean changed(ServerPlayer p) {
		for (String id : ChaosCatalog.attributes()) {
			AttributeInstance inst = instance(p, id);
			if (inst == null) continue;
			for (AttributeModifier m : inst.getModifiers()) {
				if (m.id().getNamespace().equals("battlehub") && m.id().getPath().startsWith(MODIFIER)) return true;
			}
		}
		return false;
	}

	private static AttributeInstance instance(ServerPlayer p, String id) {
		Optional<Holder<Attribute>> h = attributeHolder(id);
		return h.map(p::getAttribute).orElse(null);
	}

	static Optional<Holder<Attribute>> attributeHolder(String id) {
		return BuiltInRegistries.ATTRIBUTE.get(Ids.id(id)).map(r -> (Holder<Attribute>) r);
	}

	static Optional<Holder<MobEffect>> effectHolder(String id) {
		return Ids.effect(id);
	}

	static Optional<Holder<Potion>> potionHolder(String id) {
		return BuiltInRegistries.POTION.get(Ids.id(id)).map(r -> (Holder<Potion>) r);
	}

	/** What in the catalogue does not exist in this game (self-test): unknown effects, attributes, items, potions, specials. */
	public static List<String> unknown(net.minecraft.server.MinecraftServer server) {
		List<String> out = new ArrayList<>();
		for (ChaosEvent e : ChaosCatalog.all()) {
			for (ChaosEvent.Action act : e.actions()) {
				String id = act.arg(0);
				boolean ok = switch (act.verb()) {
					case "effect" -> effectHolder(id).isPresent();
					case "attr" -> attributeHolder(id).isPresent();
					case "give" -> Ids.isItem(id) && (act.args().size() < 4 || Ids.enchantment(server, act.arg(2)).isPresent());
					case "potion" -> potionHolder(act.arg(1)).isPresent();
					default -> knows(id);
				};
				if (!ok) out.add(e.id() + ": " + act.verb() + " " + String.join(" ", act.args()));
			}
		}
		return out;
	}
}
