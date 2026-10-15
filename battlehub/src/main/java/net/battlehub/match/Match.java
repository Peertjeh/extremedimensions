package net.battlehub.match;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.battlehub.BattleHub;
import net.battlehub.arena.Arena;
import net.battlehub.arena.ArenaBuilder;
import net.battlehub.arena.ArenaSlots;
import net.battlehub.arena.ArenaWorld;
import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.BotStyle;
import net.battlehub.core.GameMode;
import net.battlehub.core.MatchRecap;
import net.battlehub.core.Outcome;
import net.battlehub.data.Snapshots;
import net.battlehub.gui.Icons;
import net.battlehub.gui.Menus;
import net.battlehub.lang.Lang;
import net.battlehub.lobby.HubItems;
import net.battlehub.progression.Cosmetics;
import net.battlehub.session.Sessions;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * One running game in its own arena slot. Lifecycle: BUILDING (arena is placed, players wait in the lobby) →
 * COUNTDOWN (players frozen on their spawns with their kit) → RUNNING → ENDING (results shown, a short pause) →
 * CLOSED (everyone restored and back in the lobby, arena cleared). Subclasses supply the arena design and rules.
 */
public abstract class Match {
	public enum Phase { BUILDING, COUNTDOWN, RUNNING, ENDING, CLOSED }

	public final int id;
	public final GameMode mode;
	public final boolean ranked;
	protected final MinecraftServer server;
	protected final List<UUID> participants;
	protected final Map<UUID, String> names = new HashMap<>();
	protected final Set<UUID> alive = new LinkedHashSet<>();
	protected final Set<UUID> watchers = new LinkedHashSet<>();
	protected final Set<UUID> left = new HashSet<>();
	protected final Map<UUID, Integer> kills = new HashMap<>();
	protected final Map<UUID, Integer> deaths = new HashMap<>();
	protected final Map<UUID, Arena.Point> spawnOf = new HashMap<>();
	protected final Map<UUID, Outcome> outcomes = new HashMap<>();
	protected final RandomSource random = RandomSource.create();
	/** A private game: no stats, rewards or ELO. */
	private boolean unrated;
	/** Damage, hits, combos and kills of everyone, for the recap at the end. */
	protected final MatchRecap recap = new MatchRecap();
	protected Arena arena;
	protected Phase phase = Phase.BUILDING;
	protected int phaseTicks;
	protected int runTicks;
	private boolean teleported;
	/** Ticks of the intro flight at the start of the countdown (0: none). */
	protected int introTicks;
	/** How the next death came about (set by {@link #handleDeath}, read by {@link #killed}): melee, arrow or other. */
	private String nextCause = "";

	protected Match(int id, GameMode mode, boolean ranked, MinecraftServer server, List<ServerPlayer> players) {
		this.id = id;
		this.mode = mode;
		this.ranked = ranked;
		this.server = server;
		this.participants = new ArrayList<>();
		for (ServerPlayer p : players) {
			participants.add(p.getUUID());
			names.put(p.getUUID(), p.getGameProfile().name());
		}
	}

	// ------------------------------------------------------------------ hooks

	/** The arena for this match in the given theme. */
	protected abstract Blueprint design(Theme theme);

	/** Big line in the Spectate menu, e.g. "Stijn vs Tim" or "Bow Spleef (8 players)". */
	public abstract Component describe(ServerPlayer viewer);

	protected HubConfig.Game game() {
		return ConfigManager.config().game(mode);
	}

	protected String kit() {
		return game().kit;
	}

	/** Leather armour colour per player (-1: kit default). */
	protected int leatherColor(UUID id) {
		return -1;
	}

	protected int countdownSeconds() {
		HubConfig.Timings t = ConfigManager.config().timings;
		return mode.isDuel() ? t.duelCountdownSeconds : t.matchStartCountdownSeconds;
	}

	protected int timeLimitTicks() {
		return Math.max(0, game().timeLimitSeconds) * 20;
	}

	/** Minimum players who must actually arrive for the match to start. */
	protected int minimumToStart() {
		return 2;
	}

	/** Called for each player after they were teleported and got their kit. */
	protected void onArrived(ServerPlayer p, int index) {}

