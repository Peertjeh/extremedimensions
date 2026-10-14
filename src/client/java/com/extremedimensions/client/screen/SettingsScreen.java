package com.extremedimensions.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.extremedimensions.client.options.ClientOptions;
import com.extremedimensions.client.state.ClientState;
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
 * <li><b>World rules</b>: every value of the world's config and every curse's maximum and strength, searchable.
 * Everyone can read them; operators (and the owner of a world not open to LAN) can change them, which saves the
 * server's config at once. Opened outside a world (from the mod list), they are this computer's own config.</li>
 * </ul>
 */
public class SettingsScreen extends Screen {
	private static final int PW = 372, PH = 246;
	private static final int ROW = 22, LIST_ROW = 16;
	private static final Gson GSON = new Gson();

	private enum Tab {
		DISPLAY, WORLD
	}

	/** One line of the world rules: a heading, a setting, or a curse (two values). */
	private record Row(String heading, String key, String label, String tip, EditBox box, EditBox box2, String search) {}

	private final Screen parent;
	private final long openAt = System.currentTimeMillis();
	private Tab tab = Tab.DISPLAY;
	private final List<Row> rows = new ArrayList<>();
	private final List<Row> shownRows = new ArrayList<>();
	/** The values as the world has them now, by key (curses as penalty.id.max / penalty.id.per_stack). */
	private final Map<String, String> current = new HashMap<>();
	private final Map<String, String> defaults = new HashMap<>();
	private final List<AbstractButton> displayWidgets = new ArrayList<>();
	private EditBox search;
	private Button apply;
	private Button reset;
	/** The difficulty preset to apply (cycled through), and when the apply button was first pressed (to confirm). */
	private com.extremedimensions.config.Preset target = com.extremedimensions.config.Preset.TETHERED;
	private Choice presetChoice;
	private Button presetApply;
	private long presetArmedAt;
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

	private int x0() {
		return Math.max(2, (this.width - PW) / 2);
	}

	private int y0() {
		return Math.max(4, (this.height - PH) / 2);
	}

	private int listTop() {
		return y0() + 88;
	}

	private int listBottom() {
		return y0() + PH - 34;
	}

