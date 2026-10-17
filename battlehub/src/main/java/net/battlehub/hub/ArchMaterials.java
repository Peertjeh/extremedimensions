package net.battlehub.hub;

import java.util.ArrayList;
import java.util.List;

import net.battlehub.BattleHub;
import net.battlehub.arch.Shape;
import net.battlehub.arch.mat.Catalog;
import net.battlehub.arch.mat.Material;
import net.battlehub.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Connects the architecture engine's material catalog to the game: families whose blocks this version does not
 * have are dropped (the resolver then uses their stand-ins), forms that are missing are left out, and, when asked,
 * block families from other mods are discovered from the registry (a block with matching stairs and a slab), with
 * the game's map colour as their colour and their tags guessed from the name.
 */
final class ArchMaterials {
	private ArchMaterials() {}

	private static boolean checked;
	private static final List<String> missing = new ArrayList<>();
	private static int discovered;

	static synchronized void prepare(boolean discoverModBlocks) {
		if (!checked) {
			checked = true;
			for (Material m : Catalog.families()) {
				if (m.cube() != null && !exists(m.cube())) {
					missing.add(m.name());
					Catalog.forget(m.name());
					continue;
				}
				// drop single forms the game does not have
				boolean changed = false;
				String[] forms = new String[Shape.values().length];
				for (Shape s : Shape.values()) {
					String id = m.id(s);
					if (id != null && !exists(id)) {
						missing.add(id);
						changed = true;
					} else {
						forms[s.ordinal()] = id;
					}
				}
				if (changed) {
					Catalog.forget(m.name());
					Catalog.add(new Material(m.name(), m.rgb(), m.tags(), forms[Shape.CUBE.ordinal()], forms[Shape.STAIRS.ordinal()], forms[Shape.SLAB.ordinal()],
							forms[Shape.WALL.ordinal()], forms[Shape.FENCE.ordinal()], forms[Shape.PANE.ordinal()], forms[Shape.LOG.ordinal()] == null ? null : m.pillar(),
							forms[Shape.TRAPDOOR.ordinal()], forms[Shape.DOOR.ordinal()], m.aged(), m.alt()));
				}
			}
			if (!missing.isEmpty()) BattleHub.LOGGER.warn("Architecture: {} catalog blocks are not in this game version: {}", missing.size(), missing);
		}
		if (discoverModBlocks && discovered == 0) {
			for (Block b : BuiltInRegistries.BLOCK) {
				Identifier id = BuiltInRegistries.BLOCK.getKey(b);
				if (id.getNamespace().equals("minecraft")) continue;
				String name = id.toString();
				String stem = name.endsWith("s") ? name.substring(0, name.length() - 1) : name;
				String stairs = exists(stem + "_stairs") ? stem + "_stairs" : null;
				String slab = exists(stem + "_slab") ? stem + "_slab" : null;
				if (stairs == null || slab == null) continue;
				String wall = exists(stem + "_wall") ? stem + "_wall" : null;
				BlockState st = b.defaultBlockState();
				int rgb = st.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
				Catalog.discover(name, rgb, stairs, slab, wall);
				discovered++;
			}
			BattleHub.LOGGER.info("Architecture: discovered {} block families from other mods", discovered);
		}
	}

	static boolean exists(String name) {
		return BuiltInRegistries.BLOCK.containsKey(Ids.id(name.contains(":") ? name : "minecraft:" + name));
	}

	/** Catalog blocks this game version lacks (for the self-test log). */
	static synchronized List<String> missing() {
		return List.copyOf(missing);
	}
}
