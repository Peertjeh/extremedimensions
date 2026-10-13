package com.extremedimensions.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.extremedimensions.client.inventory.CursesButton;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.penalty.Penalty;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * The Curses: everything the Starved took, in two tabs (beyond the wound and the Overworld). Each curse is a rune in a
 * grid with its stacks as pips under it; the selected one shows its effect, its lore and its atonement, and can be
 * chosen as the curse to atone for (and to mend first). Scars and how to atone sit at the foot.
 */
public class CursesScreen extends Screen {
	private static final int PW = 352, PH = 212;
	private static final int COLS = 6, CELL = 22;

	/** One curse as the server sent it. */
	private record Entry(Penalty pen, int stacks, int max, int progress, int cost, String value) {}

	private final Screen parent;
	private final long openAt = System.currentTimeMillis();
	private final Map<Penalty, Entry> entries = new HashMap<>();
	private final List<Socket> sockets = new ArrayList<>();
	private int scars;
	private String atoning = "";
	private boolean overworldOn = true;
	private boolean overworldTab;
	private Penalty selected;
	private Button atone;

	public CursesScreen(String data, Screen parent) {
		super(Component.translatable("gui.extremedimensions.curses.title"));
		this.parent = parent;
		update(data);
		// Open on the tab with curses, and on the heaviest one there
		overworldTab = carried(false) == 0 && carried(true) > 0;
		selected = heaviest(overworldTab);
	}

	/** A Curses payload arrived: open the screen, or refresh it if it is already open. */
	public static void receive(String data, boolean open) {
		Minecraft mc = Minecraft.getInstance();
		if (open) {
			mc.gui.setScreen(new CursesScreen(data, CursesButton.takeReturn()));
		} else if (mc.screen instanceof CursesScreen cs) {
			cs.update(data);
		}
	}

	private void update(String data) {
		String[] parts = data.split("\\|", -1);
		if (parts.length < 4) {
			return;
		}
		try {
			scars = Integer.parseInt(parts[0]);
		} catch (NumberFormatException e) {
			scars = 0;
		}
		atoning = parts[1];
		overworldOn = "1".equals(parts[2]);
		entries.clear();
		for (String part : parts[3].split(";")) {
			String[] kv = part.split(":");
			Penalty pen = kv.length >= 6 ? Penalty.byId(kv[0]) : null;
			if (pen == null) {
				continue;
			}
			try {
				entries.put(pen, new Entry(pen, Integer.parseInt(kv[1]), Integer.parseInt(kv[2]), Integer.parseInt(kv[3]),
						Integer.parseInt(kv[4]), kv[5]));
			} catch (NumberFormatException e) {
				// skip malformed entry
			}
		}
		refreshButton();
	}

	private Entry entry(Penalty p) {
		Entry e = entries.get(p);
		return e != null ? e : new Entry(p, 0, p.max, 0, 1, p.display());
	}

	/** Stacks carried in one set. */
	private int carried(boolean overworld) {
		int n = 0;
		for (Entry e : entries.values()) {
			if (e.pen().overworld == overworld) {
				n += e.stacks();
			}
		}
		return n;
	}

	private Penalty heaviest(boolean overworld) {
		Penalty best = null;
		for (Penalty p : Penalty.values()) {
			if (p.overworld != overworld) {
				continue;
			}
			if (best == null || entry(p).stacks() > entry(best).stacks()) {
				best = p;
			}
		}
		return best;
	}

	private int x0() {
		return (this.width - PW) / 2;
	}

	private int y0() {
		return Math.max(4, (this.height - PH) / 2);
	}

	private int accent() {
		return overworldTab ? Ui.SOUL : Ui.EMBER_DEEP;
	}

	@Override
	protected void init() {
		sockets.clear();
		int x0 = x0(), y0 = y0();
		this.addRenderableWidget(new Tab(x0 + 12, y0 + 22, false));
		this.addRenderableWidget(new Tab(x0 + PW - 12 - 160, y0 + 22, true));
		int[] index = {0, 0};
		for (Penalty p : Penalty.values()) {
			int i = index[p.overworld ? 1 : 0]++;
			Socket s = new Socket(p, x0 + 16 + (i % COLS) * CELL, y0 + 44 + (i / COLS) * CELL);
			sockets.add(this.addRenderableWidget(s));
		}
		atone = this.addRenderableWidget(Button.builder(Component.translatable("gui.extremedimensions.curses.atone"), b -> {
			if (selected != null) {
				ClientPlayNetworking.send(new ModPackets.AtonePayload(selected.id));
			}
		}).pos(x0 + 168, y0 + 148).size(PW - 168 - 14, 18).build());
		refreshButton();
		showTab();
	}

