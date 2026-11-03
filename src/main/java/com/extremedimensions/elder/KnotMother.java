package com.extremedimensions.elder;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Knot Mother: what is tied into the First Knot. Whether she is the First Weaver, what the Fray made of her, or the
 * Held, the journal does not say. Below the waist she is one great knot of thread tied to the floor; a Weaver's wasted
 * body with four arms grows out of it. She fights on the Last Weaver's lines (directed by {@link WeaverService}) with
 * two blows of her own, and she cannot stray far from where she woke: her cords hold her to it.
 */
public class KnotMother extends Weaver {
	/** Draw In: her threads catch everyone near and she hauls them in to the knot, biting whoever arrives. */
	public static final int DRAW = 7;
	/** Snarl: knots flung onto the floor about her prey; whoever stands in one is held fast and loses Seam time. */
	public static final int SNARL = 8;

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
