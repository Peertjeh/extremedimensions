package com.extremedimensions.anchor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.network.Fx;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.network.Sync;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;
import com.extremedimensions.tether.TetherService;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Anchors: lodestones near a portal that store charge for one dimension.
 *
 * <p>The first fuel decides the dimension and turns the block into an Ember or Void Anchor. An anchor needs a free
 * path through the air to its portal; when blocked it leaks. Empty anchors revert to plain lodestones.
 */
public final class AnchorService {
	private AnchorService() {}

	private static int tick = 0;

	public static boolean isAnchorBlock(BlockState s) {
		return s.is(Blocks.LODESTONE) || s.is(ModBlocks.EMBER_ANCHOR) || s.is(ModBlocks.VOID_ANCHOR);
	}

	/** Items the client should hand to the server instead of placing them against an anchor. */
	public static boolean isUsableOnAnchor(Item item) {
		return item == ModItems.TETHER || item == ModItems.EMBER_TETHER || item == ModItems.VOID_TETHER
				|| item == ModItems.TETHER_FRAGMENT || FuelValues.NETHER.containsKey(item) || FuelValues.END.containsKey(item);
	}

	// ------------------------------------------------------------------ charge queries

	public static List<Anchor> linked(PlayerData d) {
		List<Anchor> out = new ArrayList<>();
		for (String k : d.linked) {
			Anchor a = DataStore.anchors().get(k);
			if (a != null) {
				out.add(a);
			}
		}
		return out;
	}

	/** The player's own charge in the anchors feeding their expedition. */
	public static double linkedCharge(ServerPlayer p, PlayerData d) {
		double sum = 0;
		for (Anchor a : linked(d)) {
			sum += a.of(p.getStringUUID());
		}
		return sum;
	}

	/** Drains {@code amount} of the player's own charge from their expedition anchors; returns what is left. */
	public static double drainLinked(ServerPlayer p, PlayerData d, double amount) {
		String uid = p.getStringUUID();
		double left = 0;
		for (Anchor a : linked(d)) {
			if (amount > 0) {
				amount -= a.take(uid, amount);
			}
			left += a.of(uid);
		}
		return left;
	}

	/** The player's own charge in charged anchors of this type nearby; -1 if none is nearby. */
	public static int nearCharge(ServerPlayer p, String type) {
		double r2 = Config.HUD_RANGE * Config.HUD_RANGE;
		double sum = -1;
		for (Anchor a : DataStore.anchors().values()) {
			if (type.equals(a.type) && a.distSq(p.getX(), p.getY(), p.getZ()) <= r2) {
				sum = Math.max(0, sum) + a.of(p.getStringUUID());
			}
		}
		return sum < 0 ? -1 : (int) Math.ceil(sum);
	}

	// ------------------------------------------------------------------ interaction

