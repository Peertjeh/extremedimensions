package net.battlehub.config;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.core.CosmeticDef;

/**
 * {@code config/battlehub/cosmetics.json}. Trails and arrow trails take a particle text (see ParticleSpec: a
 * particle id, {@code dust:#RRGGBB}, {@code rainbow}, ...); auras "shape:particle"; kill and win effects a name
 * (see Cosmetics); death cries a sound id (optionally ";pitch"); weapon skins the item whose look the sword takes
 * (optionally ";glint"); pets and hats an item; outfits colours; name colours a chat colour name; toppers a model
 * (see core/Toppers); balloons a block; badges "sign colour" (see core/Badges); kill messages and gadgets an id.
 */
public final class CosmeticConfig {
	/**
	 * 2: the wardrobe (hats and outfits) was added. 3: auras, death cries, weapon skins, pets and many more. 4: toppers,
	 * balloons, badges, kill messages, gadgets and more of the rest.
	 */
	public int configVersion;
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

	/** Hats (any item worn on the head) and outfits (dyed leather, a colour "#RRGGBB"), worn in the lobby. */
	static List<CosmeticDef> wardrobe() {
		List<CosmeticDef> l = new ArrayList<>();
		l.add(c("hat_glass", "hat", "&bGlass Helmet", "glass", 300, "minecraft:glass"));
		l.add(c("hat_slime", "hat", "&aSlime", "slime_block", 500, "minecraft:slime_block"));
		l.add(c("hat_lantern", "hat", "&6Jack o'Lantern", "jack_o_lantern", 500, "minecraft:jack_o_lantern"));
		l.add(c("hat_tnt", "hat", "&cTNT", "tnt", 600, "minecraft:tnt"));
		l.add(c("hat_cake", "hat", "&fCake", "cake", 700, "minecraft:cake"));
		l.add(c("hat_sea_lantern", "hat", "&3Sea Lantern", "sea_lantern", 700, "minecraft:sea_lantern"));
		l.add(c("hat_gold", "hat", "&6Gold Block", "gold_block", 900, "minecraft:gold_block"));
		l.add(c("hat_creeper", "hat", "&aCreeper Head", "creeper_head", 1000, "minecraft:creeper_head"));
		l.add(c("hat_beacon", "hat", "&b&lBeacon", "beacon", 1500, "minecraft:beacon"));
		l.add(c("hat_dragon", "hat", "&5&lDragon Head", "dragon_head", 2000, "minecraft:dragon_head"));
		l.add(c("outfit_white", "outfit", "&fWhite Outfit", "white_wool", 400, "#F9FFFE"));
		l.add(c("outfit_red", "outfit", "&cRed Outfit", "red_wool", 400, "#B02E26"));
		l.add(c("outfit_blue", "outfit", "&9Blue Outfit", "blue_wool", 400, "#3C44AA"));
		l.add(c("outfit_green", "outfit", "&2Green Outfit", "green_wool", 400, "#5E7C16"));
		l.add(c("outfit_black", "outfit", "&8Black Outfit", "black_wool", 500, "#1D1D21"));
		l.add(c("outfit_pink", "outfit", "&dPink Outfit", "pink_wool", 500, "#F38BAA"));
		l.add(c("outfit_purple", "outfit", "&5Purple Outfit", "purple_wool", 500, "#8932B8"));
		l.add(c("outfit_gold", "outfit", "&6Golden Outfit", "yellow_wool", 800, "#F9C74F"));
		return l;
	}

