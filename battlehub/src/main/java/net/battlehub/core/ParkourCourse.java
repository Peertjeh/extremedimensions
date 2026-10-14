package net.battlehub.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Generates a parkour course: platforms heading east with turns north and south, never back west. Every jump is
 * checked against what a player can reach (height difference and gap, per difficulty), and parts of the course
 * that are far apart along the route are kept too far apart in space to jump between, so there are no shortcuts.
 * Coordinates are relative to the start platform centre; y is the block the player stands on.
 */
public final class ParkourCourse {
	public enum Kind { START, JUMP, CHECKPOINT, FINISH }

	/** What a platform is made of. */
	public enum Style {
		/** Full blocks. */
		SOLID,
		/** A bottom slab: you stand half a block lower. */
		SLAB,
		/** A single fence post: you stand half a block higher, and it is tiny. */
		FENCE,
		/** Slippery ice on top. */
		ICE,
		/** A column climbed by a ladder on the side the course comes from. */
		LADDER
	}

	public enum Difficulty {
		EASY(30, 6), MEDIUM(50, 8), HARD(70, 10), EXTREME(90, 12);

		public final int defaultJumps;
		public final int checkpointEvery;

		Difficulty(int jumps, int checkpointEvery) {
			this.defaultJumps = jumps;
			this.checkpointEvery = checkpointEvery;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** "easy", "medium", "hard", "extreme"; "random", "" or anything else picks one at random. */
		public static Difficulty parse(String id, Random random) {
			if (id != null) {
				for (Difficulty d : values()) {
					if (d.id().equalsIgnoreCase(id.trim())) return d;
				}
			}
			Difficulty[] all = values();
			return all[random.nextInt(all.length)];
		}
	}

	/** Headings: east, south (+z), north (-z). The course never heads west, so it cannot cross itself. */
	public static final int EAST = 0;
	public static final int SOUTH = 1;
	public static final int NORTH = 2;

	public static int dx(int dir) {
		return dir == EAST ? 1 : 0;
	}

	public static int dz(int dir) {
		return dir == SOUTH ? 1 : (dir == NORTH ? -1 : 0);
	}

	/**
	 * A {@code size} x {@code size} platform centred on (x, z) with its top block at y. For LADDER, the column is
	 * {@code climb} blocks high and the ladder hangs on the side facing back along {@code dir}.
	 */
	public record Platform(int x, int y, int z, int size, Kind kind, int checkpoint, Style style, int dir, int climb) {
		public int half() {
			return size / 2;
		}

		/** Height the player's feet are at when standing on it, in half blocks. */
		public int standHalves() {
			return 2 * y + switch (style) {
				case SLAB -> 1;
				case FENCE -> 3;
				default -> 2;
			};
		}

		public double standY() {
			return standHalves() / 2.0;
		}
	}

	private ParkourCourse() {}

	/** Largest air distance (blocks) a jump may have, by height change (half blocks) and difficulty. */
	static double maxDistance(int dhHalves, Difficulty d) {
		double[] row;
		if (dhHalves >= 2) {
			row = new double[] {1.0, 1.5, 2.0, 2.3};
		} else if (dhHalves == 1) {
			row = new double[] {2.0, 2.0, 2.3, 2.9};
		} else if (dhHalves == 0) {
			row = new double[] {2.0, 2.9, 3.0, 3.2};
		} else if (dhHalves >= -2) {
			row = new double[] {2.0, 3.0, 3.2, 3.7};
		} else {
			row = new double[] {2.0, 3.0, 3.4, 4.0};
		}
		return row[d.ordinal()];
	}

	/** Air blocks between the edges of two platforms along X and Z (0 when they overlap on that axis). */
	static int airX(Platform a, Platform b) {
		return Math.max(0, Math.abs(b.x() - a.x()) - a.half() - b.half() - 1);
	}

	static int airZ(Platform a, Platform b) {
		return Math.max(0, Math.abs(b.z() - a.z()) - a.half() - b.half() - 1);
	}

	/** Straight-line air distance between the nearest edges. */
	public static double distance(Platform a, Platform b) {
		int ax = airX(a, b);
		int az = airZ(a, b);
		return Math.sqrt(ax * ax + az * az);
	}

	/** Can a player get from a to b at this difficulty? */
	public static boolean jumpable(Platform a, Platform b, Difficulty d) {
		if (b.style() == Style.LADDER) {
			// Walk or jump to the ladder, then climb: the ladder starts at the player's feet
			int gap = airX(a, b) + airZ(a, b);
			int ladderBottom = b.y() - b.climb() + 1;
			int feet = a.standHalves() / 2;
			return b.climb() >= 2 && b.climb() <= 6 && gap <= ladderGap(d) + 1 && ladderBottom <= feet && ladderBottom >= feet - 1
					&& b.size() == 1;
		}
		int dh = b.standHalves() - a.standHalves();
		if (dh > 2 || dh < -8) {
			return false;
		}
		double dist = distance(a, b);
		double max = maxDistance(dh, d);
		if (b.style() == Style.FENCE || a.style() == Style.FENCE) {
			max -= 0.5;
		}
		if (b.size() == 1 && a.size() == 1 && d.ordinal() < Difficulty.HARD.ordinal()) {
			max = Math.min(max, 2.0);
		}
		return dist <= max + 1e-9;
	}

