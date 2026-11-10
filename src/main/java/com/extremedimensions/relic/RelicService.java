package com.extremedimensions.relic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.config.Config;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Binding relics to the Tether. Using a relic binds it (the one bound before comes back to the hand); the Curses
 * screen unbinds it again. A bound relic is kept in the player's data, not in the inventory: it cannot be lost to a
 * death, a Lurker or a full bag. Also how relics are found (by tier, see {@link #random(boolean)}), and what the relics
 * do that has no home of its own elsewhere: their blows, hunger, wear and the Last Stitch's hold, the Miner's Candle's
 * light, the Veil Scale's drift, the Hollow Crown's full stomach and the Ferryman's Obol's passage.
 */
public final class RelicService {
	private RelicService() {}

	public static Item item(Relic r) {
		return ModItems.RELICS.get(r);
	}

	/** The relic an item stack is, or null. */
	public static Relic of(ItemStack stack) {
		for (Relic r : Relic.values()) {
			if (stack.is(item(r))) {
				return r;
			}
		}
		return null;
	}

	public static Relic bound(PlayerData d) {
		return Relic.byId(d.relic);
	}

	/** Whether the relic bound to the Tether acts: it sleeps with the curses in another mod's world. */
	public static boolean has(PlayerData d, Relic r) {
		return d != null && !d.away && r.id.equals(d.relic);
	}

	/** For hot paths that also see players without data (never creates it). */
	public static boolean has(ServerPlayer p, Relic r) {
		return has(DataStore.peek(p.getUUID()), r);
	}

	/** Using a relic: it is bound to the Tether, and the one bound before (if any) comes back. */
	public static InteractionResult bind(ServerPlayer p, ItemStack stack) {
		Relic r = of(stack);
		if (r == null) {
			return InteractionResult.PASS;
		}
		PlayerData d = DataStore.get(p.getUUID());
		Relic before = bound(d);
		if (before == r) {
			return InteractionResult.FAIL;
		}
		if (locked(p, d)) {
			return InteractionResult.FAIL;
		}
		stack.shrink(1);
		d.relic = r.id;
		if (before != null) {
			WorldFx.give(p, new ItemStack(item(before)));
		}
		DataStore.markDirty();
		CurseEffects.applyEffects(p, d);
		Sync.curses(p, d, false);
		WorldFx.soundSelf(p, "minecraft:block.chain.place", 1.0f, 0.7f);
		WorldFx.soundSelf(p, "minecraft:block.amethyst_block.resonate", 0.8f, 0.8f);
		WorldFx.particles(p, "extremedimensions:soul_mote", 1.0, 0.5, 0.0, 20);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.relic.bound", Component.translatable(r.nameKey())), true);
		Advancements.grant(p, Advancements.RELIC_BOUND);
		return InteractionResult.SUCCESS;
	}

	/** The relic comes off the Tether and back to the player (or falls at their feet with a full bag). */
	public static void unbind(ServerPlayer p) {
		PlayerData d = DataStore.get(p.getUUID());
		Relic r = bound(d);
		if (r == null || locked(p, d)) {
			return;
		}
		d.relic = null;
		WorldFx.give(p, new ItemStack(item(r)));
		DataStore.markDirty();
		CurseEffects.applyEffects(p, d);
		Sync.curses(p, d, false);
		WorldFx.soundSelf(p, "minecraft:block.chain.break", 1.0f, 0.9f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.relic.unbound", Component.translatable(r.nameKey())), true);
	}

	/**
	 * A relic is bound or taken off only at home and on one's feet: not on an expedition, in the Seam or fallen (its gift
	 * and its price are paid for the whole of it, not swapped for the moment that suits). The player is told so.
	 */
	private static boolean locked(ServerPlayer p, PlayerData d) {
		if (!d.inRun && !d.inSeam && !com.extremedimensions.coop.FallenService.fallen(p)) {
			return false;
		}
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.relic.locked"), true);
		return true;
	}

	/** Drain multiplier from the bound relic in this world (none while it sleeps in another mod's world). */
	public static double drainFactor(PlayerData d, String dim) {
		if (has(d, Relic.EMBER_HEART)) {
			return Dims.NETHER.equals(dim) ? Config.RELIC_EMBER_HEART_NETHER : Dims.END.equals(dim) ? Config.RELIC_EMBER_HEART_END : 1.0;
		}
		if (has(d, Relic.PALE_SHARD)) {
			return Dims.END.equals(dim) ? Config.RELIC_PALE_SHARD_END : Dims.NETHER.equals(dim) ? Config.RELIC_PALE_SHARD_NETHER : 1.0;
		}
		if (has(d, Relic.LODESTONE_CHIP)) {
			// Lodestone Chip: wherever the charge runs beyond the wound (the Seam's detours too)
			return Math.max(0, Math.min(1, Config.RELIC_LODESTONE_CHIP_DRAIN));
		}
		if (has(d, Relic.FERRYMANS_OBOL)) {
			// Ferryman's Obol: the passage is paid for on the way out as well
			return Math.max(1, Config.RELIC_FERRYMANS_OBOL_DRAIN);
		}
		return 1.0;
	}

	// ------------------------------------------------------------------ finding

	/** A random relic, as found (never the Held's Chain: that one lies only where the Held left it). */
	public static ItemStack random() {
		return random(false);
	}

	/**
	 * A random relic: first a tier by its weight ({@code relic_weight_*}, or {@code relic_great_weight_*} from a great foe:
	 * the Elders, the Starved One, a Rift Trial's gold medal), then one of that tier's relics, all as likely. With every
	 * weight of the set 0 (or none left with a relic to find), every relic is as likely.
	 */
	public static ItemStack random(boolean great) {
		return new ItemStack(item(draw(great)));
	}

	/** The relic {@link #random(boolean)} gives. */
	public static Relic draw(boolean great) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		List<Relic> all = new ArrayList<>();
		for (Relic r : Relic.values()) {
			if (r.findable()) {
				all.add(r);
			}
		}
		int total = 0;
		for (Relic.Tier t : Relic.Tier.values()) {
			total += of(t, all).isEmpty() ? 0 : t.weight(great);
		}
		if (total > 0) {
			int roll = rnd.nextInt(total);
			for (Relic.Tier t : Relic.Tier.values()) {
				List<Relic> in = of(t, all);
				int w = in.isEmpty() ? 0 : t.weight(great);
				if (roll < w) {
					return in.get(rnd.nextInt(in.size()));
				}
				roll -= w;
			}
		}
		return all.get(rnd.nextInt(all.size()));
	}

	private static List<Relic> of(Relic.Tier t, List<Relic> among) {
		List<Relic> out = new ArrayList<>();
		for (Relic r : among) {
			if (r.tier == t) {
				out.add(r);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ what the relics do

	/** A whole number from a fraction: the part left over is a chance at one more (so the average is kept). */
	private static int share(double v) {
		int whole = (int) Math.floor(v);
		return whole + (ThreadLocalRandom.current().nextDouble() < v - whole ? 1 : 0);
	}

	/** On an expedition (not yet collapsing) or a visit to the Seam: where the Pilgrim's Sandal and the Last Stitch act. */
	public static boolean wandering(PlayerData d) {
		return d != null && ((d.inRun && !d.collapsing) || d.inSeam);
	}

	/**
	 * Every tick for each player in the mod's worlds: the Pilgrim's Sandal's speed, the Unbound Pinion's wear, the Miner's
	 * Candle's light, the Veil Scale's drift and the Hollow Crown's full stomach.
	 */
	public static void tick(ServerPlayer p, PlayerData d) {
		if (d.relic == null || d.away) {
			d.pinionGlide = 0;
			d.veilDrift = false;
			return;
		}
		if (d.tickCounter % 20 == 0 && Config.RELIC_PILGRIMS_SANDAL_SPEED > 0 && has(d, Relic.PILGRIMS_SANDAL) && wandering(d)) {
			// Laid every second for three seconds: it never runs low while it lasts, and stops soon after
			CurseEffects.effect(p, "speed", 3, Config.RELIC_PILGRIMS_SANDAL_SPEED - 1);
		}
		if (d.tickCounter % 20 == 0 && has(d, Relic.MINERS_CANDLE)) {
			candle(p, deep(p));
		}
		if (has(d, Relic.UNBOUND_PINION) && p.isFallFlying() && !p.isCreative() && !p.isSpectator()) {
			if (++d.pinionGlide % 20 == 0) {
				wear(p);
			}
		} else {
			d.pinionGlide = 0;
		}
		drift(p, d);
		if (has(d, Relic.HOLLOW_CROWN)) {
			sate(p, d);
		}
	}

	// ------------------------------------------------------------------ the Miner's Candle

	/**
	 * Seconds of Night Vision the Miner's Candle lays at a time. It is laid again every second, so it never comes near the
	 * last ten seconds, in which the game makes night vision flicker.
	 */
	private static final int CANDLE_SECONDS = 15;

	/** Where the Miner's Candle lights the way: in the Overworld below {@code relic_miners_candle_depth}, under rock. */
	public static boolean deep(ServerPlayer p) {
		return Dims.OVERWORLD.equals(Dims.of(p)) && p.getY() < Config.RELIC_MINERS_CANDLE_DEPTH
				&& com.extremedimensions.lurker.CaveLurkerService.roofed(p.level(), p.blockPosition().above());
	}

	/**
	 * The Miner's Candle's Night Vision, lit or put out. It is told from a potion's by being ambient and short, and a
	 * potion's is never touched: not shortened while it lasts, not taken away.
	 */
	public static void candle(ServerPlayer p, boolean lit) {
		BuiltInRegistries.MOB_EFFECT.get(Identifier.fromNamespaceAndPath("minecraft", "night_vision")).ifPresent(h -> {
			MobEffectInstance had = p.getEffect(h);
			boolean ours = had != null && had.isAmbient() && had.getDuration() <= CANDLE_SECONDS * 20;
			if (lit && (had == null || ours)) {
				p.addEffect(new MobEffectInstance(h, CANDLE_SECONDS * 20, 0, true, false, false));
			} else if (!lit && ours) {
				p.removeEffect(h);
			}
		});
	}

	// ------------------------------------------------------------------ the Veil Scale

	/** Seconds of Slow Falling the Veil Scale lays at a time, again every half second while its bearer drifts. */
	private static final int DRIFT_SECONDS = 2;

	/**
	 * The Veil Scale: in the Seam (and its Fray), once its bearer has fallen more than {@code relic_veil_scale_fall} blocks,
	 * they drift down slowly until they land, swim or glide. Slow Falling takes the fall it counts away at once, so the
	 * drift is held on for the rest of the way down rather than caught again every few blocks.
	 */
	public static void drift(ServerPlayer p, PlayerData d) {
		if (!has(d, Relic.VEIL_SCALE) || !(d.inSeam || com.extremedimensions.seam.SeamService.inSeam(p)) || p.onGround() || p.isInWater()
				|| p.isFallFlying() || p.isSpectator()) {
			d.veilDrift = false;
			return;
		}
		if (!d.veilDrift && p.fallDistance <= Math.max(0, Config.RELIC_VEIL_SCALE_FALL)) {
			return;
		}
		boolean caught = !d.veilDrift;
		d.veilDrift = true;
		if (caught || d.tickCounter % 10 == 0) {
			CurseEffects.effect(p, "slow_falling", DRIFT_SECONDS, 0);
		}
		if (caught) {
			WorldFx.particles(p, "minecraft:end_rod", 0.5, 0.6, 0.03, 10);
		}
	}

	/** Seconds in the Seam, from the relic bound: the Veil Scale takes {@code relic_veil_scale_seconds}, never below a minute. */
	public static double seamTime(PlayerData d, double seconds) {
		if (!has(d, Relic.VEIL_SCALE) || Config.RELIC_VEIL_SCALE_SECONDS <= 0) {
			return seconds;
		}
		return Math.max(Math.min(seconds, 60), seconds - Config.RELIC_VEIL_SCALE_SECONDS);
	}

	// ------------------------------------------------------------------ the Hollow Crown

	/** The Hollow Crown: its bearer's stomach is kept full (as full as a Shrunken Stomach lets it be), and so is their saturation. */
	private static void sate(ServerPlayer p, PlayerData d) {
		if (p.isCreative() || p.isSpectator()) {
			return;
		}
		int full = CurseEffects.stomachCap(d);
		FoodData food = p.getFoodData();
		if (food.getFoodLevel() < full) {
			food.setFoodLevel(full);
		}
		if (food.getSaturationLevel() < full) {
			food.setSaturation(full);
		}
		// (what the stomach holds now is no meal: Bitter Tongue takes nothing of it)
		d.lastFood = food.getFoodLevel();
	}

	/**
	 * Natural healing (a full stomach's) under the relic bound: true to let it heal as ever. The Hollow Crown's bearer is
	 * healed only {@code relic_hollow_crown_regen} of it (given here) and the rest is refused. Potions, golden apples, a
	 * beacon, a totem and the Last Stitch heal as they always do: they never come this way.
	 */
	public static boolean regenerates(ServerPlayer p, float amount) {
		if (p.isCreative() || !has(p, Relic.HOLLOW_CROWN)) {
			return true;
		}
		double share = Math.max(0, Math.min(1, Config.RELIC_HOLLOW_CROWN_REGEN));
		if (share >= 1) {
			return true;
		}
		if (share > 0 && amount > 0) {
			p.heal((float) (amount * share));
		}
		return false;
	}

	/**
	 * The Unbound Pinion: an elytra glided on wears {@code relic_unbound_pinion_wear} times as fast. Vanilla takes one point
	 * a second; this takes the rest, through the game's own wear (so Unbreaking spares some of it as it does there), and
	 * never past the last point: worn to it, the elytra stops gliding as a worn-out one always does, and does not break.
	 * Returns the wear asked for.
	 */
	public static int wear(ServerPlayer p) {
		ItemStack wings = p.getItemBySlot(EquipmentSlot.CHEST);
		if (!wings.is(Items.ELYTRA) || !wings.isDamageableItem()) {
			return 0;
		}
		int room = wings.getMaxDamage() - 1 - wings.getDamageValue();
		int extra = Math.min(room, Math.max(1, Config.RELIC_UNBOUND_PINION_WEAR) - 1);
		if (extra > 0) {
			wings.hurtAndBreak(extra, p, EquipmentSlot.CHEST);
		}
		return Math.max(0, extra);
	}

	/**
	 * How much of the hunger a player is caused they take, from the relic bound: more with the Pilgrim's Sandal on an
	 * expedition and in the Seam, more with Hunger's Fang always, none with the Hollow Crown. It scales the player's own exhaustion (see
	 * Charms.exhaustion), so "a quarter faster" holds whether they walk, sprint, fight or swim, rather than a fixed amount
	 * a second that would be far too much for one standing still and too little for one sprinting.
	 */
	public static double hunger(ServerPlayer p) {
		PlayerData d = DataStore.peek(p.getUUID());
		if (has(d, Relic.HOLLOW_CROWN)) {
			// the Hollow Crown's bearer never hungers
			return 0.0;
		}
		if (has(d, Relic.PILGRIMS_SANDAL) && wandering(d)) {
			return Math.max(1, Config.RELIC_PILGRIMS_SANDAL_HUNGER);
		}
		if (has(d, Relic.HUNGERS_FANG)) {
			return Math.max(1, Config.RELIC_HUNGERS_FANG_HUNGER);
		}
		return 1.0;
	}

	/**
	 * Damage taken, as a multiple, from the relic bound: more for everything with the Unbound Pinion; with the Ashen Locket
	 * less from fire and lava, more from freezing and drowning.
	 */
	public static double damageTaken(PlayerData d, DamageSource source) {
		if (has(d, Relic.UNBOUND_PINION)) {
			return Math.max(1, Config.RELIC_UNBOUND_PINION_DAMAGE);
		}
		if (has(d, Relic.ASHEN_LOCKET)) {
			if (source.is(DamageTypeTags.IS_FIRE)) {
				return Math.max(0, Math.min(1, Config.RELIC_ASHEN_LOCKET_FIRE));
			}
			if (source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypeTags.IS_DROWNING)) {
				return Math.max(1, Config.RELIC_ASHEN_LOCKET_COLD);
			}
		}
		return 1.0;
	}

	/** A blow struck up close by hand, claw or blade: a melee attack, not an arrow, a spell, a blast or a thorn. */
	public static boolean melee(DamageSource source) {
		return source.getEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity()
				&& (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO))
				&& !source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.IS_EXPLOSION);
	}

	/** A player's own melee blow. */
	private static ServerPlayer striker(DamageSource source) {
		return source.getEntity() instanceof ServerPlayer p && source.is(DamageTypes.PLAYER_ATTACK) && melee(source) ? p : null;
	}

	/** The damage a hit is about to deal, from the striker's relic: the Thornward Bark's bearer hits softer in melee. */
	public static float dealt(DamageSource source, float amount) {
		ServerPlayer s = amount > 0 ? striker(source) : null;
		if (s != null && has(s, Relic.THORNWARD_BARK)) {
			return (float) (amount * Math.max(0, Math.min(1, Config.RELIC_THORNWARD_BARK_MELEE)));
		}
		return amount;
	}

	/** Set while the Thornward Bark hurts someone back, so two bearers striking each other never answer forever. */
	private static boolean answering;
	/** Blocks past the striker's own edge a blow by hand comes from (an Elder's slam lands much further off than that). */
	private static final double HAND_REACH = 4;

	/** After a hit lands: the Thornward Bark hurts whoever struck its bearer up close, and Hunger's Fang feeds on the blow. */
	public static void afterDamage(LivingEntity entity, DamageSource source, float baseTaken, float taken, boolean blocked) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (entity instanceof ServerPlayer p && !blocked && baseTaken > 0 && !answering && Config.RELIC_THORNWARD_BARK_THORNS > 0
				&& melee(source) && source.getEntity() instanceof LivingEntity attacker && attacker != p && attacker.isAlive()
				&& !p.isSpectator() && has(p, Relic.THORNWARD_BARK)) {
			// Only from up close: an Elder's slam that lands far from it is no blow by hand
			double reach = HAND_REACH + attacker.getBbWidth() / 2;
			if (attacker.distanceToSqr(p) <= reach * reach) {
				answering = true;
				try {
					attacker.hurtServer(level, p.damageSources().thorns(p), (float) Config.RELIC_THORNWARD_BARK_THORNS);
				} finally {
					answering = false;
				}
				WorldFx.sound(p, "minecraft:enchant.thorns.hit", 0.8f, 0.8f);
			}
		}
		if (entity == prey) {
			prey = null;
		}
		feed(taken > 0 ? striker(source) : null, entity, taken);
	}

	/** Hunger's Fang: its bearer's blow by hand heals them by a share of the damage it dealt. */
	private static void feed(ServerPlayer s, LivingEntity struck, float dealt) {
		if (s == null || s == struck || !s.isAlive() || Config.RELIC_HUNGERS_FANG_LEECH <= 0 || !has(s, Relic.HUNGERS_FANG)) {
			return;
		}
		float heal = (float) (dealt * Math.min(1, Config.RELIC_HUNGERS_FANG_LEECH));
		if (heal > 0 && s.getHealth() < s.getMaxHealth()) {
			s.heal(heal);
			WorldFx.particleIn(s.level(), "minecraft:damage_indicator", s.getX(), s.getY() + 1.2, s.getZ(), 0.3, 0.3, 0.3, 0.02, 2);
		}
	}

	/**
	 * The creature Hunger's Fang's bearer is striking now, and the health it had: a killing blow takes the rest of it, and
	 * the game says nothing of a blow that kills after it lands (see {@link #slain}).
	 */
	private static LivingEntity prey;
	private static float preyHealth;

	/** Before a hit lands (never refuses it): Hunger's Fang notes what its bearer's blow could take at most. */
	public static boolean striking(LivingEntity entity, DamageSource source, float amount) {
		ServerPlayer s = striker(source);
		if (s != null && s != entity && has(s, Relic.HUNGERS_FANG)) {
			prey = entity;
			preyHealth = entity.getHealth();
		}
		return true;
	}

	/** A creature died: if Hunger's Fang's bearer slew it by hand, the killing blow feeds them on the health it took. */
	public static void slain(LivingEntity entity, DamageSource source) {
		if (entity == prey) {
			prey = null;
			feed(striker(source), entity, preyHealth);
		}
	}

	/**
	 * The death check: the Last Stitch holds a killing blow once an expedition (or a Seam visit) and leaves its bearer at
	 * {@code relic_last_stitch_health}, with a moment of Resistance. Not against what kills outright (the void, a Collapse,
	 * /kill), nor for one fallen, nor while a totem would save them anyway. Returns false when it holds.
	 */
	public static boolean stitch(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer p) || p.isCreative() || p.isSpectator()) {
			return true;
		}
		PlayerData d = DataStore.peek(p.getUUID());
		if (!has(d, Relic.LAST_STITCH) || d.stitchSpent || Config.RELIC_LAST_STITCH_HEALTH <= 0 || !wandering(d)
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || com.extremedimensions.coop.FallenService.fallen(p) || totem(p)) {
			return true;
		}
		d.stitchSpent = true;
		DataStore.markDirty();
		p.setHealth((float) Math.max(1, Math.min(p.getMaxHealth(), Config.RELIC_LAST_STITCH_HEALTH)));
		CurseEffects.effect(p, "resistance", 3, 1);
		WorldFx.particles(p, "minecraft:totem_of_undying", 1.0, 0.5, 0.4, 40);
		WorldFx.particles(p, "minecraft:end_rod", 1.0, 0.6, 0.05, 16);
		WorldFx.sound(p, "minecraft:item.totem.use", 0.6f, 1.4f);
		WorldFx.soundSelf(p, "minecraft:block.wool.break", 1.0f, 0.6f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.relic.last_stitch.held").withStyle(ChatFormatting.GOLD), true);
		return false;
	}

	private static boolean totem(ServerPlayer p) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (p.getItemInHand(hand).is(Items.TOTEM_OF_UNDYING)) {
				return true;
			}
		}
		return false;
	}

	/** Atonement earned (not spent), from the relic bound: the Debtor's Seal multiplies it. */
	public static int atonement(PlayerData d, int points) {
		if (points <= 0 || !has(d, Relic.DEBTORS_SEAL)) {
			return points;
		}
		return share(points * Math.max(1, Config.RELIC_DEBTORS_SEAL_ATONEMENT));
	}

	/**
	 * How many times a curse comes, from the relic bound: with the Debtor's Seal, each curse of a death (or a Collapse) and
	 * each a Lurker lays comes {@code relic_debtors_seal_curses} times, each an Elder's ultimate lays (and each
	 * of a death in a boss fight) {@code relic_debtors_seal_boss_curses} times. Once otherwise.
	 */
	public static int curses(PlayerData d, boolean elder) {
		if (!has(d, Relic.DEBTORS_SEAL)) {
			return 1;
		}
		return Math.max(1, elder ? Config.RELIC_DEBTORS_SEAL_BOSS_CURSES : Config.RELIC_DEBTORS_SEAL_CURSES);
	}

	/** A boss fight is under way around this player: an Elder or the Starved One alive within 64 blocks. */
	public static boolean bossNear(ServerPlayer p) {
		return !p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(64),
				e -> e.isAlive() && (e instanceof com.extremedimensions.elder.ElderBoss || e instanceof com.extremedimensions.mending.StarvedOne))
				.isEmpty();
	}

	/** The young's prices, as a multiple, from the relic bound: less with the Hollow Whistle. */
	public static double barter(ServerPlayer p) {
		return has(p, Relic.HOLLOW_WHISTLE) ? Math.max(0, Math.min(1, Config.RELIC_HOLLOW_WHISTLE_PRICE)) : 1.0;
	}

	/** Standing a player wins with the tribe, from the relic bound: the Hollow Whistle's bearer wins only a share of it. */
	public static int standing(ServerPlayer p, int gain) {
		if (gain <= 0 || !has(p, Relic.HOLLOW_WHISTLE)) {
			return gain;
		}
		return share(gain * Math.max(0, Math.min(1, Config.RELIC_HOLLOW_WHISTLE_STANDING)));
	}

	/** Whether the Elders may pull this flyer down and bind their wings (never the Unbound Pinion's bearer). */
	public static boolean grounds(ServerPlayer p) {
		return !has(p, Relic.UNBOUND_PINION);
	}

	// ------------------------------------------------------------------ who comes, and how often

	/** How many times as often a Cave Lurker comes for this player: more for the Miner's Candle's bearer. */
	public static double caveLurkers(ServerPlayer p) {
		return has(p, Relic.MINERS_CANDLE) ? Math.max(1, Config.RELIC_MINERS_CANDLE_CAVE_LURKER) : 1.0;
	}

	/** How many times as often a Magpie comes for this player's chests: more for the Magpie's Glint's bearer. */
	public static double magpies(ServerPlayer p) {
		return has(p, Relic.MAGPIES_GLINT) ? Math.max(1, Config.RELIC_MAGPIES_GLINT_MAGPIE) : 1.0;
	}

	/** How many times as often a Sculk Lurker comes for this player: more for the Muffled Bell's bearer. */
	public static double sculkLurkers(ServerPlayer p) {
		return has(p, Relic.MUFFLED_BELL) ? Math.max(1, Config.RELIC_MUFFLED_BELL_SCULK_LURKER) : 1.0;
	}

	// ------------------------------------------------------------------ the Muffled Bell

	/** Set while a Sculk Lurker sets the sculk off in a player's name: what it does in theirs, the sculk hears. */
	private static boolean framing;

	/** Runs something a Sculk Lurker does in its player's name: the Muffled Bell does not hush it. */
	public static void inTheirName(Runnable r) {
		boolean was = framing;
		framing = true;
		try {
			r.run();
		} finally {
			framing = was;
		}
	}

	/** Whether this player's steps are no sound to a Cave Lurker, as if they always sneaked: the Muffled Bell's bearer. */
	public static boolean hushed(ServerPlayer p) {
		return has(p, Relic.MUFFLED_BELL);
	}

	/**
	 * The Muffled Bell: the sculk does not hear its bearer. Their vibrations are dampened as wool dampens them, so no sculk
	 * sensor, no shrieker and no Warden heeds what they do (a shrieker stepped on still shrieks, as it does at anyone).
	 */
	public static boolean muffled(Entity e) {
		return !framing && e instanceof ServerPlayer p && hushed(p);
	}

	// ------------------------------------------------------------------ the Magpie's Glint

	/** Great foes whose loot is never doubled (the mod's own Elders and the Starved One are left out by their kind). */
	private static final java.util.Set<String> BOSSES = java.util.Set.of("minecraft:ender_dragon", "minecraft:wither", "minecraft:elder_guardian",
			"minecraft:warden");

	/**
	 * The Magpie's Glint: whether a creature its bearer slew drops its loot again, at {@code relic_magpies_glint_chance}.
	 * Only a wild one (as atonement counts it, see AtonementGuard: not from a spawner, an egg, breeding or a command, nor
	 * near a spawner), never a player, a boss or one of the tribe's.
	 */
	public static boolean lootTwice(LivingEntity entity, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer killer) || entity instanceof Player || !(entity instanceof Mob)
				|| entity instanceof com.extremedimensions.lurker.LurkerBody || entity instanceof com.extremedimensions.elder.ElderBoss
				|| entity instanceof com.extremedimensions.mending.StarvedOne || Config.RELIC_MAGPIES_GLINT_CHANCE <= 0
				|| !has(killer, Relic.MAGPIES_GLINT) || BOSSES.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())
				|| !com.extremedimensions.penalty.AtonementGuard.natural(entity)) {
			return false;
		}
		return ThreadLocalRandom.current().nextDouble() < Config.RELIC_MAGPIES_GLINT_CHANCE;
	}

	// ------------------------------------------------------------------ the Anchor Rivet

	/** The charge fuel gives, as a multiple, from the relic bound: more with the Anchor Rivet. */
	public static double fuel(PlayerData d) {
		return has(d, Relic.ANCHOR_RIVET) ? Math.max(1, Config.RELIC_ANCHOR_RIVET_FUEL) : 1.0;
	}

	/**
	 * How many times as fast a Lurker drinks an anchor near the player it came for: faster from one that holds charge of
	 * the Anchor Rivet's bearer.
	 */
	public static double drink(ServerPlayer p, Anchor a) {
		return p != null && a != null && a.own(p.getStringUUID()) > 0 && has(p, Relic.ANCHOR_RIVET) ? Math.max(1, Config.RELIC_ANCHOR_RIVET_DRINK) : 1.0;
	}

	// ------------------------------------------------------------------ the Ferryman's Obol

	/** Beyond the wound, for the Ferryman's Obol: the Nether, the End, and the Seam with its Fray. */
	private static boolean beyond(String dim) {
		return Dims.isBeyond(dim) || Dims.SEAM.equals(dim);
	}

	/** The Ferryman's Obol: whether a death in this world keeps all its bearer carries (their experience still falls). */
	public static boolean keepsOnDeath(PlayerData d, String dim) {
		return has(d, Relic.FERRYMANS_OBOL) && beyond(dim);
	}

	/** Curses a death beyond the wound costs on top, from the relic bound: the Ferryman's Obol's. */
	public static int passage(PlayerData d) {
		return has(d, Relic.FERRYMANS_OBOL) ? Math.max(0, Config.RELIC_FERRYMANS_OBOL_CURSES) : 0;
	}

	/**
	 * As a player dies, just before what they carry falls (see FerrymansObolMixin): true when the Ferryman's Obol keeps it
	 * all, the bag, the armour and the off hand, as the game's keepInventory would. It is noted in their data, so it comes
	 * back on their respawn even when they leave the game first (the dead body is saved with all it carried).
	 */
	public static boolean spareDrops(ServerPlayer p) {
		return spareDrops(p, Dims.of(p));
	}

	/** As {@link #spareDrops(ServerPlayer)}, for a player dying in the given world. */
	public static boolean spareDrops(ServerPlayer p, String dim) {
		PlayerData d = DataStore.peek(p.getUUID());
		if (p.isSpectator() || !keepsOnDeath(d, dim)) {
			return false;
		}
		d.obolKept = true;
		DataStore.markDirty();
		return true;
	}

	/**
	 * On respawn after a death the Ferryman's Obol paid for: everything the dead body carried goes to the new one, each in
	 * its own slot. Returns the stacks given back (none for a death that dropped as ever).
	 */
	public static int restore(ServerPlayer from, ServerPlayer to) {
		PlayerData d = DataStore.peek(from.getUUID());
		if (d == null || !d.obolKept) {
			return 0;
		}
		d.obolKept = false;
		DataStore.markDirty();
		Inventory was = from.getInventory(), now = to.getInventory();
		int n = 0;
		for (int i = 0; i < Math.min(was.getContainerSize(), now.getContainerSize()); i++) {
			ItemStack s = was.getItem(i);
			if (!s.isEmpty()) {
				// moved, not copied: the old body keeps nothing that could come back a second time
				was.setItem(i, ItemStack.EMPTY);
				now.setItem(i, s);
				n++;
			}
		}
		if (n > 0) {
			WorldFx.soundSelf(to, "minecraft:item.armor.equip_chain", 1.0f, 0.7f);
			to.sendSystemMessage(Component.translatable("gui.extremedimensions.relic.ferrymans_obol.kept").withStyle(ChatFormatting.AQUA));
		}
		return n;
	}
}
