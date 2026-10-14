package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;

import com.google.gson.JsonObject;

import com.extremedimensions.config.Config;
import com.extremedimensions.config.Preset;
import com.extremedimensions.config.SettingsService;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.guide.Guide;
import com.extremedimensions.penalty.Penalty;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** The world's settings can be read and changed safely, and the first-expedition guide follows what the player does. */
public class SettingsTests {
	@GameTest
	public void settingsAreCheckedBeforeTheyAreTaken(GameTestHelper helper) {
		int base = Config.ATONE_BASE;
		double chance = Config.EXTREME_CARD_CHANCE;
		int max = Penalty.GLASS_JAW.max;
		double value = Penalty.GLASS_JAW.value;
		try {
			check(helper, Config.set("atone_base", "135") && Config.ATONE_BASE == 135, "a valid whole number was not taken");
			check(helper, Config.set("extreme_card_chance", "0,05") && Math.abs(Config.EXTREME_CARD_CHANCE - 0.05) < 1e-9,
					"a decimal comma was not read as a decimal point");
			check(helper, !Config.set("atone_base", "-1") && Config.ATONE_BASE == 135, "a negative value was taken");
			check(helper, !Config.set("atone_base", "lots") && !Config.set("atone_base", "1.5"), "a non-number or a fraction was taken for a whole number");
			check(helper, !Config.set("no_such_setting", "1"), "an unknown setting was taken");
			check(helper, Config.set("penalty.glass_jaw.max", "2") && Penalty.GLASS_JAW.max == 2, "a curse's maximum was not taken");
			check(helper, !Config.set("penalty.glass_jaw.max", "100"), "a curse maximum above 99 was taken");
			check(helper, Config.set("penalty.glass_jaw.per_stack", "7.5") && Penalty.GLASS_JAW.value == 7.5, "a curse's strength was not taken");
			check(helper, !Config.set("penalty.nothing.max", "1"), "an unknown curse was taken");
			JsonObject snap = Config.snapshot();
			check(helper, snap.get("atone_base").getAsInt() == 135 && snap.getAsJsonObject("penalties").getAsJsonObject("glass_jaw").get("max").getAsInt() == 2,
					"the snapshot does not hold the values just set");
			check(helper, Config.settings().size() > 80, "only %s settings listed", Config.settings().size());
			check(helper, Config.settings().stream().allMatch(st -> !st.fallback().isEmpty()), "a setting has no default");
			// Settings are shown and filled in exactly: a small chance never turns into 0 on its way through the screen
			check(helper, Config.format(0.012).equals("0.012") && Config.format(2.0).equals("2") && Config.format(0.15).equals("0.15"),
					"values lose precision as text: %s, %s, %s", Config.format(0.012), Config.format(2.0), Config.format(0.15));
			check(helper, Config.settings().stream().filter(st -> st.key().equals("lurker_spawn_chance")).allMatch(st -> st.fallback().equals("0.012")),
					"the default of lurker_spawn_chance is not kept exactly");
		} finally {
			Config.ATONE_BASE = base;
			Config.EXTREME_CARD_CHANCE = chance;
			Penalty.GLASS_JAW.max = max;
			Penalty.GLASS_JAW.value = value;
		}
		helper.succeed();
	}

	@GameTest
	public void olderConfigsFollowNewDefaultsButKeepChoices(GameTestHelper helper) {
		JsonObject old = new JsonObject();
		old.addProperty("atone_base", 100);
		old.addProperty("atone_per_stack", 75);
		Config.migrate(old);
		check(helper, old.get("atone_base").getAsInt() == 120, "an old default was not brought up to the new one (%s)", old.get("atone_base"));
		check(helper, old.get("atone_per_stack").getAsInt() == 75, "a chosen value was overwritten (%s)", old.get("atone_per_stack"));
		JsonObject current = new JsonObject();
		current.addProperty("config_version", 2);
		current.addProperty("atone_base", 100);
		Config.migrate(current);
		check(helper, current.get("atone_base").getAsInt() == 100, "a value chosen in an up-to-date file was changed (%s)", current.get("atone_base"));
		helper.succeed();
	}

	@GameTest
	public void onlyOperatorsChangeTheWorldsRules(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		int base = Config.ATONE_BASE;
		check(helper, !SettingsService.canEdit(p), "a player without operator rights may change the world's rules");
		SettingsService.change(p, "{\"atone_base\":\"999\"}");
		check(helper, Config.ATONE_BASE == base, "a player without operator rights changed atone_base to %s", Config.ATONE_BASE);
		SettingsService.change(p, "{\"preset\":\"pilgrim\"}");
		check(helper, Config.ATONE_BASE == base, "a player without operator rights applied a difficulty (atone_base %s)", Config.ATONE_BASE);
		helper.succeed();
	}

	@GameTest
	public void difficultyPresetsAreSound(GameTestHelper helper) {
		// The standard preset is exactly the mod's defaults
		java.util.Map<String, String> defaults = new java.util.LinkedHashMap<>();
		for (Config.Setting st : Config.settings()) {
			defaults.put(st.key(), st.fallback());
		}
		check(helper, Preset.TETHERED.matches(defaults), "the standard difficulty differs from the defaults");
		check(helper, Preset.matching(defaults) == Preset.TETHERED, "the defaults are not recognised as the standard difficulty");
		// From gentle to merciless: atonement costs more and a death undoes more at every step
		Preset[] all = Preset.values();
		for (int i = 1; i < all.length; i++) {
			double a = Double.parseDouble(all[i - 1].values.get("atone_base")), b = Double.parseDouble(all[i].values.get("atone_base"));
			double la = Double.parseDouble(all[i - 1].values.get("atone_death_loss")), lb = Double.parseDouble(all[i].values.get("atone_death_loss"));
			check(helper, b > a && lb >= la, "%s is not harder than %s", all[i].id, all[i - 1].id);
		}
		// Every preset value is a real setting the world takes
		JsonObject before = Config.snapshot();
		try {
			for (Preset p : all) {
				for (var e : p.values.entrySet()) {
					check(helper, Config.set(e.getKey(), e.getValue()), "preset %s has a value the world refuses: %s = %s", p.id, e.getKey(), e.getValue());
				}
				check(helper, Preset.matching(Preset.current()) == p, "after applying %s the world's rules are not recognised as it", p.id);
			}
		} finally {
			Config.apply(before);
		}
		helper.succeed();
	}

	@GameTest
	public void guideFollowsTheFirstExpedition(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		check(helper, Guide.progress(d) == 0, "a new player starts the guide at %s", Guide.progress(d));
		Guide.done(p, d, Guide.TETHER);
		check(helper, Guide.progress(d) == Guide.TETHER, "binding a Tether first should count the anchor step too, at %s", Guide.progress(d));
		Guide.done(p, d, Guide.ANCHOR);
		check(helper, Guide.progress(d) == Guide.TETHER, "an earlier step moved the guide back to %s", Guide.progress(d));
		Guide.done(p, d, Guide.HOME);
		check(helper, Guide.progress(d) == Guide.STEPS, "coming home did not finish the guide (%s)", Guide.progress(d));
		ServerPlayer veteran = cursed(helper);
		PlayerData v = DataStore.get(veteran.getUUID());
		v.expeditions = 3;
		check(helper, Guide.progress(v) == Guide.STEPS, "a player with expeditions behind them still sees the guide");
		helper.succeed();
	}
}
