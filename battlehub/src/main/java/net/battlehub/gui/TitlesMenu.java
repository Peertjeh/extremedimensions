package net.battlehub.gui;

import java.util.List;

import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.core.PlayerProfile;
import net.battlehub.core.TitleDef;
import net.battlehub.core.TitleRules;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.progression.Ranks;
import net.battlehub.progression.TitleService;
import net.battlehub.util.Icon;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;

/** Pick an unlocked title, or buy one with coins. */
public final class TitlesMenu extends Screen {
	private final int page;

	public TitlesMenu(ServerPlayer viewer) {
		this(viewer, 0);
	}

	public TitlesMenu(ServerPlayer viewer, int page) {
		super(viewer);
		this.page = page;
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.titles.title");
	}

	@Override
	public int rows() {
		return 6;
	}

	@Override
	public void draw(Canvas c) {
		PlayerProfile prof = PlayerStore.get(viewer);
		List<TitleDef> titles = ConfigManager.titles().titles;
		int[] slots = c.inner();
		int perPage = slots.length;
		int pages = Math.max(1, (titles.size() + perPage - 1) / perPage);
		int p = Math.max(0, Math.min(page, pages - 1));
		for (int i = 0; i < perPage; i++) {
			int idx = p * perPage + i;
			if (idx >= titles.size()) {
				break;
			}
			TitleDef t = titles.get(idx);
			boolean unlocked = TitleRules.unlocked(t, prof, Ranks.startElo());
			boolean selected = t.id.equals(prof.title);
			Icon icon = Icon.of(unlocked ? Items.NAME_TAG : Items.PAPER).name(Lang.colored(t.display).withStyle(s -> s.withItalic(false)))
					.lore(requirement(t));
			if (selected) {
				icon.lore(Lang.lore(viewer, "menu.titles.selected")).glint(true);
			} else if (unlocked) {
				icon.lore(Lang.lore(viewer, "menu.titles.click_select"));
			} else if (t.purchasable()) {
				icon.lore(Lang.lore(viewer, prof.coins >= t.value ? "menu.titles.click_buy" : "menu.titles.too_expensive", t.value));
			} else {
				icon.lore(Lang.lore(viewer, "menu.titles.locked"));
			}
			c.set(slots[i], icon.build(), click -> onClick(t));
		}
		c.set(45, Icon.of(Items.BARRIER).name(Lang.item(viewer, "menu.titles.clear")).build(), click -> {
			TitleRules.select(null, prof, Ranks.startElo());
			PlayerStore.save(viewer.getUUID());
			TitleService.apply(viewer);
			Msg.send(viewer, "titles.cleared");
			new TitlesMenu(viewer, page).open();
		});
		c.set(53, Icon.of(Items.SUNFLOWER).name(Lang.item(viewer, "menu.titles.coins", prof.coins)).build());
		if (p > 0) {
			c.set(48, Icon.of(Items.ARROW).name(Lang.item(viewer, "menu.page.previous")).build(), click -> new TitlesMenu(viewer, p - 1).open());
		}
		if (p < pages - 1) {
			c.set(50, Icon.of(Items.ARROW).name(Lang.item(viewer, "menu.page.next")).build(), click -> new TitlesMenu(viewer, p + 1).open());
		}
		c.back(() -> new StatsMenu(viewer, viewer.getUUID()).open());
		c.fill();
	}

	private Component requirement(TitleDef t) {
		String req = t.requirement.toLowerCase(java.util.Locale.ROOT);
		GameMode mode = GameMode.byId(t.mode);
		Object modeName = mode == null ? Lang.tr(viewer, "menu.titles.any_mode") : Lang.tr(viewer, Icons.modeKey(mode));
		return Lang.lore(viewer, "title.req." + req, t.value, modeName);
	}

	private void onClick(TitleDef t) {
		PlayerProfile prof = PlayerStore.get(viewer);
		if (TitleRules.unlocked(t, prof, Ranks.startElo())) {
			TitleRules.select(t, prof, Ranks.startElo());
			PlayerStore.save(viewer.getUUID());
			TitleService.apply(viewer);
			Msg.send(viewer, "titles.selected", Lang.colored(t.display));
			Msg.sound(viewer, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
		} else if (t.purchasable()) {
			switch (TitleRules.buy(t, prof)) {
				case BOUGHT -> {
					TitleRules.select(t, prof, Ranks.startElo());
					PlayerStore.save(viewer.getUUID());
					TitleService.apply(viewer);
					Msg.send(viewer, "titles.bought", Lang.colored(t.display), t.value);
					Msg.sound(viewer, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.4f);
				}
				case TOO_EXPENSIVE -> {
					Msg.send(viewer, "titles.too_expensive", t.value, prof.coins);
					Msg.sound(viewer, SoundEvents.VILLAGER_NO, 0.7f, 1.0f);
				}
				default -> { }
			}
		} else {
			Msg.send(viewer, "titles.locked");
			Msg.sound(viewer, SoundEvents.VILLAGER_NO, 0.7f, 1.0f);
		}
		new TitlesMenu(viewer, page).open();
	}
}
