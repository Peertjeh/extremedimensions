package com.extremedimensions.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Four difficulties for the world's rules, Easy to Extreme. Applying one sets the values below at once (operators only);
 * every other setting stays as it is. {@link #NORMAL} is the mod's own default.
 *
 * <p>What changes: how long atonement takes and how much a death undoes of it, how much charge an anchor holds and
 * how fast it drains, how often offers are multiplied and how many cards they show, how often an Extreme card turns
 * up, how often the Lurkers come, how much they take and how hard they hit, whether the whole tribe comes at once (Extreme),
 * how hard the tribe's standing is won, how often the Wound Storms break, how often
 * fragments drop, whether a curse not yet carried
 * shows in runes, and whether the Abyssal curses can be dealt, how often and how many (Hard and Extreme).
 */
public enum Preset {
	EASY("easy",
			"atone_base", "60", "atone_per_stack", "25", "atone_death_loss", "0",
			"max_charge", "3600", "base_drain", "0.6", "tether_distance_step", "200",
			"multiplier_chance", "0", "multiplier_max", "2", "offer_count", "4", "extreme_card_chance", "0",
			"lurker_spawn_chance", "0.004", "lurker_cooldown_seconds", "1800", "storm_chance", "0.02",
			"fragment_chance", "0.08", "fragment_chance_elite", "0.2", "curse_runes", "0",
			"abyssal_curses", "0", "abyssal_card_chance", "0.01", "abyssal_forced_chance", "0", "abyssal_after_collapses", "5",
			"abyssal_max_carried", "1",
			"lurker_stalk_chance", "0.15", "lurker_thefts", "1", "lurker_claw_damage", "2", "lurker_curse_chance", "0.05",
			"lurker_curse_extreme_chance", "0", "lurker_curses_max", "1", "lurker_hunt_chance", "0.1", "lurker_retaliate_chance", "0.15",
			"lurker_strike_damage", "4", "lurker_strike_blows", "2",
			"lurker_swarm_chance", "0", "tribe_rep_gift", "3", "tribe_gift_minutes", "5", "tribe_gift_cost", "1",
			"tribe_rep_bond", "10", "tribe_rep_barter", "1"),
	NORMAL("normal",
			"atone_base", "120", "atone_per_stack", "60", "atone_death_loss", "0.5",
			"max_charge", "1800", "base_drain", "1", "tether_distance_step", "100",
			"multiplier_chance", "0.15", "multiplier_max", "4", "offer_count", "3", "extreme_card_chance", "0.025",
			"lurker_spawn_chance", "0.012", "lurker_cooldown_seconds", "900", "storm_chance", "0.04",
			"fragment_chance", "0.04", "fragment_chance_elite", "0.12", "curse_runes", "0",
			"abyssal_curses", "0", "abyssal_card_chance", "0.01", "abyssal_forced_chance", "0", "abyssal_after_collapses", "5",
			"abyssal_max_carried", "1",
			"lurker_stalk_chance", "0.3", "lurker_thefts", "2", "lurker_claw_damage", "4", "lurker_curse_chance", "0.15",
			"lurker_curse_extreme_chance", "0", "lurker_curses_max", "1", "lurker_hunt_chance", "0.25", "lurker_retaliate_chance", "0.35",
			"lurker_strike_damage", "6", "lurker_strike_blows", "3",
			"lurker_swarm_chance", "0", "tribe_rep_gift", "2", "tribe_gift_minutes", "10", "tribe_gift_cost", "1",
			"tribe_rep_bond", "6", "tribe_rep_barter", "1"),
	HARD("hard",
			"atone_base", "150", "atone_per_stack", "75", "atone_death_loss", "0.65",
			"max_charge", "1500", "base_drain", "1.15", "tether_distance_step", "85",
			"multiplier_chance", "0.22", "multiplier_max", "4", "offer_count", "3", "extreme_card_chance", "0.04",
			"lurker_spawn_chance", "0.016", "lurker_cooldown_seconds", "700", "storm_chance", "0.05",
			"fragment_chance", "0.035", "fragment_chance_elite", "0.1", "curse_runes", "1",
			"abyssal_curses", "1", "abyssal_card_chance", "0.01", "abyssal_forced_chance", "0", "abyssal_after_collapses", "5",
			"abyssal_max_carried", "1",
			"lurker_stalk_chance", "0.4", "lurker_thefts", "2", "lurker_claw_damage", "6", "lurker_curse_chance", "0.25",
			"lurker_curse_extreme_chance", "0.15", "lurker_curses_max", "1", "lurker_hunt_chance", "0.4", "lurker_retaliate_chance", "0.5",
			"lurker_strike_damage", "8", "lurker_strike_blows", "4",
			"lurker_swarm_chance", "0", "tribe_rep_gift", "1", "tribe_gift_minutes", "20", "tribe_gift_cost", "2",
			"tribe_rep_bond", "4", "tribe_rep_barter", "0"),
	EXTREME("extreme",
			"atone_base", "200", "atone_per_stack", "100", "atone_death_loss", "0.8",
			"max_charge", "1200", "base_drain", "1.3", "tether_distance_step", "70",
			"multiplier_chance", "0.3", "multiplier_max", "4", "offer_count", "2", "extreme_card_chance", "0.06",
			"lurker_spawn_chance", "0.022", "lurker_cooldown_seconds", "500", "storm_chance", "0.07",
			"fragment_chance", "0.03", "fragment_chance_elite", "0.08", "curse_runes", "1",
			"abyssal_curses", "1", "abyssal_card_chance", "0.025", "abyssal_forced_chance", "0.25", "abyssal_after_collapses", "3",
			"abyssal_max_carried", "2",
			"lurker_stalk_chance", "0.5", "lurker_thefts", "3", "lurker_claw_damage", "8", "lurker_curse_chance", "0.4",
			"lurker_curse_extreme_chance", "0.4", "lurker_curses_max", "2", "lurker_hunt_chance", "0.55", "lurker_retaliate_chance", "0.7",
			"lurker_strike_damage", "10", "lurker_strike_blows", "5",
			"lurker_swarm_chance", "0.15", "tribe_rep_gift", "1", "tribe_gift_minutes", "30", "tribe_gift_cost", "3",
			"tribe_rep_bond", "3", "tribe_rep_barter", "0");

	public final String id;
	/** Setting key -> value as text (as {@link Config#set} takes it), in a fixed order. */
	public final Map<String, String> values;

	Preset(String id, String... keysAndValues) {
		this.id = id;
		Map<String, String> m = new LinkedHashMap<>();
		for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
			m.put(keysAndValues[i], keysAndValues[i + 1]);
		}
		this.values = Collections.unmodifiableMap(m);
	}

	public String nameKey() {
		return "gui.extremedimensions.preset." + id;
	}

	public String descKey() {
		return nameKey() + ".desc";
	}

	public static Preset byId(String id) {
		for (Preset p : values()) {
			if (p.id.equals(id)) {
				return p;
			}
		}
		return null;
	}

	/** True if the given values (key -> text, as Config.snapshot() holds them) match this preset's exactly. */
	public boolean matches(Map<String, String> current) {
		for (Map.Entry<String, String> e : values.entrySet()) {
			String have = current.get(e.getKey());
			try {
				if (have == null || Math.abs(Double.parseDouble(have) - Double.parseDouble(e.getValue())) > 1e-9) {
					return false;
				}
			} catch (NumberFormatException ex) {
				return false;
			}
		}
		return true;
	}

	/** The preset the given values match, or null (custom rules). */
	public static Preset matching(Map<String, String> current) {
		for (Preset p : values()) {
			if (p.matches(current)) {
				return p;
			}
		}
		return null;
	}

	/** Settings the difficulties first set in 3.7.0: a world set to one before takes its values of them on upgrading. */
	static final java.util.Set<String> ADDED_3_7 = java.util.Set.of("lurker_stalk_chance", "lurker_thefts", "lurker_claw_damage",
			"lurker_curse_chance", "lurker_curse_extreme_chance", "lurker_curses_max", "lurker_hunt_chance", "lurker_retaliate_chance",
			"lurker_strike_damage", "lurker_strike_blows");

	/** Settings the difficulties first set in 3.8.0: the swarm, and how hard the tribe's standing is won. */
	static final java.util.Set<String> ADDED_3_8 = java.util.Set.of("lurker_swarm_chance", "tribe_rep_gift", "tribe_gift_minutes",
			"tribe_gift_cost", "tribe_rep_bond", "tribe_rep_barter");

	/**
	 * An older settings file (of layout version {@code from}): if its values (the defaults for any it lacks) match a
	 * difficulty on every setting the difficulty set then, it takes that difficulty's values of the settings added since,
	 * so the world stays on its difficulty.
	 */
	static void upgrade(com.google.gson.JsonObject json, int from) {
		java.util.Set<String> added = new java.util.HashSet<>(ADDED_3_8);
		if (from < 4) {
			added.addAll(ADDED_3_7);
		}
		Map<String, String> fallback = new LinkedHashMap<>();
		for (Config.Setting st : Config.settings()) {
			fallback.put(st.key(), st.fallback());
		}
		for (Preset p : values()) {
			boolean match = true;
			for (Map.Entry<String, String> e : p.values.entrySet()) {
				if (added.contains(e.getKey())) {
					continue;
				}
				String have = json.has(e.getKey()) ? json.get(e.getKey()).getAsString() : fallback.get(e.getKey());
				try {
					match &= have != null && Math.abs(Double.parseDouble(have) - Double.parseDouble(e.getValue())) <= 1e-9;
				} catch (NumberFormatException ex) {
					match = false;
				}
			}
			if (match) {
				for (String key : added) {
					String v = p.values.get(key);
					double d = Double.parseDouble(v);
					if (d == Math.rint(d)) {
						json.addProperty(key, (long) d);
					} else {
						json.addProperty(key, d);
					}
				}
				return;
			}
		}
	}

	/** The world's current values of every setting, as text by key. */
	public static Map<String, String> current() {
		Map<String, String> out = new LinkedHashMap<>();
		for (Config.Setting st : Config.settings()) {
			out.put(st.key(), st.value());
		}
		return out;
	}
}
