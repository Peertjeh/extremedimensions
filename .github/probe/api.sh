#!/bin/bash
# TEMP: signatures for a survival test player
set +e
JARS=$(find ~/.gradle -name "*.jar" 2>/dev/null | grep -E "minecraft-(client|common|merged)|authlib|netty-transport|netty-common|fabric" | grep -v sources | sort -u)
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
show net.minecraft.server.network.CommonListenerCookie "public"
show net.minecraft.server.level.ClientInformation "public static"
show net.minecraft.network.Connection "public net.minecraft.network.Connection\("
show net.minecraft.network.protocol.PacketFlow "public static final"
show net.minecraft.server.players.PlayerList "placeNewPlayer|public .* remove\("
show net.minecraft.server.level.ServerPlayer "public net.minecraft.server.level.ServerPlayer\(|spawnInvulnerableTime|ClientLoaded|clientLoaded"
show net.minecraft.world.entity.player.Player "ClientLoaded|clientLoaded"
show com.mojang.authlib.GameProfile "public com.mojang.authlib.GameProfile\("
show io.netty.channel.embedded.EmbeddedChannel "public io.netty.channel.embedded.EmbeddedChannel\("
