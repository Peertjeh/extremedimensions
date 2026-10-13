#!/bin/bash
# TEMP: second, targeted signature probe (javap only).
set +e
OUT=${1:-probe.txt}
JARS=$(find ~/.gradle/caches -name "*.jar" 2>/dev/null | grep -v -- "-sources" | grep -E "fabric-loom|net.fabricmc|com.mojang|minecraft" | sort -u)
MC=""
for j in $JARS; do
  if unzip -Z1 "$j" 2>/dev/null | grep -q "^net/minecraft/server/MinecraftServer.class$"; then MC="$MC $j"; fi
done
CP=$(echo $JARS | tr ' ' ':')
{
short() { sed -E 's/java\.lang\.//g; s/java\.util\.function\.//g; s/java\.util\.//g; s/net\.minecraft\.//g; s/com\.mojang\.//g; s/net\.fabricmc\.fabric\.api\.//g'; }
full() { for c in "$@"; do echo "=== $c"; javap -cp "$CP" -protected "$c" 2>&1 | grep -v 'lambda\$' | grep -vE '^Compiled from' | short; done; }
pat() { local c="$1"; shift; echo "=== $c [filtered]"; javap -cp "$CP" -p "$c" 2>&1 | grep -v 'lambda\$' | grep -E "$*" | short; }
echo "##### COLOURED"
pat net.minecraft.world.level.block.Blocks " (WOOL|BANNER|WALL_BANNER|STAINED_GLASS_PANE|STAINED_GLASS|CONCRETE|CARPET|TERRACOTTA|DYED_TERRACOTTA|GLAZED_TERRACOTTA|BED|CANDLE|SHULKER_BOX|CONCRETE_POWDER|WOOL_SLAB|WOOL_STAIRS);"
pat net.minecraft.world.item.Items " (DYE|WOOL|BANNER|STAINED_GLASS_PANE|STAINED_GLASS|CONCRETE|BED|CARPET|TERRACOTTA|DYED_TERRACOTTA|CANDLE|SHULKER_BOX);"
T=$(javap -cp "$CP" -public net.minecraft.world.level.block.Blocks 2>/dev/null | grep " WOOL;" | sed -E 's/.*public static final ([a-zA-Z0-9_.$]+).*/\1/')
echo "collection type: $T"
[ -n "$T" ] && full "$T"
for c in $(for j in $MC; do unzip -Z1 "$j" | grep -iE "^net/minecraft/.*(Colou?r(ed)?(Set|Map|Collection|Blocks|Items|Variants)|DyedBlock|ByColor)[A-Za-z$]*\.class$"; done | sort -u | sed 's/\.class$//; s#/#.#g' | head -12); do full "$c"; done
full net.minecraft.world.item.DyeColor
echo "##### RESPAWN"
full 'net.minecraft.world.level.storage.LevelData$RespawnData' net.minecraft.core.GlobalPos
echo "##### BLOCKSTATE"
pat 'net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase' "public.* is\(|public.*Block getBlock"
full net.minecraft.world.item.ItemInstance
pat net.minecraft.world.level.EntityGetter "getEntitiesOfClass|getNearestPlayer|players\(|getPlayerByUUID"
full net.minecraft.world.level.LevelWriter
pat net.minecraft.core.registries.BuiltInRegistries "static final"
pat net.minecraft.world.level.block.state.properties.BlockStateProperties " (FACING|HORIZONTAL_FACING|HALF|SLAB_TYPE|ROTATION_16|AXIS|HORIZONTAL_AXIS|LIT|WATERLOGGED|SNOWY|LAYERS|STAIRS_SHAPE|OPEN|DOUBLE_BLOCK_HALF|LOWER|UP|ATTACHED|HANGING|PERSISTENT|DISTANCE|NORTH_WALL|EAST_WALL|SOUTH_WALL|WEST_WALL|NORTH|EAST|SOUTH|WEST);"
pat net.minecraft.world.level.block.StairBlock "static"
pat net.minecraft.world.level.block.SlabBlock "static"
pat net.minecraft.world.level.block.BannerBlock "static"
pat net.minecraft.world.level.block.LeavesBlock "static"
pat net.minecraft.world.level.block.LanternBlock "static"
pat net.minecraft.world.level.block.RotatedPillarBlock "static"
pat net.minecraft.world.level.block.FenceBlock "static|CrossCollisionBlock"
pat net.minecraft.world.level.block.CrossCollisionBlock "static"
pat net.minecraft.world.level.block.SnowLayerBlock "static"
pat net.minecraft.world.entity.projectile.arrow.AbstractArrow "inGround|InGround|isInGround"
pat net.minecraft.world.entity.player.Player "drop\("
pat net.minecraft.world.inventory.InventoryMenu "public"
pat net.minecraft.world.level.block.Block "public static .*(byItem|getId|stateById)|defaultBlockState|asItem"
pat net.minecraft.world.item.BlockItem "getBlock"
echo "##### GAMETEST"
for j in $JARS; do unzip -Z1 "$j" 2>/dev/null | grep -E "^net/fabricmc/fabric/api/gametest/v1/[A-Za-z]+\.class$"; done | sort -u
for c in $(for j in $JARS; do unzip -Z1 "$j" 2>/dev/null | grep -E "^net/fabricmc/fabric/api/gametest/v1/[A-Za-z]+\.class$"; done | sort -u | sed 's/\.class$//; s#/#.#g'); do full $c; done
pat net.minecraft.gametest.framework.GameTestHelper "public.*(makeMockPlayer|makeMockServerPlayer|succeed|fail\(|getLevel|absolutePos|runAfterDelay|startSequence|onEachTick|succeedWhen)"
echo "##### DATA"
for j in $MC; do
  if unzip -Z1 "$j" | grep -q "data/minecraft/dimension_type/overworld.json"; then
    for f in dimension_type/the_nether.json dimension_type/the_end.json dimension_type/overworld_caves.json; do echo "=== $f"; unzip -p "$j" "data/minecraft/$f" | tr -s ' \n' ' ' | head -c 3000; echo; done
    unzip -Z1 "$j" | grep -E "data/minecraft/(timeline|world_clock|clock)" | head -40
    for f in $(unzip -Z1 "$j" | grep -E "data/minecraft/(timeline|world_clock|clock)/[a-z_]+\.json$" | head -8); do echo "=== $f"; unzip -p "$j" "$f" | tr -s ' \n' ' ' | head -c 2500; echo; done
    unzip -Z1 "$j" | grep -E "data/minecraft/(loot_table/blocks|recipe)/(red_wool|red_banner|white_wool)" | head
    unzip -Z1 "$j" | grep -E "data/minecraft/tags/.*wool" | head -5
    break
  fi
done
for j in $MC; do unzip -Z1 "$j" | grep -E "assets/minecraft/(blockstates|items)/(red_wool|red_banner|white_wool|red_dye)\.json" | head -5; done
} > "$OUT" 2>&1
wc -l "$OUT"
