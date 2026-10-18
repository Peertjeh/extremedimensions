package net.battlehub.bot;

import com.mojang.authlib.GameProfile;

import net.battlehub.BattleHub;
import net.battlehub.core.BotLevel;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * A bot: a real player on the server without a client, so every game treats it like anyone else. Its brain
 * sets the inputs a client would send (walking, looking, jumping, hitting, using items) and it runs its own
 * physics, like a connected player's client would.
 */
public final class BotPlayer extends ServerPlayer {
	/** How well it plays (one of six levels). */
	public final net.battlehub.core.ai.Difficulty difficulty;
	/** The old three-step level the non-fighting game styles still read. */
	public final BotLevel skill;
	final BotBrain brain;
	private boolean warned;

	BotPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, net.battlehub.core.ai.Difficulty difficulty) {
		super(server, level, profile, ClientInformation.createDefault());
		this.difficulty = difficulty;
		this.skill = difficulty.legacy();
		this.brain = new BotBrain(this);
	}

	@Override
	public void tick() {
		// A client confirms it finished loading after joining or changing dimension; until then a player is invulnerable
		if (!connection.hasClientLoaded()) {
			connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		}
		if (isChangingDimension()) {
			hasChangedDimension();
		}
		try {
			brain.tick();
		} catch (RuntimeException e) {
			if (!warned) {
				warned = true;
				BattleHub.LOGGER.warn("Bot {} stumbled: {}", getGameProfile().name(), e.toString());
			}
		}
		super.tick();
		try {
			doTick();
		} catch (RuntimeException ignored) {
			// Some client-driven state is missing for bots; physics still runs.
		}
	}
}
