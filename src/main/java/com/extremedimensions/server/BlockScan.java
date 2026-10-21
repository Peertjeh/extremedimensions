package com.extremedimensions.server;

import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Looking for a few blocks in a box: only in loaded chunks (nothing is loaded to look), and only in the sections that
 * may hold one at all, so open sky or plain stone costs next to nothing.
 */
public final class BlockScan {
	private BlockScan() {}

	/** Whether any block in a box (corners included) matches. */
	public static boolean any(Level level, int x0, int y0, int z0, int x1, int y1, int z1, Predicate<BlockState> match) {
		return scan(level, x0, y0, z0, x1, y1, z1, match, null) > 0;
	}

	/** Every matching block in a box (corners included), each handed to {@code found}; returns how many. */
	public static int each(Level level, int x0, int y0, int z0, int x1, int y1, int z1, Predicate<BlockState> match, Consumer<BlockPos> found) {
		return scan(level, x0, y0, z0, x1, y1, z1, match, found);
	}

	private static int scan(Level level, int x0, int y0, int z0, int x1, int y1, int z1, Predicate<BlockState> match, Consumer<BlockPos> found) {
		int lo = Math.max(y0, level.getMinY()), hi = Math.min(y1, level.getMaxY()), n = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
			for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				LevelChunk chunk = level.getChunk(cx, cz);
				for (int sy = lo >> 4; sy <= hi >> 4; sy++) {
					int idx = level.getSectionIndexFromSectionY(sy);
					if (idx < 0 || idx >= chunk.getSections().length) {
						continue;
					}
					LevelChunkSection sec = chunk.getSection(idx);
					if (sec.hasOnlyAir() || !sec.maybeHas(match)) {
						continue;
					}
					for (int x = Math.max(x0, cx << 4); x <= Math.min(x1, (cx << 4) + 15); x++) {
						for (int z = Math.max(z0, cz << 4); z <= Math.min(z1, (cz << 4) + 15); z++) {
							for (int y = Math.max(lo, sy << 4); y <= Math.min(hi, (sy << 4) + 15); y++) {
								if (match.test(sec.getBlockState(x & 15, y & 15, z & 15))) {
									if (found == null) {
										return 1;
									}
									found.accept(m.set(x, y, z));
									n++;
								}
							}
						}
					}
				}
			}
		}
		return n;
	}
}