	/** Where a player starts (teams can override this). */
	protected Arena.Point spawnPoint(UUID id, int index) {
		return arena.spawn(index);
	}

	/** False while a player is out of play for a moment (waiting to respawn). */
	protected boolean active(UUID id) {
		return alive.contains(id);
	}

	/** A player fell below the arena. Default: counts as a death. */
	protected void fellOut(ServerPlayer p) {
		killed(p, lastAttacker(p), true);
	}

	/** After a player got their own things back (end of match, leaving). */
	protected void onRestored(ServerPlayer p) {}

	/** After a spectator arrived. */
	protected void onWatcherAdded(ServerPlayer p) {}

	/** Called when the countdown is over. */
	protected void onGo() {}

	/** Game rules, every tick while RUNNING. */
	protected void tickGame() {}

	/** Extra damage rules on top of "only while running, only between players of this match". */
	protected boolean damageAllowed(ServerPlayer victim, DamageSource source, ServerPlayer attacker) {
		return true;
	}

	/** A player died (or fell out of the arena). Default: eliminated. */
	protected void onKilled(ServerPlayer victim, ServerPlayer killer) {
		eliminate(victim);
	}

	/** A participant disconnected or used /hub. */
	protected void onLeft(UUID id) {}

	/** Decide whether the match is over; default: last one alive wins. */
	protected void checkEnd() {
		if (alive.size() <= 1) {
			finish(new ArrayList<>(alive));
		}
	}

	/** Time limit reached; default: draw. */
	protected void onTimeUp() {
		finish(List.of());
	}

	// ------------------------------------------------------------------ world interaction (games that build or use items)

	/** A participant breaks a block; true lets it break. Default: never (games remove blocks themselves). */
	public boolean onBreakBlock(ServerPlayer p, BlockPos pos, BlockState state) {
		return false;
	}

	/** A participant places a block (or empties a bucket) at {@code target}; true allows it. Default: never. */
	public boolean onPlaceBlock(ServerPlayer p, BlockPos target, ItemStack held) {
		return false;
	}

	/** Right-click on a block, before anything else; PASS lets the normal handling go on. */
	public InteractionResult onUseBlock(ServerPlayer p, BlockHitResult hit, ItemStack held) {
		return InteractionResult.PASS;
	}

	/** Right-click on an entity (a shopkeeper, for example). */
	public InteractionResult onUseEntity(ServerPlayer p, Entity target) {
		return InteractionResult.PASS;
	}

	/** Left-click on an entity, before damage; PASS lets the attack go on. */
	public InteractionResult onAttackEntity(ServerPlayer p, Entity target) {
		return InteractionResult.PASS;
	}

	/** Left-click on a block. */
	public InteractionResult onAttackBlock(ServerPlayer p, BlockPos pos) {
		return InteractionResult.PASS;
	}

	/** Right-click with a game item (an item tagged "game.something"). */
	public void onUseGameItem(ServerPlayer p, String tag) {}

	/** The player swung their arm (also when hitting nothing at all). */
	public void onSwing(ServerPlayer p) {}

	/** May participants drop items with Q? Default: no. */
	public boolean allowsDrops(ServerPlayer p) {
		return false;
	}

	/** May the player drop this item (Bed Wars: resources and blocks yes, swords and tools no)? */
	public boolean allowsDrop(ServerPlayer p, ItemStack stack) {
		return allowsDrops(p);
	}

	/** How long the end of the match is shown before everyone goes back. */
	protected int endDelayTicks() {
		return ConfigManager.config().timings.endDelaySeconds * 20;
	}

	/** After everyone went home: remove what the game left behind (displays, shopkeepers, scoreboards). */
	protected void onClosed() {}

	// ------------------------------------------------------------------ recap

	public MatchRecap recap() {
		return recap;
	}

	public boolean unrated() {
		return unrated;
	}

	public void setUnrated(boolean unrated) {
		this.unrated = unrated;
	}

	/** A swing by a participant in play (from the swing hook). */
	public void recordSwing(ServerPlayer p) {
		if (phase == Phase.RUNNING && inPlay(p.getUUID())) {
			recap.swing(p.getUUID());
		}
	}

