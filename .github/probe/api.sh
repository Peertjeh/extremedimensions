#!/bin/bash
# TEMP: remaining 26.3 signatures for the test suite and the save hook
set +e
JARS=$(find ~/.gradle -name "*.jar" 2>/dev/null | grep -E "minecraft-(client|common|merged|clientonly|client-only)|fabric" | grep -v sources | sort -u)
show() {
  local cls="$1"; local pat="$2"; local path="${cls//.//}.class"
  for j in $JARS; do
    if unzip -l "$j" 2>/dev/null | grep -q " $path$"; then
      echo "=== $cls  ($(basename $j))"
      javap -p -cp "$j" "$cls" 2>/dev/null | grep -E "$pat" | grep -v lambda | head -200
      return
    fi
  done
  echo "=== $cls NOT FOUND"
}
show net.minecraft.gametest.framework.GameTestHelper "public .* (succeed|fail|runAfterDelay|runAtTickTime|assert(True|False|Value)|getTick|startSequence|onEachTick|absolute|relative|getTestRotation|getBounds|setDayTime|getLevel)"
show 'net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$AfterSave' "."
show 'net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$BeforeSave' "."
show net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext "public"
show net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext "public"
show net.fabricmc.fabric.api.client.gametest.v1.TestInput "public"
show net.minecraft.world.entity.player.Player "public .* (getAbilities|isCreative|isSpectator|gameMode)\("
show net.minecraft.server.level.ServerPlayer "public .* (setGameMode|gameMode)"
show net.minecraft.server.level.ServerPlayerGameMode "public .* (changeGameModeForPlayer|getGameModeForPlayer)"
show net.minecraft.world.effect.MobEffects "DARKNESS|LEVITATION|RESISTANCE"
show net.minecraft.resources.ResourceKey "public static .* create"
show net.minecraft.core.registries.Registries "RECIPE;|PARTICLE_TYPE;"
