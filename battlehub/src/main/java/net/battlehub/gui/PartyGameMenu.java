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
		return mode == GameMode.TOURNAMENT ? 5 : mode == GameMode.PARKOUR || mode == GameMode.BED_WARS ? 4 : 3;
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

	private static java.util.Map<String, Integer> count(java.util.Collection<String> votes) {
		java.util.Map<String, Integer> counts = new java.util.HashMap<>();
		for (String v : votes) {
			counts.merge(v, 1, Integer::sum);
		}
		return counts;
	}

	private Icon voteLore(Icon icon, java.util.Map<String, Integer> counts, String option, boolean chosen) {
		return icon.lore(Component.empty())
				.lore(Lang.lore(viewer, "menu.parkour.votes", counts.getOrDefault(option, 0)))
				.lore(Lang.lore(viewer, chosen ? "menu.parkour.voted" : "menu.parkour.click_vote"))
				.glint(chosen);
	}

	/** Tournament: team size (row 3) and kit (row 4); a click joins the lobby with that vote. */
	private void tournamentVotes(Canvas c) {
		String myTeams = net.battlehub.queue.Parties.vote(viewer.getUUID(), mode, "teams");
		java.util.Map<String, Integer> teamVotes = count(net.battlehub.queue.Parties.votes(mode, "teams"));
		c.set(22, Icon.of(Items.PLAYER_HEAD).name(Lang.item(viewer, "menu.tournament.teams"))
				.lore(Lang.loreLines(viewer, "menu.tournament.teams.lore")).build());
		int[] teamSlots = {20, 21, 23, 24};
		java.util.List<String> sizes = net.battlehub.tournament.Tournaments.TEAM_SIZES;
		for (int i = 0; i < sizes.size(); i++) {
			String size = sizes.get(i);
			int n = Integer.parseInt(size);
			Icon icon = Icon.of(n == 1 ? Items.IRON_SWORD : Items.PLAYER_HEAD).count(n)
					.name(Lang.item(viewer, n == 1 ? "menu.tournament.solo" : "menu.tournament.team_size", n));
			c.set(teamSlots[i], voteLore(icon, teamVotes, size, size.equals(myTeams)).clean().build(), click -> {
				net.battlehub.queue.Parties.join(viewer, mode, "teams:" + size);
				new PartyGameMenu(viewer, mode).open();
			});
		}
		String myKit = net.battlehub.queue.Parties.vote(viewer.getUUID(), mode, "kit");
		java.util.Map<String, Integer> kitVotes = count(net.battlehub.queue.Parties.votes(mode, "kit"));
		c.set(31, Icon.of(Items.IRON_CHESTPLATE).name(Lang.item(viewer, "menu.tournament.kit"))
				.lore(Lang.loreLines(viewer, "menu.tournament.kit.lore")).clean().build());
		int[] kitSlots = {28, 29, 30, 32, 33, 34};
		java.util.List<String> kits = net.battlehub.tournament.Tournaments.KITS;
		for (int i = 0; i < kits.size(); i++) {
			String kit = kits.get(i);
			GameMode km = GameMode.byId(kit);
			Icon icon = km == null ? Icon.of("minecraft:white_concrete").name(Lang.item(viewer, "menu.tournament.kit_random"))
					: Icon.of(Icons.mode(km)).name(Lang.item(viewer, Icons.modeKey(km)));
			c.set(kitSlots[i], voteLore(icon, kitVotes, kit, kit.equals(myKit)).clean().build(), click -> {
				net.battlehub.queue.Parties.join(viewer, mode, "kit:" + kit);
				new PartyGameMenu(viewer, mode).open();
			});
		}
	}

	/** Bed Wars: solo (everyone for themselves) or duos; a click joins the lobby with that vote. */
	private void bedWarsFormat(Canvas c) {
		String mine = net.battlehub.queue.Parties.vote(viewer.getUUID(), mode, "format");
		java.util.Map<String, Integer> counts = count(net.battlehub.queue.Parties.votes(mode, "format"));
		String[] formats = {"solo", "duo"};
		int[] slots = {21, 23};
		for (int i = 0; i < formats.length; i++) {
			String f = formats[i];
			Icon icon = (f.equals("solo") ? Icon.of(Items.IRON_SWORD) : Icon.of(Items.PLAYER_HEAD).count(2))
					.name(Lang.item(viewer, "bedwars.format." + f))
					.lore(Lang.loreLines(viewer, "bedwars.format." + f + ".lore"));
			c.set(slots[i], voteLore(icon, counts, f, f.equals(mine)).clean().build(), click -> {
				net.battlehub.queue.Parties.join(viewer, mode, "format:" + f);
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
		} else if (mode == GameMode.TOURNAMENT) {
			tournamentVotes(c);
		} else if (mode == GameMode.BED_WARS) {
			bedWarsFormat(c);
		}
		if (mode != GameMode.TOURNAMENT) {
			c.set(13, Icon.of(Items.FILLED_MAP).name(Lang.item(viewer, "menu.mapvote.button"))
					.lore(Lang.lore(viewer, "menu.mapvote.leading", MapVoteMenu.leading(viewer, mode)))
					.lore(Lang.loreLines(viewer, "menu.mapvote.button.lore")).clean().build(),
					click -> new MapVoteMenu(viewer, mode).open());
		}
		c.set(15, Icon.of(Items.NAME_TAG).name(Lang.item(viewer, "menu.party.invite"))
				.lore(Lang.loreLines(viewer, "menu.party.invite.lore")).build(),
				click -> Queues.challengeMenu(viewer, mode));
		c.back(() -> new PartyGamesMenu(viewer).open());
		c.fill();
	}
}
