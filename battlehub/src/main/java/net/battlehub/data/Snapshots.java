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

import net.battlehub.BattleHub;
import net.battlehub.lobby.Lobby;
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
 * Inventory, health, hunger, effects, game mode and position from before a match or spectating session. Kept in
 * memory and on disk ({@code <world>/battlehub/restore/<uuid>.dat}), so even a crash cannot lose a player's items:
 * an unrestored snapshot is applied the next time that player joins.
 */
public final class Snapshots {
	private Snapshots() {}

	private static final class Snapshot {
		List<ItemStack> items = new ArrayList<>();
		int selected;
		float health = 20;
		int food = 20;
		float saturation = 5;
		float absorption;
		String gameMode = "survival";
		String dimension = "minecraft:overworld";
		double x, y, z;
		float yaw, pitch;
		List<MobEffectInstance> effects = new ArrayList<>();
		/** Only the position is still owed (the rest was restored when the player disconnected). */
		boolean positionOnly;
	}

	private static final Map<UUID, Snapshot> CACHE = new HashMap<>();
	private static Path dir;

	public static void init(MinecraftServer server) {
		CACHE.clear();
		dir = PlayerStore.worldDir(server).resolve("restore");
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			BattleHub.LOGGER.error("Could not create {}", dir, e);
		}
	}

	public static boolean has(UUID id) {
		return CACHE.containsKey(id) || (dir != null && Files.exists(file(id)));
	}

	private static Path file(UUID id) {
		return dir.resolve(id + ".dat");
	}

	/** Remembers the player's current state. An older, unrestored snapshot is kept (it is the real one). */
	public static void take(ServerPlayer p) {
		if (has(p.getUUID())) {
			Snapshot existing = load(p);
			if (existing != null && !existing.positionOnly) {
				return;
			}
		}
		Snapshot s = new Snapshot();
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			s.items.add(inv.getItem(i).copy());
		}
		s.selected = inv.getSelectedSlot();
		s.health = p.getHealth();
		s.food = p.getFoodData().getFoodLevel();
		s.saturation = p.getFoodData().getSaturationLevel();
		s.absorption = p.getAbsorptionAmount();
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
		CACHE.put(p.getUUID(), s);
		write(p, s);
	}

	/**
	 * Gives the player back everything from the snapshot. With {@code teleport} false (the player is disconnecting)
	 * the position is kept as owed and applied on the next join.
	 */
	public static boolean restore(ServerPlayer p, boolean teleport) {
		Snapshot s = load(p);
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
			GameType mode = GameType.byName(s.gameMode, GameType.SURVIVAL);
			p.setGameMode(mode);
			p.setHealth(Math.max(1f, Math.min(p.getMaxHealth(), s.health)));
			p.getFoodData().setFoodLevel(s.food);
			p.getFoodData().setSaturation(s.saturation);
			p.setAbsorptionAmount(s.absorption);
			p.clearFire();
			p.resetFallDistance();
			p.setArrowCount(0);
			p.inventoryMenu.sendAllDataToRemote();
		}
		if (!teleport) {
			s.positionOnly = true;
			CACHE.put(p.getUUID(), s);
			write(p, s);
			return true;
		}
		MinecraftServer server = p.level().getServer();
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(s.dimension)));
		p.stopRiding();
		if (level == null || net.battlehub.arena.ArenaWorld.is(level)) {
			Lobby.teleport(p);
		} else {
			p.teleportTo(level, s.x, s.y, s.z, Set.of(), s.yaw, s.pitch, true);
		}
		p.setDeltaMovement(Vec3.ZERO);
		p.resetFallDistance();
		discard(p.getUUID());
		return true;
	}

	public static void discard(UUID id) {
		CACHE.remove(id);
		if (dir != null) {
			try {
				Files.deleteIfExists(file(id));
			} catch (IOException e) {
				BattleHub.LOGGER.error("Could not delete restore file of {}", id, e);
			}
		}
	}

	private static Snapshot load(ServerPlayer p) {
		Snapshot s = CACHE.get(p.getUUID());
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
			s = new Snapshot();
			s.items = new ArrayList<>(tag.read("items", ItemStack.OPTIONAL_CODEC.listOf(), ops).orElse(List.of()));
			s.effects = new ArrayList<>(tag.read("effects", MobEffectInstance.CODEC.listOf(), ops).orElse(List.of()));
			s.selected = tag.getIntOr("selected", 0);
			s.health = tag.getFloatOr("health", 20);
			s.food = tag.getIntOr("food", 20);
			s.saturation = tag.getFloatOr("saturation", 5);
			s.absorption = tag.getFloatOr("absorption", 0);
			s.gameMode = tag.getStringOr("gameMode", "survival");
			s.dimension = tag.getStringOr("dimension", "minecraft:overworld");
			s.x = tag.getDoubleOr("x", 0);
			s.y = tag.getDoubleOr("y", 100);
			s.z = tag.getDoubleOr("z", 0);
			s.yaw = tag.getFloatOr("yaw", 0);
			s.pitch = tag.getFloatOr("pitch", 0);
			s.positionOnly = tag.getBooleanOr("positionOnly", false);
			CACHE.put(p.getUUID(), s);
			return s;
		} catch (Exception e) {
			BattleHub.LOGGER.error("Could not read restore file {}", f, e);
			return null;
		}
	}

	private static void write(ServerPlayer p, Snapshot s) {
		if (dir == null) {
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
			BattleHub.LOGGER.error("Could not write restore file for {}", p.getGameProfile().name(), e);
		}
	}
}
