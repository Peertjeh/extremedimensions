package com.extremedimensions.client.entity.elder;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.mending.StarvedOne;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Renders the Starved One at its own size (some seven blocks with its halo), with a full-bright pass for its slit, the
 * wound down its chest, its veins and claw tips. The light breathes slowly, flares as it falls on its prey or calls its
 * young, and flickers while the pillars hold it bound; it kindles as it rises out of the Heart. Dying, it does not
 * topple: its embers go out, and at the very end it shrinks into what light is left.
 */
public class StarvedOneRenderer extends MobRenderer<StarvedOne, StarvedOneRenderState, StarvedOneModel> {
	private static final Identifier TEXTURE = ExtremeDimensions.id("textures/entity/elder/starved_one.png");
	private static final Identifier GLOW = ExtremeDimensions.id("textures/entity/elder/starved_one_glow.png");

	/** Per entity: the last action begun (its count), when it began (client ticks), and the eased binding. */
	private final Map<StarvedOne, float[]> actions = new WeakHashMap<>();

	public StarvedOneRenderer(EntityRendererProvider.Context context) {
		super(context, new StarvedOneModel(context.bakeLayer(StarvedOneModel.LAYER)), 1.6F);
		this.addLayer(new LivingEntityEmissiveLayer<>(this, state -> GLOW, (state, age) -> state.glow,
				new StarvedOneModel(context.bakeLayer(StarvedOneModel.LAYER)), RenderTypes::entityTranslucentEmissive, false));
	}

	@Override
	public StarvedOneRenderState createRenderState() {
		return new StarvedOneRenderState();
	}

	@Override
	public void extractRenderState(StarvedOne so, StarvedOneRenderState state, float partialTick) {
		super.extractRenderState(so, state, partialTick);
		float now = so.tickCount + partialTick;
		// {take, action clock, binding, last frame, risen}
		float[] a = actions.computeIfAbsent(so, k -> new float[] {-1, 0, 0, now, so.rising()});
		int take = so.getTake();
		if (take != (int) a[0]) {
			a[0] = take;
			a[1] = 0;
		}
		float dt = Mth.clamp(now - a[3], 0, 5);
		a[3] = now;
		// Its action clock holds still for an instant as a blow lands (an impact frame, see HitStop)
		a[1] += dt * com.extremedimensions.client.fx.HitStop.factor(so.getId());
		state.action = so.getAction();
		state.actionTime = a[1];
		// The binding eases in over a few ticks, and out again
		a[2] = Mth.clamp(a[2] + (so.isBound() ? 0.12F : -0.08F) * dt, 0, 1);
		state.bound = a[2];
		double vx = so.getX() - so.xo, vy = so.getY() - so.yo, vz = so.getZ() - so.zo;
		state.flight = (float) Mth.clamp(Math.sqrt(vx * vx + vy * vy + vz * vz) / 0.45, 0, 1);
		float breathe = 0.8F + 0.2F * Mth.sin(now * 0.08F);
		boolean flaring = state.action == StarvedOne.SWOOP || state.action == StarvedOne.CALL || state.action == StarvedOne.BITE;
		float flare = flaring ? Math.min(1, state.actionTime / 8F) * 0.25F : 0;
		boolean calm = com.extremedimensions.client.ui.Ui.calm();
		float glow = calm ? 0.9F : Math.min(1, breathe + flare);
		if (state.bound > 0 && !calm) {
			glow *= 1 - 0.45F * state.bound * (0.5F + 0.5F * Mth.sin(now * 1.3F));
		}
		// How far it has risen out of the Heart, eased towards what the server says between its ticks
		a[4] += (so.rising() - a[4]) * Mth.clamp(dt * 0.35F, 0, 1);
		state.rising = Mth.clamp(a[4], 0, 1);
		if (state.rising < 1) {
			// its embers kindle as it comes up, flickering
			glow *= 0.35F + 0.65F * state.rising * (calm ? 1 : 0.85F + 0.15F * Mth.sin(now * 0.9F));
		}
		state.dying = state.deathTime > 0 ? Mth.clamp(state.deathTime / StarvedOne.DEATH_TICKS, 0, 1) : 0;
		// Dying, its embers go out: guttering at first, then gone
		state.glow = state.dying > 0 ? Math.max(0.03F, (1 - ElderAnim.smooth(state.dying / 0.85F)) * (calm ? 1 : 0.75F + 0.25F * Mth.sin(now * 2.3F)))
				: glow;
	}

	/** Drawn whenever any of it is in view: its arms, halo and tatters reach well past its body. */
	@Override
	protected AABB getBoundingBoxForCulling(StarvedOne so, float partialTick) {
		return so.getBoundingBox().inflate(3.0);
	}

	/** It does not topple over when it dies. */
	@Override
	protected float getFlipDegrees() {
		return 0.0F;
	}

	@Override
	public Identifier getTextureLocation(StarvedOneRenderState state) {
		return TEXTURE;
	}

	@Override
	protected void scale(StarvedOneRenderState state, PoseStack pose) {
		// At the very end of its death it shrinks into what is left of its light
		float s = state.dying > 0.85F ? Math.max(0.05F, 1 - (state.dying - 0.85F) / 0.15F * 0.95F) : 1.0F;
		pose.scale(s, s, s);
	}
}
