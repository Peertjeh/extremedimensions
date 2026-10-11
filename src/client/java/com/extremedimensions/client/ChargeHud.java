package com.extremedimensions.client;

import com.extremedimensions.Config;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

/** De HUD met alle animaties: tankmeters, timer, Collapse, veilig terug, meldingen. */
public class ChargeHud implements HudElement {
	private static final int NETHER_COLOR = 0xFF7A1A;
	private static final int END_COLOR = 0xB66BFF;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		ClientState.tickSmooth();
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int w = g.guiWidth();
		int h = g.guiHeight();
		long now = System.currentTimeMillis();

		// 1) Rode rand bij weinig tijd
		if (ClientState.dimCode != 0) {
			double remaining = ClientState.liveRun();
			if (remaining <= 60) {
				double intensity = (60 - remaining) / 60.0;
				double pulse = 0.65 + 0.35 * Math.sin(now / (220.0 - 120.0 * intensity));
				int alpha = (int) (0x1C * intensity * pulse);
				if (alpha > 0) {
					int col = ClientState.argb(alpha, 0xCC0000);
					for (int k = 1; k <= 8; k++) {
						int tx = w * k / 50;
						int ty = h * k / 32;
						g.fill(0, 0, w, ty, col);
						g.fill(0, h - ty, w, h, col);
						g.fill(0, ty, tx, h - ty, col);
						g.fill(w - tx, ty, w, h - ty, col);
					}
				}
			}
		}

		// 2) Kleine meters linksboven: alleen in de buurt van geladen altaren
		int my = 6;
		if (ClientState.nearNether >= 0) {
			drawMini(g, font, 6, my, "Nether", ClientState.shownNether, NETHER_COLOR, ClientState.netherFlashAt, ClientState.netherGain, ClientState.netherGainAt, now);
			my += 16;
		}
		if (ClientState.nearEnd >= 0) {
			drawMini(g, font, 6, my, "End", ClientState.shownEnd, END_COLOR, ClientState.endFlashAt, ClientState.endGain, ClientState.endGainAt, now);
			my += 16;
		}
		if (my > 6) {
			int py = my + 2;
			for (String line : ClientState.penaltyLines()) {
				double pulse = ClientState.penaltyAt > 0 ? ClientState.clamp01(1 - (now - ClientState.penaltyAt) / 1200.0) : 0;
				g.text(font, line, 6, py, ClientState.argb(255, pulse > 0 ? 0xFFDD66 : 0xFFAA00));
				py += 10;
			}
		}

		// 3) Grote timer in Nether/End
		if (ClientState.dimCode != 0) {
			double remaining = ClientState.liveRun();
			boolean nether = ClientState.dimCode == 1;
			int base = nether ? NETHER_COLOR : END_COLOR;
			double slide = ClientState.startAt > 0 ? ClientState.ease((now - ClientState.startAt) / 550.0) : 1.0;
			int yOff = (int) (-(1 - slide) * 44);
			double amp = remaining <= 10 ? (10 - remaining) / 10.0 * 3.0 : 0;
			int xs = (int) (Math.sin(now / 35.0) * amp);
			int ys = (int) (Math.cos(now / 41.0) * amp);
			boolean flash = remaining <= 30 && ((now / 250) % 2 == 0);
			int barW = 180;
			int bx = (w - barW) / 2 + xs;
			int by = 20 + yOff + ys;
			int fill = (int) (barW * Math.min(1.0, remaining / Math.max(1, ClientState.runMax)));
			int barColor = remaining <= 60 ? 0xFF3030 : remaining <= 120 ? 0xFFAA00 : base;
			boolean collapsing = ClientState.collapseAt > 0 && now - ClientState.collapseAt < 2000;
			if (!collapsing) {
				String label = (nether ? "NETHER  " : "END  ") + ClientState.time(remaining);
				g.centeredText(font, label, w / 2 + xs, 8 + yOff + ys, ClientState.argb(255, flash ? 0xFF5555 : 0xFFFFFF));
				g.fill(bx - 2, by - 2, bx + barW + 2, by + 10, ClientState.argb(176, 0x000000));
				g.fill(bx, by, bx + barW, by + 8, 0xFF2A2A2A);
				g.fill(bx, by, bx + Math.max(2, fill), by + 8, ClientState.argb(255, barColor));
				// glanzende veeg over de balk
				int sweep = (int) ((now / 12) % (barW + 40)) - 20;
				int sx0 = Math.max(bx, bx + sweep);
				int sx1 = Math.min(bx + Math.max(2, fill), bx + sweep + 14);
				if (sx1 > sx0) {
					g.fill(sx0, by, sx1, by + 8, ClientState.argb(70, 0xFFFFFF));
				}
				if (ClientState.drainX100 > 100) {
					g.centeredText(font, "Drain x" + String.format("%.1f", ClientState.drainX100 / 100.0), w / 2 + xs, by + 14, ClientState.argb(255, 0xFFAA00));
				}
			}
		}

