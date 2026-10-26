package com.extremedimensions.penalty;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * The second eight Abyssal curses, each a toll on something the player already has (their lifting, their food, their
 * hotbar, their kills, their footing, their shield, their cures, their animals):
 *
 * <ul>
 * <li>Blood Price: every stack lifted from another curse costs maximum health for a while, and now and then the stack
 * jumps to another curse instead. Broken by lifting stacks while it holds.</li>
 * <li>Rot: food in the inventory spoils, a quarter of a stack at a time. Broken by eating rotten flesh.</li>
 * <li>Covetous: a Lurker comes every night, whatever its wait, and takes from the hotbar. Broken by taking back what
 * it stole.</li>
 * <li>Restless Dead: a slain monster may rise again a few seconds later. Broken by slaying monsters.</li>
 * <li>Unmoored: in the End a blow may throw the player a few blocks, the void's way too. Broken by slaying
 * Endermen.</li>
 * <li>Splintering: shields wear many times as fast. Broken by blocking blows.</li>
 * <li>Bitter Cure: Instant Health and Regeneration (golden apples too) heal only part. Broken by curing a zombie
 * villager.</li>
 * <li>Forsaken Bond: once a day a tamed animal near the player forgets them. Broken by binding a Hollowling.</li>
 * </ul>
 */
public final class AbyssalTolls {
	private AbyssalTolls() {}

	/** The most maximum health Blood Price takes at once (six hearts); with the other curses it never goes below curse_health_floor. */
	private static final int MAX_BLOOD_DEBT = 12;
	/** The mark on a stack a Lurker took under Covetous, until it is back in its owner's hands. */
	public static final String STOLEN = "gui.extremedimensions.abyssal.stolen";
	/** A monster risen under Restless Dead: it does not rise twice. */
	public static final String RISEN = "extremedimensions_risen";
	/** Blocks round a cured villager within which a player shares in the cure (Bitter Cure). */
	private static final double CURE_RANGE = 16;

	private record Rising(ServerLevel level, EntityType<?> type, double x, double y, double z, long at, UUID killer) {}

	/** Monsters waiting to rise again (Restless Dead); lost with the server, as a fight would be. */
	private static final List<Rising> RISINGS = new ArrayList<>();

	public static void clear() {
		RISINGS.clear();
	}

	private static long now(ServerPlayer p) {
		return p.level().getServer().overworld().getGameTime();
	}

	/** Once a second, for a player carrying any Abyssal curse (or still paying for one). */
	static void tick(ServerPlayer p, PlayerData d, ServerLevel level) {
		blood(p, d);
		rot(p, d);
		recover(p, d);
		bond(p, d, level);
	}

	// ------------------------------------------------------------------ Blood Price

	/** A stack came off a curse: under Blood Price it costs maximum health, may jump to another curse, and counts. */
	public static void afterLift(ServerPlayer p, PlayerData d, Penalty lifted) {
		if (lifted == Penalty.BLOOD_PRICE || d.stacks(Penalty.BLOOD_PRICE) <= 0) {
			return;
		}
		d.bloodDebt = Math.min(MAX_BLOOD_DEBT, d.bloodDebt + (int) Math.round(Math.max(0, Penalty.BLOOD_PRICE.value)));
		d.bloodUntil = now(p) + Math.max(1, Config.ABYSSAL_BLOOD_PRICE_MINUTES) * 1200L;
		blood(p, d);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.abyssal.blood_price", Component.translatable(lifted.nameKey())), true);
		WorldFx.soundSelf(p, "minecraft:entity.player.hurt_sweet_berry_bush", 0.8f, 0.6f);
		if (ThreadLocalRandom.current().nextDouble() < Config.ABYSSAL_BLOOD_PRICE_JUMP) {
			List<Penalty> room = new ArrayList<>(Penalty.withRoom(d, DataStore.overworldPenalties()));
			room.remove(lifted);
			if (!room.isEmpty()) {
				Penalty to = room.get(ThreadLocalRandom.current().nextInt(room.size()));
				d.addStacks(to, 1);
				CurseEffects.applyEffects(p, d);
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.abyssal.blood_jump", Component.translatable(to.nameKey())));
			}
		}
		AbyssalCurses.advance(p, d, Penalty.BLOOD_PRICE, 1);
	}

	/** The maximum health Blood Price holds, until its time runs out, within what the lasting curses leave above the floor. */
	static void blood(ServerPlayer p, PlayerData d) {
		if (d.bloodUntil != Long.MIN_VALUE && now(p) >= d.bloodUntil) {
			d.bloodDebt = 0;
			d.bloodUntil = Long.MIN_VALUE;
		}
		double[] lasting = CurseEffects.healthCuts(p, d);
		double left = Math.max(0, CurseEffects.healthRoom(p) - lasting[0] - lasting[1] - lasting[2]);
		double want = -Math.min(Math.max(0, d.bloodDebt), left);
		if (Double.isNaN(d.bloodValue) || want != d.bloodValue) {
			d.bloodValue = want;
			CurseEffects.modifier(p, "minecraft:max_health", "blood_price", want, "add_value");
			if (p.getHealth() > p.getMaxHealth()) {
				p.setHealth(p.getMaxHealth());
			}
		}
	}

