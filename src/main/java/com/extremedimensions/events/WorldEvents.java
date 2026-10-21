package com.extremedimensions.events;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.extremedimensions.config.Config;
import com.extremedimensions.elder.FerrymanService;
import com.extremedimensions.elder.MatriarchService;
import com.extremedimensions.elder.WeaverService;
import com.extremedimensions.mending.StarvedOne;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The wounds remember: things that happen in the world because of what was done in it. Each event has a trigger, a
 * warning, what happens, an end and a cleanup, all in its own class; here is what they share. For one player events
 * keep their distance ({@link Config#EVENT_SPACING} seconds between two, one at a time), and none starts during an
 * Elder fight. The Wound Storms keep to the same rules. An echo of a Collapse is a place, not a happening: it shows to
 * whoever comes near, whenever.
 */
public final class WorldEvents {
	private WorldEvents() {}

	/** The events an operator can start ({@code /tether event <id>}). */
	public static final List<String> IDS = List.of("storm", "echo", "unstable", "unanchored", "frayed");

	/** When each player's last event began (server ticks), and which is under way for them. */
	private static final Map<UUID, Long> LAST = new HashMap<>();
	private static final Map<UUID, String> ACTIVE = new HashMap<>();

	public static void tick(MinecraftServer s) {
		Echoes.tick(s);
		UnstableAnchors.tick(s);
		Unanchored.tick(s);
		FrayedSeam.tick(s);
	}

	/** Whether a new event may begin for a player now. */
	public static boolean free(ServerPlayer p, long now) {
		if (ACTIVE.containsKey(p.getUUID()) || p.isSpectator() || inFight(p)) {
			return false;
		}
		Long last = LAST.get(p.getUUID());
		return last == null || now - last >= Math.max(0, Config.EVENT_SPACING) * 20L;
	}

	/** An event begins for a player (it holds them until {@link #end}). */
	public static void begin(ServerPlayer p, String id, long now) {
		LAST.put(p.getUUID(), now);
		ACTIVE.put(p.getUUID(), id);
	}

	/** A moment's event (a storm's start) counts for the spacing without holding the player. */
	public static void mark(ServerPlayer p, long now) {
		LAST.put(p.getUUID(), now);
	}

	public static void end(UUID id, String event) {
		ACTIVE.remove(id, event);
	}

	/** The event under way for a player, or null. */
	public static String active(UUID id) {
		return ACTIVE.get(id);
	}

	/** An Elder hunts this player, or the Starved One is near: no event interrupts that. */
	public static boolean inFight(ServerPlayer p) {
		if (MatriarchService.hunting(p) || FerrymanService.hunting(p) || WeaverService.hunting(p)) {
			return true;
		}
		return !p.level().getEntitiesOfClass(StarvedOne.class, p.getBoundingBox().inflate(64), e -> e.isAlive()).isEmpty();
	}

	/** Drain multiplier the events lay on a player beyond the wound (an echo being listened to near them). */
	public static double drainFactor(ServerPlayer p) {
		return Echoes.drainFactor(p);
	}

	/**
	 * Starts an event for a player at once, whatever its chance and spacing (an operator's command, the tests). Returns
	 * a language key saying why it could not, or null when it began.
	 */
	public static String force(ServerPlayer p, String id) {
		long now = p.level().getServer().getTickCount();
		return switch (id) {
			case "storm" -> {
				com.extremedimensions.world.WorldService.begin(p, (int) now);
				yield null;
			}
			case "echo" -> Echoes.force(p);
			case "unstable" -> UnstableAnchors.force(p);
			case "unanchored" -> Unanchored.force(p, now);
			case "frayed" -> FrayedSeam.force(p, now);
			default -> "commands.extremedimensions.event.unknown";
		};
	}

	/** Leaving a world: everything under way stops and is cleaned up. */
	public static void clear() {
		LAST.clear();
		ACTIVE.clear();
		Unanchored.clear();
		FrayedSeam.clear();
	}
}
