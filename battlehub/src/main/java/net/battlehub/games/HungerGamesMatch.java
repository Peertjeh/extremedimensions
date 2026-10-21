package net.battlehub.games;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.battlehub.arch.town.HungerMap;
import net.battlehub.arch.town.LobbyThemes;
import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.BorderShrink;
import net.battlehub.core.GameMode;
import net.battlehub.core.Loot;
import net.battlehub.hub.HungerMaps;
import net.battlehub.lang.Lang;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;

/**
 * Hunger Games: the map is the lobby city of the week (see {@link HungerMaps}), in a theme that changes per match.
 * Everyone starts empty-handed on the plaza round the cornucopia; the houses have chests and barrels, the landmarks
 * better chests. The first half minute nobody can hurt anyone, later the chests are filled again once, and a border
 * closes in on the plaza. Whoever dies drops everything; the last one alive wins (when time is up: the most kills).
 */
public final class HungerGamesMatch extends PartyMatch {
	private static final String[] CHESTS = {"cornucopia", "landmark_chest", "chest"};

	private final Random loot = new Random();
	private final WorldBorder border = new WorldBorder();
	private final BorderShrink shrink;
	private HungerMap map;
	private String themeId = "castle";
	private boolean announcedShrink;
	private int chestsFilled;
	private int refills;
	/** Blocks players put down: only those (and soft things such as leaves and cobwebs) can be broken. */
	private final Set<BlockPos> placed = new HashSet<>();

	public HungerGamesMatch(int id, MinecraftServer server, List<ServerPlayer> players) {
		super(id, GameMode.HUNGER_GAMES, server, players);
		int r = ConfigManager.config().lobby.islandRadius;
		shrink = new BorderShrink(2 * (r + 10), Math.max(8, game().setting("borderEnd", 24)), (int) game().setting("borderDelaySeconds", 300) * 20,
				(int) game().setting("borderShrinkSeconds", 600) * 20);
	}

	@Override
	public net.battlehub.core.BotStyle botStyle() {
		return net.battlehub.core.BotStyle.MELEE;
	}

	@Override
	protected Blueprint design(Theme theme) {
		map = HungerMaps.current();
		themeId = LobbyThemes.supports(theme.id()) ? theme.id() : "castle";
		net.battlehub.BattleHub.LOGGER.info("Hunger Games for {} players: the city of week {} as {}", participants.size(), HungerMaps.week(), themeId);
		return HungerMaps.blueprint(map, themeId);
	}

	/** Only themes that suit a city. */
	@Override
	protected Theme pickTheme() {
		Theme t = super.pickTheme();
		return LobbyThemes.supports(t.id()) ? t : Theme.CASTLE;
	}

	/** The map is always the city of the week. */
	@Override
	public List<net.battlehub.arena.CustomMaps.Map> customMapOptions() {
		return List.of();
	}

	public HungerMap map() {
		return map;
	}

	public String themeId() {
		return themeId;
	}

	public int chestsFilled() {
		return chestsFilled;
	}

	public int refills() {
		return refills;
	}

	private int graceTicks() {
		return (int) Math.max(0, game().setting("graceSeconds", 30)) * 20;
	}

	/** Still in the first half minute in which nobody can hurt anyone. */
	public boolean inGrace() {
		return phase != Phase.RUNNING || runTicks < graceTicks();
	}

	public double borderSize() {
		return shrink.sizeAt(runTicks);
	}

	@Override
	protected void resetForPlay(ServerPlayer p) {
		super.resetForPlay(p);
		p.setGameMode(GameType.SURVIVAL);
	}

	// ------------------------------------------------------------------ the border (per player, through packets)

	private void sendBorder(ServerPlayer p) {
		p.connection.send(new ClientboundInitializeBorderPacket(border));
	}

	@Override
	protected void onArrived(ServerPlayer p, int index) {
		border.setCenter(arena.origin().getX() + 0.5, arena.origin().getZ() + 0.5);
		border.setSize(shrink.startSize());
		border.setWarningBlocks(6);
		border.setWarningTime(10);
		sendBorder(p);
	}

	@Override
	protected void onWatcherAdded(ServerPlayer p) {
		sendBorder(p);
	}

	@Override
	protected void onRestored(ServerPlayer p) {
		p.connection.send(new ClientboundInitializeBorderPacket(p.level().getWorldBorder()));
	}

	// ------------------------------------------------------------------ chests

	@Override
	protected void onArenaReady() {
		fillAll();
	}

