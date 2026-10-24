package net.battlehub.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.battlehub.core.MenuLayout;
import net.battlehub.lang.Lang;
import net.battlehub.util.Icon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The contents of a menu while it is being drawn: items plus click actions per slot. */
public final class Canvas {
	/** Details of a click on a button. */
	public record Click(ServerPlayer player, int slot, int button, ContainerInput input) {
		public boolean right() {
			return button == 1;
		}

		public boolean shift() {
			return input == ContainerInput.QUICK_MOVE;
		}
	}

	private final ItemStack[] items;
	private final Map<Integer, Consumer<Click>> actions = new HashMap<>();
	private final ServerPlayer viewer;
	private final int rows;
	private final Style.Section section;

	Canvas(ServerPlayer viewer, int rows, Style.Section section) {
		this.viewer = viewer;
		this.rows = rows;
		this.section = section;
		this.items = new ItemStack[rows * 9];
	}

	public Style.Section section() {
		return section;
	}

	public int size() {
		return items.length;
	}

	public int rows() {
		return rows;
	}

	public void set(int slot, ItemStack stack) {
		set(slot, stack, null);
	}

	public void set(int slot, ItemStack stack, Consumer<Click> action) {
		if (slot < 0 || slot >= items.length) {
			return;
		}
		items[slot] = stack;
		if (action != null) {
			actions.put(slot, action);
		} else {
			actions.remove(slot);
		}
	}

	public void setRow(int row, int col, ItemStack stack, Consumer<Click> action) {
		set(row * 9 + col, stack, action);
	}

	public ItemStack item(int slot) {
		ItemStack s = items[slot];
		return s == null ? ItemStack.EMPTY : s;
	}

	Consumer<Click> action(int slot) {
		return actions.get(slot);
	}

	/**
	 * Fills every empty slot: the top and bottom rows in the colour of the menu's part of BattleHub, the rest dark
	 * glass, all without tooltip. Menus of three rows or more show your coins top right ({@link MenuLayout#WALLET}).
	 */
	public void fill() {
		if (rows >= 3 && (items[MenuLayout.WALLET] == null || items[MenuLayout.WALLET].isEmpty())) {
			items[MenuLayout.WALLET] = Style.wallet(viewer);
		}
		ItemStack pane = Icon.of("minecraft:black_stained_glass_pane").hidden().build();
		ItemStack edge = Style.pane(section);
		for (int i = 0; i < items.length; i++) {
			if (items[i] == null || items[i].isEmpty()) {
				items[i] = rows >= 3 && MenuLayout.frame(i, rows) ? edge.copy() : pane.copy();
			}
		}
	}

	/** Panes around the edge only (in the colour of the menu's part of BattleHub), and your coins top right. */
	public void border() {
		if (rows >= 3 && (items[MenuLayout.WALLET] == null || items[MenuLayout.WALLET].isEmpty())) {
			items[MenuLayout.WALLET] = Style.wallet(viewer);
		}
		ItemStack pane = Style.pane(section);
		for (int i = 0; i < items.length; i++) {
			int r = i / 9;
			int col = i % 9;
			if ((r == 0 || r == rows - 1 || col == 0 || col == 8) && (items[i] == null || items[i].isEmpty())) {
				items[i] = pane.copy();
			}
		}
	}

	/** Back arrow bottom left and the close button in the bottom middle, the same in every menu. */
	public void back(Runnable action) {
		set(MenuLayout.back(rows), Icon.of(Items.ARROW).name(Lang.item(viewer, "menu.back")).build(), c -> action.run());
		set(MenuLayout.close(rows), Icon.of(Items.BARRIER).name(Lang.item(viewer, "menu.close")).build(), c -> viewer.closeContainer());
	}

	/** Slots of the inner area (no border), left to right, top to bottom. */
	public int[] inner() {
		int[] out = new int[Math.max(0, (rows - 2) * 7)];
		int n = 0;
		for (int r = 1; r < rows - 1; r++) {
			for (int col = 1; col < 8; col++) {
				out[n++] = r * 9 + col;
			}
		}
		return out;
	}
}
