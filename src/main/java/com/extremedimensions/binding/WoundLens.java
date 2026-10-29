package com.extremedimensions.binding;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.extremedimensions.anchor.AnchorService;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.Dims;
import com.extremedimensions.world.WorldService;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Wound Lens: an echo shard set in a spyglass, bound with silk. It listens to wounds. Looked through, a charged
 * anchor tells whose charge it keeps; a Lurker shows what it is about; in a Wound Storm the next breach glows a few
 * seconds before something comes through; in the Seam the rift home glints. It only tells: it stops nothing, and it
 * does not name the Elders' blows. Each reading goes to the one looking, on their action bar.
 */
public final class WoundLens {
	private WoundLens() {}

	/** When each player was last told something else on the action bar (game time): the readout waits for it to be read. */
	private static final Map<UUID, Long> TOLD = new HashMap<>();
	private static boolean reading;

	/** Twice a second, for everyone looking through a lens. */
	public static void tick(MinecraftServer s) {
		if (s.getTickCount() % 10 != 5) {
			return;
		}
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (!looking(p)) {
				continue;
			}
			Component r = reading(p);
			if (r != null && !hushed(p)) {
				reading = true;
				try {
					p.sendSystemMessage(r, true);
				} finally {
					reading = false;
				}
			}
			glints(p);
		}
	}

	/** Something else was said to the player on the action bar (not a reading). */
	public static void told(ServerPlayer p) {
		if (!reading) {
			TOLD.put(p.getUUID(), p.level().getGameTime());
		}
	}

	/** Whether the readout holds off, so that what the player was just told on the action bar can be read (two seconds). */
	public static boolean hushed(ServerPlayer p) {
		Long at = TOLD.get(p.getUUID());
		return at != null && p.level().getGameTime() - at < 40;
	}

	public static void forget(UUID player) {
		TOLD.remove(player);
	}

	public static boolean looking(ServerPlayer p) {
		return p.isUsingItem() && p.getUseItem().is(ModItems.WOUND_LENS);
	}

	/** What the lens reads where the player looks, or null when it hears nothing there. */
	public static Component reading(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		double range = Math.max(4, Config.WOUND_LENS_RANGE);
		Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		double reach = hit.getType() == HitResult.Type.MISS ? range : hit.getLocation().distanceTo(eye);
		Entity seen = looked(p, eye, look, reach);
		if (seen instanceof Lurker l) {
			return lurker(l);
		}
		// (anchors are kept in the Overworld: a lodestone elsewhere at the same spot is no anchor)
		if (hit.getType() == HitResult.Type.BLOCK && Dims.OVERWORLD.equals(Dims.of(level)) && AnchorService.isAnchorBlock(level.getBlockState(hit.getBlockPos()))) {
			BlockPos b = hit.getBlockPos();
			Anchor a = DataStore.anchors().get(Anchor.key(b.getX(), b.getY(), b.getZ()));
			if (a != null) {
				return anchor(p, a);
			}
		}
		return null;
	}

	/** The creature nearest the middle of the view, nearer than the block looked at (only along the line of sight). */
	private static Entity looked(ServerPlayer p, Vec3 eye, Vec3 look, double reach) {
		Entity best = null;
		double bestDot = 0.995;
		// (as wide as the cone at its far end)
		AABB along = new AABB(eye, eye).expandTowards(look.scale(reach)).inflate(Math.max(2, reach * 0.1));
		for (Entity e : p.level().getEntities(p, along, e -> e.isAlive() && !e.isSpectator())) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d < 0.5 || d > reach) {
				continue;
			}
			double dot = to.scale(1 / d).dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = e;
			}
		}
		return best;
	}

	/** What a Lurker is about: watching, stalking, taking, drinking, fleeing, or (glowing red) hunting you. */
	public static Component lurker(Lurker l) {
		if (l.getLurkerMood() == com.extremedimensions.lurker.LurkerBody.MOOD_HUNTER) {
			return Component.translatable("gui.extremedimensions.lens.lurker.hunting").withStyle(ChatFormatting.RED);
		}
		String[] doing = {"watching", "stalking", "taking", "drinking", "fleeing"};
		int pose = Math.max(0, Math.min(doing.length - 1, l.getLurkerPose()));
		return Component.translatable("gui.extremedimensions.lens.lurker." + doing[pose]).withStyle(ChatFormatting.LIGHT_PURPLE);
	}

	/** Whose charge an anchor keeps, and whether it is opened to allies. */
	public static Component anchor(ServerPlayer p, Anchor a) {
		MutableComponent out = Component.translatable(a.type.endsWith("nether") ? "gui.extremedimensions.lens.anchor.nether" : "gui.extremedimensions.lens.anchor.end")
				.withStyle(ChatFormatting.GOLD);
		int shown = 0;
		for (Map.Entry<String, Double> e : a.owners.entrySet()) {
			if (e.getValue() < 1 || shown >= 4) {
				continue;
			}
			out.append("  ").append(name(p.level().getServer(), e.getKey()).copy().append(" " + time(e.getValue())).withStyle(ChatFormatting.WHITE));
			shown++;
		}
		if (shown == 0) {
			out.append("  ").append(Component.translatable("gui.extremedimensions.lens.anchor.empty").withStyle(ChatFormatting.GRAY));
		}
		if (a.unstable > 0) {
			out.append("  ").append(Component.translatable("gui.extremedimensions.lens.anchor.unstable", time(a.unstable)).withStyle(ChatFormatting.RED));
		}
		if (a.shared(p.getStringUUID()) > 0 || a.owners.containsKey("*")) {
			out.append("  ").append(Component.translatable("gui.extremedimensions.lens.anchor.shared").withStyle(ChatFormatting.AQUA));
		}
		return out;
	}

	/** Whose charge it is: a name, "shared" for charge kept for everyone (old worlds), or "someone" if unknown. */
	private static Component name(MinecraftServer s, String uid) {
		if ("*".equals(uid)) {
			return Component.translatable("gui.extremedimensions.lens.anchor.everyone");
		}
		try {
			UUID id = UUID.fromString(uid);
			ServerPlayer q = s.getPlayerList().getPlayer(id);
			if (q != null) {
				return q.getName();
			}
			PlayerData d = DataStore.peek(id);
			if (d != null && d.name != null) {
				return Component.literal(d.name);
			}
		} catch (IllegalArgumentException e) {
			// not a player's id
		}
		return Component.translatable("gui.extremedimensions.lens.anchor.unknown");
	}

	private static String time(double secs) {
		int s = (int) Math.round(secs);
		return String.format("%d:%02d", s / 60, s % 60);
	}

	/** The glints only the one looking sees: the storm's next breaches, and in the Seam the rift home. */
	private static void glints(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		for (BlockPos b : WorldService.breaches(p.getUUID())) {
			level.sendParticles(p, ParticleTypes.REVERSE_PORTAL, true, false, b.getX() + 0.5, b.getY() + 1.5, b.getZ() + 0.5, 24, 0.3, 1.0, 0.3, 0.05);
		}
		PlayerData d = DataStore.peek(p.getUUID());
		if (d != null && d.inSeam && SeamService.inSeam(p)) {
			Vec3 to = new Vec3(d.riftX - p.getX(), d.riftY + 1 - p.getEyeY(), d.riftZ - p.getZ());
			if (to.lengthSqr() > 1 && to.normalize().dot(p.getLookAngle()) > 0.97) {
				level.sendParticles(p, ParticleTypes.WAX_ON, true, false, d.riftX, d.riftY + 1.5, d.riftZ, 16, 0.4, 0.8, 0.4, 0.2);
			}
		}
	}
}
