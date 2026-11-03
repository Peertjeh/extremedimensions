package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.lore.Loot;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.seam.SeamFauna;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * The Last Weaver: once the hunger at the Heart is ended, the weave stirs. The first visit to a village of the Thinned
 * may wake its Weaver. It floats above the ground and keeps its distance; it casts threads that hold players to the
 * spot they stood on (and drain the Seam faster while they hold), rains needles of light, flings back anyone who comes
 * too close, sweeps the ground with a beam from its halo that carves it, strings a web of threads across the ground
 * that cuts when it is pulled taut, and brings down spirals of needles with a great needle last of all. Twice it calls
 * its kin. At half its health it weaves the Last Tapestry: it rises, threads run from its hands to everyone near while
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

	/** Seam marks of anchors still in the world ("ka@dim@x,y,z"), cleared when they are untied. */
	private static final String ANCHOR_MARK = "ka@";

	/** Its fight, if it is fighting. */
	static ElderFight fight(Entity e) {
		return WEAVERS.get(e.getUUID());
	}

	public static boolean isDriven(Entity e) {
		return WEAVERS.containsKey(e.getUUID());
	}

	/** Starts one of its blows (or its ultimate) at once; for tests and operators. False if it is not fighting. */
	public static boolean force(Entity e, int action) {
		Unweaving fight = WEAVERS.get(e.getUUID());
		if (fight == null || !(e instanceof Weaver w)) {
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
		}
		WEAVERS.clear();
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
		// Each village rolls once ("wv@"; before 2.22 the roll was kept as "w@x,z", which wells also use)
		if (DataStore.seamCells().contains("w@" + vx + "," + vz) || !DataStore.seamCells().add("wv@" + vx + "," + vz)) {
			return;
		}
		DataStore.markDirty();
		// Each village that woke none makes the next likelier (weaver_pity), so a long run of empty villages ends
		PlayerData d = DataStore.get(p.getUUID());
		if (ThreadLocalRandom.current().nextDouble() < villageChance(d.weaverMisses) && summon(p, new BlockPos(vx, vy + 1, vz))) {
			d.weaverMisses = 0;
			for (Unweaving u : WEAVERS.values()) {
				if (p.getUUID().equals(u.target) && u.village == null) {
					u.village = "wv@" + vx + "," + vz;
				}
			}
		} else {
			d.weaverMisses++;
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
		ElderFx.music(w, ElderFx.MUSIC_WEAVER);
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
			ElderFx.music(w, ElderFx.MUSIC_WEAVER);
		}
		u.keepBar(w);
		if (u.ticks % 3 == 0) {
			WorldFx.particleIn(u.level, "extremedimensions:soul_mote", w.getX(), w.getY() + 13, w.getZ(), 9, 3, 2, 0.0, 2);
			ElderFx.ambience(w);
		}
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
					a = dist < 8 && rise < 8 && rise > -4 ? u.pick(Weaver.UNRAVEL, 4, Weaver.LANCE, (lance + 1) / 2, Weaver.CRADLE, 1)
							: u.pick(Weaver.THREAD, 2, Weaver.NEEDLES, 2, Weaver.LANCE, lance, Weaver.CRADLE, 2, Weaver.THOUSAND, 2);
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
			u.level.setBlock(at, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
			u.level.setBlock(at.above(), Blocks.OCHRE_FROGLIGHT.defaultBlockState(), 3);
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

	/** Slain: its thread is given back to everyone near, with silk, fragments and a relic. */
	public static void onDefeat(Weaver w, Entity killer) {
		Unweaving u = WEAVERS.remove(w.getUUID());
		untie(u);
		ElderArena.release(w);
		ElderStrikes.forget(w);
		ElderFx.silence(w, ElderFx.MUSIC_WEAVER);
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
		ElderFx.silence(w, ElderFx.MUSIC_WEAVER);
		ElderFx.burst(w, "extremedimensions:soul_mote", 120);
		w.discard();
	}
}
