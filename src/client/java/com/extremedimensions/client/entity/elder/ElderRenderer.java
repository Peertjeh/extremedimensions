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
 * cracks, threads and halos. The light breathes slowly and flares while a blow is being made, and turns the colour of
 * that blow as it begins (or goes out), fading back after: see {@link ElderTints}.
 */
public class ElderRenderer<T extends ElderBoss> extends MobRenderer<T, ElderRenderState, EntityModel<ElderRenderState>> {
	private final Identifier texture;
	private final float scale;
	private final String name;
	/** Per entity: the last action begun (its count), when (client ticks) it began, the tell of the one before, the action. */
	private final Map<T, float[]> actions = new WeakHashMap<>();

	public ElderRenderer(EntityRendererProvider.Context context, EntityModel<ElderRenderState> model, EntityModel<ElderRenderState> glowModel,
			String name, float scale, float shadow) {
		super(context, model, shadow);
		this.texture = ExtremeDimensions.id("textures/entity/elder/" + name + ".png");
		this.scale = scale;
		this.name = name;
		Identifier glowTex = ExtremeDimensions.id("textures/entity/elder/" + name + "_glow.png");
		String[] names = ElderTints.names(name);
		Identifier[] tints = new Identifier[names.length];
		for (int i = 0; i < names.length; i++) {
			tints[i] = ExtremeDimensions.id("textures/entity/elder/" + name + "_glow_" + names[i] + ".png");
		}
		// Its own light, and over it the light of a blow fading in as its own fades out
		this.addLayer(new LivingEntityEmissiveLayer<>(this, state -> glowTex, (state, age) -> state.glow * (1 - state.tintMix), glowModel,
				RenderTypes::entityTranslucentEmissive, false));
		this.addLayer(new LivingEntityEmissiveLayer<>(this, state -> tints[Math.max(0, Math.min(tints.length - 1, state.tint))],
				(state, age) -> state.tint < 0 ? 0 : state.glow * state.tintMix, glowModel, RenderTypes::entityTranslucentEmissive, false));
	}

	@Override
	public ElderRenderState createRenderState() {
		return new ElderRenderState();
	}

	@Override
	public void extractRenderState(T boss, ElderRenderState state, float partialTick) {
		super.extractRenderState(boss, state, partialTick);
		float now = boss.tickCount + partialTick;
		int action = boss.getAction();
		float[] a = actions.computeIfAbsent(boss, k -> new float[] {-1, now, ElderTints.NONE, action});
		int take = boss.getTake();
		if (take != (int) a[0]) {
			a[2] = (int) a[0] < 0 ? ElderTints.NONE : ElderTints.of(name, (int) a[3]);
			a[0] = take;
			a[1] = now;
			a[3] = action;
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
		tell(state, now - a[1], ElderTints.of(name, action), (int) a[2], now);
		if (state.deathTime > 0) {
			// Dying, it burns at full light until it breaks apart, without the red of a wound
			state.glow = 1.0F;
			state.tintMix = 0;
			state.hasRedOverlay = false;
		}
	}

	/**
	 * The colour of a blow: it comes into its light over a few ticks as the blow begins, and fades back to its own as the
	 * blow ends; a dark blow puts its light out (flickering) and lets it come back after.
	 */
	private static void tell(ElderRenderState state, float since, int now, int before, float t) {
		float in = ElderAnim.smooth(since / 4F), out = 1 - ElderAnim.smooth(since / 8F);
		state.tint = ElderTints.NONE;
		state.tintMix = 0;
		if (now >= 0) {
			state.tint = now;
			// The same colour again (a blow struck twice) does not fade out and in between
			state.tintMix = before == now ? 1 : in;
		} else if (before >= 0 && out > 0) {
			state.tint = before;
			state.tintMix = out;
		}
		float dark = now == ElderTints.DARK ? in : before == ElderTints.DARK ? out : 0;
		if (dark > 0) {
			state.glow *= 1 - dark * (0.85F - 0.1F * Mth.sin(t * 1.7F));
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
