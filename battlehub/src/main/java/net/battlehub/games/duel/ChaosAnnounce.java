package net.battlehub.games.duel;

import java.util.function.Consumer;

import net.battlehub.core.chaos.ChaosEvent;
import net.battlehub.core.chaos.ChaosIntensity;
import net.battlehub.lang.Lang;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/** How a Chaos event is shown: a title, and a line in chat; the second time round and later with how much worse. */
public final class ChaosAnnounce {
	private ChaosAnnounce() {}

	/**
	 * @param everyone everyone in the match and watching it
	 * @param n        how many events the match has had, this one included
	 * @param planned  how many it planned
	 */
	public static void event(Consumer<Consumer<ServerPlayer>> everyone, ChaosEvent e, int level, int n, int planned) {
		int factor = ChaosIntensity.factor(level);
		int seconds = ChaosIntensity.seconds(e.seconds(), level);
		everyone.accept(o -> {
			Component name = Lang.tr(o, e.langKey());
			Component sub = Lang.tr(o, e.langKey() + ".sub", seconds);
			Component title = level == 0 ? name : Component.empty().append(name).append(Lang.colored(" &c&lx" + factor));
			Msg.title(o, title, sub, 5, 35, 10);
			if (level == 0) {
				Msg.send(o, "bridge_chaos.event_line", name, sub, Math.max(1, n), Math.max(n, planned));
			} else {
				Msg.send(o, "bridge_chaos.event_line_worse", name, sub, n, factor);
			}
			Msg.sound(o, SoundEvents.FIRECHARGE_USE, 0.7f, level == 0 ? 1.4f : 0.9f);
		});
	}
}
