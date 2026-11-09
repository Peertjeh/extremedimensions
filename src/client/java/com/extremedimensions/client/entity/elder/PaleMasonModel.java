package com.extremedimensions.client.entity.elder;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.elder.ElderBoss;
import com.extremedimensions.elder.PaleMason;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Pale Mason moving (after his concept sheet): he glides, his long legs stepping softly under the robe, swaying
 * slowly; the broken halo behind his head turns, his mantle, robe and the strips of his hood stir, his chains swing, the
 * core in his breast pulses and the pale blocks drift round him. For the Teleport Strike he is gone in a blink, thin as a
 * seam of light, and out of the air again with one arm raised high, brought down hard; for the Anchor Walls both hands
 * go up and are driven down, palms to the floor; for the Falling Blocks his arms lift slowly, the palms turning up and
 * the fingers spread, and the blocks about him rise; then one arm draws back and sweeps forward to hurl them. For the
 * Gaze he goes still, his head bowed towards his prey and his eyes blazing. The Last Door spreads his arms wide while he
 * rises a little and the halo whirls, and at the end his arms swing in and shut it. For the Bound Stones he hauls his
 * chained cubes out of the air with his arms spread, swings them round him with his whole torso and flings them; for
 * the Pillars a fist is driven into the floor, bending him double, and then each palm after it; for the Golden Rings his
 * halo whirls while three times his arms cross at his breast and are flung out level with the floor; for the Spiral he
 * rises and turns slowly about, stirring the air, his cloth and the blocks about him flung out, draws in on himself
 * and bursts outward. He forms out of the floor, laid
 * together block by block (robe, body, mantle, arms, head, hood), his eyes kindling, the halo last; dying, the halo
 * falls from behind his head and the floating blocks drop, and he sinks.
 */
