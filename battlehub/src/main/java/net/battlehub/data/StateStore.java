package net.battlehub.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import net.battlehub.BattleHub;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Saved player states (inventory, health, hunger, XP, effects, game mode, position), kept in memory and on disk in
 * {@code <world>/battlehub/<folder>/<uuid>.dat} so that not even a crash loses them. Used for the state before a
 * match ({@link Snapshots}) and for the survival state while a player is in BattleHub.
 */
public final class StateStore {
	/** How much of a saved state to give back. */
	public enum Restore {
		/** Everything, including the position; the saved state is then forgotten. */
		FULL,
		/** Everything but the position, which stays owed for the next join (the player is disconnecting). */
		OWE_POSITION,
		/** Everything but the position, which the player keeps; the saved state is then forgotten. */
		KEEP_POSITION
	}

	private static final class State {
		List<ItemStack> items = new ArrayList<>();
		int selected;
		float health = 20;
		int food = 20;
		float saturation = 5;
		float absorption;
		int xpLevel;
		float xpProgress;
		int xpTotal;
		String gameMode = "survival";
		String dimension = "minecraft:overworld";
		double x, y, z;
		float yaw, pitch;
		List<MobEffectInstance> effects = new ArrayList<>();
		/** Only the position is still owed (the rest was given back when the player disconnected). */
		boolean positionOnly;
	}

	private final String folder;
	private final Map<UUID, State> cache = new HashMap<>();
	/** Players known to have nothing saved, so has() does not look on disk every time. */
	private final java.util.Set<UUID> absent = new java.util.HashSet<>();
	private Path dir;

	public StateStore(String folder) {
		this.folder = folder;
	}

