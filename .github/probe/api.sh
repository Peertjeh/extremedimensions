#!/bin/bash
set +e
JARS=$(find ~/.gradle /home/runner/work -name "*.jar" 2>/dev/null | grep -E "minecraft|fabric-api|fabric-(object-builder|rendering|entity)" | grep -v sources)
show() {
  local cls="$1"; local pat="$2"; local path="${cls//.//}.class"
  for j in $JARS; do
    if unzip -l "$j" 2>/dev/null | grep -q " $path$"; then
      echo "=== $cls"
      javap -p -cp "$j" "$cls" 2>/dev/null | grep -E "$pat" | grep -v lambda | head -50
      return
    fi
  done
  echo "=== $cls NOT FOUND"
}
show net.minecraft.client.model.EntityModel "."
show net.minecraft.client.model.Model "public|protected"
show net.minecraft.client.model.geom.ModelPart "public float|public boolean|public net.minecraft.client.model.geom.ModelPart\(|getChild|resetPose"
show net.minecraft.client.model.geom.builders.PartDefinition "addOrReplaceChild"
show net.minecraft.client.model.geom.builders.CubeListBuilder "public"
show net.minecraft.client.model.geom.builders.CubeDeformation "public"
show net.minecraft.client.model.geom.PartPose "public static"
show net.minecraft.client.model.geom.builders.LayerDefinition "public"
show net.minecraft.client.model.geom.builders.MeshDefinition "public"
show net.minecraft.client.model.geom.ModelLayerLocation "public"
show net.minecraft.client.renderer.entity.EntityRendererProvider\$Context "public"
show net.minecraft.client.renderer.entity.MobRenderer "public|protected"
show net.minecraft.client.renderer.entity.LivingEntityRenderer "addLayer|protected|public"
show net.minecraft.client.renderer.entity.layers.EyesLayer "."
show net.minecraft.client.renderer.entity.layers.EnderEyesLayer "."
show net.minecraft.client.renderer.entity.state.LivingEntityRenderState "public"
show net.minecraft.client.renderer.entity.state.EntityRenderState "public [a-z]"
show net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry "static|interface"
show net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry "static|interface"
show net.minecraft.world.entity.monster.Monster "static|Monster\("
show net.minecraft.world.entity.ai.attributes.Attributes "MAX_HEALTH|MOVEMENT_SPEED|FOLLOW_RANGE|KNOCKBACK_RESISTANCE|ATTACK_DAMAGE"
show net.minecraft.network.syncher.SynchedEntityData "defineId"
show net.minecraft.network.syncher.SynchedEntityData\$Builder "define"
show net.minecraft.network.syncher.EntityDataSerializers "INT;|BYTE;"
show net.minecraft.world.entity.player.Player "causeFoodExhaustion"
show net.minecraft.client.renderer.rendertype.RenderTypes "eyes|entityCutoutNoCull|entityTranslucent"
show net.minecraft.client.renderer.RenderType "static.*eyes|static.*entityCutoutNoCull"
show net.minecraft.client.gui.screens.inventory.EffectsInInventory "."
show net.minecraft.client.renderer.entity.state.EndermanRenderState "."
