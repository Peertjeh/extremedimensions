package net.battlehub.gui;

import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.match.Matches;
import net.battlehub.queue.Queues;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The party games with their lobby status. */
public final class PartyGamesMenu extends Screen {
	public PartyGamesMenu(ServerPlayer viewer) {
		super(viewer);
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.party.title");
	}

	@Override
	public int rows() {
		return 4;
	}

	@Override
	public int refreshTicks() {
		return 20;
	}

	@Override
	public void draw(Canvas c) {
		GameMode[] games = GameMode.parties();
		// Seven on the first row, the rest centred on the row below
		int below = Math.max(0, games.length - 7);
		for (int i = 0; i < games.length; i++) {
			GameMode m = games[i];
			int slot = i < 7 ? 10 + i : 22 - (below - 1) + 2 * (i - 7);
			c.set(slot, icon(viewer, m).build(), click -> new PartyGameMenu(viewer, m).open());
		}
		c.back(() -> new MainMenu(viewer).open());
		c.fill();
	}

	static Icon icon(ServerPlayer viewer, GameMode m) {
		HubConfig.Game g = ConfigManager.config().game(m);
		int secs = Queues.partySecondsLeft(m);
		Icon icon = Icon.of(Icons.mode(m)).name(Lang.item(viewer, Icons.modeKey(m)))
				.lore(Lang.loreLines(viewer, "mode." + m.id() + ".desc"))
				.lore(Component.empty())
				.lore(Lang.lore(viewer, "menu.party.lobby", Queues.partyCount(m), g.maxPlayers, g.minPlayers))
				.lore(secs >= 0 ? Lang.lore(viewer, "menu.party.starting", secs) : Lang.lore(viewer, "menu.party.waiting"))
				.lore(Lang.lore(viewer, "menu.mode.playing", Matches.playing(m)))
				.lore(Component.empty())
				.lore(Lang.lore(viewer, "menu.click_to_open"))
				.glint(Queues.inParty(viewer.getUUID(), m))
				.clean();
		if (!g.enabled) {
			icon.lore(Lang.lore(viewer, "menu.disabled"));
		}
		return icon;
	}
}
