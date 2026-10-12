package com.extremedimensions.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.penalty.Penalty;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** After a Collapse or a death beyond the wound: pick one of three penalty cards. */
public class PenaltyChoiceScreen extends Screen {
	private static final int CARD_W = 132;
	private static final int CARD_H = 164;
	private static final int GAP = 14;

	private final List<Penalty> options = new ArrayList<>();
	private final List<Button> buttons = new ArrayList<>();
	private final long openAt = System.currentTimeMillis();
	private final boolean overworld;
	private int chosen = -1;
	private long chosenAt = 0;

	public PenaltyChoiceScreen(List<String> ids) {
		super(Component.translatable("gui.extremedimensions.choice.title"));
		for (String id : ids) {
			Penalty p = Penalty.byId(id);
			if (p != null) {
				options.add(p);
			}
		}
		overworld = !options.isEmpty() && options.get(0).overworld;
	}

	private int startX() {
		int n = options.size();
		return (this.width - (n * CARD_W + (n - 1) * GAP)) / 2;
	}

	private int cardY() {
		return this.height / 2 - CARD_H / 2 + 10;
	}

	@Override
	protected void init() {
		buttons.clear();
		int x0 = startX();
		int y = cardY();
		for (int i = 0; i < options.size(); i++) {
			final int idx = i;
			final Penalty pen = options.get(i);
			int x = x0 + i * (CARD_W + GAP);
			Button b = Button.builder(Component.translatable("gui.extremedimensions.choice.accept"), btn -> choose(idx, pen))
					.pos(x + 14, y + CARD_H - 28)
					.size(CARD_W - 28, 20)
					.build();
			b.visible = false;
			buttons.add(this.addRenderableWidget(b));
		}
	}

	private void choose(int idx, Penalty pen) {
		if (chosen >= 0) {
			return;
		}
		chosen = idx;
		chosenAt = System.currentTimeMillis();
		ClientPlayNetworking.send(new ModPackets.ChoosePayload(pen.id));
		for (Button b : buttons) {
			b.visible = false;
		}
	}

	private double progress(int i, long now) {
		return Ui.ease((now - openAt - i * 130L) / 520.0);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		long now = System.currentTimeMillis();

		for (int i = 0; i < buttons.size(); i++) {
			buttons.get(i).visible = chosen < 0 && progress(i, now) >= 1.0;
		}
		if (chosen >= 0 && now - chosenAt > 600) {
			this.onClose();
			return;
		}

		int accent = overworld ? Ui.SOUL : Ui.EMBER_DEEP;
		int cx = this.width / 2;
		int y = cardY();
		double bg = Ui.ease((now - openAt) / 400.0);
		g.fill(0, 0, this.width, this.height, Ui.argb((int) (90 * bg), overworld ? 0x001818 : 0x300000));
		Ui.vignette(g, this.width, this.height, 0x000000, (int) (40 * bg), 6);

		// Heading with a short glitch on open
		long age = now - openAt;
		double ha = Ui.ease(age / 350.0);
		int jit = age < 700 ? (int) (Ui.hash(now / 60) * 8 - 4) : 0;
		String head = Ui.tr(overworld ? "gui.extremedimensions.choice.heading_overworld" : "gui.extremedimensions.choice.heading").toUpperCase();
		if (age < 700) {
			Ui.centered(g, this.font, head, cx + 2 + jit, y - 42, 0x30E0FF, ha * 0.65);
		}
		Ui.heading(g, this.font, head, cx - jit / 2, y - 42, accent, ha);
		Ui.centered(g, this.font, Ui.tr("gui.extremedimensions.choice.subtitle"), cx, y - 26, Ui.TEXT, ha);

		int x0 = startX();
		for (int i = 0; i < options.size(); i++) {
			card(g, options.get(i), i, x0 + i * (CARD_W + GAP), y, mouseX, mouseY, now, accent);
		}
	}

	private void card(GuiGraphicsExtractor g, Penalty pen, int i, int x, int y, int mouseX, int mouseY, long now, int accent) {
		double e = progress(i, now);
		double fade = e;
		boolean hover = e >= 1.0 && chosen < 0 && mouseX >= x && mouseX <= x + CARD_W && mouseY >= y - 6 && mouseY <= y + CARD_H;
		if (chosen >= 0) {
			fade *= (i == chosen) ? 1.0 : 1 - Ui.clamp01((now - chosenAt) / 450.0);
		}
		int top = y + (int) ((1 - e) * 70) - (hover ? 6 : 0);
		int border = hover ? Ui.WARN : accent;
		if (i == chosen) {
			border = ((now / 70) % 2 == 0) ? 0xFFE066 : 0xFF8800;
		}
		Ui.panel(g, x, top, CARD_W, CARD_H, border, fade);
		if (hover) {
			g.fill(x + 1, top + 1, x + CARD_W - 1, top + CARD_H - 1, Ui.argb(0.08, Ui.WARN));
		}
		if (i == chosen) {
			double fl = 0.5 + 0.5 * Math.sin((now - chosenAt) / 40.0);
			g.fill(x, top, x + CARD_W, top + CARD_H, Ui.argb((int) (90 * fl), 0xFFE066));
		}

		int mid = x + CARD_W / 2;
		Ui.runeSocket(g, pen.id, mid - 9, top + 8, border, fade, hover ? 1 : 0.4);
		Ui.centered(g, this.font, Ui.tr(pen.nameKey()), mid, top + 32, Ui.PARCHMENT, fade);
		Ui.divider(g, x + 14, top + 45, CARD_W - 28, border, fade);

		int ly = top + 52;
		String[] lines = {Ui.tr(pen.lineKey(1)), Ui.tr(pen.lineKey(2)), Ui.tr(pen.durationKey(), Penalty.minutesPerStack())};
		for (int k = 0; k < lines.length; k++) {
			g.text(this.font, lines[k], mid - this.font.width(lines[k]) / 2, ly, Ui.argb(fade, k == 2 ? Ui.MUTED : Ui.TEXT));
			ly += 11;
		}
		int active = ClientState.stacksOf(pen.id);
		if (active > 0 && pen != Penalty.TOLL && pen != Penalty.TITHE) {
			String s = Ui.tr("gui.extremedimensions.choice.stacks", Ui.roman(active), Ui.roman(active + 1));
			g.text(this.font, s, mid - this.font.width(s) / 2, ly + 1, Ui.argb(fade, Ui.DANGER));
		}
		int loreY = top + 100;
		for (String line : Ui.wrap(this.font, "“" + Ui.tr(pen.loreKey()) + "”", CARD_W - 16)) {
			g.text(this.font, line, mid - this.font.width(line) / 2, loreY, Ui.argb(fade * 0.85, Ui.mix(Ui.SOUL, Ui.TEXT, 0.4)));
			loreY += 10;
		}
	}

	public boolean shouldCloseOnEsc() {
		return false;
	}

	public boolean isPauseScreen() {
		return false;
	}
}
