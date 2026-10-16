package net.battlehub.arena;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Stream;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.core.MapMarkers;
import net.battlehub.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Maps built by hand: structure files (.nbt, saved with a structure block) in
 * {@code config/battlehub/maps/<mode>/} (or {@code maps/all/} for every mode that takes custom maps). Spots are
 * marked with structure blocks in Data mode: {@code spawn} (or {@code spawn:90}), {@code spectator} and the game's
 * own spots ({@code goal_red}, {@code chest}, {@code gold}, ...). The file is read here (palette, blocks, markers)
 * and turned into an ordinary arena blueprint, built a few blocks per tick like every arena; the markers are not
 * built. Block contents (chest items, sign text) are not kept.
 */
public final class CustomMaps {
	private CustomMaps() {}

	public record Marker(String name, float yaw, BlockPos pos) {}

	public static final class Map {
		public final String id;
		/** A mode id, or "all". */
		public final String mode;
		public final Vec3i size;
		public final List<Marker> markers;
		/** The blocks, in the file's own coordinates (packed BlockPos). */
		final java.util.Map<Long, BlockState> blocks;

		Map(String id, String mode, Vec3i size, List<Marker> markers, java.util.Map<Long, BlockState> blocks) {
			this.id = id;
			this.mode = mode;
			this.size = size;
			this.markers = markers;
			this.blocks = blocks;
		}

		/** Where the file's corner goes, relative to the arena origin (the map is centred on it). */
		BlockPos offset() {
			return new BlockPos(-size.getX() / 2, 0, -size.getZ() / 2);
		}

		public int spawns() {
			int n = 0;
			for (Marker m : markers) if (m.name().equals("spawn")) n++;
			return n;
		}

		public boolean has(String mark) {
			for (Marker m : markers) if (m.name().equals(mark)) return true;
			return false;
		}

		public int blockCount() {
			return blocks.size();
		}

		public String name() {
			String n = id.replace('_', ' ');
			return n.isEmpty() ? id : Character.toUpperCase(n.charAt(0)) + n.substring(1);
		}

		/** The blocks, spawns, spectator spot and marks, centred on the arena origin. */
		public Blueprint blueprint() {
			Blueprint bp = new Blueprint();
			BlockPos o = offset();
			for (Entry<Long, BlockState> e : blocks.entrySet()) {
				BlockPos p = BlockPos.of(e.getKey()).offset(o);
				bp.set(p.getX(), p.getY(), p.getZ(), e.getValue());
			}
			for (Marker m : markers) {
				BlockPos p = m.pos().offset(o);
				switch (m.name()) {
					case "spawn" -> bp.spawn(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, m.yaw());
					case "spectator" -> bp.spectator(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, m.yaw(), 30);
					default -> bp.mark(m.name(), p.getX(), p.getY(), p.getZ());
				}
			}
			if (!has("spectator")) {
				bp.spectator(0.5, size.getY() + 6, o.getZ() - 4 + 0.5, 0, 35);
			}
			bp.include(o.getX(), o.getY(), o.getZ());
			bp.include(o.getX() + size.getX() - 1, o.getY() + size.getY() - 1, o.getZ() + size.getZ() - 1);
			bp.voidY(-6);
			bp.radius(Math.max(size.getX(), size.getZ()) / 2 + 16);
			return bp;
		}
	}

	private static final List<Map> MAPS = new ArrayList<>();

	public static Path dir() {
		return ConfigManager.dir().resolve("maps");
	}

