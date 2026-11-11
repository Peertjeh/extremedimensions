package com.extremedimensions.client.entity.elder;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Geometry of PaleHauler, written by tools/elders (do not edit by hand: run the generator). */
public final class PaleHaulerGeometry {
	private PaleHaulerGeometry() {}

	public static LayerDefinition create() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part0(r);
		part1(r);
		return LayerDefinition.create(mesh, 256, 128);
	}

	private static void part0(PartDefinition r) {
		PartDefinition p_root = r.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
		PartDefinition p_thigh_l = p_root.addOrReplaceChild("thigh_l", CubeListBuilder.create().texOffs(208, 0).addBox(-1.5F, -0.5F, -1.5F, 3F, 11.25F, 3F), PartPose.offsetAndRotation(2.3F, -22.5714F, 1.5F, -0.2F, 0F, -0.03F));
		PartDefinition p_shin_l = p_thigh_l.addOrReplaceChild("shin_l", CubeListBuilder.create().texOffs(220, 0).addBox(-1.25F, -0.5F, -1.25F, 2.5F, 11.25F, 2.5F), PartPose.offsetAndRotation(0F, 10.75F, 0F, 0.4F, 0F, 0.03F));
		PartDefinition p_foot_l = p_shin_l.addOrReplaceChild("foot_l", CubeListBuilder.create().texOffs(118, 54).addBox(-1.5F, -0.25F, -3.75F, 3F, 1.75F, 5F), PartPose.offsetAndRotation(0F, 10.75F, 0F, -0.2F, 0F, 0F));
		PartDefinition p_thigh_r = p_root.addOrReplaceChild("thigh_r", CubeListBuilder.create().texOffs(232, 0).addBox(-1.5F, -0.5F, -1.5F, 3F, 11.25F, 3F), PartPose.offsetAndRotation(-2.3F, -22.5714F, 1.5F, -0.2F, 0F, 0.03F));
		PartDefinition p_shin_r = p_thigh_r.addOrReplaceChild("shin_r", CubeListBuilder.create().texOffs(244, 0).addBox(-1.25F, -0.5F, -1.25F, 2.5F, 11.25F, 2.5F), PartPose.offsetAndRotation(0F, 10.75F, 0F, 0.4F, 0F, -0.03F));
		PartDefinition p_foot_r = p_shin_r.addOrReplaceChild("foot_r", CubeListBuilder.create().texOffs(134, 54).addBox(-1.5F, -0.25F, -3.75F, 3F, 1.75F, 5F), PartPose.offsetAndRotation(0F, 10.75F, 0F, -0.2F, 0F, 0F));
		PartDefinition p_body = p_root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(180, 42).addBox(-3.5F, -4F, -2F, 7F, 5F, 4F), PartPose.offsetAndRotation(0F, -22.5714F, 1.5F, 0F, 0F, 0F));
		PartDefinition p_belly = p_body.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(68, 54).addBox(-2.5F, -4.25F, -1.5F, 5F, 4.75F, 3F), PartPose.offsetAndRotation(0F, -4F, 0F, 0.12F, 0F, 0F));
		PartDefinition p_chest = p_belly.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(220, 27).addBox(-4F, -7F, -2F, 8F, 7.5F, 4F), PartPose.offsetAndRotation(0F, -4F, 0F, 0.22F, 0F, 0F));
		PartDefinition p_neck = p_chest.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(84, 54).addBox(-1.25F, -3.6F, -1.25F, 2.5F, 4.1F, 2.5F), PartPose.offsetAndRotation(0F, -6.8F, -0.6F, 0.55F, 0F, 0F));
		PartDefinition p_arm_l = p_chest.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(136, 0).addBox(-1.5F, -1.5F, -1.5F, 3F, 13.5F, 3F), PartPose.offsetAndRotation(5.6F, -6F, 0.2F, -0.42F, 0F, -0.14F));
		PartDefinition p_fore_l = p_arm_l.addOrReplaceChild("fore_l", CubeListBuilder.create().texOffs(8, 27).addBox(-1.25F, -0.5F, -1.25F, 2.5F, 11F, 2.5F), PartPose.offsetAndRotation(0F, 12F, 0F, -0.22F, 0F, 0.06F));
		PartDefinition p_hand_l = p_fore_l.addOrReplaceChild("hand_l", CubeListBuilder.create().texOffs(0, 54).addBox(-0.75F, -0.5F, -2F, 1.5F, 4.5F, 4F).texOffs(0, 63).addBox(-1.1F, -0.9F, -1.4F, 2.2F, 1.6F, 2.8F), PartPose.offsetAndRotation(0F, 10.5F, 0F, 0.1F, -0.45F, 0F));
		PartDefinition p_finger_l0 = p_hand_l.addOrReplaceChild("finger_l0", CubeListBuilder.create().texOffs(24, 63).addBox(-0.5F, -0.3F, -0.5F, 1F, 3.3F, 1F), PartPose.offsetAndRotation(-0.15F, 3.8F, -1.55F, -0.24F, 0F, 0.32F));
		PartDefinition p_claw_l0 = p_finger_l0.addOrReplaceChild("claw_l0", CubeListBuilder.create().texOffs(64, 63).addBox(-0.45F, -0.2F, -0.45F, 0.9F, 3F, 0.9F), PartPose.offsetAndRotation(0F, 3F, 0F, 0F, 0F, 0.55F));
		PartDefinition p_finger_l1 = p_hand_l.addOrReplaceChild("finger_l1", CubeListBuilder.create().texOffs(28, 63).addBox(-0.5F, -0.3F, -0.5F, 1F, 3.8F, 1F), PartPose.offsetAndRotation(-0.15F, 3.8F, -0.52F, -0.08F, 0F, 0.32F));
		PartDefinition p_claw_l1 = p_finger_l1.addOrReplaceChild("claw_l1", CubeListBuilder.create().texOffs(68, 63).addBox(-0.45F, -0.2F, -0.45F, 0.9F, 3F, 0.9F), PartPose.offsetAndRotation(0F, 3.5F, 0F, 0F, 0F, 0.55F));
		PartDefinition p_finger_l2 = p_hand_l.addOrReplaceChild("finger_l2", CubeListBuilder.create().texOffs(32, 63).addBox(-0.5F, -0.3F, -0.5F, 1F, 3.6F, 1F), PartPose.offsetAndRotation(-0.15F, 3.8F, 0.52F, 0.08F, 0F, 0.32F));
		PartDefinition p_claw_l2 = p_finger_l2.addOrReplaceChild("claw_l2", CubeListBuilder.create().texOffs(72, 63).addBox(-0.45F, -0.2F, -0.45F, 0.9F, 3F, 0.9F), PartPose.offsetAndRotation(0F, 3.3F, 0F, 0F, 0F, 0.55F));
		PartDefinition p_finger_l3 = p_hand_l.addOrReplaceChild("finger_l3", CubeListBuilder.create().texOffs(76, 63).addBox(-0.5F, -0.3F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(-0.15F, 3.8F, 1.55F, 0.24F, 0F, 0.32F));
		PartDefinition p_claw_l3 = p_finger_l3.addOrReplaceChild("claw_l3", CubeListBuilder.create().texOffs(80, 63).addBox(-0.45F, -0.2F, -0.45F, 0.9F, 3F, 0.9F), PartPose.offsetAndRotation(0F, 2.7F, 0F, 0F, 0F, 0.55F));
		PartDefinition p_sleeve_l0 = p_arm_l.addOrReplaceChild("sleeve_l0", CubeListBuilder.create().texOffs(96, 27).addBox(-0.625F, 0F, -1.5F, 1.25F, 10F, 3F), PartPose.offsetAndRotation(1.9F, 0.2F, -0.6F, 0.1251F, 0.15F, -0.0639F));
		PartDefinition p_sleeve_l1 = p_arm_l.addOrReplaceChild("sleeve_l1", CubeListBuilder.create().texOffs(178, 0).addBox(-0.625F, 0F, -1.3F, 1.25F, 13F, 2.6F), PartPose.offsetAndRotation(1.8F, 1.2F, 1.1F, 0.1251F, -0.15F, -0.0639F));
		PartDefinition p_sleeve_l2 = p_arm_l.addOrReplaceChild("sleeve_l2", CubeListBuilder.create().texOffs(68, 42).addBox(-1.4F, 0F, -0.625F, 2.8F, 9F, 1.25F), PartPose.offsetAndRotation(0.9F, -0.2F, -1.9F, 0.037F, 0.3F, -0.0675F));
		PartDefinition p_arm_r = p_chest.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(148, 0).addBox(-1.5F, -1.5F, -1.5F, 3F, 13.5F, 3F), PartPose.offsetAndRotation(-5.6F, -6F, 0.2F, -0.42F, 0F, 0.14F));
		PartDefinition p_fore_r = p_arm_r.addOrReplaceChild("fore_r", CubeListBuilder.create().texOffs(20, 27).addBox(-1.25F, -0.5F, -1.25F, 2.5F, 11F, 2.5F), PartPose.offsetAndRotation(0F, 12F, 0F, -0.22F, 0F, -0.06F));
		PartDefinition p_hand_r = p_fore_r.addOrReplaceChild("hand_r", CubeListBuilder.create().texOffs(12, 54).addBox(-0.75F, -0.5F, -2F, 1.5F, 4.5F, 4F).texOffs(12, 63).addBox(-1.1F, -0.9F, -1.4F, 2.2F, 1.6F, 2.8F), PartPose.offsetAndRotation(0F, 10.5F, 0F, 0.1F, 0.45F, 0F));
		PartDefinition p_finger_r0 = p_hand_r.addOrReplaceChild("finger_r0", CubeListBuilder.create().texOffs(36, 63).addBox(-0.5F, -0.3F, -0.5F, 1F, 3.3F, 1F), PartPose.offsetAndRotation(0.15F, 3.8F, -1.55F, -0.24F, 0F, -0.32F));
		PartDefinition p_claw_r0 = p_finger_r0.addOrReplaceChild("claw_r0", CubeListBuilder.create().texOffs(84, 63).addBox(-0.45F, -0.2F, -0.45F, 0.9F, 3F, 0.9F), PartPose.offsetAndRotation(0F, 3F, 0F, 0F, 0F, -0.55F));
		PartDefinition p_finger_r1 = p_hand_r.addOrReplaceChild("finger_r1", CubeListBuilder.create().texOffs(40, 63).addBox(-0.5F, -0.3F, -0.5F, 1F, 3.8F, 1F), PartPose.offsetAndRotation(0.15F, 3.8F, -0.52F, -0.08F, 0F, -0.32F));
		PartDefinition p_claw_r1 = p_finger_r1.addOrReplaceChild("claw_r1", CubeListBuilder.create().texOffs(88, 63).addBox(-0.45F, -0.2F, -0.45F, 0.9F, 3F, 0.9F), PartPose.offsetAndRotation(0F, 3.5F, 0F, 0F, 0F, -0.55F));
		PartDefinition p_finger_r2 = p_hand_r.addOrReplaceChild("finger_r2", CubeListBuilder.create().texOffs(44, 63).addBox(-0.5F, -0.3F, -0.5F, 1F, 3.6F, 1F), PartPose.offsetAndRotation(0.15F, 3.8F, 0.52F, 0.08F, 0F, -0.32F));
		PartDefinition p_claw_r2 = p_finger_r2.addOrReplaceChild("claw_r2", CubeListBuilder.create().texOffs(92, 63).addBox(-0.45F, -0.2F, -0.45F, 0.9F, 3F, 0.9F), PartPose.offsetAndRotation(0F, 3.3F, 0F, 0F, 0F, -0.55F));
		PartDefinition p_finger_r3 = p_hand_r.addOrReplaceChild("finger_r3", CubeListBuilder.create().texOffs(96, 63).addBox(-0.5F, -0.3F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(0.15F, 3.8F, 1.55F, 0.24F, 0F, -0.32F));
		PartDefinition p_claw_r3 = p_finger_r3.addOrReplaceChild("claw_r3", CubeListBuilder.create().texOffs(100, 63).addBox(-0.45F, -0.2F, -0.45F, 0.9F, 3F, 0.9F), PartPose.offsetAndRotation(0F, 2.7F, 0F, 0F, 0F, -0.55F));
		PartDefinition p_sleeve_r0 = p_arm_r.addOrReplaceChild("sleeve_r0", CubeListBuilder.create().texOffs(106, 27).addBox(-0.625F, 0F, -1.5F, 1.25F, 10F, 3F), PartPose.offsetAndRotation(-1.9F, 0.2F, -0.6F, 0.1251F, -0.15F, 0.0639F));
		PartDefinition p_sleeve_r1 = p_arm_r.addOrReplaceChild("sleeve_r1", CubeListBuilder.create().texOffs(188, 0).addBox(-0.625F, 0F, -1.3F, 1.25F, 13F, 2.6F), PartPose.offsetAndRotation(-1.8F, 1.2F, 1.1F, 0.1251F, 0.15F, 0.0639F));
		PartDefinition p_sleeve_r2 = p_arm_r.addOrReplaceChild("sleeve_r2", CubeListBuilder.create().texOffs(78, 42).addBox(-1.4F, 0F, -0.625F, 2.8F, 9F, 1.25F), PartPose.offsetAndRotation(-0.9F, -0.2F, -1.9F, 0.037F, -0.3F, 0.0675F));
		PartDefinition p_mantle = p_chest.addOrReplaceChild("mantle", CubeListBuilder.create().texOffs(134, 27).addBox(-7.25F, -0.75F, -4.25F, 14.5F, 2.75F, 8.75F).texOffs(98, 42).addBox(-4.5F, -2.25F, -3.5F, 9F, 1.75F, 7.5F).texOffs(202, 42).addBox(-7.75F, -1.75F, -3.25F, 3.5F, 1.25F, 6.75F).texOffs(224, 42).addBox(4.25F, -1.75F, -3.25F, 3.5F, 1.25F, 6.75F), PartPose.offsetAndRotation(0F, -7F, 0.2F, -0.34F, 0F, 0F));
	}

	private static void part1(PartDefinition r) {
		PartDefinition p_strip0 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip0", CubeListBuilder.create().texOffs(60, 0).addBox(-1.3F, 0F, -0.75F, 2.6F, 19F, 1.5F), PartPose.offsetAndRotation(-6F, 1F, 4F, 0.1937F, 0.15F, 0.1974F));
		PartDefinition p_strip1 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip1", CubeListBuilder.create().texOffs(20, 0).addBox(-1.2F, 0F, -0.75F, 2.4F, 23F, 1.5F), PartPose.offsetAndRotation(-4F, 1F, 4.1F, 0.196F, -0.1F, 0.1194F));
		PartDefinition p_strip2 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip2", CubeListBuilder.create().texOffs(0, 0).addBox(-1.1F, 0F, -0.75F, 2.2F, 25F, 1.5F), PartPose.offsetAndRotation(-2F, 1F, 4.2F, 0.1972F, 0.1F, 0.05F));
		PartDefinition p_strip3 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip3", CubeListBuilder.create().texOffs(30, 0).addBox(-1.4F, 0F, -0.75F, 2.8F, 22F, 1.5F), PartPose.offsetAndRotation(0F, 1F, 4.2F, 0.1974F, -0.12F, 0F));
		PartDefinition p_strip4 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip4", CubeListBuilder.create().texOffs(10, 0).addBox(-1.2F, 0F, -0.75F, 2.4F, 24F, 1.5F), PartPose.offsetAndRotation(2F, 1F, 4.2F, 0.1972F, 0.08F, -0.05F));
		PartDefinition p_strip5 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip5", CubeListBuilder.create().texOffs(40, 0).addBox(-1.1F, 0F, -0.75F, 2.2F, 21F, 1.5F), PartPose.offsetAndRotation(4F, 1F, 4.1F, 0.196F, -0.15F, -0.1194F));
		PartDefinition p_strip6 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip6", CubeListBuilder.create().texOffs(50, 0).addBox(-1.3F, 0F, -0.75F, 2.6F, 20F, 1.5F), PartPose.offsetAndRotation(6F, 1F, 4F, 0.1937F, 0.12F, -0.1974F));
		PartDefinition p_strip7 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip7", CubeListBuilder.create().texOffs(160, 0).addBox(-1.4F, 0F, -0.75F, 2.8F, 15F, 1.5F), PartPose.offsetAndRotation(-4.8F, 1.2F, 4.5F, 0.2145F, -0.1F, 0.1391F));
		PartDefinition p_strip8 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip8", CubeListBuilder.create().texOffs(70, 0).addBox(-1.5F, 0F, -0.75F, 3F, 17F, 1.5F), PartPose.offsetAndRotation(0.4F, 1.2F, 4.6F, 0.2166F, 0.12F, 0F));
		PartDefinition p_strip9 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip9", CubeListBuilder.create().texOffs(198, 0).addBox(-1.3F, 0F, -0.75F, 2.6F, 14F, 1.5F), PartPose.offsetAndRotation(4.9F, 1.2F, 4.5F, 0.2145F, -0.15F, -0.1391F));
		PartDefinition p_strip10 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip10", CubeListBuilder.create().texOffs(126, 27).addBox(-1.2F, 0F, -0.5F, 2.4F, 12F, 1F), PartPose.offsetAndRotation(-6.3F, 1.2F, -3.8F, -0.1176F, 0.25F, 0.1781F));
		PartDefinition p_strip11 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip11", CubeListBuilder.create().texOffs(170, 0).addBox(-1.4F, 0F, -0.5F, 2.8F, 16F, 1F), PartPose.offsetAndRotation(-4.4F, 1.2F, -3.9F, -0.0992F, -0.1F, 0.0997F));
		PartDefinition p_strip12 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip12", CubeListBuilder.create().texOffs(66, 27).addBox(-1F, 0F, -0.5F, 2F, 13F, 1F), PartPose.offsetAndRotation(-2.5F, 1.2F, -3.9F, -0.0996F, 0.15F, 0.04F));
		PartDefinition p_strip13 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip13", CubeListBuilder.create().texOffs(0, 27).addBox(-1.1F, 0F, -0.5F, 2.2F, 14F, 1F), PartPose.offsetAndRotation(2.5F, 1.2F, -3.9F, -0.0996F, -0.15F, -0.04F));
		PartDefinition p_strip14 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip14", CubeListBuilder.create().texOffs(128, 0).addBox(-1.4F, 0F, -0.5F, 2.8F, 17F, 1F), PartPose.offsetAndRotation(4.4F, 1.2F, -3.9F, -0.0992F, 0.1F, -0.0997F));
		PartDefinition p_strip15 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip15", CubeListBuilder.create().texOffs(44, 42).addBox(-1.2F, 0F, -0.5F, 2.4F, 11F, 1F), PartPose.offsetAndRotation(6.3F, 1.2F, -3.8F, -0.1176F, -0.25F, -0.1781F));
		PartDefinition p_strip16 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip16", CubeListBuilder.create().texOffs(36, 54).addBox(-1.5F, 0F, -0.625F, 3F, 7F, 1.25F), PartPose.offsetAndRotation(-6.1F, 1.5F, -4.5F, -0.2116F, 0.15F, 0.2166F));
		PartDefinition p_strip17 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip17", CubeListBuilder.create().texOffs(88, 42).addBox(-1.3F, 0F, -0.625F, 2.6F, 9F, 1.25F), PartPose.offsetAndRotation(-3.8F, 1.5F, -4.6F, -0.196F, -0.15F, 0.1194F));
		PartDefinition p_strip18 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip18", CubeListBuilder.create().texOffs(162, 54).addBox(-0.9F, 0F, -0.625F, 1.8F, 5F, 1.25F), PartPose.offsetAndRotation(-1.4F, 1.5F, -4F, -0.1194F, 0.2F, 0.03F));
		PartDefinition p_strip19 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip19", CubeListBuilder.create().texOffs(96, 54).addBox(-0.9F, 0F, -0.625F, 1.8F, 6F, 1.25F), PartPose.offsetAndRotation(1.5F, 1.5F, -4F, -0.1194F, -0.2F, -0.03F));
		PartDefinition p_strip20 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip20", CubeListBuilder.create().texOffs(162, 42).addBox(-1.3F, 0F, -0.625F, 2.6F, 8F, 1.25F), PartPose.offsetAndRotation(3.7F, 1.5F, -4.6F, -0.196F, 0.12F, -0.1194F));
		PartDefinition p_strip21 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip21", CubeListBuilder.create().texOffs(24, 54).addBox(-1.6F, 0F, -0.625F, 3.2F, 7F, 1.25F), PartPose.offsetAndRotation(6.1F, 1.5F, -4.5F, -0.2116F, -0.12F, -0.2166F));
		PartDefinition p_strip22 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip22", CubeListBuilder.create().texOffs(116, 27).addBox(-0.625F, 0F, -1.5F, 1.25F, 10F, 3F), PartPose.offsetAndRotation(7.6F, 1.5F, -1.6F, -0.0486F, 0.15F, -0.2355F));
		PartDefinition p_strip23 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip23", CubeListBuilder.create().texOffs(72, 27).addBox(-0.625F, 0F, -1.6F, 1.25F, 9F, 3.2F), PartPose.offsetAndRotation(7.6F, 1.5F, 1.9F, 0.078F, -0.2F, -0.2166F));
		PartDefinition p_strip24 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip24", CubeListBuilder.create().texOffs(84, 27).addBox(-0.625F, 0F, -1.6F, 1.25F, 9F, 3.2F), PartPose.offsetAndRotation(-7.6F, 1.5F, -1.4F, -0.0486F, -0.12F, 0.2355F));
		PartDefinition p_strip25 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip25", CubeListBuilder.create().texOffs(56, 27).addBox(-0.625F, 0F, -1.5F, 1.25F, 11F, 3F), PartPose.offsetAndRotation(-7.6F, 1.5F, 2F, 0.078F, 0.18F, 0.2166F));
		PartDefinition p_strip26 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip26", CubeListBuilder.create().texOffs(24, 42).addBox(-1.5F, 0F, -0.625F, 3F, 10F, 1.25F), PartPose.offsetAndRotation(-5.6F, 1.5F, 4.8F, 0.2311F, -0.2F, 0.1974F));
		PartDefinition p_strip27 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip27", CubeListBuilder.create().texOffs(32, 27).addBox(-1.6F, 0F, -0.625F, 3.2F, 12F, 1.25F), PartPose.offsetAndRotation(-2F, 1.5F, 4.9F, 0.2351F, 0.1F, 0.0599F));
		PartDefinition p_strip28 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip28", CubeListBuilder.create().texOffs(34, 42).addBox(-1.5F, 0F, -0.625F, 3F, 10F, 1.25F), PartPose.offsetAndRotation(2F, 1.5F, 4.9F, 0.2351F, -0.1F, -0.0599F));
		PartDefinition p_strip29 = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("strip29", CubeListBuilder.create().texOffs(44, 27).addBox(-1.6F, 0F, -0.625F, 3.2F, 12F, 1.25F), PartPose.offsetAndRotation(5.6F, 1.5F, 4.8F, 0.2311F, 0.2F, -0.1974F));
		PartDefinition p_head = r.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 42).addBox(-3F, -3.5F, -3.5F, 6F, 6F, 6F), PartPose.offsetAndRotation(0F, -0.8664F, -2.3194F, 0F, 0F, 0F));
		PartDefinition p_eye_l = p_head.addOrReplaceChild("eye_l", CubeListBuilder.create().texOffs(176, 54).addBox(-4.8F, -1.6F, -1F, 9.6F, 3.2F, 1.2F), PartPose.offsetAndRotation(1.55F, -1F, -3.55F, 0F, 0F, 0F));
		PartDefinition p_eye_r = p_head.addOrReplaceChild("eye_r", CubeListBuilder.create().texOffs(200, 54).addBox(-4.8F, -1.6F, -1F, 9.6F, 3.2F, 1.2F), PartPose.offsetAndRotation(-1.55F, -1F, -3.55F, 0F, 0F, 0F));
		PartDefinition p_hood = p_head.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(182, 27).addBox(-4.25F, -4.75F, -5.5F, 8.5F, 1.25F, 9.5F).texOffs(80, 0).addBox(3F, -3.5F, -5.5F, 1.25F, 8F, 9.5F).texOffs(104, 0).addBox(-4.25F, -3.5F, -5.5F, 1.25F, 8F, 9.5F).texOffs(52, 42).addBox(-3F, -3.5F, 2.5F, 6F, 9F, 1.5F).texOffs(48, 63).addBox(-3F, -3.6F, -5.75F, 6F, 1.1F, 1.5F).texOffs(108, 63).addBox(-2.95F, -2.5F, -5.6F, 0.8F, 1F, 0.8F).texOffs(104, 63).addBox(-0.4F, -2.5F, -5.6F, 0.8F, 1.4F, 0.8F).texOffs(112, 63).addBox(2.3F, -2.5F, -5.6F, 0.8F, 0.7F, 0.8F).texOffs(132, 42).addBox(-3.25F, -6F, -4F, 6.5F, 1.25F, 7.5F).texOffs(46, 54).addBox(-2.25F, -7.25F, -2.25F, 4.5F, 1.25F, 5.25F).texOffs(232, 54).addBox(-1.25F, -8.25F, -0.5F, 2.5F, 1F, 3.25F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
		PartDefinition p_peak0 = p_hood.addOrReplaceChild("peak0", CubeListBuilder.create().texOffs(150, 54).addBox(-1.1F, -3F, -1.1F, 2.2F, 3.25F, 2.2F), PartPose.offsetAndRotation(0F, -7.75F, 1.9F, -1.45F, 0F, 0F));
		PartDefinition p_peak1 = p_peak0.addOrReplaceChild("peak1", CubeListBuilder.create().texOffs(224, 54).addBox(-0.7F, -3F, -0.7F, 1.4F, 3.25F, 1.4F), PartPose.offsetAndRotation(0F, -2.75F, 0F, -0.75F, 0F, 0F));
		PartDefinition p_fringe0 = p_head.addOrReplaceChild("fringe0", CubeListBuilder.create().texOffs(170, 54).addBox(-0.5F, 0F, -0.8F, 1F, 5F, 1.6F), PartPose.offsetAndRotation(3.75F, 4.2F, -4.6F, 0F, 0F, -0.0997F));
		PartDefinition p_fringe1 = p_head.addOrReplaceChild("fringe1", CubeListBuilder.create().texOffs(172, 42).addBox(-0.5F, 0F, -1.1F, 1F, 7F, 2.2F), PartPose.offsetAndRotation(3.8F, 4.3F, -2F, 0F, 0F, -0.0997F));
		PartDefinition p_fringe2 = p_head.addOrReplaceChild("fringe2", CubeListBuilder.create().texOffs(112, 54).addBox(-0.5F, 0F, -0.8F, 1F, 6F, 1.6F), PartPose.offsetAndRotation(-3.75F, 4.2F, -4.6F, 0F, 0F, 0.0997F));
		PartDefinition p_fringe3 = p_head.addOrReplaceChild("fringe3", CubeListBuilder.create().texOffs(104, 54).addBox(-0.5F, 0F, -1.1F, 1F, 5F, 2.2F), PartPose.offsetAndRotation(-3.8F, 4.3F, -1.8F, 0F, 0F, 0.0997F));
	}

	/** Parts that move together: fine. */
	public static final String[] FINE = {"eye_l", "eye_r"};

	/** Parts that move together: fringe. */
	public static final String[] FRINGE = {"fringe0", "fringe1", "fringe2", "fringe3"};

	/** Parts that move together: sleeve. */
	public static final String[] SLEEVE = {"sleeve_l0", "sleeve_l1", "sleeve_l2", "sleeve_r0", "sleeve_r1", "sleeve_r2"};

	/** Parts that move together: strip. */
	public static final String[] STRIP = {"strip0", "strip1", "strip2", "strip3", "strip4", "strip5", "strip6", "strip7", "strip8", "strip9", "strip10", "strip11", "strip12", "strip13", "strip14", "strip15", "strip16", "strip17", "strip18", "strip19", "strip20", "strip21", "strip22", "strip23", "strip24", "strip25", "strip26", "strip27", "strip28", "strip29"};

	/** How much the fine parts are shrunk at run time (their boxes are built this much larger, for finer pixels). */
	public static final float FINE_SCALE = 0.25F;

	/** The animated parts, by name. */
	public static ModelPart find(ModelPart root, String name) {
		return switch (name) {
			case "root" -> root.getChild("root");
			case "thigh_l" -> root.getChild("root").getChild("thigh_l");
			case "shin_l" -> root.getChild("root").getChild("thigh_l").getChild("shin_l");
			case "foot_l" -> root.getChild("root").getChild("thigh_l").getChild("shin_l").getChild("foot_l");
			case "thigh_r" -> root.getChild("root").getChild("thigh_r");
			case "shin_r" -> root.getChild("root").getChild("thigh_r").getChild("shin_r");
			case "foot_r" -> root.getChild("root").getChild("thigh_r").getChild("shin_r").getChild("foot_r");
			case "body" -> root.getChild("root").getChild("body");
			case "belly" -> root.getChild("root").getChild("body").getChild("belly");
			case "chest" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest");
			case "neck" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("neck");
			case "arm_l" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_l");
			case "fore_l" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_l").getChild("fore_l");
			case "hand_l" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_l").getChild("fore_l").getChild("hand_l");
			case "finger_l0" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_l").getChild("fore_l").getChild("hand_l").getChild("finger_l0");
			case "claw_l0" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_l").getChild("fore_l").getChild("hand_l").getChild("finger_l0").getChild("claw_l0");
			case "finger_l1" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_l").getChild("fore_l").getChild("hand_l").getChild("finger_l1");
			case "claw_l1" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_l").getChild("fore_l").getChild("hand_l").getChild("finger_l1").getChild("claw_l1");
			case "finger_l2" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_l").getChild("fore_l").getChild("hand_l").getChild("finger_l2");
			case "claw_l2" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_l").getChild("fore_l").getChild("hand_l").getChild("finger_l2").getChild("claw_l2");
			case "finger_l3" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_l").getChild("fore_l").getChild("hand_l").getChild("finger_l3");
			case "claw_l3" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_l").getChild("fore_l").getChild("hand_l").getChild("finger_l3").getChild("claw_l3");
			case "sleeve_l0" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_l").getChild("sleeve_l0");
			case "sleeve_l1" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_l").getChild("sleeve_l1");
			case "sleeve_l2" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_l").getChild("sleeve_l2");
			case "arm_r" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_r");
			case "fore_r" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_r").getChild("fore_r");
			case "hand_r" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_r").getChild("fore_r").getChild("hand_r");
			case "finger_r0" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_r").getChild("fore_r").getChild("hand_r").getChild("finger_r0");
			case "claw_r0" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_r").getChild("fore_r").getChild("hand_r").getChild("finger_r0").getChild("claw_r0");
			case "finger_r1" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_r").getChild("fore_r").getChild("hand_r").getChild("finger_r1");
			case "claw_r1" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_r").getChild("fore_r").getChild("hand_r").getChild("finger_r1").getChild("claw_r1");
			case "finger_r2" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_r").getChild("fore_r").getChild("hand_r").getChild("finger_r2");
			case "claw_r2" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_r").getChild("fore_r").getChild("hand_r").getChild("finger_r2").getChild("claw_r2");
			case "finger_r3" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_r").getChild("fore_r").getChild("hand_r").getChild("finger_r3");
			case "claw_r3" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_r").getChild("fore_r").getChild("hand_r").getChild("finger_r3").getChild("claw_r3");
			case "sleeve_r0" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_r").getChild("sleeve_r0");
			case "sleeve_r1" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_r").getChild("sleeve_r1");
			case "sleeve_r2" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("arm_r").getChild("sleeve_r2");
			case "mantle" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle");
			case "strip0" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip0");
			case "strip1" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip1");
			case "strip2" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip2");
			case "strip3" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip3");
			case "strip4" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip4");
			case "strip5" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip5");
			case "strip6" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip6");
			case "strip7" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip7");
			case "strip8" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip8");
			case "strip9" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip9");
			case "strip10" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip10");
			case "strip11" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip11");
			case "strip12" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip12");
			case "strip13" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip13");
			case "strip14" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip14");
			case "strip15" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip15");
			case "strip16" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip16");
			case "strip17" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip17");
			case "strip18" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip18");
			case "strip19" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip19");
			case "strip20" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip20");
			case "strip21" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip21");
			case "strip22" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip22");
			case "strip23" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip23");
			case "strip24" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip24");
			case "strip25" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip25");
			case "strip26" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip26");
			case "strip27" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip27");
			case "strip28" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip28");
			case "strip29" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("strip29");
			case "head" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("head");
			case "eye_l" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("head").getChild("eye_l");
			case "eye_r" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("head").getChild("eye_r");
			case "hood" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("head").getChild("hood");
			case "peak0" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("head").getChild("hood").getChild("peak0");
			case "peak1" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("head").getChild("hood").getChild("peak0").getChild("peak1");
			case "fringe0" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("head").getChild("fringe0");
			case "fringe1" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("head").getChild("fringe1");
			case "fringe2" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("head").getChild("fringe2");
			case "fringe3" -> root.getChild("root").getChild("body").getChild("belly").getChild("chest").getChild("mantle").getChild("head").getChild("fringe3");
			default -> throw new IllegalArgumentException("no animated part " + name);
		};
	}
}
