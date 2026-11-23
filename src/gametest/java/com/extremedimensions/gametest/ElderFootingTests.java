package com.extremedimensions.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.extremedimensions.config.Config;
import com.extremedimensions.elder.ElderBoss;
import com.extremedimensions.elder.Ferryman;
import com.extremedimensions.elder.FerrymanService;
import com.extremedimensions.elder.KnotMother;
import com.extremedimensions.elder.Matriarch;
import com.extremedimensions.elder.MatriarchService;
import com.extremedimensions.elder.PaleMason;
import com.extremedimensions.elder.PaleMasonService;
import com.extremedimensions.elder.Weaver;
import com.extremedimensions.elder.WeaverService;
import com.extremedimensions.server.BossGround;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Elders stand on the ground and never in it. A floor raised round one, as high as twenty blocks over its feet,
 * has it up on top at once; half over a pit it stands at the height of the rim, not sunk into it; a roof over its head
 * and a ledge across its body it never climbs onto; a wall too high to step onto stops it (it goes along it, never into
 * it or up onto it); and the Pale Mason's blink sets him down on top of broken ground. Each test watches its Elder every
 * tick: once the ground the test laid has had a tick to settle, no block it could stand on is ever in its feet.
 *
 * <p>An Elder is far bigger than a test's own space, so each test lays its own ground (a floor of stone, and on it what
 * the test asks) well past that space, over the invisible walls and roof the game puts round a test (no ground to an
 * Elder), and puts it all back as it was afterwards. Each runs alone (an Elder reaches far past its own test), with no
 * arena, with nothing an Elder does tearing up the ground, and with its next blow a long way off.
 */
public class ElderFootingTests {
	/** The Elders called here. */
	private enum Kind {
		MATRIARCH, FERRYMAN, WEAVER, MASON
	}

	/** Where the Elder stands in most of these tests (relative to the test); its prey stands seven blocks from it. */
	private static final double AT_X = 3.5, AT_Z = 3.5;
	/** The ground laid for most of these tests, round the test's own space (a boat's hull reaches seven and a half blocks out). */
	private static final int X0 = -6, X1 = 13, Z0 = -6, Z1 = 13;
	private static final BlockState STONE = Blocks.STONE.defaultBlockState(), AIR = Blocks.AIR.defaultBlockState();

	// ------------------------------------------------------------------ a floor raised round it

	@GameTest(environment = Alone.FOOTING_MATRIARCH_RAISED, maxTicks = 70)
	public void aFloorRaisedRoundTheMatriarchHasHerUpOnIt(GameTestHelper helper) {
		raised(helper, Kind.MATRIARCH, 8);
	}

	@GameTest(environment = Alone.FOOTING_FERRYMAN_RAISED, maxTicks = 70)
	public void aFloorRaisedRoundTheFerrymanHasHisBoatUpOnIt(GameTestHelper helper) {
		raised(helper, Kind.FERRYMAN, 12);
	}

	@GameTest(environment = Alone.FOOTING_WEAVER_RAISED, maxTicks = 70)
	public void aFloorRaisedUnderTheWeaverHasItFloatOverIt(GameTestHelper helper) {
		raised(helper, Kind.WEAVER, 16);
	}

	@GameTest(environment = Alone.FOOTING_MASON_RAISED, maxTicks = 70)
	public void aFloorRaisedRoundThePaleMasonHasHimUpOnIt(GameTestHelper helper) {
		raised(helper, Kind.MASON, 20);
	}

