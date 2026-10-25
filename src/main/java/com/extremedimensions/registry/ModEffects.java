package com.extremedimensions.registry;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.config.Config;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * The mod's own status effects, shown as Minecraft shows any effect (an icon on the screen and in the inventory): the
 * Matriarch's hex, the Ferryman's weight, and wings an Elder has bound, whose doing is kept by the Elders' spells (see
 * ElderSpells) so their strength follows the settings; and Unravelling, the bleeding an Unravelling blade leaves.
 */
public final class ModEffects {
	private ModEffects() {}

	/** The Matriarch's hex: everyone in her reach walks slower, and milk will not go down. */
	public static final Holder<MobEffect> HEX = register("matriarch_hex", 0x4F7A2C);
	/** The Ferryman's weight: everyone in his reach jumps lower. */
	public static final Holder<MobEffect> WEIGHT = register("styx_weight", 0x2E6F73);
	/** Wings an Elder has bound after pulling their wearer out of the air: no gliding and no rockets. */
	public static final Holder<MobEffect> BOUND_WINGS = register("bound_wings", 0x6A5A7A);
	/** Unravelling: what an Unravelling blade cut comes undone, and takes damage every second while it lasts. */
	public static final Holder<MobEffect> UNRAVELLING = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
			ExtremeDimensions.id("unravelling"), new Unravelling());

	private static Holder<MobEffect> register(String path, int colour) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ExtremeDimensions.id(path), new Effect(colour));
	}

	/** A harmful effect with nothing of its own (its doing is elsewhere). */
	private static final class Effect extends MobEffect {
		Effect(int colour) {
			super(MobEffectCategory.HARMFUL, colour);
		}
	}

	/** Unravelling: damage every second, more for each level of the effect. */
	private static final class Unravelling extends MobEffect {
		Unravelling() {
			super(MobEffectCategory.HARMFUL, 0xB8243A);
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
			return duration % 20 == 0;
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
			float damage = (float) (Math.max(0, Config.ENCH_UNRAVELLING_DAMAGE) * (amplifier + 1));
			if (damage > 0) {
				entity.hurtServer(level, entity.damageSources().magic(), damage);
			}
			return true;
		}
	}

	/** Forces class loading, and with it registration. */
	public static void init() {}
}
