package com.extremedimensions.client.entity.elder;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Geometry of StarvedOne, written by tools/elders (do not edit by hand: run the generator). */
public final class StarvedOneGeometry {
	private StarvedOneGeometry() {}

	public static LayerDefinition create() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part0(r);
		part1(r);
		part2(r);
		part3(r);
		part4(r);
		part5(r);
		return LayerDefinition.create(mesh, 512, 512);
	}

	private static void part0(PartDefinition r) {
		PartDefinition p_root = r.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
		PartDefinition p_body = p_root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-9F, -77F, -6F, 18F, 37F, 12F).texOffs(192, 84).addBox(-10F, -44F, -7F, 20F, 8F, 14F).texOffs(500, 49).addBox(-1.2F, -72F, -6.7F, 2.4F, 26F, 1F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_skirt_0 = p_body.addOrReplaceChild("skirt_0", CubeListBuilder.create().texOffs(260, 49).addBox(-2.6F, 0F, -0.8F, 5.2F, 30F, 1.6F), PartPose.offsetAndRotation(0F, -42F, 7.5F, 0.12F, 0F, 0F));
		PartDefinition p_skirt_1 = p_body.addOrReplaceChild("skirt_1", CubeListBuilder.create().texOffs(144, 0).addBox(-2.6F, 0F, -0.8F, 6.2F, 37F, 1.6F), PartPose.offsetAndRotation(2.9724F, -42F, 7.161F, 0.14F, 0.3018F, 0F));
		PartDefinition p_skirt_2 = p_body.addOrReplaceChild("skirt_2", CubeListBuilder.create().texOffs(226, 0).addBox(-2.6F, 0F, -0.8F, 5.2F, 36F, 1.6F), PartPose.offsetAndRotation(5.6761F, -42F, 6.1747F, 0.16F, 0.6036F, 0F));
		PartDefinition p_skirt_3 = p_body.addOrReplaceChild("skirt_3", CubeListBuilder.create().texOffs(302, 0).addBox(-2.6F, 0F, -0.8F, 6.2F, 35F, 1.6F), PartPose.offsetAndRotation(7.0711F, -42F, 5.3033F, 0.12F, 0.7854F, 0F));
		PartDefinition p_skirt_4 = p_body.addOrReplaceChild("skirt_4", CubeListBuilder.create().texOffs(384, 0).addBox(-2.6F, 0F, -0.8F, 5.2F, 34F, 1.6F), PartPose.offsetAndRotation(8.8533F, -42F, 3.4873F, 0.14F, 1.0872F, 0F));
		PartDefinition p_skirt_5 = p_body.addOrReplaceChild("skirt_5", CubeListBuilder.create().texOffs(64, 49).addBox(-2.6F, 0F, -0.8F, 6.2F, 33F, 1.6F), PartPose.offsetAndRotation(9.8352F, -42F, 1.356F, 0.16F, 1.389F, 0F));
		PartDefinition p_skirt_6 = p_body.addOrReplaceChild("skirt_6", CubeListBuilder.create().texOffs(118, 49).addBox(-2.6F, 0F, -0.8F, 5.2F, 32F, 1.6F), PartPose.offsetAndRotation(10F, -42F, 0F, 0.12F, 1.5708F, 0F));
		PartDefinition p_skirt_7 = p_body.addOrReplaceChild("skirt_7", CubeListBuilder.create().texOffs(166, 49).addBox(-2.6F, 0F, -0.8F, 6.2F, 31F, 1.6F), PartPose.offsetAndRotation(9.548F, -42F, -2.2293F, 0.14F, 1.8726F, 0F));
		PartDefinition p_skirt_8 = p_body.addOrReplaceChild("skirt_8", CubeListBuilder.create().texOffs(276, 49).addBox(-2.6F, 0F, -0.8F, 5.2F, 30F, 1.6F), PartPose.offsetAndRotation(8.233F, -42F, -4.2571F, 0.16F, 2.1744F, 0F));
		PartDefinition p_skirt_9 = p_body.addOrReplaceChild("skirt_9", CubeListBuilder.create().texOffs(162, 0).addBox(-2.6F, 0F, -0.8F, 6.2F, 37F, 1.6F), PartPose.offsetAndRotation(7.0711F, -42F, -5.3033F, 0.12F, 2.3562F, 0F));
		PartDefinition p_skirt_10 = p_body.addOrReplaceChild("skirt_10", CubeListBuilder.create().texOffs(242, 0).addBox(-2.6F, 0F, -0.8F, 5.2F, 36F, 1.6F), PartPose.offsetAndRotation(4.6497F, -42F, -6.64F, 0.14F, 2.658F, 0F));
		PartDefinition p_skirt_11 = p_body.addOrReplaceChild("skirt_11", CubeListBuilder.create().texOffs(320, 0).addBox(-2.6F, 0F, -0.8F, 6.2F, 35F, 1.6F), PartPose.offsetAndRotation(1.808F, -42F, -7.3764F, 0.16F, 2.9598F, 0F));
		PartDefinition p_skirt_12 = p_body.addOrReplaceChild("skirt_12", CubeListBuilder.create().texOffs(400, 0).addBox(-2.6F, 0F, -0.8F, 5.2F, 34F, 1.6F), PartPose.offsetAndRotation(0F, -42F, -7.5F, 0.12F, 3.1416F, 0F));
		PartDefinition p_skirt_13 = p_body.addOrReplaceChild("skirt_13", CubeListBuilder.create().texOffs(82, 49).addBox(-2.6F, 0F, -0.8F, 6.2F, 33F, 1.6F), PartPose.offsetAndRotation(-2.9724F, -42F, -7.161F, 0.14F, 3.4434F, 0F));
		PartDefinition p_skirt_14 = p_body.addOrReplaceChild("skirt_14", CubeListBuilder.create().texOffs(134, 49).addBox(-2.6F, 0F, -0.8F, 5.2F, 32F, 1.6F), PartPose.offsetAndRotation(-5.6761F, -42F, -6.1747F, 0.16F, 3.7452F, 0F));
		PartDefinition p_skirt_15 = p_body.addOrReplaceChild("skirt_15", CubeListBuilder.create().texOffs(184, 49).addBox(-2.6F, 0F, -0.8F, 6.2F, 31F, 1.6F), PartPose.offsetAndRotation(-7.0711F, -42F, -5.3033F, 0.12F, 3.927F, 0F));
		PartDefinition p_skirt_16 = p_body.addOrReplaceChild("skirt_16", CubeListBuilder.create().texOffs(292, 49).addBox(-2.6F, 0F, -0.8F, 5.2F, 30F, 1.6F), PartPose.offsetAndRotation(-8.8533F, -42F, -3.4873F, 0.14F, 4.2288F, 0F));
		PartDefinition p_skirt_17 = p_body.addOrReplaceChild("skirt_17", CubeListBuilder.create().texOffs(180, 0).addBox(-2.6F, 0F, -0.8F, 6.2F, 37F, 1.6F), PartPose.offsetAndRotation(-9.8352F, -42F, -1.356F, 0.16F, 4.5306F, 0F));
		PartDefinition p_skirt_18 = p_body.addOrReplaceChild("skirt_18", CubeListBuilder.create().texOffs(258, 0).addBox(-2.6F, 0F, -0.8F, 5.2F, 36F, 1.6F), PartPose.offsetAndRotation(-10F, -42F, 0F, 0.12F, 4.7124F, 0F));
		PartDefinition p_skirt_19 = p_body.addOrReplaceChild("skirt_19", CubeListBuilder.create().texOffs(338, 0).addBox(-2.6F, 0F, -0.8F, 6.2F, 35F, 1.6F), PartPose.offsetAndRotation(-9.548F, -42F, 2.2293F, 0.14F, 5.0142F, 0F));
		PartDefinition p_skirt_20 = p_body.addOrReplaceChild("skirt_20", CubeListBuilder.create().texOffs(416, 0).addBox(-2.6F, 0F, -0.8F, 5.2F, 34F, 1.6F), PartPose.offsetAndRotation(-8.233F, -42F, 4.2571F, 0.16F, 5.316F, 0F));
		PartDefinition p_skirt_21 = p_body.addOrReplaceChild("skirt_21", CubeListBuilder.create().texOffs(100, 49).addBox(-2.6F, 0F, -0.8F, 6.2F, 33F, 1.6F), PartPose.offsetAndRotation(-7.0711F, -42F, 5.3033F, 0.12F, 5.4978F, 0F));
		PartDefinition p_skirt_22 = p_body.addOrReplaceChild("skirt_22", CubeListBuilder.create().texOffs(150, 49).addBox(-2.6F, 0F, -0.8F, 5.2F, 32F, 1.6F), PartPose.offsetAndRotation(-4.6497F, -42F, 6.64F, 0.14F, 5.7996F, 0F));
		PartDefinition p_skirt_23 = p_body.addOrReplaceChild("skirt_23", CubeListBuilder.create().texOffs(202, 49).addBox(-2.6F, 0F, -0.8F, 6.2F, 31F, 1.6F), PartPose.offsetAndRotation(-1.808F, -42F, 7.3764F, 0.16F, 6.1014F, 0F));
		PartDefinition p_shred_0 = p_body.addOrReplaceChild("shred_0", CubeListBuilder.create().texOffs(432, 0).addBox(-2.2F, 0F, -0.7F, 4.4F, 34F, 1.4F), PartPose.offsetAndRotation(1.2914F, -40F, 4.4103F, 0.05F, 0.2F, 0F));
		PartDefinition p_shred_1 = p_body.addOrReplaceChild("shred_1", CubeListBuilder.create().texOffs(88, 0).addBox(-2.2F, 0F, -0.7F, 4.4F, 39F, 1.4F), PartPose.offsetAndRotation(3.9275F, -40F, 3.5856F, 0.05F, 0.6488F, 0F));
		PartDefinition p_shred_2 = p_body.addOrReplaceChild("shred_2", CubeListBuilder.create().texOffs(198, 0).addBox(-2.2F, 0F, -0.7F, 4.4F, 37F, 1.4F), PartPose.offsetAndRotation(5.7857F, -40F, 2.0508F, 0.05F, 1.0976F, 0F));
		PartDefinition p_shred_3 = p_body.addOrReplaceChild("shred_3", CubeListBuilder.create().texOffs(356, 0).addBox(-2.2F, 0F, -0.7F, 4.4F, 35F, 1.4F), PartPose.offsetAndRotation(6.4981F, -40F, 0.1098F, 0.05F, 1.5464F, 0F));
		PartDefinition p_shred_4 = p_body.addOrReplaceChild("shred_4", CubeListBuilder.create().texOffs(60, 0).addBox(-2.2F, 0F, -0.7F, 4.4F, 40F, 1.4F), PartPose.offsetAndRotation(5.9234F, -40F, -1.853F, 0.05F, 1.9952F, 0F));
		PartDefinition p_shred_5 = p_body.addOrReplaceChild("shred_5", CubeListBuilder.create().texOffs(116, 0).addBox(-2.2F, 0F, -0.7F, 4.4F, 38F, 1.4F), PartPose.offsetAndRotation(4.1755F, -40F, -3.4487F, 0.05F, 2.444F, 0F));
		PartDefinition p_shred_6 = p_body.addOrReplaceChild("shred_6", CubeListBuilder.create().texOffs(274, 0).addBox(-2.2F, 0F, -0.7F, 4.4F, 36F, 1.4F), PartPose.offsetAndRotation(1.6006F, -40F, -4.3614F, 0.05F, 2.8928F, 0F));
		PartDefinition p_shred_7 = p_body.addOrReplaceChild("shred_7", CubeListBuilder.create().texOffs(446, 0).addBox(-2.2F, 0F, -0.7F, 4.4F, 34F, 1.4F), PartPose.offsetAndRotation(-1.2914F, -40F, -4.4103F, 0.05F, 3.3416F, 0F));
		PartDefinition p_shred_8 = p_body.addOrReplaceChild("shred_8", CubeListBuilder.create().texOffs(102, 0).addBox(-2.2F, 0F, -0.7F, 4.4F, 39F, 1.4F), PartPose.offsetAndRotation(-3.9275F, -40F, -3.5856F, 0.05F, 3.7904F, 0F));
		PartDefinition p_shred_9 = p_body.addOrReplaceChild("shred_9", CubeListBuilder.create().texOffs(212, 0).addBox(-2.2F, 0F, -0.7F, 4.4F, 37F, 1.4F), PartPose.offsetAndRotation(-5.7857F, -40F, -2.0508F, 0.05F, 4.2392F, 0F));
		PartDefinition p_shred_10 = p_body.addOrReplaceChild("shred_10", CubeListBuilder.create().texOffs(370, 0).addBox(-2.2F, 0F, -0.7F, 4.4F, 35F, 1.4F), PartPose.offsetAndRotation(-6.4981F, -40F, -0.1098F, 0.05F, 4.688F, 0F));
		PartDefinition p_shred_11 = p_body.addOrReplaceChild("shred_11", CubeListBuilder.create().texOffs(74, 0).addBox(-2.2F, 0F, -0.7F, 4.4F, 40F, 1.4F), PartPose.offsetAndRotation(-5.9234F, -40F, 1.853F, 0.05F, 5.1368F, 0F));
		PartDefinition p_shred_12 = p_body.addOrReplaceChild("shred_12", CubeListBuilder.create().texOffs(130, 0).addBox(-2.2F, 0F, -0.7F, 4.4F, 38F, 1.4F), PartPose.offsetAndRotation(-4.1755F, -40F, 3.4487F, 0.05F, 5.5856F, 0F));
		PartDefinition p_shred_13 = p_body.addOrReplaceChild("shred_13", CubeListBuilder.create().texOffs(288, 0).addBox(-2.2F, 0F, -0.7F, 4.4F, 36F, 1.4F), PartPose.offsetAndRotation(-1.6006F, -40F, 4.3614F, 0.05F, 6.0344F, 0F));
	}

	private static void part1(PartDefinition r) {
		PartDefinition p_mantle_0 = r.getChild("root").getChild("body").addOrReplaceChild("mantle_0", CubeListBuilder.create().texOffs(64, 84).addBox(-3F, 0F, -0.8F, 6F, 22F, 1.6F), PartPose.offsetAndRotation(-10.5744F, -76F, -5.2351F, 0.28F, -2.1916F, 0F));
		PartDefinition p_mantle_1 = r.getChild("root").getChild("body").addOrReplaceChild("mantle_1", CubeListBuilder.create().texOffs(388, 49).addBox(-3F, 0F, -0.8F, 6F, 27F, 1.6F), PartPose.offsetAndRotation(-12.3045F, -76F, -2.9043F, 0.31F, -1.8994F, 0F));
		PartDefinition p_mantle_2 = r.getChild("root").getChild("body").addOrReplaceChild("mantle_2", CubeListBuilder.create().texOffs(32, 84).addBox(-3F, 0F, -0.8F, 6F, 23F, 1.6F), PartPose.offsetAndRotation(-12.9914F, -76F, -0.3273F, 0.28F, -1.6072F, 0F));
		PartDefinition p_mantle_3 = r.getChild("root").getChild("body").addOrReplaceChild("mantle_3", CubeListBuilder.create().texOffs(356, 49).addBox(-3F, 0F, -0.8F, 6F, 28F, 1.6F), PartPose.offsetAndRotation(-12.5769F, -76F, 2.2775F, 0.31F, -1.315F, 0F));
		PartDefinition p_mantle_4 = r.getChild("root").getChild("body").addOrReplaceChild("mantle_4", CubeListBuilder.create().texOffs(0, 84).addBox(-3F, 0F, -0.8F, 6F, 24F, 1.6F), PartPose.offsetAndRotation(-11.096F, -76F, 4.6892F, 0.28F, -1.0227F, 0F));
		PartDefinition p_mantle_5 = r.getChild("root").getChild("body").addOrReplaceChild("mantle_5", CubeListBuilder.create().texOffs(324, 49).addBox(-3F, 0F, -0.8F, 6F, 29F, 1.6F), PartPose.offsetAndRotation(-8.6744F, -76F, 6.7034F, 0.31F, -0.7305F, 0F));
		PartDefinition p_mantle_6 = r.getChild("root").getChild("body").addOrReplaceChild("mantle_6", CubeListBuilder.create().texOffs(468, 49).addBox(-3F, 0F, -0.8F, 6F, 25F, 1.6F), PartPose.offsetAndRotation(-5.5174F, -76F, 8.1492F, 0.28F, -0.4383F, 0F));
		PartDefinition p_mantle_7 = r.getChild("root").getChild("body").addOrReplaceChild("mantle_7", CubeListBuilder.create().texOffs(308, 49).addBox(-3F, 0F, -0.8F, 6F, 30F, 1.6F), PartPose.offsetAndRotation(-1.8926F, -76F, 8.9041F, 0.31F, -0.1461F, 0F));
		PartDefinition p_mantle_8 = r.getChild("root").getChild("body").addOrReplaceChild("mantle_8", CubeListBuilder.create().texOffs(452, 49).addBox(-3F, 0F, -0.8F, 6F, 26F, 1.6F), PartPose.offsetAndRotation(1.8926F, -76F, 8.9041F, 0.28F, 0.1461F, 0F));
		PartDefinition p_mantle_9 = r.getChild("root").getChild("body").addOrReplaceChild("mantle_9", CubeListBuilder.create().texOffs(80, 84).addBox(-3F, 0F, -0.8F, 6F, 22F, 1.6F), PartPose.offsetAndRotation(5.5174F, -76F, 8.1492F, 0.31F, 0.4383F, 0F));
		PartDefinition p_mantle_10 = r.getChild("root").getChild("body").addOrReplaceChild("mantle_10", CubeListBuilder.create().texOffs(404, 49).addBox(-3F, 0F, -0.8F, 6F, 27F, 1.6F), PartPose.offsetAndRotation(8.6744F, -76F, 6.7034F, 0.28F, 0.7305F, 0F));
		PartDefinition p_mantle_11 = r.getChild("root").getChild("body").addOrReplaceChild("mantle_11", CubeListBuilder.create().texOffs(48, 84).addBox(-3F, 0F, -0.8F, 6F, 23F, 1.6F), PartPose.offsetAndRotation(11.096F, -76F, 4.6892F, 0.31F, 1.0227F, 0F));
		PartDefinition p_mantle_12 = r.getChild("root").getChild("body").addOrReplaceChild("mantle_12", CubeListBuilder.create().texOffs(372, 49).addBox(-3F, 0F, -0.8F, 6F, 28F, 1.6F), PartPose.offsetAndRotation(12.5769F, -76F, 2.2775F, 0.28F, 1.315F, 0F));
		PartDefinition p_mantle_13 = r.getChild("root").getChild("body").addOrReplaceChild("mantle_13", CubeListBuilder.create().texOffs(16, 84).addBox(-3F, 0F, -0.8F, 6F, 24F, 1.6F), PartPose.offsetAndRotation(12.9914F, -76F, -0.3273F, 0.31F, 1.6072F, 0F));
		PartDefinition p_mantle_14 = r.getChild("root").getChild("body").addOrReplaceChild("mantle_14", CubeListBuilder.create().texOffs(340, 49).addBox(-3F, 0F, -0.8F, 6F, 29F, 1.6F), PartPose.offsetAndRotation(12.3045F, -76F, -2.9043F, 0.28F, 1.8994F, 0F));
		PartDefinition p_mantle_15 = r.getChild("root").getChild("body").addOrReplaceChild("mantle_15", CubeListBuilder.create().texOffs(484, 49).addBox(-3F, 0F, -0.8F, 6F, 25F, 1.6F), PartPose.offsetAndRotation(10.5744F, -76F, -5.2351F, 0.31F, 2.1916F, 0F));
		PartDefinition p_crack_0 = r.getChild("root").getChild("body").addOrReplaceChild("crack_0", CubeListBuilder.create().texOffs(312, 110).addBox(-0.55F, 0F, -0.1F, 1.1F, 7F, 0.9F), PartPose.offsetAndRotation(0F, -68F, -6.75F, 0F, 0F, 0.7F));
		PartDefinition p_crack_1 = r.getChild("root").getChild("body").addOrReplaceChild("crack_1", CubeListBuilder.create().texOffs(250, 110).addBox(-0.55F, 0F, -0.1F, 1.1F, 8F, 0.9F), PartPose.offsetAndRotation(0F, -64F, -6.75F, 0F, 0F, -0.6F));
		PartDefinition p_crack_2 = r.getChild("root").getChild("body").addOrReplaceChild("crack_2", CubeListBuilder.create().texOffs(336, 110).addBox(-0.55F, 0F, -0.1F, 1.1F, 6F, 0.9F), PartPose.offsetAndRotation(0F, -57F, -6.75F, 0F, 0F, 0.55F));
		PartDefinition p_crack_3 = r.getChild("root").getChild("body").addOrReplaceChild("crack_3", CubeListBuilder.create().texOffs(208, 124).addBox(-0.55F, 0F, -0.1F, 1.1F, 5F, 0.9F), PartPose.offsetAndRotation(0F, -52F, -6.75F, 0F, 0F, -0.75F));
		PartDefinition p_head = r.getChild("root").getChild("body").addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 49).addBox(-8F, -19F, -8F, 16F, 19F, 16F).texOffs(422, 110).addBox(-8.6F, -20.5F, -9.6F, 17.2F, 3F, 2.6F).texOffs(96, 84).addBox(-9.2F, -16F, -9.4F, 1.8F, 18F, 6F).texOffs(112, 84).addBox(7.4F, -16F, -9.4F, 1.8F, 18F, 6F).texOffs(412, 84).addBox(-6.2F, -16.5F, -8.5F, 12.4F, 15F, 1F).texOffs(52, 110).addBox(-0.7F, -15F, -9F, 1.4F, 12F, 0.8F), PartPose.offsetAndRotation(0F, -78F, 0F, 0F, 0F, 0F));
		PartDefinition p_hoodpeak = p_head.addOrReplaceChild("hoodpeak", CubeListBuilder.create().texOffs(364, 84).addBox(-6F, -5F, -6F, 12F, 5F, 12F).texOffs(58, 110).addBox(-3.5F, -9F, -3F, 7F, 4F, 7F).texOffs(464, 110).addBox(-1.5F, -12F, -0.5F, 3F, 3F, 3F), PartPose.offsetAndRotation(0F, -19F, 1F, -0.35F, 0F, 0F));
		PartDefinition p_hoodtail_0 = p_head.addOrReplaceChild("hoodtail_0", CubeListBuilder.create().texOffs(302, 84).addBox(-2.5F, 0F, -0.7F, 5F, 16F, 1.4F), PartPose.offsetAndRotation(-5.4833F, -6F, 4.6621F, 0.22F, -0.9F, 0F));
		PartDefinition p_hoodtail_1 = p_head.addOrReplaceChild("hoodtail_1", CubeListBuilder.create().texOffs(260, 84).addBox(-2.5F, 0F, -0.7F, 5F, 19F, 1.4F), PartPose.offsetAndRotation(-3.599F, -6F, 6.4328F, 0.22F, -0.54F, 0F));
		PartDefinition p_hoodtail_2 = p_head.addOrReplaceChild("hoodtail_2", CubeListBuilder.create().texOffs(316, 84).addBox(-2.5F, 0F, -0.7F, 5F, 16F, 1.4F), PartPose.offsetAndRotation(-1.2532F, -6F, 7.3788F, 0.22F, -0.18F, 0F));
		PartDefinition p_hoodtail_3 = p_head.addOrReplaceChild("hoodtail_3", CubeListBuilder.create().texOffs(274, 84).addBox(-2.5F, 0F, -0.7F, 5F, 19F, 1.4F), PartPose.offsetAndRotation(1.2532F, -6F, 7.3788F, 0.22F, 0.18F, 0F));
		PartDefinition p_hoodtail_4 = p_head.addOrReplaceChild("hoodtail_4", CubeListBuilder.create().texOffs(330, 84).addBox(-2.5F, 0F, -0.7F, 5F, 16F, 1.4F), PartPose.offsetAndRotation(3.599F, -6F, 6.4328F, 0.22F, 0.54F, 0F));
		PartDefinition p_hoodtail_5 = p_head.addOrReplaceChild("hoodtail_5", CubeListBuilder.create().texOffs(288, 84).addBox(-2.5F, 0F, -0.7F, 5F, 19F, 1.4F), PartPose.offsetAndRotation(5.4833F, -6F, 4.6621F, 0.22F, 0.9F, 0F));
		PartDefinition p_halo = p_head.addOrReplaceChild("halo", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -14F, 11F, 0F, 0F, 0F));
		PartDefinition p_haloring_0 = p_halo.addOrReplaceChild("haloring_0", CubeListBuilder.create().texOffs(476, 110).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(15F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_haloring_1 = p_halo.addOrReplaceChild("haloring_1", CubeListBuilder.create().texOffs(484, 110).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(14.6722F, 3.1187F, 0F, 0F, 0F, 0.2094F));
		PartDefinition p_haloring_2 = p_halo.addOrReplaceChild("haloring_2", CubeListBuilder.create().texOffs(492, 110).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(13.7032F, 6.101F, 0F, 0F, 0F, 0.4189F));
		PartDefinition p_haloring_3 = p_halo.addOrReplaceChild("haloring_3", CubeListBuilder.create().texOffs(500, 110).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(12.1353F, 8.8168F, 0F, 0F, 0F, 0.6283F));
		PartDefinition p_haloring_4 = p_halo.addOrReplaceChild("haloring_4", CubeListBuilder.create().texOffs(0, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(10.037F, 11.1472F, 0F, 0F, 0F, 0.8378F));
		PartDefinition p_haloring_5 = p_halo.addOrReplaceChild("haloring_5", CubeListBuilder.create().texOffs(8, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(7.5F, 12.9904F, 0F, 0F, 0F, 1.0472F));
		PartDefinition p_haloring_6 = p_halo.addOrReplaceChild("haloring_6", CubeListBuilder.create().texOffs(16, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(4.6353F, 14.2658F, 0F, 0F, 0F, 1.2566F));
		PartDefinition p_haloring_7 = p_halo.addOrReplaceChild("haloring_7", CubeListBuilder.create().texOffs(24, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(1.5679F, 14.9178F, 0F, 0F, 0F, 1.4661F));
		PartDefinition p_haloring_8 = p_halo.addOrReplaceChild("haloring_8", CubeListBuilder.create().texOffs(32, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(-1.5679F, 14.9178F, 0F, 0F, 0F, 1.6755F));
		PartDefinition p_haloring_9 = p_halo.addOrReplaceChild("haloring_9", CubeListBuilder.create().texOffs(40, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(-4.6353F, 14.2658F, 0F, 0F, 0F, 1.885F));
		PartDefinition p_haloring_10 = p_halo.addOrReplaceChild("haloring_10", CubeListBuilder.create().texOffs(48, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(-7.5F, 12.9904F, 0F, 0F, 0F, 2.0944F));
	}

	private static void part2(PartDefinition r) {
		PartDefinition p_haloring_11 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_11", CubeListBuilder.create().texOffs(56, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(-10.037F, 11.1472F, 0F, 0F, 0F, 2.3038F));
		PartDefinition p_haloring_12 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_12", CubeListBuilder.create().texOffs(64, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(-12.1353F, 8.8168F, 0F, 0F, 0F, 2.5133F));
		PartDefinition p_haloring_13 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_13", CubeListBuilder.create().texOffs(72, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(-13.7032F, 6.101F, 0F, 0F, 0F, 2.7227F));
		PartDefinition p_haloring_14 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_14", CubeListBuilder.create().texOffs(80, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(-14.6722F, 3.1187F, 0F, 0F, 0F, 2.9322F));
		PartDefinition p_haloring_15 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_15", CubeListBuilder.create().texOffs(88, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(-15F, 0F, 0F, 0F, 0F, 3.1416F));
		PartDefinition p_haloring_16 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_16", CubeListBuilder.create().texOffs(96, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(-14.6722F, -3.1187F, 0F, 0F, 0F, 3.351F));
		PartDefinition p_haloring_17 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_17", CubeListBuilder.create().texOffs(104, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(-13.7032F, -6.101F, 0F, 0F, 0F, 3.5605F));
		PartDefinition p_haloring_18 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_18", CubeListBuilder.create().texOffs(112, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(-12.1353F, -8.8168F, 0F, 0F, 0F, 3.7699F));
		PartDefinition p_haloring_19 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_19", CubeListBuilder.create().texOffs(120, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(-10.037F, -11.1472F, 0F, 0F, 0F, 3.9794F));
		PartDefinition p_haloring_20 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_20", CubeListBuilder.create().texOffs(128, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(-7.5F, -12.9904F, 0F, 0F, 0F, 4.1888F));
		PartDefinition p_haloring_21 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_21", CubeListBuilder.create().texOffs(136, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(-4.6353F, -14.2658F, 0F, 0F, 0F, 4.3982F));
		PartDefinition p_haloring_22 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_22", CubeListBuilder.create().texOffs(144, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(-1.5679F, -14.9178F, 0F, 0F, 0F, 4.6077F));
		PartDefinition p_haloring_23 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_23", CubeListBuilder.create().texOffs(152, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(1.5679F, -14.9178F, 0F, 0F, 0F, 4.8171F));
		PartDefinition p_haloring_24 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_24", CubeListBuilder.create().texOffs(160, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(4.6353F, -14.2658F, 0F, 0F, 0F, 5.0265F));
		PartDefinition p_haloring_25 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_25", CubeListBuilder.create().texOffs(168, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(7.5F, -12.9904F, 0F, 0F, 0F, 5.236F));
		PartDefinition p_haloring_26 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_26", CubeListBuilder.create().texOffs(176, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(10.037F, -11.1472F, 0F, 0F, 0F, 5.4454F));
		PartDefinition p_haloring_27 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_27", CubeListBuilder.create().texOffs(184, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(12.1353F, -8.8168F, 0F, 0F, 0F, 5.6549F));
		PartDefinition p_haloring_28 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_28", CubeListBuilder.create().texOffs(192, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(13.7032F, -6.101F, 0F, 0F, 0F, 5.8643F));
		PartDefinition p_haloring_29 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloring_29", CubeListBuilder.create().texOffs(200, 124).addBox(-0.9F, -1.7F, -0.9F, 1.8F, 3.4F, 1.8F), PartPose.offsetAndRotation(14.6722F, -3.1187F, 0F, 0F, 0F, 6.0737F));
		PartDefinition p_haloinner_0 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_0", CubeListBuilder.create().texOffs(486, 124).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(10.8765F, 1.6438F, 0F, 0F, 0F, 0.15F));
		PartDefinition p_haloinner_1 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_1", CubeListBuilder.create().texOffs(490, 124).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(9.9728F, 4.6415F, 0F, 0F, 0F, 0.4356F));
		PartDefinition p_haloinner_2 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_2", CubeListBuilder.create().texOffs(494, 124).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(8.2612F, 7.2631F, 0F, 0F, 0F, 0.7212F));
		PartDefinition p_haloinner_3 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_3", CubeListBuilder.create().texOffs(498, 124).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(5.8803F, 9.2964F, 0F, 0F, 0F, 1.0068F));
		PartDefinition p_haloinner_4 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_4", CubeListBuilder.create().texOffs(502, 124).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(3.023F, 10.5765F, 0F, 0F, 0F, 1.2924F));
		PartDefinition p_haloinner_5 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_5", CubeListBuilder.create().texOffs(506, 124).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(-0.0792F, 10.9997F, 0F, 0F, 0F, 1.578F));
		PartDefinition p_haloinner_6 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_6", CubeListBuilder.create().texOffs(0, 130).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(-3.175F, 10.5318F, 0F, 0F, 0F, 1.8636F));
		PartDefinition p_haloinner_7 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_7", CubeListBuilder.create().texOffs(4, 130).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(-6.0135F, 9.2107F, 0F, 0F, 0F, 2.1492F));
		PartDefinition p_haloinner_8 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_8", CubeListBuilder.create().texOffs(8, 130).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(-8.3649F, 7.1434F, 0F, 0F, 0F, 2.4348F));
		PartDefinition p_haloinner_9 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_9", CubeListBuilder.create().texOffs(12, 130).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(-10.0386F, 4.4974F, 0F, 0F, 0F, 2.7204F));
		PartDefinition p_haloinner_10 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_10", CubeListBuilder.create().texOffs(16, 130).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(-10.899F, 1.487F, 0F, 0F, 0F, 3.006F));
		PartDefinition p_haloinner_11 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_11", CubeListBuilder.create().texOffs(20, 130).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(-10.8765F, -1.6438F, 0F, 0F, 0F, 3.2916F));
		PartDefinition p_haloinner_12 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_12", CubeListBuilder.create().texOffs(24, 130).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(-9.9728F, -4.6415F, 0F, 0F, 0F, 3.5772F));
		PartDefinition p_haloinner_13 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_13", CubeListBuilder.create().texOffs(28, 130).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(-8.2612F, -7.2631F, 0F, 0F, 0F, 3.8628F));
		PartDefinition p_haloinner_14 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_14", CubeListBuilder.create().texOffs(32, 130).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(-5.8803F, -9.2964F, 0F, 0F, 0F, 4.1484F));
		PartDefinition p_haloinner_15 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_15", CubeListBuilder.create().texOffs(36, 130).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(-3.023F, -10.5765F, 0F, 0F, 0F, 4.434F));
		PartDefinition p_haloinner_16 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_16", CubeListBuilder.create().texOffs(40, 130).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(0.0792F, -10.9997F, 0F, 0F, 0F, 4.7196F));
		PartDefinition p_haloinner_17 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_17", CubeListBuilder.create().texOffs(44, 130).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(3.175F, -10.5318F, 0F, 0F, 0F, 5.0052F));
		PartDefinition p_haloinner_18 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_18", CubeListBuilder.create().texOffs(48, 130).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(6.0135F, -9.2107F, 0F, 0F, 0F, 5.2908F));
		PartDefinition p_haloinner_19 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_19", CubeListBuilder.create().texOffs(52, 130).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(8.3649F, -7.1434F, 0F, 0F, 0F, 5.5764F));
		PartDefinition p_haloinner_20 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_20", CubeListBuilder.create().texOffs(56, 130).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(10.0386F, -4.4974F, 0F, 0F, 0F, 5.862F));
	}

	private static void part3(PartDefinition r) {
		PartDefinition p_haloinner_21 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("haloinner_21", CubeListBuilder.create().texOffs(60, 130).addBox(-0.5F, -1.7F, -0.5F, 1F, 3.4F, 1F), PartPose.offsetAndRotation(10.899F, -1.487F, 0F, 0F, 0F, 6.1476F));
		PartDefinition p_spike_0 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("spike_0", CubeListBuilder.create().texOffs(178, 110).addBox(-0.8F, -9F, -0.8F, 1.6F, 9F, 1.6F).texOffs(76, 130).addBox(-0.45F, -11F, -0.45F, 0.9F, 2F, 0.9F), PartPose.offsetAndRotation(15F, 0F, 0F, 0F, 0F, 1.5708F));
		PartDefinition p_spike_1 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("spike_1", CubeListBuilder.create().texOffs(256, 110).addBox(-0.8F, -6F, -0.8F, 1.6F, 6F, 1.6F).texOffs(80, 130).addBox(-0.45F, -8F, -0.45F, 0.9F, 2F, 0.9F), PartPose.offsetAndRotation(13.5145F, 6.5083F, 0F, 0F, 0F, 2.0196F));
		PartDefinition p_spike_2 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("spike_2", CubeListBuilder.create().texOffs(186, 110).addBox(-0.8F, -9F, -0.8F, 1.6F, 9F, 1.6F).texOffs(84, 130).addBox(-0.45F, -11F, -0.45F, 0.9F, 2F, 0.9F), PartPose.offsetAndRotation(9.3523F, 11.7275F, 0F, 0F, 0F, 2.4684F));
		PartDefinition p_spike_3 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("spike_3", CubeListBuilder.create().texOffs(264, 110).addBox(-0.8F, -6F, -0.8F, 1.6F, 6F, 1.6F).texOffs(88, 130).addBox(-0.45F, -8F, -0.45F, 0.9F, 2F, 0.9F), PartPose.offsetAndRotation(3.3378F, 14.6239F, 0F, 0F, 0F, 2.9172F));
		PartDefinition p_spike_4 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("spike_4", CubeListBuilder.create().texOffs(194, 110).addBox(-0.8F, -9F, -0.8F, 1.6F, 9F, 1.6F).texOffs(92, 130).addBox(-0.45F, -11F, -0.45F, 0.9F, 2F, 0.9F), PartPose.offsetAndRotation(-3.3378F, 14.6239F, 0F, 0F, 0F, 3.366F));
		PartDefinition p_spike_5 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("spike_5", CubeListBuilder.create().texOffs(272, 110).addBox(-0.8F, -6F, -0.8F, 1.6F, 6F, 1.6F).texOffs(96, 130).addBox(-0.45F, -8F, -0.45F, 0.9F, 2F, 0.9F), PartPose.offsetAndRotation(-9.3523F, 11.7275F, 0F, 0F, 0F, 3.8148F));
		PartDefinition p_spike_6 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("spike_6", CubeListBuilder.create().texOffs(202, 110).addBox(-0.8F, -9F, -0.8F, 1.6F, 9F, 1.6F).texOffs(100, 130).addBox(-0.45F, -11F, -0.45F, 0.9F, 2F, 0.9F), PartPose.offsetAndRotation(-13.5145F, 6.5083F, 0F, 0F, 0F, 4.2636F));
		PartDefinition p_spike_7 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("spike_7", CubeListBuilder.create().texOffs(280, 110).addBox(-0.8F, -6F, -0.8F, 1.6F, 6F, 1.6F).texOffs(104, 130).addBox(-0.45F, -8F, -0.45F, 0.9F, 2F, 0.9F), PartPose.offsetAndRotation(-15F, 0F, 0F, 0F, 0F, 4.7124F));
		PartDefinition p_spike_8 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("spike_8", CubeListBuilder.create().texOffs(210, 110).addBox(-0.8F, -9F, -0.8F, 1.6F, 9F, 1.6F).texOffs(108, 130).addBox(-0.45F, -11F, -0.45F, 0.9F, 2F, 0.9F), PartPose.offsetAndRotation(-13.5145F, -6.5083F, 0F, 0F, 0F, 5.1612F));
		PartDefinition p_spike_9 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("spike_9", CubeListBuilder.create().texOffs(288, 110).addBox(-0.8F, -6F, -0.8F, 1.6F, 6F, 1.6F).texOffs(112, 130).addBox(-0.45F, -8F, -0.45F, 0.9F, 2F, 0.9F), PartPose.offsetAndRotation(-9.3523F, -11.7275F, 0F, 0F, 0F, 5.61F));
		PartDefinition p_spike_10 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("spike_10", CubeListBuilder.create().texOffs(218, 110).addBox(-0.8F, -9F, -0.8F, 1.6F, 9F, 1.6F).texOffs(116, 130).addBox(-0.45F, -11F, -0.45F, 0.9F, 2F, 0.9F), PartPose.offsetAndRotation(-3.3378F, -14.6239F, 0F, 0F, 0F, 6.0588F));
		PartDefinition p_spike_11 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("spike_11", CubeListBuilder.create().texOffs(296, 110).addBox(-0.8F, -6F, -0.8F, 1.6F, 6F, 1.6F).texOffs(120, 130).addBox(-0.45F, -8F, -0.45F, 0.9F, 2F, 0.9F), PartPose.offsetAndRotation(3.3378F, -14.6239F, 0F, 0F, 0F, 6.5076F));
		PartDefinition p_spike_12 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("spike_12", CubeListBuilder.create().texOffs(226, 110).addBox(-0.8F, -9F, -0.8F, 1.6F, 9F, 1.6F).texOffs(124, 130).addBox(-0.45F, -11F, -0.45F, 0.9F, 2F, 0.9F), PartPose.offsetAndRotation(9.3523F, -11.7275F, 0F, 0F, 0F, 6.9564F));
		PartDefinition p_spike_13 = r.getChild("root").getChild("body").getChild("head").getChild("halo").addOrReplaceChild("spike_13", CubeListBuilder.create().texOffs(304, 110).addBox(-0.8F, -6F, -0.8F, 1.6F, 6F, 1.6F).texOffs(128, 130).addBox(-0.45F, -8F, -0.45F, 0.9F, 2F, 0.9F), PartPose.offsetAndRotation(13.5145F, -6.5083F, 0F, 0F, 0F, 7.4052F));
		PartDefinition p_arm_l = r.getChild("root").getChild("body").addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(220, 49).addBox(-2.5F, 0F, -2.5F, 5F, 27F, 5F).texOffs(128, 84).addBox(-3.6F, -1F, -3.6F, 7.2F, 15F, 7.2F), PartPose.offsetAndRotation(12F, -73F, 0F, -0.22F, 0F, -0.34F));
		PartDefinition p_sleeve_l0 = p_arm_l.addOrReplaceChild("sleeve_l0", CubeListBuilder.create().texOffs(32, 110).addBox(-1.8F, 0F, -0.5F, 3.6F, 12F, 1F), PartPose.offsetAndRotation(3.1316F, 12F, 1.324F, 0.15F, -0.4F, 0F));
		PartDefinition p_sleeve_l1 = p_arm_l.addOrReplaceChild("sleeve_l1", CubeListBuilder.create().texOffs(344, 84).addBox(-1.8F, 0F, -0.5F, 3.6F, 17F, 1F), PartPose.offsetAndRotation(-1.324F, 12F, 3.1316F, 0.15F, -1.9708F, 0F));
		PartDefinition p_sleeve_l2 = p_arm_l.addOrReplaceChild("sleeve_l2", CubeListBuilder.create().texOffs(440, 84).addBox(-1.8F, 0F, -0.5F, 3.6F, 15F, 1F), PartPose.offsetAndRotation(-3.1316F, 12F, -1.324F, 0.15F, -3.5416F, 0F));
		PartDefinition p_sleeve_l3 = p_arm_l.addOrReplaceChild("sleeve_l3", CubeListBuilder.create().texOffs(492, 84).addBox(-1.8F, 0F, -0.5F, 3.6F, 13F, 1F), PartPose.offsetAndRotation(1.324F, 12F, -3.1316F, 0.15F, -5.1124F, 0F));
		PartDefinition p_fore_l = p_arm_l.addOrReplaceChild("fore_l", CubeListBuilder.create().texOffs(420, 49).addBox(-2F, 0F, -2F, 4F, 25F, 4F).texOffs(86, 110).addBox(-2.7F, 15F, -2.7F, 5.4F, 4.5F, 5.4F), PartPose.offsetAndRotation(0F, 26F, 0F, -0.42F, 0F, 0.1F));
		PartDefinition p_hand_l = p_fore_l.addOrReplaceChild("hand_l", CubeListBuilder.create().texOffs(134, 110).addBox(-3F, 0F, -2.2F, 6F, 6F, 4.4F), PartPose.offsetAndRotation(0F, 25F, 0F, 0F, 0F, 0F));
		PartDefinition p_finger_l0 = p_hand_l.addOrReplaceChild("finger_l0", CubeListBuilder.create().texOffs(0, 110).addBox(-0.7F, 0F, -0.7F, 1.4F, 12F, 1.4F), PartPose.offsetAndRotation(-2.2F, 5.5F, -0.6F, -0.3F, 0F, -0.18F));
		PartDefinition p_tip_l0 = p_finger_l0.addOrReplaceChild("tip_l0", CubeListBuilder.create().texOffs(390, 110).addBox(-0.5F, 0F, -0.5F, 1F, 6F, 1F), PartPose.offsetAndRotation(0F, 12F, 0F, -0.55F, 0F, 0F));
		PartDefinition p_finger_l1 = p_hand_l.addOrReplaceChild("finger_l1", CubeListBuilder.create().texOffs(460, 84).addBox(-0.7F, 0F, -0.7F, 1.4F, 14F, 1.4F), PartPose.offsetAndRotation(-0.75F, 5.5F, -0.6F, -0.3F, 0F, -0.06F));
		PartDefinition p_tip_l1 = p_finger_l1.addOrReplaceChild("tip_l1", CubeListBuilder.create().texOffs(394, 110).addBox(-0.5F, 0F, -0.5F, 1F, 6F, 1F), PartPose.offsetAndRotation(0F, 14F, 0F, -0.55F, 0F, 0F));
		PartDefinition p_finger_l2 = p_hand_l.addOrReplaceChild("finger_l2", CubeListBuilder.create().texOffs(468, 84).addBox(-0.7F, 0F, -0.7F, 1.4F, 14F, 1.4F), PartPose.offsetAndRotation(0.75F, 5.5F, -0.6F, -0.3F, 0F, 0.06F));
		PartDefinition p_tip_l2 = p_finger_l2.addOrReplaceChild("tip_l2", CubeListBuilder.create().texOffs(398, 110).addBox(-0.5F, 0F, -0.5F, 1F, 6F, 1F), PartPose.offsetAndRotation(0F, 14F, 0F, -0.55F, 0F, 0F));
		PartDefinition p_finger_l3 = p_hand_l.addOrReplaceChild("finger_l3", CubeListBuilder.create().texOffs(8, 110).addBox(-0.7F, 0F, -0.7F, 1.4F, 12F, 1.4F), PartPose.offsetAndRotation(2.2F, 5.5F, -0.6F, -0.3F, 0F, 0.18F));
		PartDefinition p_tip_l3 = p_finger_l3.addOrReplaceChild("tip_l3", CubeListBuilder.create().texOffs(402, 110).addBox(-0.5F, 0F, -0.5F, 1F, 6F, 1F), PartPose.offsetAndRotation(0F, 12F, 0F, -0.55F, 0F, 0F));
		PartDefinition p_thumb_l = p_hand_l.addOrReplaceChild("thumb_l", CubeListBuilder.create().texOffs(234, 110).addBox(-0.65F, 0F, -0.65F, 1.3F, 9F, 1.3F), PartPose.offsetAndRotation(-3F, 3.5F, -1.6F, -0.6F, 0F, -0.7F));
		PartDefinition p_thumbtip_l = p_thumb_l.addOrReplaceChild("thumbtip_l", CubeListBuilder.create().texOffs(346, 124).addBox(-0.45F, 0F, -0.45F, 0.9F, 5F, 0.9F), PartPose.offsetAndRotation(0F, 9F, 0F, -0.5F, 0F, 0F));
		PartDefinition p_wristchain_l = p_fore_l.addOrReplaceChild("wristchain_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.7F, 18F, 0F, 0.6123F, 0F, 0.2976F));
		PartDefinition p_wristlink_l0 = p_wristchain_l.addOrReplaceChild("wristlink_l0", CubeListBuilder.create().texOffs(354, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.8F, 0.63F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_l1 = p_wristchain_l.addOrReplaceChild("wristlink_l1", CubeListBuilder.create().texOffs(214, 124).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.8F, 1.8F), PartPose.offsetAndRotation(0F, 3.2F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_l2 = p_wristchain_l.addOrReplaceChild("wristlink_l2", CubeListBuilder.create().texOffs(360, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.8F, 0.63F), PartPose.offsetAndRotation(0F, 6.4F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_l3 = p_wristchain_l.addOrReplaceChild("wristlink_l3", CubeListBuilder.create().texOffs(220, 124).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.8F, 1.8F), PartPose.offsetAndRotation(0F, 9.6F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_l4 = p_wristchain_l.addOrReplaceChild("wristlink_l4", CubeListBuilder.create().texOffs(366, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.8F, 0.63F), PartPose.offsetAndRotation(0F, 12.8F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_l5 = p_wristchain_l.addOrReplaceChild("wristlink_l5", CubeListBuilder.create().texOffs(226, 124).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.8F, 1.8F), PartPose.offsetAndRotation(0F, 16F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_l6 = p_wristchain_l.addOrReplaceChild("wristlink_l6", CubeListBuilder.create().texOffs(372, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.8F, 0.63F), PartPose.offsetAndRotation(0F, 19.2F, 0F, 0F, 0F, 0F));
	}

	private static void part4(PartDefinition r) {
		PartDefinition p_wristlink_l7 = r.getChild("root").getChild("body").getChild("arm_l").getChild("fore_l").getChild("wristchain_l").addOrReplaceChild("wristlink_l7", CubeListBuilder.create().texOffs(232, 124).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.8F, 1.8F), PartPose.offsetAndRotation(0F, 22.4F, 0F, 0F, 0F, 0F));
		PartDefinition p_arm_r = r.getChild("root").getChild("body").addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(240, 49).addBox(-2.5F, 0F, -2.5F, 5F, 27F, 5F).texOffs(160, 84).addBox(-3.6F, -1F, -3.6F, 7.2F, 15F, 7.2F), PartPose.offsetAndRotation(-12F, -73F, 0F, -0.22F, 0F, 0.34F));
		PartDefinition p_sleeve_r0 = p_arm_r.addOrReplaceChild("sleeve_r0", CubeListBuilder.create().texOffs(42, 110).addBox(-1.8F, 0F, -0.5F, 3.6F, 12F, 1F), PartPose.offsetAndRotation(3.1316F, 12F, 1.324F, 0.15F, -0.4F, 0F));
		PartDefinition p_sleeve_r1 = p_arm_r.addOrReplaceChild("sleeve_r1", CubeListBuilder.create().texOffs(354, 84).addBox(-1.8F, 0F, -0.5F, 3.6F, 17F, 1F), PartPose.offsetAndRotation(-1.324F, 12F, 3.1316F, 0.15F, -1.9708F, 0F));
		PartDefinition p_sleeve_r2 = p_arm_r.addOrReplaceChild("sleeve_r2", CubeListBuilder.create().texOffs(450, 84).addBox(-1.8F, 0F, -0.5F, 3.6F, 15F, 1F), PartPose.offsetAndRotation(-3.1316F, 12F, -1.324F, 0.15F, -3.5416F, 0F));
		PartDefinition p_sleeve_r3 = p_arm_r.addOrReplaceChild("sleeve_r3", CubeListBuilder.create().texOffs(502, 84).addBox(-1.8F, 0F, -0.5F, 3.6F, 13F, 1F), PartPose.offsetAndRotation(1.324F, 12F, -3.1316F, 0.15F, -5.1124F, 0F));
		PartDefinition p_fore_r = p_arm_r.addOrReplaceChild("fore_r", CubeListBuilder.create().texOffs(436, 49).addBox(-2F, 0F, -2F, 4F, 25F, 4F).texOffs(110, 110).addBox(-2.7F, 15F, -2.7F, 5.4F, 4.5F, 5.4F), PartPose.offsetAndRotation(0F, 26F, 0F, -0.42F, 0F, -0.1F));
		PartDefinition p_hand_r = p_fore_r.addOrReplaceChild("hand_r", CubeListBuilder.create().texOffs(156, 110).addBox(-3F, 0F, -2.2F, 6F, 6F, 4.4F), PartPose.offsetAndRotation(0F, 25F, 0F, 0F, 0F, 0F));
		PartDefinition p_finger_r0 = p_hand_r.addOrReplaceChild("finger_r0", CubeListBuilder.create().texOffs(16, 110).addBox(-0.7F, 0F, -0.7F, 1.4F, 12F, 1.4F), PartPose.offsetAndRotation(2.2F, 5.5F, -0.6F, -0.3F, 0F, 0.18F));
		PartDefinition p_tip_r0 = p_finger_r0.addOrReplaceChild("tip_r0", CubeListBuilder.create().texOffs(406, 110).addBox(-0.5F, 0F, -0.5F, 1F, 6F, 1F), PartPose.offsetAndRotation(0F, 12F, 0F, -0.55F, 0F, 0F));
		PartDefinition p_finger_r1 = p_hand_r.addOrReplaceChild("finger_r1", CubeListBuilder.create().texOffs(476, 84).addBox(-0.7F, 0F, -0.7F, 1.4F, 14F, 1.4F), PartPose.offsetAndRotation(0.75F, 5.5F, -0.6F, -0.3F, 0F, 0.06F));
		PartDefinition p_tip_r1 = p_finger_r1.addOrReplaceChild("tip_r1", CubeListBuilder.create().texOffs(410, 110).addBox(-0.5F, 0F, -0.5F, 1F, 6F, 1F), PartPose.offsetAndRotation(0F, 14F, 0F, -0.55F, 0F, 0F));
		PartDefinition p_finger_r2 = p_hand_r.addOrReplaceChild("finger_r2", CubeListBuilder.create().texOffs(484, 84).addBox(-0.7F, 0F, -0.7F, 1.4F, 14F, 1.4F), PartPose.offsetAndRotation(-0.75F, 5.5F, -0.6F, -0.3F, 0F, -0.06F));
		PartDefinition p_tip_r2 = p_finger_r2.addOrReplaceChild("tip_r2", CubeListBuilder.create().texOffs(414, 110).addBox(-0.5F, 0F, -0.5F, 1F, 6F, 1F), PartPose.offsetAndRotation(0F, 14F, 0F, -0.55F, 0F, 0F));
		PartDefinition p_finger_r3 = p_hand_r.addOrReplaceChild("finger_r3", CubeListBuilder.create().texOffs(24, 110).addBox(-0.7F, 0F, -0.7F, 1.4F, 12F, 1.4F), PartPose.offsetAndRotation(-2.2F, 5.5F, -0.6F, -0.3F, 0F, -0.18F));
		PartDefinition p_tip_r3 = p_finger_r3.addOrReplaceChild("tip_r3", CubeListBuilder.create().texOffs(418, 110).addBox(-0.5F, 0F, -0.5F, 1F, 6F, 1F), PartPose.offsetAndRotation(0F, 12F, 0F, -0.55F, 0F, 0F));
		PartDefinition p_thumb_r = p_hand_r.addOrReplaceChild("thumb_r", CubeListBuilder.create().texOffs(242, 110).addBox(-0.65F, 0F, -0.65F, 1.3F, 9F, 1.3F), PartPose.offsetAndRotation(3F, 3.5F, -1.6F, -0.6F, 0F, 0.7F));
		PartDefinition p_thumbtip_r = p_thumb_r.addOrReplaceChild("thumbtip_r", CubeListBuilder.create().texOffs(350, 124).addBox(-0.45F, 0F, -0.45F, 0.9F, 5F, 0.9F), PartPose.offsetAndRotation(0F, 9F, 0F, -0.5F, 0F, 0F));
		PartDefinition p_wristchain_r = p_fore_r.addOrReplaceChild("wristchain_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.7F, 18F, 0F, 0.6123F, 0F, -0.2976F));
		PartDefinition p_wristlink_r0 = p_wristchain_r.addOrReplaceChild("wristlink_r0", CubeListBuilder.create().texOffs(378, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.8F, 0.63F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_r1 = p_wristchain_r.addOrReplaceChild("wristlink_r1", CubeListBuilder.create().texOffs(238, 124).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.8F, 1.8F), PartPose.offsetAndRotation(0F, 3.2F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_r2 = p_wristchain_r.addOrReplaceChild("wristlink_r2", CubeListBuilder.create().texOffs(384, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.8F, 0.63F), PartPose.offsetAndRotation(0F, 6.4F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_r3 = p_wristchain_r.addOrReplaceChild("wristlink_r3", CubeListBuilder.create().texOffs(244, 124).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.8F, 1.8F), PartPose.offsetAndRotation(0F, 9.6F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_r4 = p_wristchain_r.addOrReplaceChild("wristlink_r4", CubeListBuilder.create().texOffs(390, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.8F, 0.63F), PartPose.offsetAndRotation(0F, 12.8F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_r5 = p_wristchain_r.addOrReplaceChild("wristlink_r5", CubeListBuilder.create().texOffs(250, 124).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.8F, 1.8F), PartPose.offsetAndRotation(0F, 16F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_r6 = p_wristchain_r.addOrReplaceChild("wristlink_r6", CubeListBuilder.create().texOffs(396, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.8F, 0.63F), PartPose.offsetAndRotation(0F, 19.2F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_r7 = p_wristchain_r.addOrReplaceChild("wristlink_r7", CubeListBuilder.create().texOffs(256, 124).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.8F, 1.8F), PartPose.offsetAndRotation(0F, 22.4F, 0F, 0F, 0F, 0F));
		PartDefinition p_wrap_0 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_0", CubeListBuilder.create().texOffs(402, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.6071F, 0.63F), PartPose.offsetAndRotation(8.5F, -71F, -6.6F, -0.0266F, 0F, 0.6462F));
		PartDefinition p_wrap_1 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_1", CubeListBuilder.create().texOffs(262, 124).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.6071F, 1.8F), PartPose.offsetAndRotation(6.69F, -68.6F, -6.68F, -0.0266F, 0F, 0.6462F));
		PartDefinition p_wrap_2 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_2", CubeListBuilder.create().texOffs(408, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.6071F, 0.63F), PartPose.offsetAndRotation(4.88F, -66.2F, -6.76F, -0.0266F, 0F, 0.6462F));
		PartDefinition p_wrap_3 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_3", CubeListBuilder.create().texOffs(268, 124).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.6071F, 1.8F), PartPose.offsetAndRotation(3.07F, -63.8F, -6.84F, -0.0266F, 0F, 0.6462F));
		PartDefinition p_wrap_4 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_4", CubeListBuilder.create().texOffs(414, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.6071F, 0.63F), PartPose.offsetAndRotation(1.26F, -61.4F, -6.92F, -0.0266F, 0F, 0.6462F));
		PartDefinition p_wrap_5 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_5", CubeListBuilder.create().texOffs(274, 124).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.6071F, 1.8F), PartPose.offsetAndRotation(-0.55F, -59F, -7F, -0.0266F, 0F, 0.6462F));
		PartDefinition p_wrap_6 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_6", CubeListBuilder.create().texOffs(420, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.6071F, 0.63F), PartPose.offsetAndRotation(-2.36F, -56.6F, -7.08F, -0.0266F, 0F, 0.6462F));
		PartDefinition p_wrap_7 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_7", CubeListBuilder.create().texOffs(280, 124).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.6071F, 1.8F), PartPose.offsetAndRotation(-4.17F, -54.2F, -7.16F, -0.0266F, 0F, 0.6462F));
		PartDefinition p_wrap_8 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_8", CubeListBuilder.create().texOffs(426, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.6071F, 0.63F), PartPose.offsetAndRotation(-5.98F, -51.8F, -7.24F, -0.0266F, 0F, 0.6462F));
		PartDefinition p_wrap_9 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_9", CubeListBuilder.create().texOffs(286, 124).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.6071F, 1.8F), PartPose.offsetAndRotation(-7.79F, -49.4F, -7.32F, -0.0266F, 0F, 0.6462F));
		PartDefinition p_wrap_10 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_10", CubeListBuilder.create().texOffs(292, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 4.4652F, 0.63F), PartPose.offsetAndRotation(-9.6F, -47F, -7.4F, 1.2773F, 0F, 0.4636F));
		PartDefinition p_wrap_11 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_11", CubeListBuilder.create().texOffs(342, 110).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 4.4652F, 1.8F), PartPose.offsetAndRotation(-10.1F, -46F, -3.7F, 1.2773F, 0F, 0.4636F));
		PartDefinition p_wrap_12 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_12", CubeListBuilder.create().texOffs(298, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 4.3269F, 0.63F), PartPose.offsetAndRotation(-10.6F, -45F, 0F, 1.22F, 0F, -2.4669F));
	}

	private static void part5(PartDefinition r) {
		PartDefinition p_wrap_13 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_13", CubeListBuilder.create().texOffs(348, 110).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 4.3269F, 1.8F), PartPose.offsetAndRotation(-9.8F, -46F, 3.5F, 1.22F, 0F, -2.4669F));
		PartDefinition p_wrap_14 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_14", CubeListBuilder.create().texOffs(432, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.873F, 0.63F), PartPose.offsetAndRotation(-9F, -47F, 7F, -0.0136F, 0F, -2.4669F));
		PartDefinition p_wrap_15 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_15", CubeListBuilder.create().texOffs(304, 124).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.873F, 1.8F), PartPose.offsetAndRotation(-6.9556F, -49.5556F, 6.9556F, -0.0136F, 0F, -2.4669F));
		PartDefinition p_wrap_16 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_16", CubeListBuilder.create().texOffs(438, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.873F, 0.63F), PartPose.offsetAndRotation(-4.9111F, -52.1111F, 6.9111F, -0.0136F, 0F, -2.4669F));
		PartDefinition p_wrap_17 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_17", CubeListBuilder.create().texOffs(310, 124).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.873F, 1.8F), PartPose.offsetAndRotation(-2.8667F, -54.6667F, 6.8667F, -0.0136F, 0F, -2.4669F));
		PartDefinition p_wrap_18 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_18", CubeListBuilder.create().texOffs(444, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.873F, 0.63F), PartPose.offsetAndRotation(-0.8222F, -57.2222F, 6.8222F, -0.0136F, 0F, -2.4669F));
		PartDefinition p_wrap_19 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_19", CubeListBuilder.create().texOffs(316, 124).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.873F, 1.8F), PartPose.offsetAndRotation(1.2222F, -59.7778F, 6.7778F, -0.0136F, 0F, -2.4669F));
		PartDefinition p_wrap_20 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_20", CubeListBuilder.create().texOffs(450, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.873F, 0.63F), PartPose.offsetAndRotation(3.2667F, -62.3333F, 6.7333F, -0.0136F, 0F, -2.4669F));
		PartDefinition p_wrap_21 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_21", CubeListBuilder.create().texOffs(322, 124).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.873F, 1.8F), PartPose.offsetAndRotation(5.3111F, -64.8889F, 6.6889F, -0.0136F, 0F, -2.4669F));
		PartDefinition p_wrap_22 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_22", CubeListBuilder.create().texOffs(456, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.873F, 0.63F), PartPose.offsetAndRotation(7.3556F, -67.4444F, 6.6444F, -0.0136F, 0F, -2.4669F));
		PartDefinition p_wrap_23 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_23", CubeListBuilder.create().texOffs(354, 110).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 4.054F, 1.8F), PartPose.offsetAndRotation(9.4F, -70F, 6.6F, -1.2711F, 0F, -2.9442F));
		PartDefinition p_wrap_24 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_24", CubeListBuilder.create().texOffs(328, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 4.054F, 0.63F), PartPose.offsetAndRotation(9.6F, -71F, 3.3F, -1.2711F, 0F, -2.9442F));
		PartDefinition p_wrap_25 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_25", CubeListBuilder.create().texOffs(360, 110).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 4.0004F, 1.8F), PartPose.offsetAndRotation(9.8F, -72F, 0F, -1.3272F, 0F, 0.9151F));
		PartDefinition p_wrap_26 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_26", CubeListBuilder.create().texOffs(334, 124).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 4.0004F, 0.63F), PartPose.offsetAndRotation(9.15F, -71.5F, -3.3F, -1.3272F, 0F, 0.9151F));
		PartDefinition p_sag0_0 = r.getChild("root").getChild("body").addOrReplaceChild("sag0_0", CubeListBuilder.create().texOffs(366, 110).addBox(-0.85F, -0.3F, -0.2975F, 1.7F, 5.5731F, 0.595F), PartPose.offsetAndRotation(-8.5F, -40F, -7.6F, 0F, 0F, -0.3367F));
		PartDefinition p_sag0_1 = r.getChild("root").getChild("body").addOrReplaceChild("sag0_1", CubeListBuilder.create().texOffs(372, 110).addBox(-0.2975F, -0.3F, -0.85F, 0.595F, 4.2189F, 1.7F), PartPose.offsetAndRotation(-6.8571F, -35.3061F, -7.6F, 0F, 0F, -0.4712F));
		PartDefinition p_sag0_2 = r.getChild("root").getChild("body").addOrReplaceChild("sag0_2", CubeListBuilder.create().texOffs(462, 124).addBox(-0.85F, -0.3F, -0.2975F, 1.7F, 3.004F, 0.595F), PartPose.offsetAndRotation(-5.2143F, -32.0816F, -7.6F, 0F, 0F, -0.7524F));
		PartDefinition p_sag0_3 = r.getChild("root").getChild("body").addOrReplaceChild("sag0_3", CubeListBuilder.create().texOffs(468, 124).addBox(-0.2975F, -0.3F, -0.85F, 0.595F, 2.2675F, 1.7F), PartPose.offsetAndRotation(-3.5714F, -30.3265F, -7.6F, 0F, 0F, -1.3986F));
		PartDefinition p_sag0_4 = r.getChild("root").getChild("body").addOrReplaceChild("sag0_4", CubeListBuilder.create().texOffs(64, 130).addBox(-0.85F, -0.3F, -0.2975F, 1.7F, 2.6249F, 0.595F), PartPose.offsetAndRotation(-1.9286F, -30.0408F, -7.6F, 0F, 0F, -2.1951F));
		PartDefinition p_sag0_5 = r.getChild("root").getChild("body").addOrReplaceChild("sag0_5", CubeListBuilder.create().texOffs(340, 124).addBox(-0.2975F, -0.3F, -0.85F, 0.595F, 3.7205F, 1.7F), PartPose.offsetAndRotation(-0.2857F, -31.2245F, -7.6F, 0F, 0F, -2.5872F));
		PartDefinition p_sag0_6 = r.getChild("root").getChild("body").addOrReplaceChild("sag0_6", CubeListBuilder.create().texOffs(378, 110).addBox(-0.85F, -0.3F, -0.2975F, 1.7F, 5.0377F, 0.595F), PartPose.offsetAndRotation(1.3571F, -33.8776F, -7.6F, 0F, 0F, -2.7624F));
		PartDefinition p_sag1_0 = r.getChild("root").getChild("body").addOrReplaceChild("sag1_0", CubeListBuilder.create().texOffs(318, 110).addBox(-0.85F, -0.3F, -0.2975F, 1.7F, 6.9604F, 0.595F), PartPose.offsetAndRotation(-2F, -40F, -7.9F, 0F, 0F, -0.2496F));
		PartDefinition p_sag1_1 = r.getChild("root").getChild("body").addOrReplaceChild("sag1_1", CubeListBuilder.create().texOffs(324, 110).addBox(-0.2975F, -0.3F, -0.85F, 0.595F, 5.0882F, 1.7F), PartPose.offsetAndRotation(-0.4286F, -33.8367F, -7.9F, 0F, 0F, -0.3577F));
		PartDefinition p_sag1_2 = r.getChild("root").getChild("body").addOrReplaceChild("sag1_2", CubeListBuilder.create().texOffs(474, 124).addBox(-0.85F, -0.3F, -0.2975F, 1.7F, 3.3402F, 0.595F), PartPose.offsetAndRotation(1.1429F, -29.6327F, -7.9F, 0F, 0F, -0.6107F));
		PartDefinition p_sag1_3 = r.getChild("root").getChild("body").addOrReplaceChild("sag1_3", CubeListBuilder.create().texOffs(480, 124).addBox(-0.2975F, -0.3F, -0.85F, 0.595F, 2.1972F, 1.7F), PartPose.offsetAndRotation(2.7143F, -27.3878F, -7.9F, 0F, 0F, -1.3909F));
		PartDefinition p_sag1_4 = r.getChild("root").getChild("body").addOrReplaceChild("sag1_4", CubeListBuilder.create().texOffs(70, 130).addBox(-0.85F, -0.3F, -0.2975F, 1.7F, 2.8956F, 0.595F), PartPose.offsetAndRotation(4.2857F, -27.102F, -7.9F, 0F, 0F, -2.3876F));
		PartDefinition p_sag1_5 = r.getChild("root").getChild("body").addOrReplaceChild("sag1_5", CubeListBuilder.create().texOffs(384, 110).addBox(-0.2975F, -0.3F, -0.85F, 0.595F, 4.558F, 1.7F), PartPose.offsetAndRotation(5.8571F, -28.7755F, -7.9F, 0F, 0F, -2.7333F));
		PartDefinition p_sag1_6 = r.getChild("root").getChild("body").addOrReplaceChild("sag1_6", CubeListBuilder.create().texOffs(330, 110).addBox(-0.85F, -0.3F, -0.2975F, 1.7F, 6.4084F, 0.595F), PartPose.offsetAndRotation(7.4286F, -32.4082F, -7.9F, 0F, 0F, -2.8676F));
	}

	/** Parts that move together: chain. */
	public static final String[] CHAIN = {"wristchain_l", "wristchain_r"};

	/** Parts that move together: robe. */
	public static final String[] ROBE = {"skirt_0", "skirt_1", "skirt_2", "skirt_3", "skirt_4", "skirt_5", "skirt_6", "skirt_7", "skirt_8", "skirt_9", "skirt_10", "skirt_11", "skirt_12", "skirt_13", "skirt_14", "skirt_15", "skirt_16", "skirt_17", "skirt_18", "skirt_19", "skirt_20", "skirt_21", "skirt_22", "skirt_23", "shred_0", "shred_1", "shred_2", "shred_3", "shred_4", "shred_5", "shred_6", "shred_7", "shred_8", "shred_9", "shred_10", "shred_11", "shred_12", "shred_13", "mantle_0", "mantle_1", "mantle_2", "mantle_3", "mantle_4", "mantle_5", "mantle_6", "mantle_7", "mantle_8", "mantle_9", "mantle_10", "mantle_11", "mantle_12", "mantle_13", "mantle_14", "mantle_15", "hoodtail_0", "hoodtail_1", "hoodtail_2", "hoodtail_3", "hoodtail_4", "hoodtail_5"};

	/** Parts that move together: sleeve. */
	public static final String[] SLEEVE = {"sleeve_l0", "sleeve_l1", "sleeve_l2", "sleeve_l3", "sleeve_r0", "sleeve_r1", "sleeve_r2", "sleeve_r3"};

	/** The animated parts, by name. */
	public static ModelPart find(ModelPart root, String name) {
		return switch (name) {
			case "root" -> root.getChild("root");
			case "body" -> root.getChild("root").getChild("body");
			case "skirt_0" -> root.getChild("root").getChild("body").getChild("skirt_0");
			case "skirt_1" -> root.getChild("root").getChild("body").getChild("skirt_1");
			case "skirt_2" -> root.getChild("root").getChild("body").getChild("skirt_2");
			case "skirt_3" -> root.getChild("root").getChild("body").getChild("skirt_3");
			case "skirt_4" -> root.getChild("root").getChild("body").getChild("skirt_4");
			case "skirt_5" -> root.getChild("root").getChild("body").getChild("skirt_5");
			case "skirt_6" -> root.getChild("root").getChild("body").getChild("skirt_6");
			case "skirt_7" -> root.getChild("root").getChild("body").getChild("skirt_7");
			case "skirt_8" -> root.getChild("root").getChild("body").getChild("skirt_8");
			case "skirt_9" -> root.getChild("root").getChild("body").getChild("skirt_9");
			case "skirt_10" -> root.getChild("root").getChild("body").getChild("skirt_10");
			case "skirt_11" -> root.getChild("root").getChild("body").getChild("skirt_11");
			case "skirt_12" -> root.getChild("root").getChild("body").getChild("skirt_12");
			case "skirt_13" -> root.getChild("root").getChild("body").getChild("skirt_13");
			case "skirt_14" -> root.getChild("root").getChild("body").getChild("skirt_14");
			case "skirt_15" -> root.getChild("root").getChild("body").getChild("skirt_15");
			case "skirt_16" -> root.getChild("root").getChild("body").getChild("skirt_16");
			case "skirt_17" -> root.getChild("root").getChild("body").getChild("skirt_17");
			case "skirt_18" -> root.getChild("root").getChild("body").getChild("skirt_18");
			case "skirt_19" -> root.getChild("root").getChild("body").getChild("skirt_19");
			case "skirt_20" -> root.getChild("root").getChild("body").getChild("skirt_20");
			case "skirt_21" -> root.getChild("root").getChild("body").getChild("skirt_21");
			case "skirt_22" -> root.getChild("root").getChild("body").getChild("skirt_22");
			case "skirt_23" -> root.getChild("root").getChild("body").getChild("skirt_23");
			case "shred_0" -> root.getChild("root").getChild("body").getChild("shred_0");
			case "shred_1" -> root.getChild("root").getChild("body").getChild("shred_1");
			case "shred_2" -> root.getChild("root").getChild("body").getChild("shred_2");
			case "shred_3" -> root.getChild("root").getChild("body").getChild("shred_3");
			case "shred_4" -> root.getChild("root").getChild("body").getChild("shred_4");
			case "shred_5" -> root.getChild("root").getChild("body").getChild("shred_5");
			case "shred_6" -> root.getChild("root").getChild("body").getChild("shred_6");
			case "shred_7" -> root.getChild("root").getChild("body").getChild("shred_7");
			case "shred_8" -> root.getChild("root").getChild("body").getChild("shred_8");
			case "shred_9" -> root.getChild("root").getChild("body").getChild("shred_9");
			case "shred_10" -> root.getChild("root").getChild("body").getChild("shred_10");
			case "shred_11" -> root.getChild("root").getChild("body").getChild("shred_11");
			case "shred_12" -> root.getChild("root").getChild("body").getChild("shred_12");
			case "shred_13" -> root.getChild("root").getChild("body").getChild("shred_13");
			case "mantle_0" -> root.getChild("root").getChild("body").getChild("mantle_0");
			case "mantle_1" -> root.getChild("root").getChild("body").getChild("mantle_1");
			case "mantle_2" -> root.getChild("root").getChild("body").getChild("mantle_2");
			case "mantle_3" -> root.getChild("root").getChild("body").getChild("mantle_3");
			case "mantle_4" -> root.getChild("root").getChild("body").getChild("mantle_4");
			case "mantle_5" -> root.getChild("root").getChild("body").getChild("mantle_5");
			case "mantle_6" -> root.getChild("root").getChild("body").getChild("mantle_6");
			case "mantle_7" -> root.getChild("root").getChild("body").getChild("mantle_7");
			case "mantle_8" -> root.getChild("root").getChild("body").getChild("mantle_8");
			case "mantle_9" -> root.getChild("root").getChild("body").getChild("mantle_9");
			case "mantle_10" -> root.getChild("root").getChild("body").getChild("mantle_10");
			case "mantle_11" -> root.getChild("root").getChild("body").getChild("mantle_11");
			case "mantle_12" -> root.getChild("root").getChild("body").getChild("mantle_12");
			case "mantle_13" -> root.getChild("root").getChild("body").getChild("mantle_13");
			case "mantle_14" -> root.getChild("root").getChild("body").getChild("mantle_14");
			case "mantle_15" -> root.getChild("root").getChild("body").getChild("mantle_15");
			case "head" -> root.getChild("root").getChild("body").getChild("head");
			case "hoodtail_0" -> root.getChild("root").getChild("body").getChild("head").getChild("hoodtail_0");
			case "hoodtail_1" -> root.getChild("root").getChild("body").getChild("head").getChild("hoodtail_1");
			case "hoodtail_2" -> root.getChild("root").getChild("body").getChild("head").getChild("hoodtail_2");
			case "hoodtail_3" -> root.getChild("root").getChild("body").getChild("head").getChild("hoodtail_3");
			case "hoodtail_4" -> root.getChild("root").getChild("body").getChild("head").getChild("hoodtail_4");
			case "hoodtail_5" -> root.getChild("root").getChild("body").getChild("head").getChild("hoodtail_5");
			case "halo" -> root.getChild("root").getChild("body").getChild("head").getChild("halo");
			case "arm_l" -> root.getChild("root").getChild("body").getChild("arm_l");
			case "sleeve_l0" -> root.getChild("root").getChild("body").getChild("arm_l").getChild("sleeve_l0");
			case "sleeve_l1" -> root.getChild("root").getChild("body").getChild("arm_l").getChild("sleeve_l1");
			case "sleeve_l2" -> root.getChild("root").getChild("body").getChild("arm_l").getChild("sleeve_l2");
			case "sleeve_l3" -> root.getChild("root").getChild("body").getChild("arm_l").getChild("sleeve_l3");
			case "fore_l" -> root.getChild("root").getChild("body").getChild("arm_l").getChild("fore_l");
			case "hand_l" -> root.getChild("root").getChild("body").getChild("arm_l").getChild("fore_l").getChild("hand_l");
			case "wristchain_l" -> root.getChild("root").getChild("body").getChild("arm_l").getChild("fore_l").getChild("wristchain_l");
			case "arm_r" -> root.getChild("root").getChild("body").getChild("arm_r");
			case "sleeve_r0" -> root.getChild("root").getChild("body").getChild("arm_r").getChild("sleeve_r0");
			case "sleeve_r1" -> root.getChild("root").getChild("body").getChild("arm_r").getChild("sleeve_r1");
			case "sleeve_r2" -> root.getChild("root").getChild("body").getChild("arm_r").getChild("sleeve_r2");
			case "sleeve_r3" -> root.getChild("root").getChild("body").getChild("arm_r").getChild("sleeve_r3");
			case "fore_r" -> root.getChild("root").getChild("body").getChild("arm_r").getChild("fore_r");
			case "hand_r" -> root.getChild("root").getChild("body").getChild("arm_r").getChild("fore_r").getChild("hand_r");
			case "wristchain_r" -> root.getChild("root").getChild("body").getChild("arm_r").getChild("fore_r").getChild("wristchain_r");
			default -> throw new IllegalArgumentException("no animated part " + name);
		};
	}
}
