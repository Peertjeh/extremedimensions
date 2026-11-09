package com.extremedimensions.reach;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.lore.Loot;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.WorldFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Home or Lock (3.44): the End's own ending. The Pale Mason, slain, leaves the two things he never set down: the
 * Homeward Keystone, the last stone of the Endermen's door home, and the Pale Lock, that shuts it for good. Set in the
 * fitting of an Unfinished Anchor that has been attuned, the first of the two decides the End for the whole world:
 *
 * <ul>
 * <li><b>Home</b>: the anchor is finished, a door opens in it and the Endermen walk home through it. Afterwards the
 * Endermen of the End let a gaze pass ({@code pale_home_calm}), and the Pale Haulers and the Pale Squalls come less
 * often ({@code pale_home_quiet}). They leave their thanks at the fitting.</li>
 * <li><b>Lock</b>: the anchor is sealed and breaks apart, and the hoard they put by for the way home spills out.
 * Afterwards the Endermen near a player in the End turn on them unprovoked ({@code pale_lock_hostile_chance}), and the
 * Haulers and the Squalls come more often ({@code pale_lock_wrath}).</li>
 * </ul>
 *
 * The other one only crumbles once the End has its ending. The choice is kept with the world among the Reach's marks.
 */
public final class HomeOrLock {
	private HomeOrLock() {}

	public static final String HOME = "home", LOCK = "lock";
	/** The mark the choice is kept under, among the Reach's (see {@link DataStore#reach()}). */
	private static final String MARK = "choice@";

	/** The End's ending: "" until one of the two is set, then {@link #HOME} or {@link #LOCK}. */
	public static String choice() {
		for (String m : DataStore.reach()) {
			if (m.startsWith(MARK)) {
				return m.substring(MARK.length());
			}
		}
		return "";
	}

	public static boolean home() {
		return HOME.equals(choice());
	}

	public static boolean locked() {
		return LOCK.equals(choice());
	}

	/** Sets the End's ending ("" to undo it, for operators and the tests). */
	public static void set(String c) {
		DataStore.reach().removeIf(m -> m.startsWith(MARK));
		if (c != null && !c.isEmpty()) {
			DataStore.reach().add(MARK + c);
		}
		DataStore.markDirty();
	}

	/** Whether this is one of the two. */
	public static boolean holds(ItemStack s) {
		return s.is(ModItems.HOMEWARD_KEYSTONE) || s.is(ModItems.PALE_LOCK);
	}

	/** What the Pale Mason leaves besides his spoils: the two, while the End has no ending yet; nothing after. */
	public static List<ItemStack> spoils() {
		if (!choice().isEmpty()) {
			return List.of();
		}
		return List.of(new ItemStack(ModItems.HOMEWARD_KEYSTONE), new ItemStack(ModItems.PALE_LOCK));
	}

