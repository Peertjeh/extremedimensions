package net.battlehub.gui;

import net.battlehub.lang.Lang;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Every active match with a [Join Spectator] button. Refreshes while open. */
public final class SpectateMenu extends Screen {
	public SpectateMenu(ServerPlayer viewer) {
		super(viewer);
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.spectate.title");
	}

	@Override
	public int rows() {
		return 6;
	}

	@Override
	public int refreshTicks() {
		return 20;
	}

	@Override
	public void draw(Canvas c) {
		c.set(22, Icon.of(Items.BARRIER).name(Lang.item(viewer, "menu.spectate.none"))
				.lore(Lang.loreLines(viewer, "menu.spectate.none.lore")).build());
		c.back(() -> new MainMenu(viewer).open());
		c.border();
	}
}
