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
public final class HungerGamesMatch extends PartyMatch implements net.battlehub.games.hunger.HungerHost {
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
	/** The real Hunger Games: tributes, Gamemakers, sponsors, locked loot and power-ups (1.43). */
	private final net.battlehub.games.hunger.Capitol capitol;

	public HungerGamesMatch(int id, MinecraftServer server, List<ServerPlayer> players) {
		super(id, GameMode.HUNGER_GAMES, server, players);
		int r = ConfigManager.config().lobby.islandRadius;
		shrink = new BorderShrink(2 * (r + 10), Math.max(8, game().setting("borderEnd", 24)), (int) game().setting("borderDelaySeconds", 300) * 20,
				(int) game().setting("borderShrinkSeconds", 600) * 20);
		capitol = new net.battlehub.games.hunger.Capitol(this, loot.nextLong(), graceTicks(), timeLimitTicks(),
				(int) game().setting("feastSeconds", 480) * 20);
	}

	/** The Capitol of this match: its tributes, Gamemakers, sponsors, vaults and power-ups. */
	public net.battlehub.games.hunger.Capitol capitol() {
		return capitol;
	}

	// ------------------------------------------------------------------ what the Capitol needs (HungerHost)

	@Override
	public net.minecraft.server.level.ServerLevel level() {
		return arena.level();
	}

