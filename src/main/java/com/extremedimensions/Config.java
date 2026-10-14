package com.extremedimensions;

/**
 * Alle balans-instellingen op een plek. Pas gerust getallen aan en bouw opnieuw.
 * Alle tijden zijn in seconden.
 */
public final class Config {
	private Config() {}

	/** Maximale tank per dimensie (30 minuten). */
	public static final double MAX_CHARGE = 1800.0;

	/** Basis-drain: 1 seconde charge per seconde in de dimensie. */
	public static final double BASE_DRAIN = 1.0;

	/** Hoeveel penalty-kaarten je te zien krijgt na een Collapse. */
	public static final int OFFER_COUNT = 3;

	/** Aantal geslaagde expedities waarna een penalty verdwijnt. */
	public static final int PENALTY_RUNS = 2;

	/** Versnelde drain-penalty: x keer zo snel. */
	public static final double ACCELERATED_DRAIN_FACTOR = 1.5;

	/** Schuld-penalty: je krijgt maar dit deel van de charge uit items. */
	public static final double DEBT_FACTOR = 0.75;

	/** Lekkende tank: je verliest dit deel van je charge. */
	public static final double LEAK_FRACTION = 0.30;

	/** Litteken: aantal HP weg (4 = 2 hartjes). */
	public static final double SCAR_HEALTH = 4.0;

	/** Elke zoveel ticks sturen we de status naar de speler (10 = 2x per seconde). */
	public static final int SYNC_INTERVAL_TICKS = 10;

	/** Charge (seconden) die een Ember Tether of Void Tether in een keer geeft. */
	public static final double TETHER_ITEM_CHARGE = 600.0;

	/** Ticks wachten voor een nieuwe poort-check na een bounce. */
	public static final int BOUNCE_GRACE_TICKS = 40;

	/**
	 * Werelden van andere mods waar Extreme Dimensions helemaal stilstaat (BattleHub: hub en arena's).
	 * Daar loopt geen charge leeg, telt geen dimensiewissel, werkt geen penalty en is de HUD verborgen;
	 * bij terugkomst gaat alles verder waar het was.
	 */
	public static final java.util.Set<String> PAUSED_NAMESPACES = java.util.Set.of("battlehub");
}
