package com.extremedimensions.client.hud;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.client.mixin.ContainerScreenAccess;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.tetherite.TetheriteBinding;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.world.inventory.SmithingMenu;

/**
 * The binding's chance beside the smithing table (3.26): while a Tetherite upgrade waits in the result slot, a panel
 * to the left of the table (or under it, on a narrow screen) gives the chance it holds and every share that makes it,
 * and what a failure costs. The server sends the numbers (see {@code TetheriteBinding.tick}).
 */
public final class BindingPanel {
	private BindingPanel() {}

	private static int permille = -1;
	private static boolean capped;
	/** Where the binding would have to be done (0 here, 1 in the Seam, 2 down in the Fray). */
	private static int where;
	private static List<TetheriteBinding.Part> parts = List.of();

	public static void set(int chance, List<TetheriteBinding.Part> shares, boolean atCap, int needed) {
		permille = chance;
		parts = List.copyOf(shares);
		capped = atCap;
		where = chance < 0 ? 0 : needed;
		TetheriteBinding.clientBlocked = where;
	}

	public static void clear() {
		permille = -1;
		parts = List.of();
		where = 0;
		TetheriteBinding.clientBlocked = 0;
	}

	public static int permille() {
		return permille;
	}

	/** Drawn after the table's background: nothing unless a binding waits in it and its chance came. */
	public static void draw(SmithingScreen screen, GuiGraphicsExtractor g, Font font) {
		SmithingMenu menu = screen.getMenu();
		if (permille < 0 || !TetheriteBinding.isBinding(menu.getSlot(SmithingMenu.BASE_SLOT).getItem(), menu.getSlot(SmithingMenu.RESULT_SLOT).getItem())) {
			return;
		}
		List<String> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		int pct = Math.round(permille / 10f);
		if (where != 0) {
			// Not here: where the metal binds, and nothing more until the table stands there
			lines.add(Ui.tr("gui.extremedimensions.binding.where." + where));
			colors.add(Ui.DANGER);
			lines.add(Ui.tr("gui.extremedimensions.binding.where_hint"));
			colors.add(Ui.MUTED);
			box(screen, g, font, lines, colors);
			return;
		}
		lines.add(Ui.tr("gui.extremedimensions.binding.title", pct));
		colors.add(pct >= 75 ? Ui.OK : pct >= 45 ? Ui.WARN : Ui.DANGER);
		for (TetheriteBinding.Part part : parts) {
			lines.add(line(part));
			colors.add(part.kind().equals("base") ? Ui.MUTED : part.share() >= 0 ? Ui.SOUL : Ui.DANGER);
		}
		if (capped) {
			lines.add(Ui.tr("gui.extremedimensions.binding.capped", pct));
			colors.add(Ui.MUTED);
		}
		lines.add(Ui.tr("gui.extremedimensions.binding.cost"));
		colors.add(Ui.MUTED);
		box(screen, g, font, lines, colors);
	}

	/** The panel itself: beside the table (left, else right, else under or over it), its lines in their colours. */
	private static void box(SmithingScreen screen, GuiGraphicsExtractor g, Font font, List<String> lines, List<Integer> colors) {
		int w = 0;
		for (String s : lines) {
			w = Math.max(w, font.width(s));
		}
		w += 12;
		int h = lines.size() * 11 + 7;
		ContainerScreenAccess at = (ContainerScreenAccess) screen;
		int left = at.extremedimensions$leftPos(), top = at.extremedimensions$topPos();
		int x, y;
		if (left >= w + 6) {
			x = left - w - 4;
			y = top;
		} else if (screen.width - (left + 176) >= w + 6) {
			// no room to its left: to its right
			x = left + 176 + 4;
			y = top;
		} else {
			// A narrow screen: under the table if it fits, else over its top
			x = Math.max(4, (screen.width - w) / 2);
			y = top + 166 + 4 + h <= screen.height ? top + 166 + 4 : Math.max(2, top - h - 4);
		}
		Ui.panel(g, x, y, w, h, Ui.SOUL, 1.0);
		for (int i = 0; i < lines.size(); i++) {
			g.text(font, lines.get(i), x + 6, y + 5 + i * 11, Ui.argb(255, colors.get(i)));
		}
	}

	private static String line(TetheriteBinding.Part part) {
		String share = (part.share() >= 0 ? "+" : "-") + Math.round(Math.abs(part.share()) * 100) + "%";
		return switch (part.kind()) {
			case "base" -> Ui.tr("gui.extremedimensions.binding.part.base", Math.round(part.share() * 100));
			case "ench" -> Ui.tr("gui.extremedimensions.binding.part.ench", share, Ui.tr(part.label()) + (part.level() > 1 ? " " + Ui.roman(part.level()) : ""));
			case "luck" -> Ui.tr(part.level() >= 0 ? "gui.extremedimensions.binding.part.luck" : "gui.extremedimensions.binding.part.unluck", share, Math.abs(part.level()));
			case "scar" -> Ui.tr("gui.extremedimensions.binding.part.scar", share, part.level());
			default -> Ui.tr("gui.extremedimensions.binding.part." + part.kind(), share);
		};
	}
}
