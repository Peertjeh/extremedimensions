package com.extremedimensions.client.entity.elder;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Render state of the Starved One: what it is doing and for how long, how firmly it is bound, its light, its rise and its death. */
public class StarvedOneRenderState extends LivingEntityRenderState {
	public int action;
	/** Ticks since the action began (client time, so it runs smoothly between updates). */
	public float actionTime;
	/** How firmly the pillars' chains hold it (0 free .. 1 bound), eased in and out. */
	public float bound;
	/** How fast it flies (0..1), for the lean and the trailing robe. */
	public float flight;
	/** Brightness of its slit, its wound and its veins (0..1). */
	public float glow = 1.0F;
	/** How far it has risen out of the Heart (0 deep under it .. 1 free of it), eased between the server's ticks. */
	public float rising = 1.0F;
	/** How far into its death it is (0 alive .. 1 gone). */
	public float dying;
}
