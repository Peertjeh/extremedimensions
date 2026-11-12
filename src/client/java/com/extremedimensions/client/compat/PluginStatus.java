package com.extremedimensions.client.compat;

/**
 * Whether the tooltip and map mods took the mod's plugins, for the compatibility test: kept free of their types, as
 * {@link JeiStatus} is, so the test can ask without their classes on its path. Each plugin sets its own when its mod
 * calls it.
 */
public final class PluginStatus {
	private PluginStatus() {}

	/** Jade and WTHIT registered the mod's tooltips (on the client). */
	public static volatile boolean jade, wthit;
	/** JourneyMap started the mod's plugin. */
	public static volatile boolean journeymap;
	/** Waypoints JourneyMap was last handed (-1: none yet). */
	public static volatile int journeymapWaypoints = -1;
}
