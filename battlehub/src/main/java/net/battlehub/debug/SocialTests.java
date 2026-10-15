package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.VARS;
import static net.battlehub.debug.SelfTest.p;

import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.gui.ClanMenu;
import net.battlehub.gui.CustomGameMenu;
import net.battlehub.gui.FriendsMenu;
import net.battlehub.gui.HubMenu;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.queue.Parties;
import net.battlehub.session.Sessions;
import net.battlehub.social.Clans;
import net.battlehub.social.CustomGames;
import net.battlehub.social.Friends;
import net.battlehub.social.Groups;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Friends, parties that play together, clans with their weekly war, and private games that count for nothing. */
final class SocialTests {
	private SocialTests() {}

	private static final String A = "BHTestA";
	private static final String B = "BHTestB";
	private static final String C = "BHTestC";
	private static final String D = "BHTestD";

	private static PlayerProfile prof(String n) {
		return PlayerStore.get(p(n));
	}

	private static Match match() {
		return (Match) VARS.get("match");
	}

	static void all(MinecraftServer server, Script s) {
		friends(server, s);
		parties(server, s);
		clans(server, s);
		custom(server, s);
	}

	private static void friends(MinecraftServer server, Script s) {
		String tag = "friends";
		SelfTest.reset(s, tag);
		s.run(tag + ": A and B are here; A asks B", () -> {
			SelfTest.join(server, A);
			SelfTest.join(server, B);
			Friends.remove(p(A), B);
			prof(B).friendRequests.clear();
			Friends.add(p(A), B);
		});
		s.check(tag + ": B has a request, nobody is friends yet", () -> prof(B).friendRequests.contains(p(A).getUUID().toString())
				&& !Friends.areFriends(p(A).getUUID(), p(B).getUUID()));
		s.run(tag + ": B accepts", () -> Friends.accept(p(B), A));
		s.check(tag + ": friends both ways, request gone", () -> Friends.areFriends(p(A).getUUID(), p(B).getUUID())
				&& Friends.areFriends(p(B).getUUID(), p(A).getUUID()) && prof(B).friendRequests.isEmpty());
		s.check(tag + ": B is on A's list and shown as in the lobby", () -> Friends.list(server, p(A)).stream()
				.anyMatch(f -> f.uuid.equals(p(B).getUUID().toString())));
		s.run(tag + ": the friends menu opens", () -> new FriendsMenu(p(A)).open());
		s.check(tag + ": friends menu", () -> p(A).containerMenu instanceof HubMenu);
		s.run(tag + ": close; A removes B again", () -> {
			p(A).closeContainer();
			Friends.remove(p(A), B);
		});
		s.check(tag + ": no longer friends", () -> !Friends.areFriends(p(A).getUUID(), p(B).getUUID())
				&& !Friends.areFriends(p(B).getUUID(), p(A).getUUID()));
	}

	/** A and C are a party: when A joins Capture the Flag, C comes along, and they end up on the same team. */
	private static void parties(MinecraftServer server, Script s) {
		String tag = "groups";
		SelfTest.reset(s, tag);
		s.run(tag + ": four players; A invites C, C accepts", () -> {
			for (String n : PartyTests.NAMES) SelfTest.join(server, n);
			Groups.clear();
			Groups.invite(p(A), p(C));
			Groups.accept(p(C), p(A));
		});
		s.check(tag + ": A leads a party with C", () -> Groups.of(p(C).getUUID()) != null && Groups.isLeader(p(A).getUUID())
				&& Groups.of(p(A).getUUID()).members().size() == 2);
		s.run(tag + ": A joins the Capture the Flag lobby", () -> Parties.join(p(A), GameMode.CTF, ""));
		s.check(tag + ": C came along", () -> Parties.members(GameMode.CTF).contains(p(C).getUUID())
				&& Sessions.state(p(C).getUUID()) == Sessions.State.QUEUED);
		s.run(tag + ": B and D join too", () -> {
			Parties.join(p(B), GameMode.CTF, "");
			Parties.join(p(D), GameMode.CTF, "");
		});
		s.waitFor(tag + ": game running", 800, () -> {
			Match m = Matches.of(p(A).getUUID());
			if (m != null && m.phase() == Match.Phase.RUNNING) {
				VARS.put("match", m);
				return true;
			}
			return false;
		});
		s.check(tag + ": A and C on one team, B and D on the other", () -> !match().isEnemy(p(A).getUUID(), p(C).getUUID())
				&& !match().isEnemy(p(B).getUUID(), p(D).getUUID()) && match().isEnemy(p(A).getUUID(), p(B).getUUID()));
		s.run(tag + ": end the game", () -> Matches.end(match().id));
		s.waitFor(tag + ": everyone back in the lobby", 400, () -> {
			for (String n : PartyTests.NAMES) {
				if (!Sessions.isFree(p(n).getUUID())) return false;
			}
			return match().phase() == Match.Phase.CLOSED;
		});
		s.run(tag + ": C leaves the party", () -> Groups.leave(p(C), false));
		s.check(tag + ": a party of one is no party", () -> Groups.of(p(A).getUUID()) == null && Groups.of(p(C).getUUID()) == null);
	}

