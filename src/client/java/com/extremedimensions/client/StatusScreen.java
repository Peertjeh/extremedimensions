package com.extremedimensions.client;

import com.extremedimensions.Config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Het altaar-scherm: tanks vullen zich op, getallen tellen op, panel faded in. */
public class StatusScreen extends Screen {
	private static final int PANEL_W = 240;
	private static final int PANEL_H = 190;
	private final long openAt = System.currentTimeMillis();

	public StatusScreen() {
		super(Component.literal("Anchor"));
	}

	@Override
	protected void init() {
		this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
				.pos(this.width / 2 - 50, this.height / 2 + PANEL_H / 2 - 28)
				.size(100, 20)
				.build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		long now = System.currentTimeMillis();
		double e = ClientState.ease((now - openAt) / 850.0);
		double pe = ClientState.ease((now - openAt) / 300.0);

		int slide = (int) ((1 - pe) * 24);
		int x = this.width / 2 - PANEL_W / 2;
		int y = this.height / 2 - PANEL_H / 2 + slide;
		int cx = this.width / 2;
		int pa = (int) (255 * pe);

		double pulse = 0.5 + 0.5 * Math.sin(now / 600.0);
		g.fill(x - 1, y - 1, x + PANEL_W + 1, y + PANEL_H + 1, ClientState.argb(pa, pulse > 0.5 ? 0x6A4FB0 : 0x4A3A7A));
		g.fill(x, y, x + PANEL_W, y + PANEL_H, ClientState.argb((int) (240 * pe), 0x121218));
		g.centeredText(this.font, "DIMENSION ANCHOR", cx, y + 8, ClientState.argb(pa, 0xFFFFFF));

		bar(g, x + 14, y + 28, "Nether anchors nearby", ClientState.nearNether, e, 0xFF7A1A, pa);
		bar(g, x + 14, y + 58, "End anchors nearby", ClientState.nearEnd, e, 0xB66BFF, pa);

		int ty = y + 92;
		int c = ClientState.argb(pa, 0xCCCCCC);
		g.text(this.font, "Expeditions: " + (int) (ClientState.expeditions * e) + "    Collapses: " + (int) (ClientState.collapses * e), x + 14, ty, c);
		g.text(this.font, "Longest run: " + ClientState.time(ClientState.longestRun * e), x + 14, ty + 12, c);
		g.text(this.font, "Drain: x" + String.format("%.1f", ClientState.drainX100 / 100.0), x + 14, ty + 24, c);

		g.text(this.font, "Active penalties:", x + 14, ty + 42, ClientState.argb(pa, 0xFFAA00));
		var lines = ClientState.penaltyLines();
		if (lines.isEmpty()) {
			g.text(this.font, "None. You're clean.", x + 22, ty + 54, ClientState.argb(pa, 0x88DD88));
		} else {
			int ly = ty + 54;
			for (String line : lines) {
				g.text(this.font, "- " + line, x + 22, ly, ClientState.argb(pa, 0xFFAA00));
				ly += 11;
			}
		}
	}

	private void bar(GuiGraphicsExtractor g, int x, int y, String name, int value, double e, int color, int pa) {
		if (value < 0) {
			g.text(this.font, name + ": none", x, y, ClientState.argb(pa, 0x888888));
			g.fill(x, y + 12, x + PANEL_W - 28, y + 20, ClientState.argb(pa, 0x2A2A2A));
			return;
		}
		double seconds = value * e;
		double max = Math.max(Config.MAX_CHARGE, value);
		g.text(this.font, name + ": " + ClientState.time(seconds), x, y, ClientState.argb(pa, color));
		int bw = PANEL_W - 28;
		int fill = (int) (bw * Math.min(1.0, seconds / max));
		g.fill(x, y + 12, x + bw, y + 20, ClientState.argb(pa, 0x2A2A2A));
		if (fill > 0) {
			g.fill(x, y + 12, x + fill, y + 20, ClientState.argb(pa, color));
			int shine = (int) ((System.currentTimeMillis() / 10) % (fill + 30)) - 15;
			int s0 = Math.max(x, x + shine);
			int s1 = Math.min(x + fill, x + shine + 10);
			if (s1 > s0) {
				g.fill(s0, y + 12, s1, y + 20, ClientState.argb(70, 0xFFFFFF));
			}
		}
	}
}
