package net.battlehub.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.battlehub.core.GameMode;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The stats of one player in every mode: duels, 1v2 and 1v3, then the party games. */
public final class ModeStatsMenu extends Screen {
	private final UUID target;

	public ModeStatsMenu(ServerPlayer viewer, UUID target) {
		super(viewer);
		this.target = target;
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.stats.per_mode.title", PlayerStore.get(target).name);
	}

	@Override
	public Style.Section section() {
		return Style.Section.PROFILE;
	}

	@Override
	public int rows() {
		return 6;
	}

	@Override
	public void draw(Canvas c) {
		PlayerProfile p = PlayerStore.get(target);
		List<GameMode> modes = new ArrayList<>(List.of(GameMode.duels()));
		modes.addAll(List.of(GameMode.teamFights()));
		modes.addAll(List.of(GameMode.parties()));
		int[] slots = c.inner();
		for (int i = 0; i < modes.size() && i < slots.length; i++) {
			c.set(slots[i], StatsMenu.modeIcon(viewer, p, modes.get(i)).build());
		}
		c.back(() -> new StatsMenu(viewer, target).open());
		c.fill();
	}
}
