package com.extremedimensions.bench;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;

/**
 * The benchmark: a mod of its own, built apart from Extreme Dimensions, that takes a dedicated server through the same
 * play whether Extreme Dimensions is installed or not, and writes what it measured (tools/bench/run.py runs the server
 * both ways and sets the two side by side). It does nothing unless {@code -Dextremedimensions.bench.out} names the file
 * to write.
 *
 * <p>The play, in phases: the server alone; players standing about near spawn; the players travelling outwards at a
 * sprint (new land generated all the way); and, only where the mod is installed, the players in the Seam (each taken
 * there by the mod's own {@code /tether seam}, as through a rift). The players are joined the way the game tests join
 * theirs (a real server-side connection to nowhere), in survival, unharmed by anything. For each phase: the server tick
 * by the clock (what the game calls MSPT) and in the server thread's CPU time, how busy the server thread was in all
 * (with the work it does between ticks), the CPU time of every thread (world generation included), garbage collection,
 * the memory still in use after it, the entities, the loaded chunks, where the players were, and where the server
 * thread stood in the slowest ticks (a watcher looks whenever a tick runs past {@link #SLOW_NANOS}).
 */
public final class Bench implements DedicatedServerModInitializer {
	static final Logger LOG = LoggerFactory.getLogger("extremedimensions-bench");

	/** The phases, in order, and how many ticks each lasts. */
	private static final String[] PHASES = {"idle", "players", "exploring", "seam"};
	private static final int[] TICKS = {400, 600, 1000, 800};
	/** How far a travelling player goes in a tick (5.6 blocks a second, a sprint). */
	private static final double SPEED = 0.28;
	/** A tick this long has the watcher note where the server thread stands. */
	private static final long SLOW_NANOS = 100_000_000L;
	/** The slowest ticks kept per phase. */
	private static final int SLOW_KEPT = 4;
	/** Where the players stand: this far from the middle of the world, each in a direction of their own, this high up. */
	private static final double SPREAD = 32, HEIGHT = 120;

	private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

	private Path out;
	private boolean warmup, mod;
	private int count;
	private final Map<String, Object> result = new LinkedHashMap<>();
	private final List<Map<String, Object>> done = new ArrayList<>();
	private final List<ServerPlayer> players = new ArrayList<>();
	private final List<EmbeddedChannel> channels = new ArrayList<>();

	/** The phase under way (-1 before the first, PHASES.length once all are done) and its tick. */
	private volatile int phase = -1;
	private int tick = 0;
	/** This tick's start, by the clock and in the server thread's CPU time (0 outside a measured tick). */
	private volatile long tickStart = 0;
	private long tickCpu = 0;
	/** What the watcher saw of the tick that began at {@link #seenAt}: where the server thread stood. */
	private volatile long seenAt = 0;
	private volatile String[] seen = null;
	private final List<Map<String, Object>> slow = new ArrayList<>();
	private volatile Thread serverThread;
	private long[] wall = new long[0], cpu = new long[0];
	/** The phase's start: clock, server thread CPU, every thread's CPU, garbage collection (ms, count). */
	private long phaseStart, phaseCpu, phaseProcessCpu, phaseGcMs, phaseGcCount;

	@Override
	public void onInitializeServer() {
		String file = System.getProperty("extremedimensions.bench.out");
		if (file == null) {
			return;
		}
		out = Path.of(file);
		warmup = Boolean.getBoolean("extremedimensions.bench.warmup");
		count = Integer.getInteger("extremedimensions.bench.players", 4);
		ServerLifecycleEvents.SERVER_STARTED.register(this::started);
		ServerTickEvents.START_SERVER_TICK.register(this::before);
		ServerTickEvents.END_SERVER_TICK.register(this::after);
	}

