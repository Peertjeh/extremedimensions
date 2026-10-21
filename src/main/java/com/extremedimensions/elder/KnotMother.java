package com.extremedimensions.elder;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Knot Mother: what is tied into the First Knot. Whether she is the First Weaver, what the Fray made of her, or the
 * Held, the journal does not say. She is shaped as a Weaver, frayed to the colours of the deep, and she fights on the
 * Last Weaver's lines (the same blows, directed by {@link WeaverService}); her arena is the Knot's own chamber.
 */
public class KnotMother extends Weaver {
	public KnotMother(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return elderAttributes(560.0, 4.0);
	}

	@Override
	public float getVoicePitch() {
		return 0.8F + this.random.nextFloat() * 0.08F;
	}
}
