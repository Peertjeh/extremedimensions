package com.extremedimensions.lore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.data.WarmGate;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.journal.MarginNote;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.server.WorldFx;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Warm chains (page XVIII: "I found a chain in the obsidian of one, still warm. I did not follow where it led.").
 *
 * <p>Each ruined portal in the Overworld is looked at once, ever, the first time a player comes near it (it is known by
 * where its structure starts). Some have a chain lying over the ground into the foot of the frame, warm: embers rise
 * from it. Whoever repairs such a gate and goes through it bound comes out near the Held's chamber in the Nether, raised
 * the first time anyone crosses. The first chamber a player raises holds the Held's letter and chain; any later one only
 * what another of the held left.
 */
public final class WarmChains {
	private WarmChains() {}

	/** Links of chain laid in front of a warm gate. */
	static final int TRAIL = 7;
	/** How close to a warm gate the last Overworld step must have been for a crossing to count as through it. */
	static final double THROUGH = 10;
	/** How close a player comes to a chain to find it warm, and to a chamber to have found it. */
	static final double FOUND = 5, REACHED = 6;

	/** The chunk each player was in when the ruined portals round them were last looked at. */
	private static final Map<UUID, Long> LOOKED = new HashMap<>();

	public static void tick(MinecraftServer s) {
		int t = s.getTickCount();
		if (t % 20 != 11) {
			return;
		}
		ServerLevel ow = s.overworld(), nether = s.getLevel(Level.NETHER);
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			PlayerData d = DataStore.peek(p.getUUID());
			if (d == null || d.away) {
				continue;
			}
			// (a spectator sees the embers too, but finds nothing)
			boolean playing = !p.isSpectator();
			if (p.level() == ow) {
				// (looked again only when the player has come into another chunk, or now and then for a gate that was not all there)
				if (t % 40 == 11 && Config.WARM_CHAIN_CHANCE > 0) {
					long chunk = (long) (p.getBlockX() >> 4) << 32 | ((p.getBlockZ() >> 4) & 0xffffffffL);
					Long was = LOOKED.put(p.getUUID(), chunk);
					if (was == null || was != chunk || t % 600 == 11) {
						look(ow, p.blockPosition());
					}
				}
				for (WarmGate g : DataStore.warmGates()) {
					double d2 = g.distSq(p.getX(), p.getY(), p.getZ());
					if (d2 < 48 * 48) {
						glow(ow, g);
					}
					if (playing && d2 < 24 * 24 && near(g, p)) {
						found(p, d, g);
					}
				}
			} else if (p.level() == nether && playing) {
				for (WarmGate g : DataStore.warmGates()) {
					if (!g.chamber) {
						continue;
					}
					double dx = g.cx + 0.5 - p.getX(), dy = g.cy - p.getY(), dz = g.cz + 0.5 - p.getZ();
					double d2 = dx * dx + dy * dy + dz * dz;
					if (d2 < REACHED * REACHED) {
						reached(p, d, g);
					} else if (d2 < 40 * 40 && !d.heldFound && t % 160 == 11) {
						// The chain calls from where it is held
						WorldFx.soundIn(Level.NETHER.identifier().toString(), "minecraft:block.chain.step", "block", g.cx + 0.5, g.cy + 1, g.cz + 0.5, 2.0f, 0.5f);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ finding ruined portals

	/** Looks at the ruined portals in the chunks around a position that no one has looked at yet. */
	public static void look(ServerLevel level, BlockPos at) {
		Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos q = at.offset(dx * 16, 0, dz * 16);
				if (!level.isLoaded(q)) {
					continue;
				}
				for (Map.Entry<Structure, LongSet> e : level.structureManager().getAllStructuresAt(q).entrySet()) {
					if (!structures.wrapAsHolder(e.getKey()).is(StructureTags.RUINED_PORTAL)) {
						continue;
					}
					List<StructureStart> starts = new ArrayList<>();
					level.structureManager().fillStartsForStructure(e.getKey(), e.getValue(), starts::add);
					for (StructureStart start : starts) {
						BoundingBox box = start.getBoundingBox();
						consider(level, box.minX() + "|" + box.minY() + "|" + box.minZ(), box);
					}
				}
			}
		}
	}

	/**
	 * A ruined portal seen for the first time: decided once whether its chain is warm (from the world's seed, so the same
	 * world always has the same warm gates), and if so the chain is laid. Returns the warm gate, or null.
	 */
	public static WarmGate consider(ServerLevel level, String key, BoundingBox box) {
		if (DataStore.gatesLooked().contains(key)) {
			return null;
		}
		for (BlockPos corner : new BlockPos[] {new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.minY(), box.maxZ()),
				new BlockPos(box.minX(), box.minY(), box.maxZ()), new BlockPos(box.maxX(), box.minY(), box.minZ())}) {
			if (!level.isLoaded(corner)) {
				return null; // looked at again once all of it is there
			}
		}
		DataStore.gatesLooked().add(key);
		DataStore.markDirty();
		RandomSource rnd = RandomSource.create(level.getSeed() ^ (key.hashCode() * 0x9E3779B97F4A7C15L));
		if (rnd.nextDouble() >= Config.WARM_CHAIN_CHANCE) {
			return null;
		}
		return warm(level, key, box);
	}

	/** Finds the frame in a ruined portal's box and lays a warm chain in front of it. Null if it has no frame to speak of. */
	public static WarmGate warm(ServerLevel level, String key, BoundingBox box) {
		int n = 0, minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos q : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState s = level.getBlockState(q);
			if (s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN)) {
				n++;
				minX = Math.min(minX, q.getX());
				maxX = Math.max(maxX, q.getX());
				minY = Math.min(minY, q.getY());
				minZ = Math.min(minZ, q.getZ());
				maxZ = Math.max(maxZ, q.getZ());
			}
		}
		if (n < 4) {
			return null;
		}
		boolean alongX = maxX - minX >= maxZ - minZ;
		BlockPos foot = new BlockPos((minX + maxX) / 2, minY, (minZ + maxZ) / 2);
		// The chain lies on the side with more open ground
		Direction a = alongX ? Direction.SOUTH : Direction.EAST, facing = open(level, foot, a) >= open(level, foot, a.getOpposite()) ? a : a.getOpposite();
		WarmGate g = new WarmGate(key, foot.getX(), foot.getY(), foot.getZ(), facing.getName());
		lay(level, g);
		DataStore.warmGates().add(g);
		DataStore.markDirty();
		return g;
	}

	/** How much open ground lies before a frame's foot in a direction. */
	private static int open(ServerLevel level, BlockPos foot, Direction f) {
		int n = 0;
		for (int k = 1; k <= TRAIL; k++) {
			if (ground(level, foot.relative(f, k)) != null) {
				n++;
			}
		}
		return n;
	}

	/** Lays the chain from the wilds to the foot of the frame, following the ground (a link or two lost along the way). */
	static void lay(ServerLevel level, WarmGate g) {
		Direction f = Direction.byName(g.facing);
		BlockPos foot = new BlockPos(g.x, g.y, g.z);
		int lost = Math.floorMod(g.key.hashCode(), 5);
		for (int k = 1; k <= TRAIL; k++) {
			BlockPos q = ground(level, foot.relative(f, k));
			if (q != null && (k == 1 || (k + lost) % 5 != 0)) {
				level.setBlock(q, HeldChamber.chain(f.getAxis()), 2);
				g.links.add(new int[] {q.getX(), q.getY(), q.getZ()});
			}
		}
	}

	/** Where a chain can lie in this column near a height: a free block on firm ground, out of water. Null if none. */
	private static BlockPos ground(ServerLevel level, BlockPos at) {
		for (int dy = 3; dy >= -5; dy--) {
			BlockPos q = at.above(dy);
			BlockState s = level.getBlockState(q), below = level.getBlockState(q.below());
			if ((s.isAir() || s.canBeReplaced()) && s.getFluidState().isEmpty() && below.isFaceSturdy(level, q.below(), Direction.UP)) {
				return q;
			}
		}
		return null;
	}

	/** Embers rise from the chain now and then: it is still warm. */
	private static void glow(ServerLevel level, WarmGate g) {
		if (g.links.isEmpty()) {
			return;
		}
		for (int i = 0; i < 2; i++) {
			int[] l = g.links.get(level.getRandom().nextInt(g.links.size()));
			WorldFx.particleIn(level, "minecraft:small_flame", l[0] + 0.5, l[1] + 0.15, l[2] + 0.5, 0.2, 0.05, 0.2, 0.004, 2);
			if (level.getRandom().nextInt(3) == 0) {
				WorldFx.particleIn(level, "minecraft:smoke", l[0] + 0.5, l[1] + 0.3, l[2] + 0.5, 0.15, 0.1, 0.15, 0.01, 1);
			}
		}
	}

	private static boolean near(WarmGate g, ServerPlayer p) {
		if (g.distSq(p.getX(), p.getY(), p.getZ()) < FOUND * FOUND) {
			return true;
		}
		for (int[] l : g.links) {
			double dx = l[0] + 0.5 - p.getX(), dy = l[1] - p.getY(), dz = l[2] + 0.5 - p.getZ();
			if (dx * dx + dy * dy + dz * dz < FOUND * FOUND) {
				return true;
			}
		}
		return false;
	}

	/** A player comes to a warm chain: the page about the ruined gates, and a note beside it in another hand. */
	public static void found(ServerPlayer p, PlayerData d, WarmGate g) {
		if (d.notes.contains(MarginNote.RUINED_GATE.id)) {
			return;
		}
		Journal.unlock(p, d, JournalEntry.RUINED_GATE);
		Journal.note(p, d, MarginNote.RUINED_GATE);
		Advancements.grant(p, Advancements.WARM_CHAIN);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.warm_chain.found"), true);
		WorldFx.soundSelf(p, "minecraft:block.chain.step", 1.0f, 0.5f);
		DataStore.markDirty();
	}

	// ------------------------------------------------------------------ the other end

	/** The warm gate a crossing came through (the last Overworld step was in its frame), or null. */
	public static WarmGate through(double x, double y, double z) {
		for (WarmGate g : DataStore.warmGates()) {
			if (g.distSq(x, y, z) < THROUGH * THROUGH) {
				return g;
			}
		}
		return null;
	}

	/**
	 * A player arrived in the Nether: if they came through a warm gate, its chamber is raised near them (once per gate)
	 * and they hear the chain. Returns the gate, or null.
	 */
	public static WarmGate crossed(ServerPlayer p, PlayerData d, ServerLevel level, BlockPos arrival) {
		if (!d.hasLast) {
			return null;
		}
		WarmGate g = through(d.lastX, d.lastY, d.lastZ);
		if (g == null) {
			return null;
		}
		if (!g.chamber && !raise(level, g, arrival, p.getUUID())) {
			return g;
		}
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.warm_chain.crossed"), true);
		WorldFx.soundIn(Level.NETHER.identifier().toString(), "minecraft:block.chain.place", "block", g.cx + 0.5, g.cy + 1, g.cz + 0.5, 2.0f, 0.5f);
		return g;
	}

	/** Raises a warm gate's chamber near an arrival. The first one a player raises holds the Held's letter and chain. */
	public static boolean raise(ServerLevel level, WarmGate g, BlockPos arrival, UUID by) {
		HeldChamber.Site site = HeldChamber.find(level, arrival);
		if (site == null) {
			return false;
		}
		String uid = by.toString();
		boolean first = DataStore.warmGates().stream().noneMatch(o -> o.chamber && o.full && uid.equals(o.by));
		HeldChamber.build(level, site, first ? Loot.HELDS_CHAMBER : Loot.HELD_REMAINS, level.getSeed() ^ g.key.hashCode());
		int dist = (int) Math.round(Math.sqrt(site.centre().distSqr(new BlockPos(arrival.getX(), site.centre().getY(), arrival.getZ()))));
		HeldChamber.passage(level, site, dist);
		g.chamber = true;
		g.full = first;
		g.by = uid;
		g.cx = site.centre().getX();
		g.cy = site.centre().getY();
		g.cz = site.centre().getZ();
		DataStore.markDirty();
		return true;
	}

	/** A player reached a chamber. */
	static void reached(ServerPlayer p, PlayerData d, WarmGate g) {
		if (d.heldFound) {
			return;
		}
		d.heldFound = true;
		Advancements.grant(p, Advancements.HELD_FOUND);
		WorldFx.soundSelf(p, "minecraft:block.chain.break", 0.7f, 0.5f);
		DataStore.markDirty();
	}
}
