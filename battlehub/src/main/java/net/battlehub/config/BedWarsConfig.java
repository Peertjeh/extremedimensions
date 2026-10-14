package net.battlehub.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code config/battlehub/bedwars.json}: teams, generators, the event schedule, building limits and the shop.
 * Times are in seconds unless the name says ticks; {team} in an item id becomes the team colour (red_wool).
 */
public final class BedWarsConfig {
	/** The format when nobody votes in the lobby: "solo" (everyone for themselves) or "duo" (teams of two). */
	public String defaultFormat = "duo";
	/** Players per team in duo. */
	public int duoTeamSize = 2;
	/** Most islands (teams) in one arena, 2 to 8; with more players than fit, teams grow. */
	public int maxTeams = 8;
	public int respawnSeconds = 5;
	/** How long the winners stand on the podium before everyone goes back. */
	public int podiumSeconds = 8;
	/** Building: blocks above and below the island floor. */
	public int buildHeight = 24;
	public int buildDepth = 10;
	/** No building this close (in blocks) to spawns, shops and generators. */
	public int protectionRadius = 2;
	/** Players may drop items (to share resources with their team). */
	public boolean dropsAllowed = true;
	/** A kill takes the victim's iron, gold, diamonds and emeralds to the killer. */
	public boolean killerTakesResources = true;
	/** Seconds TNT burns before it explodes; it only breaks blocks placed by players. */
	public int tntFuseSeconds = 3;
	public double tntPower = 3.0;
	public Map<String, Generator> generators = defaultGenerators();
	public List<Event> events = defaultEvents();
	public List<Offer> shop = defaultShop();
	public List<Upgrade> upgrades = defaultUpgrades();

	/** A generator kind: ticks between drops per tier, items per drop and the most lying around it. */
	public static final class Generator {
		public int[] intervalTicks = {20};
		public int amount = 1;
		public int cap = 16;

		public Generator() {}

		Generator(int amount, int cap, int... intervalTicks) {
			this.intervalTicks = intervalTicks;
			this.amount = amount;
			this.cap = cap;
		}
	}

	/**
	 * Something happening at a time: diamond / emerald (generators to tier {@code value}), sudden_death (all beds
	 * break), glow (everyone glows so nobody can hide).
	 */
	public static final class Event {
		public int atSeconds;
		public String type = "";
		public int value;

		public Event() {}

		Event(int atSeconds, String type, int value) {
			this.atSeconds = atSeconds;
			this.type = type;
			this.value = value;
		}
	}

	/**
	 * A shop offer. Categories: blocks, weapons, armor, tools, utility. Groups make upgrades that only go up: armor
	 * (item = chainmail, iron or diamond: leggings and boots), pickaxe, axe, shears.
	 */
	public static final class Offer {
		public String id = "";
		public String category = "blocks";
		public String item = "minecraft:stone";
		public int count = 1;
		public String currency = "iron";
		public int price = 1;
		public String group = "";
		public int tier;
		public Map<String, Integer> enchantments = new LinkedHashMap<>();

		public Offer() {}

		Offer(String id, String category, String item, int count, String currency, int price) {
			this.id = id;
			this.category = category;
			this.item = item;
			this.count = count;
			this.currency = currency;
			this.price = price;
		}

		Offer upgrade(String group, int tier) {
			this.group = group;
			this.tier = tier;
			return this;
		}

		Offer ench(String id, int level) {
			enchantments.put(id, level);
			return this;
		}
	}

	/** A team upgrade bought with diamonds: sharpness (swords), protection (armour), forge (faster base generator). */
	public static final class Upgrade {
		public String id = "";
		public String currency = "diamond";
		/** Price per level: the first entry buys level 1. */
		public int[] prices = {4};

		public Upgrade() {}

		Upgrade(String id, int... prices) {
			this.id = id;
			this.prices = prices;
		}
	}

	static Map<String, Generator> defaultGenerators() {
		Map<String, Generator> m = new LinkedHashMap<>();
		// Base generators get faster with the forge upgrade; diamond and emerald with the schedule
		m.put("iron", new Generator(1, 48, 24, 16, 10));
		m.put("gold", new Generator(1, 16, 140, 100, 60));
		m.put("diamond", new Generator(1, 4, 600, 460, 300));
		m.put("emerald", new Generator(1, 2, 1200, 900, 600));
		return m;
	}

	static List<Event> defaultEvents() {
		List<Event> l = new ArrayList<>();
		l.add(new Event(360, "diamond", 2));
		l.add(new Event(720, "emerald", 2));
		l.add(new Event(1080, "diamond", 3));
		l.add(new Event(1440, "emerald", 3));
		l.add(new Event(1500, "sudden_death", 0));
		l.add(new Event(1620, "glow", 0));
		return l;
	}

