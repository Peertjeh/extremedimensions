package net.battlehub.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The shapes of the auras worn in the lobby, as points around a player's feet. Each call draws one frame:
 * {@code tick} moves the animation on, {@code yaw} is the way the body faces (Minecraft degrees: 0 is south).
 * A point marked {@code accent} is drawn with the shape's own particle (the cloud of a rain cloud), the others
 * with the cosmetic's particle.
 */
public final class AuraShape {
	private AuraShape() {}

	public record Point(double x, double y, double z, boolean accent) {}

	public static final List<String> SHAPES = List.of("halo", "crown", "wings", "orbit", "helix", "cloud", "spiral",
			"galaxy", "heart", "ring", "tornado", "cape");

	/** The effect text of an aura is "shape:particle", for example "wings:dust:#FFFFFF" or "halo:end_rod". */
	public static String shapeOf(String effect) {
		if (effect == null) return "";
		int cut = effect.indexOf(':');
		return cut < 0 ? effect.trim() : effect.substring(0, cut).trim();
	}

	public static String particleOf(String effect) {
		if (effect == null) return "";
		int cut = effect.indexOf(':');
		return cut < 0 ? "" : effect.substring(cut + 1).trim();
	}

	/** One frame of a shape; empty for an unknown shape. */
	public static List<Point> points(String shape, long tick, float yaw) {
		List<Point> out = new ArrayList<>();
		Frame f = new Frame(yaw, out);
		double t = tick;
		switch (shape) {
			case "halo" -> {
				for (int i = 0; i < 12; i++) {
					double a = t * 0.08 + i * Math.PI * 2 / 12;
					f.local(Math.cos(a) * 0.33, 2.2, Math.sin(a) * 0.33, false);
				}
			}
			case "crown" -> {
				for (int i = 0; i < 10; i++) {
					double a = t * 0.05 + i * Math.PI * 2 / 10;
					f.local(Math.cos(a) * 0.3, 2.08, Math.sin(a) * 0.3, false);
					if (i % 2 == 0) {
						f.local(Math.cos(a) * 0.28, 2.3, Math.sin(a) * 0.28, false);
					}
				}
			}
			case "wings" -> wings(f, t);
			case "orbit" -> {
				for (int k = 0; k < 3; k++) {
					double a = t * 0.22 + k * Math.PI * 2 / 3;
					f.local(Math.cos(a) * 0.85, 1.0 + 0.25 * Math.sin(t * 0.1 + k * 2), Math.sin(a) * 0.85, false);
				}
			}
			case "helix" -> {
				for (int i = 0; i < 9; i++) {
					double h = i * 0.25;
					double a = t * 0.18 + h * 2.6;
					f.local(Math.cos(a) * 0.55, h, Math.sin(a) * 0.55, false);
					f.local(Math.cos(a + Math.PI) * 0.55, h, Math.sin(a + Math.PI) * 0.55, false);
				}
			}
			case "cloud" -> {
				double[][] puffs = {{0, 0}, {0.3, 0.1}, {-0.3, -0.05}, {0.1, 0.3}, {-0.1, -0.3}, {0.25, -0.25}, {-0.25, 0.25}};
				for (double[] p : puffs) {
					f.local(p[0], 2.7 + 0.05 * Math.sin(t * 0.2 + p[0] * 5), p[1], true);
				}
				// Two drops a frame, at places that change every frame
				for (int k = 0; k < 2; k++) {
					double a = (t * 1.7 + k * 2.3) % (Math.PI * 2);
					double r = 0.1 + ((t * 0.37 + k * 0.53) % 1.0) * 0.25;
					f.local(Math.cos(a) * r, 2.55, Math.sin(a) * r, false);
				}
			}
			case "spiral" -> {
				for (int k = 0; k < 3; k++) {
					double h = (t * 0.07 + k * 0.73) % 2.2;
					double a = t * 0.3 + k * Math.PI * 2 / 3;
					f.local(Math.cos(a) * 0.65, h, Math.sin(a) * 0.65, false);
				}
			}
			case "galaxy" -> {
				for (int arm = 0; arm < 2; arm++) {
					for (int i = 1; i <= 7; i++) {
						double r = i * 0.14;
						double a = t * 0.06 + arm * Math.PI + r * 2.4;
						f.local(Math.cos(a) * r, 0.08, Math.sin(a) * r, false);
					}
				}
			}
			case "heart" -> {
				double pulse = 1.0 + 0.12 * Math.sin(t * 0.3);
				for (int i = 0; i < 16; i++) {
					double a = i * Math.PI * 2 / 16;
					double hx = 16 * Math.pow(Math.sin(a), 3);
					double hy = 13 * Math.cos(a) - 5 * Math.cos(2 * a) - 2 * Math.cos(3 * a) - Math.cos(4 * a);
					f.local(hx * 0.022 * pulse, 1.35 + hy * 0.022 * pulse, -0.45, false);
				}
			}
			case "ring" -> {
				for (int i = 0; i < 14; i++) {
					double a = -t * 0.1 + i * Math.PI * 2 / 14;
					f.local(Math.cos(a) * 0.75, 0.1, Math.sin(a) * 0.75, false);
				}
			}
			case "tornado" -> {
				for (int i = 0; i < 10; i++) {
					double h = i * 0.24;
					double r = 0.2 + h * 0.3;
					double a = t * 0.35 + i * 0.9;
					f.local(Math.cos(a) * r, h, Math.sin(a) * r, false);
				}
			}
			case "cape" -> {
				double sway = 0.06 * Math.sin(t * 0.15);
				for (int row = 0; row < 6; row++) {
					double y = 1.4 - row * 0.2;
					double back = -0.25 - row * 0.05 - sway * row;
					for (int col = -2; col <= 2; col++) {
						f.local(col * 0.11, y, back, false);
					}
				}
			}
			default -> { }
		}
		return out;
	}

	/** Angel wings on the back, flapping slowly: a curved top edge and three feathers hanging from it. */
	private static void wings(Frame f, double t) {
		double flap = Math.toRadians(20 + 18 * Math.sin(t * 0.18));
		for (int side = -1; side <= 1; side += 2) {
			for (int i = 0; i <= 8; i++) {
				double u = i / 8.0;
				wingPoint(f, side, flap, 0.12 + u * 0.95, 1.55 + 0.45 * Math.sin(Math.PI * u * 0.85));
			}
			for (int k = 1; k <= 3; k++) {
				double u = k / 3.6;
				double topY = 1.55 + 0.45 * Math.sin(Math.PI * u * 0.85);
				for (int j = 1; j <= 3; j++) {
					wingPoint(f, side, flap, 0.12 + u * 0.95 - j * 0.04, topY - j * 0.17);
				}
			}
		}
	}

	/** {@code out}: distance from the spine along the wing; the wing turns back by {@code flap}. */
	private static void wingPoint(Frame f, int side, double flap, double out, double y) {
		f.local(side * out * Math.cos(flap), y, -0.3 - out * Math.sin(flap), false);
	}

	/** Turns points given as (right, up, forward) of the body into world offsets. */
	private static final class Frame {
		private final double fx;
		private final double fz;
		private final double rx;
		private final double rz;
		private final List<Point> out;

		Frame(float yaw, List<Point> out) {
			double r = Math.toRadians(yaw);
			this.fx = -Math.sin(r);
			this.fz = Math.cos(r);
			this.rx = -Math.cos(r);
			this.rz = -Math.sin(r);
			this.out = out;
		}

		void local(double right, double up, double forward, boolean accent) {
			out.add(new Point(rx * right + fx * forward, up, rz * right + fz * forward, accent));
		}
	}
}
