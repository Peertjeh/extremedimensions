package net.battlehub.core.hunger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * When the Gamemakers act in one Hunger Games match, decided at the start (without Minecraft, so it can be tested on
 * its own): the Feast once, announced a minute ahead, and an arena event every two and a half to three and a half
 * minutes after the peace, drawn by weight, never the same twice in a row and none in the last minute of the match.
 */
public final class HgSchedule {
	private HgSchedule() {}

	/** The Feast's own id (it is not drawn). */
	public static final String FEAST = "feast";
	/** How long before the Feast it is announced. */
	public static final int FEAST_WARNING_TICKS = 60 * 20;
	/** The shortest and longest gap between two events. */
	public static final int MIN_GAP = 150 * 20, MAX_GAP = 210 * 20;
	/** No event starts this close to the time limit. */
	public static final int QUIET_END = 60 * 20;

	/** An arena event the Gamemakers can send, with its weight in the draw. */
	public record Kind(String id, int weight) {}

	/** The arena events (the Feast aside), with how often they come. */
	public static final List<Kind> KINDS = List.of(
			new Kind("wildfire", 10), new Kind("fog", 10), new Kind("mutts", 8), new Kind("tracker_jackers", 8),
			new Kind("storm", 8), new Kind("supplies", 10), new Kind("revealed", 6), new Kind("acid_rain", 7),
			new Kind("jabberjays", 6));

	/** One planned moment: at this tick of play, this event starts (the Feast: its announcement comes earlier). */
	public record Entry(int tick, String id) {}

	/**
	 * The plan of a match: events from the end of the peace until a minute before the time limit, with the Feast at
	 * {@code feastTick} (or not, when it falls outside the match).
	 */
	public static List<Entry> plan(long seed, int graceTicks, int limitTicks, int feastTick) {
		Random r = new Random(seed);
		List<Entry> out = new ArrayList<>();
		int last = limitTicks - QUIET_END;
		boolean feast = feastTick - FEAST_WARNING_TICKS > graceTicks && feastTick <= last;
		String previous = null;
		int t = graceTicks + MIN_GAP / 2 + r.nextInt(MAX_GAP - MIN_GAP + 1) / 2;
		while (t <= last) {
			// the Feast keeps its minute free: nothing else starts within it or right after
			if (feast && Math.abs(t - feastTick) < FEAST_WARNING_TICKS + 30 * 20) {
				t = feastTick + FEAST_WARNING_TICKS + 30 * 20;
				continue;
			}
			String id = draw(r, previous);
			out.add(new Entry(t, id));
			previous = id;
			t += MIN_GAP + r.nextInt(MAX_GAP - MIN_GAP + 1);
		}
		if (feast) out.add(new Entry(feastTick, FEAST));
		out.sort((a, b) -> Integer.compare(a.tick(), b.tick()));
		return out;
	}

	/** One event by weight, not the one before. */
	static String draw(Random r, String previous) {
		int total = 0;
		for (Kind k : KINDS) {
			if (!k.id().equals(previous)) total += k.weight();
		}
		int pick = r.nextInt(total);
		for (Kind k : KINDS) {
			if (k.id().equals(previous)) continue;
			pick -= k.weight();
			if (pick < 0) return k.id();
		}
		return KINDS.get(0).id();
	}

	/** Whether this id is an event the Gamemakers know (the Feast included). */
	public static boolean known(String id) {
		if (FEAST.equals(id)) return true;
		for (Kind k : KINDS) {
			if (k.id().equals(id)) return true;
		}
		return false;
	}
}
