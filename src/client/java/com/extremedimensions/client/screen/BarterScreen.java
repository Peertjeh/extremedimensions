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
	private static final int ROW_H = 26;

	private record Offer(BarterService.Ware ware, int price, int left) {}

	private final int young;
	private final int rep;
	private final List<Offer> offers = new ArrayList<>();
	private final long openAt;

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

	/** Opens the screen, or refreshes it in place after a barter (without playing its opening again). */
	public static void show(int young, int rep, String data) {
		Minecraft mc = Minecraft.getInstance();
		long openAt = mc.screen instanceof BarterScreen b && b.young == young ? b.openAt : System.currentTimeMillis();
		mc.gui.setScreen(new BarterScreen(young, rep, data, openAt));
	}

	private int panelH() {
		return 84 + Math.max(1, offers.size()) * ROW_H + 50;
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
		int x = this.width / 2 - PANEL_W / 2, y = this.height / 2 - panelH() / 2;
		for (int i = 0; i < offers.size(); i++) {
			Offer o = offers.get(i);
			boolean can = o.left() > 0 && have(o.ware().want()) >= o.price();
			String label = o.left() <= 0 ? "gui.extremedimensions.barter.sold" : "gui.extremedimensions.barter.buy";
			Button buy = Button.builder(Component.translatable(label), b ->
					ClientPlayNetworking.send(new ModPackets.BarterTradePayload(young, o.ware().id())))
					.pos(x + PANEL_W - 14 - 64, y + 80 + i * ROW_H).size(64, 18).build();
			buy.active = can;
			this.addRenderableWidget(buy);
		}
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
				.pos(this.width / 2 - 40, y + panelH() - 26).size(80, 18).build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		double pe = Ui.ease((System.currentTimeMillis() - openAt) / 300.0);
		int x = this.width / 2 - PANEL_W / 2, y = this.height / 2 - panelH() / 2;
		int cx = this.width / 2;
		Standing.Level level = Standing.level(rep);
		Integer c = level.colour().getColor();
		int accent = c == null ? Ui.SOUL : c;
		Ui.panel(g, x, y, PANEL_W, panelH(), Ui.SOUL_DIM, pe);
		Ui.heading(g, this.font, Ui.tr("gui.extremedimensions.barter.title"), cx, y + 9, Ui.SOUL, pe);
		// Standing: the level by name, and where the player stands from -100 to 100
		String name = Ui.tr(level.key()) + "  (" + (rep > 0 ? "+" : "") + rep + ")";
		Ui.centered(g, this.font, Ui.tr("gui.extremedimensions.barter.standing"), cx, y + 26, Ui.MUTED, pe);
		Ui.centered(g, this.font, name, cx, y + 37, accent, pe);
		Ui.meter(g, x + 30, y + 50, PANEL_W - 60, 5, (rep - Standing.MIN) / (double) (Standing.MAX - Standing.MIN), accent, pe, false);
		int zero = x + 30 + (PANEL_W - 60) / 2;
		g.fill(zero, y + 48, zero + 1, y + 57, Ui.argb(pe, Ui.PARCHMENT));
		Ui.divider(g, x + 14, y + 66, PANEL_W - 28, Ui.SOUL_DIM, pe);
		if (offers.isEmpty()) {
			Ui.centered(g, this.font, Ui.tr("gui.extremedimensions.barter.nothing"), cx, y + 84, Ui.PARCHMENT, pe);
		}
		for (int i = 0; i < offers.size(); i++) {
			Offer o = offers.get(i);
			int ry = y + 80 + i * ROW_H;
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
		int hy = y + panelH() - 48;
		for (String line : Ui.wrap(this.font, Ui.tr("gui.extremedimensions.barter.hint"), PANEL_W - 28)) {
			Ui.centered(g, this.font, line, cx, hy, Ui.MUTED, pe);
			hy += 10;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
