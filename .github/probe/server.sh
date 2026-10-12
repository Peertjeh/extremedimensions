#!/bin/bash
# TEMP: boot a Fabric 26.3 server with the mod and build the Seam showcase
set -u
mkdir -p /tmp/srv/mods && cd /tmp/srv
INST=$(curl -s https://meta.fabricmc.net/v2/versions/installer | python3 -c "import sys,json;print(json.load(sys.stdin)[0]['version'])")
curl -s -o server.jar "https://meta.fabricmc.net/v2/versions/loader/26.3/0.19.5/$INST/server/jar"
curl -s -o mods/fabric-api.jar "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.162.0%2B26.3/fabric-api-0.162.0%2B26.3.jar"
cp $(ls $GITHUB_WORKSPACE/build/libs/*.jar | grep -v sources) mods/
echo "eula=true" > eula.txt
printf 'online-mode=false\nspawn-protection=0\n' > server.properties
mkfifo in
(java -Xmx3G -jar server.jar nogui < in > out.log 2>&1 &)
exec 3>in
for i in $(seq 1 180); do grep -q "Done (" out.log && break; sleep 2; done
send() { echo "$1" >&3; sleep 2; }
send 'tether seam_showcase'
for i in $(seq 1 90); do grep -q "Seam architect: done" out.log && break; sleep 2; done
X=2916
send "execute in extremedimensions:the_seam if block $X 95 $X extremedimensions:seam_vein run say SPIRE_CORE_OK"
send "execute in extremedimensions:the_seam unless block $X 90 $X minecraft:air run say ISLAND_OK"
send 'tether break_seal'
send 'stop'
sleep 15
sed -n '/Loading Minecraft/,$p' out.log | grep -v "^\s*$" | grep -iE "seam|exception|error|_OK|warn|caused|at com.extreme" | head -80
