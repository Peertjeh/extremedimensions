#!/bin/bash
set +e
JARS=$(find ~/.gradle /home/runner/work -name "*.jar" 2>/dev/null | grep -E "minecraft|fabric" | grep -v sources | sort -u)
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
find_cls() {
  for j in $JARS; do unzip -l "$j" 2>/dev/null | grep -E "$1" | awk -v j=$(basename $j) '{print j": "$4}'; done | head -40
}
echo "##### particle classes"
find_cls "fabric/api/particle/.*\.class$|fabric/api/client/particle/.*\.class$"
find_cls "net/minecraft/client/particle/(SingleQuad|TextureSheet|Particle|SpriteSet|ParticleProvider|ParticleRenderType|SimpleAnimated|SoulParticle|SuspendedTownParticle|PortalParticle)[A-Za-z$]*\.class$"
show net.fabricmc.fabric.api.particle.v1.FabricParticleTypes "static"
show net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry "."
show net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry "."
show net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry\$PendingParticleFactory "."
show net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry\$PendingParticleProvider "."
show net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider "."
show net.minecraft.client.particle.Particle "protected|public"
show net.minecraft.client.particle.SingleQuadParticle "protected|public"
show net.minecraft.client.particle.TextureSheetParticle "protected|public"
show net.minecraft.client.particle.ParticleProvider "."
show net.minecraft.client.particle.SpriteSet "."
show net.minecraft.client.particle.SoulParticle "."
show net.minecraft.client.particle.SoulParticle\$Provider "."
show net.minecraft.client.particle.SuspendedTownParticle "."
show net.minecraft.core.particles.SimpleParticleType "."
show net.minecraft.core.particles.ParticleTypes "SOUL;|register"
echo "##### loot"
find_cls "fabric/api/loot/v[0-9]/[A-Za-z]*\.class$"
show net.fabricmc.fabric.api.loot.v3.LootTableEvents "."
show net.fabricmc.fabric.api.loot.v3.LootTableEvents\$Modify "."
show net.fabricmc.fabric.api.loot.v4.LootTableEvents "."
show net.fabricmc.fabric.api.loot.v4.LootTableEvents\$Modify "."
show net.minecraft.world.level.storage.loot.LootPool "static|public"
show net.minecraft.world.level.storage.loot.LootPool\$Builder "public"
show net.minecraft.world.level.storage.loot.entries.LootItem "static"
show net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer\$Builder "public"
show net.minecraft.world.level.storage.loot.providers.number.UniformGenerator "static"
show net.minecraft.world.level.storage.loot.providers.number.ConstantValue "static"
show net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition "static"
show net.minecraft.world.level.storage.loot.LootTable\$Builder "public"
show net.minecraft.world.level.storage.loot.BuiltInLootTables "RUINED_PORTAL|BASTION_TREASURE|NETHER_BRIDGE|END_CITY|ANCIENT_CITY|STRONGHOLD_LIBRARY"
echo "##### misc"
show net.minecraft.world.entity.Mob "getLookControl|setTarget"
show net.minecraft.world.item.Item "use\(|appendHoverText"
show net.minecraft.world.item.Item\$Properties "public"
