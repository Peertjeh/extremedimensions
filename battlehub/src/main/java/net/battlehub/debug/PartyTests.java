package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.VARS;
import static net.battlehub.debug.SelfTest.p;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import net.battlehub.arena.ArenaSlots;
import net.battlehub.arena.ArenaWorld;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.CtfGame;
import net.battlehub.core.GameMode;
import net.battlehub.core.Outcome;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.games.CtfMatch;
import net.battlehub.games.FfaMatch;
import net.battlehub.games.KothMatch;
import net.battlehub.games.LmsMatch;
import net.battlehub.games.ParkourMatch;
import net.battlehub.lobby.HubItems;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.queue.Parties;
import net.battlehub.session.Sessions;
import net.battlehub.util.Icon;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Self-test scenarios for the seven party games, with four simulated players. */
final class PartyTests {
	private PartyTests() {}

	static final String[] NAMES = {"BHTestA", "BHTestB", "BHTestC", "BHTestD"};

	static Match match() {
		return (Match) VARS.get("match");
	}

	static void all(MinecraftServer server, Script s) {
		party(server, s, GameMode.SUMO, PartyTests::sumo);
		party(server, s, GameMode.TNT_RUN, PartyTests::tntRun);
		party(server, s, GameMode.BOW_SPLEEF, PartyTests::fallOff);
		party(server, s, GameMode.KOTH, PartyTests::koth);
		party(server, s, GameMode.CTF, PartyTests::ctf);
		party(server, s, GameMode.PARKOUR, PartyTests::parkour, new String[] {"hard", "hard", "easy", ""});
		soloParkour(server, s);
		party(server, s, GameMode.LMS, PartyTests::lms);
		party(server, s, GameMode.FFA, PartyTests::ffa, new String[] {"kit:axe", "kit:axe", "kit:mace", ""});
	}

	static void hit(String victim, String attacker, float amount) {
		ServerPlayer v = p(victim);
		v.hurtServer(v.level(), v.damageSources().playerAttack(p(attacker)), amount);
	}

	/** Free for All (kill target 3 for the test, the axe voted): kills, respawn with protection, first to 3 wins. */
	private static void ffa(Script s) {
		s.check("ffa: the vote picked the axe kit and everyone has it", () -> {
			for (String n : NAMES) {
				if (!p(n).getInventory().getItem(0).is(net.minecraft.world.item.Items.DIAMOND_AXE)) return false;
			}
			return ((FfaMatch) match()).target() == 3 && ((FfaMatch) match()).kitMode == GameMode.AXE;
		});
		s.run("ffa: A kills B", () -> hit("BHTestB", "BHTestA", 1000f));
		s.check("ffa: one kill for A, B waits to respawn", () -> match().kills(p("BHTestA").getUUID()) == 1
				&& p("BHTestB").gameMode() == GameType.SPECTATOR && match().alive().contains(p("BHTestB").getUUID()));
		s.waitFor("ffa: B is back with the kit", 60, () -> p("BHTestB").gameMode() == GameType.ADVENTURE
				&& p("BHTestB").getInventory().getItem(0).is(net.minecraft.world.item.Items.DIAMOND_AXE) && ArenaWorld.is(p("BHTestB").level()));
		s.check("ffa: B is protected right after spawning", () -> {
			float before = p("BHTestB").getHealth();
			hit("BHTestB", "BHTestC", 6f);
			return ((FfaMatch) match()).isProtected(p("BHTestB").getUUID()) && p("BHTestB").getHealth() == before;
		});
		s.run("ffa: A kills C", () -> hit("BHTestC", "BHTestA", 1000f));
		s.check("ffa: A leads with 2", () -> match().kills(p("BHTestA").getUUID()) == 2
				&& p("BHTestA").getUUID().equals(((FfaMatch) match()).leader()) && match().phase() == Match.Phase.RUNNING);
		s.run("ffa: A kills D, the third kill", () -> hit("BHTestD", "BHTestA", 1000f));
		s.waitFor("ffa: the kill target ends the match", 5, () -> match().phase() != Match.Phase.RUNNING);
		expectWinner(s, GameMode.FFA, "BHTestA");
	}

