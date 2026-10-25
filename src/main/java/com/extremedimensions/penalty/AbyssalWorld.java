package com.extremedimensions.penalty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The last eight Abyssal curses, the world itself turned against the player (its dark, its maps, its doors, its
 * furnaces, its fields, its stone, its sky, the Thinned):
 *
 * <ul>
 * <li>Lightless: round the player monsters spawn as if every light were dimmer. Broken by nights in the Overworld kept
 * awake through, one after the other.</li>
 * <li>Unmapped: no coordinates on the debug screen, and maps held stay blank. Broken by safe returns from
 * expeditions.</li>
 * <li>Unwelcome: zombies near the player break wooden doors on any difficulty, and other monsters open them. Broken by
 * winning a raid.</li>
 * <li>Cold Forge: furnaces the player works cook slower and burn their fuel faster. Broken by smelting.</li>
 * <li>Barren Hand: crops the player plants grow slower, and bone meal on them often fails. Broken by harvesting.</li>
 * <li>Cave-In: deep down, mining stone may bring the block above down on the player. Broken by mining deepslate.</li>
 * <li>Wingshadow: Phantoms hunt the player every night, slept or not. Broken by slaying Phantoms.</li>
 * <li>Unwatched: in the Seam the Thinned move now and then while being looked at. Broken by slaying Thinned.</li>
 * </ul>
 */
public final class AbyssalWorld {
	private AbyssalWorld() {}

	/** The game's own reduced debug screen, sent to one player: on (no coordinates) or off. */
	private static final byte REDUCED_DEBUG_ON = 22, REDUCED_DEBUG_OFF = 23;
	/** The time since rest Wingshadow keeps a player at by night, in days of the game: Phantoms come for them. */
	private static final int TICKS_A_DAY = 24000;

	/** Where Lightless and Unwelcome reach: the players carrying them, per world, refreshed every second. */
	private record Holder(ServerLevel level, double x, double z) {}

	private static final List<Holder> LIGHTLESS = new ArrayList<>();
	private static final List<Holder> UNWELCOME = new ArrayList<>();
	/** Furnaces a player under Cold Forge works ("dimension|pos" -> player), and crops one under Barren Hand planted. */
	private static final Map<String, UUID> COLD = new HashMap<>();
	private static final Set<String> BARREN = new HashSet<>();
	/** Ticks each cold furnace has run, for the share of them Cold Forge takes back. */
	private static final Map<String, Integer> COLD_TICKS = new HashMap<>();

	public static void clear() {
		LIGHTLESS.clear();
		UNWELCOME.clear();
		COLD.clear();
		BARREN.clear();
		COLD_TICKS.clear();
	}

	private static String key(Level level, BlockPos pos) {
		return level.dimension().identifier() + "|" + pos.asLong();
	}

	/** Every second: see {@link #refresh}. */
	public static void serverTick(MinecraftServer s) {
		if (s.getTickCount() % 20 == 0) {
			refresh(s);
		}
	}

