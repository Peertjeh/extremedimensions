package com.extremedimensions;

/**
 * Alle balans-instellingen op een plek. Pas gerust getallen aan en bouw opnieuw.
 * Alle tijden zijn in seconden.
 */
public final class Config {
	private Config() {}

	/** Maximale charge per altaar (30 minuten). */
	public static final double MAX_CHARGE = 1800.0;

	/** Basis-drain: 1 seconde charge per seconde in de dimensie. */
	public static final double BASE_DRAIN = 1.0;

	/** Hoeveel penalty-kaarten je te zien krijgt na een Collapse. */
	public static final int OFFER_COUNT = 3;

	/**
	 * Zoveel seconden duurt een stack van een penalty (10 minuten). De klok loopt alleen als je
	 * actief op expeditie bent in de Nether of End: niet in de Overworld en niet als je AFK staat.
	 */
	public static final int PENALTY_SECONDS = 600;

	/** Na zoveel ticks zonder rondkijken of zonder Config.ACTIVE_MOVE blokken lopen telt een speler als AFK (15 seconden). */
	public static final int IDLE_TICKS = 300;

	/** Zoveel blokken moet je (horizontaal) verplaatsen om als actief te tellen. */
	public static final double ACTIVE_MOVE = 2.0;

	// --- Penalty-sterktes (per stack) ---

	/** Versnelde drain: +75% drain per stack. */
	public static final double ACCELERATED_DRAIN_PER_STACK = 0.75;

	/** Schuld: altaar-brandstof levert per stack maar dit deel op (0.6 = 40% minder). */
	public static final double DEBT_FACTOR = 0.60;

	/** Lekkende tank: per stack verliezen de altaren van je volgende expeditie dit deel. */
	public static final double LEAK_FRACTION = 0.50;

	/** Litteken: HP weg per stack (6 = 3 hartjes). Je houdt altijd minstens 2 hartjes. */
	public static final double SCAR_HEALTH = 6.0;

	/** Gebarsten pantser: armor-punten weg per stack. */
	public static final double CRACKED_ARMOR = 4.0;

	/** Zware benen: zoveel langzamer per stack. */
	public static final double HEAVY_LEGS = 0.15;

	/** Onhandige handen: zoveel langzamer minen per stack. */
	public static final double CLUMSY_HANDS = 0.35;

	/** Rouw: duur in seconden per stack. */
	public static final int MOURNING_SECONDS = 600;

	/** Elke zoveel ticks sturen we de status naar de speler (10 = 2x per seconde). */
	public static final int SYNC_INTERVAL_TICKS = 10;

	/** Charge (seconden) die een Ember Tether of Void Tether in een keer geeft. */
	public static final double TETHER_ITEM_CHARGE = 600.0;

	/** Een altaar werkt alleen binnen zoveel blokken van een portaal (Nether Portal of End Portal). */
	public static final int PORTAL_RANGE = 5;

	/** Binnen zoveel blokken van een geladen altaar zie je linksboven de tank. */
	public static final double HUD_RANGE = 10.0;

	/** Altaren binnen zoveel blokken van het portaal waar je doorheen gaat, voeden je expeditie. */
	public static final double LINK_RANGE = 8.0;

	/** Spelers binnen zoveel blokken zien de deeltjes van een altaar. */
	public static final double PARTICLE_RANGE = 48.0;

	/** Een altaar zonder vrije weg naar zijn portaal verliest zoveel charge per seconde. */
	public static final double BLOCKED_LEAK_PER_SECOND = 1.0;

	/** Ticks na het respawnen voordat het penalty-scherm opent (zodat je de melding ziet). */
	public static final int OFFER_DELAY_TICKS = 70;

	/** Ticks wachten voor een nieuwe poort-check na een bounce. */
	public static final int BOUNCE_GRACE_TICKS = 40;
}
