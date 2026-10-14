package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.VARS;
import static net.battlehub.debug.SelfTest.p;

import net.battlehub.core.DuelFormat;
import net.battlehub.core.GameMode;
import net.battlehub.core.Outcome;
import net.battlehub.data.PlayerStore;
import net.battlehub.lobby.HubItems;
import net.battlehub.match.DuelMatch;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.queue.Challenges;
import net.battlehub.queue.PlayAgain;
import net.battlehub.queue.Queues;
import net.battlehub.session.Sessions;
import net.battlehub.util.Icon;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Best of 3 duels (rounds, score, one result for the series) and the "play again" offer (queue and rematch). */
final class FormatTests {
	private FormatTests() {}

	private static DuelMatch match() {
		return (DuelMatch) VARS.get("match");
	}

	private static void kill(String victim, String killer) {
		ServerPlayer v = p(victim);
		v.hurtServer(v.level(), v.damageSources().playerAttack(p(killer)), 1000f);
	}

	private static void waitRunning(Script s, String tag, int round) {
		s.waitFor(tag + ": round " + round + " running", 300, () -> match().phase() == Match.Phase.RUNNING && match().round() == round);
	}

	static void all(MinecraftServer server, Script s) {
		bestOfThree(server, s);
		rematch(server, s);
	}

	private static void bestOfThree(MinecraftServer server, Script s) {
		String tag = "bo3";
		SelfTest.reset(s, tag);
		s.run(tag + ": A and B choose best of 3 and queue", () -> {
			SelfTest.join(server, "BHTestA");
			SelfTest.join(server, "BHTestB");
			PlayerStore.get(p("BHTestA")).duelFormat = "bo3";
			PlayerStore.get(p("BHTestB")).duelFormat = "bo3";
			VARS.put("winsA", PlayerStore.get(p("BHTestA")).overall.wins);
			Queues.join(p("BHTestA"), GameMode.SWORD, false);
			Queues.join(p("BHTestB"), GameMode.SWORD, false);
		});
		s.waitFor(tag + ": a best-of-3 match starts", 100, () -> {
			Match m = Matches.of(p("BHTestA").getUUID());
			if (m instanceof DuelMatch d && d.format == DuelFormat.BO3) {
				VARS.put("match", d);
				return true;
			}
			return false;
		});
		waitRunning(s, tag, 1);
		s.run(tag + ": A wins round 1", () -> kill("BHTestB", "BHTestA"));
		s.check(tag + ": 1-0, B is back with a fresh kit for round 2", () -> match().roundWins(p("BHTestA").getUUID()) == 1
				&& match().round() == 2 && match().phase() == Match.Phase.COUNTDOWN && match().alive().size() == 2
				&& !p("BHTestB").getInventory().getItem(0).isEmpty() && p("BHTestB").getHealth() == p("BHTestB").getMaxHealth());
		waitRunning(s, tag, 2);
		s.run(tag + ": A holds a totem of undying and takes a fatal hit", () -> {
			p("BHTestA").setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
			kill("BHTestA", "BHTestB");
		});
		s.check(tag + ": the totem saves A, the round goes on", () -> match().round() == 2 && match().phase() == Match.Phase.RUNNING
				&& match().roundWins(p("BHTestB").getUUID()) == 0 && match().alive().contains(p("BHTestA").getUUID())
				&& p("BHTestA").getHealth() > 0 && p("BHTestA").getOffhandItem().isEmpty());
		// Vanilla ignores a second hit that is not stronger during the half second after a hit
		s.pause(12);
		s.run(tag + ": B wins round 2", () -> kill("BHTestA", "BHTestB"));
		s.check(tag + ": 1-1, round 3", () -> match().roundWins(p("BHTestB").getUUID()) == 1 && match().round() == 3);
		waitRunning(s, tag, 3);
		s.run(tag + ": A wins round 3", () -> kill("BHTestB", "BHTestA"));
		s.check(tag + ": A wins the series 2-1, counted as one match", () -> match().outcome(p("BHTestA").getUUID()) == Outcome.WIN
				&& match().outcome(p("BHTestB").getUUID()) == Outcome.LOSS && match().kills(p("BHTestA").getUUID()) == 2
				&& PlayerStore.get(p("BHTestA")).overall.wins == (int) VARS.get("winsA") + 1);
		s.waitFor(tag + ": back in the lobby with a play-again offer", 200, () -> match().phase() == Match.Phase.CLOSED
				&& PlayAgain.active(p("BHTestA").getUUID())
				&& HubItems.AGAIN.equals(Icon.tagOf(p("BHTestA").getInventory().getItem(HubItems.AGAIN_SLOT))));
		s.run(tag + ": A clicks play again", () -> PlayAgain.accept(p("BHTestA")));
		s.check(tag + ": A waits in the best-of-3 sword queue", () -> Sessions.state(p("BHTestA").getUUID()) == Sessions.State.QUEUED
				&& Sessions.get(p("BHTestA").getUUID()).queue.equals("sword:bo3") && !PlayAgain.active(p("BHTestA").getUUID()));
		s.run(tag + ": back to best of 1", () -> {
			Queues.leaveAll(p("BHTestA"));
			PlayerStore.get(p("BHTestA")).duelFormat = "bo1";
			PlayerStore.get(p("BHTestB")).duelFormat = "bo1";
		});
	}

	private static void rematch(MinecraftServer server, Script s) {
		String tag = "rematch";
		SelfTest.reset(s, tag);
		s.run(tag + ": A challenges B to axe", () -> {
			SelfTest.join(server, "BHTestA");
			SelfTest.join(server, "BHTestB");
			Challenges.send(p("BHTestA"), p("BHTestB"), GameMode.AXE);
			Challenges.accept(p("BHTestB"), p("BHTestA"));
		});
		s.waitFor(tag + ": duel running", 300, () -> {
			Match m = Matches.of(p("BHTestA").getUUID());
			if (m instanceof DuelMatch d && d.challenge && m.phase() == Match.Phase.RUNNING) {
				VARS.put("match", d);
				return true;
			}
			return false;
		});
		s.run(tag + ": A wins", () -> kill("BHTestB", "BHTestA"));
		s.waitFor(tag + ": both are offered a rematch", 200, () -> match().phase() == Match.Phase.CLOSED
				&& PlayAgain.get(p("BHTestA").getUUID()) != null && PlayAgain.get(p("BHTestA").getUUID()).kind() == PlayAgain.Kind.REMATCH
				&& PlayAgain.get(p("BHTestB").getUUID()) != null);
		s.run(tag + ": A asks for a rematch, B clicks rematch too", () -> {
			PlayAgain.accept(p("BHTestA"));
			PlayAgain.accept(p("BHTestB"));
		});
		s.waitFor(tag + ": the rematch starts", 100, () -> {
			Match m = Matches.of(p("BHTestA").getUUID());
			return m instanceof DuelMatch d && d != match() && d.mode == GameMode.AXE && m.participants().contains(p("BHTestB").getUUID());
		});
		s.run(tag + ": B gives up", () -> net.battlehub.command.HubCommands.hub(p("BHTestB")));
		s.waitFor(tag + ": the rematch is over", 200, () -> Matches.of(p("BHTestA").getUUID()) == null);
	}
}
