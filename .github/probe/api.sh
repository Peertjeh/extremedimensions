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
show net.minecraft.world.entity.monster.Enderman "."
show net.minecraft.world.entity.Mob "registerGoals|customServerAiStep|removeWhenFarAway|checkDespawn|getAmbientSound"
show net.minecraft.world.entity.LivingEntity "hurtServer|getAmbientSound|playHurtSound"
