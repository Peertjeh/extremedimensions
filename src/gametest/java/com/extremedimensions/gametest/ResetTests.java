package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.extremedimensions.seam.SeamReset;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Making the Seam anew, on a world folder of its own: a reset waits until it is carried out and can be called off, it
 * wipes the Seam's folder and nothing else of the world (not the Overworld, not the Nether, not a data pack that names
 * the Seam), keeps the old Seam aside when asked to, and finds the Seam wherever a version keeps its worlds.
 */
public class ResetTests {
	/** A world folder with an Overworld, a Nether, the Seam and a data pack with a folder named like the Seam. */
	private static Path world() throws IOException {
		Path root = Files.createTempDirectory("ed-reset").toAbsolutePath().normalize();
		for (String file : new String[] {"level.dat", "region/r.0.0.mca", "DIM-1/region/r.0.0.mca", "dimensions/extremedimensions/the_seam/region/r.0.0.mca",
				"dimensions/extremedimensions/the_seam/entities/r.0.0.mca", "datapacks/pack/data/extremedimensions/the_seam/region/keep.mca"}) {
			Path p = root.resolve(file);
			Files.createDirectories(p.getParent());
			Files.writeString(p, file);
		}
		return root;
	}

	private static Path seam(Path root) {
		return root.resolve("dimensions/extremedimensions/the_seam");
	}

	private static boolean untouched(Path root) {
		return Files.exists(root.resolve("level.dat")) && Files.exists(root.resolve("region/r.0.0.mca")) && Files.exists(root.resolve("DIM-1/region/r.0.0.mca"))
				&& Files.exists(root.resolve("datapacks/pack/data/extremedimensions/the_seam/region/keep.mca"));
	}

	private static long kept(Path root) throws IOException {
		try (Stream<Path> list = Files.list(root.resolve("dimensions/extremedimensions"))) {
			return list.filter(p -> p.getFileName().toString().startsWith("the_seam-before-reset-")).count();
		}
	}

	/** A reset asked for waits, says who asked, and is gone once called off; calling off nothing says so. */
	@GameTest
	public void aResetWaitsUntilItIsCalledOff(GameTestHelper helper) throws IOException {
		Path root = world();
		check(helper, !SeamReset.waiting(root), "a fresh world has a reset waiting");
		check(helper, SeamReset.ask(root, "Tester") && SeamReset.waiting(root), "a reset asked for is not waiting");
		check(helper, SeamReset.askedBy(root).equals("Tester"), "the waiting reset says it was asked for by '%s'", SeamReset.askedBy(root));
		check(helper, SeamReset.callOff(root) && !SeamReset.waiting(root), "a reset called off is still waiting");
		check(helper, !SeamReset.callOff(root), "calling off a reset that was not waiting said it was");
		check(helper, Files.exists(seam(root).resolve("region/r.0.0.mca")) && untouched(root), "asking for a reset and calling it off changed the world");
		helper.succeed();
	}

	/** A reset wipes the Seam's folder and takes the request away; everything else of the world stays. */
	@GameTest
	public void aResetWipesTheSeamAndNothingElse(GameTestHelper helper) throws IOException {
		Path root = world();
		SeamReset.ask(root, "Tester");
		check(helper, SeamReset.folder(root) != null && SeamReset.folder(root).equals(seam(root)), "the Seam was looked for in %s", SeamReset.folder(root));
		check(helper, SeamReset.wipe(root, false), "the reset failed");
		check(helper, !Files.exists(seam(root)), "the Seam's folder is still there");
		check(helper, kept(root) == 0, "the old Seam was kept although it was not asked to be");
		check(helper, !SeamReset.waiting(root), "the reset is still waiting after it was carried out");
		check(helper, untouched(root), "the reset touched more of the world than the Seam");
		// Nothing left to wipe: a second reset only takes its request away
		SeamReset.ask(root, "Tester");
		check(helper, SeamReset.wipe(root, false) && !SeamReset.waiting(root) && untouched(root), "a reset of a Seam with no folder failed");
		helper.succeed();
	}

	/** Asked to, a reset keeps the old Seam beside the new one, whole. */
	@GameTest
	public void aResetCanKeepTheOldSeam(GameTestHelper helper) throws IOException {
		Path root = world();
		SeamReset.ask(root, "Tester");
		check(helper, SeamReset.wipe(root, true), "the reset failed");
		check(helper, !Files.exists(seam(root)) && kept(root) == 1, "the old Seam was not moved aside (%s copies)", kept(root));
		try (Stream<Path> list = Files.list(root.resolve("dimensions/extremedimensions"))) {
			Path copy = list.filter(p -> p.getFileName().toString().startsWith("the_seam-before-reset-")).findFirst().orElseThrow();
			check(helper, Files.exists(copy.resolve("region/r.0.0.mca")) && Files.exists(copy.resolve("entities/r.0.0.mca")), "the kept Seam is not whole");
		}
		check(helper, !SeamReset.waiting(root) && untouched(root), "keeping the old Seam left the reset waiting or touched the world");
		helper.succeed();
	}

	/** Kept somewhere else than usual, the Seam is still found (by its chunks), but never in a data pack. */
	@GameTest
	public void aResetFindsTheSeamWhereverItIsKept(GameTestHelper helper) throws IOException {
		Path root = world();
		Path elsewhere = root.resolve("worlds/extremedimensions/the_seam");
		Files.createDirectories(elsewhere.resolve("region"));
		Files.move(seam(root).resolve("region/r.0.0.mca"), elsewhere.resolve("region/r.0.0.mca"));
		Files.walk(seam(root)).sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		check(helper, elsewhere.equals(SeamReset.folder(root)), "the Seam kept elsewhere was looked for in %s", SeamReset.folder(root));
		check(helper, SeamReset.wipe(root, false) && !Files.exists(elsewhere) && untouched(root), "the Seam kept elsewhere was not wiped, or more was");
		check(helper, SeamReset.folder(root) == null, "with the Seam gone, %s was taken for it", SeamReset.folder(root));
		helper.succeed();
	}
}