	@Override
	public List<ServerPlayer> tributes() {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID id : alive) {
			ServerPlayer p = player(id);
			if (p != null && active(id) && !p.isSpectator()) out.add(p);
		}
		return out;
	}

	@Override
	public List<ServerPlayer> audience() {
		List<ServerPlayer> out = new ArrayList<>();
		forEveryone(out::add);
		return out;
	}

	@Override
	public boolean live() {
		return phase == Phase.COUNTDOWN || phase == Phase.RUNNING;
	}

	@Override
	public int matchId() {
		return id;
	}

	/** The tributes stand on their plates for a while: the reaping and the opening music (1.47). */
	@Override
	protected int countdownSeconds() {
		return (int) Math.max(5, game().setting("countdownSeconds", 20));
	}

	/** Only the last ten seconds are counted on the screen: before that the reaping is shown. */
	@Override
	protected boolean showCountdown(int secs) {
		return secs <= 10;
	}

	@Override
	protected void onCountdownStart(int ticks) {
		capitol.opening(tributes(), ticks);
	}

	@Override
	protected void onFinished(List<UUID> winners) {
		capitol.finished(winners);
	}

	@Override
	public int runTicks() {
		return runTicks;
	}

	@Override
	public Random rng() {
		return loot;
	}

	@Override
	public Vec3 plaza() {
		return Vec3.atBottomCenterOf(arena.origin().above());
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
		return shrink.sizeAt(borderTicks());
	}

	private int borderTicks() {
		return runTicks;
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
	protected void onRestored(ServerPlayer p) {
		p.connection.send(new ClientboundInitializeBorderPacket(p.level().getWorldBorder()));
	}

	// ------------------------------------------------------------------ chests

	@Override
	protected void onArenaReady() {
		fillAll();
		capitol.arenaReady();
	}

	private void fillAll() {
		int house = (int) Math.max(1, game().setting("houseChestRolls", 4));
		int landmark = (int) Math.max(1, game().setting("landmarkChestRolls", 6));
		int cornucopia = (int) Math.max(1, game().setting("cornucopiaChestRolls", 7));
		var server = arena.level().getServer();
		for (BlockPos pos : arena.marks("chest")) {
			fill(pos, ConfigManager.hungerGames().house(), house);
			// now and then a vault key in a house
			if (loot.nextInt(100) < 7) extra(pos, net.battlehub.games.hunger.PowerUps.stack(server, "key", 1));
		}
		for (BlockPos pos : arena.marks("landmark_chest")) {
			fill(pos, ConfigManager.hungerGames().landmark(), landmark);
			if (loot.nextInt(100) < 25) {
				List<String> kinds = new ArrayList<>(net.battlehub.games.hunger.PowerUps.KINDS.keySet());
				extra(pos, net.battlehub.games.hunger.PowerUps.stack(server, kinds.get(loot.nextInt(kinds.size())), 1));
			}
		}
		for (BlockPos pos : arena.marks("cornucopia")) fill(pos, ConfigManager.hungerGames().cornucopia(), cornucopia);
	}

	/** One more stack in a free slot of a chest. */
	private void extra(BlockPos pos, ItemStack st) {
		if (st.isEmpty() || !(arena.level().getBlockEntity(pos) instanceof Container c)) return;
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).isEmpty()) {
				c.setItem(i, st);
				c.setChanged();
				return;
			}
		}
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
		capitol.go();
		forWatchers(this::offerSponsoring);
	}

	/** Watchers and the fallen can sponsor a tribute: a line in chat with a button. */
	private void offerSponsoring(ServerPlayer p) {
		if (capitol.sponsors().maySponsor(p)) {
			Msg.raw(p, Msg.button(p, "hg.sponsor.button", "hg.sponsor.button.hover", "/sponsor"));
		}
	}

	@Override
	protected void onWatcherAdded(ServerPlayer p) {
		sendBorder(p);
		if (phase == Phase.RUNNING) offerSponsoring(p);
	}

	@Override
	protected void tickGame() {
		capitol.tick();
		if (runTicks == graceTicks() && graceTicks() > 0) {
			forEveryone(p -> {
				Msg.send(p, "hg.grace_over");
				Msg.sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1.4f);
			});
		}
		int refill = (int) game().setting("refillSeconds", 600) * 20;
		if (refill > 0 && runTicks == refill) refill();
		if (runTicks % 20 != 0) return;
		double size = shrink.sizeAt(borderTicks());
		if (shrink.shrinking(borderTicks()) && !announcedShrink) {
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

	/**
	 * During the peace nobody is anyone's enemy: bots loot instead of swinging at people they
	 * cannot hurt.
	 */
	@Override
	public boolean isEnemy(UUID a, UUID b) {
		return !inGrace() && super.isEnemy(a, b);
	}

	@Override
	public boolean isRival(UUID a, UUID b) {
		return super.isEnemy(a, b);
	}

	/**
	 * Nobody hurts anyone in the first half minute; the rest (falls, fire, the border) hurts from the start. The night's
	 * tribute plays over the plaza while the fighting goes on.
	 */
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
		capitol.killed(victim, killer);
		victim.getInventory().dropAll();
		int left = Math.max(0, alive.size() - 1);
		String who = name(victim.getUUID());
		// the cannon for everyone, and for the fallen what they hear themselves
		net.battlehub.fx.Sfx.play(victim, net.battlehub.fx.Sfx.FALLEN);
		forEveryone(p -> {
			Msg.send(p, "hg.cannon", who, left);
			if (p != victim) net.battlehub.fx.Sfx.play(p, net.battlehub.fx.Sfx.CANNON);
		});
		eliminate(victim);
		// the fallen can still sponsor someone
		offerSponsoring(victim);
	}

	/** Damage to another tribute wins the sponsors' favour. */
	@Override
	public void recordHit(ServerPlayer attacker, ServerPlayer victim, float amount, boolean arrow) {
		super.recordHit(attacker, victim, amount, arrow);
		if (phase == Phase.RUNNING && attacker != null && attacker != victim && alive.contains(attacker.getUUID())) capitol.hit(attacker, amount);
	}

	@Override
	protected void onClosed() {
		capitol.end();
	}

	/**
	 * Locked loot (a vault needs a key, a crate five seconds of sneaking, the Capitol's chest the Feast), and power-ups
	 * used on a block as in the air.
	 */
	@Override
	public net.minecraft.world.InteractionResult onUseBlock(ServerPlayer p, net.minecraft.world.phys.BlockHitResult hit, ItemStack held) {
		if (phase != Phase.RUNNING || !active(p.getUUID())) return net.minecraft.world.InteractionResult.PASS;
		BlockPos pos = hit.getBlockPos();
		if (arena.level().getBlockEntity(pos) instanceof Container) return capitol.useBlock(p, pos, held);
		String power = net.battlehub.games.hunger.PowerUps.id(held);
		if (power != null && !"mine".equals(power) && capitol.useItem(p, held)) return net.minecraft.world.InteractionResult.SUCCESS;
		return net.minecraft.world.InteractionResult.PASS;
	}

	@Override
	public net.minecraft.world.InteractionResult onUseItem(ServerPlayer p, ItemStack held) {
		if (phase != Phase.RUNNING || !active(p.getUUID())) return net.minecraft.world.InteractionResult.PASS;
		return capitol.useItem(p, held) ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.PASS;
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
		capitol.placed(p, target, held);
		return true;
	}

	/** The city stays standing: only what players put down, and leaves, cobwebs and plants, can be broken. */
	@Override
	public boolean onBreakBlock(ServerPlayer p, BlockPos pos, BlockState state) {
		if (phase != Phase.RUNNING || !active(p.getUUID())) return false;
		if (placed.remove(pos)) {
			capitol.broken(pos);
			return true;
		}
		return state.is(BlockTags.LEAVES) || state.is(Blocks.COBWEB) || state.canBeReplaced();
	}

	@Override
	public Component status(ServerPlayer viewer) {
		int d = capitol.tributes().district(viewer.getUUID());
		return d > 0 ? Lang.tr(viewer, "hg.status_district", d, alive.size(), (int) Math.round(borderSize()))
				: Lang.tr(viewer, "hg.status", alive.size(), (int) Math.round(borderSize()));
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
			botUsing.remove(bot.getUUID());
			return net.battlehub.core.ai.Objective.attack(v3(centre), "inside the border");
		}
		// away from what the Gamemakers sent (fire, fog, a swarm, mutts, lightning)
		Vec3 danger = capitol.gamemakers().danger(bot.position());
		if (danger != null) {
			Vec3 away = bot.position().subtract(danger);
			away = away.horizontalDistanceSqr() < 0.01 ? new Vec3(1, 0, 0) : new Vec3(away.x, 0, away.z).normalize();
			Vec3 to = bot.position().add(away.scale(16));
			if (BorderShrink.outside(to.x, to.z, cx, cz, size - 8) > 0) to = centre;
			botUsing.remove(bot.getUUID());
			return net.battlehub.core.ai.Objective.attack(v3(to), "away from the Gamemakers' trap");
		}
		// its own parachute, the Feast, a vault it has a key for
		BlockPos gift = capitol.botTarget(bot);
		if (gift != null && fresh(bot, gift)) return using(bot, gift, "the Capitol's gifts");
		BlockPos crate = capitol.vaults().nearestCrate(bot.position(), 20);
		if (crate != null && Math.abs(crate.getY() - bot.getY()) <= 4 && fresh(bot, crate)) return using(bot, crate, "a sealed crate");
		BlockPos chest = botChest(bot, size);
		if (chest != null && fresh(bot, chest)) return using(bot, chest, "a chest");
		botUsing.remove(bot.getUUID());
		if (Math.hypot(bot.getX() - cx, bot.getZ() - cz) > 20) return net.battlehub.core.ai.Objective.attack(v3(centre), "the plaza");
		return null;
	}

	/** The container each bot goes to open now (its route ends where it sees it, not outside a window). */
	private final Map<UUID, BlockPos> botUsing = new HashMap<>();

	/** Going to a container: to the spot in front of it, and the route ends where it is in sight and within reach. */
	private net.battlehub.core.ai.Objective using(ServerPlayer bot, BlockPos pos, String what) {
		botUsing.put(bot.getUUID(), pos.immutable());
		return net.battlehub.core.ai.Objective.attack(v3(Vec3.atBottomCenterOf(front(pos))), what);
	}

	@Override
	public BlockPos botUseTarget(ServerPlayer bot) {
		return botUsing.get(bot.getUUID());
	}

	/** A bot forgets which containers it gave up on (self-test: a fresh start somewhere else). */
	public void botForgetGiveUps(UUID id) {
		botGaveUp.remove(id);
		botAim.remove(id);
		botUsing.remove(id);
	}

	/** No way to the container it goes for (behind walls, up where no path goes): it lets that one go for a while. */
	@Override
	public void botNoRoute(ServerPlayer bot) {
		BlockPos pos = botUsing.remove(bot.getUUID());
		if (pos == null) return;
		botGaveUp.computeIfAbsent(bot.getUUID(), k -> new HashMap<>()).put(pos, runTicks + GIVE_UP_FOR);
		botAim.remove(bot.getUUID());
		botGaveUps++;
	}

	/** What each bot goes for now, since when, and what it gave up on (until which tick). */
	private final Map<UUID, BlockPos> botAim = new HashMap<>();
	private final Map<UUID, Integer> botAimSince = new HashMap<>();
	private final Map<UUID, Map<BlockPos, Integer>> botGaveUp = new HashMap<>();
	/** How long a bot tries to get to one container before it gives up on it for a while (ticks). */
	static final int GIVE_UP_AFTER = 20 * 20, GIVE_UP_FOR = 90 * 20;

	/**
	 * Whether {@code bot} still goes for {@code pos}: false once it has tried to get there for {@link #GIVE_UP_AFTER}
	 * ticks (it cannot reach it: behind a wall, up where no path goes); then it leaves it for {@link #GIVE_UP_FOR}.
	 */
	private boolean fresh(ServerPlayer bot, BlockPos pos) {
		Map<BlockPos, Integer> gaveUp = botGaveUp.computeIfAbsent(bot.getUUID(), k -> new HashMap<>());
		gaveUp.values().removeIf(until -> until <= runTicks);
		if (gaveUp.containsKey(pos)) return false;
		if (!pos.equals(botAim.get(bot.getUUID()))) {
			botAim.put(bot.getUUID(), pos.immutable());
			botAimSince.put(bot.getUUID(), runTicks);
			return true;
		}
		if (runTicks - botAimSince.getOrDefault(bot.getUUID(), runTicks) < GIVE_UP_AFTER || net.battlehub.bot.Looting.busy(bot) || botsCracking.contains(bot.getUUID())) return true;
		gaveUp.put(pos.immutable(), runTicks + GIVE_UP_FOR);
		botAim.remove(bot.getUUID());
		botGaveUps++;
		return false;
	}

	private int botGaveUps;

	/** How often bots gave up on a container they could not reach (self-test, inspect). */
	public int botGaveUps() {
		return botGaveUps;
	}

	/** Where to stand to open a container: in front of it when it has a front. */
	private BlockPos front(BlockPos pos) {
		BlockState st = arena.level().getBlockState(pos);
		return st.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? pos.relative(st.getValue(BlockStateProperties.HORIZONTAL_FACING)) : pos;
	}

	private BlockPos botChest(ServerPlayer bot, double size) {
		Set<BlockPos> done = botLooted.getOrDefault(bot.getUUID(), Set.of());
		Map<BlockPos, Integer> gaveUp = botGaveUp.getOrDefault(bot.getUUID(), Map.of());
		double cx = arena.origin().getX() + 0.5, cz = arena.origin().getZ() + 0.5;
		// the nearest one, on about its own level (a chest up many floors waits until it walks past); not a locked one,
		// not one it could not get to a moment ago
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (String mark : CHESTS) {
			for (BlockPos pos : arena.marks(mark)) {
				if (done.contains(pos) || Math.abs(pos.getY() - bot.getY()) > 6 || capitol.vaults().locked(pos)
						|| gaveUp.getOrDefault(pos, 0) > runTicks) continue;
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

	/** Bots breaking a crate's seal now: they sneak beside it and stand still. */
	private final Set<UUID> botsCracking = new HashSet<>();

	/**
	 * At a chest it can see (within reach, nothing in between) it opens it and loots it as a player does, a stack at a
	 * time, and puts on better armor afterwards (bot/Looting); hungry, it eats. With a key it opens a vault; beside a
	 * sealed crate it sneaks until the seal breaks; it drinks a salve when hurt.
	 */
	@Override
	public void botTick(ServerPlayer bot) {
		if (arena == null || phase != Phase.RUNNING) return;
		// every tick: sneaking beside a sealed crate (the brain lets go of the keys each tick)
		BlockPos crate = capitol.vaults().nearestCrate(bot.position(), net.battlehub.core.hunger.Crack.REACH - 0.3);
		boolean calm = bot.hurtTime == 0 && !enemyNear(bot, 5);
		if (crate != null && calm && !net.battlehub.bot.Looting.busy(bot) && net.battlehub.games.hunger.Vaults.sees(bot, crate)) {
			bot.setShiftKeyDown(true);
			botsCracking.add(bot.getUUID());
		} else {
			botsCracking.remove(bot.getUUID());
		}
		if (runTicks % 5 != 0) return;
		if (runTicks % 40 == 0 && bot.getFoodData().getFoodLevel() < 14) eat(bot);
		if (runTicks % 20 == 0) capitol.botUsePower(bot);
		if (net.battlehub.bot.Looting.busy(bot)) return;
		Set<BlockPos> done = botLooted.computeIfAbsent(bot.getUUID(), k -> new HashSet<>());
		List<BlockPos> extra = new ArrayList<>(capitol.sponsors().landed().keySet());
		extra.addAll(capitol.vaults().feastChests());
		for (String mark : CHESTS) {
			for (BlockPos pos : arena.marks(mark)) {
				if (tryLoot(bot, pos, mark, done)) return;
			}
		}
		for (BlockPos pos : extra) {
			if (tryLoot(bot, pos, "gift", done)) return;
		}
	}

	/** Opens one container within reach if it can (a vault with a key first); true when it started looting. */
	private boolean tryLoot(ServerPlayer bot, BlockPos pos, String mark, Set<BlockPos> done) {
		if (done.contains(pos) || !net.battlehub.bot.Looting.canReach(bot, pos)) return false;
		if (capitol.vaults().locked(pos) && !capitol.botUnlock(bot, pos)) return false;
		if (!hasLoot(pos)) {
			done.add(pos);
			return false;
		}
		net.battlehub.bot.Looting.start(bot, pos, () -> {
			done.add(pos);
			botLoots++;
			botLootsBy.merge(mark, 1, Integer::sum);
		});
		return true;
	}

	private boolean enemyNear(ServerPlayer bot, double range) {
		for (ServerPlayer p : tributes()) {
			if (p != bot && isEnemy(bot.getUUID(), p.getUUID()) && p.distanceTo(bot) < range) return true;
		}
		return false;
	}

	@Override
	public boolean botHolds(ServerPlayer bot) {
		return botsCracking.contains(bot.getUUID());
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
