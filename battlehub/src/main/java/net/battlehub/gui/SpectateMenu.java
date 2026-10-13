package net.battlehub.gui;

import java.util.List;

import net.battlehub.lang.Lang;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.spectate.Spectators;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Every active match with a [Join Spectator] button. Refreshes every second while open. */
public final class SpectateMenu extends Screen {
	private final int page;

	public SpectateMenu(ServerPlayer viewer) {
		this(viewer, 0);
	}

	public SpectateMenu(ServerPlayer viewer, int page) {
		super(viewer);
		this.page = page;
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
		List<Match> matches = Matches.watchable();
		matches.removeIf(m -> m.participants().contains(viewer.getUUID()));
		int[] slots = c.inner();
		int pages = Math.max(1, (matches.size() + slots.length - 1) / slots.length);
		int pg = Math.max(0, Math.min(page, pages - 1));
		if (matches.isEmpty()) {
			c.set(22, Icon.of(Items.BARRIER).name(Lang.item(viewer, "menu.spectate.none"))
					.lore(Lang.loreLines(viewer, "menu.spectate.none.lore")).build());
		}
		for (int i = 0; i < slots.length; i++) {
			int idx = pg * slots.length + i;
			if (idx >= matches.size()) break;
			Match m = matches.get(idx);
			int secs = m.runSeconds();
			Icon icon = Icon.of(Icons.mode(m.mode)).name(Component.empty().withStyle(s -> s.withItalic(false)).append(m.describe(viewer)))
					.lore(Lang.lore(viewer, "menu.spectate.mode", m.status(viewer)))
					.lore(Lang.lore(viewer, "menu.spectate.alive", m.alive().size(), m.participants().size()))
					.lore(Lang.lore(viewer, "menu.spectate.arena", Lang.tr(viewer, "theme." + m.arena().theme().id())))
					.lore(Lang.lore(viewer, m.phase() == Match.Phase.COUNTDOWN ? "menu.spectate.starting" : "menu.spectate.time",
							String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60)))
					.lore(Lang.lore(viewer, "menu.spectate.watchers", m.watchers().size()))
					.lore(Component.empty())
					.lore(Lang.lore(viewer, "menu.spectate.join"))
					.clean();
			c.set(slots[i], icon.build(), click -> {
				viewer.closeContainer();
				Spectators.join(viewer, m);
			});
		}
		if (pg > 0) {
			c.set(48, Icon.of(Items.ARROW).name(Lang.item(viewer, "menu.page.previous")).build(), click -> new SpectateMenu(viewer, pg - 1).open());
		}
		if (pg < pages - 1) {
			c.set(50, Icon.of(Items.ARROW).name(Lang.item(viewer, "menu.page.next")).build(), click -> new SpectateMenu(viewer, pg + 1).open());
		}
		c.back(() -> new MainMenu(viewer).open());
		c.border();
	}
}
