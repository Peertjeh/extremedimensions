package com.extremedimensions.client.hud;

import com.extremedimensions.client.options.ClientOptions;
import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.config.Config;
import com.extremedimensions.guide.Guide;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

/**
 * The in-world HUD: anchor meters and the tether chip (a corner of the player's choice), the expedition timer (top
 * centre or above the hotbar), both at the player's HUD size, and the full-screen moments (low time, Collapse,
 * Expedition Ended, dormant portal, anchor charged). With reduced flashing, strobes, shakes and glitches are left out.
 */
public class ChargeHud implements HudElement {
	private static final int CHIP_W = 104;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		ClientState.tickSmooth();
		if (ClientState.away()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int w = g.guiWidth();
		int h = g.guiHeight();
		long now = System.currentTimeMillis();

		lowTime(g, w, h, now);
		// Meters and timer at the player's HUD size; the full-screen moments always fill the screen
		float scale = ClientOptions.hudScale() / 100f;
		int sw = Math.round(w / scale), sh = Math.round(h / scale);
		g.pose().pushMatrix();
		g.pose().scale(scale, scale);
		chips(g, font, sw, sh, now);
		timer(g, font, sw, sh, now);
		g.pose().popMatrix();
		ended(g, w, h, now);
		bounce(g, w, h, now);
		long dt = now - ClientState.depositAt;
		if (ClientState.depositAt > 0 && dt < 1300) {
			deposit(g, w, h, dt, ClientState.depositKind == 1 ? Ui.EMBER : ClientState.depositKind == 2 ? Ui.VOID : Ui.SOUL);
		}
		taken(g, w, h, now);
		seal(g, w, h, now);
		storm(g, w, h, now);
		collapse(g, font, w, h, now);
	}

	// ------------------------------------------------------------------ top left

	private static void chips(GuiGraphicsExtractor g, Font font, int w, int h, long now) {
		boolean tether = ClientState.dimCode == 0 && ClientState.tetherDist >= 0;
		int count = (ClientState.nearNether >= 0 ? 1 : 0) + (ClientState.nearEnd >= 0 ? 1 : 0) + (tether ? 1 : 0);
		List<String> guide = guideLines(font);
		int guideH = guide.isEmpty() ? 0 : guide.size() * 9 + 17;
		if (guide.isEmpty()) {
			guideClose = null;
		}
		if (count == 0 && guide.isEmpty()) {
			return;
		}
		ClientOptions.Corner corner = ClientOptions.meters();
		boolean right = corner.right();
		int x = right ? w - 6 - CHIP_W : 6;
		int stack = count * 26 + (guide.isEmpty() ? 0 : guideH + 5) - 5;
		int bottomGap = 6;
		if (corner.bottom()) {
			// On a narrow screen a bottom corner reaches the hotbar, hearts and hunger: then the stack sits above them
			float scale = ClientOptions.hudScale() / 100f;
			int widest = guide.isEmpty() ? CHIP_W : GUIDE_W;
			int left = right ? w - 6 - widest : 6, rightEdge = left + widest;
			int bar = Math.round(121 / scale);
			if (rightEdge > w / 2 - bar && left < w / 2 + bar) {
				bottomGap = Math.round(64 / scale);
			}
		}
		int y = corner.bottom() ? h - bottomGap - stack : 6;
		if (!guide.isEmpty()) {
			// The guide sits on the far side of the meters from the screen's edge (above them at the bottom)
			int gy = corner.bottom() ? y : y + count * 26;
			int gx = right ? w - 6 - GUIDE_W : 6;
			// On a narrow screen the card would reach under the expedition timer: then it waits below it
			boolean timerTop = ClientState.dimCode != 0 && (bossBar() || ClientOptions.timer() == ClientOptions.TimerPlace.TOP);
			int tw = bossBar() ? 182 : TIMER_W;
			if (timerTop && !corner.bottom() && (right ? gx < (w + tw) / 2 : gx + GUIDE_W > (w - tw) / 2)) {
				gy = Math.max(gy, timerBottom() + 4);
			}
			guideCard(g, font, gx, gy, guide, guideH);
			if (corner.bottom()) {
				y += guideH + 5;
			}
		}
		if (ClientState.nearNether >= 0) {
			anchorChip(g, font, x, y, right, Ui.tr("hud.extremedimensions.nether_anchor"), ClientState.shownNether, Ui.EMBER,
					ClientState.netherFlashAt, ClientState.netherGain, ClientState.netherGainAt, now);
			y += 26;
		}
		if (ClientState.nearEnd >= 0) {
			anchorChip(g, font, x, y, right, Ui.tr("hud.extremedimensions.end_anchor"), ClientState.shownEnd, Ui.VOID,
					ClientState.endFlashAt, ClientState.endGain, ClientState.endGainAt, now);
			y += 26;
		}
		if (tether) {
			tetherChip(g, font, x, y);
		}
	}

