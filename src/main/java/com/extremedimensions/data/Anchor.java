package com.extremedimensions.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.extremedimensions.server.Dims;

/**
 * A charged lodestone in the Overworld: a fuel tank for exactly one dimension.
 *
 * <p>Charge is owned per player. What you put in is your time, so every player has their own timer even when
 * several players share the same anchors. The key {@code "*"} holds legacy shared charge from older saves,
 * which anyone may use.
 */
public class Anchor {
	private static final String SHARED = "*";

	public int x, y, z;
	/** {@link Dims#NETHER} or {@link Dims#END}. */
	public String type;
	/** Total charge (sum of {@link #owners}). */
	public double charge = 0;
	/** Charge per player UUID. */
	public Map<String, Double> owners = new HashMap<>();

	// --- runtime only ---
	/** Free path through the air from the anchor to its portal's centre; empty = no path. */
	public transient List<double[]> path = new ArrayList<>();
	/** No free path to the portal: the anchor leaks charge. */
	public transient boolean blocked = false;
	/** Whether the path was checked at least once since loading. */
	public transient boolean checked = false;
	public transient int portalCheck = 0;

	public Anchor() {}

	public Anchor(int x, int y, int z, String type) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.type = type;
	}

	public static String key(int x, int y, int z) {
		return x + "," + y + "," + z;
	}

	public String key() {
		return key(x, y, z);
	}

	public boolean isNether() {
		return Dims.NETHER.equals(type);
	}

	public double distSq(double ox, double oy, double oz) {
		double dx = x + 0.5 - ox;
		double dy = y + 0.5 - oy;
		double dz = z + 0.5 - oz;
		return dx * dx + dy * dy + dz * dz;
	}

	/** Charge this player can use here. */
	public double of(String uid) {
		fixLegacy();
		return owners.getOrDefault(uid, 0.0) + owners.getOrDefault(SHARED, 0.0);
	}

	public void add(String uid, double amount) {
		fixLegacy();
		owners.merge(uid, amount, Double::sum);
		recalc();
	}

	/** Takes up to {@code amount} from this player (own charge first); returns what was taken. */
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

	/** Removes only this player's charge (on death). */
	public void clear(String uid) {
		fixLegacy();
		owners.remove(uid);
		recalc();
	}

	/** Multiplies this player's charge (Leaky Tank). */
	public void scale(String uid, double k) {
		fixLegacy();
		owners.computeIfPresent(uid, (key, v) -> v * k);
		recalc();
	}

	/** Leaks charge proportionally from every owner. */
	public void leak(double amount) {
		fixLegacy();
		if (charge <= 0) {
			return;
		}
		double k = Math.max(0, charge - amount) / charge;
		owners.replaceAll((key, v) -> v * k);
		recalc();
	}

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
}
