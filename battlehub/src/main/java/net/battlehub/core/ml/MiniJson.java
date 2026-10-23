package net.battlehub.core.ml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader for the combat spec and its test files (the core has no library for it): objects become
 * {@link Map}s, arrays {@link List}s, numbers {@link Double}s, plus strings, booleans and null. {@link #write} goes the
 * other way, on one line (the fight logs).
 */
public final class MiniJson {
	private final String s;
	private int i;

	private MiniJson(String s) {
		this.s = s;
	}

	public static Object parse(String text) {
		MiniJson p = new MiniJson(text);
		Object v = p.value();
		p.ws();
		if (p.i != p.s.length()) throw p.error("trailing text");
		return v;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> object(Object o) {
		return (Map<String, Object>) o;
	}

	@SuppressWarnings("unchecked")
	public static List<Object> array(Object o) {
		return (List<Object>) o;
	}

	public static double[] doubles(Object o) {
		List<Object> l = array(o);
		double[] out = new double[l.size()];
		for (int k = 0; k < out.length; k++) out[k] = ((Number) l.get(k)).doubleValue();
		return out;
	}

	private Object value() {
		ws();
		if (i >= s.length()) throw error("unexpected end");
		char c = s.charAt(i);
		switch (c) {
			case '{': return obj();
			case '[': return arr();
			case '"': return str();
			case 't': return word("true", Boolean.TRUE);
			case 'f': return word("false", Boolean.FALSE);
			case 'n': return word("null", null);
			default: return num();
		}
	}

	private Map<String, Object> obj() {
		Map<String, Object> m = new LinkedHashMap<>();
		i++;
		ws();
		if (peek() == '}') {
			i++;
			return m;
		}
		while (true) {
			ws();
			String k = str();
			ws();
			expect(':');
			m.put(k, value());
			ws();
			if (peek() == ',') {
				i++;
				continue;
			}
			expect('}');
			return m;
		}
	}

	private List<Object> arr() {
		List<Object> l = new ArrayList<>();
		i++;
		ws();
		if (peek() == ']') {
			i++;
			return l;
		}
		while (true) {
			l.add(value());
			ws();
			if (peek() == ',') {
				i++;
				continue;
			}
			expect(']');
			return l;
		}
	}

	private String str() {
		expect('"');
		StringBuilder b = new StringBuilder();
		while (true) {
			if (i >= s.length()) throw error("unterminated string");
			char c = s.charAt(i++);
			if (c == '"') return b.toString();
			if (c != '\\') {
				b.append(c);
				continue;
			}
			char e = s.charAt(i++);
			switch (e) {
				case 'n' -> b.append('\n');
				case 't' -> b.append('\t');
				case 'r' -> b.append('\r');
				case 'b' -> b.append('\b');
				case 'f' -> b.append('\f');
				case 'u' -> {
					b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
					i += 4;
				}
				default -> b.append(e);
			}
		}
	}

	private Double num() {
		int start = i;
		while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
		if (start == i) throw error("unexpected character");
		return Double.parseDouble(s.substring(start, i));
	}

	private Object word(String w, Object v) {
		if (!s.startsWith(w, i)) throw error("expected " + w);
		i += w.length();
		return v;
	}

	private void ws() {
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
	}

	private char peek() {
		return i < s.length() ? s.charAt(i) : '\0';
	}

	private void expect(char c) {
		if (peek() != c) throw error("expected '" + c + "'");
		i++;
	}

	private IllegalArgumentException error(String what) {
		return new IllegalArgumentException("JSON: " + what + " at " + i);
	}

	/** One line of JSON: maps, lists, double and int arrays, strings, numbers (whole ones without ".0"), booleans, null. */
	public static String write(Object o) {
		StringBuilder sb = new StringBuilder();
		write(sb, o);
		return sb.toString();
	}

	private static void write(StringBuilder sb, Object o) {
		if (o == null) {
			sb.append("null");
		} else if (o instanceof Map<?, ?> m) {
			sb.append('{');
			boolean first = true;
			for (Map.Entry<?, ?> e : m.entrySet()) {
				if (!first) sb.append(',');
				first = false;
				string(sb, String.valueOf(e.getKey()));
				sb.append(':');
				write(sb, e.getValue());
			}
			sb.append('}');
		} else if (o instanceof List<?> l) {
			sb.append('[');
			for (int k = 0; k < l.size(); k++) {
				if (k > 0) sb.append(',');
				write(sb, l.get(k));
			}
			sb.append(']');
		} else if (o instanceof double[] a) {
			sb.append('[');
			for (int k = 0; k < a.length; k++) {
				if (k > 0) sb.append(',');
				number(sb, a[k]);
			}
			sb.append(']');
		} else if (o instanceof int[] a) {
			sb.append('[');
			for (int k = 0; k < a.length; k++) {
				if (k > 0) sb.append(',');
				sb.append(a[k]);
			}
			sb.append(']');
		} else if (o instanceof String str) {
			string(sb, str);
		} else if (o instanceof Number n) {
			number(sb, n.doubleValue());
		} else if (o instanceof Boolean b) {
			sb.append(b);
		} else {
			string(sb, o.toString());
		}
	}

	private static void number(StringBuilder sb, double v) {
		if (Double.isNaN(v) || Double.isInfinite(v)) {
			sb.append("null");
		} else if (v == Math.rint(v) && Math.abs(v) < 1e15) {
			sb.append((long) v);
		} else {
			sb.append(v);
		}
	}

	private static void string(StringBuilder sb, String s) {
		sb.append('"');
		for (int k = 0; k < s.length(); k++) {
			char c = s.charAt(k);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
					else sb.append(c);
				}
			}
		}
		sb.append('"');
	}
}
