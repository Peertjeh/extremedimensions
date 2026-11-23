package com.extremedimensions.events;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.reach.PaleReach;
import com.extremedimensions.registry.ModEnchantments;
import com.extremedimensions.server.WorldFx;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * The Pale Squall (3.39). Out in the Pale Reach, on an expedition, the void sometimes draws breath: for
 * {@code pale_squall_warning_seconds} motes stream past the way the wind will blow and a hiss rises, then for
 * {@code pale_squall_seconds} a gust pushes everyone near off the way it blows ({@code pale_squall_push}). Sneaking
 * halves it (and, as ever, keeps one from walking off an edge); braced against a wall, nothing moves one. Anchored
 * lessens it ({@code pale_squall_anchored} a level), the curse Unmoored adds to it, and on wings it pushes twice as hard.
 * It takes hold of a player in creative too, as an explosion's push does, unless they are flying; never of a spectator.
 * The gust is sent to each player's own client and added to their motion there (3.39.1): a player is moved by their
 * client, and motion set on the server does not reach it.
 */
public final class PaleSquall {
	private PaleSquall() {}

	/** How near (blocks) to the one it came for a squall reaches. */
	static final double REACH = 48;

	static final class Squall {
		final UUID player;
		final ServerLevel level;
		final double dx, dz;
		int ticks;

		Squall(UUID player, ServerLevel level, double dx, double dz) {
			this.player = player;
			this.level = level;
			this.dx = dx;
			this.dz = dz;
		}
	}

	private static final Map<UUID, Squall> SQUALLS = new HashMap<>();
	/** How many ticks of gust each player has been sent since the world opened (for the tests). */
	private static final Map<UUID, Integer> FELT = new HashMap<>();

	public static void tick(MinecraftServer s) {
		long now = s.getTickCount();
		if (now % 1200 == 900 && Config.PALE_SQUALL > 0) {
			for (ServerPlayer p : s.getPlayerList().getPlayers()) {
				PlayerData d = DataStore.get(p.getUUID());
				if (d.inRun && PaleReach.in(p) && !SQUALLS.containsKey(p.getUUID()) && ThreadLocalRandom.current().nextDouble() < Config.PALE_SQUALL_CHANCE * com.extremedimensions.reach.HomeOrLock.weather()
						&& WorldEvents.free(p, now)) {
					start(p, now);
				}
			}
		}
		if (SQUALLS.isEmpty()) {
			return;
		}
		for (Iterator<Squall> it = SQUALLS.values().iterator(); it.hasNext();) {
			Squall q = it.next();
			if (step(s, q)) {
				it.remove();
				WorldEvents.end(q.player, "squall");
			}
		}
	}

