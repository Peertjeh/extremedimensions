package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.VARS;
import static net.battlehub.debug.SelfTest.p;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.battlehub.config.ConfigManager;
import net.battlehub.core.CosmeticDef;
import net.battlehub.core.CosmeticRules;
import net.battlehub.core.DummyHits;
import net.battlehub.core.GameMode;
import net.battlehub.core.PixelIcons;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.fx.Cinematics;
import net.battlehub.fx.Glyphs;
import net.battlehub.fx.Killfeed;
import net.battlehub.fx.ResourcePack;
import net.battlehub.hub.HubDummy;
import net.battlehub.lobby.DoubleJump;
import net.battlehub.lobby.Lobby;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.progression.Cosmetics;
import net.battlehub.progression.Wardrobe;
import net.battlehub.session.Sessions;
import net.battlehub.util.Icon;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.phys.Vec3;

/** The lobby extras (double jump, training dummy, wardrobe), the match show (intro, killfeed, victory shot) and the resource pack. */
final class FxTests {
	private FxTests() {}

	private static final String A = "BHTestA";
	private static final String B = "BHTestB";

	private static PlayerProfile prof(String n) {
		return PlayerStore.get(p(n));
	}

	private static Match match() {
		return (Match) VARS.get("match");
	}

	static void all(MinecraftServer server, Script s) {
		lobby(server, s);
		wardrobe(server, s);
		show(server, s);
		pack(server, s);
	}

	private static void lobby(MinecraftServer server, Script s) {
		String tag = "lobby extras";
		SelfTest.reset(s, tag);
		s.run(tag + ": A is in the lobby", () -> {
			SelfTest.join(server, A);
			net.battlehub.lobby.Lobby.teleport(p(A));
		});
		s.waitFor(tag + ": standing on the plaza, A may double jump", 200, () -> DoubleJump.granted(p(A).getUUID())
				&& p(A).getAbilities().mayfly);
		s.run(tag + ": A presses jump twice", () -> DoubleJump.jumpForTest(p(A)));
		s.waitFor(tag + ": launched: no more flying until A lands", 5, () -> !DoubleJump.granted(p(A).getUUID())
				&& !p(A).getAbilities().mayfly && !p(A).getAbilities().flying);
		s.waitFor(tag + ": landed, the double jump is back", 200, () -> DoubleJump.granted(p(A).getUUID()));
		s.waitFor(tag + ": the dummy stands on the plaza and A sees it", 200, () -> HubDummy.hitbox() != null
				&& !HubDummy.hitbox().isRemoved() && HubDummy.showing(p(A).getUUID()));
		s.run(tag + ": A stands in front of the dummy, fully charged, and hits it", () -> {
			Interaction box = HubDummy.hitbox();
			Vec3 at = box.position().add(0, 0, 2.5);
			p(A).teleportTo(p(A).level(), at.x, at.y, at.z, Set.of(), 180f, 0f, true);
			p(A).getAbilities().flying = false;
			VARS.put("dummyHit", HubDummy.hit(p(A), box));
		});
		s.check(tag + ": one hit counted, a number floats up", () -> {
			DummyHits d = HubDummy.stats(p(A).getUUID());
			return Boolean.TRUE.equals(VARS.get("dummyHit")) && d != null && d.hits == 1 && d.lastDamage > 0
					&& d.lastReach > 1 && d.lastReach < 4 && HubDummy.popups() >= 1;
		});
		s.run(tag + ": A hits again right away (no cooldown)", () -> {
			VARS.put("firstDamage", HubDummy.stats(p(A).getUUID()).lastDamage);
			HubDummy.hit(p(A), HubDummy.hitbox());
		});
		s.check(tag + ": weaker without the cooldown, and a combo of two", () -> {
			DummyHits d = HubDummy.stats(p(A).getUUID());
			return d.hits == 2 && d.lastDamage < (double) VARS.get("firstDamage") && d.combo == 2;
		});
		s.check(tag + ": a click on something else is not the dummy", () -> !HubDummy.hit(p(A), p(A)));
		s.run(tag + ": A right-clicks the dummy", () -> HubDummy.use(p(A), HubDummy.hitbox()));
		s.check(tag + ": the numbers start over", () -> HubDummy.stats(p(A).getUUID()) == null);
		s.waitFor(tag + ": the numbers that floated up are gone", 40, () -> HubDummy.popups() == 0);
	}

