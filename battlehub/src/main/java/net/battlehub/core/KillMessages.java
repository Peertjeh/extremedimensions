package net.battlehub.core;

import java.util.List;

/**
 * Kill messages: your own line in the chat and the killfeed when you defeat someone. The text of each is in the
 * language files under {@code killmsg.<id>} ({0} the defeated, {1} you); its short form for the killfeed under
 * {@code killmsg.<id>.feed}.
 */
public final class KillMessages {
	private KillMessages() {}

	public static final List<String> IDS = List.of("bonk", "shadow", "yeet", "roast", "pixel", "frozen", "storm", "royal",
			"cookie", "deleted", "launched", "spooky", "tea", "dragon", "glitch");

	public static boolean known(String id) {
		return IDS.contains(id);
	}
}
