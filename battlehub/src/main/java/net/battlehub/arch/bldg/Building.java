package net.battlehub.arch.bldg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Easing;
import net.battlehub.arch.Plan;
import net.battlehub.arch.Rng;
import net.battlehub.arch.Slot;
import net.battlehub.arch.Voxels;
import net.battlehub.arch.Xform;
import net.battlehub.arch.mat.Gradient;
import net.battlehub.arch.mat.MaterialContext;
import net.battlehub.arch.mat.Probe;
import net.battlehub.arch.mat.Resolver;
import net.battlehub.arch.mod.Attachment;
import net.battlehub.arch.mod.Library;

/**
 * One building while it is generated: its brief, its plan in building coordinates (the main entrance faces local
 * +z), where it stands in the world ({@link #frame}), the volumes it was planned with, the attachments and focal
 * points its modules registered, and the shared material context. {@link #emit} turns it into finished blocks.
 */
public final class Building {
	/** Height of the ground at a world column (the topmost solid y), or Integer.MIN_VALUE over the void. */
	public interface Ground {
		int at(int wx, int wz);
	}

	public final Brief brief;
	public final Style style;
	public final Library lib;
	public final Plan plan = new Plan(1 << 14);
	public final Xform frame;
	public final int detail;
	public final Rng rng;
	public final List<Volume> volumes = new ArrayList<>();
	private final List<Attachment> attachments = new ArrayList<>();
	private final List<int[]> focals = new ArrayList<>();
	private final Map<String, Integer> used = new TreeMap<>();
	private final Ground ground;
	private MaterialContext mats;
	/** The floor level of the ground storey (building y). */
	public int floorY;

	public Building(Brief brief, Library lib, Xform frame, Ground ground) {
		this.brief = brief;
		this.style = brief.style;
		this.lib = lib;
		this.frame = frame;
		this.detail = brief.detail;
		this.rng = new Rng(brief.seed);
		this.ground = ground;
	}

	public void attach(Attachment a) {
		attachments.add(a);
	}

	public List<Attachment> attachments() {
		return List.copyOf(attachments);
	}

	public List<Attachment> attachments(Attachment.Kind kind) {
		List<Attachment> out = new ArrayList<>();
		for (Attachment a : attachments) {
			if (a.kind() == kind) out.add(a);
		}
		return out;
	}

	public void focal(int x, int y, int z) {
		focals.add(new int[] {x, y, z});
	}

	public List<int[]> focals() {
		return List.copyOf(focals);
	}

	public void used(String moduleId) {
		used.merge(moduleId, 1, Integer::sum);
	}

	/** How often each module was placed. */
	public Map<String, Integer> modulesUsed() {
		return java.util.Collections.unmodifiableMap(used);
	}

	public Volume volume(String id) {
		for (Volume v : volumes) {
			if (v.id.equals(id)) return v;
		}
		return null;
	}

	/** Ground height under a building column, in building y (Integer.MIN_VALUE: none). */
	public int groundAt(int bx, int bz) {
		if (ground == null) return Integer.MIN_VALUE;
		int g = ground.at(frame.x(bx, bz), frame.z(bx, bz));
		return g == Integer.MIN_VALUE ? g : g - frame.oy();
	}

