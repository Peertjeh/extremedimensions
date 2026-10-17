package net.battlehub.arch.mod;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.battlehub.arch.Rng;
import net.battlehub.arch.bldg.Style;

/**
 * The module library: every module by id and by category. Assembly code asks for "a window for this style that
 * fits 5 × 7 × 3" and gets a weighted random pick, so adding a module needs no change anywhere else.
 */
public final class Library {
	private final Map<String, Module> byId = new LinkedHashMap<>();
	private final Map<Category, List<Module>> byCategory = new EnumMap<>(Category.class);

	public Library register(Module m) {
		Module old = byId.put(m.id(), m);
		if (old != null) byCategory.get(old.category()).remove(old);
		byCategory.computeIfAbsent(m.category(), k -> new ArrayList<>()).add(m);
		return this;
	}

	public Module get(String id) {
		return byId.get(id);
	}

	public List<Module> all(Category c) {
		return List.copyOf(byCategory.getOrDefault(c, List.of()));
	}

	public int size() {
		return byId.size();
	}

	/** The modules of a category that suit the style and fit the size. */
	public List<Module> candidates(Category c, Style style, Spec spec) {
		List<Module> out = new ArrayList<>();
		for (Module m : byCategory.getOrDefault(c, List.of())) {
			if (m.fits(spec) && m.weight(style) > 0) out.add(m);
		}
		return out;
	}

	/** A weighted random pick, or null when nothing suits. */
	public Module pick(Category c, Style style, Spec spec, Rng rng) {
		List<Module> list = candidates(c, style, spec);
		if (list.isEmpty()) return null;
		return rng.weighted(list, m -> m.weight(style) * style.preference(m.id()));
	}

	/** The library with every built-in module. */
	public static Library defaults() {
		Library l = new Library();
		Walls.register(l);
		Windows.register(l);
		Doors.register(l);
		Piers.register(l);
		Roofs.register(l);
		Ornaments.register(l);
		Interiors.register(l);
		return l;
	}
}
