package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Elders' blows that are not a hand on a body. Strikes are foretold by the Elder's sigil on the ground and land a
 * moment later where it was (step out of it). Soul wisps and lantern bombs drift after their prey (the bombs burst where they
 * hit). Shockwaves run out over the ground in a ring (jump them). A beam sweeps the ground and carves it. Threads hold
 * a player to the spot they stood on, and a web of threads strung across the ground cuts whatever stands in it when it
 * is pulled taut.
 *
 * <p>How they look is drawn by the client from what {@link AttackFx} sends it.
 */
public final class ElderStrikes {
	private ElderStrikes() {}

	public static final int LANTERN = 1;
	public static final int ROOTS = 2;
	public static final int SLAM = 3;
	public static final int NEEDLE = 4;
	/** A great needle: a bigger strike that bursts into a crater. */
	public static final int SPEAR = 5;
	/** Soul fire from the sky (the Ferryman's ultimate): soul fire where it lands. */
	public static final int SOULFALL = 6;

	private static final class Strike {
		ServerLevel level;
		UUID source;
		double x, y, z, radius;
		float damage;
		int delay, age, kind, elder;
	}

	private static final class Wisp {
		ServerLevel level;
		UUID source, target;
		double x, y, z, vx, vy, vz;
		float damage, blast;
		int age, id, elder;
	}

	private static final class Tether {
		ServerLevel level;
		UUID source, player;
		double ax, ay, az;
		int side, age, duration;
	}

	private static final class Wave {
		ServerLevel level;
		UUID source;
		double x, y, z, r, speed, max;
		float damage;
		String particle;
		ElderTerrain.Mark mark;
		final Set<UUID> hit = new HashSet<>();
	}

	private static final class Beam {
		ServerLevel level;
		UUID source;
		double fx, fy, fz;
		double cx, cz, a0, a1, r0, r1;
		int age, duration;
		float damage;
		/** A great blow: through armour (see {@link ElderPower#great}). */
		boolean pierce;
		final Set<UUID> hit = new HashSet<>();
	}

	private static final class Web {
		ServerLevel level;
		UUID source;
		final List<double[]> lines = new ArrayList<>();
		int age, duration;
		float damage;
	}

	private static int nextWisp;
	private static final List<Strike> STRIKES = new ArrayList<>();
	private static final List<Wisp> WISPS = new ArrayList<>();
	private static final List<Tether> TETHERS = new ArrayList<>();
	private static final List<Wave> WAVES = new ArrayList<>();
	private static final List<Beam> BEAMS = new ArrayList<>();
	private static final List<Web> WEBS = new ArrayList<>();

	public static void clear() {
		STRIKES.clear();
		WISPS.clear();
		TETHERS.clear();
		WAVES.clear();
		BEAMS.clear();
		WEBS.clear();
	}

	/** Everything an Elder set going ends with it. */
	public static void forget(Entity boss) {
		UUID id = boss.getUUID();
		STRIKES.removeIf(s -> id.equals(s.source));
		WISPS.removeIf(w -> id.equals(w.source));
		TETHERS.removeIf(t -> id.equals(t.source));
		WAVES.removeIf(w -> id.equals(w.source));
		BEAMS.removeIf(b -> id.equals(b.source));
		WEBS.removeIf(w -> id.equals(w.source));
	}

	/** Whether anything an Elder set going is still under way (for tests). */
	public static int active() {
		return STRIKES.size() + WISPS.size() + TETHERS.size() + WAVES.size() + BEAMS.size() + WEBS.size();
	}

	/** How many beams this Elder is sweeping (for tests). */
	public static int beamsOf(Entity boss) {
		UUID id = boss.getUUID();
		return (int) BEAMS.stream().filter(b -> id.equals(b.source)).count();
	}

	static void strike(ElderBoss source, double x, double y, double z, double radius, float damage, int delay, int kind) {
		Strike s = new Strike();
		s.level = (ServerLevel) source.level();
		s.source = source.getUUID();
		s.x = x;
		s.y = y;
		s.z = z;
		s.radius = radius;
		s.damage = damage;
		s.delay = Math.max(4, delay);
		s.kind = kind;
		s.elder = AttackFx.of(source);
		STRIKES.add(s);
		// The warning: the Elder's sigil on the ground, tightening as the blow comes (and what falls on it)
		AttackFx.sigil(s.level, s.elder, kind, x, y, z, radius, s.delay);
	}

