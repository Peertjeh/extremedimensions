package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.compat.StatsExport;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.trial.TrialService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The stats export for a web page: the world and every player the mod knows, online or not, in the words of the tablist;
 * nobody online in the file written as the server stops; the Elders a player helped defeat only where they are known;
 * the file written whole, off the server's thread, and only when there is a folder for it.
 */
public class StatsExportTests {
	/** The export's entry for one player, or null. */
	private static JsonObject entry(JsonObject export, UUID id) {
		for (JsonElement e : export.getAsJsonArray("players")) {
			if (e.getAsJsonObject().get("uuid").getAsString().equals(id.toString())) {
				return e.getAsJsonObject();
			}
		}
		return null;
	}

	private static Penalty abyssal() {
		for (Penalty pen : Penalty.values()) {
			if (pen.abyssal && pen.max > 0) {
				return pen;
			}
		}
		return null;
	}

	@GameTest
	public void theExportHoldsTheWorldAndEveryPlayer(GameTestHelper helper) {
		MinecraftServer s = helper.getLevel().getServer();
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		int curses = d.addStacks(Penalty.GLASS_JAW, 2) + d.addStacks(abyssal(), 1);
		d.scars = 2;
		d.expeditions = 4;
		d.collapses = 3;
		d.unwoven = 1;
		d.trialClaimWeek = TrialService.week();
		d.trialClaimMedal = 3;
		// Two who are not online: one whose Elders are known (the Matriarch and the Last Weaver), one never looked at
		UUID away = UUID.randomUUID(), unknown = UUID.randomUUID();
		PlayerData a = DataStore.get(away);
		a.name = "export-away";
		a.expeditions = 9;
		int awayCurses = a.addStacks(Penalty.GLASS_JAW, 1);
		a.elders = 1 << StatsExport.Elder.MATRIARCH.ordinal() | 1 << StatsExport.Elder.WEAVER.ordinal();
		DataStore.get(unknown).name = "export-unknown";

		JsonObject json = StatsExport.json(s, false);
		check(helper, json.get("generated").getAsString().matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ"), "generated reads %s", json.get("generated"));
		String version = FabricLoader.getInstance().getModContainer(ExtremeDimensions.MOD_ID).orElseThrow().getMetadata().getVersion().getFriendlyString();
		check(helper, json.get("version").getAsString().equals(version), "version reads %s, not %s", json.get("version"), version);
		JsonObject world = json.getAsJsonObject("world");
		check(helper, List.of("Easy", "Normal", "Hard", "Extreme", "Custom").contains(world.get("difficulty").getAsString()), "difficulty reads %s",
				world.get("difficulty"));
		check(helper, List.of("Mended", "Severed", "Unended").contains(world.get("ending").getAsString()), "ending reads %s", world.get("ending"));
		JsonObject tapestry = world.getAsJsonObject("tapestry");
		check(helper, tapestry.get("total").getAsInt() == 6 && tapestry.get("woven").getAsInt() >= 0 && tapestry.get("woven").getAsInt() <= 6
				&& tapestry.get("finished").isJsonPrimitive(), "the tapestry reads %s", tapestry);
		check(helper, world.get("newThread").getAsInt() >= 0 && world.get("trial").isJsonPrimitive(), "New Thread+ or the trial is missing: %s", world);

		// Online: where they are and what they carry, as the tablist has it
		JsonObject me = entry(json, p.getUUID());
		check(helper, me != null, "the online player is not in the export");
		check(helper, me.get("name").getAsString().equals(p.getScoreboardName()) && me.get("online").getAsBoolean(), "the online player reads %s", me);
		check(helper, "Overworld".equals(me.get("realm").getAsString()) && "At home".equals(me.get("status").getAsString()),
				"the online player is in %s, %s", me.get("realm"), me.get("status"));
		check(helper, me.get("curses").getAsInt() == curses && me.get("abyssal").getAsInt() == 1 && me.get("scars").getAsInt() == 2,
				"curses, abyssal and scars read %s, %s, %s (expected %s, 1, 2)", me.get("curses"), me.get("abyssal"), me.get("scars"), curses);
		check(helper, me.get("expeditions").getAsInt() == 4 && me.get("collapses").getAsInt() == 3 && me.get("unwoven").getAsInt() == 1,
				"the counts read %s", me);
		check(helper, "Gold".equals(me.get("trialMedal").getAsString()), "this week's medal reads %s", me.get("trialMedal"));
		check(helper, me.get("eldersSlain").getAsInt() == 0 && me.getAsJsonArray("elders").isEmpty(), "a new player helped defeat %s", me.get("elders"));

		// Offline: no realm or status, their own curses, the Elders as last known; unknown Elders are left out
		JsonObject them = entry(json, away);
		check(helper, them != null && "export-away".equals(them.get("name").getAsString()) && !them.get("online").getAsBoolean(),
				"the offline player reads %s", them);
		check(helper, them.get("realm").getAsString().isEmpty() && them.get("status").getAsString().isEmpty(), "an offline player is somewhere: %s", them);
		check(helper, them.get("curses").getAsInt() == awayCurses && them.get("expeditions").getAsInt() == 9 && them.get("trialMedal").getAsString().isEmpty(),
				"the offline player's numbers read %s", them);
		JsonArray elders = them.getAsJsonArray("elders");
		check(helper, them.get("eldersSlain").getAsInt() == 2 && elders.size() == 2 && "The Matriarch".equals(elders.get(0).getAsString())
				&& "The Last Weaver".equals(elders.get(1).getAsString()), "the offline player's Elders read %s, %s", them.get("eldersSlain"), elders);
		JsonObject never = entry(json, unknown);
		check(helper, never != null && !never.has("eldersSlain") && !never.has("elders"), "Elders never looked at are in the export: %s", never);

		// In order of name
		String last = "";
		for (JsonElement e : json.getAsJsonArray("players")) {
			String name = e.getAsJsonObject().get("name").getAsString().toLowerCase(java.util.Locale.ROOT);
			check(helper, name.compareTo(last) >= 0, "%s comes after %s", name, last);
			last = name;
		}

		// The file written as the server stops: nobody is online
		JsonObject stopped = entry(StatsExport.json(s, true), p.getUUID());
		check(helper, !stopped.get("online").getAsBoolean() && stopped.get("realm").getAsString().isEmpty() && stopped.get("status").getAsString().isEmpty(),
				"stopping, the online player still reads %s", stopped);
		helper.succeed();
	}

	@GameTest
	public void anEldersFallIsKeptForTheExport(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "export-elder");
		PlayerData d = DataStore.get(p.getUUID());
		check(helper, StatsExport.elders(p) == 0 && d.elders == 0, "a new player helped defeat Elders (%s)", d.elders);
		Advancements.grant(p, Advancements.FERRYMAN_SLAIN);
		check(helper, d.elders == 1 << StatsExport.Elder.FERRYMAN.ordinal(), "the Ferryman's fall was not kept (%s)", d.elders);
		Advancements.grant(p, Advancements.KNOT_MOTHER);
		JsonObject me = entry(StatsExport.json(helper.getLevel().getServer(), false), p.getUUID());
		check(helper, me.get("eldersSlain").getAsInt() == 2 && "The Ferryman".equals(me.getAsJsonArray("elders").get(0).getAsString())
				&& "The Knot Mother".equals(me.getAsJsonArray("elders").get(1).getAsString()), "the export reads %s", me);
		helper.succeed();
	}

