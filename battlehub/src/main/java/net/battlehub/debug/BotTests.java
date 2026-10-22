package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.VARS;
import static net.battlehub.debug.SelfTest.p;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.battlehub.arena.ArenaSlots;
import net.battlehub.bot.BotPlayer;
import net.battlehub.bot.Bots;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.DuelFormat;
import net.battlehub.core.GameMode;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.games.ParkourMatch;
import net.battlehub.gui.HubMenu;
import net.battlehub.hub.HubNpcs;
import net.battlehub.lobby.HubItems;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.progression.FeaturedMode;
import net.battlehub.queue.Parties;
import net.battlehub.queue.Queues;
import net.battlehub.session.Sessions;
import net.battlehub.util.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/**
 * Bots and the lobby: practice duels (sword and bow) where the bot really fights, queues and party lobbies filled
 * with bots, a bot running a parkour course, nothing saved for bots and no stats from bot matches; the lobby
 * characters and the mode of the day.
 */
final class BotTests {
	private BotTests() {}

	static final String A = "BHTestA";

	private static HubConfig.Bots cfg() {
		return ConfigManager.config().bots;
	}

	private static Match matchOfA() {
		return Matches.of(p(A).getUUID());
	}

	private static List<BotPlayer> botsIn(Match m) {
		List<BotPlayer> out = new ArrayList<>();
		if (m == null) return out;
		for (UUID id : m.participants()) {
			ServerPlayer pl = m.player(id);
			if (pl instanceof BotPlayer b) out.add(b);
		}
		return out;
	}

	private static void endAndClean(Script s, String tag) {
		s.run(tag + ": end the match", () -> {
			Match m = matchOfA();
			if (m != null) Matches.end(m.id);
		});
		s.waitFor(tag + ": bots gone and arenas free", 600, () -> Bots.online() == 0 && ArenaSlots.busy() == 0);
	}

	static void all(MinecraftServer server, Script s) {
		lobby(server, s);
		practiceSword(server, s);
		learnedSword(server, s);
		practiceBow(server, s);
		queueFill(server, s);
		partyFill(server, s);
		spleefBots(server, s);
		parkour(server, s);
		kothBots(server, s);
		bridgeBot(server, s);
		bedWarsBot(server, s);
		s.run("bots: filling off again for the other tests", () -> {
			cfg().fillQueues = false;
			cfg().fillAfterSeconds = 15;
			cfg().partyFillTo = 4;
		});
	}

	/**
	 * The learned combat model (battlehub/ml) in a real duel: the master level steers the bot to A, lands hits through
	 * the real crosshair check, and beats A who only stands there.
	 */
	private static void learnedSword(MinecraftServer server, Script s) {
		String tag = "bots learned";
		SelfTest.reset(s, tag);
		s.run(tag + ": learned combat on; A starts a sword practice duel against a master bot", () -> {
			cfg().learnedCombat = true;
			cfg().learnedModes = new ArrayList<>(List.of("sword", "boxing"));
			SelfTest.join(server, A);
			Bots.practice(p(A), GameMode.SWORD, net.battlehub.core.ai.Difficulty.MASTER);
		});
		s.waitFor(tag + ": the duel runs with one bot", 600, () -> {
			Match m = matchOfA();
			return m != null && m.phase() == Match.Phase.RUNNING && botsIn(m).size() == 1;
		});
		s.waitFor(tag + ": the model steers the bot to A and lands hits", 600, () -> {
			Match m = matchOfA();
			if (m == null || botsIn(m).isEmpty()) return false;
			BotPlayer b = botsIn(m).get(0);
			boolean hit = p(A).getHealth() < p(A).getMaxHealth();
			if (Bots.learnedTicks(b) > 40 && hit) {
				net.battlehub.BattleHub.LOGGER.info("SELFTEST learned combat: {} ticks steered, A at {} health | {}", Bots.learnedTicks(b), p(A).getHealth(),
						String.join(" | ", Bots.inspect(b)));
				return true;
			}
			logBot("learned bot", m, b, "learnedLogged");
			return false;
		});
		s.waitFor(tag + ": A, standing still, loses to it", 1200, () -> {
			Match m = matchOfA();
			if (m == null) return false;
			if (m.phase() == Match.Phase.RUNNING) {
				if (!botsIn(m).isEmpty()) logBot("learned bot", m, botsIn(m).get(0), "learnedLogged2");
				return false;
			}
			return m.outcome(p(A).getUUID()) == net.battlehub.core.Outcome.LOSS;
		});
		endAndClean(s, tag);
		s.run(tag + ": learned combat off again for the other tests", () -> cfg().learnedCombat = false);
	}

