package com.extremedimensions.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.penalty.Penalty;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * After a Collapse or a death beyond the wound: pick one of three curse cards. Sometimes the whole offer is multiplied;
 * then each card carries its own seal (2x, 3x, 4x) and adds that many stacks, never past the curse's maximum.
 */
public class PenaltyChoiceScreen extends Screen {
	private static final int CARD_W = 132;
	private static final int CARD_H = 164;
	private static final int GAP = 14;

	/** One card: the curse, its multiplier, the player's stacks now, the maximum and the value per stack. */
	private record Option(Penalty pen, int mult, int stacks, int max, String value) {}

	private final List<Option> options = new ArrayList<>();
	private final List<Button> buttons = new ArrayList<>();
	private final long openAt = System.currentTimeMillis();
	private final boolean overworld;
	/** The set the offer comes from, as the server named it ("beyond" or "overworld"); null from an older server. */
	private String set;
	private int chosen = -1;
	private long chosenAt = 0;

	public PenaltyChoiceScreen(List<String> cards) {
		super(Component.translatable("gui.extremedimensions.choice.title"));
		for (String card : cards) {
			if (card.startsWith("@")) {
				set = card.substring(1);
				continue;
			}
			String[] kv = card.split(":");
			Penalty p = Penalty.byId(kv[0]);
			if (p == null) {
				continue;
			}
			try {
				options.add(new Option(p, kv.length > 1 ? Integer.parseInt(kv[1]) : 1, kv.length > 2 ? Integer.parseInt(kv[2]) : 0,
						kv.length > 3 ? Integer.parseInt(kv[3]) : p.max, kv.length > 4 ? kv[4] : p.display()));
			} catch (NumberFormatException e) {
				options.add(new Option(p, 1, 0, p.max, p.display()));
			}
		}
		overworld = setIsOverworld();
	}

	/** Which world's set this offer comes from: from its cards, or (all Everywhere cards) from where the player is. */
	private boolean setIsOverworld() {
		if (set != null) {
			return "overworld".equals(set);
		}
		for (Option o : options) {
			if (!o.pen().everywhere) {
				return o.pen().overworld;
			}
		}
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		return mc.level != null && mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
	}

	/** True when the offer is multiplied (every card then carries a seal). */
	private boolean multiplied() {
		for (Option o : options) {
			if (o.mult() > 1) {
				return true;
			}
		}
		return false;
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
			final Penalty pen = options.get(i).pen();
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
		boolean hungry = multiplied();
		Ui.centered(g, this.font, Ui.tr(hungry ? "gui.extremedimensions.choice.hungry" : "gui.extremedimensions.choice.subtitle"), cx, y - 26,
				hungry ? ((now / 300) % 2 == 0 ? Ui.DANGER : Ui.WARN) : Ui.TEXT, ha);

		int x0 = startX();
		for (int i = 0; i < options.size(); i++) {
			card(g, options.get(i), i, x0 + i * (CARD_W + GAP), y, mouseX, mouseY, now, hungry ? Ui.DANGER : accent);
		}
	}

