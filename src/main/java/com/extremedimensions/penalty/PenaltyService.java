package com.extremedimensions.penalty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.network.Fx;
import com.extremedimensions.network.Sync;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Curses: owing them, the offer of cards (sometimes multiplied), and taking them off again through atonement, Tether
 * Fragments and Mending Cloth. Every stack taken off leaves a scar. What the curses do lives in {@link CurseEffects}.
 */
public final class PenaltyService {
	private PenaltyService() {}

	private static final String BEYOND = "beyond";
	private static final String OVERWORLD = "overworld";

	// ------------------------------------------------------------------ owing and choosing

	/** The player owes a curse from the beyond or the Overworld set. */
	public static void owe(PlayerData d, boolean overworld) {
		d.pendingPenalties++;
		d.owed.add(overworld ? OVERWORLD : BEYOND);
		if (d.pendingOffer.isEmpty()) {
			nextOffer(d);
		}
	}

	/**
	 * Draws the cards for the first owed curse. Sometimes the whole offer is multiplied: then every card carries its
	 * own multiplier. A set with every curse at its maximum offers nothing; the Starved take all levels instead.
	 */
	private static void nextOffer(PlayerData d) {
		while (!d.owed.isEmpty() && d.pendingOffer.isEmpty()) {
			boolean ow = OVERWORLD.equals(d.owed.get(0));
			List<String> ids = Penalty.rollOffer(d, ow, Config.OFFER_COUNT, Config.EXTREME_CARD_CHANCE);
			if (ids.isEmpty()) {
				d.owed.remove(0);
				d.pendingPenalties = Math.max(0, d.pendingPenalties - 1);
				d.xpForfeit = true;
				continue;
			}
			d.pendingOffer = ids;
			d.pendingMult = multipliers(ids.size());
			// Extreme cards never carry a multiplier
			for (int i = 0; i < ids.size(); i++) {
				Penalty pen = Penalty.byId(ids.get(i));
				if (pen != null && pen.extreme) {
					d.pendingMult.set(i, 1);
				}
			}
		}
	}

