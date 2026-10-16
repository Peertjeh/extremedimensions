package net.battlehub.games;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.NewLayouts;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.core.Loot;
import net.battlehub.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * SkyWars: everyone starts on their own island with a chest; the middle island has the best loot. Build, break,
 * fight; whoever dies drops everything. The last one alive wins; when time is up, the most kills among the
 * survivors wins.
 */
public final class SkyWarsMatch extends PartyMatch {
	private final Random loot = new Random();
	private int chestsFilled;

	public SkyWarsMatch(int id, MinecraftServer server, List<ServerPlayer> players) {
		super(id, GameMode.SKYWARS, server, players);
	}

	@Override
	public net.battlehub.core.BotStyle botStyle() {
		return net.battlehub.core.BotStyle.MELEE;
	}

	@Override
	protected Blueprint design(Theme theme) {
		return NewLayouts.skywars(theme, random, participants.size());
	}


	@Override
	protected void resetForPlay(ServerPlayer p) {
		super.resetForPlay(p);
		p.setGameMode(GameType.SURVIVAL);
	}

	@Override
	protected void onArenaReady() {
		int islandRolls = (int) Math.max(1, game().setting("islandChestRolls", 7));
		int centerRolls = (int) Math.max(1, game().setting("centerChestRolls", 9));
		for (BlockPos pos : arena.marks("chest")) {
			fill(pos, ConfigManager.skywars().island(), islandRolls);
		}
		for (BlockPos pos : arena.marks("center_chest")) {
			fill(pos, ConfigManager.skywars().center(), centerRolls);
		}
	}

	/** Puts loot in random slots of the chest at {@code pos}. */
	private void fill(BlockPos pos, List<Loot.Entry> table, int rolls) {
		BlockEntity be = arena.level().getBlockEntity(pos);
		if (!(be instanceof Container c)) {
			return;
		}
		c.clearContent();
		List<Integer> free = new ArrayList<>();
		for (int i = 0; i < c.getContainerSize(); i++) free.add(i);
		java.util.Collections.shuffle(free, loot);
		int n = 0;
		for (Loot.Pick pick : Loot.roll(table, rolls, loot)) {
			Item item = Ids.item(pick.item());
			if (item == Items.AIR || n >= free.size()) continue;
			c.setItem(free.get(n++), new ItemStack(item, Math.min(item.getDefaultMaxStackSize(), pick.count())));
		}
		c.setChanged();
		chestsFilled++;
	}

	public int chestsFilled() {
		return chestsFilled;
	}

	@Override
	public boolean onBreakBlock(ServerPlayer p, BlockPos pos, BlockState state) {
		return phase == Phase.RUNNING && active(p.getUUID()) && arena.bounds().contains(Vec3.atCenterOf(pos));
	}

	@Override
	public boolean onPlaceBlock(ServerPlayer p, BlockPos target, ItemStack held) {
		return phase == Phase.RUNNING && active(p.getUUID()) && arena.bounds().contains(Vec3.atCenterOf(target))
				&& (held.getItem() instanceof BlockItem || held.getItem() instanceof BucketItem);
	}

	@Override
	public boolean allowsDrops(ServerPlayer p) {
		return true;
	}

	/** Out, and everything you carried falls where you died. */
	@Override
	protected void onKilled(ServerPlayer victim, ServerPlayer killer) {
		victim.getInventory().dropAll();
		eliminate(victim);
	}

	@Override
	protected void onTimeUp() {
		UUID best = null;
		int most = -1;
		boolean tie = false;
		for (UUID id : alive) {
			int k = kills(id);
			if (k > most) {
				most = k;
				best = id;
				tie = false;
			} else if (k == most) {
				tie = true;
			}
		}
		finish(best == null || tie ? List.of() : List.of(best));
	}
}
