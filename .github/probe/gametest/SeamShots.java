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
			sp.getServer().runCommand("execute in extremedimensions:the_seam run tp @a 3066 108 2926 180 0");
			ctx.waitTicks(40);
			sp.getConnection().waitForChunksRender();
			sp.getServer().runCommand("execute in extremedimensions:the_seam run summon extremedimensions:veil_ray 3066 108 2914");
			ctx.waitTicks(8);
			ctx.takeScreenshot("01_ray_front");
			sp.getServer().runCommand("execute in extremedimensions:the_seam run summon extremedimensions:veil_ray 3058 106 2918");
			ctx.waitTicks(6);
			sp.getServer().runCommand("execute in extremedimensions:the_seam run tp @a 3058 118 2918 0 90");
			ctx.waitTicks(4);
			ctx.takeScreenshot("02_ray_above");
			sp.getServer().runCommand("execute in extremedimensions:the_seam run summon extremedimensions:veil_ray 3058 106 2918");
			sp.getServer().runCommand("execute in extremedimensions:the_seam run tp @a 3046 104 2918 -90 -10");
			ctx.waitTicks(6);
			ctx.takeScreenshot("03_ray_side");
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
