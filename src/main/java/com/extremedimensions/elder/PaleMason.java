package com.extremedimensions.elder;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Pale Mason (3.40): the foreman of the Endermen of the Pale Reach, the first of them torn loose when the wound
 * opened and the one who keeps them building. Eighteen blocks of dark Enderman under a hood and a ragged mantle of
 * pale end stone, chains of gold about him and a broken halo with golden spokes behind his head; a violet core burns in
 * his chest. He wants one thing for the anchor that will take them home: a Tether. Directed by
 * {@link PaleMasonService}.
 */
public class PaleMason extends ElderBoss {
	/** Teleport Strike: he is gone in a burst of violet, comes out of the air beside his prey and brings a claw down. */
	public static final int STRIKE = 1;
	/** Anchor Walls: both hands raised, then driven down; walls of end stone and purpur rise out of the floor. */
	public static final int WALLS = 2;
	/** Falling Blocks: arms lifted slowly, fingers spread; great blocks rise into the air over the arena. */
	public static final int LIFT = 3;
	/** ... and hurled at whoever stands under them, one arm sweeping forward. */
	public static final int THROW = 4;
	/** The Gaze: he goes still and his eyes blaze; whoever looks him in the face then is struck where they stand. */
	public static final int GAZE = 5;
	/** Bound Stones (3.41): great cubes of violet light hauled out of the air on gold chains and swung round him, then flung. */
	public static final int CHAINS = 6;
	/** Pillars of the Anchor (3.41): a fist driven into the floor, and pillars of violet light tear up out of it in three pulses. */
	public static final int PILLARS = 7;
	/** Golden Rings (3.41): his halo flares and he casts three rings of gold out across the floor. */
	public static final int RINGS = 8;
	/** The Unfinished Spiral (3.41): stones wind round him, drawing everyone in, until it bursts outward. */
	public static final int SPIRAL = 9;

	public PaleMason(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return elderAttributes(com.extremedimensions.config.Config.PALE_MASON_HEALTH, 8.0);
	}

	@Override
	protected double baseHealth() {
		return com.extremedimensions.config.Config.PALE_MASON_HEALTH;
	}

	@Override
	protected boolean driven() {
		return PaleMasonService.isDriven(this);
	}

	@Override
	protected void fadeOut() {
		PaleMasonService.fade(this);
	}

	@Override
	public double modelReach() {
		// his arms and the halo reach well past his body
		return 9.0;
	}

	@Override
	public float getVoicePitch() {
		return 0.7F + this.random.nextFloat() * 0.06F;
	}
}
