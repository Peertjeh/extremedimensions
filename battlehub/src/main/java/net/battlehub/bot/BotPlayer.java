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

	/** A knockback the server sent to the bot's (missing) client, taken at the start of its next tick. */
	private volatile net.minecraft.world.phys.Vec3 knock;
	private net.minecraft.world.phys.Vec3 lastKnock = net.minecraft.world.phys.Vec3.ZERO;

	void knocked(net.minecraft.world.phys.Vec3 motion) {
		knock = motion;
	}

	/** The last knockback the bot took (for the self-test). */
	public net.minecraft.world.phys.Vec3 lastKnock() {
		return lastKnock;
	}

	/** Ticks the bot does not think (no keys, no turning): only the game moves it (self-test). */
	private int held;

	public void hold(int ticks) {
		held = ticks;
	}

	@Override
	public void tick() {
		net.minecraft.world.phys.Vec3 k = knock;
		if (k != null) {
			knock = null;
			lastKnock = k;
			setDeltaMovement(k);
		}
		// A client confirms it finished loading after joining or changing dimension; until then a player is invulnerable
		if (!connection.hasClientLoaded()) {
			connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		}
		if (isChangingDimension()) {
			hasChangedDimension();
		}
		try {
			if (held > 0) {
				held--;
				zza = xxa = 0;
				setJumping(false);
				setSprinting(false);
			} else {
				brain.tick();
			}
		} catch (RuntimeException e) {
			if (!warned) {
				warned = true;
				BattleHub.LOGGER.warn("Bot {} stumbled: {}", getGameProfile().name(), e.toString());
			}
		}
		super.tick();
		double x0 = getX(), y0 = getY(), z0 = getZ();
		try {
			doTick();
		} catch (RuntimeException ignored) {
			// Some client-driven state is missing for bots; physics still runs.
		}
		fall(x0, y0, z0);
	}

	/** Where the bot was at the end of its last tick (more than a few blocks from there at the start of the next: a teleport). */
	private net.minecraft.world.phys.Vec3 lastEnd;

	/**
	 * A client tells the server how its player moved, and the server counts the fall from that: a fall hurts, a hit on
	 * the way down is a crit, the mace smashes. The server's own physics of a player leave the fall alone, so a bot never
	 * fell (no crits, no smash, no fall damage) until 1.46. Its physics run here, so its fall is counted here, the way
	 * the server counts a client's move. A teleport (a respawn, the start of a round) is no fall.
	 */
	private void fall(double x0, double y0, double z0) {
		if (lastEnd != null && lastEnd.distanceToSqr(x0, y0, z0) > 16) resetFallDistance();
		doCheckFallDamage(getX() - x0, getY() - y0, getZ() - z0, onGround());
		lastEnd = position();
	}
}
