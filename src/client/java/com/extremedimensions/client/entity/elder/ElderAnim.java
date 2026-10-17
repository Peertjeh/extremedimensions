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
