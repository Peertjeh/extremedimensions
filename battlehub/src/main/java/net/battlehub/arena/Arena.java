package net.battlehub.arena;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A blueprint placed in a slot: world coordinates for spawns, marks and bounds. */
public final class Arena {
	private final ServerLevel level;
	private final int slot;
	private final Theme theme;
	private final BlockPos origin;
	private final Blueprint bp;

	public Arena(ServerLevel level, int slot, Theme theme, BlockPos origin, Blueprint bp) {
		this.level = level;
		this.slot = slot;
		this.theme = theme;
		this.origin = origin;
		this.bp = bp;
	}

	public ServerLevel level() { return level; }
	public int slot() { return slot; }
	public Theme theme() { return theme; }
	public BlockPos origin() { return origin; }
	public Blueprint blueprint() { return bp; }

	public record Point(Vec3 pos, float yaw, float pitch) {}

	public Point spawn(int i) {
		List<Blueprint.Spawn> s = bp.spawns();
		Blueprint.Spawn sp = s.isEmpty() ? new Blueprint.Spawn(0.5, 1, 0.5, 0, 0) : s.get(Math.floorMod(i, s.size()));
		return new Point(new Vec3(origin.getX() + sp.x(), origin.getY() + sp.y(), origin.getZ() + sp.z()), sp.yaw(), sp.pitch());
	}

	public int spawnCount() {
		return bp.spawns().size();
	}

	public Point spectator() {
		Blueprint.Spawn sp = bp.spectator();
		return new Point(new Vec3(origin.getX() + sp.x(), origin.getY() + sp.y(), origin.getZ() + sp.z()), sp.yaw(), sp.pitch());
	}

	public BlockPos world(BlockPos rel) {
		return origin.offset(rel);
	}

	public List<BlockPos> marks(String name) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : bp.marks(name)) {
			out.add(origin.offset(p));
		}
		return out;
	}

	public BlockPos mark(String name) {
		return origin.offset(bp.markOne(name));
	}

	public double voidY() {
		return origin.getY() + bp.voidY();
	}

	/** Box that holds everything the arena built, plus room for projectiles. */
	public AABB bounds() {
		if (bp.minX() > bp.maxX()) {
			// Nothing placed and no room reserved
			return new AABB(origin).inflate(32);
		}
		return new AABB(origin.getX() + bp.minX(), origin.getY() + bp.minY(), origin.getZ() + bp.minZ(),
				origin.getX() + bp.maxX() + 1, origin.getY() + bp.maxY() + 1, origin.getZ() + bp.maxZ() + 1).inflate(4);
	}

	/**
	 * The chunks to keep loaded while the arena stands: where play happens plus room for arrows and items flying
	 * out of it. Landmarks and islands in the distance are left to load and unload like any other chunk.
	 */
	public AABB held() {
		int[] c = bp.core();
		if (c == null) return bounds();
		return new AABB(origin.getX() + c[0], origin.getY() + c[1], origin.getZ() + c[2], origin.getX() + c[3] + 1, origin.getY() + c[4] + 1,
				origin.getZ() + c[5] + 1).inflate(16).intersect(bounds());
	}

	/** Spectators stay within this horizontal radius of the origin and this height band. */
	public boolean inSpectatorBounds(Vec3 pos) {
		double dx = pos.x - (origin.getX() + 0.5);
		double dz = pos.z - (origin.getZ() + 0.5);
		int r = bp.radius();
		return dx * dx + dz * dz <= (double) r * r && pos.y > origin.getY() + bp.voidY() - 25 && pos.y < origin.getY() + 70;
	}

	/** Inside the playing field: players may build here and water and lava may flow here. */
	public boolean inPlayArea(BlockPos pos) {
		return bp.inPlayArea(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ());
	}

	/** Horizontally within the arena box (with some room around it). */
	public boolean around(BlockPos pos, int margin) {
		AABB b = bounds();
		return pos.getX() >= b.minX - margin && pos.getX() <= b.maxX + margin && pos.getZ() >= b.minZ - margin && pos.getZ() <= b.maxZ + margin;
	}

	public boolean contains(Vec3 pos) {
		return bounds().inflate(16).contains(pos);
	}
}