	static void wisp(ElderBoss source, double x, double y, double z, ServerPlayer target, float damage) {
		bomb(source, x, y, z, target, damage, 0);
	}

	/** A wisp that bursts into a real explosion of this power where it hits (0: a plain wisp). */
	static void bomb(ElderBoss source, double x, double y, double z, ServerPlayer target, float damage, float blast) {
		Wisp w = new Wisp();
		w.level = (ServerLevel) source.level();
		w.source = source.getUUID();
		w.target = target.getUUID();
		w.x = x;
		w.y = y;
		w.z = z;
		w.vy = 0.25;
		w.damage = damage;
		w.blast = blast;
		w.id = ++nextWisp;
		w.elder = AttackFx.of(source);
		WISPS.add(w);
	}

	static void tether(ElderBoss source, ServerPlayer p, int side, int duration) {
		TETHERS.removeIf(t -> p.getUUID().equals(t.player));
		Tether t = new Tether();
		t.level = (ServerLevel) source.level();
		t.source = source.getUUID();
		t.player = p.getUUID();
		t.ax = p.getX();
		t.ay = p.getY();
		t.az = p.getZ();
		t.side = side;
		t.duration = duration;
		TETHERS.add(t);
	}

	/** A shockwave running out over the ground from a point; anyone it passes on the ground is hit (jump it). */
	static void wave(ElderBoss source, double x, double y, double z, double speed, double max, float damage, String particle, ElderTerrain.Mark mark) {
		Wave w = new Wave();
		w.level = (ServerLevel) source.level();
		w.source = source.getUUID();
		w.x = x;
		w.y = y;
		w.z = z;
		w.r = 1.5;
		w.speed = speed;
		w.max = max;
		w.damage = damage;
		w.particle = particle;
		w.mark = mark;
		WAVES.add(w);
		// The client draws the ring running out at the same pace (a wave that does no harm is drawn faint)
		AttackFx.wave(w.level, AttackFx.of(source), damage <= 0, x, y, z, w.r, speed, max);
	}

	/**
	 * A beam from a point in the air down to the ground, sweeping round a centre from angle {@code a0} to {@code a1}
	 * (radians) and from radius {@code r0} to {@code r1} over {@code duration} ticks, carving the ground it touches.
	 */
	static void beam(ElderBoss source, double fx, double fy, double fz, double cx, double cz, double a0, double a1, double r0, double r1,
			int duration, float damage) {
		beam(source, fx, fy, fz, cx, cz, a0, a1, r0, r1, duration, damage, false);
	}

	/** A beam as above; {@code pierce} for a great blow, which goes through armour. */
	static void beam(ElderBoss source, double fx, double fy, double fz, double cx, double cz, double a0, double a1, double r0, double r1,
			int duration, float damage, boolean pierce) {
		Beam b = new Beam();
		b.pierce = pierce;
		b.level = (ServerLevel) source.level();
		b.source = source.getUUID();
		b.fx = fx;
		b.fy = fy;
		b.fz = fz;
		b.cx = cx;
		b.cz = cz;
		b.a0 = a0;
		b.a1 = a1;
		b.r0 = r0;
		b.r1 = r1;
		b.duration = duration;
		b.damage = damage;
		BEAMS.add(b);
	}

	/** A web of threads strung over the ground (each line x0, y0, z0, x1, y1, z1); it is pulled taut and cuts at the end. */
	static void web(ElderBoss source, List<double[]> lines, int duration, float damage) {
		Web w = new Web();
		w.level = (ServerLevel) source.level();
		w.source = source.getUUID();
		w.lines.addAll(lines);
		w.duration = duration;
		w.damage = damage;
		WEBS.add(w);
	}

	/** Whether a thread holds this player. */
	public static boolean tethered(ServerPlayer p) {
		for (Tether t : TETHERS) {
			if (p.getUUID().equals(t.player)) {
				return true;
			}
		}
		return false;
	}

