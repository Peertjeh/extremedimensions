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
 * under the crosshair with the aim in a line and what matters in a few rows, each with its colour. It fades in a
 * moment after arrival, stays long enough to read, and fades out; it never takes a click.
 */
public class SeamBriefing implements HudElement {
	/** When it starts to show after it came, how long it shows (fades included), and its fades. */
	static final long DELAY_MS = 1200, SHOW_MS = 13000, FADE_IN_MS = 450, FADE_OUT_MS = 900;
	private static final int W = 340;

	private record Row(int colour, String label, String text) {}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		if (ClientState.briefAt <= 0 || ClientState.away()) {
			return;
		}
		long t = System.currentTimeMillis() - ClientState.briefAt - (ClientState.briefKind == 1 ? DELAY_MS : 0);
		if (t < 0) {
			return;
		}
		if (t > SHOW_MS) {
			ClientState.briefAt = 0;
			return;
		}
		double alpha = Math.min(Ui.ease(t / (double) FADE_IN_MS), Ui.clamp01((SHOW_MS - t) / (double) FADE_OUT_MS));
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
		int w = Math.min(W, sw - 16);
		int textX = 22, textW = w - textX - 8;
		List<String> aimLines = Ui.wrap(font, aim, w - 20);
		List<List<String>> wrapped = new ArrayList<>();
		int h = 8 + 12 + aimLines.size() * 10 + 6;
		for (Row r : rows) {
			List<String> lines = Ui.wrap(font, r.text(), textW);
			wrapped.add(lines);
			h += 10 + lines.size() * 9 + 4;
		}
		h += 12;
		// Under the crosshair, clear of the hotbar (and of the timer when it sits above the hotbar); on a short screen as
		// high as it must go, but never over the timer at the top (the hotbar gives way first)
		boolean top = ClientOptions.timer() == ClientOptions.TimerPlace.TOP;
		int floor = sh - (top ? 64 : 64 + 40);
		int ceiling = top ? 5 + 36 + (ClientState.trial.isEmpty() ? 0 : 12) + 4 : 6;
		int y = Math.max(ceiling, Math.min(sh / 2 + 14, floor - h));
		int x = (sw - w) / 2;
		// On a screen too short for it even so, it is drawn smaller, hanging from under the timer
		float fit = Math.max(0.6F, Math.min(1.0F, (sh - 4 - ceiling) / (float) h));
		g.pose().pushMatrix();
		if (fit < 1.0F) {
			y = ceiling;
			g.pose().translate(sw / 2.0F, ceiling);
			g.pose().scale(fit, fit);
			g.pose().translate(-sw / 2.0F, -ceiling);
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
			ly += 10;
			for (String line : wrapped.get(i)) {
				Ui.text(g, font, line, x + textX, ly, Ui.TEXT, alpha);
				ly += 9;
			}
			ly += 4;
		}
		String foot = Ui.tr("gui.extremedimensions.brief.footer");
		Ui.centered(g, font, Ui.ellipsize(font, foot, w - 16), cx, y + h - 11, Ui.MUTED, alpha * 0.9);
		g.pose().popMatrix();
	}
}
