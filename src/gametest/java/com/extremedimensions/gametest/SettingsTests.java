package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;

import com.google.gson.JsonObject;

import com.extremedimensions.config.Config;
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
			check(helper, Config.set("atone_base", "120") && Config.ATONE_BASE == 120, "a valid whole number was not taken");
			check(helper, Config.set("extreme_card_chance", "0,05") && Math.abs(Config.EXTREME_CARD_CHANCE - 0.05) < 1e-9,
					"a decimal comma was not read as a decimal point");
			check(helper, !Config.set("atone_base", "-1") && Config.ATONE_BASE == 120, "a negative value was taken");
			check(helper, !Config.set("atone_base", "lots") && !Config.set("atone_base", "1.5"), "a non-number or a fraction was taken for a whole number");
			check(helper, !Config.set("no_such_setting", "1"), "an unknown setting was taken");
			check(helper, Config.set("penalty.glass_jaw.max", "2") && Penalty.GLASS_JAW.max == 2, "a curse's maximum was not taken");
			check(helper, !Config.set("penalty.glass_jaw.max", "100"), "a curse maximum above 99 was taken");
			check(helper, Config.set("penalty.glass_jaw.per_stack", "7.5") && Penalty.GLASS_JAW.value == 7.5, "a curse's strength was not taken");
			check(helper, !Config.set("penalty.nothing.max", "1"), "an unknown curse was taken");
			JsonObject snap = Config.snapshot();
			check(helper, snap.get("atone_base").getAsInt() == 120 && snap.getAsJsonObject("penalties").getAsJsonObject("glass_jaw").get("max").getAsInt() == 2,
					"the snapshot does not hold the values just set");
			check(helper, Config.settings().size() > 80, "only %s settings listed", Config.settings().size());
			check(helper, Config.settings().stream().allMatch(st -> !st.fallback().isEmpty()), "a setting has no default");
		} finally {
			Config.ATONE_BASE = base;
			Config.EXTREME_CARD_CHANCE = chance;
			Penalty.GLASS_JAW.max = max;
			Penalty.GLASS_JAW.value = value;
		}
		helper.succeed();
	}

	@GameTest
	public void onlyOperatorsChangeTheWorldsRules(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		int base = Config.ATONE_BASE;
		check(helper, !SettingsService.canEdit(p), "a player without operator rights may change the world's rules");
		SettingsService.change(p, "{\"atone_base\":\"999\"}");
		check(helper, Config.ATONE_BASE == base, "a player without operator rights changed atone_base to %s", Config.ATONE_BASE);
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
