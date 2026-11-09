package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.events.PaleSquall;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.lore.Loot;
import com.extremedimensions.mixin.DisplayAccess;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.seam.SeamFauna;
import com.extremedimensions.server.BlockFlight;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;
import com.mojang.math.Transformation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The Last Weaver: once the hunger at the Heart is ended, the weave stirs. The first visit to a village of the Thinned
 * may wake its Weaver. It floats above the ground and keeps its distance; it casts threads that hold players to the
 * spot they stood on (and drain the Seam faster while they hold), rains needles of light, flings back anyone who comes
 * too close, sweeps the ground with a beam from its halo that carves it, strings a web of threads across the ground
 * that cuts when it is pulled taut, and brings down spirals of needles with a great needle last of all. Since 3.43 it
 * also tears at the world: a grid of threads falls on its prey's ground, cutting whoever stands on a line (Warp and
 * Weft); three tornadoes of thread wander after the nearest players, drawing them in and tearing up the ground
 * (Spindle Storm); and rows of great needles fall from over it, leaving spikes of calcite in the ground (The Loom Comes
 * Down). Those three are its own: the Knot Mother never casts them. Twice it calls its kin. At half its health it weaves the Last Tapestry: it rises, threads run from its hands to everyone near while
 * time slows for them and a dome of threads closes over the ground; then the threads snap, and its curse falls on
 * everyone within reach. Every hurt it does unpicks Seam time. Slain, it gives its thread back.
 */
public final class WeaverService {
	private WeaverService() {}

	private static final double HOVER = 2.5;
	private static final String BOSS = "boss.extremedimensions.weaver";
	private static final String ULT = "gui.extremedimensions.elder.ultimate.weaver";

	/** Its name on the boss bar and its ultimate's: the Knot Mother has her own. */
	private static String boss(Weaver w) {
		return w instanceof KnotMother ? "boss.extremedimensions.knot_mother" : BOSS;
	}

	private static String ult(Weaver w) {
		return w instanceof KnotMother ? "gui.extremedimensions.elder.ultimate.knot_mother" : ULT;
	}

	private static final class Unweaving extends ElderFight {
		/** The village whose first visit woke it ("wv@x,z"), given its roll back if the fight is left unfinished. */
		String village;
		/** How many times it has called its kin (at 60% and 30% health). */
		int kin;
		int drift = 1;
		double rise;
		/** Whether this Lance sweeps back once more after its first pass. */
		boolean back;
		/** Where the Knot Mother woke (her cords hold her near it); NaN for a Weaver, which goes where it likes. */
		double homeX = Double.NaN, homeZ = Double.NaN;
		/** The Knot Mother's anchors round her chamber, whether they have been tied yet, ticks left exposed, and when
		 * each player may strike an anchor again. */
		final List<Anchor> anchors = new ArrayList<>();
		boolean tied;
		int exposed;
		final Map<UUID, Integer> struck = new HashMap<>();
		/** Her Threadline: the bearing of its prey as it began, and which way it sweeps. */
		double lineA;
		int lineWay = 1;
		/**
		 * Warp and Weft (3.43): the middle of its grid (on a block's middle, so its lines run along rows of blocks), the
		 * ground it falls to, and whether it hangs there now (laid and not yet fallen).
		 */
		double warpX, warpY, warpZ;
		boolean warp;
		/** Spindle Storm (3.43): its tornadoes, and when each player was last cut by one (game tick), so they cut once in ten. */
		final List<Spindle> spindles = new ArrayList<>();
		final Map<UUID, Long> spun = new HashMap<>();
		/** The Loom Comes Down (3.43): its great needles still falling. */
		final List<Needle> needles = new ArrayList<>();

		/** Its blows land sooner than the other Elders' (the time saved goes into its pause; see {@link WeaverService#end}). */
		@Override
		double tempo() {
			return steady() * Math.max(0.5, Config.WEAVER_WINDUP_SPEED);
		}

		/** The pace its blows would have at the other Elders' speed. */
		double steady() {
			return super.tempo();
		}
	}

	private static final Map<UUID, Unweaving> WEAVERS = new HashMap<>();

	/** One of the Knot Mother's anchors: a pillar of crying obsidian topped by a node of amber light, and its hits. */
	private static final class Anchor {
		final BlockPos base;
		int hits;
		boolean broken;

		Anchor(BlockPos base) {
			this.base = base;
		}

		BlockPos node() {
			return base.above();
		}

		boolean holds(BlockPos p) {
			return p.equals(base) || p.equals(node());
		}

		boolean stands(ServerLevel level) {
			return !Loaded.ready(level, node()) || level.getBlockState(node()).is(Blocks.OCHRE_FROGLIGHT);
		}
	}

	/**
	 * A tornado of thread of its Spindle Storm: where its foot is on the ground now, where round the Weaver it rose, and
	 * the shreds whirling round it (wool, cobweb and calcite). Their displays stand where it rose; their look carries them
	 * round it wherever it has wandered.
	 */
	private static final class Spindle {
		double x, y, z;
		final double phase;
		final double ox, oy, oz;
		final List<Display.BlockDisplay> shreds = new ArrayList<>();
		/** Where it last tore up the ground (it rose there, tearing it). */
		double tornX, tornZ;

