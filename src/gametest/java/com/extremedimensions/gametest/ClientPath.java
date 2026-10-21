package com.extremedimensions.gametest;

import java.util.ArrayList;
import java.util.Locale;

import com.extremedimensions.client.screen.JournalScreen;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.guide.Path;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.WorldFx;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;

/**
 * The Wanderer's path walked in a real game window, from a fresh world to the Seam and back, the way a player takes
 * it: a portal, an anchor fed with coal, a Tether bound to it, through to the Nether and home, then a Seam Needle on
 * crying obsidian, into the Seam and home through the rift. At each step the journal's Path tab must name the next one,
 * and the hints for what is met on the way must come. Every step is saved as a screenshot for review.
 */
public class ClientPath implements FabricClientGameTest {
	/** The Seam's and the Fray's briefings stay on the screen a while after the path's own steps: out of the shot. */
	private static void unbrief(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> com.extremedimensions.client.state.ClientState.briefAt = 0);
		ctx.waitTicks(2);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new AssertionError(message);
		}
	}

	private static boolean pressOnce(net.minecraft.client.gui.screens.Screen screen, String label) {
		for (GuiEventListener w : new ArrayList<>(screen.children())) {
			if (w instanceof AbstractButton b && b.active && b.getMessage().getString().equals(label)) {
				b.onPress(null);
				return true;
			}
		}
		return false;
	}

	/** Waits for the chunks the client has to be drawn, and goes on if they are not in time (the scenes are close up). */
	private static void settle(TestSingleplayerContext sp) {
		try {
			sp.getConnection().waitForChunksRender(false);
		} catch (AssertionError timedOut) {
			System.out.println("[path] chunks not drawn in time; going on");
		}
	}

	/**
	 * Opens the journal as the game does (the server sends what it holds) on its Path tab, checks the step it names
	 * next, takes the screenshot, and closes it again.
	 */
	private static void path(ClientGameTestContext ctx, TestSingleplayerContext sp, Path.Step next, String hint, String shot) {
		JournalScreen before = ctx.computeOnClient(mc -> JournalScreen.last());
		sp.getServer().runOnServer(s -> {
			ServerPlayer p = player(s);
			Journal.open(p, DataStore.get(p.getUUID()));
		});
		JournalScreen opened = null;
		for (int i = 0; i < 60 && opened == null; i++) {
			ctx.waitTicks(1);
			JournalScreen now = ctx.computeOnClient(mc -> JournalScreen.last());
			opened = now != before ? now : null;
		}
		check(opened != null, "the journal did not open for " + shot);
		JournalScreen js = opened;
		ctx.runOnClient(mc -> check(pressOnce(js, I18n.get("gui.extremedimensions.journal.tab.path")), "the journal has no Path tab"));
		ctx.waitTicks(16);
		check(js.nextStep() == next, shot + ": the Path names " + js.nextStep() + " next, expected " + next);
		check(hint == null || hint.equals(js.pathHint()), shot + ": the Path says " + js.pathHint() + ", expected " + hint);
		ctx.takeScreenshot(shot);
		ctx.runOnClient(mc -> js.onClose());
		ctx.waitTicks(4);
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		// (the compatibility job runs ClientCompat alone)
		if (ClientCompat.on()) {
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			sp.getServer().runCommand("gamemode survival @a");
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			// The journal is handed out a moment after joining
			ctx.waitTicks(80);
			check(sp.getServer().computeOnServer(s -> DataStore.get(player(s).getUUID()).journalDelivered), "a new player was given no journal");

			// 1. A fresh world: the Path names the first expedition, and says what the guide card says
			path(ctx, sp, Path.Step.RETURN, "gui.extremedimensions.guide.1", "30_path_fresh");

			// 2. The first expedition, as a player makes it: a Nether portal three steps ahead, a lodestone beside it fed
			// with coal, a Tether bound to the anchor; through to the Nether (onto a glass-walled ledge) and back home
			int[] at = sp.getServer().computeOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = (ServerLevel) p.level();
				BlockPos feet = p.blockPosition();
				int x = feet.getX(), y = feet.getY(), z = feet.getZ();
				for (int dx = -4; dx <= 5; dx++) {
					for (int dz = -3; dz <= 6; dz++) {
						level.setBlockAndUpdate(new BlockPos(x + dx, y - 1, z + dz), Blocks.GRASS_BLOCK.defaultBlockState());
						for (int dy = 0; dy < 6; dy++) {
							level.setBlockAndUpdate(new BlockPos(x + dx, y + dy, z + dz), Blocks.AIR.defaultBlockState());
						}
					}
				}
				// the frame first, then the portal inside it without shape updates (an unfinished portal would go out)
				for (int dx = -1; dx <= 2; dx++) {
					for (int dy = -1; dy <= 3; dy++) {
						if (dx == -1 || dx == 2 || dy == -1 || dy == 3) {
							level.setBlockAndUpdate(new BlockPos(x + dx, y + dy, z + 3), Blocks.OBSIDIAN.defaultBlockState());
						}
					}
				}
				for (int dx = 0; dx <= 1; dx++) {
					for (int dy = 0; dy <= 2; dy++) {
						level.setBlock(new BlockPos(x + dx, y + dy, z + 3),
								Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X),
								Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					}
				}
				level.setBlockAndUpdate(new BlockPos(x + 3, y, z + 1), Blocks.LODESTONE.defaultBlockState());
				return new int[] {x, y, z};
			});
			int x = at[0], y = at[1], z = at[2];
			sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d.5 %d %d.5 0 10", x, y, z));
			ctx.waitTicks(10);
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				BlockPos stone = new BlockPos(x + 3, y, z + 1);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL_BLOCK, 16));
				com.extremedimensions.anchor.AnchorService.use(p, stone);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TETHER));
				com.extremedimensions.anchor.AnchorService.use(p, stone);
			});
			ctx.waitTicks(10);
			check(sp.getServer().computeOnServer(s -> DataStore.get(player(s).getUUID()).tetherAnchor != null),
					"feeding the lodestone and binding a Tether to it did not bind the player");
			path(ctx, sp, Path.Step.RETURN, "gui.extremedimensions.guide.3", "31_path_bound");
			settle(sp);
			ctx.takeScreenshot("31_bound_at_the_portal");

			sp.getServer().runCommand("execute in minecraft:the_nether run forceload add 0 0");
			ctx.waitTicks(20);
			sp.getServer().runCommand("execute in minecraft:the_nether run fill 3 63 3 13 70 13 minecraft:glass hollow");
			sp.getServer().runCommand("execute in minecraft:the_nether run tp @a 8.5 64 8.5 0 0");
			ctx.waitTicks(40);
			check(sp.getServer().computeOnServer(s -> DataStore.get(player(s).getUUID()).inRun), "through to the Nether, no expedition began");
			settle(sp);
			ctx.takeScreenshot("32_beyond_the_wound");
			sp.getServer().runCommand(String.format(Locale.ROOT, "execute in minecraft:overworld run tp @a %d.5 %d %d.5 0 10", x, y, z));
			ctx.waitTicks(40);
			check(sp.getServer().computeOnServer(s -> !DataStore.get(player(s).getUUID()).inRun), "home again, the expedition did not end");
			check(sp.getServer().computeOnServer(s -> WorldFx.hasAdvancement(player(s), Advancements.FIRST_EXPEDITION)),
					"home from the first expedition without its advancement");

			// 3. Back home: the Path turns to the Seam, and a Tether Fragment found brings its hint (a toast)
			path(ctx, sp, Path.Step.SEAM, "gui.extremedimensions.path.seam.craft", "33_path_seam");
			sp.getServer().runOnServer(s -> player(s).getInventory().add(new ItemStack(ModItems.TETHER_FRAGMENT, 2)));
			ctx.waitTicks(50);
			check(sp.getServer().computeOnServer(s -> DataStore.get(player(s).getUUID()).hints.contains("fragment")),
					"a Tether Fragment in hand brought no hint");
			ctx.takeScreenshot("33_hint_fragment");

			// 4. A Seam Needle (its hint too), used on crying obsidian, and into the tear
			sp.getServer().runOnServer(s -> player(s).getInventory().add(new ItemStack(ModItems.SEAM_NEEDLE)));
			ctx.waitTicks(50);
			path(ctx, sp, Path.Step.SEAM, "gui.extremedimensions.path.seam.tear", "34_path_needle");
			ctx.takeScreenshot("34_hint_needle");
			BlockPos crying = new BlockPos(x - 3, y, z + 2);
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				((ServerLevel) p.level()).setBlockAndUpdate(crying, Blocks.CRYING_OBSIDIAN.defaultBlockState());
				com.extremedimensions.seam.SeamService.openRift(p, crying, Direction.NORTH, new ItemStack(ModItems.SEAM_NEEDLE));
			});
			ctx.waitTicks(10);
			sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d.5 %d.1 %d.5 180 0", crying.getX(), crying.getY(), crying.getZ() - 1));
			ctx.waitTicks(20);
			check(sp.getServer().computeOnServer(s -> DataStore.get(player(s).getUUID()).inSeam), "walking into the tear did not lead into the Seam");
			ctx.waitTicks(80);
			settle(sp);
			ctx.takeScreenshot("35_seam_arrival");
			path(ctx, sp, Path.Step.SEAM_HOME, "gui.extremedimensions.path.seam_home", "35_path_in_the_seam");

			// 5. Home through the rift the visitor arrived by: the Path turns to the seal
			double[] rift = sp.getServer().computeOnServer(s -> {
				PlayerData d = DataStore.get(player(s).getUUID());
				return new double[] {d.riftX, d.riftY, d.riftZ};
			});
			// (as the visitor, so in the Seam: from the server's own seat a bare tp lands in the Overworld)
			sp.getServer().runCommand(String.format(Locale.ROOT, "execute as @a at @s run tp @s %.2f %.2f %.2f", rift[0], rift[1], rift[2]));
			ctx.waitTicks(30);
			check(sp.getServer().computeOnServer(s -> !DataStore.get(player(s).getUUID()).inSeam), "stepping into the rift home did not lead home");
			check(sp.getServer().computeOnServer(s -> WorldFx.hasAdvancement(player(s), Advancements.SEAM_RETURNED)),
					"home from the Seam without its advancement");
			settle(sp);
			path(ctx, sp, Path.Step.SEAL, "gui.extremedimensions.path.seal", "36_path_seal");

			// 6. The First Weaver's trail begins: a frame of Reinforced Deepslate ahead, strung with silk, shows what it held
			int fx = x - 1, fz = z - 6;
			sp.getServer().runOnServer(s -> {
				ServerLevel level = (ServerLevel) player(s).level();
				for (int dx = -3; dx <= 3; dx++) {
					for (int dy = 0; dy <= 5; dy++) {
						BlockPos q = new BlockPos(fx + dx, y + dy, fz);
						level.setBlockAndUpdate(q, Math.abs(dx) == 3 || dy == 5 ? Blocks.REINFORCED_DEEPSLATE.defaultBlockState() : Blocks.AIR.defaultBlockState());
					}
				}
			});
			sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d.5 %d %d.5 180 -8", fx, y, fz + 7));
			ctx.waitTicks(10);
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				ItemStack silk = new ItemStack(ModItems.SEAM_SILK, 4);
				p.setItemInHand(InteractionHand.MAIN_HAND, silk);
				com.extremedimensions.lore.FirstWeaver.string(p, new BlockPos(fx - 3, y + 2, fz), silk);
			});
			check(sp.getServer().computeOnServer(s -> DataStore.get(player(s).getUUID()).frameStrung), "the silk did not string the frame");
			ctx.waitTicks(60);
			unbrief(ctx);
			ctx.takeScreenshot("37_frame_vision");
			ctx.waitTicks(75);
			ctx.takeScreenshot("37_frame_weave");

			// 7. The First Knot, built at once out in the Seam's dark, from outside and from its chamber, and the Knot Mother
			// pulled loose from its heart (kept from harm meanwhile: the Seam's Thinned do not wait for the shots)
			sp.getServer().runCommand("effect give @a minecraft:resistance infinite 255 true");
			sp.getServer().runOnServer(s -> com.extremedimensions.seam.SeamService.summon(player(s)));
			ctx.waitTicks(40);
			BlockPos heart = sp.getServer().computeOnServer(s -> {
				ServerPlayer p = player(s);
				return com.extremedimensions.seam.FirstKnot.showcase((ServerLevel) p.level(), p.getBlockX() + 260, 40, p.getBlockZ());
			});
			sp.getServer().runCommand("gamemode spectator @a");
			sp.getServer().runCommand(String.format(Locale.ROOT, "execute as @a at @s run tp @s %d.5 %d %d.5 -90 4", heart.getX() - 40, heart.getY() + 12, heart.getZ()));
			ctx.waitTicks(60);
			settle(sp);
			unbrief(ctx);
			ctx.takeScreenshot("38_first_knot");
			sp.getServer().runCommand(String.format(Locale.ROOT, "execute as @a at @s run tp @s %d.5 %d %d.5 -90 10", heart.getX() - 12, heart.getY() + 3, heart.getZ()));
			ctx.waitTicks(30);
			unbrief(ctx);
			ctx.takeScreenshot("38_first_knot_chamber");
			sp.getServer().runCommand("gamemode survival @a");
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getAbilities().invulnerable = true;
				com.extremedimensions.elder.WeaverService.summonKnotMother(p, heart);
			});
			ctx.waitTicks(50);
			unbrief(ctx);
			ctx.takeScreenshot("39_knot_mother_forming");
			ctx.waitTicks(140);
			check(sp.getServer().computeOnServer(s -> com.extremedimensions.elder.WeaverService.knotMotherAwake()), "the Knot Mother did not wake");
			ctx.takeScreenshot("39_knot_mother");
			sp.getServer().runOnServer(s -> {
				for (com.extremedimensions.elder.KnotMother m : player(s).level().getEntitiesOfClass(com.extremedimensions.elder.KnotMother.class,
						player(s).getBoundingBox().inflate(96))) {
					com.extremedimensions.elder.WeaverService.fade(m);
				}
				player(s).getAbilities().invulnerable = false;
			});

			// 8. Now it is yours to finish: a Weaver's Loom on a floor over the Fray, its tapestry five panels woven; the page
			// in the player's own hand; the sixth panel set, the Weaving held, and New Thread+ offered
			BlockPos loomAt = sp.getServer().computeOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = (ServerLevel) p.level();
				p.getAbilities().invulnerable = true;
				DataStore.get(p.getUUID()).seamLeft = 9999;
				DataStore.setEnding("mended");
				DataStore.setLoom("");
				DataStore.setTapestryDone(false);
				DataStore.panels().clear();
				for (com.extremedimensions.tapestry.Tapestry.Panel panel : com.extremedimensions.tapestry.Tapestry.Panel.values()) {
					if (panel != com.extremedimensions.tapestry.Tapestry.Panel.HEART) {
						DataStore.panels().add(panel.id);
					}
				}
				BlockPos c = p.blockPosition().above(28);
				for (int dx = -7; dx <= 7; dx++) {
					for (int dz = -7; dz <= 7; dz++) {
						level.setBlockAndUpdate(c.offset(dx, -1, dz), com.extremedimensions.registry.ModBlocks.POLISHED_HUSH_STONE.defaultBlockState());
						for (int dy = 0; dy < 5; dy++) {
							level.setBlockAndUpdate(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
						}
					}
				}
				BlockPos at = c.offset(0, 0, -3);
				level.setBlockAndUpdate(at, com.extremedimensions.registry.ModBlocks.WEAVERS_LOOM.defaultBlockState());
				return at;
			});
			sp.getServer().runCommand(String.format(Locale.ROOT, "execute as @a at @s run tp @s %.2f %d %.2f 180 24", loomAt.getX() + 0.5, loomAt.getY(), loomAt.getZ() + 4.5));
			ctx.waitTicks(40);
			unbrief(ctx);
			ctx.takeScreenshot("41_weavers_loom");
			sp.getServer().runOnServer(s -> com.extremedimensions.tapestry.Tapestry.use(player(s), loomAt, ItemStack.EMPTY));
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> check(com.extremedimensions.client.screen.TapestryScreen.shown() != null, "the loom showed no tapestry"));
			ctx.takeScreenshot("41_tapestry");
			check(sp.getServer().computeOnServer(s -> DataStore.get(player(s).getUUID()).journal.contains(com.extremedimensions.journal.JournalEntry.CONTINUED_MENDED.id)),
					"the loom after the ending gave no page in the player's own hand");
			sp.getServer().runOnServer(s -> Journal.open(player(s), DataStore.get(player(s).getUUID())));
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				if (mc.screen instanceof JournalScreen j) {
					j.toLastPage();
				}
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("41_own_hand");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			// The sixth panel: the Weaving begins; its first wave comes out of the rifts round the loom
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = player(s);
				ItemStack heart = new ItemStack(ModItems.THREAD_OF_THE_HEART);
				p.setItemInHand(InteractionHand.MAIN_HAND, heart);
				com.extremedimensions.tapestry.Tapestry.use(p, loomAt, heart);
			});
			check(sp.getServer().computeOnServer(s -> com.extremedimensions.tapestry.Weaving.active()), "the sixth panel did not begin the Weaving");
			ctx.waitTicks(100);
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(2);
			ctx.takeScreenshot("42_weaving");
			sp.getServer().runOnServer(s -> com.extremedimensions.tapestry.Weaving.finishNow(true));
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(40);
			ctx.takeScreenshot("42_weaving_held");
			check(sp.getServer().computeOnServer(s -> DataStore.tapestryDone()), "the held Weaving did not finish the tapestry");
			sp.getServer().runOnServer(s -> com.extremedimensions.tapestry.Tapestry.open(player(s)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("43_tapestry_finished");
			ctx.runOnClient(mc -> check(mc.screen != null && pressOnce(mc.screen, I18n.get("gui.extremedimensions.tapestry.new_thread_button")),
					"the finished tapestry offers no New Thread+"));
			ctx.waitTicks(5);
			ctx.takeScreenshot("43_new_thread");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			sp.getServer().runOnServer(s -> {
				player(s).getAbilities().invulnerable = false;
				DataStore.setEnding("");
				DataStore.panels().clear();
				DataStore.setTapestryDone(false);
				DataStore.setLoom("");
				DataStore.setLastPanel("");
			});
		}
	}
}
