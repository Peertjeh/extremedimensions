package net.battlehub.games.prophunt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.battlehub.arena.Arena;
import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.PropHuntLayout;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.PropHuntConfig;
import net.battlehub.core.GameMode;
import net.battlehub.core.StateMachine;
import net.battlehub.core.prophunt.Cooldowns;
import net.battlehub.core.prophunt.Roles;
import net.battlehub.core.prophunt.Scoreboard;
import net.battlehub.games.PartyMatch;
import net.battlehub.games.Visibility;
import net.battlehub.gui.PropDisguiseMenu;
import net.battlehub.lang.Lang;
import net.battlehub.match.Kits;
import net.battlehub.progression.TitleService;
import net.battlehub.ui.MatchBossBar;
import net.battlehub.ui.Sidebar;
import net.battlehub.util.Icon;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Prop Hunt: each round some players hunt and the rest are props, hidden as everyday blocks. Props get a head
 * start (HIDING) while the hunters wait blinded in a cage, then the hunt is on (HUNTING) until time runs out or
 * every prop is found (ROUND_END). Hunters pay for swinging at nothing or at the wrong object and heal and earn
 * arrows by finding props; props earn points by surviving, taunting and fooling hunters with decoys. After all
 * rounds the most points win. Rules in {@code core.prophunt}, numbers in prophunt.json, the look of a prop in
 * {@link Disguise}, screens in {@link PropHuntUi} and {@link PropDisguiseMenu}.
 */
public final class PropHuntMatch extends PartyMatch {
	public enum Stage { WAITING, HIDING, HUNTING, ROUND_END }
	public enum Role { HUNTER, PROP, OUT }

	public static final String ITEM_DISGUISE = "game.ph.disguise";
	public static final String ITEM_LOCK = "game.ph.lock";
	public static final String ITEM_TAUNT = "game.ph.taunt";
	public static final String ITEM_DECOY = "game.ph.decoy";
	private static final net.minecraft.resources.Identifier HEALTH_ID = Ids.id("battlehub:prop_health");
	private static final net.minecraft.resources.Identifier SPEED_ID = Ids.id("battlehub:prop_speed");
	private static final int MISS_COOLDOWN = 10;

	private final PropHuntConfig cfg;
	private final StateMachine<Stage> stage = new StateMachine<>(Stage.class, Stage.WAITING);
	private final Set<UUID> hunters = new LinkedHashSet<>();
	private final Set<UUID> props = new LinkedHashSet<>();
	/** Found props that sit out the rest of the round (when they do not join the hunters). */
	private final Set<UUID> benched = new HashSet<>();
	/** Found props that come back as hunters. */
	private final Set<UUID> toHunter = new HashSet<>();
	private final Map<UUID, Disguise> disguises = new HashMap<>();
	private final List<Decoy> decoys = new ArrayList<>();
	private final Cooldowns<String> cooldowns = new Cooldowns<>();
	private final Scoreboard<UUID> points = new Scoreboard<>();
	private final Map<UUID, Integer> timesHunted = new HashMap<>();
	private final Map<UUID, Integer> lastAttack = new HashMap<>();
	private final Set<String> objects = new HashSet<>();
	private final List<String> disguiseList = new ArrayList<>();
	private final MatchBossBar bossBar = new MatchBossBar(BossEvent.BossBarColor.YELLOW);
	private final Sidebar sidebar;
	private final PropHuntUi ui;
	private int round;
	private boolean huntersWonRound;

	record Decoy(Disguise disguise, UUID owner, int expiresAt) {}

	public PropHuntMatch(int id, MinecraftServer server, List<ServerPlayer> players) {
		super(id, GameMode.PROP_HUNT, server, players);
		this.cfg = ConfigManager.prophunt();
		// Only real blocks count; without any, barrels it is
		for (String s : cfg.disguises) {
			if (!Ids.state(s).isAir()) {
				objects.add(Ids.id(s).toString());
				disguiseList.add(Ids.id(s).toString());
			}
		}
		if (disguiseList.isEmpty()) {
			objects.add("minecraft:barrel");
			disguiseList.add("minecraft:barrel");
		}
		this.sidebar = new Sidebar("ph" + id);
		this.ui = new PropHuntUi(this);
		stage.allow(Stage.WAITING, Stage.HIDING)
				.after(Stage.HIDING, cfg.hideSeconds * 20, Stage.HUNTING)
				.allow(Stage.HIDING, Stage.ROUND_END)
				.after(Stage.HUNTING, cfg.huntSeconds * 20, Stage.ROUND_END)
				.allow(Stage.ROUND_END, Stage.HIDING)
				.onEnter((s, from) -> {
					switch (s) {
						case HUNTING -> releaseHunters();
						case ROUND_END -> roundEnded();
						default -> { }
					}
				});
	}

