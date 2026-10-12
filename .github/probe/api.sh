#!/bin/bash
set +e
JARS=$(find ~/.gradle -name "*.jar" 2>/dev/null | grep -E "minecraft-(client|common|merged|clientonly)|fabric" | grep -v sources | sort -u)
show() {
  local cls="$1"; local pat="$2"; local path="${cls//.//}.class"
  for j in $JARS; do
    if unzip -l "$j" 2>/dev/null | grep -q " $path$"; then
      echo "=== $cls  ($(basename $j))"
      javap -p -cp "$j" "$cls" 2>/dev/null | grep -E "$pat" | grep -v lambda | head -70
      return
    fi
  done
  echo "=== $cls NOT FOUND"
}
find_cls() {
  for j in $JARS; do unzip -l "$j" 2>/dev/null | grep -E "$1" | awk -v j=$(basename $j) '{print j": "$4}'; done | sort -u | head -60
}
echo "##### fog"
find_cls "net/minecraft/client/renderer/fog/.*\.class$"
show net.minecraft.client.renderer.fog.FogRenderer "."
show net.minecraft.client.renderer.fog.FogData "."
show net.minecraft.client.renderer.fog.environment.FogEnvironment "."
show net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment "."
show net.minecraft.world.attribute.EnvironmentAttributes "static"
show net.minecraft.world.attribute.AmbientParticle "."
echo "##### blocks"
show net.minecraft.world.level.block.state.properties.BlockStateProperties "HALF|FACING|_WALL|EAST|WEST|NORTH|SOUTH|STAIRS_SHAPE|SLAB_TYPE|UP |BOTTOM|HANGING|ATTACHED|OPEN"
show net.minecraft.world.level.block.state.properties.WallSide "."
show net.minecraft.world.level.block.state.properties.Half "."
show net.minecraft.world.level.block.state.properties.SlabType "."
show net.minecraft.world.level.block.StairBlock "static"
show net.minecraft.world.level.block.TrapDoorBlock "static"
echo "##### flying"
show net.minecraft.world.entity.ai.control.FlyingMoveControl "FlyingMoveControl\("
show net.minecraft.world.entity.ai.navigation.FlyingPathNavigation "FlyingPathNavigation\(|setCan"
show net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal "WaterAvoidingRandomFlyingGoal\("
show net.minecraft.world.entity.ai.goal.RandomStrollGoal "RandomStrollGoal\("
show net.minecraft.world.entity.ai.goal.LookAtPlayerGoal "LookAtPlayerGoal\("
show net.minecraft.world.entity.ai.attributes.Attributes "FLYING_SPEED|MOVEMENT_SPEED|MAX_HEALTH|FOLLOW_RANGE|ATTACK_DAMAGE"
show net.minecraft.world.entity.Mob "createNavigation|moveControl|protected.*Control|setNoGravity|isPersistenceRequired|removeWhenFarAway|checkDespawn"
show net.minecraft.world.entity.animal.Bee "createNavigation|Bee\(|FlyingMoveControl|causeFallDamage|checkFallDamage"
show net.minecraft.world.entity.Entity "public boolean causeFallDamage|checkFallDamage|isNoGravity|public void setNoGravity|isFlapping|onFlap"
echo "##### particles client"
show net.minecraft.client.multiplayer.ClientLevel "addParticle|addAlwaysVisibleParticle"
show net.minecraft.client.renderer.entity.MobRenderer "MobRenderer\("
show net.minecraft.client.model.geom.ModelPart "public float|public boolean visible|skipDraw"
