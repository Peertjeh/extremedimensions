package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.HashSet;
import java.util.Set;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.elder.ElderPower;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.WorldFx;
import com.extremedimensions.tapestry.Tapestry;
import com.extremedimensions.tapestry.Tapestry.Panel;
import com.extremedimensions.tapestry.Weaving;
import com.extremedimensions.trial.Trial;
import com.extremedimensions.trial.TrialService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * The Tapestry: threads from deeds (once, not while carried or woven), weaving only after the ending, the boons of each
 * panel in either world, one loom for the world, the Weaving held and broken, New Thread+ and the page in the player's
 * own hand. Each test changes the world's tapestry and puts it back as it found it within the same tick.
 */
public class TapestryTests {
	/** The world's tapestry as it was, to be put back. */
	private record Saved(String ending, Set<String> panels, String loom, String last, long again, boolean done, int threads) {
		static Saved take() {
			return new Saved(DataStore.ending(), new HashSet<>(DataStore.panels()), DataStore.loom(), DataStore.lastPanel(), DataStore.weaveAgainAt(),
					DataStore.tapestryDone(), DataStore.newThread());
		}

		void restore() {
			Weaving.clear();
			DataStore.setEnding(ending);
			DataStore.panels().clear();
			DataStore.panels().addAll(panels);
			DataStore.setLoom(loom);
			DataStore.setLastPanel(last);
			DataStore.setWeaveAgainAt(again);
			DataStore.setTapestryDone(done);
			DataStore.setNewThread(threads);
		}
	}

