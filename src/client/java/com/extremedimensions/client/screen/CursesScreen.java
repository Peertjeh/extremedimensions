package com.extremedimensions.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.extremedimensions.client.inventory.CursesButton;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.Penalty.Group;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.Level;

/**
 * The Curses: everything the Starved took, in four tabs (beyond the wound, the Overworld, everywhere, extreme). The
 * open tab is wide and named, the others narrow (sigil and stacks, the name on hover). Each curse is a
 * rune in a grid with its stacks as pips under it; the selected one shows its effect, its lore and its atonement, and
 * can be chosen as the curse to atone for (and to mend first). The tab whose curses act where the player stands is
 * marked. Scars and how to atone sit at the foot.
 *
 * <p>Beside it, the players standing within seven blocks: a head each (its name on hover) and, per set, one sigil with
 * how many stacks they carry there. Never which curses.
 */
public class CursesScreen extends Screen {
	private static final int PW = 352, PH = 212;
	private static final int COLS = 6, CELL = 22;
	/** Tabs: the narrow ones and the gap between; the open tab takes the rest of the row. */
	private static final int TAB_N = 62, TAB_GAP = 6;
	/** The nearby players panel: wide and narrow widths, gap to the Curses panel, row height (wide), cell height (narrow). */
	private static final int LW = 120, NW = 36, LGAP = 6, ROW = 20, CELL_N = 60;
	/** Distance between the set sigils of one nearby player (wide panel). */
	private static final int SET_STEP = 21;

	/** One curse as the server sent it. */
	private record Entry(Penalty pen, int stacks, int max, int progress, int cost, String value) {}

	/** A player standing close by: stacks per set (beyond, Overworld, everywhere, extreme). */
	private record Nearby(UUID id, String name, int[] sets) {}

	private final Screen parent;
	private final long openAt = System.currentTimeMillis();
	private final Map<Penalty, Entry> entries = new HashMap<>();
	private final List<Nearby> nearby = new ArrayList<>();
	private final List<Socket> sockets = new ArrayList<>();
	private final List<Tab> tabs = new ArrayList<>();
	private int scars;
	private String atoning = "";
	private boolean overworldOn = true;
	private Group tab = Group.BEYOND;
	private Penalty selected;
	private Button atone;
	private long refreshedAt = System.currentTimeMillis();

	/** The Curses screen on display, if any, so a fresh payload can refresh it in place. */
	private static CursesScreen shown;

	public CursesScreen(String data, Screen parent) {
		super(Component.translatable("gui.extremedimensions.curses.title"));
		this.parent = parent;
		update(data);
		// Open on the set that acts here if it holds curses, else on the first set that does, and on its heaviest curse
		Group here = here();
		if (here != null && carried(here) > 0) {
			tab = here;
		} else {
			for (Group g : Group.values()) {
				if (carried(g) > 0) {
					tab = g;
					break;
				}
			}
		}
		selected = heaviest(tab);
	}

