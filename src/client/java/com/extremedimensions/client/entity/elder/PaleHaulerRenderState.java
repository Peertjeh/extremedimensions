package com.extremedimensions.client.entity.elder;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state of the Pale Hauler: how far it has gone into each of its poses (0..1, eased from what the server tells
 * of it, see {@link PaleHaulerRenderer}), the brightness of its light and what its colour tells.
 */
public class PaleHaulerRenderState extends LivingEntityRenderState {
	/** Closing in on its prey or on a block to haul: a little lower. */
	public float stalk;
	/** Hauling a block out of a bridge: crouched, both hands on it, tugging. */
	public float haul;
	/** Crouched to leap, glowing red. */
	public float crouch;
	/** In the air, leaping at its prey. */
	public float leap;
	/** Holding what it took up over its head. */
	public float carry;
	/** About to step away into nothing, with what it took. */
	public float flee;
	/** Brightness of its eyes, its cracks and the ends of its strips (0..1): it beats like the tribe's hearts. */
	public float glow = 1.0F;
	/** What the glow's colour tells (see {@link com.extremedimensions.lurker.LurkerBody#getLurkerMood}). */
	public int mood;
}
