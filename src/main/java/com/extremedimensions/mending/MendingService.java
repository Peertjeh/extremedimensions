package com.extremedimensions.mending;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.network.Fx;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Mending, the end of the story.
 *
 * <p>Once the seal is broken, every rift into the Seam opens near its <b>Heart</b>: a great island at the centre
 * where all the threads meet, ringed by four pedestals of reinforced stone. Step onto the altar and the
 * <b>Starved One</b> rises: what came through the first wound.
 *
 * <ul>
 *   <li>It hunts with blinks through the shadows; wounded, it bites through your thread (your rift is flung to the
 *       edge of the island); near death, it drags everyone towards it.</li>
 *   <li>Feed Seam Silk to two pedestals and the light stuns it: for eight seconds it takes double damage.</li>
 *   <li>The Thinned fight beside those who befriended a Hollowling.</li>
 *   <li>Time in the Seam stands still for everyone near the Heart while it lives.</li>
 * </ul>
 *
 * It leaves two things: a <b>Mending Thread</b> and the <b>Severing Shears</b>. The first one used decides the
 * world: mended (calm, the tribe at peace, lighter drain) or severed (no more timer beyond the wound, but whoever
 * falls there loses everything they carried).
 */
public final class MendingService {
	private MendingService() {}

	public static final int HX = 0, HY = 100, HZ = 0, HR = 22;
	private static final BlockPos[] PEDESTALS = {
			new BlockPos(HX + 12, HY + 1, HZ), new BlockPos(HX - 12, HY + 1, HZ),
			new BlockPos(HX, HY + 1, HZ + 12), new BlockPos(HX, HY + 1, HZ - 12)};

	private static UUID boss;
	private static ServerBossEvent bar;
	private static int ticks;
	private static int stun;
	private static int nextTrick = 160;
	private static final boolean[] LIT = new boolean[4];

	public static boolean isDriven(Entity e) {
		return e.getUUID().equals(boss);
	}

	public static boolean active() {
		return boss != null;
	}

	public static void clear() {
		if (bar != null) {
			bar.removeAllPlayers();
		}
		bar = null;
		boss = null;
		stun = 0;
		java.util.Arrays.fill(LIT, false);
	}

	public static float damageFactor(StarvedOne so) {
		return stun > 0 ? 2.0F : 1.0F;
	}

	private static boolean open() {
		return DataStore.sealBroken() && DataStore.ending().isEmpty();
	}

	/** Is this player near the Heart, in the Seam? */
	public static boolean inArena(ServerPlayer p) {
		return SeamService.inSeam(p) && p.distanceToSqr(HX + 0.5, HY, HZ + 0.5) < 48 * 48;
	}

	/** Where a rift lands while the Heart waits: somewhere on a ring around it. */
	public static int[] arrival() {
		double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
		return new int[] {HX + (int) Math.round(Math.cos(a) * 34), HZ + (int) Math.round(Math.sin(a) * 34)};
	}

	public static boolean opensNearHeart() {
		return open();
	}

	// ------------------------------------------------------------------ ticking

	public static void tick(MinecraftServer s) {
		ServerLevel seam = s.getLevel(SeamService.SEAM);
		if (seam == null) {
			return;
		}
		if (open() && !DataStore.heartBuilt()) {
			for (ServerPlayer p : seam.players()) {
				if (p.distanceToSqr(HX, HY, HZ) < 120 * 120) {
					buildHeart(seam);
					break;
				}
			}
		}
		if (boss == null) {
			if (open() && DataStore.heartBuilt() && s.getTickCount() % 10 == 0) {
				for (ServerPlayer p : seam.players()) {
					if (!p.isSpectator() && p.distanceToSqr(HX + 0.5, HY + 1, HZ + 0.5) < 3.5 * 3.5) {
						start(seam, p);
						break;
					}
				}
			}
			return;
		}
		drive(seam);
	}

