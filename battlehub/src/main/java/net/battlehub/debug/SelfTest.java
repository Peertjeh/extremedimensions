package net.battlehub.debug;

import java.util.HashMap;
import java.util.Map;

import net.battlehub.BattleHub;
import net.battlehub.arena.ArenaSlots;
import net.battlehub.arena.ArenaWorld;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.data.Snapshots;
import net.battlehub.gui.HubMenu;
import net.battlehub.gui.MainMenu;
import net.battlehub.lobby.HubItems;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.queue.Queues;
import net.battlehub.session.Sessions;
import net.battlehub.util.Icon;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Developer self-test (only with -Dbattlehub.selftest=true): simulated players walk through the real code paths
 * (compass, menus, queues, matches, deaths, disconnects, restore) and the results are checked. Used in CI.
 */
public final class SelfTest {
	private SelfTest() {}

	public static boolean enabled() {
		return Boolean.getBoolean("battlehub.selftest");
	}

	private static Script script;
	private static final Map<String, TestPlayer> PLAYERS = new HashMap<>();
	static final Map<String, Object> VARS = new HashMap<>();
	private static final java.util.Set<String> KEEP_ONLINE = new java.util.HashSet<>();

	public static void tick() {
		if (script != null) {
			script.tick();
			if (script.finished()) {
				script = null;
			}
		}
	}

	public static void start(MinecraftServer server) {
		if (script != null) {
			return;
		}
		BattleHub.LOGGER.info("SELFTEST starting");
		Script s = new Script(() -> {
			for (Map.Entry<String, TestPlayer> e : PLAYERS.entrySet()) {
				// Players in the last match stay online: the server stop has to end that match and restore them.
				if (!e.getValue().hasDisconnected() && !KEEP_ONLINE.contains(e.getKey())) e.getValue().leave();
			}
		});
		HubWorldTests.all(server, s);
		lobbyAndMenus(server, s);
		LayoutTests.all(s);
		MenuSecurityTests.all(server, s);
		CosmeticsTests.all(server, s);
		duel(server, s, GameMode.SWORD, "queue", "kill");
		duel(server, s, GameMode.SWORD, "queue", "disconnect");
		duel(server, s, GameMode.SWORD, "queue", "forfeit");
		duel(server, s, GameMode.SWORD, "ranked", "kill");
		duel(server, s, GameMode.AXE, "challenge", "kill");
		duel(server, s, GameMode.MACE, "challenge", "kill");
		duel(server, s, GameMode.BOW, "challenge", "kill");
		duel(server, s, GameMode.CROSSBOW, "queue", "kill");
		spectating(server, s);
		FormatTests.all(server, s);
		s.run("speed up party timers for the test", () -> {
			var c = ConfigManager.config();
			c.timings.partyCountdownSeconds = 3;
			c.timings.partyFullCountdownSeconds = 2;
			c.timings.matchStartCountdownSeconds = 2;
			c.timings.respawnDelaySeconds = 1;
			c.game(GameMode.KOTH).settings.put("targetPoints", 4.0);
			c.game(GameMode.CTF).settings.put("capturesToWin", 1.0);
			c.game(GameMode.LMS).settings.put("borderDelaySeconds", 1.0);
			c.game(GameMode.LMS).settings.put("borderShrinkSeconds", 20.0);
			c.game(GameMode.FFA).settings.put("killsToWin", 3.0);
		});
		PartyTests.all(server, s);
		ProgressionTests.all(server, s);
		runningAtStop(server, s);
		script = s;
	}

