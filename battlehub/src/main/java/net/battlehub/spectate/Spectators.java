package net.battlehub.spectate;

import net.battlehub.data.Snapshots;
import net.battlehub.lang.Lang;
import net.battlehub.lobby.HubItems;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.queue.Queues;
import net.battlehub.session.Sessions;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.GameType;

/**
 * Watching a match in spectator mode, kept inside the arena. Spectators cannot influence anything and are not
 * players of the match. They leave with /hub or the clickable [Leave] in chat (spectator mode hides the hotbar,
 * so an item cannot be used), and return to the lobby automatically when the match ends.
 */
public final class Spectators {
	private Spectators() {}

	private static int tick;

	public static boolean join(ServerPlayer p, Match m) {
		Sessions.State st = Sessions.state(p.getUUID());
		if (st == Sessions.State.MATCH) {
			Msg.send(p, "spectate.in_match");
			return false;
		}
		if (m == null || !m.canWatch() || m.participants().contains(p.getUUID())) {
			Msg.send(p, "spectate.unavailable");
			return false;
		}
		if (!net.battlehub.hub.Travel.ensureInHub(p)) {
			return false;
		}
		if (st == Sessions.State.SPECTATING) {
			Match current = Matches.of(p.getUUID());
			if (current == m) {
				return true;
			}
			if (current != null) {
				current.removeWatcher(p.getUUID());
			}
		} else {
			Queues.leaveAll(p);
			p.closeContainer();
			Snapshots.take(p);
		}
		Sessions.toSpectating(p.getUUID(), m.id);
		p.getInventory().clearContent();
		p.setGameMode(GameType.SPECTATOR);
		m.addWatcher(p);
		Msg.send(p, "spectate.joined", m.describe(p), m.status(p));
		Msg.raw(p, leaveButton(p));
		Msg.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.4f);
		return true;
	}

	public static Component leaveButton(ServerPlayer p) {
		return Component.empty().append(Component.literal("  ")).append(Lang.tr(p, "spectate.leave_button").withStyle(s -> s
				.withClickEvent(new ClickEvent.RunCommand("/hub"))
				.withHoverEvent(new HoverEvent.ShowText(Lang.tr(p, "spectate.leave_hover")))));
	}

	/** Stops watching. Without teleport (disconnect) the position is restored on the next join. */
	public static void leave(ServerPlayer p, boolean teleport) {
		Match m = Matches.of(p.getUUID());
		if (m != null) {
			m.removeWatcher(p.getUUID());
		}
		Snapshots.restore(p, teleport);
		Sessions.toLobby(p.getUUID());
		if (teleport) {
			HubItems.ensure(p, false);
			Msg.send(p, "spectate.left");
		}
	}

	public static void tick(MinecraftServer server) {
		if (++tick % 40 != 0) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (Sessions.state(p.getUUID()) != Sessions.State.SPECTATING) {
				continue;
			}
			Match m = Matches.of(p.getUUID());
			if (m == null) {
				// The match vanished without sending us home (should not happen): recover.
				leave(p, true);
				continue;
			}
			Msg.actionBar(p, "spectate.actionbar", m.describe(p));
		}
	}
}
