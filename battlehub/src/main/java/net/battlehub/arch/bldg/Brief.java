package net.battlehub.arch.bldg;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.battlehub.arch.mat.Palette;

/**
 * The building's design brief, decided before any geometry and shared by every later stage: archetype, style,
 * size, storeys, symmetry, palette, roof strategy, ornament, condition, detail and special features. Modules read
 * it instead of making their own unrelated decisions.
 */
public final class Brief {
	public enum Symmetry {
		BILATERAL, RADIAL, ASYMMETRIC
	}

	public final String archetype;
	public final Style style;
	public final long seed;
	public int width, depth, height;
	public int storeys = 1;
	public Symmetry symmetry = Symmetry.BILATERAL;
	public Palette palette;
	public double ornament;
	public Condition condition = Condition.MAINTAINED;
	public Randomness randomness = Randomness.BALANCED;
	/** 0 structure only, 1 normal, 2 rich. */
	public int detail = 2;
	public final Set<String> features = new HashSet<>();
	/** The choices the archetype made (towers, transept, crown...), for checks and fingerprints. */
	public final Map<String, Object> choices = new HashMap<>();
	/** A palette recipe to use instead of a random one (null: random). */
	public String paletteRecipe;

	public Brief(String archetype, Style style, long seed) {
		this.archetype = archetype;
		this.style = style;
		this.seed = seed;
		this.ornament = style.ornament;
	}

	public Brief size(int w, int d, int h) {
		this.width = w;
		this.depth = d;
		this.height = h;
		return this;
	}

	public Brief condition(Condition c) {
		this.condition = c;
		return this;
	}

	public Brief detail(int d) {
		this.detail = d;
		return this;
	}

	public Brief feature(String f) {
		features.add(f);
		return this;
	}

	public boolean has(String f) {
		return features.contains(f);
	}

	public Brief randomness(Randomness r) {
		this.randomness = r;
		return this;
	}

	public Brief palette(String recipe) {
		this.paletteRecipe = recipe;
		return this;
	}

	public void choose(String key, Object value) {
		choices.put(key, value);
	}

	@Override
	public String toString() {
		return archetype + "/" + style.name + " " + width + "x" + depth + "x" + height + " " + condition + " " + choices;
	}
}
