package com.extremedimensions.client.entity;

import com.extremedimensions.ExtremeDimensions;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

/**
 * The Lurker: a hunched, cloaked giant on long knuckle-walking arms, with ragged cloak strips and strands that drip
 * from its mask. Everything moves procedurally: breathing, head tracking with a curious tilt, swaying strands and
 * cloth, a knuckle walk, the reach of a theft and the raised, trembling arms of a drain.
 *
 * <p>The geometry and the five textures that share it (Lurker, Hollowling, Keeper, Starved One, Thinned) are generated
 * together by {@code tools/lurker/build.py}; the mask, eyes, brows, the hood's fringe and the claws are fine parts.
 */
public class LurkerModel extends EntityModel<LurkerRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ExtremeDimensions.id("lurker"), "main");

	private static final int TENDRILS = 8;
	private static final int SKIRT = 9;
	private static final float HIPS_LEAN = 0.62F;
	private static final float NECK_BEND = 0.35F;

	private final ModelPart hips;
	private final ModelPart neck;
	private final ModelPart head;
	private final ModelPart legL;
	private final ModelPart legR;
	private final ModelPart armL;
	private final ModelPart armR;
	private final ModelPart forearmL;
	private final ModelPart forearmR;
	private final ModelPart handL;
	private final ModelPart handR;
	private final ModelPart ragL;
	private final ModelPart ragR;
	private final ModelPart hump;
	private final ModelPart cape;
	private final ModelPart[] tendrils = new ModelPart[TENDRILS];
	private final ModelPart[] drips = new ModelPart[TENDRILS];
	private final ModelPart[] skirt = new ModelPart[SKIRT];
	private final ModelPart[] fine;

	public LurkerModel(ModelPart root) {
		super(root);
		ModelPart body = root.getChild("root");
		this.legL = body.getChild("leg_l");
		this.legR = body.getChild("leg_r");
		this.hips = body.getChild("hips");
		this.hump = hips.getChild("hump");
		this.cape = hips.getChild("cape");
		this.neck = hips.getChild("neck");
		this.head = neck.getChild("head");
		this.armL = hips.getChild("arm_l");
		this.armR = hips.getChild("arm_r");
		this.ragL = armL.getChild("rag_l");
		this.ragR = armR.getChild("rag_r");
		this.forearmL = armL.getChild("forearm_l");
		this.forearmR = armR.getChild("forearm_r");
		this.handL = forearmL.getChild("hand_l");
		this.handR = forearmR.getChild("hand_r");
		for (int i = 0; i < TENDRILS; i++) {
			tendrils[i] = head.getChild("tendril" + i);
			drips[i] = tendrils[i].getChild("drip" + i);
		}
		for (int i = 0; i < SKIRT; i++) {
			skirt[i] = hips.getChild("skirt" + i);
		}
		this.fine = new ModelPart[LurkerGeometry.FINE.length];
		for (int i = 0; i < fine.length; i++) {
			fine[i] = LurkerGeometry.find(root, LurkerGeometry.FINE[i]);
		}
	}

	/** The shared body (generated with the five textures, see {@code tools/lurker/build.py}). */
	public static LayerDefinition createBodyLayer() {
		return LurkerGeometry.create();
	}

	@Override
	public void setupAnim(LurkerRenderState s) {
		super.setupAnim(s);
		// The mask, eyes, brows, fringe and claws are fine parts, built four times larger for finer pixels
		for (ModelPart f : fine) {
			f.xScale = LurkerGeometry.FINE_SCALE;
			f.yScale = LurkerGeometry.FINE_SCALE;
			f.zScale = LurkerGeometry.FINE_SCALE;
		}
		float t = s.ageInTicks;
		float breathe = Mth.sin(t * 0.06F);
		float wp = s.walkAnimationPos * 0.55F;
		float ws = Math.min(1.0F, s.walkAnimationSpeed * 1.4F);
		float stalk = s.stalk;
		float take = s.take;
		float drain = s.drain;
		float run = s.run;
		// The run is a bounding gallop: both legs and the free arm move together, in a faster cycle
		float gp = s.walkAnimationPos * 0.42F;
		float bound = Mth.sin(gp);

		// Body: a slow, heavy breath; hunches lower while stalking, rears up while draining
		hips.xRot = HIPS_LEAN + 0.035F * breathe + 0.18F * stalk - 0.22F * drain + run * (0.32F + 0.1F * bound * ws);
		hips.y += breathe * 0.35F + 1.5F * stalk + run * (1.8F - 1.6F * Math.abs(bound) * ws);
		hump.xRot += 0.03F * Mth.sin(t * 0.06F + 0.6F);

		// Legs: short, heavy steps
		legL.xRot = Mth.cos(wp) * 0.55F * ws;
		legR.xRot = -Mth.cos(wp) * 0.55F * ws;
		legL.xRot = Mth.lerp(run, legL.xRot, -Mth.cos(gp) * 0.9F * ws - 0.15F);
		legR.xRot = Mth.lerp(run, legR.xRot, -Mth.cos(gp + 0.45F) * 0.9F * ws - 0.15F);

		// Arms: hang to the ground, knuckle-walk in counter-phase to the legs
		float hang = -hips.xRot - 0.15F;
		float swingL = Mth.cos(wp + Mth.PI) * 0.75F * ws;
		float swingR = Mth.cos(wp) * 0.75F * ws;
		float aLx = hang + swingL + 0.04F * breathe;
		float aRx = hang + swingR + 0.04F * Mth.sin(t * 0.06F + 1.3F);
		float aLz = -0.14F - 0.03F * breathe;
		float aRz = 0.14F + 0.03F * breathe;
		float fLx = -0.55F - 0.3F * ws * Math.max(0, Mth.sin(wp + Mth.PI));
		float fRx = -0.55F - 0.3F * ws * Math.max(0, Mth.sin(wp));

		// Drain: both arms raised towards the anchor, trembling as the light pours in
		float tremble = Mth.sin(t * 1.9F) * 0.035F * drain;
		aLx = Mth.lerp(drain, aLx, -hips.xRot - 1.25F + tremble);
		aRx = Mth.lerp(drain, aRx, -hips.xRot - 1.25F - tremble);
		aLz = Mth.lerp(drain, aLz, -0.42F);
		aRz = Mth.lerp(drain, aRz, 0.42F);
		fLx = Mth.lerp(drain, fLx, -0.55F + tremble);
		fRx = Mth.lerp(drain, fRx, -0.55F - tremble);

		// Run: the left arm clutches the loot to its chest, the right claws the ground in long bounds
		float runHang = -hips.xRot - 0.1F;
		aLx = Mth.lerp(run, aLx, -hips.xRot - 0.95F);
		aLz = Mth.lerp(run, aLz, 0.38F);
		fLx = Mth.lerp(run, fLx, -1.7F);
		aRx = Mth.lerp(run, aRx, runHang + Mth.cos(gp + Mth.PI) * 1.05F * ws);
		aRz = Mth.lerp(run, aRz, 0.18F);
		fRx = Mth.lerp(run, fRx, -0.45F - 0.5F * ws * Math.max(0, Mth.sin(gp + Mth.PI)));

		// Take: the right arm unfolds and reaches out
		aRx = Mth.lerp(take, aRx, -hips.xRot - 1.75F);
		aRz = Mth.lerp(take, aRz, 0.05F);
		fRx = Mth.lerp(take, fRx, -0.05F);

		armL.xRot = aLx;
		armR.xRot = aRx;
		armL.zRot = aLz;
		armR.zRot = aRz;
		forearmL.xRot = fLx;
		forearmR.xRot = fRx;
		handL.xRot = Mth.lerp(run, 0.35F * (1 - drain) + 0.1F * ws, 0.9F);
		handR.xRot = Mth.lerp(take, 0.35F * (1 - drain) + 0.1F * ws, -0.6F);

		// Head: follows its prey, with a slow, curious tilt; thrown back while draining
		neck.xRot = NECK_BEND + 0.1F * stalk + 0.15F * run;
		float level = -(hips.xRot + neck.xRot) + 0.2F;
		head.yRot = s.yRot * Mth.DEG_TO_RAD * 0.85F;
		head.xRot = level + s.xRot * Mth.DEG_TO_RAD * 0.6F + 0.04F * Mth.sin(t * 0.05F) - 0.45F * drain + 0.15F * take;
		head.zRot = 0.1F * Mth.sin(t * 0.031F) * (1 - drain) + 0.08F * run * Mth.sin(gp * 0.5F);
		// Running, it glances back over its shoulder now and then
		float glance = run * Math.max(0, Mth.sin(t * 0.045F)) * 0.9F;
		head.yRot = Mth.lerp(run, head.yRot, glance);
		head.xRot -= 0.1F * run * bound * ws;

		// Strands hang from the mask and sway; they flare out when it takes
		float down = -(hips.xRot + neck.xRot + head.xRot);
		for (int i = 0; i < TENDRILS; i++) {
			float ph = i * 1.37F;
			tendrils[i].xRot = down + Mth.sin(t * 0.08F + ph) * 0.12F + ws * 0.25F * Mth.sin(wp + ph) - 0.5F * take - 0.75F * run * ws;
			tendrils[i].zRot = Mth.sin(t * 0.063F + ph * 0.7F) * 0.11F + (i - 3.5F) * 0.05F * take;
			drips[i].xRot = Mth.sin(t * 0.11F + ph) * 0.18F;
		}

		// Cloak strips hang straight down from the leaning hips and drag behind the stride
		for (int i = 0; i < SKIRT; i++) {
			float ph = i * 0.9F;
			skirt[i].xRot = -hips.xRot + Mth.sin(t * 0.07F + ph) * 0.06F + ws * 0.35F * Mth.sin(wp + ph) + 0.15F * ws + run * (0.55F + 0.2F * Mth.sin(gp * 2 + ph));
			skirt[i].zRot = Mth.sin(t * 0.05F + ph) * 0.04F;
		}
		cape.xRot = -hips.xRot + 0.12F + Mth.sin(t * 0.05F) * 0.04F + ws * 0.3F + run * (0.75F + 0.15F * Mth.sin(gp * 2));
		cape.zRot = Mth.sin(t * 0.04F + 0.5F) * 0.03F;
		ragL.xRot = -armL.xRot - hips.xRot + Mth.sin(t * 0.07F) * 0.08F;
		ragR.xRot = -armR.xRot - hips.xRot + Mth.sin(t * 0.07F + 1F) * 0.08F;

		// The young: a big head on a small body, and a quicker, bouncier step
		float hs = s.young ? 1.55F : 1.0F;
		head.xScale = hs;
		head.yScale = hs;
		head.zScale = hs;
		if (s.young) {
			hips.y -= Math.abs(Mth.sin(wp * 1.3F)) * 1.2F * ws;
			head.zRot += 0.18F * Mth.sin(t * 0.09F);
		}
	}
}