	static List<Offer> defaultShop() {
		List<Offer> l = new ArrayList<>();
		l.add(new Offer("wool", "blocks", "minecraft:{team}_wool", 16, "iron", 4));
		l.add(new Offer("terracotta", "blocks", "minecraft:{team}_terracotta", 16, "iron", 12));
		l.add(new Offer("glass", "blocks", "minecraft:{team}_stained_glass", 4, "iron", 12));
		l.add(new Offer("planks", "blocks", "minecraft:oak_planks", 16, "gold", 4));
		l.add(new Offer("end_stone", "blocks", "minecraft:end_stone", 12, "iron", 24));
		l.add(new Offer("ladder", "blocks", "minecraft:ladder", 8, "iron", 4));
		l.add(new Offer("obsidian", "blocks", "minecraft:obsidian", 4, "emerald", 4));
		l.add(new Offer("stone_sword", "weapons", "minecraft:stone_sword", 1, "iron", 10));
		l.add(new Offer("iron_sword", "weapons", "minecraft:iron_sword", 1, "gold", 7));
		l.add(new Offer("diamond_sword", "weapons", "minecraft:diamond_sword", 1, "emerald", 4));
		l.add(new Offer("knockback_stick", "weapons", "minecraft:stick", 1, "gold", 5).ench("minecraft:knockback", 1));
		l.add(new Offer("bow", "weapons", "minecraft:bow", 1, "gold", 12));
		l.add(new Offer("power_bow", "weapons", "minecraft:bow", 1, "gold", 24).ench("minecraft:power", 1));
		l.add(new Offer("arrows", "weapons", "minecraft:arrow", 6, "gold", 2));
		l.add(new Offer("chainmail_armor", "armor", "chainmail", 1, "iron", 24).upgrade("armor", 1));
		l.add(new Offer("iron_armor", "armor", "iron", 1, "gold", 12).upgrade("armor", 2));
		l.add(new Offer("diamond_armor", "armor", "diamond", 1, "emerald", 6).upgrade("armor", 3));
		l.add(new Offer("shears", "tools", "minecraft:shears", 1, "iron", 20).upgrade("shears", 1));
		l.add(new Offer("wooden_pickaxe", "tools", "minecraft:wooden_pickaxe", 1, "iron", 10).upgrade("pickaxe", 1).ench("minecraft:efficiency", 1));
		l.add(new Offer("iron_pickaxe", "tools", "minecraft:iron_pickaxe", 1, "iron", 10).upgrade("pickaxe", 2).ench("minecraft:efficiency", 2));
		l.add(new Offer("golden_pickaxe", "tools", "minecraft:golden_pickaxe", 1, "gold", 3).upgrade("pickaxe", 3).ench("minecraft:efficiency", 3));
		l.add(new Offer("diamond_pickaxe", "tools", "minecraft:diamond_pickaxe", 1, "gold", 6).upgrade("pickaxe", 4).ench("minecraft:efficiency", 3));
		l.add(new Offer("wooden_axe", "tools", "minecraft:wooden_axe", 1, "iron", 10).upgrade("axe", 1).ench("minecraft:efficiency", 1));
		l.add(new Offer("stone_axe", "tools", "minecraft:stone_axe", 1, "iron", 10).upgrade("axe", 2).ench("minecraft:efficiency", 1));
		l.add(new Offer("iron_axe", "tools", "minecraft:iron_axe", 1, "gold", 3).upgrade("axe", 3).ench("minecraft:efficiency", 2));
		l.add(new Offer("diamond_axe", "tools", "minecraft:diamond_axe", 1, "gold", 6).upgrade("axe", 4).ench("minecraft:efficiency", 3));
		l.add(new Offer("golden_apple", "utility", "minecraft:golden_apple", 1, "gold", 3));
		l.add(new Offer("tnt", "utility", "minecraft:tnt", 1, "gold", 4));
		l.add(new Offer("ender_pearl", "utility", "minecraft:ender_pearl", 1, "emerald", 4));
		l.add(new Offer("water_bucket", "utility", "minecraft:water_bucket", 1, "gold", 3));
		l.add(new Offer("snowballs", "utility", "minecraft:snowball", 8, "iron", 16));
		return l;
	}

	static List<Upgrade> defaultUpgrades() {
		List<Upgrade> l = new ArrayList<>();
		l.add(new Upgrade("sharpness", 4));
		l.add(new Upgrade("protection", 2, 4, 8));
		l.add(new Upgrade("forge", 2, 4));
		return l;
	}

	public BedWarsConfig normalize() {
		if (!"solo".equals(defaultFormat) && !"duo".equals(defaultFormat)) defaultFormat = "duo";
		duoTeamSize = Math.max(1, Math.min(4, duoTeamSize));
		maxTeams = Math.max(2, Math.min(8, maxTeams));
		respawnSeconds = Math.max(0, respawnSeconds);
		podiumSeconds = Math.max(1, podiumSeconds);
		buildHeight = Math.max(4, buildHeight);
		buildDepth = Math.max(1, buildDepth);
		protectionRadius = Math.max(0, protectionRadius);
		tntFuseSeconds = Math.max(1, tntFuseSeconds);
		tntPower = Math.max(0.5, Math.min(8, tntPower));
		if (generators == null) generators = defaultGenerators();
		defaultGenerators().forEach(generators::putIfAbsent);
		for (Generator g : generators.values()) {
			if (g.intervalTicks == null || g.intervalTicks.length == 0) g.intervalTicks = new int[] {20};
			g.amount = Math.max(1, g.amount);
			g.cap = Math.max(1, g.cap);
		}
		if (events == null) events = defaultEvents();
		events.removeIf(e -> e == null || e.type == null);
		if (shop == null) shop = defaultShop();
		shop.removeIf(o -> o == null || o.id == null || o.id.isBlank() || o.item == null);
		for (Offer o : shop) {
			if (o.category == null) o.category = "utility";
			if (o.currency == null) o.currency = "iron";
			if (o.group == null) o.group = "";
			if (o.enchantments == null) o.enchantments = new LinkedHashMap<>();
			o.count = Math.max(1, o.count);
			o.price = Math.max(0, o.price);
		}
		if (upgrades == null) upgrades = defaultUpgrades();
		upgrades.removeIf(u -> u == null || u.id == null || u.prices == null || u.prices.length == 0);
		return this;
	}
}
