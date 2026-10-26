package com.extremedimensions.client.screen;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.config.Config;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.tapestry.Tapestry;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The tapestry on the Weaver's Loom: six panels in a frame of three by two. A woven panel shows its picture, its name
 * and what it gives in this world; one not yet woven shows the bare warp, the deed that earns its thread, and where the
 * player stands with it. Once the tapestry is finished, the loom offers New Thread+.
 */
public class TapestryScreen extends Screen {
	private static final Identifier SHEET = ExtremeDimensions.id("textures/gui/tapestry.png");
	private static final int PW = 396, PH = 236, CELL_W = 122, CELL_H = 88, ART = 40, UNWEAVE_W = 76;
	/** The loom's own colour: the silk of its warp. */
	private static final int ACCENT = 0xBBA8F0;

	private static TapestryScreen shown;

	private final long openAt = System.currentTimeMillis();
	private int woven, deeds, carried, threads, againMinutes;
	private boolean done, weaving;
	private String ending = "", last = "";
	private Button newThread, unweave;

	/** The tapestry screen on show, or null (for the tests). */
	public static TapestryScreen shown() {
		return shown;
	}

	/** The server sent the tapestry: show it, or refresh the one on show. */
	public static void receive(String data) {
		if (shown != null) {
			shown.update(data);
		} else {
			Minecraft.getInstance().gui.setScreen(new TapestryScreen(data));
		}
	}

	public TapestryScreen(String data) {
		super(Component.translatable("gui.extremedimensions.tapestry.screen"));
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
		if (f.length >= 9) {
			woven = num(f[0]);
			ending = f[1];
			deeds = num(f[2]);
			carried = num(f[3]);
			done = "1".equals(f[4]);
			threads = num(f[5]);
			weaving = "1".equals(f[6]);
			againMinutes = num(f[7]);
			last = f[8];
		}
		refresh();
	}

	/** Whether a panel is woven (for the tests). */
	public boolean woven(Tapestry.Panel p) {
		return (woven & 1 << p.ordinal()) != 0;
	}

