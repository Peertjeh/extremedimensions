package net.battlehub.fx;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.battlehub.config.ConfigManager;
import net.battlehub.util.Ids;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * BattleHub's own sounds and music from the optional resource pack. Without the pack nothing extra is played
 * (the vanilla sounds BattleHub always plays stay), so players who said no never hear unknown sounds.
 */
public final class Sounds {
	private Sounds() {}

	public static final String INTRO = "intro";
	public static final String KILL = "kill";
	public static final String HIT = "hit";
	public static final String VICTORY = "victory";
	public static final String JUMP = "jump";
	public static final String DUMMY = "dummy";
	public static final String MUSIC_LOBBY = "music.lobby";

	private static final Map<UUID, Long> MUSIC_AT = new HashMap<>();

	private static Holder<SoundEvent> event(String name) {
		return Holder.direct(SoundEvent.createVariableRangeEvent(Ids.id("battlehub:" + name)));
	}

	/** Plays a BattleHub sound to a player who has the pack. */
	public static void play(ServerPlayer p, String name) {
		play(p, name, SoundSource.MASTER, 1f, 1f);
	}

	private static void play(ServerPlayer p, String name, SoundSource source, float volume, float pitch) {
		if (p == null || !ResourcePack.loaded(p)) {
			return;
		}
		p.connection.send(new ClientboundSoundPacket(event(name), source, p.getX(), p.getY(), p.getZ(), volume, pitch,
				p.level().getRandom().nextLong()));
	}

	/** Lobby music now and then for players with the pack (every few minutes, in the music volume). */
	public static void lobbyMusic(ServerPlayer p, long tick) {
		if (!ConfigManager.config().resourcePack.music || !ResourcePack.loaded(p)) {
			return;
		}
		Long last = MUSIC_AT.get(p.getUUID());
		long every = Math.max(30, ConfigManager.config().resourcePack.musicEverySeconds) * 20L;
		if (last == null || tick - last >= every) {
			MUSIC_AT.put(p.getUUID(), tick);
			play(p, MUSIC_LOBBY, SoundSource.MUSIC, 0.6f, 1f);
		}
	}

	/** Stops the lobby music (a match starts, the player leaves BattleHub). */
	public static void stopMusic(ServerPlayer p) {
		if (MUSIC_AT.remove(p.getUUID()) != null && !p.hasDisconnected()) {
			p.connection.send(new ClientboundStopSoundPacket(Ids.id("battlehub:" + MUSIC_LOBBY), SoundSource.MUSIC));
		}
	}

	public static void forget(UUID id) {
		MUSIC_AT.remove(id);
	}

	public static void clear() {
		MUSIC_AT.clear();
	}
}
