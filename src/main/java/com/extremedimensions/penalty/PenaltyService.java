package com.extremedimensions.penalty;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.network.Fx;
import com.extremedimensions.network.Sync;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

/** Owing, choosing, applying, wearing off and mending penalties. */
public final class PenaltyService {
	private PenaltyService() {}

	private static final String BEYOND = "beyond";
	private static final String OVERWORLD = "overworld";

	/** The player owes a penalty from the beyond or the Overworld set. */
	public static void owe(PlayerData d, boolean overworld) {
		d.pendingPenalties++;
		d.owed.add(overworld ? OVERWORLD : BEYOND);
		if (d.pendingOffer.isEmpty()) {
			d.pendingOffer = Penalty.rollOffer(OVERWORLD.equals(d.owed.get(0)));
		}
	}

	/** The player picked a card on the penalty screen. */
	public static void choose(ServerPlayer p, String id) {
		PlayerData d = DataStore.get(p.getUUID());
		Penalty pen = Penalty.byId(id);
		if (pen == null || !d.pendingOffer.contains(id)) {
			return;
		}
		d.pendingOffer.clear();
		d.pendingPenalties = Math.max(0, d.pendingPenalties - 1);
		if (!d.owed.isEmpty()) {
			d.owed.remove(0);
		}

		if (pen.timed) {
			d.addStack(pen);
			applyEffects(p, d);
		} else {
			switch (pen) {
				case LEAKY_TANK -> d.leakStacks++;
				case MOURNING -> {
					int secs = Config.MOURNING_SECONDS;
					d.mourningSecs = Math.max(d.mourningSecs, secs);
					effect(p, "weakness", secs, 1);
					effect(p, "hunger", secs, 1);
					effect(p, "mining_fatigue", secs, 0);
				}
				case TOLL -> d.xpDebt += Config.TOLL_LEVELS;
				case TITHE -> d.xpDebt += Config.TITHE_LEVELS;
				default -> { }
			}
		}

		WorldFx.fx(p, Fx.PENALTY, 0);
		WorldFx.particles(p, "minecraft:soul", 1.0, 0.5, 0.05, 25);
		WorldFx.particles(p, "minecraft:sculk_soul", 1.2, 0.6, 0.03, 12);
		WorldFx.soundSelf(p, "extremedimensions:penalty", 1.0f, 1.0f);
		Journal.unlock(p, d, JournalEntry.SCARS);
		Advancements.grant(p, Advancements.MARKED);

		// More owed (several deaths in a row)? The next screen follows.
		if (d.pendingPenalties > 0) {
			if (d.owed.isEmpty()) {
				d.owed.add(BEYOND);
			}
			d.pendingOffer = Penalty.rollOffer(OVERWORLD.equals(d.owed.get(0)));
			d.offerDelay = 40;
		}
		Sync.state(p, d);
		DataStore.save();
	}

	/** A Tether Fragment offered to an anchor removes the longest-running stack (or a Leaky Tank, or XP debt). */
	public static InteractionResult mend(ServerPlayer p, PlayerData d, BlockPos pos, ItemStack stack) {
		String bestId = null;
		int bestIdx = -1;
		double bestSecs = -1;
		for (Map.Entry<String, List<Double>> e : d.penaltyTime.entrySet()) {
			for (int i = 0; i < e.getValue().size(); i++) {
				if (e.getValue().get(i) > bestSecs) {
					bestSecs = e.getValue().get(i);
					bestId = e.getKey();
					bestIdx = i;
				}
			}
		}
		if (bestId != null) {
			List<Double> l = d.penaltyTime.get(bestId);
			l.remove(bestIdx);
			if (l.isEmpty()) {
				d.penaltyTime.remove(bestId);
			}
			applyEffects(p, d);
		} else if (d.leakStacks > 0) {
			d.leakStacks--;
		} else if (d.xpDebt > 0) {
			d.xpDebt = 0;
		} else {
			WorldFx.soundSelf(p, "minecraft:block.note_block.bass", 0.8f, 0.5f);
			return InteractionResult.SUCCESS;
		}
		stack.shrink(1);
		double ax = pos.getX() + 0.5;
		double ay = pos.getY() + 1.0;
		double az = pos.getZ() + 0.5;
		WorldFx.particleAt("minecraft:soul_fire_flame", ax, ay, az, 0.3, 0.5, 0.3, 0.03, 30);
		WorldFx.particleAt("minecraft:sculk_soul", ax, ay + 0.5, az, 0.5, 0.5, 0.5, 0.05, 20);
		WorldFx.particles(p, "minecraft:soul", 1.0, 0.4, 0.05, 20);
		WorldFx.sound(p, "minecraft:block.sculk_catalyst.bloom", 1.0f, 0.8f);
		WorldFx.sound(p, "minecraft:block.beacon.power_select", 0.7f, 1.4f);
		Journal.unlock(p, d, JournalEntry.MENDING);
		Advancements.grant(p, Advancements.MENDED);
		Sync.state(p, d);
		DataStore.save();
		return InteractionResult.SUCCESS;
	}