		// 4) Collapse-animatie
		long ct = now - ClientState.collapseAt;
		if (ClientState.collapseAt > 0 && ct < 2000) {
			double t = ct / 2000.0;
			if (ct < 260) {
				g.fill(0, 0, w, h, ClientState.argb((int) (230 * (1 - ct / 260.0)), 0xFFFFFF));
			}
			g.fill(0, 0, w, h, ClientState.argb((int) (95 * (1 - t)), 0x990000));
			long bucket = now / 70;
			for (int i = 0; i < 14; i++) {
				int by2 = (int) (ClientState.hash(bucket * 31 + i) * h);
				int bh = 3 + (int) (ClientState.hash(bucket * 17 + i * 3) * 16);
				int shift = (int) ((ClientState.hash(bucket * 7 + i) - .5) * 70 * (1 - t));
				int col = (i % 2 == 0) ? 0xFF2030 : 0x30E0FF;
				g.fill(Math.max(0, shift), by2, Math.min(w, w + shift), by2 + bh, ClientState.argb((int) (75 * (1 - t)), col));
			}
			// de timerbalk knapt in tweeen en valt weg
			int barW = 180;
			int bx = (w - barW) / 2;
			int half = barW / 2;
			int dx = (int) (t * 240);
			int dy = (int) (t * t * 320);
			int a = (int) (255 * (1 - t));
			g.fill(bx - dx, 20 + dy, bx + half - dx, 28 + dy, ClientState.argb(a, 0xFF3030));
			g.fill(bx + half + dx, 20 + dy + dy / 6, bx + barW + dx, 28 + dy + dy / 6, ClientState.argb(a, 0xFF3030));
			for (int i = 0; i < 18; i++) {
				double rx = ClientState.hash(i * 5L + 1) - .5;
				double ry = ClientState.hash(i * 11L + 2);
				int spx = w / 2 + (int) (rx * 360 * t);
				int spy = 24 + (int) (ry * 90 * t + t * t * 160);
				g.fill(spx, spy, spx + 3, spy + 3, ClientState.argb((int) (255 * (1 - t)), i % 2 == 0 ? 0xFFAA33 : 0xFFEE99));
			}
			if (ct < 1500) {
				double ta = ct < 1100 ? 1.0 : 1 - (ct - 1100) / 400.0;
				int ty = h / 2 - 20;
				int jit = (int) (ClientState.hash(bucket) * 6 - 3);
				g.centeredText(font, "DIMENSIONAL COLLAPSE", w / 2 + 2 + jit, ty, ClientState.argb((int) (200 * ta), 0xFF2030));
				g.centeredText(font, "DIMENSIONAL COLLAPSE", w / 2 - 2 - jit, ty, ClientState.argb((int) (200 * ta), 0x30E0FF));
				g.centeredText(font, "DIMENSIONAL COLLAPSE", w / 2, ty, ClientState.argb((int) (255 * ta), 0xFFFFFF));
			}
		}

