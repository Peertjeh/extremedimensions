package com.extremedimensions.events;

import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.anchor.SoulWards;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.WorldFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Unstable Anchors. Once the seal is broken (and in a severed world too, not in a mended one), an anchor without a
 * soul ward near the start of a Wound Storm may crack, as may one a Lurker drank its fill from. It spits sparks and
 * leaks charge, every player's alike; an Amethyst Shard used on it steadies it. Left alone it breaks after five
 * minutes and loses a quarter of everyone's charge. A soul ward keeps it from cracking at all.
 */
public final class UnstableAnchors {
	private UnstableAnchors() {}

	/** Whether anchors can crack in this world now. */
	public static boolean allowed() {
		return Config.UNSTABLE_ANCHORS > 0 && DataStore.sealBroken() && !"mended".equals(DataStore.ending()) && !com.extremedimensions.tapestry.Tapestry.steadyAnchors();
	}

	/** Cracks an anchor (unless it is warded, empty or already cracked). True when it cracked. */
	public static boolean unsettle(ServerLevel overworld, Anchor a) {
		// (the ward as last looked at with the anchor's path, if it has been)
		if (!allowed() || a.unstable > 0 || a.charge <= 0 || (a.checked ? a.warded : SoulWards.warded(overworld, a.x, a.y, a.z))) {
			return false;
		}
		crack(a);
		return true;
	}

	private static void crack(Anchor a) {
		a.unstable = Math.max(10, Config.UNSTABLE_SECONDS);
		DataStore.markDirty();
		WorldFx.soundAt("minecraft:block.basalt.break", a.x, a.y, a.z, 1.2f, 0.6f);
		WorldFx.soundAt("minecraft:block.respawn_anchor.charge", a.x, a.y, a.z, 0.8f, 0.5f);
		WorldFx.particleAt("minecraft:electric_spark", a.x + 0.5, a.y + 1.0, a.z + 0.5, 0.4, 0.4, 0.4, 0.2, 30);
		// Everyone whose charge it keeps hears of it, wherever they are (most of them are out beyond the wound)
		for (ServerPlayer p : WorldFx.server().getPlayerList().getPlayers()) {
			if (owns(p, a)) {
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.anchor.cracked", a.x, a.y, a.z, time(a.unstable)).withStyle(ChatFormatting.RED));
			}
		}
	}

	private static String time(int secs) {
		return String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
	}

	/** A storm breaks out around a player: unwarded anchors near may crack. Returns how many did. */
	public static int storm(ServerPlayer p) {
		// (anchors are kept in the Overworld: a storm anywhere else cracks none)
		if (!allowed() || !(p.level() instanceof ServerLevel level) || !Dims.OVERWORLD.equals(Dims.of(p))) {
			return 0;
		}
		int n = 0;
		double r = Math.max(0, Config.UNSTABLE_RANGE);
		for (Anchor a : DataStore.anchors().values()) {
			if (a.distSq(p.getX(), p.getY(), p.getZ()) <= r * r && ThreadLocalRandom.current().nextDouble() < Config.UNSTABLE_CHANCE
					&& unsettle(level, a)) {
				n++;
			}
		}
		return n;
	}

	/** Each second: cracked anchors leak, spit sparks, and break when their time is up. */
	public static void tick(MinecraftServer s) {
		if (s.getTickCount() % 20 != 11) {
			return;
		}
		for (Anchor a : DataStore.anchors().values()) {
			if (a.unstable <= 0) {
				continue;
			}
			step(a);
		}
	}

	/** One second of an unstable anchor (for the tests too). */
	public static void step(Anchor a) {
		a.leakAll(Math.max(0, Config.UNSTABLE_LEAK));
		a.unstable--;
		if (WorldFx.server() != null && Loaded.ready(WorldFx.server().overworld(), new BlockPos(a.x, a.y, a.z))) {
			WorldFx.particleAt("minecraft:electric_spark", a.x + 0.5, a.y + 1.0, a.z + 0.5, 0.35, 0.3, 0.35, 0.15, 6);
			if (a.unstable % 4 == 0) {
				WorldFx.soundAt("minecraft:block.amethyst_block.resonate", a.x, a.y, a.z, 0.5f, 0.5f);
			}
		}
		if (a.unstable == 0 || a.charge <= 0) {
			// Left alone it breaks: a quarter of everyone's charge is gone
			if (a.unstable == 0 && a.charge > 0) {
				a.scaleAll(1 - Math.max(0, Math.min(1, Config.UNSTABLE_LOSS)));
				WorldFx.soundAt("minecraft:block.respawn_anchor.deplete", a.x, a.y, a.z, 1.2f, 0.6f);
				WorldFx.particleAt("minecraft:lava", a.x + 0.5, a.y + 1.0, a.z + 0.5, 0.3, 0.2, 0.3, 0.0, 10);
				if (WorldFx.server() != null) {
					int lost = (int) Math.round(Math.max(0, Math.min(1, Config.UNSTABLE_LOSS)) * 100);
					for (ServerPlayer p : WorldFx.server().getPlayerList().getPlayers()) {
						if (owns(p, a)) {
							p.sendSystemMessage(Component.translatable("gui.extremedimensions.anchor.broke", a.x, a.y, a.z, lost).withStyle(ChatFormatting.RED));
						}
					}
				}
			}
			a.unstable = 0;
		}
		DataStore.markDirty();
	}

	/** An Amethyst Shard on a cracked anchor steadies it (and is used up). True when it did. */
	public static boolean steady(ServerPlayer p, Anchor a, ItemStack shard) {
		if (a.unstable <= 0) {
			return false;
		}
		a.unstable = 0;
		if (!p.isCreative()) {
			shard.shrink(1);
		}
		DataStore.markDirty();
		WorldFx.soundAt("minecraft:block.amethyst_block.chime", a.x, a.y, a.z, 1.4f, 1.0f);
		WorldFx.particleAt("minecraft:end_rod", a.x + 0.5, a.y + 1.0, a.z + 0.5, 0.3, 0.3, 0.3, 0.02, 12);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.anchor.steadied").withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	private static boolean owns(ServerPlayer p, Anchor a) {
		return a.owners.containsKey(p.getStringUUID());
	}

	/** An operator's crack: the nearest charged anchor within sixteen blocks, warded or not. */
	static String force(ServerPlayer p) {
		Anchor best = null;
		double bd = 16 * 16;
		for (Anchor a : DataStore.anchors().values()) {
			double d = a.distSq(p.getX(), p.getY(), p.getZ());
			if (d < bd && a.charge > 0) {
				bd = d;
				best = a;
			}
		}
		if (best == null) {
			return "commands.extremedimensions.event.no_anchor";
		}
		crack(best);
		return null;
	}

	/** The cracked anchors (for the status of a world). */
	public static long cracked() {
		return DataStore.anchors().values().stream().filter(a -> a.unstable > 0).count();
	}
}
