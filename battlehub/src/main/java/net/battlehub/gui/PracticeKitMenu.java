package net.battlehub.gui;

import net.battlehub.bot.Bots;
import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The kit for practising 1v2 or 1v3 against bots: sword, axe or mace (the same choice as in the PvP tab). */
public final class PracticeKitMenu extends Screen {
	private final GameMode mode;

	public PracticeKitMenu(ServerPlayer viewer, GameMode mode) {
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
	public void draw(Canvas c) {
		int slot = 11;
		for (GameMode kit : GameMode.meleeKits()) {
			c.set(slot, Icon.of(Icons.mode(kit)).name(Lang.item(viewer, Icons.modeKey(kit))).lore(Lang.loreLines(viewer, "mode." + kit.id() + ".desc"))
					.lore(Component.empty())
					.lore(Lang.lore(viewer, "menu.practice.team", mode.opponents(),
							Lang.tr(viewer, "bots.level." + PracticeMenu.practiceLevel(viewer).id())))
					.lore(Lang.lore(viewer, "menu.practice.click")).clean().build(), click -> {
						viewer.closeContainer();
						Bots.practiceTeam(viewer, mode, kit, PracticeMenu.practiceLevel(viewer));
					});
			slot += 2;
		}
		c.back(() -> new PracticeMenu(viewer).open());
		c.fill();
	}
}
