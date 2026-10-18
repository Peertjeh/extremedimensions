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
 * when it can be avoided. A match that lasts longer than the plan goes on with events it has not had yet, and only
 * after all hundred starts over.
 */
public final class ChaosPlan {
	public static final int FIXED_WITHIN = 15;

	private final Random random;
	private final List<String> order;
	private final Set<String> played = new HashSet<>();
	private final List<String> extra = new ArrayList<>();
	private int index;

	private ChaosPlan(Random random, List<String> order) {
		this.random = random;
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
		return new ChaosPlan(r, order);
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

	/** The next event: the plan first, then events this match has not had, then everything again. */
	public String next() {
		String id;
		if (index < order.size()) {
			id = order.get(index);
		} else {
			if (extra.isEmpty()) refill();
			id = extra.remove(0);
		}
		index++;
		played.add(id);
		return id;
	}

	private void refill() {
		List<String> fresh = new ArrayList<>();
		for (ChaosEvent e : ChaosCatalog.all()) {
			if (!played.contains(e.id())) fresh.add(e.id());
		}
		if (fresh.isEmpty()) {
			played.clear();
			for (ChaosEvent e : ChaosCatalog.all()) fresh.add(e.id());
		}
		Collections.shuffle(fresh, random);
		extra.addAll(fresh);
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