	/** Reads every map (at start-up and on /battlehub reload). */
	public static void load(MinecraftServer server) {
		MAPS.clear();
		Path root = dir();
		try {
			Files.createDirectories(root.resolve("all"));
		} catch (IOException e) {
			BattleHub.LOGGER.warn("Could not create {}", root);
			return;
		}
		try (Stream<Path> folders = Files.list(root)) {
			for (Path folder : (Iterable<Path>) folders.filter(Files::isDirectory)::iterator) {
				String mode = folder.getFileName().toString().toLowerCase(Locale.ROOT);
				if (!mode.equals("all") && GameMode.byId(mode) == null) {
					BattleHub.LOGGER.warn("Custom maps: {} is not a mode id (or 'all')", folder);
					continue;
				}
				try (Stream<Path> files = Files.list(folder)) {
					for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".nbt"))::iterator) {
						Map m = read(f, mode);
						if (m != null) MAPS.add(m);
					}
				}
			}
		} catch (IOException e) {
			BattleHub.LOGGER.warn("Custom maps could not be read: {}", e.toString());
		}
		if (!MAPS.isEmpty()) {
			BattleHub.LOGGER.info("Custom maps: {} loaded", MAPS.size());
		}
	}

	static Map read(Path file, String mode) {
		try {
			CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
			ListTag sizeTag = tag.getListOrEmpty("size");
			Vec3i size = new Vec3i(sizeTag.getIntOr(0, 0), sizeTag.getIntOr(1, 0), sizeTag.getIntOr(2, 0));
			if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
				BattleHub.LOGGER.warn("Custom map {} is empty", file);
				return null;
			}
			List<BlockState> palette = palette(tag.getListOrEmpty("palette"));
			List<Marker> markers = new ArrayList<>();
			java.util.Map<Long, BlockState> blocks = new LinkedHashMap<>();
			ListTag list = tag.getListOrEmpty("blocks");
			for (int i = 0; i < list.size(); i++) {
				CompoundTag b = list.getCompoundOrEmpty(i);
				ListTag pos = b.getListOrEmpty("pos");
				BlockPos p = new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0));
				CompoundTag nbt = b.getCompoundOrEmpty("nbt");
				if ("DATA".equalsIgnoreCase(nbt.getStringOr("mode", ""))) {
					// A marker: remembered, not built
					MapMarkers.Marker mk = MapMarkers.parse(nbt.getStringOr("metadata", ""));
					if (mk != null) markers.add(new Marker(mk.name(), mk.yaw(), p));
					continue;
				}
				int s = b.getIntOr("state", 0);
				BlockState st = s >= 0 && s < palette.size() ? palette.get(s) : null;
				if (st != null && !st.isAir()) {
					blocks.put(p.asLong(), st);
				}
			}
			return new Map(MapMarkers.id(file.getFileName().toString()), mode, size, markers, blocks);
		} catch (IOException | RuntimeException e) {
			BattleHub.LOGGER.warn("Custom map {} could not be read: {}", file, e.toString());
			return null;
		}
	}

	/** Block states of the palette: the block by name, then each property that it has. */
	static List<BlockState> palette(ListTag palette) {
		List<BlockState> out = new ArrayList<>();
		for (int i = 0; i < palette.size(); i++) {
			CompoundTag e = palette.getCompoundOrEmpty(i);
			Block block = Ids.block(e.getStringOr("Name", "minecraft:air"));
			BlockState st = block.defaultBlockState();
			CompoundTag props = e.getCompoundOrEmpty("Properties");
			for (String key : props.keySet()) {
				Property<?> prop = block.getStateDefinition().getProperty(key);
				if (prop != null) {
					st = with(st, prop, props.getStringOr(key, ""));
				}
			}
			out.add(st);
		}
		return out;
	}

	private static <T extends Comparable<T>> BlockState with(BlockState st, Property<T> prop, String value) {
		Optional<T> v = prop.getValue(value);
		return v.isPresent() ? st.setValue(prop, v.get()) : st;
	}

	/** Maps a mode can use: its own folder and "all", with enough spawns and every spot it needs. */
	public static List<Map> forMode(GameMode mode, int spawns, List<String> marks) {
		List<Map> out = new ArrayList<>();
		for (Map m : MAPS) {
			if (!(m.mode.equals("all") || m.mode.equals(mode.id())) || m.spawns() < spawns) continue;
			boolean ok = true;
			for (String mk : marks) ok &= m.has(mk);
			if (ok) out.add(m);
		}
		return out;
	}

	public static Map byId(String id) {
		for (Map m : MAPS) if (m.id.equals(id)) return m;
		return null;
	}

	public static List<Map> all() {
		return List.copyOf(MAPS);
	}

	public static void clear() {
		MAPS.clear();
	}
}