	private static void wardrobe(MinecraftServer server, Script s) {
		String tag = "wardrobe";
		SelfTest.reset(s, tag);
		s.run(tag + ": A owns and wears the glass helmet and the red outfit", () -> {
			SelfTest.join(server, A);
			for (String id : new String[] {"hat_glass", "outfit_red"}) {
				CosmeticDef c = Cosmetics.byId(id);
				prof(A).ownedCosmetics.add(id);
				CosmeticRules.select(prof(A), c);
			}
			Wardrobe.apply(p(A));
		});
		s.check(tag + ": helmet and dyed leather in the armour slots, locked", () -> {
			ServerPlayer a = p(A);
			var chest = a.getItemBySlot(EquipmentSlot.CHEST);
			return Icon.tagOf(a.getItemBySlot(EquipmentSlot.HEAD)).equals(Wardrobe.TAG + "hat_glass")
					&& Icon.tagOf(chest).equals(Wardrobe.TAG + "outfit_red") && chest.get(DataComponents.DYED_COLOR) != null
					&& Icon.isHubItem(a.getItemBySlot(EquipmentSlot.FEET));
		});
		s.run(tag + ": the lobby items are put in place again", () -> Lobby.refresh(p(A)));
		s.check(tag + ": the wardrobe stays on", () -> Icon.tagOf(p(A).getItemBySlot(EquipmentSlot.HEAD)).equals(Wardrobe.TAG + "hat_glass"));
		s.run(tag + ": A takes both off", () -> {
			Cosmetics.clear(p(A), "hat");
			Cosmetics.clear(p(A), "outfit");
		});
		s.check(tag + ": the armour slots are empty again", () -> p(A).getItemBySlot(EquipmentSlot.HEAD).isEmpty()
				&& p(A).getItemBySlot(EquipmentSlot.LEGS).isEmpty());
		s.check(tag + ": every cosmetic in the list is valid", () -> Cosmetics.problems().isEmpty());
	}

	/** A sword duel with the intro and the victory shot on: camera over the arena, killfeed, camera round the winner. */
	private static void show(MinecraftServer server, Script s) {
		String tag = "show";
		SelfTest.reset(s, tag);
		s.run(tag + ": intro and victory shot on; A challenges B", () -> {
			SelfTest.join(server, A);
			SelfTest.join(server, B);
			ConfigManager.config().effects.intro = true;
			ConfigManager.config().effects.introSeconds = 2;
			ConfigManager.config().effects.victoryCamera = true;
			ConfigManager.config().effects.killfeed = true;
			net.battlehub.queue.Challenges.send(p(A), p(B), GameMode.SWORD);
			net.battlehub.queue.Challenges.accept(p(B), p(A));
		});
		s.waitFor(tag + ": countdown with the intro flight for both", 400, () -> {
			Match m = Matches.of(p(A).getUUID());
			if (m != null && m.phase() == Match.Phase.COUNTDOWN && Cinematics.active(p(A).getUUID()) && Cinematics.active(p(B).getUUID())) {
				VARS.put("match", m);
				return true;
			}
			return false;
		});
		s.waitFor(tag + ": A's client looks through the camera", 10, () -> p(A).clientEntities().contains(Cinematics.cameraId(p(A).getUUID())));
		s.run(tag + ": remember the camera", () -> VARS.put("camera", Cinematics.cameraId(p(A).getUUID())));
		s.waitFor(tag + ": the fight starts, the camera is back with the player", 300, () -> match().phase() == Match.Phase.RUNNING
				&& !Cinematics.active(p(A).getUUID()) && !p(A).clientEntities().contains((int) VARS.get("camera")));
		s.pause(12);
		s.run(tag + ": A kills B", () -> {
			ServerPlayer b = p(B);
			b.hurtServer(b.level(), b.damageSources().playerAttack(p(A)), 1000f);
		});
		s.check(tag + ": the killfeed has the kill with a sword and shows it on the right", () ->
				Killfeed.lines(match().id).equals(java.util.List.of("BHTestA sword BHTestB")) && Killfeed.showing(match().id, p(A).getUUID()));
		s.check(tag + ": the victory shot circles A", () -> match().phase() == Match.Phase.ENDING && Cinematics.active(p(A).getUUID())
				&& Cinematics.active(p(B).getUUID()));
		s.waitFor(tag + ": match closed", 400, () -> match().phase() == Match.Phase.CLOSED && Sessions.isFree(p(A).getUUID())
				&& Sessions.isFree(p(B).getUUID()));
		s.check(tag + ": no camera and no killfeed left", () -> !Cinematics.active(p(A).getUUID()) && !Cinematics.active(p(B).getUUID())
				&& !Killfeed.showing(match().id, p(A).getUUID()) && Killfeed.lines(match().id).isEmpty());
		s.run(tag + ": intro and victory shot off again", () -> {
			ConfigManager.config().effects.intro = false;
			ConfigManager.config().effects.victoryCamera = false;
		});
	}

