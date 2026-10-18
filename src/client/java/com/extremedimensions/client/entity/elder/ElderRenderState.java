package com.extremedimensions.client.entity.elder;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Render state of an Elder: the action under way and how long it has run, and the brightness of its light. */
public class ElderRenderState extends LivingEntityRenderState {
	public int action;
	/** Ticks since the action began (client time, so it runs smoothly between updates). */
	public float actionTime;
	/** Brightness of the lanterns, eyes and threads (0..1). */
	public float glow = 1.0F;
	/** The colour its light is turning for the blow (see {@link ElderTints}; negative for none), and how far (0..1). */
	public int tint = -1;
	public float tintMix;
}