	private static void clans(MinecraftServer server, Script s) {
		String tag = "clans";
		SelfTest.reset(s, tag);
		s.run(tag + ": A has the coins and starts clan Testers [TST]", () -> {
			SelfTest.join(server, A);
			SelfTest.join(server, B);
			if (Clans.of(p(A).getUUID()) != null) Clans.disband(p(A));
			if (Clans.of(p(B).getUUID()) != null) Clans.leave(p(B));
			prof(A).coins += ConfigManager.config().clans.createCost;
			VARS.put("cCoins", prof(A).coins);
			Clans.create(p(A), "Testers", "tst");
		});
		s.check(tag + ": the clan exists, A owns it and paid", () -> {
			Clans.Clan c = Clans.of(p(A).getUUID());
			return c != null && c.tag.equals("TST") && c.owner.equals(p(A).getUUID().toString())
					&& prof(A).coins == (long) VARS.get("cCoins") - ConfigManager.config().clans.createCost;
		});
		s.check(tag + ": the same tag cannot be taken twice", () -> {
			Clans.create(p(B), "Others", "TST");
			return Clans.byName("Others") == null;
		});
		s.run(tag + ": A invites B, B joins", () -> {
			Clans.invite(p(A), B);
			Clans.join(p(B), "Testers");
		});
		s.check(tag + ": two members", () -> Clans.of(p(B).getUUID()) == Clans.of(p(A).getUUID())
				&& Clans.of(p(A).getUUID()).members.size() == 2);
		s.run(tag + ": A wins a ranked match, B a casual one", () -> {
			Clans.Clan c = Clans.of(p(A).getUUID());
			VARS.put("cPoints", c.weeklyPoints);
			Clans.won(p(A).getUUID(), true);
			Clans.won(p(B).getUUID(), false);
		});
		s.check(tag + ": four points this week", () -> Clans.of(p(A).getUUID()).weeklyPoints == (long) VARS.get("cPoints") + 4);
		s.run(tag + ": the week ends", () -> {
			Clans.Clan c = Clans.of(p(A).getUUID());
			// Only this clan has points: it wins the war
			for (Clans.Clan o : Clans.all()) {
				if (o != c) o.weeklyPoints = 0;
			}
			VARS.put("cWars", c.warsWon);
			VARS.put("cCoinsB", prof(B).coins);
			Clans.endWeekForTest(server);
		});
		s.check(tag + ": the clan won the war, B got the reward, the week starts at zero", () -> {
			Clans.Clan c = Clans.of(p(A).getUUID());
			return c.warsWon == (int) VARS.get("cWars") + 1 && c.weeklyPoints == 0 && c.points >= 4
					&& prof(B).coins == (long) VARS.get("cCoinsB") + ConfigManager.config().clans.warReward;
		});
		s.check(tag + ": the clan tag is in front of A's name", () -> {
			var team = server.getScoreboard().getPlayerTeam(net.battlehub.progression.TitleService.teamName(p(A).getUUID()));
			return team != null && team.getPlayerPrefix().getString().contains("[TST]");
		});
		s.run(tag + ": the clan menu opens", () -> new ClanMenu(p(A)).open());
		s.check(tag + ": clan menu", () -> p(A).containerMenu instanceof HubMenu);
		s.run(tag + ": close; A disbands the clan", () -> {
			p(A).closeContainer();
			Clans.disband(p(A));
		});
		s.check(tag + ": the clan is gone for both", () -> Clans.byName("Testers") == null && Clans.of(p(A).getUUID()) == null
				&& Clans.of(p(B).getUUID()) == null);
	}

	/** A opens a private sword duel, B joins with the code; A wins, nothing is counted. */
	private static void custom(MinecraftServer server, Script s) {
		String tag = "custom";
		SelfTest.reset(s, tag);
		s.run(tag + ": A opens a private sword lobby", () -> {
			SelfTest.join(server, A);
			SelfTest.join(server, B);
			CustomGames.clear();
			CustomGames.Lobby l = CustomGames.create(p(A), GameMode.SWORD);
			VARS.put("code", l == null ? "" : l.code);
		});
		s.check(tag + ": a four-letter code", () -> ((String) VARS.get("code")).length() == 4);
		s.check(tag + ": a wrong code is refused", () -> !CustomGames.join(p(B), "ZZZZ") || "ZZZZ".equals(VARS.get("code")));
		s.run(tag + ": the custom menu opens for B", () -> new CustomGameMenu(p(B)).open());
		s.check(tag + ": custom menu", () -> p(B).containerMenu instanceof HubMenu);
		s.run(tag + ": B joins with the code", () -> {
			p(B).closeContainer();
			CustomGames.join(p(B), ((String) VARS.get("code")).toLowerCase(java.util.Locale.ROOT));
		});
		s.check(tag + ": B is in A's lobby; only A can start", () -> CustomGames.of(p(B).getUUID()) == CustomGames.of(p(A).getUUID())
				&& !CustomGames.start(p(B)));
		s.run(tag + ": remember stats; A starts", () -> {
			VARS.put("xWins", prof(A).overall.wins);
			VARS.put("xCoins", prof(A).coins);
			VARS.put("xLoss", prof(B).overall.losses);
			CustomGames.start(p(A));
		});
		s.waitFor(tag + ": the duel runs, unrated", 600, () -> {
			Match m = Matches.of(p(A).getUUID());
			if (m != null && m.phase() == Match.Phase.RUNNING) {
				VARS.put("match", m);
				return m.unrated();
			}
			return false;
		});
		s.check(tag + ": the lobby is gone once the game started", () -> CustomGames.of(p(A).getUUID()) == null);
		s.pause(12);
		s.run(tag + ": A kills B", () -> {
			ServerPlayer b = p(B);
			b.hurtServer(b.level(), b.damageSources().playerAttack(p(A)), 1000f);
		});
		s.waitFor(tag + ": back in the lobby", 400, () -> match().phase() == Match.Phase.CLOSED
				&& Sessions.isFree(p(A).getUUID()) && Sessions.isFree(p(B).getUUID()));
		s.check(tag + ": no wins, losses or coins counted", () -> prof(A).overall.wins == (int) VARS.get("xWins")
				&& prof(A).coins == (long) VARS.get("xCoins") && prof(B).overall.losses == (int) VARS.get("xLoss"));
	}
}
