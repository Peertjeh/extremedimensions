package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.extremedimensions.compat.PlaytestLog;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.expedition.ExpeditionRecord;
import com.extremedimensions.expedition.ExpeditionService;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.relic.Relic;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.server.Dims;
import com.extremedimensions.tether.TetherService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * The record of an expedition (3.52): one line in the playtest log for every expedition that ends while the log is on
 * (none while it is off), a line for a relic bound and for a curse chosen; and after a Collapse, once the player is home
 * and has chosen what it cost, the card of it with the right numbers. Both change settings and a file every test would
 * share, so each runs alone.
 */
public class ExpeditionRecordTests {
	/** Seconds of charge the expedition begins with. */
	private static final double CHARGE = 600;

	/**
	 * Through a portal into the End, with the charge in an anchor at the player's feet and the Tether bound to it, and at
	 * once {@code out} blocks from the portal. Returns the anchor.
	 */
	private static Anchor through(GameTestHelper helper, ServerPlayer p, PlayerData d, double out) {
		BlockPos at = p.blockPosition();
		Anchor a = new Anchor(at.getX(), at.getY(), at.getZ(), Dims.END);
		a.add(p.getStringUUID(), CHARGE);
		DataStore.anchors().put(a.key(), a);
		d.hasLast = true;
		d.lastX = at.getX() + 0.5;
		d.lastY = at.getY() + 0.5;
		d.lastZ = at.getZ() + 0.5;
		d.tetherAnchor = a.key();
		d.tetherTier = 0;
		d.collapsing = false;
		d.inRun = false;
		ExpeditionService.onDimensionChange(p, d, Dims.OVERWORLD, Dims.END);
		check(helper, d.inRun && d.runTally != null, "the expedition did not begin (on it %s, tallied %s)", d.inRun, d.runTally != null);
		d.arrivalX = p.getX() - out;
		d.arrivalZ = p.getZ();
		return a;
	}

	/** The log's lines, each as column to value (the header first checked against the columns). */
	private static List<Map<String, String>> lines(GameTestHelper helper, Path file) throws IOException {
		List<String> raw = Files.readAllLines(file, StandardCharsets.UTF_8);
		check(helper, !raw.isEmpty() && raw.get(0).equals(PlaytestLog.header()), "the file does not start with the header: %s",
				raw.isEmpty() ? "(empty)" : raw.get(0));
		List<Map<String, String>> out = new ArrayList<>();
		for (String line : raw.subList(1, raw.size())) {
			// (no field the tests write holds a comma, so none is quoted)
			List<String> fields = Arrays.asList(line.split(",", -1));
			check(helper, fields.size() == PlaytestLog.COLUMNS.size(), "a line has %s fields, not %s: %s", fields.size(), PlaytestLog.COLUMNS.size(), line);
			Map<String, String> row = new LinkedHashMap<>();
			for (int i = 0; i < fields.size(); i++) {
				row.put(PlaytestLog.COLUMNS.get(i), fields.get(i));
			}
			out.add(row);
		}
		return out;
	}

	private static void forget(Path dir) {
		if (dir == null) {
			return;
		}
		try (var walk = Files.walk(dir)) {
			walk.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
		} catch (IOException e) {
			// (a temporary folder; the system clears it in time)
		}
	}

