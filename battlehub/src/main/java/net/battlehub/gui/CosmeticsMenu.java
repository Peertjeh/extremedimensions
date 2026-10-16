package net.battlehub.gui;

import java.util.List;

import net.battlehub.core.CosmeticDef;
import net.battlehub.core.CosmeticRules;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.progression.Cosmetics;
import net.battlehub.util.Icon;
import net.battlehub.util.Ids;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** The cosmetics shop: one button per category, showing what you wear. */
public final class CosmeticsMenu extends Screen {
	public CosmeticsMenu(ServerPlayer viewer) {
		super(viewer);
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.cosmetics.title");
	}

	@Override
	public int rows() {
		return 5;
	}

	static Item icon(String category) {
		return switch (category) {
			case "trail" -> Items.BLAZE_POWDER;
			case "aura" -> Ids.item("minecraft:feather");
			case "kill" -> Items.DIAMOND_SWORD;
			case "arrow" -> Items.SPECTRAL_ARROW;
			case "win" -> Items.FIREWORK_ROCKET;
			case "cry" -> Ids.item("minecraft:goat_horn");
			case "skin" -> Ids.item("minecraft:blaze_rod");
			case "pet" -> Ids.item("minecraft:dragon_head");
			case "hat" -> Ids.item("minecraft:golden_helmet");
			case "outfit" -> Ids.item("minecraft:leather_chestplate");
			default -> Ids.item("minecraft:magenta_dye");
		};
	}

	@Override
	public void draw(Canvas c) {
		PlayerProfile prof = PlayerStore.get(viewer);
		c.set(4, Icon.of(Items.SUNFLOWER).name(Lang.item(viewer, "menu.cosmetics.coins", prof.coins))
				.lore(Lang.loreLines(viewer, "menu.cosmetics.coins.lore")).build());
		int[] slots = net.battlehub.core.MenuSlots.centered(CosmeticRules.CATEGORIES.size(), 1, 6);
		int n = 0;
		for (String cat : CosmeticRules.CATEGORIES) {
			int slot = slots[n++];
			List<CosmeticDef> defs = Cosmetics.inCategory(cat);
			long owned = defs.stream().filter(d -> CosmeticRules.owns(prof, d)).count();
			CosmeticDef active = CosmeticRules.active(prof, cat, Cosmetics.all());
			Icon icon = Icon.of(icon(cat)).name(Lang.item(viewer, "menu.cosmetics.cat." + cat))
					.lore(Lang.loreLines(viewer, "menu.cosmetics.cat." + cat + ".lore"))
					.lore(Component.empty())
					.lore(active == null ? Lang.lore(viewer, "menu.cosmetics.wearing_none")
							: Lang.lore(viewer, "menu.cosmetics.wearing", Lang.colored(active.display)))
					.lore(Lang.lore(viewer, "menu.cosmetics.owned", owned, defs.size()))
					.clean();
			if (active != null) {
				icon.glint(true);
			}
			c.set(slot, icon.build(), click -> new CosmeticCategoryMenu(viewer, cat, 0).open());
		}
		c.back(() -> new MainMenu(viewer).open());
		c.fill();
	}
}
