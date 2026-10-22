package net.battlehub.bot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.ml.Lessons;
import net.battlehub.core.ml.MiniJson;
import net.battlehub.core.ml.ModelReport;
import net.battlehub.core.ml.ObsRanges;
import net.battlehub.lang.Lang;
import net.battlehub.match.Match;
import net.battlehub.util.Msg;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * After every match with a learned model the operators hear from each bot how much of its fight went as its model
 * chose, why the rest did not, which values were new to the model and what it should practise in the simulation
 * (core/ml/ModelReport, ml/spec/lessons.json). The totals per spec and mode are kept in
 * {@code config/battlehub/ml_feedback.json} with the lessons and their scenario focus, for
 * {@code ml/train/selfplay2.py --focus}; {@code /battlehub bots report} shows them.
 */
public final class ModelReports {
	private ModelReports() {}

	/** A bot tells its report after at least this many ticks of fight (3 seconds). */
	static final int MIN_TICKS = 60;
	/** Lessons explaining less of the fight than this are not mentioned. */
	static final double MIN_LESSON = 0.03;
	static final int MAX_LESSONS = 3;
	static final String FILE = "ml_feedback.json";

	private static Lessons lessons;
	private static boolean lessonsTried;
	private static final Map<CombatModels.Family, ObsRanges> RANGES = new HashMap<>();
	private static final java.util.Set<CombatModels.Family> RANGES_TRIED = new java.util.HashSet<>();
	/** The totals: per spec ("v1", "v2") over everything, and per spec and mode. */
	private static Map<String, ModelReport> totals;
	private static Map<String, Map<String, ModelReport>> byMode;
	private static String since;
	/** The last report of each bot, as told, and the last lines each operator got (self-test). */
	private static final Map<String, List<String>> LAST = new HashMap<>();
	private static final Map<UUID, List<String>> SENT = new HashMap<>();
	/** Who counts as an operator: those with the gamemaster commands (the self-test names someone else). */
	private static java.util.function.Predicate<ServerPlayer> operator = ModelReports::gamemaster;

	static synchronized Lessons lessons() {
		if (!lessonsTried) {
			lessonsTried = true;
			try {
				lessons = Lessons.shipped();
			} catch (IOException | RuntimeException e) {
				BattleHub.LOGGER.warn("Model reports without lessons: {}", e.toString());
			}
		}
		return lessons;
	}

	/** What the family's model saw in training, or null (then nothing counts as new). */
	static synchronized ObsRanges ranges(CombatModels.Family fam) {
		if (RANGES_TRIED.add(fam) && fam.spec() != null) {
			try {
				RANGES.put(fam, ObsRanges.shipped(fam.specFile, fam.spec()));
			} catch (IOException | RuntimeException e) {
				BattleHub.LOGGER.warn("Model reports without the training ranges of {}: {}", fam.specFile, e.toString());
			}
		}
		return RANGES.get(fam);
	}

	/** Whether a learned model fights in this match (v2, or v1 in its modes). */
	static boolean inPlay(Match m) {
		return LearnedFight.active(m) || (LearnedMelee.enabledFor(m) && CombatModels.ready());
	}

	// ------------------------------------------------------------------ after a match

	/** The match is over: every bot that fought with its model long enough reports to the operators; the totals grow. */
	public static void matchEnd(Match m) {
		Lessons l = lessons();
		if (l == null) return;
		List<ServerPlayer> ops = null;
		boolean added = false;
		for (UUID id : m.participants()) {
			if (!(m.player(id) instanceof BotPlayer b)) continue;
			ModelReport r = b.brain.report();
			String spec = b.brain.reportSpec();
			if (r.ticks() >= MIN_TICKS && !spec.isEmpty()) {
				if (ops == null) ops = operators(b.level().getServer());
				String name = b.getGameProfile().name();
				LAST.put(name, strings(lines(null, name, b.difficulty.id(), spec, m.mode.id(), r, l)));
				BattleHub.LOGGER.info("Model report {}: {}", name, String.join(" | ", LAST.get(name)));
				for (ServerPlayer op : ops) {
					List<Component> lines = lines(op, name, b.difficulty.id(), spec, m.mode.id(), r, l);
					for (Component c : lines) Msg.raw(op, c);
					SENT.put(op.getUUID(), strings(lines));
				}
				count(spec, m.mode.id(), r);
				added = true;
			}
			b.brain.resetReport();
		}
		if (added) save();
	}

