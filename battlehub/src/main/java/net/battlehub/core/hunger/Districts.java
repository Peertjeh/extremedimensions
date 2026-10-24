package net.battlehub.core.hunger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Tributes, their districts and training scores (without Minecraft). Two tributes per district, the districts
 * numbered from 1, handed out at random; a training score of 1 to 12 from what a player has done before, with some
 * luck, as the Gamemakers judge.
 */
public final class Districts {
	private Districts() {}

	public static final int MAX_DISTRICT = 12;

	/** The district of each of {@code n} tributes, in their order: two per district, shuffled. */
	public static List<Integer> assign(int n, Random rng) {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < n; i++) out.add(Math.min(MAX_DISTRICT, i / 2 + 1));
		Collections.shuffle(out, rng);
		return out;
	}

	/**
	 * A person's training score: Hunger Games wins count most, then kills in all games and the level, plus a roll of
	 * -2 to +2; always 1 to 12. A newcomer gets about 4.
	 */
	public static int trainingScore(int hgWins, int kills, int level, Random rng) {
		double s = 4 + Math.min(4, hgWins * 1.0) + Math.min(2, kills / 50.0) + Math.min(2, level / 25.0);
		return clamp((int) Math.round(s) + rng.nextInt(5) - 2);
	}

	/** A bot's training score from its level (0 beginner .. 5 master), with a roll of -1 to +1. */
	public static int botScore(int level, Random rng) {
		return clamp(2 + level * 2 + rng.nextInt(3) - 1);
	}

	private static int clamp(int s) {
		return Math.max(1, Math.min(12, s));
	}
}
