package com.extremedimensions.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.extremedimensions.client.hud.ChargeHud;
import com.extremedimensions.client.hud.GuideCloseButton;
import com.extremedimensions.client.options.ClientOptions;
import com.extremedimensions.client.screen.CursesScreen;
import com.extremedimensions.client.screen.SettingsScreen;
import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.SealedPockets;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The client side in a real game window: the HUD, the inventory and other menus with sealed slots, the Curses screen,
 * the curse cards, the tribe at night and every creature's face up close. Every screen is saved as a screenshot (uploaded by CI) for review; the checks
 * that can be automated fail the run.
 */
public class ClientShots implements FabricClientGameTest {
	/**
	 * A live fight with one Elder: two shots as it forms and its arena flies together, one as it arrives, one at each
	 * blow, two in its ultimate, and two as it dies (its arena coming apart after).
	 */
	private static void liveFight(ClientGameTestContext ctx, TestSingleplayerContext sp, String name, int settle,
			java.util.function.Consumer<MinecraftServer> summon, java.util.function.BiPredicate<net.minecraft.world.entity.Entity, Integer> force,
			int[] blows, String[] blowNames) {
		// Each fight on untouched ground, away from the last one's craters, fires and arena (on top of it, not inside a hill)
		sp.getServer().runOnServer(s -> {
			ServerPlayer p = player(s);
			double nx = p.getX() + 400, nz = p.getZ();
			int bx = (int) Math.floor(nx), bz = (int) Math.floor(nz);
			// load the chunk first: an unloaded one has no ground to stand on
			p.level().getChunk(bx >> 4, bz >> 4);
			int top = p.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
			p.teleportTo(nx, top > p.getY() - 48 ? top : p.getY(), nz);
		});
		sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
		ctx.waitTicks(20);
		// The world round it loaded first: its arena is raised only where the world is loaded (on the server; the
		// client may still be drawing the far chunks, which a software renderer does slowly)
		for (int i = 0; i < 60 && !sp.getServer().computeOnServer(s -> loadedRound(s, 160)); i++) {
			ctx.waitTicks(10);
		}
		ctx.waitTicks(40);
		sp.getServer().runOnServer(summon::accept);
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> check(com.extremedimensions.client.fx.CameraLock.locked(), "the view is not held on the forming " + name));
		ctx.takeScreenshot("12_fight_" + name + "_forming_a");
		ctx.waitTicks(60);
		ctx.takeScreenshot("12_fight_" + name + "_forming_b");
		ctx.waitTicks(Math.max(70, settle));
		face(sp, name);
		ctx.waitTicks(2);
		ctx.takeScreenshot("12_fight_" + name + "_arrives");
		turns(ctx, sp, name);
		for (int i = 0; i < blows.length; i++) {
			int blow = blows[i];
			sp.getServer().runOnServer(s -> elders(s).forEach(e -> force.test(e, blow)));
			ctx.waitTicks(28);
			face(sp, name);
			ctx.waitTicks(2);
			ctx.takeScreenshot("12_fight_" + name + "_" + blowNames[i]);
			ctx.waitTicks(45);
		}
		// The whole arena from high over its middle, by now built, then from out past its edge, looking along its towers
		double[] stood = sp.getServer().computeOnServer(s -> new double[] {player(s).getX(), player(s).getY(), player(s).getZ()});
		double[] mid = sp.getServer().computeOnServer(s -> com.extremedimensions.elder.ElderArena.lastCentre());
		check(mid != null, "the " + name + " raised no arena");
		// Flying, so the view holds where it is put
		sp.getServer().runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getAbilities().mayfly = true;
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
			p.setRemainingFireTicks(0);
		});
		// The arena fights back, seen while its Elder is near (from far off its part of the world unloads with it). Its
		// domain changes twice as the Elder weakens, seen from over the edge of its heart
		sp.getServer().runOnServer(s -> player(s).teleportTo(mid[0], mid[1] + 16, mid[2] - 40));
		sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "execute as @a at @s run tp @s ~ ~ ~ facing %.1f %.1f %.1f", mid[0], mid[1], mid[2] + 8));
		sp.getServer().runOnServer(s -> elders(s, 400).forEach(e -> System.out.println("[domain] " + name + ": " + e.getHealth() + " of " + e.getMaxHealth()
				+ " health before the domain changes")));
		sp.getServer().runOnServer(s -> elders(s, 400).forEach(e -> e.setHealth(e.getMaxHealth() * 0.5F)));
		ctx.waitTicks(10);
		sp.getServer().runOnServer(s -> elders(s, 400).forEach(e -> e.setHealth(e.getMaxHealth() * 0.28F)));
		ctx.waitTicks(52);
		int phase = sp.getServer().computeOnServer(s -> elders(s, 400).stream().mapToInt(com.extremedimensions.elder.ElderDomain::phase).max().orElse(-1));
		int found = sp.getServer().computeOnServer(s -> elders(s, 400).size());
		System.out.println("[domain] " + name + ": phase " + phase + " (" + found + " Elders near, " + com.extremedimensions.elder.ElderArena.count() + " arenas)");
		check(phase == 2, "the " + name + "'s domain is in phase " + phase + " at 28% of its health, expected 2");
		ctx.takeScreenshot("12_fight_" + name + "_domain");
		// A core of its great piece, from a few blocks off, tied to its Elder by a thread of light
		double[] core = sp.getServer().computeOnServer(s -> {
			com.extremedimensions.elder.ElderBoss e = elders(s, 400).stream().findFirst().orElse(null);
			if (e == null) {
				return null;
			}
			int[] cores = com.extremedimensions.elder.ElderDomain.cores(e);
			System.out.println("[domain] " + name + ": " + cores[1] + " of " + cores[0] + " cores stand, shield " + com.extremedimensions.elder.ElderDomain.ward(e));
			for (net.minecraft.core.BlockPos c : com.extremedimensions.elder.ElderDomain.coreBlocks(e)) {
				for (int k = 0; k < 16; k++) {
					double t = k * Math.PI / 8;
					net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(c.getX() + 1 + Math.cos(t) * 7, c.getY() + 1, c.getZ() + 1 + Math.sin(t) * 7);
					if (e.level().getBlockState(at).isAir() && e.level().getBlockState(at.above()).isAir()) {
						return new double[] {at.getX() + 0.5, at.getY(), at.getZ() + 0.5, c.getX() + 1, c.getY() + 1, c.getZ() + 1};
					}
				}
			}
			return null;
		});
		if (core != null) {
			sp.getServer().runOnServer(s -> player(s).teleportTo(core[0], core[1], core[2]));
			sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "execute as @a at @s run tp @s ~ ~ ~ facing %.1f %.1f %.1f", core[3], core[4], core[5]));
			ctx.waitTicks(30);
			ctx.takeScreenshot("12_fight_" + name + "_core");
		} else {
			System.out.println("[domain] " + name + ": no core standing in the open");
		}
		// An island over its Elder comes loose: its shadow gathers on the ground, then it comes down
		double[] isle = sp.getServer().computeOnServer(s -> elders(s, 400).stream()
				.map(e -> com.extremedimensions.elder.ElderDomain.loosenOver(e, e.getX(), e.getY(), e.getZ())).filter(java.util.Objects::nonNull).findFirst()
				.orElse(null));
		System.out.println("[domain] " + name + ": island " + (isle == null ? "none near" : java.util.Arrays.toString(isle)));
		if (isle != null) {
			sp.getServer().runOnServer(s -> player(s).teleportTo(isle[0] - 22, isle[1] + 8, isle[2] - 22));
			sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "execute as @a at @s run tp @s ~ ~ ~ facing %.1f %.1f %.1f", isle[0], (isle[1] + isle[3]) / 2,
					isle[2]));
			ctx.waitTicks(40);
			ctx.takeScreenshot("12_fight_" + name + "_isle_shadow");
			ctx.waitTicks(30);
			ctx.takeScreenshot("12_fight_" + name + "_isle_fall");
		}
		// Its far reaches (beyond the world loaded round the player) wait for their part of the world
		int held = sp.getServer().computeOnServer(s -> com.extremedimensions.elder.ElderArena.waiting());
		int standing = sp.getServer().computeOnServer(s -> com.extremedimensions.elder.ElderArena.standing());
		System.out.println("[arena] " + name + ": " + standing + " blocks standing, " + held + " waiting for their part of the world");
		check(held > 0, "nothing of the " + name + "'s arena waits for its part of the world (its far towers and islands are left out)");
		sp.getServer().runOnServer(s -> player(s).teleportTo(mid[0], mid[1] + 170, mid[2]));
		sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 90");
		ctx.waitTicks(40);
		ctx.takeScreenshot("12_fight_" + name + "_arena");
		// Out past its west wall (near enough that the Elder keeps to its prey), looking north along its towers: the
		// world comes in round there, and what waited for it goes up
		sp.getServer().runOnServer(s -> player(s).teleportTo(mid[0] - 190, mid[1] + 40, mid[2]));
		sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 180 15");
		for (int i = 0; i < 30 && sp.getServer().computeOnServer(s -> com.extremedimensions.elder.ElderArena.waiting()) >= held; i++) {
			ctx.waitTicks(10);
		}
		ctx.waitTicks(40);
		int left = sp.getServer().computeOnServer(s -> com.extremedimensions.elder.ElderArena.waiting());
		System.out.println("[arena] " + name + ": " + left + " still waiting once the world came in round its side");
		check(left < held, "nothing of the " + name + "'s arena went up as the world came in round its side (" + left + " of " + held + " still waiting)");
		ctx.takeScreenshot("12_fight_" + name + "_arena_side");
		sp.getServer().runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getAbilities().flying = false;
			p.getAbilities().mayfly = false;
			p.onUpdateAbilities();
		});
		sp.getServer().runOnServer(s -> player(s).teleportTo(stood[0], stood[1], stood[2]));
		ctx.waitTicks(10);
		sp.getServer().runOnServer(s -> elders(s).forEach(e -> force.test(e, com.extremedimensions.elder.ElderBoss.ULTIMATE)));
		ctx.waitTicks(38);
		face(sp, name);
		ctx.waitTicks(2);
		ctx.takeScreenshot("12_fight_" + name + "_ultimate_a");
		ctx.waitTicks(108);
		face(sp, name);
		ctx.waitTicks(2);
		ctx.takeScreenshot("12_fight_" + name + "_ultimate_b");
		ctx.waitTicks(75);
		face(sp, name);
		sp.getServer().runCommand("kill @e[type=extremedimensions:" + name + "]");
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> check(com.extremedimensions.client.fx.CameraLock.locked(), "the view is not held on the dying " + name));
		ctx.takeScreenshot("12_fight_" + name + "_death");
		ctx.waitTicks(66);
		ctx.takeScreenshot("12_fight_" + name + "_death_bloom");
		ctx.waitTicks(30);
		// Its arena is taken down at once, so the next fight builds on untouched ground
		sp.getServer().runOnServer(s -> com.extremedimensions.elder.ElderArena.clear());
		ctx.waitTicks(10);
	}

	/** Whether the world is loaded on the server in a ring this far round the player. */
	private static boolean loadedRound(MinecraftServer s, int r) {
		ServerPlayer p = player(s);
		for (int a = 0; a < 8; a++) {
			double t = a * Math.PI / 4;
			if (!p.level().isLoaded(net.minecraft.core.BlockPos.containing(p.getX() + r * Math.cos(t), p.getY(), p.getZ() + r * Math.sin(t)))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The player steps a quarter round about the Elder: on the client its body turns after them (only its yaw is sent,
	 * so a body left to itself would keep facing where it first stood).
	 */
	private static void turns(ClientGameTestContext ctx, TestSingleplayerContext sp, String name) {
		float[] before = {Float.NaN};
		ctx.runOnClient(mc -> clientElder(mc).ifPresent(e -> before[0] = e.yBodyRot));
		sp.getServer().runOnServer(s -> {
			ServerPlayer p = player(s);
			elders(s).stream().findFirst().ifPresent(e -> {
				double x = e.getX() - (p.getZ() - e.getZ()), z = e.getZ() + (p.getX() - e.getX());
				int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
				p.level().getChunk(bx >> 4, bz >> 4);
				int top = p.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
				p.teleportTo(x, top, z);
			});
		});
		ctx.waitTicks(60);
		ctx.runOnClient(mc -> {
			com.extremedimensions.elder.ElderBoss e = clientElder(mc).orElse(null);
			check(e != null && !Float.isNaN(before[0]), "no " + name + " on the client to turn");
			float lag = Math.abs(net.minecraft.util.Mth.wrapDegrees(e.yBodyRot - e.getYRot()));
			float turned = Math.abs(net.minecraft.util.Mth.wrapDegrees(e.yBodyRot - before[0]));
			System.out.println("[turn] " + name + " body " + e.yBodyRot + " yaw " + e.getYRot() + " before " + before[0]);
			check(lag < 12 && turned > 20, "the " + name + " does not turn on the client: body " + e.yBodyRot + ", yaw " + e.getYRot()
					+ ", body before " + before[0]);
		});
	}

	private static java.util.Optional<com.extremedimensions.elder.ElderBoss> clientElder(net.minecraft.client.Minecraft mc) {
		return mc.level.getEntitiesOfClass(com.extremedimensions.elder.ElderBoss.class, mc.player.getBoundingBox().inflate(96)).stream()
				.min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));
	}

	private static java.util.List<com.extremedimensions.elder.ElderBoss> elders(MinecraftServer s) {
		return elders(s, 96);
	}

	private static java.util.List<com.extremedimensions.elder.ElderBoss> elders(MinecraftServer s, double r) {
		ServerPlayer p = player(s);
		return p.level().getEntitiesOfClass(com.extremedimensions.elder.ElderBoss.class, p.getBoundingBox().inflate(r));
	}

	/**
	 * Attack effects drawn in front of the camera (as the server would send them), for the shots: 0 the three sigils
	 * with what falls on them, 1 blows landing on them with shockwaves, 2 lines and wisps.
	 */
	private static void fx(net.minecraft.client.Minecraft mc, int which) {
		double x = mc.player.getX(), z = mc.player.getZ() + 11;
		double g = Math.floor(mc.player.getY()) - 8;
		java.util.List<com.extremedimensions.network.ModPackets.AttackFxPayload.Fx> list = new java.util.ArrayList<>();
		int[] strikes = {1, 3, 4};
		int[] blows = {2, 6, 5};
		for (int e = 0; e < 3; e++) {
			float ex = (float) (x + (1 - e) * 7.5);
			if (which == 0) {
				list.add(new com.extremedimensions.network.ModPackets.AttackFxPayload.Fx(1, e, strikes[e], ex, (float) g, (float) z, 0, 0, 0, 3.0F, 50));
			} else if (which == 1) {
				list.add(new com.extremedimensions.network.ModPackets.AttackFxPayload.Fx(2, e, blows[e], ex, (float) g, (float) z, 0, 0, 0, 3.0F, 0));
				list.add(new com.extremedimensions.network.ModPackets.AttackFxPayload.Fx(3, e, 0, ex, (float) g, (float) z, 0.25F, 1.5F, 0, 6.5F, 0));
			}
		}
		if (which == 2) {
			float gy = (float) g;
			list.add(new com.extremedimensions.network.ModPackets.AttackFxPayload.Fx(4, 2, 0, (float) x + 10, gy + 7, (float) z + 4, (float) x + 7, gy, (float) z - 2, 0, 60));
			list.add(new com.extremedimensions.network.ModPackets.AttackFxPayload.Fx(4, 2, 1, (float) x + 7, gy + 7, (float) z + 4, (float) x + 4, gy, (float) z - 2, 0, 60));
			list.add(new com.extremedimensions.network.ModPackets.AttackFxPayload.Fx(4, 1, 2, (float) x + 2, gy + 8, (float) z + 5, (float) x + 1, gy, (float) z - 1, 0, 60));
			list.add(new com.extremedimensions.network.ModPackets.AttackFxPayload.Fx(4, 2, 3, (float) x - 2, gy + 11, (float) z + 6, (float) x - 3, gy, (float) z, 0, 60));
			list.add(new com.extremedimensions.network.ModPackets.AttackFxPayload.Fx(4, 1, 4, (float) x - 6, gy + 6, (float) z + 4, (float) x - 8, gy + 1, (float) z - 1, 0, 60));
			list.add(new com.extremedimensions.network.ModPackets.AttackFxPayload.Fx(4, 0, 5, (float) x - 9, gy, (float) z - 4, (float) x + 9, gy, (float) z - 4, 0, 60));
			list.add(new com.extremedimensions.network.ModPackets.AttackFxPayload.Fx(5, 1, 0, (float) x - 1.5F, gy + 3, (float) z - 3, 0, 0, 0, 0, 9001));
			list.add(new com.extremedimensions.network.ModPackets.AttackFxPayload.Fx(5, 0, 1, (float) x + 1.5F, gy + 3, (float) z - 3, 0, 0, 0, 0, 9002));
		}
		com.extremedimensions.client.fx.AttackFx.handle(new com.extremedimensions.network.ModPackets.AttackFxPayload(1.0F, list));
	}

	/** Every mob near the player (the creatures summoned for a shot). */
	private static java.util.List<net.minecraft.world.entity.Mob> mobs(MinecraftServer s) {
		ServerPlayer p = player(s);
		return p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(24));
	}

	/** The player turns to the Elder, looking a little up at it (and no longer burning, so the shot is clear). */
	/** Puts the camera at a spot, turned to face a point across the ground, at a pitch. */
	private static void eye(TestSingleplayerContext sp, double x, double y, double z, double tx, double tz, float pitch) {
		float yaw = (float) Math.toDegrees(Math.atan2(-(tx - x), tz - z));
		sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "execute as @a at @s run tp @s %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	private static void face(TestSingleplayerContext sp, String name) {
		sp.getServer().runOnServer(s -> {
			// Nothing burning at their feet: its flames would fill the shot
			ServerPlayer p = player(s);
			for (BlockPos q : BlockPos.betweenClosed(p.blockPosition().offset(-3, -1, -3), p.blockPosition().offset(3, 3, 3))) {
				if (p.level().getBlockState(q).is(net.minecraft.tags.BlockTags.FIRE)) {
					p.level().setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
				}
			}
			p.setRemainingFireTicks(0);
		});
		sp.getServer().runCommand("execute as @a at @s facing entity @e[type=extremedimensions:" + name + ",limit=1,sort=nearest] feet run tp @s ~ ~ ~ ~ ~");
		sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ ~ -16");
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	/** Presses the button with this label on a screen. */
	private static void press(net.minecraft.client.gui.screens.Screen screen, String label) {
		for (GuiEventListener w : screen.children()) {
			if (w instanceof AbstractButton b && b.getMessage().getString().equals(label)) {
				b.onPress(null);
			}
		}
	}

	/** Presses the first button with this label, once (safe when pressing it rebuilds the screen's buttons). */
	private static boolean pressOnce(net.minecraft.client.gui.screens.Screen screen, String label) {
		for (GuiEventListener w : new ArrayList<>(screen.children())) {
			if (w instanceof AbstractButton b && b.active && b.getMessage().getString().equals(label)) {
				b.onPress(null);
				return true;
			}
		}
		return false;
	}

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new AssertionError(message);
		}
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			sp.getServer().runCommand("tether overworld_penalties on");
			sp.getServer().runCommand("gamemode survival @a");
			sp.getServer().runCommand("time set noon");
			for (String c : new String[] {"scar 2", "accelerated_drain 4", "watched 2", "kindling 3", "sealed_pockets 3",
					"shrunken_stomach 2", "glass_jaw 2", "withering 1", "dwindling 1", "unhealing 1", "restless_gaze 2"}) {
				sp.getServer().runCommand("execute as @a run tether curse " + c);
			}
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				PlayerData d = DataStore.get(p.getUUID());
				d.atonement.put("kindling", 120);
				d.atoning = "kindling";
				d.scars = 2;
				p.getInventory().clearContent();
				for (int i = 0; i < 27; i++) {
					p.getInventory().setItem(i, new ItemStack(i % 2 == 0 ? Items.COBBLESTONE : Items.OAK_LOG, 32));
				}
				p.getFoodData().setFoodLevel(20);
			});
			ctx.waitTicks(30);

			// The client knows its sealed slots, and no menu lets an item in
			ctx.runOnClient(mc -> {
				check(SealedPockets.count(mc.player) == 9, "client sees " + SealedPockets.count(mc.player) + " sealed slots, expected 9");
				check(!mc.player.inventoryMenu.getSlot(35).mayPlace(new ItemStack(Items.STONE)), "client lets an item into sealed slot 35");
				int dwindled = mc.player.inventoryMenu.getSlot(9).getMaxStackSize(new ItemStack(Items.COBBLESTONE));
				check(dwindled == 32, "client stack limit with Dwindling 1 is " + dwindled + ", expected 32");
			});

			ctx.takeScreenshot("01_hud");
			ctx.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
			ctx.waitTicks(8);
			ctx.takeScreenshot("02_inventory");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));

			// Co-op: an ally's burden (one curse full already), and the status screen with anchors opened to allies
			ctx.runOnClient(mc -> mc.gui.setScreen(new com.extremedimensions.client.screen.BurdenScreen(java.util.UUID.randomUUID(), "Wren",
					"accelerated_drain:3:4:1;cracked_armor:2:4:4;dwindling:1:3:0;glass_jaw:2:3:1;debt:1:4:0;scar:2:3:2;frailty:3:4:1;heavy_legs:1:3:3")));
			ctx.waitTicks(8);
			ctx.takeScreenshot("13_burden");
			ctx.runOnClient(mc -> mc.gui.setScreen(new com.extremedimensions.client.screen.StatusScreen(true)));
			ctx.waitTicks(8);
			ctx.takeScreenshot("13_status_shared");
			// The tribe: barter as kin (every ware, a relic and a cloth among them, one sold out for today)
			ctx.runOnClient(mc -> com.extremedimensions.client.screen.BarterScreen.show(-1, 82, "100:3:1;3:10:1;0:8:2;7:2:0;5:12:1;101:12:1;102:3:1"));
			ctx.waitTicks(8);
			ctx.takeScreenshot("14_barter_kin");
			ctx.runOnClient(mc -> com.extremedimensions.client.screen.BarterScreen.show(-1, -34, ""));
			ctx.waitTicks(8);
			ctx.takeScreenshot("14_barter_hated");
			// The journal's bestiary (a creature well known, and a great one met once) and its ledger
			com.extremedimensions.client.screen.JournalScreen[] journal = new com.extremedimensions.client.screen.JournalScreen[1];
			ctx.runOnClient(mc -> {
				journal[0] = new com.extremedimensions.client.screen.JournalScreen(java.util.Set.of("tethered", "anchor", "barter", "fray"),
						"lurker:1:4;hollowling:1:0;keeper:1:1;starved_one:0:0;thinned:1:2;veil_ray:1:0;matriarch:1:0;ferryman:0:0;weaver:0:0",
						"road:expeditions=12;road:collapses=3;road:longest=1834;road:furthest=742;road:deaths=5;road:seam_visits=4;road:fray_descents=2;"
								+ "burden:curses=19/8;burden:lifted=6;burden:scars=4;burden:pages=21/37;burden:relic=ember_heart;burden:seen=6/9;"
								+ "others:standing=trusted/47;others:bartered=9;others:raids=1;others:keepers=3;others:great=0;others:lifts_given=2;"
								+ "others:lifts_taken=1;others:burdens=1", null);
				mc.gui.setScreen(journal[0]);
				check(pressOnce(journal[0], net.minecraft.client.resources.language.I18n.get("gui.extremedimensions.journal.tab.beasts")), "the journal has no bestiary tab");
			});
			ctx.waitTicks(16);
			ctx.takeScreenshot("16_bestiary_lurker");
			ctx.runOnClient(mc -> check(com.extremedimensions.client.screen.JournalScreen.likenessError() == null,
					"the bestiary could not draw a likeness: " + com.extremedimensions.client.screen.JournalScreen.likenessError()));
			ctx.runOnClient(mc -> {
				for (int i = 0; i < 6; i++) {
					pressOnce(journal[0], "▶");
				}
			});
			ctx.waitTicks(16);
			ctx.takeScreenshot("16_bestiary_matriarch");
			ctx.runOnClient(mc -> pressOnce(journal[0], net.minecraft.client.resources.language.I18n.get("gui.extremedimensions.journal.tab.ledger")));
			ctx.waitTicks(16);
			ctx.takeScreenshot("16_ledger");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));

			// Co-op: struck down, the player lies on the ground and crawls (seen from behind), and stands again after
			sp.getServer().runOnServer(s -> com.extremedimensions.coop.FallenService.knockDown(player(s)));
			ctx.waitTicks(10);
			sp.getServer().runOnServer(s -> check(player(s).getPose() == net.minecraft.world.entity.Pose.SWIMMING,
					"a fallen player stands on the server (pose " + player(s).getPose() + ")"));
			ctx.runOnClient(mc -> {
				check(mc.player.getPose() == net.minecraft.world.entity.Pose.SWIMMING, "a fallen player stands on the client (pose " + mc.player.getPose() + ")");
				check(mc.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH) <= 1e-6,
						"a fallen player can still jump on the client");
				mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("13_fallen_crawl");
			sp.getServer().runOnServer(s -> com.extremedimensions.coop.FallenService.forget(player(s)));
			ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> check(mc.player.getPose() != net.minecraft.world.entity.Pose.SWIMMING, "back on their feet, the player still crawls"));

			// Relics: all nine in the hotbar, and the Ember Heart bound to the Tether (shown in the Curses screen)
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				int i = 0;
				for (com.extremedimensions.relic.Relic r : com.extremedimensions.relic.Relic.values()) {
					p.getInventory().setItem(i++, new ItemStack(com.extremedimensions.relic.RelicService.item(r)));
				}
				DataStore.get(p.getUUID()).relic = com.extremedimensions.relic.Relic.EMBER_HEART.id;
			});
			ctx.waitTicks(5);
			ctx.takeScreenshot("02_relics_hotbar");

			// The Curses screen: all four tabs, with players nearby
			String data = sp.getServer().computeOnServer(s -> Sync.cursesData(player(s), DataStore.get(player(s).getUUID())));
			AtomicReference<CursesScreen> screen = new AtomicReference<>();
			ctx.runOnClient(mc -> {
				String nearby = mc.player.getUUID() + ",Player0,7,2,3,4;" + UUID.randomUUID() + ",Steve,0,4,1,0;" + UUID.randomUUID() + ",Alex,12,0,0,2";
				CursesScreen cs = new CursesScreen(data + nearby, null);
				screen.set(cs);
				mc.gui.setScreen(cs);
			});
			ctx.waitTicks(8);
			for (String tab : new String[] {"Beyond", "Overworld", "Everywhere", "Extreme"}) {
				ctx.runOnClient(mc -> {
					for (GuiEventListener w : screen.get().children()) {
						if (w instanceof AbstractButton b && b.getMessage().getString().equals(tab)) {
							b.onPress(null);
						}
					}
				});
				ctx.waitTicks(4);
				ctx.takeScreenshot("03_curses_" + tab.toLowerCase());
			}
			// Colour-blind colours: the four sets stay apart (the Extreme tab is still open)
			ctx.runOnClient(mc -> ClientOptions.setColourBlind(true));
			ctx.waitTicks(4);
			ctx.takeScreenshot("03_curses_colour_blind");
			ctx.runOnClient(mc -> ClientOptions.setColourBlind(false));
			ctx.runOnClient(mc -> mc.gui.setScreen(null));

			// Pacts: open them early, swear three curses and open the Pact screen through its brand in the Curses screen
			int unlock = com.extremedimensions.config.Config.PACT_UNLOCK;
			String pacted = sp.getServer().computeOnServer(s -> {
				com.extremedimensions.config.Config.PACT_UNLOCK = 0;
				ServerPlayer p = player(s);
				String refused = com.extremedimensions.pact.PactService.swear(p, java.util.Map.of("debt", 2, "cracked_armor", 1, "glass_jaw", 1, "dwindling", 1));
				check(refused == null, "a fair pact was refused: " + refused);
				return Sync.cursesData(p, DataStore.get(p.getUUID()));
			});
			ctx.runOnClient(mc -> {
				CursesScreen cs = new CursesScreen(pacted, null);
				mc.gui.setScreen(cs);
				boolean opened = false;
				for (GuiEventListener w : cs.children()) {
					if (w instanceof AbstractButton b && b.getMessage().getString().equals(net.minecraft.client.resources.language.I18n.get("gui.extremedimensions.pact.title"))) {
						b.onPress(null);
						opened = true;
					}
				}
				check(opened, "the Curses screen has no way into the pact");
			});
			ctx.waitTicks(8);
			ctx.takeScreenshot("08_pact");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			sp.getServer().runOnServer(s -> {
				com.extremedimensions.pact.PactService.swear(player(s), java.util.Map.of());
				com.extremedimensions.config.Config.PACT_UNLOCK = unlock;
			});

			// Settings: the world's rules arrived on joining, and the owner of a singleplayer world may change them
			ctx.runOnClient(mc -> {
				check(ClientState.settingsJson != null, "the world's settings never reached the client");
				check(ClientState.settingsEditable, "the owner of a singleplayer world may not change its rules");
			});
			AtomicReference<SettingsScreen> settings = new AtomicReference<>();
			ctx.runOnClient(mc -> {
				SettingsScreen ss = new SettingsScreen(null);
				settings.set(ss);
				mc.gui.setScreen(ss);
			});
			ctx.waitTicks(8);
			ctx.takeScreenshot("07_settings_display");
			ctx.runOnClient(mc -> press(settings.get(), "World rules"));
			ctx.waitTicks(6);
			ctx.takeScreenshot("07_settings_world");
			ctx.runOnClient(mc -> {
				for (GuiEventListener w : settings.get().children()) {
					if (w instanceof EditBox box && box.getWidth() == 150) {
						box.setValue("lurker");
					}
				}
			});
			ctx.waitTicks(4);
			ctx.takeScreenshot("07_settings_search");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));

			// The HUD where the player wants it: meters and guide bottom right, timer above the hotbar, a size larger
			ctx.runOnClient(mc -> {
				ClientOptions.setMeters(ClientOptions.Corner.BOTTOM_RIGHT);
				ClientOptions.setTimer(ClientOptions.TimerPlace.ABOVE_HOTBAR);
				ClientOptions.setHudScale(125);
			});
			ctx.waitTicks(4);
			ctx.takeScreenshot("01_hud_moved");
			// The guide card has its close cross; closing switches the guide off until it is switched on again
			ctx.runOnClient(mc -> {
				check(ChargeHud.guideClose != null, "the guide card has no close cross");
				GuideCloseButton.closeGuide();
				check(!ClientOptions.guidance(), "the cross did not close the guide");
			});
			ctx.waitTicks(3);
			ctx.runOnClient(mc -> {
				check(ChargeHud.guideClose == null, "the guide card is still shown after closing it");
				ClientOptions.setGuidance(true);
			});
			ctx.runOnClient(mc -> {
				ClientOptions.setMeters(ClientOptions.Corner.TOP_LEFT);
				ClientOptions.setTimer(ClientOptions.TimerPlace.TOP);
				ClientOptions.setHudScale(100);
			});

			// The curse cards, multiplied so that one spills over
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				PlayerData d = DataStore.get(p.getUUID());
				d.pendingOffer = new ArrayList<>(List.of("glass_jaw", "grounded", "short_breath"));
				d.pendingMult = new ArrayList<>(List.of(3, 4, 2));
				d.owed.clear();
				d.owed.add("beyond");
				d.pendingPenalties = 1;
				Sync.offer(p, d);
			});
			ctx.waitTicks(40);
			ctx.takeScreenshot("04_cards");
			// A rare Extreme card among the others
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				PlayerData d = DataStore.get(p.getUUID());
				// Off-centre, so it is seen at rest (the pointer sits on the middle card)
				d.pendingOffer = new ArrayList<>(List.of("echoing_steps", "grounded", "short_breath"));
				d.pendingMult = new ArrayList<>(List.of(1, 1, 1));
				Sync.offer(p, d);
			});
			ctx.waitTicks(40);
			ctx.takeScreenshot("04_cards_extreme");
			sp.getServer().runOnServer(s -> {
				PlayerData d = DataStore.get(player(s).getUUID());
				d.pendingOffer.clear();
				d.pendingMult.clear();
				d.owed.clear();
				d.pendingPenalties = 0;
			});
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(3);

			// Another menu with the player's inventory: a villager's trades
			sp.getServer().runCommand("execute as @a at @s run summon minecraft:villager ~ ~ ~3 {NoAI:1b,VillagerData:{profession:\"minecraft:farmer\",level:2,type:\"minecraft:plains\"}}");
			ctx.waitTicks(5);
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				for (Villager v : p.level().getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(10))) {
					v.setTradingPlayer(p);
					v.openTradingScreen(p, v.getDisplayName(), 2);
					break;
				}
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("05_villager");
			sp.getServer().runOnServer(s -> player(s).closeContainer());
			sp.getServer().runCommand("kill @e[type=minecraft:villager]");
			ctx.waitTicks(3);

			// The tribe at midnight, their glow mid-beat
			sp.getServer().runCommand("time set midnight");
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 5");
			ctx.waitTicks(5);
			sp.getServer().runCommand("execute as @a at @s run summon extremedimensions:lurker ^ ^ ^6 {NoAI:1b,Rotation:[180f,0f]}");
			sp.getServer().runCommand("execute as @a at @s run summon extremedimensions:hollowling ^2.2 ^ ^3.5 {NoAI:1b,Rotation:[180f,0f]}");
			sp.getServer().runCommand("execute as @a at @s run summon extremedimensions:keeper ^-3.5 ^ ^8 {NoAI:1b,Rotation:[180f,0f]}");
			for (int i = 0; i < 4; i++) {
				ctx.waitTicks(5);
				ctx.takeScreenshot("06_tribe_" + i);
			}

			// The Elders: each alone by day, then all three at night, their lanterns, eyes and threads burning
			sp.getServer().runCommand("kill @e[type=extremedimensions:lurker]");
			sp.getServer().runCommand("kill @e[type=extremedimensions:hollowling]");
			sp.getServer().runCommand("kill @e[type=extremedimensions:keeper]");
			sp.getServer().runCommand("time set noon");
			// A spectator's free camera (no hand, no hotbar), the guide card put away
			sp.getServer().runCommand("execute as @a run gamemode spectator @s");
			ctx.runOnClient(mc -> ClientOptions.setGuidance(false));
			// (long enough for any line in the chat to fade)
			ctx.waitTicks(210);
			// The tribe's faces up close by day (masks, brows and eyes in fine pixels): name, how far off it stands (its
			// hunched head reaches toward the camera), and how far the camera rises to meet its eyes
			for (String[] kin : new String[][] {{"lurker", "4.4", "1.2"}, {"hollowling", "1.6", "-0.75"}, {"keeper", "5.5", "1.9"},
					{"starved_one", "7.4", "3.1"}, {"thinned", "3.3", "0.5"}}) {
				sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
				sp.getServer().runCommand("execute as @a at @s run summon extremedimensions:" + kin[0] + " ^ ^ ^" + kin[1]
						+ " {NoAI:1b,Rotation:[180f,0f]}");
				sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~" + kin[2] + " ~ 0 0");
				ctx.waitTicks(12);
				ctx.takeScreenshot("06_face_" + kin[0]);
				sp.getServer().runOnServer(s -> mobs(s).forEach(net.minecraft.world.entity.Entity::discard));
				sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~" + (-Double.parseDouble(kin[2])) + " ~ 0 0");
				ctx.waitTicks(5);
			}
			// name, distance, yaw (the Ferryman a little turned, to show the boat), camera height for the face
			for (String[] elder : new String[][] {{"matriarch", "22", "180", "9"}, {"ferryman", "24", "220", "8.5"}, {"weaver", "23", "180", "12.5"}}) {
				int dist = Integer.parseInt(elder[1]);
				sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
				sp.getServer().runCommand("execute as @a at @s run summon extremedimensions:" + elder[0] + " ^ ^ ^" + dist
						+ " {NoAI:1b,Rotation:[" + elder[2] + "f,0f]}");
				sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~6 ~ 0 -3");
				ctx.waitTicks(15);
				ctx.takeScreenshot("10_elder_" + elder[0]);
				// Up close, eye to eye
				double up = Double.parseDouble(elder[3]);
				sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~" + (up - 6) + " ~" + (dist - 10) + " 0 -4");
				ctx.waitTicks(12);
				ctx.takeScreenshot("10_elder_" + elder[0] + "_close");
				sp.getServer().runOnServer(s -> elders(s).forEach(net.minecraft.world.entity.Entity::discard));
				sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~" + (-up) + " ~" + (10 - dist) + " 0 0");
				ctx.waitTicks(20);
			}
			sp.getServer().runCommand("time set midnight");
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
			sp.getServer().runCommand("execute as @a at @s run summon extremedimensions:matriarch ^ ^ ^44 {NoAI:1b,Rotation:[180f,0f]}");
			sp.getServer().runCommand("execute as @a at @s run summon extremedimensions:ferryman ^24 ^ ^36 {NoAI:1b,Rotation:[135f,0f]}");
			sp.getServer().runCommand("execute as @a at @s run summon extremedimensions:weaver ^-24 ^ ^36 {NoAI:1b,Rotation:[220f,0f]}");
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~5 ~ 0 -6");
			ctx.waitTicks(30);
			ctx.takeScreenshot("10_elders_night");
			sp.getServer().runOnServer(s -> elders(s).forEach(net.minecraft.world.entity.Entity::discard));
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~-5 ~ 0 0");
			ctx.waitTicks(10);

			// The camera shake hooks into the camera (its handler is merged into Camera when the mixin applies)
			ctx.runOnClient(mc -> {
				boolean found = false;
				for (java.lang.reflect.Method m : net.minecraft.client.Camera.class.getDeclaredMethods()) {
					found |= m.getName().contains("extremedimensions$shake");
				}
				check(found, "the camera shake mixin did not apply to Camera");
			});

			// The cinema: an Elder's title card as it arrives, then each Elder deep in its ultimate under the black bars
			ctx.runOnClient(mc -> com.extremedimensions.client.fx.BossCinema.handle(
					new com.extremedimensions.network.ModPackets.BossFxPayload(com.extremedimensions.network.ModPackets.BossFxPayload.INTRO, 2, 0, 0, "")));
			sp.getServer().runCommand("execute as @a at @s run summon extremedimensions:weaver ^ ^ ^23 {NoAI:1b,Rotation:[180f,0f]}");
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~6 ~ 0 -3");
			ctx.waitTicks(30);
			ctx.takeScreenshot("11_intro_weaver");
			sp.getServer().runOnServer(s -> elders(s).forEach(net.minecraft.world.entity.Entity::discard));
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~-6 ~ 0 0");
			ctx.waitTicks(20);
			String[][] ultimates = {{"matriarch", "22", "180", "0"}, {"ferryman", "24", "220", "1"}, {"weaver", "23", "180", "2"}};
			for (String[] elder : ultimates) {
				sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
				sp.getServer().runCommand("execute as @a at @s run summon extremedimensions:" + elder[0] + " ^ ^ ^" + elder[1]
						+ " {NoAI:1b,Rotation:[" + elder[2] + "f,0f]}");
				sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~6 ~ 0 -3");
				ctx.waitTicks(2);
				sp.getServer().runOnServer(s -> {
					ServerPlayer p = player(s);
					for (com.extremedimensions.elder.ElderBoss b : p.level().getEntitiesOfClass(com.extremedimensions.elder.ElderBoss.class,
							p.getBoundingBox().inflate(48))) {
						// Played eight times over, so the shot lands deep in the ultimate
						b.setAction(com.extremedimensions.elder.ElderBoss.ULTIMATE);
						b.setTempo(8.0F);
					}
				});
				int index = Integer.parseInt(elder[3]);
				ctx.runOnClient(mc -> com.extremedimensions.client.fx.BossCinema.handle(new com.extremedimensions.network.ModPackets.BossFxPayload(
						com.extremedimensions.network.ModPackets.BossFxPayload.ULTIMATE, index, 200, 1, "gui.extremedimensions.elder.ultimate." + elder[0])));
				ctx.waitTicks(20);
				ctx.takeScreenshot("11_ultimate_" + elder[0]);
				ctx.runOnClient(mc -> com.extremedimensions.client.fx.BossCinema.reset());
				sp.getServer().runOnServer(s -> elders(s).forEach(net.minecraft.world.entity.Entity::discard));
				sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~-6 ~ 0 0");
				ctx.waitTicks(20);
			}
			// The Last Weaver's face up close (its mask, eye and crown in fine pixels)
			sp.getServer().runCommand("execute as @a at @s run summon extremedimensions:weaver ^ ^ ^9 {NoAI:1b,Rotation:[180f,0f]}");
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~13 ~ 0 4");
			ctx.waitTicks(25);
			ctx.takeScreenshot("11_weaver_face");
			sp.getServer().runOnServer(s -> elders(s).forEach(net.minecraft.world.entity.Entity::discard));
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~-13 ~ 0 0");
			ctx.waitTicks(20);
			// The Matriarch's weeping mask and the Ferryman's skull up close: name, distance, camera rise
			for (String[] elder : new String[][] {{"matriarch", "9.5", "9.9"}, {"ferryman", "7", "9.5"}}) {
				sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
				sp.getServer().runCommand("execute as @a at @s run summon extremedimensions:" + elder[0] + " ^ ^ ^" + elder[1]
						+ " {NoAI:1b,Rotation:[180f,0f]}");
				sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~" + elder[2] + " ~ 0 3");
				ctx.waitTicks(25);
				ctx.takeScreenshot("11_" + elder[0] + "_face");
				sp.getServer().runOnServer(s -> elders(s).forEach(net.minecraft.world.entity.Entity::discard));
				sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~-" + elder[2] + " ~ 0 0");
				ctx.waitTicks(20);
			}
			// A Veil Ray hanging overhead: from below by day (its pale belly, spots and trailing lights), then from above
			// at night (its veins and knots of light)
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
			sp.getServer().runCommand("execute as @a at @s run summon extremedimensions:veil_ray ^ ^4 ^6 {NoAI:1b,Rotation:[150f,0f]}");
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 -29");
			ctx.waitTicks(15);
			ctx.takeScreenshot("13_veil_ray_below");
			sp.getServer().runCommand("time set midnight");
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~7.3 ~ 0 34");
			ctx.waitTicks(15);
			ctx.takeScreenshot("13_veil_ray_above");
			sp.getServer().runOnServer(s -> mobs(s).forEach(net.minecraft.world.entity.Entity::discard));
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~-7.3 ~ 0 0");
			ctx.waitTicks(20);
			// The Elders' attacks drawn in detail: their sigils by day and by night, with what falls on them; blows landing
			// with their debris and shockwaves; threads, chains, a beam, a stream, a path of sigils and two wisps
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~8 ~ 0 48");
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> fx(mc, 0));
			ctx.waitTicks(22);
			ctx.takeScreenshot("14_fx_sigils_day");
			sp.getServer().runCommand("time set midnight");
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> fx(mc, 0));
			ctx.waitTicks(22);
			ctx.takeScreenshot("14_fx_sigils_night");
			ctx.runOnClient(mc -> fx(mc, 1));
			ctx.waitTicks(4);
			ctx.takeScreenshot("14_fx_impacts");
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> fx(mc, 2));
			ctx.waitTicks(2);
			ctx.takeScreenshot("14_fx_lines");
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~-8 ~ 0 0");
			ctx.waitTicks(30);
			sp.getServer().runCommand("execute as @a run gamemode survival @s");

			// A live fight with each Elder: it comes, its three new blows one after another, its ultimate, its death. The
			// player cannot be hurt, and the ultimate's curse is switched off so the shots after are unchanged.
			int ultimateCurses = com.extremedimensions.config.Config.ELDER_ULTIMATE_CURSES;
			com.extremedimensions.config.Config.ELDER_ULTIMATE_CURSES = 0;
			// The arena comes apart soon after each death, so the last shots of a fight catch it
			int linger = com.extremedimensions.config.Config.ELDER_ARENA_LINGER_SECONDS;
			com.extremedimensions.config.Config.ELDER_ARENA_LINGER_SECONDS = 3;
			// Built sooner than in a game, so the shots of each fight catch the whole arena
			double build = com.extremedimensions.config.Config.ELDER_ARENA_BUILD_SECONDS;
			com.extremedimensions.config.Config.ELDER_ARENA_BUILD_SECONDS = 14;
			// A view far enough for the whole arena (it is only raised where the world is loaded)
			ctx.runOnClient(mc -> mc.options.renderDistance().set(12));
			sp.getServer().runCommand("effect give @a minecraft:resistance infinite 255 true");
			sp.getServer().runCommand("effect give @a minecraft:saturation infinite 10 true");
			sp.getServer().runCommand("effect give @a minecraft:fire_resistance infinite 0 true");
			ctx.waitTicks(10);
			liveFight(ctx, sp, "matriarch", 75, s -> com.extremedimensions.elder.MatriarchService.summon(player(s), "shots", player(s).blockPosition()),
					com.extremedimensions.elder.MatriarchService::force, new int[] {com.extremedimensions.elder.Matriarch.RUSH,
							com.extremedimensions.elder.Matriarch.CATHEDRAL, com.extremedimensions.elder.Matriarch.BARRAGE},
					new String[] {"rush", "cathedral", "barrage"});
			liveFight(ctx, sp, "ferryman", 40, s -> com.extremedimensions.elder.FerrymanService.summonAt(player(s), player(s).blockPosition().offset(0, 0, 20)),
					com.extremedimensions.elder.FerrymanService::force, new int[] {com.extremedimensions.elder.Ferryman.CROSSING,
							com.extremedimensions.elder.Ferryman.GAOL, com.extremedimensions.elder.Ferryman.TIDE},
					new String[] {"crossing", "gaol", "tide"});
			liveFight(ctx, sp, "weaver", 40, s -> com.extremedimensions.elder.WeaverService.summon(player(s), player(s).blockPosition().offset(0, 0, 20)),
					com.extremedimensions.elder.WeaverService::force, new int[] {com.extremedimensions.elder.Weaver.LANCE,
							com.extremedimensions.elder.Weaver.CRADLE, com.extremedimensions.elder.Weaver.THOUSAND},
					new String[] {"lance", "cradle", "thousand"});
			com.extremedimensions.config.Config.ELDER_ULTIMATE_CURSES = ultimateCurses;
			com.extremedimensions.config.Config.ELDER_ARENA_LINGER_SECONDS = linger;
			com.extremedimensions.config.Config.ELDER_ARENA_BUILD_SECONDS = build;
			ctx.runOnClient(mc -> mc.options.renderDistance().set(5));
			sp.getServer().runCommand("effect clear @a");
			sp.getServer().runCommand("kill @e[type=minecraft:item]");
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
			ctx.runOnClient(mc -> ClientOptions.setGuidance(true));
			ctx.waitTicks(25);

			// The hush masonry: every stone of the family with its slab, stairs and wall, the rest of the family behind
			int[] home = sp.getServer().computeOnServer(s -> new int[] {player(s).getBlockX(), player(s).getBlockY(), player(s).getBlockZ()});
			sp.getServer().runOnServer(s -> {
				net.minecraft.server.level.ServerLevel lvl = (net.minecraft.server.level.ServerLevel) player(s).level();
				net.minecraft.world.level.block.Block[][] rows = {
						{ModBlocks.COBBLED_HUSH_STONE, ModBlocks.COBBLED_HUSH_STONE_SLAB, ModBlocks.COBBLED_HUSH_STONE_STAIRS, ModBlocks.COBBLED_HUSH_STONE_WALL},
						{ModBlocks.HUSH_STONE, ModBlocks.HUSH_STONE_SLAB, ModBlocks.HUSH_STONE_STAIRS, ModBlocks.HUSH_STONE_WALL},
						{ModBlocks.HUSH_BRICKS, ModBlocks.HUSH_BRICK_SLAB, ModBlocks.HUSH_BRICK_STAIRS, ModBlocks.HUSH_BRICK_WALL},
						{ModBlocks.MOSSY_HUSH_BRICKS, ModBlocks.MOSSY_HUSH_BRICK_SLAB, ModBlocks.MOSSY_HUSH_BRICK_STAIRS, ModBlocks.MOSSY_HUSH_BRICK_WALL},
						{ModBlocks.POLISHED_HUSH_STONE, ModBlocks.POLISHED_HUSH_STONE_SLAB, ModBlocks.POLISHED_HUSH_STONE_STAIRS, ModBlocks.POLISHED_HUSH_STONE_WALL},
						{ModBlocks.HUSH_TILES, ModBlocks.HUSH_TILE_SLAB, ModBlocks.HUSH_TILE_STAIRS, ModBlocks.HUSH_TILE_WALL},
						{ModBlocks.PALE_HUSH_BRICKS, ModBlocks.PALE_HUSH_BRICK_SLAB, ModBlocks.PALE_HUSH_BRICK_STAIRS, ModBlocks.PALE_HUSH_BRICK_WALL},
						{ModBlocks.POLISHED_PALE_HUSH_STONE, ModBlocks.POLISHED_PALE_HUSH_STONE_SLAB, ModBlocks.POLISHED_PALE_HUSH_STONE_STAIRS,
								ModBlocks.POLISHED_PALE_HUSH_STONE_WALL}};
				// on a floor of tiles in the open sky, away from the battlefield the fights left
				int x0 = home[0] + 292, y = 110, z = home[2] + 7;
				for (int x = x0 - 3; x <= x0 + 18; x++) {
					for (int dz = -4; dz <= 6; dz++) {
						lvl.setBlock(new BlockPos(x, y - 1, z + dz), ModBlocks.POLISHED_HUSH_STONE.defaultBlockState(), 3);
					}
				}
				for (int i = 0; i < rows.length; i++) {
					int x = x0 + i * 2;
					// two blocks with a slab on them, stairs before them stepping down toward the camera, a wall beside
					lvl.setBlock(new BlockPos(x, y, z), rows[i][0].defaultBlockState(), 3);
					lvl.setBlock(new BlockPos(x, y + 1, z), rows[i][0].defaultBlockState(), 3);
					lvl.setBlock(new BlockPos(x, y + 2, z), rows[i][1].defaultBlockState(), 3);
					lvl.setBlock(new BlockPos(x, y, z - 1), rows[i][2].defaultBlockState()
							.setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.SOUTH), 3);
					lvl.setBlock(new BlockPos(x + 1, y, z), rows[i][3].defaultBlockState(), 3);
					lvl.setBlock(new BlockPos(x + 1, y + 1, z), rows[i][3].defaultBlockState(), 3);
				}
				net.minecraft.world.level.block.Block[] rest = {ModBlocks.CRACKED_HUSH_BRICKS, ModBlocks.CHISELED_HUSH_BRICKS, ModBlocks.SILKED_HUSH_BRICKS,
						ModBlocks.HUSH_PILLAR, ModBlocks.SEAM_VEIN, ModBlocks.HUSH_PILLAR, ModBlocks.CHISELED_HUSH_BRICKS, ModBlocks.SILKED_HUSH_BRICKS};
				for (int i = 0; i < rest.length; i++) {
					for (int k = 0; k < 3; k++) {
						lvl.setBlock(new BlockPos(x0 + i * 2, y + k, z + 3), rest[i].defaultBlockState(), 3);
					}
				}
			});
			// no guide over these, and a view far enough for a colossus (chunks are not awaited: the far shots take their time)
			ctx.runOnClient(mc -> {
				ClientOptions.setGuidance(false);
				mc.options.renderDistance().set(10);
			});
			sp.getServer().runCommand("execute as @a run gamemode spectator @s");
			sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.1f %.1f %.1f 0 16", home[0] + 299.5, 113.2, home[2] - 2.5));
			ctx.waitTicks(80);
			ctx.takeScreenshot("18_masonry");
			sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.1f %.1f %.1f 0 22", home[0] + 297.5, 112.0, home[2] + 3.0));
			ctx.waitTicks(15);
			ctx.takeScreenshot("18_masonry_close");

			// A rift a Seam Needle tears in the world: across the face of the crying obsidian it was opened on, the Seam
			// hazy at the far end of its tunnel; straight on, and from aside
			int[] tear = {home[0] + 340, 110, home[2] + 7};
			sp.getServer().runOnServer(s -> {
				net.minecraft.server.level.ServerLevel lvl = (net.minecraft.server.level.ServerLevel) player(s).level();
				for (int x = tear[0] - 5; x <= tear[0] + 5; x++) {
					for (int dz = -8; dz <= 4; dz++) {
						lvl.setBlock(new BlockPos(x, tear[1] - 1, tear[2] + dz), ModBlocks.HUSH_TILES.defaultBlockState(), 3);
					}
				}
				for (int x = tear[0] - 2; x <= tear[0] + 2; x++) {
					for (int k = 0; k < 5; k++) {
						lvl.setBlock(new BlockPos(x, tear[1] + k, tear[2] + 3), (x == tear[0] && k < 3 ? net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN
								: net.minecraft.world.level.block.Blocks.OBSIDIAN).defaultBlockState(), 3);
					}
				}
				com.extremedimensions.seam.SeamService.openRift(player(s), new BlockPos(tear[0], tear[1], tear[2] + 3), net.minecraft.core.Direction.NORTH,
						new net.minecraft.world.item.ItemStack(com.extremedimensions.registry.ModItems.SEAM_NEEDLE));
			});
			ctx.waitTicks(5);
			check(sp.getServer().computeOnServer(s -> !player(s).level().getEntitiesOfClass(com.extremedimensions.seam.RiftPortal.class,
					new net.minecraft.world.phys.AABB(new BlockPos(tear[0], tear[1], tear[2])).inflate(4)).isEmpty()), "a Seam Needle's rift has no tear to see");
			// the ring stands on the floor in front of the obsidian, its middle at the camera's height
			sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.1f %.2f %.1f 0 0", tear[0] + 0.5, tear[1] + 1.75 - 1.62, tear[2] - 4.5));
			ctx.waitTicks(40);
			ctx.takeScreenshot("25_rift_world");
			sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.1f %.2f %.1f 38 4", tear[0] + 4.5, tear[1] + 1.75 - 1.62, tear[2] - 2.6));
			ctx.waitTicks(20);
			ctx.takeScreenshot("25_rift_world_aside");

			// Colossal buildings, one of each plan, each on its island as the Seam would design it (far out, in the sky)
			// and a sixth: a citadel fallen to ruin, its stone worn to moss and cracks and its crowns broken
			String[] plans = {"cathedral", "citadel", "cluster", "ziggurat", "gateway", "ruin"};
			int skyTop = 120;
			for (int k = 0; k < plans.length; k++) {
				final int kk = k;
				int cx = home[0] + 700 + k * 300, cz = home[2] + 300;
				String what = sp.getServer().computeOnServer(s -> {
					com.extremedimensions.seam.Colossus.Summary[] built = new com.extremedimensions.seam.Colossus.Summary[1];
					com.extremedimensions.seam.SeamArchitect.Plan plan = com.extremedimensions.seam.SeamService.colossus(4242L + kk * 7717L, cx, skyTop, cz, 36,
							kk % 5, kk == 5 ? com.extremedimensions.seam.Colossus.RUIN : -1, built);
					plan.placeNow((net.minecraft.server.level.ServerLevel) player(s).level());
					return built[0] + ", " + plan.size() + " blocks";
				});
				com.extremedimensions.ExtremeDimensions.LOGGER.info("Colossus shot {} ({}): {}", k, plans[k], what);
				// from a corner first (its chunks take a while to show), then from the front, nearer
				sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %d.5 %d %d.5 -45 10", cx - 60, skyTop + 40, cz - 60));
				ctx.waitTicks(160);
				ctx.takeScreenshot("18_colossus_" + plans[k] + "_corner");
				sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %d.5 %d %d.5 0 -8", cx, skyTop + 28, cz - 78));
				ctx.waitTicks(60);
				ctx.takeScreenshot("18_colossus_" + plans[k]);
				if (k == 0 || k == 2) {
					// up close on its face
					sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %d.5 %d %d.5 0 -12", cx, skyTop + 10, cz - 44));
					ctx.waitTicks(30);
					ctx.takeScreenshot("18_colossus_" + plans[k] + "_close");
				}
				if (k == 0) {
					sp.getServer().runCommand("time set midnight");
					sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %d.5 %d %d.5 0 -8", cx, skyTop + 28, cz - 78));
					ctx.waitTicks(30);
					ctx.takeScreenshot("18_colossus_night");
					sp.getServer().runCommand("time set noon");
				}
			}
			// Islands of the other kinds as the Seam designs them: rolling ground, a coast of bays and headlands, what stands
			// on it set down on levelled ground with a plinth round it, worn or broken by age; and an island from below
			String[] kinds = {"village", "loom", "plain", "halo"};
			int[] kindIds = {2, 1, 0, 5};
			for (int k = 0; k < kinds.length; k++) {
				final int kk = k;
				int cx = home[0] + 700 + (6 + k) * 300, cz = home[2] + 300;
				String what = sp.getServer().computeOnServer(s -> {
					com.extremedimensions.seam.SeamArchitect.Plan plan = com.extremedimensions.seam.SeamService.showpiece(9100L + kk * 131L, cx, skyTop, cz,
							kindIds[kk]);
					plan.placeNow((net.minecraft.server.level.ServerLevel) player(s).level());
					return plan.size() + " blocks";
				});
				com.extremedimensions.ExtremeDimensions.LOGGER.info("Island shot {} ({}): {}", k, kinds[k], what);
				sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %d.5 %d %d.5 -45 22", cx - 42, skyTop + 26, cz - 42));
				ctx.waitTicks(160);
				ctx.takeScreenshot("20_island_" + kinds[k]);
				if (k == 0 || k == 1) {
					// on the ground, among what stands there
					sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %d.5 %d %d.5 25 16", cx - 16, skyTop + 12, cz - 34));
					ctx.waitTicks(40);
					ctx.takeScreenshot("20_island_" + kinds[k] + "_close");
				}
				if (k == 2) {
					// its underside, from below and off to the side
					sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %d.5 %d %d.5 -45 -28", cx - 34, skyTop - 26, cz - 34));
					ctx.waitTicks(40);
					ctx.takeScreenshot("20_island_plain_under");
				}
			}
			ctx.runOnClient(mc -> {
				ClientOptions.setGuidance(true);
				mc.options.renderDistance().set(5);
			});
			// Back home, on top of the ground there (the last fight may have left the player in a crater, since filled)
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				p.level().getChunk(home[0] >> 4, home[2] >> 4);
				int top = p.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, home[0], home[2]);
				p.teleportTo(home[0] + 0.5, Math.max(home[1], top), home[2] + 0.5);
			});
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
			sp.getServer().runCommand("execute as @a run gamemode survival @s");
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(20);

			// Rift Trials: the screen (asked for as the button does) with a board, then taken up
			sp.getServer().runOnServer(s -> {
				com.extremedimensions.trial.TrialService.roll();
				com.extremedimensions.trial.TrialBoard board = DataStore.trials();
				int[] need = com.extremedimensions.trial.TrialService.kind(com.extremedimensions.trial.TrialService.week()).thresholds();
				board.scores.put(UUID.randomUUID().toString(), new com.extremedimensions.trial.TrialBoard.Entry("Steve", need[2] + 2, 41, 3));
				board.scores.put(UUID.randomUUID().toString(), new com.extremedimensions.trial.TrialBoard.Entry("Alex", need[1], 12, 2));
				board.scores.put(UUID.randomUUID().toString(), new com.extremedimensions.trial.TrialBoard.Entry("Noor", need[0], 88, 1));
				board.lastKind = "hunt";
				board.lastTop = new ArrayList<>(List.of(new com.extremedimensions.trial.TrialBoard.Entry("Alex", 11, 30, 3),
						new com.extremedimensions.trial.TrialBoard.Entry("Steve", 6, 2, 2)));
			});
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.runOnClient(mc -> com.extremedimensions.client.screen.TrialsScreen.request(null));
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				check(com.extremedimensions.client.screen.TrialsScreen.shown() != null, "the Trials screen did not open");
				press(com.extremedimensions.client.screen.TrialsScreen.shown(), net.minecraft.client.resources.language.I18n.get("gui.extremedimensions.trial.take_up"));
			});
			ctx.waitTicks(10);
			check(sp.getServer().computeOnServer(s -> DataStore.get(player(s).getUUID()).trialArmed), "taking the trial up did not reach the server");
			ctx.takeScreenshot("09_trials");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));

			// Into the Seam with the trial taken up: it begins, and the timer carries its line
			sp.getServer().runCommand("execute as @a run tether seam");
			// On a busy machine the server can run seconds behind the client: wait until the visitor is in the Seam
			for (int i = 0; i < 40 && !sp.getServer().computeOnServer(s -> com.extremedimensions.seam.SeamService.inSeam(player(s))); i++) {
				ctx.waitTicks(5);
			}
			ctx.waitTicks(20);
			sp.getServer().runOnServer(s -> {
				PlayerData d = DataStore.get(player(s).getUUID());
				check(d.inSeam && d.trial != null, "the trial did not begin with the visit to the Seam");
				// The Seam carries its hidden seal, so a lost Seam folder is noticed (and nothing else redesigns an island)
				net.minecraft.server.level.ServerLevel seamLevel = (net.minecraft.server.level.ServerLevel) player(s).level();
				check(seamLevel.getBlockState(com.extremedimensions.seam.SeamService.SEAL).is(net.minecraft.world.level.block.Blocks.BARRIER)
						&& DataStore.seamSealed(), "the Seam was not sealed on the first visit");
				d.trial.score = com.extremedimensions.trial.Trial.Kind.byId(d.trial.kind).thresholds()[0];
				// Out of the Return Gate: four blocks before its threshold of pale stones, facing away from it, and its
				// way (to the island its bridge runs to) remembered for the rift
				ServerPlayer p = player(s);
				double yaw = Math.toRadians(p.getYRot());
				BlockPos threshold = BlockPos.containing(p.getX() + Math.sin(yaw) * 4, p.getY() - 1, p.getZ() - Math.cos(yaw) * 4);
				check(seamLevel.getBlockState(threshold).is(ModBlocks.POLISHED_PALE_HUSH_STONE),
						"the visitor did not step out of a gate (behind them: " + seamLevel.getBlockState(threshold) + ")");
				check(DataStore.seamCells().stream().anyMatch(k -> k.startsWith("g@")), "the gate's way was not remembered");
			});
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(40);
			// The first visits: what the Seam is for, on the screen (the server says so; held in full view for the shot)
			sp.getServer().runOnServer(s -> check(DataStore.get(player(s).getUUID()).seamVisits <= com.extremedimensions.config.Config.SEAM_BRIEFING,
					"the first visit is not one the Seam explains itself on"));
			ctx.runOnClient(mc -> {
				check(ClientState.briefKind == 1, "no briefing came on arrival in the Seam");
				ClientState.briefAt = System.currentTimeMillis() - 3000;
			});
			ctx.waitTicks(2);
			ctx.takeScreenshot("26_seam_briefing");
			ctx.runOnClient(mc -> ClientState.briefAt = 0);
			ctx.waitTicks(2);
			ctx.takeScreenshot("09_trial_hud");
			// The timer as the mod's own panel, for whoever switches the boss bar off
			sp.getServer().runOnServer(s -> com.extremedimensions.config.Config.HUD_BOSS_BAR = 0);
			ctx.waitTicks(8);
			ctx.takeScreenshot("09_trial_hud_panel");
			sp.getServer().runOnServer(s -> com.extremedimensions.config.Config.HUD_BOSS_BAR = 1);
			// News as Minecraft gives it: a toast in the corner for a new journal page
			ctx.runOnClient(mc -> com.extremedimensions.client.hud.Toasts.journal(0));
			ctx.waitTicks(24);
			ctx.takeScreenshot("27_toast");
			// The mod in Minecraft's own progression: its advancement tab, and its recipes in the recipe book once their
			// ingredients are held
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getInventory().add(new ItemStack(com.extremedimensions.registry.ModItems.SEAM_SILK, 4));
				p.getInventory().add(new ItemStack(com.extremedimensions.registry.ModItems.TETHER_FRAGMENT, 2));
				p.getInventory().add(new ItemStack(ModBlocks.HUSH_STONE.asItem(), 8));
				p.inventoryMenu.broadcastChanges();
			});
			ctx.waitTicks(30);
			ctx.runOnClient(mc -> {
				var advancements = mc.player.connection.getAdvancements();
				var root = advancements.get(com.extremedimensions.ExtremeDimensions.id("root"));
				if (root != null) {
					advancements.setSelectedTab(root, true);
				}
				mc.gui.setScreen(new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(advancements));
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("28_advancements");
			ctx.runOnClient(mc -> {
				mc.player.getRecipeBook().setOpen(net.minecraft.world.inventory.RecipeBookType.CRAFTING, true);
				mc.gui.setScreen(new InventoryScreen(mc.player));
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("28_recipe_book");
			ctx.runOnClient(mc -> {
				mc.player.getRecipeBook().setOpen(net.minecraft.world.inventory.RecipeBookType.CRAFTING, false);
				mc.gui.setScreen(null);
			});
			ctx.waitTicks(4);

			// The Seam builds itself before your eyes: an arch ten blocks ahead flies in out of the nothing
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				net.minecraft.world.phys.Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
				double sideX = -look.z, sideZ = look.x;
				int bx = (int) Math.floor(p.getX() + look.x * 10), bz = (int) Math.floor(p.getZ() + look.z * 10), by = (int) Math.floor(p.getY());
				com.extremedimensions.seam.SeamArchitect.Plan arch = new com.extremedimensions.seam.SeamArchitect.Plan();
				for (int w = -3; w <= 3; w++) {
					for (int h = 0; h <= 6; h++) {
						boolean edge = Math.abs(w) == 3 || h == 6;
						if (!edge) {
							continue;
						}
						int x = (int) Math.round(bx + sideX * w), z = (int) Math.round(bz + sideZ * w);
						arch.set(x, by + h, z, (h + w) % 2 == 0 ? com.extremedimensions.registry.ModBlocks.HUSH_BRICKS.defaultBlockState()
								: net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK.defaultBlockState());
					}
				}
				// Ahead of the islands still being built around: it is what the camera looks at
				arch.queue(true);
			});
			ctx.waitTicks(9);
			check(sp.getServer().computeOnServer(s -> com.extremedimensions.seam.SeamArchitect.flying() > 0), "nothing flies in as the Seam builds itself");
			ctx.takeScreenshot("17_seam_rising");
			ctx.waitTicks(40);
			ctx.takeScreenshot("17_seam_risen");

			// The Return Gate the visitor stepped out of: from the processional way, from the side, at its threshold,
			// and from far out in the fog
			double[] gate = sp.getServer().computeOnServer(s -> {
				ServerPlayer p = player(s);
				PlayerData d = DataStore.get(p.getUUID());
				double yaw = Math.toRadians(p.getYRot());
				return new double[] {d.riftX, d.riftY, d.riftZ, -Math.sin(yaw), Math.cos(yaw), p.getX(), p.getY(), p.getZ()};
			});
			sp.getServer().runCommand("gamemode spectator @a");
			ctx.waitTicks(100);
			double gx = gate[0], gy = gate[1], gz = gate[2], ox = gate[3], oz = gate[4], sx2 = -oz, sz2 = ox;
			eye(sp, gx + ox * 27, gy + 6, gz + oz * 27, gx, gz, -16);
			ctx.waitTicks(60);
			ctx.takeScreenshot("19_gate_front");
			eye(sp, gx + ox * 19 + sx2 * 24, gy + 9, gz + oz * 19 + sz2 * 24, gx, gz, -10);
			ctx.waitTicks(50);
			ctx.takeScreenshot("19_gate_side");
			eye(sp, gx + ox * 6.5 + sx2 * 2.5, gy + 1.2, gz + oz * 6.5 + sz2 * 2.5, gx, gz, -6);
			ctx.waitTicks(40);
			ctx.takeScreenshot("19_gate_threshold");
			eye(sp, gx + ox * 170 + sx2 * 40, gy + 14, gz + oz * 170 + sz2 * 40, gx, gz, -4);
			ctx.waitTicks(60);
			ctx.takeScreenshot("19_gate_far");
			// From below and behind the gate: the underside of its rock and of the arrival island, an upside-down mountain
			eye(sp, gx - ox * 30 + sx2 * 12, gy - 26, gz - oz * 30 + sz2 * 12, gx - ox * 4, gz - oz * 4, -22);
			ctx.waitTicks(40);
			ctx.takeScreenshot("19_island_under");
			// the compass points to where the server says the rift is
			ctx.runOnClient(mc -> check(ClientState.riftKnown(), "the client does not know where the rift home is"));
			// The tear over the rift home, the world above hazy at the end of its tunnel: straight on and close, from
			// aside, from behind the gate (the tunnel runs away from whoever looks, either side), and by night up there.
			// Where the rift stands now, it having wandered since
			double[] rift = sp.getServer().computeOnServer(s -> {
				PlayerData d = DataStore.get(player(s).getUUID());
				double a = Math.toRadians(d.riftYaw);
				return new double[] {d.riftX, d.riftY, d.riftZ, -Math.sin(a), Math.cos(a)};
			});
			double rx = rift[0], ry = rift[1], rz = rift[2], fx = rift[3], fz = rift[4];
			// eye level with the middle of the ring
			double eyeY = ry + com.extremedimensions.seam.RiftPortals.homeRadius() + 0.15 - 1.62;
			eye(sp, rx + fx * 4.6, eyeY, rz + fz * 4.6, rx, rz, 0);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> check(!mc.level.getEntitiesOfClass(com.extremedimensions.seam.RiftPortal.class,
					new net.minecraft.world.phys.AABB(rx - 3, ry - 3, rz - 3, rx + 3, ry + 3, rz + 3), com.extremedimensions.seam.RiftPortal::seen).isEmpty(),
					"the rift home has no tear the visitor can see"));
			ctx.takeScreenshot("25_portal_near");
			// (the gate's passage is deep: from much further aside its pylon hides the tear)
			eye(sp, rx + fx * 6.5 - fz * 1.7, eyeY + 0.3, rz + fz * 6.5 + fx * 1.7, rx, rz, 0);
			ctx.waitTicks(20);
			ctx.takeScreenshot("25_portal_aside");
			eye(sp, rx - fx * 5.5, eyeY, rz - fz * 5.5, rx, rz, 0);
			ctx.waitTicks(20);
			ctx.takeScreenshot("25_portal_behind");
			sp.getServer().runCommand("time set midnight");
			eye(sp, rx + fx * 4.6, eyeY, rz + fz * 4.6, rx, rz, 0);
			ctx.waitTicks(50);
			ctx.takeScreenshot("25_portal_night");
			sp.getServer().runCommand("time set noon");
			eye(sp, rx + fx * 12, eyeY + 2, rz + fz * 12, rx, rz, -6);
			ctx.waitTicks(50);
			ctx.takeScreenshot("25_portal_gate");
			// Back onto the arrival island for what follows: out along the processional way, well clear of the rift (it
			// may have wandered off its threshold by now, under the Restless Rift twist, and would take a spectator home)
			sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "execute as @a at @s run tp @s %.2f %.2f %.2f", gx + ox * 9, gate[6], gz + oz * 9));
			ctx.waitTicks(10);

			// The Fray: a well at the edge of the arrival island, and down on its knot among the strands
			int[] well = sp.getServer().computeOnServer(s -> {
				ServerPlayer p = player(s);
				return com.extremedimensions.seam.SeamDepth.showcase((net.minecraft.server.level.ServerLevel) p.level(), p.getBlockX(), 96, p.getBlockZ());
			});
			double out = Math.PI / 4;
			// A spectator's eye: from out over the drop, looking back at the well and its island
			sp.getServer().runCommand("gamemode spectator @a");
			float inward = (float) Math.toDegrees(Math.atan2(Math.cos(out), -Math.sin(out)));
			double sx = well[0] + 0.5 + Math.cos(out) * 8, sz = well[2] + 0.5 + Math.sin(out) * 8;
			sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "execute as @a at @s run tp @s %.2f %d %.2f %.1f 32", sx, well[1] + 6, sz, inward));
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(30);
			ctx.takeScreenshot("15_fray_well");
			// Down in the Fray, off to the side above the knot: its landing, its lanterns and the strands running off
			sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "execute as @a at @s run tp @s %.2f %d %.2f -45 30", well[0] - 9.5, well[3] + 8, well[2] - 9.5));
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(40);
			// The first time down: what the Fray is, on the screen (held in full view for the shot)
			ctx.runOnClient(mc -> {
				check(ClientState.briefKind == 2, "no briefing came on the first descent into the Fray");
				ClientState.briefAt = System.currentTimeMillis() - 2000;
			});
			ctx.waitTicks(2);
			ctx.takeScreenshot("26_fray_briefing");
			ctx.runOnClient(mc -> ClientState.briefAt = 0);
			ctx.waitTicks(2);
			ctx.takeScreenshot("15_fray_knot");
			sp.getServer().runOnServer(s -> check(com.extremedimensions.seam.SeamDepth.inFray(player(s)), "above the knot, the player is not in the Fray"));

			// The Frayed: a Veil Ray of the Fray beside one of the islands (the same model and texture layout, painted
			// sick green with red eyes), across the view; then folded into its dive; then the two side by side
			int kx = well[0], ky = well[3], kz = well[2];
			sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "execute as @a at @s run summon extremedimensions:veil_ray %d %d %d {NoAI:1b,Rotation:[45f,0f]}", kx - 6, ky + 7, kz - 6));
			ctx.waitTicks(2);
			sp.getServer().runOnServer(s -> mobs(s).forEach(m -> {
				if (m instanceof com.extremedimensions.seam.VeilRay ray) {
					ray.setFrayed(true);
				}
			}));
			sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "execute as @a at @s run tp @s %.2f %d %.2f -45 -20", kx - 13.5, ky + 3, kz - 13.5));
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(20);
			sp.getServer().runOnServer(s -> check(com.extremedimensions.seam.SeamService.inSeam(player(s)), "the Frayed Ray is shot outside the Seam"));
			ctx.takeScreenshot("26_frayed_ray");
			sp.getServer().runOnServer(s -> mobs(s).forEach(m -> {
				if (m instanceof com.extremedimensions.seam.VeilRay ray) {
					ray.setDiving(true);
					ray.setXRot(50);
				}
			}));
			ctx.waitTicks(20);
			ctx.takeScreenshot("26_frayed_ray_dive");
			sp.getServer().runOnServer(s -> mobs(s).forEach(m -> {
				if (m instanceof com.extremedimensions.seam.VeilRay ray) {
					ray.setDiving(false);
					ray.setXRot(0);
				}
			}));
			// a calm ray beside it, for the two to be compared (from below, where their lights show)
			sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "execute as @a at @s run summon extremedimensions:veil_ray %d %d %d {NoAI:1b,Rotation:[45f,0f]}", kx - 14, ky + 7, kz + 2));
			sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "execute as @a at @s run tp @s %.2f %d %.2f -45 -38", kx - 17.5, ky + 1, kz - 9.5));
			ctx.waitTicks(25);
			ctx.takeScreenshot("26_rays_compared");
			sp.getServer().runOnServer(s -> mobs(s).forEach(net.minecraft.world.entity.Entity::discard));
		}
	}
}
