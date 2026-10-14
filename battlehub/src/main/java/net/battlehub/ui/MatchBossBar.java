package net.battlehub.ui;

import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * A boss bar shown only to the players of one match (timers, the current stage). Changes are only sent when
 * something actually changed.
 */
public final class MatchBossBar {
	private final ServerBossEvent bar;
	private String lastText = "";
	private float lastProgress = -1;

	public MatchBossBar(BossEvent.BossBarColor color) {
		this.bar = new ServerBossEvent(UUID.randomUUID(), Component.empty(), color, BossEvent.BossBarOverlay.PROGRESS);
	}

	public void show(ServerPlayer p) {
		if (!bar.getPlayers().contains(p)) {
			bar.addPlayer(p);
		}
	}

	public void hide(ServerPlayer p) {
		bar.removePlayer(p);
	}

	public void update(Component text, float progress, BossEvent.BossBarColor color) {
		String s = text.getString();
		if (!s.equals(lastText)) {
			bar.setName(text);
			lastText = s;
		}
		float pr = Math.max(0f, Math.min(1f, progress));
		if (Math.abs(pr - lastProgress) > 0.001f) {
			bar.setProgress(pr);
			lastProgress = pr;
		}
		if (bar.getColor() != color) {
			bar.setColor(color);
		}
	}

	public void clear() {
		bar.removeAllPlayers();
	}
}
