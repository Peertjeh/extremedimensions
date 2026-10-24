package net.battlehub.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Toppers: small 3D models over a player's head in the lobby, built from block and item displays. A model is a
 * list of parts (a block or an item, where it sits, its size, which way it turns) with an animation, and maybe a
 * particle that falls from it. Places are blocks from the top of the head: x to the right, y up, z forwards; the
 * whole model turns with the body. {@link #frame} gives where every part is at a tick.
 */
public final class Toppers {
	private Toppers() {}

	public enum Kind { BLOCK, ITEM }

	/** NONE stands still, SPIN turns round the head, BOB goes up and down, ORBIT does both. */
	public enum Anim { NONE, SPIN, BOB, ORBIT }

	/** A part: a block's middle or an item's middle at (x, y, z), sized (sx, sy, sz), turned {@code yaw} degrees. */
	public record Part(Kind kind, String id, double x, double y, double z, float sx, float sy, float sz, float yaw, Anim anim, double speed) {}

	/** A model: its parts and a particle under it ("" none), {@code particleY} blocks over the head. */
	public record Model(List<Part> parts, String particle, double particleY) {}

	/** A part at a tick: its place from the top of the head in the world's directions, and its turn in the world. */
	public record Placed(Kind kind, String id, double x, double y, double z, float sx, float sy, float sz, float yaw) {}

	public static final Map<String, Model> MODELS = new LinkedHashMap<>();

	/** How far a part may reach from the middle of the head (blocks): wider and it gets in the way. */
	public static final double REACH = 0.8, LOW = -0.3, HIGH = 1.3;

	static {
		// a gold crown: a band of gold with points, a gem on each side
		List<Part> crown = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			double a = Math.toRadians(i * 45);
			crown.add(block("minecraft:gold_block", Math.sin(a) * 0.26, 0.04, Math.cos(a) * 0.26, 0.16f, 0.1f, 0.16f, i * 45, Anim.NONE, 0));
			if (i % 2 == 0) crown.add(block("minecraft:gold_block", Math.sin(a) * 0.26, 0.15, Math.cos(a) * 0.26, 0.07f, 0.14f, 0.07f, i * 45, Anim.NONE, 0));
		}
		String[] gems = {"minecraft:diamond_block", "minecraft:emerald_block", "minecraft:redstone_block", "minecraft:lapis_block"};
		for (int i = 0; i < 4; i++) {
			double a = Math.toRadians(i * 90);
			crown.add(block(gems[i], Math.sin(a) * 0.31, 0.05, Math.cos(a) * 0.31, 0.06f, 0.06f, 0.06f, i * 90, Anim.NONE, 0));
		}
		add("crown", crown, "", 0);

		// a halo: a turning ring of light over the head
		List<Part> halo = new ArrayList<>();
		for (int i = 0; i < 14; i++) {
			double a = Math.toRadians(i * 360.0 / 14);
			halo.add(block("minecraft:glowstone", Math.sin(a) * 0.3, 0.38, Math.cos(a) * 0.3, 0.08f, 0.04f, 0.08f, (float) (i * 360.0 / 14), Anim.SPIN, 2.0));
		}
		add("halo", halo, "minecraft:end_rod", 0.38);

		// devil horns: two curved horns, three pieces each
		List<Part> horns = new ArrayList<>();
		for (int side = -1; side <= 1; side += 2) {
			horns.add(block("minecraft:red_nether_bricks", side * 0.2, 0.02, 0.08, 0.1f, 0.1f, 0.1f, 0, Anim.NONE, 0));
			horns.add(block("minecraft:red_nether_bricks", side * 0.26, 0.11, 0.06, 0.08f, 0.1f, 0.08f, 0, Anim.NONE, 0));
			horns.add(block("minecraft:nether_wart_block", side * 0.29, 0.2, 0.02, 0.05f, 0.08f, 0.05f, 0, Anim.NONE, 0));
		}
		add("horns", horns, "", 0);

		// a rain cloud with drops falling from it
		List<Part> cloud = new ArrayList<>();
		double[][] puffs = {{0, 0.75, 0, 0.42}, {0.22, 0.72, 0.05, 0.3}, {-0.22, 0.72, -0.04, 0.32}, {0.05, 0.85, 0.12, 0.28}, {-0.08, 0.8, -0.15, 0.26}};
		for (int i = 0; i < puffs.length; i++) {
			double[] q = puffs[i];
			cloud.add(block(i == 0 ? "minecraft:light_gray_wool" : "minecraft:white_wool", q[0], q[1], q[2], (float) q[3], (float) (q[3] * 0.6), (float) q[3], 0, Anim.BOB, 0.15));
		}
		add("raincloud", cloud, "minecraft:dripping_water", 0.6);

		// a propeller cap: a red cap with a turning yellow propeller on a stick
		List<Part> prop = new ArrayList<>();
		prop.add(block("minecraft:red_wool", 0, 0.03, 0, 0.5f, 0.1f, 0.5f, 0, Anim.NONE, 0));
		prop.add(block("minecraft:red_wool", 0, 0.01, 0.3, 0.42f, 0.04f, 0.16f, 0, Anim.NONE, 0));
		prop.add(block("minecraft:iron_block", 0, 0.14, 0, 0.04f, 0.14f, 0.04f, 0, Anim.NONE, 0));
		prop.add(block("minecraft:yellow_concrete", 0, 0.22, 0, 0.6f, 0.02f, 0.08f, 0, Anim.SPIN, 25.0));
		prop.add(block("minecraft:yellow_concrete", 0, 0.22, 0, 0.08f, 0.02f, 0.6f, 0, Anim.SPIN, 25.0));
		add("propeller", prop, "", 0);

		// three gems circling the head
		List<Part> orbit = new ArrayList<>();
		String[] stones = {"minecraft:diamond", "minecraft:emerald", "minecraft:amethyst_shard"};
		for (int i = 0; i < 3; i++) {
			double a = Math.toRadians(i * 120);
			orbit.add(item(stones[i], Math.sin(a) * 0.55, 0.1, Math.cos(a) * 0.55, 0.35f, i * 120, Anim.ORBIT, 4.0));
		}
		add("gems", orbit, "minecraft:enchant", 0.1);

		// a pointed wizard's hat with a gold band
		List<Part> wizard = new ArrayList<>();
		wizard.add(block("minecraft:purple_wool", 0, 0.02, 0, 0.7f, 0.05f, 0.7f, 0, Anim.NONE, 0));
		wizard.add(block("minecraft:gold_block", 0, 0.08, 0, 0.47f, 0.05f, 0.47f, 0, Anim.NONE, 0));
		wizard.add(block("minecraft:purple_wool", 0, 0.17, 0, 0.44f, 0.14f, 0.44f, 0, Anim.NONE, 0));
		wizard.add(block("minecraft:purple_wool", 0, 0.32, -0.02, 0.32f, 0.16f, 0.32f, 0, Anim.NONE, 0));
		wizard.add(block("minecraft:purple_wool", 0, 0.47, -0.05, 0.2f, 0.16f, 0.2f, 0, Anim.NONE, 0));
		wizard.add(block("minecraft:purple_wool", 0, 0.6, -0.1, 0.1f, 0.14f, 0.1f, 0, Anim.NONE, 0));
		add("wizard", wizard, "minecraft:witch", 0.7);

		// a crown of flowers
		List<Part> flowers = new ArrayList<>();
		String[] blooms = {"minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:allium", "minecraft:oxeye_daisy", "minecraft:blue_orchid"};
		for (int i = 0; i < 10; i++) {
			double a = Math.toRadians(i * 36);
			flowers.add(item(blooms[i % blooms.length], Math.sin(a) * 0.28, 0.06, Math.cos(a) * 0.28, 0.32f, i * 36 + 90, Anim.NONE, 0));
		}
		add("flowers", flowers, "", 0);

		// a ring of fire turning over the head
		List<Part> fire = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			double a = Math.toRadians(i * 45);
			fire.add(block("minecraft:magma_block", Math.sin(a) * 0.36, 0.3, Math.cos(a) * 0.36, 0.1f, 0.1f, 0.1f, i * 45, Anim.SPIN, 6.0));
		}
		add("firering", fire, "minecraft:flame", 0.32);

		// an ice crystal floating and turning
		List<Part> ice = new ArrayList<>();
		ice.add(block("minecraft:blue_ice", 0, 0.62, 0, 0.26f, 0.26f, 0.26f, 45, Anim.ORBIT, 3.0));
		ice.add(block("minecraft:packed_ice", 0, 0.82, 0, 0.12f, 0.14f, 0.12f, 45, Anim.ORBIT, 3.0));
		ice.add(block("minecraft:packed_ice", 0, 0.44, 0, 0.12f, 0.12f, 0.12f, 45, Anim.ORBIT, 3.0));
		add("ice", ice, "minecraft:snowflake", 0.5);

		// a birthday cake with candles
		List<Part> cake = new ArrayList<>();
		cake.add(block("minecraft:cake", 0, 0.12, 0, 0.5f, 0.5f, 0.5f, 0, Anim.NONE, 0));
		for (int i = 0; i < 4; i++) {
			double a = Math.toRadians(i * 90 + 45);
			cake.add(item("minecraft:candle", Math.sin(a) * 0.12, 0.32, Math.cos(a) * 0.12, 0.25f, 0, Anim.NONE, 0));
		}
		add("cake", cake, "minecraft:small_flame", 0.42);

		// a pirate's hat with a skull on the front
		List<Part> pirate = new ArrayList<>();
		pirate.add(block("minecraft:black_wool", 0, 0.03, 0, 0.76f, 0.06f, 0.5f, 0, Anim.NONE, 0));
		pirate.add(block("minecraft:black_wool", 0, 0.14, 0, 0.56f, 0.18f, 0.34f, 0, Anim.NONE, 0));
		pirate.add(block("minecraft:gold_block", 0, 0.07, 0.02, 0.58f, 0.03f, 0.36f, 0, Anim.NONE, 0));
		pirate.add(item("minecraft:skeleton_skull", 0, 0.16, 0.19, 0.22f, 0, Anim.NONE, 0));
		add("pirate", pirate, "", 0);

		// a black top hat with a red band
		List<Part> top = new ArrayList<>();
		top.add(block("minecraft:black_wool", 0, 0.02, 0, 0.66f, 0.04f, 0.66f, 0, Anim.NONE, 0));
		top.add(block("minecraft:black_wool", 0, 0.24, 0, 0.44f, 0.4f, 0.44f, 0, Anim.NONE, 0));
		top.add(block("minecraft:red_wool", 0, 0.1, 0, 0.45f, 0.08f, 0.45f, 0, Anim.NONE, 0));
		add("tophat", top, "", 0);

		// a tall white chef's hat
		List<Part> chef = new ArrayList<>();
		chef.add(block("minecraft:white_wool", 0, 0.12, 0, 0.48f, 0.24f, 0.48f, 0, Anim.NONE, 0));
		chef.add(block("minecraft:white_wool", 0, 0.34, 0, 0.6f, 0.22f, 0.6f, 0, Anim.NONE, 0));
		chef.add(block("minecraft:white_wool", 0.12, 0.46, 0.08, 0.3f, 0.12f, 0.3f, 0, Anim.NONE, 0));
		chef.add(block("minecraft:white_wool", -0.1, 0.46, -0.1, 0.28f, 0.12f, 0.28f, 0, Anim.NONE, 0));
		add("chef", chef, "", 0);
	}

	private static Part block(String id, double x, double y, double z, float sx, float sy, float sz, float yaw, Anim anim, double speed) {
		return new Part(Kind.BLOCK, id, x, y, z, sx, sy, sz, yaw, anim, speed);
	}

	private static Part item(String id, double x, double y, double z, float scale, float yaw, Anim anim, double speed) {
		return new Part(Kind.ITEM, id, x, y, z, scale, scale, scale, yaw, anim, speed);
	}

	private static void add(String id, List<Part> parts, String particle, double particleY) {
		MODELS.put(id, new Model(List.copyOf(parts), particle, particleY));
	}

	public static boolean known(String id) {
		return MODELS.containsKey(id);
	}

	/**
	 * Every part of {@code model} at {@code tick}, for a body turned {@code bodyYaw} (Minecraft degrees: 0 is south,
	 * 90 west). Empty for an unknown model.
	 */
	public static List<Placed> frame(String model, long tick, float bodyYaw) {
		Model m = MODELS.get(model);
		if (m == null) return List.of();
		double yaw = Math.toRadians(bodyYaw);
		// the body's right and forward in the world (x east, z south)
		double rx = -Math.cos(yaw), rz = -Math.sin(yaw), fx = -Math.sin(yaw), fz = Math.cos(yaw);
		List<Placed> out = new ArrayList<>(m.parts().size());
		for (Part p : m.parts()) {
			double x = p.x(), y = p.y(), z = p.z();
			float turn = p.yaw();
			if (p.anim() == Anim.SPIN || p.anim() == Anim.ORBIT) {
				double a = Math.toRadians(tick * p.speed());
				double c = Math.cos(a), s = Math.sin(a);
				double nx = x * c - z * s, nz = x * s + z * c;
				x = nx;
				z = nz;
				turn -= (float) (tick * p.speed());
			}
			if (p.anim() == Anim.BOB || p.anim() == Anim.ORBIT) {
				y += 0.05 * Math.sin(tick * (p.anim() == Anim.BOB ? p.speed() : 0.12));
			}
			out.add(new Placed(p.kind(), p.id(), rx * x + fx * z, y, rz * x + fz * z, p.sx(), p.sy(), p.sz(), bodyYaw + turn));
		}
		return out;
	}
}
