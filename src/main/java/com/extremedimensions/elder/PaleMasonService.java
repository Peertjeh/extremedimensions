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

import org.joml.Quaternionf;

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
import com.extremedimensions.reach.PaleReach;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.server.BlockFlight;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;

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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Pale Mason (3.40): in the Pale Reach, on an expedition, the foreman of its Endermen may come for the visitor's
 * Tether, the one thing his anchor lacks. Stone and chains are heard for {@code pale_mason_wake_seconds} first (the
 * Elders' wind-up); then he steps out of the air, his arena of end stone and purpur flying together round him under a
 * great broken halo. He fights as the Endermen move: he is gone in a burst of violet and comes out of the air beside
 * his prey with a claw already coming down (Teleport Strike); he drives his hands down and walls of end stone grind up
 * out of the floor round them (Anchor Walls); he lifts great blocks into the air and hurls them (Falling Blocks); he
 * goes still and his eyes blaze, and whoever looks him in the face then is struck where they stand (the Gaze; a carved
 * pumpkin keeps it off, as it does an Enderman's). Below half his health he opens the Last Door: a doorway of purpur
 * onto the void that draws everyone towards it (braced against a wall it cannot; sneaking halves it) and strikes down
 * whoever reaches it, until it slams and his curse falls. His floor gives way in his domain. Slain, he leaves the Homing
 * Pearl and his spoils.
 */
public final class PaleMasonService {
	private PaleMasonService() {}

	private static final String BOSS = "boss.extremedimensions.pale_mason";
	private static final String ULT = "gui.extremedimensions.elder.ultimate.pale_mason";

	/** A great block he holds up in the air: its display, where it hangs, and where it was thrown. */
	private static final class Boulder {
		final Display.BlockDisplay display;
		final double x, y, z;
		final Quaternionf spin;
		boolean thrown;
		/** Where it was thrown to land. */
		double tx, ty, tz;

		Boulder(Display.BlockDisplay display, double x, double y, double z, Quaternionf spin) {
			this.display = display;
			this.x = x;
			this.y = y;
			this.z = z;
			this.spin = spin;
		}
	}

	private static final class Masonry extends ElderFight {
		int circle = 1;
		boolean halved;
		/** Where he comes out of the air in a Teleport Strike. */
		double toX, toY, toZ;
		boolean blinked;
		final List<Boulder> boulders = new ArrayList<>();
		/** Who his Gaze has struck this time. */
		final Set<UUID> stared = new HashSet<>();
		/** The Last Door: where it stands, which way it faces, its frame. */
		double doorX, doorY, doorZ, doorDx, doorDz;
		final List<Display.BlockDisplay> door = new ArrayList<>();
		/** Who reached the Door this time (struck once). */
		final Set<UUID> through = new HashSet<>();
		double lift;
		/** Bound Stones: the cubes he swings on their chains, round where he stood as he hauled them up. */
		final List<Bound> bound = new ArrayList<>();
		double boundX, boundY, boundZ;
		/** When each player was last struck by a swinging cube (game tick), so one pass strikes once. */
		final Map<UUID, Long> swept = new HashMap<>();
		/** Pillars of the Anchor: where each pillar will tear up, and the tick of his blow it does. */
		final List<double[]> pillars = new ArrayList<>();
		/** The Unfinished Spiral: the stones turning round him. */
		final List<Bound> spiral = new ArrayList<>();
	}

	/**
	 * A stone he turns round himself in the air (a chained cube, a stone of his spiral): its display stands where he
	 * stood, its look carries it round; where it is now, and where it was flung.
	 */
	private static final class Bound {
		final Display.BlockDisplay display;
		/** Its glowing heart (a chained cube only), or null. */
		final Display.BlockDisplay heart;
		/** Where round him it starts, how big it is, how high it climbs (a share), how far out it turns (a spiral stone). */
		final double phase, size, rise, reach;
		double x, y, z;
		boolean flung;
		double tx, ty, tz;

		Bound(Display.BlockDisplay display, Display.BlockDisplay heart, double phase, double size, double rise, double reach) {
			this.display = display;
			this.heart = heart;
			this.phase = phase;
			this.size = size;
			this.rise = rise;
			this.reach = reach;
		}
	}

	private static final Map<UUID, Masonry> FIGHTS = new HashMap<>();

	static ElderFight fight(Entity e) {
		return FIGHTS.get(e.getUUID());
	}

	public static boolean isDriven(Entity e) {
		return FIGHTS.containsKey(e.getUUID());
	}

	/** Starts one of his blows (or his ultimate) at once; for tests and operators. False if he is not fighting. */
	public static boolean force(Entity e, int action) {
		Masonry fight = FIGHTS.get(e.getUUID());
		if (fight == null || !(e instanceof PaleMason m)) {
			return false;
		}
		ElderSpawn.skip(m, fight);
		if (action == ElderBoss.ULTIMATE) {
			fight.beginUltimate(m, ULT);
		} else {
			fight.start(m, action);
		}
		return true;
	}

	public static void clear() {
		for (Masonry f : FIGHTS.values()) {
			f.bar.removeAllPlayers();
			dropAll(f);
		}
		FIGHTS.clear();
	}

	/** Whether the Pale Mason is after this player. */
	public static boolean hunting(ServerPlayer p) {
		for (Masonry f : FIGHTS.values()) {
			if (p.getUUID().equals(f.target)) {
				return true;
			}
		}
		return false;
	}

	/** How many great blocks he holds up (for tests). */
	public static int boulders(Entity e) {
		Masonry f = FIGHTS.get(e.getUUID());
		return f == null ? 0 : f.boulders.size();
	}

	/** Whether his Last Door stands open (for tests). */
	public static boolean doorOpen(Entity e) {
		Masonry f = FIGHTS.get(e.getUUID());
		return f != null && !f.door.isEmpty();
	}

	public static void tick(MinecraftServer s) {
		drive(s);
		if (s.getTickCount() % 20 != 7 || !PaleReach.open() || Config.PALE_MASON_CHANCE <= 0) {
			return;
		}
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			PlayerData d = DataStore.get(p.getUUID());
			if (!d.inRun || d.masonRolled || d.runActive < Config.PALE_MASON_AFTER_SECONDS || p.isCreative() || p.isSpectator() || !PaleReach.in(p)
					|| Rules.peaceful(p.level())) {
				continue;
			}
			// One chance an expedition, once it has gone on a while out in the Reach
			d.masonRolled = true;
			if (ThreadLocalRandom.current().nextDouble() < Math.min(1, Config.PALE_MASON_CHANCE)) {
				stir(p);
			}
		}
	}

	/** The key of his coming for this player. */
	public static String stirKey(ServerPlayer p) {
		return "pale_mason@" + p.getUUID();
	}

	/**
	 * Stone and chains are heard before he comes: for {@link Config#PALE_MASON_WAKE_SECONDS} a bar counts down for the
	 * one he comes for, wherever they go in the Reach; out of the Reach or home, he lets them be this time. With no
	 * wind-up he comes at once. Returns whether he stirs (or came).
	 */
	public static boolean stir(ServerPlayer p) {
		if (hunting(p) || ElderStir.stirring(stirKey(p)) || !Dims.ours(p)) {
			return false;
		}
		if (Config.PALE_MASON_WAKE_SECONDS <= 0) {
			return summon(p);
		}
		UUID visitor = p.getUUID();
		return ElderStir.begin(new ElderStir.Stir(stirKey(p), "pale_mason", (ServerLevel) p.level(), p.getX(), p.getY(), p.getZ(), p,
				Config.PALE_MASON_WAKE_SECONDS, 0, BossEvent.BossBarColor.PURPLE,
				q -> q.getUUID().equals(visitor) && DataStore.get(visitor).inRun && PaleReach.in(q), PaleMasonService::summon)
				.follows().tint(ChatFormatting.LIGHT_PURPLE).pulse(PaleMasonService::stones));
	}

	/** Each second of his coming: the void stirs round his visitor, and every other second stone and chains, louder as he nears. */
	private static void stones(ServerLevel level, double x, double y, double z, float share) {
		String dim = level.dimension().identifier().toString();
		if ((level.getGameTime() / 20) % 2 == 0) {
			WorldFx.soundIn(dim, "extremedimensions:elder.pale_mason.wake", "hostile", x, y + 6, z, 1.5f + 3f * share, 1f);
		}
		if (share > 0.7f) {
			WorldFx.soundIn(dim, "minecraft:entity.enderman.stare", "hostile", x, y + 4, z, 1.5f, 0.5f);
		}
		WorldFx.particleIn(dim, "minecraft:reverse_portal", x, y + 1, z, 10, 2, 10, 0.05, 6 + (int) (share * 24));
	}

	/** He comes for this player (also the operator command); false if he is already after them or cannot come here. */
	public static boolean summon(ServerPlayer p) {
		return !hunting(p) && summonAt(p, ElderFx.near((ServerLevel) p.level(), p, 14, 20)) != null;
	}

	/** He comes for this player at this spot: the Pale Mason, or null if he is already after them. */
	public static PaleMason summonAt(ServerPlayer p, BlockPos spot) {
		if (hunting(p) || !Dims.ours(p)) {
			return null;
		}
		ServerLevel level = (ServerLevel) p.level();
		PaleMason m = ModEntities.PALE_MASON.spawn(level, spot, EntitySpawnReason.EVENT);
		if (m == null) {
			return null;
		}
		m.setPersistenceRequired();
		m.setTarget(p);
		float yaw = ElderFx.yawTo(m, p.getX(), p.getZ());
		m.setYRot(yaw);
		m.setYBodyRot(yaw);
		m.setYHeadRot(yaw);
		Masonry f = new Masonry();
		f.target = p.getUUID();
		f.level = level;
		f.circle = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
		f.makeBar(BOSS, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10, p);
		f.cooldown = 60;
		FIGHTS.put(m.getUUID(), f);
		ElderSpawn.begin(m, f, spot);
		ElderFx.roar(m, 4f, 0.7f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.pale_mason_comes").withStyle(ChatFormatting.LIGHT_PURPLE));
		Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.PALE_MASON);
		ElderFx.music(m, ElderFx.MUSIC_PALE_MASON);
		return m;
	}

	private static void drive(MinecraftServer s) {
		if (FIGHTS.isEmpty()) {
			return;
		}
		List<Runnable> later = new ArrayList<>();
		for (Map.Entry<UUID, Masonry> e : List.copyOf(FIGHTS.entrySet())) {
			Masonry f = e.getValue();
			if (FIGHTS.get(e.getKey()) != f) {
				continue;
			}
			Entity ent = f.level.getEntity(e.getKey());
			if (!(ent instanceof PaleMason m) || !m.isAlive()) {
				f.bar.removeAllPlayers();
				dropAll(f);
				FIGHTS.remove(e.getKey());
				continue;
			}
			f.ticks++;
			ServerPlayer p = s.getPlayerList().getPlayer(f.target);
			if (p == null || !p.isAlive() || p.level() != m.level() || m.distanceToSqr(p) > ElderPower.reach(80) * ElderPower.reach(80)) {
				// His visitor gone (home, dead, out of reach): another near takes their place, or he goes back into the void
				ServerPlayer next = f.successor(m, ElderPower.reach(48));
				if (next != null) {
					f.target = next.getUUID();
					f.makeBarFor(next);
					continue;
				}
				later.add(() -> fade(m));
				continue;
			}
			fight(m, f, p);
		}
		later.forEach(Runnable::run);
	}

	private static void fight(PaleMason m, Masonry f, ServerPlayer p) {
		ServerLevel level = f.level;
		if (f.ticks % 20 == 0) {
			m.setTarget(p);
		}
		if (f.ticks % 20 == 1) {
			ElderFx.music(m, ElderFx.MUSIC_PALE_MASON);
		}
		f.keepBar(m);
		if (f.ticks % 3 == 0) {
			WorldFx.particleIn(level, "minecraft:portal", m.getX(), m.getY() + m.getBbHeight() * 0.5, m.getZ(), 1.6, m.getBbHeight() * 0.35, 1.6, 0.4, 3);
			ElderFx.ambience(m);
		}
		hold(m, f);
		if (f.action == ElderBoss.SPAWN) {
			m.motion.brake(m, 0.02, 0.1);
			ElderFx.look(m, p);
			ElderSpawn.tick(m, f);
			return;
		}
		if (f.dazed(m)) {
			m.motion.brake(m, 0.04, 0.1);
			return;
		}
		if (!f.halved && m.getHealth() <= m.getMaxHealth() / 2) {
			f.halved = true;
			f.enraged = true;
			ElderFx.roar(m, 4f, 0.55f);
			WorldFx.soundIn(ElderFx.dim(m), "extremedimensions:elder.pale_mason.phase", "hostile", m.getX(), m.getY() + 10, m.getZ(), 5f, 1f);
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.pale_mason_halved").withStyle(ChatFormatting.LIGHT_PURPLE), true);
		}
		double dx = p.getX() - m.getX(), dz = p.getZ() - m.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (f.action == ElderBoss.IDLE) {
			if (ElderUltimate.due(m, f.ultimateSpent)) {
				f.beginUltimate(m, ULT);
				ElderFx.roar(m, 6f, 0.5f);
				return;
			}
			// He keeps a stride or two off his prey and walks round them; left far behind, he is simply there again
			// (with no ground near them to step out onto, he walks after them as any Elder does)
			if (dist > ElderPower.reach(30) && f.ticks % 10 == 0 && blink(m, f, p, 7)) {
				f.cooldown = Math.max(f.cooldown, 20);
				return;
			}
			double tx, tz, speed;
			if (dist > 13) {
				tx = p.getX();
				tz = p.getZ();
				speed = 0.22;
			} else {
				double a = Math.atan2(m.getZ() - p.getZ(), m.getX() - p.getX()) + f.circle * 0.4;
				tx = p.getX() + Math.cos(a) * 10;
				tz = p.getZ() + Math.sin(a) * 10;
				speed = 0.14;
			}
			m.motion.toward(m, tx, tz, ElderFight.speed(speed), 0.02, 0.1);
			if (m.motion.speed() > 0.03) {
				m.motion.turn(m, (float) (Math.atan2(m.motion.vz, m.motion.vx) * 180 / Math.PI) - 90f, 4f);
				f.walk(m, 4.5, 2.5f);
			}
			ElderFx.look(m, p);
			if (f.ready()) {
				double rise = p.getY() - m.getY();
				// (3.41: his chained stones, his pillars, his golden rings and his spiral besides)
				int a = rise > 4 ? f.pick(PaleMason.STRIKE, 4, PaleMason.LIFT, 3, PaleMason.PILLARS, 3, PaleMason.GAZE, 2, PaleMason.CHAINS, 1)
						: dist <= 8 ? f.pick(PaleMason.SPIRAL, 3, PaleMason.WALLS, 3, PaleMason.RINGS, 2, PaleMason.GAZE, 2, PaleMason.STRIKE, 2,
								PaleMason.LIFT, 1)
						: f.pick(PaleMason.CHAINS, 3, PaleMason.PILLARS, 3, PaleMason.STRIKE, 3, PaleMason.LIFT, 2, PaleMason.RINGS, 2,
								PaleMason.WALLS, 2, PaleMason.GAZE, 2, PaleMason.SPIRAL, 1);
				f.start(m, a);
			}
			return;
		}
		f.step();
		Beat b = f.beat;
		if (f.action != ElderBoss.ULTIMATE) {
			m.motion.brake(m, 0.08, 0.1);
			if ((f.action != PaleMason.GAZE || b.t < 20) && f.action != PaleMason.CHAINS && f.action != PaleMason.SPIRAL) {
				m.motion.turn(m, ElderFx.yawTo(m, p.getX(), p.getZ()), 4f);
				ElderFx.look(m, p);
			}
		}
		switch (f.action) {
			case PaleMason.STRIKE -> strike(m, f, p, b);
			case PaleMason.WALLS -> walls(m, f, p, b);
			case PaleMason.LIFT -> lift(m, f, b);
			case PaleMason.THROW -> hurl(m, f, p, b);
			case PaleMason.GAZE -> gaze(m, f, b);
			case PaleMason.CHAINS -> chains(m, f, p, b);
			case PaleMason.PILLARS -> pillars(m, f, p, b);
			case PaleMason.RINGS -> rings(m, f, b);
			case PaleMason.SPIRAL -> spiral(m, f, b);
			case ElderBoss.ULTIMATE -> lastDoor(m, f, p, b);
			default -> f.end(m, 40);
		}
	}

	// ------------------------------------------------------------------ Teleport Strike

	/** Gone in a burst of violet, and out of the air again {@code off} blocks from this player, facing them. */
	private static boolean blink(PaleMason m, Masonry f, ServerPlayer p, double off) {
		ServerLevel level = f.level;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double base = Math.atan2(m.getZ() - p.getZ(), m.getX() - p.getX());
		for (int tries = 0; tries < 8; tries++) {
			// round to their side or behind them, never straight back where he stood
			double a = base + Math.PI * (0.5 + rnd.nextDouble()) * (rnd.nextBoolean() ? 1 : -1);
			double x = p.getX() + Math.cos(a) * off, z = p.getZ() + Math.sin(a) * off;
			double y = ElderFx.surface(level, x, p.getY() + 4, z, 6, 12);
			if (Double.isNaN(y)) {
				continue;
			}
			String dim = ElderFx.dim(m);
			WorldFx.particleIn(level, "minecraft:portal", m.getX(), m.getY() + m.getBbHeight() * 0.5, m.getZ(), 1.5, m.getBbHeight() * 0.4, 1.5, 1.2, 120);
			WorldFx.soundIn(dim, "minecraft:entity.enderman.teleport", "hostile", m.getX(), m.getY() + 8, m.getZ(), 4f, 0.5f);
			m.teleportTo(x, y, z);
			m.motion.halt();
			float yaw = ElderFx.yawTo(m, p.getX(), p.getZ());
			m.setYRot(yaw);
			m.setYBodyRot(yaw);
			m.setYHeadRot(yaw);
			WorldFx.particleIn(level, "minecraft:reverse_portal", x, y + m.getBbHeight() * 0.5, z, 1.5, m.getBbHeight() * 0.4, 1.5, 0.6, 120);
			WorldFx.soundIn(dim, "minecraft:entity.enderman.teleport", "hostile", x, y + 8, z, 4f, 0.45f);
			return true;
		}
		return false;
	}

	/**
	 * Teleport Strike: he blinks out of sight and steps out of the air beside his prey, one claw already raised; a sigil
	 * kindles under them and the claw comes down on it.
	 */
	private static void strike(PaleMason m, Masonry f, ServerPlayer prey, Beat b) {
		ServerPlayer p = f.aimed(prey);
		// (his body thins to a sliver at 6-10 and fills out again by 15; the claw is up by 22 and comes down at 26)
		if (b.at(8)) {
			f.blinked = blink(m, f, p, 4.5);
		}
		if (b.at(10)) {
			double y = ElderFx.groundUnder(p);
			f.toX = p.getX();
			f.toY = y;
			f.toZ = p.getZ();
			ElderStrikes.strike(m, f.toX, f.toY, f.toZ, 3.2, (float) Config.PALE_MASON_STRIKE_DAMAGE, f.delay(16), ElderStrikes.SLAM);
		}
		if (b.at(26)) {
			WorldFx.soundIn(ElderFx.dim(m), "extremedimensions:elder.pale_mason.strike", "hostile", f.toX, f.toY + 1, f.toZ, 4f, 1f);
			ElderFx.shake(m, f.toX, f.toZ, 24, 0.7f, 12);
			AttackFx.impact(f.level, AttackFx.of(m), AttackFx.IMPACT_BLAST, f.toX, f.toY + 0.2, f.toZ, 3.2);
		}
		if (b.past(40)) {
			if (!f.again(m, prey)) {
				f.end(m, f.enraged ? 30 : 45);
			}
		}
	}

	// ------------------------------------------------------------------ Anchor Walls

	/**
	 * Anchor Walls: both hands raised, then driven down; walls of end stone and purpur grind up out of the floor round
	 * his prey, a gap left in each, and stand {@code pale_mason_wall_seconds} before they crumble. They hem a fighter in;
	 * they also shelter from his Door.
	 */
	private static void walls(PaleMason m, Masonry f, ServerPlayer prey, Beat b) {
		// (both arms up 0-14, palms driven down at 20)
		if (b.at(20)) {
			ServerLevel level = f.level;
			ThreadLocalRandom rnd = ThreadLocalRandom.current();
			int ticks = Math.max(20, Config.PALE_MASON_WALL_SECONDS * 20);
			for (ServerPlayer p : f.split(m, prey, ElderPower.reach(40))) {
				double turn = rnd.nextDouble() * Math.PI * 2;
				int sides = 3;
				for (int side = 0; side < sides; side++) {
					double a = turn + side * Math.PI * 2 / sides;
					double r = 4.5 + rnd.nextDouble(1.5);
					double cx = p.getX() + Math.cos(a) * r, cz = p.getZ() + Math.sin(a) * r;
					// along the side, square to the line from the player
					double ux = -Math.sin(a), uz = Math.cos(a);
					int half = 4;
					int gap = rnd.nextInt(-half + 1, half);
					for (int i = -half; i <= half; i++) {
						if (i == gap || i == gap + 1) {
							continue;
						}
						double x = cx + ux * i, z = cz + uz * i;
						ElderTerrain.column(level, x, ElderFx.groundUnder(p) + 3, z, 4, i % 3 == 0 ? Blocks.PURPUR_PILLAR : Blocks.END_STONE_BRICKS, ticks);
						if (rnd.nextInt(3) == 0) {
							WorldFx.particleIn(level, "minecraft:portal", x, ElderFx.groundUnder(p) + 1, z, 0.4, 0.6, 0.4, 0.4, 6);
						}
					}
				}
				AttackFx.ring(level, AttackFx.of(m), p.getX(), ElderFx.groundUnder(p) + 0.1, p.getZ(), 6, 20);
			}
			ElderFx.shake(m, m.getX(), m.getZ(), 32, 0.6f, 14);
		}
		if (b.past(36)) {
			f.end(m, 50);
		}
	}

	// ------------------------------------------------------------------ Falling Blocks

	/** Falling Blocks: he lifts his arms and great blocks of the End rise round him, turning, high over the arena. */
	private static void lift(PaleMason m, Masonry f, Beat b) {
		ServerLevel level = f.level;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int count = Math.min(8, 3 + Math.max(1, f.fighters));
		if (b.between(4, 4 + count * 3) && b.every(3, 4) && f.boulders.size() < count) {
			double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble(4, 9);
			double x = m.getX() + Math.cos(a) * r, z = m.getZ() + Math.sin(a) * r;
			double y = m.getY() + 14 + rnd.nextDouble(5);
			BlockState[] kinds = {Blocks.END_STONE_BRICKS.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(),
					Blocks.END_STONE.defaultBlockState()};
			BlockState state = kinds[rnd.nextInt(kinds.length)];
			Quaternionf spin = new Quaternionf().rotateAxis((float) rnd.nextDouble(0.3, 1.2), BlockFlight.randomAxis(rnd));
			// it comes up off the floor under where it will hang, swelling to its size as it rises
			Display.BlockDisplay d = BlockFlight.spawn(level, x, y, z, state, BlockFlight.pose(0, -12, 0, new Quaternionf(), 0.6F));
			if (d != null) {
				DisplayAccess acc = (DisplayAccess) d;
				acc.extremedimensions$setTransformation(BlockFlight.pose(0, 0, 0, spin, (float) Config.PALE_MASON_BLOCK_SIZE));
				acc.extremedimensions$setInterpolationDuration(18);
				acc.extremedimensions$setInterpolationDelay(0);
				f.boulders.add(new Boulder(d, x, y, z, spin));
				WorldFx.particleIn(level, "minecraft:reverse_portal", x, y - 10, z, 1, 1, 1, 0.3, 14);
			}
		}
		if (b.past(4 + count * 3 + 18)) {
			// then he hurls them
			if (f.boulders.isEmpty()) {
				f.end(m, 40);
			} else {
				f.start(m, PaleMason.THROW);
			}
		}
	}

	/** ... and hurls them, one after another, at whoever stands under them: a sigil where each will land, then the crash. */
	private static void hurl(PaleMason m, Masonry f, ServerPlayer prey, Beat b) {
		ServerLevel level = f.level;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		List<ServerPlayer> targets = ElderFx.reachable(m, ElderPower.reach(48));
		if (targets.isEmpty()) {
			targets = List.of(prey);
		}
		int flight = Math.max(10, f.delay(18));
		int i = 0;
		for (Boulder bl : f.boulders) {
			// (his arm is drawn back 0-12 and sweeps forward at 16: the first leaves then, the rest after it)
			double at = 16 + i * 5;
			if (!bl.thrown && b.at(at)) {
				bl.thrown = true;
				ServerPlayer q = targets.get(i % targets.size());
				// where they stand, or a little ahead of where they are going
				Vec3 v = q.getDeltaMovement();
				double tx = q.getX() + v.x * 6 + rnd.nextDouble(-1, 1), tz = q.getZ() + v.z * 6 + rnd.nextDouble(-1, 1);
				double ty = ElderFx.surface(level, tx, q.getY() + 3, tz, 4, 10);
				if (Double.isNaN(ty)) {
					ty = ElderFx.groundUnder(q);
				}
				bl.tx = tx;
				bl.ty = ty;
				bl.tz = tz;
				DisplayAccess acc = (DisplayAccess) bl.display;
				Quaternionf tumble = new Quaternionf(bl.spin).rotateAxis((float) rnd.nextDouble(2, 4), BlockFlight.randomAxis(rnd));
				// (a display draws its block about a centre half a block over and down from it: the block comes to rest on
				// the ground, its middle over the sigil's)
				double size = Config.PALE_MASON_BLOCK_SIZE;
				acc.extremedimensions$setTransformation(
						BlockFlight.pose(tx - bl.x - 0.5, ty + size / 2 + 0.5 - bl.y, tz - bl.z - 0.5, tumble, (float) size));
				acc.extremedimensions$setInterpolationDuration(flight);
				acc.extremedimensions$setInterpolationDelay(0);
				ElderStrikes.strike(m, tx, ty, tz, Config.PALE_MASON_BLOCK_SIZE * 1.1, (float) Config.PALE_MASON_BLOCK_DAMAGE, flight, ElderStrikes.SLAM);
				WorldFx.soundIn(ElderFx.dim(m), "extremedimensions:elder.pale_mason.throw", "hostile", bl.x, bl.y, bl.z, 3f, 0.9f + rnd.nextFloat() * 0.2f);
			}
			if (bl.thrown && b.at(at + flight)) {
				// it lands and breaks
				double tx = bl.tx, ty = bl.ty, tz = bl.tz;
				WorldFx.soundIn(ElderFx.dim(m), "extremedimensions:elder.pale_mason.impact", "hostile", tx, ty, tz, 4f, 1f);
				WorldFx.particleIn(level, "minecraft:explosion", tx, ty + 0.5, tz, 1, 0.5, 1, 0, 2);
				WorldFx.particleIn(level, "minecraft:portal", tx, ty + 0.5, tz, 1.5, 0.5, 1.5, 0.8, 30);
				ElderFx.shake(m, tx, tz, 20, 0.5f, 10);
				BlockFlight.drop(bl.display);
			}
			i++;
		}
		double last = 16 + (f.boulders.size() - 1) * 5 + flight;
		if (b.past(last + 6)) {
			dropAll(f);
			f.end(m, f.enraged ? 35 : 50);
		}
	}

	/** Any great blocks he still holds come down to nothing (his blow cut short, his fight over). */
	private static void dropAll(Masonry f) {
		for (Boulder bl : f.boulders) {
			if (!bl.display.isRemoved()) {
				WorldFx.particleIn(bl.display.level(), "minecraft:portal", bl.x, bl.y, bl.z, 1, 1, 1, 0.5, 12);
			}
			BlockFlight.drop(bl.display);
		}
		f.boulders.clear();
		dropBound(f.bound);
		dropBound(f.spiral);
		f.pillars.clear();
		for (Display.BlockDisplay d : f.door) {
			BlockFlight.drop(d);
		}
		f.door.clear();
	}

	/** Blocks he holds hang where they are while a blow other than his throw cuts in (a daze, his Door). */
	private static void hold(PaleMason m, Masonry f) {
		if (!f.boulders.isEmpty() && f.action != PaleMason.LIFT && f.action != PaleMason.THROW) {
			dropAll(f);
		}
		if (!f.bound.isEmpty() && f.action != PaleMason.CHAINS) {
			dropBound(f.bound);
		}
		if (!f.spiral.isEmpty() && f.action != PaleMason.SPIRAL) {
			dropBound(f.spiral);
		}
		if (!f.pillars.isEmpty() && f.action != PaleMason.PILLARS) {
			f.pillars.clear();
		}
	}

	/** Stones he turns in the air come down to nothing. */
	private static void dropBound(List<Bound> list) {
		for (Bound s : list) {
			if (!s.display.isRemoved()) {
				WorldFx.particleIn(s.display.level(), "minecraft:portal", s.x, s.y, s.z, 1, 1, 1, 0.5, 12);
			}
			BlockFlight.drop(s.display);
			if (s.heart != null) {
				BlockFlight.drop(s.heart);
			}
		}
		list.clear();
	}

	// ------------------------------------------------------------------ the Gaze

	/**
	 * The Gaze: he goes still and his eyes blaze for a moment; then for two seconds whoever looks him in the face is
	 * struck where they stand (once). Look away, or at his feet; a carved pumpkin keeps it off.
	 */
	private static void gaze(PaleMason m, Masonry f, Beat b) {
		ServerLevel level = f.level;
		if (b.at(1)) {
			f.stared.clear();
			for (ServerPlayer q : ElderFx.targets(m, ElderPower.reach(48))) {
				q.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.pale_mason_gaze").withStyle(ChatFormatting.LIGHT_PURPLE), true);
			}
		}
		double eyeY = m.getY() + m.getEyeHeight();
		// (he goes still; his eyes swell and flicker from 8, burn to 48 and are out by 58)
		if (b.between(1, 20) && b.every(3, 1)) {
			WorldFx.particleIn(level, "minecraft:reverse_portal", m.getX(), eyeY, m.getZ(), 1.2, 0.6, 1.2, 0.2, 10);
		}
		if (b.between(20, 52)) {
			for (ServerPlayer q : ElderFx.targets(m, ElderPower.reach(48))) {
				if (f.stared.contains(q.getUUID()) || !looksAt(q, m)) {
					continue;
				}
				f.stared.add(q.getUUID());
				q.hurtServer(level, ElderPower.great(level, m), (float) Config.PALE_MASON_GAZE_DAMAGE);
				WorldFx.effect(q, MobEffects.DARKNESS, 4, 0);
				WorldFx.particleIn(level, "minecraft:portal", q.getX(), q.getY() + 1, q.getZ(), 0.6, 1, 0.6, 1.2, 40);
				WorldFx.soundIn(ElderFx.dim(m), "minecraft:entity.enderman.scream", "hostile", q.getX(), q.getY() + 1, q.getZ(), 2f, 0.6f);
				AttackFx.line(level, AttackFx.of(m), AttackFx.LINE_BEAM, m.getX(), eyeY, m.getZ(), q.getX(), q.getEyeY(), q.getZ(), 8);
			}
		}
		if (b.past(60)) {
			f.end(m, 45);
		}
	}

	/**
	 * Whether a player looks the Mason in the face, as an Enderman tells it: their gaze within a few degrees of his
	 * eyes, nothing between, and no carved pumpkin over their face (public for tests).
	 */
	public static boolean looksAt(ServerPlayer q, PaleMason m) {
		if (q.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN)) {
			return false;
		}
		Vec3 view = q.getViewVector(1.0F).normalize();
		Vec3 to = new Vec3(m.getX() - q.getX(), m.getY() + m.getEyeHeight() - q.getEyeY(), m.getZ() - q.getZ());
		double d = to.length();
		if (d < 1e-3) {
			return false;
		}
		double dot = view.dot(to.scale(1 / d));
		// his face is broad: a cone of about 2.5 blocks round his eyes at the distance he stands
		return dot > Math.cos(Math.atan2(2.5, d)) && q.hasLineOfSight(m);
	}

	// ------------------------------------------------------------------ Bound Stones

	/** Smoothstep of a share, held to 0..1. */
	private static double ease(double k) {
		k = Math.max(0, Math.min(1, k));
		return k * k * (3 - 2 * k);
	}

	/** Moves a stone he turns to have its middle at (cx, cy, cz), turned by q, over {@code ticks}. */
	private static void place(Masonry f, Bound s, Display.BlockDisplay d, double cx, double cy, double cz, Quaternionf q, double size, int ticks) {
		DisplayAccess acc = (DisplayAccess) d;
		acc.extremedimensions$setTransformation(BlockFlight.pose(cx - f.boundX - 0.5, cy - f.boundY + 0.5, cz - f.boundZ - 0.5, q, (float) size));
		acc.extremedimensions$setInterpolationDuration(ticks);
		acc.extremedimensions$setInterpolationDelay(0);
	}

	/** How far out his chained stones swing at this beat: far out, then drawn in close round him. */
	private static double swing(Masonry f, double t) {
		double far = f.enraged ? 16 : 14;
		if (t < 24) {
			return 4 + (far - 4) * ease(t / 24);
		}
		if (t < 60) {
			return far;
		}
		return far - (far - 7) * ease((t - 60) / 30);
	}

	/** Where chained stone {@code s} is at this beat of his swing. */
	private static void orbit(Masonry f, Bound s, double t) {
		double r = swing(f, t);
		double a = s.phase + Math.max(0, t - 4) * Math.PI * 2 / (f.enraged ? 64 : 80);
		s.x = f.boundX + Math.cos(a) * r;
		s.z = f.boundZ + Math.sin(a) * r;
		// hauled down out of the air to the height of a man, then rolling a little as it swings
		s.y = f.boundY + 11 + (2.2 - 11) * ease(t / 24) + Math.sin(t * 0.15 + s.phase) * 0.4;
	}

	/**
	 * Bound Stones: he hauls great cubes of violet light out of the air on chains of gold and swings them round himself at
	 * the height of a man, far out and then drawn in close (a ring on the floor shows where each sweep runs before it
	 * comes); whoever is caught in their path is struck and flung along it. Then he hurls them away at whoever is near.
	 * (The cubes come down at 24 of his blow and leave his hands at 104.)
	 */
	private static void chains(PaleMason m, Masonry f, ServerPlayer prey, Beat b) {
		ServerLevel level = f.level;
		String dim = ElderFx.dim(m);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int elder = AttackFx.of(m);
		if (b.at(2)) {
			dropBound(f.bound);
			f.boundX = m.getX();
			f.boundY = m.getY();
			f.boundZ = m.getZ();
			f.swept.clear();
			int n = Math.min(4, 2 + (f.enraged ? 1 : 0) + (f.fighters > 2 ? 1 : 0));
			Brightness lit = new Brightness(15, 15);
			double turn = rnd.nextDouble() * Math.PI * 2;
			for (int i = 0; i < n; i++) {
				Display.BlockDisplay cube = BlockFlight.spawn(level, f.boundX, f.boundY, f.boundZ, Blocks.PURPLE_STAINED_GLASS.defaultBlockState(),
						BlockFlight.pose(0, 11, 0, new Quaternionf(), 0.2F));
				Display.BlockDisplay heart = BlockFlight.spawn(level, f.boundX, f.boundY, f.boundZ, Blocks.CRYING_OBSIDIAN.defaultBlockState(),
						BlockFlight.pose(0, 11, 0, new Quaternionf(), 0.1F));
				if (cube == null || heart == null) {
					if (cube != null) {
						BlockFlight.drop(cube);
					}
					if (heart != null) {
						BlockFlight.drop(heart);
					}
					continue;
				}
				((DisplayAccess) cube).extremedimensions$setBrightnessOverride(lit);
				((DisplayAccess) heart).extremedimensions$setBrightnessOverride(lit);
				f.bound.add(new Bound(cube, heart, turn + Math.PI * 2 * i / n, 3.0, 0, 0));
			}
			WorldFx.soundIn(dim, "extremedimensions:elder.pale_mason.chains", "hostile", f.boundX, f.boundY + 10, f.boundZ, 4f, 1f);
		}
		if (f.bound.isEmpty()) {
			if (b.past(4)) {
				f.end(m, 40);
			}
			return;
		}
		// The rings on the floor show each sweep before it comes: far out, then close in
		if (b.at(6)) {
			AttackFx.ring(level, elder, f.boundX, f.boundY + 0.1, f.boundZ, swing(f, 30), 54);
		}
		if (b.at(52)) {
			AttackFx.ring(level, elder, f.boundX, f.boundY + 0.1, f.boundZ, 7, 46);
		}
		if (!b.past(100)) {
			for (Bound s : f.bound) {
				orbit(f, s, b.t);
			}
			// he turns with the swing, his arms out along its chains
			Bound lead = f.bound.get(0);
			m.motion.turn(m, ElderFx.yawTo(m, lead.x, lead.z), 14f);
			if (b.every(2, 0)) {
				for (Bound s : f.bound) {
					// (aimed where it will be when the move ends)
					Bound ahead = new Bound(null, null, s.phase, s.size, 0, 0);
					orbit(f, ahead, b.t + 2);
					Quaternionf q = new Quaternionf().rotateY((float) (-b.t * 0.12 - s.phase)).rotateX((float) (b.t * 0.07));
					place(f, s, s.display, ahead.x, ahead.y, ahead.z, q, s.size, 2);
					place(f, s, s.heart, ahead.x, ahead.y, ahead.z, new Quaternionf(q).rotateZ(0.8F), s.size * 0.45, 2);
					// its chain, from his hand to it
					double ha = Math.atan2(ahead.z - f.boundZ, ahead.x - f.boundX);
					AttackFx.line(level, elder, AttackFx.LINE_CHAIN, f.boundX + Math.cos(ha) * 4.5, f.boundY + 11, f.boundZ + Math.sin(ha) * 4.5, ahead.x,
							ahead.y + 0.6, ahead.z, 3);
				}
			}
			if (b.between(24, 100) && b.every(20, 24)) {
				Bound s = f.bound.get(0);
				WorldFx.soundIn(dim, "extremedimensions:elder.pale_mason.swing", "hostile", s.x, s.y, s.z, 3.5f, 0.9f + rnd.nextFloat() * 0.2f);
			}
			if (b.past(12)) {
				// Caught in a sweep: struck once a pass and flung along it
				long now = level.getGameTime();
				for (ServerPlayer q : ElderFx.targets(m, ElderPower.reach(48))) {
					for (Bound s : f.bound) {
						double dx = q.getX() - s.x, dz = q.getZ() - s.z, dy = q.getY() + 0.9 - s.y;
						if (dx * dx + dz * dz > Math.pow(s.size / 2 + 0.6, 2) || Math.abs(dy) > s.size / 2 + 0.9) {
							continue;
						}
						Long last = f.swept.get(q.getUUID());
						if (last != null && now - last < 12) {
							continue;
						}
						f.swept.put(q.getUUID(), now);
						q.hurtServer(level, ElderPower.great(level, m), (float) Config.PALE_MASON_CHAIN_DAMAGE);
						double a = Math.atan2(s.z - f.boundZ, s.x - f.boundX);
						q.setDeltaMovement(-Math.sin(a) * 1.1 + Math.cos(a) * 0.7, 0.55, Math.cos(a) * 1.1 + Math.sin(a) * 0.7);
						WorldFx.moved(q);
						WorldFx.soundIn(dim, "extremedimensions:elder.pale_mason.impact", "hostile", q.getX(), q.getY() + 1, q.getZ(), 2.5f, 1.3f);
						AttackFx.impact(level, elder, AttackFx.IMPACT_BLAST, q.getX(), q.getY() + 1, q.getZ(), 1.6);
					}
				}
			}
		}
		int flight = Math.max(10, f.delay(16));
		if (b.at(104)) {
			// He hurls them: at whoever is near, else away from him
			List<ServerPlayer> targets = ElderFx.reachable(m, ElderPower.reach(48));
			WorldFx.soundIn(dim, "extremedimensions:elder.pale_mason.fling", "hostile", f.boundX, f.boundY + 8, f.boundZ, 4f, 1f);
			for (int i = 0; i < f.bound.size(); i++) {
				Bound s = f.bound.get(i);
				double tx, tz;
				if (!targets.isEmpty()) {
					ServerPlayer q = targets.get(i % targets.size());
					tx = q.getX() + rnd.nextDouble(-1.5, 1.5);
					tz = q.getZ() + rnd.nextDouble(-1.5, 1.5);
				} else {
					double a = Math.atan2(s.z - f.boundZ, s.x - f.boundX);
					tx = f.boundX + Math.cos(a) * 22;
					tz = f.boundZ + Math.sin(a) * 22;
				}
				double ty = ElderFx.surface(level, tx, f.boundY + 6, tz, 8, 16);
				if (Double.isNaN(ty)) {
					ty = f.boundY;
				}
				s.flung = true;
				s.tx = tx;
				s.ty = ty;
				s.tz = tz;
				Quaternionf q = new Quaternionf().rotateY(rnd.nextFloat() * 6.28F).rotateX(2.6F);
				place(f, s, s.display, tx, ty + s.size / 2, tz, q, s.size, flight);
				place(f, s, s.heart, tx, ty + s.size / 2, tz, q, s.size * 0.45, flight);
				ElderStrikes.strike(m, tx, ty, tz, s.size * 1.1, (float) Config.PALE_MASON_CHAIN_DAMAGE, flight, ElderStrikes.SLAM);
			}
		}
		if (b.at(104 + flight)) {
			for (Bound s : f.bound) {
				if (s.flung) {
					WorldFx.soundIn(dim, "extremedimensions:elder.pale_mason.impact", "hostile", s.tx, s.ty, s.tz, 4f, 0.9f);
					WorldFx.particleIn(level, "minecraft:explosion", s.tx, s.ty + 1, s.tz, 1, 0.5, 1, 0, 2);
					WorldFx.particleIn(level, "minecraft:portal", s.tx, s.ty + 1, s.tz, 1.5, 1, 1.5, 0.9, 40);
					ElderFx.shake(m, s.tx, s.tz, 24, 0.55f, 10);
				}
			}
			dropBound(f.bound);
		}
		if (b.past(104 + flight + 8)) {
			dropBound(f.bound);
			f.end(m, f.enraged ? 35 : 50);
		}
	}

	// ------------------------------------------------------------------ Pillars of the Anchor

	/** A pillar of light will tear up here once {@code left} ticks are past (its sigil is lit now, its blow laid). */
	private static void pillar(PaleMason m, Masonry f, double x, double y, double z, int left) {
		ElderStrikes.strike(m, x, y, z, 2.0, (float) Config.PALE_MASON_PILLAR_DAMAGE, left, ElderStrikes.SLAM);
		f.pillars.add(new double[] {x, y, z, left});
	}

	/**
	 * Pillars of the Anchor: he raises a hand and drives his fist into the floor, and pillars of violet light tear up out
	 * of it in three pulses, each a ring further out round him, and each under everyone he can see (a sigil burns where
	 * each will come, a moment before). His fist lands at 24, his palms at 40 and 56.
	 */
	private static void pillars(PaleMason m, Masonry f, ServerPlayer prey, Beat b) {
		ServerLevel level = f.level;
		String dim = ElderFx.dim(m);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int elder = AttackFx.of(m);
		for (int k = 0; k < 3; k++) {
			int lands = 24 + k * 16;
			if (b.at(lands - 16)) {
				int left = f.delay(16);
				double rr = 8 + k * 7;
				int n = 8 + k * 4;
				double turn = rnd.nextDouble() * Math.PI * 2;
				for (int i = 0; i < n; i++) {
					double a = turn + Math.PI * 2 * i / n;
					double x = m.getX() + Math.cos(a) * rr, z = m.getZ() + Math.sin(a) * rr;
					double y = ElderFx.surface(level, x, m.getY() + 4, z, 6, 12);
					if (!Double.isNaN(y)) {
						pillar(m, f, x, y, z, left);
					}
				}
				for (ServerPlayer q : ElderFx.targets(m, ElderPower.reach(48))) {
					pillar(m, f, q.getX(), ElderFx.groundUnder(q), q.getZ(), left);
				}
			}
			if (b.at(lands)) {
				ElderFx.shake(m, m.getX(), m.getZ(), 40, 0.45f + 0.1f * k, 10);
				AttackFx.impact(level, elder, AttackFx.IMPACT_BLAST, m.getX(), m.getY() + 0.2, m.getZ(), 3.5);
			}
		}
		// Each tears up as its time comes: a column of light thirty blocks high
		int sounded = 0;
		for (Iterator<double[]> it = f.pillars.iterator(); it.hasNext();) {
			double[] p = it.next();
			p[3]--;
			if (p[3] > 0) {
				continue;
			}
			it.remove();
			AttackFx.line(level, elder, AttackFx.LINE_BEAM, p[0], p[1] + 0.2, p[2], p[0], p[1] + 30, p[2], 14);
			for (int i = 0; i < 10; i++) {
				WorldFx.particleIn(level, i % 2 == 0 ? "minecraft:end_rod" : "minecraft:reverse_portal", p[0], p[1] + i * 3, p[2], 0.3, 1.2, 0.3, 0.05, 3);
			}
			if (sounded++ < 3) {
				WorldFx.soundIn(dim, "extremedimensions:elder.pale_mason.pillar", "hostile", p[0], p[1] + 2, p[2], 3.5f, 0.9f + rnd.nextFloat() * 0.2f);
			}
		}
		if (b.past(72) && f.pillars.isEmpty()) {
			f.end(m, f.enraged ? 30 : 45);
		}
	}

	// ------------------------------------------------------------------ Golden Rings

	/**
	 * Golden Rings: his halo flares and turns, and three times he casts a ring of gold out across the floor from his chest,
	 * each faster than the last; whoever it runs over on the ground is struck (jump it). He casts at 10, 30 and 50.
	 */
	private static void rings(PaleMason m, Masonry f, Beat b) {
		ServerLevel level = f.level;
		String dim = ElderFx.dim(m);
		int elder = AttackFx.of(m);
		double headY = m.getY() + m.getBbHeight() - 2;
		if (b.between(0, 64) && b.every(3, 0)) {
			// the halo burning behind his head
			for (int i = 0; i < 6; i++) {
				double a = b.t * 0.3 + i * Math.PI / 3;
				WorldFx.particleIn(level, "minecraft:wax_on", m.getX() + Math.cos(a) * 3, headY + Math.sin(a) * 3, m.getZ(), 0.05, 0.05, 0.05, 0, 1);
			}
		}
		for (int k = 0; k < 3; k++) {
			if (b.at(10 + k * 20)) {
				double speed = 0.8 + 0.25 * k + (f.enraged ? 0.2 : 0);
				ElderStrikes.wave(m, m.getX(), m.getY(), m.getZ(), speed, ElderPower.reach(40), (float) Config.PALE_MASON_RING_DAMAGE, "minecraft:wax_on", null);
				WorldFx.soundIn(dim, "extremedimensions:elder.pale_mason.ring", "hostile", m.getX(), headY, m.getZ(), 4f, 0.9f + 0.1f * k);
				AttackFx.ring(level, elder, m.getX(), m.getY() + 0.1, m.getZ(), 4, 12);
				ElderFx.shake(m, m.getX(), m.getZ(), 28, 0.3f, 8);
			}
		}
		if (b.past(72)) {
			f.end(m, f.enraged ? 30 : 45);
		}
	}

	// ------------------------------------------------------------------ the Unfinished Spiral

	/** Where spiral stone {@code s} is at this beat: lifted off the floor, climbing and drawn in as it turns round him. */
	private static void coil(Masonry f, Bound s, double t) {
		double up = ease((t - 2) / 60) * (4 + s.rise * 10);
		double r = s.reach * (1 - 0.35 * ease((t - 20) / 64));
		double a = s.phase + t * (0.07 + 0.05 * ease((t - 20) / 64));
		s.x = f.boundX + Math.cos(a) * r;
		s.z = f.boundZ + Math.sin(a) * r;
		s.y = f.boundY + 0.5 + up;
	}

	/**
	 * The Unfinished Spiral: he rises a little, arms wide, and the floor round him comes up in stones that wind round him
	 * in a climbing spiral; everyone near is drawn in towards him (by the squall's rules: a wall at their back holds them,
	 * sneaking halves it, Anchored eases it), and close by him the stones grind. Then he throws his arms out and it bursts:
	 * the stones are flung far out and everyone near is struck and thrown off. The burst comes at 88 of his blow.
	 */
	private static void spiral(PaleMason m, Masonry f, Beat b) {
		ServerLevel level = f.level;
		String dim = ElderFx.dim(m);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int elder = AttackFx.of(m);
		if (b.at(2)) {
			dropBound(f.spiral);
			f.boundX = m.getX();
			f.boundY = m.getY();
			f.boundZ = m.getZ();
			BlockState[] kinds = {Blocks.END_STONE_BRICKS.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(),
					Blocks.END_STONE.defaultBlockState()};
			int n = 12;
			for (int i = 0; i < n; i++) {
				double size = 1.2 + rnd.nextDouble(1.4);
				Bound s = new Bound(null, null, Math.PI * 2 * i / n + rnd.nextDouble(0.3), size, rnd.nextDouble(), 6 + rnd.nextDouble(8));
				coil(f, s, 2);
				Display.BlockDisplay d = BlockFlight.spawn(level, f.boundX, f.boundY, f.boundZ, kinds[rnd.nextInt(kinds.length)],
						BlockFlight.pose(s.x - f.boundX - 0.5, s.y - f.boundY + 0.5 - 1, s.z - f.boundZ - 0.5, new Quaternionf(), (float) size));
				if (d != null) {
					f.spiral.add(new Bound(d, null, s.phase, s.size, s.rise, s.reach));
				}
			}
			WorldFx.soundIn(dim, "extremedimensions:elder.pale_mason.spiral", "hostile", f.boundX, f.boundY + 6, f.boundZ, 4.5f, 1f);
		}
		if (b.between(0, 20)) {
			m.motion.heave(m, 0.02);
		}
		if (b.between(2, 88) && b.every(2, 0)) {
			for (Bound s : f.spiral) {
				Bound ahead = new Bound(null, null, s.phase, s.size, s.rise, s.reach);
				coil(f, ahead, b.t + 2);
				s.x = ahead.x;
				s.y = ahead.y;
				s.z = ahead.z;
				Quaternionf q = new Quaternionf().rotateY((float) (b.t * 0.1 + s.phase)).rotateX((float) (b.t * 0.05 + s.rise));
				place(f, s, s.display, ahead.x, ahead.y, ahead.z, q, s.size, 2);
			}
			for (int i = 0; i < 4; i++) {
				double a = b.t * 0.2 + i * Math.PI / 2;
				double r = 3 + (b.t % 20) * 0.6;
				WorldFx.particleIn(level, "minecraft:reverse_portal", f.boundX + Math.cos(a) * r, f.boundY + 1 + (b.t % 20) * 0.4, f.boundZ + Math.sin(a) * r, 0.1,
						0.1, 0.1, 0.02, 2);
			}
		}
		if (b.between(20, 84)) {
			// Drawn in towards him, harder the nearer
			double reach = ElderPower.reach(36);
			for (ServerPlayer q : ElderFx.targets(m, reach)) {
				if (!PaleSquall.blown(q)) {
					continue;
				}
				double dx = m.getX() - q.getX(), dz = m.getZ() - q.getZ();
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d < 1e-3) {
					continue;
				}
				double pull = PaleSquall.push(q, dx / d, dz / d, Config.PALE_MASON_SPIRAL_PULL * (0.5 + 0.5 * Math.max(0, 1 - d / reach)));
				if (pull > 0) {
					PaleSquall.gust(q, dx / d * pull, dz / d * pull);
				}
				if (d < 5 && b.every(20, 20)) {
					q.hurtServer(level, ElderPower.great(level, m), (float) (Config.PALE_MASON_BURST_DAMAGE * 0.25));
				}
			}
			if (b.every(10, 20)) {
				ElderFx.shake(m, m.getX(), m.getZ(), 32, (float) (0.15 + 0.3 * ease((b.t - 20) / 64)), 10);
			}
		}
		int flight = 12;
		if (b.at(88)) {
			// It bursts
			m.motion.heave(m, -0.4);
			WorldFx.soundIn(dim, "extremedimensions:elder.pale_mason.burst", "hostile", f.boundX, f.boundY + 4, f.boundZ, 5f, 1f);
			WorldFx.particleIn(level, "minecraft:explosion_emitter", f.boundX, f.boundY + 2, f.boundZ, 1, 1, 1, 0, 1);
			AttackFx.burst(level, elder, f.boundX, f.boundY + 3, f.boundZ, 9);
			AttackFx.ring(level, elder, f.boundX, f.boundY + 0.1, f.boundZ, 16, 20);
			ElderFx.shake(m, f.boundX, f.boundZ, 48, 1.1f, 16);
			for (ServerPlayer q : ElderFx.targets(m, 16)) {
				double dx = q.getX() - m.getX(), dz = q.getZ() - m.getZ();
				double d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
				q.hurtServer(level, ElderPower.great(level, m), (float) (Config.PALE_MASON_BURST_DAMAGE * (1 - 0.5 * Math.min(1, d / 16))));
				q.setDeltaMovement(dx / d * 1.6, 0.65, dz / d * 1.6);
				WorldFx.moved(q);
			}
			for (Bound s : f.spiral) {
				double a = Math.atan2(s.z - f.boundZ, s.x - f.boundX) + rnd.nextDouble(-0.2, 0.2);
				double far = 18 + rnd.nextDouble(8);
				double tx = f.boundX + Math.cos(a) * far, tz = f.boundZ + Math.sin(a) * far;
				double ty = ElderFx.surface(level, tx, f.boundY + 8, tz, 10, 16);
				if (Double.isNaN(ty)) {
					ty = f.boundY;
				}
				s.flung = true;
				s.tx = tx;
				s.ty = ty;
				s.tz = tz;
				place(f, s, s.display, tx, ty + s.size / 2, tz, new Quaternionf().rotateXYZ(rnd.nextFloat() * 6, rnd.nextFloat() * 6, 0), s.size, flight);
				ElderStrikes.strike(m, tx, ty, tz, s.size, (float) (Config.PALE_MASON_BURST_DAMAGE * 0.5), flight, ElderStrikes.SLAM);
			}
		}
		if (b.at(88 + flight)) {
			for (Bound s : f.spiral) {
				WorldFx.particleIn(level, "minecraft:portal", s.tx, s.ty + 1, s.tz, 1, 0.6, 1, 0.8, 16);
			}
			dropBound(f.spiral);
		}
		if (b.past(112)) {
			dropBound(f.spiral);
			f.end(m, f.enraged ? 35 : 50);
		}
	}

	/** How many stones he turns in the air now, chained or in his spiral (for tests). */
	public static int bound(Entity e) {
		Masonry f = FIGHTS.get(e.getUUID());
		return f == null ? 0 : f.bound.size() + f.spiral.size();
	}

	/** How many of his pillars are yet to tear up (for tests). */
	public static int pillarsWaiting(Entity e) {
		Masonry f = FIGHTS.get(e.getUUID());
		return f == null ? 0 : f.pillars.size();
	}

	// ------------------------------------------------------------------ the Last Door

	/**
	 * The Last Door: he spreads his arms and rises, and a doorway of purpur and gold opens on the void before him. For
	 * most of his ultimate it draws everyone in reach towards it (braced against a wall it cannot; sneaking halves it;
	 * Anchored eases it); whoever reaches it is struck and thrown back. Then it slams, and his curse falls.
	 */
	private static void lastDoor(PaleMason m, Masonry f, ServerPlayer p, Beat b) {
		ServerLevel level = f.level;
		String dim = ElderFx.dim(m);
		int length = ElderUltimate.length();
		double k = b.t / length;
		if (b.between(0, 30)) {
			f.lift += 0.1;
			m.motion.heave(m, 0.1);
		}
		if (b.at(1)) {
			// The Door opens a little way before him, square to him
			double yaw = Math.toRadians(m.getYRot());
			double fx = -Math.sin(yaw), fz = Math.cos(yaw);
			f.doorDx = fx;
			f.doorDz = fz;
			f.doorX = m.getX() + fx * 9;
			f.doorZ = m.getZ() + fz * 9;
			double y = ElderFx.surface(level, f.doorX, m.getY() - f.lift + 4, f.doorZ, 6, 16);
			f.doorY = Double.isNaN(y) ? m.getY() - f.lift : y;
			openDoor(f);
			f.through.clear();
			WorldFx.soundIn(dim, "extremedimensions:elder.pale_mason.door", "hostile", f.doorX, f.doorY + 3, f.doorZ, 6f, 1f);
			AttackFx.ring(level, AttackFx.of(m), f.doorX, f.doorY + 0.1, f.doorZ, 5, (int) (length / Math.max(0.5, m.getTempo())));
		}
		if (b.every(2, 0) && !f.door.isEmpty()) {
			// the void pouring out of it
			double sx = -f.doorDz, sz = f.doorDx;
			for (int i = 0; i < 6; i++) {
				double u = ThreadLocalRandom.current().nextDouble(-1.4, 1.4), h = ThreadLocalRandom.current().nextDouble(0.2, 5.6);
				WorldFx.particleIn(level, i % 2 == 0 ? "minecraft:reverse_portal" : "minecraft:portal", f.doorX + sx * u, f.doorY + h, f.doorZ + sz * u, 0.05,
						0.05, 0.05, 0.6, 2);
			}
			ElderFx.shake(m, f.doorX, f.doorZ, 64, (float) (0.12 + 0.35 * k), 4);
		}
		if (b.between(24, length - 24) && !f.door.isEmpty()) {
			if (b.every(40, 24)) {
				WorldFx.soundIn(dim, "extremedimensions:elder.pale_mason.pull", "hostile", f.doorX, f.doorY + 3, f.doorZ, 5f, 1f);
			}
			double r = Math.max(16, Config.ELDER_ULTIMATE_RADIUS);
			for (ServerPlayer q : level.players()) {
				if (q.isSpectator() || !PaleSquall.blown(q)) {
					continue;
				}
				double dx = f.doorX - q.getX(), dz = f.doorZ - q.getZ();
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > r || d < 1e-3) {
					continue;
				}
				if (d < 2.2 && Math.abs(q.getY() - f.doorY) < 6) {
					// Through the Door's edge: struck and thrown back out of it
					if (f.through.add(q.getUUID())) {
						q.hurtServer(level, ElderPower.great(level, m), (float) Config.PALE_MASON_DOOR_DAMAGE);
						WorldFx.effect(q, MobEffects.DARKNESS, 5, 0);
						WorldFx.soundIn(dim, "minecraft:entity.enderman.scream", "hostile", q.getX(), q.getY() + 1, q.getZ(), 3f, 0.4f);
					}
					q.setDeltaMovement(-dx / d * 1.4, 0.55, -dz / d * 1.4);
					WorldFx.moved(q);
					continue;
				}
				double pull = PaleSquall.push(q, dx / d, dz / d, Config.PALE_MASON_DOOR_PULL * (0.6 + 0.4 * Math.min(1, 1 - d / r + 0.4)));
				if (pull > 0) {
					PaleSquall.gust(q, dx / d * pull, dz / d * pull);
				}
			}
		}
		if (b.every(20, 0)) {
			ElderFight.markPlayers(m, ElderFx.reachable(m, Math.max(0, Config.ELDER_ULTIMATE_RADIUS)), "minecraft:portal");
		}
		if (b.at(length - 12)) {
			// It slams: a blast of the void out of it, and he comes down
			m.motion.heave(m, -f.lift);
			f.lift = 0;
			WorldFx.soundIn(dim, "minecraft:entity.wither.break_block", "hostile", f.doorX, f.doorY + 2, f.doorZ, 6f, 0.4f);
			WorldFx.soundIn(dim, "minecraft:block.end_portal.spawn", "hostile", f.doorX, f.doorY + 2, f.doorZ, 6f, 0.6f);
			WorldFx.particleIn(level, "minecraft:explosion_emitter", f.doorX, f.doorY + 2, f.doorZ, 1, 1, 1, 0, 2);
			ElderStrikes.wave(m, f.doorX, f.doorY, f.doorZ, 1.1, 28, 8f, "minecraft:portal", ElderTerrain.Mark.SCORCH);
			AttackFx.burst(level, AttackFx.of(m), f.doorX, f.doorY + 2, f.doorZ, 8);
			for (Display.BlockDisplay d : f.door) {
				BlockFlight.drop(d);
			}
			f.door.clear();
			f.breakUltimate(m, BOSS);
		}
		if (b.past(length)) {
			f.closeUltimate(m);
		}
	}

	/** The Door's frame: two posts of purpur, a lintel of gold over them, the dark of the void between (displays only). */
	private static void openDoor(Masonry f) {
		ServerLevel level = f.level;
		double sx = -f.doorDz, sz = f.doorDx;
		BlockState post = Blocks.PURPUR_PILLAR.defaultBlockState(), lintel = Blocks.GOLD_BLOCK.defaultBlockState(), dark = Blocks.OBSIDIAN.defaultBlockState();
		for (int h = 0; h < 7; h++) {
			for (int side = -1; side <= 1; side += 2) {
				door(f, level, f.doorX + sx * 2 * side, f.doorY + h, f.doorZ + sz * 2 * side, h == 6 ? lintel : post, 1F);
			}
		}
		for (int u = -1; u <= 1; u++) {
			door(f, level, f.doorX + sx * u, f.doorY + 6, f.doorZ + sz * u, lintel, 1F);
		}
		// the dark of the doorway, thin
		for (int u = -1; u <= 1; u++) {
			for (int h = 0; h < 6; h++) {
				door(f, level, f.doorX + sx * u, f.doorY + h, f.doorZ + sz * u, dark, 0.98F);
			}
		}
	}

	private static void door(Masonry f, ServerLevel level, double x, double y, double z, BlockState state, float s) {
		// (a display stands one block over the place it draws its block in)
		Display.BlockDisplay d = BlockFlight.spawn(level, Math.floor(x), Math.floor(y) + 1, Math.floor(z), state,
				BlockFlight.pose(0, 0, 0, new Quaternionf(), s));
		if (d != null) {
			f.door.add(d);
		}
	}

	// ------------------------------------------------------------------ his end

	/** Slain: his spoils (the Homing Pearl among them) for his visitor, his page and his advancement for everyone there. */
	public static void onDefeat(PaleMason m, Entity killer) {
		Masonry f = FIGHTS.remove(m.getUUID());
		ElderArena.release(m);
		ElderStrikes.forget(m);
		ElderFx.silence(m, ElderFx.MUSIC_PALE_MASON);
		if (f == null) {
			return;
		}
		f.bar.removeAllPlayers();
		dropAll(f);
		ServerLevel level = (ServerLevel) m.level();
		ElderFx.burst(m, "minecraft:portal", 160);
		ElderFx.burst(m, "minecraft:reverse_portal", 100);
		AttackFx.burst(level, AttackFx.of(m), m.getX(), m.getY() + 2, m.getZ(), 9);
		ElderFx.cinema(m, 160, ModPackets.BossFxPayload.ULTIMATE_END, 0, 0, "");
		for (ItemStack s : Loot.roll(level, Loot.PALE_MASON, m.position())) {
			ElderFx.loot(m, f.target, s);
		}
		List<ServerPlayer> there = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(m) >= 64 * 64) {
				continue;
			}
			there.add(p);
			PlayerData d = DataStore.get(p.getUUID());
			Advancements.grant(p, Advancements.PALE_MASON_SLAIN);
			Journal.unlock(p, d, JournalEntry.PALE_MASON);
		}
		DataStore.markDirty();
		com.extremedimensions.compat.DiscordWebhook.elder(com.extremedimensions.compat.StatsExport.Elder.PALE_MASON, there);
	}

	/** Gone: back into the void, whole. */
	public static void fade(PaleMason m) {
		Masonry f = FIGHTS.remove(m.getUUID());
		ElderArena.release(m);
		if (f != null) {
			f.abandonUltimate(BOSS);
			f.bar.removeAllPlayers();
			dropAll(f);
			if (f.action == ElderBoss.ULTIMATE) {
				ElderFx.cinema(m, 160, ModPackets.BossFxPayload.ULTIMATE_END, 0, 0, "");
			}
		}
		ElderStrikes.forget(m);
		ElderFx.silence(m, ElderFx.MUSIC_PALE_MASON);
		ElderFx.burst(m, "minecraft:portal", 140);
		WorldFx.soundIn(ElderFx.dim(m), "minecraft:entity.enderman.teleport", "hostile", m.getX(), m.getY() + 8, m.getZ(), 4f, 0.4f);
		m.discard();
	}
}
