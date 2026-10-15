package com.extremedimensions.server;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.math.Transformation;

import com.extremedimensions.mixin.BlockDisplayAccess;
import com.extremedimensions.mixin.DisplayAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks that fly: a block appears out of nothing some way off its place and flies to it, turning upright and growing
 * to its size as it comes, then drops into it and is put down for real (or, taken back, flies off from its place and
 * is gone). What it flies as is a block display, which only the client draws; the server moves nothing but the
 * display's transformation, which the client eases between. Used by the Elders' arenas and by the Seam building itself.
 *
 * <p>Every display it makes carries {@link #TAG}; one found in a chunk that no flight is using (left there by a crash)
 * is removed as it loads.
 */
public final class BlockFlight {
	private BlockFlight() {}

	/** The tag on every block display of a flight (or a strand drawn between arena towers). */
	public static final String TAG = "extremedimensions_flight";
	/** The tag arenas used before flights were shared (leftovers from older worlds are still cleaned up). */
	private static final String OLD_TAG = "extremedimensions_arena";
	/** Ticks of the final drop into place. */
	private static final int SETTLE = 4;
	/** Ticks a block hangs over someone standing in its place before it gives up. */
	private static final int PATIENCE = 40;

	/** The block displays in use now (any other display carrying the tag is a leftover). */
	private static final Set<UUID> LIVE = new HashSet<>();

	/** What a block flying in asks of whoever sent it, as it lands. */
	public interface Landing {
		/** Whether its block may still go in (false: it vanishes). */
		boolean fits();

		/** Whether someone stands where it goes (it hangs there a while, waiting for them to move). */
		boolean blocked();

		/** It lands: its block is put down for real. */
		void land();

		/** It may not go in after all (see {@link #fits}): it vanishes. */
		default void refused() {}

		/** It waited too long over someone standing in its place. */
		default void missed() {}

		/** Its display was gone before it landed (unloaded, killed). */
		default void lost() {}
	}

	/** A block on its way in or out. */
	public static final class Flight {
		public final ServerLevel level;
		public final BlockPos pos;
		public final BlockState state;
		final Display.BlockDisplay display;
		final boolean outward;
		/** Where it starts (in) or ends (out), from its place. */
		final double ox, oy, oz;
		final Quaternionf spin;
		final int length;
		/** Particle trailing it, every this many ticks (0 = none). */
		final String spark;
		final int trailEvery;
		final Landing landing;
		int age;
		int waits;
		boolean landed;

		private Flight(ServerLevel level, BlockPos pos, BlockState state, Display.BlockDisplay display, boolean outward, double[] o, Quaternionf spin,
				int length, String spark, int trailEvery, Landing landing) {
			this.level = level;
			this.pos = pos;
			this.state = state;
			this.display = display;
			this.outward = outward;
			this.ox = o[0];
			this.oy = o[1];
			this.oz = o[2];
			this.spin = spin;
			this.length = Math.max(SETTLE + 6, length);
			this.spark = spark;
			this.trailEvery = trailEvery;
			this.landing = landing;
		}

		/** Where it starts (in) or ends (out), in the world. */
		public double x() {
			return pos.getX() + 0.5 + ox;
		}

		public double y() {
			return pos.getY() + 0.5 + oy;
		}

		public double z() {
			return pos.getZ() + 0.5 + oz;
		}
	}

	/**
	 * A block appears {@code o} off its place, small and turning, and sets off towards it; it lands after
	 * {@code length} ticks. Null if no display could be made (the caller puts the block down itself, or not).
	 */
	public static Flight in(ServerLevel level, BlockPos pos, BlockState state, double[] o, int length, String spark, int trailEvery, Landing landing) {
		Quaternionf spin = spin(ThreadLocalRandom.current());
		Display.BlockDisplay d = spawn(level, pos.getX(), pos.getY() + 1, pos.getZ(), state, pose(o[0], o[1], o[2], spin, 0.15F));
		return d == null ? null : new Flight(level, pos, state, d, false, o, spin, length, spark, trailEvery, landing);
	}

	/** A block just taken from its place flies off to {@code o} from it, shrinking, and is gone. */
	public static Flight out(ServerLevel level, BlockPos pos, BlockState state, double[] o, int length, String spark, int trailEvery) {
		Display.BlockDisplay d = spawn(level, pos.getX(), pos.getY() + 1, pos.getZ(), state, pose(0, 0, 0, new Quaternionf(), 1F));
		return d == null ? null : new Flight(level, pos, state, d, true, o, spin(ThreadLocalRandom.current()), length, spark, trailEvery, null);
	}

	/** One tick of a flight; true once it is over (its display gone). */
	public static boolean step(Flight f) {
		if (f.display.isRemoved()) {
			LIVE.remove(f.display.getUUID());
			if (!f.outward && !f.landed) {
				f.landing.lost();
			}
			return true;
		}
		int length = f.length;
		f.age++;
		DisplayAccess d = (DisplayAccess) f.display;
		if (f.outward) {
			if (f.age == 2) {
				d.extremedimensions$setTransformation(pose(f.ox, f.oy, f.oz, f.spin, 0.05F));
				d.extremedimensions$setInterpolationDuration(length);
				d.extremedimensions$setInterpolationDelay(0);
			}
			if (f.trailEvery > 0 && f.age > 2 && f.age % f.trailEvery == 0) {
				trail(f, Math.min(1, (f.age - 2) / (double) length));
			}
			if (f.age >= length + 3) {
				drop(f.display);
				return true;
			}
			return false;
		}
		int glide = length - SETTLE;
		if (f.age == 2) {
			// Up to just over its place, turning upright and growing to its size as it comes
			Quaternionf tilt = new Quaternionf().rotateAxis(0.25F, randomAxis(ThreadLocalRandom.current()));
			d.extremedimensions$setTransformation(pose(0, 1.25, 0, tilt, 1F));
			d.extremedimensions$setInterpolationDuration(glide - 2);
			d.extremedimensions$setInterpolationDelay(0);
		}
		if (f.trailEvery > 0 && f.age > 2 && f.age < glide && f.age % f.trailEvery == 0) {
			trail(f, 1 - (f.age - 2) / (double) (glide - 2));
		}
		if (f.age == glide) {
			// Then down into it
			d.extremedimensions$setTransformation(pose(0, 0, 0, new Quaternionf(), 1F));
			d.extremedimensions$setInterpolationDuration(SETTLE - 1);
			d.extremedimensions$setInterpolationDelay(0);
		}
		if (f.age == length && !f.landed) {
			if (!f.landing.fits()) {
				f.landing.refused();
				drop(f.display);
				return true;
			}
			if (f.landing.blocked()) {
				if (++f.waits < PATIENCE) {
					f.age--;
					return false;
				}
				f.landing.missed();
			} else {
				f.landed = true;
				f.landing.land();
			}
		}
		if (f.age > length) {
			drop(f.display);
			return true;
		}
		return false;
	}

	/** Lands a block flying in at once (someone is about to step where it goes); its display goes. */
	public static void rush(Flight f) {
		if (!f.outward && !f.landed) {
			f.landed = true;
			if (f.landing.fits()) {
				f.landing.land();
			} else {
				f.landing.refused();
			}
		}
		drop(f.display);
	}

	/** Whether it is still flying in (not yet landed). */
	public static boolean incoming(Flight f) {
		return !f.outward && !f.landed && !f.display.isRemoved();
	}

	/** Its display goes at once, wherever it is (nothing lands). */
	public static void cancel(Flight f) {
		drop(f.display);
	}

	/** Light trailing a flying block, {@code k} of the way from its start (in) or its block (out). */
	private static void trail(Flight f, double k) {
		double x = f.pos.getX() + 0.5 + f.ox * k, z = f.pos.getZ() + 0.5 + f.oz * k;
		double y = f.pos.getY() + 0.5 + (f.outward ? f.oy * k : f.oy * k + 1.25 * (1 - k));
		WorldFx.particleIn(f.level, f.spark, x, y, z, 0.1, 0.1, 0.1, 0.0, 1);
	}

	// ------------------------------------------------------------------ displays

	/**
	 * A tagged block display at (x, y, z) showing {@code state} as {@code look} has it, tracked as live; null if the
	 * level would not take it.
	 */
	public static Display.BlockDisplay spawn(ServerLevel level, double x, double y, double z, BlockState state, Transformation look) {
		Display.BlockDisplay d = new Display.BlockDisplay(blockDisplay(), level);
		d.setPos(x, y, z);
		((BlockDisplayAccess) d).extremedimensions$setBlockState(state);
		((DisplayAccess) d).extremedimensions$setTransformation(look);
		d.addTag(TAG);
		LIVE.add(d.getUUID());
		if (!level.addFreshEntity(d)) {
			LIVE.remove(d.getUUID());
			return null;
		}
		return d;
	}

	/** A display made by {@link #spawn} goes. */
	public static void drop(Display.BlockDisplay d) {
		LIVE.remove(d.getUUID());
		d.discard();
	}

	/** Forgets a display that is gone by itself (removed, unloaded). */
	public static void forget(Display.BlockDisplay d) {
		LIVE.remove(d.getUUID());
	}

	/** A tagged block display turning up that nothing is using (left over from a crash) is removed. */
	public static void loaded(Entity e) {
		if (e instanceof Display.BlockDisplay && !LIVE.contains(e.getUUID()) && (e.removeTag(TAG) | e.removeTag(OLD_TAG))) {
			e.discard();
		}
	}

	/** On stopping, once every user of flights has taken its displays down. */
	public static void clear() {
		LIVE.clear();
	}

	/**
	 * The look of a block in flight: the block turned by {@code q} and scaled by {@code s} about its own centre, its
	 * centre moved by (ox, oy, oz) from its place. The display itself stands one block above the place (so it is lit
	 * as the air there, not as the inside of the ground it is inlaid into).
	 */
	public static Transformation pose(double ox, double oy, double oz, Quaternionf q, float s) {
		Vector3f half = new Vector3f(0.5F * s, 0.5F * s, 0.5F * s);
		new Quaternionf(q).transform(half);
		Vector3f t = new Vector3f((float) ox + 0.5F - half.x, (float) oy - 0.5F - half.y, (float) oz + 0.5F - half.z);
		return new Transformation(t, new Quaternionf(q), new Vector3f(s, s, s), new Quaternionf());
	}

	private static Quaternionf spin(ThreadLocalRandom rnd) {
		return new Quaternionf().rotateAxis((float) rnd.nextDouble(1.2, 2.8), randomAxis(rnd));
	}

	public static Vector3f randomAxis(ThreadLocalRandom rnd) {
		Vector3f v = new Vector3f((float) rnd.nextGaussian(), (float) rnd.nextGaussian(), (float) rnd.nextGaussian());
		return v.lengthSquared() < 1e-4 ? new Vector3f(0, 1, 0) : v.normalize();
	}

	/** The vanilla block display's type, looked up by id (entity type constants come and go between versions). */
	public static EntityType<?> blockDisplay() {
		if (blockDisplay == null) {
			blockDisplay = BuiltInRegistries.ENTITY_TYPE.get(Identifier.fromNamespaceAndPath("minecraft", "block_display")).map(h -> h.value()).orElseThrow();
		}
		return blockDisplay;
	}

	private static EntityType<?> blockDisplay;
}
