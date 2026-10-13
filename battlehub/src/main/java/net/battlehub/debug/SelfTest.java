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
	private static final Map<String, Object> VARS = new HashMap<>();

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
			for (TestPlayer p : PLAYERS.values()) {
				if (!p.hasDisconnected()) p.leave();
			}
			PLAYERS.clear();
		});
		lobbyAndMenus(server, s);
		duel(server, s, GameMode.SWORD, "kill");
		duel(server, s, GameMode.SWORD, "disconnect");
		duel(server, s, GameMode.SWORD, "forfeit");
		script = s;
	}

	static TestPlayer p(String name) {
		return PLAYERS.get(name);
	}

	static void join(MinecraftServer server, String name) {
		TestPlayer old = PLAYERS.get(name);
		if (old != null && !old.hasDisconnected()) {
			return;
		}
		PLAYERS.put(name, TestPlayer.join(server, name));
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
			m.clicked(10, 0, ContainerInput.QUICK_MOVE, p("BHTestA"));
			m.clicked(12, 0, ContainerInput.THROW, p("BHTestA"));
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
		s.check("no damage in the lobby", () -> {
			ServerPlayer a = p("BHTestA");
			float before = a.getHealth();
			a.hurtServer(a.level(), a.damageSources().playerAttack(p("BHTestB")), 5f);
			return a.getHealth() == before;
		});
	}

	/** A Sword PvP duel between BHTestA and BHTestB, ended by a kill, a disconnect or /hub. */
	private static void duel(MinecraftServer server, Script s, GameMode mode, String ending) {
		String tag = mode.id() + "/" + ending;
		s.run(tag + ": join (again)", () -> {
			join(server, "BHTestA");
			join(server, "BHTestB");
		});
		s.pause(10);
		s.run(tag + ": remember stats", () -> {
			VARS.put("winsA", PlayerStore.get(p("BHTestA").getUUID()).overall.wins);
			VARS.put("lossB", PlayerStore.get(p("BHTestB").getUUID()).overall.losses);
			VARS.put("coinsA", PlayerStore.get(p("BHTestA").getUUID()).coins);
		});
		s.run(tag + ": both queue", () -> {
			Queues.join(p("BHTestA"), mode, false);
			Queues.join(p("BHTestB"), mode, false);
		});
		s.check(tag + ": queued state", () -> Sessions.state(p("BHTestA").getUUID()) == Sessions.State.QUEUED
				|| Sessions.state(p("BHTestA").getUUID()) == Sessions.State.MATCH);
		s.waitFor(tag + ": match created", 60, () -> Matches.of(p("BHTestA").getUUID()) != null);
		s.waitFor(tag + ": arena built, players in countdown", 400, () -> {
			Match m = Matches.of(p("BHTestA").getUUID());
			return m != null && m.phase() == Match.Phase.COUNTDOWN;
		});
		s.check(tag + ": players teleported into the arena with the kit", () -> {
			ServerPlayer a = p("BHTestA");
			ServerPlayer b = p("BHTestB");
			return ArenaWorld.is(a.level()) && ArenaWorld.is(b.level()) && a.gameMode() == GameType.ADVENTURE
					&& a.getInventory().getItem(0).is(Items.DIAMOND_SWORD)
					&& b.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
					&& Snapshots.has(a.getUUID());
		});
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
		s.waitFor(tag + ": match closed and A back in the lobby", 200, () -> {
			Match m = (Match) VARS.get("match");
			ServerPlayer a = p("BHTestA");
			return m.phase() == Match.Phase.CLOSED && Sessions.isFree(a.getUUID()) && !ArenaWorld.is(a.level());
		});
		s.check(tag + ": A got the old inventory and game mode back", () -> {
			ServerPlayer a = p("BHTestA");
			return HubItems.COMPASS.equals(Icon.tagOf(a.getInventory().getItem(ConfigManager.config().compass.slot)))
					&& !a.getInventory().contains(st -> st.is(Items.DIAMOND_SWORD)) && a.gameMode() != GameType.ADVENTURE
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