	public static void tick(MinecraftServer s) {
		if (!STRIKES.isEmpty()) {
			strikes();
		}
		if (!WISPS.isEmpty()) {
			wisps(s);
		}
		if (!TETHERS.isEmpty()) {
			tethers(s);
		}
		if (!WAVES.isEmpty()) {
			waves();
		}
		if (!BEAMS.isEmpty()) {
			beams();
		}
		if (!WEBS.isEmpty()) {
			webs();
		}
	}

	private static DamageSource from(ServerLevel level, UUID source) {
		Entity e = level.getEntity(source);
		return e instanceof LivingEntity le ? level.damageSources().mobAttack(le) : level.damageSources().magic();
	}

	private static ElderBoss boss(ServerLevel level, UUID source) {
		return level.getEntity(source) instanceof ElderBoss b && b.isAlive() ? b : null;
	}

	private static boolean hittable(ServerPlayer p) {
		return p.isAlive() && !p.isSpectator() && !p.isCreative();
	}

	private static void strikes() {
		Iterator<Strike> it = STRIKES.iterator();
		List<Runnable> later = new ArrayList<>();
		while (it.hasNext()) {
			Strike s = it.next();
			s.age++;
			if (s.age >= s.delay) {
				it.remove();
				later.add(() -> land(s));
			}
		}
		later.forEach(Runnable::run);
	}

	private static void land(Strike s) {
		ElderBoss b = boss(s.level, s.source);
		String dim = s.level.dimension().identifier().toString();
		AttackFx.impact(s.level, s.elder, s.kind, s.x, s.y, s.z, s.radius);
		switch (s.kind) {
			case LANTERN -> {
				WorldFx.soundIn(dim, "minecraft:block.glass.break", "hostile", s.x, s.y, s.z, 2f, 0.6f);
				WorldFx.soundIn(dim, "minecraft:item.firecharge.use", "hostile", s.x, s.y, s.z, 2f, 0.7f);
				ElderTerrain.mark(s.level, s.x, s.y, s.z, s.radius * 0.7, 0.35, ElderTerrain.Mark.ROOT);
			}
			case ROOTS -> {
				WorldFx.soundIn(dim, "minecraft:block.rooted_dirt.break", "hostile", s.x, s.y, s.z, 2f, 0.5f);
				// Roots burst out of the ground for a while
				for (int i = 0; i < 4; i++) {
					double a = i * Math.PI / 2 + s.age;
					ElderTerrain.column(s.level, s.x + Math.cos(a) * s.radius * 0.5, s.y, s.z + Math.sin(a) * s.radius * 0.5, 2 + i % 2, Blocks.MANGROVE_ROOTS, 100);
				}
				ElderTerrain.mark(s.level, s.x, s.y, s.z, s.radius, 0.5, ElderTerrain.Mark.ROOT);
			}
			case SLAM -> {
				WorldFx.soundIn(dim, "minecraft:entity.generic.explode", "hostile", s.x, s.y, s.z, 1.5f, 0.6f);
				ElderTerrain.furrow(s.level, s.x, s.y - 1, s.z, s.radius * 0.6, 1, 0, 4);
				ElderTerrain.mark(s.level, s.x, s.y, s.z, s.radius, 0.6, ElderTerrain.Mark.SOUL);
			}
			case SPEAR -> {
				if (b != null) {
					ElderTerrain.blast(b, s.x, s.y, s.z, 2.6f, false);
				}
				ElderTerrain.mark(s.level, s.x, s.y, s.z, s.radius + 2, 0.5, ElderTerrain.Mark.SCORCH);
			}
			case SOULFALL -> {
				WorldFx.soundIn(dim, "minecraft:entity.blaze.shoot", "hostile", s.x, s.y, s.z, 2f, 0.5f);
				ElderTerrain.mark(s.level, s.x, s.y, s.z, s.radius, 0.7, ElderTerrain.Mark.SOUL);
			}
			default -> {
				WorldFx.soundIn(dim, "minecraft:entity.arrow.hit", "hostile", s.x, s.y, s.z, 2f, 0.5f);
				ElderTerrain.mark(s.level, s.x, s.y, s.z, s.radius * 0.6, 0.3, ElderTerrain.Mark.SCORCH);
			}
		}
		for (ServerPlayer p : s.level.players()) {
			double dx = p.getX() - s.x, dz = p.getZ() - s.z, dy = p.getY() - s.y;
			if (!hittable(p) || dx * dx + dz * dz > s.radius * s.radius || dy < -2 || dy > 4) {
				continue;
			}
			p.hurtServer(s.level, from(s.level, s.source), s.damage);
			switch (s.kind) {
				case LANTERN, SOULFALL -> p.setRemainingFireTicks(80);
				case ROOTS -> WorldFx.effect(p, MobEffects.SLOWNESS, 3, 3);
				case SLAM -> ElderFx.knock(p, s.x, s.z, 0.6, 0.95);
				case SPEAR -> ElderFx.knock(p, s.x, s.z, 1.1, 0.7);
				default -> {
				}
			}
		}
	}

