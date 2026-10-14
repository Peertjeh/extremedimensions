#!/bin/bash
# TEMP: signature probe (javap only), printed straight into the build log.
set +e
JARS=$(find ~/.gradle/caches -name "*.jar" 2>/dev/null | grep -v -- "-sources" | grep -E "fabric-loom|net.fabricmc|com.mojang|minecraft" | sort -u)
CP=$(echo $JARS | tr ' ' ':')
short() { sed -E 's/java\.lang\.//g; s/java\.util\.function\.//g; s/java\.util\.//g; s/net\.minecraft\.//g; s/com\.mojang\.//g'; }
full() { for c in "$@"; do echo "=== $c"; javap -cp "$CP" -p "$c" 2>&1 | grep -v 'lambda\$' | grep -vE '^Compiled from' | short; done; }
full net.minecraft.world.scores.TeamColor
