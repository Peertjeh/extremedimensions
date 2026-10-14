package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.server.Dims;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * The Ferryman: on a long Nether expedition he may come to collect. His boat sails over lava and stone alike,
 * closing in and then circling his passenger, his hooded head turned to them. While he sails the Tether drains faster
 * (faster still once he is wounded below half) and each of his blows takes levels. He brings his pole down in a line of
 * fire, drags his passenger back with a chain, sends soul lanterns drifting after them, rings a knell, charges straight
 * through them splitting the ground, raises cages of chains round them that crush inwards, and sends tides of soul
 * fire rolling out over the ground. At half his health he sounds the Final Toll: the boat rises, soul fire rains for ten
 * seconds and three bells ring, and when the boat comes down his curse falls on everyone within reach. Slain, he leaves
 * fragments and perhaps a relic and waives a stack of the Ferryman's Fee; outrun home, he collects his fare.
 */
public final class FerrymanService {
	private FerrymanService() {}

	private static final String BOSS = "boss.extremedimensions.ferryman";
	private static final String ULT = "gui.extremedimensions.elder.ultimate.ferryman";

	private static final class Fare extends ElderFight {
		int circle = 1;
		boolean doubled;
		double dirX, dirZ, lift;
		final Set<UUID> struck = new HashSet<>();
		final List<double[]> cages = new ArrayList<>();
	}

	private static final Map<UUID, Fare> FARES = new HashMap<>();

	public static boolean isDriven(Entity e) {
		return FARES.containsKey(e.getUUID());
	}

	/** Starts one of its blows (or its ultimate) at once; for tests and operators. False if it is not fighting. */
	public static boolean force(Entity e, int action) {
		Fare fight = FARES.get(e.getUUID());
		if (fight == null || !(e instanceof Ferryman f)) {
			return false;
		}
		if (action == ElderBoss.ULTIMATE) {
			fight.lift = 0;
		}
		if (action == ElderBoss.ULTIMATE) {
			fight.beginUltimate(f, ULT);
		} else {
			fight.start(f, action);
		}
		return true;
	}

	public static void clear() {
		for (Fare f : FARES.values()) {
			f.bar.removeAllPlayers();
		}
		FARES.clear();
	}

	public static boolean unlocked() {
		return switch (Config.FERRYMAN_UNLOCK) {
			case 0 -> true;
			case 1 -> DataStore.sealBroken();
			default -> !DataStore.ending().isEmpty();
		};
	}

	/** Whether the Ferryman is after this player. */
	public static boolean hunting(ServerPlayer p) {
		for (Fare f : FARES.values()) {
			if (p.getUUID().equals(f.target)) {
				return true;
			}
		}
		return false;
	}

	/** The Tether's drain for this player while the Ferryman sails (1 without him). */
	public static double drainFactor(ServerPlayer p) {
		for (Fare f : FARES.values()) {
			if (p.getUUID().equals(f.target)) {
				return Math.max(0.1, Config.FERRYMAN_DRAIN) * (f.doubled ? 1.5 : 1.0);
			}
		}
		return 1.0;
	}

