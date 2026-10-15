package net.battlehub.fx;

import net.battlehub.core.PixelIcons;
import net.battlehub.lang.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Icons in text: the pixel icons of the resource pack for players who have it, otherwise a symbol from the
 * normal font that every client can show.
 */
public final class Glyphs {
	private Glyphs() {}

	/** The symbol without the pack (with a colour code). */
	public static String fallback(String name) {
		return switch (name) {
			case "sword" -> "&f⚔";
			case "bow" -> "&f➶";
			case "skull" -> "&7☠";
			case "heart" -> "&c❤";
			case "star" -> "&e★";
			case "coin" -> "&6⛃";
			case "trophy", "crown" -> "&6♛";
			case "fire" -> "&6✹";
			case "clock" -> "&7⌚";
			case "shield" -> "&9⛨";
			case "bolt" -> "&e⚡";
			default -> "&7✦";
		};
	}

	public static Component icon(ServerPlayer p, String name) {
		char g = PixelIcons.glyph(name);
		if (g != 0 && p != null && ResourcePack.loaded(p)) {
			// White so the icon keeps its own colours
			return Component.literal(String.valueOf(g)).withStyle(s -> s.withColor(0xFFFFFF));
		}
		return Lang.format(fallback(name));
	}
}