	private void fillAll() {
		int house = (int) Math.max(1, game().setting("houseChestRolls", 4));
		int landmark = (int) Math.max(1, game().setting("landmarkChestRolls", 6));
		int cornucopia = (int) Math.max(1, game().setting("cornucopiaChestRolls", 7));
		for (BlockPos pos : arena.marks("chest")) fill(pos, ConfigManager.hungerGames().house(), house);
		for (BlockPos pos : arena.marks("landmark_chest")) fill(pos, ConfigManager.hungerGames().landmark(), landmark);
		for (BlockPos pos : arena.marks("cornucopia")) fill(pos, ConfigManager.hungerGames().cornucopia(), cornucopia);
	}

	private void fill(BlockPos pos, List<Loot.Entry> table, int rolls) {
		BlockEntity be = arena.level().getBlockEntity(pos);
		if (!(be instanceof Container c)) return;
		c.clearContent();
		List<Integer> free = new ArrayList<>();
		for (int i = 0; i < c.getContainerSize(); i++) free.add(i);
		Collections.shuffle(free, loot);
		int n = 0;
		for (Loot.Pick pick : Loot.roll(table, rolls, loot)) {
			Item item = Ids.item(pick.item());
			if (item == Items.AIR || n >= free.size()) continue;
			c.setItem(free.get(n++), new ItemStack(item, Math.min(item.getDefaultMaxStackSize(), pick.count())));
		}
		c.setChanged();
		chestsFilled++;
	}

	// ------------------------------------------------------------------ the game

	@Override
	protected void onGo() {
		String week = HungerMaps.week();
		int grace = graceTicks() / 20;
		forEveryone(p -> Msg.send(p, "hg.go", week, Lang.tr(p, "hg.theme." + themeId), grace));
	}

