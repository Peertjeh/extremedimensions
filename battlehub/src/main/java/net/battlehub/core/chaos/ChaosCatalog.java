package net.battlehub.core.chaos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.battlehub.core.chaos.ChaosEvent.Category.BODY;
import static net.battlehub.core.chaos.ChaosEvent.Category.EFFECT;
import static net.battlehub.core.chaos.ChaosEvent.Category.FUN;
import static net.battlehub.core.chaos.ChaosEvent.Category.ITEM;
import static net.battlehub.core.chaos.ChaosEvent.Category.RULE;
import static net.battlehub.core.chaos.ChaosEvent.Category.WORLD;

/**
 * The events of The Bridge: Chaos (172). Every match plans at least twenty of them plus the five in {@link #FIXED};
 * how many a match really sees depends on how long it lasts, and after the plan they come again, worse
 * ({@link ChaosIntensity}). The weight (1 rare .. 3 common) sets how often an
 * event is drawn. Titles and subtitles live in the language files ({@code bridge_chaos.event.<id>} and {@code .sub}).
 */
public final class ChaosCatalog {
	private ChaosCatalog() {}

	/** In every match: a pearl, low gravity, the bridge crumbling, cobwebs and double points. */
	public static final List<String> FIXED = List.of("pearl", "low_gravity", "crumble", "cobweb", "double_points");

	public static final Set<String> VERBS = Set.of("effect", "attr", "give", "potion", "do");

	/** What {@code do} can do; the match carries each one out. */
	public static final Set<String> SPECIALS = Set.of("crumble", "webs", "double_points", "lucky", "elytra", "launch", "swap", "magnet", "shockwave", "glass_bridge", "ice_bridge", "snowstorm", "arrow_rain", "erode", "bombs", "lightning", "triple_points", "no_build", "no_pvp", "heal", "home", "restock", "danger", "fireworks", "chickens", "shuffle_hotbar", "ignite");

	private static final Map<String, ChaosEvent> BY_ID = new LinkedHashMap<>();