	// ------------------------------------------------------------------ queries (UI, tests)

	public Stage stage() { return stage.state(); }
	public int stageTicksLeft() { return stage.ticksLeft(); }
	public int round() { return round; }
	public int rounds() { return cfg.rounds; }
	public PropHuntConfig settings() { return cfg; }
	public Set<UUID> hunters() { return hunters; }
	public Set<UUID> props() { return props; }
	public Disguise disguise(UUID id) { return disguises.get(id); }
	public int points(UUID id) { return points.get(id); }
	public Scoreboard<UUID> scoreboard() { return points; }
	public int decoyCount() { return decoys.size(); }
	public List<Disguise> decoyDisguises() { return decoys.stream().map(Decoy::disguise).toList(); }
	public boolean huntersWonRound() { return huntersWonRound; }
	public Cooldowns<String> cooldowns() { return cooldowns; }
	public long now() { return runTicks; }

	@Override
	public net.battlehub.core.BotStyle botStyle() {
		return net.battlehub.core.BotStyle.PROP_HUNT;
	}

	@Override
	public boolean isEnemy(UUID a, UUID b) {
		Role ra = role(a);
		Role rb = role(b);
		return ra != Role.OUT && rb != Role.OUT && ra != rb;
	}

	public Role role(UUID id) {
		if (hunters.contains(id)) return Role.HUNTER;
		if (props.contains(id)) return Role.PROP;
		return Role.OUT;
	}

	public List<UUID> present() {
		List<UUID> out = new ArrayList<>();
		for (UUID id : participants) {
			if (!left.contains(id) && alive.contains(id)) out.add(id);
		}
		return out;
	}

	/** The blocks props may turn into, as ids. */
	public List<String> disguiseList() {
		return disguiseList;
	}

	public boolean isObject(BlockState st) {
		return objects.contains(BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString());
	}

	// ------------------------------------------------------------------ setup

	@Override
	protected Blueprint design(Theme theme) {
		return PropHuntLayout.design(theme, random, disguiseList);
	}

	@Override
	protected String kit() {
		return "prophunt_prop";
	}

	@Override
	protected int respawnTicks() {
		return Math.max(1, cfg.hunterRespawnSeconds * 20);
	}

	@Override
	protected int timeLimitTicks() {
		// Rounds and their phases decide when the game is over
		return 0;
	}

	@Override
	protected boolean active(UUID id) {
		return super.active(id) && !benched.contains(id);
	}

	@Override
	protected void onArrived(ServerPlayer p, int index) {
		bossBar.show(p);
		Msg.send(p, "prophunt.welcome", cfg.rounds);
	}

	@Override
	protected void onGo() {
		startRound();
	}

	/** Hunters are picked (everyone hunts in turn), everyone else hides. */
	private void startRound() {
		round++;
		huntersWonRound = false;
		clearRound();
		List<UUID> here = present();
		Set<UUID> picked = Roles.pickHunters(here, cfg.hunterRatio, timesHunted, new java.util.Random(random.nextLong()));
		int spawn = 0;
		for (UUID id : here) {
			ServerPlayer p = player(id);
			if (p == null) continue;
			if (picked.contains(id)) {
				timesHunted.merge(id, 1, Integer::sum);
				makeHunter(p, false, true);
			} else {
				makeProp(p, spawn++);
			}
		}
		stage.go(Stage.HIDING);
		forEveryone(p -> {
			Msg.title(p, Lang.tr(p, "prophunt.round_title", round, cfg.rounds),
					Lang.tr(p, hunters.contains(p.getUUID()) ? "prophunt.round_sub_hunter" : "prophunt.round_sub_prop", cfg.hideSeconds), 5, 50, 10);
			Msg.send(p, "prophunt.round_start", round, cfg.rounds, hunters.size(), props.size());
		});
		updateUi();
	}

