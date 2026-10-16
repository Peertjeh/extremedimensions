package com.extremedimensions.client.fx;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.client.sky.SeamFog;
import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.config.Config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The way home as the visitor sees it: a column of gold light standing over the rift, from deep in the nothing below
 * to high over the islands, gold being a light nothing else in the Seam gives. Like the lit mountain of Journey or the
 * sword's beam in Shadow of the Colossus, it is there to be seen from anywhere: farther off than the fog lets anything
 * show, the column is drawn nearer, on the true line to it and shrunk to the same size in view, so it glows faint
 * through the mist exactly where it stands. At the rift itself: a slow glow, gold ripples running out over the
 * threshold and sparks rising. Only the visitor whose rift it is sees it (each visitor has their own).
 */
public final class RiftBeacon {
	private RiftBeacon() {}

	/** The gold of the way home. */
	private static final float R = 1.0F, G = 0.80F, B = 0.42F;
	/** How far the column reaches down into the nothing below the rift. */
	private static final int BELOW = 48;
	/** Tiles the column is made of, laid end to end. */
	private static final int TILES = 90;

	private static final List<AttackFxParticle> COLUMN = new ArrayList<>();
	private static AttackFxParticle glow;
	private static ClientLevel shownIn;
	private static int ticks;

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || !ClientState.riftKnown() || Config.SEAM_RIFT_BEAM <= 0 || !AttackFx.ready()) {
			clear();
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		if (shownIn != level) {
			clear();
			shownIn = level;
		}
		ticks++;
		RandomSource rnd = level.getRandom();
		Vec3 eye = mc.gameRenderer.getMainCamera().position();
		double rx = ClientState.riftX, ry = ClientState.riftY, rz = ClientState.riftZ;
		double dx = rx - eye.x, dz = rz - eye.z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		// Past what the fog lets show, drawn nearer on the true line to it, at the same size in view
		double near = Math.max(24, SeamFog.end() * 0.7);
		double s = dist > near ? near / dist : 1;
		double y0 = ry - BELOW, y1 = ry + Config.SEAM_RIFT_BEAM;
		double step = (y1 - y0) / TILES;
		float half = (float) Math.max(step * s / 2, 0.0045 * near);
		// Standing in it, it thins so it does not blind
		float close = (float) Mth.clamp((dist - 1.0) / 5.0, 0.3, 1.0);
		boolean fresh = COLUMN.isEmpty() || !COLUMN.get(0).isAlive();
		if (fresh) {
			clearColumn();
		}
		for (int i = 0; i < TILES; i++) {
			double y = y0 + (i + 0.5) * step;
			double px = eye.x + (rx - eye.x) * s, py = eye.y + (y - eye.y) * s, pz = eye.z + (rz - eye.z) * s;
			AttackFxParticle p;
			if (fresh) {
				p = AttackFx.make(level, AttackFx.BEAM, rnd.nextInt(4), px, py, pz);
				p.mode = AttackFxParticle.Mode.AXIS;
				p.axis.set(0, 1, 0);
				p.size0 = p.size1 = half;
				p.setLifetime(40);
				p.fadeIn = 20;
				p.fadeOut = 0;
				p.frames = 4;
				p.frameRate = 0.2F;
				p.pulse = 0.07F;
				p.pulseDepth = 0.12F;
				p.color(R, G, B);
				AttackFx.add(p);
				COLUMN.add(p);
			} else {
				p = COLUMN.get(i);
				p.place(px, py, pz);
				p.size0 = p.size1 = half;
			}
			p.extend(40);
			// It fades out into the nothing below and thins away high above
			double k = (double) i / (TILES - 1);
			double ends = Math.min(1, Math.min(k / 0.25, (1 - k) / 0.3));
			p.peak = (float) (0.8 * ends * ends) * close;
		}
		// The glow over the rift, seen where it really is
		boolean there = s >= 1;
		if (glow == null || !glow.isAlive()) {
			glow = AttackFx.make(level, AttackFx.FLASH, 0, rx, ry + 1.1, rz);
			glow.size0 = glow.size1 = 1.3F;
			glow.setLifetime(40);
			glow.fadeIn = 10;
			glow.fadeOut = 0;
			glow.pulse = 0.09F;
			glow.pulseDepth = 0.3F;
			glow.color(R, G, B);
			AttackFx.add(glow);
		}
		glow.place(rx, ry + 1.1, rz);
		glow.extend(40);
		glow.peak = there ? 0.55F : 0;
		if (!there) {
			return;
		}
		// Gold ripples out over the threshold now and then
		if (dist < 48 && ticks % 36 == 0) {
			AttackFxParticle w = AttackFx.make(level, AttackFx.WAVE, rnd.nextInt(3), rx, ry + 0.03, rz);
			w.mode = AttackFxParticle.Mode.GROUND;
			w.size0 = 0.3F;
			w.size1 = 2.6F;
			w.growTicks = 40;
			w.growPower = 0.6F;
			w.setLifetime(44);
			w.fadeIn = 2;
			w.fadeOut = 22;
			w.peak = 0.8F;
			w.spin = 0.02F;
			w.color(R, G, B);
			AttackFx.add(w);
		}
		// Sparks rising round it
		if (dist < 32 && ticks % 3 == 0) {
			double a = rnd.nextDouble() * Math.PI * 2, r = 0.3 + rnd.nextDouble() * 0.6;
			AttackFxParticle g = AttackFx.make(level, AttackFx.GLINT, rnd.nextInt(4), rx + Math.cos(a) * r, ry + 0.2, rz + Math.sin(a) * r);
			g.size0 = g.size1 = 0.08F + rnd.nextFloat() * 0.06F;
			g.velocity(0, 0.05 + rnd.nextDouble() * 0.04, 0);
			g.setLifetime(30 + rnd.nextInt(20));
			g.fadeIn = 4;
			g.fadeOut = 12;
			g.color(R, G, B);
			AttackFx.add(g);
		}
	}

	private static void clearColumn() {
		for (AttackFxParticle p : COLUMN) {
			p.remove();
		}
		COLUMN.clear();
	}

	private static void clear() {
		if (!COLUMN.isEmpty()) {
			clearColumn();
		}
		if (glow != null) {
			glow.remove();
			glow = null;
		}
		shownIn = null;
	}
}
