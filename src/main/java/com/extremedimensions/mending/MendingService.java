package com.extremedimensions.mending;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.network.Fx;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.Loaded;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The Mending, the end of the story.
 *
 * <p>Once the seal is broken, every rift into the Seam opens near its <b>Heart</b>: a great island at the centre
 * where all the threads meet, crowned by four great pillars on islands of their own. Step onto the altar and the
 * <b>Starved One</b> rises out of it: what came through the first wound.
 *
 * <ul>
 *   <li>It flies: it circles its prey out of reach, falls on it claws first and climbs away again; it steps through
 *       the veil to come at you from behind; wounded, it bites through your thread (your rift is flung to the edge of
 *       the island); near death, it drags everyone towards it.</li>
 *   <li>It calls the tribe's young, red-eyed and biting, and the Veil Rays turn on the living ({@link Retinue}).</li>
 *   <li>Free, it takes only part of the harm. Feed Seam Silk to the sockets of the four pillars, and once enough burn
 *       at once their light binds it: dragged down and held, it takes double ({@link Binding}).</li>
 *   <li>The Thinned fight beside those who befriended a Hollowling.</li>
 *   <li>Time in the Seam stands still for everyone near the Heart while it lives.</li>
 * </ul>
 *
 * It leaves two things: a <b>Mending Thread</b> and the <b>Severing Shears</b>. The first one used decides the
 * world: mended (calm, the tribe at peace, lighter drain) or severed (no more timer beyond the wound, but whoever
 * falls there loses everything they carried).
 */
public final class MendingService {
	private MendingService() {}

	public static final int HX = 0, HY = 100, HZ = 0, HR = 22;
	/** How far from the Heart the fight reaches: the Heart, its strands and the four pillars' islands. */
	public static final int ARENA = 84;

	private static UUID boss;
	private static ServerBossEvent bar;
	private static int ticks;
	private static int nextTrick = 160;
	/** Its flight: rising out of the Heart, circling its prey, falling on it, or climbing away after a blow. */
	private static final int EMERGE = 0, HOVER = 1, SWOOP = 2, RISE = 3;
	private static int phase = EMERGE;
	private static int phaseTicks;
	private static int nextSwoop = 120;
	private static double orbit;
	private static Vec3 vel = Vec3.ZERO;
	/** Players at the Heart when it woke (the binding asks more sockets of a larger party). */
	private static int party = 1;

	public static boolean isDriven(Entity e) {
		return e.getUUID().equals(boss);
	}

	public static boolean active() {
		return boss != null;
	}

	public static void clear() {
		if (bar != null) {
			bar.removeAllPlayers();
		}
		bar = null;
		boss = null;
		ravenous = false;
		WARNED_AT.clear();
		Binding.forget();
		Retinue.forget();
	}

	/** Bound, it takes more harm; free, less (as long as the pillars stand to bind it: else in full). */
	public static float damageFactor(StarvedOne so) {
		if (!isDriven(so)) {
			return 1.0F;
		}
		// The more who came to end it, the more it has drunk: every blow is divided by that (3.31), as its health itself
		// cannot pass the game's 1024
		float tough = (float) toughness(party);
		if (Binding.bound()) {
			return (float) Math.max(0, Config.STARVED_BOUND_DAMAGE) / tough;
		}
		return (Binding.possible() ? (float) Math.max(0, Config.STARVED_UNBOUND_DAMAGE) : 1.0F) / tough;
	}

	/** How many times its own health it has against this many players: every blow on it is divided by this (3.31). */
	public static double toughness(int players) {
		return Math.max(0.1, healthFor(players) / StarvedOne.baseHealth());
	}

	private static boolean open() {
		return DataStore.sealBroken() && DataStore.ending().isEmpty();
	}

	/** Is this player near the Heart (on it, its strands or the pillars' islands), in the Seam? */
	public static boolean inArena(ServerPlayer p) {
		return SeamService.inSeam(p) && p.distanceToSqr(HX + 0.5, HY, HZ + 0.5) < (double) ARENA * ARENA;
	}

	/**
	 * Where a rift torn at (x, z) lands while the Heart waits: a spot on a ring around it, the same for everyone who steps
	 * through that rift (so a party arrives together, at one gate), another for a rift torn elsewhere.
	 */
	public static int[] arrival(double x, double z) {
		long h = (long) Math.floor(x) * 341873128712L ^ (long) Math.floor(z) * 132897987541L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		double a = (h & 0xFFFFFFL) / (double) 0x1000000 * Math.PI * 2;
		return new int[] {HX + (int) Math.round(Math.cos(a) * 34), HZ + (int) Math.round(Math.sin(a) * 34)};
	}

