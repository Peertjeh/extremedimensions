package com.extremedimensions.client.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.Penalty.Group;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * The operators' Players screen (3.27). On the left every player the world knows, online first, the operator among
 * them, with a search box over the list; on the right the player picked: where they are and how they fare, their
 * newest curse, and their curses set by set, each with a minus to lift a stack and a plus to lay one on. A stack lifted
 * here comes off plainly, without the scar an atonement leaves. The server checks every request (see AdminService).
 */
public class AdminScreen extends Screen {
	private static final int PW = 404, PH = 248;
	/** The player list: its width and row height, and how many rows show. */
	private static final int LIST_W = 118, ROW = 12, LIST_ROWS = 14;
	/** The curse list: row height and how many rows show. */
	private static final int CROW = 12, CURSE_ROWS = 8;
	/** Where the right-hand column starts (from the panel's left) and how wide it is. */
	private static final int RX = 142, RW = PW - RX - 10;
	/** The set tabs: the narrow ones and the gap between; the open tab takes the rest. */
	private static final int TAB_N = 40, TAB_GAP = 4;
	/** Top of the tabs and of the curse list (from the panel's top). */
	private static final int TABS_Y = 104, CURSES_Y = 122;

	private static AdminScreen shown;
	/** Set when the screen was asked for from another screen, so Done goes back there. */
	private static boolean pendingOpen;
	private static Screen returnTo;

	private record Player(String id, String name, boolean on, int stacks) {}

	private record Curse(Penalty pen, int stacks, int max, int progress, int cost) {}

	private final Screen parent;
	private final long openAt = System.currentTimeMillis();
	private String you = "";
	private final List<Player> players = new ArrayList<>();
	private final List<Player> filtered = new ArrayList<>();
	private final Map<Penalty, Curse> curses = new EnumMap<>(Penalty.class);
	private final List<Penalty> rows = new ArrayList<>();
	private JsonObject sel = new JsonObject();
	private String selId = "";
	private Group tab = Group.BEYOND;
	private boolean carriedOnly = false;
	private int listScroll, curseScroll;
	private long refreshedAt = System.currentTimeMillis(), clearArmedAt;
	private EditBox search;
	private Button clear, carriedButton;
	private final List<Tab> tabs = new ArrayList<>();

	/** The Players screen on show, or null. */
	public static AdminScreen shown() {
		return shown;
	}

	/** Asks the server for the screen; parent is where Done goes back to. */
	public static void request(Screen parent) {
		pendingOpen = true;
		returnTo = parent;
		ClientPlayNetworking.send(new ModPackets.RequestAdminPayload("", true));
	}

	/** The server's answer: opens the screen (asked for, or by /tether players) or refreshes the one on show. */
	public static void receive(String data, boolean open) {
		Minecraft mc = Minecraft.getInstance();
		if (open) {
			Screen back = pendingOpen ? returnTo : null;
			pendingOpen = false;
			returnTo = null;
			mc.gui.setScreen(new AdminScreen(data, back));
		} else if (shown != null) {
			shown.update(data);
		}
	}

	public AdminScreen(String data, Screen parent) {
		super(Component.translatable("gui.extremedimensions.admin.title"));
		this.parent = parent;
		update(data);
	}

