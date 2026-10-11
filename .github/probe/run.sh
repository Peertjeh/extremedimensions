#!/bin/bash
# TEMP: start een vanilla 26.3 server en test de summon-commando's van de tether
set -u
mkdir -p /tmp/srv && cd /tmp/srv
URL=$(curl -s https://piston-meta.mojang.com/mc/game/version_manifest_v2.json | python3 -c "import sys,json;d=json.load(sys.stdin);print([v['url'] for v in d['versions'] if v['id']=='26.3'][0])")
SJ=$(curl -s "$URL" | python3 -c "import sys,json;print(json.load(sys.stdin)['downloads']['server']['url'])")
curl -s -o server.jar "$SJ"
echo "eula=true" > eula.txt
printf 'online-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\n' > server.properties
mkfifo in
(java -Xmx2G -jar server.jar nogui < in > out.log 2>&1 &)
exec 3>in
for i in $(seq 1 120); do grep -q "Done (" out.log && break; sleep 2; done
send() { echo "$1" >&3; sleep 1; }
send 'forceload add 0 0'
send 'execute in minecraft:overworld run summon minecraft:block_display 0.5 -59.9 0.5 {Tags:["ed_tether","edt_abc","edt_abc_o","edt_abc_s0"],block_state:{Name:"minecraft:iron_chain",Properties:{axis:"y"}},brightness:{sky:15,block:15},transformation:{left_rotation:[0.00000f,0.00000f,-0.70711f,0.70711f],right_rotation:[0f,0f,0f,1f],translation:[-0.35355f,0.35355f,0.00000f],scale:[1.000f,1.00000f,1.000f]}}'
send 'execute in minecraft:overworld run summon minecraft:block_display 1.5 -59.9 0.5 {Tags:["ed_tether","edt_abc","edt_abc_b","edt_abc_s1"],block_state:{Name:"minecraft:amethyst_block"},brightness:{sky:15,block:15},transformation:{left_rotation:[0.00000f,0.00000f,-0.70711f,0.70711f],right_rotation:[0f,0f,0f,1f],translation:[-0.03182f,0.03182f,0.00000f],scale:[0.090f,1.00000f,0.090f]}}'
send 'summon minecraft:block_display 2 -59 0 {block_state:{Name:"minecraft:iron_chain"}}'
send 'execute if entity @e[type=minecraft:block_display]'
send 'execute if entity @e[type=minecraft:block_display,tag=edt_abc_s0]'
send 'data get entity @e[type=minecraft:block_display,limit=1,tag=edt_abc_s0]'
send 'kill @e[type=minecraft:block_display,tag=edt_abc_s1]'
send 'execute if entity @e[type=minecraft:block_display]'
send 'stop'
sleep 10
sed -n '/Done (/,$p' out.log
