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

	private static final java.util.Set<UUID> ADAPTIVE = new java.util.HashSet<>();

	/** The player lets the level follow their results (shown and explained after each fight). */
	public static boolean adaptive(UUID id) {
		return ADAPTIVE.contains(id);
	}

	public static void adaptive(UUID id, boolean on) {
		if (on) ADAPTIVE.add(id);
		else ADAPTIVE.remove(id);
	}

	/** The level a practice fight starts at: the chosen one, or the adaptive one. */
	public static Difficulty practiceLevel(ServerPlayer p) {
		if (!adaptive(p.getUUID())) return level(p.getUUID());
		return Difficulty.parse(net.battlehub.bot.AiMemory.get(p.level().getServer(), p.getUUID()).adaptiveLevel, Difficulty.INTERMEDIATE);
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.practice.title");
	}

	@Override
	public Style.Section section() {
		return Style.Section.PLAY;
	}

	@Override
	public int rows() {
		return 5;
	}

	@Override
	public void draw(Canvas c) {
		Difficulty chosen = practiceLevel(viewer);
		boolean adaptive = adaptive(viewer.getUUID());
		c.set(4, Icon.of(Items.TARGET).name(Lang.item(viewer, "menu.practice.info"))
				.lore(Lang.loreLines(viewer, "menu.practice.info.lore")).build());
		Item[] dyes = {Ids.item("minecraft:lime_dye"), Ids.item("minecraft:green_dye"), Ids.item("minecraft:yellow_dye"),
				Ids.item("minecraft:orange_dye"), Ids.item("minecraft:red_dye"), Ids.item("minecraft:purple_dye")};
		int slot = 10;
		for (Difficulty l : Difficulty.values()) {
			boolean on = l == chosen && !adaptive;
			c.set(slot, Icon.of(dyes[l.ordinal()]).name(Lang.item(viewer, "bots.level_button", Lang.tr(viewer, "bots.level." + l.id())))
					.lore(Lang.loreLines(viewer, "bots.level." + l.id() + ".lore"))
					.lore(Component.empty())
					.lore(Lang.lore(viewer, on ? "menu.practice.chosen" : "menu.practice.choose")).glint(on).build(),
					click -> {
						LEVEL.put(viewer.getUUID(), l);
						adaptive(viewer.getUUID(), false);
						new PracticeMenu(viewer).open();
					});
			// six levels in the row, the middle slot left free
			slot += slot == 12 ? 2 : 1;
		}
		// the free middle slot: let the level follow your results
		c.set(13, Icon.of(Items.COMPASS).name(Lang.item(viewer, "bots.level_button", Lang.tr(viewer, "bots.level.adaptive")))
				.lore(Lang.loreLines(viewer, "bots.level.adaptive.lore", Lang.tr(viewer, "bots.level." + practiceLevel(viewer).id())))
				.lore(Component.empty())
				.lore(Lang.lore(viewer, adaptive ? "menu.practice.chosen" : "menu.practice.choose")).glint(adaptive).build(),
				click -> {
					adaptive(viewer.getUUID(), true);
					new PracticeMenu(viewer).open();
				});
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
						Bots.practice(viewer, m, practiceLevel(viewer));
					});
		}
		for (GameMode m : GameMode.teamFights()) {
			c.set(slots[at++], Icon.of(Items.PLAYER_HEAD).count(m.opponents()).name(Lang.item(viewer, Icons.modeKey(m)))
					.lore(Lang.lore(viewer, "menu.practice.team", m.opponents(), Lang.tr(viewer, "bots.level." + chosen.id())))
					.lore(Lang.lore(viewer, "menu.practice.click")).clean().build(),
					// first the kit: sword, axe or mace, as in the PvP tab
					click -> new PracticeKitMenu(viewer, m).open());
		}
		c.back(() -> new MainMenu(viewer).open());
		c.fill();
	}
}
