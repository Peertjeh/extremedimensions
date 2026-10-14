package com.extremedimensions.gametest;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.google.gson.reflect.TypeToken;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.registry.ModEntities;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/** World data survives crashes, and every creature of the mod lives on a dedicated server. */
public class WorldTests {
	private static final Type MAP = new TypeToken<Map<String, Integer>>() {}.getType();

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

	@GameTest(maxTicks = 40)
	public void everyCreatureSpawnsAndTicks(GameTestHelper helper) {
		EntityType<?>[] types = {ModEntities.LURKER, ModEntities.HOLLOWLING, ModEntities.KEEPER, ModEntities.STARVED_ONE,
				ModEntities.VEIL_RAY, ModEntities.THINNED};
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