	private void card(GuiGraphicsExtractor g, Option opt, int i, int x, int y, int mouseX, int mouseY, long now, int accent) {
		Penalty pen = opt.pen();
		double e = progress(i, now);
		double fade = e;
		boolean hover = e >= 1.0 && chosen < 0 && mouseX >= x && mouseX <= x + CARD_W && mouseY >= y - 6 && mouseY <= y + CARD_H;
		if (chosen >= 0) {
			fade *= (i == chosen) ? 1.0 : 1 - Ui.clamp01((now - chosenAt) / 450.0);
		}
		int top = y + (int) ((1 - e) * 70) - (hover ? 6 : 0);
		if (pen.extreme) {
			// A rare Extreme card: blood-red and restless
			double beat = 0.5 + 0.5 * Math.sin(now / 220.0 + i);
			accent = Ui.mix(Ui.BLOOD, 0xFF6070, 0.25 * beat);
		} else if (pen.everywhere) {
			accent = Ui.mix(Ui.VOID, accent, 0.25);
		}
		// Hovered cards light up gold; an Extreme card only warms, it never loses its blood-red
		int border = hover ? (pen.extreme ? Ui.mix(Ui.BLOOD, Ui.WARN, 0.45) : Ui.WARN) : accent;
		if (i == chosen) {
			border = ((now / 70) % 2 == 0) ? 0xFFE066 : 0xFF8800;
		}
		Ui.panel(g, x, top, CARD_W, CARD_H, border, fade);
		if (pen.extreme) {
			g.fill(x + 1, top + 1, x + CARD_W - 1, top + CARD_H - 1, Ui.argb(fade * 0.12, Ui.BLOOD));
			String mark = Ui.tr("gui.extremedimensions.choice.extreme_mark");
			int mw = this.font.width(mark) + 8, mx = x + CARD_W / 2 - mw / 2;
			g.fill(mx, top - 6, mx + mw, top + 5, Ui.argb(fade, Ui.SHADOW));
			g.fill(mx + 1, top - 5, mx + mw - 1, top + 4, Ui.argb(fade, Ui.mix(Ui.INK, Ui.BLOOD, 0.6)));
			g.text(this.font, mark, mx + 4, top - 4, Ui.argb(fade, 0xFFE0E0));
		}
		if (hover && !pen.extreme) {
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
		// Stacks past the maximum spill over onto other curses; the third line says how many
		int spill = Math.max(0, opt.stacks() + opt.mult() - opt.max());
		String third = spill > 0 ? Ui.tr("gui.extremedimensions.choice.spill", spill)
				: Ui.tr(pen.extreme ? "gui.extremedimensions.choice.extreme"
						: pen.everywhere ? "gui.extremedimensions.choice.everywhere" : "gui.extremedimensions.choice.permanent", opt.max());
		String[] lines = {Ui.tr(pen.lineKey(1), opt.value()), Ui.tr(pen.lineKey(2), opt.value()), third};
		for (int k = 0; k < lines.length; k++) {
			int col = k < 2 ? Ui.TEXT : spill > 0 ? Ui.WARN : pen.extreme ? Ui.mix(Ui.BLOOD, 0xFFFFFF, 0.3)
					: pen.everywhere ? Ui.mix(Ui.VOID, Ui.MUTED, 0.4) : Ui.MUTED;
			g.text(this.font, lines[k], mid - this.font.width(lines[k]) / 2, ly, Ui.argb(fade, col));
			ly += 11;
		}
		// Stacks now, and after this card (capped at the maximum)
		int after = Math.min(opt.max(), opt.stacks() + opt.mult());
		String s = after >= opt.max()
				? Ui.tr("gui.extremedimensions.choice.stacks", opt.stacks(), after, opt.max()) + " " + Ui.tr("gui.extremedimensions.choice.capped")
				: Ui.tr("gui.extremedimensions.choice.stacks", opt.stacks(), after, opt.max());
		g.text(this.font, s, mid - this.font.width(s) / 2, ly + 1, Ui.argb(fade, opt.stacks() > 0 || opt.mult() > 1 ? Ui.DANGER : Ui.WARN));
		if (opt.mult() > 1) {
			seal(g, opt.mult(), x + CARD_W - 18, top - 7, now, fade);
		}
		int loreY = top + 100;
		for (String line : Ui.wrap(this.font, "“" + Ui.tr(pen.loreKey()) + "”", CARD_W - 16)) {
			g.text(this.font, line, mid - this.font.width(line) / 2, loreY, Ui.argb(fade * 0.85, Ui.mix(Ui.SOUL, Ui.TEXT, 0.4)));
			loreY += 10;
		}
	}

	/** The multiplier seal on a card's corner: "3x" in a red-gold diamond that breathes. */
	private void seal(GuiGraphicsExtractor g, int mult, int x, int y, long now, double fade) {
		double beat = 0.5 + 0.5 * Math.sin(now / 160.0 + mult);
		int hot = Ui.mix(Ui.DANGER, 0xFFE066, mult >= 4 ? 0.2 + 0.5 * beat : mult == 3 ? 0.35 * beat : 0.15 * beat);
		int w = 26, h = 16;
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Ui.argb(fade, Ui.SHADOW));
		g.fill(x, y, x + w, y + h, Ui.argb(fade, hot));
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, Ui.argb(fade, Ui.mix(Ui.INK, hot, 0.35)));
		g.fill(x + 2, y + 1, x + w - 2, y + 2, Ui.argb(fade * 0.5, 0xFFFFFF));
		String t = mult + "x";
		g.text(this.font, t, x + w / 2 - this.font.width(t) / 2, y + 4, Ui.argb(fade, 0xFFF4D0), true);
	}

	public boolean shouldCloseOnEsc() {
		return false;
	}

	public boolean isPauseScreen() {
		return false;
	}
}
