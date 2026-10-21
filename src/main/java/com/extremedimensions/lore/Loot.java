package com.extremedimensions.lore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.extremedimensions.ExtremeDimensions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * The mod's loot, read from its loot tables (`data/extremedimensions/loot_table/`) so that a datapack can change it:
 * the Seam's barrels, the Fray's tangles, hoards, what the Keeper, the Elders and the Seam's creatures leave, the Tribe's
 * offerings and gifts. Relics are not in the tables: how often they come stays a world rule (the config).
 */
public final class Loot {
	private Loot() {}

	public static final String SEAM_LOOM = "chests/seam_loom";
	public static final String SEAM_VILLAGE = "chests/seam_village";
	public static final String SEAM_RELIQUARY = "chests/seam_reliquary";
	public static final String FRAY_TANGLE = "chests/fray_tangle";
	public static final String OLD_HOARD = "chests/old_hoard";
	public static final String RAIDED_HOARD = "chests/raided_hoard";
	/** The barrel in the first Held's chamber a player raises (the letter and the chain), and in any later one. */
	public static final String HELDS_CHAMBER = "chests/helds_chamber";
	public static final String HELD_REMAINS = "chests/held_remains";
	public static final String KEEPER = "gameplay/keeper_spoils";
	public static final String MATRIARCH = "gameplay/matriarch_spoils";
	public static final String FERRYMAN = "gameplay/ferryman_spoils";
	public static final String WEAVER = "gameplay/weaver_spoils";
	public static final String LURKER_OFFERING = "gameplay/lurker_offering";
	public static final String HOLLOWLING_GIFT = "gameplay/hollowling_gift";
	public static final String VEIL_RAY = "gameplay/veil_ray";
	public static final String FRAYED_RAY = "gameplay/frayed_ray";
	public static final String THINNED = "gameplay/thinned";
	public static final String THINNED_FRAY = "gameplay/thinned_fray";
	/** What a first gold medal of the week brings besides its fragments and relic (a Homebound book, sometimes). */
	public static final String TRIAL_GOLD = "gameplay/trial_gold";
	/** What the Endermen leave when they finish a ring unwatched. */
	public static final String UNANCHORED = "gameplay/unanchored";

	/** For tests: a table read in place of one of the mod's, as a datapack would replace it. */
	private static final Map<String, Identifier> STANDINS = new HashMap<>();

	public static ResourceKey<LootTable> key(String table) {
		return ResourceKey.create(Registries.LOOT_TABLE, STANDINS.getOrDefault(table, ExtremeDimensions.id(table)));
	}

	/** What one of the mod's tables gives at a spot. */
	public static List<ItemStack> roll(ServerLevel level, String table, Vec3 at) {
		return level.getServer().reloadableRegistries().getLootTable(key(table)).getRandomItems(params(level, at));
	}

	/** A container filled from one of the mod's tables, its things spread over the slots as in vanilla's chests. */
	public static void fill(ServerLevel level, BlockPos pos, Container c, String table) {
		level.getServer().reloadableRegistries().getLootTable(key(table)).fill(c, params(level, Vec3.atCenterOf(pos)), level.getRandom().nextLong());
	}

	/** For tests: read another table in place of one of the mod's (null puts it back). */
	public static void standIn(String table, Identifier other) {
		if (other == null) {
			STANDINS.remove(table);
		} else {
			STANDINS.put(table, other);
		}
	}

	private static LootParams params(ServerLevel level, Vec3 at) {
		return new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, at).create(LootContextParamSets.CHEST);
	}
}
