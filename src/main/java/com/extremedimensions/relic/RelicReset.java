package com.extremedimensions.relic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.WorldFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Starting the relics over in a world that already has them, for admins: {@code /tether relic_reset confirm}. At once
 * the world's relic epoch goes up by one, every player the world knows (online or not) loses the relic bound to their
 * Tether, and every relic item made before then crumbles to dust as soon as a player comes across it: in their bag,
 * their armour or their off hand, in their ender chest, or in the container they have open. Relics found from then on
 * serve as ever. The Seam is reset apart from this (see {@code seam.SeamReset}).
 *
 * <p>An old relic is told from a new one by a stamp: while the epoch is above 0, every relic made carries it in its
 * custom data ({@link #KEY}), and one with no stamp or a lower one is from before the last reset. While the epoch is 0
 * (a world never reset) nothing is stamped and nothing crumbles, so such a world plays exactly as it always did.
 *
 * <p>Where the stamp is put on: relics are made in many places (finds, barters, spoils, the Held's chamber, an admin's
 * /give, another mod's loot), and one place missed would crumble a relic just found. So the stamp goes on in one place,
 * as a stack is made out of nothing but an item and a count (see {@code mixin.RelicStampMixin}): that is how a new
 * stack comes to be, whoever makes it. A stack read from a save or the network, or copied or split off another, is made
 * from its item and its components through another constructor, and so keeps whatever stamp it had: an old relic never
 * turns new by being saved, sent or moved. The mod's own relics ({@link RelicService#stack}) and whatever a loot table
 * gives are stamped here as well, so that they stay right should the game ever make its stacks another way; a stamp
 * put on twice is the same stamp.
 */
public final class RelicReset {
	private RelicReset() {}

	/** The relic epoch a relic was made in, in its custom data (an int; missing = made before any reset). */
	public static final String KEY = "extremedimensions_relic_epoch";

	/** The relic items, looked up as stacks are made (built the first time it is asked, long after they are registered). */
	private static volatile Set<Item> relics;

	/** How many times this world's relics were started over (0 = never). */
	public static int epoch() {
		return DataStore.relicEpoch();
	}

	private static boolean relic(ItemStack s) {
		Set<Item> known = relics;
		if (known == null) {
			known = Set.copyOf(ModItems.RELICS.values());
			relics = known;
		}
		return !s.isEmpty() && known.contains(s.getItem());
	}

	// ------------------------------------------------------------------ the stamp

	/**
	 * A stack just made, once the relics were reset (from the ItemStack constructor, see RelicStampMixin, which asks for
	 * the epoch first: it is 0 in a world never reset, and on a client playing on a server, which keeps no world data).
	 * A relic is stamped.
	 */
	public static void made(ItemStack s) {
		stamp(s);
	}

	/** Stamps a relic with the world's epoch now (nothing while the epoch is 0, nor on anything else). Returns the stack. */
	public static ItemStack stamp(ItemStack s) {
		int now = epoch();
		if (now > 0 && relic(s) && stampOf(s) != now) {
			CustomData had = s.get(DataComponents.CUSTOM_DATA);
			CompoundTag tag = had == null ? new CompoundTag() : had.copyTag();
			tag.putInt(KEY, now);
			s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		}
		return s;
	}

	/** The epoch a stack was made in (0: not stamped). */
	public static int stampOf(ItemStack s) {
		CustomData had = s.get(DataComponents.CUSTOM_DATA);
		return had == null ? 0 : had.copyTag().getIntOr(KEY, 0);
	}

	/** Whether this is a relic from before the last reset of the relics (never while the world was not reset). */
	public static boolean old(ItemStack s) {
		int now = epoch();
		return now > 0 && relic(s) && stampOf(s) < now;
	}

	/** What a loot table just gave (any table, a data pack's too): the relics among it are new ones. */
	public static void found(List<ItemStack> drops) {
		if (epoch() > 0) {
			for (ItemStack s : drops) {
				stamp(s);
			}
		}
	}

	// ------------------------------------------------------------------ the reset

	/** What a reset did: the new epoch, the bound relics wiped and the relic items that crumbled at once. */
	public record Result(int epoch, int wiped, int crumbled) {}

	/**
	 * Starts the relics over, at once: the epoch goes up by one, every player's bound relic is wiped, and the old relics
	 * players online carry (or have open in front of them) crumble now; the rest as players come across them.
	 */
	public static Result reset(MinecraftServer server, String by) {
		int epoch = epoch() + 1;
		DataStore.setRelicEpoch(epoch);
		// What those online had bound, to tell them
		List<ServerPlayer> online = List.copyOf(server.getPlayerList().getPlayers());
		Map<UUID, Relic> had = new HashMap<>();
		for (ServerPlayer p : online) {
			had.put(p.getUUID(), RelicService.bound(DataStore.get(p.getUUID())));
		}
		int wiped = 0;
		for (PlayerData d : DataStore.players()) {
			if (d.relic != null) {
				wiped++;
			}
			wipe(d);
		}
		int crumbled = 0;
		for (ServerPlayer p : online) {
			PlayerData d = DataStore.get(p.getUUID());
			RelicService.forget(p.getUUID());
			// (what the relic gave and took stops now: its health, reach and armour, the candle's light)
			CurseEffects.applyEffects(p, d);
			Sync.curses(p, d, false);
			Relic r = had.get(p.getUUID());
			p.sendSystemMessage((r != null
					? Component.translatable("gui.extremedimensions.relic.reset.wiped", Component.translatable(r.nameKey()))
					: Component.translatable("gui.extremedimensions.relic.reset.told")).withStyle(ChatFormatting.GOLD));
			crumbled += scan(p);
		}
		DataStore.markDirty();
		ExtremeDimensions.LOGGER.info("{} reset the relics (epoch {}): {} bound relics wiped, {} relic items crumbled at once; the rest crumble as players come across them",
				by, epoch, wiped, crumbled);
		return new Result(epoch, wiped, crumbled);
	}

	/**
	 * A player's bound relic is gone, and with it what a bound relic keeps: the Last Stitch's and the Held's Chain's
	 * once-an-expedition, the Unbound Pinion's glide and the Veil Scale's drift. A hold of the Held's Chain under way
	 * runs out its few seconds (ending it now would be a Collapse on the spot), and what the Ferryman's Obol kept from a
	 * death still comes back on respawn: those things are the player's, not the relic's.
	 */
	private static void wipe(PlayerData d) {
		d.relic = null;
		d.stitchSpent = false;
		d.heldSpent = false;
		d.pinionGlide = 0;
		d.veilDrift = false;
	}

	// ------------------------------------------------------------------ crumbling

	/**
	 * Once a second for every player online, in every world (another mod's too), and at once as they join: the old
	 * relics they come across crumble to dust. That is their bag with its armour and off hand, what they hold on the
	 * cursor, their ender chest and the container they have open; a relic dropped on the ground waits until it is picked
	 * up. Nothing at all while the world was never reset. A spectator is passed over; a player in creative takes what
	 * relic they like from the creative tab (made by their own game, which on a server knows nothing of the reset), so the
	 * relics they carry are stamped anew instead. Returns how many crumbled.
	 */
	public static int scan(ServerPlayer p) {
		if (epoch() <= 0 || p.isSpectator()) {
			return 0;
		}
		Inventory inv = p.getInventory();
		AbstractContainerMenu menu = p.containerMenu;
		if (p.isCreative()) {
			for (int i = 0; i < inv.getContainerSize(); i++) {
				stamp(inv.getItem(i));
			}
			stamp(menu.getCarried());
			return 0;
		}
		int n = crumble(inv) + crumble(p.getEnderChestInventory());
		if (old(menu.getCarried())) {
			n += menu.getCarried().getCount();
			menu.setCarried(ItemStack.EMPTY);
		}
		for (Slot slot : menu.slots) {
			// (the menu's own slots: the bag's are done above)
			if (slot.container != inv && old(slot.getItem())) {
				n += slot.getItem().getCount();
				slot.set(ItemStack.EMPTY);
			}
		}
		if (n > 0) {
			menu.broadcastChanges();
			crumbled(p, n);
		}
		return n;
	}

	private static int crumble(Container c) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (old(s)) {
				n += s.getCount();
				c.setItem(i, ItemStack.EMPTY);
			}
		}
		return n;
	}

	/**
	 * Binding a relic: an old one crumbles in the hand instead, and binds nothing (true). A player in creative is never
	 * refused (see {@link #scan}): theirs is stamped anew.
	 */
	public static boolean refused(ServerPlayer p, ItemStack stack) {
		if (p.isCreative()) {
			stamp(stack);
			return false;
		}
		if (!old(stack)) {
			return false;
		}
		int n = stack.getCount();
		stack.shrink(n);
		crumbled(p, n);
		return true;
	}

	/** Old relics crumbled: a quiet crack, a little smoke, and one line on the action bar saying why. */
	private static void crumbled(ServerPlayer p, int n) {
		WorldFx.soundSelf(p, "minecraft:block.amethyst_block.break", 0.5f, 0.6f);
		WorldFx.particles(p, "minecraft:smoke", 1.0, 0.3, 0.01, 8);
		p.sendSystemMessage((n == 1 ? Component.translatable("gui.extremedimensions.relic.reset.crumbled.one")
				: Component.translatable("gui.extremedimensions.relic.reset.crumbled.many", n)).withStyle(ChatFormatting.GRAY), true);
	}
}
