package com.extremedimensions.binding;

import com.extremedimensions.config.Config;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModEnchantments;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.BlockScan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Veilsight, on a helmet: in the Seam and the Fray the fog keeps farther off (drawn on the client), Seam Veins near
 * shimmer while you sneak, and the Thinned notice you from farther.
 */
public final class Veilsight {
	private Veilsight() {}

	public static boolean has(ServerPlayer p) {
		return ModEnchantments.level(p.level().registryAccess(), p.getItemBySlot(EquipmentSlot.HEAD), ModEnchantments.VEILSIGHT) > 0;
	}

	/** How many times as far the Thinned notice this player. */
	public static double noticed(ServerPlayer p) {
		return has(p) ? Math.max(1, Config.ENCH_VEILSIGHT_NOTICE) : 1.0;
	}

	/** Every second: Seam Veins near a sneaking player with Veilsight shimmer, for their eyes only. */
	public static void tick(MinecraftServer s) {
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			// each player at their own moment of the second, not all at once
			if ((s.getTickCount() + p.getId()) % 20 == 3 && p.isShiftKeyDown() && SeamService.inSeam(p) && has(p)) {
				shimmer(p);
			}
		}
	}

	/** Shows the veins within reach to one player; returns how many there were. */
	public static int shimmer(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		int r = Math.max(1, Config.ENCH_VEILSIGHT_VEINS);
		BlockPos at = p.blockPosition();
		int[] n = {0};
		BlockScan.each(level, at.getX() - r, at.getY() - r, at.getZ() - r, at.getX() + r, at.getY() + r, at.getZ() + r, st -> st.is(ModBlocks.SEAM_VEIN), q -> {
			if (q.distSqr(at) <= r * r) {
				level.sendParticles(p, ParticleTypes.END_ROD, true, false, q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 3, 0.35, 0.35, 0.35, 0.0);
				n[0]++;
			}
		});
		return n[0];
	}
}
