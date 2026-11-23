package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.extremedimensions.anchor.AnchorService;
import com.extremedimensions.compat.AnchorLook;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.events.UnstableAnchors;
import com.extremedimensions.expedition.DrainCauses;
import com.extremedimensions.expedition.ExpeditionBar;
import com.extremedimensions.expedition.ExpeditionService;
import com.extremedimensions.expedition.TimeLeft;
import com.extremedimensions.journal.AnchorList;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.seam.SeamDepth;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.Dims;
import com.extremedimensions.tether.TetherService;
import com.extremedimensions.world.WorldService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the time left comes to and what makes it run (3.55.0): the drain taken apart into its parts and its main cause,
 * the walk back and the pace, the warnings in words, the charge cut at the wound, a blocked anchor left out and told
 * of, a cracked one's reminder, a Wound Storm's bar, and the thread's pace in the Fray. Each changes something the
 * whole world shares (its anchors, a storm, the Seam's height), so each runs alone.
 */
public class TimeLeftTests {
	/** Seconds of charge an expedition begins with. */
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
		check(helper, d.inRun, "the expedition did not begin");
		d.arrivalX = p.getX() - out;
		d.arrivalZ = p.getZ();
		return a;
	}

	/** The expedition is over and its anchors gone, whatever the test did (the player's dimension as it was). */
	private static void home(ServerPlayer p, PlayerData d, String dim, List<Anchor> anchors) {
		d.inRun = false;
		d.linked.clear();
		d.dim = dim;
		TetherService.clear(p, d, false);
		for (Anchor a : anchors) {
			DataStore.anchors().remove(a.key());
		}
	}

	/** The part of a drain under this key, or NaN when it has none. */
	private static double part(DrainCauses.Breakdown why, String key) {
		for (DrainCauses.Factor f : why.factors()) {
			if (f.key().equals(key)) {
				return f.value();
			}
		}
		return Double.NaN;
	}

	/**
	 * The drain's parts multiply to the drain the expedition runs at, each as the expedition reckons it; the main cause is
	 * the largest besides the distance (here Accelerated Drain), and the boss bar names it; where the drain falls below
	 * one, the cause is what eases it most.
	 */
	@GameTest(environment = Alone.DRAIN_CAUSES)
	public void theDrainIsTakenApartIntoWhatMakesIt(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-drain-causes");
		PlayerData d = DataStore.get(p.getUUID());
		String dim = d.dim;
		int scars = d.scars, bar = Config.HUD_BOSS_BAR;
		List<Anchor> anchors = new ArrayList<>();
		try {
			d.curses.put(Penalty.ACCELERATED_DRAIN.id, 2);
			d.scars = 2;
			anchors.add(through(helper, p, d, 50));
			d.tetherTier = 1;
			d.dim = Dims.END;
			ExpeditionService.tick(p, d, Dims.END);
			DrainCauses.Breakdown why = DrainCauses.of(p, d);
			double product = 1;
			for (DrainCauses.Factor f : why.factors()) {
				product *= f.value();
			}
			check(helper, Math.abs(product - d.currentDrain) < 1e-9 && Math.abs(why.drain() - d.currentDrain) < 1e-9,
					"the parts %s come to %s and the breakdown to %s, not the drain %s", why.encode(), product, why.drain(), d.currentDrain);
			check(helper, Math.abs(part(why, DrainCauses.ACCELERATED) - (1 + Penalty.ACCELERATED_DRAIN.frac() * 2)) < 1e-9
					&& Math.abs(part(why, DrainCauses.WOVEN) - Config.SOUL_WOVEN_DRAIN) < 1e-9
					&& Math.abs(part(why, DrainCauses.SCARS) - (1 - Config.SCAR_DRAIN_REDUCTION * 2)) < 1e-9
					&& Math.abs(part(why, DrainCauses.DISTANCE) - TetherService.distanceFactor(p, d)) < 1e-9, "the parts read %s", why.encode());
			check(helper, DrainCauses.ACCELERATED.equals(why.cause()), "the main cause is %s, not Accelerated Drain (%s)", why.cause(), why.encode());
			Config.HUD_BOSS_BAR = 1;
			ExpeditionBar.update(p, d);
			String label = ExpeditionBar.label(p.getUUID());
			check(helper, label.contains(DrainCauses.ACCELERATED) || label.contains("Accelerated"), "the boss bar does not name the cause: %s", label);

			// Without the curse, at the portal: below one, and the cause is what eases it most
			d.curses.remove(Penalty.ACCELERATED_DRAIN.id);
			d.arrivalX = p.getX();
			d.arrivalZ = p.getZ();
			ExpeditionService.tick(p, d, Dims.END);
			why = DrainCauses.of(p, d);
			DrainCauses.Factor least = null;
			for (DrainCauses.Factor f : why.factors()) {
				if (least == null || f.value() < least.value()) {
					least = f;
				}
			}
			check(helper, why.drain() < 1 && least != null && least.key().equals(why.cause()), "below one (%s) the cause is %s, not the strongest ease (%s)",
					why.drain(), why.cause(), why.encode());
			check(helper, why.shown(), "a drain of %s below one is not shown", why.drain());
		} finally {
			d.curses.remove(Penalty.ACCELERATED_DRAIN.id);
			d.scars = scars;
			Config.HUD_BOSS_BAR = bar;
			ExpeditionBar.forget(p.getUUID());
			home(p, d, dim, anchors);
		}
		helper.succeed();
	}

	/**
	 * The walk back costs what half the way out drains, over the time it takes to walk; nothing at the portal. The words
	 * come once at the player's mark (one minute, two, or once the walk back is tight) and again only after the time
	 * climbed back over it, the title at half a minute, and the thread in the Seam is warned in its own words.
	 */
	@GameTest(environment = Alone.WALK_BACK)
	public void theWalkBackIsReckonedAndTheWarningsComeInWords(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-walk-back");
		PlayerData d = DataStore.get(p.getUUID());
		String dim = d.dim;
		List<Anchor> anchors = new ArrayList<>();
		try {
			anchors.add(through(helper, p, d, 100));
			d.dim = Dims.END;
			ExpeditionService.tick(p, d, Dims.END);
			double factor = TetherService.distanceFactor(p, d);
			double expect = 100 / TimeLeft.WALK_SPEED * (d.currentDrain / factor) * (1 + factor) / 2;
			double back = TimeLeft.walkBack(p, d);
			check(helper, Math.abs(back - expect) < 1e-6 && back > 1, "the walk back from 100 blocks reads %s, not %s", back, expect);
			d.arrivalX = p.getX();
			check(helper, TimeLeft.walkBack(p, d) < 1e-9, "at the portal the walk back still costs %s", TimeLeft.walkBack(p, d));
			d.arrivalX = p.getX() - 100;
			check(helper, Math.abs(TimeLeft.pace(300, 2.0) - 150) < 1e-9 && TimeLeft.pace(10, 0) < 0, "the pace is reckoned wrong");
			check(helper, TimeLeft.tight(back * 1.4, back) && !TimeLeft.tight(back * 1.6, back) && !TimeLeft.tight(5, 0),
					"tight is not under one and a half walks back");

			// At one minute (the default): not at ninety seconds, once at fifty-nine, not again a second later
			d.warnAt = TimeLeft.WARN_ONE_MINUTE;
			d.runWarned = 0;
			d.lastWarnWords = null;
			d.lastWarnTitle = null;
			TimeLeft.warn(p, d, 90, back, false);
			check(helper, d.lastWarnWords == null, "warned at 1:30: %s", d.lastWarnWords);
			TimeLeft.warn(p, d, 59, back, false);
			check(helper, "gui.extremedimensions.warn.tether".equals(d.lastWarnWords) && d.lastWarnTitle == null, "at 0:59 the words read %s, the title %s",
					d.lastWarnWords, d.lastWarnTitle);
			d.lastWarnWords = null;
			TimeLeft.warn(p, d, 58, back, false);
			check(helper, d.lastWarnWords == null, "warned twice");
			// time given back past the mark, and running down to it again: warned again
			TimeLeft.warn(p, d, 90, back, false);
			TimeLeft.warn(p, d, 59, back, false);
			check(helper, d.lastWarnWords != null, "not warned again after the time climbed back");
			TimeLeft.warn(p, d, 29, back, false);
			check(helper, "gui.extremedimensions.warn.title.tether".equals(d.lastWarnTitle), "no title at 0:29: %s", d.lastWarnTitle);

			// At two minutes, and once the walk back is tight
			d.runWarned = 0;
			d.lastWarnWords = null;
			d.warnAt = TimeLeft.WARN_TWO_MINUTES;
			TimeLeft.warn(p, d, 119, back, false);
			check(helper, d.lastWarnWords != null, "not warned at 1:59 when warned at two minutes");
			d.runWarned = 0;
			d.lastWarnWords = null;
			d.warnAt = TimeLeft.WARN_WALK_BACK;
			TimeLeft.warn(p, d, back * 2 + 40, back, false);
			check(helper, d.lastWarnWords == null, "warned with twice the walk back left");
			TimeLeft.warn(p, d, back * 1.4, back, false);
			check(helper, d.lastWarnWords != null, "not warned once the walk back needs most of what is left");

			// The thread in the Seam, by the rift
			d.seamWarned = 0;
			d.warnAt = TimeLeft.WARN_ONE_MINUTE;
			d.lastWarnWords = null;
			TimeLeft.warn(p, d, 50, 0, true);
			check(helper, "gui.extremedimensions.warn.thread.near".equals(d.lastWarnWords), "the thread's words read %s", d.lastWarnWords);
		} finally {
			d.warnAt = TimeLeft.WARN_ONE_MINUTE;
			d.runWarned = d.seamWarned = 0;
			d.lastWarn = d.lastBeat = d.seamLastWarn = d.seamLastBeat = Integer.MAX_VALUE;
			home(p, d, dim, anchors);
		}
		helper.succeed();
	}

	/** Leaky Tank spills a share at the wound; the timer's bar is measured from before it, so the share shows as gone. */
	@GameTest(environment = Alone.ENTRY_CUT)
	public void theChargeCutAtTheWoundShowsAsAlreadyGone(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-entry-cut");
		PlayerData d = DataStore.get(p.getUUID());
		String dim = d.dim;
		List<Anchor> anchors = new ArrayList<>();
		try {
			d.curses.put(Penalty.LEAKY_TANK.id, 1);
			anchors.add(through(helper, p, d, 0));
			double kept = CHARGE * (1 - Penalty.LEAKY_TANK.frac());
			check(helper, Math.abs(AnchorService.linkedCharge(p, d) - kept) < 1e-6, "Leaky Tank left %s, not %s", AnchorService.linkedCharge(p, d), kept);
			check(helper, Math.abs(d.runMax - CHARGE) < 1e-6, "the bar is measured against %s, not the %s held before the cut", d.runMax, CHARGE);
		} finally {
			d.curses.remove(Penalty.LEAKY_TANK.id);
			home(p, d, dim, anchors);
		}
		helper.succeed();
	}

	/**
	 * A blocked anchor beside the portal feeds the expedition nothing; the journal, Jade and WTHIT and the HUD say it is
	 * blocked; and once one of the expedition's own anchors is blocked, its leak counts in the drain, named as the cause.
	 */
	@GameTest(environment = Alone.BLOCKED_ANCHOR)
	public void aBlockedAnchorIsLeftOutToldOfAndCounted(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-blocked");
		PlayerData d = DataStore.get(p.getUUID());
		String dim = d.dim, uid = p.getStringUUID();
		List<Anchor> anchors = new ArrayList<>();
		BlockPos at = p.blockPosition();
		Anchor blocked = new Anchor(at.getX() + 2, at.getY(), at.getZ(), Dims.END);
		blocked.add(uid, 300);
		blocked.blocked = true;
		DataStore.anchors().put(blocked.key(), blocked);
		anchors.add(blocked);
		try {
			Anchor a = through(helper, p, d, 0);
			anchors.add(a);
			check(helper, d.linked.contains(a.key()) && !d.linked.contains(blocked.key()), "the expedition's anchors are %s", d.linked);
			check(helper, Math.abs(AnchorService.linkedCharge(p, d) - CHARGE) < 1e-6, "the expedition holds %s", AnchorService.linkedCharge(p, d));
			String list = AnchorList.encode(p);
			check(helper, list.contains("end," + blocked.x + "," + blocked.y + "," + blocked.z + ",300,300,0,,0,1"), "the journal does not say it is blocked: %s",
					list);
			if (Dims.OVERWORLD.equals(Dims.of(p))) {
				// (anchors are looked at in the Overworld only, where the tests' players stand)
				CompoundTag tag = new CompoundTag();
				AnchorLook.write(tag, p, new BlockPos(blocked.x, blocked.y, blocked.z));
				check(helper, tag.getIntOr(AnchorLook.BLOCKED, 0) == 1, "Jade and WTHIT are not told it is blocked: %s", tag);
			}
			check(helper, (AnchorService.nearTrouble(p, Dims.END) & 1) != 0, "the HUD is not told a Void Anchor nearby is blocked");

			// The expedition's own anchor blocked on the way
			a.blocked = true;
			d.dim = Dims.END;
			ExpeditionService.tick(p, d, Dims.END);
			DrainCauses.Breakdown why = DrainCauses.of(p, d);
			double leak = Config.BLOCKED_LEAK_PER_SECOND * a.of(uid) / a.charge;
			check(helper, Math.abs(why.drain() - (d.currentDrain + leak)) < 1e-9, "the drain shown is %s, not %s and the leak %s", why.drain(), d.currentDrain, leak);
			check(helper, DrainCauses.BLOCKED.equals(why.cause()), "the cause is %s, not the blocked anchor (%s)", why.cause(), why.encode());
			check(helper, TimeLeft.walkBack(p, d) < 1e-9, "at the portal the walk back costs %s", TimeLeft.walkBack(p, d));
		} finally {
			home(p, d, dim, anchors);
		}
		helper.succeed();
	}

	/** A cracked anchor reminds its owners a minute before it breaks (and not before); its leak is the drain's cause. */
	@GameTest(environment = Alone.CRACKED_REMINDER)
	public void aCrackedAnchorRemindsItsOwnersAMinuteBefore(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-cracked");
		PlayerData d = DataStore.get(p.getUUID());
		String dim = d.dim;
		List<Anchor> anchors = new ArrayList<>();
		Anchor a = null;
		try {
			a = through(helper, p, d, 0);
			anchors.add(a);
			a.unstable = UnstableAnchors.REMIND + 2;
			UnstableAnchors.lastReminded = null;
			UnstableAnchors.step(a);
			check(helper, UnstableAnchors.lastReminded == null, "reminded %s seconds before it breaks", a.unstable);
			UnstableAnchors.step(a);
			check(helper, (p.getUUID() + "@" + a.key()).equals(UnstableAnchors.lastReminded), "a minute before it breaks, the reminder went to %s",
					UnstableAnchors.lastReminded);
			d.dim = Dims.END;
			ExpeditionService.tick(p, d, Dims.END);
			DrainCauses.Breakdown why = DrainCauses.of(p, d);
			check(helper, DrainCauses.CRACKED.equals(why.cause()) && Math.abs(why.drain() - (d.currentDrain + Config.UNSTABLE_LEAK)) < 1e-9,
					"the cracked anchor's leak reads %s (%s), the drain %s", why.cause(), why.encode(), why.drain());
		} finally {
			if (a != null) {
				a.unstable = 0;
			}
			home(p, d, dim, anchors);
		}
		helper.succeed();
	}

	/** A Wound Storm's bar counts its time down while it rages, and comes down with it. */
	@GameTest(environment = Alone.STORM_BAR)
	public void aWoundStormCountsDownOnABar(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-storm-bar");
		try {
			WorldService.begin(p, helper.getLevel().getServer().getTickCount());
			String label = WorldService.stormLabel(p.getUUID());
			check(helper, WorldService.storming(p) && label.toLowerCase(Locale.ROOT).contains("storm"), "the storm has no bar: \"%s\"", label);
		} finally {
			WorldService.clear();
		}
		check(helper, WorldService.stormLabel(p.getUUID()).isEmpty(), "the storm's bar stayed after it");
		helper.succeed();
	}

	/** In the Fray the thread's drain (which the timer is now sent) is the Fray's, and the Fray is named as its cause. */
	@GameTest(environment = Alone.FRAY_PACE)
	public void theThreadsTimerKeepsPaceInTheFray(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-fray-pace");
		PlayerData d = DataStore.get(p.getUUID());
		boolean inSeam = d.inSeam;
		String dim = d.dim;
		int fray = Config.SEAM_FRAY;
		double x = p.getX(), y = p.getY(), z = p.getZ();
		try {
			Config.SEAM_FRAY = 1;
			d.inSeam = true;
			d.dim = Dims.SEAM;
			p.setPos(x, Config.SEAM_FRAY_TOP - 10, z);
			double drain = SeamService.drain(p);
			check(helper, Math.abs(drain - SeamDepth.drain(p.getY())) < 1e-9 && drain > 1, "in the Fray the thread runs at %s", drain);
			DrainCauses.Breakdown why = DrainCauses.of(p, d);
			check(helper, Math.abs(why.drain() - drain) < 1e-9 && DrainCauses.FRAY.equals(why.cause()), "the Fray reads %s at %s", why.cause(), why.drain());
			p.setPos(x, Config.SEAM_FRAY_TOP + 30, z);
			check(helper, SeamService.drain(p) == 1.0 && DrainCauses.of(p, d).cause().isEmpty(), "on the islands the thread runs at %s", SeamService.drain(p));
		} finally {
			Config.SEAM_FRAY = fray;
			d.inSeam = inSeam;
			d.dim = dim;
			p.setPos(x, y, z);
		}
		helper.succeed();
	}
}
