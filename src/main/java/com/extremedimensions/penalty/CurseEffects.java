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
 * What the curses do where they act: attribute curses as modifiers, the per-tick curses (skims, hunger, the stomach,
 * breath, cold, pockets, spells), and the hooks for damage taken, effects received and hits landed. Owing, choosing
 * and lifting curses live in {@link PenaltyService}.
 */
public final class CurseEffects {
	private CurseEffects() {}

	/** What the curses do every tick: hunger, skims, the stomach, breath, cold, sealed and heavy pockets, and spells. */
	static void tick(ServerPlayer p, PlayerData d, boolean beyond, boolean ow) {
		hunger(p, d, beyond, ow);
		skimXp(p, d, beyond, ow);
		skimFood(p, d, ow);
		shrunkenStomach(p, d);
		shortBreath(p, d);
		frostbitten(p, d);
		if (d.tickCounter % 10 == 0) {
			sealPockets(p, d);
		}
		if (d.tickCounter % 20 == 0) {
			leadPockets(p, d);
			vertigo(p, d, beyond);
			huntedNights(p, d, ow);
			restlessSleep(p, d, ow);
		}
		if (ExtremeCurses.any(d)) {
			ExtremeCurses.tick(p, d);
		}
	}

	/** Toll and Tithe: a share of every bit of experience earned goes to the Tether. */
	private static void skimXp(ServerPlayer p, PlayerData d, boolean beyond, boolean ow) {
		int now = p.totalExperience;
		if (d.lastXp >= 0 && now > d.lastXp) {
			double share = Math.min(0.9, Penalty.TOLL.frac() * d.active(Penalty.TOLL, beyond, ow) + Penalty.TITHE.frac() * d.active(Penalty.TITHE, beyond, ow));
			if (share > 0) {
				d.xpCarry += (now - d.lastXp) * share;
				int take = (int) d.xpCarry;
				if (take > 0) {
					d.xpCarry -= take;
					p.giveExperiencePoints(-take);
				}
			}
		}
		d.lastXp = p.totalExperience;
	}

	/** Bitter Tongue: food fills less. */
	private static void skimFood(ServerPlayer p, PlayerData d, boolean ow) {
		int food = p.getFoodData().getFoodLevel();
		int stacks = ow ? d.stacks(Penalty.BITTER_TONGUE) : 0;
		if (d.lastFood >= 0 && food > d.lastFood && stacks > 0) {
			d.foodCarry += (food - d.lastFood) * Math.min(0.9, Penalty.BITTER_TONGUE.frac() * stacks);
			int take = (int) d.foodCarry;
			if (take > 0) {
				d.foodCarry -= take;
				food = Math.max(0, food - take);
				p.getFoodData().setFoodLevel(food);
			}
		}
		d.lastFood = food;
	}

	/** Vertigo: now and then the world swims, more often with every stack. */
	private static void vertigo(ServerPlayer p, PlayerData d, boolean beyond) {
		int stacks = beyond ? d.stacks(Penalty.VERTIGO) : 0;
		if (stacks <= 0 || --d.vertigoIn > 0) {
			return;
		}
		d.vertigoIn = (int) Math.max(10, Penalty.VERTIGO.value / stacks * (0.7 + 0.6 * ThreadLocalRandom.current().nextDouble()));
		effect(p, "nausea", 6, 0);
	}

