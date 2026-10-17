package net.battlehub.arch.mod;

import java.util.List;
import java.util.Set;

import net.battlehub.arch.bldg.Style;

/**
 * A reusable architectural part with a contract: a category, the styles it suits, the sizes it accepts and the
 * attachments it offers. It draws itself in its own frame (x along its front, y up, z outwards; z = 0 is the
 * outer face of the wall it sits in) through a {@link Ctx}, in roles only, so the building decides materials.
 * Register new modules in a {@link Library}; the assembly code finds them by category and style.
 */
public interface Module {
	String id();

	Category category();

	/** Style tags this module suits; empty for any style. */
	Set<String> styles();

	/** Smallest and largest size accepted: {w, h, d}. */
	default int[] min() {
		return new int[] {1, 1, 1};
	}

	default int[] max() {
		return new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
	}

	default boolean fits(Spec s) {
		int[] lo = min(), hi = max();
		return s.w() >= lo[0] && s.h() >= lo[1] && s.d() >= lo[2] && s.w() <= hi[0] && s.h() <= hi[1] && s.d() <= hi[2];
	}

	/** How much this module wants to be chosen for a style (0 = never). */
	default double weight(Style style) {
		if (styles().isEmpty()) return 1;
		for (String t : styles()) {
			if (style.has(t)) return 1;
		}
		return 0;
	}

	default List<Attachment> attachments(Spec s) {
		return List.of();
	}

	void build(Ctx c, Spec s);
}
