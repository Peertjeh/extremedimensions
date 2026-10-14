#!/bin/bash
# TEMP: signature probe for Bed Wars and Prop Hunt (javap only), printed into the build log.
set +e
JARS=$(find ~/.gradle/caches -name "*.jar" 2>/dev/null | grep -v -- "-sources" | grep -E "fabric-loom|net.fabricmc|com.mojang|minecraft" | sort -u)
CP=$(echo $JARS | tr ' ' ':')
MC=$(find ~/.gradle/caches -name "minecraft-merged*.jar" 2>/dev/null | grep -v sources | head -1)
short() { sed -E 's/java\.lang\.//g; s/java\.util\.function\.//g; s/java\.util\.//g; s/net\.minecraft\.//g; s/com\.mojang\.//g'; }
full() { for c in "$@"; do echo "=== $c"; javap -cp "$CP" -p "$c" 2>&1 | grep -v 'lambda\$' | grep -vE '^Compiled from' | short; done; }
pat() { local c="$1"; shift; echo "=== $c [filtered]"; javap -cp "$CP" -p "$c" 2>&1 | grep -v 'lambda\$' | grep -E "$*" | short; }
echo "=== locate"
unzip -l "$MC" 2>/dev/null | grep -oE "net/minecraft/[A-Za-z0-9/$]*(BlockDisplay|Interaction|Villager|ServerBossEvent|BossEvent|EntitySpawnReason|PrimedTnt|ItemEntity|Objective|DisplaySlot|NumberFormat|BlankFormat|BedPart|ClientboundSetObjectivePacket|ClientboundSetScorePacket|ClientboundResetScorePacket|ClientboundSetDisplayObjectivePacket|ClientboundBlockUpdatePacket|ObjectiveCriteria)\.class" | sort -u
full 'net.minecraft.world.entity.Display$BlockDisplay' net.minecraft.world.entity.Interaction net.minecraft.world.entity.EntitySpawnReason
pat net.minecraft.world.entity.Display "public|protected|static final" 
pat net.minecraft.world.entity.EntityType "BLOCK_DISPLAY|INTERACTION|VILLAGER;|TNT;| ITEM;|create\(|spawn\("
pat net.minecraft.world.entity.Mob "setNoAi|setPersistenceRequired"
pat net.minecraft.world.entity.Entity "setInvulnerable|setSilent|setNoGravity|setGlowingTag|setCustomName\(|addTag|getTags|setYRot|setYHeadRot|public void discard|setInvisible|setCustomNameVisible|teleportTo\(double|setPos\("
full net.minecraft.world.entity.item.PrimedTnt
pat net.minecraft.world.entity.item.ItemEntity "ItemEntity\(|PickUpDelay|setUnlimitedLifetime|getItem\(|setExtendedLifetime|lifespan|age"
pat net.minecraft.server.level.ServerLevel "explode|getEntity\(|sendBlockUpdated"
pat net.minecraft.world.level.Level "explode\("
full 'net.minecraft.world.level.Level$ExplosionInteraction' net.minecraft.server.level.ServerBossEvent 'net.minecraft.world.BossEvent$BossBarColor' 'net.minecraft.world.BossEvent$BossBarOverlay'
pat net.minecraft.world.BossEvent "public"
pat net.minecraft.world.scores.Objective "Objective\(|public"
pat net.minecraft.world.scores.criteria.ObjectiveCriteria "DUMMY|static"
full 'net.minecraft.world.scores.criteria.ObjectiveCriteria$RenderType' net.minecraft.world.scores.DisplaySlot
full net.minecraft.network.protocol.game.ClientboundSetObjectivePacket net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket net.minecraft.network.protocol.game.ClientboundSetScorePacket net.minecraft.network.protocol.game.ClientboundResetScorePacket net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
full net.minecraft.network.chat.numbers.BlankFormat net.minecraft.network.chat.numbers.NumberFormat
full net.fabricmc.fabric.api.event.player.AttackBlockCallback
pat net.minecraft.server.network.ServerGamePacketListenerImpl "handleAnimate|handlePlayerAction|handleInteract|handleUseItemOn"
pat net.minecraft.world.level.block.BedBlock "PART|FACING|OCCUPIED|public static"
full net.minecraft.world.level.block.state.properties.BedPart
pat net.minecraft.world.entity.player.Inventory "getContainerSize|getItem\(|setItem\(|getNonEquipmentItems|countItem|INVENTORY_SIZE"
pat net.minecraft.world.Container "countItem|hasAnyOf|getContainerSize"
pat net.minecraft.world.item.ItemStack "isSameItemSameComponents|getMaxStackSize|copyWithCount|public boolean is\("
pat net.minecraft.world.entity.LivingEntity "setItemSlot|getItemBySlot|addEffect\(|removeEffect\("
pat net.minecraft.world.effect.MobEffects "INVISIBILITY|BLINDNESS|SLOWNESS|GLOWING|SATURATION|RESISTANCE|JUMP_BOOST|DARKNESS"
pat net.minecraft.world.level.block.Blocks " BARREL;| CHEST;| RED_BED| WHITE_WOOL| AIR;"
pat net.minecraft.world.item.Items " IRON_INGOT;| GOLD_INGOT;| DIAMOND;| EMERALD;| SHEARS;| TNT;| ENDER_PEARL;| GOLDEN_APPLE;| STICK;| WOODEN_SWORD;| WOODEN_PICKAXE;| WATER_BUCKET;| END_STONE;| OBSIDIAN;| LADDER;"
