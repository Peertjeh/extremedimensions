package net.battlehub.games.prophunt;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.battlehub.lang.Lang;
import net.battlehub.ui.MatchBossBar;
import net.battlehub.ui.Sidebar;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/** Sidebar (round, role, who is left, points), boss bar (phase timer) and the props' action bar. */
public final class PropHuntUi {
	private final PropHuntMatch match;

	PropHuntUi(PropHuntMatch match) {
		this.match = match;
	}

	static String clock(int ticks) {
		int s = Math.max(0, (ticks + 19) / 20);
		return String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}

	private String stageKey() {
		return "prophunt.stage." + match.stage().name().toLowerCase(java.util.Locale.ROOT);
	}

	void show(ServerPlayer p, Sidebar sidebar, MatchBossBar bar) {
		UUID id = p.getUUID();
		List<Component> lines = new ArrayList<>();
		lines.add(Lang.tr(p, "prophunt.sb.round", Math.max(1, match.round()), match.rounds()));
		int left = match.stageTicksLeft();
		lines.add(Lang.tr(p, "prophunt.sb.stage", Lang.tr(p, stageKey()), left >= 0 ? clock(left) : ""));
		lines.add(Component.literal(" "));
		if (match.participants().contains(id)) {
			lines.add(Lang.tr(p, "prophunt.sb.role", Lang.tr(p, "prophunt.role." + match.role(id).name().toLowerCase(java.util.Locale.ROOT))));
		}
		lines.add(Lang.tr(p, "prophunt.sb.props", match.props().size()));
		lines.add(Lang.tr(p, "prophunt.sb.hunters", match.hunters().size()));
		lines.add(Component.literal("  "));
		if (match.participants().contains(id)) {
			lines.add(Lang.tr(p, "prophunt.sb.points", match.points(id)));
		}
		List<UUID> top = match.scoreboard().ranking(match.present());
		for (int i = 0; i < Math.min(3, top.size()); i++) {
			lines.add(Lang.tr(p, "prophunt.sb.top", i + 1, match.name(top.get(i)), match.points(top.get(i))));
		}
		sidebar.show(p, Lang.tr(p, "prophunt.sb.title"), lines);
		bar.show(p);
		BossEvent.BossBarColor color = switch (match.stage()) {
			case HIDING -> BossEvent.BossBarColor.YELLOW;
			case HUNTING -> BossEvent.BossBarColor.RED;
			default -> BossEvent.BossBarColor.GREEN;
		};
		int total = switch (match.stage()) {
			case HIDING -> match.settings().hideSeconds * 20;
			case HUNTING -> match.settings().huntSeconds * 20;
			default -> 1;
		};
		bar.update(Lang.tr(p, "prophunt.bar", Lang.tr(p, stageKey()), left >= 0 ? clock(left) : "-"),
				left >= 0 ? left / (float) Math.max(1, total) : 1f, color);
	}

	/** For props: solid or not, and when taunt and decoy are ready. */
	void propBar(ServerPlayer p) {
		UUID id = p.getUUID();
		Disguise d = match.disguise(id);
		long now = match.now();
		int taunt = (int) ((match.cooldowns().ticksLeft("taunt:" + id, now) + 19) / 20);
		int decoy = (int) ((match.cooldowns().ticksLeft("decoy:" + id, now) + 19) / 20);
		Component state;
		if (d == null) {
			state = Lang.tr(p, "prophunt.bar.none");
		} else if (d.isSolid()) {
			state = Lang.tr(p, "prophunt.bar.solid");
		} else {
			int need = match.settings().solidifySeconds * 20 - d.stillTicks();
			state = Lang.tr(p, "prophunt.bar.moving", Math.max(0, (need + 19) / 20));
		}
		Msg.actionBar(p, "prophunt.bar.prop", state,
				taunt <= 0 ? Lang.tr(p, "prophunt.bar.ready") : Component.literal(taunt + "s"),
				decoy <= 0 ? Lang.tr(p, "prophunt.bar.ready") : Component.literal(decoy + "s"),
				Lang.tr(p, d != null && d.locked() ? "prophunt.bar.locked" : "prophunt.bar.free"));
	}
}
