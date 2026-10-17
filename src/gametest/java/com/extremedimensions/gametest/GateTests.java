package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.Random;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.seam.ReturnGate;
import com.extremedimensions.seam.RiftPortal;
import com.extremedimensions.seam.RiftPortals;
import com.extremedimensions.seam.SeamArchitect;
import com.extremedimensions.seam.SeamService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Return Gate: laid out whole whichever way it faces, its rift over the pale threshold with clear air to walk in,
 * the visitor stepping out on its dais facing away from it; the rift keeping to its threshold while leashed and
 * roaming (never off solid ground) when let loose; and the bridges, with their gateways, some of them hung, all of them
 * walkable. (Arriving at a gate is tested in the client test, the test server having no Seam.)
 */
public class GateTests {
	@GameTest
	public void theGateHoldsItsRiftWhicheverWayItFaces(GameTestHelper helper) {
		int top = 99;
		for (Direction front : Direction.Plane.HORIZONTAL) {
			SeamArchitect.Plan plan = new SeamArchitect.Plan();
			ReturnGate.Gate g = ReturnGate.build(plan, new Random(31), 0, top, 0, front);
			int rx = Mth.floor(g.riftX()), ry = (int) g.riftY(), rz = Mth.floor(g.riftZ());
			int lx = Mth.floor(g.landX()), ly = (int) g.landY(), lz = Mth.floor(g.landZ());
			check(helper, plan.dropped == 0, "facing %s, the gate ran %s blocks over the build limit", front, plan.dropped);
			check(helper, plan.reach(0, 0) <= ReturnGate.REACH, "facing %s, the gate reaches %s out", front, plan.reach(0, 0));
			check(helper, plan.highest() - top >= 25, "facing %s, the gate is only %s blocks tall", front, plan.highest() - top);
			check(helper, ry == top + 2 && ly == top + 2, "facing %s, the rift (%s) or the landing (%s) is not on the dais", front, ry, ly);
			check(helper, lx - rx == front.getStepX() * 4 && lz - rz == front.getStepZ() * 4,
					"facing %s, the visitor does not step out in front of the rift (%s,%s from it)", front, lx - rx, lz - rz);
			check(helper, Math.abs(Mth.wrapDegrees(g.yaw() - front.toYRot())) < 0.01, "facing %s, the visitor looks the wrong way (%s)", front, g.yaw());
			// The threshold: pale stones under the rift and around it, chiseled bricks before and behind
			Direction side = front.getClockWise();
			for (int a = -2; a <= 2; a++) {
				for (int b = -1; b <= 1; b++) {
					int x = rx + side.getStepX() * a + front.getStepX() * b, z = rz + side.getStepZ() * a + front.getStepZ() * b;
					check(helper, is(plan.at(x, ry - 1, z), ModBlocks.POLISHED_PALE_HUSH_STONE), "facing %s, the threshold has a gap at %s,%s", front, a, b);
				}
				for (int b = -2; b <= 2; b += 4) {
					int x = rx + side.getStepX() * a + front.getStepX() * b, z = rz + side.getStepZ() * a + front.getStepZ() * b;
					check(helper, is(plan.at(x, ry - 1, z), ModBlocks.CHISELED_HUSH_BRICKS), "facing %s, the threshold is open at %s,%s", front, a, b);
				}
			}
			// From the landing to the rift and on through the gate: clear for a visitor to walk, floor underfoot
			for (int k = -4; k <= 4; k++) {
				int x = rx + front.getStepX() * k, z = rz + front.getStepZ() * k;
				check(helper, plan.at(x, ry - 1, z) != null, "facing %s, no floor %s blocks from the rift", front, k);
				check(helper, !plan.has(x, ry, z) && !plan.has(x, ry + 1, z), "facing %s, the way through is blocked %s blocks from the rift", front, k);
			}
			check(helper, plan.count(Blocks.OCHRE_FROGLIGHT) >= 20, "facing %s, the gate has only %s gold lights", front, plan.count(Blocks.OCHRE_FROGLIGHT));
			check(helper, plan.count(Blocks.CAMPFIRE) == 8, "facing %s, the braziers hold %s fires, not eight", front, plan.count(Blocks.CAMPFIRE));
		}
		helper.succeed();
	}

