package net.battlehub.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Splits players over teams of (nearly) equal size, keeping friends who queued together as a party on the same
 * team when there is room. Larger parties first; a party bigger than a team is split.
 */
public final class TeamSplit {
	private TeamSplit() {}

	public static List<List<UUID>> split(List<UUID> players, int teamCount, Collection<Set<UUID>> parties, Random rnd) {
		int n = Math.max(1, teamCount);
		List<List<UUID>> teams = new ArrayList<>();
		for (int i = 0; i < n; i++) teams.add(new ArrayList<>());
		int cap = (players.size() + n - 1) / n;
		List<UUID> left = new ArrayList<>(players);
		java.util.Collections.shuffle(left, rnd);
		List<List<UUID>> groups = new ArrayList<>();
		for (Set<UUID> party : parties) {
			List<UUID> g = new ArrayList<>();
			for (UUID id : left) {
				if (party.contains(id)) g.add(id);
			}
			if (g.size() >= 2) {
				groups.add(g);
				left.removeAll(g);
			}
		}
		groups.sort((a, b) -> b.size() - a.size());
		for (List<UUID> g : groups) {
			for (UUID id : g) {
				List<UUID> best = null;
				// The team that already has this party's players and still has room, else the emptiest
				for (List<UUID> t : teams) {
					if (t.size() < cap && !java.util.Collections.disjoint(t, g)) {
						best = t;
						break;
					}
				}
				if (best == null) best = emptiest(teams);
				best.add(id);
			}
		}
		for (UUID id : left) {
			emptiest(teams).add(id);
		}
		return teams;
	}

	private static List<UUID> emptiest(List<List<UUID>> teams) {
		List<UUID> best = teams.get(0);
		for (List<UUID> t : teams) {
			if (t.size() < best.size()) best = t;
		}
		return best;
	}

	/** Two teams as one list, alternating (first of team 0, first of team 1, ...), the bigger team first. */
	public static List<UUID> interleave(List<List<UUID>> two) {
		List<UUID> a = two.get(0);
		List<UUID> b = two.size() > 1 ? two.get(1) : List.of();
		if (b.size() > a.size()) {
			List<UUID> t = a;
			a = b;
			b = t;
		}
		List<UUID> out = new ArrayList<>();
		for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
			if (i < a.size()) out.add(a.get(i));
			if (i < b.size()) out.add(b.get(i));
		}
		return out;
	}

	/** A random code of letters that are hard to mix up (no I, L, O). */
	public static String code(Random rnd, int length) {
		String letters = "ABCDEFGHJKMNPQRSTUVWXYZ";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) sb.append(letters.charAt(rnd.nextInt(letters.length())));
		return sb.toString();
	}
}
