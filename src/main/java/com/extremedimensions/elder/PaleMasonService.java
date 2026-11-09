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
				int a = rise > 4 ? f.pick(PaleMason.STRIKE, 4, PaleMason.LIFT, 3, PaleMason.GAZE, 2)
						: dist <= 8 ? f.pick(PaleMason.WALLS, 3, PaleMason.GAZE, 2, PaleMason.STRIKE, 2, PaleMason.LIFT, 1)
						: f.pick(PaleMason.STRIKE, 3, PaleMason.LIFT, 3, PaleMason.WALLS, 2, PaleMason.GAZE, 2);
				f.start(m, a);
			}
			return;
		}
		f.step();
		Beat b = f.beat;
		if (f.action != ElderBoss.ULTIMATE) {
			m.motion.brake(m, 0.08, 0.1);
			if (f.action != PaleMason.GAZE || b.t < 20) {
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
