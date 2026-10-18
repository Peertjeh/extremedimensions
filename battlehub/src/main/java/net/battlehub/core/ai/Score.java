package net.battlehub.core.ai;

import java.util.List;

/** How good a tactic looks right now, with the reasons (for the diagnostics). */
public record Score(Tactic tactic, double value, boolean critical, List<String> why) {
	@Override
	public String toString() {
		return tactic.name().toLowerCase(java.util.Locale.ROOT) + String.format(java.util.Locale.ROOT, " %.2f", value)
				+ (critical ? "!" : "") + (why.isEmpty() ? "" : " (" + String.join(", ", why) + ")");
	}
}
