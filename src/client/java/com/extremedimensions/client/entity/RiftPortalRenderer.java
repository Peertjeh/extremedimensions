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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

/**
 * Draws a rift the way Minecraft draws its portals: a frame of blocks round the opening and a swirling skin across it.
 *
 * <p>The frame is a ring of half-size blocks of obsidian, crying rift stone and rift stone bricks, laid out on a grid
 * the way a ring is built in the world (a stepped circle, thicker at some points than others, a block missing here
 * and there), lit by the world like any block, with its cracks and drips glowing. As the rift tears open its blocks
 * fly in and settle from the opening outward; as it closes they shrink away. Inside the frame, from the back to the
 * front: the world it leads to, a small hazy pixel painting at the far end of a tunnel; rings of streaking light down
 * the tunnel, each turning against the one before; the swirling skin, like a nether portal's, thin enough in the middle
 * to see through; and a glowing lip round the opening. The tunnel runs away from whoever looks into it, on either
 * side, and is drawn in the opening itself as seen from where the viewer stands, so a wall behind a rift does not cut
 * it off. Every texture keeps Minecraft's pixel size (about sixteen to a block).
 */
public class RiftPortalRenderer extends EntityRenderer<RiftPortal, RiftPortalRenderer.State> {
	private static final Identifier FRAME = tex("frame"), FRAME_GLOW = tex("frame_glow"), EDGE = tex("edge"), MEMBRANE = tex("membrane"),
			TUNNEL = tex("tunnel");
	private static final Identifier[] VIEWS = {tex("view_overworld_day"), tex("view_overworld_night"), tex("view_nether"), tex("view_seam")};
	private static final int FULL_BRIGHT = 0xF000F0;
	/** The frame's blocks: half a block each, on a grid in the opening's plane. */
	private static final float CELL = 0.5F;
	/** How far from its middle a rift home's frame may reach across (the passage of its gate is five blocks wide). */
	private static final float GATE_HALF = 2.5F;
	/** Faces of the frame's blocks in frame.png: obsidian, crying rift stone, rift stone bricks, cracked rift stone. */
	private static final int OBSIDIAN = 0, CRYING = 1, BRICKS = 2, CRACKED = 3;
	private static final int MEMBRANE_FRAMES = 8;

	private static Identifier tex(String name) {
		return ExtremeDimensions.id("textures/entity/rift/" + name + ".png");
	}