	static void tp(ServerPlayer p, double x, double y, double z) {
		p.teleportTo(p.level(), x, y, z, java.util.Set.of(), p.getYRot(), p.getXRot(), false);
		p.setDeltaMovement(Vec3.ZERO);
	}

	/** Lobby, countdown, match start; the scenario plays; then results, restore and arena release are checked. */
	static void party(MinecraftServer server, Script s, GameMode mode, Consumer<Script> scenario) {
		party(server, s, mode, scenario, new String[] {"", "", "", ""});
	}

	/** {@code votes}: what each of the four players votes for when joining (the parkour difficulty). */
	static void party(MinecraftServer server, Script s, GameMode mode, Consumer<Script> scenario, String[] votes) {
		String tag = mode.id();
		SelfTest.reset(s, tag);
		s.run(tag + ": four players join", () -> {
			for (String n : NAMES) SelfTest.join(server, n);
		});
		s.pause(10);
		s.run(tag + ": remember wins", () -> {
			VARS.put("winsA", PlayerStore.get(p("BHTestA").getUUID()).peek(mode).wins);
			VARS.put("coinsA", PlayerStore.get(p("BHTestA").getUUID()).coins);
		});
		s.run(tag + ": everyone joins the lobby", () -> {
			for (int i = 0; i < NAMES.length; i++) Parties.join(p(NAMES[i]), mode, votes[i]);
		});
		s.check(tag + ": lobby has four players", () -> Parties.members(mode).size() == 4
				&& Sessions.state(p("BHTestD").getUUID()) == Sessions.State.QUEUED);
		s.waitFor(tag + ": countdown done, match created", 200, () -> Matches.of(p("BHTestA").getUUID()) != null);
		s.waitFor(tag + ": arena built and game running", 600, () -> {
			Match m = Matches.of(p("BHTestA").getUUID());
			if (m != null && m.phase() == Match.Phase.RUNNING) {
				VARS.put("match", m);
				return true;
			}
			return false;
		});
		s.check(tag + ": all four are in the arena", () -> {
			for (String n : NAMES) {
				if (!ArenaWorld.is(p(n).level())) return false;
			}
			return match().participants().size() == 4;
		});
		scenario.accept(s);
		s.waitFor(tag + ": match closed, everybody back in the lobby", 400, () -> {
			if (match().phase() != Match.Phase.CLOSED) return false;
			for (String n : NAMES) {
				ServerPlayer pl = p(n);
				if (pl.hasDisconnected()) continue;
				if (ArenaWorld.is(pl.level()) || !Sessions.isFree(pl.getUUID())) return false;
			}
			return true;
		});
		s.check(tag + ": lobby items and game modes restored", () -> {
			for (String n : NAMES) {
				ServerPlayer pl = p(n);
				if (pl.hasDisconnected()) continue;
				String slot = Icon.tagOf(pl.getInventory().getItem(ConfigManager.config().compass.slot));
				if (pl.gameMode() == GameType.SPECTATOR || !HubItems.COMPASS.equals(slot)) {
					net.battlehub.BattleHub.LOGGER.warn("SELFTEST {} not restored: mode {}, compass slot '{}', state {}, in {}",
							n, pl.gameMode().getName(), slot, Sessions.state(pl.getUUID()), pl.level().dimension().identifier());
					return false;
				}
			}
			return true;
		});
		s.waitFor(tag + ": arena released", 600, () -> ArenaSlots.busy() == 0);
	}

	static void expectWinner(Script s, GameMode mode, String name) {
		s.check(mode.id() + ": " + name + " won, stats updated", () -> {
			UUID w = p(name).getUUID();
			PlayerProfile prof = PlayerStore.get(w);
			return match().outcome(w) == Outcome.WIN && prof.peek(match().mode).wins >= 1;
		});
	}

