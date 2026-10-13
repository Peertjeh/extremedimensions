package net.battlehub.games;

import java.util.List;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.PartyLayouts;
import net.battlehub.core.GameMode;
import net.battlehub.mixin.ArrowAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Bow Spleef: the floor bursts where an arrow lands. Fall through and you are out; the last player wins. */
public final class BowSpleefMatch extends PartyMatch {
	public BowSpleefMatch(int id, MinecraftServer server, List<ServerPlayer> players) {
		super(id, GameMode.BOW_SPLEEF, server, players);
	}

	@Override
	protected Blueprint design(Theme theme) {
		return PartyLayouts.spleef(theme, random);
	}

	@Override
	protected boolean damageAllowed(ServerPlayer victim, DamageSource source, ServerPlayer attacker) {
		// Arrows push (Punch) but the kit's Resistance cancels the damage itself.
		return attacker != null;
	}

	@Override
	protected void tickGame() {
		ServerLevel level = arena.level();
		int floorY = arena.origin().getY();
		for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, arena.bounds())) {
			if (arrow.getY() < arena.voidY() - 12) {
				arrow.discard();
				continue;
			}
			if (!((ArrowAccessor) (Object) arrow).battlehub$isInGround()) {
				continue;
			}
			BlockPos hit = nearestFloor(level, arrow.position(), floorY);
			arrow.discard();
			if (hit != null) {
				burst(level, hit);
			}
		}
	}

	private BlockPos nearestFloor(ServerLevel level, Vec3 pos, int floorY) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		int bx = (int) Math.floor(pos.x);
		int bz = (int) Math.floor(pos.z);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos p = new BlockPos(bx + dx, floorY, bz + dz);
				if (level.getBlockState(p).isAir()) continue;
				double d = Vec3.atCenterOf(p).distanceToSqr(pos);
				if (d < bestD && d < 4.0) {
					best = p;
					bestD = d;
				}
			}
		}
		return best;
	}

	/** Removes the hit block and its direct neighbours. */
	private void burst(ServerLevel level, BlockPos center) {
		int[][] offsets = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] o : offsets) {
			BlockPos p = center.offset(o[0], 0, o[1]);
			if (!level.getBlockState(p).isAir()) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 1, 0, 0, 0, 0);
		level.playSound(null, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f, 1.4f);
	}
}
