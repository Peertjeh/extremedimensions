package com.extremedimensions.elder;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Last Weaver: one of those who wove themselves into the seal and did not finish. It floats, arms spread, threads
 * of light running from its fingers to hanging lanterns, a halo turning behind its head. Directed by
 * {@link WeaverService}.
 */
public class Weaver extends ElderBoss {
	public static final int THREAD = 1;
	public static final int NEEDLES = 2;
	public static final int UNRAVEL = 3;
	/** Seraphic Lance: the halo spins up and fires a beam that sweeps across the ground and carves it. */
	public static final int LANCE = 4;
	/** Cat's Cradle: threads strung across the ground between its prey, pulled taut until they cut. */
	public static final int CRADLE = 5;
	/** A Thousand Needles: rings of needles fall in spirals onto its prey, and a great needle last of all. */
	public static final int THOUSAND = 6;

	public Weaver(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return elderAttributes(480.0, 4.0);
	}

	@Override
	protected boolean driven() {
		return WeaverService.isDriven(this);
	}

	@Override
	protected void fadeOut() {
		WeaverService.fade(this);
	}

	@Override
	public double modelReach() {
		return 12.0;
	}

	@Override
	public float getVoicePitch() {
		return 1.1F + this.random.nextFloat() * 0.08F;
	}
}
