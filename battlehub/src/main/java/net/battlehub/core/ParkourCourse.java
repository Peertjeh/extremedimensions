package net.battlehub.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a jumpable parkour course: square platforms going roughly towards +X. Coordinates are
 * relative to the start platform centre. Every jump respects vanilla sprint-jump reach:
 * flat gaps up to 3 blocks, one block up with a gap up to 2, one block down with a gap up to 3.
 */
public final class ParkourCourse {
	public enum Kind { START, JUMP, CHECKPOINT, FINISH }

	/** A {@code size} x {@code size} platform centred on (x, z), top surface at y. Size is odd. */
	public record Platform(int x, int y, int z, int size, Kind kind, int checkpoint) {
		public int half() {
			return size / 2;
		}
	}

	public static final int MAX_FLAT_GAP = 3;
	public static final int MAX_UP_GAP = 2;
	public static final int MAX_DOWN_GAP = 3;

	private ParkourCourse() {}

	/**
	 * @param jumps           number of single jumps between start and finish
	 * @param checkpointEvery a checkpoint platform after this many jumps
	 * @param maxRise         how far the course may climb above the start
	 */
	public static List<Platform> generate(long seed, int jumps, int checkpointEvery, int maxRise) {
		Random rnd = new Random(seed);
		List<Platform> out = new ArrayList<>();
		Platform cur = new Platform(0, 0, 0, 5, Kind.START, 0);
		out.add(cur);
		int checkpoint = 0;
		int sinceCheckpoint = 0;
		int drift = 0;
		for (int i = 0; i < jumps; i++) {
			boolean last = i == jumps - 1;
			sinceCheckpoint++;
			Kind kind = last ? Kind.FINISH : (sinceCheckpoint >= checkpointEvery ? Kind.CHECKPOINT : Kind.JUMP);
			int size = kind == Kind.JUMP ? (rnd.nextInt(4) == 0 ? 3 : 1) : (kind == Kind.FINISH ? 5 : 3);

			int dy;
			int r = rnd.nextInt(10);
			if (r < 3 && cur.y() < maxRise) {
				dy = 1;
			} else if (r < 5 && cur.y() > -4) {
				dy = -1;
			} else {
				dy = 0;
			}
			int maxGap = dy > 0 ? MAX_UP_GAP : (dy < 0 ? MAX_DOWN_GAP : MAX_FLAT_GAP);
			// Small platforms with long jumps are mean; keep the longest gaps for bigger landings.
			if (size == 1 && cur.size() == 1) {
				maxGap = Math.min(maxGap, 2);
			}
			int gap = 1 + rnd.nextInt(maxGap);
			int lateral = 0;
			if (gap <= 2) {
				// Sideways steps keep the course from being a straight line, but stay inside a corridor
				int want = rnd.nextInt(3) - 1;
				if (Math.abs(drift + want) <= 6) {
					lateral = want;
				}
			}
			drift += lateral;
			int x = cur.x() + cur.half() + 1 + gap + size / 2;
			int z = cur.z() + lateral;
			Platform next = new Platform(x, cur.y() + dy, z, size, kind, kind == Kind.CHECKPOINT ? ++checkpoint : 0);
			if (kind == Kind.CHECKPOINT) {
				sinceCheckpoint = 0;
			}
			out.add(next);
			cur = next;
		}
		// The finish counts as the last checkpoint index
		Platform fin = out.get(out.size() - 1);
		out.set(out.size() - 1, new Platform(fin.x(), fin.y(), fin.z(), fin.size(), Kind.FINISH, checkpoint + 1));
		return out;
	}

	/** Empty blocks between two consecutive platforms along X. */
	public static int gap(Platform a, Platform b) {
		return (b.x() - b.half()) - (a.x() + a.half()) - 1;
	}

	/** Can a player sprint-jump from a to b? */
	public static boolean jumpable(Platform a, Platform b) {
		int dy = b.y() - a.y();
		int g = gap(a, b);
		int lateral = Math.abs(b.z() - a.z());
		if (dy > 1 || dy < -2 || g < 0 || lateral > 1 + a.half() + b.half()) {
			return false;
		}
		int max = dy > 0 ? MAX_UP_GAP : (dy < 0 ? MAX_DOWN_GAP : MAX_FLAT_GAP);
		return g <= max;
	}
}
