package com.extremedimensions.anchor;

import com.extremedimensions.config.Config;
import com.extremedimensions.server.BlockScan;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Soul light wards anchors: the tribe will not drink where souls burn. A soul lantern, soul torch, soul campfire, soul
 * fire or Veil Lantern within {@link Config#LURKER_WARD_RANGE} blocks keeps Lurkers from draining an anchor.
 */
public final class SoulWards {
	private SoulWards() {}

	public static boolean isWard(Block b) {
		return b == Blocks.SOUL_LANTERN || b == Blocks.SOUL_TORCH || b == Blocks.SOUL_WALL_TORCH
				|| b == Blocks.SOUL_CAMPFIRE || b == Blocks.SOUL_FIRE || b == com.extremedimensions.registry.ModBlocks.VEIL_LANTERN;
	}

	/** Whether a Veil Lantern burns within {@link Config#VEIL_LANTERN_RANGE} blocks: no storm breaks through there. */
	public static boolean veiled(Level level, BlockPos at) {
		int r = Math.max(0, Config.VEIL_LANTERN_RANGE), h = Math.max(2, r / 2);
		Block lantern = com.extremedimensions.registry.ModBlocks.VEIL_LANTERN;
		return BlockScan.any(level, at.getX() - r, at.getY() - h, at.getZ() - r, at.getX() + r, at.getY() + h, at.getZ() + r, s -> s.is(lantern));
	}

	public static boolean warded(Level level, int x, int y, int z) {
		int r = (int) Math.round(Config.LURKER_WARD_RANGE);
		if (r <= 0) {
			return false;
		}
		return BlockScan.any(level, x - r, y - r, z - r, x + r, y + r, z + r, s -> isWard(s.getBlock()));
	}
}
