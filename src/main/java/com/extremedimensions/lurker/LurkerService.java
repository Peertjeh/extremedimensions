package com.extremedimensions.lurker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.anchor.SoulWards;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.Hoard;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.hollow.HollowService;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.network.Fx;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;
import com.extremedimensions.world.WorldService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Lurker's mind. Every Lurker is driven from here through a small state machine:
 *
 * <ul>
 *   <li><b>WATCH</b>: stands on high ground 50-72 blocks away, half in cover, and stares. Too close, or stared
 *       back at too long, and it is gone.</li>
 *   <li><b>STALK</b> (rare): creeps closer, but only while the player is not looking, and faster the closer it
 *       gets.</li>
 *   <li><b>FLEE</b>: after taking something (or after being struck) it runs off on three limbs, clutching the
 *       loot, and vanishes after a while. Kill it in time to get the loot back.</li>
 *   <li><b>DRAIN</b>: holds a charged anchor's light in its arms and drinks the anchor dry.</li>
 * </ul>
 *
 * Lurkers are never saved: one that is not driven by this service (after a restart) dissolves.
 */
public final class LurkerService {
	private LurkerService() {}

	enum Mode { WATCH, STALK, TAKE, FLEE, DRAIN }

	/** Ticks of the take animation, and the tick in it when the theft happens. */
	private static final int TAKE_TICKS = 26;
	private static final int TAKE_AT = 13;

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
		/** Distance to the player when stalking began, for the speed ramp. */
		double startDist;
		String anchor;
		/** Set when the pose must switch to stalking on the next drive (command summons). */
		boolean stalkPose;
		final Loot loot = new Loot();
		/** Where it carries the loot. */
		Hoard hoard;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Map<UUID, ServerLevel> LEVELS = new HashMap<>();
	/** Server tick of each player's last encounter (cooldown). */
	private static final Map<UUID, Long> LAST_SEEN = new HashMap<>();

	public static boolean isDriven(Entity e) {
		return STATES.containsKey(e.getUUID());
	}

