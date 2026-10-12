package com.extremedimensions.client.entity;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.seam.VeilRay;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Renders a Veil Ray large, with its seams of light full-bright, banking with how fast it turns. */
public class VeilRayRenderer extends MobRenderer<VeilRay, VeilRayRenderState, VeilRayModel> {
	private static final Identifier TEXTURE = ExtremeDimensions.id("textures/entity/veil_ray.png");
	private static final Identifier GLOW = ExtremeDimensions.id("textures/entity/veil_ray_glow.png");
	private static final float SCALE = 2.4F;

	private final Map<VeilRay, float[]> turn = new WeakHashMap<>();

	public VeilRayRenderer(EntityRendererProvider.Context context) {
		super(context, new VeilRayModel(context.bakeLayer(VeilRayModel.LAYER)), 1.6F);
		this.addLayer(new EyesLayer<>(this) {
			private final RenderType glow = RenderTypes.eyes(GLOW);

			@Override
			public RenderType renderType() {
				return glow;
			}
		});
	}

	@Override
	public VeilRayRenderState createRenderState() {
		return new VeilRayRenderState();
	}

	@Override
	public void extractRenderState(VeilRay ray, VeilRayRenderState state, float partialTick) {
		super.extractRenderState(ray, state, partialTick);
		float[] t = turn.computeIfAbsent(ray, k -> new float[] {ray.getYRot(), 0});
		float delta = Mth.wrapDegrees(ray.getYRot() - t[0]);
		t[0] = ray.getYRot();
		t[1] += (Mth.clamp(delta * 0.6F, -1, 1) - t[1]) * 0.08F;
		state.bank = t[1];
	}

	@Override
	public Identifier getTextureLocation(VeilRayRenderState state) {
		return TEXTURE;
	}

	@Override
	protected void scale(VeilRayRenderState state, PoseStack pose) {
		pose.scale(SCALE, SCALE, SCALE);
	}
}
