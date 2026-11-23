package com.extremedimensions.client.entity.elder;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.mending.StarvedOne;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Starved One moving. It hangs in the air leaning in towards what it wants, breathing slow and hungry, its tail of
 * tatters rippling behind it in a long wave (streaming out as it flies), its rags, sleeves and snapped chains stirring,
 * its crown of broken iron rattling; its head follows its prey. Every blow has weight: it gathers (draws back, strains)
 * and then throws itself into the blow, which snaps through and rebounds. Falling on its prey it flings its arms back
 * and then drives both claws out ahead; raking, one arm winds back and slashes across, the whole body twisting with it;
 * calling its young it rears up with its maw to the sky and its arms flung wide, shaking; biting the thread its head
 * draws back and snaps forward; dragging it reaches out wide and hauls back; stepping through the veil it folds in on
 * itself and opens again. Bound by the pillars its arms are dragged out and down, its back arched, its head hanging,
 * shuddering, the tail limp.
 *
 * <p>Rising out of the Heart (3.48) it stands upright in the hole it tore, its arms up over its head and its claws
 * hooked over the rim, dragging itself up hand over hand, its elbows bending with each pull, its head bowed under the
 * stone and coming up as it comes free, its chains and tail trailing down into the dark. Dying, it thrashes and sags:
 * its back folds, its head and arms drop, its crown tilts, its four chains snap away one after another (as the server
 * snaps them), and it sinks.
 */
