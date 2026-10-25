package com.extremedimensions.penalty;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.network.Sync;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Abyssal curses: the Extreme set's heavier layer, dealt on Hard and Extreme (and wherever an operator switches
 * them on). One stack each; never spilled onto, laid by an Elder, sworn in a pact or carried for a friend; four times
 * the atonement and fragments to lift, all of it lost on a death, no Mending Cloth. The way out is the curse's deed:
 * done, it breaks the curse at once. Their cards only hint, like every Extreme card; the deed reads plainly once
 * the curse is carried.
 *
 * <ul>
 * <li>Sunscorn: under the open sky by day in the Overworld the player burns, as the undead do; a helmet shields them
 * and wears. Broken by slaying undead in daylight.</li>
 * <li>Gravebound: the player always rises at the world's spawn, never at a bed or anchor. Broken by days without
 * dying.</li>
 * <li>Marrow Rot: a blow from a creature may wither the player. Broken by slaying a Wither.</li>
 * <li>Weight of Iron: each piece of iron or better past the second slows the player. Broken by slaying an Elder in
 * at most two such pieces.</li>
 * <li>Lockjaw: no eating with a monster close. Broken by slaying monsters while hungry.</li>
 * <li>The Hungering: once a day the Starved take a stack from the bag. Broken by a Nether Star offered at an
 * anchor.</li>
 * <li>Cinder Lungs: in the Nether the air burns, unless fire cannot touch the player. Broken by slaying the
 * Ferryman.</li>
 * <li>Mirror Wound: a share of every blow the player strikes comes back to them. Broken by slaying monsters with
 * arrows and tridents.</li>
 * </ul>
 *
 * <p>The next eight, tolls on what the player already has, live in {@link AbyssalTolls}; the last eight, the world
 * itself turned against the player, in {@link AbyssalWorld}.
 */
public final class AbyssalCurses {
	private AbyssalCurses() {}

	/** Blocks round a fallen Elder within which its fighters share in its death. */
	private static final double ELDER_FIGHT_RANGE = 96;

	// ------------------------------------------------------------------ dealing

	/** The Abyssal curses this player could still receive. */
	public static List<Penalty> pool(PlayerData d) {
		List<Penalty> out = new ArrayList<>();
		for (Penalty p : Penalty.values()) {
			if (p.abyssal && p.max > 0 && d.owned(p) < p.max) {
				out.add(p);
			}
		}
		return out;
	}

	/** How many Abyssal curses the player carries. */
	public static int carried(PlayerData d) {
		int n = 0;
		for (Penalty p : Penalty.values()) {
			if (p.abyssal && d.owned(p) > 0) {
				n++;
			}
		}
		return n;
	}

	/**
	 * The cards of an offer, with an Abyssal one dealt in now and then: only where they are switched on, after enough
	 * Collapses and below the most a player carries. It takes the place of a card that is not Extreme; on a forced deal
	 * (Extreme) it is the whole offer.
	 */
	public static List<String> deal(PlayerData d, List<String> cards, Random rnd) {
		if (cards.isEmpty() || Config.ABYSSAL_CURSES <= 0 || d.collapses < Math.max(0, Config.ABYSSAL_AFTER_COLLAPSES)
				|| carried(d) >= Math.max(0, Config.ABYSSAL_MAX_CARRIED)) {
			return cards;
		}
		List<Penalty> pool = pool(d);
		if (pool.isEmpty() || rnd.nextDouble() >= Config.ABYSSAL_CARD_CHANCE) {
			return cards;
		}
		String pick = pool.get(rnd.nextInt(pool.size())).id;
		if (rnd.nextDouble() < Config.ABYSSAL_FORCED_CHANCE) {
			return new ArrayList<>(List.of(pick));
		}
		List<String> out = new ArrayList<>(cards);
		List<Integer> plain = new ArrayList<>();
		for (int i = 0; i < out.size(); i++) {
			Penalty p = Penalty.byId(out.get(i));
			if (p == null || !p.extreme) {
				plain.add(i);
			}
		}
		int at = plain.isEmpty() ? rnd.nextInt(out.size()) : plain.get(rnd.nextInt(plain.size()));
		out.set(at, pick);
		return out;
	}

