package net.battlehub.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** The lobby gadgets and how long each needs before it can be used again (seconds). */
public final class Gadgets {
	private Gadgets() {}

	public static final Map<String, Integer> COOLDOWN = new LinkedHashMap<>();

	static {
		COOLDOWN.put("confetti", 6);
		COOLDOWN.put("firework", 8);
		COOLDOWN.put("rainbow", 10);
		COOLDOWN.put("disco", 20);
		COOLDOWN.put("bubbles", 8);
		COOLDOWN.put("wand", 5);
		COOLDOWN.put("thunder", 15);
		COOLDOWN.put("snowglobe", 15);
		COOLDOWN.put("smoke", 12);
		COOLDOWN.put("popper", 4);
		COOLDOWN.put("kiss", 6);
		COOLDOWN.put("boombox", 25);
	}

	public static boolean known(String id) {
		return COOLDOWN.containsKey(id);
	}

	/** Ticks until the gadget used at {@code lastUse} may be used again at {@code now} (0: ready). */
	public static int ticksLeft(String id, long lastUse, long now) {
		if (lastUse < 0) return 0;
		long ready = lastUse + COOLDOWN.getOrDefault(id, 5) * 20L;
		return (int) Math.max(0, ready - now);
	}
}
