package net.battlehub.arena;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;

/** Loose things a game leaves lying around: dropped items, arrows and other projectiles, XP, TNT, falling blocks. */
public final class Loose {
	private Loose() {}

	public static boolean is(Entity e) {
		return e instanceof ItemEntity || e instanceof Projectile || e instanceof ExperienceOrb || e instanceof PrimedTnt
				|| e instanceof FallingBlockEntity || e instanceof AreaEffectCloud;
	}

	/** Removes every loose entity in the box; returns how many. */
	public static int sweep(ServerLevel level, AABB box) {
		List<Entity> found = level.getEntitiesOfClass(Entity.class, box, Loose::is);
		for (Entity e : found) {
			e.discard();
		}
		return found.size();
	}

	public static int count(ServerLevel level, AABB box) {
		return level.getEntitiesOfClass(Entity.class, box, Loose::is).size();
	}
}
