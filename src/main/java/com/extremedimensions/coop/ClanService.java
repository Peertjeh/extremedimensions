package com.extremedimensions.coop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;
import com.extremedimensions.tribe.Standing;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Clans: players who stand before the tribe together. The tribe sells a Blood Cord only to those it trusts; used on
 * another player (who must answer in the chat), it binds the two into one clan, and each pays in blood. It is no
 * shelter:
 *
 * <ul>
 *   <li>every loss of standing one member suffers falls on every other in full ({@code clan_share_loss}); a gain is
 *   shared only in part ({@code clan_share_gain});</li>
 *   <li>near each other ({@code clan_range}) the tribe treats every member as it treats the least trusted of them;</li>
 *   <li>none may leave for a while after binding, and leaving costs the leaver standing and a curse, and every member
 *   who stays standing too.</li>
 * </ul>
 */
public final class ClanService {
	private ClanService() {}

	/** Blocks within which the two must stand to be bound; ticks an offer waits for its answer. */
	private static final double REACH = 6;
	private static final int OFFER_TICKS = 60 * 20;

	private record Offer(UUID from, long until) {}

	/** Offers waiting for an answer, by the player who is asked. */
	private static final Map<UUID, Offer> OFFERS = new HashMap<>();

	public static void clear() {
		OFFERS.clear();
	}

	public static void forget(UUID player) {
		OFFERS.remove(player);
	}

	private static boolean on() {
		return Config.CLAN_ENABLED > 0 && Config.TRIBE_ENABLED > 0;
	}

	// ------------------------------------------------------------------ who is in which clan

	/** The id of the player's clan, or null. */
	public static String clanOf(UUID player) {
		String id = player.toString();
		for (Map.Entry<String, List<String>> e : DataStore.clans().entrySet()) {
			if (e.getValue().contains(id)) {
				return e.getKey();
			}
		}
		return null;
	}

	/** Everyone in the player's clan but the player (empty when they are in none). */
	public static List<UUID> others(UUID player) {
		String clan = clanOf(player);
		List<UUID> out = new ArrayList<>();
		if (clan == null) {
			return out;
		}
		for (String m : DataStore.clans().get(clan)) {
			if (!m.equals(player.toString())) {
				out.add(UUID.fromString(m));
			}
		}
		return out;
	}

	/** Members of this clan (an empty list for none). */
	private static List<String> members(String clan) {
		List<String> m = clan == null ? null : DataStore.clans().get(clan);
		return m == null ? List.of() : m;
	}

	/**
	 * The standing the tribe goes by for this player: their own, or, when members of their clan stand near them, the
	 * lowest of theirs.
	 */
	public static int judgedRep(ServerPlayer p, int own) {
		if (!on() || Config.CLAN_RANGE <= 0) {
			return own;
		}
		List<UUID> kin = others(p.getUUID());
		if (kin.isEmpty()) {
			return own;
		}
		MinecraftServer s = WorldFx.server();
		int low = own;
		double range = Config.CLAN_RANGE;
		for (UUID id : kin) {
			ServerPlayer o = s == null ? null : s.getPlayerList().getPlayer(id);
			if (o != null && o.level() == p.level() && o.distanceToSqr(p) <= range * range && !o.isSpectator()) {
				low = Math.min(low, DataStore.get(id).tribeRep);
			}
		}
		return low;
	}

	/**
	 * A member's standing changed: what of it falls on the rest of the clan (all of a loss, part of a gain), online or
	 * not. Called by {@link Standing#change} for the one it happened to.
	 */
	public static void spread(ServerPlayer p, int delta, String why) {
		if (!on() || delta == 0) {
			return;
		}
		double share = delta < 0 ? Config.CLAN_SHARE_LOSS : Config.CLAN_SHARE_GAIN;
		int part = (int) Math.round(delta * Math.max(0, Math.min(1, share)));
		if (part == 0) {
			return;
		}
		Component cause = Component.translatable("gui.extremedimensions.clan.why", p.getDisplayName(), Component.translatable(why));
		MinecraftServer s = WorldFx.server();
		for (UUID id : others(p.getUUID())) {
			ServerPlayer o = s == null ? null : s.getPlayerList().getPlayer(id);
			Standing.shift(id, o, part, cause);
		}
	}

	// ------------------------------------------------------------------ binding

