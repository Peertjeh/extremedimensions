package com.extremedimensions.gametest;

import java.util.List;
import java.util.TreeMap;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.SealedPockets;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** TEMP: 2.4.1 checks: sealed slots in every menu, no pickup into sealed slots, atonement after death, Lurker heartbeat. */
public class CurseShots implements FabricClientGameTest {
	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static String inventory(ServerPlayer p) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < 36; i++) {
			ItemStack st = p.getInventory().getItem(i);
			b.append(st.isEmpty() ? '.' : st.is(Items.COBBLESTONE) ? '#' : st.is(Items.DIRT) ? 'd' : st.is(Items.STONE) ? 's' : '?');
			if (i % 9 == 8) {
				b.append(' ');
			}
		}
		return b.toString();
	}

	private static String looseItems(ServerPlayer p) {
		List<ItemEntity> items = p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(6));
		StringBuilder b = new StringBuilder("ground=[");
		for (ItemEntity e : items) {
			b.append(e.getItem().getCount()).append('x').append(e.getItem().getItem()).append(' ');
		}
		return b.append(']').toString();
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			sp.getServer().runCommand("gamemode survival @a");
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("execute as @a run tether curse sealed_pockets 3");
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getInventory().clearContent();
				for (int i = 0; i < 27; i++) {
					p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
				}
			});
			ctx.waitTicks(25);

			// --- pickup with only sealed slots free
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				ItemEntity e = new ItemEntity(p.level(), p.getX(), p.getY() + 0.2, p.getZ(), new ItemStack(Items.DIRT, 5));
				e.setNoPickUpDelay();
				p.level().addFreshEntity(e);
			});
			ctx.waitTicks(30);
			System.out.println("CURSE pickup_full " + sp.getServer().computeOnServer(s -> "sealed=" + SealedPockets.count(player(s))
					+ " inv=" + inventory(player(s)) + " " + looseItems(player(s)) + " (expect dirt on the ground, bottom row empty)"));
			sp.getServer().runCommand("give @a minecraft:stone 3");
			ctx.waitTicks(30);
			System.out.println("CURSE give_full " + sp.getServer().computeOnServer(s -> "inv=" + inventory(player(s)) + " " + looseItems(player(s))
					+ " (expect stone on the ground)"));
			sp.getServer().runOnServer(s -> player(s).getInventory().setItem(13, ItemStack.EMPTY));
			ctx.waitTicks(30);
			System.out.println("CURSE pickup_room " + sp.getServer().computeOnServer(s -> "inv=" + inventory(player(s)) + " " + looseItems(player(s))
					+ " (slot 13 freed: one stack comes in)"));
			System.out.println("CURSE mayPlace server " + sp.getServer().computeOnServer(s -> {
				ServerPlayer p = player(s);
				return "menuSlot35(inv35)=" + p.inventoryMenu.getSlot(35).mayPlace(new ItemStack(Items.STONE))
						+ " menuSlot26(inv26)=" + p.inventoryMenu.getSlot(26).mayPlace(new ItemStack(Items.STONE))
						+ " hotbar(menu36)=" + p.inventoryMenu.getSlot(36).mayPlace(new ItemStack(Items.STONE));
			}));
			ctx.runOnClient(mc -> System.out.println("CURSE mayPlace client sealed=" + SealedPockets.count(mc.player)
					+ " menuSlot35=" + mc.player.inventoryMenu.getSlot(35).mayPlace(new ItemStack(Items.STONE))
					+ " menuSlot26=" + mc.player.inventoryMenu.getSlot(26).mayPlace(new ItemStack(Items.STONE))));

			// --- the inventory, a chest and a villager
			ctx.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("01_inventory");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(3);

			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				BlockPos pos = p.blockPosition().offset(2, 0, 0);
				ServerLevel level = (ServerLevel) p.level();
				level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
				if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container chest) {
					chest.setItem(0, new ItemStack(Items.DIAMOND, 4));
				}
				p.getInventory().setItem(13, new ItemStack(Items.COBBLESTONE, 64));
				if (level.getBlockEntity(pos) instanceof MenuProvider mp) {
					p.openMenu(mp);
				}
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("02_chest");
			System.out.println("CURSE shift_click " + sp.getServer().computeOnServer(s -> {
				ServerPlayer p = player(s);
				p.containerMenu.quickMoveStack(p, 0);
				return "chestSlot0=" + p.containerMenu.getSlot(0).getItem() + " inv=" + inventory(p) + " (expect the diamonds to stay in the chest)";
			}));
			sp.getServer().runOnServer(s -> player(s).closeContainer());
			ctx.waitTicks(3);

			sp.getServer().runCommand("execute as @a at @s run summon minecraft:villager ~ ~ ~3 {NoAI:1b,VillagerData:{profession:\"minecraft:farmer\",level:2,type:\"minecraft:plains\"}}");
			ctx.waitTicks(5);
			System.out.println("CURSE villager " + sp.getServer().computeOnServer(s -> {
				ServerPlayer p = player(s);
				for (Villager v : p.level().getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(10))) {
					v.setTradingPlayer(p);
					v.openTradingScreen(p, v.getDisplayName(), 2);
					return "opened offers=" + v.getOffers().size();
				}
				return "no villager";
			}));
			ctx.waitTicks(10);
			ctx.takeScreenshot("03_villager");
			sp.getServer().runOnServer(s -> player(s).closeContainer());
			ctx.waitTicks(3);
			sp.getServer().runCommand("kill @e[type=minecraft:villager]");
			sp.getServer().runCommand("kill @e[type=minecraft:item]");

			// --- Lurker heartbeat: the tribe at midnight, several frames
			sp.getServer().runCommand("time set midnight");
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 5");
			ctx.waitTicks(5);
			sp.getServer().runCommand("execute as @a at @s run summon extremedimensions:lurker ^ ^ ^6 {NoAI:1b,Rotation:[180f,0f]}");
			sp.getServer().runCommand("execute as @a at @s run summon extremedimensions:hollowling ^2.2 ^ ^3.5 {NoAI:1b,Rotation:[180f,0f]}");
			sp.getServer().runCommand("execute as @a at @s run summon extremedimensions:keeper ^-3.5 ^ ^8 {NoAI:1b,Rotation:[180f,0f]}");
			for (int i = 0; i < 9; i++) {
				ctx.waitTicks(3);
				ctx.takeScreenshot("04_lurkers_" + i);
			}
			System.out.println("CURSE lurkers " + sp.getServer().computeOnServer(s -> {
				ServerPlayer p = player(s);
				return "alive=" + p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(16)).size();
			}));

			// --- atonement after a death
			sp.getServer().runCommand("time set noon");
			sp.getServer().runOnServer(s -> {
				PlayerData d = DataStore.get(player(s).getUUID());
				d.atonement.clear();
				d.atonement.put("kindling", 121);
				d.atonement.put("glass_jaw", 7);
				d.atonement.put("scar", 1);
			});
			String before = sp.getServer().computeOnServer(s -> new TreeMap<>(DataStore.get(player(s).getUUID()).atonement).toString());
			sp.getServer().runCommand("kill @a");
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> {
				if (mc.player != null) {
					mc.player.respawn();
				}
				mc.gui.setScreen(null);
			});
			ctx.waitTicks(40);
			String after = sp.getServer().computeOnServer(s -> new TreeMap<>(DataStore.get(player(s).getUUID()).atonement).toString());
			System.out.println("CURSE atonement before=" + before + " after=" + after + " (expect kindling=60 glass_jaw=3, scar gone)");
			ctx.takeScreenshot("05_respawn_chat");
			System.out.println("CURSE shots done");
		}
	}
}