	/**
	 * Leashed, the rift keeps to the pale stones, and only back and forth through its gate; let loose it roams, but
	 * never over the nothing or into a wall.
	 */
	@GameTest
	public void theRiftKeepsToItsThreshold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(new BlockPos(4, 1, 4));
		// A threshold of 3 by 3 pale stones, a ring of chiseled bricks, an outer ring of tiles; open air over all
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				int ring = Math.max(Math.abs(dx), Math.abs(dz));
				Block b = ring <= 1 ? ModBlocks.POLISHED_PALE_HUSH_STONE : ring == 2 ? ModBlocks.CHISELED_HUSH_BRICKS : ModBlocks.HUSH_TILES;
				level.setBlock(o.offset(dx, 0, dz), b.defaultBlockState(), 3);
				for (int y = 1; y <= 3; y++) {
					level.setBlock(o.offset(dx, y, dz), Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}
		PlayerData d = new PlayerData();
		d.riftX = o.getX() + 0.5;
		d.riftY = o.getY() + 1;
		d.riftZ = o.getZ() + 0.5;
		for (int i = 0; i < 400; i++) {
			SeamService.drift(level, d, true);
			BlockPos at = BlockPos.containing(d.riftX, d.riftY, d.riftZ);
			check(helper, level.getBlockState(at.below()).is(ModBlocks.POLISHED_PALE_HUSH_STONE), "the leashed rift left its threshold for %s", at);
			// its tear stands across the gate (facing south here): it goes back and forth through it, not sideways
			check(helper, Config.SEAM_PORTAL <= 0 || at.getX() == o.getX(), "the leashed rift stepped sideways out of its gate to %s", at);
		}
		boolean roamed = false;
		for (int i = 0; i < 400; i++) {
			SeamService.drift(level, d, false);
			BlockPos at = BlockPos.containing(d.riftX, d.riftY, d.riftZ);
			check(helper, level.getBlockState(at).isAir() && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP),
					"the loose rift stands in a wall or over nothing at %s", at);
			roamed |= !level.getBlockState(at.below()).is(ModBlocks.POLISHED_PALE_HUSH_STONE);
		}
		check(helper, roamed, "let loose, the rift never left its threshold");
		helper.succeed();
	}

	/**
	 * A tear is passed through by walking into its opening, from either side, whichever way it faces: not by standing
	 * beside it, over it or a few steps in front of it.
	 */
	@GameTest
	public void aTearIsPassedThroughItsOpening(GameTestHelper helper) {
		float r = 2;
		for (float yaw : new float[] {0, 90, 180, -90, 37}) {
			double a = Math.toRadians(yaw);
			double fx = -Math.sin(a), fz = Math.cos(a), sx = Math.cos(a), sz = Math.sin(a);
			// {steps ahead, steps across, height of the feet over the floor, passes?}
			double[][] cases = {{0, 0, 0, 1}, {0.5, 0.3, 0, 1}, {-0.5, -0.6, 0.5, 1}, {0, 1.0, 0, 1}, {1.5, 0, 0, 0}, {-1.2, 0, 0, 0},
					{0, 2.4, 0, 0}, {0, 0, 4.5, 0}, {0, 0, -2.5, 0}};
			for (double[] c : cases) {
				// the middle of a body as tall as a player's
				boolean through = RiftPortals.through(100 + fx * c[0] + sx * c[1], 64 + c[2] + 0.9, 100 + fz * c[0] + sz * c[1], 100, 64, 100, yaw, r);
				check(helper, through == (c[3] > 0), "facing %s, %s ahead, %s across, %s up: %s", yaw, c[0], c[1], c[2],
						through ? "passed through a tear from outside it" : "walked into a tear and did not pass through");
			}
		}
		helper.succeed();
	}

	/** A tear left to close shrinks shut and goes when it is told; one nobody keeps goes by itself; one kept stays. */
	@GameTest(maxTicks = 100)
	public void aTearClosesWhenItIsNoLongerKept(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(new BlockPos(2, 2, 2));
		RiftPortal closing = new RiftPortal(ModEntities.RIFT_PORTAL, level), unkept = new RiftPortal(ModEntities.RIFT_PORTAL, level),
				kept = new RiftPortal(ModEntities.RIFT_PORTAL, level);
		for (RiftPortal t : new RiftPortal[] {closing, unkept, kept}) {
			t.setPos(o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
			level.addFreshEntity(t);
		}
		closing.closeIn(20);
		for (int t = 1; t <= 70; t++) {
			int at = t;
			helper.runAfterDelay(t, () -> {
				kept.keep();
				if (at == 10) {
					check(helper, !closing.isRemoved() && closing.openness(0) > 0.5F, "a closing tear was gone (or shut) too soon");
				}
			});
		}
		helper.runAfterDelay(75, () -> {
			check(helper, closing.isRemoved(), "a tear told to close in 20 ticks was still there after 75");
			check(helper, unkept.isRemoved(), "a tear nobody kept was still there after 75 ticks");
			check(helper, !kept.isRemoved(), "a tear kept every tick closed anyway");
			kept.discard();
			helper.succeed();
		});
	}

	/**
	 * Bridges pass under a gateway where they leave each island, some long ones are hung from chain cables between
	 * timber frames, and whichever they are, the walk along their middle is clear for a visitor all the way across.
	 */
	@GameTest
	public void bridgesPassUnderGatewaysAndSomeAreHung(GameTestHelper helper) {
		SeamArchitect.Shape a = new SeamArchitect.Shape(0, 90, 0, 14, 0.3, 1.1, 2.0), b = new SeamArchitect.Shape(112, 93, 4, 14, 1.0, 0.2, 2.5);
		int start = a.x() + (int) Math.floor(a.edge(0)) - 2, end = b.x() - (int) Math.floor(b.edge(Math.PI)) + 2, mid = (start + end) / 2;
		Block chain = BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("minecraft", "iron_chain")).map(h -> h.value()).orElse(Blocks.AIR);
		int hung = 0, stone = 0;
		for (int i = 0; i < 24; i++) {
			SeamArchitect.Plan plan = new SeamArchitect.Plan();
			check(helper, SeamArchitect.bridge(plan, new Random(1000 + i), a, b, true), "bridge %s was not built", i);
			check(helper, plan.dropped == 0, "bridge %s ran over the build limit", i);
			if (plan.count(Blocks.DARK_OAK_FENCE) > 0) {
				hung++;
				check(helper, plan.count(chain) > 30, "hung bridge %s has hardly any cable (%s links)", i, plan.count(chain));
				check(helper, plan.count(Blocks.STRIPPED_DARK_OAK_LOG) >= 4 * 2 * 7, "hung bridge %s lacks its four timber frames", i);
			} else {
				stone++;
				check(helper, plan.count(ModBlocks.HUSH_PILLAR) >= 2 * 2 * 3, "stone bridge %s lacks its gateways", i);
			}
			// Along the middle of each run, from rim to landing: a floor with two blocks of air over it
			for (int x = start + 4; x <= end - 4; x++) {
				if (Math.abs(x - mid) <= 4) {
					continue;
				}
				int z = x < mid ? a.z() : b.z();
				boolean walk = false;
				for (int y = 70; y <= 120 && !walk; y++) {
					walk = plan.has(x, y, z) && !plan.has(x, y + 1, z) && !plan.has(x, y + 2, z);
				}
				check(helper, walk, "bridge %s cannot be walked at %s,%s", i, x, z);
			}
		}
		check(helper, hung > 0 && stone > 0, "of 24 long bridges, %s were hung and %s of stone", hung, stone);
		helper.succeed();
	}

	private static boolean is(BlockState s, Block b) {
		return s != null && s.is(b);
	}
}