	/** A Blood Cord used on another player: they are asked to be bound into one clan. */
	public static InteractionResult interact(ServerPlayer p, Entity target) {
		if (!(target instanceof ServerPlayer other) || !p.getMainHandItem().is(ModItems.BLOOD_CORD) || !Dims.ours(p)) {
			return InteractionResult.PASS;
		}
		if (!on()) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.clan.off"), true);
			return InteractionResult.SUCCESS;
		}
		String refused = check(p, other);
		if (refused != null) {
			p.sendSystemMessage(Component.translatable(refused, other.getDisplayName(), Math.max(2, Config.CLAN_MAX)), true);
			return InteractionResult.SUCCESS;
		}
		OFFERS.put(other.getUUID(), new Offer(p.getUUID(), WorldFx.server().overworld().getGameTime() + OFFER_TICKS));
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.clan.asked", other.getDisplayName()));
		MutableComponent ask = Component.translatable("gui.extremedimensions.clan.offer", p.getDisplayName(), (int) Math.round(Config.CLAN_BLOOD));
		ask.append(Component.literal(" "));
		ask.append(button("gui.extremedimensions.clan.accept", "/tether clan accept", ChatFormatting.GREEN));
		ask.append(Component.literal(" "));
		ask.append(button("gui.extremedimensions.clan.refuse", "/tether clan refuse", ChatFormatting.RED));
		other.sendSystemMessage(ask);
		other.sendSystemMessage(Component.translatable("gui.extremedimensions.clan.warning").withStyle(ChatFormatting.GRAY));
		WorldFx.soundSelf(other, "minecraft:block.note_block.chime", 0.8f, 0.7f);
		return InteractionResult.SUCCESS;
	}

	private static Component button(String key, String command, ChatFormatting colour) {
		return Component.translatable(key).withStyle(Style.EMPTY.withColor(colour).withBold(true)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
	}

	/** Why these two cannot be bound now (a message key), or null if they can. */
	private static String check(ServerPlayer from, ServerPlayer other) {
		if (from == other || !Allies.allied(from, other) || FallenService.fallen(from) || FallenService.fallen(other)
				|| from.level() != other.level() || from.distanceToSqr(other) > REACH * REACH || other.isSpectator() || other.isCreative()) {
			return "gui.extremedimensions.clan.failed";
		}
		String mine = clanOf(from.getUUID()), theirs = clanOf(other.getUUID());
		if (theirs != null) {
			return theirs.equals(mine) ? "gui.extremedimensions.clan.already" : "gui.extremedimensions.clan.other";
		}
		if (members(mine).size() >= Math.max(2, Config.CLAN_MAX)) {
			return "gui.extremedimensions.clan.full";
		}
		return null;
	}

	/** The asked player answers (from the chat buttons). */
	public static void answer(ServerPlayer other, boolean yes) {
		Offer o = OFFERS.remove(other.getUUID());
		MinecraftServer s = WorldFx.server();
		if (o == null || s == null || s.overworld().getGameTime() > o.until()) {
			other.sendSystemMessage(Component.translatable("gui.extremedimensions.clan.expired"), true);
			return;
		}
		ServerPlayer from = s.getPlayerList().getPlayer(o.from());
		if (from == null) {
			other.sendSystemMessage(Component.translatable("gui.extremedimensions.clan.expired"), true);
			return;
		}
		if (!yes) {
			from.sendSystemMessage(Component.translatable("gui.extremedimensions.clan.refused", other.getDisplayName()));
			return;
		}
		String refused = check(from, other);
		if (refused == null && !takeCord(from)) {
			refused = "gui.extremedimensions.clan.nocord";
		}
		if (refused != null) {
			Component msg = Component.translatable(refused, other.getDisplayName(), Math.max(2, Config.CLAN_MAX));
			from.sendSystemMessage(msg);
			other.sendSystemMessage(msg);
			return;
		}
		bind(from, other);
	}

	/** One Blood Cord out of the player's inventory (false if they no longer carry one). */
	private static boolean takeCord(ServerPlayer p) {
		if (p.isCreative()) {
			return true;
		}
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack st = p.getInventory().getItem(i);
			if (st.is(ModItems.BLOOD_CORD)) {
				st.shrink(1);
				return true;
			}
		}
		return false;
	}

	/** The two are bound: the asked one joins the other's clan (a new one if they had none), and both pay in blood. */
	public static void bind(ServerPlayer from, ServerPlayer other) {
		String clan = clanOf(from.getUUID());
		long now = WorldFx.server() == null ? 0 : WorldFx.server().overworld().getGameTime();
		if (clan == null) {
			clan = UUID.randomUUID().toString();
			DataStore.clans().put(clan, new ArrayList<>(List.of(from.getStringUUID())));
			DataStore.get(from.getUUID()).clanSince = now;
		}
		DataStore.clans().get(clan).add(other.getStringUUID());
		DataStore.get(other.getUUID()).clanSince = now;
		DataStore.markDirty();
		bleed(from);
		bleed(other);
		WorldFx.soundIn(Dims.fullId(other), "extremedimensions:hollowling_bond", "players", other.getX(), other.getY() + 1, other.getZ(), 1.0f, 0.6f);
		MinecraftServer s = WorldFx.server();
		for (String m : members(clan)) {
			ServerPlayer mp = s == null ? null : s.getPlayerList().getPlayer(UUID.fromString(m));
			if (mp != null) {
				mp.sendSystemMessage(Component.translatable("gui.extremedimensions.clan.bound", other.getDisplayName(), from.getDisplayName())
						.withStyle(ChatFormatting.DARK_RED));
			}
		}
		other.sendSystemMessage(Component.translatable("gui.extremedimensions.clan.rules").withStyle(ChatFormatting.GRAY));
	}

	/** The blood a binding costs: never the last of a player's health. */
	private static void bleed(ServerPlayer p) {
		double blood = Math.min(Math.max(0, Config.CLAN_BLOOD), p.getHealth() - 1.0);
		if (blood > 0 && p.level() instanceof ServerLevel level) {
			p.hurtServer(level, p.damageSources().magic(), (float) blood);
		}
		WorldFx.particles(p, "minecraft:dust{color:[0.6,0.02,0.05],scale:1.4}", 0.6, 0.8, 0.0, 30);
	}

	// ------------------------------------------------------------------ leaving

	/** Whole minutes before this member may leave their clan (0: now). */
	public static int lockedFor(ServerPlayer p) {
		MinecraftServer s = WorldFx.server();
		if (s == null) {
			return 0;
		}
		long left = DataStore.get(p.getUUID()).clanSince + Math.max(0, Config.CLAN_LOCK_MINUTES) * 1200L - s.overworld().getGameTime();
		return left <= 0 ? 0 : (int) ((left + 1199) / 1200);
	}

	/**
	 * The player cuts their cord: they lose standing and take a curse, every member who stays loses standing, and a clan
	 * left with one member is no more.
	 */
	public static void leave(ServerPlayer p) {
		String clan = clanOf(p.getUUID());
		if (clan == null) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.clan.none"), true);
			return;
		}
		int locked = lockedFor(p);
		if (locked > 0) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.clan.locked", locked));
			return;
		}
		List<String> m = DataStore.clans().get(clan);
		m.remove(p.getStringUUID());
		List<String> stayed = new ArrayList<>(m);
		if (m.size() < 2) {
			DataStore.clans().remove(clan);
		}
		DataStore.markDirty();
		// (out of the clan first: what it costs the leaver falls on no one else)
		Standing.change(p, -Math.max(0, Config.CLAN_LEAVE_COST), "gui.extremedimensions.tribe.why.clan_left");
		boolean overworld = Dims.OVERWORLD.equals(Dims.of(p)) && DataStore.overworldPenalties();
		for (int i = 0; i < Config.CLAN_LEAVE_CURSES; i++) {
			PenaltyService.curse(p, overworld);
		}
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.clan.left").withStyle(ChatFormatting.DARK_RED));
		WorldFx.particles(p, "minecraft:dust{color:[0.6,0.02,0.05],scale:1.4}", 0.6, 0.8, 0.0, 30);
		MinecraftServer s = WorldFx.server();
		Component cause = Component.translatable("gui.extremedimensions.clan.cut", p.getDisplayName());
		for (String id : stayed) {
			UUID u = UUID.fromString(id);
			ServerPlayer o = s == null ? null : s.getPlayerList().getPlayer(u);
			Standing.shift(u, o, -Math.max(0, Config.CLAN_LEFT_COST), cause);
			if (o != null) {
				o.sendSystemMessage(Component.translatable("gui.extremedimensions.clan.leftyou", p.getDisplayName()).withStyle(ChatFormatting.RED));
			}
		}
	}

	// ------------------------------------------------------------------ telling

	/** {@code /tether clan}: who the player's clan is, and where each stands with the tribe. */
	public static void info(ServerPlayer p) {
		String clan = clanOf(p.getUUID());
		if (clan == null) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.clan.none.how", Math.max(1, Config.CLAN_CORD_PRICE)));
			return;
		}
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.clan.head", members(clan).size(), Math.max(2, Config.CLAN_MAX))
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		MinecraftServer s = WorldFx.server();
		for (String id : members(clan)) {
			UUID u = UUID.fromString(id);
			PlayerData d = DataStore.get(u);
			ServerPlayer o = s == null ? null : s.getPlayerList().getPlayer(u);
			String name = o != null ? o.getScoreboardName() : d.name != null ? d.name : id.substring(0, 8);
			Standing.Level l = Standing.level(d.tribeRep);
			p.sendSystemMessage(Component.literal(" " + name + " ").withStyle(o != null ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY)
					.append(Component.translatable(l.key()).withStyle(l.colour()))
					.append(Component.literal(" (" + (d.tribeRep > 0 ? "+" : "") + d.tribeRep + ")").withStyle(ChatFormatting.GRAY)));
		}
		int locked = lockedFor(p);
		p.sendSystemMessage((locked > 0 ? Component.translatable("gui.extremedimensions.clan.locked", locked)
				: Component.translatable("gui.extremedimensions.clan.leavehow", Math.max(0, Config.CLAN_LEAVE_COST))).withStyle(ChatFormatting.GRAY));
	}
}
