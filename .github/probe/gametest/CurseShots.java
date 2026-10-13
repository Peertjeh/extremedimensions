package com.extremedimensions.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.extremedimensions.client.screen.CursesScreen;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.PenaltyService;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** TEMP: screenshots of the 2.4.0 curses UI, and checks that the Everywhere curses do what they say. */
public class CurseShots implements FabricClientGameTest {
	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static String inventory(ServerPlayer p) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < 36; i++) {
			b.append(p.getInventory().getItem(i).isEmpty() ? '.' : '#');
			if (i % 9 == 8) {
				b.append(' ');
			}
		}
		return b.toString();
	}

	private static String curses(ServerPlayer p) {
		return new TreeMap<>(DataStore.get(p.getUUID()).curses).toString();
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			sp.getServer().runCommand("tether overworld_penalties on");
			sp.getServer().runCommand("gamemode survival @a");
			for (String c : new String[] {"scar 2", "accelerated_drain 4", "vertigo 1", "watched 2", "hunted_nights 1", "kindling 3",
					"fading_brews 3", "lingering_ills 2", "sealed_pockets 3", "shrunken_stomach 2", "glass_jaw 2", "withering 1"}) {
				sp.getServer().runCommand("execute as @a run tether curse " + c);
			}
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				PlayerData d = DataStore.get(p.getUUID());
				d.atonement.put("kindling", 120);
				d.atoning = "kindling";
				d.scars = 2;
				for (int i = 0; i < 21; i++) {
					p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 1 + i));
				}
				for (int i = 30; i < 36; i++) {
					p.getInventory().setItem(i, new ItemStack(Items.DIRT, i));
				}
				p.getFoodData().setFoodLevel(20);
				p.setHealth(p.getMaxHealth());
			});
			ctx.waitTicks(30);
			String r1 = sp.getServer().computeOnServer(s -> {
				ServerPlayer p = player(s);
				return "food=" + p.getFoodData().getFoodLevel() + " maxHealth=" + p.getMaxHealth() + " inv=" + inventory(p);
			});
			System.out.println("CURSE stomach+sealed+withering " + r1);

			sp.getServer().runOnServer(s -> player(s).setHealth(player(s).getMaxHealth()));
			String h0 = sp.getServer().computeOnServer(s -> "before=" + player(s).getHealth());
			sp.getServer().runCommand("damage @p 4 minecraft:in_fire");
			ctx.waitTicks(2);
			String h1 = sp.getServer().computeOnServer(s -> " after=" + player(s).getHealth() + " (4 fire damage, Kindling 3)");
			System.out.println("CURSE kindling " + h0 + h1);

			sp.getServer().runCommand("effect clear @a");
			sp.getServer().runCommand("effect give @a minecraft:speed 100");
			sp.getServer().runCommand("effect give @a minecraft:slowness 100");
			ctx.waitTicks(2);
			String eff = sp.getServer().computeOnServer(s -> {
				StringBuilder b = new StringBuilder();
				for (MobEffectInstance e : player(s).getActiveEffects()) {
					b.append(e.getDescriptionId()).append('=').append(e.getDuration()).append(' ');
				}
				return b.toString();
			});
			System.out.println("CURSE effects (100 s = 2000 ticks given; Fading Brews 3, Lingering Ills 2) " + eff);
			sp.getServer().runCommand("effect clear @a");

			ctx.takeScreenshot("01_hud");
			ctx.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("02_inventory");

			String data = sp.getServer().computeOnServer(s -> Sync.cursesData(player(s), DataStore.get(player(s).getUUID())));
			AtomicReference<CursesScreen> screen = new AtomicReference<>();
			ctx.runOnClient(mc -> {
				String fake = mc.player.getUUID() + ",Peertjeh,7,2,3;" + UUID.randomUUID() + ",Steve,0,4,1;" + UUID.randomUUID() + ",Alex,12,0,0";
				CursesScreen cs = new CursesScreen(data + fake, null);
				screen.set(cs);
				mc.gui.setScreen(cs);
			});
			ctx.waitTicks(8);
			ctx.takeScreenshot("03_curses_first_tab");
			for (String tab : new String[] {"Beyond", "Overworld", "Everywhere"}) {
				ctx.runOnClient(mc -> {
					for (GuiEventListener w : screen.get().children()) {
						if (w instanceof AbstractButton b && b.getMessage().getString().equals(tab)) {
							b.onPress(null);
						}
					}
				});
				ctx.waitTicks(4);
				ctx.takeScreenshot("04_curses_" + tab.toLowerCase());
			}
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.runOnClient(mc -> mc.gui.setScreen(new CursesScreen(data, null)));
			ctx.waitTicks(8);
			ctx.takeScreenshot("04_curses_nobody_near");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));

			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				PlayerData d = DataStore.get(p.getUUID());
				d.pendingOffer = new ArrayList<>(List.of("glass_jaw", "grounded", "short_breath"));
				d.pendingMult = new ArrayList<>(List.of(3, 4, 2));
				d.owed.clear();
				d.owed.add("beyond");
				d.pendingPenalties = 1;
				Sync.offer(p, d);
			});
			ctx.waitTicks(40);
			ctx.takeScreenshot("05_cards_spill");
			String spill = sp.getServer().computeOnServer(s -> {
				ServerPlayer p = player(s);
				String before = curses(p);
				PenaltyService.choose(p, "glass_jaw");
				return "before=" + before + " after=" + curses(p) + " (glass_jaw 2/3 picked at 3x: 1 added, 2 spill)";
			});
			System.out.println("CURSE spill " + spill);
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(10);
			String inv = sp.getServer().computeOnServer(s -> "inv=" + inventory(player(s)) + " food=" + player(s).getFoodData().getFoodLevel());
			System.out.println("CURSE end " + inv);
			System.out.println("CURSE shots done");
		}
	}
}
