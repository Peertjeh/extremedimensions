package com.extremedimensions.lore;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.journal.MarginNote;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The First Weaver's trail. If there was a last Weaver, there was a first. Strung with Seam Silk, the great frame at
 * the heart of an Ancient City (the one place Reinforced Deepslate grows in the Overworld) shows what it held: figures
 * stepping into a weave. It leaves an Echo Thread, and the trail runs on through three more worlds: an Ember Thread
 * tied round a ledger in a bastion's chest, a Pale Thread in an empty socket in an End city's chest, a Silk Thread on
 * a loom in the Seam. Twisted together they make the Plumb Line, which points the way to the First Knot (see
 * {@code seam.FirstKnot}).
 *
 * <p>Each thread comes to a player on the trail who carries none of it and has not yet twisted the line or reached the
 * Knot, so one lost can be found again, and none can be farmed.
 */
public final class FirstWeaver {
	private FirstWeaver() {}

	/** Bastion chests (every kind) and the End city's: where the Ember and Pale Threads wait. */
	private static final Set<ResourceKey<LootTable>> BASTION = Set.of(BuiltInLootTables.BASTION_TREASURE, BuiltInLootTables.BASTION_OTHER,
			BuiltInLootTables.BASTION_BRIDGE, BuiltInLootTables.BASTION_HOGLIN_STABLE);

	/** Whether this player is on the trail and still needs this thread. */
	public static boolean needs(ServerPlayer p, PlayerData d, Item thread) {
		return d.frameStrung && !d.knotReached && !holds(p, thread) && !holds(p, ModItems.PLUMB_LINE);
	}

