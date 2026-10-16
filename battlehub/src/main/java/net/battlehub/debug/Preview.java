package net.battlehub.debug;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import net.battlehub.BattleHub;
import net.battlehub.arena.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Pictures of layouts for the self-test log: an isometric view of a blueprint (blocks in their map colours, the
 * tops light and the sides darker), written as a JPEG in base64 lines so it can be read back from the CI log.
 */
final class Preview {
	private Preview() {}

	private static final int SKY = 0x9FC6F0;

	/** An isometric picture seen from the south-east ({@code flip}: from the north-west); {@code a}: pixels per block. */
	static BufferedImage iso(Blueprint bp, int a, boolean flip) {
		Map<Long, BlockState> blocks = bp.blocks();
		int sx = flip ? -1 : 1;
		// Far to near: smaller x + z first (seen from the south-east), lower y first; only blocks with a face to show
		List<long[]> order = new ArrayList<>();
		int minCX = Integer.MAX_VALUE, maxCX = Integer.MIN_VALUE, minCY = Integer.MAX_VALUE, maxCY = Integer.MIN_VALUE;
		for (Map.Entry<Long, BlockState> e : blocks.entrySet()) {
			BlockState st = e.getValue();
			if (st.isAir()) continue;
			long k = e.getKey();
			int x = BlockPos.getX(k);
			int y = BlockPos.getY(k);
			int z = BlockPos.getZ(k);
			if (solid(blocks, x, y + 1, z) && solid(blocks, x + sx, y, z) && solid(blocks, x, y, z + sx)) continue;
			int cx = (x * sx - z * sx) * a;
			int cy = (x * sx + z * sx) * a / 2 - y * a;
			minCX = Math.min(minCX, cx);
			maxCX = Math.max(maxCX, cx);
			minCY = Math.min(minCY, cy);
			maxCY = Math.max(maxCY, cy);
			order.add(new long[] {(long) (x + z) * sx, y, k, cx, cy});
		}
		if (order.isEmpty()) {
			return null;
		}
		int width = maxCX - minCX + 2 * a + 2;
		int height = maxCY - minCY + 3 * a + 2;
		if (width > 2600 || height > 2600) {
			return null;
		}
		int ox = -minCX + a + 1;
		int oy = -minCY + a + 1;
		int[] px = new int[width * height];
		java.util.Arrays.fill(px, SKY);
		order.sort((p, q) -> p[0] != q[0] ? Long.compare(p[0], q[0]) : Long.compare(p[1], q[1]));
		for (long[] o : order) {
			int col = color(blocks.get(o[2]));
			if (col < 0) continue;
			int cx = ox + (int) o[3];
			int cy = oy + (int) o[4];
			// Top: a diamond; the two sides: one block high below its lower edges
			for (int dy = -a; dy <= 2 * a; dy++) {
				for (int dx = -a; dx <= a; dx++) {
					int pxX = cx + dx;
					int pxY = cy + dy;
					if (pxX < 0 || pxY < 0 || pxX >= width || pxY >= height) continue;
					double edge = a / 2.0 - Math.abs(dx) / 2.0;
					int c;
					if (Math.abs(dy) <= edge) {
						c = col;
					} else if (dy > edge && dy <= edge + a) {
						c = shade(col, dx < 0 ? 0.62 : 0.8);
					} else {
						continue;
					}
					px[pxY * width + pxX] = c;
				}
			}
		}
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, width, height, px, 0, width);
		return img;
	}

	private static boolean solid(Map<Long, BlockState> blocks, int x, int y, int z) {
		BlockState st = blocks.get(BlockPos.asLong(x, y, z));
		return st != null && !st.isAir() && st.canOcclude();
	}

	/** The block's map colour, -1 for blocks that show nothing (glass, air). */
	private static int color(BlockState st) {
		try {
			MapColor m = st.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
			return m == null || m.col == 0 ? -1 : m.col;
		} catch (RuntimeException e) {
			return 0x808080;
		}
	}

	private static int shade(int rgb, double f) {
		int r = (int) (((rgb >> 16) & 255) * f);
		int g = (int) (((rgb >> 8) & 255) * f);
		int b = (int) ((rgb & 255) * f);
		return (r << 16) | (g << 8) | b;
	}

	/** Writes the picture into the log as base64 JPEG lines: "SELFTEST IMG name part/total data". */
	static void log(String name, BufferedImage img) {
		if (img == null) {
			BattleHub.LOGGER.warn("SELFTEST IMG {} too big to draw", name);
			return;
		}
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(img, "jpg", out);
			String b64 = Base64.getEncoder().encodeToString(out.toByteArray());
			int chunk = 4000;
			int total = (b64.length() + chunk - 1) / chunk;
			for (int i = 0; i < total; i++) {
				BattleHub.LOGGER.info("SELFTEST IMG {} {}/{} {}", name, i + 1, total, b64.substring(i * chunk, Math.min(b64.length(), (i + 1) * chunk)));
			}
		} catch (Exception e) {
			BattleHub.LOGGER.warn("SELFTEST IMG {} failed: {}", name, e.toString());
		}
	}
}