	@GameTest(environment = Alone.PLAYTEST_LOG)
	public void anExpeditionThatEndsIsOneLineInThePlaytestLog(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-playtest");
		PlayerData d = DataStore.get(p.getUUID());
		int log = Config.PLAYTEST_LOG;
		String relic = d.relic;
		List<Anchor> anchors = new ArrayList<>();
		Path dir = null;
		try {
			dir = Files.createTempDirectory("ed-playtest");
			PlaytestLog.folder = dir.resolve("extremedimensions");
			Path file = PlaytestLog.folder.resolve(PlaytestLog.FILE);

			// Off: an expedition comes and goes, and nothing is written
			Config.PLAYTEST_LOG = 0;
			anchors.add(through(helper, p, d, 50));
			for (int i = 0; i < 40; i++) {
				ExpeditionService.tick(p, d, Dims.END);
			}
			ExpeditionService.onDimensionChange(p, d, Dims.END, Dims.OVERWORLD);
			PlaytestLog.flush();
			check(helper, !d.inRun && d.records.isEmpty(), "the expedition did not end (on it %s, %s records waiting)", d.inRun, d.records.size());
			check(helper, !Files.exists(file), "the playtest log was written while it was off");

			// On: the next one is a line, under the header, with what it came to
			Config.PLAYTEST_LOG = 1;
			anchors.add(through(helper, p, d, 50));
			double factor = TetherService.distanceFactor(p, d);
			for (int i = 0; i < 40; i++) {
				ExpeditionService.tick(p, d, Dims.END);
			}
			double drain = d.currentDrain;
			ExpeditionService.onDimensionChange(p, d, Dims.END, Dims.OVERWORLD);
			PlaytestLog.flush();
			check(helper, Files.isRegularFile(file), "the playtest log was not written to %s", file);
			List<Map<String, String>> rows = lines(helper, file);
			check(helper, rows.size() == 1, "the file holds %s lines, not one", rows.size());
			Map<String, String> row = rows.get(0);
			check(helper, PlaytestLog.EXPEDITION.equals(row.get("kind")) && p.getScoreboardName().equals(row.get("player")), "the line reads %s", row);
			check(helper, row.get("time").matches("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d"), "its time reads %s", row.get("time"));
			check(helper, "End".equals(row.get("realm")) && "Overworld".equals(row.get("from")) && "returned".equals(row.get("outcome")),
					"realm, from and outcome read %s, %s, %s", row.get("realm"), row.get("from"), row.get("outcome"));
			// (the second expedition into the End this player began: the one while the log was off counts too)
			check(helper, "2".equals(row.get("nth")), "nth reads %s, not 2", row.get("nth"));
			check(helper, "2".equals(row.get("seconds")) && "50".equals(row.get("farthest_blocks")),
					"40 ticks 50 blocks out read %s seconds, %s blocks", row.get("seconds"), row.get("farthest_blocks"));
			long distance = Math.round((factor - 1) * 100), all = Math.round((drain / Config.BASE_DRAIN - 1) * 100);
			check(helper, String.valueOf(distance).equals(row.get("distance_pct")), "distance_pct reads %s, not %s", row.get("distance_pct"), distance);
			check(helper, Math.abs(Long.parseLong(row.get("drain_pct")) - all) <= 1, "drain_pct reads %s, not %s", row.get("drain_pct"), all);
			check(helper, String.valueOf(Math.round(CHARGE)).equals(row.get("charge_s")), "charge_s reads %s", row.get("charge_s"));
			check(helper, row.get("curses").isEmpty() && "0".equals(row.get("curse_stacks")) && "iron".equals(row.get("tether"))
					&& row.get("relic").isEmpty() && !row.get("difficulty").isEmpty(), "a safe return without a relic reads %s", row);

			// A relic bound is a line of its own
			d.relic = null;
			check(helper, RelicService.bind(p, RelicService.stack(Relic.TITHE_BELL)).consumesAction(), "the Tithe Bell did not bind");
			PlaytestLog.flush();
			rows = lines(helper, file);
			check(helper, rows.size() == 2 && PlaytestLog.RELIC_BOUND.equals(rows.get(1).get("kind")) && "tithe_bell".equals(rows.get(1).get("relic")),
					"the relic bound was not written: %s", rows);
			helper.succeed();
		} catch (IOException e) {
			throw new AssertionError("the playtest log could not be read back", e);
		} finally {
			Config.PLAYTEST_LOG = log;
			PlaytestLog.folder = null;
			d.relic = relic;
			d.inRun = false;
			d.linked.clear();
			TetherService.clear(p, d, false);
			for (Anchor a : anchors) {
				DataStore.anchors().remove(a.key());
			}
			forget(dir);
		}
	}

