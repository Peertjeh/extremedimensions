package com.extremedimensions.client.hud;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.client.options.ClientOptions;
import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.client.ui.Ui;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

/**
 * What the Seam is for, said on arrival the first few times (and what the Fray is, the first few times down): a card
 * by the crosshair with the aim in a line and what matters in a few rows, each with its colour. It fades in a
 * moment after arrival, stays long enough to read, and fades out; it never takes a click, but it can be put away
 * (3.55.0): on a key, or by moving once it has shown three seconds (see PutAway).
 */
public class SeamBriefing implements HudElement {
	/** When it starts to show after it came, how long it shows (fades included), and its fades. */
	static final long DELAY_MS = 1200, SHOW_MS = 13000, FADE_IN_MS = 450, FADE_OUT_MS = 900;
	private static final int W = 340;

	/** One row of a card: a gem in its colour, its name, and what to know. */
	record Row(int colour, String label, String text) {}

	/** Whether the briefing was put away, and fades out (3.55.0); known by when it came. */
	private static final PutAway.Card CARD = new PutAway.Card();

	/** Whether a briefing is on the screen now (the guide card steps aside for it). */
	public static boolean showing() {
		if (ClientState.briefAt <= 0 || ClientState.away()) {
			return false;
		}
		long now = System.currentTimeMillis();
		long t = now - ClientState.briefAt - (ClientState.briefKind == 1 ? DELAY_MS : 0);
		return t >= 0 && t <= SHOW_MS && !CARD.gone(ClientState.briefAt, now);
	}

	/** A tick while it shows: the key, or a step after three seconds, puts it away. */
	static void watch(long now, boolean key, net.minecraft.client.player.LocalPlayer player) {
		CARD.watch(ClientState.briefAt, now, key, player);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		if (ClientState.briefAt <= 0 || ClientState.away()) {
			return;
		}
		long t = System.currentTimeMillis() - ClientState.briefAt - (ClientState.briefKind == 1 ? DELAY_MS : 0);
		if (t < 0) {
			return;
		}
		long now = System.currentTimeMillis();
		if (t > SHOW_MS || CARD.gone(ClientState.briefAt, now)) {
			ClientState.briefAt = 0;
			return;
		}
		double alpha = Math.min(Ui.ease(t / (double) FADE_IN_MS), Ui.clamp01((SHOW_MS - t) / (double) FADE_OUT_MS))
				* CARD.fade(ClientState.briefAt, now);
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		float scale = ClientOptions.hudScale() / 100f;
		int sw = Math.round(g.guiWidth() / scale), sh = Math.round(g.guiHeight() / scale);
		g.pose().pushMatrix();
		g.pose().scale(scale, scale);
		draw(g, font, sw, sh, alpha);
		g.pose().popMatrix();
	}

	private static void draw(GuiGraphicsExtractor g, Font font, int sw, int sh, double alpha) {
		boolean fray = ClientState.briefKind == 2;
		int accent = fray ? Ui.BLOOD : Ui.VOID;
		String head = Ui.tr(fray ? "gui.extremedimensions.brief.fray.title" : "gui.extremedimensions.brief.seam.title");
		String aim = Ui.tr(fray ? "gui.extremedimensions.brief.fray.aim" : "gui.extremedimensions.brief.seam.aim");
		List<Row> rows = new ArrayList<>();
		if (fray) {
			String times = String.format(java.util.Locale.ROOT, "%.1f", ClientState.briefValue / 10.0).replace(".0", "");
			rows.add(new Row(Ui.EMBER, Ui.tr("gui.extremedimensions.brief.fray.time"), Ui.tr("gui.extremedimensions.brief.fray.time.text", times)));
			rows.add(new Row(Ui.BLOOD, Ui.tr("gui.extremedimensions.brief.fray.hunt"), Ui.tr("gui.extremedimensions.brief.fray.hunt.text")));
			rows.add(new Row(Ui.WARN, Ui.tr("gui.extremedimensions.brief.fray.strands"), Ui.tr("gui.extremedimensions.brief.fray.strands.text")));
			rows.add(new Row(Ui.SOUL, Ui.tr("gui.extremedimensions.brief.fray.up"), Ui.tr("gui.extremedimensions.brief.fray.up.text")));
		} else {
			rows.add(new Row(Ui.EMBER, Ui.tr("gui.extremedimensions.brief.seam.thread"),
					Ui.tr("gui.extremedimensions.brief.seam.thread.text", Ui.time(Math.max(0, ClientState.briefValue)))));
			rows.add(new Row(Ui.VOID, Ui.tr("gui.extremedimensions.brief.seam.haul"), Ui.tr("gui.extremedimensions.brief.seam.haul.text")));
			rows.add(new Row(Ui.SOUL, Ui.tr("gui.extremedimensions.brief.seam.home"), Ui.tr("gui.extremedimensions.brief.seam.home.text")));
			rows.add(new Row(Ui.BLOOD, Ui.tr("gui.extremedimensions.brief.seam.below"), Ui.tr("gui.extremedimensions.brief.seam.below.text")));
		}
		card(g, font, sw, sh, alpha, accent, head, aim, rows, Ui.tr("gui.extremedimensions.brief.footer"), false);
	}

