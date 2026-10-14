package net.battlehub.games.bedwars;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.config.BedWarsConfig;
import net.battlehub.core.bedwars.GeneratorClock;
import net.battlehub.core.bedwars.Resource;
import net.battlehub.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The resource generators of one Bed Wars arena: iron and gold on every team island, diamonds between the islands,
 * emeralds in the middle. The timing is in {@link GeneratorClock}; this class only drops the items in the world.
 */
public final class Generators {
	/** One generator: where it is, what it drops and for which team (-1: shared). */
	public static final class Gen {
		public final BlockPos pos;
		public final Resource resource;
		public final int team;
		public final GeneratorClock clock;

		Gen(BlockPos pos, Resource resource, int team, GeneratorClock clock) {
			this.pos = pos;
			this.resource = resource;
			this.team = team;
			this.clock = clock;
		}
	}

	private final ServerLevel level;
	private final List<Gen> gens = new ArrayList<>();

	public Generators(ServerLevel level) {
		this.level = level;
	}

	static GeneratorClock clock(BedWarsConfig cfg, Resource r) {
		BedWarsConfig.Generator g = cfg.generators.get(r.id());
		if (g == null) {
			g = new BedWarsConfig.Generator();
		}
		return new GeneratorClock(g.intervalTicks, g.amount, g.cap);
	}

	public Gen add(BlockPos pos, Resource r, int team, BedWarsConfig cfg) {
		Gen g = new Gen(pos, r, team, clock(cfg, r));
		gens.add(g);
		return g;
	}

	public List<Gen> all() {
		return gens;
	}

	/** Sets every generator of this resource (diamond or emerald from the schedule) to a tier. */
	public void setTier(Resource r, int tier) {
		for (Gen g : gens) {
			if (g.resource == r) g.clock.setTier(tier);
		}
	}

	/** The base generators of a team (iron and gold) to a tier (the forge upgrade). */
	public void setTeamTier(int team, int tier) {
		for (Gen g : gens) {
			if (g.team == team) g.clock.setTier(tier);
		}
	}

	/** The current tier and seconds to the next drop of the first generator of this resource ({tier, seconds}). */
	public int[] status(Resource r) {
		for (Gen g : gens) {
			if (g.resource == r && g.team < 0) {
				return new int[] {g.clock.tier(), (g.clock.ticksUntilNext() + 19) / 20};
			}
		}
		return new int[] {0, 0};
	}

	/** One tick: generators whose time has come drop their items (unless enough already lie there). */
	public void tick() {
		for (Gen g : gens) {
			// Only count what lies around a generator when it is about to drop
			int lying = g.clock.ticksUntilNext() <= 1 ? lying(g) : 0;
			int n = g.clock.tick(lying);
			if (n > 0) {
				drop(g, n);
			}
		}
	}

	private AABB area(Gen g) {
		return new AABB(g.pos).inflate(1.5, 1.0, 1.5);
	}

	int lying(Gen g) {
		Item item = Ids.item(g.resource.item);
		int count = 0;
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, area(g), e -> e.getItem().is(item))) {
			count += e.getItem().getCount();
		}
		return count;
	}

	private void drop(Gen g, int n) {
		Vec3 at = Vec3.atBottomCenterOf(g.pos).add(0, 0.3, 0);
		ItemEntity e = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Ids.item(g.resource.item), n));
		e.setDeltaMovement(Vec3.ZERO);
		e.setNoPickUpDelay();
		level.addFreshEntity(e);
		if (g.resource == Resource.DIAMOND || g.resource == Resource.EMERALD) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.5, at.z, 4, 0.3, 0.3, 0.3, 0);
		}
	}
}
