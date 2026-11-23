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
	/**
	 * Warp and Weft (3.43): its arms raised, it weaves a square grid of threads high over its prey, a line every four
	 * blocks both ways, and marks on the ground where each will fall; then its arms slam down and the grid with them.
	 * Whoever stands on a line is cut; the cells between are safe. (Weaving 0-24, the arms come down 44-52, the grid lands
	 * at 52.) The Last Weaver's own: the Knot Mother never weaves it.
	 */
	public static final int WARP = 10;
	/**
	 * Spindle Storm (3.43): it spins up, and three tornadoes of thread rise out of the floor round it and wander after the
	 * nearest players, drawing them in, cutting whoever is caught in their core and tearing up the ground they cross;
	 * then they burst. (They rise at 20, wander to 96 and burst at 100.) The Last Weaver's own.
	 */
	public static final int SPINDLE = 11;
	/**
	 * The Loom Comes Down (3.43): its right arm raised, it yanks four times, and each time a row of great needles falls
	 * from high over it towards one of those it can reach, each leaving a spike of calcite in the ground for a while.
	 * (The yanks at 24, 34, 44 and 54; the rows land at 36, 46, 56 and 66.) The Last Weaver's own.
	 */
	public static final int NEEDLEFALL = 12;

	public Weaver(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return elderAttributes(com.extremedimensions.config.Config.WEAVER_HEALTH, 4.0);
	}

	@Override
	protected double baseHealth() {
		return com.extremedimensions.config.Config.WEAVER_HEALTH;
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
