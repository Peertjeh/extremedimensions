package com.extremedimensions.client;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.ModPackets;
import com.extremedimensions.Penalty;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Na een Collapse: kies 1 uit 3 penalty's. Kaarten schuiven in, lichten op bij hover, de keuze flitst. */
public class PenaltyChoiceScreen extends Screen {
	private static final int CARD_W = 130;
	private static final int CARD_H = 150;
	private static final int GAP = 14;

	private final List<Penalty> options = new ArrayList<>();
	private final List<Button> buttons = new ArrayList<>();
	private final long openAt = System.currentTimeMillis();
	private int chosen = -1;
	private long chosenAt = 0;

	public PenaltyChoiceScreen(List<String> ids) {
		super(Component.literal("Choose your penalty"));
		for (String id : ids) {
			Penalty p = Penalty.byId(id);
			if (p != null) {
				options.add(p);
			}
		}
	}

	private int startX() {
		int n = options.size();
		return (this.width - (n * CARD_W + (n - 1) * GAP)) / 2;
	}

	private int cardY() {
		return this.height / 2 - CARD_H / 2;
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
			Button b = Button.builder(Component.literal("Choose"), btn -> choose(idx, pen))
					.pos(x + 15, y + CARD_H - 28)
					.size(CARD_W - 30, 20)
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
		return ClientState.ease((now - openAt - i * 130L) / 520.0);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		long now = System.currentTimeMillis();

		// knoppen pas tonen als de kaart klaar is met inschuiven
		for (int i = 0; i < buttons.size(); i++) {
			buttons.get(i).visible = chosen < 0 && progress(i, now) >= 1.0;
		}
		if (chosen >= 0 && now - chosenAt > 600) {
			this.onClose();
			return;
		}

		int cx = this.width / 2;
		int y = cardY();
		g.fill(0, 0, this.width, this.height, ClientState.argb((int) (90 * ClientState.ease((now - openAt) / 400.0)), 0x300000));

		// kop met glitch in het begin
		long age = now - openAt;
		int jit = age < 700 ? (int) (ClientState.hash(now / 60) * 8 - 4) : 0;
		double ha = ClientState.ease(age / 350.0);
		if (age < 700) {
			g.centeredText(this.font, "DIMENSIONAL COLLAPSE", cx + 2 + jit, y - 38, ClientState.argb((int) (170 * ha), 0x30E0FF));
		}
		g.centeredText(this.font, "DIMENSIONAL COLLAPSE", cx - jit / 2, y - 38, ClientState.argb((int) (255 * ha), 0xFF5555));
		g.centeredText(this.font, "Your time ran out. Choose a penalty:", cx, y - 24, ClientState.argb((int) (255 * ha), 0xCCCCCC));

		int x0 = startX();
		for (int i = 0; i < options.size(); i++) {
			Penalty pen = options.get(i);
			double e = progress(i, now);
			int x = x0 + i * (CARD_W + GAP);
			int yo = (int) ((1 - e) * 70);
			double fade = e;
			boolean hover = e >= 1.0 && chosen < 0 && mouseX >= x && mouseX <= x + CARD_W && mouseY >= y - 6 && mouseY <= y + CARD_H;
			int lift = hover ? 6 : 0;
			if (chosen >= 0) {
				fade *= (i == chosen) ? 1.0 : 1 - ClientState.clamp01((now - chosenAt) / 450.0);
			}
			int top = y + yo - lift;
			int border = 0x7A1F1F;
			if (hover) {
				border = 0xFFAA33;
			}
			if (i == chosen) {
				border = ((now / 70) % 2 == 0) ? 0xFFE066 : 0xFF8800;
			}
			g.fill(x - 1, top - 1, x + CARD_W + 1, top + CARD_H + 1, ClientState.argb((int) (255 * fade), border));
			g.fill(x, top, x + CARD_W, top + CARD_H, ClientState.argb((int) (240 * fade), 0x141418));
			if (hover) {
				g.fill(x, top, x + CARD_W, top + CARD_H, ClientState.argb(28, 0xFFAA33));
			}
			if (i == chosen) {
				double fl = 0.5 + 0.5 * Math.sin((now - chosenAt) / 40.0);
				g.fill(x, top, x + CARD_W, top + CARD_H, ClientState.argb((int) (90 * fl), 0xFFE066));
			}
			g.centeredText(this.font, pen.title, x + CARD_W / 2, top + 10, ClientState.argb((int) (255 * fade), 0xFFAA00));
			g.fill(x + 12, top + 24, x + CARD_W - 12, top + 25, ClientState.argb((int) (255 * fade), 0x555555));
			int ly = top + 34;
			for (String line : pen.lines) {
				if (!line.isEmpty()) {
					g.centeredText(this.font, line, x + CARD_W / 2, ly, ClientState.argb((int) (255 * fade), 0xDDDDDD));
					ly += 12;
				}
			}
			int active = ClientState.stacksOf(pen.id);
			if (active > 0) {
				g.centeredText(this.font, "Active: x" + active + " -> x" + (active + 1), x + CARD_W / 2, ly + 2, ClientState.argb((int) (255 * fade), 0xFF5555));
			}
			int loreY = top + 86;
			for (String line : JournalScreen.wrap(this.font, pen.lore, CARD_W - 14)) {
				g.centeredText(this.font, line, x + CARD_W / 2, loreY, ClientState.argb((int) (200 * fade), 0x8FA8C8));
				loreY += 10;
			}
		}
	}

	// Geen @Override zodat dit nooit een compileerfout geeft; werkt als de methode bestaat.
	public boolean shouldCloseOnEsc() {
		return false;
	}

	public boolean isPauseScreen() {
		return false;
	}
}