	// ------------------------------------------------------------------ per tick

	/**
	 * Wears penalties off while the player is actively playing where they count. Also collects XP debt and runs
	 * the Mourning display timer. Returns true if the HUD must sync now.
	 */
	public static boolean tick(ServerPlayer p, PlayerData d, boolean onExpedition, boolean inOverworld) {
		boolean changed = false;
		if (d.tickCounter % 20 == 0) {
			if (d.mourningSecs > 0) {
				d.mourningSecs = Math.max(0, d.mourningSecs - 1);
				changed |= d.mourningSecs == 0;
			}
			// XP debt: every level the player earns goes to the Tether until the debt is paid
			if (d.xpDebt > 0 && p.experienceLevel > 0) {
				int take = Math.min(d.xpDebt, p.experienceLevel);
				p.giveExperienceLevels(-take);
				d.xpDebt -= take;
				WorldFx.particles(p, "minecraft:sculk_soul", 1.0, 0.4, 0.03, 8);
				WorldFx.soundSelf(p, "minecraft:particle.soul_escape", 1.0f, 0.8f);
				changed = true;
			}
		}
		trackActivity(p, d);
		return countDown(p, d, onExpedition, inOverworld) || changed;
	}

	/** Remembers when the player last looked around and last walked a little. */
	private static void trackActivity(ServerPlayer p, PlayerData d) {
		d.ticksSinceLook++;
		d.ticksSinceMove++;
		float yaw = p.getYRot();
		float pitch = p.getXRot();
		if (Math.abs(yaw - d.lastYaw) > 2.0f || Math.abs(pitch - d.lastPitch) > 2.0f) {
			d.ticksSinceLook = 0;
			d.lastYaw = yaw;
			d.lastPitch = pitch;
		}
		double dx = p.getX() - d.anchorX;
		double dz = p.getZ() - d.anchorZ;
		if (dx * dx + dz * dz >= Config.ACTIVE_MOVE * Config.ACTIVE_MOVE) {
			d.ticksSinceMove = 0;
			d.anchorX = p.getX();
			d.anchorZ = p.getZ();
		}
	}

