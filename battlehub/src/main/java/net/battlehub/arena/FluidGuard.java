package net.battlehub.arena;

import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Water and lava in the arena world stay on the playing field: they never flow out of it, over a wall or down
 * into the void (where nothing would ever clear them), and buckets are only emptied where the game allows a block.
 */
public final class FluidGuard {
	private FluidGuard() {}

	/** The arena of a running or starting match at this spot, or null. */
	private static Arena arenaAt(BlockPos pos) {
		for (Match m : Matches.all()) {
			Arena a = m.arena();
			if (a != null && a.around(pos, 8)) {
				return a;
			}
		}
		return null;
	}

	/** May a fluid flow into this spot? Only asked in the arena world. */
	public static boolean maySpread(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel sl) || !ArenaWorld.is(sl)) {
			return true;
		}
		Arena a = arenaAt(pos);
		return a != null && pos.getY() >= a.voidY() && a.inPlayArea(pos);
	}

	/** May this entity empty a bucket here? */
	public static boolean mayEmpty(LivingEntity who, Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel sl) || !ArenaWorld.is(sl)) {
			return true;
		}
		if (!maySpread(level, pos)) {
			return false;
		}
		if (who instanceof ServerPlayer p) {
			Match m = Matches.of(p.getUUID());
			if (m == null) {
				return p.isCreative();
			}
			ItemStack held = p.getMainHandItem().getItem() instanceof BucketItem ? p.getMainHandItem() : p.getItemInHand(InteractionHand.OFF_HAND);
			return m.onPlaceBlock(p, pos, held);
		}
		return true;
	}
}
