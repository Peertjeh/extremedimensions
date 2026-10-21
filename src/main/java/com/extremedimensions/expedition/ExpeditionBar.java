package com.extremedimensions.expedition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.extremedimensions.anchor.AnchorService;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.tether.TetherService;
import com.extremedimensions.server.Dims;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * The expedition timer as Minecraft shows such things: a boss bar at the top of the screen, stacked with any other
 * (an Elder's, a raid's) as the game stacks them. Its name says where the player is, the time left, the way home
 * (in the Seam, an arrow and the distance to the rift; elsewhere, the stretch of the Tether) and a drain above one;
 * it empties as the time runs out and turns red in the last minute. A Rift Trial under way gets a bar of its own
 * under it, filling towards the next medal, as a raid's does.
 */
public final class ExpeditionBar {
	private ExpeditionBar() {}

	private static final Map<UUID, ServerBossEvent> TIME = new HashMap<>(), TRIAL = new HashMap<>();
	/** The way home, in eight steps clockwise from straight ahead. */
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	/** Every few ticks: each player's bars follow their expedition (and go when it ends). */
	public static void tick(MinecraftServer s) {
		if (s.getTickCount() % 4 != 0) {
			return;
		}
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			update(p, DataStore.get(p.getUUID()));
		}
	}

	/** Brings a player's bars up to date: shown during an expedition when the setting asks for them, gone otherwise. */
	public static void update(ServerPlayer p, PlayerData d) {
		boolean seam = d.inSeam && SeamService.inSeam(p);
		boolean run = !seam && d.inRun && !d.collapsing && (Dims.NETHER.equals(d.dim) || Dims.END.equals(d.dim));
		if (Config.HUD_BOSS_BAR <= 0 || d.away || !p.isAlive() || !(seam || run)) {
			forget(p.getUUID());
			return;
		}
		double left = seam ? d.seamLeft : AnchorService.linkedCharge(p, d);
		double max = Math.max(1, seam ? d.seamMax : d.runMax);
		boolean nether = Dims.NETHER.equals(d.dim);
		BossEvent.BossBarColor colour = left <= 60 ? BossEvent.BossBarColor.RED
				: seam ? BossEvent.BossBarColor.PURPLE : nether ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.PINK;
		Component title;
		float progress;
		if (!seam && d.heldTicks > 0) {
			// The Held's Chain holds them: the bar counts down its grace instead
			int s = (d.heldTicks + 19) / 20;
			title = Component.translatable("hud.extremedimensions.held", String.format("%d:%02d", s / 60, s % 60)).withStyle(ChatFormatting.GOLD);
			colour = BossEvent.BossBarColor.WHITE;
			progress = (float) Math.max(0, Math.min(1, d.heldTicks / (20.0 * Math.max(1, Config.RELIC_HELDS_CHAIN_GRACE))));
		} else {
			title = name(p, d, seam, nether, left);
			progress = (float) Math.max(0, Math.min(1, left / max));
		}
		// (each set once: a bar told one thing and then another sends both, every time)
		ServerBossEvent time = bar(TIME, p, colour, BossEvent.BossBarOverlay.NOTCHED_10);
		time.setName(title);
		time.setColor(colour);
		time.setProgress(progress);
		String trial = seam ? com.extremedimensions.trial.TrialService.hud(d) : "";
		String[] t = trial.split(";");
		if (t.length < 6) {
			drop(TRIAL, p.getUUID());
			return;
		}
		try {
			int score = Integer.parseInt(t[2]);
			int[] need = {Integer.parseInt(t[3]), Integer.parseInt(t[4]), Integer.parseInt(t[5])};
			int medal = 0;
			for (int i = 0; i < 3; i++) {
				if (score >= Math.max(1, need[i])) {
					medal = i + 1;
				}
			}
			ServerBossEvent bar = bar(TRIAL, p, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
			MutableComponent label = Component.translatable("gui.extremedimensions.trial.kind." + t[0]).append("  ")
					.append(Component.literal(medal >= 3 ? String.valueOf(score) : score + " / " + Math.max(1, need[medal])));
			if (medal > 0) {
				label.append("  ").append(Component.translatable("gui.extremedimensions.trial.medal." + medal).withStyle(ChatFormatting.GOLD));
			}
			bar.setName(label);
			bar.setProgress(medal >= 3 ? 1.0F : (float) Math.max(0, Math.min(1, score / (double) Math.max(1, need[medal]))));
		} catch (NumberFormatException e) {
			drop(TRIAL, p.getUUID());
		}
	}

	/** The timer's name: where, how long, the way home, and the drain when it runs faster. */
	private static Component name(ServerPlayer p, PlayerData d, boolean seam, boolean nether, double left) {
		String where = seam ? "dimension.extremedimensions.seam" : nether ? "dimension.extremedimensions.nether" : "dimension.extremedimensions.end";
		int s = (int) Math.ceil(Math.max(0, left));
		MutableComponent out = Component.translatable(where).append("  ")
				.append(Component.literal(String.format("%d:%02d", s / 60, s % 60)).withStyle(left <= 30 ? ChatFormatting.RED : ChatFormatting.WHITE));
		if (seam && Config.SEAM_RIFT_COMPASS > 0) {
			int dist = SeamService.riftDistance(p, d);
			out.append("  ").append(Component.literal(dist < 2 ? "◆ " : arrow(p, d) + " ").withStyle(ChatFormatting.GOLD))
					.append(Component.translatable("hud.extremedimensions.rift_distance", dist));
		} else if (!seam && TetherService.distance(p, d) >= 0) {
			out.append("  ").append(Component.translatable("hud.extremedimensions.tether_distance", TetherService.distance(p, d)));
		}
		double drain = seam ? com.extremedimensions.seam.SeamDepth.drain(p.getY()) : d.currentDrain;
		if (drain > 1.0) {
			out.append("  ").append(Component.translatable("hud.extremedimensions.drain", String.format(java.util.Locale.ROOT, "%.1f", drain))
					.withStyle(drain >= 3 ? ChatFormatting.RED : drain >= 1.8 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
		}
		return out;
	}

	/** Which way the rift home lies from where the player looks. */
	static String arrow(ServerPlayer p, PlayerData d) {
		return arrow(p.getYRot(), d.riftX - p.getX(), d.riftZ - p.getZ());
	}

	/** The arrow for a way (dx, dz) seen by someone looking along a yaw: up is straight ahead, right is to the right. */
	public static String arrow(float yaw, double dx, double dz) {
		double world = Math.toDegrees(Math.atan2(dz, dx));
		// Minecraft's yaw: 0 looks south (+z), 90 west; the angle of the way home measured from straight ahead, clockwise
		double rel = world - (yaw + 90);
		int step = Math.floorMod((int) Math.round(rel / 45.0), 8);
		return ARROWS[step];
	}

	private static ServerBossEvent bar(Map<UUID, ServerBossEvent> bars, ServerPlayer p, BossEvent.BossBarColor colour, BossEvent.BossBarOverlay overlay) {
		ServerBossEvent bar = bars.computeIfAbsent(p.getUUID(), id -> new ServerBossEvent(UUID.randomUUID(), Component.empty(), colour, overlay));
		// after a respawn the player is a new object: the bar follows the one playing now
		for (ServerPlayer q : List.copyOf(bar.getPlayers())) {
			if (q != p) {
				bar.removePlayer(q);
			}
		}
		bar.addPlayer(p);
		return bar;
	}

	private static void drop(Map<UUID, ServerBossEvent> bars, UUID id) {
		ServerBossEvent bar = bars.remove(id);
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}

	/** A player's bars are taken down (they left, or their expedition ended). */
	public static void forget(UUID id) {
		drop(TIME, id);
		drop(TRIAL, id);
	}

	/** Whether a player has a timer bar now (for the tests). */
	public static boolean showing(UUID id) {
		return TIME.containsKey(id);
	}

	/** The timer bar's name as a player sees it now (for the tests), or "" when there is none. */
	public static String label(UUID id) {
		ServerBossEvent bar = TIME.get(id);
		return bar == null ? "" : bar.getName().getString();
	}

	public static void clear() {
		for (ServerBossEvent bar : TIME.values()) {
			bar.removeAllPlayers();
		}
		for (ServerBossEvent bar : TRIAL.values()) {
			bar.removeAllPlayers();
		}
		TIME.clear();
		TRIAL.clear();
	}
}
