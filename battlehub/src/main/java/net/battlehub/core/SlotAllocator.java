package net.battlehub.core;

/** Hands out arena slots (fixed regions in the arena world). A slot is free again only once it was cleaned. */
public final class SlotAllocator {
	private final boolean[] used;

	public SlotAllocator(int slots) {
		used = new boolean[Math.max(1, slots)];
	}

	/** Lowest free slot, or -1 when every slot is busy. */
	public int acquire() {
		for (int i = 0; i < used.length; i++) {
			if (!used[i]) {
				used[i] = true;
				return i;
			}
		}
		return -1;
	}

	public void release(int slot) {
		if (slot >= 0 && slot < used.length) {
			used[slot] = false;
		}
	}

	public boolean inUse(int slot) {
		return slot >= 0 && slot < used.length && used[slot];
	}

	public int busy() {
		int n = 0;
		for (boolean b : used) {
			if (b) n++;
		}
		return n;
	}

	public int capacity() {
		return used.length;
	}
}
