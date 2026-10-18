package net.battlehub.games.bedwars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What players may build and break in Bed Wars: only inside the build box, never right on spawns, shops and
 * generators, and only blocks that players placed can be broken (the map itself stays). Placements are confirmed
 * one tick later, when the block is really there.
 */
public final class BuildRules {
	private static final int FLAGS = 2 | 16 | 32;

	private final ServerLevel level;
	private final BlockPos min;
	private final BlockPos max;
	private final int radius;
	private final List<BlockPos> protectedSpots = new ArrayList<>();
	private final Set<Long> placed = new HashSet<>();
	/** Positions a player is placing at this tick, checked on the next tick. */
	private final Map<Long, Integer> pending = new HashMap<>();
	/** The arena's playing field (inside the walls, below the top); everything by default. */
	private java.util.function.Predicate<BlockPos> area = p -> true;

	public BuildRules(ServerLevel level, BlockPos min, BlockPos max, int protectionRadius) {
		this.level = level;
		this.min = min;
		this.max = max;
		this.radius = protectionRadius;
	}

	/** Building only where this holds as well (the arena's play area). */
	public BuildRules area(java.util.function.Predicate<BlockPos> where) {
		this.area = where == null ? p -> true : where;
		return this;
	}

	/** No building within the protection radius of this spot (spawns, shops, generators). */
	public void protect(BlockPos pos) {
		protectedSpots.add(pos);
	}

	public boolean inBox(BlockPos p) {
		return p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY() && p.getY() <= max.getY()
				&& p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
	}

	public boolean isProtected(BlockPos p) {
		for (BlockPos s : protectedSpots) {
			int dx = Math.abs(p.getX() - s.getX());
			int dz = Math.abs(p.getZ() - s.getZ());
			int dy = p.getY() - s.getY();
			if (dx <= radius && dz <= radius && dy >= -1 && dy <= 2) {
				return true;
			}
		}
		return false;
	}

	public enum Verdict { OK, OUTSIDE, PROTECTED }

	public Verdict mayPlace(BlockPos target) {
		if (!inBox(target) || !area.test(target)) {
			return Verdict.OUTSIDE;
		}
		if (isProtected(target)) {
			return Verdict.PROTECTED;
		}
		return Verdict.OK;
	}

	/** A placement was allowed: confirm it on the next tick. */
	public void expect(BlockPos target, int tick) {
		pending.put(target.asLong(), tick);
	}

	/** Confirms placements from earlier ticks (the block is there now) and forgets the rest. */
	public void tick(int tick) {
		if (pending.isEmpty()) {
			return;
		}
		pending.entrySet().removeIf(e -> {
			if (e.getValue() >= tick) {
				return false;
			}
			BlockPos p = BlockPos.of(e.getKey());
			BlockState st = level.getBlockState(p);
			if (!st.isAir() && st.getFluidState().isEmpty()) {
				placed.add(e.getKey());
			}
			return true;
		});
	}

	public boolean isPlaced(BlockPos p) {
		return placed.contains(p.asLong());
	}

	/** A placed block was broken (by a player or an explosion). */
	public void forget(BlockPos p) {
		placed.remove(p.asLong());
	}

	/** Mark a block as placed right away (tests, TNT-proof checks). */
	public void markPlaced(BlockPos p) {
		placed.add(p.asLong());
	}

	/** Where players built (a copy). */
	public List<BlockPos> placedPositions() {
		List<BlockPos> out = new ArrayList<>();
		for (long l : placed) out.add(BlockPos.of(l));
		return out;
	}

	public int placedCount() {
		return placed.size();
	}

	/**
	 * An explosion at {@code center}: breaks placed blocks within {@code radius}, except blast-proof ones (glass
	 * and obsidian, as in classic Bed Wars). The map itself is never damaged. Returns how many broke.
	 */
	public int explode(BlockPos center, double radius) {
		int r = (int) Math.ceil(radius);
		int broken = 0;
		List<Long> hit = new ArrayList<>();
		for (long l : placed) {
			BlockPos p = BlockPos.of(l);
			if (Math.abs(p.getX() - center.getX()) > r || Math.abs(p.getY() - center.getY()) > r || Math.abs(p.getZ() - center.getZ()) > r) {
				continue;
			}
			if (p.distSqr(center) > radius * radius) {
				continue;
			}
			hit.add(l);
		}
		for (long l : hit) {
			BlockPos p = BlockPos.of(l);
			BlockState st = level.getBlockState(p);
			if (blastProof(st)) {
				continue;
			}
			level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
			placed.remove(l);
			broken++;
		}
		return broken;
	}

	/** Every placed block where {@code where} holds falls apart (with a puff); returns how many. */
	public int crumble(java.util.function.Predicate<BlockPos> where) {
		List<Long> gone = new ArrayList<>();
		for (long l : placed) {
			if (where.test(BlockPos.of(l))) gone.add(l);
		}
		for (long l : gone) {
			BlockPos p = BlockPos.of(l);
			level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.25, 0.25, 0.25, 0.02);
			placed.remove(l);
		}
		return gone.size();
	}

	static boolean blastProof(BlockState st) {
		String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
		return id.contains("glass") || id.equals("obsidian");
	}
}
