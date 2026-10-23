package net.battlehub.bot;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.BotStyle;
import net.battlehub.core.ai.V3;
import net.battlehub.core.ml.CombatSpec;
import net.battlehub.core.ml.CombatState;
import net.battlehub.core.ml.FightLog;
import net.battlehub.core.ml.Gear;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.util.Ids;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Records every fight (core.ml.FightLog) to {@code config/battlehub/fights}, a gzip file per match, so the model can
 * learn from it (ml/train/fights.py, ml/train/imitate.py): every decision (two ticks), for every fighter in play with a
 * target, people and bots alike, the state it was in (FightState, what the model would see) and what it did over the
 * decision (keys, jump, sprint, a swing, the turn, what it held, the right button); for a bot also what its model
 * chose and how the tick went against it; the hits; and the result. Writing happens on a thread of its own.
 * {@code /battlehub fights export} zips the finished files into one to hand over.
 */
public final class FightRecorder {
	private FightRecorder() {}

	static final String DIR = "fights";
	/** People's target: someone they traded hits with this recently (ticks), else whom they see. */
	private static final int ENGAGED = 60;
	private static final double SEE = 24, CLOSE = 8;

	/** What a fighter was in at the start of a decision, waiting for what it did over it. */
	private static final class Pending {
		int tick, target;
		CombatState state;
		float yaw0, pitch0;
		boolean jumped;
		int[] model;
		String reason;
	}

	/** One match being recorded. */
	private static final class Rec {
		final Match match;
		final Path file;
		final List<UUID> fighters = new ArrayList<>();
		final Map<UUID, Integer> index = new HashMap<>();
		final Map<UUID, Pending> pending = new HashMap<>();
		final Sink sink = new Sink();
		int tick;
		int rows;

		Rec(Match match, Path file) {
			this.match = match;
			this.file = file;
		}
	}

	/** The open file of a record; only the writing thread touches it. */
	private static final class Sink {
		Writer w;
		boolean broken;
	}