	static {
		List<ChaosEvent> l = new ArrayList<>();
		e(l, "pearl", ITEM, 0, 3, "give minecraft:ender_pearl 1");
		e(l, "low_gravity", BODY, 10, 3, "effect minecraft:jump_boost 2; effect minecraft:slow_falling 0");
		e(l, "crumble", WORLD, 0, 3, "do crumble");
		e(l, "cobweb", ITEM, 0, 3, "give minecraft:cobweb 3; do webs");
		e(l, "double_points", RULE, 0, 3, "do double_points");
		e(l, "wind_burst", ITEM, 0, 3, "give minecraft:wind_charge 3");
		e(l, "snowball_fight", ITEM, 0, 3, "give minecraft:snowball 16");
		e(l, "egg_toss", ITEM, 0, 2, "give minecraft:egg 16");
		e(l, "golden_apples", ITEM, 0, 3, "give minecraft:golden_apple 2");
		e(l, "notch_apple", ITEM, 0, 1, "give minecraft:enchanted_golden_apple 1");
		e(l, "fishing_rod", ITEM, 0, 2, "give minecraft:fishing_rod 1");
		e(l, "knockback_stick", ITEM, 0, 3, "give minecraft:stick 1 minecraft:knockback 2");
		e(l, "punch_bow", ITEM, 0, 2, "give minecraft:bow 1 minecraft:punch 2; give minecraft:arrow 8");
		e(l, "quiver", ITEM, 0, 2, "give minecraft:arrow 16");
		e(l, "builder_gift", ITEM, 0, 3, "give minecraft:white_terracotta 64");
		e(l, "slime_blocks", ITEM, 0, 2, "give minecraft:slime_block 8");
		e(l, "honey_blocks", ITEM, 0, 2, "give minecraft:honey_block 8");
		e(l, "scaffolding", ITEM, 0, 2, "give minecraft:scaffolding 16");
		e(l, "ice_blocks", ITEM, 0, 2, "give minecraft:packed_ice 16");
		e(l, "glass_blocks", ITEM, 0, 2, "give minecraft:glass 32");
		e(l, "shield", ITEM, 0, 2, "give minecraft:shield 1");
		e(l, "totem", ITEM, 0, 1, "give minecraft:totem_of_undying 1");
		e(l, "mace", ITEM, 0, 2, "give minecraft:mace 1; give minecraft:wind_charge 2");
		e(l, "trident", ITEM, 0, 2, "give minecraft:trident 1 minecraft:loyalty 3");
		e(l, "multishot", ITEM, 0, 2, "give minecraft:crossbow 1 minecraft:multishot 1; give minecraft:arrow 9");
		e(l, "chorus", ITEM, 0, 2, "give minecraft:chorus_fruit 3");
		e(l, "pearl_storm", ITEM, 0, 1, "give minecraft:ender_pearl 3");
		e(l, "sharp_blade", ITEM, 0, 2, "give minecraft:diamond_sword 1 minecraft:sharpness 2");
		e(l, "lucky", FUN, 0, 2, "do lucky");
		e(l, "elytra", FUN, 15, 1, "do elytra; give minecraft:firework_rocket 3");
		e(l, "harming_potions", ITEM, 0, 2, "potion splash minecraft:harming 2");
		e(l, "healing_potions", ITEM, 0, 2, "potion splash minecraft:healing 2");
		e(l, "slowness_potions", ITEM, 0, 2, "potion splash minecraft:slowness 2");
		e(l, "leaping_potion", ITEM, 0, 2, "potion drink minecraft:strong_leaping 1");
		e(l, "swift_potion", ITEM, 0, 2, "potion drink minecraft:strong_swiftness 1");
		e(l, "turtle_potion", ITEM, 0, 1, "potion drink minecraft:turtle_master 1");
		e(l, "slow_fall_potions", ITEM, 0, 2, "potion splash minecraft:slow_falling 2");
		e(l, "invis_potion", ITEM, 0, 1, "potion drink minecraft:invisibility 1");
		e(l, "speed_rush", EFFECT, 10, 3, "effect minecraft:speed 1");
		e(l, "slow_motion", EFFECT, 8, 2, "effect minecraft:slowness 1");
		e(l, "haste", EFFECT, 12, 2, "effect minecraft:haste 2");
		e(l, "power_surge", EFFECT, 8, 2, "effect minecraft:strength 0");
		e(l, "wet_noodles", EFFECT, 10, 2, "effect minecraft:weakness 0");
		e(l, "second_wind", EFFECT, 8, 2, "effect minecraft:regeneration 1");
		e(l, "iron_skin", EFFECT, 10, 2, "effect minecraft:resistance 1");
		e(l, "super_jump", EFFECT, 8, 2, "effect minecraft:jump_boost 3");
		e(l, "lights_out", EFFECT, 4, 1, "effect minecraft:blindness 0");
		e(l, "darkness", EFFECT, 8, 1, "effect minecraft:darkness 0");
		e(l, "ghosts", EFFECT, 10, 2, "effect minecraft:invisibility 0");
		e(l, "spotlight", EFFECT, 15, 2, "effect minecraft:glowing 0");
		e(l, "lift_off", EFFECT, 6, 2, "effect minecraft:levitation 1 2; effect minecraft:slow_falling 0 6");
		e(l, "golden_hearts", EFFECT, 20, 2, "effect minecraft:absorption 2");
		e(l, "big_hearts", EFFECT, 20, 2, "effect minecraft:health_boost 2; effect minecraft:instant_health 1 1");
		e(l, "feather_fall", EFFECT, 12, 2, "effect minecraft:slow_falling 0");
		e(l, "turbo", EFFECT, 4, 2, "effect minecraft:speed 4");
		e(l, "infested", EFFECT, 12, 1, "effect minecraft:infested 0");
		e(l, "berserk", EFFECT, 8, 2, "effect minecraft:strength 1; effect minecraft:slowness 0");
		e(l, "parkour_mode", EFFECT, 10, 2, "effect minecraft:speed 2; effect minecraft:jump_boost 1");
		e(l, "tank_mode", EFFECT, 12, 2, "effect minecraft:resistance 2; effect minecraft:slowness 1");
		e(l, "tiny", BODY, 12, 3, "attr minecraft:scale total -0.5");
		e(l, "giants", BODY, 10, 2, "attr minecraft:scale total 0.6");
		e(l, "moon_walk", BODY, 12, 3, "attr minecraft:gravity total -0.7; attr minecraft:safe_fall_distance add 20");
		e(l, "heavy", BODY, 10, 2, "attr minecraft:gravity total 0.6");
		e(l, "long_arms", BODY, 12, 2, "attr minecraft:entity_interaction_range add 2.5");
		e(l, "long_reach", BODY, 15, 2, "attr minecraft:block_interaction_range add 4");
		e(l, "knockback_frenzy", BODY, 10, 3, "attr minecraft:attack_knockback add 2");
		e(l, "anchored", BODY, 10, 2, "attr minecraft:knockback_resistance add 1");
		e(l, "springs", BODY, 10, 2, "attr minecraft:jump_strength total 0.5");
		e(l, "step_up", BODY, 15, 1, "attr minecraft:step_height add 1");
		e(l, "glass_cannon", BODY, 12, 2, "attr minecraft:max_health total -0.5; attr minecraft:attack_damage total 0.5");
		e(l, "juggernaut", BODY, 15, 1, "attr minecraft:max_health add 20; effect minecraft:regeneration 2 6");
		e(l, "spam_click", BODY, 12, 2, "attr minecraft:attack_speed total 1");
		e(l, "instant_mine", BODY, 12, 2, "attr minecraft:block_break_speed total 2");
		e(l, "no_fall", BODY, 20, 1, "attr minecraft:fall_damage_multiplier total -1");
		e(l, "mice", BODY, 10, 2, "attr minecraft:scale total -0.4; attr minecraft:movement_speed total 0.3");
		e(l, "slow_giants", BODY, 10, 2, "attr minecraft:scale total 0.5; attr minecraft:movement_speed total -0.25");
		e(l, "trampoline", BODY, 10, 2, "attr minecraft:jump_strength total 0.8; attr minecraft:gravity total -0.3; attr minecraft:safe_fall_distance add 10");
		e(l, "freeze", BODY, 3, 2, "attr minecraft:movement_speed total -1; attr minecraft:jump_strength total -1");
		e(l, "launch", WORLD, 0, 2, "do launch; effect minecraft:slow_falling 0 4");
		e(l, "swap", WORLD, 0, 2, "do swap");
		e(l, "magnet", WORLD, 0, 2, "do magnet");
		e(l, "shockwave", WORLD, 0, 2, "do shockwave");
		e(l, "glass_bridge", WORLD, 15, 2, "do glass_bridge");
		e(l, "ice_bridge", WORLD, 0, 2, "do ice_bridge");
		e(l, "snowstorm", WORLD, 8, 2, "do snowstorm");
		e(l, "arrow_rain", WORLD, 6, 2, "do arrow_rain");
		e(l, "erosion", WORLD, 0, 2, "do erode");
		e(l, "bombs", WORLD, 0, 2, "do bombs");
		e(l, "thunder", WORLD, 6, 1, "do lightning");
		e(l, "triple_points", RULE, 0, 1, "do triple_points");
		e(l, "hands_off", RULE, 8, 2, "do no_build");
		e(l, "truce", RULE, 8, 2, "do no_pvp");
		e(l, "heal_all", RULE, 0, 2, "do heal");
		e(l, "back_home", RULE, 0, 1, "do home");
		e(l, "restock", RULE, 0, 2, "do restock");
		e(l, "danger_zone", RULE, 0, 1, "do danger");
		e(l, "fireworks", FUN, 0, 1, "do fireworks");
		e(l, "chicken_rain", FUN, 10, 1, "do chickens");
		e(l, "butterfingers", FUN, 0, 1, "do shuffle_hotbar");
		e(l, "hot_feet", FUN, 0, 1, "do ignite");
		// 1.31: seventy-two more
		e(l, "flint_and_steel", ITEM, 0, 1, "give minecraft:flint_and_steel 1");
		e(l, "fire_charges", ITEM, 0, 2, "give minecraft:fire_charge 3");
		e(l, "water_bucket", ITEM, 0, 2, "give minecraft:water_bucket 1");
		e(l, "stone_supply", ITEM, 0, 2, "give minecraft:cobblestone 32");
		e(l, "ladders", ITEM, 0, 2, "give minecraft:ladder 16");
		e(l, "sand_drop", ITEM, 0, 2, "give minecraft:sand 16");
		e(l, "gravel_gift", ITEM, 0, 2, "give minecraft:gravel 16");
		e(l, "soul_sand", ITEM, 0, 1, "give minecraft:soul_sand 8");
		e(l, "netherite_sword", ITEM, 0, 1, "give minecraft:netherite_sword 1");
		e(l, "battle_axe", ITEM, 0, 2, "give minecraft:iron_axe 1 minecraft:sharpness 1");
		e(l, "flame_bow", ITEM, 0, 1, "give minecraft:bow 1 minecraft:flame 1; give minecraft:arrow 8");
		e(l, "spectral_arrows", ITEM, 0, 2, "give minecraft:spectral_arrow 8");
		e(l, "quick_crossbow", ITEM, 0, 2, "give minecraft:crossbow 1 minecraft:quick_charge 3; give minecraft:arrow 6");
		e(l, "piercing_crossbow", ITEM, 0, 1, "give minecraft:crossbow 1 minecraft:piercing 4; give minecraft:arrow 6");
		e(l, "wind_storm", ITEM, 0, 1, "give minecraft:wind_charge 6");
		e(l, "cookies", ITEM, 0, 2, "give minecraft:cookie 16");
		e(l, "golden_carrots", ITEM, 0, 2, "give minecraft:golden_carrot 8");
		e(l, "honey_bottles", ITEM, 0, 1, "give minecraft:honey_bottle 3");
		e(l, "magma_blocks", ITEM, 0, 1, "give minecraft:magma_block 8");
		e(l, "dripstone", ITEM, 0, 1, "give minecraft:pointed_dripstone 8");
		e(l, "shield_wall", ITEM, 0, 1, "give minecraft:shield 1; give minecraft:oak_planks 32");
		e(l, "poison_potions", ITEM, 0, 1, "potion splash minecraft:poison 2");
		e(l, "regen_potion", ITEM, 0, 2, "potion drink minecraft:regeneration 1");
		e(l, "strength_potion", ITEM, 0, 2, "potion drink minecraft:strength 1");
		e(l, "weakness_potions", ITEM, 0, 2, "potion splash minecraft:weakness 2");
		e(l, "lingering_harm", ITEM, 0, 1, "potion lingering minecraft:harming 1");
		e(l, "lingering_heal", ITEM, 0, 1, "potion lingering minecraft:healing 1");
		e(l, "fire_res_potion", ITEM, 0, 1, "potion drink minecraft:fire_resistance 1");
		e(l, "wind_potions", ITEM, 0, 1, "potion splash minecraft:wind_charged 2");
		e(l, "weaving_potions", ITEM, 0, 1, "potion splash minecraft:weaving 2");
		e(l, "nausea", EFFECT, 8, 1, "effect minecraft:nausea 0");
		e(l, "starving", EFFECT, 10, 1, "effect minecraft:hunger 2");
		e(l, "poisoned", EFFECT, 6, 1, "effect minecraft:poison 0");
		e(l, "withering", EFFECT, 5, 1, "effect minecraft:wither 0");
		e(l, "tired_hands", EFFECT, 10, 2, "effect minecraft:mining_fatigue 1");
		e(l, "adrenaline", EFFECT, 8, 2, "effect minecraft:speed 1; effect minecraft:strength 0; effect minecraft:haste 1");
		e(l, "sluggish", EFFECT, 8, 2, "effect minecraft:slowness 2; effect minecraft:mining_fatigue 0");
		e(l, "fortress", EFFECT, 10, 1, "effect minecraft:resistance 3; effect minecraft:slowness 2");
		e(l, "glow_party", EFFECT, 12, 2, "effect minecraft:glowing 0; effect minecraft:speed 0");
		e(l, "oozing", EFFECT, 12, 1, "effect minecraft:oozing 0");
		e(l, "weaving", EFFECT, 12, 1, "effect minecraft:weaving 0");
		e(l, "gliders", EFFECT, 10, 2, "effect minecraft:slow_falling 0; effect minecraft:speed 2");
		e(l, "regen_burst", EFFECT, 4, 2, "effect minecraft:regeneration 3");
		e(l, "short_arms", BODY, 12, 2, "attr minecraft:entity_interaction_range add -1.5");
		e(l, "short_reach", BODY, 12, 1, "attr minecraft:block_interaction_range add -2.5");
		e(l, "home_run", BODY, 6, 1, "attr minecraft:attack_knockback add 4");
		e(l, "heavy_weapons", BODY, 12, 2, "attr minecraft:attack_speed total -0.5");
		e(l, "rabbits", BODY, 10, 2, "attr minecraft:jump_strength total 0.4; attr minecraft:scale total -0.3");
		e(l, "tall_ones", BODY, 12, 2, "attr minecraft:scale total 0.3; attr minecraft:entity_interaction_range add 1");
		e(l, "brittle", BODY, 10, 2, "attr minecraft:armor total -1");
		e(l, "armored", BODY, 12, 2, "attr minecraft:armor add 10; attr minecraft:armor_toughness add 4");
		e(l, "lightweight", BODY, 10, 2, "attr minecraft:gravity total -0.4; attr minecraft:movement_speed total 0.2");
		e(l, "flat_footed", BODY, 8, 2, "attr minecraft:jump_strength total -0.6");
		e(l, "sweeper", BODY, 12, 1, "attr minecraft:sweeping_damage_ratio add 1");
		e(l, "planted", BODY, 12, 1, "attr minecraft:explosion_knockback_resistance add 1");
		e(l, "kindling", FUN, 10, 1, "attr minecraft:burning_time total 2; do ignite");
		e(l, "carpet_bombing", WORLD, 0, 1, "do bombs; do bombs");
		e(l, "storm", WORLD, 8, 1, "do snowstorm; do arrow_rain");
		e(l, "earthquake", WORLD, 0, 1, "do erode; do shockwave");
		e(l, "pull", WORLD, 0, 2, "do magnet; effect minecraft:slow_falling 0 3");
		e(l, "switcheroo", RULE, 0, 1, "do swap; do heal");
		e(l, "sudden_death", RULE, 0, 1, "do danger; do double_points");
		e(l, "fresh_start", RULE, 0, 1, "do home; do heal; do restock");
		e(l, "peace_talks", RULE, 10, 1, "do no_pvp; effect minecraft:regeneration 1");
		e(l, "brawl", RULE, 8, 1, "do no_build; effect minecraft:strength 0");
		e(l, "rocket_jump", WORLD, 0, 1, "do launch; give minecraft:wind_charge 2");
		e(l, "ice_age", WORLD, 10, 1, "do ice_bridge; do snowstorm; effect minecraft:slowness 0");
		e(l, "glass_sprint", WORLD, 15, 1, "do glass_bridge; effect minecraft:speed 1");
		e(l, "dark_storm", WORLD, 8, 1, "do lightning; effect minecraft:darkness 0");
		e(l, "chicken_army", FUN, 12, 1, "do chickens; do fireworks");
		e(l, "scramble", FUN, 0, 1, "do shuffle_hotbar; do swap");
		e(l, "jackpot", FUN, 0, 1, "do lucky; do lucky");
		for (ChaosEvent ev : l) BY_ID.put(ev.id(), ev);
	}

