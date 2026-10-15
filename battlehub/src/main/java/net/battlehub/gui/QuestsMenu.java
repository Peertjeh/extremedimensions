package net.battlehub.gui;

import java.util.List;

import net.battlehub.config.ConfigManager;
import net.battlehub.core.PlayerProfile;
import net.battlehub.core.QuestDef;
import net.battlehub.core.QuestProgress;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.progression.Retention;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Today's and this week's quests with their progress and rewards, and the login streak. */
public final class QuestsMenu extends Screen {
	public QuestsMenu(ServerPlayer viewer) {
		super(viewer);
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.quests.title");
	}

	@Override
	public int rows() {
		return 5;
	}

	@Override
	public int refreshTicks() {
		return 40;
	}

	/** "▮▮▮▯▯" for 3 of 5. */
	static String bar(int progress, int target) {
		int full = target <= 0 ? 10 : (int) Math.round(10.0 * Math.min(progress, target) / target);
		return "§a" + "▮".repeat(full) + "§8" + "▮".repeat(10 - full);
	}

	@Override
	public void draw(Canvas c) {
		PlayerProfile prof = PlayerStore.get(viewer);
		Retention.ensureQuests(prof);
		long secs = Retention.secondsToNewDay();
		c.set(4, Icon.of(Items.CLOCK).name(Lang.item(viewer, "menu.quests.info"))
				.lore(Lang.lore(viewer, "menu.quests.reset", secs / 3600, (secs % 3600) / 60))
				.lore(Lang.lore(viewer, "menu.quests.streak", prof.loginStreak))
				.lore(Lang.lore(viewer, "menu.quests.done", prof.questsDone)).build());
		row(c, prof.dailyQuests, 19, false);
		row(c, prof.weeklyQuests, 28, true);
		c.back(() -> new MainMenu(viewer).open());
		c.fill();
	}

	private void row(Canvas c, List<QuestProgress> quests, int rowStart, boolean weekly) {
		int n = quests.size();
		int first = rowStart + Math.max(0, (7 - (2 * n - 1)) / 2) + 1;
		for (int i = 0; i < n; i++) {
			QuestProgress qp = quests.get(i);
			QuestDef q = ConfigManager.progress().quest(qp.id);
			if (q == null) continue;
			Icon icon = Icon.of(qp.done ? Items.ENCHANTED_BOOK : (weekly ? Items.WRITABLE_BOOK : Items.PAPER))
					.name(Lang.item(viewer, weekly ? "menu.quests.weekly" : "menu.quests.daily", Lang.tr(viewer, "quest." + q.id)))
					.lore(Component.literal(bar(qp.progress, q.target) + " §f" + qp.progress + "/" + q.target))
					.lore(Lang.lore(viewer, "menu.quests.reward", q.coins, q.xp, q.passXp))
					.lore(Lang.lore(viewer, qp.done ? "menu.quests.completed" : "menu.quests.open"))
					.glint(qp.done).clean();
			c.set(Math.min(rowStart + 7, first + 2 * i), icon.build());
		}
	}
}
