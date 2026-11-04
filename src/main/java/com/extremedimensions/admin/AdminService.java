package com.extremedimensions.admin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.coop.ClanService;
import com.extremedimensions.config.SettingsService;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.penalty.CurseLog;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.seam.SeamDepth;
import com.extremedimensions.server.Dims;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Players screen for operators (3.27): every player the world knows, online or not, the operator among them; for
 * the one picked, where they stand and how they fare, and every curse they carry, with a stack laid on or lifted off at
 * a click. A stack lifted here is taken off plainly: no scar, no reward, as an operator's hand and not an atonement.
 *
 * <p>Only operators (and the player of a world of their own) get the data or may change anything; every change is
 * written to the server log, and the player it touches is told.
 */
public final class AdminService {
	private AdminService() {}

	/** At most this many players are listed (online first, then by name): enough for any server, small enough for one payload. */
	public static final int LIST_MAX = 500;

	/** Whether this player may open the Players screen and change another's curses. */
	public static boolean allowed(ServerPlayer p) {
		return SettingsService.canEdit(p);
	}

	/** Sends the Players screen's data to an operator; anyone else is told no (and gets nothing). */
	public static void send(ServerPlayer op, String selected, boolean open) {
		if (!allowed(op)) {
			if (open) {
				op.sendSystemMessage(Component.translatable("gui.extremedimensions.admin.refused"));
			}
			return;
		}
		ServerPlayNetworking.send(op, new ModPackets.AdminPayload(data(op.level().getServer(), op.getUUID(), selected), open));
	}

	/** A change asked for from the screen: made if the asker may, then the screen gets fresh data. Returns why not, or null. */
	public static String act(ServerPlayer op, String target, String action, String curse) {
		if (!allowed(op)) {
			op.sendSystemMessage(Component.translatable("gui.extremedimensions.admin.refused"));
			return "gui.extremedimensions.admin.refused";
		}
		String why = change(op.level().getServer(), op, target, action, curse);
		if (why != null) {
			op.sendSystemMessage(Component.translatable(why));
		}
		send(op, target, false);
		return why;
	}

	/** The name the world knows a player by: their own while online, the last one seen, or the start of their id. */
	static String nameOf(MinecraftServer server, UUID id, PlayerData d) {
		ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(id);
		if (online != null) {
			return online.getName().getString();
		}
		if (d != null && d.name != null && !d.name.isEmpty()) {
			return d.name;
		}
		return id.toString().substring(0, 8);
	}

	private static int total(PlayerData d) {
		int n = 0;
		for (int v : d.curses.values()) {
			n += v;
		}
		return n;
	}

	/** Whether the world has ever seen this player (a stray id is never given data of its own). */
	private static boolean known(MinecraftServer server, UUID id) {
		return server != null && server.getPlayerList().getPlayer(id) != null || DataStore.playerIds().contains(id.toString());
	}

