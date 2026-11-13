package com.extremedimensions.client.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.extremedimensions.client.ModKeys;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.config.Config;
import com.extremedimensions.guide.Hints;
import com.extremedimensions.guide.Path;
import com.extremedimensions.journal.Bestiary;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.journal.MarginNote;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.tribe.Standing;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Tether Journal as a two-page spread, in parts: the found pages of a nameless Wanderer, the path, the bestiary
 * (every creature of the wound: its likeness once seen, what is known of it, and how to face it once enough have been
 * slain), the ledger (what the player has been through, counted), the clan (who the player is bound to, how the
 * tribe sees them together, and the cord's law with this world's numbers), the guides (short how-tos, one to a page,
 * the wiki holding the rest) and the anchors (every anchor that keeps charge for the player).
 */
public class JournalScreen extends Screen {
	private static final int PAGE_W = 150;
	private static final int PAGE_H = 196;
	private static final int SPINE = 10;
	private static final int TEXT_W = PAGE_W - 24;
	private static final int LINES_PER_PAGE = 14;
	private static final int INK = 0x4A3526, INK_DARK = 0x3B2416, INK_FAINT = 0x7A6650, INK_TEAL = 0x1F5F5A;
	/** The second hand: rust-brown ink, set in from the Wanderer's lines. */
	private static final int INK_RUST = 0x6B2A1A, NOTE_INDENT = 8;
	/** The player's own hand, after the ending: a deep blue-black ink, neither the Wanderer's nor the other's. */
	private static final int INK_OWN = 0x1E2F5C;
	/** The marks of an anchor's world: the embers of the Nether, the violet of the End. */
	private static final int INK_EMBER = 0x9A3B12, INK_VOID = 0x4B2E83;

	private enum Part { PAGES, PATH, BEASTS, LEDGER, CLAN, GUIDES, ANCHORS }

	/** The guides, in the order the story comes to them (the first expedition first); their words are in the lang files. */
	private static final String[] GUIDE_IDS = {"first_expedition", "anchors", "tribe", "curses", "matriarch", "relics", "seam", "spindle",
		"tetherite", "fray", "knot_mother", "ferryman", "pale_reach", "pale_mason", "starved_one", "last_weaver", "weavers_loom",
		"kindled_lantern", "coop", "server"};
	/**
	 * A guide's page is set closer than the Wanderer's, with narrower margins, so five short steps and the line on the
	 * wiki share it: the margin, the text's width, the steps' indent past their numbers, where the first step stands,
	 * the line height and the room between two steps.
	 */
	private static final int GUIDE_X = 10, GUIDE_W = PAGE_W - 2 * GUIDE_X, GUIDE_INDENT = 10, GUIDE_TOP = 32, GUIDE_LINE = 9, GUIDE_GAP = 2;
	/** Anchors to a page, and the height each takes. */
	private static final int KEPT_PER_PAGE = 2, KEPT_SLOT = 62;
	/**
	 * The guides' contents (3.55.0): where its first line stands on a page, the height of a line, and the lines a page
	 * holds (above its number at the foot).
	 */
	private static final int CONTENTS_TOP = 34, CONTENTS_ROW = 13, CONTENTS_PER_PAGE = (PAGE_H - 18 - CONTENTS_TOP) / CONTENTS_ROW;

	/**
	 * One line of a guide: where it stands on its page, its step's number on the step's first line (else empty), and its
	 * words; or (head) a heading in the teal ink across the page, as each hint's name is in the list of hints.
	 */
	private record GuideLine(int y, String mark, String text, boolean head) {
		GuideLine(int y, String mark, String text) {
			this(y, mark, text, false);
		}
	}

	/** One page of a guide: its title (on its first page only), its lines, and whether the guide ends on it. */
	private record GuidePage(String title, List<GuideLine> lines, boolean last) {}

	/**
	 * One anchor that keeps charge for the player, as {@code AnchorList.encode} writes it: its world, where it stands, the
	 * charge it holds for the player and their own part of it (seconds), the seconds before it breaks (0: steady), the
	 * allies who lend the player their charge in it (names joined by "/"), whether an older world's charge in it is
	 * kept for everyone, and (3.55.0) whether its way to its portal is blocked, so that it leaks.
	 */
	private record Kept(boolean nether, int x, int y, int z, int charge, int own, int cracked, String lenders, boolean everyone, boolean blocked) {}

	/**
	 * One line on a page: the Wanderer's, a note in another hand beside them, a page wholly in that hand (rust), or a
	 * page in the player's own hand.
	 */
	private record Line(String text, boolean note, boolean rust, boolean own) {
		Line(String text, boolean note) {
			this(text, note, note, false);
		}

		Line(String text, boolean note, boolean rust) {
			this(text, note, rust, false);
		}
	}

	/** One page: optional title (first page of an entry only) and lines. */
	private record Page(String title, List<Line> lines) {}

	private record Beast(Bestiary.Beast beast, boolean seen, int slain) {}

	/** One of the player's clan: where is 0 away, 1 online elsewhere, 2 near, 3 the player. */
	private record Member(String name, String level, int rep, int where) {}

	/** The cord's law as this world has it, in the order {@link #CLAN_RULE_NAMES} names (null: the server sent none). */
	private int[] clanRules;
	private static final String[] CLAN_RULE_NAMES = {"cord", "max", "blood", "loss", "gain", "range", "lock", "leave", "curses", "left"};
	private boolean clanOn;
	private String ownLevel = "wary", judgedLevel = "wary";
	private int ownRep, judgedRep, lockedFor;
	private final List<Member> clan = new ArrayList<>();

	private final Set<String> unlocked;
	/** Notes in another hand the player has found (shown beside their pages). */
	private final Set<String> notes;
	private final List<Page> pages = new ArrayList<>();
	private final List<Beast> beasts = new ArrayList<>();
	/** The ledger: page name to its lines (key, value) in order. */
	private final Map<String, List<String[]>> ledger = new LinkedHashMap<>();
	/** The Wanderer's path: steps done, beside the path done and seen (bit masks), and the line on the next step. */
	private int pathDone, sideDone, sideSeen;
	private String pathHint = "";
	private final List<GuidePage> guidePages = new ArrayList<>();
	/** The anchors that keep charge for the player, as many as were sent; and how many there are in all. */
	private final List<Kept> kept = new ArrayList<>();
	private int keptTotal;
	/** Whether the player opened their anchors to their allies, and to whom (names joined by "/"). */
	private boolean keptOpen;
	private String keptAllies = "";
	/** How many rows the tabs take under the book: two where a language's names do not fit in one. */
	private int tabRows = 1;
	private Part part = Part.PAGES;
	/** Index of the left page of the current spread (always even). */
	private int spread = 0;
	private final long openAt = System.currentTimeMillis();
	private long flipAt = 0;
	private Button prev;
	private Button next;
	private final Map<Part, Button> tabs = new EnumMap<>(Part.class);
	/** The first-meeting hints the player was told, in the order they came (3.55.0), listed after the guides. */
	private final List<Hints.Hint> hintsTold = new ArrayList<>();
	/**
	 * The Guides part opens on its contents (3.55.0): the pages they take (whole spreads), each guide's first page in
	 * the part in the order of {@link #GUIDE_IDS} and then the hints', and the contents' lines.
	 */
	private int contentsPages = 2;
	private final List<Integer> guideFirst = new ArrayList<>();
	private final List<ContentsLine> contents = new ArrayList<>();
	/** The Anchors part's switch for opening the player's anchors to their allies (3.55.0), where the world shares them. */
	private ShareToggle share;
	/** How strongly the pages are inked this frame (they fade in and turn), for what is drawn on them as widgets. */
	private double pageInk = 1.0;

