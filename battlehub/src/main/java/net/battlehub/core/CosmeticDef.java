package net.battlehub.core;

import java.util.Locale;

/**
 * A cosmetic from the config, bought with coins. Categories: trail (particles behind you), kill (effect where you
 * defeat someone), arrow (particles behind your arrows), win (effect around you when you win), color (name colour).
 * {@code effect} is a particle id for trails and arrows, an effect name for kills and wins, a colour for names.
 */
public final class CosmeticDef {
	public String id = "";
	public String category = "trail";
	public String display = "";
	/** Item shown in the shop. */
	public String icon = "minecraft:paper";
	public int price;
	public String effect = "";

	public CosmeticDef() {}

	public CosmeticDef(String id, String category, String display, String icon, int price, String effect) {
		this.id = id;
		this.category = category;
		this.display = display;
		this.icon = icon;
		this.price = price;
		this.effect = effect;
	}

	public String category() {
		return category == null ? "" : category.toLowerCase(Locale.ROOT);
	}
}
