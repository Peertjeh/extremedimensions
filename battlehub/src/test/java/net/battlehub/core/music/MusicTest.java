package net.battlehub.core.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** BattleHub's own Hunger Games music (1.47): the scores and how they sound on note blocks. */
class MusicTest {
	static final List<String> SONGS = List.of("hg_opening", "hg_gong", "hg_bloodbath", "hg_alarm", "hg_feast", "hg_anthem", "hg_victory");

	static Score load(String id) throws IOException {
		try (InputStream in = MusicTest.class.getResourceAsStream("/battlehub/music/" + id + ".json")) {
			assertNotNull(in, id + ".json in the jar");
			return Score.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	@Test
	void everyScoreReadsAndEveryNoteIsInsideIt() throws IOException {
		for (String id : SONGS) {
			Score s = load(id);
			assertEquals(id, s.id);
			assertEquals(0, 1200 % s.bpm, id + ": whole ticks per beat");
			assertTrue(s.lengthTicks > 0 && !s.notes().isEmpty(), id);
			for (Score.Note n : s.notes()) {
				assertTrue(n.tick() >= 0 && n.tick() <= s.lengthTicks, id + ": a note at " + n.tick());
				assertTrue(n.volume() > 0 && n.volume() <= 1, id + ": volume " + n.volume());
			}
		}
	}

	@Test
	void theOpeningFitsTheCountdownAndTheAnthemTheNight() throws IOException {
		// the default Hunger Games countdown is 20 seconds: the opening ends before the gong
		assertTrue(load("hg_opening").lengthTicks <= 20 * 20);
		// the night lasts as long as its anthem, 25 to 45 seconds
		int anthem = load("hg_anthem").lengthTicks;
		assertTrue(anthem >= 25 * 20 && anthem <= 45 * 20, "anthem " + anthem);
		// an alarm is short: it must not drown the game
		assertTrue(load("hg_alarm").lengthTicks <= 5 * 20);
	}

	@Test
	void onNoteBlocksEveryNoteIsInRange() throws IOException {
		for (String id : SONGS) {
			List<NoteBlocks.Strike> strikes = NoteBlocks.render(load(id));
			Map<Integer, Integer> perTick = new HashMap<>();
			int last = -1;
			for (NoteBlocks.Strike st : strikes) {
				assertTrue(st.pitch() >= 0.499f && st.pitch() <= 2.001f, id + ": pitch " + st.pitch() + " on " + st.instrument());
				assertTrue(st.tick() >= last, id + ": in order");
				last = st.tick();
				perTick.merge(st.tick(), 1, Integer::sum);
			}
			// a handful of sounds a tick at most, so the packets stay few
			int most = perTick.values().stream().mapToInt(Integer::intValue).max().orElse(0);
			assertTrue(most <= 16, id + ": " + most + " sounds in one tick");
		}
	}

	@Test
	void eachPartGetsTheInstrumentForItsRange() {
		assertEquals("didgeridoo", NoteBlocks.instrument("brass", 45));
		assertEquals("bit", NoteBlocks.instrument("brass", 62));
		assertEquals("flute", NoteBlocks.instrument("brass", 84));
		assertEquals("bass", NoteBlocks.instrument("strings", 38));
		assertEquals("harp", NoteBlocks.instrument("strings", 62));
		assertEquals("bell", NoteBlocks.instrument("bells", 86));
		// pitch 1.0 is the middle of the two octaves; outside them an octave at a time
		assertEquals(1.0f, NoteBlocks.pitch(66, "harp"), 1e-6);
		assertEquals(0.5f, NoteBlocks.pitch(54, "harp"), 1e-6);
		assertEquals(2.0f, NoteBlocks.pitch(78, "harp"), 1e-6);
		assertEquals(2.0f, NoteBlocks.pitch(90, "harp"), 1e-6);
		assertEquals(0.5f, NoteBlocks.pitch(42, "harp"), 1e-6);
	}

	@Test
	void heldNotesAreStruckAgainAndDrumsAreDrums() {
		Score s = Score.parse("{\"id\":\"t\",\"bpm\":60,\"beats\":4,\"parts\":[{\"family\":\"strings\",\"volume\":1,\"notes\":[[0,62,2,0.5]]},"
				+ "{\"family\":\"snare\",\"volume\":1,\"notes\":[[1,0,0.25,1]]}]}");
		List<NoteBlocks.Strike> st = NoteBlocks.render(s);
		long harp = st.stream().filter(x -> x.instrument().equals("harp")).count();
		// two beats at 60 a minute = 40 ticks: struck at 0, 10, 20, 30
		assertEquals(4, harp);
		assertTrue(st.stream().anyMatch(x -> x.instrument().equals("snare") && x.tick() == 20));
	}
}
