package com.extremedimensions.client.sky;

import org.joml.Matrix4f;

import com.extremedimensions.ExtremeDimensions;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * The sky of the Seam: a high-resolution cubemap of nebulae, a galaxy band, a vortex turning at the zenith, pillars
 * of light rising from a sea of clouds and the silhouettes of far islands, drawn in place of the End sky whenever the
 * player stands in the Seam. All six faces live in one 3x2 atlas.
 */
public final class SeamSky {
	private SeamSky() {}

	private static final Identifier SEAM = Identifier.fromNamespaceAndPath("extremedimensions", "the_seam");
	private static final Identifier TEXTURE = ExtremeDimensions.id("textures/environment/seam_sky.png");
	private static GpuBuffer buffer;
	private static AbstractTexture texture;
	private static boolean failed;

	/** Faces in atlas order: forward, right, up (the right vector is forward x up, so each quad faces inward). */
	private static final float[][][] FACES = {
			{{1, 0, 0}, {0, 0, 1}, {0, 1, 0}},     // east
			{{-1, 0, 0}, {0, 0, -1}, {0, 1, 0}},   // west
			{{0, 1, 0}, {-1, 0, 0}, {0, 0, -1}},   // up
			{{0, -1, 0}, {-1, 0, 0}, {0, 0, 1}},   // down
			{{0, 0, 1}, {-1, 0, 0}, {0, 1, 0}},    // south
			{{0, 0, -1}, {1, 0, 0}, {0, 1, 0}},    // north
	};

	private static boolean inSeam(Minecraft mc) {
		return mc.level != null && SEAM.equals(mc.level.dimension().identifier());
	}

	/** True while the Seam sky should replace the End sky (and everything for it is ready). */
	public static boolean active() {
		return !failed && buffer != null && texture != null && inSeam(Minecraft.getInstance());
	}

	/**
	 * Loads the texture and builds the geometry on the client tick, outside any render pass: uploads are not allowed
	 * while the sky pass is open.
	 */
	public static void prepare(Minecraft mc) {
		if (failed || !inSeam(mc) || (buffer != null && texture != null)) {
			return;
		}
		try {
			texture = mc.getTextureManager().getTexture(TEXTURE);
			if (buffer == null) {
				buffer = build();
			}
		} catch (RuntimeException e) {
			failed = true;
			ExtremeDimensions.LOGGER.warn("Could not prepare the Seam sky, falling back to the End sky", e);
		}
	}

	public static AbstractTexture texture() {
		return texture;
	}

	public static GpuBuffer buffer() {
		return buffer;
	}

	private static GpuBuffer build() {
		float d = 100.0f;
		float inset = 0.5f / 1024.0f;
		Matrix4f m = new Matrix4f();
		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(24 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
			BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
			for (int i = 0; i < 6; i++) {
				float[] f = FACES[i][0], r = FACES[i][1], u = FACES[i][2];
				float u0 = (i % 3) / 3.0f + inset / 3, u1 = (i % 3 + 1) / 3.0f - inset / 3;
				float v0 = (i / 3) / 2.0f + inset / 2, v1 = (i / 3 + 1) / 2.0f - inset / 2;
				// top-left, bottom-left, bottom-right, top-right of the face image
				float[][] corners = {{-1, 1}, {-1, -1}, {1, -1}, {1, 1}};
				float[][] uvs = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
				for (int k = 0; k < 4; k++) {
					float s = corners[k][0], t = corners[k][1];
					b.addVertex(m, (f[0] + s * r[0] + t * u[0]) * d, (f[1] + s * r[1] + t * u[1]) * d, (f[2] + s * r[2] + t * u[2]) * d)
							.setUv(uvs[k][0], uvs[k][1]).setColor(-1);
				}
			}
			try (MeshData mesh = b.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(() -> "Seam sky", 40, mesh.vertexBuffer());
			}
		}
	}
}