	/** Damage between players of this match (or from the world when {@code attacker} is null). */
	public void recordHit(ServerPlayer attacker, ServerPlayer victim, float amount, boolean arrow) {
		if (phase != Phase.RUNNING || !participants.contains(victim.getUUID())) {
			return;
		}
		UUID a = attacker != null && participants.contains(attacker.getUUID()) ? attacker.getUUID() : null;
		recap.hit(a, victim.getUUID(), amount, arrow);
		if (a != null) {
			net.battlehub.fair.Teaming.hit(this, a, victim.getUUID(), runTicks / 20);
		}
	}

	public void recordShot(ServerPlayer shooter) {
		if (phase == Phase.RUNNING && inPlay(shooter.getUUID())) {
			recap.shot(shooter.getUUID());
		}
	}

	/** Share of the coin reward paid out (1 = all); XP is not affected. */
	public double coinFactor() {
		return 1.0;
	}

	/** Lang key of the line that explains why no coins were paid, or null. */
	public String noCoinsKey() {
		return null;
	}

	// ------------------------------------------------------------------ bots

	/** How bots play this match. */
	public BotStyle botStyle() {
		return BotStyle.MELEE;
	}

	/** Should {@code a} fight {@code b}? Team games say no for teammates. */
	public boolean isEnemy(UUID a, UUID b) {
		return !a.equals(b);
	}

	/** Where a bot goes when nobody is close to fight (a hill, a flag, its bed); null: after the nearest enemy. */
	public Vec3 botObjective(ServerPlayer bot) {
		return null;
	}

	/** True while this participant can act: the match is running and they are not out or waiting to respawn. */
	public boolean inPlay(UUID id) {
		return phase == Phase.RUNNING && active(id) && !left.contains(id);
	}

	/** Lang key of a short status line for the Spectate menu. */
	public Component status(ServerPlayer viewer) {
		return Lang.tr(viewer, Icons.modeKey(mode));
	}

	// ------------------------------------------------------------------ lifecycle

	/** Reserves a slot and starts building. False if every arena slot is busy. */
	boolean begin() {
		ServerLevel level = ArenaWorld.level(server);
		if (level == null) {
			return false;
		}
		int slot = ArenaSlots.acquire();
		if (slot < 0) {
			return false;
		}
		Theme theme = pickTheme();
		Blueprint bp = design(theme);
		arena = new Arena(level, slot, theme, ArenaWorld.origin(slot), bp);
		ArenaSlots.markBuilt(slot, arena.bounds());
		for (UUID id : participants) {
			Sessions.toMatch(id, this.id);
			ServerPlayer p = player(id);
			if (p != null) {
				p.closeContainer();
				Msg.send(p, "match.preparing", Lang.tr(p, Icons.modeKey(mode)), Lang.tr(p, "theme." + theme.id()));
				Msg.sound(p, SoundEvents.NOTE_BLOCK_PLING, 0.8f, 1.6f);
			}
		}
		ArenaBuilder.build(arena, this::onBuilt);
		return true;
	}

	/** Whose theme list to use (a tournament uses its kit's mode). */
	protected String themeMode() {
		return mode.id();
	}

	/** After a normal end: offer "play again" (not between the rounds of a tournament). */
	protected boolean offersPlayAgain() {
		return true;
	}

	protected Theme pickTheme() {
		List<String> ids = ConfigManager.config().arenas.themes.get(themeMode());
		List<Theme> options = new ArrayList<>();
		if (ids != null) {
			for (String s : ids) {
				Theme t = Theme.byId(s);
				if (t != null) {
					options.add(t);
				}
			}
		}
		if (options.isEmpty()) {
			options.add(Theme.CASTLE);
		}
		return options.get(random.nextInt(options.size()));
	}

	private void onBuilt() {
		if (phase != Phase.BUILDING) {
			return;
		}
		int index = 0;
		for (UUID id : participants) {
			ServerPlayer p = player(id);
			if (p == null || left.contains(id)) {
				left.add(id);
				continue;
			}
			alive.add(id);
			prepare(p, index++);
		}
		teleported = true;
		if (alive.size() < minimumToStart()) {
			abort("match.cancelled_players");
			return;
		}
		setPhase(Phase.COUNTDOWN);
		introTicks = net.battlehub.fx.Cinematics.intro(this);
		Menus.refreshAll(server);
	}

