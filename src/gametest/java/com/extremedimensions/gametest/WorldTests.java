package com.extremedimensions.gametest;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.google.gson.reflect.TypeToken;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.expedition.ExpeditionService;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.config.Config;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.ServerEvents;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/** World data survives crashes, every creature of the mod lives on a dedicated server, and the mod keeps to its own worlds. */
public class WorldTests {
	private static final Type MAP = new TypeToken<Map<String, Integer>>() {}.getType();

	/**
	 * Another mod's world lies outside the wound's reach: there the curses sleep and a death costs nothing of the mod's;
	 * back in one of its own worlds everything wakes again. (The test server loads no worlds from data packs, so the
	 * player is sent away the way the server tick does it on entering one.)
	 */
	@GameTest
	public void theModSleepsInAnotherModsWorld(GameTestHelper helper) {
		CurseTests.check(helper, Dims.ours(helper.getLevel()), "the Overworld should be the mod's");
		CurseTests.check(helper, Dims.ours(Identifier.fromNamespaceAndPath("extremedimensions", "the_seam"))
				&& Dims.ours(Identifier.fromNamespaceAndPath("minecraft", "the_nether"))
				&& Dims.ours(Identifier.fromNamespaceAndPath("minecraft", "the_end")), "the Seam, the Nether and the End are the mod's");
		CurseTests.check(helper, !Dims.ours(Identifier.fromNamespaceAndPath("othermod", "the_nether"))
				&& !Dims.ours(Identifier.fromNamespaceAndPath("othermod", "skylands"))
				&& !Dims.ours(Identifier.fromNamespaceAndPath("minecraft", "skylands")), "another mod's world, even under a borrowed name, is not the mod's");
		int gate = Config.OWN_WORLDS_ONLY;
		try {
			Config.OWN_WORLDS_ONLY = 0;
			CurseTests.check(helper, Dims.ours(Identifier.fromNamespaceAndPath("othermod", "skylands")), "with own_worlds_only off every world is the mod's");
		} finally {
			Config.OWN_WORLDS_ONLY = gate;
		}
		ServerPlayer p = CurseTests.cursed(helper, Penalty.WITHERING, 1, Penalty.KINDLING, 2);
		PlayerData d = DataStore.get(p.getUUID());
		float cursed = p.getMaxHealth();
		CurseTests.check(helper, cursed < 20, "Withering should lower the maximum health first, got %s", cursed);
		var fire = helper.getLevel().damageSources().inFire();
		ServerEvents.setAway(p, d, true);
		try {
			CurseTests.near(helper, p.getMaxHealth(), 20, "maximum health with Withering asleep");
			CurseTests.near(helper, CurseEffects.damageTaken(p, fire, 4.0f), 4.0, "fire damage with Kindling asleep");
			CurseTests.check(helper, d.owned(Penalty.WITHERING) == 1, "a sleeping curse is still carried");
			// A death there costs nothing of the mod's
			d.atonement.put(Penalty.KINDLING.id, 40);
			ExpeditionService.onDeath(p);
			CurseTests.check(helper, d.atonement.getOrDefault(Penalty.KINDLING.id, 0) == 40, "a death in another mod's world should leave the atonement alone");
		} finally {
			d.diedAway = false;
			ServerEvents.setAway(p, d, false);
		}
		CurseTests.near(helper, p.getMaxHealth(), cursed, "maximum health with Withering awake again");
		CurseTests.near(helper, CurseEffects.damageTaken(p, fire, 4.0f), 4.0 * (1 + 2 * Penalty.KINDLING.frac()), "fire damage with Kindling awake again");
		helper.succeed();
	}

	/** Any player, operator or not, can ask where they stand and whether the mod acts there. */
	@GameTest
	public void anyoneMayAskWhereTheModActs(GameTestHelper helper) throws Exception {
		ServerPlayer p = TestPlayers.survivor(helper);
		var source = p.createCommandSourceStack();
		int acts = helper.getLevel().getServer().getCommands().getDispatcher().execute("tether where", source);
		CurseTests.check(helper, acts == 1, "/tether where in the Overworld should say the mod acts here, got %s", acts);
		boolean blocked;
		try {
			helper.getLevel().getServer().getCommands().getDispatcher().execute("tether curse clear", source);
			blocked = false;
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			blocked = true;
		}
		CurseTests.check(helper, blocked, "the operator commands should stay closed to a player who is no operator");
		helper.succeed();
	}

	@GameTest
	public void worldDataSurvivesACrashMidWrite(GameTestHelper helper) throws Exception {
		Path dir = Files.createTempDirectory("ed-data");
		Path file = dir.resolve("extremedimensions.json");
		DataStore.writeAtomically(file, "{\"stacks\":1}");
		DataStore.writeAtomically(file, "{\"stacks\":2}");
		Map<String, Integer> now = DataStore.readWithFallback(file, MAP);
		CurseTests.check(helper, now != null && now.get("stacks") == 2, "the newest write should be read, got %s", now);
		CurseTests.check(helper, Files.readString(DataStore.backupOf(file), StandardCharsets.UTF_8).contains("1"), "the previous version should stay as .bak");
		// A crash halfway through a write leaves a broken file: the backup must take over and the broken file stay
		Files.writeString(file, "{\"stacks\":", StandardCharsets.UTF_8);
		Map<String, Integer> restored = DataStore.readWithFallback(file, MAP);
		CurseTests.check(helper, restored != null && restored.get("stacks") == 1, "the backup should be restored, got %s", restored);
		boolean kept;
		try (var files = Files.list(dir)) {
			kept = files.anyMatch(f -> f.getFileName().toString().startsWith("extremedimensions.json.unreadable-"));
		}
		CurseTests.check(helper, kept, "the unreadable file should be kept aside, not deleted");
		helper.succeed();
	}

