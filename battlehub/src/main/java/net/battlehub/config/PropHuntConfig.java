package net.battlehub.config;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code config/battlehub/prophunt.json}: rounds, phase lengths, abilities, penalties and points, and the blocks
 * props may turn into. Times are in seconds.
 */
public final class PropHuntConfig {
	/** Files from before 1.5 have none (0): they get the new prop balance. */
	public int configVersion = 0;
	public int rounds = 2;
	public int hideSeconds = 30;
	public int huntSeconds = 180;
	public int roundEndSeconds = 6;
	/** Share of the players who hunt (at least one hunter and one prop). */
	public double hunterRatio = 0.25;
	/** A found prop joins the hunters for the rest of the round. */
	public boolean propsJoinHunters = true;
	public int hunterRespawnSeconds = 4;
	/** Health of props (20 = ten hearts): two hits with the hunter's sword at 10. */
	public int propHealth = 10;
	/** Walking speed of props compared to normal (1 = normal, 0.75 = a quarter slower). */
	public double propSpeed = 0.75;
	/** Health hunters lose for swinging at thin air (they always lose the miss points). */
	public double airMissDamage = 0;
	/** Health hunters lose for hitting a block that is not a prop. */
	public double wrongBlockDamage = 2;
	/** Health a hunter gets back for hitting a prop. */
	public double hitHeal = 3;
	/** Standing still this long turns a prop into a solid block. */
	public int solidifySeconds = 3;
	public int tauntCooldownSeconds = 15;
	public int decoyCooldownSeconds = 40;
	public int decoySeconds = 20;
	/** Hunters start with this many arrows and get more for every prop they find. */
	public int hunterArrows = 2;
	public int arrowsPerKill = 2;
	public Points points = new Points();
	/** Blocks props may disguise as (right-click one in the arena, or pick from the list). */
	public List<String> disguises = defaultDisguises();

	public static final class Points {
		public int hunterKill = 100;
		public int hunterHit = 10;
		public int hunterMiss = -5;
		public int propPer10Seconds = 5;
		public int propTaunt = 15;
		public int propDecoyFooled = 25;
		public int propSurvivedRound = 150;
	}

	static List<String> defaultDisguises() {
		List<String> l = new ArrayList<>();
		for (String b : new String[] {"barrel", "chest", "crafting_table", "bookshelf", "hay_block", "pumpkin", "carved_pumpkin",
				"melon", "oak_log", "spruce_log", "composter", "cauldron", "anvil", "flower_pot", "lantern", "bee_nest", "target",
				"jukebox", "note_block", "furnace", "smoker", "blast_furnace", "loom", "fletching_table", "smithing_table",
				"cartography_table", "lectern", "grindstone", "stonecutter", "oak_planks", "cobblestone", "mossy_cobblestone",
				"stone_bricks", "white_wool", "red_wool", "dried_kelp_block", "honeycomb_block", "oak_leaves", "moss_block", "dirt",
				"coarse_dirt", "sand", "gravel", "terracotta", "decorated_pot", "chiseled_bookshelf", "candle", "cake"}) {
			l.add("minecraft:" + b);
		}
		return l;
	}

	public PropHuntConfig normalize() {
		if (configVersion < 2) {
			// 1.5: props were too fast and too tough, and swinging at air hurt the hunters
			if (propHealth == 20) propHealth = 10;
			if (airMissDamage == 1) airMissDamage = 0;
			configVersion = 2;
		}
		propSpeed = Math.max(0.3, Math.min(1.5, propSpeed));
		airMissDamage = Math.max(0, airMissDamage);
		wrongBlockDamage = Math.max(0, wrongBlockDamage);
		rounds = Math.max(1, Math.min(10, rounds));
		hideSeconds = Math.max(5, hideSeconds);
		huntSeconds = Math.max(20, huntSeconds);
		roundEndSeconds = Math.max(2, roundEndSeconds);
		hunterRatio = Math.max(0, Math.min(1, hunterRatio));
		hunterRespawnSeconds = Math.max(1, hunterRespawnSeconds);
		propHealth = Math.max(1, Math.min(40, propHealth));
		solidifySeconds = Math.max(1, solidifySeconds);
		tauntCooldownSeconds = Math.max(1, tauntCooldownSeconds);
		decoyCooldownSeconds = Math.max(1, decoyCooldownSeconds);
		decoySeconds = Math.max(1, decoySeconds);
		hunterArrows = Math.max(0, hunterArrows);
		arrowsPerKill = Math.max(0, arrowsPerKill);
		if (points == null) points = new Points();
		if (disguises == null || disguises.isEmpty()) disguises = defaultDisguises();
		return this;
	}
}
