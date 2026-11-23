package com.extremedimensions.gametest;

import java.lang.reflect.Method;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * 3.56: every client screenshot goes through here. Normally the shot is the size of the test window (854x480); with
 * ED_SHOT_SIZE set (the [gallery] CI run sets 1920x1080) a view of the world is drawn again at that size, without the
 * HUD, for the store pages and the trailer (a screen stays at the window's size). The sized options are looked up by reflection, so a Fabric API without them still builds and the shot
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
		// (a screen is laid out for the window, not the larger frame: it would sit small in a corner, so it is taken at
		// the window's size; the world is drawn large with the HUD hidden, as a store page or a trailer wants it)
		if (SIZE != null && !failed && !ctx.computeOnClient(mc -> com.extremedimensions.client.title.Menus.current() != null)) {
			boolean hidden = ctx.computeOnClient(mc -> hudHidden(mc));
			ctx.runOnClient(mc -> hud(mc, true));
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
			} finally {
				ctx.runOnClient(mc -> hud(mc, hidden));
			}
		}
		ctx.takeScreenshot(name);
	}

	private static java.lang.reflect.Field hideField;
	private static Object hideOwnerKind;
	private static boolean hideLooked;

	/** Whether the HUD is hidden (F1); where 26.3 keeps that switch is looked up once, by name. */
	private static boolean hudHidden(net.minecraft.client.Minecraft mc) {
		Object owner = hideOwner(mc);
		try {
			return owner != null && hideField.getBoolean(owner);
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	private static void hud(net.minecraft.client.Minecraft mc, boolean hide) {
		Object owner = hideOwner(mc);
		if (owner == null) {
			return;
		}
		try {
			hideField.setBoolean(owner, hide);
		} catch (ReflectiveOperationException e) {
			System.out.println("SHOT-SIZE cannot hide the HUD: " + e);
		}
	}

	private static Object hideOwner(net.minecraft.client.Minecraft mc) {
		Object[] owners = { mc.options, mc.gui, mc };
		if (!hideLooked) {
			hideLooked = true;
			StringBuilder seen = new StringBuilder();
			for (int i = 0; i < owners.length && hideField == null; i++) {
				for (Class<?> c = owners[i].getClass(); c != null && c != Object.class && hideField == null; c = c.getSuperclass()) {
					for (java.lang.reflect.Field f : c.getDeclaredFields()) {
						String n = f.getName().toLowerCase(java.util.Locale.ROOT);
						if (f.getType() == boolean.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())
								&& (n.contains("hide") || n.contains("hidden"))) {
							seen.append(c.getSimpleName()).append('.').append(f.getName()).append(' ');
							if ((n.contains("hide") && (n.contains("gui") || n.contains("hud"))) || n.equals("hidden") || n.equals("hudhidden")) {
								f.setAccessible(true);
								hideField = f;
								hideOwnerKind = i;
							}
						}
					}
				}
			}
			System.out.println("SHOT-SIZE HUD switch: " + (hideField == null ? "none" : hideField) + " (fields seen: " + seen + ")");
		}
		return hideField == null ? null : owners[(Integer) hideOwnerKind];
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