	/** The part and spread shown last this session (3.55.0), where the next journal opened from the game opens. */
	private static Part lastPart = Part.PAGES;
	private static int lastSpread;

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
		this(unlocked, "", "", Set.of(), parent);
	}

	public JournalScreen(Set<String> unlocked, String beastData, String ledgerData, Set<String> notes, Screen parent) {
		this(unlocked, beastData, ledgerData, notes, "", parent);
	}

	/** The journal opened last, for the tests (they read what it was sent). */
	private static JournalScreen last;

	public static JournalScreen last() {
		return last;
	}

	public JournalScreen(Set<String> unlocked, String beastData, String ledgerData, Set<String> notes, String pathData, Screen parent) {
		this(unlocked, beastData, ledgerData, notes, pathData, "", parent);
	}

	public JournalScreen(Set<String> unlocked, String beastData, String ledgerData, Set<String> notes, String pathData, String clanData,
			Screen parent) {
		this(unlocked, beastData, ledgerData, notes, pathData, clanData, "", parent);
	}

	public JournalScreen(Set<String> unlocked, String beastData, String ledgerData, Set<String> notes, String pathData, String clanData,
			String anchorData, Screen parent) {
		this(unlocked, beastData, ledgerData, notes, pathData, clanData, anchorData, "", parent);
	}

	public JournalScreen(Set<String> unlocked, String beastData, String ledgerData, Set<String> notes, String pathData, String clanData,
			String anchorData, String hintData, Screen parent) {
		super(Component.translatable("item.extremedimensions.tether_journal"));
		last = this;
		readClan(clanData);
		readKept(anchorData);
		for (String key : hintData.split(",")) {
			try {
				Hints.Hint h = key.isEmpty() ? null : Hints.Hint.valueOf(key.toUpperCase(java.util.Locale.ROOT));
				if (h != null && !hintsTold.contains(h)) {
					hintsTold.add(h);
				}
			} catch (IllegalArgumentException e) {
				// a hint this version does not know
			}
		}
		String[] path = pathData.split(";", 4);
		if (path.length == 4) {
			try {
				pathDone = Integer.parseInt(path[0]);
				sideDone = Integer.parseInt(path[1]);
				sideSeen = Integer.parseInt(path[2]);
				pathHint = path[3];
			} catch (NumberFormatException e) {
				// a damaged path: the tab shows the path as not begun
			}
		}
		this.unlocked = unlocked;
		this.notes = notes;
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

	/** Reads the clan as {@code ClanService.journal} writes it; a damaged one leaves the tab out. */
	private void readClan(String data) {
		String[] f = data.split("\\|", -1);
		if (f.length != 15) {
			return;
		}
		try {
			int[] rules = new int[CLAN_RULE_NAMES.length];
			for (int i = 0; i < rules.length; i++) {
				rules[i] = Integer.parseInt(f[1 + i]);
			}
			String[] own = f[11].split("/"), judged = f[12].split("/");
			ownLevel = own[0];
			ownRep = Integer.parseInt(own[1]);
			judgedLevel = judged[0];
			judgedRep = Integer.parseInt(judged[1]);
			lockedFor = Integer.parseInt(f[13]);
			for (String m : f[14].split(";")) {
				String[] v = m.split(",");
				if (v.length == 4) {
					clan.add(new Member(v[0], v[1], Integer.parseInt(v[2]), Integer.parseInt(v[3])));
				}
			}
			clanOn = "1".equals(f[0]);
			clanRules = rules;
		} catch (RuntimeException e) {
			clan.clear();
			clanRules = null;
		}
	}

	/** How many the Clan tab lists (the player among them; 0 when they are of no clan), for the tests. */
	public int clanSize() {
		return clan.size();
	}

	/** Reads the anchors as {@code AnchorList.encode} writes them; a damaged list leaves the tab saying none keep charge. */
	private void readKept(String data) {
		String[] f = data.split("\\|", -1);
		if (f.length != 4) {
			return;
		}
		try {
			for (String entry : f[3].split(";")) {
				String[] v = entry.split(",", -1);
				// (nine fields from a server of before 3.55.0, which did not say whether one is blocked)
				if (v.length == 9 || v.length == 10) {
					kept.add(new Kept("nether".equals(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]), Integer.parseInt(v[3]),
							Integer.parseInt(v[4]), Integer.parseInt(v[5]), Integer.parseInt(v[6]), v[7], "1".equals(v[8]), v.length == 10 && "1".equals(v[9])));
				}
			}
			keptTotal = Math.max(kept.size(), Integer.parseInt(f[2]));
			keptOpen = "1".equals(f[0]);
			keptAllies = f[1];
		} catch (NumberFormatException e) {
			kept.clear();
			keptTotal = 0;
		}
	}

	/** How many anchors the Anchors tab lists, for the tests. */
	public int keptSize() {
		return kept.size();
	}

	/** Whether the journal has this part as a tab (the clan only where clans are bound in this world). */
	private boolean has(Part p) {
		return p != Part.CLAN || clanOn && clanRules != null;
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
		int y = Math.max(6, this.height / 2 - PAGE_H / 2 - 8);
		// (with the tabs in two rows the book stands higher where the second row would leave the screen)
		return tabRows > 1 ? Math.max(0, Math.min(y, this.height - PAGE_H - 46)) : y;
	}

	/** Pages in the part shown (a spread is two). */
	private int count() {
		return switch (part) {
			case PAGES -> pages.size();
			case BEASTS -> beasts.size() * 2;
			// (the last falls, where any are kept, add a spread: see ledgerSpread)
			case LEDGER -> ledger.isEmpty() ? 0 : ledger.containsKey("falls") ? 6 : 4;
			case PATH, CLAN -> 2;
			case GUIDES -> contentsPages + guidePages.size();
			case ANCHORS -> Math.max(1, (kept.size() + KEPT_PER_PAGE - 1) / KEPT_PER_PAGE);
		};
	}

	/**
	 * Opens on the part and spread shown last this session (3.55.0), as the game opens the journal; a part this journal
	 * has no tab for (a clan left) opens on the found pages.
	 */
	public void resume() {
		part = has(lastPart) ? lastPart : Part.PAGES;
		spread = part == lastPart ? lastSpread : 0;
	}

	/** Leaving a world: the next journal opens on its first pages again. */
	public static void forget() {
		lastPart = Part.PAGES;
		lastSpread = 0;
	}

	private void remember() {
		lastPart = part;
		lastSpread = spread;
	}

	/** Whether the Path tab is open, for the tests (a journal opened from the game may open on it already). */
	public boolean showsPath() {
		return part == Part.PATH;
	}

	/** The step the Path tab names as the next one (null when the whole path is walked), for the tests. */
	public Path.Step nextStep() {
		for (Path.Step s : Path.Step.values()) {
			if ((pathDone & 1 << s.ordinal()) == 0) {
				return s;
			}
		}
		return null;
	}

	/** The line under the next step (a translation key), for the tests. */
	public String pathHint() {
		return pathHint;
	}

	@Override
	protected void init() {
		this.clearWidgets();
		pages.clear();
		for (JournalEntry e : JournalEntry.values()) {
			if (!unlocked.contains(e.id)) {
				continue;
			}
			// Each block is kept on one page where it fits: a line of the text alone, a note with its lines together
			List<List<Line>> blocks = new ArrayList<>();
			for (String l : Ui.wrap(this.font, Ui.tr(e.textKey()), TEXT_W)) {
				// a page not in the Wanderer's hand is written all in the other ink (or in the player's own)
				blocks.add(List.of(new Line(l, false, !e.wanderer && !e.own, e.own)));
			}
			// Under the Wanderer's words, the notes another hand left beside them
			for (MarginNote n : MarginNote.values()) {
				if (n.page == e && notes.contains(n.id)) {
					List<Line> note = new ArrayList<>();
					note.add(new Line("", true));
					for (String l : Ui.wrap(this.font, Ui.tr(n.textKey()), TEXT_W - NOTE_INDENT)) {
						note.add(new Line(l, true));
					}
					blocks.add(note);
				}
			}
			paginate(Ui.tr(e.titleKey()), blocks);
		}
		layGuides();

		// The tabs in one row between the arrows, each as wide as its name, the rest for Done; where the names do not fit
		// beside Done, a cross closes the book instead; and where they do not fit beside a cross either, they take two rows
		// within the same width, the second ending in Done
		tabs.clear();
		Part[] parts = java.util.Arrays.stream(Part.values()).filter(this::has).toArray(Part[]::new);
		Component doneLabel = Component.translatable("gui.done");
		int rowW = PAGE_W * 2 + SPINE - 56, doneW = Math.max(34, this.font.width(doneLabel) + 12);
		boolean cross = false;
		int split = parts.length;
		if (fitTabs(parts, 0, parts.length, new int[parts.length], rowW - doneW - 2) > rowW - doneW - 2) {
			if (fitTabs(parts, 0, parts.length, new int[parts.length], rowW - 22) <= rowW - 22) {
				cross = true;
			} else {
				split = (parts.length + 1) / 2;
			}
		}
		tabRows = split < parts.length ? 2 : 1;
		spread = Math.max(0, Math.min(spread, (count() - 1) & ~1));

		int bx = bookX(), y = bookY() + PAGE_H + 6;
		prev = this.addRenderableWidget(Button.builder(Component.literal("◀"), b -> flip(-2)).pos(bx, y).size(24, 18).build());
		next = this.addRenderableWidget(Button.builder(Component.literal("▶"), b -> flip(2)).pos(bx + PAGE_W * 2 + SPINE - 24, y).size(24, 18).build());
		int left = bx + 28, right = left + rowW;
		int tx = tabRow(parts, 0, split, left, tabRows > 1 ? rowW : rowW - (cross ? 22 : doneW + 2), y);
		if (tabRows > 1) {
			y += 20;
			tx = tabRow(parts, split, parts.length, left, rowW - doneW - 2, y);
		}
		Button done = this.addRenderableWidget(Button.builder(cross ? Component.literal("\u2715") : doneLabel, b -> this.onClose())
				.pos(tx, y).size(right - tx, 18).build());
		if (cross) {
			done.setTooltip(net.minecraft.client.gui.components.Tooltip.create(doneLabel));
		}
		// The guides' contents, a line for each guide and one for the hints, filling the contents' pages in turn
		contents.clear();
		int by = bookY();
		for (int i = 0; i < guideFirst.size(); i++) {
			int page = i / CONTENTS_PER_PAGE;
			String label = i < GUIDE_IDS.length ? Ui.tr("gui.extremedimensions.journal.guide." + GUIDE_IDS[i])
					: Ui.tr("gui.extremedimensions.journal.hints.entry", hintsTold.size());
			contents.add(this.addRenderableWidget(new ContentsLine(bx + (page % 2) * (PAGE_W + SPINE) + GUIDE_X,
					by + CONTENTS_TOP + (i % CONTENTS_PER_PAGE) * CONTENTS_ROW, page, guideFirst.get(i), label)));
		}
		share = Config.COOP_SHARED_ANCHORS > 0
				? this.addRenderableWidget(new ShareToggle(bx + PAGE_W + SPINE + 12, by + PAGE_H - 31, PAGE_W - 24))
				: null;
		updateButtons();
	}

	/**
	 * One row of tabs from x, within room: each as wide as its name where they fit, all narrower alike where a language's
	 * names do not. Returns where the row ends.
	 */
	private int tabRow(Part[] parts, int from, int to, int x, int room, int y) {
		int[] widths = new int[to - from];
		int sum = fitTabs(parts, from, to, widths, room);
		for (int i = from; i < to; i++) {
			int w = sum > room ? room / (to - from) - 2 : widths[i - from];
			Part p = parts[i];
			Button tab = Button.builder(Component.translatable(tabKey(p)), b -> show(p)).pos(x, y).size(w, 18).build();
			tab.active = part != p;
			tabs.put(p, this.addRenderableWidget(tab));
			x += w + 2;
		}
		return x;
	}

	/**
	 * Each of these tabs as wide as its name and a margin, a narrower margin where the full one does not fit. Returns the
	 * width taken.
	 */
	private int fitTabs(Part[] parts, int from, int to, int[] widths, int room) {
		int sum = 0;
		for (int pad : new int[] {12, 6}) {
			sum = 0;
			for (int i = from; i < to; i++) {
				widths[i - from] = this.font.width(Component.translatable(tabKey(parts[i]))) + pad;
				sum += widths[i - from] + 2;
			}
			if (sum <= room) {
				break;
			}
		}
		return sum;
	}

	/**
	 * Lays the guides out, each on a page of its own: the title, the numbered steps, and the line on the wiki at the foot.
	 * A step that would run into that line (a long translation) goes on to the next page, with the rest of its guide.
	 * Before them, their contents on a spread of their own (3.55.0); after them, the hints the player was told.
	 */
	private void layGuides() {
		guidePages.clear();
		guideFirst.clear();
		int entries = GUIDE_IDS.length + 1;
		contentsPages = (((entries + CONTENTS_PER_PAGE - 1) / CONTENTS_PER_PAGE) + 1) & ~1;
		int foot = PAGE_H - 24 - GUIDE_LINE * (Ui.wrap(this.font, Ui.tr("gui.extremedimensions.journal.guide.wiki"), GUIDE_W).size() - 1) - 4;
		for (String id : GUIDE_IDS) {
			guideFirst.add(contentsPages + guidePages.size());
			String key = "gui.extremedimensions.journal.guide." + id;
			String title = Ui.tr(key);
			List<GuideLine> lines = new ArrayList<>();
			int ty = GUIDE_TOP;
			for (int n = 1; n <= 5 && Language.getInstance().has(key + "." + n); n++) {
				List<String> step = Ui.wrap(this.font, Ui.tr(key + "." + n), GUIDE_W - GUIDE_INDENT);
				int at = lines.isEmpty() ? ty : ty + GUIDE_GAP;
				if (!lines.isEmpty() && at + GUIDE_LINE * step.size() > foot) {
					guidePages.add(new GuidePage(title, lines, false));
					title = null;
					lines = new ArrayList<>();
					at = 12;
				}
				for (int i = 0; i < step.size(); i++) {
					lines.add(new GuideLine(at + i * GUIDE_LINE, i == 0 ? n + "." : "", step.get(i)));
				}
				ty = at + GUIDE_LINE * step.size();
			}
			guidePages.add(new GuidePage(title, lines, true));
		}
		guideFirst.add(contentsPages + guidePages.size());
		layHints();
	}

	/**
	 * The hints the player was told (3.55.0), to be read again: each one's name in the teal ink with its words under it,
	 * kept together on a page, as many to a page as fit; or a line on where they will be kept, while there are none.
	 */
	private void layHints() {
		String title = Ui.tr("gui.extremedimensions.journal.hints.title");
		int foot = PAGE_H - 18;
		List<GuideLine> lines = new ArrayList<>();
		int ty = GUIDE_TOP;
		if (hintsTold.isEmpty()) {
			for (String l : Ui.wrap(this.font, Ui.tr("gui.extremedimensions.journal.hints.none"), GUIDE_W - GUIDE_INDENT)) {
				lines.add(new GuideLine(ty, "", l));
				ty += GUIDE_LINE;
			}
		}
		for (Hints.Hint h : hintsTold) {
			List<String> text = Ui.wrap(this.font, Ui.tr(h.textKey()), GUIDE_W - GUIDE_INDENT);
			int at = lines.isEmpty() ? ty : ty + GUIDE_GAP + 2;
			if (!lines.isEmpty() && at + GUIDE_LINE * (1 + text.size()) > foot) {
				guidePages.add(new GuidePage(title, lines, false));
				title = null;
				lines = new ArrayList<>();
				at = 12;
			}
			lines.add(new GuideLine(at, "", Ui.ellipsize(this.font, Ui.tr(h.titleKey()), GUIDE_W), true));
			for (int i = 0; i < text.size(); i++) {
				lines.add(new GuideLine(at + (i + 1) * GUIDE_LINE, "", text.get(i)));
			}
			ty = at + GUIDE_LINE * (1 + text.size());
		}
		guidePages.add(new GuidePage(title, lines, false));
	}

	/** How many pages the guides take (one each, where every guide fits its page), for the tests. */
	public int guidePageCount() {
		return guidePages.size();
	}

	private static String tabKey(Part p) {
		return "gui.extremedimensions.journal.tab." + p.name().toLowerCase(java.util.Locale.ROOT);
	}

	/** Lays one entry's blocks out over pages: a block that would run over starts the next page, if it fits on one. */
	private void paginate(String title, List<List<Line>> blocks) {
		int cap = LINES_PER_PAGE - 2; // the title takes room on the first page
		List<Line> cur = new ArrayList<>();
		for (List<Line> b : blocks) {
			if (!cur.isEmpty() && cur.size() + b.size() > cap && b.size() <= LINES_PER_PAGE) {
				pages.add(new Page(title, cur));
				title = null;
				cur = new ArrayList<>();
				cap = LINES_PER_PAGE;
			}
			for (Line l : b) {
				if (cur.size() >= cap) {
					pages.add(new Page(title, cur));
					title = null;
					cur = new ArrayList<>();
					cap = LINES_PER_PAGE;
				}
				if (cur.isEmpty() && l.text().isEmpty()) {
					continue; // no blank line at the head of a page
				}
				cur.add(l);
			}
		}
		pages.add(new Page(title, cur));
	}

	private void show(Part p) {
		part = p;
		spread = 0;
		flipAt = System.currentTimeMillis();
		this.init();
	}

	/** Turns to the last spread of the found pages (for the tests: the newest page is there). */
	public void toLastPage() {
		if (part != Part.PAGES) {
			show(Part.PAGES);
		}
		spread = Math.max(0, (count() - 1) & ~1);
		flipAt = 0;
		updateButtons();
	}

	private void flip(int dir) {
		int np = spread + dir;
		if (np >= 0 && np < count()) {
			spread = np;
			flipAt = System.currentTimeMillis();
		}
		updateButtons();
	}

	/**
	 * Keys (3.55.0): the journal's own key closes it; the arrow keys (and Page Up, Page Down) turn the pages, Home goes
	 * back to the part's first spread, and Tab (Shift+Tab back) moves on to the next tab. Escape closes it as ever.
	 */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (ModKeys.closes(this, ModKeys.JOURNAL, event)) {
			return true;
		}
		int key = event.key();
		if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_UP || key == InputConstants.KEY_PAGEUP) {
			flip(-2);
			return true;
		}
		if (key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_DOWN || key == InputConstants.KEY_PAGEDOWN) {
			flip(2);
			return true;
		}
		if (key == InputConstants.KEY_HOME) {
			turnTo(0);
			return true;
		}
		if (key == InputConstants.KEY_TAB) {
			Part[] parts = java.util.Arrays.stream(Part.values()).filter(this::has).toArray(Part[]::new);
			int at = java.util.Arrays.asList(parts).indexOf(part);
			show(parts[(at + (ModKeys.shift(event) ? parts.length - 1 : 1)) % parts.length]);
			return true;
		}
		return super.keyPressed(event);
	}

	/** The mouse wheel turns the pages (3.55.0): away from the reader to go on, towards them to go back. */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			flip(scrollY < 0 ? 2 : -2);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** Turns straight to the spread holding this page of the part shown. */
	private void turnTo(int page) {
		int np = Math.max(0, Math.min(page, count() - 1)) & ~1;
		if (np != spread) {
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
		// the contents' lines on the spread they stand on, the anchors' switch on the anchors
		for (ContentsLine c : contents) {
			c.visible = part == Part.GUIDES && (c.page & ~1) == spread;
		}
		if (share != null) {
			share.visible = part == Part.ANCHORS;
		}
		remember();
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
		pageInk = pe * fe;
		String counter;
		String tip = null;
		// (a fall on the ledger's page of the last falls, under the mouse: its whole card, line by line)
		List<String> fallTip = null;
		switch (part) {
			case BEASTS -> {
				Beast b = beasts.get(Math.min(beasts.size() - 1, spread / 2));
				beastLeft(g, b, bx, by, pe * fe, mouseX, mouseY);
				beastRight(g, b, bx + PAGE_W + SPINE, by, pe * fe);
				long seen = beasts.stream().filter(Beast::seen).count();
				counter = Ui.tr("gui.extremedimensions.bestiary.found", seen, beasts.size());
			}
			case LEDGER -> {
				fallTip = ledgerSpread(g, bx, by, pe * fe, mouseX, mouseY);
				counter = Ui.tr("gui.extremedimensions.journal.tab.ledger");
			}
			case CLAN -> {
				tip = clanSpread(g, bx, by, pe * fe, mouseX, mouseY);
				counter = clan.isEmpty() ? Ui.tr("gui.extremedimensions.clan.none")
						: Ui.tr("gui.extremedimensions.journal.clan.count", clan.size(), clanRules[1]);
			}
			case PATH -> {
				pathSpread(g, bx, by, pe * fe);
				counter = Ui.tr("gui.extremedimensions.path.walked", Integer.bitCount(pathDone & ((1 << Path.Step.values().length) - 1)),
						Path.Step.values().length);
			}
			case GUIDES -> {
				guidePage(g, spread, bx, by, pe * fe);
				guidePage(g, spread + 1, bx + PAGE_W + SPINE, by, pe * fe);
				counter = Ui.tr("gui.extremedimensions.journal.guides.count", GUIDE_IDS.length);
			}
			case ANCHORS -> {
				keptSpread(g, bx, by, pe * fe);
				counter = kept.isEmpty() ? Ui.tr("gui.extremedimensions.journal.anchors.count_none")
						: Ui.tr("gui.extremedimensions.journal.anchors.count", keptTotal);
			}
			default -> {
				if (pages.isEmpty()) {
					String s = Ui.tr("gui.extremedimensions.journal.blank");
					Ui.ink(g, this.font, s, bx + PAGE_W / 2 - this.font.width(s) / 2, by + 80, 0x6A5A48, pe);
				} else {
					writePage(g, spread, bx, by, pe * fe);
					writePage(g, spread + 1, bx + PAGE_W + SPINE, by, pe * fe);
				}
				counter = Ui.tr("gui.extremedimensions.journal.found", unlocked.size(), JournalEntry.total());
			}
		}
		if (by - 20 >= 0) {
			g.text(this.font, counter, this.width / 2 - this.font.width(counter) / 2, by - 20, Ui.argb(pe, Ui.MUTED));
		}
		if (tip != null) {
			List<String> lines = Ui.wrap(this.font, tip, 180);
			Ui.tooltip(g, this.font, lines, java.util.Collections.nCopies(lines.size(), 0xFFFFFF), mouseX, mouseY, this.width, this.height, Ui.SOUL_DIM);
		}
		if (fallTip != null) {
			List<String> lines = new ArrayList<>();
			for (String line : fallTip) {
				lines.addAll(Ui.wrap(this.font, line, 220));
			}
			Ui.tooltip(g, this.font, lines, java.util.Collections.nCopies(lines.size(), 0xFFFFFF), mouseX, mouseY, this.width, this.height, Ui.SOUL_DIM);
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
		for (Line line : p.lines()) {
			if (line.note() && !line.text().isEmpty()) {
				// a thin stroke down the margin, as a hand marks what it wrote beside a page
				g.fill(x + 13, ty - 1, x + 14, ty + 9, Ui.argb(alpha * 0.55, INK_RUST));
			}
			Ui.ink(g, this.font, line.text(), x + 12 + (line.note() ? NOTE_INDENT : 0), ty, line.own() ? INK_OWN : line.rust() ? INK_RUST : INK, alpha);
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
			case CAVE_LURKER -> ModEntities.CAVE_LURKER;
			case SCULK_LURKER -> ModEntities.SCULK_LURKER;
			case MAGPIE -> ModEntities.MAGPIE;
			case WICK -> ModEntities.WICK;
			case TIDEWALKER -> ModEntities.TIDEWALKER;
			case GATEBREAKER -> ModEntities.GATEBREAKER;
			case LOCKWARDEN -> ModEntities.LOCKWARDEN;
			case THREADBITER -> ModEntities.THREADBITER;
			case PALE_HAULER -> ModEntities.PALE_HAULER;
			case PALE_MASON -> ModEntities.PALE_MASON;
			case EDGECRAWLER -> ModEntities.EDGECRAWLER;
			case CRAGBACK -> ModEntities.CRAGBACK;
			case HOLLOWLING -> ModEntities.HOLLOWLING;
			case KEEPER -> ModEntities.KEEPER;
			case STARVED_ONE -> ModEntities.STARVED_ONE;
			case THINNED -> ModEntities.THINNED;
			case VEIL_RAY, FRAYED_RAY -> ModEntities.VEIL_RAY;
			case MATRIARCH -> ModEntities.MATRIARCH;
			case FERRYMAN -> ModEntities.FERRYMAN;
			case LAST_WEAVER -> ModEntities.WEAVER;
			case KNOT_MOTHER -> ModEntities.KNOT_MOTHER;
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
					if (b == Bestiary.Beast.FRAYED_RAY && e instanceof com.extremedimensions.seam.VeilRay ray) {
						ray.setFrayed(true);
					}
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

	// ------------------------------------------------------------------ the path

	/**
	 * The left page: the next step and how to take it. The right page: the Wanderer's path, step by step (done, next,
	 * to come), and beside it what has been found of the things that make it easier or stir along it.
	 */
	private void pathSpread(GuiGraphicsExtractor g, int bx, int by, double alpha) {
		Path.Step next = nextStep();
		title(g, Ui.tr("gui.extremedimensions.path.title"), bx, by, alpha);
		int ty = by + 36;
		// With the path walked, the world is not finished until its tapestry is
		boolean woven = (sideDone & 1 << Path.Side.TAPESTRY.ordinal()) != 0;
		String head = Ui.tr(next != null ? "gui.extremedimensions.path.next"
				: woven ? "gui.extremedimensions.path.walked_all" : "gui.extremedimensions.path.unfinished");
		Ui.ink(g, this.font, head, bx + 12, ty, INK_TEAL, alpha);
		g.fill(bx + 12, ty + 9, bx + 12 + this.font.width(head), ty + 10, Ui.argb(alpha * 0.5, INK_TEAL));
		ty += 14;
		if (next != null) {
			for (String line : Ui.wrap(this.font, Ui.tr("gui.extremedimensions.path.step." + next.key()), TEXT_W)) {
				Ui.ink(g, this.font, line, bx + 12, ty, INK_DARK, alpha);
				ty += 11;
			}
			ty += 4;
		}
		// (a line apart as the game's tooltips have them, so the longest hint fits the page)
		for (String line : Ui.wrap(this.font, Ui.tr(pathHint.isEmpty() ? "gui.extremedimensions.guide.1" : pathHint), TEXT_W)) {
			if (ty > by + PAGE_H - 14) {
				break;
			}
			Ui.ink(g, this.font, line, bx + 12, ty, INK, alpha);
			ty += 10;
		}

		int x = bx + PAGE_W + SPINE;
		title(g, Ui.tr("gui.extremedimensions.path.steps"), x, by, alpha);
		ty = by + 34;
		for (Path.Step s : Path.Step.values()) {
			boolean done = (pathDone & 1 << s.ordinal()) != 0;
			String mark = done ? "\u2714 " : s == next ? "\u00bb " : "\u00b7 ";
			Ui.ink(g, this.font, mark + Ui.tr("gui.extremedimensions.path.step." + s.key()), x + 12, ty,
					done ? INK : s == next ? INK_DARK : INK_FAINT, alpha);
			ty += 9;
		}
		ty += 4;
		String beside = Ui.tr("gui.extremedimensions.path.beside");
		Ui.ink(g, this.font, beside, x + 12, ty, INK_TEAL, alpha);
		g.fill(x + 12, ty + 9, x + 12 + this.font.width(beside), ty + 10, Ui.argb(alpha * 0.5, INK_TEAL));
		ty += 13;
		int seen = 0, all = Path.Side.values().length;
		for (Path.Side s : Path.Side.values()) {
			if ((sideSeen & 1 << s.ordinal()) == 0) {
				continue;
			}
			seen++;
			boolean done = (sideDone & 1 << s.ordinal()) != 0;
			Ui.ink(g, this.font, (done ? "\u2714 " : "\u00b7 ") + Ui.tr("gui.extremedimensions.path.side." + s.key()), x + 12, ty,
					done ? INK : INK_DARK, alpha);
			ty += 9;
		}
		if (seen < all) {
			Ui.ink(g, this.font, Ui.tr(seen == 0 ? "gui.extremedimensions.path.beside_none" : "gui.extremedimensions.path.beside_more"),
					x + 12, ty, INK_FAINT, alpha);
		}
	}

	// ------------------------------------------------------------------ the ledger

	/**
	 * The ledger's spreads: the road and the burden, then the others and the closing line. Where the last falls are kept
	 * (3.55.0), they take the others' facing page and run on to a third spread before the closing line. Returns the card
	 * of the fall under the mouse, line by line, or null.
	 */
	private List<String> ledgerSpread(GuiGraphicsExtractor g, int bx, int by, double alpha, int mouseX, int mouseY) {
		boolean falls = ledger.containsKey("falls");
		boolean more = ledger.getOrDefault("falls", List.of()).size() > FALLS_FIRST_PAGE;
		String[] order = spread == 0 ? new String[] {"road", "burden"} : spread == 2 ? new String[] {"others", falls ? "falls" : null}
				: more ? new String[] {"falls_more", null} : new String[] {null, "blank"};
		List<String> tip = null;
		for (int side = 0; side < 2; side++) {
			int x = bx + side * (PAGE_W + SPINE);
			String name = order[side];
			if ("blank".equals(name)) {
				// (the book's last page, left blank after the closing line)
				number(g, spread + side, x, by, alpha);
				continue;
			}
			if (name != null && name.startsWith("falls")) {
				List<String> under = fallsPage(g, x, by, alpha, "falls".equals(name) ? 0 : FALLS_FIRST_PAGE, mouseX, mouseY);
				tip = under != null ? under : tip;
				number(g, spread + side, x, by, alpha);
				continue;
			}
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
				ty = row(g, x, ty, Ui.tr("gui.extremedimensions.ledger." + row[0]), INK, value(row[0], row[1]), INK_DARK, alpha);
			}
			number(g, spread + side, x, by, alpha);
		}
		return tip;
	}

	/** How many of the last falls the first of their pages holds (the rest are on the next). */
	private static final int FALLS_FIRST_PAGE = 3;

	/**
	 * A page of the last falls (3.55.0), from the {@code from}th: for each, newest first, its title, how long ago and how
	 * long and far it went, and what it cost (two lines at most). Returns the whole card of the one under the mouse, or
	 * null.
	 */
	private List<String> fallsPage(GuiGraphicsExtractor g, int x, int by, double alpha, int from, int mouseX, int mouseY) {
		title(g, Ui.tr("gui.extremedimensions.ledger.page.falls"), x, by, alpha);
		List<String[]> falls = ledger.getOrDefault("falls", List.of());
		int ty = by + 34;
		List<String> tip = null;
		for (int i = from; i < falls.size() && i < (from == 0 ? FALLS_FIRST_PAGE : Integer.MAX_VALUE) && ty < by + PAGE_H - 50; i++) {
			String kept = falls.get(i)[1];
			com.extremedimensions.network.ModPackets.RecapPayload r = com.extremedimensions.expedition.RecapText.read(kept);
			long age = com.extremedimensions.expedition.RecapText.head(kept);
			if (r == null || age < 0) {
				continue;
			}
			int top = ty;
			String head = com.extremedimensions.client.hud.CollapseRecap.tr(com.extremedimensions.expedition.RecapText.title(r));
			Ui.ink(g, this.font, Ui.ellipsize(this.font, head, TEXT_W), x + 12, ty, INK_DARK, alpha);
			ty += 10;
			String when = Ui.tr("gui.extremedimensions.ledger.falls.when", CursesScreen.ago(age),
					com.extremedimensions.expedition.RecapText.time(r.seconds()), r.farthest());
			Ui.ink(g, this.font, Ui.ellipsize(this.font, when, TEXT_W), x + 12, ty, INK_FAINT, alpha);
			ty += 10;
			String cost = com.extremedimensions.client.hud.CollapseRecap.cost(r);
			List<String> lines = Ui.wrap(this.font, cost, TEXT_W);
			for (int k = 0; k < lines.size() && k < 2; k++) {
				String line = k == 1 && lines.size() > 2 ? Ui.ellipsize(this.font, lines.get(k) + " ...", TEXT_W) : lines.get(k);
				Ui.ink(g, this.font, line, x + 12, ty, INK, alpha);
				ty += 10;
			}
			if (mouseX >= x + 12 && mouseX < x + PAGE_W - 12 && mouseY >= top && mouseY < ty) {
				tip = List.of(head,
						Ui.tr("gui.extremedimensions.recap.away") + ": " + com.extremedimensions.client.hud.CollapseRecap.tr(com.extremedimensions.expedition.RecapText.away(r)),
						Ui.tr("gui.extremedimensions.recap.far") + ": " + com.extremedimensions.client.hud.CollapseRecap.tr(com.extremedimensions.expedition.RecapText.far(r)),
						Ui.tr("gui.extremedimensions.recap.why") + ": " + com.extremedimensions.client.hud.CollapseRecap.tr(com.extremedimensions.expedition.RecapText.why(r)),
						Ui.tr("gui.extremedimensions.recap.cost") + ": " + cost);
			}
			// a faint rule between two falls
			for (int dx = x + 12; dx < x + PAGE_W - 12; dx += 3) {
				g.fill(dx, ty + 1, dx + 1, ty + 2, Ui.argb(alpha * 0.35, INK_FAINT));
			}
			ty += 5;
		}
		return tip;
	}

	/**
	 * One ruled row: the label on the left (wrapped where the value leaves too little room), the value on the right of
	 * its last line, and a dotted leader under it. Returns where the next row starts.
	 */
	private int row(GuiGraphicsExtractor g, int x, int ty, String label, int labelInk, String value, int valueInk, double alpha) {
		int vw = this.font.width(value);
		for (String line : Ui.wrap(this.font, label, TEXT_W - vw - 6)) {
			Ui.ink(g, this.font, line, x + 12, ty, labelInk, alpha);
			ty += 10;
		}
		Ui.ink(g, this.font, value, x + PAGE_W - 12 - vw, ty - 10, valueInk, alpha);
		for (int dx = x + 12; dx < x + PAGE_W - 12; dx += 3) {
			g.fill(dx, ty - 1, dx + 1, ty, Ui.argb(alpha * 0.35, INK_FAINT));
		}
		return ty + 3;
	}

	/** A heading inside a page, underlined in the teal ink. Returns where the text under it starts. */
	private int heading(GuiGraphicsExtractor g, String text, int x, int ty, double alpha) {
		Ui.ink(g, this.font, text, x + 12, ty, INK_TEAL, alpha);
		g.fill(x + 12, ty + 9, x + 12 + this.font.width(text), ty + 10, Ui.argb(alpha * 0.5, INK_TEAL));
		return ty + 14;
	}

	/** Text wrapped to the page from an indent, not past its foot. Returns where the next line starts. */
	private int para(GuiGraphicsExtractor g, String text, int x, int ty, int indent, int ink, double alpha, int foot) {
		for (String line : Ui.wrap(this.font, text, TEXT_W - indent)) {
			if (ty > foot) {
				break;
			}
			Ui.ink(g, this.font, line, x + 12 + indent, ty, ink, alpha);
			ty += 10;
		}
		return ty;
	}

	// ------------------------------------------------------------------ the clan

	/**
	 * The left page: the player's clan (each member with their standing and where they are), and how the tribe sees the
	 * player now; or, for one of no clan, how a clan is bound. The right page: the cord's law, with this world's numbers,
	 * and when the player may leave; each rule says more under the cursor. Returns what the cursor is over (or null).
	 */
	private String clanSpread(GuiGraphicsExtractor g, int bx, int by, double alpha, int mouseX, int mouseY) {
		int[] r = clanRules;
		int foot = by + PAGE_H - 24;
		title(g, Ui.tr("gui.extremedimensions.journal.clan.title"), bx, by, alpha);
		int ty = by + 34;
		if (clan.isEmpty()) {
			ty = heading(g, Ui.tr("gui.extremedimensions.clan.none"), bx, ty, alpha);
			boolean trusted = atLeastTrusted(ownLevel);
			String[] steps = {
				Ui.tr("gui.extremedimensions.journal.clan.step1", standing(ownLevel, ownRep)),
				Ui.tr("gui.extremedimensions.journal.clan.step2", r[0]),
				Ui.tr("gui.extremedimensions.journal.clan.step3"),
				Ui.tr("gui.extremedimensions.journal.clan.step4", r[2])};
			for (int i = 0; i < steps.length; i++) {
				if (ty > foot) {
					break;
				}
				String mark = i == 0 && trusted ? "\u2714" : (i + 1) + ".";
				Ui.ink(g, this.font, mark, bx + 12, ty, i == 0 && trusted ? INK_TEAL : INK_DARK, alpha);
				ty = para(g, steps[i], bx, ty, 10, i == 0 && !trusted ? INK_RUST : INK, alpha, foot) + 3;
			}
		} else {
			ty = heading(g, Ui.tr("gui.extremedimensions.journal.clan.bound", clan.size(), r[1]), bx, ty, alpha);
			// Each member on two lines: a mark and the name, where they are beside it; their standing under it
			int fits = Math.max(1, (foot - 40 - ty) / 21);
			int shown = clan.size() > fits ? fits - 1 : clan.size();
			for (int i = 0; i < shown; i++) {
				Member m = clan.get(i);
				int ink = m.where() == 0 ? INK_FAINT : m.where() == 2 ? INK_TEAL : m.where() == 3 ? INK_RUST : INK_DARK;
				g.fill(bx + 12, ty + 2, bx + 16, ty + 6, Ui.argb(alpha, ink));
				Ui.ink(g, this.font, m.name(), bx + 20, ty, m.where() == 0 ? INK_FAINT : INK_DARK, alpha);
				String where = Ui.tr("gui.extremedimensions.journal.clan.where." + m.where());
				int ww = this.font.width(where);
				if (this.font.width(m.name()) + ww + 6 <= TEXT_W - 8) {
					Ui.ink(g, this.font, where, bx + PAGE_W - 12 - ww, ty, m.where() == 1 ? INK_FAINT : ink, alpha);
				}
				Ui.ink(g, this.font, standing(m.level(), m.rep()), bx + 20, ty + 10, m.where() == 0 ? INK_FAINT : INK, alpha);
				for (int dx = bx + 12; dx < bx + PAGE_W - 12; dx += 3) {
					g.fill(dx, ty + 19, dx + 1, ty + 20, Ui.argb(alpha * 0.35, INK_FAINT));
				}
				ty += 21;
			}
			if (shown < clan.size()) {
				Ui.ink(g, this.font, Ui.tr("gui.extremedimensions.journal.clan.more", clan.size() - shown), bx + 20, ty + 4, INK_FAINT, alpha);
				ty += 21;
			}
			ty += 4;
			ty = heading(g, Ui.tr("gui.extremedimensions.journal.clan.seen"), bx, ty, alpha);
			boolean held = judgedRep < ownRep;
			Ui.ink(g, this.font, standing(judgedLevel, judgedRep), bx + 12, ty, held ? INK_RUST : INK_DARK, alpha);
			ty += 11;
			para(g, held ? Ui.tr("gui.extremedimensions.journal.clan.held", standing(ownLevel, ownRep)) : Ui.tr("gui.extremedimensions.journal.clan.own"),
					bx, ty, 0, INK_FAINT, alpha, foot);
		}
		number(g, 0, bx, by, alpha);

		int x = bx + PAGE_W + SPINE;
		title(g, Ui.tr("gui.extremedimensions.journal.clan.law"), x, by, alpha);
		ty = by + 34;
		String[][] law = {
			{"max", String.valueOf(r[1])},
			{"blood", Ui.tr("gui.extremedimensions.journal.clan.health", r[2])},
			{"loss", r[3] + "%"},
			{"gain", r[4] + "%"},
			{"range", r[5] <= 0 ? "\u2014" : r[5] + " m"},
			{"lock", r[6] + " min"},
			{"leave", r[7] <= 0 ? "0" : "-" + r[7]},
			{"curses", String.valueOf(r[8])},
			{"left", r[9] <= 0 ? "0" : "-" + r[9]}};
		String tip = null;
		for (String[] row : law) {
			int top = ty;
			ty = row(g, x, ty, Ui.tr("gui.extremedimensions.journal.clan.rule." + row[0]), INK, row[1], INK_DARK, alpha);
			if (mouseX >= x + 12 && mouseX < x + PAGE_W - 12 && mouseY >= top - 1 && mouseY < ty - 2) {
				tip = Ui.tr("gui.extremedimensions.journal.clan.rule." + row[0] + ".desc");
			}
		}
		ty += 4;
		String close = clan.isEmpty() ? Ui.tr("gui.extremedimensions.journal.clan.shelter")
				: lockedFor > 0 ? Ui.tr("gui.extremedimensions.journal.clan.fresh", lockedFor) : Ui.tr("gui.extremedimensions.journal.clan.free");
		para(g, close, x, ty, 0, !clan.isEmpty() && lockedFor > 0 ? INK_RUST : INK_FAINT, alpha, by + PAGE_H - 24);
		number(g, 1, x, by, alpha);
		return tip;
	}

	// ------------------------------------------------------------------ the guides

	/**
	 * One page of the guides: the title on a guide's first page, its numbered steps, and on its last the line on the wiki.
	 * The first spread is their contents (its lines are widgets, see {@link ContentsLine}); the hints follow the guides.
	 */
	private void guidePage(GuiGraphicsExtractor g, int index, int x, int y, double alpha) {
		if (index < contentsPages) {
			if (index == 0) {
				title(g, Ui.tr("gui.extremedimensions.journal.contents"), x, y, alpha);
			}
			number(g, index, x, y, alpha);
			return;
		}
		if (index - contentsPages >= guidePages.size()) {
			return;
		}
		GuidePage p = guidePages.get(index - contentsPages);
		if (p.title() != null) {
			title(g, p.title(), x, y, alpha);
		}
		for (GuideLine l : p.lines()) {
			if (l.head()) {
				Ui.ink(g, this.font, l.text(), x + GUIDE_X, y + l.y(), INK_TEAL, alpha);
				continue;
			}
			if (!l.mark().isEmpty()) {
				Ui.ink(g, this.font, l.mark(), x + GUIDE_X, y + l.y(), INK_TEAL, alpha);
			}
			Ui.ink(g, this.font, l.text(), x + GUIDE_X + GUIDE_INDENT, y + l.y(), INK, alpha);
		}
		if (p.last()) {
			List<String> wiki = Ui.wrap(this.font, Ui.tr("gui.extremedimensions.journal.guide.wiki"), GUIDE_W);
			int ty = y + PAGE_H - 24 - GUIDE_LINE * (wiki.size() - 1);
			for (String line : wiki) {
				Ui.ink(g, this.font, line, x + GUIDE_X, ty, INK_FAINT, alpha);
				ty += GUIDE_LINE;
			}
		}
		number(g, index, x, y, alpha);
	}

	// ------------------------------------------------------------------ the anchors

	/**
	 * Both pages: the anchors that keep charge for the player, two to a page, the Nether's first and the fullest first;
	 * or, where none does, how one is made. Past the last one, how many more there are than the journal was sent.
	 */
	private void keptSpread(GuiGraphicsExtractor g, int bx, int by, double alpha) {
		for (int side = 0; side < 2; side++) {
			int x = bx + side * (PAGE_W + SPINE), page = spread + side, from = page * KEPT_PER_PAGE;
			// (on the right page clear of the switch that opens them to allies, at its foot)
			int foot = by + PAGE_H - (share != null && side == 1 ? 42 : 24);
			if (kept.isEmpty() && side == 0) {
				title(g, Ui.tr("gui.extremedimensions.journal.anchors.title"), x, by, alpha);
				int ty = heading(g, Ui.tr("gui.extremedimensions.journal.anchors.none"), x, by + 34, alpha);
				para(g, Ui.tr("gui.extremedimensions.journal.anchors.how"), x, ty, 0, INK_FAINT, alpha, foot);
			} else if (from < kept.size()) {
				title(g, Ui.tr("gui.extremedimensions.journal.anchors.title"), x, by, alpha);
				int ty = by + 34;
				for (int i = from; i < Math.min(kept.size(), from + KEPT_PER_PAGE); i++) {
					keptEntry(g, kept.get(i), x, ty, alpha);
					ty += KEPT_SLOT;
				}
				if (from + KEPT_PER_PAGE >= kept.size() && keptTotal > kept.size()) {
					para(g, Ui.tr("gui.extremedimensions.journal.anchors.more", keptTotal - kept.size()), x, ty, 0, INK_FAINT, alpha, foot);
				}
			}
			number(g, page, x, by, alpha);
		}
	}

	/**
	 * One anchor in five lines at most: a mark in its world's colour, its kind and the charge it holds for the player;
	 * where it stands; whether it is steady or cracked (and when it breaks); and whom it is shared with.
	 */
	private void keptEntry(GuiGraphicsExtractor g, Kept a, int x, int top, double alpha) {
		String kind = Ui.tr(a.nether() ? "block.extremedimensions.ember_lodestone" : "block.extremedimensions.void_lodestone");
		String charge = Ui.time(a.charge());
		g.fill(x + 12, top + 2, x + 16, top + 6, Ui.argb(alpha, a.nether() ? INK_EMBER : INK_VOID));
		Ui.ink(g, this.font, kind, x + 20, top, INK_DARK, alpha);
		Ui.ink(g, this.font, charge, x + PAGE_W - 12 - this.font.width(charge), top, INK_TEAL, alpha);
		Ui.ink(g, this.font, Ui.tr("gui.extremedimensions.journal.anchors.at", a.x(), a.y(), a.z()), x + 20, top + 10, INK, alpha);
		boolean cracked = a.cracked() > 0;
		// (blocked, 3.55.0: said beside a crack, or in place of "steady")
		String state = cracked && a.blocked() ? Ui.tr("gui.extremedimensions.journal.anchors.cracked_blocked", Ui.time(a.cracked()))
				: cracked ? Ui.tr("gui.extremedimensions.journal.anchors.cracked", Ui.time(a.cracked()))
				: a.blocked() ? Ui.tr("gui.extremedimensions.journal.anchors.blocked")
				: Ui.tr("gui.extremedimensions.journal.anchors.steady");
		int ty = para(g, state, x, top + 20, 8, cracked || a.blocked() ? INK_RUST : INK_FAINT, alpha, top + 30);
		para(g, shared(a), x, ty, 8, INK, alpha, top + 40);
		for (int dx = x + 12; dx < x + PAGE_W - 12; dx += 3) {
			g.fill(dx, top + 52, dx + 1, top + 53, Ui.argb(alpha * 0.35, INK_FAINT));
		}
	}

	/**
	 * Whom an anchor is shared with: the allies the player opened their charge in it to, the allies who opened theirs in
	 * it to the player, everyone (charge an older world kept for all), or no one.
	 */
	private String shared(Kept a) {
		List<String> with = new ArrayList<>();
		if (a.own() > 0 && keptOpen) {
			with.add(keptAllies.isEmpty() ? Ui.tr("gui.extremedimensions.journal.anchors.open_all")
					: Ui.tr("gui.extremedimensions.journal.anchors.open", names(keptAllies)));
		}
		if (!a.lenders().isEmpty()) {
			with.add(Ui.tr("gui.extremedimensions.journal.anchors.lent", names(a.lenders())));
		}
		if (a.everyone()) {
			with.add(Ui.tr("gui.extremedimensions.journal.anchors.everyone"));
		}
		return with.isEmpty() ? Ui.tr("gui.extremedimensions.journal.anchors.alone") : String.join(" ", with);
	}

	/** Names as the server joins them ("a/b/+2"), as they are read ("a, b +2"). */
	private static String names(String joined) {
		return joined.replace("/+", " +").replace("/", ", ");
	}

	private static boolean atLeastTrusted(String level) {
		try {
			return Standing.Level.valueOf(level.toUpperCase(java.util.Locale.ROOT)).ordinal() >= Standing.Level.TRUSTED.ordinal();
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/** A standing as it is written: the level by name, the number beside it. */
	private static String standing(String level, int rep) {
		return Ui.tr("gui.extremedimensions.tribe.level." + level) + " (" + (rep > 0 ? "+" : "") + rep + ")";
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
					return standing(v[0], Integer.parseInt(v[1]));
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

	/**
	 * A line of the guides' contents (3.55.0), in the book's ink: the guide's name (or the hints'), a dotted leader and
	 * the page it starts on; a click turns to it. Page is the contents page it stands on.
	 */
	private final class ContentsLine extends AbstractButton {
		final int page;
		private final int target;
		private final String label;

		ContentsLine(int x, int y, int page, int target, String label) {
			super(x, y, GUIDE_W, CONTENTS_ROW - 1, Component.literal(label));
			this.page = page;
			this.target = target;
			this.label = label;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			turnTo(target);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int x = this.getX(), y = this.getY() + 2, w = this.getWidth();
			boolean on = this.isHovered() || this.isFocused();
			String num = String.valueOf(target + 1);
			int nw = font.width(num);
			String name = Ui.ellipsize(font, label, w - nw - 10);
			int tw = font.width(name);
			Ui.ink(g, font, name, x, y, on ? INK_TEAL : INK_DARK, pageInk);
			Ui.ink(g, font, num, x + w - nw, y, on ? INK_TEAL : INK_FAINT, pageInk);
			for (int dx = x + tw + 3; dx < x + w - nw - 3; dx += 3) {
				g.fill(dx, y + 7, dx + 1, y + 8, Ui.argb(pageInk * 0.35, INK_FAINT));
			}
			if (on) {
				g.fill(x, y + 9, x + tw, y + 10, Ui.argb(pageInk * 0.5, INK_TEAL));
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			out.add(NarratedElementType.TITLE, this.getMessage());
		}
	}

	/**
	 * The Anchors part's switch (3.55.0): whether the player's anchors are open to their allies (they may draw on the
	 * player's charge in them, after their own), as the status screen and {@code /tether share} set it. A click turns it
	 * over and tells the server.
	 */
	private final class ShareToggle extends AbstractButton {
		ShareToggle(int x, int y, int w) {
			super(x, y, w, 13, Component.translatable("gui.extremedimensions.journal.anchors.share"));
			this.setTooltip(Tooltip.create(Component.translatable("gui.extremedimensions.status.share_hint")));
		}

		private String state() {
			return Ui.tr(keptOpen ? "gui.extremedimensions.status.share_on" : "gui.extremedimensions.status.share_off");
		}

		@Override
		public void onPress(InputWithModifiers input) {
			keptOpen = !keptOpen;
			ClientPlayNetworking.send(new ModPackets.ShareAnchorsPayload(keptOpen));
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int x = this.getX(), y = this.getY(), w = this.getWidth(), h = this.getHeight();
			boolean on = this.isHovered() || this.isFocused();
			g.outline(x, y, w, h, Ui.argb(pageInk * (on ? 0.9 : 0.45), on ? INK_TEAL : INK_FAINT));
			// a box ticked while they are open, then what it means
			String mark = keptOpen ? "☑ " : "☐ ";
			String s = Ui.ellipsize(font, mark + state(), w - 8);
			Ui.ink(g, font, s, x + w / 2 - font.width(s) / 2, y + 3, keptOpen ? INK_TEAL : INK_DARK, pageInk);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			out.add(NarratedElementType.TITLE, Component.empty().append(this.getMessage()).append(": " + state()));
		}
	}
}
