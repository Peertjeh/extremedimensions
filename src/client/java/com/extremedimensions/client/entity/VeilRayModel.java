package com.extremedimensions.client.entity;

import com.extremedimensions.ExtremeDimensions;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

/**
 * The Veil Ray: a flat body with a horned head, three-jointed wings and a long whip of a tail. The wings beat in a slow
 * travelling wave from the shoulder to the tip, the tail sways a beat behind, and the whole body banks into its turns.
 *
 * <p>The geometry and the matching textures are generated together (see {@code tools/veil_ray} in the repository). The
 * wings, back, belly, head and tail are thin fine plates drawn at four texels to a model pixel: their parts are built
 * four times too large and scaled back down every frame.
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
	private final ModelPart[] fine;

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
		this.fine = new ModelPart[VeilRayGeometry.FINE.length];
		for (int i = 0; i < fine.length; i++) fine[i] = VeilRayGeometry.find(root, VeilRayGeometry.FINE[i]);
	}

	public static LayerDefinition createBodyLayer() {
		return VeilRayGeometry.create();
	}

	@Override
	public void setupAnim(VeilRayRenderState s) {
		super.setupAnim(s);
		for (ModelPart f : fine) {
			f.xScale = VeilRayGeometry.FINE_SCALE;
			f.yScale = VeilRayGeometry.FINE_SCALE;
			f.zScale = VeilRayGeometry.FINE_SCALE;
		}
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
