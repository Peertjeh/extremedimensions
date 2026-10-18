package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.expedition.ExpeditionBar;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/** The mod as part of Minecraft: its timer is a boss bar, its sounds sit on the game's volume sliders. */
public class VanillaTests {
	/** An expedition shows a boss bar named for where the player is; it goes when the expedition ends or the setting is off. */
	@GameTest
	public void theExpeditionTimerIsABossBar(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-bar");
		PlayerData d = DataStore.get(p.getUUID());
		boolean inRun = d.inRun;
		String dim = d.dim;
		double max = d.runMax;
		int setting = Config.HUD_BOSS_BAR;
		try {
			d.inRun = true;
			d.dim = Dims.NETHER;
			d.runMax = 300;
			Config.HUD_BOSS_BAR = 1;
			ExpeditionBar.update(p, d);
			check(helper, ExpeditionBar.showing(p.getUUID()), "an expedition in the Nether shows no boss bar");
			check(helper, ExpeditionBar.label(p.getUUID()).toLowerCase(java.util.Locale.ROOT).contains("nether"),
					"the boss bar is not named for the Nether: %s", ExpeditionBar.label(p.getUUID()));
			Config.HUD_BOSS_BAR = 0;
			ExpeditionBar.update(p, d);
			check(helper, !ExpeditionBar.showing(p.getUUID()), "with the setting off the boss bar stays");
			Config.HUD_BOSS_BAR = 1;
			d.inRun = false;
			ExpeditionBar.update(p, d);
			check(helper, !ExpeditionBar.showing(p.getUUID()), "the boss bar stays after the expedition");
		} finally {
			d.inRun = inRun;
			d.dim = dim;
			d.runMax = max;
			Config.HUD_BOSS_BAR = setting;
			ExpeditionBar.forget(p.getUUID());
		}
		helper.succeed();
	}

	/** The arrow in the timer points the way home from where the player looks. */
	@GameTest
	public void theWayHomeArrowTurnsWithThePlayer(GameTestHelper helper) {
		// looking south (yaw 0): south is ahead, west to the right, north behind
		check(helper, ExpeditionBar.arrow(0, 0, 10).equals("↑"), "ahead is not up");
		check(helper, ExpeditionBar.arrow(0, -10, 0).equals("→"), "west, looking south, is not to the right");
		check(helper, ExpeditionBar.arrow(0, 0, -10).equals("↓"), "behind is not down");
		check(helper, ExpeditionBar.arrow(0, 10, 10).equals("↖"), "south-east, looking south, is not ahead and to the left");
		// looking east (yaw -90) and west (yaw 90) at a rift to the east
		check(helper, ExpeditionBar.arrow(-90, 10, 0).equals("↑"), "looking east, east is not ahead");
		check(helper, ExpeditionBar.arrow(90, 10, 0).equals("↓"), "looking west, east is not behind");
		helper.succeed();
	}

	/** Every sound of the mod plays on the volume slider Minecraft would put it on, never on Master alone. */
	@GameTest
	public void soundsSitOnTheGamesSliders(GameTestHelper helper) {
		for (Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
			if (id.getNamespace().equals("extremedimensions")) {
				check(helper, WorldFx.category(id.toString()) != SoundSource.MASTER, "%s plays on Master", id);
			}
		}
		check(helper, WorldFx.category("extremedimensions:seam_ambient") == SoundSource.AMBIENT, "the hum of the Seam is not ambient");
		check(helper, WorldFx.category("extremedimensions:lurker_whisper") == SoundSource.HOSTILE, "a Lurker's whisper is not hostile");
		check(helper, WorldFx.category("extremedimensions:music.boss_weaver") == SoundSource.MUSIC, "boss music is not music");
		check(helper, WorldFx.category("minecraft:block.wool.break") == SoundSource.BLOCKS, "a block's sound is not on Blocks");
		check(helper, WorldFx.category("extremedimensions:portal_home") == SoundSource.PLAYERS, "going home is not on Players");
		helper.succeed();
	}
}
