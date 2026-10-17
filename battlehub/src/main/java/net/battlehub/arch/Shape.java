package net.battlehub.arch;

/** The block form a role takes: a full block, stairs, a slab, a wall post and so on. */
public enum Shape {
	CUBE, STAIRS, SLAB, WALL, FENCE, PANE, LOG, TRAPDOOR, DOOR, LITERAL;

	/** A full, solid block (others connect to it). */
	public boolean full() {
		return this == CUBE || this == LOG;
	}
}
