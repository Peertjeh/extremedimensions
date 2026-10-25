package net.battlehub.games.hunger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.BattleHub;
import net.battlehub.bot.BotPlayer;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.core.PlayerProfile;
import net.battlehub.core.hunger.Districts;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.util.Msg;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * The tributes of one Hunger Games match: their districts and training scores, the reaping at the start (which
 * numbered Hunger Games these are, counted over the server's life), and the nights: every few minutes the game holds
 * still for the tribute to those who fell since the last night ({@link Ceremony}).
 */
public final class Tributes {
	/** Ticks between two nights. */
	public static final int NIGHT_EVERY = 4 * 60 * 20;

	private final HungerHost host;
	private final Map<UUID, Integer> district = new HashMap<>();
	private final Map<UUID, Integer> score = new HashMap<>();
	private final List<UUID> fallenSinceNight = new ArrayList<>();
	private final Ceremony ceremony;
	private int number;
	private int nights;

	Tributes(HungerHost host) {
		this.host = host;
		this.ceremony = new Ceremony(host, this);
	}

	// ------------------------------------------------------------------ the reaping

	/**
	 * Districts and scores for everyone, the count of Hunger Games one up, and the reaping: in the chat, and while the
	 * tributes stand on their plates, district after district on the screen (over the time the countdown leaves before
	 * its last ten seconds); then each tribute is told their own district.
	 */
	void reap(List<ServerPlayer> players, int countdownTicks) {
		List<Integer> d = Districts.assign(players.size(), host.rng());
		for (int i = 0; i < players.size(); i++) {
			ServerPlayer p = players.get(i);
			district.put(p.getUUID(), d.get(i));
			score.put(p.getUUID(), scoreOf(p));
		}
		number = nextNumber();
		List<ServerPlayer> order = new ArrayList<>(players);
		order.sort((a, b) -> Integer.compare(district.get(a.getUUID()), district.get(b.getUUID())));
		for (ServerPlayer o : host.audience()) {
			Msg.send(o, "hg.reaping.title", number);
			for (ServerPlayer p : order) {
				Msg.send(o, "hg.reaping.line", district.get(p.getUUID()), host.name(p.getUUID()), score.get(p.getUUID()));
			}
			Msg.send(o, "hg.reaping.odds");
		}
		// the districts on the screen, one after the other (after the camera's flight, before the last ten seconds)
		java.util.TreeMap<Integer, List<String>> byDistrict = new java.util.TreeMap<>();
		for (ServerPlayer p : order) byDistrict.computeIfAbsent(district.get(p.getUUID()), k -> new ArrayList<>()).add(host.name(p.getUUID()));
		int start = 70, window = Math.max(40, countdownTicks - start - 215);
		int every = Math.max(12, Math.min(40, window / Math.max(1, byDistrict.size() + 1)));
		int at = start;
		net.battlehub.util.Tasks.later(at, () -> {
			if (!host.live()) return;
			for (ServerPlayer o : host.audience()) Msg.title(o, Lang.tr(o, "hg.reaping.big"), Lang.tr(o, "hg.reaping.big.sub", number), 5, every + 10, 5);
		});
		for (Map.Entry<Integer, List<String>> e : byDistrict.entrySet()) {
			at += every;
			int dn = e.getKey();
			String names = String.join("  \u00b7  ", e.getValue());
			net.battlehub.util.Tasks.later(at, () -> {
				if (!host.live()) return;
				for (ServerPlayer o : host.audience()) {
					Msg.title(o, Lang.tr(o, "hg.reaping.district", dn), Lang.tr(o, "hg.reaping.names", names), 3, every + 4, 3);
					Msg.sound(o, SoundEvents.NOTE_BLOCK_BELL, 0.5f, 0.5f + 0.06f * Math.min(dn, 12));
				}
			});
		}
		net.battlehub.util.Tasks.later(at + every, () -> {
			if (!host.live()) return;
			for (ServerPlayer p : host.tributes()) Msg.actionBar(p, "hg.reaping.you", district(p.getUUID()), score(p.getUUID()));
		});
	}

	private int scoreOf(ServerPlayer p) {
		if (p instanceof BotPlayer b) return Districts.botScore(b.difficulty.ordinal(), host.rng());
		PlayerProfile prof = PlayerStore.get(p);
		var hg = prof.peek(GameMode.HUNGER_GAMES);
		int wins = hg == null ? 0 : hg.wins;
		return Districts.trainingScore(wins, prof.overall.kills, net.battlehub.progression.Ranks.curve().levelFor(prof.xp), host.rng());
	}

	/** The server's count of Hunger Games, one up and saved (config/battlehub/hunger_games_count.txt). */
	private static int nextNumber() {
		Path f = ConfigManager.dir().resolve("hunger_games_count.txt");
		int n = 0;
		try {
			if (Files.exists(f)) n = Integer.parseInt(Files.readString(f, StandardCharsets.UTF_8).trim());
		} catch (Exception e) {
			BattleHub.LOGGER.warn("Could not read {}: {}", f, e.getMessage());
		}
		n++;
		try {
			Files.writeString(f, Integer.toString(n), StandardCharsets.UTF_8);
		} catch (Exception e) {
			BattleHub.LOGGER.warn("Could not write {}: {}", f, e.getMessage());
		}
		return n;
	}

	public int number() {
		return number;
	}

	public int district(UUID id) {
		return district.getOrDefault(id, 0);
	}

	public int score(UUID id) {
		return score.getOrDefault(id, 0);
	}

	// ------------------------------------------------------------------ nights

	/** A tribute fell: it will be honoured at the next night. */
	void fell(UUID id) {
		fallenSinceNight.add(id);
	}

	void tick() {
		int t = host.runTicks();
		if (t > 0 && t % NIGHT_EVERY == 0) night();
	}

	/** Night falls: the tribute to those who fell since the last one ({@link Ceremony}). */
	public void night() {
		nights++;
		ceremony.start(fallenSinceNight);
		fallenSinceNight.clear();
	}

	Ceremony ceremony() {
		return ceremony;
	}

	public int nights() {
		return nights;
	}

	/** How many fallen the night shows now (self-test). */
	public int inSky() {
		return ceremony.fallen();
	}

	/** Whether the night's tribute is going on now. */
	public boolean honouring() {
		return ceremony.active();
	}

	/** The things on a viewer's stage in the sky now (self-test). */
	public int onStage(UUID viewer) {
		return ceremony.onStage(viewer);
	}

	void clear() {
		ceremony.end();
	}
}
