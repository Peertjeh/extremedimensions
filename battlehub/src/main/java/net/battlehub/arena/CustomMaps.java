package net.battlehub.arena;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.core.MapMarkers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Maps built by hand: structure files (.nbt, saved with a structure block) in
 * {@code config/battlehub/maps/<mode>/} (or {@code maps/all/} for every mode that takes custom maps). Spots are
 * marked with structure blocks in Data mode: {@code spawn} (or {@code spawn:90}), {@code spectator} and the game's
 * own spots ({@code goal_red}, {@code chest}, {@code gold}, ...). The markers are removed when the map is placed.
 */
public final class CustomMaps {
	private CustomMaps() {}

	public record Marker(String name, float yaw, BlockPos pos) {}

	public static final class Map {
		public final String id;
		/** A mode id, or "all". */
		public final String mode;
		final StructureTemplate template;
		public final Vec3i size;
		public final List<Marker> markers;

		Map(String id, String mode, StructureTemplate template, Vec3i size, List<Marker> markers) {
			this.id = id;
			this.mode = mode;
			this.template = template;
			this.size = size;
			this.markers = markers;
		}

		/** Where the template's corner goes, relative to the arena origin (the map is centred on it). */
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

		public String name() {
			String n = id.replace('_', ' ');
			return n.isEmpty() ? id : Character.toUpperCase(n.charAt(0)) + n.substring(1);
		}

		/** Spawns, the spectator spot, marks and the box; the blocks themselves are placed by {@link #place}. */
		public Blueprint blueprint() {
			Blueprint bp = new Blueprint();
			BlockPos o = offset();
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

		/** Places the map in the arena and takes the markers out. */
		public void place(Arena arena) {
			BlockPos corner = arena.origin().offset(offset());
			template.placeInWorld(arena.level(), corner, corner, new StructurePlaceSettings(), RandomSource.create(), 2 | 16);
			for (Marker m : markers) {
				arena.level().setBlock(corner.offset(m.pos()), Blocks.AIR.defaultBlockState(), 2 | 16);
			}
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
						Map m = read(server, f, mode);
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

	static Map read(MinecraftServer server, Path file, String mode) {
		try {
			CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
			// Read straight from the file (saved by a structure block of this game version)
			StructureTemplate t = new StructureTemplate();
			t.load(server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), tag);
			Vec3i size = t.getSize();
			if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
				BattleHub.LOGGER.warn("Custom map {} is empty", file);
				return null;
			}
			List<Marker> markers = markers(tag);
			if (markers.isEmpty()) {
				for (StructureTemplate.StructureBlockInfo info : t.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.STRUCTURE_BLOCK)) {
					CompoundTag nbt = info.nbt();
					if (nbt == null || !"DATA".equalsIgnoreCase(nbt.getStringOr("mode", ""))) continue;
					MapMarkers.Marker mk = MapMarkers.parse(nbt.getStringOr("metadata", ""));
					if (mk != null) markers.add(new Marker(mk.name(), mk.yaw(), info.pos()));
				}
			}
			String id = MapMarkers.id(file.getFileName().toString());
			return new Map(id, mode, t, size, markers);
		} catch (IOException | RuntimeException e) {
			BattleHub.LOGGER.warn("Custom map {} could not be read: {}", file, e.toString());
			return null;
		}
	}

	/**
	 * The markers straight from the file: every block with structure block data in Data mode ("blocks", each with
	 * "pos" and "nbt"). Read from the file itself, so it does not depend on what the template keeps.
	 */
	static List<Marker> markers(CompoundTag root) {
		List<Marker> out = new ArrayList<>();
		net.minecraft.nbt.ListTag blocks = root.getListOrEmpty("blocks");
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag b = blocks.getCompoundOrEmpty(i);
			CompoundTag nbt = b.getCompoundOrEmpty("nbt");
			if (!"DATA".equalsIgnoreCase(nbt.getStringOr("mode", ""))) continue;
			MapMarkers.Marker mk = MapMarkers.parse(nbt.getStringOr("metadata", ""));
			if (mk == null) continue;
			net.minecraft.nbt.ListTag pos = b.getListOrEmpty("pos");
			out.add(new Marker(mk.name(), mk.yaw(), new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0))));
		}
		return out;
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
