package net.battlehub.arch.bldg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.battlehub.arch.Rng;
import net.battlehub.arch.Xform;
import net.battlehub.arch.mat.Palette;
import net.battlehub.arch.mod.Library;

/**
 * Runs the generation stages for one building: brief, massing (volumes), a check of the massing (hierarchy),
 * assembly from modules, condition (weathering, damage, plants) and the material context. Every stage has its
 * own random stream, so a change in one does not reshuffle the others.
 */
public final class Architect {
	private Architect() {}

	private static final Map<String, Archetype> ARCHETYPES = new LinkedHashMap<>();

	static {
		register(new Cathedral());
		register(new Castle());
		register(new MageTower());
		register(new TempleRuin());
		register(new Townhouse());
		register(new Watchtower());
		register(new Chapel());
		register(new MarketHall());
	}

	public static void register(Archetype a) {
		ARCHETYPES.put(a.id(), a);
	}

	public static Archetype get(String id) {
		return ARCHETYPES.get(id);
	}

	public static List<Archetype> all() {
		return new ArrayList<>(ARCHETYPES.values());
	}

	/** A brief for an archetype in its usual style. */
	public static Brief brief(String archetype, long seed, Palette district, double scale) {
		Archetype a = get(archetype);
		return a.brief(a.styles().get(0), seed, district, scale);
	}

	/** Generates a building from a brief, placed in the world by {@code frame}. */
	public static Building build(Brief brief, Library lib, Xform frame, Building.Ground ground) {
		Archetype a = get(brief.archetype);
		if (a == null) throw new IllegalArgumentException("unknown archetype " + brief.archetype);
		// a recipe chosen after the brief was written replaces the palette it got
		if (brief.paletteRecipe != null && brief.palette != null && !brief.palette.name().endsWith(":" + brief.paletteRecipe)
				&& brief.palette.parent() != null) {
			brief.palette = brief.style.palette(brief.palette.parent(), brief.paletteRecipe, new Rng(brief.seed).stream("palette"));
		}
		Building b = new Building(brief, lib, frame, ground);
		Rng root = new Rng(brief.seed);
		a.plan(b, root.stream("massing"));
		Massing.check(b);
		a.assemble(b, root.stream("assembly"));
		Weathering.apply(b, root.stream("weathering"));
		b.materials();
		return b;
	}
}
