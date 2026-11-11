package com.extremedimensions.client.entity.elder;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.lurker.LurkerBody;
import com.extremedimensions.lurker.PaleHauler;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/**
 * Renders the Pale Hauler with a body of its own (after its concept sheet, see {@code tools/elders/pale_hauler.py}): an
 * Enderman's height, as its hitbox is, with a full-bright pass for its eyes, the cracks in its mantle and the ends of
 * its strips. Like the rest of the tribe its light beats like the Warden's heart and its colour tells what it came for:
 * its own violet, blue while it hauls a block out of a bridge, red as it crouches and leaps.
 *
 * <p>Its poses come from what the server already tells of it: the tribe's pose (closing in, hauling, stepping away), its
 * mood, whether it is on the ground, and whether a block rides on it (what it took, held up over its head). Each is
 * eased in and out, so it never snaps from one to the next.
 */
public class PaleHaulerRenderer extends MobRenderer<PaleHauler, PaleHaulerRenderState, PaleHaulerModel> {
	private static final Identifier TEXTURE = ExtremeDimensions.id("textures/entity/elder/pale_hauler.png");
	private static final Identifier GLOW = ExtremeDimensions.id("textures/entity/elder/pale_hauler_glow.png");
	private static final Identifier GLOW_THIEF = ExtremeDimensions.id("textures/entity/elder/pale_hauler_glow_thief.png");
	private static final Identifier GLOW_HUNTER = ExtremeDimensions.id("textures/entity/elder/pale_hauler_glow_hunter.png");

	/** The glow between beats (its eyes never go dark in the hood), and between a hunter's beats; how long a beat fades (ticks). */
	private static final float GLOW_REST = 0.45F;
	private static final float GLOW_HUNTER_REST = 0.75F;
	private static final float BEAT_FADE = 10.0F;
	/** Ticks between beats: calm, closing in, and hauling or crouched to leap. */
	private static final float BEAT_CALM = 40.0F;
	private static final float BEAT_HUNT = 20.0F;
	private static final float BEAT_TAKE = 12.0F;

	/** Per entity: its poses, eased so they blend instead of snapping, and its heartbeat. */
	private final Map<PaleHauler, Blend> blend = new WeakHashMap<>();

	private static final class Blend {
		float stalk;
		float haul;
		float crouch;
		float leap;
		float carry;
		float flee;
		long last;
		/** Ticks since the last heartbeat (starts anywhere, so a band of them never beats in step). */
		float sinceBeat = ThreadLocalRandom.current().nextFloat() * BEAT_CALM;
	}

	public PaleHaulerRenderer(EntityRendererProvider.Context context) {
		super(context, new PaleHaulerModel(context.bakeLayer(PaleHaulerModel.LAYER)), 0.5F);
		this.addLayer(new LivingEntityEmissiveLayer<>(this,
				state -> state.mood == LurkerBody.MOOD_HUNTER ? GLOW_HUNTER : state.mood == LurkerBody.MOOD_THIEF ? GLOW_THIEF : GLOW,
				(state, age) -> state.glow, new PaleHaulerModel(context.bakeLayer(PaleHaulerModel.LAYER)), RenderTypes::entityTranslucentEmissive,
				false));
	}

	@Override
	public PaleHaulerRenderState createRenderState() {
		return new PaleHaulerRenderState();
	}

	@Override
	public void extractRenderState(PaleHauler hauler, PaleHaulerRenderState state, float partialTick) {
		super.extractRenderState(hauler, state, partialTick);
		Blend b = blend.computeIfAbsent(hauler, key -> new Blend());
		long now = System.nanoTime();
		float dt = b.last == 0 ? 0.05F : Math.min(0.25F, (now - b.last) / 1.0e9F);
		b.last = now;
		float k = 1 - (float) Math.exp(-dt * 6.0);
		int pose = hauler.getLurkerPose();
		int mood = hauler.getLurkerMood();
		boolean hunter = mood == LurkerBody.MOOD_HUNTER;
		boolean air = !hauler.onGround();
		// Hauling, it goes down onto the block; the crouch before a leap is quick, the leap itself quicker still
		b.stalk += ((pose == Lurker.POSE_STALK && !hunter ? 1 : 0) - b.stalk) * k;
		b.haul += ((pose == Lurker.POSE_TAKE ? 1 : 0) - b.haul) * Math.min(1, k * 1.4F);
		b.crouch += ((hunter && !air ? 1 : 0) - b.crouch) * Math.min(1, k * 1.8F);
		b.leap += ((hunter && air ? 1 : 0) - b.leap) * Math.min(1, k * 2.5F);
		// What it took rides on it, held up over its head
		b.carry += ((hauler.isVehicle() && pose != Lurker.POSE_TAKE ? 1 : 0) - b.carry) * k;
		b.flee += ((pose == Lurker.POSE_RUN ? 1 : 0) - b.flee) * k;
		state.stalk = b.stalk;
		state.haul = b.haul;
		state.crouch = b.crouch;
		state.leap = b.leap;
		state.carry = b.carry;
		state.flee = b.flee;

		// The heartbeat, as the tribe's: faster as it closes in, fastest while it hauls or is about to leap
		state.mood = mood;
		float delay = BEAT_CALM + (BEAT_HUNT - BEAT_CALM) * Math.max(b.stalk, b.flee);
		delay += (BEAT_TAKE - delay) * Math.max(b.haul, hunter ? 1 : 0);
		b.sinceBeat += dt * 20.0F;
		if (b.sinceBeat >= delay) {
			b.sinceBeat %= delay;
		}
		float beat = Math.max(0.0F, 1.0F - b.sinceBeat / BEAT_FADE);
		// With reduced flashing the heart still beats, as a soft swell instead of a flash
		float depth = com.extremedimensions.client.ui.Ui.calm() ? 0.3F : 1.0F;
		float rest = hunter ? GLOW_HUNTER_REST : GLOW_REST;
		state.glow = rest + (1.0F - rest) * beat * depth;
	}

	/** Drawn whenever any of it is in view: its head, hands and strips reach a little past its body. */
	@Override
	protected AABB getBoundingBoxForCulling(PaleHauler hauler, float partialTick) {
		return hauler.getBoundingBox().inflate(0.75);
	}

	@Override
	public Identifier getTextureLocation(PaleHaulerRenderState state) {
		return TEXTURE;
	}
}
