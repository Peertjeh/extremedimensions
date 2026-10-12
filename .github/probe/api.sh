#!/bin/bash
set +e
JARS=$(find ~/.gradle -name "*.jar" 2>/dev/null | grep -E "minecraft-client-only|minecraft-client\.jar|minecraft-common|fabric-object-builder|fabric-entity" | grep -v sources | sort -u)
show() {
  local cls="$1"; local pat="$2"; local path="${cls//.//}.class"
  for j in $JARS; do
    if unzip -l "$j" 2>/dev/null | grep -q " $path$"; then
      echo "=== $cls  ($(basename $j))"
      javap -p -cp "$j" "$cls" 2>/dev/null | grep -E "$pat" | grep -v lambda | head -40
      return
    fi
  done
  echo "=== $cls NOT FOUND"
}
show net.minecraft.world.entity.PathfinderMob "PathfinderMob\("
show net.minecraft.world.entity.Mob "createMobAttributes|mobInteract|doHurtTarget|getVoicePitch|setPersistenceRequired|isPersistenceRequired|requiresCustomPersistence|checkDespawn"
show net.minecraft.world.entity.LivingEntity "getVoicePitch|doHurtTarget|public boolean hurtServer|heal\(|getMaxHealth|setHealth"
show net.minecraft.world.entity.Entity "public boolean isInvulnerableTo|saveAsPassenger|shouldBeSaved|public void setSilent|setCustomName|setInvisible"
show net.minecraft.world.entity.ai.goal.MeleeAttackGoal "MeleeAttackGoal\("
show net.minecraft.world.entity.ai.goal.RandomStrollGoal "RandomStrollGoal\("
show net.minecraft.server.level.ServerBossEvent "ServerBossEvent\(|addPlayer|removePlayer|setProgress|removeAllPlayers"
show net.minecraft.world.BossEvent\$BossBarColor "PURPLE|WHITE"
show net.minecraft.world.BossEvent\$BossBarOverlay "PROGRESS|NOTCHED"
show net.minecraft.world.level.block.BarrelBlock "FACING|OPEN"
show net.minecraft.world.level.block.entity.BarrelBlockEntity "class|setItem|setCustomName"
show net.minecraft.world.level.biome.Biomes "DARK_FOREST|PALE_GARDEN|SOUL_SAND_VALLEY|DEEP_DARK"
show net.minecraft.world.level.LevelReader "getBiome"
show net.minecraft.core.Holder "boolean is\("
show net.minecraft.client.model.geom.ModelPart "Scale"
show net.minecraft.world.entity.MobCategory "CREATURE|AMBIENT|MISC"
show net.minecraft.world.level.block.Blocks "BLACK_CANDLE;|SKELETON_SKULL;|COBWEB;|BONE_BLOCK;|SOUL_SOIL;|DARK_OAK_FENCE;|PALE_OAK_FENCE;|MUD;"
show net.minecraft.world.level.block.CandleBlock "CANDLES|LIT"
