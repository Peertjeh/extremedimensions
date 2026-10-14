package com.extremedimensions.expedition;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.anchor.AnchorService;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.network.Fx;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.AtonementGuard;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;
import com.extremedimensions.tether.TetherService;
import com.extremedimensions.world.WorldService;
import com.extremedimensions.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Expeditions beyond the wound: linking anchors on entry, draining charge, the Leeching, the Collapse when the
 * charge runs out, deaths, and the safe return.
 */
public final class ExpeditionService {
	private ExpeditionService() {}

	private static final Set<String> ELITES = Set.of("wither_skeleton", "enderman", "blaze", "ghast", "shulker");

	// ------------------------------------------------------------------ dimension changes

	public static void onDimensionChange(ServerPlayer p, PlayerData d, String from, String to) {
		if (Dims.isBeyond(to) && !d.collapsing) {
			start(p, d, to);
		} else if (Dims.OVERWORLD.equals(to) && Dims.isBeyond(from) && d.inRun && !d.collapsing) {
			complete(p, d);
		}
		CurseEffects.applyEffects(p, d);
	}

	private static void start(ServerPlayer p, PlayerData d, String dim) {
		String uid = p.getStringUUID();
		// Working anchors of this type around the portal the player went through
		d.linked.clear();
		if (d.hasLast) {
			double r2 = Config.LINK_RANGE * Config.LINK_RANGE;
			for (Anchor a : DataStore.anchors().values()) {
				if (dim.equals(a.type) && a.of(uid) > 0 && !a.blocked && a.distSq(d.lastX, d.lastY, d.lastZ) <= r2) {
					d.linked.add(a.key());
				}
			}
		}
		double total = AnchorService.linkedCharge(p, d);
		if (total <= 0 || d.tetherAnchor == null || !d.linked.contains(d.tetherAnchor)) {
			// No charge, or not bound to one of these anchors: the wound stays dormant
			if (total > 0) {
				Journal.unlock(p, d, JournalEntry.CORD);
			}
			d.linked.clear();
			bounce(p, d);
			return;
		}
		d.arrivalX = p.getX();
		d.arrivalZ = p.getZ();
		d.hasRopeEnd = false;
		int leaky = d.stacks(Penalty.LEAKY_TANK);
		if (leaky > 0) {
			// Leaky Tank: every expedition starts with a share of the stored charge gone
			double keep = Math.pow(Math.max(0, 1.0 - Penalty.LEAKY_TANK.frac()), leaky);
			for (Anchor a : AnchorService.linked(d)) {
				a.scale(uid, keep);
			}
			total = AnchorService.linkedCharge(p, d);
		}
		d.inRun = true;
		d.runTime = 0;
		d.runMax = total;
		d.lastWarn = Integer.MAX_VALUE;
		d.lastBeat = Integer.MAX_VALUE;
		boolean nether = Dims.NETHER.equals(dim);
		WorldFx.fx(p, nether ? Fx.START_NETHER : Fx.START_END, 0);
		Journal.unlock(p, d, nether ? JournalEntry.EMBER_DEEP : JournalEntry.PALE_VOID);
		WorldFx.sound(p, "extremedimensions:portal_beyond", 1.0f, nether ? 0.9f : 1.1f);
		if (nether) {
			WorldFx.particles(p, "minecraft:flame", 1.0, 0.6, 0.06, 60);
			WorldFx.particles(p, "minecraft:soul_fire_flame", 1.0, 1.2, 0.02, 30);
		} else {
			WorldFx.particles(p, "minecraft:reverse_portal", 1.0, 0.6, 0.2, 80);
			WorldFx.particles(p, "minecraft:end_rod", 1.0, 1.2, 0.03, 20);
		}
	}

