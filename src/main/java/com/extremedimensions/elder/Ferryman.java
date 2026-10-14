package com.extremedimensions.elder;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Ferryman: a hooded giant in a long black boat, the one the Nether's people pay. He sails over lava and stone
 * alike for travellers who stay too long beyond the wound. Directed by {@link FerrymanService}.
 */
public class Ferryman extends ElderBoss {
	public static final int SLAM = 1;
	public static final int CHAIN = 2;
	public static final int LANTERN = 3;
	public static final int KNELL = 4;

	public Ferryman(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return elderAttributes(520.0, 10.0);
	}

	@Override
	protected boolean driven() {
		return FerrymanService.isDriven(this);
	}

	@Override
	protected void fadeOut() {
		FerrymanService.fade(this);
	}

	@Override
	public double modelReach() {
		return 11.0;
	}

	@Override
	public float getVoicePitch() {
		return 0.6F + this.random.nextFloat() * 0.05F;
	}
}