	private boolean has(int mask, Tapestry.Panel p) {
		return (mask & 1 << p.ordinal()) != 0;
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
		newThread = this.addRenderableWidget(Button.builder(Component.translatable("gui.extremedimensions.tapestry.new_thread_button"), b -> confirm())
				.pos(x0 + 14, y0 + PH - 22).size(150, 18).build());
		unweave = this.addRenderableWidget(Button.builder(Component.translatable("gui.extremedimensions.tapestry.unweave_button"),
				b -> ClientPlayNetworking.send(new ModPackets.UnweaveRequestPayload(""))).pos(x0 + PW - 14 - 70 - 4 - UNWEAVE_W, y0 + PH - 22).size(UNWEAVE_W, 18).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).pos(x0 + PW - 14 - 70, y0 + PH - 22).size(70, 18).build());
		refresh();
	}

	private void refresh() {
		if (newThread != null) {
			newThread.visible = done;
		}
		if (unweave != null) {
			unweave.visible = Config.UNWEAVING > 0;
		}
	}

	/** New Thread+ asks once more before the whole world takes it up. */
	private void confirm() {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				ClientPlayNetworking.send(new ModPackets.NewThreadPayload());
			}
			mc.gui.setScreen(this);
		}, Component.translatable("gui.extremedimensions.tapestry.new_thread_title"),
				Component.translatable("gui.extremedimensions.tapestry.new_thread_confirm", threads + 1,
						Math.round(Config.NEW_THREAD_ELDER * 100), Math.round(Config.NEW_THREAD_ATONEMENT * 100))));
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
		Ui.heading(g, this.font, Ui.tr("gui.extremedimensions.tapestry.screen").toUpperCase(), x0 + PW / 2, y0 + 8, ACCENT, fade);
		String world = Ui.tr(ending.isEmpty() ? "gui.extremedimensions.tapestry.world_unended"
				: "severed".equals(ending) ? "gui.extremedimensions.tapestry.world_severed" : "gui.extremedimensions.tapestry.world_mended");
		Ui.centered(g, this.font, Ui.ellipsize(this.font, world, PW - 28), x0 + PW / 2, y0 + 22, ending.isEmpty() ? Ui.WARN : Ui.MUTED, fade);
		Tapestry.Panel[] panels = Tapestry.Panel.values();
		for (int i = 0; i < panels.length; i++) {
			cell(g, panels[i], x0 + 14 + (i % 3) * (CELL_W + 3), y0 + 36 + (i / 3) * (CELL_H + 4), fade);
		}
		// (between the New Thread+ button, when there is one, and the Unweave and Done buttons)
		int sx = x0 + 14 + (done ? 156 : 0);
		int ex = x0 + PW - 14 - 70 - 6 - (Config.UNWEAVING > 0 ? UNWEAVE_W + 4 : 0);
		g.text(this.font, Ui.ellipsize(this.font, status(), ex - sx), sx, y0 + PH - 17,
				Ui.argb(fade, weaving ? Ui.DANGER : done ? Ui.OK : Ui.MUTED));
	}

	private String status() {
		int count = Integer.bitCount(woven & 0x3F);
		if (weaving) {
			return Ui.tr("gui.extremedimensions.tapestry.status_weaving");
		}
		if (done) {
			return threads > 0 ? Ui.tr("gui.extremedimensions.tapestry.status_threads", Ui.roman(threads)) : Ui.tr("gui.extremedimensions.tapestry.status_done");
		}
		if (againMinutes > 0 && !last.isEmpty()) {
			return Ui.tr("gui.extremedimensions.tapestry.status_again", Ui.tr("gui.extremedimensions.tapestry.panel." + last), againMinutes);
		}
		return Ui.tr("gui.extremedimensions.tapestry.status_count", count, Tapestry.Panel.values().length);
	}

	/** One panel: its picture (or the bare warp), its name, and what it gives or what earns it. */
	private void cell(GuiGraphicsExtractor g, Tapestry.Panel p, int x, int y, double fade) {
		boolean isWoven = woven(p);
		g.fill(x, y, x + CELL_W, y + CELL_H, Ui.argb(fade * 0.35, isWoven ? 0x2A2238 : 0x14101C));
		g.fill(x, y, x + CELL_W, y + 1, Ui.argb(fade * 0.6, isWoven ? ACCENT : Ui.TRACK));
		int tile = isWoven ? p.ordinal() : Tapestry.Panel.values().length;
		// (the sheet's tiles are drawn at their own size, one texel to a pixel of the screen, so they stay sharp)
		g.blit(RenderPipelines.GUI_TEXTURED, SHEET, x + 4, y + 5, tile * (float) ART, 0f, ART, ART, ART * (Tapestry.Panel.values().length + 1), ART);
		int tx = x + ART + 9, tw = CELL_W - ART - 13;
		String name = Ui.tr(p.nameKey());
		g.text(this.font, Ui.ellipsize(this.font, name, tw), tx, y + 6, Ui.argb(fade, isWoven ? Ui.PARCHMENT : Ui.MUTED));
		int ly = y + 18;
		String body;
		int colour;
		if (isWoven) {
			body = Ui.tr(p.boonKey(ending), p.boonArgs(ending));
			colour = "severed".equals(ending) ? 0xFF8888 : 0x88FFDD;
		} else {
			body = Ui.tr(p.deedKey());
			colour = Ui.MUTED;
		}
		for (String line : Ui.wrap(this.font, body, tw)) {
			if (ly > y + CELL_H - 22) {
				break;
			}
			g.text(this.font, line, tx, ly, Ui.argb(fade, colour));
			ly += 9;
		}
		if (!isWoven) {
			// Where the player stands with it: the thread in hand, the deed done but the thread lost, or still to do
			String state = has(carried, p) ? Ui.tr(ending.isEmpty() ? "gui.extremedimensions.tapestry.state_carried_wait" : "gui.extremedimensions.tapestry.state_carried")
					: has(deeds, p) ? Ui.tr("gui.extremedimensions.tapestry.state_lost", Math.max(0, Config.TAPESTRY_RESPIN_SILK))
					: Ui.tr("gui.extremedimensions.tapestry.state_todo");
			int sc = has(carried, p) ? Ui.OK : has(deeds, p) ? Ui.WARN : 0x777777;
			int sy = y + CELL_H - 20;
			for (String line : Ui.wrap(this.font, state, CELL_W - 10)) {
				if (sy > y + CELL_H - 10) {
					break;
				}
				g.text(this.font, line, x + 5, sy, Ui.argb(fade, sc));
				sy += 9;
			}
		}
	}
}
