package com.extremedimensions.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.config.Config;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.Penalty.Group;

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
 * The Pact: curses taken on willingly, for what a safe return brings. One tab per set that acts beyond the wound;
 * each curse shows the stacks carried (dim) and the stacks sworn (gold) up to its maximum. The heat and what it
 * brings are shown at the foot. A pact is sworn at home, and holds on every expedition until it is changed.
 */
public class PactScreen extends Screen {
	private static final int PW = 372, PH = 212;
	private static final int COLS = 6, CELL = 22;
	private static final Group[] SETS = {Group.BEYOND, Group.EVERYWHERE, Group.EXTREME};

	private final Screen parent;
	private final long openAt = System.currentTimeMillis();
	/** Stacks carried and maximum per curse, as the Curses screen had them. */
	private final Map<Penalty, int[]> carried;
	private final Map<Penalty, Integer> sworn = new LinkedHashMap<>();
	private final boolean unlocked;
	private final boolean away;
	private final List<Socket> sockets = new ArrayList<>();
	private Group tab = Group.BEYOND;
	private Penalty selected;
	private Button plus, minus, seal;

	/** pact = "unlocked;away;id:n,id:n"; carried = curse -> {stacks, max}. */
	public PactScreen(Screen parent, String pact, Map<Penalty, int[]> carried) {
		super(Component.translatable("gui.extremedimensions.pact.title"));
		this.parent = parent;
		this.carried = carried;
		String[] parts = (pact == null ? "" : pact).split(";", -1);
		unlocked = parts.length > 0 && "1".equals(parts[0]);
		away = parts.length > 1 && "1".equals(parts[1]);
		if (parts.length > 2 && !parts[2].isEmpty()) {
			for (String e : parts[2].split(",")) {
				String[] kv = e.split(":");
				Penalty p = kv.length == 2 ? Penalty.byId(kv[0]) : null;
				if (p != null) {
					try {
						sworn.put(p, Integer.parseInt(kv[1]));
					} catch (NumberFormatException ex) {
						// skip a malformed entry
					}
				}
			}
		}
	}

	private int x0() {
		return Math.max(2, (this.width - PW) / 2);
	}

	private int y0() {
		return Math.max(4, (this.height - PH) / 2);
	}

	private int own(Penalty p) {
		int[] c = carried.get(p);
		return c == null ? 0 : c[0];
	}

	private int max(Penalty p) {
		int[] c = carried.get(p);
		return c == null ? p.max : c[1];
	}

	private int swornOf(Penalty p) {
		return sworn.getOrDefault(p, 0);
	}

	private int heat() {
		int h = 0;
		for (Map.Entry<Penalty, Integer> e : sworn.entrySet()) {
			h += e.getValue() * (e.getKey().extreme ? 2 : 1);
		}
		return h;
	}

	private boolean editable() {
		return unlocked && !away;
	}