	/** Air blocks between the platform before a ladder and the ladder itself. */
	static int ladderGap(Difficulty d) {
		return switch (d) {
			case EASY, MEDIUM -> 0;
			case HARD -> 1;
			case EXTREME -> 2;
		};
	}

	/**
	 * Is the later platform {@code b} out of reach from the earlier platform {@code a}? Straight-line air distance of
	 * at least {@link #SEPARATION} (nobody jumps 5 blocks), more when {@code a} is higher (falls reach further).
	 */
	public static boolean separated(Platform a, Platform b) {
		if (b.style() == Style.LADDER) {
			// The foot of the ladder could be grabbed too
			Platform foot = new Platform(b.x() - dx(b.dir()), b.y() - b.climb(), b.z() - dz(b.dir()), 1, Kind.JUMP, 0, Style.SOLID, b.dir(), 0);
			if (!separatedFrom(a, foot)) {
				return false;
			}
		}
		return separatedFrom(a, b);
	}

	private static boolean separatedFrom(Platform a, Platform b) {
		if (b.standHalves() - a.standHalves() > 2) {
			// More than a block higher: nobody jumps up there
			return true;
		}
		int drop = a.y() - b.y();
		double need = SEPARATION + (drop >= 3 ? Math.min(4, drop * 0.5) : 0);
		return distance(a, b) >= need;
	}

	/** Platforms this many places apart along the route must be {@link #separated}. */
	static final int NEIGHBOURS = 3;
	public static final double SEPARATION = 5.0;

	public static List<Platform> generate(long seed, Difficulty d, int jumps, int checkpointEvery, int maxRise) {
		Random rnd = new Random(seed);
		// What each platform is, fixed up front so backtracking does not change the checkpoint rhythm
		Kind[] kinds = new Kind[jumps + 1];
		kinds[0] = Kind.START;
		int since = 0;
		for (int i = 1; i <= jumps; i++) {
			since++;
			if (i == jumps) {
				kinds[i] = Kind.FINISH;
			} else if (since >= checkpointEvery) {
				kinds[i] = Kind.CHECKPOINT;
				since = 0;
			} else {
				kinds[i] = Kind.JUMP;
			}
		}
		List<Platform> out = new ArrayList<>();
		// Route state after each platform: heading, jumps left in the turn, last turn, x where it ended
		List<int[]> states = new ArrayList<>();
		out.add(new Platform(0, 0, 0, 5, Kind.START, 0, Style.SOLID, EAST, 0));
		states.add(new int[] {EAST, 0, EAST, Integer.MIN_VALUE / 2});
		int backtracks = 0;
		while (out.size() <= jumps) {
			int i = out.size();
			Platform cur = out.get(i - 1);
			int[] st = states.get(i - 1).clone();
			Kind kind = kinds[i];
			// Turns: a few jumps north or south, then east again
			if (st[1] > 0) {
				st[1]--;
				if (st[1] == 0) {
					st[0] = EAST;
					st[3] = cur.x();
				}
			} else if (st[0] == EAST && kind == Kind.JUMP && rnd.nextInt(8) == 0) {
				// Turn back towards the middle line when the course has wandered off
				int want = cur.z() > 8 ? NORTH : (cur.z() < -8 ? SOUTH : (rnd.nextBoolean() ? NORTH : SOUTH));
				// No U-turns: turning the other way needs a good stretch east first
				boolean opposite = (want == NORTH && st[2] == SOUTH) || (want == SOUTH && st[2] == NORTH);
				if (!opposite || cur.x() - st[3] >= 14) {
					st[0] = want;
					st[2] = want;
					st[1] = 2 + rnd.nextInt(3);
				}
			}
			Platform next = null;
			for (int attempt = 0; attempt < 60 && next == null; attempt++) {
				Platform cand = candidate(rnd, d, cur, kind, attempt < 40 ? st[0] : EAST, maxRise, attempt >= 30);
				if (jumpable(cur, cand, d) && clearOf(out, cand)) {
					next = cand;
				}
			}
			if (next == null && backtracks < 2000 && out.size() > 1) {
				// Dead end: choose the previous platform again
				backtracks++;
				out.remove(out.size() - 1);
				states.remove(states.size() - 1);
				continue;
			}
			if (next == null) {
				int size = kind == Kind.FINISH ? 5 : 3;
				next = new Platform(cur.x() + cur.half() + 2 + size / 2, cur.y() - 1, cur.z(), size, kind, 0, Style.SOLID, EAST, 0);
				st[0] = EAST;
				st[1] = 0;
			}
			out.add(next);
			states.add(st);
		}
		int checkpoint = 0;
		for (int i = 1; i < out.size(); i++) {
			Platform p = out.get(i);
			if (p.kind() == Kind.CHECKPOINT) {
				out.set(i, withCheckpoint(p, ++checkpoint));
			}
		}
		Platform fin = out.get(out.size() - 1);
		out.set(out.size() - 1, withCheckpoint(fin, checkpoint + 1));
		return out;
	}