	private static void sumo(Script s) {
		s.check("sumo: hits push but do no damage", () -> {
			ServerPlayer b = p("BHTestB");
			float before = b.getHealth();
			b.hurtServer(b.level(), b.damageSources().playerAttack(p("BHTestA")), 6f);
			return b.getHealth() == before;
		});
		fallOff(s);
	}

	/** B, C and D fall off; A is the last one standing. */
	private static void fallOff(Script s) {
		s.run("B, C and D fall off", () -> {
			for (String n : new String[] {"BHTestB", "BHTestC", "BHTestD"}) {
				ServerPlayer pl = p(n);
				tp(pl, pl.getX(), match().arena().voidY() - 3, pl.getZ());
			}
		});
		s.waitFor("the fallen players are eliminated", 40, () -> match().alive().size() == 1 && match().alive().contains(p("BHTestA").getUUID()));
		s.check("eliminated players watch as spectators", () -> p("BHTestB").gameMode() == GameType.SPECTATOR);
		s.waitFor("match ended", 20, () -> match().phase() == Match.Phase.ENDING || match().phase() == Match.Phase.CLOSED);
		s.run("check winner", () -> { });
		s.check("A won", () -> match().outcome(p("BHTestA").getUUID()) == Outcome.WIN
				&& match().outcome(p("BHTestB").getUUID()) == Outcome.LOSS);
	}

	private static void tntRun(Script s) {
		s.run("tnt_run: A stands on a floor block", () -> {
			BlockPos o = match().arena().origin();
			VARS.put("tntBlock", o.offset(3, 0, 3));
			tp(p("BHTestA"), o.getX() + 3.5, o.getY() + 1, o.getZ() + 3.5);
		});
		s.waitFor("tnt_run: the block under A disappears", 60, () -> match().arena().level().getBlockState((BlockPos) VARS.get("tntBlock")).isAir()
				&& match().arena().level().getBlockState(((BlockPos) VARS.get("tntBlock")).below()).isAir());
		s.run("tnt_run: A moves to safe ground", () -> {
			BlockPos o = match().arena().origin();
			tp(p("BHTestA"), o.getX() - 6.5, o.getY() - 6, o.getZ() + 0.5);
		});
		fallOff(s);
	}

	private static void koth(Script s) {
		s.run("koth: A climbs the hill", () -> {
			KothMatch m = (KothMatch) match();
			BlockPos z = m.arena().mark("zone");
			tp(p("BHTestA"), z.getX() + 0.5, z.getY(), z.getZ() + 0.5);
		});
		s.run("koth: B is killed", () -> {
			ServerPlayer b = p("BHTestB");
			b.hurtServer(b.level(), b.damageSources().playerAttack(p("BHTestC")), 1000f);
		});
		s.check("koth: B waits as a spectator", () -> p("BHTestB").gameMode() == GameType.SPECTATOR && match().alive().contains(p("BHTestB").getUUID()));
		s.waitFor("koth: B respawns with the kit", 60, () -> p("BHTestB").gameMode() == GameType.ADVENTURE
				&& !p("BHTestB").getInventory().getItem(0).isEmpty());
		s.waitFor("koth: A scores and wins", 300, () -> match().phase() != Match.Phase.RUNNING);
		s.check("koth: A has the points", () -> ((KothMatch) match()).score().points(p("BHTestA").getUUID()) >= 4);
		expectWinner(s, GameMode.KOTH, "BHTestA");
	}

