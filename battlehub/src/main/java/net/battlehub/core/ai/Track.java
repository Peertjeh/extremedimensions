package net.battlehub.core.ai;

/**
 * What an NPC remembers about one other player's movement: the last places it actually saw them, in a small ring
 * buffer. From that it estimates their speed, predicts where they will be a few ticks ahead, and knows how sure it
 * still is when it has not seen them for a while (the certainty fades).
 */
public final class Track {
	private static final int SIZE = 40;

	private final int[] ticks = new int[SIZE];
	private final double[] xs = new double[SIZE];
	private final double[] ys = new double[SIZE];
	private final double[] zs = new double[SIZE];
	private int count;
	private int head;

	/** A sighting at this tick (only call when the player is actually seen). */
	public void observe(int tick, V3 pos) {
		if (count > 0 && ticks[(head - 1 + SIZE) % SIZE] == tick) return;
		ticks[head] = tick;
		xs[head] = pos.x();
		ys[head] = pos.y();
		zs[head] = pos.z();
		head = (head + 1) % SIZE;
		count = Math.min(SIZE, count + 1);
	}

	public boolean empty() {
		return count == 0;
	}

	public int size() {
		return count;
	}

	private int index(int back) {
		return (head - 1 - back + SIZE * 2) % SIZE;
	}

	public int lastSeen() {
		return count == 0 ? Integer.MIN_VALUE : ticks[index(0)];
	}

	public V3 last() {
		if (count == 0) return null;
		int i = index(0);
		return new V3(xs[i], ys[i], zs[i]);
	}

	/** Blocks per tick, averaged over the last few sightings (zero with fewer than two). */
	public V3 velocity() {
		if (count < 2) return V3.ZERO;
		int n = Math.min(6, count - 1);
		int a = index(0), b = index(n);
		int dt = ticks[a] - ticks[b];
		if (dt <= 0) return V3.ZERO;
		return new V3((xs[a] - xs[b]) / dt, (ys[a] - ys[b]) / dt, (zs[a] - zs[b]) / dt);
	}

	/**
	 * Where they will probably be {@code ahead} ticks after {@code now}: the last sighting moved on with the
	 * estimated speed. Only the horizontal part is carried far; up and down is held short (jumps end).
	 */
	public V3 predict(int now, int ahead) {
		V3 p = last();
		if (p == null) return null;
		int dt = Math.max(0, now - lastSeen()) + Math.max(0, ahead);
		dt = Math.min(dt, 20);
		V3 v = velocity();
		return new V3(p.x() + v.x() * dt, p.y() + v.y() * Math.min(dt, 3), p.z() + v.z() * dt);
	}

	/** How sure the NPC still is of where they are: 1 when seen just now, halving every {@code halfLife} ticks. */
	public double confidence(int now, int halfLife) {
		if (count == 0) return 0;
		int age = Math.max(0, now - lastSeen());
		return Math.pow(0.5, age / (double) Math.max(1, halfLife));
	}

	/**
	 * How the sideways motion (seen from {@code observer}) changed direction over the last {@code window} ticks:
	 * returns the number of direction changes. A player who keeps changing strafe direction is harder to predict.
	 */
	public int strafeChanges(V3 observer, int window) {
		if (count < 3) return 0;
		int changes = 0;
		int lastSign = 0;
		int newest = ticks[index(0)];
		for (int k = Math.min(count - 2, SIZE - 2); k >= 0; k--) {
			int a = index(k), b = index(k + 1);
			if (newest - ticks[b] > window) continue;
			V3 to = new V3(xs[a] - observer.x(), 0, zs[a] - observer.z()).unit();
			V3 side = to.left();
			double lateral = (xs[a] - xs[b]) * side.x() + (zs[a] - zs[b]) * side.z();
			int sign = lateral > 0.02 ? 1 : lateral < -0.02 ? -1 : 0;
			if (sign != 0) {
				if (lastSign != 0 && sign != lastSign) changes++;
				lastSign = sign;
			}
		}
		return changes;
	}

	public void clear() {
		count = 0;
		head = 0;
	}
}
