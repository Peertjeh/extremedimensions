package com.extremedimensions.elder;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Matriarch: a colossal, crowned mother of the hollows, rooted like a tree, hung with moss and lanterns. She comes
 * for those who have slain her children's keepers. Directed by {@link MatriarchService}.
 */
public class Matriarch extends ElderBoss {
	public static final int SWEEP = 1;
	public static final int LANTERNS = 2;
	public static final int ROOTS = 3;
	public static final int WAIL = 4;
	public static final int RISE = 5;
	/** Thornbound Charge: she rears back and hurls herself at her prey, ploughing through the ground. */
	public static final int RUSH = 6;
	/** Cathedral of Thorns: both hands into the ground, and rings of thorns burst out of it, one after another. */
	public static final int CATHEDRAL = 7;
	/** A Hundred Lanterns: her branches thrown wide, her lanterns hurled as burning bombs. */
	public static final int BARRAGE = 8;

	public Matriarch(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return elderAttributes(640.0, 8.0);
	}

	@Override
	protected boolean driven() {
		return MatriarchService.isDriven(this);
	}

	@Override
	protected void fadeOut() {
		MatriarchService.fade(this);
	}

	@Override
	public double modelReach() {
		return 9.0;
	}

	@Override
	public float getVoicePitch() {
		return 0.45F + this.random.nextFloat() * 0.06F;
	}
}
