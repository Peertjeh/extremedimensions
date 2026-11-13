package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.guide.Guide;
import com.extremedimensions.guide.Hints;
import com.extremedimensions.guide.Path;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModItems;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** The Wanderer's path in the journal (each step done by doing it, a line on the next) and the first-meeting hints. */
public class PathTests {
	@GameTest
	public void thePathFollowsWhatWasDone(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "path-walker");
		PlayerData d = DataStore.get(p.getUUID());
		p.getInventory().clearContent();
		check(helper, Path.current(p, d) == Path.Step.RETURN, "a new player's next step is %s", Path.current(p, d));
		check(helper, "gui.extremedimensions.guide.1".equals(Path.hint(p, d)), "a new player is told %s", Path.hint(p, d));
		// The first expedition says what the guide card says, step by step
		d.guide = Guide.TETHER;
		check(helper, "gui.extremedimensions.guide.3".equals(Path.hint(p, d)), "bound to an anchor, the player is told %s", Path.hint(p, d));
		d.guide = Guide.STEPS;
		check(helper, Path.current(p, d) == Path.Step.SEAM, "back from the first expedition, the next step is %s", Path.current(p, d));
		check(helper, "gui.extremedimensions.path.seam.craft".equals(Path.hint(p, d)), "without a needle the player is told %s", Path.hint(p, d));
		p.getInventory().add(new ItemStack(ModItems.SEAM_NEEDLE));
		check(helper, "gui.extremedimensions.path.seam.tear".equals(Path.hint(p, d)), "with a needle the player is told %s", Path.hint(p, d));
		d.seamVisits = 1;
		check(helper, Path.current(p, d) == Path.Step.SEAM_HOME, "in the Seam for the first time, the next step is %s", Path.current(p, d));
		Advancements.grant(p, Advancements.SEAM_RETURNED);
		check(helper, Path.done(p, d, Path.Step.SEAM_HOME), "home from the Seam does not count");
		// The world's steps are the world's: done for this player as soon as anyone did them (other tests may have)
		check(helper, Path.done(p, d, Path.Step.SEAL) == DataStore.sealBroken(), "the seal on the path and in the world differ");
		String[] sent = Path.encode(p, d).split(";");
		int steps = Integer.parseInt(sent[0]);
		check(helper, sent.length == 4 && (steps & 7) == 7, "the journal is sent %s", String.join(";", sent));
		helper.succeed();
	}

	/**
	 * The whole path walked, the world chosen: the journal says the world is not finished and names the six threads and
	 * the Weaving, until the tapestry is; then it says so. (Alone: the seal, the Starved One and the ending are the
	 * world's, and other tests read them.)
	 */
	@GameTest(environment = Alone.PATH_ENDING)
	public void afterTheChoiceTheWorldIsNotYetFinished(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "path-chooser");
		PlayerData d = DataStore.get(p.getUUID());
		boolean seal = DataStore.sealBroken(), starved = DataStore.starvedSlain(), woven = DataStore.tapestryDone();
		String ending = DataStore.ending();
		try {
			d.guide = Guide.STEPS;
			d.seamVisits = 1;
			Advancements.grant(p, Advancements.SEAM_RETURNED);
			DataStore.setSealBroken(true);
			DataStore.setStarvedSlain(true);
			DataStore.setTapestryDone(false);
			DataStore.setEnding("");
			check(helper, Path.current(p, d) == Path.Step.CHOOSE, "with the Starved One slain the next step is %s", Path.current(p, d));
			DataStore.setEnding("mended");
			check(helper, Path.current(p, d) == null, "with the world chosen the path still names %s", Path.current(p, d));
			check(helper, "gui.extremedimensions.path.beyond".equals(Path.hint(p, d)), "with the world chosen the player is told %s", Path.hint(p, d));
			int tapestry = 1 << Path.Side.TAPESTRY.ordinal();
			String[] sent = Path.encode(p, d).split(";");
			check(helper, (Integer.parseInt(sent[1]) & tapestry) == 0, "the tapestry counts as finished before it is (%s)", String.join(";", sent));
			DataStore.setTapestryDone(true);
			check(helper, "gui.extremedimensions.path.finished".equals(Path.hint(p, d)), "with the tapestry finished the player is told %s", Path.hint(p, d));
			sent = Path.encode(p, d).split(";");
			check(helper, (Integer.parseInt(sent[1]) & tapestry) != 0, "the finished tapestry is not sent to the journal (%s)", String.join(";", sent));
		} finally {
			DataStore.setSealBroken(seal);
			DataStore.setStarvedSlain(starved);
			DataStore.setTapestryDone(woven);
			DataStore.setEnding(ending);
		}
		helper.succeed();
	}

	@GameTest
	public void besideThePathOnceItsPageIsKnown(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "path-beside");
		PlayerData d = DataStore.get(p.getUUID());
		int companion = 1 << Path.Side.COMPANION.ordinal(), matriarch = 1 << Path.Side.MATRIARCH.ordinal();
		String[] sent = Path.encode(p, d).split(";");
		check(helper, (Integer.parseInt(sent[2]) & companion) == 0, "the Hollowling stands beside the path before its page is known");
		d.journal.add(JournalEntry.HOLLOWS.id);
		sent = Path.encode(p, d).split(";");
		check(helper, (Integer.parseInt(sent[2]) & companion) != 0 && (Integer.parseInt(sent[1]) & companion) == 0,
				"with its page known the Hollowling reads %s", String.join(";", sent));
		d.companion = true;
		sent = Path.encode(p, d).split(";");
		check(helper, (Integer.parseInt(sent[1]) & companion) != 0, "a Hollowling at the player's side is not counted");
		check(helper, (Integer.parseInt(sent[2]) & matriarch) == 0, "the Matriarch shows beside the path before her page");
		helper.succeed();
	}

	@GameTest
	public void aHintComesOnce(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "hint-once");
		PlayerData d = DataStore.get(p.getUUID());
		d.hints.clear();
		d.hintsOffered.clear();
		d.hintsChecked = true;
		Hints.meet(p, Hints.Hint.ELDER);
		Hints.meet(p, Hints.Hint.ELDER);
		check(helper, d.hintsOffered.size() == 1 && d.hintsOffered.contains("elder"), "the Elder's hint was sent as %s", d.hintsOffered);
		Hints.shown(p, Hints.Hint.ELDER.ordinal());
		check(helper, d.hints.size() == 1 && d.hints.contains("elder"), "the Elder's hint was kept as %s", d.hints);
		// Turned off for the world, none comes
		int was = Config.HINTS;
		Config.HINTS = 0;
		Hints.meet(p, Hints.Hint.STORM);
		Config.HINTS = was;
		check(helper, !Hints.offered(p, Hints.Hint.STORM), "a hint came while hints were off");
		// Something held for the first time, and an advancement's moment
		p.getInventory().clearContent();
		p.getInventory().add(new ItemStack(ModItems.TETHER_FRAGMENT));
		Hints.look(p, d);
		check(helper, Hints.offered(p, Hints.Hint.FRAGMENT), "a fragment in hand brought no hint (%s)", d.hintsOffered);
		Advancements.grant(p, Advancements.WATCHED);
		check(helper, Hints.offered(p, Hints.Hint.LURKER), "being watched brought no hint (%s)", d.hintsOffered);
		helper.succeed();
	}

	/**
	 * A hint counts as told only once the player's game showed it (3.55.0): sent and not shown (hints off in the player's
	 * own settings), it is not sent again that visit, nor told, and comes again on the next visit; a hint never sent
	 * cannot be marked as shown; and the journal is sent the hints told, in the order they came.
	 */
	@GameTest
	public void aHintIsToldOnlyOnceShown(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "hint-shown");
		PlayerData d = DataStore.get(p.getUUID());
		d.hints.clear();
		d.hintsOffered.clear();
		d.hintsChecked = true;
		Hints.meet(p, Hints.Hint.SILK);
		check(helper, !d.hints.contains("silk") && d.hintsOffered.contains("silk"), "a hint sent was told before it was shown (%s)", d.hints);
		// never shown (hints off on the player's side): not sent again this visit
		Hints.meet(p, Hints.Hint.SILK);
		check(helper, d.hintsOffered.size() == 1 && d.hints.isEmpty(), "a hint not shown was sent again or told (%s / %s)", d.hintsOffered, d.hints);
		// a hint the server never sent is not told because the game says so
		Hints.shown(p, Hints.Hint.STORM.ordinal());
		Hints.shown(p, -1);
		Hints.shown(p, Hints.Hint.values().length);
		check(helper, d.hints.isEmpty(), "a hint never sent was marked as told (%s)", d.hints);
		// the next visit: what was never shown comes again, and once shown it is told for good
		Hints.known(p, d);
		check(helper, !Hints.offered(p, Hints.Hint.SILK), "a hint never shown still counts as sent after joining again");
		Hints.meet(p, Hints.Hint.SILK);
		Hints.meet(p, Hints.Hint.NEEDLE);
		Hints.shown(p, Hints.Hint.NEEDLE.ordinal());
		Hints.shown(p, Hints.Hint.SILK.ordinal());
		check(helper, d.hints.contains("silk") && d.hints.contains("needle") && d.hintsOffered.isEmpty(), "shown hints were not told (%s)", d.hints);
		Hints.known(p, d);
		Hints.meet(p, Hints.Hint.SILK);
		check(helper, d.hintsOffered.isEmpty(), "a hint told was sent again");
		check(helper, "needle,silk".equals(Hints.told(d)), "the journal is sent the hints as %s", Hints.told(d));
		helper.succeed();
	}

	@GameTest
	public void aVeteranIsNotToldWhatTheyKnow(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "hint-veteran");
		PlayerData d = DataStore.get(p.getUUID());
		d.hints.clear();
		d.hintsChecked = false;
		d.collapses = 2;
		d.seamVisits = 3;
		d.journal.add(JournalEntry.FRAGMENT.id);
		Hints.known(p, d);
		check(helper, d.hints.contains("collapse") && d.hints.contains("needle") && d.hints.contains("fragment"),
				"what a veteran knew was not marked (%s)", d.hints);
		check(helper, !d.hints.contains("storm") && !d.hints.contains("elder"), "what a veteran never met was marked (%s)", d.hints);
		// ... and looked at only once: a new player's first Collapse later still brings its hint
		d.hints.remove("collapse");
		Hints.known(p, d);
		check(helper, !d.hints.contains("collapse"), "what they knew was looked at again");
		helper.succeed();
	}
}
