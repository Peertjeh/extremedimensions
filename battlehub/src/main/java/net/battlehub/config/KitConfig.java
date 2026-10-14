package net.battlehub.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code config/battlehub/kits.json}: the fixed loadout per mode. */
public final class KitConfig {
	public Map<String, Kit> kits = defaults();

	public static final class Kit {
		public List<KitItem> items = new ArrayList<>();
		/** Potion effects for the whole match, e.g. {"minecraft:resistance": 4}. */
		public Map<String, Integer> effects = new LinkedHashMap<>();

		Kit add(KitItem item) {
			items.add(item);
			return this;
		}

		Kit effect(String id, int amplifier) {
			effects.put(id, amplifier);
			return this;
		}
	}

	public static final class KitItem {
		public String item = "minecraft:air";
		public int count = 1;
		/** Hotbar/inventory index 0-35, or head, chest, legs, feet, offhand. */
		public String slot = "0";
		public Map<String, Integer> enchantments = new LinkedHashMap<>();
		public boolean unbreakable = true;
		/** Leather armour colour as 0xRRGGBB, -1 for none (CTF recolours armour per team). */
		public int color = -1;

		public KitItem() {}

		KitItem(String item, int count, String slot) {
			this.item = item;
			this.count = count;
			this.slot = slot;
		}

		KitItem ench(String id, int level) {
			enchantments.put(id, level);
			return this;
		}

		KitItem breakable() {
			unbreakable = false;
			return this;
		}
	}

	private static KitItem i(String item, int count, String slot) {
		return new KitItem("minecraft:" + item, count, slot);
	}

	private static Kit armor(Kit k, String material) {
		return k.add(i(material + "_helmet", 1, "head")).add(i(material + "_chestplate", 1, "chest"))
				.add(i(material + "_leggings", 1, "legs")).add(i(material + "_boots", 1, "feet"));
	}

	static Map<String, Kit> defaults() {
		Map<String, Kit> m = new LinkedHashMap<>();
		m.put("sword", armor(new Kit().add(i("diamond_sword", 1, "0")).add(i("golden_apple", 3, "1")), "iron"));
		m.put("axe", armor(new Kit().add(i("diamond_axe", 1, "0")).add(i("shield", 1, "offhand")), "iron"));
		m.put("mace", armor(new Kit().add(i("mace", 1, "0")).add(i("wind_charge", 16, "1")).add(i("golden_apple", 2, "2")), "diamond"));
		m.put("bow", armor(new Kit().add(i("bow", 1, "0").ench("minecraft:power", 1)).add(i("iron_sword", 1, "1"))
				.add(i("arrow", 32, "8")), "chainmail"));
		m.put("crossbow", armor(new Kit().add(i("crossbow", 1, "0").ench("minecraft:quick_charge", 1)).add(i("iron_sword", 1, "1"))
				.add(i("arrow", 32, "8")), "iron"));
		m.put("bow_spleef", new Kit().add(i("bow", 1, "0").ench("minecraft:infinity", 1).ench("minecraft:punch", 1)).add(i("arrow", 1, "9"))
				.effect("minecraft:resistance", 4).effect("minecraft:saturation", 0));
		m.put("tnt_run", new Kit().effect("minecraft:speed", 0).effect("minecraft:saturation", 0));
		m.put("sumo", new Kit().effect("minecraft:resistance", 4).effect("minecraft:saturation", 0));
		m.put("koth", armor(new Kit().add(i("stone_sword", 1, "0")), "chainmail").effect("minecraft:saturation", 0));
		m.put("ctf", armor(new Kit().add(i("iron_sword", 1, "0")).add(i("bow", 1, "1")).add(i("arrow", 16, "8"))
				.add(i("golden_apple", 1, "2")), "leather").effect("minecraft:saturation", 0));
		m.put("parkour", new Kit().effect("minecraft:saturation", 0));
		m.put("lms", armor(new Kit().add(i("iron_sword", 1, "0")).add(i("bow", 1, "1")).add(i("arrow", 16, "8"))
				.add(i("golden_apple", 2, "2")).add(i("cooked_beef", 8, "3")), "iron"));
		m.put("ffa", armor(new Kit().add(i("diamond_sword", 1, "0")).add(i("bow", 1, "1").ench("minecraft:power", 1))
				.add(i("arrow", 16, "8")).add(i("golden_apple", 1, "2")), "iron").effect("minecraft:saturation", 0));
		// Bed Wars: the start kit (the shop sells the rest; leather is dyed in the team colour)
		m.put("bedwars", armor(new Kit().add(i("wooden_sword", 1, "0")), "leather").effect("minecraft:saturation", 0));
		// Prop Hunt: hunters (arrows are ammo, handed out by the game); props get their abilities from the game
		m.put("prophunt_hunter", armor(new Kit().add(i("iron_sword", 1, "0")).add(i("bow", 1, "1")), "chainmail")
				.effect("minecraft:saturation", 0));
		m.put("prophunt_prop", new Kit().effect("minecraft:saturation", 0));
		return m;
	}

	public KitConfig normalize() {
		if (kits == null) kits = new LinkedHashMap<>();
		defaults().forEach(kits::putIfAbsent);
		for (Kit k : kits.values()) {
			if (k.items == null) k.items = new ArrayList<>();
			if (k.effects == null) k.effects = new LinkedHashMap<>();
			for (KitItem it : k.items) {
				if (it.enchantments == null) it.enchantments = new LinkedHashMap<>();
				if (it.slot == null) it.slot = "0";
			}
		}
		return this;
	}
}
