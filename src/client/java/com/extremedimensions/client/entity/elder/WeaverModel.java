package com.extremedimensions.client.entity.elder;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.elder.ElderBoss;
import com.extremedimensions.elder.Weaver;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Last Weaver moving: it floats and bobs, its long tatters ripple, its arms rise and fall a little with its
 * threads and lanterns, its halo turns, the needles circling its waist drift round and the spools at its hips spin.
 * It throws a thread forward with one arm, raises both to rain needles (the halo turning faster), draws both arms in
 * before flinging everything near away; for the lance its halo spins up and it points; for the cradle its hands weave
 * and then pull the threads taut; for the thousand needles it rises with its arms up and its needles whirl. In its
 * ultimate it rises with its arms spread wide while its halo and its needles swell and whirl, until everything snaps.
 * Dying, its halo falls and it sinks.
 */
public class WeaverModel extends EntityModel<ElderRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ExtremeDimensions.id("weaver"), "main");

	private final ModelPart root, body, head, halo, armL, armR, handL, handR, needles, spoolL, spoolR;
	private final ModelPart[] robe, mantle, strands, lanterns;

	public WeaverModel(ModelPart model) {
		super(model);
		this.root = WeaverGeometry.find(model, "root");
		this.body = WeaverGeometry.find(model, "body");
		this.head = WeaverGeometry.find(model, "head");
		this.halo = WeaverGeometry.find(model, "halo");
		this.armL = WeaverGeometry.find(model, "arm_l");
		this.armR = WeaverGeometry.find(model, "arm_r");
		this.handL = WeaverGeometry.find(model, "hand_l");
		this.handR = WeaverGeometry.find(model, "hand_r");
		this.needles = WeaverGeometry.find(model, "needles");
		this.spoolL = WeaverGeometry.find(model, "spool_l");
		this.spoolR = WeaverGeometry.find(model, "spool_r");
		this.robe = ElderAnim.parts(model, WeaverGeometry.ROBE, WeaverGeometry::find);
		this.mantle = ElderAnim.parts(model, WeaverGeometry.MANTLE, WeaverGeometry::find);
		this.strands = ElderAnim.parts(model, WeaverGeometry.STRAND, WeaverGeometry::find);
		this.lanterns = ElderAnim.parts(model, WeaverGeometry.LANTERN, WeaverGeometry::find);
	}

	@Override
	public void setupAnim(ElderRenderState s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		float at = s.actionTime;
		float drift = Mth.clamp(s.walkAnimationSpeed * 1.4F, 0, 1);
		boolean ultimate = s.action == ElderBoss.ULTIMATE;
		root.y += Mth.sin(t * 0.05F) * 2.2F;
		body.zRot += Mth.sin(t * 0.03F) * 0.012F;
		body.xRot += Mth.sin(t * 0.025F) * 0.01F + 0.05F * drift;
		head.yRot += Mth.clamp(s.yRot, -45, 45) * Mth.DEG_TO_RAD * 0.5F;
		head.xRot += Mth.clamp(s.xRot, -30, 30) * Mth.DEG_TO_RAD * 0.4F;
		float rain = s.action == Weaver.NEEDLES ? ElderAnim.env(at, 14, 12, 14) : 0;
		float charge = s.action == Weaver.LANCE ? ElderAnim.env(at, 20, 36, 8) : 0;
		float whirl = s.action == Weaver.THOUSAND ? ElderAnim.env(at, 14, 56, 14) : 0;
		float storm = ultimate ? ElderAnim.env(at, 40, 140, 6) : 0;
		halo.zRot += t * (0.006F + 0.04F * rain + 0.12F * charge + 0.06F * whirl + 0.14F * storm);
		float haloSize = 1 + 0.15F * charge + 0.9F * storm;
		halo.xScale = haloSize;
		halo.yScale = haloSize;
		halo.zScale = haloSize;
		needles.yRot += t * (0.02F + 0.25F * whirl + 0.3F * storm);
		float ring = 1 + 0.5F * whirl + 1.1F * storm;
		needles.xScale = ring;
		needles.zScale = ring;
		spoolL.yRot += t * (0.08F + 0.4F * (whirl + storm));
		spoolR.yRot -= t * (0.08F + 0.4F * (whirl + storm));
		for (int i = 0; i < robe.length; i++) {
			robe[i].xRot += Mth.sin(t * 0.04F + i * 0.8F) * 0.02F + 0.1F * drift + 0.12F * storm * Mth.sin(t * 0.3F + i);
			robe[i].zRot += Mth.cos(t * 0.035F + i * 1.1F) * 0.025F;
		}
		for (int i = 0; i < mantle.length; i++) {
			mantle[i].xRot += Mth.sin(t * 0.06F + i * 1.3F) * 0.05F + 0.1F * drift;
		}
		for (int i = 0; i < strands.length; i++) {
			strands[i].zRot += Mth.sin(t * 0.07F + i * 1.5F) * (0.12F + 0.2F * storm);
			strands[i].xRot += Mth.cos(t * 0.05F + i) * 0.08F + 0.15F * drift;
		}
		for (int i = 0; i < lanterns.length; i++) {
			lanterns[i].zRot += Mth.sin(t * 0.06F + i * 1.9F) * (0.08F + 0.3F * storm);
			lanterns[i].xRot += Mth.cos(t * 0.05F + i * 1.2F) * (0.06F + 0.2F * storm);
		}
		armL.zRot += Mth.sin(t * 0.035F) * 0.05F;
		armR.zRot -= Mth.sin(t * 0.035F + 0.6F) * 0.05F;
		handL.zRot += Mth.sin(t * 0.05F) * 0.06F;
		handR.zRot -= Mth.sin(t * 0.05F + 0.9F) * 0.06F;

		switch (s.action) {
			case Weaver.THREAD -> {
				float e = ElderAnim.env(at, 10, 8, 12);
				armR.xRot -= 0.9F * e;
				armR.zRot += 0.2F * e;
				head.yRot -= 0.2F * e;
			}
			case Weaver.NEEDLES -> {
				armL.zRot -= 0.6F * rain;
				armR.zRot += 0.6F * rain;
				head.xRot -= 0.3F * rain;
			}
			case Weaver.UNRAVEL -> {
				float in = ElderAnim.env(at, 6, 4, 4);
				float out = ElderAnim.env(at - 10, 3, 4, 8);
				armL.xRot -= 0.8F * in - 0.3F * out;
				armR.xRot -= 0.8F * in - 0.3F * out;
				body.xRot -= 0.1F * in;
			}
			case Weaver.LANCE -> {
				// The halo spins up behind its head, and it points along the beam
				head.xRot -= 0.25F * charge;
				armR.xRot -= 0.9F * charge;
				armR.zRot += 0.5F * charge;
				armL.zRot -= 0.3F * charge;
				body.xRot += Mth.sin(t * 2.2F) * 0.01F * charge;
			}
			case Weaver.CRADLE -> {
				// Its hands weave, then it pulls everything taut
				float weave = ElderAnim.env(at, 4, 8, 4);
				float pull = ElderAnim.env(at - 14, 6, 44, 8);
				float snap = ElderAnim.env(at - 62, 2, 3, 8);
				handL.zRot += Mth.sin(at * 1.3F) * 0.4F * weave;
				handR.zRot -= Mth.sin(at * 1.3F + 1F) * 0.4F * weave;
				armL.xRot += 0.5F * pull;
				armR.xRot += 0.5F * pull;
				armL.zRot += 0.25F * pull;
				armR.zRot -= 0.25F * pull;
				body.xRot -= 0.08F * pull;
				armL.zRot -= 0.5F * snap;
				armR.zRot += 0.5F * snap;
			}
			case Weaver.THOUSAND -> {
				// It rises with its arms up while its needles whirl
				root.y -= 6F * whirl;
				armL.zRot -= 0.7F * whirl;
				armR.zRot += 0.7F * whirl;
				head.xRot -= 0.35F * whirl;
			}
			case ElderBoss.ULTIMATE -> {
				float snap = ElderAnim.env(at - 188, 3, 4, 8);
				// Arms spread wide and up, head back, trembling
				armL.zRot -= 0.55F * storm;
				armR.zRot += 0.55F * storm;
				head.xRot -= 0.5F * storm;
				body.xRot -= 0.12F * storm;
				body.zRot += Mth.sin(t * 2.6F) * 0.012F * storm;
				// Everything snaps
				armL.xRot -= 1.0F * snap;
				armR.xRot -= 1.0F * snap;
				body.xRot += 0.35F * snap;
			}
			default -> {
			}
		}
		if (s.deathTime > 0) {
			float k = Math.min(1, s.deathTime / 70F);
			head.xRot += 0.6F * k;
			body.xRot += 0.3F * k + Mth.sin(t * 2.4F) * 0.03F * k;
			armL.zRot += 0.9F * k;
			armR.zRot -= 0.9F * k;
			halo.y += 40F * k * k;
			root.y += 14F * k * k;
		}
	}
}
