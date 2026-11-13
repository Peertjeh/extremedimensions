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

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Lurker's mind. Every Lurker is driven from here through a small state machine:
 *
 * <ul>
 *   <li><b>WATCH</b>: stands on high ground 50-72 blocks away, half in cover, and stares. Too close, or stared
 *       back at too long, and it is gone.</li>
 *   <li><b>STALK</b>: creeps closer to steal, its glow pulsing blue, but only while the player is not looking, and
 *       faster the closer it gets. Its reach claws, takes a few things and may leave a curse.</li>
 *   <li><b>STRIKE</b>: comes to hurt instead, glowing red: it runs at the player openly and deals a few heavy blows
 *       (each with a short wind-up to see coming), and may leave a curse. Struck, a Lurker may also turn on you.</li>
 *   <li><b>FLEE</b>: after taking something (or after being struck) it runs off on three limbs, clutching the
 *       loot, and vanishes after a while. Kill it in time to get the loot back.</li>
 *   <li><b>DRAIN</b>: holds a charged anchor's light in its arms and drinks the anchor dry.</li>
 * </ul>
 *
 * Lurkers are never saved: one that is not driven by this service (after a restart) dissolves.
 */
public final class LurkerService {
	private LurkerService() {}

	enum Mode { WATCH, STALK, TAKE, FLEE, DRAIN, STRIKE }

	/** Ticks of the take animation, and the tick in it when the theft happens. */
	private static final int TAKE_TICKS = 26;
	private static final int TAKE_AT = 13;
	/** A blow: how near it must come (blocks, centre to centre), its wind-up and the pause after it (ticks). */
	private static final double STRIKE_REACH = 2.6;
	/** An ink bolt at a prey it cannot reach but sees: how far it throws, and its wind-up (ticks). */
	private static final double BOLT_RANGE = 24.0;
	private static final int BOLT_WINDUP = 16;
	/** How often it asks whether its prey is out of its way (it works out a route, so not every tick). */
	private static final int BLOCK_CHECK = 10;
	/** How far round a hidden prey a hunting Lurker looks for a place to see them from, and how long it tries (ticks). */
	private static final int SIGHT_RADIUS = 8;
	private static final int SIGHT_GIVE_UP = 200;
	private static final int STRIKE_WINDUP = 10;
	private static final int STRIKE_PAUSE = 16;
	/** A Lurker's body (as its entity type has it): where it shows itself, it must fit. */
	static final double BODY_WIDTH = 1.3, BODY_HEIGHT = 3.4;
	/** Ticks a hunting Lurker that sees its prey but cannot get at it waits before it reaches in after them. */
	private static final int REACH_IN_AFTER = 60;
	/** How far it reaches in (blocks). */
	private static final double REACH_IN_RANGE = 12;

	/** What a Lurker took and is carrying away. */
	static final class Loot {
		final List<ItemStack> stacks = new ArrayList<>();
		int levels = 0;
		UUID from;

		boolean any() {
			return !stacks.isEmpty() || levels > 0;
		}
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
		/** Where it runs to (kept until it gets there, so it never keeps turning). */
		Vec3 away;
		/** Curses it left this encounter, blows it landed, and the wind-up and pause of a blow (ticks left). */
		int curses;
		int blows;
		int windup;
		int pause;
		/** Ticks its prey has stayed out of its reach, in sight; and whether the blow it winds up reaches in after them. */
		int unreach;
		boolean reachIn;
		/** One of a swarm: it hunts until the swarm is over, whatever its blows. */
		boolean swarm;
		/** Ticks its prey has been out of its way (no whole route there); whether it sent its young; its bolt (ticks). */
		int blocked;
		boolean sentYoung;
		/** Out of sight of a prey under a roof: where it goes to see them from, and how long it has been at it. */
		BlockPos sightFrom;
		int sightTicks;
		int boltIn;
		int boltWindup;
		/** What its last reach took and the player has not been told yet (3.55.0): told as it runs. */
		final List<ItemStack> untold = new ArrayList<>();
		int untoldLevels;
	}

	/** A theft as its victim was told it (3.55.0): what was taken, the levels, which way it ran, and whether to its hoard. */
	public record Theft(List<ItemStack> stacks, int levels, String bearing, boolean hoard) {}

	/** The last theft told, and to whom (for the tests: a test player's client reads no chat). */
	public record Told(UUID to, Theft theft) {}

