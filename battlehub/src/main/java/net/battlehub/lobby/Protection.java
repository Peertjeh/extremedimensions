package net.battlehub.lobby;

import net.battlehub.config.ConfigManager;
import net.battlehub.session.Sessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;

/** Who may build in the lobby. */
public final class Protection {
	private Protection() {}

	/** Operators in creative mode may still build the lobby. */
	public static boolean canBuild(Player player) {
		return player instanceof ServerPlayer sp && sp.isCreative() && sp.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	public static boolean isAdmin(ServerPlayer p) {
		return p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	/** Breaking a block: never in matches (games remove blocks themselves), not in the lobby unless building. */
	public static boolean mayBreak(ServerPlayer p) {
		Sessions.State s = Sessions.state(p.getUUID());
		if (s == Sessions.State.MATCH || s == Sessions.State.SPECTATING) {
			return false;
		}
		return !ConfigManager.config().protection.protectLobbyBlocks || canBuild(p) || !Lobby.inArea(p);
	}

	/**
	 * Beds and respawn anchors explode in BattleHub's worlds (they only work in the overworld and the nether), so using
	 * one does nothing at all. Sneaking with an item still places it against the block.
	 */
	public static boolean blocksSleep(ServerPlayer p, net.minecraft.world.level.block.state.BlockState state, ItemStack held) {
		net.minecraft.world.level.block.Block b = state.getBlock();
		if (!(b instanceof net.minecraft.world.level.block.BedBlock) && !(b instanceof net.minecraft.world.level.block.RespawnAnchorBlock)) {
			return false;
		}
		if (p.isSecondaryUseActive() && !held.isEmpty()) {
			return false;
		}
		if (Sessions.state(p.getUUID()) != Sessions.State.MATCH) {
			net.battlehub.util.Msg.actionBar(p, "lobby.no_sleep");
		}
		return true;
	}

	/** Placing blocks or emptying buckets. */
	public static boolean mayPlace(ServerPlayer p, ItemStack held) {
		if (!(held.getItem() instanceof BlockItem) && !(held.getItem() instanceof BucketItem)) {
			return true;
		}
		return mayBreak(p);
	}
}
