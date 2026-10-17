package net.battlehub.arch.mat;

import net.battlehub.arch.Cell;

/**
 * Where a block is being decided: in building coordinates (for gradients that run over the whole building, so
 * they never restart at a module's edge) and in world coordinates (for effects that continue across buildings).
 */
public final class Probe {
	public int bx, by, bz;
	public int wx, wy, wz;
	public Cell cell;

	public Probe set(int bx, int by, int bz, int wx, int wy, int wz, Cell cell) {
		this.bx = bx;
		this.by = by;
		this.bz = bz;
		this.wx = wx;
		this.wy = wy;
		this.wz = wz;
		this.cell = cell;
		return this;
	}
}
