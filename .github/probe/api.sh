#!/bin/bash
set +e
JARS=$(find ~/.gradle /home/runner/work -name "*.jar" 2>/dev/null | grep -E "minecraft|fabric" | grep -v sources | sort -u)
show() {
  local cls="$1"; local pat="$2"; local path="${cls//.//}.class"
  for j in $JARS; do
    if unzip -l "$j" 2>/dev/null | grep -q " $path$"; then
      echo "=== $cls  ($(basename $j))"
      javap -p -cp "$j" "$cls" 2>/dev/null | grep -E "$pat" | grep -v lambda | head -80
      return
    fi
  done
  echo "=== $cls NOT FOUND"
}
find_cls() {
  for j in $JARS; do unzip -l "$j" 2>/dev/null | grep -E "$1" | awk -v j=$(basename $j) '{print j": "$4}'; done | sort -u | head -60
}
echo "##### rendering api classes"
find_cls "fabric/api/client/rendering/v1/.*\.class$"
find_cls "net/minecraft/client/renderer/(Sky|LevelRenderer|DimensionSpecialEffects|state/Sky|state/Level)[A-Za-z$]*\.class$"
find_cls "net/minecraft/world/(level/dimension|attribute)/[A-Za-z$]*\.class$"
show net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry "."
show net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry\$SkyRenderer "."
show net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents "."
show net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents "."
show net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext "."
show net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext "."
show net.fabricmc.fabric.api.client.rendering.v1.world.WorldTerrainRenderContext "."
show net.minecraft.client.renderer.SkyRenderer "public|private|protected"
show net.minecraft.client.renderer.LevelRenderer "[Ss]ky"
show net.minecraft.world.level.dimension.DimensionType "Sky|sky|record|public"
show net.minecraft.world.level.dimension.DimensionType\$Skybox "."
show net.minecraft.client.renderer.state.SkyRenderState "."
show net.fabricmc.fabric.api.client.screen.v1.Screens "."
show net.minecraft.client.gui.GuiGraphicsExtractor "public"
show net.minecraft.client.gui.components.AbstractWidget "public|protected abstract"
show net.minecraft.client.gui.components.AbstractButton "public|protected"
show net.minecraft.client.gui.components.Button "public|protected"
show net.minecraft.client.gui.screens.inventory.InventoryScreen "."
show net.minecraft.client.gui.components.ImageButton "."
show net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen "."
for j in $JARS; do
  if unzip -l "$j" 2>/dev/null | grep -q "data/minecraft/dimension_type/the_end.json"; then
    unzip -p "$j" data/minecraft/dimension_type/the_end.json; echo
    unzip -l "$j" | grep -E "environment|end_sky|sky" | head; break
  fi
done
