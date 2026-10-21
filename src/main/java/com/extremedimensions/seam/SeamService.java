package com.extremedimensions.seam;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.lore.Loot;
import com.extremedimensions.lurker.LurkerService;
import com.extremedimensions.mending.MendingService;
import com.extremedimensions.network.Fx;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;
import com.extremedimensions.tether.TetherService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.effect.MobEffects;

/**
 * The Seam: the place between the wounds.
 *
 * <ul>
 *   <li><b>Rifts</b>: a Seam Needle used on crying obsidian tears a rift for fifteen seconds. Step into it and you
 *       are in the Seam, with a fixed amount of time.</li>
 *   <li><b>The way back</b>: you arrive on an island next to a return rift. The rift drifts, slowly, and a thread
 *       of silk is laid behind you so you can find it again. Step into it before the time runs out.</li>
 *   <li><b>Collapse</b>: run out of time or fall into the nothing and the Seam spits you out at your rift, cursed.</li>
 *   <li><b>Islands</b>: built as you explore, on a grid: quiet hush stone islands, Weaver looms strung with silk,
 *       and rare villages of the Thinned, with graves and a barrel of lore.</li>
 * </ul>
 */
public final class SeamService {
	private SeamService() {}

	public static final ResourceKey<Level> SEAM = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("extremedimensions", "the_seam"));
	private static final String SEAM_ID = "extremedimensions:the_seam";
	/** The island grid: one island (or none) per cell of this many blocks. */
	public static final int CELL = 100;
	private static final int PULL_TICKS = 60;

	/** A rift torn open in the world, waiting for someone to step in. */
	private static final class Rift {
		ServerLevel level;
		double x, y, z;
		int ticks;
		/** How it is seen, when tears are on (null otherwise). */
		RiftPortal portal;
		/** The way it faces, out from the obsidian it was torn on (null for one opened at someone's feet). */
		Direction face;
	}

	/** Ticks a rift a Seam Needle tears stays open. */
	private static final int RIFT_TICKS = 300;

	private static final List<Rift> RIFTS = new ArrayList<>();

	public static void clear() {
		RIFTS.clear();
		RiftPortals.clear();
		SeamArchitect.clear();
		SeamDepth.clear();
		falls = null;
		generation++;
		designing = false;
		seedKnown = false;
		sealChecked = false;
	}

	// ------------------------------------------------------------------ the seal (is the Seam still the one we built?)

	/** A barrier on the Seam's floor at the origin, set once: no island ever reaches down there, and no one can break it. */
	public static final BlockPos SEAL = new BlockPos(0, 0, 0);
	private static boolean sealChecked;

	/**
	 * Once a session, before anything is built: if the seal is gone (the Seam's folder was deleted, or reset with
	 * {@link SeamReset}), everything the world data remembers of it is forgotten so the Seam is designed again (see
	 * {@link #forget()}); islands a crash left unfinished ({@code u@} marks) are designed again too. A cell is otherwise
	 * never designed twice, so breaking an island's heart cannot make its chests and veins come back.
	 */
	public static void checkSeal(ServerLevel seam) {
		if (sealChecked) {
			return;
		}
		sealChecked = true;
		seam.getChunk(0, 0);
		boolean sealed = seam.getBlockState(SEAL).is(Blocks.BARRIER);
		java.util.Set<String> cells = DataStore.seamCells();
		if (!sealed && DataStore.seamSealed()) {
			ExtremeDimensions.LOGGER.info("The Seam was made anew: its {} remembered islands and marks are designed again", cells.size());
			forget();
		} else {
			List<String> unfinished = new ArrayList<>();
			for (String key : cells) {
				if (key.startsWith("u@")) {
					unfinished.add(key);
				}
			}
			for (String key : unfinished) {
				cells.remove(key);
				cells.remove(key.substring(2));
				// Some of it may stand already: it is built again only into open air
				cells.add("r@" + key.substring(2));
			}
			if (!unfinished.isEmpty()) {
				ExtremeDimensions.LOGGER.info("Seam architect: {} islands left unfinished by a crash will be built again", unfinished.size());
			}
		}
		falls = null;
		SeamDepth.forget();
		if (!sealed) {
			seam.setBlock(SEAL, Blocks.BARRIER.defaultBlockState(), 2);
		}
		DataStore.setSeamSealed(true);
		DataStore.markDirty();
	}

	/**
	 * The Seam is new: its islands and marks (villages, looms, barrels, falls) are forgotten, and the Heart, which is
	 * built again when someone comes near it; whoever was inside it is brought home when next seen ({@link #lost}).
	 */
	private static void forget() {
		DataStore.seamCells().clear();
		DataStore.setHeartBuilt(false);
		for (PlayerData d : DataStore.players()) {
			if (d.inSeam) {
				d.inSeam = false;
				d.seamLost = true;
			}
		}
	}

	/**
	 * The Seam was made anew while this player was in it: they are home again where their rift opened, with no curse
	 * and no collapse counted (a trial they were on is simply over).
	 */
	private static void lost(ServerPlayer p, PlayerData d) {
		d.seamLost = false;
		d.seamPull = 0;
		d.trial = null;
		TetherService.clearThread(p, d);
		if (inSeam(p)) {
			WorldFx.tp(p, Dims.fullIdOf(d.homeDim), d.homeX, d.homeY, d.homeZ);
			WorldFx.fx(p, Fx.SAFE, 0);
			p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.extremedimensions.seam.made_anew")
					.withStyle(net.minecraft.ChatFormatting.GOLD));
		}
		Sync.state(p, d);
		DataStore.markDirty();
	}

	/** An island's plan is fully built: it no longer needs building again after a crash. */
	static void built(SeamArchitect.Meta m) {
		if (!m.cell().isEmpty() && (DataStore.seamCells().remove("u@" + m.cell()) | DataStore.seamCells().remove("r@" + m.cell()))) {
			DataStore.markDirty();
		}
	}

	public static boolean inSeam(ServerPlayer p) {
		return Dims.SEAM.equals(Dims.of(p));
	}

	private static ServerLevel seam() {
		MinecraftServer s = WorldFx.server();
		return s == null ? null : s.getLevel(SEAM);
	}

	// ------------------------------------------------------------------ rifts

	/** A Seam Needle on crying obsidian: the air in front of it tears open. */
	public static InteractionResult openRift(ServerPlayer p, BlockPos obsidian, Direction face, ItemStack needle) {
		ServerLevel level = (ServerLevel) p.level();
		if (inSeam(p) || Dims.END.equals(Dims.of(level))) {
			return InteractionResult.FAIL;
		}
		BlockPos at = obsidian.relative(face);
		if (!level.getBlockState(at).canBeReplaced()) {
			return InteractionResult.FAIL;
		}
		if (!p.isCreative()) {
			needle.shrink(1);
		}
		Rift r = new Rift();
		r.level = level;
		r.x = at.getX() + 0.5;
		r.y = at.getY() + 0.1;
		r.z = at.getZ() + 0.5;
		r.portal = RiftPortals.entry(level, p, r.x, at.getY(), r.z, face, RIFT_TICKS);
		r.face = face;
		RIFTS.add(r);
		String dim = level.dimension().identifier().toString();
		WorldFx.soundIn(dim, "extremedimensions:rift_open", "player", r.x, r.y + 1, r.z, 1.5f, 1f);
		return InteractionResult.SUCCESS;
	}

	private static void tickRifts() {
		Iterator<Rift> it = RIFTS.iterator();
		List<Runnable> later = new ArrayList<>();
		while (it.hasNext()) {
			Rift r = it.next();
			r.ticks++;
			String dim = r.level.dimension().identifier().toString();
			// a tear has sparks of its own; a bare rift is all motes
			if (r.ticks % 2 == 0 && r.portal == null) {
				double a = r.ticks * 0.35;
				for (int k = 0; k < 3; k++) {
					double ang = a + k * 2.094;
					WorldFx.particleIn(dim, "extremedimensions:soul_mote", r.x + Math.cos(ang) * 0.6, r.y + 0.9 + Math.sin(a * 0.7 + k) * 0.6,
							r.z + Math.sin(ang) * 0.6, 0, 0, 0, 0, 1);
				}
				WorldFx.particleIn(dim, "minecraft:reverse_portal", r.x, r.y + 1, r.z, 0.2, 0.6, 0.2, 0.05, 4);
			}
			if (r.ticks % 10 == 0) {
				WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", r.x, r.y + 1, r.z, 0.3, 0.5, 0.3, 0, 2);
			}
			for (ServerPlayer p : r.level.players()) {
				if (!p.isSpectator() && (p.distanceToSqr(r.x, r.y + 0.5, r.z) < 1.3 * 1.3
						|| r.portal != null && RiftPortals.through(p, r.portal.getX(), r.portal.getY(), r.portal.getZ(), r.portal.getYRot(), r.portal.radius()))) {
					later.add(() -> enter(p, r));
				}
			}
			if (r.ticks > RIFT_TICKS) {
				WorldFx.particleIn(dim, "minecraft:reverse_portal", r.x, r.y + 1, r.z, 0.3, 0.8, 0.3, 0.3, 30);
				it.remove();
			}
		}
		later.forEach(Runnable::run);
	}

	/**
	 * Home is a step out in front of a tear, on the side the player came from, not in it: a tear stays open a while, and
	 * one coming straight back would otherwise be pulled through it again.
	 */
	private static void home(ServerPlayer p, PlayerData d, Rift from) {
		if (from.face == null) {
			return;
		}
		double ox = from.face.getStepX(), oz = from.face.getStepZ();
		if (from.face.getAxis().isVertical()) {
			// torn in a floor or a ceiling: the way they walked in
			ox = p.xo - from.x;
			oz = p.zo - from.z;
			double len = Math.sqrt(ox * ox + oz * oz);
			if (len < 0.1) {
				return;
			}
			ox /= len;
			oz /= len;
		}
		double hx = from.x + ox * 1.6, hz = from.z + oz * 1.6;
		BlockPos at = BlockPos.containing(hx, from.y, hz);
		if (from.level.getBlockState(at).getCollisionShape(from.level, at).isEmpty()
				&& from.level.getBlockState(at.above()).getCollisionShape(from.level, at.above()).isEmpty()) {
			d.homeX = hx;
			d.homeZ = hz;
		}
	}

	/** Through the rift: an island is built where the player lands, with the return rift beside it. */
	private static void enter(ServerPlayer p, Rift from) {
		ServerLevel seam = seam();
		PlayerData d = DataStore.get(p.getUUID());
		// The fallen cannot leave their world, not even through a rift
		if (seam == null || d.inSeam || com.extremedimensions.coop.FallenService.fallen(p)) {
			return;
		}
		checkSeal(seam);
		d.homeDim = Dims.of(from.level);
		d.homeX = from.x;
		d.homeY = from.y;
		d.homeZ = from.z;
		home(p, d, from);
		int x = (int) Math.floor(from.x);
		int z = (int) Math.floor(from.z);
		if (MendingService.opensNearHeart()) {
			// The seal is broken: every rift opens near the Heart, where all the threads meet
			int[] a = MendingService.arrival();
			x = a[0];
			z = a[1];
		}
		int top = 96;
		SeamArchitect.Plan arrival = new SeamArchitect.Plan();
		Random rnd = new Random(x * 31L + z);
		SeamArchitect.Shape shape = SeamArchitect.Shape.of(rnd, x, top, z, 12);
		double landX = x + 0.5, landY = top + 1, landZ = z + 0.5;
		float yaw = p.getYRot();
		double riftX = x + 3.5, riftY = top + 1, riftZ = z + 0.5;
		// a bare rift beside the landing faces it across the island
		float riftYaw = -90;
		boolean gate = Config.SEAM_RETURN_GATE > 0;
		if (gate) {
			// The gate stands on the far side of the island from the bridge, looking down the island to it
			Approach way = approach(x, z, shape);
			Direction out = way.dir();
			int tx = x - out.getStepX() * ReturnGate.OFFSET, tz = z - out.getStepZ() * ReturnGate.OFFSET;
			// The gate's rock first; the arrival island is laid over it where they meet
			Terrain rock = Terrain.of(rnd, SeamArchitect.Shape.of(rnd, tx, top, tz, ReturnGate.ROCK), Terrain.ALL_SIDES);
			rock.emit(arrival);
			Terrain land = Terrain.of(rnd, shape, Terrain.ALL_SIDES);
			land.emit(arrival);
			// The gate is set down on them: the ground under it levelled, a ledge grown under whatever overhangs
			SeamArchitect.Plan gatePlan = new SeamArchitect.Plan();
			ReturnGate.Gate g = ReturnGate.build(gatePlan, rnd, tx, top, tz, out);
			Terrain.settle(arrival, gatePlan, top, rock, land);
			arrival.merge(gatePlan);
			ReturnGate.approach(arrival, rnd, tx, top, tz, out);
			if (way.to() != null) {
				boolean alongX = out.getAxis() == Direction.Axis.X;
				boolean ahead = out.getAxisDirection() == Direction.AxisDirection.POSITIVE;
				SeamArchitect.bridge(arrival, rnd, ahead ? shape : way.to(), ahead ? way.to() : shape, alongX);
			}
			landX = g.landX();
			landY = g.landY();
			landZ = g.landZ();
			yaw = g.yaw();
			riftX = g.riftX();
			riftY = g.riftY();
			riftZ = g.riftZ();
			// its tear stands across the gate, between the pylons
			riftYaw = g.yaw();
		} else {
			SeamArchitect.island(arrival, rnd, shape, true);
			SeamArchitect.lamp(arrival, rnd, x - 3, top + 1, z - 3);
		}
		// The ground under the one arriving is there at once; the rest of the island (its gate too) flies in around them
		arrival.placeAround(seam, gate ? landX : x + 2, gate ? landZ : z + 0.5, gate ? 4.5 : 7);
		if (!gate) {
			for (int dx = -1; dx <= 5; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					// the landing and the rift stand clear
					seam.setBlock(new BlockPos(x + dx, top + 1, z + dz), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		addFalls(arrival.falls);
		DataStore.seamCells().add("a@" + x + "," + z);
		d.riftX = riftX;
		d.riftY = riftY;
		d.riftZ = riftZ;
		d.riftYaw = riftYaw;
		d.inSeam = true;
		d.seamVisits++;
		d.seamMax = Math.max(30, Config.SEAM_SECONDS) + Config.WOVEN_SEAM_SECONDS * TetherService.carriedTier(p, d)
				+ (com.extremedimensions.relic.RelicService.has(d, com.extremedimensions.relic.Relic.SEAM_LANTERN) ? Math.max(0, Config.RELIC_SEAM_LANTERN_SECONDS) : 0)
				+ Math.max(0, Config.ENCH_SILKBOUND_SECONDS) * TetherService.charm(p, d, com.extremedimensions.registry.ModEnchantments.SILKBOUND);
		d.seamLeft = d.seamMax;
		d.seamPull = 0;
		// A Rift Trial taken up at home begins with this visit
		com.extremedimensions.trial.TrialService.begin(p, d, x, z);
		d.hasRopeEnd = false;
		// Out of the gate, facing away from it, the way home at their back
		WorldFx.tp(p, SEAM_ID, landX, landY, landZ, yaw, 0);
		WorldFx.sound(p, "extremedimensions:rift_open", 1.0f, 0.8f);
		WorldFx.fx(p, Fx.START_END, 0);
		if (d.seamVisits <= Config.SEAM_BRIEFING) {
			// The first few times: what the Seam is for and how to get home, on the screen
			WorldFx.fx(p, Fx.SEAM_BRIEF, (int) Math.round(d.seamMax));
		} else if (gate && d.seamVisits <= 3) {
			// (with the briefing turned off, still a word on where home is)
			p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.extremedimensions.seam.gate_hint")
					.withStyle(net.minecraft.ChatFormatting.GOLD), true);
		}
		Advancements.grant(p, Advancements.SEAM_ENTERED);
		Journal.unlock(p, d, JournalEntry.SEAM);
		DataStore.markDirty();
	}

	// ------------------------------------------------------------------ ticking

	public static void tick(MinecraftServer s) {
		tickRifts();
		ServerLevel level = s.getLevel(SEAM);
		if (level != null) {
			if (SeamArchitect.pending() > 0) {
				SeamArchitect.tick(level, Math.max(1, Config.SEAM_BUILD_BUDGET_MS) * 1_000_000L);
			}
			if (s.getTickCount() % 200 == 0 && SeamArchitect.pending() > 100_000) {
				forgetFar(level);
			}
			lights(level, s.getTickCount());
			SeamDepth.tickWorld(level);
		}
		boolean slow = s.getTickCount() % 20 == 0;
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			PlayerData d = DataStore.get(p.getUUID());
			if (d.inSeam && level != null && inSeam(p)) {
				// A visit carried over from before the server started: is the Seam it was in still there?
				checkSeal(level);
			}
			if (d.seamLost && p.isAlive()) {
				lost(p, d);
				continue;
			}
			if (!d.inSeam || !p.isAlive()) {
				continue;
			}
			if (!inSeam(p)) {
				// Left the Seam some other way (a command, a death): the thread is cut
				d.inSeam = false;
				RiftPortals.closeHome(p);
				com.extremedimensions.trial.TrialService.fail(p, d);
				TetherService.clearThread(p, d);
				continue;
			}
			tickPlayer(p, d, s.getTickCount());
			if (slow) {
				build(p);
				com.extremedimensions.trial.TrialService.tick(p, d);
				Sync.state(p, d);
			}
		}
	}

	private static void tickPlayer(ServerPlayer p, PlayerData d, int tick) {
		if (d.seamPull > 0) {
			pull(p, d);
			return;
		}
		if (!p.isCreative() && !(MendingService.active() && MendingService.inArena(p))) {
			// Time runs out faster in the Fray
			d.seamLeft -= SeamDepth.drain(p.getY()) * com.extremedimensions.events.FrayedSeam.drain(p) / 20.0;
		}
		TetherService.lay(p, d);
		SeamDepth.tick(p, d, tick);
		String dim = SEAM_ID;
		ServerLevel seam = seam();
		if (seam != null) {
			RiftPortals.keepHome(seam, p, d);
		}
		// The return rift: sparks of gold spiralling up, and the pull of a portal (the column over it is the client's;
		// a tear, when it has one, has sparks of its own)
		if (tick % 3 == 0 && !RiftPortals.on()) {
			double a = tick * 0.2;
			WorldFx.particleIn(dim, "extremedimensions:ember_mote", d.riftX + Math.cos(a) * 0.6, d.riftY + 0.3 + (tick % 30) / 15.0,
					d.riftZ + Math.sin(a) * 0.6, 0, 0, 0, 0, 1);
			WorldFx.particleIn(dim, "minecraft:reverse_portal", d.riftX, d.riftY + 1, d.riftZ, 0.15, 0.5, 0.15, 0.04, 2);
		}
		boolean restless = com.extremedimensions.trial.TrialService.restless(d);
		if (tick % (restless ? 100 : 200) == 0) {
			drift(seam, d, Config.SEAM_RIFT_LEASH > 0 && !restless);
		}
		// It hums: a sound from the rift itself, so it can be found by ear in the fog
		if (Config.SEAM_RIFT_HUM > 0 && tick % 80 == 0 && p.distanceToSqr(d.riftX, d.riftY, d.riftZ) < Config.SEAM_RIFT_HUM * Config.SEAM_RIFT_HUM) {
			float vol = Config.SEAM_RIFT_HUM / 16f;
			WorldFx.soundFor(p, "minecraft:block.beacon.ambient", "ambient", d.riftX, d.riftY + 1, d.riftZ, vol, 0.62f);
			if (ThreadLocalRandom.current().nextInt(3) == 0) {
				WorldFx.soundFor(p, "minecraft:block.amethyst_block.chime", "ambient", d.riftX, d.riftY + 1.5, d.riftZ, vol * 0.6f, 0.7f);
			}
		}
		if (tick % 160 == 0) {
			WorldFx.soundSelf(p, "extremedimensions:seam_ambient", 0.5f, 1.0f);
		}
		if (tick % 600 == 0 && ThreadLocalRandom.current().nextInt(3) == 0) {
			// The Thinned watch, but here they do not take
			LurkerService.spawnWatch(p);
		}
		boolean fallen = com.extremedimensions.coop.FallenService.fallen(p);
		if (d.seamLeft <= 0 || p.getY() < 8) {
			// Out of time (checked first: no slipping through the rift as it runs out); fallen, there is no pulling them out
			if (fallen) {
				com.extremedimensions.coop.FallenService.bleedOut(p);
			} else {
				collapse(p, d);
			}
		} else if (!fallen && (p.distanceToSqr(d.riftX, d.riftY + 0.5, d.riftZ) < 1.4 * 1.4
				|| RiftPortals.on() && RiftPortals.through(p, d.riftX, d.riftY, d.riftZ, d.riftYaw, RiftPortals.homeRadius()))) {
			exit(p, d);
		}
		villages(p);
	}

	/**
	 * The rift wanders a block at a time, never off solid ground. Leashed, it keeps to the floor it stands on (its
	 * gate's threshold), and with a tear (which stands across the gate, as wide as its opening) only back and forth
	 * through the gate, never sideways into a pylon; let loose (a Restless Rift trial, or the leash turned off) it
	 * roams, a step up or down too.
	 */
	public static void drift(ServerLevel seam, PlayerData d, boolean leashed) {
		Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(seam.getRandom());
		if (leashed && RiftPortals.on()) {
			Direction ahead = Direction.fromYRot(d.riftYaw);
			dir = seam.getRandom().nextBoolean() ? ahead : ahead.getOpposite();
		}
		BlockPos at = BlockPos.containing(d.riftX, d.riftY, d.riftZ);
		BlockState floor = seam.getBlockState(at.below());
		BlockPos next = at.relative(dir);
		for (int dy : leashed ? new int[] {0} : new int[] {0, 1, -1}) {
			BlockPos n = next.above(dy);
			BlockState under = seam.getBlockState(n.below());
			if (!seam.getBlockState(n).isAir() || !seam.getBlockState(n.above()).isAir() || !under.isFaceSturdy(seam, n.below(), Direction.UP)) {
				continue;
			}
			if (leashed && !under.is(floor.getBlock())) {
				return;
			}
			d.riftX = n.getX() + 0.5;
			d.riftY = n.getY();
			d.riftZ = n.getZ() + 0.5;
			return;
		}
	}

	private static void exit(ServerPlayer p, PlayerData d) {
		d.inSeam = false;
		RiftPortals.closeHome(p);
		com.extremedimensions.trial.TrialService.finish(p, d);
		// The thread home is taken up; a Tether bound before the visit stays bound
		TetherService.clearThread(p, d);
		WorldFx.tp(p, Dims.fullIdOf(d.homeDim), d.homeX, d.homeY, d.homeZ);
		WorldFx.fx(p, Fx.SAFE, 0);
		WorldFx.soundSelf(p, "extremedimensions:portal_home", 0.6f, 1.1f);
		Advancements.grant(p, Advancements.SEAM_RETURNED);
		DataStore.markDirty();
	}

	/** Out of time, or fallen: the Seam drags the player back out over three seconds. */
	private static void collapse(ServerPlayer p, PlayerData d) {
		d.seamPull = PULL_TICKS;
		WorldFx.fx(p, Fx.COLLAPSE, 0);
		WorldFx.sound(p, "extremedimensions:collapse", 1.2f, 1.1f);
		WorldFx.effect(p, MobEffects.LEVITATION, 3, 0);
		WorldFx.effect(p, MobEffects.RESISTANCE, 5, 4);
	}

	private static void pull(ServerPlayer p, PlayerData d) {
		WorldFx.particles(p, "extremedimensions:soul_mote", 1.0, 1.2, 0, 4);
		if (--d.seamPull > 0) {
			return;
		}
		if (com.extremedimensions.coop.FallenService.fallen(p)) {
			// Fell during the pull: the Seam cannot drag them out, and they do not last
			com.extremedimensions.coop.FallenService.bleedOut(p);
			return;
		}
		d.inSeam = false;
		RiftPortals.closeHome(p);
		d.collapses++;
		com.extremedimensions.trial.TrialService.fail(p, d);
		TetherService.clearThread(p, d);
		p.removeEffect(MobEffects.LEVITATION);
		WorldFx.tp(p, Dims.fullIdOf(d.homeDim), d.homeX, d.homeY, d.homeZ);
		PenaltyService.curse(p, Dims.OVERWORLD.equals(d.homeDim));
		DataStore.markDirty();
	}

	/** Seam Silk and lore are found in villages; the first visit to one is remembered. */
	private static void villages(ServerPlayer p) {
		if (p.tickCount % 40 != 0) {
			return;
		}
		for (String key : DataStore.seamCells()) {
			if (!key.startsWith("v@")) {
				continue;
			}
			String[] xyz = key.substring(2).split(",");
			double dx = Integer.parseInt(xyz[0]) - p.getX();
			double dz = Integer.parseInt(xyz[2]) - p.getZ();
			if (dx * dx + dz * dz < 16 * 16) {
				Advancements.grant(p, Advancements.THINNED_FOUND);
				Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.THINNED);
				// The first visit to a village may wake its Weaver
				com.extremedimensions.elder.WeaverService.nearVillage(p, Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
				return;
			}
		}
	}

	// ------------------------------------------------------------------ islands

	private static long hash(long a, long b, long salt) {
		long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return h & Long.MAX_VALUE;
	}

	private static volatile long seedCache;
	private static volatile boolean seedKnown;

	/** The world seed, read once on the server thread (designs run on their own thread and only use the copy). */
	private static long seed() {
		if (!seedKnown) {
			ServerLevel level = seam();
			seedCache = level == null ? 0 : level.getSeed();
			seedKnown = level != null;
		}
		return seedCache;
	}

	private static final int PLAIN = 0, LOOM = 1, VILLAGE = 2, SPIRE = 3, HANGING = 4, HALO = 5;

	private static int kind(int type) {
		return type < 7 ? PLAIN : type < 10 ? LOOM : type < 13 ? VILLAGE : type < 16 ? SPIRE : type < 18 ? HANGING : HALO;
	}

	/** The island of a cell: x, top, z, radius, type. */
	private static int[] center(int cx, int cz) {
		long h = hash(cx, cz, seed());
		int type = (int) ((h >> 32) % 20);
		int x = cx * CELL + CELL / 2 + (int) (h % 13) - 6;
		int z = cz * CELL + CELL / 2 + (int) ((h >> 8) % 13) - 6;
		int top = 80 + (int) ((h >> 16) % 20);
		int v = (int) ((h >> 24) % 5);
		int r = switch (kind(type)) {
			case SPIRE -> 34 + v;
			case VILLAGE -> 26 + v;
			case LOOM -> 22 + v;
			case HANGING -> 14 + v;
			case HALO -> 16 + v;
			default -> 11 + (int) ((h >> 24) % 9);
		};
		return new int[] {x, top, z, r, type};
	}

	/** Cells around the Heart are left to its crown (and no island may reach into it). */
	private static boolean nearHeart(int[] c) {
		double dx = c[0] - MendingService.HX, dz = c[2] - MendingService.HZ;
		double reach = 84 + c[3];
		return dx * dx + dz * dz < reach * reach;
	}

	/** True when a rift's arrival island lies within {@code dist} of this point. */
	private static boolean nearArrival(int x, int z, int dist, List<int[]> arrivals) {
		for (int[] a : arrivals) {
			double dx = a[0] - x, dz = a[1] - z;
			if (dx * dx + dz * dz < (double) dist * dist) {
				return true;
			}
		}
		return false;
	}

	/** The way an arrival island looks out (towards the island it is joined to), and the island its bridge runs to. */
	private record Approach(Direction dir, SeamArchitect.Shape to) {}

	/**
	 * Where the arrival island is joined to the rest of the Seam: the nearest island along one of the four directions
	 * that a bridge can reach without a landing as wide as a square, or one the arrival island already touches (no
	 * bridge then). Remembered for the spot ("g@x,z=dir[,cx,cz,r]"), so whoever comes back by the same rift finds the
	 * same gate and the same bridge, however the islands round it were built since.
	 */
	private static Approach approach(int x, int z, SeamArchitect.Shape a) {
		String prefix = "g@" + x + "," + z + "=";
		for (String key : DataStore.seamCells()) {
			if (!key.startsWith(prefix)) {
				continue;
			}
			try {
				String[] v = key.substring(prefix.length()).split(",");
				Direction dir = Direction.from2DDataValue(Integer.parseInt(v[0]));
				if (v.length < 4) {
					return new Approach(dir, null);
				}
				int cx = Integer.parseInt(v[1]), cz = Integer.parseInt(v[2]), r = Integer.parseInt(v[3]);
				int[] c = center(cx, cz);
				return new Approach(dir, SeamArchitect.Shape.of(new Random(hash(cx, cz, seed() + 7)), c[0], c[1], c[2], r));
			} catch (RuntimeException e) {
				break;
			}
		}
		// An island built before this arrival was designed without it; one still to come will be designed with it
		List<int[]> before = arrivals();
		List<int[]> with = new ArrayList<>(before);
		with.add(new int[] {x, z});
		int acx = Math.floorDiv(x, CELL), acz = Math.floorDiv(z, CELL);
		Direction best = null;
		SeamArchitect.Shape to = null;
		String cell = "";
		double score = Double.MAX_VALUE;
		for (int cx = acx - 1; cx <= acx + 1; cx++) {
			for (int cz = acz - 1; cz <= acz + 1; cz++) {
				int[] c = center(cx, cz);
				if (nearHeart(c)) {
					continue;
				}
				boolean built = DataStore.seamCells().contains("c@" + cx + "," + cz);
				SeamArchitect.Shape s = shapeOf(cx, cz, new Random(hash(cx, cz, seed() + 7)), built ? before : with);
				double dx = s.x() - a.x(), dz = s.z() - a.z();
				boolean alongX = Math.abs(dx) >= Math.abs(dz);
				double along = Math.abs(alongX ? dx : dz), side = Math.abs(alongX ? dz : dx);
				boolean touch = Math.sqrt(dx * dx + dz * dz) - s.r() - a.r() <= 2;
				boolean ahead = alongX ? dx > 0 : dz > 0;
				if (!touch && (side > 30 || !(ahead ? SeamArchitect.canBridge(a, s, alongX) : SeamArchitect.canBridge(s, a, alongX)))) {
					continue;
				}
				double sc = touch ? 0 : along + 1.5 * side;
				if (sc < score) {
					score = sc;
					best = alongX ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
					to = touch ? null : s;
					cell = touch ? "" : "," + cx + "," + cz + "," + s.r();
				}
			}
		}
		// Beside the Heart, the gate looks to it
		double hx = MendingService.HX - a.x(), hz = MendingService.HZ - a.z();
		boolean heart = DataStore.heartBuilt() || MendingService.opensNearHeart();
		if (heart && Math.sqrt(hx * hx + hz * hz) - MendingService.HR - a.r() <= 2) {
			best = Math.abs(hx) >= Math.abs(hz) ? (hx > 0 ? Direction.EAST : Direction.WEST) : (hz > 0 ? Direction.SOUTH : Direction.NORTH);
			to = null;
			cell = "";
		}
		if (best == null) {
			best = Direction.from2DDataValue(Math.floorMod(x * 7 + z * 13, 4));
		}
		DataStore.seamCells().add(prefix + best.get2DDataValue() + cell);
		return new Approach(best, to);
	}

	/**
	 * The sides of a cell's island an arrival island's bridge comes in on (bits as a plan's mask: 1 east, 2 west, 4
	 * south, 8 north), read on the server thread: the island is given a level way in there too, so the bridge lands on
	 * it as on any other.
	 */
	public static int inbound(int cx, int cz) {
		int mask = 0;
		String tail = "," + cx + "," + cz + ",";
		for (String key : DataStore.seamCells()) {
			int eq = key.indexOf('=');
			if (!key.startsWith("g@") || eq < 0 || !key.substring(eq).contains(tail)) {
				continue;
			}
			String[] v = key.substring(eq + 1).split(",");
			if (v.length < 4 || !v[1].equals(String.valueOf(cx)) || !v[2].equals(String.valueOf(cz))) {
				continue;
			}
			try {
				// The arrival looks out this way; its bridge comes in on this island's opposite side
				mask |= switch (Direction.from2DDataValue(Integer.parseInt(v[0]))) {
					case EAST -> 2;
					case WEST -> 1;
					case SOUTH -> 8;
					default -> 4;
				};
			} catch (NumberFormatException e) {
				// not a way
			}
		}
		return mask;
	}

	/** Every rift's arrival island (x, z), copied on the server thread so a design can read it on its own. */
	private static List<int[]> arrivals() {
		List<int[]> out = new ArrayList<>();
		for (String key : DataStore.seamCells()) {
			if (key.startsWith("a@")) {
				String[] xz = key.substring(2).split(",");
				out.add(new int[] {Integer.parseInt(xz[0]), Integer.parseInt(xz[1])});
			}
		}
		return out;
	}

	/** Designs the cells around the player, nearest first, one per call; the architect builds them over time. */
	private static void build(ServerPlayer p) {
		checkSeal((ServerLevel) p.level());
		if (designing || SeamArchitect.pending() > 150_000) {
			return;
		}
		int pcx = Math.floorDiv(p.getBlockX(), CELL);
		int pcz = Math.floorDiv(p.getBlockZ(), CELL);
		for (int ring = 0; ring <= 2; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					int cx = pcx + dx, cz = pcz + dz;
					String key = "c@" + cx + "," + cz;
					if (DataStore.seamCells().contains(key)) {
						continue;
					}
					DataStore.seamCells().add(key);
					// Until its last block stands (a stop while it is still being designed included), it is unfinished
					DataStore.seamCells().add("u@" + key);
					designLater(p.level().getServer(), cx, cz);
					return;
				}
			}
		}
		// Every island near is designed: now the Fray beneath them, nearest first
		if (Config.SEAM_FRAY <= 0) {
			return;
		}
		for (int ring = 0; ring <= 1; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					int cx = pcx + dx, cz = pcz + dz;
					String key = "d@" + cx + "," + cz;
					if (DataStore.seamCells().contains(key)) {
						continue;
					}
					DataStore.seamCells().add(key);
					DataStore.seamCells().add("u@" + key);
					designDepthLater(p.level().getServer(), cx, cz);
					return;
				}
			}
		}
	}

	/**
	 * Whether the player is over the island of the cell they are in (a designed cell, within its island's reach): a
	 * wandering trial counts islands reached, not blocks of one's own laid out over the nothing.
	 */
	public static boolean overIsland(ServerPlayer p) {
		int cx = Math.floorDiv(p.getBlockX(), CELL), cz = Math.floorDiv(p.getBlockZ(), CELL);
		if (!DataStore.seamCells().contains("c@" + cx + "," + cz)) {
			return false;
		}
		int[] c = center(cx, cz);
		if (nearHeart(c)) {
			return false;
		}
		int r = c[3];
		if (nearArrival(c[0], c[2], r + 18, arrivals())) {
			r = Math.min(r, 10);
		}
		double dx = p.getX() - c[0], dz = p.getZ() - c[2], reach = r + 3;
		return dx * dx + dz * dz <= reach * reach;
	}

	/** The island of a cell as it is (or will be) built: the same random source, so the rim is predicted exactly. */
	private static SeamArchitect.Shape shapeOf(int cx, int cz, Random rnd, List<int[]> arrivals) {
		int[] c = center(cx, cz);
		int r = c[3];
		if (nearArrival(c[0], c[2], r + 18, arrivals)) {
			r = Math.min(r, 10);
		}
		return SeamArchitect.Shape.of(rnd, c[0], c[1], c[2], r);
	}

	// ------------------------------------------------------------------ designing (off the server thread)

	/** One thread designs islands so the server never stalls on it; the plans are queued back on the server thread. */
	private static final ExecutorService DESIGNER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Extreme Dimensions Seam architect");
		t.setDaemon(true);
		return t;
	});
	/** Bumped when the world closes, so a design finished for the old world is thrown away. */
	private static volatile int generation;
	private static boolean designing;

	private static void designLater(MinecraftServer s, int cx, int cz) {
		long seed = seed();
		List<int[]> arrivals = arrivals();
		int inbound = inbound(cx, cz);
		int gen = generation;
		designing = true;
		DESIGNER.execute(() -> {
			SeamArchitect.Plan plan = null;
			try {
				plan = plan(cx, cz, arrivals, inbound);
			} catch (Throwable e) {
				// Whatever goes wrong, the result is still handed back below, or the Seam would never design again
				ExtremeDimensions.LOGGER.error("Could not design Seam cell {},{} (seed {})", cx, cz, seed, e);
			}
			SeamArchitect.Plan done = plan;
			s.execute(() -> {
				if (gen != generation) {
					return;
				}
				designing = false;
				if (done != null) {
					commit(done);
				} else {
					// Nothing to build there (the Heart's crown stands there, or the design failed)
					DataStore.seamCells().remove("u@c@" + cx + "," + cz);
				}
			});
		});
	}

	/** Designs the Fray beneath a cell on the architect's thread, like an island. */
	private static void designDepthLater(MinecraftServer s, int cx, int cz) {
		long seed = seed();
		List<int[]> arrivals = arrivals();
		int gen = generation;
		designing = true;
		DESIGNER.execute(() -> {
			SeamArchitect.Plan plan = null;
			try {
				plan = SeamDepth.plan(cx, cz, neighbour(cx, cz, arrivals), neighbour(cx + 1, cz, arrivals), neighbour(cx, cz + 1, arrivals), seed);
			} catch (Throwable e) {
				// Whatever goes wrong, the result is still handed back below, or the Seam would never design again
				ExtremeDimensions.LOGGER.error("Could not design the Fray under Seam cell {},{} (seed {})", cx, cz, seed, e);
			}
			SeamArchitect.Plan done = plan;
			s.execute(() -> {
				if (gen != generation) {
					return;
				}
				designing = false;
				if (done != null) {
					commit(done);
				} else {
					DataStore.seamCells().remove("u@d@" + cx + "," + cz);
				}
			});
		});
	}

	/** Unfinished islands far from everyone in the Seam are forgotten (they wait in memory); they are designed again on return. */
	private static void forgetFar(ServerLevel level) {
		List<double[]> near = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			near.add(new double[] {p.getX(), p.getZ()});
		}
		List<SeamArchitect.Meta> dropped = SeamArchitect.dropFar(near, 3.5 * CELL);
		forget(dropped);
		if (!dropped.isEmpty()) {
			ExtremeDimensions.LOGGER.info("Seam architect: put off {} unfinished islands far from everyone", dropped.size());
		}
	}

	/**
	 * On stopping, before the world is saved: blocks in the air land, and islands not yet finished are forgotten, so
	 * they are designed and built again (whole) when someone comes back instead of staying half built for good.
	 */
	public static void stopping() {
		// Strands that gave way in the Fray are woven back before the world is saved
		SeamDepth.reweave(seam());
		List<SeamArchitect.Meta> left = SeamArchitect.stop(seam());
		forget(left);
		if (!left.isEmpty()) {
			ExtremeDimensions.LOGGER.info("Seam architect: {} unfinished islands will be built again on the next visit", left.size());
		}
	}

	/** Forgets cells (and their light falls and marks), so they are designed again. */
	private static void forget(List<SeamArchitect.Meta> plans) {
		for (SeamArchitect.Meta m : plans) {
			if (m.cell().isEmpty()) {
				// An arrival island or the Heart's crown: not designed again, so its light falls stay
				continue;
			}
			DataStore.seamCells().remove(m.cell());
			DataStore.seamCells().remove("u@" + m.cell());
			if (m.started()) {
				// Partly standing: when it is designed again it only fills open air, and its veins do not grow back
				DataStore.seamCells().add("r@" + m.cell());
			}
			for (int[] f : m.falls()) {
				DataStore.seamCells().remove("f@" + f[0] + "," + f[1] + "," + f[2] + "," + f[3]);
			}
			m.marks().forEach(DataStore.seamCells()::remove);
		}
		if (!plans.isEmpty()) {
			falls = null;
			DataStore.markDirty();
		}
	}

	/** On the server thread: the plan goes into the build queue and its marks and light falls into the world data. */
	private static void commit(SeamArchitect.Plan plan) {
		plan.rebuild = plan.cell != null && DataStore.seamCells().contains("r@" + plan.cell);
		String cell = plan.queue();
		if (cell != null) {
			// Until its last block stands, a crash leaves this island to be designed again
			DataStore.seamCells().add("u@" + cell);
		}
		DataStore.seamCells().addAll(plan.marks);
		SeamDepth.forget();
		addFalls(plan.falls);
		DataStore.markDirty();
	}

	/** The plan of a cell, ready to queue, or null where the Heart's crown stands. Touches no world. */
	private static SeamArchitect.Plan plan(int cx, int cz, List<int[]> arrivals, int inbound) {
		int[] c = center(cx, cz);
		if (nearHeart(c)) {
			return null;
		}
		Random rnd = new Random(hash(cx, cz, seed() + 7));
		SeamArchitect.Shape shape = shapeOf(cx, cz, rnd, arrivals);
		SeamArchitect.Plan plan = new SeamArchitect.Plan();
		int x = shape.x(), top = shape.top(), z = shape.z(), r = shape.r();
		int kind = nearArrival(x, z, r + 30, arrivals) ? PLAIN : kind(c[4]);
		// Which sides get a bridge: this cell strings them east and south, its neighbours west and north
		SeamArchitect.Shape east = neighbour(cx + 1, cz, arrivals), west = neighbour(cx - 1, cz, arrivals);
		SeamArchitect.Shape south = neighbour(cx, cz + 1, arrivals), north = neighbour(cx, cz - 1, arrivals);
		boolean toEast = east != null && (hash(cx, cz, seed() + 11) & 3) != 0 && SeamArchitect.canBridge(shape, east, true);
		boolean toSouth = south != null && (hash(cx, cz, seed() + 13) & 3) != 0 && SeamArchitect.canBridge(shape, south, false);
		boolean fromWest = west != null && (hash(cx - 1, cz, seed() + 11) & 3) != 0 && SeamArchitect.canBridge(west, shape, true);
		boolean fromNorth = north != null && (hash(cx, cz - 1, seed() + 13) & 3) != 0 && SeamArchitect.canBridge(north, shape, false);
		// (and the sides an arrival island's bridge comes in on)
		int mask = (toEast ? 1 : 0) | (fromWest ? 2 : 0) | (toSouth ? 4 : 0) | (fromNorth ? 8 : 0) | inbound;
		Terrain ground = Terrain.of(rnd, shape, mask);
		int depth = ground.emit(plan);
		build(plan, rnd, kind, shape, depth, mask, ground);
		if (toEast) {
			SeamArchitect.bridge(plan, rnd, shape, east, true);
		}
		if (toSouth) {
			SeamArchitect.bridge(plan, rnd, shape, south, false);
		}
		plan.cell = "c@" + cx + "," + cz;
		plan.prepare();
		return plan;
	}

	/** The island of a neighbouring cell as it is (or will be) built, or null if the Heart's crown has that cell. */
	private static SeamArchitect.Shape neighbour(int cx, int cz, List<int[]> arrivals) {
		return nearHeart(center(cx, cz)) ? null : shapeOf(cx, cz, new Random(hash(cx, cz, seed() + 7)), arrivals);
	}

	/**
	 * Builds what stands on an island, then lays its streets out to the bridges ({@code mask}: bit 0 east, 1 west,
	 * 2 south, 3 north) and dresses the open ground that is left. Whatever stands on the ground is designed on its own
	 * first and then set down on the island ({@link #standOn}), so the ground is fitted to it.
	 */
	private static void build(SeamArchitect.Plan plan, Random rnd, int kind, SeamArchitect.Shape s, int depth, int mask, Terrain ground) {
		int x = s.x(), top = s.top(), z = s.z(), r = s.r();
		switch (kind) {
			case SPIRE -> {
				SeamArchitect.Plan b = new SeamArchitect.Plan();
				Colossus.build(b, rnd, x, top, z, colossusRadius(s));
				standOn(plan, b, ground, top);
				SeamArchitect.streets(plan, rnd, s, mask, 0, false);
				SeamArchitect.grounds(plan, rnd, s, 6, SeamArchitect.ARCH, SeamArchitect.TREE, SeamArchitect.LAMP, SeamArchitect.STONES, SeamArchitect.CLUSTER);
			}
			case LOOM -> {
				SeamArchitect.Plan b = new SeamArchitect.Plan();
				SeamArchitect.loom(b, rnd, x, top, z, r);
				age(b, rnd, top, Ruin.RUINED);
				standOn(plan, b, ground, top);
				SeamArchitect.streets(plan, rnd, s, mask, 0, false);
				SeamArchitect.grounds(plan, rnd, s, 5, SeamArchitect.ARCH, SeamArchitect.TREE, SeamArchitect.LAMP, SeamArchitect.STONES);
			}
			case VILLAGE -> {
				SeamArchitect.Plan b = new SeamArchitect.Plan();
				// laid out for the room the island has (its houses stay clear of the coast where it comes in nearest)
				SeamArchitect.village(b, rnd, x, top, z, Math.min(r, (int) Math.round(s.nearest()) + 3));
				// the Thinned live in it: worn, broken here and there, never a ruin
				age(b, rnd, top, Ruin.DAMAGED);
				standOn(plan, b, ground, top);
				SeamArchitect.streets(plan, rnd, s, mask, r >= 24 ? 9 : 7, false);
				SeamArchitect.grounds(plan, rnd, s, 3, SeamArchitect.TREE, SeamArchitect.LAMP);
				plan.marks.add("v@" + x + "," + top + "," + z);
			}
			case HANGING -> {
				SeamArchitect.hangingTower(plan, rnd, x, top - depth, z);
				SeamArchitect.streets(plan, rnd, s, mask, 0, true);
				SeamArchitect.plain(plan, rnd, s);
			}
			case HALO -> {
				SeamArchitect.Plan b = new SeamArchitect.Plan();
				SeamArchitect.halo(b, rnd, x, top, z, r);
				standOn(plan, b, ground, top);
				SeamArchitect.streets(plan, rnd, s, mask, 6, true);
				SeamArchitect.grounds(plan, rnd, s, 3 + rnd.nextInt(2), SeamArchitect.CLUSTER, SeamArchitect.CLUSTER, SeamArchitect.LAMP);
			}
			default -> {
				SeamArchitect.streets(plan, rnd, s, mask, 0, true);
				SeamArchitect.plain(plan, rnd, s);
			}
		}
	}

	/**
	 * What time has done to a structure designed on its own (a condition drawn as for a colossal building, no worse than
	 * {@code worst}), from a stream of its own drawn from {@code rnd}.
	 */
	private static void age(SeamArchitect.Plan structure, Random rnd, int top, int worst) {
		Random age = new Random(rnd.nextLong());
		Ruin.apply(structure, age, Math.min(worst, Ruin.condition(age, false)), top);
	}

	/** Sets a structure designed on its own down on its island: the ground fitted to it, then the structure merged on. */
	private static void standOn(SeamArchitect.Plan plan, SeamArchitect.Plan structure, Terrain ground, int top) {
		ground.settle(plan, structure, top);
		plan.merge(structure);
	}

	/**
	 * The island radius a colossal building is laid out for: between the island's radius and where its coast comes in
	 * nearest, so it fills the island without hanging far out over a bay (what overhangs stands on a ledge grown for it).
	 */
	static int colossusRadius(SeamArchitect.Shape s) {
		return Math.min(s.r(), (int) Math.round((s.nearest() + s.r()) / 2) + 1);
	}

	/**
	 * A colossal building on its island at a spot, designed as the Seam designs its great islands (no bridges), to a
	 * given plan (-1: any): the plan, for showing one off, and what was built.
	 */
	public static SeamArchitect.Plan colossus(long seed, int x, int top, int z, int r, int layout, Colossus.Summary[] built) {
		return colossus(seed, x, top, z, r, layout, -1, built);
	}

	/** As above, in a given scheme of materials (-1: any; see {@link Colossus#RUIN} and the rest). */
	public static SeamArchitect.Plan colossus(long seed, int x, int top, int z, int r, int layout, int scheme, Colossus.Summary[] built) {
		Random rnd = new Random(seed);
		SeamArchitect.Shape shape = SeamArchitect.Shape.of(rnd, x, top, z, r);
		SeamArchitect.Plan plan = new SeamArchitect.Plan();
		Terrain ground = Terrain.of(rnd, shape, 0);
		ground.emit(plan);
		SeamArchitect.Plan b = new SeamArchitect.Plan();
		Colossus.Summary summary = Colossus.build(b, rnd, x, top, z, colossusRadius(shape), layout, scheme);
		standOn(plan, b, ground, top);
		SeamArchitect.grounds(plan, rnd, shape, 6, SeamArchitect.ARCH, SeamArchitect.TREE, SeamArchitect.LAMP, SeamArchitect.STONES, SeamArchitect.CLUSTER);
		if (built != null && built.length > 0) {
			built[0] = summary;
		}
		return plan;
	}

	/** The kinds of island {@link #showpiece} designs, by name. */
	public static final String[] SHOWPIECES = {"plain", "loom", "village", "spire", "hanging", "halo"};

	/**
	 * An island of a kind ({@link #SHOWPIECES}, as the Seam designs it, with no bridges) at a spot, for showing off and
	 * for tests: its ground, what stands on it and its dressing.
	 */
	public static SeamArchitect.Plan showpiece(long seed, int x, int top, int z, int kind) {
		Random rnd = new Random(seed);
		int r = switch (kind) { case SPIRE -> 36; case VILLAGE -> 28; case LOOM -> 24; case HANGING -> 16; case HALO -> 18; default -> 18; };
		SeamArchitect.Shape shape = SeamArchitect.Shape.of(rnd, x, top, z, r);
		SeamArchitect.Plan plan = new SeamArchitect.Plan();
		Terrain ground = Terrain.of(rnd, shape, 0);
		int depth = ground.emit(plan);
		build(plan, rnd, kind, shape, depth, 0, ground);
		return plan;
	}

	/**
	 * Operator showcase: designs one of every kind of structure (three colossal buildings, to see how they differ) in a
	 * row of cells far from everything (x = 40..47 cells), at different heights and joined by bridges, so they can be
	 * visited with a teleport.
	 */
	public static int showcase(MinecraftServer s) {
		return s.getLevel(SEAM) == null ? 0 : designShowcase();
	}

	/** Designs the showcase row and queues its blocks (see {@link #showcase}); returns how many structures. */
	public static int designShowcase() {
		int[] kinds = {SPIRE, SPIRE, SPIRE, LOOM, VILLAGE, HANGING, HALO, PLAIN};
		int n = kinds.length;
		Random[] rnds = new Random[n];
		SeamArchitect.Shape[] shapes = new SeamArchitect.Shape[n];
		for (int i = 0; i < n; i++) {
			int cx = 40 + i, cz = 40;
			rnds[i] = new Random(hash(cx, cz, 99));
			int x = cx * CELL + CELL / 2, z = cz * CELL + CELL / 2, top = 90 + (i % 3) * 6 - 6;
			int r = switch (kinds[i]) { case SPIRE -> 36; case VILLAGE -> 28; case LOOM -> 24; case HANGING -> 16; case HALO -> 18; default -> 16; };
			shapes[i] = SeamArchitect.Shape.of(rnds[i], x, top, z, r);
		}
		for (int i = 0; i < n; i++) {
			DataStore.seamCells().add("c@" + (40 + i) + ",40");
			boolean east = i < n - 1 && SeamArchitect.canBridge(shapes[i], shapes[i + 1], true);
			boolean west = i > 0 && SeamArchitect.canBridge(shapes[i - 1], shapes[i], true);
			SeamArchitect.Plan plan = new SeamArchitect.Plan();
			plan.cell = "c@" + (40 + i) + ",40";
			int mask = (east ? 1 : 0) | (west ? 2 : 0);
			Terrain ground = Terrain.of(rnds[i], shapes[i], mask);
			int depth = ground.emit(plan);
			build(plan, rnds[i], kinds[i], shapes[i], depth, mask, ground);
			if (east) {
				SeamArchitect.bridge(plan, rnds[i], shapes[i], shapes[i + 1], true);
			}
			commit(plan);
		}
		ExtremeDimensions.LOGGER.info("Seam showcase: {} structures, {} blocks queued at x={}..{} z={}", n, SeamArchitect.pending(),
				40 * CELL, (40 + n) * CELL, 40 * CELL + CELL / 2);
		return n;
	}

	// ------------------------------------------------------------------ light falls, the heart beam

	private static List<int[]> falls;

	private static List<int[]> falls() {
		if (falls == null) {
			falls = new ArrayList<>();
			for (String key : DataStore.seamCells()) {
				if (key.startsWith("f@")) {
					String[] v = key.substring(2).split(",");
					falls.add(new int[] {Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]), Integer.parseInt(v[3])});
				}
			}
		}
		return falls;
	}

	public static void addFalls(List<int[]> list) {
		for (int[] f : list) {
			DataStore.seamCells().add("f@" + f[0] + "," + f[1] + "," + f[2] + "," + f[3]);
			falls().add(f);
		}
	}

	/** Light pouring off island edges, and the beam over the Heart, seen from far away. */
	private static void lights(ServerLevel level, int tick) {
		List<ServerPlayer> players = level.players();
		if (players.isEmpty()) {
			return;
		}
		if (tick % 8 == 0) {
			for (int[] f : falls()) {
				for (ServerPlayer p : players) {
					double dx = p.getX() - f[0], dz = p.getZ() - f[2];
					if (dx * dx + dz * dz < 110 * 110) {
						double half = f[3] / 2.0;
						WorldFx.particleFar(SEAM_ID, "minecraft:end_rod", f[0] + 0.5, f[1] - half, f[2] + 0.5, 0.2, half * 0.6, 0.2, 0.0, 5, 130);
						break;
					}
				}
			}
		}
		if (DataStore.heartBuilt() && tick % 4 == 0) {
			for (ServerPlayer p : players) {
				if (p.distanceToSqr(MendingService.HX, MendingService.HY, MendingService.HZ) < 200 * 200) {
					double hx = MendingService.HX + 0.5, hy = MendingService.HY + 30, hz = MendingService.HZ + 0.5;
					WorldFx.particleFar(SEAM_ID, "minecraft:end_rod", hx, hy, hz, 0.3, 18, 0.3, 0.0, 14, 220);
					WorldFx.particleFar(SEAM_ID, "minecraft:reverse_portal", hx, hy, hz, 1.2, 18, 1.2, 0.05, 18, 220);
					break;
				}
			}
		}
	}

	/**
	 * Barrels of the Thinned: silk, pages, fragments and old things; richer the harder they are to reach (1 a loom, 2 a
	 * village, 3 a colossus's reliquary, 4 a tangle in the Fray). What is in them comes from the loot tables.
	 */
	public static void fillLoot(ServerLevel level, BlockPos b, int richness) {
		if (!(level.getBlockEntity(b) instanceof Container c)) {
			return;
		}
		Loot.fill(level, b, c, richness >= 4 ? Loot.FRAY_TANGLE : richness == 3 ? Loot.SEAM_RELIQUARY : richness == 2 ? Loot.SEAM_VILLAGE : Loot.SEAM_LOOM);
		if (ThreadLocalRandom.current().nextDouble() < Config.RELIC_SEAM_CHEST_CHANCE * (1 + richness)) {
			// Sometimes a relic lies among the Weavers' leavings
			for (int slot = 0; slot < c.getContainerSize(); slot++) {
				if (c.getItem(slot).isEmpty()) {
					c.setItem(slot, com.extremedimensions.relic.RelicService.random());
					break;
				}
			}
		}
		c.setChanged();
		// a fresh barrel: the plundering trial counts it once, whoever opens it first
		com.extremedimensions.trial.TrialService.stocked(b);
	}

	/** Operator command: straight into the Seam, as if through a rift at the player's feet. */
	public static boolean summon(ServerPlayer p) {
		if (inSeam(p)) {
			return false;
		}
		Rift r = new Rift();
		r.level = (ServerLevel) p.level();
		r.x = p.getX();
		r.y = p.getY();
		r.z = p.getZ();
		enter(p, r);
		return true;
	}

	/** Rift distance for the HUD. */
	public static int riftDistance(ServerPlayer p, PlayerData d) {
		double dx = p.getX() - d.riftX, dz = p.getZ() - d.riftZ;
		return (int) Math.sqrt(dx * dx + dz * dz);
	}
}
