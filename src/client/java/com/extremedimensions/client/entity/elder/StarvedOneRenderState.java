package com.extremedimensions.client.entity.elder;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Render state of the Starved One: what it is doing and for how long, how firmly it is bound, and its light. */
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
}
