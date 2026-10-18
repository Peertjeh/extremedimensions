package com.extremedimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.penalty.PenaltyService;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Everywhere curses that change numbers vanilla computes: the damage a cursed player takes (Kindling, Frostbitten,
 * Volatile, Glass Jaw) and how long effects last on them (Fading Brews, Lingering Ills); and how hard an Elder's blows
 * hit (see ElderPower).
 */
@Mixin(LivingEntity.class)
public abstract class CursedPlayerMixin {
	/** The source of the hit being taken; read when its amount is first used, right after the early checks. */
	@Unique
	private static DamageSource extremedimensions$source;

	@Inject(method = "hurtServer", at = @At("HEAD"))
	private void extremedimensions$rememberSource(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		extremedimensions$source = source;
	}

	@ModifyVariable(method = "hurtServer", at = @At(value = "LOAD", ordinal = 0), argsOnly = true)
	private float extremedimensions$cursedDamage(float amount) {
		DamageSource source = extremedimensions$source;
		extremedimensions$source = null;
		if (source != null && (Object) this instanceof ServerPlayer player) {
			// an Elder's blows hit as hard as it fights (see ElderPower), then the curses add their share
			return CurseEffects.damageTaken(player, source, com.extremedimensions.elder.ElderPower.scale(source, amount));
		}
		return amount;
	}

	@ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), argsOnly = true)
	private MobEffectInstance extremedimensions$cursedEffect(MobEffectInstance effect) {
		return (Object) this instanceof ServerPlayer player ? CurseEffects.scaleEffect(player, effect) : effect;
	}
}