	/**
	 * Every mixin landed in its target. Mixins here are optional (a missing target is skipped, not fatal), so after a
	 * Minecraft update a renamed method would silently switch a curse off; this test catches that.
	 */
	@GameTest
	public void everyMixinIsApplied(GameTestHelper helper) {
		Object[][] expected = {
			{net.minecraft.world.entity.LivingEntity.class, "rememberSource", "cursedDamage", "cursedEffect"},
			{net.minecraft.world.entity.player.Inventory.class, "freeSlot", "roomInSlot"},
			{net.minecraft.world.inventory.Slot.class, "sealed", "dwindling"},
			{net.minecraft.world.food.FoodData.class, "unhealing"},
			{net.minecraft.world.entity.Mob.class, "wild"},
			{net.minecraft.world.entity.npc.villager.Villager.class, "illRepute"},
		};
		for (Object[] row : expected) {
			Class<?> target = (Class<?>) row[0];
			for (int i = 1; i < row.length; i++) {
				String handler = "extremedimensions$" + row[i];
				boolean found = false;
				for (java.lang.reflect.Method m : target.getDeclaredMethods()) {
					found |= m.getName().contains(handler);
				}
				CurseTests.check(helper, found, "mixin %s did not apply to %s", handler, target.getSimpleName());
			}
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void everyCreatureSpawnsAndTicks(GameTestHelper helper) {
		EntityType<?>[] types = {ModEntities.LURKER, ModEntities.HOLLOWLING, ModEntities.KEEPER, ModEntities.STARVED_ONE,
				ModEntities.VEIL_RAY, ModEntities.THINNED, ModEntities.MATRIARCH, ModEntities.FERRYMAN, ModEntities.WEAVER,
				ModEntities.KNOT_MOTHER};
		Mob[] spawned = new Mob[types.length];
		for (int i = 0; i < types.length; i++) {
			spawned[i] = (Mob) helper.spawn(types[i], new BlockPos(1 + (i % 4) * 2, 2, 1 + (i / 4) * 3));
		}
		helper.runAfterDelay(10, () -> {
			for (int i = 0; i < types.length; i++) {
				CurseTests.check(helper, spawned[i].isRemoved() || spawned[i].tickCount >= 5, "%s did not tick (%s ticks)", types[i], spawned[i].tickCount);
				// Discarded, not killed: a slain Starved One or Keeper would end the fight for the whole world
				spawned[i].discard();
			}
			helper.succeed();
		});
	}

	/** Stepping through a portal and straight back is not an expedition: no count, no return's atonement. */
	@GameTest
	public void aStepThroughAndBackIsNoExpedition(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-hopper");
		PlayerData d = DataStore.get(p.getUUID());
		int before = d.expeditions;
		d.inRun = true;
		d.runActive = 4;
		ExpeditionService.onDimensionChange(p, d, Dims.NETHER, Dims.OVERWORLD);
		CurseTests.check(helper, d.expeditions == before && !d.inRun, "a four-second hop counted as an expedition (%s, was %s)", d.expeditions, before);
		d.inRun = true;
		d.runActive = Math.max(1, Config.ATONE_RETURN_MIN_SECONDS) + 5;
		ExpeditionService.onDimensionChange(p, d, Dims.NETHER, Dims.OVERWORLD);
		CurseTests.check(helper, d.expeditions == before + 1, "a real expedition did not count (%s, was %s)", d.expeditions, before);
		helper.succeed();
	}

	/** An anchor that keeps someone else's charge cannot be broken out from under them; an empty or own one can. */
	@GameTest
	public void anotherPlayersAnchorCannotBeBroken(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-breaker");
		BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
		com.extremedimensions.data.Anchor a = new com.extremedimensions.data.Anchor(pos.getX(), pos.getY(), pos.getZ(), Dims.NETHER);
		String key = a.key();
		DataStore.anchors().put(key, a);
		try {
			CurseTests.check(helper, com.extremedimensions.anchor.AnchorService.mayBreak(p, Dims.OVERWORLD, pos), "an empty anchor could not be broken");
			a.add(p.getStringUUID(), 100);
			CurseTests.check(helper, com.extremedimensions.anchor.AnchorService.mayBreak(p, Dims.OVERWORLD, pos), "the player's own anchor could not be broken");
			a.add(java.util.UUID.randomUUID().toString(), 100);
			CurseTests.check(helper, !com.extremedimensions.anchor.AnchorService.mayBreak(p, Dims.OVERWORLD, pos), "another player's charge could be broken out from under them");
		} finally {
			DataStore.anchors().remove(key);
		}
		helper.succeed();
	}

	/** A curse offer left unanswered (a client that never shows it) is chosen by the Tether after the set time. */
	@GameTest(maxTicks = 100)
	public void anUnansweredCurseOfferIsChosenForThePlayer(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-silent");
		PlayerData d = DataStore.get(p.getUUID());
		int timeout = Config.OFFER_TIMEOUT_SECONDS;
		Config.OFFER_TIMEOUT_SECONDS = 1;
		int stacks = d.curses.values().stream().mapToInt(Integer::intValue).sum();
		com.extremedimensions.penalty.PenaltyService.owe(d, false);
		d.offerDelay = -1;
		CurseTests.check(helper, !d.pendingOffer.isEmpty(), "no offer was drawn");
		helper.runAfterDelay(40, () -> {
			Config.OFFER_TIMEOUT_SECONDS = timeout;
			int now = d.curses.values().stream().mapToInt(Integer::intValue).sum();
			CurseTests.check(helper, d.pendingOffer.isEmpty() && now > stacks, "the offer still stands (%s) and the curses went from %s to %s", d.pendingOffer, stacks, now);
			helper.succeed();
		});
	}
}
