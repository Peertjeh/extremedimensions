#!/bin/bash
# TEMP: print the 26.3 signatures the 2.3.0 penalty overhaul relies on
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
find_cls "npc/(villager/)?Villager\.class$|item/trading/MerchantOffer\.class$|MerchantOffer\.class$"
show net.minecraft.world.entity.npc.villager.Villager "updateSpecialPrices|resetSpecialPrices|startTrading|getOffers"
show net.minecraft.world.entity.npc.Villager "updateSpecialPrices|resetSpecialPrices|startTrading|getOffers"
show net.minecraft.world.item.trading.MerchantOffer "SpecialPrice|getBaseCost|getCost|getItemCost|getPriceMultiplier"
show net.minecraft.world.entity.player.Player "stopSleep|getSleepTimer|isSleeping|totalExperience|giveExperiencePoints|giveExperienceLevels|experienceLevel|getFoodData|causeFoodExhaustion"
show net.minecraft.world.entity.LivingEntity "stopSleeping|isSleeping|getSleepingPos"
show net.minecraft.server.level.ServerPlayer "stopSleepInBed|startSleepInBed|isSleeping"
show net.minecraft.world.item.ItemStack "hurtAndBreak|isDamageableItem|getMaxDamage|setDamageValue"
show net.minecraft.world.level.Level "getDayTime|isDarkOutside|isBrightOutside|isNight|isDay|getSkyDarken|canSeeSky|getGameTime"
show net.minecraft.server.level.ServerLevel "getDayTime|isDarkOutside|isBrightOutside|isNight|setDayTime"
show net.minecraft.world.food.FoodData "getFoodLevel|setFoodLevel|getSaturationLevel|setSaturation|eat"
show net.minecraft.world.entity.ai.attributes.Attributes "FALL_DAMAGE|BURNING|GRAVITY|JUMP|INTERACTION_RANGE|ARMOR_TOUGHNESS|ATTACK_SPEED|LUCK|SAFE_FALL"
show net.fabricmc.fabric.api.event.player.AttackEntityCallback "interact|EVENT"
show net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents "EVENT|START|ALLOW"
show net.minecraft.world.entity.EquipmentSlot "MAINHAND"
show net.minecraft.client.gui.GuiGraphicsExtractor "public void (fill|text|fakeItem|item|outline|renderOutline|pose)"
