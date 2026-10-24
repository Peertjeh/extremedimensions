package net.battlehub.gui;

import java.util.HashMap;
import java.util.Map;

import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.queue.Parties;
import net.battlehub.queue.Queues;
import net.battlehub.queue.TeamFights;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * The kit for Free for All, 1v2 or 1v3. Free for All: a click joins the lobby with a vote for that kit (the most
 * votes decide). 1v2 / 1v3: a click opens the choice of side.
 */
public final class PvpKitMenu extends Screen {
	private final GameMode mode;

	public PvpKitMenu(ServerPlayer viewer, GameMode mode) {
		super(viewer);
		this.mode = mode;
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.pvp.kit_title", Lang.tr(viewer, Icons.modeKey(mode)));
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

	@Override
	public void draw(Canvas c) {
		boolean ffa = mode == GameMode.FFA;
		Map<String, Integer> votes = new HashMap<>();
		for (String v : Parties.votes(GameMode.FFA, "kit")) {
			votes.merge(v, 1, Integer::sum);
		}
		String mine = Parties.vote(viewer.getUUID(), GameMode.FFA, "kit");
		boolean inFfa = Queues.inParty(viewer.getUUID(), GameMode.FFA);
		int slot = 11;
		for (GameMode kit : GameMode.meleeKits()) {
			Icon icon = Icon.of(Icons.mode(kit)).name(Lang.item(viewer, Icons.modeKey(kit))).lore(Lang.loreLines(viewer, "mode." + kit.id() + ".desc"))
					.lore(Component.empty());
			if (ffa) {
				boolean chosen = inFfa && kit.id().equals(mine);
				icon.lore(Lang.lore(viewer, "menu.parkour.votes", votes.getOrDefault(kit.id(), 0)))
						.lore(Lang.lore(viewer, chosen ? "menu.parkour.voted" : "menu.parkour.click_vote")).glint(chosen);
				c.set(slot, icon.clean().build(), click -> {
					Parties.join(viewer, GameMode.FFA, "kit:" + kit.id());
					new PvpKitMenu(viewer, mode).open();
				});
			} else {
				icon.lore(Lang.lore(viewer, "menu.pvp.waiting", TeamFights.waiting(mode, kit)))
						.lore(Lang.lore(viewer, "menu.click_to_open")).glint(TeamFights.sideOf(viewer.getUUID(), mode, kit) != null);
				c.set(slot, icon.clean().build(), click -> new TeamFightMenu(viewer, mode, kit).open());
			}
			slot += 2;
		}
		c.back(() -> new PvpMenu(viewer).open());
		if (ffa) {
			c.set(4, PartyGamesMenu.icon(viewer, GameMode.FFA).build());
			if (inFfa) {
				c.set(net.battlehub.core.MenuLayout.extra(rows()), Icon.of(Items.BARRIER).name(Lang.item(viewer, "menu.party.leave"))
						.lore(Lang.loreLines(viewer, "menu.party.leave.lore")).build(), click -> {
							Queues.toggleParty(viewer, GameMode.FFA);
							new PvpKitMenu(viewer, mode).open();
						});
			}
		}
		c.fill();
	}
}
