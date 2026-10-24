package net.battlehub.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** One chest menu page. {@link #draw(Canvas)} is called on open and on every refresh. */
public abstract class Screen {
	protected final ServerPlayer viewer;

	protected Screen(ServerPlayer viewer) {
		this.viewer = viewer;
	}

	public abstract Component title();

	/** 1 to 6 rows of 9 slots. */
	public abstract int rows();

	public abstract void draw(Canvas c);

	/** The part of BattleHub this menu belongs to: the colour of its top and bottom rows. */
	public Style.Section section() {
		return Style.Section.NONE;
	}

	/** Redraw every N ticks while open; 0 never. */
	public int refreshTicks() {
		return 0;
	}

	public ServerPlayer viewer() {
		return viewer;
	}

	public final void open() {
		Menus.open(viewer, this);
	}
}
