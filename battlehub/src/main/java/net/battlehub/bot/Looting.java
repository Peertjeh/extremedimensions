package net.battlehub.bot;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.battlehub.core.LootPace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Bots loot chests as players do (core.LootPace): they open a chest they can see (its lid opens for everyone, with
 * the sound), take one stack at a time, close it again, and afterwards open their inventory to put on better armor
 * one piece at a time. At a chest and with the inventory open a bot stands still (BotBrain): nobody runs about with a
 * screen open. A hit or an enemy close by makes it close the screen and fight; it comes back to it a little later.
 */
public final class Looting {
	private Looting() {}

	/** Ticks a bot leaves chests alone after it had to stop (a hit, someone close). */
	static final int PAUSE = 60;
	/** Farther than this from the chest (eyes to its middle) and the chest closes. */
	private static final double REACH = 4.5;

	private static final class Session {
		final ServerLevel level;
		final BlockPos pos;
		final Runnable done;
		final long since;
		int wait, stacks;

		Session(ServerLevel level, BlockPos pos, Runnable done, int wait) {
			this.level = level;
			this.pos = pos;
			this.done = done;
			this.wait = wait;
			this.since = level.getGameTime();
		}
	}

	private static final Map<UUID, Session> AT = new HashMap<>();
	/** Ticks until a bot opens its inventory to put on armor (absent: nothing to put on). */
	private static final Map<UUID, Integer> DRESS = new HashMap<>();

	/** A bot with its inventory open, putting on armor: ticks to the next piece, and where it stood. */
	private static final class Dressing {
		int wait;
		final Vec3 at;
		final long since;

		Dressing(int wait, Vec3 at, long since) {
			this.wait = wait;
			this.at = at;
			this.since = since;
		}
	}

	private static final Map<UUID, Dressing> DRESSING = new HashMap<>();
	/** Each bot's last piece of armor: ticks the inventory was open for it, and how far the bot moved meanwhile (self-test). */
	private static final Map<UUID, double[]> LAST_DRESS = new HashMap<>();
	/** The game time until which a bot leaves chests alone. */
	private static final Map<UUID, Long> PAUSED = new HashMap<>();
	/** Each bot's last emptied chest: ticks it took and stacks it took (self-test). */
	private static final Map<UUID, int[]> LAST = new HashMap<>();
	private static final Random RNG = new Random();

	/** Whether this bot has a screen open now: a chest (it looks at it) or its inventory. It stands still. */
	public static boolean busy(ServerPlayer bot) {
		return AT.containsKey(bot.getUUID()) || DRESSING.containsKey(bot.getUUID());
	}

	/**
	 * Whether a bot can open this chest from where it stands: within reach of its eyes, with nothing in between (no
	 * looting through a wall).
	 */
	public static boolean canReach(ServerPlayer bot, BlockPos pos) {
		Vec3 eye = bot.getEyePosition(), mid = Vec3.atCenterOf(pos);
		if (eye.distanceToSqr(mid) > OPEN_REACH * OPEN_REACH) return false;
		net.minecraft.world.phys.BlockHitResult hit = bot.level().clip(new net.minecraft.world.level.ClipContext(eye, mid,
				net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, bot));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getBlockPos().equals(pos);
	}

	/** How close the eyes come to a chest's middle before a bot opens it (a player walks up to it). */
	private static final double OPEN_REACH = 3.5;

	/** The chest this bot is looting, or null. */
	public static BlockPos chest(ServerPlayer bot) {
		Session s = AT.get(bot.getUUID());
		return s == null ? null : s.pos;
	}

	/**
	 * A bot within reach of a chest with loot opens it; {@code done} runs once it has taken everything (or nothing
	 * more fits). Nothing happens while it is at another chest or just had to stop.
	 */
	public static void start(ServerPlayer bot, BlockPos pos, Runnable done) {
		if (!(bot.level() instanceof ServerLevel level) || AT.containsKey(bot.getUUID())) return;
		if (level.getGameTime() < PAUSED.getOrDefault(bot.getUUID(), 0L)) return;
		AT.put(bot.getUUID(), new Session(level, pos, done, LootPace.vary(pace(bot).open(), RNG)));
		lid(level, pos, true);
		// the right-click on the chest
		BotActions.swing(bot);
	}

	/**
	 * Every tick of a bot in a match: the next stack when it is time; after a chest, the inventory opened and one piece
	 * of armor after the other (only while {@code calm}: nobody puts armor on in the middle of a fight).
	 */
	static void tick(ServerPlayer bot, boolean calm) {
		Session s = AT.get(bot.getUUID());
		if (s != null) {
			if (bot.level() != s.level || !(s.level.getBlockEntity(s.pos) instanceof Container c)
					|| bot.getEyePosition().distanceToSqr(Vec3.atCenterOf(s.pos)) > REACH * REACH) {
				stop(bot, 0);
				return;
			}
			if (--s.wait > 0) return;
			if (!takeOne(bot, c)) {
				finish(bot, s);
				return;
			}
			s.stacks++;
			s.wait = LootPace.vary(pace(bot).item(), RNG);
			return;
		}
		Dressing dr = DRESSING.get(bot.getUUID());
		if (dr != null) {
			if (!calm) {
				// the inventory closes; it tries again once things are quiet
				DRESSING.remove(bot.getUUID());
				DRESS.put(bot.getUUID(), PAUSE);
				return;
			}
			if (--dr.wait > 0) return;
			wearOne(bot);
			LAST_DRESS.put(bot.getUUID(), new double[] {bot.level().getGameTime() - dr.since,
					Math.hypot(bot.getX() - dr.at.x, bot.getZ() - dr.at.z)});
			if (upgrade(bot) >= 0) {
				DRESSING.put(bot.getUUID(), new Dressing(LootPace.vary(pace(bot).equip(), RNG), bot.position(), bot.level().getGameTime()));
			} else {
				DRESSING.remove(bot.getUUID());
			}
			return;
		}
		Integer d = DRESS.get(bot.getUUID());
		if (d == null || !calm) return;
		if (d > 0) {
			DRESS.put(bot.getUUID(), d - 1);
			return;
		}
		DRESS.remove(bot.getUUID());
		if (upgrade(bot) >= 0) {
			// the inventory opens: finding the piece and shift-clicking it takes a moment, standing still
			DRESSING.put(bot.getUUID(), new Dressing(LootPace.vary(pace(bot).equip(), RNG), bot.position(), bot.level().getGameTime()));
		}
	}

