package com.extremedimensions.anchor;

import com.extremedimensions.config.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Soul light wards anchors: the tribe will not drink where souls burn. A soul lantern, soul torch, soul campfire or
 * soul fire within {@link Config#LURKER_WARD_RANGE} blocks keeps Lurkers from draining an anchor.
 */
public final class SoulWards {
	private SoulWards() {}

	public static boolean isWard(Block b) {
		return b == Blocks.SOUL_LANTERN || b == Blocks.SOUL_TORCH || b == Blocks.SOUL_WALL_TORCH
				|| b == Blocks.SOUL_CAMPFIRE || b == Blocks.SOUL_FIRE;
	}

	public static boolean warded(Level level, int x, int y, int z) {
		int r = (int) Math.round(Config.LURKER_WARD_RANGE);
		if (r <= 0) {
			return false;
		}
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					m.set(x + dx, y + dy, z + dz);
					if (isWard(level.getBlockState(m).getBlock())) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
