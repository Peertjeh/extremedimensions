package com.extremedimensions.penalty;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The carrying curses reach into what is carried inside other things, so a shulker box or a bundle in the inventory
 * is no way round them: Sealed Pockets seals the same share of every carried box as of the pockets themselves,
 * Dwindling shrinks the stacks inside a box or a bundle as it shrinks the inventory's own, and Lead Pockets weighs
 * every stack inside as a stack of its own. A box set down as a block is a chest like any other and untouched.
 */
public final class CarriedContainers {
	private CarriedContainers() {}

	/** The slots of a shulker box. */
	private static final int BOX = 27;

	/** How many stacks lie inside an item (a shulker box or a bundle); 0 for anything else. */
	public static int inside(ItemStack s) {
		int n = 0;
		ItemContainerContents box = s.get(DataComponents.CONTAINER);
		if (box != null) {
			for (var ignored : box.nonEmptyItems()) {
				n++;
			}
		}
		BundleContents bundle = s.get(DataComponents.BUNDLE_CONTENTS);
		if (bundle != null) {
			n += bundle.size();
		}
		return n;
	}

	/** How many slots of a carried box are sealed while {@code sealed} slots of the pockets are. */
	public static int sealedInBox(int sealed) {
		return Math.min(BOX, (int) Math.ceil(BOX * Math.max(0, sealed) / (double) Inventory.INVENTORY_SIZE));
	}

	/** Sealed Pockets: what lies in the sealed last slots of a carried box comes out, handed to {@code out}. */
	public static void seal(ServerPlayer p, int sealed, Consumer<ItemStack> out) {
		int keep = BOX - sealedInBox(sealed);
		if (keep >= BOX) {
			return;
		}
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			ItemContainerContents box = s.get(DataComponents.CONTAINER);
			if (box == null) {
				continue;
			}
			List<ItemStack> items = new ArrayList<>(box.itemCopies().toList());
			List<ItemStack> spilled = new ArrayList<>();
			for (int j = keep; j < items.size(); j++) {
				if (!items.get(j).isEmpty()) {
					spilled.add(items.get(j));
					items.set(j, ItemStack.EMPTY);
				}
			}
			if (!spilled.isEmpty()) {
				s.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
				spilled.forEach(out);
			}
		}
	}

	/** Dwindling: no stack inside a carried box or bundle holds more than the inventory's own may; the rest goes to {@code out}. */
	public static void dwindle(ServerPlayer p, Consumer<ItemStack> out) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			ItemContainerContents box = s.get(DataComponents.CONTAINER);
			if (box != null) {
				List<ItemStack> items = new ArrayList<>(box.itemCopies().toList());
				List<ItemStack> rest = trim(p, items);
				if (!rest.isEmpty()) {
					s.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
					rest.forEach(out);
				}
			}
			BundleContents bundle = s.get(DataComponents.BUNDLE_CONTENTS);
			if (bundle != null) {
				List<ItemStack> items = new ArrayList<>(bundle.itemCopies().toList());
				List<ItemStack> rest = trim(p, items);
				if (!rest.isEmpty()) {
					s.set(DataComponents.BUNDLE_CONTENTS, bundle.copyWithContents(items.stream().filter(x -> !x.isEmpty())));
					rest.forEach(out);
				}
			}
		}
	}

	/** Splits what is above the Dwindling limit off the stacks (in place) and returns it. */
	private static List<ItemStack> trim(ServerPlayer p, List<ItemStack> items) {
		List<ItemStack> rest = new ArrayList<>();
		for (ItemStack in : items) {
			int max = Dwindling.limit(p, in.getMaxStackSize());
			if (in.getCount() > max) {
				rest.add(in.split(in.getCount() - max));
			}
		}
		return rest;
	}
}