	/** Who carries Lightless and Unwelcome now, and where. */
	public static void refresh(MinecraftServer s) {
		LIGHTLESS.clear();
		UNWELCOME.clear();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (p.isSpectator() || p.isCreative()) {
				continue;
			}
			PlayerData d = DataStore.peek(p.getUUID());
			if (d == null) {
				continue;
			}
			if (d.stacks(Penalty.LIGHTLESS) > 0) {
				LIGHTLESS.add(new Holder(p.level(), p.getX(), p.getZ()));
			}
			if (d.stacks(Penalty.UNWELCOME) > 0) {
				UNWELCOME.add(new Holder(p.level(), p.getX(), p.getZ()));
			}
		}
	}

	private static boolean near(List<Holder> holders, Level level, double x, double z, double range) {
		for (Holder h : holders) {
			if (h.level() == level && (h.x() - x) * (h.x() - x) + (h.z() - z) * (h.z() - z) <= range * range) {
				return true;
			}
		}
		return false;
	}

	/** Once a second, for every player (see AbyssalCurses.tick). */
	static void tick(ServerPlayer p, PlayerData d, ServerLevel level) {
		night(p, d, level);
		unmapped(p, d);
		unwelcome(p, d, level);
		wingshadow(p, d, level.isDarkOutside() && Dims.OVERWORLD.equals(Dims.of(p)));
		hero(p, d);
	}

	// ------------------------------------------------------------------ Lightless

	/** Light levels Lightless takes off a spawn spot near a player who carries it (see LightlessMixin), else 0. */
	public static int darker(ServerLevelAccessor level, BlockPos pos) {
		if (LIGHTLESS.isEmpty()) {
			return 0;
		}
		return near(LIGHTLESS, level.getLevel(), pos.getX() + 0.5, pos.getZ() + 0.5, Math.max(1, Config.ABYSSAL_LIGHTLESS_RANGE))
				? (int) Math.round(Math.max(0, Penalty.LIGHTLESS.value)) : 0;
	}

	/** Lightless' deed: a night in the Overworld kept awake through counts; a night slept in starts the count over. */
	private static void night(ServerPlayer p, PlayerData d, ServerLevel level) {
		if (d.stacks(Penalty.LIGHTLESS) <= 0) {
			d.nightDark = d.nightWatched = d.nightSlept = false;
			return;
		}
		boolean dark = level.getServer().overworld().isDarkOutside();
		if (dark) {
			d.nightDark = true;
			if (Dims.OVERWORLD.equals(Dims.of(p))) {
				d.nightWatched = true;
			}
			if (p.isSleeping()) {
				d.nightSlept = true;
			}
		} else if (d.nightDark) {
			dawn(p, d);
		}
	}

	/** A night is over: kept awake through in the Overworld it counts, slept in it undoes the count. */
	public static void dawn(ServerPlayer p, PlayerData d) {
		if (d.nightSlept) {
			if (AbyssalCurses.progress(d, Penalty.LIGHTLESS) > 0) {
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.abyssal.lightless_slept"), true);
			}
			AbyssalCurses.restart(p, d, Penalty.LIGHTLESS);
		} else if (d.nightWatched) {
			AbyssalCurses.advance(p, d, Penalty.LIGHTLESS, 1);
		}
		d.nightDark = d.nightWatched = d.nightSlept = false;
	}

	// ------------------------------------------------------------------ Unmapped

	/** Unmapped: the debug screen keeps no coordinates (the game's own reduced debug info, told to this player). */
	private static void unmapped(ServerPlayer p, PlayerData d) {
		boolean want = d.stacks(Penalty.UNMAPPED) > 0;
		if (want) {
			// (said again now and then: a respawn or a new world tells the client the world's own rule)
			if (!d.unmappedSent || d.tickCounter % 100 == 0) {
				p.connection.send(new ClientboundEntityEventPacket(p, REDUCED_DEBUG_ON));
				d.unmappedSent = true;
			}
		} else if (d.unmappedSent) {
			p.connection.send(new ClientboundEntityEventPacket(p, REDUCED_DEBUG_OFF));
			d.unmappedSent = false;
		}
	}

	/** Whether a map held by this entity stays blank (Unmapped, see UnmappedMapMixin). */
	public static boolean blankMaps(Entity entity) {
		if (!(entity instanceof ServerPlayer p) || p.isCreative()) {
			return false;
		}
		PlayerData d = DataStore.peek(p.getUUID());
		return d != null && d.stacks(Penalty.UNMAPPED) > 0;
	}

	// ------------------------------------------------------------------ Unwelcome

	/** Whether a door-breaking mob is close enough to a player under Unwelcome to break doors on any difficulty. */
	public static boolean unwelcome(Mob mob) {
		return !UNWELCOME.isEmpty() && near(UNWELCOME, mob.level(), mob.getX(), mob.getZ(), Math.max(1, Penalty.UNWELCOME.value));
	}

	/** Unwelcome: zombies close by learn to break doors; other monsters after the player open the doors in their way. */
	private static void unwelcome(ServerPlayer p, PlayerData d, ServerLevel level) {
		if (d.stacks(Penalty.UNWELCOME) <= 0) {
			return;
		}
		double r = Math.max(1, Penalty.UNWELCOME.value);
		for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(r), m -> m instanceof Enemy && m.isAlive())) {
			if (m instanceof Zombie z) {
				if (!z.canBreakDoors()) {
					z.setCanBreakDoors(true);
				}
			} else if (m.getTarget() == p) {
				openDoors(m, level);
			}
		}
	}

	/** The wooden doors right round a mob swing open. Returns how many. */
	public static int openDoors(Mob m, ServerLevel level) {
		int opened = 0;
		BlockPos at = m.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-1, 0, -1), at.offset(1, 1, 1))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.WOODEN_DOORS) && state.getBlock() instanceof DoorBlock door && !state.getValue(DoorBlock.OPEN)) {
				door.setOpen(m, level, state, pos.immutable(), true);
				opened++;
			}
		}
		return opened;
	}

	/** Unwelcome's deed: a raid won (the Hero of the Village comes over the player). */
	private static void hero(ServerPlayer p, PlayerData d) {
		boolean hero = p.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
		if (hero && !d.wasHero) {
			AbyssalCurses.advance(p, d, Penalty.UNWELCOME, 1);
		}
		d.wasHero = hero;
	}

	// ------------------------------------------------------------------ Cold Forge and Barren Hand

	/** A player used a block: a furnace worked under Cold Forge is marked, a crop planted under Barren Hand too. */
	public static InteractionResult onUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!(player instanceof ServerPlayer p) || p.isCreative()) {
			return InteractionResult.PASS;
		}
		PlayerData d = DataStore.peek(p.getUUID());
		if (d == null) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		if (d.stacks(Penalty.COLD_FORGE) > 0 && level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity) {
			COLD.put(key(level, pos), p.getUUID());
		}
		ItemStack held = p.getItemInHand(hand);
		if (d.stacks(Penalty.BARREN_HAND) > 0 && held.getItem() instanceof BlockItem item && item.getBlock() instanceof CropBlock) {
			BARREN.add(key(level, pos.relative(hit.getDirection())));
		}
		return InteractionResult.PASS;
	}

	/** Marks a furnace as worked by this player (Cold Forge), for the tests and the hook. */
	public static void chill(Level level, BlockPos pos, ServerPlayer p) {
		COLD.put(key(level, pos), p.getUUID());
	}

	/** Marks a crop as planted under Barren Hand, for the tests and the hook. */
	public static void sow(Level level, BlockPos pos) {
		BARREN.add(key(level, pos));
	}

	/** Whether a furnace is worked by a player still under Cold Forge (see ColdForgeMixin). */
	public static boolean cold(Level level, BlockPos pos) {
		if (COLD.isEmpty()) {
			return false;
		}
		UUID who = COLD.get(key(level, pos));
		if (who == null) {
			return false;
		}
		PlayerData d = DataStore.peek(who);
		if (d == null || d.owned(Penalty.COLD_FORGE) <= 0) {
			COLD.remove(key(level, pos));
			COLD_TICKS.remove(key(level, pos));
			return false;
		}
		return true;
	}

	/** Whether Cold Forge takes this furnace's cooking tick back (its share of them, spread over the furnace's own ticks). */
	public static boolean slowTick(Level level, BlockPos pos) {
		int n = COLD_TICKS.merge(key(level, pos), 1, Integer::sum);
		if (n > 1_000_000) {
			COLD_TICKS.put(key(level, pos), 0);
		}
		return Math.floorMod(n * 37L, 100L) < Penalty.COLD_FORGE.value;
	}

	/** Extra fuel a cold furnace burns this tick. */
	public static int extraFuel(long time) {
		double over = Math.max(0, Config.ABYSSAL_COLD_FORGE_FUEL - 1);
		int extra = (int) Math.floor(over);
		if (over - extra > 0 && Math.floorMod(time * 61L, 100L) < (over - extra) * 100) {
			extra++;
		}
		return extra;
	}

	/** Items taken out of a furnace by a player: Cold Forge's deed. */
	public static void smelted(Player player, int count) {
		if (player instanceof ServerPlayer p && count > 0) {
			AbyssalCurses.advance(p, DataStore.get(p.getUUID()), Penalty.COLD_FORGE, count);
		}
	}

	/** Whether a crop was planted by a player under Barren Hand (it stays barren until it is broken). */
	public static boolean barren(Level level, BlockPos pos) {
		return !BARREN.isEmpty() && BARREN.contains(key(level, pos));
	}

	/** Whether Barren Hand takes this growth or this bone meal (its share of them). */
	public static boolean withers(net.minecraft.util.RandomSource random) {
		return random.nextFloat() * 100 < Penalty.BARREN_HAND.value;
	}

	// ------------------------------------------------------------------ broken blocks: Barren Hand, Cave-In

	/** A player broke a block: a ripe crop counts for Barren Hand, deepslate for Cave-In, and deep stone may cave in. */
	public static void afterBreak(Level level, Player player, BlockPos pos, BlockState state) {
		if (!(player instanceof ServerPlayer p) || !(level instanceof ServerLevel server)) {
			return;
		}
		BARREN.remove(key(level, pos));
		PlayerData d = DataStore.get(p.getUUID());
		if (AbyssalCurses.carried(d) == 0) {
			return;
		}
		if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
			AbyssalCurses.advance(p, d, Penalty.BARREN_HAND, 1);
		}
		if (state.is(Blocks.DEEPSLATE)) {
			AbyssalCurses.advance(p, d, Penalty.CAVE_IN, 1);
		}
		if (d.stacks(Penalty.CAVE_IN) > 0 && !p.isCreative() && pos.getY() < Config.ABYSSAL_CAVE_IN_DEPTH
				&& state.is(BlockTags.BASE_STONE_OVERWORLD) && ThreadLocalRandom.current().nextDouble() < Penalty.CAVE_IN.frac()) {
			caveIn(server, pos.above());
		}
	}

	/** The block over a mined spot comes down, hurting what it lands on. Returns the falling block, or null if it holds. */
	public static FallingBlockEntity caveIn(ServerLevel level, BlockPos at) {
		BlockState over = level.getBlockState(at);
		if (over.isAir() || !over.getFluidState().isEmpty() || level.getBlockEntity(at) != null || over.getDestroySpeed(level, at) < 0) {
			return null;
		}
		FallingBlockEntity fall = FallingBlockEntity.fall(level, at, over);
		fall.setHurtsEntities(2.0f, 40);
		WorldFx.particleIn(level, "minecraft:ash", at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.4, 0.1, 0.4, 0.02, 20);
		level.playSound(null, at, net.minecraft.sounds.SoundEvents.DEEPSLATE_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.6f);
		return fall;
	}

	// ------------------------------------------------------------------ Wingshadow

	/** Wingshadow: by night the player is kept as one who has not slept for days, and the Phantoms come. */
	public static void wingshadow(ServerPlayer p, PlayerData d, boolean night) {
		if (!night || d.stacks(Penalty.WINGSHADOW) <= 0) {
			return;
		}
		Stat<?> rest = Stats.CUSTOM.get(Stats.TIME_SINCE_REST);
		int want = (int) Math.min(Integer.MAX_VALUE, Math.round(Math.max(1, Penalty.WINGSHADOW.value) * TICKS_A_DAY));
		if (p.getStats().getValue(rest) < want) {
			p.getStats().setValue(p, rest, want);
		}
	}

	// ------------------------------------------------------------------ kills: Wingshadow, Unwatched

	/** A player slew a creature: Phantoms count for Wingshadow, Thinned for Unwatched. */
	static void onKill(ServerPlayer killer, PlayerData d, LivingEntity victim) {
		if ("minecraft:phantom".equals(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString())) {
			AbyssalCurses.advance(killer, d, Penalty.WINGSHADOW, 1);
		}
		if (victim instanceof com.extremedimensions.seam.Thinned) {
			AbyssalCurses.advance(killer, d, Penalty.UNWATCHED, 1);
		}
	}

	/** Unwatched: whether a Thinned hunting this player moves this second though it is looked at. */
	public static boolean slipping(ServerPlayer prey, Entity thinned) {
		PlayerData d = DataStore.peek(prey.getUUID());
		if (d == null || d.stacks(Penalty.UNWATCHED) <= 0) {
			return false;
		}
		long second = thinned.level().getGameTime() / 20 + thinned.getId();
		return Math.floorMod(second * 2654435761L, 100L) < Penalty.UNWATCHED.value;
	}
}
