package net.battlehub.arch.mod;

import net.battlehub.arch.Dir;
import net.battlehub.arch.Xform;

/**
 * A connection point a module offers: an entrance (with the direction you walk out), a roof seat, a stair
 * landing, a wall to lean a buttress on. Given in the module's frame; {@link #in} moves it to the building.
 */
public record Attachment(String name, Kind kind, int x, int y, int z, Dir facing, int w, int h) {
	public enum Kind {
		/** A way in: clear space w × h, walking out towards {@code facing}. */
		ENTRANCE,
		/** The top of walls a roof must sit on (x,z = one corner, w × h = the footprint). */
		ROOF_SEAT,
		/** Where stairs arrive or leave. */
		LANDING,
		/** Ground a structure stands on. */
		BASE,
		/** A face other modules may lean on (buttresses, bridges). */
		FACE
	}

	public Attachment in(Xform f) {
		int ax = f.x(x, z), az = f.z(x, z);
		return new Attachment(name, kind, ax, f.y(y), az, f.dir(facing), w, h);
	}
}
