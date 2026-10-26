package com.extremedimensions.tetherite;

import com.extremedimensions.config.Config;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Spindle: where Tether Fragments are worked back into thread. Fragments and Seam Silk are loaded onto it with a
 * use; a use with an empty hand turns it, and every turn winds the frayed pieces tighter round the silk, the spool
 * turning and filling as it goes. When enough turns are done it gives up a Tether Cord, which Tetherite will not be
 * made without.
 *
 * <p>Everything it holds is in its block state, so it keeps through a restart and drops with the spindle (its loot
 * table gives back what was loaded).
 */
public class SpindleBlock extends Block {
	public static final int MAX_FRAGMENTS = 8;
	public static final int MAX_SILK = 4;
	public static final int MAX_TURNS = 15;
	public static final IntegerProperty FRAGMENTS = IntegerProperty.create("fragments", 0, MAX_FRAGMENTS);
	public static final IntegerProperty SILK = IntegerProperty.create("silk", 0, MAX_SILK);
	public static final IntegerProperty SPUN = IntegerProperty.create("spun", 0, MAX_TURNS);

	private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 3, 14), Block.box(5, 3, 5, 11, 15, 11), Block.box(7, 15, 7, 9, 16, 9));

	public SpindleBlock(Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(FRAGMENTS, 0).setValue(SILK, 0).setValue(SPUN, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FRAGMENTS, SILK, SPUN);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Fragments per cord (at most what the spindle holds). */
	public static int fragmentsPerCord() {
		return Math.max(1, Math.min(MAX_FRAGMENTS, Config.SPINDLE_FRAGMENTS));
	}

	/** Seam Silk per cord (at most what the spindle holds; 0 needs none). */
	public static int silkPerCord() {
		return Math.max(0, Math.min(MAX_SILK, Config.SPINDLE_SILK));
	}

	/** Turns a cord takes. */
	public static int turnsPerCord() {
		return Math.max(1, Math.min(MAX_TURNS, Config.SPINDLE_TURNS));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		boolean fragment = stack.is(ModItems.TETHER_FRAGMENT), silk = stack.is(ModItems.SEAM_SILK);
		if (!fragment && !silk) {
			// An empty main hand turns it (the game asks the block with the hand first, as at a lectern)
			return stack.isEmpty() && hand == InteractionHand.MAIN_HAND ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
		}
		return load(state, level, pos, player, stack, false);
	}

	/**
	 * Fragments or silk onto the spindle: one at a time, or as much as fits ({@code all}: a crouching player, from the
	 * block-use event, since the game never asks the block for a crouching hand). PASS for anything else.
	 */
	public static InteractionResult load(BlockState state, Level level, BlockPos pos, Player player, ItemStack stack, boolean all) {
		boolean fragment = stack.is(ModItems.TETHER_FRAGMENT), silk = stack.is(ModItems.SEAM_SILK);
		if (!fragment && !silk) {
			return InteractionResult.PASS;
		}
		IntegerProperty slot = fragment ? FRAGMENTS : SILK;
		int max = fragment ? MAX_FRAGMENTS : MAX_SILK;
		int have = state.getValue(slot);
		if (have >= max) {
			if (level instanceof ServerLevel) {
				tell(player, Component.translatable("gui.extremedimensions.spindle.full"));
			}
			return InteractionResult.FAIL;
		}
		if (!(level instanceof ServerLevel)) {
			return InteractionResult.SUCCESS;
		}
		int add = all ? Math.min(max - have, stack.getCount()) : 1;
		if (!player.isCreative()) {
			stack.shrink(add);
		}
		BlockState next = state.setValue(slot, have + add);
		level.setBlock(pos, next, Block.UPDATE_ALL);
		WorldFx.particleIn(level, fragment ? "minecraft:soul" : "minecraft:witch", pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 0.2, 0.2, 0.2, 0.01, 6);
		WorldFx.soundIn(dim(level), fragment ? "minecraft:block.amethyst_block.place" : "minecraft:block.wool.place", "block",
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.8f, fragment ? 1.4f : 1.0f);
		status(player, next);
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		int fragments = state.getValue(FRAGMENTS), silk = state.getValue(SILK);
		if (fragments < fragmentsPerCord() || silk < silkPerCord()) {
			if (level instanceof ServerLevel) {
				tell(player, Component.translatable("gui.extremedimensions.spindle.need", fragmentsPerCord(), silkPerCord(), fragments, silk));
			}
			return InteractionResult.FAIL;
		}
		if (!(level instanceof ServerLevel)) {
			return InteractionResult.SUCCESS;
		}
		spin((ServerLevel) level, pos, state, player);
		return InteractionResult.SUCCESS_SERVER;
	}

	/** One turn of the spindle; the last one of a cord gives it up. */
	public static void spin(ServerLevel level, BlockPos pos, BlockState state, Player player) {
		int spun = state.getValue(SPUN) + 1;
		double x = pos.getX() + 0.5, y = pos.getY() + 0.6, z = pos.getZ() + 0.5;
		if (spun < turnsPerCord()) {
			level.setBlock(pos, state.setValue(SPUN, spun), Block.UPDATE_ALL);
			WorldFx.particleIn(level, "minecraft:end_rod", x, y, z, 0.18, 0.25, 0.18, 0.01, 3);
			WorldFx.soundIn(dim(level), "minecraft:ui.loom.select_pattern", "block", x, y, z, 0.9f, 0.8f + 0.06f * spun);
			if (player != null) {
				tell(player, Component.translatable("gui.extremedimensions.spindle.turn", spun, turnsPerCord()));
			}
			return;
		}
		BlockState done = state.setValue(SPUN, 0)
				.setValue(FRAGMENTS, state.getValue(FRAGMENTS) - fragmentsPerCord())
				.setValue(SILK, state.getValue(SILK) - silkPerCord());
		level.setBlock(pos, done, Block.UPDATE_ALL);
		Block.popResource(level, pos.above(), new ItemStack(ModItems.TETHER_CORD));
		WorldFx.particleIn(level, "minecraft:soul_fire_flame", x, y + 0.3, z, 0.25, 0.3, 0.25, 0.03, 16);
		WorldFx.particleIn(level, "minecraft:end_rod", x, y + 0.3, z, 0.3, 0.4, 0.3, 0.06, 12);
		WorldFx.soundIn(dim(level), "minecraft:ui.loom.take_result", "block", x, y, z, 1.0f, 1.0f);
		WorldFx.soundIn(dim(level), "minecraft:block.amethyst_block.resonate", "block", x, y, z, 1.0f, 1.2f);
		if (player != null) {
			tell(player, Component.translatable("gui.extremedimensions.spindle.done"));
		}
	}

	/** A line on the action bar of whoever is at the spindle. */
	private static void tell(Player player, Component line) {
		if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
			sp.sendSystemMessage(line, true);
		}
	}

	private static void status(Player player, BlockState state) {
		tell(player, Component.translatable("gui.extremedimensions.spindle.loaded", state.getValue(FRAGMENTS), MAX_FRAGMENTS,
				state.getValue(SILK), MAX_SILK));
	}

	private static String dim(Level level) {
		return level.dimension().identifier().toString();
	}
}