	/** Looking around alone (mouse macro) or moving alone (water stream) is not enough: it takes both. */
	private static boolean countDown(ServerPlayer p, PlayerData d, boolean onExpedition, boolean inOverworld) {
		int before = d.penaltyClock;
		boolean any = false;
		for (String id : d.penaltyTime.keySet()) {
			Penalty pen = Penalty.byId(id);
			boolean ow = pen != null && pen.overworld;
			if ((ow && inOverworld) || (!ow && onExpedition)) {
				any = true;
				break;
			}
		}
		if (!any) {
			d.penaltyClock = 0;
			return before != d.penaltyClock;
		}
		boolean active = d.ticksSinceLook < Config.IDLE_TICKS && d.ticksSinceMove < Config.IDLE_TICKS;
		d.penaltyClock = active ? 1 : 2;
		if (!active) {
			return before != d.penaltyClock;
		}

		boolean worn = false;
		Iterator<Map.Entry<String, List<Double>>> it = d.penaltyTime.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, List<Double>> e = it.next();
			Penalty kind = Penalty.byId(e.getKey());
			boolean owKind = kind != null && kind.overworld;
			if (owKind ? !inOverworld : !onExpedition) {
				continue;
			}
			List<Double> left = new ArrayList<>();
			for (double secs : e.getValue()) {
				double rest = secs - 1.0 / 20.0;
				if (rest > 0) {
					left.add(rest);
				} else {
					worn = true;
				}
			}
			e.setValue(left);
			if (left.isEmpty()) {
				it.remove();
			}
		}
		if (!worn) {
			return before != d.penaltyClock;
		}
		applyEffects(p, d);
		WorldFx.sound(p, "minecraft:block.amethyst_block.chime", 1.0f, 1.2f);
		WorldFx.particles(p, "minecraft:happy_villager", 1.0, 0.5, 0.1, 15);
		return true;
	}

	// ------------------------------------------------------------------ effects

	/** Sets every attribute penalty to match its stacks and refreshes the effects. */
	public static void applyEffects(ServerPlayer p, PlayerData d) {
		boolean ow = Dims.OVERWORLD.equals(Dims.of(p));
		double scarHp = Math.min(16.0, Config.SCAR_HEALTH * d.stacks(Penalty.SCAR));
		modifier(p, "minecraft:max_health", "scar", -scarHp, "add_value");
		double hollowHp = ow ? Math.min(16.0 - scarHp, Config.HOLLOW_HEART * d.stacks(Penalty.HOLLOW_HEART)) : 0;
		modifier(p, "minecraft:max_health", "hollow_heart", -hollowHp, "add_value");
		modifier(p, "minecraft:movement_speed", "leaden_steps", ow ? -compound(Config.LEADEN_STEPS, d.stacks(Penalty.LEADEN_STEPS)) : 0, "add_multiplied_total");
		modifier(p, "minecraft:block_break_speed", "dull_pick", ow ? -compound(Config.DULL_PICK, d.stacks(Penalty.DULL_PICK)) : 0, "add_multiplied_total");
		modifier(p, "minecraft:armor", "cracked_armor", -Config.CRACKED_ARMOR * d.stacks(Penalty.CRACKED_ARMOR), "add_value");
		modifier(p, "minecraft:movement_speed", "heavy_legs", -compound(Config.HEAVY_LEGS, d.stacks(Penalty.HEAVY_LEGS)), "add_multiplied_total");
		modifier(p, "minecraft:block_break_speed", "clumsy_hands", -compound(Config.CLUMSY_HANDS, d.stacks(Penalty.CLUMSY_HANDS)), "add_multiplied_total");
		refreshEffects(p, d);
	}

	/** Total reduction of {@code stacks} multiplicative reductions of {@code each}. */
	private static double compound(double each, int stacks) {
		return 1 - Math.pow(1 - each, stacks);
	}

	private static void modifier(ServerPlayer p, String attribute, String name, double value, String operation) {
		String u = p.getStringUUID();
		WorldFx.run("attribute " + u + " " + attribute + " modifier remove extremedimensions:" + name);
		if (value != 0) {
			WorldFx.run(String.format(Locale.ROOT, "attribute %s %s modifier add extremedimensions:%s %.4f %s", u, attribute, name, value, operation));
		}
	}

	/** A vanilla effect without icon or particles: penalties are shown as Tether Marks instead. */
	public static void effect(ServerPlayer p, String id, int secs, int amp) {
		BuiltInRegistries.MOB_EFFECT.get(Identifier.fromNamespaceAndPath("minecraft", id)).ifPresent(h ->
				p.addEffect(new MobEffectInstance(h, secs * 20, Math.max(0, amp), true, false, false)));
	}

	/** Lasting effects are re-applied briefly every few seconds, so they stop by themselves when a penalty ends. */
	public static void refreshEffects(ServerPlayer p, PlayerData d) {
		String dim = Dims.of(p);
		int frailty = d.stacks(Penalty.FRAILTY);
		if (frailty > 0) {
			effect(p, "weakness", 8, frailty - 1);
		}
		int famine = d.stacks(Penalty.FAMINE);
		if (famine > 0) {
			effect(p, "hunger", 8, famine - 1);
		}
		if (Dims.OVERWORLD.equals(dim)) {
			int gnawing = d.stacks(Penalty.GNAWING);
			if (gnawing > 0) {
				effect(p, "hunger", 8, gnawing - 1);
			}
			int luck = d.stacks(Penalty.ILL_LUCK);
			if (luck > 0) {
				effect(p, "unluck", 8, luck - 1);
			}
			if (d.stacks(Penalty.DEEP_DREAD) > 0 && p.getY() < 62) {
				effect(p, "darkness", 8, 0);
			}
		}
		int shadowed = d.stacks(Penalty.SHADOWED);
		if (shadowed > 0 && Dims.isBeyond(dim)) {
			effect(p, "darkness", 8, 0);
			if (shadowed >= 2) {
				effect(p, "slowness", 8, shadowed - 2);
			}
		}
	}
}
