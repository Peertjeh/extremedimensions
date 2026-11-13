package com.extremedimensions.client.tooltip;

import java.util.List;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.anchor.FuelValues;
import com.extremedimensions.config.Config;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

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
		} else if (item == ModItems.LOOSE_LEAF) {
			key = "loose_leaf";
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
		} else if (item == ModItems.HOMING_PEARL) {
			key = "homing_pearl";
		} else if (item == ModItems.HOMEWARD_KEYSTONE) {
			key = "homeward_keystone";
		} else if (item == ModItems.PALE_LOCK) {
			key = "pale_lock";
		} else if (item == ModItems.MENDING_CLOTH) {
			key = "mending_cloth";
		} else if (item == ModItems.BLOOD_CORD) {
			key = "blood_cord";
		} else if (item == ModItems.SEAM_SILK) {
			key = "seam_silk";
		} else if (item == ModItems.WOUND_LENS) {
			key = "wound_lens";
		} else if (item == ModItems.VEIL_LANTERN) {
			key = "veil_lantern";
		} else if (item == ModItems.ECHO_THREAD) {
			key = "echo_thread";
		} else if (item == ModItems.EMBER_THREAD) {
			key = "ember_thread";
		} else if (item == ModItems.PALE_THREAD) {
			key = "pale_thread";
		} else if (item == ModItems.SILK_THREAD) {
			key = "silk_thread";
		} else if (item == ModItems.PLUMB_LINE) {
			key = "plumb_line";
		} else if (item == ModItems.DISC_FRAGMENT_ELEGY) {
			key = "disc_fragment_elegy";
		} else if (item == ModItems.DISC_FRAGMENT_ABYSS) {
			key = "disc_fragment_abyss";
		} else if (item == ModItems.WEAVERS_LOOM) {
			key = "weavers_loom";
		} else if (item == ModItems.SPINDLE) {
			key = "spindle";
		} else if (item == ModItems.TETHERITE_ORE) {
			key = "tetherite_ore";
		} else if (item == ModItems.TETHERITE_SCRAP) {
			key = "tetherite_scrap";
		} else if (item == ModItems.TETHER_CORD) {
			key = "tether_cord";
		} else if (item == ModItems.TETHERITE_INGOT) {
			key = "tetherite_ingot";
		}
		if ((item == ModItems.TETHERITE_INGOT || item == ModItems.TETHERITE_UPGRADE_SMITHING_TEMPLATE) && Config.TETHERITE_BIND_WHERE > 0) {
			// Where the metal binds to netherite (3.26.1)
			lines.add(Component.translatable("tooltip.extremedimensions.tetherite.where." + Math.min(2, Config.TETHERITE_BIND_WHERE))
					.withStyle(ChatFormatting.DARK_PURPLE));
		}
		if (com.extremedimensions.tetherite.Tetherite.isGear(stack)) {
			// Tetherite gear: tethered, and what the whole set does
			lines.add(Component.translatable("tooltip.extremedimensions.tetherite.tethered").withStyle(ChatFormatting.DARK_AQUA));
			lines.add(Component.translatable("tooltip.extremedimensions.tetherite.set", Math.round(Config.TETHERITE_SET_DRAIN * 100))
					.withStyle(ChatFormatting.DARK_GRAY));
			// ... and the price the metal asks of whoever bears it
			// (none while the world has them switched off)
			double weight = com.extremedimensions.tetherite.Tetherite.drawback(Config.TETHERITE_WEIGHT);
			double hunger = com.extremedimensions.tetherite.Tetherite.drawback(Config.TETHERITE_HUNGER);
			double lure = com.extremedimensions.tetherite.Tetherite.drawback(Config.TETHERITE_LURE);
			if (weight > 0 && stack.is(net.minecraft.tags.ItemTags.TRIMMABLE_ARMOR)) {
				lines.add(Component.translatable("tooltip.extremedimensions.tetherite.weight", pct(weight)).withStyle(ChatFormatting.RED));
			}
			if (hunger > 0) {
				lines.add(Component.translatable("tooltip.extremedimensions.tetherite.hunger", pct(hunger)).withStyle(ChatFormatting.RED));
			}
			if (lure > 0) {
				lines.add(Component.translatable("tooltip.extremedimensions.tetherite.lure", pct(lure)).withStyle(ChatFormatting.RED));
			}
		}
		com.extremedimensions.tapestry.Tapestry.Panel panel = com.extremedimensions.tapestry.Tapestry.Panel.of(stack);
		if (panel != null) {
			// A thread of the Tapestry: the deed that earned it, and its panel's boon in either world
			lines.add(Component.translatable(panel.deedKey()).withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("tooltip.extremedimensions.tapestry_thread.mended", Component.translatable(panel.boonKey("mended"), panel.boonArgs("mended")))
					.withStyle(ChatFormatting.AQUA));
			lines.add(Component.translatable("tooltip.extremedimensions.tapestry_thread.severed", Component.translatable(panel.boonKey("severed"), panel.boonArgs("severed")))
					.withStyle(ChatFormatting.RED));
			lines.add(Component.translatable("tooltip.extremedimensions.tapestry_thread.weave").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			return;
		}
		charms(stack, lines);
		com.extremedimensions.relic.Relic relic = com.extremedimensions.relic.RelicService.of(stack);
		if (relic != null) {
			// A relic: its tier right under its name, its gift, its price (both with the server's numbers) and how to bind it
			lines.add(Math.min(1, lines.size()), Component.translatable(relic.tier.nameKey()).withStyle(relic.tier.colour));
			lines.add(Component.literal("+ ").append(Component.translatable(relic.giftKey(), relic.giftArgs())).withStyle(ChatFormatting.GREEN));
			lines.add(Component.literal("- ").append(Component.translatable(relic.priceKey(), relic.priceArgs())).withStyle(ChatFormatting.RED));
			lines.add(Component.translatable(Config.RELIC_SINGLE_USE > 0 ? "tooltip.extremedimensions.relic.bind" : "tooltip.extremedimensions.relic.bind_kept")
					.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			return;
		}
		if (item == ModItems.MENDING_CLOTH) {
			cloth(lines);
			return;
		}
		if (key != null) {
			lines.add(Component.translatable("tooltip.extremedimensions." + key + ".1").withStyle(ChatFormatting.GRAY));
			if (item == ModItems.EMBER_TETHER || item == ModItems.VOID_TETHER) {
				// (what it charges for this player, where a curse or a relic makes that more or less)
				yours(lines, Config.TETHER_ITEM_CHARGE, 1, item == ModItems.EMBER_TETHER ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE);
			}
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
			fuel(lines, nether, stack.getCount(), ChatFormatting.GOLD);
		} else if (end > 0) {
			lines.add(Component.translatable("tooltip.extremedimensions.fuel.end", Ui.time(end)).withStyle(ChatFormatting.LIGHT_PURPLE));
			fuel(lines, end, stack.getCount(), ChatFormatting.LIGHT_PURPLE);
		}
	}

	/**
	 * Under a fuel's worth (3.55.0): what the whole stack is worth, and what it is worth to this player where a curse
	 * (Debt) or a relic (the Anchor Rivet) makes their fuel worth more or less than its face.
	 */
	private static void fuel(List<Component> lines, double each, int count, ChatFormatting colour) {
		if (count > 1) {
			lines.add(Component.translatable("tooltip.extremedimensions.fuel.stack", count, Ui.time(each * count)).withStyle(colour));
		}
		yours(lines, each, count, colour);
	}

	/** What fuel worth {@code each} a piece is worth to this player, a piece and (with more than one) the stack; nothing at face value. */
	private static void yours(List<Component> lines, double each, int count, ChatFormatting colour) {
		double worth = com.extremedimensions.client.state.ClientState.fuelWorth;
		if (Math.abs(worth - 1) < 0.001) {
			return;
		}
		String factor = com.extremedimensions.penalty.CurseTotals.format(worth * 100) + "%";
		lines.add((count > 1 ? Component.translatable("tooltip.extremedimensions.fuel.yours_stack", Ui.time(each * worth), Ui.time(each * worth * count), factor)
				: Component.translatable("tooltip.extremedimensions.fuel.yours", Ui.time(each * worth), factor)).withStyle(colour, ChatFormatting.ITALIC));
	}

	/**
	 * What the mod's enchantments do, one dark line each under the item's own (on books too): unlike vanilla's, their
	 * names alone do not say it.
	 */
	private static void charms(ItemStack stack, List<Component> lines) {
		for (ItemEnchantments set : List.of(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY),
				stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY))) {
			for (Holder<Enchantment> h : set.keySet()) {
				h.unwrapKey().map(k -> k.identifier()).filter(id -> ExtremeDimensions.MOD_ID.equals(id.getNamespace())).ifPresent(id -> {
					Component desc = Component.translatable("enchantment." + id.getNamespace() + "." + id.getPath() + ".desc").withStyle(ChatFormatting.DARK_GRAY);
					// right under the enchantment's own line, where there is one
					String name = Enchantment.getFullname(h, set.getLevel(h)).getString();
					int at = -1;
					for (int i = 0; i < lines.size() && at < 0; i++) {
						if (lines.get(i).getString().equals(name)) {
							at = i + 1;
						}
					}
					if (at < 0) {
						lines.add(desc);
					} else {
						lines.add(at, desc);
					}
				});
			}
		}
	}

	/**
	 * The Mending Cloth by the world's numbers (3.49): the share of a stack it pays, how often one may be used, and what it
	 * will not do.
	 */
	private static void cloth(List<Component> lines) {
		lines.add(Component.translatable("tooltip.extremedimensions.mending_cloth.1", pct(Math.max(0.01, Math.min(1, Config.CLOTH_SHARE))))
				.withStyle(ChatFormatting.GRAY));
		if (Config.CLOTH_COOLDOWN_MINUTES > 0) {
			lines.add(Component.translatable("tooltip.extremedimensions.mending_cloth.wait", Config.CLOTH_COOLDOWN_MINUTES).withStyle(ChatFormatting.GRAY));
		}
		if (Config.CLOTH_EXTREME <= 0) {
			lines.add(Component.translatable("tooltip.extremedimensions.mending_cloth.extreme").withStyle(ChatFormatting.DARK_GRAY));
		}
		if (Config.CLOTH_SCARS <= 0) {
			lines.add(Component.translatable("tooltip.extremedimensions.mending_cloth.unscarred").withStyle(ChatFormatting.DARK_GRAY));
		}
		lines.add(Component.translatable("tooltip.extremedimensions.mending_cloth.2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	/** A share as a whole percentage, as text (0.03 gives "3"). */
	private static String pct(double share) {
		return Config.format(Math.round(share * 1000) / 10.0);
	}
}
