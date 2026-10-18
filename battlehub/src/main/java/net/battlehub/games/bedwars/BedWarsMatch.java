package net.battlehub.games.bedwars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.battlehub.arena.Arena;
import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.BedWarsLayout;
import net.battlehub.config.BedWarsConfig;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.core.StateMachine;
import net.battlehub.core.bedwars.BedWarsRules;
import net.battlehub.core.bedwars.EventSchedule;
import net.battlehub.core.bedwars.Resource;
import net.battlehub.core.bedwars.Shop;
import net.battlehub.core.bedwars.TeamState;
import net.battlehub.games.Npcs;
import net.battlehub.games.PartyMatch;
import net.battlehub.gui.BedWarsShopMenu;
import net.battlehub.lang.Lang;
import net.battlehub.progression.TitleService;
import net.battlehub.ui.MatchBossBar;
import net.battlehub.ui.Sidebar;
import net.battlehub.util.Icon;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bed Wars: solo (everyone for themselves) or duos, up to eight islands. Generators drop iron, gold, diamonds and emeralds; the shop
 * turns them into blocks, weapons, armour and tools. A team's bed brings its players back after a death; once the
 * bed is broken, a death is final. The last team with players left wins.
 *
 * <p>Stages (on top of the match lifecycle): COUNTDOWN (the lobby countdown is over, players frozen on their
 * island) → ACTIVE → SUDDEN_DEATH (every bed breaks, later everyone glows) → PODIUM (the winners on a podium).
 * Rules live in {@code core.bedwars}, numbers in bedwars.json, the screens in {@link BedWarsUi} and
 * {@link BedWarsShopMenu}; this class glues them to the world.
 */
public final class BedWarsMatch extends PartyMatch implements BedWarsShop.Teams {
	public enum Stage { COUNTDOWN, ACTIVE, SUDDEN_DEATH, PODIUM }

	/** Leather colours per team, matching {@link BedWarsLayout#COLORS}. */
	private static final int[] LEATHER = {0xB02E26, 0x3C44AA, 0x80C71F, 0xFED83D, 0x3AB3DA, 0xF9FFFE, 0xF38BAA, 0x474F52};
	private static final String[] CODES = {"&c", "&9", "&a", "&e", "&b", "&f", "&d", "&8"};
	private static final int FLAGS = 2 | 16 | 32;

	private final BedWarsConfig cfg;
	private final List<TeamState> teams = new ArrayList<>();
	private final Map<UUID, Integer> teamOf = new HashMap<>();
	private final Map<UUID, Integer> slotInTeam = new HashMap<>();
	private final StateMachine<Stage> stage = new StateMachine<>(Stage.class, Stage.COUNTDOWN);
	private final EventSchedule schedule;
	private final BedWarsShop shop;
	private final Npcs npcs = new Npcs();
	private final MatchBossBar bossBar = new MatchBossBar(BossEvent.BossBarColor.WHITE);
	private final Sidebar sidebar;
	private final BedWarsUi ui;
	/** Both halves of every bed (world position) and whose bed it is. */
	private final Map<Long, Integer> bedBlocks = new HashMap<>();
	private final Map<UUID, Integer> finalKills = new HashMap<>();
	private final Map<UUID, Integer> bedsBroken = new HashMap<>();
	private final List<Tnt> tnt = new ArrayList<>();
	/** Block items players may place: what the shop sells as blocks. */
	private final Set<String> placeable = new HashSet<>();
	private Generators gens;
	private BuildRules build;
	private boolean setUp;
	private boolean glowing;

	private record Tnt(PrimedTnt entity, int explodeAt) {}

	/** "solo" or "duo", as voted in the lobby. */
	public final String format;

	public BedWarsMatch(int id, MinecraftServer server, List<ServerPlayer> players) {
		this(id, server, players, "");
	}