	public static boolean opensNearHeart() {
		return open();
	}

	// ------------------------------------------------------------------ ticking

	public static void tick(MinecraftServer s) {
		ServerLevel seam = s.getLevel(SeamService.SEAM);
		if (seam == null) {
			return;
		}
		if (open() && (!DataStore.heartBuilt() || s.getTickCount() % 100 == 0)) {
			for (ServerPlayer p : seam.players()) {
				if (p.distanceToSqr(HX, HY, HZ) < 120 * 120) {
					// Build it the first time, and again if the Seam was regenerated and the altar is gone
					BlockPos altar = new BlockPos(HX, HY, HZ);
					if (!DataStore.heartBuilt() || (Loaded.ready(seam, altar) && seam.getBlockState(altar).isAir())) {
						buildHeart(seam);
					}
					break;
				}
			}
		}
		if (boss == null) {
			// Once slain it stays slain: the altar wakes nothing more (its Thread and Shears lie there to be used)
			if (open() && DataStore.heartBuilt() && !DataStore.starvedSlain() && s.getTickCount() % 10 == 0) {
				if (!com.extremedimensions.elder.ElderStir.stirring(STIR)) {
					for (ServerPlayer p : seam.players()) {
						if (!p.isSpectator() && p.distanceToSqr(HX + 0.5, HY + 1, HZ + 0.5) < 3.5 * 3.5) {
							stir(seam, p);
							break;
						}
					}
				}
				if (s.getTickCount() % 20 == 0 && !com.extremedimensions.elder.ElderStir.stirring(STIR)) {
					for (ServerPlayer p : seam.players()) {
						watched(seam, p);
					}
				}
			} else if (open() && DataStore.heartBuilt() && DataStore.starvedSlain() && s.getTickCount() % 20 == 0) {
				reoffer(seam);
				if (s.getTickCount() % 100 == 0) {
					// Whoever stands on the altar now is told why nothing wakes, and what is left to do
					for (ServerPlayer p : seam.players()) {
						if (!p.isSpectator() && p.distanceToSqr(HX + 0.5, HY + 1, HZ + 0.5) < 3.5 * 3.5) {
							p.sendSystemMessage(Component.translatable("gui.extremedimensions.heart.still"), true);
						}
					}
				}
			}
			return;
		}
		drive(seam);
	}

	/** The key of the Heart's wind-up. */
	public static final String STIR = "starved_one";

	/**
	 * The Heart groans before the Starved One rises (3.38): for {@link Config#STARVED_WAKE_SECONDS} a bar counts down for
	 * everyone near. Whoever is still within {@link Config#STARVED_CALM_RANGE} of the altar when it runs out is the one it
	 * rises for (the one who stepped onto it, first of all); with everyone gone that far it sinks back into the hunger,
	 * and stepping onto the altar again wakes it again. With no wind-up (or stepped on in creative) it rises at once.
	 */
	public static void stir(ServerLevel seam, ServerPlayer p) {
		if (Config.STARVED_WAKE_SECONDS <= 0 || p.isCreative()) {
			start(seam, p);
			return;
		}
		com.extremedimensions.elder.ElderStir.begin(new com.extremedimensions.elder.ElderStir.Stir(STIR, "starved_one", seam, HX + 0.5, HY + 1, HZ + 0.5, p,
				Config.STARVED_WAKE_SECONDS, Math.max(8, Config.STARVED_CALM_RANGE), BossEvent.BossBarColor.PURPLE, q -> true, q -> {
					if (boss == null && !DataStore.starvedSlain()) {
						start(seam, q);
					}
				}).pulse(MendingService::groans));
	}

	/** Each second the Heart groans: souls gather over the altar and where it will break through, louder as it comes. */
	private static void groans(ServerLevel level, double x, double y, double z, float share) {
		String dim = level.dimension().identifier().toString();
		WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", x, y + 1, z, 2, 1.5, 2, 0.02, 10 + (int) (share * 24));
		WorldFx.particleIn(dim, "extremedimensions:soul_mote", HX, HY - 2, HZ - 8, 2, 2, 2, 0.02, 8 + (int) (share * 16));
		if ((level.getGameTime() / 20) % 2 == 0) {
			WorldFx.soundIn(dim, "minecraft:entity.warden.heartbeat", "hostile", x, y, z, 1.5f + 2.5f * share, 0.5f);
			WorldFx.soundIn(dim, "extremedimensions:keeper_roar", "hostile", HX, HY - 4, HZ - 8, 0.6f + 2f * share, 0.4f);
		}
	}

