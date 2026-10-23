package com.extremedimensions.elder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.extremedimensions.config.Config;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An arena that is broken into comes down where it has lost its support. Whatever breaks an arena's blocks (an Elder's
 * blow, a creeper, TNT, a pickaxe) marks the structures they belonged to; a few times a second each marked structure is
 * looked at again: its blocks that still stand are split into the pieces that still hang together, and a piece that
 * no longer rests on anything (the world, or another of the arena's structures) falls as rubble. A structure that
 * never rested on anything (an island, a tower on its own platform over a valley) keeps its largest piece and drops
 * the rest. The floor never comes down this way, nor do cores.
 *
 * <p>Rubble falls as falling blocks that drop nothing and hurt whoever they land on
 * ({@code elder_arena_rubble_damage}); where one comes to rest it lies as a block of the arena, and goes when the arena
 * goes. No more than {@code elder_arena_rubble_max} fall at once; the rest crumble where they are.
 */
public final class ArenaRuin {
	private ArenaRuin() {}

	/** Structures looked at in one go (a few times a second); the rest wait their turn. */
	private static final int GROUPS_PER_CHECK = 2;
	/** A structure larger than this is taken to stand (looking at it all would take too long). */
	private static final int LARGEST = 60000;

	/** Rubble on its way down, to be taken into its arena where it comes to rest. */
	private record Debris(FallingBlockEntity block, ElderArena.Arena arena) {}

	private static final List<Debris> FALLING = new ArrayList<>();

	// ------------------------------------------------------------------ what was broken

	/** Something broke into the world round a point (an Elder's blow, a pickaxe): the structures it reached are looked at. */
	static void struck(ServerLevel level, double x, double y, double z, double r) {
		if (Config.ELDER_ARENA_COLLAPSE <= 0 || ElderArena.arenas().isEmpty()) {
			return;
		}
		int ir = (int) Math.ceil(r) + 1;
		int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
		List<BlockPos> near = new ArrayList<>();
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dy = -ir; dy <= ir; dy++) {
				for (int dz = -ir; dz <= ir; dz++) {
					near.add(new BlockPos(bx + dx, by + dy, bz + dz));
				}
			}
		}
		blasted(level, near);
	}

	/** Blocks an explosion is about to take: the structures of any arena among them are looked at. */
	public static void blasted(ServerLevel level, List<BlockPos> blocks) {
		if (Config.ELDER_ARENA_COLLAPSE <= 0 || ElderArena.arenas().isEmpty()) {
			return;
		}
		for (BlockPos pos : blocks) {
			ElderArena.Piece p = ElderArena.standing(level, pos);
			if (p == null || p.group < 0) {
				continue;
			}
			ElderArena.Arena a = ElderArena.owner(level, pos);
			if (a != null && !a.dissolving) {
				a.damaged.add(p.group);
			}
		}
	}

	// ------------------------------------------------------------------ what still stands

	/** Looks again at a few of an arena's marked structures; whatever has lost its support falls. */
	static void settle(ElderArena.Arena a) {
		Iterator<Integer> it = a.damaged.iterator();
		int done = 0;
		while (it.hasNext() && done < GROUPS_PER_CHECK) {
			int g = it.next();
			it.remove();
			done++;
			if (g < 0 || g >= a.groups.size() || g >= a.bp.kinds.size()) {
				continue;
			}
			int kind = a.bp.kinds.get(g);
			if (kind == ArenaDesign.FLOOR || kind == ArenaDesign.CORE) {
				continue;
			}
			settle(a, g);
		}
	}

	private static void settle(ElderArena.Arena a, int g) {
		ServerLevel level = a.level;
		List<ElderArena.Piece> group = a.groups.get(g);
		if (group.size() > LARGEST) {
			return;
		}
		Map<Long, ElderArena.Piece> standing = new HashMap<>();
		for (ElderArena.Piece p : group) {
			if (!p.placed) {
				continue;
			}
			if (!Loaded.ready(level, p.pos)) {
				// Part of it is out of the loaded world: it is looked at again once all of it is in
				return;
			}
			if (level.getBlockState(p.pos) == p.state) {
				standing.put(p.pos.asLong(), p);
			}
		}
		if (standing.isEmpty()) {
			return;
		}
		// The pieces of it that still hang together, and which of them rest on something
		List<List<ElderArena.Piece>> parts = new ArrayList<>();
		List<Boolean> rests = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (ElderArena.Piece start : standing.values()) {
			if (!seen.add(start.pos.asLong())) {
				continue;
			}
			List<ElderArena.Piece> part = new ArrayList<>();
			boolean rest = false;
			ArrayDeque<ElderArena.Piece> todo = new ArrayDeque<>();
			todo.add(start);
			while (!todo.isEmpty()) {
				ElderArena.Piece p = todo.poll();
				part.add(p);
				for (Direction d : Direction.values()) {
					BlockPos n = p.pos.relative(d);
					long k = n.asLong();
					ElderArena.Piece q = standing.get(k);
					if (q != null) {
						if (seen.add(k)) {
							todo.add(q);
						}
					} else if (!rest && holds(level, n, g)) {
						rest = true;
					}
				}
			}
			parts.add(part);
			rests.add(rest);
		}
		boolean any = rests.contains(true);
		int largest = 0;
		for (int i = 1; i < parts.size(); i++) {
			if (parts.get(i).size() > parts.get(largest).size()) {
				largest = i;
			}
		}
		List<ElderArena.Piece> fall = new ArrayList<>();
		for (int i = 0; i < parts.size(); i++) {
			boolean stays = any ? rests.get(i) : i == largest;
			if (!stays) {
				fall.addAll(parts.get(i));
			}
		}
		if (!fall.isEmpty()) {
			fall(a, fall);
		}
	}

	/**
	 * Whether the block at {@code n} holds up a structure beside it: a solid block of the world, or of another of the
	 * arena's structures (a wall leans on the next stretch of wall, a beam on its posts).
	 */
	private static boolean holds(ServerLevel level, BlockPos n, int g) {
		BlockState s = level.getBlockState(n);
		if (s.isAir() || s.liquid() || s.canBeReplaced()) {
			return false;
		}
		ElderArena.Piece q = ElderArena.standing(level, n);
		return q == null || q.group != g;
	}

	// ------------------------------------------------------------------ rubble

	/** Blocks of an arena come down: the lowest first fall as rubble, as many as may, the rest crumble where they are. */
	static void fall(ElderArena.Arena a, List<ElderArena.Piece> pieces) {
		ServerLevel level = a.level;
		pieces.sort((p, q) -> Integer.compare(p.pos.getY(), q.pos.getY()));
		int room = Math.max(0, Config.ELDER_ARENA_RUBBLE_MAX);
		int i = 0;
		for (ElderArena.Piece p : pieces) {
			if (level.getBlockState(p.pos) != p.state) {
				continue;
			}
			ElderArena.fell(a, p);
			if (i++ < room) {
				rubble(a, p.pos, p.state, Config.ELDER_ARENA_RUBBLE_DAMAGE);
			} else {
				level.setBlock(p.pos, Blocks.AIR.defaultBlockState(), 2 | 16);
				if (i % 6 == 0) {
					WorldFx.particleIn(level, "minecraft:poof", p.pos.getX() + 0.5, p.pos.getY() + 0.5, p.pos.getZ() + 0.5, 0.3, 0.3, 0.3, 0.01, 2);
				}
			}
		}
		if (i > 0) {
			BlockPos at = pieces.get(pieces.size() / 2).pos;
			WorldFx.soundIn(level.dimension().identifier().toString(), "minecraft:block.deepslate.break", "block", at.getX() + 0.5, at.getY() + 0.5,
					at.getZ() + 0.5, 2.5f, 0.6f);
		}
	}

	/**
	 * One block of an arena falls as rubble from where it stands: it drops nothing, hurts whoever it lands on once it
	 * has fallen far enough, and lies as a block of the arena where it comes to rest.
	 */
	static FallingBlockEntity rubble(ElderArena.Arena a, BlockPos pos, BlockState state, int damage) {
		FallingBlockEntity fb = FallingBlockEntity.fall(a.level, pos, state);
		fb.dropItem = false;
		if (damage > 0) {
			fb.setHurtsEntities(1.0F, damage);
		}
		FALLING.add(new Debris(fb, a));
		return fb;
	}

	/** Every tick: rubble that has come to rest lies as a block of its arena (or is gone, if the arena is). */
	static void tick() {
		if (FALLING.isEmpty()) {
			return;
		}
		Iterator<Debris> it = FALLING.iterator();
		while (it.hasNext()) {
			Debris d = it.next();
			if (!d.block.isRemoved()) {
				continue;
			}
			it.remove();
			BlockPos at = d.block.blockPosition();
			Level level = d.block.level();
			BlockState lies = level.getBlockState(at);
			if (lies.is(d.block.getBlockState().getBlock()) && level == d.arena.level) {
				ElderArena.landed(d.arena, at, lies);
			}
		}
	}

	/** Rubble still falling (for tests). */
	static int falling() {
		return FALLING.size();
	}

	/** On stopping: rubble in the air is gone (it would land as blocks no arena remembers). */
	static void clear() {
		for (Debris d : FALLING) {
			d.block.discard();
		}
		FALLING.clear();
	}
}
