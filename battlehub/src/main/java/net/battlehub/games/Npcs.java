package net.battlehub.games;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.util.Ids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Standing NPCs for a match (Bed Wars shopkeepers): no AI, silent, cannot be hurt, never despawn, and pulled back
 * to their place when pushed. The match that owns them removes them when it closes.
 */
public final class Npcs {
	private record Npc(Entity entity, Vec3 home, float yaw) {}

	private final List<Npc> npcs = new ArrayList<>();

	/** Spawns an entity of this type (e.g. minecraft:villager) at {@code pos}; null when the type cannot be made. */
	public Entity spawn(ServerLevel level, String type, Vec3 pos, float yaw, Component name) {
		EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(Ids.id(type));
		Entity e = t == null ? null : t.create(level, EntitySpawnReason.COMMAND);
		if (e == null) {
			return null;
		}
		e.snapTo(pos.x, pos.y, pos.z);
		e.setYRot(yaw);
		e.setYHeadRot(yaw);
		e.setSilent(true);
		e.setNoGravity(true);
		if (name != null) {
			e.setCustomName(name);
			e.setCustomNameVisible(true);
		}
		if (e instanceof Mob mob) {
			mob.setNoAi(true);
			mob.setPersistenceRequired();
		}
		e.addTag("battlehub_npc");
		Visibility.protectNpc(e);
		level.addFreshEntity(e);
		npcs.add(new Npc(e, pos, yaw));
		return e;
	}

	public boolean owns(Entity e) {
		for (Npc n : npcs) {
			if (n.entity == e) return true;
		}
		return false;
	}

	/** Puts pushed NPCs back (call now and then). */
	public void tick() {
		for (Npc n : npcs) {
			Entity e = n.entity;
			if (!e.isAlive()) continue;
			if (e.position().distanceToSqr(n.home) > 0.04) {
				e.teleportTo(n.home.x, n.home.y, n.home.z);
				e.setDeltaMovement(Vec3.ZERO);
			}
			e.setYRot(n.yaw);
			e.setYHeadRot(n.yaw);
		}
	}

	/** Removes every NPC of this match. */
	public void removeAll() {
		for (Npc n : npcs) {
			Visibility.releaseNpc(n.entity);
			n.entity.discard();
		}
		npcs.clear();
	}
}
