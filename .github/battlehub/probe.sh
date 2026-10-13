#!/bin/bash
# TEMP: dump the Minecraft 26.3 / Fabric API signatures BattleHub needs (javap only, no decompiled code).
set +e
OUT=${1:-probe.txt}
JARS=$(find ~/.gradle/caches -name "*.jar" 2>/dev/null | grep -v -- "-sources" | grep -E "fabric-loom|net.fabricmc|com.mojang|minecraft" | sort -u)
MC=""
for j in $JARS; do
  if unzip -Z1 "$j" 2>/dev/null | grep -q "^net/minecraft/server/MinecraftServer.class$"; then MC="$MC $j"; fi
done
CP=$(echo $JARS | tr ' ' ':')
{
echo "##### MC jars: $MC"
echo "##### fabric api modules:"; echo "$JARS" | grep -oE "fabric-[a-z0-9-]+-v[0-9]+|fabric-[a-z0-9-]+-api" | sort -u | tr '\n' ' '; echo
short() { sed -E 's/java\.lang\.//g; s/java\.util\.function\.//g; s/java\.util\.//g; s/net\.minecraft\.//g; s/com\.mojang\.//g; s/net\.fabricmc\.fabric\.api\.//g'; }
full() { for c in "$@"; do echo "=== $c"; javap -cp "$CP" -protected "$c" 2>&1 | grep -v 'lambda\$' | grep -vE '^Compiled from' | short; done; }
pat() { local c="$1"; shift; echo "=== $c [filtered]"; javap -cp "$CP" -protected "$c" 2>&1 | grep -v 'lambda\$' | grep -E "$*" | short; }
names() { for c in "$@"; do echo "=== $c [static field names]"; javap -cp "$CP" -public "$c" 2>&1 | grep "static final" | sed -E 's/.* ([A-Za-z0-9_$]+);$/\1/' | tr '\n' ' ' | fold -w 220; echo; done; }
typed() { for c in "$@"; do echo "=== $c [static fields typed]"; javap -cp "$CP" -public "$c" 2>&1 | grep "static" | grep -v "(" | sed -E 's/public static final //' | short | tr '\n' ';' | fold -w 220; echo; done; }
findc() { echo "=== find $1"; for j in $MC $JARS; do unzip -Z1 "$j" 2>/dev/null | grep -E "$1" ; done | grep -v '\$[0-9]' | sort -u | head -${2:-40}; }

echo "##### LOCATE"
findc "/(TeleportTransition|Relative|ClickType|ContainerInput|ResolvableProfile|ItemLore|CustomData|TooltipDisplay|DyedItemColor|Unbreakable|ChargedProjectiles|ValueOutput|ValueInput|TagValueOutput|TagValueInput|ProblemReporter|GameRules|PermissionSet|Permissions|PermissionLevel|LevelBasedPermissionSet|AbstractArrow|Projectile|WindCharge|PrimedTnt|ItemEntity|FireworkRocketEntity|ClientInformation|TicketType|WorldBorder|ServerScoreboard|PlayerTeam|ServerboundContainerClickPacket|HashedStack|RespawnData|LevelData)\.class$" 80
findc "net/minecraft/server/permissions/.*\.class$" 40
findc "net/fabricmc/fabric/api/(entity|event|networking|command|message|gametest)/.*\.class$" 120

echo "##### PLAYER"
full net.minecraft.server.level.ServerPlayer net.minecraft.world.entity.player.Player net.minecraft.world.entity.player.Inventory net.minecraft.world.entity.player.Abilities net.minecraft.world.food.FoodData net.minecraft.server.level.ServerPlayerGameMode net.minecraft.world.level.GameType net.minecraft.world.entity.EquipmentSlot net.minecraft.server.level.ClientInformation com.mojang.authlib.GameProfile
echo "##### ENTITY"
full net.minecraft.world.entity.LivingEntity net.minecraft.world.entity.Entity net.minecraft.world.entity.Relative net.minecraft.world.level.portal.TeleportTransition net.minecraft.world.effect.MobEffectInstance
typed net.minecraft.world.effect.MobEffects
echo "##### SERVER"
full net.minecraft.server.MinecraftServer net.minecraft.server.players.PlayerList net.minecraft.server.level.ServerLevel net.minecraft.world.level.Level net.minecraft.world.level.storage.LevelResource net.minecraft.world.level.border.WorldBorder net.minecraft.server.level.ServerChunkCache net.minecraft.server.level.TicketType net.minecraft.world.level.ChunkPos
echo "##### ITEMS"
full net.minecraft.world.item.ItemStack net.minecraft.world.item.component.ItemLore net.minecraft.world.item.component.CustomData net.minecraft.world.item.component.ResolvableProfile net.minecraft.world.item.component.TooltipDisplay net.minecraft.world.item.component.DyedItemColor net.minecraft.world.item.component.ChargedProjectiles net.minecraft.world.item.enchantment.ItemEnchantments net.minecraft.world.item.enchantment.EnchantmentHelper
typed net.minecraft.core.component.DataComponents net.minecraft.world.item.enchantment.Enchantments
pat net.minecraft.world.item.Item "getDefaultInstance|getName|getDescriptionId|builtInRegistryHolder|components\(|getDefaultMaxStackSize"
echo "##### TEXT"
full net.minecraft.network.chat.Component net.minecraft.network.chat.MutableComponent net.minecraft.network.chat.Style net.minecraft.network.chat.TextColor net.minecraft.network.chat.ClickEvent 'net.minecraft.network.chat.ClickEvent$RunCommand' 'net.minecraft.network.chat.ClickEvent$SuggestCommand' net.minecraft.network.chat.HoverEvent 'net.minecraft.network.chat.HoverEvent$ShowText' net.minecraft.ChatFormatting
echo "##### PACKETS"
full net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket net.minecraft.network.protocol.game.ClientboundSoundPacket net.minecraft.network.protocol.game.ClientboundClearTitlesPacket net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
pat net.minecraft.server.network.ServerGamePacketListenerImpl "public|protected"
pat net.minecraft.server.network.ServerCommonPacketListenerImpl "public|protected"
echo "##### MENUS"
full net.minecraft.world.inventory.AbstractContainerMenu net.minecraft.world.inventory.ChestMenu net.minecraft.world.inventory.ClickType net.minecraft.world.inventory.ContainerInput net.minecraft.world.inventory.Slot net.minecraft.world.SimpleMenuProvider net.minecraft.world.MenuProvider net.minecraft.world.SimpleContainer net.minecraft.world.Container
names net.minecraft.world.inventory.MenuType
echo "##### SCOREBOARD"
full net.minecraft.world.scores.Scoreboard net.minecraft.world.scores.PlayerTeam net.minecraft.world.scores.Team 'net.minecraft.world.scores.Team$CollisionRule' 'net.minecraft.world.scores.Team$Visibility' net.minecraft.server.ServerScoreboard
echo "##### COMMANDS"
full net.minecraft.commands.Commands net.minecraft.commands.CommandSourceStack net.minecraft.commands.arguments.EntityArgument
for c in $(for j in $MC; do unzip -Z1 "$j" | grep -E "^net/minecraft/server/permissions/[A-Za-z]+\.class$"; done | sort -u | sed 's/\.class$//; s#/#.#g'); do full $c; done
echo "##### DAMAGE"
full net.minecraft.world.damagesource.DamageSource net.minecraft.world.damagesource.DamageSources
names net.minecraft.world.damagesource.DamageTypes net.minecraft.tags.DamageTypeTags
echo "##### REGISTRIES"
full net.minecraft.resources.Identifier net.minecraft.resources.ResourceKey net.minecraft.resources.RegistryOps net.minecraft.core.Registry net.minecraft.core.DefaultedRegistry 'net.minecraft.core.HolderLookup$Provider' 'net.minecraft.core.HolderGetter' net.minecraft.core.Holder
names net.minecraft.core.registries.BuiltInRegistries net.minecraft.core.registries.Registries
echo "##### NBT"
full net.minecraft.nbt.NbtIo net.minecraft.nbt.NbtOps net.minecraft.nbt.CompoundTag net.minecraft.nbt.NbtAccounter net.minecraft.world.level.storage.TagValueOutput net.minecraft.world.level.storage.TagValueInput net.minecraft.world.level.storage.ValueOutput net.minecraft.world.level.storage.ValueInput net.minecraft.util.ProblemReporter
echo "##### WORLD"
full net.minecraft.core.BlockPos net.minecraft.core.Vec3i net.minecraft.world.phys.Vec3 net.minecraft.world.phys.AABB net.minecraft.core.Direction
pat net.minecraft.world.level.block.state.BlockBehaviour\$BlockStateBase "public.*(is\(|getBlock|isAir|blocksMotion|isSolid|getValue|hasProperty|canSurvive|isFaceSturdy)"
pat net.minecraft.world.level.block.state.StateHolder "public.*(setValue|getValue|trySetValue)"
typed net.minecraft.world.level.block.state.properties.BlockStateProperties
full net.minecraft.world.level.block.state.properties.SlabType net.minecraft.world.level.block.state.properties.Half net.minecraft.world.level.block.state.properties.WallSide net.minecraft.world.level.block.state.properties.DoubleBlockHalf
names net.minecraft.world.level.block.Blocks net.minecraft.world.item.Items
echo "##### ENTITIES"
full net.minecraft.world.entity.projectile.Projectile net.minecraft.world.entity.projectile.arrow.AbstractArrow net.minecraft.world.entity.projectile.AbstractArrow net.minecraft.world.entity.item.ItemEntity net.minecraft.world.entity.item.PrimedTnt net.minecraft.world.entity.projectile.FireworkRocketEntity
names net.minecraft.world.entity.EntityType
echo "##### SOUNDS/PARTICLES"
typed net.minecraft.sounds.SoundEvents
names net.minecraft.core.particles.ParticleTypes
full net.minecraft.sounds.SoundSource
echo "##### GAMERULES"
pat net.minecraft.world.level.gamerules.GameRules "public"
echo "##### FABRIC"
for c in net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents net.fabricmc.fabric.api.event.player.UseItemCallback net.fabricmc.fabric.api.event.player.UseBlockCallback net.fabricmc.fabric.api.event.player.AttackBlockCallback net.fabricmc.fabric.api.event.player.AttackEntityCallback net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback net.fabricmc.fabric.api.entity.FakePlayer net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent net.fabricmc.fabric.api.message.v1.ServerMessageEvents; do
  full $c
  for n in $(for j in $JARS; do unzip -Z1 "$j" 2>/dev/null | grep -E "^${c//.//}\\\$[A-Za-z]+\.class$"; done | sort -u | sed 's/\.class$//; s#/#.#g'); do full "$n"; done
done
echo "##### DATA"
for j in $MC; do
  if unzip -Z1 "$j" | grep -q "data/minecraft/dimension_type/overworld.json"; then
    for f in dimension_type/overworld.json worldgen/biome/the_void.json worldgen/biome/plains.json worldgen/biome/snowy_plains.json worldgen/biome/nether_wastes.json; do echo "=== $f"; unzip -p "$j" "data/minecraft/$f" | tr -s ' \n' ' ' | head -c 4000; echo; done
    break
  fi
done
} > "$OUT" 2>&1
wc -l "$OUT"
