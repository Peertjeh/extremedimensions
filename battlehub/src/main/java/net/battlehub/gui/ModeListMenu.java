package net.battlehub.gui;

import java.util.List;

import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.match.Matches;
import net.battlehub.queue.Queues;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Duels (Sword, Axe, Mace) or Ranged (Bow, Crossbow). */
public final class ModeListMenu extends Screen {
	public enum Kind { MELEE, RANGED }

	private final Kind kind;

	public ModeListMenu(ServerPlayer viewer, Kind kind) {
		super(viewer);
		this.kind = kind;
	}

	private List<GameMode> modes() {
		return kind == Kind.MELEE ? List.of(GameMode.SWORD, GameMode.AXE, GameMode.MACE) : List.of(GameMode.BOW, GameMode.CROSSBOW);
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, kind == Kind.MELEE ? "menu.duels.title" : "menu.ranged.title");
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
		List<GameMode> modes = modes();
		int[] slots = modes.size() == 3 ? new int[] {11, 13, 15} : new int[] {11, 15};
		for (int i = 0; i < modes.size(); i++) {
			GameMode m = modes.get(i);
			Icon icon = Icon.of(Icons.mode(m)).name(Lang.item(viewer, Icons.modeKey(m)))
					.lore(Lang.loreLines(viewer, "mode." + m.id() + ".desc"))
					.lore(Component.empty())
					.lore(Lang.lore(viewer, "menu.mode.queued", Queues.waiting(m, false)))
					.lore(Lang.lore(viewer, "menu.mode.playing", Matches.playing(m)))
					.lore(Component.empty())
					.lore(Lang.lore(viewer, "menu.click_to_open"))
					.glint(Queues.isQueued(viewer.getUUID(), m, false))
					.clean();
			c.set(slots[i], icon.build(), click -> new ModeMenu(viewer, m, this).open());
		}
		c.back(() -> new MainMenu(viewer).open());
		c.fill();
	}
}
