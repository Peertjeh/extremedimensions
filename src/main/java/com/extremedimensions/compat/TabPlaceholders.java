package com.extremedimensions.compat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.anchor.AnchorService;
import com.extremedimensions.config.Config;
import com.extremedimensions.config.Preset;
import com.extremedimensions.coop.FallenService;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.seam.SeamDepth;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.Dims;
import com.extremedimensions.tapestry.Tapestry;
import com.extremedimensions.tapestry.Unweaving;
import com.extremedimensions.trial.Trial;
import com.extremedimensions.trial.TrialService;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the mod tells a tablist, a scoreboard or a chat format about each player and about the world, as
 * {@code %extremedimensions:<name>%} placeholders (offered to Text Placeholder API by {@link PlaceholderApiHook}, which
 * is how TAB reads them on Fabric). The values are worked out on the server's thread now and then and kept, so a mod
 * that asks from a thread of its own (TAB does) reads a finished snapshot and never the live data. They are in English:
 * a tablist is the same for everyone, and a dedicated server has no language files of the mod's.
 */
public final class TabPlaceholders {
	private TabPlaceholders() {}

	/** The placeholders about one player. */
	public static final List<String> PLAYER = List.of("curses", "curses_beyond", "curses_overworld", "curses_everywhere", "curses_extreme",
			"abyssal", "badge", "scars", "realm", "status", "time_left", "expeditions", "collapses", "unwoven", "trial_medal");
	/** The placeholders about the world. */
	public static final List<String> WORLD = List.of("difficulty", "ending", "tapestry", "new_thread", "trial");

	private static final Map<UUID, Map<String, Component>> PLAYERS = new ConcurrentHashMap<>();
	private static volatile Map<String, Component> world = Map.of();
	/** Whether anything asks for the placeholders (set when they are offered; the tests take snapshots directly). */
	public static volatile boolean offered = false;

	/**
	 * At start-up: offers the placeholders to placeholder-api if it is installed. (This class never touches
	 * placeholder-api itself, so a server without it never loads a class of it; a version this mod does not know is
	 * left alone with a warning.)
	 */
	public static void offer() {
		if (!FabricLoader.getInstance().isModLoaded("placeholder-api")) {
			return;
		}
		try {
			PlaceholderApiHook.register();
			offered = true;
			ExtremeDimensions.LOGGER.info("Offered {} placeholders to placeholder-api as %{}:<name>%", PLAYER.size() + WORLD.size(),
					ExtremeDimensions.MOD_ID);
		} catch (LinkageError | RuntimeException e) {
			ExtremeDimensions.LOGGER.warn("This version of placeholder-api is not one Extreme Dimensions knows; its placeholders are not offered", e);
		}
	}

	/** A player's placeholder as last worked out ("" when the placeholders are switched off or not known yet). */
	public static Component player(UUID player, String name) {
		if (Config.PLACEHOLDERS <= 0) {
			return Component.empty();
		}
		Map<String, Component> m = PLAYERS.get(player);
		Component c = m == null ? null : m.get(name);
		return c == null ? Component.empty() : c;
	}

	/** A placeholder about the world as last worked out. */
	public static Component world(String name) {
		if (Config.PLACEHOLDERS <= 0) {
			return Component.empty();
		}
		Component c = world.get(name);
		return c == null ? Component.empty() : c;
	}

	/** Every server tick: now and then, the snapshot of every player online and of the world is made anew. */
	public static void tick(MinecraftServer s) {
		if (!offered || Config.PLACEHOLDERS <= 0 || s.getTickCount() % Math.max(1, Config.PLACEHOLDER_REFRESH_TICKS) != 0) {
			return;
		}
		refresh(s);
	}

