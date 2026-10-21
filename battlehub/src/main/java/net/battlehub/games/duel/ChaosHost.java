package net.battlehub.games.duel;

import java.util.List;
import java.util.function.Consumer;

import net.battlehub.games.bedwars.BuildRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/** A match the Chaos events can run in ({@link ChaosDirector}): The Bridge: Chaos and its 1v1v1v1. */
public interface ChaosHost {
	/** Everyone still in the match, online. */
	List<ServerPlayer> inPlay();

	ServerLevel level();

	/** What players built (crumbling, cobwebs, bombs), or null before the arena stands. */
	BuildRules buildRules();

	/** The middle of the map (power-ups, magnet, shockwave). */
	BlockPos middle();

	RandomSource rng();

	MinecraftServer srv();

	/** Everyone who watches the match too (sounds). */
	void everyone(Consumer<ServerPlayer> action);

	/** How far a spot is from the middle (the crumbling takes what is close to it). */
	int fromMiddle(BlockPos p);

	/** The straight ways between the islands at their height (glass and ice bridges, rain, bombs). */
	List<BlockPos> bridgeLine();

	void sendHome(ServerPlayer p);

	/** The kit again (and what comes with it). */
	void restock(ServerPlayer p);
}
