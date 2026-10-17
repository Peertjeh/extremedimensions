package com.extremedimensions.seam;

import static com.extremedimensions.seam.SeamArchitect.*;

import net.minecraft.world.level.block.state.BlockState;

/**
 * The material plan of one building, shared by every module that builds it: where its masonry sits on its ladder of
 * tones is worked out in the building's coordinates, not a module's own, so a gradient does not start again at every
 * tower, wing or wall it is assembled from. Two modules that lay the same block of the world lay the same stone.
 *
 * <p>A block's place on the ladder is its height between the building's foot and its crown, eased so the dark foot
 * stays short and the pale crown long, moved a little by two fields of smooth noise (narrow streaks that run down
 * the stone, the way weathering and a builder's hand blend a gradient, and broad patches across a face), never by a
 * coin tossed for each block. What dither is left to soften the edges between bands is drawn from the position
 * itself, so it too is the same whichever module asks. Wear (cracks, moss) gathers in patches, more of it low down.
 *
 * <p>The ladder itself, and the other roles (trim, roof, floor...), belong to the building's scheme; this decides only
 * where on each ladder a block falls.
 */
final class MaterialContext {
	final long seed;
	/** The building's foot and crown (world heights) that its gradients run between. */
	final int foot, crown;
	/** How far the streaks and the patches move a block along its ladder (as a fraction of the ladder). */
	final double streaks, patches;
	/** How much of the masonry wears (cracks, moss), at most; more at the foot, in patches. */
	final double wear;

	MaterialContext(long seed, int foot, int crown, double streaks, double patches, double wear) {
		this.seed = seed;
		this.foot = foot;
		this.crown = Math.max(foot + 1, crown);
		this.streaks = streaks;
		this.patches = patches;
		this.wear = wear;
	}

	/** The usual context: streaks of a sixth of the ladder, patches of a tenth. */
	static MaterialContext of(long seed, int foot, int crown, double wear) {
		return new MaterialContext(seed, foot, crown, 0.16, 0.1, wear);
	}

	/** Height between foot and crown, 0..1, eased (smoothstep) so the bands gather at neither end. */
	double height(int y) {
		double t = Math.max(0, Math.min(1, (y - foot) / (double) (crown - foot)));
		return t * t * (3 - 2 * t);
	}

	/**
	 * Where a block of the world falls on a ladder, 0..1: its eased height plus {@code bias} (a module asking for a
	 * paler or darker course), moved by the streaks and the patches.
	 */
	double place(int x, int y, int z, double bias) {
		double streak = (noise(seed, x / 2.3, z / 2.3) - 0.5) * 2;
		double patch = (noise(seed + 1, (x + z) / 9.0, y / 7.0) - 0.5) * 2;
		return Math.max(0, Math.min(1, height(y) + bias + streak * streaks + patch * patches));
	}

	/** A fixed fraction 0..1 drawn from a position (the same for anyone asking about that block). */
	double hash(int x, int y, int z, int salt) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L) ^ (salt * 0x27D4EB2F165667C5L);
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h & 0xFFFFFF) / (double) 0xFFFFFF;
	}

	/** The block of a ladder for a world position: its place, with a little dither of its own to soften the bands. */
	BlockState pick(BlockState[] ladder, int x, int y, int z, double bias) {
		double v = place(x, y, z, bias) * (ladder.length - 1) + (hash(x, y, z, 1) - 0.5) * 0.5;
		return ladder[(int) Math.max(0, Math.min(ladder.length - 1, Math.round(v)))];
	}

	/** Whether the block at a position is worn: in patches, more of them towards the foot. */
	boolean worn(int x, int y, int z) {
		if (wear <= 0) {
			return false;
		}
		double patch = noise(seed + 2, x / 4.0 + z / 5.0, y / 4.0);
		return patch < wear * (1.6 - height(y)) && hash(x, y, z, 2) < 0.75;
	}

	/** Masonry of a ladder at a world position, worn where wear gathers. */
	BlockState masonry(BlockState[] ladder, int x, int y, int z, double bias) {
		BlockState b = pick(ladder, x, y, z, bias);
		if (worn(x, y, z)) {
			if (b == brick()) {
				return hash(x, y, z, 3) < 0.4 ? mossy() : cracked();
			}
			if (b == stone()) {
				return cobble();
			}
			if (b == bsBrick()) {
				return mc("cracked_polished_blackstone_bricks", b);
			}
		}
		return b;
	}
}
