package com.extremedimensions.server;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import com.extremedimensions.ExtremeDimensions;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Every block a boss fight puts into the world, until the world has its own block back. Whatever part of a fight puts a
 * block down (an arena, the ground an Elder marks, thorns it raises, the ground an ultimate breaks, the Knot Mother's
 * anchors, the Starved One's nodes) takes a hold on that place here, and lets go of it when it is done with it. The first
 * time a place is held, the block the world had there is kept; once nothing holds the place any more, that block is
 * put back, if what stands there is still the fight's (its block in any shape, or what such a block turns into by
 * itself, as grass into dirt; or, where the fight left a hole, nothing or what gives way to anything). Whatever else
 * stands there by then (a block a player set, or nothing where a blast broke the fight's block out) is left as it is.
 * A place whose ground is not loaded when it is let go goes back as soon as it is: the world is never loaded only for
 * that.
 *
 * <p>So two parts of a fight on one place never undo each other wrongly: an arena's block marked by a blow goes back to
 * the arena's block as the mark fades, and to the world's own once the arena is gone, in whatever order the two let go.
 *
 * <p>Held places are written to a journal beside the world's data ({@code extremedimensions_fight_blocks.dat}), appended
 * to at the end of every tick that changed it (before any chunk saved after that tick can hold the block) and written
 * anew, short, when the world is saved. A server that stops puts back everything loaded first, before the world is
 * saved; one that crashes or is killed mid-fight finds its fights' blocks in the journal as it starts again, and puts
 * the world's own back as each of their chunks loads.
 */
public final class FightBlocks {
	private FightBlocks() {}

	/** A place a fight holds: what the world had there, the fight's block there now, and how many of its parts hold it. */
	private static final class Held {
		BlockState original;
		BlockState placed;
		int holders;

		Held(BlockState original, BlockState placed) {
			this.original = original;
			this.placed = placed;
		}
	}

	private static final Map<ServerLevel, Long2ObjectOpenHashMap<Held>> HELD = new HashMap<>();
	/** Places nothing holds any more whose ground was not loaded to put them back: by level, then by chunk. */
	private static final Map<ServerLevel, Map<Long, LongArrayList>> DUE = new HashMap<>();
	/** Places held in all levels (or waiting to go back). */
	private static int live;
	/** Places put back in one tick at most, of those waiting for their ground. */
	private static final int BUDGET = 800;

	// ------------------------------------------------------------------ the journal

	private static final int MAGIC = 0x45444642, VERSION = 1;
	private static final byte DIM = 1, STATE = 2, PUT = 3, DROP = 4;
	private static Path file;
	/** What is to be appended at the end of this tick. */
	private static final ByteArrayOutputStream PENDING = new ByteArrayOutputStream();
	private static final DataOutputStream OUT = new DataOutputStream(PENDING);
	/** The numbers the journal knows the blocks and levels by, since it was last written anew. */
	private static final Map<BlockState, Integer> STATES = new IdentityHashMap<>();
	private static final Map<ServerLevel, Integer> DIMS = new IdentityHashMap<>();
	/** Records in the journal since it was last written anew (it is written anew once there are many more than places held). */
	private static long records;
	private static boolean warned;

	// ------------------------------------------------------------------ holding

	/**
	 * A fight puts a block down here and takes a hold on the place; returns what stood there just before (what this part
	 * of the fight puts back when it is done; the world's own block is kept apart from it the first time).
	 */
	public static BlockState place(ServerLevel level, BlockPos pos, BlockState state, int flags) {
		BlockState before = level.getBlockState(pos);
		boolean set = level.setBlock(pos, state, flags);
		take(level, pos.asLong(), before, set ? state : before);
		return before;
	}

	/**
	 * A fight takes a hold on a block that came to stand here some other way (rubble come to rest, ground it only watches);
	 * {@code original} is what the world had here, if nothing of a fight holds the place yet.
	 */
	public static void hold(ServerLevel level, BlockPos pos, BlockState original) {
		take(level, pos.asLong(), original, Loaded.ready(level, pos) ? level.getBlockState(pos) : original);
	}

	/** A fight changes a block it already holds (puts back its own layer, moves a layer down): the holds stay as they are. */
	public static void put(ServerLevel level, BlockPos pos, BlockState state, int flags) {
		boolean set = level.setBlock(pos, state, flags);
		long k = pos.asLong();
		Held h = get(level, k);
		if (h != null) {
			BlockState now = set ? state : level.getBlockState(pos);
			if (h.placed != now) {
				h.placed = now;
				journal(level, k, h);
			}
		}
	}

	/**
	 * A part of a fight lets go of a place. Once nothing holds it, the world's own block goes back there (now, or once its
	 * ground is loaded), if what stands there is still the fight's.
	 */
	public static void release(ServerLevel level, BlockPos pos) {
		long k = pos.asLong();
		Held h = get(level, k);
		if (h == null) {
			return;
		}
		if (--h.holders > 0) {
			return;
		}
		h.holders = 0;
		if (!settle(level, k, h)) {
			due(level, k);
		}
	}

	/** The world keeps a fight's block for good (an arena that stays): what stands here now is the world's own from now on. */
	public static void keep(ServerLevel level, BlockPos pos) {
		long k = pos.asLong();
		Held h = get(level, k);
		if (h == null) {
			return;
		}
		h.original = Loaded.ready(level, pos) ? level.getBlockState(pos) : h.placed;
		journal(level, k, h);
		release(level, pos);
	}

	/** Whether a fight holds this place (or it waits to go back): what stands here drops nothing. */
	public static boolean holds(Level level, BlockPos pos) {
		if (live == 0 || !(level instanceof ServerLevel sl)) {
			return false;
		}
		Long2ObjectOpenHashMap<Held> m = HELD.get(sl);
		return m != null && m.containsKey(pos.asLong());
	}

	/** Places held (or waiting to go back) in all levels (for tests). */
	public static int count() {
		return live;
	}

	private static Held get(ServerLevel level, long k) {
		Long2ObjectOpenHashMap<Held> m = HELD.get(level);
		return m == null ? null : m.get(k);
	}

	private static void take(ServerLevel level, long k, BlockState before, BlockState now) {
		Long2ObjectOpenHashMap<Held> m = HELD.computeIfAbsent(level, x -> new Long2ObjectOpenHashMap<>());
		Held h = m.get(k);
		if (h == null) {
			h = new Held(before, now);
			h.holders = 1;
			m.put(k, h);
			live++;
			journal(level, k, h);
			return;
		}
		// (a place still waiting to go back, held again: it waits no more)
		h.holders = Math.max(0, h.holders) + 1;
		if (h.placed != now) {
			h.placed = now;
			journal(level, k, h);
		}
	}

	/** Puts the world's own block back at a place nothing holds; false if its ground is not loaded now. */
	private static boolean settle(ServerLevel level, long k, Held h) {
		BlockPos pos = BlockPos.of(k);
		if (!Loaded.ready(level, pos)) {
			return false;
		}
		BlockState now = level.getBlockState(pos);
		if (now != h.original && fights(now, h.placed)) {
			// (no neighbour updates: nothing beside it is knocked off, and nothing drops)
			level.setBlock(pos, h.original, 2 | 16);
		}
		forget(level, k);
		return true;
	}

	/**
	 * Whether what stands at a place is still the fight's, which left {@code placed} there: that block in any shape, or
	 * what it turns into by itself (grass dying back to dirt); and where the fight left nothing there (a hole, a plant
	 * cleared), nothing or what gives way to anything (a plant, water run in, snow). A block of the fight's that something
	 * broke out since is gone (what a fight destroys may stay destroyed): the world's own does not come back there, to hang
	 * over a crater blasted out under it.
	 */
	static boolean fights(BlockState now, BlockState placed) {
		if (now.is(placed.getBlock())) {
			return true;
		}
		if (placed.isAir() || placed.canBeReplaced()) {
			return now.isAir() || now.canBeReplaced() && !now.hasBlockEntity();
		}
		if (now.is(BlockTags.DIRT) && placed.is(BlockTags.DIRT)) {
			// (grass dying back to dirt under cover, or growing over dirt)
			return true;
		}
		if (placed.is(BlockTags.NYLIUM) && now.is(Blocks.NETHERRACK)) {
			return true;
		}
		return "chorus_flower".equals(id(placed)) && "chorus_plant".equals(id(now));
	}

	private static String id(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
	}

	private static void forget(ServerLevel level, long k) {
		Long2ObjectOpenHashMap<Held> m = HELD.get(level);
		if (m == null || m.remove(k) == null) {
			return;
		}
		live--;
		if (m.isEmpty()) {
			HELD.remove(level);
		}
		drop(level, k);
	}

	private static void due(ServerLevel level, long k) {
		long chunk = ((long) (BlockPos.getX(k) >> 4) << 32) | ((BlockPos.getZ(k) >> 4) & 0xFFFFFFFFL);
		DUE.computeIfAbsent(level, x -> new HashMap<>()).computeIfAbsent(chunk, c -> new LongArrayList()).add(k);
	}

	// ------------------------------------------------------------------ every tick

	/** What waited for its ground goes back where that has loaded; the journal gets this tick's changes. */
	public static void tick(MinecraftServer s) {
		if (!DUE.isEmpty()) {
			int budget = BUDGET;
			Iterator<Map.Entry<ServerLevel, Map<Long, LongArrayList>>> lit = DUE.entrySet().iterator();
			while (lit.hasNext() && budget > 0) {
				Map.Entry<ServerLevel, Map<Long, LongArrayList>> le = lit.next();
				ServerLevel level = le.getKey();
				Iterator<Map.Entry<Long, LongArrayList>> cit = le.getValue().entrySet().iterator();
				while (cit.hasNext() && budget > 0) {
					Map.Entry<Long, LongArrayList> ce = cit.next();
					long c = ce.getKey();
					if (!Loaded.chunk(level, (int) (c >> 32), (int) c)) {
						continue;
					}
					LongArrayList list = ce.getValue();
					while (!list.isEmpty() && budget > 0) {
						long k = list.removeLong(list.size() - 1);
						Held h = get(level, k);
						if (h != null && h.holders <= 0) {
							settle(level, k, h);
							budget--;
						}
					}
					if (list.isEmpty()) {
						cit.remove();
					}
				}
				if (le.getValue().isEmpty()) {
					lit.remove();
				}
			}
		}
		if (live == 0 && records > 0) {
			// Nothing held any more: the journal is written anew, empty
			rewrite();
		}
		flush();
	}

	/**
	 * Places of a level waiting to go back are put back now where their ground is loaded (for tests, whose ground counts as
	 * loaded only while the test runs); how many went back.
	 */
	public static int settle(ServerLevel level) {
		Map<Long, LongArrayList> chunks = DUE.remove(level);
		if (chunks == null) {
			return 0;
		}
		int n = 0;
		for (LongArrayList list : chunks.values()) {
			for (int i = 0; i < list.size(); i++) {
				long k = list.getLong(i);
				Held h = get(level, k);
				if (h == null || h.holders > 0) {
					continue;
				}
				if (settle(level, k, h)) {
					n++;
				} else {
					due(level, k);
				}
			}
		}
		return n;
	}

	// ------------------------------------------------------------------ the server starts, saves and stops

	/** The world loads: what a fight cut short left in it (a crash) is read from the journal, to go back as it loads. */
	public static void load(MinecraftServer s) {
		unload();
		file = s.getWorldPath(LevelResource.ROOT).resolve(ExtremeDimensions.MOD_ID + "_fight_blocks.dat");
		read(s);
		rewrite();
		if (live > 0) {
			ExtremeDimensions.LOGGER.info("{} blocks a boss fight left (it was cut short) go back as their ground loads", live);
		}
	}

	/** The world was saved: the journal is written anew, short, if it has grown long. */
	public static void saved() {
		if (records > 2L * live + 4096) {
			rewrite();
		} else {
			flush();
		}
	}

	/**
	 * The server stops, before the world is saved: every place a fight still holds goes back now where its ground is
	 * loaded; the rest stays in the journal and goes back once its ground loads after the next start.
	 */
	public static void stopping() {
		for (Map.Entry<ServerLevel, Long2ObjectOpenHashMap<Held>> e : new ArrayList<>(HELD.entrySet())) {
			ServerLevel level = e.getKey();
			for (long k : e.getValue().keySet().toLongArray()) {
				Held h = get(level, k);
				if (h != null) {
					h.holders = 0;
					settle(level, k, h);
				}
			}
		}
		DUE.clear();
		rewrite();
	}

	/** The server has stopped: nothing of this world is kept for the next. */
	public static void unload() {
		flush();
		HELD.clear();
		DUE.clear();
		live = 0;
		STATES.clear();
		DIMS.clear();
		PENDING.reset();
		records = 0;
		file = null;
	}

	/**
	 * As after a crash (for tests): everything held is forgotten as the server would forget it, and read back from the
	 * journal as the world's start would read it, to go back as it loads.
	 */
	public static void restart(MinecraftServer s) {
		flush();
		Path was = file;
		HELD.clear();
		DUE.clear();
		live = 0;
		STATES.clear();
		DIMS.clear();
		PENDING.reset();
		records = 0;
		file = was;
		if (file != null) {
			read(s);
			rewrite();
		}
	}

	/** The journal's file (for tests), or null before the world has loaded. */
	public static Path journal() {
		return file;
	}

	// ------------------------------------------------------------------ the journal's records

	private static void journal(ServerLevel level, long k, Held h) {
		if (file == null) {
			return;
		}
		try {
			int d = dim(level), o = state(h.original), p = state(h.placed);
			OUT.writeByte(PUT);
			OUT.writeInt(d);
			OUT.writeLong(k);
			OUT.writeInt(o);
			OUT.writeInt(p);
			records++;
		} catch (IOException e) {
			failed(e);
		}
	}

	private static void drop(ServerLevel level, long k) {
		if (file == null) {
			return;
		}
		try {
			int d = dim(level);
			OUT.writeByte(DROP);
			OUT.writeInt(d);
			OUT.writeLong(k);
			records++;
		} catch (IOException e) {
			failed(e);
		}
	}

	private static int dim(ServerLevel level) throws IOException {
		Integer id = DIMS.get(level);
		if (id == null) {
			id = DIMS.size();
			DIMS.put(level, id);
			OUT.writeByte(DIM);
			OUT.writeInt(id);
			OUT.writeUTF(level.dimension().identifier().toString());
		}
		return id;
	}

	private static int state(BlockState s) throws IOException {
		Integer id = STATES.get(s);
		if (id == null) {
			id = STATES.size();
			STATES.put(s, id);
			OUT.writeByte(STATE);
			OUT.writeInt(id);
			OUT.writeUTF(encode(s));
		}
		return id;
	}

	/** A block as text (its id and properties), as the game's own codec writes it: read back whatever else has changed. */
	private static String encode(BlockState s) {
		return BlockState.CODEC.encodeStart(JsonOps.INSTANCE, s).result().map(Object::toString).orElse("");
	}

	private static BlockState decode(String text) {
		if (text.isEmpty()) {
			return null;
		}
		try {
			return BlockState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(text)).result().orElse(null);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** This tick's records go to the end of the journal. */
	private static void flush() {
		if (file == null || PENDING.size() == 0) {
			return;
		}
		try {
			Files.write(file, PENDING.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			failed(e);
		}
		PENDING.reset();
	}

	/** The journal written anew: only the places held now. */
	private static void rewrite() {
		if (file == null) {
			return;
		}
		PENDING.reset();
		STATES.clear();
		DIMS.clear();
		records = 0;
		try {
			OUT.writeInt(MAGIC);
			OUT.writeInt(VERSION);
			for (Map.Entry<ServerLevel, Long2ObjectOpenHashMap<Held>> e : HELD.entrySet()) {
				for (Long2ObjectMap.Entry<Held> h : e.getValue().long2ObjectEntrySet()) {
					journal(e.getKey(), h.getLongKey(), h.getValue());
				}
			}
			byte[] bytes = PENDING.toByteArray();
			PENDING.reset();
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.write(tmp, bytes);
			try {
				Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			PENDING.reset();
			failed(e);
		}
	}

	/** The journal read: every place it holds is a place to go back, as its ground loads. */
	private static void read(MinecraftServer s) {
		if (file == null || !Files.exists(file)) {
			return;
		}
		Map<String, ServerLevel> levels = new HashMap<>();
		for (ServerLevel level : s.getAllLevels()) {
			levels.put(level.dimension().identifier().toString(), level);
		}
		Map<Integer, ServerLevel> dims = new HashMap<>();
		Map<Integer, BlockState> states = new HashMap<>();
		Map<ServerLevel, Long2ObjectOpenHashMap<Held>> found = new HashMap<>();
		int lost = 0;
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
			if (in.readInt() != MAGIC) {
				ExtremeDimensions.LOGGER.warn("{} is not a journal of fight blocks; it is left unread", file.getFileName());
				return;
			}
			in.readInt();
			while (true) {
				int tag = in.read();
				if (tag < 0) {
					break;
				}
				if (tag == DIM) {
					int id = in.readInt();
					dims.put(id, levels.get(in.readUTF()));
				} else if (tag == STATE) {
					int id = in.readInt();
					states.put(id, decode(in.readUTF()));
				} else if (tag == PUT) {
					ServerLevel level = dims.get(in.readInt());
					long k = in.readLong();
					BlockState o = states.get(in.readInt()), p = states.get(in.readInt());
					if (level == null) {
						continue;
					}
					if (o == null || p == null) {
						// (a block no longer in the game: its place is let be)
						lost++;
						Long2ObjectOpenHashMap<Held> m = found.get(level);
						if (m != null) {
							m.remove(k);
						}
						continue;
					}
					found.computeIfAbsent(level, x -> new Long2ObjectOpenHashMap<>()).put(k, new Held(o, p));
				} else if (tag == DROP) {
					ServerLevel level = dims.get(in.readInt());
					long k = in.readLong();
					Long2ObjectOpenHashMap<Held> m = level == null ? null : found.get(level);
					if (m != null) {
						m.remove(k);
					}
				} else {
					ExtremeDimensions.LOGGER.warn("The journal of fight blocks is damaged; what came before the damage is kept");
					break;
				}
			}
		} catch (EOFException e) {
			// A record cut off by the crash: everything before it counts
		} catch (IOException e) {
			ExtremeDimensions.LOGGER.error("Could not read the journal of fight blocks", e);
		}
		if (lost > 0) {
			ExtremeDimensions.LOGGER.warn("{} blocks in the journal of fight blocks are no longer in the game and are let be", lost);
		}
		for (Map.Entry<ServerLevel, Long2ObjectOpenHashMap<Held>> e : found.entrySet()) {
			ServerLevel level = e.getKey();
			Long2ObjectOpenHashMap<Held> m = HELD.computeIfAbsent(level, x -> new Long2ObjectOpenHashMap<>());
			for (Long2ObjectMap.Entry<Held> h : e.getValue().long2ObjectEntrySet()) {
				if (m.put(h.getLongKey(), h.getValue()) == null) {
					live++;
				}
				due(level, h.getLongKey());
			}
		}
	}

	private static void failed(IOException e) {
		if (!warned) {
			warned = true;
			ExtremeDimensions.LOGGER.error("Could not write the journal of fight blocks", e);
		}
	}
}
