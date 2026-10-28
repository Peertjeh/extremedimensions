package net.battlehub.hub;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.battlehub.BattleHub;
import net.battlehub.arena.Arena;
import net.battlehub.arena.ArenaBuilder;
import net.battlehub.arena.ArenaWorld;
import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.config.ConfigManager;
import net.battlehub.data.PlayerStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * BattleHub's own world ({@code battlehub:hub}): an empty dimension with a lobby the mod builds itself. Together
 * with the arena dimension it is "BattleHub"; every other dimension is normal gameplay that BattleHub leaves alone.
 */
public final class HubWorld {
	private HubWorld() {}

	public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, BattleHub.id("hub"));
	/** Bump to rebuild the lobby on existing servers after a layout change. */
	private static final int LOBBY_VERSION = 4;
	private static final BlockPos ORIGIN = new BlockPos(0, 100, 0);

	private static volatile boolean ready;

	public static ServerLevel level(MinecraftServer server) {
		ServerLevel level = server.getLevel(KEY);
		return level != null ? level : server.overworld();
	}

	public static boolean is(Level level) {
		return level.dimension() == KEY;
	}

	/** The hub or the arenas: places where BattleHub's rules apply. */
	public static boolean isBattleHub(Level level) {
		return is(level) || ArenaWorld.is(level);
	}

	/**
	 * Whether a chat message reaches a player (1.50, {@code hub.separateChat}): only when both are in BattleHub or both
	 * are in the normal world. A message without a player behind it (the console, a command block) reaches everyone, and
	 * so does one that starts with {@code hub.globalChatPrefix} (1.51).
	 */
	public static boolean hearsChat(ServerPlayer sender, ServerPlayer receiver, String text) {
		var hub = ConfigManager.config().hub;
		if (sender == null || !hub.separateChat) return true;
		if (!hub.globalChatPrefix.isEmpty() && text.startsWith(hub.globalChatPrefix)) return true;
		return isBattleHub(sender.level()) == isBattleHub(receiver.level());
	}

	public static boolean ready() {
		return ready;
	}

	/** Where players arrive when no lobby position was set with /battlehub setlobby. */
	public static Vec3 defaultSpawn() {
		return new Vec3(ORIGIN.getX() + HubLayout.SPAWN_X, ORIGIN.getY() + HubLayout.SPAWN_Y, ORIGIN.getZ() + HubLayout.SPAWN_Z);
	}

	public static float defaultYaw() {
		return HubLayout.SPAWN_YAW;
	}

	public static int floorY() {
		return ORIGIN.getY();
	}

	/** The lobby's blueprint, as it is built (for the pictures in the self-test). */
	public static Blueprint previewBlueprint() {
		return design();
	}

	/** The lobby of the configured style: the generated citadel, or the classic plaza with four buildings. */
	static Blueprint design() {
		var cfg = ConfigManager.config().lobby;
		return "classic".equals(cfg.style) ? HubLayout.design(20260101L) : ArchLobby.design(cfg);
	}

	/** What the lobby is built from: changing the style or the seed rebuilds it. */
	private static String recipe() {
		var cfg = ConfigManager.config().lobby;
		return "classic".equals(cfg.style) ? "classic" : "citadel:" + cfg.seed + ":" + cfg.detail + ":" + cfg.islandRadius + ":" + cfg.townDensity;
	}

	/** Radius of the plaza round the fountain (its balustrade carries the leaderboards). */
	public static int plazaRadius() {
		return HubLayout.RADIUS;
	}

	/** Where the lobby layout's 0,0,0 is in the hub world. */
	static BlockPos origin() {
		return ORIGIN;
	}

	private static Path marker(MinecraftServer server) {
		return PlayerStore.worldDir(server).resolve("hub.json");
	}

	/** Builds the lobby the first time (or after a layout update). */
	public static void ensureBuilt(MinecraftServer server) {
		ready = false;
		if (server.getLevel(KEY) == null) {
			BattleHub.LOGGER.error("The hub dimension battlehub:hub is missing; is the BattleHub data pack disabled?");
			return;
		}
		JsonObject old = readMarker(server);
		if (old != null && old.has("version") && old.get("version").getAsInt() >= LOBBY_VERSION && old.has("recipe")
				&& old.get("recipe").getAsString().equals(recipe())) {
			ready = true;
			return;
		}
		build(server, old);
	}

	/** Removes the lobby BattleHub built before and builds it again (admins: /battlehub lobby rebuild). */
	public static void rebuild(MinecraftServer server) {
		build(server, readMarker(server));
	}

	private static boolean resetting;

	/** A reset is running: nobody comes in until the lobby stands again. */
	public static boolean resetting() {
		return resetting;
	}

	/**
	 * Resets BattleHub's world (admins: /battlehub world reset): everyone in the lobby goes back to where they came
	 * from, the whole lobby area is emptied (the island, its sky islands, the old lobby's box and anything left
	 * round them, entities included) and the lobby is built again from scratch. With {@code newLook} it gets a new
	 * seed: another city on the same plan. Matches in the arena world go on. False when a reset is already running
	 * or the hub world is missing.
	 */
	public static boolean reset(MinecraftServer server, boolean newLook) {
		ServerLevel level = server.getLevel(KEY);
		if (level == null || resetting) {
			return false;
		}
		resetting = true;
		ready = false;
		for (net.minecraft.server.level.ServerPlayer p : new java.util.ArrayList<>(server.getPlayerList().getPlayers())) {
			if (is(p.level())) {
				Travel.leave(p);
				net.battlehub.util.Msg.send(p, "hub.reset_out");
			}
		}
		if (newLook) {
			ConfigManager.config().lobby.seed = new java.util.Random().nextLong() & 0xFFFFFFFFL;
			ConfigManager.saveMain();
		}
		JsonObject old = readMarker(server);
		AABB all = resetBox(level);
		if (old != null && old.has("minX")) {
			all = all.minmax(new AABB(old.get("minX").getAsInt(), old.get("minY").getAsInt(), old.get("minZ").getAsInt(),
					old.get("maxX").getAsInt() + 1, old.get("maxY").getAsInt() + 1, old.get("maxZ").getAsInt() + 1));
		}
		BattleHub.LOGGER.info("BattleHub world reset: clearing {} and building the lobby again{}", all, newLook ? " (new seed)" : "");
		ArenaBuilder.clear(level, all, () -> {
			try {
				Files.deleteIfExists(marker(server));
			} catch (Exception e) {
				BattleHub.LOGGER.warn("Could not remove {}: {}", marker(server), e.getMessage());
			}
			build(server, null, () -> {
				resetting = false;
				net.battlehub.util.Msg.broadcast(server, "hub.reset_done");
			});
		});
		return true;
	}

	/** Everything the lobby can reach: the island with its sky islands (or the classic districts) and room round them. */
	private static AABB resetBox(ServerLevel level) {
		int r = Math.max(ConfigManager.config().lobby.islandRadius, 100) + 70;
		return new AABB(ORIGIN.getX() - r, Math.max(level.getMinY(), ORIGIN.getY() - 90), ORIGIN.getZ() - r,
				ORIGIN.getX() + r + 1, Math.min(level.getMaxY(), ORIGIN.getY() + 150), ORIGIN.getZ() + r + 1);
	}

	private static void build(MinecraftServer server, JsonObject old) {
		build(server, old, null);
	}

	private static void build(MinecraftServer server, JsonObject old, Runnable then) {
		ServerLevel level = server.getLevel(KEY);
		if (level == null) {
			return;
		}
		if (old != null && old.has("minX")) {
			AABB box = new AABB(old.get("minX").getAsInt(), old.get("minY").getAsInt(), old.get("minZ").getAsInt(),
					old.get("maxX").getAsInt() + 1, old.get("maxY").getAsInt() + 1, old.get("maxZ").getAsInt() + 1);
			ArenaBuilder.clear(level, box, null);
		}
		Blueprint bp = design();
		Arena lobby = new Arena(level, -1, Theme.CASTLE, ORIGIN, bp);
		ArenaBuilder.build(lobby, () -> {
			writeMarker(server, bp);
			ready = true;
			BattleHub.LOGGER.info("BattleHub lobby built in {}", KEY.identifier());
			// whoever arrived while it was being built (back from a match) stands on it now
			for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (is(p.level()) && net.battlehub.session.Sessions.state(p.getUUID()) == net.battlehub.session.Sessions.State.LOBBY) net.battlehub.lobby.Lobby.teleport(p);
			}
			if (then != null) then.run();
		});
	}

	private static JsonObject readMarker(MinecraftServer server) {
		Path f = marker(server);
		if (!Files.exists(f)) {
			return null;
		}
		try {
			return JsonParser.parseString(Files.readString(f)).getAsJsonObject();
		} catch (Exception e) {
			BattleHub.LOGGER.warn("Could not read {}: {}", f, e.getMessage());
			return null;
		}
	}

	private static void writeMarker(MinecraftServer server, Blueprint bp) {
		JsonObject o = new JsonObject();
		o.addProperty("version", LOBBY_VERSION);
		o.addProperty("recipe", recipe());
		o.addProperty("minX", ORIGIN.getX() + bp.minX());
		o.addProperty("minY", ORIGIN.getY() + bp.minY());
		o.addProperty("minZ", ORIGIN.getZ() + bp.minZ());
		o.addProperty("maxX", ORIGIN.getX() + bp.maxX());
		o.addProperty("maxY", ORIGIN.getY() + bp.maxY());
		o.addProperty("maxZ", ORIGIN.getZ() + bp.maxZ());
		try {
			ConfigManager.writeJson(marker(server), ConfigManager.GSON.toJson(o));
		} catch (Exception e) {
			BattleHub.LOGGER.error("Could not write {}", marker(server), e);
		}
	}
}
