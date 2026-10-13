package net.battlehub.queue;

import net.battlehub.core.GameMode;
import net.battlehub.util.Msg;
import net.minecraft.server.level.ServerPlayer;

/** Challenges between two players. (Phase 2 placeholder; arrives in phase 3.) */
public final class Challenges {
	private Challenges() {}

	static void openPicker(ServerPlayer p, GameMode mode) {
		Msg.send(p, "general.coming_soon");
	}
}
