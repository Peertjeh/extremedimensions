package com.extremedimensions.lurker;

import com.extremedimensions.config.Config;
import com.extremedimensions.registry.ModEntities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The health of every kind of Lurker and of the Keeper, from their settings (3.31: {@code lurker_health},
 * {@code magpie_health}, {@code keeper_health} and so on). A creature takes it as it is made; one loaded from the world
 * keeps the health it was saved with (its saved health is read after this), so a changed setting holds for every one
 * that comes from then on.
 */
public final class TribeHealth {
	private TribeHealth() {}

	/** This kind's health from its setting, or -1 for a kind without one (the Starved One keeps its own). */
	public static double of(EntityType<?> type) {
		if (type == ModEntities.LURKER) {
			return Config.LURKER_HEALTH;
		}
		if (type == ModEntities.CAVE_LURKER) {
			return Config.CAVE_LURKER_HEALTH;
		}
		if (type == ModEntities.SCULK_LURKER) {
			return Config.SCULK_LURKER_HEALTH;
		}
		if (type == ModEntities.MAGPIE) {
			return Config.MAGPIE_HEALTH;
		}
		if (type == ModEntities.WICK) {
			return Config.WICK_HEALTH;
		}
		if (type == ModEntities.TIDEWALKER) {
			return Config.TIDEWALKER_HEALTH;
		}
		if (type == ModEntities.GATEBREAKER) {
			return Config.GATEBREAKER_HEALTH;
		}
		if (type == ModEntities.LOCKWARDEN) {
			return Config.LOCKWARDEN_HEALTH;
		}
		if (type == ModEntities.THREADBITER) {
			return Config.THREADBITER_HEALTH;
		}
		if (type == ModEntities.EDGECRAWLER) {
			return Config.EDGECRAWLER_HEALTH;
		}
		if (type == ModEntities.CRAGBACK) {
			return Config.CRAGBACK_HEALTH;
		}
		if (type == ModEntities.KEEPER) {
			return Config.KEEPER_HEALTH;
		}
		return -1;
	}

	/** Gives a creature just made its kind's health, at full (on the server; the client is told by it). */
	public static void apply(LivingEntity e) {
		if (e.level() == null || e.level().isClientSide()) {
			return;
		}
		double hp = of(e.getType());
		AttributeInstance max = e.getAttribute(Attributes.MAX_HEALTH);
		if (hp <= 0 || max == null) {
			return;
		}
		max.setBaseValue(Math.min(1024, hp));
		e.setHealth(e.getMaxHealth());
	}
}
