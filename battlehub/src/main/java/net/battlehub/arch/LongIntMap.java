package net.battlehub.arch;

import java.util.Arrays;

/** An open-addressing map from long keys to int values (no boxing), for very many blocks. */
public final class LongIntMap {
	public interface Visitor {
		void at(long key, int value);
	}

	private static final long EMPTY = Long.MIN_VALUE;

	private long[] keys;
	private int[] vals;
	private int size;

	public LongIntMap(int capacity) {
		int cap = Integer.highestOneBit(Math.max(16, capacity) * 2 - 1);
		keys = new long[cap];
		vals = new int[cap];
		Arrays.fill(keys, EMPTY);
	}

	private static int mix(long k) {
		k ^= k >>> 33;
		k *= 0xff51afd7ed558ccdL;
		k ^= k >>> 33;
		return (int) k;
	}

	private int slot(long k) {
		int mask = keys.length - 1;
		int i = mix(k) & mask;
		while (keys[i] != EMPTY && keys[i] != k) {
			i = (i + 1) & mask;
		}
		return i;
	}

	public boolean contains(long k) {
		return keys[slot(k)] != EMPTY;
	}

	/** The value, or {@code missing}. */
	public int get(long k, int missing) {
		int s = slot(k);
		return keys[s] == EMPTY ? missing : vals[s];
	}

	/** Sets the value; true when the key is new. */
	public boolean put(long k, int v) {
		int s = slot(k);
		if (keys[s] != EMPTY) {
			vals[s] = v;
			return false;
		}
		keys[s] = k;
		vals[s] = v;
		size++;
		if (size * 2 > keys.length) grow();
		return true;
	}

	public boolean remove(long k) {
		int mask = keys.length - 1;
		int i = slot(k);
		if (keys[i] == EMPTY) return false;
		keys[i] = EMPTY;
		size--;
		int j = (i + 1) & mask;
		while (keys[j] != EMPTY) {
			long kk = keys[j];
			int vv = vals[j];
			keys[j] = EMPTY;
			int s = slot(kk);
			keys[s] = kk;
			vals[s] = vv;
			j = (j + 1) & mask;
		}
		return true;
	}

	private void grow() {
		long[] ok = keys;
		int[] ov = vals;
		keys = new long[ok.length * 2];
		vals = new int[ok.length * 2];
		Arrays.fill(keys, EMPTY);
		for (int i = 0; i < ok.length; i++) {
			if (ok[i] == EMPTY) continue;
			int s = slot(ok[i]);
			keys[s] = ok[i];
			vals[s] = ov[i];
		}
	}

	public int size() {
		return size;
	}

	public void forEach(Visitor v) {
		long[] k = keys;
		int[] vs = vals;
		for (int i = 0; i < k.length; i++) {
			if (k[i] != EMPTY) v.at(k[i], vs[i]);
		}
	}

	public long[] keys() {
		long[] out = new long[size];
		int n = 0;
		for (long k : keys) {
			if (k != EMPTY) out[n++] = k;
		}
		return out;
	}
}
