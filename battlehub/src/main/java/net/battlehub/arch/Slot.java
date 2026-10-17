package net.battlehub.arch;

/**
 * The architectural role of a block. Geometry is drawn in roles, not in block names: which block a role becomes
 * is decided afterwards by the building's palette and gradients, so a wall stays a wall and glass stays glass
 * whatever a gradient says. {@link #fallback} is the role asked next when a palette has nothing for this one.
 */
public enum Slot {
	/** Explicit empty space (openings, rooms); clears terrain. */
	AIR(null),
	/** The top of the land, the soil under it and the rock deeper down / under a floating island. */
	GROUND(null), SOIL(null), ROCK(null),
	/** Roads, and paved squares and courtyards. */
	PATH(null), PAVING(PATH),
	/** Heavy base masonry: plinths, socles, retaining walls. */
	FOUNDATION(null),
	/** The main wall masonry, and a second masonry for panels and infill. */
	WALL(null), WALL_ALT(WALL),
	/** Horizontal courses, corner stones, piers and columns. */
	BAND(null), QUOIN(null), PILLAR(null),
	/** Frames, sills, lintels, cornices, mouldings. */
	TRIM(null),
	/** Arch rings and vault ribs. */
	ARCH(TRIM),
	/** Roof surface and its edges (ridges, hips, eaves). */
	ROOF(null), ROOF_EDGE(ROOF),
	/** Floors inside, and the second floor material for patterns. */
	FLOOR(null), FLOOR_ALT(FLOOR),
	/** Clear glazing and stained glass (the variant picks the colour of a composition). */
	GLASS(null), STAINED(GLASS),
	/** Iron and copper fittings, light sources. */
	METAL(null), LIGHT(null),
	/** Planks (doors, floors, furniture) and timber beams. */
	WOOD(null), TIMBER(WOOD),
	/** Luminous, magical material. */
	MAGIC(null),
	/** A rare highlight (gold, chiseled stone). */
	ACCENT(TRIM),
	/** Leaves, moss and the like; water; fallen stone. */
	LEAVES(null), WATER(null), RUBBLE(null),
	/** A block given by name (furniture, decoration). */
	LITERAL(null);

	public final Slot fallback;

	Slot(Slot fallback) {
		this.fallback = fallback;
	}

	/** Masonry-like roles that weathering may crack or overgrow. */
	public boolean weathers() {
		return switch (this) {
			case FOUNDATION, WALL, WALL_ALT, BAND, QUOIN, PILLAR, TRIM, ARCH, PAVING, PATH -> true;
			default -> false;
		};
	}

	/** Roles that hold the building up (damage takes them last). */
	public boolean structural() {
		return switch (this) {
			case FOUNDATION, WALL, WALL_ALT, QUOIN, PILLAR, ARCH -> true;
			default -> false;
		};
	}
}
