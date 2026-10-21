package com.extremedimensions.client.entity;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Geometry of VeilRay, written by tools/elders (do not edit by hand: run the generator). */
public final class VeilRayGeometry {
	private VeilRayGeometry() {}

	public static LayerDefinition create() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part0(r);
		return LayerDefinition.create(mesh, 512, 512);
	}

	private static void part0(PartDefinition r) {
		PartDefinition p_root = r.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 18F, 0F, 0F, 0F, 0F));
		PartDefinition p_body = p_root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(320, 266).addBox(-4.6F, -1.6F, -8.6F, 9.2F, 3.2F, 17.2F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_rback = p_body.addOrReplaceChild("rback", CubeListBuilder.create().texOffs(0, 0).addBox(-20.8F, -8F, -36.8F, 41.6F, 1.6F, 73.6F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_rbelly = p_body.addOrReplaceChild("rbelly", CubeListBuilder.create().texOffs(232, 0).addBox(-20.8F, 6.4F, -36.8F, 41.6F, 1.6F, 73.6F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_rridge = p_body.addOrReplaceChild("rridge", CubeListBuilder.create().texOffs(0, 150).addBox(-2F, -4F, -30F, 4F, 4F, 60F), PartPose.offsetAndRotation(0F, -2F, 0F, 0F, 0F, 0F));
		PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(114, 298).addBox(-3.4F, -1F, -3.6F, 6.8F, 2F, 3.6F), PartPose.offsetAndRotation(0F, 0F, -9F, 0F, 0F, 0F));
		PartDefinition p_rhead = p_head.addOrReplaceChild("rhead", CubeListBuilder.create().texOffs(224, 266).addBox(-16F, -5.6F, -15.2F, 32F, 11.2F, 15.2F).texOffs(432, 266).addBox(-14F, -4.4F, -19.2F, 28F, 8.8F, 4F).texOffs(40, 298).addBox(-10.4F, -3.2F, -21.2F, 20.8F, 6F, 2F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_reye_l = p_head.addOrReplaceChild("reye_l", CubeListBuilder.create().texOffs(496, 266).addBox(-0.6F, -2.2F, -2.8F, 1.2F, 4.4F, 5.6F), PartPose.offsetAndRotation(4.05F, -0.35F, -2.4F, 0F, 0.5F, 0F));
		PartDefinition p_horn_l = p_head.addOrReplaceChild("horn_l", CubeListBuilder.create().texOffs(86, 298).addBox(-0.5F, -0.5F, -5.5F, 1F, 1F, 5.5F), PartPose.offsetAndRotation(2.8F, 0F, -4.6F, 0.08F, -0.35F, 0F));
		PartDefinition p_rhorntip_l = p_horn_l.addOrReplaceChild("rhorntip_l", CubeListBuilder.create().texOffs(376, 266).addBox(-1.2F, -1.2F, -10.4F, 2.4F, 2.4F, 10.4F), PartPose.offsetAndRotation(0F, 0F, -5.5F, 0.25F, 0F, 0F));
		PartDefinition p_wing_l = p_body.addOrReplaceChild("wing_l", CubeListBuilder.create(), PartPose.offsetAndRotation(5.2F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_rw0_l = p_wing_l.addOrReplaceChild("rw0_l", CubeListBuilder.create().texOffs(0, 76).addBox(0F, -2.8F, -36F, 48F, 5.6F, 68F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_wingtip_l = p_wing_l.addOrReplaceChild("wingtip_l", CubeListBuilder.create(), PartPose.offsetAndRotation(12F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_rw1_l = p_wingtip_l.addOrReplaceChild("rw1_l", CubeListBuilder.create().texOffs(236, 150).addBox(0F, -2F, -28F, 44F, 4F, 48F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_wingend_l = p_wingtip_l.addOrReplaceChild("wingend_l", CubeListBuilder.create(), PartPose.offsetAndRotation(11F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_rw2_l = p_wingend_l.addOrReplaceChild("rw2_l", CubeListBuilder.create().texOffs(0, 266).addBox(0F, -1.6F, -20F, 28F, 3.2F, 28F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_reye_r = p_head.addOrReplaceChild("reye_r", CubeListBuilder.create().texOffs(0, 298).addBox(-0.6F, -2.2F, -2.8F, 1.2F, 4.4F, 5.6F), PartPose.offsetAndRotation(-4.05F, -0.35F, -2.4F, 0F, -0.5F, 0F));
		PartDefinition p_horn_r = p_head.addOrReplaceChild("horn_r", CubeListBuilder.create().texOffs(100, 298).addBox(-0.5F, -0.5F, -5.5F, 1F, 1F, 5.5F), PartPose.offsetAndRotation(-2.8F, 0F, -4.6F, 0.08F, 0.35F, 0F));
		PartDefinition p_rhorntip_r = p_horn_r.addOrReplaceChild("rhorntip_r", CubeListBuilder.create().texOffs(404, 266).addBox(-1.2F, -1.2F, -10.4F, 2.4F, 2.4F, 10.4F), PartPose.offsetAndRotation(0F, 0F, -5.5F, 0.25F, 0F, 0F));
		PartDefinition p_wing_r = p_body.addOrReplaceChild("wing_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.2F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_rw0_r = p_wing_r.addOrReplaceChild("rw0_r", CubeListBuilder.create().texOffs(232, 76).addBox(-48F, -2.8F, -36F, 48F, 5.6F, 68F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_wingtip_r = p_wing_r.addOrReplaceChild("wingtip_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-12F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_rw1_r = p_wingtip_r.addOrReplaceChild("rw1_r", CubeListBuilder.create().texOffs(0, 214).addBox(-44F, -2F, -28F, 44F, 4F, 48F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_wingend_r = p_wingtip_r.addOrReplaceChild("wingend_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-11F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_rw2_r = p_wingend_r.addOrReplaceChild("rw2_r", CubeListBuilder.create().texOffs(112, 266).addBox(-28F, -1.6F, -20F, 28F, 3.2F, 28F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_tail0 = p_body.addOrReplaceChild("tail0", CubeListBuilder.create().texOffs(16, 298).addBox(-1.8F, -0.9F, 0F, 3.6F, 1.8F, 8F), PartPose.offsetAndRotation(0F, 0F, 9F, 0F, 0F, 0F));
		PartDefinition p_rtail0 = p_tail0.addOrReplaceChild("rtail0", CubeListBuilder.create().texOffs(350, 214).addBox(-8.4F, -4.4F, 0F, 16.8F, 8.8F, 32.8F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_tail1 = p_tail0.addOrReplaceChild("tail1", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 0F, 8F, 0F, 0F, 0F));
		PartDefinition p_rtail1 = p_tail1.addOrReplaceChild("rtail1", CubeListBuilder.create().texOffs(184, 214).addBox(-4.4F, -2.4F, 0F, 8.8F, 4.8F, 40.8F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_tail2 = p_tail1.addOrReplaceChild("tail2", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 0F, 10F, 0F, 0F, 0F));
		PartDefinition p_rtail2 = p_tail2.addOrReplaceChild("rtail2", CubeListBuilder.create().texOffs(128, 150).addBox(-2F, -2F, 0F, 4F, 4F, 50F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_fin = p_body.addOrReplaceChild("fin", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -2F, 2F, 0F, 0F, 0F));
		PartDefinition p_rfin = p_fin.addOrReplaceChild("rfin", CubeListBuilder.create().texOffs(284, 214).addBox(-1.2F, -13.6F, -14F, 2.4F, 13.6F, 30F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
	}

	/** Parts that move together: fine. */
	public static final String[] FINE = {"rback", "rbelly", "rridge", "rhead", "reye_l", "rhorntip_l", "rw0_l", "rw1_l", "rw2_l", "reye_r", "rhorntip_r", "rw0_r", "rw1_r", "rw2_r", "rtail0", "rtail1", "rtail2", "rfin"};

	/** How much the fine parts are shrunk at run time (their boxes are built this much larger, for finer pixels). */
	public static final float FINE_SCALE = 0.25F;

	/** The animated parts, by name. */
	public static ModelPart find(ModelPart root, String name) {
		return switch (name) {
			case "rback" -> root.getChild("root").getChild("body").getChild("rback");
			case "rbelly" -> root.getChild("root").getChild("body").getChild("rbelly");
			case "rridge" -> root.getChild("root").getChild("body").getChild("rridge");
			case "rhead" -> root.getChild("root").getChild("body").getChild("head").getChild("rhead");
			case "reye_l" -> root.getChild("root").getChild("body").getChild("head").getChild("reye_l");
			case "rhorntip_l" -> root.getChild("root").getChild("body").getChild("head").getChild("horn_l").getChild("rhorntip_l");
			case "rw0_l" -> root.getChild("root").getChild("body").getChild("wing_l").getChild("rw0_l");
			case "rw1_l" -> root.getChild("root").getChild("body").getChild("wing_l").getChild("wingtip_l").getChild("rw1_l");
			case "rw2_l" -> root.getChild("root").getChild("body").getChild("wing_l").getChild("wingtip_l").getChild("wingend_l").getChild("rw2_l");
			case "reye_r" -> root.getChild("root").getChild("body").getChild("head").getChild("reye_r");
			case "rhorntip_r" -> root.getChild("root").getChild("body").getChild("head").getChild("horn_r").getChild("rhorntip_r");
			case "rw0_r" -> root.getChild("root").getChild("body").getChild("wing_r").getChild("rw0_r");
			case "rw1_r" -> root.getChild("root").getChild("body").getChild("wing_r").getChild("wingtip_r").getChild("rw1_r");
			case "rw2_r" -> root.getChild("root").getChild("body").getChild("wing_r").getChild("wingtip_r").getChild("wingend_r").getChild("rw2_r");
			case "rtail0" -> root.getChild("root").getChild("body").getChild("tail0").getChild("rtail0");
			case "rtail1" -> root.getChild("root").getChild("body").getChild("tail0").getChild("tail1").getChild("rtail1");
			case "rtail2" -> root.getChild("root").getChild("body").getChild("tail0").getChild("tail1").getChild("tail2").getChild("rtail2");
			case "rfin" -> root.getChild("root").getChild("body").getChild("fin").getChild("rfin");
			default -> throw new IllegalArgumentException("no animated part " + name);
		};
	}
}
