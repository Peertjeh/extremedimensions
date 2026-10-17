package com.extremedimensions.client.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.journal.Bestiary;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.registry.ModEntities;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Tether Journal as a two-page spread, with three parts: the found pages of a nameless Wanderer, the bestiary
 * (every creature of the wound: its likeness once seen, what is known of it, and how to face it once enough have been
 * slain) and the ledger (what the player has been through, counted).
 */
public class JournalScreen extends Screen {
	private static final int PAGE_W = 150;
	private static final int PAGE_H = 196;
	private static final int SPINE = 10;
	private static final int TEXT_W = PAGE_W - 24;
	private static final int LINES_PER_PAGE = 14;
	private static final int INK = 0x4A3526, INK_DARK = 0x3B2416, INK_FAINT = 0x7A6650, INK_TEAL = 0x1F5F5A;

	private enum Part { PAGES, BEASTS, LEDGER }

	/** One page: optional title (first page of an entry only) and lines. */
	private record Page(String title, List<String> lines) {}

	private record Beast(Bestiary.Beast beast, boolean seen, int slain) {}

	private final Set<String> unlocked;
	private final List<Page> pages = new ArrayList<>();
	private final List<Beast> beasts = new ArrayList<>();
	/** The ledger: page name to its lines (key, value) in order. */
	private final Map<String, List<String[]>> ledger = new LinkedHashMap<>();
	private Part part = Part.PAGES;
	/** Index of the left page of the current spread (always even). */
	private int spread = 0;
	private final long openAt = System.currentTimeMillis();
	private long flipAt = 0;
	private Button prev;
	private Button next;
	private final Map<Part, Button> tabs = new EnumMap<>(Part.class);

	/** The inventory, when the journal was opened from its button: closing goes back there. */
	private final Screen parent;

	/** Likenesses for the bestiary, made once (and none where a creature cannot be made here). */
	private static final Map<Bestiary.Beast, LivingEntity> LIKENESS = new EnumMap<>(Bestiary.Beast.class);
	/** Why the last likeness could not be made or drawn (null when all went well), for the tests. */
	private static String likenessError;

	public static String likenessError() {
		return likenessError;
	}

	public JournalScreen(Set<String> unlocked, Screen parent) {
		this(unlocked, "", "", parent);
	}

	public JournalScreen(Set<String> unlocked, String beastData, String ledgerData, Screen parent) {
		super(Component.translatable("item.extremedimensions.tether_journal"));
		this.unlocked = unlocked;
		this.parent = parent;
		Map<String, int[]> known = new LinkedHashMap<>();
		for (String entry : beastData.split(";")) {
			String[] kv = entry.split(":");
			if (kv.length >= 3) {
				try {
					known.put(kv[0], new int[] {Integer.parseInt(kv[1]), Integer.parseInt(kv[2])});
				} catch (NumberFormatException e) {
					// skip a damaged entry
				}
			}
		}
		for (Bestiary.Beast b : Bestiary.Beast.values()) {
			int[] v = known.getOrDefault(b.id, new int[] {0, 0});
			beasts.add(new Beast(b, v[0] != 0, v[1]));
		}
		for (String entry : ledgerData.split(";")) {
			int colon = entry.indexOf(':'), eq = entry.indexOf('=');
			if (colon > 0 && eq > colon) {
				ledger.computeIfAbsent(entry.substring(0, colon), k -> new ArrayList<>())
						.add(new String[] {entry.substring(colon + 1, eq), entry.substring(eq + 1)});
			}
		}
	}

	@Override
	public void onClose() {
		if (parent != null) {
			Minecraft.getInstance().gui.setScreen(parent);
		} else {
			super.onClose();
		}
	}

	private int bookX() {
		return this.width / 2 - PAGE_W - SPINE / 2;
	}

	private int bookY() {
		return Math.max(6, this.height / 2 - PAGE_H / 2 - 8);
	}

	/** Pages in the part shown (a spread is two). */
	private int count() {
		return switch (part) {
			case PAGES -> pages.size();
			case BEASTS -> beasts.size() * 2;
			case LEDGER -> ledger.isEmpty() ? 0 : 4;
		};
	}