	/** C watches a duel between A and B: menu entry, spectator mode, bounds, /hub, auto-return at the end. */
	private static void spectating(MinecraftServer server, Script s) {
		String tag = "spectate";
		reset(s, tag);
		s.run(tag + ": join players", () -> {
			join(server, "BHTestA");
			join(server, "BHTestB");
			join(server, "BHTestC");
		});
		s.pause(10);
		s.run(tag + ": empty spectate menu", () -> new net.battlehub.gui.SpectateMenu(p("BHTestC")).open());
		s.check(tag + ": 'No active matches' is shown", () -> p("BHTestC").containerMenu instanceof HubMenu m
				&& m.getSlot(22).getItem().is(Items.BARRIER));
		s.run(tag + ": start a duel", () -> {
			p("BHTestC").closeContainer();
			net.battlehub.queue.Challenges.send(p("BHTestA"), p("BHTestB"), GameMode.SWORD);
			net.battlehub.queue.Challenges.accept(p("BHTestB"), p("BHTestA"));
		});
		s.waitFor(tag + ": duel running", 600, () -> {
			Match m = Matches.of(p("BHTestA").getUUID());
			return m != null && m.phase() == Match.Phase.RUNNING;
		});
		s.run(tag + ": open spectate menu", () -> {
			VARS.put("match", Matches.of(p("BHTestA").getUUID()));
			new net.battlehub.gui.SpectateMenu(p("BHTestC")).open();
		});
		s.check(tag + ": the duel is listed with the player names", () -> p("BHTestC").containerMenu instanceof HubMenu m
				&& m.getSlot(10).getItem().is(Items.DIAMOND_SWORD)
				&& m.getSlot(10).getItem().getHoverName().getString().contains("BHTestA"));
		s.run(tag + ": click Join Spectator", () -> ((HubMenu) p("BHTestC").containerMenu).clicked(10, 0, ContainerInput.PICKUP, p("BHTestC")));
		s.waitFor(tag + ": C is a spectator in the arena", 10, () -> {
			ServerPlayer c = p("BHTestC");
			return c.gameMode() == GameType.SPECTATOR && ArenaWorld.is(c.level())
					&& Sessions.state(c.getUUID()) == Sessions.State.SPECTATING;
		});
		s.check(tag + ": spectator is not a player of the match", () -> {
			Match m = (Match) VARS.get("match");
			return !m.participants().contains(p("BHTestC").getUUID()) && m.watchers().contains(p("BHTestC").getUUID());
		});
		s.run(tag + ": C flies far away", () -> {
			ServerPlayer c = p("BHTestC");
			c.teleportTo(c.level(), c.getX() + 300, c.getY(), c.getZ(), java.util.Set.of(), 0, 0, false);
		});
		s.waitFor(tag + ": C is brought back inside the arena", 40, () -> {
			Match m = (Match) VARS.get("match");
			return m.arena().inSpectatorBounds(p("BHTestC").position());
		});
		s.run(tag + ": C leaves with /hub", () -> net.battlehub.command.HubCommands.hub(p("BHTestC")));
		s.check(tag + ": C back in the lobby with the compass", () -> {
			ServerPlayer c = p("BHTestC");
			return !ArenaWorld.is(c.level()) && Sessions.isFree(c.getUUID()) && c.gameMode() != GameType.SPECTATOR
					&& HubItems.COMPASS.equals(Icon.tagOf(c.getInventory().getItem(ConfigManager.config().compass.slot)));
		});
		s.run(tag + ": C watches again via /spectate <player>", () -> net.battlehub.spectate.Spectators.join(p("BHTestC"), (Match) VARS.get("match")));
		s.check(tag + ": spectators take no damage", () -> {
			ServerPlayer c = p("BHTestC");
			float before = c.getHealth();
			c.hurtServer(c.level(), c.damageSources().generic(), 5f);
			return c.getHealth() == before;
		});
		s.pause(5);
		s.run(tag + ": A kills B", () -> {
			ServerPlayer b = p("BHTestB");
			b.hurtServer(b.level(), b.damageSources().playerAttack(p("BHTestA")), 1000f);
		});
		s.waitFor(tag + ": match over, C sent back to the lobby", 300, () -> {
			ServerPlayer c = p("BHTestC");
			Match m = (Match) VARS.get("match");
			return m.phase() == Match.Phase.CLOSED && !ArenaWorld.is(c.level()) && Sessions.isFree(c.getUUID())
					&& c.gameMode() != GameType.SPECTATOR && !Snapshots.has(c.getUUID());
		});
		s.waitFor(tag + ": arena released", 400, () -> ArenaSlots.busy() == 0);
	}