	private static void anchorChip(GuiGraphicsExtractor g, Font font, int x, int y, boolean right, String name, double seconds, int color,
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
			// The gain floats up beside the chip, on the side facing the middle of the screen
			String plus = "+" + Ui.time(gain);
			int gx = right ? x - 6 - font.width(plus) : x + CHIP_W + 6;
			Ui.text(g, font, plus, gx, y + 3 - (int) (Ui.ease(age) * 10), Ui.PARCHMENT, 1 - age);
		}
	}

	private static final int GUIDE_W = 150;

	/** The next step of the first-expedition guide, wrapped to the card; empty when the guide is finished or off. */
	private static List<String> guideLines(Font font) {
		int done = ClientState.guide;
		if (!ClientOptions.guidance() || done >= Guide.STEPS || done < 0) {
			return List.of();
		}
		return Ui.wrap(font, Ui.tr("gui.extremedimensions.guide." + (done + 1)), GUIDE_W - 10);
	}

	/**
	 * Where the guide card's close cross is on the screen (x, y, size in GUI pixels), or null while no card is shown.
	 * The HUD cannot be clicked during play; with the chat open the cross can (see GuideCloseButton).
	 */
	public static volatile int[] guideClose = null;

	private static void guideCard(GuiGraphicsExtractor g, Font font, int x, int y, List<String> lines, int h) {
		Ui.chip(g, x, y, GUIDE_W, h, Ui.SOUL, 0.95);
		String head = Ui.tr("gui.extremedimensions.guide.title").toUpperCase();
		g.text(font, head, x + 5, y + 4, Ui.argb(255, Ui.SOUL));
		String step = (ClientState.guide + 1) + "/" + Guide.STEPS;
		g.text(font, step, x + GUIDE_W - 16 - font.width(step), y + 4, Ui.argb(255, Ui.MUTED));
		// The close cross, top right
		int cx = x + GUIDE_W - 11, cy = y + 4;
		for (int i = 0; i < 7; i++) {
			g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, Ui.argb(220, Ui.MUTED));
			g.fill(cx + 6 - i, cy + i, cx + 7 - i, cy + i + 1, Ui.argb(220, Ui.MUTED));
		}
		float scale = ClientOptions.hudScale() / 100f;
		guideClose = new int[] {Math.round((cx - 2) * scale), Math.round((cy - 2) * scale), Math.round(11 * scale)};
		int ly = y + 15;
		for (String line : lines) {
			g.text(font, line, x + 5, ly, Ui.argb(255, Ui.TEXT));
			ly += 9;
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

	/** The expedition timer panel's size and top edge (in HUD-scaled pixels). */
	private static final int TIMER_W = 196, TIMER_H = 36;

	private static int timerTop(int h) {
		return ClientOptions.timer() == ClientOptions.TimerPlace.TOP ? 5 : h - 64 - TIMER_H;
	}

	/** Whether the expedition timer is the server's boss bar (as Minecraft shows such things) instead of this panel. */
	public static boolean bossBar() {
		return Config.HUD_BOSS_BAR > 0;
	}

	/** How far down the screen the timer reaches (GUI pixels at the HUD's size), for what must sit below it. */
	public static int timerBottom() {
		boolean trial = ClientState.dimCode == 3 && !ClientState.trial.isEmpty();
		// a boss bar is 19 pixels of name and bar from 3 down (one more for a trial), drawn at the game's own size
		return bossBar() ? Math.round((3 + 19 * (trial ? 2 : 1) + 2) / (ClientOptions.hudScale() / 100f)) : 5 + TIMER_H + (trial ? TRIAL_H : 0);
	}

	private static void timer(GuiGraphicsExtractor g, Font font, int w, int h, long now) {
		if (ClientState.dimCode == 0 || bossBar() || (ClientState.collapseAt > 0 && now - ClientState.collapseAt < HOLD_MAX_MS)) {
			return;
		}
		double remaining = ClientState.liveRun();
		boolean nether = ClientState.dimCode == 1;
		boolean seam = ClientState.dimCode == 3;
		int accent = seam ? Ui.SOUL : nether ? Ui.EMBER : Ui.VOID;
		double slide = ClientState.startAt > 0 ? Ui.ease((now - ClientState.startAt) / 550.0) : 1.0;
		// The last ten seconds shake the panel, the last thirty flash the time (neither with reduced flashing)
		double amp = remaining <= 10 && !Ui.calm() ? (10 - remaining) / 10.0 * 3.0 : 0;
		int xs = (int) (Math.sin(now / 35.0) * amp);
		int ys = (int) (Math.cos(now / 41.0) * amp);
		int pw = TIMER_W;
		String[] trial = seam && !ClientState.trial.isEmpty() ? ClientState.trial.split(";") : null;
		if (trial != null && trial.length < 6) {
			trial = null;
		}
		int ph = TIMER_H + (trial != null ? TRIAL_H : 0);
		int px = (w - pw) / 2 + xs;
		boolean top = ClientOptions.timer() == ClientOptions.TimerPlace.TOP;
		// above the hotbar, a taller panel grows upwards
		int py = timerTop(h) - (top ? 0 : ph - TIMER_H) + (int) ((1 - slide) * 48) * (top ? -1 : 1) + ys;
		int barColor = remaining <= 60 ? Ui.DANGER : remaining <= 120 ? Ui.WARN : accent;
		boolean flash = remaining <= 30 && (Ui.calm() || (now / 250) % 2 == 0);

		Ui.panel(g, px, py, pw, ph, barColor, 0.95);
		String name = Ui.tr(seam ? "dimension.extremedimensions.seam" : nether ? "dimension.extremedimensions.nether" : "dimension.extremedimensions.end").toUpperCase();
		g.text(font, name, px + 7, py + 6, Ui.argb(255, accent));
		String t = Ui.time(remaining);
		g.text(font, t, px + pw - 7 - font.width(t), py + 6, Ui.argb(255, flash ? Ui.DANGER : Ui.PARCHMENT));
		Ui.meter(g, px + 7, py + 17, pw - 14, 6, remaining / Math.max(1, ClientState.runMax), barColor, 1.0, true);

		StringBuilder info = new StringBuilder();
		// In the Seam, a gold arrow beside the distance points the way home, turning as the player turns
		boolean compass = seam && ClientState.riftKnown() && Config.SEAM_RIFT_COMPASS > 0 && mcPlayer() != null;
		if (ClientState.tetherDist >= 0) {
			int dist = compass ? riftDistance() : ClientState.tetherDist;
			info.append(Ui.tr(seam ? "hud.extremedimensions.rift_distance" : "hud.extremedimensions.tether_distance", dist));
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
			int tx = px + pw / 2 - font.width(s) / 2 + (compass ? 6 : 0);
			g.text(font, s, tx, py + 26, Ui.argb(255, dc));
			if (compass) {
				arrow(g, tx - 7, py + 30, riftDistance() < 2 ? Float.NaN : riftBearing());
			}
		}
		if (trial != null) {
			trialLine(g, font, trial, px, py + TIMER_H - 2, pw);
		}
	}

	/** The gold of the way home. */
	private static final int HOME = 0xFFCC66;

	private static net.minecraft.world.entity.player.Player mcPlayer() {
		return Minecraft.getInstance().player;
	}

	/** Blocks to the rift home across the ground, measured now (the server's count comes once a second). */
	private static int riftDistance() {
		var p = mcPlayer();
		double dx = ClientState.riftX - p.getX(), dz = ClientState.riftZ - p.getZ();
		return (int) Math.sqrt(dx * dx + dz * dz);
	}

	/** Which way the rift home lies from where the player looks, in radians clockwise (0 straight ahead). */
	private static float riftBearing() {
		var p = mcPlayer();
		double world = Math.atan2(ClientState.riftZ - p.getZ(), ClientState.riftX - p.getX());
		double facing = Math.toRadians(p.getYRot()) + Math.PI / 2;
		return (float) (world - facing);
	}

	/**
	 * A small gold arrow centred on ({@code cx}, {@code cy}) and turned by {@code angle} (clockwise, 0 is up), over a
	 * dark one a pixel lower so it reads on any sky; a gold diamond instead when the angle is NaN (it is right here).
	 */
	private static void arrow(GuiGraphicsExtractor g, int cx, int cy, float angle) {
		for (int pass = 0; pass < 2; pass++) {
			int color = pass == 0 ? Ui.argb(200, 0x000000) : Ui.argb(255, HOME);
			g.pose().pushMatrix();
			g.pose().translate(cx + 0.5f, cy + (pass == 0 ? 1.5f : 0.5f));
			if (Float.isNaN(angle)) {
				g.pose().rotate((float) (Math.PI / 4));
				g.fill(-2, -2, 2, 2, color);
			} else {
				g.pose().rotate(angle);
				// the head, widening down from the tip, then the shaft
				for (int i = 0; i < 4; i++) {
					g.fill(-i - 1, -5 + i, i + 1, -4 + i, color);
				}
				g.fill(-1, -1, 1, 3, color);
			}
			g.pose().popMatrix();
		}
	}

	/** The trial line's height, and the bronze, silver and gold of its medal pips. */
	private static final int TRIAL_H = 11;
	private static final int[] MEDALS = {0xC9814B, 0xC8D0DA, 0xFFC94A};

	/** The Rift Trial under the timer: its name, the score against the next medal, and three medal pips. */
	private static void trialLine(GuiGraphicsExtractor g, Font font, String[] t, int px, int y, int pw) {
		int score, b, sv, gd;
		try {
			score = Integer.parseInt(t[2]);
			b = Integer.parseInt(t[3]);
			sv = Integer.parseInt(t[4]);
			gd = Integer.parseInt(t[5]);
		} catch (NumberFormatException e) {
			return;
		}
		int[] need = {b, sv, gd};
		int medal = 0;
		for (int i = 0; i < 3; i++) {
			if (score >= Math.max(1, need[i])) {
				medal = i + 1;
			}
		}
		g.fill(px + 7, y, px + pw - 7, y + 1, Ui.argb(90, 0xFFC060));
		String name = Ui.tr("gui.extremedimensions.trial.kind." + t[0]);
		String count = medal >= 3 ? String.valueOf(score) : score + " / " + need[medal];
		g.text(font, name, px + 7, y + 3, Ui.argb(255, 0xFFC060));
		int pipX = px + pw - 7 - 3 * 7 + 1;
		g.text(font, count, pipX - 5 - font.width(count), y + 3, Ui.argb(255, medal > 0 ? MEDALS[medal - 1] : Ui.PARCHMENT));
		for (int i = 0; i < 3; i++) {
			int c = i < medal ? MEDALS[i] : Ui.TRACK;
			g.fill(pipX + i * 7, y + 4, pipX + i * 7 + 5, y + 9, Ui.argb(255, i < medal ? Ui.mix(c, 0x000000, 0.45) : Ui.SHADOW));
			g.fill(pipX + i * 7 + 1, y + 5, pipX + i * 7 + 4, y + 8, Ui.argb(255, c));
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
		double pulse = Ui.calm() ? 0.8 : 0.65 + 0.35 * Math.sin(now / (220.0 - 120.0 * intensity));
		int alpha = (int) (0x1C * intensity * pulse);
		if (alpha > 0) {
			Ui.vignette(g, w, h, 0xCC0000, alpha, 8);
		}
	}

	/** Length of the client pull sequence; matches the server's pull (60 ticks). */
	public static final long PULL_MS = 3000;
	/** Longest the screen stays black while waiting for the respawn. */
	private static final long HOLD_MAX_MS = 12000;
	private static final long FADE_IN_MS = 2200;

	/**
	 * The Collapse: the charge snaps, the timer bar breaks, souls rush past as the Tether yanks the player backwards,
	 * the view tunnels into black, the screen stays black through the (skipped) death and respawn, and the Overworld
	 * fades back in.
	 */
	private static void collapse(GuiGraphicsExtractor g, Font font, int w, int h, long now) {
		if (ClientState.collapseAt <= 0) {
			return;
		}
		long ct = now - ClientState.collapseAt;
		boolean home = ClientState.homeAt > ClientState.collapseAt;
		if (ct < PULL_MS) {
			pull(g, font, w, h, now, ct);
		} else if (!home && ct < HOLD_MAX_MS) {
			g.fill(0, 0, w, h, 0xFF000000);
		} else {
			long ft = home ? now - ClientState.homeAt : ct - HOLD_MAX_MS;
			if (ft < FADE_IN_MS) {
				fadeIn(g, w, h, ft);
			}
		}
	}

	private static void pull(GuiGraphicsExtractor g, Font font, int w, int h, long now, long ct) {
		double p = ct / (double) PULL_MS;
		int cx = w / 2;
		int cy = h / 2;
		int tint = ClientState.collapseDim == 3 ? Ui.SOUL_DIM : ClientState.collapseDim == 2 ? Ui.VOID : Ui.EMBER_DEEP;

		boolean calm = Ui.calm();
		// The snap: a hard jolt of light (a soft one with reduced flashing)
		if (ct < 260) {
			g.fill(0, 0, w, h, Ui.argb((int) ((calm ? 40 : 200) * (1 - ct / 260.0)), 0xFFFFFF));
		}
		// Tinted haze that deepens
		g.fill(0, 0, w, h, Ui.argb((int) (70 * Math.min(1, p * 2)), tint));

		// Signal glitches while the tether strains
		if (ct < 1100 && !calm) {
			double gt = 1 - ct / 1100.0;
			long bucket = now / 70;
			for (int i = 0; i < 12; i++) {
				int by = (int) (Ui.hash(bucket * 31 + i) * h);
				int bh = 2 + (int) (Ui.hash(bucket * 17 + i * 3) * 12);
				int shift = (int) ((Ui.hash(bucket * 7 + i) - .5) * 60 * gt);
				g.fill(Math.max(0, shift), by, Math.min(w, w + shift), by + bh, Ui.argb((int) (60 * gt), i % 2 == 0 ? 0xFF2030 : 0x30E0FF));
			}
		}

		// The timer bar snaps in two and falls away, where the player keeps their timer (the boss bar's place at the
		// top of the screen, or their own panel's)
		if (ct < 1300) {
			double t = ct / 1300.0;
			float scale = bossBar() ? 1f : ClientOptions.hudScale() / 100f;
			int barW = (int) (182 * scale);
			int barH = Math.max(2, (int) ((bossBar() ? 5 : 6) * scale));
			int barY = bossBar() ? 12 : (int) ((timerTop(Math.round(h / scale)) + 17) * scale);
			int bx = (w - barW) / 2;
			int half = barW / 2;
			int dx = (int) (t * 200);
			int dy = (int) (t * t * 260);
			int a = (int) (255 * (1 - t));
			g.fill(bx - dx, barY + dy, bx + half - dx, barY + barH + dy, Ui.argb(a, Ui.DANGER));
			g.fill(bx + half + dx, barY + dy + dy / 6, bx + barW + dx, barY + barH + dy + dy / 6, Ui.argb(a, Ui.DANGER));
		}

		// Warp: souls streak outward past the player, faster and brighter as the pull peaks
		double reach = Math.hypot(w, h) * 0.6;
		double speed = 0.9 + p * 2.6;
		for (int i = 0; i < 90; i++) {
			double seed = Ui.hash(i * 13L + 5);
			double ang = Ui.hash(i * 29L + 1) * Math.PI * 2;
			double life = (ct / 1000.0 * speed + seed) % 1.0;
			double r = 8 + life * life * reach;
			double len = 3 + life * 14 * (0.4 + p);
			int a = (int) (Math.min(1, life * 3) * (60 + 170 * p));
			int col = i % 4 == 0 ? 0xFFFFFF : Ui.SOUL;
			for (int k = 0; k < 3; k++) {
				double rr = r - k * len / 3;
				int sx = cx + (int) (Math.cos(ang) * rr);
				int sy = cy + (int) (Math.sin(ang) * rr * 0.75);
				g.fill(sx, sy, sx + 2, sy + 2, Ui.argb(a * (3 - k) / 3, col));
			}
		}

		// Tunnel vision closing in
		Ui.vignette(g, w, h, 0x000000, (int) (20 + 120 * p * p), 10);

		// Title, glitching
		if (ct > 150 && ct < 2000) {
			double ta = Math.min(1, (ct - 150) / 200.0) * (ct < 1600 ? 1.0 : 1 - (ct - 1600) / 400.0);
			int ty = h / 2 - 24;
			int jit = (int) (Ui.hash(now / 70) * 6 - 3);
			String s = Ui.tr("hud.extremedimensions.collapse").toUpperCase();
			int sw = font.width(s) / 2;
			if (!calm) {
				g.text(font, s, cx - sw + 2 + jit, ty, Ui.argb((int) (170 * ta), 0xFF2030));
				g.text(font, s, cx - sw - 2 - jit, ty, Ui.argb((int) (170 * ta), 0x30E0FF));
			}
			g.text(font, s, cx - sw, ty, Ui.argb((int) (255 * ta), 0xFFFFFF));
		}

		// The last moment: a soul-white flash collapsing into black
		if (ct > 2350) {
			double e = Ui.clamp01((ct - 2350) / 650.0);
			g.fill(0, 0, w, h, Ui.argb((int) (255 * e), 0x000000));
			if (ct < 2650 && !calm) {
				double f = 1 - Math.abs(ct - 2500) / 150.0;
				int bw = (int) (w * 0.5 * (1 - e));
				g.fill(cx - bw, cy - 1, cx + bw, cy + 1, Ui.argb((int) (255 * f), 0xE8FFFA));
			}
		}
	}

	/** Back home: the black lifts, a soft soul-light breathes in and motes rise. */
	private static void fadeIn(GuiGraphicsExtractor g, int w, int h, long ft) {
		double e = Ui.ease(ft / (double) FADE_IN_MS);
		g.fill(0, 0, w, h, Ui.argb((int) (255 * (1 - e)), 0x000000));
		Ui.vignette(g, w, h, Ui.SOUL_DIM, (int) (40 * (1 - e)), 8);
		for (int i = 0; i < 40; i++) {
			double seed = Ui.hash(i * 17L + 3);
			double life = (ft / 1800.0 + seed) % 1.0;
			int sx = (int) (Ui.hash(i * 31L + 9) * w);
			int sy = (int) (h - life * h * 0.8);
			g.fill(sx, sy, sx + 2, sy + 2, Ui.argb((int) (Math.sin(life * Math.PI) * 160 * (1 - e)), i % 3 == 0 ? 0xFFFFFF : Ui.SOUL));
		}
	}

	/** A Lurker took something: the edges of vision blacken like spilled ink and violet static shivers through. */
	/**
	 * The seal breaks: a white flash, then dark cracks that branch across the screen from the centre and fade.
	 */
	private static void seal(GuiGraphicsExtractor g, int w, int h, long now) {
		long st = now - ClientState.sealAt;
		if (ClientState.sealAt <= 0 || st >= 5000) {
			return;
		}
		double t = st / 5000.0;
		int flash = (int) ((Ui.calm() ? 50 : 255) * Math.max(0, 1 - st / 700.0));
		if (flash > 0) {
			g.fill(0, 0, w, h, Ui.argb(flash, 0xFFFFFF));
		}
		double grow = Ui.ease(st / 1600.0);
		int alpha = (int) (220 * (1 - t));
		int cx = w / 2, cy = h / 2;
		for (int i = 0; i < 9; i++) {
			double ang = Ui.hash(i * 31L) * Math.PI * 2;
			double x = cx, y = cy;
			int segs = 14;
			for (int k = 0; k < segs * grow; k++) {
				ang += (Ui.hash(i * 97L + k) - 0.5) * 0.9;
				double len = 6 + Ui.hash(i * 13L + k * 7L) * 14;
				double nx = x + Math.cos(ang) * len, ny = y + Math.sin(ang) * len;
				int steps = (int) len;
				for (int s = 0; s < steps; s++) {
					int px = (int) (x + (nx - x) * s / steps), py = (int) (y + (ny - y) * s / steps);
					g.fill(px, py, px + 2, py + 2, Ui.argb(alpha, 0x0A0612));
					g.fill(px + 1, py, px + 2, py + 1, Ui.argb(alpha / 2, Ui.SOUL));
				}
				x = nx;
				y = ny;
			}
		}
		Ui.vignette(g, w, h, 0x000000, (int) (40 * (1 - t)), 8);
	}

	/** A Wound Storm begins: the edges of the screen tear with violet light for a moment. */
	private static void storm(GuiGraphicsExtractor g, int w, int h, long now) {
		long st = now - ClientState.stormAt;
		if (ClientState.stormAt <= 0 || st >= 2500) {
			return;
		}
		double t = st / 2500.0;
		int a = (int) (120 * Math.sin(Math.min(1, st / 300.0) * Math.PI / 2) * (1 - t));
		Ui.vignette(g, w, h, Ui.VOID, a / 6, 8);
		if (Ui.calm()) {
			return;
		}
		long bucket = now / 70;
		for (int i = 0; i < 6; i++) {
			int bx = (int) (Ui.hash(bucket * 5 + i) * w);
			int bh = (int) (Ui.hash(bucket * 9 + i * 3) * h * 0.5);
			g.fill(bx, 0, bx + 1, bh, Ui.argb(a, 0xE8D8FF));
		}
	}

	private static void taken(GuiGraphicsExtractor g, int w, int h, long now) {
		long tt = now - ClientState.takenAt;
		if (ClientState.takenAt <= 0 || tt >= 1800) {
			return;
		}
		double t = tt / 1800.0;
		double in = Ui.ease(tt / 180.0);
		int a = (int) (190 * in * (1 - t));
		Ui.vignette(g, w, h, 0x07000C, a / 4, 10);
		Ui.vignette(g, w, h, Ui.VOID, a / 10, 5);
		if (Ui.calm()) {
			return;
		}
		long bucket = now / 50;
		for (int i = 0; i < 10; i++) {
			int by = (int) (Ui.hash(bucket * 13 + i) * h);
			int bh = 1 + (int) (Ui.hash(bucket * 7 + i * 5) * 3);
			int bw = (int) (Ui.hash(bucket * 3 + i) * w * 0.6);
			int bx = (int) (Ui.hash(bucket * 11 + i * 2) * (w - bw));
			g.fill(bx, by, bx + bw, by + bh, Ui.argb((int) (90 * (1 - t)), i % 2 == 0 ? Ui.VOID : 0x000000));
		}
	}

	/** Dark red haze that slowly lifts (with a vanilla title over it only if the server's config asks for one). */
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