	private void started(MinecraftServer server) {
		mod = FabricLoader.getInstance().isModLoaded("extremedimensions");
		result.put("mod", mod);
		result.put("mod_version", FabricLoader.getInstance().getModContainer("extremedimensions")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null));
		result.put("players", count);
		// From the JVM's start: Fabric, every mod's setup, the data packs, and the world made and its spawn prepared
		result.put("startup_ms", ManagementFactory.getRuntimeMXBean().getUptime());
		result.put("heap_after_start_mb", heapAfterGc());
		result.put("phases", done);
		LOG.info("BENCH started {} the mod in {} ms", mod ? "with" : "without", result.get("startup_ms"));
		if (warmup) {
			finish(server);
			return;
		}
		serverThread = Thread.currentThread();
		Thread watcher = new Thread(this::watch, "extremedimensions-bench watcher");
		watcher.setDaemon(true);
		watcher.start();
		begin(server, 0);
	}

	/** Looks in on the server thread every few milliseconds; in a tick running long, notes where it stands. */
	private void watch() {
		while (phase < PHASES.length) {
			long at = tickStart;
			Thread t = serverThread;
			if (at != 0 && t != null && at != seenAt && System.nanoTime() - at > SLOW_NANOS) {
				seen = where(t.getStackTrace());
				seenAt = at;
			}
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	/** The top of a stack, down to the first few frames of Extreme Dimensions' own; and the first of those, if any. */
	private static String[] where(StackTraceElement[] stack) {
		StringBuilder at = new StringBuilder();
		String first = null;
		int ours = 0;
		for (int i = 0; i < stack.length && i < 40 && ours < 3; i++) {
			StackTraceElement f = stack[i];
			String frame = f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1) + "." + f.getMethodName() + ":" + f.getLineNumber();
			if (f.getClassName().startsWith("com.extremedimensions.") && !f.getClassName().startsWith("com.extremedimensions.bench.")) {
				ours++;
				first = first == null ? frame : first;
			}
			at.append(i == 0 ? "" : " < ").append(frame);
		}
		return new String[] {at.toString(), first};
	}

	private void before(MinecraftServer server) {
		if (phase >= 0 && phase < PHASES.length) {
			tickStart = System.nanoTime();
			tickCpu = THREADS.getCurrentThreadCpuTime();
		}
	}

	private void after(MinecraftServer server) {
		if (tickStart == 0) {
			return;
		}
		long began = tickStart;
		wall[tick] = System.nanoTime() - began;
		cpu[tick] = THREADS.getCurrentThreadCpuTime() - tickCpu;
		tickStart = 0;
		if (seenAt == began && seen != null) {
			Map<String, Object> s = new LinkedHashMap<>();
			s.put("ms", round(wall[tick] / 1e6));
			s.put("tick", tick);
			s.put("mod_frame", seen[1]);
			s.put("at", seen[0]);
			slow.add(s);
			slow.sort((a, b) -> Double.compare((double) b.get("ms"), (double) a.get("ms")));
			while (slow.size() > SLOW_KEPT) {
				slow.remove(slow.size() - 1);
			}
		}
		tick++;
		// What the server sends the players goes nowhere: let it go, or it piles up in memory
		for (EmbeddedChannel c : channels) {
			c.runPendingTasks();
			c.releaseOutbound();
		}
		if (PHASES[phase].equals("exploring")) {
			for (int i = 0; i < players.size(); i++) {
				ServerPlayer p = players.get(i);
				double a = angle(i);
				move(p, (ServerLevel) p.level(), p.getX() + Math.cos(a) * SPEED, HEIGHT, p.getZ() + Math.sin(a) * SPEED);
			}
		}
		if (tick < TICKS[phase]) {
			return;
		}
		done.add(stats(server));
		LOG.info("BENCH phase {} done: {}", PHASES[phase], done.get(done.size() - 1));
		// (the Seam is the mod's own world: without the mod there is no such phase)
		if (phase + 1 < PHASES.length && (mod || !PHASES[phase + 1].equals("seam"))) {
			begin(server, phase + 1);
		} else {
			phase = PHASES.length;
			finish(server);
		}
	}

	/** Sets the next phase going: what it needs in place, then its clocks. */
	private void begin(MinecraftServer server, int next) {
		phase = next;
		tick = 0;
		slow.clear();
		wall = new long[TICKS[next]];
		cpu = new long[TICKS[next]];
		switch (PHASES[next]) {
			case "players" -> {
				ServerLevel level = server.overworld();
				for (int i = 0; i < count; i++) {
					players.add(join(server, level, i));
				}
				for (int i = 0; i < count; i++) {
					move(players.get(i), level, Math.cos(angle(i)) * SPREAD, HEIGHT, Math.sin(angle(i)) * SPREAD);
				}
			}
			case "seam" -> {
				// The mod's own way in, for an operator: a rift opens where the player is and takes them through
				for (ServerPlayer p : players) {
					server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
							"execute as " + p.getStringUUID() + " run tether seam");
				}
			}
			default -> {
			}
		}
		phaseStart = System.nanoTime();
		phaseCpu = THREADS.getCurrentThreadCpuTime();
		phaseProcessCpu = processCpu();
		phaseGcMs = gc(true);
		phaseGcCount = gc(false);
	}

	/** A player joined the way the game tests join theirs: a real connection on the server's side, to nowhere. */
	private ServerPlayer join(MinecraftServer server, ServerLevel level, int i) {
		GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("bench-" + i).getBytes(StandardCharsets.UTF_8)), "bench_" + i);
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
		ServerPlayer p = new ServerPlayer(server, level, profile, cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		channels.add(new EmbeddedChannel(connection));
		server.getPlayerList().placeNewPlayer(connection, p, cookie);
		p.setGameMode(GameType.SURVIVAL);
		// Nothing may kill them (a fall, a mob, lava), or the phases after would have fewer players than the one before
		p.getAbilities().invulnerable = true;
		p.onUpdateAbilities();
		return p;
	}

	private static void move(ServerPlayer p, ServerLevel level, double x, double y, double z) {
		p.teleportTo(level, x, y, z, Set.of(), p.getYRot(), p.getXRot(), false);
		// (the chunks round them follow, as they do when a player's client says it has moved)
		((ServerLevel) p.level()).getChunkSource().move(p);
	}

	private double angle(int i) {
		return Math.PI * 2 * i / Math.max(1, count);
	}

	/** The phase just over, measured. */
	private Map<String, Object> stats(MinecraftServer server) {
		double seconds = (System.nanoTime() - phaseStart) / 1e9;
		long threadCpu = THREADS.getCurrentThreadCpuTime() - phaseCpu;
		long processCpu = processCpu() - phaseProcessCpu;
		long gcMs = gc(true) - phaseGcMs, gcCount = gc(false) - phaseGcCount;
		Map<String, Object> s = new LinkedHashMap<>();
		s.put("name", PHASES[phase]);
		s.put("ticks", TICKS[phase]);
		s.put("seconds", round(seconds));
		s.put("tps", round(TICKS[phase] / seconds));
		long[] w = wall.clone(), c = cpu.clone();
		Arrays.sort(w);
		Arrays.sort(c);
		s.put("mspt_avg", round(Arrays.stream(w).average().orElse(0) / 1e6));
		s.put("mspt_p50", round(w[w.length / 2] / 1e6));
		s.put("mspt_p95", round(w[(int) (w.length * 0.95)] / 1e6));
		s.put("mspt_p99", round(w[(int) (w.length * 0.99)] / 1e6));
		s.put("mspt_max", round(w[w.length - 1] / 1e6));
		s.put("tick_cpu_avg", round(Arrays.stream(c).average().orElse(0) / 1e6));
		s.put("tick_cpu_p95", round(c[(int) (c.length * 0.95)] / 1e6));
		// The server thread's work between ticks (chunks handed over from the workers, for one) counts here too
		s.put("server_thread_busy", round(threadCpu / 1e9 / seconds));
		s.put("process_cpu_s", processCpu < 0 ? null : round(processCpu / 1e9));
		s.put("gc_ms", gcMs);
		s.put("gc_count", gcCount);
		int entities = 0, chunks = 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity e : level.getAllEntities()) {
				entities++;
			}
			chunks += level.getChunkSource().getLoadedChunksCount();
		}
		s.put("entities", entities);
		s.put("loaded_chunks", chunks);
		s.put("online", server.getPlayerList().getPlayerCount());
		Map<String, Integer> where = new TreeMap<>();
		for (ServerPlayer p : players) {
			where.merge(p.level().dimension().identifier().toString(), 1, Integer::sum);
		}
		s.put("players_in", where);
		s.put("slowest", new ArrayList<>(slow));
		s.put("heap_after_mb", heapAfterGc());
		return s;
	}

	private void finish(MinecraftServer server) {
		try {
			Files.writeString(out, new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(result), StandardCharsets.UTF_8);
			LOG.info("BENCH written to {}", out.toAbsolutePath());
		} catch (Exception e) {
			LOG.error("BENCH could not write {}", out, e);
		}
		server.halt(false);
	}

	/** The memory still in use once the garbage is cleared away, in MB. */
	private static double heapAfterGc() {
		System.gc();
		System.gc();
		return round(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / 1048576.0);
	}

	/** The CPU time of every thread of the server, in nanoseconds (-1 if the JVM cannot tell). */
	private static long processCpu() {
		return ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os ? os.getProcessCpuTime() : -1;
	}

	/** Garbage collection so far, over every collector: milliseconds, or collections. */
	private static long gc(boolean ms) {
		long sum = 0;
		for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
			sum += Math.max(0, ms ? b.getCollectionTime() : b.getCollectionCount());
		}
		return sum;
	}

	private static double round(double v) {
		return Math.round(v * 1000) / 1000.0;
	}
}
