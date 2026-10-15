package com.extremedimensions.client.state;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.network.ModPackets;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.Dwindling;
import com.extremedimensions.penalty.SealedPockets;

/** The latest state received from the server, plus animation timestamps for the HUD. */
public final class ClientState {
	private ClientState() {}

	/** One curse the player carries, as sent by the server. */
	public record Mark(String id, Penalty penalty, int stacks, int max, double value) {}

	/** Own charge in charged anchors nearby; -1 = none nearby. */
	public static int nearNether = -1;
	public static int nearEnd = -1;
	public static int runCharge = 0;
	public static int runMax = 0;
	/** 0 = Overworld, 1 = Nether, 2 = End, 3 = the Seam, 4 = another mod's world (nothing of the mod shows there). */
	public static int dimCode = 0;
	public static int drainX100 = 100;
	public static int expeditions = 0;
	public static int collapses = 0;
	public static int longestRun = 0;
	public static int runSecs = 0;
	public static List<Mark> marks = new ArrayList<>();
	/** Curse stacks taken off (each one lightens the drain). */
	public static int scars = 0;
	/** -1 = not bound, else blocks to the arrival portal (beyond) or the anchor (Overworld). */
	public static int tetherDist = -1;
	/** The Rift Trial under way: "kind;twist;score;bronze;silver;gold", or "" without one. */
	public static String trial = "";
	public static long syncMs = 0;

	// --- animation state ---
	public static double shownNether = 0;
	public static double shownEnd = 0;
	private static long lastFrame = 0;
	public static long startAt = 0;
	public static long safeAt = 0;
	public static long collapseAt = 0;
	/** Dimension the Collapse happened in (1 = Nether, 2 = End) and when the player arrived home after it. */
	public static int collapseDim = 1;
	public static long homeAt = 0;
	/** A Lurker took something. */
	public static long takenAt = 0;
	/** The seal broke (or the world was severed), and a Wound Storm began. */
	public static long sealAt = 0;
	public static long stormAt = 0;
	public static long bounceAt = 0;
	public static long penaltyAt = 0;
	public static long endedAt = 0;
	public static long netherFlashAt = 0;
	public static long endFlashAt = 0;
	public static long netherGainAt = 0;
	public static long endGainAt = 0;
	public static int netherGain = 0;
	public static int endGain = 0;
	/** Anchor charged (1 = Nether, 2 = End) or tether bound (3). */
	public static long depositAt = 0;
	public static int depositKind = 1;

	/** Steps of the first-expedition guide done; finished at Guide.STEPS (unknown until the first update). */
	public static int guide = com.extremedimensions.guide.Guide.STEPS;

	/** The world's settings as the server sent them (JSON), and whether this player may change them. */
	public static String settingsJson = null;
	public static boolean settingsEditable = false;
	/** The server's values were laid over this computer's config (a remote server): undone on leaving. */
	private static boolean remoteSettings = false;

	/** The server's settings arrived: the HUD and screens use its numbers from now on. */
	public static void applySettings(ModPackets.SettingsPayload s) {
		settingsJson = s.json();
		settingsEditable = s.canEdit();
		// In singleplayer the client shares the server's values already; only a remote server's are laid over ours
		if (!net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer()) {
			try {
				com.google.gson.JsonElement json = com.google.gson.JsonParser.parseString(s.json());
				if (json.isJsonObject()) {
					com.extremedimensions.config.Config.apply(json.getAsJsonObject());
					remoteSettings = true;
				}
			} catch (Exception e) {
				// keep this computer's values
			}
		}
		com.extremedimensions.client.screen.SettingsScreen.refresh();
	}

	public static void apply(ModPackets.StatePayload s) {
		// What follows holds for the world the client stands in now, and only there
		stateLevel = net.minecraft.client.Minecraft.getInstance().level;
		nearNether = s.nearNether();
		nearEnd = s.nearEnd();
		runCharge = s.runCharge();
		runMax = s.runMax();
		dimCode = s.dimCode();
		drainX100 = s.drainX100();
		expeditions = s.expeditions();
		collapses = s.collapses();
		longestRun = s.longestRun();
		runSecs = s.runSecs();
		marks = parse(s.penalties());
		// In another mod's world the curses sleep: no pocket is sealed and every stack holds its full count
		SealedPockets.client = away() ? 0 : Math.min(27, stacksOf("sealed_pockets") * (int) Math.round(valueOf("sealed_pockets")));
		Dwindling.client = away() ? 0 : stacksOf("dwindling");
		Dwindling.clientValue = valueOf("dwindling");
		scars = s.scars();
		tetherDist = s.tetherDist();
		guide = s.guide();
		trial = s.trial();
		syncMs = System.currentTimeMillis();
		// First update from the Overworld after a Collapse: start the fade-in
		if (collapseAt > 0 && homeAt <= collapseAt && dimCode == 0 && syncMs - collapseAt > 2000) {
			homeAt = syncMs;
		}
	}

	/**
	 * The client world the last state came from. In any other world (after a portal, or on another server behind a
	 * proxy, which never says goodbye to the client) what the client holds is stale, and nothing of it acts until the
	 * server speaks again; a server without the mod never does.
	 */
	private static Object stateLevel = null;

