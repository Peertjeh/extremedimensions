package com.extremedimensions.client.entity.elder;

import java.util.Arrays;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Small helpers for the Elders' animation: envelopes for blows, whole turns, things kept hanging true and lookups of part
 * groups.
 */
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

	/** A blow driven home: slow to start and fastest as it lands at 1, where it stops dead. */
	static float fall(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * x;
	}

	/**
	 * How far something has turned at {@code t} ticks, making {@code turns} whole turns: it spins up over {@code up} ticks,
	 * turns steadily for {@code steady} and slows to rest over {@code down}, so it ends where it began, with no jump.
	 */
	static float spin(float t, float up, float steady, float down, int turns) {
		// the speed eases in and out (smoothstep), so it starts and stops without a jolt
		float rate = Mth.PI * 2 * turns / (up / 2 + steady + down / 2);
		if (t <= 0) {
			return 0;
		}
		if (t < up) {
			float x = t / up;
			return rate * up * (x * x * x - x * x * x * x / 2);
		}
		if (t < up + steady) {
			return rate * (up / 2 + t - up);
		}
		float y = Math.min(1, (t - up - steady) / down);
		return rate * (up / 2 + steady + down * (y - y * y * y + y * y * y * y / 2));
	}

	/** How fast {@link #spin} is turning at {@code t}, from 0 at rest to 1 at full speed. */
	static float spinRate(float t, float up, float steady, float down) {
		return t < up ? smooth(t / up) : 1 - smooth((t - up - steady) / down);
	}

	/**
	 * The direction (x, y, z) of the model (a unit {x, y, z}) in the frame of the last of {@code chain}: the parts from the
	 * root down, however they are turned.
	 */
	static float[] aim(float x, float y, float z, ModelPart... chain) {
		float n = Mth.sqrt(x * x + y * y + z * z);
		x /= n;
		y /= n;
		z /= n;
		for (ModelPart p : chain) {
			// into the part's own frame: undo its turns (Z, then Y, then X)
			float c = Mth.cos(p.zRot), s = Mth.sin(p.zRot);
			float u = c * x + s * y;
			y = c * y - s * x;
			x = u;
			c = Mth.cos(p.yRot);
			s = Mth.sin(p.yRot);
			u = c * x - s * z;
			z = s * x + c * z;
			x = u;
			c = Mth.cos(p.xRot);
			s = Mth.sin(p.xRot);
			u = c * y + s * z;
			z = c * z - s * y;
			y = u;
		}
		return new float[] {x, y, z};
	}

	/**
	 * Something hung from a chain of parts that is kept hanging true (or pointing anywhere else) however they are turned.
	 * It is turned the least way from how it would hang were it fixed to an anchor part higher up (the body, which does not
	 * turn over), so it never twists about itself, even as the arm it hangs from turns right over, and it turns round
	 * with the body.
	 */
	static final class Hang {
		private final ModelPart part;
		private final ModelPart[] chain, inner;
		/** Its turn at rest {xRot, yRot, zRot}, and its axes at rest (by columns) in the anchor's frame. */
		private final float[] rest, held = new float[9];

		/**
		 * Made with the model, at rest: {@code chain} is every part it hangs from, from the root down to its parent, and
		 * the first {@code anchor} of them stand above the anchor's frame.
		 */
		Hang(ModelPart part, int anchor, ModelPart... chain) {
			this.part = part;
			this.chain = chain;
			this.inner = Arrays.copyOfRange(chain, anchor, chain.length);
			this.rest = new float[] {part.xRot, part.yRot, part.zRot};
			float[] r = turn(rest[0], rest[1], rest[2]);
			for (int i = 0; i < 3; i++) {
				float[] v = {r[i], r[3 + i], r[6 + i]};
				for (int j = inner.length - 1; j >= 0; j--) {
					float[] m = turn(inner[j].xRot, inner[j].yRot, inner[j].zRot);
					v = new float[] {m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
							m[6] * v[0] + m[7] * v[1] + m[8] * v[2]};
				}
				held[i] = v[0];
				held[3 + i] = v[1];
				held[6 + i] = v[2];
			}
		}

		/**
		 * Turns it by {@code k} of the way from how it hangs at rest to point its +Y along (x, y, z) of the model (straight
		 * down, to hang); what it has been swung by already stays. Returns that direction in its parent's frame.
		 */
		float[] point(float k, float x, float y, float z) {
			float[] b = aim(x, y, z, chain);
			// how it would hang fixed to the anchor, in its parent's frame
			float[] cx = aim(held[0], held[3], held[6], inner), cy = aim(held[1], held[4], held[7], inner);
			float[] cz = aim(held[2], held[5], held[8], inner);
			// and the least turn bringing its +Y round to b: c I + [v]x + v v' / (1 + c)
			float vx = cy[1] * b[2] - cy[2] * b[1], vy = cy[2] * b[0] - cy[0] * b[2], vz = cy[0] * b[1] - cy[1] * b[0];
			float c = Math.max(-0.999F, cy[0] * b[0] + cy[1] * b[1] + cy[2] * b[2]), f = 1 / (1 + c);
			float m00 = c + f * vx * vx, m01 = -vz + f * vx * vy, m02 = vy + f * vx * vz;
			float m10 = vz + f * vx * vy, m11 = c + f * vy * vy, m12 = -vx + f * vy * vz;
			float m20 = -vy + f * vx * vz, m21 = vx + f * vy * vz, m22 = c + f * vz * vz;
			// the two together, back into turns about X, Y and Z
			float n00 = m00 * cx[0] + m01 * cx[1] + m02 * cx[2], n10 = m10 * cx[0] + m11 * cx[1] + m12 * cx[2];
			float n20 = m20 * cx[0] + m21 * cx[1] + m22 * cx[2];
			float n21 = m20 * cy[0] + m21 * cy[1] + m22 * cy[2], n22 = m20 * cz[0] + m21 * cz[1] + m22 * cz[2];
			float ny = (float) Math.asin(Mth.clamp(-n20, -1.0F, 1.0F));
			float nx = (float) Math.atan2(n21, n22), nz = (float) Math.atan2(n10, n00);
			part.xRot += k * wrap(nx - rest[0]);
			part.yRot += k * wrap(ny - rest[1]);
			part.zRot += k * wrap(nz - rest[2]);
			return b;
		}
	}

	/** The matrix (by rows) of turns about X, then Y, then Z, as a part makes them. */
	private static float[] turn(float x, float y, float z) {
		float cx = Mth.cos(x), sx = Mth.sin(x), cy = Mth.cos(y), sy = Mth.sin(y), cz = Mth.cos(z), sz = Mth.sin(z);
		return new float[] {cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx,
				sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx,
				-sy, cy * sx, cy * cx};
	}

	/** An angle brought within half a turn of nothing. */
	static float wrap(float a) {
		return (float) (a - Math.PI * 2 * Math.round(a / (Math.PI * 2)));
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

	/**
	 * The shudder of something loosely hung (a halo, cloth) as blows land at {@code impacts}: from rest it is thrown out
	 * within a tick, swings back past where it was and dies away over {@code decay} ticks.
	 */
	static float shudder(float t, float[] impacts, float decay) {
		float sum = 0;
		for (float i : impacts) {
			float u = t - i;
			if (u > 0 && u < decay) {
				float k = u / decay;
				sum += (1 - k) * (1 - k) * Mth.sin(u * 1.4F);
			}
		}
		return sum;
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
