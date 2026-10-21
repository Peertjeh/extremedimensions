package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.extremedimensions.config.Config;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.network.ModPackets.AttackFxPayload.Fx;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The look of the Elders' attacks, sent to the clients to draw: every effect of a tick is gathered and, at the end of
 * the tick, each player near gets those near them in one packet. The client draws them in fine detail (sigils on the
 * ground, impacts, shockwave rings, threads, chains and beams, wisps, bursts of debris); see the client's
 * {@code AttackFx}.
 */
public final class AttackFx {
	private AttackFx() {}

	/** A sigil on the ground foretelling a blow (variant: the strike's kind, see {@link ElderStrikes}); lasts {@code ticks}. */
	public static final int SIGIL = 1;
	/** A blow landing (variant: the strike's kind, or one of the impacts below). */
	public static final int IMPACT = 2;
	/** A shockwave running out over the ground (x2: its speed in blocks a tick; size: how far it runs; variant 1: a faint one). */
	public static final int WAVE = 3;
	/** Something drawn along a line from the point to the second point for {@code ticks} (variant: one of the lines below). */
	public static final int LINE = 4;
	/** A wisp or lantern bomb in flight (second point: its velocity; ticks: its id; variant: one of the wisps below). */
	public static final int WISP = 5;
	/** A great burst of an Elder's matter (an explosion, a death); size is its reach. */
	public static final int BURST = 6;
	/** A ring of the Elder's sigil laid round a place for {@code ticks} (a cage, a circle of thorns); size is its radius. */
	public static final int RING = 7;

	/** Impacts beyond the strikes' own kinds. */
	public static final int IMPACT_WISP = 20;
	public static final int IMPACT_BOMB = 21;
	public static final int IMPACT_SCORCH = 22;
	public static final int IMPACT_CUT = 23;
	public static final int IMPACT_BLAST = 24;
	public static final int IMPACT_CHARGE = 25;

	public static final int LINE_THREAD = 0;
	public static final int LINE_THREAD_TAUT = 1;
	public static final int LINE_CHAIN = 2;
	public static final int LINE_BEAM = 3;
	public static final int LINE_STREAM = 4;
	/** A path of the Elder's sigils on the ground along the line, kindling one after another (the way a charge will take). */
	public static final int LINE_PATH = 5;

	public static final int WISP_SOUL = 0;
	public static final int WISP_BOMB = 1;

	/** How far from a player the effects they are sent reach, in blocks. */
	private static final double RANGE = 128;
	/** At most this many effects go to one player in one tick (the rest are dropped). */
	private static final int CAP = 1500;

	private static final Map<ServerLevel, List<Fx>> PENDING = new HashMap<>();

	/** Whose effects these are, for the client's colours: 0 the Matriarch, 1 the Ferryman, 2 the Last Weaver. */
	static int of(ElderBoss b) {
		return b == null ? 0 : ElderFx.index(b);
	}

	private static void add(ServerLevel level, Fx fx) {
		PENDING.computeIfAbsent(level, k -> new ArrayList<>()).add(fx);
	}

	static void sigil(ServerLevel level, int elder, int kind, double x, double y, double z, double radius, int ticks) {
		add(level, new Fx(SIGIL, elder, kind, (float) x, (float) y, (float) z, 0, 0, 0, (float) radius, ticks));
	}

	static void impact(ServerLevel level, int elder, int kind, double x, double y, double z, double radius) {
		add(level, new Fx(IMPACT, elder, kind, (float) x, (float) y, (float) z, 0, 0, 0, (float) radius, 0));
	}

	/** A shockwave from {@code start} blocks out to {@code max}, growing {@code speed} blocks a tick. */
	static void wave(ServerLevel level, int elder, boolean faint, double x, double y, double z, double start, double speed, double max) {
		add(level, new Fx(WAVE, elder, faint ? 1 : 0, (float) x, (float) y, (float) z, (float) speed, (float) start, 0, (float) max, 0));
	}

	static void line(ServerLevel level, int elder, int kind, double x0, double y0, double z0, double x1, double y1, double z1, int ticks) {
		add(level, new Fx(LINE, elder, kind, (float) x0, (float) y0, (float) z0, (float) x1, (float) y1, (float) z1, 0, ticks));
	}

	static void wisp(ServerLevel level, int elder, int kind, int id, double x, double y, double z, double vx, double vy, double vz) {
		add(level, new Fx(WISP, elder, kind, (float) x, (float) y, (float) z, (float) vx, (float) vy, (float) vz, 0, id));
	}

	static void burst(ServerLevel level, int elder, double x, double y, double z, double size) {
		add(level, new Fx(BURST, elder, 0, (float) x, (float) y, (float) z, 0, 0, 0, (float) size, 0));
	}

	static void ring(ServerLevel level, int elder, double x, double y, double z, double radius, int ticks) {
		add(level, new Fx(RING, elder, 0, (float) x, (float) y, (float) z, 0, 0, 0, (float) radius, ticks));
	}

	/** A taut thread of light between two points (co-op: a curse carried from one player to another). */
	public static void thread(net.minecraft.world.level.Level level, double x0, double y0, double z0, double x1, double y1, double z1, int ticks) {
		if (level instanceof ServerLevel sl) {
			line(sl, 2, LINE_THREAD_TAUT, x0, y0, z0, x1, y1, z1, ticks);
		}
	}

	/** Effects gathered so far this tick (for tests). */
	public static int pending() {
		int n = 0;
		for (List<Fx> l : PENDING.values()) {
			n += l.size();
		}
		return n;
	}

	/** Sends the tick's effects: each player gets those within reach of them (a line counts by its nearer end). */
	public static void flush(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		float detail = (float) Math.max(0.25, Math.min(2, Config.ELDER_ATTACK_FX_DETAIL));
		for (Map.Entry<ServerLevel, List<Fx>> e : PENDING.entrySet()) {
			List<Fx> all = e.getValue();
			for (ServerPlayer p : e.getKey().players()) {
				List<Fx> mine = new ArrayList<>();
				for (Fx f : all) {
					if (near(p, f) && mine.size() < CAP) {
						mine.add(f);
					}
				}
				if (!mine.isEmpty()) {
					ServerPlayNetworking.send(p, new ModPackets.AttackFxPayload(detail, mine));
				}
			}
		}
		PENDING.clear();
	}

	private static boolean near(ServerPlayer p, Fx f) {
		double r2 = RANGE * RANGE;
		if (p.distanceToSqr(f.x(), f.y(), f.z()) <= r2) {
			return true;
		}
		return f.kind() == LINE && p.distanceToSqr(f.x2(), f.y2(), f.z2()) <= r2;
	}

	/** Forgets what was gathered (a server stopping). */
	public static void clear() {
		PENDING.clear();
	}
}
