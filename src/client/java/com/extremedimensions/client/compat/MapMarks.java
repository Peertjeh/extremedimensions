package com.extremedimensions.client.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.client.state.ClientState;
import com.extremedimensions.client.ui.Ui;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.server.Dims;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * What a map mod shows as waypoints: the player's anchors, each where the server says it stands, in the Ember's orange
 * or the Void's violet; while they are in the Seam the Return Gate, where the rift home hangs, and on an expedition in
 * the Nether or the End the portal they came through (3.55.0), both in the gold of the way home. Kept free of any map
 * mod's types: a map mod's plugin listens, and is handed the whole list whenever it changes.
 */
public final class MapMarks {
	private MapMarks() {}

	public enum Kind { EMBER, VOID, GATE, ARRIVAL }

	/** One waypoint: what it marks, where, in which world, under which name and colour (0xRRGGBB). */
	public record Mark(Kind kind, BlockPos pos, ResourceKey<Level> dimension, String name, int colour) {}

	/** The gold of the way home, as the HUD's compass draws it. */
	private static final int GOLD = 0xFFCC66;
	private static final ResourceKey<Level> SEAM = ResourceKey.create(Registries.DIMENSION, ExtremeDimensions.id(Dims.SEAM));
	/** The rift wanders over its threshold: the gate's mark stays where it is while the rift stays this near it. */
	private static final int GATE_SLACK = 4;
	/** Ticks a new world is given before its marks are handed over (a map mod takes a world up a little after it loads). */
	private static final int SETTLE = 60;

	private static final List<Consumer<List<Mark>>> LISTENERS = new CopyOnWriteArrayList<>();
	private static List<ModPackets.AnchorsPayload.Spot> anchors = List.of();
	private static List<Mark> shown = List.of();
	private static BlockPos gate;
	private static Object world;
	private static int settle;

	/** A map mod's plugin: handed every mark there is now, each time they change (on the client's thread). */
	public static void listen(Consumer<List<Mark>> listener) {
		LISTENERS.add(listener);
	}

	/** The marks as last handed over. */
	public static List<Mark> shown() {
		return shown;
	}

	/** The server's list of the player's anchors. */
	public static void anchors(List<ModPackets.AnchorsPayload.Spot> list) {
		anchors = List.copyOf(list);
	}

	/** Leaving a world: its anchors are no longer the player's to see. */
	public static void reset() {
		anchors = List.of();
		gate = null;
	}

	/**
	 * Every client tick: the marks are handed over when they change (looked at twice a second), and all of them again a
	 * little after the player comes into a world, a new one or through a portal, in case the map mod let them go.
	 */
	public static void tick(Minecraft mc) {
		if (LISTENERS.isEmpty()) {
			return;
		}
		if (mc.level == null) {
			world = null;
			if (!shown.isEmpty()) {
				hand(List.of());
			}
			return;
		}
		if (mc.level != world) {
			world = mc.level;
			settle = SETTLE;
		}
		if (settle > 0) {
			if (--settle == 0) {
				hand(marks());
			}
			return;
		}
		if (mc.level.getGameTime() % 10 == 0) {
			List<Mark> now = marks();
			if (!now.equals(shown)) {
				hand(now);
			}
		}
	}

	private static void hand(List<Mark> marks) {
		shown = marks;
		for (Consumer<List<Mark>> l : LISTENERS) {
			l.accept(marks);
		}
	}

	private static List<Mark> marks() {
		List<Mark> out = new ArrayList<>();
		String ember = ModBlocks.EMBER_ANCHOR.getName().getString(), voidName = ModBlocks.VOID_ANCHOR.getName().getString();
		for (ModPackets.AnchorsPayload.Spot s : anchors) {
			out.add(s.ember() ? new Mark(Kind.EMBER, new BlockPos(s.x(), s.y(), s.z()), Level.OVERWORLD, ember, Ui.EMBER)
					: new Mark(Kind.VOID, new BlockPos(s.x(), s.y(), s.z()), Level.OVERWORLD, voidName, Ui.VOID));
		}
		if (ClientState.riftKnown()) {
			BlockPos at = BlockPos.containing(ClientState.riftX, ClientState.riftY, ClientState.riftZ);
			if (gate == null || gate.distSqr(at) > GATE_SLACK * GATE_SLACK) {
				gate = at;
			}
			out.add(new Mark(Kind.GATE, gate, SEAM, Component.translatable("gui.extremedimensions.map.return_gate").getString(), GOLD));
		} else {
			gate = null;
		}
		if (ClientState.homeKnown()) {
			// The portal the expedition came through, in the world it opens into (it stays where it is: no slack wanted)
			ResourceKey<Level> where = ClientState.dimCode == 1 ? Level.NETHER : Level.END;
			out.add(new Mark(Kind.ARRIVAL, BlockPos.containing(ClientState.riftX, ClientState.riftY, ClientState.riftZ), where,
					Component.translatable("gui.extremedimensions.map.arrival_portal").getString(), GOLD));
		}
		return out;
	}
}
