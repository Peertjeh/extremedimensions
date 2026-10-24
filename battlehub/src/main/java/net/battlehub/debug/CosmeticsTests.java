package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.p;

import java.util.List;

import net.battlehub.BattleHub;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.gui.CosmeticCategoryMenu;
import net.battlehub.gui.CosmeticsMenu;
import net.battlehub.gui.HubMenu;
import net.battlehub.gui.MainMenu;
import net.battlehub.progression.Cosmetics;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

/**
 * The cosmetics shop: buying through the menu, wearing and taking off, too expensive, the name colour on the
 * scoreboard team and every effect. A and B keep their cosmetics, so the duels after this play the kill, win and
 * trail effects in real matches.
 */
final class CosmeticsTests {
	private CosmeticsTests() {}

	private static PlayerProfile prof(String name) {
		return PlayerStore.get(p(name).getUUID());
	}

	/** Clicks the item with this name in the open menu of {@code player}. */
	private static void click(String player, String itemName) {
		ServerPlayer pl = p(player);
		HubMenu m = (HubMenu) pl.containerMenu;
		for (int i = 0; i < m.getRowCount() * 9; i++) {
			if (m.getSlot(i).getItem().getHoverName().getString().equals(itemName)) {
				m.clicked(i, 0, ContainerInput.PICKUP, pl);
				return;
			}
		}
		throw new IllegalStateException(itemName + " is not in the menu");
	}