	/** In another mod's world, or in a world the server has said nothing about: the mod sleeps there, and its HUD with it. */
	public static boolean away() {
		var level = net.minecraft.client.Minecraft.getInstance().level;
		return dimCode == 4 || level == null || level != stateLevel;
	}

	/** Every client tick: in a world the mod sleeps in, no pocket stays sealed and every stack holds its full count. */
	public static void tick() {
		if (away()) {
			SealedPockets.client = 0;
			Dwindling.client = 0;
		}
	}

	/** Whether the server the client plays on runs the mod at all (its screens and requests mean nothing elsewhere). */
	public static boolean modOnServer() {
		return net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(ModPackets.RequestCursesPayload.TYPE);
	}

	/** Whether a Collapse sequence is still running (the death screen is skipped during it). */
	public static boolean inCollapse() {
		return collapseAt > 0 && homeAt <= collapseAt && System.currentTimeMillis() - collapseAt < 15000;
	}

	private static List<Mark> parse(String all) {
		List<Mark> out = new ArrayList<>();
		if (all == null || all.isEmpty()) {
			return out;
		}
		for (String part : all.split(";")) {
			String[] kv = part.split(":");
			try {
				int stacks = kv.length > 1 ? Integer.parseInt(kv[1]) : 1;
				int max = kv.length > 2 ? Integer.parseInt(kv[2]) : stacks;
				Penalty pen = Penalty.byId(kv[0]);
				double value = kv.length > 3 ? Double.parseDouble(kv[3]) : pen != null ? pen.value : 0;
				out.add(new Mark(kv[0], pen, stacks, max, value));
			} catch (NumberFormatException e) {
				// skip malformed entry
			}
		}
		return out;
	}

	/** Value per stack of a curse the player carries (as the server's config has it), 0 if not carried. */
	public static double valueOf(String id) {
		for (Mark m : marks) {
			if (m.id().equals(id)) {
				return m.value();
			}
		}
		return 0;
	}

	/** Active stacks of a penalty. */
	public static int stacksOf(String id) {
		for (Mark m : marks) {
			if (m.id().equals(id)) {
				return m.stacks();
			}
		}
		return 0;
	}

	/** Called every frame: lets the shown anchor values glide towards the real ones. */
	public static void tickSmooth() {
		long now = System.currentTimeMillis();
		double dt = lastFrame == 0 ? 0.016 : Math.min(0.25, (now - lastFrame) / 1000.0);
		lastFrame = now;
		double k = 1 - Math.exp(-dt * 7.0);
		shownNether += (Math.max(0, nearNether) - shownNether) * k;
		shownEnd += (Math.max(0, nearEnd) - shownEnd) * k;
	}

	/** Handles an animation request from the server (see {@code Fx}). */
	public static void onFx(int type, int value) {
		long now = System.currentTimeMillis();
		switch (type) {
			case 1 -> { netherFlashAt = now; netherGainAt = now; netherGain = value; depositAt = now; depositKind = 1; }
			case 2 -> { endFlashAt = now; endGainAt = now; endGain = value; depositAt = now; depositKind = 2; }
			case 3, 4 -> startAt = now;
			case 5 -> safeAt = now;
			case 6 -> { collapseAt = now; collapseDim = dimCode; }
			case 7 -> bounceAt = now;
			case 8 -> penaltyAt = now;
			case 9 -> endedAt = now;
			case 10 -> { depositAt = now; depositKind = 3; }
			case 11 -> takenAt = now;
			case 12 -> sealAt = now;
			case 13 -> stormAt = now;
			default -> { }
		}
	}

	/**
	 * Remaining expedition charge, interpolated between server updates. At most 0.6 s ahead, so the timer freezes
	 * with the server (singleplayer pause).
	 */
	public static double liveRun() {
		double elapsed = Math.min(0.6, (System.currentTimeMillis() - syncMs) / 1000.0);
		return Math.max(0, runCharge - elapsed * drainX100 / 100.0);
	}

	/** Back to defaults when leaving a world. */
	public static void reset() {
		stateLevel = null;
		nearNether = -1;
		nearEnd = -1;
		runCharge = 0;
		runMax = 0;
		dimCode = 0;
		drainX100 = 100;
		expeditions = 0;
		collapses = 0;
		longestRun = 0;
		runSecs = 0;
		marks = new ArrayList<>();
		SealedPockets.client = 0;
		Dwindling.client = 0;
		Dwindling.clientValue = 0;
		settingsJson = null;
		settingsEditable = false;
		guide = com.extremedimensions.guide.Guide.STEPS;
		if (remoteSettings) {
			// Back to this computer's own config after a server's
			remoteSettings = false;
			com.extremedimensions.config.Config.load();
		}
		scars = 0;
		tetherDist = -1;
		trial = "";
		shownNether = 0;
		shownEnd = 0;
		startAt = safeAt = collapseAt = bounceAt = penaltyAt = endedAt = homeAt = takenAt = sealAt = stormAt = 0;
		netherFlashAt = endFlashAt = netherGainAt = endGainAt = depositAt = 0;
	}
}
