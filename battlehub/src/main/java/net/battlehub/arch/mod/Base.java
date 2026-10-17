package net.battlehub.arch.mod;

import java.util.Set;

/** A module with its contract given at construction; subclasses only draw. */
abstract class Base implements Module {
	private final String id;
	private final Category category;
	private final Set<String> styles;
	private final int[] min;
	private final int[] max;

	Base(String id, Category category, Set<String> styles, int[] min, int[] max) {
		this.id = id;
		this.category = category;
		this.styles = Set.copyOf(styles);
		this.min = min;
		this.max = max;
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public Category category() {
		return category;
	}

	@Override
	public Set<String> styles() {
		return styles;
	}

	@Override
	public int[] min() {
		return min.clone();
	}

	@Override
	public int[] max() {
		return max.clone();
	}

	static int[] size(int w, int h, int d) {
		return new int[] {w, h, d};
	}

	static final int[] ANY = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};

	static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