	private static final Map<Integer, Rec> RECS = new LinkedHashMap<>();
	/** The files still being written (the writing thread reads this too). */
	private static final java.util.Set<Path> OPEN = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "BattleHub fight logs");
		t.setDaemon(true);
		return t;
	});
	private static long now;
	private static final Map<UUID, Long> SWUNG = new HashMap<>();
	private static final Map<UUID, Vec3> POS = new HashMap<>();
	private static final Map<UUID, Vec3> VEL = new HashMap<>();
	private static final Map<UUID, Boolean> GROUND = new HashMap<>();
	/** Who traded hits with whom, and when: player -> (other, tick). */
	private static final Map<UUID, Object[]> ENGAGED_WITH = new HashMap<>();
	private static volatile Path lastClosed;
	private static volatile int lastClosedMatch = -1;
	private static boolean warned;

	private static net.battlehub.config.HubConfig.Fights cfg() {
		return ConfigManager.config().fights;
	}

	static Path dir() {
		return ConfigManager.dir().resolve(DIR);
	}

	// ------------------------------------------------------------------ events

	/** An arm swing of anyone (HubEvents). */
	public static void swung(ServerPlayer p) {
		SWUNG.put(p.getUUID(), now);
	}

	/** A hit between players (HubEvents): a line in the fight's log, and the two count as fighting each other. */
	public static void hit(ServerPlayer victim, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == victim) return;
		ENGAGED_WITH.put(victim.getUUID(), new Object[] {attacker.getUUID(), now});
		ENGAGED_WITH.put(attacker.getUUID(), new Object[] {victim.getUUID(), now});
		for (Rec r : RECS.values()) {
			Integer v = r.index.get(victim.getUUID());
			if (v == null) continue;
			write(r, FightLog.hit(r.tick, r.index.getOrDefault(attacker.getUUID(), -1), v, amount));
		}
	}

	/** A match is over with its result (Results): the last line, and the file closes. */
	public static void end(Match m) {
		Rec r = RECS.remove(m.id);
		if (r == null) return;
		List<Integer> winners = new ArrayList<>();
		for (int i = 0; i < r.fighters.size(); i++) {
			if (m.outcome(r.fighters.get(i)) == net.battlehub.core.Outcome.WIN) winners.add(i);
		}
		close(r, winners, "done");
	}

	// ------------------------------------------------------------------ every tick

	public static void tick(MinecraftServer server) {
		now++;
		// where everyone in a match is and how fast they go (people's movement is the client's: from their positions)
		for (Match m : Matches.all()) {
			for (UUID id : m.participants()) {
				ServerPlayer p = m.player(id);
				if (p == null) continue;
				Vec3 pos = p.position(), last = POS.put(id, pos);
				VEL.put(id, last == null || last.distanceToSqr(pos) > 16 ? Vec3.ZERO : pos.subtract(last));
			}
		}
		for (Rec r : List.copyOf(RECS.values())) {
			if (Matches.get(r.match.id) != r.match || r.match.phase() != Match.Phase.RUNNING || !cfg().record) {
				RECS.remove(r.match.id);
				close(r, List.of(), "stopped");
			}
		}
		if (!cfg().record) return;
		for (Match m : Matches.all()) {
			if (m.phase() == Match.Phase.RUNNING && fight(m) && !RECS.containsKey(m.id)) open(m);
		}
		for (Rec r : RECS.values()) {
			step(r);
			r.tick++;
		}
		if (now % 200 == 0) {
			SWUNG.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
			POS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
			VEL.keySet().retainAll(POS.keySet());
			GROUND.keySet().retainAll(POS.keySet());
			ENGAGED_WITH.keySet().retainAll(POS.keySet());
		}
	}

	/** Matches with a fight in them (melee and bow games; not parkour, spleef, TNT run or prop hunt). */
	static boolean fight(Match m) {
		BotStyle s = m.botStyle();
		return s == BotStyle.MELEE || s == BotStyle.RANGED;
	}

	private static void open(Match m) {
		CombatSpec spec = CombatModels.V2.ready() ? CombatModels.V2.spec() : null;
		if (spec == null) {
			if (!warned) BattleHub.LOGGER.warn("Fight logs are off: spec v2 could not be loaded");
			warned = true;
			return;
		}
		String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
		Rec r = new Rec(m, dir().resolve(stamp + "-" + m.mode.id() + "-" + m.id + ".jsonl.gz"));
		List<FightLog.Fighter> fs = new ArrayList<>();
		for (UUID id : m.participants()) {
			ServerPlayer p = m.player(id);
			if (p == null) continue;
			r.index.put(id, r.fighters.size());
			r.fighters.add(id);
		}
		for (int i = 0; i < r.fighters.size(); i++) {
			UUID id = r.fighters.get(i);
			int team = i;
			for (int j = 0; j < i; j++) {
				if (!m.isEnemy(r.fighters.get(j), id)) {
					team = j;
					break;
				}
			}
			ServerPlayer p = m.player(id);
			String name = cfg().names && p != null ? p.getGameProfile().name() : null;
			String model = LearnedFight.active(m) ? "v2" : LearnedMelee.enabledFor(m) ? "v1" : "none";
			fs.add(p instanceof BotPlayer b ? new FightLog.Fighter("bot", b.difficulty.id(), model, team, name)
					: new FightLog.Fighter("human", null, null, team, name));
		}
		String map = m.arena() == null ? "" : m.arena().theme().id();
		String header = FightLog.header(spec, version(), m.id, m.mode.id(), map, m.botWeapon().name().toLowerCase(java.util.Locale.ROOT),
				Instant.now().toString(), fs);
		RECS.put(m.id, r);
		OPEN.add(r.file);
		Sink sink = r.sink;
		Path file = r.file;
		IO.submit(() -> {
			try {
				Files.createDirectories(file.getParent());
				sink.w = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8));
				sink.w.write(header);
				sink.w.write('\n');
			} catch (IOException e) {
				sink.broken = true;
				BattleHub.LOGGER.warn("Could not write {}: {}", file, e.toString());
			}
		});
	}

	/** One tick of a recorded match: jumps are noted; every second tick each fighter's last decision is written and a new one begins. */
	private static void step(Rec r) {
		Match m = r.match;
		CombatSpec spec = CombatModels.V2.spec();
		for (int i = 0; i < r.fighters.size(); i++) {
			UUID id = r.fighters.get(i);
			ServerPlayer p = m.player(id);
			if (p == null || !m.inPlay(id) || p.isSpectator()) {
				r.pending.remove(id);
				continue;
			}
			Pending last = r.pending.get(id);
			boolean ground = p.onGround();
			Boolean was = GROUND.put(id, ground);
			if (last != null && Boolean.TRUE.equals(was) && !ground && VEL.getOrDefault(id, Vec3.ZERO).y > 0.2) last.jumped = true;
			if (r.tick % spec.decisionTicks != 0) continue;
			if (last != null) {
				write(r, FightLog.row(last.tick, i, last.target, last.state, did(p, last), last.model, last.reason, null));
				r.rows++;
				r.pending.remove(id);
			}
			ServerPlayer opp = target(m, p);
			Integer o = opp == null ? null : r.index.get(opp.getUUID());
			if (o == null) continue;
			Pending next = new Pending();
			next.tick = r.tick;
			next.target = o;
			next.state = state(spec, m, p, opp);
			next.yaw0 = p.getYRot();
			next.pitch0 = p.getXRot();
			if (p instanceof BotPlayer b) {
				next.model = b.brain.modelChoice();
				String why = b.brain.accounted();
				next.reason = why.isEmpty() ? null : why;
			}
			r.pending.put(id, next);
		}
	}

	/** What the fighter did since its decision began: keys, jump, sprint, a swing, the turn, its hand, the right button. */
	private static double[] did(ServerPlayer p, Pending d) {
		int forward, strafe;
		boolean jump = d.jumped;
		boolean[] keys = p instanceof BotPlayer ? null : keys(p);
		if (keys != null) {
			forward = (keys[0] ? 1 : 0) - (keys[1] ? 1 : 0);
			strafe = (keys[2] ? 1 : 0) - (keys[3] ? 1 : 0);
			jump |= keys[4];
		} else if (p instanceof BotPlayer) {
			forward = (int) Math.signum(p.zza);
			strafe = (int) Math.signum(p.xxa);
		} else {
			// no keys from the client: the direction it moves in, relative to where it looks
			Vec3 v = VEL.getOrDefault(p.getUUID(), Vec3.ZERO);
			double r = Math.toRadians(p.getYRot());
			double f = -Math.sin(r) * v.x + Math.cos(r) * v.z, l = Math.cos(r) * v.x + Math.sin(r) * v.z;
			forward = f > 0.03 ? 1 : f < -0.03 ? -1 : 0;
			strafe = l > 0.03 ? 1 : l < -0.03 ? -1 : 0;
		}
		boolean swung = SWUNG.getOrDefault(p.getUUID(), Long.MIN_VALUE) > now - CombatModels.V2.spec().decisionTicks;
		int hand = Math.max(0, CombatModels.V2.spec().hands.indexOf(LearnedFight.handOf(p.getMainHandItem())));
		return FightLog.action(forward, strafe, jump, p.isSprinting(), swung, FightLog.turnLeft(d.yaw0, p.getYRot()), p.getXRot() - d.pitch0, hand,
				p.isUsingItem());
	}

	/** The state of {@code p} against {@code opp} now, from what the server knows of both. */
	private static CombatState state(CombatSpec spec, Match m, ServerPlayer p, ServerPlayer opp) {
		Vec3 vel = p instanceof BotPlayer ? p.getDeltaMovement() : VEL.getOrDefault(p.getUUID(), Vec3.ZERO);
		double charge;
		if (p instanceof BotPlayer) {
			charge = p.getAttackStrengthScale(0.5f);
		} else {
			// the client keeps a person's charge: from the ticks since its last swing and its weapon's cooldown
			double cd = Math.max(1, Gear.melee(Ids.key(p.getMainHandItem().getItem())).cooldown());
			charge = Math.max(0, Math.min(1, (sinceSwing(p.getUUID()) + 0.5) / cd));
		}
		boolean visible = FightState.sees(p, new V3(p.getX(), p.getEyeY(), p.getZ()), opp);
		Vec3 oppVel = opp instanceof BotPlayer ? opp.getDeltaMovement() : VEL.getOrDefault(opp.getUUID(), Vec3.ZERO);
		FightState.Seen seen = FightState.Seen.of(opp, opp.position(), visible ? oppVel : Vec3.ZERO, visible ? (int) Math.min(100, sinceSwing(opp.getUUID())) : 100,
				visible);
		ItemStack held = opp.getMainHandItem();
		ItemStack weapon = Gear.isWeapon(Ids.key(held.getItem())) ? held : FightState.weapon(opp, m.botWeapon());
		return FightState.build(spec, m, p, vel, charge, opp, seen, weapon);
	}

	private static long sinceSwing(UUID id) {
		Long t = SWUNG.get(id);
		return t == null ? 100 : Math.min(100, now - t);
	}

	/**
	 * Whom a fighter fights: a bot its own target; a person whoever they traded hits with lately, else the nearest enemy
	 * they see, else the nearest one close by.
	 */
	static ServerPlayer target(Match m, ServerPlayer p) {
		if (p instanceof BotPlayer b) {
			UUID t = b.brain.targetId();
			ServerPlayer o = t == null ? null : m.player(t);
			return o != null && m.inPlay(t) && o.level() == p.level() ? o : null;
		}
		Object[] e = ENGAGED_WITH.get(p.getUUID());
		if (e != null && now - (long) e[1] <= ENGAGED) {
			ServerPlayer o = m.player((UUID) e[0]);
			if (o != null && m.inPlay(o.getUUID()) && m.isEnemy(p.getUUID(), o.getUUID()) && o.level() == p.level() && o.distanceTo(p) <= SEE) return o;
		}
		V3 eye = new V3(p.getX(), p.getEyeY(), p.getZ());
		ServerPlayer seen = null, close = null;
		double seenD = SEE, closeD = CLOSE;
		for (UUID id : m.participants()) {
			if (id.equals(p.getUUID()) || !m.inPlay(id) || !m.isEnemy(p.getUUID(), id)) continue;
			ServerPlayer o = m.player(id);
			if (o == null || o.level() != p.level() || o.isSpectator()) continue;
			double d = o.distanceTo(p);
			if (d < seenD && FightState.sees(p, eye, o)) {
				seen = o;
				seenD = d;
			}
			if (d < closeD) {
				close = o;
				closeD = d;
			}
		}
		return seen != null ? seen : close;
	}

	private static Method lastInput;
	private static boolean inputLooked;
	private static final Map<String, Method> KEY = new HashMap<>();

	/** The keys a person holds (forward, backward, left, right, jump) as their client last told the server; null when unknown. */
	static boolean[] keys(ServerPlayer p) {
		if (!inputLooked) {
			inputLooked = true;
			try {
				lastInput = ServerPlayer.class.getMethod("getLastClientInput");
			} catch (ReflectiveOperationException | RuntimeException e) {
				lastInput = null;
			}
		}
		if (lastInput == null) return null;
		try {
			Object in = lastInput.invoke(p);
			if (in == null) return null;
			String[] names = {"forward", "backward", "left", "right", "jump"};
			boolean[] out = new boolean[names.length];
			for (int k = 0; k < names.length; k++) {
				Method m = KEY.get(names[k]);
				if (m == null) {
					m = in.getClass().getMethod(names[k]);
					KEY.put(names[k], m);
				}
				out[k] = (Boolean) m.invoke(in);
			}
			return out;
		} catch (ReflectiveOperationException | RuntimeException | ClassCastException e) {
			lastInput = null;
			return null;
		}
	}

	// ------------------------------------------------------------------ files

	private static void write(Rec r, String line) {
		Sink sink = r.sink;
		IO.submit(() -> {
			if (sink.w == null || sink.broken) return;
			try {
				sink.w.write(line);
				sink.w.write('\n');
			} catch (IOException e) {
				sink.broken = true;
				BattleHub.LOGGER.warn("Fight log stopped: {}", e.toString());
			}
		});
	}

	private static void close(Rec r, List<Integer> winners, String reason) {
		write(r, FightLog.end(r.tick, winners, reason));
		Sink sink = r.sink;
		Path file = r.file;
		int match = r.match.id;
		int rows = r.rows;
		long cap = Math.max(10, cfg().maxMegabytes) * 1024L * 1024L;
		IO.submit(() -> {
			try {
				if (sink.w != null) sink.w.close();
			} catch (IOException e) {
				BattleHub.LOGGER.warn("Could not close {}: {}", file, e.toString());
			}
			OPEN.remove(file);
			if (rows == 0) {
				// nobody fought (everyone left at once, a match without targets): no file
				try {
					Files.deleteIfExists(file);
				} catch (IOException ignored) {
					// left as it is
				}
				return;
			}
			lastClosed = file;
			lastClosedMatch = match;
			trim(cap);
		});
	}

	/** The oldest logs and exports go until the folder holds at most {@code cap} bytes. */
	private static void trim(long cap) {
		try (Stream<Path> s = Files.walk(dir())) {
			List<Path> files = s.filter(Files::isRegularFile).filter(f -> !isOpen(f)).sorted(Comparator.comparingLong(FightRecorder::modified)).toList();
			long total = 0;
			for (Path f : files) total += size(f);
			for (Path f : files) {
				if (total <= cap) break;
				total -= size(f);
				Files.deleteIfExists(f);
			}
		} catch (IOException e) {
			BattleHub.LOGGER.warn("Could not tidy {}: {}", dir(), e.toString());
		}
	}

	private static boolean isOpen(Path f) {
		return OPEN.contains(f);
	}

	private static long size(Path f) {
		try {
			return Files.size(f);
		} catch (IOException e) {
			return 0;
		}
	}

	private static long modified(Path f) {
		try {
			return Files.getLastModifiedTime(f).toMillis();
		} catch (IOException e) {
			return 0;
		}
	}

	/** Finished logs not yet exported (in the folder itself, oldest first). */
	private static List<Path> waiting() throws IOException {
		if (!Files.isDirectory(dir())) return List.of();
		try (Stream<Path> s = Files.list(dir())) {
			return s.filter(f -> f.getFileName().toString().endsWith(".jsonl.gz")).filter(f -> !isOpen(f))
					.sorted(Comparator.comparingLong(FightRecorder::modified)).toList();
		}
	}

	/** How many fights wait to be exported and how big they are; how many are being recorded now. */
	public static int[] status() {
		try {
			List<Path> w = waiting();
			long bytes = 0;
			for (Path f : w) bytes += size(f);
			return new int[] {w.size(), (int) Math.round(bytes / 1024.0), RECS.size()};
		} catch (IOException e) {
			return new int[] {0, 0, RECS.size()};
		}
	}

	/**
	 * Zips the finished logs not yet exported into {@code fights/export-<time>.zip} (on the writing thread, after
	 * everything before it is written) and moves them to {@code fights/exported}; {@code done} gets the zip and the
	 * number of fights (null and 0 when there was nothing), on the server thread.
	 */
	public static void export(MinecraftServer server, Consumer<Object[]> done) {
		IO.submit(() -> {
			Object[] result;
			try {
				List<Path> w = waiting();
				if (w.isEmpty()) {
					result = new Object[] {null, 0, 0L};
				} else {
					String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
					Path zip = dir().resolve("export-" + stamp + ".zip");
					try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip))) {
						for (Path f : w) {
							z.putNextEntry(new ZipEntry(f.getFileName().toString()));
							Files.copy(f, z);
							z.closeEntry();
						}
					}
					Path moved = dir().resolve("exported");
					Files.createDirectories(moved);
					for (Path f : w) Files.move(f, moved.resolve(f.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					result = new Object[] {zip, w.size(), Files.size(zip)};
				}
			} catch (IOException e) {
				BattleHub.LOGGER.warn("Could not export the fight logs: {}", e.toString());
				result = new Object[] {null, -1, 0L};
			}
			Object[] r = result;
			server.execute(() -> done.accept(r));
		});
	}

	/** Deletes every finished log, exported or not, and the exports (not the fights being recorded); the number deleted. */
	public static int clearFiles() {
		int n = 0;
		if (!Files.isDirectory(dir())) return 0;
		try (Stream<Path> s = Files.walk(dir())) {
			for (Path f : s.filter(Files::isRegularFile).filter(f -> !isOpen(f)).toList()) {
				Files.deleteIfExists(f);
				n++;
			}
		} catch (IOException e) {
			BattleHub.LOGGER.warn("Could not clear {}: {}", dir(), e.toString());
		}
		return n;
	}

	/** The last finished log and its match (self-test). */
	public static Path lastClosed() {
		return lastClosed;
	}

	public static int lastClosedMatch() {
		return lastClosedMatch;
	}

	/** Server stopping: every open log ends ("stopped"); the files are written before the server goes. */
	public static void clear() {
		for (Rec r : List.copyOf(RECS.values())) close(r, List.of(), "stopped");
		RECS.clear();
		try {
			IO.submit(() -> {}).get(10, java.util.concurrent.TimeUnit.SECONDS);
		} catch (Exception e) {
			BattleHub.LOGGER.warn("The fight logs were not all written: {}", e.toString());
		}
		SWUNG.clear();
		POS.clear();
		VEL.clear();
		GROUND.clear();
		ENGAGED_WITH.clear();
	}

	private static String version() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(BattleHub.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
	}
}