	@GameTest
	public void theFileIsWrittenWholeAndOnlyWhenAsked(GameTestHelper helper) {
		MinecraftServer s = helper.getLevel().getServer();
		String before = Config.EXPORT_DIR;
		Path dir = null;
		try {
			dir = Files.createTempDirectory("ed-export");
			// No folder: no export
			Config.EXPORT_DIR = "";
			check(helper, StatsExport.dir() == null && StatsExport.export(s, false) == null, "an export is written without export_dir");
			Config.EXPORT_DIR = "  ";
			check(helper, StatsExport.export(s, false) == null, "an export is written with a blank export_dir");
			// A relative folder is the server's
			Config.EXPORT_DIR = "config/battlehub/export";
			check(helper, StatsExport.dir().equals(FabricLoader.getInstance().getGameDir().resolve("config/battlehub/export").normalize()),
					"a relative export_dir is %s", StatsExport.dir());

			// Written off the server's thread, whole, and with nothing left beside it
			Config.EXPORT_DIR = dir.resolve("sub").toString();
			Path file = StatsExport.export(s, false).get(10, TimeUnit.SECONDS);
			check(helper, file.equals(dir.resolve("sub").resolve(StatsExport.FILE)) && Files.isRegularFile(file), "the export went to %s", file);
			check(helper, !StatsExport.writtenBy.isEmpty() && !StatsExport.writtenBy.equals(Thread.currentThread().getName()),
					"the export was written on the server's thread (%s)", StatsExport.writtenBy);
			JsonObject read = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			check(helper, read.has("generated") && read.has("version") && read.getAsJsonObject("world").has("tapestry") && read.get("players").isJsonArray(),
					"the file holds %s", read.keySet());
			try (var listing = Files.list(file.getParent())) {
				List<String> names = listing.map(f -> f.getFileName().toString()).toList();
				check(helper, names.equals(List.of(StatsExport.FILE)), "the folder holds %s", names);
			}
			// Written again: the new one takes the old one's place
			Files.writeString(file, "old");
			StatsExport.export(s, true).get(10, TimeUnit.SECONDS);
			check(helper, Files.readString(file).trim().startsWith("{"), "the old file stayed");
			helper.succeed();
		} catch (java.io.IOException | java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException | InterruptedException e) {
			throw new AssertionError("the export could not be written or read back", e);
		} finally {
			Config.EXPORT_DIR = before;
			if (dir != null) {
				try (var walk = Files.walk(dir)) {
					walk.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
				} catch (java.io.IOException e) {
					// (a temporary folder; the system clears it in time)
				}
			}
		}
	}
}