	/**
	 * A card in the briefing's look, by the crosshair: a panel in the accent's colour with its heading, the aim in a
	 * few centred lines and a divider (no aim: the divider alone), the rows, and the footer (none when null). The recap
	 * after a Collapse is drawn by it too. {@code compact}: a row's name and its text share one line (the texts lined up
	 * after the longest name, and cut short rather than wrapped), so the card holds no more lines than it has rows.
	 */
	static void card(GuiGraphicsExtractor g, Font font, int sw, int sh, double alpha, int accent, String head, String aim, List<Row> rows,
			String foot, boolean compact) {
		int w = Math.min(W, sw - 16);
		int textX = 22, textW = w - textX - 8;
		int labelW = 0;
		if (compact) {
			for (Row r : rows) {
				labelW = Math.max(labelW, font.width(r.label().toUpperCase()));
			}
		}
		List<String> aimLines = aim == null ? List.of() : Ui.wrap(font, aim, w - 20);
		List<List<String>> wrapped = new ArrayList<>();
		int h = 8 + 12 + aimLines.size() * 10 + 6;
		for (Row r : rows) {
			List<String> lines = compact ? List.of(Ui.ellipsize(font, r.text(), textW - labelW - 8)) : Ui.wrap(font, r.text(), textW);
			wrapped.add(lines);
			h += compact ? 12 : 10 + lines.size() * 9 + 4;
		}
		h += foot == null ? 6 : 12;
		// Clear of the hotbar (and of the timer when it sits above the hotbar), never over the timer at the top, and
		// clear of the crosshair (3.55.0): under it where it fits there, else over it, else drawn smaller in the roomier
		// of the two. Only on a screen too short even for that does it cover the crosshair, as high as it must go
		boolean top = ChargeHud.bossBar() || ClientOptions.timer() == ClientOptions.TimerPlace.TOP;
		int floor = sh - (top ? 64 : 64 + 40);
		int ceiling = top ? ChargeHud.timerBottom() + 4 : 6;
		int under = sh / 2 + 14, over = sh / 2 - 12;
		float fit = 1.0F;
		int y;
		if (under + h <= floor) {
			y = under;
		} else if (over - h >= ceiling) {
			y = over - h;
		} else {
			int roomUnder = floor - under, roomOver = over - ceiling;
			fit = Math.max(roomUnder, roomOver) / (float) h;
			if (fit >= 0.6F) {
				y = roomUnder >= roomOver ? under : Math.round(over - h * fit);
			} else {
				// (on a screen too short for it even so, it is drawn smaller, hanging from under the timer)
				fit = Math.max(0.6F, Math.min(1.0F, (sh - 4 - ceiling) / (float) h));
				y = fit < 1.0F ? ceiling : Math.max(ceiling, Math.min(under, floor - h));
			}
		}
		int x = (sw - w) / 2;
		g.pose().pushMatrix();
		if (fit < 1.0F) {
			g.pose().translate(sw / 2.0F, y);
			g.pose().scale(fit, fit);
			g.pose().translate(-sw / 2.0F, -y);
		}
		Ui.panel(g, x, y, w, h, accent, alpha * 0.96);
		int cx = sw / 2;
		Ui.heading(g, font, head, cx, y + 7, accent, alpha);
		int ly = y + 21;
		for (String line : aimLines) {
			Ui.centered(g, font, line, cx, ly, Ui.PARCHMENT, alpha);
			ly += 10;
		}
		Ui.divider(g, x + 12, ly + 1, w - 24, accent, alpha);
		ly += 7;
		for (int i = 0; i < rows.size(); i++) {
			Row r = rows.get(i);
			// a gem in the row's colour, then its name and what to know
			g.fill(x + 9, ly + 1, x + 16, ly + 8, Ui.argb(alpha, Ui.SHADOW));
			g.fill(x + 10, ly + 2, x + 15, ly + 7, Ui.argb(alpha, r.colour()));
			g.fill(x + 10, ly + 2, x + 12, ly + 4, Ui.argb(alpha * 0.7, 0xFFFFFF));
			Ui.text(g, font, r.label().toUpperCase(), x + textX, ly, r.colour(), alpha);
			if (compact) {
				Ui.text(g, font, wrapped.get(i).get(0), x + textX + labelW + 8, ly, Ui.TEXT, alpha);
				ly += 12;
				continue;
			}
			ly += 10;
			for (String line : wrapped.get(i)) {
				Ui.text(g, font, line, x + textX, ly, Ui.TEXT, alpha);
				ly += 9;
			}
			ly += 4;
		}
		if (foot != null) {
			Ui.centered(g, font, Ui.ellipsize(font, foot, w - 16), cx, y + h - 11, Ui.MUTED, alpha * 0.9);
		}
		g.pose().popMatrix();
	}
}
