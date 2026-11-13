package com.extremedimensions.client.compat.journeymap;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.client.compat.MapMarks;
import com.extremedimensions.client.compat.PluginStatus;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;

/**
 * The mod in JourneyMap: the player's anchors as waypoints, Ember or Void in their own colours, while they are in the
 * Seam the Return Gate, and on an expedition the portal they came through (3.55.0; all of it from {@link MapMarks}).
 * The waypoints are JourneyMap's own, kept for this session only: the server sends the anchors again on every join,
 * and takes them off when they break or hold nothing of the player's any more.
 *
 * <p>Loaded by JourneyMap alone, through the {@code journeymap} entrypoint, so it is never touched without JourneyMap.
 *
 * <p>Built against JourneyMap's plugin API 2.0.0 as its jar has it ({@code WaypointFactory.createWaypoint(modId, pos,
 * name, dimension, persistent)}, read by CI's probe).
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class ExtremeJourneyMapPlugin implements IClientPlugin {
	private IClientAPI api;
	/** The waypoints handed to JourneyMap, to be taken back before the next ones. */
	private final List<Waypoint> placed = new ArrayList<>();

	@Override
	public void initialize(IClientAPI api) {
		this.api = api;
		MapMarks.listen(this::show);
		PluginStatus.journeymap = true;
	}

	@Override
	public String getModId() {
		return ExtremeDimensions.MOD_ID;
	}

	private void show(List<MapMarks.Mark> marks) {
		try {
			for (Waypoint w : placed) {
				api.removeWaypoint(getModId(), w);
			}
			placed.clear();
			for (MapMarks.Mark m : marks) {
				// (not kept on disk: they are the server's to give, and come again with the next join)
				Waypoint w = WaypointFactory.createWaypoint(getModId(), m.pos(), m.name(), m.dimension(), false);
				w.setColor(m.colour());
				api.addWaypoint(getModId(), w);
				placed.add(w);
			}
			PluginStatus.journeymapWaypoints = placed.size();
		} catch (RuntimeException e) {
			// (a waypoint JourneyMap will not take is no reason to stop the game)
			ExtremeDimensions.LOGGER.warn("JourneyMap did not take the mod's waypoints", e);
		}
	}
}
