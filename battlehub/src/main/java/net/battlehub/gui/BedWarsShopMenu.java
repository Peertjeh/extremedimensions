package net.battlehub.gui;

import java.util.List;

import net.battlehub.config.BedWarsConfig;
import net.battlehub.core.bedwars.Resource;
import net.battlehub.core.bedwars.Shop;
import net.battlehub.games.bedwars.BedWarsMatch;
import net.battlehub.games.bedwars.BedWarsShop;
import net.battlehub.games.bedwars.BedWarsUi;
import net.battlehub.lang.Lang;
import net.battlehub.util.Icon;
import net.battlehub.util.Ids;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Bed Wars shop: categories on the top row, the offers below, your resources at the bottom. Like every
 * BattleHub menu nothing can be taken out; a click asks the match to buy, which checks and pays in one go, and
 * the page is redrawn in place.
 */
public final class BedWarsShopMenu extends Screen {
	private final BedWarsMatch match;
	private String category;

	public BedWarsShopMenu(ServerPlayer viewer, BedWarsMatch match, String category) {
		super(viewer);
		this.match = match;
		this.category = category;
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "bedwars.shop.title");
	}

	@Override
	public int rows() {
		return 6;
	}

	@Override
	public int refreshTicks() {
		return 10;
	}

	public String category() {
		return category;
	}

	private static Item categoryIcon(String c) {
		return switch (c) {
			case "blocks" -> Ids.item("minecraft:white_wool");
			case "weapons" -> Items.GOLDEN_SWORD;
			case "armor" -> Items.CHAINMAIL_CHESTPLATE;
			case "tools" -> Items.STONE_PICKAXE;
			case "utility" -> Items.TNT;
			default -> Items.DIAMOND;
		};
	}

	@Override
	public void draw(Canvas c) {
		String[] cats = BedWarsShop.CATEGORIES;
		for (int i = 0; i < cats.length; i++) {
			String cat = cats[i];
			boolean on = cat.equals(category);
			c.set(1 + i + (i >= 3 ? 1 : 0), Icon.of(categoryIcon(cat)).name(Lang.item(viewer, "bedwars.shop.cat." + cat))
					.lore(Lang.lore(viewer, on ? "bedwars.shop.cat_open" : "menu.click_to_open")).glint(on).clean().build(), click -> {
						category = cat;
						redraw();
					});
		}
		int[] inner = c.inner();
		int n = 0;
		if ("upgrades".equals(category)) {
			for (BedWarsConfig.Upgrade u : match.shop().upgrades()) {
				if (n >= inner.length) break;
				c.set(inner[n++], upgradeIcon(u), click -> {
					match.buyUpgrade(viewer, u.id);
					redraw();
				});
			}
		} else {
			List<Shop.Offer> offers = match.shop().offers(category);
			for (Shop.Offer o : offers) {
				if (n >= inner.length) break;
				ItemStack icon = offerIcon(o);
				if (icon.isEmpty()) continue;
				c.set(inner[n++], icon, click -> {
					match.buy(viewer, o.id());
					redraw();
				});
			}
		}
		c.set(49, wallet());
		c.fill();
	}

	private void redraw() {
		if (viewer.containerMenu instanceof HubMenu m && m.screen() == this) {
			m.redraw();
		}
	}

	private Component price(Resource r, int amount) {
		return Lang.format(r.color + amount + " ").append(Lang.tr(viewer, "bedwars.resource." + r.id()));
	}

	private ItemStack offerIcon(Shop.Offer o) {
		ItemStack stack = match.shop().stack(viewer, o);
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		Icon icon = Icon.of(stack).count(o.count());
		if (o.isUpgrade() && "armor".equals(o.group())) {
			icon.name(Lang.item(viewer, "bedwars.shop.armor", Lang.tr(viewer, "bedwars.material." + o.item())));
		}
		icon.lore(Lang.lore(viewer, "bedwars.shop.price", price(o.currency(), o.price())));
		if (o.isUpgrade()) {
			icon.lore(Lang.lore(viewer, "bedwars.shop.tier", BedWarsUi.roman(o.tier())));
		}
		icon.lore(Component.empty());
		Shop.Result r = match.shop().check(viewer, o);
		icon.lore(Lang.lore(viewer, switch (r) {
			case BOUGHT -> "bedwars.shop.click_buy";
			case TOO_EXPENSIVE -> "bedwars.shop.need_more";
			case ALREADY_OWNED -> "bedwars.shop.owned";
			case NO_SPACE -> "bedwars.shop.full";
		}));
		return icon.glint(r == Shop.Result.ALREADY_OWNED).clean().build();
	}

	private static Item upgradeItem(String id) {
		return switch (id) {
			case "sharpness" -> Items.IRON_SWORD;
			case "protection" -> Items.IRON_CHESTPLATE;
			case "forge" -> Items.FURNACE;
			default -> Ids.item("minecraft:enchanted_book");
		};
	}

	private ItemStack upgradeIcon(BedWarsConfig.Upgrade u) {
		int team = match.teamOf(viewer.getUUID());
		int level = match.shop().upgradeLevel(team, u.id);
		int next = match.shop().nextPrice(team, u.id);
		Icon icon = Icon.of(upgradeItem(u.id)).name(Lang.item(viewer, "bedwars.upgrade." + u.id))
				.lore(Lang.loreLines(viewer, "bedwars.upgrade." + u.id + ".desc"))
				.lore(Lang.lore(viewer, "bedwars.shop.level", level, u.prices.length));
		if (next < 0) {
			icon.lore(Component.empty()).lore(Lang.lore(viewer, "bedwars.shop.maxed"));
		} else {
			Resource cur = match.shop().upgradeCurrency(u.id);
			icon.lore(Lang.lore(viewer, "bedwars.shop.price", price(cur, next))).lore(Component.empty())
					.lore(Lang.lore(viewer, viewer.getInventory().countItem(Ids.item(cur.item)) >= next ? "bedwars.shop.click_buy" : "bedwars.shop.need_more"));
		}
		return icon.glint(level > 0).clean().build();
	}

	private ItemStack wallet() {
		Icon icon = Icon.of(Items.CHEST).name(Lang.item(viewer, "bedwars.shop.wallet"));
		for (Resource r : Resource.values()) {
			icon.lore(Lang.lore(viewer, "bedwars.shop.wallet_line", price(r, viewer.getInventory().countItem(Ids.item(r.item)))));
		}
		return icon.build();
	}
}
