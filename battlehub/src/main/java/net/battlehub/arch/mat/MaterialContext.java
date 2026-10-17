package net.battlehub.arch.mat;

import java.util.EnumMap;
import java.util.Map;

import net.battlehub.arch.Easing;
import net.battlehub.arch.Noise3;
import net.battlehub.arch.Slot;

/**
 * What every module of one building shares when blocks get their material: the building's palette, a gradient
 * field per role (all in building coordinates), the noise for coherent patches and how weathered the building is.
 * Because every module asks the same context, a gradient runs on unbroken across hundreds of modules.
 */
public final class MaterialContext {
	public final long seed;
	public final Palette palette;
	private final Map<Slot, Gradient> fields = new EnumMap<>(Slot.class);
	private Gradient common = Gradient.constant(0.5);
	final Noise3 patch;
	final Noise3 weatherNoise;
	/** 0 = new, 1 = as weathered as it gets. */
	double weathering;
	/** Weathering is strongest within this many blocks of the ground and fades above. */
	double weatherReach = 7;
	int groundY;

	public MaterialContext(long seed, Palette palette) {
		this.seed = seed;
		this.palette = palette;
		this.patch = new Noise3(seed ^ 0x5DEECE66DL);
		this.weatherNoise = new Noise3(seed ^ 0x2545F4914F6CDD1DL);
	}

	/** The field for every role that has none of its own. */
	public MaterialContext common(Gradient g) {
		common = g;
		return this;
	}

	public MaterialContext field(Slot slot, Gradient g) {
		fields.put(slot, g);
		return this;
	}

	public Gradient field(Slot slot) {
		for (Slot s = slot; s != null; s = s.fallback) {
			Gradient g = fields.get(s);
			if (g != null) return g;
		}
		return common;
	}

	public MaterialContext weathering(double amount, int groundY, double reach) {
		this.weathering = Easing.clamp(amount);
		this.groundY = groundY;
		this.weatherReach = reach;
		return this;
	}

	public double weathering() {
		return weathering;
	}

	/** How strongly weathering shows here (0..1): more near the ground, in patches. */
	public double weatherAt(Probe p) {
		if (weathering <= 0) return 0;
		double low = 1 - Easing.norm(p.by - groundY, 0, weatherReach * (0.6 + weathering));
		double base = weathering * (0.35 + 0.65 * low);
		return Easing.clamp(base);
	}
}