	/** Operators online who want the reports. */
	private static List<ServerPlayer> operators(MinecraftServer server) {
		List<ServerPlayer> out = new ArrayList<>();
		if (!ConfigManager.config().bots.modelReport) return out;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (Bots.isBot(p.getUUID()) || net.battlehub.data.PlayerStore.get(p).modelReportsOff) continue;
			if (operator.test(p)) out.add(p);
		}
		return out;
	}

	private static boolean gamemaster(ServerPlayer p) {
		return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(p.createCommandSourceStack());
	}

	/** The self-test lets someone else count as an operator; null: the gamemasters again. */
	public static void operatorsForTest(java.util.function.Predicate<ServerPlayer> who) {
		operator = who == null ? ModelReports::gamemaster : who;
	}

	/** The last report lines an operator got in chat (self-test). */
	public static List<String> sentTo(UUID player) {
		return SENT.getOrDefault(player, List.of());
	}

	/**
	 * A report as chat lines for a viewer (null: the server's language): who, how much of the fight went as the model
	 * chose, why not the rest, the values new to it, and what to practise in the simulation.
	 */
	static List<Component> lines(ServerPlayer p, String bot, String level, String spec, String mode, ModelReport r, Lessons l) {
		List<Component> out = new ArrayList<>();
		out.add(tr(p, "bots.report.header", bot, tr(p, "bots.level." + level), spec, mode, Math.round(r.ticks() / 20.0)));
		out.addAll(body(p, r, l));
		return out;
	}

	/** A report without its first line: the split, the reasons, the new values, the lessons. */
	private static List<Component> body(ServerPlayer p, ModelReport r, Lessons l) {
		List<Component> out = new ArrayList<>();
		out.add(tr(p, "bots.report.split", pct(r.keptShare()), pct(r.changedShare()), pct(r.replacedShare())));
		List<String> why = new ArrayList<>();
		for (ModelReport.Reason reason : r.reasons()) {
			if (why.size() == 4) break;
			why.add(tr(p, "bots.report.why." + reason.why().id()).getString() + " " + pct(reason.share()) + "%");
		}
		if (!why.isEmpty()) out.add(tr(p, "bots.report.why", String.join(", ", why)));
		List<String> fresh = new ArrayList<>();
		for (ModelReport.Feature f : r.outsideTop(4)) fresh.add(f.name() + " " + pct(f.share()) + "%");
		if (!fresh.isEmpty()) out.add(tr(p, "bots.report.new", String.join(", ", fresh)));
		List<String> learn = new ArrayList<>();
		for (ModelReport.Lesson lesson : r.lessons(l, MIN_LESSON, MAX_LESSONS)) {
			String text = tr(p, "bots.report.lesson." + lesson.id()).getString();
			if (lesson.items()) text += " " + tr(p, "bots.report.with_items").getString();
			learn.add(text + " (" + pct(lesson.weight()) + "%)");
		}
		out.add(learn.isEmpty() ? tr(p, "bots.report.nothing") : tr(p, "bots.report.learn", String.join("; ", learn)));
		return out;
	}

	private static Component tr(ServerPlayer p, String key, Object... args) {
		return p == null ? Lang.tr(key, args) : Lang.tr(p, key, args);
	}

	private static long pct(double share) {
		return Math.round(share * 100);
	}

	private static List<String> strings(List<Component> lines) {
		List<String> out = new ArrayList<>();
		for (Component c : lines) out.add(c.getString());
		return out;
	}

	/** The last report a bot told (self-test). */
	public static List<String> last(String botName) {
		return LAST.getOrDefault(botName, List.of());
	}

	// ------------------------------------------------------------------ the totals and their file

	private static synchronized void count(String spec, String mode, ModelReport r) {
		load();
		ModelReport one = new ModelReport();
		one.add(r);
		one.match();
		totals.computeIfAbsent(spec, k -> new ModelReport()).add(one);
		byMode.computeIfAbsent(spec, k -> new LinkedHashMap<>()).computeIfAbsent(mode, k -> new ModelReport()).add(one);
	}

	private static Path file() {
		return ConfigManager.dir().resolve(FILE);
	}

	@SuppressWarnings("unchecked")
	private static synchronized void load() {
		if (totals != null) return;
		totals = new LinkedHashMap<>();
		byMode = new LinkedHashMap<>();
		since = Instant.now().toString();
		Path f = file();
		if (!Files.exists(f)) return;
		try {
			Map<String, Object> d = MiniJson.object(MiniJson.parse(Files.readString(f, StandardCharsets.UTF_8)));
			if (d.get("since") instanceof String s) since = s;
			for (Map.Entry<String, Object> e : MiniJson.object(d.get("specs")).entrySet()) {
				Map<String, Object> spec = MiniJson.object(e.getValue());
				totals.put(e.getKey(), ModelReport.fromMap(MiniJson.object(spec.get("total"))));
				Map<String, ModelReport> modes = new LinkedHashMap<>();
				for (Map.Entry<String, Object> me : MiniJson.object(spec.get("by_mode")).entrySet()) {
					modes.put(me.getKey(), ModelReport.fromMap((Map<String, Object>) me.getValue()));
				}
				byMode.put(e.getKey(), modes);
			}
		} catch (IOException | RuntimeException e) {
			BattleHub.LOGGER.warn("Could not read {}, starting the model report totals again: {}", f, e.toString());
		}
	}

	private static synchronized void save() {
		Lessons l = lessons();
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("doc", "How the learned bots' fights kept to their models (written by BattleHub after every match with them). "
				+ "Train on what they lacked: python train/selfplay2.py --focus <this file> (see ml/README.md).");
		d.put("version", 1);
		d.put("since", since);
		d.put("updated", Instant.now().toString());
		Map<String, Object> specs = new LinkedHashMap<>();
		for (Map.Entry<String, ModelReport> e : totals.entrySet()) {
			Map<String, Object> spec = new LinkedHashMap<>();
			spec.put("total", e.getValue().toMap());
			Map<String, Object> modes = new LinkedHashMap<>();
			byMode.getOrDefault(e.getKey(), Map.of()).forEach((mode, r) -> modes.put(mode, r.toMap()));
			spec.put("by_mode", modes);
			List<Object> ls = new ArrayList<>();
			for (ModelReport.Lesson lesson : e.getValue().lessons(l, MIN_LESSON, 6)) {
				Map<String, Object> one = new LinkedHashMap<>();
				one.put("id", lesson.id());
				one.put("weight", Math.round(lesson.weight() * 1000) / 1000.0);
				one.put("items", lesson.items());
				one.put("focus", l.focus(lesson.id()));
				ls.add(one);
			}
			spec.put("lessons", ls);
			specs.put(e.getKey(), spec);
		}
		d.put("specs", specs);
		ConfigManager.writeJson(file(), ConfigManager.GSON.toJson(d));
	}

	// ------------------------------------------------------------------ the command

	/** The totals since the file began, per spec and mode, with what to practise (/battlehub bots report). */
	public static synchronized List<Component> summary(ServerPlayer p) {
		load();
		Lessons l = lessons();
		List<Component> out = new ArrayList<>();
		if (totals.isEmpty() || l == null) {
			out.add(tr(p, "bots.report.none", since));
			return out;
		}
		for (Map.Entry<String, ModelReport> e : totals.entrySet()) {
			ModelReport r = e.getValue();
			out.add(tr(p, "bots.report.total", e.getKey(), r.matches(), Math.round(r.ticks() / 20.0), since));
			out.addAll(body(p, r, l));
			for (Map.Entry<String, ModelReport> me : byMode.getOrDefault(e.getKey(), Map.of()).entrySet()) {
				ModelReport mr = me.getValue();
				out.add(tr(p, "bots.report.mode", me.getKey(), mr.matches(), pct(mr.keptShare()), Math.round(mr.ticks() / 20.0)));
			}
		}
		out.add(tr(p, "bots.report.file", file().toString()));
		return out;
	}

	/** An operator switches the reports in their chat on or off; true when now on. */
	public static boolean toggle(ServerPlayer p, boolean on) {
		net.battlehub.data.PlayerStore.get(p).modelReportsOff = !on;
		net.battlehub.data.PlayerStore.save(p.getUUID());
		return on;
	}

	/** Starts the totals (and the file) again. */
	public static synchronized void reset() {
		totals = new LinkedHashMap<>();
		byMode = new LinkedHashMap<>();
		since = Instant.now().toString();
		try {
			Files.deleteIfExists(file());
		} catch (IOException e) {
			BattleHub.LOGGER.warn("Could not delete {}: {}", file(), e.toString());
		}
	}

	/** Server stopping: the totals are on disk; forget the rest. */
	static synchronized void clear() {
		totals = null;
		byMode = null;
		LAST.clear();
		SENT.clear();
		operator = ModelReports::gamemaster;
	}
}
