package com.extremedimensions.tribe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.relic.Relic;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.server.WorldFx;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Gifts and barter with the young of the tribe. A Hollowling takes a small, shiny gift (enough of it) and carries it
 * home, and the tribe thinks better of the giver. Once the tribe tolerates a player, a Hollowling barters with them:
 * a Tether Fragment always, a few wares that change every day, and for kin a relic and a Mending Cloth. Each ware can
 * be bought only so often a day; prices fall as the tribe comes to trust the player.
 */
public final class BarterService {
	private BarterService() {}

	/** Blocks within which a player can barter with a Hollowling. */
	public static final double REACH = 8;

	/** What the young take as a gift, and how many of it make one. */
	public static final Map<Item, Integer> LIKES = new LinkedHashMap<>();

	static {
		LIKES.put(Items.GOLD_NUGGET, 4);
		LIKES.put(Items.IRON_NUGGET, 6);
		LIKES.put(Items.AMETHYST_SHARD, 2);
		LIKES.put(Items.GLOW_BERRIES, 4);
		LIKES.put(Items.SWEET_BERRIES, 6);
		LIKES.put(Items.STRING, 4);
		LIKES.put(Items.FEATHER, 4);
		LIKES.put(Items.COPPER_INGOT, 2);
		LIKES.put(Items.LAPIS_LAZULI, 3);
		LIKES.put(Items.HONEYCOMB, 2);
		LIKES.put(Items.CANDLE, 1);
		LIKES.put(Items.EMERALD, 1);
		LIKES.put(Items.ECHO_SHARD, 1);
	}

	/** A ware: what the Hollowling wants (and how many, before discounts), what it gives, and how often a day. */
	public record Ware(int id, Item want, int price, Item give, int count, int uses) {
		/** What the ware gives (a relic is drawn when it is bought). */
		public ItemStack stack() {
			return id == RELIC ? RelicService.random() : new ItemStack(give, count);
		}
	}

	public static final int FRAGMENT = 100, RELIC = 101, CLOTH = 102;

	/** Wares a Hollowling may offer on a day (a few of them each day). */
	public static final List<Ware> DAILY = List.of(
			new Ware(0, Items.GOLD_NUGGET, 12, ModItems.WANDERERS_PAGE, 1, 2),
			new Ware(1, Items.AMETHYST_SHARD, 6, Items.SOUL_TORCH, 8, 3),
			new Ware(2, Items.GLOW_BERRIES, 10, Items.EXPERIENCE_BOTTLE, 4, 3),
			new Ware(3, Items.STRING, 16, Items.ECHO_SHARD, 1, 1),
			new Ware(4, Items.COPPER_INGOT, 8, Items.ENDER_PEARL, 2, 2),
			new Ware(5, Items.BONE, 20, Items.NAME_TAG, 1, 1),
			new Ware(6, Items.LAPIS_LAZULI, 6, Items.GLOW_INK_SAC, 3, 2),
			new Ware(7, Items.EMERALD, 3, Items.GOLDEN_APPLE, 1, 1),
			new Ware(8, Items.IRON_NUGGET, 18, Items.SOUL_LANTERN, 2, 2),
			new Ware(9, Items.FEATHER, 10, Items.CANDLE, 4, 2));

	/** A ware by id, with its price as set (the fragment's and the relic's come from the config). */
	public static Ware ware(int id) {
		return switch (id) {
			case FRAGMENT -> new Ware(FRAGMENT, Items.EMERALD, Math.max(1, Config.TRIBE_BARTER_FRAGMENT_PRICE), ModItems.TETHER_FRAGMENT, 1, 1);
			case RELIC -> new Ware(RELIC, Items.EMERALD, Math.max(1, Config.TRIBE_BARTER_RELIC_PRICE), RelicService.item(Relic.values()[0]), 1, 1);
			case CLOTH -> new Ware(CLOTH, Items.ECHO_SHARD, 4, ModItems.MENDING_CLOTH, 1, 1);
			default -> id >= 0 && id < DAILY.size() ? DAILY.get(id) : null;
		};
	}

	/** The day in the Overworld (by game time, so sleeping does not bring new wares). */
	private static long today() {
		var s = WorldFx.server();
		return s == null ? 0 : s.overworld().getGameTime() / 24000L;
	}

	/** Starts a new day for the player's wares if a day has passed. */
	private static void roll(PlayerData d) {
		long day = today();
		if (d.tribeDay != day) {
			d.tribeDay = day;
			d.barterUsed.clear();
		}
	}

	/** The wares offered to this player today, in order (the fragment first, kin's wares last). */
	public static List<Ware> offers(ServerPlayer p) {
		List<Ware> out = new ArrayList<>();
		out.add(ware(FRAGMENT));
		List<Ware> daily = new ArrayList<>(DAILY);
		Collections.shuffle(daily, new Random(p.getUUID().getLeastSignificantBits() * 31 + today()));
		out.addAll(daily.subList(0, Math.max(0, Math.min(daily.size(), Config.TRIBE_BARTER_OFFERS))));
		if (Standing.of(p) == Standing.Level.KIN) {
			if (Config.TRIBE_BARTER_RELIC_PRICE > 0) {
				out.add(ware(RELIC));
			}
			out.add(ware(CLOTH));
		}
		return out;
	}

