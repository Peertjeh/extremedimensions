package com.extremedimensions.client.entity;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Render state of the Lurker: smoothed pose weights (0..1) on top of the usual living-entity state. */
public class LurkerRenderState extends LivingEntityRenderState {
	public float stalk;
	public float take;
	public float drain;
}