	/** A squall begins for a player: the void draws breath. Null when it began, else a language key for why not. */
	public static String start(ServerPlayer p, long now) {
		if (!(p.level() instanceof ServerLevel level)) {
			return "commands.extremedimensions.event.no_room";
		}
		double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
		SQUALLS.put(p.getUUID(), new Squall(p.getUUID(), level, Math.cos(a), Math.sin(a)));
		WorldEvents.begin(p, "squall", now);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.squall.coming", Math.max(0, Config.PALE_SQUALL_WARNING_SECONDS))
				.withStyle(ChatFormatting.LIGHT_PURPLE), true);
		WorldFx.soundSelf(p, "minecraft:item.elytra.flying", 0.3f, 0.6f);
		return null;
	}

	static String force(ServerPlayer p, long now) {
		if (SQUALLS.containsKey(p.getUUID())) {
			return "commands.extremedimensions.event.busy";
		}
		return start(p, now);
	}

	/** One tick of a squall; true once it has passed (or the one it came for is gone). */
	private static boolean step(MinecraftServer s, Squall q) {
		q.ticks++;
		ServerPlayer owner = s.getPlayerList().getPlayer(q.player);
		if (owner == null || owner.level() != q.level) {
			return true;
		}
		int warn = Math.max(0, Config.PALE_SQUALL_WARNING_SECONDS) * 20, blow = Math.max(1, Config.PALE_SQUALL_SECONDS) * 20;
		if (q.ticks <= warn) {
			// The void draws breath: motes stream past the way it will blow, and a hiss rises
			if (q.ticks % 4 == 0) {
				streaks(q, owner, 0.35, 3);
			}
			if (q.ticks % 20 == 1) {
				WorldFx.soundSelf(owner, "minecraft:item.elytra.flying", 0.3f + 0.5f * q.ticks / Math.max(1f, warn), 0.7f);
			}
			if (q.ticks == warn) {
				for (ServerPlayer p : q.level.players()) {
					if (p.distanceToSqr(owner) < REACH * REACH) {
						p.sendSystemMessage(Component.translatable("gui.extremedimensions.squall.blows").withStyle(ChatFormatting.LIGHT_PURPLE), true);
					}
				}
			}
			return false;
		}
		if (q.ticks > warn + blow) {
			owner.sendSystemMessage(Component.translatable("gui.extremedimensions.squall.passes").withStyle(ChatFormatting.GRAY), true);
			return true;
		}
		// The gust: everyone near the one it came for
		for (ServerPlayer p : q.level.players()) {
			if (!blown(p) || p.distanceToSqr(owner) > REACH * REACH) {
				continue;
			}
			double f = push(p, q.dx, q.dz);
			if (f > 0) {
				gust(p, q.dx * f, q.dz * f);
			}
		}
		if (q.ticks % 2 == 0) {
			streaks(q, owner, 1.1, 8);
		}
		if (q.ticks % 20 == 0) {
			WorldFx.soundSelf(owner, "minecraft:item.elytra.flying", 1.0f, 0.5f);
		}
		return false;
	}

	/**
	 * Whether a squall takes hold of a player at all: never of a spectator, nor of one flying in creative (as an
	 * explosion's push); of one standing in creative it does.
	 */
	public static boolean blown(ServerPlayer p) {
		return !p.isSpectator() && !(p.isCreative() && p.getAbilities().flying);
	}

	/** One tick of gust on a player, sent to their client to be added to their motion there. */
	public static void gust(ServerPlayer p, double x, double z) {
		FELT.merge(p.getUUID(), 1, Integer::sum);
		if (ServerPlayNetworking.canSend(p, ModPackets.SquallPayload.TYPE)) {
			ServerPlayNetworking.send(p, new ModPackets.SquallPayload((float) x, (float) z));
		}
	}

	/** How many ticks of gust a player has been sent (for the tests). */
	public static int felt(UUID player) {
		return FELT.getOrDefault(player, 0);
	}

	/**
	 * How hard a squall blowing this way pushes this player in a tick: nothing braced against a wall in the wind's way;
	 * sneaking halves it, Anchored lessens it, Unmoored adds to it, and on wings it pushes twice as hard.
	 */
	public static double push(ServerPlayer p, double dx, double dz) {
		return push(p, dx, dz, Config.PALE_SQUALL_PUSH);
	}

	/**
	 * How hard a pull or push of {@code base} a tick, this way, takes a player, by the squall's rules (also the Pale
	 * Mason's Last Door, which draws everyone towards it).
	 */
	public static double push(ServerPlayer p, double dx, double dz, double base) {
		if (braced(p, dx, dz)) {
			return 0;
		}
		double f = Math.max(0, base);
		if (p.isShiftKeyDown()) {
			f *= 0.5;
		}
		int anchored = Math.max(ModEnchantments.level(p.registryAccess(), p.getItemBySlot(EquipmentSlot.CHEST), ModEnchantments.ANCHORED),
				ModEnchantments.level(p.registryAccess(), p.getItemBySlot(EquipmentSlot.LEGS), ModEnchantments.ANCHORED));
		f *= Math.max(0, 1 - Config.PALE_SQUALL_ANCHORED * anchored);
		f *= 1 + 0.25 * DataStore.get(p.getUUID()).stacks(Penalty.UNMOORED);
		if (p.isFallFlying()) {
			f *= 2;
		}
		return f;
	}

	/** Whether a solid block stands right in the wind's way at the player's body (a wall to lean on). */
	public static boolean braced(ServerPlayer p, double dx, double dz) {
		for (double up : new double[] {0.4, 1.4}) {
			BlockPos at = BlockPos.containing(p.getX() + dx * 0.75, p.getY() + up, p.getZ() + dz * 0.75);
			if (p.level().getBlockState(at).isCollisionShapeFullBlock(p.level(), at)) {
				return true;
			}
		}
		return false;
	}

	/** Motes streaming past the way the wind blows, round the one it came for. */
	private static void streaks(Squall q, ServerPlayer owner, double speed, int n) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (int i = 0; i < n; i++) {
			double x = owner.getX() + rnd.nextDouble(-10, 10), y = owner.getY() + rnd.nextDouble(-1, 4), z = owner.getZ() + rnd.nextDouble(-10, 10);
			// (a count of 0 sends one mote moving along the given direction)
			q.level.sendParticles(rnd.nextBoolean() ? ParticleTypes.END_ROD : ParticleTypes.CLOUD, x, y, z, 0, q.dx, rnd.nextDouble(-0.05, 0.05), q.dz, speed);
		}
	}

	/** Whether a squall is under way for a player (for the tests). */
	public static boolean blowing(UUID player) {
		return SQUALLS.containsKey(player);
	}

	public static void clear() {
		SQUALLS.clear();
		FELT.clear();
	}
}
