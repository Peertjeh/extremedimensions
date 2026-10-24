package net.battlehub.gui;

import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.match.Matches;
import net.battlehub.queue.Queues;
import net.battlehub.queue.TeamFights;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** PvP: Free for All, 1v1, 1v2 or 1v3; then the kit (sword, axe or mace). */
public final class PvpMenu extends Screen {
	public PvpMenu(ServerPlayer viewer) {
		super(viewer);
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.pvp.title");
	}

	@Override
	public Style.Section section() {
		return Style.Section.PLAY;
	}

	@Override
	public int rows() {
		return 3;
	}

	@Override
	public int refreshTicks() {
		return 20;
	}

	static int waitingTeamFights(GameMode mode) {
		int n = 0;
		for (GameMode kit : GameMode.meleeKits()) {
			n += TeamFights.waiting(mode, kit);
		}
		return n;
	}

	@Override
	public void draw(Canvas c) {
		c.set(10, Icon.of(Items.IRON_SWORD).name(Lang.item(viewer, Icons.modeKey(GameMode.FFA)))
				.lore(Lang.loreLines(viewer, "menu.pvp.ffa.lore"))
				.lore(Component.empty())
				.lore(Lang.lore(viewer, "menu.pvp.waiting", Queues.partyCount(GameMode.FFA)))
				.lore(Lang.lore(viewer, "menu.mode.playing", Matches.playing(GameMode.FFA)))
				.glint(Queues.inParty(viewer.getUUID(), GameMode.FFA)).clean().build(),
				click -> new PvpKitMenu(viewer, GameMode.FFA).open());
		int duelWaiting = 0;
		int duelPlaying = 0;
		for (GameMode kit : GameMode.meleeKits()) {
			duelWaiting += Queues.waiting(kit, false, Queues.formatOf(viewer));
			duelPlaying += Matches.playing(kit);
		}
		c.set(12, Icon.of(Items.DIAMOND_SWORD).name(Lang.item(viewer, "menu.pvp.1v1"))
				.lore(Lang.loreLines(viewer, "menu.pvp.1v1.lore"))
				.lore(Component.empty())
				.lore(Lang.lore(viewer, "menu.pvp.waiting", duelWaiting))
				.lore(Lang.lore(viewer, "menu.mode.playing", duelPlaying)).clean().build(),
				click -> new ModeListMenu(viewer, ModeListMenu.Kind.MELEE).open());
		int slot = 14;
		for (GameMode mode : GameMode.teamFights()) {
			c.set(slot, Icon.of(Items.PLAYER_HEAD).count(mode.opponents() + 1).name(Lang.item(viewer, Icons.modeKey(mode)))
					.lore(Lang.loreLines(viewer, "mode." + mode.id() + ".desc"))
					.lore(Component.empty())
					.lore(Lang.lore(viewer, "menu.pvp.waiting", waitingTeamFights(mode)))
					.lore(Lang.lore(viewer, "menu.mode.playing", Matches.playing(mode))).clean().build(),
					click -> new PvpKitMenu(viewer, mode).open());
			slot += 2;
		}
		Style.title(c, viewer, Items.DIAMOND_SWORD, "menu.main.pvp");
		c.back(() -> new MainMenu(viewer).open());
		c.fill();
	}
}
