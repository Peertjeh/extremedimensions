#!/bin/bash
# TEMP: print the 26.3 signatures 2.4.1 relies on
set +e
JARS=$(find ~/.gradle -name "*.jar" 2>/dev/null | grep -E "minecraft-(client|common|merged|clientonly|client-only)|fabric" | grep -v sources | sort -u)
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
show net.minecraft.world.entity.player.Inventory "."
show net.minecraft.world.inventory.Slot "."
show net.minecraft.world.entity.item.ItemEntity "playerTouch|getItem|PickUp|pickup"
show net.minecraft.world.entity.player.Player "take\(|onItemPickup|getInventory"
show net.minecraft.client.gui.screens.inventory.AbstractContainerScreen "."
show net.minecraft.client.gui.screens.inventory.MerchantScreen "extract|render"
show net.minecraft.client.renderer.entity.layers.EyesLayer "."
show net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer "."
show 'net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer$AlphaFunction' "."
show net.minecraft.client.renderer.entity.layers.RenderLayer "."
show net.minecraft.client.renderer.entity.WardenRenderer "."
show net.minecraft.client.renderer.entity.state.WardenRenderState "."
show net.minecraft.client.renderer.entity.state.EntityRenderState "ageInTicks|partialTick"
show net.minecraft.client.renderer.rendertype.RenderTypes "eyes|Emissive|emissive|Translucent"
show net.minecraft.client.renderer.SubmitNodeCollector "submitModel"
show net.minecraft.client.renderer.OrderedSubmitNodeCollector "submitModel"
show net.minecraft.world.entity.Mob "getTarget|isAggressive"
show net.minecraft.world.entity.monster.warden.Warden "heart|Heart"
show net.fabricmc.fabric.api.client.screen.v1.ScreenEvents "public"
show net.minecraft.world.inventory.AbstractContainerMenu "slots|moveItemStackTo|getSlot\("
show net.minecraft.util.ARGB "public static int (color|white|multiplyAlpha)"