	private static void wisps(MinecraftServer s) {
		Iterator<Wisp> it = WISPS.iterator();
		List<Runnable> later = new ArrayList<>();
		while (it.hasNext()) {
			Wisp w = it.next();
			w.age++;
			ServerPlayer p = s.getPlayerList().getPlayer(w.target);
			if (p == null || !p.isAlive() || p.level() != w.level || w.age > 200) {
				AttackFx.impact(w.level, w.elder, AttackFx.IMPACT_WISP, w.x, w.y, w.z, 1);
				it.remove();
				continue;
			}
			// It drifts after its prey, slower than a sprint, quicker than a walk (a bomb is heavier and quicker)
			double pull = w.blast > 0 ? 0.06 : 0.045;
			double dx = p.getX() - w.x, dy = p.getY() + 1.2 - w.y, dz = p.getZ() - w.z;
			double d = Math.max(0.01, Math.sqrt(dx * dx + dy * dy + dz * dz));
			w.vx = w.vx * 0.9 + dx / d * pull;
			w.vy = w.vy * 0.9 + dy / d * pull;
			w.vz = w.vz * 0.9 + dz / d * pull;
			w.x += w.vx;
			w.y += w.vy;
			w.z += w.vz;
			AttackFx.wisp(w.level, w.elder, w.blast > 0 ? AttackFx.WISP_BOMB : AttackFx.WISP_SOUL, w.id, w.x, w.y, w.z, w.vx, w.vy, w.vz);
			// A bomb also bursts on the ground it flies into
			boolean struck = d < 1.3;
			if (w.blast > 0 && !struck && w.age > 10) {
				BlockState at = w.level.getBlockState(BlockPos.containing(w.x, w.y, w.z));
				struck = !at.isAir() && !at.canBeReplaced();
			}
			if (struck) {
				it.remove();
				later.add(() -> {
					AttackFx.impact(w.level, w.elder, w.blast > 0 ? AttackFx.IMPACT_BOMB : AttackFx.IMPACT_WISP, w.x, w.y, w.z, 1.5);
					WorldFx.soundIn(w.level.dimension().identifier().toString(), "minecraft:entity.vex.hurt", "hostile", w.x, w.y, w.z, 1.5f, 0.5f);
					ElderBoss b = boss(w.level, w.source);
					if (w.blast > 0 && b != null) {
						ElderTerrain.blast(b, w.x, w.y, w.z, w.blast, true);
					} else if (hittable(p) && d < 1.6) {
						p.hurtServer(w.level, from(w.level, w.source), w.damage);
					}
				});
			}
		}
		later.forEach(Runnable::run);
	}