	@Override
	protected void init() {
		this.clearWidgets();
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
		spread = Math.max(0, Math.min(spread, (count() - 1) & ~1));

		int bx = bookX(), y = bookY() + PAGE_H + 6;
		prev = this.addRenderableWidget(Button.builder(Component.literal("◀"), b -> flip(-2)).pos(bx, y).size(24, 18).build());
		next = this.addRenderableWidget(Button.builder(Component.literal("▶"), b -> flip(2)).pos(bx + PAGE_W * 2 + SPINE - 24, y).size(24, 18).build());
		int tx = bx + 28;
		tabs.clear();
		for (Part p : Part.values()) {
			String key = "gui.extremedimensions.journal.tab." + p.name().toLowerCase(java.util.Locale.ROOT);
			Button tab = Button.builder(Component.translatable(key), b -> show(p)).pos(tx, y).size(60, 18).build();
			tab.active = part != p;
			tabs.put(p, this.addRenderableWidget(tab));
			tx += 62;
		}
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
				.pos(tx + 2, y).size(bx + PAGE_W * 2 + SPINE - 28 - tx - 4, 18).build());
		updateButtons();
	}

	private void show(Part p) {
		part = p;
		spread = 0;
		flipAt = System.currentTimeMillis();
		this.init();
	}

	private void flip(int dir) {
		int np = spread + dir;
		if (np >= 0 && np < count()) {
			spread = np;
			flipAt = System.currentTimeMillis();
		}
		updateButtons();
	}

	private void updateButtons() {
		if (prev != null) {
			prev.active = spread > 0;
			next.active = spread + 2 < count();
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

		double fe = flipAt > 0 ? Ui.ease((now - flipAt) / 300.0) : 1.0;
		String counter;
		switch (part) {
			case BEASTS -> {
				Beast b = beasts.get(Math.min(beasts.size() - 1, spread / 2));
				beastLeft(g, b, bx, by, pe * fe, mouseX, mouseY);
				beastRight(g, b, bx + PAGE_W + SPINE, by, pe * fe);
				long seen = beasts.stream().filter(Beast::seen).count();
				counter = Ui.tr("gui.extremedimensions.bestiary.found", seen, beasts.size());
			}
			case LEDGER -> {
				ledgerSpread(g, bx, by, pe * fe);
				counter = Ui.tr("gui.extremedimensions.journal.tab.ledger");
			}
			default -> {
				if (pages.isEmpty()) {
					String s = Ui.tr("gui.extremedimensions.journal.blank");
					Ui.ink(g, this.font, s, bx + PAGE_W / 2 - this.font.width(s) / 2, by + 80, 0x6A5A48, pe);
				} else {
					writePage(g, spread, bx, by, pe * fe);
					writePage(g, spread + 1, bx + PAGE_W + SPINE, by, pe * fe);
				}
				counter = Ui.tr("gui.extremedimensions.journal.found", unlocked.size(), JournalEntry.values().length);
			}
		}
		if (by - 20 >= 0) {
			g.text(this.font, counter, this.width / 2 - this.font.width(counter) / 2, by - 20, Ui.argb(pe, Ui.MUTED));
		}
	}

	private static void page(GuiGraphicsExtractor g, int x, int y, double alpha) {
		g.fill(x, y, x + PAGE_W, y + PAGE_H, Ui.argb(alpha, 0xD9C9A3));
		g.fill(x, y, x + PAGE_W, y + 3, Ui.argb(alpha * 0.25, 0x6A5A48));
		g.fill(x, y + PAGE_H - 3, x + PAGE_W, y + PAGE_H, Ui.argb(alpha * 0.3, 0x6A5A48));
	}

	private void title(GuiGraphicsExtractor g, String title, int x, int y, double alpha) {
		int cx = x + PAGE_W / 2;
		Ui.ink(g, this.font, title, cx - this.font.width(title) / 2, y + 12, INK_DARK, alpha);
		g.fill(cx - 30, y + 23, cx + 30, y + 24, Ui.argb(alpha * 0.6, 0x2E7F78));
	}

	private void number(GuiGraphicsExtractor g, int index, int x, int y, double alpha) {
		String num = String.valueOf(index + 1);
		Ui.ink(g, this.font, num, x + PAGE_W / 2 - this.font.width(num) / 2, y + PAGE_H - 13, INK_FAINT, alpha * 0.7);
	}

	private void writePage(GuiGraphicsExtractor g, int index, int x, int y, double alpha) {
		if (index >= pages.size()) {
			return;
		}
		Page p = pages.get(index);
		int ty = y + 12;
		if (p.title() != null) {
			title(g, p.title(), x, y, alpha);
			ty += 22;
		}
		for (String line : p.lines()) {
			Ui.ink(g, this.font, line, x + 12, ty, INK, alpha);
			ty += 11;
		}
		number(g, index, x, y, alpha);
	}

	// ------------------------------------------------------------------ the bestiary

	/** The left page: the creature's name and likeness (a blank sigil until it has been seen), and how many were slain. */
	private void beastLeft(GuiGraphicsExtractor g, Beast b, int x, int y, double alpha, int mouseX, int mouseY) {
		title(g, b.seen() ? Ui.tr(b.beast().nameKey()) : Ui.tr("gui.extremedimensions.bestiary.unknown"), x, y, alpha);
		int fx = x + 14, fy = y + 32, fw = PAGE_W - 28, fh = 120;
		g.fill(fx, fy, fx + fw, fy + fh, Ui.argb(alpha * 0.85, 0x1C1420));
		g.fill(fx, fy, fx + fw, fy + 1, Ui.argb(alpha, 0x6A5A48));
		g.fill(fx, fy + fh - 1, fx + fw, fy + fh, Ui.argb(alpha, 0x6A5A48));
		LivingEntity e = b.seen() ? likeness(b.beast()) : null;
		if (e != null && alpha > 0.6) {
			float size = Math.max(e.getBbHeight(), e.getBbWidth() * 0.9f);
			int scale = Math.max(4, (int) (fh * 0.72 / Math.max(0.5f, size)));
			try {
				InventoryScreen.extractEntityInInventoryFollowsMouse(g, fx, fy, fx + fw, fy + fh, scale, 0.0625f,
						fx + fw * 0.35f, fy + fh * 0.4f, e);
			} catch (RuntimeException ex) {
				LIKENESS.put(b.beast(), null);
				likenessError = b.beast().id + ": " + ex;
				com.extremedimensions.ExtremeDimensions.LOGGER.warn("Could not draw the likeness of {}", b.beast().id, ex);
			}
		} else {
			Ui.runeSocket(g, b.beast().id, fx + fw / 2 - 8, fy + fh / 2 - 8, Ui.SOUL_DIM, alpha, 0.2);
		}
		String status = b.seen() ? Ui.tr("gui.extremedimensions.bestiary.slain", b.slain()) : Ui.tr("gui.extremedimensions.bestiary.unseen");
		Ui.ink(g, this.font, status, x + PAGE_W / 2 - this.font.width(status) / 2, fy + fh + 10, b.seen() ? INK_DARK : INK_FAINT, alpha);
		number(g, spread, x, y, alpha);
	}

	/** The right page: what is known of it once seen, and how to face it once enough have been slain. */
	private void beastRight(GuiGraphicsExtractor g, Beast b, int x, int y, double alpha) {
		// Set close, as a book is, so what is known and how to face it share the page
		int ty = y + 12, lh = 9;
		if (!b.seen()) {
			for (String line : Ui.wrap(this.font, Ui.tr("gui.extremedimensions.bestiary.unseen_text"), TEXT_W)) {
				Ui.ink(g, this.font, line, x + 12, ty, INK_FAINT, alpha);
				ty += 11;
			}
			number(g, spread + 1, x, y, alpha);
			return;
		}
		for (String line : Ui.wrap(this.font, Ui.tr(b.beast().loreKey()), TEXT_W)) {
			Ui.ink(g, this.font, line, x + 12, ty, INK, alpha);
			ty += lh;
		}
		ty += 4;
		String head = Ui.tr("gui.extremedimensions.bestiary.notes_title");
		Ui.ink(g, this.font, head, x + 12, ty, INK_TEAL, alpha);
		g.fill(x + 12, ty + 9, x + 12 + this.font.width(head), ty + 10, Ui.argb(alpha * 0.5, INK_TEAL));
		ty += 12;
		String notes = b.slain() >= b.beast().notesAt ? Ui.tr(b.beast().notesKey())
				: Ui.tr("gui.extremedimensions.bestiary.notes_locked", b.beast().notesAt - b.slain());
		for (String line : Ui.wrap(this.font, notes, TEXT_W)) {
			if (ty > y + PAGE_H - 24) {
				break;
			}
			Ui.ink(g, this.font, line, x + 12, ty, b.slain() >= b.beast().notesAt ? INK_TEAL : INK_FAINT, alpha);
			ty += lh;
		}
		number(g, spread + 1, x, y, alpha);
	}

	private static EntityType<? extends LivingEntity> type(Bestiary.Beast b) {
		return switch (b) {
			case LURKER -> ModEntities.LURKER;
			case HOLLOWLING -> ModEntities.HOLLOWLING;
			case KEEPER -> ModEntities.KEEPER;
			case STARVED_ONE -> ModEntities.STARVED_ONE;
			case THINNED -> ModEntities.THINNED;
			case VEIL_RAY -> ModEntities.VEIL_RAY;
			case MATRIARCH -> ModEntities.MATRIARCH;
			case FERRYMAN -> ModEntities.FERRYMAN;
			case LAST_WEAVER -> ModEntities.WEAVER;
		};
	}

	private static LivingEntity likeness(Bestiary.Beast b) {
		if (LIKENESS.containsKey(b)) {
			return LIKENESS.get(b);
		}
		LivingEntity e = null;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			try {
				e = type(b).create(mc.level, EntitySpawnReason.LOAD);
				if (e == null) {
					likenessError = b.id + ": could not be made";
				} else {
					// Never added to the world, so it is given an id of its own, one no real creature has
					e.setId(-7300 - b.ordinal());
				}
			} catch (RuntimeException ex) {
				e = null;
				likenessError = b.id + ": " + ex;
				com.extremedimensions.ExtremeDimensions.LOGGER.warn("Could not make the likeness of {}", b.id, ex);
			}
		}
		LIKENESS.put(b, e);
		return e;
	}

	// ------------------------------------------------------------------ the ledger

	private void ledgerSpread(GuiGraphicsExtractor g, int bx, int by, double alpha) {
		String[] order = spread == 0 ? new String[] {"road", "burden"} : new String[] {"others", null};
		for (int side = 0; side < 2; side++) {
			int x = bx + side * (PAGE_W + SPINE);
			String name = order[side];
			if (name == null) {
				// The last page: a line from the Wanderer
				int ty = by + PAGE_H / 2 - 10;
				for (String line : Ui.wrap(this.font, Ui.tr("gui.extremedimensions.ledger.page.close"), TEXT_W)) {
					Ui.ink(g, this.font, line, x + PAGE_W / 2 - this.font.width(line) / 2, ty, INK_FAINT, alpha);
					ty += 11;
				}
				number(g, spread + side, x, by, alpha);
				continue;
			}
			title(g, Ui.tr("gui.extremedimensions.ledger.page." + name), x, by, alpha);
			int ty = by + 34;
			for (String[] row : ledger.getOrDefault(name, List.of())) {
				String label = Ui.tr("gui.extremedimensions.ledger." + row[0]);
				String value = value(row[0], row[1]);
				int vw = this.font.width(value);
				for (String line : Ui.wrap(this.font, label, TEXT_W - vw - 6)) {
					Ui.ink(g, this.font, line, x + 12, ty, INK, alpha);
					ty += 10;
				}
				Ui.ink(g, this.font, value, x + PAGE_W - 12 - vw, ty - 10, INK_DARK, alpha);
				// a dotted leader under each line
				for (int dx = x + 12; dx < x + PAGE_W - 12; dx += 3) {
					g.fill(dx, ty - 1, dx + 1, ty, Ui.argb(alpha * 0.35, INK_FAINT));
				}
				ty += 3;
			}
			number(g, spread + side, x, by, alpha);
		}
	}

	/** A ledger value as it is written: times as times, the standing by name, the relic by name. */
	private static String value(String key, String raw) {
		try {
			switch (key) {
				case "longest":
					return Ui.time(Double.parseDouble(raw));
				case "furthest":
					return raw + " m";
				case "standing": {
					String[] v = raw.split("/");
					int rep = Integer.parseInt(v[1]);
					return Ui.tr("gui.extremedimensions.tribe.level." + v[0]) + " (" + (rep > 0 ? "+" : "") + rep + ")";
				}
				case "relic":
					return raw.isEmpty() ? Ui.tr("gui.extremedimensions.ledger.none") : Ui.tr("item.extremedimensions." + raw);
				default:
					return raw;
			}
		} catch (RuntimeException e) {
			return raw;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
