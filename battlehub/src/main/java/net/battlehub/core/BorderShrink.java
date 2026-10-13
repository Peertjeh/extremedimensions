package net.battlehub.core;

/** Last Man Standing border: full size during the delay, then a linear shrink to the end size. */
public record BorderShrink(double startSize, double endSize, int delayTicks, int shrinkTicks) {
	public double sizeAt(long ticks) {
		if (ticks <= delayTicks) {
			return startSize;
		}
		if (shrinkTicks <= 0 || ticks >= (long) delayTicks + shrinkTicks) {
			return endSize;
		}
		double t = (ticks - delayTicks) / (double) shrinkTicks;
		return startSize + (endSize - startSize) * t;
	}

	public boolean shrinking(long ticks) {
		return ticks > delayTicks && ticks < (long) delayTicks + shrinkTicks;
	}

	/** How far (x, z) is outside a square border of {@code size} centred on (cx, cz); 0 when inside. */
	public static double outside(double x, double z, double cx, double cz, double size) {
		double half = size / 2.0;
		double dx = Math.max(0, Math.abs(x - cx) - half);
		double dz = Math.max(0, Math.abs(z - cz) - half);
		return Math.max(dx, dz);
	}
}
