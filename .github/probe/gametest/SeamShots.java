package com.extremedimensions.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public class SeamShots implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			ctx.takeScreenshot("00_overworld");
			ctx.setScreen(() -> new InventoryScreen(ctx.computeOnClient(mc -> mc.player)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("01_inventory");
			ctx.setScreen(() -> null);
			ctx.runOnClient(mc -> mc.options.renderDistance().set(9));
			sp.getServer().runCommand("gamemode spectator @a");
			ctx.getInput().pressKey(290);
			sp.getServer().runCommand("tether seam_showcase");
			ctx.waitTicks(40);
			shot(ctx, sp, "02_spire_n", 2916, 104, 2846, 0, -22);
			shot(ctx, sp, "02_spire_s", 2916, 104, 2986, 180, -22);
			shot(ctx, sp, "02_spire_w", 2846, 104, 2916, -90, -22);
			shot(ctx, sp, "02_spire_e", 2986, 104, 2916, 90, -22);
			shot(ctx, sp, "03_spire_far", 2830, 150, 2830, -45, 5);
			shot(ctx, sp, "04_loom", 2988, 104, 2880, 0, -18);
			shot(ctx, sp, "05_village", 3060, 125, 2892, 0, 35);
			shot(ctx, sp, "06_hanging", 3132, 70, 2880, 0, -5);
			shot(ctx, sp, "07_halo", 3204, 112, 2880, 0, -12);
			shot(ctx, sp, "08_panorama", 3020, 150, 2780, -30, 18);
			shot(ctx, sp, "09_sky_up", 3000, 160, 2700, 0, -80);
			shot(ctx, sp, "10_sky_horizon", 3000, 160, 2700, 90, -5);
		}
	}

	private static void shot(ClientGameTestContext ctx, TestSingleplayerContext sp, String name, int x, int y, int z, float yaw, float pitch) {
		sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "execute in extremedimensions:the_seam run tp @a %d %d %d %.1f %.1f", x, y, z, yaw, pitch));
		ctx.waitTicks(30);
		sp.getConnection().waitForChunksRender(6000);
		ctx.waitTicks(10);
		ctx.takeScreenshot(name);
	}
}
