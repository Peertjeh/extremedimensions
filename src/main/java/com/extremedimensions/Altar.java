package com.extremedimensions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Een geladen Lodestone in de Overworld: een tank voor precies een dimensie. */
public class Altar {
	public int x, y, z;
	/** ServerLogic.NETHER of ServerLogic.END */
	public String type;
	/** Totale charge in dit altaar (som van owners). */
	public double charge = 0;
	/**
	 * Charge per speler (UUID): wat je zelf in een anker stopt, is jouw tijd.
	 * Zo heeft elke speler zijn eigen timer, ook als jullie dezelfde ankers gebruiken.
	 * Sleutel "*" = oude gedeelde charge van voor deze versie, die mag iedereen gebruiken.
	 */
	public Map<String, Double> owners = new HashMap<>();

	// --- alleen tijdens het spelen ---
	/** Vrije weg door de lucht van het altaar naar het midden van het portaal (punten x, y, z). Leeg = geen weg. */
	public transient List<double[]> path = new ArrayList<>();
	/** Geen vrije weg naar een portaal: het altaar lekt charge. */
	public transient boolean blocked = false;
	/** Of de weg al eens is gecontroleerd sinds het laden. */
	public transient boolean checked = false;
	public transient int portalCheck = 0;

	public Altar() {}

	public Altar(int x, int y, int z, String type) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.type = type;
	}

	/** Charge die deze speler hier kan gebruiken. */
	public double of(String uid) {
		fixLegacy();
		return owners.getOrDefault(uid, 0.0) + owners.getOrDefault(SHARED, 0.0);
	}

	public void add(String uid, double amount) {
		fixLegacy();
		owners.merge(uid, amount, Double::sum);
		recalc();
	}

	/** Haalt hooguit amount weg bij deze speler (eerst zijn eigen charge); geeft terug hoeveel. */
	public double take(String uid, double amount) {
		fixLegacy();
		double taken = 0;
		for (String k : new String[] {uid, SHARED}) {
			double have = owners.getOrDefault(k, 0.0);
			double t = Math.min(have, amount - taken);
			if (t > 0) {
				owners.put(k, have - t);
				taken += t;
			}
		}
		recalc();
		return taken;
	}

	/** Alleen de charge van deze speler weg (bij zijn dood). */
	public void clear(String uid) {
		fixLegacy();
		owners.remove(uid);
		recalc();
	}

	/** De charge van deze speler vermenigvuldigen (Leaky Tank). */
	public void scale(String uid, double k) {
		fixLegacy();
		owners.computeIfPresent(uid, (key, v) -> v * k);
		recalc();
	}

	/** Lekken: alle spelers verliezen naar verhouding. */
	public void leak(double amount) {
		fixLegacy();
		if (charge <= 0) {
			return;
		}
		double k = Math.max(0, charge - amount) / charge;
		owners.replaceAll((key, v) -> v * k);
		recalc();
	}

	private static final String SHARED = "*";

	private void fixLegacy() {
		if (owners == null) {
			owners = new HashMap<>();
		}
		if (owners.isEmpty() && charge > 0) {
			owners.put(SHARED, charge);
		}
	}

	private void recalc() {
		owners.values().removeIf(v -> v < 0.001);
		double sum = 0;
		for (double v : owners.values()) {
			sum += v;
		}
		charge = sum;
	}

	public String key() {
		return key(x, y, z);
	}

	public static String key(int x, int y, int z) {
		return x + "," + y + "," + z;
	}

	public boolean isNether() {
		return ServerLogic.NETHER.equals(type);
	}

	public double distSq(double ox, double oy, double oz) {
		double dx = x + 0.5 - ox;
		double dy = y + 0.5 - oy;
		double dz = z + 0.5 - oz;
		return dx * dx + dy * dy + dz * dz;
	}
}