	public static volatile Told lastTheft = null;

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
		SWARM_AT.clear();
	}

	/** Swarms on their way: the dread swells first, and they come when it breaks off (the server tick, per player). */
	private static final Map<UUID, Long> SWARM_AT = new HashMap<>();

	/** The swarms whose dread has broken off come now (to a player still here and in a world they come to). */
	private static void swarmsDue(MinecraftServer s) {
		Iterator<Map.Entry<UUID, Long>> it = SWARM_AT.entrySet().iterator();
		List<ServerPlayer> due = new ArrayList<>();
		while (it.hasNext()) {
			Map.Entry<UUID, Long> e = it.next();
			ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
			if (p == null) {
				it.remove();
			} else if (s.getTickCount() >= e.getValue()) {
				it.remove();
				due.add(p);
			}
		}
		for (ServerPlayer p : due) {
			String dim = Dims.of(p);
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && (Dims.OVERWORLD.equals(dim) || Dims.NETHER.equals(dim)) && !Rules.peaceful(p.level())) {
				swarm(p);
			}
		}
	}

	/**
	 * A swarm sets out for this player: the dread swells under everything first, and the swarm comes when it breaks off
	 * into silence (at once when the dread is switched off). False if none could come.
	 */
	public static boolean swarmComes(ServerPlayer p) {
		if (com.extremedimensions.world.AmbientService.dread(p)) {
			SWARM_AT.put(p.getUUID(), WorldFx.server().getTickCount() + (long) com.extremedimensions.world.AmbientService.DREAD_TICKS);
			return true;
		}
		return swarm(p);
	}

	/** For the tests: whether a swarm is on its way to this player (its dread swelling). */
	public static boolean swarmComing(ServerPlayer p) {
		return SWARM_AT.containsKey(p.getUUID());
	}

	// ------------------------------------------------------------------ spawning

	public static void tick(MinecraftServer s) {
		drive();
		if (!SWARM_AT.isEmpty()) {
			swarmsDue(s);
		}
		if (Config.LURKER_ENABLED == 0 || s.getTickCount() % 400 != 0) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			String dim = Dims.of(p);
			if (p.isSpectator() || p.isCreative() || !(Dims.OVERWORLD.equals(dim) || Dims.NETHER.equals(dim)) || hunting(p) || SWARM_AT.containsKey(p.getUUID()) || Rules.peaceful(p.level())) {
				continue;
			}
			// Covetous: one comes every night, whatever the wait, and it comes to take
			if (com.extremedimensions.penalty.AbyssalTolls.covetousNight(p, DataStore.get(p.getUUID()))) {
				LAST_SEEN.put(p.getUUID(), (long) s.getTickCount());
				summon(p, "stalk");
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
			// Tetherite calls to them: the metal of swallowed Tethers is what the tribe is always after
			often *= com.extremedimensions.tetherite.Tetherite.lure(p);
			if (rnd.nextDouble() >= Config.LURKER_SPAWN_CHANCE * often) {
				continue;
			}
			LAST_SEEN.put(p.getUUID(), (long) s.getTickCount());
			// Extreme: now and then the whole tribe comes at once (twice as often for one it hates)
			double swarmChance = Config.LURKER_SWARM_CHANCE
					* (com.extremedimensions.tribe.Standing.of(p).ordinal() <= com.extremedimensions.tribe.Standing.Level.HATED.ordinal() ? 2 : 1);
			if (swarmChance > 0 && !kin(p) && WorldService.tribeSteals() && rnd.nextDouble() < swarmChance) {
				if (swarmComes(p)) {
					continue;
				}
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

	/**
	 * What of the Lurkers is after this player right now, near them (64 blocks) and as they would know it: "swarm" (one
	 * of a swarm), "hunt" (one glowing red at them, or reaching for what they carry); null when none is.
	 */
	public static String threat(ServerPlayer p) {
		if (SWARM_AT.containsKey(p.getUUID())) {
			// (its dread already swelling: leaving now does not save them either)
			return "swarm";
		}
		String found = null;
		for (Map.Entry<UUID, State> e : STATES.entrySet()) {
			State st = e.getValue();
			ServerLevel level = LEVELS.get(e.getKey());
			if (!p.getUUID().equals(st.target) || level != p.level() || !(level.getEntity(e.getKey()) instanceof Lurker l) || !l.isAlive()
					|| l.distanceToSqr(p) > 64 * 64) {
				continue;
			}
			if (st.swarm) {
				return "swarm";
			}
			if (st.mode == Mode.STRIKE || st.mode == Mode.TAKE) {
				found = "hunt";
			}
		}
		// (a Cave Lurker on their heels too)
		if (found == null && CaveLurkerService.hunting(p)) {
			found = "hunt";
		}
		return found;
	}

	/** A player who left while the tribe hunted them is back: what hunted them comes again. False if nothing could. */
	public static boolean comeBack(ServerPlayer p, String why) {
		if (p.isSpectator() || p.isCreative() || Rules.peaceful(p.level()) || kin(p) || !WorldService.tribeSteals()) {
			return false;
		}
		String dim = Dims.of(p);
		if (!(Dims.OVERWORLD.equals(dim) || Dims.NETHER.equals(dim))) {
			return false;
		}
		LAST_SEEN.put(p.getUUID(), (long) WorldFx.server().getTickCount());
		return "swarm".equals(why) ? swarm(p) : summon(p, "hunt");
	}

	/**
	 * Extreme: the tribe comes for one player all at once. Lurkers from every side, come to hurt (glowing red) and hunting
	 * until the swarm is over, and the young with them, biting; each other player near adds a Lurker and two young. A
	 * shriek and a line in the chat tell it. False if none could come (no ground near enough that they fit on).
	 */
	public static boolean swarm(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		int others = 0;
		for (ServerPlayer o : level.players()) {
			others += o != p && !o.isSpectator() && !o.isCreative() && o.distanceToSqr(p) < 24 * 24 ? 1 : 0;
		}
		int lurkers = Math.max(0, Config.LURKER_SWARM_LURKERS) + others;
		int young = Math.max(0, Config.LURKER_SWARM_HOLLOWLINGS) + 2 * others;
		int seconds = Math.max(5, Config.LURKER_SWARM_SECONDS);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double base = rnd.nextDouble() * Math.PI * 2;
		int came = 0;
		for (int i = 0; i < lurkers; i++) {
			BlockPos spot = null;
			for (int t = 0; t < 8 && spot == null; t++) {
				double a = base + (i + rnd.nextDouble() * 0.5) * Math.PI * 2 / Math.max(1, lurkers);
				double r = 18 + rnd.nextDouble() * 10;
				int x = (int) Math.floor(p.getX() + Math.cos(a) * r);
				int z = (int) Math.floor(p.getZ() + Math.sin(a) * r);
				if (Loaded.ready(level, new BlockPos(x, (int) p.getY(), z))) {
					spot = TribeMoves.surface(level, x, z, (int) p.getY() + 12, BODY_WIDTH, BODY_HEIGHT);
				}
			}
			if (spot == null) {
				continue;
			}
			State st = new State();
			st.target = p.getUUID();
			st.swarm = true;
			Lurker l = spawn(level, spot, st);
			if (l != null) {
				hunt(l, st, p);
				st.limit = seconds * 20;
				came++;
			}
		}
		came += HollowService.swarm(level, p, young, seconds).size();
		if (came == 0) {
			return false;
		}
		p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.extremedimensions.tribe.swarm")
				.withStyle(net.minecraft.ChatFormatting.DARK_RED));
		WorldFx.soundIn(Dims.fullId(p), "extremedimensions:lurker_shriek", "hostile", p.getX(), p.getY() + 1, p.getZ(), 2.0f, 0.5f);
		WorldFx.effect(p, net.minecraft.world.effect.MobEffects.DARKNESS, 3, 0);
		return true;
	}

	/** For the tests: how many Lurkers of a swarm hunt this player right now. */
	public static int swarming(ServerPlayer p) {
		int n = 0;
		for (State st : STATES.values()) {
			n += st.swarm && p.getUUID().equals(st.target) ? 1 : 0;
		}
		return n;
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
			// On the ground with room for its height, never up in a tree it could not climb down from (nor in water)
			BlockPos c = TribeMoves.surface(level, x, z, (int) p.getY() + 16, BODY_WIDTH, BODY_HEIGHT);
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

	/** Scans down from {@code top} for safe ground with room for a Lurker's whole body. */
	private static BlockPos standable(ServerLevel level, int x, int top, int z, int depth) {
		for (int y = top; y > top - depth; y--) {
			BlockPos feet = new BlockPos(x, y, z);
			if (TribeMoves.roomFor(level, feet, BODY_WIDTH, BODY_HEIGHT)) {
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
				l.setLurkerMood(st.mode == Mode.STRIKE ? LurkerBody.MOOD_HUNTER : LurkerBody.MOOD_THIEF);
			}
			ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
			if (p == null || p.level() != l.level() || p.distanceToSqr(l) > 112 * 112 || p.isDeadOrDying() || p.isCreative() || p.isSpectator()) {
				later.add(() -> vanish(l));
				continue;
			}
			switch (st.mode) {
				case WATCH -> watch(l, st, p, later);
				case STALK -> stalk(l, st, p, later);
				case TAKE -> take(l, st, p);
				case FLEE -> flee(l, st, p, later);
				case DRAIN -> drain(l, st, p, later);
				case STRIKE -> strike(l, st, p);
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
		// it turns its head slowly after you, never with a jerk
		l.getLookControl().setLookAt(p, 10, 10);
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
				if (!kin(p) && ThreadLocalRandom.current().nextDouble() < Config.LURKER_HUNT_CHANCE) {
					// It came to hurt, not to steal: the dread swells as it turns
					com.extremedimensions.world.AmbientService.dread(p);
					hunt(l, st, p);
				} else {
					st.mode = Mode.STALK;
					l.setLurkerPose(Lurker.POSE_STALK);
					l.setLurkerMood(LurkerBody.MOOD_THIEF);
					st.ticks = 0;
					st.limit = 20 * 90;
					st.startDist = dist;
					whisper(l, p, 0.7f);
				}
			} else if (seamHunts(p)) {
				// In the Seam it never steals; but where a visitor's thread runs thin, it may come to hurt
				com.extremedimensions.world.AmbientService.dread(p);
				hunt(l, st, p);
			} else {
				later.add(() -> {
					WorldService.offering((ServerLevel) l.level(), l.getX(), l.getY(), l.getZ());
					vanish(l);
				});
			}
		}
	}

	/**
	 * Whether a Lurker that watched a visitor in the Seam turns to hunt them: only once their thread runs thin, not for
	 * its kin, and at {@code lurker_seam_hunt_chance}.
	 */
	private static boolean seamHunts(ServerPlayer p) {
		return Dims.SEAM.equals(Dims.of(p)) && WorldService.tribeSteals() && !kin(p) && com.extremedimensions.seam.SeamFauna.thin(p)
				&& ThreadLocalRandom.current().nextDouble() < Config.LURKER_SEAM_HUNT_CHANCE;
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
		if (st.ticks % BLOCK_CHECK == 0) {
			st.blocked = TribeMoves.blocked(l, p) ? st.blocked + BLOCK_CHECK : 0;
		}
		if (st.blocked >= blockedAfter()) {
			// Its prey is somewhere it cannot follow (a house too low for it): it stands, and sends its young in
			holdOut(l, st, p, false);
			if (st.mode == Mode.STALK && st.ticks > st.limit) {
				later.add(() -> vanish(l));
			}
			return;
		}
		if (seen(p, l, 0.9)) {
			l.getNavigation().stop();
			l.getLookControl().setLookAt(p, 30, 30);
		} else if (st.ticks % 5 == 0) {
			double close = 1.0 - Math.max(0.0, Math.min(1.0, (dist - 3.0) / Math.max(1.0, st.startDist - 3.0)));
			double speed = Config.LURKER_STALK_SPEED_MIN + (Config.LURKER_STALK_SPEED_MAX - Config.LURKER_STALK_SPEED_MIN) * close * close;
			TribeMoves.follow(l, p, speed);
		}
		double reach = takeReach();
		// (never through a wall: it reaches for what it can see)
		if (reach > 0 && p.distanceToSqr(l) < reach * reach && l.hasLineOfSight(p)) {
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
			if (com.extremedimensions.binding.Charms.soulward(p)) {
				// Soulward: it flinches from the helmet's soul light before its arm can close, and runs empty-handed
				WorldFx.soundIn(dim, "extremedimensions:lurker_shriek", "hostile", l.getX(), l.getY() + 2.5, l.getZ(), 1.0f, 1.4f);
				WorldFx.particles(p, "extremedimensions:soul_mote", 1.2, 0.5, 0.0, 16);
				run(l, st, p, true);
				return;
			}
			if (com.extremedimensions.tribe.Standing.spared(p)) {
				// Kin of the tribe: it looks at them a long moment, and lets them be
				p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.extremedimensions.tribe.spared"), true);
				run(l, st, p, true);
				return;
			}
			// Its claw rakes the player as it reaches; then it takes what it can, and may leave a curse behind
			hurt(l, p, Config.LURKER_CLAW_DAMAGE, false);
			boolean took = false;
			int had = st.loot.stacks.size(), hadLevels = st.loot.levels;
			for (int i = 0; i < Math.max(1, Config.LURKER_THEFTS); i++) {
				if (!snatch(l, st, p)) {
					break;
				}
				took = true;
			}
			if (took) {
				taken(p);
				// What this reach took, to be told as it runs (when it knows where to)
				for (int i = had; i < st.loot.stacks.size(); i++) {
					st.untold.add(st.loot.stacks.get(i).copy());
				}
				st.untoldLevels += Math.max(0, st.loot.levels - hadLevels);
			}
			curseChance(l, st, p);
		}
		if (st.ticks >= TAKE_TICKS) {
			run(l, st, p, false);
		}
	}

	/** Takes one thing from the player, by weight among what reaches them now; false when nothing could be taken. */
	private static boolean snatch(Lurker l, State st, ServerPlayer p) {
		st.loot.from = p.getUUID();
		double d = Math.sqrt(p.distanceToSqr(l));
		// Only what reaches the player right now (and is possible) can be picked, by weight
		List<Integer> kinds = new ArrayList<>();
		boolean tooth = com.extremedimensions.relic.RelicService.has(p, com.extremedimensions.relic.Relic.LURKERS_TOOTH);
		// Covetous: it came for the hotbar, and takes from it if it can
		if (!tooth && inReach(0, d) && com.extremedimensions.penalty.AbyssalTolls.covets(p) && stealItem(p, st.loot)) {
			return true;
		}
		for (int k = 0; k < 4; k++) {
			// Lurker's Tooth: no item and no armour can be taken from its bearer; and a curse counts towards the
			// curses one encounter may leave
			if (!(tooth && (k == 0 || k == 2)) && !(k == 3 && st.curses >= Math.max(1, Config.LURKER_CURSES_MAX))) {
				kinds.add(k);
			}
		}
		while (!kinds.isEmpty()) {
			double total = 0;
			for (int k : kinds) {
				total += inReach(k, d) ? Math.max(0, weight(k)) : 0;
			}
			if (total <= 0) {
				return false;
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
				default -> PenaltyService.curse(p, Dims.OVERWORLD.equals(Dims.of(p)) && DataStore.overworldPenalties(),
						Config.LURKER_CURSE_EXTREME_CHANCE, com.extremedimensions.penalty.CurseLog.Source.LURKER) != null;
			};
			if (took && pick == 3) {
				st.curses++;
			}
			if (took) {
				return true;
			}
			kinds.remove(Integer.valueOf(pick));
		}
		return false;
	}

	/** Something was taken: the theft shows. */
	private static void taken(ServerPlayer p) {
		WorldFx.particles(p, "minecraft:sculk_soul", 1.0, 0.5, 0.08, 25);
		WorldFx.particles(p, "extremedimensions:lurker_wisp", 1.0, 0.5, 0.0, 12);
		WorldFx.particles(p, "minecraft:reverse_portal", 1.0, 0.8, 0.3, 40);
		WorldFx.fx(p, Fx.TAKEN, 0);
		Advancements.grant(p, Advancements.ROBBED);
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
		// (under Covetous only the hotbar, and what it takes carries the tribe's mark)
		boolean covetous = com.extremedimensions.penalty.AbyssalTolls.covets(p);
		for (int i = 0; i < (covetous ? 9 : 36); i++) {
			if (!inv.getItem(i).isEmpty()) {
				slots.add(i);
			}
		}
		if (slots.isEmpty()) {
			return false;
		}
		int slot = slots.get(ThreadLocalRandom.current().nextInt(slots.size()));
		ItemStack stolen = inv.getItem(slot).copy();
		inv.setItem(slot, ItemStack.EMPTY);
		if (covetous) {
			com.extremedimensions.penalty.AbyssalTolls.mark(p, stolen);
		}
		loot.stacks.add(stolen);
		return true;
	}

	private static boolean stealXp(ServerPlayer p, Loot loot) {
		if (p.experienceLevel < 3) {
			return false;
		}
		int levels = Math.max(3, (int) Math.round(p.experienceLevel * Config.LURKER_XP_FRACTION));
		p.giveExperienceLevels(-levels);
		loot.levels += levels;
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
		loot.stacks.add(p.getItemBySlot(s).copy());
		p.setItemSlot(s, ItemStack.EMPTY);
		return true;
	}

	/** Breaks into a run, away from the player. */
	private static void run(Lurker l, State st, ServerPlayer p, boolean struck) {
		st.mode = Mode.FLEE;
		st.ticks = 0;
		st.limit = Math.max(3, Config.LURKER_RUN_SECONDS) * 20;
		l.setLurkerPose(Lurker.POSE_RUN);
		l.setLurkerMood(LurkerBody.MOOD_CALM);
		st.away = null;
		st.windup = 0;
		String dim = l.level().dimension().identifier().toString();
		String sound = struck ? "extremedimensions:lurker_shriek" : "extremedimensions:lurker_flee";
		WorldFx.soundIn(dim, sound, "hostile", l.getX(), l.getY() + 2.5, l.getZ(), 1.6f, 1f);
		if (st.loot.any()) {
			// It runs home to its hoard (struck or not): follow it, and you find where your things went
			st.hoard = HollowService.hoardFor(p, l.position());
			if (st.hoard != null) {
				st.limit = 20 * 25;
			}
		}
		tellTheft(l, st, p);
		flee(l, st, p, new ArrayList<>());
	}

	/**
	 * The player is told what the Lurker took and which way it ran (3.55.0): "The Lurker took your [Diamond Pickaxe] and
	 * ran north-east, to its hoard", each thing taken shown on hover. Before, only the screen's flicker said that
	 * something had gone, and not what.
	 */
	private static void tellTheft(Lurker l, State st, ServerPlayer p) {
		if (st.untold.isEmpty() && st.untoldLevels <= 0) {
			return;
		}
		MutableComponent what = Component.empty();
		boolean first = true;
		for (ItemStack stack : st.untold) {
			// (the item's own name, which shows the item on hover)
			Component name = stack.getDisplayName();
			what.append(first ? "" : ", ").append(stack.getCount() > 1
					? Component.translatable("gui.extremedimensions.lurker.took_count", stack.getCount(), name) : name);
			first = false;
		}
		if (st.untoldLevels > 0) {
			what.append(first ? "" : ", ").append(Component.translatable("gui.extremedimensions.lurker.took_levels",
					st.untoldLevels));
		}
		// Which way: towards its hoard where it runs to one, else on away from the player
		double dx = st.hoard != null ? st.hoard.x + 0.5 - p.getX() : l.getX() - p.getX();
		double dz = st.hoard != null ? st.hoard.z + 0.5 - p.getZ() : l.getZ() - p.getZ();
		String bearing = com.extremedimensions.seam.FirstKnot.bearing(dx, dz);
		p.sendSystemMessage(Component.translatable(st.hoard != null ? "gui.extremedimensions.lurker.took_hoard"
				: "gui.extremedimensions.lurker.took", what, Component.translatable("gui.extremedimensions.dir." + bearing))
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		lastTheft = new Told(p.getUUID(), new Theft(List.copyOf(st.untold), st.untoldLevels, bearing, st.hoard != null));
		st.untold.clear();
		st.untoldLevels = 0;
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
				TribeMoves.go(l, home.x, home.y, home.z, speed);
			}
			if (st.ticks % 3 == 0) {
				WorldFx.particleIn(l.level().dimension().identifier().toString(), "extremedimensions:lurker_wisp",
						l.getX(), l.getY() + 1.4, l.getZ(), 0.35, 0.5, 0.35, 0.0, 2);
			}
			if (l.position().distanceToSqr(home) < 3.5 * 3.5 || st.ticks > st.limit) {
				Hoard h = st.hoard;
				List<ItemStack> stacks = new ArrayList<>(st.loot.stacks);
				int levels = st.loot.levels;
				st.loot.stacks.clear();
				st.loot.levels = 0;
				st.hoard = null;
				later.add(() -> {
					HollowService.stash(h, stacks, levels);
					vanish(l);
				});
			}
			return;
		}
		if (st.ticks % 8 == 0 || l.getNavigation().isDone()) {
			double ramp = Math.min(1.0, st.ticks / 40.0);
			double speed = Config.LURKER_RUN_SPEED * (0.6 + 0.4 * ramp);
			// One spot to run to, away from the player over safe ground, kept until it is reached (a goal that kept
			// shifting made it turn back and forth)
			if (st.away == null || l.position().distanceToSqr(st.away) < 3.0 * 3.0 || l.getNavigation().isDone()) {
				st.away = TribeMoves.away(l, p.position(), 22);
			}
			if (st.away != null) {
				TribeMoves.go(l, st.away.x, st.away.y, st.away.z, speed);
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

	/**
	 * What a Lurker drinks from an anchor in a tick, come for this player: twice as much from one holding the charge of
	 * the Anchor Rivet's bearer (see RelicService).
	 */
	public static double sip(ServerPlayer p, Anchor a) {
		return Config.LURKER_DRAIN_PER_SECOND / 20.0 * com.extremedimensions.relic.RelicService.drink(p, a);
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
		a.leak(sip(p, a));

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
		if (st != null && st.loot.any() && l.level() instanceof ServerLevel level) {
			// What it took is never simply gone (its victim left, it was cut off, the server stopped): it ends up in the
			// hoard it would have run to, or falls where it stood
			Hoard h = st.hoard;
			ServerPlayer from = st.loot.from == null || WorldFx.server() == null ? null : WorldFx.server().getPlayerList().getPlayer(st.loot.from);
			if (h == null && from != null && from.level() == level) {
				h = HollowService.hoardFor(from, l.position());
			}
			if (h != null) {
				HollowService.stash(h, st.loot.stacks, st.loot.levels);
			} else {
				for (ItemStack stack : st.loot.stacks) {
					l.spawnAtLocation(level, stack);
				}
			}
			st.loot.stacks.clear();
			st.loot.levels = 0;
		}
		l.discard();
	}

	/**
	 * Struck: it shrieks and runs, or (by chance) turns on its attacker. Already running, it keeps going; already
	 * hunting, it keeps at it until it is badly hurt.
	 */
	public static void onHurt(Lurker l, net.minecraft.world.damagesource.DamageSource source) {
		State st = STATES.get(l.getUUID());
		// Only a creature's blow makes it flee or turn: a bush, a burn or a drop does not
		if (st == null || st.mode == Mode.FLEE || !TribeMoves.struck(source)) {
			return;
		}
		ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
		if (p == null) {
			vanish(l);
			return;
		}
		if (st.mode == Mode.STRIKE) {
			if (l.getHealth() < l.getMaxHealth() * 0.35) {
				run(l, st, p, true);
			}
			return;
		}
		if (!kin(p) && WorldService.tribeSteals() && !Dims.SEAM.equals(Dims.of(p)) && ThreadLocalRandom.current().nextDouble() < Config.LURKER_RETALIATE_CHANCE) {
			hunt(l, st, p);
			return;
		}
		run(l, st, p, true);
	}

	/** Kin of the tribe: no Lurker ever comes to hurt them. */
	private static boolean kin(ServerPlayer p) {
		return com.extremedimensions.tribe.Standing.of(p) == com.extremedimensions.tribe.Standing.Level.KIN;
	}

	/** It comes to hurt: its glow turns red, it shrieks, and it runs at the player openly. */
	private static void hunt(Lurker l, State st, ServerPlayer p) {
		st.mode = Mode.STRIKE;
		st.ticks = 0;
		st.limit = Math.max(5, Config.LURKER_STRIKE_SECONDS) * 20;
		st.blows = 0;
		st.windup = 0;
		st.pause = 10;
		l.setLurkerPose(Lurker.POSE_STALK);
		l.setLurkerMood(LurkerBody.MOOD_HUNTER);
		WorldFx.soundIn(Dims.fullId(p), "extremedimensions:lurker_shriek", "hostile", l.getX(), l.getY() + 2.5, l.getZ(), 1.6f, 0.7f);
		whisper(l, p, 0.9f);
	}

	/**
	 * A Lurker come to hurt: it runs at the player and, once near, rears up for a blow (a short wind-up, red motes at its
	 * hands, a low shriek) that lands if the player is still within reach. After its blows, or badly hurt, or when it
	 * has hunted long enough, it runs.
	 */
	private static void strike(Lurker l, State st, ServerPlayer p) {
		l.getLookControl().setLookAt(p, 60, 60);
		double dx = p.getX() - l.getX(), dz = p.getZ() - l.getZ();
		double reach = Math.sqrt(dx * dx + dz * dz);
		boolean even = Math.abs(p.getY() - l.getY()) < 2.5;
		if (st.windup > 0) {
			l.getNavigation().stop();
			if (st.windup % 3 == 0) {
				double yaw = Math.toRadians(l.getYRot());
				WorldFx.particleIn(Dims.fullId(p), "minecraft:dust{color:[1.0,0.16,0.1],scale:1.1}", l.getX() - Math.sin(yaw) * 0.9, l.getY() + 2.6,
							l.getZ() + Math.cos(yaw) * 0.9, 0.35, 0.35, 0.35, 0.0, 6);
			}
			if (--st.windup > 0) {
				return;
			}
			if (st.reachIn) {
				// It reaches in after a prey it cannot get at (a hole, a pillar, a run kept at a distance) and drags them out
				st.reachIn = false;
				st.unreach = 0;
				if (l.distanceTo(p) <= REACH_IN_RANGE && l.hasLineOfSight(p) && hurt(l, p, Config.LURKER_STRIKE_DAMAGE, false)) {
					TribeMoves.yank(l, p);
					st.blows++;
					curseChance(l, st, p);
				}
				l.setLurkerPose(Lurker.POSE_STALK);
				st.pause = STRIKE_PAUSE;
				return;
			}
			// The blow falls: it lands on whoever is still within reach of its arms (a step back, a shield, and it misses)
			if (reach <= STRIKE_REACH + 1.0 && even && l.hasLineOfSight(p)) {
				if (HollowService.guarded(p) || com.extremedimensions.binding.Charms.soulward(p)) {
					// A Hollowling friend leaps in, or the helmet's soul light flares: it flinches and runs
					WorldFx.particles(p, "extremedimensions:soul_mote", 1.2, 0.5, 0.0, 16);
					run(l, st, p, true);
					return;
				}
				if (hurt(l, p, Config.LURKER_STRIKE_DAMAGE, true)) {
					st.blows++;
					curseChance(l, st, p);
				}
			}
			l.setLurkerPose(Lurker.POSE_STALK);
			st.pause = STRIKE_PAUSE;
			// (one of a swarm hunts until the swarm is over)
			if (!st.swarm && st.blows >= Math.max(1, Config.LURKER_STRIKE_BLOWS)) {
				run(l, st, p, false);
			}
			return;
		}
		if (st.ticks > st.limit || l.getHealth() < l.getMaxHealth() * 0.35) {
			run(l, st, p, true);
			return;
		}
		if (st.ticks % BLOCK_CHECK == 0) {
			st.blocked = TribeMoves.blocked(l, p) ? st.blocked + BLOCK_CHECK : 0;
			if (st.blocked == 0) {
				st.sightFrom = null;
				st.sightTicks = 0;
			}
		}
		if (st.boltWindup > 0 || (st.blocked >= blockedAfter() && TribeMoves.sheltered(l.level(), p.blockPosition()))) {
			// Under a roof it cannot get under: an ink bolt when it sees its prey, its young when it does not
			holdOut(l, st, p, true);
			return;
		}
		if (st.pause > 0) {
			st.pause--;
		}
		boolean near = reach <= STRIKE_REACH && even;
		if (st.pause <= 0 && near && l.hasLineOfSight(p)) {
			// Rears up: the player sees the blow coming
			l.getNavigation().stop();
			l.setLurkerPose(Lurker.POSE_TAKE);
			st.windup = STRIKE_WINDUP;
			WorldFx.soundIn(Dims.fullId(p), "extremedimensions:lurker_shriek", "hostile", l.getX(), l.getY() + 2.5, l.getZ(), 1.2f, 0.6f);
			return;
		}
		// Its prey in sight but no nearer for a while (in a hole too low for it, up a pillar, a run kept at a distance)
		st.unreach = TribeMoves.outOfReach(l, p, near, REACH_IN_RANGE, st.unreach);
		if (st.pause <= 0 && st.unreach >= REACH_IN_AFTER) {
			l.getNavigation().stop();
			l.setLurkerPose(Lurker.POSE_TAKE);
			st.windup = STRIKE_WINDUP + 6;
			st.reachIn = true;
			TribeMoves.reachLine(l, p, "minecraft:dust{color:[1.0,0.16,0.1],scale:0.9}");
			WorldFx.soundIn(Dims.fullId(p), "extremedimensions:lurker_shriek", "hostile", l.getX(), l.getY() + 2.5, l.getZ(), 1.4f, 0.5f);
			return;
		}
		if (st.ticks % 4 == 0) {
			TribeMoves.follow(l, p, Config.LURKER_STRIKE_SPEED);
		}
		if (st.ticks % 4 == 0) {
			WorldFx.particleIn(Dims.fullId(p), "minecraft:dust{color:[0.9,0.1,0.08],scale:0.8}", l.getX(), l.getY() + 1.6, l.getZ(), 0.4, 0.8, 0.4, 0.0, 2);
		}
	}

	private static int blockedAfter() {
		return Math.max(1, Config.LURKER_BLOCKED_SECONDS) * 20;
	}

	/**
	 * Its prey hides where it cannot follow. It stops and stands, facing them, rather than circling the house. Come to
	 * hurt and in sight of them (a window, a door), it throws ink bolts; out of sight, or come to steal, it sends two
	 * of its young in after them: to bite, or to bring it what they take. Once the young bring it something, it runs.
	 */
	private static void holdOut(Lurker l, State st, ServerPlayer p, boolean hunter) {
		l.getLookControl().setLookAt(p, 12, 12);
		if (st.boltIn > 0) {
			st.boltIn--;
		}
		if (st.boltWindup > 0) {
			stand(l);
			bolt(l, st, p);
			return;
		}
		boolean bolts = hunter && Config.LURKER_BOLT_DAMAGE > 0;
		if (bolts && l.hasLineOfSight(p) && l.distanceTo(p) <= BOLT_RANGE) {
			stand(l);
			st.sightFrom = null;
			if (st.boltIn <= 0) {
				st.boltWindup = BOLT_WINDUP;
				l.setLurkerPose(Lurker.POSE_TAKE);
				WorldFx.soundIn(Dims.fullId(p), "extremedimensions:lurker_whisper", "hostile", l.getX(), l.getY() + 2.5, l.getZ(), 1.6f, 0.5f);
			}
			return;
		}
		if (bolts && st.sightTicks < SIGHT_GIVE_UP && l.level() instanceof ServerLevel level) {
			// Out of its sight: it goes round to where it would see them (the door, a window) instead of circling the house
			if (st.sightFrom == null ? st.ticks % 20 == 0 : st.ticks % 40 == 0) {
				st.sightFrom = TribeMoves.sightFrom(level, l, p, SIGHT_RADIUS);
			}
			if (st.sightFrom != null) {
				st.sightTicks++;
				Vec3 spot = Vec3.atBottomCenterOf(st.sightFrom);
				double dx = spot.x - l.getX(), dz = spot.z - l.getZ();
				double flat = Math.sqrt(dx * dx + dz * dz);
				if (flat < 2.5 && TribeMoves.clear(level, l, spot)) {
					// the last steps straight onto the spot (a route would stop it a little off, behind the wall's edge)
					l.getNavigation().stop();
					if (flat > 0.3) {
						l.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, Config.LURKER_STRIKE_SPEED);
					} else {
						l.getMoveControl().setWait();
					}
				} else if (st.ticks % 5 == 0) {
					TribeMoves.go(l, spot.x, spot.y, spot.z, Config.LURKER_STRIKE_SPEED);
				}
				return;
			}
		}
		stand(l);
		// (no room beside it for them this time, against a wall: it calls again a moment later)
		if (!st.sentYoung && Config.LURKER_YOUNG > 0 && st.ticks % 10 == 0) {
			st.sentYoung = sendYoung(l, st, p, hunter);
		}
		if (!hunter && st.loot.any()) {
			run(l, st, p, false);
		}
	}

	/** It stops where it is and stands, facing what it is after. */
	private static void stand(Lurker l) {
		l.getNavigation().stop();
		l.getMoveControl().setWait();
	}

	/** The bolt gathers in its hand as ink drawn out of the air, then flies; it lands only on a prey still in its line. */
	private static void bolt(Lurker l, State st, ServerPlayer p) {
		double yaw = Math.toRadians(l.getYRot());
		if (st.boltWindup % 3 == 0) {
			WorldFx.particleIn(Dims.fullId(p), "minecraft:squid_ink", l.getX() - Math.sin(yaw) * 0.9, l.getY() + 2.4, l.getZ() + Math.cos(yaw) * 0.9,
					0.2, 0.2, 0.2, 0.01, 5);
		}
		if (--st.boltWindup > 0) {
			return;
		}
		l.setLurkerPose(Lurker.POSE_STALK);
		st.boltIn = Math.max(1, Config.LURKER_BOLT_SECONDS) * 20;
		TribeMoves.reachLine(l, p, "minecraft:squid_ink");
		WorldFx.soundIn(Dims.fullId(p), "extremedimensions:lurker_shriek", "hostile", l.getX(), l.getY() + 2.5, l.getZ(), 1.2f, 1.4f);
		// (a step behind a wall, and it splashes on the stone)
		if (l.hasLineOfSight(p) && l.distanceTo(p) <= BOLT_RANGE + 2 && hurt(l, p, Config.LURKER_BOLT_DAMAGE, false)) {
			WorldFx.particles(p, "minecraft:squid_ink", 0.6, 0.6, 0.05, 14);
			st.blows++;
			curseChance(l, st, p);
			if (!st.swarm && st.blows >= Math.max(1, Config.LURKER_STRIKE_BLOWS)) {
				run(l, st, p, false);
			}
		}
	}

	/** It calls its young out of the dark to do what it cannot: two of them, from where it stands. False if none came. */
	private static boolean sendYoung(Lurker l, State st, ServerPlayer p, boolean bite) {
		List<UUID> sent = HollowService.errand((ServerLevel) l.level(), l.blockPosition(), p, l.getUUID(), bite, Config.LURKER_YOUNG, Config.LURKER_YOUNG_SECONDS);
		if (sent.isEmpty()) {
			return false;
		}
		st.limit = Math.max(st.limit, st.ticks + (Math.max(5, Config.LURKER_YOUNG_SECONDS) + 10) * 20);
		WorldFx.soundIn(Dims.fullId(p), "extremedimensions:lurker_shriek", "hostile", l.getX(), l.getY() + 2.5, l.getZ(), 1.0f, 1.7f);
		WorldFx.soundIn(Dims.fullId(p), "extremedimensions:hollowling_giggle", "hostile", l.getX(), l.getY() + 0.8, l.getZ(), 1.0f, 0.8f);
		return true;
	}

	/** One of its young brings it what it took from its prey. False when this Lurker is no longer about to take it. */
	public static boolean receive(UUID lurker, ItemStack stack, UUID from) {
		State st = STATES.get(lurker);
		if (st == null || stack.isEmpty()) {
			return false;
		}
		st.loot.stacks.add(stack);
		st.loot.from = from;
		return true;
	}

	/** For the tests: whether this Lurker's prey is out of its way, and whether it sent its young. */
	public static boolean holdingOut(Lurker l) {
		State st = STATES.get(l.getUUID());
		return st != null && st.blocked >= blockedAfter();
	}

	/** For the tests: what a driven Lurker holding out is doing, in a line. */
	public static String holdOutState(Lurker l) {
		State st = STATES.get(l.getUUID());
		if (st == null) {
			return "not driven";
		}
		ServerPlayer p = WorldFx.server().getPlayerList().getPlayer(st.target);
		return String.format(Locale.ROOT, "%s at %.1f %.1f %.1f, blocked %d, sight from %s for %d, bolt in %d, winding %d, sees %s",
				st.mode, l.getX(), l.getY(), l.getZ(), st.blocked, st.sightFrom, st.sightTicks, st.boltIn, st.boltWindup,
				p != null && l.hasLineOfSight(p));
	}

	public static boolean sentYoung(Lurker l) {
		State st = STATES.get(l.getUUID());
		return st != null && st.sentYoung;
	}

	/**
	 * A blow from a Lurker (its claw as it reaches, or a strike), as a monster's attack: armour, a raised shield and the
	 * game's difficulty all count. A strike throws the player back a little further. True when it hurt them.
	 */
	private static boolean hurt(Lurker l, ServerPlayer p, double damage, boolean strike) {
		if (damage <= 0 || !(l.level() instanceof ServerLevel level)) {
			return false;
		}
		boolean hit = p.hurtServer(level, l.damageSources().mobAttack(l), (float) damage);
		if (hit && strike) {
			double dx = p.getX() - l.getX(), dz = p.getZ() - l.getZ();
			double d = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
			p.setDeltaMovement(dx / d * 0.55, 0.32, dz / d * 0.55);
			com.extremedimensions.server.WorldFx.moved(p);
		}
		if (hit) {
			WorldFx.soundIn(Dims.fullId(p), "minecraft:entity.warden.attack_impact", "hostile", p.getX(), p.getY() + 1, p.getZ(), 0.7f, 1.3f);
		}
		return hit;
	}

	/**
	 * Its touch may leave a curse as well (as many as an encounter may leave): from the Extreme set by chance, where the
	 * world's difficulty allows it.
	 */
	private static void curseChance(Lurker l, State st, ServerPlayer p) {
		if (st.curses >= Config.LURKER_CURSES_MAX || ThreadLocalRandom.current().nextDouble() >= Config.LURKER_CURSE_CHANCE) {
			return;
		}
		boolean overworld = Dims.OVERWORLD.equals(Dims.of(p)) && DataStore.overworldPenalties();
		if (PenaltyService.curse(p, overworld, Config.LURKER_CURSE_EXTREME_CHANCE, com.extremedimensions.penalty.CurseLog.Source.LURKER) != null) {
			st.curses++;
			WorldFx.particles(p, "minecraft:sculk_soul", 1.0, 0.6, 0.06, 20);
			WorldFx.soundFor(p, "extremedimensions:lurker_whisper", "hostile", l.getX(), l.getY() + 2, l.getZ(), 1.0f, 0.7f);
		}
	}

	/** Slain while fleeing: what it took falls back into the world. */
	public static void onDeath(Lurker l, Entity killer) {
		State st = STATES.remove(l.getUUID());
		LEVELS.remove(l.getUUID());
		if (st == null) {
			return;
		}
		ServerLevel level = (ServerLevel) l.level();
		for (ItemStack stack : st.loot.stacks) {
			l.spawnAtLocation(level, stack);
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
			case "swarm" -> swarm(p);
			case "stalk", "hunt" -> {
				if (!spawnWatch(p)) {
					yield false;
				}
				boolean hunt = mode.equals("hunt");
				for (State st : STATES.values()) {
					if (p.getUUID().equals(st.target) && st.mode == Mode.WATCH) {
						st.mode = hunt ? Mode.STRIKE : Mode.STALK;
						st.ticks = 0;
						st.stalkPose = true;
						st.limit = hunt ? Math.max(5, Config.LURKER_STRIKE_SECONDS) * 20 : 20 * 90;
					}
				}
				yield true;
			}
			default -> spawnWatch(p);
		};
	}

	/**
	 * For the tests: drives a Lurker already in the world after this player, in the given mode ("take" reaches for them at
	 * once, "hunt" comes to hurt, "stalk" creeps in, "flee" runs).
	 */
	public static void adopt(ServerLevel level, Lurker l, ServerPlayer p, String mode) {
		State st = new State();
		st.target = p.getUUID();
		st.mode = Mode.WATCH;
		st.limit = 20 * 60;
		STATES.put(l.getUUID(), st);
		LEVELS.put(l.getUUID(), level);
		switch (mode) {
			case "take" -> {
				st.mode = Mode.TAKE;
				l.setLurkerPose(Lurker.POSE_TAKE);
				l.setLurkerMood(LurkerBody.MOOD_THIEF);
			}
			case "hunt" -> hunt(l, st, p);
			case "stalk" -> {
				st.mode = Mode.STALK;
				st.limit = 20 * 90;
				l.setLurkerPose(Lurker.POSE_STALK);
				l.setLurkerMood(LurkerBody.MOOD_THIEF);
			}
			case "flee" -> run(l, st, p, true);
			default -> { }
		}
	}

	/** For the tests: the mode a driven Lurker is in ("" if it is not driven), and the curses and blows it dealt. */
	public static String mode(Lurker l) {
		State st = STATES.get(l.getUUID());
		return st == null ? "" : st.mode.name().toLowerCase(Locale.ROOT);
	}

	public static int blows(Lurker l) {
		State st = STATES.get(l.getUUID());
		return st == null ? 0 : st.blows;
	}

	/** For the tests: what a driven Lurker carries (items, then levels as a count of their own). */
	public static int carried(Lurker l) {
		State st = STATES.get(l.getUUID());
		return st == null ? 0 : st.loot.stacks.size();
	}
}
