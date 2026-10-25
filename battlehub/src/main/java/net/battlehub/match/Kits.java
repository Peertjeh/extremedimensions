package net.battlehub.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.KitConfig;
import net.battlehub.core.KitLayout;
import net.battlehub.data.PlayerStore;
import net.battlehub.util.Ids;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

/** Hands out the kit of a mode from kits.json. */
public final class Kits {
	private Kits() {}

	/**
	 * Clears the player and gives the kit.
	 *
	 * @param leatherColor colour for leather armour (-1 keeps the kit's own colour), used for CTF teams
	 */
	/** The inventory slots (0-35) the kit puts items in, in kit order; these are what a player can rearrange. */
	public static List<Integer> numericSlots(KitConfig.Kit kit) {
		List<Integer> out = new ArrayList<>();
		for (KitConfig.KitItem ki : kit.items) {
			int i = numeric(ki);
			if (i >= 0) out.add(i);
		}
		return out;
	}

	/** The kit item that normally goes in this inventory slot. */
	public static KitConfig.KitItem itemAt(KitConfig.Kit kit, int slot) {
		for (KitConfig.KitItem ki : kit.items) {
			if (numeric(ki) == slot) return ki;
		}
		return null;
	}

	private static int numeric(KitConfig.KitItem ki) {
		try {
			int i = Integer.parseInt(ki.slot == null ? "0" : ki.slot.trim());
			return i >= 0 && i < 36 ? i : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	public static void give(ServerPlayer p, String kitName, int leatherColor) {
		give(p, kitName, leatherColor, null);
	}

	/**
	 * Clears the player and gives the kit, with the player's own layout. {@code team}: the colour name that fills in
	 * "{team}" in item ids (The Bridge's clay), or null for white.
	 */
	public static void give(ServerPlayer p, String kitName, int leatherColor, String team) {
		Inventory inv = p.getInventory();
		inv.clearContent();
		p.removeAllEffects();
		KitConfig.Kit kit = ConfigManager.kits().kits.get(kitName);
		if (kit == null) {
			BattleHub.LOGGER.warn("Kit '{}' does not exist in kits.json", kitName);
			p.inventoryMenu.sendAllDataToRemote();
			return;
		}
		// The player's own arrangement of this kit, if they made one
		List<Integer> kitSlots = numericSlots(kit);
		List<Integer> layout = layout(p, kitName);
		for (KitConfig.KitItem ki : kit.items) {
			ItemStack stack = stack(p, ki, leatherColor, team);
			if (stack.isEmpty()) {
				continue;
			}
			String slot = ki.slot == null ? "0" : ki.slot.trim().toLowerCase(java.util.Locale.ROOT);
			switch (slot) {
				case "head", "helmet" -> p.setItemSlot(EquipmentSlot.HEAD, stack);
				case "chest", "chestplate" -> p.setItemSlot(EquipmentSlot.CHEST, stack);
				case "legs", "leggings" -> p.setItemSlot(EquipmentSlot.LEGS, stack);
				case "feet", "boots" -> p.setItemSlot(EquipmentSlot.FEET, stack);
				case "offhand" -> p.setItemSlot(EquipmentSlot.OFFHAND, stack);
				default -> {
					int i;
					try {
						i = Integer.parseInt(slot);
					} catch (NumberFormatException e) {
						i = -1;
					}
					if (i >= 0 && i < 36) {
						i = KitLayout.slotFor(i, kitSlots, layout);
					}
					if (i >= 0 && i < 36 && inv.getItem(i).isEmpty()) {
						inv.setItem(i, stack);
					} else {
						inv.add(stack);
					}
				}
			}
		}
		for (Map.Entry<String, Integer> e : kit.effects.entrySet()) {
			Ids.effect(e.getKey()).ifPresent(h -> p.addEffect(new MobEffectInstance(h, MobEffectInstance.INFINITE_DURATION,
					Math.max(0, e.getValue()), false, false, true)));
		}
		net.battlehub.util.Inv.select(p, 0);
		p.inventoryMenu.sendAllDataToRemote();
	}

	/**
	 * The arrangement of a kit someone gets: their own from the kit editor. A bot has none of its own: in a match with a
	 * person it gets theirs (1.49), so the bot fights with the very kit the person has, in the same slots (what they put in
	 * the hotbar is in its hotbar too).
	 */
	static List<Integer> layout(ServerPlayer p, String kitName) {
		if (!net.battlehub.bot.Bots.isBot(p)) return PlayerStore.get(p).kitLayouts.get(kitName);
		Match m = Matches.of(p.getUUID());
		if (m == null) return null;
		for (java.util.UUID id : m.participants()) {
			if (net.battlehub.bot.Bots.isBot(id)) continue;
			ServerPlayer person = m.player(id);
			List<Integer> theirs = person == null ? null : PlayerStore.get(person).kitLayouts.get(kitName);
			if (theirs != null) return theirs;
		}
		return null;
	}

	public static ItemStack stack(ServerPlayer p, KitConfig.KitItem ki, int leatherColor) {
		return stack(p, ki, leatherColor, null);
	}

	public static ItemStack stack(ServerPlayer p, KitConfig.KitItem ki, int leatherColor, String team) {
		String id = ki.item == null ? "minecraft:air" : ki.item.replace("{team}", team == null ? "white" : team);
		Item item = Ids.item(id);
		if (item == Items.AIR) {
			BattleHub.LOGGER.warn("Unknown kit item '{}'", id);
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(item, Math.max(1, Math.min(item.getDefaultMaxStackSize(), ki.count)));
		if (ki.enchantments != null) {
			for (Map.Entry<String, Integer> e : ki.enchantments.entrySet()) {
				Ids.enchantment(p.level().getServer(), e.getKey()).ifPresentOrElse(
						h -> stack.enchant(h, Math.max(1, e.getValue())),
						() -> BattleHub.LOGGER.warn("Unknown enchantment '{}' in kit item {}", e.getKey(), ki.item));
			}
		}
		if (ki.unbreakable && stack.isDamageableItem()) {
			stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		}
		int color = leatherColor >= 0 ? leatherColor : ki.color;
		if (color >= 0 && Ids.key(item).startsWith("minecraft:leather_")) {
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
		}
		net.battlehub.progression.Cosmetics.applySkin(p, stack);
		return stack;
	}
}
