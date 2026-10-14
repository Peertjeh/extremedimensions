#!/bin/bash
# TEMP: signatures for the Extreme curses and anti-farm atonement
set +e
JARS=$(find ~/.gradle -name "*.jar" 2>/dev/null | grep -E "minecraft-(client|common|merged)|fabric" | grep -v sources | sort -u)
show() {
  local cls="$1"; local pat="$2"; local path="${cls//.//}.class"
  for j in $JARS; do
    if unzip -l "$j" 2>/dev/null | grep -q " $path$"; then
      echo "=== $cls  ($(basename $j))"
      javap -p -cp "$j" "$cls" 2>/dev/null | grep -E "$pat" | grep -v lambda | head -60
      return
    fi
  done
  echo "=== $cls NOT FOUND"
}
code() {
  local cls="$1"; local meth="$2"; local path="${cls//.//}.class"
  for j in $JARS; do
    if unzip -l "$j" 2>/dev/null | grep -q " $path$"; then
      echo "=== CODE $cls.$meth"
      javap -p -c -cp "$j" "$cls" 2>/dev/null | awk -v m="$meth" '$0 ~ m"\\(" && /\(/ {p=1} p {print} p && /^$/ {exit}' | grep -E "invoke|public|private|protected" | head -40
      return
    fi
  done
}
show net.minecraft.world.food.FoodData "tick|heal"
code net.minecraft.world.food.FoodData "void tick"
show net.minecraft.world.Container "getMaxStackSize"
show net.minecraft.world.inventory.Slot "getMaxStackSize"
show net.minecraft.world.entity.monster.EnderMan "Star|star|Look|setTarget|isLookingAtMe|teleport"
show net.minecraft.world.entity.NeutralMob "public"
show net.minecraft.world.entity.monster.ZombifiedPiglin "Anger|anger|setTarget|alert"
show net.minecraft.world.entity.TamableAnimal "isTame|tame\("
show net.minecraft.world.entity.animal.equine.AbstractHorse "isTamed|tameWithName"
show net.minecraft.world.entity.animal.horse.AbstractHorse "isTamed|tameWithName"
show 'net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents$AllowDeath' "."
show net.fabricmc.fabric.api.event.player.UseEntityCallback "interact|EVENT"
show net.minecraft.world.level.Level "isThundering|canSeeSky|isRainingAt"
show net.minecraft.world.entity.EntityType "LIGHTNING_BOLT"
show net.minecraft.world.entity.LightningBolt "public"
show net.minecraft.world.entity.ai.attributes.Attributes "FOLLOW_RANGE|BLOCK_INTERACTION_RANGE"
show net.minecraft.world.entity.Mob "finalizeSpawn|spawnReason|SpawnReason|isPersistenceRequired"
show net.minecraft.world.entity.Entity "getTags|addTag|removeTag"
show net.minecraft.world.item.Items "TOTEM_OF_UNDYING"
show net.minecraft.world.entity.LivingEntity "checkTotemDeathProtection|getItemInHand"
