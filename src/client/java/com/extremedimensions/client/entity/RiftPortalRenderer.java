package com.extremedimensions.client.entity;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.seam.RiftPortal;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Draws a rift as a tear in the world, after the rifts of Ratchet &amp; Clank: Rift Apart. From the back of it to the
 * front: the world it leads to, hazy at the far end of a tunnel; five rings of streaking light down the tunnel, each
 * turning against the one before; a faint shimmering skin across the opening; cracks running out into the air around
 * it like broken glass; two burning rims of magenta, turning opposite ways so they flicker; and chips of rock torn
 * loose, tumbling round it. The tunnel always runs away from whoever looks into it, on either side, so it has depth
 * from both; it is drawn in the opening itself, as seen from where the viewer stands, so a wall behind a rift does not
 * cut it off. Everything but the rock is its own light.
 */
public class RiftPortalRenderer extends EntityRenderer<RiftPortal, RiftPortalRenderer.State> {
	private static final Identifier RIM_A = tex("rim_a"), RIM_B = tex("rim_b"), CRACKS = tex("cracks"), TUNNEL = tex("tunnel"),
			HAZE = tex("haze"), SHARD = tex("shard");
	private static final Identifier[] VIEWS = {tex("view_overworld_day"), tex("view_overworld_night"), tex("view_nether"), tex("view_seam")};
	/** The ring's radius as a fraction of the rim texture's half-size (where the generator drew it). */
	private static final float RIM_AT = 0.6F;
	private static final int FULL_BRIGHT = 0xF000F0;

	private static Identifier tex(String name) {
		return ExtremeDimensions.id("textures/entity/rift/" + name + ".png");
	}

