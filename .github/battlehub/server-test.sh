#!/bin/bash
# Boots a real Fabric 26.3 server with the freshly built BattleHub jar, runs a few commands (and the self-test
# when SELFTEST is set), stops it and fails the job on errors.
set -u
MC=26.3
LOADER=0.19.5
FAPI=0.162.0+26.3
JAR=$(ls "$GITHUB_WORKSPACE"/battlehub/build/libs/*.jar | grep -v sources | head -1)
mkdir -p /tmp/srv/mods && cd /tmp/srv
INST=$(curl -s https://meta.fabricmc.net/v2/versions/installer | python3 -c "import sys,json;print(json.load(sys.stdin)[0]['version'])")
curl -s -o server.jar "https://meta.fabricmc.net/v2/versions/loader/$MC/$LOADER/$INST/server/jar"
curl -s -o mods/fabric-api.jar "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FAPI/+/%2B}/fabric-api-${FAPI/+/%2B}.jar"
cp "$JAR" mods/
echo "eula=true" > eula.txt
cat > server.properties <<'PROPS'
online-mode=false
spawn-protection=0
level-type=minecraft\:flat
generator-settings={"layers"\:[{"block"\:"minecraft\:bedrock","height"\:1},{"block"\:"minecraft\:dirt","height"\:2},{"block"\:"minecraft\:grass_block","height"\:1}],"biome"\:"minecraft\:plains"}
sync-chunk-writes=false
PROPS
mkfifo in
(java -Xmx3G -Dbattlehub.selftest=true -jar server.jar nogui < in > out.log 2>&1 &)
exec 3>in
for i in $(seq 1 240); do grep -q "Done (" out.log && break; grep -q "Exception in server tick loop\|Failed to start the minecraft server" out.log && break; sleep 1; done
send() { echo "$1" >&3; sleep "${2:-2}"; }
send 'battlehub reload'
send 'list'
if [ -n "${SELFTEST:-}" ]; then
  send 'battlehub selftest' 1
  for i in $(seq 1 ${SELFTEST_TIMEOUT:-600}); do grep -q "SELFTEST FINISHED" out.log && break; sleep 1; done
fi
send 'stop' 1
for i in $(seq 1 60); do grep -q "All dimensions are saved\|ThreadedAnvilChunkStorage: All dimensions" out.log && break; sleep 1; done
sleep 3
echo "===== server log (filtered) ====="
sed -n '/Loading Minecraft\|Starting minecraft server/,$p' out.log | grep -iE "battlehub|selftest|exception|error|warn|caused by|at net\.battlehub|mixin|Done \(" | grep -v "Ambiguous\|Couldn't load chunk\|SELFTEST PASS\|SELFTEST IMG" | head -400
echo "===== around each failure ====="
grep -n "SELFTEST FAIL" out.log | cut -d: -f1 | while read -r n; do sed -n "$((n > 12 ? n - 12 : 1)),$((n + 2))p" out.log | grep -v "SELFTEST IMG"; echo "-----"; done | head -400
echo "===== tail ====="
tail -25 out.log
echo "===== pictures (base64 JPEG, one part per line) ====="
grep "SELFTEST IMG" out.log | sed 's/^.*SELFTEST IMG /IMG /'
echo "===== files ====="
ls -la config/battlehub world/battlehub world/battlehub/players world/battlehub/restore 2>/dev/null
FAIL=0
grep -q "Done (" out.log || { echo "::error::server did not start"; FAIL=1; }
if grep -nE "\[[^]]*(ERROR|FATAL)\]" out.log | grep -iv "Couldn't load chunk"; then echo "::error::errors in server log"; FAIL=1; fi
grep -n "SELFTEST FAIL" out.log && FAIL=1
if [ -n "${SELFTEST:-}" ]; then
  grep -q "SELFTEST FINISHED" out.log || { echo "::error::selftest did not finish"; FAIL=1; }
  # The self-test leaves one duel running: stopping the server must end it and restore both players.
  grep -q "Server stopping: ending 1 running match" out.log || { echo "::error::running match not ended on stop"; FAIL=1; }
  if [ -n "$(ls -A world/battlehub/restore 2>/dev/null)" ]; then echo "::error::players not restored on stop"; ls -la world/battlehub/restore; FAIL=1; fi
fi
exit $FAIL