	@GameTest(environment = Alone.COLLAPSE_RECAP)
	public void aCollapseIsToldOnceThePlayerIsHomeAndHasChosen(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-recap");
		PlayerData d = DataStore.get(p.getUUID());
		int recap = Config.COLLAPSE_RECAP, log = Config.PLAYTEST_LOG;
		List<Anchor> anchors = new ArrayList<>();
		Path dir = null;
		try {
			dir = Files.createTempDirectory("ed-recap");
			PlaytestLog.folder = dir;
			Config.COLLAPSE_RECAP = 1;
			Config.PLAYTEST_LOG = 1;
			ExpeditionRecord.lastRecap = null;

			// Three seconds 80 blocks out, then the charge is gone: the Collapse
			Anchor a = through(helper, p, d, 80);
			anchors.add(a);
			double factor = TetherService.distanceFactor(p, d);
			for (int i = 0; i < 60; i++) {
				ExpeditionService.tick(p, d, Dims.END);
			}
			double drain = d.currentDrain;
			a.clear(p.getStringUUID());
			ExpeditionService.tick(p, d, Dims.END);
			check(helper, d.collapsing && !d.pendingOffer.isEmpty(), "the charge ran out without a Collapse (collapsing %s, offer %s)", d.collapsing,
					d.pendingOffer);
			check(helper, ExpeditionRecord.lastRecap == null, "the recap came during the pull");

			// The death and the respawn: home, but the curse is not chosen yet
			ExpeditionService.onDeath(p);
			ExpeditionService.onRespawn(p, d);
			check(helper, ExpeditionRecord.lastRecap == null, "the recap came before the curse was chosen");

			// Chosen: the card, with what it came to
			Map<String, Integer> chosen = new LinkedHashMap<>();
			int picks = 0;
			for (int guard = 0; !d.pendingOffer.isEmpty() && guard < 8; guard++) {
				String id = d.pendingOffer.get(0);
				chosen.merge(id, d.pendingMult.isEmpty() ? 1 : Math.max(1, d.pendingMult.get(0)), Integer::sum);
				PenaltyService.choose(p, id);
				picks++;
			}
			ExpeditionRecord.Sent sent = ExpeditionRecord.lastRecap;
			check(helper, sent != null && sent.to().equals(p.getUUID()), "no recap was sent once the curse was chosen");
			ModPackets.RecapPayload r = sent.recap();
			check(helper, Dims.END.equals(r.realm()) && "collapsed".equals(r.outcome()), "the recap is of %s, %s", r.realm(), r.outcome());
			// (61 ticks: the one the charge ran out in counts too)
			check(helper, r.seconds() == 3 && r.charge() == (int) CHARGE && r.farthest() == 80, "the recap reads %s s away on %s, %s blocks out",
					r.seconds(), r.charge(), r.farthest());
			long distance = Math.round(factor * 100), all = Math.round(drain / Config.BASE_DRAIN * 100);
			check(helper, r.distanceX100() == distance, "the distance's share reads %s, not %s", r.distanceX100(), distance);
			check(helper, Math.abs(r.drainX100() - all) <= 1, "the drain reads %s, not %s", r.drainX100(), all);
			for (Map.Entry<String, Integer> e : chosen.entrySet()) {
				check(helper, r.curses().contains(e.getKey() + ":" + e.getValue()), "the recap's cost %s does not hold %s x%s", r.curses(), e.getKey(),
						e.getValue());
			}
			check(helper, r.silk() == 0 && r.fragments() == 0, "a Collapse beyond the wound took silk or fragments: %s, %s", r.silk(), r.fragments());
			check(helper, d.records.isEmpty(), "%s records still wait", d.records.size());

			// The same record is the playtest log's: the curse chosen, then the expedition it was owed for
			PlaytestLog.flush();
			List<Map<String, String>> rows = lines(helper, dir.resolve(PlaytestLog.FILE));
			check(helper, picks > 0 && rows.size() == picks + 1, "the log holds %s lines, not %s", rows.size(), picks + 1);
			Map<String, String> card = rows.get(0), run = rows.get(rows.size() - 1);
			check(helper, PlaytestLog.CURSE_CHOSEN.equals(card.get("kind")) && "End".equals(card.get("realm")) && "collapsed".equals(card.get("outcome")),
					"the curse chosen reads %s", card);
			check(helper, PlaytestLog.EXPEDITION.equals(run.get("kind")) && "collapsed".equals(run.get("outcome")) && "80".equals(run.get("farthest_blocks")),
					"the Collapse reads %s", run);
			for (String id : chosen.keySet()) {
				check(helper, run.get("curses").contains(id), "the Collapse's cost %s does not hold %s", run.get("curses"), id);
			}

			// Switched off: the record is still written, but no card is sent
			Config.COLLAPSE_RECAP = 0;
			ExpeditionRecord.lastRecap = null;
			anchors.add(a = through(helper, p, d, 10));
			ExpeditionService.tick(p, d, Dims.END);
			a.clear(p.getStringUUID());
			ExpeditionService.tick(p, d, Dims.END);
			ExpeditionService.onDeath(p);
			ExpeditionService.onRespawn(p, d);
			for (int guard = 0; !d.pendingOffer.isEmpty() && guard < 8; guard++) {
				PenaltyService.choose(p, d.pendingOffer.get(0));
			}
			check(helper, d.records.isEmpty(), "the second Collapse's record still waits");
			check(helper, ExpeditionRecord.lastRecap == null, "a recap was sent with collapse_recap off");
			helper.succeed();
		} catch (IOException e) {
			throw new AssertionError("the playtest log could not be read back", e);
		} finally {
			Config.COLLAPSE_RECAP = recap;
			Config.PLAYTEST_LOG = log;
			PlaytestLog.folder = null;
			ExpeditionRecord.lastRecap = null;
			d.inRun = false;
			d.collapsing = false;
			d.collapseOwed = false;
			d.linked.clear();
			for (Anchor x : anchors) {
				DataStore.anchors().remove(x.key());
			}
			forget(dir);
		}
	}
}
