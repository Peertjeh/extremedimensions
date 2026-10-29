package com.extremedimensions.server;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.network.ModPackets;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Server-side presentation: particles, sounds, titles, effects and client animations, sent straight to the players
 * (no commands parsed, nothing logged). {@link #run} remains for the few things that are clearer as a command, such as
 * a display entity built from NBT.
 */
public final class WorldFx {
	private WorldFx() {}

	private static MinecraftServer server;
	private static final Map<String, Optional<ParticleOptions>> PARTICLES = new ConcurrentHashMap<>();

	public static void bind(MinecraftServer s) {
		server = s;
	}

	public static MinecraftServer server() {
		return server;
	}

	/** How many commands {@link #run} has run since the server started (the performance tests count them). */
	public static int commandsRun;

	/** Runs a command silently as the server. */
	public static void run(String command) {
		if (server == null) {
			return;
		}
		commandsRun++;
		try {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
		} catch (Exception e) {
			ExtremeDimensions.LOGGER.warn("Command failed: {}", command, e);
		}
	}

	/** Plays a client animation (see {@link com.extremedimensions.network.Fx}). */
	public static void fx(ServerPlayer p, int kind, int value) {
		ServerPlayNetworking.send(p, new ModPackets.FxPayload(kind, value));
	}

	// ------------------------------------------------------------------ lookups

	/** {@code "minecraft:soul"} or {@code "soul"} as an identifier. */
	public static Identifier id(String full) {
		int colon = full.indexOf(':');
		return colon < 0 ? Identifier.fromNamespaceAndPath("minecraft", full)
				: Identifier.fromNamespaceAndPath(full.substring(0, colon), full.substring(colon + 1));
	}

	/** A loaded world by its id ({@code "minecraft:the_nether"}), or null. */
	public static ServerLevel level(String dim) {
		return server == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id(dim)));
	}

	/** A particle without parameters, or null for one that needs them (those still go through the command). */
	private static ParticleOptions particle(String name) {
		if (name.indexOf('{') >= 0) {
			return null;
		}
		return PARTICLES.computeIfAbsent(name, n -> {
			ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getValue(id(n));
			return type instanceof ParticleOptions simple ? Optional.of(simple) : Optional.empty();
		}).orElse(null);
	}

	private static Holder<SoundEvent> sound(String id) {
		Identifier key = id(id);
		return BuiltInRegistries.SOUND_EVENT.get(key).<Holder<SoundEvent>>map(h -> h)
				.orElseGet(() -> Holder.direct(SoundEvent.createVariableRangeEvent(key)));
	}

	/** A sound category by its command name: master, hostile, neutral, ambient, player, block... */
	private static SoundSource source(String name) {
		return switch (name) {
			case "block" -> SoundSource.BLOCKS;
			case "player" -> SoundSource.PLAYERS;
			case "record" -> SoundSource.RECORDS;
			default -> SoundSource.valueOf(name.toUpperCase(Locale.ROOT));
		};
	}

	// ------------------------------------------------------------------ particles

	/** Particles around a player, visible to everyone nearby. */
	public static void particles(ServerPlayer p, String name, double dy, double spread, double speed, int count) {
		particleIn(p.level(), name, p.getX(), p.getY() + dy, p.getZ(), spread, spread, spread, speed, count);
	}

	/** Particles at a fixed Overworld position. With count 0, (dx, dy, dz) is the direction of motion. */
	public static void particleAt(String name, double x, double y, double z, double dx, double dy, double dz, double speed, int count) {
		particleIn(Level.OVERWORLD.identifier().toString(), name, x, y, z, dx, dy, dz, speed, count);
	}

	public static void particleIn(String dim, String name, double x, double y, double z, double dx, double dy, double dz, double speed, int count) {
		ServerLevel level = level(dim);
		if (level != null) {
			particleIn(level, name, x, y, z, dx, dy, dz, speed, count);
		}
	}

	public static void particleIn(Level level, String name, double x, double y, double z, double dx, double dy, double dz, double speed, int count) {
		ParticleOptions opt = particle(name);
		if (opt == null) {
			run(String.format(Locale.ROOT, "execute in %s run particle %s %.2f %.2f %.2f %.3f %.3f %.3f %.3f %d normal",
					level.dimension().identifier(), name, x, y, z, dx, dy, dz, speed, count));
		} else if (level instanceof ServerLevel sl) {
			sl.sendParticles(opt, x, y, z, count, dx, dy, dz, speed);
		}
	}

	/** Particles seen from far away (up to {@code range} blocks), for landmarks like the Seam's light falls. */
	public static void particleFar(String dim, String name, double x, double y, double z, double dx, double dy, double dz,
			double speed, int count, double range) {
		ServerLevel level = level(dim);
		ParticleOptions opt = particle(name);
		if (level == null || opt == null) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(x, y, z) <= range * range) {
				level.sendParticles(p, opt, true, false, x, y, z, count, dx, dy, dz, speed);
			}
		}
	}

	// ------------------------------------------------------------------ sounds

	/** A sound at the player, audible to everyone nearby, on the volume slider Minecraft would put it on. */
	public static void sound(ServerPlayer p, String id, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound(id), category(id), vol, pitch);
	}

	/** A sound only this player hears, on the volume slider Minecraft would put it on. */
	public static void soundSelf(ServerPlayer p, String id, float vol, float pitch) {
		p.connection.send(new ClientboundSoundPacket(sound(id), category(id), p.getX(), p.getY(), p.getZ(), vol, pitch,
				ThreadLocalRandom.current().nextLong()));
	}

	/**
	 * The volume slider a sound belongs on, as Minecraft sorts its own: the tribe and the Elders on Hostile Creatures,
	 * the hum of the Seam on Ambient/Environment, blocks on Blocks, music on Music, and whatever happens to the player
	 * (the Tether, rifts, curses, pages) on Players. Never Master, which no slider but the first can turn down.
	 */
	public static SoundSource category(String id) {
		String n = id.substring(id.indexOf(':') + 1);
		if (n.startsWith("music_disc.")) {
			return SoundSource.RECORDS;
		}
		if (n.startsWith("music")) {
			return SoundSource.MUSIC;
		}
		if (n.startsWith("block.") || n.startsWith("particle.")) {
			return SoundSource.BLOCKS;
		}
		if (n.contains("ambient") || n.startsWith("ambient.") || n.startsWith("event.raid") || n.startsWith("entity.lightning_bolt")) {
			return n.startsWith("lurker") || n.startsWith("hollowling") || n.startsWith("hoard") ? SoundSource.HOSTILE : n.startsWith("entity.lightning_bolt") ? SoundSource.WEATHER
					: n.startsWith("event.raid") ? SoundSource.HOSTILE : SoundSource.AMBIENT;
		}
		if (n.startsWith("elder.") || n.startsWith("lurker") || n.startsWith("hollowling") || n.startsWith("keeper") || n.startsWith("hoard") || n.startsWith("entity.warden")
				|| n.startsWith("entity.phantom") || n.startsWith("entity.vex")) {
			return SoundSource.HOSTILE;
		}
		if (n.startsWith("entity.villager")) {
			return SoundSource.NEUTRAL;
		}
		return SoundSource.PLAYERS;
	}

	/** A sound from a position that only this player hears (a whisper meant for one). */
	public static void soundFor(ServerPlayer p, String id, String category, double x, double y, double z, float vol, float pitch) {
		p.connection.send(new ClientboundSoundPacket(sound(id), source(category), x, y, z, vol, pitch, ThreadLocalRandom.current().nextLong()));
	}

	/** A block sound at an Overworld position. */
	public static void soundAt(String id, int x, int y, int z, float vol, float pitch) {
		soundIn(Level.OVERWORLD.identifier().toString(), id, "block", x + 0.5, y, z + 0.5, vol, pitch);
	}

	/** A sound at a position in a world, in a category (hostile, neutral, ambient, player, block, master). */
	public static void soundIn(String dim, String id, String category, double x, double y, double z, float vol, float pitch) {
		ServerLevel level = level(dim);
		if (level != null) {
			level.playSound(null, x, y, z, sound(id), source(category), vol, pitch);
		}
	}

	// ------------------------------------------------------------------ players

	/**
	 * Big centred title from language keys.
	 *
	 * @param subtitleArgKey optional language key passed as the subtitle's single argument, or null
	 */
	public static void title(ServerPlayer p, String titleKey, String color, String subtitleKey, String subtitleArgKey) {
		Component subtitle = subtitleArgKey == null ? Component.translatable(subtitleKey)
				: Component.translatable(subtitleKey, Component.translatable(subtitleArgKey));
		ChatFormatting tint = formatting(color);
		p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 25));
		p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle.copy().withStyle(ChatFormatting.GRAY)));
		p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(titleKey)
				.withStyle(tint, ChatFormatting.BOLD)));
	}

	/** A chat colour by its name ({@code "dark_purple"}); white if unknown. */
	private static ChatFormatting formatting(String name) {
		try {
			return ChatFormatting.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return ChatFormatting.WHITE;
		}
	}

	public static void tp(ServerPlayer p, String dim, double x, double y, double z) {
		ServerLevel level = level(dim);
		if (level != null) {
			p.teleportTo(level, x, y, z, Set.of(), p.getYRot(), p.getXRot(), true);
		}
	}

	/** As {@link #tp(ServerPlayer, String, double, double, double)}, turned to face a way. */
	public static void tp(ServerPlayer p, String dim, double x, double y, double z, float yaw, float pitch) {
		ServerLevel level = level(dim);
		if (level != null) {
			p.teleportTo(level, x, y, z, Set.of(), yaw, pitch, true);
		}
	}

	/** A vanilla effect as {@code /effect give ... true} would give it: no particles, no icon. */
	public static void effect(ServerPlayer p, Holder<MobEffect> effect, int seconds, int amplifier) {
		p.addEffect(new MobEffectInstance(effect, seconds * 20, amplifier, false, false));
	}

	/** Into the inventory, or dropped at the player's feet if there is no room. */
	public static void give(ServerPlayer p, ItemStack stack) {
		if (!p.getInventory().add(stack) && !stack.isEmpty()) {
			ItemEntity item = new ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), stack, 0, 0.1, 0);
			item.setNoPickUpDelay();
			p.level().addFreshEntity(item);
		}
	}

	/** Spawns a creature by id at a block position, prepared as a natural one would be. */
	/** Spawns a creature on a block; null if it could not be placed. */
	public static net.minecraft.world.entity.Entity summon(ServerLevel level, String type, int x, int y, int z) {
		EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(id(type));
		return t.spawn(level, new BlockPos(x, y, z), EntitySpawnReason.COMMAND);
	}

	/** Every criterion of one of this mod's advancements. */
	/** Whether the player has this advancement of the mod (false while no server is bound, or if it did not load). */
	public static boolean hasAdvancement(ServerPlayer p, String id) {
		if (server == null) {
			return false;
		}
		var holder = server.getAdvancements().get(ExtremeDimensions.id(id));
		return holder != null && p.getAdvancements().getOrStartProgress(holder).isDone();
	}

	public static void grantAdvancement(ServerPlayer p, String id) {
		if (server == null) {
			return;
		}
		var holder = server.getAdvancements().get(ExtremeDimensions.id(id));
		if (holder != null) {
			for (String criterion : holder.value().criteria().keySet()) {
				p.getAdvancements().award(holder, criterion);
			}
		}
	}
}