	private static Platform withCheckpoint(Platform p, int index) {
		return new Platform(p.x(), p.y(), p.z(), p.size(), p.kind(), index, p.style(), p.dir(), p.climb());
	}

	/** Far enough from every earlier platform except the last few. */
	static boolean clearOf(List<Platform> course, Platform cand) {
		for (int i = 0; i < course.size() - NEIGHBOURS; i++) {
			if (!separated(course.get(i), cand)) {
				return false;
			}
		}
		return true;
	}

	private static Platform candidate(Random rnd, Difficulty d, Platform cur, Kind kind, int dir, int maxRise, boolean careful) {
		int level = d.ordinal();
		boolean special = kind == Kind.JUMP;
		// Size: checkpoints 3, finish 5; jumps get smaller with difficulty
		int size;
		if (kind == Kind.FINISH) {
			size = 5;
		} else if (kind == Kind.CHECKPOINT) {
			size = 3;
		} else {
			int oneIn = switch (d) {
				case EASY -> 1;
				case MEDIUM -> 6;
				case HARD -> 2;
				case EXTREME -> 1;
			};
			size = d == Difficulty.EASY ? 3 : ((d == Difficulty.EXTREME ? rnd.nextInt(5) != 0 : rnd.nextInt(oneIn) == 0) ? 1 : 3);
		}
		Style style = Style.SOLID;
		if (special && !careful) {
			int r = rnd.nextInt(100);
			if (level >= 1 && r < 10 && cur.style() != Style.LADDER) {
				style = Style.LADDER;
			} else if (level >= 1 && r < 25) {
				style = Style.SLAB;
			} else if (level >= 2 && r < 25 + 6 * level) {
				style = Style.ICE;
			} else if (level >= 2 && r < 25 + 12 * level) {
				style = Style.FENCE;
			} else if (level == 0 && r < 8) {
				style = Style.LADDER;
			}
		}
		if (style == Style.FENCE || style == Style.LADDER) {
			size = 1;
		}
		if (style == Style.ICE && size == 1 && level < 3) {
			size = 3;
		}
		int fx = dx(dir);
		int fz = dz(dir);
		if (style == Style.LADDER) {
			int climb = 2 + rnd.nextInt(Math.min(5, 2 + level));
			int feet = cur.standHalves() / 2;
			int top = feet - 1 + climb;
			if (top > maxRise + 4) {
				climb = Math.max(2, maxRise + 5 - feet);
				top = feet - 1 + climb;
			}
			int gap = careful ? 0 : rnd.nextInt(ladderGap(d) + 1);
			// The ladder hangs on the near side of the column, so the column is one block further
			int along = cur.half() + 1 + gap + 1;
			return new Platform(cur.x() + fx * along, top, cur.z() + fz * along, 1, kind, 0, style, dir, climb);
		}
		// Height change
		int r = rnd.nextInt(10);
		int dy;
		if (r < 3 && cur.y() < maxRise) {
			dy = 1;
		} else if (r < 6 && cur.y() > -6) {
			dy = cur.style() == Style.LADDER && rnd.nextBoolean() ? -2 : -1;
		} else {
			dy = 0;
		}
		Platform probe = new Platform(0, cur.y() + dy, 0, size, kind, 0, style, dir, 0);
		int dh = probe.standHalves() - cur.standHalves();
		double max = maxDistance(dh, d) - (style == Style.FENCE || cur.style() == Style.FENCE ? 0.5 : 0);
		int maxGap = Math.max(1, (int) Math.floor(max));
		int gap = careful ? Math.min(2, maxGap) : 1 + rnd.nextInt(maxGap);
		// Sideways offset for diagonal jumps (medium and up), within what the distance allows
		int side = 0;
		if (level >= 1 && !careful && rnd.nextInt(3) == 0) {
			// Heading north or south, a sideways step may only go east (the course never heads west)
			side = fx == 0 || rnd.nextBoolean() ? 1 : -1;
			if (fz == 0 && Math.abs(cur.z()) > 12) {
				side = cur.z() > 0 ? -1 : 1;
			}
		}
		// Hard and extreme: one air block sideways as well, a real diagonal jump
		int offset = cur.half() + size / 2 + 1 + (level >= 2 && rnd.nextBoolean() ? 1 : 0);
		int along = cur.half() + 1 + gap + size / 2;
		int x = cur.x() + fx * along + (fx == 0 ? side * offset : 0);
		int z = cur.z() + fz * along + (fz == 0 ? side * offset : 0);
		return new Platform(x, cur.y() + dy, z, size, kind, 0, style, dir, 0);
	}
}