	private static void e(List<ChaosEvent> l, String id, ChaosEvent.Category c, int seconds, int weight, String actions) {
		List<ChaosEvent.Action> list = new ArrayList<>();
		for (String a : actions.split(";")) list.add(ChaosEvent.Action.parse(a));
		l.add(new ChaosEvent(id, c, seconds, weight, List.copyOf(list)));
	}

	public static List<ChaosEvent> all() {
		return List.copyOf(BY_ID.values());
	}

	public static ChaosEvent get(String id) {
		ChaosEvent e = BY_ID.get(id);
		if (e == null) throw new IllegalArgumentException("unknown chaos event " + id);
		return e;
	}

	public static boolean has(String id) {
		return BY_ID.containsKey(id);
	}

	/** Every attribute an event changes (so they can all be taken back). */
	public static List<String> attributes() {
		List<String> out = new ArrayList<>();
		for (ChaosEvent e : BY_ID.values()) {
			for (ChaosEvent.Action a : e.actions()) {
				if (a.verb().equals("attr") && !out.contains(a.arg(0))) out.add(a.arg(0));
			}
		}
		return out;
	}

	/** What is wrong with the catalogue (empty when it is fine); the tests check this. */
	public static List<String> problems() {
		List<String> out = new ArrayList<>();
		if (BY_ID.size() < 150) out.add("there are " + BY_ID.size() + " events, fewer than 150");
		for (String f : FIXED) {
			if (!BY_ID.containsKey(f)) out.add("fixed event " + f + " is missing");
		}
		java.util.Set<String> usedSpecials = new java.util.HashSet<>();
		for (ChaosEvent e : BY_ID.values()) {
			String where = e.id() + ": ";
			if (!e.id().matches("[a-z_]+")) out.add(where + "bad id");
			if (e.seconds() < 0 || e.seconds() > 30) out.add(where + "length " + e.seconds());
			if (e.weight() < 1 || e.weight() > 5) out.add(where + "weight " + e.weight());
			if (e.actions().isEmpty()) out.add(where + "no actions");
			for (ChaosEvent.Action a : e.actions()) {
				String p = check(e, a);
				if (p != null) out.add(where + a.verb() + " " + String.join(" ", a.args()) + ": " + p);
				if (a.verb().equals("do")) usedSpecials.add(a.arg(0));
			}
		}
		for (String s : SPECIALS) {
			if (!usedSpecials.contains(s)) out.add("special " + s + " is never used");
		}
		return out;
	}