	// ------------------------------------------------------------------ Rot

	private static void rot(ServerPlayer p, PlayerData d) {
		if (d.stacks(Penalty.ROT) <= 0) {
			d.rotIn = -1;
			d.abyssalSeen.remove("rot");
			return;
		}
		int every = Math.max(1, (int) Math.round(Penalty.ROT.value * 60));
		if (d.rotIn < 0) {
			d.rotIn = every;
		} else if (--d.rotIn <= 0) {
			spoil(p);
			d.rotIn = every;
		}
		// The deed: rotten flesh eaten, as the game counts it
		int eaten = p.getStats().getValue(Stats.ITEM_USED.get(Items.ROTTEN_FLESH));
		Integer seen = d.abyssalSeen.get("rot");
		d.abyssalSeen.put("rot", eaten);
		if (seen != null && eaten > seen) {
			AbyssalCurses.advance(p, d, Penalty.ROT, eaten - seen);
		}
	}

	/** A quarter of one stack of food in the inventory (at least one) turns to rotten flesh. Returns what spoiled, or empty. */
	public static ItemStack spoil(ServerPlayer p) {
		var inv = p.getInventory();
		List<Integer> food = new ArrayList<>();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && s.has(DataComponents.FOOD) && !s.is(Items.ROTTEN_FLESH)
					&& !"extremedimensions".equals(BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace())) {
				food.add(i);
			}
		}
		if (food.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = inv.getItem(food.get(ThreadLocalRandom.current().nextInt(food.size())));
		int n = Math.max(1, (stack.getCount() + 3) / 4);
		ItemStack spoiled = stack.copyWithCount(n);
		stack.shrink(n);
		WorldFx.give(p, new ItemStack(Items.ROTTEN_FLESH, n));
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.abyssal.rot", n, spoiled.getHoverName()), true);
		WorldFx.soundSelf(p, "minecraft:block.composter.fill", 1.0f, 0.6f);
		return spoiled;
	}

	// ------------------------------------------------------------------ Covetous

	/** Whether a Lurker takes from this player's hotbar (Covetous). */
	public static boolean covets(ServerPlayer p) {
		return DataStore.get(p.getUUID()).stacks(Penalty.COVETOUS) > 0;
	}

	/** Covetous: true once a night in the Overworld, the night a Lurker is sent whatever its wait. */
	public static boolean covetousNight(ServerPlayer p, PlayerData d) {
		if (d.stacks(Penalty.COVETOUS) <= 0 || !Dims.OVERWORLD.equals(Dims.of(p)) || !p.level().isDarkOutside()) {
			return false;
		}
		long night = now(p) / 24000L;
		if (night == d.covetousNight) {
			return false;
		}
		d.covetousNight = night;
		return true;
	}

	/** A Lurker took this stack under Covetous: it carries the tribe's mark until its owner holds it again. */
	public static void mark(ServerPlayer p, ItemStack stack) {
		ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		stack.set(DataComponents.LORE, lore.withLineAdded(Component.translatable(STOLEN).withStyle(ChatFormatting.DARK_PURPLE)));
		DataStore.get(p.getUUID()).covetousOwed++;
	}

	private static boolean marked(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return false;
		}
		for (Component line : lore.lines()) {
			if (line.getContents() instanceof TranslatableContents tc && STOLEN.equals(tc.getKey())) {
				return true;
			}
		}
		return false;
	}

	/** Takes the mark off a stack again. */
	private static void unmark(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		List<Component> keep = new ArrayList<>();
		for (Component line : lore.lines()) {
			if (!(line.getContents() instanceof TranslatableContents tc && STOLEN.equals(tc.getKey()))) {
				keep.add(line);
			}
		}
		if (keep.isEmpty()) {
			stack.remove(DataComponents.LORE);
		} else {
			stack.set(DataComponents.LORE, new ItemLore(keep));
		}
	}

	/** Stolen things back in the player's hands: the mark comes off, and each counts towards Covetous' deed. */
	static void recover(ServerPlayer p, PlayerData d) {
		if (d.covetousOwed <= 0) {
			return;
		}
		var inv = p.getInventory();
		int back = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && marked(s)) {
				unmark(s);
				back++;
			}
		}
		// (a stack split into several counts once: no more than was taken)
		int n = Math.min(back, d.covetousOwed);
		if (n > 0) {
			d.covetousOwed -= n;
			AbyssalCurses.advance(p, d, Penalty.COVETOUS, n);
		}
	}

	// ------------------------------------------------------------------ Restless Dead and Unmoored

	/** A player slew a creature: Restless Dead may raise it, and counts it; Unmoored counts Endermen. */
	static void onKill(ServerPlayer killer, PlayerData d, LivingEntity victim) {
		if (victim instanceof Enemy && victim instanceof Mob && victim.level() instanceof ServerLevel level) {
			if (d.stacks(Penalty.RESTLESS_DEAD) > 0 && !victim.entityTags().contains(RISEN) && victim.getMaxHealth() <= 100
					&& !(victim instanceof com.extremedimensions.elder.ElderBoss)
					&& ThreadLocalRandom.current().nextDouble() < Penalty.RESTLESS_DEAD.frac()) {
				long at = level.getGameTime() + Math.max(1, Config.ABYSSAL_RESTLESS_DELAY_SECONDS) * 20L;
				RISINGS.add(new Rising(level, victim.getType(), victim.getX(), victim.getY(), victim.getZ(), at, killer.getUUID()));
			}
			// (a monster it raised itself, or one from a spawner, does not count)
			if (AtonementGuard.natural(victim) && !victim.entityTags().contains(RISEN)) {
				AbyssalCurses.advance(killer, d, Penalty.RESTLESS_DEAD, 1);
			}
		}
		if ("minecraft:enderman".equals(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString())) {
			AbyssalCurses.advance(killer, d, Penalty.UNMOORED, 1);
		}
	}

	/** Every tick: the monsters whose time has come rise again, where they fell, after their killer. */
	public static void serverTick(MinecraftServer s) {
		if (RISINGS.isEmpty()) {
			return;
		}
		for (Iterator<Rising> it = RISINGS.iterator(); it.hasNext();) {
			Rising r = it.next();
			if (r.level().getGameTime() < r.at()) {
				continue;
			}
			it.remove();
			rise(r);
		}
	}

	/** One monster rises again (and is returned, or null if it could not). */
	private static Entity rise(Rising r) {
		BlockPos at = BlockPos.containing(r.x(), r.y(), r.z());
		if (!r.level().isLoaded(at)) {
			return null;
		}
		Entity risen = r.type().spawn(r.level(), at, EntitySpawnReason.MOB_SUMMONED);
		if (risen == null) {
			return null;
		}
		risen.addTag(RISEN);
		if (risen instanceof Mob m && r.level().getEntity(r.killer()) instanceof ServerPlayer killer) {
			m.setTarget(killer);
		}
		WorldFx.particleIn(r.level(), "minecraft:soul", r.x(), r.y() + 0.6, r.z(), 0.4, 0.6, 0.4, 0.04, 24);
		WorldFx.particleIn(r.level(), "minecraft:sculk_soul", r.x(), r.y() + 0.2, r.z(), 0.5, 0.1, 0.5, 0.02, 12);
		r.level().playSound(null, r.x(), r.y(), r.z(), net.minecraft.sounds.SoundEvents.ZOMBIE_VILLAGER_CONVERTED,
				net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 0.5f);
		return risen;
	}

	/** For the tests: raise the next monster waiting, now. */
	public static Entity riseNext() {
		if (RISINGS.isEmpty()) {
			return null;
		}
		return rise(RISINGS.remove(0));
	}

	/** How many monsters wait to rise. */
	public static int risings() {
		return RISINGS.size();
	}

	/** Unmoored: a blow in the End may throw the player a few blocks, wherever that lands them. */
	private static void unmoor(ServerPlayer p) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double r = Math.max(1, Config.ABYSSAL_UNMOORED_RANGE);
		double angle = rnd.nextDouble() * Math.PI * 2, dist = r * (0.5 + 0.5 * rnd.nextDouble());
		WorldFx.particles(p, "minecraft:portal", 1.0, 0.5, 0.4, 30);
		p.teleportTo(p.getX() + Math.cos(angle) * dist, p.getY(), p.getZ() + Math.sin(angle) * dist);
		WorldFx.particles(p, "minecraft:reverse_portal", 1.0, 0.5, 0.1, 30);
		WorldFx.sound(p, "minecraft:entity.enderman.teleport", 1.0f, 0.7f);
	}

	/** For the tests and the hook: throw the player as Unmoored would. */
	public static void throwPlayer(ServerPlayer p) {
		unmoor(p);
	}

	// ------------------------------------------------------------------ Splintering, Unmoored on a blow

	/** After a hit lands (or is blocked): Splintering wears the shield and counts the block; Unmoored may throw. */
	static void afterDamage(LivingEntity entity, DamageSource source, float baseTaken, float taken, boolean blocked) {
		if (!(entity instanceof ServerPlayer p) || p.isCreative()) {
			return;
		}
		PlayerData d = DataStore.get(p.getUUID());
		// Only a foe's blow counts towards Splintering's deed, and only a foe's blow throws under Unmoored
		boolean foe = source.getEntity() instanceof LivingEntity attacker && attacker != p;
		if (blocked && baseTaken > 0 && d.stacks(Penalty.SPLINTERING) > 0) {
			splinter(p, baseTaken);
			if (foe) {
				AbyssalCurses.advance(p, d, Penalty.SPLINTERING, 1);
			}
		}
		if (foe && taken > 0 && d.stacks(Penalty.UNMOORED) > 0 && Dims.END.equals(Dims.of(p))
				&& ThreadLocalRandom.current().nextDouble() < Penalty.UNMOORED.frac() * com.extremedimensions.binding.Charms.unmoored(p)) {
			unmoor(p);
		}
	}

	/**
	 * The shield in use wears as many times as fast as Splintering says: on top of the game's own wear (one plus the
	 * blow, for blows of three or more) the rest of it. Returns the extra wear.
	 */
	public static int splinter(ServerPlayer p, float blow) {
		ItemStack shield = p.getUseItem();
		if (shield.isEmpty() || !shield.isDamageableItem()) {
			return 0;
		}
		int vanilla = blow >= 3 ? 1 + (int) Math.floor(blow) : 0;
		int extra = (int) Math.round(Math.max(0, Penalty.SPLINTERING.value - 1) * vanilla);
		if (extra > 0) {
			shield.hurtAndBreak(extra, p, p.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		}
		return extra;
	}

	// ------------------------------------------------------------------ Bitter Cure

	/** Healing from Instant Health or Regeneration (see BitterCureMixin): under Bitter Cure, only part of it. */
	public static float cure(LivingEntity entity, float amount) {
		if (!(entity instanceof ServerPlayer p) || p.isCreative()) {
			return amount;
		}
		PlayerData d = DataStore.peek(p.getUUID());
		if (d == null || d.stacks(Penalty.BITTER_CURE) <= 0) {
			return amount;
		}
		return (float) (amount * Math.max(0, 1 - Penalty.BITTER_CURE.frac()));
	}

	/** A mob turned into another: a zombie villager cured breaks Bitter Cure for those close by. */
	public static void converted(Mob previous, Mob converted) {
		if (!"minecraft:zombie_villager".equals(BuiltInRegistries.ENTITY_TYPE.getKey(previous.getType()).toString())
				|| !"minecraft:villager".equals(BuiltInRegistries.ENTITY_TYPE.getKey(converted.getType()).toString())
				|| !(converted.level() instanceof ServerLevel level)) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(converted) <= CURE_RANGE * CURE_RANGE) {
				AbyssalCurses.advance(p, DataStore.get(p.getUUID()), Penalty.BITTER_CURE, 1);
			}
		}
	}

	// ------------------------------------------------------------------ Forsaken Bond

	/** Forsaken Bond: once a day (by the Overworld's clock) a tamed animal near the player forgets them. */
	private static void bond(ServerPlayer p, PlayerData d, ServerLevel level) {
		if (d.stacks(Penalty.FORSAKEN_BOND) <= 0) {
			return;
		}
		long day = now(p) / 24000L;
		if (d.bondDay == Long.MIN_VALUE || day < d.bondDay) {
			// (the day it was taken is spared)
			d.bondDay = day;
			return;
		}
		if (day == d.bondDay) {
			return;
		}
		d.bondDay = day;
		forsake(p);
	}

	/** One of the player's tamed animals close by (a wolf, a cat, a horse) goes wild again. Returns it, or null if none is near. */
	public static Mob forsake(ServerPlayer p) {
		double r = Math.max(1, Penalty.FORSAKEN_BOND.value);
		List<Mob> tame = p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(r),
				m -> (m instanceof TamableAnimal a && a.isTame() && a.getOwner() == p) || (m instanceof AbstractHorse h && h.isTamed() && h.getOwner() == p));
		if (tame.isEmpty()) {
			return null;
		}
		Mob m = tame.get(ThreadLocalRandom.current().nextInt(tame.size()));
		if (m instanceof TamableAnimal a) {
			a.setTame(false, true);
			a.setOwnerReference(null);
			a.setOrderedToSit(false);
		} else if (m instanceof AbstractHorse h) {
			h.setTamed(false);
			h.setOwner(null);
		}
		WorldFx.particleIn(p.level(), "minecraft:smoke", m.getX(), m.getY() + m.getBbHeight(), m.getZ(), 0.3, 0.3, 0.3, 0.02, 14);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.abyssal.forsaken", m.getDisplayName()), true);
		return m;
	}
}
