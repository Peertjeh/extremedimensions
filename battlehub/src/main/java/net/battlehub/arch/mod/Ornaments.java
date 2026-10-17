package net.battlehub.arch.mod;

import java.util.Set;

import net.battlehub.arch.Cell;
import net.battlehub.arch.Dir;
import net.battlehub.arch.Shape;
import net.battlehub.arch.Slot;

/**
 * Small parts that finish a building at its smallest scale: finials, gargoyles, banners, hanging lanterns,
 * sconces, chimneys, dormers, floating crystals, urns, planters, trees and lamp posts. Each one is drawn at its
 * local origin; the assembler decides where they go (ornament follows the architecture, never the other way).
 */
public final class Ornaments {
	private Ornaments() {}

	static void register(Library l) {
		l.register(new Finial());
		l.register(new Gargoyle());
		l.register(new Banner());
		l.register(new Lantern());
		l.register(new Sconce());
		l.register(new Chimney());
		l.register(new Dormer());
		l.register(new Crystal());
		l.register(new Urn());
		l.register(new Planter());
		l.register(new Tree());
		l.register(new LampPost());
	}

	/** On top of a spire, gable or pinnacle (origin = the block above the tip). */
	static final class Finial extends Base {
		Finial() {
			super("orn.finial", Category.ORNAMENT, Set.of(), size(1, 1, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			String top = c.style().has("arcane") ? "end_rod" : c.style().has("gothic") ? "lightning_rod" : c.style().has("castle") ? "lightning_rod" : "end_rod";
			if (s.b("gold")) {
				c.decor(0, 0, 0, Cell.of(Slot.ACCENT, Cell.DETAIL));
				c.decor(0, 1, 0, Cell.literal(top, Cell.DETAIL));
			} else {
				c.decor(0, 0, 0, Cell.literal(top, Cell.DETAIL));
			}
		}
	}

	/** A spout beast sticking out of a wall top (origin = the wall block it leans out of; out is +z). */
	static final class Gargoyle extends Base {
		Gargoyle() {
			super("orn.gargoyle", Category.ORNAMENT, Set.of("gothic", "sacred"), size(1, 1, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			c.decor(0, 0, 1, Cell.stairs(Slot.TRIM, Dir.SOUTH, true, Cell.DETAIL));
			c.rich(0, 0, 2, Cell.slab(Slot.TRIM, true, Cell.DETAIL));
		}
	}

	/** A banner hanging on a wall (origin = the block in front of the wall; facing out = +z). */
	static final class Banner extends Base {
		Banner() {
			super("orn.banner", Category.ORNAMENT, Set.of(), size(1, 1, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			c.decor(0, 0, 0, Cell.literal(s.s("colour", "red") + "_wall_banner[facing=south]", Cell.DETAIL));
		}
	}

	/** A lantern hanging from a bracket on chains (origin = the bracket in front of the wall). */
	static final class Lantern extends Base {
		Lantern() {
			super("orn.lantern", Category.ORNAMENT, Set.of(), size(1, 1, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int chain = s.i("chain", 1);
			c.decor(0, 0, 0, Cell.stairs(Slot.TRIM, Dir.NORTH, true, Cell.DETAIL));
			for (int i = 1; i <= chain; i++) c.decor(0, -i, 0, Cell.literal("iron_chain", Cell.DETAIL));
			String lamp = c.style().has("arcane") ? "soul_lantern[hanging=true]" : "lantern[hanging=true]";
			c.decor(0, -chain - 1, 0, Cell.literal(lamp, Cell.DETAIL));
		}
	}

	/** A lantern standing on a small bracket on a wall (origin = the bracket). */
	static final class Sconce extends Base {
		Sconce() {
			super("orn.sconce", Category.ORNAMENT, Set.of(), size(1, 1, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			c.decor(0, 0, 0, Cell.slab(Slot.TRIM, true, Cell.DETAIL));
			c.decor(0, 1, 0, Cell.literal(c.style().has("arcane") ? "soul_lantern" : "lantern", Cell.DETAIL));
		}
	}

	/** A chimney stack from y = 0 up {@code h} blocks, smoking at rich detail. */
	static final class Chimney extends Base {
		Chimney() {
			super("orn.chimney", Category.ORNAMENT, Set.of("town", "rustic", "castle"), size(1, 2, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int h = s.h();
			for (int y = 0; y < h; y++) c.set(0, y, 0, Cell.of(Slot.FOUNDATION, Cell.DETAIL));
			c.set(0, h, 0, Cell.slab(Slot.FOUNDATION, false, Cell.DETAIL));
			c.rich(0, h - 1, 0, Cell.literal("campfire[lit=true,signal_fire=false]", Cell.FEATURE));
		}
	}

	/**
	 * A dormer on a roof slope (origin = the dormer's front-left corner at the eave; out = +z): a small gabled box
	 * with a window, three wide.
	 */
	static final class Dormer extends Base {
		Dormer() {
			super("orn.dormer", Category.ORNAMENT, Set.of("town", "palace", "gothic", "castle"), size(3, 3, 2), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int depth = s.d();
			for (int z = -depth; z <= 0; z++) {
				for (int y = 0; y < 2; y++) {
					c.set(0, y, z, Cell.of(Slot.WALL_ALT, Cell.DETAIL));
					c.set(2, y, z, Cell.of(Slot.WALL_ALT, Cell.DETAIL));
					c.set(1, y, z, z == 0 ? Windows.pane(Slot.GLASS).prio(Cell.FEATURE) : Cell.air(Cell.FEATURE));
				}
				c.set(0, 2, z, Cell.stairs(Slot.ROOF, Dir.EAST, false, Cell.FEATURE));
				c.set(2, 2, z, Cell.stairs(Slot.ROOF, Dir.WEST, false, Cell.FEATURE));
				c.set(1, 2, z, Cell.of(Slot.ROOF, Cell.FEATURE));
				c.set(1, 3, z, Cell.slab(Slot.ROOF_EDGE, false, Cell.FEATURE));
			}
			c.set(1, 2, 1, Cell.stairs(Slot.TRIM, Dir.NORTH, true, Cell.FEATURE));
		}
	}

	/** A floating crystal (meant to float: the structure check leaves it alone). */
	static final class Crystal extends Base {
		Crystal() {
			super("orn.crystal", Category.ORNAMENT, Set.of("arcane", "magic"), size(1, 1, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int size = s.i("size", 1);
			for (int y = 0; y < size + 1; y++) c.set(0, y, 0, Cell.of(Slot.MAGIC, Cell.DETAIL).flag(Cell.FLOAT));
			if (size >= 2) {
				for (Dir d : Dir.values()) c.set(d.dx, size / 2, d.dz, Cell.of(Slot.MAGIC, Cell.DETAIL).flag(Cell.FLOAT));
			}
			c.decor(0, size + 1, 0, Cell.literal("amethyst_cluster[facing=up]", Cell.DETAIL).flag(Cell.FLOAT));
			c.decor(0, -1, 0, Cell.literal("amethyst_cluster[facing=down]", Cell.DETAIL).flag(Cell.FLOAT));
		}
	}

	/** An urn on a pedestal. */
	static final class Urn extends Base {
		Urn() {
			super("orn.urn", Category.ORNAMENT, Set.of(), size(1, 1, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			c.decor(0, 0, 0, Cell.of(Slot.TRIM, Cell.DETAIL));
			c.decor(0, 1, 0, Cell.literal("decorated_pot", Cell.DETAIL));
		}
	}

	/** A small planter: a trim border round soil with flowers and a little shrub. */
	static final class Planter extends Base {
		Planter() {
			super("orn.planter", Category.ORNAMENT, Set.of(), size(3, 1, 3), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int w = s.w(), d = s.d();
			String[] flowers = {"poppy", "dandelion", "cornflower", "allium", "azure_bluet", "oxeye_daisy", "red_tulip", "white_tulip", "lily_of_the_valley"};
			for (int x = 0; x < w; x++) {
				for (int z = 0; z < d; z++) {
					boolean border = x == 0 || z == 0 || x == w - 1 || z == d - 1;
					if (border) {
						c.set(x, 0, z, Cell.slab(Slot.TRIM, false, Cell.DETAIL));
					} else {
						c.set(x, -1, z, Cell.of(Slot.GROUND, Cell.DETAIL));
						c.set(x, 0, z, Cell.literal((x + z) % 3 == 0 ? "flowering_azalea" : c.decor.pick(flowers), Cell.DETAIL));
					}
				}
			}
		}
	}

	/** A tree: a trunk with a round crown of leaves (gardens, courtyards). Option height. */
	static final class Tree extends Base {
		Tree() {
			super("orn.tree", Category.ORNAMENT, Set.of(), size(1, 3, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int h = s.i("height", 5);
			double r = s.num("crown", 2.4);
			for (int y = 0; y < h; y++) c.set(0, y, 0, Cell.log(Slot.TIMBER, 'y', Cell.DETAIL));
			int cy = h;
			int ir = (int) Math.ceil(r);
			for (int x = -ir; x <= ir; x++) {
				for (int y = -ir; y <= ir; y++) {
					for (int z = -ir; z <= ir; z++) {
						double d = Math.sqrt(x * x + y * y * 1.6 + z * z);
						if (d > r + 0.2) continue;
						if (d > r - 0.8 && c.decor.next() < 0.25) continue;
						c.setIfEmpty(x, cy + y, z, Cell.of(Slot.LEAVES, Cell.DETAIL));
					}
				}
			}
		}
	}

	/** A lamp post: a wall post or fence column with a lantern on top. */
	static final class LampPost extends Base {
		LampPost() {
			super("orn.lamppost", Category.ORNAMENT, Set.of(), size(1, 3, 1), ANY);
		}

		@Override
		public void build(Ctx c, Spec s) {
			int h = s.i("height", 3);
			c.set(0, 0, 0, Cell.of(Slot.TRIM, Cell.DETAIL));
			for (int y = 1; y < h; y++) c.set(0, y, 0, Cell.shape(Slot.TRIM, Shape.WALL, Cell.DETAIL));
			c.set(0, h, 0, Cell.literal(c.style().has("arcane") ? "soul_lantern" : "lantern", Cell.DETAIL));
		}
	}
}