	private void prepare(ServerPlayer p, int index) {
		p.closeContainer();
		net.battlehub.lobby.DoubleJump.reset(p);
		net.battlehub.fx.Sounds.stopMusic(p);
		Snapshots.take(p);
		p.stopRiding();
		resetForPlay(p);
		Arena.Point sp = spawnPoint(p.getUUID(), index);
		spawnOf.put(p.getUUID(), sp);
		teleport(p, sp);
		Kits.give(p, kit(), leatherColor(p.getUUID()));
		onArrived(p, index);
	}

	/** Full health and hunger, no effects, fire or arrows, adventure mode. */
	protected void resetForPlay(ServerPlayer p) {
		p.removeAllEffects();
		p.setGameMode(GameType.ADVENTURE);
		p.setHealth(p.getMaxHealth());
		p.getFoodData().setFoodLevel(20);
		p.getFoodData().setSaturation(5f);
		p.clearFire();
		p.setArrowCount(0);
		p.resetFallDistance();
	}

	/**
	 * Next round in the same arena: arrows and dropped items are removed, everybody still in the match goes back
	 * to their spawn, healed and with a fresh kit, and the countdown starts again.
	 */
	protected void restartRound() {
		clearLooseEntities();
		for (UUID id : alive) {
			ServerPlayer p = player(id);
			Arena.Point sp = spawnOf.get(id);
			if (p == null || sp == null) continue;
			p.closeContainer();
			resetForPlay(p);
			teleport(p, sp);
			Kits.give(p, kit(), leatherColor(id));
		}
		runTicks = 0;
		introTicks = 0;
		setPhase(Phase.COUNTDOWN);
	}

	/** Removes projectiles, dropped items and other loose entities from the arena. */
	protected void clearLooseEntities() {
		for (Entity e : arena.level().getEntitiesOfClass(Entity.class, arena.bounds(), e -> !(e instanceof Player))) {
			e.discard();
		}
	}

	/** Small line under the countdown numbers. */
	protected Component countdownSubtitle(ServerPlayer p) {
		return Lang.tr(p, "match.get_ready", Lang.tr(p, Icons.modeKey(mode)));
	}

	protected void teleport(ServerPlayer p, Arena.Point pt) {
		p.teleportTo(arena.level(), pt.pos().x, pt.pos().y, pt.pos().z, Set.of(), pt.yaw(), pt.pitch(), true);
		p.setDeltaMovement(Vec3.ZERO);
		p.resetFallDistance();
	}

	protected void setPhase(Phase next) {
		phase = next;
		phaseTicks = 0;
	}

	void tick() {
		phaseTicks++;
		switch (phase) {
			case BUILDING -> {
				if (phaseTicks > ConfigManager.config().timings.arenaBuildTimeoutSeconds * 20) {
					abort("match.cancelled_arena");
				}
			}
			case COUNTDOWN -> tickCountdown();
			case RUNNING -> {
				runTicks++;
				if (runTicks % 20 == 0) {
					net.battlehub.fair.Teaming.tick(this, runTicks / 20);
				}
				voidCheck();
				if (phase == Phase.RUNNING) {
					tickGame();
				}
				int limit = timeLimitTicks();
				if (phase == Phase.RUNNING && limit > 0) {
					int remaining = limit - runTicks;
					if (remaining == 60 * 20 || remaining == 30 * 20 || remaining == 10 * 20) {
						forEveryone(p -> Msg.send(p, "match.time_left", remaining / 20));
					}
					if (runTicks >= limit) {
						forEveryone(p -> Msg.send(p, "match.time_up"));
						onTimeUp();
					}
				}
				tickWatchers();
			}
			case ENDING -> {
				tickWatchers();
				if (phaseTicks % 10 == 0) {
					celebrate();
				}
				if (phaseTicks >= endDelayTicks()) {
					close();
				}
			}
			default -> { }
		}
	}

	/** Big title while the camera flies over the arena: the mode and who plays. */
	protected Component introTitle(ServerPlayer p) {
		return Lang.tr(p, "intro.title", Lang.tr(p, Icons.modeKey(mode)));
	}

