package com.extremedimensions.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.journal.JournalEntry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The Tether Journal: the found pages of a nameless Wanderer, as a two-page spread. */
public class JournalScreen extends Screen {
	private static final int PAGE_W = 150;
	private static final int PAGE_H = 196;
	private static final int SPINE = 10;
	private static final int TEXT_W = PAGE_W - 24;
	private static final int LINES_PER_PAGE = 14;

	/** One page: optional title (first page of an entry only) and lines. */
	private record Page(String title, List<String> lines) {}

	private final Set<String> unlocked;
	private final List<Page> pages = new ArrayList<>();
	/** Index of the left page of the current spread (always even). */
	private int spread = 0;
	private final long openAt = System.currentTimeMillis();
	private long flipAt = 0;
	private Button prev;
	private Button next;

	/** The inventory, when the journal was opened from its button: closing goes back there. */
	private final Screen parent;

	public JournalScreen(Set<String> unlocked, Screen parent) {
		super(Component.translatable("item.extremedimensions.tether_journal"));
		this.unlocked = unlocked;
		this.parent = parent;
	}

	@Override
	public void onClose() {
		if (parent != null) {
			net.minecraft.client.Minecraft.getInstance().gui.setScreen(parent);
		} else {
			super.onClose();
		}
	}

	private int bookX() {
		return this.width / 2 - PAGE_W - SPINE / 2;
	}

	private int bookY() {
		return this.height / 2 - PAGE_H / 2 - 8;
	}

	@Override
	protected void init() {
		pages.clear();
		for (JournalEntry e : JournalEntry.values()) {
			if (!unlocked.contains(e.id)) {
				continue;
			}
			List<String> lines = Ui.wrap(this.font, Ui.tr(e.textKey()), TEXT_W);
			int first = LINES_PER_PAGE - 2; // the title takes room on the first page
			pages.add(new Page(Ui.tr(e.titleKey()), lines.subList(0, Math.min(lines.size(), first))));
			for (int i = first; i < lines.size(); i += LINES_PER_PAGE) {
				pages.add(new Page(null, lines.subList(i, Math.min(lines.size(), i + LINES_PER_PAGE))));
			}
		}
		spread = Math.max(0, Math.min(spread, (pages.size() - 1) & ~1));

		int y = bookY() + PAGE_H + 6;
		prev = this.addRenderableWidget(Button.builder(Component.literal("◀"), b -> flip(-2))
				.pos(bookX(), y).size(24, 18).build());
		next = this.addRenderableWidget(Button.builder(Component.literal("▶"), b -> flip(2))
				.pos(bookX() + PAGE_W * 2 + SPINE - 24, y).size(24, 18).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
				.pos(this.width / 2 - 40, y).size(80, 18).build());
		updateButtons();
	}

	private void flip(int dir) {
		int np = spread + dir;
		if (np >= 0 && np < pages.size()) {
			spread = np;
			flipAt = System.currentTimeMillis();
		}
		updateButtons();
	}

	private void updateButtons() {
		if (prev != null) {
			prev.active = spread > 0;
			next.active = spread + 2 < pages.size();
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		long now = System.currentTimeMillis();
		double pe = Ui.ease((now - openAt) / 350.0);
		int bx = bookX();
		int by = bookY() + (int) ((1 - pe) * 20);
		int bw = PAGE_W * 2 + SPINE;

		// Leather cover, breathing soul-light edge, two parchment pages and a spine
		double breath = Ui.pulse(now, 5655, 0);
		Ui.panel(g, bx - 8, by - 8, bw + 16, PAGE_H + 16, Ui.mix(Ui.SOUL_DIM, Ui.SOUL, breath * 0.5), pe);
		g.fill(bx - 6, by - 6, bx + bw + 6, by + PAGE_H + 6, Ui.argb(pe, 0x2A1C14));
		page(g, bx, by, pe);
		page(g, bx + PAGE_W + SPINE, by, pe);
		g.fill(bx + PAGE_W, by, bx + PAGE_W + SPINE, by + PAGE_H, Ui.argb(pe, 0x1C130D));
		g.fill(bx + PAGE_W + SPINE / 2, by + 2, bx + PAGE_W + SPINE / 2 + 1, by + PAGE_H - 2, Ui.argb(pe * 0.6, Ui.SOUL_DIM));

		// Drifting soul motes over the pages
		for (int i = 0; i < 16; i++) {
			double seed = Ui.hash(i * 31L + 7);
			double phase = (now / 1000.0 / (6 + seed * 6) + seed * 9) % 1.0;
			int sx = bx + 6 + (int) (Ui.hash(i * 17L + 3) * (bw - 12));
			int sy = by + PAGE_H - 6 - (int) (phase * (PAGE_H - 12));
			g.fill(sx, sy, sx + 2, sy + 2, Ui.argb((int) (Math.sin(phase * Math.PI) * 90 * pe), Ui.SOUL));
		}

		if (pages.isEmpty()) {
			String s = Ui.tr("gui.extremedimensions.journal.blank");
			Ui.ink(g, this.font, s, bx + PAGE_W / 2 - this.font.width(s) / 2, by + 80, 0x6A5A48, pe);
			return;
		}
		double fe = flipAt > 0 ? Ui.ease((now - flipAt) / 300.0) : 1.0;
		writePage(g, spread, bx, by, pe * fe);
		writePage(g, spread + 1, bx + PAGE_W + SPINE, by, pe * fe);

		String counter = Ui.tr("gui.extremedimensions.journal.found", unlocked.size(), JournalEntry.values().length);
		g.text(this.font, counter, this.width / 2 - this.font.width(counter) / 2, by - 20, Ui.argb(pe, Ui.MUTED));
	}

	private static void page(GuiGraphicsExtractor g, int x, int y, double alpha) {
		g.fill(x, y, x + PAGE_W, y + PAGE_H, Ui.argb(alpha, 0xD9C9A3));
		g.fill(x, y, x + PAGE_W, y + 3, Ui.argb(alpha * 0.25, 0x6A5A48));
		g.fill(x, y + PAGE_H - 3, x + PAGE_W, y + PAGE_H, Ui.argb(alpha * 0.3, 0x6A5A48));
	}

	private void writePage(GuiGraphicsExtractor g, int index, int x, int y, double alpha) {
		if (index >= pages.size()) {
			return;
		}
		Page p = pages.get(index);
		int ty = y + 12;
		int cx = x + PAGE_W / 2;
		if (p.title() != null) {
			Ui.ink(g, this.font, p.title(), cx - this.font.width(p.title()) / 2, ty, 0x3B2416, alpha);
			g.fill(cx - 30, ty + 11, cx + 30, ty + 12, Ui.argb(alpha * 0.6, 0x2E7F78));
			ty += 22;
		}
		for (String line : p.lines()) {
			Ui.ink(g, this.font, line, x + 12, ty, 0x4A3526, alpha);
			ty += 11;
		}
		String num = String.valueOf(index + 1);
		Ui.ink(g, this.font, num, cx - this.font.width(num) / 2, y + PAGE_H - 13, 0x7A6650, alpha * 0.7);
	}

	public boolean isPauseScreen() {
		return false;
	}
}
