package com.extremedimensions;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Welke items hoeveel charge (in seconden) opleveren als je ze in het altaar stopt.
 * Het altaar is een Enchanting Table: rechtermuisklik met het item in je hand.
 * Pas de waardes aan om de grind zwaarder of lichter te maken.
 */
public final class ChargeItems {
	private ChargeItems() {}

	public static final Map<Item, Double> NETHER = new HashMap<>();
	public static final Map<Item, Double> END = new HashMap<>();

	static {
		// Nether Charge: vuur en hitte. 150 steenkool = 5 minuten.
		NETHER.put(Items.COAL, 2.0);
		NETHER.put(Items.CHARCOAL, 2.0);
		NETHER.put(Items.COAL_BLOCK, 18.0);
		NETHER.put(Items.MAGMA_BLOCK, 6.0);
		NETHER.put(Items.FIRE_CHARGE, 12.0);
		NETHER.put(Items.BLAZE_POWDER, 15.0);
		NETHER.put(Items.BLAZE_ROD, 30.0);

		// End Charge: ender- en echo-spul.
		END.put(Items.AMETHYST_SHARD, 3.0);
		END.put(Items.CHORUS_FRUIT, 6.0);
		END.put(Items.ENDER_PEARL, 20.0);
		END.put(Items.ENDER_EYE, 45.0);
		END.put(Items.SHULKER_SHELL, 90.0);
		END.put(Items.ECHO_SHARD, 120.0);
	}
}
