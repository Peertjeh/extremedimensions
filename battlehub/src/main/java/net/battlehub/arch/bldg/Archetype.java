package net.battlehub.arch.bldg;

import net.battlehub.arch.Rng;
import net.battlehub.arch.mat.Palette;

/**
 * A kind of building as a grammar: how a brief is chosen (sizes, options, palette within a style), how the
 * building decomposes into volumes (massing first), and how those volumes are assembled from modules. New
 * archetypes plug into {@link Architect} without touching the engine.
 */
public interface Archetype {
	String id();

	/** The styles this archetype is built in, the first being its usual one. */
	java.util.List<Style> styles();

	/** Picks the brief's sizes and options for a style. {@code scale} is 1 for normal size. */
	Brief brief(Style style, long seed, Palette district, double scale);

	/** Massing: the volumes, before any wall is drawn. */
	void plan(Building b, Rng rng);

	/** Fills the volumes in. */
	void assemble(Building b, Rng rng);

	/** Picks a palette for a brief: its recipe if it names one, else a random one of the style's. */
	static Palette palette(Brief brief, Palette district, Rng rng) {
		return brief.paletteRecipe != null ? brief.style.palette(district, brief.paletteRecipe, rng) : brief.style.palette(district, rng);
	}
}
