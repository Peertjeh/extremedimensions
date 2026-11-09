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
 * rises a little and the halo whirls, and at the end his arms swing in and shut it. He forms out of the floor, laid
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
		float[] ac = ElderAnim.accent(at, impacts(action), 10, 12);
		float g = ac[0], j = ac[1];
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
			default -> ElderAnim.NONE;
		};
	}
}
