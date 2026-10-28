package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.p;

import net.battlehub.compat.ExternalTitles;
import net.battlehub.compat.PlaceholderApiHook;
import net.battlehub.compat.TabPlaceholders;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.core.PlayerProfile;
import net.battlehub.core.TitleDef;
import net.battlehub.core.TitleRules;
import net.battlehub.data.PlayerStore;
import net.battlehub.hub.Travel;
import net.battlehub.progression.FeaturedMode;
import net.battlehub.progression.Ranks;
import net.battlehub.queue.Queues;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * BattleHub's placeholders (1.51): what they say about a player in the lobby, in a queue and outside BattleHub, and about
 * the hub; read back through Text Placeholder API, as TAB reads them, when the server has it installed (the CI server does).
 */
final class PlaceholderTests {
	private PlaceholderTests() {}

	static final String A = "BHTestA";
	static final String S = "BHTestS";

	/** Set by the CI script when it put placeholder-api in the mods folder: then reading them back must work. */
	private static boolean apiExpected() {
		return Boolean.getBoolean("battlehub.selftest.placeholderapi");
	}

	private static String of(String player, String name) {
		return TabPlaceholders.player(p(player).getUUID(), name).getString();
	}

	static void all(MinecraftServer server, Script s) {
		String tag = "placeholders";
		SelfTest.reset(s, tag);
		s.run(tag + ": A in the lobby, S in the normal world", () -> {
			SelfTest.join(server, A);
			ServerPlayer pl = SelfTest.joinOutside(server, S);
			if (Travel.inBattleHub(pl)) Travel.leave(pl);
		});
		s.waitFor(tag + ": A in BattleHub, S outside", 200, () -> Travel.inBattleHub(p(A)) && !Travel.inBattleHub(p(S)));
		s.check(tag + ": A in the lobby: activity, status, level, ELO and tier", () -> {
			TabPlaceholders.refresh(server);
			String level = Integer.toString(Ranks.curve().progress(PlayerStore.get(p(A)).xp).level());
			boolean ok = of(A, "activity").equals("lobby") && of(A, "status").equals("Lobby") && of(A, "level").equals(level)
					&& Integer.parseInt(of(A, "elo")) > 0 && !of(A, "tier").isEmpty();
			if (!ok) {
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST placeholders of A: activity {} status {} level {} (expected {}) elo {} tier {}",
						of(A, "activity"), of(A, "status"), of(A, "level"), level, of(A, "elo"), of(A, "tier"));
			}
			return ok;
		});
		s.check(tag + ": S outside BattleHub is away, with no status", () -> of(S, "activity").equals("away") && of(S, "status").isEmpty());
		s.check(tag + ": the hub counts A among its players", () -> Integer.parseInt(TabPlaceholders.world("players").getString()) >= 1);
		s.run(tag + ": A queues for Sword", () -> Queues.join(p(A), GameMode.SWORD, false));
		s.check(tag + ": A is in the queue, by name of the mode", () -> {
			TabPlaceholders.refresh(server);
			return of(A, "activity").equals("queue") && of(A, "status").equals("In queue: " + TabPlaceholders.modeName(GameMode.SWORD));
		});
		s.run(tag + ": A leaves the queue", () -> Queues.leaveAll(p(A)));
		s.check(tag + ": the mode of the day and the next weekly tournament, when switched on", () -> {
			var c = ConfigManager.config();
			c.featured.enabled = true;
			c.weekly.enabled = true;
			TabPlaceholders.refresh(server);
			GameMode today = FeaturedMode.today();
			String mode = TabPlaceholders.world("mode_of_the_day").getString();
			String weekly = TabPlaceholders.world("weekly").getString();
			c.featured.enabled = false;
			c.weekly.enabled = false;
			TabPlaceholders.refresh(server);
			boolean ok = today != null && mode.equals(TabPlaceholders.modeName(today)) && weekly.matches("\\d+[dhm]( \\d+[hm])?|Sign-ups open")
					&& TabPlaceholders.world("weekly").getString().isEmpty() && TabPlaceholders.world("mode_of_the_day").getString().isEmpty();
			if (!ok) net.battlehub.BattleHub.LOGGER.warn("SELFTEST placeholders: mode of the day '{}' ({}), weekly '{}'", mode, today, weekly);
			return ok;
		});
		s.check(tag + ": time to go reads as days, hours or minutes", () -> TabPlaceholders.duration(5).equals("5m")
				&& TabPlaceholders.duration(65).equals("1h 5m") && TabPlaceholders.duration(3000).equals("2d 2h")
				&& TabPlaceholders.duration(-3).equals("0m"));
		s.check(tag + ": read back through placeholder-api as TAB reads them", () -> {
			if (!FabricLoader.getInstance().isModLoaded("placeholder-api")) {
				if (apiExpected()) return false;
				net.battlehub.BattleHub.LOGGER.info("SELFTEST placeholders: placeholder-api is not installed, reading back skipped");
				return true;
			}
			TabPlaceholders.refresh(server);
			Component activity = PlaceholderApiHook.read(p(A), "battlehub", "activity");
			Component players = PlaceholderApiHook.read(p(A), "battlehub", "players");
			return TabPlaceholders.offered && activity != null && activity.getString().equals("lobby")
					&& players != null && players.getString().equals(TabPlaceholders.world("players").getString())
					&& PlaceholderApiHook.read(p(A), "battlehub", "no_such_thing") == null;
		});
		s.check(tag + ": a title earned through a placeholder is kept, one not met is not (1.51)", () -> {
			if (!FabricLoader.getInstance().isModLoaded("placeholder-api")) return !apiExpected();
			// BattleHub's own placeholders stand in for another mod's: level 1 is met, activity "match" is not
			TitleDef met = new TitleDef("test_ph_level", "&fTest", "placeholder", 1, "").placeholder("battlehub:level", "", "");
			TitleDef unmet = new TitleDef("test_ph_match", "&fTest", "placeholder", 0, "").placeholder("battlehub:activity", "match", "");
			var titles = ConfigManager.titles().titles;
			titles.add(met);
			titles.add(unmet);
			TabPlaceholders.refresh(server);
			PlayerProfile prof = PlayerStore.get(p(A));
			var earned = ExternalTitles.check(p(A));
			boolean ok = earned.contains(met) && !earned.contains(unmet) && TitleRules.unlocked(met, prof, 1000)
					&& !TitleRules.unlocked(unmet, prof, 1000) && ExternalTitles.check(p(A)).isEmpty()
					&& ExternalTitles.available(p(A), prof, unmet);
			titles.remove(met);
			titles.remove(unmet);
			prof.ownedTitles.remove(met.id);
			prof.knownTitles.remove(met.id);
			return ok;
		});
		s.check(tag + ": Extreme Dimensions' titles are in the list, hidden without that mod", () -> {
			TitleDef tethered = ConfigManager.titles().byId("tethered");
			return tethered != null && "extremedimensions:expeditions".equals(tethered.placeholder)
					&& (FabricLoader.getInstance().isModLoaded("extremedimensions")
							|| !ExternalTitles.available(p(A), PlayerStore.get(p(A)), tethered));
		});
	}
}
