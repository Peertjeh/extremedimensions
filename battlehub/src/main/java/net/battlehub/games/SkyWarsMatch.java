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
 * SkyWars: everyone starts on their own island with two chests; the smaller middle islands have better loot and the
 * super big island in the middle the best. Build, break, fight; whoever dies drops everything. The last one alive
 * wins; when time is up, the most kills among the survivors wins.
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
		int midRolls = (int) Math.max(1, game().setting("midChestRolls", 8));
		int centerRolls = (int) Math.max(1, game().setting("centerChestRolls", 9));
		for (BlockPos pos : arena.marks("chest")) {
			fill(pos, ConfigManager.skywars().island(), islandRolls, true);
		}
		for (BlockPos pos : arena.marks("mid_chest")) {
			fill(pos, ConfigManager.skywars().mid(), midRolls, false);
		}
		for (BlockPos pos : arena.marks("center_chest")) {
			fill(pos, ConfigManager.skywars().center(), centerRolls, false);
		}
	}

	/**
	 * Puts loot in random slots of the chest at {@code pos}. A spawn island's chest always has blocks among it (the
	 * islands are far apart: without blocks nobody gets off), one of the list's blocks when the dice gave none.
	 */
	private void fill(BlockPos pos, List<Loot.Entry> table, int rolls, boolean blocks) {
		BlockEntity be = arena.level().getBlockEntity(pos);
		if (!(be instanceof Container c)) {
			return;
		}
		c.clearContent();
		List<Integer> free = new ArrayList<>();
		for (int i = 0; i < c.getContainerSize(); i++) free.add(i);
		java.util.Collections.shuffle(free, loot);
		int n = 0;
		boolean gotBlocks = false;
		for (Loot.Pick pick : Loot.roll(table, rolls, loot)) {
			Item item = Ids.item(pick.item());
			if (item == Items.AIR || n >= free.size()) continue;
			ItemStack st = new ItemStack(item, Math.min(item.getDefaultMaxStackSize(), pick.count()));
			gotBlocks |= net.battlehub.bot.Bots.buildBlock(st);
			c.setItem(free.get(n++), st);
		}
		if (blocks && !gotBlocks && n < free.size()) {
			List<Loot.Entry> blockLines = new ArrayList<>();
			for (Loot.Entry e : table) {
				if (net.battlehub.bot.Bots.buildBlock(new ItemStack(Ids.item(e.item())))) blockLines.add(e);
			}
			for (Loot.Pick pick : Loot.roll(blockLines, 1, loot)) {
				Item item = Ids.item(pick.item());
				c.setItem(free.get(n++), new ItemStack(item, Math.min(item.getDefaultMaxStackSize(), pick.count())));
			}
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
		return phase == Phase.RUNNING && active(p.getUUID()) && arena.bounds().contains(Vec3.atCenterOf(target)) && arena.inPlayArea(target)
				&& (held.getItem() instanceof BlockItem || held.getItem() instanceof BucketItem);
	}

	// ------------------------------------------------------------------ bots

	/** Chests a bot has emptied: it does not walk back to them. */
	private final java.util.Map<UUID, java.util.Set<BlockPos>> botLooted = new java.util.HashMap<>();
	private int botLoots;

	/**
	 * A bot loots as a player does: first the chests on its own island, then, with blocks to build with, it bridges
	 * to the nearest chest further in (a middle island, then the middle). Once nothing is left within reach it just
	 * fights (and bridges to whoever it goes after).
	 */
	@Override
	public net.battlehub.core.ai.Objective botGoal(ServerPlayer bot) {
		BlockPos chest = botChest(bot);
		return chest == null ? null : net.battlehub.core.ai.Objective.attack(v3(Vec3.atBottomCenterOf(chest)), "a chest");
	}

	/** The nearest full chest it has not emptied yet: on its own island, or anywhere once it has blocks to bridge. */
	private BlockPos botChest(ServerPlayer bot) {
		if (arena == null) return null;
		boolean canBridge = botBlocks(bot) >= 8;
		java.util.Set<BlockPos> done = botLooted.getOrDefault(bot.getUUID(), java.util.Set.of());
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (String mark : CHESTS) {
			for (BlockPos pos : arena.marks(mark)) {
				if (done.contains(pos) || !hasLoot(pos)) continue;
				double dx = pos.getX() + 0.5 - bot.getX(), dz = pos.getZ() + 0.5 - bot.getZ();
				double flat = Math.sqrt(dx * dx + dz * dz);
				boolean here = flat <= 7 && Math.abs(pos.getY() - bot.getY()) <= 3;
				if (!here && !canBridge) continue;
				if (flat < bestD) {
					bestD = flat;
					best = pos;
				}
			}
		}
		return best;
	}

	private static final String[] CHESTS = {"chest", "mid_chest", "center_chest"};

	private boolean hasLoot(BlockPos pos) {
		return arena.level().getBlockEntity(pos) instanceof Container c && !c.isEmpty();
	}

	private static int botBlocks(ServerPlayer bot) {
		int n = 0;
		for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
			ItemStack st = bot.getInventory().getItem(i);
			if (net.battlehub.bot.Bots.buildBlock(st)) n += st.getCount();
		}
		return n;
	}

	/** At a chest (within reach): it takes everything it can carry and puts on armor it is not wearing yet. */
	@Override
	public void botTick(ServerPlayer bot) {
		if (runTicks % 5 != 0 || arena == null || phase != Phase.RUNNING) return;
		for (String mark : CHESTS) {
			for (BlockPos pos : arena.marks(mark)) {
				if (bot.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > 3.5 * 3.5) continue;
				botLooted.computeIfAbsent(bot.getUUID(), k -> new java.util.HashSet<>()).add(pos);
				if (!(arena.level().getBlockEntity(pos) instanceof Container c) || c.isEmpty()) continue;
				for (int i = 0; i < c.getContainerSize(); i++) {
					ItemStack st = c.getItem(i);
					if (st.isEmpty()) continue;
					ItemStack rest = st.copy();
					bot.getInventory().add(rest);
					c.setItem(i, rest.isEmpty() ? ItemStack.EMPTY : rest);
				}
				c.setChanged();
				botLoots++;
				arena.level().playSound(null, pos, net.minecraft.sounds.SoundEvents.CHEST_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1f);
				wearArmor(bot);
			}
		}
	}

	/** Armor from the inventory into an empty armor slot, or over a weaker piece (leather to netherite). */
	private static void wearArmor(ServerPlayer bot) {
		net.minecraft.world.entity.player.Inventory inv = bot.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.isEmpty()) continue;
			net.minecraft.world.entity.EquipmentSlot slot = bot.getEquipmentSlotForItem(st);
			if (!slot.isArmor()) continue;
			ItemStack worn = bot.getItemBySlot(slot);
			if (!worn.isEmpty() && armorRank(worn) >= armorRank(st)) continue;
			bot.setItemSlot(slot, st.copy());
			inv.setItem(i, worn.isEmpty() ? ItemStack.EMPTY : worn.copy());
		}
	}

	private static int armorRank(ItemStack st) {
		String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
		String[] order = {"leather", "golden", "chainmail", "iron", "diamond", "netherite"};
		for (int i = order.length - 1; i >= 0; i--) {
			if (id.startsWith(order[i])) return i + 1;
		}
		return 0;
	}

	/** Chests emptied by bots this match (self-test, inspect). */
	public int botLoots() {
		return botLoots;
	}

	/** Bots bridge anywhere a player may build. */
	@Override
	public boolean botMayBuild(BlockPos pos) {
		return phase == Phase.RUNNING && arena.bounds().contains(Vec3.atCenterOf(pos)) && arena.inPlayArea(pos);
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
