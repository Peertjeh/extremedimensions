package com.extremedimensions.lurker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.network.Fx;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The Lurker's mind. Every Lurker is driven from here through a small state machine:
 *
 * <ul>
 *   <li><b>WATCH</b>: stands on high ground 24-40 blocks away and stares. Too close, or stared back at too long,
 *       and it is gone.</li>
 *   <li><b>STALK</b> (rare): creeps closer, but only while the player is not looking.</li>
 *   <li><b>FLEE</b>: after taking something it waits at a distance with the loot. Kill it in time to get it back.</li>
 *   <li><b>DRAIN</b>: holds a charged anchor's light in its arms and drinks the anchor dry.</li>
 * </ul>
 *
 * Lurkers are never saved: one that is not driven by this service (after a restart) dissolves.
 */
public final class LurkerService {
	private LurkerService() {}

	enum Mode { WATCH, STALK, FLEE, DRAIN }

	/** What a Lurker took and is carrying away. */
	static final class Loot {
		ItemStack stack = ItemStack.EMPTY;
		int levels = 0;
		UUID from;
	}

	static final class State {
		Mode mode;
		UUID target;
		int ticks;
		int limit;
		int stared;
		String anchor;
		final Loot loot = new Loot();
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Map<UUID, ServerLevel> LEVELS = new HashMap<>();

	public static boolean isDriven(Entity e) {
		return STATES.containsKey(e.getUUID());
	}

	public static void clear() {
		STATES.clear();
		LEVELS.clear();
	}

	// ------------------------------------------------------------------ spawning

	public static void tick(MinecraftServer s) {
		drive();
		if (Config.LURKER_ENABLED == 0 || s.getTickCount() % 400 != 0) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			String dim = Dims.of(p);
			if (p.isSpectator() || p.isCreative() || !(Dims.OVERWORLD.equals(dim) || Dims.NETHER.equals(dim)) || hunting(p)) {
				continue;
			}
			if (rnd.nextDouble() >= Config.LURKER_SPAWN_CHANCE) {
				continue;
			}
			if (Dims.OVERWORLD.equals(dim) && rnd.nextDouble() < Config.LURKER_DRAIN_CHANCE && spawnDrain(p)) {
				continue;
			}
			spawnWatch(p);
		}
	}

	private static boolean hunting(ServerPlayer p) {
		for (State st : STATES.values()) {
			if (p.getUUID().equals(st.target)) {
				return true;
			}
		}
		return false;
	}

	/** A Lurker appears on high ground at a distance, watching this player. */
	public static boolean spawnWatch(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		BlockPos pos = vantage(level, p, 24, 40);
		if (pos == null) {
			return false;
		}
		State st = new State();
		st.mode = Mode.WATCH;
		st.target = p.getUUID();
		st.limit = 20 * (Config.LURKER_WATCH_MIN + ThreadLocalRandom.current().nextInt(Math.max(1, Config.LURKER_WATCH_MAX - Config.LURKER_WATCH_MIN)));
		return spawn(level, pos, st) != null;
	}

	/** A Lurker appears beside a charged anchor near this player and starts drinking it. */
	public static boolean spawnDrain(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		Anchor best = null;
		for (Anchor a : DataStore.anchors().values()) {
			double d = a.distSq(p.getX(), p.getY(), p.getZ());
			if (a.charge > 30 && d <= 48 * 48 && d > 14 * 14 && (best == null || d < best.distSq(p.getX(), p.getY(), p.getZ()))) {
				best = a;
			}
		}
		if (best == null) {
			return false;
		}
		BlockPos spot = null;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (int i = 0; i < 12 && spot == null; i++) {
			int x = best.x + rnd.nextInt(-3, 4);
			int z = best.z + rnd.nextInt(-3, 4);
			if (Math.abs(x - best.x) < 2 && Math.abs(z - best.z) < 2) {
				continue;
			}
			spot = standable(level, x, best.y + 3, z, 6);
		}
		if (spot == null) {
			return false;
		}
		State st = new State();
		st.mode = Mode.DRAIN;
		st.target = p.getUUID();
		st.anchor = best.key();
		st.limit = Config.LURKER_DRAIN_SECONDS * 20;
		Lurker l = spawn(level, spot, st);
		if (l == null) {
			return false;
		}
		l.setCarriedBlock((best.isNether() ? ModBlocks.EMBER_ANCHOR : ModBlocks.VOID_ANCHOR).defaultBlockState());
		WorldFx.particleIn(Dims.fullId(p), "minecraft:reverse_portal", spot.getX() + 0.5, spot.getY() + 1.5, spot.getZ() + 0.5, 0.5, 1.0, 0.5, 0.2, 60);
		return true;
	}