	/** Everything added in configVersion 3. */
	static List<CosmeticDef> more() {
		List<CosmeticDef> l = new ArrayList<>();
		// Trails: plain, coloured and mixed particles
		l.add(c("trail_rainbow", "trail", "&c&lR&6&la&e&li&a&ln&b&lb&9&lo&d&lw", "prismarine_crystals", 1600, "rainbow:1.1"));
		l.add(c("trail_emerald", "trail", "&aEmerald Sparkle", "emerald", 600, "happy_villager"));
		l.add(c("trail_rage", "trail", "&4Rage", "fire_charge", 600, "angry_villager"));
		l.add(c("trail_bubbles", "trail", "&bBubbles", "pufferfish", 500, "bubble_pop"));
		l.add(c("trail_spores", "trail", "&dSpore Blossom", "spore_blossom", 700, "falling_spore_blossom"));
		l.add(c("trail_crimson", "trail", "&cCrimson Spores", "crimson_fungus", 600, "crimson_spore"));
		l.add(c("trail_warped", "trail", "&3Warped Spores", "warped_fungus", 600, "warped_spore"));
		l.add(c("trail_ash", "trail", "&7Ash", "basalt", 500, "ash+white_ash"));
		l.add(c("trail_honey", "trail", "&6Honey Drops", "honey_bottle", 700, "falling_honey"));
		l.add(c("trail_sculk", "trail", "&3Sculk Souls", "sculk", 1100, "sculk_soul"));
		l.add(c("trail_wax", "trail", "&eWax", "honeycomb", 600, "wax_on"));
		l.add(c("trail_copper_fire", "trail", "&aCopper Fire", "copper_torch", 900, "copper_fire_flame"));
		l.add(c("trail_fireflies", "trail", "&eFireflies", "firefly_bush", 1000, "firefly"));
		l.add(c("trail_pale", "trail", "&fPale Leaves", "pale_oak_sapling", 800, "pale_oak_leaves"));
		l.add(c("trail_autumn", "trail", "&6Autumn Leaves", "oak_leaves", 900, "tint:tinted_leaves:#E07020"));
		l.add(c("trail_ruby", "trail", "&cRuby Dust", "redstone", 500, "dust:#E0115F:1.2"));
		l.add(c("trail_sapphire", "trail", "&9Sapphire Dust", "lapis_lazuli", 500, "dust:#0F52BA:1.2"));
		l.add(c("trail_gold_dust", "trail", "&6Gold Dust", "gold_nugget", 700, "dust:#FFD700:1.0+dust:#FFF3A0:0.6"));
		l.add(c("trail_toxic", "trail", "&aToxic", "slime_ball", 800, "dust:#7CFF00>#1F5F00:1.3"));
		l.add(c("trail_inferno", "trail", "&6Inferno", "magma_cream", 1000, "dust:#FFB000>#FF2000:1.4+flame"));
		l.add(c("trail_frost", "trail", "&bFrostbite", "blue_ice", 900, "dust:#E0FFFF>#5FA8FF:1.2+snowflake"));
		l.add(c("trail_void", "trail", "&5Void", "ender_eye", 1100, "dust:#5A1E8C>#050008:1.5+reverse_portal"));
		l.add(c("trail_light", "trail", "&bLight Streaks", "glowstone_dust", 1300, "trail:#7DF9FF"));
		l.add(c("trail_ominous", "trail", "&8Ominous", "ominous_bottle", 1400, "ominous_spawning"));
		l.add(c("trail_gold_blocks", "trail", "&6Midas Touch", "gold_block", 1500, "block:gold_block"));
		l.add(c("trail_diamonds", "trail", "&b&lDiamond Rain", "diamond", 2000, "item:diamond"));
		// Auras: shapes of particles round you in the lobby
		l.add(c("aura_angel", "aura", "&f&lAngel Wings", "feather", 2500, "wings:dust:#FFFFFF:1.0"));
		l.add(c("aura_demon", "aura", "&4&lDemon Wings", "crimson_roots", 2500, "wings:dust:#8B0000:1.1+dust:#2B0000:0.8"));
		l.add(c("aura_fire_wings", "aura", "&6&lPhoenix Wings", "blaze_rod", 3000, "wings:flame"));
		l.add(c("aura_ice_wings", "aura", "&b&lFrost Wings", "packed_ice", 2800, "wings:snowflake"));
		l.add(c("aura_rainbow_wings", "aura", "&d&lRainbow Wings", "white_banner", 3500, "wings:rainbow"));
		l.add(c("aura_soul_wings", "aura", "&3&lSoul Wings", "soul_lantern", 3000, "wings:soul_fire_flame"));
		l.add(c("aura_halo", "aura", "&fHalo", "end_rod", 1200, "halo:end_rod"));
		l.add(c("aura_golden_halo", "aura", "&6Golden Halo", "gold_ingot", 1500, "halo:dust:#FFD700:0.8"));
		l.add(c("aura_crown", "aura", "&6&lCrown", "golden_helmet", 2000, "crown:dust:#FFD700:0.9"));
		l.add(c("aura_flame_crown", "aura", "&cCrown of Fire", "fire_charge", 2000, "crown:flame"));
		l.add(c("aura_orbit", "aura", "&dArcane Orbit", "enchanting_table", 1300, "orbit:enchant"));
		l.add(c("aura_fire_orbit", "aura", "&6Fire Orbit", "magma_block", 1300, "orbit:flame"));
		l.add(c("aura_fireflies", "aura", "&eFirefly Swarm", "firefly_bush", 1600, "orbit:firefly"));
		l.add(c("aura_helix", "aura", "&bDNA Helix", "glow_ink_sac", 1800, "helix:dust:#00FFAA>#AA00FF:0.9"));
		l.add(c("aura_rain", "aura", "&9Rain Cloud", "water_bucket", 1400, "cloud:falling_water"));
		l.add(c("aura_storm", "aura", "&eThunder Cloud", "lightning_rod", 1800, "cloud:electric_spark"));
		l.add(c("aura_snow", "aura", "&fSnow Cloud", "snow_block", 1400, "cloud:snowflake"));
		l.add(c("aura_spiral", "aura", "&eStar Spiral", "nether_star", 1500, "spiral:end_rod"));
		l.add(c("aura_galaxy", "aura", "&5Galaxy", "dragon_egg", 2200, "galaxy:dust:#9B59FF:0.8+dust:#3EC1FF:0.6"));
		l.add(c("aura_heart", "aura", "&cBig Heart", "red_dye", 1600, "heart:dust:#FF3355:0.9"));
		l.add(c("aura_soul_ring", "aura", "&3Soul Ring", "soul_campfire", 1200, "ring:soul_fire_flame"));
		l.add(c("aura_tornado", "aura", "&7Tornado", "wind_charge", 2000, "tornado:dust:#DDDDDD:1.2+small_gust"));
		l.add(c("aura_cape", "aura", "&4Royal Cape", "red_banner", 2200, "cape:dust:#B00020>#4A0010:1.1"));
		// Arrow trails
		l.add(c("arrow_rainbow", "arrow", "&dRainbow Arrows", "spectral_arrow", 1200, "rainbow:1.2"));
		l.add(c("arrow_snow", "arrow", "&fFrost Arrows", "snowball", 500, "snowflake"));
		l.add(c("arrow_electric", "arrow", "&eStorm Arrows", "lightning_rod", 800, "electric_spark"));
		l.add(c("arrow_sculk", "arrow", "&3Sculk Arrows", "sculk", 900, "sculk_soul"));
		l.add(c("arrow_cherry", "arrow", "&dPetal Arrows", "cherry_sapling", 800, "cherry_leaves"));
		l.add(c("arrow_emerald", "arrow", "&aEmerald Arrows", "emerald", 600, "happy_villager"));
		l.add(c("arrow_light", "arrow", "&bLaser Arrows", "glowstone_dust", 1300, "trail:#FF3030"));
		l.add(c("arrow_ender", "arrow", "&5Ender Arrows", "ender_pearl", 900, "dust:#5A1E8C:1.2+portal"));
		l.add(c("arrow_totem", "arrow", "&6&lTotem Arrows", "totem_of_undying", 1500, "totem_of_undying"));
		// Kill effects
		l.add(c("kill_smite", "kill", "&e&lSmite", "trident", 1800, "smite"));
		l.add(c("kill_rocket", "kill", "&d&lFirework Burst", "firework_star", 1600, "rocket"));
		l.add(c("kill_frost", "kill", "&bShatter", "blue_ice", 900, "frost"));
		l.add(c("kill_sculk", "kill", "&3Sculk Shriek", "sculk_shrieker", 1300, "sculk"));
		l.add(c("kill_gust", "kill", "&fGust", "wind_charge", 1000, "gust"));
		l.add(c("kill_confetti", "kill", "&dConfetti", "pink_dye", 1100, "confetti"));
		l.add(c("kill_ender", "kill", "&5Ender", "ender_pearl", 900, "ender"));
		l.add(c("kill_cherry", "kill", "&dPetals", "cherry_leaves", 800, "cherry"));
		l.add(c("kill_sonic", "kill", "&3&lSonic Boom", "echo_shard", 2000, "sonic"));
		l.add(c("kill_squid", "kill", "&8Ink Splash", "ink_sac", 700, "squid"));
		l.add(c("kill_bubbles", "kill", "&bBubbles", "pufferfish", 700, "bubbles"));
		l.add(c("kill_skull", "kill", "&8Wither Skull", "wither_skeleton_skull", 1400, "skull"));
		l.add(c("kill_gold", "kill", "&6Jackpot", "gold_block", 1200, "gold"));
		l.add(c("kill_notes", "kill", "&aSong of Defeat", "note_block", 700, "notes"));
		// Win effects
		l.add(c("win_rockets", "win", "&d&lGrand Finale", "firework_star", 2200, "rockets"));
		l.add(c("win_storm", "win", "&e&lThunderstorm", "lightning_rod", 2000, "storm"));
		l.add(c("win_rainbow", "win", "&c&lR&6&la&e&li&a&ln&b&lb&9&lo&d&lw", "white_banner", 1500, "rainbow"));
		l.add(c("win_dragon", "win", "&5&lDragon's Breath", "dragon_head", 2500, "dragon"));
		l.add(c("win_galaxy", "win", "&5Galaxy", "dragon_egg", 1800, "galaxy"));
		l.add(c("win_snow", "win", "&fBlizzard", "snow_block", 1000, "snow"));
		l.add(c("win_confetti", "win", "&dConfetti Rain", "pink_dye", 1200, "confetti"));
		l.add(c("win_aurora", "win", "&bAurora", "prismarine_crystals", 1800, "aurora"));
		l.add(c("win_sakura", "win", "&dSakura", "cherry_sapling", 1300, "sakura"));
		l.add(c("win_souls", "win", "&3Soul Pyre", "soul_campfire", 1300, "souls"));
		l.add(c("win_gold", "win", "&6Gold Rain", "gold_block", 1600, "gold_rain"));
		// Death cries
		l.add(c("cry_goat", "cry", "&fScreaming Goat", "goat_horn", 600, "entity.goat.screaming.death"));
		l.add(c("cry_villager", "cry", "&6Villager", "emerald", 400, "entity.villager.death"));
		l.add(c("cry_wither", "cry", "&8Wither", "wither_skeleton_skull", 1200, "entity.wither.death;1.2"));
		l.add(c("cry_dragon", "cry", "&5Dragon Roar", "dragon_head", 1500, "entity.ender_dragon.growl"));
		l.add(c("cry_ghast", "cry", "&fGhast", "ghast_tear", 800, "entity.ghast.scream"));
		l.add(c("cry_warden", "cry", "&3Warden", "sculk_shrieker", 1300, "entity.warden.roar"));
		l.add(c("cry_pig", "cry", "&dPiggy", "porkchop", 400, "entity.pig.death;1.3"));
		l.add(c("cry_anvil", "cry", "&7Anvil", "anvil", 600, "block.anvil.land"));
		l.add(c("cry_glass", "cry", "&bShattered", "glass", 500, "block.glass.break;0.8"));
		l.add(c("cry_bell", "cry", "&6Bell", "bell", 600, "block.bell.use"));
		l.add(c("cry_horn", "cry", "&eWar Horn", "goat_horn", 1000, "item.goat_horn.sound.0"));
		l.add(c("cry_totem", "cry", "&6Totem", "totem_of_undying", 900, "item.totem.use;1.4"));
		l.add(c("cry_witch", "cry", "&5Witch Laugh", "potion", 700, "entity.witch.celebrate"));
		l.add(c("cry_allay", "cry", "&bAllay", "amethyst_shard", 800, "entity.allay.death"));
		l.add(c("cry_enderman", "cry", "&5Enderman", "ender_pearl", 900, "entity.enderman.scream"));
		l.add(c("cry_creeper", "cry", "&aHiss", "creeper_head", 700, "entity.creeper.primed"));
		// Weapon skins: your sword takes the look of another item in matches (it still is the same sword)
		l.add(c("skin_blaze", "skin", "&6Blaze Rod", "blaze_rod", 1200, "minecraft:blaze_rod;glint"));
		l.add(c("skin_breeze", "skin", "&bBreeze Rod", "breeze_rod", 1200, "minecraft:breeze_rod"));
		l.add(c("skin_bone", "skin", "&fBone Club", "bone", 600, "minecraft:bone"));
		l.add(c("skin_stick", "skin", "&eStick of Truth", "stick", 500, "minecraft:stick;glint"));
		l.add(c("skin_trident", "skin", "&3Trident", "trident", 2000, "minecraft:trident"));
		l.add(c("skin_spear", "skin", "&bDiamond Spear", "diamond_spear", 1800, "minecraft:diamond_spear"));
		l.add(c("skin_netherite_spear", "skin", "&8Netherite Spear", "netherite_spear", 2200, "minecraft:netherite_spear;glint"));
		l.add(c("skin_golden", "skin", "&6Golden Blade", "golden_sword", 900, "minecraft:golden_sword;glint"));
		l.add(c("skin_netherite", "skin", "&8Netherite Edge", "netherite_sword", 1500, "minecraft:netherite_sword"));
		l.add(c("skin_copper", "skin", "&6Copper Sword", "copper_sword", 700, "minecraft:copper_sword"));
		l.add(c("skin_echo", "skin", "&3Echo Dagger", "echo_shard", 1400, "minecraft:echo_shard;glint"));
		l.add(c("skin_amethyst", "skin", "&dAmethyst Shard", "amethyst_shard", 1000, "minecraft:amethyst_shard"));
		l.add(c("skin_fish", "skin", "&bFish Slap", "cod", 800, "minecraft:cod"));
		l.add(c("skin_carrot", "skin", "&6Carrot on a Stick", "carrot_on_a_stick", 600, "minecraft:carrot_on_a_stick"));
		l.add(c("skin_feather", "skin", "&fFeather", "feather", 700, "minecraft:feather;glint"));
		l.add(c("skin_scythe", "skin", "&bScythe", "diamond_hoe", 1300, "minecraft:diamond_hoe;glint"));
		l.add(c("skin_lightsaber", "skin", "&f&lLightsaber", "end_rod", 2500, "minecraft:end_rod"));
		l.add(c("skin_mace", "skin", "&7War Hammer", "mace", 2000, "minecraft:mace"));
		// Pets: float next to you in the lobby
		l.add(c("pet_creeper", "pet", "&aMini Creeper", "creeper_head", 1200, "minecraft:creeper_head"));
		l.add(c("pet_dragon", "pet", "&5Baby Dragon", "dragon_head", 2500, "minecraft:dragon_head"));
		l.add(c("pet_ghast", "pet", "&fLittle Ghast", "dried_ghast", 1800, "minecraft:dried_ghast"));
		l.add(c("pet_golem", "pet", "&6Copper Golem", "copper_golem_statue", 2000, "minecraft:copper_golem_statue"));
		l.add(c("pet_cake", "pet", "&fCake", "cake", 800, "minecraft:cake"));
		l.add(c("pet_slime", "pet", "&aSlime", "slime_block", 900, "minecraft:slime_block"));
		l.add(c("pet_bee", "pet", "&eBee Nest", "bee_nest", 1000, "minecraft:bee_nest"));
		l.add(c("pet_beacon", "pet", "&b&lBeacon", "beacon", 2200, "minecraft:beacon"));
		l.add(c("pet_star", "pet", "&f&lNether Star", "nether_star", 2400, "minecraft:nether_star"));
		l.add(c("pet_heart", "pet", "&3Heart of the Sea", "heart_of_the_sea", 1800, "minecraft:heart_of_the_sea"));
		l.add(c("pet_totem", "pet", "&6Totem", "totem_of_undying", 1600, "minecraft:totem_of_undying"));
		l.add(c("pet_eye", "pet", "&2Eye of Ender", "ender_eye", 1200, "minecraft:ender_eye"));
		l.add(c("pet_crystal", "pet", "&dEnd Crystal", "end_crystal", 2000, "minecraft:end_crystal"));
		l.add(c("pet_amethyst", "pet", "&dAmethyst", "amethyst_cluster", 1000, "minecraft:amethyst_cluster"));
		l.add(c("pet_conduit", "pet", "&3Conduit", "conduit", 1800, "minecraft:conduit"));
		l.add(c("pet_core", "pet", "&7Heavy Core", "heavy_core", 1500, "minecraft:heavy_core"));
		l.add(c("pet_pumpkin", "pet", "&6Jack o'Lantern", "jack_o_lantern", 900, "minecraft:jack_o_lantern"));
		l.add(c("pet_skull", "pet", "&8Wither Skull", "wither_skeleton_skull", 1400, "minecraft:wither_skeleton_skull"));
		l.add(c("pet_piglin", "pet", "&6Piglin", "piglin_head", 1200, "minecraft:piglin_head"));
		l.add(c("pet_egg", "pet", "&5&lDragon Egg", "dragon_egg", 3000, "minecraft:dragon_egg"));
		l.add(c("pet_heart_block", "pet", "&6Creaking Heart", "creaking_heart", 1300, "minecraft:creaking_heart"));
		// More hats
		l.add(c("hat_skeleton", "hat", "&fSkeleton Skull", "skeleton_skull", 700, "minecraft:skeleton_skull"));
		l.add(c("hat_wither", "hat", "&8Wither Skull", "wither_skeleton_skull", 1200, "minecraft:wither_skeleton_skull"));
		l.add(c("hat_zombie", "hat", "&2Zombie Head", "zombie_head", 800, "minecraft:zombie_head"));
		l.add(c("hat_piglin", "hat", "&6Piglin Head", "piglin_head", 1200, "minecraft:piglin_head"));
		l.add(c("hat_pumpkin", "hat", "&6Pumpkin", "carved_pumpkin", 400, "minecraft:carved_pumpkin"));
		l.add(c("hat_honey", "hat", "&6Honey Block", "honey_block", 600, "minecraft:honey_block"));
		l.add(c("hat_ice", "hat", "&bIce Cube", "ice", 400, "minecraft:ice"));
		l.add(c("hat_glowstone", "hat", "&eGlowstone", "glowstone", 600, "minecraft:glowstone"));
		l.add(c("hat_diamond", "hat", "&b&lDiamond Block", "diamond_block", 1800, "minecraft:diamond_block"));
		l.add(c("hat_emerald", "hat", "&a&lEmerald Block", "emerald_block", 1600, "minecraft:emerald_block"));
		l.add(c("hat_amethyst", "hat", "&dAmethyst", "amethyst_block", 900, "minecraft:amethyst_block"));
		l.add(c("hat_sculk", "hat", "&3Sculk Sensor", "sculk_sensor", 1100, "minecraft:sculk_sensor"));
		l.add(c("hat_conduit", "hat", "&3Conduit", "conduit", 1500, "minecraft:conduit"));
		l.add(c("hat_core", "hat", "&7Heavy Core", "heavy_core", 1500, "minecraft:heavy_core"));
		l.add(c("hat_ender_chest", "hat", "&5Ender Chest", "ender_chest", 1000, "minecraft:ender_chest"));
		l.add(c("hat_crafter", "hat", "&7Crafter", "crafter", 900, "minecraft:crafter"));
		l.add(c("hat_vault", "hat", "&6Vault", "vault", 1600, "minecraft:vault"));
		l.add(c("hat_lamp", "hat", "&6Redstone Lamp", "redstone_lamp", 600, "minecraft:redstone_lamp"));
		l.add(c("hat_target", "hat", "&cTarget", "target", 500, "minecraft:target"));
		l.add(c("hat_note", "hat", "&aNote Block", "note_block", 500, "minecraft:note_block"));
		l.add(c("hat_melon", "hat", "&aMelon", "melon", 400, "minecraft:melon"));
		l.add(c("hat_beehive", "hat", "&eBeehive", "beehive", 700, "minecraft:beehive"));
		l.add(c("hat_cherry", "hat", "&dCherry Leaves", "cherry_leaves", 600, "minecraft:cherry_leaves"));
		l.add(c("hat_azalea", "hat", "&aFlowering Azalea", "flowering_azalea", 600, "minecraft:flowering_azalea"));
		l.add(c("hat_shroomlight", "hat", "&6Shroomlight", "shroomlight", 700, "minecraft:shroomlight"));
		l.add(c("hat_pot", "hat", "&6Decorated Pot", "decorated_pot", 800, "minecraft:decorated_pot"));
		l.add(c("hat_golem", "hat", "&6Copper Golem", "copper_golem_statue", 1800, "minecraft:copper_golem_statue"));
		l.add(c("hat_egg", "hat", "&5&lDragon Egg", "dragon_egg", 2500, "minecraft:dragon_egg"));
		// More outfits, many fading from top to bottom
		l.add(c("outfit_cyan", "outfit", "&3Cyan Outfit", "cyan_wool", 400, "#169C9C"));
		l.add(c("outfit_lime", "outfit", "&aLime Outfit", "lime_wool", 400, "#80C71F"));
		l.add(c("outfit_orange", "outfit", "&6Orange Outfit", "orange_wool", 400, "#F9801D"));
		l.add(c("outfit_brown", "outfit", "&6Brown Outfit", "brown_wool", 400, "#835432"));
		l.add(c("outfit_sunset", "outfit", "&6Sunset", "orange_terracotta", 1000, "#FFB000>#8A2BE2"));
		l.add(c("outfit_ocean", "outfit", "&bOcean", "prismarine", 1000, "#00E5FF>#003E8A"));
		l.add(c("outfit_fire", "outfit", "&cFire", "magma_block", 1100, "#FFE000,#FF6A00,#B00000"));
		l.add(c("outfit_forest", "outfit", "&2Forest", "moss_block", 1000, "#9DFF4A>#0B5D1E"));
		l.add(c("outfit_ice", "outfit", "&bGlacier", "packed_ice", 1000, "#F0FFFF>#4F8FFF"));
		l.add(c("outfit_candy", "outfit", "&dCandy Cane", "pink_wool", 900, "#FF4F8B,#FFFFFF,#FF4F8B"));
		l.add(c("outfit_royal", "outfit", "&5Royal", "purple_wool", 1300, "#FFD700,#4B0082,#FFD700"));
		l.add(c("outfit_midnight", "outfit", "&8Midnight", "black_wool", 1000, "#3A3A8A>#050510"));
		l.add(c("outfit_rgb", "outfit", "&c&lR&a&lG&9&lB", "white_wool", 1400, "#FF0000,#00FF00,#0000FF"));
		l.add(c("outfit_cherry", "outfit", "&dCherry Blossom", "cherry_leaves", 1100, "#FFD1DC>#FF1493"));
		l.add(c("outfit_toxic", "outfit", "&aToxic", "slime_block", 1100, "#ADFF2F>#1F5F2F"));
		l.add(c("outfit_lava", "outfit", "&6Lava", "magma_block", 1200, "#FF8C00>#2B0000"));
		l.add(c("outfit_vapor", "outfit", "&dVaporwave", "purpur_block", 1500, "#FF71CE,#B967FF,#01CDFE"));
		l.add(c("outfit_ender", "outfit", "&5Ender", "end_stone", 1200, "#2E1A47>#000000"));
		l.add(c("outfit_gold_black", "outfit", "&6Gold & Black", "gilded_blackstone", 1600, "#FFD700,#111111,#FFD700"));
		return l;
	}

