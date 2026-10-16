package net.battlehub.progression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import net.battlehub.bot.Bots;
import net.battlehub.core.CosmeticDef;
import net.battlehub.hub.HubWorld;
import net.battlehub.mixin.DisplayAccessor;
import net.battlehub.mixin.ItemDisplayAccessor;
import net.battlehub.session.Sessions;
import net.battlehub.util.Ids;
import net.battlehub.util.Tags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

/**
 * Pets: an item floating by your shoulder in the lobby, bobbing and slowly turning. It is an item display that
 * follows you every tick (gliding between positions); it goes when you leave the lobby and comes back after.
 */
public final class Pets {
	private Pets() {}

	public static final String TAG = "battlehub_pet";

	private record Pet(Display.ItemDisplay entity, String item) {}

	private static final Map<UUID, Pet> PETS = new HashMap<>();

	public static int count() {
		return PETS.size();
	}

	public static boolean has(UUID id) {
		return PETS.containsKey(id);
	}

	public static Entity entity(UUID id) {
		Pet p = PETS.get(id);
		return p == null ? null : p.entity();
	}

	static void tick(MinecraftServer server, int tick) {
		ServerLevel hub = HubWorld.level(server);
		for (UUID id : new ArrayList<>(PETS.keySet())) {
			ServerPlayer owner = server.getPlayerList().getPlayer(id);
			Pet pet = PETS.get(id);
			if (owner == null || !eligible(owner) || pet.entity().isRemoved() || pet.entity().level() != owner.level()) {
				remove(id);
			}
		}
		if (hub == null) {
			return;
		}
		for (ServerPlayer p : hub.players()) {
			CosmeticDef c = eligible(p) ? Cosmetics.active(p, "pet") : null;
			if (c == null || !Ids.isItem(c.effect)) {
				remove(p.getUUID());
				continue;
			}
			Pet pet = PETS.get(p.getUUID());
			if (pet != null && !pet.item().equals(c.effect)) {
				remove(p.getUUID());
				pet = null;
			}
			if (pet == null) {
				pet = spawn(hub, p, c.effect);
				if (pet == null) continue;
				PETS.put(p.getUUID(), pet);
			}
			follow(p, pet.entity(), tick);
		}
		if (tick % 100 == 0) {
			sweep(hub);
		}
	}

	private static boolean eligible(ServerPlayer p) {
		return HubWorld.is(p.level()) && Sessions.inLobby(p.getUUID()) && !p.isSpectator() && !p.isInvisible() && !Bots.isBot(p);
	}

	private static Vec3 spot(ServerPlayer p, int tick) {
		double yaw = Math.toRadians(p.yBodyRot);
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double bob = 0.08 * Math.sin(tick * 0.12);
		// Over the right shoulder, a little behind
		return p.position().add(rx * 0.7 - fx * 0.15, 1.65 + bob + (p.isShiftKeyDown() ? -0.3 : 0), rz * 0.7 - fz * 0.15);
	}

	private static Pet spawn(ServerLevel hub, ServerPlayer owner, String item) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Ids.id("minecraft:item_display"));
		Entity e = type == null ? null : type.create(hub, EntitySpawnReason.COMMAND);
		if (!(e instanceof Display.ItemDisplay d)) {
			return null;
		}
		((ItemDisplayAccessor) d).battlehub$setItemStack(new ItemStack(Ids.item(item)));
		d.getEntityData().set(DisplayAccessor.battlehub$scale(), new Vector3f(0.45f, 0.45f, 0.45f));
		d.getEntityData().set(DisplayAccessor.battlehub$posRotDuration(), 3);
		Vec3 at = spot(owner, 0);
		d.snapTo(at.x, at.y, at.z);
		d.addTag(TAG);
		hub.addFreshEntity(d);
		return new Pet(d, item);
	}

	private static void follow(ServerPlayer owner, Display.ItemDisplay d, int tick) {
		Vec3 at = spot(owner, tick);
		d.teleportTo(at.x, at.y, at.z);
		// Turning slowly, one way round
		d.setYRot((tick * 3) % 360);
	}

	public static void remove(UUID id) {
		Pet pet = PETS.remove(id);
		if (pet != null) {
			pet.entity().discard();
		}
	}

	public static void removeAll() {
		for (UUID id : new ArrayList<>(PETS.keySet())) {
			remove(id);
		}
	}

	/** Pets left from before a restart (or a crash) are not ours any more. */
	private static void sweep(ServerLevel hub) {
		List<Entity> ours = new ArrayList<>();
		for (Pet p : PETS.values()) ours.add(p.entity());
		for (Display.ItemDisplay e : hub.getEntities(EntityTypeTest.forClass(Display.ItemDisplay.class), e -> Boolean.TRUE.equals(Tags.has(e, TAG)))) {
			if (!ours.contains(e)) {
				e.discard();
			}
		}
	}
}
