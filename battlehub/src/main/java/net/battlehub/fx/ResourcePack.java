package net.battlehub.fx;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.battlehub.BattleHub;
import net.battlehub.bot.Bots;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.lang.Lang;
import net.battlehub.util.Msg;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The optional resource pack. It is built when the server starts and offered when a player comes into
 * BattleHub; the player may say no. BattleHub serves it over HTTP itself (a small server on its own port), or
 * the admin hosts the zip from {@code config/battlehub/resourcepack/} somewhere and sets the URL.
 */
public final class ResourcePack {
	private ResourcePack() {}

	public enum Status { OFFERED, ACCEPTED, LOADED, DECLINED, FAILED }

	private static final Map<UUID, Status> STATUS = new ConcurrentHashMap<>();
	private static byte[] zip;
	private static String hash = "";
	private static String url = "";
	private static UUID packId;
	private static Object http;

	private static HubConfig.ResourcePack cfg() {
		return ConfigManager.config().resourcePack;
	}

	public static Path dir() {
		return ConfigManager.dir().resolve("resourcepack");
	}

	/** Builds the pack and starts serving it (when switched on). */
	public static void start(MinecraftServer server) {
		stop();
		if (!cfg().enabled) {
			return;
		}
		try {
			Files.createDirectories(dir().resolve("overlay"));
			zip = PackBuilder.build(dir().resolve("overlay"));
			Files.write(dir().resolve("battlehub-pack.zip"), zip);
			hash = sha1(zip);
			packId = UUID.nameUUIDFromBytes(("battlehub-pack:" + hash).getBytes(StandardCharsets.UTF_8));
		} catch (IOException | RuntimeException e) {
			BattleHub.LOGGER.error("BattleHub resource pack could not be built: {}", e.toString());
			zip = null;
			return;
		}
		if (!cfg().url.isBlank()) {
			url = cfg().url.trim();
			BattleHub.LOGGER.info("BattleHub resource pack: offered from {}", url);
			return;
		}
		if (cfg().host.isBlank()) {
			BattleHub.LOGGER.warn("BattleHub resource pack: set resourcePack.host (the address players use) or resourcePack.url; not offered");
			zip = null;
			return;
		}
		try {
			com.sun.net.httpserver.HttpServer s = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(cfg().port), 0);
			byte[] body = zip;
			s.createContext("/", ex -> {
				try {
					if (!ex.getRequestURI().getPath().equals("/battlehub-pack.zip")) {
						ex.sendResponseHeaders(404, -1);
						return;
					}
					ex.getResponseHeaders().add("Content-Type", "application/zip");
					ex.sendResponseHeaders(200, body.length);
					try (OutputStream os = ex.getResponseBody()) {
						os.write(body);
					}
				} finally {
					ex.close();
				}
			});
			s.start();
			http = s;
			url = "http://" + cfg().host.trim() + ":" + cfg().port + "/battlehub-pack.zip";
			BattleHub.LOGGER.info("BattleHub resource pack: serving {} ({} bytes)", url, zip.length);
		} catch (IOException | RuntimeException | LinkageError e) {
			BattleHub.LOGGER.error("BattleHub resource pack: could not serve it on port {}: {}", cfg().port, e.toString());
			zip = null;
		}
	}

	public static void stop() {
		if (http instanceof com.sun.net.httpserver.HttpServer s) {
			s.stop(0);
		}
		http = null;
		zip = null;
		url = "";
		STATUS.clear();
	}

	private static String sha1(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
		} catch (NoSuchAlgorithmException e) {
			return "";
		}
	}

	public static boolean active() {
		return zip != null && !url.isEmpty();
	}

	/** Offers the pack to a player who came into BattleHub (once per session). */
	public static void offer(ServerPlayer p) {
		if (!active() || Bots.isBot(p) || STATUS.containsKey(p.getUUID())) {
			return;
		}
		STATUS.put(p.getUUID(), Status.OFFERED);
		// With our own server the hash is known; a pack hosted elsewhere may have been changed, so it is not checked
		String h = cfg().url.isBlank() ? hash : "";
		p.connection.send(new ClientboundResourcePackPushPacket(packId, url, h, cfg().required,
				Optional.of(Lang.tr(p, cfg().required ? "pack.prompt_required" : "pack.prompt"))));
	}

	/** What the client answered (from the packet listener). */
	public static void response(ServerPlayer p, UUID id, String action) {
		if (packId == null || !packId.equals(id)) {
			return;
		}
		switch (action) {
			case "ACCEPTED", "DOWNLOADED" -> STATUS.put(p.getUUID(), Status.ACCEPTED);
			case "SUCCESSFULLY_LOADED" -> {
				STATUS.put(p.getUUID(), Status.LOADED);
				Msg.send(p, "pack.loaded");
			}
			case "DECLINED" -> {
				STATUS.put(p.getUUID(), Status.DECLINED);
				if (cfg().required && net.battlehub.hub.Travel.inBattleHub(p)) {
					Msg.send(p, "pack.required");
					net.battlehub.hub.Travel.leave(p);
				} else {
					Msg.send(p, "pack.declined");
				}
			}
			case "FAILED_DOWNLOAD", "INVALID_URL", "FAILED_RELOAD", "DISCARDED" -> STATUS.put(p.getUUID(), Status.FAILED);
			default -> { }
		}
	}

	public static boolean loaded(ServerPlayer p) {
		return STATUS.get(p.getUUID()) == Status.LOADED;
	}

	public static Status status(UUID id) {
		return STATUS.get(id);
	}

	/** Self-test: pretend this player loaded the pack (or not). */
	public static void setLoadedForTest(UUID id, boolean loaded) {
		if (loaded) STATUS.put(id, Status.LOADED);
		else STATUS.remove(id);
	}

	public static void forget(UUID id) {
		STATUS.remove(id);
	}

	public static UUID packId() {
		return packId;
	}

	public static String url() {
		return url;
	}

	/** The built zip (for the self-test), or null. */
	public static byte[] zip() {
		return zip;
	}
}