		Spindle(double x, double y, double z, double phase) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.phase = phase;
			this.tornX = x;
			this.tornZ = z;
			// (one block over the ground, so they are lit as the air there)
			this.ox = x;
			this.oy = y + 1;
			this.oz = z;
		}
	}

	/** A great needle of its Loom: where it lands, its display (null if the level took none), its row, and ticks left. */
	private static final class Needle {
		final double x, y, z;
		final Display.BlockDisplay display;
		final Quaternionf tilt;
		final int row;
		int left;
		/** Whether its fall has begun (the tick after it was laid, so the client sees it fall and not only where it ends). */
		boolean falling;

		Needle(double x, double y, double z, Display.BlockDisplay display, Quaternionf tilt, int row, int left) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.display = display;
			this.tilt = tilt;
			this.row = row;
			this.left = left;
		}
	}

	/** Seam marks of anchors still in the world ("ka@dim@x,y,z"), cleared when they are untied. */
	private static final String ANCHOR_MARK = "ka@";

	/** Its fight, if it is fighting. */
	static ElderFight fight(Entity e) {
		return WEAVERS.get(e.getUUID());
	}

	public static boolean isDriven(Entity e) {
		return WEAVERS.containsKey(e.getUUID());
	}

	/**
	 * Starts one of its blows (or its ultimate) at once; for tests and operators. False if it is not fighting, or for a
	 * blow of the Last Weaver's own asked of the Knot Mother (her body does not move into them).
	 */
	public static boolean force(Entity e, int action) {
		Unweaving fight = WEAVERS.get(e.getUUID());
		if (fight == null || !(e instanceof Weaver w)) {
			return false;
		}
		if (w instanceof KnotMother && (action == Weaver.WARP || action == Weaver.SPINDLE || action == Weaver.NEEDLEFALL)) {
			return false;
		}
		ElderSpawn.skip(w, fight);
		if (action == ElderBoss.ULTIMATE) {
			fight.rise = 0;
		}
		if (action == ElderBoss.ULTIMATE) {
			fight.beginUltimate(w, ult(w));
		} else {
			fight.start(w, action);
		}
		return true;
	}

	public static void clear() {
		for (Unweaving u : WEAVERS.values()) {
			u.bar.removeAllPlayers();
			untie(u);
			unspin(u);
		}
		WEAVERS.clear();
		WARNED_AT.clear();
		WARNED_FOR.clear();
	}

	public static boolean unlocked() {
		return switch (Config.WEAVER_UNLOCK) {
			case 0 -> true;
			case 1 -> DataStore.sealBroken();
			default -> DataStore.starvedSlain();
		};
	}

	/** Whether a Weaver is after this player, or near enough to set its kin on them. */
	public static boolean hunting(ServerPlayer p) {
		for (Map.Entry<UUID, Unweaving> e : WEAVERS.entrySet()) {
			if (p.getUUID().equals(e.getValue().target)) {
				return true;
			}
			Entity w = e.getValue().level.getEntity(e.getKey());
			if (w != null && w.level() == p.level() && w.distanceToSqr(p) < 48 * 48) {
				return true;
			}
		}
		return false;
	}

	/** A visitor stands in a village of the Thinned: on its first visit, the village's Weaver may wake. */
	public static void nearVillage(ServerPlayer p, int vx, int vy, int vz) {
		if (!unlocked() || p.isCreative() || p.isSpectator() || Rules.peaceful(p.level())) {
			return;
		}
		String due = "wvd@" + vx + "," + vz;
		if (DataStore.seamCells().contains(due)) {
			// Its Weaver sank back into the weave when everyone went (3.38): walked into again, it wakes again
			if (!hunting(p) && stir(p, vx, vy, vz)) {
				DataStore.seamCells().remove(due);
				DataStore.markDirty();
			}
			return;
		}
		// Each village rolls once ("wv@"; before 2.22 the roll was kept as "w@x,z", which wells also use)
		if (DataStore.seamCells().contains("w@" + vx + "," + vz) || !DataStore.seamCells().add("wv@" + vx + "," + vz)) {
			return;
		}
		DataStore.markDirty();
		// Each village that woke none makes the next likelier (weaver_pity), so a long run of empty villages ends
		PlayerData d = DataStore.get(p.getUUID());
		if (ThreadLocalRandom.current().nextDouble() < villageChance(d.weaverMisses) && !hunting(p) && stir(p, vx, vy, vz)) {
			d.weaverMisses = 0;
		} else {
			d.weaverMisses++;
		}
	}

	/** The key of a village's wind-up. */
	public static String stirKey(int vx, int vz) {
		return "weaver@" + vx + "," + vz;
	}

	/**
	 * The threads of a village tighten before its Last Weaver wakes (3.38): for {@link Config#WEAVER_WAKE_SECONDS} a bar
	 * counts down for everyone near. Whoever is still within {@link Config#WEAVER_CALM_RANGE} of the village when it runs
	 * out is the one it wakes for (the one who walked in, first of all); with everyone gone that far it sinks back into
	 * the weave, and the next visit to that village wakes it again. With no wind-up it wakes at once. Returns whether the
	 * threads tighten (or it woke).
	 */
	public static boolean stir(ServerPlayer p, int vx, int vy, int vz) {
		String key = stirKey(vx, vz);
		if (ElderStir.stirring(key) || !Dims.ours(p)) {
			return false;
		}
		if (Config.WEAVER_WAKE_SECONDS <= 0) {
			return riseInVillage(p, vx, vy, vz);
		}
		return ElderStir.begin(new ElderStir.Stir(key, "weaver", (ServerLevel) p.level(), vx + 0.5, vy + 1, vz + 0.5, p, Config.WEAVER_WAKE_SECONDS,
				Math.max(8, Config.WEAVER_CALM_RANGE), BossEvent.BossBarColor.BLUE, q -> true, q -> riseInVillage(q, vx, vy, vz))
				.tint(ChatFormatting.AQUA)
				.settled(() -> {
					DataStore.seamCells().add("wvd@" + vx + "," + vz);
					DataStore.markDirty();
				})
				.pulse(WeaverService::tighten));
	}

	/** The village's Weaver wakes for this player. */
	private static boolean riseInVillage(ServerPlayer p, int vx, int vy, int vz) {
		if (!summon(p, new BlockPos(vx, vy + 1, vz))) {
			return false;
		}
		for (Unweaving u : WEAVERS.values()) {
			if (p.getUUID().equals(u.target) && u.village == null) {
				u.village = "wv@" + vx + "," + vz;
			}
		}
		return true;
	}

	/** Each second the threads tighten: light runs up them over the village, and they hum, louder as it wakes. */
	private static void tighten(ServerLevel level, double x, double y, double z, float share) {
		String dim = level.dimension().identifier().toString();
		WorldFx.particleIn(dim, "minecraft:end_rod", x, y + 6, z, 4, 5, 4, 0.02, 10 + (int) (share * 20));
		if ((level.getGameTime() / 20) % 2 == 0) {
			WorldFx.soundIn(dim, "minecraft:block.amethyst_block.resonate", "hostile", x, y + 4, z, 1.5f + 2.5f * share, 0.5f);
			WorldFx.soundIn(dim, "minecraft:entity.warden.heartbeat", "hostile", x, y, z, 1f + 2f * share, 0.7f);
		}
	}

	/** When each player was last warned of a village's Weaver (server tick), and which village. */
	private static final Map<UUID, Long> WARNED_AT = new HashMap<>();
	private static final Map<UUID, String> WARNED_FOR = new HashMap<>();

	/**
	 * A visitor comes near a village of the Thinned (within {@link Config#WEAVER_WARN_RANGE}) whose Weaver may still wake
	 * (3.38): threads of light hang over it, and on coming near a new one, and about once a minute after, they are told
	 * that walking in may wake it.
	 */
	public static void warnVillage(ServerPlayer p, int vx, int vy, int vz) {
		if (!unlocked() || p.isCreative() || p.isSpectator() || Rules.peaceful(p.level()) || hunting(p) || ElderStir.stirring(stirKey(vx, vz))) {
			return;
		}
		boolean due = DataStore.seamCells().contains("wvd@" + vx + "," + vz);
		if (!due && (DataStore.seamCells().contains("w@" + vx + "," + vz) || DataStore.seamCells().contains("wv@" + vx + "," + vz))) {
			// Rolled already, and nothing woke (or it woke and is gone)
			return;
		}
		WorldFx.particleIn(p.level(), "minecraft:end_rod", vx + 0.5, vy + 8, vz + 0.5, 1.5, 3, 1.5, 0.01, 6);
		long now = p.level().getServer().getTickCount();
		String key = vx + "," + vz;
		Long at = WARNED_AT.get(p.getUUID());
		boolean fresh = !key.equals(WARNED_FOR.get(p.getUUID()));
		if (fresh || at == null || now - at >= 1200 || now < at) {
			WARNED_AT.put(p.getUUID(), now);
			WARNED_FOR.put(p.getUUID(), key);
			Component c = Component.translatable(due ? "gui.extremedimensions.stir.weaver.due" : "gui.extremedimensions.stir.weaver.watched")
					.withStyle(ChatFormatting.AQUA);
			if (fresh) {
				p.sendSystemMessage(c);
			}
			p.sendSystemMessage(c, true);
		}
	}

	/** The chance a village wakes the Last Weaver after this many villages in a row that woke none. */
	public static double villageChance(int misses) {
		return Math.max(0, Math.min(1, Config.WEAVER_CHANCE + Math.max(0, misses) * Math.max(0, Config.WEAVER_PITY)));
	}

	/** The Weaver rises near a place and turns on this player (also the operator command). */
	public static boolean summon(ServerPlayer p, BlockPos near) {
		// Never in another mod's world
		if (!Dims.ours(p)) {
			return false;
		}
		for (Unweaving u : WEAVERS.values()) {
			if (p.getUUID().equals(u.target)) {
				return false;
			}
		}
		ServerLevel level = (ServerLevel) p.level();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		BlockPos spot = ElderFx.ground(level, near.getX() + rnd.nextInt(-3, 4), near.getY() + 6, near.getZ() + rnd.nextInt(-3, 4), 14);
		if (spot == null || spot.distSqr(p.blockPosition()) < 10 * 10) {
			spot = ElderFx.near(level, p, 14, 20);
		}
		Weaver w = ModEntities.WEAVER.spawn(level, spot.above(2), EntitySpawnReason.EVENT);
		if (w == null) {
			return false;
		}
		w.setPersistenceRequired();
		w.setTarget(p);
		float yaw = ElderFx.yawTo(w, p.getX(), p.getZ());
		w.setYRot(yaw);
		w.setYBodyRot(yaw);
		w.setYHeadRot(yaw);
		Unweaving u = new Unweaving();
		u.target = p.getUUID();
		u.level = level;
		u.drift = rnd.nextBoolean() ? 1 : -1;
		u.makeBar(BOSS, BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10, p);
		WEAVERS.put(w.getUUID(), u);
		ElderSpawn.begin(w, u, spot);
		WorldFx.soundIn(ElderFx.dim(w), "extremedimensions:lurker_shriek", "hostile", w.getX(), w.getY() + 12, w.getZ(), 5f, 1.2f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.weaver_wakes"));
		Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.LAST_WEAVER);
		ElderFx.music(w, ElderFx.MUSIC_WEAVER);
		return true;
	}

	/** Whether the Knot Mother is awake (she is one of a kind). */
	public static boolean knotMotherAwake() {
		for (Map.Entry<UUID, Unweaving> e : WEAVERS.entrySet()) {
			if (e.getValue().level.getEntity(e.getKey()) instanceof KnotMother) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether the Knot Mother is awake and near this player (within 64 blocks): while she is, the Seam's thread does not
	 * run for them, as at the Heart while the Starved One lives (her blows still pick at it).
	 */
	public static boolean knotMotherNear(ServerPlayer p) {
		for (Map.Entry<UUID, Unweaving> e : WEAVERS.entrySet()) {
			if (e.getValue().level == p.level() && e.getValue().level.getEntity(e.getKey()) instanceof KnotMother k && k.distanceToSqr(p) < 64 * 64) {
				return true;
			}
		}
		return false;
	}

	/** The key of the First Knot's wind-up (she is one of a kind). */
	public static final String KNOT_STIR = "knot_mother";

	/**
	 * The First Knot loosens before the Knot Mother wakes (3.38): for {@link Config#KNOT_MOTHER_WAKE_SECONDS} a bar counts
	 * down for everyone near. Whoever is still within {@link Config#KNOT_MOTHER_CALM_RANGE} of the heart when it runs out
	 * is the one she wakes for (the one who pulled, first of all); with everyone gone that far the knot pulls tight again
	 * and she sleeps on, with no rest to wait out. With no wind-up she wakes at once. Returns whether it loosens (or she woke).
	 */
	public static boolean stirKnotMother(ServerPlayer p, BlockPos heart) {
		if (!Dims.ours(p) || knotMotherAwake() || ElderStir.stirring(KNOT_STIR)) {
			return false;
		}
		if (Config.KNOT_MOTHER_WAKE_SECONDS <= 0 || p.isCreative()) {
			// (pulled in creative she wakes at once: nobody there could keep her stirring)
			return summonKnotMother(p, heart);
		}
		return ElderStir.begin(new ElderStir.Stir(KNOT_STIR, "knot_mother", (ServerLevel) p.level(), heart.getX() + 0.5, heart.getY(), heart.getZ() + 0.5, p,
				Config.KNOT_MOTHER_WAKE_SECONDS, Math.max(8, Config.KNOT_MOTHER_CALM_RANGE), BossEvent.BossBarColor.PURPLE, q -> true,
				q -> summonKnotMother(q, heart)).pulse(WeaverService::loosen));
	}

	/** Each second the knot loosens: its strands shed and creak round the heart, louder as she comes. */
	private static void loosen(ServerLevel level, double x, double y, double z, float share) {
		String dim = level.dimension().identifier().toString();
		WorldFx.particleIn(dim, "minecraft:witch", x, y + 1.5, z, 1.2, 1.5, 1.2, 0.05, 12 + (int) (share * 20));
		WorldFx.particleIn(dim, "minecraft:enchant", x, y + 2, z, 2, 2, 2, 0.4, 20);
		if ((level.getGameTime() / 20) % 2 == 0) {
			WorldFx.soundIn(dim, "minecraft:block.wool.break", "hostile", x, y + 1, z, 1.5f + 2f * share, 0.5f);
			WorldFx.soundIn(dim, "minecraft:entity.warden.heartbeat", "hostile", x, y, z, 1f + 2f * share, 0.55f);
		}
	}

	/** The Knot Mother pulled loose from the First Knot's heart: she rises over it and turns on whoever pulled. */
	public static boolean summonKnotMother(ServerPlayer p, BlockPos heart) {
		if (!Dims.ours(p) || knotMotherAwake()) {
			return false;
		}
		ServerLevel level = (ServerLevel) p.level();
		KnotMother w = ModEntities.KNOT_MOTHER.spawn(level, heart.above(2), EntitySpawnReason.EVENT);
		if (w == null) {
			return false;
		}
		w.setPersistenceRequired();
		w.setTarget(p);
		float yaw = ElderFx.yawTo(w, p.getX(), p.getZ());
		w.setYRot(yaw);
		w.setYBodyRot(yaw);
		w.setYHeadRot(yaw);
		Unweaving u = new Unweaving();
		u.target = p.getUUID();
		u.level = level;
		u.drift = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
		u.makeBar(boss(w), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10, p);
		u.homeX = heart.getX() + 0.5;
		u.homeZ = heart.getZ() + 0.5;
		WEAVERS.put(w.getUUID(), u);
		ElderSpawn.begin(w, u, heart);
		WorldFx.soundIn(ElderFx.dim(w), "minecraft:block.sculk_shrieker.shriek", "hostile", w.getX(), w.getY() + 8, w.getZ(), 5f, 0.5f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.knot_mother_wakes"));
		ElderFx.music(w, track(w));
		return true;
	}

	public static void tick(MinecraftServer s) {
		if (WEAVERS.isEmpty()) {
			strays(s);
			return;
		}
		List<Runnable> later = new ArrayList<>();
		// Over a copy: a blast of one may slay another, and its death takes it out of the fights mid-loop
		for (Map.Entry<UUID, Unweaving> e : List.copyOf(WEAVERS.entrySet())) {
			Unweaving u = e.getValue();
			if (WEAVERS.get(e.getKey()) != u) {
				continue;
			}
			Entity ent = u.level.getEntity(e.getKey());
			if (!(ent instanceof Weaver w) || !w.isAlive()) {
				u.bar.removeAllPlayers();
				untie(u);
				unspin(u);
				WEAVERS.remove(e.getKey());
				continue;
			}
			u.ticks++;
			ServerPlayer p = s.getPlayerList().getPlayer(u.target);
			if (p == null || !p.isAlive() || p.level() != w.level() || w.distanceToSqr(p) > ElderPower.reach(80) * ElderPower.reach(80)) {
				// Its prey gone: it turns on another near it, if there is one, and only leaves when there is none
				p = u.successor(w, ElderPower.reach(48));
				if (p == null) {
					later.add(() -> fade(w));
					continue;
				}
				u.target = p.getUUID();
				w.setTarget(p);
				u.makeBarFor(p);
			}
			drive(w, u, p, later);
		}
		later.forEach(Runnable::run);
	}

	private static void drive(Weaver w, Unweaving u, ServerPlayer p, List<Runnable> later) {
		String dim = ElderFx.dim(w);
		if (u.ticks % 20 == 0) {
			w.setTarget(p);
		}
		if (u.ticks % 20 == 1) {
			ElderFx.music(w, track(w));
		}
		u.keepBar(w);
		if (u.ticks % 3 == 0) {
			WorldFx.particleIn(u.level, "extremedimensions:soul_mote", w.getX(), w.getY() + 13, w.getZ(), 9, 3, 2, 0.0, 2);
			ElderFx.ambience(w);
		}
		hold(u);
		if (u.action == ElderBoss.SPAWN) {
			// Forming: it hangs where it is woven, turned towards its prey
			w.motion.brake(w, 0.015, HOVER);
			w.motion.turn(w, ElderFx.yawTo(w, p.getX(), p.getZ()), 1.5f);
			ElderSpawn.tick(w, u);
			return;
		}
		if (w instanceof KnotMother && knot(w, u, p)) {
			return;
		}
		if (u.dazed(w)) {
			w.motion.brake(w, 0.03, HOVER);
			return;
		}
		float frac = w.getHealth() / w.getMaxHealth();
		if (w instanceof KnotMother) {
			// Her phases: at 70% and 35% every thread is drawn back to her and her anchors are tied again
			if ((u.kin == 0 && frac <= 0.7f) || (u.kin == 1 && frac <= 0.35f)) {
				u.kin++;
				if (u.kin == 2) {
					u.enraged = true;
				}
				later.add(() -> {
					retie(w, u);
					ElderFx.shake(w, w.getX(), w.getZ(), 40, 0.6f, 20);
				});
			}
		} else if ((u.kin == 0 && frac <= 0.6f) || (u.kin == 1 && frac <= 0.3f)) {
			u.kin++;
			if (u.kin == 2) {
				u.enraged = true;
			}
			later.add(() -> {
				kin(w, 3 + ElderPower.brood(u.fighters));
				ElderFx.shake(w, w.getX(), w.getZ(), 40, 0.6f, 20);
				ElderStrikes.wave(w, w.getX(), w.getY() - HOVER, w.getZ(), 0.8, 16, 4f, "minecraft:end_rod", null);
			});
		}
		double dx = p.getX() - w.getX(), dz = p.getZ() - w.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		// It turns to its prey, but not while its Lance burns (the beams come from its halo, where they were fired)
		if (u.action != ElderBoss.ULTIMATE && !(u.action == Weaver.LANCE && ElderStrikes.beamsOf(w) > 0)) {
			w.motion.turn(w, ElderFx.yawTo(w, p.getX(), p.getZ()), 3f);
		}
		if (u.action == ElderBoss.IDLE) {
			if (ElderUltimate.due(w, u.ultimateSpent)) {
				u.rise = 0;
				u.beginUltimate(w, ult(w));
				if (w instanceof KnotMother) {
					WorldFx.soundIn(dim, "extremedimensions:elder.knot_mother.ultimate", "hostile", w.getX(), w.getY() + 12, w.getZ(), 6f, 1f);
				} else {
					WorldFx.soundIn(dim, "extremedimensions:lurker_shriek", "hostile", w.getX(), w.getY() + 12, w.getZ(), 6f, 0.8f);
					WorldFx.soundIn(dim, "minecraft:block.amethyst_block.resonate", "hostile", w.getX(), w.getY() + 14, w.getZ(), 6f, 0.4f);
				}
				return;
			}
			// It keeps its distance: closer when far, back when near, drifting round in between
			if (dist > 17) {
				double[] to = leashed(u, p.getX(), p.getZ());
				w.motion.toward(w, to[0], to[1], ElderFight.speed(0.13), 0.012, HOVER);
			} else if (dist < 9) {
				double[] to = leashed(u, w.getX() - dx, w.getZ() - dz);
				w.motion.toward(w, to[0], to[1], ElderFight.speed(0.12), 0.015, HOVER);
			} else {
				double a = Math.atan2(w.getZ() - p.getZ(), w.getX() - p.getX()) + u.drift * 0.3;
				double[] to = leashed(u, p.getX() + Math.cos(a) * dist, p.getZ() + Math.sin(a) * dist);
				w.motion.toward(w, to[0], to[1], ElderFight.speed(0.07), 0.008, HOVER);
			}
			if (u.ready()) {
				// The Lance is its favourite blow, near and far; its unravelling reaches only those about it, not one high
				// over it on a spire or far down under it
				int lance = Math.max(0, Config.WEAVER_LANCE_WEIGHT);
				double rise = p.getY() - (w.getY() - HOVER);
				int a;
				if (w instanceof KnotMother) {
					// Her own kit: the Threadline (most against those who keep off), the Knot Snare (most against those
					// who come close), the Weaver's thread and cradle; none of the Weaver's needles, Lance or unravelling
					int line = Math.max(0, Config.KNOT_MOTHER_THREADLINE_WEIGHT), snare = Math.max(0, Config.KNOT_MOTHER_SNARE_WEIGHT);
					a = dist < 9 ? u.pick(KnotMother.SNARE, snare * 2, KnotMother.THREADLINE, line, Weaver.CRADLE, 2, Weaver.THREAD, 1)
							: u.pick(KnotMother.THREADLINE, line * 2, KnotMother.SNARE, snare, Weaver.CRADLE, 2, Weaver.THREAD, 2);
				} else {
					// (3.43: its grid of threads, its tornadoes and its rows of great needles besides, its own and never the
					// Knot Mother's; close in, no needles, whose rows begin four blocks out)
					a = dist < 8 && rise < 8 && rise > -4
							? u.pick(Weaver.UNRAVEL, 4, Weaver.LANCE, (lance + 1) / 2, Weaver.CRADLE, 1, Weaver.WARP, 3, Weaver.SPINDLE, 2)
							: u.pick(Weaver.THREAD, 2, Weaver.NEEDLES, 2, Weaver.LANCE, lance, Weaver.CRADLE, 2, Weaver.THOUSAND, 2, Weaver.WARP, 3,
									Weaver.SPINDLE, 2, Weaver.NEEDLEFALL, 3);
				}
				u.start(w, a);
			}
			return;
		}
		u.step();
		Beat b = u.beat;
		if (u.action == Weaver.UNRAVEL && b.between(2, 10) && Math.sqrt(w.distanceToSqr(p.getX(), w.getY(), p.getZ())) > 6) {
			// It swoops in on the one it unravels (the Knot Mother no further than her cords let her)
			double[] to = leashed(u, p.getX(), p.getZ());
			w.motion.toward(w, to[0], to[1], 0.3, 0.06, HOVER);
		} else if (u.action != ElderBoss.ULTIMATE) {
			// Calling its needles it rises high, and drops as they fall
			double lift = u.action == Weaver.NEEDLES && b.t < 20 ? 4.0 * Math.min(1, Math.max(0, (b.t - 4) / 12.0)) : 0;
			w.motion.brake(w, 0.015, HOVER + (u.action == Weaver.THOUSAND ? 3 : 0) + lift);
		}
		switch (u.action) {
			case Weaver.THREAD -> {
				if (b.at(15)) {
					int side = 1;
					int n = 0;
					for (ServerPlayer q : ElderFx.reachable(w, ElderPower.reach(36))) {
						if (n >= 1 + ElderPower.splits(u.fighters)) {
							break;
						}
						ElderStrikes.tether(w, q, side, 90);
						side = -side;
						n++;
					}
					if (w instanceof KnotMother) {
						WorldFx.soundIn(dim, "extremedimensions:elder.knot_mother.snare", "hostile", p.getX(), p.getY() + 1, p.getZ(), 4f, 1f);
					} else {
						WorldFx.soundIn(dim, "minecraft:entity.leash_knot.place", "hostile", p.getX(), p.getY() + 1, p.getZ(), 3f, 0.5f);
					}
				}
				if (b.past(30)) {
					end(w, u, 55);
				}
			}
			case Weaver.NEEDLES -> {
				if (b.at(20)) {
					ThreadLocalRandom rnd = ThreadLocalRandom.current();
					for (ServerPlayer q : ElderFx.reachable(w, ElderPower.reach(40))) {
						ElderStrikes.strike(w, q.getX(), ElderFx.groundUnder(q), q.getZ(), 2.2, 7f, u.delay(28), ElderStrikes.NEEDLE);
					}
					double py = ElderFx.groundUnder(p);
					for (int i = 0; i < 6; i++) {
						double x = p.getX() + rnd.nextDouble(-8, 8), z = p.getZ() + rnd.nextDouble(-8, 8);
						double y = ElderFx.surface(u.level, x, py + 4, z, 4, 12);
						if (!Double.isNaN(y)) {
							ElderStrikes.strike(w, x, y, z, 2.2, 7f, u.delay(30 + i * 3), ElderStrikes.NEEDLE);
						}
					}
					WorldFx.soundIn(dim, "minecraft:block.amethyst_block.resonate", "hostile", w.getX(), w.getY() + 14, w.getZ(), 4f, 0.5f);
					// it drops out of the height it rose to as they fall
					ElderFx.shake(w, w.getX(), w.getZ(), 30, 0.5f, 12);
				}
				if (b.past(40)) {
					end(w, u, 55);
				}
			}
			case Weaver.UNRAVEL -> {
				if (b.at(10)) {
					WorldFx.soundIn(dim, "extremedimensions:lurker_shriek", "hostile", w.getX(), w.getY() + 12, w.getZ(), 4f, 1.3f);
					ElderFx.shake(w, w.getX(), w.getZ(), 24, 0.5f, 10);
					// Everyone about its body, from under its hem to over its halo
					for (ServerPlayer q : ElderFx.targets(w, 11, HOVER + 4, w.getBbHeight() + 2)) {
						ElderFx.shove(w, q, 1.8, 0.6);
						WorldFx.effect(q, MobEffects.DARKNESS, 3, 0);
					}
				}
				if (b.past(26)) {
					end(w, u, 50);
				}
			}
			case Weaver.LANCE -> lance(w, u, p, b);
			case Weaver.CRADLE -> cradle(w, u, p, b);
			case Weaver.THOUSAND -> thousand(w, u, p, b);
			case Weaver.WARP -> warp(w, u, p, b);
			case Weaver.SPINDLE -> spindle(w, u, b);
			case Weaver.NEEDLEFALL -> needlefall(w, u, p, b);
			case KnotMother.THREADLINE -> threadline(w, u, p, b);
			case KnotMother.SNARE -> snare(w, u, p, b);
			case ElderBoss.ULTIMATE -> tapestry(w, u, b);
			default -> end(w, u, 40);
		}
	}

	private static void end(Weaver w, Unweaving u, double pause) {
		// Its blows land sooner, but come no more often: the time a blow saved is added to the pause after it
		double saved = u.action == ElderBoss.ULTIMATE ? 0 : u.beat.t / u.steady() - u.beat.t / u.tempo();
		u.end(w, pause + Math.max(0, saved));
		if (ThreadLocalRandom.current().nextInt(3) == 0) {
			u.drift = -u.drift;
		}
	}

	/** A point it may go to: for the Knot Mother pulled back inside her leash round where she woke. */
	private static double[] leashed(Unweaving u, double x, double z) {
		if (Double.isNaN(u.homeX)) {
			return new double[] {x, z};
		}
		double r = Math.max(2, Config.KNOT_MOTHER_LEASH);
		double dx = x - u.homeX, dz = z - u.homeZ;
		double d = Math.sqrt(dx * dx + dz * dz);
		return d <= r ? new double[] {x, z} : new double[] {u.homeX + dx / d * r, u.homeZ + dz / d * r};
	}

	/** How far the Knot Mother is from where she woke (NaN for a Weaver, or one not fighting); for tests. */
	public static double strayed(Entity e) {
		Unweaving u = WEAVERS.get(e.getUUID());
		if (u == null || Double.isNaN(u.homeX)) {
			return Double.NaN;
		}
		double dx = e.getX() - u.homeX, dz = e.getZ() - u.homeZ;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** The height of the core at the Knot Mother's breast over her feet (blocks), where her anchors' threads meet. */
	private static final double CHEST = 12.75;

	/**
	 * The Knot Mother's own, every tick of her fight once formed: her anchors tied the first time, a thread of amber from
	 * each standing anchor to the core at her breast, an anchor something else broke counted broken; and while she is
	 * exposed she hangs still, open. True while she is exposed (nothing else of hers this tick).
	 */
	private static boolean knot(Weaver w, Unweaving u, ServerPlayer p) {
		if (!u.tied) {
			u.tied = true;
			tie(w, u);
		}
		if (u.ticks % 10 == 0) {
			double cy = w.getY() + CHEST;
			for (Anchor a : List.copyOf(u.anchors)) {
				if (a.broken) {
					continue;
				}
				if (!a.stands(u.level)) {
					sever(w, u, a);
					continue;
				}
				BlockPos n = a.node();
				AttackFx.line(u.level, AttackFx.of(w), AttackFx.LINE_THREAD, n.getX() + 0.5, n.getY() + 0.6, n.getZ() + 0.5, w.getX(), cy, w.getZ(), 10);
			}
		}
		if (u.exposed > 0) {
			u.exposed--;
			w.motion.brake(w, 0.05, HOVER);
			if (u.exposed % 8 == 0) {
				WorldFx.particleIn(u.level, "minecraft:end_rod", w.getX(), w.getY() + CHEST, w.getZ(), 0.7, 0.7, 0.7, 0.04, 8);
				WorldFx.particleIn(u.level, "minecraft:reverse_portal", w.getX(), w.getY() + CHEST, w.getZ(), 0.9, 0.9, 0.9, 0.05, 14);
			}
			if (u.exposed == 0) {
				end(w, u, 20);
			}
			return true;
		}
		return false;
	}

	/** Ties her anchors round where she woke: pillars of crying obsidian, each topped by a node of amber light. */
	private static void tie(Weaver w, Unweaving u) {
		int n = Math.max(0, Config.KNOT_MOTHER_ANCHORS);
		if (n == 0 || Double.isNaN(u.homeX)) {
			return;
		}
		double r = Math.max(3, Config.KNOT_MOTHER_ANCHOR_RADIUS);
		double base = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
		String dim = ElderFx.dim(w);
		for (int i = 0; i < n; i++) {
			BlockPos at = null;
			// Its own place round her, or (something standing there, a hole in the floor) the nearest free one beside it
			for (int k = 0; k < 9 && at == null; k++) {
				double a = base + 2 * Math.PI * i / n + 0.22 * ((k + 1) / 2) * (k % 2 == 0 ? 1 : -1);
				double rr = r - (k >= 5 ? 2 : 0);
				at = anchorSpot(w, u, u.homeX + Math.cos(a) * rr, u.homeZ + Math.sin(a) * rr);
			}
			if (at == null) {
				continue;
			}
			double x = at.getX() + 0.5, y = at.getY(), z = at.getZ() + 0.5;
			// (held for the fight: whatever happens to it, what stood there is back once the fight lets it go)
			com.extremedimensions.server.FightBlocks.place(u.level, at, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
			com.extremedimensions.server.FightBlocks.place(u.level, at.above(), Blocks.OCHRE_FROGLIGHT.defaultBlockState(), 3);
			u.anchors.add(new Anchor(at));
			DataStore.seamCells().add(mark(u.level, at));
			WorldFx.particleIn(u.level, "minecraft:end_rod", x, y + 1.5, z, 0.3, 0.6, 0.3, 0.05, 16);
			// each anchor rises with a low bronze clang
			WorldFx.soundIn(dim, "extremedimensions:elder.knot_mother.anchor_hit", "hostile", x, y + 1, z, 3f, 0.7f);
		}
		DataStore.markDirty();
	}

	/** Where an anchor can stand near a point: on the floor under her, with room for its two blocks; null if nowhere. */
	private static BlockPos anchorSpot(Weaver w, Unweaving u, double x, double z) {
		if (!Loaded.ready(u.level, BlockPos.containing(x, w.getY(), z))) {
			return null;
		}
		double y = ElderFx.surface(u.level, x, w.getY(), z, 1, 24);
		if (Double.isNaN(y)) {
			return null;
		}
		BlockPos at = BlockPos.containing(x, y, z);
		for (Anchor a : u.anchors) {
			if (a.base.distManhattan(at) < 3) {
				return null;
			}
		}
		return u.level.getBlockState(at).canBeReplaced() && u.level.getBlockState(at.above()).canBeReplaced() ? at : null;
	}

	/** A phase of hers begins: every thread is drawn back to her, and her anchors are tied again, all of them. */
	private static void retie(Weaver w, Unweaving u) {
		double cy = w.getY() + CHEST;
		for (int i = 0; i < 10; i++) {
			double a = 2 * Math.PI * i / 10;
			AttackFx.line(u.level, AttackFx.of(w), AttackFx.LINE_THREAD_TAUT, w.getX() + Math.cos(a) * 16, w.getY() - HOVER + 0.5,
					w.getZ() + Math.sin(a) * 16, w.getX(), cy, w.getZ(), 16);
		}
		// The turning of the fight: a gong and her choir, her roar, a flash, the floor bursting under her and a ring running out
		double gy = w.getY() - HOVER;
		WorldFx.soundIn(ElderFx.dim(w), "extremedimensions:elder.knot_mother.phase", "hostile", w.getX(), cy, w.getZ(), 6f, 1f);
		ElderFx.roar(w, 5f, 0.9f);
		ElderFx.cinema(w, 96, ModPackets.BossFxPayload.FLASH, 0, 0.6f, "");
		AttackFx.burst(u.level, AttackFx.of(w), w.getX(), gy + 0.5, w.getZ(), 6);
		AttackFx.wave(u.level, AttackFx.of(w), false, w.getX(), gy + 0.1, w.getZ(), 1, 1.0, 24);
		WorldFx.particleIn(u.level, "minecraft:reverse_portal", w.getX(), cy, w.getZ(), 2.5, 3.0, 2.5, 0.2, 120);
		untie(u);
		tie(w, u);
	}

	/** Her anchors untied: what stands of them taken out of the world, and their marks with them. */
	private static void untie(Unweaving u) {
		if (u == null || u.anchors.isEmpty()) {
			return;
		}
		for (Anchor a : u.anchors) {
			if (!a.broken) {
				unblock(u.level, a.base);
			}
			DataStore.seamCells().remove(mark(u.level, a.base));
		}
		u.anchors.clear();
		DataStore.markDirty();
	}

	private static void unblock(ServerLevel level, BlockPos base) {
		if (com.extremedimensions.server.FightBlocks.holds(level, base) || com.extremedimensions.server.FightBlocks.holds(level, base.above())) {
			// What stood there goes back now, or once its ground is loaded (even after a restart)
			com.extremedimensions.server.FightBlocks.release(level, base.above());
			com.extremedimensions.server.FightBlocks.release(level, base);
			return;
		}
		// (an anchor from before fights held their blocks)
		if (!Loaded.ready(level, base.above())) {
			return;
		}
		if (level.getBlockState(base.above()).is(Blocks.OCHRE_FROGLIGHT)) {
			level.setBlock(base.above(), Blocks.AIR.defaultBlockState(), 3);
		}
		if (level.getBlockState(base).is(Blocks.CRYING_OBSIDIAN)) {
			level.setBlock(base, Blocks.AIR.defaultBlockState(), 3);
		}
	}

	private static String mark(ServerLevel level, BlockPos base) {
		return ANCHOR_MARK + level.dimension().identifier() + "@" + base.getX() + "," + base.getY() + "," + base.getZ();
	}

	/** Anchors a fight left in the world when the server stopped (marks with no fight to them): untied once their chunk is in. */
	private static void strays(MinecraftServer s) {
		if (s.getTickCount() % 100 != 0) {
			return;
		}
		List<String> marks = DataStore.seamCells().stream().filter(k -> k.startsWith(ANCHOR_MARK)).toList();
		for (String m : marks) {
			int at = m.lastIndexOf('@');
			String[] xyz = m.substring(at + 1).split(",");
			if (at <= ANCHOR_MARK.length() || xyz.length != 3) {
				DataStore.seamCells().remove(m);
				continue;
			}
			String dim = m.substring(ANCHOR_MARK.length(), at);
			BlockPos base;
			try {
				base = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
			} catch (NumberFormatException e) {
				DataStore.seamCells().remove(m);
				continue;
			}
			for (ServerLevel level : s.getAllLevels()) {
				if (level.dimension().identifier().toString().equals(dim) && Loaded.ready(level, base.above())) {
					unblock(level, base);
					DataStore.seamCells().remove(m);
					DataStore.markDirty();
				}
			}
		}
	}

	/** An anchor of hers breaks: its light goes out, the thread snaps, and she is exposed. */
	private static void sever(Weaver w, Unweaving u, Anchor a) {
		a.broken = true;
		unblock(u.level, a.base);
		DataStore.seamCells().remove(mark(u.level, a.base));
		DataStore.markDirty();
		BlockPos n = a.node();
		String dim = ElderFx.dim(w);
		WorldFx.particleIn(u.level, "minecraft:end_rod", n.getX() + 0.5, n.getY() + 0.5, n.getZ() + 0.5, 0.4, 0.4, 0.4, 0.25, 30);
		// It breaks with weight: a great snap and shards, bronze crashing, the thread whipping back to her, the floor bursting
		// at its foot, a flash and a shake for everyone near
		WorldFx.soundIn(dim, "extremedimensions:elder.knot_mother.anchor_break", "hostile", n.getX() + 0.5, n.getY() + 0.5, n.getZ() + 0.5, 6f, 1f);
		AttackFx.line(u.level, AttackFx.of(w), AttackFx.LINE_THREAD_TAUT, n.getX() + 0.5, n.getY() + 0.6, n.getZ() + 0.5, w.getX(), w.getY() + CHEST,
				w.getZ(), 6);
		AttackFx.burst(u.level, AttackFx.of(w), a.base.getX() + 0.5, a.base.getY() + 0.1, a.base.getZ() + 0.5, 4);
		ElderFx.shake(w, n.getX() + 0.5, n.getZ() + 0.5, 40, 0.7f, 18);
		ElderFx.cinema(w, 96, ModPackets.BossFxPayload.FLASH, 0, 0.45f, "");
		int ticks = (int) Math.round(Math.max(0, Config.KNOT_MOTHER_EXPOSED_SECONDS) * 20);
		if (ticks > 0 && u.action != ElderBoss.ULTIMATE && u.action != ElderBoss.SPAWN) {
			u.start(w, KnotMother.EXPOSED);
			u.exposed = ticks;
			ElderFx.roar(w, 5f, 1.3f);
			// Laid bare: her core thrums like a heart, violet light pouring out of it
			WorldFx.soundIn(dim, "extremedimensions:elder.knot_mother.exposed", "hostile", w.getX(), w.getY() + CHEST, w.getZ(), 5f, 1f);
			WorldFx.particleIn(u.level, "minecraft:reverse_portal", w.getX(), w.getY() + CHEST, w.getZ(), 1.2, 1.2, 1.2, 0.3, 90);
		}
	}

	/**
	 * A player strikes a block: if it is part of a standing anchor of the Knot Mother's it takes a hit instead of being
	 * mined (true), and it breaks after {@code knot_mother_anchor_hits}.
	 */
	public static boolean anchorHit(Player player, ServerLevel level, BlockPos pos) {
		for (Map.Entry<UUID, Unweaving> e : WEAVERS.entrySet()) {
			Unweaving u = e.getValue();
			if (u.level != level || u.anchors.isEmpty()) {
				continue;
			}
			for (Anchor a : u.anchors) {
				if (a.broken || !a.holds(pos)) {
					continue;
				}
				if (u.ticks < u.struck.getOrDefault(player.getUUID(), Integer.MIN_VALUE)) {
					return true;
				}
				u.struck.put(player.getUUID(), u.ticks + 8);
				a.hits++;
				BlockPos n = a.node();
				WorldFx.particleIn(level, "minecraft:enchanted_hit", n.getX() + 0.5, n.getY() + 0.5, n.getZ() + 0.5, 0.5, 0.5, 0.5, 0.3, 10);
				// a heavy bronze clang, higher with each blow it takes, and its light cracking
				WorldFx.soundIn(level.dimension().identifier().toString(), "extremedimensions:elder.knot_mother.anchor_hit", "block", n.getX() + 0.5,
						n.getY() + 0.5, n.getZ() + 0.5, 3f, 0.85f + 0.12f * a.hits);
				AttackFx.impact(level, 2, AttackFx.IMPACT_CHARGE, n.getX() + 0.5, a.base.getY() + 0.1, n.getZ() + 0.5, 1.4);
				if (a.hits >= Math.max(1, Config.KNOT_MOTHER_ANCHOR_HITS) && level.getEntity(e.getKey()) instanceof Weaver w) {
					sever(w, u, a);
				}
				return true;
			}
		}
		return false;
	}

	/** What a blow on the Knot Mother is multiplied by: less while her anchors stand, more while she lies exposed. */
	public static float knotFactor(Entity e) {
		Unweaving u = WEAVERS.get(e.getUUID());
		if (u == null) {
			return 1;
		}
		if (u.exposed > 0) {
			return (float) Math.max(1, Config.KNOT_MOTHER_EXPOSED_DAMAGE);
		}
		int up = 0;
		for (Anchor a : u.anchors) {
			up += a.broken ? 0 : 1;
		}
		if (up == 0) {
			return 1;
		}
		return (float) (1 - Math.min(0.9, Math.max(0, Config.KNOT_MOTHER_ANCHOR_WARD)) * up / u.anchors.size());
	}

	/** Where the Knot Mother's standing anchors' nodes are (for tests). */
	public static List<BlockPos> anchors(Entity e) {
		Unweaving u = WEAVERS.get(e.getUUID());
		List<BlockPos> out = new ArrayList<>();
		if (u != null) {
			for (Anchor a : u.anchors) {
				if (!a.broken) {
					out.add(a.node());
				}
			}
		}
		return out;
	}

	/** Ticks the Knot Mother still lies exposed (for tests). */
	public static int exposed(Entity e) {
		Unweaving u = WEAVERS.get(e.getUUID());
		return u == null ? 0 : u.exposed;
	}

	/**
	 * Threadline (the Knot Mother's own): she raises a hand, a thread of amber runs from it to where its sweep begins and
	 * a line of marks kindles along the floor there; then the thread is swept low across the floor, through where her
	 * prey stands and on to the first block behind them. Jump it, or step out of its lane.
	 */
	private static void threadline(Weaver w, Unweaving u, ServerPlayer p, Beat b) {
		String dim = ElderFx.dim(w);
		double fy = ElderFx.groundUnder(p) + 0.5;
		double sweep = Math.max(0.2, Math.min(1.5, Config.KNOT_MOTHER_THREADLINE_SWEEP));
		if (b.at(1)) {
			u.lineA = Math.atan2(p.getZ() - w.getZ(), p.getX() - w.getX());
			u.lineWay = u.drift > 0 ? 1 : -1;
		}
		double a0 = u.lineA - u.lineWay * sweep, a1 = u.lineA + u.lineWay * sweep;
		double dist = Math.max(2, Math.sqrt(w.distanceToSqr(p.getX(), w.getY(), p.getZ())));
		if (b.between(2, 20) && u.ticks % 3 == 0) {
			double r = dist + 6;
			double x0 = w.getX() + Math.cos(a0) * r, z0 = w.getZ() + Math.sin(a0) * r;
			AttackFx.line(u.level, AttackFx.of(w), AttackFx.LINE_THREAD, w.getX(), w.getY() + 14, w.getZ(), x0, fy, z0, 4);
			AttackFx.line(u.level, AttackFx.of(w), AttackFx.LINE_PATH, w.getX(), fy, w.getZ(), x0, fy, z0, 4);
		}
		if (b.at(20)) {
			int duration = Math.max(10, (int) Math.round(22 / u.steady()));
			ElderStrikes.threadline(w, w.getX(), fy, w.getZ(), a0, a1, dist, fy, duration, (float) Math.max(0, Config.KNOT_MOTHER_THREADLINE_DAMAGE));
			// The sweep cracks like a whip: heard from her and loud where it runs at its prey; the floor jolts under them,
			// and bursts where it begins
			WorldFx.soundIn(dim, "extremedimensions:elder.knot_mother.lash", "hostile", w.getX(), w.getY() + 12, w.getZ(), 5f, 1f);
			WorldFx.soundIn(dim, "extremedimensions:elder.knot_mother.lash", "hostile", p.getX(), p.getY() + 1, p.getZ(), 3f, 1.1f);
			ElderFx.shake(w, p.getX(), p.getZ(), 30, 0.55f, 14);
			double r0 = dist + 6;
			AttackFx.impact(u.level, AttackFx.of(w), AttackFx.IMPACT_BLAST, w.getX() + Math.cos(a0) * Math.min(r0, 10), fy - 0.4,
					w.getZ() + Math.sin(a0) * Math.min(r0, 10), 2.2);
		}
		if (b.past(28) && ElderStrikes.threadsOf(w) == 0) {
			end(w, u, 45);
		}
	}

	/**
	 * Knot Snare (the Knot Mother's own): her hands pull towards each other and patterns are drawn on the floor, one under
	 * each of her prey and more about them, closing inwards for at least {@code knot_mother_snare_warning} seconds;
	 * whoever is still in one when it closes is held fast and loses Seam time.
	 */
	private static void snare(Weaver w, Unweaving u, ServerPlayer p, Beat b) {
		if (b.at(12)) {
			ThreadLocalRandom rnd = ThreadLocalRandom.current();
			float damage = (float) Math.max(0, Config.KNOT_MOTHER_SNARE_DAMAGE);
			int warn = Math.max(u.delay(30), (int) Math.round(Math.max(0.5, Config.KNOT_MOTHER_SNARE_WARNING) * 20));
			for (ServerPlayer q : u.split(w, p, ElderPower.reach(36))) {
				ElderStrikes.strike(w, q.getX(), ElderFx.groundUnder(q), q.getZ(), 2.6, damage, warn, ElderStrikes.SNARE);
			}
			double py = ElderFx.groundUnder(p);
			for (int i = 0; i < 3; i++) {
				double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble(4, 9);
				double x = p.getX() + Math.cos(a) * r, z = p.getZ() + Math.sin(a) * r;
				if (!Loaded.ready(u.level, BlockPos.containing(x, py, z))) {
					continue;
				}
				double y = ElderFx.surface(u.level, x, py + 4, z, 4, 12);
				if (!Double.isNaN(y)) {
					ElderStrikes.strike(w, x, y, z, 2.6, damage, warn + 4 * (i + 1), ElderStrikes.SNARE);
				}
			}
			// Her knots tied faster and faster, up to the moment they close (about the pattern's warning; faster when it is shorter)
			float rate = (float) Math.max(0.5, Math.min(2.0, 30.0 / Math.max(1, warn)));
			WorldFx.soundIn(ElderFx.dim(w), "extremedimensions:elder.knot_mother.knot", "hostile", p.getX(), p.getY() + 1, p.getZ(), 4f, rate);
		}
		if (b.past(30)) {
			end(w, u, 50);
		}
	}

	/** Caught in a closing Snare: held fast for a moment, and the Seam takes its time faster. */
	public static void snared(ServerPlayer p) {
		ElderSpells.snare(p, Math.max(1, (int) Math.round(Config.KNOT_MOTHER_SNARE_SECONDS * 20 * com.extremedimensions.binding.Charms.steadfast(p))));
		WorldFx.particles(p, "minecraft:end_rod", 0.6, 0.4, 0.0, 12);
		PlayerData d = DataStore.get(p.getUUID());
		int time = Math.max(0, Config.KNOT_MOTHER_SNARE_TIME);
		if (d.inSeam && d.seamPull <= 0 && !p.isCreative() && time > 0) {
			d.seamLeft = Math.max(1, d.seamLeft - time);
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.knot_mother_snare", time), true);
		} else {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.knot_mother_snare_held"), true);
		}
	}

	/** Where its halo is (a beam's source). */
	private static double[] halo(Weaver w) {
		double yaw = Math.toRadians(w.getYRot());
		return new double[] {w.getX() + Math.sin(yaw) * 2.25, w.getY() + 16.5, w.getZ() - Math.cos(yaw) * 2.25};
	}

	/**
	 * Seraphic Lance: the halo spins up and fires a beam that sweeps through its prey wherever they stand (on an island
	 * high over it, down in a pit, far off), on past them to the ground behind, and often (always once it is enraged)
	 * sweeps back again at where they fled to.
	 */
	private static void lance(Weaver w, Unweaving u, ServerPlayer p, Beat b) {
		String dim = ElderFx.dim(w);
		double[] h = halo(w);
		if (b.at(1)) {
			WorldFx.soundIn(dim, "minecraft:block.beacon.activate", "hostile", h[0], h[1], h[2], 5f, 0.5f);
			u.back = u.enraged || ThreadLocalRandom.current().nextDouble() < Config.WEAVER_LANCE_RETURN;
		}
		if (b.between(0, 22) || (u.back && b.between(42, 56))) {
			WorldFx.particleIn(u.level, "minecraft:end_rod", h[0], h[1], h[2], 2.5, 2.5, 2.5, -0.15, 3);
			if (u.ticks % 4 == 0) {
				AttackFx.impact(u.level, 2, AttackFx.IMPACT_CUT, h[0], h[1], h[2], 1.5);
			}
		}
		if (b.at(22)) {
			// A beam for its prey, and with more of them against it one for each of the nearest others
			for (ServerPlayer q : u.split(w, p, ElderPower.reach(44))) {
				sweep(w, u, q, h, u.drift > 0 ? 1 : -1);
			}
		}
		if (u.back && b.at(56)) {
			// Back the other way, at where they went
			for (ServerPlayer q : u.split(w, p, ElderPower.reach(44))) {
				sweep(w, u, q, h, u.drift > 0 ? -1 : 1);
			}
		}
		// It holds still until its beams have burnt out (they come from its halo)
		if (b.past(u.back ? 96 : 62) && ElderStrikes.beamsOf(w) == 0) {
			end(w, u, 40);
		}
	}

	private static void sweep(Weaver w, Unweaving u, ServerPlayer p, double[] h, int way) {
		// Aimed at them: its arc runs round the halo at their own height, from just short of them to just past them
		double dx = p.getX() - h[0], dz = p.getZ() - h[2];
		double r = Math.max(1.5, Math.sqrt(dx * dx + dz * dz));
		double a = Math.atan2(dz, dx);
		// It burns longer than it used to, over a wider arc at the same speed (whatever the pace its blows land at)
		double length = Math.max(0.5, Config.WEAVER_LANCE_LENGTH);
		double sweep = Math.min(1.6, 0.75 * length) * way;
		int burn = Math.max(8, (int) Math.round(34 / u.steady() * length));
		ElderStrikes.lance(w, h[0], h[1], h[2], a - sweep, a + sweep, Math.max(0.5, r - 2), r + 2, p.getY() + p.getBbHeight() * 0.5, burn,
				(float) Config.WEAVER_LANCE_DAMAGE, true);
		WorldFx.soundIn(ElderFx.dim(w), "minecraft:entity.warden.sonic_boom", "hostile", h[0], h[1], h[2], 4f, 1.2f);
		ElderFx.shake(w, p.getX(), p.getZ(), 30, 0.5f, 30);
	}

	/** Cat's Cradle: threads strung across the ground round its prey, pulled taut until they cut. */
	private static void cradle(Weaver w, Unweaving u, ServerPlayer p, Beat b) {
		String dim = ElderFx.dim(w);
		if (b.at(1) && !(w instanceof KnotMother)) {
			WorldFx.soundIn(dim, "minecraft:entity.leash_knot.place", "hostile", w.getX(), w.getY() + 12, w.getZ(), 5f, 0.4f);
		}
		if (b.at(14)) {
			// A cradle round its prey, and with more of them against it round each of the nearest others
			boolean first = true;
			for (ServerPlayer q : u.split(w, p, ElderPower.reach(36))) {
				ElderStrikes.web(w, cradleAround(w, u, q, first), u.delay(50), 14f);
				if (w instanceof KnotMother) {
					WorldFx.soundIn(dim, "extremedimensions:elder.knot_mother.knot", "hostile", q.getX(), q.getY() + 1, q.getZ(), 4f, 0.6f);
				} else {
					WorldFx.soundIn(dim, "minecraft:block.tripwire.attach", "hostile", q.getX(), q.getY(), q.getZ(), 4f, 0.5f);
				}
				first = false;
			}
		}
		if (b.past(74)) {
			end(w, u, 55);
		}
	}

	/** The threads of a cradle round {@code p}: a star of seven, one right through them, and (for the first) two from its hands. */
	private static List<double[]> cradleAround(Weaver w, Unweaving u, ServerPlayer p, boolean hands) {
		List<double[]> lines = new ArrayList<>();
		double[][] anchors = new double[7][];
		double base = ThreadLocalRandom.current().nextDouble() * Math.PI;
		for (int i = 0; i < 7; i++) {
			double a = base + 2 * Math.PI * i / 7;
			double x = p.getX() + Math.cos(a) * 10, z = p.getZ() + Math.sin(a) * 10;
			double y = ElderFx.surface(u.level, x, p.getY() + 4, z, 6, 12);
			anchors[i] = new double[] {x, (Double.isNaN(y) ? p.getY() : y) + 1.0, z};
		}
		for (int i = 0; i < 7; i++) {
			double[] a0 = anchors[i], a1 = anchors[(i + 3) % 7];
			lines.add(new double[] {a0[0], a0[1], a0[2], a1[0], a1[1], a1[2]});
		}
		// One thread runs right through where they stand (standing still is no shelter)
		double through = base + Math.PI / 7;
		lines.add(new double[] {p.getX() + Math.cos(through) * 10, p.getY() + 1.0, p.getZ() + Math.sin(through) * 10,
				p.getX() - Math.cos(through) * 10, p.getY() + 1.0, p.getZ() - Math.sin(through) * 10});
		if (hands) {
			double yaw = Math.toRadians(w.getYRot());
			for (int side = -1; side <= 1; side += 2) {
				double hx = w.getX() - Math.cos(yaw) * 9.5 * side, hz = w.getZ() - Math.sin(yaw) * 9.5 * side;
				double[] a = anchors[side < 0 ? 0 : 3];
				lines.add(new double[] {hx, w.getY() + 13.5, hz, a[0], a[1], a[2]});
			}
		}
		return lines;
	}

	/** A Thousand Needles: it rises, rings of needles spiral down onto its prey, and a great needle falls last of all. */
	private static void thousand(Weaver w, Unweaving u, ServerPlayer p, Beat b) {
		String dim = ElderFx.dim(w);
		if (b.at(1)) {
			WorldFx.soundIn(dim, "minecraft:block.amethyst_block.resonate", "hostile", w.getX(), w.getY() + 14, w.getZ(), 5f, 0.4f);
		}
		if (b.every(5, 16) && b.t < 58) {
			double turn = b.t * 0.6;
			double r = Math.max(2.5, 12 - (b.t - 16) * 0.25);
			for (ServerPlayer q : ElderFx.reachable(w, ElderPower.reach(44))) {
				for (int i = 0; i < 3; i++) {
					double a = turn + 2 * Math.PI * i / 3;
					double x = q.getX() + Math.cos(a) * r, z = q.getZ() + Math.sin(a) * r;
					double y = ElderFx.surface(u.level, x, q.getY() + 4, z, 4, 12);
					ElderStrikes.strike(w, x, Double.isNaN(y) ? q.getY() : y, z, 1.8, 5f, u.delay(14), ElderStrikes.NEEDLE);
				}
			}
			WorldFx.soundIn(dim, "minecraft:block.amethyst_cluster.break", "hostile", w.getX(), w.getY() + 14, w.getZ(), 3f, 0.8f);
		}
		if (b.at(60)) {
			// The great needle, on its prey and with more of them against it on the nearest others too
			for (ServerPlayer q : u.split(w, p, ElderPower.reach(44))) {
				ElderStrikes.strike(w, q.getX(), ElderFx.groundUnder(q), q.getZ(), 4.2, 16f, u.delay(20), ElderStrikes.SPEAR);
				WorldFx.soundIn(dim, "minecraft:item.trident.thunder", "hostile", q.getX(), q.getY() + 10, q.getZ(), 4f, 0.6f);
			}
		}
		if (b.past(86)) {
			end(w, u, 65);
		}
	}

	// ------------------------------------------------------------------ Warp and Weft (3.43)

	/** How far apart the lines of its Warp and Weft run, and how many run each side of its middle (24 blocks across). */
	private static final double WARP_STEP = 4;
	private static final int WARP_LINES = 3;
	/** How near one of its lines one must stand to be cut as it lands. */
	private static final double WARP_EDGE = 0.9;

	/**
	 * Warp and Weft: its arms raised, it weaves a square grid of threads 24 blocks across, twelve blocks over the ground
	 * of its prey (over the middle of everyone standing near them), a line every four blocks both ways; from 24 a line on
	 * the ground under each shows where it will fall. Its arms slam down at 44 and the grid falls with them; it lands at
	 * 52, and whoever stands on the ground on one of its lines is cut, the ground scorched along them in places. The cells
	 * of three blocks by three between the lines are safe.
	 */
	private static void warp(Weaver w, Unweaving u, ServerPlayer p, Beat b) {
		ServerLevel level = u.level;
		int elder = AttackFx.of(w);
		if (b.at(6)) {
			double sx = p.getX(), sz = p.getZ();
			int n = 1;
			for (ServerPlayer q : ElderFx.targets(w, ElderPower.reach(80))) {
				double dx = q.getX() - p.getX(), dz = q.getZ() - p.getZ();
				if (q != p && dx * dx + dz * dz <= 12 * 12) {
					sx += q.getX();
					sz += q.getZ();
					n++;
				}
			}
			// (on a block's middle: its lines run along rows of blocks, and the three between each two are safe)
			u.warpX = Math.floor(sx / n) + 0.5;
			u.warpZ = Math.floor(sz / n) + 0.5;
			u.warpY = ElderFx.groundUnder(p);
			u.warp = true;
		}
		if (u.warp && b.between(6, 52)) {
			// Held high while it weaves, drawn again every four beats; then drawn lower every tick as it falls
			boolean falling = b.t >= 44;
			if (falling || b.every(4, 6)) {
				double h = falling ? 12 * Math.max(0, 1 - (b.t - 44) / 8) : 12;
				int life = falling ? 2 : Math.max(5, (int) Math.ceil(4 / u.tempo()) + 1);
				weave(level, elder, u, AttackFx.LINE_THREAD, u.warpY + h, life);
				if (b.t >= 24) {
					weave(level, elder, u, AttackFx.LINE_PATH, u.warpY + 0.1, life);
				}
			}
		}
		if (u.warp && b.at(52)) {
			u.warp = false;
			fallWarp(w, u);
		}
		if (b.past(80)) {
			u.warp = false;
			end(w, u, 50);
		}
	}

	/** Its grid's lines at height {@code y}, both ways, drawn for {@code ticks}. */
	private static void weave(ServerLevel level, int elder, Unweaving u, int kind, double y, int ticks) {
		double half = WARP_LINES * WARP_STEP;
		for (int k = -WARP_LINES; k <= WARP_LINES; k++) {
			double o = k * WARP_STEP;
			AttackFx.line(level, elder, kind, u.warpX - half, y, u.warpZ + o, u.warpX + half, y, u.warpZ + o, ticks);
			AttackFx.line(level, elder, kind, u.warpX + o, y, u.warpZ - half, u.warpX + o, y, u.warpZ + half, ticks);
		}
	}

	/** Its grid lands: whoever stands on one of its lines is cut, its threads snap taut on the ground, and it is scorched. */
	private static void fallWarp(Weaver w, Unweaving u) {
		ServerLevel level = u.level;
		int elder = AttackFx.of(w);
		float damage = (float) Math.max(0, Config.WEAVER_WARP_DAMAGE);
		for (ServerPlayer q : ElderFx.targets(w, ElderPower.reach(80))) {
			if (!onWarp(u, q)) {
				continue;
			}
			if (damage > 0) {
				q.hurtServer(level, ElderPower.great(level, w), damage);
			}
			WorldFx.particleIn(level, "minecraft:enchanted_hit", q.getX(), q.getY() + 0.6, q.getZ(), 0.4, 0.4, 0.4, 0.2, 10);
		}
		weave(level, elder, u, AttackFx.LINE_THREAD_TAUT, u.warpY + 0.15, 8);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double half = WARP_LINES * WARP_STEP;
		for (int i = 0; i < 8; i++) {
			// a spot somewhere along one of its lines, of those running one way or the other
			double along = rnd.nextDouble(-half, half), on = rnd.nextInt(-WARP_LINES, WARP_LINES + 1) * WARP_STEP;
			double x = u.warpX + (i % 2 == 0 ? along : on), z = u.warpZ + (i % 2 == 0 ? on : along);
			ElderTerrain.mark(level, x, u.warpY, z, 1.5, 0.55, ElderTerrain.Mark.SCORCH);
			AttackFx.impact(level, elder, AttackFx.IMPACT_CUT, x, u.warpY + 0.1, z, 1.4);
		}
		WorldFx.particleIn(level, "minecraft:end_rod", u.warpX, u.warpY + 0.4, u.warpZ, half * 0.6, 0.2, half * 0.6, 0.05, 60);
		WorldFx.soundIn(ElderFx.dim(w), "extremedimensions:elder.weaver.warpfall", "hostile", u.warpX, u.warpY + 1, u.warpZ, 5f, 1f);
		ElderFx.shake(w, u.warpX, u.warpZ, 32, 0.55f, 12);
	}

	/** Whether a player stands on the ground on one of its grid's lines, inside its square, as it lands. */
	private static boolean onWarp(Unweaving u, ServerPlayer q) {
		double dx = q.getX() - u.warpX, dz = q.getZ() - u.warpZ, half = WARP_LINES * WARP_STEP;
		if (Math.abs(dx) > half + WARP_EDGE || Math.abs(dz) > half + WARP_EDGE || Math.abs(q.getY() - u.warpY) > 2.5
				|| q.getY() - ElderFx.groundUnder(q) > 0.6) {
			return false;
		}
		return Math.abs(dx - WARP_STEP * Math.round(dx / WARP_STEP)) <= WARP_EDGE || Math.abs(dz - WARP_STEP * Math.round(dz / WARP_STEP)) <= WARP_EDGE;
	}

	/** Whether its Warp and Weft hangs over the ground now, laid and not yet fallen (for tests). */
	public static boolean warpLaid(Entity e) {
		Unweaving u = WEAVERS.get(e.getUUID());
		return u != null && u.warp;
	}

	// ------------------------------------------------------------------ Spindle Storm (3.43)

	/** How tall a tornado of its Spindle Storm stands, how far it draws players in, how near it cuts, how fast it wanders. */
	private static final double SPINDLE_HEIGHT = 14, SPINDLE_REACH = 9, SPINDLE_CORE = 2.5, SPINDLE_SPEED = 0.25;

	/**
	 * Spindle Storm: it spins up, and at 20 three tornadoes of thread rise out of the floor round it, six to ten blocks
	 * out. Until 96 each wanders after the player nearest it (a little slower than a sprint), drawing everyone near in
	 * towards its core (by the squall's rules: a wall at their back holds them, sneaking halves it, Anchored eases it),
	 * cutting and whirling whoever is caught in its core, and tearing up the ground it crosses. At 100 they burst, their
	 * shreds thrown out and whoever is close thrown up and away.
	 */
	private static void spindle(Weaver w, Unweaving u, Beat b) {
		if (b.at(20)) {
			rise(w, u);
		}
		if (b.between(21, 100) && u.spindles.isEmpty()) {
			// none could rise (nothing but a drop round it)
			end(w, u, 40);
			return;
		}
		if (b.between(20, 100)) {
			whirl(w, u, b);
		}
		if (b.at(100)) {
			burst(w, u);
		}
		if (b.past(110)) {
			// (its shreds have come down where they were thrown)
			dropSpindles(u);
		}
		if (b.past(116)) {
			dropSpindles(u);
			end(w, u, 50);
		}
	}

	/** Its three tornadoes rise out of the floor round it, spread round, six to ten blocks out. */
	private static void rise(Weaver w, Unweaving u) {
		ServerLevel level = u.level;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		dropSpindles(u);
		u.spun.clear();
		BlockState[] kinds = {ArenaDesign.mc("white_wool", Blocks.COBWEB.defaultBlockState()), Blocks.COBWEB.defaultBlockState(),
				ArenaDesign.mc("calcite", Blocks.COBWEB.defaultBlockState())};
		double base = rnd.nextDouble() * Math.PI * 2;
		for (int i = 0; i < 3; i++) {
			double a = base + Math.PI * 2 * i / 3 + rnd.nextDouble(-0.3, 0.3), r = rnd.nextDouble(6, 10);
			double x = w.getX() + Math.cos(a) * r, z = w.getZ() + Math.sin(a) * r;
			if (!Loaded.ready(level, BlockPos.containing(x, w.getY(), z))) {
				continue;
			}
			double y = ElderFx.surface(level, x, w.getY(), z, 4, 24);
			if (Double.isNaN(y)) {
				continue;
			}
			Spindle s = new Spindle(x, y, z, a);
			for (BlockState kind : kinds) {
				// (small, in the floor at its foot: they swell as they are drawn up)
				Display.BlockDisplay d = BlockFlight.spawn(level, s.ox, s.oy, s.oz, kind, at(s.ox, s.oy, s.oz, x, y - 0.3, z, new Quaternionf(), 0.1));
				if (d != null) {
					s.shreds.add(d);
				}
			}
			u.spindles.add(s);
			AttackFx.burst(level, AttackFx.of(w), x, y + 0.5, z, 2.5);
			WorldFx.particleIn(level, "minecraft:cloud", x, y + 0.5, z, 1.2, 0.3, 1.2, 0.08, 14);
			ElderTerrain.furrow(level, x, y - 1, z, 1.6, 1, 1, 2);
		}
		ElderFx.shake(w, w.getX(), w.getZ(), 28, 0.4f, 10);
	}

	/**
	 * Each tick of its tornadoes: they wander, twist and whirl their shreds, draw in whoever is near, cut whoever is in
	 * their core (once in ten ticks, however many hold them), and tear up the ground they cross.
	 */
	private static void whirl(Weaver w, Unweaving u, Beat b) {
		ServerLevel level = u.level;
		String dim = ElderFx.dim(w);
		int elder = AttackFx.of(w);
		List<ServerPlayer> near = ElderFx.targets(w, ElderPower.reach(64));
		long now = level.getGameTime();
		// (they rise out of the floor over eight beats)
		double rise = Math.max(0.05, Math.min(1, (b.t - 20) / 8));
		boolean wandering = b.t < 96;
		boolean redraw = b.every(3, 20), tear = wandering && b.every(8, 20), hum = wandering && b.every(30, 20);
		int life = Math.max(4, (int) Math.ceil(3 / u.tempo()) + 2);
		float damage = (float) Math.max(0, Config.WEAVER_SPINDLE_DAMAGE);
		for (Spindle s : u.spindles) {
			if (wandering) {
				wander(level, s, near, u.ticks);
			}
			double turn = u.ticks * 0.45;
			if (redraw) {
				twist(level, elder, s, turn, rise, life);
			}
			// motes spiralling up it, and dust at its foot
			for (int i = 0; i < 3; i++) {
				double a = turn * 1.2 + s.phase + Math.PI * 2 * i / 3;
				double h = ((u.ticks * 0.6 + i * 4.7) % SPINDLE_HEIGHT) * rise, r = 0.4 + 2.6 * h / SPINDLE_HEIGHT;
				WorldFx.particleIn(level, "minecraft:end_rod", s.x + Math.cos(a) * r, s.y + h, s.z + Math.sin(a) * r, 0.05, 0.05, 0.05, 0.01, 1);
			}
			if (u.ticks % 3 == 0) {
				WorldFx.particleIn(level, "minecraft:cloud", s.x, s.y + 0.3, s.z, 1.0, 0.2, 1.0, 0.06, 2);
			}
			if (u.ticks % 2 == 0) {
				shreds(s, u.ticks + 2, rise);
			}
			if (hum) {
				WorldFx.soundIn(dim, "extremedimensions:elder.weaver.spindle", "hostile", s.x, s.y + 4, s.z, 4f, 1f);
			}
			// (only ground it has crossed since: standing still over someone, it digs no pit)
			if (tear && Math.pow(s.x - s.tornX, 2) + Math.pow(s.z - s.tornZ, 2) >= 1) {
				ElderTerrain.furrow(level, s.x, s.y - 1, s.z, 1.2, 1, 1, 3);
				s.tornX = s.x;
				s.tornZ = s.z;
			}
			for (ServerPlayer q : near) {
				double dx = s.x - q.getX(), dz = s.z - q.getZ(), dy = q.getY() - s.y;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > SPINDLE_REACH || dy < -2 || dy > SPINDLE_HEIGHT * rise + 1) {
					continue;
				}
				// Drawn in towards its core, harder the nearer
				if (d > 0.6 && PaleSquall.blown(q)) {
					double pull = PaleSquall.push(q, dx / d, dz / d, Config.WEAVER_SPINDLE_PULL * (0.4 + 0.6 * (1 - d / SPINDLE_REACH)));
					if (pull > 0) {
						PaleSquall.gust(q, dx / d * pull, dz / d * pull);
					}
				}
				Long last = u.spun.get(q.getUUID());
				if (d <= SPINDLE_CORE && (last == null || now - last >= 10)) {
					u.spun.put(q.getUUID(), now);
					if (damage > 0) {
						q.hurtServer(level, ElderPower.great(level, w), damage);
					}
					// whirled round its core and up
					double tx = d > 0.3 ? -dz / d : 1, tz = d > 0.3 ? dx / d : 0;
					q.setDeltaMovement(tx * 0.45, 0.35, tz * 0.45);
					WorldFx.moved(q);
					WorldFx.particleIn(level, "minecraft:enchanted_hit", q.getX(), q.getY() + 1, q.getZ(), 0.4, 0.5, 0.4, 0.1, 8);
					WorldFx.soundIn(dim, "minecraft:block.wool.break", "hostile", q.getX(), q.getY() + 1, q.getZ(), 2f, 0.6f);
				}
			}
		}
	}

	/** A tornado wanders a step after the player nearest it, along the ground (never out over a drop with no floor in reach). */
	private static void wander(ServerLevel level, Spindle s, List<ServerPlayer> near, int ticks) {
		ServerPlayer to = null;
		double best = Double.MAX_VALUE;
		for (ServerPlayer q : near) {
			double d = q.distanceToSqr(s.x, q.getY(), s.z);
			if (d < best) {
				best = d;
				to = q;
			}
		}
		if (to == null || best < 0.04) {
			return;
		}
		// (each sways its own way as it goes, so the three do not run together)
		double a = Math.atan2(to.getZ() - s.z, to.getX() - s.x) + 0.5 * Math.sin(ticks * 0.06 + s.phase * 3);
		double step = Math.min(Math.sqrt(best), SPINDLE_SPEED);
		double nx = s.x + Math.cos(a) * step, nz = s.z + Math.sin(a) * step;
		if (!Loaded.ready(level, BlockPos.containing(nx, s.y, nz))) {
			return;
		}
		double ny = ElderFx.surface(level, nx, s.y, nz, 3, 8);
		if (Double.isNaN(ny)) {
			return;
		}
		s.x = nx;
		s.y = ny;
		s.z = nz;
	}

	/** A tornado's column: six threads twisting up from its foot, wider as they climb ({@code rise} of its height risen). */
	private static void twist(ServerLevel level, int elder, Spindle s, double turn, double rise, int ticks) {
		double[] prev = null;
		for (int k = 0; k <= 6; k++) {
			double f = k / 6.0, a = turn + s.phase + k * 0.9, r = 0.4 + 2.6 * f;
			double[] pt = {s.x + Math.cos(a) * r, s.y + SPINDLE_HEIGHT * f * rise, s.z + Math.sin(a) * r};
			if (prev != null) {
				AttackFx.line(level, elder, AttackFx.LINE_THREAD, prev[0], prev[1], prev[2], pt[0], pt[1], pt[2], ticks);
			}
			prev = pt;
		}
	}

	/** Its shreds whirl round it (where they will be at tick {@code t} of its fight), climbing and falling as they turn. */
	private static void shreds(Spindle s, double t, double rise) {
		for (int k = 0; k < s.shreds.size(); k++) {
			double a = t * 0.38 + s.phase + Math.PI * 2 * k / 3;
			double h = 0.3 + (6 + 4.5 * Math.sin(t * 0.07 + k * 2.1)) * rise, r = 0.8 + h * 0.17;
			Quaternionf q = new Quaternionf().rotateY((float) (-t * 0.3 - k)).rotateX((float) (t * 0.2 + k));
			place(s.shreds.get(k), s.ox, s.oy, s.oz, s.x + Math.cos(a) * r, s.y + h, s.z + Math.sin(a) * r, q, 0.3 + 0.35 * rise, 2);
		}
	}

	/** Its tornadoes burst: their shreds thrown out, and whoever stands close thrown up and away. */
	private static void burst(Weaver w, Unweaving u) {
		ServerLevel level = u.level;
		String dim = ElderFx.dim(w);
		int elder = AttackFx.of(w);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		List<ServerPlayer> near = ElderFx.targets(w, ElderPower.reach(64));
		// (they come down before they go, ten beats on)
		int flight = Math.max(3, (int) Math.round(8 / u.tempo()));
		for (Spindle s : u.spindles) {
			AttackFx.burst(level, elder, s.x, s.y + 3, s.z, 4);
			AttackFx.ring(level, elder, s.x, s.y + 0.1, s.z, 4, 10);
			WorldFx.particleIn(level, "minecraft:end_rod", s.x, s.y + 5, s.z, 1.5, 4, 1.5, 0.25, 40);
			WorldFx.particleIn(level, "minecraft:cloud", s.x, s.y + 1, s.z, 1.5, 0.5, 1.5, 0.15, 16);
			WorldFx.soundIn(dim, "minecraft:block.wool.break", "hostile", s.x, s.y + 2, s.z, 4f, 0.5f);
			ElderFx.shake(w, s.x, s.z, 20, 0.45f, 8);
			for (ServerPlayer q : near) {
				double dx = q.getX() - s.x, dz = q.getZ() - s.z, dy = q.getY() - s.y;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 4 || dy < -2 || dy > SPINDLE_HEIGHT) {
					continue;
				}
				d = Math.max(0.5, d);
				q.setDeltaMovement(dx / d * 0.9, 0.6, dz / d * 0.9);
				WorldFx.moved(q);
			}
			for (Display.BlockDisplay d : s.shreds) {
				double a = rnd.nextDouble() * Math.PI * 2, far = rnd.nextDouble(5, 9);
				double tx = s.x + Math.cos(a) * far, tz = s.z + Math.sin(a) * far;
				double ty = ElderFx.surface(level, tx, s.y + 4, tz, 4, 12);
				place(d, s.ox, s.oy, s.oz, tx, (Double.isNaN(ty) ? s.y : ty) + 0.3, tz, new Quaternionf().rotateXYZ(rnd.nextFloat() * 6, rnd.nextFloat() * 6, 0),
						0.5, flight);
			}
		}
	}

	/** Its tornadoes' shreds come down to nothing (the storm over, or cut short, or its fight over). */
	private static void dropSpindles(Unweaving u) {
		for (Spindle s : u.spindles) {
			for (Display.BlockDisplay d : s.shreds) {
				BlockFlight.drop(d);
			}
		}
		u.spindles.clear();
	}

	/** How many shreds whirl round the tornadoes of its Spindle Storm now (for tests). */
	public static int spindleShreds(Entity e) {
		Unweaving u = WEAVERS.get(e.getUUID());
		int n = 0;
		if (u != null) {
			for (Spindle s : u.spindles) {
				n += s.shreds.size();
			}
		}
		return n;
	}

	/** How many tornadoes of its Spindle Storm stand now (for tests). */
	public static int spindles(Entity e) {
		Unweaving u = WEAVERS.get(e.getUUID());
		return u == null ? 0 : u.spindles.size();
	}

	// ------------------------------------------------------------------ The Loom Comes Down (3.43)

	/** Needles to a row, where the first falls (blocks out in front of it), how far apart they fall, how high they fall from. */
	private static final int NEEDLE_ROW = 8;
	private static final double NEEDLE_FIRST = 4, NEEDLE_GAP = 2.5, NEEDLE_FROM = 30;
	/** How long and how thick a great needle is. */
	private static final float NEEDLE_LENGTH = 14F, NEEDLE_THICK = 0.6F;

	/**
	 * The Loom Comes Down: its right arm raised, it yanks it down four times (at 24, 34, 44 and 54); each time a row of
	 * eight great needles falls from high over it, from four blocks in front of it towards the next of those it can reach
	 * (its prey first), a sigil burning under each where it will land. They land twelve beats on (at 36, 46, 56 and 66; never
	 * so soon that the sigils cannot be read), and each leaves a spike of calcite in the ground for a while, so the field
	 * fills with things to run round and stand behind.
	 */
	private static void needlefall(Weaver w, Unweaving u, ServerPlayer p, Beat b) {
		// (those already laid fall first: one laid this tick begins its fall on the next)
		fall(w, u);
		for (int k = 0; k < 4; k++) {
			if (b.at(24 + k * 10)) {
				row(w, u, p, k);
			}
		}
		if (b.past(90) && u.needles.isEmpty()) {
			end(w, u, 55);
		}
	}

	/** A yank of its arm: a row of great needles from in front of it towards the next of those it can reach, its prey first. */
	private static void row(Weaver w, Unweaving u, ServerPlayer prey, int k) {
		ServerLevel level = u.level;
		List<ServerPlayer> all = ElderFx.reachable(w, ElderPower.reach(44));
		all.remove(prey);
		all.sort(java.util.Comparator.comparingDouble(q -> q.distanceToSqr(w)));
		all.add(0, prey);
		ServerPlayer to = all.get(k % all.size());
		double dx = to.getX() - w.getX(), dz = to.getZ() - w.getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d < 0.5) {
			// (right under it: the way it faces)
			double yaw = Math.toRadians(w.getYRot());
			dx = -Math.sin(yaw);
			dz = Math.cos(yaw);
			d = 1;
		}
		double ux = dx / d, uz = dz / d;
		int left = u.delay(12);
		float damage = (float) Math.max(0, Config.WEAVER_NEEDLE_DAMAGE);
		BlockState calcite = ArenaDesign.mc("calcite", Blocks.END_STONE_BRICKS.defaultBlockState()), concrete = ArenaDesign.mc("white_concrete", calcite);
		Brightness lit = new Brightness(15, 15);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double floor = w.getY() - HOVER;
		for (int i = 0; i < NEEDLE_ROW; i++) {
			double r = NEEDLE_FIRST + NEEDLE_GAP * i;
			double x = w.getX() + ux * r, z = w.getZ() + uz * r;
			if (!Loaded.ready(level, BlockPos.containing(x, floor, z))) {
				continue;
			}
			double y = ElderFx.surface(level, x, floor, z, 4, 12);
			if (Double.isNaN(y)) {
				continue;
			}
			floor = y;
			ElderStrikes.strike(w, x, y, z, 1.6, damage, left, ElderStrikes.NEEDLE);
			// Leaning a little along its row, as if flung down from over its shoulder; thirty blocks up to begin with
			Quaternionf tilt = new Quaternionf().rotateAxis((float) (rnd.nextDouble(0.08, 0.2) * (rnd.nextBoolean() ? 1 : -1)),
					new Vector3f((float) -uz, 0, (float) ux));
			Display.BlockDisplay nd = BlockFlight.spawn(level, x, y + 1, z, i % 2 == 0 ? calcite : concrete, spike(tilt, NEEDLE_FROM - 1));
			if (nd != null) {
				((DisplayAccess) nd).extremedimensions$setBrightnessOverride(lit);
			}
			u.needles.add(new Needle(x, y, z, nd, tilt, k, left));
		}
		// its arm comes down with a crack of glass high over it
		WorldFx.soundIn(ElderFx.dim(w), "minecraft:block.amethyst_cluster.break", "hostile", w.getX(), w.getY() + 14, w.getZ(), 3f, 0.7f + 0.1f * k);
	}

	/**
	 * The look of a great needle whose point is {@code tipY} over its display (which stands one block over the ground it
	 * falls on), leaning as {@code tilt} turns it about its point.
	 */
	private static Transformation spike(Quaternionf tilt, double tipY) {
		Vector3f c = new Vector3f(NEEDLE_THICK / 2, 0, NEEDLE_THICK / 2);
		new Quaternionf(tilt).transform(c);
		return new Transformation(new Vector3f(-c.x, (float) tipY - c.y, -c.z), new Quaternionf(tilt), new Vector3f(NEEDLE_THICK, NEEDLE_LENGTH, NEEDLE_THICK),
				new Quaternionf());
	}

	/** Each tick of its needles: those just laid begin to fall, and those due land, each leaving a spike of calcite for a while. */
	private static void fall(Weaver w, Unweaving u) {
		if (u.needles.isEmpty()) {
			return;
		}
		ServerLevel level = u.level;
		Block stone = ArenaDesign.mc("calcite", Blocks.END_STONE_BRICKS.defaultBlockState()).getBlock();
		// Where each row landed this tick (sums of x, y and z, and how many), for its one sound and jolt
		Map<Integer, double[]> rows = new HashMap<>();
		for (Iterator<Needle> it = u.needles.iterator(); it.hasNext();) {
			Needle n = it.next();
			n.left--;
			if (!n.falling) {
				n.falling = true;
				if (n.display != null) {
					// (its point half a block into the ground as it lands)
					DisplayAccess acc = (DisplayAccess) n.display;
					acc.extremedimensions$setTransformation(spike(n.tilt, -1.5));
					acc.extremedimensions$setInterpolationDuration(Math.max(1, n.left));
					acc.extremedimensions$setInterpolationDelay(0);
				}
			}
			if (n.left > 0) {
				continue;
			}
			it.remove();
			if (n.display != null) {
				BlockFlight.drop(n.display);
			}
			// A spike where it went in, for a while: not up into whoever stands there, nor on one already standing
			BlockPos foot = BlockPos.containing(n.x, n.y, n.z);
			if (!underfoot(level, n.x, n.y, n.z) && !ElderTerrain.temporary(level, foot) && !ElderTerrain.temporary(level, foot.below())) {
				ElderTerrain.column(level, n.x, n.y + 1, n.z, 5, stone, 200);
			}
			WorldFx.particleIn(level, "minecraft:end_rod", n.x, n.y + 1, n.z, 0.3, 1.2, 0.3, 0.06, 8);
			WorldFx.particleIn(level, "minecraft:cloud", n.x, n.y + 0.3, n.z, 0.6, 0.2, 0.6, 0.05, 4);
			double[] r = rows.computeIfAbsent(n.row, key -> new double[4]);
			r[0] += n.x;
			r[1] += n.y;
			r[2] += n.z;
			r[3]++;
		}
		for (double[] r : rows.values()) {
			double x = r[0] / r[3], y = r[1] / r[3], z = r[2] / r[3];
			WorldFx.soundIn(ElderFx.dim(w), "extremedimensions:elder.weaver.needlefall", "hostile", x, y + 1, z, 5f, 1f);
			ElderFx.shake(w, x, z, 24, 0.35f, 8);
		}
	}

	/** Whether a player stands where a needle's spike would rise (it does not rise up into them). */
	private static boolean underfoot(ServerLevel level, double x, double y, double z) {
		AABB room = new AABB(Math.floor(x), y - 0.5, Math.floor(z), Math.floor(x) + 1, y + 5, Math.floor(z) + 1);
		for (ServerPlayer q : level.players()) {
			if (!q.isSpectator() && q.getBoundingBox().intersects(room)) {
				return true;
			}
		}
		return false;
	}

	/** Its needles still falling come down to nothing (its blow cut short, its fight over). */
	private static void dropNeedles(Unweaving u) {
		for (Needle n : u.needles) {
			if (n.display != null) {
				BlockFlight.drop(n.display);
			}
		}
		u.needles.clear();
	}

	/** How many great needles of its Loom are still falling (for tests). */
	public static int needlesFalling(Entity e) {
		Unweaving u = WEAVERS.get(e.getUUID());
		return u == null ? 0 : u.needles.size();
	}

	// ------------------------------------------------------------------ what its blows hold in the world

	/** The look of a display standing at (ox, oy, oz) that shows its block with its middle at (cx, cy, cz), turned and sized. */
	private static Transformation at(double ox, double oy, double oz, double cx, double cy, double cz, Quaternionf q, double size) {
		return BlockFlight.pose(cx - ox - 0.5, cy - oy + 0.5, cz - oz - 0.5, q, (float) size);
	}

	/** Moves a display to show its block there, over {@code ticks}. */
	private static void place(Display.BlockDisplay d, double ox, double oy, double oz, double cx, double cy, double cz, Quaternionf q, double size, int ticks) {
		DisplayAccess acc = (DisplayAccess) d;
		acc.extremedimensions$setTransformation(at(ox, oy, oz, cx, cy, cz, q, size));
		acc.extremedimensions$setInterpolationDuration(ticks);
		acc.extremedimensions$setInterpolationDelay(0);
	}

	/**
	 * What one of its blows holds in the world goes once another action has cut in (a daze, its ultimate, an anchor of the
	 * Knot Mother's breaking) or the blow is over: its grid, its tornadoes, its needles.
	 */
	private static void hold(Unweaving u) {
		if (u.warp && u.action != Weaver.WARP) {
			u.warp = false;
		}
		if (!u.spindles.isEmpty() && u.action != Weaver.SPINDLE) {
			dropSpindles(u);
		}
		if (!u.needles.isEmpty() && u.action != Weaver.NEEDLEFALL) {
			dropNeedles(u);
		}
	}

	/** Everything its blows hold in the world comes down (its fight over: slain, gone, the server stopping). */
	private static void unspin(Unweaving u) {
		if (u == null) {
			return;
		}
		u.warp = false;
		dropSpindles(u);
		dropNeedles(u);
	}

	/**
	 * The Last Tapestry: it rises high with its arms spread; threads run from its hands to everyone near while time slows
	 * for them, needles fall and a dome of threads closes over the ground; then everything is pulled taut and snaps, the
	 * ground below bursts, and its curse falls on everyone within reach.
	 */
	private static void tapestry(Weaver w, Unweaving u, Beat b) {
		ServerLevel level = u.level;
		String dim = ElderFx.dim(w);
		int length = ElderUltimate.length();
		double k = b.t / length;
		if (b.between(0, 40)) {
			u.rise += 0.15;
			w.motion.heave(w, 0.15);
		}
		if (b.at(1)) {
			// Its web is laid on the ground under it
			double gy = ElderFx.surface(level, w.getX(), w.getY(), w.getZ(), 4, 24);
			AttackFx.ring(level, 2, w.getX(), Double.isNaN(gy) ? w.getY() - HOVER : gy, w.getZ(), 26, (int) (length / Math.max(0.5, w.getTempo())));
		}
		if (b.every(2, 0)) {
			ElderFx.shake(w, w.getX(), w.getZ(), 64, (float) (0.1 + 0.4 * k), 4);
		}
		// Everyone its curse will fall on
		List<ServerPlayer> near = ElderFx.reachable(w, Math.max(64, Config.ELDER_ULTIMATE_RADIUS));
		if (b.between(30, length * 0.88) && b.every(3, 30)) {
			double yaw = Math.toRadians(w.getYRot());
			int i = 0;
			for (ServerPlayer q : near) {
				double side = (i++ % 2 == 0) ? 1 : -1;
				double hx = w.getX() - Math.cos(yaw) * 9.5 * side, hz = w.getZ() - Math.sin(yaw) * 9.5 * side;
				AttackFx.line(level, 2, b.t > length * 0.6 ? AttackFx.LINE_THREAD_TAUT : AttackFx.LINE_THREAD, hx, w.getY() + 13.5, hz,
						q.getX(), q.getY() + 1.2, q.getZ(), 3);
			}
		}
		if (b.every(20, 30)) {
			// Time slows for those it holds
			for (ServerPlayer q : near) {
				WorldFx.effect(q, MobEffects.SLOWNESS, 2, 1);
				WorldFx.effect(q, MobEffects.MINING_FATIGUE, 2, 1);
			}
		}
		if (b.between(40, length - 15) && b.every(8, 40)) {
			// The dome: threads from high over it down to a ring on the ground, turning
			double apexY = w.getY() + 10;
			for (int m = 0; m < 12; m++) {
				double a = 2 * Math.PI * m / 12 + b.t * 0.02;
				double gx = w.getX() + Math.cos(a) * 26, gz = w.getZ() + Math.sin(a) * 26;
				double gy = ElderFx.surface(level, gx, w.getY() - u.rise, gz, 8, 20);
				AttackFx.line(level, 2, AttackFx.LINE_THREAD_TAUT, w.getX(), apexY, w.getZ(), gx, Double.isNaN(gy) ? w.getY() - u.rise - HOVER : gy, gz, 8);
			}
			for (ServerPlayer q : near) {
				ElderStrikes.strike(w, q.getX(), ElderFx.groundUnder(q), q.getZ(), 2.0, 5f, 16, ElderStrikes.NEEDLE);
			}
		}
		lanceRings(w, u, b, length);
		if (b.every(20, 0)) {
			ElderFight.markPlayers(w, near, "minecraft:end_rod");
		}
		if (b.at(length - 10)) {
			// Everything snaps
			double gy = w.getY() - u.rise - HOVER;
			w.motion.heave(w, -u.rise);
			u.rise = 0;
			if (w instanceof KnotMother) {
				// every thread of hers snapping at once, and her cry over it
				WorldFx.soundIn(dim, "extremedimensions:elder.knot_mother.anchor_break", "hostile", w.getX(), w.getY() + 10, w.getZ(), 6f, 0.8f);
				WorldFx.soundIn(dim, "extremedimensions:elder.knot_mother.roar", "hostile", w.getX(), w.getY() + 10, w.getZ(), 6f, 1f);
			} else {
				WorldFx.soundIn(dim, "minecraft:block.glass.break", "hostile", w.getX(), w.getY() + 10, w.getZ(), 6f, 0.5f);
				WorldFx.soundIn(dim, "minecraft:block.amethyst_block.break", "hostile", w.getX(), w.getY() + 10, w.getZ(), 6f, 0.4f);
				WorldFx.soundIn(dim, "minecraft:entity.warden.sonic_boom", "hostile", w.getX(), w.getY() + 10, w.getZ(), 6f, 0.6f);
			}
			WorldFx.particleIn(level, "minecraft:explosion_emitter", w.getX(), gy + 1, w.getZ(), 3, 1, 3, 0, 4);
			ElderTerrain.blast(w, w.getX(), gy + 0.5, w.getZ(), 3.0f, false);
			ElderStrikes.wave(w, w.getX(), gy, w.getZ(), 1.2, 34, 9f, "minecraft:end_rod", ElderTerrain.Mark.SCORCH);
			ElderTerrain.mark(level, w.getX(), gy, w.getZ(), 14, 0.5, ElderTerrain.Mark.SCORCH);
			AttackFx.burst(level, 2, w.getX(), gy + 1, w.getZ(), 8);
			u.breakUltimate(w, boss(w));
		}
		if (b.past(length)) {
			u.closeUltimate(w);
		}
	}

	/**
	 * In its ultimate the Lance sweeps whole circles: rings of beams round it, each turning the other way, marked on the
	 * ground before they fire.
	 */
	private static void lanceRings(Weaver w, Unweaving u, Beat b, int length) {
		int rings = Math.max(0, Math.min(4, Config.WEAVER_ULTIMATE_RINGS));
		if (rings == 0) {
			return;
		}
		double fire = Math.max(2, length * 0.25), warn = Math.max(1, fire - 20);
		double gy = w.getY() - u.rise - HOVER;
		double[] h = halo(w);
		if (b.at(warn)) {
			for (int i = 0; i < rings; i++) {
				AttackFx.ring(u.level, 2, w.getX(), gy + 0.1, w.getZ(), ringRadius(i), (int) (fire - warn) + 6);
			}
			WorldFx.soundIn(ElderFx.dim(w), "minecraft:block.beacon.activate", "hostile", h[0], h[1], h[2], 6f, 0.4f);
		}
		if (b.between(warn, fire)) {
			WorldFx.particleIn(u.level, "minecraft:end_rod", h[0], h[1], h[2], 3, 3, 3, -0.2, 5);
		}
		if (b.at(fire)) {
			int duration = (int) Math.max(20, length * 0.5);
			double base = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
			for (int i = 0; i < rings; i++) {
				double r = ringRadius(i), a0 = base + Math.PI * i, way = i % 2 == 0 ? 1 : -1;
				ElderStrikes.beam(w, h[0], h[1], h[2], w.getX(), w.getZ(), a0, a0 + way * Math.PI * 2, r, r, gy, duration,
						(float) Config.WEAVER_ULTIMATE_RING_DAMAGE);
			}
			WorldFx.soundIn(ElderFx.dim(w), "minecraft:entity.warden.sonic_boom", "hostile", h[0], h[1], h[2], 6f, 0.9f);
			ElderFx.shake(w, w.getX(), w.getZ(), 48, 0.7f, 20);
		}
	}

	/** How far out ring {@code i} of its ultimate's Lance sweeps. */
	static double ringRadius(int i) {
		return 11 + i * 9;
	}

	/** Its kin come: Thinned from the village (only in the Seam, where they belong). */
	private static void kin(Weaver w, int count) {
		ServerLevel level = (ServerLevel) w.level();
		if (!Dims.SEAM.equals(Dims.of(level))) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (int i = 0; i < count; i++) {
			BlockPos spot = ElderFx.ground(level, (int) Math.floor(w.getX()) + rnd.nextInt(-7, 8), (int) w.getY() + 2, (int) Math.floor(w.getZ()) + rnd.nextInt(-7, 8), 10);
			if (spot != null) {
				SeamFauna.summonThinned(level, spot);
			}
		}
		WorldFx.soundIn(ElderFx.dim(w), "extremedimensions:lurker_whisper", "hostile", w.getX(), w.getY() + 6, w.getZ(), 4f, 1.4f);
	}

	/** One of its blows landed: it unpicks some of the thread that holds the player in the Seam. */
	public static void unweave(ServerPlayer p) {
		PlayerData d = DataStore.get(p.getUUID());
		if (!d.inSeam || d.seamPull > 0 || p.isCreative()) {
			return;
		}
		int theft = Math.max(0, Config.WEAVER_TIME_THEFT);
		if (theft <= 0) {
			return;
		}
		d.seamLeft = Math.max(1, d.seamLeft - theft);
		WorldFx.particles(p, "extremedimensions:soul_mote", 1.0, 0.5, 0.0, 10);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.weaver_unpicks", theft), true);
	}

	/** Its fight's music: the Last Weaver's "Final Collapse", or the Knot Mother's own "Mother of Knots". */
	private static int track(Weaver w) {
		return w instanceof KnotMother ? ElderFx.MUSIC_KNOT_MOTHER : ElderFx.MUSIC_WEAVER;
	}

	/** Slain: its thread is given back to everyone near, with silk, fragments and a relic. */
	public static void onDefeat(Weaver w, Entity killer) {
		Unweaving u = WEAVERS.remove(w.getUUID());
		untie(u);
		unspin(u);
		ElderArena.release(w);
		ElderStrikes.forget(w);
		ElderFx.silence(w, track(w));
		if (u == null) {
			return;
		}
		u.bar.removeAllPlayers();
		ServerLevel level = (ServerLevel) w.level();
		ElderFx.burst(w, "extremedimensions:soul_mote", 160);
		ElderFx.burst(w, "minecraft:end_rod", 60);
		AttackFx.burst(level, 2, w.getX(), w.getY() + 2, w.getZ(), 9);
		ElderFx.cinema(w, 160, ModPackets.BossFxPayload.ULTIMATE_END, 0, 0, "");
		boolean knot = w instanceof KnotMother;
		for (ItemStack s : Loot.roll((ServerLevel) w.level(), knot ? Loot.KNOT_MOTHER : Loot.WEAVER, w.position())) {
			ElderFx.loot(w, u.target, s);
		}
		for (int i = 0; i < Math.max(0, knot ? Config.KNOT_MOTHER_RELICS : Config.WEAVER_RELICS); i++) {
			ElderFx.loot(w, u.target, RelicService.random());
		}
		if (knot) {
			com.extremedimensions.seam.FirstKnot.slain(level.getServer().overworld().getGameTime());
		}
		int gift = Math.max(0, Config.WEAVER_TIME_GIFT);
		List<ServerPlayer> there = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(w) >= 64 * 64) {
				continue;
			}
			there.add(p);
			PlayerData d = DataStore.get(p.getUUID());
			if (knot) {
				Advancements.grant(p, Advancements.KNOT_MOTHER);
				Journal.unlock(p, d, JournalEntry.FIRST_KNOT);
			} else {
				Advancements.grant(p, Advancements.WEAVER_SLAIN);
				Journal.unlock(p, d, JournalEntry.LAST_WEAVER);
			}
			com.extremedimensions.tapestry.Tapestry.earn(p, knot ? com.extremedimensions.tapestry.Tapestry.Panel.KNOT
					: com.extremedimensions.tapestry.Tapestry.Panel.UNRAVELLING);
			if (d.inSeam && gift > 0) {
				d.seamLeft += gift;
				d.seamMax = Math.max(d.seamMax, d.seamLeft);
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.weaver_gift", gift));
			}
		}
		DataStore.markDirty();
		com.extremedimensions.compat.DiscordWebhook.elder(knot ? com.extremedimensions.compat.StatsExport.Elder.KNOT_MOTHER
				: com.extremedimensions.compat.StatsExport.Elder.WEAVER, there);
	}

	/** Gone: it unravels into the dark, whole. */
	public static void fade(Weaver w) {
		Unweaving u = WEAVERS.remove(w.getUUID());
		untie(u);
		unspin(u);
		ElderArena.release(w);
		if (u != null && u.village != null) {
			// Left unfinished (its prey gone): the village it rose in may wake a Weaver again, on the next first visit
			DataStore.seamCells().remove(u.village);
			DataStore.markDirty();
		}
		if (u != null) {
			u.abandonUltimate(boss(w));
			u.bar.removeAllPlayers();
			if (u.action == ElderBoss.ULTIMATE) {
				ElderFx.cinema(w, 160, ModPackets.BossFxPayload.ULTIMATE_END, 0, 0, "");
			}
		}
		ElderStrikes.forget(w);
		ElderFx.silence(w, track(w));
		ElderFx.burst(w, "extremedimensions:soul_mote", 120);
		w.discard();
	}
}
