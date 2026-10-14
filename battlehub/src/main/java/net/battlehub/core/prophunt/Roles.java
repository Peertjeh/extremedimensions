package net.battlehub.core.prophunt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Who hunts in a Prop Hunt round. At least one hunter and one prop; players who hunted less often go first, so
 * over several rounds everyone gets a turn.
 */
public final class Roles {
	private Roles() {}

	public static int hunterCount(int players, double ratio) {
		if (players < 2) {
			return 0;
		}
		int n = (int) Math.round(players * Math.max(0, Math.min(1, ratio)));
		return Math.max(1, Math.min(players - 1, n));
	}

	/** Picks the hunters for a round; {@code timesHunted} counts earlier rounds per player. */
	public static <T> Set<T> pickHunters(List<T> players, double ratio, java.util.Map<T, Integer> timesHunted, Random random) {
		List<T> order = new ArrayList<>(players);
		Collections.shuffle(order, random);
		order.sort(Comparator.comparingInt(p -> timesHunted.getOrDefault(p, 0)));
		return new HashSet<>(order.subList(0, hunterCount(players.size(), ratio)));
	}
}
