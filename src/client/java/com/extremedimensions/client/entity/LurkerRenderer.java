package com.extremedimensions.client.entity;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.lurker.Lurker;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Renders the Lurker with its own model, texture and a full-bright layer for the eyes and the violet cracks. */
public class LurkerRenderer extends MobRenderer<Lurker, LurkerRenderState, LurkerModel> {
	private static final Identifier TEXTURE = ExtremeDimensions.id("textures/entity/lurker.png");
	private static final Identifier GLOW = ExtremeDimensions.id("textures/entity/lurker_glow.png");
	private static final float SCALE = 1.4F;

	/** Per-entity smoothed pose weights, so poses blend instead of snapping. */
	private final Map<Lurker, Blend> blend = new WeakHashMap<>();

	private static final class Blend {
		float stalk;
		float take;
		float drain;
		long last;
	}

	public LurkerRenderer(EntityRendererProvider.Context context) {
		super(context, new LurkerModel(context.bakeLayer(LurkerModel.LAYER)), 1.1F);
		this.addLayer(new EyesLayer<>(this) {
			private final RenderType glow = RenderTypes.eyes(GLOW);

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
	public void extractRenderState(Lurker lurker, LurkerRenderState state, float partialTick) {
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
		state.drain = b.drain;
	}

	@Override
	public Identifier getTextureLocation(LurkerRenderState state) {
		return TEXTURE;
	}

	@Override
	protected void scale(LurkerRenderState state, PoseStack pose) {
		pose.scale(SCALE, SCALE, SCALE);
	}
}
