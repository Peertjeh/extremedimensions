#!/bin/bash
set +e
JARS=$(find ~/.gradle -name "*.jar" 2>/dev/null | grep -E "fabric-client-gametest|fabric-gametest" | grep -v sources | sort -u)
echo "$JARS"
for j in $JARS; do
  for c in $(unzip -l "$j" | grep -oE "net/fabricmc/fabric/api/client/gametest/v1/[A-Za-z/$]*\.class" | sed 's/\.class$//' | tr / .); do
    echo "=== $c"; javap -cp "$j" "$c" 2>/dev/null | grep -v "^Compiled" | head -60
  done
done
find ~/.gradle -path "*fabric-loom*" -name "*.jar" | head -3