	private static void ctf(Script s) {
		s.run("ctf: find a red and a blue player", () -> {
			CtfMatch m = (CtfMatch) match();
			UUID red = m.ctf().members(CtfGame.Team.RED).get(0);
			UUID blue = m.ctf().members(CtfGame.Team.BLUE).get(0);
			for (String n : NAMES) {
				if (p(n).getUUID().equals(red)) VARS.put("red", n);
				if (p(n).getUUID().equals(blue)) VARS.put("blue", n);
			}
		});
		s.check("ctf: teams are 2 v 2 with team-coloured armour", () -> {
			CtfMatch m = (CtfMatch) match();
			return m.ctf().members(CtfGame.Team.RED).size() == 2 && m.ctf().members(CtfGame.Team.BLUE).size() == 2
					&& !p((String) VARS.get("red")).getItemBySlot(EquipmentSlot.CHEST).isEmpty();
		});
		s.check("ctf: no friendly fire", () -> {
			CtfMatch m = (CtfMatch) match();
			List<UUID> reds = m.ctf().members(CtfGame.Team.RED);
			ServerPlayer r1 = m.player(reds.get(0));
			ServerPlayer r2 = m.player(reds.get(1));
			float before = r2.getHealth();
			r2.hurtServer(r2.level(), r2.damageSources().playerAttack(r1), 4f);
			return r2.getHealth() == before;
		});
		s.run("ctf: red player runs to the blue flag", () -> {
			CtfMatch m = (CtfMatch) match();
			BlockPos f = m.home(CtfGame.Team.BLUE);
			tp(p((String) VARS.get("red")), f.getX() + 0.5, f.getY(), f.getZ() + 0.5);
		});
		s.waitFor("ctf: blue flag picked up", 20, () -> ((CtfMatch) match()).ctf().carrier(CtfGame.Team.BLUE) != null);
		s.run("ctf: carrier runs home", () -> {
			CtfMatch m = (CtfMatch) match();
			BlockPos f = m.home(CtfGame.Team.RED);
			tp(p((String) VARS.get("red")), f.getX() + 0.5, f.getY(), f.getZ() + 0.5);
		});
		s.waitFor("ctf: capture wins the match for red", 20, () -> match().phase() != Match.Phase.RUNNING);
		s.check("ctf: the red team won", () -> {
			CtfMatch m = (CtfMatch) match();
			for (UUID id : m.ctf().members(CtfGame.Team.RED)) {
				if (m.outcome(id) != Outcome.WIN) return false;
			}
			for (UUID id : m.ctf().members(CtfGame.Team.BLUE)) {
				if (m.outcome(id) != Outcome.LOSS) return false;
			}
			return true;
		});
	}

	/** Runs {@code name} over every checkpoint to the finish of the current parkour match. */
	private static void runCourse(Script s, String tag, String name) {
		s.run(tag + ": " + name + " runs to every checkpoint", () -> VARS.put("pk", 0));
		s.waitFor(tag + ": " + name + " reaches the finish", 900, () -> {
			ParkourMatch m = (ParkourMatch) match();
			if (m.race().hasFinished(p(name).getUUID())) return true;
			int step = (int) VARS.get("pk");
			// Visit the next checkpoint or finish platform every 5 ticks
			if (m.player(p(name).getUUID()).level().getServer().getTickCount() % 5 == 0) {
				int i = step;
				while (i < m.course().size() && m.course().get(i).checkpoint() == 0) i++;
				if (i < m.course().size()) {
					Vec3 top = m.platformTop(i);
					tp(p(name), top.x, top.y + 0.05, top.z);
					// Simulated players have no client to report that they landed
					p(name).setOnGround(true);
					VARS.put("pk", i + 1);
				}
			}
			return false;
		});
	}

	private static void parkour(Script s) {
		s.check("parkour: the vote picked hard (2 hard, 1 easy, 1 no preference)", () ->
				((ParkourMatch) match()).difficulty == net.battlehub.core.ParkourCourse.Difficulty.HARD
						&& ((ParkourMatch) match()).course().size() - 1 == net.battlehub.core.ParkourCourse.Difficulty.HARD.defaultJumps);
		s.run("parkour: C falls off the start", () -> {
			ServerPlayer c = p("BHTestC");
			tp(c, c.getX(), match().arena().voidY() - 3, c.getZ());
		});
		s.waitFor("parkour: C is put back, not eliminated", 30, () -> p("BHTestC").getY() > match().arena().voidY()
				&& match().alive().contains(p("BHTestC").getUUID()) && p("BHTestC").gameMode() == GameType.ADVENTURE);
		runCourse(s, "parkour", "BHTestA");
		s.check("parkour: best time stored for hard", () -> PlayerStore.get(p("BHTestA").getUUID()).bestTimes.containsKey("parkour_hard"));
		s.run("parkour: the others give up", () -> {
			for (String n : new String[] {"BHTestB", "BHTestC", "BHTestD"}) net.battlehub.command.HubCommands.hub(p(n));
		});
		s.waitFor("parkour: race over", 40, () -> match().phase() != Match.Phase.RUNNING);
		expectWinner(s, GameMode.PARKOUR, "BHTestA");
		s.check("parkour: a hard course pays coins", () -> PlayerStore.get(p("BHTestA").getUUID()).coins > (long) VARS.get("coinsA"));
	}

