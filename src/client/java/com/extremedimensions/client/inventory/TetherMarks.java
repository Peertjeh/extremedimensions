package com.extremedimensions.client.inventory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.client.state.ClientState.Mark;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.penalty.Penalty;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * "Tether Marks": active penalties as cards beside the inventory, instead of text on screen. Every penalty has its
 * own rune, its stacks as a Roman numeral and the time left; hovering shows the effect and its lore.
 */
public final class TetherMarks {
	private TetherMarks() {}

	private static final int BOX_W = 128;
	private static final int BOX_H = 26;
	private static final int GAP = 3;

	private static Field leftPos;
	private static Field topPos;
	private static Field imageWidth;
	private static boolean reflectFailed;

	public static void draw(InventoryScreen screen, GuiGraphicsExtractor g, int mouseX, int mouseY) {
		List<Mark> marks = ClientState.marks;
		if (marks.isEmpty()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int[] pos = screenPos(screen);
		int x = pos[0] + pos[2] + 6;
		if (x + BOX_W > screen.width - 2) {
			x = screen.width - BOX_W - 2;
		}
		int y = pos[1];
		long now = System.currentTimeMillis();

		// Header with the clock state
		Ui.panel(g, x, y, BOX_W, 16, Ui.SOUL_DIM, 1.0);
		g.text(font, Ui.tr("gui.extremedimensions.marks.title").toUpperCase(), x + 6, y + 4, Ui.argb(255, Ui.SOUL));
		String status;
		int sc;
		switch (ClientState.penaltyClock) {
			case 1 -> { status = Ui.tr("gui.extremedimensions.marks.fading"); sc = Ui.OK; }
			case 2 -> { status = Ui.tr("gui.extremedimensions.marks.idle"); sc = (now / 500) % 2 == 0 ? Ui.DANGER : Ui.WARN; }
			default -> { status = Ui.tr("gui.extremedimensions.marks.dormant"); sc = Ui.MUTED; }
		}
		g.text(font, status, x + BOX_W - 6 - font.width(status), y + 4, Ui.argb(255, sc));
		y += 16 + GAP + 1;

		int maxRows = Math.max(1, (screen.height - y - 4) / (BOX_H + GAP));
		Mark hovered = null;
		for (int i = 0; i < marks.size() && i < maxRows; i++) {
			Mark m = marks.get(i);
			int by = y + i * (BOX_H + GAP);
			boolean hover = mouseX >= x && mouseX < x + BOX_W && mouseY >= by && mouseY < by + BOX_H;
			card(g, font, m, x, by, hover, now);
			if (hover) {
				hovered = m;
			}
		}
		if (hovered != null) {
			tooltip(g, font, hovered, mouseX, mouseY, screen.width, screen.height);
		}
	}

	private static int accent(Mark m) {
		return m.penalty() != null && m.penalty().overworld ? Ui.SOUL : Ui.EMBER_DEEP;
	}

	private static void card(GuiGraphicsExtractor g, Font font, Mark m, int x, int y, boolean hover, long now) {
		int accent = accent(m);
		double fresh = ClientState.penaltyAt > 0 ? Ui.clamp01(1 - (now - ClientState.penaltyAt) / 1500.0) : 0;
		double breath = 0.5 + 0.5 * Math.sin(now / 700.0 + m.id().hashCode());
		int edge = Ui.mix(accent, 0xFFFFFF, Math.max(fresh * 0.6, hover ? 0.35 : 0));
		Ui.panel(g, x, y, BOX_W, BOX_H, edge, 1.0);
		Ui.runeSocket(g, m.id(), x + 4, y + 4, accent, 1.0, breath);

		String name = m.penalty() != null ? Ui.tr(m.penalty().nameKey()) : m.id();
		if (m.secs() == -2) {
			name = Ui.tr("gui.extremedimensions.marks.xp_debt");
		} else if (m.stacks() > 1) {
			name += " " + Ui.roman(m.stacks());
		}
		g.text(font, name, x + 27, y + 5, Ui.argb(255, Ui.PARCHMENT));

		String when;
		int wc = Ui.MUTED;
		if (m.secs() == -2) {
			when = Ui.tr("gui.extremedimensions.marks.levels_owed", m.stacks());
			wc = 0xC9A0FF;
		} else if (m.secs() < 0) {
			when = Ui.tr("gui.extremedimensions.marks.next_expedition");
			wc = Ui.WARN;
		} else {
			when = Ui.time(m.secs());
			if (m.penalty() != null && m.penalty().timed && ClientState.penaltyClock == 2) {
				wc = Ui.WARN;
			}
		}
		g.text(font, when, x + 27, y + 15, Ui.argb(255, wc));
	}

	private static void tooltip(GuiGraphicsExtractor g, Font font, Mark m, int mx, int my, int sw, int sh) {
		List<String> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		Penalty p = m.penalty();
		lines.add(p != null ? Ui.tr(p.nameKey()) : m.id());
		colors.add(Ui.PARCHMENT);
		if (p != null) {
			lines.add(Ui.tr(p.lineKey(1)) + " " + Ui.tr(p.lineKey(2)));
			colors.add(Ui.TEXT);
			if (p.timed) {
				lines.add(Ui.tr("gui.extremedimensions.marks.per_stack", Ui.tr(p.durationKey(), Penalty.minutesPerStack())));
				colors.add(Ui.MUTED);
			}
			lines.add("“" + Ui.tr(p.loreKey()) + "”");
			colors.add(Ui.SOUL);
		}
		Ui.tooltip(g, font, lines, colors, mx, my, sw, sh, accent(m));
	}

	/** leftPos, topPos and imageWidth of the inventory screen (these fields are not public). */
	private static int[] screenPos(AbstractContainerScreen<?> screen) {
		if (!reflectFailed) {
			try {
				if (leftPos == null) {
					leftPos = AbstractContainerScreen.class.getDeclaredField("leftPos");
					topPos = AbstractContainerScreen.class.getDeclaredField("topPos");
					imageWidth = AbstractContainerScreen.class.getDeclaredField("imageWidth");
					leftPos.setAccessible(true);
					topPos.setAccessible(true);
					imageWidth.setAccessible(true);
				}
				return new int[] {leftPos.getInt(screen), topPos.getInt(screen), imageWidth.getInt(screen)};
			} catch (ReflectiveOperationException | RuntimeException e) {
				reflectFailed = true;
			}
		}
		return new int[] {(screen.width - 176) / 2, (screen.height - 166) / 2, 176};
	}
}
