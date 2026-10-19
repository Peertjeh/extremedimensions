package net.battlehub.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A player's own arrangement of a kit: which inventory slot (0-35) each kit slot goes to. Stored as a list where
 * entry i is the slot for the kit's item that is normally in slot {@code order.get(i)}. A layout made before the kit
 * got more items (they are added at the end) keeps working: the new items go to their own slot when it is free,
 * otherwise to the first free one. Invalid layouts (doubles, slots out of range, more entries than the kit has
 * items) are ignored and the kit's own slots are used.
 */
public final class KitLayout {
	private KitLayout() {}

	/** Where the item from {@code kitSlot} goes for this player. */
	public static int slotFor(int kitSlot, List<Integer> kitSlots, List<Integer> layout) {
		List<Integer> full = resolve(kitSlots, layout);
		if (full == null) {
			return kitSlot;
		}
		int i = kitSlots.indexOf(kitSlot);
		return i < 0 ? kitSlot : full.get(i);
	}

	public static boolean valid(List<Integer> kitSlots, List<Integer> layout) {
		return resolve(kitSlots, layout) != null;
	}

	/**
	 * The slot of every kit item for this layout, extended for items the kit got since the layout was made; null when
	 * the layout cannot be used.
	 */
	public static List<Integer> resolve(List<Integer> kitSlots, List<Integer> layout) {
		if (layout == null || kitSlots == null || layout.isEmpty() || layout.size() > kitSlots.size()) {
			return null;
		}
		java.util.Set<Integer> used = new java.util.HashSet<>();
		for (Integer s : layout) {
			if (s == null || s < 0 || s > 35 || !used.add(s)) {
				return null;
			}
		}
		List<Integer> out = new ArrayList<>(layout);
		for (int i = layout.size(); i < kitSlots.size(); i++) {
			int slot = kitSlots.get(i);
			if (used.contains(slot)) {
				slot = 0;
				while (used.contains(slot)) slot++;
			}
			used.add(slot);
			out.add(slot);
		}
		return out;
	}

	/** Swaps whatever is in two inventory slots in a layout (an empty slot simply receives the item). */
	public static List<Integer> swap(List<Integer> kitSlots, List<Integer> layout, int a, int b) {
		List<Integer> full = resolve(kitSlots, layout);
		List<Integer> out = full != null ? full : new ArrayList<>(kitSlots);
		for (int i = 0; i < out.size(); i++) {
			if (out.get(i) == a) {
				out.set(i, b);
			} else if (out.get(i) == b) {
				out.set(i, a);
			}
		}
		return out;
	}

	/** The layout as "inventory slot → kit slot" (for drawing the editor). */
	public static Map<Integer, Integer> bySlot(List<Integer> kitSlots, List<Integer> layout) {
		Map<Integer, Integer> m = new java.util.HashMap<>();
		List<Integer> full = resolve(kitSlots, layout);
		List<Integer> l = full != null ? full : kitSlots;
		for (int i = 0; i < kitSlots.size(); i++) {
			m.put(l.get(i), kitSlots.get(i));
		}
		return m;
	}
}
