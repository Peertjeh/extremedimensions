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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The four pillars round the Heart, and the light that binds the Starved One.
 *
 * <p>The pillars are the obelisks of the Heart's crown, each on its own island some sixty blocks out on the diagonals.
 * As the Starved One wakes, a socket of reinforced stone opens in each shaft, on the corner that faces the Heart, at
 * eye height above the plinth's top step, and a strand of hush stone runs out to each island from the Heart's rim.
 * Feed a socket {@code starved_socket_silk} Seam Silk and it burns for {@code starved_socket_seconds}. Once enough burn
 * at once ({@code starved_sockets} alone, one more for every two players beyond the first, never more than there are
 * pillars) their light binds it: it is dragged down before whoever is nearest it and held there for
 * {@code starved_bind_seconds}, taking {@code starved_bound_damage} times the harm; free, it takes only
 * {@code starved_unbound_damage}. Then every socket goes dark and must be fed again.
 */
public final class Binding {
	private Binding() {}

	public static final int PILLARS = 4;
	/** How far out the crown stands its obelisks (see {@link SeamArchitect#crown}). */
	public static final int RING = 64;

	private static final BlockPos[] SOCKETS = new BlockPos[PILLARS];
	/** Silk fed to each socket towards its lighting. */
	private static final int[] FED = new int[PILLARS];
	/** Ticks each socket still burns (0: dark). */
	private static final int[] BURN = new int[PILLARS];
	private static final boolean[] STRAND = new boolean[PILLARS];
	/** Ticks the Starved One is still held, and where. */
	private static int bound;
	private static Vec3 bindAt;
	/** The sockets whose light holds it (their chains run to it). */
	private static final boolean[] HOLDING = new boolean[PILLARS];

