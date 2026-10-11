package com.extremedimensions.client;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.Penalty;

/** De laatste status die de server ons stuurde, plus meldingen voor in beeld. */
public final class ClientState {
	private ClientState() {}

	public static int nether = 0;
	public static int end = 0;
	/** 0 = Overworld/overig, 1 = Nether, 2 = End */
	public static int dimCode = 0;
	public static int drainX100 = 100;
	public static int expeditions = 0;
	public static int collapses = 0;
	public static int longestRun = 0;
	public static int runSecs = 0;
	public static String penalties = "";
	public static long syncMs = 0;

	public static String noticeText = "";
	public static int noticeColor = 0xFFFFFFFF;
	public static long noticeUntil = 0;

	public static long noticeAt = 0;

	// --- animatie-status ---
	public static double shownNether = 0;
	public static double shownEnd = 0;
	private static long lastFrame = 0;
	public static long startAt = 0;
	public static long safeAt = 0;
	public static long collapseAt = 0;
	public static long bounceAt = 0;
	public static long penaltyAt = 0;
	public static long netherFlashAt = 0;
	public static long endFlashAt = 0;
	public static long netherGainAt = 0;
	public static long endGainAt = 0;
	public static int netherGain = 0;
	public static int endGain = 0;

	public static void notice(String text, int color) {
		noticeText = text;
		noticeColor = color;
		noticeAt = System.currentTimeMillis();
		noticeUntil = noticeAt + 4500;
	}

	public static double clamp01(double v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}

	public static double ease(double t) {
		double c = 1 - clamp01(t);
		return 1 - c * c * c;
	}

	public static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	public static double hash(long n) {
		long x = n * 0x9E3779B97F4A7C15L;
		x ^= x >>> 32;
		x *= 0xD6E8FEB86659FD93L;
		x ^= x >>> 32;
		return (x & 0xFFFFFF) / (double) 0x1000000;
	}

	/** Elke frame aanroepen: laat de getoonde tanks soepel meebewegen met de echte waarde. */
	public static void tickSmooth() {
		long now = System.currentTimeMillis();
		double dt = lastFrame == 0 ? 0.016 : Math.min(0.25, (now - lastFrame) / 1000.0);
		lastFrame = now;
		double k = 1 - Math.exp(-dt * 7.0);
		shownNether += (live(1) - shownNether) * k;
		shownEnd += (live(2) - shownEnd) * k;
	}

	/** Verwerkt een animatie-opdracht van de server (zie Fx). */
	public static void onFx(int type, int value) {
		long now = System.currentTimeMillis();
		switch (type) {
			case 1 -> { netherFlashAt = now; netherGainAt = now; netherGain = value; }
			case 2 -> { endFlashAt = now; endGainAt = now; endGain = value; }
			case 3, 4 -> startAt = now;
			case 5 -> safeAt = now;
			case 6 -> collapseAt = now;
			case 7 -> bounceAt = now;
			case 8 -> penaltyAt = now;
			default -> { }
		}
	}

	/** Charge van een dimensie (1 = Nether, 2 = End), soepel doorgeteld tussen server-updates. */
	public static double live(int code) {
		double base = code == 1 ? nether : end;
		if (dimCode == code) {
			double elapsed = (System.currentTimeMillis() - syncMs) / 1000.0;
			base = Math.max(0, base - elapsed * drainX100 / 100.0);
		}
		return base;
	}

	public static String time(double seconds) {
		int s = (int) Math.ceil(seconds);
		return String.format("%d:%02d", s / 60, s % 60);
	}

	/** Actieve penalty's als leesbare regels, bijv. "Schuld (2 runs)". */
	public static List<String> penaltyLines() {
		List<String> out = new ArrayList<>();
		if (penalties == null || penalties.isEmpty()) {
			return out;
		}
		for (String part : penalties.split(";")) {
			String[] kv = part.split(":");
			Penalty p = Penalty.byId(kv[0]);
			String runs = kv.length > 1 ? kv[1] : "?";
			out.add((p != null ? p.title : kv[0]) + " (" + runs + " runs)");
		}
		return out;
	}
}
