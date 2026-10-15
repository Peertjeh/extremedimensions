package com.extremedimensions.client.entity.elder;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.elder.ElderBoss;
import com.extremedimensions.elder.Ferryman;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Ferryman moving: the boat rocks and rides the swell (harder the faster it sails), he turns in it to face his
 * passenger, his robe and the tatters of his sleeves stir, lanterns swing and the vortex before his raised palm turns.
 * He raises the pole high and brings it down, thrusts his chained hand out, lifts the vortex to loose his soul lanterns,
 * strikes the boat for the knell; for the crossing he couches the pole like a lance and leans into the charge, for the
 * gaol he closes his raised fist and crushes, for the tide he plants the pole. In his ultimate the pole goes up and
 * stays up, the vortex swells and spins until it roars, and at the end he brings everything down. Dying, he sinks into
 * the boat.
 * As he forms his boat condenses out of the smoke first, then he rises in it, his robe falling, his hood last, his
 * lanterns kindling as the last bell rings.
 */
public class FerrymanModel extends EntityModel<ElderRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ExtremeDimensions.id("ferryman"), "main");

	private final ModelPart root, boat, figure, torso, hood, armL, armR, foreL, foreR, vortex, vortexB, vortexC;
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
		this.foreR = FerrymanGeometry.find(model, "forearm_r");
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
		ElderAnim.reset(boat, figure, hood);
		ElderAnim.reset(robe);
		ElderAnim.reset(lanterns);
		float t = s.ageInTicks;
		// Dying, it plays its ultimate once more, ending in the bloom as it breaks apart
		boolean dying = s.deathTime > 0;
		int action = dying ? ElderBoss.ULTIMATE : s.action;
		float at = dying ? s.deathTime * (200F / ElderBoss.DEATH_TICKS) : s.actionTime;
		float sail = Mth.clamp(s.walkAnimationSpeed * 1.2F, 0, 1);
		root.y += Mth.sin(t * 0.06F) * 0.8F;
		boat.zRot += Mth.sin(t * 0.05F) * (0.035F + 0.03F * sail);
		boat.xRot += Mth.sin(t * 0.037F) * (0.02F + 0.02F * sail) - 0.03F * sail;
		figure.yRot += Mth.clamp(s.yRot, -100, 100) * Mth.DEG_TO_RAD;
		torso.xRot += Mth.sin(t * 0.04F) * 0.02F;
		hood.xRot += Mth.clamp(s.xRot, -30, 30) * Mth.DEG_TO_RAD * 0.4F;
		for (int i = 0; i < robe.length; i++) {
			robe[i].xRot += Mth.sin(t * 0.06F + i * 0.8F) * 0.03F + 0.08F * sail;
			robe[i].zRot += Mth.cos(t * 0.05F + i * 1.2F) * 0.02F;
		}
		for (int i = 0; i < mantle.length; i++) {
			mantle[i].xRot += Mth.sin(t * 0.07F + i * 1.1F) * 0.05F + 0.1F * sail;
		}
		for (int i = 0; i < strands.length; i++) {
			strands[i].zRot += Mth.sin(t * 0.08F + i * 1.6F) * 0.12F;
			strands[i].xRot += Mth.cos(t * 0.06F + i) * 0.08F + 0.15F * sail;
		}
		boolean ultimate = action == ElderBoss.ULTIMATE;
		float lift = action == Ferryman.LANTERN ? ElderAnim.env(at, 10, 14, 10) : 0;
		float storm = ultimate ? ElderAnim.env(at, 30, 150, 20) : 0;
		for (int i = 0; i < lanterns.length; i++) {
			lanterns[i].zRot += Mth.sin(t * 0.055F + i * 2.1F) * (0.1F + 0.2F * lift + 0.35F * storm);
			lanterns[i].xRot += Mth.cos(t * 0.045F + i * 1.3F) * (0.07F + 0.25F * storm) + 0.2F * sail;
		}
		float spin = 1 + 3 * lift + 7 * storm;
		vortex.zRot += t * 0.12F * spin;
		vortexB.yRot += t * 0.09F * spin;
		vortexC.xRot -= t * 0.15F * spin;
		// The vortex swells in his ultimate
		float swell = 1 + 1.3F * ElderAnim.env(at, 60, 120, 15) * (ultimate ? 1 : 0) + 0.25F * lift;
		vortex.xScale = swell;
		vortex.yScale = swell;
		vortex.zScale = swell;
		armL.zRot += Mth.sin(t * 0.04F) * 0.03F;
		armR.xRot += Mth.sin(t * 0.035F + 1F) * 0.02F;

		switch (action) {
			case Ferryman.SLAM -> {
				float wind = ElderAnim.smooth(at / 24F);
				float down = ElderAnim.smooth((at - 24) / 6F);
				float back = ElderAnim.smooth((at - 30) / 12F);
				armR.xRot += (-1.3F * wind * (1 - down) + 0.5F * down) * (1 - back);
				armR.zRot += 0.2F * wind * (1 - back);
				torso.xRot += (-0.1F * wind + 0.18F * down) * (1 - back);
			}
			case Ferryman.CHAIN -> {
				// the raised arm straightens and thrusts forward
				float e = ElderAnim.env(at, 8, 6, 10);
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
			case Ferryman.CROSSING -> {
				// The pole couched like a lance, he leans into the charge
				float ready = ElderAnim.env(at, 16, 30, 14);
				float charge = ElderAnim.env(at - 20, 4, 22, 12);
				armR.xRot -= 0.9F * ready;
				foreR.xRot -= 0.5F * ready;
				torso.xRot -= 0.12F * ready;
				torso.xRot += 0.3F * charge;
				hood.xRot -= 0.2F * charge;
				boat.xRot += 0.06F * charge + Mth.sin(t * 0.9F) * 0.02F * charge;
				armL.zRot += 0.4F * charge;
			}
			case Ferryman.GAOL -> {
				// The raised fist closes, pulls, and crushes
				float raise = ElderAnim.env(at, 10, 26, 10);
				float crush = ElderAnim.env(at - 34, 3, 4, 8);
				armL.zRot -= 0.35F * raise;
				foreL.zRot -= 0.3F * raise;
				hood.xRot -= 0.15F * raise;
				if (at > 12 && at < 36) {
					armL.xRot += Mth.sin(at * 0.8F) * 0.12F;
				}
				armL.xRot += 0.6F * crush;
				foreL.zRot += 0.8F * crush;
				torso.xRot += 0.15F * crush;
			}
			case Ferryman.TIDE -> {
				// Pole up over his head, then planted in the boat
				float up = ElderAnim.smooth(at / 16F) * (1 - ElderAnim.smooth((at - 17) / 3F));
				float plant = ElderAnim.smooth((at - 17) / 3F) * (1 - ElderAnim.smooth((at - 32) / 12F));
				armR.xRot -= 1.6F * up;
				armR.zRot += 0.3F * up;
				torso.xRot -= 0.12F * up;
				armR.xRot += 0.5F * plant;
				torso.xRot += 0.22F * plant;
				hood.xRot += 0.15F * plant;
			}
			case ElderBoss.SPAWN -> condensing(at, t);
			case ElderBoss.ULTIMATE -> {
				// The pole raised high and held, the vortex lifted, and at the end everything comes down
				float hold = ElderAnim.env(at, 30, 150, 4);
				float strike = ElderAnim.env(at - 188, 3, 4, 8);
				armR.xRot -= 1.75F * hold;
				armR.zRot += 0.25F * hold;
				armL.zRot -= 0.45F * hold;
				hood.xRot -= 0.35F * hold;
				torso.xRot -= 0.15F * hold;
				torso.xRot += Mth.sin(t * 2.4F) * 0.015F * storm;
				armR.xRot += 2.2F * strike;
				torso.xRot += 0.4F * strike;
				hood.xRot += 0.3F * strike;
			}
			default -> {
			}
		}
		if (dying) {
			// At the very end it sinks as it breaks apart
			float sink = ElderAnim.smooth((s.deathTime - ElderBoss.DEATH_TICKS * 0.9F) / (ElderBoss.DEATH_TICKS * 0.1F));
			root.y += 8F * sink;
		}

	}

	/** Forming, on its 140-tick timeline: his boat condenses out of the smoke, then he rises in it, his hood last. */
	private void condensing(float at, float t) {
		float hull = ElderAnim.smooth((at - 25) / 45F);
		float wobble = (1 - hull) * Mth.sin(t * 0.9F) * 0.15F;
		boat.xScale *= hull * (1 + wobble);
		boat.yScale *= hull;
		boat.zScale *= hull * (1 - wobble);
		float rise = ElderAnim.smooth((at - 65) / 50F);
		figure.xScale *= 0.3F + 0.7F * rise;
		figure.yScale *= rise;
		figure.zScale *= 0.3F + 0.7F * rise;
		for (int i = 0; i < robe.length; i++) {
			robe[i].yScale *= ElderAnim.smooth((at - 75 - (i % 8) * 3) / 30F);
		}
		ElderAnim.scale(hood, ElderAnim.smooth((at - 105) / 20F));
		for (int i = 0; i < lanterns.length; i++) {
			ElderAnim.scale(lanterns[i], ElderAnim.smooth((at - 100 - i * 4) / 10F));
		}
		vortex.yRot += t * 0.5F * (1 - ElderAnim.smooth((at - 110) / 30F));
		// He lifts his free hand as the last bell rings
		armL.zRot -= 0.6F * ElderAnim.env(at - 100, 15, 15, 10);
	}
}
