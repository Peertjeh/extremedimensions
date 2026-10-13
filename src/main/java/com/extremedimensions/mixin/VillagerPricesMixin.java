package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.server.Dims;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;

/** Ill Repute: villagers ask more of a cursed player, a share of each price per stack. */
@Mixin(Villager.class)
public abstract class VillagerPricesMixin {
	@Inject(method = "updateSpecialPrices", at = @At("TAIL"))
	private void extremedimensions$illRepute(Player player, CallbackInfo ci) {
		if (!(player instanceof ServerPlayer sp) || !PenaltyService.owHere(Dims.of(sp))) {
			return;
		}
		int stacks = DataStore.get(sp.getUUID()).stacks(Penalty.ILL_REPUTE);
		if (stacks <= 0) {
			return;
		}
		double share = Penalty.ILL_REPUTE.frac() * stacks;
		for (MerchantOffer offer : ((Villager) (Object) this).getOffers()) {
			int more = (int) Math.ceil(offer.getBaseCostA().getCount() * share);
			if (more > 0) {
				offer.addToSpecialPriceDiff(more);
			}
		}
	}
}
