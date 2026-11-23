package com.extremedimensions.relic;

import com.extremedimensions.config.Config;
import com.extremedimensions.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

/**
 * The Kindled Seam Lantern: the Seam Lantern lit from a lantern's flame and a soul lantern's, a lantern that stands or
 * hangs as theirs do, and burns blue-violet. Its light reaches twice as far as a lantern's: it carries its light out
 * {@code kindled_lantern_spread} blocks in the eight directions round it, as unseen lights ({@code
 * extremedimensions:kindled_light}, vanilla's light block under a name of its own, level {@code kindled_lantern_light})
 * where there is open air (or a little above or below, where there is not), and takes them back when it is broken. Now
 * and then (a random tick) it lays again any that were built over or put out, and puts its own in the place of the
 * vanilla light blocks it carried out before 3.51.1.
 */
public class KindledLanternBlock extends LanternBlock {
	/** The eight directions its light is carried in, as unit steps (the diagonals scaled down to the same reach). */
	private static final double[][] WAYS = {{1, 0}, {0.7071, 0.7071}, {0, 1}, {-0.7071, 0.7071}, {-1, 0}, {-0.7071, -0.7071}, {0, -1}, {0.7071, -0.7071}};
	/** Heights tried at each spot for open air, in order: level with it, then a step up, a step down, two up. */
	private static final int[] HEIGHTS = {0, 1, -1, 2};

	public KindledLanternBlock(Properties props) {
		super(props);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		super.onPlace(state, level, pos, old, moved);
		if (level instanceof ServerLevel server && !old.is(this)) {
			spread(server, pos);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
		super.affectNeighborsAfterRemoval(state, level, pos, moved);
		if (!level.getBlockState(pos).is(this)) {
			gather(level, pos);
		}
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		spread(level, pos);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		// its flame stirs blue and violet, now and then
		if (random.nextInt(4) == 0) {
			double y = pos.getY() + (state.getValue(HANGING) ? 0.35 : 0.3);
			level.addParticle(random.nextBoolean() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2,
					y + random.nextDouble() * 0.2, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2, 0, 0.01, 0);
		}
	}

	/** Its light carried out round it: an unseen light at each spot where there is open air (public for tests). */
	public static void spread(ServerLevel level, BlockPos pos) {
		int reach = Math.max(0, Math.min(15, Config.KINDLED_LANTERN_SPREAD));
		if (reach <= 0) {
			return;
		}
		int level15 = Math.max(1, Math.min(15, Config.KINDLED_LANTERN_LIGHT));
		BlockState light = ModBlocks.KINDLED_LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level15);
		for (double[] w : WAYS) {
			BlockPos spot = pos.offset((int) Math.round(w[0] * reach), 0, (int) Math.round(w[1] * reach));
			for (int dy : HEIGHTS) {
				BlockPos at = spot.above(dy);
				if (!level.isLoaded(at)) {
					break;
				}
				BlockState there = level.getBlockState(at);
				if (there.is(ModBlocks.KINDLED_LIGHT)) {
					break;
				}
				if (there.is(Blocks.LIGHT)) {
					// one it carried out before 3.51.1, as vanilla's light (at its level): its own in its place
					if (there.getValue(LightBlock.LEVEL) == level15) {
						level.setBlock(at, light.setValue(BlockStateProperties.WATERLOGGED, there.getValue(BlockStateProperties.WATERLOGGED)), 3);
					}
					break;
				}
				if (there.isAir() || there.is(Blocks.WATER) && there.getFluidState().isSource()) {
					level.setBlock(at, light.setValue(BlockStateProperties.WATERLOGGED, there.is(Blocks.WATER)), 3);
					break;
				}
			}
		}
	}

	/**
	 * Its light taken back: every unseen light on the eight lines out from it, as far as it could ever have carried it
	 * and at every height it could have been laid (so a light laid under other settings goes too, and a vanilla light
	 * laid before 3.51.1).
	 */
	public static void gather(ServerLevel level, BlockPos pos) {
		for (double[] w : WAYS) {
			for (int r = 1; r <= 15; r++) {
				BlockPos spot = pos.offset((int) Math.round(w[0] * r), 0, (int) Math.round(w[1] * r));
				for (int dy : HEIGHTS) {
					BlockPos at = spot.above(dy);
					if (!level.isLoaded(at)) {
						continue;
					}
					BlockState there = level.getBlockState(at);
					if (there.is(ModBlocks.KINDLED_LIGHT) || there.is(Blocks.LIGHT)) {
						level.setBlock(at, there.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.defaultFluidState().createLegacyBlock()
								: Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
		}
	}
}
