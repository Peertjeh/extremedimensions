package com.extremedimensions.client.entity;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Render state of the Lurker: smoothed pose weights (0..1) on top of the usual living-entity state. */
public class LurkerRenderState extends LivingEntityRenderState {
	public float stalk;
	public float take;
	public float drain;
	public float run;
	/** A Hollowling: big head, stubby limbs. */
	public boolean young;
	/** Brightness of the eyes and cracks (0..1): it beats like the Warden's heart. */
	public float glow = 1.0F;
}
