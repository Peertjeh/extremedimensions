package com.extremedimensions.coop;

import java.util.function.Predicate;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Who crawls: the fallen. On the server that is {@link FallenService}; on a client, what the server last said (a
 * client sets {@link #client} when it starts). The pose is forced on both sides, so the fallen player sees the world
 * from the ground and moves at a crawl, and everyone else sees them crawl.
 */
public final class Crawl {
	private Crawl() {}

	/** On a client: whether a player there is fallen. */
	public static volatile Predicate<Player> client = p -> false;

	public static boolean crawls(Player p) {
		return p instanceof ServerPlayer sp ? FallenService.fallen(sp) : client.test(p);
	}
}