	/**
	 * The shared material context, set up the first time it is asked for: gradients run over the whole building
	 * (bottom to eaves, darker towards the outer edges, lighter near focal points), plus noise and weathering.
	 */
	public MaterialContext materials() {
		if (mats != null) return mats;
		long s = rng.stream("materials").seed();
		mats = new MaterialContext(s, brief.palette);
		int y0 = floorY;
		// The gradient spans where most of the walls are (up to the 85th percentile of wall heights), so a slender
		// tower gets the whole run and a cathedral's nave is not squeezed into the bottom by its spires
		int[] hist = new int[Math.max(1, plan.maxY() - y0 + 1)];
		int[] total = {0};
		plan.forEach((x, y, z, c) -> {
			if ((c.slot() == Slot.WALL || c.slot() == Slot.WALL_ALT || c.slot() == Slot.QUOIN) && !c.is(Cell.INSIDE) && y >= y0 && y - y0 < hist.length) {
				hist[y - y0]++;
				total[0]++;
			}
		});
		int y1 = Math.max(y0 + 6, (int) (plan.maxY() * 0.9));
		if (total[0] > 0) {
			int acc = 0;
			for (int i = 0; i < hist.length; i++) {
				acc += hist[i];
				if (acc >= total[0] * 0.85) {
					y1 = Math.max(y0 + 6, y0 + i);
					break;
				}
			}
		}
		double cx = (plan.minX() + plan.maxX()) / 2.0, cz = (plan.minZ() + plan.maxZ()) / 2.0;
		double hx = (plan.maxX() - plan.minX()) / 2.0, hz = (plan.maxZ() - plan.minZ()) / 2.0;
		Gradient.Stack wall = new Gradient.Stack()
				.add(Gradient.vertical(y0, y1, Easing.LINEAR), 1.0)
				.offset(Gradient.edges(cx, cz, hx, hz, Easing.CUBIC_IN), -0.14)
				.noise(s + 1, 0.06, 8);
		for (int[] f : focals) {
			wall.offset(Gradient.radial(f[0], f[1], f[2], 9, Easing.SMOOTH), 0.18);
		}
		mats.common(wall);
		mats.field(Slot.FOUNDATION, new Gradient.Stack().add(Gradient.vertical(plan.minY(), y0 + 2, Easing.LINEAR), 1).noise(s + 2, 0.1, 4));
		int roofLo = y0 + (int) ((plan.maxY() - y0) * 0.35);
		mats.field(Slot.ROOF, new Gradient.Stack().add(Gradient.vertical(roofLo, plan.maxY(), Easing.SMOOTH), 1).noise(s + 3, 0.1, 6));
		mats.field(Slot.FLOOR, new Gradient.Stack().add(Gradient.noise(s + 4, 5, 2), 1));
		mats.field(Slot.PAVING, new Gradient.Stack().add(Gradient.noise(s + 5, 6, 2), 1));
		mats.field(Slot.STAINED, Gradient.vertical(y0, plan.maxY(), Easing.LINEAR));
		mats.weathering(brief.condition.weathering, y0, 7);
		return mats;
	}

	/** Replaces the field of one role (archetypes that want their own gradient). */
	public void field(Slot slot, Gradient g) {
		materials().field(slot, g);
	}

	/**
	 * Writes the finished blocks into {@code out} in world coordinates. Explicit air only clears what is already
	 * there (terrain), so empty rooms cost nothing.
	 */
	public void emit(Voxels out) {
		MaterialContext mc = materials();
		Probe p = new Probe();
		plan.forEach((x, y, z, c) -> {
			int wx = frame.x(x, z), wy = frame.y(y), wz = frame.z(x, z);
			if (c.air()) {
				if (out.filled(wx, wy, wz)) out.set(wx, wy, wz, "air");
				return;
			}
			Cell wc = c.in(frame);
			p.set(x, y, z, wx, wy, wz, wc);
			out.set(wx, wy, wz, Resolver.resolve(wc, p, mc));
		});
	}

	/** The world box this building covers: {minX, minY, minZ, maxX, maxY, maxZ}. */
	public int[] worldBox() {
		int[] xs = {frame.x(plan.minX(), plan.minZ()), frame.x(plan.maxX(), plan.maxZ())};
		int[] zs = {frame.z(plan.minX(), plan.minZ()), frame.z(plan.maxX(), plan.maxZ())};
		return new int[] {Math.min(xs[0], xs[1]), frame.y(plan.minY()), Math.min(zs[0], zs[1]), Math.max(xs[0], xs[1]), frame.y(plan.maxY()),
				Math.max(zs[0], zs[1])};
	}
}
