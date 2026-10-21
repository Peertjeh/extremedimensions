package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.extremedimensions.config.Config;
import com.extremedimensions.mixin.DisplayAccess;
import com.extremedimensions.mixin.InteractionAccess;
import com.extremedimensions.server.BlockFlight;
import com.extremedimensions.server.WorldFx;
import com.mojang.math.Transformation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Ferryman's Gaol of Chains: a closed cage of iron bars round each passenger, with chains for a roof. It is made of
 * displays, not of the world's blocks, so it rises whatever the rules on griefing; no one walks, jumps or pearls out of
 * it. Struck often enough, a bar breaks, and through that gap one may get out before the cage crushes inwards on
 * whoever is still inside.
 */
public final class ElderGaol {
	private ElderGaol() {}

	/** Bars round a cage. */
	static final int BARS = 16;
	/** How far the bars stand from the middle, and how high the cage is. */
	static final double RADIUS = 3.2, HEIGHT = 4.5;
	private static final String TAG = "extremedimensions_gaol";

	static final class Cage {
		final UUID owner;
		final ServerLevel level;
		final String dim;
		final double x, y, z;
		final Display.BlockDisplay[] bars = new Display.BlockDisplay[BARS];
		final Interaction[] posts = new Interaction[BARS];
		final int[] hits = new int[BARS];
		final boolean[] broken = new boolean[BARS];
		/** Who it shut in (by the entity they were then: one who died and came back is not held), less those who got out. */
		final Map<UUID, Integer> inside = new HashMap<>();
		int age, crushedAt;
		boolean crushed;

		Cage(UUID owner, ServerLevel level, double x, double y, double z) {
			this.owner = owner;
			this.level = level;
			this.dim = level.dimension().identifier().toString();
			this.x = x;
			this.y = y;
			this.z = z;
		}

		double angle(int i) {
			return 2 * Math.PI * i / BARS;
		}
	}

	private static final List<Cage> CAGES = new ArrayList<>();
	/** Each post (by its entity) and the bar it stands for. */
	private static final Map<UUID, Cage> POSTS = new HashMap<>();
	private static EntityType<?> interaction;

