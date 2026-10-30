package com.extremedimensions.penalty;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.extremedimensions.config.Config;
import com.extremedimensions.coop.FallenService;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.elder.FerrymanService;
import com.extremedimensions.elder.MatriarchService;
import com.extremedimensions.elder.WeaverService;
import com.extremedimensions.hollow.HollowService;
import com.extremedimensions.lurker.LurkerService;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;
import com.extremedimensions.tribe.RaidService;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * No leaving the wound's creatures behind by leaving the game. A player who logs out while the wound is after them (a
 * swarm, a Lurker glowing red at them or reaching for what they carry, a Keeper, a raid, an Elder, or a blow from one of
 * the wound's creatures in the last {@code flee_danger_seconds}) comes back with {@code flee_curses} curses, and what
 * hunted them (a swarm, a hunting Lurker) comes again ({@code flee_returns}).
 *
 * <p>A dedicated server shutting down is no one's flight; a single-player world closed in danger is. A fallen player who
 * leaves bleeds out instead, which costs its own curse.
 */
public final class FleeService {
	private FleeService() {}

	/** Player -> server tick of the last blow a creature of the wound dealt them. */
	private static final Map<UUID, Integer> STRUCK = new HashMap<>();

	public static void clear() {
		STRUCK.clear();
	}

	/** After damage: a blow from one of the wound's creatures puts a player in danger for a while. */
	public static void afterDamage(LivingEntity entity, DamageSource source, float baseTaken, float taken, boolean blocked) {
		if (entity instanceof ServerPlayer p && taken > 0 && woundCreature(source.getEntity())) {
			STRUCK.put(p.getUUID(), p.level().getServer().getTickCount());
		}
	}

	private static boolean woundCreature(Entity e) {
		return e != null && !(e instanceof ServerPlayer) && "extremedimensions".equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace());
	}

	/** What of the wound is after this player right now ("swarm", "hunt", "keeper", "raid", "elder", "blow"); null if nothing. */
	public static String danger(ServerPlayer p) {
		String lurker = LurkerService.threat(p);
		String young = HollowService.threat(p);
		if ("swarm".equals(lurker) || "swarm".equals(young)) {
			return "swarm";
		}
		if (lurker != null) {
			return lurker;
		}
		if (young != null) {
			return young;
		}
		if (RaidService.raiding(p)) {
			return "raid";
		}
		if (MatriarchService.hunting(p) || FerrymanService.hunting(p) || WeaverService.hunting(p)) {
			return "elder";
		}
		Integer at = STRUCK.get(p.getUUID());
		if (at != null && p.level().getServer().getTickCount() - at <= Math.max(0, Config.FLEE_DANGER_SECONDS) * 20) {
			return "blow";
		}
		return null;
	}

	/** A player leaving: in danger, the flight is noted, to be paid for when they come back. */
	public static void leaving(ServerPlayer p, MinecraftServer s) {
		boolean fled = Config.FLEE_CURSES > 0 && (s.isRunning() || !s.isDedicatedServer());
		String why = fled && p.isAlive() && !p.isCreative() && !p.isSpectator() && !FallenService.fallen(p) ? danger(p) : null;
		STRUCK.remove(p.getUUID());
		if (why != null) {
			mark(p, why);
		}
	}

	/** Notes a flight from this danger (the server's own leaving and the tests). */
	public static void mark(ServerPlayer p, String why) {
		DataStore.get(p.getUUID()).fled = why;
		DataStore.markDirty();
	}

	/** Back after a flight: the curses fall on them, and what hunted them comes again. */
	public static void collect(ServerPlayer p, PlayerData d) {
		String why = d.fled;
		if (why == null) {
			return;
		}
		d.fled = null;
		DataStore.markDirty();
		if (!Dims.ours(p) || p.isCreative() || p.isSpectator()) {
			return;
		}
		boolean overworld = Dims.OVERWORLD.equals(Dims.of(p)) && DataStore.overworldPenalties();
		int cursed = 0;
		for (int i = 0; i < Config.FLEE_CURSES; i++) {
			cursed += PenaltyService.curse(p, overworld, Config.LURKER_CURSE_EXTREME_CHANCE) != null ? 1 : 0;
		}
		if (cursed > 0) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.fled", cursed).withStyle(ChatFormatting.DARK_RED));
			WorldFx.particles(p, "minecraft:sculk_soul", 1.0, 0.6, 0.06, 24);
			WorldFx.soundFor(p, "extremedimensions:lurker_whisper", "hostile", p.getX(), p.getY() + 1, p.getZ(), 1.0f, 0.6f);
		}
		if (Config.FLEE_RETURNS > 0 && ("swarm".equals(why) || "hunt".equals(why)) && LurkerService.comeBack(p, why)) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.fled.waiting").withStyle(ChatFormatting.DARK_RED));
		}
	}
}
