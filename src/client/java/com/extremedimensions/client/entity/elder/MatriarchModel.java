package com.extremedimensions.client.entity.elder;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.elder.ElderBoss;
import com.extremedimensions.elder.Matriarch;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Matriarch moving: she breathes, her head follows her prey, her robe of bark ripples, the moss on her branches
 * sways and her lanterns swing; walking, she rolls from side to side and her arms swing with her steps. She sweeps one
 * arm back and across, shakes her branches until the lanterns fall, drives both hands into the ground for the roots,
 * throws her head back to wail; rears back and lunges for her charge, raises both arms
 * and slams them into the ground for the cathedral of thorns, throws her branches wide and flings her lanterns. Her
 * ultimate is the whole of her: hands into the ground, rising and spreading, arching back as her chest opens, and the
 * bloom. Dying, she slumps and sinks, trembling. As she forms she is drawn up out of the earth clawing at the air, her
 * branches growing, her moss unfurling, her lanterns kindling one by one and her crown last.
 */
public class MatriarchModel extends EntityModel<ElderRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ExtremeDimensions.id("matriarch"), "main");

	private final ModelPart root, chest, head, crown, armL, armR, foreL, foreR, handL, handR;
	private final ModelPart[] robe, moss, lanterns, branches;

	public MatriarchModel(ModelPart model) {
		super(model);
		this.root = MatriarchGeometry.find(model, "root");
		this.chest = MatriarchGeometry.find(model, "chest");
		this.head = MatriarchGeometry.find(model, "head");
		this.crown = MatriarchGeometry.find(model, "crown");
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
		ElderAnim.reset(crown);
		ElderAnim.reset(branches);
		ElderAnim.reset(moss);
		ElderAnim.reset(lanterns);
		float t = s.ageInTicks;
		// Dying, it plays its ultimate once more, ending in the bloom as it breaks apart
		boolean dying = s.deathTime > 0;
		int action = dying ? ElderBoss.ULTIMATE : s.action;
		float at = dying ? s.deathTime * (200F / ElderBoss.DEATH_TICKS) : s.actionTime;
		float walk = Mth.clamp(s.walkAnimationSpeed * 1.6F, 0, 1);
		float stride = s.walkAnimationPos * 0.45F;
		chest.y += Mth.sin(t * 0.04F) * 0.6F;
		chest.xRot += Mth.sin(t * 0.04F) * 0.015F;
		// Walking: she rolls from foot to foot, her arms swing and her robe drags
		chest.zRot += Mth.sin(stride) * 0.06F * walk;
		chest.yRot += Mth.sin(stride) * 0.05F * walk;
		root.y -= Math.abs(Mth.sin(stride)) * 1.4F * walk;
		armL.xRot += Mth.sin(stride) * 0.22F * walk;
		armR.xRot -= Mth.sin(stride) * 0.22F * walk;
		head.yRot += Mth.clamp(s.yRot, -50, 50) * Mth.DEG_TO_RAD * 0.6F;
		head.xRot += Mth.clamp(s.xRot, -30, 30) * Mth.DEG_TO_RAD * 0.5F + Mth.sin(t * 0.03F) * 0.03F;
		for (int i = 0; i < robe.length; i++) {
			robe[i].xRot += Mth.sin(t * 0.05F + i * 0.9F) * 0.025F + walk * 0.12F * (0.6F + 0.4F * Mth.sin(stride * 2 + i));
			robe[i].zRot += Mth.cos(t * 0.043F + i * 1.3F) * 0.02F;
		}
		boolean ultimate = action == ElderBoss.ULTIMATE;
		float shake = action == Matriarch.LANTERNS ? ElderAnim.env(at, 6, 18, 8)
				: action == Matriarch.BARRAGE ? ElderAnim.env(at, 10, 36, 10)
				: ultimate ? ElderAnim.env(at, 30, 150, 20) : 0;
		float rush = action == Matriarch.RUSH ? ElderAnim.env(at - 18, 4, 22, 10) : 0;
		for (int i = 0; i < moss.length; i++) {
			moss[i].zRot += Mth.sin(t * 0.07F + i * 1.7F) * (0.09F + 0.2F * shake);
			moss[i].xRot += Mth.cos(t * 0.055F + i * 0.6F) * 0.07F + 0.55F * rush + 0.15F * walk;
		}
		for (int i = 0; i < lanterns.length; i++) {
			float spin = ultimate ? ElderAnim.env(at, 30, 120, 30) : 0;
			lanterns[i].zRot += Mth.sin(t * 0.05F + i * 2.3F) * 0.1F + Mth.sin(t * 1.1F + i) * 0.3F * shake + Mth.sin(t * 0.35F + i * 0.8F) * 0.6F * spin;
			lanterns[i].xRot += Mth.cos(t * 0.041F + i * 1.1F) * 0.07F + 0.6F * rush + Mth.cos(t * 0.35F + i * 0.8F) * 0.6F * spin;
		}
		float spread = action == Matriarch.BARRAGE ? ElderAnim.env(at, 16, 30, 12) : ultimate ? ElderAnim.env(at - 30, 60, 100, 10) : 0;
		for (int i = 0; i < branches.length; i++) {
			float side = (i % 2 == 0) ? 1 : -1;
			branches[i].zRot += Mth.sin(t * 0.025F + i) * 0.015F + Mth.sin(t * 1.3F + i) * 0.07F * shake - side * 0.25F * spread;
			branches[i].xRot += Mth.cos(t * 0.02F + i * 0.7F) * 0.01F + 0.35F * rush + 0.2F * spread;
		}
		armL.xRot += Mth.sin(t * 0.045F) * 0.04F;
		armR.xRot += Mth.sin(t * 0.045F + 1.3F) * 0.04F;
		handL.xRot += Mth.sin(t * 0.06F) * 0.06F;
		handR.xRot += Mth.sin(t * 0.06F + 0.8F) * 0.06F;

		switch (action) {
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
			case ElderBoss.SPAWN -> rising(at, t);
			case Matriarch.RUSH -> {
				// She rears back, arms flung behind her, then lunges with her talons out in front
				float rear = ElderAnim.env(at, 14, 4, 4);
				chest.xRot -= 0.35F * rear;
				head.xRot -= 0.3F * rear;
				armL.xRot += 0.7F * rear;
				armR.xRot += 0.7F * rear;
				armL.zRot -= 0.3F * rear;
				armR.zRot += 0.3F * rear;
				chest.xRot += 0.42F * rush;
				head.xRot -= 0.25F * rush;
				armL.xRot -= 1.3F * rush;
				armR.xRot -= 1.3F * rush;
				foreL.xRot -= 0.4F * rush;
				foreR.xRot -= 0.4F * rush;
				root.y += Mth.sin(t * 1.4F) * 1.2F * rush;
			}
			case Matriarch.CATHEDRAL -> {
				// Both arms up over her crown, then down into the ground
				float raise = ElderAnim.smooth(at / 16F) * (1 - ElderAnim.smooth((at - 18) / 3F));
				float slam = ElderAnim.smooth((at - 18) / 3F) * (1 - ElderAnim.smooth((at - 44) / 10F));
				armL.xRot -= 1.6F * raise;
				armR.xRot -= 1.6F * raise;
				armL.zRot += 0.4F * raise;
				armR.zRot -= 0.4F * raise;
				head.xRot -= 0.3F * raise;
				chest.xRot -= 0.1F * raise;
				armL.xRot += 1.1F * slam;
				armR.xRot += 1.1F * slam;
				foreL.xRot += 0.6F * slam;
				foreR.xRot += 0.6F * slam;
				chest.xRot += 0.38F * slam;
				head.xRot += 0.25F * slam;
				root.y += 1.5F * slam;
			}
			case Matriarch.BARRAGE -> {
				// Branches flung wide, arms throwing in turn
				armL.zRot -= 0.5F * spread;
				armR.zRot += 0.5F * spread;
				head.xRot -= 0.2F * spread;
				float phase = (at - 18) / 4F;
				if (at > 18 && at < 50) {
					float fling = Math.max(0, Mth.sin(phase * Mth.PI));
					if (((int) phase) % 2 == 0) {
						armR.xRot -= 1.0F * fling;
					} else {
						armL.xRot -= 1.0F * fling;
					}
					chest.yRot += 0.12F * Mth.sin(phase * Mth.PI);
				}
			}
			case ElderBoss.ULTIMATE -> requiem(s, at, t);
			default -> {
			}
		}
		if (dying) {
			// At the very end it sinks as it breaks apart
			float sink = ElderAnim.smooth((s.deathTime - ElderBoss.DEATH_TICKS * 0.9F) / (ElderBoss.DEATH_TICKS * 0.1F));
			root.y += 8F * sink;
		}

	}

	/** Forming, on its 140-tick timeline: drawn up out of the earth, clawing at the air, kindling her lanterns. */
	private void rising(float at, float t) {
		float reach = ElderAnim.env(at - 30, 30, 50, 25);
		armL.xRot -= 1.3F * reach;
		armR.xRot -= 1.3F * reach;
		armL.zRot -= 0.3F * reach;
		armR.zRot += 0.3F * reach;
		foreL.xRot -= 0.4F * reach;
		foreR.xRot -= 0.4F * reach;
		head.xRot -= 0.5F * reach;
		chest.xRot -= 0.15F * reach;
		float claw = Mth.sin(at * 0.5F) * 0.15F * reach;
		handL.xRot += claw;
		handR.xRot -= claw;
		for (int i = 0; i < branches.length; i++) {
			ElderAnim.scale(branches[i], 0.2F + 0.8F * ElderAnim.smooth((at - 30 - i * 2) / 60F));
		}
		for (int i = 0; i < moss.length; i++) {
			moss[i].yScale *= ElderAnim.smooth((at - 50 - (i % 10) * 3) / 40F);
		}
		for (int i = 0; i < lanterns.length; i++) {
			ElderAnim.scale(lanterns[i], ElderAnim.smooth((at - 60 - i * 4) / 10F));
		}
		ElderAnim.scale(crown, ElderAnim.smooth((at - 95) / 25F));
		// The earth breaks open round her and she shakes it off
		chest.zRot += Mth.sin(t * 3.1F) * 0.05F * ElderAnim.env(at - 122, 2, 6, 10);
	}

	/** The Verdant Requiem on its 200-tick timeline. */
	private void requiem(ElderRenderState s, float at, float t) {
		float roots = ElderAnim.env(at, 24, 6, 20);
		float rise = ElderAnim.env(at - 30, 50, 70, 30);
		float open = ElderAnim.env(at - 150, 20, 20, 6);
		float bloom = ElderAnim.env(at - 190, 2, 4, 6);
		float tremble = Mth.sin(t * 2.7F) * 0.02F + Mth.sin(t * 4.1F) * 0.015F;
		// Hands into the ground
		armL.xRot += 1.1F * roots;
		armR.xRot += 1.1F * roots;
		foreL.xRot += 0.6F * roots;
		foreR.xRot += 0.6F * roots;
		chest.xRot += 0.32F * roots;
		head.xRot += 0.3F * roots;
		// Rising and spreading, her head thrown back
		armL.zRot -= 0.9F * rise;
		armR.zRot += 0.9F * rise;
		armL.xRot -= 0.5F * rise;
		armR.xRot -= 0.5F * rise;
		head.xRot -= 0.45F * rise;
		root.y -= 3F * rise;
		chest.xRot += tremble * (rise + open);
		// Her chest opens: she arches back, arms up and wide
		chest.xRot -= 0.32F * open;
		head.xRot -= 0.35F * open;
		armL.zRot -= 0.6F * open;
		armR.zRot += 0.6F * open;
		armL.xRot -= 0.4F * open;
		armR.xRot -= 0.4F * open;
		crown.xRot -= 0.2F * open;
		// The bloom: she snaps forward, everything thrown out
		chest.xRot += 0.55F * bloom;
		armL.xRot -= 0.6F * bloom;
		armR.xRot -= 0.6F * bloom;
		handL.xRot -= 0.5F * bloom;
		handR.xRot -= 0.5F * bloom;
	}
}
