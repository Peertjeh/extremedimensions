package net.battlehub.core.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Plans a parkour jump from one platform to the next with real player movement ({@link PlayerSim}): it tries a
 * few ways (walk or sprint, a short or a long run-up) and simulates each from the start of the run-up to the
 * landing, then picks the one that lands with the most room to spare. Ways that already failed for this jump are
 * skipped, so a bot that missed tries something else instead of the same impossible jump again. When no way lands,
 * there is no plan: the jump is not possible with player movement.
 */
public final class JumpPlanner {
	private JumpPlanner() {}

	/** A platform top: the block area [minX, maxX) x [minZ, maxZ), the height of its surface and its friction. */
	public record Pad(double minX, double minZ, double maxX, double maxZ, double y, double friction) {
		double cx() {
			return (minX + maxX) / 2;
		}

		double cz() {
			return (minZ + maxZ) / 2;
		}

		/** A player (0.6 wide) standing here touches the pad. */
		boolean supports(double x, double z) {
			return x > minX - 0.3 && x < maxX + 0.3 && z > minZ - 0.3 && z < maxZ + 0.3;
		}

		/** How far inside the pad a point is (negative: outside). */
		double inset(double x, double z) {
			return Math.min(Math.min(x - minX, maxX - x), Math.min(z - minZ, maxZ - z));
		}
	}

	/**
	 * A way to make the jump: where to start the run-up, the take-off point (jump when it is reached), sprint or
	 * not, where it should land, how much room is left there, and a name for remembering failures.
	 */
	public record Plan(V3 start, V3 takeoff, boolean sprint, V3 landing, double margin, String variant, double dirX, double dirZ, int ticks) {}

	/** The ways tried, by name. */
	static final String[] VARIANTS = {"sprint-long", "sprint-short", "walk-long", "walk-short", "sprint-standing"};

	public static Plan plan(Pad from, Pad to, Set<String> failed) {
		// aim at the middle of the target, from the side of the take-off pad facing it
		double tx = to.cx(), tz = to.cz();
		double fx = from.cx(), fz = from.cz();
		double len = Math.hypot(tx - fx, tz - fz);
		if (len < 1e-6) return null;
		double dx = (tx - fx) / len, dz = (tz - fz) / len;
		// along the direction: how far the take-off pad reaches from its middle, and the edge point
		double reachAlong = edgeDistance(from, dx, dz);
		V3 edge = new V3(fx + dx * reachAlong, from.y(), fz + dz * reachAlong);
		Plan best = null;
		List<Plan> all = new ArrayList<>();
		for (String v : VARIANTS) {
			if (failed != null && failed.contains(v)) continue;
			boolean sprint = v.startsWith("sprint");
			double runup = v.endsWith("long") ? Math.max(0.5, 2 * reachAlong - 0.4) : v.endsWith("short") ? Math.min(1.5, 2 * reachAlong - 0.4) : 0;
			// take off just before the edge (the feet may still hang 0.3 over it)
			double off = 0.15;
			V3 takeoff = new V3(edge.x() + dx * off, from.y(), edge.z() + dz * off);
			V3 start = runup <= 0 ? new V3(edge.x() - dx * 0.2, from.y(), edge.z() - dz * 0.2)
					: new V3(edge.x() - dx * runup, from.y(), edge.z() - dz * runup);
			Plan p = simulate(from, to, start, takeoff, sprint, dx, dz, v);
			if (p != null) all.add(p);
		}
		for (Plan p : all) {
			if (best == null || p.margin() > best.margin()) best = p;
		}
		return best;
	}

	/** Distance from the middle of a pad to its edge along (dx, dz). */
	static double edgeDistance(Pad p, double dx, double dz) {
		double hx = (p.maxX() - p.minX()) / 2, hz = (p.maxZ() - p.minZ()) / 2;
		double ax = Math.abs(dx) < 1e-9 ? Double.MAX_VALUE : hx / Math.abs(dx);
		double az = Math.abs(dz) < 1e-9 ? Double.MAX_VALUE : hz / Math.abs(dz);
		return Math.min(ax, az);
	}

	/** Runs the jump: run-up from {@code start}, jump at {@code takeoff}, keep pushing towards the target in the air. */
	static Plan simulate(Pad from, Pad to, V3 start, V3 takeoff, boolean sprint, double dx, double dz, String variant) {
		PlayerSim s = new PlayerSim(start.x(), from.y(), start.z());
		s.friction = from.friction();
		boolean jumped = false;
		double takeoffAlong = (takeoff.x() - start.x()) * dx + (takeoff.z() - start.z()) * dz;
		for (int t = 0; t < 80; t++) {
			double along = (s.x - start.x()) * dx + (s.z - start.z()) * dz;
			// jump on the tick whose step would carry the player past the take-off point (just before the edge)
			double step = (s.vx * dx + s.vz * dz) + (sprint ? PlayerSim.SPRINT : PlayerSim.WALK) * 0.98;
			boolean jump = !jumped && s.onGround && along + step >= takeoffAlong - 1e-6;
			if (jump) jumped = true;
			double prevY = s.y;
			s.tick(dx, dz, sprint, jump);
			if (s.onGround && !from.supports(s.x, s.z)) s.walkOff();
			if (!s.onGround && s.vy < 0) {
				// coming down: onto the target, back onto the take-off pad, or nothing
				if (prevY >= to.y() - 1e-6 && s.y <= to.y() + 1e-6 && to.supports(s.x, s.z)) {
					double m = to.inset(s.x, s.z) + 0.3;
					return new Plan(start, takeoff, sprint, new V3(s.x, to.y(), s.z), Math.min(m, 0.3 + Math.max(0, to.inset(s.x, s.z))), variant, dx, dz, t + 1);
				}
				if (prevY >= from.y() - 1e-6 && s.y <= from.y() + 1e-6 && from.supports(s.x, s.z)) return null;
				if (s.y < Math.min(from.y(), to.y()) - 2) return null;
			}
			// bumping into the side of a higher target: no good
			if (s.y < to.y() - 0.05 && to.supports(s.x, s.z) && to.y() > from.y()) {
				if (s.y < to.y() - 0.6) return null;
			}
		}
		return null;
	}
}
