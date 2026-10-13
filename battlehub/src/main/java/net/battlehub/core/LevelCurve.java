package net.battlehub.core;

/**
 * XP needed per level: {@code base + increment * (level - 1)}, optionally scaled by {@code growth^(level - 1)}.
 * Level 1 starts at 0 total XP.
 */
public final class LevelCurve {
	public record Progress(int level, long xpIntoLevel, long xpForNext, boolean maxed) {
		public double fraction() {
			return maxed || xpForNext <= 0 ? 1.0 : Math.min(1.0, (double) xpIntoLevel / xpForNext);
		}
	}

	private final int base;
	private final int increment;
	private final double growth;
	private final int maxLevel;

	public LevelCurve(int base, int increment, double growth, int maxLevel) {
		this.base = Math.max(1, base);
		this.increment = Math.max(0, increment);
		this.growth = growth <= 0 ? 1.0 : growth;
		this.maxLevel = Math.max(1, maxLevel);
	}

	/** XP needed to go from {@code level} to {@code level + 1}. */
	public long xpToNext(int level) {
		double v = (base + (double) increment * (level - 1)) * Math.pow(growth, level - 1);
		return Math.max(1L, Math.round(v));
	}

	/** Total XP needed to reach {@code level} from zero. */
	public long totalFor(int level) {
		long sum = 0;
		for (int l = 1; l < Math.min(level, maxLevel); l++) {
			sum += xpToNext(l);
		}
		return sum;
	}

	public Progress progress(long totalXp) {
		long xp = Math.max(0, totalXp);
		int level = 1;
		while (level < maxLevel) {
			long need = xpToNext(level);
			if (xp < need) {
				return new Progress(level, xp, need, false);
			}
			xp -= need;
			level++;
		}
		return new Progress(maxLevel, 0, 0, true);
	}

	public int levelFor(long totalXp) {
		return progress(totalXp).level();
	}

	public int maxLevel() {
		return maxLevel;
	}
}
