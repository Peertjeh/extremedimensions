package net.battlehub.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A weighted loot table (SkyWars chests): each roll picks one entry by weight and a count between min and max. */
public final class Loot {
	private Loot() {}

	public record Entry(String item, int min, int max, int weight) {
		public static Entry parse(String text) {
			// "minecraft:iron_sword 1-1 x5" or "minecraft:cobblestone 16-32" (weight 1)
			String[] parts = text.trim().split("\\s+");
			String item = parts[0];
			int min = 1;
			int max = 1;
			int weight = 1;
			for (int i = 1; i < parts.length; i++) {
				String p = parts[i];
				try {
					if (p.startsWith("x")) {
						weight = Integer.parseInt(p.substring(1));
					} else if (p.contains("-")) {
						String[] r = p.split("-");
						min = Integer.parseInt(r[0]);
						max = Integer.parseInt(r[1]);
					} else {
						min = max = Integer.parseInt(p);
					}
				} catch (NumberFormatException ignored) {
					// keep the defaults for a broken part
				}
			}
			return new Entry(item, Math.max(1, Math.min(min, max)), Math.max(1, Math.max(min, max)), Math.max(1, weight));
		}
	}

	public record Pick(String item, int count) {}

	public static List<Pick> roll(List<Entry> table, int rolls, Random rnd) {
		List<Pick> out = new ArrayList<>();
		int total = 0;
		for (Entry e : table) total += e.weight();
		if (total <= 0) {
			return out;
		}
		for (int r = 0; r < rolls; r++) {
			int x = rnd.nextInt(total);
			for (Entry e : table) {
				x -= e.weight();
				if (x < 0) {
					out.add(new Pick(e.item(), e.min() + rnd.nextInt(e.max() - e.min() + 1)));
					break;
				}
			}
		}
		return out;
	}
}
