package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.VARS;
import static net.battlehub.debug.SelfTest.p;

import net.battlehub.core.GameMode;
import net.battlehub.core.Outcome;
import net.battlehub.core.SideQueue;
import net.battlehub.gui.HubMenu;
import net.battlehub.gui.PvpKitMenu;
import net.battlehub.gui.PvpMenu;
import net.battlehub.gui.TeamFightMenu;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.match.TeamFightMatch;
import net.battlehub.queue.PlayAgain;
import net.battlehub.queue.Queues;
import net.battlehub.queue.TeamFights;
import net.battlehub.session.Sessions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** The PvP menus and a 1v2 with the sword: sides by preference, no friendly fire, the team falls, play again. */
final class TeamFightTests {
	private TeamFightTests() {}

	private static TeamFightMatch match() {
		return (TeamFightMatch) VARS.get("match");
	}

	private static void hit(String victim, String attacker, float amount) {
		ServerPlayer v = p(victim);
		v.hurtServer(v.level(), v.damageSources().playerAttack(p(attacker)), amount);
	}

	static void all(MinecraftServer server, Script s) {
		String tag = "1v2";
		SelfTest.reset(s, tag);
		s.run(tag + ": the PvP menus open", () -> {
			SelfTest.join(server, "BHTestA");
			new PvpMenu(p("BHTestA")).open();
		});
		s.check(tag + ": PvP shows FFA, 1v1, 1v2 and 1v3", () -> p("BHTestA").containerMenu instanceof HubMenu m
				&& m.getSlot(10).getItem().is(Items.IRON_SWORD) && m.getSlot(12).getItem().is(Items.DIAMOND_SWORD)
				&& m.getSlot(14).getItem().getCount() == 3 && m.getSlot(16).getItem().getCount() == 4);
		s.run(tag + ": kit and side menus", () -> new PvpKitMenu(p("BHTestA"), GameMode.ONE_V_TWO).open());
		s.check(tag + ": sword, axe and mace", () -> p("BHTestA").containerMenu instanceof HubMenu m
				&& m.getSlot(11).getItem().is(Items.DIAMOND_SWORD) && m.getSlot(13).getItem().is(Items.DIAMOND_AXE)
				&& m.getSlot(15).getItem().is(Items.MACE));
		s.run(tag + ": the side menu and the FFA kits", () -> {
			new TeamFightMenu(p("BHTestA"), GameMode.ONE_V_TWO, GameMode.SWORD).open();
			new PvpKitMenu(p("BHTestA"), GameMode.FFA).open();
			p("BHTestA").closeContainer();
		});
		s.run(tag + ": A wants to be alone, B in the team, C does not mind", () -> {
			SelfTest.join(server, "BHTestB");
			SelfTest.join(server, "BHTestC");
			TeamFights.join(p("BHTestA"), GameMode.ONE_V_TWO, GameMode.SWORD, SideQueue.Side.SOLO);
			TeamFights.join(p("BHTestB"), GameMode.ONE_V_TWO, GameMode.SWORD, SideQueue.Side.TEAM);
			TeamFights.join(p("BHTestC"), GameMode.ONE_V_TWO, GameMode.SWORD, SideQueue.Side.ANY);
		});
		s.waitFor(tag + ": the match starts", 600, () -> {
			Match m = Matches.of(p("BHTestA").getUUID());
			if (m instanceof TeamFightMatch t && m.phase() == Match.Phase.RUNNING) {
				VARS.put("match", t);
				return true;
			}
			return false;
		});
		s.check(tag + ": A alone, B and C together, all with swords", () -> match().members(0).equals(java.util.List.of(p("BHTestA").getUUID()))
				&& match().members(1).contains(p("BHTestB").getUUID()) && match().members(1).contains(p("BHTestC").getUUID())
				&& p("BHTestA").getInventory().getItem(0).is(Items.DIAMOND_SWORD) && match().kitMode() == GameMode.SWORD);
		s.check(tag + ": no friendly fire in the team", () -> {
			float before = p("BHTestC").getHealth();
			hit("BHTestC", "BHTestB", 5f);
			return p("BHTestC").getHealth() == before;
		});
		s.run(tag + ": A takes out B", () -> hit("BHTestB", "BHTestA", 1000f));
		s.check(tag + ": C still stands, the match goes on", () -> match().phase() == Match.Phase.RUNNING
				&& match().alive().contains(p("BHTestC").getUUID()) && !match().alive().contains(p("BHTestB").getUUID()));
		s.run(tag + ": A takes out C", () -> hit("BHTestC", "BHTestA", 1000f));
		s.check(tag + ": A wins alone", () -> match().phase() != Match.Phase.RUNNING && match().winnerSide() == 0
				&& match().outcome(p("BHTestA").getUUID()) == Outcome.WIN && match().outcome(p("BHTestB").getUUID()) == Outcome.LOSS
				&& match().outcome(p("BHTestC").getUUID()) == Outcome.LOSS);
		s.waitFor(tag + ": back in the hub with a play-again offer", 200, () -> match().phase() == Match.Phase.CLOSED
				&& PlayAgain.get(p("BHTestA").getUUID()) != null && PlayAgain.get(p("BHTestA").getUUID()).mode() == GameMode.ONE_V_TWO);
		s.run(tag + ": A plays again", () -> PlayAgain.accept(p("BHTestA")));
		s.check(tag + ": A waits alone in the 1v2 sword queue again", () -> Sessions.state(p("BHTestA").getUUID()) == Sessions.State.QUEUED
				&& TeamFights.sideOf(p("BHTestA").getUUID(), GameMode.ONE_V_TWO, GameMode.SWORD) == SideQueue.Side.SOLO);
		s.run(tag + ": A leaves the queue", () -> Queues.leaveAll(p("BHTestA")));
		s.check(tag + ": not waiting any more", () -> TeamFights.sideOf(p("BHTestA").getUUID(), GameMode.ONE_V_TWO, GameMode.SWORD) == null
				&& Sessions.state(p("BHTestA").getUUID()) == Sessions.State.LOBBY);
	}
}
