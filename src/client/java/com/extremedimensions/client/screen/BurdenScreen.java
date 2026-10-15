package com.extremedimensions.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.penalty.Penalty;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * An ally's burden (sneak and an empty hand on them): the curses they carry, each with its stacks and how many of it
 * the player carries already, and a button to offer to carry one stack of it for them (they are asked first).
 */
public class BurdenScreen extends Screen {
	private static final int PANEL_W = 300;
	private static final int ROW_H = 24;
	/** Curses shown at once: up to seven, fewer in a small window. */
	private int rows = 7;

	private record Row(Penalty pen, int stacks, int max, int mine) {}

	private final UUID friend;
	private final String name;
	private final List<Row> list = new ArrayList<>();
	private final long openAt = System.currentTimeMillis();
	private int page;

	public BurdenScreen(UUID friend, String name, String data) {
		super(Component.translatable("gui.extremedimensions.burden.title", name));
		this.friend = friend;
		this.name = name;
		for (String entry : data.split(";")) {
			String[] kv = entry.split(":");
			if (kv.length < 4) {
				continue;
			}
			Penalty pen = Penalty.byId(kv[0]);
			if (pen == null) {
				continue;
			}
			try {
				list.add(new Row(pen, Integer.parseInt(kv[1]), Integer.parseInt(kv[2]), Integer.parseInt(kv[3])));
			} catch (NumberFormatException e) {
				// skip a damaged entry
			}
		}
	}

	private int panelH() {
		return 64 + Math.max(1, Math.min(this.rows, this.list.size())) * ROW_H + 30;
	}

	private int pages() {
		return Math.max(1, (list.size() + rows - 1) / rows);
	}

	@Override
	protected void init() {
		this.clearWidgets();
		rows = Math.max(2, Math.min(7, (this.height - 12 - 94) / ROW_H));
		page = Math.min(page, pages() - 1);
		int x = this.width / 2 - PANEL_W / 2, y = this.height / 2 - panelH() / 2;
		int first = page * rows;
		for (int i = first; i < Math.min(list.size(), first + rows); i++) {
			Row r = list.get(i);
			// Room for the stack and its toll
			boolean full = r.mine() + 1 + Math.max(0, com.extremedimensions.config.Config.COOP_BURDEN_TOLL) > r.max();
			Button carry = Button.builder(Component.translatable(full ? "gui.extremedimensions.burden.full" : "gui.extremedimensions.burden.carry"), b -> {
				ClientPlayNetworking.send(new ModPackets.CarryPayload(friend, r.pen().id));
				this.onClose();
			}).pos(x + PANEL_W - 14 - 76, y + 56 + (i - first) * ROW_H).size(76, 18).build();
			carry.active = !full;
			this.addRenderableWidget(carry);
		}
		int by = y + panelH() - 26;
		if (pages() > 1) {
			this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
				page = (page + pages() - 1) % pages();
				this.init();
			}).pos(x + 14, by).size(20, 18).build());
			this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
				page = (page + 1) % pages();
				this.init();
			}).pos(x + 38, by).size(20, 18).build());
		}
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
				.pos(this.width / 2 - 40, by).size(80, 18).build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		double pe = Ui.ease((System.currentTimeMillis() - openAt) / 300.0);
		int x = this.width / 2 - PANEL_W / 2, y = this.height / 2 - panelH() / 2;
		int cx = this.width / 2;
		Ui.panel(g, x, y, PANEL_W, panelH(), Ui.SOUL_DIM, pe);
		Ui.heading(g, this.font, Ui.tr("gui.extremedimensions.burden.title", name), cx, y + 9, Ui.SOUL, pe);
		int ty = y + 26;
		for (String line : Ui.wrap(this.font, Ui.tr("gui.extremedimensions.burden.hint"), PANEL_W - 28)) {
			Ui.centered(g, this.font, line, cx, ty, Ui.MUTED, pe);
			ty += 10;
		}
		if (list.isEmpty()) {
			Ui.centered(g, this.font, Ui.tr("gui.extremedimensions.burden.none", name), cx, y + 64, Ui.PARCHMENT, pe);
			return;
		}
		int first = page * rows;
		for (int i = first; i < Math.min(list.size(), first + rows); i++) {
			Row r = list.get(i);
			int ry = y + 56 + (i - first) * ROW_H;
			int accent = Ui.setColour(r.pen().group);
			Ui.runeSocket(g, r.pen().id, x + 14, ry, accent, pe, 0.4);
			g.text(this.font, Ui.tr(r.pen().nameKey()), x + 38, ry, Ui.argb(pe, Ui.PARCHMENT));
			String stacks = r.stacks() + "/" + r.max() + "   " + Ui.tr("gui.extremedimensions.burden.yours", r.mine(), r.max());
			g.text(this.font, stacks, x + 38, ry + 10, Ui.argb(pe, r.mine() >= r.max() ? Ui.WARN : Ui.MUTED));
		}
		if (pages() > 1) {
			String p = (page + 1) + "/" + pages();
			g.text(this.font, p, x + 64, y + panelH() - 21, Ui.argb(pe, Ui.MUTED));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
