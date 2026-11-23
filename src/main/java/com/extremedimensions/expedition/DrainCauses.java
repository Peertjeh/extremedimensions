package com.extremedimensions.expedition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.extremedimensions.anchor.AnchorService;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.relic.Relic;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.seam.SeamDepth;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.Dims;
import com.extremedimensions.tether.TetherService;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Why the charge runs out as fast as it does (3.55.0). The drain is taken apart into what makes it, each part a factor
 * it is multiplied by, so the timer can name its main cause beside it ("x2.4 · Echo near") and the anchor status screen
 * can list them all. The parts are the very ones {@link ExpeditionService#tick} multiplies, reckoned the same way, and
 * multiplied together they come to the drain; nothing here changes any of them. On top of them come the seconds the
 * expedition's own anchors leak while one is cracked or blocked, which run its charge out as surely as the drain does.
 */
public final class DrainCauses {
	private DrainCauses() {}

	/** One part of the drain: the words that name it (a language key) and what it multiplies the drain by. */
	public record Factor(String key, double value) {}

	/**
	 * The drain taken apart: what the charge runs out at now (seconds a second, leaks counted), its parts, and the main
	 * cause (a language key; "" when there is none worth naming).
	 */
	public record Breakdown(double drain, List<Factor> factors, String cause) {
		/** The parts as the client reads them: "key=1.30;key=0.85". */
		public String encode() {
			StringBuilder out = new StringBuilder();
			for (Factor f : factors) {
				if (out.length() > 0) {
					out.append(';');
				}
				out.append(f.key()).append('=').append(String.format(Locale.ROOT, "%.2f", f.value()));
			}
			return out.toString();
		}

		/** The drain's words for the boss bar: "Drain x2.4 · Echo near", green below one, yellow and red as it climbs. */
		public MutableComponent label() {
			MutableComponent out = Component.translatable("hud.extremedimensions.drain", String.format(Locale.ROOT, "%.1f", drain));
			if (!cause.isEmpty()) {
				out.append(" · ").append(Component.translatable(cause));
			}
			return out.withStyle(drain < 1 ? ChatFormatting.GREEN : drain >= 3 ? ChatFormatting.RED : drain >= 1.8 ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
		}

		/** Whether the drain is worth showing: it differs from one by enough to read in a tenth. */
		public boolean shown() {
			return Math.abs(drain - 1) >= 0.05;
		}
	}

	/** The language keys of the parts (a relic's is its own name). */
	public static final String PREFIX = "hud.extremedimensions.cause.";
	public static final String BASE = PREFIX + "base", WOVEN = PREFIX + "woven", SCARS = PREFIX + "scars", THRIFT = PREFIX + "thrift",
			FLOOR = PREFIX + "floor", ACCELERATED = PREFIX + "accelerated", WOUND = PREFIX + "wound", MENDED = PREFIX + "mended",
			STARVING = PREFIX + "starving", SEVERED = PREFIX + "severed", TOLL = PREFIX + "toll", TETHERITE = PREFIX + "tetherite",
			DISTANCE = PREFIX + "distance", FERRYMAN = PREFIX + "ferryman", HOMEBOUND = PREFIX + "homebound", ECHO = PREFIX + "echo",
			PALE = PREFIX + "pale", CRACKED = PREFIX + "cracked", BLOCKED = PREFIX + "blocked", FRAY = PREFIX + "fray",
			FRAYED = PREFIX + "frayed", STILL = PREFIX + "still";

	/** How far from one a part must be to count as one (a hundredth of a second a second is nothing to name). */
	private static final double NOTHING = 0.005;

	/**
	 * The drain as it is for this player now: on an expedition every part of it, in the Seam the thread's, and at home
	 * what the next expedition would start at (the Tether, scars, curses and a Fresh Wound).
	 */
	public static Breakdown of(ServerPlayer p, PlayerData d) {
		List<Factor> out = new ArrayList<>();
		if (d.inSeam && Dims.SEAM.equals(d.dim)) {
			double drain = SeamService.drain(p);
			if (drain <= 0) {
				// (creative, the Heart's fight, the Knot Mother near: the thread does not run at all)
				out.add(new Factor(STILL, 0));
			} else {
				add(out, FRAY, SeamDepth.drain(p.getY()));
				add(out, FRAYED, com.extremedimensions.events.FrayedSeam.drain(p));
			}
			return new Breakdown(drain, out, cause(drain, out));
		}
		boolean run = d.inRun && !d.collapsing && (Dims.NETHER.equals(d.dim) || Dims.END.equals(d.dim));
		String dim = d.dim;
		double world = run ? com.extremedimensions.world.WorldService.drainFactor(dim) : 1.0;
		if (world <= 0) {
			// A severed world: the Tether counts nothing beyond the wound
			out.add(new Factor(SEVERED, 0));
			return new Breakdown(0, out, SEVERED);
		}
		double relic = run ? RelicService.drainFactor(d, dim) : 1.0;
		double toll = run ? Math.min(1, com.extremedimensions.tapestry.Tapestry.drain(dim)) : 1.0;
		double tetherite = run ? com.extremedimensions.tetherite.Tetherite.drainEase(p) : 1.0;
		// What eases the drain, held together to the drain floor (as PlayerData.drainMultiplier holds it)
		double tier = d.tetherTier == 2 ? Config.SEAM_WOVEN_DRAIN : d.tetherTier == 1 ? Config.SOUL_WOVEN_DRAIN : 1.0;
		double scar = 1.0 - Config.SCAR_DRAIN_REDUCTION * Math.min(5, d.scars);
		double thrift = 1.0 - Config.ENCH_THRIFT_DRAIN * d.charm(com.extremedimensions.registry.ModEnchantments.THRIFT);
		double ease = Math.min(1.0, Math.min(1, world) * Math.min(1, relic) * toll * tetherite);
		double eased = tier * scar * thrift * ease;
		add(out, BASE, Config.BASE_DRAIN);
		if (eased > 0) {
			add(out, WOVEN, tier);
			add(out, SCARS, scar);
			add(out, THRIFT, thrift);
			add(out, world < 1 ? MENDED : STARVING, world);
			add(out, relicKey(d), relic);
			add(out, TOLL, toll);
			add(out, TETHERITE, tetherite);
			if (eased < Config.ENCH_DRAIN_FLOOR) {
				// the eases went past the floor: this much of them does not count
				add(out, FLOOR, Config.ENCH_DRAIN_FLOOR / eased);
			}
		} else {
			// (an ease set to nothing: the floor is all that is left of them)
			add(out, FLOOR, Config.ENCH_DRAIN_FLOOR);
			add(out, STARVING, Math.max(1, world));
			add(out, relicKey(d), Math.max(1, relic));
		}
		add(out, ACCELERATED, 1.0 + Penalty.ACCELERATED_DRAIN.frac() * d.stacks(Penalty.ACCELERATED_DRAIN));
		if (run) {
			add(out, DISTANCE, TetherService.distanceFactor(p, d));
			add(out, FERRYMAN, com.extremedimensions.elder.FerrymanService.drainFactor(p));
			add(out, HOMEBOUND, com.extremedimensions.binding.Homebound.drainFactor(p));
			add(out, ECHO, com.extremedimensions.events.WorldEvents.drainFactor(p));
			add(out, PALE, com.extremedimensions.reach.PaleReach.drainFactor(p));
		}
		add(out, WOUND, com.extremedimensions.penalty.Wound.drainFactor(d));
		double drain = d.currentDrain;
		if (run && drain > 0) {
			// The expedition's anchors leaking on top of the drain: as much again of the charge, every second
			double[] leak = leaks(p, d);
			add(out, CRACKED, (drain + leak[0]) / drain);
			add(out, BLOCKED, (drain + leak[0] + leak[1]) / (drain + leak[0]));
			drain += leak[0] + leak[1];
		}
		return new Breakdown(drain, out, cause(drain, out));
	}

	/** A part, when it does anything. */
	private static void add(List<Factor> out, String key, double value) {
		if (Math.abs(value - 1) >= NOTHING) {
			out.add(new Factor(key, value));
		}
	}

	/** The words for the relic bound: its own name. */
	private static String relicKey(PlayerData d) {
		Relic r = RelicService.bound(d);
		return r == null ? PREFIX + "relic" : r.nameKey();
	}

	/**
	 * The main cause: where the drain runs faster than one, the part that speeds it most besides the distance (the
	 * distance itself only when nothing else does); where it runs slower, the part that eases it most.
	 */
	public static String cause(double drain, List<Factor> factors) {
		Factor best = null;
		double distance = 1;
		for (Factor f : factors) {
			if (DISTANCE.equals(f.key())) {
				distance = f.value();
				continue;
			}
			if (drain >= 1 ? f.value() > 1 && (best == null || f.value() > best.value()) : f.value() < 1 && (best == null || f.value() < best.value())) {
				best = f;
			}
		}
		if (best == null && drain > 1 && distance > 1) {
			return DISTANCE;
		}
		return best == null ? "" : best.key();
	}

	/**
	 * Seconds a second the anchors feeding the expedition lose of what they keep for the player besides the drain: {what
	 * the cracked ones leak, what the blocked ones leak}. A cracked one leaks a set share from everyone's charge, a
	 * blocked one its leak shared out over all it holds (see UnstableAnchors and AnchorService).
	 */
	public static double[] leaks(ServerPlayer p, PlayerData d) {
		String uid = p.getStringUUID();
		double cracked = 0, blocked = 0;
		for (Anchor a : AnchorService.linked(d)) {
			double mine = a.of(uid);
			if (mine <= 0) {
				continue;
			}
			if (a.unstable > 0) {
				cracked += Math.min(mine, Math.max(0, Config.UNSTABLE_LEAK));
			}
			if (a.blocked && a.charge > 0) {
				blocked += Math.max(0, Config.BLOCKED_LEAK_PER_SECOND) * mine / a.charge;
			}
		}
		return new double[] {cracked, blocked};
	}
}