	/** Safe return: the chain disappears entirely and the Tether comes back. */
	private static void complete(ServerPlayer p, PlayerData d) {
		TetherService.clear(p, d, true);
		d.inRun = false;
		d.linked.clear();
		d.expeditions++;
		d.longestRun = Math.max(d.longestRun, d.runTime);
		d.runTime = 0;

		// Ferryman's Fee: every crossing home is paid for in levels
		int fee = (int) Math.round(Penalty.FERRYMANS_FEE.value) * d.stacks(Penalty.FERRYMANS_FEE);
		if (fee > 0 && p.experienceLevel > 0) {
			p.giveExperienceLevels(-Math.min(fee, p.experienceLevel));
		}
		PenaltyService.atone(p, d, false, Config.ATONE_PER_RETURN);
		WorldFx.fx(p, Fx.SAFE, 0);
		WorldFx.particles(p, "minecraft:totem_of_undying", 1.0, 0.5, 0.35, 40);
		WorldFx.particles(p, "minecraft:soul", 1.0, 0.8, 0.03, 20);
		WorldFx.soundSelf(p, "extremedimensions:portal_home", 0.6f, 1.0f);
		Journal.unlock(p, d, JournalEntry.WOUND);
		Advancements.grant(p, Advancements.FIRST_EXPEDITION);
		if (d.expeditions >= 5) {
			Journal.unlock(p, d, JournalEntry.HABIT);
		}
		if (d.expeditions >= Advancements.VETERAN_EXPEDITIONS) {
			Advancements.grant(p, Advancements.VETERAN);
		}
		DataStore.markDirty();
	}

	/**
	 * A Seam-Woven Tether unravels instead of letting the player collapse: the silk comes apart thread by thread and
	 * pulls them home, whole. The Tether is spent.
	 */
	private static void unravel(ServerPlayer p, PlayerData d) {
		TetherService.clear(p, d, false);
		d.tetherTier = 0;
		WorldFx.particles(p, "extremedimensions:soul_mote", 1.0, 1.0, 0.0, 40);
		WorldFx.sound(p, "extremedimensions:rift_open", 1.0f, 1.3f);
		Journal.unlock(p, d, JournalEntry.WOVEN);
		if (d.hasLast) {
			WorldFx.tp(p, "minecraft:overworld", d.lastX, d.lastY, d.lastZ);
		} else {
			sendToSpawn(p);
		}
	}

	/** Entered without a working anchor: the portal is dormant and sends the player back. */
	private static void bounce(ServerPlayer p, PlayerData d) {
		d.inRun = false;
		d.grace = Config.BOUNCE_GRACE_TICKS;
		WorldFx.fx(p, Fx.BOUNCE, 0);
		WorldFx.particles(p, "minecraft:poof", 1.0, 0.4, 0.05, 30);
		WorldFx.sound(p, "minecraft:entity.villager.no", 1.0f, 0.7f);
		if (d.hasLast) {
			WorldFx.tp(p, "minecraft:overworld", d.lastX, d.lastY, d.lastZ);
		} else {
			sendToSpawn(p);
		}
	}

	private static void sendToSpawn(ServerPlayer p) {
		if (WorldFx.server() == null) {
			return;
		}
		BlockPos sp = WorldFx.server().overworld().getRespawnData().pos();
		WorldFx.run(String.format(Locale.ROOT, "execute in minecraft:overworld run spreadplayers %d %d 0 2 false %s",
				sp.getX(), sp.getZ(), p.getStringUUID()));
	}

	// ------------------------------------------------------------------ per tick

	/** Drains charge beyond the wound. Returns true if the HUD must sync now. */
	public static boolean tick(ServerPlayer p, PlayerData d, String dim) {
		if (Dims.OVERWORLD.equals(dim) && !d.collapsing) {
			// Last Overworld position: decides which anchors feed the next expedition
			d.lastX = p.getX();
			d.lastY = p.getY();
			d.lastZ = p.getZ();
			d.hasLast = true;
		}

		boolean changed = false;
		if (Dims.isBeyond(dim) && d.inRun && !d.collapsing) {
			d.currentDrain = d.drainMultiplier() * TetherService.distanceFactor(p, d) * WorldService.drainFactor(dim);
			double left = AnchorService.drainLinked(p, d, d.currentDrain / 20.0);
			d.runTime += 1.0 / 20.0;
			double far = TetherService.arrivalDistance(p, d);
			if (far > d.furthest) {
				d.furthest = far;
				if (far >= Advancements.LONG_TETHER_BLOCKS) {
					Advancements.grant(p, Advancements.LONG_TETHER);
				}
			}
			if (left <= 0 && d.tetherTier == 2 && d.tetherAnchor != null) {
				unravel(p, d);
				changed = true;
			} else if (left <= 0) {
				collapse(p, d);
				changed = true;
			} else {
				int secs = (int) Math.ceil(left);
				warn(p, d, secs);
				if (left < Config.LEECH_SECONDS && d.tickCounter % 10 == 0) {
					leech(p, left);
				}
				if (secs <= 10 && secs != d.lastBeat) {
					d.lastBeat = secs;
					WorldFx.sound(p, "minecraft:entity.warden.heartbeat", 1.3f, 1.0f + (10 - secs) * 0.04f);
				}
			}
		} else if (d.collapsing) {
			pull(p, d);
		}
		if (!(Dims.isBeyond(dim) && d.inRun)) {
			d.currentDrain = d.drainMultiplier();
		}
		return changed;
	}

