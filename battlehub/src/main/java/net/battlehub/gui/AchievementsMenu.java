package net.battlehub.gui;

import net.battlehub.config.ConfigManager;
import net.battlehub.core.Achievement;
import net.battlehub.core.Daily;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.progression.Retention;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Every achievement with its tier and the way to the next one; prestige at the bottom. */
public final class AchievementsMenu extends Screen {
	private final boolean confirm;

	public AchievementsMenu(ServerPlayer viewer, boolean confirm) {
		super(viewer);
		this.confirm = confirm;
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.achievements.title");
	}

	@Override
	public Style.Section section() {
		return Style.Section.PROFILE;
	}

	@Override
	public int rows() {
		return 5;
	}

	private static Item icon(Achievement a) {
		return switch (a) {
			case FIGHTER -> Items.IRON_SWORD;
			case WINNER -> Items.GOLD_INGOT;
			case REGULAR -> Items.CLOCK;
			case STREAK -> Items.BLAZE_POWDER;
			case DUELIST -> Items.DIAMOND_SWORD;
			case CLIMBER -> Items.EXPERIENCE_BOTTLE;
			case COLLECTOR -> Items.CHEST;
			case RUNNER -> Items.FEATHER;
			case BED_BREAKER -> Items.TNT;
			case HUNTER -> Items.SPYGLASS;
			case RANKED -> Items.NETHER_STAR;
			case QUESTER -> Items.WRITABLE_BOOK;
		};
	}

	@Override
	public void draw(Canvas c) {
		PlayerProfile prof = PlayerStore.get(viewer);
		Retention.checkAchievements(viewer, prof);
		int[] slots = c.inner();
		Achievement[] all = Achievement.values();
		for (int i = 0; i < all.length && i < slots.length; i++) {
			Achievement a = all[i];
			int tier = a.tier(prof);
			boolean max = tier >= a.tiers.size();
			Icon icon = Icon.of(icon(a)).name(Lang.item(viewer, "menu.achievements.name", Lang.tr(viewer, "achievement." + a.id()),
					tier == 0 ? "-" : Retention.roman(tier), a.tiers.size()))
					.lore(Lang.loreLines(viewer, "achievement." + a.id() + ".lore"));
			if (max) {
				icon.lore(Lang.lore(viewer, "menu.achievements.max", a.value(prof)));
			} else {
				long need = a.tiers.get(tier);
				icon.lore(Lang.lore(viewer, "menu.achievements.next", a.value(prof), need, Retention.roman(tier + 1),
						Achievement.reward(tier + 1, ConfigManager.progress().achievementCoins)));
			}
			c.set(slots[i], icon.glint(max).clean().build());
		}
		boolean can = Retention.canPrestige(prof);
		Icon prestige = Icon.of(Items.BEACON).name(Lang.item(viewer, "menu.prestige", Daily.prestigeStars(prof.prestige), prof.prestige))
				.lore(Lang.loreLines(viewer, "menu.prestige.lore", ConfigManager.config().levels.maxLevel,
						Math.round(Daily.prestigeBonus(prof.prestige + 1) * 100)))
				.lore(Lang.lore(viewer, !can ? "menu.prestige.locked" : confirm ? "menu.prestige.confirm" : "menu.prestige.click"))
				.glint(can);
		c.set(net.battlehub.core.MenuLayout.extra(rows()), prestige.build(), click -> {
			if (!can) return;
			if (!confirm) {
				new AchievementsMenu(viewer, true).open();
				return;
			}
			viewer.closeContainer();
			Retention.prestige(viewer);
		});
		Style.title(c, viewer, Items.GOLDEN_APPLE, "menu.main.achievements");
		c.back(() -> new MainMenu(viewer).open());
		c.fill();
	}
}
