package com.extremedimensions.client.screen;

import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.client.state.ClientState.Mark;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.config.Config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Anchor status (sneak + empty hand on an anchor, or its key): your charge here, your record, your marks, and what makes your drain
 * (3.55.0: every part of it, the Tether, scars and curses among them, as the next expedition would start).
 */
public class StatusScreen extends Screen {
	private static final int PANEL_W = 250;
	private static final int PANEL_H = 222;
	private final long openAt = System.currentTimeMillis();
	/** Co-op: whether the player's anchors are open to their allies (they can draw on the player's charge in them). */
	private boolean sharing;

	public StatusScreen(boolean sharing) {
		super(Component.translatable("gui.extremedimensions.status.title"));
		this.sharing = sharing;
	}

	@Override
	protected void init() {
		int by = this.height / 2 + PANEL_H / 2 - 28;
		if (Config.COOP_SHARED_ANCHORS > 0) {
			this.addRenderableWidget(Button.builder(shareLabel(), b -> {
				sharing = !sharing;
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.extremedimensions.network.ModPackets.ShareAnchorsPayload(sharing));
				b.setMessage(shareLabel());
			}).pos(this.width / 2 - 115, by).size(140, 20)
					.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.extremedimensions.status.share_hint")))
					.build());
			this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
					.pos(this.width / 2 + 31, by).size(84, 20).build());
			return;
		}
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
				.pos(this.width / 2 - 50, by)
				.size(100, 20)
				.build());
	}

	private Component shareLabel() {
		return Component.translatable(sharing ? "gui.extremedimensions.status.share_on" : "gui.extremedimensions.status.share_off");
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		long now = System.currentTimeMillis();
		double e = Ui.ease((now - openAt) / 850.0);
		double pe = Ui.ease((now - openAt) / 300.0);

		int x = this.width / 2 - PANEL_W / 2;
		int y = this.height / 2 - PANEL_H / 2 + (int) ((1 - pe) * 24);
		int cx = this.width / 2;

		Ui.panel(g, x, y, PANEL_W, PANEL_H, Ui.SOUL_DIM, pe);
		Ui.heading(g, this.font, Ui.tr("gui.extremedimensions.status.title"), cx, y + 9, Ui.SOUL, pe);

		meter(g, x + 14, y + 28, Ui.tr("gui.extremedimensions.status.nether"), ClientState.nearNether, e, Ui.EMBER, pe);
		meter(g, x + 14, y + 56, Ui.tr("gui.extremedimensions.status.end"), ClientState.nearEnd, e, Ui.VOID, pe);
		Ui.divider(g, x + 14, y + 84, PANEL_W - 28, Ui.SOUL, pe);

		// Record: three stat columns
		int sy = y + 92;
		stat(g, x + 14 + (PANEL_W - 28) / 6, sy, Ui.tr("gui.extremedimensions.status.expeditions"), String.valueOf((int) (ClientState.expeditions * e)), pe);
		stat(g, cx, sy, Ui.tr("gui.extremedimensions.status.collapses"), String.valueOf((int) (ClientState.collapses * e)), pe);
		stat(g, x + PANEL_W - 14 - (PANEL_W - 28) / 6, sy, Ui.tr("gui.extremedimensions.status.longest"), Ui.time(ClientState.longestRun * e), pe);
		Ui.divider(g, x + 14, y + 120, PANEL_W - 28, Ui.SOUL, pe);

		int ty = y + 128;
		g.text(this.font, Ui.tr("gui.extremedimensions.curses.title"), x + 14, ty, Ui.argb(pe, Ui.SOUL));
		String drain = Ui.tr("hud.extremedimensions.drain", String.format("%.1f", ClientState.drainX100 / 100.0));
		int dx = x + PANEL_W - 14 - this.font.width(drain);
		g.text(this.font, drain, dx, ty, Ui.argb(pe, ClientState.drainX100 > 100 ? Ui.warn() : ClientState.drainX100 < 100 ? Ui.good() : Ui.MUTED));
		int ly = ty + 12;
		// What makes the drain (3.55.0): every part of it, flowing over two lines at most, and all of them one to a line
		// while the drain is pointed at
		int lines = breakdown(g, x + 20, ly, PANEL_W - 34, pe);
		ly += lines * 10 + (lines > 0 ? 2 : 0);
		boolean over = mouseX >= Math.min(dx, x + 20) && mouseX <= x + PANEL_W - 14 && mouseY >= ty - 2 && mouseY <= ty + 9 + lines * 10;
		if (ClientState.marks.isEmpty()) {
			g.text(this.font, Ui.tr("gui.extremedimensions.status.clean"), x + 20, ly, Ui.argb(pe, Ui.good()));
			tip(g, over, mouseX, mouseY);
			return;
		}
		// (as many curses as the lines the drain's parts left: four lines in all, the last saying how many more)
		int room = Math.max(1, 3 - lines);
		int shown = 0;
		for (Mark m : ClientState.marks) {
			if (shown == room && ClientState.marks.size() > room) {
				g.text(this.font, Ui.tr("gui.extremedimensions.status.more", ClientState.marks.size() - room), x + 20, ly, Ui.argb(pe, Ui.MUTED));
				break;
			}
			String name = (m.penalty() != null ? Ui.tr(m.penalty().nameKey()) : m.id()) + " " + m.stacks() + "/" + m.max();
			g.text(this.font, "◆ " + name, x + 20, ly, Ui.argb(pe, Ui.warn()));
			ly += 11;
			shown++;
		}
		tip(g, over, mouseX, mouseY);
	}

	/** A part of the drain in words: "Scars x0.85". */
	private static String part(ClientState.Cause c) {
		return Ui.tr(c.key()) + " ×" + String.format("%.2f", c.value());
	}

	/** A part's colour: green where it eases the drain, yellow where it speeds it. */
	private static int partColour(ClientState.Cause c) {
		return c.value() < 1 ? Ui.good() : Ui.warn();
	}

	/**
	 * The drain's parts side by side, flowing over at most two lines (the rest counted at the end); how many lines it took.
	 * Nothing where the drain has no parts (a plain x1.0).
	 */
	private int breakdown(GuiGraphicsExtractor g, int x, int y, int w, double alpha) {
		var parts = ClientState.drainFactors;
		if (parts.isEmpty()) {
			return 0;
		}
		String sep = " · ";
		int line = 0, cx = x;
		for (int i = 0; i < parts.size(); i++) {
			String s = part(parts.get(i));
			int need = (cx > x ? this.font.width(sep) : 0) + this.font.width(s);
			if (cx > x && cx + need > x + w) {
				if (line == 1) {
					String more = Ui.tr("gui.extremedimensions.status.drain_more", parts.size() - i);
					g.text(this.font, sep + more, cx, y + line * 10, Ui.argb(alpha, Ui.MUTED));
					return 2;
				}
				line++;
				cx = x;
				need = this.font.width(s);
			}
			if (cx > x) {
				g.text(this.font, sep, cx, y + line * 10, Ui.argb(alpha, Ui.MUTED));
				cx += this.font.width(sep);
			}
			g.text(this.font, s, cx, y + line * 10, Ui.argb(alpha, partColour(parts.get(i))));
			cx += this.font.width(s);
		}
		return line + 1;
	}

	/** Every part of the drain, one to a line, while the drain is pointed at. */
	private void tip(GuiGraphicsExtractor g, boolean over, int mouseX, int mouseY) {
		var parts = ClientState.drainFactors;
		if (!over || parts.isEmpty()) {
			return;
		}
		java.util.List<String> lines = new java.util.ArrayList<>();
		java.util.List<Integer> colours = new java.util.ArrayList<>();
		lines.add(ClientState.drainText());
		colours.add(Ui.PARCHMENT);
		for (ClientState.Cause c : parts) {
			lines.add(part(c));
			colours.add(partColour(c));
		}
		Ui.tooltip(g, this.font, lines, colours, mouseX, mouseY, this.width, this.height, Ui.SOUL_DIM);
	}

	private void stat(GuiGraphicsExtractor g, int cx, int y, String label, String value, double alpha) {
		Ui.centered(g, this.font, value, cx, y, Ui.PARCHMENT, alpha);
		g.text(this.font, label, cx - this.font.width(label) / 2, y + 12, Ui.argb(alpha, Ui.MUTED));
	}

	private void meter(GuiGraphicsExtractor g, int x, int y, String name, int value, double e, int color, double alpha) {
		int bw = PANEL_W - 28;
		if (value < 0) {
			g.text(this.font, name, x, y, Ui.argb(alpha, Ui.MUTED));
			String none = Ui.tr("gui.extremedimensions.status.none");
			g.text(this.font, none, x + bw - this.font.width(none), y, Ui.argb(alpha, Ui.MUTED));
			Ui.meter(g, x, y + 12, bw, 7, 0, color, alpha, false);
			return;
		}
		double seconds = value * e;
		g.text(this.font, name, x, y, Ui.argb(alpha, Ui.TEXT));
		String t = Ui.time(seconds);
		g.text(this.font, t, x + bw - this.font.width(t), y, Ui.argb(alpha, color));
		Ui.meter(g, x, y + 12, bw, 7, seconds / Math.max(Config.MAX_CHARGE, value), color, alpha, true);
	}

	/** Its own key closes it (3.55.0), as Escape does. */
	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		return com.extremedimensions.client.ModKeys.closes(this, com.extremedimensions.client.ModKeys.STATUS, event) || super.keyPressed(event);
	}

	public boolean isPauseScreen() {
		return false;
	}
}
