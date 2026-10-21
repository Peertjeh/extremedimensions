package net.battlehub.core.chaos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The events of one Chaos match, decided at the start: at least {@code randomCount} different events drawn from
 * the pool (rare ones less often), plus the five that are in every match, spread over the first
 * {@link #FIXED_WITHIN} turns so even a short match sees them. Two events of the same kind never follow each other
 * when it can be avoided. A match that lasts longer than the plan has them all again, in the same order, twice as
 * bad ({@link ChaosIntensity}); after twice the plan four times as bad, and so on.
 */
public final class ChaosPlan {
	public static final int FIXED_WITHIN = 15;

	private final List<String> order;
	private final Set<String> played = new HashSet<>();
	private int index;
	private int level;

	/** One turn: which event, and how bad (0 as written, 1 twice as bad, 2 four times, ...). */
	public record Turn(String id, int level) {}

	private ChaosPlan(List<String> order) {
		this.order = order;
	}

	public static ChaosPlan create(long seed, int randomCount) {
		Random r = new Random(seed);
		List<ChaosEvent> pool = new ArrayList<>();
		for (ChaosEvent e : ChaosCatalog.all()) {
			if (!ChaosCatalog.FIXED.contains(e.id())) pool.add(e);
		}
		int count = Math.max(0, Math.min(randomCount, pool.size()));
		List<String> drawn = draw(r, pool, count);
		List<String> fixed = new ArrayList<>(ChaosCatalog.FIXED);
		Collections.shuffle(fixed, r);
		int n = count + fixed.size();
		String[] slots = new String[n];
		boolean[] pinned = new boolean[n];
		// one fixed event in each fifth of the first turns
		int within = Math.min(FIXED_WITHIN, n);
		int window = Math.max(1, within / fixed.size());
		for (int i = 0; i < fixed.size(); i++) {
			int at = Math.min(n - 1, i * window + r.nextInt(window));
			while (pinned[at]) at = (at + 1) % n;
			slots[at] = fixed.get(i);
			pinned[at] = true;
		}
		int d = 0;
		for (int i = 0; i < n; i++) {
			if (slots[i] == null) slots[i] = drawn.get(d++);
		}
		spread(slots, pinned);
		List<String> order = new ArrayList<>(List.of(slots));
		return new ChaosPlan(order);
	}

	/** Weighted draw without putting back: each event's chance follows its weight. */
	static List<String> draw(Random r, List<ChaosEvent> pool, int count) {
		List<double[]> keys = new ArrayList<>();
		for (int i = 0; i < pool.size(); i++) {
			// Efraimidis-Spirakis: the largest u^(1/w) win
			double u = Math.max(1e-12, r.nextDouble());
			keys.add(new double[] {Math.pow(u, 1.0 / Math.max(1, pool.get(i).weight())), i});
		}
		keys.sort((a, b) -> Double.compare(b[0], a[0]));
		List<String> out = new ArrayList<>();
		for (int i = 0; i < count; i++) out.add(pool.get((int) keys.get(i)[1]).id());
		Collections.shuffle(out, r);
		return out;
	}

	/** Swaps events that are not pinned so two of the same kind do not follow each other where possible. */
	private static void spread(String[] slots, boolean[] pinned) {
		for (int i = 1; i < slots.length; i++) {
			if (cat(slots[i]) != cat(slots[i - 1])) continue;
			int fix = pinned[i] ? i - 1 : i;
			if (pinned[fix]) continue;
			for (int j = 0; j < slots.length; j++) {
				if (j == fix || pinned[j]) continue;
				if (fits(slots, j, slots[fix]) && fits(slots, fix, slots[j])) {
					String t = slots[j];
					slots[j] = slots[fix];
					slots[fix] = t;
					break;
				}
			}
		}
	}

	/** Would {@code id} at {@code at} differ in kind from both neighbours (ignoring the spot it comes from)? */
	private static boolean fits(String[] slots, int at, String id) {
		ChaosEvent.Category c = cat(id);
		return (at == 0 || cat(slots[at - 1]) != c || slots[at - 1].equals(id))
				&& (at == slots.length - 1 || cat(slots[at + 1]) != c || slots[at + 1].equals(id));
	}

	private static ChaosEvent.Category cat(String id) {
		return ChaosCatalog.get(id).category();
	}

	/** The next event (see {@link #nextTurn()} for how bad it is). */
	public String next() {
		return nextTurn().id();
	}

	/** The next turn: the plan, then the plan again one level worse each time round. */
	public Turn nextTurn() {
		if (order.isEmpty()) return new Turn(ChaosCatalog.FIXED.get(0), 0);
		String id = order.get(index % order.size());
		level = index / order.size();
		index++;
		played.add(id);
		return new Turn(id, level);
	}

	/** How bad the last event was (0 the first time round). */
	public int level() {
		return level;
	}

	/** How bad the events are now, as a factor (1, 2, 4, ...). */
	public int factor() {
		return ChaosIntensity.factor(level);
	}

	/** The planned events, in order. */
	public List<String> order() {
		return List.copyOf(order);
	}

	public int planned() {
		return order.size();
	}

	/** How many events this match has had. */
	public int count() {
		return index;
	}

	/** Every different event this match has had since the last start-over. */
	public Set<String> played() {
		return new LinkedHashSet<>(played);
	}
}
