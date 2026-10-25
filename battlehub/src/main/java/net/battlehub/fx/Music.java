package net.battlehub.fx;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.music.NoteBlocks;
import net.battlehub.core.music.Score;
import net.battlehub.util.Ids;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * BattleHub's own music (1.47): pieces written for the Hunger Games (the opening, the gong, the bloodbath, the alarm
 * of an event, the Feast, the anthem of the night, the victory). A player with the resource pack hears the
 * synthesised .ogg; everyone else hears the same score on note blocks, played tick by tick by the server
 * ({@link NoteBlocks}). One piece at a time per player, in the "Jukebox/Note Blocks" volume.
 */
public final class Music {
	private Music() {}

	public static final String OPENING = "hg_opening", GONG = "hg_gong", BLOODBATH = "hg_bloodbath", ALARM = "hg_alarm", FEAST = "hg_feast",
			ANTHEM = "hg_anthem", VICTORY = "hg_victory";
	public static final List<String> SONGS = List.of(OPENING, GONG, BLOODBATH, ALARM, FEAST, ANTHEM, VICTORY);

	private record Track(Score score, List<NoteBlocks.Strike>[] byTick) {}

	private record Playing(String song, long start, boolean pack, int length) {}

	private static final Map<String, Track> TRACKS = new HashMap<>();
	private static final Map<UUID, Playing> PLAYING = new HashMap<>();
	private static final Map<String, Holder<SoundEvent>> SOUNDS = new HashMap<>();
	/** The last pieces started for each player (self-test). */
	private static final Map<UUID, java.util.Deque<String>> HISTORY = new HashMap<>();
	private static long now;

	private static boolean on() {
		return ConfigManager.config().effects.music;
	}

	/** The score of a piece, read from the jar once ({@code /battlehub/music/<song>.json}); null when missing. */
	@SuppressWarnings("unchecked")
	private static Track track(String song) {
		return TRACKS.computeIfAbsent(song, s -> {
			try (InputStream in = Music.class.getResourceAsStream("/battlehub/music/" + s + ".json")) {
				if (in == null) return null;
				Score score = Score.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
				List<NoteBlocks.Strike> strikes = NoteBlocks.render(score);
				int last = strikes.isEmpty() ? 0 : strikes.get(strikes.size() - 1).tick();
				List<NoteBlocks.Strike>[] byTick = new List[last + 1];
				for (NoteBlocks.Strike st : strikes) {
					if (byTick[st.tick()] == null) byTick[st.tick()] = new ArrayList<>();
					byTick[st.tick()].add(st);
				}
				return new Track(score, byTick);
			} catch (IOException | RuntimeException e) {
				BattleHub.LOGGER.warn("BattleHub music {} could not be read: {}", s, e.toString());
				return null;
			}
		});
	}

	/** The synthesised .ogg of a piece (for the resource pack), or null. */
	public static byte[] ogg(String song) {
		try (InputStream in = Music.class.getResourceAsStream("/battlehub/music/" + song + ".ogg")) {
			return in == null ? null : in.readAllBytes();
		} catch (IOException e) {
			return null;
		}
	}

	/** How long a piece lasts (ticks), 0 when unknown. */
	public static int length(String song) {
		Track t = track(song);
		return t == null ? 0 : t.score().lengthTicks;
	}

	public static void play(Collection<ServerPlayer> players, String song) {
		for (ServerPlayer p : players) play(p, song);
	}

	/** Plays a piece to a player, from {@code fromTick} on (on note blocks; with the pack always from the start). */
	public static void play(ServerPlayer p, String song, int fromTick) {
		if (p == null || p.hasDisconnected() || !on() || net.battlehub.bot.Bots.isBot(p)) return;
		Track t = track(song);
		if (t == null) return;
		stop(p);
		boolean pack = ResourcePack.loaded(p);
		if (pack) {
			p.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvent.createVariableRangeEvent(Ids.id("battlehub:music." + song))),
					SoundSource.RECORDS, p.getX(), p.getY(), p.getZ(), volume(), 1f, p.level().getRandom().nextLong()));
		}
		PLAYING.put(p.getUUID(), new Playing(song, now - Math.max(0, fromTick), pack, t.byTick().length + 1));
		java.util.Deque<String> h = HISTORY.computeIfAbsent(p.getUUID(), k -> new java.util.ArrayDeque<>());
		h.addLast(song);
		while (h.size() > 12) h.removeFirst();
	}

	/** The pieces started for a player lately, oldest first (self-test). */
	public static List<String> history(UUID id) {
		java.util.Deque<String> h = HISTORY.get(id);
		return h == null ? List.of() : List.copyOf(h);
	}

	public static void play(ServerPlayer p, String song) {
		play(p, song, 0);
	}

	private static float volume() {
		return (float) Math.max(0, Math.min(1, ConfigManager.config().effects.musicVolume));
	}

	/** Stops what a player hears. */
	public static void stop(ServerPlayer p) {
		Playing was = PLAYING.remove(p.getUUID());
		if (was != null && was.pack() && !p.hasDisconnected()) {
			p.connection.send(new ClientboundStopSoundPacket(Ids.id("battlehub:music." + was.song()), SoundSource.RECORDS));
		}
	}

	public static void stop(Collection<ServerPlayer> players) {
		for (ServerPlayer p : players) stop(p);
	}

	/** The piece a player hears now, or null (self-test, inspect). */
	public static String playing(UUID id) {
		Playing p = PLAYING.get(id);
		return p == null ? null : p.song();
	}

	/** Every tick: the note block pieces play on; finished ones end. */
	public static void tick(MinecraftServer server) {
		now++;
		if (PLAYING.isEmpty()) return;
		float vol = volume();
		for (Map.Entry<UUID, Playing> e : new ArrayList<>(PLAYING.entrySet())) {
			Playing pl = e.getValue();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			int at = (int) (now - pl.start());
			if (p == null || p.hasDisconnected() || at > pl.length()) {
				PLAYING.remove(e.getKey());
				continue;
			}
			if (pl.pack()) continue;
			Track t = track(pl.song());
			if (t == null || at < 0 || at >= t.byTick().length || t.byTick()[at] == null) continue;
			for (NoteBlocks.Strike st : t.byTick()[at]) {
				p.connection.send(new ClientboundSoundPacket(note(st.instrument()), SoundSource.RECORDS, p.getX(), p.getY() + 1, p.getZ(),
						st.volume() * vol, st.pitch(), 0L));
			}
		}
	}

	private static Holder<SoundEvent> note(String instrument) {
		return SOUNDS.computeIfAbsent(instrument,
				i -> Holder.direct(SoundEvent.createVariableRangeEvent(Ids.id("minecraft:block.note_block." + i))));
	}

	public static void forget(UUID id) {
		PLAYING.remove(id);
		HISTORY.remove(id);
	}

	public static void clear() {
		PLAYING.clear();
		HISTORY.clear();
	}
}