	/** Hunted Nights: something climbs out of the dark near the player, more often with every stack. */
	private static void huntedNights(ServerPlayer p, PlayerData d, boolean ow) {
		int stacks = ow ? d.stacks(Penalty.HUNTED_NIGHTS) : 0;
		if (stacks <= 0 || !p.level().isDarkOutside() || p.isCreative() || p.isSpectator() || --d.huntIn > 0) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		d.huntIn = (int) Math.max(20, Penalty.HUNTED_NIGHTS.value / stacks * (0.7 + 0.6 * rnd.nextDouble()));
		ServerLevel level = (ServerLevel) p.level();
		double a = rnd.nextDouble() * Math.PI * 2, r = 14 + rnd.nextDouble() * 8;
		int x = (int) Math.floor(p.getX() + Math.cos(a) * r), z = (int) Math.floor(p.getZ() + Math.sin(a) * r);
		if (!level.isLoaded(new BlockPos(x, (int) p.getY(), z))) {
			return;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		String[] risen = {"minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:husk", "minecraft:stray"};
		WorldFx.particleAt("minecraft:soul", x + 0.5, y + 0.5, z + 0.5, 0.4, 0.4, 0.4, 0.02, 20);
		WorldFx.particleAt("minecraft:large_smoke", x + 0.5, y + 0.3, z + 0.5, 0.3, 0.2, 0.3, 0.01, 10);
		AtonementGuard.markWild(WorldFx.summon(level, risen[rnd.nextInt(risen.length)], x, y, z));
		WorldFx.soundIn("minecraft:overworld", "minecraft:block.rooted_dirt.break", "hostile", x + 0.5, y, z + 0.5, 1.2f, 0.5f);
	}

	/** Restless Sleep: a nightmare may wake the player before the night passes. */
	private static void restlessSleep(ServerPlayer p, PlayerData d, boolean ow) {
		if (!p.isSleeping()) {
			d.sleepRolled = false;
			return;
		}
		if (!d.sleepRolled) {
			d.sleepRolled = true;
			int stacks = ow ? d.stacks(Penalty.RESTLESS_SLEEP) : 0;
			d.sleepFails = ThreadLocalRandom.current().nextDouble() < Math.min(0.95, Penalty.RESTLESS_SLEEP.frac() * stacks);
		}
		if (d.sleepFails && p.getSleepTimer() >= 40) {
			d.sleepFails = false;
			p.stopSleepInBed(true, true);
			WorldFx.soundSelf(p, "extremedimensions:lurker_whisper", 1.0f, 0.7f);
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.curses.nightmare"), true);
		}
	}

	/** Worn Edges and Rusted Tools: tools and weapons wear faster where the curse acts. */
	public static void wear(ServerPlayer p) {
		if (p.isCreative()) {
			return;
		}
		PlayerData d = DataStore.get(p.getUUID());
		String dim = Dims.of(p);
		int extra = d.active(Penalty.WORN_EDGES, PenaltyService.beyondHere(dim), PenaltyService.owHere(dim)) * (int) Math.round(Penalty.WORN_EDGES.value)
				+ d.active(Penalty.RUSTED_TOOLS, PenaltyService.beyondHere(dim), PenaltyService.owHere(dim)) * (int) Math.round(Penalty.RUSTED_TOOLS.value);
		ItemStack held = p.getMainHandItem();
		if (extra > 0 && held.isDamageableItem()) {
			held.hurtAndBreak(extra, p, EquipmentSlot.MAINHAND);
		}
	}

	// ------------------------------------------------------------------ effects

	/** Sets every attribute curse to match its stacks, in the world the player is in, and refreshes the effects. */
	public static void applyEffects(ServerPlayer p, PlayerData d) {
		String dim = Dims.of(p);
		boolean b = PenaltyService.beyondHere(dim), o = PenaltyService.owHere(dim);
		double scarHp = Math.min(16.0, 2 * Penalty.SCAR.value * d.active(Penalty.SCAR, b, o));
		double hollowHp = Math.min(16.0, 2 * Penalty.HOLLOW_HEART.value * d.active(Penalty.HOLLOW_HEART, b, o));
		modifier(p, "minecraft:max_health", "scar", -scarHp, "add_value");
		modifier(p, "minecraft:max_health", "hollow_heart", -hollowHp, "add_value");
		modifier(p, "minecraft:armor", "cracked_armor", -Penalty.CRACKED_ARMOR.value * d.active(Penalty.CRACKED_ARMOR, b, o), "add_value");
		modifier(p, "minecraft:armor_toughness", "thin_skin", -Penalty.THIN_SKIN.value * d.active(Penalty.THIN_SKIN, b, o), "add_value");
		modifier(p, "minecraft:movement_speed", "heavy_legs", -compound(Penalty.HEAVY_LEGS, d.active(Penalty.HEAVY_LEGS, b, o)), "add_multiplied_total");
		modifier(p, "minecraft:movement_speed", "leaden_steps", -compound(Penalty.LEADEN_STEPS, d.active(Penalty.LEADEN_STEPS, b, o)), "add_multiplied_total");
		int shadowed = d.active(Penalty.SHADOWED, b, o);
		modifier(p, "minecraft:movement_speed", "shadowed", shadowed >= 2 ? -compound(Penalty.SHADOWED, shadowed - 1) : 0, "add_multiplied_total");
		modifier(p, "minecraft:block_break_speed", "clumsy_hands", -compound(Penalty.CLUMSY_HANDS, d.active(Penalty.CLUMSY_HANDS, b, o)), "add_multiplied_total");
		modifier(p, "minecraft:block_break_speed", "dull_pick", -compound(Penalty.DULL_PICK, d.active(Penalty.DULL_PICK, b, o)), "add_multiplied_total");
		modifier(p, "minecraft:attack_damage", "frailty", -Penalty.FRAILTY.value * d.active(Penalty.FRAILTY, b, o), "add_value");
		modifier(p, "minecraft:attack_damage", "weak_arm", -Penalty.WEAK_ARM.value * d.active(Penalty.WEAK_ARM, b, o), "add_value");
		modifier(p, "minecraft:attack_speed", "slow_hands", -compound(Penalty.SLOW_HANDS, d.active(Penalty.SLOW_HANDS, b, o)), "add_multiplied_total");
		modifier(p, "minecraft:luck", "cursed_coin", -Penalty.CURSED_COIN.value * d.active(Penalty.CURSED_COIN, b, o), "add_value");
		modifier(p, "minecraft:luck", "ill_luck", -Penalty.ILL_LUCK.value * d.active(Penalty.ILL_LUCK, b, o), "add_value");
		modifier(p, "minecraft:fall_damage_multiplier", "brittle_bones", Penalty.BRITTLE_BONES.frac() * d.active(Penalty.BRITTLE_BONES, b, o), "add_value");
		modifier(p, "minecraft:fall_damage_multiplier", "old_bones", Penalty.OLD_BONES.frac() * d.active(Penalty.OLD_BONES, b, o), "add_value");
		modifier(p, "minecraft:burning_time", "burning_blood", Penalty.BURNING_BLOOD.frac() * d.active(Penalty.BURNING_BLOOD, b, o), "add_value");
		double reach = -Penalty.SHORT_REACH.value * d.active(Penalty.SHORT_REACH, b, o);
		modifier(p, "minecraft:block_interaction_range", "short_reach", reach, "add_value");
		modifier(p, "minecraft:entity_interaction_range", "short_reach", reach, "add_value");
		modifier(p, "minecraft:jump_strength", "weak_knees", -compound(Penalty.WEAK_KNEES, d.active(Penalty.WEAK_KNEES, b, o)), "add_multiplied_total");
		modifier(p, "minecraft:gravity", "heavy_soul", Penalty.HEAVY_SOUL.frac() * d.active(Penalty.HEAVY_SOUL, b, o), "add_multiplied_base");
		// Everywhere
		modifier(p, "minecraft:max_health", "withering", -Math.min(8.0, 2 * Penalty.WITHERING.value * d.stacks(Penalty.WITHERING)), "add_value");
		modifier(p, "minecraft:jump_strength", "grounded", -compound(Penalty.GROUNDED, d.stacks(Penalty.GROUNDED)), "add_multiplied_total");
		modifier(p, "minecraft:safe_fall_distance", "fragile_footing", -Penalty.FRAGILE_FOOTING.value * d.stacks(Penalty.FRAGILE_FOOTING), "add_value");
		// Extreme
		modifier(p, "minecraft:block_interaction_range", "withered_grasp", -Penalty.WITHERED_GRASP.value * d.stacks(Penalty.WITHERED_GRASP), "add_value");
		d.leadValue = Double.NaN;
		refreshEffects(p, d);
	}

	/** Total reduction of {@code stacks} multiplicative reductions of a percentage curse. */
	private static double compound(Penalty pen, int stacks) {
		return stacks <= 0 ? 0 : 1 - Math.pow(Math.max(0, 1 - pen.frac()), stacks);
	}

	private static void modifier(ServerPlayer p, String attribute, String name, double value, String operation) {
		BuiltInRegistries.ATTRIBUTE.get(WorldFx.id(attribute)).ifPresent(holder -> {
			AttributeInstance instance = p.getAttribute(holder);
			if (instance == null) {
				return;
			}
			Identifier id = Identifier.fromNamespaceAndPath("extremedimensions", name);
			instance.removeModifier(id);
			if (value != 0) {
				instance.addPermanentModifier(new AttributeModifier(id, value, AttributeModifier.Operation.valueOf(operation.toUpperCase(Locale.ROOT))));
			}
		});
	}

	/** A vanilla effect without icon or particles: curses are shown in the Curses screen instead. */
	public static void effect(ServerPlayer p, String id, int secs, int amp) {
		BuiltInRegistries.MOB_EFFECT.get(Identifier.fromNamespaceAndPath("minecraft", id)).ifPresent(h ->
				p.addEffect(new MobEffectInstance(h, secs * 20, Math.max(0, amp), true, false, false)));
	}

	/** Darkness (Shadowed, Deep Dread, Dusk Blindness): re-applied briefly so it stops by itself. */
	public static void refreshEffects(ServerPlayer p, PlayerData d) {
		String dim = Dims.of(p);
		boolean b = PenaltyService.beyondHere(dim), o = PenaltyService.owHere(dim);
		boolean dark = d.active(Penalty.SHADOWED, b, o) > 0
				|| (d.active(Penalty.DEEP_DREAD, b, o) > 0 && p.getY() < 62)
				|| (d.active(Penalty.DUSK_BLINDNESS, b, o) > 0 && p.level().isDarkOutside() && p.level().canSeeSky(p.blockPosition().above()));
		if (dark) {
			effect(p, "darkness", 8, 0);
		}
	}

	// ------------------------------------------------------------------ everywhere

	/** Sealed Pockets: how many slots of the main inventory are sealed (from the last slot backwards; the hotbar never). */
	public static int sealedSlots(PlayerData d) {
		return Math.min(27, d.stacks(Penalty.SEALED_POCKETS) * (int) Math.round(Penalty.SEALED_POCKETS.value));
	}

	/** Sealed Pockets: whatever lies in a sealed slot is moved to a free slot, or falls out. */
	private static void sealPockets(ServerPlayer p, PlayerData d) {
		int n = sealedSlots(d);
		if (n <= 0 || p.isCreative() || p.isSpectator()) {
			return;
		}
		var inv = p.getInventory();
		for (int i = 35; i > 35 - n; i--) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) {
				continue;
			}
			inv.setItem(i, ItemStack.EMPTY);
			int free = -1;
			for (int j = 0; j <= 35 - n && free < 0; j++) {
				if (inv.getItem(j).isEmpty()) {
					free = j;
				}
			}
			if (free >= 0) {
				inv.setItem(free, s);
			} else {
				throwAway(p, s, 20);
			}
		}
	}

	/** Shrunken Stomach: the most food the player can hold. */
	public static int stomachCap(PlayerData d) {
		return Math.max(2, 20 - 2 * (int) Math.round(Penalty.SHRUNKEN_STOMACH.value) * d.stacks(Penalty.SHRUNKEN_STOMACH));
	}

	/**
	 * Shrunken Stomach: food (and saturation) never rise above the cap. Below the 18 food natural healing needs, a full
	 * shrunken stomach still heals slowly, at the vanilla pace and cost, so the curse never stops healing outright.
	 */
	private static void shrunkenStomach(ServerPlayer p, PlayerData d) {
		if (d.stacks(Penalty.SHRUNKEN_STOMACH) <= 0 || p.isCreative() || p.isSpectator()) {
			return;
		}
		int cap = stomachCap(d);
		FoodData food = p.getFoodData();
		if (food.getFoodLevel() > cap) {
			food.setFoodLevel(cap);
			d.lastFood = cap;
		}
		if (food.getSaturationLevel() > cap) {
			food.setSaturation(cap);
		}
		if (cap < 18 && food.getFoodLevel() >= cap && p.getHealth() < p.getMaxHealth() && d.tickCounter % 80 == 0
				&& ExtremeCurses.mayRegenerate(p)) {
			p.heal(1.0f);
			p.causeFoodExhaustion(6.0f);
		}
	}

	/** Short Breath: under water the air runs out faster. */
	private static void shortBreath(ServerPlayer p, PlayerData d) {
		int air = p.getAirSupply();
		int stacks = d.stacks(Penalty.SHORT_BREATH);
		if (stacks > 0 && d.lastAir >= 0 && air < d.lastAir && air > 0 && !p.isCreative()) {
			d.airCarry += (d.lastAir - air) * Penalty.SHORT_BREATH.frac() * stacks;
			int take = (int) d.airCarry;
			if (take > 0) {
				d.airCarry -= take;
				air = Math.max(0, air - take);
				p.setAirSupply(air);
			}
		}
		d.lastAir = air;
	}

	/** Frostbitten: powder snow freezes faster (its damage is raised in {@link #damageTaken}). */
	private static void frostbitten(ServerPlayer p, PlayerData d) {
		int frozen = p.getTicksFrozen();
		int stacks = d.stacks(Penalty.FROSTBITTEN);
		if (stacks > 0 && d.lastFrozen >= 0 && frozen > d.lastFrozen && !p.isCreative()) {
			d.frozenCarry += (frozen - d.lastFrozen) * Penalty.FROSTBITTEN.frac() * stacks;
			int add = (int) d.frozenCarry;
			if (add > 0) {
				d.frozenCarry -= add;
				frozen += add;
				p.setTicksFrozen(frozen);
			}
		}
		d.lastFrozen = frozen;
	}

	/** Lead Pockets: slower while more than half of the main inventory is full. */
	private static void leadPockets(ServerPlayer p, PlayerData d) {
		int stacks = d.stacks(Penalty.LEAD_POCKETS);
		double want = 0;
		if (stacks > 0) {
			var inv = p.getInventory();
			int used = 0;
			for (int i = 0; i < 36; i++) {
				if (!inv.getItem(i).isEmpty()) {
					used++;
				}
			}
			if (used > 18) {
				want = -compound(Penalty.LEAD_POCKETS, stacks);
			}
		}
		if (Double.isNaN(d.leadValue) || want != d.leadValue) {
			d.leadValue = want;
			modifier(p, "minecraft:movement_speed", "lead_pockets", want, "add_multiplied_total");
		}
	}

	/** Kindling, Frostbitten, Volatile and Glass Jaw: more damage from fire, cold, explosions and mobs. */
	public static float damageTaken(ServerPlayer p, DamageSource source, float amount) {
		if (amount <= 0) {
			return amount;
		}
		PlayerData d = DataStore.get(p.getUUID());
		double m = 1.0;
		if (source.is(DamageTypeTags.IS_FIRE)) {
			m *= 1 + Penalty.KINDLING.frac() * d.stacks(Penalty.KINDLING);
		}
		if (source.is(DamageTypeTags.IS_FREEZING)) {
			m *= 1 + Penalty.FROSTBITTEN.frac() * d.stacks(Penalty.FROSTBITTEN);
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			m *= 1 + Penalty.VOLATILE.frac() * d.stacks(Penalty.VOLATILE);
		}
		if (source.getEntity() instanceof Mob) {
			m *= 1 + Penalty.GLASS_JAW.frac() * d.stacks(Penalty.GLASS_JAW);
		}
		return (float) (amount * m);
	}

	/** Fading Brews and Lingering Ills: good effects run out sooner, bad ones linger (beacons and curse effects excepted). */
	public static MobEffectInstance scaleEffect(ServerPlayer p, MobEffectInstance e) {
		if (e == null || e.isAmbient() || e.isInfiniteDuration()) {
			return e;
		}
		PlayerData d = DataStore.get(p.getUUID());
		MobEffectCategory category = e.getEffect().value().getCategory();
		if (category == MobEffectCategory.BENEFICIAL && d.stacks(Penalty.FADING_BREWS) > 0) {
			return e.withScaledDuration((float) (1 - compound(Penalty.FADING_BREWS, d.stacks(Penalty.FADING_BREWS))));
		}
		if (category == MobEffectCategory.HARMFUL && d.stacks(Penalty.LINGERING_ILLS) > 0) {
			return e.withScaledDuration((float) (1 + Penalty.LINGERING_ILLS.frac() * d.stacks(Penalty.LINGERING_ILLS)));
		}
		return e;
	}

	/** After a hit lands: Hungry Wounds, Brittle Gear and Fumbling. */
	public static void afterDamage(LivingEntity entity, DamageSource source, float baseTaken, float taken, boolean blocked) {
		if (!(entity instanceof ServerPlayer p) || taken <= 0 || p.isCreative() || p.isSpectator()) {
			return;
		}
		PlayerData d = DataStore.get(p.getUUID());
		int hungry = d.stacks(Penalty.HUNGRY_WOUNDS);
		if (hungry > 0) {
			p.causeFoodExhaustion((float) (Penalty.HUNGRY_WOUNDS.value * hungry));
		}
		int brittle = d.stacks(Penalty.BRITTLE_GEAR) * (int) Math.round(Penalty.BRITTLE_GEAR.value);
		if (brittle > 0 && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				ItemStack armor = p.getItemBySlot(slot);
				if (armor.isDamageableItem()) {
					armor.hurtAndBreak(brittle, p, slot);
				}
			}
		}
		int fumbling = d.stacks(Penalty.FUMBLING);
		if (fumbling > 0 && source.getEntity() != null && source.getEntity() != p
				&& ThreadLocalRandom.current().nextDouble() < Math.min(0.9, Penalty.FUMBLING.frac() * fumbling)) {
			ItemStack held = p.getMainHandItem();
			if (!held.isEmpty()) {
				p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				throwAway(p, held, 40);
				WorldFx.soundSelf(p, "minecraft:entity.item.break", 0.6f, 1.6f);
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.curses.fumbled"), true);
			}
		}
	}

	/** Drops a stack a little way from the player, out of reach for a moment. */
	private static void throwAway(ServerPlayer p, ItemStack stack, int pickupDelay) {
		ServerLevel level = (ServerLevel) p.level();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		ItemEntity item = new ItemEntity(level, p.getX(), p.getEyeY() - 0.3, p.getZ(), stack);
		double a = rnd.nextDouble() * Math.PI * 2;
		item.setDeltaMovement(Math.cos(a) * 0.25, 0.25, Math.sin(a) * 0.25);
		item.setPickUpDelay(pickupDelay);
		level.addFreshEntity(item);
	}

	/** Hunger without the vanilla effect: the same exhaustion per tick as Hunger I per stack. */
	private static void hunger(ServerPlayer p, PlayerData d, boolean beyond, boolean ow) {
		int levels = d.active(Penalty.FAMINE, beyond, ow) + d.active(Penalty.GNAWING, beyond, ow);
		if (levels > 0) {
			p.causeFoodExhaustion(0.005f * levels);
		}
	}
}
