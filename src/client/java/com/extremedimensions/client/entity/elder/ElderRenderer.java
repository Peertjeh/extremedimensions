package com.extremedimensions.client.entity.elder;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.elder.ElderBoss;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Renders an Elder at its colossal scale, with a second, full-bright pass for everything that burns: lanterns, eyes,
 * cracks, threads and halos. The light breathes slowly and flares while a blow is being made.
 */
public class ElderRenderer<T extends ElderBoss> extends MobRenderer<T, ElderRenderState, EntityModel<ElderRenderState>> {
	private final Identifier texture;
	private final float scale;
	/** Per entity: the last action seen and when (client ticks) it began. */
	private final Map<T, float[]> actions = new WeakHashMap<>();

	public ElderRenderer(EntityRendererProvider.Context context, EntityModel<ElderRenderState> model, EntityModel<ElderRenderState> glowModel,
			String name, float scale, float shadow) {
		super(context, model, shadow);
		this.texture = ExtremeDimensions.id("textures/entity/elder/" + name + ".png");
		this.scale = scale;
		Identifier glowTex = ExtremeDimensions.id("textures/entity/elder/" + name + "_glow.png");
		this.addLayer(new LivingEntityEmissiveLayer<>(this, state -> glowTex, (state, age) -> state.glow, glowModel,
				RenderTypes::entityTranslucentEmissive, false));
	}

	@Override
	public ElderRenderState createRenderState() {
		return new ElderRenderState();
	}

	@Override
	public void extractRenderState(T boss, ElderRenderState state, float partialTick) {
		super.extractRenderState(boss, state, partialTick);
		float now = boss.tickCount + partialTick;
		float[] a = actions.computeIfAbsent(boss, k -> new float[] {-1, now});
		int action = boss.getAction();
		if (action != (int) a[0]) {
			a[0] = action;
			a[1] = now;
		}
		state.action = action;
		// The action plays at the tempo the server runs it at
		state.actionTime = (now - a[1]) * Math.max(0.1F, boss.getTempo());
		float breathe = 0.82F + 0.18F * Mth.sin(now * 0.08F);
		float flare = action == ElderBoss.IDLE ? 0 : Math.min(1.0F, state.actionTime / 10.0F) * 0.25F;
		if (action == ElderBoss.ULTIMATE) {
			flare = 0.3F + 0.15F * Mth.sin(now * 0.6F);
		}
		if (action == ElderBoss.SPAWN) {
			// Still more light than matter as it forms
			flare = 0.4F;
		}
		boolean calm = com.extremedimensions.client.ui.Ui.calm();
		state.glow = Math.min(1.0F, calm ? 0.9F : breathe + flare);
		if (state.deathTime > 0) {
			// Dying, it burns at full light until it breaks apart, without the red of a wound
			state.glow = 1.0F;
			state.hasRedOverlay = false;
		}
	}

	/** Drawn whenever any of the model is in view, not only its body. */
	@Override
	protected AABB getBoundingBoxForCulling(T boss, float partialTick) {
		return boss.getBoundingBox().inflate(boss.modelReach());
	}

	/** It does not topple over when it dies (it breaks apart where it stands). */
	@Override
	protected float getFlipDegrees() {
		return 0.0F;
	}

	@Override
	public Identifier getTextureLocation(ElderRenderState state) {
		return this.texture;
	}

	@Override
	protected void scale(ElderRenderState state, PoseStack pose) {
		float s = this.scale;
		boolean dying = state.deathTime > 0;
		if (state.action == ElderBoss.ULTIMATE || dying) {
			// It swells as its ultimate builds (and as it dies, which plays its ultimate once more), then snaps back
			float t = dying ? state.deathTime * (200F / ElderBoss.DEATH_TICKS) : state.actionTime;
			float grow = ElderAnim.smooth(t / 40F) * (1 - ElderAnim.smooth((t - 190F) / 10F));
			s *= 1.0F + 0.14F * grow;
		}
		pose.scale(s, s, s);
	}
}
