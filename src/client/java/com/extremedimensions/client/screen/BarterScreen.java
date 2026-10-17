package com.extremedimensions.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.tribe.BarterService;
import com.extremedimensions.tribe.Standing;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Barter with a Hollowling: the player's standing with the tribe, and today's wares (what it wants, what it gives,
 * how many more times today), each with a button to buy it.
 */
public class BarterScreen extends Screen {
	private static final int PANEL_W = 320;
	private static final int ROW_H = 24;
	/** Room for the title, the standing and its meter, and for the hint and the buttons below the wares. */
	private static final int HEAD = 56, FOOT = 48;

	private record Offer(BarterService.Ware ware, int price, int left) {}

	private final int young;
	private final int rep;
	private final List<Offer> offers = new ArrayList<>();
	private final long openAt;
	/** Wares shown at once (as many as the window has room for) and the page of them shown. */
	private int rows = 1;
	private int page;

	private BarterScreen(int young, int rep, String data, long openAt) {
		super(Component.translatable("gui.extremedimensions.barter.title"));
		this.young = young;
		this.rep = rep;
		this.openAt = openAt;
		for (String entry : data.split(";")) {
			String[] kv = entry.split(":");
			if (kv.length < 3) {
				continue;
			}
			try {
				BarterService.Ware w = BarterService.ware(Integer.parseInt(kv[0]));
				if (w != null) {
					offers.add(new Offer(w, Integer.parseInt(kv[1]), Integer.parseInt(kv[2])));
				}
			} catch (NumberFormatException e) {
				// skip a damaged entry
			}
		}
	}

	/** The barter screen open now, if any. */
	private static BarterScreen shown;

	/** Opens the screen, or refreshes it in place after a barter (without playing its opening again). */
	public static void show(int young, int rep, String data) {
		BarterScreen before = shown;
		long openAt = before != null && before.young == young ? before.openAt : System.currentTimeMillis();
		BarterScreen next = new BarterScreen(young, rep, data, openAt);
		Minecraft.getInstance().gui.setScreen(next);
		shown = next;
	}

	@Override
	public void removed() {
		super.removed();
		if (shown == this) {
			shown = null;
		}
	}

	private static int colour(Standing.Level level) {
		return switch (level) {
			case HUNTED -> Ui.BLOOD;
			case HATED -> Ui.DANGER;
			case WARY -> Ui.MUTED;
			case TOLERATED -> Ui.WARN;
			case TRUSTED -> Ui.OK;
			case KIN -> Ui.SOUL;
		};
	}

	private int panelH() {
		return HEAD + Math.max(1, rows) * ROW_H + FOOT;
	}

	private int pages() {
		return Math.max(1, (offers.size() + rows - 1) / rows);
	}

