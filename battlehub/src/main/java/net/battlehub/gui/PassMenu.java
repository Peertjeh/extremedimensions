package net.battlehub.gui;

import net.battlehub.config.ConfigManager;
import net.battlehub.config.ProgressConfig;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.progression.Retention;
import net.battlehub.progression.Seasons;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** The free season pass: every tier with its reward, reached ones lit up. Two pages of 28 tiers. */
public final class PassMenu extends Screen {
	private static final int PER_PAGE = 28;
	private final int page;

	public PassMenu(ServerPlayer viewer, int page) {
		super(viewer);
		this.page = page;
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.pass.title", Math.max(1, Seasons.current()));
	}

	@Override
	public int rows() {
		return 6;
	}

	@Override
	public void draw(Canvas c) {
		ProgressConfig.Pass pass = ConfigManager.progress().pass;
		PlayerProfile prof = PlayerStore.get(viewer);
		int tier = Retention.passTier(prof);
		long into = prof.passXp - (long) tier * pass.xpPerTier;
		c.set(4, Icon.of(Items.NETHER_STAR).name(Lang.item(viewer, "menu.pass.info", tier, pass.tiers))
				.lore(tier >= pass.tiers ? Lang.lore(viewer, "menu.pass.max")
						: Lang.lore(viewer, "menu.pass.next", Math.max(0, into), pass.xpPerTier))
				.lore(Lang.loreLines(viewer, "menu.pass.info.lore")).glint(true).build());
		int start = page * PER_PAGE;
		int i = 0;
		for (int row = 1; row <= 4; row++) {
			for (int col = 1; col <= 7; col++) {
				int t = start + i + 1;
				i++;
				if (t > pass.tiers) continue;
				boolean reached = tier >= t;
				String reward = Retention.tierReward(t);
				Item item = reward.equals("cosmetic") ? Items.CHEST : t % 5 == 0 ? Items.GOLD_INGOT
						: reached ? net.battlehub.util.Ids.item("minecraft:lime_stained_glass_pane") : net.battlehub.util.Ids.item("minecraft:gray_stained_glass_pane");
				Component what = reward.equals("cosmetic") ? Lang.tr(viewer, "menu.pass.cosmetic")
						: Lang.tr(viewer, "menu.pass.coins", reward.substring(6));
				c.set(row * 9 + col, Icon.of(item).count(Math.max(1, Math.min(64, t)))
						.name(Lang.item(viewer, reached ? "menu.pass.tier_done" : "menu.pass.tier", t))
						.lore(what).glint(reached && (item == Items.CHEST || item == Items.GOLD_INGOT)).build());
			}
		}
		if (page > 0) {
			c.set(45, Icon.of(Items.ARROW).name(Lang.item(viewer, "menu.page.previous")).build(), click -> new PassMenu(viewer, page - 1).open());
		}
		if ((page + 1) * PER_PAGE < pass.tiers) {
			c.set(53, Icon.of(Items.ARROW).name(Lang.item(viewer, "menu.page.next")).build(), click -> new PassMenu(viewer, page + 1).open());
		}
		c.back(() -> new MainMenu(viewer).open());
		c.fill();
	}
}
