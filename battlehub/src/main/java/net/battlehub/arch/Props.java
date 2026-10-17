package net.battlehub.arch;

/**
 * Turns the direction properties of a block name like {@code "wall_torch[facing=north]"} with a frame: facing,
 * axis, a banner or skull rotation (0-15), and the north/east/south/west connections.
 */
public final class Props {
	private Props() {}

	public static String turn(String id, Xform f) {
		int open = id.indexOf('[');
		if (open < 0 || !id.endsWith("]")) return id;
		String[] pairs = id.substring(open + 1, id.length() - 1).split(",");
		StringBuilder out = new StringBuilder(id.substring(0, open)).append('[');
		// Connection sides move together: collect them first
		String[] sides = new String[4];
		boolean any = false;
		for (String p : pairs) {
			int eq = p.indexOf('=');
			if (eq < 0) continue;
			String k = p.substring(0, eq).trim();
			String v = p.substring(eq + 1).trim();
			Dir side = Dir.byId(k);
			if (side != null) {
				sides[f.dir(side).ordinal()] = v;
				continue;
			}
			switch (k) {
				case "facing", "horizontal_facing" -> {
					Dir d = Dir.byId(v);
					if (d != null) v = f.dir(d).id();
				}
				case "axis" -> {
					if (f.swapsAxes() && (v.equals("x") || v.equals("z"))) v = v.equals("x") ? "z" : "x";
				}
				case "rotation" -> {
					try {
						int r = Integer.parseInt(v);
						if (f.mirror()) r = (16 - r) & 15;
						v = Integer.toString((r + f.turns() * 4) & 15);
					} catch (NumberFormatException e) {
						// leave it
					}
				}
				case "hinge" -> {
					if (f.mirror()) v = v.equals("left") ? "right" : "left";
				}
				default -> {
				}
			}
			if (any) out.append(',');
			out.append(k).append('=').append(v);
			any = true;
		}
		for (Dir d : Dir.values()) {
			if (sides[d.ordinal()] == null) continue;
			if (any) out.append(',');
			out.append(d.id()).append('=').append(sides[d.ordinal()]);
			any = true;
		}
		return out.append(']').toString();
	}

	/** The name without properties. */
	public static String base(String id) {
		int open = id.indexOf('[');
		return open < 0 ? id : id.substring(0, open);
	}

	/** The value of one property, or null. */
	public static String get(String id, String key) {
		int open = id.indexOf('[');
		if (open < 0) return null;
		for (String p : id.substring(open + 1, id.length() - 1).split(",")) {
			int eq = p.indexOf('=');
			if (eq > 0 && p.substring(0, eq).trim().equals(key)) return p.substring(eq + 1).trim();
		}
		return null;
	}

	/** The name with one property set (added or replaced). */
	public static String with(String id, String key, String value) {
		int open = id.indexOf('[');
		if (open < 0) return id + "[" + key + "=" + value + "]";
		StringBuilder out = new StringBuilder(id.substring(0, open)).append('[');
		boolean done = false;
		boolean any = false;
		for (String p : id.substring(open + 1, id.length() - 1).split(",")) {
			int eq = p.indexOf('=');
			if (eq < 0) continue;
			String k = p.substring(0, eq).trim();
			if (any) out.append(',');
			any = true;
			if (k.equals(key)) {
				out.append(k).append('=').append(value);
				done = true;
			} else {
				out.append(p.trim());
			}
		}
		if (!done) {
			if (any) out.append(',');
			out.append(key).append('=').append(value);
		}
		return out.append(']').toString();
	}
}