	/** The characters on the plaza and the mode of the day. */
	private static void lobby(MinecraftServer server, Script s) {
		String tag = "npcs";
		SelfTest.reset(s, tag);
		s.run(tag + ": A in the lobby; sumo is the mode of the day", () -> {
			SelfTest.join(server, A);
			VARS.put("featuredModes", new ArrayList<>(ConfigManager.config().featured.modes));
			ConfigManager.config().featured.enabled = true;
			ConfigManager.config().featured.modes = new ArrayList<>(List.of("sumo"));
		});
		s.run(tag + ": A stands at the spawn, by the characters", () -> {
			net.battlehub.lobby.Lobby.teleport(p(A));
			VARS.put("npcPolls", 0);
		});
		s.waitFor(tag + ": eight characters, shown to A", 200, () -> HubNpcs.count() == 8 && HubNpcs.showing(p(A).getUUID()));
		s.waitFor(tag + ": eight click boxes", 400, () -> {
			boolean ok = HubNpcs.hitboxes() == 8;
			int polls = (int) VARS.get("npcPolls") + 1;
			VARS.put("npcPolls", polls);
			if (!ok && polls % 100 == 0) {
				net.minecraft.server.level.ServerLevel hub = (net.minecraft.server.level.ServerLevel) p(A).level();
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST npcs after {} ticks: {} (A at {}, mode {}, chunk loaded {}, client loaded {}, {} chunks loaded in the hub)",
						polls, HubNpcs.describe(server), p(A).position(), p(A).gameMode(), hub.isLoaded(p(A).blockPosition()),
						p(A).connection.hasClientLoaded(), hub.getChunkSource().getLoadedChunksCount());
			}
			if (!ok && polls == 100) {
				// Test players have no client; if the plaza is still not loaded round them, load it the way a client would
				net.minecraft.server.level.ServerLevel hub = (net.minecraft.server.level.ServerLevel) p(A).level();
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST npcs: plaza chunks not loaded round the test player, forcing them for this test");
				for (int cx = -2; cx <= 1; cx++) {
					for (int cz = -2; cz <= 1; cz++) hub.setChunkForced(cx, cz, true);
				}
				VARS.put("npcForced", true);
			}
			return ok;
		});
		s.waitFor(tag + ": the characters are on A's client", 200, () -> {
			java.util.Set<Integer> missing = new java.util.HashSet<>(HubNpcs.entityIds());
			missing.removeAll(p(A).clientEntities());
			if (!missing.isEmpty() && !missing.equals(VARS.put("npcMissing", missing))) {
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST npcs: {} of {} entities not on A's client", missing.size(), HubNpcs.entityIds().size());
			}
			return missing.isEmpty();
		});
		s.waitFor(tag + ": labels show the name, how many play, and the mode of the day on the party games", 60, () -> {
			String bw = HubNpcs.label(p(A).getUUID(), "bed_wars");
			String party = HubNpcs.label(p(A).getUUID(), "party");
			return bw.contains("Bed Wars") && bw.contains("playing") && party.contains("★") && !bw.contains("★")
					&& FeaturedMode.is(GameMode.SUMO);
		});
		s.run(tag + ": A right-clicks a bed in the lobby", () -> {
			net.minecraft.server.level.ServerLevel hub = (net.minecraft.server.level.ServerLevel) p(A).level();
			net.minecraft.core.BlockPos at = p(A).blockPosition().offset(2, 0, 0);
			VARS.put("bedAt", at);
			VARS.put("bedWas", hub.getBlockState(at));
			hub.setBlock(at, net.battlehub.util.Ids.state("minecraft:red_bed"), 3);
			p(A).gameMode.useItemOn(p(A), hub, net.minecraft.world.item.ItemStack.EMPTY, net.minecraft.world.InteractionHand.MAIN_HAND,
					new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at), net.minecraft.core.Direction.UP, at, false));
		});
		s.check(tag + ": the bed is still there and nothing exploded", () -> p(A).level()
				.getBlockState((net.minecraft.core.BlockPos) VARS.get("bedAt")).is(net.battlehub.util.Ids.block("minecraft:red_bed")) && !p(A).isSleeping());
		s.run(tag + ": the bed goes again", () -> p(A).level().setBlock((net.minecraft.core.BlockPos) VARS.get("bedAt"),
				(net.minecraft.world.level.block.state.BlockState) VARS.get("bedWas"), 3));
		s.run(tag + ": A clicks the practice character", () -> HubNpcs.click(p(A), HubNpcs.hitbox("practice")));
		s.check(tag + ": the practice menu opens", () -> p(A).containerMenu instanceof HubMenu);
		s.run(tag + ": chunks forced for the test are released", () -> {
			if (VARS.remove("npcForced") == null) return;
			net.minecraft.server.level.ServerLevel hub = (net.minecraft.server.level.ServerLevel) p(A).level();
			for (int cx = -2; cx <= 1; cx++) {
				for (int cz = -2; cz <= 1; cz++) hub.setChunkForced(cx, cz, false);
			}
		});
		s.run(tag + ": close it; no mode of the day for the other tests", () -> {
			p(A).closeContainer();
			@SuppressWarnings("unchecked")
			List<String> modes = (List<String>) VARS.get("featuredModes");
			ConfigManager.config().featured.modes = modes;
			ConfigManager.config().featured.enabled = false;
		});
		s.check(tag + ": bot join and leave messages are not shown in chat", () ->
				Bots.silenced(Component.translatable("multiplayer.player.joined", "Bot_Nova"))
						&& !Bots.silenced(Component.translatable("multiplayer.player.joined", "Steve"))
						&& Bots.isBot(Bots.uuid("Bot_Nova")) && !Bots.isBot(p(A).getUUID()));
	}

	/** A sword duel against a hard bot: it walks up and hits; A wins; alone with a bot: no stats, coins, XP or pass progress; nothing saved. */
	private static void practiceSword(MinecraftServer server, Script s) {
		String tag = "bots sword";
		SelfTest.reset(s, tag);
		s.run(tag + ": A starts a practice duel against a hard bot", () -> {
			SelfTest.join(server, A);
			PlayerProfile prof = PlayerStore.get(p(A).getUUID());
			VARS.put("botWins", prof.overall.wins);
			VARS.put("botPlayed", prof.overall.played);
			VARS.put("botCoins", prof.coins);
			VARS.put("botXp", prof.xp);
			VARS.put("botPassXp", prof.passXp);
			net.battlehub.bot.AiMemory.forget(server, p(A).getUUID());
			Bots.practice(p(A), GameMode.SWORD, net.battlehub.core.ai.Difficulty.EXPERT);
		});
		s.waitFor(tag + ": the duel runs with one bot", 600, () -> {
			Match m = matchOfA();
			return m != null && m.phase() == Match.Phase.RUNNING && botsIn(m).size() == 1;
		});
		s.waitFor(tag + ": the bot comes over to A", 600, () -> {
			Match m = matchOfA();
			if (m == null || botsIn(m).isEmpty()) return false;
			BotPlayer b = botsIn(m).get(0);
			double d = b.position().distanceTo(p(A).position());
			boolean ok = d < 3.0 || p(A).getHealth() < p(A).getMaxHealth();
			if (!ok && m.runSeconds() > 0 && m.runSeconds() % 5 == 0 && !Integer.valueOf(m.runSeconds()).equals(VARS.put("swLogged", m.runSeconds()))) {
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST sword bot after {}s: {} blocks from A, bot at {}, A at {}", m.runSeconds(),
						String.format(java.util.Locale.ROOT, "%.1f", d), b.position(), p(A).position());
			}
			return ok;
		});
		s.waitFor(tag + ": the bot hits A", 200, () -> p(A).getHealth() < p(A).getMaxHealth());
		s.run(tag + ": A starts strafing (sideways, changing direction now and then)", () -> {
			BotPlayer b = botsIn(matchOfA()).get(0);
			Vec3 a = p(A).position(), toBot = b.position().subtract(a).normalize();
			VARS.put("strafeCenter", a);
			VARS.put("strafeSide", new Vec3(-toBot.z, 0, toBot.x));
			VARS.put("strafeOffset", 0.0);
			VARS.put("strafeDir", 1.0);
			VARS.put("swings0", Bots.report(b).swings());
			VARS.put("landed0", Bots.report(b).landed());
		});
		s.waitFor(tag + ": against a strafing target the bot swings and misses now and then (no lock)", 1200, () -> {
			Match m = matchOfA();
			if (m == null || botsIn(m).isEmpty()) return false;
			BotPlayer b = botsIn(m).get(0);
			// A sprint-strafes 0.22 blocks a tick within a block and a half, turning every 8 to 24 ticks
			double off = (double) VARS.get("strafeOffset"), dir = (double) VARS.get("strafeDir");
			if (Math.abs(off) > 1.5 || java.util.concurrent.ThreadLocalRandom.current().nextInt(16) == 0) dir = Math.abs(off) > 1.5 ? -Math.signum(off) : -dir;
			off += dir * 0.22;
			VARS.put("strafeOffset", off);
			VARS.put("strafeDir", dir);
			Vec3 c = (Vec3) VARS.get("strafeCenter"), side = (Vec3) VARS.get("strafeSide");
			PartyTests.tp(p(A), c.x + side.x * off, c.y, c.z + side.z * off);
			p(A).setHealth(p(A).getMaxHealth());
			int swings = Bots.report(b).swings() - (int) VARS.get("swings0"), landed = Bots.report(b).landed() - (int) VARS.get("landed0");
			if (swings >= 15) {
				net.battlehub.BattleHub.LOGGER.info("SELFTEST aim against a strafing target: {} swings, {} landed ({}%)", swings, landed,
						Math.round(100.0 * landed / swings));
				return landed > 0 && landed < swings;
			}
			return false;
		});
		s.run(tag + ": A stands still again", () -> {
			Vec3 c = (Vec3) VARS.get("strafeCenter");
			PartyTests.tp(p(A), c.x, c.y, c.z);
		});
		s.check(tag + ": the bot's mind decides, keeps its reasons and switched tactics on the way", () -> {
			BotPlayer b = botsIn(matchOfA()).get(0);
			net.battlehub.core.ai.CombatMind.Report r = Bots.report(b);
			net.battlehub.BattleHub.LOGGER.info("SELFTEST bot mind: {}", String.join(" | ", r.lines()));
			return r.thinks() > 5 && !r.top().isEmpty() && r.difficulty() == net.battlehub.core.ai.Difficulty.EXPERT
					&& r.transitions().stream().anyMatch(t -> t.to() == net.battlehub.core.ai.Tactic.STRIKE
							|| t.to() == net.battlehub.core.ai.Tactic.SPACE || t.to() == net.battlehub.core.ai.Tactic.APPROACH)
					&& r.transitions().stream().allMatch(t -> !t.why().isEmpty());
		});
		s.run(tag + ": an admin makes the bot a rusher and logs its decisions", () -> {
			BotPlayer b = botsIn(matchOfA()).get(0);
			VARS.put("botName", b.getGameProfile().name());
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "battlehub bots profile " + b.getGameProfile().name() + " rusher");
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "battlehub bots debug " + b.getGameProfile().name());
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "battlehub bots inspect " + b.getGameProfile().name());
		});
		s.waitFor(tag + ": the bot fights as a rusher now, and inspect explains it", 100, () -> {
			BotPlayer b = Bots.byName((String) VARS.get("botName"));
			if (b == null) return false;
			java.util.List<String> lines = Bots.inspect(b);
			return Bots.report(b).archetype() == net.battlehub.core.ai.Archetype.RUSHER && Bots.report(b).thinks() > 0
					&& lines.size() >= 4 && lines.get(1).startsWith("rusher, expert");
		});
		s.run(tag + ": debug log off again; A watches the bot (particles only A sees)", () -> {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "battlehub bots debug " + VARS.get("botName"));
			BotPlayer b = Bots.byName((String) VARS.get("botName"));
			VARS.put("showThinks", Bots.report(b).thinks());
			Bots.toggleShow(p(A), b);
		});
		s.pause(30);
		s.check(tag + ": while watched the bot keeps thinking; watching stops again", () -> {
			BotPlayer b = Bots.byName((String) VARS.get("botName"));
			boolean watched = b != null && b.getUUID().equals(Bots.showing(p(A).getUUID())) && Bots.report(b).thinks() > (int) VARS.get("showThinks");
			if (b != null) Bots.toggleShow(p(A), b);
			return watched && Bots.showing(p(A).getUUID()) == null;
		});
		s.run(tag + ": A wins", () -> {
			BotPlayer bot = botsIn(matchOfA()).get(0);
			VARS.put("bot", bot.getUUID());
			bot.hurtServer(bot.level(), bot.damageSources().playerAttack(p(A)), 1000f);
		});
		s.waitFor(tag + ": match over", 200, () -> matchOfA() == null || matchOfA().phase() == Match.Phase.ENDING
				|| matchOfA().phase() == Match.Phase.CLOSED);
		s.check(tag + ": one real player with a bot: no stats, no coins, no XP, no pass progress", () -> {
			PlayerProfile prof = PlayerStore.get(p(A).getUUID());
			return prof.overall.wins == (int) VARS.get("botWins") && prof.overall.played == (int) VARS.get("botPlayed")
					&& prof.coins == (long) VARS.get("botCoins") && prof.xp == (long) VARS.get("botXp")
					&& prof.passXp == (long) VARS.get("botPassXp");
		});
		s.check(tag + ": A got a debrief from the bot: damage, its plan, what it saw, a tip", () -> {
			java.util.List<String> d = Bots.lastDebrief(p(A).getUUID());
			net.battlehub.BattleHub.LOGGER.info("SELFTEST debrief: {}", String.join(" | ", d));
			return d.size() >= 3 && d.get(0).contains((String) VARS.get("botName"));
		});
		s.check(tag + ": the bots remember the fight: the result, on disk, and /bots memory shows it", () -> {
			net.battlehub.core.ai.PlayerKnowledge k = net.battlehub.bot.AiMemory.get(server, p(A).getUUID());
			java.nio.file.Path f = net.battlehub.data.PlayerStore.worldDir(server).resolve("ai").resolve(p(A).getUUID() + ".json");
			java.util.List<Component> lines = Bots.memory(p(A));
			net.battlehub.BattleHub.LOGGER.info("SELFTEST memory: {} | {}", k.results, lines.stream().map(Component::getString).toList());
			net.battlehub.BattleHub.LOGGER.info("SELFTEST rating after the win: {} (rated {}), adaptive {}", k.rating, k.rated, k.adaptiveLevel);
			return k.results.contains("W:expert") && java.nio.file.Files.exists(f) && lines.size() >= 4 && k.rated > 0 && k.rating > 1050;
		});
		s.run(tag + ": A wipes it with /bots forget", () -> server.getCommands().performPrefixedCommand(p(A).createCommandSourceStack(), "bots forget"));
		s.check(tag + ": the bots forgot A (memory and file)", () -> net.battlehub.bot.AiMemory.get(server, p(A).getUUID()).results.isEmpty()
				&& !java.nio.file.Files.exists(net.battlehub.data.PlayerStore.worldDir(server).resolve("ai").resolve(p(A).getUUID() + ".json")));
		s.waitFor(tag + ": the bot leaves after the match", 400, () -> Bots.online() == 0);
		s.check(tag + ": nothing saved for the bot", () -> {
			UUID bot = (UUID) VARS.get("bot");
			boolean noFile = !java.nio.file.Files.exists(server.getWorldPath(LevelResource.ROOT).resolve("playerdata").resolve(bot + ".dat"));
			boolean noProfile = PlayerStore.all().stream().noneMatch(pr -> pr.uuid.equals(bot.toString()));
			return noFile && noProfile;
		});
		s.waitFor(tag + ": arena free", 600, () -> ArenaSlots.busy() == 0);
	}

	/** A bow duel: the bot keeps its distance and shoots. */
	private static void practiceBow(MinecraftServer server, Script s) {
		String tag = "bots bow";
		SelfTest.reset(s, tag);
		s.run(tag + ": A starts a bow duel against a normal bot", () -> {
			SelfTest.join(server, A);
			Bots.practice(p(A), GameMode.BOW, net.battlehub.core.ai.Difficulty.INTERMEDIATE);
		});
		s.waitFor(tag + ": running", 600, () -> matchOfA() != null && matchOfA().phase() == Match.Phase.RUNNING);
		s.run(tag + ": watch", () -> VARS.put("bowPolls", 0));
		s.waitFor(tag + ": the bot's arrows hit A", 600, () -> {
			boolean hit = p(A).getHealth() < p(A).getMaxHealth();
			int polls = (int) VARS.get("bowPolls") + 1;
			VARS.put("bowPolls", polls);
			if (!hit && polls % 100 == 0 && matchOfA() != null && !botsIn(matchOfA()).isEmpty()) {
				BotPlayer b = botsIn(matchOfA()).get(0);
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST bow bot after {} ticks: {} blocks from A, sees A {}, drawing {}, holds {}, bot at {}",
						polls, String.format(java.util.Locale.ROOT, "%.1f", b.distanceTo(p(A))), b.hasLineOfSight(p(A)), b.isUsingItem(),
						b.getMainHandItem().getItem(), b.position());
			}
			return hit;
		});
		s.waitFor(tag + ": in a bow fight the bow is its weapon (it shoots again)", 400, () -> {
			Match m = matchOfA();
			if (m == null || botsIn(m).isEmpty()) return false;
			BotPlayer b = botsIn(m).get(0);
			// A stays in the fight: the test is about the bot's choice of weapon, not about who wins
			p(A).setHealth(p(A).getMaxHealth());
			if (Bots.shots(b) < 2) return false;
			net.battlehub.BattleHub.LOGGER.info("SELFTEST bow fight: {} arrows | {}", Bots.shots(b), String.join(" | ", Bots.inspect(b)));
			return true;
		});
		endAndClean(s, tag);
		maceFight(server, s);
		teamPracticeKit(server, s);
		knockback(server, s);
	}

	/** A hits a bot: it is knocked back as a player would be (the knockback used to reach only a client it does not have). */
	private static void knockback(MinecraftServer server, Script s) {
		String tag = "bots knockback";
		SelfTest.reset(s, tag);
		s.run(tag + ": A starts a sword duel against a beginner bot", () -> {
			SelfTest.join(server, A);
			Bots.practice(p(A), GameMode.SWORD, net.battlehub.core.ai.Difficulty.BEGINNER);
		});
		s.waitFor(tag + ": running", 600, () -> matchOfA() != null && matchOfA().phase() == Match.Phase.RUNNING && botsIn(matchOfA()).size() == 1);
		s.run(tag + ": A faces the bot two blocks away and hits it", () -> {
			BotPlayer b = botsIn(matchOfA()).get(0);
			ServerPlayer a = p(A);
			// a direction with six blocks of room (arenas have cover), so the bot flies freely
			net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) a.level();
			float free = a.getYRot();
			for (int k = 0; k < 16; k++) {
				double r = Math.toRadians(k * 22.5);
				boolean clear = true;
				for (double t = 1; t <= 6 && clear; t += 0.5) {
					net.minecraft.core.BlockPos q = net.minecraft.core.BlockPos.containing(a.getX() - Math.sin(r) * t, a.getY() + 0.1, a.getZ() + Math.cos(r) * t);
					clear = level.getBlockState(q).getCollisionShape(level, q).isEmpty() && level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty()
							&& !level.getBlockState(q.below()).getCollisionShape(level, q.below()).isEmpty();
				}
				if (clear) {
					free = k * 22.5f;
					break;
				}
			}
			a.setYRot(free);
			a.setYHeadRot(free);
			double yaw = Math.toRadians(free);
			double fx = -Math.sin(yaw), fz = Math.cos(yaw);
			PartyTests.tp(b, a.getX() + fx * 2, a.getY(), a.getZ() + fz * 2);
			b.setDeltaMovement(Vec3.ZERO);
			a.attack(b);
			VARS.put("kbFrom", b.position());
			VARS.put("kbDir", new Vec3(fx, 0, fz));
		});
		s.pause(4);
		s.check(tag + ": the bot was knocked away from A", () -> {
			BotPlayer b = botsIn(matchOfA()).get(0);
			Vec3 from = (Vec3) VARS.get("kbFrom"), dir = (Vec3) VARS.get("kbDir");
			double moved = b.position().subtract(from).dot(dir);
			double push = b.lastKnock().dot(dir);
			net.battlehub.BattleHub.LOGGER.info("SELFTEST knockback: speed away from A {} from the hit, {} blocks away 4 ticks later",
					String.format(java.util.Locale.ROOT, "%.2f", push), String.format(java.util.Locale.ROOT, "%.2f", moved));
			return push > 0.25 && moved > 0.4;
		});
		endAndClean(s, tag);
	}

	/** A mace duel: the bot fights with the mace and now and then flies up on a wind charge to smash down. */
	private static void maceFight(MinecraftServer server, Script s) {
		String tag = "bots mace";
		SelfTest.reset(s, tag);
		s.run(tag + ": A starts a mace duel against an expert bot", () -> {
			SelfTest.join(server, A);
			Bots.practice(p(A), GameMode.MACE, net.battlehub.core.ai.Difficulty.EXPERT);
		});
		s.waitFor(tag + ": running", 600, () -> matchOfA() != null && matchOfA().phase() == Match.Phase.RUNNING && botsIn(matchOfA()).size() == 1);
		s.run(tag + ": watch", () -> VARS.put("macePolls", 0));
		s.waitFor(tag + ": it throws a wind charge at its feet", 1200, () -> {
			Match m = matchOfA();
			if (m == null || botsIn(m).isEmpty()) return false;
			BotPlayer b = botsIn(m).get(0);
			p(A).setHealth(p(A).getMaxHealth());
			int polls = (int) VARS.get("macePolls") + 1;
			VARS.put("macePolls", polls);
			if (polls % 80 == 0 && Bots.launches(b) == 0) {
				// A steps back now and then, as a player does: a wind charge is for a few blocks out, not point-blank
				backOff(b, 4.5);
			}
			if (Bots.launches(b) >= 1) {
				VARS.put("launchY", b.getY());
				VARS.put("launchTop", b.getY());
				net.battlehub.BattleHub.LOGGER.info("SELFTEST mace bot launched {} time(s) | {}", Bots.launches(b), String.join(" | ", Bots.inspect(b)));
				return true;
			}
			logBot("mace bot", m, b, "maceLogged");
			return false;
		});
		s.waitFor(tag + ": and goes up (more than a jump)", 40, () -> {
			Match m = matchOfA();
			if (m == null || botsIn(m).isEmpty()) return false;
			double y = botsIn(m).get(0).getY(), top = Math.max((double) VARS.get("launchTop"), y);
			VARS.put("launchTop", top);
			boolean up = top > (double) VARS.get("launchY") + 1.4;
			if (up) net.battlehub.BattleHub.LOGGER.info("SELFTEST mace bot flew {} blocks up", String.format(java.util.Locale.ROOT, "%.1f", top - (double) VARS.get("launchY")));
			return up;
		});
		s.waitFor(tag + ": and fights with the mace in hand", 200, () -> {
			Match m = matchOfA();
			if (m == null || botsIn(m).isEmpty()) return false;
			p(A).setHealth(p(A).getMaxHealth());
			return botsIn(m).get(0).getMainHandItem().is(net.minecraft.world.item.Items.MACE);
		});
		endAndClean(s, tag);
	}

	/** A steps away from the bot to about {@code distance} blocks, onto floor (the other way when that is the void or a wall). */
	private static void backOff(BotPlayer b, double distance) {
		ServerPlayer a = p(A);
		double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
		double len = Math.max(0.01, Math.hypot(dx, dz));
		for (int side = 1; side >= -1; side -= 2) {
			double x = b.getX() + side * dx / len * distance, z = b.getZ() + side * dz / len * distance;
			net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(x, a.getY(), z);
			if (!a.level().getBlockState(feet.below()).isAir() && a.level().getBlockState(feet).isAir() && a.level().getBlockState(feet.above()).isAir()) {
				PartyTests.tp(a, x, a.getY(), z);
				return;
			}
		}
	}

	/** 1v2 practice with the mace kit: everybody gets the mace kit (it used to be the sword kit, whatever was chosen). */
	private static void teamPracticeKit(MinecraftServer server, Script s) {
		String tag = "bots team kit";
		SelfTest.reset(s, tag);
		s.run(tag + ": A practises 1v2 with the mace kit", () -> {
			SelfTest.join(server, A);
			Bots.practiceTeam(p(A), GameMode.ONE_V_TWO, GameMode.MACE, net.battlehub.core.ai.Difficulty.CASUAL);
		});
		s.waitFor(tag + ": the fight runs with two bots", 600, () -> matchOfA() != null && botsIn(matchOfA()).size() == 2
				&& matchOfA().phase() != Match.Phase.CLOSED);
		s.waitFor(tag + ": A and both bots carry the mace kit", 200, () -> {
			Match m = matchOfA();
			if (m == null) return false;
			boolean ok = p(A).getInventory().countItem(net.minecraft.world.item.Items.MACE) == 1;
			for (BotPlayer b : botsIn(m)) ok &= b.getInventory().countItem(net.minecraft.world.item.Items.MACE) == 1;
			return ok;
		});
		endAndClean(s, tag);
	}

	/** Alone in a casual queue: a bot joins after a while, unless the player said no (/bots off, the hotbar switch). Ranked: never. */
	private static void queueFill(MinecraftServer server, Script s) {
		String tag = "bots queue";
		SelfTest.reset(s, tag);
		s.run(tag + ": filling on, after one second; A queues for ranked sword", () -> {
			cfg().fillQueues = true;
			cfg().fillAfterSeconds = 1;
			SelfTest.join(server, A);
			Queues.join(p(A), GameMode.SWORD, true, DuelFormat.BO1);
		});
		s.pause(80);
		s.check(tag + ": no bot in ranked, and no bots switch on the hotbar", () -> matchOfA() == null && Bots.online() == 0
				&& !Icon.tagOf(p(A).getInventory().getItem(HubItems.BOTS_SLOT)).startsWith("bots_"));
		s.run(tag + ": A types /bots off and queues for casual axe", () -> {
			Queues.leaveAll(p(A));
			p(A).level().getServer().getCommands().performPrefixedCommand(p(A).createCommandSourceStack(), "bots off");
			Queues.join(p(A), GameMode.AXE, false, DuelFormat.BO1);
		});
		s.check(tag + ": remembered, and the hotbar switch shows bots off", () -> PlayerStore.get(p(A)).noBots
				&& HubItems.BOTS_OFF.equals(Icon.tagOf(p(A).getInventory().getItem(HubItems.BOTS_SLOT))));
		s.pause(80);
		s.check(tag + ": with bots off nobody fills in; A still waits", () -> matchOfA() == null && Bots.online() == 0
				&& Sessions.state(p(A).getUUID()) == Sessions.State.QUEUED);
		s.run(tag + ": A clicks the switch (bots on again)", () -> Bots.choose(p(A), true));
		s.check(tag + ": the switch shows bots on", () -> !PlayerStore.get(p(A)).noBots
				&& HubItems.BOTS_ON.equals(Icon.tagOf(p(A).getInventory().getItem(HubItems.BOTS_SLOT))));
		s.waitFor(tag + ": a duel against a bot starts", 400, () -> botsIn(matchOfA()).size() == 1 && matchOfA().mode == GameMode.AXE);
		endAndClean(s, tag);
	}

	/** One player in the Sumo lobby: bots fill it to four and the game starts. */
	private static void partyFill(MinecraftServer server, Script s) {
		String tag = "bots party";
		SelfTest.reset(s, tag);
		s.run(tag + ": A joins Sumo alone (short countdown for the test)", () -> {
			VARS.put("partyCountdown", ConfigManager.config().timings.partyCountdownSeconds);
			VARS.put("partyFull", ConfigManager.config().timings.partyFullCountdownSeconds);
			ConfigManager.config().timings.partyCountdownSeconds = 4;
			ConfigManager.config().timings.partyFullCountdownSeconds = 2;
			cfg().fillQueues = true;
			cfg().fillAfterSeconds = 1;
			cfg().partyFillTo = 4;
			SelfTest.join(server, A);
			Parties.join(p(A), GameMode.SUMO);
		});
		s.waitFor(tag + ": three bots join and the match starts", 600, () -> {
			Match m = matchOfA();
			return m != null && m.mode == GameMode.SUMO && botsIn(m).size() == 3;
		});
		s.waitFor(tag + ": the bots fight (someone gets hit)", 600, () -> {
			Match m = matchOfA();
			if (m == null || m.phase() != Match.Phase.RUNNING) return false;
			for (BotPlayer b : botsIn(m)) {
				if (b.getLastHurtByMob() != null) return true;
			}
			return p(A).getLastHurtByMob() != null;
		});
		s.waitFor(tag + ": the metrics count the bots' thinking", 100, () -> Bots.thinksPerSecond() > 0);
		s.check(tag + ": /battlehub bots reports bots, thinking and tick time", () -> {
			List<Component> lines = Bots.metrics();
			net.battlehub.BattleHub.LOGGER.info("SELFTEST metrics: {}", lines.stream().map(Component::getString).toList());
			return lines.size() == 3 && lines.get(0).getString().contains("3");
		});
		endAndClean(s, tag);
		s.run(tag + ": filling off, countdown as before", () -> {
			cfg().fillQueues = false;
			ConfigManager.config().timings.partyCountdownSeconds = (int) VARS.get("partyCountdown");
			ConfigManager.config().timings.partyFullCountdownSeconds = (int) VARS.get("partyFull");
		});
	}

	/** Spleef with bots: they dig the snow (every dug block gives a snowball), never the block they stand on. */
	private static void spleefBots(MinecraftServer server, Script s) {
		String tag = "bots spleef";
		SelfTest.reset(s, tag);
		s.run(tag + ": A joins Spleef alone; bots fill in", () -> {
			VARS.put("partyCountdown", ConfigManager.config().timings.partyCountdownSeconds);
			VARS.put("partyFull", ConfigManager.config().timings.partyFullCountdownSeconds);
			ConfigManager.config().timings.partyCountdownSeconds = 4;
			ConfigManager.config().timings.partyFullCountdownSeconds = 2;
			cfg().fillQueues = true;
			cfg().fillAfterSeconds = 1;
			cfg().partyFillTo = 3;
			SelfTest.join(server, A);
			Parties.join(p(A), GameMode.SPLEEF);
		});
		s.waitFor(tag + ": the match runs with bots", 600, () -> {
			Match m = matchOfA();
			return m != null && m.mode == GameMode.SPLEEF && m.phase() == Match.Phase.RUNNING && !botsIn(m).isEmpty();
		});
		s.waitFor(tag + ": a bot digs snow (it got snowballs for it)", 600, () -> {
			Match m = matchOfA();
			if (m == null) return false;
			for (BotPlayer b : botsIn(m)) {
				if (b.getInventory().countItem(net.minecraft.world.item.Items.SNOWBALL) > 0) return true;
			}
			return false;
		});
		s.check(tag + ": the bots still stand (none dug its own floor away)", () -> {
			Match m = matchOfA();
			if (m == null) return true;
			for (BotPlayer b : botsIn(m)) {
				if (m.inPlay(b.getUUID()) && b.getDeltaMovement().y < -0.6) return false;
			}
			return true;
		});
		endAndClean(s, tag);
		s.run(tag + ": filling off, countdown as before", () -> {
			cfg().fillQueues = false;
			cfg().partyFillTo = 4;
			ConfigManager.config().timings.partyCountdownSeconds = (int) VARS.get("partyCountdown");
			ConfigManager.config().timings.partyFullCountdownSeconds = (int) VARS.get("partyFull");
		});
	}

	/** A bot on an easy parkour course gets past the first platforms. */
	private static void parkour(MinecraftServer server, Script s) {
		String tag = "bots parkour";
		SelfTest.reset(s, tag);
		s.run(tag + ": A joins an easy Parkour Race; one (hard) bot fills in", () -> {
			cfg().fillQueues = true;
			cfg().fillAfterSeconds = 1;
			cfg().partyFillTo = 2;
			VARS.put("fillLevel", cfg().fillLevel);
			// The hard bot rarely misses on purpose, so the course decides less about how long this takes
			cfg().fillLevel = "hard";
			SelfTest.join(server, A);
			Parties.join(p(A), GameMode.PARKOUR, "easy");
		});
		s.waitFor(tag + ": the race runs with a bot", 900, () -> {
			Match m = matchOfA();
			return m instanceof ParkourMatch && m.phase() == Match.Phase.RUNNING && botsIn(m).size() == 1;
		});
		s.run(tag + ": no more bots", () -> {
			cfg().fillQueues = false;
			cfg().fillLevel = (String) VARS.get("fillLevel");
		});
		s.waitFor(tag + ": the bot jumps from platform to platform", 1800, () -> {
			Match m = matchOfA();
			if (!(m instanceof ParkourMatch pm) || botsIn(m).isEmpty()) return false;
			BotPlayer b = botsIn(m).get(0);
			boolean ok = pm.platformIndex(b) >= 3 || pm.lastPlatform(b.getUUID()) >= 3;
			if (!ok && m.runSeconds() > 0 && m.runSeconds() % 15 == 0 && !Integer.valueOf(m.runSeconds()).equals(VARS.put("pkLogged", m.runSeconds()))) {
				StringBuilder first = new StringBuilder();
				for (int i = 0; i < Math.min(5, pm.course().size()); i++) {
					net.battlehub.core.ParkourCourse.Platform pl = pm.course().get(i);
					first.append(i == 0 ? "" : ", ").append(i).append(' ').append(pl.style()).append(" at ").append(pm.platformTop(i));
				}
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST parkour bot after {}s: on platform {}, best {}, at {} | course {} | {}", m.runSeconds(),
						pm.platformIndex(b), pm.lastPlatform(b.getUUID()), b.position(), first, String.join(" | ", Bots.inspect(b)));
			}
			return ok;
		});
		endAndClean(s, tag);
	}

	/** Logs what a bot is doing every 15 seconds while a test waits (to see why, when it fails). */
	private static void logBot(String what, Match m, BotPlayer b, String key) {
		if (m.runSeconds() > 0 && m.runSeconds() % 15 == 0 && !Integer.valueOf(m.runSeconds()).equals(VARS.put(key, m.runSeconds()))) {
			net.battlehub.BattleHub.LOGGER.warn("SELFTEST {} after {}s: at {} | {}", what, m.runSeconds(), b.position(), String.join(" | ", Bots.inspect(b)));
		}
	}

	/** King of the Hill with bots: they go up the hill and stay on it. */
	private static void kothBots(MinecraftServer server, Script s) {
		String tag = "bots koth";
		SelfTest.reset(s, tag);
		s.run(tag + ": A joins KOTH alone; bots fill in", () -> {
			VARS.put("partyCountdown", ConfigManager.config().timings.partyCountdownSeconds);
			VARS.put("partyFull", ConfigManager.config().timings.partyFullCountdownSeconds);
			ConfigManager.config().timings.partyCountdownSeconds = 4;
			ConfigManager.config().timings.partyFullCountdownSeconds = 2;
			cfg().fillQueues = true;
			cfg().fillAfterSeconds = 1;
			cfg().partyFillTo = 3;
			SelfTest.join(server, A);
			Parties.join(p(A), GameMode.KOTH);
		});
		s.waitFor(tag + ": the match runs with bots", 600, () -> {
			Match m = matchOfA();
			return m instanceof net.battlehub.games.KothMatch && m.phase() == Match.Phase.RUNNING && !botsIn(m).isEmpty();
		});
		s.run(tag + ": no more bots", () -> cfg().fillQueues = false);
		s.waitFor(tag + ": a bot stands on the hill, holding it", 1200, () -> {
			if (!(matchOfA() instanceof net.battlehub.games.KothMatch km)) return false;
			for (BotPlayer b : botsIn(km)) {
				if (km.inZone(b)) {
					net.battlehub.BattleHub.LOGGER.info("SELFTEST koth bot: {}", String.join(" | ", Bots.inspect(b)));
					return Bots.inspect(b).stream().anyMatch(l -> l.startsWith("goal: hold the hill"));
				}
				logBot("koth bot", km, b, "kothLogged");
			}
			return false;
		});
		endAndClean(s, tag);
		s.run(tag + ": countdown as before", () -> {
			cfg().partyFillTo = 4;
			ConfigManager.config().timings.partyCountdownSeconds = (int) VARS.get("partyCountdown");
			ConfigManager.config().timings.partyFullCountdownSeconds = (int) VARS.get("partyFull");
		});
	}

	/** The Bridge against a bot, with the path between the islands gone: the bot builds its own way across. */
	private static void bridgeBot(MinecraftServer server, Script s) {
		String tag = "bots bridge";
		SelfTest.reset(s, tag);
		s.run(tag + ": A queues for casual Bridge; a (master) bot fills in", () -> {
			cfg().fillQueues = true;
			cfg().fillAfterSeconds = 1;
			VARS.put("fillLevel", cfg().fillLevel);
			cfg().fillLevel = "master";
			SelfTest.join(server, A);
			Queues.join(p(A), GameMode.BRIDGE, false, DuelFormat.BO1);
		});
		s.waitFor(tag + ": the match runs against a bot", 600, () -> {
			Match m = matchOfA();
			return m instanceof net.battlehub.games.duel.BridgeMatch && m.phase() == Match.Phase.RUNNING && botsIn(m).size() == 1;
		});
		s.run(tag + ": no more bots for now", () -> VARS.put("pathPlaced", -1));
		s.pause(200);
		s.check(tag + ": while the path is there the bot walks it and builds nothing", () -> {
			Match m = matchOfA();
			BotPlayer b = botsIn(m).get(0);
			net.battlehub.BattleHub.LOGGER.info("SELFTEST bridge bot with the path: placed {} | {}", Bots.placed(b), String.join(" | ", Bots.inspect(b)));
			return Bots.placed(b) == 0;
		});
		s.run(tag + ": no more bots; the bot is sent home and the path between the islands is taken away", () -> {
			cfg().fillQueues = false;
			cfg().fillLevel = (String) VARS.get("fillLevel");
			Match m = matchOfA();
			BotPlayer b = botsIn(m).get(0);
			// it may have walked across already: from its own island it has to build to get anywhere
			net.battlehub.arena.Arena.Point home = m.home(b.getUUID());
			if (home != null) PartyTests.tp(b, home.pos().x, home.pos().y, home.pos().z);
			net.minecraft.core.BlockPos o = m.arena().origin();
			int reach = net.battlehub.arena.layout.NewLayouts.BRIDGE_ISLAND - 6;
			for (int x = -reach; x <= reach; x++) {
				for (int z = -3; z <= 3; z++) {
					m.arena().level().setBlock(o.offset(x, 0, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
				}
			}
		});
		s.waitFor(tag + ": the bot sneaks to the edge and builds across with its clay", 1800, () -> {
			Match m = matchOfA();
			if (m == null || botsIn(m).isEmpty()) return false;
			BotPlayer b = botsIn(m).get(0);
			if (Bots.placed(b) >= 3) {
				net.battlehub.BattleHub.LOGGER.info("SELFTEST bridge bot: {}", String.join(" | ", Bots.inspect(b)));
				return true;
			}
			logBot("bridge bot", m, b, "bridgeLogged");
			return false;
		});
		s.run(tag + ": the bot is put on its island a block and a half from the north edge", () -> {
			Match m = matchOfA();
			BotPlayer b = botsIn(m).get(0);
			net.minecraft.core.BlockPos o = m.arena().origin();
			double x = b.getX() > o.getX() ? o.getX() + net.battlehub.arena.layout.NewLayouts.BRIDGE_ISLAND + 0.5
					: o.getX() - net.battlehub.arena.layout.NewLayouts.BRIDGE_ISLAND + 0.5;
			PartyTests.tp(b, x, o.getY() + 1, o.getZ() - 3.6);
			VARS.put("edgeY", (double) o.getY() + 1);
		});
		s.pause(4);
		s.run(tag + ": a hit knocks it towards the void (0.4 sideways, 0.42 up)", () -> {
			BotPlayer b = botsIn(matchOfA()).get(0);
			b.setDeltaMovement(0, 0.42, -0.4);
			VARS.put("recovered", false);
		});
		s.waitFor(tag + ": in the air it holds the keys back towards the island", 8, () -> {
			BotPlayer b = botsIn(matchOfA()).get(0);
			double yaw = Math.toRadians(b.getYRot());
			double kx = -Math.sin(yaw) * b.zza + Math.cos(yaw) * b.xxa, kz = Math.cos(yaw) * b.zza + Math.sin(yaw) * b.xxa;
			double len = Math.hypot(kx, kz);
			return !b.onGround() && len > 0.5 && kz / len > 0.5;
		});
		s.pause(30);
		s.check(tag + ": and it lands back on the island (logged)", () -> {
			BotPlayer b = botsIn(matchOfA()).get(0);
			net.battlehub.BattleHub.LOGGER.info("SELFTEST knocked bot landed at {} (island floor at y {})", b.position(), VARS.get("edgeY"));
			return true;
		});
		endAndClean(s, tag);
	}

	/** Bed Wars against a bot: it fetches iron, buys wool at its shop and builds towards the other island. */
	private static void bedWarsBot(MinecraftServer server, Script s) {
		String tag = "bots bed wars";
		SelfTest.reset(s, tag);
		s.run(tag + ": A joins solo Bed Wars; one bot fills in", () -> {
			VARS.put("partyCountdown", ConfigManager.config().timings.partyCountdownSeconds);
			VARS.put("partyFull", ConfigManager.config().timings.partyFullCountdownSeconds);
			ConfigManager.config().timings.partyCountdownSeconds = 4;
			ConfigManager.config().timings.partyFullCountdownSeconds = 2;
			cfg().fillQueues = true;
			cfg().fillAfterSeconds = 1;
			cfg().partyFillTo = 2;
			SelfTest.join(server, A);
			Parties.join(p(A), GameMode.BED_WARS, "format:solo");
		});
		s.waitFor(tag + ": the match runs with a bot", 900, () -> {
			Match m = matchOfA();
			return m instanceof net.battlehub.games.bedwars.BedWarsMatch && m.phase() == Match.Phase.RUNNING && botsIn(m).size() == 1;
		});
		s.run(tag + ": no more bots", () -> cfg().fillQueues = false);
		s.waitFor(tag + ": the bot bought wool at its shop with iron from its generator", 2400, () -> {
			Match m = matchOfA();
			if (m == null || botsIn(m).isEmpty()) return false;
			BotPlayer b = botsIn(m).get(0);
			for (int i = 0; i < b.getInventory().getContainerSize(); i++) {
				if (b.getInventory().getItem(i).is(net.minecraft.tags.ItemTags.WOOL)) return true;
			}
			logBot("bed wars bot", m, b, "bwLogged");
			return false;
		});
		s.waitFor(tag + ": it builds towards the other island", 1800, () -> {
			Match m = matchOfA();
			if (m == null || botsIn(m).isEmpty()) return false;
			BotPlayer b = botsIn(m).get(0);
			if (Bots.placed(b) >= 3) {
				net.battlehub.BattleHub.LOGGER.info("SELFTEST bed wars bot: {}", String.join(" | ", Bots.inspect(b)));
				return true;
			}
			logBot("bed wars bot", m, b, "bwLogged2");
			return false;
		});
		s.run(tag + ": the bot is put next to A's bed, with A standing right there", () -> {
			net.battlehub.games.bedwars.BedWarsMatch bw = (net.battlehub.games.bedwars.BedWarsMatch) matchOfA();
			BotPlayer b = botsIn(bw).get(0);
			net.minecraft.core.BlockPos bed = bw.bed(bw.teamOf(p(A).getUUID()));
			VARS.put("bwBed", bed);
			// on the floor two blocks from the bed, A one block further
			for (int[] d : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
				net.minecraft.core.BlockPos at = bed.offset(d[0], 0, d[1]);
				if (bw.arena().level().getBlockState(at).isAir() && !bw.arena().level().getBlockState(at.below()).isAir()) {
					PartyTests.tp(b, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
					PartyTests.tp(p(A), at.getX() + 0.5 + d[0] / 2.0, at.getY(), at.getZ() + 0.5 + d[1] / 2.0);
					break;
				}
			}
		});
		s.waitFor(tag + ": it breaks the bed rather than fight A", 200, () -> {
			Match m = matchOfA();
			if (!(m instanceof net.battlehub.games.bedwars.BedWarsMatch bw)) return false;
			p(A).setHealth(p(A).getMaxHealth());
			net.minecraft.core.BlockPos bed = (net.minecraft.core.BlockPos) VARS.get("bwBed");
			boolean gone = !(bw.arena().level().getBlockState(bed).getBlock() instanceof net.minecraft.world.level.block.BedBlock);
			if (gone) net.battlehub.BattleHub.LOGGER.info("SELFTEST bed wars bot broke A's bed | {}", String.join(" | ", Bots.inspect(botsIn(m).get(0))));
			return gone;
		});
		endAndClean(s, tag);
		s.run(tag + ": countdown as before", () -> {
			cfg().partyFillTo = 4;
			ConfigManager.config().timings.partyCountdownSeconds = (int) VARS.get("partyCountdown");
			ConfigManager.config().timings.partyFullCountdownSeconds = (int) VARS.get("partyFull");
		});
		skywarsBot(server, s);
		hungerGamesBot(server, s);
	}

	/**
	 * Hunger Games with three bots in the city: they empty the cornucopia next to their spawns, then walk into the
	 * town (no doors in the way) to the chests of the houses and landmarks.
	 */
	private static void hungerGamesBot(MinecraftServer server, Script s) {
		String tag = "bots hunger games";
		SelfTest.reset(s, tag);
		s.run(tag + ": A joins the Hunger Games; three bots fill in", () -> {
			VARS.put("partyCountdown", ConfigManager.config().timings.partyCountdownSeconds);
			VARS.put("partyFull", ConfigManager.config().timings.partyFullCountdownSeconds);
			ConfigManager.config().timings.partyCountdownSeconds = 4;
			ConfigManager.config().timings.partyFullCountdownSeconds = 2;
			cfg().fillQueues = true;
			cfg().fillAfterSeconds = 1;
			cfg().partyFillTo = 4;
			VARS.put("swFalls", new java.util.HashMap<UUID, Integer>());
			VARS.put("swHurt", new java.util.HashMap<UUID, Integer>());
			VARS.put("swSelfFalls", 0);
			// the border stays out of the way (other tests make it close in fast)
			java.util.Map<String, Double> hg = ConfigManager.config().game(GameMode.HUNGER_GAMES).settings;
			VARS.put("hgSettings", new java.util.LinkedHashMap<>(hg));
			hg.put("borderDelaySeconds", 600.0);
			// a long peace: nobody is an enemy, so the bots only loot (after it they would fight A, who stays)
			hg.put("graceSeconds", 600.0);
			SelfTest.join(server, A);
			Parties.join(p(A), GameMode.HUNGER_GAMES, "");
		});
		s.waitFor(tag + ": the match runs with three bots", 900, () -> {
			Match m = matchOfA();
			return m instanceof net.battlehub.games.HungerGamesMatch && m.phase() == Match.Phase.RUNNING && botsIn(m).size() == 3;
		});
		s.run(tag + ": no more bots; A waits at the edge of the plaza", () -> {
			cfg().fillQueues = false;
			Match m = matchOfA();
			PartyTests.tp(p(A), m.arena().origin().getX() + 0.5, m.arena().origin().getY() + 1, m.arena().origin().getZ() + 30.5);
		});
		s.waitFor(tag + ": the bots empty the cornucopia", 1200, () -> {
			Match m = matchOfA();
			if (!(m instanceof net.battlehub.games.HungerGamesMatch hg) || botsIn(m).isEmpty()) return false;
			watchFalls(server, m);
			if (hg.botLoots("cornucopia") >= 3) {
				BotPlayer b = botsIn(m).get(0);
				net.battlehub.BattleHub.LOGGER.info("SELFTEST hunger games bots looted {} cornucopia chests, one wears {} | {}", hg.botLoots("cornucopia"),
						b.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST), String.join(" | ", Bots.inspect(b)));
				return true;
			}
			logBot("hunger games bot", m, botsIn(m).get(0), "hgLogged");
			return false;
		});
		s.check(tag + ": during the peace nobody is anyone's enemy", () -> !matchOfA().isEnemy(p(A).getUUID(), botsIn(matchOfA()).get(0).getUUID()));
		s.waitFor(tag + ": and walk into the town to the chests of the houses and landmarks", 3600, () -> {
			Match m = matchOfA();
			if (!(m instanceof net.battlehub.games.HungerGamesMatch hg) || botsIn(m).isEmpty()) return false;
			watchFalls(server, m);
			int town = hg.botLoots("chest") + hg.botLoots("landmark_chest");
			if (town >= 1) {
				BotPlayer b = botsIn(m).get(0);
				net.battlehub.BattleHub.LOGGER.info("SELFTEST hunger games bots looted {} chests in the town | {}", town, String.join(" | ", Bots.inspect(b)));
				return true;
			}
			logBot("hunger games bot", m, botsIn(m).get(0), "hgLogged2");
			return false;
		});
		endAndClean(s, tag);
		s.run(tag + ": countdown and settings as before", () -> {
			cfg().partyFillTo = 4;
			ConfigManager.config().timings.partyCountdownSeconds = (int) VARS.get("partyCountdown");
			ConfigManager.config().timings.partyFullCountdownSeconds = (int) VARS.get("partyFull");
			java.util.Map<String, Double> hg = ConfigManager.config().game(GameMode.HUNGER_GAMES).settings;
			hg.clear();
			@SuppressWarnings("unchecked")
			java.util.Map<String, Double> saved = (java.util.Map<String, Double>) VARS.get("hgSettings");
			hg.putAll(saved);
		});
	}

	/**
	 * SkyWars with three bots on a big map: they empty the chests on their islands and bridge off them to the next
	 * chests, and none walks or jumps off the map by itself (a fall within two seconds of a hit is a fight).
	 */
	private static void skywarsBot(MinecraftServer server, Script s) {
		String tag = "bots skywars";
		SelfTest.reset(s, tag);
		s.run(tag + ": A joins SkyWars; three bots fill in", () -> {
			VARS.put("partyCountdown", ConfigManager.config().timings.partyCountdownSeconds);
			VARS.put("partyFull", ConfigManager.config().timings.partyFullCountdownSeconds);
			ConfigManager.config().timings.partyCountdownSeconds = 4;
			ConfigManager.config().timings.partyFullCountdownSeconds = 2;
			cfg().fillQueues = true;
			cfg().fillAfterSeconds = 1;
			cfg().partyFillTo = 4;
			VARS.put("swFalls", new java.util.HashMap<UUID, Integer>());
			VARS.put("swHurt", new java.util.HashMap<UUID, Integer>());
			VARS.put("swSelfFalls", 0);
			SelfTest.join(server, A);
			Parties.join(p(A), GameMode.SKYWARS, "");
		});
		s.waitFor(tag + ": the match runs with three bots", 900, () -> {
			Match m = matchOfA();
			return m instanceof net.battlehub.games.SkyWarsMatch && m.phase() == Match.Phase.RUNNING && botsIn(m).size() == 3;
		});
		s.run(tag + ": no more bots", () -> {
			cfg().fillQueues = false;
			net.battlehub.games.SkyWarsMatch sw = (net.battlehub.games.SkyWarsMatch) matchOfA();
			net.battlehub.BattleHub.LOGGER.info("SELFTEST skywars map: {} times as wide as in 1.29, {} blocks from the middle, middle island radius {}",
					String.format(java.util.Locale.ROOT, "%.2f", sw.sizeFactor()), Math.round(sw.plan().outer), Math.round(sw.plan().center.r()));
		});
		s.waitFor(tag + ": the bots empty the chests on their islands", 1200, () -> {
			Match m = matchOfA();
			if (!(m instanceof net.battlehub.games.SkyWarsMatch sw) || botsIn(m).isEmpty()) return false;
			watchFalls(server, m);
			if (sw.botLoots() >= 4) {
				BotPlayer b = botsIn(m).get(0);
				net.battlehub.BattleHub.LOGGER.info("SELFTEST skywars bots looted {} chests, one wears {} | {}", sw.botLoots(),
						b.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST), String.join(" | ", Bots.inspect(b)));
				return true;
			}
			logBot("skywars bot", m, botsIn(m).get(0), "swLogged");
			return false;
		});
		s.waitFor(tag + ": and bridge off them to the next chest", 2400, () -> {
			Match m = matchOfA();
			if (m == null || botsIn(m).isEmpty()) return false;
			watchFalls(server, m);
			for (BotPlayer b : botsIn(m)) {
				if (Bots.placed(b) >= 3) {
					net.battlehub.BattleHub.LOGGER.info("SELFTEST skywars bot built {} blocks | {}", Bots.placed(b), String.join(" | ", Bots.inspect(b)));
					return true;
				}
			}
			logBot("skywars bot", m, botsIn(m).get(0), "swLogged2");
			return false;
		});
		s.run(tag + ": watch them for half a minute more", () -> VARS.put("swUntil", server.getTickCount() + 600));
		s.waitFor(tag + ": (watching)", 700, () -> {
			Match m = matchOfA();
			if (m == null || m.phase() != Match.Phase.RUNNING) return true;
			watchFalls(server, m);
			return server.getTickCount() >= (int) VARS.get("swUntil");
		});
		s.check(tag + ": no bot walked or jumped off the map by itself", () -> {
			net.battlehub.BattleHub.LOGGER.info("SELFTEST skywars falls: {} in all, {} without a hit before", ((java.util.Map<?, ?>) VARS.get("swFalls")).size(),
					VARS.get("swSelfFalls"));
			return (int) VARS.get("swSelfFalls") == 0;
		});
		endAndClean(s, tag);
		s.run(tag + ": countdown as before", () -> {
			cfg().partyFillTo = 4;
			ConfigManager.config().timings.partyCountdownSeconds = (int) VARS.get("partyCountdown");
			ConfigManager.config().timings.partyFullCountdownSeconds = (int) VARS.get("partyFull");
		});
	}

	/** Keeps A alive, notes when each bot was last hit and counts falls below the islands (logged, with who fell how). */
	@SuppressWarnings("unchecked")
	private static void watchFalls(MinecraftServer server, Match m) {
		p(A).setHealth(p(A).getMaxHealth());
		java.util.Map<UUID, Integer> falls = (java.util.Map<UUID, Integer>) VARS.get("swFalls");
		java.util.Map<UUID, Integer> hurt = (java.util.Map<UUID, Integer>) VARS.get("swHurt");
		int now = server.getTickCount();
		double floor = m.arena().origin().getY();
		for (BotPlayer b : botsIn(m)) {
			if (b.isSpectator()) continue;
			if (b.hurtTime > 0) hurt.put(b.getUUID(), now);
			if (b.getY() < floor - 6 && !falls.containsKey(b.getUUID())) {
				falls.put(b.getUUID(), now);
				boolean hit = now - hurt.getOrDefault(b.getUUID(), -1000) <= 40;
				if (!hit) VARS.put("swSelfFalls", (int) VARS.get("swSelfFalls") + 1);
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST skywars bot fell {} at {} | {}", hit ? "after a hit" : "BY ITSELF", b.position(),
						String.join(" | ", Bots.inspect(b)));
			}
		}
	}
}
