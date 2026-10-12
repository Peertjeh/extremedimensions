package com.extremedimensions.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

public class SeamShots implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			sp.getServer().runCommand("gamemode spectator @a");
			ctx.getInput().pressKey(290);
			sp.getServer().runCommand("tether seam_showcase");
			ctx.waitTicks(60);
			shot(ctx, sp, "01_spire_front", 2916, 100, 2862, 0, -30);
			shot(ctx, sp, "02_spire_side", 2970, 98, 2930, 120, -28);
			shot(ctx, sp, "03_spire_close", 2930, 94, 2885, 20, -15);
			shot(ctx, sp, "04_bridge_a", 2944, 96, 2905, -90, 8);
			shot(ctx, sp, "05_bridge_b", 3020, 99, 2925, 90, 12);
			shot(ctx, sp, "06_village_top", 3060, 118, 2890, 0, 35);
			shot(ctx, sp, "07_house_close", 3050, 99, 2905, -30, 5);
			shot(ctx, sp, "08_loom", 2988, 100, 2885, 0, -15);
			shot(ctx, sp, "09_hanging", 3132, 70, 2885, 0, -5);
			shot(ctx, sp, "10_halo", 3204, 108, 2885, 0, -10);
			shot(ctx, sp, "11_island_under", 3270, 70, 2900, 0, -25);
			// fauna: stand in the village and wait for life
			sp.getServer().runCommand("gamemode creative @a");
			sp.getServer().runCommand("execute in extremedimensions:the_seam run tp @a 3060 93 2916 0 0");
			ctx.waitTicks(200);
			sp.getConnection().waitForChunksRender();
			ctx.takeScreenshot("12_fauna_village");
			sp.getServer().runCommand("execute in extremedimensions:the_seam run tp @a 3060 93 2916 0 -35");
			ctx.waitTicks(40);
			ctx.takeScreenshot("13_fauna_sky");
			// the Heart's crown
			sp.getServer().runCommand("gamemode spectator @a");
			sp.getServer().runCommand("tether break_seal");
			shot(ctx, sp, "14_heart_wait", 0, 130, 40, 180, 10);
			ctx.waitTicks(200);
			shot(ctx, sp, "15_obelisk", 20, 125, 20, -45, -12);
			shot(ctx, sp, "16_obelisk_close", 30, 110, 30, -45, -35);
			shot(ctx, sp, "17_heart_halo", 0, 112, -20, 0, -40);
		}
	}

	private static void shot(ClientGameTestContext ctx, TestSingleplayerContext sp, String name, int x, int y, int z, float yaw, float pitch) {
		sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "execute in extremedimensions:the_seam run tp @a %d %d %d %.1f %.1f", x, y, z, yaw, pitch));
		ctx.waitTicks(30);
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(10);
		ctx.takeScreenshot(name);
	}
}
