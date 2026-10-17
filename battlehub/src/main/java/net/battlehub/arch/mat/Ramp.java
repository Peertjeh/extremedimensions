package net.battlehub.arch.mat;

import java.util.ArrayList;
import java.util.List;

/**
 * An ordered run of materials for one role, from one end of a gradient to the other (usually dark to light), each
 * with a share of the run: a big share makes a dominant material. Where two neighbours meet, a border of width
 * {@link #fray} lets them interlock in coherent patches instead of a hard line.
 */
public final class Ramp {
	public record Entry(Material material, double weight) {}

	private final List<Entry> entries;
	private final double[] edges;
	public final double fray;

	public Ramp(List<Entry> entries, double fray) {
		if (entries.isEmpty()) throw new IllegalArgumentException("empty ramp");
		this.entries = List.copyOf(entries);
		this.fray = fray;
		double total = 0;
		for (Entry e : entries) total += e.weight();
		edges = new double[entries.size() + 1];
		double acc = 0;
		for (int i = 0; i < entries.size(); i++) {
			edges[i] = acc / total;
			acc += entries.get(i).weight();
		}
		edges[entries.size()] = 1;
	}

	/**
	 * A ramp from names in the catalog; "name*3" gives a name three times the usual share. Names the catalog does
	 * not know are skipped (so a missing block never breaks a palette); null when none are left.
	 */
	public static Ramp of(double fray, String... names) {
		List<Entry> list = new ArrayList<>();
		for (String n : names) {
			double w = 1;
			int star = n.indexOf('*');
			if (star > 0) {
				w = Double.parseDouble(n.substring(star + 1));
				n = n.substring(0, star);
			}
			Material m = Catalog.get(n);
			if (m != null) list.add(new Entry(m, w));
		}
		return list.isEmpty() ? null : new Ramp(list, fray);
	}

	public static Ramp of(String... names) {
		return of(0.12, names);
	}

	public int size() {
		return entries.size();
	}

	public Material get(int i) {
		return entries.get(Math.max(0, Math.min(entries.size() - 1, i))).material();
	}

	public List<Entry> entries() {
		return entries;
	}

	/**
	 * The entry for gradient position {@code t} (0..1), with {@code r} (0..1, a coherent per-stone random number)
	 * deciding inside the border between two entries.
	 */
	public int index(double t, double r) {
		int n = entries.size();
		if (n == 1) return 0;
		t = Math.max(0, Math.min(1, t));
		int i = n - 1;
		for (int k = 0; k < n; k++) {
			if (t < edges[k + 1]) {
				i = k;
				break;
			}
		}
		double half = fray / 2;
		if (half <= 0) return i;
		// Near the upper edge: sometimes the next entry; near the lower edge: sometimes the previous one
		double up = edges[i + 1];
		if (i < n - 1 && t > up - half) {
			double p = (t - (up - half)) / fray;
			if (r < p) return i + 1;
		}
		double down = edges[i];
		if (i > 0 && t < down + half) {
			double p = ((down + half) - t) / fray;
			if (r < p) return i - 1;
		}
		return i;
	}

	/** The entry nearest to {@code i} that has a form, or -1. */
	public int nearestWith(int i, net.battlehub.arch.Shape shape) {
		for (int d = 0; d < entries.size(); d++) {
			if (i + d < entries.size() && entries.get(i + d).material().has(shape)) return i + d;
			if (i - d >= 0 && entries.get(i - d).material().has(shape)) return i - d;
		}
		return -1;
	}

	/** True when the materials go from dark to light (within a little slack), as most ramps promise. */
	public boolean ascending(double slack) {
		for (int i = 1; i < entries.size(); i++) {
			if (entries.get(i).material().value() + slack < entries.get(i - 1).material().value()) return false;
		}
		return true;
	}

	public Ramp reversed() {
		List<Entry> r = new ArrayList<>(entries);
		java.util.Collections.reverse(r);
		return new Ramp(r, fray);
	}
}
