#!/bin/bash
# TEMP: print signatures of the 26.3 classes the Lurker needs
set +e
JARS=$(find ~/.gradle /home/runner/work -name "*.jar" 2>/dev/null | grep -E "minecraft|fabric-api|fabric-(object-builder|rendering|entity|biome)" | grep -v sources)
show() {
  local cls="$1"; local pat="$2"; local path="${cls//.//}.class"
  for j in $JARS; do
    if unzip -l "$j" 2>/dev/null | grep -q " $path$"; then
      echo "=== $cls  ($(basename $j))"
      javap -p -cp "$j" "$cls" 2>/dev/null | grep -E "$pat" | head -60
      return
    fi
  done
  echo "=== $cls NOT FOUND"
}
show net.minecraft.world.entity.monster.EnderMan "EnderMan\(|registerGoals|customServerAiStep|setCarriedBlock|getAmbientSound|getHurtSound|getDeathSound|removeWhenFarAway|createAttributes|teleport|setTarget|isSensitiveToWater|requiresCustomPersistence|hurtServer|class "
show net.minecraft.world.entity.EntityType\$Builder "static|sized|build|clientTrackingRange|eyeHeight|fireImmune"
show net.minecraft.client.renderer.entity.EndermanRenderer "."
show net.minecraft.client.renderer.entity.LivingEntityRenderer "scale|getTextureLocation"
show net.minecraft.client.renderer.entity.state.EndermanRenderState "."
show net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry "static"
show net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry "static"
show net.minecraft.client.renderer.entity.EntityRenderers "static"
show net.minecraft.world.entity.Mob "getNavigation|getLookControl|setPersistenceRequired|goalSelector"
show net.minecraft.world.entity.ai.navigation.PathNavigation "moveTo|stop|isDone"
show net.minecraft.world.entity.ai.control.LookControl "setLookAt"
show net.minecraft.world.entity.Entity "teleportTo|discard\(|getViewVector|spawnAtLocation|setInvisible|setSilent|setNoGravity|addTag|getTags"
show net.minecraft.world.entity.LivingEntity "hasLineOfSight|setItemSlot|getItemBySlot"
show net.minecraft.world.entity.item.ItemEntity "ItemEntity\(|setPickUpDelay|setDefaultPickUpDelay"
show net.minecraft.world.level.levelgen.Heightmap\$Types "public static final"
show net.minecraft.world.level.Level "getHeight\(|addFreshEntity"
show net.minecraft.world.entity.ai.goal.LookAtPlayerGoal "LookAtPlayerGoal\("
show net.minecraft.world.entity.ai.goal.FloatGoal "FloatGoal\("
show net.minecraft.world.entity.EntityType "create\(|spawn\("
show net.minecraft.world.entity.EntitySpawnReason "public static final"
