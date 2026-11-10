package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.extremedimensions.binding.Homebound;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.relic.Relic;
import com.extremedimensions.relic.RelicReset;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.server.Perms;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * Starting the relics over (/tether relic_reset): every player the world knows loses their bound relic, relics made
 * afterwards carry the new epoch, and the old ones crumble wherever a player comes across them. A world never reset
 * stamps nothing and loses nothing.
 */
public class RelicResetTests {
	/** What a reset changes in a player's data, to put back afterwards (it reaches every player the world knows). */
	private record Was(String relic, boolean stitch, boolean held, boolean obol) {}

	/** A relic as one from before the reset: no stamp at all. */
	private static ItemStack old(Relic r) {
		ItemStack s = new ItemStack(RelicService.item(r));
		s.remove(DataComponents.CUSTOM_DATA);
		return s;
	}

	/**
	 * Before a reset nothing is stamped and nothing crumbles; after it, bound relics are gone (online and offline, the
	 * Obol's keep left alone), new relics carry the epoch, and old ones crumble in the bag, the off hand, the ender chest
	 * and an open chest, or in the hand as they are bound; new relics and everything else stay.
	 */
	@GameTest(environment = Alone.RELIC_RESET, maxTicks = 40)
	public void theRelicsStartOver(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer p = TestPlayers.survivor(helper, "test-relic-reset");
		ServerPlayer maker = TestPlayers.creative(helper, "test-relic-maker");
		PlayerData d = DataStore.get(p.getUUID());
		PlayerData away = DataStore.get(UUID.randomUUID());
		int epoch = DataStore.relicEpoch();
		Map<PlayerData, Was> was = new HashMap<>();
		for (PlayerData each : DataStore.players()) {
			was.put(each, new Was(each.relic, each.stitchSpent, each.heldSpent, each.obolKept));
		}
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		try {
			DataStore.setRelicEpoch(0);
			// A world never reset: nothing stamped, nothing crumbles
			ItemStack found = RelicService.random();
			check(helper, !found.has(DataComponents.CUSTOM_DATA) && !new ItemStack(RelicService.item(Relic.EMBER_HEART)).has(DataComponents.CUSTOM_DATA),
					"a relic made in a world never reset carries custom data");
			p.getInventory().setItem(5, found);
			check(helper, RelicReset.scan(p) == 0 && p.getInventory().getItem(5) == found && !RelicReset.old(found),
					"a relic crumbled in a world never reset");

			// Asking only explains
			d.relic = Relic.LAST_STITCH.id;
			d.stitchSpent = true;
			d.obolKept = true;
			away.relic = Relic.EMBER_HEART.id;
			away.obolKept = true;
			CommandSourceStack console = server.createCommandSourceStack().withSuppressedOutput();
			server.getCommands().performPrefixedCommand(console, "tether relic_reset");
			check(helper, DataStore.relicEpoch() == 0 && Relic.LAST_STITCH.id.equals(d.relic), "asking about a reset of the relics carried it out");

			// The reset: bound relics wiped for everyone the world knows, and what the player carries crumbles now
			ItemStack stale = old(Relic.PALE_SHARD), staleOff = old(Relic.FEATHERBONE), staleEnder = old(Relic.TITHE_BELL),
					staleChest = old(Relic.KINDRED_KNOT), staleBind = old(Relic.HUNGERING_HAND), staleMade = old(Relic.ASHEN_LOCKET);
			RelicReset.Result r = RelicReset.reset(server, "Tester");
			check(helper, r.epoch() == 1 && DataStore.relicEpoch() == 1, "the reset made the epoch %s (%s kept)", r.epoch(), DataStore.relicEpoch());
			check(helper, d.relic == null && !d.stitchSpent, "the online player kept their relic (%s) or its Last Stitch spent", d.relic);
			check(helper, away.relic == null, "the offline player kept their relic (%s)", away.relic);
			check(helper, d.obolKept && away.obolKept, "the reset took what the Ferryman's Obol kept from a death");
			check(helper, r.wiped() >= 2, "the reset counted %s bound relics wiped", r.wiped());
			check(helper, r.crumbled() >= 1 && p.getInventory().getItem(5).isEmpty(), "the old relic in the bag did not crumble at the reset (%s counted)",
					r.crumbled());

			// Relics made from now on carry the new epoch, however they are made; saved and read back, or copied, an
			// old relic stays old and a new one new
			ItemStack fresh = RelicService.random();
			check(helper, RelicReset.stampOf(fresh) == 1 && !RelicReset.old(fresh), "a relic found after the reset carries %s", RelicReset.stampOf(fresh));
			ItemStack made = new ItemStack(RelicService.item(Relic.MINERS_CANDLE));
			check(helper, RelicReset.stampOf(made) == 1, "a relic made with new ItemStack carries %s", RelicReset.stampOf(made));
			check(helper, RelicReset.old(stale) && RelicReset.old(stale.copy()), "a relic from before the reset (or its copy) is not old");
			ItemStack readBack = Homebound.decode(helper.getLevel().registryAccess(), Homebound.encode(helper.getLevel().registryAccess(), stale));
			check(helper, RelicService.of(readBack) == Relic.PALE_SHARD && RelicReset.old(readBack), "an old relic saved and read back is new again");
			ItemStack freshBack = Homebound.decode(helper.getLevel().registryAccess(), Homebound.encode(helper.getLevel().registryAccess(), fresh));
			check(helper, RelicReset.stampOf(freshBack) == 1, "a new relic saved and read back carries %s", RelicReset.stampOf(freshBack));
			check(helper, !RelicReset.old(new ItemStack(Items.BREAD)), "bread counts as an old relic");
			// (and one of an earlier reset is old in a later one)
			DataStore.setRelicEpoch(2);
			check(helper, RelicReset.old(fresh), "a relic of the first reset is not old after a second");
			DataStore.setRelicEpoch(1);

			// The scan: old relics crumble in the bag, the off hand, the ender chest and an open chest; the rest stays
			ItemStack bread = new ItemStack(Items.BREAD, 5), coal = new ItemStack(Items.COAL, 3), kept = RelicService.random();
			p.getInventory().setItem(5, stale);
			p.getInventory().setItem(6, fresh);
			p.getInventory().setItem(7, bread);
			p.setItemSlot(EquipmentSlot.OFFHAND, staleOff);
			p.getEnderChestInventory().setItem(0, staleEnder);
			helper.getLevel().setBlock(at, Blocks.CHEST.defaultBlockState(), 3);
			ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(at);
			chest.setItem(0, staleChest);
			chest.setItem(1, coal);
			chest.setItem(2, kept);
			p.openMenu(chest);
			check(helper, p.containerMenu != p.inventoryMenu, "the chest did not open");
			int n = RelicReset.scan(p);
			check(helper, n == 4, "the scan crumbled %s old relics, not 4", n);
			check(helper, p.getInventory().getItem(5).isEmpty() && p.getOffhandItem().isEmpty() && p.getEnderChestInventory().getItem(0).isEmpty(),
					"an old relic stayed in the bag, the off hand or the ender chest");
			check(helper, chest.getItem(0).isEmpty(), "the old relic stayed in the open chest");
			check(helper, p.getInventory().getItem(6) == fresh && p.getInventory().getItem(7).is(Items.BREAD) && p.getInventory().getItem(7).getCount() == 5,
					"the scan took a new relic or bread from the bag");
			check(helper, chest.getItem(1).is(Items.COAL) && chest.getItem(1).getCount() == 3 && RelicService.of(chest.getItem(2)) != null,
					"the scan took coal or a new relic from the chest");
			check(helper, RelicReset.scan(p) == 0, "a second scan found more to crumble");
			p.closeContainer();

			// In creative an old relic is stamped anew rather than crumbled (the creative tab's are made by the client)
			maker.getInventory().setItem(0, staleMade);
			check(helper, RelicReset.scan(maker) == 0 && maker.getInventory().getItem(0) == staleMade && RelicReset.stampOf(staleMade) == 1,
					"a creative player's relic crumbled, or was not stamped anew");

			// Binding an old relic: it crumbles in the hand, and nothing is bound; a new one binds as ever
			p.setItemInHand(InteractionHand.MAIN_HAND, staleBind);
			check(helper, RelicService.bind(p, p.getMainHandItem()) == InteractionResult.FAIL, "binding an old relic did not fail");
			check(helper, d.relic == null && p.getMainHandItem().isEmpty(), "an old relic was bound (%s) or stayed in the hand", d.relic);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RelicService.item(Relic.EMBER_HEART)));
			check(helper, RelicService.bind(p, p.getMainHandItem()) == InteractionResult.SUCCESS && RelicService.has(d, Relic.EMBER_HEART),
					"a relic made after the reset did not bind");
		} finally {
			DataStore.setRelicEpoch(epoch);
			if (p.containerMenu != p.inventoryMenu) {
				p.closeContainer();
			}
			// (emptied first, so taking it away spills nothing)
			if (helper.getLevel().getBlockEntity(at) instanceof ChestBlockEntity chest) {
				chest.clearContent();
			}
			helper.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), 3);
			for (Map.Entry<PlayerData, Was> e : was.entrySet()) {
				PlayerData each = e.getKey();
				each.relic = e.getValue().relic();
				each.stitchSpent = e.getValue().stitch();
				each.heldSpent = e.getValue().held();
				each.obolKept = e.getValue().obol();
			}
			for (ServerPlayer each : server.getPlayerList().getPlayers()) {
				CurseEffects.applyEffects(each, DataStore.get(each.getUUID()));
			}
		}
		helper.succeed();
	}

	/** The reset is for admins: a permission of its own, at the admins' level, that a plain player lacks and the console holds. */
	@GameTest
	public void theRelicResetIsForAdmins(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-relic-plain");
		CommandSourceStack console = helper.getLevel().getServer().createCommandSourceStack();
		check(helper, "extremedimensions.admin.relic_reset".equals(Perms.Node.RELIC_RESET.dotted()), "the relic reset's permission is written %s",
				Perms.Node.RELIC_RESET.dotted());
		check(helper, Perms.Node.RELIC_RESET.level == PermissionLevel.ADMINS, "the relic reset falls back on %s", Perms.Node.RELIC_RESET.level);
		check(helper, !Perms.has(p, Perms.Node.RELIC_RESET) && !Perms.has(p.createCommandSourceStack(), Perms.Node.RELIC_RESET),
				"a player without operator rights may reset the relics");
		check(helper, Perms.has(console, Perms.Node.RELIC_RESET), "the server console may not reset the relics");
		var node = helper.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("tether");
		var reset = node == null ? null : node.getChild("relic_reset");
		check(helper, reset != null && reset.getChild("confirm") != null, "there is no /tether relic_reset confirm");
		check(helper, reset.canUse(console) && !reset.canUse(p.createCommandSourceStack()), "/tether relic_reset is open to the wrong ones");
		helper.succeed();
	}
}
