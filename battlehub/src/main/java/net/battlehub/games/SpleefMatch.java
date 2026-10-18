package net.battlehub.games;

import java.util.List;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.NewLayouts;
import net.battlehub.core.GameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Spleef: snow floors over the void and a fast shovel. Dig the snow away under the others; every block you dig
 * gives a snowball to push people with. Fall through the bottom floor and you are out.
 */
public final class SpleefMatch extends PartyMatch {
	public SpleefMatch(int id, MinecraftServer server, List<ServerPlayer> players) {
		super(id, GameMode.SPLEEF, server, players);
	}

	private int layers() {
		return (int) Math.max(1, Math.min(4, game().setting("layers", 2)));
	}

	@Override
	public net.battlehub.core.BotStyle botStyle() {
		return net.battlehub.core.BotStyle.RUNNER;
	}

	@Override
	protected Blueprint design(Theme theme) {
		return NewLayouts.snowSpleef(theme, random, layers());
	}


	@Override
	protected void resetForPlay(ServerPlayer p) {
		super.resetForPlay(p);
		// Survival so the shovel can dig; the match decides what may break
		p.setGameMode(GameType.SURVIVAL);
	}

	/** Snowballs only push here. */
	@Override
	public float throwableDamage() {
		return 0;
	}

	/** Snowballs push, nothing else touches you. */
	@Override
	protected boolean damageAllowed(ServerPlayer victim, DamageSource source, ServerPlayer attacker) {
		return attacker != null && attacker != victim && source.getDirectEntity() instanceof Projectile;
	}

	@Override
	public boolean onBreakBlock(ServerPlayer p, BlockPos pos, BlockState state) {
		if (phase != Phase.RUNNING || !active(p.getUUID()) || !state.is(Blocks.SNOW_BLOCK) || !arena.contains(Vec3.atCenterOf(pos))) {
			return false;
		}
		// Gone without drops; a snowball straight into the inventory instead
		arena.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		arena.level().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5,
				pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
		if (p.getInventory().countItem(Items.SNOWBALL) < 16) {
			p.getInventory().add(new ItemStack(Items.SNOWBALL));
		}
		return false;
	}

	@Override
	public boolean allowsDrops(ServerPlayer p) {
		return false;
	}
}
