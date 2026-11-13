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
import com.extremedimensions.relic.Relic;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;
import com.extremedimensions.tether.TetherService;
import com.extremedimensions.world.WorldService;
import com.extremedimensions.registry.ModItems;

import net.minecraft.ChatFormatting;
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
			if (!(d.inRun && Dims.SEAM.equals(from))) {
				start(p, d, to);
			}
			// Back from a detour into the Seam, the expedition carries on where it was (it is not begun anew)
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
			String why = dormant(p, d, dim, total);
			d.linked.clear();
			bounce(p, d);
			// Why it stayed shut, and what opens it (in the chat, where a longer line wraps and stays to be read)
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.bounce." + why, Config.PORTAL_RANGE).withStyle(net.minecraft.ChatFormatting.GRAY));
			com.extremedimensions.guide.Hints.meet(p, com.extremedimensions.guide.Hints.Hint.WOUND);
			return;
		}
		d.arrivalX = p.getX();
		d.arrivalY = p.getY();
		d.arrivalZ = p.getZ();
		d.hasRopeEnd = false;
		// On the expedition from here (so a pact's stacks of Leaky Tank count as well)
		d.inRun = true;
		// What the anchors held before anything was cut at the wound (3.55.0): the timer's bar is measured against it, so
		// what was cut shows as already gone, and the player is told what was taken
		double before = total, leaked = 0, chained = 0;
		int leaky = d.stacks(Penalty.LEAKY_TANK);
		if (leaky > 0) {
			// Leaky Tank: every expedition starts with a share of the stored charge gone
			double keep = Math.pow(Math.max(0, 1.0 - Penalty.LEAKY_TANK.frac()), leaky);
			for (Anchor a : AnchorService.linked(d)) {
				a.scale(uid, keep);
			}
			total = AnchorService.linkedCharge(p, d);
			leaked = before - total;
		}
		// Held's Chain: no anchor holds more for the player than the chain allows (charge laid in before it was bound too)
		if (RelicService.has(d, Relic.HELDS_CHAIN)) {
			double cap = AnchorService.cap(d);
			double was = total;
			for (Anchor a : AnchorService.linked(d)) {
				double own = a.own(uid);
				if (own > cap) {
					a.scale(uid, cap / own);
				}
			}
			total = AnchorService.linkedCharge(p, d);
			chained = was - total;
		}
		cut(p, leaked, chained);
		d.inRun = true;
		d.heldSpent = false;
		d.heldTicks = 0;
		// A new expedition, a new Last Stitch
		d.stitchSpent = false;
		d.runActive = 0;
		d.ferrymanRolled = false;
		d.masonRolled = false;
		com.extremedimensions.pact.PactService.begin(p, d);
		d.runTime = 0;
		d.runMax = before;
		// What this expedition comes to is counted from here, for its record (see ExpeditionRecord)
		d.runTally = ExpeditionRecord.begin(d, dim, Dims.OVERWORLD, total, d.tetherTier);
		d.lastWarn = Integer.MAX_VALUE;
		d.lastBeat = Integer.MAX_VALUE;
		d.runWarned = 0;
		boolean nether = Dims.NETHER.equals(dim);
		WorldFx.fx(p, nether ? Fx.START_NETHER : Fx.START_END, 0);
		Journal.unlock(p, d, nether ? JournalEntry.EMBER_DEEP : JournalEntry.PALE_VOID);
		com.extremedimensions.guide.Guide.done(p, d, com.extremedimensions.guide.Guide.THROUGH);
		WorldFx.sound(p, "extremedimensions:portal_beyond", 1.0f, nether ? 0.9f : 1.1f);
		if (nether) {
			WorldFx.particles(p, "minecraft:flame", 1.0, 0.6, 0.06, 60);
			WorldFx.particles(p, "minecraft:soul_fire_flame", 1.0, 1.2, 0.02, 30);
		} else {
			WorldFx.particles(p, "minecraft:reverse_portal", 1.0, 0.6, 0.2, 80);
			WorldFx.particles(p, "minecraft:end_rod", 1.0, 1.2, 0.03, 20);
		}
		if (nether) {
			// Through a warm gate: the chain's other end is near
			com.extremedimensions.lore.WarmChains.crossed(p, d, (ServerLevel) p.level(), p.blockPosition());
		}
	}

	/**
	 * What Leaky Tank and the Held's Chain took of the charge at the wound, said on the action bar (3.55.0: before, it
	 * went without a word). Nothing is said for less than a second.
	 */
	private static void cut(ServerPlayer p, double leaked, double chained) {
		boolean leak = leaked >= 1, chain = chained >= 1;
		if (!leak && !chain) {
			return;
		}
		Component line = leak && chain ? Component.translatable("gui.extremedimensions.entry.both", TimeLeft.clock(leaked), TimeLeft.clock(chained))
				: leak ? Component.translatable("gui.extremedimensions.entry.leaky", TimeLeft.clock(leaked))
				: Component.translatable("gui.extremedimensions.entry.chain", TimeLeft.clock(chained), TimeLeft.clock(AnchorService.cap(DataStore.get(p.getUUID()))));
		p.sendSystemMessage(line.copy().withStyle(ChatFormatting.GOLD), true);
	}

	/** Safe return: the chain disappears entirely and the Tether comes back. */
	private static void complete(ServerPlayer p, PlayerData d) {
		// Its record (read before the expedition is over: the Held's Chain's hold, the pact), told once the levels are taken
		ExpeditionRecord record = ExpeditionRecord.end(p, d, false, ExpeditionRecord.Outcome.RETURNED);
		TetherService.clear(p, d, true);
		if (d.heldTicks > 0) {
			// Home while the Held's Chain still held them: made it
			d.heldTicks = 0;
			WorldFx.soundSelf(p, "minecraft:block.chain.break", 0.8f, 1.2f);
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.relic.helds_chain.made_it").withStyle(ChatFormatting.GOLD));
		}
		// Ferryman's Fee: every crossing home is paid for in levels (counted while still on the expedition: a pact's too)
		int fee = (int) Math.round(Penalty.FERRYMANS_FEE.value) * d.stacks(Penalty.FERRYMANS_FEE);
		d.inRun = false;
		d.linked.clear();
		// Only an expedition that was one counts and earns: stepping through and straight back is not
		boolean real = d.runActive >= Math.max(0, Config.ATONE_RETURN_MIN_SECONDS);
		if (!real && record != null) {
			// (its line still comes, marked, so a spreadsheet can leave it out)
			record.notes.add("a step through and back: not counted");
		}
		if (real) {
			d.expeditions++;
			// Unmapped: every safe return counts towards its deed
			com.extremedimensions.penalty.AbyssalCurses.advance(p, d, Penalty.UNMAPPED, 1);
		}
		d.longestRun = Math.max(d.longestRun, d.runTime);
		d.runTime = 0;

		// Tithe Bell: the bell takes its due for every crossing home
		if (com.extremedimensions.relic.RelicService.has(d, com.extremedimensions.relic.Relic.TITHE_BELL)) {
			fee += Math.max(0, Config.RELIC_TITHE_BELL_LEVELS);
		}
		if (fee > 0 && p.experienceLevel > 0) {
			int taken = Math.min(fee, p.experienceLevel);
			p.giveExperienceLevels(-taken);
			if (record != null) {
				record.levels = taken;
			}
		}
		// The Toll panel of a severed world: every crossing home gives something back (a real one: a step through and
		// straight back is no crossing)
		if (real && com.extremedimensions.tapestry.Tapestry.levelsBack() > 0) {
			p.giveExperienceLevels(com.extremedimensions.tapestry.Tapestry.levelsBack());
		}
		if (real) {
			PenaltyService.atone(p, d, false, Config.ATONE_PER_RETURN);
		}
		// A pact kept to the end pays out (its share of the reward is its active time)
		com.extremedimensions.pact.PactService.kept(p, d);
		WorldFx.fx(p, Fx.SAFE, 0);
		WorldFx.particles(p, "minecraft:totem_of_undying", 1.0, 0.5, 0.35, 40);
		WorldFx.particles(p, "minecraft:soul", 1.0, 0.8, 0.03, 20);
		WorldFx.soundSelf(p, "extremedimensions:portal_home", 0.6f, 1.0f);
		Journal.unlock(p, d, JournalEntry.WOUND);
		Advancements.grant(p, Advancements.FIRST_EXPEDITION);
		com.extremedimensions.guide.Guide.done(p, d, com.extremedimensions.guide.Guide.HOME);
		if (d.expeditions >= 5) {
			Journal.unlock(p, d, JournalEntry.HABIT);
		}
		if (d.expeditions >= Advancements.VETERAN_EXPEDITIONS) {
			Advancements.grant(p, Advancements.VETERAN);
		}
		DataStore.markDirty();
		ExpeditionRecord.settle(p, d, record);
	}

	/**
	 * A Seam-Woven Tether unravels instead of letting the player collapse: the silk comes apart thread by thread and
	 * pulls them home, whole. The Tether is spent.
	 */
	private static void unravel(ServerPlayer p, PlayerData d) {
		// (what it cost is the Tether, and nothing more)
		ExpeditionRecord.ended(p, d, false, ExpeditionRecord.Outcome.UNRAVELLED);
		TetherService.clear(p, d, false);
		d.heldTicks = 0;
		d.tetherTier = 0;
		// Saved from the Collapse, not returned: the expedition ends here, without the reward of a safe return
		com.extremedimensions.pact.PactService.broken(p, d);
		d.inRun = false;
		d.linked.clear();
		d.longestRun = Math.max(d.longestRun, d.runTime);
		d.runTime = 0;
		DataStore.markDirty();
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
	/**
	 * Why the wound stayed shut for this player: no anchor of that world beside the portal they came through, an anchor
	 * whose light cannot reach it, none of their own time in one, no Tether bound, or a Tether bound to an anchor by
	 * another portal.
	 */
	public static String dormant(ServerPlayer p, PlayerData d, String dim, double total) {
		boolean near = false, open = false;
		if (d.hasLast) {
			double r2 = Config.LINK_RANGE * Config.LINK_RANGE;
			for (Anchor a : DataStore.anchors().values()) {
				if (dim.equals(a.type) && a.distSq(d.lastX, d.lastY, d.lastZ) <= r2) {
					near = true;
					open |= !a.blocked;
				}
			}
		}
		if (!near) {
			return "no_anchor." + AnchorService.side(dim);
		}
		if (!open) {
			return "blocked";
		}
		if (total <= 0) {
			return "no_charge";
		}
		return d.tetherAnchor == null ? "no_tether" : "elsewhere";
	}

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
		// A detour into the Seam does not stop the expedition's charge running out (its distance does not count there)
		boolean seam = Dims.SEAM.equals(dim);
		if ((Dims.isBeyond(dim) || seam) && d.inRun && !d.collapsing) {
			// What eases the drain here (a relic, a mended world) is held to the drain floor with the rest; what worsens it
			// is not (and a severed world has none at all)
			// (the Toll panel of a mended world eases it too)
			double world = WorldService.drainFactor(dim), relic = com.extremedimensions.relic.RelicService.drainFactor(d, dim);
			double toll = Math.min(1, com.extremedimensions.tapestry.Tapestry.drain(dim));
			double distance = seam ? 1.0 : TetherService.distanceFactor(p, d);
			d.currentDrain = (world <= 0 ? 0 : d.drainMultiplier(Math.min(1, world) * Math.min(1, relic) * toll * com.extremedimensions.tetherite.Tetherite.drainEase(p)) * Math.max(1, world) * Math.max(1, relic))
					* distance * com.extremedimensions.elder.FerrymanService.drainFactor(p)
					* com.extremedimensions.binding.Homebound.drainFactor(p) * com.extremedimensions.events.WorldEvents.drainFactor(p)
					* com.extremedimensions.reach.PaleReach.drainFactor(p) * com.extremedimensions.penalty.Wound.drainFactor(d);
			double left = AnchorService.drainLinked(p, d, d.currentDrain / 20.0);
			d.runTime += 1.0 / 20.0;
			double far = seam ? 0 : TetherService.arrivalDistance(p, d);
			// For its record: how far out, how much of the drain the distance made, and the drain itself
			ExpeditionRecord.tick(d.runTally, far, distance, d.currentDrain);
			if (far > d.furthest) {
				d.furthest = far;
				if (far >= Advancements.LONG_TETHER_BLOCKS) {
					Advancements.grant(p, Advancements.LONG_TETHER);
				}
			}
			if (left <= 0 && holds(p, d, seam)) {
				// The Held's Chain holds them a little longer: home before it lets go, and no Collapse
				changed = d.heldTicks % 20 == 0;
			} else if (left <= 0 && d.tetherTier == 2 && d.tetherAnchor != null) {
				unravel(p, d);
				changed = true;
			} else if (left <= 0) {
				collapse(p, d);
				changed = true;
			} else {
				// The bells, the beat of the last ten seconds and (3.55.0) the warnings in words; from the Seam there is no
				// walking back to the portal
				TimeLeft.warn(p, d, left, seam ? -1 : TimeLeft.walkBack(p, d), false);
				if (left < Config.LEECH_SECONDS && d.tickCounter % 10 == 0) {
					leech(p, left);
				}
			}
		} else if (d.collapsing) {
			pull(p, d);
		}
		if (!((Dims.isBeyond(dim) || seam) && d.inRun)) {
			// (a Fresh Wound shows in it at home too: the drain the next expedition would start at)
			d.currentDrain = d.drainMultiplier() * com.extremedimensions.penalty.Wound.drainFactor(d);
		}
		return changed;
	}

	/** Leaving the game while the Held's Chain holds is no way out: the hold ends there, as if its time had run out. */
	public static void leaving(ServerPlayer p, PlayerData d) {
		if (d.heldTicks > 0 && d.inRun && !d.collapsing) {
			d.heldTicks = 0;
			if (d.tetherTier == 2 && d.tetherAnchor != null) {
				unravel(p, d);
			} else {
				collapse(p, d);
			}
			DataStore.markDirty();
		}
	}

	/**
	 * The Held's Chain: once an expedition, when the charge runs out beyond the wound (not in the Seam), the chain holds
	 * the player for {@link Config#RELIC_HELDS_CHAIN_GRACE} seconds before the Collapse. True while it holds.
	 */
	private static boolean holds(ServerPlayer p, PlayerData d, boolean seam) {
		if (d.heldTicks > 0) {
			d.heldTicks--;
			if (d.heldTicks % 20 == 0 && d.heldTicks > 0) {
				int secs = d.heldTicks / 20;
				WorldFx.soundSelf(p, "minecraft:block.chain.step", 1.0f, 0.6f + 0.04f * Math.max(0, 10 - secs));
				WorldFx.particles(p, "minecraft:small_flame", 1.0, 0.5, 0.01, 6);
			}
			return d.heldTicks > 0;
		}
		if (d.heldSpent || seam || Config.RELIC_HELDS_CHAIN_GRACE <= 0 || !RelicService.has(d, Relic.HELDS_CHAIN)) {
			return false;
		}
		d.heldSpent = true;
		d.heldTicks = Config.RELIC_HELDS_CHAIN_GRACE * 20;
		DataStore.markDirty();
		WorldFx.sound(p, "minecraft:block.chain.place", 1.2f, 0.5f);
		WorldFx.soundSelf(p, "minecraft:entity.warden.heartbeat", 1.3f, 0.8f);
		WorldFx.particles(p, "minecraft:small_flame", 1.0, 0.8, 0.02, 30);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.relic.helds_chain.holds",
				Config.RELIC_HELDS_CHAIN_GRACE), true);
		return true;
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
		// Thrift: the Tether that drank less takes its due now (read before the Tether is lost)
		boolean thrift = d.charm(com.extremedimensions.registry.ModEnchantments.THRIFT) > 0;
		d.foresightOwed = com.extremedimensions.binding.Charms.foresight(d);
		TetherService.clear(p, d, false);
		d.heldTicks = 0;
		com.extremedimensions.pact.PactService.broken(p, d);
		d.collapsing = true;
		d.collapseOwed = true;
		d.collapseRetry = -Config.COLLAPSE_PULL_TICKS;
		d.collapses++;
		// Its record takes the curses owed now as its own, and is told once the player is home and has chosen them
		ExpeditionRecord record = ExpeditionRecord.end(p, d, false, ExpeditionRecord.Outcome.COLLAPSED);
		oweCollapse(d, thrift);
		ExpeditionRecord.settle(p, d, record);
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

	/**
	 * The curses a death beyond the wound costs (public for tests): a death's (so a bound Debtor's Seal asks it more than
	 * once), and on top of it what a bound Ferryman's Obol asks for the passage.
	 */
	public static void oweDeathBeyond(PlayerData d, boolean overworld, boolean boss) {
		PenaltyService.oweDeath(d, overworld, boss);
		for (int i = 0, n = RelicService.passage(d); i < n; i++) {
			PenaltyService.owe(d, overworld);
		}
	}

	/**
	 * The curses a Collapse costs (public for tests): its own, as a death's beyond the wound (so a bound Debtor's Seal asks
	 * it more than once, and a Ferryman's Obol its passage), and on top of it what a Tether with Thrift and a bound
	 * Lodestone Chip take for the charge they eked out.
	 */
	public static void oweCollapse(PlayerData d, boolean thrift) {
		oweDeathBeyond(d, false, false);
		if (thrift) {
			for (int i = 0; i < Config.ENCH_THRIFT_COLLAPSE; i++) {
				PenaltyService.owe(d, false);
			}
		}
		if (RelicService.has(d, Relic.LODESTONE_CHIP)) {
			for (int i = 0; i < Config.RELIC_LODESTONE_CHIP_COLLAPSE; i++) {
				PenaltyService.owe(d, false);
			}
		}
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
		if (d.away || !Dims.ours(p)) {
			// A death in another mod's world costs nothing of the mod's: no penance, no chain, no curse
			d.diedAway = true;
			return;
		}
		d.diedToday = true;
		d.collapseOwed = false;
		// Fallen in a boss fight: to a bound Debtor's Seal, the death's curses are a boss's
		boolean boss = RelicService.has(d, Relic.DEBTORS_SEAL) && RelicService.bossNear(p);
		// Every death undoes part of the penance, wherever it happens, and leaves a Fresh Wound (or opens it again)
		int toll = PenaltyService.deathToll(d);
		d.atonementLost += toll;
		com.extremedimensions.penalty.Wound.open(d);
		// A Collapse that ends in death beyond the wound leaves an echo there
		if (d.collapsing && Dims.isBeyond(dim)) {
			com.extremedimensions.events.Echoes.record(p, dim, toll);
		}
		if (d.collapsing && Dims.SEAM.equals(dim)) {
			// The expedition collapsed while on a visit to the Seam: the visit ends with it (what it cost is the expedition's)
			ExpeditionRecord.ended(p, d, true, ExpeditionRecord.Outcome.COLLAPSED);
		}
		// Death always costs the chain (and the Tether item)
		if (d.tetherAnchor != null) {
			// Foresight still shows its card for this death, though the Tether is gone
			d.foresightOwed = com.extremedimensions.binding.Charms.foresight(d);
			TetherService.clear(p, d, false);
		}
		if (Dims.OVERWORLD.equals(dim) && DataStore.overworldPenalties() && !d.collapsing) {
			d.endedIn = Dims.OVERWORLD;
			PenaltyService.oweDeath(d, true, boss);
			DataStore.markDirty();
			return;
		}
		if (Dims.SEAM.equals(dim) && !d.collapsing) {
			// A death in the Seam costs what its Collapse would: a curse of the set of the world the visit began from,
			// and (on an expedition) the charge carried, as a death beyond would
			if (d.inRun) {
				// The expedition the visit was made from ends here too, and the charge it carried is its cost
				ExpeditionRecord run = ExpeditionRecord.end(p, d, false, ExpeditionRecord.Outcome.DIED_IN_SEAM);
				if (run != null) {
					run.chargeLost = AnchorService.linkedCharge(p, d);
				}
				ExpeditionRecord.settle(p, d, run);
				com.extremedimensions.pact.PactService.broken(p, d);
				for (Anchor a : AnchorService.linked(d)) {
					a.clear(p.getStringUUID());
				}
			}
			// The visit's record takes the death's curses, and the atonement it undid
			ExpeditionRecord visit = ExpeditionRecord.end(p, d, true, ExpeditionRecord.Outcome.DIED);
			if (visit != null) {
				visit.atonement = toll;
			}
			d.inRun = false;
			d.linked.clear();
			d.endedIn = dim;
			oweDeathBeyond(d, Dims.OVERWORLD.equals(d.homeDim) && DataStore.overworldPenalties(), boss);
			ExpeditionRecord.settle(p, d, visit);
			DataStore.markDirty();
			return;
		}
		if (Dims.isBeyond(dim) && !d.collapsing && d.inRun) {
			com.extremedimensions.pact.PactService.broken(p, d);
		}
		if (Dims.isBeyond(dim) && !d.collapsing) {
			// Its record takes the charge carried, the atonement undone and the death's curses
			ExpeditionRecord record = d.inRun ? ExpeditionRecord.end(p, d, false, ExpeditionRecord.Outcome.DIED) : null;
			if (record != null) {
				record.chargeLost = AnchorService.linkedCharge(p, d);
				record.atonement = toll;
			}
			// Only this player's time goes to zero; everyone else carries on
			if (d.inRun) {
				for (Anchor a : AnchorService.linked(d)) {
					a.clear(p.getStringUUID());
				}
			}
			d.inRun = false;
			d.linked.clear();
			d.endedIn = dim;
			oweDeathBeyond(d, false, boss);
			ExpeditionRecord.settle(p, d, record);
			Journal.unlock(p, d, JournalEntry.ENDED);
			Advancements.grant(p, Advancements.ENDED);
			DataStore.markDirty();
		}
	}

	/**
	 * The chance a monster slain beyond the wound leaves a Tether Fragment. Empty Pockets takes points off it, but always
	 * leaves {@code empty_pockets_floor} of it, so fragments never stop on the harder difficulties.
	 */
	public static double fragmentChance(PlayerData d, boolean elite) {
		double base = elite ? Config.FRAGMENT_CHANCE_ELITE : Config.FRAGMENT_CHANCE;
		double less = Penalty.EMPTY_POCKETS.value / 100.0 * d.stacks(Penalty.EMPTY_POCKETS) * (elite ? 3 : 1);
		return Math.max(base * Math.max(0, Config.EMPTY_POCKETS_FLOOR), base - less);
	}

	public static void onRespawn(ServerPlayer p, PlayerData d) {
		if (d.diedAway) {
			// Died outside the wound's reach: an expedition left waiting there ends quietly, without reward or curse
			if (d.inRun) {
				ExpeditionRecord.ended(p, d, false, ExpeditionRecord.Outcome.DIED_ELSEWHERE);
			}
			d.diedAway = false;
			d.collapsing = false;
			d.collapseRetry = 0;
			d.inRun = false;
			d.linked.clear();
			d.runTime = 0;
			d.away = !Dims.ours(p);
			d.dim = d.away ? Dims.OVERWORLD : Dims.of(p);
			if (!d.away && Dims.isBeyond(Dims.of(p))) {
				// A respawn point beyond the wound sends the player home, as any respawn does
				sendToSpawn(p);
				d.dim = Dims.OVERWORLD;
			}
			CurseEffects.applyEffects(p, d);
			return;
		}
		// Always back to the Overworld, even with a bed or respawn anchor beyond the wound (one in another mod's world is its own business)
		if (Dims.ours(p) && !Dims.OVERWORLD.equals(Dims.of(p))) {
			sendToSpawn(p);
		}
		d.collapsing = false;
		d.collapseRetry = 0;
		d.inRun = false;
		d.linked.clear();
		d.runTime = 0;
		CurseEffects.applyEffects(p, d);
		// The death itself is message enough: the haze and the sound stay, the big title only if the config asks for it
		boolean title = Config.RESPAWN_TITLE == 1;
		if (Dims.OVERWORLD.equals(d.endedIn)) {
			if (title) {
				WorldFx.title(p, "title.extremedimensions.tether_tightens", "dark_purple", "title.extremedimensions.died_in", Dims.nameKey(Dims.OVERWORLD));
			}
			WorldFx.fx(p, Fx.ENDED, 0);
			WorldFx.soundSelf(p, "extremedimensions:expedition_ended", 1.0f, 0.8f);
		} else if (d.endedIn != null) {
			if (title) {
				WorldFx.title(p, "title.extremedimensions.expedition_ended", "dark_red", "title.extremedimensions.died_in", Dims.nameKey(d.endedIn));
			}
			WorldFx.fx(p, Fx.ENDED, 0);
			WorldFx.soundSelf(p, "extremedimensions:expedition_ended", 1.0f, 1.0f);
		} else {
			WorldFx.sound(p, "minecraft:block.beacon.deactivate", 1.0f, 0.8f);
		}
		d.endedIn = null;
		// Home: a Collapse's record takes the atonement its death undid, and is told once what it cost is chosen
		ExpeditionRecord.home(p, d);
		if (d.atonementLost > 0) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.curses.atonement_lost",
					Penalty.display(Math.round(Math.max(0, Math.min(1, Config.ATONE_DEATH_LOSS)) * 100)), d.atonementLost));
			d.atonementLost = 0;
		}
		// What the Fresh Wound takes, and for how long
		com.extremedimensions.penalty.Wound.tell(p, d);
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
		// Only a monster of the wild, mostly felled by the killer's own hand, and not too many at one spot, is atonement,
		// and only such a monster may leave a Tether Fragment (fragments lift curses too: no farm may yield them)
		boolean elite = ELITES.contains(type);
		boolean wild = (elite || mob instanceof Enemy) && AtonementGuard.counts(killer, mob);
		if (wild) {
			PenaltyService.onKill(killer, elite, mob instanceof Enemy);
		}
		if (!wild || !Dims.isBeyond(Dims.of(mob.level())) || !(mob instanceof Enemy)) {
			return;
		}
		double chance = fragmentChance(d, ELITES.contains(type));
		if (com.extremedimensions.relic.RelicService.has(d, com.extremedimensions.relic.Relic.KEEPERS_EYE)) {
			chance *= Math.max(0, Config.RELIC_KEEPERS_EYE_FRAGMENTS);
		}
		if (ThreadLocalRandom.current().nextDouble() >= chance) {
			return;
		}
		String where = mob.level().dimension().identifier().toString();
		double x = mob.getX(), y = mob.getY() + 0.5, z = mob.getZ();
		mob.level().addFreshEntity(new ItemEntity(mob.level(), x, y, z, new ItemStack(ModItems.TETHER_FRAGMENT), 0, 0, 0));
		WorldFx.particleIn(where, "minecraft:soul", x, y, z, 0.3, 0.4, 0.3, 0.04, 25);
		WorldFx.soundIn(where, "minecraft:particle.soul_escape", "player", x, y, z, 1.5f, 0.7f);
		Journal.unlock(killer, d, JournalEntry.FRAGMENT);
	}
}