	private static void start(ServerLevel seam, ServerPlayer p) {
		StarvedOne so = ModEntities.STARVED_ONE.spawn(seam, new BlockPos(HX, HY + 1, HZ - 8), EntitySpawnReason.EVENT);
		if (so == null) {
			return;
		}
		so.setPersistenceRequired();
		so.setTarget(p);
		boss = so.getUUID();
		ticks = 0;
		stun = 0;
		nextTrick = 160;
		java.util.Arrays.fill(LIT, false);
		bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("boss.extremedimensions.starved_one"),
				BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_20);
		roar(so, 4f);
		WorldFx.particleIn(SeamService.SEAM.identifier().toString(), "extremedimensions:lurker_wisp", HX, HY + 3, HZ - 8, 2, 3, 2, 0, 120);
		for (ServerPlayer q : seam.players()) {
			if (inArena(q)) {
				Journal.unlock(q, DataStore.get(q.getUUID()), JournalEntry.HEART);
				WorldFx.run("effect give " + q.getStringUUID() + " minecraft:darkness 5 0 true");
			}
		}
	}

	private static void roar(Entity e, float vol) {
		WorldFx.run(String.format(Locale.ROOT, "execute in %s run playsound extremedimensions:keeper_roar hostile @a %.2f %.2f %.2f %.1f 0.55",
				SeamService.SEAM.identifier().toString(), e.getX(), e.getY() + 4, e.getZ(), vol));
	}

	private static void drive(ServerLevel seam) {
		Entity ent = seam.getEntity(boss);
		List<ServerPlayer> arena = new ArrayList<>();
		for (ServerPlayer p : seam.players()) {
			if (inArena(p) && !p.isSpectator() && p.isAlive()) {
				arena.add(p);
			}
		}
		if (!(ent instanceof StarvedOne so) || !so.isAlive()) {
			if (ent == null || !ent.isAlive()) {
				clear();
			}
			return;
		}
		if (arena.isEmpty()) {
			// Nobody left at the Heart: it sinks back into the seam, whole again
			fade(so);
			return;
		}
		ticks++;
		String dim = SeamService.SEAM.identifier().toString();
		if (ticks % 20 == 0) {
			for (ServerPlayer p : arena) {
				bar.addPlayer(p);
			}
		}
		bar.setProgress(so.getHealth() / so.getMaxHealth());
		ServerPlayer target = nearest(so, arena);
		if (stun > 0) {
			stun--;
			so.getNavigation().stop();
			so.setTarget(null);
			so.setLurkerPose(Lurker.POSE_DRAIN);
			if (stun % 4 == 0) {
				WorldFx.particleIn(dim, "extremedimensions:soul_mote", so.getX(), so.getY() + 3, so.getZ(), 1, 2, 1, 0, 6);
			}
			if (stun == 0) {
				unlight(seam);
			}
			return;
		}
		if (ticks % 20 == 0) {
			so.setTarget(target);
		}
		double d2 = so.distanceToSqr(target);
		so.setLurkerPose(d2 < 5.5 * 5.5 ? Lurker.POSE_TAKE : so.getDeltaMovement().horizontalDistanceSqr() > 0.002 ? Lurker.POSE_RUN : Lurker.POSE_WATCH);
		if (ticks % 3 == 0) {
			WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", so.getX(), so.getY() + 2.5, so.getZ(), 0.8, 1.5, 0.8, 0, 2);
		}
		float f = so.getHealth() / so.getMaxHealth();
		if (--nextTrick <= 0) {
			ThreadLocalRandom rnd = ThreadLocalRandom.current();
			if (f <= 0.25f) {
				nextTrick = 110 + rnd.nextInt(40);
				drag(so, arena);
			} else if (f <= 0.6f && rnd.nextBoolean()) {
				nextTrick = 180 + rnd.nextInt(60);
				bite(so, arena);
			} else {
				nextTrick = 150 + rnd.nextInt(60);
				blink(so, target);
			}
		}
		// The Thinned fight beside those who befriended one of their young
		if (ticks % 300 == 150) {
			for (ServerPlayer p : arena) {
				if (DataStore.get(p.getUUID()).companion) {
					WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", so.getX(), so.getY() + 3, so.getZ(), 1.5, 2, 1.5, 0, 40);
					WorldFx.run(String.format(Locale.ROOT, "execute in %s run playsound extremedimensions:lurker_shriek hostile @a %.2f %.2f %.2f 2 0.8",
							dim, so.getX(), so.getY() + 3, so.getZ()));
					so.hurtServer(seam, seam.damageSources().magic(), 12.0F);
					break;
				}
			}
		}
	}

	private static ServerPlayer nearest(Entity e, List<ServerPlayer> ps) {
		ServerPlayer best = ps.get(0);
		for (ServerPlayer p : ps) {
			if (p.distanceToSqr(e) < best.distanceToSqr(e)) {
				best = p;
			}
		}
		return best;
	}

	private static void blink(StarvedOne so, ServerPlayer p) {
		Vec3 look = p.getViewVector(1.0f);
		double x = p.getX() - look.x * 4, z = p.getZ() - look.z * 4;
		ServerLevel seam = (ServerLevel) so.level();
		BlockPos g = BlockPos.containing(x, p.getY(), z);
		for (int dy = 3; dy >= -3; dy--) {
			BlockPos q = g.above(dy);
			if (seam.getBlockState(q).isAir() && !seam.getBlockState(q.below()).isAir()) {
				String dim = SeamService.SEAM.identifier().toString();
				WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", so.getX(), so.getY() + 3, so.getZ(), 1, 2, 1, 0, 40);
				so.teleportTo(q.getX() + 0.5, q.getY(), q.getZ() + 0.5);
				WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", q.getX() + 0.5, q.getY() + 3, q.getZ() + 0.5, 1, 2, 1, 0, 40);
				WorldFx.run(String.format(Locale.ROOT, "execute in %s run playsound extremedimensions:lurker_vanish hostile @a %d %d %d 2 0.5",
						dim, q.getX(), q.getY() + 2, q.getZ()));
				return;
			}
		}
	}

	/** It bites through the thread: every rift near the Heart is flung to the edge of the island. */
	private static void bite(StarvedOne so, List<ServerPlayer> arena) {
		roar(so, 3f);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (ServerPlayer p : arena) {
			PlayerData d = DataStore.get(p.getUUID());
			if (!d.inSeam) {
				continue;
			}
			double a = rnd.nextDouble() * Math.PI * 2;
			d.riftX = HX + Math.cos(a) * (HR - 3) + 0.5;
			d.riftZ = HZ + Math.sin(a) * (HR - 3) + 0.5;
			d.riftY = HY + 1;
			WorldFx.run("effect give " + p.getStringUUID() + " minecraft:darkness 3 0 true");
			WorldFx.soundSelf(p, "minecraft:entity.leash_knot.break", 1.0f, 0.5f);
		}
	}

	/** Near death it drags everyone close towards it. */
	private static void drag(StarvedOne so, List<ServerPlayer> arena) {
		roar(so, 4f);
		for (ServerPlayer p : arena) {
			Vec3 to = so.position().subtract(p.position());
			double dist = to.length();
			if (dist < 26 && dist > 3) {
				// A short, hard yank towards it, never into a wall
				Vec3 step = to.normalize().scale(Math.min(6.0, dist * 0.4));
				BlockPos dest = BlockPos.containing(p.getX() + step.x, p.getY(), p.getZ() + step.z);
				ServerLevel seam = (ServerLevel) so.level();
				for (int dy = 2; dy >= -2; dy--) {
					BlockPos q = dest.above(dy);
					if (seam.getBlockState(q).isAir() && seam.getBlockState(q.above()).isAir() && !seam.getBlockState(q.below()).isAir()) {
						WorldFx.particles(p, "extremedimensions:lurker_wisp", 1.0, 0.5, 0, 10);
						WorldFx.run(String.format(Locale.ROOT, "tp %s %.2f %d %.2f", p.getStringUUID(), p.getX() + step.x, q.getY(), p.getZ() + step.z));
						break;
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ pedestals

	public static boolean isPedestal(BlockPos pos) {
		for (BlockPos p : PEDESTALS) {
			if (p.equals(pos)) {
				return true;
			}
		}
		return false;
	}

	/** Seam Silk fed to a pedestal lights it; two lit pedestals stun the Starved One. */
	public static InteractionResult onPedestal(ServerPlayer p, BlockPos pos, ItemStack stack) {
		if (boss == null || !stack.is(ModItems.SEAM_SILK)) {
			return InteractionResult.PASS;
		}
		for (int i = 0; i < PEDESTALS.length; i++) {
			if (PEDESTALS[i].equals(pos) && !LIT[i]) {
				LIT[i] = true;
				if (!p.isCreative()) {
					stack.shrink(1);
				}
				ServerLevel seam = (ServerLevel) p.level();
				seam.setBlock(pos.above(), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
				String dim = SeamService.SEAM.identifier().toString();
				WorldFx.particleIn(dim, "extremedimensions:soul_mote", pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 0.3, 1.5, 0.3, 0, 30);
				WorldFx.run(String.format(Locale.ROOT, "execute in %s run playsound minecraft:block.beacon.activate block @a %d %d %d 2 1.4",
						dim, pos.getX(), pos.getY(), pos.getZ()));
				int lit = 0;
				for (boolean b : LIT) {
					lit += b ? 1 : 0;
				}
				if (lit >= 2 && stun == 0) {
					stun = 160;
					Entity e = seam.getEntity(boss);
					if (e != null) {
						WorldFx.run(String.format(Locale.ROOT, "execute in %s run playsound extremedimensions:lurker_shriek hostile @a %.2f %.2f %.2f 4 0.5",
								dim, e.getX(), e.getY() + 4, e.getZ()));
					}
				}
				return InteractionResult.SUCCESS;
			}
		}
		return InteractionResult.PASS;
	}

	private static void unlight(ServerLevel seam) {
		for (int i = 0; i < PEDESTALS.length; i++) {
			LIT[i] = false;
			if (seam.getBlockState(PEDESTALS[i].above()).is(Blocks.SOUL_LANTERN)) {
				seam.setBlock(PEDESTALS[i].above(), Blocks.AIR.defaultBlockState(), 3);
			}
		}
	}

	// ------------------------------------------------------------------ the Heart

	/** The Heart of the Seam: a great island of hush brick around an altar of silk, ringed by four pedestals. */
	private static void buildHeart(ServerLevel seam) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		BlockState stone = ModBlocks.HUSH_STONE.defaultBlockState();
		BlockState brick = ModBlocks.HUSH_BRICKS.defaultBlockState();
		BlockState vein = ModBlocks.SEAM_VEIN.defaultBlockState();
		for (int dx = -HR; dx <= HR; dx++) {
			for (int dz = -HR; dz <= HR; dz++) {
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist > HR) {
					continue;
				}
				double f = 1 - dist / HR;
				int depth = 3 + (int) Math.round(Math.pow(f, 0.6) * 26) + rnd.nextInt(2);
				for (int y = HY - depth; y <= HY; y++) {
					BlockState s = y == HY ? ((int) dist % 6 == 0 ? vein : brick) : (rnd.nextInt(14) == 0 ? vein : stone);
					seam.setBlock(new BlockPos(HX + dx, y, HZ + dz), s, 2);
				}
				for (int y = HY + 1; y <= HY + 6; y++) {
					seam.setBlock(new BlockPos(HX + dx, y, HZ + dz), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		// The altar
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				seam.setBlock(new BlockPos(HX + dx, HY, HZ + dz), vein, 2);
			}
		}
		for (BlockPos p : PEDESTALS) {
			seam.setBlock(p, Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 3);
			seam.setBlock(p.below(), vein, 2);
		}
		// Pillars of silk around the rim
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4 + Math.PI / 8;
			int x = HX + (int) Math.round(Math.cos(a) * (HR - 2));
			int z = HZ + (int) Math.round(Math.sin(a) * (HR - 2));
			int h = 6 + rnd.nextInt(6);
			for (int k = 1; k <= h; k++) {
				seam.setBlock(new BlockPos(x, HY + k, z), (k % 3 == 0 ? vein : brick), 2);
			}
			seam.setBlock(new BlockPos(x, HY + h + 1, z), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
		}
		DataStore.setHeartBuilt(true);
	}

	// ------------------------------------------------------------------ the end

	public static void onDefeat(StarvedOne so) {
		if (bar != null) {
			bar.removeAllPlayers();
		}
		boss = null;
		bar = null;
		stun = 0;
		ServerLevel seam = (ServerLevel) so.level();
		unlight(seam);
		BlockPos c = new BlockPos(HX, HY + 1, HZ);
		Block.popResource(seam, c, new ItemStack(ModItems.MENDING_THREAD));
		Block.popResource(seam, c, new ItemStack(ModItems.SEVERING_SHEARS));
		String dim = SeamService.SEAM.identifier().toString();
		WorldFx.particleIn(dim, "extremedimensions:soul_mote", so.getX(), so.getY() + 3, so.getZ(), 2, 3, 2, 0, 200);
		WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", so.getX(), so.getY() + 3, so.getZ(), 2, 3, 2, 0, 120);
		for (ServerPlayer p : seam.players()) {
			if (inArena(p)) {
				Advancements.grant(p, Advancements.STARVED_SLAIN);
				Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.WANDERER);
				WorldFx.soundSelf(p, "extremedimensions:lurker_death", 1.0f, 0.5f);
			}
		}
	}

	/** The first of the two used decides the world. Afterwards the other one only crumbles. */
	public static InteractionResult useEnding(ServerPlayer p, ItemStack stack) {
		boolean mend = stack.is(ModItems.MENDING_THREAD);
		stack.shrink(1);
		if (!DataStore.ending().isEmpty()) {
			WorldFx.particles(p, "minecraft:large_smoke", 1.0, 0.3, 0.02, 12);
			WorldFx.soundSelf(p, "minecraft:block.wool.break", 1.0f, 0.6f);
			return InteractionResult.SUCCESS;
		}
		DataStore.setEnding(mend ? "mended" : "severed");
		MinecraftServer s = WorldFx.server();
		for (ServerPlayer q : s.getPlayerList().getPlayers()) {
			WorldFx.fx(q, mend ? Fx.SAFE : Fx.SEAL, 0);
			WorldFx.soundSelf(q, mend ? "extremedimensions:portal_home" : "extremedimensions:collapse", 1.0f, mend ? 0.8f : 0.6f);
			Journal.unlock(q, DataStore.get(q.getUUID()), JournalEntry.ENDING);
		}
		Advancements.grant(p, mend ? Advancements.ENDING_MENDED : Advancements.ENDING_SEVERED);
		return InteractionResult.SUCCESS;
	}

	public static void fade(StarvedOne so) {
		WorldFx.particleIn(SeamService.SEAM.identifier().toString(), "extremedimensions:lurker_wisp", so.getX(), so.getY() + 3, so.getZ(), 1.5, 2.5, 1.5, 0, 60);
		so.discard();
		if (bar != null) {
			bar.removeAllPlayers();
		}
		boss = null;
		bar = null;
		stun = 0;
		if (so.level() instanceof ServerLevel seam) {
			unlight(seam);
		}
	}

	/** Operator command: break the seal (as if the Dragon died) for testing. */
	public static void breakSeal(MinecraftServer s) {
		com.extremedimensions.world.WorldService.onDragonDeath(s);
	}
}
