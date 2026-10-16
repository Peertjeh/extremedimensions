#!/bin/bash
# TEMP: signature probe for BattleHub 1.14 (javap only), printed into the job log.
set +e
JARS=$(find ~/.gradle/caches -name "*.jar" 2>/dev/null | grep -v -- "-sources" | grep -E "fabric-loom|net.fabricmc|com.mojang|minecraft" | sort -u)
CP=$(echo $JARS | tr ' ' ':')
MC=$(for j in $JARS; do unzip -l "$j" 2>/dev/null | grep -q "net/minecraft/server/MinecraftServer.class" && echo "$j" && break; done)
echo "=== mc jar: $MC"
short() { sed -E 's/java\.lang\.//g; s/java\.util\.function\.//g; s/java\.util\.//g; s/net\.minecraft\.//g; s/com\.mojang\.//g'; }
full() { for c in "$@"; do echo "=== $c"; javap -cp "$CP" -p "$c" 2>&1 | grep -v 'lambda\$' | grep -vE '^Compiled from' | short; done; }
pat() { local c="$1"; shift; echo "=== $c [filtered]"; javap -cp "$CP" -p "$c" 2>&1 | grep -v 'lambda\$' | grep -E "$*" | short; }
names() { echo "=== $1 [names]"; javap -cp "$CP" "$1" 2>&1 | grep "public static final" | sed -E 's/.* ([A-Za-z0-9_]+);$/\1/' | tr '\n' ' ' | fold -s -w 220; echo; }
cls() { echo "=== classes $1"; unzip -l "$MC" 2>/dev/null | grep -oE "$1[A-Za-z0-9]*\.class" | sed 's/\.class//' | grep -v '\$' | tr '\n' ' ' | fold -s -w 220; echo; }

# --- bug fixes
cls net/minecraft/network/protocol/game/Clientbound.*Held
cls net/minecraft/network/protocol/game/Clientbound.*Carried
full net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket net.minecraft.world.Clearable net.minecraft.world.level.material.FlowingFluid
pat net.minecraft.server.level.ServerLevel "setChunkForced|getForceLoadedChunks|sendParticles|public .*getEntities"
pat net.minecraft.world.level.block.Block "UPDATE_"
pat net.minecraft.world.entity.Entity " pick\(|public .*discard"
pat net.minecraft.world.entity.player.Player "blockInteractionRange|entityInteractionRange"
pat net.minecraft.world.item.BucketItem "public|protected"
pat net.minecraft.world.item.Item "getPlayerPOVHitResult"
echo "=== entity classes"
unzip -l "$MC" 2>/dev/null | grep -oE "net/minecraft/world/entity/[A-Za-z0-9/]*(ExperienceOrb|FallingBlockEntity|AreaEffectCloud|ItemEntity|PrimedTnt|FireworkRocketEntity|LightningBolt|Projectile|ArmorStand|Display|Interaction)\.class" | sort -u

# --- cosmetics
names net.minecraft.core.particles.ParticleTypes
cls net/minecraft/core/particles/
full net.minecraft.core.particles.DustParticleOptions net.minecraft.core.particles.DustColorTransitionOptions net.minecraft.core.particles.ColorParticleOption net.minecraft.core.particles.TrailParticleOption net.minecraft.core.particles.SpellParticleOption net.minecraft.core.particles.PowerParticleOption net.minecraft.core.particles.ItemParticleOption net.minecraft.core.particles.BlockParticleOption
names net.minecraft.core.component.DataComponents
cls net/minecraft/world/item/component/
full net.minecraft.world.item.component.DyedItemColor net.minecraft.world.item.component.CustomModelData net.minecraft.world.item.component.TooltipDisplay net.minecraft.world.item.component.ItemLore net.minecraft.world.item.component.Fireworks net.minecraft.world.item.component.FireworkExplosion 'net.minecraft.world.item.component.FireworkExplosion$Shape'
pat net.minecraft.world.entity.projectile.FireworkRocketEntity "FireworkRocketEntity\(|explode|public"
pat net.minecraft.world.entity.LightningBolt "setVisualOnly|LightningBolt\(|setCosmetic"
pat net.minecraft.world.entity.EntityType "LIGHTNING_BOLT|FIREWORK_ROCKET|ITEM_DISPLAY|BLOCK_DISPLAY|TEXT_DISPLAY|ARMOR_STAND|public .* create\("
full 'net.minecraft.world.entity.Display$ItemDisplay'
pat net.minecraft.world.entity.Display "public|protected .*set|DATA_.*ID"
pat net.minecraft.world.item.ItemStack "public .* set\(|public .* get\(|public .* remove\(|copyWithCount|public ItemStack\("
names net.minecraft.sounds.SoundEvents
names net.minecraft.world.item.Items

# --- maps and hub
names net.minecraft.world.level.block.Blocks
names net.minecraft.world.level.block.state.properties.BlockStateProperties
full net.minecraft.world.level.block.state.properties.StairsShape net.minecraft.world.level.block.state.properties.Half net.minecraft.world.level.block.state.properties.WallSide
echo "=== probe done"
