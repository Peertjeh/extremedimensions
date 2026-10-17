package net.battlehub.arch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One planned block: its role, its form (with direction, half and axis), how strongly it holds its place against
 * later drawing ({@link #prio}), a few flags and, for decoration, an exact block name. Cells are interned, so a
 * plan stores a small number per block.
 *
 * @param variant picks among a role's alternatives (a stained-glass colour, a second trim) without naming a block
 */
public record Cell(Slot slot, Shape shape, Dir facing, Half half, char axis, int prio, int flags, int variant, String literal) {
	/** Priorities: later drawing replaces a block only with an equal or higher priority. */
	public static final int TERRAIN = 0, FILL = 1, BODY = 2, DETAIL = 3, OPEN = 4, FEATURE = 5, FORCE = 7;

	/** Optional decoration: left out at low detail, never needed to hold anything up. */
	public static final int DECOR = 1;
	/** Meant to float (magic): the structure check leaves it alone. */
	public static final int FLOAT = 2;
	/** Inside a building (furniture, floors): weathering leaves it alone. */
	public static final int INSIDE = 4;
	/** Never damaged by ruin (paths players need). */
	public static final int KEEP = 8;

	private static final Map<Cell, Integer> IDS = new ConcurrentHashMap<>();
	private static final List<Cell> BY_ID = new ArrayList<>();

	public static final Cell AIR = new Cell(Slot.AIR, Shape.CUBE, null, null, (char) 0, OPEN, 0, 0, null);

	public static Cell of(Slot slot) {
		return new Cell(slot, Shape.CUBE, null, null, (char) 0, BODY, 0, 0, null);
	}

	public static Cell of(Slot slot, int prio) {
		return new Cell(slot, Shape.CUBE, null, null, (char) 0, prio, 0, 0, null);
	}

	public static Cell stairs(Slot slot, Dir facing, boolean top, int prio) {
		return new Cell(slot, Shape.STAIRS, facing, top ? Half.TOP : Half.BOTTOM, (char) 0, prio, 0, 0, null);
	}

	public static Cell slab(Slot slot, boolean top, int prio) {
		return new Cell(slot, Shape.SLAB, null, top ? Half.TOP : Half.BOTTOM, (char) 0, prio, 0, 0, null);
	}

	public static Cell shape(Slot slot, Shape shape, int prio) {
		return new Cell(slot, shape, null, null, (char) 0, prio, 0, 0, null);
	}

	public static Cell log(Slot slot, char axis, int prio) {
		return new Cell(slot, Shape.LOG, null, null, axis, prio, 0, 0, null);
	}

	/** An exact block, e.g. "lantern[hanging=true]"; directions in it are turned with the frame. */
	public static Cell literal(String id, int prio) {
		return new Cell(Slot.LITERAL, Shape.LITERAL, null, null, (char) 0, prio, 0, 0, id);
	}

	public static Cell air(int prio) {
		return new Cell(Slot.AIR, Shape.CUBE, null, null, (char) 0, prio, 0, 0, null);
	}

	public Cell prio(int p) {
		return new Cell(slot, shape, facing, half, axis, p, flags, variant, literal);
	}

	public Cell flag(int f) {
		return new Cell(slot, shape, facing, half, axis, prio, flags | f, variant, literal);
	}

	public Cell variant(int v) {
		return new Cell(slot, shape, facing, half, axis, prio, flags, v, literal);
	}

	public Cell facing(Dir d) {
		return new Cell(slot, shape, d, half, axis, prio, flags, variant, literal);
	}

	public Cell slot(Slot s) {
		return new Cell(s, shape, facing, half, axis, prio, flags, variant, literal);
	}

	public boolean is(int flag) {
		return (flags & flag) != 0;
	}

	public boolean air() {
		return slot == Slot.AIR;
	}

	/** Takes up the whole block (for connections, support and the structure check). */
	public boolean solid() {
		return !air() && slot != Slot.WATER && slot != Slot.LEAVES && (shape.full() || shape == Shape.STAIRS || shape == Shape.SLAB && half == Half.DOUBLE);
	}

	/** This cell in a parent frame: its direction and axis turned. */
	public Cell in(Xform f) {
		if (f.turns() == 0 && !f.mirror()) return this;
		Dir d = f.dir(facing);
		char a = axis;
		if (f.swapsAxes() && (a == 'x' || a == 'z')) a = a == 'x' ? 'z' : 'x';
		String lit = literal == null ? null : Props.turn(literal, f);
		return new Cell(slot, shape, d, half, a, prio, flags, variant, lit);
	}

	public static int id(Cell c) {
		Integer i = IDS.get(c);
		if (i != null) return i;
		synchronized (BY_ID) {
			i = IDS.get(c);
			if (i != null) return i;
			int n = BY_ID.size();
			BY_ID.add(c);
			IDS.put(c, n);
			return n;
		}
	}

	public static Cell byId(int id) {
		synchronized (BY_ID) {
			return BY_ID.get(id);
		}
	}
}
