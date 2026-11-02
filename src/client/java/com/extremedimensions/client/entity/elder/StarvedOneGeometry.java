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
		return LayerDefinition.create(mesh, 512, 512);
	}

	private static void part0(PartDefinition r) {
		PartDefinition p_root = r.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
		PartDefinition p_tail_0 = p_root.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(78, 37).addBox(-3.6F, 0F, -2.88F, 7.2F, 11F, 5.76F), PartPose.offsetAndRotation(0F, -36F, 1.5F, 0.34F, 0F, 0F));
		PartDefinition p_tail_0_rag0 = p_tail_0.addOrReplaceChild("tail_0_rag0", CubeListBuilder.create().texOffs(314, 37).addBox(-1.9F, 0F, -0.6F, 3.8F, 12F, 1.2F), PartPose.offsetAndRotation(0F, 0.5F, 3.68F, 0.14F, 0F, 0F));
		PartDefinition p_tail_0_rag1 = p_tail_0.addOrReplaceChild("tail_0_rag1", CubeListBuilder.create().texOffs(354, 37).addBox(-1.9F, 0F, -0.6F, 4.4F, 11F, 1.2F), PartPose.offsetAndRotation(3.8661F, 0.5F, 1.9941F, 0.14F, 0.9981F, 0F));
		PartDefinition p_tail_0_rag2 = p_tail_0.addOrReplaceChild("tail_0_rag2", CubeListBuilder.create().texOffs(0, 54).addBox(-1.9F, 0F, -0.6F, 3.8F, 10F, 1.2F), PartPose.offsetAndRotation(4.5301F, 0.5F, 0.639F, 0.14F, 1.3963F, 0F));
		PartDefinition p_tail_0_rag3 = p_tail_0.addOrReplaceChild("tail_0_rag3", CubeListBuilder.create().texOffs(300, 37).addBox(-1.9F, 0F, -0.6F, 4.4F, 12F, 1.2F), PartPose.offsetAndRotation(3.1261F, 0.5F, -2.6996F, 0.14F, 2.3944F, 0F));
		PartDefinition p_tail_0_rag4 = p_tail_0.addOrReplaceChild("tail_0_rag4", CubeListBuilder.create().texOffs(396, 37).addBox(-1.9F, 0F, -0.6F, 3.8F, 11F, 1.2F), PartPose.offsetAndRotation(1.5733F, 0.5F, -3.4581F, 0.14F, 2.7925F, 0F));
		PartDefinition p_tail_0_rag5 = p_tail_0.addOrReplaceChild("tail_0_rag5", CubeListBuilder.create().texOffs(432, 37).addBox(-1.9F, 0F, -0.6F, 4.4F, 10F, 1.2F), PartPose.offsetAndRotation(-2.7804F, 0.5F, -2.9317F, 0.14F, 3.7907F, 0F));
		PartDefinition p_tail_0_rag6 = p_tail_0.addOrReplaceChild("tail_0_rag6", CubeListBuilder.create().texOffs(326, 37).addBox(-1.9F, 0F, -0.6F, 3.8F, 12F, 1.2F), PartPose.offsetAndRotation(-3.9837F, 0.5F, -1.84F, 0.14F, 4.1888F, 0F));
		PartDefinition p_tail_0_rag7 = p_tail_0.addOrReplaceChild("tail_0_rag7", CubeListBuilder.create().texOffs(368, 37).addBox(-1.9F, 0F, -0.6F, 4.4F, 11F, 1.2F), PartPose.offsetAndRotation(-4.0917F, 0.5F, 1.6815F, 0.14F, 5.1869F, 0F));
		PartDefinition p_tail_0_rag8 = p_tail_0.addOrReplaceChild("tail_0_rag8", CubeListBuilder.create().texOffs(12, 54).addBox(-1.9F, 0F, -0.6F, 3.8F, 10F, 1.2F), PartPose.offsetAndRotation(-2.9568F, 0.5F, 2.819F, 0.14F, 5.5851F, 0F));
		PartDefinition p_tail_1 = p_tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(200, 37).addBox(-2.7F, 0F, -2.16F, 5.4F, 10F, 4.32F), PartPose.offsetAndRotation(0F, 10.5F, 0F, 0.2F, 0F, 0F));
		PartDefinition p_tail_1_rag0 = p_tail_1.addOrReplaceChild("tail_1_rag0", CubeListBuilder.create().texOffs(408, 37).addBox(-1.9F, 0F, -0.6F, 3.8F, 11F, 1.2F), PartPose.offsetAndRotation(0F, 0.5F, 2.8F, 0.14F, 0F, 0F));
		PartDefinition p_tail_1_rag1 = p_tail_1.addOrReplaceChild("tail_1_rag1", CubeListBuilder.create().texOffs(446, 37).addBox(-1.9F, 0F, -0.6F, 4.4F, 10F, 1.2F), PartPose.offsetAndRotation(3.0957F, 0.5F, 1.3064F, 0.14F, 1.0854F, 0F));
		PartDefinition p_tail_1_rag2 = p_tail_1.addOrReplaceChild("tail_1_rag2", CubeListBuilder.create().texOffs(292, 54).addBox(-1.9F, 0F, -0.6F, 3.8F, 9F, 1.2F), PartPose.offsetAndRotation(3.5F, 0.5F, 0F, 0.14F, 1.5708F, 0F));
		PartDefinition p_tail_1_rag3 = p_tail_1.addOrReplaceChild("tail_1_rag3", CubeListBuilder.create().texOffs(382, 37).addBox(-1.9F, 0F, -0.6F, 4.4F, 11F, 1.2F), PartPose.offsetAndRotation(1.633F, 0.5F, -2.4766F, 0.14F, 2.6562F, 0F));
		PartDefinition p_tail_1_rag4 = p_tail_1.addOrReplaceChild("tail_1_rag4", CubeListBuilder.create().texOffs(24, 54).addBox(-1.9F, 0F, -0.6F, 3.8F, 10F, 1.2F), PartPose.offsetAndRotation(0F, 0.5F, -2.8F, 0.14F, 3.1416F, 0F));
		PartDefinition p_tail_1_rag5 = p_tail_1.addOrReplaceChild("tail_1_rag5", CubeListBuilder.create().texOffs(250, 54).addBox(-1.9F, 0F, -0.6F, 4.4F, 9F, 1.2F), PartPose.offsetAndRotation(-3.0957F, 0.5F, -1.3064F, 0.14F, 4.227F, 0F));
		PartDefinition p_tail_1_rag6 = p_tail_1.addOrReplaceChild("tail_1_rag6", CubeListBuilder.create().texOffs(420, 37).addBox(-1.9F, 0F, -0.6F, 3.8F, 11F, 1.2F), PartPose.offsetAndRotation(-3.5F, 0.5F, 0F, 0.14F, 4.7124F, 0F));
		PartDefinition p_tail_1_rag7 = p_tail_1.addOrReplaceChild("tail_1_rag7", CubeListBuilder.create().texOffs(460, 37).addBox(-1.9F, 0F, -0.6F, 4.4F, 10F, 1.2F), PartPose.offsetAndRotation(-1.633F, 0.5F, 2.4766F, 0.14F, 5.7978F, 0F));
		PartDefinition p_tail_2 = p_tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(338, 37).addBox(-1.9F, 0F, -1.52F, 3.8F, 9F, 3.04F), PartPose.offsetAndRotation(0F, 9.5F, 0F, 0.22F, 0F, 0F));
		PartDefinition p_tail_2_rag0 = p_tail_2.addOrReplaceChild("tail_2_rag0", CubeListBuilder.create().texOffs(36, 54).addBox(-1.9F, 0F, -0.6F, 3.8F, 10F, 1.2F), PartPose.offsetAndRotation(0F, 0.5F, 1.92F, 0.14F, 0F, 0F));
		PartDefinition p_tail_2_rag1 = p_tail_2.addOrReplaceChild("tail_2_rag1", CubeListBuilder.create().texOffs(264, 54).addBox(-1.9F, 0F, -0.6F, 4.4F, 9F, 1.2F), PartPose.offsetAndRotation(2.2348F, 0.5F, 0.7F, 0.14F, 1.1976F, 0F));
		PartDefinition p_tail_2_rag2 = p_tail_2.addOrReplaceChild("tail_2_rag2", CubeListBuilder.create().texOffs(48, 66).addBox(-1.9F, 0F, -0.6F, 3.8F, 8F, 1.2F), PartPose.offsetAndRotation(2.3398F, 0.5F, -0.4272F, 0.14F, 1.7952F, 0F));
		PartDefinition p_tail_2_rag3 = p_tail_2.addOrReplaceChild("tail_2_rag3", CubeListBuilder.create().texOffs(474, 37).addBox(-1.9F, 0F, -0.6F, 4.4F, 10F, 1.2F), PartPose.offsetAndRotation(0.3558F, 0.5F, -1.8988F, 0.14F, 2.9928F, 0F));
		PartDefinition p_tail_2_rag4 = p_tail_2.addOrReplaceChild("tail_2_rag4", CubeListBuilder.create().texOffs(304, 54).addBox(-1.9F, 0F, -0.6F, 3.8F, 9F, 1.2F), PartPose.offsetAndRotation(-1.0413F, 0.5F, -1.7299F, 0.14F, 3.5904F, 0F));
		PartDefinition p_tail_2_rag5 = p_tail_2.addOrReplaceChild("tail_2_rag5", CubeListBuilder.create().texOffs(20, 66).addBox(-1.9F, 0F, -0.6F, 4.4F, 8F, 1.2F), PartPose.offsetAndRotation(-2.3931F, 0.5F, 0.145F, 0.14F, 4.788F, 0F));
		PartDefinition p_tail_2_rag6 = p_tail_2.addOrReplaceChild("tail_2_rag6", CubeListBuilder.create().texOffs(48, 54).addBox(-1.9F, 0F, -0.6F, 3.8F, 10F, 1.2F), PartPose.offsetAndRotation(-1.8764F, 0.5F, 1.1971F, 0.14F, 5.3856F, 0F));
		PartDefinition p_tail_3 = p_tail_2.addOrReplaceChild("tail_3", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 8.5F, 0F, 0.28F, 0F, 0F));
		PartDefinition p_tail_3_rag0 = p_tail_3.addOrReplaceChild("tail_3_rag0", CubeListBuilder.create().texOffs(60, 54).addBox(-1.9F, 0F, -0.6F, 3.8F, 10F, 1.2F), PartPose.offsetAndRotation(0F, 0.5F, 1.12F, 0.14F, 0F, 0F));
		PartDefinition p_tail_3_rag1 = p_tail_3.addOrReplaceChild("tail_3_rag1", CubeListBuilder.create().texOffs(278, 54).addBox(-1.9F, 0F, -0.6F, 4.4F, 9F, 1.2F), PartPose.offsetAndRotation(1.3651F, 0.5F, 0.2483F, 0.14F, 1.3472F, 0F));
		PartDefinition p_tail_3_rag2 = p_tail_3.addOrReplaceChild("tail_3_rag2", CubeListBuilder.create().texOffs(60, 66).addBox(-1.9F, 0F, -0.6F, 3.8F, 8F, 1.2F), PartPose.offsetAndRotation(1.2124F, 0.5F, -0.56F, 0.14F, 2.0944F, 0F));
		PartDefinition p_tail_3_rag3 = p_tail_3.addOrReplaceChild("tail_3_rag3", CubeListBuilder.create().texOffs(488, 37).addBox(-1.9F, 0F, -0.6F, 4.4F, 10F, 1.2F), PartPose.offsetAndRotation(-0.4137F, 0.5F, -1.07F, 0.14F, 3.4416F, 0F));
		PartDefinition p_tail_3_rag4 = p_tail_3.addOrReplaceChild("tail_3_rag4", CubeListBuilder.create().texOffs(316, 54).addBox(-1.9F, 0F, -0.6F, 3.8F, 9F, 1.2F), PartPose.offsetAndRotation(-1.2124F, 0.5F, -0.56F, 0.14F, 4.1888F, 0F));
		PartDefinition p_tail_3_rag5 = p_tail_3.addOrReplaceChild("tail_3_rag5", CubeListBuilder.create().texOffs(34, 66).addBox(-1.9F, 0F, -0.6F, 4.4F, 8F, 1.2F), PartPose.offsetAndRotation(-0.9514F, 0.5F, 0.8216F, 0.14F, 5.536F, 0F));
		PartDefinition p_body = p_root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(46, 37).addBox(-4.5F, -10F, -3.2F, 9F, 10F, 6.6F).texOffs(280, 0).addBox(-7.5F, -31F, -0.8F, 15F, 22F, 6F).texOffs(410, 0).addBox(-5.6F, -28.5F, -3.4F, 11.2F, 18F, 2.8F).texOffs(440, 0).addBox(-0.8F, -31F, -4.3F, 1.6F, 18F, 1.5F).texOffs(104, 54).addBox(-10.5F, -34F, -2.6F, 21F, 4F, 6.8F).texOffs(188, 54).addBox(-12.6F, -35.5F, -2.4F, 4.8F, 4.2F, 5.6F).texOffs(210, 54).addBox(7.8F, -35.5F, -2.4F, 4.8F, 4.2F, 5.6F).texOffs(288, 76).addBox(-1.1F, -32F, 4.9F, 2.2F, 2.2F, 1.6F).texOffs(298, 76).addBox(-1.1F, -28F, 4.9F, 2.2F, 2.2F, 1.6F).texOffs(308, 76).addBox(-1.1F, -24F, 4.9F, 2.2F, 2.2F, 1.6F).texOffs(318, 76).addBox(-1.1F, -20F, 4.9F, 2.2F, 2.2F, 1.6F).texOffs(328, 76).addBox(-1.1F, -16F, 4.9F, 2.2F, 2.2F, 1.6F).texOffs(338, 76).addBox(-1.1F, -12F, 4.9F, 2.2F, 2.2F, 1.6F).texOffs(348, 76).addBox(-1.1F, -8F, 4.9F, 2.2F, 2.2F, 1.6F).texOffs(358, 76).addBox(-1.1F, -4F, 4.9F, 2.2F, 2.2F, 1.6F), PartPose.offsetAndRotation(0F, -36F, 0F, 0.45F, 0F, 0F));
		PartDefinition p_rib_l0 = p_body.addOrReplaceChild("rib_l0", CubeListBuilder.create().texOffs(80, 82).addBox(0F, -0.6F, -0.7F, 6.3F, 1.2F, 1.4F).texOffs(216, 66).addBox(5.1F, -0.6F, -0.6F, 1.2F, 1.2F, 5.2F), PartPose.offsetAndRotation(0.7F, -28.6F, -3.7F, 0F, 0F, 0.16F));
		PartDefinition p_rib_r0 = p_body.addOrReplaceChild("rib_r0", CubeListBuilder.create().texOffs(98, 82).addBox(-6.3F, -0.6F, -0.7F, 6.3F, 1.2F, 1.4F).texOffs(232, 66).addBox(-6.3F, -0.6F, -0.6F, 1.2F, 1.2F, 5.2F), PartPose.offsetAndRotation(-0.7F, -28.6F, -3.7F, 0F, 0F, -0.16F));
		PartDefinition p_rib_l1 = p_body.addOrReplaceChild("rib_l1", CubeListBuilder.create().texOffs(116, 82).addBox(0F, -0.6F, -0.7F, 6.75F, 1.2F, 1.4F).texOffs(248, 66).addBox(5.55F, -0.6F, -0.6F, 1.2F, 1.2F, 5.2F), PartPose.offsetAndRotation(0.7F, -25.5F, -3.7F, 0F, 0F, 0.185F));
		PartDefinition p_rib_r1 = p_body.addOrReplaceChild("rib_r1", CubeListBuilder.create().texOffs(134, 82).addBox(-6.75F, -0.6F, -0.7F, 6.75F, 1.2F, 1.4F).texOffs(264, 66).addBox(-6.75F, -0.6F, -0.6F, 1.2F, 1.2F, 5.2F), PartPose.offsetAndRotation(-0.7F, -25.5F, -3.7F, 0F, 0F, -0.185F));
	}

	private static void part1(PartDefinition r) {
		PartDefinition p_rib_l2 = r.getChild("root").getChild("body").addOrReplaceChild("rib_l2", CubeListBuilder.create().texOffs(40, 82).addBox(0F, -0.6F, -0.7F, 7.2F, 1.2F, 1.4F).texOffs(280, 66).addBox(6F, -0.6F, -0.6F, 1.2F, 1.2F, 5.2F), PartPose.offsetAndRotation(0.7F, -22.4F, -3.7F, 0F, 0F, 0.21F));
		PartDefinition p_rib_r2 = r.getChild("root").getChild("body").addOrReplaceChild("rib_r2", CubeListBuilder.create().texOffs(60, 82).addBox(-7.2F, -0.6F, -0.7F, 7.2F, 1.2F, 1.4F).texOffs(296, 66).addBox(-7.2F, -0.6F, -0.6F, 1.2F, 1.2F, 5.2F), PartPose.offsetAndRotation(-0.7F, -22.4F, -3.7F, 0F, 0F, -0.21F));
		PartDefinition p_rib_l3 = r.getChild("root").getChild("body").addOrReplaceChild("rib_l3", CubeListBuilder.create().texOffs(152, 82).addBox(0F, -0.6F, -0.7F, 6.75F, 1.2F, 1.4F).texOffs(312, 66).addBox(5.55F, -0.6F, -0.6F, 1.2F, 1.2F, 5.2F), PartPose.offsetAndRotation(0.7F, -19.3F, -3.7F, 0F, 0F, 0.235F));
		PartDefinition p_rib_r3 = r.getChild("root").getChild("body").addOrReplaceChild("rib_r3", CubeListBuilder.create().texOffs(170, 82).addBox(-6.75F, -0.6F, -0.7F, 6.75F, 1.2F, 1.4F).texOffs(328, 66).addBox(-6.75F, -0.6F, -0.6F, 1.2F, 1.2F, 5.2F), PartPose.offsetAndRotation(-0.7F, -19.3F, -3.7F, 0F, 0F, -0.235F));
		PartDefinition p_rib_l4 = r.getChild("root").getChild("body").addOrReplaceChild("rib_l4", CubeListBuilder.create().texOffs(188, 82).addBox(0F, -0.6F, -0.7F, 6.3F, 1.2F, 1.4F).texOffs(344, 66).addBox(5.1F, -0.6F, -0.6F, 1.2F, 1.2F, 5.2F), PartPose.offsetAndRotation(0.7F, -16.2F, -3.7F, 0F, 0F, 0.26F));
		PartDefinition p_rib_r4 = r.getChild("root").getChild("body").addOrReplaceChild("rib_r4", CubeListBuilder.create().texOffs(206, 82).addBox(-6.3F, -0.6F, -0.7F, 6.3F, 1.2F, 1.4F).texOffs(360, 66).addBox(-6.3F, -0.6F, -0.6F, 1.2F, 1.2F, 5.2F), PartPose.offsetAndRotation(-0.7F, -16.2F, -3.7F, 0F, 0F, -0.26F));
		PartDefinition p_rib_l5 = r.getChild("root").getChild("body").addOrReplaceChild("rib_l5", CubeListBuilder.create().texOffs(224, 82).addBox(0F, -0.6F, -0.7F, 5.85F, 1.2F, 1.4F).texOffs(376, 66).addBox(4.65F, -0.6F, -0.6F, 1.2F, 1.2F, 5.2F), PartPose.offsetAndRotation(0.7F, -13.1F, -3.7F, 0F, 0F, 0.285F));
		PartDefinition p_rib_r5 = r.getChild("root").getChild("body").addOrReplaceChild("rib_r5", CubeListBuilder.create().texOffs(240, 82).addBox(-5.85F, -0.6F, -0.7F, 5.85F, 1.2F, 1.4F).texOffs(392, 66).addBox(-5.85F, -0.6F, -0.6F, 1.2F, 1.2F, 5.2F), PartPose.offsetAndRotation(-0.7F, -13.1F, -3.7F, 0F, 0F, -0.285F));
		PartDefinition p_shroud_0 = r.getChild("root").getChild("body").addOrReplaceChild("shroud_0", CubeListBuilder.create().texOffs(322, 0).addBox(-2.7F, 0F, -0.7F, 5.4F, 26F, 1.4F), PartPose.offsetAndRotation(-8.3547F, -32.5F, 0.9093F, -0.3115F, 0F, 0.1412F));
		PartDefinition p_shroud_1 = r.getChild("root").getChild("body").addOrReplaceChild("shroud_1", CubeListBuilder.create().texOffs(50, 0).addBox(-2.7F, 0F, -0.7F, 6.2F, 33F, 1.4F), PartPose.offsetAndRotation(-9.5025F, -32.5F, 2.0219F, -0.2772F, 0F, 0.2061F));
		PartDefinition p_shroud_2 = r.getChild("root").getChild("body").addOrReplaceChild("shroud_2", CubeListBuilder.create().texOffs(120, 0).addBox(-2.7F, 0F, -0.7F, 5.4F, 30F, 1.4F), PartPose.offsetAndRotation(-9.7847F, -32.5F, 3.2237F, -0.2128F, 0F, 0.2534F));
		PartDefinition p_shroud_3 = r.getChild("root").getChild("body").addOrReplaceChild("shroud_3", CubeListBuilder.create().texOffs(244, 0).addBox(-2.7F, 0F, -0.7F, 6.2F, 27F, 1.4F), PartPose.offsetAndRotation(-9.1755F, -32.5F, 4.405F, -0.1786F, 0F, 0.1457F));
		PartDefinition p_shroud_4 = r.getChild("root").getChild("body").addOrReplaceChild("shroud_4", CubeListBuilder.create().texOffs(18, 0).addBox(-2.7F, 0F, -0.7F, 5.4F, 34F, 1.4F), PartPose.offsetAndRotation(-7.7306F, -32.5F, 5.4584F, -0.1128F, 0F, 0.1561F));
		PartDefinition p_shroud_5 = r.getChild("root").getChild("body").addOrReplaceChild("shroud_5", CubeListBuilder.create().texOffs(102, 0).addBox(-2.7F, 0F, -0.7F, 6.2F, 31F, 1.4F), PartPose.offsetAndRotation(-5.5815F, -32.5F, 6.2879F, -0.0404F, 0F, 0.1351F));
		PartDefinition p_shroud_6 = r.getChild("root").getChild("body").addOrReplaceChild("shroud_6", CubeListBuilder.create().texOffs(228, 0).addBox(-2.7F, 0F, -0.7F, 5.4F, 28F, 1.4F), PartPose.offsetAndRotation(-2.9239F, -32.5F, 6.8178F, -0.0931F, 0F, 0.0449F));
		PartDefinition p_shroud_7 = r.getChild("root").getChild("body").addOrReplaceChild("shroud_7", CubeListBuilder.create().texOffs(0, 0).addBox(-2.7F, 0F, -0.7F, 6.2F, 35F, 1.4F), PartPose.offsetAndRotation(0F, -32.5F, 7F, -0.0439F, 0F, 0F));
		PartDefinition p_shroud_8 = r.getChild("root").getChild("body").addOrReplaceChild("shroud_8", CubeListBuilder.create().texOffs(86, 0).addBox(-2.7F, 0F, -0.7F, 5.4F, 32F, 1.4F), PartPose.offsetAndRotation(2.9239F, -32.5F, 6.8178F, -0.0121F, 0F, -0.0701F));
		PartDefinition p_shroud_9 = r.getChild("root").getChild("body").addOrReplaceChild("shroud_9", CubeListBuilder.create().texOffs(152, 0).addBox(-2.7F, 0F, -0.7F, 6.2F, 29F, 1.4F), PartPose.offsetAndRotation(5.5815F, -32.5F, 6.2879F, -0.1116F, 0F, -0.0863F));
		PartDefinition p_shroud_10 = r.getChild("root").getChild("body").addOrReplaceChild("shroud_10", CubeListBuilder.create().texOffs(338, 0).addBox(-2.7F, 0F, -0.7F, 5.4F, 26F, 1.4F), PartPose.offsetAndRotation(7.7306F, -32.5F, 5.4584F, -0.1128F, 0F, -0.1561F));
		PartDefinition p_shroud_11 = r.getChild("root").getChild("body").addOrReplaceChild("shroud_11", CubeListBuilder.create().texOffs(68, 0).addBox(-2.7F, 0F, -0.7F, 6.2F, 33F, 1.4F), PartPose.offsetAndRotation(9.1755F, -32.5F, 4.405F, -0.1443F, 0F, -0.2308F));
		PartDefinition p_shroud_12 = r.getChild("root").getChild("body").addOrReplaceChild("shroud_12", CubeListBuilder.create().texOffs(136, 0).addBox(-2.7F, 0F, -0.7F, 5.4F, 30F, 1.4F), PartPose.offsetAndRotation(9.7847F, -32.5F, 3.2237F, -0.2222F, 0F, -0.1584F));
		PartDefinition p_shroud_13 = r.getChild("root").getChild("body").addOrReplaceChild("shroud_13", CubeListBuilder.create().texOffs(262, 0).addBox(-2.7F, 0F, -0.7F, 6.2F, 27F, 1.4F), PartPose.offsetAndRotation(9.5025F, -32.5F, 2.0219F, -0.2772F, 0F, -0.2061F));
		PartDefinition p_shroud_14 = r.getChild("root").getChild("body").addOrReplaceChild("shroud_14", CubeListBuilder.create().texOffs(34, 0).addBox(-2.7F, 0F, -0.7F, 5.4F, 34F, 1.4F), PartPose.offsetAndRotation(8.3547F, -32.5F, 0.9093F, -0.3571F, 0F, -0.2323F));
		PartDefinition p_shroud_sh0 = r.getChild("root").getChild("body").addOrReplaceChild("shroud_sh0", CubeListBuilder.create().texOffs(222, 37).addBox(-3.2F, 0F, -0.7F, 6.4F, 13F, 1.4F), PartPose.offsetAndRotation(-9.5F, -35F, -1.5F, -0.5159F, 0F, 0.3877F));
		PartDefinition p_shroud_sh1 = r.getChild("root").getChild("body").addOrReplaceChild("shroud_sh1", CubeListBuilder.create().texOffs(240, 37).addBox(-3.2F, 0F, -0.7F, 6.4F, 13F, 1.4F), PartPose.offsetAndRotation(9.5F, -35F, -1.5F, -0.5159F, 0F, -0.3877F));
		PartDefinition p_head = r.getChild("root").getChild("body").addOrReplaceChild("head", CubeListBuilder.create().texOffs(388, 54).addBox(-2.2F, -3F, -1.6F, 4.4F, 5F, 4.2F).texOffs(0, 37).addBox(-6F, -2.5F, -5F, 12F, 6F, 11F).texOffs(170, 0).addBox(-7F, -15F, -9F, 14F, 15F, 15F).texOffs(106, 37).addBox(-5.2F, -18.5F, -9.6F, 10.4F, 4F, 12F).texOffs(160, 54).addBox(-3F, -21F, -10.4F, 6F, 3F, 8F).texOffs(482, 66).addBox(-1.4F, -22.5F, -11.2F, 2.8F, 2F, 4F).texOffs(172, 37).addBox(-5F, -13.5F, 5.5F, 10F, 11F, 4F).texOffs(232, 54).addBox(-3F, -11.5F, 9F, 6F, 8F, 3F).texOffs(444, 66).addBox(-7.6F, -16.2F, -10.4F, 15.2F, 3F, 2.4F).texOffs(448, 0).addBox(-7.8F, -14F, -10.2F, 1.8F, 14F, 4F).texOffs(460, 0).addBox(6F, -14F, -10.2F, 1.8F, 14F, 4F).texOffs(278, 37).addBox(-4.8F, -13.4F, -9.4F, 9.6F, 12.4F, 0.8F).texOffs(288, 82).addBox(-4.4F, -6.6F, -9.8F, 8.8F, 1.4F, 0.6F).texOffs(308, 82).addBox(-3.9F, -7.3F, -9.95F, 0.55F, 0.9F, 0.5F).texOffs(312, 82).addBox(-3.3F, -5.4F, -9.95F, 0.55F, 0.9F, 0.5F).texOffs(316, 82).addBox(-2.65F, -7.3F, -9.95F, 0.55F, 0.9F, 0.5F).texOffs(320, 82).addBox(-2.05F, -5.4F, -9.95F, 0.55F, 0.9F, 0.5F).texOffs(324, 82).addBox(-1.4F, -7.3F, -9.95F, 0.55F, 0.9F, 0.5F).texOffs(328, 82).addBox(-0.8F, -5.4F, -9.95F, 0.55F, 0.9F, 0.5F).texOffs(332, 82).addBox(-0.15F, -7.3F, -9.95F, 0.55F, 0.9F, 0.5F).texOffs(336, 82).addBox(0.45F, -5.4F, -9.95F, 0.55F, 0.9F, 0.5F).texOffs(340, 82).addBox(1.1F, -7.3F, -9.95F, 0.55F, 0.9F, 0.5F).texOffs(344, 82).addBox(1.7F, -5.4F, -9.95F, 0.55F, 0.9F, 0.5F).texOffs(348, 82).addBox(2.35F, -7.3F, -9.95F, 0.55F, 0.9F, 0.5F).texOffs(352, 82).addBox(2.95F, -5.4F, -9.95F, 0.55F, 0.9F, 0.5F).texOffs(356, 82).addBox(3.6F, -7.3F, -9.95F, 0.55F, 0.9F, 0.5F).texOffs(360, 82).addBox(4.2F, -5.4F, -9.95F, 0.55F, 0.9F, 0.5F), PartPose.offsetAndRotation(0F, -34F, -3F, -0.38F, 0F, 0F));
		PartDefinition p_crown = p_head.addOrReplaceChild("crown", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -15.6F, -1.2F, 0F, 0F, 0F));
		PartDefinition p_band_0 = p_crown.addOrReplaceChild("band_0", CubeListBuilder.create().texOffs(144, 76).addBox(-2F, -1.2F, -0.6F, 4F, 2.4F, 1.2F), PartPose.offsetAndRotation(0F, -0.4F, 8.2F, 0F, 0F, -0.05F));
		PartDefinition p_band_1 = p_crown.addOrReplaceChild("band_1", CubeListBuilder.create().texOffs(156, 76).addBox(-2F, -1.2F, -0.6F, 4F, 2.4F, 1.2F), PartPose.offsetAndRotation(3.2541F, 0F, 7.3879F, 0.08F, 0.4488F, 0F));
		PartDefinition p_band_2 = p_crown.addOrReplaceChild("band_2", CubeListBuilder.create().texOffs(168, 76).addBox(-2F, -1.2F, -0.6F, 4F, 2.4F, 1.2F), PartPose.offsetAndRotation(5.8637F, 0.4F, 5.1126F, 0F, 0.8976F, 0.05F));
		PartDefinition p_band_3 = p_crown.addOrReplaceChild("band_3", CubeListBuilder.create().texOffs(180, 76).addBox(-2F, -1.2F, -0.6F, 4F, 2.4F, 1.2F), PartPose.offsetAndRotation(7.312F, -0.4F, 1.8247F, 0.08F, 1.3464F, -0.05F));
		PartDefinition p_band_6 = p_crown.addOrReplaceChild("band_6", CubeListBuilder.create().texOffs(192, 76).addBox(-2F, -1.2F, -0.6F, 4F, 2.4F, 1.2F), PartPose.offsetAndRotation(3.2541F, -0.4F, -7.3879F, 0F, 2.6928F, -0.05F));
		PartDefinition p_band_7 = p_crown.addOrReplaceChild("band_7", CubeListBuilder.create().texOffs(204, 76).addBox(-2F, -1.2F, -0.6F, 4F, 2.4F, 1.2F), PartPose.offsetAndRotation(0F, 0F, -8.2F, 0.08F, 3.1416F, 0F));
		PartDefinition p_band_8 = p_crown.addOrReplaceChild("band_8", CubeListBuilder.create().texOffs(216, 76).addBox(-2F, -1.2F, -0.6F, 4F, 2.4F, 1.2F), PartPose.offsetAndRotation(-3.2541F, 0.4F, -7.3879F, 0F, 3.5904F, 0.05F));
		PartDefinition p_band_9 = p_crown.addOrReplaceChild("band_9", CubeListBuilder.create().texOffs(228, 76).addBox(-2F, -1.2F, -0.6F, 4F, 2.4F, 1.2F), PartPose.offsetAndRotation(-5.8637F, -0.4F, -5.1126F, 0.08F, 4.0392F, -0.05F));
		PartDefinition p_band_10 = p_crown.addOrReplaceChild("band_10", CubeListBuilder.create().texOffs(240, 76).addBox(-2F, -1.2F, -0.6F, 4F, 2.4F, 1.2F), PartPose.offsetAndRotation(-7.312F, 0F, -1.8247F, 0F, 4.488F, 0F));
		PartDefinition p_band_11 = p_crown.addOrReplaceChild("band_11", CubeListBuilder.create().texOffs(252, 76).addBox(-2F, -1.2F, -0.6F, 4F, 2.4F, 1.2F), PartPose.offsetAndRotation(-7.312F, 0.4F, 1.8247F, 0.08F, 4.9368F, 0.05F));
		PartDefinition p_band_12 = p_crown.addOrReplaceChild("band_12", CubeListBuilder.create().texOffs(264, 76).addBox(-2F, -1.2F, -0.6F, 4F, 2.4F, 1.2F), PartPose.offsetAndRotation(-5.8637F, -0.4F, 5.1126F, 0F, 5.3856F, -0.05F));
		PartDefinition p_band_13 = p_crown.addOrReplaceChild("band_13", CubeListBuilder.create().texOffs(276, 76).addBox(-2F, -1.2F, -0.6F, 4F, 2.4F, 1.2F), PartPose.offsetAndRotation(-3.2541F, 0F, 7.3879F, 0.08F, 5.8344F, 0F));
		PartDefinition p_spike_0 = p_crown.addOrReplaceChild("spike_0", CubeListBuilder.create().texOffs(348, 54).addBox(-0.9F, -8.06F, -0.9F, 1.8F, 8.06F, 1.8F), PartPose.offsetAndRotation(0F, -0.8F, 8.2F, -0.25F, 0F, -0.12F));
	}

	private static void part2(PartDefinition r) {
		PartDefinition p_spiketip_0 = r.getChild("root").getChild("body").getChild("head").getChild("crown").getChild("spike_0").addOrReplaceChild("spiketip_0", CubeListBuilder.create().texOffs(132, 76).addBox(-0.5F, -4.94F, -0.5F, 1F, 4.94F, 1F), PartPose.offsetAndRotation(0F, -8.06F, 0F, -0.2F, 0F, -0.25F));
		PartDefinition p_spike_1 = r.getChild("root").getChild("body").getChild("head").getChild("crown").addOrReplaceChild("spike_1", CubeListBuilder.create().texOffs(408, 66).addBox(-0.9F, -5.58F, -0.9F, 1.8F, 5.58F, 1.8F), PartPose.offsetAndRotation(3.9202F, -0.8F, 6.9907F, -0.35F, 0.55F, 0F));
		PartDefinition p_spiketip_1 = p_spike_1.addOrReplaceChild("spiketip_1", CubeListBuilder.create().texOffs(506, 76).addBox(-0.5F, -3.42F, -0.5F, 1F, 3.42F, 1F), PartPose.offsetAndRotation(0F, -5.58F, 0F, 0.2F, 0F, 0F));
		PartDefinition p_spike_2 = r.getChild("root").getChild("body").getChild("head").getChild("crown").addOrReplaceChild("spike_2", CubeListBuilder.create().texOffs(120, 66).addBox(-0.9F, -6.82F, -0.9F, 1.8F, 6.82F, 1.8F), PartPose.offsetAndRotation(-4.2348F, -0.8F, 6.7678F, -0.3F, -0.6F, 0.12F));
		PartDefinition p_spiketip_2 = p_spike_2.addOrReplaceChild("spiketip_2", CubeListBuilder.create().texOffs(136, 76).addBox(-0.5F, -4.18F, -0.5F, 1F, 4.18F, 1F), PartPose.offsetAndRotation(0F, -6.82F, 0F, -0.2F, 0F, 0.25F));
		PartDefinition p_spike_3 = r.getChild("root").getChild("body").getChild("head").getChild("crown").addOrReplaceChild("spike_3", CubeListBuilder.create().texOffs(496, 66).addBox(-0.9F, -3.72F, -0.9F, 1.8F, 3.72F, 1.8F), PartPose.offsetAndRotation(7.3179F, -0.8F, 1.7959F, -0.5F, 1.35F, -0.12F));
		PartDefinition p_spike_4 = r.getChild("root").getChild("body").getChild("head").getChild("crown").addOrReplaceChild("spike_4", CubeListBuilder.create().texOffs(128, 66).addBox(-0.9F, -6.2F, -0.9F, 1.8F, 6.2F, 1.8F), PartPose.offsetAndRotation(-7.4812F, -0.8F, 0.58F, -0.45F, -1.5F, 0F));
		PartDefinition p_spiketip_4 = p_spike_4.addOrReplaceChild("spiketip_4", CubeListBuilder.create().texOffs(0, 82).addBox(-0.5F, -3.8F, -0.5F, 1F, 3.8F, 1F), PartPose.offsetAndRotation(0F, -6.2F, 0F, -0.2F, 0F, 0F));
		PartDefinition p_spike_5 = r.getChild("root").getChild("body").getChild("head").getChild("crown").addOrReplaceChild("spike_5", CubeListBuilder.create().texOffs(416, 66).addBox(-0.9F, -4.96F, -0.9F, 1.8F, 4.96F, 1.8F), PartPose.offsetAndRotation(5.066F, -0.8F, -6.0466F, -0.6F, 2.4F, 0.12F));
		PartDefinition p_spiketip_5 = p_spike_5.addOrReplaceChild("spiketip_5", CubeListBuilder.create().texOffs(4, 82).addBox(-0.5F, -3.04F, -0.5F, 1F, 3.04F, 1F), PartPose.offsetAndRotation(0F, -4.96F, 0F, 0.2F, 0F, 0.25F));
		PartDefinition p_spike_6 = r.getChild("root").getChild("body").getChild("head").getChild("crown").addOrReplaceChild("spike_6", CubeListBuilder.create().texOffs(504, 66).addBox(-0.9F, -3.1F, -0.9F, 1.8F, 3.1F, 1.8F), PartPose.offsetAndRotation(-4.4885F, -0.8F, -6.5694F, -0.55F, -2.5F, -0.12F));
		PartDefinition p_spike_7 = r.getChild("root").getChild("body").getChild("head").getChild("crown").addOrReplaceChild("spike_7", CubeListBuilder.create().texOffs(72, 66).addBox(-0.9F, -7.44F, -0.9F, 1.8F, 7.44F, 1.8F), PartPose.offsetAndRotation(0.686F, -0.8F, -8.1656F, -0.7F, 3.05F, 0F));
		PartDefinition p_spiketip_7 = p_spike_7.addOrReplaceChild("spiketip_7", CubeListBuilder.create().texOffs(140, 76).addBox(-0.5F, -4.56F, -0.5F, 1F, 4.56F, 1F), PartPose.offsetAndRotation(0F, -7.44F, 0F, 0.2F, 0F, 0F));
		PartDefinition p_crownchain_0 = r.getChild("root").getChild("body").getChild("head").getChild("crown").addOrReplaceChild("crownchain_0", CubeListBuilder.create(), PartPose.offsetAndRotation(7.5923F, 0.8F, -0.3724F, -0.02F, 0F, -0.0499F));
		PartDefinition p_crownlink_00 = p_crownchain_0.addOrReplaceChild("crownlink_00", CubeListBuilder.create().texOffs(368, 76).addBox(-0.8F, -0.3F, -0.28F, 1.6F, 3.2F, 0.56F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_crownlink_01 = p_crownchain_0.addOrReplaceChild("crownlink_01", CubeListBuilder.create().texOffs(0, 76).addBox(-0.28F, -0.3F, -0.8F, 0.56F, 3.2F, 1.6F), PartPose.offsetAndRotation(0F, 2.6F, 0F, 0F, 0F, 0F));
		PartDefinition p_crownlink_02 = p_crownchain_0.addOrReplaceChild("crownlink_02", CubeListBuilder.create().texOffs(374, 76).addBox(-0.8F, -0.3F, -0.28F, 1.6F, 3.2F, 0.56F), PartPose.offsetAndRotation(0F, 5.2F, 0F, 0F, 0F, 0F));
		PartDefinition p_crownlink_03 = p_crownchain_0.addOrReplaceChild("crownlink_03", CubeListBuilder.create().texOffs(6, 76).addBox(-0.28F, -0.3F, -0.8F, 0.56F, 3.2F, 1.6F), PartPose.offsetAndRotation(0F, 7.8F, 0F, 0F, 0F, 0F));
		PartDefinition p_crownlink_04 = p_crownchain_0.addOrReplaceChild("crownlink_04", CubeListBuilder.create().texOffs(380, 76).addBox(-0.8F, -0.3F, -0.28F, 1.6F, 3.2F, 0.56F), PartPose.offsetAndRotation(0F, 10.4F, 0F, 0F, 0F, 0F));
		PartDefinition p_crownlink_05 = p_crownchain_0.addOrReplaceChild("crownlink_05", CubeListBuilder.create().texOffs(12, 76).addBox(-0.28F, -0.3F, -0.8F, 0.56F, 3.2F, 1.6F), PartPose.offsetAndRotation(0F, 13F, 0F, 0F, 0F, 0F));
		PartDefinition p_crownchain_1 = r.getChild("root").getChild("body").getChild("head").getChild("crown").addOrReplaceChild("crownchain_1", CubeListBuilder.create(), PartPose.offsetAndRotation(2.3485F, 0.8F, -7.8938F, -0.02F, 0F, -0.0499F));
		PartDefinition p_crownlink_10 = p_crownchain_1.addOrReplaceChild("crownlink_10", CubeListBuilder.create().texOffs(386, 76).addBox(-0.8F, -0.3F, -0.28F, 1.6F, 3.2F, 0.56F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_crownlink_11 = p_crownchain_1.addOrReplaceChild("crownlink_11", CubeListBuilder.create().texOffs(18, 76).addBox(-0.28F, -0.3F, -0.8F, 0.56F, 3.2F, 1.6F), PartPose.offsetAndRotation(0F, 2.6F, 0F, 0F, 0F, 0F));
		PartDefinition p_crownlink_12 = p_crownchain_1.addOrReplaceChild("crownlink_12", CubeListBuilder.create().texOffs(392, 76).addBox(-0.8F, -0.3F, -0.28F, 1.6F, 3.2F, 0.56F), PartPose.offsetAndRotation(0F, 5.2F, 0F, 0F, 0F, 0F));
		PartDefinition p_crownlink_13 = p_crownchain_1.addOrReplaceChild("crownlink_13", CubeListBuilder.create().texOffs(24, 76).addBox(-0.28F, -0.3F, -0.8F, 0.56F, 3.2F, 1.6F), PartPose.offsetAndRotation(0F, 7.8F, 0F, 0F, 0F, 0F));
		PartDefinition p_arm_l = r.getChild("root").getChild("body").addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(354, 0).addBox(-1.8F, 0F, -1.8F, 3.6F, 24F, 3.6F).texOffs(408, 54).addBox(-2.4F, -1.5F, -2.4F, 4.8F, 5F, 4.8F).texOffs(80, 66).addBox(-2.3F, 21.5F, -2.3F, 4.6F, 3.6F, 4.6F), PartPose.offsetAndRotation(10.4F, -31.5F, 0.4F, -0.7882F, 0F, -0.4409F));
		PartDefinition p_sleeve_l0 = p_arm_l.addOrReplaceChild("sleeve_l0", CubeListBuilder.create().texOffs(328, 54).addBox(-1.7F, 0F, -0.5F, 3.4F, 10F, 1F), PartPose.offsetAndRotation(1.9307F, 2F, 1.0547F, 0.3463F, 0F, 0.3383F));
		PartDefinition p_sleeve_l1 = p_arm_l.addOrReplaceChild("sleeve_l1", CubeListBuilder.create().texOffs(152, 37).addBox(-1.7F, 0F, -0.5F, 3.4F, 15F, 1F), PartPose.offsetAndRotation(-1.8788F, 3F, 1.1447F, 0.2952F, 0F, 0.5121F));
		PartDefinition p_sleeve_l2 = p_arm_l.addOrReplaceChild("sleeve_l2", CubeListBuilder.create().texOffs(258, 37).addBox(-1.7F, 0F, -0.5F, 3.4F, 14F, 1F), PartPose.offsetAndRotation(-0.0519F, 4F, -2.1994F, 0.1787F, 0F, 0.3811F));
		PartDefinition p_fore_l = p_arm_l.addOrReplaceChild("fore_l", CubeListBuilder.create().texOffs(386, 0).addBox(-1.5F, 0F, -1.5F, 3F, 22F, 3F).texOffs(428, 54).addBox(-2.5F, 13.5F, -2.5F, 5F, 4.5F, 5F), PartPose.offsetAndRotation(0F, 24F, 0F, -0.32F, 0F, 0.12F));
		PartDefinition p_hand_l = p_fore_l.addOrReplaceChild("hand_l", CubeListBuilder.create().texOffs(448, 54).addBox(-2.7F, 0F, -2F, 5.4F, 5.2F, 4F), PartPose.offsetAndRotation(0F, 22F, 0F, 0F, 0F, 0F));
		PartDefinition p_finger_l0 = p_hand_l.addOrReplaceChild("finger_l0", CubeListBuilder.create().texOffs(356, 54).addBox(-0.65F, 0F, -0.65F, 1.3F, 9F, 1.3F), PartPose.offsetAndRotation(-2.2F, 5F, -0.5F, -0.35F, 0F, -0.2F));
		PartDefinition p_claw_l0 = p_finger_l0.addOrReplaceChild("claw_l0", CubeListBuilder.create().texOffs(136, 66).addBox(-0.6F, 0F, -0.6F, 1.2F, 7F, 1.2F), PartPose.offsetAndRotation(0F, 9F, 0F, -0.5F, 0F, 0F));
		PartDefinition p_tip_l0 = p_claw_l0.addOrReplaceChild("tip_l0", CubeListBuilder.create().texOffs(8, 82).addBox(-0.45F, 0F, -0.45F, 0.9F, 4F, 0.9F), PartPose.offsetAndRotation(0F, 7F, 0F, -0.35F, 0F, 0F));
		PartDefinition p_finger_l1 = p_hand_l.addOrReplaceChild("finger_l1", CubeListBuilder.create().texOffs(72, 54).addBox(-0.65F, 0F, -0.65F, 1.3F, 10F, 1.3F), PartPose.offsetAndRotation(-0.75F, 5F, -0.5F, -0.35F, 0F, -0.07F));
		PartDefinition p_claw_l1 = p_finger_l1.addOrReplaceChild("claw_l1", CubeListBuilder.create().texOffs(144, 66).addBox(-0.6F, 0F, -0.6F, 1.2F, 7F, 1.2F), PartPose.offsetAndRotation(0F, 10F, 0F, -0.5F, 0F, 0F));
		PartDefinition p_tip_l1 = p_claw_l1.addOrReplaceChild("tip_l1", CubeListBuilder.create().texOffs(12, 82).addBox(-0.45F, 0F, -0.45F, 0.9F, 4F, 0.9F), PartPose.offsetAndRotation(0F, 7F, 0F, -0.35F, 0F, 0F));
		PartDefinition p_finger_l2 = p_hand_l.addOrReplaceChild("finger_l2", CubeListBuilder.create().texOffs(80, 54).addBox(-0.65F, 0F, -0.65F, 1.3F, 10F, 1.3F), PartPose.offsetAndRotation(0.75F, 5F, -0.5F, -0.35F, 0F, 0.07F));
		PartDefinition p_claw_l2 = p_finger_l2.addOrReplaceChild("claw_l2", CubeListBuilder.create().texOffs(152, 66).addBox(-0.6F, 0F, -0.6F, 1.2F, 7F, 1.2F), PartPose.offsetAndRotation(0F, 10F, 0F, -0.5F, 0F, 0F));
		PartDefinition p_tip_l2 = p_claw_l2.addOrReplaceChild("tip_l2", CubeListBuilder.create().texOffs(16, 82).addBox(-0.45F, 0F, -0.45F, 0.9F, 4F, 0.9F), PartPose.offsetAndRotation(0F, 7F, 0F, -0.35F, 0F, 0F));
	}

	private static void part3(PartDefinition r) {
		PartDefinition p_finger_l3 = r.getChild("root").getChild("body").getChild("arm_l").getChild("fore_l").getChild("hand_l").addOrReplaceChild("finger_l3", CubeListBuilder.create().texOffs(364, 54).addBox(-0.65F, 0F, -0.65F, 1.3F, 9F, 1.3F), PartPose.offsetAndRotation(2.2F, 5F, -0.5F, -0.35F, 0F, 0.2F));
		PartDefinition p_claw_l3 = p_finger_l3.addOrReplaceChild("claw_l3", CubeListBuilder.create().texOffs(160, 66).addBox(-0.6F, 0F, -0.6F, 1.2F, 7F, 1.2F), PartPose.offsetAndRotation(0F, 9F, 0F, -0.5F, 0F, 0F));
		PartDefinition p_tip_l3 = p_claw_l3.addOrReplaceChild("tip_l3", CubeListBuilder.create().texOffs(20, 82).addBox(-0.45F, 0F, -0.45F, 0.9F, 4F, 0.9F), PartPose.offsetAndRotation(0F, 7F, 0F, -0.35F, 0F, 0F));
		PartDefinition p_thumb_l = r.getChild("root").getChild("body").getChild("arm_l").getChild("fore_l").getChild("hand_l").addOrReplaceChild("thumb_l", CubeListBuilder.create().texOffs(168, 66).addBox(-0.6F, 0F, -0.6F, 1.2F, 7F, 1.2F), PartPose.offsetAndRotation(-2.9F, 3.2F, -1.5F, -0.6F, 0F, -0.7F));
		PartDefinition p_thumbclaw_l = p_thumb_l.addOrReplaceChild("thumbclaw_l", CubeListBuilder.create().texOffs(436, 66).addBox(-0.5F, 0F, -0.5F, 1F, 6F, 1F), PartPose.offsetAndRotation(0F, 7F, 0F, -0.5F, 0F, 0F));
		PartDefinition p_thumbtip_l = p_thumbclaw_l.addOrReplaceChild("thumbtip_l", CubeListBuilder.create().texOffs(280, 82).addBox(-0.4F, 0F, -0.4F, 0.8F, 3F, 0.8F), PartPose.offsetAndRotation(0F, 6F, 0F, -0.3F, 0F, 0F));
		PartDefinition p_wristchain_l = r.getChild("root").getChild("body").getChild("arm_l").getChild("fore_l").addOrReplaceChild("wristchain_l", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, 17.5F, 0F, 0.5789F, 0F, 0.3358F));
		PartDefinition p_wristlink_l0 = p_wristchain_l.addOrReplaceChild("wristlink_l0", CubeListBuilder.create().texOffs(398, 76).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.6F, 0.63F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_l1 = p_wristchain_l.addOrReplaceChild("wristlink_l1", CubeListBuilder.create().texOffs(30, 76).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.6F, 1.8F), PartPose.offsetAndRotation(0F, 3F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_l2 = p_wristchain_l.addOrReplaceChild("wristlink_l2", CubeListBuilder.create().texOffs(404, 76).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.6F, 0.63F), PartPose.offsetAndRotation(0F, 6F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_l3 = p_wristchain_l.addOrReplaceChild("wristlink_l3", CubeListBuilder.create().texOffs(36, 76).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.6F, 1.8F), PartPose.offsetAndRotation(0F, 9F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_l4 = p_wristchain_l.addOrReplaceChild("wristlink_l4", CubeListBuilder.create().texOffs(410, 76).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.6F, 0.63F), PartPose.offsetAndRotation(0F, 12F, 0F, 0F, 0F, 0F));
		PartDefinition p_arm_r = r.getChild("root").getChild("body").addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(370, 0).addBox(-1.8F, 0F, -1.8F, 3.6F, 24F, 3.6F).texOffs(468, 54).addBox(-2.4F, -1.5F, -2.4F, 4.8F, 5F, 4.8F).texOffs(100, 66).addBox(-2.3F, 21.5F, -2.3F, 4.6F, 3.6F, 4.6F), PartPose.offsetAndRotation(-10.4F, -31.5F, 0.4F, -0.7882F, 0F, 0.4409F));
		PartDefinition p_sleeve_r0 = p_arm_r.addOrReplaceChild("sleeve_r0", CubeListBuilder.create().texOffs(338, 54).addBox(-1.7F, 0F, -0.5F, 3.4F, 10F, 1F), PartPose.offsetAndRotation(1.9307F, 2F, 1.0547F, 0.2906F, 0F, -0.5129F));
		PartDefinition p_sleeve_r1 = p_arm_r.addOrReplaceChild("sleeve_r1", CubeListBuilder.create().texOffs(162, 37).addBox(-1.7F, 0F, -0.5F, 3.4F, 15F, 1F), PartPose.offsetAndRotation(-1.8788F, 3F, 1.1447F, 0.3495F, 0F, -0.342F));
		PartDefinition p_sleeve_r2 = p_arm_r.addOrReplaceChild("sleeve_r2", CubeListBuilder.create().texOffs(268, 37).addBox(-1.7F, 0F, -0.5F, 3.4F, 14F, 1F), PartPose.offsetAndRotation(-0.0519F, 4F, -2.1994F, 0.1801F, 0F, -0.3765F));
		PartDefinition p_fore_r = p_arm_r.addOrReplaceChild("fore_r", CubeListBuilder.create().texOffs(398, 0).addBox(-1.5F, 0F, -1.5F, 3F, 22F, 3F).texOffs(488, 54).addBox(-2.5F, 13.5F, -2.5F, 5F, 4.5F, 5F), PartPose.offsetAndRotation(0F, 24F, 0F, -0.32F, 0F, -0.12F));
		PartDefinition p_hand_r = p_fore_r.addOrReplaceChild("hand_r", CubeListBuilder.create().texOffs(0, 66).addBox(-2.7F, 0F, -2F, 5.4F, 5.2F, 4F), PartPose.offsetAndRotation(0F, 22F, 0F, 0F, 0F, 0F));
		PartDefinition p_finger_r0 = p_hand_r.addOrReplaceChild("finger_r0", CubeListBuilder.create().texOffs(372, 54).addBox(-0.65F, 0F, -0.65F, 1.3F, 9F, 1.3F), PartPose.offsetAndRotation(2.2F, 5F, -0.5F, -0.35F, 0F, 0.2F));
		PartDefinition p_claw_r0 = p_finger_r0.addOrReplaceChild("claw_r0", CubeListBuilder.create().texOffs(176, 66).addBox(-0.6F, 0F, -0.6F, 1.2F, 7F, 1.2F), PartPose.offsetAndRotation(0F, 9F, 0F, -0.5F, 0F, 0F));
		PartDefinition p_tip_r0 = p_claw_r0.addOrReplaceChild("tip_r0", CubeListBuilder.create().texOffs(24, 82).addBox(-0.45F, 0F, -0.45F, 0.9F, 4F, 0.9F), PartPose.offsetAndRotation(0F, 7F, 0F, -0.35F, 0F, 0F));
		PartDefinition p_finger_r1 = p_hand_r.addOrReplaceChild("finger_r1", CubeListBuilder.create().texOffs(88, 54).addBox(-0.65F, 0F, -0.65F, 1.3F, 10F, 1.3F), PartPose.offsetAndRotation(0.75F, 5F, -0.5F, -0.35F, 0F, 0.07F));
		PartDefinition p_claw_r1 = p_finger_r1.addOrReplaceChild("claw_r1", CubeListBuilder.create().texOffs(184, 66).addBox(-0.6F, 0F, -0.6F, 1.2F, 7F, 1.2F), PartPose.offsetAndRotation(0F, 10F, 0F, -0.5F, 0F, 0F));
		PartDefinition p_tip_r1 = p_claw_r1.addOrReplaceChild("tip_r1", CubeListBuilder.create().texOffs(28, 82).addBox(-0.45F, 0F, -0.45F, 0.9F, 4F, 0.9F), PartPose.offsetAndRotation(0F, 7F, 0F, -0.35F, 0F, 0F));
		PartDefinition p_finger_r2 = p_hand_r.addOrReplaceChild("finger_r2", CubeListBuilder.create().texOffs(96, 54).addBox(-0.65F, 0F, -0.65F, 1.3F, 10F, 1.3F), PartPose.offsetAndRotation(-0.75F, 5F, -0.5F, -0.35F, 0F, -0.07F));
		PartDefinition p_claw_r2 = p_finger_r2.addOrReplaceChild("claw_r2", CubeListBuilder.create().texOffs(192, 66).addBox(-0.6F, 0F, -0.6F, 1.2F, 7F, 1.2F), PartPose.offsetAndRotation(0F, 10F, 0F, -0.5F, 0F, 0F));
		PartDefinition p_tip_r2 = p_claw_r2.addOrReplaceChild("tip_r2", CubeListBuilder.create().texOffs(32, 82).addBox(-0.45F, 0F, -0.45F, 0.9F, 4F, 0.9F), PartPose.offsetAndRotation(0F, 7F, 0F, -0.35F, 0F, 0F));
		PartDefinition p_finger_r3 = p_hand_r.addOrReplaceChild("finger_r3", CubeListBuilder.create().texOffs(380, 54).addBox(-0.65F, 0F, -0.65F, 1.3F, 9F, 1.3F), PartPose.offsetAndRotation(-2.2F, 5F, -0.5F, -0.35F, 0F, -0.2F));
		PartDefinition p_claw_r3 = p_finger_r3.addOrReplaceChild("claw_r3", CubeListBuilder.create().texOffs(200, 66).addBox(-0.6F, 0F, -0.6F, 1.2F, 7F, 1.2F), PartPose.offsetAndRotation(0F, 9F, 0F, -0.5F, 0F, 0F));
		PartDefinition p_tip_r3 = p_claw_r3.addOrReplaceChild("tip_r3", CubeListBuilder.create().texOffs(36, 82).addBox(-0.45F, 0F, -0.45F, 0.9F, 4F, 0.9F), PartPose.offsetAndRotation(0F, 7F, 0F, -0.35F, 0F, 0F));
		PartDefinition p_thumb_r = p_hand_r.addOrReplaceChild("thumb_r", CubeListBuilder.create().texOffs(208, 66).addBox(-0.6F, 0F, -0.6F, 1.2F, 7F, 1.2F), PartPose.offsetAndRotation(2.9F, 3.2F, -1.5F, -0.6F, 0F, 0.7F));
		PartDefinition p_thumbclaw_r = p_thumb_r.addOrReplaceChild("thumbclaw_r", CubeListBuilder.create().texOffs(440, 66).addBox(-0.5F, 0F, -0.5F, 1F, 6F, 1F), PartPose.offsetAndRotation(0F, 7F, 0F, -0.5F, 0F, 0F));
		PartDefinition p_thumbtip_r = p_thumbclaw_r.addOrReplaceChild("thumbtip_r", CubeListBuilder.create().texOffs(284, 82).addBox(-0.4F, 0F, -0.4F, 0.8F, 3F, 0.8F), PartPose.offsetAndRotation(0F, 6F, 0F, -0.3F, 0F, 0F));
		PartDefinition p_wristchain_r = p_fore_r.addOrReplaceChild("wristchain_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, 17.5F, 0F, 0.5789F, 0F, -0.3358F));
		PartDefinition p_wristlink_r0 = p_wristchain_r.addOrReplaceChild("wristlink_r0", CubeListBuilder.create().texOffs(416, 76).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.6F, 0.63F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_r1 = p_wristchain_r.addOrReplaceChild("wristlink_r1", CubeListBuilder.create().texOffs(42, 76).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.6F, 1.8F), PartPose.offsetAndRotation(0F, 3F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_r2 = p_wristchain_r.addOrReplaceChild("wristlink_r2", CubeListBuilder.create().texOffs(422, 76).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.6F, 0.63F), PartPose.offsetAndRotation(0F, 6F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_r3 = p_wristchain_r.addOrReplaceChild("wristlink_r3", CubeListBuilder.create().texOffs(48, 76).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.6F, 1.8F), PartPose.offsetAndRotation(0F, 9F, 0F, 0F, 0F, 0F));
		PartDefinition p_wristlink_r4 = p_wristchain_r.addOrReplaceChild("wristlink_r4", CubeListBuilder.create().texOffs(428, 76).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.6F, 0.63F), PartPose.offsetAndRotation(0F, 12F, 0F, 0F, 0F, 0F));
		PartDefinition p_wrap_0 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_0", CubeListBuilder.create().texOffs(434, 76).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.8576F, 0.63F), PartPose.offsetAndRotation(8.8F, -33F, -3.4F, -0.0384F, 0F, 0.6327F));
	}

	private static void part4(PartDefinition r) {
		PartDefinition p_wrap_1 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_1", CubeListBuilder.create().texOffs(54, 76).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.8576F, 1.8F), PartPose.offsetAndRotation(6.875F, -30.375F, -3.525F, -0.0384F, 0F, 0.6327F));
		PartDefinition p_wrap_2 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_2", CubeListBuilder.create().texOffs(440, 76).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.8576F, 0.63F), PartPose.offsetAndRotation(4.95F, -27.75F, -3.65F, -0.0384F, 0F, 0.6327F));
		PartDefinition p_wrap_3 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_3", CubeListBuilder.create().texOffs(60, 76).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.8576F, 1.8F), PartPose.offsetAndRotation(3.025F, -25.125F, -3.775F, -0.0384F, 0F, 0.6327F));
		PartDefinition p_wrap_4 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_4", CubeListBuilder.create().texOffs(446, 76).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.8576F, 0.63F), PartPose.offsetAndRotation(1.1F, -22.5F, -3.9F, -0.0384F, 0F, 0.6327F));
		PartDefinition p_wrap_5 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_5", CubeListBuilder.create().texOffs(66, 76).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.8576F, 1.8F), PartPose.offsetAndRotation(-0.825F, -19.875F, -4.025F, -0.0384F, 0F, 0.6327F));
		PartDefinition p_wrap_6 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_6", CubeListBuilder.create().texOffs(452, 76).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.8576F, 0.63F), PartPose.offsetAndRotation(-2.75F, -17.25F, -4.15F, -0.0384F, 0F, 0.6327F));
		PartDefinition p_wrap_7 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_7", CubeListBuilder.create().texOffs(72, 76).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.8576F, 1.8F), PartPose.offsetAndRotation(-4.675F, -14.625F, -4.275F, -0.0384F, 0F, 0.6327F));
		PartDefinition p_wrap_8 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_8", CubeListBuilder.create().texOffs(458, 76).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.5347F, 0.63F), PartPose.offsetAndRotation(-6.6F, -12F, -4.4F, 0.9878F, 0F, 0.3805F));
		PartDefinition p_wrap_9 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_9", CubeListBuilder.create().texOffs(78, 76).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.5347F, 1.8F), PartPose.offsetAndRotation(-7.2F, -10.5F, -1.95F, 0.9878F, 0F, 0.3805F));
		PartDefinition p_wrap_10 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_10", CubeListBuilder.create().texOffs(464, 76).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.5568F, 0.63F), PartPose.offsetAndRotation(-7.8F, -9F, 0.5F, 0.9766F, 0F, -2.705F));
		PartDefinition p_wrap_11 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_11", CubeListBuilder.create().texOffs(84, 76).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.5568F, 1.8F), PartPose.offsetAndRotation(-7.1F, -10.5F, 2.95F, 0.9766F, 0F, -2.705F));
		PartDefinition p_wrap_12 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_12", CubeListBuilder.create().texOffs(470, 76).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.6109F, 0.63F), PartPose.offsetAndRotation(-6.4F, -12F, 5.4F, 0.0166F, 0F, -2.4798F));
		PartDefinition p_wrap_13 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_13", CubeListBuilder.create().texOffs(90, 76).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.6109F, 1.8F), PartPose.offsetAndRotation(-4.55F, -14.375F, 5.45F, 0.0166F, 0F, -2.4798F));
		PartDefinition p_wrap_14 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_14", CubeListBuilder.create().texOffs(476, 76).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.6109F, 0.63F), PartPose.offsetAndRotation(-2.7F, -16.75F, 5.5F, 0.0166F, 0F, -2.4798F));
		PartDefinition p_wrap_15 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_15", CubeListBuilder.create().texOffs(96, 76).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.6109F, 1.8F), PartPose.offsetAndRotation(-0.85F, -19.125F, 5.55F, 0.0166F, 0F, -2.4798F));
		PartDefinition p_wrap_16 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_16", CubeListBuilder.create().texOffs(482, 76).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.6109F, 0.63F), PartPose.offsetAndRotation(1F, -21.5F, 5.6F, 0.0166F, 0F, -2.4798F));
		PartDefinition p_wrap_17 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_17", CubeListBuilder.create().texOffs(102, 76).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.6109F, 1.8F), PartPose.offsetAndRotation(2.85F, -23.875F, 5.65F, 0.0166F, 0F, -2.4798F));
		PartDefinition p_wrap_18 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_18", CubeListBuilder.create().texOffs(488, 76).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.6109F, 0.63F), PartPose.offsetAndRotation(4.7F, -26.25F, 5.7F, 0.0166F, 0F, -2.4798F));
		PartDefinition p_wrap_19 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_19", CubeListBuilder.create().texOffs(108, 76).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.6109F, 1.8F), PartPose.offsetAndRotation(6.55F, -28.625F, 5.75F, 0.0166F, 0F, -2.4798F));
		PartDefinition p_wrap_20 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_20", CubeListBuilder.create().texOffs(494, 76).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 3.2683F, 0.63F), PartPose.offsetAndRotation(8.4F, -31F, 5.8F, -1.1185F, 0F, -2.6012F));
		PartDefinition p_wrap_21 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_21", CubeListBuilder.create().texOffs(114, 76).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 3.2683F, 1.8F), PartPose.offsetAndRotation(9F, -32F, 3.4F, -1.1185F, 0F, -2.6012F));
		PartDefinition p_wrap_22 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_22", CubeListBuilder.create().texOffs(256, 82).addBox(-0.9F, -0.3F, -0.315F, 1.8F, 2.8361F, 0.63F), PartPose.offsetAndRotation(9.6F, -33F, 1F, -1.3909F, 0F, 1.5708F));
		PartDefinition p_wrap_23 = r.getChild("root").getChild("body").addOrReplaceChild("wrap_23", CubeListBuilder.create().texOffs(500, 76).addBox(-0.315F, -0.3F, -0.9F, 0.63F, 2.8361F, 1.8F), PartPose.offsetAndRotation(9.2F, -33F, -1.2F, -1.3909F, 0F, 1.5708F));
		PartDefinition p_sag_0 = r.getChild("root").getChild("body").addOrReplaceChild("sag_0", CubeListBuilder.create().texOffs(424, 66).addBox(-0.85F, -0.3F, -0.2975F, 1.7F, 5.4484F, 0.595F), PartPose.offsetAndRotation(-4.5F, -4F, -3.6F, 0F, 0F, -0.2531F));
		PartDefinition p_sag_1 = r.getChild("root").getChild("body").addOrReplaceChild("sag_1", CubeListBuilder.create().texOffs(430, 66).addBox(-0.2975F, -0.3F, -0.85F, 0.595F, 4.0456F, 1.7F), PartPose.offsetAndRotation(-3.2857F, 0.6939F, -3.6F, 0F, 0F, -0.3602F));
		PartDefinition p_sag_2 = r.getChild("root").getChild("body").addOrReplaceChild("sag_2", CubeListBuilder.create().texOffs(262, 82).addBox(-0.85F, -0.3F, -0.2975F, 1.7F, 2.7342F, 0.595F), PartPose.offsetAndRotation(-2.0714F, 3.9184F, -3.6F, 0F, 0F, -0.6052F));
		PartDefinition p_sag_3 = r.getChild("root").getChild("body").addOrReplaceChild("sag_3", CubeListBuilder.create().texOffs(268, 82).addBox(-0.2975F, -0.3F, -0.85F, 0.595F, 1.8474F, 1.7F), PartPose.offsetAndRotation(-0.8571F, 5.6735F, -3.6F, 0F, 0F, -1.3397F));
		PartDefinition p_sag_4 = r.getChild("root").getChild("body").addOrReplaceChild("sag_4", CubeListBuilder.create().texOffs(274, 82).addBox(-0.85F, -0.3F, -0.2975F, 1.7F, 2.2958F, 0.595F), PartPose.offsetAndRotation(0.3571F, 5.9592F, -3.6F, 0F, 0F, -2.3434F));
		PartDefinition p_sag_5 = r.getChild("root").getChild("body").addOrReplaceChild("sag_5", CubeListBuilder.create().texOffs(120, 76).addBox(-0.2975F, -0.3F, -0.85F, 0.595F, 3.5177F, 1.7F), PartPose.offsetAndRotation(1.5714F, 4.7755F, -3.6F, 0F, 0F, -2.7124F));
		PartDefinition p_sag_6 = r.getChild("root").getChild("body").addOrReplaceChild("sag_6", CubeListBuilder.create().texOffs(126, 76).addBox(-0.85F, -0.3F, -0.2975F, 1.7F, 4.8976F, 0.595F), PartPose.offsetAndRotation(2.7857F, 2.1224F, -3.6F, 0F, 0F, -2.8551F));
	}

	/** Parts that move together: chain. */
	public static final String[] CHAIN = {"crownchain_0", "crownchain_1", "wristchain_l", "wristchain_r"};

	/** Parts that move together: shroud. */
	public static final String[] SHROUD = {"shroud_0", "shroud_1", "shroud_2", "shroud_3", "shroud_4", "shroud_5", "shroud_6", "shroud_7", "shroud_8", "shroud_9", "shroud_10", "shroud_11", "shroud_12", "shroud_13", "shroud_14", "shroud_sh0", "shroud_sh1"};

	/** Parts that move together: sleeve. */
	public static final String[] SLEEVE = {"sleeve_l0", "sleeve_l1", "sleeve_l2", "sleeve_r0", "sleeve_r1", "sleeve_r2"};

	/** The animated parts, by name. */
	public static ModelPart find(ModelPart root, String name) {
		return switch (name) {
			case "root" -> root.getChild("root");
			case "tail_0" -> root.getChild("root").getChild("tail_0");
			case "tail_1" -> root.getChild("root").getChild("tail_0").getChild("tail_1");
			case "tail_2" -> root.getChild("root").getChild("tail_0").getChild("tail_1").getChild("tail_2");
			case "tail_3" -> root.getChild("root").getChild("tail_0").getChild("tail_1").getChild("tail_2").getChild("tail_3");
			case "body" -> root.getChild("root").getChild("body");
			case "shroud_0" -> root.getChild("root").getChild("body").getChild("shroud_0");
			case "shroud_1" -> root.getChild("root").getChild("body").getChild("shroud_1");
			case "shroud_2" -> root.getChild("root").getChild("body").getChild("shroud_2");
			case "shroud_3" -> root.getChild("root").getChild("body").getChild("shroud_3");
			case "shroud_4" -> root.getChild("root").getChild("body").getChild("shroud_4");
			case "shroud_5" -> root.getChild("root").getChild("body").getChild("shroud_5");
			case "shroud_6" -> root.getChild("root").getChild("body").getChild("shroud_6");
			case "shroud_7" -> root.getChild("root").getChild("body").getChild("shroud_7");
			case "shroud_8" -> root.getChild("root").getChild("body").getChild("shroud_8");
			case "shroud_9" -> root.getChild("root").getChild("body").getChild("shroud_9");
			case "shroud_10" -> root.getChild("root").getChild("body").getChild("shroud_10");
			case "shroud_11" -> root.getChild("root").getChild("body").getChild("shroud_11");
			case "shroud_12" -> root.getChild("root").getChild("body").getChild("shroud_12");
			case "shroud_13" -> root.getChild("root").getChild("body").getChild("shroud_13");
			case "shroud_14" -> root.getChild("root").getChild("body").getChild("shroud_14");
			case "shroud_sh0" -> root.getChild("root").getChild("body").getChild("shroud_sh0");
			case "shroud_sh1" -> root.getChild("root").getChild("body").getChild("shroud_sh1");
			case "head" -> root.getChild("root").getChild("body").getChild("head");
			case "crown" -> root.getChild("root").getChild("body").getChild("head").getChild("crown");
			case "crownchain_0" -> root.getChild("root").getChild("body").getChild("head").getChild("crown").getChild("crownchain_0");
			case "crownchain_1" -> root.getChild("root").getChild("body").getChild("head").getChild("crown").getChild("crownchain_1");
			case "arm_l" -> root.getChild("root").getChild("body").getChild("arm_l");
			case "sleeve_l0" -> root.getChild("root").getChild("body").getChild("arm_l").getChild("sleeve_l0");
			case "sleeve_l1" -> root.getChild("root").getChild("body").getChild("arm_l").getChild("sleeve_l1");
			case "sleeve_l2" -> root.getChild("root").getChild("body").getChild("arm_l").getChild("sleeve_l2");
			case "fore_l" -> root.getChild("root").getChild("body").getChild("arm_l").getChild("fore_l");
			case "hand_l" -> root.getChild("root").getChild("body").getChild("arm_l").getChild("fore_l").getChild("hand_l");
			case "wristchain_l" -> root.getChild("root").getChild("body").getChild("arm_l").getChild("fore_l").getChild("wristchain_l");
			case "arm_r" -> root.getChild("root").getChild("body").getChild("arm_r");
			case "sleeve_r0" -> root.getChild("root").getChild("body").getChild("arm_r").getChild("sleeve_r0");
			case "sleeve_r1" -> root.getChild("root").getChild("body").getChild("arm_r").getChild("sleeve_r1");
			case "sleeve_r2" -> root.getChild("root").getChild("body").getChild("arm_r").getChild("sleeve_r2");
			case "fore_r" -> root.getChild("root").getChild("body").getChild("arm_r").getChild("fore_r");
			case "hand_r" -> root.getChild("root").getChild("body").getChild("arm_r").getChild("fore_r").getChild("hand_r");
			case "wristchain_r" -> root.getChild("root").getChild("body").getChild("arm_r").getChild("fore_r").getChild("wristchain_r");
			default -> throw new IllegalArgumentException("no animated part " + name);
		};
	}
}