	/**
	 * A cage rises round {@code q}: closed bars, and everyone within them shut in. It stands on the ground under them
	 * (caught mid-jump, it rises from where they will land); only with no ground close under them does it hang in the air.
	 */
	static Cage raise(ElderBoss owner, ServerPlayer q) {
		ServerLevel level = (ServerLevel) owner.level();
		double ground = ElderFx.footing(q, 4);
		Cage c = new Cage(owner.getUUID(), level, q.getX(), Double.isNaN(ground) ? q.getY() : Math.min(ground, q.getY()), q.getZ());
		BlockState bar = Blocks.IRON_BARS.defaultBlockState().setValue(CrossCollisionBlock.EAST, true).setValue(CrossCollisionBlock.WEST, true);
		// Every bar lit as the middle of the cage is: on uneven ground a bar's foot sits in a block and would go black
		BlockPos mid = BlockPos.containing(c.x, c.y + 1, c.z);
		Brightness light = new Brightness(level.getBrightness(LightLayer.BLOCK, mid), level.getBrightness(LightLayer.SKY, mid));
		for (int i = 0; i < BARS; i++) {
			double a = c.angle(i);
			double px = c.x + Math.cos(a) * RADIUS, pz = c.z + Math.sin(a) * RADIUS;
			c.bars[i] = BlockFlight.spawn(level, px, c.y, pz, bar, look(a, 0));
			if (c.bars[i] != null) {
				((DisplayAccess) c.bars[i]).extremedimensions$setBrightnessOverride(light);
			}
			Interaction post = new Interaction(interactionType(), level);
			post.setPos(px, c.y, pz);
			((InteractionAccess) post).extremedimensions$setWidth(1.1F);
			((InteractionAccess) post).extremedimensions$setHeight((float) HEIGHT);
			post.addTag(TAG);
			// Known before it is added: as it loads, a tagged post nothing knows of is taken for one left from a crash
			POSTS.put(post.getUUID(), c);
			if (level.addFreshEntity(post)) {
				c.posts[i] = post;
			} else {
				POSTS.remove(post.getUUID());
			}
		}
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && !p.isCreative() && within(c, p, RADIUS + 0.4) && p.getY() > c.y - 2 && p.getY() < c.y + HEIGHT) {
				c.inside.put(p.getUUID(), p.getId());
			}
		}
		CAGES.add(c);
		WorldFx.soundIn(c.dim, "minecraft:block.chain.place", "hostile", c.x, c.y, c.z, 3f, 0.5f);
		WorldFx.soundIn(c.dim, "minecraft:block.iron_door.close", "hostile", c.x, c.y, c.z, 3f, 0.5f);
		return c;
	}

	/**
	 * A bar standing tangent to the cage at angle {@code a}: iron bars run along it, tall as the cage, wide enough to
	 * meet the next, pulled {@code in} blocks towards the middle (as it crushes).
	 */
	private static Transformation look(double a, double in) {
		float width = (float) (2 * Math.PI * RADIUS / BARS) + 0.15F;
		Quaternionf turn = new Quaternionf().rotateY((float) -(a + Math.PI / 2));
		Vector3f scale = new Vector3f(width, (float) HEIGHT, 1F);
		// the block's own middle at the foot of the bar, moved inwards along the radius
		Vector3f centre = new Vector3f(-0.5F * width, 0, -0.5F);
		turn.transform(centre);
		centre.add((float) (-Math.cos(a) * in), 0, (float) (-Math.sin(a) * in));
		return new Transformation(centre, turn, scale, new Quaternionf());
	}

	private static boolean within(Cage c, Entity e, double r) {
		double dx = e.getX() - c.x, dz = e.getZ() - c.z;
		return dx * dx + dz * dz <= r * r;
	}

	/** Whether a player standing at angle {@code a} from the middle is at a broken bar (the gap a bar leaves). */
	private static boolean gap(Cage c, double a) {
		double step = 2 * Math.PI / BARS;
		for (int i = 0; i < BARS; i++) {
			if (c.broken[i]) {
				double d = Math.abs(Math.atan2(Math.sin(a - c.angle(i)), Math.cos(a - c.angle(i))));
				if (d < step * 0.6) {
					return true;
				}
			}
		}
		return false;
	}

	/** A player struck one of the bars: enough blows break it. True when it was a bar of a cage. */
	public static boolean struck(ServerPlayer p, Entity post) {
		Cage c = POSTS.get(post.getUUID());
		if (c == null || c.crushed) {
			return false;
		}
		for (int i = 0; i < BARS; i++) {
			if (c.posts[i] != null && c.posts[i].getUUID().equals(post.getUUID())) {
				c.hits[i]++;
				double px = post.getX(), py = post.getY() + 1.5, pz = post.getZ();
				WorldFx.particleIn(c.level, "minecraft:crit", px, py, pz, 0.3, 0.6, 0.3, 0.1, 6);
				if (c.hits[i] >= Math.max(1, Config.FERRYMAN_GAOL_HITS)) {
					breakBar(c, i);
				} else {
					WorldFx.soundIn(c.dim, "minecraft:block.chain.hit", "hostile", px, py, pz, 1.5f, 0.6f + 0.15f * c.hits[i]);
				}
				return true;
			}
		}
		return false;
	}

	private static void breakBar(Cage c, int i) {
		c.broken[i] = true;
		Interaction post = c.posts[i];
		double px = post.getX(), py = post.getY() + 1.5, pz = post.getZ();
		POSTS.remove(post.getUUID());
		post.discard();
		c.posts[i] = null;
		if (c.bars[i] != null) {
			BlockFlight.drop(c.bars[i]);
			c.bars[i] = null;
		}
		WorldFx.soundIn(c.dim, "minecraft:block.chain.break", "hostile", px, py, pz, 2.5f, 0.5f);
		WorldFx.soundIn(c.dim, "minecraft:entity.zombie.break_wooden_door", "hostile", px, py, pz, 1.5f, 1.4f);
		WorldFx.particleIn(c.level, "minecraft:soul_fire_flame", px, py, pz, 0.3, 1.2, 0.3, 0.05, 20);
	}

	/** The cage crushes inwards: whoever is still inside takes the blow. */
	static void crush(ElderBoss owner, Cage c) {
		c.crushed = true;
		c.crushedAt = c.age;
		for (int i = 0; i < BARS; i++) {
			if (c.bars[i] != null) {
				DisplayAccess d = (DisplayAccess) c.bars[i];
				d.extremedimensions$setInterpolationDelay(0);
				d.extremedimensions$setInterpolationDuration(4);
				d.extremedimensions$setTransformation(look(c.angle(i), RADIUS - 1.2));
			}
		}
		float damage = (float) Math.max(0, Config.FERRYMAN_GAOL_DAMAGE);
		for (ServerPlayer p : c.level.players()) {
			if (c.inside.containsKey(p.getUUID()) && within(c, p, RADIUS + 0.6) && p.getY() > c.y - 2 && p.getY() < c.y + HEIGHT && damage > 0) {
				p.hurtServer(c.level, c.level.damageSources().mobAttack(owner), damage);
			}
		}
		AttackFx.impact(c.level, 1, ElderStrikes.SOULFALL, c.x, c.y, c.z, RADIUS + 0.4);
		WorldFx.soundIn(c.dim, "minecraft:entity.iron_golem.damage", "hostile", c.x, c.y, c.z, 3f, 0.4f);
	}

	/** Every tick: no one shut in leaves but through a gap; old cages come down. */
	public static void tick(MinecraftServer s) {
		for (Cage c : List.copyOf(CAGES)) {
			c.age++;
			// a crushed cage stays a moment as the bars meet; one its Elder never crushed goes in the end all the same
			if (c.crushed ? c.age - c.crushedAt > 14 : c.age > 400) {
				take(c);
				continue;
			}
			if (c.crushed) {
				continue;
			}
			for (ServerPlayer p : c.level.players()) {
				Integer held = c.inside.get(p.getUUID());
				if (held == null) {
					continue;
				}
				if (held != p.getId() || !p.isAlive() || p.isSpectator()) {
					// Died (and perhaps came back), or turned spectator: the gaol no longer holds them
					c.inside.remove(p.getUUID());
					continue;
				}
				double dx = p.getX() - c.x, dz = p.getZ() - c.z;
				double d = Math.sqrt(dx * dx + dz * dz);
				double a = Math.atan2(dz, dx);
				boolean free = gap(c, a);
				if (d > RADIUS + 0.6 && free) {
					// out through a broken bar
					c.inside.remove(p.getUUID());
					continue;
				}
				double limit = free ? RADIUS + 1.5 : RADIUS - 0.55;
				double top = c.y + HEIGHT - 1.9;
				// Nor down out of it: no one digs or drops out under the bars
				if (d > limit || p.getY() > top || p.getY() < c.y - 1.5) {
					double r = Math.min(d, RADIUS - 0.7);
					double nx = c.x + Math.cos(a) * r, nz = c.z + Math.sin(a) * r;
					double ny = Math.max(c.y, Math.min(p.getY(), top));
					p.teleportTo(nx, ny, nz);
					p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
					p.needsSync = true;
				}
			}
		}
	}

	private static void take(Cage c) {
		CAGES.remove(c);
		for (int i = 0; i < BARS; i++) {
			if (c.bars[i] != null) {
				BlockFlight.drop(c.bars[i]);
			}
			if (c.posts[i] != null) {
				POSTS.remove(c.posts[i].getUUID());
				c.posts[i].discard();
			}
		}
	}

	/** An Elder's cages come down (it left, died). */
	static void forget(UUID owner) {
		for (Cage c : List.copyOf(CAGES)) {
			if (c.owner.equals(owner)) {
				take(c);
			}
		}
	}

	/** Whether a player is shut in a cage now (for the tests). */
	public static boolean shutIn(ServerPlayer p) {
		for (Cage c : CAGES) {
			if (!c.crushed && c.inside.containsKey(p.getUUID())) {
				return true;
			}
		}
		return false;
	}

	/** The posts of the cage a player is shut in (for the tests): what one strikes to break a bar. */
	public static List<Entity> posts(ServerPlayer p) {
		List<Entity> out = new ArrayList<>();
		for (Cage c : CAGES) {
			if (c.inside.containsKey(p.getUUID())) {
				for (Interaction post : c.posts) {
					if (post != null) {
						out.add(post);
					}
				}
			}
		}
		return out;
	}

	/** A post of a cage left over from a crash is removed when it turns up. */
	/** A post of a cage that is gone (left over from a crash), found as it loads: to be removed. */
	public static boolean stray(Entity e) {
		return e instanceof Interaction && !POSTS.containsKey(e.getUUID()) && e.removeTag(TAG);
	}

	public static void clear() {
		for (Cage c : List.copyOf(CAGES)) {
			take(c);
		}
		CAGES.clear();
		POSTS.clear();
	}

	private static EntityType<?> interactionType() {
		if (interaction == null) {
			interaction = BuiltInRegistries.ENTITY_TYPE.get(Identifier.fromNamespaceAndPath("minecraft", "interaction")).map(h -> h.value()).orElseThrow();
		}
		return interaction;
	}
}
