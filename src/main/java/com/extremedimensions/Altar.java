package com.extremedimensions;

import java.util.ArrayList;
import java.util.List;

/** Een geladen Lodestone in de Overworld: een tank voor precies een dimensie. */
public class Altar {
	public int x, y, z;
	/** ServerLogic.NETHER of ServerLogic.END */
	public String type;
	public double charge = 0;

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