	public RiftPortalRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0;
	}

	public static class State extends EntityRenderState {
		float radius, yaw, open, time;
		int view;
		boolean hidden;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(RiftPortal portal, State state, float partialTick) {
		super.extractRenderState(portal, state, partialTick);
		state.radius = portal.radius();
		state.yaw = portal.getYRot();
		state.open = portal.openness(partialTick);
		state.time = portal.tickCount + partialTick;
		state.view = Mth.clamp(portal.view(), 0, VIEWS.length - 1);
		state.hidden = !portal.seen();
	}

	@Override
	protected AABB getBoundingBoxForCulling(RiftPortal portal, float partialTick) {
		float r = portal.radius() * 2.2F;
		return portal.getBoundingBox().inflate(r, r * 1.2, r);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		float r = state.radius * state.open;
		if (r < 0.02F || state.hidden) {
			return;
		}
		float cy = state.radius + 0.15F;
		double yaw = Math.toRadians(state.yaw);
		double fx = -Math.sin(yaw), fz = Math.cos(yaw), sx = Math.cos(yaw), sz = Math.sin(yaw);
		// The viewer in the opening's own frame: how far in front of it (on whichever side), how far across and up
		double ex = camera.pos.x - state.x, ez = camera.pos.z - state.z;
		double along = ex * fx + ez * fz;
		float side = along >= 0 ? 1 : -1;
		View v = new View((float) Math.max(0.35, Math.abs(along)), (float) (ex * sx + ez * sz), (float) (camera.pos.y - state.y - cy), r, side);
		float t = state.time;
		pose.pushPose();
		pose.translate(0, cy, 0);
		pose.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(-state.yaw)));
		int layer = 0;
		// The world at the far end, hazy
		layer = disc(out, pose, v, layer, RenderTypes.entityTranslucentEmissive(VIEWS[state.view]), r * 1.15F, 2.6F, 0, alpha(0.97F));
		// Rings of light down the tunnel, the far ones first
		for (int k = 4; k >= 0; k--) {
			float spin = (k % 2 == 0 ? 1 : -1) * t * (1.6F - k * 0.15F) + k * 37;
			layer = disc(out, pose, v, layer, RenderTypes.entityTranslucentEmissive(TUNNEL), r * (1.16F - 0.03F * k), 0.25F + 0.5F * k, spin,
					alpha(0.78F - 0.08F * k));
		}
		// The shimmering skin across the opening
		layer = disc(out, pose, v, layer, RenderTypes.entityTranslucentEmissive(HAZE), r * 1.6F, 0, t * 0.4F, alpha(0.85F));
		// Cracks in the air round it
		layer = disc(out, pose, v, layer, RenderTypes.entityTranslucentEmissive(CRACKS), r * 2.1F, 0, t * 0.15F + 11,
				alpha(0.8F + 0.2F * Mth.sin(t * 0.21F)));
		// The burning rims, turning opposite ways
		float half = r / RIM_AT;
		layer = disc(out, pose, v, layer, RenderTypes.entityTranslucentEmissive(RIM_A), half, 0, t * 1.1F, 0xFFFFFFFF);
		disc(out, pose, v, layer, RenderTypes.entityTranslucentEmissive(RIM_B), half * 1.02F, 0, -t * 0.8F + 40, alpha(0.82F + 0.18F * Mth.sin(t * 0.37F)));
		// Chips of rock tumbling round it
		RenderType rock = RenderTypes.entityCutout(SHARD);
		for (int i = 0; i < 9; i++) {
			float a = i * 0.698F + t * (0.012F + 0.004F * (i % 3)) * (i % 2 == 0 ? 1 : -1);
			float orbit = r * (1.22F + 0.18F * ((i * 7) % 5) / 4F);
			float bob = Mth.sin(t * 0.05F + i) * 0.12F;
			float size = 0.09F + 0.05F * ((i * 5) % 4);
			cube(out, pose, rock, Mth.cos(a) * orbit, Mth.sin(a) * orbit + bob, side * (0.12F * ((i % 3) - 1)), size, t * (1.5F + i * 0.4F), i);
		}
		pose.popPose();
	}

	/**
	 * Where the viewer stands in the opening's frame: {@code e} blocks in front of it, {@code x} across and {@code y}
	 * up from its middle, on {@code side} (+1 in front, -1 behind); {@code r} is how far open it is.
	 */
	private record View(float e, float x, float y, float r, float side) {}

	private static int alpha(float a) {
		return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | 0xFFFFFF;
	}

	/**
	 * A square sprite seen {@code depth} blocks down the tunnel, turned {@code spin} degrees. It is not put there (the
	 * tunnel would go into whatever stands behind the rift): it is drawn in the opening itself, a hair nearer the viewer
	 * than the layer before it, where and as large as the viewer would see it from that depth, so it shifts and shrinks
	 * as they move like something far off. Each layer is drawn in its own turn, back to front. Returns the next turn.
	 */
	private static int disc(SubmitNodeCollector out, PoseStack pose, View v, int layer, RenderType type, float half, float depth, float spin, int argb) {
		float s = v.e() / (v.e() + depth);
		// past the rim it would be hidden by the edge of the hole; keep it within
		float off = (1 - s) * Mth.sqrt(v.x() * v.x() + v.y() * v.y()), most = v.r() * 0.5F;
		float shift = (1 - s) * (off > most ? most / off : 1);
		float ox = v.x() * shift, oy = v.y() * shift;
		float size = half * s, z = v.side() * 0.015F * layer, side = v.side();
		float c = Mth.cos(spin * Mth.DEG_TO_RAD), sn = Mth.sin(spin * Mth.DEG_TO_RAD);
		out.order(1 + layer).submitCustomGeometry(pose, type, (p, vc) -> {
			// wound to face the viewer's side
			for (int n = 0; n < 4; n++) {
				float[] k = CORNERS[side > 0 ? n : 3 - n];
				float x = ox + (k[0] * c - k[1] * sn) * size, y = oy + (k[0] * sn + k[1] * c) * size;
				vertex(p, vc, x, y, z, k[2], k[3], argb, side);
			}
		});
		return layer + 1;
	}

	private static final float[][] CORNERS = {{-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}};

	/** A small cube of rock at (x, y, z) in the opening's frame, tumbling. */
	private static void cube(SubmitNodeCollector out, PoseStack pose, RenderType type, float x, float y, float z, float size, float spin, int seed) {
		pose.pushPose();
		pose.translate(x, y, z);
		pose.mulPose(new Matrix4f().rotation(Axis.XP.rotationDegrees(spin + seed * 31)));
		pose.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(spin * 0.7F + seed * 53)));
		pose.scale(size, size * (0.7F + 0.1F * (seed % 4)), size);
		out.submitCustomGeometry(pose, type, (p, vc) -> {
			float[][][] faces = {
				{{-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}}, {{1, -1, -1}, {-1, -1, -1}, {-1, 1, -1}, {1, 1, -1}},
				{{1, -1, 1}, {1, -1, -1}, {1, 1, -1}, {1, 1, 1}}, {{-1, -1, -1}, {-1, -1, 1}, {-1, 1, 1}, {-1, 1, -1}},
				{{-1, 1, 1}, {1, 1, 1}, {1, 1, -1}, {-1, 1, -1}}, {{-1, -1, -1}, {1, -1, -1}, {1, -1, 1}, {-1, -1, 1}}};
			float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
			for (float[][] f : faces) {
				float nx = (f[0][0] + f[2][0]) / 2, ny = (f[0][1] + f[2][1]) / 2, nz = (f[0][2] + f[2][2]) / 2;
				for (int n = 0; n < 4; n++) {
					vc.addVertex(p, f[n][0], f[n][1], f[n][2]).setColor(0xFFFFFFFF).setUv(uv[n][0], uv[n][1])
							.setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, nx, ny, nz);
				}
			}
		});
		pose.popPose();
	}

	private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int argb, float nz) {
		vc.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, 0, 0, nz);
	}
}