	private static void tethers(MinecraftServer s) {
		Iterator<Tether> it = TETHERS.iterator();
		while (it.hasNext()) {
			Tether t = it.next();
			t.age++;
			ServerPlayer p = s.getPlayerList().getPlayer(t.player);
			Entity boss = t.level.getEntity(t.source);
			if (p == null || !p.isAlive() || boss == null || !boss.isAlive() || p.level() != t.level || t.age > t.duration) {
				it.remove();
				continue;
			}
			// The thread runs from the Weaver's hand to the player
			double yaw = Math.toRadians(boss.getYRot());
			double hx = boss.getX() - Math.cos(yaw) * 9.5 * t.side, hz = boss.getZ() - Math.sin(yaw) * 9.5 * t.side;
			if (t.age % 2 == 0) {
				AttackFx.line(t.level, AttackFx.of(boss instanceof ElderBoss eb ? eb : null), AttackFx.LINE_THREAD, hx, boss.getY() + 13.5, hz,
						p.getX(), p.getY() + 1.2, p.getZ(), 2);
			}
			// It holds them near the spot it caught them: straying, they are yanked back
			double dx = t.ax - p.getX(), dz = t.az - p.getZ();
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > 4.5 && t.age % 4 == 0) {
				ElderFx.knock(p, t.ax, t.az, -Math.min(1.2, 0.25 + (d - 4.5) * 0.2), 0.2);
			}
			// And the Seam takes its time faster while it holds
			PlayerData data = DataStore.get(p.getUUID());
			if (data.inSeam && !p.isCreative()) {
				data.seamLeft = Math.max(1, data.seamLeft - 1.0 / 20.0);
			}
		}
	}

	private static void waves() {
		Iterator<Wave> it = WAVES.iterator();
		while (it.hasNext()) {
			Wave w = it.next();
			w.r += w.speed;
			if (w.r > w.max) {
				it.remove();
				continue;
			}
			if (w.mark != null) {
				for (int i = 0; i < 6; i++) {
					double a = Math.random() * Math.PI * 2;
					ElderTerrain.mark(w.level, w.x + Math.cos(a) * w.r, w.y, w.z + Math.sin(a) * w.r, 1.2, 0.6, w.mark);
				}
			}
			for (ServerPlayer p : w.level.players()) {
				if (!hittable(p) || w.hit.contains(p.getUUID())) {
					continue;
				}
				double dx = p.getX() - w.x, dz = p.getZ() - w.z;
				double d = Math.sqrt(dx * dx + dz * dz);
				// On the ground as it passes: hit; in the air over it: spared
				if (Math.abs(d - w.r) < 1.1 && p.getY() - w.y < 1.0 && p.getY() - w.y > -2.5) {
					w.hit.add(p.getUUID());
					p.hurtServer(w.level, from(w.level, w.source), w.damage);
					ElderFx.knock(p, w.x, w.z, 0.9, 0.6);
				}
			}
		}
	}

	private static void beams() {
		Iterator<Beam> it = BEAMS.iterator();
		while (it.hasNext()) {
			Beam b = it.next();
			b.age++;
			ElderBoss boss = boss(b.level, b.source);
			if (boss == null || b.age > b.duration) {
				it.remove();
				continue;
			}
			double k = b.age / (double) b.duration;
			double a = b.a0 + (b.a1 - b.a0) * k, r = b.r0 + (b.r1 - b.r0) * k;
			double gx = b.cx + Math.cos(a) * r, gz = b.cz + Math.sin(a) * r;
			double gy = ElderFx.surface(b.level, gx, b.fy - 4, gz, 6, 30);
			if (Double.isNaN(gy)) {
				gy = b.fy - 16;
			}
			String dim = b.level.dimension().identifier().toString();
			int elder = AttackFx.of(boss);
			AttackFx.line(b.level, elder, AttackFx.LINE_BEAM, b.fx, b.fy, b.fz, gx, gy + 0.2, gz, 2);
			if (b.age % 3 == 0) {
				AttackFx.impact(b.level, elder, AttackFx.IMPACT_SCORCH, gx, gy, gz, 1.4);
				WorldFx.soundIn(dim, "minecraft:block.fire.extinguish", "hostile", gx, gy, gz, 1.2f, 0.6f);
			}
			// It carves a burning trench
			ElderTerrain.furrow(b.level, gx, gy - 1, gz, 1.3, 1, 0, 6);
			ElderTerrain.mark(b.level, gx, gy, gz, 2.2, 0.8, ElderTerrain.Mark.SCORCH);
			for (ServerPlayer p : b.level.players()) {
				if (!hittable(p) || b.hit.contains(p.getUUID())) {
					continue;
				}
				if (segmentDistance(p.getX(), p.getY() + 1, p.getZ(), b.fx, b.fy, b.fz, gx, gy, gz) < 1.6) {
					b.hit.add(p.getUUID());
					p.hurtServer(b.level, b.pierce ? ElderPower.great(b.level, boss) : from(b.level, b.source), b.damage);
					p.setRemainingFireTicks(100);
					ElderFx.knock(p, gx, gz, 0.7, 0.5);
				}
			}
			if (b.age % 12 == 0) {
				b.hit.clear();
			}
		}
	}

	private static void webs() {
		Iterator<Web> it = WEBS.iterator();
		while (it.hasNext()) {
			Web w = it.next();
			w.age++;
			if (boss(w.level, w.source) == null) {
				it.remove();
				continue;
			}
			String dim = w.level.dimension().identifier().toString();
			boolean taut = w.age >= w.duration;
			if (w.age % 2 == 0 || taut) {
				ElderBoss owner = boss(w.level, w.source);
				for (double[] l : w.lines) {
					AttackFx.line(w.level, AttackFx.of(owner), taut ? AttackFx.LINE_THREAD_TAUT : AttackFx.LINE_THREAD, l[0], l[1], l[2], l[3], l[4], l[5],
							taut ? 12 : 2);
				}
			}
			for (ServerPlayer p : w.level.players()) {
				if (!hittable(p)) {
					continue;
				}
				for (double[] l : w.lines) {
					if (segmentDistance(p.getX(), p.getY() + 0.9, p.getZ(), l[0], l[1], l[2], l[3], l[4], l[5]) < (taut ? 1.6 : 0.8)) {
						if (taut) {
							p.hurtServer(w.level, from(w.level, w.source), w.damage);
							ElderFx.knock(p, (l[0] + l[3]) / 2, (l[2] + l[5]) / 2, 0.5, 0.5);
						} else if (w.age % 10 == 0) {
							p.hurtServer(w.level, from(w.level, w.source), w.damage * 0.25f);
							WorldFx.effect(p, MobEffects.SLOWNESS, 1, 2);
						}
						break;
					}
				}
			}
			if (taut) {
				it.remove();
				WorldFx.soundIn(dim, "minecraft:entity.player.attack.sweep", "hostile", w.lines.get(0)[0], w.lines.get(0)[1], w.lines.get(0)[2], 4f, 1.6f);
				for (double[] l : w.lines) {
					cut(w.level, l);
				}
			}
		}
	}

	/** Pulled taut, a thread cuts through the soft things standing in its line: leaves, plants, wool, glass. */
	private static void cut(ServerLevel level, double[] l) {
		if (!ElderTerrain.griefing(level)) {
			return;
		}
		double len = Math.sqrt((l[3] - l[0]) * (l[3] - l[0]) + (l[4] - l[1]) * (l[4] - l[1]) + (l[5] - l[2]) * (l[5] - l[2]));
		int steps = (int) Math.ceil(len * 2);
		for (int i = 0; i <= steps; i++) {
			double k = i / (double) steps;
			BlockPos pos = BlockPos.containing(l[0] + (l[3] - l[0]) * k, l[1] + (l[4] - l[1]) * k, l[2] + (l[5] - l[2]) * k);
			BlockState s = level.getBlockState(pos);
			if (s.is(BlockTags.LEAVES) || s.is(BlockTags.WOOL) || s.is(BlockTags.FLOWERS) || s.is(Blocks.GLASS) || s.is(Blocks.GLASS_PANE)
					|| (s.canBeReplaced() && !s.isAir() && !s.liquid())) {
				level.destroyBlock(pos, false, null, 512);
				AttackFx.impact(level, 2, AttackFx.IMPACT_CUT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.6);
			}
		}
	}

	/** Distance from a point to a segment. */
	static double segmentDistance(double px, double py, double pz, double ax, double ay, double az, double bx, double by, double bz) {
		double vx = bx - ax, vy = by - ay, vz = bz - az;
		double wx = px - ax, wy = py - ay, wz = pz - az;
		double len = vx * vx + vy * vy + vz * vz;
		double t = len <= 1e-9 ? 0 : Math.max(0, Math.min(1, (wx * vx + wy * vy + wz * vz) / len));
		double cx = ax + vx * t - px, cy = ay + vy * t - py, cz = az + vz * t - pz;
		return Math.sqrt(cx * cx + cy * cy + cz * cz);
	}
}
