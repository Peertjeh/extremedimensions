package net.battlehub.gui;

import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.queue.Queues;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** One party game: join or leave its lobby, or invite a friend. */
public final class PartyGameMenu extends Screen {
	private final GameMode mode;

	public PartyGameMenu(ServerPlayer viewer, GameMode mode) {
		super(viewer);
		this.mode = mode;
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.mode.title", Lang.tr(viewer, Icons.modeKey(mode)));
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
		c.set(4, PartyGamesMenu.icon(viewer, mode).build());
		boolean in = Queues.inParty(viewer.getUUID(), mode);
		if (in) {
			c.set(11, Icon.of(Items.BARRIER).name(Lang.item(viewer, "menu.party.leave"))
					.lore(Lang.loreLines(viewer, "menu.party.leave.lore")).build(),
					click -> {
						Queues.toggleParty(viewer, mode);
						new PartyGameMenu(viewer, mode).open();
					});
		} else {
			c.set(11, Icon.of("minecraft:lime_dye").name(Lang.item(viewer, "menu.party.join"))
					.lore(Lang.loreLines(viewer, "menu.party.join.lore")).build(),
					click -> {
						Queues.toggleParty(viewer, mode);
						new PartyGameMenu(viewer, mode).open();
					});
		}
		c.set(15, Icon.of(Items.NAME_TAG).name(Lang.item(viewer, "menu.party.invite"))
				.lore(Lang.loreLines(viewer, "menu.party.invite.lore")).build(),
				click -> Queues.challengeMenu(viewer, mode));
		c.back(() -> new PartyGamesMenu(viewer).open());
		c.fill();
	}
}