	private static void warn(ServerPlayer p, PlayerData d, int secs) {
		for (int t : new int[] {60, 30, 10}) {
			if (secs <= t && d.lastWarn > t) {
				WorldFx.sound(p, "minecraft:block.bell.use", 0.7f, 0.6f);
				WorldFx.particles(p, "minecraft:smoke", 1.0, 0.5, 0.02, 25);
				d.lastWarn = secs;
				return;
			}
		}
	}

	/** The Leeching: below {@link Config#LEECH_SECONDS} the Starved visibly drink souls from the player. */
	private static void leech(ServerPlayer p, double left) {
		double k = 1.0 - left / Config.LEECH_SECONDS;
		int n = 2 + (int) Math.round(k * 10);
		WorldFx.particles(p, "minecraft:sculk_soul", 1.0, 0.5, 0.03, n);
		WorldFx.particles(p, "extremedimensions:soul_mote", 1.0, 0.6, 0.0, Math.max(1, n / 2));
		if (k > 0.5) {
			WorldFx.particles(p, "minecraft:soul", 0.8, 0.8, 0.02, n / 2);
		}
		if (ThreadLocalRandom.current().nextDouble() < 0.15 + k * 0.35) {
			WorldFx.soundSelf(p, "minecraft:particle.soul_escape", 0.6f + (float) k * 0.6f, 0.6f + (float) k * 0.3f);
		}
	}

	/**
	 * The charge ran out: the Tether drags the player out of the dimension. For {@link Config#COLLAPSE_PULL_TICKS}
	 * the player is held and lifted while souls stream past; then the death follows (and the client skips the
	 * death screen, see the client's collapse sequence).
	 */
	private static void collapse(ServerPlayer p, PlayerData d) {
		TetherService.clear(p, d, false);
		d.collapsing = true;
		d.collapseRetry = -Config.COLLAPSE_PULL_TICKS;
		d.collapses++;
		PenaltyService.owe(d, false);
		Journal.unlock(p, d, JournalEntry.STARVED);
		Advancements.grant(p, Advancements.COLLAPSE);
		WorldFx.fx(p, Fx.COLLAPSE, 0);
		int secs = Config.COLLAPSE_PULL_TICKS / 20 + 1;
		CurseEffects.effect(p, "slowness", secs, 6);
		CurseEffects.effect(p, "levitation", secs, 0);
		CurseEffects.effect(p, "resistance", secs, 4);
		WorldFx.sound(p, "extremedimensions:collapse", 1.2f, 1.0f);
		WorldFx.particles(p, "minecraft:sculk_soul", 1.0, 0.6, 0.05, 30);
		DataStore.markDirty();
	}

	/** Runs every tick while collapsing: the pull, then the death (retried every second as a safety net). */
	private static void pull(ServerPlayer p, PlayerData d) {
		int t = ++d.collapseRetry;
		if (t < 0) {
			if (t % 2 == 0) {
				// Souls rush past towards the player, faster as the pull peaks
				double k = 1.0 + t / (double) Config.COLLAPSE_PULL_TICKS;
				WorldFx.particles(p, "minecraft:reverse_portal", 1.0, 2.5, 0.6 + k, 12 + (int) (k * 20));
				WorldFx.particles(p, "minecraft:soul", 1.0, 1.5, 0.02, 2 + (int) (k * 4));
			}
			return;
		}
		if (t % 20 == 0) {
			WorldFx.particles(p, "minecraft:sculk_soul", 1.0, 0.4, 0.1, 40);
			p.kill((ServerLevel) p.level());
		}
	}

	// ------------------------------------------------------------------ death and respawn