	private int have(Item item) {
		var p = Minecraft.getInstance().player;
		if (p == null) {
			return 0;
		}
		int n = 0;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	@Override
	protected void init() {
		this.clearWidgets();
		rows = Math.max(2, Math.min(Math.max(1, offers.size()), (this.height - 12 - HEAD - FOOT) / ROW_H));
		page = Math.min(page, pages() - 1);
		int x = this.width / 2 - PANEL_W / 2, y = this.height / 2 - panelH() / 2;
		int first = page * rows;
		for (int i = first; i < Math.min(offers.size(), first + rows); i++) {
			Offer o = offers.get(i);
			boolean can = o.left() > 0 && have(o.ware().want()) >= o.price();
			String label = o.left() <= 0 ? "gui.extremedimensions.barter.sold" : "gui.extremedimensions.barter.buy";
			Button buy = Button.builder(Component.translatable(label), b ->
					ClientPlayNetworking.send(new ModPackets.BarterTradePayload(young, o.ware().id())))
					.pos(x + PANEL_W - 14 - 64, y + HEAD + (i - first) * ROW_H).size(64, 18).build();
			buy.active = can;
			this.addRenderableWidget(buy);
		}
		int by = y + panelH() - 24;
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
		Standing.Level level = Standing.level(rep);
		int accent = colour(level);
		Ui.panel(g, x, y, PANEL_W, panelH(), Ui.SOUL_DIM, pe);
		Ui.heading(g, this.font, Ui.tr("gui.extremedimensions.barter.title"), cx, y + 9, Ui.SOUL, pe);
		// Standing: the level by name, and where the player stands from -100 to 100
		String label = Ui.tr("gui.extremedimensions.barter.standing") + ": ";
		String name = Ui.tr(level.key()) + " (" + (rep > 0 ? "+" : "") + rep + ")";
		int lw = this.font.width(label), nw = this.font.width(name);
		g.text(this.font, label, cx - (lw + nw) / 2, y + 22, Ui.argb(pe, Ui.MUTED));
		g.text(this.font, name, cx - (lw + nw) / 2 + lw, y + 22, Ui.argb(pe, accent));
		Ui.meter(g, x + 30, y + 35, PANEL_W - 60, 5, (rep - Standing.MIN) / (double) (Standing.MAX - Standing.MIN), accent, pe, false);
		int zero = x + 30 + (PANEL_W - 60) / 2;
		g.fill(zero, y + 33, zero + 1, y + 42, Ui.argb(pe, Ui.PARCHMENT));
		Ui.divider(g, x + 14, y + 48, PANEL_W - 28, Ui.SOUL_DIM, pe);
		if (offers.isEmpty()) {
			Ui.centered(g, this.font, Ui.tr("gui.extremedimensions.barter.nothing"), cx, y + HEAD + 6, Ui.PARCHMENT, pe);
		}
		int first = page * rows;
		for (int i = first; i < Math.min(offers.size(), first + rows); i++) {
			Offer o = offers.get(i);
			int ry = y + HEAD + (i - first) * ROW_H;
			boolean kin = o.ware().id() == BarterService.RELIC || o.ware().id() == BarterService.CLOTH;
			if (kin) {
				g.fill(x + 8, ry - 3, x + PANEL_W - 8, ry + ROW_H - 5, Ui.argb(pe * 0.18, Ui.SOUL));
			}
			// What it wants
			g.fakeItem(new ItemStack(o.ware().want()), x + 14, ry);
			int have = have(o.ware().want());
			g.text(this.font, "×" + o.price(), x + 32, ry + 5, Ui.argb(pe, have >= o.price() ? Ui.PARCHMENT : Ui.DANGER));
			g.text(this.font, "→", x + 58, ry + 5, Ui.argb(pe, Ui.MUTED));
			// What it gives
			ItemStack give = new ItemStack(o.ware().give(), o.ware().count());
			g.fakeItem(give, x + 72, ry);
			String giveName = o.ware().id() == BarterService.RELIC ? Ui.tr("gui.extremedimensions.barter.relic")
					: (o.ware().count() > 1 ? o.ware().count() + "× " : "") + give.getHoverName().getString();
			g.text(this.font, Ui.ellipsize(this.font, giveName, PANEL_W - 190), x + 92, ry, Ui.argb(pe, kin ? Ui.SOUL : Ui.PARCHMENT));
			g.text(this.font, Ui.tr("gui.extremedimensions.barter.left", o.left(), o.ware().uses()), x + 92, ry + 10,
					Ui.argb(pe, o.left() > 0 ? Ui.MUTED : Ui.WARN));
		}
		int hy = y + panelH() - FOOT + 2;
		for (String line : Ui.wrap(this.font, Ui.tr("gui.extremedimensions.barter.hint"), PANEL_W - 28)) {
			Ui.centered(g, this.font, line, cx, hy, Ui.MUTED, pe);
			hy += 10;
		}
		if (pages() > 1) {
			g.text(this.font, (page + 1) + "/" + pages(), x + 64, y + panelH() - 19, Ui.argb(pe, Ui.MUTED));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