	@Override
	protected void init() {
		shown = this;
		rows.clear();
		displayWidgets.clear();
		int x0 = x0(), y0 = y0();
		this.addRenderableWidget(new TabButton(x0 + 12, y0 + 22, (PW - 30) / 2, Tab.DISPLAY));
		this.addRenderableWidget(new TabButton(x0 + 18 + (PW - 30) / 2, y0 + 22, (PW - 30) / 2, Tab.WORLD));

		// Display
		int cx = x0 + PW - 18 - 124, y = y0 + 48;
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

		// World rules: a difficulty preset at once, then every value
		presetChoice = new Choice(x0 + 108, y0 + 44, 124, 16, () -> Ui.tr(target.nameKey()), () -> {
			var all = com.extremedimensions.config.Preset.values();
			target = all[(target.ordinal() + 1) % all.length];
			presetArmedAt = 0;
			presetChoice.setTooltip(Tooltip.create(Component.translatable(target.descKey())));
			refreshButtons();
		}, "difficulty");
		presetChoice.setTooltip(Tooltip.create(Component.translatable(target.descKey())));
		this.addRenderableWidget(presetChoice);
		presetApply = this.addRenderableWidget(Button.builder(Component.translatable("gui.extremedimensions.preset.apply"), b -> applyPreset())
				.pos(x0 + 238, y0 + 43).size(PW - 238 - 16, 18).build());
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
		String section = null;
		for (Config.Setting st : Config.settings()) {
			if (!st.section().equals(section)) {
				section = st.section();
				rows.add(new Row(sectionName(section), null, null, null, null, null, ""));
			}
			String label = label(st.key());
			String tip = tip(st.key());
			EditBox b = box(64);
			b.setTooltip(Tooltip.create(Component.literal(tip != null ? label + ": " + tip : label + " (" + st.key() + ")")));
			rows.add(new Row(null, st.key(), label, tip, b, null, (label + " " + st.key()).toLowerCase(Locale.ROOT)));
		}
		Group group = null;
		for (Penalty pen : Penalty.values()) {
			if (pen.group != group) {
				group = pen.group;
				rows.add(new Row(Ui.tr("gui.extremedimensions.curses.title") + ": " + Ui.tr("gui.extremedimensions.curses.tab_" + group.id()),
						null, null, null, null, null, ""));
			}
			String name = Ui.tr(pen.nameKey());
			EditBox max = box(30), per = box(64);
			max.setTooltip(Tooltip.create(Component.translatable("gui.extremedimensions.settings.tip_max", name)));
			per.setTooltip(Tooltip.create(Component.translatable("gui.extremedimensions.settings.tip_per_stack", name)));
			rows.add(new Row(null, "penalty." + pen.id, name, null, max, per, (name + " " + pen.id).toLowerCase(Locale.ROOT)));
		}
		apply = this.addRenderableWidget(Button.builder(Component.translatable("gui.extremedimensions.settings.apply"), b -> applyChanges())
				.pos(x0 + 14, y0 + PH - 26).size(110, 18).build());
		reset = this.addRenderableWidget(Button.builder(Component.translatable("gui.extremedimensions.settings.defaults"), b -> fillDefaults())
				.pos(x0 + 130, y0 + PH - 26).size(110, 18).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.pos(x0 + PW - 14 - 100, y0 + PH - 26).size(100, 18).build());
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

	private EditBox box(int w) {
		EditBox b = new EditBox(this.font, 0, 0, w, 12, Component.empty());
		b.setMaxLength(12);
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
			String k1 = r.box2() == null ? r.key() : r.key() + ".max";
			sync(r.box(), k1, old, keepEdits);
			if (r.box2() != null) {
				sync(r.box2(), r.key() + ".per_stack", old, keepEdits);
			}
		}
		filter();
	}

	private void sync(EditBox b, String key, Map<String, String> old, boolean keepEdits) {
		String now = current.getOrDefault(key, "");
		if (!keepEdits || b.getValue().equals(old.getOrDefault(key, ""))) {
			b.setValue(now);
		}
		b.setEditable(editable);
		colour(b, b.getValue());
	}

	/** Unchanged values in the text colour, changed ones in amber, ones that cannot be taken in red. */
	private void colour(EditBox b, String text) {
		String key = keyOf(b);
		int col = key == null || text.equals(current.getOrDefault(key, text)) ? Ui.TEXT : valid(key, text) ? Ui.WARN : Ui.DANGER;
		b.setTextColor(Ui.argb(255, col));
		b.setTextColorUneditable(Ui.argb(255, Ui.MUTED));
		if (apply != null) {
			refreshButtons();
		}
	}

