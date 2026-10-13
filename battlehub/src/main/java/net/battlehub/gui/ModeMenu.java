package net.battlehub.gui;

import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.match.Matches;
import net.battlehub.queue.Queues;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** One duel mode: Queue (random opponent) or Challenge (pick a player). */
public final class ModeMenu extends Screen {
	private final GameMode mode;
	private final Screen parent;

	public ModeMenu(ServerPlayer viewer, GameMode mode, Screen parent) {
		super(viewer);
		this.mode = mode;
		this.parent = parent;
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
		boolean queued = Queues.isQueued(viewer.getUUID(), mode, false);
		c.set(4, Icon.of(Icons.mode(mode)).name(Lang.item(viewer, Icons.modeKey(mode)))
				.lore(Lang.loreLines(viewer, "mode." + mode.id() + ".desc"))
				.lore(Component.empty())
				.lore(Lang.lore(viewer, "menu.mode.queued", Queues.waiting(mode, false)))
				.lore(Lang.lore(viewer, "menu.mode.playing", Matches.playing(mode))).clean().build());
		if (queued) {
			c.set(11, Icon.of(Items.BARRIER).name(Lang.item(viewer, "menu.mode.leave_queue"))
					.lore(Lang.loreLines(viewer, "menu.mode.leave_queue.lore")).build(),
					click -> {
						Queues.toggle(viewer, mode, false);
						new ModeMenu(viewer, mode, parent).open();
					});
		} else {
			c.set(11, Icon.of(Items.CLOCK).name(Lang.item(viewer, "menu.mode.queue"))
					.lore(Lang.loreLines(viewer, "menu.mode.queue.lore")).build(),
					click -> {
						Queues.toggle(viewer, mode, false);
						viewer.closeContainer();
					});
		}
		c.set(15, Icon.of(Items.NAME_TAG).name(Lang.item(viewer, "menu.mode.challenge"))
				.lore(Lang.loreLines(viewer, "menu.mode.challenge.lore")).build(),
				click -> Queues.challengeMenu(viewer, mode));
		c.back(() -> parent.open());
		c.fill();
	}
}
