package com.extremedimensions.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.Penalty;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * "Tether Marks": je penalty's als eigen kaartjes naast de inventory, in plaats van tekst in beeld.
 * Elke penalty heeft een eigen rune (een symmetrisch patroon uit zijn naam), de stacks als Romeins cijfer
 * en de tijd die nog over is. Ga met je muis erop staan voor de uitleg en de lore.
 */
public final class TetherMarks {
	private TetherMarks() {}

	private static final int BOX_W = 124;
	private static final int BOX_H = 26;
	private static final int GAP = 2;
	private static final int BEYOND = 0xC0462E;
	private static final int OVERWORLD = 0x3FA7B8;

	private record Mark(String id, Penalty pen, int stacks, int secs) {}

	private static Field leftPos;
	private static Field topPos;
	private static Field imageWidth;
	private static boolean reflectFailed;

	public static void draw(InventoryScreen screen, GuiGraphicsExtractor g, int mouseX, int mouseY) {
		List<Mark> marks = parse();
		if (marks.isEmpty()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int[] pos = screenPos(screen);
		int x = pos[0] + pos[2] + 4;
		if (x + BOX_W > screen.width - 2) {
			x = screen.width - BOX_W - 2;
		}
		int y = pos[1];
		long now = System.currentTimeMillis();

		// Kop met de status van de klok
		g.text(font, "TETHER MARKS", x + 2, y, ClientState.argb(255, 0x8FE6F0));
		String status;
		int sc;
		switch (ClientState.penaltyClock) {
			case 1 -> { status = "fading"; sc = 0x88DD88; }
			case 2 -> { status = "paused: idle"; sc = (now / 500) % 2 == 0 ? 0xFF5555 : 0xFFAA00; }
			default -> { status = "dormant"; sc = 0x7A7268; }
		}
		g.text(font, status, x + BOX_W - font.width(status) - 2, y, ClientState.argb(255, sc));
		y += 11;

		int maxRows = Math.max(1, (screen.height - y - 4) / (BOX_H + GAP));
		Mark hovered = null;
		int hx = 0;
		int hy = 0;
		for (int i = 0; i < marks.size() && i < maxRows; i++) {
			Mark m = marks.get(i);
			int by = y + i * (BOX_H + GAP);
			boolean hover = mouseX >= x && mouseX < x + BOX_W && mouseY >= by && mouseY < by + BOX_H;
			drawBox(g, font, m, x, by, hover, now);
			if (hover) {
				hovered = m;
				hx = mouseX;
				hy = mouseY;
			}
		}
		if (hovered != null) {
			drawTooltip(g, font, hovered, hx, hy, screen.width, screen.height);
		}
	}

	private static void drawBox(GuiGraphicsExtractor g, Font font, Mark m, int x, int y, boolean hover, long now) {
		boolean ow = m.pen() != null && m.pen().overworld;
		int accent = ow ? OVERWORLD : BEYOND;
		double fresh = ClientState.penaltyAt > 0 ? ClientState.clamp01(1 - (now - ClientState.penaltyAt) / 1500.0) : 0;
		double breath = 0.5 + 0.5 * Math.sin(now / 700.0 + m.id().hashCode());
		int edge = mix(accent, 0xFFFFFF, Math.max(fresh * 0.6, hover ? 0.35 : 0));

		g.fill(x, y, x + BOX_W, y + BOX_H, ClientState.argb(235, 0x0E0A10));
		g.fill(x, y, x + BOX_W, y + 1, ClientState.argb(255, edge));
		g.fill(x, y + BOX_H - 1, x + BOX_W, y + BOX_H, ClientState.argb(255, mix(edge, 0x000000, 0.45)));
		g.fill(x, y, x + 1, y + BOX_H, ClientState.argb(255, edge));
		g.fill(x + BOX_W - 1, y, x + BOX_W, y + BOX_H, ClientState.argb(255, mix(edge, 0x000000, 0.45)));
		g.fill(x + 1, y + 1, x + BOX_W - 1, y + BOX_H - 1, ClientState.argb(255, ow ? 0x101A1C : 0x1A0F0E));

		// Rune-vakje
		int ix = x + 4;
		int iy = y + 4;
		g.fill(ix, iy, ix + 18, iy + 18, ClientState.argb(255, 0x050306));
		g.fill(ix, iy, ix + 18, iy + 18, ClientState.argb((int) (40 + 40 * breath), accent));
		drawRune(g, m.id(), ix + 2, iy + 2, mix(accent, 0xFFFFFF, 0.25 + 0.2 * breath));

		// Naam + stacks, en daaronder de tijd
		String name = m.pen() != null ? m.pen().title : m.id();
		if (m.secs() == -2) {
			name = "XP Debt";
		} else if (m.stacks() > 1) {
			name += " " + roman(m.stacks());
		}
		g.text(font, name, x + 26, y + 4, ClientState.argb(255, 0xE8D9B8));
		String when;
		int wc = 0x9A8F80;
		if (m.secs() == -2) {
			when = m.stacks() + (m.stacks() == 1 ? " level owed" : " levels owed");
			wc = 0xC9A0FF;
		} else if (m.secs() < 0) {
			when = "next expedition";
			wc = 0xFFAA00;
		} else {
			when = ClientState.time(m.secs());
			if (m.pen() != null && m.pen().timed && ClientState.penaltyClock == 2) {
				wc = 0xFFAA00;
			}
		}
		g.text(font, when, x + 26, y + 14, ClientState.argb(255, wc));
	}

	/** Een rune van 7x7 pixels (3x7 gespiegeld), afgeleid uit de naam van de penalty: elke penalty zijn eigen teken. */
	private static void drawRune(GuiGraphicsExtractor g, String id, int x, int y, int color) {
		long h = id.hashCode() * 0x9E3779B97F4A7C15L;
		for (int row = 0; row < 7; row++) {
			for (int col = 0; col < 4; col++) {
				boolean on = ((h >>> (row * 4 + col)) & 1) == 1 || (col == 3 && (row == 0 || row == 6));
				if (on) {
					int px = x + col * 2;
					int mx = x + (6 - col) * 2;
					int py = y + row * 2;
					g.fill(px, py, px + 2, py + 2, ClientState.argb(255, color));
					g.fill(mx, py, mx + 2, py + 2, ClientState.argb(255, color));
				}
			}
		}
	}

	private static void drawTooltip(GuiGraphicsExtractor g, Font font, Mark m, int mx, int my, int sw, int sh) {
		List<String> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		Penalty p = m.pen();
		lines.add(p != null ? p.title : m.id());
		colors.add(0xE8D9B8);
		if (p != null) {
			lines.add(p.lines[0] + " " + p.lines[1]);
			colors.add(0xCDBFA5);
			if (m.secs() >= 0 && p.timed) {
				lines.add("Each stack: " + p.lines[2]);
				colors.add(0x9A8F80);
			}
			lines.add("\"" + p.lore + "\"");
			colors.add(0x9FE8F2);
		}
		int w = 0;
		for (String l : lines) {
			w = Math.max(w, font.width(l));
		}
		int h = lines.size() * 10 + 6;
		int x = Math.min(mx + 10, sw - w - 10);
		int y = Math.min(my - 4, sh - h - 4);
		g.fill(x - 4, y - 4, x + w + 4, y + h, 0xF0100A14);
		g.fill(x - 4, y - 4, x + w + 4, y - 3, ClientState.argb(255, p != null && p.overworld ? OVERWORLD : BEYOND));
		for (int i = 0; i < lines.size(); i++) {
			g.text(font, lines.get(i), x, y + i * 10, ClientState.argb(255, colors.get(i)));
		}
	}

	private static List<Mark> parse() {
		List<Mark> out = new ArrayList<>();
		String all = ClientState.penalties;
		if (all == null || all.isEmpty()) {
			return out;
		}
		for (String part : all.split(";")) {
			String[] kv = part.split(":");
			try {
				int stacks = kv.length > 1 ? Integer.parseInt(kv[1]) : 1;
				int secs = kv.length > 2 ? Integer.parseInt(kv[2]) : 0;
				out.add(new Mark(kv[0], Penalty.byId(kv[0]), stacks, secs));
			} catch (NumberFormatException e) {
				// kapotte regel overslaan
			}
		}
		return out;
	}

	/** leftPos, topPos en imageWidth van het inventory-scherm (die velden zijn niet publiek). */
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

	private static String roman(int n) {
		String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
	}

	private static int mix(int a, int b, double t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int gg = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (gg << 8) | bl;
	}
}
