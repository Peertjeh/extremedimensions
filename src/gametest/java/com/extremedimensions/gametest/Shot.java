package com.extremedimensions.gametest;

import java.lang.reflect.Method;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * 3.56: every client screenshot goes through here. Normally the shot is the size of the test window (854x480); with
 * ED_SHOT_SIZE set (the [gallery] CI run sets 1920x1080) the frame is drawn again at that size for the store pages and
 * the trailer. The sized options are looked up by reflection, so a Fabric API without them still builds and the shot
 * falls back to the window size, said once in the log.
 */
final class Shot {
	private static final int[] SIZE = parse(System.getenv("ED_SHOT_SIZE"));
	private static Method of;
	private static Method withSize;
	private static Method take;
	private static boolean failed;

	private Shot() {
	}

	static void take(ClientGameTestContext ctx, String name) {
		if (SIZE != null && !failed) {
			try {
				if (take == null) {
					Class<?> options = Class.forName("net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions");
					of = options.getMethod("of", String.class);
					withSize = options.getMethod("withSize", int.class, int.class);
					take = ClientGameTestContext.class.getMethod("takeScreenshot", options);
				}
				Object opts = withSize.invoke(of.invoke(null, name), SIZE[0], SIZE[1]);
				take.invoke(ctx, opts);
				return;
			} catch (ReflectiveOperationException | RuntimeException e) {
				failed = true;
				System.out.println("SHOT-SIZE fallback to the window size: " + e);
			}
		}
		ctx.takeScreenshot(name);
	}

	private static int[] parse(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		String[] p = s.trim().toLowerCase().split("x");
		try {
			int w = Integer.parseInt(p[0]);
			int h = Integer.parseInt(p[1]);
			return w >= 320 && h >= 240 && w <= 3840 && h <= 2160 ? new int[] { w, h } : null;
		} catch (RuntimeException e) {
			return null;
		}
	}
}
