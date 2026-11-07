package com.extremedimensions.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.extremedimensions.client.options.ClientOptions;
import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.config.Config;
import com.extremedimensions.config.Preset;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.Penalty.Group;

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
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/**
 * Settings, in two tabs.
 *
 * <ul>
 * <li><b>Display</b>: this player's own options (fewer flashes, colour-blind colours, where the meters and the timer
 * sit, HUD size, the first-expedition guide), kept on their computer.</li>
 * <li><b>World rules</b>: every value of the world's config and every curse's maximum and strength. Everyone can read
 * them; operators (and the owner of a world not open to LAN) can change them, which saves the server's config at once.
 * Opened outside a world (from the mod list), they are this computer's own config.</li>
 * </ul>
 *
 * <p>The world rules are hundreds of values, so (3.34) they open on an overview: a tile for each part of the rules,
 * telling how many of its values differ from their defaults. A tile opens that part alone. Switches are On/Off buttons,
 * a value that differs from its default is marked in gold and a ↺ beside a field puts its default back. "Changed"
 * gathers every value that differs from its default, the search looks through every part at once, and Undo takes back
 * what was typed but not applied. Nothing changes until Apply, and leaving with changes not applied asks first.
 */
public class SettingsScreen extends Screen {
	private static final int ROW = 19, LIST_ROW = 16;
	/** The panel's least and greatest size; it grows with the window between them. */
	private static final int MIN_W = 372, MAX_W = 452, MIN_H = 246, MAX_H = 300;
	/** The overview's columns of tiles (filled down, then across). */
	private static final int COLS = 3;
	/** The part that holds every curse's maximum and strength (the others are the config's sections). */
	private static final String CURSES_PART = "penalties";
	/** The value column: a number's box (or a switch's button), a curse's maximum box beside it, and the ↺. */
	private static final int VALUE_W = 64, MAX_BOX_W = 30, GAP = 6, RESET_W = 11;
	/** How long a first press of Done (with changes not applied) waits for the second. */
	private static final long ARMED_MS = 4000;
	private static final Gson GSON = new Gson();
	/**
	 * Headings inside the two longest parts: where each creature's settings start in the Lurker's part, and each
	 * Elder's (and what they share) in the Elders' part. Setting key to the heading's language key.
	 */
	private static final Map<String, String> SUBHEADS = Map.ofEntries(Map.entry("lurker_enabled", "entity.extremedimensions.lurker"),
			Map.entry("cave_lurker_enabled", "entity.extremedimensions.cave_lurker"), Map.entry("sculk_lurker_enabled", "entity.extremedimensions.sculk_lurker"),
			Map.entry("magpie_enabled", "entity.extremedimensions.magpie"), Map.entry("wick_enabled", "entity.extremedimensions.wick"),
			Map.entry("tidewalker_enabled", "entity.extremedimensions.tidewalker"), Map.entry("gatebreaker_enabled", "entity.extremedimensions.gatebreaker"),
			Map.entry("lockwarden_enabled", "entity.extremedimensions.lockwarden"), Map.entry("threadbiter_enabled", "entity.extremedimensions.threadbiter"),
			Map.entry("edgecrawler_enabled", "entity.extremedimensions.edgecrawler"), Map.entry("cragback_enabled", "entity.extremedimensions.cragback"),
			Map.entry("flee_curses", "gui.extremedimensions.settings.sub.flee"), Map.entry("matriarch_after_keepers", "entity.extremedimensions.matriarch"),
			Map.entry("ferryman_unlock", "entity.extremedimensions.ferryman"), Map.entry("weaver_unlock", "entity.extremedimensions.weaver"),
			Map.entry("knot_mother_relics", "entity.extremedimensions.knot_mother"), Map.entry("weaver_lance_weight", "gui.extremedimensions.settings.sub.lance"),
			Map.entry("elder_speed", "gui.extremedimensions.settings.sub.fights"), Map.entry("matriarch_ridges", "gui.extremedimensions.settings.sub.domains"),
			Map.entry("elder_cores", "gui.extremedimensions.settings.sub.cores"), Map.entry("elder_attack_fx_detail", "gui.extremedimensions.settings.sub.power"),
			Map.entry("matriarch_hex_slow", "gui.extremedimensions.settings.sub.spells"), Map.entry("starved_health", "entity.extremedimensions.starved_one"));

	private enum Tab {
		DISPLAY, WORLD
	}

	/** What the world rules show: the overview's tiles, one part, every changed value, or what the search found. */
	private enum View {
		OVERVIEW, PART, CHANGED, SEARCH
	}

	/**
	 * One line of the world rules: a heading (label holding its short form, for inside its part), a setting, or a curse
	 * (two values). Part is the part it belongs to; sw marks a switch.
	 */
	private record Row(String heading, String key, String label, String tip, EditBox box, EditBox box2, String search, String part,
			boolean sw) {}

	/** One part of the rules (a section of the config, or every curse's numbers), as the overview shows it. */
	private record Part(String id, String name, List<Row> rows) {}

	private final Screen parent;
	private final long openAt = System.currentTimeMillis();
	private Tab tab = Tab.DISPLAY;
	private int pw = MIN_W, ph = MIN_H;
	private final List<Row> rows = new ArrayList<>();
	private final List<Row> shownRows = new ArrayList<>();
	private final List<Part> parts = new ArrayList<>();
	/** Each box's key (curses as penalty.id.max / penalty.id.per_stack). */
	private final Map<EditBox, String> boxKeys = new IdentityHashMap<>();
	/** The part open (null on the overview), and whether "Changed" is open instead. */
	private String partId;
	private boolean changedOnly;
	/** The values as the world has them now, by key (curses as penalty.id.max / penalty.id.per_stack). */
	private final Map<String, String> current = new HashMap<>();
	private final Map<String, String> defaults = new HashMap<>();
	private final List<AbstractButton> displayWidgets = new ArrayList<>();
	private final List<Tile> tiles = new ArrayList<>();
	/** The list's lines: a pool, one per row in view, bound to the rows as the list scrolls. */
	private final List<RowLine> lines = new ArrayList<>();
	private final List<Toggle> toggles = new ArrayList<>();
	private final List<ResetMark> resets = new ArrayList<>();
	private EditBox search;
	private Button apply, undo, reset, done;
	private Link back, changed;
	/** The difficulty preset to apply (cycled through), and when the apply button was first pressed (to confirm). */
	private Preset target = Preset.NORMAL;
	private Choice presetChoice;
	private Button presetApply;
	private long presetArmedAt;
	/** When Done was first pressed with changes not applied (a second press within four seconds leaves without them). */
	private long leaveArmedAt;
	private int scroll;
	private boolean editable;

