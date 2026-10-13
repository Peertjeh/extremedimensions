#!/bin/bash
# TEMP: small signature probe (javap only), printed straight into the build log.
set +e
JARS=$(find ~/.gradle/caches -name "*.jar" 2>/dev/null | grep -v -- "-sources" | grep -E "fabric-loom|net.fabricmc|com.mojang|minecraft" | sort -u)
CP=$(echo $JARS | tr ' ' ':')
short() { sed -E 's/java\.lang\.//g; s/java\.util\.function\.//g; s/java\.util\.//g; s/net\.minecraft\.//g; s/com\.mojang\.//g'; }
full() { for c in "$@"; do echo "=== $c"; javap -cp "$CP" -p "$c" 2>&1 | grep -v 'lambda\$' | grep -vE '^Compiled from' | short; done; }
pat() { local c="$1"; shift; echo "=== $c [filtered]"; javap -cp "$CP" -p "$c" 2>&1 | grep -v 'lambda\$' | grep -E "$*" | short; }
full net.minecraft.server.network.CommonListenerCookie net.minecraft.server.players.NameAndId net.minecraft.network.protocol.PacketFlow
pat net.minecraft.network.Connection "Connection\(|channel|public .*(send|isConnected|isMemoryConnection|setupInboundProtocol|setupOutboundProtocol|setListener|handleDisconnection|setReadOnly|tick|disconnect)\("
pat net.minecraft.world.level.block.Block "static final int UPDATE"
pat net.minecraft.world.entity.Entity "unsetRemoved|setRemoved|onRemoval|public .* remove\("
pat net.minecraft.util.Unit "INSTANCE"
pat net.minecraft.server.players.PlayerList "placeNewPlayer|remove\(|respawn"
pat net.minecraft.server.network.ServerGamePacketListenerImpl "ServerGamePacketListenerImpl\(|public void tick|resetPosition|ackBlockChangesUpTo"
pat net.minecraft.world.entity.ai.attributes.Attributes "MAX_HEALTH|STEP_HEIGHT|KNOCKBACK_RESISTANCE|GRAVITY|SAFE_FALL"
pat net.minecraft.world.entity.LivingEntity "public void (die|kill)|setLastHurtByPlayer|lastHurtByPlayerMemoryTime|getCombatTracker|invulnerableTime"
pat net.minecraft.world.item.component.CustomModelData "CustomModelData\("