	/**
	 * One of the two used on a block: on the attuned fitting of an Unfinished Anchor it decides the End; anywhere else
	 * the player is told where it goes. Once the End has its ending, it only crumbles.
	 */
	public static InteractionResult onUse(ServerPlayer p, ServerLevel level, BlockPos pos, ItemStack held) {
		boolean home = held.is(ModItems.HOMEWARD_KEYSTONE);
		if (!choice().isEmpty()) {
			// The End has its ending: the other one is dust
			held.shrink(1);
			WorldFx.particles(p, "minecraft:large_smoke", 1.0, 0.3, 0.02, 12);
			WorldFx.soundSelf(p, "minecraft:block.stone.break", 1.0f, 0.5f);
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.reach.choice_made").withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.SUCCESS;
		}
		if (!PaleReach.isFitting(level, pos)) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.reach.choice_fitting").withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.SUCCESS;
		}
		if (!PaleReach.isAttuned(level, pos)) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.reach.choice_attune").withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.SUCCESS;
		}
		if (!p.isCreative()) {
			held.shrink(1);
		}
		choose(level, pos, p, home);
		return InteractionResult.SUCCESS;
	}

	/** The End's ending is chosen at this fitting (by {@code by}, or by an operator: null). */
	public static void choose(ServerLevel level, BlockPos fitting, ServerPlayer by, boolean home) {
		set(home ? HOME : LOCK);
		if (home) {
			AnchorRites.home(level, fitting);
			spill(level, fitting, Loot.PALE_THANKS);
		} else {
			AnchorRites.lock(level, fitting);
			spill(level, fitting, Loot.PALE_HOARD);
		}
		String dim = level.dimension().identifier().toString();
		WorldFx.soundIn(dim, home ? "minecraft:block.end_portal.spawn" : "minecraft:block.respawn_anchor.deplete", "blocks", fitting.getX() + 0.5,
				fitting.getY() + 1, fitting.getZ() + 0.5, 3f, home ? 1f : 0.5f);
		MinecraftServer s = level.getServer();
		for (ServerPlayer q : s.getPlayerList().getPlayers()) {
			q.sendSystemMessage(Component.translatable(home ? "gui.extremedimensions.reach.chosen_home" : "gui.extremedimensions.reach.chosen_lock")
					.withStyle(home ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_PURPLE));
		}
		if (by != null) {
			Advancements.grant(by, home ? Advancements.PALE_HOME : Advancements.PALE_LOCKED);
			Journal.unlock(by, DataStore.get(by.getUUID()), JournalEntry.LAST_STONE);
		}
	}

	/** Their thanks (home) or their hoard (lock) bursts out of the fitting. */
	private static void spill(ServerLevel level, BlockPos fitting, String table) {
		Vec3 at = new Vec3(fitting.getX() + 0.5, fitting.getY() + 1.2, fitting.getZ() + 0.5);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (ItemStack stack : Loot.roll(level, table, at)) {
			ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack);
			item.setDeltaMovement(rnd.nextDouble(-0.15, 0.15), 0.3 + rnd.nextDouble(0.2), rnd.nextDouble(-0.15, 0.15));
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
	}

	/** How often the Pale Haulers and the Pale Squalls come now, as a share of before: fewer once home, more once locked. */
	public static double weather() {
		String c = choice();
		if (HOME.equals(c)) {
			return Math.max(0, Config.PALE_HOME_QUIET);
		}
		if (LOCK.equals(c)) {
			return Math.max(1, Config.PALE_LOCK_WRATH);
		}
		return 1;
	}

	/** Once a second: the Endermen of the End, calmed or turned by the ending. */
	public static void tick(MinecraftServer s) {
		if (s.getTickCount() % 20 != 11) {
			return;
		}
		String c = choice();
		if (c.isEmpty()) {
			return;
		}
		ServerLevel end = s.getLevel(Level.END);
		if (end == null) {
			return;
		}
		for (ServerPlayer p : end.players()) {
			if (HOME.equals(c)) {
				calm(p);
			} else {
				turn(p);
			}
		}
	}

	/** Gone home: an Enderman that took a gaze for a slight lets it pass (one the player struck stays angry). */
	public static int calm(ServerPlayer p) {
		if (Config.PALE_HOME_CALM <= 0) {
			return 0;
		}
		int n = 0;
		for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48), e -> e.getType() == EntityType.ENDERMAN)) {
			if (m.getTarget() != p || m.getLastHurtByMob() == p) {
				continue;
			}
			m.setTarget(null);
			if (m instanceof NeutralMob angry) {
				angry.stopBeingAngry();
			}
			n++;
		}
		return n;
	}

	/**
	 * Locked out: now and then an Enderman near a player turns on them unprovoked (never on a creative or spectating
	 * player, nor in peace).
	 */
	public static int turn(ServerPlayer p) {
		if (p.isCreative() || p.isSpectator() || p.level().getDifficulty() == Difficulty.PEACEFUL || Config.PALE_LOCK_HOSTILE_CHANCE <= 0) {
			return 0;
		}
		int n = 0;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double range = Math.max(4, Config.PALE_LOCK_HOSTILE_RANGE);
		for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(range), e -> e.getType() == EntityType.ENDERMAN)) {
			if (m.getTarget() != null || m.distanceToSqr(p) > range * range || rnd.nextDouble() >= Config.PALE_LOCK_HOSTILE_CHANCE) {
				continue;
			}
			m.setLastHurtByMob(p);
			m.setTarget(p);
			if (m instanceof NeutralMob angry) {
				angry.startPersistentAngerTimer();
			}
			n++;
		}
		return n;
	}
}
