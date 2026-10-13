package com.extremedimensions.gametest;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.PenaltyService;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;

/** TEMP: screenshots of the curse overhaul: the inventory button, the Curses screen and the cards. */
public class CurseShots implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			sp.getServer().runCommand("tether overworld_penalties on");
			for (String c : new String[] {"scar 2", "accelerated_drain 4", "heavy_soul 1", "brittle_bones 3", "vertigo 1", "short_chain 2",
					"watched 2", "ill_repute 1", "hunted_nights 3"}) {
				sp.getServer().runCommand("execute as @a run tether curse " + c);
			}
			sp.getServer().runOnServer(s -> {
				for (ServerPlayer p : s.getPlayerList().getPlayers()) {
					PlayerData d = DataStore.get(p.getUUID());
					d.atonement.put("scar", 87);
					d.atoning = "scar";
					d.scars = 3;
				}
			});
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("01_inventory");
			ctx.runOnClient(mc -> ClientPlayNetworking.send(new ModPackets.RequestCursesPayload()));
			ctx.waitTicks(20);
			ctx.takeScreenshot("02_curses_beyond");

			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			sp.getServer().runCommand("execute as @a run tether curse clear");
			for (String c : new String[] {"hollow_heart 3", "restless_sleep 1", "bitter_tongue 2", "dusk_blindness 1", "short_tether 2"}) {
				sp.getServer().runCommand("execute as @a run tether curse " + c);
			}
			ctx.waitTicks(5);
			ctx.runOnClient(mc -> ClientPlayNetworking.send(new ModPackets.RequestCursesPayload()));
			ctx.waitTicks(20);
			ctx.takeScreenshot("03_curses_overworld");

			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			offer(sp, 0.0);
			ctx.waitTicks(40);
			ctx.takeScreenshot("04_cards");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			offer(sp, 1.0);
			ctx.waitTicks(40);
			ctx.takeScreenshot("05_cards_multiplied");
			System.out.println("CURSE shots done");
		}
	}

	private static void offer(TestSingleplayerContext sp, double chance) {
		sp.getServer().runOnServer(s -> {
			Config.MULTIPLIER_CHANCE = chance;
			for (ServerPlayer p : s.getPlayerList().getPlayers()) {
				PlayerData d = DataStore.get(p.getUUID());
				d.pendingOffer.clear();
				d.pendingMult.clear();
				d.owed.clear();
				d.pendingPenalties = 0;
				PenaltyService.owe(d, false);
				Sync.offer(p, d);
				System.out.println("CURSE offer " + d.pendingOffer + " x " + d.pendingMult);
			}
		});
	}
}