	private void tickCountdown() {
		// After an intro there are always three seconds left to count down
		int total = Math.max(Math.max(1, countdownSeconds()) * 20, introTicks > 0 ? introTicks + 60 : 0);
		if (introTicks > 0 && phaseTicks == 1) {
			for (UUID id : alive) {
				ServerPlayer p = player(id);
				if (p == null) continue;
				Msg.title(p, introTitle(p), describe(p), 5, introTicks, 10);
				Msg.sound(p, SoundEvents.UI_TOAST_IN, 1f, 0.8f);
				net.battlehub.fx.Sounds.play(p, net.battlehub.fx.Sounds.INTRO);
			}
		}
		for (UUID id : alive) {
			ServerPlayer p = player(id);
			Arena.Point sp = spawnOf.get(id);
			if (p == null || sp == null) continue;
			double dx = p.getX() - sp.pos().x;
			double dz = p.getZ() - sp.pos().z;
			if (dx * dx + dz * dz > 0.09 || p.getY() < sp.pos().y - 2) {
				p.teleportTo(arena.level(), sp.pos().x, sp.pos().y, sp.pos().z, Relative.ROTATION, 0, 0, false);
				p.setDeltaMovement(Vec3.ZERO);
			}
		}
		if ((phaseTicks - 1) % 20 == 0 && phaseTicks > introTicks) {
			int secs = (total - phaseTicks + 1 + 19) / 20;
			if (secs > 0) {
				for (UUID id : alive) {
					ServerPlayer p = player(id);
					if (p == null) continue;
					String color = secs <= 3 ? "&c" : "&e";
					Msg.title(p, Lang.format(color + "&l" + secs), countdownSubtitle(p), 0, 25, 5);
					Msg.sound(p, SoundEvents.NOTE_BLOCK_HAT, 1f, secs <= 3 ? 1.4f : 1.0f);
				}
			}
		}
		if (phaseTicks >= total) {
			net.battlehub.fx.Cinematics.stopMatch(server, id);
			introTicks = 0;
			setPhase(Phase.RUNNING);
			for (UUID id : alive) {
				ServerPlayer p = player(id);
				if (p == null) continue;
				Msg.title(p, Lang.tr(p, "match.go"), Component.empty(), 0, 20, 10);
				Msg.sound(p, SoundEvents.NOTE_BLOCK_PLING, 1f, 2.0f);
			}
			onGo();
		}
	}

	private void voidCheck() {
		for (UUID id : new ArrayList<>(alive)) {
			ServerPlayer p = player(id);
			if (p != null && active(id) && p.getY() < arena.voidY()) {
				fellOut(p);
				if (phase != Phase.RUNNING) {
					return;
				}
			}
		}
	}

	private void tickWatchers() {
		if (runTicks % 10 != 0 && phase == Phase.RUNNING) {
			return;
		}
		for (UUID id : participants) {
			if (alive.contains(id) || left.contains(id)) continue;
			ServerPlayer p = player(id);
			if (p != null && p.isSpectator() && !arena.inSpectatorBounds(p.position())) {
				teleport(p, arena.spectator());
			}
		}
		for (UUID id : watchers) {
			ServerPlayer p = player(id);
			if (p != null && (!ArenaWorld.is(p.level()) || !arena.inSpectatorBounds(p.position()))) {
				teleport(p, arena.spectator());
			}
		}
	}

	// ------------------------------------------------------------------ combat events

	/** From ALLOW_DAMAGE. */
	public boolean allowDamage(ServerPlayer victim, DamageSource source) {
		if (phase != Phase.RUNNING || !active(victim.getUUID())) {
			return false;
		}
		ServerPlayer attacker = attackerOf(source);
		if (attacker != null && attacker != victim && !active(attacker.getUUID())) {
			return false;
		}
		return damageAllowed(victim, source, attacker);
	}

