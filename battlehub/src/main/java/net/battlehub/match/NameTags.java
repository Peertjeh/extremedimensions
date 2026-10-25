package net.battlehub.match;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.battlehub.bot.Bots;
import net.battlehub.config.ConfigManager;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

/**
 * Name tags only up close (1.49). In a match a player sees an enemy's name tag only within {@code combat.nameTagRange}
 * blocks with nothing in between; farther off or behind a wall it is gone. Vanilla draws it up to 64 blocks away and
 * through walls when they do not sneak, which gives away where everyone is. Teammates keep theirs; spectators and the
 * lobby see every tag ({@code combat.nameTagRange} 0: vanilla everywhere).
 * <p>
 * Done per viewer, with team packets only that viewer gets; the server's own teams (and with them titles and name
 * colours) stay as they are. A player in a team gets a copy of it with the tag hidden on that viewer's screen. A player in
 * none gets a team of their own there. Refreshed every few ticks and sent again now and then, since the server sends a
 * team to everyone again whenever it changes it.
 */
public final class NameTags {
	private NameTags() {}

	/** How often (ticks) who sees whose tag is worked out, and how often a hidden tag is hidden again regardless. */
	static final int EVERY = 5, AGAIN = 40;
	/** The name of the team a player without one gets on a viewer's screen. */
	static final String OWN = "bht.";

	/** What each viewer has hidden: viewer, then player name, then the team name used for it. */
	private static final Map<UUID, Map<String, String>> HIDDEN = new HashMap<>();
	private static long tick;

	public static void tick(MinecraftServer server) {
		if (++tick % EVERY != 0) return;
		double range = ConfigManager.config().combat.nameTagRange;
		boolean again = tick % AGAIN == 0;
		Set<UUID> viewers = new HashSet<>();
		if (range > 0) {
			for (Match m : Matches.all()) {
				if (m.phase() != Match.Phase.RUNNING && m.phase() != Match.Phase.COUNTDOWN) continue;
				for (UUID vid : m.participants()) {
					ServerPlayer v = m.player(vid);
					if (v == null || Bots.isBot(v) || v.isSpectator() || !m.inPlay(vid)) continue;
					viewers.add(vid);
					Set<String> hide = new HashSet<>();
					for (UUID pid : m.participants()) {
						// rivals, also in a moment of peace (the start of the Hunger Games): where they are is theirs to keep
						if (pid.equals(vid) || !m.inPlay(pid) || !m.isRival(vid, pid)) continue;
						ServerPlayer p = m.player(pid);
						if (p == null || p.level() != v.level() || p.isSpectator()) continue;
						if (!shown(v, p, range)) hide.add(p.getScoreboardName());
					}
					update(v, hide, again);
				}
			}
		}
		// not in a match any more (or the setting off): every tag back
		for (Iterator<Map.Entry<UUID, Map<String, String>>> it = HIDDEN.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Map<String, String>> e = it.next();
			if (viewers.contains(e.getKey())) continue;
			ServerPlayer v = server.getPlayerList().getPlayer(e.getKey());
			if (v != null) e.getValue().forEach((name, team) -> show(v, name, team));
			it.remove();
		}
	}

	/** Whether a viewer sees this player's tag: close enough, with nothing in between. */
	public static boolean shown(ServerPlayer viewer, ServerPlayer p, double range) {
		return viewer.distanceToSqr(p) <= range * range && viewer.hasLineOfSight(p);
	}

	/**
	 * Whether a match hides this player's tag from someone at this distance (for bots, who used to find people behind
	 * walls by their tags).
	 */
	public static boolean hides(Match m, double dist) {
		double range = ConfigManager.config().combat.nameTagRange;
		return range > 0 && m != null && dist > range;
	}

	/** Whose tags a viewer has hidden now (for the self-test). */
	public static Set<String> hidden(UUID viewer) {
		Map<String, String> h = HIDDEN.get(viewer);
		return h == null ? Set.of() : Set.copyOf(h.keySet());
	}

	private static void update(ServerPlayer v, Set<String> hide, boolean again) {
		Map<String, String> now = HIDDEN.computeIfAbsent(v.getUUID(), k -> new HashMap<>());
		for (String name : hide) {
			String was = now.get(name);
			if (was == null || again) now.put(name, hide(v, name, was));
		}
		for (Iterator<Map.Entry<String, String>> it = now.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, String> e = it.next();
			if (hide.contains(e.getKey())) continue;
			show(v, e.getKey(), e.getValue());
			it.remove();
		}
	}

	/** Hides a player's tag on one viewer's screen; the team name used. {@code was}: the one used before, or null. */
	private static String hide(ServerPlayer v, String name, String was) {
		PlayerTeam real = v.level().getServer().getScoreboard().getPlayersTeam(name);
		Scoreboard dummy = new Scoreboard();
		if (real != null) {
			PlayerTeam copy = copy(dummy, real);
			copy.setNameTagVisibility(Team.Visibility.NEVER);
			v.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(copy, false));
			// a team of its own from before (the server has given them a team since): gone
			if (was != null && was.startsWith(OWN)) v.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(dummy.addPlayerTeam(was)));
			return real.getName();
		}
		String own = OWN + Integer.toHexString(name.hashCode());
		PlayerTeam t = dummy.addPlayerTeam(own);
		t.setNameTagVisibility(Team.Visibility.NEVER);
		dummy.addPlayerToTeam(name, t);
		if (own.equals(was)) {
			// there already: hidden again, and the player put back in it (the server may have moved them)
			v.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(t, false));
			v.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(t, name, ClientboundSetPlayerTeamPacket.Action.ADD));
		} else {
			v.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(t, true));
		}
		return own;
	}

	/** Shows a player's tag on one viewer's screen again: their real team as it is, or their own team gone. */
	private static void show(ServerPlayer v, String name, String team) {
		if (v.hasDisconnected()) return;
		if (team.startsWith(OWN)) {
			v.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(new Scoreboard().addPlayerTeam(team)));
			return;
		}
		PlayerTeam real = v.level().getServer().getScoreboard().getPlayerTeam(team);
		if (real != null) v.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(real, false));
	}

	/** A copy of a team (on a scoreboard of its own) with the same look and rules. */
	private static PlayerTeam copy(Scoreboard dummy, PlayerTeam real) {
		PlayerTeam c = dummy.addPlayerTeam(real.getName());
		c.setDisplayName(real.getDisplayName());
		c.setPlayerPrefix(real.getPlayerPrefix());
		c.setPlayerSuffix(real.getPlayerSuffix());
		c.setColor(real.getColor());
		c.setAllowFriendlyFire(real.isAllowFriendlyFire());
		c.setSeeFriendlyInvisibles(real.canSeeFriendlyInvisibles());
		c.setNameTagVisibility(real.getNameTagVisibility());
		c.setDeathMessageVisibility(real.getDeathMessageVisibility());
		c.setCollisionRule(real.getCollisionRule());
		return c;
	}

	public static void clear() {
		HIDDEN.clear();
	}
}
