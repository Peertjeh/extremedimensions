package net.battlehub.gui;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Opening menus and refreshing the live ones (Spectate list, queue counters). */
public final class Menus {
	private Menus() {}

	private static int tick;

	public static void open(ServerPlayer p, Screen screen) {
		p.openMenu(new SimpleMenuProvider((id, inv, player) -> HubMenu.create(id, inv, screen), screen.title()));
	}

	public static void tick(MinecraftServer server) {
		tick++;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.containerMenu instanceof HubMenu m) {
				MenuGuard.sweep(p);
				int every = m.screen().refreshTicks();
				if (every > 0 && tick % every == 0) {
					m.redraw();
				}
			}
		}
	}

	/** Redraws every open BattleHub menu now (after queues or matches changed). */
	public static void refreshAll(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.containerMenu instanceof HubMenu m && m.screen().refreshTicks() > 0) {
				m.redraw();
			}
		}
	}
}
