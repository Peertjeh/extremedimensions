package com.extremedimensions.server;

import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;

/** The world's own rules as the mod reads them (mobGriefing is read where blocks change: ElderTerrain, ElderArena). */
public final class Rules {
	private Rules() {}

	/**
	 * On peaceful nothing of the wound comes to fight: no Lurker finds anyone, no Keeper wakes or hunts, no Elder comes,
	 * nothing leaks out of a storm, and what is out goes, as vanilla's monsters do (their types are not allowed in
	 * peaceful). The Starved One still waits at the Heart, as the Ender Dragon waits in the End.
	 */
	public static boolean peaceful(Level level) {
		return level.getDifficulty() == Difficulty.PEACEFUL;
	}
}
