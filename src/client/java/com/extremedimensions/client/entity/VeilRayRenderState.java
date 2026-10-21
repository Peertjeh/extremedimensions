package com.extremedimensions.client.entity;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state of a Veil Ray: how hard it is banking into its turn (-1..1), whether it is one of the Frayed, and how far
 * its wings are folded for a dive (0..1).
 */
public class VeilRayRenderState extends LivingEntityRenderState {
	public float bank;
	public boolean frayed;
	public float dive;
}
