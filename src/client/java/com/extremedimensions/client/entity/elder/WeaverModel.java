package com.extremedimensions.client.entity.elder;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.elder.Weaver;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Last Weaver moving: it floats and bobs, its long tatters ripple, its arms rise and fall a little with its
 * threads and lanterns, and its halo turns. It throws a thread forward with one arm, raises both to rain needles
 * (the halo turning faster), and draws both arms in before flinging everything near away.
 */
public class WeaverModel extends EntityModel<ElderRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ExtremeDimensions.id("weaver"), "main");

	private final ModelPart root, body, head, halo, armL, armR, handL, handR;
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
		root.y += Mth.sin(t * 0.05F) * 2.2F;
		body.zRot += Mth.sin(t * 0.03F) * 0.012F;
		body.xRot += Mth.sin(t * 0.025F) * 0.01F;
		head.yRot += Mth.clamp(s.yRot, -45, 45) * Mth.DEG_TO_RAD * 0.5F;
		head.xRot += Mth.clamp(s.xRot, -30, 30) * Mth.DEG_TO_RAD * 0.4F;
		float needles = s.action == Weaver.NEEDLES ? ElderAnim.env(at, 14, 12, 14) : 0;
		halo.zRot += t * (0.006F + 0.04F * needles);
		for (int i = 0; i < robe.length; i++) {
			robe[i].xRot += Mth.sin(t * 0.04F + i * 0.8F) * 0.02F;
			robe[i].zRot += Mth.cos(t * 0.035F + i * 1.1F) * 0.025F;
		}
		for (int i = 0; i < mantle.length; i++) {
			mantle[i].xRot += Mth.sin(t * 0.06F + i * 1.3F) * 0.05F;
		}
		for (int i = 0; i < strands.length; i++) {
			strands[i].zRot += Mth.sin(t * 0.07F + i * 1.5F) * 0.12F;
			strands[i].xRot += Mth.cos(t * 0.05F + i) * 0.08F;
		}
		for (int i = 0; i < lanterns.length; i++) {
			lanterns[i].zRot += Mth.sin(t * 0.06F + i * 1.9F) * 0.08F;
			lanterns[i].xRot += Mth.cos(t * 0.05F + i * 1.2F) * 0.06F;
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
				armL.zRot -= 0.6F * needles;
				armR.zRot += 0.6F * needles;
				head.xRot -= 0.3F * needles;
			}
			case Weaver.UNRAVEL -> {
				float in = ElderAnim.env(at, 6, 4, 4);
				float out = ElderAnim.env(at - 10, 3, 4, 8);
				armL.xRot -= 0.8F * in - 0.3F * out;
				armR.xRot -= 0.8F * in - 0.3F * out;
				body.xRot -= 0.1F * in;
			}
			default -> {
			}
		}
	}
}
