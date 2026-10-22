package net.battlehub.bot;

import java.util.HashMap;
import java.util.Map;

import net.battlehub.core.ml.CombatSpec;
import net.battlehub.core.ml.TerrainRays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The world's blocks as spec v2's terrain columns ({@link TerrainRays}): the top of the highest block with a collision
 * shape from scan_from above the feet down to depth below them (NaN: none). One per decision: columns asked for twice
 * (the rays cross) are looked at once.
 */
final class WorldColumns implements TerrainRays.Columns {
	private final Level level;
	private final CombatSpec spec;
	private final Map<Long, Double> seen = new HashMap<>();
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

	WorldColumns(Level level, CombatSpec spec) {
		this.level = level;
		this.spec = spec;
	}

	@Override
	public double ground(double x, double z, double y) {
		int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
		int top = (int) Math.floor(y + spec.scanFrom), bottom = (int) Math.floor(y - spec.depth);
		long key = ((long) bx & 0x3FFFFFL) << 42 | ((long) bz & 0x3FFFFFL) << 20 | ((long) top & 0xFFFFFL);
		Double cached = seen.get(key);
		if (cached != null) return cached;
		double g = Double.NaN;
		for (int by = top; by >= bottom; by--) {
			pos.set(bx, by, bz);
			BlockState st = level.getBlockState(pos);
			VoxelShape shape = st.getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				g = by + shape.max(Direction.Axis.Y);
				break;
			}
		}
		seen.put(key, g);
		return g;
	}
}
