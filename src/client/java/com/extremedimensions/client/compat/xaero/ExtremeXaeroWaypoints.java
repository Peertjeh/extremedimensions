package com.extremedimensions.client.compat.xaero;

import java.util.List;
import java.util.Locale;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.client.compat.MapMarks;
import com.extremedimensions.client.compat.PluginStatus;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.resources.Identifier;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;

/**
 * The mod's waypoints in Xaero's Minimap (3.54.0): the player's anchors (Ember gold, Void purple) and in the Seam the
 * Return Gate (yellow), from {@link MapMarks}, as third-party waypoints of their own origin. Handed over the way Xaero's
 * Minimap feeds in its own Waystones support (read from its jar by CI's probe): the minimap's current session, its
 * world's root container, the container of the waypoint's dimension, and there the third-party waypoints of an origin.
 * They are the server's to give and not kept on disk: every hand-over takes back all of the mod's from every dimension
 * and lays the new ones.
 *
 * <p>Started only where Xaero's Minimap is loaded, so its classes are never touched without it.
 */
public final class ExtremeXaeroWaypoints {
	/** The origin the mod's waypoints go under (Xaero's Minimap keeps each origin's apart). */
	private static final Identifier ORIGIN = ExtremeDimensions.id("anchors");
	private static List<MapMarks.Mark> pending = List.of();
	/** Whether the last marks are still to be handed over (the minimap's session comes a little after a world does). */
	private static boolean dirty;
	private static boolean warned;

	private ExtremeXaeroWaypoints() {}

	public static void start() {
		MapMarks.listen(marks -> {
			pending = marks;
			dirty = true;
			hand();
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (dirty) {
				hand();
			}
		});
		PluginStatus.xaero = true;
	}

	private static void hand() {
		try {
			MinimapSession session = (MinimapSession) BuiltInHudModules.MINIMAP.getCurrentSession();
			if (session == null) {
				return;
			}
			MinimapWorldRootContainer root = session.getWorldManager().getAutoRootContainer();
			if (root == null) {
				return;
			}
			for (Object c : root.getSubContainers()) {
				if (c instanceof MinimapWorldContainer container) {
					container.getThirdPartyWaypointManager().clearOrigin(ORIGIN);
				}
			}
			int n = 0;
			for (MapMarks.Mark m : pending) {
				String dimension = session.getDimensionHelper().getDimensionDirectoryName(m.dimension());
				MinimapWorldContainer container = root.addSubContainer(root.getPath().resolve(dimension));
				ThirdPartyWaypoints waypoints = container.getThirdPartyWaypointManager().get(ORIGIN);
				String name = m.name();
				String initials = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
				waypoints.add(m.kind() + "@" + m.pos().getX() + "," + m.pos().getY() + "," + m.pos().getZ(),
						new Waypoint(m.pos().getX(), m.pos().getY(), m.pos().getZ(), name, initials, colour(m.kind()), WaypointPurpose.NORMAL));
				n++;
			}
			PluginStatus.xaeroWaypoints = n;
			dirty = false;
		} catch (RuntimeException | LinkageError e) {
			// (a waypoint Xaero's Minimap will not take is no reason to stop the game; nor to try every tick)
			dirty = false;
			if (!warned) {
				warned = true;
				ExtremeDimensions.LOGGER.warn("Xaero's Minimap did not take the mod's waypoints", e);
			}
		}
	}

	/** The nearest of Xaero's colours to the mark's own. */
	private static WaypointColor colour(MapMarks.Kind kind) {
		return switch (kind) {
			case EMBER -> WaypointColor.GOLD;
			case VOID -> WaypointColor.PURPLE;
			case GATE, ARRIVAL -> WaypointColor.YELLOW;
		};
	}
}
