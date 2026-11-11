package com.extremedimensions.mending;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.elder.ElderFx;
import com.extremedimensions.elder.HeartArena;
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
 * <p>Since 3.48 it comes as the Elders do: after the wind-up it claws its way up out of the Heart under a darkening sky
 * (nothing hurts it meanwhile), the pillars lighting one by one, and bursts free to its own music; the Heart raises an
 * arena round the fight ({@link HeartArena}) that changes twice as it weakens, with an upheaval and islands falling;
 * once a fight it drinks time; and it dies slowly, its chains snapping and its embers going out, before the Thread and
 * the Shears are laid. Its fight reaches the whole of the Heart's island group (see {@link #within}), and with nobody
 * left in it the Starved One waits {@code starved_leave_seconds} over the Heart before it sinks back.
 *
 * It leaves two things: a <b>Mending Thread</b> and the <b>Severing Shears</b>. The first one used decides the
 * world: mended (calm, the tribe at peace, lighter drain) or severed (no more timer beyond the wound, but whoever
 * falls there loses everything they carried).
 */
public final class MendingService {
	private MendingService() {}

	public static final int HX = 0, HY = 100, HZ = 0, HR = 22;
	/** How far from the Heart (across) the Starved One flies at most, and its fight reaches at least: the pillars' islands. */
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
	/** Ticks nobody has been at its fight (3.48): it sinks back once they come to {@code starved_leave_seconds}. */
	private static int alone;
	/** Ticks it has been rising out of the Heart (3.48), or -1 once it is free of it. */
	private static int rise = -1;
	/** How far its fight has changed (3.48): 0, then 1 below {@code starved_phase_one}, 2 below {@code starved_phase_two}. */
	private static int stage;
	/** When its fight last changed (its clock), so that its drinking of time waits a little after. */
	private static int changedAt = -1000;
	/** Ticks into its drinking of time (3.48), -1 while it does not drink; and whether it has drunk in this fight. */
	private static int drink = -1;
	private static boolean drunk;
	/** Ticks it hangs dazed, an island of its arena having fallen on it. */
	private static int dazed;
	/** The pillars' nodes as it fell, for the chains that snap as it dies. */
	private static final BlockPos[] DYING = new BlockPos[Binding.PILLARS];

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
		HeartArena.clear();
		HeartScenes.forget();
		reset();
		java.util.Arrays.fill(DYING, null);
	}

	/** What a fight keeps of its own, back as it was before one. */
	private static void reset() {
		alone = 0;
		rise = -1;
		stage = 0;
		changedAt = -1000;
		drink = -1;
		drunk = false;
		dazed = 0;
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
		// While it drinks time it shrugs off most of every blow, as an Elder in its ultimate (elder_ultimate_guard)
		float ward = drink >= 0 ? (float) Math.max(0, 1 - Math.min(1, Config.ELDER_ULTIMATE_GUARD)) : 1.0F;
		return (Binding.possible() ? (float) Math.max(0, Config.STARVED_UNBOUND_DAMAGE) : 1.0F) * ward / tough;
	}

	/** How many times its own health it has against this many players: every blow on it is divided by this (3.31). */
	public static double toughness(int players) {
		return Math.max(0.1, healthFor(players) / StarvedOne.baseHealth());
	}

	private static boolean open() {
		return DataStore.sealBroken() && DataStore.ending().isEmpty();
	}

	/**
	 * Is this player at the Heart's fight, in the Seam: anywhere in the Heart's island group (see {@link #within})? Who
	 * flew out to a pillar to quench its node, high up its shaft, is there as much as who stands on the altar.
	 */
	public static boolean inArena(ServerPlayer p) {
		return SeamService.inSeam(p) && within(p.getX(), p.getY(), p.getZ());
	}

	/**
	 * Whether a point is within the Heart's fight (3.48): the whole of its island group, as a cylinder round the Heart
	 * {@code starved_arena_reach} blocks across (the pillars stand 64 out, their islands 14 more; never less than
	 * {@link #ARENA}) from {@code starved_arena_below} under its floor (the islands' undersides) to
	 * {@code starved_arena_above} over it (the nodes, some forty to fifty up, and the shafts' tops). (Before 3.48 it was a
	 * ball round the Heart, and the top of a node far out on its shaft lay outside it: whoever flew there alone to quench
	 * it saw the Starved One sink back.)
	 */
	public static boolean within(double x, double y, double z) {
		double dx = x - (HX + 0.5), dz = z - (HZ + 0.5);
		double r = Math.max(ARENA, Config.STARVED_ARENA_REACH);
		return dx * dx + dz * dz < r * r && y > HY - Math.max(0, Config.STARVED_ARENA_BELOW) && y < HY + Math.max(0, Config.STARVED_ARENA_ABOVE);
	}

	/** Who counts as at its fight now: in it, alive (not dying), and not a spectator. */
	private static boolean present(ServerPlayer p) {
		return inArena(p) && !p.isSpectator() && p.isAlive() && !p.isDeadOrDying();
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
			// its own voice under the stone, louder as it comes (3.48: no longer a borrowed Warden's heart and Keeper's roar)
			WorldFx.soundIn(dim, "extremedimensions:elder.starved_one.ambient", "hostile", x, y, z, 1.5f + 2.5f * share, 0.9f + 0.1f * share);
			WorldFx.soundIn(dim, "extremedimensions:elder.starved_one.voice", "hostile", HX + HeartScenes.EX, HY - 4, HZ + HeartScenes.EZ, 0.6f + 2f * share,
					0.9f + 0.1f * share);
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
		// It rises out of the Heart itself, through the stone, from under where it breaks the floor open
		StarvedOne so = ModEntities.STARVED_ONE.spawn(seam, new BlockPos(HX + HeartScenes.EX, HY - HeartScenes.DEPTH, HZ + HeartScenes.EZ),
				EntitySpawnReason.EVENT);
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
		reset();
		java.util.Arrays.fill(DYING, null);
		// It rises first (3.48): nothing hurts it meanwhile, the pillars light one by one, and it bursts free at the end
		rise = 0;
		so.setRising(0);
		Binding.wake(seam, false);
		// The sky darkens and the fog closes in for everyone at the Heart, as under an Elder; its bar fills as it rises
		bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("boss.extremedimensions.starved_one"),
				BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_20);
		bar.setDarkenScreen(true);
		bar.setCreateWorldFog(true);
		bar.setProgress(0);
		for (ServerPlayer q : seam.players()) {
			if (present(q)) {
				bar.addPlayer(q);
			}
		}
		HeartScenes.begin(seam, so, riseLength());
		// ... and the Heart's arena rises round the fight
		HeartArena.raise(so);
		for (ServerPlayer q : seam.players()) {
			if (inArena(q)) {
				Journal.unlock(q, DataStore.get(q.getUUID()), JournalEntry.HEART);
				Advancements.grant(q, Advancements.HEART_REACHED);
			}
		}
	}

	/** How long it takes to rise out of the Heart, in ticks ({@code starved_rise_seconds}). */
	static int riseLength() {
		return Math.max(40, (int) Math.round(Config.STARVED_RISE_SECONDS * 20));
	}

	/**
	 * One tick of its rise out of the Heart: it is drawn up from under the floor, the Heart's ground quaking harder and
	 * cracking, souls spiralling in, the four pillars lighting one after another; the floor breaks open over it and it
	 * claws its way up, until it bursts free.
	 */
	private static void rising(ServerLevel seam, StarvedOne so) {
		int length = riseLength();
		rise++;
		double k = Math.min(1, rise / (double) length);
		so.setRising((float) k);
		bar.setProgress((float) k);
		// The pillars light one after another (their nodes open, their strands of souls run in to the Heart)
		for (int i = 0; i < Binding.PILLARS; i++) {
			if (HeartScenes.at(rise, length, 0.3 + 0.12 * i)) {
				BlockPos n = Binding.light(seam, i);
				if (n != null) {
					HeartScenes.lit(seam, n);
				}
			}
		}
		java.util.List<BlockPos> lit = new java.util.ArrayList<>();
		for (int i = 0; i < Binding.PILLARS; i++) {
			if (Binding.node(i) != null) {
				lit.add(Binding.node(i));
			}
		}
		HeartScenes.rising(seam, so, rise, length, lit);
		// Drawn up a little under the stone, then, the floor broken open, up out of it, hands first
		double from = HY - HeartScenes.DEPTH, to = HY + 1;
		double want = k < 0.6 ? from + 2 * k / 0.6 : from + 2 + (to - from - 2) * HeartScenes.ease((k - 0.6) / 0.38);
		so.setDeltaMovement(HX + HeartScenes.EX + 0.5 - so.getX(), want - so.getY(), HZ + HeartScenes.EZ + 0.5 - so.getZ());
		vel = Vec3.ZERO;
		if (rise >= length) {
			burst(seam, so);
		}
	}

	/** It bursts free of the Heart: a shockwave, its roar and a flash, its name across the screen; the fight begins. */
	private static void burst(ServerLevel seam, StarvedOne so) {
		rise = -1;
		so.setRising(-1);
		phase = EMERGE;
		phaseTicks = 0;
		so.act(StarvedOne.CALL);
		// (the sky stays dark over its fight; the fog lifts, to close in again as it drinks time)
		bar.setCreateWorldFog(false);
		HeartScenes.burst(seam, so);
		Retinue.wake(seam, party);
		for (ServerPlayer q : seam.players()) {
			if (inArena(q)) {
				WorldFx.effect(q, MobEffects.DARKNESS, 3, 0);
				q.sendSystemMessage(Component.translatable("gui.extremedimensions.heart.awake", Binding.healStopAt(),
						Math.max(1, Config.STARVED_SOCKET_SILK), Binding.stunAt()).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
			}
		}
	}

	private static void roar(Entity e, float vol) {
		WorldFx.soundIn(SeamService.SEAM.identifier().toString(), "extremedimensions:elder.starved_one.roar", "hostile", e.getX(), e.getY() + 4, e.getZ(), vol, 1f);
	}

	/** Its voice: a snarl as it turns to a trick. */
	private static void voice(Entity e, float vol) {
		WorldFx.soundIn(SeamService.SEAM.identifier().toString(), "extremedimensions:elder.starved_one.voice", "hostile", e.getX(), e.getY() + 4, e.getZ(), vol,
				0.9f + 0.2f * ThreadLocalRandom.current().nextFloat());
	}

	private static void drive(ServerLevel seam) {
		Entity ent = seam.getEntity(boss);
		List<ServerPlayer> arena = new ArrayList<>();
		for (ServerPlayer p : seam.players()) {
			if (present(p)) {
				arena.add(p);
			}
		}
		if (!(ent instanceof StarvedOne so) || !so.isAlive()) {
			if (ent == null || !ent.isAlive()) {
				UUID gone = boss;
				end(seam);
				HeartArena.release(gone);
				clear();
			}
			return;
		}
		ticks++;
		String dim = SeamService.SEAM.identifier().toString();
		if (ticks % 20 == 0) {
			for (ServerPlayer p : arena) {
				bar.addPlayer(p);
			}
			// (and lets go of those who left its fight: the sky lifts for them)
			for (ServerPlayer q : List.copyOf(bar.getPlayers())) {
				if (q.level() != seam || !inArena(q)) {
					bar.removePlayer(q);
				}
			}
		}
		if (ticks % 40 == 1) {
			// Its music for everyone at its fight, asked for again every two seconds (it fades once it stops coming)
			ElderFx.music(seam, MendingService::inArena, ElderFx.MUSIC_STARVED_ONE);
		}
		if (arena.isEmpty()) {
			// Nobody at its fight: it waits a while for its prey to come back, then sinks back into the seam, whole again
			if (++alone >= Math.max(0, Config.STARVED_LEAVE_SECONDS) * 20) {
				fade(so);
				return;
			}
		} else {
			alone = 0;
		}
		if (rise >= 0) {
			rising(seam, so);
			return;
		}
		bar.setProgress(so.getHealth() / so.getMaxHealth());
		// White while stunned, green while the pillars heal it (3.33)
		bar.setColor(Binding.bound() ? BossEvent.BossBarColor.WHITE : Binding.healing() ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.PURPLE);
		HeartArena.tick(so, arena);
		if (arena.isEmpty()) {
			waiting(seam, so, arena);
			return;
		}
		ServerPlayer target = nearest(so, arena);
		so.setTarget(target);
		if (ticks % 3 == 0) {
			WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", so.getX(), so.getY() + 3, so.getZ(), 1.0, 2.0, 1.0, 0, 2);
		}
		if (Binding.tick(seam, so, arena, ticks)) {
			so.rest();
			phase = HOVER;
			vel = Vec3.ZERO;
			if (drink >= 0) {
				// Every pillar quenched while it drank: the chains tear its drinking of time out of it, unspent
				drink = -1;
				bar.setCreateWorldFog(false);
				HeartScenes.drinkBroken(seam, so);
			}
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
		if (dazed > 0) {
			// An island fell on it: it hangs dazed a moment, sinking, stars about its head
			dazed--;
			so.rest();
			vel = vel.scale(0.8);
			so.setDeltaMovement(vel.x, -0.04, vel.z);
			if (ticks % 4 == 0) {
				WorldFx.particleIn(dim, "minecraft:crit", so.getX(), so.getY() + so.getBbHeight() * 0.9, so.getZ(), 1.5, 0.4, 1.5, 0.05, 6);
			}
			return;
		}
		float f = so.getHealth() / so.getMaxHealth();
		// Its fight changes twice as it weakens (3.48): an upheaval, and islands falling
		int want = f <= Config.STARVED_PHASE_TWO ? 2 : f <= Config.STARVED_PHASE_ONE ? 1 : 0;
		while (stage < want) {
			stage++;
			changed(seam, so, arena);
		}
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
		// Once a fight, weakened, it drinks time (3.48)
		if (drink < 0 && !drunk && Config.STARVED_DRINK_AT > 0 && f <= Config.STARVED_DRINK_AT && phase != SWOOP && phase != EMERGE
				&& ticks - changedAt > 100) {
			drunk = true;
			drink = 0;
			so.act(StarvedOne.CALL);
			bar.setCreateWorldFog(true);
			HeartScenes.drinkBegins(seam, so, drinkLength());
		}
		if (drink >= 0) {
			drinking(seam, so, arena);
			return;
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
	 * Nobody at its fight (3.48): it hangs over the Heart and waits for its prey to come back; the pillars feed it
	 * meanwhile (and hold it, if all four still burn quenched), and what its fight holds goes on as it stood.
	 */
	private static void waiting(ServerLevel seam, StarvedOne so, List<ServerPlayer> arena) {
		so.rest();
		Binding.tick(seam, so, arena, ticks);
		Vec3 held = Binding.bindAt();
		if (held != null) {
			fly(so, held, 0.5, 0.35);
			return;
		}
		so.setBound(false);
		if (drink >= 0) {
			drink = -1;
			bar.setCreateWorldFog(false);
			HeartScenes.drinkBroken(seam, so);
		}
		phase = HOVER;
		fly(so, new Vec3(HX + 0.5, HY + 8 + Math.sin(ticks * 0.03) * 1.5, HZ + 0.5), 0.15, 0.06);
	}

	/** Its fight changes ({@link #stage} has just grown): its own sound, it rears up, and the Heart's arena breaks up. */
	private static void changed(ServerLevel seam, StarvedOne so, List<ServerPlayer> arena) {
		changedAt = ticks;
		String dim = SeamService.SEAM.identifier().toString();
		WorldFx.soundIn(dim, "extremedimensions:elder.starved_one.phase", "hostile", so.getX(), so.getY() + 4, so.getZ(), 6f, 1f);
		so.act(StarvedOne.CALL);
		ElderFx.cinema(seam, MendingService::inArena, ElderFx.CINEMA_STARVED_ONE, com.extremedimensions.network.ModPackets.BossFxPayload.FLASH, 0, 0.35f, "");
		HeartArena.change(so, stage, arena);
		for (ServerPlayer p : arena) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.heart.phase_" + Math.min(2, stage)).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE),
					true);
		}
	}

	/** How long its drinking of time draws in before the soul fire bursts out of it, in ticks. */
	static int drinkLength() {
		return Math.max(60, (int) Math.round(Config.STARVED_DRINK_SECONDS * 20));
	}

	/**
	 * One tick of its drinking of time (3.48): it hangs high over the Heart and draws the light out of it, and everyone
	 * in its reach towards it, slowed; at the end the soul fire it drank bursts out of it.
	 */
	private static void drinking(ServerLevel seam, StarvedOne so, List<ServerPlayer> arena) {
		int length = drinkLength();
		drink++;
		// (under the crown, clear of its hanging heart)
		fly(so, new Vec3(HX + 0.5, HY + 11, HZ + 0.5), 0.3, 0.12);
		ServerPlayer target = nearest(so, arena);
		so.getLookControl().setLookAt(target, 20, 20);
		HeartScenes.drinking(seam, so, drink, length, arena);
		if (drink >= length) {
			drink = -1;
			bar.setCreateWorldFog(false);
			HeartScenes.nova(seam, so, arena);
			nextTrick = Math.max(nextTrick, 80);
			begin(HOVER, so, target);
		}
	}

	/** It hangs dazed for a while (an island of its arena fell on it; see HeartArena). */
	public static void daze(int ticks) {
		dazed = Math.max(dazed, Math.max(0, ticks));
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
				// Free of the Heart, it springs up out of the hole it tore and spreads itself over the floor
				fly(so, new Vec3(HX + HeartScenes.EX + 0.5, HY + 5, HZ + HeartScenes.EZ + 0.5), 0.3, 0.2);
				if (phaseTicks > 40) {
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
					WorldFx.soundIn(dim, "extremedimensions:elder.starved_one.swoop", "hostile", so.getX(), so.getY() + 3, so.getZ(), 5f, 1f);
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
		if (phase != EMERGE) {
			// (a goal in the ground is over it instead, so it rises over what stands there before it gets there)
			double under = floor(so, goal.x, goal.y, goal.z);
			if (!Double.isNaN(under) && under > goal.y) {
				goal = new Vec3(goal.x, under, goal.z);
			}
		}
		Vec3 to = goal.subtract(so.position());
		double d = to.length();
		Vec3 want = d < 1e-3 ? Vec3.ZERO : to.scale(Math.min(speed, d * 0.3) / d);
		vel = vel.add(want.subtract(vel).scale(ease));
		// It drifts through stone as through smoke, but never sinks into the ground under it: over whatever it drifts into
		// it rises at once (only as it wakes does it come up through the stone of the Heart, on purpose)
		double floor = phase == EMERGE ? Double.NaN : floor(so, so.getX() + vel.x, so.getY() + vel.y, so.getZ() + vel.z);
		if (!Double.isNaN(floor) && so.getY() + vel.y < floor) {
			vel = new Vec3(vel.x, Math.max(0, vel.y), vel.z);
			so.setDeltaMovement(vel.x, floor - so.getY(), vel.z);
		} else {
			so.setDeltaMovement(vel);
		}
	}

	/** The ground under the whole of it at (x, z), its feet at {@code y} (see BossGround); NaN with none near. */
	private static double floor(StarvedOne so, double x, double y, double z) {
		return com.extremedimensions.server.BossGround.box(so.level(), x, z, so.getBbWidth() / 2, y);
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
		// (out of the air over the ground there, never in it)
		double floor = floor(so, to.x, to.y, to.z);
		if (!Double.isNaN(floor) && floor > to.y) {
			to = new Vec3(to.x, floor, to.z);
		}
		String dim = SeamService.SEAM.identifier().toString();
		WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", so.getX(), so.getY() + 3, so.getZ(), 1, 2, 1, 0, 40);
		so.teleportTo(to.x, to.y, to.z);
		vel = Vec3.ZERO;
		so.act(StarvedOne.BLINK);
		WorldFx.particleIn(dim, "extremedimensions:lurker_wisp", to.x, to.y + 3, to.z, 1, 2, 1, 0, 40);
		WorldFx.soundIn(dim, "extremedimensions:lurker_vanish", "hostile", to.x, to.y + 2, to.z, 2f, 0.5f);
		voice(so, 4f);
		// and falls on it at once
		begin(SWOOP, so, p);
	}

	/** It bites through the thread: every rift near the Heart is flung to the edge of the island. */
	private static void bite(StarvedOne so, List<ServerPlayer> arena) {
		voice(so, 5f);
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

	/**
	 * The fight is over (won, or nobody left): the sockets go dark, the young fade, the rays calm, the floor it tore
	 * open closes and its music ends.
	 */
	private static void end(ServerLevel seam) {
		Binding.end(seam);
		Retinue.end(seam);
		HeartScenes.close(seam);
		HeartScenes.silence(seam);
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
		boolean wasDrinking = drink >= 0;
		boss = null;
		bar = null;
		DataStore.setStarvedSlain(true);
		ServerLevel seam = (ServerLevel) so.level();
		// Its chains to the pillars still hold as it dies, and snap one by one (see dying); its young and rays are gone at once
		for (int i = 0; i < Binding.PILLARS; i++) {
			DYING[i] = Binding.node(i);
		}
		Retinue.end(seam);
		HeartScenes.silence(seam);
		if (wasDrinking) {
			HeartScenes.drinkBroken(seam, so);
		}
		// Its arena comes apart as it dies
		HeartArena.release(so);
		reset();
		// (the altar offers nothing until the Thread and the Shears are laid at the end of its death)
		lastOffer = seam.getGameTime();
		java.util.List<ServerPlayer> there = new java.util.ArrayList<>();
		for (ServerPlayer p : seam.players()) {
			if (inArena(p) && !p.isSpectator() && !p.isCreative()) {
				there.add(p);
				// Each who fought it takes something from its hoard of everything it ever ate
				for (int i = 0; i < Math.max(0, com.extremedimensions.config.Config.RELIC_STARVED_ONE); i++) {
					// (a great foe: its relics are drawn from the great foes' odds)
					WorldFx.give(p, com.extremedimensions.relic.RelicService.random(true));
				}
				Advancements.grant(p, Advancements.STARVED_SLAIN);
				Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.WANDERER);
			}
		}
		com.extremedimensions.compat.DiscordWebhook.starvedOne(there);
	}

	/**
	 * Each tick of its death (3.48, as an Elder's): the view of everyone at the Heart held on it, it convulses and sags,
	 * its chains to the pillars snapping one after another (each node going dark as it does), its embers going out, the
	 * arena flying apart round it, until it collapses into its own light.
	 */
	public static void dying(StarvedOne so, ServerLevel seam, int t) {
		for (int i = 0; i < Binding.PILLARS; i++) {
			if (DYING[i] != null && t == HeartScenes.snapAt(i)) {
				Binding.darken(seam, i);
				HeartScenes.snap(seam, so, DYING[i]);
				DYING[i] = null;
			}
		}
		HeartScenes.death(seam, so, t, StarvedOne.DEATH_TICKS, DYING);
	}

	/** It is gone: the pillars stand broken as before, the floor it tore closes, and the Thread and the Shears are laid. */
	public static void died(StarvedOne so, ServerLevel seam) {
		Binding.end(seam);
		HeartScenes.close(seam);
		java.util.Arrays.fill(DYING, null);
		offerEnding(seam);
		String dim = SeamService.SEAM.identifier().toString();
		WorldFx.particleIn(dim, "extremedimensions:soul_mote", HX + 0.5, HY + 2, HZ + 0.5, 1, 1, 1, 0, 60);
		WorldFx.soundIn(dim, "minecraft:block.amethyst_block.resonate", "block", HX + 0.5, HY + 1.5, HZ + 0.5, 3f, 0.6f);
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
		if (drink >= 0 && so.level() instanceof ServerLevel seam) {
			HeartScenes.drinkBroken(seam, so);
		}
		boss = null;
		bar = null;
		reset();
		// Its arena comes apart with it
		HeartArena.release(so);
		if (so.level() instanceof ServerLevel seam) {
			end(seam);
		}
	}

	// ------------------------------------------------------------------ for tests and pictures

	/** It rises now for this player, as if its wind-up were over (for tests and pictures). */
	public static void wakeNow(ServerLevel seam, ServerPlayer p) {
		if (boss == null) {
			start(seam, p);
		}
	}

	/** The Starved One the Heart drives now, or null (for tests). */
	public static StarvedOne driven(ServerLevel seam) {
		return boss != null && seam.getEntity(boss) instanceof StarvedOne so ? so : null;
	}

	/** How far its fight has changed (for tests): 0, 1 or 2. */
	public static int stage() {
		return stage;
	}

	/** Whether it is still rising out of the Heart (for tests). */
	public static boolean risingNow() {
		return boss != null && rise >= 0;
	}

	/** Whether it is drinking time now (for tests). */
	public static boolean drinkingNow() {
		return drink >= 0;
	}

	/** The Heart is built now, where it stands (for pictures; it is otherwise built as someone comes near it). */
	public static void buildHeartNow(ServerLevel seam) {
		buildHeart(seam);
	}

	/** Operator command: break the seal (as if the Dragon died) for testing. */
	public static void breakSeal(MinecraftServer s) {
		com.extremedimensions.world.WorldService.onDragonDeath(s);
	}
}
