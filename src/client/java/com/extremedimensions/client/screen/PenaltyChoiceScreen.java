package com.extremedimensions.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.client.title.Menus;
import com.extremedimensions.client.ui.CurseLine;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.config.Config;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.penalty.Penalty;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * After a Collapse or a death beyond the wound: pick one of three curse cards. Sometimes the whole offer is multiplied;
 * then each card carries its own seal (2x, 3x, 4x) and adds that many stacks, never past the curse's maximum. An
 * Abyssal card is black with a violet crack through it; on Extreme it sometimes comes alone, and then there is no choice.
 *
 * <p>Since 3.55.0 the cards wait while a container is open (a chest's contents are not torn from under the player), say
 * which of several curses owed in a row this is ("Curse 1 of 3"), count down the time before the Tether chooses, and
 * have a button that opens the Curses screen over them and comes back.
 */
public class PenaltyChoiceScreen extends Screen {
	private static final int CARD_W = 132;
	private static final int CARD_H = 164;
	private static final int GAP = 14;

	/**
	 * One card: the curse, its multiplier, the player's stacks now, the maximum, the value per stack, and whether the
	 * player can read it (a curse they have never carried shows runes on the harder difficulties).
	 */
	private record Option(Penalty pen, int mult, int stacks, int max, String value, boolean readable) {}

	private final List<Option> options = new ArrayList<>();
	private final List<Button> buttons = new ArrayList<>();
	private final long openAt = System.currentTimeMillis();
	private final boolean overworld;
	/** The set the offer comes from, as the server named it ("beyond" or "overworld"); null from an older server. */
	private String set;
	private int chosen = -1;
	private long chosenAt = 0;
	/** Which offer of a run the server said this one is (0 until it said); another number later means it moved on. */
	private int seenNth = 0;
	/** The button that opens the Curses screen over the cards. */
	private Button yours;
	/** A card's lore cut short under the mouse this frame, line by line: drawn whole over the cards once they are drawn. */
	private List<String> loreTip;

	/** Cards that came while a container was open: they wait for it to close (null: none waiting). */
	private static List<String> waiting;
	/** Whether the wait is watched every tick yet (it is watched from the first wait on). */
	private static boolean watching;

	/**
	 * The server dealt the cards: they open now, unless a container is open (a chest, a furnace, the inventory), which
	 * they would tear from under the player. Then they wait for it to close (3.55.0).
	 */
	public static void receive(String ids) {
		List<String> cards = java.util.Arrays.asList(ids.split(","));
		if (Menus.current() instanceof AbstractContainerScreen<?>) {
			waiting = cards;
			if (!watching) {
				watching = true;
				ClientTickEvents.END_CLIENT_TICK.register(PenaltyChoiceScreen::tick);
			}
			return;
		}
		waiting = null;
		Minecraft.getInstance().gui.setScreen(new PenaltyChoiceScreen(cards));
	}

	/** Every client tick: cards waiting open once no container is open any more (and are forgotten with the world). */
	private static void tick(Minecraft mc) {
		if (waiting == null) {
			return;
		}
		if (mc.level == null || mc.player == null) {
			waiting = null;
			return;
		}
		if (!(Menus.current() instanceof AbstractContainerScreen<?>)) {
			List<String> cards = waiting;
			waiting = null;
			mc.gui.setScreen(new PenaltyChoiceScreen(cards));
		}
	}

	public PenaltyChoiceScreen(List<String> cards) {
		super(Component.translatable("gui.extremedimensions.choice.title"));
		for (String card : cards) {
			if (card.startsWith("@")) {
				set = card.substring(1);
				continue;
			}
			String[] kv = card.split(":");
			Penalty p = Penalty.byId(kv[0]);
			if (p == null) {
				continue;
			}
			try {
				options.add(new Option(p, kv.length > 1 ? Integer.parseInt(kv[1]) : 1, kv.length > 2 ? Integer.parseInt(kv[2]) : 0,
						kv.length > 3 ? Integer.parseInt(kv[3]) : p.max, kv.length > 4 ? kv[4] : p.display(), kv.length <= 5 || !"0".equals(kv[5])));
			} catch (NumberFormatException e) {
				options.add(new Option(p, 1, 0, p.max, p.display(), true));
			}
		}
		overworld = setIsOverworld();
	}

	/** Which world's set this offer comes from: from its cards, or (all Everywhere cards) from where the player is. */
	private boolean setIsOverworld() {
		if (set != null) {
			return "overworld".equals(set);
		}
		for (Option o : options) {
			if (!o.pen().everywhere) {
				return o.pen().overworld;
			}
		}
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		return mc.level != null && mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
	}

	/** True when the only card on offer is an Abyssal one: the abyss chose. */
	private boolean forced() {
		return options.size() == 1 && options.get(0).pen().abyssal;
	}

	/** True when the offer is multiplied (every card then carries a seal). */
	private boolean multiplied() {
		for (Option o : options) {
			if (o.mult() > 1) {
				return true;
			}
		}
		return false;
	}

	private int startX() {
		int n = options.size();
		return (this.width - (n * CARD_W + (n - 1) * GAP)) / 2;
	}

	private int cardY() {
		return this.height / 2 - CARD_H / 2 + 10;
	}

	@Override
	protected void init() {
		buttons.clear();
		int x0 = startX();
		int y = cardY();
		for (int i = 0; i < options.size(); i++) {
			final int idx = i;
			final Penalty pen = options.get(i).pen();
			int x = x0 + i * (CARD_W + GAP);
			Button b = Button.builder(Component.translatable("gui.extremedimensions.choice.accept"), btn -> choose(idx, pen))
					.pos(x + 14, y + CARD_H - 28)
					.size(CARD_W - 28, 20)
					.build();
			b.visible = false;
			buttons.add(this.addRenderableWidget(b));
		}
		// The curses carried, over the cards; closing them comes back here (3.55.0)
		yours = this.addRenderableWidget(Button.builder(Component.translatable("gui.extremedimensions.choice.your_curses"),
				btn -> CursesScreen.openOver(this)).pos(this.width - 96, 6).size(90, 16)
				.tooltip(Tooltip.create(Component.translatable("gui.extremedimensions.choice.your_curses.desc"))).build());
	}

	/** Seconds before the Tether chooses, as the server last said (-1: no clock runs). */
	private static int secondsLeft(long now) {
		if (ClientState.offerLeft < 0) {
			return -1;
		}
		return (int) Math.max(0, ClientState.offerLeft - (now - ClientState.syncMs) / 1000);
	}

	/**
	 * Under the cards (3.55.0): which of the curses owed in a row this is, and how long before the Tether chooses, with
	 * a thin bar running down; red in the last fifteen seconds.
	 */
	private void footer(GuiGraphicsExtractor g, int cx, int y, long now) {
		List<String> parts = new ArrayList<>();
		if (ClientState.offerOf > 1) {
			parts.add(Ui.tr("gui.extremedimensions.choice.series", ClientState.offerNth, ClientState.offerOf));
		}
		int left = chosen < 0 ? secondsLeft(now) : -1;
		if (left >= 0) {
			parts.add(Ui.tr("gui.extremedimensions.choice.countdown", Ui.time(left)));
		}
		if (parts.isEmpty()) {
			return;
		}
		double fa = Ui.ease((now - openAt) / 600.0);
		boolean late = left >= 0 && left <= 15;
		int col = late ? (Ui.calm() || (now / 400) % 2 == 0 ? Ui.DANGER : Ui.WARN) : Ui.MUTED;
		Ui.centered(g, this.font, String.join("  ·  ", parts), cx, y, col, fa);
		if (left >= 0 && Config.OFFER_TIMEOUT_SECONDS > 0) {
			int bw = 160;
			double share = Math.min(1, left / (double) Config.OFFER_TIMEOUT_SECONDS);
			g.fill(cx - bw / 2, y + 12, cx + bw / 2, y + 14, Ui.argb(fa * 0.6, Ui.TRACK));
			g.fill(cx - bw / 2, y + 12, cx - bw / 2 + (int) Math.round(bw * share), y + 14, Ui.argb(fa, late ? Ui.DANGER : Ui.WARN));
		}
	}

	private void choose(int idx, Penalty pen) {
		if (chosen >= 0) {
			return;
		}
		chosen = idx;
		chosenAt = System.currentTimeMillis();
		ClientPlayNetworking.send(new ModPackets.ChoosePayload(pen.id));
		for (Button b : buttons) {
			b.visible = false;
		}
	}

	private double progress(int i, long now) {
		return Ui.ease((now - openAt - i * 130L) / 520.0);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		long now = System.currentTimeMillis();

		for (int i = 0; i < buttons.size(); i++) {
			buttons.get(i).visible = chosen < 0 && progress(i, now) >= 1.0;
		}
		if (yours != null) {
			yours.visible = chosen < 0;
		}
		if (chosen >= 0 && now - chosenAt > 600) {
			this.onClose();
			return;
		}
		// The server moved on without a card from here (the Tether chose when the time ran out): these cards are spent
		if (ClientState.offerOf > 0 && seenNth == 0) {
			seenNth = ClientState.offerNth;
		} else if (seenNth > 0 && chosen < 0 && (ClientState.offerOf == 0 || ClientState.offerNth != seenNth)) {
			this.onClose();
			return;
		}

		boolean forced = forced();
		int accent = forced ? Ui.abyss() : Ui.setColour(overworld ? Penalty.Group.OVERWORLD : Penalty.Group.BEYOND);
		int cx = this.width / 2;
		int y = cardY();
		double bg = Ui.ease((now - openAt) / 400.0);
		g.fill(0, 0, this.width, this.height, Ui.argb((int) ((forced ? 150 : 90) * bg), forced ? 0x0A0014 : overworld ? 0x001818 : 0x300000));
		Ui.vignette(g, this.width, this.height, 0x000000, (int) (40 * bg), 6);

		// Heading with a short glitch on open
		long age = now - openAt;
		double ha = Ui.ease(age / 350.0);
		boolean glitch = age < 700 && !Ui.calm();
		int jit = glitch ? (int) (Ui.hash(now / 60) * 8 - 4) : 0;
		String head = Ui.tr(overworld ? "gui.extremedimensions.choice.heading_overworld" : "gui.extremedimensions.choice.heading").toUpperCase();
		if (glitch) {
			Ui.centered(g, this.font, head, cx + 2 + jit, y - 42, 0x30E0FF, ha * 0.65);
		}
		Ui.heading(g, this.font, head, cx - jit / 2, y - 42, accent, ha);
		boolean hungry = multiplied();
		if (forced) {
			Ui.centered(g, this.font, Ui.tr("gui.extremedimensions.choice.forced"), cx, y - 26, Ui.mix(Ui.abyss(), 0xFFFFFF, 0.45), ha);
		} else {
			Ui.centered(g, this.font, Ui.tr(hungry ? "gui.extremedimensions.choice.hungry" : "gui.extremedimensions.choice.subtitle"), cx, y - 26,
					hungry ? (Ui.calm() || (now / 300) % 2 == 0 ? Ui.DANGER : Ui.WARN) : Ui.TEXT, ha);
		}

		int x0 = startX();
		loreTip = null;
		for (int i = 0; i < options.size(); i++) {
			card(g, options.get(i), i, x0 + i * (CARD_W + GAP), y, mouseX, mouseY, now, hungry ? Ui.DANGER : accent);
		}
		footer(g, cx, y + CARD_H + 8, now);
		if (loreTip != null) {
			Ui.tooltip(g, this.font, loreTip, java.util.Collections.nCopies(loreTip.size(), 0xFFFFFF), mouseX, mouseY, this.width, this.height, accent);
		}
	}

	private void card(GuiGraphicsExtractor g, Option opt, int i, int x, int y, int mouseX, int mouseY, long now, int accent) {
		Penalty pen = opt.pen();
		double e = progress(i, now);
		double fade = e;
		boolean hover = e >= 1.0 && chosen < 0 && mouseX >= x && mouseX <= x + CARD_W && mouseY >= y - 6 && mouseY <= y + CARD_H;
		if (chosen >= 0) {
			fade *= (i == chosen) ? 1.0 : 1 - Ui.clamp01((now - chosenAt) / 450.0);
		}
		int top = y + (int) ((1 - e) * 70) - (hover ? 6 : 0);
		if (pen.abyssal) {
			// An Abyssal card: violet, slow to breathe, and it does not light up when looked at
			double beat = Ui.pulse(now, 2600, i);
			accent = Ui.mix(Ui.abyss(), 0x000000, 0.25 * (1 - beat));
		} else if (pen.extreme) {
			// A rare Extreme card: blood-red and restless
			double beat = Ui.pulse(now, 1382, i);
			accent = Ui.mix(Ui.setColour(Penalty.Group.EXTREME), 0xFFFFFF, 0.2 * beat);
		} else if (pen.everywhere) {
			accent = Ui.mix(Ui.setColour(Penalty.Group.EVERYWHERE), accent, 0.25);
		}
		// Hovered cards light up gold; an Extreme card only warms, it never loses its blood-red
		int border = pen.abyssal ? (hover ? Ui.mix(Ui.abyss(), 0xFFFFFF, 0.3) : accent)
				: hover ? (pen.extreme ? Ui.mix(Ui.setColour(Penalty.Group.EXTREME), Ui.WARN, 0.45) : Ui.WARN) : accent;
		if (i == chosen) {
			border = Ui.calm() || (now / 70) % 2 == 0 ? 0xFFE066 : 0xFF8800;
		}
		Ui.panel(g, x, top, CARD_W, CARD_H, border, fade);
		if (pen.abyssal) {
			g.fill(x + 1, top + 1, x + CARD_W - 1, top + CARD_H - 1, Ui.argb(fade * 0.85, 0x050008));
			crack(g, pen.id, x, top, now, fade);
			banner(g, Ui.tr("gui.extremedimensions.choice.abyssal_mark"), x, top, Ui.abyss(), 0xEEDDFF, fade);
		} else if (pen.extreme) {
			g.fill(x + 1, top + 1, x + CARD_W - 1, top + CARD_H - 1, Ui.argb(fade * 0.12, Ui.setColour(Penalty.Group.EXTREME)));
			banner(g, Ui.tr("gui.extremedimensions.choice.extreme_mark"), x, top, Ui.setColour(Penalty.Group.EXTREME), 0xFFE0E0, fade);
		}
		if (hover && !pen.extreme) {
			g.fill(x + 1, top + 1, x + CARD_W - 1, top + CARD_H - 1, Ui.argb(0.08, Ui.WARN));
		}
		if (i == chosen) {
			double fl = Ui.calm() ? 0.35 : 0.5 + 0.5 * Math.sin((now - chosenAt) / 40.0);
			g.fill(x, top, x + CARD_W, top + CARD_H, Ui.argb((int) (90 * fl), 0xFFE066));
		}

		int mid = x + CARD_W / 2;
		Ui.runeSocket(g, pen.id, mid - 9, top + 8, border, fade, hover ? 1 : 0.4);
		boolean plain = opt.readable();
		Ui.centered(g, this.font, Ui.ellipsize(this.font, Ui.curse(Ui.tr(pen.nameKey()), plain), CARD_W - 10), mid, top + 32, Ui.PARCHMENT, fade);
		Ui.divider(g, x + 14, top + 45, CARD_W - 28, border, fade);

		int ly = top + 52;
		// Stacks past the maximum spill over onto other curses; the third line says how many
		int spill = Math.max(0, opt.stacks() + opt.mult() - opt.max());
		String third = spill > 0 ? Ui.tr("gui.extremedimensions.choice.spill", spill)
				: Ui.tr(pen.abyssal ? "gui.extremedimensions.choice.abyssal" : pen.extreme ? "gui.extremedimensions.choice.extreme"
						: pen.everywhere ? "gui.extremedimensions.choice.everywhere" : "gui.extremedimensions.choice.permanent", opt.max());
		String[] lines = {Ui.curse(Ui.tr(pen.lineKey(1), opt.value()), plain), Ui.curse(Ui.tr(pen.lineKey(2), opt.value()), plain), third};
		for (int k = 0; k < lines.length; k++) {
			int col = k < 2 ? Ui.TEXT : spill > 0 ? Ui.WARN : pen.abyssal ? Ui.mix(Ui.abyss(), 0xFFFFFF, 0.35)
					: pen.extreme ? Ui.mix(Ui.setColour(Penalty.Group.EXTREME), 0xFFFFFF, 0.3)
					: pen.everywhere ? Ui.mix(Ui.setColour(Penalty.Group.EVERYWHERE), Ui.MUTED, 0.4) : Ui.MUTED;
			centredCut(g, lines[k], mid, ly, CARD_W - 8, col, fade, mouseX, mouseY);
			ly += 11;
		}
		// Stacks now, and after this card (capped at the maximum)
		int after = Math.min(opt.max(), opt.stacks() + opt.mult());
		String s = after >= opt.max()
				? Ui.tr("gui.extremedimensions.choice.stacks", opt.stacks(), after, opt.max()) + " " + Ui.tr("gui.extremedimensions.choice.capped")
				: Ui.tr("gui.extremedimensions.choice.stacks", opt.stacks(), after, opt.max());
		g.text(this.font, s, mid - this.font.width(s) / 2, ly + 1, Ui.argb(fade, opt.stacks() > 0 || opt.mult() > 1 ? Ui.DANGER : Ui.WARN));
		if (opt.mult() > 1) {
			seal(g, opt.mult(), x + CARD_W - 18, top - 7, now, fade);
		}
		int loreY = top + 100;
		// What all its stacks come to once this card is taken (3.55.0): the lines above read one stack. Never for a card
		// still in runes, whose number is not the player's to know yet
		String total = plain ? CurseLine.total("after", pen, after, opt.value()) : null;
		if (total != null) {
			centredCut(g, total, mid, ly + 12, CARD_W - 8, Ui.WARN, fade, mouseX, mouseY);
			loreY += 9;
		}
		if (!plain) {
			// Unknown: one line of its lore in runes, and how it comes to be read
			String runes = Ui.ellipsize(this.font, "“" + Ui.curse(Ui.tr(pen.loreKey()), false) + "”", CARD_W - 16);
			g.text(this.font, runes, mid - this.font.width(runes) / 2, loreY, Ui.argb(fade * 0.85, Ui.mix(Ui.SOUL, Ui.TEXT, 0.4)));
			loreY += 11;
			for (String line : Ui.wrap(this.font, Ui.tr("gui.extremedimensions.choice.unknown"), CARD_W - 16)) {
				g.text(this.font, line, mid - this.font.width(line) / 2, loreY, Ui.argb(fade, Ui.MUTED));
				loreY += 10;
			}
			return;
		}
		String lore = "“" + Ui.tr(pen.loreKey()) + "”";
		List<String> wrapped = Ui.wrap(this.font, lore, CARD_W - 16);
		// Under a total the lore keeps clear of the button: what is cut is read whole on hover
		int room = total != null ? Math.max(1, (top + CARD_H - 30 - loreY) / 10) : wrapped.size();
		int from = loreY;
		for (int k = 0; k < wrapped.size() && k < room; k++) {
			String line = k == room - 1 && wrapped.size() > room ? Ui.ellipsize(this.font, wrapped.get(k) + " ...", CARD_W - 16) : wrapped.get(k);
			g.text(this.font, line, mid - this.font.width(line) / 2, loreY, Ui.argb(fade * 0.85, Ui.mix(Ui.SOUL, Ui.TEXT, 0.4)));
			loreY += 10;
		}
		if (wrapped.size() > room && chosen < 0 && mouseX >= x && mouseX <= x + CARD_W && mouseY >= from - 1 && mouseY < loreY) {
			loreTip = Ui.wrap(this.font, lore, 200);
		}
	}

	/**
	 * A line centred on a card, cut to a width (3.55.0): drawn with an ellipsis where it had to be cut, and read whole in
	 * a tooltip while the mouse is over it.
	 */
	private void centredCut(GuiGraphicsExtractor g, String text, int mid, int y, int w, int colour, double fade, int mouseX, int mouseY) {
		String shown = Ui.ellipsize(this.font, text, w);
		int sx = mid - this.font.width(shown) / 2;
		g.text(this.font, shown, sx, y, Ui.argb(fade, colour));
		if (!shown.equals(text) && chosen < 0 && mouseX >= sx && mouseX < sx + this.font.width(shown) && mouseY >= y - 1 && mouseY < y + 10) {
			g.setTooltipForNextFrame(this.font, Component.literal(text), mouseX, mouseY);
		}
	}

	/** The mark above a card's top edge: EXTREME on blood-red, ABYSSAL on violet. */
	private void banner(GuiGraphicsExtractor g, String mark, int x, int top, int colour, int ink, double fade) {
		int mw = this.font.width(mark) + 8, mx = x + CARD_W / 2 - mw / 2;
		g.fill(mx, top - 6, mx + mw, top + 5, Ui.argb(fade, Ui.SHADOW));
		g.fill(mx + 1, top - 5, mx + mw - 1, top + 4, Ui.argb(fade, Ui.mix(Ui.INK, colour, 0.6)));
		g.text(this.font, mark, mx + 4, top - 4, Ui.argb(fade, ink));
	}

	/**
	 * The crack through an Abyssal card: a jagged violet line from the top edge to the bottom, the same for each curse,
	 * with a faint glow round it that flickers now and then.
	 */
	private void crack(GuiGraphicsExtractor g, String seed, int x, int top, long now, double fade) {
		int colour = Ui.abyss();
		long base = seed.hashCode() * 31L;
		double flicker = Ui.calm() ? 1.0 : 0.75 + 0.25 * Math.sin(now / 90.0 + Ui.hash(now / 400) * 6);
		int cx = x + CARD_W / 2 + (int) (Ui.hash(base) * 30 - 15);
		for (int yy = top + 2; yy < top + CARD_H - 2; yy += 6) {
			int nx = Math.max(x + 8, Math.min(x + CARD_W - 9, cx + (int) (Ui.hash(base + yy) * 9 - 4)));
			int lo = Math.min(cx, nx), hi = Math.max(cx, nx);
			int end = Math.min(yy + 6, top + CARD_H - 2);
			g.fill(lo - 2, yy, hi + 3, end, Ui.argb(fade * 0.12 * flicker, colour));
			g.fill(cx, yy, cx + 1, yy + 3, Ui.argb(fade * 0.7 * flicker, colour));
			g.fill(lo, yy + 3, hi + 1, yy + 4, Ui.argb(fade * 0.7 * flicker, colour));
			g.fill(nx, yy + 3, nx + 1, end, Ui.argb(fade * 0.7 * flicker, colour));
			cx = nx;
		}
	}

	/** The multiplier seal on a card's corner: "3x" in a red-gold diamond that breathes. */
	private void seal(GuiGraphicsExtractor g, int mult, int x, int y, long now, double fade) {
		double beat = Ui.pulse(now, 1005, mult);
		int hot = Ui.mix(Ui.DANGER, 0xFFE066, mult >= 4 ? 0.2 + 0.5 * beat : mult == 3 ? 0.35 * beat : 0.15 * beat);
		int w = 26, h = 16;
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Ui.argb(fade, Ui.SHADOW));
		g.fill(x, y, x + w, y + h, Ui.argb(fade, hot));
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, Ui.argb(fade, Ui.mix(Ui.INK, hot, 0.35)));
		g.fill(x + 2, y + 1, x + w - 2, y + 2, Ui.argb(fade * 0.5, 0xFFFFFF));
		String t = mult + "x";
		g.text(this.font, t, x + w / 2 - this.font.width(t) / 2, y + 4, Ui.argb(fade, 0xFFF4D0), true);
	}

	public boolean shouldCloseOnEsc() {
		return false;
	}

	public boolean isPauseScreen() {
		return false;
	}
}
