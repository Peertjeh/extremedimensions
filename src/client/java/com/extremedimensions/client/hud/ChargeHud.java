package com.extremedimensions.client.hud;

import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.config.Config;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

/**
 * The in-world HUD: anchor meters and the tether chip (top left), the expedition timer (top centre), and the
 * full-screen moments (low time, Collapse, Expedition Ended, dormant portal, anchor charged).
 */
public class ChargeHud implements HudElement {
	private static final int CHIP_W = 104;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		ClientState.tickSmooth();
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui) {
			return;
		}
		Font font = mc.font;
		int w = g.guiWidth();
		int h = g.guiHeight();
		long now = System.currentTimeMillis();

		lowTime(g, w, h, now);
		chips(g, font, now);
		timer(g, font, w, now);
		collapse(g, font, w, h, now);
		ended(g, w, h, now);
		bounce(g, w, h, now);
		long dt = now - ClientState.depositAt;
		if (ClientState.depositAt > 0 && dt < 1300) {
			deposit(g, w, h, dt, ClientState.depositKind == 1 ? Ui.EMBER : ClientState.depositKind == 2 ? Ui.VOID : Ui.SOUL);
		}
	}

	// ------------------------------------------------------------------ top left

	private static void chips(GuiGraphicsExtractor g, Font font, long now) {
		int y = 6;
		if (ClientState.nearNether >= 0) {
			anchorChip(g, font, 6, y, Ui.tr("hud.extremedimensions.nether_anchor"), ClientState.shownNether, Ui.EMBER,
					ClientState.netherFlashAt, ClientState.netherGain, ClientState.netherGainAt, now);
			y += 26;
		}
		if (ClientState.nearEnd >= 0) {
			anchorChip(g, font, 6, y, Ui.tr("hud.extremedimensions.end_anchor"), ClientState.shownEnd, Ui.VOID,
					ClientState.endFlashAt, ClientState.endGain, ClientState.endGainAt, now);
			y += 26;
		}
		if (ClientState.dimCode == 0 && ClientState.tetherDist >= 0) {
			tetherChip(g, font, 6, y);
		}
	}

	private static void anchorChip(GuiGraphicsExtractor g, Font font, int x, int y, String name, double seconds, int color,
			long flashAt, int gain, long gainAt, long now) {
		double flash = flashAt > 0 ? Ui.clamp01(1 - (now - flashAt) / 900.0) : 0;
		Ui.chip(g, x, y, CHIP_W, 21, color, 1.0);
		if (flash > 0) {
			g.fill(x, y, x + CHIP_W, y + 21, Ui.argb(flash * 0.25, color));
		}
		g.text(font, name, x + 5, y + 3, Ui.argb(255, Ui.mix(Ui.TEXT, 0xFFFFFF, flash)));
		String t = Ui.time(seconds);
		g.text(font, t, x + CHIP_W - 4 - font.width(t), y + 3, Ui.argb(255, Ui.mix(color, 0xFFFFFF, flash)));
		Ui.meter(g, x + 5, y + 14, CHIP_W - 10, 3, seconds / Math.max(Config.MAX_CHARGE, seconds), color, 1.0, false);
		double age = gainAt > 0 ? (now - gainAt) / 1700.0 : 2;
		if (age < 1) {
			Ui.text(g, font, "+" + Ui.time(gain), x + CHIP_W + 6, y + 3 - (int) (Ui.ease(age) * 10), Ui.PARCHMENT, 1 - age);
		}
	}

	private static void tetherChip(GuiGraphicsExtractor g, Font font, int x, int y) {
		int max = (int) Config.TETHER_BREAK_RANGE;
		int dist = ClientState.tetherDist;
		double strain = Ui.clamp01(dist / (double) max);
		int color = strain >= 1 ? Ui.DANGER : strain >= 0.8 ? Ui.WARN : Ui.SOUL;
		Ui.chip(g, x, y, CHIP_W, 21, color, 1.0);
		g.text(font, Ui.tr("hud.extremedimensions.tethered"), x + 5, y + 3, Ui.argb(255, Ui.TEXT));
		String d = dist + "/" + max + "m";
		g.text(font, d, x + CHIP_W - 4 - font.width(d), y + 3, Ui.argb(255, color));
		Ui.meter(g, x + 5, y + 14, CHIP_W - 10, 3, strain, color, 1.0, false);
	}

	// ------------------------------------------------------------------ expedition timer

	private static void timer(GuiGraphicsExtractor g, Font font, int w, long now) {
		if (ClientState.dimCode == 0 || (ClientState.collapseAt > 0 && now - ClientState.collapseAt < 2000)) {
			return;
		}
		double remaining = ClientState.liveRun();
		boolean nether = ClientState.dimCode == 1;
		int accent = nether ? Ui.EMBER : Ui.VOID;
		double slide = ClientState.startAt > 0 ? Ui.ease((now - ClientState.startAt) / 550.0) : 1.0;
		double amp = remaining <= 10 ? (10 - remaining) / 10.0 * 3.0 : 0;
		int xs = (int) (Math.sin(now / 35.0) * amp);
		int ys = (int) (Math.cos(now / 41.0) * amp);
		int pw = 196;
		int ph = 36;
		int px = (w - pw) / 2 + xs;
		int py = 5 - (int) ((1 - slide) * 48) + ys;
		int barColor = remaining <= 60 ? Ui.DANGER : remaining <= 120 ? Ui.WARN : accent;
		boolean flash = remaining <= 30 && (now / 250) % 2 == 0;

		Ui.panel(g, px, py, pw, ph, barColor, 0.95);
		String name = Ui.tr(nether ? "dimension.extremedimensions.nether" : "dimension.extremedimensions.end").toUpperCase();
		g.text(font, name, px + 7, py + 6, Ui.argb(255, accent));
		String t = Ui.time(remaining);
		g.text(font, t, px + pw - 7 - font.width(t), py + 6, Ui.argb(255, flash ? Ui.DANGER : Ui.PARCHMENT));
		Ui.meter(g, px + 7, py + 17, pw - 14, 6, remaining / Math.max(1, ClientState.runMax), barColor, 1.0, true);

		StringBuilder info = new StringBuilder();
		if (ClientState.tetherDist >= 0) {
			info.append(Ui.tr("hud.extremedimensions.tether_distance", ClientState.tetherDist));
		}
		if (ClientState.drainX100 > 100) {
			if (info.length() > 0) {
				info.append("  ·  ");
			}
			info.append(Ui.tr("hud.extremedimensions.drain", String.format("%.1f", ClientState.drainX100 / 100.0)));
		}
		if (info.length() > 0) {
			int dc = ClientState.drainX100 >= 300 ? Ui.DANGER : ClientState.drainX100 >= 180 ? Ui.WARN : Ui.MUTED;
			String s = info.toString();
			g.text(font, s, px + pw / 2 - font.width(s) / 2, py + 26, Ui.argb(255, dc));
		}
	}

	// ------------------------------------------------------------------ full-screen moments

	private static void lowTime(GuiGraphicsExtractor g, int w, int h, long now) {
		if (ClientState.dimCode == 0) {
			return;
		}
		double remaining = ClientState.liveRun();
		if (remaining > 60) {
			return;
		}
		double intensity = (60 - remaining) / 60.0;
		double pulse = 0.65 + 0.35 * Math.sin(now / (220.0 - 120.0 * intensity));
		int alpha = (int) (0x1C * intensity * pulse);
		if (alpha > 0) {
			Ui.vignette(g, w, h, 0xCC0000, alpha, 8);
		}
	}

	private static void collapse(GuiGraphicsExtractor g, Font font, int w, int h, long now) {
		long ct = now - ClientState.collapseAt;
		if (ClientState.collapseAt <= 0 || ct >= 2000) {
			return;
		}
		double t = ct / 2000.0;
		if (ct < 260) {
			g.fill(0, 0, w, h, Ui.argb((int) (230 * (1 - ct / 260.0)), 0xFFFFFF));
		}
		g.fill(0, 0, w, h, Ui.argb((int) (95 * (1 - t)), 0x990000));
		long bucket = now / 70;
		for (int i = 0; i < 14; i++) {
			int by = (int) (Ui.hash(bucket * 31 + i) * h);
			int bh = 3 + (int) (Ui.hash(bucket * 17 + i * 3) * 16);
			int shift = (int) ((Ui.hash(bucket * 7 + i) - .5) * 70 * (1 - t));
			int col = (i % 2 == 0) ? 0xFF2030 : 0x30E0FF;
			g.fill(Math.max(0, shift), by, Math.min(w, w + shift), by + bh, Ui.argb((int) (75 * (1 - t)), col));
		}
		// the timer bar snaps in two and falls away
		int barW = 182;
		int bx = (w - barW) / 2;
		int half = barW / 2;
		int dx = (int) (t * 240);
		int dy = (int) (t * t * 320);
		int a = (int) (255 * (1 - t));
		g.fill(bx - dx, 22 + dy, bx + half - dx, 28 + dy, Ui.argb(a, Ui.DANGER));
		g.fill(bx + half + dx, 22 + dy + dy / 6, bx + barW + dx, 28 + dy + dy / 6, Ui.argb(a, Ui.DANGER));
		for (int i = 0; i < 18; i++) {
			double rx = Ui.hash(i * 5L + 1) - .5;
			double ry = Ui.hash(i * 11L + 2);
			int spx = w / 2 + (int) (rx * 360 * t);
			int spy = 24 + (int) (ry * 90 * t + t * t * 160);
			g.fill(spx, spy, spx + 3, spy + 3, Ui.argb((int) (255 * (1 - t)), i % 2 == 0 ? 0xFFAA33 : 0xFFEE99));
		}
		if (ct < 1500) {
			double ta = ct < 1100 ? 1.0 : 1 - (ct - 1100) / 400.0;
			int ty = h / 2 - 20;
			int jit = (int) (Ui.hash(bucket) * 6 - 3);
			String s = Ui.tr("hud.extremedimensions.collapse").toUpperCase();
			int sw = font.width(s) / 2;
			g.text(font, s, w / 2 - sw + 2 + jit, ty, Ui.argb((int) (200 * ta), 0xFF2030));
			g.text(font, s, w / 2 - sw - 2 - jit, ty, Ui.argb((int) (200 * ta), 0x30E0FF));
			g.text(font, s, w / 2 - sw, ty, Ui.argb((int) (255 * ta), 0xFFFFFF));
		}
	}

	/** Dark red haze that slowly lifts (the big text is a vanilla title). */
	private static void ended(GuiGraphicsExtractor g, int w, int h, long now) {
		long et = now - ClientState.endedAt;
		if (ClientState.endedAt <= 0 || et >= 3500) {
			return;
		}
		double t = et / 3500.0;
		int a = (int) (150 * Ui.ease(et / 400.0) * (1 - t));
		g.fill(0, 0, w, h, Ui.argb(a / 2, 0x200000));
		Ui.vignette(g, w, h, 0x600000, a / 6, 10);
	}

	/** Dormant portal: a short amber pulse around the screen edge. */
	private static void bounce(GuiGraphicsExtractor g, int w, int h, long now) {
		long bt = now - ClientState.bounceAt;
		if (ClientState.bounceAt <= 0 || bt >= 700) {
			return;
		}
		int a = (int) (130 * (1 - bt / 700.0));
		int th = 14;
		g.fill(0, 0, w, th, Ui.argb(a, Ui.WARN));
		g.fill(0, h - th, w, h, Ui.argb(a, Ui.WARN));
		g.fill(0, th, th, h - th, Ui.argb(a, Ui.WARN));
		g.fill(w - th, th, w, h - th, Ui.argb(a, Ui.WARN));
	}

	/** Anchor charged or tether bound: sparks spiral into the crosshair and burst into rings. */
	private static void deposit(GuiGraphicsExtractor g, int w, int h, long ms, int color) {
		int cx = w / 2;
		int cy = h / 2;
		double pull = Ui.clamp01(ms / 650.0);
		double e = pull * pull;
		if (ms < 700) {
			for (int i = 0; i < 18; i++) {
				double ang = i * Math.PI * 2 / 18 + e * 2.2 + Ui.hash(i) * 0.3;
				double r = (1 - e) * (80 + Ui.hash(i * 7L) * 40);
				int px = cx + (int) (Math.cos(ang) * r);
				int py = cy + (int) (Math.sin(ang) * r);
				int a = (int) (255 * Math.min(1, ms / 150.0));
				int col = i % 3 == 0 ? 0xFFFFFF : color;
				g.fill(px - 1, py - 1, px + 2, py + 2, Ui.argb(a, col));
				int tx = cx + (int) (Math.cos(ang - 0.15) * (r + 6));
				int ty = cy + (int) (Math.sin(ang - 0.15) * (r + 6));
				g.fill(tx, ty, tx + 1, ty + 1, Ui.argb(a / 2, col));
			}
		}
		long bt = ms - 600;
		if (bt > 0) {
			double st = bt / 700.0;
			int a = (int) (220 * (1 - st));
			int core = (int) (7 * (1 - st));
			for (int k = core; k > 0; k--) {
				g.fill(cx - k, cy - (core - k), cx + k + 1, cy + (core - k) + 1, Ui.argb(a, k > core - 2 ? color : 0xFFFFFF));
			}
			for (int ring = 0; ring < 2; ring++) {
				double rt = Ui.ease(st - ring * 0.12);
				int r = (int) (6 + rt * (ring == 0 ? 60 : 38));
				int ra = (int) (a * (ring == 0 ? 1 : 0.7));
				for (int i = 0; i < 36; i++) {
					double ang = i * Math.PI * 2 / 36;
					int px = cx + (int) (Math.cos(ang) * r);
					int py = cy + (int) (Math.sin(ang) * r);
					g.fill(px, py, px + 2, py + 2, Ui.argb(ra, ring == 0 ? color : 0xFFFFFF));
				}
			}
			Ui.vignette(g, w, h, color, (int) (20 * (1 - st)), 5);
		}
	}
}
