package net.battlehub.queue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.battlehub.config.ConfigManager;
import net.battlehub.core.DuelFormat;
import net.battlehub.core.GameMode;
import net.battlehub.gui.Icons;
import net.battlehub.lang.Lang;
import net.battlehub.lobby.HubItems;
import net.battlehub.match.DuelMatch;
import net.battlehub.match.Match;
import net.battlehub.session.Sessions;
import net.battlehub.util.Msg;
import net.battlehub.util.Tasks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * After a match: one click (chat button or the item in the first hotbar slot) to play the same thing again: the
 * same queue (mode, format, ranked or not), a rematch after a challenge, or the same party game lobby.
 */
public final class PlayAgain {
	private PlayAgain() {}

	public enum Kind { QUEUE, REMATCH, PARTY }

	public record Offer(Kind kind, GameMode mode, boolean ranked, DuelFormat format, UUID opponent, String opponentName,
			String vote, long expires) {}

	private static final Map<UUID, Offer> OFFERS = new HashMap<>();

	/** Offers the player another round of what they just played. */
	public static void offer(ServerPlayer p, Match m) {
		int secs = ConfigManager.config().timings.playAgainSeconds;
		if (secs <= 0) {
			return;
		}
		long expires = Tasks.now() + secs * 20L;
		Offer o;
		if (m instanceof DuelMatch d) {
			UUID opponent = null;
			for (UUID id : m.participants()) {
				if (!id.equals(p.getUUID())) opponent = id;
			}
			o = d.challenge && opponent != null
					? new Offer(Kind.REMATCH, m.mode, false, d.format, opponent, m.name(opponent), "", expires)
					: new Offer(Kind.QUEUE, m.mode, m.ranked, d.format, null, "", "", expires);
		} else if (m instanceof net.battlehub.match.TeamFightMatch t) {
			// The same 1v2 / 1v3 queue: same kit, same side as last time
			o = new Offer(Kind.QUEUE, m.mode, false, DuelFormat.BO1, null, "", t.kitMode().id(), expires);
		} else {
			o = new Offer(Kind.PARTY, m.mode, false, DuelFormat.BO1, null, "", Parties.lastVote(p.getUUID(), m.mode), expires);
		}
		OFFERS.put(p.getUUID(), o);
		// A "play again" item from an earlier offer may be back with the restored inventory: replace it.
		if (HubItems.AGAIN.equals(net.battlehub.util.Icon.tagOf(p.getInventory().getItem(HubItems.AGAIN_SLOT)))) {
			p.getInventory().setItem(HubItems.AGAIN_SLOT, net.minecraft.world.item.ItemStack.EMPTY);
		}
		String button = o.kind() == Kind.REMATCH ? "again.rematch_button" : "again.button";
		Msg.raw(p, Lang.tr(p, "again.prompt", label(p, o)).append(" ")
				.append(Msg.button(p, button, "again.hover", "/battlehub again", label(p, o))));
		HubItems.ensure(p, false);
	}

	public static Offer get(UUID id) {
		Offer o = OFFERS.get(id);
		if (o != null && Tasks.now() > o.expires()) {
			OFFERS.remove(id);
			return null;
		}
		return o;
	}

	public static boolean active(UUID id) {
		return get(id) != null;
	}

	public static void forget(UUID id) {
		OFFERS.remove(id);
	}

	/** "Sword PvP (Best of 3)", "Ranked Bow PvP", "Axe PvP vs Stijn" or "TNT Run". */
	public static Component label(ServerPlayer p, Offer o) {
		if (o == null) {
			return Component.empty();
		}
		return switch (o.kind()) {
			case QUEUE -> o.mode().isTeamFight() ? TeamFights.label(p, o.mode(), GameMode.byId(o.vote()))
					: o.ranked() ? Lang.tr(p, "again.ranked_label", Queues.modeLabel(p, o.mode(), o.format()))
					: Queues.modeLabel(p, o.mode(), o.format());
			case REMATCH -> Lang.tr(p, "again.rematch_label", Queues.modeLabel(p, o.mode(), o.format()), o.opponentName());
			case PARTY -> Lang.tr(p, Icons.modeKey(o.mode()));
		};
	}

	/** The button or item was used. */
	public static boolean accept(ServerPlayer p) {
		Offer o = get(p.getUUID());
		if (o == null) {
			Msg.send(p, "again.none");
			return false;
		}
		if (!Sessions.inLobby(p.getUUID())) {
			Msg.send(p, "queue.in_match");
			return false;
		}
		OFFERS.remove(p.getUUID());
		boolean ok = switch (o.kind()) {
			case QUEUE -> o.mode().isTeamFight()
					? TeamFights.join(p, o.mode(), GameMode.byId(o.vote()), TeamFights.lastSide(p.getUUID()))
					: Queues.join(p, o.mode(), o.ranked(), o.format());
			case PARTY -> Parties.join(p, o.mode(), o.vote());
			case REMATCH -> rematch(p, o);
		};
		HubItems.ensure(p, Sessions.state(p.getUUID()) == Sessions.State.QUEUED);
		return ok;
	}

	private static boolean rematch(ServerPlayer p, Offer o) {
		ServerPlayer opponent = p.level().getServer().getPlayerList().getPlayer(o.opponent());
		if (opponent == null) {
			Msg.send(p, "again.opponent_gone", o.opponentName());
			return false;
		}
		// When the opponent already asked for a rematch, clicking accepts it.
		if (Challenges.hasPending(o.opponent(), p.getUUID())) {
			return Challenges.accept(p, opponent);
		}
		Challenges.send(p, opponent, o.mode(), o.format());
		return true;
	}

	/** Once a second: expired offers lose their item. */
	public static void tick(MinecraftServer server) {
		for (UUID id : new ArrayList<>(OFFERS.keySet())) {
			if (get(id) == null) {
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				if (p != null && net.battlehub.hub.HubWorld.is(p.level()) && Sessions.inLobby(id)) {
					HubItems.ensure(p, Sessions.state(id) == Sessions.State.QUEUED);
				}
			}
		}
	}

	public static void clear() {
		OFFERS.clear();
	}
}
