package com.extremedimensions.pact;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.WorldFx;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Pacts, like Heat in Hades: curses taken on willingly. A pact is sworn at home and stays sworn; on every expedition
 * its stacks act on top of the curses carried (never past a curse's maximum). Its heat is the sum of the sworn stacks
 * (an Extreme stack counts twice). A safe return with a pact brings atonement, Tether Fragments and, from enough heat,
 * a chance at a relic; a death or a Collapse brings nothing (and the curse it costs, as always).
 *
 * <p>Only curses that act beyond the wound can be sworn (not the Overworld set). Pacts open after the Starved One
 * falls ({@code pact_unlock}).
 */
public final class PactService {
	private PactService() {}

	public static boolean unlocked() {
		return switch (Config.PACT_UNLOCK) {
			case 0 -> true;
			case 1 -> DataStore.sealBroken();
			default -> !DataStore.ending().isEmpty();
		};
	}

	/** Curses that act on expeditions can be sworn; the Overworld set cannot. */
	public static boolean allowed(Penalty p) {
		return p.group != Penalty.Group.OVERWORLD && p.max > 0;
	}

	public static int heat(Map<String, Integer> pact) {
		int heat = 0;
		for (Map.Entry<String, Integer> e : pact.entrySet()) {
			Penalty p = Penalty.byId(e.getKey());
			if (p != null && e.getValue() != null && e.getValue() > 0) {
				heat += e.getValue() * (p.extreme ? 2 : 1);
			}
		}
		return heat;
	}

	public static int heat(PlayerData d) {
		return d.pact == null ? 0 : heat(d.pact);
	}

	/**
	 * Swears a new pact (an empty one renounces it). Stacks are cut to what each curse still has room for; returns
	 * null when taken, else the reason key it was refused for.
	 */
	public static String swear(ServerPlayer p, Map<String, Integer> wanted) {
		PlayerData d = DataStore.get(p.getUUID());
		if (!unlocked()) {
			return "gui.extremedimensions.pact.locked";
		}
		if (d.inRun || SeamService.inSeam(p)) {
			return "gui.extremedimensions.pact.away";
		}
		Map<String, Integer> pact = new HashMap<>();
		for (Map.Entry<String, Integer> e : wanted.entrySet()) {
			Penalty pen = Penalty.byId(e.getKey());
			int n = e.getValue() == null ? 0 : e.getValue();
			if (pen == null || !allowed(pen) || n <= 0) {
				continue;
			}
			int room = Math.max(0, pen.max - d.owned(pen));
			if (Math.min(n, room) > 0) {
				pact.put(pen.id, Math.min(n, room));
			}
		}
		if (heat(pact) > Config.PACT_MAX_HEAT) {
			return "gui.extremedimensions.pact.too_hot";
		}
		d.pact = pact;
		DataStore.markDirty();
		Sync.curses(p, d, false);
		if (pact.isEmpty()) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.pact.renounced"), true);
			WorldFx.soundSelf(p, "minecraft:block.chain.break", 0.8f, 0.7f);
		} else {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.pact.sworn", heat(pact)), true);
			WorldFx.soundSelf(p, "minecraft:block.respawn_anchor.charge", 0.8f, 0.6f);
			WorldFx.particles(p, "minecraft:soul_fire_flame", 1.0, 0.5, 0.02, 20);
		}
		return null;
	}

	/** An expedition begins: a sworn pact takes hold. */
	public static void begin(ServerPlayer p, PlayerData d) {
		int heat = heat(d);
		if (heat > 0) {
			CurseEffects.applyEffects(p, d);
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.pact.holds", heat), true);
		}
	}

	/** A safe return with a pact: what it was sworn for. */
	public static void kept(ServerPlayer p, PlayerData d) {
		int heat = heat(d);
		if (heat <= 0) {
			return;
		}
		CurseEffects.applyEffects(p, d);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		// Only time spent active beyond the wound counts: stepping through and back, or standing idle, earns nothing
		double share = share(d);
		int points = (int) Math.round(heat * Math.max(0, Config.PACT_ATONE_PER_HEAT) * share);
		PenaltyService.atone(p, d, false, points);
		double f = heat / Math.max(0.5, Config.PACT_HEAT_PER_FRAGMENT) * share;
		int fragments = (int) f + (rnd.nextDouble() < f - (int) f ? 1 : 0);
		if (fragments > 0) {
			WorldFx.give(p, new ItemStack(ModItems.TETHER_FRAGMENT, fragments));
		}
		double chance = heat >= Config.PACT_RELIC_MIN_HEAT ? Math.min(Config.PACT_RELIC_MAX_CHANCE, heat * Config.PACT_RELIC_CHANCE_PER_HEAT) * share : 0;
		boolean relic = rnd.nextDouble() < chance;
		if (relic) {
			WorldFx.give(p, RelicService.random());
		}
		if (share < 1) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.pact.short", (int) Math.floor(share * 100)));
		}
		p.sendSystemMessage(Component.translatable(relic ? "gui.extremedimensions.pact.kept_relic" : "gui.extremedimensions.pact.kept",
				heat, points, fragments));
		WorldFx.soundSelf(p, "minecraft:ui.toast.challenge_complete", 0.6f, 0.8f);
		DataStore.markDirty();
	}

	/** The part of a kept pact's reward the expedition earned: its active seconds against {@code pact_full_seconds}. */
	public static double share(PlayerData d) {
		return Config.PACT_FULL_SECONDS <= 0 ? 1 : Math.max(0, Math.min(1, d.runActive / (double) Config.PACT_FULL_SECONDS));
	}

	/** A death or a Collapse on an expedition under a pact: its reward is gone (the pact itself stays sworn). */
	public static void broken(ServerPlayer p, PlayerData d) {
		if (heat(d) > 0) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.pact.broken"));
		}
	}

	/** "unlocked;away;id:n,id:n" for the Curses and Pact screens. */
	public static String data(ServerPlayer p, PlayerData d) {
		StringBuilder out = new StringBuilder();
		out.append(unlocked() ? 1 : 0).append(';').append(d.inRun || SeamService.inSeam(p) ? 1 : 0).append(';');
		boolean first = true;
		for (Map.Entry<String, Integer> e : d.pact.entrySet()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			out.append(e.getKey()).append(':').append(e.getValue());
		}
		return out.toString();
	}
}
