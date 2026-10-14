#!/bin/bash
# TEMP: which methods open a screen in this Minecraft version (javap only).
set +e
JARS=$(find ~/.gradle/caches .gradle -name "*.jar" 2>/dev/null | grep -v -- "-sources" | grep -E "minecraft|loom" | sort -u)
CP=$(echo $JARS | tr ' ' ':')
for c in net.minecraft.client.Minecraft net.minecraft.client.gui.Gui net.minecraft.client.gui.screens.Screen; do
  echo "=== $c"
  javap -cp "$CP" -p "$c" 2>&1 | grep -i -E "screen|class " | head -40
done
