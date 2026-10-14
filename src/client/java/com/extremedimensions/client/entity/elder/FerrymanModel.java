package com.extremedimensions.client.entity.elder;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.elder.Ferryman;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Ferryman moving: the boat rocks and rides the swell, he turns in it to face his passenger, his robe and the
 * tatters of his sleeves stir, lanterns swing and the vortex before his raised palm turns. He raises the pole high and brings it
 * down, thrusts his chained hand out, lifts the vortex to loose his soul lanterns, and strikes the boat for the knell.
 */
public class FerrymanModel extends EntityModel<ElderRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ExtremeDimensions.id("ferryman"), "main");

	private final ModelPart root, boat, figure, torso, hood, armL, armR, foreL, vortex, vortexB, vortexC;
	private final ModelPart[] robe, mantle, strands, lanterns;

	public FerrymanModel(ModelPart model) {
		super(model);
		this.root = FerrymanGeometry.find(model, "root");
		this.boat = FerrymanGeometry.find(model, "boat");
		this.figure = FerrymanGeometry.find(model, "figure");
		this.torso = FerrymanGeometry.find(model, "torso");
		this.hood = FerrymanGeometry.find(model, "hood");
		this.armL = FerrymanGeometry.find(model, "arm_l");
		this.armR = FerrymanGeometry.find(model, "arm_r");
		this.foreL = FerrymanGeometry.find(model, "forearm_l");
		this.vortex = FerrymanGeometry.find(model, "vortex");
		this.vortexB = FerrymanGeometry.find(model, "vortex_b");
		this.vortexC = FerrymanGeometry.find(model, "vortex_c");
		this.robe = ElderAnim.parts(model, FerrymanGeometry.ROBE, FerrymanGeometry::find);
		this.mantle = ElderAnim.parts(model, FerrymanGeometry.MANTLE, FerrymanGeometry::find);
		this.strands = ElderAnim.parts(model, FerrymanGeometry.STRAND, FerrymanGeometry::find);
		this.lanterns = ElderAnim.parts(model, FerrymanGeometry.LANTERN, FerrymanGeometry::find);
	}

	@Override
	public void setupAnim(ElderRenderState s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		float at = s.actionTime;
		root.y += Mth.sin(t * 0.06F) * 0.8F;
		boat.zRot += Mth.sin(t * 0.05F) * 0.035F;
		boat.xRot += Mth.sin(t * 0.037F) * 0.02F;
		figure.yRot += Mth.clamp(s.yRot, -100, 100) * Mth.DEG_TO_RAD;
		torso.xRot += Mth.sin(t * 0.04F) * 0.02F;
		hood.xRot += Mth.clamp(s.xRot, -30, 30) * Mth.DEG_TO_RAD * 0.4F;
		for (int i = 0; i < robe.length; i++) {
			robe[i].xRot += Mth.sin(t * 0.06F + i * 0.8F) * 0.03F;
			robe[i].zRot += Mth.cos(t * 0.05F + i * 1.2F) * 0.02F;
		}
		for (int i = 0; i < mantle.length; i++) {
			mantle[i].xRot += Mth.sin(t * 0.07F + i * 1.1F) * 0.05F;
		}
		for (int i = 0; i < strands.length; i++) {
			strands[i].zRot += Mth.sin(t * 0.08F + i * 1.6F) * 0.12F;
			strands[i].xRot += Mth.cos(t * 0.06F + i) * 0.08F;
		}
		float lift = s.action == Ferryman.LANTERN ? ElderAnim.env(at, 10, 14, 10) : 0;
		for (int i = 0; i < lanterns.length; i++) {
			lanterns[i].zRot += Mth.sin(t * 0.055F + i * 2.1F) * (0.1F + 0.2F * lift);
			lanterns[i].xRot += Mth.cos(t * 0.045F + i * 1.3F) * 0.07F;
		}
		float spin = 1 + 3 * lift;
		vortex.zRot += t * 0.12F * spin;
		vortexB.yRot += t * 0.09F * spin;
		vortexC.xRot -= t * 0.15F * spin;
		armL.zRot += Mth.sin(t * 0.04F) * 0.03F;
		armR.xRot += Mth.sin(t * 0.035F + 1F) * 0.02F;

		switch (s.action) {
			case Ferryman.SLAM -> {
				float wind = ElderAnim.smooth(at / 24F);
				float down = ElderAnim.smooth((at - 24) / 6F);
				float back = ElderAnim.smooth((at - 30) / 12F);
				armR.xRot += (-1.3F * wind * (1 - down) + 0.5F * down) * (1 - back);
				armR.zRot += 0.2F * wind * (1 - back);
				torso.xRot += (-0.1F * wind + 0.18F * down) * (1 - back);
			}
			case Ferryman.CHAIN -> {
				float e = ElderAnim.env(at, 8, 6, 10);
				// the raised arm straightens and thrusts forward
				armL.zRot += 0.3F * e;
				armL.xRot -= 0.9F * e;
				foreL.zRot += 1.5F * e;
			}
			case Ferryman.LANTERN -> {
				armL.zRot -= 0.35F * lift;
				foreL.zRot -= 0.2F * lift;
				hood.xRot -= 0.2F * lift;
			}
			case Ferryman.KNELL -> {
				float e = ElderAnim.env(at, 4, 6, 10);
				armR.xRot += 0.35F * e;
				torso.xRot += 0.08F * e;
			}
			default -> {
			}
		}
	}
}
