package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.server.BlockFlight;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;
import com.mojang.math.Transformation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The Ferryman: on a long Nether expedition he may come to collect. His boat sails over lava and stone alike,
 * closing in and then circling his passenger, his hooded head turned to them. While he sails the Tether drains faster
 * (faster still once he is wounded below half) and each of his blows takes levels. He brings his pole down in a line of
 * fire, drags his passenger back with a chain, sends soul lanterns drifting after them, rings a knell, charges straight
 * through them splitting the ground, raises cages of chains round them that crush inwards, and sends tides of soul
 * fire rolling out over the ground. Since 3.43 he also drives his pole down into a whirlpool of soul fire that draws
 * everyone round and in before a geyser bursts up out of its middle (the Undertow), raises two spectral oars that sweep
 * the ground before him in three strokes (Oars of the Drowned), and raises bells out of the floor beside his passengers
 * that toll in turn, each toll a shockwave (Bells of the Drowned). At half his health he sounds the Final Toll: the boat
 * rises, soul fire rains for ten seconds and three bells ring, and when the boat comes down his curse falls on everyone
 * within reach. Slain, he leaves fragments and perhaps a relic and waives a stack of the Ferryman's Fee; outrun home, he
 * collects his fare.
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
		final List<ElderGaol.Cage> gaols = new ArrayList<>();
		/** The Undertow: where its whirlpool turns. */
		double poolX, poolY, poolZ;
		/** Oars of the Drowned: where they rise (they sweep round it), which way he faced then, and each stroke's aim. */
		double oarX, oarY, oarZ, oarFace;
		final double[] strokes = {Double.NaN, Double.NaN, Double.NaN};
		final List<Oar> oars = new ArrayList<>();
		/** Who a stroke of the oars has struck (once a stroke). */
		final Set<UUID> rowed = new HashSet<>();
		/** Bells of the Drowned: the bells risen out of the floor. */
		final List<Bell> bells = new ArrayList<>();
	}

	/** A spectral oar of his Oars of the Drowned: its shaft and its blade, and the side of him it rises at (1 or -1). */
	private static final class Oar {
		final int side;
		final Display.BlockDisplay shaft, blade;

		Oar(int side, Display.BlockDisplay shaft, Display.BlockDisplay blade) {
			this.side = side;
			this.shaft = shaft;
			this.blade = blade;
		}
	}

	/** A bell of his Bells of the Drowned: its frame and its body, where it stands, which way it is turned. */
	private static final class Bell {
		final Display.BlockDisplay frame, body;
		final double x, y, z;
		final float yaw;
		/** Ticks since it came up out of the floor, and until it hangs still again after a toll. */
		int age, settle;
		/** Which way it swung last (each toll swings it the other way). */
		int swing = 1;

		Bell(Display.BlockDisplay frame, Display.BlockDisplay body, double x, double y, double z, float yaw) {
			this.frame = frame;
			this.body = body;
			this.x = x;
			this.y = y;
			this.z = z;
			this.yaw = yaw;
		}
	}

	private static final Map<UUID, Fare> FARES = new HashMap<>();

	/** How many displays make up his spectral oars now (for tests). */
	public static int oars(Entity e) {
		Fare f = FARES.get(e.getUUID());
		return f == null ? 0 : f.oars.size() * 2;
	}

	/** How many of his bells stand risen out of the floor now (for tests). */
	public static int bells(Entity e) {
		Fare f = FARES.get(e.getUUID());
		return f == null ? 0 : f.bells.size();
	}

	/** His fight, if he is fighting. */
	static ElderFight fight(Entity e) {
		return FARES.get(e.getUUID());
	}

	public static boolean isDriven(Entity e) {
		return FARES.containsKey(e.getUUID());
	}

	/** Starts one of its blows (or its ultimate) at once; for tests and operators. False if it is not fighting. */
	public static boolean force(Entity e, int action) {
		Fare fight = FARES.get(e.getUUID());
		if (fight == null || !(e instanceof Ferryman f)) {
			return false;
		}
		ElderSpawn.skip(f, fight);
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
			dropAll(f);
		}
		FARES.clear();
	}

	public static boolean unlocked() {
		return switch (Config.FERRYMAN_UNLOCK) {
			case 0 -> true;
			case 1 -> DataStore.sealBroken();
			default -> DataStore.starvedSlain();
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
			if (d.inRun && !d.ferrymanTold && !p.isCreative() && !p.isSpectator() && Dims.NETHER.equals(Dims.of(p)) && !Rules.peaceful(p.level())
					&& Config.FERRYMAN_CHANCE > 0) {
				// The first crossing since he sails: whoever makes it is told what may come, and that a bell warns of it
				d.ferrymanTold = true;
				DataStore.markDirty();
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.ferryman_sails", (Math.max(0, Config.FERRYMAN_AFTER_SECONDS) + 59) / 60)
						.withStyle(ChatFormatting.GOLD));
			}
			if (!d.inRun || d.ferrymanRolled || d.runActive < Config.FERRYMAN_AFTER_SECONDS || p.isCreative() || p.isSpectator()
					|| !Dims.NETHER.equals(Dims.of(p)) || Rules.peaceful(p.level())) {
				continue;
			}
			// One chance per expedition; the Fee he is owed makes him likelier
			d.ferrymanRolled = true;
			double chance = Math.min(1, Math.max(0, Config.FERRYMAN_CHANCE) * (1 + d.stacks(Penalty.FERRYMANS_FEE)));
			if (ThreadLocalRandom.current().nextDouble() < chance) {
				stir(p);
			}
		}
	}

	/** The key of the bell that tolls for this player. */
	public static String stirKey(ServerPlayer p) {
		return "ferryman@" + p.getUUID();
	}

	/**
	 * His bell tolls before he comes (3.38): for {@link Config#FERRYMAN_WAKE_SECONDS} a bar counts down for the one he
	 * comes for, wherever they go, and the bell rings louder as he nears. Still in the Nether on their expedition when it
	 * stops, they are his; gone home or out of the Nether, he lets this crossing pass (he has had his chance on it).
	 * With no wind-up he comes at once. Returns whether the bell tolls (or he came).
	 */
	public static boolean stir(ServerPlayer p) {
		if (hunting(p) || ElderStir.stirring(stirKey(p)) || !Dims.ours(p)) {
			return false;
		}
		if (Config.FERRYMAN_WAKE_SECONDS <= 0) {
			return summon(p);
		}
		UUID passenger = p.getUUID();
		return ElderStir.begin(new ElderStir.Stir(stirKey(p), "ferryman", (ServerLevel) p.level(), p.getX(), p.getY(), p.getZ(), p,
				Config.FERRYMAN_WAKE_SECONDS, 0, BossEvent.BossBarColor.YELLOW,
				q -> q.getUUID().equals(passenger) && DataStore.get(passenger).inRun && Dims.NETHER.equals(Dims.of(q)), FerrymanService::summon)
				.follows().tint(ChatFormatting.GOLD).pulse(FerrymanService::tolls));
	}

	/** Each second of his coming: soul fire stirs about his passenger, and every other second the bell, louder as he nears. */
	private static void tolls(ServerLevel level, double x, double y, double z, float share) {
		String dim = level.dimension().identifier().toString();
		if ((level.getGameTime() / 20) % 2 == 0) {
			WorldFx.soundIn(dim, "minecraft:block.bell.use", "hostile", x, y + 6, z, 1.5f + 3f * share, 0.45f);
		}
		if (share > 0.7f) {
			WorldFx.soundIn(dim, "minecraft:block.bell.resonate", "hostile", x, y + 6, z, 2f, 0.5f);
		}
		WorldFx.particleIn(dim, "minecraft:soul_fire_flame", x, y + 0.5, z, 10, 1.5, 10, 0.01, 4 + (int) (share * 16));
	}

	/** He comes for this player (also the operator command); false if he is already after them or cannot sail in. */
	public static boolean summon(ServerPlayer p) {
		return !hunting(p) && summonAt(p, ElderFx.near((ServerLevel) p.level(), p, 16, 22)) != null;
	}

	/** He comes for this player, his boat at this spot; the Ferryman, or null if he is already after them. */
	public static Ferryman summonAt(ServerPlayer p, BlockPos spot) {
		// Never in another mod's world
		if (hunting(p) || !Dims.ours(p)) {
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
		ElderSpawn.begin(f, fare, spot);
		ElderFx.roar(f, 4f, 0.7f);
		WorldFx.soundIn(ElderFx.dim(f), "minecraft:block.bell.use", "hostile", f.getX(), f.getY() + 8, f.getZ(), 5f, 0.45f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.ferryman_comes"));
		Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.FERRYMAN);
		ElderFx.music(f, ElderFx.MUSIC_FERRYMAN);
		return f;
	}

	private static void drive(MinecraftServer s) {
		if (FARES.isEmpty()) {
			return;
		}
		List<Runnable> later = new ArrayList<>();
		// Over a copy: a blast of one may sink another, and his end takes him out of the fares mid-loop
		for (Map.Entry<UUID, Fare> e : List.copyOf(FARES.entrySet())) {
			Fare fare = e.getValue();
			if (FARES.get(e.getKey()) != fare) {
				continue;
			}
			Entity ent = fare.level.getEntity(e.getKey());
			if (!(ent instanceof Ferryman f) || !f.isAlive()) {
				fare.bar.removeAllPlayers();
				dropAll(fare);
				FARES.remove(e.getKey());
				ElderGaol.forget(e.getKey());
				continue;
			}
			fare.ticks++;
			ServerPlayer p = s.getPlayerList().getPlayer(fare.target);
			if (p == null || !p.isAlive() || p.level() != f.level() || f.distanceToSqr(p) > ElderPower.reach(80) * ElderPower.reach(80)) {
				// Gone home, outrun or slipped away with him still sailing: he collects his fare all the same (a death is
				// paid for enough). One who left the game pays when they come back.
				if (p == null) {
					DataStore.get(fare.target).fareOwed += Math.max(0, Config.FERRYMAN_ESCAPE_LEVELS);
					DataStore.markDirty();
				} else if (p.isAlive() && (p.level() == f.level() || !DataStore.get(p.getUUID()).inRun)) {
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
		hold(fare);
		if (fare.action == ElderBoss.SPAWN) {
			// Forming: his boat waits on the whirl, his hood turned to his passenger
			f.motion.brake(f, 0.02, 0.1);
			ElderFx.look(f, p);
			ElderSpawn.tick(f, fare);
			return;
		}
		if (fare.dazed(f)) {
			f.motion.brake(f, 0.04, 0.1);
			return;
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
				// Up on a shore or a tower over the boat, or down in a pit, his tides and his crossing never reach them;
				// his crossing goes no further than it can carry the boat
				double rise = p.getY() - f.getY();
				boolean cross = dist <= ElderPower.reach(30) - 6;
				// (3.43: his undertow and his oars about the boat, his bells wherever his passengers stand)
				int a = rise > 3.5 || rise < -3 ? fare.pick(Ferryman.LANTERN, 3, Ferryman.GAOL, 2, Ferryman.SLAM, 2, Ferryman.CHAIN, 2, Ferryman.BELLS, 2)
						: dist <= 8 ? fare.pick(Ferryman.KNELL, 3, Ferryman.TIDE, 2, Ferryman.GAOL, 1, Ferryman.UNDERTOW, 3, Ferryman.OARS, 3, Ferryman.BELLS, 2)
						: dist <= 20 ? fare.pick(Ferryman.SLAM, 3, Ferryman.CROSSING, 3, Ferryman.TIDE, 2, Ferryman.GAOL, 2, Ferryman.LANTERN, 2, Ferryman.CHAIN, 1,
								Ferryman.UNDERTOW, 3, Ferryman.OARS, 3, Ferryman.BELLS, 2)
						: fare.pick(Ferryman.LANTERN, 3, Ferryman.CHAIN, 2, Ferryman.GAOL, 1, Ferryman.CROSSING, cross ? 3 : 0, Ferryman.BELLS, 2);
				fare.start(f, a);
			}
			return;
		}
		fare.step();
		Beat b = fare.beat;
		if (fare.action != Ferryman.CROSSING && fare.action != ElderBoss.ULTIMATE) {
			// Mid-blow the boat drifts to a stop, turned to its passenger
			f.motion.turn(f, ElderFx.yawTo(f, p.getX(), p.getZ()), 3f);
			double near = Math.sqrt(f.distanceToSqr(p.getX(), f.getY(), p.getZ()));
			if (fare.action == Ferryman.SLAM && b.between(10, 24) && near > 8) {
				// The boat surges in under the falling pole, and stops dead as it strikes
				double ry = Math.toRadians(f.getYRot());
				f.motion.drive(f, -Math.sin(ry) * 0.32, Math.cos(ry) * 0.32, 0.06, 0.1);
			} else {
				// (his pole planted for the undertow, his oars rising where he stops: the boat holds still)
				boolean planted = fare.action == Ferryman.SLAM || fare.action == Ferryman.UNDERTOW || fare.action == Ferryman.OARS;
				f.motion.brake(f, planted ? 0.1 : 0.02, 0.1);
			}
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
				if (b.at(10) && !p.isCreative() && !p.isSpectator()) {
					// A chain of soul fire lashes out from the boat and hauls its passenger in (more passengers, more chains)
					for (ServerPlayer q : fare.split(f, p, ElderPower.reach(36))) {
						AttackFx.line((ServerLevel) f.level(), 1, AttackFx.LINE_CHAIN, f.getX(), f.getY() + 9, f.getZ(), q.getX(), q.getY() + 1, q.getZ(), 6);
						AttackFx.impact((ServerLevel) f.level(), 1, AttackFx.IMPACT_WISP, q.getX(), q.getY() + 1, q.getZ(), 1);
						WorldFx.soundIn(dim, "minecraft:block.chain.break", "hostile", q.getX(), q.getY(), q.getZ(), 3f, 0.5f);
						ElderFx.pull(f, q, 9);
						WorldFx.effect(q, MobEffects.SLOWNESS, 2, 1);
					}
				}
				if (b.past(24)) {
					end(f, fare, 50);
				}
			}
			case Ferryman.LANTERN -> {
				if (b.at(16)) {
					double yaw = Math.toRadians(f.getYRot());
					int n = 0;
					for (ServerPlayer q : ElderFx.reachable(f, ElderPower.reach(48))) {
						if (n >= Math.min(8, 3 + fare.fighters)) {
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
					// Everyone about the boat, as high as he stands in it
					for (ServerPlayer q : ElderFx.targets(f, 14, 4, f.getBbHeight() + 2)) {
						WorldFx.effect(q, MobEffects.WEAKNESS, 4, 1);
						ElderFx.shove(f, q, 1.2, 0.35);
					}
				}
				if (b.past(22)) {
					end(f, fare, 50);
				}
			}
			case Ferryman.CROSSING -> crossing(f, fare, p, b);
			case Ferryman.GAOL -> gaol(f, fare, p, b);
			case Ferryman.TIDE -> tide(f, fare, b);
			case Ferryman.UNDERTOW -> undertow(f, fare, b);
			case Ferryman.OARS -> row(f, fare, p, b);
			case Ferryman.BELLS -> peal(f, fare, b);
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
		double py = ElderFx.groundUnder(p);
		for (int i = 0; i < 7; i++) {
			double k = 3 + i * 3;
			double x = f.getX() + dx / d * k, z = f.getZ() + dz / d * k;
			// The fire runs over the ground: about its passenger's, else about the boat's; none where neither is near
			double y = ElderFx.surface(level, x, py + 4, z, 4, 12);
			if (Double.isNaN(y)) {
				y = ElderFx.surface(level, x, f.getY() + 4, z, 6, 16);
			}
			if (!Double.isNaN(y)) {
				ElderStrikes.strike(f, x, y, z, 2.6, 10f, fare.delay(6 + i * 3), ElderStrikes.SLAM);
			}
		}
		WorldFx.soundIn(ElderFx.dim(f), "minecraft:entity.iron_golem.attack", "hostile", f.getX(), f.getY() + 4, f.getZ(), 4f, 0.4f);
		ElderFx.shake(f, f.getX(), f.getZ(), 30, 0.6f, 10);
	}

	/** Crossing of the Dead: the boat backs off, then charges straight through its passenger, splitting the ground. */
	private static void crossing(Ferryman f, Fare fare, ServerPlayer prey, Beat b) {
		ServerLevel level = fare.level;
		String dim = ElderFx.dim(f);
		// With more passengers aboard it comes about and crosses again, at the next one each time
		ServerPlayer p = fare.aimed(prey);
		if (b.at(1)) {
			WorldFx.soundIn(dim, "minecraft:block.bell.use", "hostile", f.getX(), f.getY() + 8, f.getZ(), 5f, 0.7f);
		}
		if (b.between(0, 20)) {
			double dx = p.getX() - f.getX(), dz = p.getZ() - f.getZ();
			double d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
			f.motion.drive(f, -dx / d * 0.18, -dz / d * 0.18, 0.03, 0.1);
			f.motion.turn(f, ElderFx.yawTo(f, p.getX(), p.getZ()), 5f);
			ElderFx.look(f, p);
			// The way the boat will take, its sigils kindling along the ground it sails over, as far as it will go
			if (fare.ticks % 6 == 0) {
				double run = Math.min(d + 6, ElderPower.reach(30));
				AttackFx.line(level, 1, AttackFx.LINE_PATH, f.getX() + dx / d * 4, f.getY(), f.getZ() + dz / d * 4,
						f.getX() + dx / d * run, f.getY(), f.getZ() + dz / d * run, 7);
			}
		}
		if (b.at(20)) {
			double dx = p.getX() - f.getX(), dz = p.getZ() - f.getZ();
			double d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
			fare.dirX = dx / d;
			fare.dirZ = dz / d;
			// It crosses as far as its passenger stood, and on past them
			fare.chargeEnd = 20 + fare.chargeBeats(d + 6, ElderFight.speed(0.85), 26, ElderPower.reach(30));
			fare.struck.clear();
			WorldFx.soundIn(dim, "minecraft:item.trident.riptide_3", "hostile", f.getX(), f.getY() + 4, f.getZ(), 5f, 0.5f);
			ElderFx.shake(f, f.getX(), f.getZ(), 30, 0.6f, 10);
		}
		if (b.between(20, fare.chargeEnd)) {
			double sp = ElderFight.speed(0.85);
			f.motion.drive(f, fare.dirX * sp, fare.dirZ * sp, 0.3, 0.1);
			f.motion.turn(f, (float) (Math.atan2(fare.dirZ, fare.dirX) * 180 / Math.PI) - 90f, 8f);
			ElderFx.look(f, p);
			double fx = f.getX() + fare.dirX * 7, fz = f.getZ() + fare.dirZ * 7;
			ElderTerrain.furrow(level, fx, f.getY(), fz, 2.2, 1, 3, 7);
			if (fare.ticks % 2 == 0) {
				ElderTerrain.mark(level, f.getX() - fare.dirX * 6, f.getY(), f.getZ() - fare.dirZ * 6, 2, 0.5, ElderTerrain.Mark.SOUL);
			}
			if (fare.ticks % 2 == 0) {
				AttackFx.impact(level, 1, AttackFx.IMPACT_CHARGE, fx, f.getY(), fz, 2.2);
			}
			WorldFx.particleIn(level, "minecraft:soul", f.getX(), f.getY() + 3, f.getZ(), 3, 2, 3, 0.02, 2);
			// Along the hull (it is long) and within reach of it, from a little under the boat to the top of him (not
			// through the ground under it)
			for (ServerPlayer q : ElderFx.targets(f, 11, 2.5, f.getBbHeight())) {
				double qx = q.getX() - f.getX(), qz = q.getZ() - f.getZ();
				double along = qx * fare.dirX + qz * fare.dirZ, across = Math.abs(-qx * fare.dirZ + qz * fare.dirX);
				if (fare.struck.contains(q.getUUID()) || Math.abs(along) > 10 || across > 3.6) {
					continue;
				}
				fare.struck.add(q.getUUID());
				q.hurtServer(level, ElderPower.great(level, f), 14f);
				double side = (-qx * fare.dirZ + qz * fare.dirX) >= 0 ? 1 : -1;
				q.setDeltaMovement(-fare.dirZ * side * 1.3 + fare.dirX * 0.5, 0.7, fare.dirX * side * 1.3 + fare.dirZ * 0.5);
				com.extremedimensions.server.WorldFx.moved(q);
			}
		}
		if (b.at(fare.chargeEnd)) {
			ElderStrikes.wave(f, f.getX() + fare.dirX * 8, f.getY(), f.getZ() + fare.dirZ * 8, 0.6, 9, 5f, "minecraft:soul_fire_flame", ElderTerrain.Mark.SOUL);
		}
		if (b.past(fare.chargeEnd)) {
			f.motion.brake(f, 0.08, 0.1);
		}
		if (b.past(fare.chargeEnd + 14) && !fare.again(f, prey)) {
			end(f, fare, 55);
		}
	}

	/**
	 * Gaol of Chains: a closed cage of iron bars rises round each passenger it falls on, chains drawn over it for a roof,
	 * and after a moment it crushes inwards. No one walks, jumps or pearls out; whoever breaks a bar gets out through the
	 * gap, and whoever stays takes the blow.
	 */
	private static void gaol(Ferryman f, Fare fare, ServerPlayer p, Beat b) {
		ServerLevel level = fare.level;
		String dim = ElderFx.dim(f);
		int hold = (int) Math.round(Math.max(0.5, Config.FERRYMAN_GAOL_SECONDS) * 20);
		if (b.at(1)) {
			WorldFx.soundIn(dim, "minecraft:block.chain.break", "hostile", f.getX(), f.getY() + 8, f.getZ(), 5f, 0.4f);
		}
		if (b.at(12)) {
			fare.gaols.clear();
			for (ServerPlayer q : fare.split(f, p, ElderPower.reach(40))) {
				ElderGaol.Cage c = ElderGaol.raise(f, q);
				fare.gaols.add(c);
				// The ring fills as the time runs out
				AttackFx.ring(level, 1, c.x, c.y, c.z, ElderGaol.RADIUS, hold);
			}
		}
		if (!b.past(12)) {
			return;
		}
		boolean done = true;
		for (ElderGaol.Cage c : fare.gaols) {
			if (!c.crushed && c.age >= hold) {
				ElderGaol.crush(f, c);
			}
			if (!c.crushed && fare.ticks % 4 == 0) {
				// Its roof: chains of soul fire from the tops of the bars to a point overhead
				for (int i = 0; i < 8; i++) {
					double a = 2 * Math.PI * i / 8;
					AttackFx.line(level, 1, AttackFx.LINE_CHAIN, c.x + Math.cos(a) * ElderGaol.RADIUS, c.y + ElderGaol.HEIGHT, c.z + Math.sin(a) * ElderGaol.RADIUS,
							c.x, c.y + ElderGaol.HEIGHT + 1.5, c.z, 4);
				}
			}
			done &= c.crushed && c.age - c.crushedAt >= 10;
		}
		if (done || b.past(400)) {
			end(f, fare, 55);
		}
	}

	/** Tide of the Styx: the pole planted in the boat, two tides of soul fire roll out over the ground. */
	private static void tide(Ferryman f, Fare fare, Beat b) {
		String dim = ElderFx.dim(f);
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

	// ------------------------------------------------------------------ what his blows raise (3.43)

	/** Smoothstep of a share, held to 0..1. */
	private static double ease(double k) {
		k = Math.max(0, Math.min(1, k));
		return k * k * (3 - 2 * k);
	}

	/** An angle brought within half a turn either way. */
	private static double wrap(double a) {
		return Math.atan2(Math.sin(a), Math.cos(a));
	}

	/** Part of the way from one angle round to another, the short way. */
	private static double turn(double from, double to, double k) {
		return from + wrap(to - from) * k;
	}

	/** Ticks for {@code beats} of a blow at the pace it plays out (at least one). */
	private static int span(Fare fare, double beats) {
		return Math.max(1, (int) Math.round(beats / fare.tempo()));
	}

	/**
	 * The look of a block display: its block scaled by (sx, sy, sz) and turned by {@code q} about its own middle, which
	 * stands (ox, oy, oz) off the display.
	 */
	private static Transformation look(double ox, double oy, double oz, Quaternionf q, float sx, float sy, float sz) {
		Vector3f half = new Vector3f(0.5F * sx, 0.5F * sy, 0.5F * sz);
		new Quaternionf(q).transform(half);
		return new Transformation(new Vector3f((float) ox - half.x, (float) oy - half.y, (float) oz - half.z), new Quaternionf(q), new Vector3f(sx, sy, sz),
				new Quaternionf());
	}

	/** A display eases to a new look over {@code ticks}. */
	private static void move(Display.BlockDisplay d, Transformation look, int ticks) {
		DisplayAccess acc = (DisplayAccess) d;
		acc.extremedimensions$setTransformation(look);
		acc.extremedimensions$setInterpolationDuration(ticks);
		acc.extremedimensions$setInterpolationDelay(0);
	}

	/** His oars and his bells go once their blow is over or cut short (a daze, his ultimate, another blow). */
	private static void hold(Fare fare) {
		if (!fare.oars.isEmpty() && fare.action != Ferryman.OARS) {
			dropOars(fare);
		}
		if (!fare.bells.isEmpty() && fare.action != Ferryman.BELLS) {
			dropBells(fare);
		}
	}

	/** Everything his blows raised goes (his fight over). */
	private static void dropAll(Fare fare) {
		dropOars(fare);
		dropBells(fare);
	}

	private static void dropOars(Fare fare) {
		for (Oar o : fare.oars) {
			BlockFlight.drop(o.shaft);
			BlockFlight.drop(o.blade);
		}
		fare.oars.clear();
	}

	private static void dropBells(Fare fare) {
		for (Bell bell : fare.bells) {
			if (!bell.frame.isRemoved()) {
				WorldFx.particleIn(bell.frame.level(), "minecraft:soul", bell.x, bell.y + 0.5, bell.z, 1, 0.4, 1, 0.02, 6);
			}
			BlockFlight.drop(bell.frame);
			if (bell.body != null) {
				BlockFlight.drop(bell.body);
			}
		}
		fare.bells.clear();
	}

	// ------------------------------------------------------------------ the Undertow (3.43)

	/** How far round the boat his whirlpool draws, and how far out of its middle the geyser bursts (blocks). */
	private static final double POOL = 18, GEYSER = 5;

	/**
	 * The Undertow: he lifts his pole (0-16) and drives it down, and a whirlpool of soul fire opens round the boat. For
	 * three seconds it draws everyone near round and in (by the squall's rules: a wall in the way holds them, sneaking
	 * halves it, Anchored eases it) and slows them; its middle kindles (60), and as he rips the pole up (76) a geyser of
	 * soul fire bursts out of it, throwing whoever stands there high, and a wave runs out from it over the ground.
	 */
	private static void undertow(Ferryman f, Fare fare, Beat b) {
		ServerLevel level = fare.level;
		String dim = ElderFx.dim(f);
		int elder = AttackFx.of(f);
		if (b.between(0, 16) && b.every(3, 0)) {
			// the pole raised: soul fire stirs on the floor round the boat
			WorldFx.particleIn(level, "minecraft:soul", f.getX(), f.getY() + 0.3, f.getZ(), 6, 0.1, 6, 0.02, 6);
		}
		if (b.at(16)) {
			fare.poolX = f.getX();
			fare.poolY = f.getY();
			fare.poolZ = f.getZ();
			// its edge and its inner turn, laid on the floor while it draws
			int lasts = fare.delay(60);
			AttackFx.ring(level, elder, fare.poolX, fare.poolY + 0.1, fare.poolZ, POOL, lasts);
			AttackFx.ring(level, elder, fare.poolX, fare.poolY + 0.1, fare.poolZ, 6, lasts);
			AttackFx.impact(level, elder, AttackFx.IMPACT_BLAST, fare.poolX, fare.poolY + 0.2, fare.poolZ, 3);
			WorldFx.soundIn(dim, "minecraft:entity.generic.splash", "hostile", fare.poolX, fare.poolY + 2, fare.poolZ, 5f, 0.4f);
			WorldFx.soundIn(dim, "minecraft:block.bell.resonate", "hostile", fare.poolX, fare.poolY + 6, fare.poolZ, 4f, 0.45f);
			ElderFx.shake(f, fare.poolX, fare.poolZ, 32, 0.6f, 12);
			// the floor round it takes the soul fire in places
			ThreadLocalRandom rnd = ThreadLocalRandom.current();
			for (int i = 0; i < 7; i++) {
				double a = rnd.nextDouble() * Math.PI * 2, r = 4 + rnd.nextDouble(POOL - 5);
				ElderTerrain.mark(level, fare.poolX + Math.cos(a) * r, fare.poolY, fare.poolZ + Math.sin(a) * r, 1.5 + rnd.nextDouble(1.5), 0.6,
						ElderTerrain.Mark.SOUL);
			}
		}
		if (b.between(16, 76)) {
			if (b.every(2, 16)) {
				// soul fire turning in a spiral over the floor, the way the whirl turns
				for (int arm = 0; arm < 3; arm++) {
					for (int i = 0; i < 6; i++) {
						double r = 2 + i * 3;
						double a = arm * Math.PI * 2 / 3 + fare.circle * (b.t * 0.12 - r * 0.22);
						WorldFx.particleIn(level, "minecraft:soul_fire_flame", fare.poolX + Math.cos(a) * r, fare.poolY + 0.15, fare.poolZ + Math.sin(a) * r, 0.15,
								0.02, 0.15, 0.005, 1);
					}
				}
			}
			if (b.every(20, 16)) {
				WorldFx.soundIn(dim, "minecraft:block.bubble_column.whirlpool_inside", "hostile", fare.poolX, fare.poolY + 1, fare.poolZ, 4f, 0.5f);
				ElderFx.shake(f, fare.poolX, fare.poolZ, 28, 0.25f, 10);
			}
			// Drawn round and in: one push of both, the way the whirl turns and towards its middle
			for (ServerPlayer q : ElderFx.targets(f, POOL + 4, 4, 8)) {
				double dx = fare.poolX - q.getX(), dz = fare.poolZ - q.getZ();
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > POOL || d < 2.5 || q.getY() < fare.poolY - 4 || q.getY() > fare.poolY + 8) {
					continue;
				}
				if (b.every(10, 16)) {
					WorldFx.effect(q, MobEffects.SLOWNESS, 1, 0);
				}
				if (!PaleSquall.blown(q)) {
					continue;
				}
				double ix = dx / d, iz = dz / d;
				double ux = iz * fare.circle * 0.85 + ix * 0.55, uz = -ix * fare.circle * 0.85 + iz * 0.55;
				double n = Math.sqrt(ux * ux + uz * uz);
				ux /= n;
				uz /= n;
				double pull = PaleSquall.push(q, ux, uz, Config.FERRYMAN_UNDERTOW_PULL * (0.6 + 0.4 * (1 - d / POOL)));
				if (pull > 0) {
					PaleSquall.gust(q, ux * pull, uz * pull);
				}
			}
		}
		if (b.at(60)) {
			// its middle kindles: whoever is in it has a moment to get out
			AttackFx.ring(level, elder, fare.poolX, fare.poolY + 0.1, fare.poolZ, GEYSER, fare.delay(16));
			WorldFx.soundIn(dim, "minecraft:block.bell.use", "hostile", fare.poolX, fare.poolY + 6, fare.poolZ, 4f, 0.6f);
		}
		if (b.between(60, 76) && b.every(2, 60)) {
			WorldFx.particleIn(level, "minecraft:soul", fare.poolX, fare.poolY + 0.8, fare.poolZ, GEYSER * 0.4, 0.4, GEYSER * 0.4, 0.04, 8);
		}
		if (b.at(76)) {
			geyser(f, fare);
		}
		if (b.past(96)) {
			end(f, fare, 55);
		}
	}

	/** The geyser: soul fire bursts up out of the whirl's middle, throws whoever is in it high, and a wave runs out from it. */
	private static void geyser(Ferryman f, Fare fare) {
		ServerLevel level = fare.level;
		String dim = ElderFx.dim(f);
		int elder = AttackFx.of(f);
		double x = fare.poolX, y = fare.poolY, z = fare.poolZ;
		WorldFx.soundIn(dim, "extremedimensions:elder.ferryman.geyser", "hostile", x, y + 2, z, 5f, 1f);
		WorldFx.soundIn(dim, "minecraft:entity.generic.explode", "hostile", x, y + 2, z, 3f, 0.6f);
		WorldFx.particleIn(level, "minecraft:soul_fire_flame", x, y + 4, z, 1.2, 4, 1.2, 0.12, 160);
		WorldFx.particleIn(level, "minecraft:soul", x, y + 2, z, 2.5, 1.5, 2.5, 0.08, 40);
		// a column of it, as high as he stands in the boat
		for (int i = 0; i < 9; i++) {
			WorldFx.particleIn(level, "minecraft:explosion", x, y + 0.5 + i * 1.6, z, 0.5, 0.4, 0.5, 0, 1);
		}
		AttackFx.line(level, elder, AttackFx.LINE_BEAM, x, y + 0.2, z, x, y + 16, z, 10);
		AttackFx.impact(level, elder, AttackFx.IMPACT_BLAST, x, y + 0.2, z, GEYSER);
		ElderFx.shake(f, x, z, 40, 1.0f, 16);
		for (ServerPlayer q : ElderFx.targets(f, POOL, 4, f.getBbHeight() + 4)) {
			double dx = q.getX() - x, dz = q.getZ() - z;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > GEYSER || q.getY() < y - 3 || q.getY() > y + f.getBbHeight() + 2) {
				continue;
			}
			q.hurtServer(level, ElderPower.great(level, f), (float) Config.FERRYMAN_GEYSER_DAMAGE);
			// thrown high, and a little out of the middle
			double out = d < 0.3 ? 0 : 0.25 / d;
			q.setDeltaMovement(dx * out, 0.9, dz * out);
			WorldFx.moved(q);
		}
		ElderStrikes.wave(f, x, y, z, 0.9, 16, (float) (Config.FERRYMAN_GEYSER_DAMAGE / 2), "minecraft:soul_fire_flame", ElderTerrain.Mark.SOUL);
		ElderTerrain.mark(level, x, y, z, GEYSER, 0.7, ElderTerrain.Mark.SOUL);
	}

	// ------------------------------------------------------------------ Oars of the Drowned (3.43)

	/** An oar: how far out from where they rise its root stands, how long its shaft is, how far along it its blade is. */
	private static final double ROOT = 2.5, SHAFT = 12, BLADE = 11;
	/** How far round a stroke sweeps each side of its aim (each oar sweeps one side), and how near and far it reaches. */
	private static final double SWEEP = Math.toRadians(70), NEAR = 4, FAR = 14.6;
	/** Where his three strokes peak (each sweeps for 8 beats about it). */
	private static final double[] PEAKS = {36, 52, 68};

	/** The aim of stroke {@code k} (radians round where the oars rose), else the last one known, else where he faced. */
	private static double aim(Fare fare, int k) {
		for (int i = Math.min(k, fare.strokes.length - 1); i >= 0; i--) {
			if (!Double.isNaN(fare.strokes[i])) {
				return fare.strokes[i];
			}
		}
		return fare.oarFace;
	}

	/** How far through stroke {@code k} the oars are at this beat (0 to 1). */
	private static double stroke(int k, double t) {
		return Math.max(0, Math.min(1, (t - (PEAKS[k] - 4)) / 8));
	}

	/**
	 * Where an oar is at this beat: {its angle round where they rose, how far it is raised from lying flat (radians), how
	 * far it is sunk into the ground (blocks)}.
	 */
	private static double[] oarPose(Fare fare, int side, double t) {
		double out = side * Math.PI / 2, sweep = side * SWEEP;
		if (t < 28) {
			// rising upright out of the ground at his side
			return new double[] {fare.oarFace + out, Math.PI / 2, (SHAFT + 1) * (1 - ease((t - 12) / 16))};
		}
		if (t < PEAKS[0] - 4) {
			// laid down flat, out at the flank of the first stroke
			double e = ease((t - 28) / (PEAKS[0] - 4 - 28));
			return new double[] {turn(fare.oarFace + out, aim(fare, 0) + sweep, e), Math.PI / 2 * (1 - e), 0};
		}
		for (int k = 0; k < PEAKS.length; k++) {
			if (t < PEAKS[k] + 4) {
				// the stroke: flat along the ground, from the flank round to straight ahead
				return new double[] {aim(fare, k) + sweep * (1 - stroke(k, t)), 0, 0};
			}
			if (k + 1 < PEAKS.length && t < PEAKS[k + 1] - 4) {
				// lifted off the ground and swung back out for the next
				double e = ease((t - PEAKS[k] - 4) / (PEAKS[k + 1] - PEAKS[k] - 8));
				return new double[] {turn(aim(fare, k), aim(fare, k + 1) + sweep, e), Math.toRadians(30) * Math.sin(Math.PI * e), 0};
			}
		}
		double c = aim(fare, PEAKS.length - 1), last = PEAKS[PEAKS.length - 1] + 4;
		if (t < 84) {
			// upright again at his side
			double e = ease((t - last) / (84 - last));
			return new double[] {turn(c, c + out, e), Math.PI / 2 * e, 0};
		}
		// and down into the ground
		return new double[] {c + out, Math.PI / 2, (SHAFT + 1) * ease((t - 84) / 16)};
	}

	/** A point {@code along} blocks up an oar's shaft from its root, off where the oars rose. */
	private static double[] oarPoint(double[] pose, double along) {
		double a = pose[0], up = pose[1];
		// lying flat its blade skims the floor; upright its root stands in it
		double r = ROOT + Math.cos(up) * along;
		return new double[] {Math.cos(a) * r, 1.5 * Math.cos(up) - pose[2] + Math.sin(up) * along, Math.sin(a) * r};
	}

	/**
	 * The looks of an oar's shaft and blade at this beat: the shaft (the block's z) along it, the blade (the block's x)
	 * standing upright as it lies flat, broad to the way it sweeps.
	 */
	private static Transformation[] oarLooks(Fare fare, int side, double t) {
		double[] pose = oarPose(fare, side, t);
		Quaternionf q = new Quaternionf().rotateY((float) (Math.PI / 2 - pose[0])).rotateX((float) -pose[1]).rotateZ((float) (Math.PI / 2));
		double[] mid = oarPoint(pose, SHAFT / 2), blade = oarPoint(pose, BLADE);
		return new Transformation[] {look(mid[0], mid[1], mid[2], q, 0.6F, 0.6F, (float) SHAFT), look(blade[0], blade[1], blade[2], q, 3F, 0.4F, 2F)};
	}

	/**
	 * Oars of the Drowned: he spreads his arms and two spectral oars rise out of the ground at his sides (12-28). Then
	 * three strokes, peaking at 36, 52 and 68: a path of sigils on the ground shows each sweep first (12 beats before its
	 * peak), then the oars sweep it flat along the ground, each one half of it from the flank round to straight ahead,
	 * towards whoever he rows for, tearing up the ground; whoever an oar passes is struck (once a stroke) and flung along
	 * it. The oars sink back into the ground (84-100).
	 */
	private static void row(Ferryman f, Fare fare, ServerPlayer prey, Beat b) {
		ServerLevel level = fare.level;
		String dim = ElderFx.dim(f);
		int elder = AttackFx.of(f);
		if (b.at(12)) {
			dropOars(fare);
			fare.oarX = f.getX();
			fare.oarY = f.getY();
			fare.oarZ = f.getZ();
			fare.oarFace = Math.atan2(prey.getZ() - fare.oarZ, prey.getX() - fare.oarX);
			for (int k = 0; k < fare.strokes.length; k++) {
				fare.strokes[k] = Double.NaN;
			}
			BlockState wood = ArenaDesign.mc("stripped_dark_oak_log", Blocks.DARK_OAK_FENCE.defaultBlockState());
			wood = wood.hasProperty(BlockStateProperties.AXIS) ? wood.setValue(BlockStateProperties.AXIS, Direction.Axis.Z) : wood;
			BlockState planks = ArenaDesign.mc("dark_oak_planks", Blocks.DARK_OAK_FENCE.defaultBlockState());
			Brightness lit = new Brightness(15, 15);
			for (int side = -1; side <= 1; side += 2) {
				Transformation[] at = oarLooks(fare, side, 12);
				Display.BlockDisplay shaft = BlockFlight.spawn(level, fare.oarX, fare.oarY, fare.oarZ, wood, at[0]);
				Display.BlockDisplay blade = BlockFlight.spawn(level, fare.oarX, fare.oarY, fare.oarZ, planks, at[1]);
				if (shaft == null || blade == null) {
					if (shaft != null) {
						BlockFlight.drop(shaft);
					}
					if (blade != null) {
						BlockFlight.drop(blade);
					}
					continue;
				}
				// spectral: lit as if by their own soul fire
				((DisplayAccess) shaft).extremedimensions$setBrightnessOverride(lit);
				((DisplayAccess) blade).extremedimensions$setBrightnessOverride(lit);
				fare.oars.add(new Oar(side, shaft, blade));
				double[] foot = oarPoint(new double[] {fare.oarFace + side * Math.PI / 2, Math.PI / 2, 0}, 0);
				WorldFx.particleIn(level, "minecraft:soul", fare.oarX + foot[0], fare.oarY + 0.3, fare.oarZ + foot[2], 0.6, 0.3, 0.6, 0.03, 14);
			}
			WorldFx.soundIn(dim, "minecraft:entity.generic.splash", "hostile", fare.oarX, fare.oarY + 1, fare.oarZ, 4f, 0.45f);
		}
		if (b.between(12, 100) && fare.oars.isEmpty()) {
			// none could rise
			end(f, fare, 40);
			return;
		}
		for (int k = 0; k < PEAKS.length; k++) {
			if (b.at(PEAKS[k] - 12)) {
				// at whoever he rows for this stroke (with more passengers, each stroke at the next)
				List<ServerPlayer> marks = fare.split(f, prey, ElderPower.reach(30));
				ServerPlayer q = marks.get(k % marks.size());
				double dx = q.getX() - fare.oarX, dz = q.getZ() - fare.oarZ;
				fare.strokes[k] = dx * dx + dz * dz < 1 ? Math.toRadians(f.getYRot() + 90) : Math.atan2(dz, dx);
				path(fare, elder, fare.strokes[k]);
			}
			if (b.at(PEAKS[k] - 4)) {
				fare.rowed.clear();
				WorldFx.soundIn(dim, "extremedimensions:elder.ferryman.oars", "hostile", fare.oarX, fare.oarY + 3, fare.oarZ, 4.5f, 0.9f + 0.1f * k);
				WorldFx.soundIn(dim, "minecraft:entity.generic.splash", "hostile", fare.oarX, fare.oarY + 1, fare.oarZ, 4f, 0.5f);
			}
			if (b.at(PEAKS[k])) {
				ElderFx.shake(f, fare.oarX, fare.oarZ, 30, 0.5f, 8);
			}
		}
		if (b.between(12, 100) && b.every(2, 12)) {
			// (aimed where it will be when the move ends)
			int step = span(fare, 2);
			for (Oar o : fare.oars) {
				Transformation[] at = oarLooks(fare, o.side, b.t + 2);
				move(o.shaft, at[0], step);
				move(o.blade, at[1], step);
				// a faint soul fire about the blade
				double[] w = oarPoint(oarPose(fare, o.side, b.t), BLADE);
				WorldFx.particleIn(level, "minecraft:soul_fire_flame", fare.oarX + w[0], fare.oarY + w[1], fare.oarZ + w[2], 0.8, 0.8, 0.8, 0.01, 2);
			}
		}
		// The strokes: each oar sweeps the stretch it covered this tick
		for (int k = 0; k < PEAKS.length; k++) {
			double e0 = stroke(k, b.prev), e1 = stroke(k, b.t);
			if (e1 <= e0) {
				continue;
			}
			double c = aim(fare, k);
			for (Oar o : fare.oars) {
				sweep(f, fare, c + o.side * SWEEP * (1 - e0), c + o.side * SWEEP * (1 - e1));
				// the ground torn up under its blade, here and there along the sweep (and where the two meet)
				for (double mark : new double[] {0.25, 0.75, 1.0}) {
					if (e0 < mark && e1 >= mark && (mark < 1 || o.side == 1)) {
						tear(f, fare, c + o.side * SWEEP * (1 - mark));
					}
				}
			}
		}
		if (b.at(84)) {
			for (Oar o : fare.oars) {
				double[] foot = oarPoint(oarPose(fare, o.side, 84), 0);
				WorldFx.particleIn(level, "minecraft:soul", fare.oarX + foot[0], fare.oarY + 0.3, fare.oarZ + foot[2], 0.6, 0.3, 0.6, 0.03, 14);
			}
			WorldFx.soundIn(dim, "minecraft:entity.generic.splash", "hostile", fare.oarX, fare.oarY + 1, fare.oarZ, 3f, 0.4f);
		}
		if (b.past(100) && !fare.oars.isEmpty()) {
			dropOars(fare);
		}
		if (b.past(104)) {
			end(f, fare, 55);
		}
	}

	/** The way a stroke will sweep, laid on the ground in sigils: the arc its blades will run, and its two edges. */
	private static void path(Fare fare, int elder, double c) {
		ServerLevel level = fare.level;
		int lasts = fare.delay(16);
		double y = fare.oarY + 0.1, r = ROOT + BLADE;
		int n = 7;
		for (int side = -1; side <= 1; side += 2) {
			for (int i = 0; i < n; i++) {
				double a0 = c + side * SWEEP * i / n, a1 = c + side * SWEEP * (i + 1) / n;
				AttackFx.line(level, elder, AttackFx.LINE_PATH, fare.oarX + Math.cos(a0) * r, y, fare.oarZ + Math.sin(a0) * r, fare.oarX + Math.cos(a1) * r, y,
						fare.oarZ + Math.sin(a1) * r, lasts);
			}
			double edge = c + side * SWEEP;
			AttackFx.line(level, elder, AttackFx.LINE_PATH, fare.oarX + Math.cos(edge) * NEAR, y, fare.oarZ + Math.sin(edge) * NEAR,
					fare.oarX + Math.cos(edge) * (FAR - 0.6), y, fare.oarZ + Math.sin(edge) * (FAR - 0.6), lasts);
		}
	}

	/** An oar sweeps round from angle {@code from} to {@code to}: whoever it passes is struck (once a stroke) and flung along it. */
	private static void sweep(Ferryman f, Fare fare, double from, double to) {
		ServerLevel level = fare.level;
		int elder = AttackFx.of(f);
		double span = wrap(to - from);
		for (ServerPlayer q : ElderFx.targets(f, FAR + 6, 4, 6)) {
			if (fare.rowed.contains(q.getUUID())) {
				continue;
			}
			double dx = q.getX() - fare.oarX, dz = q.getZ() - fare.oarZ, dy = q.getY() - fare.oarY;
			double r = Math.sqrt(dx * dx + dz * dz);
			if (r < NEAR || r > FAR || dy < -2 || dy > 3.2) {
				continue;
			}
			double a = Math.atan2(dz, dx);
			double rel = wrap(a - from);
			// (as wide as they stand: the nearer the shaft, the more of its turn they fill)
			double m = 0.1 + 0.6 / r;
			if (span >= 0 ? rel < -m || rel > span + m : rel > m || rel < span - m) {
				continue;
			}
			fare.rowed.add(q.getUUID());
			q.hurtServer(level, ElderPower.great(level, f), (float) Config.FERRYMAN_OAR_DAMAGE);
			// along the sweep, the way it turns, and a little out
			double way = span >= 0 ? 1 : -1;
			q.setDeltaMovement(-Math.sin(a) * way * 1.3 + Math.cos(a) * 0.35, 0.55, Math.cos(a) * way * 1.3 + Math.sin(a) * 0.35);
			WorldFx.moved(q);
			WorldFx.soundIn(ElderFx.dim(f), "minecraft:entity.player.attack.knockback", "hostile", q.getX(), q.getY() + 1, q.getZ(), 2.5f, 0.6f);
			AttackFx.impact(level, elder, AttackFx.IMPACT_BLAST, q.getX(), q.getY() + 1, q.getZ(), 1.6);
		}
	}

	/** The ground torn up under a blade as it passes, at angle {@code a} round where the oars rose. */
	private static void tear(Ferryman f, Fare fare, double a) {
		ServerLevel level = fare.level;
		double r = ROOT + BLADE;
		double x = fare.oarX + Math.cos(a) * r, z = fare.oarZ + Math.sin(a) * r;
		double y = ElderFx.surface(level, x, fare.oarY + 4, z, 6, 10);
		if (Double.isNaN(y)) {
			return;
		}
		ElderTerrain.furrow(level, x, y, z, 1.8, 1, 2, 4);
		AttackFx.impact(level, AttackFx.of(f), AttackFx.IMPACT_CHARGE, x, y, z, 2);
		WorldFx.particleIn(level, "minecraft:soul", x, y + 0.5, z, 1, 0.4, 1, 0.03, 8);
	}

	// ------------------------------------------------------------------ Bells of the Drowned (3.43)

	/** The most bells he raises at once, and how big each is (blocks). */
	private static final int MOST_BELLS = 10;
	private static final double BELL = 2.5;
	/** His tolls (each a swing of his lantern): the bells ring in turn. */
	private static final double[] TOLLS = {30, 38, 46, 54, 62, 70};

	/**
	 * The looks of a bell's frame and body: {@code rise} blocks over (or under) where it stands, swung by {@code tilt}
	 * (its body swings further in the frame), grown by {@code grow}. The bell block draws only its frame; its body is a
	 * block of gold hung in it.
	 */
	private static Transformation[] bellLooks(float yaw, double rise, double tilt, double grow) {
		float s = (float) (BELL * grow);
		Quaternionf q = new Quaternionf().rotateY(yaw).rotateX((float) tilt);
		Quaternionf hung = new Quaternionf().rotateY(yaw).rotateX((float) (tilt * 1.8));
		return new Transformation[] {look(0, rise + s / 2, 0, q, s, s, s), look(0, rise + s * 0.5, 0, hung, s * 0.42F, s * 0.5F, s * 0.42F)};
	}

	private static void moveBell(Bell bell, double rise, double tilt, double grow, int ticks) {
		Transformation[] at = bellLooks(bell.yaw, rise, tilt, grow);
		move(bell.frame, at[0], ticks);
		if (bell.body != null) {
			move(bell.body, at[1], ticks);
		}
	}

	/**
	 * Bells of the Drowned: he raises his lantern (0-20) and bells rise out of the floor, one beside each passenger he
	 * can reach and three round him. Six times he swings the lantern (30 to 70) and a bell tolls, each in turn (the next
	 * kindles a moment before): a shockwave of soul fire runs out over the ground from it (jump it). He lowers the lantern
	 * (80) and the bells sink back into the floor.
	 */
	private static void peal(Ferryman f, Fare fare, Beat b) {
		ServerLevel level = fare.level;
		String dim = ElderFx.dim(f);
		int elder = AttackFx.of(f);
		if (b.between(0, 20) && b.every(4, 0)) {
			WorldFx.particleIn(level, "minecraft:soul_fire_flame", f.getX(), f.getY() + f.getBbHeight() * 0.8, f.getZ(), 1.5, 1, 1.5, 0.02, 4);
		}
		if (b.at(20)) {
			raiseBells(f, fare);
			WorldFx.soundIn(dim, "minecraft:block.bell.resonate", "hostile", f.getX(), f.getY() + 8, f.getZ(), 4f, 0.45f);
			ElderFx.shake(f, f.getX(), f.getZ(), 40, 0.5f, 12);
		}
		for (Bell bell : fare.bells) {
			bell.age++;
			if (bell.age == 1) {
				// up out of the floor (a tick after it was sent under it, so it is seen to rise)
				moveBell(bell, 0, 0, 1, span(fare, 10));
			}
			if (bell.settle > 0 && --bell.settle == 0) {
				moveBell(bell, 0, 0, 1, span(fare, 6));
			}
		}
		if (!fare.bells.isEmpty()) {
			for (int k = 0; k < TOLLS.length; k++) {
				Bell bell = fare.bells.get(k % fare.bells.size());
				if (b.at(TOLLS[k] - 6)) {
					// the next to toll kindles
					AttackFx.ring(level, elder, bell.x, bell.y + 0.1, bell.z, 2.5, fare.delay(6));
					WorldFx.particleIn(level, "minecraft:soul_fire_flame", bell.x, bell.y + BELL * 0.5, bell.z, 0.8, 0.8, 0.8, 0.03, 10);
				}
				if (b.at(TOLLS[k])) {
					toll(f, fare, bell);
				}
			}
		}
		if (b.at(80)) {
			for (Bell bell : fare.bells) {
				bell.settle = 0;
				moveBell(bell, -(BELL + 0.6), 0, 1, fare.delay(16));
				WorldFx.particleIn(level, "minecraft:soul", bell.x, bell.y + 0.3, bell.z, 1, 0.2, 1, 0.02, 8);
			}
		}
		if (b.past(96) && !fare.bells.isEmpty()) {
			dropBells(fare);
		}
		if (b.past(100)) {
			end(f, fare, 55);
		}
	}

	/** Bells rise out of the floor: one beside each passenger he can reach (the nearest first), three round him; ten at most. */
	private static void raiseBells(Ferryman f, Fare fare) {
		dropBells(fare);
		ServerLevel level = fare.level;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		BlockState frame = ArenaDesign.mc("bell", Blocks.SOUL_LANTERN.defaultBlockState()), gold = Blocks.GOLD_BLOCK.defaultBlockState();
		List<ServerPlayer> near = ElderFx.reachable(f, ElderPower.reach(40));
		near.sort(java.util.Comparator.comparingDouble(q -> q.distanceToSqr(f)));
		for (ServerPlayer q : near) {
			if (fare.bells.size() >= MOST_BELLS - 3) {
				break;
			}
			double base = ElderFx.groundUnder(q);
			for (int tries = 0; tries < 6; tries++) {
				double a = rnd.nextDouble() * Math.PI * 2, r = 2 + rnd.nextDouble(2);
				double x = q.getX() + Math.cos(a) * r, z = q.getZ() + Math.sin(a) * r;
				double y = ElderFx.surface(level, x, base + 2, z, 2, 6);
				if (!Double.isNaN(y) && bell(f, fare, frame, gold, x, y, z)) {
					break;
				}
			}
		}
		double turn = rnd.nextDouble() * Math.PI * 2;
		for (int i = 0; i < 3; i++) {
			double a = turn + i * Math.PI * 2 / 3;
			double x = f.getX() + Math.cos(a) * 8, z = f.getZ() + Math.sin(a) * 8;
			double y = ElderFx.surface(level, x, f.getY() + 4, z, 6, 12);
			if (!Double.isNaN(y)) {
				bell(f, fare, frame, gold, x, y, z);
			}
		}
	}

	/** One bell, under the floor here and about to rise out of it, turned to him; false if it could not be made. */
	private static boolean bell(Ferryman f, Fare fare, BlockState frame, BlockState gold, double x, double y, double z) {
		ServerLevel level = fare.level;
		float yaw = (float) Math.atan2(f.getX() - x, f.getZ() - z);
		Transformation[] under = bellLooks(yaw, -(BELL + 0.6), 0, 1);
		Display.BlockDisplay d = BlockFlight.spawn(level, x, y, z, frame, under[0]);
		if (d == null) {
			return false;
		}
		Display.BlockDisplay body = BlockFlight.spawn(level, x, y, z, gold, under[1]);
		Brightness lit = new Brightness(15, 15);
		((DisplayAccess) d).extremedimensions$setBrightnessOverride(lit);
		if (body != null) {
			((DisplayAccess) body).extremedimensions$setBrightnessOverride(lit);
		}
		fare.bells.add(new Bell(d, body, x, y, z, yaw));
		ElderTerrain.mark(level, x, y, z, 1.6, 0.6, ElderTerrain.Mark.SOUL);
		WorldFx.particleIn(level, "minecraft:soul", x, y + 0.3, z, 1, 0.2, 1, 0.03, 10);
		WorldFx.soundIn(ElderFx.dim(f), "extremedimensions:elder.ferryman.bell", "hostile", x, y + 1, z, 2.5f, 0.9f);
		return true;
	}

	/** A bell tolls: it jolts in its frame, and a shockwave of soul fire runs out over the ground from it. */
	private static void toll(Ferryman f, Fare fare, Bell bell) {
		ServerLevel level = fare.level;
		String dim = ElderFx.dim(f);
		bell.swing = -bell.swing;
		moveBell(bell, 0.15, 0.35 * bell.swing, 1.12, 2);
		bell.settle = 4;
		ElderStrikes.wave(f, bell.x, bell.y, bell.z, 0.8, 10, (float) Config.FERRYMAN_BELL_DAMAGE, "minecraft:soul_fire_flame", ElderTerrain.Mark.SOUL);
		WorldFx.soundIn(dim, "minecraft:block.bell.use", "hostile", bell.x, bell.y + 1.5, bell.z, 4f, 0.5f);
		WorldFx.soundIn(dim, "extremedimensions:elder.ferryman.bell", "hostile", bell.x, bell.y + 1.5, bell.z, 4f, 1f);
		AttackFx.impact(level, AttackFx.of(f), AttackFx.IMPACT_BLAST, bell.x, bell.y + 0.2, bell.z, 2);
		WorldFx.particleIn(level, "minecraft:soul", bell.x, bell.y + BELL * 0.6, bell.z, 1.2, 0.8, 1.2, 0.05, 14);
		ElderFx.shake(f, bell.x, bell.z, 16, 0.4f, 8);
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
		if (b.at(1)) {
			// The toll is laid on the ground under him while he hangs over it
			AttackFx.ring(level, 1, f.getX(), f.getY(), f.getZ(), 12, (int) (length / Math.max(0.5, f.getTempo())));
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
			List<ServerPlayer> targets = ElderFx.reachable(f, ElderPower.reach(48));
			double x, z, y;
			if (!targets.isEmpty() && rnd.nextInt(3) == 0) {
				// On one he marks: on the ground about them, wherever they stand (a tower top, the floor of a pit)
				ServerPlayer q = targets.get(rnd.nextInt(targets.size()));
				x = q.getX() + rnd.nextDouble(-2, 2);
				z = q.getZ() + rnd.nextDouble(-2, 2);
				double base = ElderFx.groundUnder(q);
				y = ElderFx.surface(level, x, base + 2, z, 2, 6);
				y = Double.isNaN(y) ? base : y;
			} else {
				double a = rnd.nextDouble() * Math.PI * 2, r = 6 + rnd.nextDouble(ElderPower.reach(24));
				x = f.getX() + Math.cos(a) * r;
				z = f.getZ() + Math.sin(a) * r;
				y = ElderFx.surface(level, x, f.getY() - fare.lift + 4, z, 6, 16);
				y = Double.isNaN(y) ? f.getY() - fare.lift : y;
			}
			ElderStrikes.strike(f, x, y, z, 2.6, 7f, 16, ElderStrikes.SOULFALL);
		}
		if (b.every(20, 0)) {
			ElderFight.markPlayers(f, ElderFx.reachable(f, Math.max(0, Config.ELDER_ULTIMATE_RADIUS)), "minecraft:soul_fire_flame");
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
			AttackFx.burst(level, 1, f.getX(), f.getY() + 1, f.getZ(), 8);
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

	/** A fare owed by someone who left the game while he sailed is collected as they come back. */
	public static void collectOwed(ServerPlayer p, PlayerData d) {
		if (d.fareOwed <= 0) {
			return;
		}
		int levels = Math.min(p.experienceLevel, d.fareOwed);
		d.fareOwed = 0;
		if (levels > 0) {
			p.giveExperienceLevels(-levels);
		}
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.ferryman_collects", levels));
		DataStore.markDirty();
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
		ElderArena.release(f);
		ElderGaol.forget(f.getUUID());
		ElderStrikes.forget(f);
		ElderFx.silence(f, ElderFx.MUSIC_FERRYMAN);
		if (fare == null) {
			return;
		}
		fare.bar.removeAllPlayers();
		dropAll(fare);
		ServerLevel level = (ServerLevel) f.level();
		ElderFx.burst(f, "minecraft:soul_fire_flame", 100);
		ElderFx.burst(f, "minecraft:soul", 80);
		AttackFx.burst(level, 1, f.getX(), f.getY() + 2, f.getZ(), 9);
		ElderFx.cinema(f, 160, ModPackets.BossFxPayload.ULTIMATE_END, 0, 0, "");
		for (ItemStack s : Loot.roll((ServerLevel) f.level(), Loot.FERRYMAN, f.position())) {
			ElderFx.loot(f, fare.target, s);
		}
		if (ThreadLocalRandom.current().nextDouble() < Config.FERRYMAN_RELIC_CHANCE) {
			ElderFx.loot(f, fare.target, RelicService.random());
		}
		List<ServerPlayer> there = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(f) >= 64 * 64) {
				continue;
			}
			there.add(p);
			PlayerData d = DataStore.get(p.getUUID());
			Advancements.grant(p, Advancements.FERRYMAN_SLAIN);
			Journal.unlock(p, d, JournalEntry.FERRYMAN);
			com.extremedimensions.tapestry.Tapestry.earn(p, com.extremedimensions.tapestry.Tapestry.Panel.TOLL);
			if ((p.getUUID().equals(fare.target) || p == killer) && d.owned(Penalty.FERRYMANS_FEE) > 0) {
				PenaltyService.lift(p, d, Penalty.FERRYMANS_FEE, "gui.extremedimensions.elder.ferryman_waived");
			}
		}
		DataStore.markDirty();
		com.extremedimensions.compat.DiscordWebhook.elder(com.extremedimensions.compat.StatsExport.Elder.FERRYMAN, there);
	}

	/** Gone: he poles back into the dark, whole. */
	public static void fade(Ferryman f) {
		Fare fare = FARES.remove(f.getUUID());
		ElderArena.release(f);
		ElderGaol.forget(f.getUUID());
		if (fare != null) {
			fare.abandonUltimate(BOSS);
			fare.bar.removeAllPlayers();
			dropAll(fare);
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
