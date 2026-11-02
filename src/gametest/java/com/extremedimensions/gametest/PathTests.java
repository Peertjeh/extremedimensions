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
		d.hintsChecked = true;
		Hints.meet(p, Hints.Hint.ELDER);
		Hints.meet(p, Hints.Hint.ELDER);
		check(helper, d.hints.size() == 1 && d.hints.contains("elder"), "the Elder's hint was kept as %s", d.hints);
		// Turned off for the world, none comes
		int was = Config.HINTS;
		Config.HINTS = 0;
		Hints.meet(p, Hints.Hint.STORM);
		Config.HINTS = was;
		check(helper, !d.hints.contains("storm"), "a hint came while hints were off");
		// Something held for the first time, and an advancement's moment
		p.getInventory().clearContent();
		p.getInventory().add(new ItemStack(ModItems.TETHER_FRAGMENT));
		Hints.look(p, d);
		check(helper, d.hints.contains("fragment"), "a fragment in hand brought no hint (%s)", d.hints);
		Advancements.grant(p, Advancements.WATCHED);
		check(helper, d.hints.contains("lurker"), "being watched brought no hint (%s)", d.hints);
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