	private String keyOf(EditBox b) {
		for (Row r : rows) {
			if (r.box() == b) {
				return r.box2() == null ? r.key() : r.key() + ".max";
			}
			if (r.box2() == b) {
				return r.key() + ".per_stack";
			}
		}
		return null;
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

	/** Every changed value that can be taken, by key. */
	private Map<String, String> changes() {
		Map<String, String> out = new HashMap<>();
		for (Row r : rows) {
			if (r.key() == null) {
				continue;
			}
			String k1 = r.box2() == null ? r.key() : r.key() + ".max";
			add(out, k1, r.box().getValue());
			if (r.box2() != null) {
				add(out, r.key() + ".per_stack", r.box2().getValue());
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
		if (now - presetArmedAt > 4000) {
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
	private com.extremedimensions.config.Preset currentPreset() {
		return com.extremedimensions.config.Preset.matching(current);
	}

	private void fillDefaults() {
		if (!editable) {
			return;
		}
		for (Row r : rows) {
			if (r.key() == null) {
				continue;
			}
			String k1 = r.box2() == null ? r.key() : r.key() + ".max";
			r.box().setValue(defaults.getOrDefault(k1, r.box().getValue()));
			if (r.box2() != null) {
				r.box2().setValue(defaults.getOrDefault(r.key() + ".per_stack", r.box2().getValue()));
			}
		}
	}

	private void refreshButtons() {
		boolean world = tab == Tab.WORLD;
		int n = changes().size();
		apply.visible = world;
		reset.visible = world;
		apply.active = editable && n > 0;
		reset.active = editable;
		presetChoice.visible = world && editable;
		presetApply.visible = world && editable;
		boolean armed = System.currentTimeMillis() - presetArmedAt <= 4000;
		presetApply.active = editable && target != currentPreset();
		presetApply.setMessage(Component.translatable(armed ? "gui.extremedimensions.preset.confirm" : "gui.extremedimensions.preset.apply"));
		apply.setMessage(n > 0 ? Component.translatable("gui.extremedimensions.settings.apply_n", n)
				: Component.translatable("gui.extremedimensions.settings.apply"));
	}

	/** The rows matching the search (a heading stays while any of its rows does). */
	private void filter() {
		shownRows.clear();
		String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		Row heading = null;
		for (Row r : rows) {
			if (r.heading() != null) {
				heading = r;
				continue;
			}
			if (q.isEmpty() || r.search().contains(q)) {
				if (heading != null) {
					shownRows.add(heading);
					heading = null;
				}
				shownRows.add(r);
			}
		}
		layoutList();
	}

	private int visibleRows() {
		return (listBottom() - listTop()) / LIST_ROW;
	}

	private void layoutList() {
		int max = Math.max(0, shownRows.size() - visibleRows());
		scroll = Math.max(0, Math.min(scroll, max));
		boolean world = tab == Tab.WORLD;
		for (Row r : rows) {
			if (r.box() != null) {
				r.box().visible = false;
			}
			if (r.box2() != null) {
				r.box2().visible = false;
			}
		}
		int right = x0() + PW - 22;
		for (int i = scroll; i < shownRows.size() && i - scroll < visibleRows(); i++) {
			Row r = shownRows.get(i);
			int y = listTop() + (i - scroll) * LIST_ROW + 2;
			if (r.box2() != null) {
				r.box().setX(right - 64 - 6 - 30);
				r.box().setY(y);
				r.box().visible = world;
				r.box2().setX(right - 64);
				r.box2().setY(y);
				r.box2().visible = world;
			} else if (r.box() != null) {
				r.box().setX(right - 64);
				r.box().setY(y);
				r.box().visible = world;
			}
		}
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
		if (tab == Tab.WORLD && scrollY != 0) {
			scroll -= (int) Math.signum(scrollY) * 3;
			layoutList();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void onClose() {
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
		Ui.panel(g, x0, y0, PW, PH, accent, fade);
		Ui.heading(g, this.font, Ui.tr("gui.extremedimensions.settings.title").toUpperCase(), x0 + PW / 2, y0 + 8, accent, fade);
		if (tab == Tab.DISPLAY) {
			display(g, x0, y0, mouseX, mouseY, fade);
		} else {
			world(g, x0, y0, mouseX, mouseY, fade, accent);
		}
	}

	private static final String[] DISPLAY_KEYS = {"reduce_flashing", "colour_blind", "meters", "timer", "hud_scale", "guidance"};

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
		Ui.divider(g, x0 + 12, y0 + PH - 52, PW - 24, Ui.EMBER, fade);
		Ui.centered(g, this.font, Ui.tr("gui.extremedimensions.settings.display_note"), x0 + PW / 2, y0 + PH - 44, Ui.MUTED, fade);
	}

	private void world(GuiGraphicsExtractor g, int x0, int y0, int mouseX, int mouseY, double fade, int accent) {
		// Difficulty: the preset the rules match now (or custom); operators pick and apply another beside it
		var now = currentPreset();
		String difficulty = Ui.tr("gui.extremedimensions.settings.difficulty");
		g.text(this.font, difficulty, x0 + 16, y0 + 48, Ui.argb(fade, Ui.PARCHMENT));
		String state = now == null ? Ui.tr("gui.extremedimensions.preset.custom") : Ui.tr(now.nameKey());
		if (editable) {
			g.text(this.font, state, x0 + 16, y0 + 57, Ui.argb(fade * 0.8, Ui.MUTED));
		} else {
			g.text(this.font, state, x0 + 108, y0 + 48, Ui.argb(fade, accent));
		}
		if (System.currentTimeMillis() - presetArmedAt > 4000 && presetApply.getMessage().getString().equals(Ui.tr("gui.extremedimensions.preset.confirm"))) {
			refreshButtons();
		}
		String note = Ui.tr(editable ? (online() ? "gui.extremedimensions.settings.world_note_edit" : "gui.extremedimensions.settings.world_note_local")
				: "gui.extremedimensions.settings.world_note_read");
		int nx = x0 + 176;
		int ny = y0 + 64;
		for (String line : Ui.wrap(this.font, note, PW - 176 - 16)) {
			g.text(this.font, line, nx, ny, Ui.argb(fade, editable ? Ui.MUTED : Ui.WARN));
			ny += 9;
		}
		int top = listTop(), bottom = listBottom();
		g.fill(x0 + 12, top - 2, x0 + PW - 12, bottom + 2, Ui.argb(fade * 0.35, Ui.SHADOW));
		int right = x0 + PW - 22;
		g.enableScissor(x0 + 12, top - 2, x0 + PW - 12, bottom + 2);
		for (int i = scroll; i < shownRows.size() && i - scroll < visibleRows(); i++) {
			Row r = shownRows.get(i);
			int y = top + (i - scroll) * LIST_ROW;
			if (r.heading() != null) {
				g.text(this.font, r.heading().toUpperCase(), x0 + 16, y + 4, Ui.argb(fade, accent));
				if (i + 1 < shownRows.size() && shownRows.get(i + 1).box2() != null) {
					String mx = Ui.tr("gui.extremedimensions.settings.col_max"), ps = Ui.tr("gui.extremedimensions.settings.col_per_stack");
					g.text(this.font, mx, right - 64 - 6 - 30 + 15 - this.font.width(mx) / 2, y + 4, Ui.argb(fade, Ui.MUTED));
					g.text(this.font, ps, right - 32 - this.font.width(ps) / 2, y + 4, Ui.argb(fade, Ui.MUTED));
				}
				continue;
			}
			String label = r.label();
			int room = (r.box2() != null ? right - 64 - 6 - 30 : right - 64) - (x0 + 20) - 6;
			while (this.font.width(label) > room && label.length() > 4) {
				label = label.substring(0, label.length() - 2);
			}
			if (!label.equals(r.label())) {
				label = label + "…";
			}
			g.text(this.font, label, x0 + 20, y + 4, Ui.argb(fade, Ui.TEXT));
		}
		g.disableScissor();
		// Scrollbar
		int total = shownRows.size(), vis = visibleRows();
		if (total > vis) {
			int track = bottom - top;
			int thumb = Math.max(12, track * vis / total);
			int ty = top + (track - thumb) * scroll / Math.max(1, total - vis);
			g.fill(x0 + PW - 10, top, x0 + PW - 8, bottom, Ui.argb(fade * 0.6, Ui.TRACK));
			g.fill(x0 + PW - 10, ty, x0 + PW - 8, ty + thumb, Ui.argb(fade, accent));
		}
		if (shownRows.isEmpty()) {
			Ui.centered(g, this.font, Ui.tr("gui.extremedimensions.settings.no_match"), x0 + PW / 2, top + 20, Ui.MUTED, fade);
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
}
