package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.List;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModItems;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Seam's furnishings for builders: the Seam Silk Block and its carpet, Nebula Glass and its pane, and the Veil Lamp.
 * Vanilla's blocks in all but their colours: each is crafted, the silk is never wool to vanilla's recipes, the glass
 * goes only to Silk Touch, the pane joins its neighbours, and the lamp gives light 15 in its own block and carries none
 * out.
 */
public class BuilderBlockTests {
	private static final String[] RECIPES = {"seam_silk_block", "seam_silk_carpet", "nebula_glass", "nebula_glass_pane", "veil_lamp"};

	@GameTest
	public void theBuildersBlocksAreCraftedButNeverWool(GameTestHelper helper) {
		var recipes = helper.getLevel().getServer().getRecipeManager();
		for (String id : RECIPES) {
			check(helper, recipes.byKey(ResourceKey.create(Registries.RECIPE, ExtremeDimensions.id(id))).isPresent(), "there is no recipe for %s", id);
		}
		// vanilla's recipes for wool and carpet take them by these tags; silk in them would be spent as plain wool
		check(helper, !new ItemStack(ModItems.SEAM_SILK_BLOCK).is(ItemTags.WOOL), "the Seam Silk Block is in the wool tag");
		check(helper, !new ItemStack(ModItems.SEAM_SILK_CARPET).is(ItemTags.WOOL_CARPETS), "the Seam Silk Carpet is in the wool carpet tag");
		helper.succeed();
	}

	@GameTest
	public void theVeilLampLightsItsOwnBlockAtFifteen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		try {
			level.setBlock(at, ModBlocks.VEIL_LAMP.defaultBlockState(), 3);
			check(helper, level.getBlockState(at).getLightEmission() == 15, "the Veil Lamp gives light %s", level.getBlockState(at).getLightEmission());
			// none of the unseen lights a Kindled Seam Lantern carries out to 14 blocks: the lamp is no rival to its reach
			int carried = 0;
			for (BlockPos q : BlockPos.betweenClosed(at.offset(-15, -2, -15), at.offset(15, 3, 15))) {
				carried += level.getBlockState(q).is(ModBlocks.KINDLED_LIGHT) ? 1 : 0;
			}
			check(helper, carried == 0, "%s lights were carried out round the Veil Lamp", carried);
		} finally {
			level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
		}
		helper.succeed();
	}

	@GameTest
	public void nebulaGlassGoesOnlyToSilkTouchAndItsPanesJoin(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		ItemStack bare = new ItemStack(Items.DIAMOND_PICKAXE), silk = new ItemStack(Items.DIAMOND_PICKAXE);
		silk.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
		try {
			level.setBlock(at.below(), Blocks.STONE.defaultBlockState(), 3);
			// the glass and its pane, as vanilla's: nothing without Silk Touch, themselves with it
			for (Block b : new Block[] {ModBlocks.NEBULA_GLASS, ModBlocks.NEBULA_GLASS_PANE}) {
				level.setBlock(at, b.defaultBlockState(), 3);
				BlockState s = level.getBlockState(at);
				List<ItemStack> lost = Block.getDrops(s, level, at, null, null, bare);
				check(helper, lost.isEmpty(), "%s dropped %s without Silk Touch", b, lost);
				List<ItemStack> kept = Block.getDrops(s, level, at, null, null, silk);
				check(helper, kept.size() == 1 && kept.get(0).is(b.asItem()) && kept.get(0).getCount() == 1, "%s gave %s to Silk Touch", b, kept);
			}
			// the cloth and the lamp drop themselves, as wool, carpet and a lamp do
			for (Block b : new Block[] {ModBlocks.SEAM_SILK_BLOCK, ModBlocks.SEAM_SILK_CARPET, ModBlocks.VEIL_LAMP}) {
				level.setBlock(at, b.defaultBlockState(), 3);
				List<ItemStack> drops = Block.getDrops(level.getBlockState(at), level, at, null);
				check(helper, drops.size() == 1 && drops.get(0).is(b.asItem()), "%s dropped %s", b, drops);
			}
			// a pane joins another of its own and a vanilla glass pane, as panes join each other
			level.setBlock(at, ModBlocks.NEBULA_GLASS_PANE.defaultBlockState(), 3);
			level.setBlock(at.west(), ModBlocks.NEBULA_GLASS_PANE.defaultBlockState(), 3);
			level.setBlock(at.east(), Blocks.GLASS_PANE.defaultBlockState(), 3);
			BlockState pane = level.getBlockState(at);
			check(helper, pane.getValue(CrossCollisionBlock.WEST) && pane.getValue(CrossCollisionBlock.EAST), "the pane did not join its neighbours: %s", pane);
		} finally {
			for (BlockPos q : new BlockPos[] {at, at.west(), at.east(), at.below()}) {
				level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		helper.succeed();
	}

	/**
	 * A hush slab drops one of itself, and a double slab two, as vanilla's stone slab does (the hush slabs' loot tables
	 * were written in an older form of the count's condition: checked here against vanilla's, side by side).
	 */
	@GameTest
	public void aHushSlabDropsOneAndADoubleTwo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		try {
			for (Block b : new Block[] {Blocks.STONE_SLAB, ModBlocks.HUSH_STONE_SLAB, ModBlocks.COBBLED_HUSH_STONE_SLAB, ModBlocks.POLISHED_HUSH_STONE_SLAB,
					ModBlocks.HUSH_BRICK_SLAB, ModBlocks.MOSSY_HUSH_BRICK_SLAB, ModBlocks.HUSH_TILE_SLAB, ModBlocks.PALE_HUSH_BRICK_SLAB,
					ModBlocks.POLISHED_PALE_HUSH_STONE_SLAB}) {
				for (net.minecraft.world.level.block.state.properties.SlabType type : new net.minecraft.world.level.block.state.properties.SlabType[] {
						net.minecraft.world.level.block.state.properties.SlabType.BOTTOM, net.minecraft.world.level.block.state.properties.SlabType.DOUBLE}) {
					level.setBlock(at, b.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, type), 3);
					List<ItemStack> drops = Block.getDrops(level.getBlockState(at), level, at, null);
					int want = type == net.minecraft.world.level.block.state.properties.SlabType.DOUBLE ? 2 : 1;
					int got = drops.stream().filter(d -> d.is(b.asItem())).mapToInt(ItemStack::getCount).sum();
					check(helper, got == want, "%s as %s dropped %s, not %s", b, type, drops, want);
				}
			}
		} finally {
			level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
		}
		helper.succeed();
	}
}