	public RiftPortalRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0;
	}

	public static class State extends EntityRenderState {
		float radius, yaw, open, time;
		int view, light, seed;
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
		state.seed = portal.getUUID().hashCode();
		// The frame is lit by the world where the ring stands, as a block there would be
		BlockPos at = BlockPos.containing(portal.getX(), portal.getY() + portal.radius() + 0.15, portal.getZ());
		int block = portal.level().getBrightness(LightLayer.BLOCK, at), sky = portal.level().getBrightness(LightLayer.SKY, at);
		state.light = Math.max(block, 3) << 4 | sky << 20;
	}

	@Override
	protected AABB getBoundingBoxForCulling(RiftPortal portal, float partialTick) {
		float r = portal.radius() + 2;
		return portal.getBoundingBox().inflate(r, r * 1.4, r);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		float r = state.radius * state.open;
		if (state.open < 0.01F || state.hidden) {
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
		if (r > 0.05F) {
			// The world at the far end, hazy
			layer = disc(out, pose, v, layer, RenderTypes.entityTranslucentEmissive(VIEWS[state.view]), r * 1.1F, 2.6F, 0, alpha(0.97F), 0, 1);
			// Rings of light down the tunnel, the far ones first
			for (int k = 3; k >= 0; k--) {
				float spin = (k % 2 == 0 ? 1 : -1) * t * (1.4F - k * 0.15F) + k * 37;
				layer = disc(out, pose, v, layer, RenderTypes.entityTranslucentEmissive(TUNNEL), r * (1.08F - 0.03F * k), 0.3F + 0.55F * k, spin,
						alpha(0.75F - 0.1F * k), 0, 1);
			}
			// The swirling skin, a frame of it at a time, like a nether portal's
			int frame = (int) (t / 2.5F) % MEMBRANE_FRAMES;
			layer = disc(out, pose, v, layer, RenderTypes.entityTranslucentEmissive(MEMBRANE), r, 0, 0, alpha(0.9F), frame / (float) MEMBRANE_FRAMES,
					(frame + 1) / (float) MEMBRANE_FRAMES);
			// The glowing lip just inside the frame
			disc(out, pose, v, layer, RenderTypes.entityTranslucentEmissive(EDGE), r * 1.06F, 0, 0, 0xFFFFFFFF, 0, 1);
		}
		frame(out, pose, state, t);
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

	/** A fixed fraction 0..1 for a cell of a frame (the same every frame, different for every rift). */
	private static float hash(int seed, int i, int j, int salt) {
		int h = seed ^ i * 0x27D4EB2D ^ j * 0x165667B1 ^ salt * 0x9E3779B9;
		h ^= h >>> 15;
		h *= 0x85EBCA6B;
		h ^= h >>> 13;
		return (h & 0xFFFF) / 65535F;
	}

	/**
	 * The frame: blocks on a half-block grid in the opening's plane, round the opening out to a band that thickens and
	 * thins as it goes round (never past the passage of a gate, for a rift home that stands in one). Some cells stand out
	 * a step, a few are missing, and a few sit a little loose of the rest. Lit by the world, with their glowing pixels
	 * drawn again at full brightness; and chips of the same stone tumbling round it.
	 */
	private static void frame(SubmitNodeCollector out, PoseStack pose, State state, float t) {
		float R = state.radius, open = state.open;
		int seed = state.seed;
		float p1 = hash(seed, 1, 0, 9) * 6.28F, p2 = hash(seed, 2, 0, 9) * 6.28F;
		float most = state.view != RiftPortal.SEAM ? GATE_HALF : 99;
		int n = (int) Math.ceil((R + 2) / CELL);
		java.util.List<float[]> cubes = new java.util.ArrayList<>();
		for (int i = -n; i < n; i++) {
			for (int j = -n; j < n; j++) {
				float cx = (i + 0.5F) * CELL, cyy = (j + 0.5F) * CELL;
				float d = Mth.sqrt(cx * cx + cyy * cyy);
				float a = (float) Math.atan2(cyy, cx);
				float wave = Math.max(0, 0.6F * Mth.sin(3 * a + p1) + 0.4F * Mth.sin(7 * a + p2));
				float band = CELL * (1.05F + 1.5F * wave) - hash(seed, i, j, 1) * CELL * 0.5F;
				if (d < R + CELL * 0.35F || d > R + band || Math.abs(cx) + CELL / 2 > most + 0.001F) {
					continue;
				}
				float h0 = hash(seed, i, j, 2);
				boolean outer = d > R + CELL * 1.1F;
				// a few blocks gone from the outer edge (never from under it, where it stands)
				if (outer && h0 < 0.08F && cyy > -R) {
					continue;
				}
				float h1 = hash(seed, i, j, 3);
				int face = d < R + CELL * 1.1F ? (h1 < 0.45F ? CRYING : h1 < 0.8F ? OBSIDIAN : CRACKED) : (h1 < 0.4F ? BRICKS : h1 < 0.8F ? OBSIDIAN : CRACKED);
				// they fly in from the opening outward as it tears open, and shrink away as it closes
				float delay = 0.5F * (d - R) / (CELL * 2.6F) + hash(seed, i, j, 4) * 0.25F;
				float grow = Mth.clamp((open - delay) / 0.35F, 0, 1);
				if (grow <= 0) {
					continue;
				}
				float loose = outer && h0 > 0.95F ? 0.12F + 0.2F * hash(seed, i, j, 5) : 0;
				float fly = (1 - grow) * 0.8F + loose;
				float ux = cx / Math.max(0.01F, d), uy = cyy / Math.max(0.01F, d);
				float hz = CELL / 2, z = 0;
				// some stand out a step, to one side or the other
				float h6 = hash(seed, i, j, 6);
				if (h6 < 0.3F) {
					hz = CELL * 0.75F;
					z = (h6 < 0.15F ? 1 : -1) * CELL * 0.25F;
				}
				float half = CELL / 2 * grow;
				cubes.add(new float[] {cx + ux * fly, cyy + uy * fly, z, half, half, hz * grow, face});
			}
		}
		// Chips of the same stone tumbling round it
		java.util.List<float[]> chips = new java.util.ArrayList<>();
		for (int i = 0; i < 7; i++) {
			float a = i * 0.898F + t * (0.01F + 0.004F * (i % 3)) * (i % 2 == 0 ? 1 : -1);
			float orbit = R + CELL * (2.2F + 0.9F * ((i * 7) % 5) / 4F);
			float bob = Mth.sin(t * 0.05F + i) * 0.12F;
			float size = (0.07F + 0.03F * ((i * 5) % 4)) * open;
			chips.add(new float[] {Mth.cos(a) * orbit, Mth.sin(a) * orbit + bob, 0.1F * ((i % 3) - 1), size, t * (1.2F + i * 0.3F), i});
		}
		int light = state.light;
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(FRAME), (p, vc) -> {
			for (float[] c : cubes) {
				box(p, vc, c[0], c[1], c[2], c[3], c[4], c[5], (int) c[6], light, 0xFFFFFFFF);
			}
		});
		out.submitCustomGeometry(pose, RenderTypes.eyes(FRAME_GLOW), (p, vc) -> {
			for (float[] c : cubes) {
				int face = (int) c[6];
				if (face == CRYING || face == CRACKED) {
					box(p, vc, c[0], c[1], c[2], c[3] * 1.002F, c[4] * 1.002F, c[5] * 1.002F, face, FULL_BRIGHT, 0xFFFFFFFF);
				}
			}
		});
		RenderType chip = RenderTypes.entityCutout(FRAME);
		for (float[] c : chips) {
			pose.pushPose();
			pose.translate(c[0], c[1], c[2]);
			pose.mulPose(new Matrix4f().rotation(Axis.XP.rotationDegrees(c[4] + c[5] * 31)));
			pose.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(c[4] * 0.7F + c[5] * 53)));
			float s = c[3];
			int face = ((int) c[5]) % 2 == 0 ? CRACKED : OBSIDIAN;
			out.submitCustomGeometry(pose, chip, (p, vc) -> box(p, vc, 0, 0, 0, s, s, s, face, light, 0xFFFFFFFF));
			pose.popPose();
		}
	}

	/**
	 * A square sprite seen {@code depth} blocks down the tunnel, turned {@code spin} degrees, its texture read from
	 * {@code v0} to {@code v1} (one frame of a strip). It is not put there (the tunnel would go into whatever stands
	 * behind the rift): it is drawn in the opening itself, a hair nearer the viewer than the layer before it, where and as
	 * large as the viewer would see it from that depth, so it shifts and shrinks as they move like something far off.
	 * Each layer is drawn in its own turn, back to front. Returns the next turn.
	 */
	private static int disc(SubmitNodeCollector out, PoseStack pose, View v, int layer, RenderType type, float half, float depth, float spin, int argb,
			float v0, float v1) {
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
				vertex(p, vc, x, y, z, k[2], v0 + (v1 - v0) * k[3], argb, side);
			}
		});
		return layer + 1;
	}

	private static final float[][] CORNERS = {{-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}};

	/** The six faces of a block, of half-size (hx, hy, hz) round (x, y, z), each showing one face of frame.png. */
	private static void box(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float hx, float hy, float hz, int face, int light, int argb) {
		float u0 = face / 4F, u1 = (face + 1) / 4F;
		float[][][] faces = {
			{{-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}}, {{1, -1, -1}, {-1, -1, -1}, {-1, 1, -1}, {1, 1, -1}},
			{{1, -1, 1}, {1, -1, -1}, {1, 1, -1}, {1, 1, 1}}, {{-1, -1, -1}, {-1, -1, 1}, {-1, 1, 1}, {-1, 1, -1}},
			{{-1, 1, 1}, {1, 1, 1}, {1, 1, -1}, {-1, 1, -1}}, {{-1, -1, -1}, {1, -1, -1}, {1, -1, 1}, {-1, -1, 1}}};
		float[][] uv = {{u0, 1}, {u1, 1}, {u1, 0}, {u0, 0}};
		for (float[][] f : faces) {
			float nx = (f[0][0] + f[2][0]) / 2, ny = (f[0][1] + f[2][1]) / 2, nz = (f[0][2] + f[2][2]) / 2;
			for (int n = 0; n < 4; n++) {
				vc.addVertex(p, x + f[n][0] * hx, y + f[n][1] * hy, z + f[n][2] * hz).setColor(argb).setUv(uv[n][0], uv[n][1])
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
			}
		}
	}

	private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int argb, float nz) {
		vc.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, 0, 0, nz);
	}
}