	private static void setLanguage(ServerPlayer p, String code) {
		net.minecraft.server.level.ClientInformation d = p.clientInformation();
		p.updateOptions(new net.minecraft.server.level.ClientInformation(code, d.viewDistance(), d.chatVisibility(), d.chatColors(),
				d.modelCustomisation(), d.mainHand(), d.textFilteringEnabled(), d.allowsListing(), d.particleStatus()));
	}

	/** The item in slot 0 of the mode's kit. */
	static net.minecraft.world.item.Item kitWeapon(GameMode mode) {
		var kit = ConfigManager.kits().kits.get(ConfigManager.config().game(mode).kit);
		for (var it : kit.items) {
			if ("0".equals(it.slot)) return net.battlehub.util.Ids.item(it.item);
		}
		return Items.AIR;
	}

	public static TestPlayer p(String name) {
		return PLAYERS.get(name);
	}

	/** A test player online and in the BattleHub lobby (players join in the normal world and use /hub). */
	public static void join(MinecraftServer server, String name) {
		TestPlayer p = joinOutside(server, name);
		if (!net.battlehub.hub.Travel.inBattleHub(p)) {
			net.battlehub.hub.Travel.enter(p);
		}
	}

	/** A test player online, wherever they are (new players: the normal world). */
	public static TestPlayer joinOutside(MinecraftServer server, String name) {
		TestPlayer old = PLAYERS.get(name);
		if (old != null && !old.hasDisconnected()) {
			return old;
		}
		TestPlayer p = TestPlayer.join(server, name);
		PLAYERS.put(name, p);
		return p;
	}

	/** Leaves a duel running; the CI script then stops the server and checks everybody was restored. */
	private static void runningAtStop(MinecraftServer server, Script s) {
		String tag = "server stop";
		reset(s, tag);
		s.run(tag + ": A and B start a duel", () -> {
			join(server, "BHTestA");
			join(server, "BHTestB");
			Queues.join(p("BHTestA"), GameMode.SWORD, false);
			Queues.join(p("BHTestB"), GameMode.SWORD, false);
		});
		s.waitFor(tag + ": duel running when the server stops", 400, () -> {
			Match m = Matches.of(p("BHTestA").getUUID());
			return m != null && m.phase() == Match.Phase.RUNNING && Snapshots.has(p("BHTestA").getUUID());
		});
		s.run(tag + ": keep A and B online", () -> {
			KEEP_ONLINE.add("BHTestA");
			KEEP_ONLINE.add("BHTestB");
		});
	}

	/** Puts every test player back in the lobby, so a failed scenario cannot break the next one. */
	static void reset(Script s, String tag) {
		s.run(tag + ": reset leftovers", () -> {
			for (TestPlayer pl : PLAYERS.values()) {
				if (pl.hasDisconnected()) continue;
				Match m = Matches.of(pl.getUUID());
				if (m != null) {
					BattleHub.LOGGER.warn("SELFTEST leftover match #{} ended", m.id);
					Matches.end(m.id);
				}
				if (Sessions.state(pl.getUUID()) == Sessions.State.SPECTATING) {
					net.battlehub.spectate.Spectators.leave(pl, true);
				}
				Queues.leaveAll(pl);
			}
		});
		s.waitFor(tag + ": arena slots free before the start", 600, () -> ArenaSlots.busy() == 0);
	}

