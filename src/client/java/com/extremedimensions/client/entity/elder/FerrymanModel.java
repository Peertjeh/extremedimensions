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
 * gaol he closes his raised fist and crushes, for the tide he plants the pole. For the undertow he hoists the pole in both
 * hands, drives it down into the floor and leans on it stirring it round, then rips it up over his head; for the oars
 * he throws his arms wide, heaves and rows three great strokes; for the bells he holds the great lantern high and it
 * swings hard on its chain at every toll. In these the pole stays upright in his hand and the lanterns hang true however
 * he moves his arms. In his ultimate the pole goes up and stays up, the vortex swells and spins until it roars, and at
 * the end he brings everything down. Dying, he sinks into the boat.
 * As he forms his boat condenses out of the smoke first, then he rises in it, his robe falling, his hood last, his
 * lanterns kindling as the last bell rings.
 */
public class FerrymanModel extends EntityModel<ElderRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ExtremeDimensions.id("ferryman"), "main");

	private final ModelPart root, boat, figure, torso, hood, armL, armR, foreL, foreR, handR, pole, wrist, vortex, vortexB, vortexC;
	private final ModelPart[] robe, mantle, strands, lanterns, fine;
	/** The pole, the chain at his left wrist and the lanterns on the crossbar, kept hanging true in his newer blows. */
	private final ElderAnim.Hang poleHung, wristHung, crossL, crossR;

	// Key poses of his newer blows, as turns added to his rest: {right arm x, y, z, right forearm x, z, left arm x, y, z,
	// left forearm x, z, torso x, hood x, figure x}. (A straight arm is turned to where it points: the right forearm is
	// straightened by (1.1, 0.2) and the left by (0.3, 1.7); bent to grip, they were found by reaching for the pole.)
	private static final int POSE = 13;
	/** The Undertow: the pole hoisted high in both hands, the left below the right, his head back. */
	private static final float[] U_LIFT = {-1.74F, 0, -0.04F, 1.1F, 0.2F, -1.3F, -0.04F, -0.59F, -0.29F, 0.72F, -0.1F, -0.25F, -0.03F};
	/** Driven down, thrown forward over it, the left hand left higher on the pole. */
	private static final float[] U_DRIVE = {-0.68F, 0, 0.06F, 0.12F, -0.44F, -1.3F, -0.28F, -0.55F, -0.02F, 0.9F, 0.3F, 0.15F, 0.04F};
	/** Leaning on it, both hands on it at his breast, his head bowed over the whirl. */
	private static final float[] U_LEAN = {-0.56F, 0, 0.05F, -0.02F, -0.73F, -1.19F, -0.16F, -0.6F, -0.21F, 0.61F, 0.34F, 0.25F, 0.03F};
	/** Ripped up and back over his head, arching back, his left arm flung wide. */
	private static final float[] U_RIP = {-1.89F, 0, -0.18F, 0.34F, 0.13F, 0.05F, -0.04F, -0.67F, 0.22F, 1.14F, -0.25F, -0.35F, -0.04F};
	/** The Oars: both arms thrown out wide and low, straight. */
	private static final float[] O_WIDE = {0.7F, 0, 0.67F, 1.1F, 0.2F, 0.45F, 0, 0.38F, 0.3F, 1.7F, 0.05F, 0.05F, 0};
	/** Heaved up wide above his shoulders, as if the oars were lifted out of the ground. */
	private static final float[] O_LIFTED = {0.65F, 0, 2.0F, 1.1F, 0.2F, 0.4F, 0, -0.95F, 0.3F, 1.7F, -0.1F, -0.15F, -0.02F};
	/** Reaching forward, wide, leaning into the stroke. */
	private static final float[] O_CATCH = {-0.31F, 0, 0.88F, 1.1F, 0.2F, -0.56F, 0, 0.17F, 0.3F, 1.7F, 0.45F, 0.1F, 0.08F};
	/** Swept back, wide, thrown back. */
	private static final float[] O_FINISH = {1.42F, 0, 0.77F, 1.1F, 0.2F, 1.17F, 0, 0.28F, 0.3F, 1.7F, -0.3F, -0.15F, -0.06F};
	/** Held out at his sides, the oars upright. */
	private static final float[] O_UPRIGHT = {0.65F, 0, 1.3F, 1.1F, 0.2F, 0.4F, 0, -0.25F, 0.3F, 1.7F, 0, -0.05F, 0};
	/** The Bells: the great lantern raised high on its chain, his head turned up to it, the pole braced. */
	private static final float[] B_RAISED = {-0.15F, 0, 0.05F, 0, 0, -0.19F, 0, -1.5F, 0.3F, 1.6F, -0.1F, -0.3F, -0.03F};

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
		this.handR = FerrymanGeometry.find(model, "hand_r");
		this.pole = FerrymanGeometry.find(model, "pole");
		this.wrist = FerrymanGeometry.find(model, "wristchain");
		this.vortex = FerrymanGeometry.find(model, "vortex");
		this.vortexB = FerrymanGeometry.find(model, "vortex_b");
		this.vortexC = FerrymanGeometry.find(model, "vortex_c");
		this.robe = ElderAnim.parts(model, FerrymanGeometry.ROBE, FerrymanGeometry::find);
		this.mantle = ElderAnim.parts(model, FerrymanGeometry.MANTLE, FerrymanGeometry::find);
		this.strands = ElderAnim.parts(model, FerrymanGeometry.STRAND, FerrymanGeometry::find);
		this.lanterns = ElderAnim.parts(model, FerrymanGeometry.LANTERN, FerrymanGeometry::find);
		this.fine = ElderAnim.parts(model, FerrymanGeometry.FINE, FerrymanGeometry::find);
		this.poleHung = new ElderAnim.Hang(pole, 2, root, figure, torso, armR, foreR, handR);
		this.wristHung = new ElderAnim.Hang(wrist, 2, root, figure, torso, armL, foreL);
		// (the lanterns on the crossbar, which is set square on the pole)
		this.crossL = new ElderAnim.Hang(lanterns[2], 2, root, figure, torso, armR, foreR, handR, pole);
		this.crossR = new ElderAnim.Hang(lanterns[3], 2, root, figure, torso, armR, foreR, handR, pole);
	}

	@Override
	public void setupAnim(ElderRenderState s) {
		super.setupAnim(s);
		ElderAnim.reset(boat, figure, hood);
		ElderAnim.reset(robe);
		ElderAnim.reset(lanterns);
		// The prow's eyes are fine parts, built four times larger for finer pixels and shrunk to size here (his own skull
		// is at Minecraft's own size)
		for (ModelPart f : fine) {
			f.xScale = FerrymanGeometry.FINE_SCALE;
			f.yScale = FerrymanGeometry.FINE_SCALE;
			f.zScale = FerrymanGeometry.FINE_SCALE;
		}
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
		// For his newer blows: how far the pole, the chain at his wrist and the lanterns are kept hanging true however he
		// throws his arms about; how far the pole is tipped from upright {x, z} and slid down through his hand; how far the
		// wrist chain swings {forward, aside}; and how hard the lanterns swing
		float keep = 0, tipX = 0, tipZ = 0, slide = 0, swingX = 0, swingZ = 0, rock = 0;

		switch (action) {
			case Ferryman.SLAM -> {
				float wind = ElderAnim.smooth(at / 24F);
				float down = ElderAnim.snap((at - 24) / 5F);
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
				float plant = ElderAnim.snap((at - 17) / 3F) * (1 - ElderAnim.smooth((at - 32) / 12F));
				armR.xRot -= 1.6F * up;
				armR.zRot += 0.3F * up;
				torso.xRot -= 0.12F * up;
				armR.xRot += 0.5F * plant;
				torso.xRot += 0.22F * plant;
				hood.xRot += 0.15F * plant;
			}
			case Ferryman.UNDERTOW -> {
				// The Undertow: he crouches, then hoists the pole high in both hands, the left below the right, his head
				// back; drives it down through the boat into the floor (16), thrown over it, and leans on it with both
				// hands, twisting and stirring it in slow circles, his body going round with it and his robes swirling;
				// he bears down on it, then rips it up and back over his head as the geyser bursts (76), his left arm
				// flung wide, and comes back to rest
				keep = ElderAnim.env(at, 3, 90, 3);
				float dip = ElderAnim.env(at, 3, 0, 5);
				float rip = ElderAnim.snap((at - 72) / 4F);
				float k = 1 - ElderAnim.smooth((at - 79) / 17F);
				float[] p = new float[POSE];
				toward(p, U_LIFT, ElderAnim.smooth((at - 1) / 10F));
				toward(p, U_DRIVE, ElderAnim.fall((at - 12) / 4F));
				toward(p, U_LEAN, ElderAnim.smooth((at - 18) / 8F));
				toward(p, U_RIP, rip);
				pose(p, k);
				torso.xRot += 0.12F * dip;
				hood.xRot += 0.1F * dip;
				figure.y += 2F * dip;
				// Stirring: two slow circles, his body going round with the pole and twisting with it
				float stir = ElderAnim.env(at - 18, 8, 40, 8);
				float q = (at - 18) * (Mth.PI * 2 / 28F);
				float cq = Mth.cos(q) * stir, sq = Mth.sin(q) * stir;
				torso.zRot += 0.1F * cq;
				torso.xRot += 0.06F * sq;
				torso.yRot += 0.2F * sq;
				figure.zRot += 0.03F * cq;
				hood.yRot -= 0.15F * sq;
				// He bears down on it before he rips it up
				float bear = ElderAnim.env(at - 64, 7, 1, 2);
				torso.xRot += 0.1F * bear + ElderAnim.tremor(t, 0.02F * bear);
				figure.y += 2F * bear;
				// the pole stirred round, its foot down in the floor, then ripped back over his head
				float back = 1.1F * rip * k;
				tipX = 0.18F * cq - 0.06F * bear;
				tipZ = 0.18F * sq - back;
				slide = 36F * ElderAnim.fall((at - 12) / 4F) * (1 - ElderAnim.smooth((at - 72) / 4F));
				// the robes swirl round him the way the pole goes, and the lanterns swing out with it
				for (int i = 0; i < robe.length; i++) {
					float a = i * (Mth.PI * 2 / robe.length);
					robe[i].xRot += stir * (0.1F + 0.2F * (0.5F + 0.5F * Mth.sin(2 * a - 2 * q)));
					robe[i].zRot += 0.12F * stir * Mth.cos(a - q);
				}
				for (int i = 0; i < mantle.length; i++) {
					mantle[i].xRot += stir * 0.2F * (0.5F + 0.5F * Mth.sin(i * 1.1F - 2 * q));
				}
				rock = 0.3F * stir + 0.8F * ElderAnim.env(at - 72, 4, 0, 16);
			}
			case Ferryman.OARS -> {
				// Oars of the Drowned: he throws his arms out wide and low, then heaves them up, straining, as if lifting
				// heavy oars out of the ground (12-28); he reaches forward and rows three huge strokes, his arms sweeping
				// from before him to behind him as he leans forward and throws himself back (36, 52, 68), lifting them
				// forward again over the water between; then he holds them out at his sides and lowers them (84-100)
				keep = ElderAnim.env(at, 3, 94, 3);
				float[] p = new float[POSE];
				toward(p, O_WIDE, ElderAnim.snap((at - 2) / 8F));
				toward(p, O_LIFTED, ElderAnim.smooth((at - 13) / 15F));
				toward(p, O_CATCH, ElderAnim.smooth((at - 28) / 4F));
				float clear = 0, pull = 0;
				for (int k = 0; k < 3; k++) {
					float peak = 36 + 16 * k;
					toward(p, O_FINISH, ElderAnim.smooth((at - peak + 4) / 8F));
					pull += ElderAnim.env(at - peak + 4, 4, 0, 4);
					if (k < 2) {
						toward(p, O_CATCH, ElderAnim.smooth((at - peak - 4) / 8F));
						clear += Mth.sin(Mth.PI * ElderAnim.smooth((at - peak - 4) / 8F));
					}
				}
				toward(p, O_UPRIGHT, ElderAnim.smooth((at - 72) / 12F));
				pose(p, 1 - ElderAnim.smooth((at - 84) / 16F));
				// (out wide, a turn of zRot raises each arm: the oars lifted clear of the water as they swing forward)
				armR.zRot += 0.25F * clear;
				armL.zRot -= 0.25F * clear;
				float strain = ElderAnim.env(at - 12, 8, 8, 4);
				armR.zRot += ElderAnim.tremor(t, 0.04F * strain);
				armL.zRot += ElderAnim.tremor(t + 1.3F, 0.04F * strain);
				torso.zRot += ElderAnim.tremor(t + 2.1F, 0.02F * strain);
				figure.y += 2F * strain;
				// the pole held upright out at his side, swinging after the strokes; his robe and the lanterns thrown
				tipZ = -0.25F * pull;
				for (int i = 0; i < robe.length; i++) {
					robe[i].xRot += 0.18F * pull * (0.6F + 0.4F * Mth.sin(t * 0.5F + i * 1.7F));
				}
				for (int i = 0; i < mantle.length; i++) {
					mantle[i].xRot += 0.3F * pull;
				}
				rock = 0.6F * pull + 0.3F * clear + 0.3F * strain;
				swingX = -0.3F * pull + 0.15F * clear;
			}
			case Ferryman.BELLS -> {
				// Bells of the Drowned: he lifts the great lantern high on its chain, his head turned up to it; six times
				// the bells toll (30-70), and with each his arm jerks down, the lantern swings hard on its chain, he
				// flinches and every chain on him swings; then he lowers it (80-100)
				keep = ElderAnim.env(at, 3, 94, 3);
				float k = 1 - ElderAnim.smooth((at - 80) / 20F);
				float[] p = new float[POSE];
				float raise = ElderAnim.smooth((at - 2) / 16F);
				toward(p, B_RAISED, raise);
				pose(p, k);
				// he draws himself up to his full height in the boat to hold it up
				figure.y -= 5F * raise * k;
				torso.zRot -= 0.08F * raise * k;
				float jerk = 0, wind = 0, swing = 0, sway = 0;
				for (int i = 0; i < 6; i++) {
					float toll = 30 + 8 * i;
					jerk += ElderAnim.env(at - toll, 1, 0, 6);
					wind = Math.max(wind, ElderAnim.gather(at, toll, 4));
					float u = at - toll;
					if (u > 0) {
						// each toll kicks it the other way, half a swing after the last: they build on each other
						float kick = (float) Math.exp(-u / 12F) * (i % 2 == 0 ? 1 : -1);
						swing += kick * Mth.sin(u * Mth.PI / 8);
						sway += kick * Mth.sin(u * Mth.PI / 8 - 1.2F);
					}
				}
				// (raised, a turn of zRot drops the arm out to his side and of xRot brings it forward)
				armL.zRot += 0.45F * jerk - 0.12F * wind;
				armL.xRot += -0.35F * jerk + 0.1F * wind;
				foreL.xRot -= 0.3F * jerk;
				torso.xRot += -0.14F * jerk + 0.04F * wind;
				torso.zRot -= 0.06F * jerk;
				hood.xRot += 0.3F * jerk;
				hood.zRot += 0.08F * jerk;
				figure.y += 2F * jerk;
				swingX = 0.55F * swing * k;
				swingZ = 0.2F * sway * k;
				rock = 0.6F * Math.abs(jerk) * k + 0.25F * Math.abs(swing) * k;
				for (int i = 0; i < mantle.length; i++) {
					mantle[i].xRot += 0.12F * Math.abs(jerk);
				}
				for (int i = 0; i < strands.length; i++) {
					strands[i].xRot += 0.25F * swing * k;
				}
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
		// The weight of his blows: he crouches in the boat and draws back as he gathers, then throws himself into the
		// blow; the boat dips its bow and rocks (the geyser of the Undertow throws him back instead, and the strokes of
		// the oars and the tolls of the bells lend him less of it)
		float[] ac = ElderAnim.accent(at, impacts(action), 10, 12);
		float power = action == Ferryman.OARS ? 0.5F : action == Ferryman.BELLS ? 0.4F : 1F;
		float g = ac[0] * power, j = ac[1] * power * (action == Ferryman.UNDERTOW && at > 60 ? -1 : 1);
		figure.y += 1.6F * g + 0.8F * j;
		torso.xRot += -0.16F * g + 0.24F * j;
		torso.zRot += ElderAnim.tremor(t, 0.025F * g);
		hood.xRot += -0.1F * g + 0.15F * j;
		boat.xRot += 0.07F * j - 0.03F * g;
		boat.zRot += 0.04F * j * Mth.sin(t * 0.7F);
		root.y += 1.2F * j;
		root.z -= 1.8F * j;
		if (keep > 0) {
			held(keep, tipX, tipZ, slide, swingX, swingZ, rock, t);
		}
		if (dying) {
			// At the very end it sinks as it breaks apart
			float sink = ElderAnim.smooth((s.deathTime - ElderBoss.DEATH_TICKS * 0.9F) / (ElderBoss.DEATH_TICKS * 0.1F));
			root.y += 8F * sink;
		}

	}

	/** Moves {@code pose} the way to {@code to} by {@code k} (0 stays where it is, 1 gets there). */
	private static void toward(float[] pose, float[] to, float k) {
		for (int i = 0; i < POSE; i++) {
			pose[i] += (to[i] - pose[i]) * k;
		}
	}

	/** Adds a pose (see {@link #POSE}), by {@code k}. */
	private void pose(float[] p, float k) {
		armR.xRot += p[0] * k;
		armR.yRot += p[1] * k;
		armR.zRot += p[2] * k;
		foreR.xRot += p[3] * k;
		foreR.zRot += p[4] * k;
		armL.xRot += p[5] * k;
		armL.yRot += p[6] * k;
		armL.zRot += p[7] * k;
		foreL.xRot += p[8] * k;
		foreL.zRot += p[9] * k;
		torso.xRot += p[10] * k;
		hood.xRot += p[11] * k;
		figure.xRot += p[12] * k;
	}

	/**
	 * For his newer blows (by {@code k}): the pole kept upright in his hand however he holds it, tipped by {@code tipX}
	 * (its foot out to his left) and {@code tipZ} (its foot back) and slid {@code slide} down through his grip; the chain
	 * at his left wrist hanging straight from it with its great lantern, swung {@code swingX} back and {@code swingZ} out;
	 * and the lanterns on the crossbar hanging true from it, all of them swinging as hard as {@code rock}.
	 */
	private void held(float k, float tipX, float tipZ, float slide, float swingX, float swingZ, float rock, float t) {
		float[] a = poleHung.point(k, Mth.sin(tipX), Mth.cos(tipX) * Mth.cos(tipZ), Mth.sin(tipZ));
		pole.x += k * slide * a[0];
		pole.y += k * slide * a[1];
		pole.z += k * slide * a[2];
		wristHung.point(k, Mth.sin(swingZ), Mth.cos(swingX) * Mth.cos(swingZ), Mth.sin(swingX));
		crossL.point(k, 0, 1, 0);
		crossR.point(k, 0, 1, 0);
		// the great lantern swings on past its chain; the rest swing on their own
		lanterns[4].xRot += k * 0.5F * swingX;
		lanterns[4].zRot += k * 0.5F * swingZ;
		for (int i = 0; i < lanterns.length; i++) {
			float w = i == 4 ? 0.4F : i >= 2 ? 1F : 0.4F;
			lanterns[i].xRot += k * rock * w * 0.35F * Mth.sin(t * 0.45F + i * 2.1F);
			lanterns[i].zRot += k * rock * w * 0.3F * Mth.cos(t * 0.4F + i * 1.7F);
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

	/** When each of his blows lands (ticks at tempo 1, as the server strikes them). */
	private static float[] impacts(int action) {
		return switch (action) {
			case Ferryman.SLAM -> new float[] {24};
			case Ferryman.CHAIN -> new float[] {10};
			case Ferryman.LANTERN -> new float[] {16};
			case Ferryman.KNELL -> new float[] {8};
			case Ferryman.CROSSING -> new float[] {20};
			case Ferryman.GAOL -> new float[] {12, 34};
			case Ferryman.TIDE -> new float[] {18, 28};
			// the pole driven down, and the geyser
			case Ferryman.UNDERTOW -> new float[] {16, 76};
			case Ferryman.OARS -> new float[] {36, 52, 68};
			case Ferryman.BELLS -> new float[] {30, 38, 46, 54, 62, 70};
			default -> ElderAnim.NONE;
		};
	}
}
