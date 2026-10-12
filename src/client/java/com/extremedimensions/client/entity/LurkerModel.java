package com.extremedimensions.client.entity;

import com.extremedimensions.ExtremeDimensions;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The Lurker: a hunched, cloaked giant on long knuckle-walking arms, with ragged cloak strips and strands that drip
 * from its mask. Everything moves procedurally: breathing, head tracking with a curious tilt, swaying strands and
 * cloth, a knuckle walk, the reach of a theft and the raised, trembling arms of a drain.
 *
 * <p>The geometry and the matching textures are generated together (see {@code tools/lurker} in the repository).
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
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition parts = mesh.getRoot();
		PartDefinition p_root = parts.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
		PartDefinition p_leg_l = p_root.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(104, 57).addBox(-3F, 0F, -3F, 6F, 13F, 6F), PartPose.offsetAndRotation(5F, -13F, 3F, 0F, 0F, 0F));
		PartDefinition p_leg_r = p_root.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(0, 78).addBox(-3F, 0F, -3F, 6F, 13F, 6F), PartPose.offsetAndRotation(-5F, -13F, 3F, 0F, 0F, 0F));
		PartDefinition p_hips = p_root.addOrReplaceChild("hips", CubeListBuilder.create().texOffs(0, 0).addBox(-9F, -20F, -6F, 18F, 20F, 12F), PartPose.offsetAndRotation(0F, -13F, 3F, 0.62F, 0F, 0F));
		PartDefinition p_hump = p_hips.addOrReplaceChild("hump", CubeListBuilder.create().texOffs(50, 32).addBox(-10F, -12F, -2F, 20F, 14F, 8F), PartPose.offsetAndRotation(0F, -14F, 4F, -0.25F, 0F, 0F));
		PartDefinition p_skirt0 = p_hips.addOrReplaceChild("skirt0", CubeListBuilder.create().texOffs(96, 113).addBox(-2F, 0F, -0.5F, 4F, 9F, 1F), PartPose.offsetAndRotation(-7F, -1F, -6F, 0F, 0F, 0F));
		PartDefinition p_skirt1 = p_hips.addOrReplaceChild("skirt1", CubeListBuilder.create().texOffs(108, 97).addBox(-2.5F, 0F, -0.5F, 5F, 11F, 1F), PartPose.offsetAndRotation(-2.5F, -1F, -6.5F, 0F, 0F, 0F));
		PartDefinition p_skirt2 = p_hips.addOrReplaceChild("skirt2", CubeListBuilder.create().texOffs(76, 113).addBox(-2F, 0F, -0.5F, 4F, 10F, 1F), PartPose.offsetAndRotation(3F, -1F, -6F, 0F, 0F, 0F));
		PartDefinition p_skirt3 = p_hips.addOrReplaceChild("skirt3", CubeListBuilder.create().texOffs(0, 125).addBox(-2F, 0F, -0.5F, 4F, 8F, 1F), PartPose.offsetAndRotation(7.5F, -1F, -3F, 0F, 0F, 0F));
		PartDefinition p_skirt4 = p_hips.addOrReplaceChild("skirt4", CubeListBuilder.create().texOffs(0, 113).addBox(-2.5F, 0F, -0.5F, 5F, 11F, 1F), PartPose.offsetAndRotation(-7F, -1F, 5.5F, 0F, 0F, 0F));
		PartDefinition p_skirt5 = p_hips.addOrReplaceChild("skirt5", CubeListBuilder.create().texOffs(94, 97).addBox(-2F, 0F, -0.5F, 4F, 12F, 1F), PartPose.offsetAndRotation(-2F, -1F, 6.5F, 0F, 0F, 0F));
		PartDefinition p_skirt6 = p_hips.addOrReplaceChild("skirt6", CubeListBuilder.create().texOffs(86, 113).addBox(-2F, 0F, -0.5F, 4F, 10F, 1F), PartPose.offsetAndRotation(3.5F, -1F, 6F, 0F, 0F, 0F));
		PartDefinition p_skirt7 = p_hips.addOrReplaceChild("skirt7", CubeListBuilder.create().texOffs(10, 125).addBox(-2.5F, 0F, -0.5F, 5F, 8F, 1F), PartPose.offsetAndRotation(-8F, -1F, 0F, 0F, 0F, 0F));
		PartDefinition p_skirt8 = p_hips.addOrReplaceChild("skirt8", CubeListBuilder.create().texOffs(106, 113).addBox(-2F, 0F, -0.5F, 4F, 9F, 1F), PartPose.offsetAndRotation(8F, -1F, 1F, 0F, 0F, 0F));
		PartDefinition p_cape = p_hips.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(60, 0).addBox(-10F, 0F, 0F, 20F, 24F, 2F), PartPose.offsetAndRotation(0F, -19F, 6F, 0F, 0F, 0F));
		PartDefinition p_mantle_l = p_hips.addOrReplaceChild("mantle_l", CubeListBuilder.create().texOffs(72, 78).addBox(-3F, -2F, -5F, 8F, 5F, 11F), PartPose.offsetAndRotation(8F, -20F, -1F, 0F, 0F, 0.35F));
		PartDefinition p_mantle_r = p_hips.addOrReplaceChild("mantle_r", CubeListBuilder.create().texOffs(0, 97).addBox(-5F, -2F, -5F, 8F, 5F, 11F), PartPose.offsetAndRotation(-8F, -20F, -1F, 0F, 0F, -0.35F));
		PartDefinition p_neck = p_hips.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(54, 97).addBox(-3.5F, -7F, -3.5F, 7F, 8F, 7F), PartPose.offsetAndRotation(0F, -19F, -3F, 0.35F, 0F, 0F));
		PartDefinition p_head = p_neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 32).addBox(-6F, -11F, -10F, 12F, 12F, 13F), PartPose.offsetAndRotation(0F, -6F, -1F, -0.77F, 0F, 0F));
		PartDefinition p_hood = p_head.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 57).addBox(-7F, -12.5F, -8F, 14F, 7F, 14F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_tendril0 = p_head.addOrReplaceChild("tendril0", CubeListBuilder.create().texOffs(82, 97).addBox(-0.5F, 0F, -0.5F, 1F, 14F, 1F), PartPose.offsetAndRotation(-5F, 1F, -8F, 0F, 0F, 0F));
		PartDefinition p_drip0 = p_tendril0.addOrReplaceChild("drip0", CubeListBuilder.create().texOffs(30, 125).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 14F, 0F, 0F, 0F, 0F));
		PartDefinition p_tendril1 = p_head.addOrReplaceChild("tendril1", CubeListBuilder.create().texOffs(12, 113).addBox(-1F, 0F, -1F, 2F, 10F, 2F), PartPose.offsetAndRotation(-3F, 1F, -9.5F, 0F, 0F, 0F));
		PartDefinition p_drip1 = p_tendril1.addOrReplaceChild("drip1", CubeListBuilder.create().texOffs(38, 125).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 10F, 0F, 0F, 0F, 0F));
		PartDefinition p_tendril2 = p_head.addOrReplaceChild("tendril2", CubeListBuilder.create().texOffs(24, 78).addBox(-0.5F, 0F, -0.5F, 1F, 18F, 1F), PartPose.offsetAndRotation(-1F, 1F, -9F, 0F, 0F, 0F));
		PartDefinition p_drip2 = p_tendril2.addOrReplaceChild("drip2", CubeListBuilder.create().texOffs(46, 125).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 18F, 0F, 0F, 0F, 0F));
		PartDefinition p_tendril3 = p_head.addOrReplaceChild("tendril3", CubeListBuilder.create().texOffs(86, 97).addBox(-1F, 0F, -1F, 2F, 13F, 2F), PartPose.offsetAndRotation(1.5F, 1F, -9.5F, 0F, 0F, 0F));
		PartDefinition p_drip3 = p_tendril3.addOrReplaceChild("drip3", CubeListBuilder.create().texOffs(54, 125).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 13F, 0F, 0F, 0F, 0F));
		PartDefinition p_tendril4 = p_head.addOrReplaceChild("tendril4", CubeListBuilder.create().texOffs(68, 78).addBox(-0.5F, 0F, -0.5F, 1F, 16F, 1F), PartPose.offsetAndRotation(3.5F, 1F, -9F, 0F, 0F, 0F));
		PartDefinition p_drip4 = p_tendril4.addOrReplaceChild("drip4", CubeListBuilder.create().texOffs(62, 125).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 16F, 0F, 0F, 0F, 0F));
		PartDefinition p_tendril5 = p_head.addOrReplaceChild("tendril5", CubeListBuilder.create().texOffs(20, 113).addBox(-0.5F, 0F, -0.5F, 1F, 11F, 1F), PartPose.offsetAndRotation(5F, 1F, -7.5F, 0F, 0F, 0F));
		PartDefinition p_drip5 = p_tendril5.addOrReplaceChild("drip5", CubeListBuilder.create().texOffs(70, 125).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 11F, 0F, 0F, 0F, 0F));
		PartDefinition p_tendril6 = p_head.addOrReplaceChild("tendril6", CubeListBuilder.create().texOffs(116, 113).addBox(-0.5F, 0F, -0.5F, 1F, 9F, 1F), PartPose.offsetAndRotation(-2F, 1F, -6F, 0F, 0F, 0F));
		PartDefinition p_drip6 = p_tendril6.addOrReplaceChild("drip6", CubeListBuilder.create().texOffs(78, 125).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 9F, 0F, 0F, 0F, 0F));
		PartDefinition p_tendril7 = p_head.addOrReplaceChild("tendril7", CubeListBuilder.create().texOffs(104, 97).addBox(-0.5F, 0F, -0.5F, 1F, 12F, 1F), PartPose.offsetAndRotation(2F, 1F, -6F, 0F, 0F, 0F));
		PartDefinition p_drip7 = p_tendril7.addOrReplaceChild("drip7", CubeListBuilder.create().texOffs(86, 125).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 12F, 0F, 0F, 0F, 0F));
		PartDefinition p_arm_l = p_hips.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(56, 57).addBox(-3F, -2F, -3F, 6F, 14F, 6F), PartPose.offsetAndRotation(10F, -18F, -2F, 0F, 0F, 0F));
		PartDefinition p_rag_l = p_arm_l.addOrReplaceChild("rag_l", CubeListBuilder.create().texOffs(38, 97).addBox(-0.5F, 0F, -1.5F, 1F, 13F, 3F), PartPose.offsetAndRotation(3F, 3F, 0F, 0F, 0F, 0F));
		PartDefinition p_forearm_l = p_arm_l.addOrReplaceChild("forearm_l", CubeListBuilder.create().texOffs(28, 78).addBox(-2.5F, 0F, -2.5F, 5F, 13F, 5F), PartPose.offsetAndRotation(0F, 11F, 0F, 0F, 0F, 0F));
		PartDefinition p_hand_l = p_forearm_l.addOrReplaceChild("hand_l", CubeListBuilder.create().texOffs(24, 113).addBox(-3F, 0F, -4.5F, 6F, 5F, 7F).texOffs(94, 125).addBox(-2.5F, 4.5F, -4.5F, 1F, 3F, 1F).texOffs(22, 125).addBox(-0.5F, 4.5F, -5F, 1F, 4F, 1F).texOffs(98, 125).addBox(1.5F, 4.5F, -4.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(0F, 12F, 0F, 0F, 0F, 0F));
		PartDefinition p_arm_r = p_hips.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(80, 57).addBox(-3F, -2F, -3F, 6F, 14F, 6F), PartPose.offsetAndRotation(-10F, -18F, -2F, 0F, 0F, 0F));
		PartDefinition p_rag_r = p_arm_r.addOrReplaceChild("rag_r", CubeListBuilder.create().texOffs(46, 97).addBox(-0.5F, 0F, -1.5F, 1F, 13F, 3F), PartPose.offsetAndRotation(-3F, 3F, 0F, 0F, 0F, 0F));
		PartDefinition p_forearm_r = p_arm_r.addOrReplaceChild("forearm_r", CubeListBuilder.create().texOffs(48, 78).addBox(-2.5F, 0F, -2.5F, 5F, 13F, 5F), PartPose.offsetAndRotation(0F, 11F, 0F, 0F, 0F, 0F));
		PartDefinition p_hand_r = p_forearm_r.addOrReplaceChild("hand_r", CubeListBuilder.create().texOffs(50, 113).addBox(-3F, 0F, -4.5F, 6F, 5F, 7F).texOffs(102, 125).addBox(-2.5F, 4.5F, -4.5F, 1F, 3F, 1F).texOffs(26, 125).addBox(-0.5F, 4.5F, -5F, 1F, 4F, 1F).texOffs(106, 125).addBox(1.5F, 4.5F, -4.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(0F, 12F, 0F, 0F, 0F, 0F));
		return LayerDefinition.create(mesh, 128, 256);
	}

	@Override
	public void setupAnim(LurkerRenderState s) {
		super.setupAnim(s);
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