	private static String check(ChaosEvent e, ChaosEvent.Action a) {
		if (!VERBS.contains(a.verb())) return "unknown verb";
		switch (a.verb()) {
			case "effect" -> {
				if (a.args().size() < 2 || a.args().size() > 3) return "effect <id> <amplifier> [seconds]";
				if (!a.arg(0).contains(":")) return "id without namespace";
				int amp = number(a.arg(1));
				if (amp < 0 || amp > 9) return "amplifier";
				if (a.args().size() == 3 && (number(a.arg(2)) < 1 || number(a.arg(2)) > 30)) return "seconds";
				if (a.args().size() == 2 && e.seconds() <= 0) return "an effect needs a length";
			}
			case "attr" -> {
				if (a.args().size() != 3) return "attr <id> <add|base|total> <amount>";
				if (!a.arg(0).contains(":")) return "id without namespace";
				if (!Set.of("add", "base", "total").contains(a.arg(1))) return "operation";
				if (Double.isNaN(decimal(a.arg(2)))) return "amount";
				if (e.seconds() <= 0) return "an attribute change needs a length";
			}
			case "give" -> {
				if (a.args().size() != 2 && a.args().size() != 4) return "give <item> <count> [<enchantment> <level>]";
				if (!a.arg(0).contains(":")) return "id without namespace";
				int n = number(a.arg(1));
				if (n < 1 || n > 64) return "count";
				if (a.args().size() == 4 && (!a.arg(2).contains(":") || number(a.arg(3)) < 1 || number(a.arg(3)) > 5)) return "enchantment";
			}
			case "potion" -> {
				if (a.args().size() != 3) return "potion <splash|lingering|drink> <potion> <count>";
				if (!Set.of("splash", "lingering", "drink").contains(a.arg(0))) return "kind";
				if (!a.arg(1).contains(":")) return "id without namespace";
				if (number(a.arg(2)) < 1 || number(a.arg(2)) > 4) return "count";
			}
			default -> {
				if (a.args().size() != 1 || !SPECIALS.contains(a.arg(0))) return "unknown special";
			}
		}
		return null;
	}

	private static int number(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException ex) {
			return -1;
		}
	}

	private static double decimal(String s) {
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException ex) {
			return Double.NaN;
		}
	}
}
