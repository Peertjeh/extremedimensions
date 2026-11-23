package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.config.Config;
import com.extremedimensions.registry.ModBlocks;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The Kindled Seam Lantern (3.50.0): crafted from the relic and both lanterns, it carries its light twice as far. */
public class KindledLanternTests {
	/** The eight spots round it its light is carried to, at the reach given. */
	private static BlockPos[] spots(BlockPos at, int reach) {
		int d = (int) Math.round(0.7071 * reach);
		return new BlockPos[] {at.offset(reach, 0, 0), at.offset(d, 0, d), at.offset(0, 0, reach), at.offset(-d, 0, d), at.offset(-reach, 0, 0),
				at.offset(-d, 0, -d), at.offset(0, 0, -reach), at.offset(d, 0, -d)};
	}

	/** Whether an unseen light stands at the spot or a little above or below it (where it is laid when the spot is not open). */
	private static boolean lit(ServerLevel level, BlockPos spot) {
		for (int dy : new int[] {0, 1, -1, 2}) {
			BlockState s = level.getBlockState(spot.above(dy));
			if (s.is(ModBlocks.KINDLED_LIGHT)) {
				return true;
			}
		}
		return false;
	}

	@GameTest(environment = Alone.KINDLED_LANTERN)
	public void aKindledLanternCarriesItsLightAndTakesItBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		check(helper, level.getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, ExtremeDimensions.id("kindled_seam_lantern"))).isPresent(),
				"there is no recipe for the Kindled Seam Lantern");
		int spread = Config.KINDLED_LANTERN_SPREAD, light = Config.KINDLED_LANTERN_LIGHT;
		Config.KINDLED_LANTERN_SPREAD = 14;
		Config.KINDLED_LANTERN_LIGHT = 15;
		BlockPos rel = new BlockPos(1, 2, 1), at = helper.absolutePos(rel);
		try {
			// open air round it out past its reach, on a floor
			for (int dx = -16; dx <= 16; dx++) {
				for (int dz = -16; dz <= 16; dz++) {
					level.setBlock(at.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
					for (int dy = 0; dy <= 3; dy++) {
						level.setBlock(at.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
			level.setBlock(at, ModBlocks.KINDLED_SEAM_LANTERN.defaultBlockState(), 3);
			check(helper, level.getBlockState(at).getLightEmission() == 15, "it gives light %s", level.getBlockState(at).getLightEmission());
			int carried = 0;
			for (BlockPos s : spots(at, 14)) {
				carried += lit(level, s) ? 1 : 0;
			}
			check(helper, carried == 8, "its light was carried to %s of the eight spots", carried);
			BlockState one = level.getBlockState(at.offset(14, 0, 0));
			check(helper, one.is(ModBlocks.KINDLED_LIGHT) && one.getValue(LightBlock.LEVEL) == 15, "the light carried east is %s", one);
			check(helper, one.getBlock() instanceof LightBlock, "the light it carries is not a light block (a shader pack would not see it)");
			// One carried out before 3.51.1, as vanilla's light block, is put in its own light's place
			level.setBlock(at.offset(0, 0, 14), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
			com.extremedimensions.relic.KindledLanternBlock.spread(level, at);
			check(helper, level.getBlockState(at.offset(0, 0, 14)).is(ModBlocks.KINDLED_LIGHT), "an older vanilla light was left as it was: %s",
					level.getBlockState(at.offset(0, 0, 14)));
			// One built over is laid again in time, a step up
			level.setBlock(at.offset(14, 0, 0), Blocks.STONE.defaultBlockState(), 3);
			com.extremedimensions.relic.KindledLanternBlock.spread(level, at);
			check(helper, level.getBlockState(at.offset(14, 1, 0)).is(ModBlocks.KINDLED_LIGHT), "a light built over was not laid again above it");
			// Broken, it takes them all back
			level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
			int left = 0;
			for (BlockPos s : spots(at, 14)) {
				left += lit(level, s) ? 1 : 0;
			}
			check(helper, left == 0, "%s of its lights are left after it was broken", left);
			// ...and an older vanilla light with them
			level.setBlock(at.offset(-14, 0, 0), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
			com.extremedimensions.relic.KindledLanternBlock.gather(level, at);
			check(helper, level.getBlockState(at.offset(-14, 0, 0)).isAir(), "an older vanilla light was left after it was broken");
		} finally {
			Config.KINDLED_LANTERN_SPREAD = spread;
			Config.KINDLED_LANTERN_LIGHT = light;
			com.extremedimensions.relic.KindledLanternBlock.gather(level, at);
			for (int dx = -16; dx <= 16; dx++) {
				for (int dz = -16; dz <= 16; dz++) {
					for (int dy = -1; dy <= 3; dy++) {
						level.setBlock(at.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
		helper.succeed();
	}
}