	static boolean holds(ServerPlayer p, Item item) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(item)) {
				return true;
			}
		}
		return false;
	}

	/** Whether this is the frame: Reinforced Deepslate in the Overworld (the Seam's pedestals are no frame). */
	public static boolean isFrame(ServerLevel level, BlockPos pos) {
		return Dims.OVERWORLD.equals(Dims.of(level)) && level.getBlockState(pos).is(Blocks.REINFORCED_DEEPSLATE);
	}

	/** Seam Silk used on the frame: it is strung, shows what it held, and leaves the Echo Thread. */
	public static InteractionResult string(ServerPlayer p, BlockPos pos, ItemStack silk) {
		PlayerData d = DataStore.get(p.getUUID());
		if (d.frameStrung && !needs(p, d, ModItems.ECHO_THREAD)) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.frame.strung"), true);
			WorldFx.soundSelf(p, "minecraft:block.wool.place", 0.6f, 0.6f);
			return InteractionResult.SUCCESS;
		}
		int cost = Math.max(0, Config.FRAME_SILK);
		if (!p.isCreative() && silk.getCount() < cost) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.frame.more_silk", cost), true);
			WorldFx.soundSelf(p, "minecraft:block.note_block.bass", 0.8f, 0.5f);
			return InteractionResult.SUCCESS;
		}
		if (!p.isCreative()) {
			silk.shrink(cost);
		}
		boolean first = !d.frameStrung;
		d.frameStrung = true;
		DataStore.markDirty();
		VISIONS.add(new Vision((ServerLevel) p.level(), pos, p.getX(), p.getZ(), DataStore.ending()));
		WorldFx.give(p, new ItemStack(ModItems.ECHO_THREAD));
		if (first) {
			Journal.unlock(p, d, JournalEntry.FRAME);
			Journal.note(p, d, MarginNote.WEAVERS);
			Advancements.grant(p, Advancements.FRAME_STRUNG);
		}
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.frame.shows"), true);
		return InteractionResult.SUCCESS;
	}

	/**
	 * A chest opened by a player on the trail: a bastion's holds the Ember Thread tied round a ledger, an End city's the
	 * Pale Thread; whoever else opens it finds only what it always held.
	 */
	public static void chest(ResourceKey<LootTable> table, Object opener, Consumer<ItemStack> drops) {
		if (!(opener instanceof ServerPlayer p) || p.isSpectator()) {
			return;
		}
		Item thread = BASTION.contains(table) ? ModItems.EMBER_THREAD : BuiltInLootTables.END_CITY_TREASURE.equals(table) ? ModItems.PALE_THREAD : null;
		if (thread == null) {
			return;
		}
		PlayerData d = DataStore.get(p.getUUID());
		if (needs(p, d, thread)) {
			drops.accept(new ItemStack(thread));
			WorldFx.soundSelf(p, "minecraft:block.wool.break", 0.8f, 0.7f);
		}
	}

	/** A loom in the Seam: for one on the trail, its half-woven tapestry shows the first knot tied, and gives its thread. */
	public static InteractionResult loom(ServerPlayer p, BlockPos pos) {
		PlayerData d = DataStore.get(p.getUUID());
		if (!needs(p, d, ModItems.SILK_THREAD)) {
			// Anyone else: just a loom
			return InteractionResult.PASS;
		}
		ServerLevel level = (ServerLevel) p.level();
		String dim = level.dimension().identifier().toString();
		double x = pos.getX() + 0.5, y = pos.getY() + 1.2, z = pos.getZ() + 0.5;
		WorldFx.particleIn(dim, "extremedimensions:soul_mote", x, y, z, 0.6, 0.6, 0.6, 0.0, 30);
		WorldFx.particleIn(dim, "minecraft:end_rod", x, y + 0.6, z, 0.3, 0.4, 0.3, 0.02, 12);
		WorldFx.soundIn(dim, "minecraft:ui.loom.take_result", "block", x, y, z, 1.0f, 0.6f);
		WorldFx.soundIn(dim, "minecraft:block.amethyst_block.resonate", "block", x, y, z, 0.8f, 0.7f);
		WorldFx.give(p, new ItemStack(ModItems.SILK_THREAD));
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.loom.tapestry"), true);
		return InteractionResult.SUCCESS;
	}

	// ------------------------------------------------------------------ the vision in the frame

	/** What the strung frame shows, for eight seconds: strings across it, three figures stepping into the weave. */
	private static final class Vision {
		final ServerLevel level;
		final String dim;
		/** The frame's middle, and which way it runs (along x or along z). */
		final double cx, cy, cz;
		final boolean alongX;
		final double width, height;
		/** Which side the stringer stood (+1 or -1 across the frame): the figures come from there. */
		final int side;
		/** "mended", "severed" or "": the weave closes, frays, or only takes them. */
		final String ending;
		int t;

		Vision(ServerLevel level, BlockPos at, double px, double pz, String ending) {
			this.level = level;
			this.dim = level.dimension().identifier().toString();
			this.ending = ending;
			// The frame's extent: its Reinforced Deepslate within ten blocks
			int minX = at.getX(), maxX = minX, minY = at.getY(), maxY = minY, minZ = at.getZ(), maxZ = minZ;
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			for (int dx = -10; dx <= 10; dx++) {
				for (int dy = -6; dy <= 10; dy++) {
					for (int dz = -10; dz <= 10; dz++) {
						m.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
						if (level.getBlockState(m).is(Blocks.REINFORCED_DEEPSLATE)) {
							minX = Math.min(minX, m.getX());
							maxX = Math.max(maxX, m.getX());
							minY = Math.min(minY, m.getY());
							maxY = Math.max(maxY, m.getY());
							minZ = Math.min(minZ, m.getZ());
							maxZ = Math.max(maxZ, m.getZ());
						}
					}
				}
			}
			alongX = maxX - minX >= maxZ - minZ;
			cx = (minX + maxX + 1) / 2.0;
			cz = (minZ + maxZ + 1) / 2.0;
			cy = (minY + maxY + 1) / 2.0;
			width = Math.max(3, (alongX ? maxX - minX : maxZ - minZ) - 1);
			height = Math.max(3, maxY - minY - 1);
			double across = alongX ? pz - cz : px - cx;
			side = across >= 0 ? 1 : -1;
		}

		/** A point on (or off) the frame: {@code u} along it from the middle, {@code v} up from the middle, {@code w} out. */
		double[] at(double u, double v, double w) {
			return alongX ? new double[] {cx + u, cy + v, cz + w * side} : new double[] {cx + w * side, cy + v, cz + u};
		}

		void particle(String name, double u, double v, double w, int count) {
			double[] q = at(u, v, w);
			WorldFx.particleIn(dim, name, q[0], q[1], q[2], 0.05, 0.05, 0.05, 0.0, count);
		}

		void sound(String id, float vol, float pitch) {
			WorldFx.soundIn(dim, id, "block", cx, cy, cz, vol, pitch);
		}

		/** One tick; false once it is over. */
		boolean step() {
			t++;
			int strings = 9;
			double half = width / 2, top = height / 2;
			if (t == 1) {
				sound("minecraft:block.wool.place", 1.0f, 0.6f);
				sound("minecraft:block.amethyst_block.resonate", 1.2f, 0.5f);
			}
			// The strings: drawn up the frame one by one, then held
			if (t % 2 == 0 && t < 130) {
				int shown = Math.min(strings, t / 4 + 1);
				for (int i = 0; i < shown; i++) {
					double u = -half + width * (i + 0.5) / strings;
					double v = -top + ((t * 0.37 + i * 1.7) % (height));
					particle("extremedimensions:soul_mote", u, v, 0, 1);
				}
			}
			// The figures: three, one after another, walk out of the dark towards the frame and step into it
			for (int f = 0; f < 3; f++) {
				int start = 20 + f * 25, walk = 60;
				int s = t - start;
				if (s < 0 || s > walk || t % 2 != 0) {
					continue;
				}
				double w = 7.0 * (1 - s / (double) walk);
				double u = (f - 1) * half * 0.45;
				double feet = -top;
				for (double v = 0.1; v < 1.8; v += 0.35) {
					particle("minecraft:sculk_soul", u, feet + v, w, 1);
				}
				particle("minecraft:soul", u, feet + 2.0, w, 1);
				if (s == walk) {
					particle("minecraft:end_rod", u, feet + 1, 0, 14);
					sound("minecraft:block.sculk_sensor.clicking", 1.0f, 0.6f + f * 0.1f);
				}
			}
			// Then the weave: closing in a mended world, fraying in a severed one, or only holding them
			if (t >= 125 && t % 2 == 0) {
				double k = (t - 125) / 35.0;
				for (int i = 0; i < strings; i++) {
					double u0 = -half + width * (i + 0.5) / strings;
					double v = -top + ((t * 0.5 + i * 2.3) % height);
					switch (ending) {
						case "mended" -> particle("minecraft:end_rod", u0 * (1 - k), v, 0, 1);
						case "severed" -> particle("minecraft:large_smoke", u0, v - k * 2, 0, 1);
						default -> particle("extremedimensions:soul_mote", u0, v, 0, 1);
					}
				}
			}
			if (t == 125) {
				sound("minecraft:block.sculk_shrieker.shriek", 0.5f, 0.6f);
			}
			if (t >= 160) {
				sound("minecraft:block.beacon.deactivate", 0.8f, 0.6f);
				return false;
			}
			return true;
		}
	}

	private static final List<Vision> VISIONS = new ArrayList<>();

	public static void tick(MinecraftServer s) {
		for (Iterator<Vision> it = VISIONS.iterator(); it.hasNext();) {
			if (!it.next().step()) {
				it.remove();
			}
		}
	}

	/** Visions showing now (for the tests). */
	public static int visions() {
		return VISIONS.size();
	}

	public static void clear() {
		VISIONS.clear();
	}
}