	/** One shift-click: the first stack of the chest that (partly) fits; false when the chest is empty or nothing fits. */
	private static boolean takeOne(ServerPlayer bot, Container c) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack st = c.getItem(i);
			if (st.isEmpty()) continue;
			ItemStack rest = st.copy();
			bot.getInventory().add(rest);
			if (rest.getCount() == st.getCount()) continue;
			c.setItem(i, rest.isEmpty() ? ItemStack.EMPTY : rest);
			c.setChanged();
			return true;
		}
		return false;
	}

	private static void finish(ServerPlayer bot, Session s) {
		AT.remove(bot.getUUID());
		LAST.put(bot.getUUID(), new int[] {(int) (s.level.getGameTime() - s.since), s.stacks});
		lid(s.level, s.pos, false);
		s.done.run();
		// then, a moment later, into the inventory to put on what it found, one piece after the other
		DRESS.put(bot.getUUID(), LootPace.vary(pace(bot).open(), RNG));
	}

	/** The bot closes the chest without finishing it, and leaves chests alone for {@code pause} ticks. */
	public static void stop(ServerPlayer bot, int pause) {
		Session s = AT.remove(bot.getUUID());
		if (s != null) lid(s.level, s.pos, false);
		if (pause > 0 && bot.level() instanceof ServerLevel level) PAUSED.put(bot.getUUID(), level.getGameTime() + pause);
	}

	/** A bot out of its match (or gone): nothing open, nothing to put on. */
	static void forget(ServerPlayer bot) {
		stop(bot, 0);
		DRESS.remove(bot.getUUID());
		DRESSING.remove(bot.getUUID());
		PAUSED.remove(bot.getUUID());
	}

	/** The last chest this bot emptied: {ticks it took, stacks it took}, or null (self-test). */
	public static int[] lastChest(ServerPlayer bot) {
		return LAST.get(bot.getUUID());
	}

	/** The last piece of armor this bot put on: {ticks its inventory was open, blocks it moved meanwhile}, or null (self-test). */
	public static double[] lastDress(ServerPlayer bot) {
		return LAST_DRESS.get(bot.getUUID());
	}

	static void clear() {
		LAST.clear();
		LAST_DRESS.clear();
		AT.clear();
		DRESS.clear();
		DRESSING.clear();
		PAUSED.clear();
	}

	private static LootPace.Pace pace(ServerPlayer bot) {
		return LootPace.of(bot instanceof BotPlayer b ? b.difficulty.ordinal() : 2);
	}

	/** The chest's lid opens or closes for everyone who sees it, with its sound. */
	private static void lid(ServerLevel level, BlockPos pos, boolean open) {
		level.blockEvent(pos, level.getBlockState(pos).getBlock(), 1, open ? 1 : 0);
		level.playSound(null, pos, open ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5f,
				level.getRandom().nextFloat() * 0.1f + 0.9f);
	}

	/** One armor piece from the inventory into an empty armor slot, or over a weaker one; false when there is none. */
	public static boolean wearOne(ServerPlayer bot) {
		int i = upgrade(bot);
		if (i < 0) return false;
		Inventory inv = bot.getInventory();
		ItemStack st = inv.getItem(i);
		EquipmentSlot slot = bot.getEquipmentSlotForItem(st);
		ItemStack worn = bot.getItemBySlot(slot);
		bot.setItemSlot(slot, st.copy());
		inv.setItem(i, worn.isEmpty() ? ItemStack.EMPTY : worn.copy());
		return true;
	}

	/** The inventory slot of a piece of armor better than what the bot wears there, or -1. */
	private static int upgrade(ServerPlayer bot) {
		Inventory inv = bot.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.isEmpty()) continue;
			EquipmentSlot slot = bot.getEquipmentSlotForItem(st);
			if (!slot.isArmor()) continue;
			ItemStack worn = bot.getItemBySlot(slot);
			if (!worn.isEmpty() && armorRank(worn) >= armorRank(st)) continue;
			return i;
		}
		return -1;
	}

	/** Leather 1 .. netherite 6, 0 for anything else. */
	static int armorRank(ItemStack st) {
		String id = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
		String[] order = {"leather", "golden", "chainmail", "iron", "diamond", "netherite"};
		for (int i = order.length - 1; i >= 0; i--) {
			if (id.startsWith(order[i])) return i + 1;
		}
		return 0;
	}
}
