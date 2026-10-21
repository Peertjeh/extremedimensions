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
	/** Crossing of the Dead: the boat backs off, then charges straight through its passenger, splitting the ground. */
	public static final int CROSSING = 5;
	/** Gaol of Chains: chains burst out of the ground in a cage round each passenger, then crush inwards. */
	public static final int GAOL = 6;
	/** Tide of the Styx: the pole planted, a wave of soul fire rolls out over the ground. */
	public static final int TIDE = 7;

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