	private static int count(ServerPlayer p, Item item) {
		int n = 0;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(item)) {
				n += inv.getItem(i).getCount();
			}
		}
		return n;
	}

	private static BlockPos loom(GameTestHelper helper, int x, int z) {
		BlockPos at = helper.absolutePos(new BlockPos(x, 1, z));
		helper.getLevel().setBlockAndUpdate(at, ModBlocks.WEAVERS_LOOM.defaultBlockState());
		return at;
	}

	@GameTest
	public void threadsComeFromDeedsAndWaitForTheEnding(GameTestHelper helper) {
		Saved saved = Saved.take();
		try {
			ServerPlayer p = TestPlayers.survivor(helper, "tapestry-deeds");
			p.getInventory().clearContent();
			DataStore.panels().clear();
			DataStore.setLoom("");
			DataStore.setEnding("");
			BlockPos at = loom(helper, 1, 1);
			p.teleportTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 2.5);

			// A deed gives its thread once: not again while it is carried
			Tapestry.earn(p, Panel.ROOTS);
			Tapestry.earn(p, Panel.ROOTS);
			check(helper, count(p, ModItems.THREAD_OF_ROOTS) == 1, "the Matriarch's fall gave %s Threads of Roots", count(p, ModItems.THREAD_OF_ROOTS));
			check(helper, WorldFx.hasAdvancement(p, Advancements.LOOM_RECIPE), "an Elder's fall did not teach the Weaver's Loom");

			// Before the ending the loom will not take it
			ItemStack thread = new ItemStack(ModItems.THREAD_OF_ROOTS);
			p.getInventory().clearContent();
			p.setItemInHand(InteractionHand.MAIN_HAND, thread);
			Tapestry.use(p, at, thread);
			check(helper, !Tapestry.woven(Panel.ROOTS) && thread.getCount() == 1, "the loom took a thread before the ending");
			check(helper, DataStore.loom().endsWith("|" + at.getX() + "|" + at.getY() + "|" + at.getZ()), "the loom used is not the tapestry's (%s)",
					DataStore.loom());

			// After it, the panel is woven and the thread used; no thread comes for a woven panel
			DataStore.setEnding("mended");
			Tapestry.use(p, at, thread);
			check(helper, Tapestry.woven(Panel.ROOTS) && thread.isEmpty(), "the loom did not weave the Roots panel after the ending");
			check(helper, Tapestry.mended(Panel.ROOTS) && !Tapestry.severed(Panel.ROOTS), "the woven panel does not count as mended");
			Tapestry.earn(p, Panel.ROOTS);
			check(helper, count(p, ModItems.THREAD_OF_ROOTS) == 0, "a thread came for a panel already woven");
			check(helper, Tapestry.woven(Panel.ROOTS) && Tapestry.count() == 1, "the tapestry counts %s panels", Tapestry.count());

			// The first time at the loom after the ending, the journal goes on in the player's own hand
			PlayerData d = DataStore.get(p.getUUID());
			check(helper, d.journal.contains(JournalEntry.CONTINUED_MENDED.id) && !d.journal.contains(JournalEntry.CONTINUED_SEVERED.id),
					"the page in the player's own hand is not the mended world's");
			check(helper, !JournalEntry.CONTINUED_SEVERED.possible("mended") && JournalEntry.CONTINUED_MENDED.possible("mended"),
					"a mended world's journal asks for the severed page");
			check(helper, JournalEntry.CONTINUED_MENDED.own && !JournalEntry.CONTINUED_MENDED.wanderer, "the own-hand page is in the wrong hand");
			check(helper, !Journal.complete(d), "a journal of few pages counts as complete");
			helper.succeed();
		} finally {
			saved.restore();
		}
	}

	@GameTest
	public void eachPanelGivesItsWorldsBoon(GameTestHelper helper) {
		Saved saved = Saved.take();
		try {
			DataStore.panels().clear();
			// Nothing woven: nothing changes
			DataStore.setEnding("mended");
			check(helper, Tapestry.seamSeconds() == 0 && Tapestry.giftStep() == 1 && Tapestry.drain("the_nether") == 1.0, "an empty tapestry gives boons");
			for (Panel panel : Panel.values()) {
				DataStore.panels().add(panel.id);
			}
			// Mended
			check(helper, Tapestry.giftStep() == Config.TAPESTRY_ROOTS_GIFTS, "Roots (mended): gifts every %s", Tapestry.giftStep());
			check(helper, Tapestry.keepersHunt(), "Roots (mended) stopped the Keepers' hunt");
			check(helper, Math.abs(Tapestry.drain("the_nether") - Config.TAPESTRY_TOLL_DRAIN) < 1e-9 && Tapestry.drain("the_end") == 1.0,
					"Toll (mended): Nether drain %s, End drain %s", Tapestry.drain("the_nether"), Tapestry.drain("the_end"));
			check(helper, Tapestry.levelsBack() == 0, "Toll (mended) gives levels back");
			check(helper, Tapestry.seamSeconds() == Config.TAPESTRY_UNRAVELLING_SECONDS && Tapestry.thinShare() == 3, "Unravelling (mended) is wrong");
			check(helper, Tapestry.anchorSeconds() == Config.TAPESTRY_CHAIN_SECONDS && !Tapestry.steadyAnchors(), "Chain (mended) is wrong");
			check(helper, Math.abs(Tapestry.frayDrain(2.0) - Config.TAPESTRY_KNOT_FRAY) < 1e-9 && Tapestry.strandChance() == 1.0, "Knot (mended) is wrong");
			check(helper, Math.abs(Tapestry.echoShare(0.5) - Config.TAPESTRY_HEART_ECHO) < 1e-9, "Heart (mended): an echo gives back %s", Tapestry.echoShare(0.5));
			// Severed
			DataStore.setEnding("severed");
			check(helper, Tapestry.giftStep() == 1 && !Tapestry.keepersHunt(), "Roots (severed) is wrong");
			check(helper, Tapestry.drain("the_nether") == 1.0 && Tapestry.levelsBack() == Config.TAPESTRY_TOLL_LEVELS, "Toll (severed) is wrong");
			check(helper, Tapestry.seamSeconds() == 0 && Tapestry.thinShare() == Config.TAPESTRY_UNRAVELLING_SHARE, "Unravelling (severed) is wrong");
			check(helper, Tapestry.anchorSeconds() == 0 && Tapestry.steadyAnchors(), "Chain (severed) is wrong");
			check(helper, Tapestry.frayDrain(2.0) == 2.0 && Math.abs(Tapestry.strandChance() - Config.TAPESTRY_KNOT_STRANDS) < 1e-9, "Knot (severed) is wrong");
			check(helper, Tapestry.echoShare(0.5) == 0.5, "Heart (severed) changed the echoes");
			check(helper, !com.extremedimensions.events.UnstableAnchors.allowed(), "anchors still turn unstable under a severed Chain panel");
			// Heart (severed): one fall spared, the next not until the wait is over
			ServerPlayer p = TestPlayers.survivor(helper, "tapestry-heart");
			PlayerData d = DataStore.get(p.getUUID());
			d.heartSparedAt = 0;
			check(helper, Tapestry.spares(p, d), "the Heart panel spared no fall");
			check(helper, !Tapestry.spares(p, d), "the Heart panel spared two falls in a row");
			// The thread's lines name the numbers the server has
			check(helper, Panel.TOLL.boonArgs("severed").length == 1 && Panel.KNOT.boonArgs("mended").length == 2, "a boon's line lacks its numbers");
			helper.succeed();
		} finally {
			saved.restore();
		}
	}

	@GameTest
	public void oneLoomHoldsTheTapestry(GameTestHelper helper) {
		Saved saved = Saved.take();
		try {
			ServerPlayer p = TestPlayers.survivor(helper, "tapestry-looms");
			DataStore.setLoom("");
			BlockPos first = loom(helper, 1, 1), second = loom(helper, 3, 1);
			Tapestry.use(p, first, ItemStack.EMPTY);
			String held = DataStore.loom();
			Tapestry.use(p, second, ItemStack.EMPTY);
			check(helper, held.equals(DataStore.loom()), "a second loom took the tapestry (%s, was %s)", DataStore.loom(), held);
			// The first one gone: the next one used holds it
			helper.getLevel().setBlockAndUpdate(first, Blocks.AIR.defaultBlockState());
			Tapestry.use(p, second, ItemStack.EMPTY);
			check(helper, DataStore.loom().endsWith("|" + second.getX() + "|" + second.getY() + "|" + second.getZ()), "the tapestry did not pass to the loom left");
			// A lost thread is spun back from silk by one whose deed it was, not by another
			DataStore.setEnding("severed");
			DataStore.panels().clear();
			p.getInventory().clearContent();
			ItemStack silk = new ItemStack(ModItems.SEAM_SILK, 3);
			p.setItemInHand(InteractionHand.MAIN_HAND, silk);
			Tapestry.use(p, second, silk);
			check(helper, count(p, ModItems.THREAD_OF_THE_HEART) == 0 && silk.getCount() == 3, "too little silk spun a thread back");
			silk.setCount(Math.max(1, Config.TAPESTRY_RESPIN_SILK) + 1);
			Tapestry.use(p, second, silk);
			check(helper, count(p, ModItems.THREAD_OF_THE_HEART) == 1 && silk.getCount() == 1,
					"the Heart's thread was not spun back from silk (%s threads, %s silk)", count(p, ModItems.THREAD_OF_THE_HEART), silk.getCount());
			check(helper, count(p, ModItems.THREAD_OF_ROOTS) == 0, "a thread was spun for a deed this player never did");
			helper.succeed();
		} finally {
			saved.restore();
		}
	}

	@GameTest
	public void theWeavingHoldsOrUnravels(GameTestHelper helper) {
		Saved saved = Saved.take();
		try {
			ServerPlayer p = TestPlayers.survivor(helper, "tapestry-weaving");
			p.getInventory().clearContent();
			DataStore.setLoom("");
			DataStore.setTapestryDone(false);
			DataStore.setWeaveAgainAt(0);
			DataStore.setEnding("mended");
			BlockPos at = loom(helper, 1, 1);
			p.teleportTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 2.5);
			DataStore.panels().clear();
			for (Panel panel : Panel.values()) {
				if (panel != Panel.HEART) {
					DataStore.panels().add(panel.id);
				}
			}
			// The sixth panel begins the Weaving
			ItemStack heart = new ItemStack(ModItems.THREAD_OF_THE_HEART);
			p.setItemInHand(InteractionHand.MAIN_HAND, heart);
			Tapestry.use(p, at, heart);
			check(helper, Tapestry.count() == 6 && Weaving.active() && Weaving.ward() == 1.0, "the sixth panel did not begin the Weaving (%s panels)", Tapestry.count());
			// Broken: the last panel unravels, its thread falls at the loom, and the loom will not take it again yet
			Weaving.finishNow(false);
			check(helper, !Weaving.active() && !Tapestry.woven(Panel.HEART) && !DataStore.tapestryDone(), "a broken Weaving kept the last panel");
			check(helper, DataStore.weaveAgainAt() > System.currentTimeMillis(), "a broken Weaving can be tried again at once");
			boolean dropped = !helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(at).inflate(3),
					e -> e.getItem().is(ModItems.THREAD_OF_THE_HEART)).isEmpty();
			check(helper, dropped, "the unravelled panel's thread did not fall at the loom");
			heart = new ItemStack(ModItems.THREAD_OF_THE_HEART);
			p.setItemInHand(InteractionHand.MAIN_HAND, heart);
			Tapestry.use(p, at, heart);
			check(helper, !Tapestry.woven(Panel.HEART) && heart.getCount() == 1, "the loom took the unravelled panel again at once");
			// Held: the tapestry is finished
			DataStore.setWeaveAgainAt(0);
			Tapestry.use(p, at, heart);
			check(helper, Weaving.active(), "the Weaving did not begin again");
			Weaving.finishNow(true);
			check(helper, DataStore.tapestryDone() && Tapestry.count() == 6, "a held Weaving did not finish the tapestry");
			check(helper, WorldFx.hasAdvancement(p, Advancements.TAPESTRY), "holding the Weaving gave no advancement");
			helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(at).inflate(4)).forEach(ItemEntity::discard);
			helper.succeed();
		} finally {
			saved.restore();
		}
	}

	@GameTest
	public void aNewThreadIsHarder(GameTestHelper helper) {
		Saved saved = Saved.take();
		try {
			ServerPlayer p = TestPlayers.survivor(helper, "tapestry-thread");
			PlayerData d = DataStore.get(p.getUUID());
			DataStore.setNewThread(0);
			double health = ElderPower.health(1), damage = ElderPower.damage(1);
			Penalty curse = Penalty.values()[0];
			int cost = PenaltyService.cost(d, curse);
			Trial.Kind kind = Trial.Kind.values()[0];
			int gold = kind.thresholds()[2];
			check(helper, TrialService.threads(kind, gold * 2) == 0, "trials go on past gold before New Thread+");
			// Not before the tapestry is finished, nor away from its loom
			BlockPos at = loom(helper, 1, 1);
			DataStore.setLoom("");
			Tapestry.use(p, at, ItemStack.EMPTY);
			DataStore.setTapestryDone(false);
			Tapestry.newThread(p);
			check(helper, DataStore.newThread() == 0, "a new thread was taken up before the tapestry was finished");
			DataStore.setTapestryDone(true);
			p.teleportTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 2.5);
			d.matriarchSlain = true;
			Tapestry.newThread(p);
			check(helper, DataStore.newThread() == 1, "no new thread was taken up at the finished tapestry");
			check(helper, Math.abs(ElderPower.health(1) - health * (1 + Config.NEW_THREAD_ELDER)) < 1e-6
					&& Math.abs(ElderPower.damage(1) - damage * (1 + Config.NEW_THREAD_ELDER)) < 1e-6, "the Elders did not grow stronger");
			check(helper, PenaltyService.cost(d, curse) >= cost, "atonement grew cheaper with a new thread");
			check(helper, TrialService.threads(kind, gold + gold / 2) == Math.max(0, (gold / 2) / Math.max(1, gold / 4)), "a trial past gold counts %s threads",
					TrialService.threads(kind, gold + gold / 2));
			check(helper, !d.matriarchSlain, "the Matriarch does not come again in a new thread");
			check(helper, WorldFx.hasAdvancement(p, Advancements.NEW_THREAD), "taking up a new thread gave no advancement");
			helper.succeed();
		} finally {
			saved.restore();
		}
	}
}
