package net.battlehub.arch.bldg;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.arch.Dir;
import net.battlehub.arch.Rng;
import net.battlehub.arch.Xform;
import net.battlehub.arch.geom.Footprint;
import net.battlehub.arch.mod.Ctx;
import net.battlehub.arch.mod.Module;
import net.battlehub.arch.mod.Spec;

/**
 * Composes a straight wall run into a facade with a rhythm: corners, piers and bays, laid out symmetrically from
 * the middle, one window type per storey for the whole run (repetition reads as architecture), the entrance in the
 * central bay of the main front, and small variations per bay drawn from the decoration stream (glass schemes,
 * shutters), which never change the bay grid.
 */
public final class Facade {
	private Facade() {}

	/** One straight side of a volume, drawn in a frame whose local +z points out of the building. */
	public record Run(Xform frame, int length, Dir outward) {}

	/** What a facade is made of; filled in by the assembler from style, role and brief. */
	public static final class Rules {
		public int bay = 5;
		public String pier = null;
		public int pierWidth = 1;
		public int pierDepth = 1;
		public boolean cornerPiers;
		public boolean pinnacles;
		/** Window module per storey (the last one repeats); null for none. */
		public String[] windows = {"window.rect"};
		public String door;
		public int doorWidth = 3;
		public boolean entrance;
		public int plinth = 1;
		public boolean bands = true;
		public boolean cornice = true;
		public boolean quoins = true;
		public boolean timberUpper;
		/** Storey floors relative to the run's y = 0, and the height of a storey. */
		public int[] floors = {0};
		public int storey = 5;
		/** Where the piers stop (default: the wall height). */
		public int pierTop = -1;
	}

	/** The four runs of a rectangle whose walls start at height y (the frames' y = 0). */
	public static List<Run> runs(Footprint fp, int y) {
		List<Run> out = new ArrayList<>();
		int x0 = fp.minX(), x1 = fp.maxX(), z0 = fp.minZ(), z1 = fp.maxZ();
		out.add(new Run(Xform.facing(Dir.SOUTH, x0, y, z1), x1 - x0 + 1, Dir.SOUTH));
		out.add(new Run(Xform.facing(Dir.EAST, x1, y, z1), z1 - z0 + 1, Dir.EAST));
		out.add(new Run(Xform.facing(Dir.NORTH, x1, y, z0), x1 - x0 + 1, Dir.NORTH));
		out.add(new Run(Xform.facing(Dir.WEST, x0, y, z0), z1 - z0 + 1, Dir.WEST));
		return out;
	}

	/** Bay layout: the start and width of every bay (corner width cw, piers pw between bays). */
	public static int[][] layout(int length, int bay, int cw, int pw) {
		int usable = length - 2 * cw;
		int n = Math.max(1, (usable + pw) / (bay + pw));
		int[] widths = new int[n];
		java.util.Arrays.fill(widths, bay);
		int left = usable - n * bay - (n - 1) * pw;
		if (left < 0) {
			// too short for even one full bay: one bay over everything
			return new int[][] {{cw, Math.max(1, usable)}};
		}
		// Widen bays in symmetric pairs from the middle outwards
		int i = 0;
		while (left >= 2 && n >= 2) {
			int a = (n - 1) / 2 - (i % ((n + 1) / 2));
			int b = n - 1 - a;
			if (a == b) {
				widths[a]++;
				left--;
			} else {
				widths[a]++;
				widths[b]++;
				left -= 2;
			}
			i++;
		}
		if (left >= 1 && n % 2 == 1) {
			widths[n / 2] += left;
			left = 0;
		}
		int extraCorner = left;
		int[][] out = new int[n][2];
		int x = cw + extraCorner / 2;
		for (int k = 0; k < n; k++) {
			out[k][0] = x;
			out[k][1] = widths[k];
			x += widths[k] + pw;
		}
		return out;
	}

	/** Draws one run: wall body, piers, windows per storey and bay, the entrance. */
	public static void compose(Building b, Volume v, Run run, Rules r, Rng rng, Rng decor) {
		Ctx c = new Ctx(b, run.frame(), rng, decor);
		int len = run.length();
		int h = v.top - v.y0 + 1;
		int d = v.wall;
		int[] bands = new int[Math.max(0, r.floors.length - 1)];
		for (int i = 1; i < r.floors.length; i++) bands[i - 1] = r.floors[i];
		if (r.timberUpper && r.floors.length > 1) {
			int split = r.floors[1];
			c.place("wall.body", Xform.ID, Spec.of(len, split, d).with("plinth", r.plinth).with("quoins", r.quoins));
			c.place("wall.timber", Xform.at(0, split, 0), Spec.of(len, h - split, d).with("span", Math.max(4, r.bay)));
		} else {
			c.place("wall.body", Xform.ID, Spec.of(len, h, d).with("plinth", r.plinth).with("bands", r.bands ? bands : new int[0])
					.with("cornice", r.cornice).with("quoins", r.quoins));
		}
		boolean piers = r.pier != null && !r.pier.equals("pier.none");
		int pw = piers ? r.pierWidth : 1;
		int cw = Math.max(1, piers && r.cornerPiers ? r.pierWidth : 1);
		int[][] bays = layout(len, r.bay, cw, pw);
		int n = bays.length;
		int pierTop = r.pierTop > 0 ? r.pierTop : h;
		Module pierMod = piers ? b.lib.get(r.pier) : null;
		if (pierMod != null) {
			Spec ps = Spec.of(pw, h, r.pierDepth).with("top", pierTop).with("pinnacle", r.pinnacles).with("plinth", r.plinth);
			for (int k = 0; k < n - 1; k++) {
				c.place(pierMod, Xform.at(bays[k][0] + bays[k][1], 0, 0), ps);
			}
			if (r.cornerPiers) {
				c.place(pierMod, Xform.at(0, 0, 0), ps);
				c.place(pierMod, Xform.at(len - pw, 0, 0), ps);
			}
		}
		int doorBay = r.entrance ? n / 2 : -1;
		// One glass scheme per run, alternating A/B from the middle out (structured variation)
		int scheme = decor.nextInt(3);
		for (int k = 0; k < n; k++) {
			int bx = bays[k][0], bw = bays[k][1];
			for (int s = 0; s < r.floors.length; s++) {
				int fy = r.floors[s];
				int sh = (s + 1 < r.floors.length ? r.floors[s + 1] : h) - fy;
				if (k == doorBay && s == 0 && r.door != null) {
					Module dm = b.lib.get(r.door);
					int dh = Math.min(h, Math.max(sh, dm != null && dm.id().equals("door.portal") ? h : sh));
					// doors stand on the floor: their frame starts one above the floor row
					c.place(dm, Xform.at(bx, fy + 1, 0), Spec.of(bw, dh - 1, d));
					continue;
				}
				if (k == doorBay && r.door != null && "door.portal".equals(r.door)) continue;
				String wid = r.windows == null || r.windows.length == 0 ? null : r.windows[Math.min(s, r.windows.length - 1)];
				if (wid == null) continue;
				Module wm = b.lib.get(wid);
				if (wm == null) continue;
				Spec ws = Spec.of(bw, sh, d).with("glass", (scheme + Math.abs(k - n / 2)) % 2 * 2);
				if (!wm.fits(ws)) continue;
				c.place(wm, Xform.at(bx, fy, 0), ws);
			}
		}
	}
}
