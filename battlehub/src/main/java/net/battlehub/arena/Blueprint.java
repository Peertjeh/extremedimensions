package net.battlehub.arena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An arena design in coordinates relative to the slot origin: the blocks to place plus named points (spawns,
 * zones, flags, checkpoints). Later placements overwrite earlier ones.
 */
public final class Blueprint {
	/** Where a player starts, relative to the origin; yaw 0 faces south (+Z). */
	public record Spawn(double x, double y, double z, float yaw, float pitch) {}

	private final Map<Long, BlockState> blocks = new HashMap<>();
	private final List<Spawn> spawns = new ArrayList<>();
	private final Map<String, List<BlockPos>> marks = new LinkedHashMap<>();
	private Spawn spectator = new Spawn(0, 15, -20, 0, 35);
	/** Falling below this relative Y counts as falling out of the arena. */
	private int voidY = -10;
	/** Spectators are kept inside this many blocks horizontally from the origin. */
	private int radius = 40;
	private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
	private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

	public void set(int x, int y, int z, BlockState state) {
		if (state == null) {
			return;
		}
		blocks.put(BlockPos.asLong(x, y, z), state);
		minX = Math.min(minX, x);
		minY = Math.min(minY, y);
		minZ = Math.min(minZ, z);
		maxX = Math.max(maxX, x);
		maxY = Math.max(maxY, y);
		maxZ = Math.max(maxZ, z);
	}

	/**
	 * Grows the arena box to include this point without placing anything (room for what players build; the box
	 * is cleared when the match is over).
	 */
	public void include(int x, int y, int z) {
		minX = Math.min(minX, x);
		minY = Math.min(minY, y);
		minZ = Math.min(minZ, z);
		maxX = Math.max(maxX, x);
		maxY = Math.max(maxY, y);
		maxZ = Math.max(maxZ, z);
	}

	/** Only sets the block if nothing is there yet. */
	public void setIfEmpty(int x, int y, int z, BlockState state) {
		if (!blocks.containsKey(BlockPos.asLong(x, y, z))) {
			set(x, y, z, state);
		}
	}

	public void remove(int x, int y, int z) {
		blocks.remove(BlockPos.asLong(x, y, z));
	}

	public BlockState get(int x, int y, int z) {
		BlockState s = blocks.get(BlockPos.asLong(x, y, z));
		return s == null ? Blocks.AIR.defaultBlockState() : s;
	}

	public boolean has(int x, int y, int z) {
		BlockState s = blocks.get(BlockPos.asLong(x, y, z));
		return s != null && !s.isAir();
	}

	public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
				for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
					set(x, y, z, state);
				}
			}
		}
	}

	public Map<Long, BlockState> blocks() {
		return blocks;
	}

	public void spawn(double x, double y, double z, float yaw) {
		spawns.add(new Spawn(x, y, z, yaw, 0));
	}

	/** {@code count} spawns on a circle around the origin, everybody facing the middle. */
	public void spawnRing(int count, double radius, double y) {
		for (int i = 0; i < count; i++) {
			double a = i * Math.PI * 2 / count;
			double x = Math.round(Math.cos(a) * radius) + 0.5;
			double z = Math.round(Math.sin(a) * radius) + 0.5;
			float yaw = (float) Math.toDegrees(Math.atan2(x - 0.5, -(z - 0.5)));
			spawns.add(new Spawn(x, y, z, yaw, 0));
		}
	}

	public List<Spawn> spawns() {
		return spawns;
	}

	public void spectator(double x, double y, double z, float yaw, float pitch) {
		spectator = new Spawn(x, y, z, yaw, pitch);
	}

	public Spawn spectator() {
		return spectator;
	}

	public void mark(String name, int x, int y, int z) {
		marks.computeIfAbsent(name, k -> new ArrayList<>()).add(new BlockPos(x, y, z));
	}

	public List<BlockPos> marks(String name) {
		return marks.getOrDefault(name, List.of());
	}

	public BlockPos markOne(String name) {
		List<BlockPos> l = marks(name);
		return l.isEmpty() ? BlockPos.ZERO : l.get(0);
	}

	public void voidY(int y) {
		this.voidY = y;
	}

	public int voidY() {
		return voidY;
	}

	public void radius(int r) {
		this.radius = r;
	}

	public int radius() {
		return radius;
	}

	public boolean isEmpty() {
		return blocks.isEmpty();
	}

	public int minX() { return minX; }
	public int minY() { return minY; }
	public int minZ() { return minZ; }
	public int maxX() { return maxX; }
	public int maxY() { return maxY; }
	public int maxZ() { return maxZ; }
}