	public static void tick(MinecraftServer s) {
		drive(s);
		if (s.getTickCount() % 20 != 0 || !unlocked()) {
			return;
		}
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			PlayerData d = DataStore.get(p.getUUID());
			if (!d.inRun || d.ferrymanRolled || d.runActive < Config.FERRYMAN_AFTER_SECONDS || p.isCreative() || p.isSpectator()
					|| !Dims.NETHER.equals(Dims.of(p))) {
				continue;
			}
			// One chance per expedition; the Fee he is owed makes him likelier
			d.ferrymanRolled = true;
			double chance = Math.min(1, Math.max(0, Config.FERRYMAN_CHANCE) * (1 + d.owned(Penalty.FERRYMANS_FEE)));
			if (ThreadLocalRandom.current().nextDouble() < chance) {
				summon(p);
			}
		}
	}

	/** He comes for this player (also the operator command); false if he is already after them or cannot sail in. */
	public static boolean summon(ServerPlayer p) {
		return !hunting(p) && summonAt(p, ElderFx.near((ServerLevel) p.level(), p, 20, 28)) != null;
	}

	/** He comes for this player, his boat at this spot; the Ferryman, or null if he is already after them. */
	public static Ferryman summonAt(ServerPlayer p, BlockPos spot) {
		if (hunting(p)) {
			return null;
		}
		ServerLevel level = (ServerLevel) p.level();
		Ferryman f = ModEntities.FERRYMAN.spawn(level, spot, EntitySpawnReason.EVENT);
		if (f == null) {
			return null;
		}
		f.setPersistenceRequired();
		f.setTarget(p);
		float yaw = ElderFx.yawTo(f, p.getX(), p.getZ());
		f.setYRot(yaw);
		f.setYBodyRot(yaw);
		f.setYHeadRot(yaw);
		Fare fare = new Fare();
		fare.target = p.getUUID();
		fare.level = level;
		fare.circle = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
		fare.makeBar(BOSS, BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10, p);
		fare.cooldown = 60;
		FARES.put(f.getUUID(), fare);
		ElderFx.roar(f, 4f, 0.7f);
		WorldFx.soundIn(ElderFx.dim(f), "minecraft:block.bell.use", "hostile", f.getX(), f.getY() + 8, f.getZ(), 5f, 0.45f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.ferryman_comes"));
		ElderFx.cinema(f, 96, ModPackets.BossFxPayload.INTRO, 0, 0, "");
		ElderFx.shake(f, f.getX(), f.getZ(), 48, 0.5f, 30);
		Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.FERRYMAN);
		ElderFx.music(f, ElderFx.MUSIC_FERRYMAN);
		return f;
	}

	private static void drive(MinecraftServer s) {
		if (FARES.isEmpty()) {
			return;
		}
		List<Runnable> later = new ArrayList<>();
		Iterator<Map.Entry<UUID, Fare>> it = FARES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Fare> e = it.next();
			Fare fare = e.getValue();
			Entity ent = fare.level.getEntity(e.getKey());
			if (!(ent instanceof Ferryman f) || !f.isAlive()) {
				fare.bar.removeAllPlayers();
				it.remove();
				continue;
			}
			fare.ticks++;
			ServerPlayer p = s.getPlayerList().getPlayer(fare.target);
			if (p == null || !p.isAlive() || p.level() != f.level() || f.distanceToSqr(p) > 80 * 80) {
				// Gone home with him still sailing: he collects his fare on the way out
				if (p != null && p.isAlive() && p.level() != f.level() && !DataStore.get(p.getUUID()).inRun) {
					later.add(() -> collect(p));
				}
				later.add(() -> fade(f));
				continue;
			}
			sail(f, fare, p);
		}
		later.forEach(Runnable::run);
	}

	private static void sail(Ferryman f, Fare fare, ServerPlayer p) {
		ServerLevel level = fare.level;
		String dim = ElderFx.dim(f);
		if (fare.ticks % 20 == 0) {
			f.setTarget(p);
		}
		if (fare.ticks % 20 == 1) {
			ElderFx.music(f, ElderFx.MUSIC_FERRYMAN);
		}
		fare.keepBar(f);
		if (fare.ticks % 3 == 0) {
			WorldFx.particleIn(level, "minecraft:soul_fire_flame", f.getX(), f.getY() + 0.3, f.getZ(), 2.5, 0.1, 6, 0.01, 2);
			ElderFx.ambience(f);
		}
		if (!fare.doubled && f.getHealth() <= f.getMaxHealth() / 2) {
			fare.doubled = true;
			fare.enraged = true;
			ElderFx.roar(f, 4f, 0.6f);
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.ferryman_doubles"), true);
		}
		double dx = p.getX() - f.getX(), dz = p.getZ() - f.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (fare.action == ElderBoss.IDLE) {
			if (ElderUltimate.due(f, fare.ultimateSpent)) {
				fare.lift = 0;
				fare.beginUltimate(f, ULT);
				WorldFx.soundIn(dim, "minecraft:block.bell.use", "hostile", f.getX(), f.getY() + 8, f.getZ(), 6f, 0.4f);
				ElderFx.roar(f, 6f, 0.5f);
				return;
			}
			// Closing in, then circling: the boat points where it sails, the hooded head turns to its passenger
			double tx, tz, speed;
			if (dist > 15) {
				tx = p.getX();
				tz = p.getZ();
				speed = 0.26;
			} else {
				double a = Math.atan2(f.getZ() - p.getZ(), f.getX() - p.getX()) + fare.circle * 0.45;
				tx = p.getX() + Math.cos(a) * 12;
				tz = p.getZ() + Math.sin(a) * 12;
				speed = 0.17;
			}
			f.motion.toward(f, tx, tz, ElderFight.speed(speed), 0.018, 0.1);
			if (f.motion.speed() > 0.03) {
				f.motion.turn(f, (float) (Math.atan2(f.motion.vz, f.motion.vx) * 180 / Math.PI) - 90f, 3f);
			}
			ElderFx.look(f, p);
			wake(f);
			if (fare.ready()) {
				int a = dist <= 8 ? fare.pick(Ferryman.KNELL, 3, Ferryman.TIDE, 2, Ferryman.GAOL, 1)
						: dist <= 20 ? fare.pick(Ferryman.SLAM, 3, Ferryman.CROSSING, 3, Ferryman.TIDE, 2, Ferryman.GAOL, 2, Ferryman.LANTERN, 2, Ferryman.CHAIN, 1)
						: fare.pick(Ferryman.CROSSING, 3, Ferryman.LANTERN, 3, Ferryman.CHAIN, 2, Ferryman.GAOL, 1);
				fare.start(f, a);
			}
			return;
		}
		fare.step();
		Beat b = fare.beat;
		if (fare.action != Ferryman.CROSSING && fare.action != ElderBoss.ULTIMATE) {
			// Mid-blow the boat drifts to a stop, turned to its passenger
			f.motion.turn(f, ElderFx.yawTo(f, p.getX(), p.getZ()), 3f);
			f.motion.brake(f, 0.02, 0.1);
			ElderFx.look(f, p);
		}
		switch (fare.action) {
			case Ferryman.SLAM -> {
				if (b.at(24)) {
					slam(f, fare, p);
				}
				if (b.past(42)) {
					end(f, fare, 55);
				}
			}
			case Ferryman.CHAIN -> {
				if (b.at(10)) {
					ElderFx.line(dim, "minecraft:soul_fire_flame", f.getX(), f.getY() + 9, f.getZ(), p.getX(), p.getY() + 1, p.getZ(), 24);
					WorldFx.soundIn(dim, "minecraft:block.chain.break", "hostile", p.getX(), p.getY(), p.getZ(), 3f, 0.5f);
					ElderFx.pull(f, p, 9);
					WorldFx.effect(p, MobEffects.SLOWNESS, 2, 1);
				}
				if (b.past(24)) {
					end(f, fare, 50);
				}
			}
			case Ferryman.LANTERN -> {
				if (b.at(16)) {
					double yaw = Math.toRadians(f.getYRot());
					int n = 0;
					for (ServerPlayer q : ElderFx.targets(f, 48)) {
						if (n >= 4) {
							break;
						}
						for (int i = 0; i < (q == p ? 3 : 1); i++) {
							double side = (i - 1) * 3.0;
							ElderStrikes.wisp(f, f.getX() - Math.cos(yaw) * side, f.getY() + 15, f.getZ() - Math.sin(yaw) * side, q, 6f);
						}
						n++;
					}
					WorldFx.soundIn(dim, "minecraft:entity.vex.charge", "hostile", f.getX(), f.getY() + 14, f.getZ(), 3f, 0.5f);
				}
				if (b.past(34)) {
					end(f, fare, 55);
				}
			}
			case Ferryman.KNELL -> {
				if (b.at(8)) {
					WorldFx.soundIn(dim, "minecraft:block.bell.use", "hostile", f.getX(), f.getY() + 8, f.getZ(), 5f, 0.55f);
					ElderFx.shake(f, f.getX(), f.getZ(), 30, 0.6f, 12);
					ElderStrikes.wave(f, f.getX(), f.getY(), f.getZ(), 0.6, 13, 5f, "minecraft:soul", ElderTerrain.Mark.SOUL);
					for (ServerPlayer q : ElderFx.targets(f, 14)) {
						WorldFx.effect(q, MobEffects.WEAKNESS, 4, 1);
						ElderFx.shove(f, q, 1.2, 0.35);
					}
				}
				if (b.past(22)) {
					end(f, fare, 50);
				}
			}
			case Ferryman.CROSSING -> crossing(f, fare, p, b);
			case Ferryman.GAOL -> gaol(f, fare, b);
			case Ferryman.TIDE -> tide(f, fare, b);
			case ElderBoss.ULTIMATE -> finalToll(f, fare, b);
			default -> end(f, fare, 40);
		}
	}

	/** The wake of the boat: soul fire and ash behind it. */
	private static void wake(Ferryman f) {
		if (f.motion.speed() > 0.08) {
			double yaw = Math.toRadians(f.getYRot());
			double bx = f.getX() + Math.sin(yaw) * 8, bz = f.getZ() - Math.cos(yaw) * 8;
			WorldFx.particleIn(f.level(), "minecraft:soul_fire_flame", bx, f.getY() + 0.4, bz, 1.2, 0.1, 1.2, 0.02, 3);
			WorldFx.particleIn(f.level(), "minecraft:white_ash", bx, f.getY() + 0.8, bz, 1.5, 0.3, 1.5, 0.01, 4);
		}
	}

	private static void end(Ferryman f, Fare fare, double pause) {
		fare.end(f, pause);
		if (ThreadLocalRandom.current().nextInt(3) == 0) {
			fare.circle = -fare.circle;
		}
	}

	/** The pole comes down: a line of fire runs out from the boat towards its passenger. */
	private static void slam(Ferryman f, Fare fare, ServerPlayer p) {
		double dx = p.getX() - f.getX(), dz = p.getZ() - f.getZ();
		double d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
		ServerLevel level = (ServerLevel) f.level();
		for (int i = 0; i < 7; i++) {
			double k = 3 + i * 3;
			double x = f.getX() + dx / d * k, z = f.getZ() + dz / d * k;
			double y = ElderFx.surface(level, x, p.getY() + 4, z, 4, 12);
			ElderStrikes.strike(f, x, Double.isNaN(y) ? p.getY() : y, z, 2.6, 10f, fare.delay(6 + i * 3), ElderStrikes.SLAM);
		}
		WorldFx.soundIn(ElderFx.dim(f), "minecraft:entity.iron_golem.attack", "hostile", f.getX(), f.getY() + 4, f.getZ(), 4f, 0.4f);
		ElderFx.shake(f, f.getX(), f.getZ(), 30, 0.6f, 10);
	}

	/** Crossing of the Dead: the boat backs off, then charges straight through its passenger, splitting the ground. */
	private static void crossing(Ferryman f, Fare fare, ServerPlayer p, Beat b) {
		ServerLevel level = fare.level;
		String dim = ElderFx.dim(f);
		if (b.at(1)) {
			ElderFx.call(f, "crossing_of_the_dead");
			WorldFx.soundIn(dim, "minecraft:block.bell.use", "hostile", f.getX(), f.getY() + 8, f.getZ(), 5f, 0.7f);
		}
		if (b.between(0, 20)) {
			double dx = p.getX() - f.getX(), dz = p.getZ() - f.getZ();
			double d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
			f.motion.drive(f, -dx / d * 0.18, -dz / d * 0.18, 0.03, 0.1);
			f.motion.turn(f, ElderFx.yawTo(f, p.getX(), p.getZ()), 5f);
			ElderFx.look(f, p);
			if (fare.ticks % 3 == 0) {
				for (int i = 4; i < 40; i += 2) {
					WorldFx.particleIn(level, "minecraft:soul_fire_flame", f.getX() + dx / d * i, p.getY() + 0.2, f.getZ() + dz / d * i, 0.5, 0, 0.5, 0, 1);
				}
			}
		}
		if (b.at(20)) {
			double dx = p.getX() - f.getX(), dz = p.getZ() - f.getZ();
			double d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
			fare.dirX = dx / d;
			fare.dirZ = dz / d;
			fare.struck.clear();
			WorldFx.soundIn(dim, "minecraft:item.trident.riptide_3", "hostile", f.getX(), f.getY() + 4, f.getZ(), 5f, 0.5f);
			ElderFx.shake(f, f.getX(), f.getZ(), 30, 0.6f, 10);
		}
		if (b.between(20, 46)) {
			double sp = ElderFight.speed(0.85);
			f.motion.drive(f, fare.dirX * sp, fare.dirZ * sp, 0.3, 0.1);
			f.motion.turn(f, (float) (Math.atan2(fare.dirZ, fare.dirX) * 180 / Math.PI) - 90f, 8f);
			ElderFx.look(f, p);
			double fx = f.getX() + fare.dirX * 7, fz = f.getZ() + fare.dirZ * 7;
			ElderTerrain.furrow(level, fx, f.getY(), fz, 2.2, 1, 3, 4);
			if (fare.ticks % 2 == 0) {
				ElderTerrain.mark(level, f.getX() - fare.dirX * 6, f.getY(), f.getZ() - fare.dirZ * 6, 2, 0.5, ElderTerrain.Mark.SOUL);
			}
			WorldFx.particleIn(level, "minecraft:soul_fire_flame", fx, f.getY() + 1, fz, 2, 0.6, 2, 0.08, 10);
			WorldFx.particleIn(level, "minecraft:soul", f.getX(), f.getY() + 3, f.getZ(), 3, 2, 3, 0.02, 4);
			for (ServerPlayer q : ElderFx.targets(f, 12)) {
				double qx = q.getX() - f.getX(), qz = q.getZ() - f.getZ();
				// Along the hull (it is long) and within reach of it
				double along = qx * fare.dirX + qz * fare.dirZ, across = Math.abs(-qx * fare.dirZ + qz * fare.dirX);
				if (fare.struck.contains(q.getUUID()) || Math.abs(along) > 10 || across > 3.6 || q.getY() > f.getY() + 8) {
					continue;
				}
				fare.struck.add(q.getUUID());
				q.hurtServer(level, level.damageSources().mobAttack(f), 14f);
				double side = (-qx * fare.dirZ + qz * fare.dirX) >= 0 ? 1 : -1;
				q.setDeltaMovement(-fare.dirZ * side * 1.3 + fare.dirX * 0.5, 0.7, fare.dirX * side * 1.3 + fare.dirZ * 0.5);
				q.needsSync = true;
			}
		}
		if (b.at(46)) {
			ElderStrikes.wave(f, f.getX() + fare.dirX * 8, f.getY(), f.getZ() + fare.dirZ * 8, 0.6, 9, 5f, "minecraft:soul_fire_flame", ElderTerrain.Mark.SOUL);
		}
		if (b.past(46)) {
			f.motion.brake(f, 0.08, 0.1);
		}
		if (b.past(60)) {
			end(f, fare, 55);
		}
	}

	/** Gaol of Chains: a cage of chains bursts out of the ground round each passenger, then crushes inwards. */
	private static void gaol(Ferryman f, Fare fare, Beat b) {
		ServerLevel level = fare.level;
		String dim = ElderFx.dim(f);
		if (b.at(1)) {
			ElderFx.call(f, "gaol_of_chains");
			WorldFx.soundIn(dim, "minecraft:block.chain.break", "hostile", f.getX(), f.getY() + 8, f.getZ(), 5f, 0.4f);
		}
		if (b.at(12)) {
			fare.cages.clear();
			int n = 0;
			for (ServerPlayer q : ElderFx.targets(f, 40)) {
				if (n++ >= 3) {
					break;
				}
				double cx = q.getX(), cy = q.getY(), cz = q.getZ();
				fare.cages.add(new double[] {cx, cy, cz});
				for (int i = 0; i < 14; i++) {
					double a = 2 * Math.PI * i / 14;
					ElderTerrain.column(level, cx + Math.cos(a) * 3.2, cy, cz + Math.sin(a) * 3.2, 4, Blocks.IRON_BARS, 90);
				}
				ElderFx.ring(level, "minecraft:soul_fire_flame", cx, cy + 0.2, cz, 3.2, 28, 0);
				WorldFx.effect(q, MobEffects.SLOWNESS, 4, 1);
				WorldFx.soundIn(dim, "minecraft:block.chain.place", "hostile", cx, cy, cz, 3f, 0.5f);
			}
		}
		if (b.between(12, 36) && fare.ticks % 3 == 0) {
			for (double[] c : fare.cages) {
				double r = 3.2 - 2.2 * (b.t - 12) / 24.0;
				ElderFx.ring(level, "minecraft:soul", c[0], c[1] + 1 + (b.t % 3), c[2], r, 16, b.t * 0.2);
			}
		}
		if (b.at(36)) {
			for (double[] c : fare.cages) {
				WorldFx.particleIn(level, "minecraft:soul_fire_flame", c[0], c[1] + 1, c[2], 1.2, 1, 1.2, 0.1, 50);
				WorldFx.soundIn(dim, "minecraft:entity.iron_golem.damage", "hostile", c[0], c[1], c[2], 3f, 0.4f);
				for (ServerPlayer q : ElderFx.targets(f, 60)) {
					if (q.distanceToSqr(c[0], c[1], c[2]) < 3.6 * 3.6) {
						q.hurtServer(level, level.damageSources().mobAttack(f), 14f);
					}
				}
			}
		}
		if (b.past(48)) {
			end(f, fare, 55);
		}
	}

	/** Tide of the Styx: the pole planted in the boat, two tides of soul fire roll out over the ground. */
	private static void tide(Ferryman f, Fare fare, Beat b) {
		String dim = ElderFx.dim(f);
		if (b.at(1)) {
			ElderFx.call(f, "tide_of_the_styx");
		}
		if (b.between(0, 18)) {
			WorldFx.particleIn(fare.level, "minecraft:soul", f.getX(), f.getY() + 6, f.getZ(), 4, 3, 4, 0.05, 4);
		}
		if (b.at(18)) {
			WorldFx.soundIn(dim, "minecraft:entity.generic.splash", "hostile", f.getX(), f.getY() + 2, f.getZ(), 5f, 0.4f);
			WorldFx.soundIn(dim, "minecraft:entity.blaze.shoot", "hostile", f.getX(), f.getY() + 2, f.getZ(), 5f, 0.4f);
			ElderFx.shake(f, f.getX(), f.getZ(), 36, 0.8f, 16);
			ElderStrikes.wave(f, f.getX(), f.getY(), f.getZ(), 0.75, 24, 10f, "minecraft:soul_fire_flame", ElderTerrain.Mark.SOUL);
			ElderTerrain.mark(fare.level, f.getX(), f.getY(), f.getZ(), 6, 0.7, ElderTerrain.Mark.SOUL);
		}
		if (b.at(28)) {
			ElderStrikes.wave(f, f.getX(), f.getY(), f.getZ(), 0.6, 20, 6f, "minecraft:soul", ElderTerrain.Mark.SOUL);
		}
		if (b.past(44)) {
			end(f, fare, 60);
		}
	}

	/**
	 * The Final Toll: the boat rises out of the ground into the air; soul fire rains round it and onto everyone it
	 * marks while the bell tolls three times; then the boat comes down, the ground bursts in soul fire and his curse
	 * falls on everyone within reach.
	 */
	private static void finalToll(Ferryman f, Fare fare, Beat b) {
		ServerLevel level = fare.level;
		String dim = ElderFx.dim(f);
		int length = ElderUltimate.length();
		double k = b.t / length;
		if (b.between(0, 40)) {
			fare.lift += 0.15;
			f.motion.heave(f, 0.15);
		}
		if (b.every(2, 0)) {
			ElderFx.shake(f, f.getX(), f.getZ(), 64, (float) (0.15 + 0.45 * k), 4);
			WorldFx.particleIn(level, "minecraft:soul", f.getX(), f.getY() - 0.5, f.getZ(), 3, 0.5, 7, 0.03, 3);
		}
		if (b.at(2) || b.at(length * 0.35) || b.at(length * 0.7)) {
			WorldFx.soundIn(dim, "minecraft:block.bell.use", "hostile", f.getX(), f.getY() + 8, f.getZ(), 6f, 0.4f);
			WorldFx.soundIn(dim, "minecraft:block.bell.resonate", "hostile", f.getX(), f.getY() + 8, f.getZ(), 6f, 0.5f);
			ElderStrikes.wave(f, f.getX(), f.getY() - fare.lift, f.getZ(), 0.9, 30, 4f, "minecraft:soul", ElderTerrain.Mark.SOUL);
			ElderFx.shake(f, f.getX(), f.getZ(), 80, 0.9f, 16);
		}
		if (b.between(20, length - 20) && b.every(5, 20)) {
			ThreadLocalRandom rnd = ThreadLocalRandom.current();
			List<ServerPlayer> targets = ElderFx.targets(f, 48);
			double x, z;
			if (!targets.isEmpty() && rnd.nextInt(3) == 0) {
				ServerPlayer q = targets.get(rnd.nextInt(targets.size()));
				x = q.getX() + rnd.nextDouble(-2, 2);
				z = q.getZ() + rnd.nextDouble(-2, 2);
			} else {
				double a = rnd.nextDouble() * Math.PI * 2, r = 6 + rnd.nextDouble(24);
				x = f.getX() + Math.cos(a) * r;
				z = f.getZ() + Math.sin(a) * r;
			}
			double y = ElderFx.surface(level, x, f.getY() - fare.lift + 4, z, 6, 16);
			ElderStrikes.strike(f, x, Double.isNaN(y) ? f.getY() - fare.lift : y, z, 2.6, 7f, 16, ElderStrikes.SOULFALL);
		}
		if (b.every(20, 0)) {
			ElderFight.markPlayers(f, ElderFx.targets(f, 64), "minecraft:soul_fire_flame");
		}
		if (b.at(length - 10)) {
			// The boat comes down
			f.motion.heave(f, -fare.lift);
			fare.lift = 0;
			WorldFx.soundIn(dim, "minecraft:entity.wither.break_block", "hostile", f.getX(), f.getY() + 2, f.getZ(), 6f, 0.4f);
			WorldFx.soundIn(dim, "minecraft:block.bell.use", "hostile", f.getX(), f.getY() + 8, f.getZ(), 6f, 0.3f);
			WorldFx.particleIn(level, "minecraft:explosion_emitter", f.getX(), f.getY() + 1, f.getZ(), 3, 1, 3, 0, 4);
			ElderTerrain.blast(f, f.getX(), f.getY() + 0.5, f.getZ(), 3.2f, false);
			ElderStrikes.wave(f, f.getX(), f.getY(), f.getZ(), 1.2, 38, 10f, "minecraft:soul_fire_flame", ElderTerrain.Mark.SOUL);
			ElderTerrain.mark(level, f.getX(), f.getY(), f.getZ(), 16, 0.6, ElderTerrain.Mark.SOUL);
			fare.breakUltimate(f, BOSS);
		}
		if (b.past(length)) {
			fare.closeUltimate(f);
		}
	}

	/** One of his blows landed: it takes its toll in levels. */
	public static void toll(ServerPlayer p) {
		int levels = Math.min(p.experienceLevel, Math.max(0, Config.FERRYMAN_TOLL_LEVELS));
		if (levels <= 0) {
			return;
		}
		p.giveExperienceLevels(-levels);
		WorldFx.particles(p, "minecraft:wax_off", 1.0, 0.5, 0.05, 10);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.ferryman_toll", levels), true);
	}

	private static void collect(ServerPlayer p) {
		int levels = Math.min(p.experienceLevel, Math.max(0, Config.FERRYMAN_ESCAPE_LEVELS));
		if (levels > 0) {
			p.giveExperienceLevels(-levels);
		}
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.ferryman_collects", levels));
		WorldFx.soundSelf(p, "minecraft:block.bell.use", 1f, 0.5f);
	}

	/** Slain: fragments, perhaps a relic, and the Fee he was owed waived for his passenger and his slayer. */
	public static void onDefeat(Ferryman f, Entity killer) {
		Fare fare = FARES.remove(f.getUUID());
		ElderStrikes.forget(f);
		ElderFx.silence(f, ElderFx.MUSIC_FERRYMAN);
		if (fare == null) {
			return;
		}
		fare.bar.removeAllPlayers();
		ServerLevel level = (ServerLevel) f.level();
		ElderFx.burst(f, "minecraft:soul_fire_flame", 200);
		ElderFx.burst(f, "minecraft:soul", 80);
		ElderFx.cinema(f, 160, ModPackets.BossFxPayload.ULTIMATE_END, 0, 0, "");
		f.spawnAtLocation(level, new ItemStack(ModItems.TETHER_FRAGMENT, 3));
		if (ThreadLocalRandom.current().nextDouble() < Config.FERRYMAN_RELIC_CHANCE) {
			f.spawnAtLocation(level, RelicService.random());
		}
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(f) >= 64 * 64) {
				continue;
			}
			PlayerData d = DataStore.get(p.getUUID());
			Advancements.grant(p, Advancements.FERRYMAN_SLAIN);
			Journal.unlock(p, d, JournalEntry.FERRYMAN);
			if ((p.getUUID().equals(fare.target) || p == killer) && d.owned(Penalty.FERRYMANS_FEE) > 0) {
				PenaltyService.lift(p, d, Penalty.FERRYMANS_FEE, "gui.extremedimensions.elder.ferryman_waived");
			}
		}
		DataStore.markDirty();
	}

	/** Gone: he poles back into the dark, whole. */
	public static void fade(Ferryman f) {
		Fare fare = FARES.remove(f.getUUID());
		if (fare != null) {
			fare.bar.removeAllPlayers();
			if (fare.action == ElderBoss.ULTIMATE) {
				ElderFx.cinema(f, 160, ModPackets.BossFxPayload.ULTIMATE_END, 0, 0, "");
			}
		}
		ElderStrikes.forget(f);
		ElderFx.silence(f, ElderFx.MUSIC_FERRYMAN);
		ElderFx.burst(f, "minecraft:soul", 100);
		f.discard();
	}
}
