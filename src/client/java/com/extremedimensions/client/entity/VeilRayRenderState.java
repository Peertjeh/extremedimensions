package com.extremedimensions.client.entity;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Render state of a Veil Ray: how hard it is banking into its turn (-1..1). */
public class VeilRayRenderState extends LivingEntityRenderState {
	public float bank;
}
