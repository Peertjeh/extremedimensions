package com.extremedimensions.client.entity;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Geometry of Lurker, written by tools/elders (do not edit by hand: run the generator). */
public final class LurkerGeometry {
	private LurkerGeometry() {}

	public static LayerDefinition create() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part0(r);
		part1(r);
		return LayerDefinition.create(mesh, 256, 128);
	}

	private static void part0(PartDefinition r) {
		PartDefinition p_root = r.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
		PartDefinition p_leg_l = p_root.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(104, 32).addBox(-3F, 0F, -3F, 6F, 13F, 6F), PartPose.offsetAndRotation(5F, -13F, 3F, 0F, 0F, 0F));
		PartDefinition p_leg_r = p_root.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(128, 32).addBox(-3F, 0F, -3F, 6F, 13F, 6F), PartPose.offsetAndRotation(-5F, -13F, 3F, 0F, 0F, 0F));
		PartDefinition p_hips = p_root.addOrReplaceChild("hips", CubeListBuilder.create().texOffs(0, 0).addBox(-9F, -20F, -6F, 18F, 20F, 12F), PartPose.offsetAndRotation(0F, -13F, 3F, 0.62F, 0F, 0F));
		PartDefinition p_hump = p_hips.addOrReplaceChild("hump", CubeListBuilder.create().texOffs(154, 0).addBox(-10F, -12F, -2F, 20F, 14F, 8F), PartPose.offsetAndRotation(0F, -14F, 4F, -0.25F, 0F, 0F));
		PartDefinition p_skirt0 = p_hips.addOrReplaceChild("skirt0", CubeListBuilder.create().texOffs(54, 69).addBox(-2F, 0F, -0.5F, 4F, 9F, 1F), PartPose.offsetAndRotation(-7F, -1F, -6F, 0F, 0F, 0F));
		PartDefinition p_skirt1 = p_hips.addOrReplaceChild("skirt1", CubeListBuilder.create().texOffs(232, 53).addBox(-2.5F, 0F, -0.5F, 5F, 11F, 1F), PartPose.offsetAndRotation(-2.5F, -1F, -6.5F, 0F, 0F, 0F));
		PartDefinition p_skirt2 = p_hips.addOrReplaceChild("skirt2", CubeListBuilder.create().texOffs(34, 69).addBox(-2F, 0F, -0.5F, 4F, 10F, 1F), PartPose.offsetAndRotation(3F, -1F, -6F, 0F, 0F, 0F));
		PartDefinition p_skirt3 = p_hips.addOrReplaceChild("skirt3", CubeListBuilder.create().texOffs(90, 69).addBox(-2F, 0F, -0.5F, 4F, 8F, 1F), PartPose.offsetAndRotation(7.5F, -1F, -3F, 0F, 0F, 0F));
		PartDefinition p_skirt4 = p_hips.addOrReplaceChild("skirt4", CubeListBuilder.create().texOffs(244, 53).addBox(-2.5F, 0F, -0.5F, 5F, 11F, 1F), PartPose.offsetAndRotation(-7F, -1F, 5.5F, 0F, 0F, 0F));
		PartDefinition p_skirt5 = p_hips.addOrReplaceChild("skirt5", CubeListBuilder.create().texOffs(166, 53).addBox(-2F, 0F, -0.5F, 4F, 12F, 1F), PartPose.offsetAndRotation(-2F, -1F, 6.5F, 0F, 0F, 0F));
		PartDefinition p_skirt6 = p_hips.addOrReplaceChild("skirt6", CubeListBuilder.create().texOffs(44, 69).addBox(-2F, 0F, -0.5F, 4F, 10F, 1F), PartPose.offsetAndRotation(3.5F, -1F, 6F, 0F, 0F, 0F));
		PartDefinition p_skirt7 = p_hips.addOrReplaceChild("skirt7", CubeListBuilder.create().texOffs(78, 69).addBox(-2.5F, 0F, -0.5F, 5F, 8F, 1F), PartPose.offsetAndRotation(-8F, -1F, 0F, 0F, 0F, 0F));
		PartDefinition p_skirt8 = p_hips.addOrReplaceChild("skirt8", CubeListBuilder.create().texOffs(64, 69).addBox(-2F, 0F, -0.5F, 4F, 9F, 1F), PartPose.offsetAndRotation(8F, -1F, 1F, 0F, 0F, 0F));
		PartDefinition p_cape = p_hips.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(60, 0).addBox(-10F, 0F, 0F, 20F, 24F, 2F), PartPose.offsetAndRotation(0F, -19F, 6F, 0F, 0F, 0F));
		PartDefinition p_mantle_l = p_hips.addOrReplaceChild("mantle_l", CubeListBuilder.create().texOffs(200, 32).addBox(-3F, -2F, -5F, 8F, 5F, 11F), PartPose.offsetAndRotation(8F, -20F, -1F, 0F, 0F, 0.35F));
		PartDefinition p_mantle_r = p_hips.addOrReplaceChild("mantle_r", CubeListBuilder.create().texOffs(0, 53).addBox(-5F, -2F, -5F, 8F, 5F, 11F), PartPose.offsetAndRotation(-8F, -20F, -1F, 0F, 0F, -0.35F));
		PartDefinition p_neck = p_hips.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(54, 53).addBox(-3.5F, -7F, -3.5F, 7F, 8F, 7F), PartPose.offsetAndRotation(0F, -19F, -3F, 0.35F, 0F, 0F));
		PartDefinition p_head = p_neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(104, 0).addBox(-6F, -11F, -10F, 12F, 12F, 13F), PartPose.offsetAndRotation(0F, -6F, -1F, -0.77F, 0F, 0F));
		PartDefinition p_hood = p_head.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 32).addBox(-7F, -12.5F, -8F, 14F, 7F, 14F).texOffs(116, 69).addBox(-7.2F, -12.7F, -10.4F, 14.4F, 1.6F, 2.6F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_tmask = p_head.addOrReplaceChild("tmask", CubeListBuilder.create().texOffs(12, 69).addBox(-5F, -10F, -10.45F, 10F, 10F, 1F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_tbrow_l = p_head.addOrReplaceChild("tbrow_l", CubeListBuilder.create().texOffs(24, 81).addBox(-1.45F, -0.25F, -0.3F, 3F, 1F, 1F), PartPose.offsetAndRotation(2.45F, -6.75F, -10.5F, 0F, 0F, -0.22F));
		PartDefinition p_teye_l = p_head.addOrReplaceChild("teye_l", CubeListBuilder.create().texOffs(248, 69).addBox(-1.35F, -0.95F, -0.15F, 3F, 2F, 1F), PartPose.offsetAndRotation(2.35F, -5.05F, -10.55F, 0F, 0F, 0F));
		PartDefinition p_tfray_l0 = p_head.addOrReplaceChild("tfray_l0", CubeListBuilder.create().texOffs(108, 69).addBox(-0.7F, -0.1F, -0.3F, 1F, 5F, 1F), PartPose.offsetAndRotation(5.7F, -10.9F, -9.6F, 0.05F, 0F, -0.08F));
		PartDefinition p_tfray_l1 = p_head.addOrReplaceChild("tfray_l1", CubeListBuilder.create().texOffs(152, 69).addBox(-0.15F, -0.1F, -0.3F, 1F, 4F, 1F), PartPose.offsetAndRotation(6.15F, -10.9F, -8.7F, 0.05F, 0F, -0.08F));
		PartDefinition p_tfray_l2 = p_head.addOrReplaceChild("tfray_l2", CubeListBuilder.create().texOffs(100, 69).addBox(-0.6F, -0.1F, -0.3F, 1F, 6F, 1F), PartPose.offsetAndRotation(6.6F, -10.9F, -7.8F, 0.05F, 0F, -0.08F));
		PartDefinition p_tbrow_r = p_head.addOrReplaceChild("tbrow_r", CubeListBuilder.create().texOffs(32, 81).addBox(-1.55F, -0.25F, -0.3F, 3F, 1F, 1F), PartPose.offsetAndRotation(-2.45F, -6.75F, -10.5F, 0F, 0F, 0.22F));
		PartDefinition p_teye_r = p_head.addOrReplaceChild("teye_r", CubeListBuilder.create().texOffs(0, 81).addBox(-1.65F, -0.95F, -0.15F, 3F, 2F, 1F), PartPose.offsetAndRotation(-2.35F, -5.05F, -10.55F, 0F, 0F, 0F));
		PartDefinition p_tfray_r0 = p_head.addOrReplaceChild("tfray_r0", CubeListBuilder.create().texOffs(156, 69).addBox(-0.3F, -0.1F, -0.3F, 1F, 4F, 1F), PartPose.offsetAndRotation(-5.7F, -10.9F, -9.6F, 0.05F, 0F, 0.08F));
		PartDefinition p_tfray_r1 = p_head.addOrReplaceChild("tfray_r1", CubeListBuilder.create().texOffs(104, 69).addBox(0.15F, -0.1F, -0.3F, 1F, 6F, 1F), PartPose.offsetAndRotation(-6.15F, -10.9F, -8.7F, 0.05F, 0F, 0.08F));
		PartDefinition p_tfray_r2 = p_head.addOrReplaceChild("tfray_r2", CubeListBuilder.create().texOffs(112, 69).addBox(-0.4F, -0.1F, -0.3F, 1F, 5F, 1F), PartPose.offsetAndRotation(-6.6F, -10.9F, -7.8F, 0.05F, 0F, 0.08F));
		PartDefinition p_tfringe_0 = p_head.addOrReplaceChild("tfringe_0", CubeListBuilder.create().texOffs(40, 81).addBox(-0.8F, 0.15F, -0.25F, 1F, 1F, 1F), PartPose.offsetAndRotation(-4.2F, -11.15F, -10.5F, 0.15F, 0F, 0F));
		PartDefinition p_tfringe_1 = p_head.addOrReplaceChild("tfringe_1", CubeListBuilder.create().texOffs(8, 81).addBox(-0.2F, 0.15F, -0.25F, 1F, 2F, 1F), PartPose.offsetAndRotation(-2.8F, -11.15F, -10.5F, 0.15F, 0F, 0F));
		PartDefinition p_tfringe_2 = p_head.addOrReplaceChild("tfringe_2", CubeListBuilder.create().texOffs(12, 81).addBox(-0.6F, 0.15F, -0.25F, 1F, 2F, 1F), PartPose.offsetAndRotation(-1.4F, -11.15F, -10.5F, 0.15F, 0F, 0F));
		PartDefinition p_tfringe_3 = p_head.addOrReplaceChild("tfringe_3", CubeListBuilder.create().texOffs(44, 81).addBox(0F, 0.15F, -0.25F, 1F, 1F, 1F), PartPose.offsetAndRotation(0F, -11.15F, -10.5F, 0.15F, 0F, 0F));
		PartDefinition p_tfringe_4 = p_head.addOrReplaceChild("tfringe_4", CubeListBuilder.create().texOffs(48, 81).addBox(-0.4F, 0.15F, -0.25F, 1F, 1F, 1F), PartPose.offsetAndRotation(1.4F, -11.15F, -10.5F, 0.15F, 0F, 0F));
		PartDefinition p_tfringe_5 = p_head.addOrReplaceChild("tfringe_5", CubeListBuilder.create().texOffs(16, 81).addBox(-0.8F, 0.15F, -0.25F, 1F, 2F, 1F), PartPose.offsetAndRotation(2.8F, -11.15F, -10.5F, 0.15F, 0F, 0F));
		PartDefinition p_tfringe_6 = p_head.addOrReplaceChild("tfringe_6", CubeListBuilder.create().texOffs(20, 81).addBox(-0.2F, 0.15F, -0.25F, 1F, 2F, 1F), PartPose.offsetAndRotation(4.2F, -11.15F, -10.5F, 0.15F, 0F, 0F));
		PartDefinition p_tendril0 = p_head.addOrReplaceChild("tendril0", CubeListBuilder.create().texOffs(90, 53).addBox(-0.5F, 0F, -0.5F, 1F, 14F, 1F), PartPose.offsetAndRotation(-5F, 1F, -8F, 0F, 0F, 0F));
		PartDefinition p_drip0 = p_tendril0.addOrReplaceChild("drip0", CubeListBuilder.create().texOffs(168, 69).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 14F, 0F, 0F, 0F, 0F));
	}

	private static void part1(PartDefinition r) {
		PartDefinition p_tendril1 = r.getChild("root").getChild("hips").getChild("neck").getChild("head").addOrReplaceChild("tendril1", CubeListBuilder.create().texOffs(0, 69).addBox(-1F, 0F, -1F, 2F, 10F, 2F), PartPose.offsetAndRotation(-3F, 1F, -9.5F, 0F, 0F, 0F));
		PartDefinition p_drip1 = p_tendril1.addOrReplaceChild("drip1", CubeListBuilder.create().texOffs(176, 69).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 10F, 0F, 0F, 0F, 0F));
		PartDefinition p_tendril2 = r.getChild("root").getChild("hips").getChild("neck").getChild("head").addOrReplaceChild("tendril2", CubeListBuilder.create().texOffs(152, 32).addBox(-0.5F, 0F, -0.5F, 1F, 18F, 1F), PartPose.offsetAndRotation(-1F, 1F, -9F, 0F, 0F, 0F));
		PartDefinition p_drip2 = p_tendril2.addOrReplaceChild("drip2", CubeListBuilder.create().texOffs(184, 69).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 18F, 0F, 0F, 0F, 0F));
		PartDefinition p_tendril3 = r.getChild("root").getChild("hips").getChild("neck").getChild("head").addOrReplaceChild("tendril3", CubeListBuilder.create().texOffs(82, 53).addBox(-1F, 0F, -1F, 2F, 13F, 2F), PartPose.offsetAndRotation(1.5F, 1F, -9.5F, 0F, 0F, 0F));
		PartDefinition p_drip3 = p_tendril3.addOrReplaceChild("drip3", CubeListBuilder.create().texOffs(192, 69).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 13F, 0F, 0F, 0F, 0F));
		PartDefinition p_tendril4 = r.getChild("root").getChild("hips").getChild("neck").getChild("head").addOrReplaceChild("tendril4", CubeListBuilder.create().texOffs(196, 32).addBox(-0.5F, 0F, -0.5F, 1F, 16F, 1F), PartPose.offsetAndRotation(3.5F, 1F, -9F, 0F, 0F, 0F));
		PartDefinition p_drip4 = p_tendril4.addOrReplaceChild("drip4", CubeListBuilder.create().texOffs(200, 69).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 16F, 0F, 0F, 0F, 0F));
		PartDefinition p_tendril5 = r.getChild("root").getChild("hips").getChild("neck").getChild("head").addOrReplaceChild("tendril5", CubeListBuilder.create().texOffs(8, 69).addBox(-0.5F, 0F, -0.5F, 1F, 11F, 1F), PartPose.offsetAndRotation(5F, 1F, -7.5F, 0F, 0F, 0F));
		PartDefinition p_drip5 = p_tendril5.addOrReplaceChild("drip5", CubeListBuilder.create().texOffs(208, 69).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 11F, 0F, 0F, 0F, 0F));
		PartDefinition p_tendril6 = r.getChild("root").getChild("hips").getChild("neck").getChild("head").addOrReplaceChild("tendril6", CubeListBuilder.create().texOffs(74, 69).addBox(-0.5F, 0F, -0.5F, 1F, 9F, 1F), PartPose.offsetAndRotation(-2F, 1F, -6F, 0F, 0F, 0F));
		PartDefinition p_drip6 = p_tendril6.addOrReplaceChild("drip6", CubeListBuilder.create().texOffs(216, 69).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 9F, 0F, 0F, 0F, 0F));
		PartDefinition p_tendril7 = r.getChild("root").getChild("hips").getChild("neck").getChild("head").addOrReplaceChild("tendril7", CubeListBuilder.create().texOffs(176, 53).addBox(-0.5F, 0F, -0.5F, 1F, 12F, 1F), PartPose.offsetAndRotation(2F, 1F, -6F, 0F, 0F, 0F));
		PartDefinition p_drip7 = p_tendril7.addOrReplaceChild("drip7", CubeListBuilder.create().texOffs(224, 69).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 12F, 0F, 0F, 0F, 0F));
		PartDefinition p_arm_l = r.getChild("root").getChild("hips").addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(56, 32).addBox(-3F, -2F, -3F, 6F, 14F, 6F), PartPose.offsetAndRotation(10F, -18F, -2F, 0F, 0F, 0F));
		PartDefinition p_rag_l = p_arm_l.addOrReplaceChild("rag_l", CubeListBuilder.create().texOffs(38, 53).addBox(-0.5F, 0F, -1.5F, 1F, 13F, 3F), PartPose.offsetAndRotation(3F, 3F, 0F, 0F, 0F, 0F));
		PartDefinition p_forearm_l = p_arm_l.addOrReplaceChild("forearm_l", CubeListBuilder.create().texOffs(156, 32).addBox(-2.5F, 0F, -2.5F, 5F, 13F, 5F), PartPose.offsetAndRotation(0F, 11F, 0F, 0F, 0F, 0F));
		PartDefinition p_hand_l = p_forearm_l.addOrReplaceChild("hand_l", CubeListBuilder.create().texOffs(180, 53).addBox(-3F, 0F, -4.5F, 6F, 5F, 7F).texOffs(232, 69).addBox(-2.5F, 4.5F, -4.5F, 1F, 3F, 1F).texOffs(160, 69).addBox(-0.5F, 4.5F, -5F, 1F, 4F, 1F).texOffs(236, 69).addBox(1.5F, 4.5F, -4.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(0F, 12F, 0F, 0F, 0F, 0F));
		PartDefinition p_claw_l0 = p_hand_l.addOrReplaceChild("claw_l0", CubeListBuilder.create().texOffs(94, 53).addBox(-1.28F, -0.8F, -1.28F, 2.56F, 10.4F, 2.56F), PartPose.offsetAndRotation(-2F, 7.5F, -4F, -0.35F, 0F, -0.08F));
		PartDefinition p_claw_l1 = p_hand_l.addOrReplaceChild("claw_l1", CubeListBuilder.create().texOffs(106, 53).addBox(-1.28F, -0.8F, -1.28F, 2.56F, 10.4F, 2.56F), PartPose.offsetAndRotation(0F, 8.5F, -4.5F, -0.35F, 0F, 0F));
		PartDefinition p_claw_l2 = p_hand_l.addOrReplaceChild("claw_l2", CubeListBuilder.create().texOffs(118, 53).addBox(-1.28F, -0.8F, -1.28F, 2.56F, 10.4F, 2.56F), PartPose.offsetAndRotation(2F, 7.5F, -4F, -0.35F, 0F, 0.08F));
		PartDefinition p_arm_r = r.getChild("root").getChild("hips").addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(80, 32).addBox(-3F, -2F, -3F, 6F, 14F, 6F), PartPose.offsetAndRotation(-10F, -18F, -2F, 0F, 0F, 0F));
		PartDefinition p_rag_r = p_arm_r.addOrReplaceChild("rag_r", CubeListBuilder.create().texOffs(46, 53).addBox(-0.5F, 0F, -1.5F, 1F, 13F, 3F), PartPose.offsetAndRotation(-3F, 3F, 0F, 0F, 0F, 0F));
		PartDefinition p_forearm_r = p_arm_r.addOrReplaceChild("forearm_r", CubeListBuilder.create().texOffs(176, 32).addBox(-2.5F, 0F, -2.5F, 5F, 13F, 5F), PartPose.offsetAndRotation(0F, 11F, 0F, 0F, 0F, 0F));
		PartDefinition p_hand_r = p_forearm_r.addOrReplaceChild("hand_r", CubeListBuilder.create().texOffs(206, 53).addBox(-3F, 0F, -4.5F, 6F, 5F, 7F).texOffs(240, 69).addBox(-2.5F, 4.5F, -4.5F, 1F, 3F, 1F).texOffs(164, 69).addBox(-0.5F, 4.5F, -5F, 1F, 4F, 1F).texOffs(244, 69).addBox(1.5F, 4.5F, -4.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(0F, 12F, 0F, 0F, 0F, 0F));
		PartDefinition p_claw_r0 = p_hand_r.addOrReplaceChild("claw_r0", CubeListBuilder.create().texOffs(130, 53).addBox(-1.28F, -0.8F, -1.28F, 2.56F, 10.4F, 2.56F), PartPose.offsetAndRotation(-2F, 7.5F, -4F, -0.35F, 0F, -0.08F));
		PartDefinition p_claw_r1 = p_hand_r.addOrReplaceChild("claw_r1", CubeListBuilder.create().texOffs(142, 53).addBox(-1.28F, -0.8F, -1.28F, 2.56F, 10.4F, 2.56F), PartPose.offsetAndRotation(0F, 8.5F, -4.5F, -0.35F, 0F, 0F));
		PartDefinition p_claw_r2 = p_hand_r.addOrReplaceChild("claw_r2", CubeListBuilder.create().texOffs(154, 53).addBox(-1.28F, -0.8F, -1.28F, 2.56F, 10.4F, 2.56F), PartPose.offsetAndRotation(2F, 7.5F, -4F, -0.35F, 0F, 0.08F));
	}

	/** Parts that move together: fine. */
	public static final String[] FINE = {"claw_l0", "claw_l1", "claw_l2", "claw_r0", "claw_r1", "claw_r2"};

	/** How much the fine parts are shrunk at run time (their boxes are built this much larger, for finer pixels). */
	public static final float FINE_SCALE = 0.25F;

	/** The animated parts, by name. */
	public static ModelPart find(ModelPart root, String name) {
		return switch (name) {
			case "tmask" -> root.getChild("root").getChild("hips").getChild("neck").getChild("head").getChild("tmask");
			case "tbrow_l" -> root.getChild("root").getChild("hips").getChild("neck").getChild("head").getChild("tbrow_l");
			case "teye_l" -> root.getChild("root").getChild("hips").getChild("neck").getChild("head").getChild("teye_l");
			case "tfray_l0" -> root.getChild("root").getChild("hips").getChild("neck").getChild("head").getChild("tfray_l0");
			case "tfray_l1" -> root.getChild("root").getChild("hips").getChild("neck").getChild("head").getChild("tfray_l1");
			case "tfray_l2" -> root.getChild("root").getChild("hips").getChild("neck").getChild("head").getChild("tfray_l2");
			case "tbrow_r" -> root.getChild("root").getChild("hips").getChild("neck").getChild("head").getChild("tbrow_r");
			case "teye_r" -> root.getChild("root").getChild("hips").getChild("neck").getChild("head").getChild("teye_r");
			case "tfray_r0" -> root.getChild("root").getChild("hips").getChild("neck").getChild("head").getChild("tfray_r0");
			case "tfray_r1" -> root.getChild("root").getChild("hips").getChild("neck").getChild("head").getChild("tfray_r1");
			case "tfray_r2" -> root.getChild("root").getChild("hips").getChild("neck").getChild("head").getChild("tfray_r2");
			case "tfringe_0" -> root.getChild("root").getChild("hips").getChild("neck").getChild("head").getChild("tfringe_0");
			case "tfringe_1" -> root.getChild("root").getChild("hips").getChild("neck").getChild("head").getChild("tfringe_1");
			case "tfringe_2" -> root.getChild("root").getChild("hips").getChild("neck").getChild("head").getChild("tfringe_2");
			case "tfringe_3" -> root.getChild("root").getChild("hips").getChild("neck").getChild("head").getChild("tfringe_3");
			case "tfringe_4" -> root.getChild("root").getChild("hips").getChild("neck").getChild("head").getChild("tfringe_4");
			case "tfringe_5" -> root.getChild("root").getChild("hips").getChild("neck").getChild("head").getChild("tfringe_5");
			case "tfringe_6" -> root.getChild("root").getChild("hips").getChild("neck").getChild("head").getChild("tfringe_6");
			case "claw_l0" -> root.getChild("root").getChild("hips").getChild("arm_l").getChild("forearm_l").getChild("hand_l").getChild("claw_l0");
			case "claw_l1" -> root.getChild("root").getChild("hips").getChild("arm_l").getChild("forearm_l").getChild("hand_l").getChild("claw_l1");
			case "claw_l2" -> root.getChild("root").getChild("hips").getChild("arm_l").getChild("forearm_l").getChild("hand_l").getChild("claw_l2");
			case "claw_r0" -> root.getChild("root").getChild("hips").getChild("arm_r").getChild("forearm_r").getChild("hand_r").getChild("claw_r0");
			case "claw_r1" -> root.getChild("root").getChild("hips").getChild("arm_r").getChild("forearm_r").getChild("hand_r").getChild("claw_r1");
			case "claw_r2" -> root.getChild("root").getChild("hips").getChild("arm_r").getChild("forearm_r").getChild("hand_r").getChild("claw_r2");
			default -> throw new IllegalArgumentException("no animated part " + name);
		};
	}
}