	/** Right click on a lodestone or anchor with the main hand. */
	public static InteractionResult use(ServerPlayer p, BlockPos pos) {
		PlayerData d = DataStore.get(p.getUUID());
		ItemStack stack = p.getMainHandItem();

		if (stack.isEmpty()) {
			if (p.isShiftKeyDown()) {
				// Sneak + empty hand: status screen (or the owed penalty first)
				Sync.state(p, d);
				if (!d.pendingOffer.isEmpty()) {
					Sync.offer(p, d);
				} else {
					ServerPlayNetworking.send(p, new ModPackets.OpenStatusPayload());
				}
				return InteractionResult.SUCCESS;
			}
			if (Anchor.key(pos.getX(), pos.getY(), pos.getZ()).equals(d.tetherAnchor) && !d.inRun) {
				TetherService.untie(p, d);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}

		Item item = stack.getItem();
		if (item == ModItems.TETHER_FRAGMENT) {
			return PenaltyService.mend(p, d, pos, stack);
		}
		if (item == ModItems.TETHER) {
			return TetherService.tie(p, d, pos, stack);
		}
		String type;
		if (item == ModItems.EMBER_TETHER || FuelValues.NETHER.containsKey(item)) {
			type = Dims.NETHER;
		} else if (item == ModItems.VOID_TETHER || FuelValues.END.containsKey(item)) {
			type = Dims.END;
		} else {
			return InteractionResult.PASS; // plain lodestone use (compasses)
		}
		return deposit(p, d, pos, stack, type);
	}

	private static InteractionResult deposit(ServerPlayer p, PlayerData d, BlockPos pos, ItemStack stack, String type) {
		if (!Dims.OVERWORLD.equals(Dims.of(p))) {
			return InteractionResult.SUCCESS;
		}
		ServerLevel level = (ServerLevel) p.level();
		String key = Anchor.key(pos.getX(), pos.getY(), pos.getZ());
		Anchor anchor = DataStore.anchors().get(key);
		if (anchor != null && !type.equals(anchor.type)) {
			WorldFx.soundSelf(p, "minecraft:block.note_block.bass", 0.8f, 0.5f);
			return InteractionResult.SUCCESS;
		}
		if (AnchorPaths.findPortal(level, pos, type) == null) {
			WorldFx.soundSelf(p, "minecraft:block.note_block.bass", 0.8f, 0.5f);
			return InteractionResult.SUCCESS;
		}

		String uid = p.getStringUUID();
		double space = Config.MAX_CHARGE - (anchor != null ? anchor.of(uid) : 0);
		if (space < 1) {
			return InteractionResult.SUCCESS;
		}

		Item item = stack.getItem();
		boolean instant = item == ModItems.EMBER_TETHER || item == ModItems.VOID_TETHER;
		double added;
		if (instant) {
			added = Math.min(space, Config.TETHER_ITEM_CHARGE * d.debtFactor());
			stack.shrink(1);
		} else {
			double per = FuelValues.of(type, item) * d.debtFactor();
			int n = (int) Math.min(stack.getCount(), Math.ceil(space / per));
			added = Math.min(space, n * per);
			stack.shrink(n);
		}

		boolean nether = Dims.NETHER.equals(type);
		if (anchor == null) {
			// First charge: the anchor picks its dimension and changes appearance
			anchor = new Anchor(pos.getX(), pos.getY(), pos.getZ(), type);
			DataStore.anchors().put(key, anchor);
			level.setBlock(pos, (nether ? ModBlocks.EMBER_ANCHOR : ModBlocks.VOID_ANCHOR).defaultBlockState(), 3, 512);
			WorldFx.sound(p, "minecraft:item.lodestone_compass.lock", 1.0f, 0.8f);
			WorldFx.sound(p, "minecraft:block.beacon.activate", 0.8f, nether ? 0.8f : 1.2f);
			Journal.unlock(p, d, JournalEntry.ANCHOR);
		}
		anchor.add(uid, added);
		Advancements.grant(p, Advancements.ANCHOR_CHARGED);
		if (Config.MAX_CHARGE - anchor.of(uid) < 1) {
			Journal.unlock(p, d, JournalEntry.SILENCE);
			Advancements.grant(p, Advancements.ANCHOR_FULL);
		}
		anchor.portalCheck = 0;

		WorldFx.fx(p, nether ? Fx.DEPOSIT_NETHER : Fx.DEPOSIT_END, (int) Math.round(added));
		double ax = pos.getX() + 0.5;
		double ay = pos.getY() + 1.0;
		double az = pos.getZ() + 0.5;
		WorldFx.particleAt(nether ? "minecraft:flame" : "minecraft:portal", ax, ay, az, 0.4, 0.4, 0.4, nether ? 0.04 : 0.6, instant ? 60 : 30);
		WorldFx.particleAt("minecraft:enchant", ax, ay + 0.5, az, 0.6, 0.6, 0.6, 0.4, instant ? 50 : 25);
		WorldFx.sound(p, nether ? "minecraft:block.respawn_anchor.charge" : "minecraft:block.end_portal_frame.fill", 0.8f, instant ? 1.0f : 1.2f);
		Sync.state(p, d);
		return InteractionResult.SUCCESS;
	}

	public static void onBroken(String dim, BlockPos pos) {
		if (Dims.OVERWORLD.equals(dim)) {
			DataStore.anchors().remove(Anchor.key(pos.getX(), pos.getY(), pos.getZ()));
		}
	}

	// ------------------------------------------------------------------ upkeep

	/** Every 10 ticks: leaking, reverting, path checks and particles. */
	public static void tick(MinecraftServer s) {
		if (++tick < 10) {
			return;
		}
		tick = 0;

		double leak = Config.BLOCKED_LEAK_PER_SECOND * 0.5;
		for (Anchor a : DataStore.anchors().values()) {
			if (a.blocked && a.charge > 0) {
				a.leak(leak);
			}
		}

		ServerLevel ow = s.overworld();
		List<ServerPlayer> watchers = new ArrayList<>();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (Dims.OVERWORLD.equals(Dims.of(p))) {
				watchers.add(p);
			}
		}
		if (watchers.isEmpty()) {
			return;
		}
		double r2 = Config.PARTICLE_RANGE * Config.PARTICLE_RANGE;
		Iterator<Anchor> it = DataStore.anchors().values().iterator();
		while (it.hasNext()) {
			Anchor a = it.next();
			boolean watched = false;
			for (ServerPlayer p : watchers) {
				if (a.distSq(p.getX(), p.getY(), p.getZ()) <= r2) {
					watched = true;
					break;
				}
			}
			if (!watched) {
				continue;
			}
			BlockPos pos = new BlockPos(a.x, a.y, a.z);
			if (!ow.isLoaded(pos)) {
				continue;
			}
			BlockState state = ow.getBlockState(pos);
			if (!isAnchorBlock(state)) {
				it.remove(); // blown up or otherwise gone
				continue;
			}
			if (a.charge <= 0) {
				// Empty: a plain lodestone again, ready to be bound to a dimension anew
				if (!state.is(Blocks.LODESTONE)) {
					ow.setBlock(pos, Blocks.LODESTONE.defaultBlockState(), 3, 512);
				}
				WorldFx.particleAt("minecraft:large_smoke", a.x + 0.5, a.y + 1.0, a.z + 0.5, 0.3, 0.2, 0.3, 0.02, 12);
				WorldFx.particleAt("minecraft:ash", a.x + 0.5, a.y + 1.0, a.z + 0.5, 0.4, 0.4, 0.4, 0.0, 20);
				WorldFx.soundAt("minecraft:block.respawn_anchor.deplete", a.x, a.y, a.z, 1.0f, 0.8f);
				it.remove();
				continue;
			}
			if (--a.portalCheck <= 0) {
				a.portalCheck = 4; // every 2 seconds
				a.path = AnchorPaths.findPath(ow, a);
				boolean nowBlocked = a.path.isEmpty();
				if (nowBlocked && !a.blocked && a.checked && a.charge > 0) {
					warnBlocked(watchers, a);
				}
				a.blocked = nowBlocked;
				a.checked = true;
				a.warded = SoulWards.warded(ow, a.x, a.y, a.z);
			}
			particles(a);
			hum(a, watchers);
		}
	}