	private static void lobbyAndMenus(MinecraftServer server, Script s) {
		s.run("join test players", () -> {
			join(server, "BHTestA");
			join(server, "BHTestB");
		});
		s.pause(15);
		s.check("players are in the lobby state", () -> Sessions.isFree(p("BHTestA").getUUID()) && Sessions.isFree(p("BHTestB").getUUID()));
		s.check("compass is in its hotbar slot", () -> {
			int slot = ConfigManager.config().compass.slot;
			return HubItems.COMPASS.equals(Icon.tagOf(p("BHTestA").getInventory().getItem(slot)));
		});
		s.run("stray compass copies are removed", () -> p("BHTestA").getInventory().setItem(20, HubItems.compass(p("BHTestA"))));
		s.waitFor("only one compass after the lobby tick", 40, () -> {
			int n = 0;
			for (int i = 0; i < p("BHTestA").getInventory().getContainerSize(); i++) {
				if (HubItems.COMPASS.equals(Icon.tagOf(p("BHTestA").getInventory().getItem(i)))) n++;
			}
			return n == 1;
		});
		s.run("open main menu", () -> new MainMenu(p("BHTestA")).open());
		s.check("main menu has the six items", () -> {
			if (!(p("BHTestA").containerMenu instanceof HubMenu m)) return false;
			int[] slots = {10, 11, 12, 14, 15, 16};
			for (int sl : slots) {
				if (m.getSlot(sl).getItem().isEmpty() || m.getSlot(sl).getItem().is(net.battlehub.util.Ids.item("minecraft:black_stained_glass_pane"))) return false;
			}
			return m.getSlot(10).getItem().is(Items.DIAMOND_SWORD) && m.getSlot(15).getItem().is(Items.ENDER_EYE);
		});
		s.run("items cannot be taken from the menu", () -> {
			HubMenu m = (HubMenu) p("BHTestA").containerMenu;
			m.clicked(0, 0, ContainerInput.QUICK_MOVE, p("BHTestA"));
			m.clicked(10, 0, ContainerInput.THROW, p("BHTestA"));
			m.clicked(12, 1, ContainerInput.SWAP, p("BHTestA"));
			m.clicked(11, 0, ContainerInput.CLONE, p("BHTestA"));
		});
		s.check("menu item still there and nothing in the inventory", () ->
				p("BHTestA").containerMenu instanceof HubMenu m && m.getSlot(10).getItem().is(Items.DIAMOND_SWORD)
						&& m.getCarried().isEmpty() && !p("BHTestA").getInventory().contains(st -> st.is(Items.DIAMOND_SWORD)));
		s.run("click Duels", () -> ((HubMenu) p("BHTestA").containerMenu).clicked(10, 0, ContainerInput.PICKUP, p("BHTestA")));
		s.waitFor("Duels submenu opened", 10, () -> p("BHTestA").containerMenu instanceof HubMenu m
				&& m.getSlot(11).getItem().is(Items.DIAMOND_SWORD) && m.getSlot(15).getItem().is(Items.MACE));
		s.run("close menu", () -> p("BHTestA").closeContainer());
		s.check("compass cannot be thrown", () -> {
			ServerPlayer a = p("BHTestA");
			a.getInventory().setSelectedSlot(ConfigManager.config().compass.slot);
			a.drop(true);
			return HubItems.COMPASS.equals(Icon.tagOf(a.getInventory().getItem(ConfigManager.config().compass.slot)));
		});
		s.run("A's client switches to Belgian Dutch", () -> setLanguage(p("BHTestA"), "nl_be"));
		s.check("Dutch players get the Dutch menu", () -> {
			new MainMenu(p("BHTestA")).open();
			return net.battlehub.lang.Lang.languageOf(p("BHTestA")).equals("nl_nl")
					&& p("BHTestA").containerMenu instanceof HubMenu m && m.getSlot(15).getItem().getHoverName().getString().contains("Toekijken")
					&& net.battlehub.lang.Lang.languageOf(p("BHTestB")).equals("en_us");
		});
		s.run("A back to English", () -> {
			setLanguage(p("BHTestA"), "en_us");
			p("BHTestA").closeContainer();
		});
		s.check("no damage in the lobby", () -> {
			ServerPlayer a = p("BHTestA");
			float before = a.getHealth();
			a.hurtServer(a.level(), a.damageSources().playerAttack(p("BHTestB")), 5f);
			return a.getHealth() == before;
		});
	}

