package com.extremedimensions.coop;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;

/**
 * Share the burden: sneaking, a player right-clicks an ally with an empty hand and sees the curses they carry. They
 * can offer to carry one stack of one of them; the ally is asked (in the chat) and, if they let them, the stack comes
 * off the ally and onto the one who offered (with {@code coop_burden_toll} stacks more as the toll of carrying it).
 * Pact stacks stay with whoever swore them. The same two players can do it again after
 * {@code coop_burden_cooldown_minutes} (remembered across restarts), the carrier must have room for the stack and its
 * toll, and no one is relieved of more than {@code coop_burden_relief_per_day} stacks a day, however many friends (or
 * second accounts) offer.
 */
public final class BurdenService {
	private BurdenService() {}

	/** Blocks within which the two must stand for a curse to be carried over. */
	private static final double REACH = 8;
	/** Ticks an offer waits for an answer. */
	private static final int OFFER_TICKS = 60 * 20;

	private record Offer(UUID carrier, String curse, long until) {}

	/** Offers waiting for an answer, by the ally who is asked. */
	private static final Map<UUID, Offer> OFFERS = new HashMap<>();
	public static void clear() {
		OFFERS.clear();
	}

	/** Sneak and an empty hand on an ally: their curses, to carry one for them. */
	public static InteractionResult interact(ServerPlayer p, Entity target) {
		if (!(target instanceof ServerPlayer friend) || !p.isShiftKeyDown() || !p.getMainHandItem().isEmpty() || !Dims.ours(p)
				|| FallenService.fallen(friend) || FallenService.fallen(p) || !Allies.allied(p, friend)) {
			return InteractionResult.PASS;
		}
		if (Config.COOP_BURDEN <= 0) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.burden.off"), true);
			return InteractionResult.SUCCESS;
		}
		ServerPlayNetworking.send(p, new ModPackets.BurdenPayload(friend.getUUID(), friend.getScoreboardName(), list(p, friend)));
		return InteractionResult.SUCCESS;
	}

	/** "id:stacks:max:mine;..." for every curse the ally carries that could be carried for them. */
	static String list(ServerPlayer p, ServerPlayer friend) {
		PlayerData mine = DataStore.get(p.getUUID()), theirs = DataStore.get(friend.getUUID());
		StringBuilder out = new StringBuilder();
		for (Penalty pen : Penalty.values()) {
			int n = theirs.owned(pen);
			if (n <= 0 || pen.abyssal) {
				// (an Abyssal curse is no burden a friend can take up)
				continue;
			}
			if (out.length() > 0) {
				out.append(';');
			}
			out.append(pen.id).append(':').append(n).append(':').append(pen.max).append(':').append(mine.owned(pen))
					.append(':').append(com.extremedimensions.penalty.CurseRunes.readable(mine, pen) ? 1 : 0);
		}
		return out.toString();
	}

	/** The carrier offers to carry one stack of an ally's curse: the ally is asked. */
	public static void offer(ServerPlayer p, UUID friendId, String curseId) {
		MinecraftServer s = WorldFx.server();
		ServerPlayer friend = s == null ? null : s.getPlayerList().getPlayer(friendId);
		Penalty pen = Penalty.byId(curseId);
		if (friend == null || pen == null || Config.COOP_BURDEN <= 0) {
			return;
		}
		String refused = check(p, friend, pen);
		if (refused != null) {
			p.sendSystemMessage(Component.translatable(refused, friend.getDisplayName(), cooldownLeft(p, friend)), true);
			return;
		}
		OFFERS.put(friend.getUUID(), new Offer(p.getUUID(), pen.id, s.overworld().getGameTime() + OFFER_TICKS));
		Component curse = Component.translatable(pen.nameKey());
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.burden.asked", curse, friend.getDisplayName()));
		MutableComponent ask = Component.translatable("gui.extremedimensions.burden.offer", p.getDisplayName(), curse);
		ask.append(Component.literal(" "));
		ask.append(button("gui.extremedimensions.burden.accept", "/tether burden accept", ChatFormatting.GREEN));
		ask.append(Component.literal(" "));
		ask.append(button("gui.extremedimensions.burden.refuse", "/tether burden refuse", ChatFormatting.RED));
		friend.sendSystemMessage(ask);
		WorldFx.soundSelf(friend, "minecraft:block.note_block.chime", 0.8f, 1.2f);
	}

	private static Component button(String key, String command, ChatFormatting colour) {
		return Component.translatable(key).withStyle(Style.EMPTY.withColor(colour).withBold(true)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
	}

	/** Why a curse cannot be carried over now (a message key), or null if it can. */
	private static String check(ServerPlayer carrier, ServerPlayer friend, Penalty pen) {
		if (!Allies.allied(carrier, friend) || FallenService.fallen(carrier) || FallenService.fallen(friend)
				|| carrier.level() != friend.level() || carrier.distanceToSqr(friend) > REACH * REACH) {
			return "gui.extremedimensions.burden.failed";
		}
		if (pen.abyssal || DataStore.get(friend.getUUID()).owned(pen) <= 0
				|| DataStore.get(carrier.getUUID()).owned(pen) + 1 + Math.max(0, Config.COOP_BURDEN_TOLL) > pen.max) {
			return "gui.extremedimensions.burden.failed";
		}
		if (cooldownLeft(carrier, friend) > 0) {
			return "gui.extremedimensions.burden.cooldown";
		}
		if (reliefsLeft(friend) <= 0) {
			return "gui.extremedimensions.burden.spent";
		}
		return null;
	}

	/** The Overworld day (of 24000 ticks) it is now. */
	private static long day() {
		MinecraftServer s = WorldFx.server();
		return s == null ? 0 : s.overworld().getGameTime() / 24000L;
	}

	/** Stacks this player may still be relieved of today. */
	public static int reliefsLeft(ServerPlayer friend) {
		PlayerData d = DataStore.get(friend.getUUID());
		int used = d.reliefDay == day() ? d.reliefs : 0;
		return Math.max(0, Config.COOP_BURDEN_RELIEF_PER_DAY - used);
	}

	/** Whole minutes before this carrier may carry for this ally again (0: now). */
	private static int cooldownLeft(ServerPlayer carrier, ServerPlayer friend) {
		Map<String, Long> carried = DataStore.get(carrier.getUUID()).carriedFor;
		Long last = carried == null ? null : carried.get(friend.getStringUUID());
		MinecraftServer s = WorldFx.server();
		if (last == null || s == null) {
			return 0;
		}
		long left = last + Math.max(0, Config.COOP_BURDEN_COOLDOWN_MINUTES) * 1200L - s.overworld().getGameTime();
		return left <= 0 ? 0 : (int) ((left + 1199) / 1200);
	}

	/** The asked ally answers (from the chat buttons). */
	public static void answer(ServerPlayer friend, boolean yes) {
		Offer o = OFFERS.remove(friend.getUUID());
		MinecraftServer s = WorldFx.server();
		if (o == null || s == null || s.overworld().getGameTime() > o.until()) {
			friend.sendSystemMessage(Component.translatable("gui.extremedimensions.burden.expired"), true);
			return;
		}
		ServerPlayer carrier = s.getPlayerList().getPlayer(o.carrier());
		Penalty pen = Penalty.byId(o.curse());
		if (carrier == null || pen == null) {
			friend.sendSystemMessage(Component.translatable("gui.extremedimensions.burden.expired"), true);
			return;
		}
		if (!yes) {
			carrier.sendSystemMessage(Component.translatable("gui.extremedimensions.burden.refused", friend.getDisplayName()));
			return;
		}
		String refused = check(carrier, friend, pen);
		if (refused != null) {
			Component msg = Component.translatable(refused, friend.getDisplayName(), cooldownLeft(carrier, friend));
			carrier.sendSystemMessage(msg);
			friend.sendSystemMessage(msg);
			return;
		}
		carry(carrier, friend, pen);
	}

	/** One stack goes from the ally to the carrier (and the toll on top). */
	public static void carry(ServerPlayer carrier, ServerPlayer friend, Penalty pen) {
		PlayerData from = DataStore.get(friend.getUUID()), to = DataStore.get(carrier.getUUID());
		if (!from.removeStack(pen)) {
			return;
		}
		to.addStacks(pen, 1 + Math.max(0, Config.COOP_BURDEN_TOLL));
		to.burdensCarried++;
		MinecraftServer s = WorldFx.server();
		if (s != null) {
			long now = s.overworld().getGameTime();
			if (to.carriedFor == null) {
				to.carriedFor = new HashMap<>();
			}
			// Only waits still running are worth keeping
			long wait = Math.max(0, Config.COOP_BURDEN_COOLDOWN_MINUTES) * 1200L;
			to.carriedFor.values().removeIf(t -> t + wait < now);
			to.carriedFor.put(friend.getStringUUID(), now);
		}
		long today = day();
		from.reliefs = from.reliefDay == today ? from.reliefs + 1 : 1;
		from.reliefDay = today;
		DataStore.markDirty();
		CurseEffects.applyEffects(friend, from);
		CurseEffects.applyEffects(carrier, to);
		// A thread of light carries it from one to the other
		com.extremedimensions.elder.AttackFx.thread(friend.level(), friend.getX(), friend.getY() + 1.2, friend.getZ(),
				carrier.getX(), carrier.getY() + 1.2, carrier.getZ(), 16);
		WorldFx.sound(friend, "minecraft:block.beacon.power_select", 0.8f, 1.5f);
		WorldFx.sound(carrier, "minecraft:block.chain.place", 1.0f, 0.6f);
		WorldFx.particles(friend, "extremedimensions:soul_mote", 1.0, 0.8, 0.0, 14);
		WorldFx.particles(carrier, "minecraft:soul", 1.0, 0.4, 0.04, 14);
		Component curse = Component.translatable(pen.nameKey());
		carrier.sendSystemMessage(Component.translatable("gui.extremedimensions.burden.carried", curse, friend.getDisplayName()));
		friend.sendSystemMessage(Component.translatable("gui.extremedimensions.burden.relieved", carrier.getDisplayName(), curse));
		Advancements.grant(carrier, Advancements.BURDEN_CARRIED);
		Sync.state(friend, from);
		Sync.state(carrier, to);
		Sync.curses(friend, from, false);
		Sync.curses(carrier, to, false);
		DataStore.markDirty();
	}
}