	private static void warnBlocked(List<ServerPlayer> players, Anchor a) {
		double r2 = (Config.HUD_RANGE * 2) * (Config.HUD_RANGE * 2);
		for (ServerPlayer p : players) {
			if (a.distSq(p.getX(), p.getY(), p.getZ()) <= r2) {
				WorldFx.soundSelf(p, "minecraft:block.respawn_anchor.deplete", 0.8f, 0.6f);
			}
		}
	}

	/** A charged anchor hums now and then, low and quiet, louder the fuller it is. */
	private static void hum(Anchor a, List<ServerPlayer> watchers) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		if (rnd.nextInt(10) != 0) {
			return;
		}
		double fill = Math.min(1.0, a.charge / Config.MAX_CHARGE);
		float vol = 0.25f + 0.35f * (float) fill;
		for (ServerPlayer p : watchers) {
			if (a.distSq(p.getX(), p.getY(), p.getZ()) <= 16 * 16) {
				if (a.isNether()) {
					WorldFx.soundAt("minecraft:block.respawn_anchor.ambient", a.x, a.y, a.z, vol, 0.7f + 0.2f * (float) fill);
				} else {
					WorldFx.soundAt("minecraft:block.amethyst_block.chime", a.x, a.y, a.z, vol, 0.6f + 0.2f * (float) fill);
				}
				return;
			}
		}
	}

	private static void particles(Anchor a) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double fill = Math.min(1.0, a.charge / Config.MAX_CHARGE);
		double cx = a.x + 0.5;
		double cy = a.y + 0.5;
		double cz = a.z + 0.5;
		boolean nether = a.isNether();

		// Glow around the block: the fuller, the more
		int n = 1 + (int) Math.round(fill * 9);
		// Motes of its own light drift up from the stone
		WorldFx.particleAt(nether ? "extremedimensions:ember_mote" : "extremedimensions:void_mote", cx, cy + 0.4, cz, 0.35, 0.3, 0.35, 0.0, 1 + n / 3);
		if (a.warded && rnd.nextInt(2) == 0) {
			// Warded: a faint ring of soul light circles the anchor
			double ang = rnd.nextDouble() * Math.PI * 2;
			WorldFx.particleAt("extremedimensions:soul_mote", cx + Math.cos(ang) * 0.9, cy + 0.1, cz + Math.sin(ang) * 0.9, 0.0, 0.0, 0.0, 0.0, 1);
		}
		if (nether) {
			WorldFx.particleAt("minecraft:flame", cx, cy + 0.2, cz, 0.45, 0.45, 0.45, 0.01, n);
			if (fill > 0.25) {
				WorldFx.particleAt("minecraft:small_flame", cx, cy + 0.7, cz, 0.3, 0.2, 0.3, 0.02, n);
			}
			if (fill > 0.5 && rnd.nextInt(3) == 0) {
				WorldFx.particleAt("minecraft:lava", cx, cy + 0.6, cz, 0.2, 0.1, 0.2, 0.0, 1);
			}
			if (fill > 0.8) {
				WorldFx.particleAt("minecraft:soul_fire_flame", cx, cy + 0.3, cz, 0.5, 0.5, 0.5, 0.01, 2);
			}
		} else {
			WorldFx.particleAt("minecraft:portal", cx, cy + 0.2, cz, 0.5, 0.5, 0.5, 0.6, n * 2);
			if (fill > 0.25) {
				WorldFx.particleAt("minecraft:reverse_portal", cx, cy + 0.7, cz, 0.3, 0.3, 0.3, 0.02, n);
			}
			if (fill > 0.5 && rnd.nextInt(2) == 0) {
				WorldFx.particleAt("minecraft:end_rod", cx, cy + 0.8, cz, 0.3, 0.3, 0.3, 0.02, 1);
			}
			if (fill > 0.8) {
				WorldFx.particleAt("minecraft:dragon_breath", cx, cy + 0.4, cz, 0.4, 0.3, 0.4, 0.01, 2);
			}
		}

		if (a.blocked) {
			// No path: no stream, just smoke while it drains
			WorldFx.particleAt("minecraft:large_smoke", cx, cy + 0.7, cz, 0.15, 0.1, 0.15, 0.01, 1 + (int) Math.round(fill * 2));
			return;
		}

		// Stream along the free path to the portal's centre
		List<double[]> path = a.path;
		if (path.size() < 2) {
			return;
		}
		int streams = 2 + (int) Math.round(fill * 6);
		for (int i = 0; i < streams; i++) {
			int seg = rnd.nextInt(path.size() - 1);
			double[] p0 = path.get(seg);
			double[] p1 = path.get(seg + 1);
			double dx = p1[0] - p0[0];
			double dy = p1[1] - p0[1];
			double dz = p1[2] - p0[2];
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (len < 1e-6) {
				continue;
			}
			double jx = (rnd.nextDouble() - 0.5) * 0.2;
			double jy = (rnd.nextDouble() - 0.5) * 0.2;
			double jz = (rnd.nextDouble() - 0.5) * 0.2;
			WorldFx.particleAt(nether ? "minecraft:flame" : "minecraft:end_rod", p0[0] + jx, p0[1] + jy, p0[2] + jz,
					dx / len, dy / len, dz / len, nether ? 0.06 : 0.08, 0);
			if (rnd.nextInt(2) == 0) {
				double t = rnd.nextDouble();
				WorldFx.particleAt(nether ? "minecraft:small_flame" : "minecraft:reverse_portal",
						p0[0] + dx * t, p0[1] + dy * t, p0[2] + dz * t, 0.04, 0.04, 0.04, 0.0, 1);
			}
		}
	}
}