	/** The pack is built, served over HTTP, offered, and changes the icons for a player who loaded it. */
	private static void pack(MinecraftServer server, Script s) {
		String tag = "resource pack";
		SelfTest.reset(s, tag);
		s.run(tag + ": switched on, served on this machine", () -> {
			SelfTest.join(server, A);
			SelfTest.join(server, B);
			var rp = ConfigManager.config().resourcePack;
			rp.enabled = true;
			rp.host = "127.0.0.1";
			rp.url = "";
			rp.port = 25600 + (int) (System.nanoTime() % 300);
			ResourcePack.start(server);
		});
		s.check(tag + ": built with the font, the icons and the sounds", () -> {
			byte[] zip = ResourcePack.zip();
			if (!ResourcePack.active() || zip == null) return false;
			Set<String> names = new HashSet<>();
			try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
				for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) names.add(e.getName());
			} catch (java.io.IOException e) {
				return false;
			}
			return names.containsAll(Set.of("pack.mcmeta", "assets/minecraft/font/default.json",
					"assets/battlehub/textures/font/icons.png", "assets/battlehub/sounds.json"));
		});
		s.check(tag + ": the built-in server hands out exactly that zip", () -> {
			try {
				HttpResponse<byte[]> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(ResourcePack.url()))
						.timeout(java.time.Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofByteArray());
				return r.statusCode() == 200 && Arrays.equals(r.body(), ResourcePack.zip());
			} catch (java.io.IOException | InterruptedException e) {
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST pack download failed: {}", e.toString());
				return false;
			}
		});
		s.run(tag + ": offered to A, who loads it; B says no", () -> {
			ResourcePack.offer(p(A));
			ResourcePack.offer(p(B));
			ResourcePack.response(p(A), ResourcePack.packId(), "SUCCESSFULLY_LOADED");
			ResourcePack.response(p(B), ResourcePack.packId(), "DECLINED");
		});
		s.check(tag + ": A gets the pixel icons, B the normal symbols", () -> ResourcePack.loaded(p(A)) && !ResourcePack.loaded(p(B))
				&& Glyphs.icon(p(A), "sword").getString().equals(String.valueOf(PixelIcons.glyph("sword")))
				&& Glyphs.icon(p(B), "sword").getString().equals("⚔"));
		s.check(tag + ": B is still in BattleHub (the pack is optional)", () -> net.battlehub.hub.Travel.inBattleHub(p(B)));
		s.run(tag + ": switched off again", () -> {
			ConfigManager.config().resourcePack.enabled = false;
			ResourcePack.start(server);
		});
		s.check(tag + ": nothing offered or served", () -> !ResourcePack.active() && !ResourcePack.loaded(p(A)));
	}
}
