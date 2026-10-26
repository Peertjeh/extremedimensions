package com.extremedimensions.tapestry;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A knot of curse thread, tied round the Weaver's Loom while a set is unwoven (see {@link Unweaving}), in that set's
 * colour. It is cut by blows, never mined, and one left behind by a lost unweaving (a crash) comes undone by itself.
 */
public class CurseKnotBlock extends Block {
	/** The set it was tied from, in the order of {@link Unweaving#SETS}. */
	public static final IntegerProperty SET = IntegerProperty.create("set", 0, Unweaving.SETS.length - 1);

	public CurseKnotBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(SET, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SET);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!Unweaving.holds(level, pos)) {
			level.removeBlock(pos, false);
		}
	}
}
