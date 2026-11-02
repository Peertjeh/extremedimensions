package com.extremedimensions.client.entity.elder;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.mending.StarvedOne;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Starved One moving. It hangs in the air and bobs, its tatters, sleeves and chains stirring and trailing behind
 * it as it flies (it leans into its flight); its halo of rusted spikes turns slowly behind its head, which follows its
 * prey. Falling on its prey it reaches both claws out ahead; raking, one arm swings back and slashes across; calling
 * its young it throws both arms up and wide and its halo spins; biting the thread it lunges with its head; dragging,
 * it reaches out and hauls back; stepping through the veil it folds in on itself and opens again. Bound by the pillars
 * its arms are pulled out and down, its back arches, its head hangs and it shudders, the robe hanging still.
 */
public class StarvedOneModel extends EntityModel<StarvedOneRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ExtremeDimensions.id("starved_one"), "main");

	private final ModelPart root, body, head, halo, armL, armR, foreL, foreR, handL, handR;
	private final ModelPart[] robe, sleeves, chains;

	public StarvedOneModel(ModelPart model) {
		super(model);
		this.root = StarvedOneGeometry.find(model, "root");
		this.body = StarvedOneGeometry.find(model, "body");
		this.head = StarvedOneGeometry.find(model, "head");
		this.halo = StarvedOneGeometry.find(model, "halo");
		this.armL = StarvedOneGeometry.find(model, "arm_l");
		this.armR = StarvedOneGeometry.find(model, "arm_r");
		this.foreL = StarvedOneGeometry.find(model, "fore_l");
		this.foreR = StarvedOneGeometry.find(model, "fore_r");
		this.handL = StarvedOneGeometry.find(model, "hand_l");
		this.handR = StarvedOneGeometry.find(model, "hand_r");
		this.robe = ElderAnim.parts(model, StarvedOneGeometry.ROBE, StarvedOneGeometry::find);
		this.sleeves = ElderAnim.parts(model, StarvedOneGeometry.SLEEVE, StarvedOneGeometry::find);
		this.chains = ElderAnim.parts(model, StarvedOneGeometry.CHAIN, StarvedOneGeometry::find);
	}

	@Override
	public void setupAnim(StarvedOneRenderState s) {
		super.setupAnim(s);
		ElderAnim.reset(body, halo);
		float t = s.ageInTicks;
		float bound = s.bound, free = 1 - bound;
		float fly = s.flight * free;
		int action = bound > 0.5F ? StarvedOne.IDLE : s.action;
		float at = s.actionTime;

		// Hanging in the air: a slow bob, a lean into its flight
		root.y += Mth.sin(t * 0.07F) * 1.6F * free;
		body.xRot += 0.28F * fly + Mth.sin(t * 0.045F) * 0.025F;
		head.yRot += Mth.clamp(s.yRot, -60, 60) * Mth.DEG_TO_RAD * 0.7F;
		head.xRot += Mth.clamp(s.xRot, -40, 40) * Mth.DEG_TO_RAD * 0.5F;
		halo.zRot += t * 0.015F;
		// Tatters stir, and trail behind it in flight (the front ones lift, the back ones stream)
		float stir = 0.05F * free + 0.01F;
		for (int i = 0; i < robe.length; i++) {
			ModelPart r = robe[i];
			float a = r.yRot;
			r.xRot += Mth.sin(t * 0.06F + i * 0.8F) * stir + 0.32F * fly * Mth.cos(a) + 0.12F * fly;
			r.zRot += Mth.cos(t * 0.05F + i * 1.2F) * 0.03F * free;
		}
		for (int i = 0; i < sleeves.length; i++) {
			sleeves[i].xRot += Mth.sin(t * 0.08F + i * 1.4F) * 0.08F * free + 0.25F * fly;
			sleeves[i].zRot += Mth.cos(t * 0.07F + i) * 0.05F * free;
		}
		for (int i = 0; i < chains.length; i++) {
			chains[i].zRot += Mth.sin(t * 0.09F + i * 2.1F) * 0.18F * free;
			chains[i].xRot += Mth.cos(t * 0.07F + i) * 0.12F * free + 0.4F * fly;
		}
		// Its long arms drift, reaching a little
		armL.zRot += Mth.sin(t * 0.05F) * 0.05F;
		armR.zRot -= Mth.sin(t * 0.05F + 1.3F) * 0.05F;
		armL.xRot += Mth.sin(t * 0.04F + 0.5F) * 0.06F - 0.2F * fly;
		armR.xRot += Mth.sin(t * 0.04F + 2.1F) * 0.06F - 0.2F * fly;
		handL.xRot += Mth.sin(t * 0.11F) * 0.08F;
		handR.xRot += Mth.sin(t * 0.11F + 1.7F) * 0.08F;

		switch (action) {
			case StarvedOne.SWOOP -> {
				// both claws out ahead as it falls
				float e = ElderAnim.env(at, 6, 40, 8);
				armL.xRot -= 1.35F * e;
				armR.xRot -= 1.35F * e;
				armL.zRot += 0.2F * e;
				armR.zRot -= 0.2F * e;
				foreL.xRot -= 0.25F * e;
				foreR.xRot -= 0.25F * e;
				body.xRot += 0.25F * e;
				head.xRot += 0.2F * e;
			}
			case StarvedOne.CLAW -> {
				// the right arm swings back and rakes across
				float wind = ElderAnim.smooth(at / 3F), cut = ElderAnim.smooth((at - 3) / 4F), back = ElderAnim.smooth((at - 9) / 12F);
				float k = 1 - back;
				armR.xRot += (-1.9F * wind + 1.2F * cut) * k;
				armR.yRot += (0.6F * wind - 1.1F * cut) * k;
				armR.zRot += (0.3F * wind - 0.8F * cut) * k;
				foreR.xRot -= 0.5F * wind * k;
				armL.xRot -= 0.6F * ElderAnim.env(at, 3, 4, 10);
				body.yRot += (0.25F * wind - 0.35F * cut) * k;
			}
			case StarvedOne.CALL -> {
				// arms flung up and wide, the halo whirling
				float e = ElderAnim.env(at, 10, 24, 14);
				armL.zRot -= 1.6F * e;
				armR.zRot += 1.6F * e;
				armL.xRot -= 0.3F * e;
				armR.xRot -= 0.3F * e;
				foreL.zRot -= 0.4F * e;
				foreR.zRot += 0.4F * e;
				head.xRot -= 0.45F * e;
				body.xRot -= 0.12F * e;
				halo.zRot += at * 0.18F * e;
				float swell = 1 + 0.12F * e;
				halo.xScale = swell;
				halo.yScale = swell;
			}
			case StarvedOne.BITE -> {
				// it lunges with its head
				float e = ElderAnim.env(at, 5, 6, 12);
				head.xRot += 0.6F * e;
				head.z -= 4F * e;
				body.xRot += 0.35F * e;
				armL.zRot -= 0.5F * e;
				armR.zRot += 0.5F * e;
			}
			case StarvedOne.DRAG -> {
				// it reaches out wide and hauls back to its chest
				float reach = ElderAnim.smooth(at / 6F), haul = ElderAnim.smooth((at - 8) / 6F), back = ElderAnim.smooth((at - 18) / 10F);
				float k = 1 - back;
				armL.xRot += (-1.5F * reach + 0.9F * haul) * k;
				armR.xRot += (-1.5F * reach + 0.9F * haul) * k;
				foreL.xRot += (-0.2F * reach - 0.9F * haul) * k;
				foreR.xRot += (-0.2F * reach - 0.9F * haul) * k;
				body.xRot += (0.2F * reach - 0.15F * haul) * k;
			}
			case StarvedOne.BLINK -> {
				// it folds in on itself and opens again, out of the veil
				float open = ElderAnim.smooth(at / 8F);
				float k = 0.55F + 0.45F * open;
				body.xScale = k;
				body.zScale = k;
				body.yScale = 0.8F + 0.2F * open;
			}
			default -> {
			}
		}

		if (bound > 0) {
			// Chained by the pillars: arms pulled out and down, back arched, head hanging, shuddering
			float shake = Mth.sin(t * 1.9F) * 0.035F * bound;
			armL.zRot -= 0.75F * bound;
			armR.zRot += 0.75F * bound;
			armL.xRot += 0.35F * bound + shake;
			armR.xRot += 0.35F * bound - shake;
			foreL.xRot += 0.3F * bound;
			foreR.xRot += 0.3F * bound;
			body.xRot -= 0.22F * bound + shake;
			head.xRot += 0.6F * bound;
			head.zRot += shake * 2;
			halo.zRot -= t * 0.012F * bound;
		}
	}
}
