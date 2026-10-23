package com.extremedimensions.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Whether a chunk is there to be read now. {@link Level#isLoaded} says yes to a chunk still on its way in as well, and
 * reading a block of one of those makes the server thread stand and wait until it has come: so on the server thread the
 * mod looks only into chunks loaded now. (The benchmark caught it waiting so, 150 to 260 ms a time, for the ruined
 * portals of the warm chains, a block of the Seam put down at a chunk's edge, and a Veil Ray looking for open air.)
 */
public final class Loaded {
	private Loaded() {}

	/** Whether the chunk of {@code pos} is loaded now (and pos within the world's height). */
	public static boolean ready(Level level, BlockPos pos) {
		return level.isLoaded(pos) && now(level, pos.getX() >> 4, pos.getZ() >> 4);
	}

	/** Whether chunk (cx, cz) is loaded now. */
	public static boolean chunk(Level level, int cx, int cz) {
		return level.hasChunk(cx, cz) && now(level, cx, cz);
	}

	private static boolean now(Level level, int cx, int cz) {
		// (off the server thread nothing holds the server up, and the game hands out loaded chunks on its thread only)
		return !(level instanceof ServerLevel s) || !s.getServer().isSameThread() || s.getChunkSource().getChunkNow(cx, cz) != null;
	}
}
