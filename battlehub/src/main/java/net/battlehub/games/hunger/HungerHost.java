package net.battlehub.games.hunger;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.battlehub.arena.Arena;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** What the Gamemakers, the sponsors, the vaults and the power-ups need of a Hunger Games match. */
public interface HungerHost {
	Arena arena();

	ServerLevel level();

	/** The tributes still in the game (alive, in play, not spectating). */
	List<ServerPlayer> tributes();

	/** Everyone who should hear about the game: the players and the watchers. */
	List<ServerPlayer> audience();

	/** Ticks of play. */
	int runTicks();

	Random rng();

	/** The border's width now (blocks, square round the plaza). */
	double borderSize();

	/** The middle of the plaza, at its floor. */
	net.minecraft.world.phys.Vec3 plaza();

	String name(UUID id);

	/** Whether the two are enemies now (the peace aside). */
	boolean isEnemy(UUID a, UUID b);
}
