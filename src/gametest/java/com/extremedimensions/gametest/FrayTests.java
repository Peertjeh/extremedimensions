package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.config.Config;
import com.extremedimensions.seam.SeamArchitect;
import com.extremedimensions.seam.SeamDepth;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** The Fray beneath the Seam's islands: knots out past the rims, wells over them, strands between, a faster drain. */
public class FrayTests {
	@GameTest
	public void theFrayHangsBeneathTheIslands(GameTestHelper helper) {
		double wells = Config.SEAM_WELL_CHANCE;
		Config.SEAM_WELL_CHANCE = 1.0;
		try {
			long seed = 12345L;
			SeamArchitect.Shape island = new SeamArchitect.Shape(1050, 90, 1050, 14, 0.3, 1.1, 2.0);
			SeamArchitect.Shape east = new SeamArchitect.Shape(1150, 86, 1046, 12, 1.0, 0.2, 2.5);
			SeamDepth.Knot k = SeamDepth.knot(10, 10, island, seed);
			SeamDepth.Knot e = SeamDepth.knot(11, 10, east, seed);
			check(helper, k != null && k.well(), "with every island given a well, this one has none");
			check(helper, !island.inside(k.x(), k.z(), -2), "the knot hangs under its island (%s,%s)", k.x(), k.z());
			check(helper, k.y() < Config.SEAM_FRAY_TOP && k.y() > 8, "the knot hangs at %s, not in the Fray", k.y());
			SeamArchitect.Plan plan = SeamDepth.plan(10, 10, island, east, null, seed);
			check(helper, plan != null, "no Fray was planned");
			check(helper, plan.has(k.x(), k.y(), k.z()), "the knot has no landing under its well");
			check(helper, !plan.has(k.x(), k.top(), k.z()) && plan.has(k.x() + 2, k.top(), k.z()), "the well is no ring over a drop");
			check(helper, plan.marks.contains("w@" + k.x() + "," + k.top() + "," + k.z() + "," + k.y()), "the well was not remembered");
			check(helper, plan.marks.contains("k@" + k.x() + "," + k.y() + "," + k.z()), "the knot was not remembered");
			// The strand east: somewhere in the column halfway between the knots
			int mx = (k.x() + e.x()) / 2, mz = (k.z() + e.z()) / 2;
			boolean strand = false;
			for (int y = 2; y < Config.SEAM_FRAY_TOP && !strand; y++) {
				for (int dx = -1; dx <= 1 && !strand; dx++) {
					for (int dz = -1; dz <= 1 && !strand; dz++) {
						strand = plan.has(mx + dx, y, mz + dz);
					}
				}
			}
			check(helper, strand, "no strand runs from the knot to the next one east");
		} finally {
			Config.SEAM_WELL_CHANCE = wells;
		}
		helper.succeed();
	}

	@GameTest
	public void timeRunsOutFasterInTheFray(GameTestHelper helper) {
		check(helper, SeamDepth.drain(Config.SEAM_FRAY_TOP - 10) == Config.SEAM_FRAY_DRAIN, "the Fray drains at %s", SeamDepth.drain(Config.SEAM_FRAY_TOP - 10));
		check(helper, SeamDepth.drain(Config.SEAM_FRAY_TOP + 30) == 1.0, "the islands drain at %s", SeamDepth.drain(Config.SEAM_FRAY_TOP + 30));
		SeamDepth.Well w = new SeamDepth.Well(100, 90, 100, 30);
		check(helper, SeamDepth.inShaft(w, 100.5, 60, 100.5, 2) && !SeamDepth.inShaft(w, 104.5, 60, 100.5, 2), "the shaft is not where its well is");
		check(helper, !SeamDepth.inShaft(w, 100.5, 95, 100.5, 2) && !SeamDepth.inShaft(w, 100.5, 20, 100.5, 2), "the shaft reaches past its ends");
		helper.succeed();
	}
}