	/** A duel between BHTestA and BHTestB, started by queue, ranked queue or challenge, ended by a kill, a disconnect or /hub. */
	private static void duel(MinecraftServer server, Script s, GameMode mode, String start, String ending) {
		String tag = mode.id() + "/" + start + "/" + ending;
		reset(s, tag);
		s.run(tag + ": join (again)", () -> {
			join(server, "BHTestA");
			join(server, "BHTestB");
			// The hub can run in survival; matches use adventure, so the check at the end sees it given back
			p("BHTestA").setGameMode(GameType.SURVIVAL);
		});
		s.pause(10);
		s.run(tag + ": remember stats", () -> {
			VARS.put("winsA", PlayerStore.get(p("BHTestA").getUUID()).overall.wins);
			VARS.put("lossB", PlayerStore.get(p("BHTestB").getUUID()).overall.losses);
			VARS.put("coinsA", PlayerStore.get(p("BHTestA").getUUID()).coins);
			VARS.put("eloA", PlayerStore.get(p("BHTestA").getUUID()).elo(mode, ConfigManager.config().elo.start));
			VARS.put("eloB", PlayerStore.get(p("BHTestB").getUUID()).elo(mode, ConfigManager.config().elo.start));
		});
		if (start.equals("challenge")) {
			s.run(tag + ": A challenges B", () -> net.battlehub.queue.Challenges.send(p("BHTestA"), p("BHTestB"), mode));
			s.check(tag + ": challenge is pending", () -> net.battlehub.queue.Challenges.hasPending(p("BHTestA").getUUID(), p("BHTestB").getUUID()));
			s.run(tag + ": B accepts", () -> net.battlehub.queue.Challenges.accept(p("BHTestB"), p("BHTestA")));
		} else {
			boolean ranked = start.equals("ranked");
			s.run(tag + ": both queue", () -> {
				Queues.join(p("BHTestA"), mode, ranked);
				Queues.join(p("BHTestB"), mode, ranked);
			});
			s.check(tag + ": queued state", () -> Sessions.state(p("BHTestA").getUUID()) == Sessions.State.QUEUED
					|| Sessions.state(p("BHTestA").getUUID()) == Sessions.State.MATCH);
		}
		s.waitFor(tag + ": match created", 60, () -> Matches.of(p("BHTestA").getUUID()) != null);
		s.waitFor(tag + ": arena built, players in countdown", 400, () -> {
			Match m = Matches.of(p("BHTestA").getUUID());
			return m != null && m.phase() == Match.Phase.COUNTDOWN;
		});
		s.check(tag + ": players teleported into the arena with the kit", () -> {
			ServerPlayer a = p("BHTestA");
			ServerPlayer b = p("BHTestB");
			return ArenaWorld.is(a.level()) && ArenaWorld.is(b.level()) && a.gameMode() == GameType.ADVENTURE
					&& a.getInventory().getItem(0).is(kitWeapon(mode))
					&& !b.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()
					&& Snapshots.has(a.getUUID());
		});
		s.check(tag + ": match is " + (start.equals("ranked") ? "ranked" : "casual"), () ->
				Matches.of(p("BHTestA").getUUID()).ranked == start.equals("ranked"));
		if (mode == GameMode.MACE) {
			s.check(tag + ": mace is played on floating islands", () ->
					Matches.of(p("BHTestA").getUUID()).arena().theme().open());
		}
		s.check(tag + ": no damage during the countdown", () -> {
			ServerPlayer b = p("BHTestB");
			float before = b.getHealth();
			b.hurtServer(b.level(), b.damageSources().playerAttack(p("BHTestA")), 4f);
			return b.getHealth() == before;
		});
		s.waitFor(tag + ": fight started", 200, () -> {
			Match m = Matches.of(p("BHTestA").getUUID());
			return m != null && m.phase() == Match.Phase.RUNNING;
		});
		s.run(tag + ": remember match", () -> VARS.put("match", Matches.of(p("BHTestA").getUUID())));
		switch (ending) {
			case "kill" -> {
				s.check(tag + ": damage between fighters works", () -> {
					ServerPlayer b = p("BHTestB");
					float before = b.getHealth();
					b.hurtServer(b.level(), b.damageSources().playerAttack(p("BHTestA")), 4f);
					return b.getHealth() < before;
				});
				s.pause(12);
				s.run(tag + ": A kills B", () -> {
					ServerPlayer b = p("BHTestB");
					b.hurtServer(b.level(), b.damageSources().playerAttack(p("BHTestA")), 1000f);
				});
				s.check(tag + ": B did not really die", () -> p("BHTestB").isAlive() && p("BHTestB").getHealth() > 0);
			}
			case "disconnect" -> s.run(tag + ": B disconnects", () -> p("BHTestB").leave());
			case "forfeit" -> s.run(tag + ": B uses /hub", () -> net.battlehub.command.HubCommands.hub(p("BHTestB")));
			default -> { }
		}
		s.check(tag + ": A won", () -> {
			Match m = (Match) VARS.get("match");
			return m.outcome(p("BHTestA").getUUID()) == net.battlehub.core.Outcome.WIN
					&& m.outcome(p("BHTestB").getUUID()) == net.battlehub.core.Outcome.LOSS;
		});
		s.check(tag + ": stats and coins updated", () -> {
			PlayerProfile a = PlayerStore.get(p("BHTestA").getUUID());
			PlayerProfile b = PlayerStore.get(TestPlayer.uuid("BHTestB"));
			return a.overall.wins == (int) VARS.get("winsA") + 1 && b.overall.losses == (int) VARS.get("lossB") + 1
					&& a.coins > (long) VARS.get("coinsA") && a.peek(mode).wins >= 1;
		});
		if (start.equals("ranked")) {
			s.check(tag + ": ELO moved by the K-factor", () -> {
				int a = PlayerStore.get(p("BHTestA").getUUID()).elo(mode, 1000);
				int b = PlayerStore.get(TestPlayer.uuid("BHTestB")).elo(mode, 1000);
				int ea = (int) VARS.get("eloA");
				int eb = (int) VARS.get("eloB");
				return a > ea && b < eb && (a - ea) == (eb - b) && PlayerStore.get(p("BHTestA").getUUID()).rankedStats(mode).wins >= 1;
			});
		} else {
			s.check(tag + ": casual match leaves ELO alone", () ->
					PlayerStore.get(p("BHTestA").getUUID()).elo(mode, 1000) == (int) VARS.get("eloA"));
		}
		s.waitFor(tag + ": match closed and A back in the lobby", 200, () -> {
			Match m = (Match) VARS.get("match");
			ServerPlayer a = p("BHTestA");
			return m.phase() == Match.Phase.CLOSED && Sessions.isFree(a.getUUID()) && !ArenaWorld.is(a.level());
		});
		s.check(tag + ": A got the old inventory and game mode back", () -> {
			ServerPlayer a = p("BHTestA");
			return HubItems.COMPASS.equals(Icon.tagOf(a.getInventory().getItem(ConfigManager.config().compass.slot)))
					&& !a.getInventory().contains(st -> st.is(Items.DIAMOND_SWORD)) && a.gameMode() == GameType.SURVIVAL
					&& !Snapshots.has(a.getUUID());
		});
		if (ending.equals("disconnect")) {
			s.check(tag + ": B's position is still owed", () -> Snapshots.has(TestPlayer.uuid("BHTestB")));
			s.run(tag + ": B rejoins", () -> join(server, "BHTestB"));
			s.pause(5);
			s.check(tag + ": B restored in the lobby", () -> !ArenaWorld.is(p("BHTestB").level())
					&& !Snapshots.has(p("BHTestB").getUUID()) && Sessions.isFree(p("BHTestB").getUUID()));
		} else {
			s.check(tag + ": B back in the lobby with the compass", () -> {
				ServerPlayer b = p("BHTestB");
				return !ArenaWorld.is(b.level()) && Sessions.isFree(b.getUUID())
						&& HubItems.COMPASS.equals(Icon.tagOf(b.getInventory().getItem(ConfigManager.config().compass.slot)));
			});
		}
		s.check(tag + ": profile saved to disk", () -> java.nio.file.Files.exists(PlayerStore.worldDir(server)
				.resolve("players").resolve(p("BHTestA").getUUID() + ".json")));
		s.waitFor(tag + ": arena slot cleared and released", 400, () -> {
			Match m = (Match) VARS.get("match");
			BlockPos o = m.arena().origin();
			return ArenaSlots.busy() == 0 && m.arena().level().getBlockState(o).isAir();
		});
	}
}
