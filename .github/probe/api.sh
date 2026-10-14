#!/bin/bash
# TEMP: print the 26.3 signatures phase 1 relies on (gametests and direct replacements for commands)
set +e
JARS=$(find ~/.gradle -name "*.jar" 2>/dev/null | grep -E "minecraft-(client|common|merged|clientonly|client-only)|fabric" | grep -v sources | sort -u)
show() {
  local cls="$1"; local pat="$2"; local path="${cls//.//}.class"
  for j in $JARS; do
    if unzip -l "$j" 2>/dev/null | grep -q " $path$"; then
      echo "=== $cls  ($(basename $j))"
      javap -p -cp "$j" "$cls" 2>/dev/null | grep -E "$pat" | grep -v lambda | head -90
      return
    fi
  done
  echo "=== $cls NOT FOUND"
}
find_cls() {
  for j in $JARS; do unzip -l "$j" 2>/dev/null | grep -E "$1" | awk -v j=$(basename $j) '{print j": "$4}'; done | sort -u | head -60
}
find_cls "fabricmc/fabric/api/gametest/|fabricmc/fabric/api/client/gametest/v1/[A-Za-z]+\.class|fabricmc/fabric/api/client/gametest/v1/context/[A-Za-z]+\.class"
show net.fabricmc.fabric.api.gametest.v1.GameTest "."
show net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker "."
show net.minecraft.gametest.framework.GameTestHelper "public"
show net.minecraft.gametest.framework.GameTestAssertException "public"
show net.minecraft.server.level.ServerLevel "sendParticles|playSound\(|public .* getEntitiesOfClass|addFreshEntity"
show net.minecraft.world.level.Level "public .* playSound\(|playLocalSound"
show net.minecraft.core.particles.SimpleParticleType "public"
show net.minecraft.core.registries.BuiltInRegistries "PARTICLE_TYPE|SOUND_EVENT|ATTRIBUTE|MOB_EFFECT|ENTITY_TYPE|ITEM;"
show net.minecraft.core.DefaultedRegistry "public"
show net.minecraft.core.Registry "public .* (get|getValue|getOptional|wrapAsHolder)\("
show net.minecraft.network.protocol.game.ClientboundSoundPacket "public"
show net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket "public"
show net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket "public"
show net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket "public"
show net.minecraft.world.entity.ai.attributes.AttributeInstance "public"
show net.minecraft.world.entity.ai.attributes.AttributeModifier "public"
show 'net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation' "public"
show net.minecraft.world.entity.LivingEntity "public .* (getAttribute|addEffect|removeEffect|kill)\("
show net.minecraft.world.entity.Entity "public .* (teleportTo|snapTo|moveTo|kill|discard|addTag|getTags)\("
show net.minecraft.server.level.ServerPlayer "public .* (teleportTo|awardRecipes|getAdvancements|playNotifySound|sendSystemMessage|drop)\("
show net.minecraft.server.PlayerAdvancements "public .* award|getOrStartProgress"
show net.minecraft.advancements.AdvancementHolder "public"
show net.minecraft.advancements.Advancement "public .* criteria"
show net.minecraft.server.ServerAdvancementManager "public .* get\("
show net.minecraft.world.item.crafting.RecipeManager "public .* byKey"
show net.minecraft.server.MinecraftServer "public .* (getAdvancements|getRecipeManager|getLevel|getAverageTickTimeNanos|getTickTimesNanos|tickRateManager|getCurrentSmoothedTickTime)\("
show net.minecraft.world.entity.EntityType "public .* create\(|public .* spawn\("
show net.minecraft.world.entity.EntitySpawnReason "public static final"
show net.minecraft.world.entity.Relative "public static final|public static .* (ALL|DELTA|ROTATION)"
show net.minecraft.world.level.storage.LevelResource "public"
show net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents "public static final"
show net.minecraft.world.entity.Display "public|setTransformation|setBlockState"
show 'net.minecraft.world.entity.Display$BlockDisplay' "public|setBlockState"