	/** What a ware costs this player now. */
	public static int price(ServerPlayer p, Ware w) {
		double off = switch (Standing.of(p)) {
			case KIN -> Config.TRIBE_BARTER_KIN_DISCOUNT;
			case TRUSTED -> Config.TRIBE_BARTER_TRUSTED_DISCOUNT;
			default -> 0;
		};
		double k = Math.max(0, Config.TRIBE_BARTER_PRICE) * (1 - Math.max(0, Math.min(0.95, off)));
		return Math.max(1, (int) Math.ceil(w.price() * k));
	}

	/** How many more times today the player can buy this ware. */
	public static int left(ServerPlayer p, Ware w) {
		PlayerData d = DataStore.get(p.getUUID());
		roll(d);
		return Math.max(0, w.uses() - d.barterUsed.getOrDefault(w.id(), 0));
	}

	/** The offers as the barter screen reads them: "id:price:left;...". */
	public static String encode(ServerPlayer p) {
		StringBuilder out = new StringBuilder();
		for (Ware w : offers(p)) {
			if (out.length() > 0) {
				out.append(';');
			}
			out.append(w.id()).append(':').append(price(p, w)).append(':').append(left(p, w));
		}
		return out.toString();
	}

	/** Opens (or refreshes) the barter screen with this Hollowling. */
	public static void open(ServerPlayer p, Entity young) {
		ServerPlayNetworking.send(p, new ModPackets.BarterPayload(young.getId(), DataStore.get(p.getUUID()).tribeRep, encode(p)));
	}

	private static int count(ServerPlayer p, Item item) {
		var inv = p.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static void remove(ServerPlayer p, Item item, int n) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) {
				int take = Math.min(n, s.getCount());
				s.shrink(take);
				n -= take;
			}
		}
		inv.setChanged();
	}

	/** The barter screen's button: buys the ware if the Hollowling is still there, and shows the wares again. */
	public static void onTrade(ServerPlayer p, int young, int ware) {
		Entity e = p.level().getEntity(young);
		if (!com.extremedimensions.hollow.HollowService.canBarter(p, e)) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.barter.gone"), true);
			return;
		}
		String refused = trade(p, ware);
		if (refused != null) {
			p.sendSystemMessage(Component.translatable(refused), true);
		}
		open(p, e);
	}

	/** Buys a ware; returns null when it was bought, else why not (a message key). */
	public static String trade(ServerPlayer p, int id) {
		if (!Standing.atLeast(p, Standing.Level.TOLERATED) && !p.isCreative()) {
			return "gui.extremedimensions.barter.distrust";
		}
		Ware w = null;
		for (Ware o : offers(p)) {
			if (o.id() == id) {
				w = o;
			}
		}
		if (w == null) {
			return "gui.extremedimensions.barter.gone";
		}
		if (left(p, w) <= 0) {
			return "gui.extremedimensions.barter.sold_out";
		}
		int cost = price(p, w);
		if (count(p, w.want()) < cost) {
			return "gui.extremedimensions.barter.short";
		}
		remove(p, w.want(), cost);
		WorldFx.give(p, w.stack());
		PlayerData d = DataStore.get(p.getUUID());
		d.barterUsed.merge(w.id(), 1, Integer::sum);
		d.bartered++;
		WorldFx.soundSelf(p, "extremedimensions:hollowling_giggle", 0.9f, 1.2f);
		WorldFx.soundSelf(p, "minecraft:entity.item.pickup", 0.6f, 0.8f);
		Advancements.grant(p, Advancements.FIRST_BARTER);
		Journal.unlock(p, d, JournalEntry.BARTER);
		Standing.change(p, Config.TRIBE_REP_BARTER, "gui.extremedimensions.tribe.why.barter");
		DataStore.markDirty();
		return null;
	}

	/** How many of an item make one gift of it in this world (the usual amount times tribe_gift_cost); 0 if it is not liked. */
	public static int giftSize(ItemStack held) {
		Integer usual = LIKES.get(held.getItem());
		return usual == null ? 0 : usual * Math.max(1, Config.TRIBE_GIFT_COST);
	}

	/**
	 * A gift for a Hollowling: what it takes (as many as make one gift of it), or an empty stack if it takes nothing
	 * (not something it likes, not enough of it, or too soon after the last gift, each said to the player). The young
	 * take one gift each tribe_gift_minutes of play, so sleeping through the nights wins nothing.
	 */
	public static ItemStack gift(ServerPlayer p, ItemStack held) {
		int need = giftSize(held);
		if (need <= 0) {
			return ItemStack.EMPTY;
		}
		PlayerData d = DataStore.get(p.getUUID());
		if (held.getCount() < need) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.tribe.gift_more", need, held.getHoverName()), true);
			return ItemStack.EMPTY;
		}
		if (d.tribeGiftIn > 0) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.tribe.gift_wait", (d.tribeGiftIn + 59) / 60), true);
			return ItemStack.EMPTY;
		}
		ItemStack taken = held.copyWithCount(need);
		if (!p.isCreative()) {
			held.shrink(need);
		}
		d.tribeGiftIn = Math.max(0, Config.TRIBE_GIFT_MINUTES) * 60;
		Standing.change(p, Config.TRIBE_REP_GIFT * (taken.is(Items.ECHO_SHARD) ? 2 : 1), "gui.extremedimensions.tribe.why.gift");
		DataStore.markDirty();
		return taken;
	}
}
