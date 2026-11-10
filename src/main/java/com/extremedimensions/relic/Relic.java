package com.extremedimensions.relic;

import com.extremedimensions.config.Config;
import com.extremedimensions.penalty.Penalty;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The relics: one at a time is bound to the Tether, and each brings a gift and a price. Their numbers live in the
 * config ({@code relic_*}); the texts in the language files under {@code item.extremedimensions.<id>} with
 * {@code .gift} and {@code .price} formatted with {@link #giftArgs()} and {@link #priceArgs()}. Each is of a tier,
 * which sets how often it is found (see {@link RelicService#random(boolean)}) and the colour of its name.
 */
public enum Relic {
	// --- common ---------------------------------------------------------------------------------
	/** Less drain in the Nether, more in the End. */
	EMBER_HEART("ember_heart", Tier.COMMON),
	/** Less drain in the End, more in the Nether. */
	PALE_SHARD("pale_shard", Tier.COMMON),
	/** More experience, levels taken on every safe return. */
	TITHE_BELL("tithe_bell", Tier.COMMON),
	/** Swifter on an expedition and in the Seam, hungrier there. */
	PILGRIMS_SANDAL("pilgrims_sandal", Tier.COMMON),
	/** Less drain everywhere beyond the wound, a curse more from every Collapse. */
	LODESTONE_CHIP("lodestone_chip", Tier.COMMON),

	// --- uncommon -------------------------------------------------------------------------------
	/** Longer reach, a heart less. */
	HUNGERING_HAND("hungering_hand", Tier.UNCOMMON),
	/** Long falls without harm, less armour. */
	FEATHERBONE("featherbone", Tier.UNCOMMON),
	/** Fall again sooner after an ally lifts you, bleed out sooner while fallen. */
	KINDRED_KNOT("kindred_knot", Tier.UNCOMMON),
	/** Who strikes you up close is hurt back; your own blows hit softer. */
	THORNWARD_BARK("thornward_bark", Tier.UNCOMMON),
	/** The young barter for less; the tribe's goodwill comes slower. */
	HOLLOW_WHISTLE("hollow_whistle", Tier.UNCOMMON),

	// --- rare -----------------------------------------------------------------------------------
	/** More fragments from wild kills, less atonement from kills. */
	KEEPERS_EYE("keepers_eye", Tier.RARE),
	/** More time in the Seam, monsters notice you from farther at night. */
	SEAM_LANTERN("seam_lantern", Tier.RARE),
	/** Lurkers cannot take items or armour, but come more often. */
	LURKERS_TOOTH("lurkers_tooth", Tier.RARE),
	/** Once an expedition a killing blow leaves you standing; less maximum health. */
	LAST_STITCH("last_stitch", Tier.RARE),
	/** Melee blows heal you a little; hunger always runs faster. */
	HUNGERS_FANG("hungers_fang", Tier.RARE),

	// --- epic -----------------------------------------------------------------------------------
	/** A few seconds' grace when the charge runs out beyond the wound; each anchor holds less for you. Only found. */
	HELDS_CHAIN("helds_chain", Tier.EPIC),
	/** Atonement doubled; the curses of deaths and Lurkers twice over, an Elder's three times. */
	DEBTORS_SEAL("debtors_seal", Tier.EPIC),
	/** The Elders cannot pull you from the air or bind your wings; more damage taken, an elytra worn through fast. */
	UNBOUND_PINION("unbound_pinion", Tier.EPIC);

	/** How rare a relic is: how often it is found, and the colour of its name. */
	public enum Tier {
		COMMON(ChatFormatting.WHITE), UNCOMMON(ChatFormatting.YELLOW), RARE(ChatFormatting.AQUA), EPIC(ChatFormatting.LIGHT_PURPLE);

		/** The colour of the tier's line in a tooltip (the item's name takes the same from its rarity). */
		public final ChatFormatting colour;

		Tier(ChatFormatting colour) {
			this.colour = colour;
		}

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		/** The line under a relic's name ("Rare relic"). */
		public String nameKey() {
			return "tooltip.extremedimensions.relic.tier." + id();
		}

		/** How often this tier is found among ordinary finds, or from a great foe (the config's relic_*weight_*). */
		public int weight(boolean great) {
			return Math.max(0, switch (this) {
				case COMMON -> great ? Config.RELIC_GREAT_WEIGHT_COMMON : Config.RELIC_WEIGHT_COMMON;
				case UNCOMMON -> great ? Config.RELIC_GREAT_WEIGHT_UNCOMMON : Config.RELIC_WEIGHT_UNCOMMON;
				case RARE -> great ? Config.RELIC_GREAT_WEIGHT_RARE : Config.RELIC_WEIGHT_RARE;
				case EPIC -> great ? Config.RELIC_GREAT_WEIGHT_EPIC : Config.RELIC_WEIGHT_EPIC;
			});
		}
	}

	public final String id;
	public final Tier tier;

	Relic(String id, Tier tier) {
		this.id = id;
		this.tier = tier;
	}

	public String nameKey() {
		return "item.extremedimensions." + id;
	}

	public String giftKey() {
		return nameKey() + ".gift";
	}

	public String priceKey() {
		return nameKey() + ".price";
	}

	/** Whether it can be found at random (the Held's Chain lies only where the Held left it). */
	public boolean findable() {
		return this != HELDS_CHAIN;
	}

	private static String pct(double factor) {
		return Penalty.display(Math.round(Math.abs(factor - 1) * 100));
	}

	/** A level as the game writes it (Speed II). */
	private static Component level(int n) {
		return Component.translatable("enchantment.level." + Math.max(1, n));
	}

	/** The numbers in the gift's text, from the config. */
	public Object[] giftArgs() {
		return switch (this) {
			case EMBER_HEART -> new Object[] {pct(Config.RELIC_EMBER_HEART_NETHER)};
			case PALE_SHARD -> new Object[] {pct(Config.RELIC_PALE_SHARD_END)};
			case HUNGERING_HAND -> new Object[] {Penalty.display(Config.RELIC_HUNGERING_HAND_REACH)};
			case FEATHERBONE -> new Object[] {Penalty.display(Config.RELIC_FEATHERBONE_FALL)};
			case KEEPERS_EYE -> new Object[] {Penalty.display(Config.RELIC_KEEPERS_EYE_FRAGMENTS)};
			case TITHE_BELL -> new Object[] {Penalty.display(Math.round(Config.RELIC_TITHE_BELL_XP * 100))};
			case LURKERS_TOOTH -> new Object[] {};
			case SEAM_LANTERN -> new Object[] {Config.RELIC_SEAM_LANTERN_SECONDS};
			case KINDRED_KNOT -> new Object[] {pct(Config.RELIC_KINDRED_KNOT_COOLDOWN)};
			case HELDS_CHAIN -> new Object[] {Config.RELIC_HELDS_CHAIN_GRACE};
			case PILGRIMS_SANDAL -> new Object[] {level(Config.RELIC_PILGRIMS_SANDAL_SPEED)};
			case LODESTONE_CHIP -> new Object[] {pct(Config.RELIC_LODESTONE_CHIP_DRAIN)};
			case THORNWARD_BARK -> new Object[] {Penalty.display(Config.RELIC_THORNWARD_BARK_THORNS / 2)};
			case HOLLOW_WHISTLE -> new Object[] {pct(Config.RELIC_HOLLOW_WHISTLE_PRICE)};
			case LAST_STITCH -> new Object[] {Penalty.display(Config.RELIC_LAST_STITCH_HEALTH / 2)};
			case HUNGERS_FANG -> new Object[] {Penalty.display(Math.round(Config.RELIC_HUNGERS_FANG_LEECH * 100))};
			case DEBTORS_SEAL -> new Object[] {Penalty.display(Config.RELIC_DEBTORS_SEAL_ATONEMENT)};
			case UNBOUND_PINION -> new Object[] {};
		};
	}

	/** The numbers in the price's text, from the config. */
	public Object[] priceArgs() {
		return switch (this) {
			case EMBER_HEART -> new Object[] {pct(Config.RELIC_EMBER_HEART_END)};
			case PALE_SHARD -> new Object[] {pct(Config.RELIC_PALE_SHARD_NETHER)};
			case HUNGERING_HAND -> new Object[] {Penalty.display(Config.RELIC_HUNGERING_HAND_HEALTH / 2)};
			case FEATHERBONE -> new Object[] {Penalty.display(Config.RELIC_FEATHERBONE_ARMOR)};
			case KEEPERS_EYE -> new Object[] {pct(Config.RELIC_KEEPERS_EYE_ATONEMENT)};
			case TITHE_BELL -> new Object[] {Config.RELIC_TITHE_BELL_LEVELS};
			case LURKERS_TOOTH -> new Object[] {Penalty.display(Config.RELIC_LURKERS_TOOTH_OFTEN)};
			case SEAM_LANTERN -> new Object[] {Penalty.display(Math.round(Config.RELIC_SEAM_LANTERN_NOTICE * 100))};
			case KINDRED_KNOT -> new Object[] {pct(Config.RELIC_KINDRED_KNOT_BLEED)};
			case HELDS_CHAIN -> new Object[] {Penalty.display(Config.RELIC_HELDS_CHAIN_CAP / 60.0)};
			case PILGRIMS_SANDAL -> new Object[] {pct(Config.RELIC_PILGRIMS_SANDAL_HUNGER)};
			case LODESTONE_CHIP -> new Object[] {Config.RELIC_LODESTONE_CHIP_COLLAPSE};
			case THORNWARD_BARK -> new Object[] {pct(Config.RELIC_THORNWARD_BARK_MELEE)};
			case HOLLOW_WHISTLE -> new Object[] {pct(Config.RELIC_HOLLOW_WHISTLE_STANDING)};
			case LAST_STITCH -> new Object[] {Penalty.display(Config.RELIC_LAST_STITCH_MAX_HEALTH / 2)};
			case HUNGERS_FANG -> new Object[] {pct(Config.RELIC_HUNGERS_FANG_HUNGER)};
			case DEBTORS_SEAL -> new Object[] {Config.RELIC_DEBTORS_SEAL_CURSES, Config.RELIC_DEBTORS_SEAL_BOSS_CURSES};
			case UNBOUND_PINION -> new Object[] {pct(Config.RELIC_UNBOUND_PINION_DAMAGE), Config.RELIC_UNBOUND_PINION_WEAR};
		};
	}

	public static Relic byId(String id) {
		if (id == null) {
			return null;
		}
		for (Relic r : values()) {
			if (r.id.equals(id)) {
				return r;
			}
		}
		return null;
	}
}
