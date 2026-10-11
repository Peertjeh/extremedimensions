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
		if (ClientState.dimCode == 0 && ClientState.tetherDist >= 0) {
			int max = (int) Config.TETHER_BREAK_RANGE;
			int dist = ClientState.tetherDist;
			int tc = dist >= max ? 0xFF5555 : dist >= max - 1 ? 0xFFAA00 : 0x9FE8F2;
			g.text(font, "Tethered  " + dist + "/" + max + "m", 6, my, ClientState.argb(255, tc));
			my += 12;
		}
		boolean beyond = ClientState.dimCode != 0;
		if (my > 6 || beyond || ClientState.penaltyClock != 0) {
			int py = my + 2;
			if (ClientState.penaltyClock != 0) {
				boolean paused = ClientState.penaltyClock == 2;
				boolean blink = paused && (now / 500) % 2 == 0;
				g.text(font, paused ? "Penalties paused: you are idle!" : "Penalties wearing off...", 6, py,
						ClientState.argb(255, paused ? (blink ? 0xFF5555 : 0xFFAA00) : 0x88DD88));
				py += 12;
			}
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
				// Tether-afstand en drain: hoe verder van het portaal, hoe sneller het gaat
				String info = ClientState.tetherDist >= 0 ? "Tether " + ClientState.tetherDist + "m" : "";
				if (ClientState.drainX100 > 100) {
					info += (info.isEmpty() ? "" : "   ") + "Drain x" + String.format("%.1f", ClientState.drainX100 / 100.0);
				}
				if (!info.isEmpty()) {
					int dc = ClientState.drainX100 >= 300 ? 0xFF5555 : ClientState.drainX100 >= 180 ? 0xFFAA00 : 0x9FE8F2;
					g.centeredText(font, info, w / 2 + xs, by + 14, ClientState.argb(255, dc));
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

		// 5) Door de wond: overgang naar de Nether, de End of terug naar huis
		long tt = now - ClientState.transAt;
		if (ClientState.transAt > 0 && tt < TRANS_MS) {
			drawTransition(g, w, h, tt, ClientState.transKind);
		}

		// 5b) Anker gevuld: vonken worden het midden in gezogen en slaan uit
		long dt = now - ClientState.depositAt;
		if (ClientState.depositAt > 0 && dt < 1300) {
			drawDeposit(g, w, h, dt, ClientState.depositKind == 1 ? NETHER_COLOR : END_COLOR);
		}

		// 6) Expedition Ended: donkerrode waas die langzaam wegtrekt (de grote tekst is een vanilla title)
		long et = now - ClientState.endedAt;
		if (ClientState.endedAt > 0 && et < 3500) {
			double t = et / 3500.0;
			double in = ClientState.ease(et / 400.0);
			int a = (int) (150 * in * (1 - t));
			g.fill(0, 0, w, h, ClientState.argb(a / 2, 0x200000));
			for (int k = 1; k <= 10; k++) {
				int tx = w * k / 40;
				int ty = h * k / 28;
				int col = ClientState.argb(a / 6, 0x600000);
				g.fill(0, 0, w, ty, col);
				g.fill(0, h - ty, w, h, col);
				g.fill(0, ty, tx, h - ty, col);
				g.fill(w - tx, ty, w, h - ty, col);
			}
		}

		// 7) Poort slapend (bounce)
		long bt = now - ClientState.bounceAt;
		if (ClientState.bounceAt > 0 && bt < 700) {
			int a = (int) (130 * (1 - bt / 700.0));
			int th = 14;
			g.fill(0, 0, w, th, ClientState.argb(a, 0xFFAA00));
			g.fill(0, h - th, w, h, ClientState.argb(a, 0xFFAA00));
			g.fill(0, th, th, h - th, ClientState.argb(a, 0xFFAA00));
			g.fill(w - th, th, w, h - th, ClientState.argb(a, 0xFFAA00));
		}

		// 8) Meldingen: schuiven in, vervagen uit
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

	private static final long TRANS_MS = 2800;

	/** {flits/gloed, diepe kleur, tint} per overgang. */
	private static int[] transColors(int kind) {
		return switch (kind) {
			case 1 -> new int[] {0xFFC060, 0x1E0400, 0xFF5A1A};
			case 2 -> new int[] {0xF0DDFF, 0x07000F, 0xB66BFF};
			default -> new int[] {0xEFFFFA, 0x02120E, 0x5FE3D0};
		};
	}

	/**
	 * Een wond scheurt open in het midden van je beeld, met een draaiende ring van tekens,
	 * zwarte filmbalken en deeltjes die bij de dimensie passen. Daarna sluit de wond zich weer.
	 */
	private static void drawTransition(GuiGraphicsExtractor g, int w, int h, long ms, int kind) {
		int[] c = transColors(kind);
		int glow = c[0], deep = c[1], tint = c[2];
		double t = ms / (double) TRANS_MS;
		double fadeOut = 1 - ClientState.clamp01((ms - 1900) / 900.0);
		double env = ClientState.ease(ms / 250.0) * fadeOut;
		int cx = w / 2;
		int cy = h / 2;

		// witte/gekleurde flits op het moment van doorgaan
		if (ms < 320) {
			g.fill(0, 0, w, h, ClientState.argb((int) (210 * (1 - ms / 320.0)), glow));
		}
		// diepe sluier over de wereld, met een getinte rand
		g.fill(0, 0, w, h, ClientState.argb((int) (150 * env), deep));
		for (int k = 1; k <= 8; k++) {
			int tx = w * k / 44;
			int ty = h * k / 30;
			int col = ClientState.argb((int) (22 * env), tint);
			g.fill(0, 0, w, ty, col);
			g.fill(0, h - ty, w, h, col);
			g.fill(0, ty, tx, h - ty, col);
			g.fill(w - tx, ty, w, h - ty, col);
		}

		// de wond: een oog-vorm die opengaat, even ademt en weer dichtgaat
		double open = ClientState.ease((ms - 120) / 650.0) * (1 - ClientState.ease((ms - 1500) / 700.0));
		open *= 1 + 0.05 * Math.sin(ms / 90.0);
		int halfW = (int) (w * 0.42 * (0.25 + 0.75 * ClientState.ease(ms / 520.0)) * (0.6 + 0.4 * fadeOut));
		int halfH = (int) (h * 0.17 * open);
		if (halfW > 2) {
			for (int x = -halfW; x < halfW; x += 2) {
				double f = 1 - Math.pow(x / (double) halfW, 2);
				int hh = (int) (halfH * f);
				int edge = Math.max(1, (int) (3 * f * env));
				// gloeiende rand
				g.fill(cx + x, cy - hh - edge, cx + x + 2, cy + hh + edge, ClientState.argb((int) (230 * env), tint));
				if (hh > 0) {
					g.fill(cx + x, cy - hh, cx + x + 2, cy + hh, ClientState.argb((int) (245 * env), deep));
					// licht dat vanuit het midden de wond in valt
					int core = (int) (hh * 0.35);
					g.fill(cx + x, cy - core, cx + x + 2, cy + core, ClientState.argb((int) (60 * env * f), glow));
				}
			}
			// de felle naad door het midden
			int seam = Math.max(1, (int) (2 * env));
			g.fill(cx - halfW, cy - seam / 2, cx + halfW, cy - seam / 2 + seam, ClientState.argb((int) (255 * env), glow));
			// wat er achter de wond zit
			for (int i = 0; i < 40 && halfH > 2; i++) {
				double sx = (ClientState.hash(i * 13L + kind) * 2 - 1);
				double phase = (ms / (kind == 1 ? 900.0 : 2200.0) + ClientState.hash(i * 7L + 5)) % 1.0;
				int px;
				int py;
				int col;
				if (kind == 1) { // sintels die omhoog dwarrelen
					px = cx + (int) (sx * halfW * 0.85);
					py = cy + halfH - (int) (phase * halfH * 2);
					col = i % 3 == 0 ? 0xFFE08A : 0xFF7A1A;
				} else if (kind == 2) { // sterren die het niets in worden getrokken
					double r = (1 - phase);
					px = cx + (int) (sx * halfW * 0.85 * r);
					py = cy + (int) ((ClientState.hash(i * 3L + 9) * 2 - 1) * halfH * 0.8 * r);
					col = i % 3 == 0 ? 0xFFFFFF : 0xD9A8FF;
				} else { // zielen die opstijgen naar het licht
					px = cx + (int) (sx * halfW * 0.85);
					py = cy + (int) ((0.5 - phase) * halfH * 1.6);
					col = i % 2 == 0 ? 0xCFFFF4 : 0x5FE3D0;
				}
				double fx = 1 - Math.pow((px - cx) / (double) halfW, 2);
				if (Math.abs(py - cy) < halfH * fx) {
					int a = (int) (220 * env * Math.sin(phase * Math.PI));
					g.fill(px, py, px + 2, py + 2, ClientState.argb(a, col));
				}
			}
		}

		// draaiende ring van tekens om de wond
		int ringR = (int) (Math.min(w, h) * (0.30 + 0.04 * ClientState.ease(ms / 900.0)));
		double spin = ms / 1400.0 * (kind == 0 ? -1 : 1);
		for (int i = 0; i < 24; i++) {
			double ang = spin + i * Math.PI * 2 / 24;
			int rx = cx + (int) (Math.cos(ang) * ringR);
			int ry = cy + (int) (Math.sin(ang) * ringR * 0.55);
			int a = (int) (200 * env * (0.6 + 0.4 * Math.sin(ms / 160.0 + i)));
			if (i % 3 == 0) {
				g.fill(rx - 2, ry - 2, rx + 3, ry + 3, ClientState.argb(a, tint));
				g.fill(rx - 1, ry - 1, rx + 2, ry + 2, ClientState.argb(a, glow));
			} else {
				g.fill(rx, ry, rx + 2, ry + 2, ClientState.argb(a / 2 + 30, tint));
			}
		}

		// schokgolf op het moment dat de wond helemaal open is
		long sw = ms - 650;
		if (sw > 0 && sw < 900) {
			double st = sw / 900.0;
			int r = (int) (20 + ClientState.ease(st) * Math.max(w, h) * 0.7);
			int a = (int) (170 * (1 - st));
			for (int i = 0; i < 72; i++) {
				double ang = i * Math.PI * 2 / 72;
				int rx = cx + (int) (Math.cos(ang) * r);
				int ry = cy + (int) (Math.sin(ang) * r * 0.55);
				g.fill(rx - 1, ry - 1, rx + 2, ry + 2, ClientState.argb(a, glow));
			}
		}

		// deeltjes over het hele scherm
		for (int i = 0; i < 46; i++) {
			double seed = ClientState.hash(i * 31L + 11 + kind * 1000L);
			double seed2 = ClientState.hash(i * 17L + 3 + kind * 1000L);
			int px;
			int py;
			int col;
			double life;
			if (kind == 1) { // sintels stijgen op van onderen
				life = ((t * 1.6 + seed) % 1.0);
				px = (int) (seed2 * w + Math.sin(ms / 300.0 + i) * 6);
				py = (int) (h - life * h * 1.1);
				col = i % 4 == 0 ? 0xFFE08A : i % 2 == 0 ? 0xFF7A1A : 0xC0300A;
			} else if (kind == 2) { // alles wordt naar het midden gezogen
				life = ((t * 1.3 + seed) % 1.0);
				double ang = seed2 * Math.PI * 2 + life * 1.2;
				double r = (1 - ClientState.ease(life)) * Math.max(w, h) * 0.6;
				px = cx + (int) (Math.cos(ang) * r);
				py = cy + (int) (Math.sin(ang) * r * 0.6);
				col = i % 3 == 0 ? 0xFFFFFF : 0xB66BFF;
			} else { // zielvonkjes waaieren uit vanuit het licht en stijgen op
				life = ((t * 1.2 + seed) % 1.0);
				double ang = seed2 * Math.PI * 2;
				double r = ClientState.ease(life) * Math.max(w, h) * 0.5;
				px = cx + (int) (Math.cos(ang) * r);
				py = cy + (int) (Math.sin(ang) * r * 0.55 - life * 40);
				col = i % 2 == 0 ? 0xCFFFF4 : 0x5FE3D0;
			}
			int a = (int) (230 * env * Math.sin(life * Math.PI));
			int size = i % 5 == 0 ? 3 : 2;
			g.fill(px, py, px + size, py + size, ClientState.argb(a, col));
		}

		// filmbalken
		double bars = ClientState.ease(ms / 400.0) * (1 - ClientState.ease((ms - 2100) / 600.0));
		int bh = (int) (h / 9.0 * bars);
		if (bh > 0) {
			g.fill(0, 0, w, bh, 0xFF000000);
			g.fill(0, h - bh, w, h, 0xFF000000);
			g.fill(0, bh, w, bh + 1, ClientState.argb((int) (160 * bars), tint));
			g.fill(0, h - bh - 1, w, h - bh, ClientState.argb((int) (160 * bars), tint));
		}
	}

	/** Anker gevuld: vonken spiralen naar het midden van je beeld en slaan uit in een ring. */
	private static void drawDeposit(GuiGraphicsExtractor g, int w, int h, long ms, int color) {
		int cx = w / 2;
		int cy = h / 2;
		double pull = ClientState.clamp01(ms / 650.0);
		double e = pull * pull;
		if (ms < 700) {
			for (int i = 0; i < 18; i++) {
				double ang = i * Math.PI * 2 / 18 + e * 2.2 + ClientState.hash(i) * 0.3;
				double r = (1 - e) * (80 + ClientState.hash(i * 7L) * 40);
				int px = cx + (int) (Math.cos(ang) * r);
				int py = cy + (int) (Math.sin(ang) * r);
				int a = (int) (255 * Math.min(1, ms / 150.0));
				int col = i % 3 == 0 ? 0xFFFFFF : color;
				g.fill(px - 1, py - 1, px + 2, py + 2, ClientState.argb(a, col));
				// staartje richting buiten
				int tx = cx + (int) (Math.cos(ang - 0.15) * (r + 6));
				int ty = cy + (int) (Math.sin(ang - 0.15) * (r + 6));
				g.fill(tx, ty, tx + 1, ty + 1, ClientState.argb(a / 2, col));
			}
		}
		long bt = ms - 600;
		if (bt > 0) {
			double st = bt / 700.0;
			int a = (int) (220 * (1 - st));
			// felle kern
			int core = (int) (7 * (1 - st));
			for (int k = core; k > 0; k--) {
				g.fill(cx - k, cy - (core - k), cx + k + 1, cy + (core - k) + 1, ClientState.argb(a, k > core - 2 ? color : 0xFFFFFF));
			}
			// twee ringen die uitslaan
			for (int ring = 0; ring < 2; ring++) {
				double rt = ClientState.ease(st - ring * 0.12);
				int r = (int) (6 + rt * (ring == 0 ? 60 : 38));
				int ra = (int) (a * (ring == 0 ? 1 : 0.7));
				for (int i = 0; i < 36; i++) {
					double ang = i * Math.PI * 2 / 36;
					int px = cx + (int) (Math.cos(ang) * r);
					int py = cy + (int) (Math.sin(ang) * r);
					g.fill(px, py, px + 2, py + 2, ClientState.argb(ra, ring == 0 ? color : 0xFFFFFF));
				}
			}
			// zachte gloed langs de randen van het scherm
			int ga = (int) (40 * (1 - st));
			for (int k = 1; k <= 5; k++) {
				int tx = w * k / 60;
				int ty = h * k / 40;
				int col = ClientState.argb(ga / 2, color);
				g.fill(0, 0, w, ty, col);
				g.fill(0, h - ty, w, h, col);
				g.fill(0, ty, tx, h - ty, col);
				g.fill(w - tx, ty, w, h - ty, col);
			}
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