	@Override
	protected void tickGame() {
		if (runTicks == graceTicks() && graceTicks() > 0) {
			forEveryone(p -> {
				Msg.send(p, "hg.grace_over");
				Msg.sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1.4f);
			});
		}
		int refill = (int) game().setting("refillSeconds", 600) * 20;
		if (refill > 0 && runTicks == refill) refill();
		if (runTicks % 20 != 0) return;
		double size = shrink.sizeAt(runTicks);
		if (shrink.shrinking(runTicks) && !announcedShrink) {
			announcedShrink = true;
			forEveryone(p -> Msg.send(p, "hg.shrinking"));
		}
		border.setSize(size);
		ClientboundSetBorderSizePacket packet = new ClientboundSetBorderSizePacket(border);
		forEveryone(p -> p.connection.send(packet));
		double dmg = game().setting("borderDamagePerSecond", 1);
		double cx = arena.origin().getX() + 0.5, cz = arena.origin().getZ() + 0.5;
		for (UUID id : new ArrayList<>(alive)) {
			ServerPlayer p = player(id);
			if (p == null || !active(id)) continue;
			if (BorderShrink.outside(p.getX(), p.getZ(), cx, cz, size) > 0) {
				p.hurtServer(arena.level(), p.damageSources().outOfBorder(), (float) dmg);
				Msg.actionBar(p, "hg.outside");
			}
			if (phase != Phase.RUNNING) return;
		}
	}

	/** The chests are filled again (once, halfway): announced to everyone. */
	public void refill() {
		fillAll();
		refills++;
		botLooted.clear();
		forEveryone(p -> {
			Msg.send(p, "hg.refill");
			Msg.sound(p, SoundEvents.CHEST_OPEN, 0.8f, 0.8f);
		});
	}

	/** During the peace nobody is anyone's enemy: bots loot instead of swinging at people they cannot hurt. */
	@Override
	public boolean isEnemy(UUID a, UUID b) {
		return !inGrace() && super.isEnemy(a, b);
	}

	/** Nobody hurts anyone in the first half minute; the rest (falls, fire, the border) hurts from the start. */
	@Override
	protected boolean damageAllowed(ServerPlayer victim, DamageSource source, ServerPlayer attacker) {
		return attacker == null || !inGrace();
	}

	@Override
	public boolean allowsDrops(ServerPlayer p) {
		return true;
	}

	/** Out, with a cannon shot everyone hears, and everything you carried falls where you died. */
	@Override
	protected void onKilled(ServerPlayer victim, ServerPlayer killer) {
		victim.getInventory().dropAll();
		int left = Math.max(0, alive.size() - 1);
		String who = name(victim.getUUID());
		forEveryone(p -> {
			Msg.send(p, "hg.cannon", who, left);
			Msg.sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 0.6f);
		});
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

	@Override
	public boolean onPlaceBlock(ServerPlayer p, BlockPos target, ItemStack held) {
		if (phase != Phase.RUNNING || !active(p.getUUID()) || !arena.inPlayArea(target)) return false;
		if (!(held.getItem() instanceof BlockItem) && !(held.getItem() instanceof BucketItem)) return false;
		placed.add(target.immutable());
		return true;
	}

	/** The city stays standing: only what players put down, and leaves, cobwebs and plants, can be broken. */
	@Override
	public boolean onBreakBlock(ServerPlayer p, BlockPos pos, BlockState state) {
		if (phase != Phase.RUNNING || !active(p.getUUID())) return false;
		return placed.remove(pos) || state.is(BlockTags.LEAVES) || state.is(Blocks.COBWEB) || state.canBeReplaced();
	}

	@Override
	public Component status(ServerPlayer viewer) {
		return Lang.tr(viewer, "hg.status", alive.size(), (int) Math.round(borderSize()));
	}

	// ------------------------------------------------------------------ bots

	private final Map<UUID, Set<BlockPos>> botLooted = new HashMap<>();
	private int botLoots;
	private final Map<String, Integer> botLootsBy = new HashMap<>();

	public int botLoots() {
		return botLoots;
	}

	/** Chests bots emptied of one kind ("cornucopia", "landmark_chest", "chest"), for the self-test. */
	public int botLoots(String mark) {
		return botLootsBy.getOrDefault(mark, 0);
	}

	/**
	 * A bot loots like a player: the nearest full chest it has not emptied (the cornucopia first, it starts next to
	 * it), not up or down many floors. Outside the border, or with nothing left to loot, it heads for the plaza,
	 * where the others end up as well.
	 */
	@Override
	public net.battlehub.core.ai.Objective botGoal(ServerPlayer bot) {
		if (arena == null) return null;
		Vec3 centre = Vec3.atBottomCenterOf(arena.origin().above());
		double cx = arena.origin().getX() + 0.5, cz = arena.origin().getZ() + 0.5;
		double size = borderSize();
		if (BorderShrink.outside(bot.getX(), bot.getZ(), cx, cz, size - 12) > 0) {
			return net.battlehub.core.ai.Objective.attack(v3(centre), "inside the border");
		}
		BlockPos chest = botChest(bot, size);
		if (chest != null) {
			BlockState st = arena.level().getBlockState(chest);
			BlockPos front = st.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? chest.relative(st.getValue(BlockStateProperties.HORIZONTAL_FACING)) : chest;
			return net.battlehub.core.ai.Objective.attack(v3(Vec3.atBottomCenterOf(front)), "a chest");
		}
		if (Math.hypot(bot.getX() - cx, bot.getZ() - cz) > 20) return net.battlehub.core.ai.Objective.attack(v3(centre), "the plaza");
		return null;
	}

	private BlockPos botChest(ServerPlayer bot, double size) {
		Set<BlockPos> done = botLooted.getOrDefault(bot.getUUID(), Set.of());
		double cx = arena.origin().getX() + 0.5, cz = arena.origin().getZ() + 0.5;
		// the nearest one, on about its own level (a chest up many floors waits until it walks past)
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (String mark : CHESTS) {
			for (BlockPos pos : arena.marks(mark)) {
				if (done.contains(pos) || Math.abs(pos.getY() - bot.getY()) > 6) continue;
				if (BorderShrink.outside(pos.getX() + 0.5, pos.getZ() + 0.5, cx, cz, size - 6) > 0) continue;
				double d = Math.hypot(pos.getX() + 0.5 - bot.getX(), pos.getZ() + 0.5 - bot.getZ());
				if (d < bestD && hasLoot(pos)) {
					bestD = d;
					best = pos;
				}
			}
		}
		return best;
	}

	private boolean hasLoot(BlockPos pos) {
		return arena.level().getBlockEntity(pos) instanceof Container c && !c.isEmpty();
	}

	/** At a chest (within reach) it takes everything it can carry and puts on better armor; hungry, it eats. */
	@Override
	public void botTick(ServerPlayer bot) {
		if (runTicks % 5 != 0 || arena == null || phase != Phase.RUNNING) return;
		for (String mark : CHESTS) {
			for (BlockPos pos : arena.marks(mark)) {
				if (bot.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > 3.5 * 3.5) continue;
				botLooted.computeIfAbsent(bot.getUUID(), k -> new HashSet<>()).add(pos);
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
				botLootsBy.merge(mark, 1, Integer::sum);
				arena.level().playSound(null, pos, SoundEvents.CHEST_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1f);
				SkyWarsMatch.wearArmor(bot);
			}
		}
		if (runTicks % 40 == 0 && bot.getFoodData().getFoodLevel() < 14) eat(bot);
	}

	private static void eat(ServerPlayer bot) {
		net.minecraft.world.entity.player.Inventory inv = bot.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			net.minecraft.world.food.FoodProperties food = st.get(DataComponents.FOOD);
			if (food == null || st.is(Items.ROTTEN_FLESH)) continue;
			bot.getFoodData().setFoodLevel(Math.min(20, bot.getFoodData().getFoodLevel() + food.nutrition()));
			st.shrink(1);
			return;
		}
	}
}