	/** Disguises, decoys, cooldowns and pending respawns of the last round are gone. */
	private void clearRound() {
		List<ServerPlayer> viewers = viewers();
		for (Map.Entry<UUID, Disguise> e : disguises.entrySet()) {
			e.getValue().remove(viewers);
		}
		disguises.clear();
		for (Decoy d : decoys) d.disguise().remove(viewers);
		decoys.clear();
		cooldowns.clear();
		respawnAt.clear();
		benched.clear();
		toHunter.clear();
		lastAttack.clear();
		for (UUID id : new ArrayList<>(hunters)) unhide(id);
		for (UUID id : new ArrayList<>(props)) unhide(id);
		hunters.clear();
		props.clear();
	}

	private void unhide(UUID id) {
		ServerPlayer p = player(id);
		if (p != null) {
			Visibility.show(p);
			p.removeEffect(MobEffects.INVISIBILITY);
			propBody(p, false);
		} else {
			Visibility.forget(id);
		}
	}

	private void makeHunter(ServerPlayer p, boolean released, boolean freshKit) {
		UUID id = p.getUUID();
		props.remove(id);
		benched.remove(id);
		hunters.add(id);
		Visibility.show(p);
		propBody(p, false);
		p.removeAllEffects();
		p.setGameMode(GameType.SURVIVAL);
		p.setHealth(p.getMaxHealth());
		if (freshKit) {
			Kits.give(p, game().kit, -1);
			p.getInventory().add(new ItemStack(Items.ARROW, Math.max(0, cfg.hunterArrows)));
		}
		TitleService.setOverride(p, Lang.tr(p, "prophunt.prefix_hunter"));
		if (released) {
			teleport(p, mark("hunter_release"));
		} else {
			teleport(p, mark("hunter_cage"));
			int ticks = (stage.is(Stage.HIDING) ? stage.ticksLeft() : cfg.hideSeconds * 20) + 20;
			p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0, false, false, false));
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 6, false, false, false));
		}
		p.inventoryMenu.sendAllDataToRemote();
	}

	private void makeProp(ServerPlayer p, int spawnIndex) {
		UUID id = p.getUUID();
		props.add(id);
		p.removeAllEffects();
		p.setGameMode(GameType.ADVENTURE);
		Kits.give(p, "prophunt_prop", -1);
		givePropItems(p);
		propBody(p, true);
		p.setHealth(p.getMaxHealth());
		teleport(p, arena.spawn(spawnIndex));
		TitleService.setOverride(p, Lang.tr(p, "prophunt.prefix_prop"));
		// Not seen as a player any more: only as a block
		Visibility.hide(p);
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
		disguise(p, Ids.state(disguiseList.get(random.nextInt(disguiseList.size()))));
	}

	private void givePropItems(ServerPlayer p) {
		var inv = p.getInventory();
		inv.setItem(0, Icon.of(Items.BLAZE_ROD).name(Lang.item(p, "prophunt.item.disguise")).lore(Lang.loreLines(p, "prophunt.item.disguise.lore"))
				.tag(ITEM_DISGUISE).build());
		inv.setItem(1, Icon.of(Items.TRIPWIRE_HOOK).name(Lang.item(p, "prophunt.item.lock")).lore(Lang.loreLines(p, "prophunt.item.lock.lore"))
				.tag(ITEM_LOCK).build());
		inv.setItem(2, Icon.of(Items.NOTE_BLOCK).name(Lang.item(p, "prophunt.item.taunt")).lore(Lang.loreLines(p, "prophunt.item.taunt.lore", cfg.tauntCooldownSeconds))
				.tag(ITEM_TAUNT).build());
		inv.setItem(3, Icon.of(Items.SLIME_BALL).name(Lang.item(p, "prophunt.item.decoy")).lore(Lang.loreLines(p, "prophunt.item.decoy.lore", cfg.decoySeconds, cfg.decoyCooldownSeconds))
				.tag(ITEM_DECOY).build());
		inv.setSelectedSlot(0);
		p.inventoryMenu.sendAllDataToRemote();
	}

	/**
	 * Props are weaker and slower than hunters (prophunt.json: health and walking speed); the changes are
	 * temporary and removed when the player stops being a prop.
	 */
	private void propBody(ServerPlayer p, boolean prop) {
		AttributeInstance health = p.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			if (prop && cfg.propHealth != 20) {
				health.addOrUpdateTransientModifier(new AttributeModifier(HEALTH_ID, cfg.propHealth - 20, AttributeModifier.Operation.ADD_VALUE));
				p.setHealth(Math.min(p.getHealth(), p.getMaxHealth()));
			} else if (health.hasModifier(HEALTH_ID)) {
				health.removeModifier(HEALTH_ID);
				p.setHealth(p.getMaxHealth());
			}
		}
		AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			if (prop && cfg.propSpeed != 1) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, cfg.propSpeed - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			} else if (speed.hasModifier(SPEED_ID)) {
				speed.removeModifier(SPEED_ID);
			}
		}
	}

	private Arena.Point mark(String name) {
		BlockPos b = arena.mark(name);
		float yaw = name.equals("hunter_release") || name.equals("hunter_cage") ? 0 : 180;
		return new Arena.Point(Vec3.atBottomCenterOf(b), yaw, 0);
	}

	private void releaseHunters() {
		for (UUID id : hunters) {
			ServerPlayer p = player(id);
			if (p == null || !active(id)) continue;
			p.removeEffect(MobEffects.BLINDNESS);
			p.removeEffect(MobEffects.SLOWNESS);
			teleport(p, mark("hunter_release"));
		}
		forEveryone(p -> {
			Msg.title(p, Lang.tr(p, "prophunt.hunt_title"), Lang.tr(p, "prophunt.hunt_sub", cfg.huntSeconds), 5, 40, 10);
			Msg.sound(p, SoundEvents.RAID_HORN, 0.6f, 1f);
		});
	}

	// ------------------------------------------------------------------ game loop

	@Override
	protected void tickGame() {
		tickRespawns();
		if (stage.is(Stage.ROUND_END)) {
			if (stage.ticksInState() >= cfg.roundEndSeconds * 20) {
				if (round >= cfg.rounds) {
					endGame();
				} else {
					startRound();
				}
				return;
			}
			stage.tick();
			return;
		}
		stage.tick();
		if (phase != Phase.RUNNING) {
			return;
		}
		List<ServerPlayer> viewers = viewers();
		int solidify = cfg.solidifySeconds * 20;
		for (Map.Entry<UUID, Disguise> e : disguises.entrySet()) {
			ServerPlayer p = player(e.getKey());
			if (p != null && active(e.getKey())) e.getValue().follow(p, solidify, viewers);
		}
		Iterator<Decoy> it = decoys.iterator();
		while (it.hasNext()) {
			Decoy d = it.next();
			if (runTicks >= d.expiresAt()) {
				d.disguise().remove(viewers);
				it.remove();
			}
		}
		if (stage.is(Stage.HUNTING) && stage.ticksInState() > 0 && stage.ticksInState() % 200 == 0) {
			for (UUID id : props) {
				if (active(id)) points.add(id, cfg.points.propPer10Seconds);
			}
		}
		if (runTicks % 20 == 0) {
			// New viewers and clients that reset a fake block see the solid props again
			for (Disguise d : disguises.values()) d.sendFake(viewers);
			updateUi();
		}
		if (runTicks % 5 == 0) {
			for (UUID id : props) {
				ServerPlayer p = player(id);
				if (p != null && active(id)) ui.propBar(p);
			}
		}
	}

	private List<ServerPlayer> viewers() {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID id : participants) {
			ServerPlayer p = player(id);
			if (p != null && !left.contains(id)) out.add(p);
		}
		forWatchers(out::add);
		return out;
	}

	private void updateUi() {
		for (ServerPlayer p : viewers()) ui.show(p, sidebar, bossBar);
	}

	/** Time up (props win the round) or every prop found (hunters win it). */
	private void roundEnded() {
		huntersWonRound = props.stream().noneMatch(this::active);
		for (UUID id : props) {
			if (active(id)) points.add(id, cfg.points.propSurvivedRound);
		}
		List<ServerPlayer> viewers = viewers();
		for (Disguise d : disguises.values()) d.unsolidify(viewers);
		boolean hw = huntersWonRound;
		forEveryone(p -> {
			Msg.title(p, Lang.tr(p, hw ? "prophunt.round_hunters" : "prophunt.round_props"), Lang.tr(p, "prophunt.round_points", points.get(p.getUUID())), 5, 60, 15);
			Msg.send(p, hw ? "prophunt.round_hunters_chat" : "prophunt.round_props_chat", round, cfg.rounds);
			Msg.sound(p, SoundEvents.NOTE_BLOCK_PLING, 0.8f, hw ? 0.8f : 1.4f);
		});
		updateUi();
	}

	/** After the last round: the most points win (a draw when nobody scored). */
	private void endGame() {
		List<UUID> here = present();
		finish(points.winners(here));
	}

	@Override
	protected void finish(List<UUID> winners) {
		if (phase == Phase.ENDING || phase == Phase.CLOSED) {
			return;
		}
		clearRound();
		super.finish(winners);
		updateUi();
	}

	// ------------------------------------------------------------------ disguises and abilities

	/** The prop now looks like this block (only blocks from the list in prophunt.json). */
	public boolean disguise(ServerPlayer p, BlockState state) {
		UUID id = p.getUUID();
		if (!props.contains(id) || !active(id) || state.isAir() || !isObject(state)) {
			return false;
		}
		Disguise d = disguises.get(id);
		if (d == null) {
			d = new Disguise(arena.level(), id, state, p.position(), p.getYRot(), true);
			disguises.put(id, d);
		} else {
			d.setState(state, viewers());
		}
		Msg.actionBar(p, "prophunt.disguised", Component.translatable(state.getBlock().getDescriptionId()));
		Msg.sound(p, SoundEvents.ITEM_PICKUP, 0.6f, 1.2f);
		return true;
	}

	@Override
	public void onUseGameItem(ServerPlayer p, String tag) {
		UUID id = p.getUUID();
		if (!props.contains(id) || !active(id) || !(stage.is(Stage.HIDING) || stage.is(Stage.HUNTING))) {
			return;
		}
		switch (tag) {
			case ITEM_DISGUISE -> new PropDisguiseMenu(p, this).open();
			case ITEM_LOCK -> {
				Disguise d = disguises.get(id);
				if (d != null) {
					Msg.actionBar(p, d.toggleLock() ? "prophunt.locked" : "prophunt.unlocked");
				}
			}
			case ITEM_TAUNT -> taunt(p);
			case ITEM_DECOY -> decoy(p);
			default -> { }
		}
	}

	private static final SoundEvent[] TAUNTS = {SoundEvents.VILLAGER_AMBIENT, SoundEvents.FOX_SCREECH, SoundEvents.PARROT_AMBIENT,
			SoundEvents.ENDERMAN_AMBIENT, SoundEvents.GOAT_SCREAMING_AMBIENT, SoundEvents.PLAYER_BURP};

	/** A sound everyone can hear from where the prop hides, for points. */
	public boolean taunt(ServerPlayer p) {
		UUID id = p.getUUID();
		if (!cooldowns.use("taunt:" + id, runTicks, cfg.tauntCooldownSeconds * 20)) {
			Msg.actionBar(p, "prophunt.cooldown", (cooldowns.ticksLeft("taunt:" + id, runTicks) + 19) / 20);
			return false;
		}
		Vec3 at = disguises.containsKey(id) ? disguises.get(id).position() : p.position();
		ServerLevel level = arena.level();
		level.playSound(null, at.x, at.y, at.z, TAUNTS[random.nextInt(TAUNTS.length)], SoundSource.PLAYERS, 1.6f, 0.9f + random.nextFloat() * 0.3f);
		level.sendParticles(ParticleTypes.NOTE, at.x, at.y + 1.2, at.z, 3, 0.3, 0.2, 0.3, 1);
		if (stage.is(Stage.HUNTING)) {
			points.add(id, cfg.points.propTaunt);
			Msg.actionBar(p, "prophunt.taunted", cfg.points.propTaunt);
		}
		return true;
	}

	/** A copy of the prop's block that stays where it is; hunters who hit it are fooled. */
	public boolean decoy(ServerPlayer p) {
		UUID id = p.getUUID();
		if (!cooldowns.use("decoy:" + id, runTicks, cfg.decoyCooldownSeconds * 20)) {
			Msg.actionBar(p, "prophunt.cooldown", (cooldowns.ticksLeft("decoy:" + id, runTicks) + 19) / 20);
			return false;
		}
		Disguise mine = disguises.get(id);
		BlockState st = mine != null ? mine.state() : Ids.state("minecraft:barrel");
		BlockPos at = p.blockPosition();
		Disguise d = new Disguise(arena.level(), id, st, Vec3.atBottomCenterOf(at), p.getYRot(), false);
		decoys.add(new Decoy(d, id, runTicks + cfg.decoySeconds * 20));
		Msg.actionBar(p, "prophunt.decoy_placed", cfg.decoySeconds);
		Msg.sound(p, SoundEvents.ITEM_FRAME_PLACE, 0.8f, 1f);
		return true;
	}

	// ------------------------------------------------------------------ hunting

	@Override
	protected boolean damageAllowed(ServerPlayer victim, DamageSource source, ServerPlayer attacker) {
		if (!stage.is(Stage.HUNTING)) {
			return false;
		}
		UUID v = victim.getUUID();
		if (attacker == null || attacker == victim) {
			// Falls, the void and the penalties of hunters
			return true;
		}
		return hunters.contains(attacker.getUUID()) && props.contains(v);
	}

	/** Who a hitbox (or a solid block) belongs to. */
	private UUID propAt(Entity e) {
		for (Map.Entry<UUID, Disguise> d : disguises.entrySet()) {
			if (d.getValue().isHitbox(e)) return d.getKey();
		}
		return null;
	}

	private UUID propAt(BlockPos pos) {
		for (Map.Entry<UUID, Disguise> d : disguises.entrySet()) {
			if (pos.equals(d.getValue().solidPos())) return d.getKey();
		}
		return null;
	}

	private Decoy decoyAt(Entity e) {
		for (Decoy d : decoys) {
			if (d.disguise().isHitbox(e)) return d;
		}
		return null;
	}

	private boolean hunting(ServerPlayer p) {
		return stage.is(Stage.HUNTING) && hunters.contains(p.getUUID()) && active(p.getUUID()) && phase == Phase.RUNNING;
	}

	@Override
	public InteractionResult onAttackEntity(ServerPlayer p, Entity target) {
		if (!hunting(p)) {
			return props.contains(p.getUUID()) || propAt(target) != null || decoyAt(target) != null ? InteractionResult.FAIL : InteractionResult.PASS;
		}
		UUID prop = propAt(target);
		if (prop != null) {
			ServerPlayer victim = player(prop);
			if (victim != null) hitProp(p, victim);
			return InteractionResult.FAIL;
		}
		Decoy d = decoyAt(target);
		if (d != null) {
			fooled(p, d);
			return InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}

	@Override
	public InteractionResult onAttackBlock(ServerPlayer p, BlockPos pos) {
		if (!hunting(p)) {
			return InteractionResult.FAIL;
		}
		UUID prop = propAt(pos);
		if (prop != null) {
			ServerPlayer victim = player(prop);
			if (victim != null) hitProp(p, victim);
			return InteractionResult.FAIL;
		}
		lastAttack.put(p.getUUID(), runTicks);
		if (isObject(arena.level().getBlockState(pos))) {
			penalty(p, cfg.wrongBlockDamage, "prophunt.miss_object");
		}
		return InteractionResult.FAIL;
	}

	/** A swing at thin air costs the hunter (a hit or a block in reach is handled elsewhere). */
	@Override
	public void onSwing(ServerPlayer p) {
		if (!hunting(p)) {
			return;
		}
		if (lastAttack.getOrDefault(p.getUUID(), -100) >= runTicks - 1) {
			return;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1f).scale(p.entityInteractionRange()));
		BlockHitResult block = arena.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		if (block.getType() != HitResult.Type.MISS) {
			return;
		}
		for (Disguise d : disguises.values()) {
			if (d.position().distanceToSqr(eye) < 36 && hitsBlockAt(eye, end, d.position())) return;
		}
		for (Decoy d : decoys) {
			if (hitsBlockAt(eye, end, d.disguise().position())) return;
		}
		penalty(p, cfg.airMissDamage, "prophunt.miss_air");
	}

	private static boolean hitsBlockAt(Vec3 from, Vec3 to, Vec3 bottomCenter) {
		return new net.minecraft.world.phys.AABB(bottomCenter.x - 0.5, bottomCenter.y, bottomCenter.z - 0.5,
				bottomCenter.x + 0.5, bottomCenter.y + 1, bottomCenter.z + 0.5).inflate(0.1).clip(from, to).isPresent();
	}

	/** Missing costs health and points (not more often than twice a second). */
	public void penalty(ServerPlayer hunter, double damage, String key) {
		UUID id = hunter.getUUID();
		if (!cooldowns.use("miss:" + id, runTicks, MISS_COOLDOWN)) {
			return;
		}
		points.add(id, cfg.points.hunterMiss);
		Msg.actionBar(hunter, key, cfg.points.hunterMiss);
		Msg.sound(hunter, SoundEvents.VILLAGER_NO, 0.6f, 1.2f);
		if (damage > 0) {
			hunter.hurtServer(arena.level(), hunter.damageSources().magic(), (float) damage);
		}
	}

	/** A hunter hits a prop (through its hitbox or its solid block): damage as from their weapon, health back. */
	public void hitProp(ServerPlayer hunter, ServerPlayer prop) {
		lastAttack.put(hunter.getUUID(), runTicks);
		float strength = hunter.getAttackStrengthScale(0.5f);
		float damage = (float) hunter.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2f + strength * strength * 0.8f);
		hunter.resetAttackStrengthTicker();
		Disguise d = disguises.get(prop.getUUID());
		if (d != null) d.unsolidify(viewers());
		if (prop.hurtServer(arena.level(), hunter.damageSources().playerAttack(hunter), Math.max(1f, damage))) {
			if (props.contains(prop.getUUID())) {
				points.add(hunter.getUUID(), cfg.points.hunterHit);
			}
			hunter.heal((float) cfg.hitHeal);
			Vec3 at = d != null ? d.position() : prop.position();
			arena.level().sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y + 0.8, at.z, 4, 0.3, 0.3, 0.3, 0.1);
			Msg.sound(hunter, SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1f);
		}
	}

	/** A hunter fell for a decoy: it pops, the hunter pays, the prop that placed it scores. */
	private void fooled(ServerPlayer hunter, Decoy d) {
		lastAttack.put(hunter.getUUID(), runTicks);
		decoys.remove(d);
		Vec3 at = d.disguise().position();
		d.disguise().remove(viewers());
		arena.level().sendParticles(ParticleTypes.POOF, at.x, at.y + 0.5, at.z, 8, 0.3, 0.3, 0.3, 0.02);
		cooldowns.reset("miss:" + hunter.getUUID());
		penalty(hunter, cfg.wrongBlockDamage, "prophunt.fooled");
		points.add(d.owner(), cfg.points.propDecoyFooled);
		ServerPlayer owner = player(d.owner());
		if (owner != null) {
			Msg.actionBar(owner, "prophunt.decoy_fooled", name(hunter.getUUID()), cfg.points.propDecoyFooled);
		}
	}

	@Override
	protected void onKilled(ServerPlayer victim, ServerPlayer killer) {
		UUID v = victim.getUUID();
		victim.closeContainer();
		if (props.remove(v)) {
			Disguise d = disguises.remove(v);
			if (d != null) d.remove(viewers());
			unhide(v);
			if (killer != null && hunters.contains(killer.getUUID())) {
				points.add(killer.getUUID(), cfg.points.hunterKill);
				if (cfg.arrowsPerKill > 0) {
					killer.getInventory().add(new ItemStack(Items.ARROW, cfg.arrowsPerKill));
					killer.inventoryMenu.sendAllDataToRemote();
				}
				killer.heal((float) cfg.hitHeal);
			}
			forEveryone(p -> Msg.send(p, "prophunt.found", name(v), killer == null ? Component.literal("?") : Component.literal(name(killer.getUUID())),
					props.size()));
			victim.getInventory().clearContent();
			if (cfg.propsJoinHunters) {
				toHunter.add(v);
				scheduleRespawn(victim);
			} else {
				benched.add(v);
				victim.setGameMode(GameType.SPECTATOR);
				teleport(victim, arena.spectator());
			}
			if (stage.is(Stage.HUNTING) && props.stream().noneMatch(this::active)) {
				stage.go(Stage.ROUND_END);
			}
		} else if (hunters.contains(v)) {
			scheduleRespawn(victim);
		}
		updateUi();
	}

	@Override
	protected void respawn(ServerPlayer p) {
		UUID id = p.getUUID();
		p.setHealth(p.getMaxHealth());
		p.getFoodData().setFoodLevel(20);
		p.clearFire();
		Msg.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.2f);
		boolean released = !stage.is(Stage.HIDING);
		if (toHunter.remove(id)) {
			makeHunter(p, released, true);
			Msg.send(p, "prophunt.now_hunter");
		} else if (hunters.contains(id)) {
			makeHunter(p, released, false);
		}
	}

	@Override
	protected void checkEnd() {
		if (phase != Phase.RUNNING && phase != Phase.COUNTDOWN) {
			return;
		}
		List<UUID> here = present();
		if (here.size() < 2) {
			List<UUID> w = points.winners(here);
			finish(w.isEmpty() ? here : w);
		}
	}

	// ------------------------------------------------------------------ clicks

	@Override
	public InteractionResult onUseBlock(ServerPlayer p, BlockHitResult hit, ItemStack held) {
		UUID id = p.getUUID();
		if (props.contains(id) && active(id)) {
			String tag = Icon.tagOf(held);
			if (tag.isEmpty() || ITEM_DISGUISE.equals(tag)) {
				BlockState st = arena.level().getBlockState(hit.getBlockPos());
				if (!disguise(p, st)) {
					Msg.actionBar(p, "prophunt.not_a_prop");
				}
			} else if (tag.startsWith("game.")) {
				onUseGameItem(p, tag);
			}
			return InteractionResult.SUCCESS;
		}
		// Nothing in the square opens, turns or gets placed
		return InteractionResult.FAIL;
	}

	@Override
	public InteractionResult onUseEntity(ServerPlayer p, Entity target) {
		return InteractionResult.FAIL;
	}

	// ------------------------------------------------------------------ leaving and cleanup

	@Override
	protected void onLeft(UUID id) {
		super.onLeft(id);
		Disguise d = disguises.remove(id);
		if (d != null) d.remove(viewers());
		decoys.removeIf(dec -> {
			if (dec.owner().equals(id)) {
				dec.disguise().remove(viewers());
				return true;
			}
			return false;
		});
		Visibility.forget(id);
		boolean wasHunter = hunters.remove(id);
		props.remove(id);
		benched.remove(id);
		toHunter.remove(id);
		sidebar.forget(id);
		if (phase != Phase.RUNNING || stage.is(Stage.ROUND_END) || stage.is(Stage.WAITING)) {
			return;
		}
		// Keep both sides playing: a prop takes over when the last hunter leaves
		if (wasHunter && hunters.isEmpty()) {
			List<UUID> candidates = new ArrayList<>();
			for (UUID pid : props) if (active(pid)) candidates.add(pid);
			if (candidates.size() >= 2) {
				UUID pick = candidates.get(random.nextInt(candidates.size()));
				Disguise pd = disguises.remove(pick);
				if (pd != null) pd.remove(viewers());
				ServerPlayer np = player(pick);
				if (np != null) {
					makeHunter(np, !stage.is(Stage.HIDING), true);
					Msg.send(np, "prophunt.now_hunter");
				}
			} else {
				stage.go(Stage.ROUND_END);
			}
		} else if (props.stream().noneMatch(this::active) && present().size() >= 2) {
			stage.go(Stage.ROUND_END);
		}
	}

	@Override
	protected void onRestored(ServerPlayer p) {
		TitleService.clearOverride(p);
		Visibility.show(p);
		propBody(p, false);
		sidebar.hide(p);
		bossBar.hide(p);
	}

	@Override
	protected void onWatcherAdded(ServerPlayer p) {
		ui.show(p, sidebar, bossBar);
		for (Disguise d : disguises.values()) d.sendFake(List.of(p));
	}

	@Override
	protected void onClosed() {
		clearRound();
		bossBar.clear();
		for (UUID id : sidebar.viewers()) {
			ServerPlayer p = player(id);
			if (p != null) sidebar.hide(p);
		}
	}

	@Override
	public Component status(ServerPlayer viewer) {
		return Lang.tr(viewer, "prophunt.status", round, cfg.rounds, props.size());
	}

	@Override
	public boolean ownSidebar() {
		return true;
	}
}
