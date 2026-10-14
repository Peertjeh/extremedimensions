package net.battlehub.gui;

import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.queue.Queues;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** One party game: join or leave its lobby, or invite a friend. */
public final class PartyGameMenu extends Screen {
	private final GameMode mode;

	public PartyGameMenu(ServerPlayer viewer, GameMode mode) {
		super(viewer);
		this.mode = mode;
	}

	@Override
	public Component title() {
		return Lang.tr(viewer, "menu.mode.title", Lang.tr(viewer, Icons.modeKey(mode)));
	}

	@Override
	public int rows() {
		return mode == GameMode.PARKOUR ? 4 : 3;
	}

	private static final String[] DIFFICULTIES = {"easy", "medium", "hard", "extreme", "random"};
	private static final String[] DIFFICULTY_ICONS = {"minecraft:lime_concrete", "minecraft:yellow_concrete", "minecraft:orange_concrete",
			"minecraft:red_concrete", "minecraft:white_concrete"};

	/** Parkour: one button per difficulty; clicking joins the lobby with that vote (or changes the vote). */
	private void difficulties(Canvas c) {
		String mine = net.battlehub.queue.Parties.vote(viewer.getUUID(), mode);
		java.util.Map<String, Integer> counts = new java.util.HashMap<>();
		for (String v : net.battlehub.queue.Parties.votes(mode)) {
			counts.merge(v, 1, Integer::sum);
		}
		for (int i = 0; i < DIFFICULTIES.length; i++) {
			String d = DIFFICULTIES[i];
			boolean chosen = d.equals(mine);
			Icon icon = Icon.of(DIFFICULTY_ICONS[i]).name(Lang.item(viewer, "parkour.difficulty." + d))
					.lore(Lang.loreLines(viewer, "parkour.difficulty." + d + ".lore"))
					.lore(Component.empty())
					.lore(Lang.lore(viewer, "menu.parkour.votes", counts.getOrDefault(d, 0)))
					.lore(Lang.lore(viewer, chosen ? "menu.parkour.voted" : "menu.parkour.click_vote"))
					.glint(chosen);
			c.set(20 + i, icon.build(), click -> {
				net.battlehub.queue.Parties.join(viewer, mode, d);
				new PartyGameMenu(viewer, mode).open();
			});
		}
	}

	@Override
	public int refreshTicks() {
		return 20;
	}

	@Override
	public void draw(Canvas c) {
		c.set(4, PartyGamesMenu.icon(viewer, mode).build());
		boolean in = Queues.inParty(viewer.getUUID(), mode);
		if (in) {
			c.set(11, Icon.of(Items.BARRIER).name(Lang.item(viewer, "menu.party.leave"))
					.lore(Lang.loreLines(viewer, "menu.party.leave.lore")).build(),
					click -> {
						Queues.toggleParty(viewer, mode);
						new PartyGameMenu(viewer, mode).open();
					});
		} else {
			c.set(11, Icon.of("minecraft:lime_dye").name(Lang.item(viewer, "menu.party.join"))
					.lore(Lang.loreLines(viewer, "menu.party.join.lore")).build(),
					click -> {
						Queues.toggleParty(viewer, mode);
						new PartyGameMenu(viewer, mode).open();
					});
		}
		if (mode == GameMode.PARKOUR) {
			difficulties(c);
		}
		c.set(15, Icon.of(Items.NAME_TAG).name(Lang.item(viewer, "menu.party.invite"))
				.lore(Lang.loreLines(viewer, "menu.party.invite.lore")).build(),
				click -> Queues.challengeMenu(viewer, mode));
		c.back(() -> new PartyGamesMenu(viewer).open());
		c.fill();
	}
}
