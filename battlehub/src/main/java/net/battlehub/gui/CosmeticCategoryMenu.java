package net.battlehub.gui;

import java.util.List;

import net.battlehub.core.CosmeticDef;
import net.battlehub.core.CosmeticRules;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.progression.Cosmetics;
import net.battlehub.util.Icon;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** All cosmetics of one category: buy, wear or take off. */
public final class CosmeticCategoryMenu extends Screen {
	private final String category;
	private final int page;

	public CosmeticCategoryMenu(ServerPlayer viewer, String category, int page) {
		super(viewer);
		this.category = category;
		this.page = page;
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.cosmetics.category_title", Lang.tr(viewer, "menu.cosmetics.cat." + category));
	}

	@Override
	public int rows() {
		return 6;
	}

	@Override
	public void draw(Canvas c) {
		PlayerProfile prof = PlayerStore.get(viewer);
		List<CosmeticDef> defs = Cosmetics.inCategory(category);
		CosmeticDef active = CosmeticRules.active(prof, category, Cosmetics.all());
		int[] slots = c.inner();
		int perPage = slots.length;
		int pages = Math.max(1, (defs.size() + perPage - 1) / perPage);
		int p = Math.max(0, Math.min(page, pages - 1));
		for (int i = 0; i < perPage; i++) {
			int idx = p * perPage + i;
			if (idx >= defs.size()) {
				break;
			}
			CosmeticDef d = defs.get(idx);
			boolean owned = CosmeticRules.owns(prof, d);
			boolean wearing = active != null && active.id.equals(d.id);
			Icon icon = Icon.of(d.icon).name(Lang.colored(d.display).withStyle(s -> s.withItalic(false))).clean();
			if (wearing) {
				icon.lore(Lang.lore(viewer, "menu.cosmetics.wearing_this")).lore(Lang.lore(viewer, "menu.cosmetics.click_off")).glint(true);
			} else if (owned) {
				icon.lore(Lang.lore(viewer, "menu.cosmetics.owned_this")).lore(Lang.lore(viewer, "menu.cosmetics.click_wear"));
			} else {
				icon.lore(Lang.lore(viewer, "menu.cosmetics.price", d.price))
						.lore(Lang.lore(viewer, prof.coins >= d.price ? "menu.cosmetics.click_buy" : "menu.cosmetics.too_expensive", d.price - prof.coins));
			}
			c.set(slots[i], icon.build(), click -> {
				Cosmetics.click(viewer, d);
				new CosmeticCategoryMenu(viewer, category, p).open();
			});
		}
		c.set(45, Icon.of(Items.BARRIER).name(Lang.item(viewer, "menu.cosmetics.clear")).build(), click -> {
			Cosmetics.clear(viewer, category);
			Msg.send(viewer, "cosmetics.cleared", Lang.tr(viewer, "menu.cosmetics.cat." + category));
			new CosmeticCategoryMenu(viewer, category, p).open();
		});
		c.set(53, Icon.of(Items.SUNFLOWER).name(Lang.item(viewer, "menu.cosmetics.coins", prof.coins)).build());
		if (p > 0) {
			c.set(48, Icon.of(Items.ARROW).name(Lang.item(viewer, "menu.page.previous")).build(),
					click -> new CosmeticCategoryMenu(viewer, category, p - 1).open());
		}
		if (p < pages - 1) {
			c.set(50, Icon.of(Items.ARROW).name(Lang.item(viewer, "menu.page.next")).build(),
					click -> new CosmeticCategoryMenu(viewer, category, p + 1).open());
		}
		c.back(() -> new CosmeticsMenu(viewer).open());
		c.fill();
	}
}
