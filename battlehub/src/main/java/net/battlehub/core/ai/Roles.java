package net.battlehub.core.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who on a team attacks and who stays home. Only the team's NPCs get a role (people do what they like); a team
 * with something to defend and at least two players keeps one NPC home (two from four players up), the one whose
 * personality fits best (defensive, patient, not aggressive), ties broken by who is closest to home and then by
 * id, so the split is stable from one think to the next. An all-NPC team always keeps an attacker.
 */
public final class Roles {
	private Roles() {}

	public enum Role { ATTACKER, DEFENDER }

	/** An NPC on the team: its personality and how far it is from what the team defends. */
	public record Member(UUID id, Personality personality, double fromHome) {}

	public static Map<UUID, Role> assign(List<Member> npcs, int teamSize, boolean somethingToDefend) {
		Map<UUID, Role> out = new HashMap<>();
		for (Member m : npcs) out.put(m.id(), Role.ATTACKER);
		if (!somethingToDefend || teamSize < 2 || npcs.isEmpty()) return out;
		int defenders = teamSize >= 4 ? 2 : 1;
		if (npcs.size() == teamSize) defenders = Math.min(defenders, npcs.size() - 1);
		defenders = Math.min(defenders, npcs.size());
		List<Member> order = new ArrayList<>(npcs);
		order.sort(Comparator.comparingDouble((Member m) -> -fit(m.personality())).thenComparingDouble(Member::fromHome)
				.thenComparing(m -> m.id().toString()));
		for (int i = 0; i < defenders; i++) out.put(order.get(i).id(), Role.DEFENDER);
		return out;
	}

	/** How well a personality suits staying home (rounded, so a tiny difference does not beat being closer). */
	static double fit(Personality p) {
		double f = 0.5 * p.defensive() + 0.3 * p.patience() + 0.2 * (1 - p.aggression());
		return Math.round(f * 20) / 20.0;
	}
}
