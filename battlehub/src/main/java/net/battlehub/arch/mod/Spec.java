package net.battlehub.arch.mod;

import java.util.HashMap;
import java.util.Map;

/**
 * The size a module is asked to fill (width along its front, height, depth into the wall or building) and named
 * options ("arch" → a curve, "storey" → 0, "focal" → true...). Immutable: {@code with} gives a changed copy.
 */
public record Spec(int w, int h, int d, Map<String, Object> opts) {
	public Spec {
		opts = Map.copyOf(opts);
	}

	public static Spec of(int w, int h, int d) {
		return new Spec(w, h, d, Map.of());
	}

	public Spec with(String key, Object value) {
		Map<String, Object> m = new HashMap<>(opts);
		m.put(key, value);
		return new Spec(w, h, d, m);
	}

	public Spec size(int nw, int nh, int nd) {
		return new Spec(nw, nh, nd, opts);
	}

	public int i(String key, int def) {
		Object o = opts.get(key);
		return o instanceof Number n ? n.intValue() : def;
	}

	public double num(String key, double def) {
		Object o = opts.get(key);
		return o instanceof Number n ? n.doubleValue() : def;
	}

	public boolean b(String key) {
		return Boolean.TRUE.equals(opts.get(key));
	}

	public String s(String key, String def) {
		Object o = opts.get(key);
		return o == null ? def : o.toString();
	}

	@SuppressWarnings("unchecked")
	public <T> T get(String key, T def) {
		Object o = opts.get(key);
		return o == null ? def : (T) o;
	}
}
