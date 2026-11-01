package net.battlehub.hub;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.battlehub.bot.Bots;
import net.battlehub.config.ConfigManager;
import net.battlehub.mixin.PlayerInfoPacketAccessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The tab list per world (1.52, {@code hub.separateTabList}): a player's tab list shows the players in their own world,
 * BattleHub (hub and arenas) or the normal world, and BattleHub's bots not at all ({@code hub.botsInTabList}). Only
 * the line in the list goes: the client still knows the player and draws them with their skin. Each player info
 * packet that says who is listed is set right per receiver as it is sent (PlayerInfoSendMixin), and a player who goes
 * from one world to the other is sent round again.
 */
public final class TabListWorlds {
	private TabListWorlds() {}

	/** Per player: whether they were in BattleHub at the last look (absent: not seen yet). */
	private static final Map<UUID, Boolean> WHERE = new HashMap<>();
	/** The settings at the last look: when they change, everyone's list is sent anew. */
	private static String settings = "";

	/** Whether {@code viewer}'s tab list shows {@code shown}. */
	public static boolean listed(ServerPlayer viewer, ServerPlayer shown) {
		var hub = ConfigManager.config().hub;
		if (!hub.botsInTabList && Bots.isBot(shown)) return false;
		if (!hub.separateTabList || viewer == shown) return true;
		return HubWorld.isBattleHub(viewer.level()) == HubWorld.isBattleHub(shown.level());
	}

	/**
	 * The packet as {@code viewer} gets it: a player listed who does not belong in their list is unlisted. Nobody is
	 * listed who was not (another mod may have its reasons), and entries of no player on the server (fake ones of TAB
	 * or of NPCs) stay as they are.
	 */
	public static Packet<?> forViewer(ClientboundPlayerInfoUpdatePacket info, ServerPlayer viewer) {
		if (!info.actions().contains(Action.UPDATE_LISTED)) return info;
		MinecraftServer server = viewer.level().getServer();
		if (server == null) return info;
		List<Entry> entries = info.entries();
		List<Entry> out = new ArrayList<>(entries.size());
		boolean changed = false;
		for (Entry e : entries) {
			ServerPlayer shown = e.listed() ? server.getPlayerList().getPlayer(e.profileId()) : null;
			if (shown != null && !listed(viewer, shown)) {
				out.add(new Entry(e.profileId(), e.profile(), false, e.latency(), e.gameMode(), e.displayName(), e.showHat(),
						e.listOrder(), e.chatSession()));
				changed = true;
			} else {
				out.add(e);
			}
		}
		if (!changed) return info;
		ClientboundPlayerInfoUpdatePacket copy = new ClientboundPlayerInfoUpdatePacket(info.actions(), List.<ServerPlayer>of());
		((PlayerInfoPacketAccessor) copy).battlehub$setEntries(out);
		return copy;
	}

	/**
	 * Twice a second: a player who went from one world to the other (BattleHub's door, a portal, a command, a respawn)
	 * gets everyone's line anew, and everyone gets theirs; forViewer decides per receiver. After a change of the
	 * settings everyone is sent round.
	 */
	public static void tick(MinecraftServer server, int tick) {
		if (tick % 10 != 0) return;
		var hub = ConfigManager.config().hub;
		String now = hub.separateTabList + "/" + hub.botsInTabList;
		if (!now.equals(settings)) {
			settings = now;
			WHERE.clear();
		}
		List<ServerPlayer> all = server.getPlayerList().getPlayers();
		Set<UUID> online = new HashSet<>();
		for (ServerPlayer p : all) {
			online.add(p.getUUID());
			boolean in = HubWorld.isBattleHub(p.level());
			Boolean was = WHERE.put(p.getUUID(), in);
			if (was != null && was == in) continue;
			ClientboundPlayerInfoUpdatePacket one = new ClientboundPlayerInfoUpdatePacket(EnumSet.of(Action.UPDATE_LISTED), List.of(p));
			for (ServerPlayer o : all) {
				if (o != p) o.connection.send(one);
			}
			p.connection.send(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(Action.UPDATE_LISTED), all));
		}
		WHERE.keySet().retainAll(online);
	}

	public static void clear() {
		WHERE.clear();
		settings = "";
	}
}