	public void init(MinecraftServer server) {
		cache.clear();
		absent.clear();
		dir = PlayerStore.worldDir(server).resolve(folder);
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			BattleHub.LOGGER.error("Could not create {}", dir, e);
		}
	}

	public boolean has(UUID id) {
		if (cache.containsKey(id)) {
			return true;
		}
		if (dir == null || absent.contains(id)) {
			return false;
		}
		if (Files.exists(file(id))) {
			return true;
		}
		absent.add(id);
		return false;
	}

	private Path file(UUID id) {
		return dir.resolve(id + ".dat");
	}

	/**
	 * Remembers the player's current state. With {@code keepExisting}, an older state that was not given back yet
	 * is kept instead (it is the real one).
	 */
	public void take(ServerPlayer p, boolean keepExisting) {
		if (keepExisting && has(p.getUUID())) {
			State existing = load(p);
			if (existing != null && !existing.positionOnly) {
				return;
			}
		}
		State s = new State();
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			s.items.add(inv.getItem(i).copy());
		}
		s.selected = inv.getSelectedSlot();
		s.health = p.getHealth();
		s.food = p.getFoodData().getFoodLevel();
		s.saturation = p.getFoodData().getSaturationLevel();
		s.absorption = p.getAbsorptionAmount();
		s.xpLevel = p.experienceLevel;
		s.xpProgress = p.experienceProgress;
		s.xpTotal = p.totalExperience;
		s.gameMode = p.gameMode().getName();
		s.dimension = p.level().dimension().identifier().toString();
		s.x = p.getX();
		s.y = p.getY();
		s.z = p.getZ();
		s.yaw = p.getYRot();
		s.pitch = p.getXRot();
		for (MobEffectInstance e : p.getActiveEffects()) {
			s.effects.add(new MobEffectInstance(e));
		}
		cache.put(p.getUUID(), s);
		absent.remove(p.getUUID());
		write(p, s);
	}

	/** Changes where a saved state will put the player back. */
	public void setPosition(ServerPlayer p, ServerLevel level, Vec3 pos, float yaw, float pitch) {
		State s = load(p);
		if (s == null) {
			return;
		}
		s.dimension = level.dimension().identifier().toString();
		s.x = pos.x;
		s.y = pos.y;
		s.z = pos.z;
		s.yaw = yaw;
		s.pitch = pitch;
		write(p, s);
	}

	/**
	 * Gives the saved state back. {@code fallback} sends the player somewhere sensible when the saved dimension is
	 * gone or not allowed ({@code allowed} false). Returns false when there was nothing saved.
	 */
	public boolean restore(ServerPlayer p, Restore how, Consumer<ServerPlayer> fallback, java.util.function.Predicate<ServerLevel> allowed) {
		State s = load(p);
		if (s == null) {
			return false;
		}
		if (!s.positionOnly) {
			p.closeContainer();
			p.containerMenu.setCarried(ItemStack.EMPTY);
			Inventory inv = p.getInventory();
			inv.clearContent();
			for (int i = 0; i < Math.min(inv.getContainerSize(), s.items.size()); i++) {
				inv.setItem(i, s.items.get(i).copy());
			}
			inv.setSelectedSlot(Math.max(0, Math.min(8, s.selected)));
			p.removeAllEffects();
			for (MobEffectInstance e : s.effects) {
				p.addEffect(new MobEffectInstance(e));
			}
			p.setGameMode(GameType.byName(s.gameMode, GameType.SURVIVAL));
			p.setHealth(Math.max(1f, Math.min(p.getMaxHealth(), s.health)));
			p.getFoodData().setFoodLevel(s.food);
			p.getFoodData().setSaturation(s.saturation);
			p.setAbsorptionAmount(s.absorption);
			setXp(p, s.xpLevel, s.xpProgress, s.xpTotal);
			p.clearFire();
			p.resetFallDistance();
			p.setArrowCount(0);
			p.inventoryMenu.sendAllDataToRemote();
		}
		switch (how) {
			case OWE_POSITION -> {
				s.positionOnly = true;
				cache.put(p.getUUID(), s);
				write(p, s);
				return true;
			}
			case KEEP_POSITION -> {
				discard(p.getUUID());
				return true;
			}
			default -> { }
		}
		MinecraftServer server = p.level().getServer();
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(s.dimension)));
		p.stopRiding();
		if (level == null || !allowed.test(level)) {
			fallback.accept(p);
		} else {
			p.teleportTo(level, s.x, s.y, s.z, Set.of(), s.yaw, s.pitch, true);
		}
		p.setDeltaMovement(Vec3.ZERO);
		p.resetFallDistance();
		discard(p.getUUID());
		return true;
	}

	/** Sets the XP bar and makes sure the client hears about it. */
	public static void setXp(ServerPlayer p, int level, float progress, int total) {
		p.experienceProgress = Math.max(0f, Math.min(1f, progress));
		p.totalExperience = Math.max(0, total);
		// setExperienceLevels also forces the next XP update to the client
		p.setExperienceLevels(Math.max(0, level));
	}

	public void discard(UUID id) {
		cache.remove(id);
		absent.add(id);
		if (dir != null) {
			try {
				Files.deleteIfExists(file(id));
			} catch (IOException e) {
				BattleHub.LOGGER.error("Could not delete {} file of {}", folder, id, e);
			}
		}
	}

	private State load(ServerPlayer p) {
		State s = cache.get(p.getUUID());
		if (s != null || dir == null) {
			return s;
		}
		Path f = file(p.getUUID());
		if (!Files.exists(f)) {
			return null;
		}
		try {
			CompoundTag tag = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
			RegistryOps<Tag> ops = p.level().registryAccess().createSerializationContext(NbtOps.INSTANCE);
			s = new State();
			s.items = new ArrayList<>(tag.read("items", ItemStack.OPTIONAL_CODEC.listOf(), ops).orElse(List.of()));
			s.effects = new ArrayList<>(tag.read("effects", MobEffectInstance.CODEC.listOf(), ops).orElse(List.of()));
			s.selected = tag.getIntOr("selected", 0);
			s.health = tag.getFloatOr("health", 20);
			s.food = tag.getIntOr("food", 20);
			s.saturation = tag.getFloatOr("saturation", 5);
			s.absorption = tag.getFloatOr("absorption", 0);
			s.xpLevel = tag.getIntOr("xpLevel", p.experienceLevel);
			s.xpProgress = tag.getFloatOr("xpProgress", p.experienceProgress);
			s.xpTotal = tag.getIntOr("xpTotal", p.totalExperience);
			s.gameMode = tag.getStringOr("gameMode", "survival");
			s.dimension = tag.getStringOr("dimension", "minecraft:overworld");
			s.x = tag.getDoubleOr("x", 0);
			s.y = tag.getDoubleOr("y", 100);
			s.z = tag.getDoubleOr("z", 0);
			s.yaw = tag.getFloatOr("yaw", 0);
			s.pitch = tag.getFloatOr("pitch", 0);
			s.positionOnly = tag.getBooleanOr("positionOnly", false);
			cache.put(p.getUUID(), s);
			return s;
		} catch (Exception e) {
			BattleHub.LOGGER.error("Could not read {}", f, e);
			return null;
		}
	}

	private void write(ServerPlayer p, State s) {
		// Bots keep their state in memory only
		if (dir == null || net.battlehub.bot.Bots.isBot(p.getUUID())) {
			return;
		}
		try {
			RegistryOps<Tag> ops = p.level().registryAccess().createSerializationContext(NbtOps.INSTANCE);
			CompoundTag tag = new CompoundTag();
			tag.store("items", ItemStack.OPTIONAL_CODEC.listOf(), ops, s.items);
			tag.store("effects", MobEffectInstance.CODEC.listOf(), ops, s.effects);
			tag.putInt("selected", s.selected);
			tag.putFloat("health", s.health);
			tag.putInt("food", s.food);
			tag.putFloat("saturation", s.saturation);
			tag.putFloat("absorption", s.absorption);
			tag.putInt("xpLevel", s.xpLevel);
			tag.putFloat("xpProgress", s.xpProgress);
			tag.putInt("xpTotal", s.xpTotal);
			tag.putString("gameMode", s.gameMode);
			tag.putString("dimension", s.dimension);
			tag.putDouble("x", s.x);
			tag.putDouble("y", s.y);
			tag.putDouble("z", s.z);
			tag.putFloat("yaw", s.yaw);
			tag.putFloat("pitch", s.pitch);
			tag.putBoolean("positionOnly", s.positionOnly);
			Path f = file(p.getUUID());
			Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
			NbtIo.writeCompressed(tag, tmp);
			try {
				Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomic) {
				Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Exception e) {
			BattleHub.LOGGER.error("Could not write {} file for {}", folder, p.getGameProfile().name(), e);
		}
	}
}