	@Override
	protected void init() {
		sockets.clear();
		int x0 = x0(), y0 = y0();
		int tw = (PW - 24 - 2 * 6) / 3;
		for (int i = 0; i < SETS.length; i++) {
			this.addRenderableWidget(new SetTab(x0 + 12 + i * (tw + 6), y0 + 22, tw, SETS[i]));
		}
		int[] index = new int[Group.values().length];
		for (Penalty p : Penalty.values()) {
			if (!com.extremedimensions.pact.PactService.allowed(p)) {
				continue;
			}
			int i = index[p.group.ordinal()]++;
			sockets.add(this.addRenderableWidget(new Socket(p, x0 + 16 + (i % COLS) * CELL, y0 + 44 + (i / COLS) * CELL)));
		}
		minus = this.addRenderableWidget(Button.builder(Component.literal("-"), b -> change(-1)).pos(x0 + 168, y0 + 104).size(20, 18).build());
		plus = this.addRenderableWidget(Button.builder(Component.literal("+"), b -> change(1)).pos(x0 + 192, y0 + 104).size(20, 18).build());
		seal = this.addRenderableWidget(Button.builder(Component.translatable("gui.extremedimensions.pact.seal"), b -> send())
				.pos(x0 + 14, y0 + PH - 22).size(110, 18).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.extremedimensions.pact.clear"), b -> {
			sworn.clear();
			refresh();
		}).pos(x0 + 130, y0 + PH - 22).size(90, 18).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).pos(x0 + PW - 14 - 100, y0 + PH - 22).size(100, 18).build());
		showTab();
	}

	private void showTab() {
		for (Socket s : sockets) {
			s.visible = s.pen.group == tab;
		}
		if (selected == null || selected.group != tab) {
			selected = null;
			for (Socket s : sockets) {
				if (s.visible) {
					selected = s.pen;
					break;
				}
			}
		}
		refresh();
	}

	private void change(int by) {
		if (selected == null || !editable()) {
			return;
		}
		int now = swornOf(selected);
		int next = Math.max(0, Math.min(max(selected) - own(selected), now + by));
		if (by > 0 && heat() + (selected.extreme ? 2 : 1) > Config.PACT_MAX_HEAT) {
			return;
		}
		if (next == 0) {
			sworn.remove(selected);
		} else {
			sworn.put(selected, next);
		}
		refresh();
	}

	private void refresh() {
		if (plus == null) {
			return;
		}
		boolean can = editable() && selected != null;
		plus.active = can && swornOf(selected) < max(selected) - own(selected)
				&& heat() + (selected.extreme ? 2 : 1) <= Config.PACT_MAX_HEAT;
		minus.active = can && swornOf(selected) > 0;
		seal.active = editable();
	}

	private void send() {
		StringBuilder out = new StringBuilder();
		for (Map.Entry<Penalty, Integer> e : sworn.entrySet()) {
			if (out.length() > 0) {
				out.append(',');
			}
			out.append(e.getKey().id).append(':').append(e.getValue());
		}
		ClientPlayNetworking.send(new ModPackets.SwearPactPayload(out.toString()));
		onClose();
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(parent);
	}

	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		double fade = Ui.ease((System.currentTimeMillis() - openAt) / 260.0);
		int x0 = x0(), y0 = y0();
		int accent = 0xE0A040;
		g.fill(0, 0, this.width, this.height, Ui.argb((int) (80 * fade), 0x180C00));
		Ui.panel(g, x0, y0, PW, PH, accent, fade);
		Ui.heading(g, this.font, Ui.tr("gui.extremedimensions.pact.title").toUpperCase(), x0 + PW / 2, y0 + 8, accent, fade);
		String heat = Ui.tr("gui.extremedimensions.pact.heat", heat(), Config.PACT_MAX_HEAT);
		g.text(this.font, heat, x0 + PW - 14 - this.font.width(heat), y0 + 8, Ui.argb(fade, heat() > 0 ? 0xFFC060 : Ui.MUTED));
		g.fill(x0 + 158, y0 + 42, x0 + 159, y0 + 164, Ui.argb(fade * 0.5, accent));
		if (selected != null) {
			int x = x0 + 168, y = y0 + 44, w = PW - 168 - 14;
			Ui.runeSocket(g, selected.id, x, y, CursesScreen.color(selected.group), fade, 0.8);
			g.text(this.font, Ui.tr(selected.nameKey()), x + 24, y + 1, Ui.argb(fade, Ui.PARCHMENT));
			g.text(this.font, Ui.tr("gui.extremedimensions.pact.carried", own(selected), max(selected)), x + 24, y + 11, Ui.argb(fade, Ui.MUTED));
			int ly = y + 26;
			for (String line : new String[] {Ui.tr(selected.lineKey(1), selected.display()), Ui.tr(selected.lineKey(2), selected.display())}) {
				g.text(this.font, line, x, ly, Ui.argb(fade, Ui.TEXT));
				ly += 10;
			}
			String sw = Ui.tr("gui.extremedimensions.pact.sworn_n", swornOf(selected), selected.extreme ? 2 : 1);
			g.text(this.font, sw, x + 50, y + 65, Ui.argb(fade, swornOf(selected) > 0 ? 0xFFC060 : Ui.TEXT));
			pips(g, x, y + 82, own(selected), swornOf(selected), max(selected), fade);
			if (!unlocked || away) {
				int ny = y + 92;
				for (String line : Ui.wrap(this.font, Ui.tr(unlocked ? "gui.extremedimensions.pact.away" : "gui.extremedimensions.pact.locked"), w)) {
					g.text(this.font, line, x, ny, Ui.argb(fade, Ui.WARN));
					ny += 10;
				}
			}
		}
		// What a safe return with this pact brings
		Ui.divider(g, x0 + 12, y0 + 167, PW - 24, accent, fade);
		int h = heat();
		double frag = h / Math.max(0.5, Config.PACT_HEAT_PER_FRAGMENT);
		double relic = h >= Config.PACT_RELIC_MIN_HEAT ? Math.min(Config.PACT_RELIC_MAX_CHANCE, h * Config.PACT_RELIC_CHANCE_PER_HEAT) : 0;
		String reward = h == 0 ? Ui.tr("gui.extremedimensions.pact.none")
				: Ui.tr("gui.extremedimensions.pact.reward", h * Config.PACT_ATONE_PER_HEAT, Penalty.display(Math.round(frag * 10) / 10.0),
						Penalty.display(Math.round(relic * 100)));
		Ui.centered(g, this.font, Ui.ellipsize(this.font, reward, PW - 24), x0 + PW / 2, y0 + 171, h == 0 ? Ui.MUTED : Ui.TEXT, fade);
		// ...and on what terms: active time beyond the wound, and no death or Collapse on the way
		if (h > 0) {
			String terms = Ui.tr("gui.extremedimensions.pact.terms", Penalty.display(Math.round(Config.PACT_FULL_SECONDS / 6.0) / 10.0));
			Ui.centered(g, this.font, Ui.ellipsize(this.font, terms, PW - 24), x0 + PW / 2, y0 + 180, Ui.MUTED, fade);
		}
	}

	/** Carried stacks dim, sworn stacks gold, the rest hollow up to the maximum. */
	private static void pips(GuiGraphicsExtractor g, int x, int y, int own, int sworn, int max, double fade) {
		for (int i = 0; i < max; i++) {
			int col = i < own ? Ui.MUTED : i < own + sworn ? 0xFFC060 : Ui.TRACK;
			g.fill(x + i * 6, y, x + i * 6 + 4, y + 4, Ui.argb(fade, col));
		}
	}

	private final class Socket extends AbstractButton {
		final Penalty pen;

		Socket(Penalty pen, int x, int y) {
			super(x, y, 18, 21, Component.translatable(pen.nameKey()));
			this.pen = pen;
			this.setTooltip(Tooltip.create(Component.translatable(pen.nameKey())));
		}

		@Override
		public void onPress(InputWithModifiers input) {
			selected = pen;
			refresh();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int x = this.getX(), y = this.getY();
			int s = swornOf(pen);
			int col = s > 0 ? 0xFFC060 : own(pen) > 0 ? CursesScreen.color(pen.group) : Ui.MUTED;
			Ui.runeSocket(g, pen.id, x, y, col, s > 0 || own(pen) > 0 ? 1.0 : 0.5, pen == selected || this.isHovered() ? 1.0 : 0.3);
			if (pen == selected) {
				g.outline(x - 1, y - 1, 20, 20, Ui.argb(255, Ui.WARN));
			}
			int max = max(pen), pw = max * 4 - 1;
			for (int i = 0; i < max; i++) {
				int c = i < own(pen) ? Ui.MUTED : i < own(pen) + s ? 0xFFC060 : Ui.TRACK;
				g.fill(x + 9 - pw / 2 + i * 4, y + 19, x + 9 - pw / 2 + i * 4 + 3, y + 22, Ui.argb(255, c));
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			out.add(NarratedElementType.TITLE, this.getMessage());
		}
	}

	private final class SetTab extends AbstractButton {
		final Group group;

		SetTab(int x, int y, int w, Group group) {
			super(x, y, w, 14, Component.translatable("gui.extremedimensions.curses.tab_" + group.id()));
			this.group = group;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			tab = group;
			showTab();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			boolean on = tab == group;
			int col = CursesScreen.color(group);
			int x = this.getX(), y = this.getY();
			Ui.chip(g, x, y, this.getWidth(), this.getHeight(), on ? col : Ui.MUTED, on ? 1.0 : this.isHovered() ? 0.8 : 0.55);
			var font = Minecraft.getInstance().font;
			CursesScreen.sigil(g, group, x + 5, y + 4, on ? col : Ui.MUTED, on ? 1.0 : 0.7);
			String label = this.getMessage().getString().toUpperCase();
			g.text(font, label, x + 16, y + 3, Ui.argb(on ? 1.0 : 0.7, on ? Ui.PARCHMENT : Ui.TEXT));
			int n = 0;
			for (Map.Entry<Penalty, Integer> e : sworn.entrySet()) {
				if (e.getKey().group == group) {
					n += e.getValue();
				}
			}
			String count = String.valueOf(n);
			g.text(font, count, x + this.getWidth() - 5 - font.width(count), y + 3, Ui.argb(on ? 1.0 : 0.7, n > 0 ? 0xFFC060 : Ui.MUTED));
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			out.add(NarratedElementType.TITLE, this.getMessage());
		}
	}
}
