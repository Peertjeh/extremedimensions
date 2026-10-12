#!/bin/bash
set +e
for j in $(find ~/.gradle -name "*.jar" 2>/dev/null | grep -E "minecraft-(client|common|merged)" | grep -v sources | sort -u); do
  if unzip -l "$j" 2>/dev/null | grep -q "data/minecraft/dimension_type/the_end.json"; then
    echo "##### from $j"
    for f in dimension_type/the_end.json dimension_type/overworld.json worldgen/world_preset/flat.json worldgen/world_preset/normal.json worldgen/biome/the_void.json worldgen/biome/end_barrens.json; do
      echo "=== $f"; unzip -p "$j" "data/minecraft/$f" | head -c 3000; echo
    done
    unzip -l "$j" | grep -E "data/minecraft/(dimension_type|timeline|environment)" | head -30
    break
  fi
done