	private static Lurker spawn(ServerLevel level, BlockPos pos, State st) {
		Lurker l = ModEntities.LURKER.spawn(level, pos, EntitySpawnReason.EVENT);
		if (l == null) {
			return null;
		}
		l.setPersistenceRequired();
		STATES.put(l.getUUID(), st);
		LEVELS.put(l.getUUID(), level);
		return l;
	}

	/** The highest standable spot among a few random points at the given distance (treetops and hills first). */
	private static BlockPos vantage(ServerLevel level, ServerPlayer p, int min, int max) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		boolean nether = Dims.NETHER.equals(Dims.of(p));
		BlockPos best = null;
		for (int i = 0; i < 10; i++) {
			double ang = rnd.nextDouble() * Math.PI * 2;
			double dist = min + rnd.nextDouble() * (max - min);
			int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist);
			int z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
			if (!level.isLoaded(new BlockPos(x, (int) p.getY(), z))) {
				continue;
			}
			BlockPos c;
			if (nether) {
				c = standable(level, x, (int) p.getY() + 12, z, 28);
			} else {
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				c = new BlockPos(x, y, z);
				if (!level.getBlockState(c).isAir() || !level.getBlockState(c.above()).isAir() || !level.getBlockState(c.above(2)).isAir()) {
					c = null;
				}
			}
			if (c != null && (best == null || c.getY() > best.getY())) {
				best = c;
			}
		}
		return best;
	}

	/** Scans down from {@code top} for a solid block with three blocks of air above it. */
	private static BlockPos standable(ServerLevel level, int x, int top, int z, int depth) {
		for (int y = top; y > top - depth; y--) {
			BlockPos feet = new BlockPos(x, y, z);
			if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir() && level.getBlockState(feet.above(2)).isAir()
					&& !level.getBlockState(feet.below()).isAir() && !level.getBlockState(feet.below()).liquid()) {
				return feet;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ behaviour

	private static void drive() {
		Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();
		List<Runnable> later = new ArrayList<>();
		while (it.hasNext()) {
			Map.Entry<UUID, State> e = it.next();
			ServerLevel level = LEVELS.get(e.getKey());
			Entity ent = level == null ? null : level.getEntity(e.getKey());
			if (!(ent instanceof Lurker l) || !l.isAlive()) {
				it.remove();
				LEVELS.remove(e.getKey());
				continue;
			}
			State st = e.getValue();
			st.ticks++;
			ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
			if (p == null || p.level() != l.level() || p.distanceToSqr(l) > 112 * 112) {
				later.add(() -> vanish(l));
				continue;
			}
			switch (st.mode) {
				case WATCH -> watch(l, st, p, later);
				case STALK -> stalk(l, st, p, later);
				case FLEE -> flee(l, st, p, later);
				case DRAIN -> drain(l, st, p, later);
			}
		}
		later.forEach(Runnable::run);
	}

	/** Is the player looking right at the Lurker? */
	private static boolean seen(ServerPlayer p, Lurker l, double cone) {
		Vec3 to = l.getEyePosition().subtract(p.getEyePosition()).normalize();
		return p.getViewVector(1.0f).dot(to) > cone && p.hasLineOfSight(l);
	}

	private static void watch(Lurker l, State st, ServerPlayer p, List<Runnable> later) {
		l.getNavigation().stop();
		l.getLookControl().setLookAt(p, 30, 30);
		double dist = Math.sqrt(p.distanceToSqr(l));
		if (seen(p, l, 0.985)) {
			if (st.stared == 0) {
				PlayerData d = DataStore.get(p.getUUID());
				Journal.unlock(p, d, JournalEntry.TRIBE);
				Advancements.grant(p, Advancements.WATCHED);
			}
			st.stared++;
		}
		if (st.ticks % 160 == 0 && ThreadLocalRandom.current().nextInt(3) == 0) {
			whisper(l, p, 0.35f);
		}
		if (dist < 10 || st.stared > 50) {
			later.add(() -> vanish(l));
		} else if (st.ticks > st.limit) {
			if (ThreadLocalRandom.current().nextDouble() < Config.LURKER_STALK_CHANCE) {
				st.mode = Mode.STALK;
				st.ticks = 0;
				st.limit = 20 * 90;
				whisper(l, p, 0.7f);
			} else {
				later.add(() -> vanish(l));
			}
		}
	}

	/** Creeps in only while unobserved, like something at the edge of your vision. */
	private static void stalk(Lurker l, State st, ServerPlayer p, List<Runnable> later) {
		if (seen(p, l, 0.9)) {
			l.getNavigation().stop();
			l.getLookControl().setLookAt(p, 30, 30);
		} else if (st.ticks % 10 == 0) {
			l.getNavigation().moveTo(p, 1.15);
		}
		if (p.distanceToSqr(l) < 2.6 * 2.6) {
			take(l, st, p);
		} else if (st.ticks > st.limit) {
			later.add(() -> vanish(l));
		}
	}

	/** It takes one thing: an item, experience, a piece of armour, or it lays a curse. */
	private static void take(Lurker l, State st, ServerPlayer p) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		l.setTarget(p); // jaw open, shaking
		l.getLookControl().setLookAt(p, 90, 90);
		String dim = Dims.fullId(p);
		WorldFx.run(String.format(Locale.ROOT, "execute in %s run playsound extremedimensions:lurker_take hostile @a %.2f %.2f %.2f 1.4 1",
				dim, l.getX(), l.getY() + 2, l.getZ()));
		WorldFx.particles(p, "minecraft:sculk_soul", 1.0, 0.5, 0.08, 25);
		WorldFx.particles(p, "minecraft:reverse_portal", 1.0, 0.8, 0.3, 40);
		WorldFx.fx(p, Fx.TAKEN, 0);
		Advancements.grant(p, Advancements.ROBBED);

		double roll = rnd.nextDouble();
		boolean took = false;
		st.loot.from = p.getUUID();
		if (roll < 0.35) {
			took = stealItem(p, st.loot);
		} else if (roll < 0.55) {
			took = stealXp(p, st.loot);
		} else if (roll < 0.70) {
			took = stealArmor(p, st.loot);
		}
		if (!took) {
			PenaltyService.curse(p, Dims.OVERWORLD.equals(Dims.of(p)));
		}
		// Withdraw with the loot, then linger: there is a moment to take it back
		BlockPos away = vantage((ServerLevel) l.level(), p, 14, 22);
		if (away != null) {
			blink(l, Vec3.atBottomCenterOf(away));
		}
		l.setTarget(null);
		st.mode = Mode.FLEE;
		st.ticks = 0;
		st.limit = Config.LURKER_FLEE_SECONDS * 20;
	}

	private static boolean stealItem(ServerPlayer p, Loot loot) {
		var inv = p.getInventory();
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < 36; i++) {
			if (!inv.getItem(i).isEmpty()) {
				slots.add(i);
			}
		}
		if (slots.isEmpty()) {
			return false;
		}
		int slot = slots.get(ThreadLocalRandom.current().nextInt(slots.size()));
		loot.stack = inv.getItem(slot).copy();
		inv.setItem(slot, ItemStack.EMPTY);
		return true;
	}

	private static boolean stealXp(ServerPlayer p, Loot loot) {
		if (p.experienceLevel < 3) {
			return false;
		}
		int levels = Math.max(3, (int) Math.round(p.experienceLevel * Config.LURKER_XP_FRACTION));
		p.giveExperienceLevels(-levels);
		loot.levels = levels;
		return true;
	}

	private static boolean stealArmor(ServerPlayer p, Loot loot) {
		List<EquipmentSlot> worn = new ArrayList<>();
		for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (!p.getItemBySlot(s).isEmpty()) {
				worn.add(s);
			}
		}
		if (worn.isEmpty()) {
			return false;
		}
		EquipmentSlot s = worn.get(ThreadLocalRandom.current().nextInt(worn.size()));
		loot.stack = p.getItemBySlot(s).copy();
		p.setItemSlot(s, ItemStack.EMPTY);
		return true;
	}

	private static void flee(Lurker l, State st, ServerPlayer p, List<Runnable> later) {
		l.getNavigation().stop();
		l.getLookControl().setLookAt(p, 30, 30);
		if (st.ticks > st.limit || p.distanceToSqr(l) < 3 * 3 && st.ticks > 40) {
			later.add(() -> vanish(l));
		}
	}

	/** Drinks an anchor: its light streams into the Lurker's arms. */
	private static void drain(Lurker l, State st, ServerPlayer p, List<Runnable> later) {
		Anchor a = DataStore.anchors().get(st.anchor);
		if (a == null || a.charge <= 0 || st.ticks > st.limit) {
			later.add(() -> vanish(l));
			return;
		}
		// Someone comes close: interrupted
		for (ServerPlayer near : WorldFx.server().getPlayerList().getPlayers()) {
			if (near.level() == l.level() && near.distanceToSqr(l) < 8 * 8) {
				later.add(() -> vanish(l));
				return;
			}
		}
		l.getNavigation().stop();
		l.getLookControl().setLookAt(a.x + 0.5, a.y + 0.5, a.z + 0.5);
		if (st.ticks == 10) {
			l.setTarget(p); // jaw open, trembling while it drinks
		}
		a.leak(Config.LURKER_DRAIN_PER_SECOND / 20.0);

		boolean nether = a.isNether();
		double sx = a.x + 0.5, sy = a.y + 0.9, sz = a.z + 0.5;
		double tx = l.getX(), ty = l.getY() + 2.0, tz = l.getZ();
		double dx = tx - sx, dy = ty - sy, dz = tz - sz;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (st.ticks % 2 == 0 && len > 0.1) {
			ThreadLocalRandom rnd = ThreadLocalRandom.current();
			for (int i = 0; i < 3; i++) {
				double t = rnd.nextDouble();
				WorldFx.particleAt(nether ? "minecraft:soul_fire_flame" : "minecraft:dragon_breath",
						sx + dx * t, sy + dy * t, sz + dz * t, dx / len, dy / len, dz / len, 0.25, 0);
			}
			WorldFx.particleAt("minecraft:reverse_portal", sx, sy, sz, 0.3, 0.2, 0.3, 0.05, 4);
			WorldFx.particleAt(nether ? "minecraft:flame" : "minecraft:portal", tx, ty, tz, 0.3, 0.4, 0.3, 0.02, 3);
		}
		if (st.ticks % 10 == 0) {
			WorldFx.particleAt("minecraft:smoke", sx, sy + 0.2, sz, 0.2, 0.1, 0.2, 0.01, 4);
		}
		if (st.ticks % 40 == 1) {
			WorldFx.soundAt("extremedimensions:lurker_drain", a.x, a.y, a.z, 1.2f, nether ? 0.9f : 1.1f);
		}
	}

	private static void whisper(Lurker l, ServerPlayer p, float vol) {
		WorldFx.run(String.format(Locale.ROOT, "execute in %s run playsound extremedimensions:lurker_whisper hostile %s %.2f %.2f %.2f %.2f 1",
				Dims.fullId(p), p.getStringUUID(), l.getX(), l.getY() + 2, l.getZ(), vol));
	}

	/** Gone: a fold in the air, a reversed breath, nothing. */
	public static void vanish(Lurker l) {
		String dim = l.level().dimension().identifier().toString();
		WorldFx.particleIn(dim, "minecraft:portal", l.getX(), l.getY() + 1.4, l.getZ(), 0.4, 1.2, 0.4, 0.6, 60);
		WorldFx.particleIn(dim, "minecraft:reverse_portal", l.getX(), l.getY() + 1.4, l.getZ(), 0.3, 1.0, 0.3, 0.1, 30);
		WorldFx.particleIn(dim, "minecraft:large_smoke", l.getX(), l.getY() + 1.0, l.getZ(), 0.3, 0.8, 0.3, 0.01, 12);
		WorldFx.run(String.format(Locale.ROOT, "execute in %s run playsound extremedimensions:lurker_vanish hostile @a %.2f %.2f %.2f 1 1",
				dim, l.getX(), l.getY() + 1.5, l.getZ()));
		STATES.remove(l.getUUID());
		LEVELS.remove(l.getUUID());
		l.discard();
	}

	private static void blink(Lurker l, Vec3 to) {
		String dim = l.level().dimension().identifier().toString();
		WorldFx.particleIn(dim, "minecraft:portal", l.getX(), l.getY() + 1.4, l.getZ(), 0.4, 1.2, 0.4, 0.6, 40);
		l.teleportTo(to.x, to.y, to.z);
		WorldFx.particleIn(dim, "minecraft:reverse_portal", to.x, to.y + 1.4, to.z, 0.4, 1.2, 0.4, 0.2, 40);
	}

	/** Hit while not carrying loot: it simply is not there any more. Returns true if the hit should be ignored. */
	public static boolean onHurt(Lurker l) {
		State st = STATES.get(l.getUUID());
		if (st == null || st.mode == Mode.FLEE) {
			return false;
		}
		vanish(l);
		return true;
	}

	/** Slain while fleeing: what it took falls back into the world. */
	public static void onDeath(Lurker l, Entity killer) {
		State st = STATES.remove(l.getUUID());
		LEVELS.remove(l.getUUID());
		if (st == null) {
			return;
		}
		ServerLevel level = (ServerLevel) l.level();
		if (!st.loot.stack.isEmpty()) {
			l.spawnAtLocation(level, st.loot.stack);
		}
		if (killer instanceof ServerPlayer p) {
			if (st.loot.levels > 0 && p.getUUID().equals(st.loot.from)) {
				p.giveExperienceLevels(st.loot.levels);
			}
			Advancements.grant(p, Advancements.HUNTED);
			Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.TRIBE);
		}
	}

	/** Operator command: summon a Lurker in a given mode near the player. */
	public static boolean summon(ServerPlayer p, String mode) {
		return switch (mode) {
			case "drain" -> spawnDrain(p);
			case "stalk" -> {
				if (!spawnWatch(p)) {
					yield false;
				}
				for (State st : STATES.values()) {
					if (p.getUUID().equals(st.target) && st.mode == Mode.WATCH) {
						st.mode = Mode.STALK;
						st.limit = 20 * 90;
					}
				}
				yield true;
			}
			default -> spawnWatch(p);
		};
	}
}