	public static void onDeath(ServerPlayer p) {
		PlayerData d = DataStore.get(p.getUUID());
		String dim = Dims.of(p);
		d.diedToday = true;
		// Every death undoes part of the penance, wherever it happens
		d.atonementLost += PenaltyService.deathToll(d);
		// Death always costs the chain (and the Tether item)
		if (d.tetherAnchor != null) {
			TetherService.clear(p, d, false);
		}
		if (Dims.OVERWORLD.equals(dim) && DataStore.overworldPenalties() && !d.collapsing) {
			d.endedIn = Dims.OVERWORLD;
			PenaltyService.owe(d, true);
			DataStore.markDirty();
			return;
		}
		if (Dims.isBeyond(dim) && !d.collapsing) {
			// Only this player's time goes to zero; everyone else carries on
			if (d.inRun) {
				for (Anchor a : AnchorService.linked(d)) {
					a.clear(p.getStringUUID());
				}
			}
			d.inRun = false;
			d.linked.clear();
			d.endedIn = dim;
			PenaltyService.owe(d, false);
			Journal.unlock(p, d, JournalEntry.ENDED);
			Advancements.grant(p, Advancements.ENDED);
			DataStore.markDirty();
		}
	}

	public static void onRespawn(ServerPlayer p, PlayerData d) {
		// Always back to the Overworld, even with a bed or respawn anchor beyond the wound
		if (!Dims.OVERWORLD.equals(Dims.of(p))) {
			sendToSpawn(p);
		}
		d.collapsing = false;
		d.collapseRetry = 0;
		d.inRun = false;
		d.linked.clear();
		d.runTime = 0;
		CurseEffects.applyEffects(p, d);
		if (Dims.OVERWORLD.equals(d.endedIn)) {
			WorldFx.title(p, "title.extremedimensions.tether_tightens", "dark_purple", "title.extremedimensions.died_in", Dims.nameKey(Dims.OVERWORLD));
			WorldFx.fx(p, Fx.ENDED, 0);
			WorldFx.soundSelf(p, "extremedimensions:expedition_ended", 1.0f, 0.8f);
		} else if (d.endedIn != null) {
			WorldFx.title(p, "title.extremedimensions.expedition_ended", "dark_red", "title.extremedimensions.died_in", Dims.nameKey(d.endedIn));
			WorldFx.fx(p, Fx.ENDED, 0);
			WorldFx.soundSelf(p, "extremedimensions:expedition_ended", 1.0f, 1.0f);
		} else {
			WorldFx.sound(p, "minecraft:block.beacon.deactivate", 1.0f, 0.8f);
		}
		d.endedIn = null;
		if (d.atonementLost > 0) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.curses.atonement_lost",
					Penalty.display(Math.round(Math.max(0, Math.min(1, Config.ATONE_DEATH_LOSS)) * 100)), d.atonementLost));
			d.atonementLost = 0;
		}
		if (!d.pendingOffer.isEmpty()) {
			d.offerDelay = Config.OFFER_DELAY_TICKS;
		}
		WorldFx.particles(p, "minecraft:reverse_portal", 1.0, 0.6, 0.15, 60);
	}

	/** A player killed a mob: a chance at a Tether Fragment beyond the wound, and lore for Endermen. */
	public static void onMobKilled(ServerPlayer killer, LivingEntity mob) {
		PlayerData d = DataStore.get(killer.getUUID());
		String type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
		if ("enderman".equals(type)) {
			Journal.unlock(killer, d, JournalEntry.UNANCHORED);
		}
		// Only a monster of the wild, mostly felled by the killer's own hand, and not too many at one spot, is atonement
		boolean elite = ELITES.contains(type);
		if ((elite || mob instanceof Enemy) && AtonementGuard.counts(killer, mob)) {
			PenaltyService.onKill(killer, elite, mob instanceof Enemy);
		}
		if (!Dims.isBeyond(Dims.of(mob.level())) || !(mob instanceof Enemy)) {
			return;
		}
		// Empty Pockets: fewer fragments find their way to the player
		double less = Penalty.EMPTY_POCKETS.value / 100.0 * d.stacks(Penalty.EMPTY_POCKETS);
		double chance = ELITES.contains(type) ? Config.FRAGMENT_CHANCE_ELITE - 3 * less : Config.FRAGMENT_CHANCE - less;
		if (ThreadLocalRandom.current().nextDouble() >= chance) {
			return;
		}
		String where = mob.level().dimension().identifier().toString();
		double x = mob.getX(), y = mob.getY() + 0.5, z = mob.getZ();
		mob.level().addFreshEntity(new ItemEntity(mob.level(), x, y, z, new ItemStack(ModItems.TETHER_FRAGMENT), 0, 0, 0));
		WorldFx.particleIn(where, "minecraft:soul", x, y, z, 0.3, 0.4, 0.3, 0.04, 25);
		WorldFx.soundIn(where, "minecraft:particle.soul_escape", "master", x, y, z, 1.5f, 0.7f);
		Journal.unlock(killer, d, JournalEntry.FRAGMENT);
	}
}