	/** Whether the curse is one a Mending Cloth will not take. */
	public static boolean clothRefuses(Penalty p) {
		return p.abyssal && Config.ABYSSAL_MENDING_CLOTH <= 0;
	}

	// ------------------------------------------------------------------ deeds

	/** How much of its deed breaks the curse (the config's abyssal_break_<curse>). */
	public static int target(Penalty p) {
		try {
			return Math.max(1, Config.class.getField("ABYSSAL_BREAK_" + p.id.toUpperCase(Locale.ROOT)).getInt(null));
		} catch (ReflectiveOperationException e) {
			return 1;
		}
	}

	/** How far the player is with the curse's deed. */
	public static int progress(PlayerData d, Penalty p) {
		return d.abyssalDeeds == null ? 0 : d.abyssalDeeds.getOrDefault(p.id, 0);
	}

	/** The player did {@code n} more of a curse's deed; done, the curse breaks. */
	public static void advance(ServerPlayer p, PlayerData d, Penalty pen, int n) {
		if (n <= 0 || !pen.abyssal || d.owned(pen) <= 0) {
			return;
		}
		set(p, d, pen, progress(d, pen) + n);
	}

	/** The deed starts over (a streak broken: Lightless' nights). */
	public static void restart(ServerPlayer p, PlayerData d, Penalty pen) {
		if (pen.abyssal && d.owned(pen) > 0 && progress(d, pen) > 0) {
			set(p, d, pen, 0);
		}
	}

	private static void set(ServerPlayer p, PlayerData d, Penalty pen, int value) {
		if (value >= target(pen)) {
			breakCurse(p, d, pen);
			return;
		}
		if (d.abyssalDeeds == null) {
			d.abyssalDeeds = new java.util.HashMap<>();
		}
		d.abyssalDeeds.put(pen.id, Math.max(0, value));
		DataStore.markDirty();
		Sync.curses(p, d, false);
	}

	/** The deed is done: the curse lets go at once, every stack of it, and leaves its scar. */
	public static void breakCurse(ServerPlayer p, PlayerData d, Penalty pen) {
		if (d.abyssalDeeds != null) {
			d.abyssalDeeds.remove(pen.id);
		}
		for (int guard = 0; d.owned(pen) > 0 && guard < 8; guard++) {
			PenaltyService.lift(p, d, pen, "gui.extremedimensions.abyssal.broken");
		}
		WorldFx.title(p, "gui.extremedimensions.abyssal.broken_title", "dark_purple", "gui.extremedimensions.abyssal.broken_subtitle", pen.nameKey());
		WorldFx.sound(p, "minecraft:block.end_portal.spawn", 0.6f, 1.4f);
		WorldFx.particles(p, "minecraft:reverse_portal", 1.0, 0.6, 0.1, 60);
	}

	// ------------------------------------------------------------------ what they do

	/** Once a second, for a player carrying any Abyssal curse. */
	public static void tick(ServerPlayer p, PlayerData d) {
		if (p.isCreative() || p.isSpectator() || !(p.level() instanceof ServerLevel level)) {
			return;
		}
		sunscorn(p, d, level);
		cinderLungs(p, d, level);
		weightOfIron(p, d);
		hungering(p, d, level);
		gravebound(p, d, level);
		AbyssalTolls.tick(p, d, level);
		AbyssalWorld.tick(p, d, level);
	}

	/** Sunscorn: the undead's sun. Bare-headed under the open sky by day in the Overworld, the player catches fire. */
	private static void sunscorn(ServerPlayer p, PlayerData d, ServerLevel level) {
		if (d.stacks(Penalty.SUNSCORN) <= 0 || !Dims.OVERWORLD.equals(Dims.of(p)) || !inSun(level, p.getX(), p.getEyeY(), p.getZ()) || p.isInWaterOrRain()) {
			return;
		}
		scorch(p);
	}