public class StarvedOneModel extends EntityModel<StarvedOneRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ExtremeDimensions.id("starved_one"), "main");

	private final ModelPart root, body, head, crown, armL, armR, foreL, foreR, handL, handR;
	private final ModelPart[] tail, shroud, sleeves, chains;

	public StarvedOneModel(ModelPart model) {
		super(model);
		this.root = StarvedOneGeometry.find(model, "root");
		this.body = StarvedOneGeometry.find(model, "body");
		this.head = StarvedOneGeometry.find(model, "head");
		this.crown = StarvedOneGeometry.find(model, "crown");
		this.armL = StarvedOneGeometry.find(model, "arm_l");
		this.armR = StarvedOneGeometry.find(model, "arm_r");
		this.foreL = StarvedOneGeometry.find(model, "fore_l");
		this.foreR = StarvedOneGeometry.find(model, "fore_r");
		this.handL = StarvedOneGeometry.find(model, "hand_l");
		this.handR = StarvedOneGeometry.find(model, "hand_r");
		this.tail = ElderAnim.parts(model, new String[] {"tail_0", "tail_1", "tail_2", "tail_3"}, StarvedOneGeometry::find);
		this.shroud = ElderAnim.parts(model, StarvedOneGeometry.SHROUD, StarvedOneGeometry::find);
		this.sleeves = ElderAnim.parts(model, StarvedOneGeometry.SLEEVE, StarvedOneGeometry::find);
		this.chains = ElderAnim.parts(model, StarvedOneGeometry.CHAIN, StarvedOneGeometry::find);
	}

	@Override
	public void setupAnim(StarvedOneRenderState s) {
		super.setupAnim(s);
		ElderAnim.reset(body, crown);
		ElderAnim.reset(chains);
		float t = s.ageInTicks;
		float bound = s.bound, free = 1 - bound;
		float fly = s.flight * free;
		int action = bound > 0.5F ? StarvedOne.IDLE : s.action;
		float at = s.actionTime;

		// Hanging in the air, breathing slow and hungry, leaning further into its flight
		root.y += Mth.sin(t * 0.07F) * 1.6F * free;
		body.xRot += 0.22F * fly + Mth.sin(t * 0.05F) * 0.035F;
		head.yRot += Mth.clamp(s.yRot, -60, 60) * Mth.DEG_TO_RAD * 0.7F;
		head.xRot += Mth.clamp(s.xRot, -40, 40) * Mth.DEG_TO_RAD * 0.5F - 0.1F * fly;
		crown.zRot += Mth.sin(t * 0.31F) * 0.012F * free;
		// Its tail ripples in a long wave from the waist down, and streams out behind as it flies
		for (int i = 0; i < tail.length; i++) {
			float w = 0.6F + 0.4F * i;
			tail[i].xRot += (Mth.sin(t * 0.12F - i * 0.9F) * 0.11F * w + 0.18F * fly) * free + 0.12F * bound;
			tail[i].zRot += Mth.cos(t * 0.09F - i * 0.9F) * 0.09F * w * free;
		}
		for (int i = 0; i < shroud.length; i++) {
			shroud[i].xRot += Mth.sin(t * 0.06F + i * 0.8F) * 0.05F * free + 0.18F * fly;
			shroud[i].zRot += Mth.cos(t * 0.05F + i * 1.2F) * 0.03F * free;
		}
		for (int i = 0; i < sleeves.length; i++) {
			sleeves[i].xRot += Mth.sin(t * 0.08F + i * 1.4F) * 0.08F * free + 0.25F * fly;
			sleeves[i].zRot += Mth.cos(t * 0.07F + i) * 0.05F * free;
		}
		for (int i = 0; i < chains.length; i++) {
			chains[i].zRot += Mth.sin(t * 0.09F + i * 2.1F) * 0.18F * free;
			chains[i].xRot += Mth.cos(t * 0.07F + i) * 0.12F * free + 0.4F * fly;
		}
		// Its long arms drift and its claws flex
		armL.zRot += Mth.sin(t * 0.05F) * 0.05F;
		armR.zRot -= Mth.sin(t * 0.05F + 1.3F) * 0.05F;
		armL.xRot += Mth.sin(t * 0.04F + 0.5F) * 0.06F - 0.25F * fly;
		armR.xRot += Mth.sin(t * 0.04F + 2.1F) * 0.06F - 0.25F * fly;
		handL.xRot += Mth.sin(t * 0.11F) * 0.1F;
		handR.xRot += Mth.sin(t * 0.11F + 1.7F) * 0.1F;

		switch (action) {
			case StarvedOne.SWOOP -> {
				// arms flung back, then both claws driven out ahead as it falls
				float back = ElderAnim.env(at, 4, 1, 3);
				float drive = ElderAnim.snap((at - 4) / 5F) * (1 - ElderAnim.smooth((at - 44) / 8F));
				armL.xRot += 0.9F * back - 1.5F * drive;
				armR.xRot += 0.9F * back - 1.5F * drive;
				armL.zRot -= 0.3F * back - 0.2F * drive;
				armR.zRot += 0.3F * back - 0.2F * drive;
				foreL.xRot -= 0.3F * drive;
				foreR.xRot -= 0.3F * drive;
				body.xRot += -0.15F * back + 0.3F * drive;
				head.xRot += 0.2F * drive;
			}
			case StarvedOne.CLAW -> {
				// the right arm winds back and rakes across, the whole body twisting with it
				float wind = ElderAnim.smooth(at / 3F), cut = ElderAnim.snap((at - 3) / 4F), home = ElderAnim.smooth((at - 10) / 12F);
				float k = 1 - home;
				armR.xRot += (-1.9F * wind + 1.3F * cut) * k;
				armR.yRot += (0.7F * wind - 1.3F * cut) * k;
				armR.zRot += (0.3F * wind - 0.9F * cut) * k;
				foreR.xRot -= 0.5F * wind * k;
				armL.xRot -= 0.6F * ElderAnim.env(at, 3, 4, 10);
				body.yRot += (0.35F * wind - 0.5F * cut) * k;
				head.yRot += (0.2F * wind - 0.3F * cut) * k;
			}
			case StarvedOne.CALL -> {
				// it rears up, its maw to the sky, arms flung up and wide, shaking with the call
				float e = ElderAnim.env(at, 10, 26, 14);
				float shake = ElderAnim.tremor(t, 0.05F * e);
				armL.zRot -= 1.7F * e;
				armR.zRot += 1.7F * e;
				armL.xRot -= 0.35F * e + shake;
				armR.xRot -= 0.35F * e - shake;
				foreL.zRot -= 0.45F * e;
				foreR.zRot += 0.45F * e;
				head.xRot -= 0.7F * e;
				body.xRot -= 0.35F * e;
				body.zRot += shake;
				crown.zRot += shake * 3;
				root.y -= 3F * e;
			}
			case StarvedOne.BITE -> {
				// its head draws back, then snaps forward
				float draw = ElderAnim.env(at, 4, 1, 2);
				float lunge = ElderAnim.snap((at - 5) / 3F) * (1 - ElderAnim.smooth((at - 10) / 10F));
				head.xRot += -0.4F * draw + 0.7F * lunge;
				head.z += 2F * draw - 5F * lunge;
				body.xRot += -0.15F * draw + 0.35F * lunge;
				armL.zRot -= 0.5F * lunge;
				armR.zRot += 0.5F * lunge;
			}
			case StarvedOne.DRAG -> {
				// it reaches out wide and hauls back to its chest
				float reach = ElderAnim.smooth(at / 6F), haul = ElderAnim.snap((at - 8) / 5F), home = ElderAnim.smooth((at - 18) / 10F);
				float k = 1 - home;
				armL.xRot += (-1.5F * reach + 1.0F * haul) * k;
				armR.xRot += (-1.5F * reach + 1.0F * haul) * k;
				armL.zRot -= 0.4F * reach * (1 - haul) * k;
				armR.zRot += 0.4F * reach * (1 - haul) * k;
				foreL.xRot += (-0.2F * reach - 1.0F * haul) * k;
				foreR.xRot += (-0.2F * reach - 1.0F * haul) * k;
				body.xRot += (0.25F * reach - 0.25F * haul) * k;
			}
			case StarvedOne.BLINK -> {
				// it folds in on itself and opens again, out of the veil
				float open = ElderAnim.snap(at / 8F);
				float k = 0.55F + 0.45F * open;
				body.xScale = k;
				body.zScale = k;
				body.yScale = 0.8F + 0.2F * open;
			}
			default -> {
			}
		}

		// The weight of its blows: it gathers (draws back, strains), then throws itself into the blow and rebounds
		float[] ac = ElderAnim.accent(at, impacts(action), 6, 10);
		float g = ac[0] * free, j = ac[1] * free;
		root.y += 1.5F * g + 1.5F * j;
		root.z -= 2.4F * j;
		body.xRot += -0.14F * g + 0.2F * j;
		body.zRot += ElderAnim.tremor(t, 0.03F * g);
		head.xRot += -0.1F * g + 0.15F * j;

		if (bound > 0) {
			// Chained by the pillars: arms dragged out and down, back arched, head hanging, shuddering
			float shake = Mth.sin(t * 1.9F) * 0.035F * bound;
			armL.zRot -= 0.75F * bound;
			armR.zRot += 0.75F * bound;
			armL.xRot += 0.45F * bound + shake;
			armR.xRot += 0.45F * bound - shake;
			foreL.xRot += 0.3F * bound;
			foreR.xRot += 0.3F * bound;
			body.xRot -= 0.35F * bound + shake;
			head.xRot += 0.6F * bound;
			head.zRot += shake * 2;
			crown.zRot += shake * 2;
		}

		float rise = s.rising;
		if (rise < 1) {
			// Rising out of the Heart: upright in the hole, claws hooked over its rim, dragging itself up hand over hand
			float reach = ElderAnim.smooth(rise / 0.25F), climb = ElderAnim.smooth((rise - 0.6F) / 0.4F);
			float loose = 1 - ElderAnim.smooth((rise - 0.92F) / 0.08F);
			float pull = Mth.sin(climb * Mth.PI * 6);
			float strain = ElderAnim.tremor(t, 0.05F * reach);
			float k = reach * loose;
			body.xRot -= 0.45F * k * (1 - 0.5F * climb);
			armL.xRot -= (2.2F - 0.6F * Math.max(0, pull)) * k;
			armR.xRot -= (2.2F - 0.6F * Math.max(0, -pull)) * k;
			armL.zRot += 0.3F * k;
			armR.zRot -= 0.3F * k;
			foreL.xRot -= 0.7F * Math.max(0, pull) * k;
			foreR.xRot -= 0.7F * Math.max(0, -pull) * k;
			handL.xRot += 0.8F * k;
			handR.xRot += 0.8F * k;
			head.xRot += (0.6F - 0.9F * climb) * k + strain;
			body.zRot += strain;
			crown.zRot += strain * 2;
			root.y += 1.2F * pull * climb * loose;
			for (int i = 0; i < tail.length; i++) {
				tail[i].xRot -= 0.25F * k;
			}
			for (int i = 0; i < chains.length; i++) {
				chains[i].xRot += (0.7F + Mth.sin(t * 0.3F + i) * 0.12F) * k;
			}
		}

		float die = s.dying;
		if (die > 0) {
			// Dying: it thrashes, then sags; its chains snap away one after another, and it sinks
			float sag = ElderAnim.smooth(die / 0.7F), thrash = 1 - ElderAnim.smooth(die / 0.6F);
			float shake = ElderAnim.tremor(t * 1.5F, 0.12F * thrash);
			body.xRot += 0.55F * sag + shake;
			body.zRot += shake * 0.8F;
			head.xRot += 0.75F * sag;
			head.zRot += 0.2F * sag;
			armL.xRot += 0.6F * sag + shake;
			armR.xRot += 0.6F * sag - shake;
			armL.zRot += 0.35F * sag;
			armR.zRot -= 0.35F * sag;
			foreL.xRot += 0.3F * sag;
			foreR.xRot += 0.3F * sag;
			crown.zRot += 0.35F * sag;
			crown.xRot += 0.2F * sag;
			root.y += 10F * ElderAnim.smooth((die - 0.55F) / 0.45F);
			for (int i = 0; i < tail.length; i++) {
				tail[i].xRot -= 0.15F * sag;
			}
			for (int i = 0; i < chains.length; i++) {
				// (the server snaps the chain to pillar i at 40 + 25 i of its two hundred ticks: see HeartScenes)
				float snapAt = (40 + 25 * i) / (float) StarvedOne.DEATH_TICKS;
				if (die > snapAt) {
					float gone = ElderAnim.smooth((die - snapAt) / 0.06F);
					chains[i].xRot += 1.6F * gone;
					ElderAnim.scale(chains[i], Math.max(0.02F, 1 - 0.98F * gone));
				}
			}
		}
	}

	/** When each of its blows lands (ticks of the action, as the server strikes them). */
	private static float[] impacts(int action) {
		return switch (action) {
			case StarvedOne.SWOOP -> new float[] {5};
			case StarvedOne.CLAW -> new float[] {4};
			case StarvedOne.CALL -> new float[] {12};
			case StarvedOne.BITE -> new float[] {6};
			case StarvedOne.DRAG -> new float[] {9};
			default -> ElderAnim.NONE;
		};
	}
}
