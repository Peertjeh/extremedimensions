package com.extremedimensions.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.CurseEffects;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The world's settings in the game: every player gets them on joining (so the HUD and screens use the server's
 * numbers), and operators can change them from the settings screen. Changes are saved to the config file at once and
 * take effect without a restart.
 */
public final class SettingsService {
	private SettingsService() {}

	private static final Gson GSON = new Gson();

	/** Operators, and the owner of a world that is not open to LAN, may change the world's settings. */
	public static boolean canEdit(ServerPlayer p) {
		MinecraftServer s = p.level().getServer();
		if (s != null && s.isSingleplayer() && !s.isPublished()) {
			return true;
		}
		return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(p.createCommandSourceStack());
	}

	public static void send(ServerPlayer p) {
		ServerPlayNetworking.send(p, new ModPackets.SettingsPayload(GSON.toJson(Config.snapshot()), canEdit(p)));
	}

	public static void sendAll(MinecraftServer s) {
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			send(p);
		}
	}

	/** A player asked to change settings: only taken from those allowed, each value checked, saved together. */
	public static void change(ServerPlayer p, String changes) {
		if (!canEdit(p)) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.settings.not_allowed"));
			send(p);
			return;
		}
		JsonObject json;
		try {
			JsonElement parsed = JsonParser.parseString(changes);
			json = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
		} catch (Exception e) {
			json = new JsonObject();
		}
		int taken = 0;
		List<String> refused = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : json.entrySet()) {
			String value = e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : "";
			if (Config.set(e.getKey(), value)) {
				taken++;
			} else {
				refused.add(e.getKey());
			}
		}
		MinecraftServer s = p.level().getServer();
		if (taken > 0) {
			Config.save();
			ExtremeDimensions.LOGGER.info("{} changed {} world setting(s)", p.getName().getString(), taken);
			// Curses' strength may have changed: every player's attribute curses and HUD follow at once
			for (ServerPlayer q : s.getPlayerList().getPlayers()) {
				var d = DataStore.get(q.getUUID());
				CurseEffects.applyEffects(q, d);
				Sync.state(q, d);
			}
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.settings.saved", taken));
		}
		for (String key : refused) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.settings.refused", key));
		}
		sendAll(s);
	}
}
