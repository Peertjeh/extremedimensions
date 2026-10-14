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
				ModEntities.VEIL_RAY, ModEntities.THINNED, ModEntities.MATRIARCH, ModEntities.FERRYMAN, ModEntities.WEAVER};
		Mob[] spawned = new Mob[types.length];
		for (int i = 0; i < types.length; i++) {
			spawned[i] = (Mob) helper.spawn(types[i], new BlockPos(1 + (i % 3) * 2, 2, 1 + (i / 3) * 3));
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
}
