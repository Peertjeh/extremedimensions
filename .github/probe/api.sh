#!/bin/bash
# TEMP: signatures for the phase 2 settings screen
set +e
JARS=$(find ~/.gradle -name "*.jar" 2>/dev/null | grep -E "minecraft-(client|common|merged)|fabric|joml" | grep -v sources | sort -u)
show() {
  local cls="$1"; local pat="$2"; local path="${cls//.//}.class"
  for j in $JARS; do
    if unzip -l "$j" 2>/dev/null | grep -q " $path$"; then
      echo "=== $cls  ($(basename $j))"
      javap -p -cp "$j" "$cls" 2>/dev/null | grep -E "$pat" | grep -v lambda | head -40
      return
    fi
  done
  echo "=== $cls NOT FOUND"
}
show net.minecraft.client.gui.GuiGraphicsExtractor "pose|Scissor|scissor|nextStratum"
show org.joml.Matrix3x2fStack "pushMatrix|popMatrix|scale\(float, float\)|translate\(float, float\)"
show net.minecraft.client.gui.components.EditBox "public net|setFilter|setValue|getValue|setResponder|setMaxLength|setTextColor|setBordered|setEditable|setHint"
show net.minecraft.client.gui.screens.Screen "mouseScrolled|keyPressed|addRenderableWidget|removeWidget|rebuildWidgets|clearWidgets|setFocused|tick\(\)"
show net.minecraft.client.gui.components.events.ContainerEventHandler "mouseScrolled|mouseClicked|keyPressed"
show net.minecraft.client.KeyMapping "public net|Category|consumeClick|isDown"
show 'net.minecraft.client.KeyMapping$Category' "public|static"
show net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper "public"
show net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper "public"
show net.minecraft.server.level.ServerPlayer "createCommandSourceStack|permissions|hasPermissions|getPermission"
show net.minecraft.world.entity.player.Player "permissions|hasPermissions|getPermissionLevel"
show net.minecraft.commands.Commands "hasPermission|LEVEL_GAMEMASTERS"
show net.minecraft.commands.CommandSourceStack "hasPermission|permissions"
show net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback "public"
show com.mojang.blaze3d.platform.InputConstants "KEY_O|KEY_K|UNKNOWN|Type"
