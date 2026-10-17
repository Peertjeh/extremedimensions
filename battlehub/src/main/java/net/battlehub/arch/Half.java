package net.battlehub.arch;

/** Upper or lower half (stairs, slabs, trapdoors, doors); slabs can also be double. */
public enum Half {
	BOTTOM, TOP, DOUBLE;

	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}