	public BedWarsMatch(int id, MinecraftServer server, List<ServerPlayer> players, String formatVote) {
		super(id, GameMode.BED_WARS, server, players);
		this.cfg = ConfigManager.bedwars();
		this.format = BedWarsRules.SOLO.equals(formatVote) || BedWarsRules.DUO.equals(formatVote) ? formatVote : cfg.defaultFormat;
		int count = BedWarsRules.teamCount(participants.size(), BedWarsRules.teamSize(format, cfg.duoTeamSize),
				Math.min(cfg.maxTeams, BedWarsLayout.COLORS.length));
		// Friends who queued as a party end up on the same team when there is room
		List<List<UUID>> members = net.battlehub.core.TeamSplit.split(participants, Math.max(1, count),
				net.battlehub.social.Groups.among(participants), new java.util.Random(random.nextLong()));
		for (int t = 0; t < members.size(); t++) {
			for (int i = 0; i < members.get(t).size(); i++) {
				slotInTeam.put(members.get(t).get(i), i);
				teamOf.put(members.get(t).get(i), t);
			}
		}
		for (int i = 0; i < members.size(); i++) {
			teams.add(new TeamState(i, members.get(i)));
		}
		List<EventSchedule.Event> events = new ArrayList<>();
		for (BedWarsConfig.Event e : cfg.events) {
			events.add(new EventSchedule.Event(e.atSeconds, e.type, e.value));
		}
		this.schedule = new EventSchedule(events);
		this.shop = new BedWarsShop(cfg, this);
		for (BedWarsConfig.Offer o : cfg.shop) {
			if ("blocks".equals(o.category)) {
				for (String c : BedWarsLayout.COLORS) placeable.add(o.item.replace("{team}", c));
			}
		}
		this.sidebar = new Sidebar("bw" + id);
		this.ui = new BedWarsUi(this);
		stage.allow(Stage.COUNTDOWN, Stage.ACTIVE, Stage.PODIUM)
				.allow(Stage.ACTIVE, Stage.SUDDEN_DEATH, Stage.PODIUM)
				.allow(Stage.SUDDEN_DEATH, Stage.PODIUM)
				.allow(Stage.PODIUM)
				.onEnter((s, from) -> {
					if (s == Stage.SUDDEN_DEATH) suddenDeath();
				});
	}

	// ------------------------------------------------------------------ queries (UI, shop, tests)

	public List<TeamState> teams() { return teams; }
	public Stage stage() { return stage.state(); }
	public BedWarsShop shop() { return shop; }
	public Generators generators() { return gens; }
	public BuildRules buildRules() { return build; }
	public EventSchedule schedule() { return schedule; }
	public BedWarsConfig settings() { return cfg; }
	public int finalKills(UUID id) { return finalKills.getOrDefault(id, 0); }
	public int bedsBroken(UUID id) { return bedsBroken.getOrDefault(id, 0); }
	public int runTicks() { return runTicks; }

	@Override
	public int teamOf(UUID id) {
		return teamOf.getOrDefault(id, -1);
	}

	public TeamState team(UUID id) {
		int t = teamOf(id);
		return t < 0 ? null : teams.get(t);
	}

	@Override
	public String color(int team) {
		return BedWarsLayout.COLORS[Math.floorMod(team, BedWarsLayout.COLORS.length)];
	}

	public String code(int team) {
		return CODES[Math.floorMod(team, CODES.length)];
	}

	@Override
	public int leather(int team) {
		return LEATHER[Math.floorMod(team, LEATHER.length)];
	}

	public Component teamName(ServerPlayer viewer, int team) {
		return Lang.tr(viewer, "bedwars.team." + color(team));
	}

