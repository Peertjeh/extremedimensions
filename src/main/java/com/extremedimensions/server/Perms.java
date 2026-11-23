package com.extremedimensions.server;

import java.util.function.Predicate;

import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * The mod's permissions (3.29), through Fabric API's permission API: a permission mod such as LuckPerms grants or
 * denies each one to whom it will (LuckPerms writes them with dots: {@code extremedimensions.admin.players}). Without
 * one, each falls back to the operator level it always needed, so nothing changes for a server that has none.
 *
 * <p>The levels are fixed (not settings), so that whoever may change the settings cannot lower the bar for a permission
 * they lack.
 */
public final class Perms {
	private Perms() {}

	/** One permission: its id ({@code extremedimensions:admin/...}) and the operator level that stands in without a permission mod. */
	public enum Node {
		/** The Players screen, {@code /tether players}, {@code /tether curse} and {@code /tether tribe set}. */
		PLAYERS("admin/players", PermissionLevel.GAMEMASTERS),
		/** Changing the settings (the settings screen), {@code /tether preset}, {@code /tether overworld_penalties} and {@code /tether lurkers}. */
		SETTINGS("admin/settings", PermissionLevel.GAMEMASTERS),
		/** Calling things into the world: {@code boss}, {@code hoard}, {@code lurker}, {@code event}, {@code storm}, {@code seam}, {@code break_seal}, {@code seam_showcase}. */
		SPAWN("admin/spawn", PermissionLevel.GAMEMASTERS),
		/** {@code /tether export} and {@code /tether discord_test}. */
		EXPORT("admin/export", PermissionLevel.GAMEMASTERS),
		/** {@code /tether seam_reset}: wipes the Seam, so admins only (and the console), never command blocks. */
		SEAM_RESET("admin/seam_reset", PermissionLevel.ADMINS),
		/** {@code /tether relic_reset}: wipes every relic in the world, so admins only (and the console), never command blocks. */
		RELIC_RESET("admin/relic_reset", PermissionLevel.ADMINS);

		public final Identifier id;
		public final PermissionLevel level;

		Node(String path, PermissionLevel level) {
			this.id = Identifier.fromNamespaceAndPath("extremedimensions", path);
			this.level = level;
		}

		/** As a permission mod such as LuckPerms writes it: {@code extremedimensions.admin.players}. */
		public String dotted() {
			return id.getNamespace() + "." + id.getPath().replace('/', '.');
		}
	}

	/** Whether this command source holds the permission (granted by a permission mod, or by its operator level without one). */
	public static boolean has(CommandSourceStack src, Node node) {
		return ((PermissionContextOwner) (Object) src).checkPermission(node.id, node.level);
	}

	/** Whether this player holds the permission; the player of a world of their own (not opened to LAN) always does. */
	public static boolean has(ServerPlayer p, Node node) {
		MinecraftServer s = p.level().getServer();
		if (s != null && s.isSingleplayer() && !s.isPublished()) {
			return true;
		}
		return ((PermissionContextOwner) (Object) p).checkPermission(node.id, node.level);
	}

	/** For a command: only those who hold the permission see it and may run it. */
	public static Predicate<CommandSourceStack> require(Node node) {
		return src -> has(src, node);
	}
}
