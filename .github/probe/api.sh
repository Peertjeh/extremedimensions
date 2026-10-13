#!/bin/bash
# TEMP: print the 26.3 signatures the 2.4.0 curses rely on
set +e
JARS=$(find ~/.gradle -name "*.jar" 2>/dev/null | grep -E "minecraft-(client|common|merged|clientonly)|fabric" | grep -v sources | sort -u)
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
  for j in $JARS; do unzip -l "$j" 2>/dev/null | grep -E "$1" | awk -v j=$(basename $j) '{print j": "$4}'; done | sort -u | head -40
}
find_cls "PlayerFace|PlayerSkin|/PlayerInfo\.class|SkinManager\.class|ServerLivingEntityEvents"
show net.minecraft.world.entity.LivingEntity "hurtServer|addEffect|forceAddEffect|actuallyHurt|getArmorSlots|getItemBySlot|isHurt|heal\("
show net.minecraft.world.entity.Entity "TicksFrozen|AirSupply|isInPowderSnow|isFullyFrozen|getMaxAirSupply"
show net.minecraft.world.effect.MobEffectInstance "public"
show net.minecraft.world.effect.MobEffect "getCategory|isBeneficial"
show net.minecraft.world.effect.MobEffectCategory "public"
show net.minecraft.tags.DamageTypeTags "IS_FIRE|IS_FREEZING|IS_EXPLOSION|BYPASSES_ARMOR|IS_DROWNING|IS_FALL"
show net.minecraft.world.damagesource.DamageSource "public .* is\(|getEntity|getDirectEntity"
show net.minecraft.world.entity.ai.attributes.Attributes "SAFE_FALL|JUMP|MAX_HEALTH|MOVEMENT_SPEED|OXYGEN"
show net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents "EVENT|AFTER|ALLOW"
show 'net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents$AfterDamage' "afterDamage"
show net.minecraft.world.food.FoodData "public"
show net.minecraft.world.entity.player.Player "causeFoodExhaustion|getFoodData|getInventory|drop\("
show net.minecraft.world.entity.player.Inventory "public .*(getContainerSize|getItem|setItem|add\(|getFreeSlot|getNonEquipmentItems|removeItem|getSelected)"
show net.minecraft.client.gui.components.PlayerFaceRenderer "public"
show net.minecraft.client.gui.components.PlayerFaceExtractor "public"
show net.minecraft.client.multiplayer.PlayerInfo "getSkin|getProfile"
show net.minecraft.client.multiplayer.ClientPacketListener "getPlayerInfo|getOnlinePlayers"
show net.minecraft.world.entity.player.PlayerSkin "public"
show net.minecraft.client.resources.PlayerSkin "public"
show net.minecraft.core.ClientAsset "public"
show 'net.minecraft.core.ClientAsset$Texture' "public"
show net.minecraft.client.gui.GuiGraphicsExtractor "public .*(blit|sprite|texture|item|tooltip|setTooltip)"
show net.minecraft.client.gui.screens.inventory.AbstractContainerScreen "leftPos|topPos|imageWidth|imageHeight"
show net.minecraft.client.player.AbstractClientPlayer "getSkin|public"
