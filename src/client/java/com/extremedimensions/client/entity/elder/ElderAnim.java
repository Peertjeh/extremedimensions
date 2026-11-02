package com.extremedimensions.client.entity.elder;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Small helpers for the Elders' animation: envelopes for blows and lookups of part groups. */
final class ElderAnim {
	private ElderAnim() {}

	static float smooth(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3 - 2 * x);
	}

	/** 0 → 1 over {@code in} ticks, held for {@code hold}, back to 0 over {@code out}. */
	static float env(float t, float in, float hold, float out) {
		if (t < 0) {
			return 0;
		}
		if (t < in) {
			return smooth(t / in);
		}
		if (t < in + hold) {
			return 1;
		}
		return 1 - smooth((t - in - hold) / out);
	}

	/** Back-out easing: it snaps past where it is going and settles back (a blow that follows through). */
	static float snap(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		float c1 = 1.70158F, c3 = c1 + 1, u = x - 1;
		return 1 + c3 * u * u * u + c1 * u * u;
	}

	/** Gathering for a blow that lands at {@code impact}: 0 → 1 over the {@code wind} ticks before it, let go at once. */
	static float gather(float t, float impact, float wind) {
		if (t >= impact) {
			return Math.max(0, 1 - (t - impact) / 2F);
		}
		return smooth((t - (impact - wind)) / wind);
	}

	/** The jolt of a blow landing at {@code impact}: 1 at once, dying away over {@code decay} ticks with a rebound. */
	static float jolt(float t, float impact, float decay) {
		float s = t - impact;
		if (s < 0 || s > decay) {
			return 0;
		}
		float k = s / decay;
		return (1 - k) * (1 - k) * Mth.cos(k * Mth.PI * 1.5F);
	}

	/** A tremor, as of something straining: quick small shakes of about {@code amp}. */
	static float tremor(float t, float amp) {
		return (Mth.sin(t * 2.9F) * 0.6F + Mth.sin(t * 4.7F + 1.1F) * 0.4F) * amp;
	}

	/**
	 * The weight a blow lends the whole body, for an action whose blows land at {@code impacts} (ticks at tempo 1):
	 * {gather, jolt}: how far it has gathered for the next one (a crouch, a draw back, a strain) and how hard the last
	 * one jolts it (thrown into the blow, then a rebound).
	 */
	static float[] accent(float t, float[] impacts, float wind, float decay) {
		float g = 0, j = 0;
		for (float i : impacts) {
			g = Math.max(g, gather(t, i, wind));
			float ji = jolt(t, i, decay);
			if (Math.abs(ji) > Math.abs(j)) {
				j = ji;
			}
		}
		return new float[] {g, j};
	}

	static final float[] NONE = {};

	/** Back to its own size (every frame, before an animation scales it: a pose need not reset the scale). */
	static void reset(ModelPart... parts) {
		for (ModelPart p : parts) {
			p.xScale = 1;
			p.yScale = 1;
			p.zScale = 1;
		}
	}

	/** Scales a part by {@code k} on top of whatever it is scaled by already. */
	static void scale(ModelPart p, float k) {
		p.xScale *= k;
		p.yScale *= k;
		p.zScale *= k;
	}

	interface Finder {
		ModelPart find(ModelPart root, String name);
	}

	static ModelPart[] parts(ModelPart root, String[] names, Finder finder) {
		ModelPart[] out = new ModelPart[names.length];
		for (int i = 0; i < names.length; i++) {
			out[i] = finder.find(root, names[i]);
		}
		return out;
	}
}
