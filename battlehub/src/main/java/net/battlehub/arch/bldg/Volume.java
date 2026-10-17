package net.battlehub.arch.bldg;

import java.util.HashMap;
import java.util.Map;

import net.battlehub.arch.Dir;
import net.battlehub.arch.geom.Footprint;

/**
 * One mass of a building, planned before any wall is drawn: its ground plan, floor and eaves heights, roof, wall
 * thickness, storeys, its rank in the height hierarchy (0 dominant, 1 secondary, 2 accent) and which side is its
 * front. Archetypes plan volumes first and check the massing, then the assembler fills them in.
 */
public final class Volume {
	public enum Role {
		MAIN, WING, NAVE, AISLE, TRANSEPT, APSE, FACADE_TOWER, CROSSING_TOWER, TOWER, TURRET, KEEP, GATEHOUSE, CURTAIN, HALL,
		PORCH, HOUSE, TEMPLE, COLONNADE, PODIUM, COURT
	}

	public enum Roof {
		GABLE, STEEP_GABLE, HIP, PYRAMID, MANSARD, FLAT, BATTLEMENT, CONE, SPIRE, DOME, NONE
	}

	public final String id;
	public final Role role;
	public Footprint fp;
	/** Floor level and the top of the walls (eaves), building y. */
	public int y0, top;
	public Roof roof;
	public int wall;
	public int storeys;
	public int rank;
	/** The main front (null: none), and whether it has the way in. */
	public Dir front;
	public boolean entrance;
	public final Map<String, Object> opts = new HashMap<>();

	public Volume(String id, Role role, Footprint fp, int y0, int top, Roof roof, int wall, int storeys, int rank) {
		this.id = id;
		this.role = role;
		this.fp = fp;
		this.y0 = y0;
		this.top = top;
		this.roof = roof;
		this.wall = wall;
		this.storeys = Math.max(1, storeys);
		this.rank = rank;
	}

	public int height() {
		return top - y0 + 1;
	}

	public Volume front(Dir d, boolean entrance) {
		this.front = d;
		this.entrance = entrance;
		return this;
	}

	public Volume opt(String k, Object v) {
		opts.put(k, v);
		return this;
	}

	public int i(String k, int def) {
		Object o = opts.get(k);
		return o instanceof Number n ? n.intValue() : def;
	}

	public boolean b(String k) {
		return Boolean.TRUE.equals(opts.get(k));
	}

	public String s(String k, String def) {
		Object o = opts.get(k);
		return o == null ? def : o.toString();
	}

	/** Storey heights: the y (building) of each floor, from the ground floor up. */
	public int[] floors() {
		int[] out = new int[storeys];
		int h = height() / storeys;
		for (int i = 0; i < storeys; i++) out[i] = y0 + i * h;
		return out;
	}

	@Override
	public String toString() {
		return id + "(" + role + " " + fp.width() + "x" + fp.depth() + " y" + y0 + ".." + top + " " + roof + ")";
	}
}
