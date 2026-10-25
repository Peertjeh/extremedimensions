package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.penalty.AbyssalWorld;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Unmapped: a map in a cursed player's hands draws nothing of the world round them. */
@Mixin(MapItem.class)
public abstract class UnmappedMapMixin {
	@Inject(method = "update", at = @At("HEAD"), cancellable = true)
	private void extremedimensions$unmapped(Level level, Entity entity, MapItemSavedData data, CallbackInfo ci) {
		if (AbyssalWorld.blankMaps(entity)) {
			ci.cancel();
		}
	}
}
