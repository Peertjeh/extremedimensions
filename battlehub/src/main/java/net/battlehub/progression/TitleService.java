package net.battlehub.progression;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.battlehub.config.ConfigManager;
import net.battlehub.core.PlayerProfile;
import net.battlehub.core.TitleDef;
import net.battlehub.core.TitleRules;
import net.battlehub.data.PlayerStore;
import net.battlehub.hub.HubWorld;
import net.battlehub.lang.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

/**
 * Shows the selected title in front of the name and the bought name colour in chat, above the head and in the tab
 * list while the player is in BattleHub, using one scoreboard team per player ("bh." + uuid). Matches can
 * temporarily override the prefix (CTF team colours).
 */
public final class TitleService {
	private TitleService() {}

	private static final Map<UUID, Component> OVERRIDES = new HashMap<>();

	public static String teamName(UUID id) {
		return "bh." + id.toString().replace("-", "").substring(0, 14);
	}

	public static TitleDef selected(PlayerProfile p) {
		if (p.title == null || p.title.isEmpty()) {
			return null;
		}
		TitleDef t = ConfigManager.titles().byId(p.title);
		if (t == null || !TitleRules.unlocked(t, p, Ranks.startElo())) {
			return null;
		}
		return t;
	}

	/** The title text in its colours, or null. */
	public static Component display(PlayerProfile p) {
		TitleDef t = selected(p);
		return t == null ? null : Lang.colored(t.display);
	}

	public static void setOverride(ServerPlayer p, Component prefix) {
		OVERRIDES.put(p.getUUID(), prefix);
		apply(p);
	}

	public static void clearOverride(ServerPlayer p) {
		if (OVERRIDES.remove(p.getUUID()) != null) {
			apply(p);
		}
	}

	public static void apply(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		ServerScoreboard board = server.getScoreboard();
		String name = p.getScoreboardName();
		String teamName = teamName(p.getUUID());
		Component override = OVERRIDES.get(p.getUUID());
		PlayerProfile prof = PlayerStore.get(p);
		// Titles and name colours belong to BattleHub: in the normal world the name is left alone
		boolean inBattleHub = HubWorld.isBattleHub(p.level());
		Component title = inBattleHub ? display(prof) : null;
		// Team colours (CTF) win over the bought name colour
		TeamColor color = override == null && inBattleHub ? Cosmetics.nameColor(prof) : null;
		String stars = inBattleHub ? net.battlehub.core.Daily.prestigeStars(prof.prestige) : "";
		net.battlehub.social.Clans.Clan clan = inBattleHub ? net.battlehub.social.Clans.of(p.getUUID()) : null;
		boolean wanted = override != null || ((title != null || color != null || !stars.isEmpty() || clan != null)
				&& ConfigManager.config().titles.useScoreboardTeams);
		PlayerTeam team = board.getPlayerTeam(teamName);
		if (!wanted) {
			if (team != null) {
				board.removePlayerTeam(team);
			}
			return;
		}
		PlayerTeam current = board.getPlayersTeam(name);
		if (current != null && !current.getName().startsWith("bh.")) {
			// Another plugin or a datapack already put this player in a team; leave it alone.
			return;
		}
		if (team == null) {
			team = board.addPlayerTeam(teamName);
		}
		MutableComponent prefix = Component.empty();
		if (!stars.isEmpty()) {
			prefix.append(Component.literal(stars + " ").withStyle(s -> s.withColor(0xFFAA00)));
		}
		if (clan != null) {
			prefix.append(Component.literal("[" + clan.tag + "] ").withStyle(s -> s.withColor(0x55FFFF)));
		}
		if (override != null) {
			prefix.append(override);
		} else if (title != null) {
			prefix.append(Component.literal("[").withStyle(s -> s.withColor(0x555555)))
					.append(title)
					.append(Component.literal("] ").withStyle(s -> s.withColor(0x555555)));
		}
		team.setPlayerPrefix(prefix);
		team.setColor(Optional.ofNullable(color));
		if (current == null || !current.getName().equals(teamName)) {
			board.addPlayerToTeam(name, team);
		}
	}

	public static void remove(ServerPlayer p) {
		OVERRIDES.remove(p.getUUID());
		ServerScoreboard board = p.level().getServer().getScoreboard();
		PlayerTeam team = board.getPlayerTeam(teamName(p.getUUID()));
		if (team != null) {
			board.removePlayerTeam(team);
		}
	}
}