	/** Forgets everything (the server stops, or the fight is over and the world tidied). */
	static void forget() {
		java.util.Arrays.fill(SOCKETS, null);
		java.util.Arrays.fill(FED, 0);
		java.util.Arrays.fill(BURN, 0);
		java.util.Arrays.fill(STRAND, false);
		java.util.Arrays.fill(HOLDING, false);
		bound = 0;
		bindAt = null;
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

	/**
	 * Pillar {@code i}'s socket, or null while the pillar does not stand there (not yet built, or out of loaded land):
	 * the corner of the shaft that faces the Heart, two above the plinth's top step.
	 */
	public static BlockPos find(ServerLevel seam, int i) {
		int[] c = pillar(i), s = towards(i);
		if (!Loaded.ready(seam, new BlockPos(c[0] + 5 * s[0], MendingService.HY, c[1] + 5 * s[1]))) {
			return null;
		}
		return socketOf(seam, c[0], c[1], s[0], s[1], MendingService.HY + 30, MendingService.HY - 30);
	}

	/**
	 * The socket of an obelisk centred on (cx, cz), on its corner that steps (sx, sz) away: looking down from {@code top}
	 * to {@code bottom}, the plinth's top step at that corner (five out on both axes), and the shaft's corner (four out)
	 * two above it; null if no plinth or shaft stands there.
	 */
	public static BlockPos socketOf(net.minecraft.world.level.Level level, int cx, int cz, int sx, int sz, int top, int bottom) {
		int wx = cx + 5 * sx, wz = cz + 5 * sz;
		for (int y = top; y > bottom; y--) {
			BlockPos step = new BlockPos(wx, y, wz);
			if (!level.getBlockState(step).isAir() && level.getBlockState(step.above()).isAir()) {
				BlockPos socket = new BlockPos(cx + 4 * sx, y + 2, cz + 4 * sz);
				return !level.getBlockState(socket).isAir() && !level.getBlockState(socket.below()).isAir() ? socket : null;
			}
		}
		return null;
	}

	/** The pillar whose socket this is, or -1. */
	public static int socketAt(ServerLevel seam, BlockPos pos) {
		for (int i = 0; i < PILLARS; i++) {
			if (SOCKETS[i] != null) {
				if (SOCKETS[i].equals(pos)) {
					return i;
				}
				continue;
			}
			int[] c = pillar(i), s = towards(i);
			if (pos.getX() == c[0] + 4 * s[0] && pos.getZ() == c[1] + 4 * s[1] && pos.equals(find(seam, i))) {
				return i;
			}
		}
		return -1;
	}

	/** How many sockets must burn at once to bind it against this many: more for a larger party, never more than four. */
	public static int needed(int players) {
		return Math.max(1, Math.min(PILLARS, Math.max(1, Config.STARVED_SOCKETS) + Math.max(0, players - 1) / 2));
	}

	/** Sockets found so far. */
	static int found() {
		int n = 0;
		for (BlockPos s : SOCKETS) {
			n += s == null ? 0 : 1;
		}
		return n;
	}

	static int lit() {
		int n = 0;
		for (int b : BURN) {
			n += b > 0 ? 1 : 0;
		}
		return n;
	}

	/** How many must burn now: as many as the party needs, or all there are if fewer stand. */
	static int target(int players) {
		return Math.min(needed(players), found());
	}

	/** Can it be bound at all (enough pillars stand)? While it cannot, it takes harm in full. */
	static boolean possible(int players) {
		return found() > 0 && target(players) > 0;
	}

	// ------------------------------------------------------------------ waking

	/** It wakes: the old pedestals crumble, the sockets open and the strands run out to the pillars. */
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

	/** Opens the sockets of the pillars that stand (and lays their strands); those not found yet are looked for again. */
	private static void open(ServerLevel seam) {
		for (int i = 0; i < PILLARS; i++) {
			if (SOCKETS[i] != null) {
				continue;
			}
			BlockPos s = find(seam, i);
			if (s == null) {
				continue;
			}
			SOCKETS[i] = s;
			if (!seam.getBlockState(s).is(Blocks.REINFORCED_DEEPSLATE)) {
				seam.setBlock(s, Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 3);
			}
			if (!STRAND[i]) {
				STRAND[i] = true;
				strand(seam, i, s).queue(true);
			}
		}
	}

	/**
	 * A strand from the Heart's rim to pillar {@code i}'s island: three wide, walled both sides, rising or falling by
	 * half blocks (slabs), laid only into open air. Its socket tells the height of the island.
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

	// ------------------------------------------------------------------ feeding

	/** Seam Silk held to a socket: it drinks, and lights once it has drunk enough. */
	static InteractionResult feed(ServerPlayer p, int i, ItemStack stack, boolean awake, int players) {
		if (!awake) {
			p.sendSystemMessage(Component.translatable(DataStore.starvedSlain() ? "gui.extremedimensions.heart.socket_gone"
					: "gui.extremedimensions.heart.socket_wait"), true);
			return InteractionResult.PASS;
		}
		ServerLevel seam = (ServerLevel) p.level();
		if (SOCKETS[i] == null) {
			SOCKETS[i] = find(seam, i);
		}
		BlockPos s = SOCKETS[i];
		if (s == null) {
			return InteractionResult.PASS;
		}
		String dim = SeamService.SEAM.identifier().toString();
		if (BURN[i] > 0 || bound > 0) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.heart.socket_burning", Math.max(1, BURN[i] / 20)), true);
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
		FED[i] = 0;
		BURN[i] = (int) Math.max(20, Config.STARVED_SOCKET_SECONDS * 20);
		seam.setBlock(s, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
		WorldFx.soundIn(dim, "minecraft:block.beacon.activate", "block", s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 3f, 1.3f);
		WorldFx.particleIn(dim, "extremedimensions:soul_mote", s.getX() + 0.5, s.getY() + 8, s.getZ() + 0.5, 0.6, 8, 0.6, 0.02, 60);
		int lit = lit(), want = target(players);
		for (ServerPlayer q : seam.players()) {
			if (MendingService.inArena(q)) {
				q.sendSystemMessage(Component.translatable("gui.extremedimensions.heart.socket_lit", lit, want), true);
			}
		}
		return InteractionResult.SUCCESS;
	}

	// ------------------------------------------------------------------ the fight

	/**
	 * Every tick of the fight: sockets burn down (and go dark), sockets not found yet are looked for, and once enough
	 * burn at once it is bound. True on the tick it is bound.
	 */
	static boolean tick(ServerLevel seam, StarvedOne so, List<ServerPlayer> arena, long tick) {
		if (tick % 40 == 0 && found() < PILLARS) {
			open(seam);
		}
		String dim = SeamService.SEAM.identifier().toString();
		for (int i = 0; i < PILLARS; i++) {
			if (BURN[i] <= 0 || SOCKETS[i] == null) {
				continue;
			}
			BlockPos s = SOCKETS[i];
			if (tick % 10 == 0) {
				// a column of light up the shaft, seen from across the Heart
				WorldFx.particleIn(dim, "extremedimensions:soul_mote", s.getX() + 0.5, s.getY() + 10, s.getZ() + 0.5, 0.5, 10, 0.5, 0.0, 8);
			}
			if (bound > 0) {
				continue;
			}
			if (--BURN[i] == 0) {
				darken(seam, i);
				for (ServerPlayer q : arena) {
					q.sendSystemMessage(Component.translatable("gui.extremedimensions.heart.socket_dark"), true);
				}
			}
		}
		if (bound > 0) {
			chains(so, tick);
			if (--bound == 0) {
				release(seam, so, arena);
			}
			return false;
		}
		int want = target(arena.size());
		if (want > 0 && lit() >= want) {
			bind(seam, so, arena);
			return true;
		}
		return false;
	}

	/** The lit sockets pour their light into chains: it is dragged down before whoever is nearest and held there. */
	private static void bind(ServerLevel seam, StarvedOne so, List<ServerPlayer> arena) {
		bound = (int) Math.max(20, Config.STARVED_BIND_SECONDS * 20);
		for (int i = 0; i < PILLARS; i++) {
			HOLDING[i] = BURN[i] > 0;
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

	/** It tears free: every socket goes dark, to be fed again. */
	private static void release(ServerLevel seam, StarvedOne so, List<ServerPlayer> arena) {
		so.setBound(false);
		bindAt = null;
		for (int i = 0; i < PILLARS; i++) {
			BURN[i] = 0;
			FED[i] = 0;
			HOLDING[i] = false;
			darken(seam, i);
		}
		String dim = SeamService.SEAM.identifier().toString();
		WorldFx.soundIn(dim, "minecraft:block.chain.break", "block", so.getX(), so.getY() + 3, so.getZ(), 4f, 0.5f);
		WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", so.getX(), so.getY() + 3, so.getZ(), 1.5, 2, 1.5, 0, 40);
		for (ServerPlayer q : arena) {
			q.sendSystemMessage(Component.translatable("gui.extremedimensions.heart.unbound"), true);
		}
	}

	/** Light running from each holding socket to it, a few motes along each chain. */
	private static void chains(Entity so, long tick) {
		if (tick % 4 != 0) {
			return;
		}
		String dim = SeamService.SEAM.identifier().toString();
		Vec3 to = so.position().add(0, 3.5, 0);
		for (int i = 0; i < PILLARS; i++) {
			if (!HOLDING[i] || SOCKETS[i] == null) {
				continue;
			}
			Vec3 from = Vec3.atCenterOf(SOCKETS[i]);
			for (int k = 0; k < 10; k++) {
				double t = (k + (tick / 4 % 3) / 3.0) / 10.0;
				Vec3 at = from.lerp(to, t);
				WorldFx.particleIn(dim, "minecraft:soul_fire_flame", at.x, at.y, at.z, 0.05, 0.05, 0.05, 0.0, 1);
			}
		}
	}

	private static void darken(ServerLevel seam, int i) {
		BlockPos s = SOCKETS[i];
		if (s != null && Loaded.ready(seam, s) && seam.getBlockState(s).is(Blocks.CRYING_OBSIDIAN)) {
			seam.setBlock(s, Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 3);
		}
	}

	/** The fight is over (won, or everyone gone): every socket dark, the Starved One free of its chains. */
	static void end(ServerLevel seam) {
		for (int i = 0; i < PILLARS; i++) {
			darken(seam, i);
		}
		forget();
	}

	/** Is this the Seam Silk a socket drinks? */
	static boolean silk(ItemStack stack) {
		return stack.is(ModItems.SEAM_SILK);
	}
}