	/** The settings screen on display, so fresh settings from the server refresh it in place. */
	private static SettingsScreen shown;

	public SettingsScreen(Screen parent) {
		super(Component.translatable("gui.extremedimensions.settings.title"));
		this.parent = parent;
	}

	/** Online, the world's settings come from the server; outside a world they are this computer's config. */
	private static boolean online() {
		return Minecraft.getInstance().getConnection() != null;
	}

	/** Fresh settings from the server while the screen is open. */
	public static void refresh() {
		if (shown != null) {
			shown.takeValues(true);
		}
	}

	/** For the client test: how many On/Off switches the list shows now. */
	public int shownSwitches() {
		int n = 0;
		for (Toggle t : toggles) {
			if (t.visible) {
				n++;
			}
		}
		return n;
	}

	private int x0() {
		return Math.max(2, (this.width - pw) / 2);
	}

	private int y0() {
		return Math.max(4, (this.height - ph) / 2);
	}

	private int listTop() {
		return y0() + 86;
	}

	private int listBottom() {
		return y0() + ph - 32;
	}

	@Override
	protected void init() {
		shown = this;
		pw = Math.max(MIN_W, Math.min(MAX_W, this.width - 12));
		ph = Math.max(MIN_H, Math.min(MAX_H, this.height - 8));
		rows.clear();
		parts.clear();
		boxKeys.clear();
		displayWidgets.clear();
		tiles.clear();
		lines.clear();
		toggles.clear();
		resets.clear();
		int x0 = x0(), y0 = y0();
		this.addRenderableWidget(new TabButton(x0 + 12, y0 + 22, (pw - 30) / 2, Tab.DISPLAY));
		this.addRenderableWidget(new TabButton(x0 + 18 + (pw - 30) / 2, y0 + 22, (pw - 30) / 2, Tab.WORLD));

		// Display
		int cx = x0 + pw - 18 - 124, y = y0 + 48;
		addChoice(cx, y, () -> onOff(ClientOptions.reduceFlashing()), () -> ClientOptions.setReduceFlashing(!ClientOptions.reduceFlashing()),
				"reduce_flashing");
		addChoice(cx, y + ROW, () -> onOff(ClientOptions.colourBlind()), () -> ClientOptions.setColourBlind(!ClientOptions.colourBlind()),
				"colour_blind");
		addChoice(cx, y + ROW * 2, () -> Ui.tr("gui.extremedimensions.settings.corner." + ClientOptions.meters().name().toLowerCase(Locale.ROOT)),
				() -> ClientOptions.setMeters(ClientOptions.Corner.values()[(ClientOptions.meters().ordinal() + 1) % ClientOptions.Corner.values().length]),
				"meters");
		addChoice(cx, y + ROW * 3, () -> Ui.tr("gui.extremedimensions.settings.timer." + ClientOptions.timer().name().toLowerCase(Locale.ROOT)),
				() -> ClientOptions.setTimer(ClientOptions.TimerPlace.values()[(ClientOptions.timer().ordinal() + 1) % ClientOptions.TimerPlace.values().length]),
				"timer");
		addChoice(cx, y + ROW * 4, () -> ClientOptions.hudScale() + "%", () -> {
			int[] s = ClientOptions.HUD_SCALES;
			int i = 0;
			while (i < s.length && s[i] != ClientOptions.hudScale()) {
				i++;
			}
			ClientOptions.setHudScale(s[(i + 1) % s.length]);
		}, "hud_scale");
		addChoice(cx, y + ROW * 5, () -> onOff(ClientOptions.guidance()), () -> ClientOptions.setGuidance(!ClientOptions.guidance()), "guidance");
		addChoice(cx, y + ROW * 6, () -> onOff(ClientOptions.hints()), () -> ClientOptions.setHints(!ClientOptions.hints()), "hints");
		addChoice(cx, y + ROW * 7, () -> Ui.tr("gui.extremedimensions.settings.menu." + ClientOptions.menu().name().toLowerCase(Locale.ROOT)),
				() -> ClientOptions.setMenu(ClientOptions.MenuStyle.values()[(ClientOptions.menu().ordinal() + 1) % ClientOptions.MenuStyle.values().length]),
				"menu");

		// World rules: a difficulty preset at once, then every value
		presetChoice = new Choice(x0 + 108, y0 + 44, 124, 16, () -> Ui.tr(target.nameKey()), () -> {
			var all = Preset.values();
			target = all[(target.ordinal() + 1) % all.length];
			presetArmedAt = 0;
			presetChoice.setTooltip(Tooltip.create(Component.translatable(target.descKey())));
			refreshButtons();
		}, "difficulty");
		presetChoice.setTooltip(Tooltip.create(Component.translatable(target.descKey())));
		this.addRenderableWidget(presetChoice);
		presetApply = this.addRenderableWidget(Button.builder(Component.translatable("gui.extremedimensions.preset.apply"), b -> applyPreset())
				.pos(x0 + 238, y0 + 43).size(pw - 238 - 16, 18).build());
		search = new EditBox(this.font, x0 + 16, y0 + 66, 150, 14, Component.translatable("gui.extremedimensions.settings.search"));
		search.setHint(Component.translatable("gui.extremedimensions.settings.search"));
		search.setMaxLength(40);
		search.setResponder(text -> {
			scroll = 0;
			filter();
		});
		this.addRenderableWidget(search);
		for (Config.Setting st : Config.settings()) {
			defaults.put(st.key(), st.fallback());
		}
		for (Penalty pen : Penalty.values()) {
			defaults.put("penalty." + pen.id + ".max", String.valueOf(pen.defaultMax));
			defaults.put("penalty." + pen.id + ".per_stack", Config.format(pen.defaultValue));
		}

		// The rows, part by part: every section of the config, then every curse's numbers set by set
		String section = null;
		List<Row> partRows = new ArrayList<>();
		for (Config.Setting st : Config.settings()) {
			if (!st.section().equals(section)) {
				section = st.section();
				String name = sectionName(section);
				rows.add(new Row(name, null, name, null, null, null, "", section, false));
				partRows = new ArrayList<>();
				parts.add(new Part(section, name, partRows));
			}
			String sub = SUBHEADS.get(st.key());
			if (sub != null) {
				String name = Ui.tr(sub);
				// in a search the heading names its part too, unless it is the part's own name (The Lurker)
				String full = name.equals(sectionName(section)) ? name : sectionName(section) + ": " + name;
				Row h = new Row(full, null, name, null, null, null, "", section, false);
				rows.add(h);
				partRows.add(h);
			}
			String label = label(st.key());
			EditBox b = box(VALUE_W, st.key());
			Row r = new Row(null, st.key(), label, tip(st.key()), b, null, (label + " " + st.key()).toLowerCase(Locale.ROOT), section,
					Config.isSwitch(st.key()));
			b.setTooltip(Tooltip.create(Component.literal(tipText(r))));
			rows.add(r);
			partRows.add(r);
		}
		List<Row> curseRows = new ArrayList<>();
		parts.add(new Part(CURSES_PART, sectionName(CURSES_PART), curseRows));
		Group group = null;
		for (Penalty pen : Penalty.values()) {
			if (pen.group != group) {
				group = pen.group;
				String set = Ui.tr("gui.extremedimensions.curses.tab_" + group.id());
				Row h = new Row(Ui.tr("gui.extremedimensions.curses.title") + ": " + set, null, set, null, null, null, "", CURSES_PART, false);
				rows.add(h);
				curseRows.add(h);
			}
			String name = Ui.tr(pen.nameKey());
			EditBox max = box(MAX_BOX_W, "penalty." + pen.id + ".max"), per = box(VALUE_W, "penalty." + pen.id + ".per_stack");
			max.setTooltip(Tooltip.create(Component.translatable("gui.extremedimensions.settings.tip_max", name)));
			per.setTooltip(Tooltip.create(Component.translatable("gui.extremedimensions.settings.tip_per_stack", name)));
			Row r = new Row(null, "penalty." + pen.id, name, null, max, per, (name + " " + pen.id).toLowerCase(Locale.ROOT), CURSES_PART, false);
			rows.add(r);
			curseRows.add(r);
		}

		// The overview: a tile for every part, filled down the columns in the config's order
		int down = (parts.size() + COLS - 1) / COLS;
		int area = listBottom() - listTop() + 4;
		int pitch = Math.min(20, area / Math.max(1, down));
		int tw = (pw - 24 - (COLS - 1) * 4) / COLS;
		for (int i = 0; i < parts.size(); i++) {
			int col = i / down, row = i % down;
			tiles.add(this.addRenderableWidget(new Tile(parts.get(i), x0 + 12 + col * (tw + 4), listTop() - 2 + row * pitch + 1, tw, pitch - 2)));
		}
		// The list's lines, as many as fit
		for (int i = 0; i < visibleRows(); i++) {
			lines.add(this.addRenderableWidget(new RowLine()));
			toggles.add(this.addRenderableWidget(new Toggle()));
			resets.add(this.addRenderableWidget(new ResetMark()));
		}
		back = this.addRenderableWidget(new Link(x0 + 172, y0 + 65, 54, "gui.extremedimensions.settings.back",
				() -> "‹ " + Ui.tr("gui.extremedimensions.settings.back"), this::overview));
		changed = this.addRenderableWidget(new Link(x0 + pw - 16 - 112, y0 + 65, 112, "gui.extremedimensions.settings.changed",
				() -> Ui.tr("gui.extremedimensions.settings.changed_n", changedCount()), this::openChanged));
		changed.setTooltip(Tooltip.create(Component.translatable("gui.extremedimensions.settings.changed.desc")));

		apply = this.addRenderableWidget(Button.builder(Component.translatable("gui.extremedimensions.settings.apply"), b -> applyChanges())
				.pos(x0 + 14, y0 + ph - 26).size(96, 18).build());
		apply.setTooltip(Tooltip.create(Component.translatable(online() ? "gui.extremedimensions.settings.world_note_edit"
				: "gui.extremedimensions.settings.world_note_local")));
		undo = this.addRenderableWidget(Button.builder(Component.translatable("gui.extremedimensions.settings.undo"), b -> undo())
				.pos(x0 + 114, y0 + ph - 26).size(70, 18).build());
		undo.setTooltip(Tooltip.create(Component.translatable("gui.extremedimensions.settings.undo.desc")));
		reset = this.addRenderableWidget(Button.builder(Component.translatable("gui.extremedimensions.settings.defaults"), b -> fillDefaults())
				.pos(x0 + 188, y0 + ph - 26).size(80, 18).build());
		reset.setTooltip(Tooltip.create(Component.translatable("gui.extremedimensions.settings.defaults.desc")));
		done = this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> leave())
				.pos(x0 + pw - 14 - 80, y0 + ph - 26).size(80, 18).build());
		takeValues(false);
		if (online()) {
			ClientPlayNetworking.send(new ModPackets.RequestSettingsPayload());
		}
		showTab();
	}

	private void addChoice(int x, int y, Supplier<String> value, Runnable next, String key) {
		Choice c = new Choice(x, y, 124, 16, value, next, key);
		c.setTooltip(Tooltip.create(Component.translatable("gui.extremedimensions.settings." + key + ".desc")));
		displayWidgets.add(this.addRenderableWidget(c));
	}

	private EditBox box(int w, String key) {
		EditBox b = new EditBox(this.font, 0, 0, w, 12, Component.empty());
		b.setMaxLength(12);
		boxKeys.put(b, key);
		b.setResponder(text -> colour(b, text));
		b.visible = false;
		return this.addRenderableWidget(b);
	}

	private static String onOff(boolean on) {
		return Ui.tr(on ? "options.on" : "options.off");
	}

	/** A setting's name: from the language file, or made readable from its key. */
	private static String label(String key) {
		String k = "config.extremedimensions." + key;
		if (Language.getInstance().has(k)) {
			return I18n.get(k);
		}
		String words = key.replace('_', ' ');
		return Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	private static String tip(String key) {
		String k = "config.extremedimensions." + key + ".desc";
		return Language.getInstance().has(k) ? I18n.get(k) : null;
	}

	private static String sectionName(String section) {
		String k = "gui.extremedimensions.settings.section." + section;
		return Language.getInstance().has(k) ? I18n.get(k) : section;
	}

	/** A row's tooltip: its name, what it does, its default and (for a setting) its key in the config file. */
	private String tipText(Row r) {
		StringBuilder s = new StringBuilder(r.label());
		if (r.box2() != null) {
			s.append('\n').append(Ui.tr("gui.extremedimensions.settings.tip_max", r.label()));
			s.append('\n').append(Ui.tr("gui.extremedimensions.settings.tip_per_stack", r.label()));
		} else if (r.tip() != null) {
			s.append('\n').append(r.tip());
		}
		s.append('\n').append(Ui.tr("gui.extremedimensions.settings.default_value", defaultText(r)));
		if (r.box2() == null) {
			s.append("  ·  ").append(r.key());
		}
		return s.toString();
	}

	/** A row's default as the player reads it: On or Off, a number, or a curse's maximum and strength. */
	private String defaultText(Row r) {
		if (r.box2() != null) {
			return Ui.tr("gui.extremedimensions.settings.default_curse", defaults.getOrDefault(r.key() + ".max", "?"),
					defaults.getOrDefault(r.key() + ".per_stack", "?"));
		}
		String d = defaults.getOrDefault(r.key(), "?");
		return r.sw() ? onOff(on(d)) : d;
	}

	/** Takes the world's values; with keepEdits, fields the player changed keep what they typed. */
	private void takeValues(boolean keepEdits) {
		Map<String, String> old = new HashMap<>(current);
		current.clear();
		JsonObject json;
		if (online() && ClientState.settingsJson != null) {
			try {
				JsonElement parsed = JsonParser.parseString(ClientState.settingsJson);
				json = parsed.isJsonObject() ? parsed.getAsJsonObject() : Config.snapshot();
			} catch (Exception e) {
				json = Config.snapshot();
			}
			editable = ClientState.settingsEditable;
		} else {
			json = Config.snapshot();
			editable = !online();
		}
		for (Config.Setting st : Config.settings()) {
			if (json.has(st.key())) {
				current.put(st.key(), st.whole() ? String.valueOf(json.get(st.key()).getAsInt()) : Config.format(json.get(st.key()).getAsDouble()));
			}
		}
		JsonObject pens = json.has("penalties") ? json.getAsJsonObject("penalties") : new JsonObject();
		for (Penalty pen : Penalty.values()) {
			if (pens.has(pen.id)) {
				JsonObject o = pens.getAsJsonObject(pen.id);
				current.put("penalty." + pen.id + ".max", String.valueOf(o.get("max").getAsInt()));
				current.put("penalty." + pen.id + ".per_stack", Config.format(o.get("per_stack").getAsDouble()));
			}
		}
		for (Row r : rows) {
			if (r.key() == null) {
				continue;
			}
			sync(r.box(), old, keepEdits);
			if (r.box2() != null) {
				sync(r.box2(), old, keepEdits);
			}
		}
		filter();
	}

	private void sync(EditBox b, Map<String, String> old, boolean keepEdits) {
		String key = boxKeys.get(b);
		String now = current.getOrDefault(key, "");
		if (!keepEdits || b.getValue().equals(old.getOrDefault(key, ""))) {
			b.setValue(now);
		}
		b.setEditable(editable);
		colour(b, b.getValue());
	}

	/** Unchanged values in the text colour, changed ones in amber, ones that cannot be taken in red. */
	private void colour(EditBox b, String text) {
		String key = boxKeys.get(b);
		int col = key == null || text.equals(current.getOrDefault(key, text)) ? Ui.TEXT : valid(key, text) ? Ui.WARN : Ui.DANGER;
		b.setTextColor(Ui.argb(255, col));
		b.setTextColorUneditable(Ui.argb(255, Ui.MUTED));
		refreshButtons();
	}

	private static boolean valid(String key, String text) {
		String t = text.trim().replace(',', '.');
		boolean whole = key.endsWith(".max");
		for (Config.Setting st : Config.settings()) {
			if (st.key().equals(key)) {
				whole = st.whole();
			}
		}
		try {
			if (whole) {
				int v = Integer.parseInt(t);
				return v >= 0 && (!key.endsWith(".max") || v <= 99);
			}
			double v = Double.parseDouble(t);
			return Double.isFinite(v) && v >= 0;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/** Two values as numbers (so "2" and "2.0" are one); text that is no number only equals itself. */
	private static boolean same(String a, String b) {
		if (a == null || b == null) {
			return true;
		}
		try {
			return Double.parseDouble(a.trim().replace(',', '.')) == Double.parseDouble(b.trim().replace(',', '.'));
		} catch (NumberFormatException e) {
			return a.trim().equals(b.trim());
		}
	}

	private static boolean on(String text) {
		try {
			return Double.parseDouble(text.trim().replace(',', '.')) != 0;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/** Whether the world has this row's value at something else than its default (marked in gold). */
	private boolean differs(Row r) {
		if (r.key() == null) {
			return false;
		}
		if (r.box2() == null) {
			return !same(current.get(r.key()), defaults.get(r.key()));
		}
		return !same(current.get(r.key() + ".max"), defaults.get(r.key() + ".max"))
				|| !same(current.get(r.key() + ".per_stack"), defaults.get(r.key() + ".per_stack"));
	}

	/** Whether this row's field holds something else than the world has (a change not applied yet, in yellow). */
	private boolean pending(Row r) {
		if (r.key() == null) {
			return false;
		}
		boolean one = !r.box().getValue().equals(current.getOrDefault(boxKeys.get(r.box()), r.box().getValue()));
		return one || r.box2() != null && !r.box2().getValue().equals(current.getOrDefault(boxKeys.get(r.box2()), r.box2().getValue()));
	}

	/** Whether this row's field holds something else than its default (so its ↺ has something to put back). */
	private boolean offDefault(Row r) {
		if (r.key() == null) {
			return false;
		}
		return !same(r.box().getValue(), defaults.get(boxKeys.get(r.box())))
				|| r.box2() != null && !same(r.box2().getValue(), defaults.get(boxKeys.get(r.box2())));
	}

	/** How many values differ from their defaults or wait to be applied, all parts together. */
	private int changedCount() {
		int n = 0;
		for (Row r : rows) {
			if (r.key() != null && (differs(r) || pending(r))) {
				n++;
			}
		}
		return n;
	}

	/** Every changed value that can be taken, by key. */
	private Map<String, String> changes() {
		Map<String, String> out = new HashMap<>();
		for (Row r : rows) {
			if (r.key() == null) {
				continue;
			}
			add(out, boxKeys.get(r.box()), r.box().getValue());
			if (r.box2() != null) {
				add(out, boxKeys.get(r.box2()), r.box2().getValue());
			}
		}
		return out;
	}

	private void add(Map<String, String> out, String key, String text) {
		if (!text.equals(current.getOrDefault(key, text)) && valid(key, text)) {
			out.put(key, text.trim().replace(',', '.'));
		}
	}

	private void applyChanges() {
		Map<String, String> changes = changes();
		if (changes.isEmpty() || !editable) {
			return;
		}
		leaveArmedAt = 0;
		if (online()) {
			ClientPlayNetworking.send(new ModPackets.ChangeSettingsPayload(GSON.toJson(changes)));
		} else {
			changes.forEach(Config::set);
			Config.save();
			takeValues(false);
		}
	}

	/** Applies the chosen difficulty: the first press arms the button, a second one within four seconds applies. */
	private void applyPreset() {
		if (!editable) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - presetArmedAt > ARMED_MS) {
			presetArmedAt = now;
			refreshButtons();
			return;
		}
		presetArmedAt = 0;
		if (online()) {
			JsonObject json = new JsonObject();
			json.addProperty("preset", target.id);
			ClientPlayNetworking.send(new ModPackets.ChangeSettingsPayload(GSON.toJson(json)));
		} else {
			target.values.forEach(Config::set);
			Config.save();
			takeValues(false);
		}
		refreshButtons();
	}

	/** The preset the world's rules match now, or null for custom rules. */
	private Preset currentPreset() {
		return Preset.matching(current);
	}

	/** Puts a row's default back in its field (applied with Apply, like any change). */
	private void toDefault(Row r) {
		r.box().setValue(defaults.getOrDefault(boxKeys.get(r.box()), r.box().getValue()));
		if (r.box2() != null) {
			r.box2().setValue(defaults.getOrDefault(boxKeys.get(r.box2()), r.box2().getValue()));
		}
	}

	/** The defaults into the fields in view: the part open, what was found, or (from the overview) every one. */
	private void fillDefaults() {
		if (!editable) {
			return;
		}
		for (Row r : view() == View.OVERVIEW ? rows : new ArrayList<>(shownRows)) {
			if (r.key() != null) {
				toDefault(r);
			}
		}
	}

	/** Takes back every change not applied yet: each field holds what the world has again. */
	private void undo() {
		for (Row r : rows) {
			if (r.key() == null) {
				continue;
			}
			r.box().setValue(current.getOrDefault(boxKeys.get(r.box()), r.box().getValue()));
			if (r.box2() != null) {
				r.box2().setValue(current.getOrDefault(boxKeys.get(r.box2()), r.box2().getValue()));
			}
		}
		leaveArmedAt = 0;
	}

	private void flip(Row r) {
		if (editable && r != null && r.sw()) {
			r.box().setValue(on(r.box().getValue()) ? "0" : "1");
		}
	}

	private String query() {
		return search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
	}

	private Part part() {
		for (Part p : parts) {
			if (p.id().equals(partId)) {
				return p;
			}
		}
		return null;
	}

	private View view() {
		if (!query().isEmpty()) {
			return View.SEARCH;
		}
		if (changedOnly) {
			return View.CHANGED;
		}
		return part() != null ? View.PART : View.OVERVIEW;
	}

	private void open(String id) {
		partId = id;
		changedOnly = false;
		scroll = 0;
		filter();
	}

	private void openChanged() {
		partId = null;
		changedOnly = true;
		scroll = 0;
		filter();
	}

	private void overview() {
		partId = null;
		changedOnly = false;
		scroll = 0;
		filter();
	}

	/** The rows the view shows: a part's own, or every one that matches (under its part's heading). */
	private void filter() {
		shownRows.clear();
		switch (view()) {
			case SEARCH -> {
				String q = query();
				gather(r -> r.search().contains(q));
			}
			case CHANGED -> gather(r -> differs(r) || pending(r));
			case PART -> shownRows.addAll(part().rows());
			case OVERVIEW -> {
				// the tiles
			}
		}
		layoutList();
		refreshButtons();
	}

	/** Every row that keeps, each part's heading above its first (a heading stays while any of its rows does). */
	private void gather(Predicate<Row> keep) {
		Row heading = null;
		for (Row r : rows) {
			if (r.key() == null) {
				heading = r;
				continue;
			}
			if (keep.test(r)) {
				if (heading != null) {
					shownRows.add(heading);
					heading = null;
				}
				shownRows.add(r);
			}
		}
	}

	private void refreshButtons() {
		if (apply == null || done == null || back == null) {
			return;
		}
		boolean world = tab == Tab.WORLD;
		View v = view();
		int n = changes().size();
		apply.visible = world && editable;
		undo.visible = world && editable;
		reset.visible = world && editable;
		apply.active = n > 0;
		undo.active = n > 0;
		reset.active = editable;
		presetChoice.visible = world && editable;
		presetApply.visible = world && editable;
		boolean armed = System.currentTimeMillis() - presetArmedAt <= ARMED_MS;
		presetApply.active = editable && target != currentPreset();
		presetApply.setMessage(Component.translatable(armed ? "gui.extremedimensions.preset.confirm" : "gui.extremedimensions.preset.apply"));
		apply.setMessage(n > 0 ? Component.translatable("gui.extremedimensions.settings.apply_n", n)
				: Component.translatable("gui.extremedimensions.settings.apply"));
		back.visible = world && (v == View.PART || v == View.CHANGED);
		changed.visible = world && v == View.OVERVIEW;
		for (Tile t : tiles) {
			t.visible = world && v == View.OVERVIEW;
		}
		boolean leaving = n > 0 && System.currentTimeMillis() - leaveArmedAt <= ARMED_MS;
		done.setMessage(Component.translatable(leaving ? "gui.extremedimensions.settings.leave_confirm" : "gui.done"));
		done.setTooltip(leaving ? Tooltip.create(Component.translatable("gui.extremedimensions.settings.leave_confirm.desc", n)) : null);
		marks();
	}

	/** The ↺ beside every field in view that holds something else than its default. */
	private void marks() {
		for (ResetMark m : resets) {
			m.visible = tab == Tab.WORLD && editable && m.row != null && offDefault(m.row);
		}
	}

	private int visibleRows() {
		return (listBottom() - listTop()) / LIST_ROW;
	}

	/** Binds the lines to the rows in view and puts each field in its place. */
	private void layoutList() {
		int vis = visibleRows();
		int max = Math.max(0, shownRows.size() - vis);
		scroll = Math.max(0, Math.min(scroll, max));
		boolean list = tab == Tab.WORLD && view() != View.OVERVIEW;
		for (Row r : rows) {
			if (r.box() != null) {
				r.box().visible = false;
			}
			if (r.box2() != null) {
				r.box2().visible = false;
			}
		}
		int x0 = x0();
		int right = x0 + pw - 22;
		int valueX = right - RESET_W - 2 - VALUE_W;
		int maxX = valueX - GAP - MAX_BOX_W;
		for (int i = 0; i < lines.size(); i++) {
			Row r = list && i < vis && scroll + i < shownRows.size() ? shownRows.get(scroll + i) : null;
			Row setting = r != null && r.key() != null ? r : null;
			int y = listTop() + i * LIST_ROW;
			lines.get(i).bind(setting, x0 + 14, y, (setting != null && setting.box2() != null ? maxX : valueX) - 4 - (x0 + 14));
			toggles.get(i).bind(setting != null && setting.sw() ? setting : null, valueX, y + 2);
			resets.get(i).bind(setting, right - RESET_W + 1, y + 2);
			if (setting == null) {
				continue;
			}
			if (setting.box2() != null) {
				setting.box().setX(maxX);
				setting.box().setY(y + 2);
				setting.box().visible = true;
				setting.box2().setX(valueX);
				setting.box2().setY(y + 2);
				setting.box2().visible = true;
			} else if (!setting.sw()) {
				setting.box().setX(valueX);
				setting.box().setY(y + 2);
				setting.box().visible = true;
			}
		}
		marks();
	}

	private void showTab() {
		boolean world = tab == Tab.WORLD;
		for (AbstractButton b : displayWidgets) {
			b.visible = !world;
		}
		search.visible = world;
		layoutList();
		refreshButtons();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (tab == Tab.WORLD && view() != View.OVERVIEW && scrollY != 0) {
			scroll -= (int) Math.signum(scrollY) * 3;
			layoutList();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** Escape steps back: out of the search, then out of a part to the overview, then out of the screen. */
	@Override
	public void onClose() {
		if (tab == Tab.WORLD) {
			if (!query().isEmpty()) {
				search.setValue("");
				return;
			}
			if (view() != View.OVERVIEW) {
				overview();
				return;
			}
		}
		leave();
	}

	/** Done: with changes not applied, the first press asks (the button says so), a second within four seconds leaves. */
	private void leave() {
		long now = System.currentTimeMillis();
		if (editable && !changes().isEmpty() && now - leaveArmedAt > ARMED_MS) {
			leaveArmedAt = now;
			refreshButtons();
			return;
		}
		if (shown == this) {
			shown = null;
		}
		Minecraft.getInstance().gui.setScreen(parent);
	}

	public boolean isPauseScreen() {
		return !online();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		double fade = Ui.ease((System.currentTimeMillis() - openAt) / 260.0);
		int x0 = x0(), y0 = y0();
		int accent = tab == Tab.WORLD ? Ui.SOUL : Ui.EMBER;
		g.fill(0, 0, this.width, this.height, Ui.argb((int) (70 * fade), 0x0A0612));
		Ui.panel(g, x0, y0, pw, ph, accent, fade);
		Ui.heading(g, this.font, Ui.tr("gui.extremedimensions.settings.title").toUpperCase(), x0 + pw / 2, y0 + 8, accent, fade);
		long now = System.currentTimeMillis();
		if (leaveArmedAt != 0 && now - leaveArmedAt > ARMED_MS) {
			leaveArmedAt = 0;
			refreshButtons();
		}
		if (tab == Tab.DISPLAY) {
			display(g, x0, y0, mouseX, mouseY, fade);
		} else {
			world(g, x0, y0, mouseX, mouseY, fade, accent);
		}
	}

	private static final String[] DISPLAY_KEYS = {"reduce_flashing", "colour_blind", "meters", "timer", "hud_scale", "guidance", "hints", "menu"};

	private void display(GuiGraphicsExtractor g, int x0, int y0, int mouseX, int mouseY, double fade) {
		int y = y0 + 48;
		for (int i = 0; i < DISPLAY_KEYS.length; i++) {
			int ry = y + i * ROW;
			String label = Ui.tr("gui.extremedimensions.settings." + DISPLAY_KEYS[i]);
			g.text(this.font, label, x0 + 18, ry + 4, Ui.argb(fade, Ui.PARCHMENT));
			if ("colour_blind".equals(DISPLAY_KEYS[i])) {
				// The four set colours as they look now, with their sigils
				int sx = x0 + 18 + this.font.width(label) + 8;
				for (Group group : Group.values()) {
					CursesScreen.sigil(g, group, sx, ry + 4, Ui.setColour(group), fade);
					sx += 11;
				}
			}
		}
		Ui.divider(g, x0 + 12, y0 + ph - 46, pw - 24, Ui.EMBER, fade);
		Ui.centered(g, this.font, Ui.tr("gui.extremedimensions.settings.display_note"), x0 + pw / 2, y0 + ph - 40, Ui.MUTED, fade);
	}

	private void world(GuiGraphicsExtractor g, int x0, int y0, int mouseX, int mouseY, double fade, int accent) {
		// Difficulty: the preset the rules match now (or custom); operators pick and apply another beside it
		var now = currentPreset();
		String difficulty = Ui.tr("gui.extremedimensions.settings.difficulty");
		String state = now == null ? Ui.tr("gui.extremedimensions.preset.custom") : Ui.tr(now.nameKey());
		if (editable) {
			// two short lines beside the picker, clear of the search box below
			g.text(this.font, difficulty, x0 + 16, y0 + 43, Ui.argb(fade, Ui.PARCHMENT));
			g.text(this.font, Ui.ellipsize(this.font, state, 88), x0 + 16, y0 + 53, Ui.argb(fade * 0.8, Ui.MUTED));
		} else {
			g.text(this.font, difficulty, x0 + 16, y0 + 48, Ui.argb(fade, Ui.PARCHMENT));
			g.text(this.font, state, x0 + 108, y0 + 48, Ui.argb(fade, accent));
		}
		if (System.currentTimeMillis() - presetArmedAt > ARMED_MS && presetApply.getMessage().getString().equals(Ui.tr("gui.extremedimensions.preset.confirm"))) {
			refreshButtons();
		}
		// Beside the search: where the player is
		View v = view();
		int hx = x0 + 172, hy = y0 + 69, hw = pw - 172 - 16;
		switch (v) {
			case OVERVIEW -> {
				// the tiles say it all; the search box's hint tells it looks through every part
			}
			case PART -> g.text(this.font, Ui.ellipsize(this.font, part().name(), hw - 60), hx + 60, hy, Ui.argb(fade, accent));
			case CHANGED -> g.text(this.font, Ui.ellipsize(this.font, Ui.tr("gui.extremedimensions.settings.changed_title"), hw - 60), hx + 60, hy,
					Ui.argb(fade, accent));
			case SEARCH -> {
				int found = 0;
				for (Row r : shownRows) {
					if (r.key() != null) {
						found++;
					}
				}
				g.text(this.font, Ui.tr("gui.extremedimensions.settings.found", found), hx, hy, Ui.argb(fade, Ui.MUTED));
			}
		}
		if (!editable) {
			g.text(this.font, Ui.ellipsize(this.font, Ui.tr("gui.extremedimensions.settings.world_note_read"), pw - 16 - 110), x0 + 16,
					y0 + ph - 21, Ui.argb(fade, Ui.WARN));
		}
		int top = listTop(), bottom = listBottom();
		if (v == View.OVERVIEW) {
			return;
		}
		g.fill(x0 + 12, top - 2, x0 + pw - 12, bottom + 2, Ui.argb(fade * 0.35, Ui.SHADOW));
		int right = x0 + pw - 22;
		int valueX = right - RESET_W - 2 - VALUE_W;
		int maxX = valueX - GAP - MAX_BOX_W;
		for (int i = scroll; i < shownRows.size() && i - scroll < visibleRows(); i++) {
			Row r = shownRows.get(i);
			if (r.heading() == null) {
				continue;
			}
			int y = top + (i - scroll) * LIST_ROW;
			String heading = v == View.PART ? r.label() : r.heading();
			g.text(this.font, Ui.ellipsize(this.font, heading.toUpperCase(), maxX - x0 - 24), x0 + 16, y + 4, Ui.argb(fade, accent));
			if (i + 1 < shownRows.size() && shownRows.get(i + 1).box2() != null) {
				String mx = Ui.tr("gui.extremedimensions.settings.col_max"), ps = Ui.tr("gui.extremedimensions.settings.col_per_stack");
				g.text(this.font, mx, maxX + MAX_BOX_W / 2 - this.font.width(mx) / 2, y + 4, Ui.argb(fade, Ui.MUTED));
				g.text(this.font, ps, valueX + VALUE_W / 2 - this.font.width(ps) / 2, y + 4, Ui.argb(fade, Ui.MUTED));
			}
		}
		// Scrollbar
		int total = shownRows.size(), vis = visibleRows();
		if (total > vis) {
			int track = bottom - top;
			int thumb = Math.max(12, track * vis / total);
			int ty = top + (track - thumb) * scroll / Math.max(1, total - vis);
			g.fill(x0 + pw - 10, top, x0 + pw - 8, bottom, Ui.argb(fade * 0.6, Ui.TRACK));
			g.fill(x0 + pw - 10, ty, x0 + pw - 8, ty + thumb, Ui.argb(fade, accent));
		}
		if (shownRows.isEmpty()) {
			Ui.centered(g, this.font, Ui.tr(v == View.CHANGED ? "gui.extremedimensions.settings.none_changed" : "gui.extremedimensions.settings.no_match"),
					x0 + pw / 2, top + 20, Ui.MUTED, fade);
		}
	}

	/** A display option: its current value; a click moves on to the next. */
	private final class Choice extends AbstractButton {
		private final Supplier<String> value;
		private final Runnable next;

		Choice(int x, int y, int w, int h, Supplier<String> value, Runnable next, String key) {
			super(x, y, w, h, Component.translatable("gui.extremedimensions.settings." + key));
			this.value = value;
			this.next = next;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			next.run();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int x = this.getX(), y = this.getY(), w = this.getWidth(), h = this.getHeight();
			Ui.chip(g, x, y, w, h, this.isHovered() ? Ui.WARN : Ui.EMBER, this.isHovered() ? 1.0 : 0.75);
			var font = Minecraft.getInstance().font;
			String v = value.get();
			g.text(font, "‹", x + 5, y + 4, Ui.argb(0.6, Ui.MUTED));
			g.text(font, "›", x + w - 9, y + 4, Ui.argb(0.6, Ui.MUTED));
			g.text(font, v, x + w / 2 - font.width(v) / 2, y + 4, Ui.argb(1.0, Ui.PARCHMENT));
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			out.add(NarratedElementType.TITLE, Component.empty().append(this.getMessage()).append(": " + value.get()));
		}
	}

	private final class TabButton extends AbstractButton {
		private final Tab which;

		TabButton(int x, int y, int w, Tab which) {
			super(x, y, w, 14, Component.translatable("gui.extremedimensions.settings.tab_" + which.name().toLowerCase(Locale.ROOT)));
			this.which = which;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			tab = which;
			showTab();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			boolean on = tab == which;
			int col = which == Tab.WORLD ? Ui.SOUL : Ui.EMBER;
			int x = this.getX(), y = this.getY();
			Ui.chip(g, x, y, this.getWidth(), this.getHeight(), on ? col : Ui.MUTED, on ? 1.0 : this.isHovered() ? 0.8 : 0.55);
			var font = Minecraft.getInstance().font;
			String label = this.getMessage().getString().toUpperCase();
			g.text(font, label, x + this.getWidth() / 2 - font.width(label) / 2, y + 3, Ui.argb(on ? 1.0 : 0.7, on ? Ui.PARCHMENT : Ui.TEXT));
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			out.add(NarratedElementType.TITLE, this.getMessage());
		}
	}

	/** One part of the rules on the overview: its name, and how many of its values differ from their defaults. */
	private final class Tile extends AbstractButton {
		private final Part part;
		/** What the tooltip was made for (the counts), so it is made again only when they change. */
		private String tipFor;

		Tile(Part part, int x, int y, int w, int h) {
			super(x, y, w, h, Component.literal(part.name()));
			this.part = part;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			open(part.id());
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int settings = 0, differ = 0, waiting = 0;
			for (Row r : part.rows()) {
				if (r.key() != null) {
					settings++;
					differ += differs(r) ? 1 : 0;
					waiting += pending(r) ? 1 : 0;
				}
			}
			String counts = settings + ":" + differ;
			if (!counts.equals(tipFor)) {
				tipFor = counts;
				this.setTooltip(Tooltip.create(Component.literal(part.name()).append("\n")
						.append(Component.translatable("gui.extremedimensions.settings.part_tip", settings, differ))));
			}
			var font = Minecraft.getInstance().font;
			int x = this.getX(), y = this.getY(), w = this.getWidth(), h = this.getHeight();
			Ui.chip(g, x, y, w, h, Ui.SOUL, this.isHovered() ? 1.0 : 0.85);
			if (this.isHovered()) {
				g.fill(x + 2, y + 2, x + w - 2, y + h - 2, Ui.argb(0.12, 0xFFFFFF));
			}
			if (differ > 0) {
				g.fill(x + 2, y + 2, x + 4, y + h - 2, Ui.argb(1.0, Ui.EMBER));
			}
			String count = differ > 0 ? String.valueOf(differ) : "";
			int room = w - 12 - (count.isEmpty() ? 0 : font.width(count) + 4);
			int ty = y + (h - 8) / 2;
			g.text(font, Ui.ellipsize(font, part.name(), room), x + 7, ty, Ui.argb(1.0, waiting > 0 ? Ui.WARN : this.isHovered() ? Ui.SOUL : Ui.PARCHMENT));
			if (!count.isEmpty()) {
				g.text(font, count, x + w - 5 - font.width(count), ty, Ui.argb(1.0, Ui.EMBER));
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			out.add(NarratedElementType.TITLE, this.getMessage());
		}
	}

	/** A setting's line in the list: its name (gold when it differs from its default), what it does on hover. */
	private final class RowLine extends AbstractButton {
		private Row row;

		RowLine() {
			super(0, 0, 10, LIST_ROW, Component.empty());
			this.visible = false;
		}

		void bind(Row r, int x, int y, int w) {
			if (r != row) {
				row = r;
				this.setTooltip(r == null ? null : Tooltip.create(Component.literal(tipText(r))));
				this.setMessage(r == null ? Component.empty() : Component.literal(r.label()));
			}
			this.visible = r != null;
			this.setX(x);
			this.setY(y);
			this.setWidth(Math.max(10, w));
		}

		/** A click on a switch's name flips it, as on its button. */
		@Override
		public void onPress(InputWithModifiers input) {
			flip(row);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			if (row == null) {
				return;
			}
			var font = Minecraft.getInstance().font;
			int x = this.getX(), y = this.getY(), w = this.getWidth();
			if (this.isHovered()) {
				g.fill(x, y + 1, x + w, y + LIST_ROW - 1, Ui.argb(0.08, 0xFFFFFF));
			}
			boolean differ = differs(row);
			if (differ) {
				g.fill(x, y + 4, x + 2, y + 12, Ui.argb(1.0, Ui.EMBER));
			}
			g.text(font, Ui.ellipsize(font, row.label(), w - 10), x + 6, y + 4, Ui.argb(1.0, differ ? Ui.EMBER : Ui.TEXT));
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			out.add(NarratedElementType.TITLE, this.getMessage());
		}
	}

	/** A switch's value: On or Off, a click flips it (yellow until applied). */
	private final class Toggle extends AbstractButton {
		private Row row;

		Toggle() {
			super(0, 0, VALUE_W, 12, Component.empty());
			this.visible = false;
		}

		void bind(Row r, int x, int y) {
			if (r != row) {
				row = r;
				this.setMessage(r == null ? Component.empty() : Component.literal(r.label()));
				this.setTooltip(r == null ? null : Tooltip.create(Component.literal(tipText(r))));
			}
			this.visible = r != null;
			this.setX(x);
			this.setY(y);
		}

		@Override
		public void onPress(InputWithModifiers input) {
			flip(row);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			if (row == null) {
				return;
			}
			var font = Minecraft.getInstance().font;
			int x = this.getX(), y = this.getY(), w = this.getWidth(), h = this.getHeight();
			boolean lit = on(row.box().getValue());
			boolean waiting = pending(row);
			boolean hot = this.isHovered() && editable;
			Ui.chip(g, x, y, w, h, Ui.SOUL, hot ? 1.0 : 0.8);
			if (hot) {
				g.fill(x + 2, y + 2, x + w - 2, y + h - 2, Ui.argb(0.12, 0xFFFFFF));
			}
			// a lamp: lit green when on
			g.fill(x + 5, y + 4, x + 9, y + 8, Ui.argb(lit ? 1.0 : 0.4, lit ? Ui.OK : Ui.MUTED));
			String v = onOff(lit);
			int col = waiting ? Ui.WARN : lit ? Ui.PARCHMENT : Ui.MUTED;
			g.text(font, v, x + 6 + (w - 6) / 2 - font.width(v) / 2, y + 2, Ui.argb(editable ? 1.0 : 0.6, col));
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			out.add(NarratedElementType.TITLE, Component.empty().append(this.getMessage()).append(": " + (row == null ? "" : onOff(on(row.box().getValue())))));
		}
	}

	/** The ↺ beside a field that holds something else than its default: a click puts the default back. */
	private final class ResetMark extends AbstractButton {
		private Row row;

		ResetMark() {
			super(0, 0, RESET_W, 12, Component.translatable("gui.extremedimensions.settings.reset_one", ""));
			this.visible = false;
		}

		void bind(Row r, int x, int y) {
			if (r != row) {
				row = r;
				this.setTooltip(r == null ? null : Tooltip.create(Component.translatable("gui.extremedimensions.settings.reset_one", defaultText(r))));
			}
			this.setX(x);
			this.setY(y);
		}

		@Override
		public void onPress(InputWithModifiers input) {
			if (editable && row != null) {
				toDefault(row);
			}
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			var font = Minecraft.getInstance().font;
			boolean hot = this.isHovered();
			g.text(font, "↺", this.getX() + 2, this.getY() + 2, Ui.argb(hot ? 1.0 : 0.75, hot ? Ui.WARN : Ui.MUTED));
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			out.add(NarratedElementType.TITLE, this.getMessage());
		}
	}

	/** A small link-like button: Back to the overview, or Changed. */
	private final class Link extends AbstractButton {
		private final Supplier<String> text;
		private final Runnable action;

		Link(int x, int y, int w, String key, Supplier<String> text, Runnable action) {
			super(x, y, w, 16, Component.translatable(key));
			this.text = text;
			this.action = action;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			action.run();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			var font = Minecraft.getInstance().font;
			int x = this.getX(), y = this.getY(), w = this.getWidth(), h = this.getHeight();
			boolean hot = this.isHovered();
			Ui.chip(g, x, y, w, h, Ui.SOUL, hot ? 1.0 : 0.8);
			String s = Ui.ellipsize(font, text.get(), w - 8);
			g.text(font, s, x + w / 2 - font.width(s) / 2, y + 4, Ui.argb(1.0, hot ? Ui.WARN : Ui.PARCHMENT));
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			out.add(NarratedElementType.TITLE, Component.literal(text.get()));
		}
	}
}