	/** In the sun with Sunscorn: a helmet shields the player and wears for it; bare-headed, they catch fire. */
	public static void scorch(ServerPlayer p) {
		ItemStack helmet = p.getItemBySlot(EquipmentSlot.HEAD);
		if (!helmet.isEmpty()) {
			// shielded, as a zombie's helmet shields it, and wearing for it
			if (helmet.isDamageableItem() && ThreadLocalRandom.current().nextBoolean()) {
				helmet.hurtAndBreak(1, p, EquipmentSlot.HEAD);
			}
			return;
		}
		p.setRemainingFireTicks(Math.max(p.getRemainingFireTicks(), (int) (20 * Math.max(1, Penalty.SUNSCORN.value))));
	}

	/** Whether a spot lies in the sun: day, dry, and under the open sky. */
	public static boolean inSun(ServerLevel level, double x, double y, double z) {
		BlockPos at = BlockPos.containing(x, y, z);
		return !level.isDarkOutside() && !level.isRainingAt(at) && level.canSeeSky(at);
	}

	/** Cinder Lungs: in the Nether the air burns every few seconds, unless fire cannot touch the player. */
	private static void cinderLungs(ServerPlayer p, PlayerData d, ServerLevel level) {
		if (d.stacks(Penalty.CINDER_LUNGS) <= 0 || !Dims.NETHER.equals(Dims.of(p)) || p.hasEffect(MobEffects.FIRE_RESISTANCE)) {
			return;
		}
		int every = Math.max(1, (int) Math.round(Penalty.CINDER_LUNGS.value));
		if ((d.tickCounter / 20) % every == 0) {
			p.hurtServer(level, p.damageSources().onFire(), 1.0f);
		}
	}

	/** Weight of Iron: slower by a share for every piece of iron or better past the second. */
	private static void weightOfIron(ServerPlayer p, PlayerData d) {
		int over = d.stacks(Penalty.WEIGHT_OF_IRON) > 0 ? Math.max(0, heavy(p) - 2) : 0;
		double want = over == 0 ? 0 : -Math.min(0.9, Penalty.WEIGHT_OF_IRON.frac() * over);
		if (Double.isNaN(d.ironValue) || want != d.ironValue) {
			d.ironValue = want;
			CurseEffects.modifier(p, "minecraft:movement_speed", "weight_of_iron", want, "add_multiplied_total");
		}
	}

