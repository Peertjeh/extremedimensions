package com.extremedimensions.tribe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.Hoard;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.hollow.HollowService;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.lore.Loot;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Raids. Opening a hoard that is not the player's own is a raid on it: first its young swarm out and steal all they
 * can carry back into it (catch them, or hold out until they tire), then its Keeper wakes. Slay the Keeper and the
 * hoard spills its spoils into its barrel: Tether Fragments, pages, emeralds, things the tribe hid away, now and then
 * a relic. Go too far from the hoard, or fall, and the raid fails; the hoard keeps its secrets (and can be raided
 * again). Every raid costs standing with the tribe.
 */
public final class RaidService {
	private RaidService() {}

	enum Wave { YOUNG, KEEPER }

	static final class Raid {
		String hoard;
		UUID raider;
		Wave wave = Wave.YOUNG;
		int ticks;
		int total;
		List<UUID> young = new ArrayList<>();
		ServerBossEvent bar;
	}

	private static final Map<String, Raid> RAIDS = new HashMap<>();

	public static void clear() {
		for (Raid r : RAIDS.values()) {
			r.bar.removeAllPlayers();
		}
		RAIDS.clear();
	}

	public static boolean raiding(String hoard) {
		return hoard != null && RAIDS.containsKey(hoard);
	}

	/** Whether this player is raiding a hoard right now. */
	public static boolean raiding(ServerPlayer p) {
		for (Raid r : RAIDS.values()) {
			if (p.getUUID().equals(r.raider)) {
				return true;
			}
		}
		return false;
	}

	public static int count() {
		return RAIDS.size();
	}

	/** The wave a raid on this hoard is in ("young", "keeper"), or null when there is none. */
	public static String wave(String hoard) {
		Raid r = RAIDS.get(hoard);
		return r == null ? null : r.wave.name().toLowerCase(java.util.Locale.ROOT);
	}

