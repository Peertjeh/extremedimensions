package net.battlehub.games.duel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.NewLayouts;
import net.battlehub.core.DuelFormat;
import net.battlehub.core.GameMode;
import net.battlehub.lang.Lang;
import net.battlehub.match.DuelMatch;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/** Boxing: hits do no damage (Resistance in the kit), only knockback; the first to land 100 hits wins the round. */
public final class BoxingMatch extends DuelMatch {
	private final Map<UUID, Integer> hits = new HashMap<>();
	private final Map<UUID, Integer> combo = new HashMap<>();
	private UUID roundTo;

	public BoxingMatch(int id, boolean ranked, DuelFormat format, boolean challenge, MinecraftServer server, ServerPlayer a, ServerPlayer b) {
		super(id, GameMode.BOXING, ranked, format, challenge, server, a, b);
	}

	private int hitsToWin() {
		return (int) Math.max(1, game().setting("hitsToWin", 100));
	}

	public int hits(UUID id) {
		return hits.getOrDefault(id, 0);
	}

	@Override
	protected Blueprint design(Theme theme) {
		return NewLayouts.boxing(theme, random);
	}

	@Override
	protected boolean damageAllowed(ServerPlayer victim, DamageSource source, ServerPlayer attacker) {
		if (attacker == null || attacker == victim || source.getDirectEntity() != attacker) {
			return false;
		}
		int h = hits.merge(attacker.getUUID(), 1, Integer::sum);
		int c = combo.merge(attacker.getUUID(), 1, Integer::sum);
		combo.put(victim.getUUID(), 0);
		UUID a = participants.get(0);
		UUID b = participants.get(1);
		forEveryone(o -> Msg.actionBar(o, "boxing.hits", name(a), hits(a), hits(b), name(b)));
		if (c >= 3) {
			Msg.actionBar(attacker, "boxing.combo", c, h, hitsToWin());
		}
		if (h >= hitsToWin() && roundTo == null) {
			// Decided after the hit is done (not in the middle of it)
			roundTo = attacker.getUUID();
		}
		return true;
	}

	@Override
	protected void tickGame() {
		for (UUID id : alive) {
			ServerPlayer p = player(id);
			if (p != null && p.getHealth() < p.getMaxHealth()) {
				p.setHealth(p.getMaxHealth());
			}
		}
		if (roundTo != null) {
			UUID w = roundTo;
			roundTo = null;
			roundWon(w, other(w));
			if (phase == Phase.RUNNING) {
				checkEnd();
			}
		}
	}

	@Override
	protected void restartRound() {
		hits.clear();
		combo.clear();
		super.restartRound();
	}

	@Override
	protected void onTimeUp() {
		UUID a = participants.get(0);
		UUID b = participants.get(1);
		if (hits(a) == hits(b)) {
			finish(List.of());
			return;
		}
		UUID w = hits(a) > hits(b) ? a : b;
		roundWon(w, other(w));
		if (phase == Phase.RUNNING) {
			checkEnd();
		}
	}

	@Override
	public Component status(ServerPlayer viewer) {
		UUID a = participants.get(0);
		UUID b = participants.get(1);
		return Lang.tr(viewer, "boxing.status", name(a), hits(a), hits(b), name(b));
	}
}