	/** Pieces of armour of iron or better the player wears. */
	public static int heavy(Player p) {
		int n = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			String id = BuiltInRegistries.ITEM.getKey(p.getItemBySlot(slot).getItem()).getPath();
			if (id.startsWith("iron_") || id.startsWith("diamond_") || id.startsWith("netherite_")) {
				n++;
			}
		}
		return n;
	}

	/** The Hungering: once a day (by the Overworld's clock) the Starved take a stack from the bag. */
	private static void hungering(ServerPlayer p, PlayerData d, ServerLevel level) {
		if (d.stacks(Penalty.THE_HUNGERING) <= 0) {
			return;
		}
		long day = level.getServer().overworld().getGameTime() / 24000L;
		if (d.hungeringDay == Long.MIN_VALUE || day < d.hungeringDay) {
			// (the day it was taken is spared)
			d.hungeringDay = day;
			return;
		}
		if (day == d.hungeringDay) {
			return;
		}
		d.hungeringDay = day;
		devour(p);
	}

	/** One stack from the bag (never the hotbar, the armour or the mod's own things) is gone. Returns it, or empty. */
	public static ItemStack devour(ServerPlayer p) {
		var inv = p.getInventory();
		List<Integer> slots = new ArrayList<>();
		for (int i = 9; i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && !"extremedimensions".equals(BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace())) {
				slots.add(i);
			}
		}
		if (slots.isEmpty()) {
			return ItemStack.EMPTY;
		}
		int slot = slots.get(ThreadLocalRandom.current().nextInt(slots.size()));
		ItemStack taken = inv.getItem(slot).copy();
		inv.setItem(slot, ItemStack.EMPTY);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.abyssal.hungering", taken.getCount(), taken.getHoverName()), true);
		WorldFx.soundSelf(p, "minecraft:entity.generic.eat", 1.0f, 0.4f);
		WorldFx.particles(p, "minecraft:sculk_soul", 1.0, 0.5, 0.03, 16);
		return taken;
	}

	/** Gravebound: its deed is days without dying, counted on the Overworld's clock from the curse or the last death. */
	private static void gravebound(ServerPlayer p, PlayerData d, ServerLevel level) {
		if (d.owned(Penalty.GRAVEBOUND) <= 0) {
			d.graveSince = Long.MIN_VALUE;
			return;
		}
		long now = level.getServer().overworld().getGameTime();
		if (d.graveSince == Long.MIN_VALUE || d.graveSince > now) {
			d.graveSince = now;
		}
		int days = (int) ((now - d.graveSince) / 24000L);
		if (days != progress(d, Penalty.GRAVEBOUND)) {
			set(p, d, Penalty.GRAVEBOUND, days);
		}
	}

	// ------------------------------------------------------------------ hooks

	/** After a hit lands: Marrow Rot on the player struck, Mirror Wound on the player striking. */
	public static void afterDamage(LivingEntity entity, DamageSource source, float baseTaken, float taken, boolean blocked) {
		AbyssalTolls.afterDamage(entity, source, baseTaken, taken, blocked);
		if (taken <= 0 || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (entity instanceof ServerPlayer p && !p.isCreative() && source.getEntity() instanceof LivingEntity attacker && attacker != p
				&& DataStore.get(p.getUUID()).stacks(Penalty.MARROW_ROT) > 0
				&& ThreadLocalRandom.current().nextDouble() < Penalty.MARROW_ROT.frac()) {
			p.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
		}
		if (source.getEntity() instanceof ServerPlayer striker && source.getDirectEntity() == striker && entity != striker && !striker.isCreative()
				&& DataStore.get(striker.getUUID()).stacks(Penalty.MIRROR_WOUND) > 0) {
			float back = (float) (taken * Penalty.MIRROR_WOUND.frac());
			if (back > 0) {
				striker.hurtServer(level, striker.damageSources().magic(), back);
			}
		}
	}

	/** A player slew a creature: the deeds of Sunscorn, Lockjaw, Mirror Wound and Marrow Rot. */
	public static void onKill(ServerPlayer killer, LivingEntity victim, DamageSource source) {
		PlayerData d = DataStore.get(killer.getUUID());
		if (carried(d) == 0 || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		if (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(victim.getType()).is(EntityTypeTags.UNDEAD) && Dims.OVERWORLD.equals(Dims.of(killer))
				&& inSun(level, victim.getX(), victim.getEyeY(), victim.getZ())) {
			advance(killer, d, Penalty.SUNSCORN, 1);
		}
		if (victim instanceof Enemy && killer.getFoodData().getFoodLevel() <= 6) {
			advance(killer, d, Penalty.LOCKJAW, 1);
		}
		if (victim instanceof Enemy && source.is(DamageTypeTags.IS_PROJECTILE)) {
			advance(killer, d, Penalty.MIRROR_WOUND, 1);
		}
		if ("minecraft:wither".equals(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString())) {
			advance(killer, d, Penalty.MARROW_ROT, 1);
		}
		AbyssalTolls.onKill(killer, d, victim);
		AbyssalWorld.onKill(killer, d, victim);
	}

	/** An Elder fell: its fighters close by do the deeds of Weight of Iron (in light armour) and Cinder Lungs (the Ferryman). */
	public static void elderSlain(LivingEntity elder) {
		if (!(elder.level() instanceof ServerLevel level)) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(elder) <= ELDER_FIGHT_RANGE * ELDER_FIGHT_RANGE) {
				elderDeeds(p, elder instanceof com.extremedimensions.elder.Ferryman);
			}
		}
	}

	/** One fighter's share in an Elder's death: Weight of Iron's deed in light armour, Cinder Lungs' if it was the Ferryman. */
	public static void elderDeeds(ServerPlayer p, boolean ferryman) {
		PlayerData d = DataStore.get(p.getUUID());
		if (heavy(p) <= 2) {
			advance(p, d, Penalty.WEIGHT_OF_IRON, 1);
		}
		if (ferryman) {
			advance(p, d, Penalty.CINDER_LUNGS, 1);
		}
	}

	/** The player died: Gravebound's days start over. */
	public static void onDeath(ServerPlayer p) {
		PlayerData d = DataStore.get(p.getUUID());
		if (d.owned(Penalty.GRAVEBOUND) > 0 && p.level() instanceof ServerLevel level) {
			d.graveSince = level.getServer().overworld().getGameTime();
			if (d.abyssalDeeds != null) {
				d.abyssalDeeds.remove(Penalty.GRAVEBOUND.id);
			}
		}
	}

	/** The player rose again: Gravebound sends them to the world's spawn, wherever their bed or anchor stands. */
	public static void onRespawn(ServerPlayer p) {
		PlayerData d = DataStore.get(p.getUUID());
		// a new body: the attribute modifiers are set on it afresh
		d.ironValue = Double.NaN;
		d.bloodValue = Double.NaN;
		if (d.stacks(Penalty.GRAVEBOUND) <= 0 || WorldFx.server() == null) {
			return;
		}
		ServerLevel overworld = WorldFx.server().overworld();
		BlockPos spawn = overworld.getRespawnData().pos();
		if (p.level() == overworld && p.blockPosition().distSqr(spawn) <= 32 * 32) {
			return;
		}
		int y = Math.max(spawn.getY(), overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()));
		p.teleportTo(overworld, spawn.getX() + 0.5, y, spawn.getZ() + 0.5, Set.of(), p.getYRot(), p.getXRot(), true);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.abyssal.gravebound"), true);
	}

	/** Lockjaw stacks and reach as the client last heard them (the client refuses the meal too, so it never starts). */
	public static int clientLockjaw = 0;
	public static double clientLockjawRange = 0;

	/** Lockjaw: no eating while a monster stands close, on both sides. PASS lets the meal go ahead. */
	public static InteractionResult refuseFood(Player player, ItemStack stack) {
		if (player.isCreative() || player.isSpectator() || !stack.has(DataComponents.FOOD)) {
			return InteractionResult.PASS;
		}
		boolean server = player instanceof ServerPlayer;
		int stacks = server ? DataStore.get(player.getUUID()).stacks(Penalty.LOCKJAW) : clientLockjaw;
		if (stacks <= 0) {
			return InteractionResult.PASS;
		}
		double r = Math.max(1, server || clientLockjawRange <= 0 ? Penalty.LOCKJAW.value : clientLockjawRange);
		if (player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(r), m -> m instanceof Enemy && m.isAlive()).isEmpty()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer p) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.abyssal.lockjaw"), true);
			WorldFx.soundSelf(p, "minecraft:entity.player.burp", 0.6f, 0.5f);
		} else {
			// (the client never asks the server, so it hears the jaw shut itself)
			player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_BURP, 0.6f, 0.5f);
		}
		return InteractionResult.FAIL;
	}

	/** A Nether Star offered at an anchor: The Hungering's deed. Not carrying it, nothing happens. */
	public static InteractionResult offerStar(ServerPlayer p, BlockPos pos, ItemStack stack) {
		PlayerData d = DataStore.get(p.getUUID());
		if (!stack.is(Items.NETHER_STAR) || d.owned(Penalty.THE_HUNGERING) <= 0) {
			return InteractionResult.PASS;
		}
		if (!p.isCreative()) {
			stack.shrink(1);
		}
		WorldFx.particleIn(p.level(), "minecraft:end_rod", pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 0.3, 0.6, 0.3, 0.05, 40);
		advance(p, d, Penalty.THE_HUNGERING, 1);
		return InteractionResult.SUCCESS;
	}
}
