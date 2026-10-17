package com.extremedimensions.client.tooltip;

import java.util.List;

import com.extremedimensions.anchor.FuelValues;
import com.extremedimensions.config.Config;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

/** Item tooltips: what every mod item does, and how much anchor charge a fuel item is worth. */
public final class Tooltips {
	private Tooltips() {}

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> append(stack, lines));
	}

	private static void append(ItemStack stack, List<Component> lines) {
		Item item = stack.getItem();
		String key = null;
		if (item == ModItems.TETHER) {
			key = "tether";
		} else if (item == ModItems.EMBER_TETHER) {
			key = "ember_tether";
		} else if (item == ModItems.VOID_TETHER) {
			key = "void_tether";
		} else if (item == ModItems.TETHER_JOURNAL) {
			key = "tether_journal";
		} else if (item == ModItems.TETHER_FRAGMENT) {
			key = "tether_fragment";
		} else if (item == ModItems.WANDERERS_PAGE) {
			key = "wanderers_page";
		} else if (item == ModItems.SOUL_WOVEN_TETHER) {
			key = "soul_woven_tether";
		} else if (item == ModItems.SEAM_WOVEN_TETHER) {
			key = "seam_woven_tether";
		} else if (item == ModItems.MENDING_THREAD) {
			key = "mending_thread";
		} else if (item == ModItems.SEVERING_SHEARS) {
			key = "severing_shears";
		} else if (item == ModItems.SEAM_NEEDLE) {
			key = "seam_needle";
		} else if (item == ModItems.VEIL_WING) {
			key = "veil_wing";
		} else if (item == ModItems.MENDING_CLOTH) {
			key = "mending_cloth";
		} else if (item == ModItems.SEAM_SILK) {
			key = "seam_silk";
		}
		com.extremedimensions.relic.Relic relic = com.extremedimensions.relic.RelicService.of(stack);
		if (relic != null) {
			// A relic: its gift, its price (both with the server's numbers) and how to bind it
			lines.add(Component.literal("+ ").append(Component.translatable(relic.giftKey(), relic.giftArgs())).withStyle(ChatFormatting.GREEN));
			lines.add(Component.literal("- ").append(Component.translatable(relic.priceKey(), relic.priceArgs())).withStyle(ChatFormatting.RED));
			lines.add(Component.translatable("tooltip.extremedimensions.relic.bind").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			return;
		}
		if (key != null) {
			lines.add(Component.translatable("tooltip.extremedimensions." + key + ".1").withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("tooltip.extremedimensions." + key + ".2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			return;
		}
		if (stack.is(Items.SOUL_LANTERN) || stack.is(Items.SOUL_TORCH) || stack.is(Items.SOUL_CAMPFIRE)) {
			lines.add(Component.translatable("tooltip.extremedimensions.soul_ward", (int) Config.LURKER_WARD_RANGE).withStyle(ChatFormatting.DARK_AQUA));
		}
		double nether = FuelValues.of(Dims.NETHER, item);
		double end = FuelValues.of(Dims.END, item);
		if (nether > 0) {
			lines.add(Component.translatable("tooltip.extremedimensions.fuel.nether", Ui.time(nether)).withStyle(ChatFormatting.GOLD));
		} else if (end > 0) {
			lines.add(Component.translatable("tooltip.extremedimensions.fuel.end", Ui.time(end)).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
	}
}