	private static void command(MinecraftServer server, String cmd) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
	}

	static void all(MinecraftServer server, Script s) {
		coinsCommand(server, s);
		shop(server, s);
		more(server, s);
		custom(server, s);
	}

	/** 1.44: a topper and a balloon go with A, a badge after A's name, a gadget in the hotbar with a cooldown. */
	private static void custom(MinecraftServer server, Script s) {
		String tag = "cosmetics custom";
		s.run(tag + ": A buys a crown, a red balloon, a star badge and a confetti cannon", () -> {
			prof("BHTestA").coins += 50000;
			for (String id : List.of("topper_crown", "balloon_red", "badge_star", "gadget_confetti")) {
				Cosmetics.click(p("BHTestA"), Cosmetics.byId(id));
			}
			p("BHTestA").closeContainer();
		});
		s.waitFor(tag + ": the crown sits on A's head, every part of it", 40, () -> {
			ServerPlayer a = p("BHTestA");
			int parts = net.battlehub.core.Toppers.MODELS.get("crown").parts().size();
			if (net.battlehub.progression.Wearables.parts(a.getUUID()) != parts) return false;
			net.minecraft.world.phys.Vec3 head = a.position().add(0, a.getBbHeight(), 0);
			return a.level().getEntitiesOfClass(net.minecraft.world.entity.Display.class, a.getBoundingBox().inflate(2),
					e -> Boolean.TRUE.equals(net.battlehub.util.Tags.has(e, net.battlehub.progression.Wearables.TAG)) && e.position().distanceTo(head) < 1.0).size() >= parts;
		});
		s.waitFor(tag + ": the balloon floats over A on its string", 60, () -> {
			net.minecraft.world.entity.Entity b = net.battlehub.progression.Wearables.balloon(p("BHTestA").getUUID());
			return b != null && b.position().distanceTo(p("BHTestA").position()) < net.battlehub.core.Balloons.STRING + 1.5
					&& b.getY() > p("BHTestA").getY() + 1.5;
		});
		s.check(tag + ": a star after A's name (team suffix)", () -> {
			var team = server.getScoreboard().getPlayersTeam(p("BHTestA").getScoreboardName());
			return team != null && team.getPlayerSuffix().getString().contains("★");
		});
		s.waitFor(tag + ": the confetti cannon is in A's hotbar", 100, () ->
				net.battlehub.util.Icon.tagOf(p("BHTestA").getInventory().getItem(net.battlehub.lobby.HubItems.GADGET_SLOT)).equals(net.battlehub.lobby.HubItems.GADGET + "confetti"));
		s.run(tag + ": A fires it twice in a row", () -> {
			VARS().put("gadgets", net.battlehub.progression.GadgetFx.used());
			net.battlehub.progression.GadgetFx.use(p("BHTestA"));
			net.battlehub.progression.GadgetFx.use(p("BHTestA"));
		});
		s.check(tag + ": once fired, then it waits", () -> net.battlehub.progression.GadgetFx.used() == (int) VARS().get("gadgets") + 1
				&& net.battlehub.progression.GadgetFx.ticksLeft(p("BHTestA"), "confetti") > 0);
		s.run(tag + ": every gadget plays once", () -> {
			for (String g : net.battlehub.core.Gadgets.COOLDOWN.keySet()) net.battlehub.progression.GadgetFx.play(p("BHTestA"), g);
		});
		s.pause(110);
		s.check(tag + ": every particle and sound of the gadgets exists, every new cosmetic is valid", () -> {
			List<String> problems = Cosmetics.problems();
			if (!problems.isEmpty()) BattleHub.LOGGER.warn("SELFTEST cosmetics problems: {}", problems);
			return problems.isEmpty();
		});
		s.run(tag + ": A goes back to survival", () -> net.battlehub.hub.Travel.leave(p("BHTestA")));
		s.waitFor(tag + ": the crown and the balloon stay behind and go", 10, () -> net.battlehub.progression.Wearables.parts(p("BHTestA").getUUID()) == 0
				&& net.battlehub.progression.Wearables.balloon(p("BHTestA").getUUID()) == null);
		s.run(tag + ": A comes back and takes everything off", () -> {
			net.battlehub.hub.Travel.enter(p("BHTestA"));
			for (String c : List.of("topper", "balloon", "badge", "gadget")) Cosmetics.clear(p("BHTestA"), c);
		});
		s.waitFor(tag + ": nothing left on A, no star", 40, () -> {
			var team = server.getScoreboard().getPlayersTeam(p("BHTestA").getScoreboardName());
			return net.battlehub.progression.Wearables.parts(p("BHTestA").getUUID()) == 0
					&& (team == null || !team.getPlayerSuffix().getString().contains("★"));
		});
	}

	/** Auras, pets, weapon skins, death cries, fading outfits and the firework and lightning effects. */
	private static void more(MinecraftServer server, Script s) {
		String tag = "cosmetics more";
		s.run(tag + ": A gets coins and buys angel wings, a baby dragon, a blaze rod skin, a goat cry and a sunset outfit", () -> {
			prof("BHTestA").coins += 50000;
			VARS().put("auraFrames", net.battlehub.progression.Auras.frames());
			VARS().put("spectacles", net.battlehub.fx.Spectacle.shown());
			for (String id : List.of("aura_angel", "pet_dragon", "skin_blaze", "cry_goat", "outfit_sunset")) {
				Cosmetics.click(p("BHTestA"), Cosmetics.byId(id));
			}
			p("BHTestA").closeContainer();
		});
		s.check(tag + ": all five in use", () -> List.of("aura", "pet", "skin", "cry", "outfit").stream()
				.allMatch(c -> prof("BHTestA").cosmetics.containsKey(c)));
		s.waitFor(tag + ": the wings are drawn round A", 40, () -> net.battlehub.progression.Auras.frames() > (int) VARS().get("auraFrames") + 3);
		s.waitFor(tag + ": the dragon floats by A's shoulder", 40, () -> {
			net.minecraft.world.entity.Entity pet = net.battlehub.progression.Pets.entity(p("BHTestA").getUUID());
			return pet != null && !pet.isRemoved() && pet.position().distanceTo(p("BHTestA").position()) < 2.5;
		});
		s.check(tag + ": the outfit fades from orange at the top to purple at the feet", () ->
				(net.minecraft.world.item.component.DyedItemColor.getOrDefault(p("BHTestA").getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST), 0) & 0xFFFFFF) == 0xFFB000
						&& (net.minecraft.world.item.component.DyedItemColor.getOrDefault(p("BHTestA").getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET), 0) & 0xFFFFFF) == 0x8A2BE2);
		s.check(tag + ": a sword from a kit looks like a blaze rod and shines; a bow stays a bow", () -> {
			ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
			ItemStack bow = new ItemStack(Items.BOW);
			Cosmetics.applySkin(p("BHTestA"), sword);
			Cosmetics.applySkin(p("BHTestA"), bow);
			return net.battlehub.util.Ids.id("minecraft:blaze_rod").equals(sword.get(net.minecraft.core.component.DataComponents.ITEM_MODEL))
					&& Boolean.TRUE.equals(sword.get(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE))
					&& !net.battlehub.util.Ids.id("minecraft:blaze_rod").equals(bow.get(net.minecraft.core.component.DataComponents.ITEM_MODEL));
		});
		s.run(tag + ": the cry, a firework burst and a lightning bolt play", () -> {
			ServerPlayer a = p("BHTestA");
			Cosmetics.cry(a.level(), Cosmetics.byId("cry_goat").effect, a.position());
			Cosmetics.killEffect("rocket", a.level(), a.position());
			Cosmetics.killEffect("smite", a.level(), a.position());
		});
		s.check(tag + ": the firework and the bolt were shown (on the clients only)", () ->
				net.battlehub.fx.Spectacle.shown() >= (int) VARS().get("spectacles") + 2);
		s.run(tag + ": A goes back to survival", () -> net.battlehub.hub.Travel.leave(p("BHTestA")));
		s.waitFor(tag + ": the pet stays behind in the hub and goes", 10, () -> !net.battlehub.progression.Pets.has(p("BHTestA").getUUID()));
		s.run(tag + ": A comes back", () -> net.battlehub.hub.Travel.enter(p("BHTestA")));
		s.waitFor(tag + ": the pet is back", 40, () -> net.battlehub.progression.Pets.has(p("BHTestA").getUUID()));
		s.run(tag + ": A takes off the pet, wings, skin, cry and outfit (the duels after this use the earlier ones)", () -> {
			for (String c : List.of("aura", "pet", "skin", "cry", "outfit")) {
				Cosmetics.clear(p("BHTestA"), c);
			}
		});
		s.waitFor(tag + ": the pet is gone", 10, () -> !net.battlehub.progression.Pets.has(p("BHTestA").getUUID())
				&& p("BHTestA").getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty());
	}

	private static java.util.Map<String, Object> VARS() {
		return SelfTest.VARS;
	}

	/** /battlehub coins as an admin (the server console): give, take (never below zero), set, also offline. */
	private static void coinsCommand(MinecraftServer server, Script s) {
		String tag = "coins command";
		s.run(tag + ": set A to 1000, give 250", () -> {
			SelfTest.join(server, "BHTestA");
			command(server, "battlehub coins set BHTestA 1000");
			command(server, "battlehub coins give BHTestA 250");
		});
		s.check(tag + ": A has 1250", () -> prof("BHTestA").coins == 1250);
		s.run(tag + ": take more than A has", () -> command(server, "battlehub coins take BHTestA 99999"));
		s.check(tag + ": A has 0, not less", () -> prof("BHTestA").coins == 0);
		s.run(tag + ": a name BattleHub does not know changes nothing", () -> command(server, "battlehub coins give NoSuchPlayer 5"));
		s.check(tag + ": still 0", () -> prof("BHTestA").coins == 0 && net.battlehub.data.PlayerStore.find("NoSuchPlayer") == null);
	}

	private static void shop(MinecraftServer server, Script s) {
		String tag = "cosmetics";
		SelfTest.reset(s, tag);
		s.run(tag + ": A has 5000 coins, B none", () -> {
			SelfTest.join(server, "BHTestA");
			SelfTest.join(server, "BHTestB");
			for (String n : List.of("BHTestA", "BHTestB")) {
				prof(n).ownedCosmetics.clear();
				prof(n).cosmetics.clear();
			}
			prof("BHTestA").coins = 5000;
			prof("BHTestB").coins = 0;
			new MainMenu(p("BHTestA")).open();
		});
		s.check(tag + ": the compass menu has a cosmetics button", () -> p("BHTestA").containerMenu instanceof HubMenu m
				&& m.getSlot(13).getItem().is(Items.FIREWORK_ROCKET));
		s.run(tag + ": A opens the shop", () -> click("BHTestA", "Cosmetics"));
		s.waitFor(tag + ": sixteen categories (trails to gadgets)", 5, () -> {
			if (!(p("BHTestA").containerMenu instanceof HubMenu m) || !(m.screen() instanceof CosmeticsMenu)) return false;
			for (int slot : net.battlehub.core.MenuSlots.centered(16, 1, 6)) {
				ItemStack it = m.getSlot(slot).getItem();
				if (it.isEmpty() || it.is(net.battlehub.util.Ids.item("minecraft:black_stained_glass_pane"))) return false;
			}
			return true;
		});
		s.run(tag + ": A opens trails", () -> click("BHTestA", "Trails"));
		s.waitFor(tag + ": the trails page", 5, () -> p("BHTestA").containerMenu instanceof HubMenu m && m.screen() instanceof CosmeticCategoryMenu);
		s.run(tag + ": A clicks Flames (400 coins)", () -> click("BHTestA", "Flames"));
		s.waitFor(tag + ": bought, paid and in use", 5, () -> prof("BHTestA").ownedCosmetics.contains("trail_flames")
				&& prof("BHTestA").coins == 4600 && "trail_flames".equals(prof("BHTestA").cosmetics.get("trail")));
		s.run(tag + ": A clicks Flames again", () -> click("BHTestA", "Flames"));
		s.waitFor(tag + ": turned off, still owned", 5, () -> !prof("BHTestA").cosmetics.containsKey("trail")
				&& prof("BHTestA").ownedCosmetics.contains("trail_flames"));
		s.run(tag + ": and once more", () -> click("BHTestA", "Flames"));
		s.waitFor(tag + ": in use again without paying twice", 5, () -> "trail_flames".equals(prof("BHTestA").cosmetics.get("trail"))
				&& prof("BHTestA").coins == 4600);
		s.run(tag + ": B tries Lightning without coins", () -> Cosmetics.click(p("BHTestB"), Cosmetics.byId("kill_lightning")));
		s.check(tag + ": refused, nothing paid", () -> !prof("BHTestB").ownedCosmetics.contains("kill_lightning")
				&& prof("BHTestB").coins == 0 && !prof("BHTestB").cosmetics.containsKey("kill"));
		s.run(tag + ": A buys a red name, Lightning, fire arrows and fireworks", () -> {
			for (String id : List.of("color_red", "kill_lightning", "arrow_flames", "win_fireworks")) {
				Cosmetics.click(p("BHTestA"), Cosmetics.byId(id));
			}
			p("BHTestA").closeContainer();
		});
		s.check(tag + ": all paid (5000 - 3500)", () -> prof("BHTestA").coins == 1500 && prof("BHTestA").cosmetics.size() == 5);
		s.check(tag + ": A's name is red on the nametag, in chat and in the tab list", () -> {
			PlayerTeam team = server.getScoreboard().getPlayersTeam(p("BHTestA").getScoreboardName());
			return team != null && team.getColor().equals(java.util.Optional.of(TeamColor.RED));
		});
		s.run(tag + ": B earns coins and buys a few", () -> {
			prof("BHTestB").coins = 3000;
			for (String id : List.of("trail_notes", "kill_blood", "win_hearts", "color_aqua")) {
				Cosmetics.click(p("BHTestB"), Cosmetics.byId(id));
			}
		});
		s.check(tag + ": B paid 2100", () -> prof("BHTestB").coins == 900 && prof("BHTestB").cosmetics.size() == 4);
		s.run(tag + ": every kill and win effect plays once", () -> {
			ServerPlayer a = p("BHTestA");
			ServerLevel level = a.level();
			for (String e : Cosmetics.KILL_EFFECTS) {
				Cosmetics.killEffect(e, level, a.position());
			}
			for (int t = 0; t <= 40; t += 10) {
				for (String e : Cosmetics.WIN_EFFECTS) {
					Cosmetics.winEffect(e, level, a.position(), t);
				}
			}
		});
		s.check(tag + ": every particle, effect, colour and icon exists", () -> {
			List<String> problems = Cosmetics.problems();
			if (!problems.isEmpty()) {
				BattleHub.LOGGER.warn("SELFTEST cosmetics problems: {}", problems);
			}
			return problems.isEmpty();
		});
		s.run(tag + ": A walks with a trail", () -> {
			ServerPlayer a = p("BHTestA");
			a.teleportTo(a.getX() + 1, a.getY(), a.getZ());
		});
		s.pause(3);
		s.run(tag + ": A takes the red name off in the shop", () -> {
			new CosmeticCategoryMenu(p("BHTestA"), "color", 0).open();
			click("BHTestA", "None");
		});
		s.waitFor(tag + ": A's name has no colour any more", 5, () -> {
			PlayerTeam team = server.getScoreboard().getPlayersTeam(p("BHTestA").getScoreboardName());
			return !prof("BHTestA").cosmetics.containsKey("color") && (team == null || team.getColor().isEmpty());
		});
		s.run(tag + ": A wears it again for the duels", () -> {
			Cosmetics.click(p("BHTestA"), Cosmetics.byId("color_red"));
			p("BHTestA").closeContainer();
		});
		s.run(tag + ": A goes back to survival", () -> net.battlehub.hub.Travel.leave(p("BHTestA")));
		s.check(tag + ": no BattleHub colour in the normal world", () -> {
			PlayerTeam team = server.getScoreboard().getPlayersTeam(p("BHTestA").getScoreboardName());
			return team == null || team.getColor().isEmpty();
		});
		s.run(tag + ": A comes back with /hub", () -> net.battlehub.hub.Travel.enter(p("BHTestA")));
		s.check(tag + ": red again in BattleHub", () -> {
			PlayerTeam team = server.getScoreboard().getPlayersTeam(p("BHTestA").getScoreboardName());
			return team != null && team.getColor().equals(java.util.Optional.of(TeamColor.RED));
		});
	}
}
