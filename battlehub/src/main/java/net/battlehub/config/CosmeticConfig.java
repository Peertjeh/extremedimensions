package net.battlehub.config;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.core.CosmeticDef;

/**
 * {@code config/battlehub/cosmetics.json}. Trails and arrow trails take a particle id; kill effects: lightning,
 * explosion, hearts, flames, souls, blood, firework, totem; win effects: fireworks, totem, hearts, notes, flames,
 * sparks; colours: a chat colour name (red, gold, aqua, ...).
 */
public final class CosmeticConfig {
	/** Show trails during matches too (not only in the hub). */
	public boolean trailsInMatches = true;
	public List<CosmeticDef> cosmetics = defaults();

	private static CosmeticDef c(String id, String category, String display, String icon, int price, String effect) {
		return new CosmeticDef(id, category, display, "minecraft:" + icon, price, effect);
	}

	static List<CosmeticDef> defaults() {
		List<CosmeticDef> l = new ArrayList<>();
		// Trails
		l.add(c("trail_flames", "trail", "&6Flames", "blaze_powder", 400, "flame"));
		l.add(c("trail_hearts", "trail", "&cHearts", "poppy", 600, "heart"));
		l.add(c("trail_notes", "trail", "&aMusic", "note_block", 500, "note"));
		l.add(c("trail_snow", "trail", "&fSnowfall", "snowball", 500, "snowflake"));
		l.add(c("trail_magic", "trail", "&dMagic", "enchanted_book", 700, "enchant"));
		l.add(c("trail_sparks", "trail", "&eSparks", "end_rod", 800, "end_rod"));
		l.add(c("trail_portal", "trail", "&5Portal", "obsidian", 800, "portal"));
		l.add(c("trail_cherry", "trail", "&dCherry Blossom", "cherry_sapling", 900, "cherry_leaves"));
		l.add(c("trail_souls", "trail", "&bSoul Fire", "soul_lantern", 900, "soul_fire_flame"));
		l.add(c("trail_glow", "trail", "&3Glow", "glow_ink_sac", 1000, "glow"));
		l.add(c("trail_electric", "trail", "&eElectric", "lightning_rod", 1200, "electric_spark"));
		l.add(c("trail_totem", "trail", "&6&lTotem", "totem_of_undying", 1500, "totem_of_undying"));
		// Kill effects
		l.add(c("kill_hearts", "kill", "&cHeartbreak", "red_dye", 500, "hearts"));
		l.add(c("kill_flames", "kill", "&6Burst of Flames", "fire_charge", 600, "flames"));
		l.add(c("kill_blood", "kill", "&4Blood", "redstone_block", 700, "blood"));
		l.add(c("kill_souls", "kill", "&bSouls", "soul_sand", 800, "souls"));
		l.add(c("kill_explosion", "kill", "&7Explosion", "tnt", 900, "explosion"));
		l.add(c("kill_firework", "kill", "&eFirework", "firework_rocket", 1000, "firework"));
		l.add(c("kill_lightning", "kill", "&b&lLightning", "lightning_rod", 1200, "lightning"));
		l.add(c("kill_totem", "kill", "&6&lTotem", "totem_of_undying", 1500, "totem"));
		// Arrow trails
		l.add(c("arrow_crit", "arrow", "&fCritical", "arrow", 300, "crit"));
		l.add(c("arrow_flames", "arrow", "&6Fire Arrows", "blaze_powder", 400, "flame"));
		l.add(c("arrow_hearts", "arrow", "&cCupid", "poppy", 500, "heart"));
		l.add(c("arrow_notes", "arrow", "&aMusical", "note_block", 500, "note"));
		l.add(c("arrow_sparks", "arrow", "&eShooting Star", "end_rod", 700, "end_rod"));
		l.add(c("arrow_souls", "arrow", "&bSoul Arrows", "soul_lantern", 700, "soul_fire_flame"));
		l.add(c("arrow_portal", "arrow", "&5Void Arrows", "obsidian", 800, "portal"));
		// Win effects
		l.add(c("win_hearts", "win", "&cLove", "red_dye", 600, "hearts"));
		l.add(c("win_notes", "win", "&aVictory Song", "jukebox", 600, "notes"));
		l.add(c("win_flames", "win", "&6Ring of Fire", "fire_charge", 800, "flames"));
		l.add(c("win_sparks", "win", "&eStardust", "end_rod", 1000, "sparks"));
		l.add(c("win_fireworks", "win", "&e&lFireworks", "firework_rocket", 1200, "fireworks"));
		l.add(c("win_totem", "win", "&6&lTotem Rain", "totem_of_undying", 1800, "totem"));
		// Name colours
		l.add(c("color_white", "color", "&fWhite", "white_dye", 200, "white"));
		l.add(c("color_yellow", "color", "&eYellow", "yellow_dye", 300, "yellow"));
		l.add(c("color_gold", "color", "&6Gold", "orange_dye", 300, "gold"));
		l.add(c("color_red", "color", "&cRed", "red_dye", 300, "red"));
		l.add(c("color_green", "color", "&aGreen", "lime_dye", 300, "green"));
		l.add(c("color_aqua", "color", "&bAqua", "light_blue_dye", 300, "aqua"));
		l.add(c("color_blue", "color", "&9Blue", "blue_dye", 300, "blue"));
		l.add(c("color_dark_aqua", "color", "&3Teal", "cyan_dye", 350, "dark_aqua"));
		l.add(c("color_pink", "color", "&dPink", "pink_dye", 400, "light_purple"));
		l.add(c("color_purple", "color", "&5Purple", "purple_dye", 400, "dark_purple"));
		l.add(c("color_dark_red", "color", "&4Crimson", "red_dye", 400, "dark_red"));
		return l;
	}

	public CosmeticConfig normalize() {
		if (cosmetics == null) {
			cosmetics = defaults();
		}
		cosmetics.removeIf(c -> c == null || c.id == null || c.id.isBlank());
		for (CosmeticDef c : cosmetics) {
			if (c.display == null) c.display = c.id;
			if (c.icon == null) c.icon = "minecraft:paper";
			if (c.effect == null) c.effect = "";
			c.price = Math.max(0, c.price);
		}
		return this;
	}
}
