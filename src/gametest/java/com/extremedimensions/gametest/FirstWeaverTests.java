package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.guide.Path;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.journal.MarginNote;
import com.extremedimensions.lore.FirstWeaver;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.WorldFx;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * The First Weaver's trail, from start to end: the frame strung with silk, the threads of the bastion, the End city and
 * the loom, the Plumb Line's bearing and the First Knot it points to (the Knot Mother herself is in BossTests).
 */
public class FirstWeaverTests {
	private static boolean holds(ServerPlayer p, net.minecraft.world.item.Item item) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(item)) {
				return true;
			}
		}
		return false;
	}

	@GameTest
	public void theTrailRunsThroughFourWorlds(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "first-weaver");
		PlayerData d = DataStore.get(p.getUUID());
		p.getInventory().clearContent();
		BlockPos frame = helper.absolutePos(new BlockPos(1, 2, 1));
		helper.getLevel().setBlockAndUpdate(frame, Blocks.REINFORCED_DEEPSLATE.defaultBlockState());
		check(helper, FirstWeaver.isFrame(helper.getLevel(), frame), "Reinforced Deepslate in the Overworld is not the frame");

		// Not on the trail yet: the bastion's chest holds nothing of it
		List<ItemStack> found = new ArrayList<>();
		FirstWeaver.chest(BuiltInLootTables.BASTION_TREASURE, p, found::add);
		check(helper, found.isEmpty(), "a bastion gave a thread to one not on the trail");

		// Too little silk: nothing happens
		ItemStack silk = new ItemStack(ModItems.SEAM_SILK, 2);
		p.setItemInHand(InteractionHand.MAIN_HAND, silk);
		FirstWeaver.string(p, frame, silk);
		check(helper, !d.frameStrung && silk.getCount() == 2, "two silk strung the frame");

		// Enough: the frame is strung, shows what it held and leaves the Echo Thread, with its page, note and advancement
		silk = new ItemStack(ModItems.SEAM_SILK, 6);
		p.setItemInHand(InteractionHand.MAIN_HAND, silk);
		FirstWeaver.string(p, frame, silk);
		check(helper, d.frameStrung && silk.getCount() == 2, "the frame was not strung with four silk (strung %s, %s silk left)", d.frameStrung, silk.getCount());
		check(helper, holds(p, ModItems.ECHO_THREAD), "the strung frame left no Echo Thread");
		check(helper, d.journal.contains(JournalEntry.FRAME.id) && d.notes.contains(MarginNote.WEAVERS.id), "the frame's page or note is missing");
		check(helper, WorldFx.hasAdvancement(p, Advancements.FRAME_STRUNG), "stringing the frame gave no advancement");
		check(helper, FirstWeaver.visions() > 0, "the strung frame shows nothing");
		// Strung again while the thread is in the bag: no second thread, no silk taken
		FirstWeaver.string(p, frame, silk);
		check(helper, silk.getCount() == 2, "the frame took silk a second time");

		// The bastion and the End city keep their threads for one on the trail, once each while carried
		FirstWeaver.chest(BuiltInLootTables.BASTION_OTHER, p, found::add);
		check(helper, found.size() == 1 && found.get(0).is(ModItems.EMBER_THREAD), "a bastion's chest gave %s", found);
		p.getInventory().add(found.get(0));
		found.clear();
		FirstWeaver.chest(BuiltInLootTables.BASTION_TREASURE, p, found::add);
		check(helper, found.isEmpty(), "a second Ember Thread came while one was carried");
		FirstWeaver.chest(BuiltInLootTables.SIMPLE_DUNGEON, p, found::add);
		check(helper, found.isEmpty(), "a dungeon's chest gave a thread");
		FirstWeaver.chest(BuiltInLootTables.END_CITY_TREASURE, p, found::add);
		check(helper, found.size() == 1 && found.get(0).is(ModItems.PALE_THREAD), "an End city's chest gave %s", found);
		p.getInventory().add(found.get(0));

		// The loom in the Seam: its tapestry gives the Silk Thread once; after that it is only a loom
		InteractionResult loom = FirstWeaver.loom(p, frame.above());
		check(helper, loom == InteractionResult.SUCCESS && holds(p, ModItems.SILK_THREAD), "the loom gave no Silk Thread");
		check(helper, FirstWeaver.loom(p, frame.above()) == InteractionResult.PASS, "the loom gave a second thread");

		// Beside the path in the journal, the trail is now known
		String[] sent = Path.encode(p, d).split(";");
		int trail = 1 << Path.Side.FIRST_WEAVER.ordinal();
		check(helper, (Integer.parseInt(sent[2]) & trail) != 0 && (Integer.parseInt(sent[1]) & trail) == 0, "the trail beside the path reads %s",
				String.join(";", sent));

		// Twisted into the Plumb Line, no thread comes again
		p.getInventory().clearContent();
		p.getInventory().add(new ItemStack(ModItems.PLUMB_LINE));
		found.clear();
		FirstWeaver.chest(BuiltInLootTables.BASTION_TREASURE, p, found::add);
		check(helper, found.isEmpty(), "a thread came to one who already twisted the Plumb Line");
		helper.succeed();
	}

	@GameTest
	public void theLinePointsToTheKnot(GameTestHelper helper) {
		check(helper, "north".equals(com.extremedimensions.seam.FirstKnot.bearing(0, -10)), "towards -z is not north");
		check(helper, "east".equals(com.extremedimensions.seam.FirstKnot.bearing(10, 0)), "towards +x is not east");
		check(helper, "south_east".equals(com.extremedimensions.seam.FirstKnot.bearing(7, 7)), "towards +x+z is not south-east");
		check(helper, "north_west".equals(com.extremedimensions.seam.FirstKnot.bearing(-7, -7)), "towards -x-z is not north-west");
		// Far out from the Heart, the same for the same seed, and deep in the Fray
		int[] a = com.extremedimensions.seam.FirstKnot.find(1234L), b = com.extremedimensions.seam.FirstKnot.find(1234L);
		double far = Math.sqrt((double) a[0] * a[0] + (double) a[2] * a[2]);
		check(helper, a[0] == b[0] && a[2] == b[2], "the First Knot moved for the same seed");
		check(helper, far > 300 && far < 1500, "the First Knot hangs %s blocks from the Heart", (int) far);
		check(helper, com.extremedimensions.seam.SeamDepth.inFray(a[1]), "the First Knot hangs above the Fray, at %s", a[1]);
		helper.succeed();
	}

	/** Standing in the Knot's chamber ends the trail: its page and advancement, once. */
	@GameTest
	public void reachingTheKnotEndsTheTrail(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "first-knot");
		PlayerData d = DataStore.get(p.getUUID());
		d.frameStrung = true;
		com.extremedimensions.seam.FirstKnot.reached(p, d);
		check(helper, d.knotReached && d.journal.contains(JournalEntry.FIRST_KNOT.id), "reaching the Knot did not end the trail");
		check(helper, WorldFx.hasAdvancement(p, Advancements.FIRST_KNOT), "reaching the Knot gave no advancement");
		check(helper, !FirstWeaver.needs(p, d, ModItems.EMBER_THREAD), "one who reached the Knot still needs threads");
		helper.succeed();
	}
}
