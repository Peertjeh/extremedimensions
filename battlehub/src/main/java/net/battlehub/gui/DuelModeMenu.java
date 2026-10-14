package net.battlehub.gui;

import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.queue.Challenges;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** "/duel <player>": pick the mode for the challenge. */
public final class DuelModeMenu extends Screen {
	private final ServerPlayer target;

	public DuelModeMenu(ServerPlayer viewer, ServerPlayer target) {
		super(viewer);
		this.target = target;
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.duelmode.title", target.getGameProfile().name());
	}

	@Override
	public int rows() {
		return 3;
	}

	@Override
	public void draw(Canvas c) {
		int[] slots = {11, 12, 13, 14, 15};
		GameMode[] modes = GameMode.duels();
		for (int i = 0; i < modes.length; i++) {
			GameMode m = modes[i];
			c.set(slots[i], Icon.of(Icons.mode(m)).name(Lang.item(viewer, Icons.modeKey(m)))
					.lore(Lang.loreLines(viewer, "mode." + m.id() + ".desc"))
					.lore(Component.empty())
					.lore(Lang.lore(viewer, "menu.duelmode.click", target.getGameProfile().name())).clean().build(),
					click -> {
						viewer.closeContainer();
						Challenges.send(viewer, target, m);
					});
		}
		c.set(22, FormatButton.icon(viewer), click -> FormatButton.toggle(viewer, () -> new DuelModeMenu(viewer, target).open()));
		c.fill();
	}
}
