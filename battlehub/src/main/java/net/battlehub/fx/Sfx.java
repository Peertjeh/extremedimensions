package net.battlehub.fx;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.battlehub.bot.Bots;
import net.battlehub.config.ConfigManager;
import net.battlehub.util.Ids;
import net.battlehub.util.Tasks;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * BattleHub's own sound effects (1.49), synthesised by tools/music/sfx.py: the cannon when a tribute falls, what the
 * fallen hear, and the night's tribute (the Capitol's seal, the emblem rising, a funeral bell for each of the fallen).
 * With the resource pack a player hears the sound itself; without it a mix of the game's own sounds made for each
 * (a few layers, each with its pitch, volume and delay).
 */
public final class Sfx {
	private Sfx() {}

	public static final String CANNON = "cannon", FALLEN = "fallen", TOLL = "toll", SEAL = "seal", RISE = "rise";
	public static final List<String> ALL = List.of(CANNON, FALLEN, TOLL, SEAL, RISE);

	/** One sound of the game in the mix without the pack: which, how loud, how high, how many ticks later. */
	record Layer(String sound, float volume, float pitch, int delay) {}

	static final Map<String, List<Layer>> WITHOUT_PACK = Map.of(
			CANNON, List.of(new Layer("minecraft:entity.generic.explode", 1f, 0.5f, 0), new Layer("minecraft:entity.warden.sonic_boom", 0.25f, 0.5f, 0),
					new Layer("minecraft:item.trident.thunder", 0.5f, 0.6f, 3), new Layer("minecraft:entity.generic.explode", 0.35f, 0.5f, 9)),
			FALLEN, List.of(new Layer("minecraft:entity.warden.heartbeat", 1f, 0.8f, 0), new Layer("minecraft:entity.warden.heartbeat", 0.7f, 0.7f, 20),
					new Layer("minecraft:block.note_block.bass", 0.9f, 0.6f, 26), new Layer("minecraft:block.note_block.bass", 0.8f, 0.53f, 34),
					new Layer("minecraft:block.beacon.deactivate", 0.6f, 0.5f, 30)),
			TOLL, List.of(new Layer("minecraft:block.bell.use", 1f, 0.5f, 0), new Layer("minecraft:block.note_block.bell", 0.6f, 0.5f, 0),
					new Layer("minecraft:block.bell.resonate", 0.5f, 0.6f, 2)),
			SEAL, List.of(new Layer("minecraft:event.raid.horn", 0.7f, 0.7f, 0), new Layer("minecraft:block.note_block.basedrum", 1f, 0.6f, 0),
					new Layer("minecraft:block.note_block.basedrum", 1f, 0.6f, 4), new Layer("minecraft:block.note_block.basedrum", 1f, 0.55f, 8)),
			RISE, List.of(new Layer("minecraft:block.beacon.activate", 1f, 0.6f, 0), new Layer("minecraft:block.amethyst_block.resonate", 0.8f, 0.7f, 10),
					new Layer("minecraft:block.note_block.chime", 0.7f, 1.0f, 40), new Layer("minecraft:block.note_block.chime", 0.6f, 1.33f, 42)));

	private static final Map<String, Holder<SoundEvent>> EVENTS = new HashMap<>();

	private static boolean on() {
		return ConfigManager.config().effects.sounds;
	}

	private static float volume() {
		return (float) Math.max(0, Math.min(1, ConfigManager.config().effects.soundVolume));
	}

	public static void play(Collection<ServerPlayer> players, String id) {
		for (ServerPlayer p : players) play(p, id);
	}

	/** A sound for one player (nobody else hears it), at their own place. */
	public static void play(ServerPlayer p, String id) {
		if (p == null || p.hasDisconnected() || !on() || Bots.isBot(p)) return;
		float vol = volume();
		if (ResourcePack.loaded(p) && ogg(id) != null) {
			send(p, "battlehub:sfx." + id, vol, 1f);
			return;
		}
		for (Layer l : WITHOUT_PACK.getOrDefault(id, List.of())) {
			if (l.delay() <= 0) send(p, l.sound(), l.volume() * vol, l.pitch());
			else Tasks.later(l.delay(), () -> {
				if (!p.hasDisconnected()) send(p, l.sound(), l.volume() * vol, l.pitch());
			});
		}
	}

	private static void send(ServerPlayer p, String sound, float volume, float pitch) {
		Holder<SoundEvent> h = EVENTS.computeIfAbsent(sound, s -> Holder.direct(SoundEvent.createVariableRangeEvent(Ids.id(s))));
		p.connection.send(new ClientboundSoundPacket(h, SoundSource.MASTER, p.getX(), p.getEyeY(), p.getZ(), volume, pitch, p.level().getRandom().nextLong()));
	}

	/** The synthesised .ogg of a sound (for the resource pack), or null. */
	public static byte[] ogg(String id) {
		try (InputStream in = Sfx.class.getResourceAsStream("/battlehub/sfx/" + id + ".ogg")) {
			return in == null ? null : in.readAllBytes();
		} catch (IOException e) {
			return null;
		}
	}
}
