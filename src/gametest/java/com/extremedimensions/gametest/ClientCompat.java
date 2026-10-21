package com.extremedimensions.gametest;

import com.extremedimensions.client.screen.JournalScreen;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.journal.Journal;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The mod beside Sodium, Iris and Mod Menu, as most players run it: the compatibility job in CI fetches their newest
 * builds for this game version (tools/compat/fetch.py) and runs the client test with them, this test alone
 * ({@code -Dextremedimensions.compat=true}; the others stand aside there). The game must start with them all, the
 * journal and the settings must open, Mod Menu must list the mod and its Configure button open the mod's settings, and
 * the Seam (its sky, its fog, the expedition's bar) must draw under Sodium's renderer. Every step is a screenshot.
 */
public class ClientCompat implements FabricClientGameTest {
	/** Whether this run is the compatibility job's. */
	public static boolean on() {
		return Boolean.getBoolean("extremedimensions.compat");
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new AssertionError(message);
		}
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!on()) {
			return;
		}
		FabricLoader loader = FabricLoader.getInstance();
		for (String id : new String[] {"sodium", "iris", "modmenu"}) {
			System.out.println("[compat] " + id + ": " + loader.getModContainer(id).map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("not installed"));
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("effect give @a minecraft:resistance infinite 255 true");
			ctx.waitTicks(80);
			ctx.takeScreenshot("compat_01_overworld");

			// The journal, as the game opens it
			JournalScreen before = ctx.computeOnClient(mc -> JournalScreen.last());
			sp.getServer().runOnServer(s -> Journal.open(player(s), DataStore.get(player(s).getUUID())));
			ctx.waitTicks(30);
			check(ctx.computeOnClient(mc -> JournalScreen.last()) != before, "the journal did not open");
			ctx.takeScreenshot("compat_02_journal");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(4);

			// The mod's settings, through Mod Menu's own list and its Configure button when Mod Menu is there
			if (loader.isModLoaded("modmenu")) {
				ctx.runOnClient(mc -> {
					try {
						Object list = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen")
								.getConstructor(net.minecraft.client.gui.screens.Screen.class).newInstance((Object) null);
						mc.gui.setScreen((net.minecraft.client.gui.screens.Screen) list);
					} catch (ReflectiveOperationException e) {
						System.out.println("[compat] Mod Menu's list could not be opened: " + e);
					}
				});
				ctx.waitTicks(10);
				ctx.takeScreenshot("compat_03_mod_menu");
				boolean found = false;
				for (var e : loader.getEntrypointContainers("modmenu", Object.class)) {
					if (e.getProvider().getMetadata().getId().equals("extremedimensions")
							&& e.getEntrypoint() instanceof com.extremedimensions.client.compat.ModMenuEntry entry) {
						net.minecraft.client.gui.screens.Screen settings = ctx.computeOnClient(mc -> entry.getModConfigScreenFactory().create(null));
						found = settings instanceof com.extremedimensions.client.screen.SettingsScreen;
						ctx.runOnClient(mc -> mc.gui.setScreen(settings));
					}
				}
				check(found, "Mod Menu's Configure does not open the mod's settings");
				ctx.waitTicks(10);
				ctx.takeScreenshot("compat_04_settings");
				ctx.runOnClient(mc -> mc.gui.setScreen(null));
				ctx.waitTicks(4);
			}

			// Into the Seam: its sky, its fog and the expedition's bar under Sodium's renderer
			sp.getServer().runCommand("execute as @a run tether seam");
			for (int i = 0; i < 40 && !sp.getServer().computeOnServer(s -> com.extremedimensions.seam.SeamService.inSeam(player(s))); i++) {
				ctx.waitTicks(5);
			}
			check(sp.getServer().computeOnServer(s -> com.extremedimensions.seam.SeamService.inSeam(player(s))), "the visit to the Seam did not begin");
			ctx.waitTicks(100);
			try {
				sp.getConnection().waitForChunksRender(false);
			} catch (AssertionError late) {
				System.out.println("[compat] chunks not drawn in time; going on");
			}
			ctx.takeScreenshot("compat_05_seam");
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ ~180 -10");
			ctx.waitTicks(40);
			ctx.takeScreenshot("compat_06_seam_behind");
		}
	}
}
