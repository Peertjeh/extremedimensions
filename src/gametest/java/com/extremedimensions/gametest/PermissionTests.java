package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.extremedimensions.admin.AdminService;
import com.extremedimensions.config.SettingsService;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.network.Sync;
import com.extremedimensions.server.Perms;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.permission.v1.PermissionContext;
import net.fabricmc.fabric.api.permission.v1.PermissionEvents;
import net.fabricmc.fabric.api.permission.v1.PermissionNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * 3.29: the mod's permissions. Without a permission mod each falls back to the operator level it always needed; a
 * permission mod (here a stand-in, answering only for the test's own players) grants or denies each one alone.
 */
public class PermissionTests {
	/** What the stand-in permission mod says, per player: permission path to granted or denied. Unlisted: no answer. */
	private static final Map<UUID, Map<String, Boolean>> ANSWERS = new ConcurrentHashMap<>();
	private static boolean registered;

	/** Registers the stand-in once (events cannot be taken back); it answers only for players in {@link #ANSWERS}. */
	private static synchronized void standIn() {
		if (registered) {
			return;
		}
		registered = true;
		PermissionEvents.ON_REQUEST.register(new PermissionEvents.OnRequest() {
			@Override
			@SuppressWarnings("unchecked")
			public <T> T handlePermissionRequest(PermissionContext context, PermissionNode<T> permission) {
				Map<String, Boolean> mine = ANSWERS.get(context.uuid());
				if (mine == null || !"extremedimensions".equals(permission.key().getNamespace())) {
					return null;
				}
				return (T) mine.get(permission.key().getPath());
			}
		});
	}

	/** With no permission mod: a player without operator rights holds none of them, the server console holds all. */
	@GameTest
	public void withoutAPermissionModTheOperatorLevelsStand(GameTestHelper helper) {
		standIn();
		ServerPlayer p = TestPlayers.survivor(helper, "test-plain");
		CommandSourceStack console = helper.getLevel().getServer().createCommandSourceStack();
		for (Perms.Node n : Perms.Node.values()) {
			check(helper, !Perms.has(p, n), "a player without operator rights holds %s", n.dotted());
			check(helper, !Perms.has(p.createCommandSourceStack(), n), "a player's commands hold %s without operator rights", n.dotted());
			check(helper, Perms.has(console, n), "the server console lacks %s", n.dotted());
		}
		check(helper, !SettingsService.canEdit(p) && !AdminService.allowed(p), "a player without operator rights may change settings or open the Players screen");
		check(helper, "extremedimensions.admin.players".equals(Perms.Node.PLAYERS.dotted()), "the Players permission is written %s",
				Perms.Node.PLAYERS.dotted());
		helper.succeed();
	}

	/**
	 * A permission mod grants one permission alone: the Players screen (and its commands) without the settings or
	 * calling bosses; and what it denies stays denied.
	 */
	@GameTest
	public void aPermissionModGrantsOnePermissionAlone(GameTestHelper helper) {
		standIn();
		ServerPlayer p = TestPlayers.survivor(helper, "test-moderator");
		ServerPlayer target = TestPlayers.survivor(helper, "test-target");
		Map<String, Boolean> mine = new ConcurrentHashMap<>();
		mine.put(Perms.Node.PLAYERS.id.getPath(), true);
		mine.put(Perms.Node.SPAWN.id.getPath(), false);
		ANSWERS.put(p.getUUID(), mine);
		try {
			check(helper, Perms.has(p, Perms.Node.PLAYERS) && Perms.has(p.createCommandSourceStack(), Perms.Node.PLAYERS),
					"the permission granted is not held");
			check(helper, AdminService.allowed(p), "the Players screen stays shut to a player granted it");
			// (the admin flag is the ninth part of the Curses screen's data; the Fresh Wound's follows it since 3.49)
			String[] data = Sync.cursesData(p, DataStore.get(p.getUUID())).split("\\|", -1);
			check(helper, data.length > 8 && "1".equals(data[8]), "the Curses screen does not offer the Players screen");
			check(helper, AdminService.act(p, target.getStringUUID(), "add", com.extremedimensions.penalty.Penalty.GLASS_JAW.id) == null,
					"a player granted the Players screen could not lay a curse on");
			check(helper, !SettingsService.canEdit(p), "granting the Players screen let them change the settings");
			check(helper, !Perms.has(p, Perms.Node.SPAWN) && !Perms.has(p, Perms.Node.SEAM_RESET) && !Perms.has(p, Perms.Node.RELIC_RESET)
					&& !Perms.has(p, Perms.Node.EXPORT),
					"granting the Players screen gave other permissions too");
			// Taken away again: a permission mod's no stands
			mine.put(Perms.Node.PLAYERS.id.getPath(), false);
			check(helper, !AdminService.allowed(p), "the Players screen stays open after the permission was taken away");
		} finally {
			ANSWERS.remove(p.getUUID());
		}
		helper.succeed();
	}
}
