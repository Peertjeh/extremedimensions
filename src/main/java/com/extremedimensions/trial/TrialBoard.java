package com.extremedimensions.trial;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The world's scoreboard: each player's best run of this week's trial, and the best three of the last one. */
public class TrialBoard {
	public int week = Integer.MIN_VALUE;
	/** Player UUID -> best run this week. */
	public Map<String, Entry> scores = new HashMap<>();
	public int lastWeek = Integer.MIN_VALUE;
	public String lastKind = "";
	public List<Entry> lastTop = new ArrayList<>();

	public static class Entry {
		public String name;
		public int score;
		/** Seconds left in the Seam on the way out (the tie-break). */
		public int left;
		public int medal;

		public Entry() {}

		public Entry(String name, int score, int left, int medal) {
			this.name = name;
			this.score = score;
			this.left = left;
			this.medal = medal;
		}

		/** Better than another run: a higher score, or the same with more time to spare. */
		public boolean beats(Entry other) {
			return other == null || score > other.score || (score == other.score && left > other.left);
		}
	}

	/** Best first. */
	public List<Entry> ranked() {
		List<Entry> out = new ArrayList<>(scores.values());
		out.sort(Comparator.comparingInt((Entry e) -> -e.score).thenComparingInt(e -> -e.left).thenComparing(e -> e.name == null ? "" : e.name));
		return out;
	}
}
