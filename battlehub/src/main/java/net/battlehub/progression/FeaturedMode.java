package net.battlehub.progression;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.Featured;
import net.battlehub.core.GameMode;

/** The mode of the day: more coins there, a star in the menus and on the lobby NPCs. */
public final class FeaturedMode {
	private FeaturedMode() {}

	/** Today's mode, or null when switched off. */
	public static GameMode today() {
		HubConfig.FeaturedMode c = ConfigManager.config().featured;
		if (!c.enabled) {
			return null;
		}
		List<GameMode> pool = new ArrayList<>();
		for (String id : c.modes) {
			GameMode m = GameMode.byId(id);
			if (m != null && ConfigManager.config().game(m).enabled && !pool.contains(m)) {
				pool.add(m);
			}
		}
		return Featured.pick(pool, LocalDate.now().toEpochDay());
	}

	public static boolean is(GameMode mode) {
		return mode != null && mode == today();
	}

	public static double multiplier() {
		return Math.max(1.0, ConfigManager.config().featured.coinMultiplier);
	}

	/** Tells the player which mode pays more today. */
	public static void announce(net.minecraft.server.level.ServerPlayer p) {
		GameMode m = today();
		if (m != null) {
			net.battlehub.util.Msg.send(p, "featured.today", net.battlehub.lang.Lang.tr(p, net.battlehub.gui.Icons.modeKey(m)), multiplierText());
		}
	}

	/** "2" or "1.5". */
	public static String multiplierText() {
		double m = multiplier();
		return m == Math.rint(m) ? String.valueOf((long) m) : String.valueOf(m);
	}
}
