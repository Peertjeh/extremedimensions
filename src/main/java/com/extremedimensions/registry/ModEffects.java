package com.extremedimensions.registry;

import com.extremedimensions.ExtremeDimensions;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The mod's own status effects, shown as Minecraft shows any effect (an icon on the screen and in the inventory): the
 * Matriarch's hex, the Ferryman's weight, and wings an Elder has bound. What they do is kept by the Elders' spells
 * (see ElderSpells), so their strength follows the settings.
 */
public final class ModEffects {
	private ModEffects() {}

	/** The Matriarch's hex: everyone in her reach walks slower, and milk will not go down. */
	public static final Holder<MobEffect> HEX = register("matriarch_hex", 0x4F7A2C);
	/** The Ferryman's weight: everyone in his reach jumps lower. */
	public static final Holder<MobEffect> WEIGHT = register("styx_weight", 0x2E6F73);
	/** Wings an Elder has bound after pulling their wearer out of the air: no gliding and no rockets. */
	public static final Holder<MobEffect> BOUND_WINGS = register("bound_wings", 0x6A5A7A);

	private static Holder<MobEffect> register(String path, int colour) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ExtremeDimensions.id(path), new Effect(colour));
	}

	/** A harmful effect with nothing of its own (its doing is elsewhere). */
	private static final class Effect extends MobEffect {
		Effect(int colour) {
			super(MobEffectCategory.HARMFUL, colour);
		}
	}

	/** Forces class loading, and with it registration. */
	public static void init() {}
}
