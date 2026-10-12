#!/bin/bash
set +e
J=$(find ~/.gradle -name "minecraft-clientonly-deobf-26.3.jar" | head -1)
C=$(find ~/.gradle -name "*.jar" | grep -E "minecraft-common-7e|blaze3d|renderpearl" | tr '\n' ':')
echo "JAR $J"
javap -c -p -cp "$J:$C" net.minecraft.client.renderer.SkyRenderer > /tmp/sky.txt 2>&1
for m in "SkyRenderer(net" "private void renderEndSky" "private static com.mojang.renderpearl.api.buffers.GpuBuffer buildEndSky" "public void render(" "private net.minecraft.client.renderer.texture.AbstractTexture getTexture"; do
  echo "######## $m"
  awk -v m="$m" 'index($0,m){p=1} p{print} p&&/^$/{exit}' /tmp/sky.txt | head -150
done
javap -p -cp "$J:$C" net.minecraft.client.renderer.state.level.SkyRenderState
find ~/.gradle -name "*.jar" | grep -iE "mixin" | head
