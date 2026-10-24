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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;

/**
 * The tributes of one Hunger Games match: their districts and training scores, the reaping at the start (which
 * numbered Hunger Games these are, counted over the server's life), and the nights: every few minutes the anthem
 * plays and the tributes who fell since the last night shine in the sky over the plaza.
 */
public final class Tributes {
	/** Ticks between two nights, and how long the fallen stay in the sky. */
	public static final int NIGHT_EVERY = 4 * 60 * 20, SKY_TICKS = 30 * 20;

	private final HungerHost host;
	private final Map<UUID, Integer> district = new HashMap<>();
	private final Map<UUID, Integer> score = new HashMap<>();
	private final List<UUID> fallenSinceNight = new ArrayList<>();
	private final List<Display.TextDisplay> sky = new ArrayList<>();
	private int skyUntil = -1;
	private int number;
	private int nights;

	Tributes(HungerHost host) {
		this.host = host;
	}

	// ------------------------------------------------------------------ the reaping

	/** Districts and scores for everyone, the count of Hunger Games one up, and the reaping told to all. */
	void reap(List<ServerPlayer> players) {
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
			Msg.sound(o, SoundEvents.NOTE_BLOCK_BELL, 1f, 0.6f);
		}
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

	/** A tribute fell: it will shine in the sky at the next night. */
	void fell(UUID id) {
		fallenSinceNight.add(id);
	}

	void tick() {
		int t = host.runTicks();
		if (t > 0 && t % NIGHT_EVERY == 0) night();
		if (skyUntil >= 0 && t >= skyUntil) clearSky();
	}

	/** The anthem, and the fallen in the sky over the plaza (or a word that nobody fell). */
	public void night() {
		nights++;
		clearSky();
		for (ServerPlayer o : host.audience()) {
			Msg.title(o, Lang.tr(o, "hg.night.title"), Lang.tr(o, fallenSinceNight.isEmpty() ? "hg.night.none" : "hg.night.sub", fallenSinceNight.size()), 10, 70, 20);
			anthem(o);
		}
		Vec3 c = host.plaza();
		int n = fallenSinceNight.size();
		for (int i = 0; i < n; i++) {
			UUID id = fallenSinceNight.get(i);
			double a = (i - (n - 1) / 2.0) * 0.35;
			Vec3 at = c.add(Math.sin(a) * 20, 28 + Math.cos(a) * 2, -Math.cos(a) * 20);
			Component text = Lang.tr("hg.night.face", host.name(id), district(id));
			Display.TextDisplay d = Holo.text(host.level(), at, text, 3.0f);
			if (d != null) sky.add(d);
		}
		skyUntil = host.runTicks() + SKY_TICKS;
		fallenSinceNight.clear();
	}

	/** A few notes of the anthem (the note block bell, slow). */
	private static void anthem(ServerPlayer p) {
		float[] notes = {0.7f, 0.84f, 0.94f, 0.84f, 1.05f, 0.94f};
		for (int i = 0; i < notes.length; i++) {
			float pitch = notes[i];
			net.battlehub.util.Tasks.later(1 + i * 8, () -> Msg.sound(p, SoundEvents.NOTE_BLOCK_BELL, 0.9f, pitch));
		}
	}

	private void clearSky() {
		for (Display.TextDisplay d : sky) Holo.remove(d);
		sky.clear();
		skyUntil = -1;
	}

	public int nights() {
		return nights;
	}

	/** The faces in the sky now (self-test). */
	public int inSky() {
		return (int) sky.stream().filter(d -> !d.isRemoved()).count();
	}

	void clear() {
		clearSky();
	}
}
