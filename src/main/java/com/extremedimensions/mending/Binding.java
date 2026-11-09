package com.extremedimensions.mending;

import java.util.List;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.seam.SeamArchitect;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.joml.Quaternionf;
import com.extremedimensions.mixin.DisplayAccess;
import com.extremedimensions.server.BlockFlight;
import com.extremedimensions.server.FightBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The four pillars round the Heart, and their hold on the Starved One (3.33).
 *
 * <p>The pillars are the obelisks of the Heart's crown, each on its own island some sixty blocks out on the diagonals.
 * As the Starved One wakes, a node opens on top of the lowest piece of each shaft, where it first breaks (some forty
 * blocks up, as the dragon's crystals stand on their towers): a heart of sculk with a glowing copy of itself turning
 * over it, seen through the stone, and a strand of hush stone runs out to each island from the Heart's rim. While a
 * node feeds it, a beam of souls runs from it to the Starved One, and it heals ({@code starved_heal} of its health a
 * second for each). Quench a node (strike it with an arrow or any thrown thing, break it, or feed it
 * {@code starved_socket_silk} Seam Silk) and it stays dark for {@code starved_socket_seconds}. With
 * {@code starved_heal_quench} quenched at once it heals no more; with {@code starved_sockets} (all four) it is pulled
 * down before whoever is nearest and held stunned for {@code starved_bind_seconds}, taking {@code starved_bound_damage}
 * times the harm; free, it takes {@code starved_unbound_damage}. The nodes' own time stands still while it is held.
 * Once the fight is over (won, or everyone gone) each node goes again and its shaft stands broken as before: a node is
 * held for the fight by {@link FightBlocks}, so not even a crash leaves one behind.
 */
public final class Binding {
	private Binding() {}

	public static final int PILLARS = 4;
	/** How far out the crown stands its obelisks (see {@link SeamArchitect#crown}). */
	public static final int RING = 64;
	/** A node while it feeds the Starved One, and once it is quenched. */
	public static final Block FEEDING = Blocks.SCULK_CATALYST, QUENCHED = Blocks.REINFORCED_DEEPSLATE;
	/** The highest a shaft's lowest piece may reach above its plinth. */
	private static final int MAX_SHAFT = 80;

	private static final BlockPos[] NODES = new BlockPos[PILLARS];
	/** Silk fed to each node towards quenching it. */
	private static final int[] FED = new int[PILLARS];
	/** Ticks each node stays quenched (0: it feeds the Starved One). */
	private static final int[] QUENCH = new int[PILLARS];
	private static final boolean[] STRAND = new boolean[PILLARS];
	/** The glowing heart turning over each feeding node. */
	private static final Display.BlockDisplay[] MARK = new Display.BlockDisplay[PILLARS];
	/** Ticks the Starved One is still held stunned, and where. */
	private static int bound;
	private static Vec3 bindAt;
	/** The pillars whose chains hold it. */
	private static final boolean[] HOLDING = new boolean[PILLARS];
	/** Whether all four quenched may stun it again (once one has fed it since the last stun). */
	private static boolean stunReady = true;

	/** Forgets everything (the server stops, or the fight is over and the world tidied). */
	static void forget() {
		for (int i = 0; i < PILLARS; i++) {
			unmark(i);
		}
		java.util.Arrays.fill(NODES, null);
		java.util.Arrays.fill(FED, 0);
		java.util.Arrays.fill(QUENCH, 0);
		java.util.Arrays.fill(STRAND, false);
		java.util.Arrays.fill(HOLDING, false);
		bound = 0;
		bindAt = null;
		stunReady = true;
	}

	public static boolean bound() {
		return bound > 0;
	}

	/** Where it is held (null when free). */
	static Vec3 bindAt() {
		return bound > 0 ? bindAt : null;
	}

	/** The centre of pillar {@code i}, where the crown stands it: x, z. */
	public static int[] pillar(int i) {
		double a = i * Math.PI / 2 + Math.PI / 4;
		return new int[] {MendingService.HX + (int) Math.round(Math.cos(a) * RING), MendingService.HZ + (int) Math.round(Math.sin(a) * RING)};
	}

	/** The way from pillar {@code i} towards the Heart, as a step on each axis (the pillars stand on the diagonals). */
	private static int[] towards(int i) {
		int[] c = pillar(i);
		return new int[] {-Integer.signum(c[0] - MendingService.HX), -Integer.signum(c[1] - MendingService.HZ)};
	}

	/** Pillar {@code i}'s node, or null while the pillar does not stand there (not built, or out of loaded land). */
	public static BlockPos find(ServerLevel seam, int i) {
		int[] c = pillar(i), s = towards(i);
		if (!Loaded.ready(seam, new BlockPos(c[0] + 5 * s[0], MendingService.HY, c[1] + 5 * s[1])) || !Loaded.ready(seam, new BlockPos(c[0], MendingService.HY, c[1]))) {
			return null;
		}
		return nodeOf(seam, c[0], c[1], s[0], s[1], MendingService.HY + 30, MendingService.HY - 30);
	}

	/**
	 * The node of an obelisk centred on (cx, cz): looking down from {@code top} to {@code bottom} for the plinth's top
	 * step at the corner that steps (sx, sz) away (five out on both axes), then up the shaft's heart from just above it
	 * to the top of its lowest piece, where the shaft first breaks (or where a node already stands). Null with no
	 * plinth, or no shaft of at least three blocks over it.
	 */
	public static BlockPos nodeOf(Level level, int cx, int cz, int sx, int sz, int top, int bottom) {
		int y = stepOf(level, cx, cz, sx, sz, top, bottom);
		if (y == Integer.MIN_VALUE) {
			return null;
		}
		for (int k = 1; k <= MAX_SHAFT; k++) {
			BlockPos at = new BlockPos(cx, y + k, cz);
			BlockState st = level.getBlockState(at);
			if (st.isAir() || st.is(FEEDING) || st.is(QUENCHED)) {
				return k > 3 ? at : null;
			}
		}
		return null;
	}

	/** The height of an obelisk's plinth's top step at its corner that steps (sx, sz) away, or MIN_VALUE if none. */
	static int stepOf(Level level, int cx, int cz, int sx, int sz, int top, int bottom) {
		int wx = cx + 5 * sx, wz = cz + 5 * sz;
		for (int y = top; y > bottom; y--) {
			BlockPos step = new BlockPos(wx, y, wz);
			if (!level.getBlockState(step).isAir() && level.getBlockState(step.above()).isAir()) {
				return y;
			}
		}
		return Integer.MIN_VALUE;
	}

	/** The pillar whose node this is, or -1. */
	public static int socketAt(ServerLevel seam, BlockPos pos) {
		for (int i = 0; i < PILLARS; i++) {
			if (NODES[i] != null) {
				if (NODES[i].equals(pos)) {
					return i;
				}
				continue;
			}
			int[] c = pillar(i);
			if (pos.getX() == c[0] && pos.getZ() == c[1] && pos.equals(find(seam, i))) {
				return i;
			}
		}
		return -1;
	}

	/** How many pillars quenched at once stun it (all four by default). */
	public static int stunAt() {
		return Math.max(1, Math.min(PILLARS, Config.STARVED_SOCKETS));
	}

	/** How many pillars quenched at once stop its healing (three by default). */
	public static int healStopAt() {
		return Math.max(1, Math.min(PILLARS, Config.STARVED_HEAL_QUENCH));
	}

	/** Nodes found so far (the pillars that stand). */
	static int found() {
		int n = 0;
		for (BlockPos s : NODES) {
			n += s == null ? 0 : 1;
		}
		return n;
	}

	/** Nodes quenched now. */
	static int quenched() {
		int n = 0;
		for (int i = 0; i < PILLARS; i++) {
			n += NODES[i] != null && QUENCH[i] > 0 ? 1 : 0;
		}
		return n;
	}

	/** Does any pillar stand to hold it? While none does, it takes harm in full. */
	static boolean possible() {
		return found() > 0;
	}

	/** Do the pillars heal it now: free, and fewer quenched than stop its healing (of those that stand)? */
	static boolean healing() {
		int f = found();
		return f > 0 && bound == 0 && quenched() < Math.min(healStopAt(), f);
	}

	// ------------------------------------------------------------------ waking

	/** It wakes: the old pedestals crumble, the nodes open atop the pillars and the strands run out to them. */
	static void wake(ServerLevel seam) {
		forget();
		retirePedestals(seam);
		open(seam);
	}

	/** The four little pedestals of the first Heart (before 3.18) go back to the floor they stood in. */
	private static void retirePedestals(ServerLevel seam) {
		int hx = MendingService.HX, hy = MendingService.HY, hz = MendingService.HZ;
		for (BlockPos p : new BlockPos[] {new BlockPos(hx + 12, hy + 1, hz), new BlockPos(hx - 12, hy + 1, hz),
				new BlockPos(hx, hy + 1, hz + 12), new BlockPos(hx, hy + 1, hz - 12)}) {
			if (Loaded.ready(seam, p) && seam.getBlockState(p).is(Blocks.REINFORCED_DEEPSLATE)) {
				seam.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
				if (seam.getBlockState(p.above()).is(Blocks.SOUL_LANTERN)) {
					seam.setBlock(p.above(), Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}
	}

	/** Opens the nodes of the pillars that stand (and lays their strands); those not found yet are looked for again. */
	private static void open(ServerLevel seam) {
		for (int i = 0; i < PILLARS; i++) {
			if (NODES[i] != null) {
				continue;
			}
			BlockPos n = find(seam, i);
			if (n == null) {
				continue;
			}
			NODES[i] = n;
			node(seam, n, FEEDING);
			mark(seam, i, 0);
			if (!STRAND[i]) {
				STRAND[i] = true;
				// (the strand meets the island at the plinth, two above its top step, far below the node)
				int[] c = pillar(i), t = towards(i);
				int step = stepOf(seam, c[0], c[1], t[0], t[1], MendingService.HY + 30, MendingService.HY - 30);
				strand(seam, i, new BlockPos(n.getX(), step == Integer.MIN_VALUE ? n.getY() : step + 2, n.getZ())).queue(true);
			}
		}
	}

	/** The glowing heart over a feeding node, seen through the stone: turned a little further every second. */
	private static void mark(ServerLevel seam, int i, long tick) {
		BlockPos n = NODES[i];
		if (n == null) {
			return;
		}
		float turn = (float) ((tick / 20) % 360) * 0.35F + i;
		var look = BlockFlight.pose(0, 1.4, 0, new Quaternionf().rotateY(turn).rotateX(0.35F), 0.7F);
		if (MARK[i] == null || MARK[i].isRemoved()) {
			if (MARK[i] != null) {
				// (gone with its chunk: let go, so the old one is tidied away if it comes back)
				BlockFlight.forget(MARK[i]);
			}
			MARK[i] = BlockFlight.spawn(seam, n.getX(), n.getY() + 1, n.getZ(), FEEDING.defaultBlockState(), look);
			if (MARK[i] != null) {
				MARK[i].setGlowingTag(true);
			}
			return;
		}
		DisplayAccess d = (DisplayAccess) MARK[i];
		d.extremedimensions$setInterpolationDelay(0);
		d.extremedimensions$setInterpolationDuration(20);
		d.extremedimensions$setTransformation(look);
	}

	private static void unmark(int i) {
		if (MARK[i] != null) {
			BlockFlight.drop(MARK[i]);
			MARK[i] = null;
		}
	}

	/**
	 * A strand from the Heart's rim to pillar {@code i}'s island: three wide, walled both sides, rising or falling by
	 * half blocks (slabs), laid only into open air. {@code socket}, two above the plinth's top step, tells the island's height.
	 */
	public static SeamArchitect.Plan strand(ServerLevel seam, int i, BlockPos socket) {
		int[] c = pillar(i);
		double ux = c[0] - MendingService.HX, uz = c[1] - MendingService.HZ;
		double len = Math.sqrt(ux * ux + uz * uz);
		ux /= len;
		uz /= len;
		double ax = MendingService.HX + 0.5 + ux * (MendingService.HR - 2), az = MendingService.HZ + 0.5 + uz * (MendingService.HR - 2);
		double bx = c[0] + 0.5 - ux * 9, bz = c[1] + 0.5 - uz * 9;
		int island = socket.getY() - 5;
		int yb = island;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int y = socket.getY() - 2; y > island - 8; y--) {
			m.set((int) Math.floor(bx), y, (int) Math.floor(bz));
			if (!seam.getBlockState(m).isAir()) {
				yb = y;
				break;
			}
		}
		return strand(seam, ax, az, MendingService.HY, bx, bz, yb);
	}

	/** A strand between two points (floor heights at each end); exposed for the tests. */
	public static SeamArchitect.Plan strand(ServerLevel seam, double ax, double az, int ya, double bx, double bz, int yb) {
		SeamArchitect.Plan p = new SeamArchitect.Plan();
		double dx = bx - ax, dz = bz - az;
		double len = Math.sqrt(dx * dx + dz * dz);
		double ux = dx / len, uz = dz / len;
		BlockState floor = ModBlocks.HUSH_BRICKS.defaultBlockState(), spine = ModBlocks.SEAM_VEIN.defaultBlockState();
		BlockState slab = ModBlocks.HUSH_TILE_SLAB.defaultBlockState(), wall = ModBlocks.HUSH_BRICK_WALL.defaultBlockState();
		int x0 = (int) Math.floor(Math.min(ax, bx)) - 4, x1 = (int) Math.ceil(Math.max(ax, bx)) + 4;
		int z0 = (int) Math.floor(Math.min(az, bz)) - 4, z1 = (int) Math.ceil(Math.max(az, bz)) + 4;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				double px = x + 0.5 - ax, pz = z + 0.5 - az;
				double along = px * ux + pz * uz, side = Math.abs(px * uz - pz * ux);
				if (along < -0.5 || along > len + 0.5 || side > 2.5) {
					continue;
				}
				double h = ya + (yb - ya) * Math.max(0, Math.min(1, along / len));
				int fy = (int) Math.floor(h);
				boolean half = h - fy >= 0.5;
				if (!place(seam, p, m, x, fy, z, side < 0.5 ? spine : floor)) {
					continue;
				}
				if (side <= 1.5) {
					if (half) {
						place(seam, p, m, x, fy + 1, z, slab);
					}
				} else if (along > 1.5 && along < len - 1.5) {
					place(seam, p, m, x, fy + 1, z, wall);
				}
			}
		}
		return p;
	}

	/** Lays a block of a strand where the air is open (never over what stands); false where something stands. */
	private static boolean place(ServerLevel seam, SeamArchitect.Plan p, BlockPos.MutableBlockPos m, int x, int y, int z, BlockState s) {
		m.set(x, y, z);
		if (seam != null && (!Loaded.ready(seam, m) || !seam.getBlockState(m).isAir())) {
			return false;
		}
		p.set(x, y, z, s);
		return true;
	}

	// ------------------------------------------------------------------ quenching

	/** Seam Silk held to a node: it drinks, and is quenched once it has drunk enough. */
	static InteractionResult feed(ServerPlayer p, int i, ItemStack stack, boolean awake, int players) {
		if (!awake) {
			p.sendSystemMessage(Component.translatable(DataStore.starvedSlain() ? "gui.extremedimensions.heart.socket_gone"
					: "gui.extremedimensions.heart.socket_wait"), true);
			return InteractionResult.PASS;
		}
		ServerLevel seam = (ServerLevel) p.level();
		if (NODES[i] == null) {
			NODES[i] = find(seam, i);
		}
		BlockPos s = NODES[i];
		if (s == null) {
			return InteractionResult.PASS;
		}
		String dim = SeamService.SEAM.identifier().toString();
		if (QUENCH[i] > 0) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.heart.socket_burning", Math.max(1, QUENCH[i] / 20)), true);
			return InteractionResult.SUCCESS;
		}
		if (!p.isCreative()) {
			stack.shrink(1);
		}
		int need = Math.max(1, Config.STARVED_SOCKET_SILK);
		FED[i]++;
		WorldFx.particleIn(dim, "extremedimensions:soul_mote", s.getX() + 0.5, s.getY() + 0.5, s.getZ() + 0.5, 0.4, 0.4, 0.4, 0.02, 10);
		if (FED[i] < need) {
			WorldFx.soundIn(dim, "minecraft:block.wool.place", "block", s.getX() + 0.5, s.getY() + 0.5, s.getZ() + 0.5, 1.2f, 0.6f + 0.2f * FED[i]);
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.heart.socket_fed", FED[i], need), true);
			return InteractionResult.SUCCESS;
		}
		quench(seam, i);
		return InteractionResult.SUCCESS;
	}

	/** A node goes dark for its time: it feeds the Starved One no more, and everyone at the Heart is told how many are. */
	static boolean quench(ServerLevel seam, int i) {
		BlockPos s = NODES[i];
		if (s == null || QUENCH[i] > 0) {
			return false;
		}
		FED[i] = 0;
		QUENCH[i] = (int) Math.max(20, Config.STARVED_SOCKET_SECONDS * 20);
		if (Loaded.ready(seam, s)) {
			node(seam, s, QUENCHED);
		}
		unmark(i);
		String dim = SeamService.SEAM.identifier().toString();
		WorldFx.soundIn(dim, "minecraft:block.beacon.deactivate", "block", s.getX() + 0.5, s.getY() + 0.5, s.getZ() + 0.5, 4f, 0.7f);
		WorldFx.soundIn(dim, "minecraft:block.sculk_catalyst.bloom", "block", s.getX() + 0.5, s.getY() + 0.5, s.getZ() + 0.5, 3f, 0.5f);
		WorldFx.particleFar(dim, "minecraft:soul", s.getX() + 0.5, s.getY() + 1.5, s.getZ() + 0.5, 0.6, 1.2, 0.6, 0.04, 40, 160);
		int q = quenched(), f = found(), stop = Math.min(healStopAt(), f);
		for (ServerPlayer p : seam.players()) {
			if (MendingService.inArena(p)) {
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.heart.socket_lit", q, f, stop), true);
				if (q == stop) {
					p.sendSystemMessage(Component.translatable("gui.extremedimensions.heart.heal_stopped").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
				}
			}
		}
		return true;
	}

	/** A node quenched long enough wakes and feeds it again. */
	private static void relight(ServerLevel seam, int i, List<ServerPlayer> arena, long tick) {
		BlockPos s = NODES[i];
		if (s != null && Loaded.ready(seam, s)) {
			node(seam, s, FEEDING);
		}
		mark(seam, i, tick);
		for (ServerPlayer q : arena) {
			q.sendSystemMessage(Component.translatable("gui.extremedimensions.heart.socket_dark", quenched()), true);
		}
	}

	/** An arrow (or any thrown thing) at a feeding node or the heart over it: spent, and the node quenched. */
	private static boolean struck(ServerLevel seam, BlockPos n) {
		AABB box = new AABB(n).inflate(0.6).expandTowards(0, 2.6, 0);
		List<Entity> hits = seam.getEntities((Entity) null, box, e -> THROWN.contains(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath()));
		if (hits.isEmpty()) {
			return false;
		}
		for (Entity e : hits) {
			// (an arrow is spent in it; a trident is not taken from its thrower)
			if (!"trident".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath())) {
				e.discard();
			}
		}
		return true;
	}

	/** What may be thrown or shot at a node to quench it. */
	private static final java.util.Set<String> THROWN = java.util.Set.of("arrow", "spectral_arrow", "trident", "snowball", "egg", "wind_charge");

	// ------------------------------------------------------------------ the fight

	/**
	 * Every tick of the fight: nodes struck or broken are quenched, quenched ones count down and wake again, the
	 * feeding ones heal it (with their beams), and once all are quenched at once it is stunned. True on the tick it is.
	 */
	static boolean tick(ServerLevel seam, StarvedOne so, List<ServerPlayer> arena, long tick) {
		if (tick % 40 == 0 && found() < PILLARS) {
			open(seam);
		}
		String dim = SeamService.SEAM.identifier().toString();
		for (int i = 0; i < PILLARS; i++) {
			BlockPos n = NODES[i];
			if (n == null) {
				continue;
			}
			if (QUENCH[i] == 0) {
				if (tick % 2 == 0 && struck(seam, n)) {
					quench(seam, i);
				} else if (tick % 10 == 0 && Loaded.ready(seam, n) && !seam.getBlockState(n).is(FEEDING)) {
					// Broken (or blown away): that quenches it too
					quench(seam, i);
				} else if (tick % 20 == 0) {
					mark(seam, i, tick);
				}
				continue;
			}
			if (tick % 20 == 0 && Loaded.ready(seam, n) && seam.getBlockState(n).isAir()) {
				node(seam, n, QUENCHED);
			}
			if (bound == 0 && --QUENCH[i] == 0) {
				relight(seam, i, arena, tick);
			}
		}
		if (bound > 0) {
			chains(so, tick);
			if (--bound == 0) {
				release(seam, so, arena);
			}
			return false;
		}
		int stun = Math.min(stunAt(), found());
		if (stun > 0 && quenched() >= stun) {
			if (stunReady) {
				stunReady = false;
				bind(seam, so, arena);
				return true;
			}
		} else {
			stunReady = true;
		}
		if (healing()) {
			heal(so, tick, dim);
		}
		return false;
	}

	/** The feeding nodes pour into it: beams of souls from each, and its health back a share a second for each. */
	private static void heal(StarvedOne so, long tick, String dim) {
		Vec3 to = so.position().add(0, 3.0, 0);
		int feeding = 0;
		for (int i = 0; i < PILLARS; i++) {
			if (NODES[i] == null || QUENCH[i] > 0) {
				continue;
			}
			feeding++;
			if (tick % 5 == 0) {
				Vec3 from = Vec3.atCenterOf(NODES[i]).add(0, 1.4, 0);
				double phase = (tick / 5 % 3) / 3.0;
				for (int k = 0; k < 16; k++) {
					Vec3 at = from.lerp(to, (k + phase) / 16.0);
					WorldFx.particleFar(dim, "minecraft:soul", at.x, at.y, at.z, 0.05, 0.05, 0.05, 0.0, 1, 160);
				}
			}
		}
		if (feeding > 0 && tick % 20 == 0 && so.getHealth() < so.getMaxHealth()) {
			so.heal((float) (Math.max(0, Config.STARVED_HEAL) * so.getMaxHealth() * feeding));
			WorldFx.particleIn(dim, "minecraft:soul", so.getX(), so.getY() + 3, so.getZ(), 1.2, 1.5, 1.2, 0.02, 6 * feeding);
		}
	}

	/** Every pillar quenched: their dead hearts pull it down in chains before whoever is nearest, stunned. */
	private static void bind(ServerLevel seam, StarvedOne so, List<ServerPlayer> arena) {
		bound = (int) Math.max(20, Config.STARVED_BIND_SECONDS * 20);
		for (int i = 0; i < PILLARS; i++) {
			HOLDING[i] = QUENCH[i] > 0;
		}
		ServerPlayer near = null;
		for (ServerPlayer p : arena) {
			if (near == null || p.distanceToSqr(so) < near.distanceToSqr(so)) {
				near = p;
			}
		}
		Vec3 at = so.position();
		if (near != null && near.distanceToSqr(so) < 48 * 48) {
			Vec3 away = new Vec3(so.getX() - near.getX(), 0, so.getZ() - near.getZ());
			away = away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize();
			at = near.position().add(away.scale(3.5)).add(0, 0.2, 0);
		} else {
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			for (int dy = 0; dy < 40; dy++) {
				m.set(so.getBlockX(), so.getBlockY() - dy, so.getBlockZ());
				if (!seam.getBlockState(m).isAir()) {
					at = new Vec3(so.getX(), m.getY() + 1.2, so.getZ());
					break;
				}
			}
		}
		bindAt = at;
		so.setBound(true);
		WorldFx.punch(seam, so, so.getX(), so.getZ(), 80, 0.9f, 18);
		String dim = SeamService.SEAM.identifier().toString();
		WorldFx.soundIn(dim, "extremedimensions:lurker_shriek", "hostile", so.getX(), so.getY() + 4, so.getZ(), 5f, 0.45f);
		WorldFx.soundIn(dim, "minecraft:block.chain.place", "block", so.getX(), so.getY() + 3, so.getZ(), 4f, 0.5f);
		for (ServerPlayer q : arena) {
			q.sendSystemMessage(Component.translatable("gui.extremedimensions.heart.bound"), true);
		}
	}

	/** It shakes off the stun; the quenched nodes go on counting down where they stood. */
	private static void release(ServerLevel seam, StarvedOne so, List<ServerPlayer> arena) {
		so.setBound(false);
		bindAt = null;
		java.util.Arrays.fill(HOLDING, false);
		String dim = SeamService.SEAM.identifier().toString();
		WorldFx.soundIn(dim, "minecraft:block.chain.break", "block", so.getX(), so.getY() + 3, so.getZ(), 4f, 0.5f);
		WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", so.getX(), so.getY() + 3, so.getZ(), 1.5, 2, 1.5, 0, 40);
		for (ServerPlayer q : arena) {
			q.sendSystemMessage(Component.translatable("gui.extremedimensions.heart.unbound"), true);
		}
	}

	/** Light running from each pillar that holds it to it, a few motes along each chain. */
	private static void chains(Entity so, long tick) {
		if (tick % 4 != 0) {
			return;
		}
		String dim = SeamService.SEAM.identifier().toString();
		Vec3 to = so.position().add(0, 3.5, 0);
		for (int i = 0; i < PILLARS; i++) {
			if (!HOLDING[i] || NODES[i] == null) {
				continue;
			}
			Vec3 from = Vec3.atCenterOf(NODES[i]);
			for (int k = 0; k < 10; k++) {
				double t = (k + (tick / 4 % 3) / 3.0) / 10.0;
				Vec3 at = from.lerp(to, t);
				WorldFx.particleIn(dim, "minecraft:soul_fire_flame", at.x, at.y, at.z, 0.05, 0.05, 0.05, 0.0, 1);
			}
		}
	}

	/** The fight is over (won, or everyone gone): every node dark, the hearts over them gone, the Starved One free. */
	static void end(ServerLevel seam) {
		for (int i = 0; i < PILLARS; i++) {
			BlockPos s = NODES[i];
			if (s != null) {
				// The node goes, and its shaft stands broken as before (now, or once its ground is loaded)
				FightBlocks.release(seam, s);
			}
		}
		forget();
	}

	/** A node set into its shaft for the fight, feeding or quenched: held, it goes again once the fight is over. */
	private static void node(ServerLevel seam, BlockPos n, Block b) {
		if (FightBlocks.holds(seam, n)) {
			FightBlocks.put(seam, n, b.defaultBlockState(), 3);
		} else {
			FightBlocks.place(seam, n, b.defaultBlockState(), 3);
		}
	}

	/** Is this the Seam Silk a node drinks? */
	static boolean silk(ItemStack stack) {
		return stack.is(ModItems.SEAM_SILK);
	}
}
