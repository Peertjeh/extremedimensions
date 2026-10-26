package com.extremedimensions.client.screen;

import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.penalty.Penalty;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Unweaving at the Weaver's Loom: one row for each curse set, with how full it is, what unweaving it costs, and the
 * button that begins it. A set can be unwoven once it is full, the loom is free, the wait is over and the price is in
 * the bag; the server checks it all again.
 */
public class UnweaveScreen extends Screen {
	private static final int PW = 380, PH = 210, ROW_H = 34, BTN_W = 74;
	/** The loom's own colour, as on the tapestry. */
	private static final int ACCENT = 0xBBA8F0;

	private static UnweaveScreen shown;

	/** One set as the server sent it. */
	private record Row(Penalty.Group set, int stacks, int most, int ingots, int cords, boolean full) {
	}

	private final long openAt = System.currentTimeMillis();
	private int ingots, cords, waitMinutes;
	private boolean busy;
	private Row[] rows = new Row[0];
	private final Button[] buttons = new Button[4];

	/** The Unweaving screen on show, or null (for the tests). */
	public static UnweaveScreen shown() {
		return shown;
	}

	/** The server sent the Unweaving: show it, or refresh the one on show. */
	public static void receive(String data) {
		if (shown != null) {
			shown.update(data);
		} else {
			Minecraft.getInstance().gui.setScreen(new UnweaveScreen(data));
		}
	}

	public UnweaveScreen(String data) {
		super(Component.translatable("gui.extremedimensions.unweave.screen"));
		update(data);
	}

	private static int num(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private void update(String data) {
		String[] f = (data == null ? "" : data).split(";", -1);
		if (f.length >= 5) {
			ingots = num(f[0]);
			cords = num(f[1]);
			waitMinutes = num(f[2]);
			busy = "1".equals(f[3]);
			String[] sets = f[4].isEmpty() ? new String[0] : f[4].split("\\|");
			Row[] parsed = new Row[Math.min(buttons.length, sets.length)];
			for (int i = 0; i < parsed.length; i++) {
				String[] r = sets[i].split(",", -1);
				Penalty.Group g = r.length >= 6 ? group(r[0]) : null;
				parsed[i] = g == null ? null : new Row(g, num(r[1]), num(r[2]), num(r[3]), num(r[4]), "1".equals(r[5]));
			}
			rows = parsed;
		}
		refresh();
	}

	private static Penalty.Group group(String name) {
		for (Penalty.Group g : Penalty.Group.values()) {
			if (g.name().equals(name)) {
				return g;
			}
		}
		return null;
	}

	private static boolean creative() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.player.isCreative();
	}

	private boolean affords(Row r) {
		return creative() || ingots >= r.ingots() && cords >= r.cords();
	}

	/** Whether this set can be unwoven now, as far as the screen knows (for the tests too). */
	public boolean ready(int i) {
		Row r = i < rows.length ? rows[i] : null;
		return r != null && r.full() && !busy && waitMinutes <= 0 && affords(r);
	}

	private int x0() {
		return Math.max(2, (this.width - PW) / 2);
	}

	private int y0() {
		return Math.max(4, (this.height - PH) / 2);
	}

	private int rowY(int i) {
		return y0() + 34 + i * (ROW_H + 4);
	}

