package com.extremedimensions.client.entity;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.blaze3d.vertex.PoseStack;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.lurker.LurkerBody;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/**
 * Renders the tribe with the Lurker's body: its own texture, a full-bright layer for the eyes and cracks, and a
 * scale. Lurkers, Hollowlings (small, with a big head), Keepers (huge, bone-masked), the Starved One and the Thinned
 * all share it.
 *
 * <p>The glow beats like the Warden's heart: a flash that fades over half a second, then a dim ember until the next
 * beat. Calm, it beats every two seconds; stalking, running or drinking an anchor, twice as fast; reaching for you,
 * faster still. Each body keeps its own rhythm. Its colour tells what a Lurker came for: its own violet when calm, a
 * blue pulse when it comes to steal, and a red glow that hardly dims when it comes to hurt.
 */
public class LurkerRenderer<T extends Mob & LurkerBody> extends MobRenderer<T, LurkerRenderState, LurkerModel> {
	private final Identifier texture;
	private final float scale;
	private final boolean young;

	/** Per-entity smoothed pose weights, so poses blend instead of snapping. */
	private final Map<T, Blend> blend = new WeakHashMap<>();

	/** The glow between beats, and how long a beat takes to fade (ticks), as the Warden's heart. */
	private static final float GLOW_REST = 0.22F;
	/** The red glow of a Lurker come to hurt, between its beats. */
	private static final float GLOW_HUNTER_REST = 0.7F;
	private static final float BEAT_FADE = 10.0F;
	/** Ticks between beats: calm, hunting (stalk, run, drain) and reaching (take). */
	private static final float BEAT_CALM = 40.0F;
	private static final float BEAT_HUNT = 20.0F;
	private static final float BEAT_TAKE = 12.0F;

	private static final class Blend {
		float stalk;
		float take;
		float drain;
		float run;
		long last;
		/** Ticks since the last heartbeat (starts anywhere, so a pack never beats in step). */
		float sinceBeat = ThreadLocalRandom.current().nextFloat() * BEAT_CALM;
	}

	public LurkerRenderer(EntityRendererProvider.Context context, String name, float scale, float shadow, boolean young) {
		super(context, new LurkerModel(context.bakeLayer(LurkerModel.LAYER)), shadow);
		this.texture = ExtremeDimensions.id("textures/entity/" + name + ".png");
		this.scale = scale;
		this.young = young;
		Identifier glowTex = ExtremeDimensions.id("textures/entity/" + name + "_glow.png");
		Identifier thiefTex = ExtremeDimensions.id("textures/entity/" + name + "_glow_thief.png");
		Identifier hunterTex = ExtremeDimensions.id("textures/entity/" + name + "_glow_hunter.png");
		this.addLayer(new LivingEntityEmissiveLayer<>(this,
				state -> state.mood == LurkerBody.MOOD_HUNTER ? hunterTex : state.mood == LurkerBody.MOOD_THIEF ? thiefTex : glowTex,
				(state, age) -> state.glow, new LurkerModel(context.bakeLayer(LurkerModel.LAYER)), RenderTypes::entityTranslucentEmissive, false));
	}

	@Override
	public LurkerRenderState createRenderState() {
		return new LurkerRenderState();
	}

	@Override
	public void extractRenderState(T lurker, LurkerRenderState state, float partialTick) {
		super.extractRenderState(lurker, state, partialTick);
		int pose = lurker.getLurkerPose();
		Blend b = blend.computeIfAbsent(lurker, key -> new Blend());
		long now = System.nanoTime();
		float dt = b.last == 0 ? 0.05F : Math.min(0.25F, (now - b.last) / 1.0e9F);
		b.last = now;
		float k = 1 - (float) Math.exp(-dt * 6.0);
		b.stalk += ((pose == Lurker.POSE_STALK ? 1 : 0) - b.stalk) * k;
		b.take += ((pose == Lurker.POSE_TAKE ? 1 : 0) - b.take) * Math.min(1, k * 1.8F);
		b.drain += ((pose == Lurker.POSE_DRAIN ? 1 : 0) - b.drain) * k;
		state.stalk = b.stalk;
		state.take = b.take;
		b.run += ((pose == Lurker.POSE_RUN ? 1 : 0) - b.run) * Math.min(1, k * 1.4F);
		state.drain = b.drain;
		state.young = this.young;
		state.run = b.run;

		// The heartbeat: faster the hungrier the body is (and fastest when it comes to hurt)
		state.mood = lurker.getLurkerMood();
		float hunt = Math.max(b.stalk, Math.max(b.run, b.drain));
		float delay = BEAT_CALM + (BEAT_HUNT - BEAT_CALM) * hunt;
		delay += (BEAT_TAKE - delay) * b.take;
		if (state.mood == LurkerBody.MOOD_HUNTER) {
			delay = BEAT_TAKE;
		}
		b.sinceBeat += dt * 20.0F;
		if (b.sinceBeat >= delay) {
			b.sinceBeat %= delay;
		}
		float beat = Math.max(0.0F, 1.0F - b.sinceBeat / BEAT_FADE);
		// With reduced flashing the heart still beats, as a soft swell instead of a flash
		float depth = com.extremedimensions.client.ui.Ui.calm() ? 0.3F : 1.0F;
		// Come to hurt, the red hardly dims between beats; come to steal, the blue pulses from a dimmer rest
		float rest = state.mood == LurkerBody.MOOD_HUNTER ? GLOW_HUNTER_REST : GLOW_REST;
		state.glow = rest + (1.0F - rest) * beat * depth;
	}

	@Override
	public Identifier getTextureLocation(LurkerRenderState state) {
		return this.texture;
	}

	@Override
	protected void scale(LurkerRenderState state, PoseStack pose) {
		pose.scale(this.scale, this.scale, this.scale);
	}
}
