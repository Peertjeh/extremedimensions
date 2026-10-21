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
import net.battlehub.core.chaos.ChaosIntensity;
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
 * bridges, chickens, elytra). Once the plan is done the events come again, worse each time round
 * ({@link ChaosIntensity}). A goal ends every running event. Effects and attribute changes of running events are
 * given again to a player who comes back after dying.
 */
public final class ChaosDirector {
	private static final String MODIFIER = "chaos_";
	private static final Set<String> INSTANT = Set.of("minecraft:instant_health", "minecraft:instant_damage", "minecraft:saturation");

	private final ChaosHost match;
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
		final int level;
		final int ticks;
		final int end;
		final List<Entity> entities = new ArrayList<>();
		final List<BlockPos> blocks = new ArrayList<>();
		final Map<UUID, ItemStack> chest = new HashMap<>();

		Active(ChaosEvent event, int level, int ticks, int end) {
			this.event = event;
			this.level = level;
			this.ticks = ticks;
			this.end = end;
		}
	}

	public ChaosDirector(ChaosHost match, long seed, int randomCount) {
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

	public int goalMultiplier() {
		return goalMultiplier;
	}

	public boolean noBuild() {
		return clock < noBuildUntil;
	}

	public boolean noPvp() {
		return clock < noPvpUntil;
	}

	/** The next planned event and how bad it is this time round. */
	public ChaosPlan.Turn next() {
		return plan.nextTurn();
	}

	/** One tick of play: events with something to do every tick, and events that are over. */
	public void tick() {
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

	/** Starts an event for everyone in play, as written. */
	public void start(ChaosEvent e) {
		start(e, 0);
	}

	/** Starts an event for everyone in play, {@code level} times round worse (0: as written). */
	public void start(ChaosEvent e, int level) {
		int ticks = ChaosIntensity.seconds(e.seconds(), level) * 20;
		Active a = new Active(e, level, ticks, clock + Math.max(1, ticks));
		active.add(a);
		for (ServerPlayer p : match.inPlay()) {
			int n = 0;
			for (ChaosEvent.Action act : e.actions()) {
				switch (act.verb()) {
					case "effect" -> effect(p, act, ticks, level);
					case "attr" -> attribute(p, e, act, n++, level);
					case "give" -> give(p, act, level);
					case "potion" -> potion(p, act, level);
					default -> { }
				}
			}
			p.inventoryMenu.sendAllDataToRemote();
		}
		for (ChaosEvent.Action act : e.actions()) {
			if (act.verb().equals("do")) special(a, act.arg(0));
		}
	}

	private static void effect(ServerPlayer p, ChaosEvent.Action act, int eventTicks, int level) {
		int ticks = act.args().size() > 2 ? ChaosIntensity.seconds(Integer.parseInt(act.arg(2)), level) * 20 : eventTicks;
		int amp = ChaosIntensity.amplifier(Integer.parseInt(act.arg(1)), level);
		boolean instant = INSTANT.contains(act.arg(0));
		effectHolder(act.arg(0)).ifPresent(h -> p.addEffect(new MobEffectInstance(h, instant ? 1 : Math.max(1, ticks), amp, false, true, true)));
	}

	private static void attribute(ServerPlayer p, ChaosEvent e, ChaosEvent.Action act, int n, int level) {
		AttributeInstance inst = instance(p, act.arg(0));
		if (inst == null) return;
		AttributeModifier.Operation op = switch (act.arg(1)) {
			case "base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
			case "total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
			default -> AttributeModifier.Operation.ADD_VALUE;
		};
		double amount = ChaosIntensity.attribute(act.arg(0), act.arg(1), Double.parseDouble(act.arg(2)), level);
		inst.addOrUpdateTransientModifier(new AttributeModifier(modifierId(e, n), amount, op));
		if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
	}

	private void give(ServerPlayer p, ChaosEvent.Action act, int level) {
		Item item = Ids.item(act.arg(0));
		int left = ChaosIntensity.count(Integer.parseInt(act.arg(1)), level, 64);
		// in stacks: twice a totem is two totems, not one stack of two
		while (left > 0) {
			ItemStack stack = new ItemStack(item, Math.min(left, Math.max(1, item.getDefaultMaxStackSize())));
			left -= stack.getCount();
			if (act.args().size() > 3) {
				int enchant = ChaosIntensity.enchantment(Integer.parseInt(act.arg(3)), level);
				Ids.enchantment(match.srv(), act.arg(2)).ifPresent(h -> stack.enchant(h, enchant));
			}
			if (!p.getInventory().add(stack)) break;
		}
	}

	private static void potion(ServerPlayer p, ChaosEvent.Action act, int level) {
		Item item = switch (act.arg(0)) {
			case "splash" -> Items.SPLASH_POTION;
			case "lingering" -> Items.LINGERING_POTION;
			default -> Items.POTION;
		};
		Optional<Holder<Potion>> potion = potionHolder(act.arg(1));
		if (potion.isEmpty()) return;
		for (int i = 0; i < ChaosIntensity.count(Integer.parseInt(act.arg(2)), level, 8); i++) {
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
		int lv = a.level;
		switch (what) {
			case "crumble" -> {
				// Everything built over the middle of the gap falls apart (a wider middle each time round)
				int reach = Math.min(40, 7 * ChaosIntensity.factor(lv));
				if (build != null) build.crumble(p -> match.fromMiddle(p) <= reach);
			}
			case "webs" -> {
				if (build == null) break;
				List<BlockPos> tops = new ArrayList<>();
				for (BlockPos p : build.placedPositions()) {
					BlockPos up = p.above();
					if (level.getBlockState(up).isAir() && build.mayPlace(up) == BuildRules.Verdict.OK) tops.add(up);
				}
				for (int i = 0; i < ChaosIntensity.times(6, lv, 48) && !tops.isEmpty(); i++) {
					BlockPos at = tops.remove(match.rng().nextInt(tops.size()));
					level.setBlock(at, Ids.state("minecraft:cobweb"), 3);
					build.markPlaced(at);
				}
			}
			case "double_points" -> goalMultiplier = Math.max(goalMultiplier, Math.min(16, 2 * ChaosIntensity.factor(lv)));
			case "triple_points" -> goalMultiplier = Math.max(goalMultiplier, Math.min(24, 3 * ChaosIntensity.factor(lv)));
			case "no_build" -> noBuildUntil = a.end;
			case "no_pvp" -> noPvpUntil = a.end;
			case "lucky" -> {
				String[] goodies = {"minecraft:golden_apple 3", "minecraft:ender_pearl 2", "minecraft:wind_charge 4", "minecraft:totem_of_undying 1",
						"minecraft:slime_block 16", "minecraft:enchanted_golden_apple 1", "minecraft:cobweb 4", "minecraft:snowball 16",
						"minecraft:mace 1", "minecraft:trident 1"};
				for (ServerPlayer p : players) {
					for (int g = 0; g < ChaosIntensity.times(1, lv, 8); g++) {
						give(p, ChaosEvent.Action.parse("give " + goodies[match.rng().nextInt(goodies.length)]), 0);
					}
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
				for (ServerPlayer p : players) push(p, new Vec3(p.getDeltaMovement().x, ChaosIntensity.push(1.25, lv, 3), p.getDeltaMovement().z));
			}
			case "magnet", "shockwave" -> {
				Vec3 mid = Vec3.atBottomCenterOf(match.middle());
				for (ServerPlayer p : players) {
					Vec3 d = new Vec3(mid.x - p.getX(), 0, mid.z - p.getZ());
					d = d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
					double k = ChaosIntensity.push(1, lv, 2.5);
					push(p, what.equals("magnet") ? d.scale(1.1 * k).add(0, 0.4 * k, 0) : d.scale(-1.0 * k).add(0, 0.5 * k, 0));
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
				double chance = ChaosIntensity.chance(1 / 3.0, lv);
				if (build != null) build.crumble(p -> match.rng().nextDouble() < chance);
			}
			case "bombs" -> {
				List<BlockPos> spots = build == null ? new ArrayList<>() : build.placedPositions();
				if (spots.isEmpty()) spots = new ArrayList<>(match.bridgeLine());
				double radius = Math.min(5, 2.5 + 0.5 * lv), blast = ChaosIntensity.push(0.9, lv, 2.5);
				for (int i = 0; i < ChaosIntensity.times(3, lv, 24) && !spots.isEmpty(); i++) {
					BlockPos c = spots.remove(match.rng().nextInt(spots.size()));
					if (build != null) build.explode(c, radius);
					level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 1, 0, 0, 0, 0);
					Vec3 cv = Vec3.atCenterOf(c);
					for (ServerPlayer p : players) {
						Vec3 d = p.position().subtract(cv);
						if (d.lengthSqr() > 25) continue;
						d = d.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : d.normalize();
						push(p, d.scale(blast).add(0, blast / 2, 0));
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
				float left = Math.max(1f, 6f / ChaosIntensity.factor(lv));
				for (ServerPlayer p : players) p.setHealth(Math.min(p.getHealth(), left));
			}
			case "home" -> {
				for (ServerPlayer p : players) match.sendHome(p);
			}
			case "restock" -> {
				for (ServerPlayer p : players) match.restock(p);
			}
			case "fireworks" -> {
				for (ServerPlayer p : players) {
					for (int i = 0; i < ChaosIntensity.times(4, lv, 32); i++) {
						double x = p.getX() + match.rng().nextInt(9) - 4, z = p.getZ() + match.rng().nextInt(9) - 4;
						level.sendParticles(ParticleTypes.FIREWORK, x, p.getY() + 6 + match.rng().nextInt(4), z, 40, 0.4, 0.4, 0.4, 0.15);
					}
				}
				match.everyone(o -> net.battlehub.util.Msg.sound(o, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.8f, 1f));
			}
			case "chickens" -> {
				for (ServerPlayer p : players) {
					for (int i = 0; i < ChaosIntensity.times(3, lv, 24); i++) {
						Entity c = type("minecraft:chicken").create(level, EntitySpawnReason.COMMAND);
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
				int fire = Math.min(400, 60 * ChaosIntensity.factor(lv));
				for (ServerPlayer p : players) p.setRemainingFireTicks(fire);
			}
			// snowstorm, arrow_rain and lightning work every few ticks (during)
			default -> { }
		}
	}

	/** Events that do something all the time they run. */
	private void during(Active a) {
		int age = clock - (a.end - Math.max(1, a.ticks));
		int lv = a.level;
		for (ChaosEvent.Action act : a.event.actions()) {
			if (!act.verb().equals("do")) continue;
			switch (act.arg(0)) {
				case "snowstorm" -> {
					if (age % 3 == 0) rain(a, type("minecraft:snowball"), ChaosIntensity.times(2, lv, 16), -0.6);
				}
				case "arrow_rain" -> {
					if (age % 4 == 0) rain(a, type("minecraft:arrow"), ChaosIntensity.times(2, lv, 16), -1.4);
				}
				case "lightning" -> {
					if (age % ChaosIntensity.every(25, lv, 5) != 0) break;
					ServerLevel level = match.level();
					for (ServerPlayer p : match.inPlay()) {
						if (!(type("minecraft:lightning_bolt").create(level, EntitySpawnReason.COMMAND) instanceof LightningBolt bolt)) continue;
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
	public void goalScored() {
		goalMultiplier = 1;
		finishAll(false);
	}

	/** A player came back after dying: the effects and body changes of running events again, for what is left. */
	public void reapply(ServerPlayer p) {
		for (Active a : active) {
			int left = a.end - clock;
			if (left <= 0) continue;
			int n = 0;
			for (ChaosEvent.Action act : a.event.actions()) {
				if (act.verb().equals("attr")) {
					attribute(p, a.event, act, n++, a.level);
				} else if (act.verb().equals("effect") && act.args().size() == 2) {
					effect(p, act, left, a.level);
				}
			}
		}
	}

	// ------------------------------------------------------------------ helpers

	/** Entity types by id (26.x has no constants for most of them). */
	private static EntityType<?> type(String id) {
		return net.battlehub.hub.ClientEntities.type(id);
	}

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
