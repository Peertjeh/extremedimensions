package com.extremedimensions.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Five difficulties for the world's rules, from a gentle story to a merciless one. Applying one sets the values below
 * at once (operators only); every other setting stays as it is. {@link #TETHERED} is the mod's own default.
 *
 * <p>What changes: how long atonement takes and how much a death undoes of it, how much charge an anchor holds and
 * how fast it drains, how often offers are multiplied and how many cards they show, how often an Extreme card turns
 * up, how often the Lurkers come and the Wound Storms break, and how often fragments drop.
 */
public enum Preset {
	PILGRIM("pilgrim",
			"atone_base", "60", "atone_per_stack", "25", "atone_death_loss", "0",
			"max_charge", "3600", "base_drain", "0.6", "tether_distance_step", "200",
			"multiplier_chance", "0", "multiplier_max", "2", "offer_count", "4", "extreme_card_chance", "0",
			"lurker_spawn_chance", "0.004", "lurker_cooldown_seconds", "1800", "storm_chance", "0.02",
			"fragment_chance", "0.08", "fragment_chance_elite", "0.2"),
	SEEKER("seeker",
			"atone_base", "90", "atone_per_stack", "40", "atone_death_loss", "0.25",
			"max_charge", "2400", "base_drain", "0.8", "tether_distance_step", "150",
			"multiplier_chance", "0.08", "multiplier_max", "3", "offer_count", "3", "extreme_card_chance", "0.01",
			"lurker_spawn_chance", "0.008", "lurker_cooldown_seconds", "1200", "storm_chance", "0.03",
			"fragment_chance", "0.06", "fragment_chance_elite", "0.16"),
	TETHERED("tethered",
			"atone_base", "120", "atone_per_stack", "60", "atone_death_loss", "0.5",
			"max_charge", "1800", "base_drain", "1", "tether_distance_step", "100",
			"multiplier_chance", "0.15", "multiplier_max", "4", "offer_count", "3", "extreme_card_chance", "0.025",
			"lurker_spawn_chance", "0.012", "lurker_cooldown_seconds", "900", "storm_chance", "0.04",
			"fragment_chance", "0.04", "fragment_chance_elite", "0.12"),
	STARVED("starved",
			"atone_base", "150", "atone_per_stack", "75", "atone_death_loss", "0.65",
			"max_charge", "1500", "base_drain", "1.15", "tether_distance_step", "85",
			"multiplier_chance", "0.22", "multiplier_max", "4", "offer_count", "3", "extreme_card_chance", "0.04",
			"lurker_spawn_chance", "0.016", "lurker_cooldown_seconds", "700", "storm_chance", "0.05",
			"fragment_chance", "0.035", "fragment_chance_elite", "0.1"),
	SEVERED("severed",
			"atone_base", "200", "atone_per_stack", "100", "atone_death_loss", "0.8",
			"max_charge", "1200", "base_drain", "1.3", "tether_distance_step", "70",
			"multiplier_chance", "0.3", "multiplier_max", "4", "offer_count", "2", "extreme_card_chance", "0.06",
			"lurker_spawn_chance", "0.022", "lurker_cooldown_seconds", "500", "storm_chance", "0.07",
			"fragment_chance", "0.03", "fragment_chance_elite", "0.08");

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

	/** The world's current values of every setting, as text by key. */
	public static Map<String, String> current() {
		Map<String, String> out = new LinkedHashMap<>();
		for (Config.Setting st : Config.settings()) {
			out.put(st.key(), st.value());
		}
		return out;
	}
}
