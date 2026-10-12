#!/bin/bash
# TEMP: boot a Fabric 26.3 server with the mod and poke the new content
set -u
mkdir -p /tmp/srv/mods && cd /tmp/srv
INST=$(curl -s https://meta.fabricmc.net/v2/versions/installer | python3 -c "import sys,json;print(json.load(sys.stdin)[0]['version'])")
curl -s -o server.jar "https://meta.fabricmc.net/v2/versions/loader/26.3/0.19.5/$INST/server/jar"
curl -s -o mods/fabric-api.jar "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.162.0%2B26.3/fabric-api-0.162.0%2B26.3.jar"
cp $(ls $GITHUB_WORKSPACE/build/libs/*.jar | grep -v sources) mods/
ls -la mods
echo "eula=true" > eula.txt
printf 'online-mode=false\nspawn-protection=0\n' > server.properties
mkfifo in
(java -Xmx3G -jar server.jar nogui < in > out.log 2>&1 &)
exec 3>in
for i in $(seq 1 180); do grep -qE "Done \(|Exception|Error" out.log && grep -q "Done (" out.log && break; sleep 2; done
send() { echo "$1" >&3; sleep 2; }
send 'execute in extremedimensions:the_seam run forceload add 0 0'
send 'execute in extremedimensions:the_seam run setblock 0 100 0 extremedimensions:hush_stone'
send 'execute in extremedimensions:the_seam if block 0 100 0 extremedimensions:hush_stone run say SEAM_OK'
send 'forceload add 0 0'
send 'summon extremedimensions:hollowling 0 120 0'
send 'summon extremedimensions:keeper 2 120 0'
send 'summon extremedimensions:lurker 4 120 0'
sleep 6
send 'give @a extremedimensions:seam_needle'
send 'stop'
sleep 15
sed -n '/Loading Minecraft/,$p' out.log | grep -v "^\s*$" | head -200