	/** When each player near the altar was last told what sleeps beneath it (server tick). */
	private static final java.util.Map<UUID, Long> WARNED_AT = new java.util.HashMap<>();

	/**
	 * A visitor near the altar while the Starved One sleeps beneath it (within {@link Config#STARVED_WARN_RANGE}; 3.38):
	 * souls drift over the altar, and on coming near, and about once a minute after, they are told that stepping onto it
	 * wakes it.
	 */
	private static void watched(ServerLevel seam, ServerPlayer p) {
		double r = Config.STARVED_WARN_RANGE;
		if (p.isSpectator() || p.isCreative() || r <= 0 || p.distanceToSqr(HX + 0.5, HY + 1, HZ + 0.5) >= r * r) {
			return;
		}
		WorldFx.particleIn(seam, "extremedimensions:soul_mote", HX + 0.5, HY + 1.5, HZ + 0.5, 0.4, 0.8, 0.4, 0.01, 6);
		long now = seam.getServer().getTickCount();
		Long at = WARNED_AT.get(p.getUUID());
		if (at == null || now - at >= 1200 || now < at) {
			Component c = Component.translatable("gui.extremedimensions.stir.starved_one.watched").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE);
			if (at == null) {
				p.sendSystemMessage(c);
			}
			WARNED_AT.put(p.getUUID(), now);
			p.sendSystemMessage(c, true);
		}
	}

	/** Ravenous: below {@code starved_enrage_at} of its health it is faster, its tricks come sooner and bring famine. */
	private static boolean ravenous = false;

	/** The Starved One's health for this many players at the Heart: more for every one beyond the first. */
	/** How much health it has in all against this many players (its own, times {@link #toughness}). */
	public static double healthFor(int players) {
		return StarvedOne.baseHealth() * (1 + Math.max(0, players - 1) * Math.max(0, Config.STARVED_HEALTH_PER_PLAYER));
	}

	private static void start(ServerLevel seam, ServerPlayer p) {
		// It rises out of the Heart itself, through the stone
		StarvedOne so = ModEntities.STARVED_ONE.spawn(seam, new BlockPos(HX, HY - 7, HZ - 8), EntitySpawnReason.EVENT);
		if (so == null) {
			return;
		}
		so.setPersistenceRequired();
		so.setTarget(p);
		// The more who come to end it, the more it has drunk
		int players = 0;
		for (ServerPlayer q : seam.players()) {
			if (inArena(q) && !q.isSpectator()) {
				players++;
			}
		}
		// Its own health, whoever comes: the more there are, the more of each blow it shrugs off (see damageFactor)
		var health = so.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(StarvedOne.baseHealth());
			so.setHealth(so.getMaxHealth());
		}
		ravenous = false;
		boss = so.getUUID();
		ticks = 0;
		nextTrick = 200;
		phase = EMERGE;
		phaseTicks = 0;
		nextSwoop = 140;
		vel = Vec3.ZERO;
		party = Math.max(1, players);
		so.act(StarvedOne.CALL);
		Binding.wake(seam);
		Retinue.wake(seam, party);
		bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("boss.extremedimensions.starved_one"),
				BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_20);
		roar(so, 4f);
		WorldFx.particleIn(SeamService.SEAM.identifier().toString(), "extremedimensions:lurker_wisp", HX, HY + 3, HZ - 8, 2, 3, 2, 0, 120);
		WorldFx.punch(seam, so, HX, HZ - 8, 70, 0.8f, 24);
		for (ServerPlayer q : seam.players()) {
			if (inArena(q)) {
				Journal.unlock(q, DataStore.get(q.getUUID()), JournalEntry.HEART);
				Advancements.grant(q, Advancements.HEART_REACHED);
				WorldFx.effect(q, MobEffects.DARKNESS, 5, 0);
				q.sendSystemMessage(Component.translatable("gui.extremedimensions.heart.awake", Binding.healStopAt(),
						Math.max(1, Config.STARVED_SOCKET_SILK), Binding.stunAt()).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
			}
		}
	}

	private static void roar(Entity e, float vol) {
		WorldFx.soundIn(SeamService.SEAM.identifier().toString(), "extremedimensions:keeper_roar", "hostile", e.getX(), e.getY() + 4, e.getZ(), vol, 0.55f);
	}

	private static void drive(ServerLevel seam) {
		Entity ent = seam.getEntity(boss);
		List<ServerPlayer> arena = new ArrayList<>();
		for (ServerPlayer p : seam.players()) {
			if (inArena(p) && !p.isSpectator() && p.isAlive()) {
				arena.add(p);
			}
		}
		if (!(ent instanceof StarvedOne so) || !so.isAlive()) {
			if (ent == null || !ent.isAlive()) {
				end(seam);
				clear();
			}
			return;
		}
		if (arena.isEmpty()) {
			// Nobody left at the Heart: it sinks back into the seam, whole again
			fade(so);
			return;
		}
		ticks++;
		String dim = SeamService.SEAM.identifier().toString();
		if (ticks % 20 == 0) {
			for (ServerPlayer p : arena) {
				bar.addPlayer(p);
			}
		}
		bar.setProgress(so.getHealth() / so.getMaxHealth());
		// White while stunned, green while the pillars heal it (3.33)
		bar.setColor(Binding.bound() ? BossEvent.BossBarColor.WHITE : Binding.healing() ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.PURPLE);
		ServerPlayer target = nearest(so, arena);
		so.setTarget(target);
		if (ticks % 3 == 0) {
			WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", so.getX(), so.getY() + 3, so.getZ(), 1.0, 2.0, 1.0, 0, 2);
		}
		if (Binding.tick(seam, so, arena, ticks)) {
			so.rest();
			phase = HOVER;
			vel = Vec3.ZERO;
		}
		Vec3 held = Binding.bindAt();
		if (held != null) {
			// Bound: dragged down to where the chains hold it, shuddering, its blows and tricks stopped
			fly(so, held, 0.5, 0.35);
			so.getLookControl().setLookAt(target, 10, 10);
			if (ticks % 4 == 0) {
				WorldFx.particleIn(dim, "extremedimensions:soul_mote", so.getX(), so.getY() + 3, so.getZ(), 1, 2, 1, 0, 4);
			}
			return;
		}
		so.setBound(false);
		float f = so.getHealth() / so.getMaxHealth();
		if (!ravenous && f <= Config.STARVED_ENRAGE_AT) {
			// Ravenous: it flies faster and its tricks come sooner
			ravenous = true;
			var speed = so.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.setBaseValue(speed.getBaseValue() * Math.max(1, Config.STARVED_ENRAGE_SPEED));
			}
			roar(so, 5f);
			for (ServerPlayer p : arena) {
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.starved_ravenous"), true);
			}
			nextTrick = Math.min(nextTrick, 40);
		}
		flight(seam, so, target);
		if (phase != EMERGE && Retinue.tick(seam, so, arena, ticks, ravenous)) {
			so.act(StarvedOne.CALL);
		}
		if (phase != EMERGE && phase != SWOOP && --nextTrick <= 0) {
			ThreadLocalRandom rnd = ThreadLocalRandom.current();
			if (f <= 0.25f) {
				nextTrick = 110 + rnd.nextInt(40);
				drag(so, arena);
			} else if (f <= 0.6f && rnd.nextBoolean()) {
				nextTrick = 180 + rnd.nextInt(60);
				bite(so, arena);
			} else {
				nextTrick = 150 + rnd.nextInt(60);
				blink(so, target);
			}
			if (ravenous) {
				nextTrick = nextTrick * 3 / 5;
				// Famine: everyone at the Heart starves a little with every trick
				for (ServerPlayer p : arena) {
					WorldFx.effect(p, MobEffects.HUNGER, 5, 1);
				}
			}
		}
		// The Thinned fight beside those who befriended one of their young
		if (ticks % 300 == 150) {
			for (ServerPlayer p : arena) {
				if (DataStore.get(p.getUUID()).companion) {
					WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", so.getX(), so.getY() + 3, so.getZ(), 1.5, 2, 1.5, 0, 40);
					WorldFx.soundIn(dim, "extremedimensions:lurker_shriek", "hostile", so.getX(), so.getY() + 3, so.getZ(), 2f, 0.8f);
					so.hurtServer(seam, seam.damageSources().magic(), 12.0F);
					break;
				}
			}
		}
	}

	/**
	 * Its flight, each tick: it rises out of the Heart; then it circles its prey a few blocks over it, out of reach of a
	 * sword; now and then it falls on it claws first, rakes it if it gets there, and climbs away slowly (the moment to
	 * strike back), then circles again.
	 */
	private static void flight(ServerLevel seam, StarvedOne so, ServerPlayer target) {
		phaseTicks++;
		double pace = so.getAttributeValue(Attributes.MOVEMENT_SPEED) / 0.3;
		String dim = SeamService.SEAM.identifier().toString();
		so.getLookControl().setLookAt(target, 30, 30);
		switch (phase) {
			case EMERGE -> {
				fly(so, new Vec3(HX + 0.5, HY + 3, HZ - 7.5), 0.12, 0.2);
				if (phaseTicks > 60) {
					begin(HOVER, so, target);
				}
			}
			case HOVER -> {
				orbit += 0.014 * pace;
				double r = 6.5;
				Vec3 goal = new Vec3(target.getX() + Math.cos(orbit) * r, target.getY() + 3.2 + Math.sin(ticks * 0.05) * 0.8,
						target.getZ() + Math.sin(orbit) * r);
				fly(so, keepIn(goal), 0.34 * pace, 0.12);
				if (--nextSwoop <= 0 && so.distanceToSqr(target) < 30 * 30) {
					begin(SWOOP, so, target);
					so.act(StarvedOne.SWOOP);
					WorldFx.soundIn(dim, "minecraft:entity.phantom.swoop", "hostile", so.getX(), so.getY() + 3, so.getZ(), 3f, 0.5f);
				}
			}
			case SWOOP -> {
				Vec3 at = target.position();
				Vec3 from = new Vec3(so.getX() - at.x, 0, so.getZ() - at.z);
				from = from.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : from.normalize();
				fly(so, at.add(from.scale(1.6)).add(0, -0.3, 0), 0.62 * pace, 0.28);
				double dx = target.getX() - so.getX(), dz = target.getZ() - so.getZ(), dy = target.getY() - so.getY();
				if (dx * dx + dz * dz < 3.0 * 3.0 && dy > -3.0 && dy < 2.5) {
					so.act(StarvedOne.CLAW);
					// Its claws: the blow of its attack damage, and a knock away from it
					if (target.hurtServer(seam, so.damageSources().mobAttack(so), (float) so.getAttributeValue(Attributes.ATTACK_DAMAGE))) {
						Vec3 away = new Vec3(target.getX() - so.getX(), 0, target.getZ() - so.getZ());
						away = away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize();
						target.setDeltaMovement(target.getDeltaMovement().add(away.x * 0.9, 0.35, away.z * 0.9));
						com.extremedimensions.server.WorldFx.moved(target);
						WorldFx.soundIn(dim, "minecraft:entity.player.attack.sweep", "hostile", target.getX(), target.getY() + 1, target.getZ(), 1.5f, 0.5f);
						WorldFx.punch(seam, so, target.getX(), target.getZ(), 24, 0.75f, 10);
					}
					begin(RISE, so, target);
				} else if (phaseTicks > 50) {
					so.rest();
					begin(RISE, so, target);
				}
			}
			case RISE -> {
				Vec3 away = new Vec3(so.getX() - target.getX(), 0, so.getZ() - target.getZ());
				away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
				fly(so, keepIn(target.position().add(away.scale(7)).add(0, 4, 0)), 0.16 * pace, 0.1);
				if (phaseTicks > 34) {
					so.rest();
					begin(HOVER, so, target);
				}
			}
			default -> begin(HOVER, so, target);
		}
		// It turns to face its prey as it flies
		double dx = target.getX() - so.getX(), dz = target.getZ() - so.getZ();
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float turned = Mth.approachDegrees(so.getYRot(), yaw, 14.0F);
		so.setYRot(turned);
		so.yBodyRot = turned;
	}

	private static void begin(int next, StarvedOne so, ServerPlayer target) {
		phase = next;
		phaseTicks = 0;
		if (next == HOVER) {
			orbit = Math.atan2(so.getZ() - target.getZ(), so.getX() - target.getX());
			nextSwoop = (int) ((80 + ThreadLocalRandom.current().nextInt(60)) * (ravenous ? 0.6 : 1.0));
		}
	}

	/** Eases its velocity towards the goal (never faster than {@code speed}, slowing as it arrives). */
	private static void fly(StarvedOne so, Vec3 goal, double speed, double ease) {
		Vec3 to = goal.subtract(so.position());
		double d = to.length();
		Vec3 want = d < 1e-3 ? Vec3.ZERO : to.scale(Math.min(speed, d * 0.3) / d);
		vel = vel.add(want.subtract(vel).scale(ease));
		so.setDeltaMovement(vel);
	}

	/** A goal kept within the fight's reach of the Heart, and not far under it. */
	private static Vec3 keepIn(Vec3 goal) {
		double dx = goal.x - HX, dz = goal.z - HZ, r = Math.sqrt(dx * dx + dz * dz), max = ARENA - 6;
		double y = Math.max(goal.y, HY - 24);
		if (r > max) {
			return new Vec3(HX + dx / r * max, y, HZ + dz / r * max);
		}
		return new Vec3(goal.x, y, goal.z);
	}

	private static ServerPlayer nearest(Entity e, List<ServerPlayer> ps) {
		ServerPlayer best = ps.get(0);
		for (ServerPlayer p : ps) {
			if (p.distanceToSqr(e) < best.distanceToSqr(e)) {
				best = p;
			}
		}
		return best;
	}

	/** It steps through the veil and comes out in the air behind its prey, close enough to rake it. */
	private static void blink(StarvedOne so, ServerPlayer p) {
		Vec3 look = p.getViewVector(1.0f);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 to = keepIn(p.position().subtract(flat.scale(4.5)).add(0, 1.5, 0));
		String dim = SeamService.SEAM.identifier().toString();
		WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", so.getX(), so.getY() + 3, so.getZ(), 1, 2, 1, 0, 40);
		so.teleportTo(to.x, to.y, to.z);
		vel = Vec3.ZERO;
		so.act(StarvedOne.BLINK);
		WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", to.x, to.y + 3, to.z, 1, 2, 1, 0, 40);
		WorldFx.soundIn(dim, "extremedimensions:lurker_vanish", "hostile", to.x, to.y + 2, to.z, 2f, 0.5f);
		// and falls on it at once
		begin(SWOOP, so, p);
	}

	/** It bites through the thread: every rift near the Heart is flung to the edge of the island. */
	private static void bite(StarvedOne so, List<ServerPlayer> arena) {
		roar(so, 3f);
		so.act(StarvedOne.BITE);
		WorldFx.punch((ServerLevel) so.level(), so, so.getX(), so.getZ(), 48, 0.55f, 12);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (ServerPlayer p : arena) {
			PlayerData d = DataStore.get(p.getUUID());
			if (!d.inSeam || p.isCreative() || p.isSpectator()) {
				continue;
			}
			double a = rnd.nextDouble() * Math.PI * 2;
			d.riftX = HX + Math.cos(a) * (HR - 3) + 0.5;
			d.riftZ = HZ + Math.sin(a) * (HR - 3) + 0.5;
			d.riftY = HY + 1;
			WorldFx.effect(p, MobEffects.DARKNESS, 3, 0);
			WorldFx.soundSelf(p, "minecraft:entity.leash_knot.break", 1.0f, 0.5f);
		}
	}

	/** Near death it drags everyone close towards it. */
	private static void drag(StarvedOne so, List<ServerPlayer> arena) {
		roar(so, 4f);
		so.act(StarvedOne.DRAG);
		WorldFx.punch((ServerLevel) so.level(), so, so.getX(), so.getZ(), 40, 0.6f, 12);
		for (ServerPlayer p : arena) {
			if (p.isCreative() || p.isSpectator()) {
				continue;
			}
			Vec3 to = so.position().subtract(p.position());
			double dist = to.length();
			if (dist < 26 && dist > 3) {
				// A short, hard yank towards it, never into a wall
				Vec3 step = to.normalize().scale(Math.min(6.0, dist * 0.4));
				BlockPos dest = BlockPos.containing(p.getX() + step.x, p.getY(), p.getZ() + step.z);
				ServerLevel seam = (ServerLevel) so.level();
				for (int dy = 2; dy >= -2; dy--) {
					BlockPos q = dest.above(dy);
					if (seam.getBlockState(q).isAir() && seam.getBlockState(q.above()).isAir() && !seam.getBlockState(q.below()).isAir()) {
						WorldFx.particles(p, "extremedimensions:lurker_wisp", 1.0, 0.5, 0, 10);
						p.teleportTo(p.getX() + step.x, q.getY(), p.getZ() + step.z);
						break;
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ the pillars

	/** Is this the node atop one of the four pillars round the Heart? */
	public static boolean isSocket(ServerLevel seam, BlockPos pos) {
		return seam.getBlockState(pos).is(Binding.FEEDING) || seam.getBlockState(pos).is(Binding.QUENCHED)
				? Binding.socketAt(seam, pos) >= 0 : false;
	}

	/** Seam Silk held to a pillar's socket: it drinks, and burns once it has drunk enough (see {@link Binding}). */
	public static InteractionResult onSocket(ServerPlayer p, BlockPos pos, ItemStack stack) {
		if (!Binding.silk(stack) || !(p.level() instanceof ServerLevel seam)) {
			return InteractionResult.PASS;
		}
		int i = Binding.socketAt(seam, pos);
		if (i < 0) {
			return InteractionResult.PASS;
		}
		return Binding.feed(p, i, stack, boss != null, party);
	}

	/** The fight is over (won, or nobody left): the sockets go dark, the young fade, the rays calm. */
	private static void end(ServerLevel seam) {
		Binding.end(seam);
		Retinue.end(seam);
	}

	// ------------------------------------------------------------------ the Heart

	/** The Heart of the Seam: a great island of hush brick around an altar of silk, crowned by four pillars. */
	private static void buildHeart(ServerLevel seam) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		BlockState stone = ModBlocks.HUSH_STONE.defaultBlockState();
		BlockState brick = ModBlocks.HUSH_BRICKS.defaultBlockState();
		BlockState vein = ModBlocks.SEAM_VEIN.defaultBlockState();
		for (int dx = -HR; dx <= HR; dx++) {
			for (int dz = -HR; dz <= HR; dz++) {
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist > HR) {
					continue;
				}
				double f = 1 - dist / HR;
				int depth = 3 + (int) Math.round(Math.pow(f, 0.6) * 26) + rnd.nextInt(2);
				for (int y = HY - depth; y <= HY; y++) {
					BlockState s = y == HY ? ((int) dist % 6 == 0 ? vein : brick) : (rnd.nextInt(14) == 0 ? vein : stone);
					seam.setBlock(new BlockPos(HX + dx, y, HZ + dz), s, 2);
				}
				for (int y = HY + 1; y <= HY + 6; y++) {
					seam.setBlock(new BlockPos(HX + dx, y, HZ + dz), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		// The altar
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				seam.setBlock(new BlockPos(HX + dx, HY, HZ + dz), vein, 2);
			}
		}
		// Pillars of silk around the rim
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4 + Math.PI / 8;
			int x = HX + (int) Math.round(Math.cos(a) * (HR - 2));
			int z = HZ + (int) Math.round(Math.sin(a) * (HR - 2));
			int h = 6 + rnd.nextInt(6);
			for (int k = 1; k <= h; k++) {
				seam.setBlock(new BlockPos(x, HY + k, z), (k % 3 == 0 ? vein : brick), 2);
			}
			seam.setBlock(new BlockPos(x, HY + h + 1, z), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
		}
		// The crown: obelisks on floating plinths and a halo turning high above, built over the next seconds
		com.extremedimensions.seam.SeamArchitect.Plan crown = new com.extremedimensions.seam.SeamArchitect.Plan();
		com.extremedimensions.seam.SeamArchitect.crown(crown, new java.util.Random(seam.getSeed() ^ 0x4EA27L), HX, HY, HZ);
		crown.queue();
		SeamService.addFalls(crown.falls);
		DataStore.setHeartBuilt(true);
	}

	// ------------------------------------------------------------------ the end

	public static void onDefeat(StarvedOne so) {
		if (bar != null) {
			bar.removeAllPlayers();
		}
		boss = null;
		bar = null;
		DataStore.setStarvedSlain(true);
		ServerLevel seam = (ServerLevel) so.level();
		end(seam);
		offerEnding(seam);
		String dim = SeamService.SEAM.identifier().toString();
		WorldFx.particleIn(dim, "extremedimensions:soul_mote", so.getX(), so.getY() + 3, so.getZ(), 2, 3, 2, 0, 200);
		WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", so.getX(), so.getY() + 3, so.getZ(), 2, 3, 2, 0, 120);
		java.util.List<ServerPlayer> there = new java.util.ArrayList<>();
		for (ServerPlayer p : seam.players()) {
			if (inArena(p) && !p.isSpectator() && !p.isCreative()) {
				there.add(p);
				// Each who fought it takes something from its hoard of everything it ever ate
				for (int i = 0; i < Math.max(0, com.extremedimensions.config.Config.RELIC_STARVED_ONE); i++) {
					WorldFx.give(p, com.extremedimensions.relic.RelicService.random());
				}
				Advancements.grant(p, Advancements.STARVED_SLAIN);
				Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.WANDERER);
				WorldFx.soundSelf(p, "extremedimensions:lurker_death", 1.0f, 0.5f);
			}
		}
		com.extremedimensions.compat.DiscordWebhook.starvedOne(there);
	}

	/**
	 * The Thread and the Shears on the altar. They lie there until taken, as long as that takes, and fire cannot burn
	 * them (they are fireproof items); should they still be lost, the altar offers them again ({@link #reoffer}).
	 */
	private static void offerEnding(ServerLevel seam) {
		offerEnding(seam, HX + 0.5, HY + 1.2, HZ + 0.5);
		lastOffer = seam.getGameTime();
	}

	/** The Thread and the Shears laid down here, never to despawn. */
	public static void offerEnding(ServerLevel level, double x, double y, double z) {
		for (ItemStack stack : new ItemStack[] {new ItemStack(ModItems.MENDING_THREAD), new ItemStack(ModItems.SEVERING_SHEARS)}) {
			net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level, x, y, z, stack, 0, 0.15, 0);
			item.setDefaultPickUpDelay();
			item.setUnlimitedLifetime();
			level.addFreshEntity(item);
		}
	}

	/** When the altar last offered the Thread and the Shears (game time). */
	private static long lastOffer = -100000;

	/**
	 * The Starved One is slain and no ending is chosen yet, but neither the Thread nor the Shears is to be found (taken
	 * and lost, or thrown away): whoever stands on the altar with neither is offered both again, once a minute at most.
	 * (Only the first used decides the world; any other crumbles, so a second pair changes nothing.)
	 */
	private static void reoffer(ServerLevel seam) {
		if (seam.getGameTime() - lastOffer < 1200) {
			return;
		}
		net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(HX - 24, HY - 24, HZ - 24, HX + 25, HY + 24, HZ + 25);
		boolean lying = !seam.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, around,
				e -> e.getItem().is(ModItems.MENDING_THREAD) || e.getItem().is(ModItems.SEVERING_SHEARS)).isEmpty();
		if (lying) {
			return;
		}
		for (ServerPlayer p : seam.players()) {
			if (!p.isSpectator() && p.distanceToSqr(HX + 0.5, HY + 1, HZ + 0.5) < 3.5 * 3.5
					&& p.getInventory().countItem(ModItems.MENDING_THREAD) == 0 && p.getInventory().countItem(ModItems.SEVERING_SHEARS) == 0) {
				offerEnding(seam);
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.heart.offers_again"));
				return;
			}
		}
	}

	/** The first of the two used decides the world. Afterwards the other one only crumbles. */
	public static InteractionResult useEnding(ServerPlayer p, ItemStack stack) {
		boolean mend = stack.is(ModItems.MENDING_THREAD);
		stack.shrink(1);
		if (!DataStore.ending().isEmpty()) {
			WorldFx.particles(p, "minecraft:large_smoke", 1.0, 0.3, 0.02, 12);
			WorldFx.soundSelf(p, "minecraft:block.wool.break", 1.0f, 0.6f);
			return InteractionResult.SUCCESS;
		}
		DataStore.setEnding(mend ? "mended" : "severed");
		MinecraftServer s = WorldFx.server();
		for (ServerPlayer q : s.getPlayerList().getPlayers()) {
			WorldFx.fx(q, mend ? Fx.SAFE : Fx.SEAL, 0);
			WorldFx.soundSelf(q, mend ? "extremedimensions:portal_home" : "extremedimensions:collapse", 1.0f, mend ? 0.8f : 0.6f);
			Journal.unlock(q, DataStore.get(q.getUUID()), JournalEntry.ENDING);
			// The story is chosen, but the world is not finished: the six threads and the Weaving are its true end
			q.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.extremedimensions.ending.unfinished")
					.withStyle(net.minecraft.ChatFormatting.GOLD));
		}
		Advancements.grant(p, mend ? Advancements.ENDING_MENDED : Advancements.ENDING_SEVERED);
		// The one who chose takes the thread of the Heart: the story goes on in their hand
		com.extremedimensions.tapestry.Tapestry.earn(p, com.extremedimensions.tapestry.Tapestry.Panel.HEART);
		com.extremedimensions.compat.DiscordWebhook.ending(mend, p);
		return InteractionResult.SUCCESS;
	}

	/** It sinks back into the seam (nobody left at the Heart, or a stray one left over from before a restart). */
	public static void fade(StarvedOne so) {
		boolean mine = isDriven(so);
		WorldFx.particleIn(SeamService.SEAM.identifier().toString(), "extremedimensions:lurker_wisp", so.getX(), so.getY() + 3, so.getZ(), 1.5, 2.5, 1.5, 0, 60);
		so.discard();
		if (!mine) {
			return;
		}
		if (bar != null) {
			bar.removeAllPlayers();
		}
		boss = null;
		bar = null;
		if (so.level() instanceof ServerLevel seam) {
			end(seam);
		}
	}

	/** Operator command: break the seal (as if the Dragon died) for testing. */
	public static void breakSeal(MinecraftServer s) {
		com.extremedimensions.world.WorldService.onDragonDeath(s);
	}
}
