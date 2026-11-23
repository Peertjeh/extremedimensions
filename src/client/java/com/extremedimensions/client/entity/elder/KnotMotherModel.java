package com.extremedimensions.client.entity.elder;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.elder.ElderBoss;
import com.extremedimensions.elder.KnotMother;
import com.extremedimensions.elder.Weaver;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Knot Mother moving (after her design document): she glides rather than walks, swaying slowly; her crown turns
 * slowly behind her head, the rings float and turn about her, the core at her breast pulses, her robes move in long
 * slow layers and her threads sway. Every blow begins in her hands: for the Threadline one hand rises with the thread
 * and draws a clear path across; for the Knot Snare both hands pull towards each other; for the cradle her fingers
 * weave. When an anchor of hers breaks she recoils, her crown falters and her mantle falls open on a core burning
 * bright: she is exposed. Her ultimate spreads her arms while the rings swell and whirl. She forms from the floor up,
 * threads drawing her together (robes, body, arms, hood, veil), the crown and the rings last; dying, the threads break,
 * the crown falls still and she sinks.
 */
public class KnotMotherModel extends EntityModel<ElderRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ExtremeDimensions.id("knot_mother"), "main");

	private final ModelPart root, body, head, halo, ringA, ringB, chest, bodyCore, torsoCore, headCore;
	private final ModelPart armL, armR, foreL, foreR, handL, handR;
	private final ModelPart[] robe, mantle, strands, veil;

	public KnotMotherModel(ModelPart model) {
		super(model);
		this.root = KnotMotherGeometry.find(model, "root");
		this.body = KnotMotherGeometry.find(model, "body");
		this.head = KnotMotherGeometry.find(model, "head");
		this.halo = KnotMotherGeometry.find(model, "halo");
		this.ringA = KnotMotherGeometry.find(model, "ring_a");
		this.ringB = KnotMotherGeometry.find(model, "ring_b");
		this.chest = KnotMotherGeometry.find(model, "chest");
		this.bodyCore = KnotMotherGeometry.find(model, "bodycore");
		this.torsoCore = KnotMotherGeometry.find(model, "torsocore");
		this.headCore = KnotMotherGeometry.find(model, "headcore");
		this.armL = KnotMotherGeometry.find(model, "arm_l");
		this.armR = KnotMotherGeometry.find(model, "arm_r");
		this.foreL = KnotMotherGeometry.find(model, "forearm_l");
		this.foreR = KnotMotherGeometry.find(model, "forearm_r");
		this.handL = KnotMotherGeometry.find(model, "hand_l");
		this.handR = KnotMotherGeometry.find(model, "hand_r");
		this.robe = ElderAnim.parts(model, KnotMotherGeometry.ROBE, KnotMotherGeometry::find);
		this.mantle = ElderAnim.parts(model, KnotMotherGeometry.MANTLE, KnotMotherGeometry::find);
		this.strands = ElderAnim.parts(model, KnotMotherGeometry.STRAND, KnotMotherGeometry::find);
		this.veil = ElderAnim.parts(model, KnotMotherGeometry.VEIL, KnotMotherGeometry::find);
	}

	@Override
	public void setupAnim(ElderRenderState s) {
		super.setupAnim(s);
		ElderAnim.reset(root, body, head, halo, ringA, ringB, chest, bodyCore, torsoCore, headCore);
		ElderAnim.reset(armL, armR, foreL, foreR, handL, handR);
		ElderAnim.reset(robe);
		ElderAnim.reset(mantle);
		ElderAnim.reset(strands);
		ElderAnim.reset(veil);
		float t = s.ageInTicks;
		boolean dying = s.deathTime > 0;
		int action = dying ? ElderBoss.ULTIMATE : s.action;
		float at = dying ? s.deathTime * (200F / ElderBoss.DEATH_TICKS) : s.actionTime;
		float drift = Mth.clamp(s.walkAnimationSpeed * 1.4F, 0, 1);
		float storm = action == ElderBoss.ULTIMATE ? ElderAnim.env(at, 40, 140, 6) : 0;
		float line = action == KnotMother.THREADLINE ? ElderAnim.env(at, 10, 16, 10) : 0;
		float bind = action == KnotMother.SNARE ? ElderAnim.env(at, 6, 14, 10) : 0;
		float exposed = action == KnotMother.EXPOSED ? ElderAnim.env(at, 3, 60, 12) : 0;

		// She glides, swaying slowly
		root.y += Mth.sin(t * 0.045F) * 1.6F;
		body.zRot += Mth.sin(t * 0.026F) * 0.016F;
		body.xRot += Mth.sin(t * 0.02F) * 0.012F + 0.05F * drift;
		head.yRot += Mth.clamp(s.yRot, -45, 45) * Mth.DEG_TO_RAD * 0.5F;
		head.xRot += Mth.clamp(s.xRot, -30, 30) * Mth.DEG_TO_RAD * 0.4F;
		// Her crown turns slowly (faltering when she is exposed); her rings float and turn
		halo.zRot += t * (0.005F + 0.06F * storm) + ElderAnim.tremor(t, 0.25F * exposed);
		ElderAnim.scale(halo, 1 + 0.5F * storm - 0.06F * exposed);
		ringA.yRot += t * (0.012F + 0.05F * line + 0.08F * bind + 0.2F * storm);
		ringA.y += Mth.sin(t * 0.05F) * 1.5F;
		ringB.zRot -= t * (0.008F + 0.15F * storm);
		ringB.y += Mth.sin(t * 0.04F + 1.3F) * 1.2F;
		float swell = 1 + 0.6F * storm + 0.08F * bind;
		ringA.xScale *= swell;
		ringA.zScale *= swell;
		ElderAnim.scale(ringB, 1 + 0.4F * storm);
		// The core at her breast pulses, and burns bright when she is exposed
		ElderAnim.scale(chest, 1 + 0.08F * Mth.sin(t * 0.12F) + 0.15F * line + 0.12F * bind + 0.7F * exposed + 0.3F * storm);
		for (int i = 0; i < robe.length; i++) {
			robe[i].xRot += Mth.sin(t * 0.035F + i * 0.7F) * 0.018F + 0.08F * drift + 0.1F * storm * Mth.sin(t * 0.3F + i);
			robe[i].zRot += Mth.cos(t * 0.03F + i * 1.1F) * 0.02F;
		}
		for (int i = 0; i < mantle.length; i++) {
			// Her mantle falls open when she is exposed
			mantle[i].xRot += Mth.sin(t * 0.05F + i * 1.3F) * 0.04F + 0.08F * drift + 0.5F * exposed;
		}
		for (int i = 0; i < strands.length; i++) {
			strands[i].zRot += Mth.sin(t * 0.06F + i * 1.5F) * (0.06F + 0.2F * storm + 0.15F * line);
			strands[i].xRot += Mth.cos(t * 0.05F + i) * 0.05F + 0.1F * drift;
		}
		for (int i = 0; i < veil.length; i++) {
			veil[i].xRot += Mth.sin(t * 0.055F + i * 0.7F) * 0.035F + 0.06F * drift + 0.15F * storm * Mth.sin(t * 0.4F + i);
			veil[i].zRot += Mth.cos(t * 0.045F + i * 1.3F) * 0.025F;
		}
		// Her hands at rest: the raised one keeps its threads up, the low one's fingers stir
		armR.zRot += Mth.sin(t * 0.03F) * 0.04F;
		foreR.xRot += Mth.sin(t * 0.04F + 0.7F) * 0.05F;
		armL.zRot -= Mth.sin(t * 0.03F + 0.6F) * 0.04F;
		handL.zRot += Mth.sin(t * 0.07F) * 0.08F;
		handR.xRot += Mth.sin(t * 0.05F + 1.1F) * 0.06F;

		switch (action) {
			case KnotMother.THREADLINE -> {
				// One hand rises with the thread, then draws a clear path across
				float raise = ElderAnim.env(at, 10, 8, 4);
				float draw = ElderAnim.env(at - 18, 3, 12, 8);
				armL.xRot -= 0.9F * raise;
				armL.zRot -= 0.3F * raise;
				foreL.xRot -= 0.4F * raise;
				armL.yRot += 1.1F * draw;
				armL.zRot += 0.4F * draw;
				head.yRot += 0.3F * draw;
				body.yRot += 0.12F * draw;
			}
			case KnotMother.SNARE -> {
				// Both hands pull towards each other: the pattern on the floor closes
				armL.zRot -= 0.2F * bind;
				armR.zRot += 0.5F * bind;
				armL.yRot -= 0.5F * bind;
				armR.yRot += 0.4F * bind;
				foreR.xRot += 0.6F * bind;
				handL.zRot -= 0.4F * bind;
				head.xRot += 0.2F * bind;
			}
			case KnotMother.EXPOSED -> {
				// She recoils, arms thrown back, the core open
				body.xRot -= 0.22F * exposed;
				head.xRot -= 0.3F * exposed;
				armL.zRot += 0.5F * exposed;
				armR.zRot -= 0.4F * exposed;
				armL.xRot += 0.4F * exposed;
				armR.xRot += 0.3F * exposed;
				body.zRot += ElderAnim.tremor(t, 0.03F * exposed);
				root.y += 4F * exposed;
			}
			case Weaver.THREAD -> {
				float e = ElderAnim.env(at, 10, 8, 12);
				armL.xRot -= 0.7F * e;
				armL.zRot -= 0.2F * e;
				head.yRot += 0.2F * e;
			}
			case Weaver.CRADLE -> {
				float weave = ElderAnim.env(at, 4, 8, 4);
				float pull = ElderAnim.env(at - 14, 6, 44, 8);
				float snap = ElderAnim.env(at - 62, 2, 3, 8);
				handL.zRot += Mth.sin(at * 1.3F) * 0.4F * weave;
				handR.zRot -= Mth.sin(at * 1.3F + 1F) * 0.4F * weave;
				armL.xRot += 0.4F * pull;
				armR.xRot += 0.3F * pull;
				body.xRot -= 0.06F * pull;
				armL.zRot += 0.4F * snap;
				armR.zRot -= 0.4F * snap;
			}
			case ElderBoss.ULTIMATE -> {
				float snap = ElderAnim.env(at - 188, 3, 4, 8);
				// Arms spread and up, head back; then both hands to her breast as everything is drawn in
				armL.zRot -= 0.6F * storm;
				armR.zRot += 0.3F * storm;
				armL.xRot -= 0.3F * storm;
				head.xRot -= 0.45F * storm;
				body.zRot += Mth.sin(t * 2.6F) * 0.012F * storm;
				armL.xRot -= 0.8F * snap;
				armR.xRot -= 0.8F * snap;
				body.xRot += 0.3F * snap;
			}
			default -> {
			}
		}
		// The weight of her blows: she gathers, then drops into the blow
		float[] ac = ElderAnim.accent(at, impacts(action), 10, 12);
		float g = ac[0], j = ac[1];
		root.y += -1.8F * g + 1.6F * j;
		body.xRot += -0.08F * g + 0.14F * j;
		body.zRot += ElderAnim.tremor(t, 0.015F * g);
		head.xRot += -0.08F * g + 0.1F * j;
		halo.zRot += 0.3F * j;
		if (action == ElderBoss.SPAWN) {
			drawn(at, t);
		}
		if (dying) {
			// The threads break one by one, the crown falls still and the rings drop away; at the very end she sinks
			float loose = ElderAnim.smooth((s.deathTime - ElderBoss.DEATH_TICKS * 0.5F) / (ElderBoss.DEATH_TICKS * 0.4F));
			for (int i = 0; i < strands.length; i++) {
				strands[i].yScale *= 1 - loose * ElderAnim.smooth((s.deathTime - ElderBoss.DEATH_TICKS * 0.5F - (i % 8) * 2) / 10F);
			}
			ringA.y += 30F * loose;
			ringB.y += 24F * loose;
			float sink = ElderAnim.smooth((s.deathTime - ElderBoss.DEATH_TICKS * 0.9F) / (ElderBoss.DEATH_TICKS * 0.1F));
			root.y += 8F * sink;
		}
	}

	/** Forming, on the same 140-tick timeline as the Weaver: threads draw her together from the floor up. */
	private void drawn(float at, float t) {
		float hem = ElderAnim.smooth((at - 10) / 40F);
		bodyCore.xScale *= 0.6F + 0.4F * hem;
		bodyCore.yScale *= hem;
		bodyCore.zScale *= 0.6F + 0.4F * hem;
		for (int i = 0; i < robe.length; i++) {
			robe[i].yScale *= ElderAnim.smooth((at - 14 - (i % 12) * 3) / 30F);
		}
		ElderAnim.scale(torsoCore, ElderAnim.smooth((at - 55) / 25F));
		for (ModelPart m : mantle) {
			m.yScale *= ElderAnim.smooth((at - 70) / 25F);
		}
		float arms = ElderAnim.smooth((at - 70) / 30F);
		ElderAnim.scale(armL, arms);
		ElderAnim.scale(armR, arms);
		armL.zRot -= 1.0F * (1 - arms);
		armR.zRot += 1.0F * (1 - arms);
		ElderAnim.scale(headCore, ElderAnim.smooth((at - 85) / 22F));
		for (ModelPart v : veil) {
			v.yScale *= ElderAnim.smooth((at - 100) / 20F);
		}
		// The crown and the rings last, spinning into place
		float crown = ElderAnim.smooth((at - 105) / 25F);
		ElderAnim.scale(halo, crown);
		halo.zRot += t * 0.3F * (1 - crown);
		float rings = ElderAnim.smooth((at - 112) / 22F);
		ElderAnim.scale(ringA, rings);
		ElderAnim.scale(ringB, rings);
		ringA.yRot += t * 0.4F * (1 - rings);
		// The last thread pulled tight: she swells a little and settles
		ElderAnim.scale(root, 1 + 0.05F * ElderAnim.env(at - 124, 2, 2, 12));
	}

	/** When each of her blows lands (ticks at tempo 1, as the server strikes them). */
	private static float[] impacts(int action) {
		return switch (action) {
			case KnotMother.THREADLINE -> new float[] {20};
			case KnotMother.SNARE -> new float[] {12};
			case Weaver.THREAD -> new float[] {15};
			case Weaver.CRADLE -> new float[] {14, 62};
			default -> ElderAnim.NONE;
		};
	}
}