	public static void clear() {
		STATES.clear();
		LEVELS.clear();
		LAST_SEEN.clear();
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
			if (p.isSpectator() || p.isCreative() || !(Dims.OVERWORLD.equals(dim) || Dims.NETHER.equals(dim)) || hunting(p) || Rules.peaceful(p.level())) {
				continue;
			}
			// Rare by design: a base chance, and never again soon after the last encounter
			Long last = LAST_SEEN.get(p.getUUID());
			if (last != null && s.getTickCount() - last < Config.LURKER_COOLDOWN_SECONDS * 20L) {
				continue;
			}
			// Marked by the Tribe (Nether) and Watched (Overworld): they find this player more often
			PlayerData pd = DataStore.get(p.getUUID());
			double often = Dims.NETHER.equals(dim)
					? 1 + Penalty.MARKED_BY_THE_TRIBE.frac() * pd.stacks(Penalty.MARKED_BY_THE_TRIBE)
					: 1 + Penalty.WATCHED.frac() * (PenaltyService.owHere(dim) ? pd.stacks(Penalty.WATCHED) : 0);
			if (com.extremedimensions.relic.RelicService.has(pd, com.extremedimensions.relic.Relic.LURKERS_TOOTH)) {
				// Lurker's Tooth: the tribe smells its own on you
				often *= Math.max(0, Config.RELIC_LURKERS_TOOTH_OFTEN);
			}
			if (rnd.nextDouble() >= Config.LURKER_SPAWN_CHANCE * often) {
				continue;
			}
			LAST_SEEN.put(p.getUUID(), (long) s.getTickCount());
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
		BlockPos pos = vantage(level, p, Config.LURKER_WATCH_DISTANCE_MIN, Math.max(Config.LURKER_WATCH_DISTANCE_MIN + 1, Config.LURKER_WATCH_DISTANCE_MAX));
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
			if (a.charge > 30 && d <= 48 * 48 && d > 14 * 14 && (best == null || d < best.distSq(p.getX(), p.getY(), p.getZ())) && !SoulWards.warded(level, a.x, a.y, a.z)) {
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
		l.setLurkerPose(Lurker.POSE_DRAIN);
		// The page that tells how to keep them thirsty
		Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.SOUL_LIGHT);
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

	/**
	 * Picks where a Lurker shows itself: among a number of random standable spots at the given distance, the best
	 * score wins. High ground scores, and so does partial cover: something between it and the player (leaves, a
	 * trunk, a ridge) that hides the body but not the head.
	 */
	private static BlockPos vantage(ServerLevel level, ServerPlayer p, int min, int max) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		boolean nether = Dims.NETHER.equals(Dims.of(p));
		Vec3 eye = p.getEyePosition();
		BlockPos best = null;
		double bestScore = -1e9;
		for (int i = 0; i < 16; i++) {
			double ang = rnd.nextDouble() * Math.PI * 2;
			double dist = min + rnd.nextDouble() * (max - min);
			int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist);
			int z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
			if (!Loaded.ready(level, new BlockPos(x, (int) p.getY(), z))) {
				continue;
			}
			BlockPos c;
			if (nether) {
				c = standable(level, x, (int) p.getY() + 16, z, 36);
			} else {
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				c = new BlockPos(x, y, z);
				if (!level.getBlockState(c).isAir() || !level.getBlockState(c.above()).isAir() || !level.getBlockState(c.above(2)).isAir()
						|| level.getBlockState(c.below()).isAir()) {
					c = null;
				}
			}
			if (c == null) {
				continue;
			}
			// Cover: rays from the player's eyes to its feet, body and head
			boolean feet = blocked(level, eye, Vec3.atBottomCenterOf(c).add(0, 0.5, 0));
			boolean body = blocked(level, eye, Vec3.atBottomCenterOf(c).add(0, 1.8, 0));
			boolean head = blocked(level, eye, Vec3.atBottomCenterOf(c).add(0, 3.2, 0));
			double score = (c.getY() - p.getY()) * 1.5;
			if (!head && (feet || body)) {
				score += 14; // half hidden, watching over the edge
			} else if (head && feet && body) {
				score -= 6; // fully hidden: nobody would ever see it
			}
			score += rnd.nextDouble() * 4;
			if (score > bestScore) {
				bestScore = score;
				best = c;
			}
		}
		return best;
	}

	/** Is there something solid (or leaves) on the line between two points? */
	private static boolean blocked(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		double len = d.length();
		int steps = (int) Math.ceil(len);
		for (int i = 2; i < steps - 1; i++) {
			Vec3 pt = from.add(d.scale(i / len));
			if (!level.getBlockState(BlockPos.containing(pt.x, pt.y, pt.z)).isAir()) {
				return true;
			}
		}
		return false;
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
			if (st.stalkPose) {
				st.stalkPose = false;
				l.setLurkerPose(Lurker.POSE_STALK);
			}
			ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
			if (p == null || p.level() != l.level() || p.distanceToSqr(l) > 112 * 112) {
				later.add(() -> vanish(l));
				continue;
			}
			switch (st.mode) {
				case WATCH -> watch(l, st, p, later);
				case STALK -> stalk(l, st, p, later);
				case TAKE -> take(l, st, p);
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
		if (st.ticks % 100 == 50) {
			animals(l, p);
		}
		if (dist < 14 || st.stared > 50) {
			later.add(() -> vanish(l));
		} else if (st.ticks > st.limit) {
			if (!Dims.SEAM.equals(Dims.of(p)) && WorldService.tribeSteals() && ThreadLocalRandom.current().nextDouble() < Config.LURKER_STALK_CHANCE) {
				st.mode = Mode.STALK;
				l.setLurkerPose(Lurker.POSE_STALK);
				st.ticks = 0;
				st.limit = 20 * 90;
				st.startDist = dist;
				whisper(l, p, 0.7f);
			} else {
				later.add(() -> {
					WorldService.offering((ServerLevel) l.level(), l.getX(), l.getY(), l.getZ());
					vanish(l);
				});
			}
		}
	}

	/**
	 * Creeps in only while unobserved, like something at the edge of your vision. It starts slow and gathers
	 * speed as the distance closes, so the last stretch comes faster than you expect.
	 */
	private static void stalk(Lurker l, State st, ServerPlayer p, List<Runnable> later) {
		double dist = Math.sqrt(p.distanceToSqr(l));
		if (st.ticks % 100 == 50) {
			animals(l, p);
		}
		if (st.startDist <= 0) {
			st.startDist = dist;
		}
		if (seen(p, l, 0.9)) {
			l.getNavigation().stop();
			l.getLookControl().setLookAt(p, 30, 30);
		} else if (st.ticks % 5 == 0) {
			double close = 1.0 - Math.max(0.0, Math.min(1.0, (dist - 3.0) / Math.max(1.0, st.startDist - 3.0)));
			double speed = Config.LURKER_STALK_SPEED_MIN + (Config.LURKER_STALK_SPEED_MAX - Config.LURKER_STALK_SPEED_MIN) * close * close;
			l.getNavigation().moveTo(p, speed);
		}
		double reach = takeReach();
		if (reach > 0 && p.distanceToSqr(l) < reach * reach) {
			l.getNavigation().stop();
			l.setLurkerPose(Lurker.POSE_TAKE);
			st.mode = Mode.TAKE;
			st.ticks = 0;
		} else if (st.ticks > st.limit) {
			later.add(() -> vanish(l));
		}
	}

	/**
	 * The take, as a short sequence: it unfolds its arm towards the player, snatches one thing (an item, experience,
	 * a piece of armour, or a curse) and then withdraws with the loot.
	 */
	private static void take(Lurker l, State st, ServerPlayer p) {
		l.getNavigation().stop();
		l.getLookControl().setLookAt(p, 90, 90);
		String dim = Dims.fullId(p);
		if (st.ticks == 1) {
			WorldFx.soundIn(dim, "extremedimensions:lurker_take", "hostile", l.getX(), l.getY() + 2, l.getZ(), 1.4f, 1f);
		}
		if (st.ticks == TAKE_AT) {
			if (HollowService.guarded(p)) {
				// A Hollowling friend leaps in: it flinches and runs empty-handed
				run(l, st, p, true);
				return;
			}
			if (com.extremedimensions.tribe.Standing.spared(p)) {
				// Kin of the tribe: it looks at them a long moment, and lets them be
				p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.extremedimensions.tribe.spared"), true);
				run(l, st, p, true);
				return;
			}
			snatch(l, st, p);
		}
		if (st.ticks >= TAKE_TICKS) {
			run(l, st, p, false);
		}
	}

	private static void snatch(Lurker l, State st, ServerPlayer p) {
		st.loot.from = p.getUUID();
		double d = Math.sqrt(p.distanceToSqr(l));
		// Only what reaches the player right now (and is possible) can be picked, by weight
		List<Integer> kinds = new ArrayList<>();
		boolean tooth = com.extremedimensions.relic.RelicService.has(p, com.extremedimensions.relic.Relic.LURKERS_TOOTH);
		for (int k = 0; k < 4; k++) {
			// Lurker's Tooth: no item and no armour can be taken from its bearer
			if (!(tooth && (k == 0 || k == 2))) {
				kinds.add(k);
			}
		}
		while (!kinds.isEmpty()) {
			double total = 0;
			for (int k : kinds) {
				total += inReach(k, d) ? Math.max(0, weight(k)) : 0;
			}
			if (total <= 0) {
				return;
			}
			double roll = ThreadLocalRandom.current().nextDouble() * total;
			int pick = kinds.get(0);
			for (int k : kinds) {
				double w = inReach(k, d) ? Math.max(0, weight(k)) : 0;
				if (w > 0 && roll < w) {
					pick = k;
					break;
				}
				roll -= w;
			}
			boolean took = switch (pick) {
				case 0 -> stealItem(p, st.loot);
				case 1 -> stealXp(p, st.loot);
				case 2 -> stealArmor(p, st.loot);
				default -> PenaltyService.curse(p, Dims.OVERWORLD.equals(Dims.of(p)) && DataStore.overworldPenalties()) != null;
			};
			if (took) {
				WorldFx.particles(p, "minecraft:sculk_soul", 1.0, 0.5, 0.08, 25);
				WorldFx.particles(p, "extremedimensions:lurker_wisp", 1.0, 0.5, 0.0, 12);
				WorldFx.particles(p, "minecraft:reverse_portal", 1.0, 0.8, 0.3, 40);
				WorldFx.fx(p, Fx.TAKEN, 0);
				Advancements.grant(p, Advancements.ROBBED);
				return;
			}
			kinds.remove(Integer.valueOf(pick));
		}
	}

	/** 0 = item, 1 = experience, 2 = armour, 3 = curse. */
	private static double range(int kind) {
		return switch (kind) {
			case 0 -> Config.LURKER_ITEM_RANGE;
			case 1 -> Config.LURKER_XP_RANGE;
			case 2 -> Config.LURKER_ARMOR_RANGE;
			default -> Config.LURKER_CURSE_RANGE;
		};
	}

	private static double weight(int kind) {
		return switch (kind) {
			case 0 -> Config.LURKER_ITEM_WEIGHT;
			case 1 -> Config.LURKER_XP_WEIGHT;
			case 2 -> Config.LURKER_ARMOR_WEIGHT;
			default -> Config.LURKER_CURSE_WEIGHT;
		};
	}

	/** A little slack, since the player can step back during the reach. */
	private static boolean inReach(int kind, double dist) {
		return range(kind) > 0 && dist <= range(kind) + 1.5;
	}

	/** Distance at which a stalking Lurker strikes: the longest reach of anything it can do. */
	private static double takeReach() {
		double r = 0;
		for (int k = 0; k < 4; k++) {
			if (weight(k) > 0) {
				r = Math.max(r, range(k));
			}
		}
		return r;
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

	/** Breaks into a run, away from the player. */
	private static void run(Lurker l, State st, ServerPlayer p, boolean struck) {
		st.mode = Mode.FLEE;
		st.ticks = 0;
		st.limit = Math.max(3, Config.LURKER_RUN_SECONDS) * 20;
		l.setLurkerPose(Lurker.POSE_RUN);
		String dim = l.level().dimension().identifier().toString();
		String sound = struck ? "extremedimensions:lurker_shriek" : "extremedimensions:lurker_flee";
		WorldFx.soundIn(dim, sound, "hostile", l.getX(), l.getY() + 2.5, l.getZ(), 1.6f, 1f);
		if (!st.loot.stack.isEmpty() || st.loot.levels > 0) {
			// It runs home to its hoard (struck or not): follow it, and you find where your things went
			st.hoard = HollowService.hoardFor(p, l.position());
			if (st.hoard != null) {
				st.limit = 20 * 25;
			}
		}
		flee(l, st, p, new ArrayList<>());
	}

	/**
	 * Runs on three limbs, the loot clutched to its chest: away from the player, accelerating out of the first
	 * strides, leaving a faint trail of ink. After a good stretch it folds into the air.
	 */
	private static void flee(Lurker l, State st, ServerPlayer p, List<Runnable> later) {
		if (st.hoard != null) {
			Vec3 home = new Vec3(st.hoard.x + 0.5, st.hoard.y, st.hoard.z + 0.5);
			if (st.ticks % 8 == 0 || l.getNavigation().isDone()) {
				double speed = Config.LURKER_RUN_SPEED * (0.6 + 0.4 * Math.min(1.0, st.ticks / 40.0));
				l.getNavigation().moveTo(home.x, home.y, home.z, speed);
			}
			if (st.ticks % 3 == 0) {
				WorldFx.particleIn(l.level().dimension().identifier().toString(), "extremedimensions:lurker_wisp",
						l.getX(), l.getY() + 1.4, l.getZ(), 0.35, 0.5, 0.35, 0.0, 2);
			}
			if (l.position().distanceToSqr(home) < 3.5 * 3.5 || st.ticks > st.limit) {
				Hoard h = st.hoard;
				ItemStack stack = st.loot.stack;
				int levels = st.loot.levels;
				st.loot.stack = ItemStack.EMPTY;
				st.loot.levels = 0;
				st.hoard = null;
				later.add(() -> {
					HollowService.stash(h, stack, levels);
					vanish(l);
				});
			}
			return;
		}
		if (st.ticks % 8 == 0 || l.getNavigation().isDone()) {
			double ramp = Math.min(1.0, st.ticks / 40.0);
			double speed = Config.LURKER_RUN_SPEED * (0.6 + 0.4 * ramp);
			Vec3 away = new Vec3(l.getX() - p.getX(), 0, l.getZ() - p.getZ());
			if (away.lengthSqr() < 0.01) {
				away = new Vec3(1, 0, 0);
			}
			// Mostly straight away, with a slight weave so it does not run like a machine
			double ang = Math.atan2(away.z, away.x) + Math.sin(st.ticks * 0.05 + l.getId()) * 0.5;
			int tx = (int) Math.floor(l.getX() + Math.cos(ang) * 20);
			int tz = (int) Math.floor(l.getZ() + Math.sin(ang) * 20);
			ServerLevel level = (ServerLevel) l.level();
			BlockPos goal = Dims.NETHER.equals(Dims.of(level))
					? standable(level, tx, (int) l.getY() + 8, tz, 20)
					: new BlockPos(tx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz), tz);
			if (goal != null) {
				l.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, speed);
			}
		}
		if (st.ticks % 3 == 0) {
			String dim = l.level().dimension().identifier().toString();
			WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", l.getX(), l.getY() + 1.4, l.getZ(), 0.35, 0.5, 0.35, 0.0, 2);
		}
		if (st.ticks > st.limit) {
			later.add(() -> vanish(l));
		}
	}

	/** Drinks an anchor: its light streams into the Lurker's arms. */
	private static void drain(Lurker l, State st, ServerPlayer p, List<Runnable> later) {
		Anchor a = DataStore.anchors().get(st.anchor);
		if (a == null || a.charge <= 0 || st.ticks > st.limit) {
			if (a != null && a.charge > 0 && l.level() instanceof net.minecraft.server.level.ServerLevel level) {
				// It drank its fill: the anchor it drank from may crack
				com.extremedimensions.events.UnstableAnchors.unsettle(level, a);
			}
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
		// Someone lit soul light nearby: it flinches and is gone
		if (st.ticks % 20 == 0 && SoulWards.warded(l.level(), a.x, a.y, a.z)) {
			String dim = l.level().dimension().identifier().toString();
			WorldFx.soundIn(dim, "extremedimensions:lurker_shriek", "hostile", l.getX(), l.getY() + 2.5, l.getZ(), 1.2f, 1.3f);
			WorldFx.particleIn(dim, "extremedimensions:soul_mote", a.x + 0.5, a.y + 1.0, a.z + 0.5, 0.6, 0.6, 0.6, 0.0, 20);
			later.add(() -> vanish(l));
			return;
		}
		l.getNavigation().stop();
		l.getLookControl().setLookAt(a.x + 0.5, a.y + 0.5, a.z + 0.5);
		a.leak(Config.LURKER_DRAIN_PER_SECOND / 20.0);

		beam(l, a, st.ticks);
	}

	/**
	 * The drain beam: a thin stream of coloured motes flowing from the anchor up into the Lurker's raised hands,
	 * with a faint glow where it leaves the stone.
	 */
	private static void beam(Lurker l, Anchor a, int tick) {
		boolean nether = a.isNether();
		String dust = nether ? "minecraft:dust{color:[1.0,0.55,0.18],scale:0.55}" : "minecraft:dust{color:[0.74,0.38,1.0],scale:0.55}";
		String core = nether ? "minecraft:dust{color:[1.0,0.9,0.6],scale:0.35}" : "minecraft:dust{color:[0.95,0.85,1.0],scale:0.35}";
		double sx = a.x + 0.5, sy = a.y + 0.95, sz = a.z + 0.5;
		double yaw = Math.toRadians(l.getYRot());
		// Between the raised hands, in front of the chest
		double tx = l.getX() - Math.sin(yaw) * 1.3, ty = l.getY() + 3.1, tz = l.getZ() + Math.cos(yaw) * 1.3;
		double dx = tx - sx, dy = ty - sy, dz = tz - sz;
		int n = 10;
		double phase = (tick % 10) / 10.0;
		for (int i = 0; i < n; i++) {
			double t = (i + phase) / n;
			// a slight arc, as if the light were pulled upward
			double lift = Math.sin(t * Math.PI) * 0.35;
			WorldFx.particleAt(i % 3 == 0 ? core : dust, sx + dx * t, sy + dy * t + lift, sz + dz * t, 0.02, 0.02, 0.02, 0.0, 1);
		}
		if (tick % 4 == 0) {
			WorldFx.particleAt(nether ? "minecraft:small_flame" : "minecraft:reverse_portal", sx, sy, sz, 0.18, 0.05, 0.18, 0.01, 2);
			WorldFx.particleAt("minecraft:end_rod", tx, ty, tz, 0.15, 0.15, 0.15, 0.005, 1);
		}
		if (tick % 40 == 1) {
			WorldFx.soundAt("extremedimensions:lurker_drain", a.x, a.y, a.z, 1.2f, nether ? 0.9f : 1.1f);
		}
	}

	/**
	 * The world notices before you do: wolves and cats near the player turn towards the Lurker and growl or hiss,
	 * parrots mimic something they should not know, and grazing animals stop to stare.
	 */
	private static void animals(Lurker l, ServerPlayer p) {
		if (ThreadLocalRandom.current().nextDouble() >= Config.LURKER_ANIMAL_WARNING) {
			return;
		}
		String dim = l.level().dimension().identifier().toString();
		AABB box = p.getBoundingBox().inflate(20, 8, 20);
		for (Mob m : l.level().getEntitiesOfClass(Mob.class, box, e -> e.isAlive() && !(e instanceof Lurker))) {
			String id = BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).toString();
			String sound = switch (id) {
				case "minecraft:wolf" -> "minecraft:entity.wolf.growl";
				case "minecraft:cat", "minecraft:ocelot" -> "minecraft:entity.cat.hiss";
				case "minecraft:parrot" -> "minecraft:entity.parrot.imitate.warden";
				case "minecraft:fox" -> "minecraft:entity.fox.screech";
				case "minecraft:horse", "minecraft:donkey", "minecraft:mule" -> "minecraft:entity.horse.angry";
				case "minecraft:cow", "minecraft:sheep", "minecraft:pig", "minecraft:goat", "minecraft:llama", "minecraft:chicken" -> "";
				default -> null;
			};
			if (sound == null) {
				continue;
			}
			m.getNavigation().stop();
			m.getLookControl().setLookAt(l, 30, 30);
			if (!sound.isEmpty()) {
				WorldFx.soundIn(dim, sound, "neutral", m.getX(), m.getY() + 0.5, m.getZ(), 0.9f, (float) (0.85 + ThreadLocalRandom.current().nextDouble() * 0.2));
			}
		}
	}

	private static void whisper(Lurker l, ServerPlayer p, float vol) {
		WorldFx.soundFor(p, "extremedimensions:lurker_whisper", "hostile", l.getX(), l.getY() + 2, l.getZ(), vol, 1f);
	}

	/** Gone: a fold in the air, a reversed breath, nothing. */
	public static void vanish(Lurker l) {
		String dim = l.level().dimension().identifier().toString();
		WorldFx.particleIn(dim, "minecraft:portal", l.getX(), l.getY() + 1.4, l.getZ(), 0.4, 1.2, 0.4, 0.6, 60);
		WorldFx.particleIn(dim, "minecraft:reverse_portal", l.getX(), l.getY() + 1.4, l.getZ(), 0.3, 1.0, 0.3, 0.1, 30);
		WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", l.getX(), l.getY() + 1.6, l.getZ(), 0.5, 1.2, 0.5, 0.0, 30);
		WorldFx.soundIn(dim, "extremedimensions:lurker_vanish", "hostile", l.getX(), l.getY() + 1.5, l.getZ(), 1f, 1f);
		State st = STATES.remove(l.getUUID());
		LEVELS.remove(l.getUUID());
		if (st != null && (!st.loot.stack.isEmpty() || st.loot.levels > 0) && l.level() instanceof ServerLevel level) {
			// What it took is never simply gone (its victim left, it was cut off, the server stopped): it ends up in the
			// hoard it would have run to, or falls where it stood
			Hoard h = st.hoard;
			ServerPlayer from = st.loot.from == null || WorldFx.server() == null ? null : WorldFx.server().getPlayerList().getPlayer(st.loot.from);
			if (h == null && from != null && from.level() == level) {
				h = HollowService.hoardFor(from, l.position());
			}
			if (h != null) {
				HollowService.stash(h, st.loot.stack, st.loot.levels);
			} else if (!st.loot.stack.isEmpty()) {
				l.spawnAtLocation(level, st.loot.stack);
			}
			st.loot.stack = ItemStack.EMPTY;
			st.loot.levels = 0;
		}
		l.discard();
	}

	/** Struck: it shrieks and runs. Already running, it just keeps going. */
	public static void onHurt(Lurker l) {
		State st = STATES.get(l.getUUID());
		if (st == null || st.mode == Mode.FLEE) {
			return;
		}
		ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
		if (p == null) {
			vanish(l);
			return;
		}
		run(l, st, p, true);
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
			com.extremedimensions.tribe.Standing.change(p, Config.TRIBE_REP_LURKER, "gui.extremedimensions.tribe.why.lurker");
		}
	}

	/** Operator command: summon a Lurker in a given mode near the player. */
	public static boolean summon(ServerPlayer p, String mode) {
		// Never in another mod's world
		if (!Dims.ours(p)) {
			return false;
		}
		return switch (mode) {
			case "drain" -> spawnDrain(p);
			case "stalk" -> {
				if (!spawnWatch(p)) {
					yield false;
				}
				for (State st : STATES.values()) {
					if (p.getUUID().equals(st.target) && st.mode == Mode.WATCH) {
						st.mode = Mode.STALK;
						st.ticks = 0;
						st.stalkPose = true;
						st.limit = 20 * 90;
					}
				}
				yield true;
			}
			default -> spawnWatch(p);
		};
	}
}
