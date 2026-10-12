package com.extremedimensions.anchor;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Seconds of charge each fuel item adds to an anchor of its dimension. */
public final class FuelValues {
	private FuelValues() {}

	public static final Map<Item, Double> NETHER = new HashMap<>();
	public static final Map<Item, Double> END = new HashMap<>();

	/** Charge for this item in its dimension's anchor, or 0 if it is no fuel there. */
	public static double of(String dim, Item item) {
		Double v = (com.extremedimensions.server.Dims.NETHER.equals(dim) ? NETHER : END).get(item);
		return v == null ? 0 : v;
	}

	static {
		// Nether: fire and heat. 150 coal = 5 minutes.
		NETHER.put(Items.COAL, 2.0);
		NETHER.put(Items.CHARCOAL, 2.0);
		NETHER.put(Items.COAL_BLOCK, 18.0);
		NETHER.put(Items.MAGMA_BLOCK, 6.0);
		NETHER.put(Items.FIRE_CHARGE, 12.0);
		NETHER.put(Items.BLAZE_POWDER, 15.0);
		NETHER.put(Items.BLAZE_ROD, 30.0);

		// End: ender and echo matter.
		END.put(Items.AMETHYST_SHARD, 3.0);
		END.put(Items.CHORUS_FRUIT, 6.0);
		END.put(Items.ENDER_PEARL, 20.0);
		END.put(Items.ENDER_EYE, 45.0);
		END.put(Items.SHULKER_SHELL, 90.0);
		END.put(Items.ECHO_SHARD, 120.0);
	}
}
