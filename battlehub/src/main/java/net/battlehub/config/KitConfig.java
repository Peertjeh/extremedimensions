package net.battlehub.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code config/battlehub/kits.json}: the fixed loadout per mode. */
public final class KitConfig {
	public Map<String, Kit> kits = defaults();
	/** What the file has been brought up to (2: the mace kit has a sword, 1.46). */
	public int configVersion;

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

	/** Team-coloured clay for The Bridge ("{team}" is filled in per player). */
	static final String CLAY = "{team}_terracotta";

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
		// the sword for the ground, the mace for coming down from a wind charge (the sword is last in the list, so
		// layouts players made before it was there keep working)
		m.put("mace", armor(new Kit().add(i("mace", 1, "1")).add(i("wind_charge", 16, "2")).add(i("golden_apple", 2, "3"))
				.add(i("diamond_sword", 1, "0")), "diamond"));
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
		// The Bridge: clay in the team colour ({team} becomes red, blue, lime or yellow), part of the kit so the kit
		// editor can place it too
		m.put("bridge", armor(new Kit().add(i("iron_sword", 1, "0")).add(i("bow", 1, "1")).add(i("diamond_pickaxe", 1, "2")
				.ench("minecraft:efficiency", 2)).add(i("golden_apple", 8, "5")).add(i("arrow", 1, "8"))
				.add(i(CLAY, 64, "3")).add(i(CLAY, 64, "4")), "leather"));
		m.put("boxing", new Kit().add(i("diamond_sword", 1, "0").ench("minecraft:sharpness", 1)).effect("minecraft:speed", 1)
				.effect("minecraft:resistance", 4).effect("minecraft:saturation", 0));
		m.put("build_uhc", armor(new Kit().add(i("diamond_sword", 1, "0").ench("minecraft:sharpness", 2))
				.add(i("fishing_rod", 1, "1")).add(i("bow", 1, "2").ench("minecraft:power", 2))
				.add(i("golden_apple", 6, "3")).add(i("lava_bucket", 1, "4")).add(i("water_bucket", 1, "5"))
				.add(i("cobblestone", 64, "6")).add(i("oak_planks", 64, "7")).add(i("diamond_axe", 1, "8"))
				.add(i("arrow", 32, "9")).add(i("diamond_pickaxe", 1, "10")).add(i("lava_bucket", 1, "11"))
				.add(i("water_bucket", 1, "12")).add(i("cooked_beef", 16, "13")), "diamond"));
		m.put("spleef", new Kit().add(i("diamond_shovel", 1, "0").ench("minecraft:efficiency", 5)).effect("minecraft:saturation", 0));
		m.put("skywars", new Kit());
		m.put("hunger_games", new Kit());
		m.put("murder_mystery", new Kit().effect("minecraft:saturation", 0));
		return m;
	}

	public KitConfig normalize() {
		if (kits == null) kits = new LinkedHashMap<>();
		defaults().forEach(kits::putIfAbsent);
		// kits.json from before 1.28: The Bridge handed its clay out apart from the kit; now it is part of it (added
		// at the end, so layouts players made earlier keep working)
		Kit bridge = kits.get("bridge");
		if (bridge != null && bridge.items != null && bridge.items.stream().noneMatch(it -> it.item != null && it.item.contains("terracotta"))) {
			bridge.items.add(i(CLAY, 64, "3"));
			bridge.items.add(i(CLAY, 64, "4"));
		}
		if (configVersion < 2) {
			maceSword(kits.get("mace"));
			configVersion = 2;
		}
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

	/**
	 * kits.json from before 1.46: the mace kit gets a sword, which people fight with on the ground (the mace is for
	 * coming down from a wind charge). The kit as it came out of the box gets the new one (sword first); a kit someone
	 * changed keeps everything and gets the sword in the first free hotbar slot.
	 */
	static void maceSword(Kit mace) {
		if (mace == null) return;
		if (mace.items == null) mace.items = new ArrayList<>();
		if (mace.items.stream().anyMatch(it -> it != null && it.item != null && it.item.endsWith("_sword"))) return;
		if (oldMace(mace)) {
			mace.items.get(0).slot = "1";
			mace.items.get(1).slot = "2";
			mace.items.get(2).slot = "3";
			mace.items.add(3, i("diamond_sword", 1, "0"));
			return;
		}
		java.util.Set<String> used = new java.util.HashSet<>();
		for (KitItem it : mace.items) {
			if (it != null && it.slot != null) used.add(it.slot);
		}
		for (int slot = 0; slot < 36; slot++) {
			if (!used.contains(String.valueOf(slot))) {
				mace.items.add(i("diamond_sword", 1, String.valueOf(slot)));
				return;
			}
		}
	}

	/** The mace kit as BattleHub made it before 1.46 (mace, 16 wind charges, 2 golden apples, then the armour). */
	private static boolean oldMace(Kit k) {
		List<KitItem> l = k.items;
		return l.size() >= 3 && same(l.get(0), "minecraft:mace", 1, "0") && same(l.get(1), "minecraft:wind_charge", 16, "1")
				&& same(l.get(2), "minecraft:golden_apple", 2, "2")
				&& l.subList(3, l.size()).stream().allMatch(it -> it != null && it.slot != null && !it.slot.matches("\\d+"));
	}

	private static boolean same(KitItem it, String item, int count, String slot) {
		return it != null && item.equals(it.item) && it.count == count && slot.equals(it.slot) && (it.enchantments == null || it.enchantments.isEmpty());
	}
}
