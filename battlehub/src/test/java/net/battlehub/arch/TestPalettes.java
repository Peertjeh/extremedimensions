package net.battlehub.arch;

import net.battlehub.arch.mat.Palette;
import net.battlehub.arch.town.Regions;

/** The palette every test building is generated under (the lobby's dimension palette). */
final class TestPalettes {
	private TestPalettes() {}

	static Palette dimension() {
		return Regions.dimension();
	}
}
