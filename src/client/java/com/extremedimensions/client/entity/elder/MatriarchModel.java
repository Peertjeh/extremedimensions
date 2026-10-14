package com.extremedimensions.client.entity.elder;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.elder.Matriarch;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Matriarch moving: she breathes, her head follows her prey, her robe of bark ripples, the moss on her branches
 * sways and her lanterns swing. She sweeps one arm back and across, shakes her branches until the lanterns fall,
 * drives both hands into the ground for the roots, throws her head back to wail, and claws her way up as she rises.
 */
public class MatriarchModel extends EntityModel<ElderRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ExtremeDimensions.id("matriarch"), "main");

	private final ModelPart chest, head, armL, armR, foreL, foreR, handL, handR;
	private final ModelPart[] robe, moss, lanterns, branches;

	public MatriarchModel(ModelPart model) {
		super(model);
		this.chest = MatriarchGeometry.find(model, "chest");
		this.head = MatriarchGeometry.find(model, "head");
		this.armL = MatriarchGeometry.find(model, "arm_l");
		this.armR = MatriarchGeometry.find(model, "arm_r");
		this.foreL = MatriarchGeometry.find(model, "forearm_l");
		this.foreR = MatriarchGeometry.find(model, "forearm_r");
		this.handL = MatriarchGeometry.find(model, "hand_l");
		this.handR = MatriarchGeometry.find(model, "hand_r");
		this.robe = ElderAnim.parts(model, MatriarchGeometry.ROBE, MatriarchGeometry::find);
		this.moss = ElderAnim.parts(model, MatriarchGeometry.MOSS, MatriarchGeometry::find);
		this.lanterns = ElderAnim.parts(model, MatriarchGeometry.LANTERN, MatriarchGeometry::find);
		this.branches = ElderAnim.parts(model, MatriarchGeometry.BRANCH, MatriarchGeometry::find);
	}

	@Override
	public void setupAnim(ElderRenderState s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		float at = s.actionTime;
		chest.y += Mth.sin(t * 0.04F) * 0.6F;
		chest.xRot += Mth.sin(t * 0.04F) * 0.015F;
		head.yRot += Mth.clamp(s.yRot, -50, 50) * Mth.DEG_TO_RAD * 0.6F;
		head.xRot += Mth.clamp(s.xRot, -30, 30) * Mth.DEG_TO_RAD * 0.5F + Mth.sin(t * 0.03F) * 0.03F;
		for (int i = 0; i < robe.length; i++) {
			robe[i].xRot += Mth.sin(t * 0.05F + i * 0.9F) * 0.025F;
			robe[i].zRot += Mth.cos(t * 0.043F + i * 1.3F) * 0.02F;
		}
		float shake = s.action == Matriarch.LANTERNS ? ElderAnim.env(at, 6, 18, 8) : 0;
		for (int i = 0; i < moss.length; i++) {
			moss[i].zRot += Mth.sin(t * 0.07F + i * 1.7F) * (0.09F + 0.2F * shake);
			moss[i].xRot += Mth.cos(t * 0.055F + i * 0.6F) * 0.07F;
		}
		for (int i = 0; i < lanterns.length; i++) {
			lanterns[i].zRot += Mth.sin(t * 0.05F + i * 2.3F) * 0.1F + Mth.sin(t * 1.1F + i) * 0.3F * shake;
			lanterns[i].xRot += Mth.cos(t * 0.041F + i * 1.1F) * 0.07F;
		}
		for (int i = 0; i < branches.length; i++) {
			branches[i].zRot += Mth.sin(t * 0.025F + i) * 0.015F + Mth.sin(t * 1.3F + i) * 0.07F * shake;
			branches[i].xRot += Mth.cos(t * 0.02F + i * 0.7F) * 0.01F;
		}
		armL.xRot += Mth.sin(t * 0.045F) * 0.04F;
		armR.xRot += Mth.sin(t * 0.045F + 1.3F) * 0.04F;
		handL.xRot += Mth.sin(t * 0.06F) * 0.06F;
		handR.xRot += Mth.sin(t * 0.06F + 0.8F) * 0.06F;

		switch (s.action) {
			case Matriarch.SWEEP -> {
				// Back and up, then across in front of her, then home
				float wind = ElderAnim.smooth(at / 18F);
				float cut = ElderAnim.smooth((at - 18) / 8F);
				float back = ElderAnim.smooth((at - 26) / 8F);
				float out = 0.7F * wind * (1 - cut) - 1.6F * cut * (1 - back);
				armR.zRot += out;
				armR.xRot -= 0.6F * wind * (1 - cut) + 0.25F * cut * (1 - back);
				foreR.xRot += 0.5F * wind * (1 - cut);
				chest.yRot += (0.15F * wind - 0.35F * cut) * (1 - back);
			}
			case Matriarch.LANTERNS -> {
				head.xRot -= 0.35F * shake;
				armL.xRot -= 0.4F * shake;
				armR.xRot -= 0.4F * shake;
			}
			case Matriarch.ROOTS -> {
				float e = ElderAnim.env(at, 16, 14, 12);
				armL.xRot += 0.9F * e;
				armR.xRot += 0.9F * e;
				foreL.xRot += 0.7F * e;
				foreR.xRot += 0.7F * e;
				chest.xRot += 0.28F * e;
				head.xRot += 0.2F * e;
			}
			case Matriarch.WAIL -> {
				float e = ElderAnim.env(at, 10, 20, 10);
				head.xRot -= 0.55F * e;
				chest.xRot -= 0.12F * e;
				armL.zRot -= 0.5F * e;
				armR.zRot += 0.5F * e;
				armL.xRot += 0.3F * e;
				armR.xRot += 0.3F * e;
			}
			case Matriarch.RISE -> {
				float k = 1 - ElderAnim.smooth(at / 60F);
				armL.xRot -= 1.1F * k;
				armR.xRot -= 1.1F * k;
				head.xRot -= 0.4F * k;
			}
			default -> {
			}
		}
	}
}
