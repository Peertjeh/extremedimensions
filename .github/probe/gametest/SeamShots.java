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
			shot(ctx, sp, "01_spire_close", 2930, 94, 2885, 20, -15);
			shot(ctx, sp, "02_village_n", 3060, 99, 2916, 180, 8);
			shot(ctx, sp, "03_village_s", 3060, 99, 2916, 0, 8);
			shot(ctx, sp, "04_village_e", 3060, 99, 2916, -90, 8);
			// fauna: stand on the plaza and wait for life
			sp.getServer().runCommand("gamemode creative @a");
			sp.getServer().runCommand("execute in extremedimensions:the_seam run tp @a 3066 97 2922 135 12");
			ctx.waitTicks(260);
			sp.getConnection().waitForChunksRender();
			ctx.takeScreenshot("05_fauna_plaza");
			sp.getServer().runCommand("execute in extremedimensions:the_seam run tp @a 3066 97 2922 135 -25");
			ctx.waitTicks(20);
			ctx.takeScreenshot("06_fauna_sky");
			sp.getServer().runCommand("execute in extremedimensions:the_seam run tp @a 3066 97 2922 -45 -20");
			ctx.waitTicks(20);
			ctx.takeScreenshot("07_fauna_sky2");
			sp.getServer().runCommand("execute in extremedimensions:the_seam run summon extremedimensions:veil_ray 3058 101 2913");
			ctx.waitTicks(30);
			ctx.takeScreenshot("08_ray_close");
			// the Heart's crown
			sp.getServer().runCommand("gamemode spectator @a");
			sp.getServer().runCommand("tether break_seal");
			shot(ctx, sp, "09_heart_wait", 0, 130, 40, 180, 10);
			ctx.waitTicks(200);
			shot(ctx, sp, "10_obelisk_far", 0, 140, 0, -45, 5);
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
