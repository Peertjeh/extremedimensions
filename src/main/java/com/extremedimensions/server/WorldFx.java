package com.extremedimensions.server;

import java.util.Locale;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.network.ModPackets;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Server-side presentation: particles, sounds, titles and client animations.
 *
 * <p>Most of it runs through vanilla commands. Their syntax is stable across versions and they behave exactly
 * like the vanilla equivalents (sound categories, particle visibility, multiplayer broadcast).
 */
public final class WorldFx {
	private WorldFx() {}

	private static MinecraftServer server;

	public static void bind(MinecraftServer s) {
		server = s;
	}

	public static MinecraftServer server() {
		return server;
	}

	/** Runs a command silently as the server. */
	public static void run(String command) {
		if (server == null) {
			return;
		}
		try {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
		} catch (Exception e) {
			ExtremeDimensions.LOGGER.warn("Command failed: {}", command, e);
		}
	}

	/** Plays a client animation (see {@link com.extremedimensions.network.Fx}). */
	public static void fx(ServerPlayer p, int kind, int value) {
		ServerPlayNetworking.send(p, new ModPackets.FxPayload(kind, value));
	}

	private static void at(ServerPlayer p, String cmd) {
		run("execute at " + p.getStringUUID() + " run " + cmd);
	}

	/** Particles around a player, visible to everyone nearby. */
	public static void particles(ServerPlayer p, String name, double dy, double spread, double speed, int count) {
		at(p, String.format(Locale.ROOT, "particle %s ~ ~%.2f ~ %.2f %.2f %.2f %.3f %d normal", name, dy, spread, spread, spread, speed, count));
	}

	/** Particles at a fixed Overworld position. With count 0, (dx, dy, dz) is the direction of motion. */
	public static void particleAt(String name, double x, double y, double z, double dx, double dy, double dz, double speed, int count) {
		particleIn("minecraft:overworld", name, x, y, z, dx, dy, dz, speed, count);
	}

	public static void particleIn(String dim, String name, double x, double y, double z, double dx, double dy, double dz, double speed, int count) {
		run(String.format(Locale.ROOT, "execute in %s run particle %s %.2f %.2f %.2f %.3f %.3f %.3f %.3f %d normal",
				dim, name, x, y, z, dx, dy, dz, speed, count));
	}

	/** A sound at the player, audible to everyone nearby. */
	public static void sound(ServerPlayer p, String id, float vol, float pitch) {
		at(p, String.format(Locale.ROOT, "playsound %s master @a ~ ~ ~ %.2f %.2f", id, vol, pitch));
	}

	/** A sound only this player hears. */
	public static void soundSelf(ServerPlayer p, String id, float vol, float pitch) {
		at(p, String.format(Locale.ROOT, "playsound %s master %s ~ ~ ~ %.2f %.2f", id, p.getStringUUID(), vol, pitch));
	}

	public static void soundAt(String id, int x, int y, int z, float vol, float pitch) {
		run(String.format(Locale.ROOT, "execute in minecraft:overworld run playsound %s block @a %d %d %d %.2f %.2f", id, x, y, z, vol, pitch));
	}

	/**
	 * Big centred title from language keys.
	 *
	 * @param subtitleArgKey optional language key passed as the subtitle's single argument, or null
	 */
	public static void title(ServerPlayer p, String titleKey, String color, String subtitleKey, String subtitleArgKey) {
		String u = p.getStringUUID();
		String with = subtitleArgKey == null ? "" : ",\"with\":[{\"translate\":\"" + subtitleArgKey + "\"}]";
		run("title " + u + " times 10 70 25");
		run("title " + u + " subtitle {\"translate\":\"" + subtitleKey + "\",\"color\":\"gray\"" + with + "}");
		run("title " + u + " title {\"translate\":\"" + titleKey + "\",\"color\":\"" + color + "\",\"bold\":true}");
	}

	public static void tp(ServerPlayer p, String dim, double x, double y, double z) {
		run(String.format(Locale.ROOT, "execute in %s run tp %s %.2f %.2f %.2f", dim, p.getStringUUID(), x, y, z));
	}
}
