package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.seam.Colossus;
import com.extremedimensions.seam.SeamArchitect;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The hush masonry and the colossal buildings made of it: every block of the family is an item, is mined with a
 * pickaxe and drops itself (the Seam's stone could once be mined with nothing, and gave nothing), its shapes are
 * known as slabs, stairs and walls; and the colossi stay on their islands, under the build limit, and differ.
 */
public class MasonryTests {
	@GameTest
	public void everyHushBlockIsMinedAndDrops(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		ItemStack pick = new ItemStack(Items.WOODEN_PICKAXE);
		check(helper, ModBlocks.HUSH.size() == 37, "the hush family has %s blocks, expected 37 (36 and the vein)", ModBlocks.HUSH.size());
		for (Block b : ModBlocks.HUSH) {
			BlockState s = b.defaultBlockState();
			String id = BuiltInRegistries.BLOCK.getKey(b).toString();
			check(helper, BuiltInRegistries.ITEM.containsKey(BuiltInRegistries.BLOCK.getKey(b)), "%s has no item", id);
			check(helper, s.is(BlockTags.MINEABLE_WITH_PICKAXE), "%s is not mined with a pickaxe", id);
			check(helper, pick.isCorrectToolForDrops(s), "a pickaxe is not the tool for %s", id);
			level.setBlock(pos, s, 3);
			boolean drops = !Block.getDrops(level.getBlockState(pos), level, pos, null).isEmpty();
			check(helper, drops, "%s drops nothing", id);
			if (b instanceof SlabBlock) {
				check(helper, s.is(BlockTags.SLABS), "%s is not known as a slab", id);
			} else if (b instanceof StairBlock) {
				check(helper, s.is(BlockTags.STAIRS), "%s is not known as stairs", id);
			} else if (b instanceof WallBlock) {
				check(helper, s.is(BlockTags.WALLS), "%s is not known as a wall", id);
			}
		}
		level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		helper.succeed();
	}

	/** Designs a row of colossi from different seeds: each fits its island, stays under the limit and is its own. */
	@GameTest(maxTicks = 200)
	public void colossiFitTheirIslandsAndDiffer(GameTestHelper helper) {
		int r = 36, top = 99;
		Set<Integer> layouts = new HashSet<>(), schemes = new HashSet<>(), crowns = new HashSet<>();
		Set<String> kinds = new HashSet<>();
		int seeds = 24, hush = 0, smallest = Integer.MAX_VALUE, largest = 0;
		double farthest = 0;
		long t0 = System.nanoTime();
		for (int i = 0; i < seeds; i++) {
			SeamArchitect.Plan plan = new SeamArchitect.Plan();
			Colossus.Summary s = Colossus.build(plan, new Random(7919L * i + 13), 0, top, 0, r);
			double reach = plan.reach(0, 0);
			farthest = Math.max(farthest, reach);
			smallest = Math.min(smallest, plan.size());
			largest = Math.max(largest, plan.size());
			check(helper, reach <= r - 1, "colossus %s (%s) reaches %s blocks out on an island of radius %s", i, s, reach, r);
			check(helper, plan.dropped == 0, "colossus %s (%s) ran %s blocks above the build limit", i, s, plan.dropped);
			check(helper, plan.size() > 3000, "colossus %s (%s) is only %s blocks", i, s, plan.size());
			check(helper, s.height() >= 40, "colossus %s (%s) is only %s blocks tall", i, s, s.height());
			layouts.add(s.layout());
			schemes.add(s.scheme());
			crowns.add(s.crown());
			kinds.add(s.layout() + "/" + s.scheme() + "/" + s.crown() + "/" + s.towers());
			hush += plan.count(ModBlocks.HUSH_BRICKS) + plan.count(ModBlocks.PALE_HUSH_BRICKS) + plan.count(ModBlocks.POLISHED_HUSH_STONE)
					+ plan.count(ModBlocks.HUSH_PILLAR);
		}
		double ms = (System.nanoTime() - t0) / 1e6;
		com.extremedimensions.ExtremeDimensions.LOGGER.info(
				"Colossi: {} designed in {} ms ({} ms each), {} to {} blocks, reaching {} out; {} layouts, {} schemes, {} crowns, {} kinds",
				seeds, Math.round(ms), Math.round(ms / seeds), smallest, largest, Math.round(farthest), layouts.size(), schemes.size(),
				crowns.size(), kinds.size());
		check(helper, layouts.size() >= 4, "only %s plans among %s colossi", layouts.size(), seeds);
		check(helper, schemes.size() >= 3, "only %s schemes of material among %s colossi", schemes.size(), seeds);
		check(helper, crowns.size() >= 3, "only %s kinds of crown among %s colossi", crowns.size(), seeds);
		check(helper, kinds.size() >= seeds * 2 / 3, "only %s different colossi among %s", kinds.size(), seeds);
		check(helper, hush > seeds * 500, "the colossi are hardly built of hush masonry (%s blocks of it)", hush);
		helper.succeed();
	}
}
