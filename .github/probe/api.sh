#!/bin/bash
set +e
JARS=$(find ~/.gradle -name "*.jar" 2>/dev/null | grep -E "minecraft-client-only|minecraft-client\.jar|minecraft-common|fabric-particles|fabric-loot" | grep -v sources | sort -u)
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
show net.minecraft.client.particle.SingleQuadParticle\$Layer "."
show net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet "."
show net.minecraft.client.particle.SuspendedTownParticle\$Provider "."
show net.minecraft.client.particle.PortalParticle "."
show net.fabricmc.fabric.api.loot.v3.LootTableEvents\$ModifyDrops "."
show net.minecraft.world.level.storage.loot.LootContext "public"