		// 5) Veilig terug-animatie
		long st = now - ClientState.safeAt;
		if (ClientState.safeAt > 0 && st < 1600) {
			double t = st / 1600.0;
			int lx = (int) (w * ClientState.ease(st / 700.0));
			for (int k = 0; k < 6; k++) {
				g.fill(lx - 2 - k * 6, 0, lx + 2 - k * 6, h, ClientState.argb((int) ((120 - k * 18) * (1 - t)), 0x55FF55));
			}
			g.centeredText(font, "SAFELY BACK", w / 2, h / 3, ClientState.argb((int) (255 * (1 - t * t)), 0x88FF88));
			for (int i = 0; i < 16; i++) {
				double ang = i * 0.39 + t * 2;
				int r = (int) (30 + ClientState.ease(st / 900.0) * 80);
				int sx = w / 2 + (int) (Math.cos(ang) * r);
				int sy = h / 3 + 4 + (int) (Math.sin(ang) * r * 0.45);
				g.fill(sx, sy, sx + 3, sy + 3, ClientState.argb((int) (230 * (1 - t)), i % 2 == 0 ? 0xCCFFCC : 0x55FF55));
			}
		}

		// 6) Poort slapend (bounce)
		long bt = now - ClientState.bounceAt;
		if (ClientState.bounceAt > 0 && bt < 700) {
			int a = (int) (130 * (1 - bt / 700.0));
			int th = 14;
			g.fill(0, 0, w, th, ClientState.argb(a, 0xFFAA00));
			g.fill(0, h - th, w, h, ClientState.argb(a, 0xFFAA00));
			g.fill(0, th, th, h - th, ClientState.argb(a, 0xFFAA00));
			g.fill(w - th, th, w, h - th, ClientState.argb(a, 0xFFAA00));
		}

		// 7) Meldingen: schuiven in, vervagen uit
		if (now < ClientState.noticeUntil) {
			String t = ClientState.noticeText;
			long age = now - ClientState.noticeAt;
			long left = ClientState.noticeUntil - now;
			double in = ClientState.ease(age / 260.0);
			double out = left < 600 ? left / 600.0 : 1.0;
			double k = in * out;
			int tw = font.width(t);
			int ny = h / 4 - (int) ((1 - in) * 20);
			g.fill(w / 2 - tw / 2 - 8, ny - 5, w / 2 + tw / 2 + 8, ny + font.lineHeight + 5, ClientState.argb((int) (176 * k), 0x000000));
			g.fill(w / 2 - tw / 2 - 8, ny + font.lineHeight + 4, w / 2 - tw / 2 - 8 + (int) ((tw + 16) * in), ny + font.lineHeight + 5, ClientState.argb((int) (255 * k), ClientState.noticeColor));
			g.centeredText(font, t, w / 2, ny, ClientState.argb((int) (255 * k), ClientState.noticeColor));
		}
	}

	private static void drawMini(GuiGraphicsExtractor g, Font font, int x, int y, String name, double seconds, int color,
								long flashAt, int gain, long gainAt, long now) {
		double flash = flashAt > 0 ? ClientState.clamp01(1 - (now - flashAt) / 900.0) : 0;
		int textColor = flash > 0 ? mix(color, 0xFFFFFF, flash) : color;
		g.text(font, name + "  " + ClientState.time(seconds), x, y, ClientState.argb(255, textColor));
		int bw = 70;
		int fill = (int) (bw * Math.min(1.0, seconds / Math.max(Config.MAX_CHARGE, seconds)));
		g.fill(x, y + 10, x + bw, y + 13, ClientState.argb(144, 0x000000));
		if (fill > 0) {
			g.fill(x, y + 10, x + fill, y + 13, ClientState.argb(255, color));
		}
		if (flash > 0) {
			g.fill(x - 2, y + 8, x + bw + 2, y + 15, ClientState.argb((int) (90 * flash), color));
			g.fill(x, y + 10, x + fill, y + 13, ClientState.argb((int) (170 * flash), 0xFFFFFF));
		}
		double age = gainAt > 0 ? (now - gainAt) / 1700.0 : 2;
		if (age < 1) {
			g.text(font, "+" + ClientState.time(gain), x + 82, y - (int) (ClientState.ease(age) * 12), ClientState.argb((int) (255 * (1 - age)), 0xFFFFFF));
		}
	}

	private static int mix(int a, int b, double t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
