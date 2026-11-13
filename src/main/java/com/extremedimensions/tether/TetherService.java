package com.extremedimensions.tether;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.anchor.AnchorService;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
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
			// A lodestone becomes an anchor with its first fuel; only then does a Tether hold to it
			AnchorService.refuse(p, !Dims.OVERWORLD.equals(Dims.of(p)) ? "overworld_only" : "not_yet", Config.PORTAL_RANGE);
			return InteractionResult.SUCCESS;
		}
		if (d.inRun || key.equals(d.tetherAnchor)) {
			return InteractionResult.SUCCESS;
		}
		if (com.extremedimensions.penalty.Wound.refusesBinding(p, d)) {
			// A wound too fresh: no Tether holds to an anchor yet (and the one in hand stays there)
			return InteractionResult.SUCCESS;
		}
		// Already bound elsewhere? That tether comes back.
		clear(p, d, d.tetherAnchor != null);
		d.tetherTier = stack.is(ModItems.SEAM_WOVEN_TETHER) ? 2 : stack.is(ModItems.SOUL_WOVEN_TETHER) ? 1 : 0;
		d.tetherCharms = charms(stack);
		stack.shrink(1);
		d.tetherAnchor = key;
		d.hasRopeEnd = true;
		d.ropeDim = "minecraft:overworld";
		d.ropeX = pos.getX() + 0.5;
		d.ropeY = pos.getY() + 1.02;
		d.ropeZ = pos.getZ() + 0.5;
		// (the Tether left the inventory for the anchor: both are saved together)
		DataStore.markDirty();
		Journal.unlock(p, d, JournalEntry.CORD);
		Advancements.grant(p, Advancements.BOUND);
		WorldFx.fx(p, Fx.TIE, 0);
		com.extremedimensions.guide.Guide.done(p, d, com.extremedimensions.guide.Guide.TETHER);
		WorldFx.sound(p, "minecraft:entity.leash_knot.place", 1.0f, 0.7f);
		WorldFx.sound(p, "minecraft:block.chain.place", 1.0f, 0.8f);
		WorldFx.particleAt("extremedimensions:soul_mote", pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 0.3, 0.3, 0.3, 0.0, 16);
		WorldFx.particleAt("minecraft:soul", pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0.3, 0.2, 0.3, 0.02, 12);
		Sync.state(p, d);
		return InteractionResult.SUCCESS;
	}

	/** Empty hand on your own anchor: untie and get the Tether back. */
	public static void untie(ServerPlayer p, PlayerData d) {
		comeBack(p, d, "untied");
		WorldFx.sound(p, "minecraft:entity.leash_knot.break", 1.0f, 0.8f);
		Sync.state(p, d);
	}

	/**
	 * The Tether comes off its anchor and back to the player, and the action bar says so (3.55.0): why ({@code why}:
	 * untied, walked too far, or the anchor gone), and that it is back in the pack. It used to come back with only a
	 * sound. With a full pack the chat already says it waits for room (see {@link #owe}): then the action bar only says
	 * why it came off, and nothing at all for a Tether untied by hand.
	 */
	private static void comeBack(ServerPlayer p, PlayerData d, String why) {
		int owed = d.tetherOwed == null ? 0 : d.tetherOwed.size();
		clear(p, d, true);
		boolean kept = d.tetherOwed != null && d.tetherOwed.size() > owed;
		if (!kept || !"untied".equals(why)) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.tether." + why + (kept ? "_full" : "")), true);
		}
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
				comeBack(p, d, anchor == null ? "anchor_gone" : "slipped");
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
		// Short Chain: the distance starts to bite sooner
		double step = Math.max(25, Config.TETHER_DISTANCE_STEP - Penalty.SHORT_CHAIN.value * d.stacks(Penalty.SHORT_CHAIN));
		return 1.0 + arrivalDistance(p, d) / step;
	}

	/** Removes this player's chain and unbinds them, optionally returning the Tether item. */
	public static void clear(ServerPlayer p, PlayerData d, boolean returnItem) {
		WorldFx.run("kill @e[type=minecraft:block_display,tag=" + tag(p) + "]");
		if (returnItem && d.tetherAnchor != null) {
			ItemStack back = new ItemStack(tierItem(d.tetherTier));
			// it comes back with the enchantments it was tied with
			enchant(back, d.tetherCharms, p.level().registryAccess());
			if (!p.getInventory().add(back)) {
				// A full pack: it is kept for them until there is room, never dropped (where they stand, home through a
				// portal, it would lie in the portal and be carried back beyond)
				owe(p, d, d.tetherTier, d.tetherCharms);
			}
		}
		d.tetherAnchor = null;
		d.hasRopeEnd = false;
		if (d.tetherCharms != null) {
			d.tetherCharms.clear();
		}
		DataStore.markDirty();
	}

	/** A Tether kept for a player whose pack is full: it is handed over as soon as there is room (see {@link #settle}). */
	static void owe(ServerPlayer p, PlayerData d, int tier, java.util.Map<String, Integer> charms) {
		StringBuilder entry = new StringBuilder().append(tier).append(';');
		if (charms != null) {
			charms.forEach((id, level) -> entry.append(id).append('=').append(level).append(','));
		}
		if (d.tetherOwed == null) {
			d.tetherOwed = new java.util.ArrayList<>();
		}
		d.tetherOwed.add(entry.toString());
		DataStore.markDirty();
		p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.extremedimensions.tether.owed")
				.withStyle(net.minecraft.ChatFormatting.GOLD));
	}

	/** Every second: a Tether kept for this player is handed over once there is a free slot in their pack. */
	public static void settle(ServerPlayer p, PlayerData d) {
		if (d.tetherOwed == null || d.tetherOwed.isEmpty() || p.getInventory().getFreeSlot() < 0 || !p.isAlive()) {
			return;
		}
		String entry = d.tetherOwed.remove(0);
		int cut = entry.indexOf(';');
		int tier = 0;
		java.util.Map<String, Integer> charms = new java.util.HashMap<>();
		try {
			tier = Integer.parseInt(entry.substring(0, Math.max(0, cut)));
			for (String c : entry.substring(cut + 1).split(",")) {
				int eq = c.lastIndexOf('=');
				if (eq > 0) {
					charms.put(c.substring(0, eq), Integer.parseInt(c.substring(eq + 1)));
				}
			}
		} catch (RuntimeException e) {
			// a damaged entry still gives back a plain Tether of its tier
		}
		ItemStack back = new ItemStack(tierItem(tier));
		enchant(back, charms, p.level().registryAccess());
		if (!p.getInventory().add(back)) {
			d.tetherOwed.add(0, entry);
			return;
		}
		DataStore.markDirty();
		p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.extremedimensions.tether.returned")
				.withStyle(net.minecraft.ChatFormatting.GOLD));
		WorldFx.sound(p, "minecraft:block.chain.place", 0.8f, 1.1f);
	}

	/** A Tether item's enchantments, by id. */
	public static java.util.Map<String, Integer> charms(ItemStack stack) {
		java.util.Map<String, Integer> out = new java.util.HashMap<>();
		for (var e : stack.getEnchantments().entrySet()) {
			e.getKey().unwrapKey().ifPresent(k -> out.put(k.identifier().toString(), e.getIntValue()));
		}
		return out;
	}

	/** Puts kept enchantments back on a Tether item (any a datapack has taken out are left off). */
	public static void enchant(ItemStack stack, java.util.Map<String, Integer> charms, net.minecraft.core.RegistryAccess access) {
		if (charms == null) {
			return;
		}
		var registry = access.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
		for (java.util.Map.Entry<String, Integer> c : charms.entrySet()) {
			net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(c.getKey());
			if (id != null) {
				registry.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, id))
						.ifPresent(h -> stack.enchant(h, c.getValue()));
			}
		}
	}

	/** The level of a Tether enchantment a player has with them: on the bound Tether if bound, else the best Tether they carry. */
	public static int charm(ServerPlayer p, PlayerData d, net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> k) {
		// Bound, only the bound Tether counts: a second one in the bag would give Thrift and Silkbound at once
		if (d.tetherAnchor != null) {
			return d.charm(k);
		}
		int best = 0;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (isTether(s.getItem())) {
				best = Math.max(best, com.extremedimensions.registry.ModEnchantments.level(p.level().registryAccess(), s, k));
			}
		}
		return best;
	}

	/** Removes this player's chain (or thread home in the Seam) and nothing else: they stay bound. */
	public static void clearThread(ServerPlayer p, PlayerData d) {
		WorldFx.run("kill @e[type=minecraft:block_display,tag=" + tag(p) + "]");
		d.hasRopeEnd = false;
	}

	/**
	 * The best Tether tier a player has with them: the one they are bound with, or a woven Tether they carry (the Seam
	 * grants its extra time for it).
	 */
	public static int carriedTier(ServerPlayer p, PlayerData d) {
		int best = d.tetherAnchor != null ? d.tetherTier : 0;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize() && best < 2; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(ModItems.SEAM_WOVEN_TETHER)) {
				best = 2;
			} else if (s.is(ModItems.SOUL_WOVEN_TETHER)) {
				best = Math.max(best, 1);
			}
		}
		return best;
	}

	/** Every 10 seconds: removes segments of players who are no longer bound (or laying a thread in the Seam), including leftovers. */
	public static void sweep(MinecraftServer s) {
		if (s.getTickCount() % 200 != 0) {
			return;
		}
		StringBuilder sel = new StringBuilder("kill @e[type=minecraft:block_display,tag=" + TAG);
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			PlayerData d = DataStore.get(p.getUUID());
			if (d.tetherAnchor != null || d.inSeam) {
				sel.append(",tag=!").append(tag(p));
			}
		}
		WorldFx.run(sel.append("]").toString());
	}

	private static String tag(ServerPlayer p) {
		return "edt_" + p.getStringUUID().replace("-", "").substring(0, 12);
	}

	/** Standing near the anchor, or near its portal, keeps the tether whole. */
	/** The Tether item for a tier. */
	public static Item tierItem(int tier) {
		return tier == 2 ? ModItems.SEAM_WOVEN_TETHER : tier == 1 ? ModItems.SOUL_WOVEN_TETHER : ModItems.TETHER;
	}

	public static boolean isTether(Item item) {
		return item == ModItems.TETHER || item == ModItems.SOUL_WOVEN_TETHER || item == ModItems.SEAM_WOVEN_TETHER;
	}

	private static boolean tooFar(ServerPlayer p, Anchor anchor) {
		PlayerData d = DataStore.get(p.getUUID());
		// Short Tether (an Overworld curse) snaps it sooner
		int shorter = PenaltyService.owHere(Dims.of(p)) ? d.stacks(Penalty.SHORT_TETHER) : 0;
		double max = Math.max(2, Config.TETHER_BREAK_RANGE - Penalty.SHORT_TETHER.value * shorter) + 0.5 + (d.tetherTier > 0 ? Config.WOVEN_REACH : 0);
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
			return new String[] {"\"extremedimensions:seam_vein\"", "0.06"}; // a thread of silk
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
			WorldFx.soundIn(dim, end ? "minecraft:block.amethyst_cluster.step" : "minecraft:block.chain.step", "player", x, y, z, 0.3f,
					(float) (0.8 + ThreadLocalRandom.current().nextDouble() * 0.3));
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
