package net.battlehub.gui;

import java.util.List;
import java.util.Map;

import net.battlehub.config.ConfigManager;
import net.battlehub.config.KitConfig;
import net.battlehub.core.KitLayout;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.match.Kits;
import net.battlehub.util.Icon;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Arrange a kit: the top four rows are the inventory (three rows, then the hotbar). Click an item, then the place
 * it should go: the two swap. Saved right away and used every time this kit is handed out.
 */
public final class KitEditorMenu extends Screen {
	private final String kit;
	private final int selected;

	public KitEditorMenu(ServerPlayer viewer, String kit, int selected) {
		super(viewer);
		this.kit = kit;
		this.selected = selected;
	}

	/** Menu slot for an inventory slot: rows 1-3 of the inventory first, the hotbar on the fourth row. */
	static int menuSlot(int inv) {
		return inv >= 9 ? inv - 9 : 27 + inv;
	}

	static int invSlot(int menu) {
		return menu >= 27 ? menu - 27 : menu + 9;
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.kit_editor.title");
	}

	@Override
	public int rows() {
		return 5;
	}

	@Override
	public void draw(Canvas c) {
		KitConfig.Kit k = ConfigManager.kits().kits.get(kit);
		if (k == null) {
			new KitListMenu(viewer).open();
			return;
		}
		PlayerProfile prof = PlayerStore.get(viewer);
		List<Integer> kitSlots = Kits.numericSlots(k);
		List<Integer> layout = prof.kitLayouts.get(kit);
		Map<Integer, Integer> bySlot = KitLayout.bySlot(kitSlots, layout);
		for (int inv = 0; inv < 36; inv++) {
			int menu = menuSlot(inv);
			Integer kitSlot = bySlot.get(inv);
			ItemStack shown;
			if (kitSlot != null) {
				KitConfig.KitItem ki = Kits.itemAt(k, kitSlot);
				ItemStack stack = ki == null ? ItemStack.EMPTY : Kits.stack(viewer, ki, -1);
				Icon icon = Icon.of(stack.isEmpty() ? new ItemStack(Items.BARRIER) : stack)
						.lore(Lang.lore(viewer, inv == selected ? "menu.kit_editor.selected" : "menu.kit_editor.pick"));
				shown = icon.glint(inv == selected).build();
			} else {
				shown = Icon.of(inv < 9 ? "minecraft:light_gray_stained_glass_pane" : "minecraft:gray_stained_glass_pane")
						.name(Lang.item(viewer, selected >= 0 ? "menu.kit_editor.put" : "menu.kit_editor.empty")).build();
			}
			int slot = inv;
			c.set(menu, shown, click -> clicked(slot, kitSlots));
		}
		c.set(36, Icon.of(Items.BOOK).name(Lang.item(viewer, "menu.kit_editor.help"))
				.lore(Lang.loreLines(viewer, "menu.kit_editor.help.lore")).build());
		c.set(38, Icon.of(Items.WATER_BUCKET).name(Lang.item(viewer, "menu.kit_editor.reset")).build(), click -> {
			prof.kitLayouts.remove(kit);
			PlayerStore.save(viewer.getUUID());
			Msg.send(viewer, "kits.reset");
			new KitEditorMenu(viewer, kit, -1).open();
		});
		c.back(() -> new KitListMenu(viewer).open());
		c.fill();
	}

	private void clicked(int inv, List<Integer> kitSlots) {
		PlayerProfile prof = PlayerStore.get(viewer);
		if (selected < 0) {
			if (KitLayout.bySlot(kitSlots, prof.kitLayouts.get(kit)).containsKey(inv)) {
				new KitEditorMenu(viewer, kit, inv).open();
			}
			return;
		}
		if (inv != selected) {
			prof.kitLayouts.put(kit, KitLayout.swap(kitSlots, prof.kitLayouts.get(kit), selected, inv));
			PlayerStore.save(viewer.getUUID());
			Msg.sound(viewer, SoundEvents.ITEM_PICKUP, 0.6f, 1.2f);
		}
		new KitEditorMenu(viewer, kit, -1).open();
	}
}