	private void showTab() {
		for (Socket s : sockets) {
			s.visible = s.pen.overworld == overworldTab;
		}
		refreshButton();
	}

	private void refreshButton() {
		if (atone == null) {
			return;
		}
		boolean has = selected != null && entry(selected).stacks() > 0;
		boolean current = selected != null && selected.id.equals(atoning);
		atone.setMessage(Component.translatable(current ? "gui.extremedimensions.curses.atoning" : "gui.extremedimensions.curses.atone"));
		atone.active = has && !current;
		atone.visible = selected != null;
	}

	private void select(Penalty p) {
		selected = p;
		refreshButton();
	}

	@Override
	public void onClose() {
		if (parent != null) {
			Minecraft.getInstance().gui.setScreen(parent);
		} else {
			super.onClose();
		}
	}

	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		long now = System.currentTimeMillis();
		double fade = Ui.ease((now - openAt) / 260.0);
		int accent = accent();
		int x0 = x0(), y0 = y0();
		int cx = this.width / 2;
		g.fill(0, 0, this.width, this.height, Ui.argb((int) (70 * fade), overworldTab ? 0x001818 : 0x200000));
		Ui.panel(g, x0, y0, PW, PH, accent, fade);
		Ui.heading(g, this.font, Ui.tr("gui.extremedimensions.curses.title").toUpperCase(), cx, y0 + 8, accent, fade);