	private static List<Integer> multipliers(int n) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		boolean multiplied = rnd.nextDouble() < Config.MULTIPLIER_CHANCE;
		int lo = Math.max(1, Config.MULTIPLIER_MIN), hi = Math.max(lo, Config.MULTIPLIER_MAX);
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(multiplied ? lo + rnd.nextInt(hi - lo + 1) : 1);
		}
		return out;
	}

	/** The player picked a card on the curse screen. */
	public static void choose(ServerPlayer p, String id) {
		PlayerData d = DataStore.get(p.getUUID());
		Penalty pen = Penalty.byId(id);
		int idx = d.pendingOffer.indexOf(id);
		if (pen == null || idx < 0) {
			return;
		}
		int mult = idx < d.pendingMult.size() ? Math.max(1, d.pendingMult.get(idx)) : 1;
		d.pendingOffer.clear();
		d.pendingMult.clear();
		d.pendingPenalties = Math.max(0, d.pendingPenalties - 1);
		if (!d.owed.isEmpty()) {
			d.owed.remove(0);
		}

		int added = d.addStacks(pen, mult);
		Map<Penalty, Integer> spilled = spill(d, mult - added);
		CurseEffects.applyEffects(p, d);
		WorldFx.fx(p, Fx.PENALTY, 0);
		WorldFx.particles(p, "minecraft:soul", 1.0, 0.5, 0.05, 25 * mult);
		WorldFx.particles(p, "minecraft:sculk_soul", 1.2, 0.6, 0.03, 12 * mult);
		WorldFx.soundSelf(p, "extremedimensions:penalty", 1.0f, mult > 1 ? 0.8f : 1.0f);
		Journal.unlock(p, d, JournalEntry.SCARS);
		Advancements.grant(p, Advancements.MARKED);
		if (!spilled.isEmpty()) {
			MutableComponent names = Component.empty();
			boolean first = true;
			for (Map.Entry<Penalty, Integer> e : spilled.entrySet()) {
				if (!first) {
					names.append(", ");
				}
				first = false;
				names.append(Component.translatable(e.getKey().nameKey())).append(" +" + e.getValue());
			}
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.choice.spilled", names), true);
		}

		// More owed (several deaths in a row)? The next screen follows.
		nextOffer(d);
		if (!d.pendingOffer.isEmpty()) {
			d.offerDelay = 40;
		}
		Sync.state(p, d);
		DataStore.markDirty();
	}

	/**
	 * Stacks a multiplied card could not add (its curse reached the maximum) are not lost: each lands on a random curse
	 * that still has room, in any set (Overworld curses only while they are switched on). Extreme curses take part with
	 * a smaller weight ({@code extreme_spill_weight}).
	 */
	private static Map<Penalty, Integer> spill(PlayerData d, int n) {
		Map<Penalty, Integer> out = new LinkedHashMap<>();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double extremeWeight = Math.max(0, Config.EXTREME_SPILL_WEIGHT);
		for (int i = 0; i < n; i++) {
			List<Penalty> room = Penalty.withRoom(d, DataStore.overworldPenalties());
			double total = 0;
			for (Penalty pen : room) {
				total += pen.extreme ? extremeWeight : 1;
			}
			if (total <= 0) {
				break;
			}
			double roll = rnd.nextDouble() * total;
			Penalty t = null;
			for (Penalty pen : room) {
				roll -= pen.extreme ? extremeWeight : 1;
				if (roll < 0) {
					t = pen;
					break;
				}
			}
			if (t == null) {
				t = room.get(room.size() - 1);
			}
			if (d.addStacks(t, 1) > 0) {
				out.merge(t, 1, Integer::sum);
			}
		}
		return out;
	}

	/** A curse laid without a choice (a Lurker, a Collapse in the Seam): one stack of a random curse. Null if none fits. */
	public static Penalty curse(ServerPlayer p, boolean overworld) {
		PlayerData d = DataStore.get(p.getUUID());
		List<String> ids = Penalty.rollOffer(d, overworld, 1, 0);
		if (ids.isEmpty()) {
			return null;
		}
		Penalty pen = Penalty.byId(ids.get(0));
		d.addStacks(pen, 1);
		CurseEffects.applyEffects(p, d);
		WorldFx.fx(p, Fx.PENALTY, 0);
		Sync.state(p, d);
		DataStore.markDirty();
		return pen;
	}

	// ------------------------------------------------------------------ taking curses off

	/**
	 * The curse that mending works on: the one being atoned for, else the heaviest. With a world given, only curses that
	 * can be atoned for there: that world's set and the Everywhere set. Null = any curse.
	 */
	public static Penalty target(PlayerData d, Boolean overworld) {
		Penalty chosen = d.atoning == null ? null : Penalty.byId(d.atoning);
		if (chosen != null && d.stacks(chosen) > 0 && fits(chosen, overworld)) {
			return chosen;
		}
		Penalty best = null;
		for (Penalty p : Penalty.values()) {
			if (fits(p, overworld) && d.stacks(p) > 0 && (best == null || d.stacks(p) > d.stacks(best))) {
				best = p;
			}
		}
		return best;
	}

	private static boolean fits(Penalty p, Boolean overworld) {
		return overworld == null || p.everywhere || (overworld ? p.overworld : p.group == Penalty.Group.BEYOND);
	}

	/** Atonement points needed to take the next stack of a curse off: more for every stack above the last. */
	public static int cost(PlayerData d, Penalty p) {
		int base = Math.max(1, Config.ATONE_BASE + Config.ATONE_PER_STACK * Math.max(0, d.stacks(p) - 1));
		return p.extreme ? (int) Math.ceil(base * Math.max(1, Config.EXTREME_ATONE_FACTOR)) : base;
	}

	/** Atonement earned in one world goes to the curse being atoned for in that set (or the heaviest one there). */
	public static void atone(ServerPlayer p, PlayerData d, boolean overworld, int points) {
		for (int guard = 0; points > 0 && guard < 16; guard++) {
			Penalty t = target(d, overworld);
			if (t == null) {
				return;
			}
			int have = d.atonement.getOrDefault(t.id, 0) + points;
			int need = cost(d, t);
			if (have < need) {
				d.atonement.put(t.id, have);
				Sync.curses(p, d, false);
				return;
			}
			points = have - need;
			lift(p, d, t, "gui.extremedimensions.curses.atoned");
		}
	}

	/**
	 * A death undoes part of the penance: every curse's atonement loses {@code atone_death_loss} (half by default),
	 * rounded against the player. Returns the points lost.
	 */
	public static int deathToll(PlayerData d) {
		double keep = 1 - Math.max(0, Math.min(1, Config.ATONE_DEATH_LOSS));
		int lost = 0;
		for (var it = d.atonement.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			int have = e.getValue();
			int kept = (int) Math.floor(have * keep);
			lost += have - kept;
			if (kept <= 0) {
				it.remove();
			} else {
				e.setValue(kept);
			}
		}
		return lost;
	}

	/** A Tether Fragment offered to an anchor: takes a stack off, at one fragment per stack the curse has. */
	public static InteractionResult mend(ServerPlayer p, PlayerData d, BlockPos pos, ItemStack stack) {
		Penalty t = target(d, null);
		if (t == null) {
			WorldFx.soundSelf(p, "minecraft:block.note_block.bass", 0.8f, 0.5f);
			return InteractionResult.SUCCESS;
		}
		int cost = Math.max(1, Config.FRAGMENT_COST_PER_STACK * d.stacks(t));
		if (t.extreme) {
			cost = (int) Math.ceil(cost * Math.max(1, Config.EXTREME_ATONE_FACTOR));
		}
		if (!p.isCreative()) {
			if (count(p, stack) < cost) {
				WorldFx.soundSelf(p, "minecraft:block.note_block.bass", 0.8f, 0.5f);
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.curses.needs_fragments", cost,
						Component.translatable(t.nameKey())), true);
				return InteractionResult.SUCCESS;
			}
			take(p, stack, cost);
		}
		double ax = pos.getX() + 0.5, ay = pos.getY() + 1.0, az = pos.getZ() + 0.5;
		WorldFx.particleAt("minecraft:soul_fire_flame", ax, ay, az, 0.3, 0.5, 0.3, 0.03, 30);
		WorldFx.particleAt("minecraft:sculk_soul", ax, ay + 0.5, az, 0.5, 0.5, 0.5, 0.05, 20);
		WorldFx.sound(p, "minecraft:block.sculk_catalyst.bloom", 1.0f, 0.8f);
		lift(p, d, t, "gui.extremedimensions.curses.mended");
		return InteractionResult.SUCCESS;
	}

	/** A Mending Cloth, pressed to the skin: one stack of the curse being atoned for (or the heaviest) comes off. */
	public static InteractionResult cloth(ServerPlayer p, ItemStack stack) {
		PlayerData d = DataStore.get(p.getUUID());
		Penalty t = target(d, null);
		if (t == null) {
			WorldFx.soundSelf(p, "minecraft:block.note_block.bass", 0.8f, 0.5f);
			return InteractionResult.SUCCESS;
		}
		if (!p.isCreative()) {
			stack.shrink(1);
		}
		WorldFx.sound(p, "minecraft:block.wool.place", 1.0f, 0.7f);
		lift(p, d, t, "gui.extremedimensions.curses.mended");
		return InteractionResult.SUCCESS;
	}

	/** Fragments the player carries: the held stack and the rest of the inventory. */
	private static int count(ServerPlayer p, ItemStack held) {
		int n = 0;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(ModItems.TETHER_FRAGMENT)) {
				n += inv.getItem(i).getCount();
			}
		}
		return n;
	}

	/** Takes fragments, the held stack first. */
	private static void take(ServerPlayer p, ItemStack held, int n) {
		int fromHeld = Math.min(n, held.getCount());
		held.shrink(fromHeld);
		n -= fromHeld;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(ModItems.TETHER_FRAGMENT)) {
				int k = Math.min(n, s.getCount());
				s.shrink(k);
				n -= k;
			}
		}
	}

	/** One stack comes off a curse, and leaves a scar (up to five). */
	private static void lift(ServerPlayer p, PlayerData d, Penalty t, String messageKey) {
		if (!d.removeStack(t)) {
			return;
		}
		if (d.scars < 5) {
			d.scars++;
			Journal.unlock(p, d, JournalEntry.WOVEN);
		}
		CurseEffects.applyEffects(p, d);
		WorldFx.particles(p, "minecraft:soul", 1.0, 0.4, 0.05, 20);
		WorldFx.particles(p, "extremedimensions:soul_mote", 1.0, 0.8, 0.0, 14);
		WorldFx.sound(p, "minecraft:block.beacon.power_select", 0.7f, 1.4f);
		WorldFx.soundSelf(p, "minecraft:block.amethyst_block.chime", 1.0f, 1.2f);
		int left = d.stacks(t);
		p.sendSystemMessage(Component.translatable(messageKey, Component.translatable(t.nameKey()), left, t.max), true);
		Journal.unlock(p, d, JournalEntry.MENDING);
		Advancements.grant(p, Advancements.MENDED);
		Sync.state(p, d);
		Sync.curses(p, d, false);
		DataStore.markDirty();
	}

	/** The player picked a curse to atone for in the Curses screen. */
	public static void setAtoning(ServerPlayer p, String id) {
		PlayerData d = DataStore.get(p.getUUID());
		Penalty pen = Penalty.byId(id);
		d.atoning = pen == null ? null : pen.id;
		Sync.curses(p, d, false);
		DataStore.markDirty();
	}

	// ------------------------------------------------------------------ per tick

	/** Curse upkeep (see {@link CurseEffects}) and atonement, every tick. Returns true if the HUD must sync now. */
	public static boolean tick(ServerPlayer p, PlayerData d, boolean onExpedition, boolean inOverworld) {
		String dim = Dims.of(p);
		boolean beyond = beyondHere(dim);
		boolean ow = owHere(dim);
		boolean changed = false;
		trackActivity(p, d);
		CurseEffects.tick(p, d, beyond, ow);
		if (d.tickCounter % 20 == 0) {
			if (d.xpForfeit) {
				d.xpForfeit = false;
				if (p.experienceLevel > 0) {
					p.giveExperienceLevels(-p.experienceLevel);
					WorldFx.particles(p, "minecraft:sculk_soul", 1.0, 0.5, 0.05, 30);
					p.sendSystemMessage(Component.translatable("gui.extremedimensions.curses.forfeit"), true);
				}
				changed = true;
			}
			boolean active = d.ticksSinceLook < Config.IDLE_TICKS && d.ticksSinceMove < Config.IDLE_TICKS;
			if (onExpedition && active && ++d.activeSecs >= 60) {
				d.activeSecs -= 60;
				atone(p, d, false, Config.ATONE_PER_MINUTE);
			}
			if (inOverworld) {
				dawn(p, d);
			}
		}
		return changed;
	}

	/** Beyond curses act in the Nether, the End and the Seam. */
	public static boolean beyondHere(String dim) {
		return Dims.isBeyond(dim) || Dims.SEAM.equals(dim);
	}

	/** Overworld curses act in the Overworld, while Overworld curses are switched on. */
	public static boolean owHere(String dim) {
		return Dims.OVERWORLD.equals(dim) && DataStore.overworldPenalties();
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

	/** Dawn in the Overworld: a night survived without dying is atonement. */
	private static void dawn(ServerPlayer p, PlayerData d) {
		int dark = p.level().isDarkOutside() ? 1 : 0;
		if (d.wasDark == 1 && dark == 0) {
			if (!d.diedToday) {
				atone(p, d, true, Config.ATONE_PER_DAY);
			}
			d.diedToday = false;
		}
		d.wasDark = dark;
	}

	/** A player killed something: elites beyond and monsters at night at home are atonement. */
	public static void onKill(ServerPlayer p, boolean elite, boolean hostile) {
		PlayerData d = DataStore.get(p.getUUID());
		String dim = Dims.of(p);
		if (elite && beyondHere(dim)) {
			atone(p, d, false, killPoints(d, Config.ATONE_PER_ELITE));
		} else if (hostile && Dims.OVERWORLD.equals(dim) && p.level().isDarkOutside()) {
			atone(p, d, true, killPoints(d, Config.ATONE_PER_NIGHT_KILL));
		}
	}

	/** Points for a kill; the Keeper's Eye takes a share (a fraction left over is a chance at one more point). */
	public static int killPoints(PlayerData d, int points) {
		if (!com.extremedimensions.relic.RelicService.has(d, com.extremedimensions.relic.Relic.KEEPERS_EYE)) {
			return points;
		}
		double v = points * Math.max(0, Config.RELIC_KEEPERS_EYE_ATONEMENT);
		int whole = (int) v;
		return whole + (ThreadLocalRandom.current().nextDouble() < v - whole ? 1 : 0);
	}

}