	/** Makes the snapshot anew, and forgets players who have left. */
	public static void refresh(MinecraftServer s) {
		Map<UUID, Boolean> online = new LinkedHashMap<>();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			PLAYERS.put(p.getUUID(), values(p));
			online.put(p.getUUID(), true);
		}
		PLAYERS.keySet().removeIf(id -> !online.containsKey(id));
		world = values(s);
	}

	/** The server stops: nothing is kept for the next world. */
	public static void clear() {
		PLAYERS.clear();
		world = Map.of();
	}

	/** Every placeholder about one player, worked out now (on the server's thread). */
	public static Map<String, Component> values(ServerPlayer p) {
		PlayerData d = DataStore.get(p.getUUID());
		Map<String, Component> m = new LinkedHashMap<>();
		int total = curses(d, true), abyssal = abyssal(d, true);
		m.put("curses", text(total));
		for (Penalty.Group g : Penalty.Group.values()) {
			m.put("curses_" + g.id(), text(Unweaving.stacks(d, g)));
		}
		m.put("abyssal", text(abyssal));
		m.put("badge", badge(total, abyssal));
		m.put("scars", text(d.scars));
		m.put("realm", text(realm(p, d)));
		m.put("status", text(status(p, d)));
		boolean seam = SeamService.inSeam(p);
		double left = seam ? d.seamLeft : d.inRun ? AnchorService.linkedCharge(p, d) : -1;
		m.put("time_left", text(left < 0 ? "" : clock(left)));
		m.put("expeditions", text(d.expeditions));
		m.put("collapses", text(d.collapses));
		m.put("unwoven", text(d.unwoven));
		m.put("trial_medal", text(trialMedal(d)));
		return m;
	}

	/** Every placeholder about the world, worked out now. */
	public static Map<String, Component> values(MinecraftServer s) {
		Map<String, Component> m = new LinkedHashMap<>();
		m.put("difficulty", text(difficulty()));
		m.put("ending", text(ending()));
		m.put("tapestry", text(Tapestry.count() + "/" + Tapestry.Panel.values().length));
		m.put("new_thread", text(DataStore.newThread()));
		m.put("trial", text(trial()));
		return m;
	}

	// The words below are the stats export's and the Discord messages' too (see StatsExport, DiscordWebhook)

	/** The world's difficulty: Easy, Normal, Hard, Extreme, or Custom when its rules match none of them. */
	static String difficulty() {
		Preset preset = Preset.matching(Preset.current());
		return preset == null ? "Custom" : switch (preset) {
			case EASY -> "Easy";
			case NORMAL -> "Normal";
			case HARD -> "Hard";
			case EXTREME -> "Extreme";
		};
	}

	/** The world's ending: Mended, Severed or Unended. */
	static String ending() {
		String ending = DataStore.ending();
		return "mended".equals(ending) ? "Mended" : "severed".equals(ending) ? "Severed" : "Unended";
	}

	/** This week's Rift Trial and twist ("Hunter · Still Seam"); "" while the trials are not open yet. */
	static String trial() {
		int week = TrialService.week();
		return TrialService.unlocked() ? trial(TrialService.kind(week), TrialService.twist(week)) : "";
	}

	static String trial(Trial.Kind kind, Trial.Twist twist) {
		return goal(kind) + " · " + twist(twist);
	}

	/**
	 * Curse stacks a player carries: for one online as they act (on an expedition with the pact's on top, as the tablist
	 * shows them), for one offline their own.
	 */
	static int curses(PlayerData d, boolean online) {
		int total = 0;
		for (Penalty pen : Penalty.values()) {
			total += online ? d.carried(pen) : d.owned(pen);
		}
		return total;
	}

	/** Abyssal curses a player carries (counted the same way). */
	static int abyssal(PlayerData d, boolean online) {
		int n = 0;
		for (Penalty pen : Penalty.values()) {
			n += pen.abyssal && (online ? d.carried(pen) : d.owned(pen)) > 0 ? 1 : 0;
		}
		return n;
	}

	/** This week's best Rift Trial medal (Bronze, Silver, Gold), "" without one. */
	static String trialMedal(PlayerData d) {
		return d.trialClaimWeek == TrialService.week() ? medal(d.trialClaimMedal) : "";
	}

	private static Component text(int n) {
		return Component.literal(Integer.toString(n));
	}

	private static Component text(String s) {
		return s.isEmpty() ? Component.empty() : Component.literal(s);
	}

	/** The curses carried at a glance: nothing without curses, else a skull and the count in a colour that darkens. */
	static Component badge(int total, int abyssal) {
		if (total <= 0 && abyssal <= 0) {
			return Component.empty();
		}
		ChatFormatting colour = total >= 60 ? ChatFormatting.DARK_RED : total >= 30 ? ChatFormatting.RED : total >= 10 ? ChatFormatting.YELLOW
				: ChatFormatting.GRAY;
		MutableComponent c = Component.literal("☠" + total).withStyle(colour);
		if (abyssal > 0) {
			c.append(Component.literal(" ✦").withStyle(ChatFormatting.DARK_PURPLE));
		}
		return c;
	}

	static String realm(ServerPlayer p, PlayerData d) {
		if (d.away) {
			return "Elsewhere";
		}
		return switch (Dims.of(p.level())) {
			case Dims.NETHER -> "Nether";
			case Dims.END -> "End";
			case Dims.SEAM -> SeamDepth.inFray(p) ? "Fray" : "Seam";
			case Dims.OVERWORLD -> "Overworld";
			default -> "Elsewhere";
		};
	}

	static String status(ServerPlayer p, PlayerData d) {
		if (FallenService.fallen(p)) {
			return "Fallen";
		}
		if (!d.unweaving.isEmpty() && Unweaving.active()) {
			return "Unweaving";
		}
		if (SeamService.inSeam(p)) {
			return SeamDepth.inFray(p) ? "In the Fray" : "In the Seam";
		}
		if (d.inRun) {
			return "On an expedition";
		}
		return d.away ? "Elsewhere" : "At home";
	}

	/** Seconds as m:ss (h:mm:ss from an hour). */
	public static String clock(double seconds) {
		long s = (long) Math.ceil(Math.max(0, seconds));
		return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
				: String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}

	static String medal(int medal) {
		return switch (medal) {
			case 3 -> "Gold";
			case 2 -> "Silver";
			case 1 -> "Bronze";
			default -> "";
		};
	}

	static String goal(Trial.Kind kind) {
		return switch (kind) {
			case VEINS -> "Vein-cutter";
			case HUNT -> "Hunter";
			case WANDER -> "Wanderer";
			case FAR -> "Far-walker";
			case PLUNDER -> "Plunderer";
		};
	}

	static String twist(Trial.Twist twist) {
		return switch (twist) {
			case CALM -> "Still Seam";
			case SHORT -> "Short Thread";
			case HUNTED -> "Thin Hours";
			case RESTLESS -> "Restless Rift";
			case HEAVY -> "Heavy Silk";
		};
	}
}