	/**
	 * It stands on a floor; then the ground rises all round it at once, {@code height} blocks over its feet (an arena's
	 * floor built late, a cliff thrown up under it): a moment later it is up on top, never inside, and there it stays.
	 */
	private static void raised(GameTestHelper helper, Kind kind, int height) {
		Scene s = new Scene(helper);
		int top = height + 1;
		s.floor(X0, Z0, X1, Z1, top + 4);
		s.prey = s.player("ft-" + kind.ordinal() + "-raised", AT_X, 1, AT_Z + 7);
		ElderBoss b = s.call(kind, AT_X, 1, AT_Z, false);
		double hover = hover(b);
		s.watch(3, 60);
		s.at(10, () -> {
			s.check(Math.abs(s.y(b) - (1 + hover)) < 0.25, "the %s did not settle on the floor first (its feet at %.2f)", kind, s.y(b));
			s.fill(X0, 1, Z0, X1, height, Z1, STONE);
		});
		double[] low = {Double.MAX_VALUE};
		for (int t = 13; t <= 60; t++) {
			s.at(t, () -> low[0] = Math.min(low[0], s.y(b)));
		}
		s.at(13, () -> s.check(s.y(b) >= top - 0.05, "three ticks after the ground rose %s blocks round it the %s is still in it (its feet at %.2f, the top at %s)",
				height, kind, s.y(b), top));
		s.at(60, () -> {
			s.check(low[0] >= top - 0.05, "the %s sank back into the raised ground (its feet down to %.2f, the top at %s)", kind, low[0], top);
			s.check(Math.abs(s.y(b) - (top + hover)) < 0.2, "the %s does not stand on the raised ground (its feet at %.2f, the top at %s)", kind, s.y(b), top);
			s.end();
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ half over a pit

	@GameTest(environment = Alone.FOOTING_MATRIARCH_RIM, maxTicks = 60)
	public void theMatriarchHalfOverAPitStandsAtItsRim(GameTestHelper helper) {
		rim(helper, Kind.MATRIARCH);
	}

	@GameTest(environment = Alone.FOOTING_FERRYMAN_RIM, maxTicks = 60)
	public void theFerrymanHalfOverAPitStandsAtItsRim(GameTestHelper helper) {
		rim(helper, Kind.FERRYMAN);
	}

	@GameTest(environment = Alone.FOOTING_WEAVER_RIM, maxTicks = 60)
	public void theWeaverHalfOverAPitFloatsOverItsRim(GameTestHelper helper) {
		rim(helper, Kind.WEAVER);
	}

	@GameTest(environment = Alone.FOOTING_MASON_RIM, maxTicks = 60)
	public void thePaleMasonHalfOverAPitStandsAtItsRim(GameTestHelper helper) {
		rim(helper, Kind.MASON);
	}

	/**
	 * A plateau six blocks high with a pit six deep cut into one side of it, the Elder standing half over the pit and
	 * half on its rim: it stands at the rim's height, not sunk into the rim (it used to stand at the mean of the ground
	 * under it, several blocks into the rim).
	 */
	private static void rim(GameTestHelper helper, Kind kind) {
		Scene s = new Scene(helper);
		s.floor(X0, Z0, X1, Z1, 12);
		// (the rim runs along x = 4: the Elder's columns 1 to 3 are over the pit, 4 and 5 on the rim)
		s.fill(4, 1, Z0, X1, 6, Z1, STONE);
		s.prey = s.player("ft-" + kind.ordinal() + "-rim", AT_X, 7, AT_Z + 7);
		ElderBoss b = s.call(kind, AT_X, 7, AT_Z, false);
		double hover = hover(b);
		s.watch(3, 50);
		double[] low = {Double.MAX_VALUE};
		for (int t = 3; t <= 50; t++) {
			s.at(t, () -> low[0] = Math.min(low[0], s.y(b)));
		}
		s.at(50, () -> {
			s.check(low[0] >= 7 - 0.05, "the %s sank into the rim of the pit (its feet down to %.2f, the rim at 7)", kind, low[0]);
			s.check(Math.abs(s.y(b) - (7 + hover)) < 0.2, "the %s does not stand at the rim's height (its feet at %.2f, the rim at 7)", kind, s.y(b));
			s.end();
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ what hangs over it

	@GameTest(environment = Alone.FOOTING_MATRIARCH_LEDGE, maxTicks = 60)
	public void theMatriarchNeverClimbsOntoWhatHangsOverHer(GameTestHelper helper) {
		ledge(helper, Kind.MATRIARCH);
	}

	@GameTest(environment = Alone.FOOTING_FERRYMAN_LEDGE, maxTicks = 60)
	public void theFerrymanNeverClimbsOntoWhatHangsOverHim(GameTestHelper helper) {
		ledge(helper, Kind.FERRYMAN);
	}

	@GameTest(environment = Alone.FOOTING_WEAVER_LEDGE, maxTicks = 60)
	public void theWeaverNeverClimbsOntoWhatHangsOverIt(GameTestHelper helper) {
		ledge(helper, Kind.WEAVER);
	}

	@GameTest(environment = Alone.FOOTING_MASON_LEDGE, maxTicks = 60)
	public void thePaleMasonNeverClimbsOntoWhatHangsOverHim(GameTestHelper helper) {
		ledge(helper, Kind.MASON);
	}

	/**
	 * A roof of stone three blocks over its head, and a ledge two blocks thick across most of its body four blocks over
	 * its feet, with air between: it stays on the floor under them (it used to climb onto anything within eight blocks
	 * over its feet).
	 */
	private static void ledge(GameTestHelper helper, Kind kind) {
		Scene s = new Scene(helper);
		s.floor(X0, Z0, X1, Z1, 26);
		s.prey = s.player("ft-" + kind.ordinal() + "-ledge", AT_X, 1, AT_Z + 7);
		ElderBoss b = s.call(kind, AT_X, 1, AT_Z, false);
		double hover = hover(b);
		int bottom = Mth.floor(1 + hover), roof = Mth.ceil(1 + hover + b.getBbHeight()) + 3;
		s.at(5, () -> {
			s.fill(X0, roof, Z0, X1, roof, Z1, STONE);
			s.fill(Mth.floor(AT_X), bottom + 4, Z0, X1, bottom + 5, Z1, STONE);
		});
		s.watch(3, 50);
		double[] high = {-Double.MAX_VALUE};
		for (int t = 8; t <= 50; t++) {
			s.at(t, () -> high[0] = Math.max(high[0], s.y(b)));
		}
		s.at(50, () -> {
			s.check(high[0] <= 1 + hover + 0.2, "the %s climbed onto what hangs over it (its feet up to %.2f; the ledge at %s, the roof at %s)", kind, high[0],
					bottom + 4, roof);
			s.check(Math.abs(s.y(b) - (1 + hover)) < 0.2, "the %s does not stand on the floor under the ledge (its feet at %.2f)", kind, s.y(b));
			s.end();
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ a wall in its way

	@GameTest(environment = Alone.FOOTING_MATRIARCH_WALL, maxTicks = 100)
	public void aHighWallStopsTheMatriarch(GameTestHelper helper) {
		wall(helper, Kind.MATRIARCH);
	}

	@GameTest(environment = Alone.FOOTING_FERRYMAN_WALL, maxTicks = 100)
	public void aHighWallStopsTheFerrymansBoat(GameTestHelper helper) {
		wall(helper, Kind.FERRYMAN);
	}

	@GameTest(environment = Alone.FOOTING_WEAVER_WALL, maxTicks = 100)
	public void aHighWallStopsTheWeaver(GameTestHelper helper) {
		wall(helper, Kind.WEAVER);
	}

	@GameTest(environment = Alone.FOOTING_MASON_WALL, maxTicks = 100)
	public void aHighWallStopsThePaleMason(GameTestHelper helper) {
		wall(helper, Kind.MASON);
	}

	/**
	 * Whole and free to move, the Elder goes after its prey, who stands beyond a wall twelve blocks high (two thick, and
	 * longer than it can go along in the time): it comes up to the wall, and never walks into it or up onto it.
	 */
	private static void wall(GameTestHelper helper, Kind kind) {
		Scene s = new Scene(helper);
		int wx = 8, top = 12, x0 = -12, x1 = 14, z0 = -14, z1 = 21;
		s.floor(x0, z0, x1, z1, top + 3);
		s.fill(wx, 1, z0, wx + 1, top, z1, STONE);
		s.prey = s.player("ft-" + kind.ordinal() + "-wall", 22.5, 1, AT_Z);
		ElderBoss b = s.call(kind, 0.5, 1, AT_Z, true);
		double hover = hover(b);
		// (its front, its body's or its boat's, two and a half blocks short of the wall)
		double reach = Math.max(b.getBbWidth() / 2, b.hullReach());
		s.put(b, wx - 2.5 - reach, 1, AT_Z);
		s.watch(3, 90);
		double[] front = {-Double.MAX_VALUE}, high = {-Double.MAX_VALUE};
		for (int t = 3; t <= 90; t++) {
			s.at(t, () -> {
				front[0] = Math.max(front[0], b.getX() - s.origin.getX() + reach);
				high[0] = Math.max(high[0], s.y(b));
			});
		}
		s.at(90, () -> {
			s.check(front[0] >= wx - 1.5, "the %s never came up to the wall (its front reached %.2f, the wall at %s)", kind, front[0], wx);
			s.check(high[0] <= 1 + hover + 0.25, "the %s climbed the wall (its feet up to %.2f, the wall's top at %s)", kind, high[0], top + 1);
			s.end();
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ the Pale Mason's blink

	/**
	 * Teleport Strike: the Pale Mason blinks out of the air beside his prey onto broken ground (steps of up to three
	 * blocks, every two blocks), and comes down on top of the highest of it under him: neither in it nor hanging over it.
	 */
	@GameTest(environment = Alone.FOOTING_MASON_BLINK, maxTicks = 60)
	public void thePaleMasonBlinksOntoBrokenGroundAndStandsOnIt(GameTestHelper helper) {
		Scene s = new Scene(helper);
		s.floor(X0, Z0, X1, Z1, 10);
		for (int x = X0; x <= X1; x++) {
			for (int z = Z0; z <= Z1; z++) {
				if (bump(x, z) > 0) {
					s.fill(x, 1, z, x, bump(x, z), z, STONE);
				}
			}
		}
		s.prey = s.player("ft-mason-blink", AT_X, 1 + bump(3, 3), AT_Z);
		ElderBoss b = s.call(Kind.MASON, 10.5, 1 + bump(10, 10), 10.5, false);
		PaleMasonService.force(b, PaleMason.STRIKE);
		s.watch(3, 45);
		double[] last = {b.getX(), b.getZ()};
		int[] blinked = {-1};
		for (int t = 1; t <= 45; t++) {
			s.at(t, () -> {
				double dx = b.getX() - last[0], dz = b.getZ() - last[1];
				last[0] = b.getX();
				last[1] = b.getZ();
				if (blinked[0] >= 0 || dx * dx + dz * dz < 9) {
					return;
				}
				blinked[0] = s.tick;
				double g = groundUnder(s.level, b);
				s.check(!Double.isNaN(g) && b.getY() >= g - 0.05 && b.getY() <= g + 0.5,
						"the Pale Mason blinked down with his feet at %.2f, the top of the ground under him at %.2f", s.y(b), g - s.origin.getY());
				double px = b.getX() - s.prey.getX(), pz = b.getZ() - s.prey.getZ();
				s.check(px * px + pz * pz < 6 * 6, "the Pale Mason blinked down %.1f blocks from his prey", Math.sqrt(px * px + pz * pz));
			});
		}
		s.at(45, () -> {
			s.check(blinked[0] >= 0, "the Pale Mason never blinked");
			s.end();
			helper.succeed();
		});
	}

	/** Broken ground: in squares of two blocks, steps of up to three blocks over the floor. */
	private static int bump(int x, int z) {
		return Math.floorMod(Math.floorDiv(x, 2) * 7 + Math.floorDiv(z, 2) * 3, 4);
	}

	// ------------------------------------------------------------------ the ground under a giant

	/**
	 * The ground under a giant, column by column: on a slab it is half a block up; leaves and barriers are none; with
	 * its feet in blocks it climbs out of them (past a gap of a single block, which is no room); a ledge over its feet
	 * with air between is never stood on, nor a block over a body in the air; and a footprint stands on the highest of
	 * its columns. (All within the test's own space, on a floor of its own a block up, put back afterwards.)
	 */
	@GameTest(maxTicks = 20)
	public void theGroundUnderAGiant(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		Map<BlockPos, BlockState> was = new LinkedHashMap<>();
		java.util.function.BiConsumer<BlockPos, BlockState> set = (pos, s) -> {
			if (level.getBlockEntity(pos) == null) {
				was.putIfAbsent(pos, level.getBlockState(pos));
				level.setBlock(pos, s, 2);
			}
		};
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				set.accept(o.offset(x, 1, z), STONE);
				for (int y = 2; y <= 7; y++) {
					set.accept(o.offset(x, y, z), AIR);
				}
			}
		}
		set.accept(o.offset(2, 2, 1), Blocks.STONE_SLAB.defaultBlockState());
		set.accept(o.offset(3, 2, 1), Blocks.OAK_LEAVES.defaultBlockState());
		set.accept(o.offset(3, 3, 1), Blocks.OAK_LEAVES.defaultBlockState());
		set.accept(o.offset(4, 2, 1), Blocks.BARRIER.defaultBlockState());
		set.accept(o.offset(4, 3, 1), Blocks.BARRIER.defaultBlockState());
		for (int y = 5; y <= 6; y++) {
			set.accept(o.offset(5, y, 1), STONE);
		}
		for (int y = 2; y <= 4; y++) {
			set.accept(o.offset(6, y, 1), STONE);
		}
		set.accept(o.offset(1, 2, 3), STONE);
		set.accept(o.offset(1, 4, 3), STONE);
		set.accept(o.offset(2, 2, 3), STONE);
		set.accept(o.offset(2, 3, 3), STONE);
		set.accept(o.offset(2, 6, 3), STONE);
		set.accept(o.offset(2, 2, 6), STONE);
		set.accept(o.offset(6, 6, 6), STONE);
		double feet = o.getY() + 2;
		try {
			ground(helper, level, o, 1, 1, feet, 2, "on the floor");
			ground(helper, level, o, 2, 1, feet, 2.5, "on a slab");
			ground(helper, level, o, 3, 1, feet, 2, "in leaves");
			ground(helper, level, o, 4, 1, feet, 2, "in barriers");
			ground(helper, level, o, 5, 1, feet, 2, "under a ledge three blocks over its feet");
			ground(helper, level, o, 6, 1, feet, 5, "with its feet three blocks into stone");
			ground(helper, level, o, 1, 3, feet, 5, "in stone with a gap of one block in it");
			ground(helper, level, o, 2, 3, feet, 4, "in stone with two blocks of room over it, and more stone over that");
			ground(helper, level, o, 5, 5, o.getY() + 7, 2, "five blocks over the floor");
			ground(helper, level, o, 6, 6, o.getY() + 3, 2, "in the air, under a block three blocks over it");
			double box = BossGround.box(level, o.getX() + 2.5, o.getZ() + 6.5, 1.5, feet) - o.getY();
			CurseTests.check(helper, Math.abs(box - 3) < 1e-6, "a footprint with a block in it stands at %s, not on the block (3)", box);
		} finally {
			for (Map.Entry<BlockPos, BlockState> e : was.entrySet()) {
				level.setBlock(e.getKey(), e.getValue(), 2);
			}
		}
		helper.succeed();
	}

	private static void ground(GameTestHelper helper, ServerLevel level, BlockPos o, int x, int z, double feet, double want, String where) {
		double g = BossGround.column(level, o.getX() + x, o.getZ() + z, feet) - o.getY();
		CurseTests.check(helper, Math.abs(g - want) < 1e-6, "%s, the ground is at %s, not %s", where, g, want);
	}

	// ------------------------------------------------------------------ the scene

	/** How high its service holds it over the ground: the Weaver floats, the boat and the Mason ride a hair over it. */
	private static double hover(ElderBoss b) {
		return b instanceof Weaver ? 2.5 : b instanceof Matriarch ? 0 : 0.1;
	}

	/**
	 * The columns of an Elder's footprint, as the Elders look at them: under its body (a little in from its edges), and
	 * for a boat along its hull's keel and to each side of it.
	 */
	private static List<int[]> columns(ElderBoss b) {
		List<int[]> out = new ArrayList<>();
		double x = b.getX(), z = b.getZ(), half = b.getBbWidth() / 2;
		for (int bx = Mth.floor(x - half + 0.1); bx <= Mth.floor(x + half - 0.1); bx++) {
			for (int bz = Mth.floor(z - half + 0.1); bz <= Mth.floor(z + half - 0.1); bz++) {
				out.add(new int[] {bx, bz});
			}
		}
		double reach = b.hullReach();
		if (reach > half) {
			double a = Math.toRadians(b.getYRot()), fx = -Math.sin(a), fz = Math.cos(a);
			int n = Math.max(1, (int) Math.ceil((reach - half) / 0.7));
			for (int i = 0; i <= n; i++) {
				double t = half + (reach - half) * i / n;
				for (int end = -1; end <= 1; end += 2) {
					for (int side = -1; side <= 1; side++) {
						out.add(new int[] {Mth.floor(x + fx * t * end - fz * 1.2 * side), Mth.floor(z + fz * t * end + fx * 1.2 * side)});
					}
				}
			}
		}
		return out;
	}

	/**
	 * A block it could stand on reaching up into the bottom block of an Elder's body, under any part of it, described;
	 * null if there is none. (Barriers are no ground.)
	 */
	private static String inFeet(ServerLevel level, ElderBoss b) {
		double y = b.getY();
		for (int[] c : columns(b)) {
			for (int by = Mth.floor(y + 0.05); by <= Mth.floor(y + 0.95); by++) {
				BlockPos pos = new BlockPos(c[0], by, c[1]);
				BlockState s = level.getBlockState(pos);
				if (s.isAir() || s.is(Blocks.BARRIER)) {
					continue;
				}
				VoxelShape shape = s.getCollisionShape(level, pos);
				if (!shape.isEmpty() && by + shape.max(Direction.Axis.Y) > y + 0.05 && by + shape.min(Direction.Axis.Y) < y + 0.95) {
					return String.format(Locale.ROOT, "%s at %s is in its feet (it stands at %.2f %.2f %.2f)", s, pos, b.getX(), y, b.getZ());
				}
			}
		}
		return null;
	}

	/**
	 * The top of the highest block it could stand on under any part of an Elder, from the bottom block of its body down
	 * (24 blocks at most); NaN with none.
	 */
	private static double groundUnder(ServerLevel level, ElderBoss b) {
		double best = Double.NaN;
		int from = Mth.floor(b.getY() + 0.95);
		for (int[] c : columns(b)) {
			for (int by = from; by >= from - 24; by--) {
				BlockPos pos = new BlockPos(c[0], by, c[1]);
				BlockState s = level.getBlockState(pos);
				VoxelShape shape = s.getCollisionShape(level, pos);
				if (!s.is(Blocks.BARRIER) && !shape.isEmpty()) {
					double top = by + shape.max(Direction.Axis.Y);
					best = Double.isNaN(best) ? top : Math.max(best, top);
					break;
				}
			}
		}
		return best;
	}

	/**
	 * The settings these tests hold while they run: no arena, nothing an Elder does tearing up the ground, an Elder still
	 * forming for a minute, and its next blow a long way off. Returns what puts them back.
	 */
	private static Runnable quiet() {
		int arena = Config.ELDER_ARENA, grief = Config.ELDER_GRIEFING;
		double spawn = Config.ELDER_SPAWN_SECONDS, attack = Config.ELDER_ATTACK_SPEED, strike = Config.PALE_MASON_STRIKE_DAMAGE;
		Config.ELDER_ARENA = 0;
		Config.ELDER_GRIEFING = 0;
		Config.ELDER_SPAWN_SECONDS = 60;
		Config.ELDER_ATTACK_SPEED = 0.1;
		Config.PALE_MASON_STRIKE_DAMAGE = 0;
		return () -> {
			Config.ELDER_ARENA = arena;
			Config.ELDER_GRIEFING = grief;
			Config.ELDER_SPAWN_SECONDS = spawn;
			Config.ELDER_ATTACK_SPEED = attack;
			Config.PALE_MASON_STRIKE_DAMAGE = strike;
		};
	}

	/**
	 * Forces the chunks round the test's space, where its ground is laid and its Elder appears. They are not let go
	 * again (the game keeps every test's chunks forced the same way, and letting go of one a neighbouring test stands in
	 * would stop that test); the test server forgets them when it stops.
	 */
	private static void forceAround(GameTestHelper helper, int r) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		int cx = o.getX() >> 4, cz = o.getZ() >> 4;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				level.setChunkForced(cx + dx, cz + dz, true);
			}
		}
	}

	/**
	 * One test: its Elder and its prey, the ground laid for it, and the settings it changed, all put back by
	 * {@link #end} (at once when a check fails, or a step throws).
	 */
	private static final class Scene {
		final GameTestHelper helper;
		final ServerLevel level;
		final BlockPos origin;
		/** What stood where the test laid a block, to be put back. */
		final Map<BlockPos, BlockState> was = new LinkedHashMap<>();
		final Runnable settings;
		final net.minecraft.world.Difficulty difficulty;
		ServerPlayer prey;
		ElderBoss elder;
		/** The test's tick now, and the last tick at which what the test laid or moved may still be in the Elder's feet. */
		int tick, grace = 2;
		boolean over;

		Scene(GameTestHelper helper) {
			this.helper = helper;
			this.level = helper.getLevel();
			this.origin = helper.absolutePos(BlockPos.ZERO);
			forceAround(helper, 2);
			this.settings = quiet();
			// (on peaceful no Elder would stay to be watched)
			this.difficulty = level.getDifficulty();
			level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		}

		/** Its feet's height over the test's own floor level. */
		double y(ElderBoss b) {
			return b.getY() - origin.getY();
		}

		/** A creative player (none of its blows lands on one) standing at (x, y, z), relative to the test. */
		ServerPlayer player(String name, double x, double y, double z) {
			ServerPlayer p = TestPlayers.creative(helper, name);
			p.teleportTo(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
			return p;
		}

		/** Lays a block (relative to the test), keeping what stood there; never over a block that holds data. */
		void set(int x, int y, int z, BlockState s) {
			BlockPos pos = origin.offset(x, y, z);
			BlockState now = level.getBlockState(pos);
			if (now == s || level.getBlockEntity(pos) != null) {
				return;
			}
			was.putIfAbsent(pos, now);
			level.setBlock(pos, s, 2);
			grace = Math.max(grace, tick + 2);
		}

		void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
			for (int x = x0; x <= x1; x++) {
				for (int y = y0; y <= y1; y++) {
					for (int z = z0; z <= z1; z++) {
						set(x, y, z, s);
					}
				}
			}
		}

		/** A floor of stone (its top at 1) over the ground of the test, with open air over it up to {@code clear}. */
		void floor(int x0, int z0, int x1, int z1, int clear) {
			fill(x0, 0, z0, x1, 0, z1, STONE);
			fill(x0, 1, z0, x1, clear, z1, AIR);
		}

		/**
		 * Calls an Elder of this kind for the prey and sets it down at (x, y, z) (relative; y the ground under it, a Weaver
		 * hung its own height over that). With {@code idle} it is whole and free to move at once, its forming cut short (it
		 * goes after its prey); else it is still forming and holds still where it is put (the Matriarch, who forms under the
		 * ground, is made whole at once and holds still by her prey, who must stand within nine blocks of her).
		 */
		ElderBoss call(Kind kind, double x, double y, double z, boolean idle) {
			ServerPlayer p = prey;
			BlockPos spot = origin.offset(Mth.floor(x), Mth.floor(y), Mth.floor(z));
			ElderBoss b = switch (kind) {
				case MATRIARCH -> {
					List<Matriarch> had = level.getEntitiesOfClass(Matriarch.class, p.getBoundingBox().inflate(64));
					MatriarchService.summon(p, "", spot);
					Matriarch m = level.getEntitiesOfClass(Matriarch.class, p.getBoundingBox().inflate(64)).stream().filter(e -> !had.contains(e)).findFirst()
							.orElse(null);
					if (m != null) {
						MatriarchService.force(m, ElderBoss.IDLE);
					}
					yield m;
				}
				case FERRYMAN -> FerrymanService.summonAt(p, spot);
				case MASON -> PaleMasonService.summonAt(p, spot);
				case WEAVER -> {
					List<Weaver> had = level.getEntitiesOfClass(Weaver.class, p.getBoundingBox().inflate(64));
					WeaverService.summon(p, spot.offset(12, 0, 0));
					yield level.getEntitiesOfClass(Weaver.class, p.getBoundingBox().inflate(64)).stream()
							.filter(w -> !had.contains(w) && !(w instanceof KnotMother)).findFirst().orElse(null);
				}
			};
			elder = b;
			check(b != null, "the %s did not come", kind);
			if (idle) {
				switch (kind) {
					case FERRYMAN -> FerrymanService.force(b, ElderBoss.IDLE);
					case MASON -> PaleMasonService.force(b, ElderBoss.IDLE);
					case WEAVER -> WeaverService.force(b, ElderBoss.IDLE);
					default -> {
					}
				}
			}
			put(b, x, y, z);
			return b;
		}

		/** Sets the Elder down at (x, y, z) (relative; y the ground under it, its own height over that). */
		void put(ElderBoss b, double x, double y, double z) {
			b.teleportTo(origin.getX() + x, origin.getY() + y + hover(b), origin.getZ() + z);
			grace = Math.max(grace, tick + 2);
		}

		/** Runs {@code step} at tick {@code t} of the test (unless it is over), putting everything back should it fail. */
		void at(int t, Runnable step) {
			helper.runAfterDelay(t, () -> {
				if (over) {
					return;
				}
				tick = t;
				try {
					step.run();
				} catch (RuntimeException e) {
					end();
					throw e;
				}
			});
		}

		/**
		 * Every tick from {@code from} to {@code to}: nothing it could stand on is in the Elder's feet (once what the test
		 * laid or moved has had its tick: the Elder moves after the test's own steps).
		 */
		void watch(int from, int to) {
			for (int t = from; t <= to; t++) {
				at(t, () -> {
					if (tick > grace) {
						String in = inFeet(level, elder);
						check(in == null, "tick %s: %s", tick, in);
					}
				});
			}
		}

		void check(boolean ok, String message, Object... args) {
			if (!ok) {
				end();
				// (written out here: the game's own message reads %s alone, not the heights to two places)
				throw helper.assertionException("%s", String.format(Locale.ROOT, message, args));
			}
		}

		/** Puts everything back: the Elder sent away, the ground as it was, the settings. */
		void end() {
			if (over) {
				return;
			}
			over = true;
			try {
				if (elder != null && !elder.isRemoved()) {
					send(elder);
				}
			} finally {
				for (Map.Entry<BlockPos, BlockState> e : was.entrySet()) {
					level.setBlock(e.getKey(), e.getValue(), 2);
				}
				settings.run();
				level.getServer().setDifficulty(difficulty, true);
			}
		}
	}

	/** Sends an Elder away, whole (no fight is won). */
	private static void send(ElderBoss b) {
		if (b instanceof Matriarch m) {
			MatriarchService.fade(m);
		} else if (b instanceof Ferryman f) {
			FerrymanService.fade(f);
		} else if (b instanceof PaleMason m) {
			PaleMasonService.fade(m);
		} else if (b instanceof Weaver w) {
			WeaverService.fade(w);
		}
	}
}
