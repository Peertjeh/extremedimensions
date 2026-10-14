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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The client side in a real game window: the HUD, the inventory and other menus with sealed slots, the Curses screen,
 * the curse cards and the tribe at night. Every screen is saved as a screenshot (uploaded by CI) for review; the checks
 * that can be automated fail the run.
 */
public class ClientShots implements FabricClientGameTest {
	/** A live fight with one Elder, with a shot as it comes, at each blow, twice in its ultimate and as it dies. */
	private static void liveFight(ClientGameTestContext ctx, TestSingleplayerContext sp, String name, int settle,
			java.util.function.Consumer<MinecraftServer> summon, java.util.function.BiPredicate<net.minecraft.world.entity.Entity, Integer> force,
			int[] blows, String[] blowNames) {
		// Each fight on untouched ground, away from the last one's craters and fires
		sp.getServer().runCommand("execute as @a at @s run tp @s ~120 ~ ~ 0 0");
		ctx.waitTicks(20);
		sp.getServer().runOnServer(summon::accept);
		ctx.waitTicks(settle);
		face(sp, name);
		ctx.waitTicks(2);
		ctx.takeScreenshot("12_fight_" + name + "_arrives");
		for (int i = 0; i < blows.length; i++) {
			int blow = blows[i];
			sp.getServer().runOnServer(s -> elders(s).forEach(e -> force.test(e, blow)));
			ctx.waitTicks(28);
			face(sp, name);
			ctx.waitTicks(2);
			ctx.takeScreenshot("12_fight_" + name + "_" + blowNames[i]);
			ctx.waitTicks(45);
		}
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
		ctx.waitTicks(35);
		ctx.takeScreenshot("12_fight_" + name + "_death");
		ctx.waitTicks(50);
	}

	private static java.util.List<com.extremedimensions.elder.ElderBoss> elders(MinecraftServer s) {
		ServerPlayer p = player(s);
		return p.level().getEntitiesOfClass(com.extremedimensions.elder.ElderBoss.class, p.getBoundingBox().inflate(96));
	}

	/** The player turns to the Elder, looking a little up at it (and no longer burning, so the shot is clear). */
	private static void face(TestSingleplayerContext sp, String name) {
		sp.getServer().runOnServer(s -> player(s).setRemainingFireTicks(0));
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

			// Relics: all eight in the hotbar, and the Ember Heart bound to the Tether (shown in the Curses screen)
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
			sp.getServer().runCommand("execute as @a run gamemode survival @s");

			// A live fight with each Elder: it comes, its three new blows one after another, its ultimate, its death. The
			// player cannot be hurt, and the ultimate's curse is switched off so the shots after are unchanged.
			int ultimateCurses = com.extremedimensions.config.Config.ELDER_ULTIMATE_CURSES;
			com.extremedimensions.config.Config.ELDER_ULTIMATE_CURSES = 0;
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
			sp.getServer().runCommand("effect clear @a");
			sp.getServer().runCommand("kill @e[type=minecraft:item]");
			sp.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
			ctx.runOnClient(mc -> ClientOptions.setGuidance(true));
			ctx.waitTicks(25);

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
			ctx.waitTicks(20);
			sp.getServer().runOnServer(s -> {
				PlayerData d = DataStore.get(player(s).getUUID());
				check(d.inSeam && d.trial != null, "the trial did not begin with the visit to the Seam");
				d.trial.score = com.extremedimensions.trial.Trial.Kind.byId(d.trial.kind).thresholds()[0];
			});
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(40);
			ctx.takeScreenshot("09_trial_hud");
		}
	}
}
