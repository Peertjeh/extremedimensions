package net.battlehub.fair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.match.Match;
import net.battlehub.util.Msg;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * In every-one-for-themselves games (Free for All, Last Man Standing): two players who stay close together for a
 * long time, fight others but never each other, are warned and made to glow for a while.
 */
public final class Teaming {
	private Teaming() {}

	private static final Map<Integer, Map<String, Integer>> TOGETHER = new HashMap<>();
	private static final Map<Integer, Map<String, Integer>> LAST_HIT = new HashMap<>();
	private static final Map<Integer, Map<UUID, Integer>> FOUGHT = new HashMap<>();

	public static boolean watches(Match m) {
		return ConfigManager.config().fairness.antiTeaming && (m.mode == GameMode.FFA || m.mode == GameMode.LMS);
	}

	private static String pair(UUID a, UUID b) {
		return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
	}

	/** {@code a} hit {@code b} at {@code tick} (seconds of the match). */
	public static void hit(Match m, UUID a, UUID b, int second) {
		if (!watches(m)) {
			return;
		}
		LAST_HIT.computeIfAbsent(m.id, k -> new HashMap<>()).put(pair(a, b), second);
		FOUGHT.computeIfAbsent(m.id, k -> new HashMap<>()).put(a, second);
	}

	/** Once a second while the match runs. */
	public static void tick(Match m, int second) {
		if (!watches(m)) {
			return;
		}
		int limit = Math.max(10, ConfigManager.config().fairness.teamingSeconds);
		Map<String, Integer> together = TOGETHER.computeIfAbsent(m.id, k -> new HashMap<>());
		Map<String, Integer> hits = LAST_HIT.getOrDefault(m.id, Map.of());
		Map<UUID, Integer> fought = FOUGHT.getOrDefault(m.id, Map.of());
		List<ServerPlayer> in = new ArrayList<>();
		for (UUID id : m.alive()) {
			ServerPlayer p = m.player(id);
			if (p != null && m.inPlay(id)) in.add(p);
		}
		if (in.size() < 3) {
			return;
		}
		for (int i = 0; i < in.size(); i++) {
			for (int j = i + 1; j < in.size(); j++) {
				ServerPlayer a = in.get(i);
				ServerPlayer b = in.get(j);
				String key = pair(a.getUUID(), b.getUUID());
				boolean close = a.distanceTo(b) < 6;
				boolean peaceful = second - hits.getOrDefault(key, -1000) > 30;
				boolean fighting = second - fought.getOrDefault(a.getUUID(), -1000) < 30 || second - fought.getOrDefault(b.getUUID(), -1000) < 30;
				int t = together.getOrDefault(key, 0);
				t = close && peaceful && fighting ? t + 1 : Math.min(t, 0) + (t < 0 ? 1 : 0);
				if (t >= limit) {
					for (ServerPlayer p : List.of(a, b)) {
						Msg.send(p, "fair.teaming");
						p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false, false));
					}
					t = -limit;
				}
				together.put(key, t);
			}
		}
	}

	public static void forget(int matchId) {
		TOGETHER.remove(matchId);
		LAST_HIT.remove(matchId);
		FOUGHT.remove(matchId);
	}
}
