package net.battlehub.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.TooltipDisplay;

/** Small builder for menu icons and hub items. */
public final class Icon {
	public static final String TAG = "battlehub";

	private final ItemStack stack;
	private final List<Component> lore = new ArrayList<>();

	private Icon(ItemStack stack) {
		this.stack = stack;
	}

	public static Icon of(Item item) {
		return new Icon(new ItemStack(item == null || item == Items.AIR ? Items.BARRIER : item));
	}

	public static Icon of(String id) {
		return of(Ids.item(id));
	}

	public static Icon of(ItemStack stack) {
		return new Icon(stack.copy());
	}

	/** The player's own head. */
	public static Icon head(ServerPlayer p) {
		Icon i = of(Items.PLAYER_HEAD);
		i.stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(p.getGameProfile()));
		return i;
	}

	/** A head for a player who may be offline: the client fetches the skin by name. */
	public static Icon head(String name) {
		Icon i = of(Items.PLAYER_HEAD);
		if (name != null && !name.isBlank()) {
			i.stack.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(name));
		}
		return i;
	}

	/** Item name (never italic, unlike a custom name). */
	public Icon name(Component name) {
		stack.set(DataComponents.ITEM_NAME, name);
		stack.set(DataComponents.CUSTOM_NAME, Component.empty().withStyle(s -> s.withItalic(false)).append(name));
		return this;
	}

	public Icon lore(Component line) {
		lore.add(line);
		return this;
	}

	public Icon lore(List<Component> lines) {
		lore.addAll(lines);
		return this;
	}

	private boolean facts, actions;

	/**
	 * A fact about the thing ("In the queue: 3"), after the description; the first one comes after an empty line
	 * (1.45: every tooltip reads description, facts, what a click does).
	 */
	public Icon fact(Component line) {
		if (!facts && !lore.isEmpty()) lore.add(Component.empty());
		facts = true;
		lore.add(line);
		return this;
	}

	/** What a click does ("▶ Click to join"), last in the tooltip; the first one comes after an empty line. */
	public Icon action(Component line) {
		if (!actions && !lore.isEmpty()) lore.add(Component.empty());
		actions = true;
		lore.add(Component.literal("▶ ").withStyle(s -> s.withColor(0xFFFF55).withItalic(false)).append(line));
		return this;
	}

	public Icon count(int count) {
		stack.setCount(Math.max(1, Math.min(stack.getMaxStackSize(), count)));
		return this;
	}

	public Icon glint(boolean on) {
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, on);
		return this;
	}

	/** Tag of every item shown in a BattleHub menu; such items may never leave the menu. */
	public static final String MENU = "menu";

	/** Marks the stack as a BattleHub item (compass, leave-queue item, ...). */
	public Icon tag(String value) {
		mark(stack, value);
		return this;
	}

	/** Marks an existing stack as a BattleHub item. */
	public static void mark(ItemStack stack, String value) {
		CompoundTag t = new CompoundTag();
		t.putString(TAG, value);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
	}

	/** Hides attribute, enchantment and other noise lines in the tooltip. */
	public Icon clean() {
		LinkedHashSet<DataComponentType<?>> hidden = new LinkedHashSet<>();
		hidden.add(DataComponents.ATTRIBUTE_MODIFIERS);
		hidden.add(DataComponents.ENCHANTMENTS);
		hidden.add(DataComponents.STORED_ENCHANTMENTS);
		hidden.add(DataComponents.UNBREAKABLE);
		hidden.add(DataComponents.DYED_COLOR);
		hidden.add(DataComponents.TRIM);
		hidden.add(DataComponents.POTION_CONTENTS);
		hidden.add(DataComponents.BANNER_PATTERNS);
		hidden.add(DataComponents.FIREWORKS);
		hidden.add(DataComponents.CHARGED_PROJECTILES);
		hidden.add(DataComponents.LODESTONE_TRACKER);
		stack.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(false, hidden));
		return this;
	}

	/** No tooltip at all (filler panes). */
	public Icon hidden() {
		stack.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new LinkedHashSet<>()));
		return this;
	}

	public ItemStack build() {
		if (!lore.isEmpty()) {
			stack.set(DataComponents.LORE, new ItemLore(List.copyOf(lore.subList(0, Math.min(lore.size(), 256)))));
		}
		return stack;
	}

	/** The BattleHub tag of a stack, or "" for normal items. */
	public static String tagOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return "";
		}
		return data.copyTag().getStringOr(TAG, "");
	}

	public static boolean isHubItem(ItemStack stack) {
		return !tagOf(stack).isEmpty();
	}
}
