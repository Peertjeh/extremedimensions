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
 * The Veil Ray: a flat body with a horned head, three-jointed wings and a long whip of a tail. The wings beat in a slow
 * travelling wave from the shoulder to the tip, the tail sways a beat behind, and the whole body banks into its turns.
 *
 * <p>The geometry and the matching textures are generated together (see {@code tools/veil_ray} in the repository).
 */
public class VeilRayModel extends EntityModel<VeilRayRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ExtremeDimensions.id("veil_ray"), "main");

	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart wingL;
	private final ModelPart wingR;
	private final ModelPart tipL;
	private final ModelPart tipR;
	private final ModelPart endL;
	private final ModelPart endR;
	private final ModelPart tail0;
	private final ModelPart tail1;
	private final ModelPart tail2;

	public VeilRayModel(ModelPart root) {
		super(root);
		this.body = root.getChild("root").getChild("body");
		this.head = body.getChild("head");
		this.wingL = body.getChild("wing_l");
		this.wingR = body.getChild("wing_r");
		this.tipL = wingL.getChild("wingtip_l");
		this.tipR = wingR.getChild("wingtip_r");
		this.endL = tipL.getChild("wingend_l");
		this.endR = tipR.getChild("wingend_r");
		this.tail0 = body.getChild("tail0");
		this.tail1 = tail0.getChild("tail1");
		this.tail2 = tail1.getChild("tail2");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition parts = mesh.getRoot();
		PartDefinition p_root = parts.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 18F, 0F, 0F, 0F, 0F));
		PartDefinition p_body = p_root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5F, -2F, -9F, 10F, 4F, 18F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 50).addBox(-4F, -1.5F, -5F, 8F, 3F, 5F), PartPose.offsetAndRotation(0F, 0F, -9F, 0F, 0F, 0F));
		PartDefinition p_horn_l = p_head.addOrReplaceChild("horn_l", CubeListBuilder.create().texOffs(26, 50).addBox(-0.5F, -0.5F, -5F, 1F, 1F, 5F), PartPose.offsetAndRotation(3F, 0F, -5F, 0F, -0.35F, 0F));
		PartDefinition p_horn_r = p_head.addOrReplaceChild("horn_r", CubeListBuilder.create().texOffs(38, 50).addBox(-0.5F, -0.5F, -5F, 1F, 1F, 5F), PartPose.offsetAndRotation(-3F, 0F, -5F, 0F, 0.35F, 0F));
		PartDefinition p_wing_l = p_body.addOrReplaceChild("wing_l", CubeListBuilder.create().texOffs(56, 0).addBox(0F, -1F, -8F, 12F, 2F, 15F), PartPose.offsetAndRotation(5F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_wingtip_l = p_wing_l.addOrReplaceChild("wingtip_l", CubeListBuilder.create().texOffs(80, 22).addBox(0F, -0.5F, -6F, 11F, 1F, 10F), PartPose.offsetAndRotation(12F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_wingend_l = p_wingtip_l.addOrReplaceChild("wingend_l", CubeListBuilder.create().texOffs(50, 50).addBox(0F, -0.5F, -4F, 7F, 1F, 5F), PartPose.offsetAndRotation(11F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_wing_r = p_body.addOrReplaceChild("wing_r", CubeListBuilder.create().texOffs(0, 22).addBox(-12F, -1F, -8F, 12F, 2F, 15F), PartPose.offsetAndRotation(-5F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_wingtip_r = p_wing_r.addOrReplaceChild("wingtip_r", CubeListBuilder.create().texOffs(0, 39).addBox(-11F, -0.5F, -6F, 11F, 1F, 10F), PartPose.offsetAndRotation(-12F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_wingend_r = p_wingtip_r.addOrReplaceChild("wingend_r", CubeListBuilder.create().texOffs(74, 50).addBox(-7F, -0.5F, -4F, 7F, 1F, 5F), PartPose.offsetAndRotation(-11F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_tail0 = p_body.addOrReplaceChild("tail0", CubeListBuilder.create().texOffs(66, 39).addBox(-2F, -1F, 0F, 4F, 2F, 8F), PartPose.offsetAndRotation(0F, 0F, 9F, 0F, 0F, 0F));
		PartDefinition p_tail1 = p_tail0.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(42, 39).addBox(-1F, -0.5F, 0F, 2F, 1F, 10F), PartPose.offsetAndRotation(0F, 0F, 8F, 0F, 0F, 0F));
		PartDefinition p_tail2 = p_tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(54, 22).addBox(-0.5F, -0.5F, 0F, 1F, 1F, 12F), PartPose.offsetAndRotation(0F, 0F, 10F, 0F, 0F, 0F));
		PartDefinition p_fin = p_body.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(90, 39).addBox(-0.5F, -3F, -3F, 1F, 3F, 7F), PartPose.offsetAndRotation(0F, -2F, 2F, 0F, 0F, 0F));
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(VeilRayRenderState s) {
		super.setupAnim(s);
		float t = s.ageInTicks * 0.09F;
		// A travelling wave: shoulder, then elbow, then tip, each a little later and a little wider
		float w0 = Mth.sin(t) * 0.32F;
		float w1 = Mth.sin(t - 0.7F) * 0.38F;
		float w2 = Mth.sin(t - 1.4F) * 0.45F;
		wingL.zRot = -w0 + s.bank * 0.25F;
		wingR.zRot = w0 + s.bank * 0.25F;
		tipL.zRot = -w1;
		tipR.zRot = w1;
		endL.zRot = -w2;
		endR.zRot = w2;
		// The body rises and falls against the beat, and banks into the turn
		body.y += Mth.cos(t) * 0.8F;
		body.zRot = -s.bank * 0.45F;
		body.xRot = s.xRot * Mth.DEG_TO_RAD * 0.5F;
		head.xRot = Mth.sin(t - 0.3F) * 0.05F;
		// The tail follows, a whip a beat behind
		tail0.xRot = Mth.sin(t - 1.0F) * 0.12F;
		tail1.xRot = Mth.sin(t - 1.7F) * 0.16F;
		tail2.xRot = Mth.sin(t - 2.4F) * 0.22F;
		tail0.yRot = -s.bank * 0.25F;
		tail1.yRot = Mth.sin(t * 0.5F - 1.2F) * 0.12F - s.bank * 0.2F;
		tail2.yRot = Mth.sin(t * 0.5F - 1.9F) * 0.18F;
	}
}