	/** Everything added in configVersion 4 (1.44): toppers, balloons, badges, kill messages, gadgets and more. */
	static List<CosmeticDef> v4() {
		List<CosmeticDef> l = new ArrayList<>();
		// Toppers: small models over your head (core/Toppers)
		l.add(c("topper_crown", "topper", "&6&lGolden Crown", "golden_helmet", 1500, "crown"));
		l.add(c("topper_halo", "topper", "&eHalo", "glowstone", 1400, "halo"));
		l.add(c("topper_horns", "topper", "&4Devil Horns", "red_nether_bricks", 1200, "horns"));
		l.add(c("topper_raincloud", "topper", "&7Rain Cloud", "white_wool", 1300, "raincloud"));
		l.add(c("topper_propeller", "topper", "&ePropeller Cap", "red_wool", 1100, "propeller"));
		l.add(c("topper_gems", "topper", "&b&lOrbiting Gems", "diamond", 1800, "gems"));
		l.add(c("topper_wizard", "topper", "&5Wizard's Hat", "purple_wool", 1300, "wizard"));
		l.add(c("topper_flowers", "topper", "&dFlower Crown", "poppy", 1000, "flowers"));
		l.add(c("topper_firering", "topper", "&6Ring of Fire", "magma_block", 1600, "firering"));
		l.add(c("topper_ice", "topper", "&bIce Crystal", "blue_ice", 1500, "ice"));
		l.add(c("topper_cake", "topper", "&dBirthday Cake", "cake", 1200, "cake"));
		l.add(c("topper_pirate", "topper", "&8Pirate's Hat", "black_wool", 1300, "pirate"));
		l.add(c("topper_tophat", "topper", "&8Top Hat", "black_wool", 1000, "tophat"));
		l.add(c("topper_chef", "topper", "&fChef's Hat", "white_wool", 900, "chef"));
		// Balloons: a block on a string by your hand
		l.add(c("balloon_red", "balloon", "&cRed Balloon", "red_concrete", 500, "minecraft:red_concrete"));
		l.add(c("balloon_blue", "balloon", "&9Blue Balloon", "blue_concrete", 500, "minecraft:blue_concrete"));
		l.add(c("balloon_lime", "balloon", "&aLime Balloon", "lime_concrete", 500, "minecraft:lime_concrete"));
		l.add(c("balloon_gold", "balloon", "&6Golden Balloon", "gold_block", 900, "minecraft:gold_block"));
		l.add(c("balloon_slime", "balloon", "&aSlime Balloon", "slime_block", 800, "minecraft:slime_block"));
		l.add(c("balloon_tnt", "balloon", "&cTNT Balloon", "tnt", 900, "minecraft:tnt"));
		l.add(c("balloon_target", "balloon", "&cTarget Balloon", "target", 700, "minecraft:target"));
		l.add(c("balloon_pumpkin", "balloon", "&6Jack o'Balloon", "jack_o_lantern", 900, "minecraft:jack_o_lantern"));
		l.add(c("balloon_moon", "balloon", "&fFull Moon", "pearlescent_froglight", 1000, "minecraft:pearlescent_froglight"));
		l.add(c("balloon_disco", "balloon", "&bDisco Ball", "sea_lantern", 1000, "minecraft:sea_lantern"));
		l.add(c("balloon_honey", "balloon", "&6Honey Balloon", "honey_block", 800, "minecraft:honey_block"));
		l.add(c("balloon_melon", "balloon", "&aMelon Balloon", "melon", 600, "minecraft:melon"));
		l.add(c("balloon_ender", "balloon", "&5Crying Balloon", "crying_obsidian", 1000, "minecraft:crying_obsidian"));
		l.add(c("balloon_beacon", "balloon", "&b&lBeacon Balloon", "beacon", 2000, "minecraft:beacon"));
		// Badges: a sign after your name
		l.add(c("badge_star", "badge", "&6★ Star", "nether_star", 300, "★ #FFD700"));
		l.add(c("badge_sparkle", "badge", "&b✦ Sparkle", "amethyst_shard", 300, "✦ #55FFFF"));
		l.add(c("badge_heart", "badge", "&c❤ Heart", "red_dye", 300, "❤ #FF5555"));
		l.add(c("badge_swords", "badge", "&7⚔ Swords", "iron_sword", 400, "⚔ #C0C0C0"));
		l.add(c("badge_skull", "badge", "&f☠ Skull", "skeleton_skull", 400, "☠ #E0E0E0"));
		l.add(c("badge_bolt", "badge", "&e⚡ Lightning", "lightning_rod", 400, "⚡ #FFFF55"));
		l.add(c("badge_crown", "badge", "&6♛ Crown", "gold_ingot", 600, "♛ #FFAA00"));
		l.add(c("badge_sun", "badge", "&e☀ Sun", "sunflower", 400, "☀ #FFD24F"));
		l.add(c("badge_moon", "badge", "&9☾ Moon", "clock", 400, "☾ #AAB4FF"));
		l.add(c("badge_flower", "badge", "&d✿ Blossom", "pink_tulip", 400, "✿ #FF8AD8"));
		l.add(c("badge_note", "badge", "&a♪ Note", "note_block", 300, "♪ #55FF55"));
		l.add(c("badge_snow", "badge", "&b❄ Snowflake", "snowball", 400, "❄ #AEEBFF"));
		l.add(c("badge_yinyang", "badge", "&f☯ Balance", "white_wool", 500, "☯ #FFFFFF"));
		l.add(c("badge_infinity", "badge", "&5∞ Infinity", "ender_pearl", 600, "∞ #C44FFF"));
		l.add(c("badge_omega", "badge", "&3Ω Omega", "heart_of_the_sea", 700, "Ω #00E5FF"));
		l.add(c("badge_radioactive", "badge", "&a☢ Radioactive", "slime_ball", 600, "☢ #7CFC00"));
		l.add(c("badge_two_stars", "badge", "&6★★ Rising Star", "glowstone_dust", 800, "★★ #FFD700>#FF6A00"));
		l.add(c("badge_three_sparks", "badge", "&d✦✦✦ Starlight", "end_crystal", 900, "✦✦✦ #FF4F8B>#4F8BFF"));
		l.add(c("badge_hearts", "badge", "&c♥♥♥ Sweetheart", "pink_dye", 900, "♥♥♥ #FF0000>#FF9EC4"));
		l.add(c("badge_fire", "badge", "&6✪ Champion", "blaze_powder", 1200, "✪✪✪ #FFE000>#FF2000"));
		// Kill messages: your own line in the chat and the killfeed
		l.add(c("killmsg_bonk", "killmsg", "&eBonk!", "wooden_shovel", 400, "bonk"));
		l.add(c("killmsg_shadow", "killmsg", "&8Shadow Realm", "black_dye", 600, "shadow"));
		l.add(c("killmsg_yeet", "killmsg", "&bYeet", "feather", 500, "yeet"));
		l.add(c("killmsg_roast", "killmsg", "&6Roasted", "cooked_chicken", 500, "roast"));
		l.add(c("killmsg_pixel", "killmsg", "&aPixelated", "map", 600, "pixel"));
		l.add(c("killmsg_frozen", "killmsg", "&bFrozen Solid", "packed_ice", 600, "frozen"));
		l.add(c("killmsg_storm", "killmsg", "&eThunderstruck", "lightning_rod", 700, "storm"));
		l.add(c("killmsg_royal", "killmsg", "&6Royal Decree", "gold_ingot", 800, "royal"));
		l.add(c("killmsg_cookie", "killmsg", "&6Crumbled", "cookie", 400, "cookie"));
		l.add(c("killmsg_deleted", "killmsg", "&cDeleted", "barrier", 700, "deleted"));
		l.add(c("killmsg_launched", "killmsg", "&fLaunched", "firework_rocket", 700, "launched"));
		l.add(c("killmsg_spooky", "killmsg", "&5Spooked", "carved_pumpkin", 600, "spooky"));
		l.add(c("killmsg_tea", "killmsg", "&aTea Time", "flower_pot", 500, "tea"));
		l.add(c("killmsg_dragon", "killmsg", "&5Dragon's Breath", "dragon_breath", 900, "dragon"));
		l.add(c("killmsg_glitch", "killmsg", "&dGlitched", "command_block", 900, "glitch"));
		// Gadgets: lobby toys in your hotbar
		l.add(c("gadget_confetti", "gadget", "&dConfetti Cannon", "pink_dye", 600, "confetti"));
		l.add(c("gadget_firework", "gadget", "&cFirework Launcher", "firework_rocket", 900, "firework"));
		l.add(c("gadget_rainbow", "gadget", "&aRainbow Beam", "prismarine_crystals", 1100, "rainbow"));
		l.add(c("gadget_disco", "gadget", "&bDisco Ball", "sea_lantern", 1500, "disco"));
		l.add(c("gadget_bubbles", "gadget", "&3Bubble Blower", "glass_bottle", 600, "bubbles"));
		l.add(c("gadget_wand", "gadget", "&5Magic Wand", "blaze_rod", 1000, "wand"));
		l.add(c("gadget_thunder", "gadget", "&eThunder Clap", "lightning_rod", 1200, "thunder"));
		l.add(c("gadget_snowglobe", "gadget", "&fSnow Globe", "snow_block", 900, "snowglobe"));
		l.add(c("gadget_smoke", "gadget", "&7Smoke Bomb", "gunpowder", 700, "smoke"));
		l.add(c("gadget_popper", "gadget", "&eParty Popper", "firework_star", 500, "popper"));
		l.add(c("gadget_kiss", "gadget", "&cBlow a Kiss", "poppy", 600, "kiss"));
		l.add(c("gadget_boombox", "gadget", "&6Boombox", "jukebox", 1300, "boombox"));
		// More kill effects
		l.add(c("kill_grave", "kill", "&7Gravestone", "polished_andesite", 1000, "grave"));
		l.add(c("kill_anvil", "kill", "&8Anvil Drop", "anvil", 1100, "anvil"));
		l.add(c("kill_ice_prison", "kill", "&bIce Prison", "ice", 1200, "ice_prison"));
		l.add(c("kill_black_hole", "kill", "&5&lBlack Hole", "ender_eye", 1600, "black_hole"));
		l.add(c("kill_shatter", "kill", "&fShatter", "glass", 900, "shatter"));
		// More trails
		l.add(c("trail_bubblegum", "trail", "&dBubblegum", "pink_dye", 600, "dust:#FF9EC4>#FF4F8B:1.2"));
		l.add(c("trail_mint", "trail", "&aMint", "lime_dye", 600, "dust:#B6FFDB>#2EC27E:1.2"));
		l.add(c("trail_lava_drip", "trail", "&6Lava Drip", "lava_bucket", 900, "dripping_lava+dust:#FF5A00:0.8"));
		l.add(c("trail_ocean", "trail", "&3Ocean Spray", "prismarine_shard", 900, "dust:#00E5FF>#003E8A:1.2+splash"));
		l.add(c("trail_starlight", "trail", "&eStarlight", "nether_star", 1200, "end_rod+dust:#FFF6B0:0.7"));
		l.add(c("trail_shadow", "trail", "&8Shadow", "black_dye", 1000, "dust:#202020>#000000:1.6+smoke"));
		l.add(c("trail_amethyst", "trail", "&5Amethyst", "amethyst_shard", 900, "block:amethyst_block+dust:#B37CFF:0.9"));
		l.add(c("trail_witch", "trail", "&5Witchcraft", "cauldron", 900, "witch+dust:#7B2FBE:1.0"));
		l.add(c("trail_cupid", "trail", "&cCupid's Path", "pink_tulip", 1000, "heart+dust:#FF7AA2:1.0"));
		l.add(c("trail_galaxy", "trail", "&5&lGalaxy", "end_crystal", 1600, "dust:#4B0082>#00E5FF:1.4+end_rod"));
		// More pets
		l.add(c("pet_axolotl", "pet", "&dAxolotl in a Bucket", "axolotl_bucket", 900, "minecraft:axolotl_bucket"));
		l.add(c("pet_parrot", "pet", "&aParrot Feather", "feather", 600, "minecraft:feather"));
		l.add(c("pet_cookie", "pet", "&6Cookie", "cookie", 400, "minecraft:cookie"));
		l.add(c("pet_lantern", "pet", "&bSoul Lantern", "soul_lantern", 800, "minecraft:soul_lantern"));
		l.add(c("pet_bell", "pet", "&6Bell", "bell", 700, "minecraft:bell"));
		l.add(c("pet_spyglass", "pet", "&6Spyglass", "spyglass", 600, "minecraft:spyglass"));
		l.add(c("pet_trident", "pet", "&3Mini Trident", "trident", 1200, "minecraft:trident"));
		l.add(c("pet_shell", "pet", "&bNautilus Shell", "nautilus_shell", 900, "minecraft:nautilus_shell"));
		// More hats
		l.add(c("hat_bookshelf", "hat", "&6Bookshelf", "bookshelf", 500, "minecraft:bookshelf"));
		l.add(c("hat_furnace", "hat", "&7Furnace", "furnace", 500, "minecraft:furnace"));
		l.add(c("hat_jukebox", "hat", "&6Jukebox", "jukebox", 700, "minecraft:jukebox"));
		l.add(c("hat_hay", "hat", "&eHay Bale", "hay_block", 400, "minecraft:hay_block"));
		l.add(c("hat_sponge", "hat", "&eSponge", "sponge", 500, "minecraft:sponge"));
		l.add(c("hat_froglight", "hat", "&dFroglight", "pearlescent_froglight", 800, "minecraft:pearlescent_froglight"));
		// More death cries
		l.add(c("cry_cat", "cry", "&eSad Cat", "string", 400, "entity.cat.hurt;1.2"));
		l.add(c("cry_chicken", "cry", "&fChicken", "feather", 300, "entity.chicken.hurt"));
		l.add(c("cry_elder", "cry", "&3Elder Curse", "prismarine_crystals", 900, "entity.elder_guardian.curse"));
		l.add(c("cry_shulker", "cry", "&5Shulker", "shulker_shell", 600, "entity.shulker.hurt"));
		l.add(c("cry_fox", "cry", "&6Fox Scream", "sweet_berries", 600, "entity.fox.screech"));
		l.add(c("cry_level", "cry", "&aLevel Down", "experience_bottle", 700, "entity.player.levelup;0.5"));
		// More weapon skins
		l.add(c("skin_bamboo", "skin", "&aBamboo Stick", "bamboo", 500, "bamboo"));
		l.add(c("skin_fishing_rod", "skin", "&3Fishing Rod", "fishing_rod", 600, "fishing_rod"));
		l.add(c("skin_torch", "skin", "&6Torch", "torch", 700, "torch"));
		l.add(c("skin_end_rod", "skin", "&fLight Wand", "end_rod", 1200, "end_rod;glint"));
		l.add(c("skin_brush", "skin", "&6Brush", "brush", 600, "brush"));
		// More auras
		l.add(c("aura_cherry_cape", "aura", "&dBlossom Cape", "cherry_leaves", 1200, "cape:cherry_leaves"));
		l.add(c("aura_love", "aura", "&cLove Aura", "pink_tulip", 1100, "heart:heart"));
		l.add(c("aura_frost_ring", "aura", "&bFrost Ring", "packed_ice", 1100, "ring:snowflake"));
		l.add(c("aura_gold_tornado", "aura", "&6Golden Tornado", "gold_block", 1500, "tornado:dust:#FFD700"));
		return l;
	}

	public CosmeticConfig normalize() {
		if (cosmetics == null) {
			cosmetics = defaults();
		}
		if (configVersion < 2) {
			for (CosmeticDef w : wardrobe()) {
				if (cosmetics.stream().noneMatch(o -> o != null && w.id.equals(o.id))) cosmetics.add(w);
			}
			configVersion = 2;
		}
		if (configVersion < 3) {
			for (CosmeticDef w : more()) {
				if (cosmetics.stream().noneMatch(o -> o != null && w.id.equals(o.id))) cosmetics.add(w);
			}
			configVersion = 3;
		}
		if (configVersion < 4) {
			for (CosmeticDef w : v4()) {
				if (cosmetics.stream().noneMatch(o -> o != null && w.id.equals(o.id))) cosmetics.add(w);
			}
			configVersion = 4;
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
