package net.battlehub.core.music;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * BattleHub's music for players without the resource pack (1.47): the same score played with vanilla note block
 * sounds. A note block plays two octaves: pitch 0.5 is its lowest note, 2.0 the highest. Every part is given the
 * instrument that has its note in range (low brass on the didgeridoo, middle brass on the square-wave "bit", high on
 * the flute; strings on the harp, guitar or bass; bells on the bell), or the first one, an octave up or down. Note
 * blocks do not hold a note, so held notes of strings, brass, choir and bass are struck again while they last, softer.
 * Drums go to the bass drum, snare and hi-hat; a gong is a low bass drum with a low bell and a low didgeridoo.
 */
public final class NoteBlocks {
	private NoteBlocks() {}

	/** One sound: tick from the start, the note block instrument ("harp", "bass", ...), its pitch, its volume. */
	public record Strike(int tick, String instrument, float pitch, float volume) {}

	/** The lowest MIDI note of each pitched instrument (played at pitch 0.5); two octaves up is its highest. */
	public static final Map<String, Integer> LOWEST = Map.ofEntries(Map.entry("harp", 54), Map.entry("pling", 54),
			Map.entry("bit", 54), Map.entry("banjo", 54), Map.entry("iron_xylophone", 54), Map.entry("guitar", 42),
			Map.entry("bass", 30), Map.entry("didgeridoo", 30), Map.entry("flute", 66), Map.entry("cow_bell", 66),
			Map.entry("bell", 78), Map.entry("chime", 78), Map.entry("xylophone", 78));

	/** The instruments a part may use, the first that has the note in range. */
	static final Map<String, List<String>> CHOICES = Map.of(
			"strings", List.of("harp", "guitar", "bass", "flute", "bell"),
			"brass", List.of("didgeridoo", "bit", "flute"),
			"choir", List.of("flute", "harp", "guitar"),
			"bass", List.of("bass", "guitar"),
			"bells", List.of("bell", "iron_xylophone", "flute"),
			"harp", List.of("harp", "guitar", "bass", "bell"),
			"lead", List.of("flute", "harp", "bell"));

	/** How often a held note is struck again (ticks), for the parts that hold notes. */
	static final Map<String, Integer> SUSTAIN = Map.of("strings", 10, "choir", 10, "brass", 8, "bass", 10);

	/** Every sound of the piece, in the order they come. */
	public static List<Strike> render(Score s) {
		List<Strike> out = new ArrayList<>();
		for (Score.Note n : s.notes()) {
			switch (n.family()) {
				case "timpani" -> out.add(new Strike(n.tick(), "basedrum", clamp(0.5f + (n.pitch() - 33) / 30f, 0.5f, 1.0f), n.volume()));
				case "kick" -> out.add(new Strike(n.tick(), "basedrum", 0.8f, n.volume()));
				case "snare" -> out.add(new Strike(n.tick(), "snare", 1.0f, n.volume() * 0.8f));
				case "hat" -> out.add(new Strike(n.tick(), "hat", 1.3f, n.volume() * 0.7f));
				case "gong" -> {
					out.add(new Strike(n.tick(), "basedrum", 0.5f, n.volume()));
					out.add(new Strike(n.tick(), "bell", 0.5f, n.volume() * 0.7f));
					out.add(new Strike(n.tick(), "didgeridoo", pitch(n.pitch(), "didgeridoo"), n.volume() * 0.8f));
					for (int t = 6; t < n.ticks(); t += 6) out.add(new Strike(n.tick() + t, "bell", 0.5f, n.volume() * 0.5f * (1 - t / (float) n.ticks())));
				}
				default -> pitched(n, out);
			}
		}
		out.sort(Comparator.comparingInt(Strike::tick));
		return out;
	}

	private static void pitched(Score.Note n, List<Strike> out) {
		String inst = instrument(n.family(), n.pitch());
		float pitch = pitch(n.pitch(), inst);
		out.add(new Strike(n.tick(), inst, pitch, n.volume()));
		Integer every = SUSTAIN.get(n.family());
		if (every == null) return;
		for (int t = every; t < n.ticks() - 2; t += every) {
			out.add(new Strike(n.tick() + t, inst, pitch, n.volume() * 0.6f));
		}
	}

	/** The instrument for a note of this kind of part. */
	public static String instrument(String family, int midi) {
		List<String> choices = CHOICES.getOrDefault(family, CHOICES.get("harp"));
		for (String c : choices) {
			int low = LOWEST.get(c);
			if (midi >= low && midi <= low + 24) return c;
		}
		return choices.get(0);
	}

	/** The pitch to play {@code midi} on {@code instrument}: in its two octaves, an octave at a time when outside. */
	public static float pitch(int midi, String instrument) {
		int low = LOWEST.getOrDefault(instrument, 54);
		int m = midi;
		while (m < low) m += 12;
		while (m > low + 24) m -= 12;
		return (float) Math.pow(2, (m - low - 12) / 12.0);
	}

	private static float clamp(float v, float lo, float hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