public class PaleMasonModel extends EntityModel<ElderRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ExtremeDimensions.id("pale_mason"), "main");

	private final ModelPart root, body, bodyCore, legL, legR, torso, torsoCore, chest, head, headCore, hood, eyeL, eyeR;
	private final ModelPart haloFrame, halo, armL, armR, foreL, foreR, handL, handR, orbit;
	private final ModelPart[] robe, mantle, veil, sleeve, chains, swag, drape, floats, clawL, clawR;

	public PaleMasonModel(ModelPart model) {
		super(model);
		this.root = PaleMasonGeometry.find(model, "root");
		this.body = PaleMasonGeometry.find(model, "body");
		this.bodyCore = PaleMasonGeometry.find(model, "bodycore");
		this.legL = PaleMasonGeometry.find(model, "leg_l");
		this.legR = PaleMasonGeometry.find(model, "leg_r");
		this.torso = PaleMasonGeometry.find(model, "torso");
		this.torsoCore = PaleMasonGeometry.find(model, "torsocore");
		this.chest = PaleMasonGeometry.find(model, "chest");
		this.head = PaleMasonGeometry.find(model, "head");
		this.headCore = PaleMasonGeometry.find(model, "headcore");
		this.hood = PaleMasonGeometry.find(model, "hood");
		this.eyeL = PaleMasonGeometry.find(model, "eye_l");
		this.eyeR = PaleMasonGeometry.find(model, "eye_r");
		this.haloFrame = PaleMasonGeometry.find(model, "halo_frame");
		this.halo = PaleMasonGeometry.find(model, "halo");
		this.armL = PaleMasonGeometry.find(model, "arm_l");
		this.armR = PaleMasonGeometry.find(model, "arm_r");
		this.foreL = PaleMasonGeometry.find(model, "forearm_l");
		this.foreR = PaleMasonGeometry.find(model, "forearm_r");
		this.handL = PaleMasonGeometry.find(model, "hand_l");
		this.handR = PaleMasonGeometry.find(model, "hand_r");
		this.orbit = PaleMasonGeometry.find(model, "orbit");
		this.robe = ElderAnim.parts(model, PaleMasonGeometry.ROBE, PaleMasonGeometry::find);
		this.mantle = ElderAnim.parts(model, PaleMasonGeometry.MANTLE, PaleMasonGeometry::find);
		this.veil = ElderAnim.parts(model, PaleMasonGeometry.VEIL, PaleMasonGeometry::find);
		this.sleeve = ElderAnim.parts(model, PaleMasonGeometry.SLEEVE, PaleMasonGeometry::find);
		this.chains = ElderAnim.parts(model, PaleMasonGeometry.CHAINS, PaleMasonGeometry::find);
		this.swag = ElderAnim.parts(model, PaleMasonGeometry.SWAG, PaleMasonGeometry::find);
		this.drape = ElderAnim.parts(model, PaleMasonGeometry.DRAPE, PaleMasonGeometry::find);
		this.floats = ElderAnim.parts(model, PaleMasonGeometry.FLOAT, PaleMasonGeometry::find);
		this.clawL = ElderAnim.parts(model, PaleMasonGeometry.CLAW_L, PaleMasonGeometry::find);
		this.clawR = ElderAnim.parts(model, PaleMasonGeometry.CLAW_R, PaleMasonGeometry::find);
	}

	@Override
	public void setupAnim(ElderRenderState s) {
		super.setupAnim(s);
		ElderAnim.reset(root, body, bodyCore, legL, legR, torso, torsoCore, chest, head, headCore, hood, eyeL, eyeR);
		ElderAnim.reset(haloFrame, halo, armL, armR, foreL, foreR, handL, handR, orbit);
		ElderAnim.reset(robe);
		ElderAnim.reset(mantle);
		ElderAnim.reset(veil);
		ElderAnim.reset(sleeve);
		ElderAnim.reset(chains);
		ElderAnim.reset(swag);
		ElderAnim.reset(drape);
		ElderAnim.reset(floats);
		float t = s.ageInTicks;
		// Dying, he plays his ultimate once more, ending in the bloom as he breaks apart
		boolean dying = s.deathTime > 0;
		int action = dying ? ElderBoss.ULTIMATE : s.action;
		float at = dying ? s.deathTime * (200F / ElderBoss.DEATH_TICKS) : s.actionTime;
		float drift = Mth.clamp(s.walkAnimationSpeed * 1.4F, 0, 1);
		float stride = s.walkAnimationPos * 0.35F;
		float storm = action == ElderBoss.ULTIMATE ? ElderAnim.env(at, 40, 140, 6) : 0;
		float lift = action == PaleMason.LIFT ? ElderAnim.env(at, 30, 40, 16) : 0;
		float gaze = action == PaleMason.GAZE ? ElderAnim.env(at, 8, 40, 10) : 0;
		// He goes still as his gaze falls
		float calm = 1 - 0.85F * gaze;

		// He glides, swaying slowly, his legs stepping softly under the robe
		root.y += Mth.sin(t * 0.045F) * 1.4F * calm - Math.abs(Mth.cos(stride)) * 0.8F * drift;
		body.zRot += Mth.sin(t * 0.026F) * 0.016F * calm;
		body.xRot += Mth.sin(t * 0.02F) * 0.012F * calm + 0.06F * drift;
		torso.xRot += Mth.sin(t * 0.032F + 0.5F) * 0.012F * calm;
		float swing = Mth.sin(stride) * 0.2F * drift;
		legL.xRot += swing;
		legR.xRot -= swing;
		// He turns his head to his prey, and bows it to them as his gaze falls
		head.yRot += Mth.clamp(s.yRot, -45, 45) * Mth.DEG_TO_RAD * (0.5F + 0.4F * gaze);
		head.xRot += Mth.clamp(s.xRot, -30, 30) * Mth.DEG_TO_RAD * (0.4F + 0.5F * gaze);
		// The halo turns slowly behind his head and floats; in his ultimate it whirls, and as the blocks are lifted it
		// makes a turn of its own (whole turns, so it ends where it began)
		haloFrame.y += Mth.sin(t * 0.05F) * 0.8F - 6F * storm;
		haloFrame.xRot += Mth.sin(t * 0.03F) * 0.03F * calm;
		halo.zRot += t * 0.006F;
		if (action == ElderBoss.ULTIMATE) {
			halo.zRot += turns(at, 196, 10);
		} else if (action == PaleMason.LIFT) {
			halo.zRot += turns(at, 86, 1);
		}
		ElderAnim.scale(halo, 1 + 0.35F * storm + 0.05F * gaze);
		// The core in his breast pulses, brighter as he gathers himself
		ElderAnim.scale(chest, 1 + 0.08F * Mth.sin(t * 0.12F) + 0.15F * lift + 0.25F * gaze + 0.3F * storm);
		for (int i = 0; i < robe.length; i++) {
			robe[i].xRot += Mth.sin(t * 0.035F + i * 0.7F) * 0.018F * calm + 0.09F * drift + 0.12F * storm * Mth.sin(t * 0.3F + i);
			robe[i].zRot += Mth.cos(t * 0.03F + i * 1.1F) * 0.018F * calm;
		}
		for (int i = 0; i < mantle.length; i++) {
			mantle[i].xRot += Mth.sin(t * 0.05F + i * 1.3F) * 0.035F * calm + 0.07F * drift + 0.15F * storm * Mth.sin(t * 0.35F + i);
		}
		for (int i = 0; i < veil.length; i++) {
			veil[i].xRot += Mth.sin(t * 0.055F + i * 0.7F) * 0.04F * calm + 0.06F * drift + 0.15F * storm * Mth.sin(t * 0.4F + i);
			veil[i].zRot += Mth.cos(t * 0.045F + i * 1.3F) * 0.025F * calm;
		}
		for (int i = 0; i < sleeve.length; i++) {
			sleeve[i].xRot += Mth.sin(t * 0.06F + i * 1.1F) * 0.05F * calm + 0.08F * drift;
			sleeve[i].zRot += Mth.cos(t * 0.05F + i * 1.7F) * 0.03F * calm;
		}
		for (int i = 0; i < chains.length; i++) {
			chains[i].zRot += Mth.sin(t * 0.06F + i * 1.7F) * (0.05F * calm + 0.25F * storm);
			chains[i].xRot += Mth.cos(t * 0.05F + i) * 0.05F * calm + 0.12F * drift + 0.2F * storm * Mth.sin(t * 0.33F + i);
		}
		for (int i = 0; i < swag.length; i++) {
			swag[i].xRot += Mth.sin(t * 0.05F + i * 1.4F) * 0.08F * calm + 0.1F * drift + 0.25F * storm * Mth.sin(t * 0.4F + i);
		}
		for (int i = 0; i < drape.length; i++) {
			drape[i].xRot += Mth.sin(t * 0.04F + i) * 0.02F * calm + 0.04F * drift;
		}
		// The blocks drift round him, bobbing and tumbling; lifted, they rise and turn over; in his ultimate they whirl
		// out wide
		orbit.yRot += t * 0.008F + (action == ElderBoss.ULTIMATE ? turns(at, 196, 3) : 0);
		for (int i = 0; i < floats.length; i++) {
			float way = i % 2 == 0 ? 1 : -1;
			floats[i].y += Mth.sin(t * 0.05F + i * 1.3F) * 2.5F - lift * (22F + 6F * (i % 3)) - storm * 10F;
			floats[i].xRot += t * 0.01F * way + (action == PaleMason.LIFT ? turns(at, 86, 1) * way : 0);
			floats[i].zRot += t * 0.007F * way;
			floats[i].x *= 1 + 0.45F * storm + 0.15F * lift;
			floats[i].z *= 1 + 0.45F * storm + 0.15F * lift;
		}
		// His arms at rest hang a little forward, his claws stirring
		armL.zRot -= Mth.sin(t * 0.03F) * 0.025F * calm;
		armR.zRot += Mth.sin(t * 0.03F + 0.6F) * 0.025F * calm;
		armL.xRot += Mth.sin(t * 0.04F) * 0.03F * calm - swing * 0.5F;
		armR.xRot += Mth.sin(t * 0.04F + 1.3F) * 0.03F * calm + swing * 0.5F;
		// How far each hand's fingers spread and curl, for the blow under way
		float spreadL = 0.5F * storm + 0.6F * lift, spreadR = spreadL;
		float curlL = 0.3F * gaze - 0.25F * lift, curlR = curlL;
		// The weight of his blows (see below), which some of them also throw his limbs and cloth about with
		float[] ac = ElderAnim.accent(at, impacts(action), 10, 12);
		float g = ac[0], j = ac[1];

		switch (action) {
			case PaleMason.STRIKE -> {
				// Gone in a blink, thin as a seam of light, and out of the air again; one arm raised high, then brought
				// down hard
				float blink = ElderAnim.env(at - 2, 4, 4, 5);
				float raise = ElderAnim.smooth((at - 11) / 11F);
				float down = ElderAnim.snap((at - 23) / 3F);
				float k = 1 - ElderAnim.smooth((at - 34) / 14F);
				float up = raise * (1 - down);
				root.xScale *= 1 - 0.94F * blink;
				root.zScale *= 1 - 0.94F * blink;
				root.yScale *= 1 + 0.12F * blink;
				armR.xRot += (-2.75F * up - 0.75F * down) * k;
				// (raised, a turn of zRot the other way takes the hand out from his head)
				armR.zRot += (-0.35F * up - 0.15F * down) * k;
				foreR.xRot += (-0.45F * up + 0.25F * down) * k;
				handR.xRot += (-0.5F * up + 0.3F * down) * k;
				torso.xRot += (-0.14F * up + 0.36F * down) * k;
				torso.yRot += (0.22F * up - 0.1F * down) * k;
				head.xRot += (-0.1F * up + 0.25F * down) * k;
				armL.zRot -= 0.25F * up * k;
				spreadR += 0.55F * up * k;
				curlR += 0.35F * down * k;
			}
			case PaleMason.WALLS -> {
				// Both hands raised, then driven down, palms to the floor, as he drops into it
				float raise = ElderAnim.smooth(at / 14F);
				float down = ElderAnim.snap((at - 17) / 3F);
				float k = 1 - ElderAnim.smooth((at - 30) / 14F);
				float up = raise * (1 - down);
				armL.xRot += (-2.6F * up - 1.15F * down) * k;
				armR.xRot += (-2.6F * up - 1.15F * down) * k;
				armL.zRot += (0.42F * up + 0.12F * down) * k;
				armR.zRot += (-0.42F * up - 0.12F * down) * k;
				foreL.xRot += (-0.3F * up + 0.3F * down) * k;
				foreR.xRot += (-0.3F * up + 0.3F * down) * k;
				handL.xRot += (-0.2F * up - 0.75F * down) * k;
				handR.xRot += (-0.2F * up - 0.75F * down) * k;
				torso.xRot += (-0.18F * up + 0.5F * down) * k;
				torso.y += 6F * down * k;
				head.xRot += (-0.2F * up + 0.2F * down) * k;
				spreadL += 0.5F * (up + down) * k;
				spreadR += 0.5F * (up + down) * k;
			}
			case PaleMason.LIFT -> {
				// The arms lifted slowly, the palms turning up and the fingers spread, straining; he rises a little
				armL.xRot -= 1.3F * lift;
				armR.xRot -= 1.3F * lift;
				armL.yRot -= 0.55F * lift;
				armR.yRot += 0.55F * lift;
				armL.zRot -= 0.15F * lift + ElderAnim.tremor(t, 0.02F * lift);
				armR.zRot += 0.15F * lift + ElderAnim.tremor(t + 3, 0.02F * lift);
				foreL.xRot += 0.2F * lift;
				foreR.xRot += 0.2F * lift;
				handL.yRot += 2.8F * lift;
				handR.yRot -= 2.8F * lift;
				head.xRot -= 0.18F * lift;
				torso.xRot -= 0.06F * lift;
				root.y -= 3F * lift;
			}
			case PaleMason.THROW -> {
				// One arm drawn back and up, then swept forward to hurl
				float wind = ElderAnim.smooth(at / 12F);
				float hurl = ElderAnim.snap((at - 13) / 3F);
				float k = 1 - ElderAnim.smooth((at - 24) / 14F);
				float back = wind * (1 - hurl);
				armL.xRot += (-2.9F * back - 1.1F * hurl) * k;
				armL.zRot += (0.45F * back - 0.1F * hurl) * k;
				foreL.xRot += (-0.7F * back + 0.3F * hurl) * k;
				handL.xRot += (-0.4F * back + 0.25F * hurl) * k;
				torso.yRot += (-0.35F * back + 0.45F * hurl) * k;
				torso.xRot += (-0.1F * back + 0.22F * hurl) * k;
				head.yRot += (0.1F * back - 0.15F * hurl) * k;
				armR.zRot += 0.25F * back * k;
				armR.xRot += 0.2F * back * k;
				curlL += 0.35F * back * k;
				spreadL += 0.5F * hurl * k;
			}
			case PaleMason.GAZE -> {
				// Still, his head bowed towards his prey, his eyes blazing
				head.xRot += 0.45F * gaze;
				head.zRot += ElderAnim.tremor(t, 0.012F * gaze);
				torso.xRot += 0.08F * gaze;
				armL.zRot += 0.06F * gaze;
				armR.zRot -= 0.06F * gaze;
				float blaze = 1 + 0.45F * gaze + 0.08F * gaze * Mth.sin(t * 1.3F);
				ElderAnim.scale(eyeL, blaze);
				ElderAnim.scale(eyeR, blaze);
			}
			case PaleMason.CHAINS -> {
				// Bound Stones: he gathers himself low, then his arms swing out wide and up, palms up, hauling the chained
				// cubes out of the air, his head thrown back to them; they drop to the ends of their chains and wrench at
				// him as they catch (24). He sweeps them round him in a great circle, his torso twisting into the swing
				// and back, the arms see-sawing a beat after it, robe, mantle and chains swinging out; at the last he
				// draws them back behind him and hurls both arms forward to fling them (104), following through into a
				// bow before he straightens
				float coil = ElderAnim.env(at, 5, 0, 7);
				float haul = ElderAnim.smooth((at - 4) / 16F);
				float wind = ElderAnim.smooth((at - 90) / 10F);
				float fling = ElderAnim.snap((at - 100) / 4F);
				float follow = ElderAnim.smooth((at - 104) / 8F);
				float k = 1 - ElderAnim.smooth((at - 110) / 20F);
				float wide = haul * (1 - fling), back = wind * (1 - fling), fore = fling * k;
				float heave = haul * (1 - ElderAnim.smooth((at - 18) / 8F));
				float palms = haul * (1 - ElderAnim.smooth((at - 20) / 5F));
				float grip = ElderAnim.smooth((at - 20) / 5F) * (1 - fling);
				float throwOut = ElderAnim.env(at - 100, 4, 2, 14);
				// The swing (the torso one way and back), the arms a beat behind it, and how fast it is turning
				float twist = sweep(at), arms = sweep(at - 4), spin = sweep(at + 1) - sweep(at - 1);
				torso.yRot += 0.55F * twist;
				body.yRot -= 0.1F * twist;
				body.zRot -= 0.05F * arms;
				head.yRot -= 0.3F * twist;
				// (spread wide, a turn of xRot sweeps an arm forward or back and one of zRot raises or lowers it: the
				// arm leading the swing rides high, the other low)
				armL.xRot += -0.5F * coil - 0.3F * wide + 0.75F * back - 1.75F * fore + 0.55F * follow * k - 0.45F * arms;
				armR.xRot += -0.5F * coil - 0.3F * wide + 0.75F * back - 1.75F * fore + 0.55F * follow * k + 0.45F * arms;
				// (and as the cubes catch on their chains they wrench his spread arms down)
				float yank = 0.3F * j * wide;
				armL.zRot += 0.3F * coil - 1.35F * wide - 0.35F * heave - 0.25F * back + 0.15F * fore + yank - 0.35F * arms;
				armR.zRot += -0.3F * coil + 1.35F * wide + 0.35F * heave + 0.25F * back - 0.15F * fore - yank - 0.35F * arms;
				foreL.xRot += -0.3F * coil + 0.25F * wide + 0.3F * fore;
				foreR.xRot += -0.3F * coil + 0.25F * wide + 0.3F * fore;
				handL.yRot += 1.5F * palms;
				handR.yRot -= 1.5F * palms;
				handL.xRot -= 0.3F * fore;
				handR.xRot -= 0.3F * fore;
				spreadL += 0.6F * palms + 0.9F * fore;
				spreadR += 0.6F * palms + 0.9F * fore;
				curlL += 0.3F * coil + 0.75F * grip - 0.25F * fore;
				curlR += 0.3F * coil + 0.75F * grip - 0.25F * fore;
				torso.xRot += 0.18F * coil - 0.16F * heave - 0.2F * back + 0.35F * fore + 0.12F * follow * k;
				torso.y += 3F * fore;
				head.xRot += 0.12F * coil - 0.3F * heave - 0.12F * back + 0.1F * fore;
				root.y += 2.5F * coil - 1.5F * back + 2F * fore;
				whirl(1.2F * Math.abs(spin) + 0.3F * throwOut, 1.4F * spin, t);
				// The halo sways after the swing and makes a turn of its own; the blocks about him swing out wide
				haloFrame.zRot += 0.6F * spin;
				halo.zRot -= turns(at - 24, 80, 1);
				float out = haul * (1 - ElderAnim.smooth((at - 100) / 16F));
				orbit.yRot += turns(at - 20, 92, 2);
				for (ModelPart f : floats) {
					f.x *= 1 + 0.5F * out;
					f.z *= 1 + 0.5F * out;
				}
			}
			case PaleMason.PILLARS -> {
				// Pillars of the Anchor: his right hand rises high over him, the fingers spread, his head tilted up to it
				// and his core flaring; he drives the fist down into the floor (24), bending deep at the waist, and stays
				// bowed over it as the pillars tear up; then the left palm is slammed down (40) and the right (56), each
				// with a smaller bend, and he rises
				float raise = ElderAnim.smooth(at / 14F);
				float down = fall((at - 16) / 8F);
				float up = raise * (1 - down);
				float fist = down * (1 - ElderAnim.smooth((at - 28) / 12F));
				float bow = down * (1 - 0.7F * ElderAnim.smooth((at - 27) / 10F)) * (1 - ElderAnim.smooth((at - 58) / 14F));
				float upL = ElderAnim.smooth((at - 29) / 8F), downL = fall((at - 37) / 3F);
				float upR = ElderAnim.smooth((at - 45) / 8F), downR = fall((at - 53) / 3F);
				float liftL = upL * (1 - downL), palmL = downL * (1 - ElderAnim.smooth((at - 44) / 10F));
				float liftR = upR * (1 - downR), palmR = downR * (1 - ElderAnim.smooth((at - 59) / 13F));
				armR.xRot += -2.9F * up - 1.3F * fist - 2.2F * liftR - 1.2F * palmR;
				armR.zRot += -0.3F * up + 0.08F * fist - 0.2F * liftR + 0.1F * palmR;
				foreR.xRot += -0.3F * up + 0.32F * fist - 0.4F * liftR + 0.3F * palmR;
				handR.xRot += -0.3F * up + 0.2F * fist - 0.2F * liftR - 0.75F * palmR;
				armL.xRot += 0.15F * up + 0.45F * fist - 2.2F * liftL - 1.2F * palmL;
				armL.zRot += -0.4F * up - 0.3F * fist + 0.2F * liftL - 0.1F * palmL;
				foreL.xRot += -0.4F * liftL + 0.3F * palmL;
				handL.xRot += -0.2F * liftL - 0.75F * palmL;
				spreadR += 0.8F * up + 0.5F * liftR + 0.6F * palmR;
				curlR += -0.35F * up + 1.1F * fist;
				spreadL += 0.5F * liftL + 0.6F * palmL;
				// (a turn of yRot one way takes the right shoulder back and the left forward)
				torso.yRot += 0.28F * up - 0.2F * fist - 0.2F * liftL + 0.18F * palmL + 0.2F * liftR - 0.18F * palmR;
				torso.zRot += 0.08F * up;
				torso.xRot += -0.2F * up + 0.95F * bow + 0.25F * (palmL + palmR);
				torso.y += 8F * bow + 3F * (palmL + palmR);
				body.xRot += 0.12F * bow;
				head.xRot += -0.45F * up - 0.4F * bow - 0.15F * (liftL + liftR);
				root.y += -2F * up + 4F * bow + 1.5F * (palmL + palmR);
				ElderAnim.scale(chest, 1 + 0.5F * up + 0.3F * fist + 0.2F * (liftL + liftR));
				// His cloth thrown out by each blow and falling back
				whirl(0.35F * j, 0, t);
			}
			case PaleMason.RINGS -> {
				// Golden Rings: the halo whirls and flares; three times his arms cross at his breast and are flung out to
				// the sides, level with the floor and palms down, casting a ring of gold out across it (10, 30, 50), and
				// he bobs with each cast
				// (between casts his arms sweep straight back in from his sides to his breast and hold there a moment)
				float in = 0, out = 0, flash = 0, bob = 0;
				for (int c = 0; c < 3; c++) {
					float cast = 10 + 20 * c;
					float fling = ElderAnim.snap((at - cast + 3) / 3F);
					in += (c == 0 ? ElderAnim.smooth(at / 7F) : ElderAnim.smooth((at - cast + 15) / 8F)) * (1 - fling);
					out += fling * (1 - ElderAnim.smooth((at - cast - 5) / (c == 2 ? 16F : 8F)));
					flash += ElderAnim.env(at - cast + 2, 2, 1, 9);
					bob += ElderAnim.env(at - cast, 1.5F, 0, 9);
				}
				// (held out before him, a turn of yRot swings an arm across his breast or out to his side; crossed, the
				// elbows are before his belly and the hands at the other shoulder, the left arm over the right)
				armL.xRot += -0.95F * in - 1.45F * out;
				armR.xRot += -0.85F * in - 1.45F * out;
				armL.yRot += 0.8F * in - 1.4F * out;
				armR.yRot += -0.8F * in + 1.4F * out;
				armL.zRot -= 0.2F * in;
				armR.zRot += 0.2F * in;
				foreL.xRot += -1.8F * in + 0.3F * out;
				foreR.xRot += -1.8F * in + 0.3F * out;
				foreL.zRot -= 0.3F * in;
				foreR.zRot += 0.3F * in;
				handL.xRot -= 0.2F * out;
				handR.xRot -= 0.2F * out;
				spreadL += 0.9F * out;
				spreadR += 0.9F * out;
				curlL += 0.4F * in - 0.2F * out;
				curlR += 0.4F * in - 0.2F * out;
				torso.xRot += 0.16F * in - 0.14F * out;
				torso.y += 2F * in;
				head.xRot += 0.1F * in - 0.12F * out;
				root.y += 2.5F * bob;
				whirl(0.3F * flash, 0, t);
				float ring = ElderAnim.env(at, 6, 54, 10);
				halo.zRot += turns(at, 64, 3);
				ElderAnim.scale(halo, 1 + 0.12F * ring + 0.06F * flash);
				haloFrame.y -= 3F * ring;
				ElderAnim.scale(chest, 1 + 0.2F * flash);
			}
			case PaleMason.SPIRAL -> {
				// The Unfinished Spiral: he rises a little off the floor, his arms spreading wide, and turns slowly about
				// once while his hands stir the air in slow circles, robe and mantle flaring out and the blocks about him
				// whirling faster; he draws in on himself, then throws both arms out wide and the spiral bursts (88), and
				// he settles back to the floor
				float rise = ElderAnim.smooth(at / 20F) * (1 - ElderAnim.smooth((at - 92) / 20F));
				float open = ElderAnim.smooth((at - 2) / 16F);
				float stir = ElderAnim.smooth((at - 18) / 8F) * (1 - ElderAnim.smooth((at - 72) / 8F));
				float draw = ElderAnim.smooth((at - 74) / 10F);
				float burst = ElderAnim.snap((at - 84) / 4F);
				float k = 1 - ElderAnim.smooth((at - 92) / 20F);
				float wide = open * (1 - draw), in = draw * (1 - burst), out = burst * k;
				float swirl = ElderAnim.smooth((at - 14) / 14F) * (1 - ElderAnim.smooth((at - 80) / 10F));
				float blast = ElderAnim.env(at - 84, 4, 3, 16);
				// His hands stir three slow circles (the leading hand rides high), and he turns about once
				float q = (at - 20) * (Mth.PI * 2 * 3 / 64F);
				float cx = Mth.sin(q) * stir, cy = Mth.cos(q) * stir;
				float fx = Mth.sin(q + 0.8F) * stir, fy = Mth.cos(q + 0.8F) * stir;
				float turning = at < 20 || at > 84 ? 0 : 1 - Mth.cos((at - 20) / 64F * Mth.PI * 2);
				body.yRot += turns(at - 20, 64, 1);
				torso.yRot += 0.12F * turning;
				// (drawn in, his arms fold across his breast)
				armL.xRot += -0.2F * wide + 0.25F * out - 0.9F * in + 0.18F * cx;
				armR.xRot += -0.2F * wide + 0.25F * out - 0.9F * in - 0.18F * cx;
				armL.zRot += -1.25F * wide - 1.6F * out - 0.15F * in - 0.16F * cy;
				armR.zRot += 1.25F * wide + 1.6F * out + 0.15F * in - 0.16F * cy;
				armL.yRot += 0.7F * in;
				armR.yRot -= 0.7F * in;
				foreL.xRot += 0.15F * wide + 0.32F * out - 1.5F * in + 0.3F * fx;
				foreR.xRot += 0.15F * wide + 0.32F * out - 1.5F * in - 0.3F * fx;
				foreL.zRot += 0.3F * fy - 0.25F * in;
				foreR.zRot += 0.3F * fy + 0.25F * in;
				spreadL += 0.5F * wide + 1.0F * out;
				spreadR += 0.5F * wide + 1.0F * out;
				curlL += 0.3F * stir + 0.8F * in - 0.4F * out;
				curlR += 0.3F * stir + 0.8F * in - 0.4F * out;
				torso.xRot += -0.08F * wide + 0.28F * in - 0.2F * out;
				torso.y += 3F * in;
				head.xRot += -0.2F * wide + 0.3F * in - 0.3F * out;
				// (the halo kept upright behind his head as he throws it back)
				haloFrame.xRot += 0.3F * out;
				root.y += -6F * rise + 2.5F * in - 2F * out;
				legL.xRot += 0.12F * rise;
				legR.xRot += 0.06F * rise;
				whirl(0.35F * swirl + 0.55F * blast, 0.15F * turning * swirl, t);
				orbit.yRot += turns(at - 14, 90, 2);
				for (int i = 0; i < floats.length; i++) {
					floats[i].x *= 1 + 0.3F * swirl + 0.7F * blast;
					floats[i].z *= 1 + 0.3F * swirl + 0.7F * blast;
					floats[i].y -= (4F + 3F * (i % 3)) * swirl;
				}
				halo.zRot += turns(at - 20, 72, 2) + turns(at - 84, 24, 1);
				ElderAnim.scale(halo, 1 + 0.1F * swirl - 0.08F * in + 0.25F * blast);
				ElderAnim.scale(chest, 1 + 0.25F * swirl + 0.5F * blast);
			}
			case ElderBoss.SPAWN -> laid(at, t);
			case ElderBoss.ULTIMATE -> {
				// The Last Door: arms spread wide and he rises, head back, while the halo whirls; at the end his arms
				// swing in before him and shut it
				float shut = ElderAnim.env(at - 186, 3, 5, 8);
				armL.zRot -= 1.3F * storm;
				armR.zRot += 1.3F * storm;
				armL.xRot -= 0.3F * storm;
				armR.xRot -= 0.3F * storm;
				foreL.xRot += 0.28F * storm;
				foreR.xRot += 0.28F * storm;
				head.xRot -= 0.35F * storm;
				torso.xRot -= 0.1F * storm;
				root.y -= 10F * storm;
				body.zRot += Mth.sin(t * 2.6F) * 0.01F * storm;
				armL.zRot += 1.3F * shut;
				armR.zRot -= 1.3F * shut;
				armL.xRot -= 1.1F * shut;
				armR.xRot -= 1.1F * shut;
				torso.xRot += 0.3F * shut;
				head.xRot += 0.3F * shut;
			}
			default -> {
			}
		}
		claws(clawL, 1, spreadL, curlL, t, calm);
		claws(clawR, -1, spreadR, curlR, t + 2.1F, calm);
		// The weight of his blows: he gathers, then drops into the blow
		root.y += -1.6F * g + 1.6F * j;
		torso.xRot += -0.06F * g + 0.12F * j;
		torso.zRot += ElderAnim.tremor(t, 0.015F * g);
		head.xRot += -0.06F * g + 0.1F * j;
		halo.zRot += 0.25F * j;
		for (int i = 0; i < chains.length; i++) {
			chains[i].zRot += 0.3F * j * Mth.sin(i * 1.9F + 0.5F);
		}
		if (dying) {
			// The halo falls from behind his head and the floating blocks drop to the floor; at the very end he sinks
			float loose = ElderAnim.smooth((s.deathTime - ElderBoss.DEATH_TICKS * 0.5F) / (ElderBoss.DEATH_TICKS * 0.4F));
			haloFrame.y += 26F * loose;
			haloFrame.xRot += 0.8F * loose;
			for (int i = 0; i < floats.length; i++) {
				float fall = ElderAnim.smooth((s.deathTime - ElderBoss.DEATH_TICKS * 0.5F - i * 3) / 12F);
				floats[i].y *= 1 - fall;
			}
			float sink = ElderAnim.smooth((s.deathTime - ElderBoss.DEATH_TICKS * 0.9F) / (ElderBoss.DEATH_TICKS * 0.1F));
			root.y += 8F * sink;
		}
	}

	/**
	 * A hand's fingers (the four in order across the hand, then the thumb): spread out from each other and curled
	 * towards the palm by the given amounts, stirring a little at rest.
	 */
	private static void claws(ModelPart[] f, int side, float spread, float curl, float t, float calm) {
		for (int i = 0; i < f.length; i++) {
			boolean thumb = i == f.length - 1;
			float across = thumb ? 0 : (i - 1.5F) / 1.5F;
			if (thumb) {
				f[i].zRot += side * 0.35F * spread;
			} else {
				f[i].zRot -= across * 0.4F * spread;
			}
			f[i].xRot += curl + Mth.sin(t * 0.07F + i * 0.9F) * 0.05F * calm;
		}
	}

	/**
	 * How far something has turned at {@code at} ticks of an action {@code length} ticks long, making {@code turns} whole
	 * turns: from rest, fastest in the middle and slowing to rest, so it ends where it began.
	 */
	private static float turns(float at, float length, int turns) {
		float u = Mth.clamp(at / length, 0, 1);
		float tau = Mth.PI * 2;
		return tau * turns * (u - Mth.sin(tau * u) / tau);
	}

	/** A blow driven home: slow to start and fastest as it lands at 1, where it stops dead. */
	private static float fall(float x) {
		x = Mth.clamp(x, 0, 1);
		return x * x * x;
	}

	/**
	 * The swing of the Bound Stones at {@code at}: his torso twisted one way and back again over the sweep (24 to 100
	 * ticks), from rest and back to rest.
	 */
	private static float sweep(float at) {
		return Mth.sin(Mth.PI * 2 * ElderAnim.smooth((at - 24) / 76F));
	}

	/**
	 * His cloth flung out from him by a whirl or a blow ({@code out}, a turn outward; negative falls back in) and trailing
	 * after him as he turns ({@code lag}, a turn round him the other way): the robe, the mantle (most), the hood's strips,
	 * the sleeves and the hanging chains.
	 */
	private void whirl(float out, float lag, float t) {
		for (int i = 0; i < robe.length; i++) {
			robe[i].xRot += out * (0.8F + 0.25F * Mth.sin(t * 0.5F + i * 1.7F));
			robe[i].zRot += 0.6F * lag;
		}
		for (int i = 0; i < mantle.length; i++) {
			mantle[i].xRot += out * (1.1F + 0.3F * Mth.sin(t * 0.6F + i * 1.3F));
			mantle[i].zRot += lag;
		}
		for (int i = 0; i < veil.length; i++) {
			veil[i].xRot += 0.6F * out * (1 + 0.3F * Mth.sin(t * 0.7F + i));
		}
		for (int i = 0; i < sleeve.length; i++) {
			sleeve[i].xRot += 0.7F * out * (1 + 0.3F * Mth.sin(t * 0.8F + i * 1.9F));
		}
		for (int i = 0; i < chains.length; i++) {
			chains[i].xRot += 0.6F * out;
			chains[i].zRot += lag;
		}
	}

	/**
	 * Forming, on the same 140-tick timeline as the others: he rises out of the floor as he is laid together block by
	 * block, robe, body, mantle and arms, then the head and its hood, his eyes kindling, and the halo last, wheeling into
	 * place; the floating blocks come up out of the floor to him.
	 */
	private void laid(float at, float t) {
		float rise = ElderAnim.smooth((at - 5) / 55F);
		root.y += 70F * (1 - rise);
		for (int i = 0; i < robe.length; i++) {
			robe[i].yScale *= ElderAnim.smooth((at - 15 - (i % 10) * 3) / 30F);
		}
		float frame = ElderAnim.smooth((at - 25) / 30F);
		ElderAnim.scale(torsoCore, 0.4F + 0.6F * frame);
		ElderAnim.scale(chest, ElderAnim.smooth((at - 55) / 15F));
		for (int i = 0; i < mantle.length; i++) {
			mantle[i].yScale *= ElderAnim.smooth((at - 50 - (i % 8) * 3) / 25F);
		}
		for (ModelPart d : drape) {
			ElderAnim.scale(d, ElderAnim.smooth((at - 70) / 20F));
		}
		for (int i = 0; i < swag.length; i++) {
			ElderAnim.scale(swag[i], ElderAnim.smooth((at - 80 - i * 3) / 15F));
		}
		float arms = ElderAnim.smooth((at - 50) / 35F);
		ElderAnim.scale(armL, arms);
		ElderAnim.scale(armR, arms);
		armL.zRot -= 1.2F * (1 - arms);
		armR.zRot += 1.2F * (1 - arms);
		ElderAnim.scale(headCore, ElderAnim.smooth((at - 70) / 22F));
		ElderAnim.scale(hood, ElderAnim.smooth((at - 82) / 20F));
		for (ModelPart v : veil) {
			v.yScale *= ElderAnim.smooth((at - 92) / 18F);
		}
		float eyes = ElderAnim.smooth((at - 100) / 10F);
		ElderAnim.scale(eyeL, eyes);
		ElderAnim.scale(eyeR, eyes);
		for (int i = 0; i < chains.length; i++) {
			chains[i].yScale *= ElderAnim.smooth((at - 95 - i * 2) / 15F);
		}
		// The halo last, wheeling into place
		float crown = ElderAnim.smooth((at - 100) / 25F);
		ElderAnim.scale(haloFrame, crown);
		halo.zRot += (1 - crown) * (1 - crown) * 12F;
		for (int i = 0; i < floats.length; i++) {
			float up = ElderAnim.smooth((at - 40 - i * 8) / 30F);
			floats[i].y += 140F * (1 - up);
			ElderAnim.scale(floats[i], up);
		}
		// The last block laid: he swells a little and settles
		ElderAnim.scale(root, 1 + 0.05F * ElderAnim.env(at - 124, 2, 2, 12));
	}

	/** When each of his blows lands (ticks at tempo 1, as the server should strike them). */
	private static float[] impacts(int action) {
		return switch (action) {
			case PaleMason.STRIKE -> new float[] {26};
			case PaleMason.WALLS -> new float[] {20};
			case PaleMason.THROW -> new float[] {16};
			// the cubes catching on their chains, then flung
			case PaleMason.CHAINS -> new float[] {24, 104};
			case PaleMason.PILLARS -> new float[] {24, 40, 56};
			case PaleMason.RINGS -> new float[] {10, 30, 50};
			case PaleMason.SPIRAL -> new float[] {88};
			default -> ElderAnim.NONE;
		};
	}
}