	/**
	 * The screen's data as JSON: {@code you} (the asker), {@code players} (id, name, on: online, n: stacks carried), and
	 * for the player picked {@code sel}: where they are, how they fare, and per curse [stacks, max, atonement, cost].
	 */
	public static String data(MinecraftServer server, UUID viewer, String selected) {
		JsonObject out = new JsonObject();
		out.addProperty("you", viewer.toString());
		record Row(UUID id, String name, boolean on, int n) {}
		List<Row> rows = new ArrayList<>();
		java.util.Set<String> seen = new java.util.HashSet<>();
		if (server != null) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				seen.add(p.getStringUUID());
				rows.add(new Row(p.getUUID(), p.getName().getString(), true, total(DataStore.get(p.getUUID()))));
			}
		}
		for (String s : DataStore.playerIds()) {
			if (seen.contains(s)) {
				continue;
			}
			try {
				UUID id = UUID.fromString(s);
				PlayerData d = DataStore.get(id);
				rows.add(new Row(id, nameOf(server, id, d), false, total(d)));
			} catch (IllegalArgumentException e) {
				// not a player's id
			}
		}
		rows.sort(Comparator.comparing((Row r) -> !r.on()).thenComparing(r -> r.name().toLowerCase(java.util.Locale.ROOT)));
		JsonArray list = new JsonArray();
		for (int i = 0; i < rows.size() && i < LIST_MAX; i++) {
			Row r = rows.get(i);
			JsonObject o = new JsonObject();
			o.addProperty("id", r.id().toString());
			o.addProperty("name", r.name());
			o.addProperty("on", r.on());
			o.addProperty("n", r.n());
			list.add(o);
		}
		out.add("players", list);
		UUID pick = null;
		try {
			pick = selected == null || selected.isEmpty() ? viewer : UUID.fromString(selected);
		} catch (IllegalArgumentException e) {
			pick = viewer;
		}
		if (!known(server, pick)) {
			pick = viewer;
		}
		out.add("sel", detail(server, pick));
		return out.toString();
	}

	/** One player in full: where they are, how they fare, their newest curse, and every curse with its stacks. */
	static JsonObject detail(MinecraftServer server, UUID id) {
		PlayerData d = DataStore.get(id);
		ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(id);
		JsonObject o = new JsonObject();
		o.addProperty("id", id.toString());
		o.addProperty("name", nameOf(server, id, d));
		o.addProperty("on", p != null);
		if (p != null) {
			String world = Dims.of(p);
			o.addProperty("world", Dims.SEAM.equals(world) && SeamDepth.inFray(p) ? "the_fray" : world);
			o.addProperty("run", d.inRun);
			o.addProperty("health", (int) Math.ceil(p.getHealth()));
		}
		o.addProperty("scars", d.scars);
		o.addProperty("lifted", d.lifted);
		o.addProperty("deaths", d.deaths);
		o.addProperty("exp", d.expeditions);
		o.addProperty("seam", d.seamVisits);
		o.addProperty("fray", d.frayDescents);
		o.addProperty("rep", d.tribeRep);
		o.addProperty("relic", d.relic == null ? "" : d.relic);
		o.addProperty("atoning", d.atoning == null ? "" : d.atoning);
		int sworn = 0;
		if (d.pact != null) {
			for (int v : d.pact.values()) {
				sworn += v;
			}
		}
		o.addProperty("pact", sworn);
		StringBuilder clan = new StringBuilder();
		for (UUID m : ClanService.others(id)) {
			if (clan.length() > 0) {
				clan.append(", ");
			}
			clan.append(nameOf(server, m, DataStore.get(m)));
		}
		o.addProperty("clan", clan.toString());
		List<CurseLog.Entry> log = CurseLog.entries(d);
		if (!log.isEmpty()) {
			CurseLog.Entry e = log.get(0);
			JsonObject last = new JsonObject();
			last.addProperty("id", e.id());
			last.addProperty("n", e.stacks());
			last.addProperty("ago", Math.max(0, (System.currentTimeMillis() - e.at()) / 1000));
			last.addProperty("from", e.source().id);
			o.add("last", last);
		}
		JsonObject curses = new JsonObject();
		for (Penalty pen : Penalty.values()) {
			JsonArray a = new JsonArray();
			a.add(d.owned(pen));
			a.add(pen.max);
			a.add(d.atonement.getOrDefault(pen.id, 0));
			a.add(PenaltyService.cost(d, pen));
			curses.add(pen.id, a);
		}
		o.add("curses", curses);
		return o;
	}

	/**
	 * Lays a stack on ({@code add}), lifts one off ({@code lift}) or lifts every curse ({@code clear}) for a player online
	 * or not, and tells them. Returns why nothing changed (a lang key), or null when done.
	 */
	public static String change(MinecraftServer server, ServerPlayer by, String targetId, String action, String curseId) {
		UUID id;
		try {
			id = UUID.fromString(targetId);
		} catch (IllegalArgumentException | NullPointerException e) {
			return "gui.extremedimensions.admin.no_player";
		}
		if (!known(server, id)) {
			return "gui.extremedimensions.admin.no_player";
		}
		PlayerData d = DataStore.get(id);
		ServerPlayer target = server == null ? null : server.getPlayerList().getPlayer(id);
		Penalty pen = curseId == null || curseId.isEmpty() ? null : Penalty.byId(curseId);
		String who = nameOf(server, id, d), byName = by == null ? "the server" : by.getName().getString();
		boolean self = by != null && by.getUUID().equals(id);
		switch (action == null ? "" : action) {
			case "add" -> {
				if (pen == null) {
					return "gui.extremedimensions.admin.no_curse";
				}
				if (CurseLog.add(d, pen, 1, CurseLog.Source.OPERATOR) <= 0) {
					return "gui.extremedimensions.admin.full";
				}
				ExtremeDimensions.LOGGER.info("{} laid a stack of {} on {} ({}/{})", byName, pen.id, who, d.owned(pen), pen.max);
			}
			case "lift" -> {
				if (pen == null) {
					return "gui.extremedimensions.admin.no_curse";
				}
				if (!d.removeStack(pen)) {
					return "gui.extremedimensions.admin.none";
				}
				ExtremeDimensions.LOGGER.info("{} lifted a stack of {} from {} ({}/{})", byName, pen.id, who, d.owned(pen), pen.max);
				if (target != null && !self) {
					target.sendSystemMessage(Component.translatable("gui.extremedimensions.admin.lifted_you", Component.translatable(pen.nameKey()),
							d.owned(pen), pen.max));
				}
			}
			case "clear" -> {
				int lifted = 0;
				for (Penalty p : Penalty.values()) {
					while (d.removeStack(p)) {
						lifted++;
					}
				}
				if (lifted == 0) {
					return "gui.extremedimensions.admin.none_at_all";
				}
				ExtremeDimensions.LOGGER.info("{} lifted every curse from {} ({} stacks)", byName, who, lifted);
				if (target != null && !self) {
					target.sendSystemMessage(Component.translatable("gui.extremedimensions.admin.cleared_you"));
				}
			}
			default -> {
				return "gui.extremedimensions.admin.no_action";
			}
		}
		if (target != null) {
			CurseEffects.applyEffects(target, d);
			Sync.state(target, d);
			Sync.curses(target, d, false);
		}
		DataStore.markDirty();
		return null;
	}
}
