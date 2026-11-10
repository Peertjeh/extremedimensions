package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.relic.RelicReset;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * A relic made after the relics were reset carries the world's relic epoch (see RelicReset). The hook is the
 * constructor a stack is made with from nothing but an item and a count: {@code new ItemStack(item)} and an item's
 * default stack, which the mod, other mods and the game's own /give and loot tables build on (what a loot table gives is
 * stamped once more in RelicReset, should a version of the game make it another way). A stack read from a save or the
 * network, or copied or split off another, is made from its item and its components through another constructor, and
 * keeps whatever stamp it had. It runs for every stack the game makes, so in a world never reset it reads one number and
 * does nothing more.
 */
@Mixin(ItemStack.class)
public abstract class RelicStampMixin {
	@Inject(method = "<init>(Lnet/minecraft/world/level/ItemLike;I)V", at = @At("TAIL"))
	private void extremedimensions$stamp(ItemLike item, int count, CallbackInfo ci) {
		if (DataStore.relicEpoch() > 0) {
			RelicReset.made((ItemStack) (Object) this);
		}
	}
}
