package net.battlehub.fair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.util.Msg;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /report <player> <reason>}: written to {@code <world>/battlehub/reports.log} (one JSON object per line,
 * with the match the player was in) and shown to admins who are online. One report a minute per player.
 */
public final class Reports {
	private Reports() {}

	private static final Map<UUID, Long> LAST = new HashMap<>();
	private static final List<String> RECENT = new ArrayList<>();

	private static Path file(MinecraftServer server) {
		return PlayerStore.worldDir(server).resolve("reports.log");
	}

	public static boolean report(ServerPlayer from, String target, String reason) {
		long now = System.currentTimeMillis();
		Long last = LAST.get(from.getUUID());
		int cooldown = Math.max(0, ConfigManager.config().fairness.reportCooldownSeconds);
		if (last != null && now - last < cooldown * 1000L) {
			Msg.send(from, "fair.report_wait", (cooldown * 1000L - (now - last) + 999) / 1000);
			return false;
		}
		if (target.equalsIgnoreCase(from.getGameProfile().name())) {
			Msg.send(from, "fair.report_self");
			return false;
		}
		LAST.put(from.getUUID(), now);
		MinecraftServer server = from.level().getServer();
		ServerPlayer t = server.getPlayerList().getPlayerByName(target);
		Match m = t == null ? null : Matches.of(t.getUUID());
		JsonObject o = new JsonObject();
		o.addProperty("time", Instant.ofEpochMilli(now).toString());
		o.addProperty("from", from.getGameProfile().name());
		o.addProperty("player", t == null ? target : t.getGameProfile().name());
		o.addProperty("reason", reason);
		if (m != null) {
			o.addProperty("match", m.id);
			o.addProperty("mode", m.mode.id());
		}
		String line = ConfigManager.GSON.toJson(o).replace("\n", "").replaceAll("\\s{2,}", " ");
		try {
			Files.writeString(file(server), line + System.lineSeparator(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			BattleHub.LOGGER.error("Could not write the report", e);
		}
		synchronized (RECENT) {
			RECENT.add(line);
			while (RECENT.size() > 20) RECENT.remove(0);
		}
		BattleHub.LOGGER.info("Report: {}", line);
		Msg.send(from, "fair.reported", target);
		for (ServerPlayer admin : server.getPlayerList().getPlayers()) {
			if (Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(admin.createCommandSourceStack())) {
				Msg.raw(admin, Lang.tr(admin, "fair.report_admin", from.getGameProfile().name(), target, reason));
			}
		}
		return true;
	}

	/** The last reports since the server started (newest last). */
	public static List<String> recent() {
		synchronized (RECENT) {
			return List.copyOf(RECENT);
		}
	}

	public static void clear() {
		LAST.clear();
	}
}
