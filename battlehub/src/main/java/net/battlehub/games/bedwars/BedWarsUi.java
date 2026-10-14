package net.battlehub.games.bedwars;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.core.bedwars.EventSchedule;
import net.battlehub.core.bedwars.Resource;
import net.battlehub.core.bedwars.TeamState;
import net.battlehub.lang.Lang;
import net.battlehub.ui.MatchBossBar;
import net.battlehub.ui.Sidebar;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/** What Bed Wars players and spectators see: the sidebar (teams, beds, own score) and the boss bar (next event). */
public final class BedWarsUi {
	private final BedWarsMatch match;

	BedWarsUi(BedWarsMatch match) {
		this.match = match;
	}

	public static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> String.valueOf(n);
		};
	}

	static String clock(int seconds) {
		int s = Math.max(0, seconds);
		return String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}

	Component resourceName(ServerPlayer p, Resource r) {
		return Lang.tr(p, "bedwars.resource." + r.id());
	}

	Component eventName(ServerPlayer p, EventSchedule.Event e) {
		return switch (e.type()) {
			case "diamond", "emerald" -> Lang.tr(p, "bedwars.event_name." + e.type(), roman(e.value()));
			default -> Lang.tr(p, "bedwars.event_name." + e.type());
		};
	}

	void show(ServerPlayer p, Sidebar sidebar, MatchBossBar bar) {
		int now = match.seconds();
		EventSchedule.Event next = match.schedule().upcoming();
		List<Component> lines = new ArrayList<>();
		lines.add(Lang.tr(p, "bedwars.sb.time", clock(now)));
		lines.add(Component.literal(" "));
		if (match.stage() == BedWarsMatch.Stage.PODIUM) {
			lines.add(Lang.tr(p, "bedwars.sb.over"));
		} else if (next != null) {
			lines.add(Lang.tr(p, "bedwars.sb.next", eventName(p, next), clock(next.atSecond() - now)));
		} else if (match.limitSeconds() > 0) {
			lines.add(Lang.tr(p, "bedwars.sb.next", Lang.tr(p, "bedwars.event_name.end"), clock(match.limitSeconds() - now)));
		}
		lines.add(Component.literal("  "));
		int mine = match.teamOf(p.getUUID());
		for (TeamState t : match.teams()) {
			Component status;
			if (t.isOut()) {
				status = Lang.tr(p, "bedwars.sb.out");
			} else if (t.bedAlive()) {
				status = Lang.tr(p, "bedwars.sb.bed");
			} else {
				status = Lang.tr(p, "bedwars.sb.players", t.playing().size());
			}
			lines.add(Lang.tr(p, "bedwars.sb.team", match.teamName(p, t.index), status,
					t.index == mine ? Lang.tr(p, "bedwars.sb.you") : Component.empty()));
		}
		if (mine >= 0) {
			lines.add(Component.literal("   "));
			lines.add(Lang.tr(p, "bedwars.sb.kills", match.kills(p.getUUID())));
			lines.add(Lang.tr(p, "bedwars.sb.finals", match.finalKills(p.getUUID())));
			lines.add(Lang.tr(p, "bedwars.sb.beds", match.bedsBroken(p.getUUID())));
		}
		sidebar.show(p, Lang.tr(p, "bedwars.sb.title"), lines);
		bar.show(p);
		updateBar(p, bar, now, next);
	}

	private void updateBar(ServerPlayer p, MatchBossBar bar, int now, EventSchedule.Event next) {
		if (match.stage() == BedWarsMatch.Stage.PODIUM) {
			bar.update(Lang.tr(p, "bedwars.bar.over"), 1f, BossEvent.BossBarColor.YELLOW);
			return;
		}
		boolean sudden = match.stage() == BedWarsMatch.Stage.SUDDEN_DEATH;
		BossEvent.BossBarColor color = sudden ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.WHITE;
		if (next != null) {
			int from = 0;
			for (EventSchedule.Event e : match.schedule().all()) {
				if (e.atSecond() <= now && e != next) from = Math.max(from, e.atSecond());
			}
			float span = Math.max(1, next.atSecond() - from);
			bar.update(Lang.tr(p, "bedwars.bar.next", eventName(p, next), clock(next.atSecond() - now)), (next.atSecond() - now) / span, color);
		} else if (match.limitSeconds() > 0) {
			bar.update(Lang.tr(p, "bedwars.bar.end", clock(match.limitSeconds() - now)),
					(match.limitSeconds() - now) / (float) Math.max(1, match.limitSeconds()), color);
		} else {
			bar.update(Lang.tr(p, "bedwars.bar.sudden"), 1f, color);
		}
	}
}
