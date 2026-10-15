package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.p;

import java.util.List;

import net.battlehub.BattleHub;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.gui.CosmeticCategoryMenu;
import net.battlehub.gui.CosmeticsMenu;
import net.battlehub.gui.HubMenu;
import net.battlehub.gui.MainMenu;
import net.battlehub.progression.Cosmetics;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

/**
 * The cosmetics shop: buying through the menu, wearing and taking off, too expensive, the name colour on the
 * scoreboard team and every effect. A and B keep their cosmetics, so the duels after this play the kill, win and
 * trail effects in real matches.
 */
final class CosmeticsTests {
	private CosmeticsTests() {}

	private static PlayerProfile prof(String name) {
		return PlayerStore.get(p(name).getUUID());
	}

	/** Clicks the item with this name in the open menu of {@code player}. */
	private static void click(String player, String itemName) {
		ServerPlayer pl = p(player);
		HubMenu m = (HubMenu) pl.containerMenu;
		for (int i = 0; i < m.getRowCount() * 9; i++) {
			if (m.getSlot(i).getItem().getHoverName().getString().equals(itemName)) {
				m.clicked(i, 0, ContainerInput.PICKUP, pl);
				return;
			}
		}
		throw new IllegalStateException(itemName + " is not in the menu");
	}

	private static void command(MinecraftServer server, String cmd) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
	}

	static void all(MinecraftServer server, Script s) {
		coinsCommand(server, s);
		shop(server, s);
	}

	/** /battlehub coins as an admin (the server console): give, take (never below zero), set, also offline. */
	private static void coinsCommand(MinecraftServer server, Script s) {
		String tag = "coins command";
		s.run(tag + ": set A to 1000, give 250", () -> {
			SelfTest.join(server, "BHTestA");
			command(server, "battlehub coins set BHTestA 1000");
			command(server, "battlehub coins give BHTestA 250");
		});
		s.check(tag + ": A has 1250", () -> prof("BHTestA").coins == 1250);
		s.run(tag + ": take more than A has", () -> command(server, "battlehub coins take BHTestA 99999"));
		s.check(tag + ": A has 0, not less", () -> prof("BHTestA").coins == 0);
		s.run(tag + ": a name BattleHub does not know changes nothing", () -> command(server, "battlehub coins give NoSuchPlayer 5"));
		s.check(tag + ": still 0", () -> prof("BHTestA").coins == 0 && net.battlehub.data.PlayerStore.find("NoSuchPlayer") == null);
	}

	private static void shop(MinecraftServer server, Script s) {
		String tag = "cosmetics";
		SelfTest.reset(s, tag);
		s.run(tag + ": A has 5000 coins, B none", () -> {
			SelfTest.join(server, "BHTestA");
			SelfTest.join(server, "BHTestB");
			for (String n : List.of("BHTestA", "BHTestB")) {
				prof(n).ownedCosmetics.clear();
				prof(n).cosmetics.clear();
			}
			prof("BHTestA").coins = 5000;
			prof("BHTestB").coins = 0;
			new MainMenu(p("BHTestA")).open();
		});
		s.check(tag + ": the compass menu has a cosmetics button", () -> p("BHTestA").containerMenu instanceof HubMenu m
				&& m.getSlot(13).getItem().is(Items.FIREWORK_ROCKET));
		s.run(tag + ": A opens the shop", () -> click("BHTestA", "Cosmetics"));
		s.waitFor(tag + ": seven categories (with the wardrobe)", 5, () -> p("BHTestA").containerMenu instanceof HubMenu m
				&& m.screen() instanceof CosmeticsMenu && !m.getSlot(10).getItem().isEmpty() && !m.getSlot(16).getItem().isEmpty());
		s.run(tag + ": A opens trails", () -> click("BHTestA", "Trails"));
		s.waitFor(tag + ": the trails page", 5, () -> p("BHTestA").containerMenu instanceof HubMenu m && m.screen() instanceof CosmeticCategoryMenu);
		s.run(tag + ": A clicks Flames (400 coins)", () -> click("BHTestA", "Flames"));
		s.waitFor(tag + ": bought, paid and in use", 5, () -> prof("BHTestA").ownedCosmetics.contains("trail_flames")
				&& prof("BHTestA").coins == 4600 && "trail_flames".equals(prof("BHTestA").cosmetics.get("trail")));
		s.run(tag + ": A clicks Flames again", () -> click("BHTestA", "Flames"));
		s.waitFor(tag + ": turned off, still owned", 5, () -> !prof("BHTestA").cosmetics.containsKey("trail")
				&& prof("BHTestA").ownedCosmetics.contains("trail_flames"));
		s.run(tag + ": and once more", () -> click("BHTestA", "Flames"));
		s.waitFor(tag + ": in use again without paying twice", 5, () -> "trail_flames".equals(prof("BHTestA").cosmetics.get("trail"))
				&& prof("BHTestA").coins == 4600);
		s.run(tag + ": B tries Lightning without coins", () -> Cosmetics.click(p("BHTestB"), Cosmetics.byId("kill_lightning")));
		s.check(tag + ": refused, nothing paid", () -> !prof("BHTestB").ownedCosmetics.contains("kill_lightning")
				&& prof("BHTestB").coins == 0 && !prof("BHTestB").cosmetics.containsKey("kill"));
		s.run(tag + ": A buys a red name, Lightning, fire arrows and fireworks", () -> {
			for (String id : List.of("color_red", "kill_lightning", "arrow_flames", "win_fireworks")) {
				Cosmetics.click(p("BHTestA"), Cosmetics.byId(id));
			}
			p("BHTestA").closeContainer();
		});
		s.check(tag + ": all paid (5000 - 3500)", () -> prof("BHTestA").coins == 1500 && prof("BHTestA").cosmetics.size() == 5);
		s.check(tag + ": A's name is red on the nametag, in chat and in the tab list", () -> {
			PlayerTeam team = server.getScoreboard().getPlayersTeam(p("BHTestA").getScoreboardName());
			return team != null && team.getColor().equals(java.util.Optional.of(TeamColor.RED));
		});
		s.run(tag + ": B earns coins and buys a few", () -> {
			prof("BHTestB").coins = 3000;
			for (String id : List.of("trail_notes", "kill_blood", "win_hearts", "color_aqua")) {
				Cosmetics.click(p("BHTestB"), Cosmetics.byId(id));
			}
		});
		s.check(tag + ": B paid 2100", () -> prof("BHTestB").coins == 900 && prof("BHTestB").cosmetics.size() == 4);
		s.run(tag + ": every kill and win effect plays once", () -> {
			ServerPlayer a = p("BHTestA");
			ServerLevel level = a.level();
			for (String e : Cosmetics.KILL_EFFECTS) {
				Cosmetics.killEffect(e, level, a.position());
			}
			for (int t = 0; t <= 40; t += 10) {
				for (String e : Cosmetics.WIN_EFFECTS) {
					Cosmetics.winEffect(e, level, a.position(), t);
				}
			}
		});
		s.check(tag + ": every particle, effect, colour and icon exists", () -> {
			List<String> problems = Cosmetics.problems();
			if (!problems.isEmpty()) {
				BattleHub.LOGGER.warn("SELFTEST cosmetics problems: {}", problems);
			}
			return problems.isEmpty();
		});
		s.run(tag + ": A walks with a trail", () -> {
			ServerPlayer a = p("BHTestA");
			a.teleportTo(a.getX() + 1, a.getY(), a.getZ());
		});
		s.pause(3);
		s.run(tag + ": A takes the red name off in the shop", () -> {
			new CosmeticCategoryMenu(p("BHTestA"), "color", 0).open();
			click("BHTestA", "None");
		});
		s.waitFor(tag + ": A's name has no colour any more", 5, () -> {
			PlayerTeam team = server.getScoreboard().getPlayersTeam(p("BHTestA").getScoreboardName());
			return !prof("BHTestA").cosmetics.containsKey("color") && (team == null || team.getColor().isEmpty());
		});
		s.run(tag + ": A wears it again for the duels", () -> {
			Cosmetics.click(p("BHTestA"), Cosmetics.byId("color_red"));
			p("BHTestA").closeContainer();
		});
		s.run(tag + ": A goes back to survival", () -> net.battlehub.hub.Travel.leave(p("BHTestA")));
		s.check(tag + ": no BattleHub colour in the normal world", () -> {
			PlayerTeam team = server.getScoreboard().getPlayersTeam(p("BHTestA").getScoreboardName());
			return team == null || team.getColor().isEmpty();
		});
		s.run(tag + ": A comes back with /hub", () -> net.battlehub.hub.Travel.enter(p("BHTestA")));
		s.check(tag + ": red again in BattleHub", () -> {
			PlayerTeam team = server.getScoreboard().getPlayersTeam(p("BHTestA").getScoreboardName());
			return team != null && team.getColor().equals(java.util.Optional.of(TeamColor.RED));
		});
	}
}