	/** A Curses payload arrived: open the screen, or refresh it if it is already open. */
	public static void receive(String data, boolean open) {
		Minecraft mc = Minecraft.getInstance();
		if (open) {
			mc.gui.setScreen(new CursesScreen(data, CursesButton.takeReturn()));
		} else if (shown != null) {
			shown.update(data);
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
		nearby.clear();
		if (parts.length > 4 && !parts[4].isEmpty()) {
			for (String part : parts[4].split(";")) {
				String[] kv = part.split(",");
				if (kv.length < 3) {
					continue;
				}
				try {
					// One count per set; a set the server did not send (an older server) reads as none
					int[] sets = new int[Group.values().length];
					for (int k = 0; k < sets.length && k + 2 < kv.length; k++) {
						sets[k] = Integer.parseInt(kv[k + 2]);
					}
					nearby.add(new Nearby(UUID.fromString(kv[0]), kv[1], sets));
				} catch (IllegalArgumentException e) {
					// skip malformed entry
				}
			}
		}
		refreshButton();
	}

	private Entry entry(Penalty p) {
		Entry e = entries.get(p);
		return e != null ? e : new Entry(p, 0, p.max, 0, 1, p.display());
	}

	/** Stacks carried in one set. */
	private int carried(Group group) {
		int n = 0;
		for (Entry e : entries.values()) {
			if (e.pen().group == group) {
				n += e.stacks();
			}
		}
		return n;
	}

	private Penalty heaviest(Group group) {
		Penalty best = null;
		for (Penalty p : Penalty.values()) {
			if (p.group != group) {
				continue;
			}
			if (best == null || entry(p).stacks() > entry(best).stacks()) {
				best = p;
			}
		}
		return best;
	}

	/** The world set that acts where the player stands: beyond the wound, the Overworld (if switched on), or none. */
	private Group here() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}
		if (mc.level.dimension() == Level.OVERWORLD) {
			return overworldOn ? Group.OVERWORLD : null;
		}
		return Group.BEYOND;
	}

	/** True when a set's curses act where the player stands. Everywhere and Extreme curses always do. */
	private boolean activeHere(Group group) {
		return group == Group.EVERYWHERE || group == Group.EXTREME || group == here();
	}

	public static int color(Group group) {
		return switch (group) {
			case OVERWORLD -> Ui.SOUL;
			case EVERYWHERE -> Ui.VOID;
			case EXTREME -> Ui.BLOOD;
			default -> Ui.EMBER_DEEP;
		};
	}

	private static String tabKey(Group group) {
		return "gui.extremedimensions.curses.tab_" + group.id();
	}

	/** Width of the nearby players panel: wide when there is room, a narrow column on small screens, none if even that does not fit. */
	private int sideW() {
		if (this.width >= PW + LGAP + LW + 8) {
			return LW;
		}
		return this.width >= PW + LGAP + NW + 4 ? NW : 0;
	}

	/** The Curses panel and the nearby players panel are centred together. */
	private int x0() {
		int side = sideW();
		return Math.max(2, (this.width - PW - (side > 0 ? LGAP + side : 0)) / 2);
	}

	private int y0() {
		return Math.max(4, (this.height - PH) / 2);
	}

	private int accent() {
		return color(tab);
	}

	@Override
	protected void init() {
		shown = this;
		sockets.clear();
		tabs.clear();
		int x0 = x0(), y0 = y0();
		Group[] groups = Group.values();
		for (Group group : groups) {
			tabs.add(this.addRenderableWidget(new Tab(x0 + 12, y0 + 22, group)));
		}
		int[] index = new int[groups.length];
		for (Penalty p : Penalty.values()) {
			int i = index[p.group.ordinal()]++;
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
			s.visible = s.pen.group == tab;
		}
		layoutTabs();
		refreshButton();
	}

	/** The open tab is wide enough for its name; the others shrink to sigil and stacks. */
	private void layoutTabs() {
		int row = PW - 24, wide = row - (tabs.size() - 1) * (TAB_N + TAB_GAP);
		int x = x0() + 12;
		for (Tab t : tabs) {
			t.setX(x);
			t.setWidth(t.group == tab ? wide : TAB_N);
			x += t.getWidth() + TAB_GAP;
		}
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
		if (shown == this) {
			shown = null;
		}
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
		// Fresh numbers (and who stands close) every second while open
		if (now - refreshedAt > 1000) {
			refreshedAt = now;
			ClientPlayNetworking.send(new ModPackets.RequestCursesPayload(false));
		}
		double fade = Ui.ease((now - openAt) / 260.0);
		int accent = accent();
		int x0 = x0(), y0 = y0();
		int cx = x0 + PW / 2;
		int tint = switch (tab) {
			case OVERWORLD -> 0x001818;
			case EVERYWHERE -> 0x120020;
			case EXTREME -> 0x24000A;
			default -> 0x200000;
		};
		g.fill(0, 0, this.width, this.height, Ui.argb((int) (70 * fade), tint));
		Ui.panel(g, x0, y0, PW, PH, accent, fade);
		Ui.heading(g, this.font, Ui.tr("gui.extremedimensions.curses.title").toUpperCase(), cx, y0 + 8, accent, fade);

		// The line between grid and detail
		g.fill(x0 + 158, y0 + 42, x0 + 159, y0 + 168, Ui.argb(fade * 0.5, accent));
		if (tab == Group.OVERWORLD && !overworldOn) {
			int ly = y0 + 120;
			for (String line : Ui.wrap(this.font, Ui.tr("gui.extremedimensions.curses.overworld_off"), 136)) {
				g.text(this.font, line, x0 + 16, ly, Ui.argb(fade, Ui.WARN));
				ly += 10;
			}
		}
		detail(g, x0 + 168, y0 + 44, PW - 168 - 14, fade, accent);
		footer(g, x0, y0 + 172, fade, accent);
		nearbyPanel(g, x0, y0, mouseX, mouseY, fade);
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
		String hint = Ui.tr("gui.extremedimensions.curses.hint_" + tab.id());
		Ui.centered(g, this.font, hint, x0 + PW / 2, y + 18, Ui.MUTED, fade);
		Ui.centered(g, this.font, Ui.tr("gui.extremedimensions.curses.hint_mend"), x0 + PW / 2, y + 29, Ui.MUTED, fade * 0.8);
	}

	/**
	 * Who stands within seven blocks: a head each and one sigil per set with the stacks carried there, nearest first,
	 * never taller than the Curses panel. Hover a head for the name, a sigil for its set. On small screens the panel is a
	 * narrow column: the head with its sigils under it.
	 */
	private void nearbyPanel(GuiGraphicsExtractor g, int x0, int y0, int mouseX, int mouseY, double fade) {
		int side = sideW();
		if (side == 0) {
			return;
		}
		int lx = x0 + PW + LGAP;
		boolean wide = side == LW;
		int perCell = wide ? ROW : CELL_N;
		int top = wide ? 30 : 8;
		int rows = Math.min(nearby.size(), (PH - top - 4) / perCell);
		boolean more = nearby.size() > rows;
		if (more) {
			rows = Math.max(0, rows - 1);
		}
		int lh = nearby.isEmpty() ? (wide ? 56 : 30) : Math.min(PH, top + (rows + (more ? 1 : 0)) * perCell + 4);
		Ui.panel(g, lx, y0, side, lh, Ui.MUTED, fade);
		if (wide) {
			Ui.centered(g, this.font, Ui.tr("gui.extremedimensions.curses.nearby").toUpperCase(), lx + side / 2, y0 + 9, Ui.PARCHMENT, fade);
			Ui.divider(g, lx + 10, y0 + 22, side - 20, Ui.MUTED, fade);
		}
		if (nearby.isEmpty()) {
			if (wide) {
				int ly = y0 + 30;
				for (String line : Ui.wrap(this.font, Ui.tr("gui.extremedimensions.curses.nearby_none"), side - 16)) {
					Ui.centered(g, this.font, line, lx + side / 2, ly, Ui.MUTED, fade);
					ly += 10;
				}
			} else {
				Ui.centered(g, this.font, "-", lx + side / 2, y0 + 11, Ui.MUTED, fade);
				if (mouseX >= lx && mouseX < lx + side && mouseY >= y0 && mouseY < y0 + lh) {
					g.setTooltipForNextFrame(this.font, Component.translatable("gui.extremedimensions.curses.nearby_none"), mouseX, mouseY);
				}
			}
			return;
		}
		Component tip = null;
		Group[] groups = Group.values();
		for (int r = 0; r < rows; r++) {
			Nearby n = nearby.get(r);
			int ry = y0 + top + r * perCell;
			int fx = wide ? lx + 8 : lx + (side - 16) / 2;
			face(g, n, fx, ry, fade);
			if (mouseX >= fx && mouseX < fx + 16 && mouseY >= ry && mouseY < ry + 16) {
				tip = Component.literal(n.name());
			}
			for (int k = 0; k < groups.length; k++) {
				int sx = wide ? lx + 30 + k * SET_STEP : lx + 6, sy = wide ? ry + 4 : ry + 20 + k * 9;
				int count = n.sets()[k];
				sigil(g, groups[k], sx, sy, count > 0 ? color(groups[k]) : Ui.MUTED, fade * (count > 0 ? 1.0 : 0.6));
				String c = String.valueOf(count);
				g.text(this.font, c, sx + 9, sy, Ui.argb(fade, count > 0 ? Ui.PARCHMENT : Ui.MUTED));
				if (mouseX >= sx - 1 && mouseX < sx + 9 + this.font.width(c) && mouseY >= sy - 1 && mouseY < sy + 9) {
					tip = Component.translatable("gui.extremedimensions.curses.set_" + groups[k].id());
				}
			}
		}
		if (more) {
			int ry = y0 + top + rows * perCell;
			String rest = wide ? Ui.tr("gui.extremedimensions.status.more", nearby.size() - rows) : "+" + (nearby.size() - rows);
			Ui.centered(g, this.font, rest, lx + side / 2, ry + 4, Ui.MUTED, fade);
		}
		if (tip != null) {
			g.setTooltipForNextFrame(this.font, tip, mouseX, mouseY);
		}
	}

	/** A player's face from their skin (the tab list, else the entity in view); a plain tile with an initial otherwise. */
	private static void face(GuiGraphicsExtractor g, Nearby n, int x, int y, double fade) {
		Minecraft mc = Minecraft.getInstance();
		PlayerSkin skin = null;
		if (mc.getConnection() != null) {
			PlayerInfo info = mc.getConnection().getPlayerInfo(n.id());
			if (info != null) {
				skin = info.getSkin();
			}
		}
		if (skin == null && mc.level != null && mc.level.getPlayerByUUID(n.id()) instanceof AbstractClientPlayer player) {
			skin = player.getSkin();
		}
		g.fill(x - 1, y - 1, x + 17, y + 17, Ui.argb(fade, Ui.SHADOW));
		if (skin != null) {
			PlayerFaceExtractor.extractRenderState(g, skin, x, y, 16);
		} else {
			g.fill(x, y, x + 16, y + 16, Ui.argb(fade, Ui.INK_RAISED));
			String initial = n.name().isEmpty() ? "?" : n.name().substring(0, 1).toUpperCase();
			g.text(mc.font, initial, x + 8 - mc.font.width(initial) / 2, y + 4, Ui.argb(fade, Ui.TEXT));
		}
	}

	/** Each set's sigil, 7x7: a flame beyond the wound, a tree for the Overworld, an eye for everywhere, a skull for extreme. */
	private static final String[][] SIGILS = {
		{"...#...", "...##..", "..###..", ".##.##.", ".#...#.", ".#...#.", "..###.."},
		{"..###..", ".#####.", "#######", ".#####.", "...#...", "...#...", ".#####."},
		{"..###..", ".#...#.", "#..#..#", "#.###.#", "#..#..#", ".#...#.", "..###.."},
		{".#####.", "#######", "#..#..#", "#######", ".##.##.", "..###..", "..#.#.."},
	};

	public static void sigil(GuiGraphicsExtractor g, Group group, int x, int y, int rgb, double alpha) {
		String[] rows = SIGILS[group.ordinal()];
		int c = Ui.argb(alpha, rgb);
		for (int r = 0; r < rows.length; r++) {
			for (int k = 0; k < rows[r].length(); k++) {
				if (rows[r].charAt(k) == '#') {
					g.fill(x + k, y + r, x + k + 1, y + r + 1, c);
				}
			}
		}
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

	/**
	 * One of the four tabs: the set's sigil and stacks (and its name when open), and a pulse when its curses act where
	 * the player stands.
	 */
	private final class Tab extends AbstractButton {
		final Group group;

		Tab(int x, int y, Group group) {
			super(x, y, TAB_N, 14, Component.translatable(tabKey(group)));
			this.group = group;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			tab = group;
			if (selected == null || selected.group != group) {
				selected = heaviest(group);
			}
			showTab();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			boolean on = tab == group;
			int col = color(group);
			int x = this.getX(), y = this.getY();
			Ui.chip(g, x, y, this.getWidth(), this.getHeight(), on ? col : Ui.MUTED, on ? 1.0 : this.isHovered() ? 0.8 : 0.55);
			var font = Minecraft.getInstance().font;
			sigil(g, group, x + 5, y + 4, on ? col : Ui.MUTED, on ? 1.0 : 0.7);
			if (on) {
				g.text(font, this.getMessage().getString().toUpperCase(), x + 16, y + 3, Ui.argb(1.0, Ui.PARCHMENT));
			}
			int n = carried(group);
			String count = String.valueOf(n);
			int cxr = x + this.getWidth() - 5 - font.width(count);
			g.text(font, count, cxr, y + 3, Ui.argb(on ? 1.0 : 0.7, n > 0 ? Ui.DANGER : Ui.MUTED));
			if (activeHere(group)) {
				// A breathing mark: these curses act where the player stands
				double beat = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 300.0);
				int mx = cxr - 6;
				g.fill(mx, y + 5, mx + 3, y + 8, Ui.argb(0.45 + 0.55 * beat, Ui.mix(col, 0xFFFFFF, 0.35)));
			}
			if (this.isHovered()) {
				Component where = Component.translatable(activeHere(group)
						? "gui.extremedimensions.curses.active_here" : "gui.extremedimensions.curses.inactive_here");
				g.setTooltipForNextFrame(font, on ? where : Component.empty().append(this.getMessage()).append(" · ").append(where), mouseX, mouseY);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			out.add(NarratedElementType.TITLE, this.getMessage());
		}
	}
}
