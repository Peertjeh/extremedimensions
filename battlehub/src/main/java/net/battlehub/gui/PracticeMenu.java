package net.battlehub.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.battlehub.bot.Bots;
import net.battlehub.core.ai.Difficulty;
import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.util.Icon;
import net.battlehub.util.Ids;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Practice against bots: pick how good they are, then a duel, 1v2 or 1v3. Starts right away. */
public final class PracticeMenu extends Screen {
	private static final Map<UUID, Difficulty> LEVEL = new HashMap<>();

	public PracticeMenu(ServerPlayer viewer) {
		super(viewer);
	}

	public static Difficulty level(UUID id) {
		return LEVEL.getOrDefault(id, Difficulty.INTERMEDIATE);
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.practice.title");
	}

	@Override
	public int rows() {
		return 5;
	}

	@Override
	public void draw(Canvas c) {
		Difficulty chosen = level(viewer.getUUID());
		c.set(4, Icon.of(Items.TARGET).name(Lang.item(viewer, "menu.practice.info"))
				.lore(Lang.loreLines(viewer, "menu.practice.info.lore")).build());
		Item[] dyes = {Ids.item("minecraft:lime_dye"), Ids.item("minecraft:green_dye"), Ids.item("minecraft:yellow_dye"),
				Ids.item("minecraft:orange_dye"), Ids.item("minecraft:red_dye"), Ids.item("minecraft:purple_dye")};
		int slot = 10;
		for (Difficulty l : Difficulty.values()) {
			boolean on = l == chosen;
			c.set(slot, Icon.of(dyes[l.ordinal()]).name(Lang.item(viewer, "bots.level_button", Lang.tr(viewer, "bots.level." + l.id())))
					.lore(Lang.loreLines(viewer, "bots.level." + l.id() + ".lore"))
					.lore(Component.empty())
					.lore(Lang.lore(viewer, on ? "menu.practice.chosen" : "menu.practice.choose")).glint(on).build(),
					click -> {
						LEVEL.put(viewer.getUUID(), l);
						new PracticeMenu(viewer).open();
					});
			// six levels in the row, the middle slot left free
			slot += slot == 12 ? 2 : 1;
		}
		java.util.List<GameMode> all = new java.util.ArrayList<>(java.util.List.of(GameMode.duels()));
		all.addAll(java.util.List.of(GameMode.teamFights()));
		int[] slots = net.battlehub.core.MenuSlots.centered(all.size(), 2, 7);
		int at = 0;
		for (GameMode m : GameMode.duels()) {
			c.set(slots[at++], Icon.of(Icons.mode(m)).name(Lang.item(viewer, Icons.modeKey(m)))
					.lore(Lang.lore(viewer, "menu.practice.duel", Lang.tr(viewer, "bots.level." + chosen.id())))
					.lore(Lang.lore(viewer, "menu.practice.click")).clean().build(),
					click -> {
						viewer.closeContainer();
						Bots.practice(viewer, m, level(viewer.getUUID()));
					});
		}
		for (GameMode m : GameMode.teamFights()) {
			c.set(slots[at++], Icon.of(Items.PLAYER_HEAD).count(m.opponents()).name(Lang.item(viewer, Icons.modeKey(m)))
					.lore(Lang.lore(viewer, "menu.practice.team", m.opponents(), Lang.tr(viewer, "bots.level." + chosen.id())))
					.lore(Lang.lore(viewer, "menu.practice.click")).clean().build(),
					click -> {
						viewer.closeContainer();
						Bots.practiceTeam(viewer, m, GameMode.SWORD, level(viewer.getUUID()));
					});
		}
		c.back(() -> new MainMenu(viewer).open());
		c.fill();
	}
}
