package net.battlehub.games;

import java.util.List;
import java.util.UUID;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.PartyLayouts;
import net.battlehub.core.BlockDecay;
import net.battlehub.core.GameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.Blocks;

/** TNT Run: blocks vanish shortly after you step on them. Fall through every floor and you are out. */
public final class TntRunMatch extends PartyMatch {
	private BlockDecay decay;

	public TntRunMatch(int id, MinecraftServer server, List<ServerPlayer> players) {
		super(id, GameMode.TNT_RUN, server, players);
	}

	private int layers() {
		return (int) Math.max(1, Math.min(5, game().setting("layers", 3)));
	}

	@Override
	public net.battlehub.core.BotStyle botStyle() {
		return net.battlehub.core.BotStyle.RUNNER;
	}

	@Override
	protected Blueprint design(Theme theme) {
		decay = new BlockDecay((int) Math.max(1, game().setting("decayTicks", 8)));
		return PartyLayouts.tntRun(theme, random, layers());
	}

	@Override
	protected boolean damageAllowed(ServerPlayer victim, DamageSource source, ServerPlayer attacker) {
		return false;
	}

	@Override
	protected void tickGame() {
		ServerLevel level = arena.level();
		for (UUID id : alive) {
			ServerPlayer p = player(id);
			if (p == null || !active(id)) continue;
			double y = p.getY() - 0.2;
			for (double ox : new double[] {-0.3, 0.3}) {
				for (double oz : new double[] {-0.3, 0.3}) {
					BlockPos pos = BlockPos.containing(p.getX() + ox, y, p.getZ() + oz);
					if (isFloor(level, pos)) {
						decay.step(pos.asLong(), runTicks);
					}
				}
			}
		}
		for (long packed : decay.collect(runTicks)) {
			BlockPos pos = BlockPos.of(packed);
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
			level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
			level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.01);
		}
	}

	/** A top block of one of the floors (the TNT underneath goes with it). */
	private boolean isFloor(ServerLevel level, BlockPos pos) {
		int rel = pos.getY() - arena.origin().getY();
		if (rel > 0 || rel % 7 != 0 || rel < -7 * (layers() - 1)) {
			return false;
		}
		return !level.getBlockState(pos).isAir();
	}
}
