package net.battlehub.arch.mat;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.arch.Easing;
import net.battlehub.arch.Noise3;

/**
 * A field over space with values 0..1 that says where along a ramp a block falls. Fields are combined in a
 * {@link Stack}: a weighted, normalised blend G(p) = Σ wᵢ·gᵢ(p) / Σ wᵢ, nudged by low-amplitude coherent noise,
 * t' = clamp(G + α·N(p)). All of it is worked out in building coordinates.
 */
public interface Gradient {
	double at(Probe p);

	/** Bottom to top between two heights. */
	static Gradient vertical(double y0, double y1, Easing e) {
		return p -> e.apply(Easing.norm(p.by, y0, y1));
	}

	/** Along one horizontal axis of the building (x when {@code alongX}), between two coordinates. */
	static Gradient horizontal(boolean alongX, double a0, double a1, Easing e) {
		return p -> e.apply(Easing.norm(alongX ? p.bx : p.bz, a0, a1));
	}

	/**
	 * 0 on the building's centre lines, 1 at its outer edges: darker corners frame a facade when the ramp's dark
	 * end is used for 1 (combine with {@link #invert}).
	 */
	static Gradient edges(double cx, double cz, double halfX, double halfZ, Easing e) {
		return p -> e.apply(Math.max(Math.abs(p.bx - cx) / Math.max(1, halfX), Math.abs(p.bz - cz) / Math.max(1, halfZ)));
	}

	/** 1 at a focal point, fading to 0 at {@code radius}. */
	static Gradient radial(double cx, double cy, double cz, double radius, Easing e) {
		return p -> {
			double dx = p.bx - cx, dy = p.by - cy, dz = p.bz - cz;
			return e.apply(1 - Math.sqrt(dx * dx + dy * dy + dz * dz) / radius);
		};
	}

	/** By world position: continues across buildings (a whole district shifting towards one material). */
	static Gradient worldRadial(double cx, double cz, double radius, Easing e) {
		return p -> e.apply(1 - Math.hypot(p.wx - cx, p.wz - cz) / radius);
	}

	static Gradient constant(double v) {
		return p -> v;
	}

	/** Coherent noise in building space ({@code scale} blocks across). */
	static Gradient noise(long seed, double scale, int octaves) {
		Noise3 n = new Noise3(seed);
		return p -> n.at(p.bx, p.by, p.bz, scale, octaves);
	}

	default Gradient invert() {
		return p -> 1 - at(p);
	}

	/**
	 * A blend of weighted fields plus bounded noise. Local influences (a focal point, darker corners) are added
	 * as offsets after the blend: as a blend member a local field would pull every block everywhere.
	 */
	final class Stack implements Gradient {
		private final List<Gradient> fields = new ArrayList<>();
		private final List<Double> weights = new ArrayList<>();
		private final List<Gradient> offsets = new ArrayList<>();
		private final List<Double> amounts = new ArrayList<>();
		private Noise3 noise;
		private double amplitude;
		private double scale = 6;
		private Easing easing = Easing.LINEAR;

		public Stack add(Gradient g, double weight) {
			fields.add(g);
			weights.add(weight);
			return this;
		}

		/** A local influence: {@code amount} × g(p) added after the blend (negative darkens). */
		public Stack offset(Gradient g, double amount) {
			offsets.add(g);
			amounts.add(amount);
			return this;
		}

		/** Noise added to the blend: amplitude α (keep it small so the gradient still reads) at a scale. */
		public Stack noise(long seed, double amplitude, double scale) {
			this.noise = new Noise3(seed);
			this.amplitude = amplitude;
			this.scale = scale;
			return this;
		}

		public Stack ease(Easing e) {
			this.easing = e;
			return this;
		}

		@Override
		public double at(Probe p) {
			double sum = 0, w = 0;
			for (int i = 0; i < fields.size(); i++) {
				double wi = weights.get(i);
				sum += wi * fields.get(i).at(p);
				w += wi;
			}
			double t = w == 0 ? 0.5 : sum / w;
			for (int i = 0; i < offsets.size(); i++) t += amounts.get(i) * offsets.get(i).at(p);
			if (noise != null && amplitude > 0) {
				t += amplitude * noise.signed(p.bx, p.by, p.bz, scale, 2);
			}
			return easing.apply(Easing.clamp(t));
		}

		public int size() {
			return fields.size();
		}
	}
}