	private static int num(JsonObject o, String key) {
		JsonElement e = o.get(key);
		try {
			return e == null || e.isJsonNull() ? 0 : e.getAsInt();
		} catch (RuntimeException ex) {
			return 0;
		}
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static boolean bool(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && !e.isJsonNull() && e.getAsBoolean();
	}

	private void update(String data) {
		JsonObject root;
		try {
			root = JsonParser.parseString(data == null ? "{}" : data).getAsJsonObject();
		} catch (RuntimeException e) {
			return;
		}
		you = str(root, "you");
		players.clear();
		if (root.get("players") instanceof JsonArray list) {
			for (JsonElement e : list) {
				if (e instanceof JsonObject o) {
					players.add(new Player(str(o, "id"), str(o, "name"), bool(o, "on"), num(o, "n")));
				}
			}
		}
		String before = selId;
		sel = root.get("sel") instanceof JsonObject o ? o : new JsonObject();
		selId = str(sel, "id");
		curses.clear();
		if (sel.get("curses") instanceof JsonObject all) {
			for (Map.Entry<String, JsonElement> e : all.entrySet()) {
				Penalty pen = Penalty.byId(e.getKey());
				if (pen != null && e.getValue() instanceof JsonArray a && a.size() >= 4) {
					try {
						curses.put(pen, new Curse(pen, a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt()));
					} catch (RuntimeException ex) {
						// skip malformed entry
					}
				}
			}
		}
		if (!selId.equals(before)) {
			// A player newly picked: open on the first set where they carry something, at the top
			curseScroll = 0;
			clearArmedAt = 0;
			tab = Group.BEYOND;
			for (Group g : Group.values()) {
				if (carried(g) > 0) {
					tab = g;
					break;
				}
			}
		}
		filter();
		layout();
	}

	/** Stacks the player picked carries in one set. */
	private int carried(Group g) {
		int n = 0;
		for (Curse c : curses.values()) {
			if (c.pen().group == g) {
				n += c.stacks();
			}
		}
		return n;
	}

	private int carriedAll() {
		int n = 0;
		for (Curse c : curses.values()) {
			n += c.stacks();
		}
		return n;
	}

	private Curse curse(Penalty p) {
		Curse c = curses.get(p);
		return c != null ? c : new Curse(p, 0, p.max, 0, 1);
	}

	/** The players the search leaves, and the curses of the open set (or only those carried). */
	private void filter() {
		String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		filtered.clear();
		for (Player p : players) {
			if (q.isEmpty() || p.name().toLowerCase(Locale.ROOT).contains(q)) {
				filtered.add(p);
			}
		}
		listScroll = Math.max(0, Math.min(listScroll, filtered.size() - LIST_ROWS));
		rows.clear();
		// The set's own curses first, then (under Extreme) the Abyssal ones
		for (int pass = 0; pass < 2; pass++) {
			for (Penalty p : Penalty.values()) {
				if (p.group == tab && p.abyssal == (pass == 1) && (!carriedOnly || curse(p).stacks() > 0)) {
					rows.add(p);
				}
			}
		}
		curseScroll = Math.max(0, Math.min(curseScroll, rows.size() - CURSE_ROWS));
	}

	private int x0() {
		return Math.max(2, (this.width - PW) / 2);
	}

	private int y0() {
		return Math.max(4, (this.height - PH) / 2);
	}

	@Override
	protected void init() {
		shown = this;
		int x0 = x0(), y0 = y0();
		String was = search == null ? "" : search.getValue();
		search = new EditBox(this.font, x0 + 10, y0 + 22, LIST_W, 14, Component.translatable("gui.extremedimensions.admin.search"));
		search.setHint(Component.translatable("gui.extremedimensions.admin.search"));
		search.setMaxLength(32);
		search.setValue(was);
		search.setResponder(text -> {
			listScroll = 0;
			filter();
			layout();
		});
		this.addRenderableWidget(search);
		for (int i = 0; i < LIST_ROWS; i++) {
			this.addRenderableWidget(new PlayerSlot(i, x0 + 10, y0 + 40 + i * ROW));
		}
		tabs.clear();
		for (Group g : Group.values()) {
			tabs.add(this.addRenderableWidget(new Tab(g, x0 + RX, y0 + TABS_Y)));
		}
		for (int i = 0; i < CURSE_ROWS; i++) {
			int y = y0 + CURSES_Y + i * CROW;
			this.addRenderableWidget(new CurseSlot(i, x0 + RX, y, RW - 6 - 26));
			this.addRenderableWidget(new StackButton(i, false, x0 + RX + RW - 6 - 24, y));
			this.addRenderableWidget(new StackButton(i, true, x0 + RX + RW - 6 - 12, y));
		}
		clear = this.addRenderableWidget(Button.builder(Component.empty(), b -> clearAll()).pos(x0 + RX, y0 + PH - 24).size(124, 18).build());
		carriedButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
			carriedOnly = !carriedOnly;
			curseScroll = 0;
			filter();
			layout();
		}).pos(x0 + RX + 128, y0 + PH - 24).size(RW - 128, 18).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).pos(x0 + 10, y0 + PH - 24).size(LIST_W, 18).build());
		filter();
		layout();
	}

	/** Tabs, footer buttons and their words, after any change. */
	private void layout() {
		if (clear == null) {
			return;
		}
		int wide = RW - (tabs.size() - 1) * (TAB_N + TAB_GAP), x = x0() + RX;
		for (Tab t : tabs) {
			t.setX(x);
			t.setWidth(t.group == tab ? wide : TAB_N);
			x += t.getWidth() + TAB_GAP;
		}
		boolean armed = System.currentTimeMillis() - clearArmedAt < 4000;
		clear.setMessage(Component.translatable(armed ? "gui.extremedimensions.admin.clear_confirm" : "gui.extremedimensions.admin.clear"));
		clear.active = carriedAll() > 0;
		clear.setTooltip(Tooltip.create(Component.translatable("gui.extremedimensions.admin.clear_tip", str(sel, "name"))));
		carriedButton.setMessage(Component.translatable(carriedOnly ? "gui.extremedimensions.admin.show_carried" : "gui.extremedimensions.admin.show_all"));
	}

	/** Lifts every curse of the player picked: the first press arms the button, a second within four seconds does it. */
	private void clearAll() {
		long now = System.currentTimeMillis();
		if (now - clearArmedAt > 4000) {
			clearArmedAt = now;
		} else {
			clearArmedAt = 0;
			ClientPlayNetworking.send(new ModPackets.AdminActionPayload(selId, "clear", ""));
		}
		layout();
	}

	private void pick(String id) {
		if (id.equals(selId)) {
			return;
		}
		ClientPlayNetworking.send(new ModPackets.RequestAdminPayload(id, false));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			int step = (int) -Math.signum(scrollY);
			if (mouseX < x0() + RX - 4) {
				listScroll = Math.max(0, Math.min(listScroll + step * 2, filtered.size() - LIST_ROWS));
			} else if (mouseY >= y0() + CURSES_Y - 2) {
				curseScroll = Math.max(0, Math.min(curseScroll + step, rows.size() - CURSE_ROWS));
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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

	private static String worldName(String id) {
		if (id.isEmpty()) {
			return "";
		}
		String key = "gui.extremedimensions.admin.world." + id;
		String t = Ui.tr(key);
		return t.equals(key) ? id : t;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		long now = System.currentTimeMillis();
		// Fresh numbers every two seconds while open (who came online, what the curses did meanwhile)
		if (now - refreshedAt > 2000) {
			refreshedAt = now;
			ClientPlayNetworking.send(new ModPackets.RequestAdminPayload(selId, false));
		}
		if (clearArmedAt != 0 && now - clearArmedAt > 4000) {
			clearArmedAt = 0;
			layout();
		}
		double fade = Ui.ease((now - openAt) / 260.0);
		int x0 = x0(), y0 = y0();
		int accent = Ui.EMBER;
		g.fill(0, 0, this.width, this.height, Ui.argb((int) (70 * fade), 0x140A00));
		Ui.panel(g, x0, y0, PW, PH, accent, fade);
		Ui.heading(g, this.font, Ui.tr("gui.extremedimensions.admin.title").toUpperCase(), x0 + PW / 2, y0 + 8, accent, fade);
		String count = Ui.tr("gui.extremedimensions.admin.count", players.size(), (int) players.stream().filter(Player::on).count());
		g.text(this.font, count, x0 + PW - 10 - this.font.width(count), y0 + 8, Ui.argb(fade, Ui.MUTED));
		// The line between the list and the player
		g.fill(x0 + RX - 7, y0 + 22, x0 + RX - 6, y0 + PH - 30, Ui.argb(fade * 0.5, accent));
		if (filtered.isEmpty()) {
			int ly = y0 + 44;
			for (String line : Ui.wrap(this.font, Ui.tr("gui.extremedimensions.admin.none_found"), LIST_W)) {
				g.text(this.font, line, x0 + 12, ly, Ui.argb(fade * 0.8, Ui.MUTED));
				ly += 10;
			}
		}
		scrollbar(g, x0 + 10 + LIST_W + 1, y0 + 40, LIST_ROWS * ROW, listScroll, filtered.size(), LIST_ROWS, fade);
		overview(g, x0 + RX, y0 + 22, RW, fade);
		scrollbar(g, x0 + RX + RW - 3, y0 + CURSES_Y, CURSE_ROWS * CROW, curseScroll, rows.size(), CURSE_ROWS, fade);
		if (rows.isEmpty()) {
			g.text(this.font, Ui.tr("gui.extremedimensions.admin.set_empty"), x0 + RX + 2, y0 + CURSES_Y + 2, Ui.argb(fade * 0.8, Ui.MUTED));
		}
	}

	private void scrollbar(GuiGraphicsExtractor g, int x, int y, int h, int at, int total, int shown, double fade) {
		if (total <= shown) {
			return;
		}
		g.fill(x, y, x + 2, y + h, Ui.argb(fade * 0.5, Ui.TRACK));
		int bh = Math.max(8, h * shown / total), by = y + (h - bh) * at / Math.max(1, total - shown);
		g.fill(x, by, x + 2, by + bh, Ui.argb(fade, Ui.MUTED));
	}

	/** The player picked: name and where they are, how they fare, and their newest curse. */
	private void overview(GuiGraphicsExtractor g, int x, int y, int w, double fade) {
		if (selId.isEmpty()) {
			return;
		}
		boolean on = bool(sel, "on");
		g.fill(x, y + 2, x + 5, y + 7, Ui.argb(fade, on ? Ui.OK : Ui.MUTED));
		String name = str(sel, "name") + (selId.equals(you) ? " " + Ui.tr("gui.extremedimensions.admin.you") : "");
		g.text(this.font, Ui.ellipsize(this.font, name, w - 10), x + 9, y, Ui.argb(fade, Ui.PARCHMENT));
		String status;
		if (on) {
			status = Ui.tr("gui.extremedimensions.admin.online", worldName(str(sel, "world")));
			if (bool(sel, "run")) {
				status += " · " + Ui.tr("gui.extremedimensions.admin.on_run");
			}
			status += " · " + Ui.tr("gui.extremedimensions.admin.health", num(sel, "health"));
		} else {
			status = Ui.tr("gui.extremedimensions.admin.offline");
		}
		g.text(this.font, Ui.ellipsize(this.font, status, w), x, y + 11, Ui.argb(fade, on ? Ui.TEXT : Ui.MUTED));

		// How they fare: six numbers three to a row, then the pact and relic, then the curse atoned for and the clan
		int cw = w / 3, sy = y + 25;
		String relic = str(sel, "relic"), atoning = str(sel, "atoning"), clan = str(sel, "clan");
		com.extremedimensions.relic.Relic r = relic.isEmpty() ? null : com.extremedimensions.relic.Relic.byId(relic);
		Penalty at = atoning.isEmpty() ? null : Penalty.byId(atoning);
		String[][] cells = {
			{"scars", num(sel, "scars") + "/5"}, {"lifted", String.valueOf(num(sel, "lifted"))}, {"deaths", String.valueOf(num(sel, "deaths"))},
			{"expeditions", String.valueOf(num(sel, "exp"))}, {"seam", num(sel, "seam") + " / " + num(sel, "fray")}, {"standing", String.valueOf(num(sel, "rep"))},
		};
		for (int i = 0; i < cells.length; i++) {
			stat(g, cells[i][0], cells[i][1], x + (i % 3) * cw, sy + (i / 3) * 10, cw - 4, fade);
		}
		stat(g, "pact", String.valueOf(num(sel, "pact")), x, sy + 20, cw - 4, fade);
		stat(g, "relic", r == null ? "-" : Ui.tr(r.nameKey()), x + cw, sy + 20, w - cw, fade);
		stat(g, "atoning", at == null ? "-" : Ui.tr(at.nameKey()), x, sy + 30, w / 2 - 4, fade);
		stat(g, "clan", clan.isEmpty() ? "-" : clan, x + w / 2, sy + 30, w - w / 2, fade);
		int ly = sy + 40;
		String lastLine;
		int lastColour = Ui.MUTED;
		if (sel.get("last") instanceof JsonObject last && Penalty.byId(str(last, "id")) != null) {
			Penalty p = Penalty.byId(str(last, "id"));
			lastLine = Ui.tr("gui.extremedimensions.admin.last", Ui.tr(p.nameKey()), num(last, "n"), Ui.tr("gui.extremedimensions.curses.from." + str(last, "from")),
					CursesScreen.ago(num(last, "ago")));
			lastColour = Ui.mix(Ui.setColour(p.group), Ui.TEXT, 0.35);
		} else {
			lastLine = Ui.tr("gui.extremedimensions.admin.last_none");
		}
		g.text(this.font, Ui.ellipsize(this.font, lastLine, w), x, ly, Ui.argb(fade, lastColour));
	}

	/** One figure of the overview: its label muted, its value after it, cut to the width it has. */
	private void stat(GuiGraphicsExtractor g, String key, String value, int x, int y, int w, double fade) {
		String label = Ui.tr("gui.extremedimensions.admin.stat." + key) + " ";
		g.text(this.font, label, x, y, Ui.argb(fade, Ui.MUTED));
		g.text(this.font, Ui.ellipsize(this.font, value, Math.max(8, w - this.font.width(label))), x + this.font.width(label), y, Ui.argb(fade, Ui.TEXT));
	}

	/** One player in the list: online dot, name, and how many stacks they carry. */
	private final class PlayerSlot extends AbstractButton {
		private final int slot;

		PlayerSlot(int slot, int x, int y) {
			super(x, y, LIST_W, ROW, Component.empty());
			this.slot = slot;
		}

		private Player player() {
			int i = listScroll + slot;
			return i < filtered.size() ? filtered.get(i) : null;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			Player p = player();
			if (p != null) {
				pick(p.id());
			}
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			Player p = player();
			this.active = p != null;
			if (p == null) {
				return;
			}
			int x = this.getX(), y = this.getY(), w = this.getWidth();
			boolean picked = p.id().equals(selId), me = p.id().equals(you);
			if (picked) {
				g.fill(x, y, x + w, y + ROW, Ui.argb(0.28, Ui.EMBER));
			} else if (this.isHovered()) {
				g.fill(x, y, x + w, y + ROW, Ui.argb(0.12, 0xFFFFFF));
			}
			g.fill(x + 2, y + 4, x + 6, y + 8, Ui.argb(1.0, p.on() ? Ui.OK : Ui.TRACK));
			String n = String.valueOf(p.stacks());
			String name = p.name() + (me ? " " + Ui.tr("gui.extremedimensions.admin.you") : "");
			g.text(Minecraft.getInstance().font, Ui.ellipsize(Minecraft.getInstance().font, name, w - 14 - Minecraft.getInstance().font.width(n) - 4), x + 9, y + 2,
					Ui.argb(1.0, picked || me ? Ui.PARCHMENT : p.on() ? Ui.TEXT : Ui.MUTED));
			g.text(Minecraft.getInstance().font, n, x + w - 3 - Minecraft.getInstance().font.width(n), y + 2, Ui.argb(1.0, p.stacks() > 0 ? Ui.EMBER_DEEP : Ui.MUTED));
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			Player p = player();
			out.add(NarratedElementType.TITLE, Component.literal(p == null ? "" : p.name()));
		}
	}

	/** A set's tab: its colour and stacks; the open one is wide and named. */
	private final class Tab extends AbstractButton {
		private final Group group;

		Tab(Group group, int x, int y) {
			super(x, y, TAB_N, 14, Component.translatable("gui.extremedimensions.curses.tab_" + group.id()));
			this.group = group;
			this.setTooltip(Tooltip.create(Component.translatable("gui.extremedimensions.curses.tab_" + group.id())));
		}

		@Override
		public void onPress(InputWithModifiers input) {
			if (tab != group) {
				tab = group;
				curseScroll = 0;
				filter();
				layout();
			}
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			var font = Minecraft.getInstance().font;
			int x = this.getX(), y = this.getY(), w = this.getWidth();
			int c = Ui.setColour(group);
			boolean open = tab == group;
			g.fill(x, y, x + w, y + 14, Ui.argb(open ? 0.35 : this.isHovered() ? 0.18 : 0.1, c));
			g.fill(x, y + 13, x + w, y + 14, Ui.argb(open ? 1.0 : 0.4, c));
			g.fill(x + 3, y + 4, x + 8, y + 9, Ui.argb(1.0, c));
			String n = String.valueOf(carried(group));
			g.text(font, n, x + w - 4 - font.width(n), y + 3, Ui.argb(1.0, carried(group) > 0 ? Ui.TEXT : Ui.MUTED));
			if (open) {
				g.text(font, Ui.ellipsize(font, this.getMessage().getString(), w - 20 - font.width(n)), x + 11, y + 3, Ui.argb(1.0, Ui.PARCHMENT));
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			out.add(NarratedElementType.TITLE, this.getMessage());
		}
	}

	/** One curse of the open set: its name, its atonement so far and its stacks; its effect on hover. */
	private final class CurseSlot extends AbstractButton {
		private final int slot;
		private Object tipFor;

		CurseSlot(int slot, int x, int y, int w) {
			super(x, y, w, CROW, Component.empty());
			this.slot = slot;
		}

		@Override
		public void onPress(InputWithModifiers input) {}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int i = curseScroll + slot;
			Penalty p = i < rows.size() ? rows.get(i) : null;
			this.active = p != null;
			if (p == null) {
				tipFor = null;
				this.setTooltip(null);
				return;
			}
			var font = Minecraft.getInstance().font;
			Curse c = curse(p);
			String key = p.id + ":" + c.stacks() + ":" + c.max();
			if (!key.equals(tipFor)) {
				tipFor = key;
				Component tip = Component.translatable(p.nameKey()).append("\n").append(Component.translatable(p.lineKey(1), p.display()))
						.append("\n").append(Component.translatable(p.lineKey(2), p.display()));
				if (p.abyssal) {
					tip = Component.empty().append(tip).append("\n").append(Component.translatable("gui.extremedimensions.curses.abyssal_row"));
				}
				this.setTooltip(Tooltip.create(tip));
			}
			int x = this.getX(), y = this.getY(), w = this.getWidth();
			boolean has = c.stacks() > 0;
			if (this.isHovered()) {
				g.fill(x, y, x + w, y + CROW, Ui.argb(0.1, 0xFFFFFF));
			}
			int col = p.abyssal ? Ui.abyss() : Ui.setColour(p.group);
			g.fill(x + 2, y + 3, x + 7, y + 8, Ui.argb(has ? 1.0 : 0.35, col));
			String stacks = c.stacks() + "/" + c.max();
			int sw = font.width("00/00");
			int meterW = 40, mx = x + w - sw - 6 - meterW;
			g.text(font, Ui.ellipsize(font, Ui.tr(p.nameKey()), mx - x - 16), x + 11, y + 2, Ui.argb(1.0, has ? Ui.PARCHMENT : Ui.MUTED));
			if (has) {
				Ui.meter(g, mx, y + 4, meterW, 4, Math.min(1.0, c.progress() / (double) Math.max(1, c.cost())), Ui.SOUL, 1.0, false);
			}
			g.text(font, stacks, x + w - 2 - font.width(stacks), y + 2, Ui.argb(1.0, has && c.stacks() >= c.max() ? Ui.DANGER : has ? Ui.TEXT : Ui.MUTED));
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			int i = curseScroll + slot;
			out.add(NarratedElementType.TITLE, i < rows.size() ? Component.translatable(rows.get(i).nameKey()) : Component.empty());
		}
	}

	/** Lifts a stack off (minus) or lays one on (plus) the curse in its row. */
	private final class StackButton extends AbstractButton {
		private final int slot;
		private final boolean add;
		private boolean tipShown;

		StackButton(int slot, boolean add, int x, int y) {
			super(x, y, 11, 11, Component.translatable(add ? "gui.extremedimensions.admin.add" : "gui.extremedimensions.admin.lift"));
			this.slot = slot;
			this.add = add;
		}

		private Penalty pen() {
			int i = curseScroll + slot;
			return i < rows.size() ? rows.get(i) : null;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			Penalty p = pen();
			if (p != null && this.active) {
				ClientPlayNetworking.send(new ModPackets.AdminActionPayload(selId, add ? "add" : "lift", p.id));
			}
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			Penalty p = pen();
			// An empty row (the set ends above it): nothing to press, nothing to say
			if (p == null || !tipShown) {
				tipShown = p != null;
				this.setTooltip(p == null ? null
						: Tooltip.create(Component.translatable(add ? "gui.extremedimensions.admin.add_tip" : "gui.extremedimensions.admin.lift_tip")));
			}
			if (p == null) {
				this.active = false;
				return;
			}
			Curse c = curse(p);
			this.active = add ? c.stacks() < c.max() : c.stacks() > 0;
			int x = this.getX(), y = this.getY();
			int col = !this.active ? Ui.TRACK : add ? Ui.EMBER_DEEP : Ui.SOUL;
			g.fill(x, y, x + 11, y + 11, Ui.argb(this.active && this.isHovered() ? 0.45 : 0.2, col));
			g.fill(x, y + 10, x + 11, y + 11, Ui.argb(0.8, col));
			int glyph = this.active ? (this.isHovered() ? 0xFFFFFF : col) : Ui.MUTED;
			// A minus, and for the plus its upright too
			g.fill(x + 3, y + 5, x + 8, y + 6, Ui.argb(this.active ? 1.0 : 0.5, glyph));
			if (add) {
				g.fill(x + 5, y + 3, x + 6, y + 8, Ui.argb(this.active ? 1.0 : 0.5, glyph));
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			out.add(NarratedElementType.TITLE, this.getMessage());
		}
	}
}
