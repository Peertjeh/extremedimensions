package com.extremedimensions.tether;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.network.Fx;
import com.extremedimensions.network.Sync;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * The physical tether: an iron chain bound to an anchor, laid on the ground wherever the player walks.
 *
 * <p>Each segment is a {@code block_display} entity tagged per player. In the Overworld the tether is short and
 * snaps beyond {@link Config#TETHER_BREAK_RANGE}; beyond the wound it trails behind the player. It disappears
 * on a safe return (the item comes back) and on death (the item is lost).
 */
public final class TetherService {
	private TetherService() {}

	private static final String TAG = "ed_tether";

	/** Right click with a Tether on a charged anchor. */
	public static InteractionResult tie(ServerPlayer p, PlayerData d, BlockPos pos, ItemStack stack) {
		String key = Anchor.key(pos.getX(), pos.getY(), pos.getZ());
		Anchor anchor = DataStore.anchors().get(key);
		if (anchor == null || !Dims.OVERWORLD.equals(Dims.of(p))) {
			WorldFx.soundSelf(p, "minecraft:block.note_block.bass", 0.8f, 0.5f);
			return InteractionResult.SUCCESS;
		}
		if (d.inRun || key.equals(d.tetherAnchor)) {
			return InteractionResult.SUCCESS;
		}
		// Already bound elsewhere? That tether comes back.
		clear(p, d, d.tetherAnchor != null);
		stack.shrink(1);
		d.tetherAnchor = key;
		d.hasRopeEnd = true;
		d.ropeDim = "minecraft:overworld";
		d.ropeX = pos.getX() + 0.5;
		d.ropeY = pos.getY() + 1.02;
		d.ropeZ = pos.getZ() + 0.5;
		Journal.unlock(p, d, JournalEntry.CORD);
		Advancements.grant(p, Advancements.BOUND);
		WorldFx.fx(p, Fx.TIE, 0);
		WorldFx.sound(p, "minecraft:entity.leash_knot.place", 1.0f, 0.7f);
		WorldFx.sound(p, "minecraft:block.chain.place", 1.0f, 0.8f);
		WorldFx.particleAt("extremedimensions:soul_mote", pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 0.3, 0.3, 0.3, 0.0, 16);
		WorldFx.particleAt("minecraft:soul", pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0.3, 0.2, 0.3, 0.02, 12);
		Sync.state(p, d);
		return InteractionResult.SUCCESS;
	}

	/** Empty hand on your own anchor: untie and get the Tether back. */
	public static void untie(ServerPlayer p, PlayerData d) {
		clear(p, d, true);
		WorldFx.sound(p, "minecraft:entity.leash_knot.break", 1.0f, 0.8f);
		Sync.state(p, d);
	}

	/** Per-tick update: snapping in the Overworld and laying new segments. Returns true if the HUD must sync. */
	public static boolean tick(ServerPlayer p, PlayerData d, String dim) {
		if (d.tetherAnchor == null || d.collapsing) {
			return false;
		}
		boolean changed = false;
		if (Dims.OVERWORLD.equals(dim) && !d.inRun) {
			Anchor anchor = DataStore.anchors().get(d.tetherAnchor);
			if (anchor == null || tooFar(p, anchor)) {
				WorldFx.particles(p, "minecraft:crit", 0.6, 0.3, 0.3, 14);
				clear(p, d, true);
				WorldFx.sound(p, "minecraft:entity.leash_knot.break", 1.0f, 0.9f);
				WorldFx.sound(p, "minecraft:block.chain.break", 1.0f, 0.7f);
				return true;
			}
		}
		if (d.inRun || Dims.OVERWORLD.equals(dim)) {
			lay(p, d);
		}
		return changed;
	}

	/** Horizontal distance to the arrival portal (beyond) or the anchor (Overworld); -1 when not bound. */
	public static int distance(ServerPlayer p, PlayerData d) {
		if (d.tetherAnchor == null) {
			return -1;
		}
		if (!d.inRun) {
			Anchor anchor = DataStore.anchors().get(d.tetherAnchor);
			if (anchor == null) {
				return 0;
			}
			double ax = p.getX() - anchor.x - 0.5;
			double az = p.getZ() - anchor.z - 0.5;
			return (int) Math.sqrt(ax * ax + az * az);
		}
		return (int) arrivalDistance(p, d);
	}

	public static double arrivalDistance(ServerPlayer p, PlayerData d) {
		double dx = p.getX() - d.arrivalX;
		double dz = p.getZ() - d.arrivalZ;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** The further from the portal, the faster the Starved drink. */
	public static double distanceFactor(ServerPlayer p, PlayerData d) {
		return 1.0 + arrivalDistance(p, d) / Config.TETHER_DISTANCE_STEP;
	}

	/** Removes this player's chain and unbinds them, optionally returning the Tether item. */
	public static void clear(ServerPlayer p, PlayerData d, boolean returnItem) {
		WorldFx.run("kill @e[type=minecraft:block_display,tag=" + tag(p) + "]");
		if (returnItem && d.tetherAnchor != null) {
			if (!p.getInventory().add(new ItemStack(ModItems.TETHER))) {
				WorldFx.run("give " + p.getStringUUID() + " extremedimensions:tether");
			}
		}
		d.tetherAnchor = null;
		d.hasRopeEnd = false;
	}

	/** Every 10 seconds: removes segments of players who are no longer bound, including leftovers. */
	public static void sweep(MinecraftServer s) {
		if (s.getTickCount() % 200 != 0) {
			return;
		}
		StringBuilder sel = new StringBuilder("kill @e[type=minecraft:block_display,tag=" + TAG);
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (DataStore.get(p.getUUID()).tetherAnchor != null) {
				sel.append(",tag=!").append(tag(p));
			}
		}
		WorldFx.run(sel.append("]").toString());
	}

	private static String tag(ServerPlayer p) {
		return "edt_" + p.getStringUUID().replace("-", "").substring(0, 12);
	}

	/** Standing near the anchor, or near its portal, keeps the tether whole. */
	private static boolean tooFar(ServerPlayer p, Anchor anchor) {
		double max = Config.TETHER_BREAK_RANGE + 0.5;
		double dx = anchor.x + 0.5 - p.getX();
		double dz = anchor.z + 0.5 - p.getZ();
		if (dx * dx + dz * dz <= max * max && Math.abs(anchor.y - p.getY()) <= max) {
			return false;
		}
		if (!anchor.path.isEmpty()) {
			double[] portal = anchor.path.get(anchor.path.size() - 1);
			double px = portal[0] - p.getX();
			double py = portal[1] - p.getY() - 1.0;
			double pz = portal[2] - p.getZ();
			double slack = Config.TETHER_PORTAL_SLACK;
			return px * px + py * py + pz * pz > slack * slack;
		}
		return true;
	}

	/**
	 * Iron chain in the Overworld and Nether, a thin glowing amethyst cord in the End.
	 * Returns {block id as SNBT string, thickness}; 26.3 block displays only accept a plain id here.
	 */
	private static String[] look(String dim) {
		if ("extremedimensions:the_seam".equals(dim)) {
			return new String[] {"\"minecraft:white_wool\"", "0.06"}; // a thread of silk
		}
		if ("minecraft:the_end".equals(dim)) {
			return new String[] {"\"minecraft:amethyst_block\"", "0.09"};
		}
		String chain = BuiltInRegistries.BLOCK.containsKey(Identifier.fromNamespaceAndPath("minecraft", "iron_chain"))
				? "minecraft:iron_chain" : "minecraft:chain";
		return new String[] {"\"" + chain + "\"", "1"}; // the default chain state is upright (axis=y)
	}

	/** Lays a segment on the ground once the player walked far enough since the previous one. */
	public static void lay(ServerPlayer p, PlayerData d) {
		String dim = Dims.fullId(p);
		double x = p.getX();
		double y = p.getY() + 0.1;
		double z = p.getZ();
		if (!d.hasRopeEnd || !dim.equals(d.ropeDim)) {
			// Just came through a portal: the chain starts again at the arrival point
			d.hasRopeEnd = true;
			d.ropeDim = dim;
			d.ropeX = x;
			d.ropeY = y;
			d.ropeZ = z;
			return;
		}
		double dx = x - d.ropeX;
		double dy = y - d.ropeY;
		double dz = z - d.ropeZ;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len > 8) {
			// Teleport or a long fall: never stretch through the air
			d.ropeX = x;
			d.ropeY = y;
			d.ropeZ = z;
			return;
		}
		// Only on the ground (or swimming): jumping leaves no chain hanging in the air
		if (len < Config.ROPE_SEGMENT || !(p.onGround() || p.isInWater())) {
			return;
		}
		// Quaternion rotating the block's y axis onto the segment direction
		double ux = dx / len, uy = dy / len, uz = dz / len;
		double qx = uz, qy = 0, qz = -ux, qw = 1 + uy;
		double n = Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
		if (n < 1e-6) {
			qx = 1;
			qy = 0;
			qz = 0;
			qw = 0;
		} else {
			qx /= n;
			qy /= n;
			qz /= n;
			qw /= n;
		}
		String[] look = look(dim);
		double thick = Double.parseDouble(look[1]);
		// The centre of the (scaled) block's bottom face sits on the start point
		double[] c = rotate(qx, qy, qz, qw, 0.5 * thick, 0, 0.5 * thick);
		int index = d.ropeIndex++;
		String tag = tag(p);
		String nbt = String.format(Locale.ROOT,
				"{Tags:[\"%s\",\"%s\",\"%s_s%d\"],block_state:%s,brightness:{sky:15,block:15},"
				+ "transformation:{left_rotation:[%.5ff,%.5ff,%.5ff,%.5ff],right_rotation:[0f,0f,0f,1f],"
				+ "translation:[%.5ff,%.5ff,%.5ff],scale:[%.3ff,%.5ff,%.3ff]}}",
				TAG, tag, tag, index, look[0], qx, qy, qz, qw, -c[0], -c[1], -c[2], thick, len, thick);
		WorldFx.run(String.format(Locale.ROOT, "execute in %s run summon minecraft:block_display %.3f %.3f %.3f %s",
				dim, d.ropeX, d.ropeY, d.ropeZ, nbt));
		// The chain clinks softly as it is laid, and beyond the wound a little soul light clings to it
		boolean end = Dims.END.equals(Dims.of(p));
		if (index % 2 == 0) {
			WorldFx.run(String.format(Locale.ROOT, "execute in %s run playsound %s player @a %.2f %.2f %.2f 0.3 %.2f",
					dim, end ? "minecraft:block.amethyst_cluster.step" : "minecraft:block.chain.step", x, y, z,
					0.8 + ThreadLocalRandom.current().nextDouble() * 0.3));
		}
		if (index % 3 == 0 && Dims.isBeyond(Dims.of(p))) {
			WorldFx.particleIn(dim, "extremedimensions:soul_mote", d.ropeX, d.ropeY + 0.15, d.ropeZ, 0.2, 0.05, 0.2, 0.0, 1);
		}
		int old = index - Config.ROPE_MAX_SEGMENTS;
		if (old >= 0) {
			WorldFx.run("kill @e[type=minecraft:block_display,tag=" + tag + "_s" + old + "]");
		}
		d.ropeX = x;
		d.ropeY = y;
		d.ropeZ = z;
	}

	/** Rotates vector v by quaternion q. */
	private static double[] rotate(double qx, double qy, double qz, double qw, double vx, double vy, double vz) {
		double tx = 2 * (qy * vz - qz * vy);
		double ty = 2 * (qz * vx - qx * vz);
		double tz = 2 * (qx * vy - qy * vx);
		return new double[] {
				vx + qw * tx + (qy * tz - qz * ty),
				vy + qw * ty + (qz * tx - qx * tz),
				vz + qw * tz + (qx * ty - qy * tx)};
	}
}
