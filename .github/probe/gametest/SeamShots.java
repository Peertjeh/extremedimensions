package com.extremedimensions.gametest;

import java.util.Locale;

import com.extremedimensions.seam.SeamArchitect;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.seam.VeilRay;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** TEMP: screenshots of the bigger islands and bridges, and a flight log of the Veil Rays around a spire. */
public class SeamShots implements FabricClientGameTest {
	private static final String IN = "execute in extremedimensions:the_seam run ";

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			sp.getServer().runCommand("gamemode spectator @a");
			ctx.getInput().pressKey(290);
			sp.getServer().runCommand("tether seam_showcase");
			sp.getServer().runCommand(IN + "tp @a 4050 150 4050");
			for (int i = 0; i < 40; i++) {
				int left = sp.getServer().computeOnServer(s -> SeamArchitect.pending());
				System.out.println("ARCH pending " + left);
				if (left == 0) {
					break;
				}
				ctx.waitTicks(20);
			}
			shot(ctx, sp, "01_halo_crystals", 4430, 104, 4030, -45, 20);
			shot(ctx, sp, "02_plain", 4536, 106, 4036, -45, 22);
			shot(ctx, sp, "03_plain_low", 4562, 99, 4062, 135, 0);
			shot(ctx, sp, "04_halo_low", 4462, 93, 4062, 135, -6);
			shot(ctx, sp, "05_spire_grounds", 4084, 92, 4084, 135, 8);
		}
	}

	/** Each ray: where it is, how many probe points across its body and wings sit in a block, whether it collided. */
	private static String rays(MinecraftServer s, int t) {
		ServerLevel lvl = s.getLevel(SeamService.SEAM);
		StringBuilder b = new StringBuilder();
		for (Entity e : lvl.getAllEntities()) {
			if (!(e instanceof VeilRay r)) {
				continue;
			}
			double yaw = Math.toRadians(r.getYRot());
			double dx = -Math.sin(yaw), dz = Math.cos(yaw);
			int clip = 0;
			for (double along : new double[] {-3.5, 0, 2.5}) {
				for (double side : new double[] {-5.2, -3, 0, 3, 5.2}) {
					for (double up : new double[] {-0.6, 0.6}) {
						BlockPos q = BlockPos.containing(r.getX() + dx * along - dz * side, r.getY() + 0.45 + up, r.getZ() + dz * along + dx * side);
						if (!lvl.getBlockState(q).isAir()) {
							clip++;
						}
					}
				}
			}
			b.append(String.format(Locale.ROOT, "RAY|%d|%d|%.1f|%.1f|%.1f|clip=%d|coll=%b%n", t, r.getId(), r.getX(), r.getY(), r.getZ(), clip,
					r.horizontalCollision || r.verticalCollision));
		}
		if (b.length() == 0) {
			b.append("RAY|").append(t).append("|none\n");
		}
		return b.toString();
	}

	/** A camera a little behind, beside and above the ray nearest the visitor, looking at it. */
	private static String chase(MinecraftServer s) {
		ServerLevel lvl = s.getLevel(SeamService.SEAM);
		VeilRay best = null;
		for (Entity e : lvl.getAllEntities()) {
			if (e instanceof VeilRay r && (best == null || r.distanceToSqr(4070, 86, 4068) < best.distanceToSqr(4070, 86, 4068))) {
				best = r;
			}
		}
		if (best == null) {
			return null;
		}
		double yaw = Math.toRadians(best.getYRot());
		double dx = -Math.sin(yaw), dz = Math.cos(yaw);
		double x = best.getX() - dx * 12 - dz * 7, y = best.getY() + 5, z = best.getZ() - dz * 12 + dx * 7;
		return String.format(Locale.ROOT, "tp @a %.1f %.1f %.1f facing %.1f %.1f %.1f", x, y, z, best.getX() + dx * 4, best.getY(), best.getZ() + dz * 4);
	}

	private static void shot(ClientGameTestContext ctx, TestSingleplayerContext sp, String name, int x, int y, int z, float yaw, float pitch) {
		sp.getServer().runCommand(String.format(Locale.ROOT, IN + "tp @a %d %d %d %.1f %.1f", x, y, z, yaw, pitch));
		ctx.waitTicks(30);
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(10);
		ctx.takeScreenshot(name);
	}
}