	/**
	 * A totem of undying (or any item that protects from death) in a hand saves a player who is in play, as in
	 * vanilla; not against damage that ignores invulnerability (the void).
	 */
	public boolean totemSaves(ServerPlayer victim, DamageSource source) {
		if (phase != Phase.RUNNING || !active(victim.getUUID()) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return victim.getMainHandItem().has(DataComponents.DEATH_PROTECTION) || victim.getOffhandItem().has(DataComponents.DEATH_PROTECTION);
	}

	/** From ALLOW_DEATH: the death is cancelled (unless a totem saves the player); the match decides what happens instead. */
	public void handleDeath(ServerPlayer victim, DamageSource source) {
		victim.setHealth(victim.getMaxHealth());
		victim.clearFire();
		if (phase != Phase.RUNNING || !active(victim.getUUID())) {
			return;
		}
		ServerPlayer killer = attackerOf(source);
		if (killer == null || killer == victim) {
			killer = lastAttacker(victim);
		}
		Entity direct = source == null ? null : source.getDirectEntity();
		nextCause = direct instanceof net.minecraft.world.entity.projectile.Projectile ? "arrow"
				: direct instanceof Player ? "melee" : "other";
		killed(victim, killer, false);
	}

	protected void killed(ServerPlayer victim, ServerPlayer killer, boolean fell) {
		UUID v = victim.getUUID();
		deaths.merge(v, 1, Integer::sum);
		boolean credited = killer != null && killer != victim && participants.contains(killer.getUUID());
		if (credited) {
			kills.merge(killer.getUUID(), 1, Integer::sum);
			recap.kill(killer.getUUID());
			Msg.sound(killer, SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
			Cosmetics.onKill(killer, victim, victim.position());
		}
		final ServerPlayer k = credited ? killer : null;
		String cause = fell ? "fall" : nextCause.isEmpty() ? "melee" : nextCause;
		nextCause = "";
		net.battlehub.fx.Killfeed.kill(this, v, k == null ? null : k.getUUID(), cause);
		if (k != null) {
			net.battlehub.fx.Sounds.play(k, net.battlehub.fx.Sounds.KILL);
		}
		forEveryone(p -> {
			if (k != null) {
				Msg.send(p, fell ? "match.knocked_off" : "match.killed", name(v), name(k.getUUID()));
			} else {
				Msg.send(p, fell ? "match.fell" : "match.died", name(v));
			}
		});
		victim.setHealth(victim.getMaxHealth());
		victim.setDeltaMovement(Vec3.ZERO);
		victim.resetFallDistance();
		onKilled(victim, k);
		if (phase == Phase.RUNNING) {
			checkEnd();
		}
	}

	/** Out of the game: watch the rest as a spectator inside the arena. */
	protected void eliminate(ServerPlayer p) {
		alive.remove(p.getUUID());
		p.getInventory().clearContent();
		p.removeAllEffects();
		p.setGameMode(GameType.SPECTATOR);
		teleport(p, arena.spectator());
		Msg.titleKey(p, "match.eliminated", "match.eliminated_sub");
		Msg.sound(p, SoundEvents.PLAYER_HURT, 1f, 0.8f);
	}

	protected ServerPlayer attackerOf(DamageSource source) {
		if (source == null) return null;
		Entity e = source.getEntity();
		return e instanceof ServerPlayer sp ? sp : null;
	}

	protected ServerPlayer lastAttacker(ServerPlayer victim) {
		Player p = victim.getLastHurtByPlayer();
		if (p instanceof ServerPlayer sp && participants.contains(sp.getUUID()) && sp != victim) {
			return sp;
		}
		return null;
	}

	// ------------------------------------------------------------------ leaving

	/** The player disconnected: counts as giving up. Items are restored right away, the position on next join. */
	public void quit(ServerPlayer p) {
		leave(p, false);
	}

	/** The player used /hub (or the leave option): gives up and goes back to the lobby now. */
	public void forfeit(ServerPlayer p) {
		leave(p, true);
		Sessions.toLobby(p.getUUID());
		HubItems.ensure(p, false);
	}

	private void leave(ServerPlayer p, boolean teleport) {
		UUID id = p.getUUID();
		if (!participants.contains(id) || left.contains(id) || phase == Phase.CLOSED) {
			if (watchers.remove(id)) {
				net.battlehub.fx.Cinematics.stop(p);
				net.battlehub.fx.Killfeed.hide(this, p);
				Snapshots.restore(p, teleport);
			}
			return;
		}
		left.add(id);
		boolean wasAlive = alive.remove(id);
		net.battlehub.fx.Cinematics.stop(p);
		net.battlehub.fx.Killfeed.hide(this, p);
		if (phase == Phase.BUILDING) {
			abort("match.cancelled_left");
			return;
		}
		if (phase == Phase.COUNTDOWN || phase == Phase.RUNNING) {
			net.battlehub.fair.LeavePenalty.left(id, this);
		}
		if (teleported) {
			Snapshots.restore(p, teleport);
			onRestored(p);
		}
		forEveryone(o -> Msg.send(o, "match.left", name(id)));
		onLeft(id);
		if (wasAlive && (phase == Phase.RUNNING || phase == Phase.COUNTDOWN)) {
			checkEnd();
		}
	}

	// ------------------------------------------------------------------ ending

	/** Ends the match with these winners (empty: draw). Results are applied immediately. */
	protected void finish(List<UUID> winners) {
		if (phase == Phase.ENDING || phase == Phase.CLOSED) {
			return;
		}
		setPhase(Phase.ENDING);
		boolean draw = winners.isEmpty();
		for (UUID id : participants) {
			Outcome o;
			if (left.contains(id)) {
				o = Outcome.LOSS;
			} else if (draw) {
				o = Outcome.DRAW;
			} else {
				o = winners.contains(id) ? Outcome.WIN : Outcome.LOSS;
			}
			outcomes.put(id, o);
		}
		Component winnerNames = joinNames(winners);
		for (UUID id : participants) {
			ServerPlayer p = player(id);
			if (p == null || left.contains(id)) continue;
			Outcome o = outcomes.get(id);
			switch (o) {
				case WIN -> {
					Msg.title(p, Lang.tr(p, "match.victory"), Lang.tr(p, "match.victory_sub"), 5, 60, 15);
					Msg.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
				}
				case LOSS -> {
					Msg.title(p, Lang.tr(p, "match.defeat"), draw ? Component.empty() : Lang.tr(p, "match.defeat_sub", winnerNames), 5, 60, 15);
					Msg.sound(p, SoundEvents.VILLAGER_NO, 0.8f, 0.8f);
				}
				case DRAW -> {
					Msg.title(p, Lang.tr(p, "match.draw"), Component.empty(), 5, 60, 15);
					Msg.sound(p, SoundEvents.NOTE_BLOCK_PLING, 0.8f, 1f);
				}
			}
			p.setHealth(p.getMaxHealth());
			p.clearFire();
		}
		forWatchers(p -> Msg.send(p, draw ? "match.ended_draw" : "match.ended_winner", winnerNames));
		for (UUID w : winners) {
			if (!left.contains(w) && player(w) != null) {
				net.battlehub.fx.Cinematics.victory(this, w, endDelayTicks() - 10);
				break;
			}
		}
		try {
			Results.apply(this);
		} catch (Exception e) {
			BattleHub.LOGGER.error("Could not apply results of match {}", id, e);
		}
		try {
			RecapMessages.send(this);
		} catch (RuntimeException e) {
			BattleHub.LOGGER.error("Could not show the recap of match {}", id, e);
		}
		Menus.refreshAll(server);
	}

	/** Winners still in the arena show their win effect. */
	private void celebrate() {
		for (UUID id : participants) {
			ServerPlayer p = player(id);
			if (p != null && !left.contains(id) && outcomes.get(id) == Outcome.WIN) {
				Cosmetics.celebrate(p, phaseTicks);
			}
		}
	}

	/** Everyone back to the lobby, arena cleared, slot released. */
	void close() {
		if (phase == Phase.CLOSED) {
			return;
		}
		setPhase(Phase.CLOSED);
		net.battlehub.fx.Cinematics.stopMatch(server, id);
		net.battlehub.fx.Killfeed.close(this);
		sendHome(offersPlayAgain());
		cleanUp();
		clearArena();
		Menus.refreshAll(server);
	}

	/** Game leftovers and loose entities go before the arena is cleared; a failing game hook never blocks cleanup. */
	private void cleanUp() {
		net.battlehub.fair.Teaming.forget(id);
		try {
			onClosed();
		} catch (RuntimeException e) {
			BattleHub.LOGGER.error("Match #{}: cleanup failed", id, e);
		}
		if (arena != null) {
			clearLooseEntities();
		}
	}

	/** Stops the match without results (admin, server stop, arena failure). */
	public void abort(String reasonKey) {
		if (phase == Phase.CLOSED) {
			return;
		}
		setPhase(Phase.CLOSED);
		for (UUID id : participants) {
			ServerPlayer p = player(id);
			if (p != null && !left.contains(id)) {
				Msg.send(p, reasonKey);
			}
		}
		forWatchers(p -> Msg.send(p, reasonKey));
		sendHome(false);
		cleanUp();
		clearArena();
		Menus.refreshAll(server);
	}

	/** Everybody back to where they came from; {@code offerAgain}: after a normal end, offer to play again. */
	private void sendHome(boolean offerAgain) {
		for (UUID id : participants) {
			ServerPlayer p = player(id);
			if (p == null || left.contains(id)) continue;
			if (teleported) {
				Snapshots.restore(p, true);
				onRestored(p);
			}
			Sessions.toLobby(id);
			HubItems.ensure(p, false);
			if (offerAgain && teleported) {
				net.battlehub.queue.PlayAgain.offer(p, this);
			}
		}
		for (UUID id : new ArrayList<>(watchers)) {
			ServerPlayer p = player(id);
			if (p == null) continue;
			Snapshots.restore(p, true);
			onRestored(p);
			Sessions.toLobby(id);
			HubItems.ensure(p, false);
			Msg.send(p, "spectate.match_over");
		}
		watchers.clear();
	}

	private void clearArena() {
		if (arena != null) {
			int slot = arena.slot();
			ArenaBuilder.clear(arena, () -> ArenaSlots.release(slot));
		}
	}

	// ------------------------------------------------------------------ spectators

	public boolean canWatch() {
		return phase == Phase.COUNTDOWN || phase == Phase.RUNNING;
	}

	public void addWatcher(ServerPlayer p) {
		watchers.add(p.getUUID());
		teleport(p, arena.spectator());
		onWatcherAdded(p);
		net.battlehub.fx.Killfeed.watcherAdded(this, p);
	}

	public void removeWatcher(UUID id) {
		watchers.remove(id);
		ServerPlayer p = player(id);
		if (p != null) {
			net.battlehub.fx.Cinematics.stop(p);
			net.battlehub.fx.Killfeed.hide(this, p);
			onRestored(p);
		}
	}

	/** Games with their own sidebar (Bed Wars, Prop Hunt) keep it; the others show the killfeed there. */
	public boolean ownSidebar() {
		return false;
	}

	public Set<UUID> watchers() {
		return watchers;
	}

	// ------------------------------------------------------------------ helpers and queries

	public ServerPlayer player(UUID id) {
		return server.getPlayerList().getPlayer(id);
	}

	public String name(UUID id) {
		return names.getOrDefault(id, "?");
	}

	protected Component joinNames(List<UUID> ids) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < ids.size(); i++) {
			if (i > 0) sb.append(i == ids.size() - 1 ? " & " : ", ");
			sb.append(name(ids.get(i)));
		}
		return Component.literal(sb.toString());
	}

	/** Runs for every participant still here and every watcher. */
	protected void forEveryone(java.util.function.Consumer<ServerPlayer> action) {
		for (UUID id : participants) {
			ServerPlayer p = player(id);
			if (p != null && !left.contains(id)) action.accept(p);
		}
		forWatchers(action);
	}

	protected void forWatchers(java.util.function.Consumer<ServerPlayer> action) {
		for (UUID id : watchers) {
			ServerPlayer p = player(id);
			if (p != null) action.accept(p);
		}
	}

	public List<UUID> participants() { return participants; }
	public Set<UUID> alive() { return alive; }
	public Set<UUID> leftPlayers() { return left; }
	public Phase phase() { return phase; }
	public Arena arena() { return arena; }
	public int kills(UUID id) { return kills.getOrDefault(id, 0); }
	public int deaths(UUID id) { return deaths.getOrDefault(id, 0); }
	public Outcome outcome(UUID id) { return outcomes.getOrDefault(id, Outcome.LOSS); }
	public int runSeconds() { return runTicks / 20; }

	public boolean involves(UUID id) {
		return participants.contains(id) || watchers.contains(id);
	}
}