		// The grid's frame and the line between grid and detail
		g.fill(x0 + 158, y0 + 42, x0 + 159, y0 + 168, Ui.argb(fade * 0.5, accent));
		if (overworldTab && !overworldOn) {
			int ly = y0 + 120;
			for (String line : Ui.wrap(this.font, Ui.tr("gui.extremedimensions.curses.overworld_off"), 136)) {
				g.text(this.font, line, x0 + 16, ly, Ui.argb(fade, Ui.WARN));
				ly += 10;
			}
		}
		detail(g, x0 + 168, y0 + 44, PW - 168 - 14, fade, accent);
		footer(g, x0, y0 + 172, fade, accent);
	}

	/** The selected curse: rune and name, stacks, effect, lore and atonement. */
	private void detail(GuiGraphicsExtractor g, int x, int y, int w, double fade, int accent) {
		if (selected == null) {
			return;
		}
		Entry e = entry(selected);
		boolean has = e.stacks() > 0;
		int col = has ? accent : Ui.MUTED;
		Ui.runeSocket(g, selected.id, x, y, col, fade, has ? 0.8 : 0.2);
		g.text(this.font, Ui.tr(selected.nameKey()), x + 24, y + 1, Ui.argb(fade, has ? Ui.PARCHMENT : Ui.TEXT));
		String stacks = Ui.tr("gui.extremedimensions.curses.stacks", e.stacks(), e.max());
		g.text(this.font, stacks, x + 24, y + 11, Ui.argb(fade, e.stacks() >= e.max() && has ? Ui.DANGER : Ui.MUTED));
		pips(g, x + 24 + this.font.width(stacks) + 5, y + 13, e.stacks(), e.max(), accent, fade);

		int ly = y + 26;
		for (String line : new String[] {Ui.tr(selected.lineKey(1), e.value()), Ui.tr(selected.lineKey(2), e.value())}) {
			g.text(this.font, line, x, ly, Ui.argb(fade, Ui.TEXT));
			ly += 10;
		}
		ly += 3;
		List<String> lore = Ui.wrap(this.font, "“" + Ui.tr(selected.loreKey()) + "”", w);
		for (int i = 0; i < lore.size() && i < 3; i++) {
			g.text(this.font, lore.get(i), x, ly, Ui.argb(fade * 0.85, Ui.mix(Ui.SOUL, Ui.TEXT, 0.4)));
			ly += 10;
		}

		// Atonement towards the next stack off
		int ay = y + 84;
		g.text(this.font, Ui.tr("gui.extremedimensions.curses.atonement"), x, ay, Ui.argb(fade, has ? Ui.SOUL : Ui.MUTED));
		String prog = has ? e.progress() + " / " + e.cost() : "-";
		g.text(this.font, prog, x + w - this.font.width(prog), ay, Ui.argb(fade, Ui.MUTED));
		Ui.meter(g, x, ay + 11, w, 6, has ? Math.min(1.0, e.progress() / (double) Math.max(1, e.cost())) : 0, Ui.SOUL, fade, selected.id.equals(atoning));
	}

	/** Scars, and how curses come off in this set. */
	private void footer(GuiGraphicsExtractor g, int x0, int y, double fade, int accent) {
		Ui.divider(g, x0 + 12, y, PW - 24, accent, fade);
		String label = Ui.tr("gui.extremedimensions.curses.scars");
		g.text(this.font, label, x0 + 14, y + 6, Ui.argb(fade, Ui.PARCHMENT));
		int px = x0 + 14 + this.font.width(label) + 6;
		for (int i = 0; i < 5; i++) {
			g.fill(px + i * 7, y + 7, px + i * 7 + 5, y + 12, Ui.argb(fade, i < scars ? Ui.SOUL : Ui.TRACK));
		}
		String drain = Ui.tr("gui.extremedimensions.curses.scar_drain", Math.min(5, scars) * 3);
		g.text(this.font, drain, px + 40, y + 6, Ui.argb(fade, Ui.MUTED));
		String hint = Ui.tr(overworldTab ? "gui.extremedimensions.curses.hint_overworld" : "gui.extremedimensions.curses.hint_beyond");
		Ui.centered(g, this.font, hint, this.width / 2, y + 18, Ui.MUTED, fade);
		Ui.centered(g, this.font, Ui.tr("gui.extremedimensions.curses.hint_mend"), this.width / 2, y + 29, Ui.MUTED, fade * 0.8);
	}

	/** Stacks as pips: filled for each stack carried, hollow up to the maximum, red when full. */
	private static void pips(GuiGraphicsExtractor g, int x, int y, int stacks, int max, int accent, double fade) {
		int col = stacks >= max && stacks > 0 ? Ui.DANGER : Ui.mix(accent, 0xFFFFFF, 0.25);
		for (int i = 0; i < max; i++) {
			g.fill(x + i * 4, y, x + i * 4 + 3, y + 3, Ui.argb(fade, i < stacks ? col : Ui.TRACK));
		}
	}

	/** A curse in the grid: its rune, its pips, selected or not. */
	private final class Socket extends AbstractButton {
		final Penalty pen;

		Socket(Penalty pen, int x, int y) {
			super(x, y, 18, 21, Component.translatable(pen.nameKey()));
			this.pen = pen;
			this.setTooltip(Tooltip.create(Component.translatable(pen.nameKey())));
		}

		@Override
		public void onPress(InputWithModifiers input) {
			select(pen);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			Entry e = entry(pen);
			boolean has = e.stacks() > 0;
			int accent = accent();
			boolean sel = pen == selected;
			int x = this.getX(), y = this.getY();
			double alpha = has ? 1.0 : 0.45;
			Ui.runeSocket(g, pen.id, x, y, has ? accent : Ui.MUTED, alpha, sel || this.isHovered() ? 1.0 : has ? 0.5 : 0.1);
			if (sel) {
				g.outline(x - 1, y - 1, 20, 20, Ui.argb(255, Ui.WARN));
			}
			if (pen.id.equals(atoning) && has) {
				g.fill(x + 14, y + 1, x + 17, y + 4, Ui.argb(255, Ui.SOUL));
			}
			int pw = e.max() * 4 - 1;
			pips(g, x + 9 - pw / 2, y + 19, e.stacks(), e.max(), accent, has ? 1.0 : 0.5);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			out.add(NarratedElementType.TITLE, this.getMessage());
		}
	}

	/** One of the two tabs: the set's name and how many stacks it holds. */
	private final class Tab extends AbstractButton {
		final boolean overworld;

		Tab(int x, int y, boolean overworld) {
			super(x, y, 160, 14, Component.translatable(overworld ? "gui.extremedimensions.curses.tab_overworld" : "gui.extremedimensions.curses.tab_beyond"));
			this.overworld = overworld;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			overworldTab = overworld;
			if (selected == null || selected.overworld != overworld) {
				selected = heaviest(overworld);
			}
			showTab();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			boolean on = overworldTab == overworld;
			int col = overworld ? Ui.SOUL : Ui.EMBER_DEEP;
			int x = this.getX(), y = this.getY();
			Ui.chip(g, x, y, this.getWidth(), this.getHeight(), on ? col : Ui.MUTED, on ? 1.0 : this.isHovered() ? 0.8 : 0.55);
			String label = this.getMessage().getString().toUpperCase();
			int n = carried(overworld);
			String count = String.valueOf(n);
			var font = Minecraft.getInstance().font;
			g.text(font, label, x + 6, y + 3, Ui.argb(on ? 1.0 : 0.7, on ? Ui.PARCHMENT : Ui.TEXT));
			g.text(font, count, x + this.getWidth() - 6 - font.width(count), y + 3, Ui.argb(on ? 1.0 : 0.7, n > 0 ? Ui.DANGER : Ui.MUTED));
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			out.add(NarratedElementType.TITLE, this.getMessage());
		}
	}
}
