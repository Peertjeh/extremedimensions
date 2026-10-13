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
		if (bp.isEmpty()) {
			return new AABB(origin).inflate(32);
		}
		return new AABB(origin.getX() + bp.minX(), origin.getY() + bp.minY(), origin.getZ() + bp.minZ(),
				origin.getX() + bp.maxX() + 1, origin.getY() + bp.maxY() + 1, origin.getZ() + bp.maxZ() + 1).inflate(4);
	}

	/** Spectators stay within this horizontal radius of the origin and this height band. */
	public boolean inSpectatorBounds(Vec3 pos) {
		double dx = pos.x - (origin.getX() + 0.5);
		double dz = pos.z - (origin.getZ() + 0.5);
		int r = bp.radius();
		return dx * dx + dz * dz <= (double) r * r && pos.y > origin.getY() + bp.voidY() - 25 && pos.y < origin.getY() + 70;
	}

	public boolean contains(Vec3 pos) {
		return bounds().inflate(16).contains(pos);
	}
}