	@Override
	public List<ServerPlayer> online(int team) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID id : teams.get(team).members()) {
			ServerPlayer p = player(id);
			if (p != null && !left.contains(id)) out.add(p);
		}
		return out;
	}

	/** The bed of a team: the foot block (or null before the arena exists). */
	public BlockPos bed(int team) {
		return arena == null ? null : arena.mark("bed_" + team);
	}

	@Override
	public boolean isEnemy(UUID a, UUID b) {
		return teamOf(a) != teamOf(b);
	}

	/** Bots guard their own bed while it stands; after that they go hunting. */
	@Override
	public net.minecraft.world.phys.Vec3 botObjective(ServerPlayer bot) {
		TeamState t = team(bot.getUUID());
		BlockPos b = t == null ? null : bed(t.index);
		if (b == null || !t.bedAlive()) {
			return null;
		}
		return new net.minecraft.world.phys.Vec3(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
	}

	/**
	 * Bots first get blocks (iron from their generator, wool from their shop, at the shop's price), then one of a
	 * team keeps home while the bed stands and the others go for the nearest enemy bed; with no beds left they hunt.
	 */
	@Override
	public net.battlehub.core.ai.Objective botGoal(ServerPlayer bot) {
		TeamState t = team(bot.getUUID());
		if (t == null || arena == null) return null;
		BlockPos shopAt = arena.mark("shop_" + t.index), gen = arena.mark("gen_" + t.index);
		if (botBlocks(bot) < 8 && shopAt != null && gen != null) {
			Shop.Offer wool = shop.offer("wool");
			if (wool != null && shop.check(bot, wool) == Shop.Result.BOUGHT) {
				return net.battlehub.core.ai.Objective.attack(v3(Vec3.atBottomCenterOf(shopAt)), "the shop");
			}
			return net.battlehub.core.ai.Objective.attack(v3(Vec3.atBottomCenterOf(gen)), "our generator");
		}
		BlockPos own = bed(t.index);
		if (t.bedAlive() && own != null && botRole(bot, true, Vec3.atCenterOf(own)) == net.battlehub.core.ai.Roles.Role.DEFENDER) {
			return net.battlehub.core.ai.Objective.defend(v3(Vec3.atBottomCenterOf(own)), 3, 10, "our bed");
		}
		TeamState target = botTargetTeam(bot);
		if (target == null) return null;
		return net.battlehub.core.ai.Objective.attack(v3(Vec3.atBottomCenterOf(bed(target.index))), "the " + color(target.index) + " bed");
	}

	/** The nearest other team whose bed still stands, or null. */
	private TeamState botTargetTeam(ServerPlayer bot) {
		TeamState mine = team(bot.getUUID());
		TeamState best = null;
		double bestD = Double.MAX_VALUE;
		for (TeamState o : teams) {
			if (o == mine || !o.bedAlive() || bed(o.index) == null) continue;
			double d = bot.position().distanceToSqr(Vec3.atCenterOf(bed(o.index)));
			if (d < bestD) {
				bestD = d;
				best = o;
			}
		}
		return best;
	}

	@Override
	public BlockPos botBreakTarget(ServerPlayer bot) {
		TeamState target = botTargetTeam(bot);
		return target == null ? null : bed(target.index);
	}

	@Override
	public boolean botMayBuild(BlockPos pos) {
		return build != null && build.mayPlace(pos) == BuildRules.Verdict.OK;
	}

	@Override
	public boolean botMayBreak(BlockPos pos) {
		return build != null && build.isPlaced(pos);
	}

	/** At its own shop with iron to spare and few blocks: a bot buys wool, as a player would through the menu. */
	@Override
	public void botTick(ServerPlayer bot) {
		if (runTicks % 10 != 0 || arena == null) return;
		TeamState t = team(bot.getUUID());
		if (t == null || botBlocks(bot) >= 16) return;
		BlockPos at = arena.mark("shop_" + t.index);
		if (at != null && bot.position().distanceTo(Vec3.atBottomCenterOf(at)) < 3.5) {
			shop.buy(bot, "wool");
		}
	}

	/** Blocks a player carries that may be placed here. */
	private int botBlocks(ServerPlayer p) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.getItem() instanceof BlockItem && placeable.contains(Ids.key(s.getItem()))) n += s.getCount();
		}
		return n;
	}

	public boolean isActive(UUID id) {
		return active(id);
	}

	public boolean isWaitingToRespawn(UUID id) {
		return respawnAt.containsKey(id);
	}

	// ------------------------------------------------------------------ setup

	@Override
	protected Blueprint design(Theme theme) {
		return BedWarsLayout.design(theme, random, teams.size(), cfg.buildHeight, cfg.buildDepth);
	}

	@Override
	protected int leatherColor(UUID id) {
		return leather(teamOf(id));
	}

	@Override
	protected int respawnTicks() {
		return Math.max(1, cfg.respawnSeconds * 20);
	}

	@Override
	protected int endDelayTicks() {
		return cfg.podiumSeconds * 20;
	}

	/** Everyone of a team starts on their island, a little apart so nobody stands inside another. */
	@Override
	protected Arena.Point spawnPoint(UUID id, int index) {
		int t = Math.max(0, teamOf(id));
		Arena.Point base = arena.spawn(t);
		int slot = slotInTeam.getOrDefault(id, 0);
		double[][] offsets = {{0, 0}, {1.2, 0}, {0, 1.2}, {1.2, 1.2}, {-1.2, 0}, {0, -1.2}, {-1.2, -1.2}, {1.2, -1.2}};
		double[] o = offsets[slot % offsets.length];
		return new Arena.Point(base.pos().add(o[0], 0, o[1]), base.yaw(), 0);
	}

	@Override
	protected void resetForPlay(ServerPlayer p) {
		super.resetForPlay(p);
		p.setGameMode(GameType.SURVIVAL);
	}

	@Override
	protected void onArrived(ServerPlayer p, int index) {
		setUp();
		p.setGameMode(GameType.SURVIVAL);
		shop.equip(p);
		int t = teamOf(p.getUUID());
		TitleService.setOverride(p, Lang.tr(p, "bedwars.prefix", teamName(p, t)));
		Msg.send(p, "bedwars.your_team", teamName(p, t), teams.get(t).members().size());
		bossBar.show(p);
	}

	/** Generators, build rules, beds and shopkeepers, once the arena is there. */
	private void setUp() {
		if (setUp) {
			return;
		}
		setUp = true;
		ServerLevel level = arena.level();
		gens = new Generators(level);
		build = new BuildRules(level, arena.mark("build_min"), arena.mark("build_max"), cfg.protectionRadius).area(arena::inPlayArea);
		for (int t = 0; t < teams.size(); t++) {
			BlockPos gen = arena.mark("gen_" + t);
			gens.add(gen, Resource.IRON, t, cfg);
			gens.add(gen, Resource.GOLD, t, cfg);
			build.protect(gen);
			build.protect(arena.mark("spawn_" + t));
			BlockPos shopAt = arena.mark("shop_" + t);
			BlockPos shopBlock = arena.mark("shopblock_" + t);
			build.protect(shopAt);
			build.protect(shopBlock);
			BlockPos foot = arena.mark("bed_" + t);
			bedBlocks.put(foot.asLong(), t);
			BlockState st = level.getBlockState(foot);
			if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
				bedBlocks.put(foot.relative(st.getValue(BlockStateProperties.HORIZONTAL_FACING)).asLong(), t);
			}
			// The shopkeeper looks towards the middle of the island
			BlockPos spawn = arena.mark("spawn_" + t);
			Vec3 at = Vec3.atBottomCenterOf(shopAt);
			float yaw = (float) Math.toDegrees(Math.atan2(-(spawn.getX() - shopAt.getX()), spawn.getZ() - shopAt.getZ()));
			npcs.spawn(level, "minecraft:villager", at, yaw, Lang.tr("bedwars.shopkeeper"));
		}
		for (BlockPos p : arena.marks("gen_diamond")) {
			gens.add(p, Resource.DIAMOND, -1, cfg);
			build.protect(p);
		}
		for (BlockPos p : arena.marks("gen_emerald")) {
			gens.add(p, Resource.EMERALD, -1, cfg);
			build.protect(p);
		}
	}

	@Override
	protected void onGo() {
		stage.go(Stage.ACTIVE);
		// Players who never arrived do not keep a team alive
		for (UUID id : left) {
			TeamState t = team(id);
			if (t != null) t.left(id);
		}
		forEveryone(p -> {
			Msg.send(p, "bedwars.format", Lang.tr(p, "bedwars.format." + format), teams.size());
			Msg.send(p, "bedwars.start");
		});
		updateUi();
		checkEnd();
	}

	// ------------------------------------------------------------------ game loop

	@Override
	protected void tickGame() {
		tickRespawns();
		stage.tick();
		build.tick(runTicks);
		gens.tick();
		tickTnt();
		if (phase != Phase.RUNNING) {
			return;
		}
		if (runTicks % 20 == 0) {
			for (EventSchedule.Event e : schedule.due(runSeconds())) {
				event(e);
				if (phase != Phase.RUNNING) return;
			}
			npcs.tick();
			updateUi();
		}
	}

	private void event(EventSchedule.Event e) {
		switch (e.type()) {
			case "diamond", "emerald" -> {
				Resource r = Resource.byId(e.type());
				gens.setTier(r, e.value());
				forEveryone(p -> {
					Msg.send(p, "bedwars.event.generator", ui.resourceName(p, r), BedWarsUi.roman(e.value()));
					Msg.sound(p, SoundEvents.NOTE_BLOCK_CHIME, 0.8f, 1.2f);
				});
			}
			case "sudden_death" -> stage.go(Stage.SUDDEN_DEATH);
			case "glow" -> {
				glowing = true;
				for (UUID id : alive) {
					ServerPlayer p = player(id);
					if (p != null && active(id)) glow(p);
				}
				forEveryone(p -> {
					Msg.send(p, "bedwars.event.glow");
					Msg.sound(p, SoundEvents.ELDER_GUARDIAN_CURSE, 0.5f, 1f);
				});
			}
			default -> { }
		}
	}

	/** Every bed still standing breaks: from now on every death is final. */
	private void suddenDeath() {
		for (TeamState t : teams) {
			if (t.destroyBed()) {
				removeBed(t.index);
			}
		}
		forEveryone(p -> {
			Msg.title(p, Lang.tr(p, "bedwars.sudden_death_title"), Lang.tr(p, "bedwars.sudden_death_sub"), 5, 60, 15);
			Msg.send(p, "bedwars.sudden_death");
			Msg.sound(p, SoundEvents.ENDER_DRAGON_GROWL, 0.7f, 1f);
		});
		updateUi();
	}

	private void glow(ServerPlayer p) {
		p.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
	}

	private void updateUi() {
		for (UUID id : participants) {
			ServerPlayer p = player(id);
			if (p != null && !left.contains(id)) ui.show(p, sidebar, bossBar);
		}
		forWatchers(p -> ui.show(p, sidebar, bossBar));
	}

	// ------------------------------------------------------------------ deaths and respawns

	@Override
	protected boolean damageAllowed(ServerPlayer victim, DamageSource source, ServerPlayer attacker) {
		return attacker == null || attacker == victim || teamOf(attacker.getUUID()) != teamOf(victim.getUUID());
	}

	@Override
	protected void onKilled(ServerPlayer victim, ServerPlayer killer) {
		UUID v = victim.getUUID();
		TeamState team = team(v);
		victim.closeContainer();
		List<ItemStack> loot = BedWarsShop.takeResources(victim);
		if (killer != null && cfg.killerTakesResources && !loot.isEmpty()) {
			Map<Resource, Integer> got = new java.util.EnumMap<>(Resource.class);
			for (ItemStack s : loot) {
				Resource r = Resource.byItem(Ids.key(s.getItem()));
				if (r != null) got.merge(r, s.getCount(), Integer::sum);
				BedWarsShop.add(killer, s);
			}
			killer.inventoryMenu.sendAllDataToRemote();
			got.forEach((r, n) -> Msg.send(killer, "bedwars.loot", n, ui.resourceName(killer, r)));
		}
		victim.getInventory().clearContent();
		shop.downgradeTools(v);
		if (team != null && team.died(v)) {
			scheduleRespawn(victim);
			return;
		}
		// No bed: out for good
		if (killer != null) {
			finalKills.merge(killer.getUUID(), 1, Integer::sum);
			TeamState kt = team(killer.getUUID());
			if (kt != null) kt.addFinalKill();
		}
		forEveryone(p -> Msg.send(p, "bedwars.final_kill", name(v)));
		eliminate(victim);
		if (team != null && team.isOut()) {
			int t = team.index;
			forEveryone(p -> Msg.send(p, "bedwars.team_out", teamName(p, t)));
		}
		updateUi();
	}

	@Override
	protected void onRespawned(ServerPlayer p) {
		p.setGameMode(GameType.SURVIVAL);
		shop.equip(p);
		if (glowing) {
			glow(p);
		}
	}

	@Override
	protected void checkEnd() {
		if (phase != Phase.RUNNING && phase != Phase.COUNTDOWN) {
			return;
		}
		if (BedWarsRules.over(teams)) {
			TeamState w = BedWarsRules.winner(teams);
			finish(w == null ? List.of() : w.members());
		}
	}

	@Override
	protected void onTimeUp() {
		TeamState w = BedWarsRules.onTimeUp(teams);
		finish(w == null ? List.of() : w.members());
	}

	@Override
	protected void onLeft(UUID id) {
		super.onLeft(id);
		TeamState t = team(id);
		if (t != null) {
			t.left(id);
			if (t.isOut() && (phase == Phase.RUNNING || phase == Phase.COUNTDOWN)) {
				int idx = t.index;
				forEveryone(p -> Msg.send(p, "bedwars.team_out", teamName(p, idx)));
			}
		}
		sidebar.forget(id);
	}

	// ------------------------------------------------------------------ the end: podium

	@Override
	protected void finish(List<UUID> winners) {
		if (phase == Phase.ENDING || phase == Phase.CLOSED) {
			return;
		}
		super.finish(winners);
		stage.go(Stage.PODIUM);
		podium(winners);
	}

	/** The winners stand on a golden podium above the middle; everyone else watches from above. */
	private void podium(List<UUID> winners) {
		for (Tnt t : tnt) t.entity().discard();
		tnt.clear();
		ServerLevel level = arena.level();
		BlockPos top = arena.mark("podium");
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (dx * dx + dz * dz <= 5) {
					level.setBlock(top.offset(dx, -1, dz), Blocks.GOLD_BLOCK.defaultBlockState(), FLAGS);
				}
			}
		}
		int i = 0;
		for (UUID id : participants) {
			ServerPlayer p = player(id);
			if (p == null || left.contains(id)) continue;
			p.removeEffect(MobEffects.GLOWING);
			p.closeContainer();
			if (winners.contains(id)) {
				p.setGameMode(GameType.ADVENTURE);
				double a = i++ * Math.PI * 2 / Math.max(1, winners.size());
				double r = winners.size() > 1 ? 1.2 : 0;
				teleport(p, new Arena.Point(Vec3.atBottomCenterOf(top).add(Math.cos(a) * r, 0, Math.sin(a) * r), 0, 0));
			} else {
				p.setGameMode(GameType.SPECTATOR);
				teleport(p, arena.spectator());
			}
		}
		updateUi();
	}

	@Override
	protected void onRestored(ServerPlayer p) {
		TitleService.clearOverride(p);
		sidebar.hide(p);
		bossBar.hide(p);
	}

	@Override
	protected void onWatcherAdded(ServerPlayer p) {
		bossBar.show(p);
		ui.show(p, sidebar, bossBar);
	}

	@Override
	protected void onClosed() {
		npcs.removeAll();
		bossBar.clear();
		for (UUID id : sidebar.viewers()) {
			ServerPlayer p = player(id);
			if (p != null) sidebar.hide(p);
		}
		tnt.clear();
	}

	// ------------------------------------------------------------------ blocks

	@Override
	public boolean onBreakBlock(ServerPlayer p, BlockPos pos, BlockState state) {
		if (phase != Phase.RUNNING || !active(p.getUUID()) || build == null) {
			return false;
		}
		Integer bedTeam = bedBlocks.get(pos.asLong());
		if (bedTeam != null) {
			breakBed(p, bedTeam);
			return false;
		}
		if (build.isPlaced(pos)) {
			build.forget(pos);
			return true;
		}
		Msg.actionBar(p, "bedwars.cannot_break");
		return false;
	}

	/** {@code p} breaks the bed of {@code team}: not their own, and only once. */
	public boolean breakBed(ServerPlayer p, int team) {
		int mine = teamOf(p.getUUID());
		if (team == mine) {
			Msg.actionBar(p, "bedwars.own_bed");
			return false;
		}
		TeamState t = teams.get(team);
		if (!t.destroyBed()) {
			return false;
		}
		removeBed(team);
		bedsBroken.merge(p.getUUID(), 1, Integer::sum);
		if (mine >= 0) teams.get(mine).addBedBroken();
		forEveryone(o -> {
			Msg.send(o, "bedwars.bed_destroyed", teamName(o, team), name(p.getUUID()));
			Msg.sound(o, SoundEvents.ENDER_DRAGON_GROWL, 0.4f, 1.4f);
		});
		for (ServerPlayer m : online(team)) {
			Msg.title(m, Lang.tr(m, "bedwars.bed_gone_title"), Lang.tr(m, "bedwars.bed_gone_sub"), 5, 50, 15);
			Msg.sound(m, SoundEvents.WITHER_DEATH, 0.5f, 1.2f);
		}
		updateUi();
		return true;
	}

	private void removeBed(int team) {
		ServerLevel level = arena.level();
		for (Map.Entry<Long, Integer> e : bedBlocks.entrySet()) {
			if (e.getValue() == team) {
				BlockPos p = BlockPos.of(e.getKey());
				level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
				level.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.02);
			}
		}
	}

	@Override
	public boolean onPlaceBlock(ServerPlayer p, BlockPos target, ItemStack held) {
		if (phase != Phase.RUNNING || !active(p.getUUID()) || build == null) {
			return false;
		}
		BuildRules.Verdict v = build.mayPlace(target);
		if (v != BuildRules.Verdict.OK) {
			Msg.actionBar(p, v == BuildRules.Verdict.OUTSIDE ? "bedwars.build_outside" : "bedwars.build_protected");
			return false;
		}
		if (held.is(Items.TNT)) {
			igniteTnt(p, target, held);
			return false;
		}
		boolean bucket = held.getItem() instanceof BucketItem;
		if (!bucket && !(held.getItem() instanceof BlockItem && placeable.contains(Ids.key(held.getItem())))) {
			Msg.actionBar(p, "bedwars.cannot_place");
			return false;
		}
		build.expect(target, runTicks);
		return true;
	}

	/** TNT lights itself when placed; it hurts players and breaks only blocks that players placed. */
	private void igniteTnt(ServerPlayer p, BlockPos target, ItemStack held) {
		if (!p.isCreative()) {
			held.shrink(1);
		}
		int fuse = cfg.tntFuseSeconds * 20;
		PrimedTnt e = new PrimedTnt(arena.level(), target.getX() + 0.5, target.getY(), target.getZ() + 0.5, p);
		// It never explodes by itself (that would break the map): the match sets it off
		e.setFuse(fuse + 40);
		arena.level().addFreshEntity(e);
		tnt.add(new Tnt(e, runTicks + fuse));
		p.inventoryMenu.sendAllDataToRemote();
	}

	private void tickTnt() {
		Iterator<Tnt> it = tnt.iterator();
		while (it.hasNext()) {
			Tnt t = it.next();
			if (runTicks < t.explodeAt()) continue;
			it.remove();
			PrimedTnt e = t.entity();
			if (!e.isAlive()) continue;
			Vec3 at = e.position();
			e.discard();
			arena.level().explode(e, at.x, at.y, at.z, (float) cfg.tntPower, Level.ExplosionInteraction.NONE);
			build.explode(BlockPos.containing(at), cfg.tntPower + 0.5);
		}
	}

	public int tntCount() {
		return tnt.size();
	}

	// ------------------------------------------------------------------ clicks: shop, beds

	@Override
	public InteractionResult onUseBlock(ServerPlayer p, BlockHitResult hit, ItemStack held) {
		BlockPos pos = hit.getBlockPos();
		for (int t = 0; t < teams.size(); t++) {
			if (arena != null && pos.equals(arena.mark("shopblock_" + t))) {
				openShop(p);
				return InteractionResult.SUCCESS;
			}
		}
		BlockState st = arena == null ? null : arena.level().getBlockState(pos);
		if (st != null && st.getBlock() instanceof BedBlock) {
			// Beds cannot be slept in (they would explode here); sneak to build against them
			return p.isShiftKeyDown() && held.getItem() instanceof BlockItem ? InteractionResult.PASS : InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}

	@Override
	public InteractionResult onUseEntity(ServerPlayer p, Entity target) {
		if (npcs.owns(target)) {
			openShop(p);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	public InteractionResult onAttackEntity(ServerPlayer p, Entity target) {
		if (npcs.owns(target)) {
			openShop(p);
			return InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}

	public void openShop(ServerPlayer p) {
		if (!active(p.getUUID()) || (phase != Phase.RUNNING && phase != Phase.COUNTDOWN)) {
			return;
		}
		new BedWarsShopMenu(p, this, BedWarsShop.CATEGORIES[0]).open();
	}

	/** A click in the shop. Only players in play, only while the game runs. */
	public Shop.Result buy(ServerPlayer p, String offerId) {
		if (!canShop(p)) {
			return null;
		}
		Shop.Result r = shop.buy(p, offerId);
		feedback(p, r);
		return r;
	}

	public Shop.Result buyUpgrade(ServerPlayer p, String upgrade) {
		if (!canShop(p)) {
			return null;
		}
		Shop.Result r = shop.buyUpgrade(p, upgrade);
		feedback(p, r);
		return r;
	}

	private boolean canShop(ServerPlayer p) {
		if (phase != Phase.RUNNING || !active(p.getUUID()) || p.hasDisconnected()) {
			Msg.actionBar(p, "bedwars.shop_closed");
			return false;
		}
		return true;
	}

	private void feedback(ServerPlayer p, Shop.Result r) {
		switch (r) {
			case BOUGHT -> Msg.sound(p, SoundEvents.NOTE_BLOCK_PLING, 0.7f, 1.6f);
			case TOO_EXPENSIVE -> {
				Msg.actionBar(p, "bedwars.too_expensive");
				Msg.sound(p, SoundEvents.VILLAGER_NO, 0.7f, 1f);
			}
			case ALREADY_OWNED -> Msg.actionBar(p, "bedwars.already_owned");
			case NO_SPACE -> {
				Msg.actionBar(p, "bedwars.no_space");
				Msg.sound(p, SoundEvents.VILLAGER_NO, 0.7f, 1f);
			}
		}
	}

	@Override
	public void upgraded(int team, String upgrade, int level, ServerPlayer buyer) {
		if ("forge".equals(upgrade)) {
			gens.setTeamTier(team, level + 1);
		}
		for (ServerPlayer m : online(team)) {
			Msg.send(m, "bedwars.upgraded", name(buyer.getUUID()), Lang.tr(m, "bedwars.upgrade." + upgrade), BedWarsUi.roman(level));
		}
	}

	// ------------------------------------------------------------------ drops

	@Override
	public boolean allowsDrops(ServerPlayer p) {
		return cfg.dropsAllowed;
	}

	/** Resources and blocks may be shared; swords, tools and armour stay with their owner. */
	@Override
	public boolean allowsDrop(ServerPlayer p, ItemStack stack) {
		if (!cfg.dropsAllowed || Icon.isHubItem(stack)) {
			return false;
		}
		String path = Ids.key(stack.getItem());
		return !(path.endsWith("_sword") || path.endsWith("_pickaxe") || path.endsWith("_axe") || path.endsWith("shears"));
	}

	@Override
	public Component status(ServerPlayer viewer) {
		return Lang.tr(viewer, "bedwars.status", BedWarsRules.stillIn(teams).size(), teams.size());
	}

	/** Seconds of play so far (for the sidebar). */
	public int seconds() {
		return runSeconds();
	}

	/** Time limit in seconds (0: none). */
	public int limitSeconds() {
		return timeLimitTicks() / 20;
	}

	@Override
	public boolean ownSidebar() {
		return true;
	}
}
