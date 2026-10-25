package net.battlehub.core.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.battlehub.core.ml.MiniJson;

/**
 * A piece of BattleHub's own music as a score (1.47): parts with notes in beats, read from the JSON that
 * {@code tools/music/compose.py} writes next to the .ogg it synthesises from the same score. A beat is a whole number
 * of game ticks (the tempo divides 1200), so every note starts on a tick.
 */
public final class Score {
	/** One note: when (ticks from the start), which kind of part, the MIDI pitch (C4 = 60), how long, how loud (0..1). */
	public record Note(int tick, String family, int pitch, int ticks, float volume) {}

	public final String id;
	public final int bpm;
	/** How long the piece is in ticks (its last bar), not counting the tail of its last notes. */
	public final int lengthTicks;
	private final List<Note> notes;

	private Score(String id, int bpm, int lengthTicks, List<Note> notes) {
		this.id = id;
		this.bpm = bpm;
		this.lengthTicks = lengthTicks;
		this.notes = notes;
	}

	public int ticksPerBeat() {
		return 1200 / bpm;
	}

	/** All notes, in the order they start. */
	public List<Note> notes() {
		return notes;
	}

	public static Score parse(String json) {
		Map<String, Object> d = MiniJson.object(MiniJson.parse(json));
		String id = String.valueOf(d.get("id"));
		int bpm = ((Number) d.get("bpm")).intValue();
		if (bpm <= 0 || 1200 % bpm != 0) throw new IllegalArgumentException(id + ": a beat must be a whole number of ticks, not " + bpm + " bpm");
		int perBeat = 1200 / bpm;
		int length = (int) Math.round(((Number) d.get("beats")).doubleValue() * perBeat);
		List<Note> out = new ArrayList<>();
		for (Object po : MiniJson.array(d.get("parts"))) {
			Map<String, Object> part = MiniJson.object(po);
			String family = String.valueOf(part.get("family"));
			double partVolume = part.get("volume") instanceof Number v ? v.doubleValue() : 1.0;
			for (Object no : MiniJson.array(part.get("notes"))) {
				List<Object> nv = MiniJson.array(no);
				double beat = ((Number) nv.get(0)).doubleValue();
				int pitch = ((Number) nv.get(1)).intValue();
				double beats = ((Number) nv.get(2)).doubleValue();
				double vel = ((Number) nv.get(3)).doubleValue();
				int tick = (int) Math.round(beat * perBeat);
				int ticks = Math.max(1, (int) Math.round(beats * perBeat));
				out.add(new Note(tick, family, pitch, ticks, (float) Math.max(0, Math.min(1, vel * partVolume))));
			}
		}
		out.sort(Comparator.comparingInt(Note::tick));
		return new Score(id, bpm, length, Collections.unmodifiableList(out));
	}
}