	/** Whether opening this hoard is a raid for this player (and starts it); false when it is not. */
	public static boolean start(ServerPlayer p, Hoard h) {
		if (Config.TRIBE_RAID <= 0 || h.keeperSlain || h.raided || p.isCreative() || p.isSpectator()
				|| p.getStringUUID().equals(h.owner) || Dims.END.equals(h.dim) || Rules.peaceful(p.level())) {
			return false;
		}
		if (RAIDS.containsKey(h.key())) {
			return true;
		}
		ServerLevel level = (ServerLevel) p.level();
		Raid r = new Raid();
		r.hoard = h.key();
		r.raider = p.getUUID();
		r.young.addAll(HollowService.raidYoung(level, new BlockPos(h.x, h.y, h.z), h.key(), Math.max(0, Config.TRIBE_RAID_YOUNG)));
		r.total = Math.max(1, r.young.size());
		r.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("gui.extremedimensions.raid.bar.young"),
				BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_6);
		r.bar.addPlayer(p);
		RAIDS.put(r.hoard, r);
		Standing.change(p, Config.TRIBE_REP_RAID, "gui.extremedimensions.tribe.why.raid");
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.raid.start").withStyle(ChatFormatting.DARK_PURPLE));
		String dim = Dims.fullIdOf(h.dim);
		WorldFx.soundIn(dim, "extremedimensions:keeper_roar", "hostile", h.x + 0.5, h.y + 1, h.z + 0.5, 1.6f, 1.6f);
		WorldFx.soundIn(dim, "extremedimensions:hollowling_giggle", "hostile", h.x + 0.5, h.y + 1, h.z + 0.5, 1.4f, 0.8f);
		WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", h.x + 0.5, h.y + 1.5, h.z + 0.5, 1.5, 1.2, 1.5, 0.0, 50);
		Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.RAID);
		return true;
	}

	public static void tick(MinecraftServer s) {
		if (RAIDS.isEmpty()) {
			return;
		}
		List<Runnable> later = new ArrayList<>();
		Iterator<Raid> it = RAIDS.values().iterator();
		while (it.hasNext()) {
			Raid r = it.next();
			r.ticks++;
			Hoard h = HollowService.hoardByKey(r.hoard);
			ServerPlayer p = s.getPlayerList().getPlayer(r.raider);
			if (h == null) {
				// Broken up, burnt, gone: nothing left to raid
				r.bar.removeAllPlayers();
				it.remove();
				continue;
			}
			double range = Math.max(8, Config.TRIBE_RAID_RANGE);
			if (p == null || !p.isAlive() || !Dims.of(p).equals(h.dim) || h.distSq(p.getX(), p.getY(), p.getZ()) > range * range) {
				ServerPlayer gone = p;
				it.remove();
				later.add(() -> fail(r, gone));
				continue;
			}
			if (r.ticks % 20 == 0) {
				audience(r, h, p);
			}
			if (r.wave == Wave.YOUNG) {
				r.young.removeIf(id -> !HollowService.youngAlive(id));
				r.bar.setProgress(Math.max(0f, Math.min(1f, r.young.size() / (float) r.total)));
				if (r.young.isEmpty() || r.ticks > Math.max(1, Config.TRIBE_RAID_YOUNG_SECONDS) * 20) {
					later.add(() -> keeper(r, h, p));
				}
			} else if (h.keeperSlain) {
				it.remove();
				later.add(() -> win(r, h, p));
			} else if (r.ticks > 60 && !HollowService.keeperAwake(h.key())) {
				// Its Keeper went back into the dark (or never came): the raid is over
				it.remove();
				later.add(() -> fail(r, p));
			}
		}
		later.forEach(Runnable::run);
	}

	/** Everyone near the hoard sees the raid's bar. */
	private static void audience(Raid r, Hoard h, ServerPlayer raider) {
		double range = Math.max(8, Config.TRIBE_RAID_RANGE);
		for (ServerPlayer q : raider.level().getServer().getPlayerList().getPlayers()) {
			boolean near = q == raider || Dims.of(q).equals(h.dim) && h.distSq(q.getX(), q.getY(), q.getZ()) <= range * range;
			if (near && !r.bar.getPlayers().contains(q)) {
				r.bar.addPlayer(q);
			} else if (!near && r.bar.getPlayers().contains(q)) {
				r.bar.removePlayer(q);
			}
		}
	}

	/** The young are spent: the Keeper wakes (or the Matriarch, whose fight the raid becomes). */
	private static void keeper(Raid r, Hoard h, ServerPlayer p) {
		if (r.wave != Wave.YOUNG) {
			return;
		}
		r.wave = Wave.KEEPER;
		r.ticks = 0;
		boolean matriarch = HollowService.matriarchFor(DataStore.get(p.getUUID()));
		if (Config.KEEPER_ENABLED <= 0) {
			RAIDS.remove(r.hoard);
			win(r, h, p);
			return;
		}
		HollowService.wake(h, p);
		if (matriarch) {
			RAIDS.remove(r.hoard);
			r.bar.removeAllPlayers();
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.raid.matriarch").withStyle(ChatFormatting.DARK_PURPLE));
			return;
		}
		// The Keeper's own bar takes over
		r.bar.setVisible(false);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.raid.keeper").withStyle(ChatFormatting.DARK_PURPLE));
	}

	private static void fail(Raid r, ServerPlayer p) {
		r.bar.removeAllPlayers();
		// The young that swarmed out go home with what they took, and are gone
		HollowService.releaseYoung(r.young, HollowService.hoardByKey(r.hoard));
		if (p != null) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.raid.failed").withStyle(ChatFormatting.GRAY));
		}
	}

	/** The Keeper fell: the hoard spills its spoils. */
	private static void win(Raid r, Hoard h, ServerPlayer p) {
		r.bar.removeAllPlayers();
		HollowService.releaseYoung(r.young, null);
		h.raided = true;
		HollowService.deposit(h, spoils((ServerLevel) p.level(), h));
		PlayerData d = DataStore.get(p.getUUID());
		d.raidsWon++;
		Advancements.grant(p, Advancements.RAIDER);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.raid.won").withStyle(ChatFormatting.GOLD));
		String dim = Dims.fullIdOf(h.dim);
		WorldFx.soundIn(dim, "minecraft:entity.player.levelup", "players", h.x + 0.5, h.y + 1, h.z + 0.5, 1.0f, 0.8f);
		WorldFx.soundIn(dim, "minecraft:block.barrel.open", "blocks", h.x + 0.5, h.y + 1, h.z + 0.5, 1.0f, 0.6f);
		WorldFx.particleIn(dim, "minecraft:totem_of_undying", h.x + 0.5, h.y + 1.5, h.z + 0.5, 0.6, 0.8, 0.6, 0.3, 40);
		WorldFx.particleIn(dim, "extremedimensions:soul_mote", h.x + 0.5, h.y + 1.5, h.z + 0.5, 0.8, 1.0, 0.8, 0.0, 20);
		DataStore.markDirty();
	}

	/** What a raided hoard spills: what the tribe hid away, more than any hoard found in the wild (raided_hoard). */
	public static List<ItemStack> spoils(ServerLevel level, Hoard h) {
		List<ItemStack> out = new ArrayList<>(Loot.roll(level, Loot.RAIDED_HOARD, new Vec3(h.x + 0.5, h.y + 0.5, h.z + 0.5)));
		if (ThreadLocalRandom.current().nextDouble() < Config.TRIBE_RAID_RELIC_CHANCE) {
			out.add(RelicService.random());
		}
		return out;
	}
}
