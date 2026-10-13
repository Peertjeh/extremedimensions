package net.battlehub.arena;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** The empty arena dimension and the grid of slots in it. */
public final class ArenaWorld {
	private ArenaWorld() {}

	public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, BattleHub.id("arenas"));
	private static final int COLUMNS = 16;

	public static ServerLevel level(MinecraftServer server) {
		ServerLevel level = server.getLevel(KEY);
		if (level == null) {
			BattleHub.LOGGER.error("The arena dimension battlehub:arenas is missing; is the BattleHub data pack disabled?");
		}
		return level;
	}

	public static boolean is(Level level) {
		return level.dimension() == KEY;
	}

	/** Centre of a slot at floor height. */
	public static BlockPos origin(int slot) {
		HubConfig.Arenas a = ConfigManager.config().arenas;
		int col = slot % COLUMNS;
		int row = slot / COLUMNS;
		return new BlockPos((col + 1) * a.slotSpacing, a.baseY, (row + 1) * a.slotSpacing);
	}

	/** The slot a position belongs to, or -1. */
	public static int slotAt(double x, double z) {
		int s = ConfigManager.config().arenas.slotSpacing;
		int col = (int) Math.round(x / s) - 1;
		int row = (int) Math.round(z / s) - 1;
		if (col < 0 || col >= COLUMNS || row < 0) {
			return -1;
		}
		return row * COLUMNS + col;
	}
}
