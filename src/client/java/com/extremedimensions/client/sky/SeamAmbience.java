package com.extremedimensions.client.sky;

import com.extremedimensions.registry.ModParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/**
 * The air of the Seam, drawn on the client: fireflies of violet, rose and gold drifting all around, motes of light
 * sifting down like dust on a breath of air that blows towards the way home (as the wind and the flying cloth do in
 * Journey), and soft twinkles rising from below. All of it full-bright, so shader packs bloom it.
 */
public final class SeamAmbience {
	private SeamAmbience() {}

	private static final Identifier SEAM = Identifier.fromNamespaceAndPath("extremedimensions", "the_seam");

	public static boolean inSeam(Minecraft mc) {
		return mc.level != null && SEAM.equals(mc.level.dimension().identifier());
	}

	public static void tick(Minecraft mc) {
		if (!inSeam(mc) || mc.player == null || mc.isPaused()) {
			return;
		}
		ClientLevel level = mc.level;
		RandomSource r = level.getRandom();
		double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
		// Fireflies: everywhere around, thicker close by
		for (int i = 0; i < 6; i++) {
			double d = 3 + Math.pow(r.nextDouble(), 0.7) * 26;
			double a = r.nextDouble() * Math.PI * 2;
			level.addParticle(ModParticles.SEAM_FIREFLY, px + Math.cos(a) * d, py - 6 + r.nextDouble() * 18, pz + Math.sin(a) * d, 0, 0, 0);
		}
		// Dust of light sifting down from above, on a breath of air that always blows towards the way home
		double wx = 0, wz = 0;
		if (com.extremedimensions.client.state.ClientState.riftKnown()) {
			double dx = com.extremedimensions.client.state.ClientState.riftX - px, dz = com.extremedimensions.client.state.ClientState.riftZ - pz;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > 4) {
				wx = dx / d * 0.014;
				wz = dz / d * 0.014;
			}
		}
		for (int i = 0; i < 3; i++) {
			level.addParticle(ModParticles.SEAM_DUST, px + (r.nextDouble() - 0.5) * 40 - wx * 120, py + 6 + r.nextDouble() * 16,
					pz + (r.nextDouble() - 0.5) * 40 - wz * 120, wx, 0, wz);
		}
		// Twinkles rising out of the void below
		if (r.nextInt(2) == 0) {
			level.addParticle(ModParticles.VOID_MOTE, px + (r.nextDouble() - 0.5) * 30, py - 4 - r.nextDouble() * 10, pz + (r.nextDouble() - 0.5) * 30, 0, 0.02, 0);
		}
	}
}
