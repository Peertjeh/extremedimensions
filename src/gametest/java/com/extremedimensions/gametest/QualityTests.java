package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;
import static com.extremedimensions.gametest.CurseTests.near;

import com.extremedimensions.anchor.AnchorService;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.Hoard;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.expedition.ExpeditionRecord;
import com.extremedimensions.expedition.ExpeditionService;
import com.extremedimensions.expedition.RecapText;
import com.extremedimensions.journal.Ledger;
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.lurker.LurkerService;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.penalty.CurseTotals;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The 3.55.0 overhaul of what the player is told: curse totals, fragments that move on to the next curse, the reason a
 * wound stays shut, an Ember or Void Tether that would be wasted, and what a Lurker took. None of it changes what
 * anything costs or gives: these check that what is said is what the code does.
 */
public class QualityTests {
	/**
	 * A curse's total is worked out as the code applies it: Debt and Leaky Tank compound (Debt keeping a twentieth of
	 * every stack), Toll stops at its ceiling, Vertigo comes more often, and a curse without a number has no total.
	 */
	@GameTest
	public void curseTotalsAreWhatTheCurseDoes(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.DEBT, 3, Penalty.ACCELERATED_DRAIN, 2);
		PlayerData d = DataStore.get(p.getUUID());
		near(helper, CurseTotals.total(Penalty.DEBT, Penalty.DEBT.value, d.owned(Penalty.DEBT)), (1 - d.debtFactor()) * 100, "Debt at 3 stacks");
		near(helper, CurseTotals.total(Penalty.ACCELERATED_DRAIN, Penalty.ACCELERATED_DRAIN.value, 2), Penalty.ACCELERATED_DRAIN.value * 2,
				"Accelerated Drain at 2 stacks");
		near(helper, CurseTotals.total(Penalty.LEAKY_TANK, 10, 3), (1 - Math.pow(0.9, 3)) * 100, "Leaky Tank at 3 stacks of 10%");
		near(helper, CurseTotals.total(Penalty.DEBT, 99, 2), (1 - 0.05 * 0.05) * 100, "Debt keeps a twentieth of every stack");
		near(helper, CurseTotals.total(Penalty.TOLL, 50, 3), 90, "Toll held at its ceiling");
		check(helper, CurseTotals.capped(Penalty.TOLL, 50, 3) && !CurseTotals.capped(Penalty.TOLL, 15, 3), "Toll's ceiling not told right");
		near(helper, CurseTotals.total(Penalty.VERTIGO, 90, 2), 45, "Vertigo at 2 stacks");
		near(helper, CurseTotals.total(Penalty.VERTIGO, 90, 20), 10, "Vertigo never comes oftener than every 10 s");
		near(helper, CurseTotals.total(Penalty.SCAR, 2, 4), 8, "Scar takes at most 8 hearts");
		check(helper, CurseTotals.spec(Penalty.FAMINE) == null && CurseTotals.spec(Penalty.DWINDLING) == null
				&& CurseTotals.spec(Penalty.SUNSCORN) == null, "a curse whose card names no number has a total");
		check(helper, CurseTotals.total(Penalty.HEAVY_LEGS, 8, 0) == 0, "no stacks, yet a total");
		helper.succeed();
	}

	/**
	 * Bounced by a portal whose anchor the Tether is bound to: the player hears what is wrong with that anchor (none of
	 * their time in it, or blocked), not that it stands by another portal, though another anchor beside this one holds
	 * their time. Bound to one that really stands elsewhere, they still hear that.
	 */
	@GameTest(environment = Alone.BOUND_ANCHOR)
	public void aBoundAnchorBesideThePortalSaysWhatIsWrong(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "bound-here");
		PlayerData d = DataStore.get(p.getUUID());
		// Far off, where no other test's anchor stands
		d.hasLast = true;
		d.lastX = 71000.5;
		d.lastY = 70;
		d.lastZ = 71000.5;
		Anchor full = new Anchor(71002, 70, 71000, Dims.NETHER);
		Anchor bound = new Anchor(71000, 70, 71002, Dims.NETHER);
		Anchor far = new Anchor(72000, 70, 72000, Dims.NETHER);
		full.add(p.getStringUUID(), 300);
		far.add(p.getStringUUID(), 300);
		for (Anchor a : new Anchor[] {full, bound, far}) {
			DataStore.anchors().put(a.key(), a);
		}
		String was = d.tetherAnchor;
		try {
			d.tetherAnchor = bound.key();
			check(helper, "bound_empty".equals(ExpeditionService.dormant(p, d, Dims.NETHER, 300)), "bound to an empty anchor here: %s",
					ExpeditionService.dormant(p, d, Dims.NETHER, 300));
			bound.blocked = true;
			check(helper, "blocked".equals(ExpeditionService.dormant(p, d, Dims.NETHER, 300)), "bound to a blocked anchor here: %s",
					ExpeditionService.dormant(p, d, Dims.NETHER, 300));
			d.tetherAnchor = far.key();
			check(helper, "elsewhere".equals(ExpeditionService.dormant(p, d, Dims.NETHER, 300)), "bound to an anchor by another portal: %s",
					ExpeditionService.dormant(p, d, Dims.NETHER, 300));
		} finally {
			d.tetherAnchor = was;
			for (Anchor a : new Anchor[] {full, bound, far}) {
				DataStore.anchors().remove(a.key());
			}
		}
		helper.succeed();
	}

	/**
	 * A Lurker's theft is told: what it took (the item itself, shown on hover) and which way it ran, here east, to the
	 * player's own hoard it carries its loot to.
	 */
	@GameTest(environment = Alone.LURKER_TOLD, maxTicks = 200)
	public void aLurkerSaysWhatItTookAndWhereItRan(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Difficulty was = level.getDifficulty();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer p = TestPlayers.survivor(helper, "lurker-told");
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
		p.snapTo(at.x, at.y, at.z, 0f, 0f);
		double xp = Config.LURKER_XP_WEIGHT, armor = Config.LURKER_ARMOR_WEIGHT, curseWeight = Config.LURKER_CURSE_WEIGHT;
		double curseChance = Config.LURKER_CURSE_CHANCE;
		int thefts = Config.LURKER_THEFTS;
		// Its hoard, east of the player: the Lurker runs there, and builds none of its own
		Hoard hoard = new Hoard(Dims.of(p), (int) Math.floor(at.x) + 30, (int) Math.floor(at.y), (int) Math.floor(at.z), p.getStringUUID());
		// past the grace a player has on joining (three seconds)
		helper.runAfterDelay(70, () -> {
			Config.LURKER_XP_WEIGHT = 0;
			Config.LURKER_ARMOR_WEIGHT = 0;
			Config.LURKER_CURSE_WEIGHT = 0;
			Config.LURKER_CURSE_CHANCE = 0;
			Config.LURKER_THEFTS = 1;
			p.getInventory().clearContent();
			p.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
			p.setHealth(20f);
			DataStore.hoards().add(hoard);
			LurkerService.lastTheft = null;
			Lurker l = ModEntities.LURKER.spawn(level, helper.absolutePos(new BlockPos(4, 2, 2)), EntitySpawnReason.COMMAND);
			check(helper, l != null, "no Lurker came");
			LurkerService.adopt(level, l, p, "take");
			helper.runAfterDelay(40, () -> {
				try {
					LurkerService.Told told = LurkerService.lastTheft;
					check(helper, told != null && told.to().equals(p.getUUID()), "the theft was not told to its victim");
					check(helper, told.theft().stacks().size() == 1 && told.theft().stacks().get(0).is(Items.DIAMOND_PICKAXE),
							"the theft told of %s, not the pickaxe", told.theft().stacks());
					check(helper, told.theft().hoard() && "east".equals(told.theft().bearing()), "it was told to run %s (to its hoard: %s), not east to it",
							told.theft().bearing(), told.theft().hoard());
				} finally {
					Config.LURKER_XP_WEIGHT = xp;
					Config.LURKER_ARMOR_WEIGHT = armor;
					Config.LURKER_CURSE_WEIGHT = curseWeight;
					Config.LURKER_CURSE_CHANCE = curseChance;
					Config.LURKER_THEFTS = thefts;
					LurkerService.lastTheft = null;
					DataStore.hoards().remove(hoard);
					// (gone without its loot, which would otherwise be carried to the hoard)
					l.discard();
					level.getServer().setDifficulty(was, true);
				}
				helper.succeed();
			});
		});
	}

	/**
	 * A Void Tether used on an anchor with room for less than half of it only warns; the same use again a moment later
	 * spends it (filling the anchor). One that mostly fits is spent at once.
	 */
	@GameTest(environment = Alone.TETHER_WASTE, maxTicks = 120)
	public void aTetherThatWouldMostlyGoToWasteAsksFirst(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "tether-waster");
		PlayerData d = DataStore.get(p.getUUID());
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2, 1.5));
		p.snapTo(at.x, at.y, at.z, 0f, 0f);
		helper.setBlock(new BlockPos(2, 2, 5), Blocks.END_PORTAL_FRAME.defaultBlockState());
		BlockPos stone = helper.absolutePos(new BlockPos(2, 2, 3));
		helper.setBlock(new BlockPos(2, 2, 3), Blocks.LODESTONE.defaultBlockState());
		Anchor a = new Anchor(stone.getX(), stone.getY(), stone.getZ(), Dims.END);
		String uid = p.getStringUUID();
		double cap = AnchorService.cap(d), full = Config.TETHER_ITEM_CHARGE * AnchorService.worth(d);
		// Room for a fifth of it
		a.add(uid, Math.max(0, cap - full / 5));
		DataStore.anchors().put(a.key(), a);
		double before = a.own(uid);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.VOID_TETHER, 2));
		AnchorService.use(p, stone);
		AnchorService.use(p, stone);
		check(helper, p.getMainHandItem().getCount() == 2 && a.own(uid) == before, "a Void Tether that would mostly be wasted was spent at once");
		helper.runAfterDelay(20, () -> {
			try {
				AnchorService.use(p, stone);
				check(helper, p.getMainHandItem().getCount() == 1 && Math.abs(a.own(uid) - cap) < 1e-6,
						"used again, the Void Tether was not spent (%s left, %s of %s in the anchor)", p.getMainHandItem().getCount(), a.own(uid), cap);
				// Room for most of it: spent at once
				a.clear(uid);
				a.add(uid, Math.max(0, cap - full * 0.8));
				AnchorService.use(p, stone);
				check(helper, p.getMainHandItem().isEmpty(), "a Void Tether that mostly fits was not spent at once");
			} finally {
				DataStore.anchors().remove(a.key());
				AnchorService.forget(p.getUUID());
			}
			helper.succeed();
		});
	}

	/**
	 * The journal keeps the last five falls, newest first, as their cards had them, and its ledger carries them to the
	 * page of the last falls; the words of a kept card read as the card's own did.
	 */
	@GameTest
	public void theLastFallsAreKeptForTheJournal(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "fallen-often");
		PlayerData d = DataStore.get(p.getUUID());
		long now = System.currentTimeMillis();
		for (int i = 0; i < 7; i++) {
			ExpeditionRecord.keep(d, now - (7 - i) * 60_000L, new ModPackets.RecapPayload(Dims.NETHER, "collapsed", 60 + i, 600, 100 + i, 150, 210,
					"heavy_legs:2,debt:1", 0, 0, 30));
		}
		check(helper, d.falls.size() == RecapText.KEPT, "%s falls kept, not %s", d.falls.size(), RecapText.KEPT);
		ModPackets.RecapPayload newest = RecapText.read(d.falls.get(0));
		check(helper, newest != null && newest.seconds() == 66 && newest.farthest() == 106 && "heavy_legs:2,debt:1".equals(newest.curses())
				&& newest.atonement() == 30, "the newest fall kept reads %s", d.falls.get(0));
		check(helper, RecapText.head(d.falls.get(0)) == now - 60_000L, "the newest fall was kept as of %s", RecapText.head(d.falls.get(0)));
		// two curses, the Tether and the atonement undone
		check(helper, RecapText.cost(newest).size() == 4, "the kept card's cost reads %s parts", RecapText.cost(newest).size());
		String ledger = Ledger.encode(d);
		check(helper, ledger.contains("falls:1=") && ledger.contains("|" + Dims.NETHER + "|collapsed|66|") && ledger.contains("falls:5=")
				&& !ledger.contains("falls:6="),
				"the ledger's falls read %s", ledger);
		check(helper, ExpeditionRecord.chatLine(newest) != null, "a kept fall makes no chat line");
		helper.succeed();
	}

	/**
	 * Several curses owed in a row: the screen is told which of them the offer is ("Curse 2 of 3") and how long before
	 * the Tether chooses, until the last is answered.
	 */
	@GameTest
	public void cursesOwedInARowSayWhichThisIs(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "owing-three");
		PlayerData d = DataStore.get(p.getUUID());
		check(helper, PenaltyService.offerState(p, d).isEmpty(), "an offer is told with none waiting");
		for (int i = 0; i < 3; i++) {
			PenaltyService.owe(d, false);
		}
		String[] s = PenaltyService.offerState(p, d).split(":");
		check(helper, s.length == 3 && "1".equals(s[1]) && "3".equals(s[2]), "the first of three reads %s", String.join(":", s));
		check(helper, Config.OFFER_TIMEOUT_SECONDS <= 0 || Integer.parseInt(s[0]) == Config.OFFER_TIMEOUT_SECONDS,
				"a fresh offer has %s s left, not the whole %s", s[0], Config.OFFER_TIMEOUT_SECONDS);
		PenaltyService.choose(p, d.pendingOffer.get(0));
		s = PenaltyService.offerState(p, d).split(":");
		check(helper, s.length == 3 && "-1".equals(s[0]) && "2".equals(s[1]) && "3".equals(s[2]),
				"the second of three (its cards not shown yet) reads %s", String.join(":", s));
		PenaltyService.choose(p, d.pendingOffer.get(0));
		PenaltyService.choose(p, d.pendingOffer.get(0));
		check(helper, d.pendingOffer.isEmpty() && PenaltyService.offerState(p, d).isEmpty() && d.offerSeries == 0,
				"after the third the offer still reads %s", PenaltyService.offerState(p, d));
		helper.succeed();
	}

	/**
	 * Fragments go on to the next curse once the chosen curse's stack has had its five: the chosen one first, then the
	 * heaviest other, each fragment giving what it always gave; with every stack's fragments given, the next is kept;
	 * and a player with no curse keeps theirs.
	 */
	@GameTest
	public void fragmentsMoveOnToTheNextCurse(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.KINDLING, 1, Penalty.GLASS_JAW, 2, Penalty.VOLATILE, 1);
		PlayerData d = DataStore.get(p.getUUID());
		double share = Config.FRAGMENT_ATONE_SHARE;
		int max = Config.FRAGMENT_MAX_PER_STACK;
		try {
			Config.FRAGMENT_ATONE_SHARE = 0.1;
			Config.FRAGMENT_MAX_PER_STACK = 2;
			// Kindling is the one being atoned for, though Glass Jaw is heavier
			d.atoning = Penalty.KINDLING.id;
			BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
			ItemStack fragments = new ItemStack(ModItems.TETHER_FRAGMENT, 16);
			for (int i = 0; i < 2; i++) {
				PenaltyService.mend(p, d, at, fragments);
			}
			check(helper, d.fragmentsOffered(Penalty.KINDLING) == 2 && d.fragmentsOffered(Penalty.GLASS_JAW) == 0,
					"the first two fragments did not go to the curse being atoned for");
			// The third: Kindling's stack has had its two, so it goes to the heaviest other, Glass Jaw, and gives a tenth
			int left = PenaltyService.cost(d, Penalty.GLASS_JAW) - d.atonement.getOrDefault(Penalty.GLASS_JAW.id, 0);
			PenaltyService.mend(p, d, at, fragments);
			check(helper, d.fragmentsOffered(Penalty.GLASS_JAW) == 1 && fragments.getCount() == 13,
					"the third fragment did not go on to Glass Jaw (%s offered there, %s left in hand)", d.fragmentsOffered(Penalty.GLASS_JAW),
					fragments.getCount());
			check(helper, d.atonement.getOrDefault(Penalty.GLASS_JAW.id, 0) == Math.max(1, (int) Math.ceil(left * 0.1)),
					"a fragment gone on gave %s, not a tenth of the %s left", d.atonement.getOrDefault(Penalty.GLASS_JAW.id, 0), left);
			// Glass Jaw's second, then Volatile's two; after that every stack has had its fragments, and the next is kept
			for (int i = 0; i < 3; i++) {
				PenaltyService.mend(p, d, at, fragments);
			}
			check(helper, d.fragmentsOffered(Penalty.GLASS_JAW) == 2 && d.fragmentsOffered(Penalty.VOLATILE) == 2 && fragments.getCount() == 10,
					"the fragments did not go on to Volatile once Glass Jaw had its two");
			PenaltyService.mend(p, d, at, fragments);
			check(helper, fragments.getCount() == 10, "an anchor took a fragment no curse's stack had room for");
			check(helper, d.owned(Penalty.KINDLING) == 1 && d.owned(Penalty.GLASS_JAW) == 2 && d.owned(Penalty.VOLATILE) == 1,
					"two fragments a stack lifted a stack");
		} finally {
			Config.FRAGMENT_ATONE_SHARE = share;
			Config.FRAGMENT_MAX_PER_STACK = max;
		}
		// No curse at all: the fragment is kept
		ServerPlayer clean = TestPlayers.survivor(helper, "clean-mender");
		ItemStack one = new ItemStack(ModItems.TETHER_FRAGMENT, 1);
		PenaltyService.mend(clean, DataStore.get(clean.getUUID()), helper.absolutePos(new BlockPos(1, 1, 1)), one);
		check(helper, one.getCount() == 1, "a player with no curse lost a fragment to an anchor");
		helper.succeed();
	}
}
