package net.battlehub.util;

import java.util.Collection;

import net.battlehub.lang.Lang;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Chat, action bar, titles and sounds for single players. All text goes through {@link Lang}. */
public final class Msg {
	private Msg() {}

	public static final String PREFIX_KEY = "prefix";

	public static void send(ServerPlayer p, String key, Object... args) {
		p.sendSystemMessage(Component.empty().append(Lang.tr(p, PREFIX_KEY)).append(Lang.tr(p, key, args)));
	}

	/** Without the "BattleHub »" prefix. */
	public static void raw(ServerPlayer p, Component c) {
		p.sendSystemMessage(c);
	}

	public static void actionBar(ServerPlayer p, String key, Object... args) {
		p.sendOverlayMessage(Lang.tr(p, key, args));
	}

	public static void title(ServerPlayer p, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
		p.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
		p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle == null ? Component.empty() : subtitle));
		p.connection.send(new ClientboundSetTitleTextPacket(title == null ? Component.empty() : title));
	}

	public static void titleKey(ServerPlayer p, String titleKey, String subtitleKey, Object... args) {
		title(p, titleKey == null ? Component.empty() : Lang.tr(p, titleKey, args),
				subtitleKey == null ? Component.empty() : Lang.tr(p, subtitleKey, args), 5, 40, 10);
	}

	public static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
		sound(p, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), volume, pitch);
	}

	public static void sound(ServerPlayer p, Holder<SoundEvent> sound, float volume, float pitch) {
		p.connection.send(new ClientboundSoundPacket(sound, SoundSource.MASTER, p.getX(), p.getY(), p.getZ(), volume, pitch,
				p.level().getRandom().nextLong()));
	}

	/** Same key to everybody, each in their own language. */
	public static void broadcast(MinecraftServer server, String key, Object... args) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			send(p, key, args);
		}
	}

	public static void sendAll(Collection<ServerPlayer> players, String key, Object... args) {
		for (ServerPlayer p : players) {
			send(p, key, args);
		}
	}
}