	@Override
	protected void init() {
		shown = this;
		int x0 = x0();
		for (int i = 0; i < buttons.length; i++) {
			int at = i;
			buttons[i] = this.addRenderableWidget(Button.builder(Component.translatable("gui.extremedimensions.unweave.button"), b -> confirm(at))
					.pos(x0 + PW - 14 - BTN_W, rowY(i) + (ROW_H - 18) / 2).size(BTN_W, 18).build());
		}
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).pos(x0 + PW - 14 - 70, y0() + PH - 22).size(70, 18).build());
		refresh();
	}

	private void refresh() {
		for (int i = 0; i < buttons.length; i++) {
			if (buttons[i] != null) {
				buttons[i].visible = i < rows.length && rows[i] != null;
				buttons[i].active = ready(i);
			}
		}
	}

	/** Unweaving asks once more: the price is taken at once, and only half comes back if the knots hold. */
	private void confirm(int i) {
		Row r = i < rows.length ? rows[i] : null;
		if (r == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		mc.gui.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				ClientPlayNetworking.send(new ModPackets.UnweaveRequestPayload(r.set().name()));
				mc.gui.setScreen(null);
			} else {
				mc.gui.setScreen(this);
			}
		}, Component.translatable("gui.extremedimensions.unweave.confirm_title", setName(r.set())),
				Component.translatable("gui.extremedimensions.unweave.confirm", setName(r.set()), r.ingots(), r.cords())));
	}

	private static Component setName(Penalty.Group g) {
		return Component.translatable("gui.extremedimensions.curses.tab_" + g.id());
	}

	@Override
	public void removed() {
		super.removed();
		if (shown == this) {
			shown = null;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		double fade = Ui.ease((System.currentTimeMillis() - openAt) / 260.0);
		int x0 = x0(), y0 = y0();
		g.fill(0, 0, this.width, this.height, Ui.argb((int) (80 * fade), 0x0C0816));
		Ui.panel(g, x0, y0, PW, PH, ACCENT, fade);
		Ui.heading(g, this.font, Ui.tr("gui.extremedimensions.unweave.screen").toUpperCase(), x0 + PW / 2, y0 + 8, ACCENT, fade);
		Ui.centered(g, this.font, Ui.ellipsize(this.font, Ui.tr("gui.extremedimensions.unweave.intro"), PW - 28), x0 + PW / 2, y0 + 22, Ui.MUTED, fade);
		for (int i = 0; i < rows.length; i++) {
			if (rows[i] != null) {
				row(g, rows[i], x0 + 14, rowY(i), fade);
			}
		}
		String status = busy ? Ui.tr("gui.extremedimensions.unweave.status_busy")
				: waitMinutes > 0 ? Ui.tr("gui.extremedimensions.unweave.status_wait", waitMinutes)
				: Ui.tr("gui.extremedimensions.unweave.status_carried", ingots, cords);
		g.text(this.font, Ui.ellipsize(this.font, status, PW - 28 - 76), x0 + 14, y0 + PH - 17,
				Ui.argb(fade, busy || waitMinutes > 0 ? Ui.WARN : Ui.MUTED));
	}

	/** One set: its colour and name, how full it is, and the price. */
	private void row(GuiGraphicsExtractor g, Row r, int x, int y, double fade) {
		int colour = Ui.setColour(r.set());
		int w = PW - 28;
		g.fill(x, y, x + w, y + ROW_H, Ui.argb(fade * 0.35, r.full() ? 0x2A2238 : 0x14101C));
		g.fill(x, y, x + 2, y + ROW_H, Ui.argb(fade, colour));
		int tw = w - BTN_W - 16;
		g.text(this.font, Ui.ellipsize(this.font, setName(r.set()).getString(), tw / 2), x + 8, y + 5, Ui.argb(fade, r.full() ? Ui.PARCHMENT : Ui.MUTED));
		String fill = Ui.tr("gui.extremedimensions.unweave.fill", r.stacks(), r.most());
		g.text(this.font, fill, x + 8 + tw - this.font.width(fill), y + 5, Ui.argb(fade, r.full() ? Ui.OK : Ui.MUTED));
		Ui.meter(g, x + 8, y + 16, tw, 3, r.most() <= 0 ? 0 : Math.min(1.0, r.stacks() / (double) r.most()), colour, fade, r.full());
		String price = r.full() ? Ui.tr("gui.extremedimensions.unweave.price", r.ingots(), r.cords()) : Ui.tr("gui.extremedimensions.unweave.not_full_yet");
		int pc = !r.full() ? 0x777777 : affords(r) ? Ui.MUTED : Ui.DANGER;
		g.text(this.font, Ui.ellipsize(this.font, price, tw), x + 8, y + 23, Ui.argb(fade, pc));
	}
}
