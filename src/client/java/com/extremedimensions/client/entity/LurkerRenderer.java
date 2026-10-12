package com.extremedimensions.client.entity;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.lurker.LurkerBody;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/**
 * Renders the tribe with the Lurker's body: its own texture, a full-bright layer for the eyes and cracks, and a
 * scale. Lurkers, Hollowlings (small, with a big head) and Keepers (huge, bone-masked) all share it.
 */
public class LurkerRenderer<T extends Mob & LurkerBody> extends MobRenderer<T, LurkerRenderState, LurkerModel> {
	private final Identifier texture;
	private final float scale;
	private final boolean young;

	/** Per-entity smoothed pose weights, so poses blend instead of snapping. */
	private final Map<T, Blend> blend = new WeakHashMap<>();

	private static final class Blend {
		float stalk;
		float take;
		float drain;
		float run;
		long last;
	}

	public LurkerRenderer(EntityRendererProvider.Context context, String name, float scale, float shadow, boolean young) {
		super(context, new LurkerModel(context.bakeLayer(LurkerModel.LAYER)), shadow);
		this.texture = ExtremeDimensions.id("textures/entity/" + name + ".png");
		this.scale = scale;
		this.young = young;
		Identifier glowTex = ExtremeDimensions.id("textures/entity/" + name + "_glow.png");
		this.addLayer(new EyesLayer<>(this) {
			private final RenderType glow = RenderTypes.eyes(glowTex);

			@Override
			public RenderType renderType() {
				return glow;
			}
		});
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