	/** Parkour alone: the race starts with one player, finishing counts as a win and a record on that difficulty. */
	private static void soloParkour(MinecraftServer server, Script s) {
		String tag = "solo parkour";
		SelfTest.reset(s, tag);
		s.run(tag + ": A joins alone, voting easy", () -> {
			SelfTest.join(server, "BHTestA");
			VARS.put("coinsA", PlayerStore.get(p("BHTestA").getUUID()).coins);
			VARS.put("xpA", PlayerStore.get(p("BHTestA").getUUID()).xp);
			Parties.join(p("BHTestA"), GameMode.PARKOUR, "easy");
		});
		s.waitFor(tag + ": the race starts with one player", 300, () -> {
			Match m = Matches.of(p("BHTestA").getUUID());
			if (m instanceof ParkourMatch pm && m.phase() == Match.Phase.RUNNING) {
				VARS.put("match", pm);
				return true;
			}
			return false;
		});
		s.check(tag + ": easy course", () -> ((ParkourMatch) match()).difficulty == net.battlehub.core.ParkourCourse.Difficulty.EASY
				&& match().participants().size() == 1);
		runCourse(s, tag, "BHTestA");
		s.waitFor(tag + ": finished, counted as a win", 100, () -> match().phase() != Match.Phase.RUNNING
				&& match().outcome(p("BHTestA").getUUID()) == Outcome.WIN
				&& PlayerStore.get(p("BHTestA").getUUID()).bestTimes.containsKey("parkour_easy"));
		s.check(tag + ": XP but no coins for an easy course", () -> {
			net.battlehub.core.PlayerProfile prof = PlayerStore.get(p("BHTestA").getUUID());
			// Only a level-up bonus may add coins
			var lv = ConfigManager.config().levels;
			int before = net.battlehub.progression.Ranks.curve().levelFor((long) VARS.get("xpA"));
			int after = net.battlehub.progression.Ranks.curve().levelFor(prof.xp);
			long bonus = 0;
			for (int l = before + 1; l <= after; l++) {
				if (lv.bonusEvery > 0 && l % lv.bonusEvery == 0) bonus += lv.bonusCoins;
			}
			return prof.xp > (long) VARS.get("xpA") && prof.coins == (long) VARS.get("coinsA") + bonus;
		});
		s.waitFor(tag + ": back in the lobby", 200, () -> match().phase() == Match.Phase.CLOSED);
	}

	private static void lms(Script s) {
		s.pause(60);
		s.check("lms: the border is shrinking", () -> ((LmsMatch) match()).borderSize() < ConfigManager.config().game(GameMode.LMS).setting("borderStart", 80));
		s.run("lms: A kills B, C and D", () -> {
			for (String n : new String[] {"BHTestB", "BHTestC", "BHTestD"}) {
				ServerPlayer pl = p(n);
				pl.hurtServer(pl.level(), pl.damageSources().playerAttack(p("BHTestA")), 1000f);
			}
		});
		s.waitFor("lms: last man standing", 20, () -> match().phase() != Match.Phase.RUNNING);
		s.check("lms: A has three kills", () -> match().kills(p("BHTestA").getUUID()) == 3);
		expectWinner(s, GameMode.LMS, "BHTestA");
	}
}
