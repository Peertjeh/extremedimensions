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
 * The mod beside Sodium, Iris, Mod Menu, Text Placeholder API and JEI, as most players run it: the compatibility job in CI fetches their newest
 * builds for this game version (tools/compat/fetch.py) and runs the client test with them, this test alone
 * ({@code -Dextremedimensions.compat=true}; the others stand aside there). The game must start with them all, the
 * journal and the settings must open, Mod Menu must list the mod and its Configure button open the mod's settings, Text
 * Placeholder API must read the tablist placeholders back, JEI must take the mod's categories and pages, and the Seam (its sky, its fog, the expedition's bar) must
 * draw under Sodium's renderer. Last (3.51.1), when the job could fetch a shader pack: the pack is turned on in the
 * Seam, the mod's lanterns must be in its list of blocks under a vanilla light's number, and the Seam must show no End
 * flash. Every step is a screenshot.
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
		for (String id : new String[] {"sodium", "iris", "modmenu", "placeholder-api", "jei"}) {
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

			// The tablist placeholders, read back through Text Placeholder API as TAB reads them, when it is there
			if (loader.isModLoaded("placeholder-api")) {
				String[] got = sp.getServer().computeOnServer(s -> {
					ServerPlayer p = player(s);
					DataStore.get(p.getUUID()).addStacks(com.extremedimensions.penalty.Penalty.values()[0], 1);
					com.extremedimensions.compat.TabPlaceholders.refresh(s);
					String[] out = new String[5];
					String[] names = {"curses", "realm", "status", "difficulty", "tapestry"};
					for (int i = 0; i < names.length; i++) {
						net.minecraft.network.chat.Component c = com.extremedimensions.compat.PlaceholderApiHook.parse(p, names[i]);
						out[i] = c == null ? null : c.getString();
					}
					return out;
				});
				System.out.println("[compat] placeholders: curses=" + got[0] + " realm=" + got[1] + " status=" + got[2] + " difficulty=" + got[3]
						+ " tapestry=" + got[4]);
				check(com.extremedimensions.compat.TabPlaceholders.offered, "the placeholders were not offered to placeholder-api");
				check(got[0] != null && Integer.parseInt(got[0]) >= 1, "%extremedimensions:curses% did not read the curse just taken: " + got[0]);
				check("Overworld".equals(got[1]) && "At home".equals(got[2]), "realm and status read " + got[1] + " / " + got[2]);
				check(got[3] != null && !got[3].isEmpty() && got[4] != null && got[4].endsWith("/6"), "the world's placeholders read " + got[3] + " / " + got[4]);
			}

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

			// JEI (3.35): the mod's own categories and item pages are given to it, and its recipe screen opens on them
			if (loader.isModLoaded("jei")) {
				for (int i = 0; i < 40 && com.extremedimensions.client.compat.JeiStatus.show == null; i++) {
					ctx.waitTicks(5);
				}
				System.out.println("[compat] JEI: " + com.extremedimensions.client.compat.JeiStatus.fuels + " fuels, "
						+ com.extremedimensions.client.compat.JeiStatus.trades + " wares and gifts, " + com.extremedimensions.client.compat.JeiStatus.spoils
						+ " spoils, " + com.extremedimensions.client.compat.JeiStatus.infos + " item pages");
				check(com.extremedimensions.client.compat.JeiStatus.fuels > 0 && com.extremedimensions.client.compat.JeiStatus.trades > 0
						&& com.extremedimensions.client.compat.JeiStatus.spins == 1 && com.extremedimensions.client.compat.JeiStatus.spoils > 0
						&& com.extremedimensions.client.compat.JeiStatus.infos > 0, "the mod's JEI plugin gave JEI nothing");
				check(ctx.computeOnClient(mc -> com.extremedimensions.client.compat.JeiStatus.show()), "JEI's runtime never came up");
				ctx.waitTicks(20);
				ctx.takeScreenshot("compat_06_jei");
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

			if (loader.isModLoaded("iris")) {
				underShaders(ctx, sp);
			}
		}
	}

	/** Iris's public switch for its shader pack, turned on or off (false when it cannot be reached). */
	private static boolean shaders(boolean on) {
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object instance = api.getMethod("getInstance").invoke(null);
			Object config = api.getMethod("getConfig").invoke(instance);
			Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, on);
			return true;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			System.out.println("[compat] could not turn the shader pack " + (on ? "on" : "off") + ": " + e);
			return false;
		}
	}

	private static boolean shaderPackInUse() {
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			return (Boolean) api.getMethod("isShaderPackInUse").invoke(api.getMethod("getInstance").invoke(null));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return false;
		}
	}

	/** The number Iris gives a block state for the shader pack in use ({@code null} when it gives none, or has no list). */
	@SuppressWarnings("unchecked")
	private static Integer shaderId(net.minecraft.world.level.block.state.BlockState state) {
		try {
			Class<?> settings = Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings");
			Object ids = settings.getMethod("getBlockStateIds").invoke(settings.getField("INSTANCE").get(null));
			if (ids instanceof it.unimi.dsi.fastutil.objects.Object2IntMap<?> map) {
				var m = (it.unimi.dsi.fastutil.objects.Object2IntMap<net.minecraft.world.level.block.state.BlockState>) map;
				return m.containsKey(state) ? m.getInt(state) : null;
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			System.out.println("[compat] Iris's list of blocks could not be read: " + e);
		}
		return null;
	}

	/**
	 * The Seam under the shader pack the job fetched (3.51.1): a Kindled Seam Lantern and a Veil Lantern before the
	 * visitor, the pack turned on, its list of blocks read back, and two looks a while apart (a white-out between them
	 * was the End's flash, which the Seam no longer has). Left out when there is no pack.
	 */
	private static void underShaders(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		java.nio.file.Path packs = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
		boolean any;
		try (var list = java.nio.file.Files.list(packs)) {
			any = list.findAny().isPresent();
		} catch (java.io.IOException e) {
			any = false;
		}
		if (!any) {
			System.out.println("[compat] no shader pack in " + packs + ": the look under one is left out");
			return;
		}
		// The two lanterns, three blocks ahead and to either side, on the ground, looking away from the gate again
		sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ ~180 20");
		ctx.waitTicks(5);
		sp.getServer().runOnServer(s -> {
			ServerPlayer p = player(s);
			net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) p.level();
			net.minecraft.core.Direction ahead = p.getDirection();
			net.minecraft.core.BlockPos base = p.blockPosition().relative(ahead, 3);
			net.minecraft.core.BlockPos[] at = {base.relative(ahead.getClockWise()), base.relative(ahead.getCounterClockWise())};
			net.minecraft.world.level.block.Block[] lanterns = {com.extremedimensions.registry.ModBlocks.KINDLED_SEAM_LANTERN,
					com.extremedimensions.registry.ModBlocks.VEIL_LANTERN};
			for (int i = 0; i < at.length; i++) {
				if (level.getBlockState(at[i].below()).isAir()) {
					level.setBlock(at[i].below(), com.extremedimensions.registry.ModBlocks.POLISHED_PALE_HUSH_STONE.defaultBlockState(), 3);
				}
				level.setBlock(at[i], lanterns[i].defaultBlockState(), 3);
			}
		});
		ctx.waitTicks(10);
		check(ctx.computeOnClient(mc -> shaders(true)), "Iris's switch for its shader pack could not be reached");
		for (int i = 0; i < 24 && !ctx.computeOnClient(mc -> shaderPackInUse()); i++) {
			ctx.waitTicks(5);
		}
		boolean inUse = ctx.computeOnClient(mc -> shaderPackInUse());
		System.out.println("[compat] shader pack in use: " + inUse);
		if (!inUse) {
			// (a pack that does not compile on the job's software renderer: nothing to look at, which is not the mod's)
			ctx.takeScreenshot("compat_07_seam_shader_failed");
			ctx.runOnClient(mc -> shaders(false));
			return;
		}
		ctx.waitTicks(20);
		ctx.takeScreenshot("compat_07_seam_shader");

		// The lanterns in the pack's list, under a vanilla light's number (where the pack names that light at all)
		Integer kindled = ctx.computeOnClient(mc -> shaderId(com.extremedimensions.registry.ModBlocks.KINDLED_SEAM_LANTERN.defaultBlockState()));
		Integer crying = ctx.computeOnClient(mc -> shaderId(net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN.defaultBlockState()));
		Integer veil = ctx.computeOnClient(mc -> shaderId(com.extremedimensions.registry.ModBlocks.VEIL_LANTERN.defaultBlockState()));
		Integer soul = ctx.computeOnClient(mc -> shaderId(net.minecraft.world.level.block.Blocks.SOUL_LANTERN.defaultBlockState()));
		Integer light = ctx.computeOnClient(mc -> shaderId(net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState()
				.setValue(net.minecraft.world.level.block.LightBlock.LEVEL, com.extremedimensions.config.Config.KINDLED_LANTERN_LIGHT)));
		Integer amethyst = ctx.computeOnClient(mc -> shaderId(net.minecraft.world.level.block.Blocks.AMETHYST_CLUSTER.defaultBlockState()));
		Integer carried = ctx.computeOnClient(mc -> shaderId(com.extremedimensions.registry.ModBlocks.KINDLED_LIGHT.defaultBlockState()
				.setValue(net.minecraft.world.level.block.LightBlock.LEVEL, com.extremedimensions.config.Config.KINDLED_LANTERN_LIGHT)));
		System.out.println("[compat] shader ids: kindled_seam_lantern=" + kindled + " crying_obsidian=" + crying + " veil_lantern=" + veil
				+ " soul_lantern=" + soul + " light=" + light + " amethyst_cluster=" + amethyst + " kindled_light=" + carried);
		if (crying != null && com.extremedimensions.client.options.ClientOptions.shaderLook() == com.extremedimensions.client.options.ClientOptions.ShaderLook.CRYING_OBSIDIAN) {
			check(crying.equals(kindled), "the Kindled Seam Lantern is not in the shader pack's list as crying obsidian: " + kindled + " / " + crying);
			check(crying.equals(carried), "the lantern's unseen light is not in the shader pack's list as crying obsidian: " + carried + " / " + crying);
		}
		if (soul != null) {
			check(soul.equals(veil), "the Veil Lantern is not in the shader pack's list as a soul lantern: " + veil + " / " + soul);
		}

		// No End flash in the Seam, over a stretch of it
		float most = 0f;
		for (int i = 0; i < 6; i++) {
			ctx.waitTicks(10);
			most = Math.max(most, ctx.computeOnClient(mc -> mc.level == null || mc.level.endFlashState() == null ? 0f : mc.level.endFlashState().getIntensity(1f)));
		}
		check(most == 0f, "an End flash showed in the Seam under the shader pack: " + most);
		ctx.takeScreenshot("compat_08_seam_shader_later");

		// (3.52.0) the mod's other glowing blocks, in a row five blocks ahead, each in the pack's list as its vanilla light
		net.minecraft.world.level.block.state.BlockState[] row = {com.extremedimensions.registry.ModBlocks.EMBER_ANCHOR.defaultBlockState(),
				com.extremedimensions.registry.ModBlocks.VOID_ANCHOR.defaultBlockState(), com.extremedimensions.registry.ModBlocks.SEAM_VEIN.defaultBlockState(),
				com.extremedimensions.registry.ModBlocks.SILKED_HUSH_BRICKS.defaultBlockState(), com.extremedimensions.registry.ModBlocks.TETHERITE_ORE.defaultBlockState(),
				knot(0), knot(1), knot(2), knot(3)};
		net.minecraft.world.level.block.state.BlockState[] as = {net.minecraft.world.level.block.Blocks.MAGMA_BLOCK.defaultBlockState(),
				net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN.defaultBlockState(), net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN.defaultBlockState(),
				net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN.defaultBlockState(), net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS.defaultBlockState(),
				net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState(), net.minecraft.world.level.block.Blocks.SOUL_TORCH.defaultBlockState(),
				net.minecraft.world.level.block.Blocks.AMETHYST_CLUSTER.defaultBlockState(), net.minecraft.world.level.block.Blocks.REDSTONE_TORCH.defaultBlockState()};
		sp.getServer().runOnServer(s -> {
			ServerPlayer p = player(s);
			net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) p.level();
			net.minecraft.core.Direction ahead = p.getDirection();
			net.minecraft.core.BlockPos mid = p.blockPosition().relative(ahead, 5);
			for (int i = 0; i < row.length; i++) {
				net.minecraft.core.BlockPos at = mid.relative(ahead.getClockWise(), i - row.length / 2);
				level.setBlock(at.below(), com.extremedimensions.registry.ModBlocks.POLISHED_PALE_HUSH_STONE.defaultBlockState(), 3);
				level.setBlock(at, row[i], 3);
			}
		});
		ctx.waitTicks(40);
		ctx.takeScreenshot("compat_09_seam_shader_lights");
		StringBuilder got = new StringBuilder();
		for (int i = 0; i < row.length; i++) {
			int k = i;
			Integer mine = ctx.computeOnClient(mc -> shaderId(row[k]));
			Integer theirs = ctx.computeOnClient(mc -> shaderId(as[k]));
			got.append(' ').append(row[i].getBlock().getDescriptionId().replace("block.extremedimensions.", "")).append('=').append(mine).append('/').append(theirs);
			if (theirs != null) {
				check(theirs.equals(mine), row[i] + " is not in the shader pack's list as " + as[i] + ": " + mine + " / " + theirs);
			}
		}
		System.out.println("[compat] shader ids of the glowing blocks (mine/vanilla):" + got);
		ctx.runOnClient(mc -> shaders(false));
		ctx.waitTicks(10);
	}

	private static net.minecraft.world.level.block.state.BlockState knot(int set) {
		return com.extremedimensions.registry.ModBlocks.CURSE_KNOT.defaultBlockState().setValue(com.extremedimensions.tapestry.CurseKnotBlock.SET, set);
	}
}
