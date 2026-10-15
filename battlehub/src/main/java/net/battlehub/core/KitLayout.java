package net.battlehub.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A player's own arrangement of a kit: which inventory slot (0-35) each kit slot goes to. Stored as a list where
 * entry i is the slot for the kit's item that is normally in slot {@code order.get(i)}. Invalid layouts (doubles,
 * slots out of range, a different number of items) are ignored and the kit's own slots are used.
 */
public final class KitLayout {
	private KitLayout() {}

	/** Where the item from {@code kitSlot} goes for this player. */
	public static int slotFor(int kitSlot, List<Integer> kitSlots, List<Integer> layout) {
		if (!valid(kitSlots, layout)) {
			return kitSlot;
		}
		int i = kitSlots.indexOf(kitSlot);
		return i < 0 ? kitSlot : layout.get(i);
	}

	public static boolean valid(List<Integer> kitSlots, List<Integer> layout) {
		if (layout == null || kitSlots == null || layout.size() != kitSlots.size()) {
			return false;
		}
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		for (Integer s : layout) {
			if (s == null || s < 0 || s > 35 || !seen.add(s)) {
				return false;
			}
		}
		return true;
	}

	/** Swaps whatever is in two inventory slots in a layout (an empty slot simply receives the item). */
	public static List<Integer> swap(List<Integer> kitSlots, List<Integer> layout, int a, int b) {
		List<Integer> out = valid(kitSlots, layout) ? new ArrayList<>(layout) : new ArrayList<>(kitSlots);
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
		List<Integer> l = valid(kitSlots, layout) ? layout : kitSlots;
		for (int i = 0; i < kitSlots.size(); i++) {
			m.put(l.get(i), kitSlots.get(i));
		}
		return m;
	}
}
