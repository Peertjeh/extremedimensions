package net.battlehub.games.duel;

import java.util.UUID;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.NewLayouts;
import net.battlehub.core.DuelFormat;
import net.battlehub.core.GameMode;
import net.battlehub.games.bedwars.BuildRules;
import net.battlehub.match.DuelMatch;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Build UHC: a full kit with blocks, buckets, a rod and golden apples. You heal only from golden apples (hunger is
 * held just under the level where natural healing starts). Only blocks players placed can be broken.
 */
public final class BuildUhcMatch extends DuelMatch {
	private BuildRules build;

	public BuildUhcMatch(int id, boolean ranked, DuelFormat format, boolean challenge, MinecraftServer server, ServerPlayer a, ServerPlayer b) {
		super(id, GameMode.BUILD_UHC, ranked, format, challenge, server, a, b);
	}

	@Override
	protected Blueprint design(Theme theme) {
		return NewLayouts.buildUhc(theme, random, (int) game().setting("buildHeight", 20));
	}

	@Override
	protected void resetForPlay(ServerPlayer p) {
		super.resetForPlay(p);
		p.setGameMode(GameType.SURVIVAL);
	}

	@Override
	protected void onArenaReady() {
		BlockPos min;
		BlockPos max;
		if (!arena.marks("build_min").isEmpty() && !arena.marks("build_max").isEmpty()) {
			min = arena.mark("build_min");
			max = arena.mark("build_max");
		} else {
			// A hand-built map without a build box: the whole map
			AABB b = arena.bounds().deflate(4);
			min = BlockPos.containing(b.minX, b.minY, b.minZ);
			max = BlockPos.containing(b.maxX - 1, b.maxY + 10, b.maxZ - 1);
		}
		build = new BuildRules(arena.level(), min, max, 1).area(arena::inPlayArea);
		for (int i = 0; i < Math.min(2, arena.spawnCount()); i++) {
			build.protect(BlockPos.containing(arena.spawn(i).pos()));
		}
	}

	@Override
	public boolean onPlaceBlock(ServerPlayer p, BlockPos target, ItemStack held) {
		if (phase != Phase.RUNNING || !active(p.getUUID()) || build == null) {
			return false;
		}
		if (!(held.getItem() instanceof BlockItem) && !(held.getItem() instanceof BucketItem)) {
			return false;
		}
		if (build.mayPlace(target) != BuildRules.Verdict.OK) {
			Msg.actionBar(p, "builduhc.cannot_build");
			return false;
		}
		build.expect(target, runTicks);
		return true;
	}

	@Override
	public boolean onBreakBlock(ServerPlayer p, BlockPos pos, BlockState state) {
		if (phase == Phase.RUNNING && build != null && build.isPlaced(pos)) {
			build.forget(pos);
			return true;
		}
		return false;
	}

	@Override
	protected void tickGame() {
		if (build != null) {
			build.tick(runTicks);
		}
		// No natural healing: hunger stays just below the level where it starts
		for (UUID id : alive) {
			ServerPlayer p = player(id);
			if (p == null) continue;
			if (p.getFoodData().getFoodLevel() > 17) {
				p.getFoodData().setFoodLevel(17);
			}
			p.getFoodData().setSaturation(0f);
		}
	}
}
