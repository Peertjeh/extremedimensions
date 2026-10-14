package com.extremedimensions.elder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;

/**
 * The Ferryman: on a long Nether expedition he may come to collect. His boat sails over lava and stone alike,
 * closing in and then circling his passenger. While he sails the Tether drains faster (faster still once he is
 * wounded below half), each of his blows takes levels, he brings his pole down in a line of fire, drags his
 * passenger back with a chain, sends soul lanterns drifting after them and rings a knell. Slain, he leaves fragments
 * and perhaps a relic and waives a stack of the Ferryman's Fee; outrun home, he collects his fare.
 */
public final class FerrymanService {
	private FerrymanService() {}

	private static final class Fare {
		UUID target;
		ServerBossEvent bar;
		ServerLevel level;
		int ticks, nextMove = 80, action, actionAt;
		int circle = 1;
		boolean doubled;
	}

	private static final Map<UUID, Fare> FARES = new HashMap<>();

	public static boolean isDriven(Entity e) {
		return FARES.containsKey(e.getUUID());
	}

	public static void clear() {
		for (Fare f : FARES.values()) {
			f.bar.removeAllPlayers();
		}
		FARES.clear();
	}

	public static boolean unlocked() {
		return switch (Config.FERRYMAN_UNLOCK) {
			case 0 -> true;
			case 1 -> DataStore.sealBroken();
			default -> !DataStore.ending().isEmpty();
		};
	}

	/** Whether the Ferryman is after this player. */
	public static boolean hunting(ServerPlayer p) {
		for (Fare f : FARES.values()) {
			if (p.getUUID().equals(f.target)) {
				return true;
			}
		}
		return false;
	}

	/** The Tether's drain for this player while the Ferryman sails (1 without him). */
	public static double drainFactor(ServerPlayer p) {
		for (Fare f : FARES.values()) {
			if (p.getUUID().equals(f.target)) {
				return Math.max(0.1, Config.FERRYMAN_DRAIN) * (f.doubled ? 1.5 : 1.0);
			}
		}
		return 1.0;
	}

	public static void tick(MinecraftServer s) {
		drive(s);
		if (s.getTickCount() % 20 != 0 || !unlocked()) {
			return;
		}
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			PlayerData d = DataStore.get(p.getUUID());
			if (!d.inRun || d.ferrymanRolled || d.runActive < Config.FERRYMAN_AFTER_SECONDS || p.isCreative() || p.isSpectator()
					|| !Dims.NETHER.equals(Dims.of(p))) {
				continue;
			}
			// One chance per expedition; the Fee he is owed makes him likelier
			d.ferrymanRolled = true;
			double chance = Math.min(1, Math.max(0, Config.FERRYMAN_CHANCE) * (1 + d.owned(Penalty.FERRYMANS_FEE)));
			if (ThreadLocalRandom.current().nextDouble() < chance) {
				summon(p);
			}
		}
	}

	/** He comes for this player (also the operator command); false if he is already after them or cannot sail in. */
	public static boolean summon(ServerPlayer p) {
		return !hunting(p) && summonAt(p, ElderFx.near((ServerLevel) p.level(), p, 20, 28)) != null;
	}

	/** He comes for this player, his boat at this spot; the Ferryman, or null if he is already after them. */
	public static Ferryman summonAt(ServerPlayer p, BlockPos spot) {
		if (hunting(p)) {
			return null;
		}
		ServerLevel level = (ServerLevel) p.level();
		Ferryman f = ModEntities.FERRYMAN.spawn(level, spot, EntitySpawnReason.EVENT);
		if (f == null) {
			return null;
		}
		f.setPersistenceRequired();
		f.setTarget(p);
		f.setYRot(ElderFx.yawTo(f, p.getX(), p.getZ()));
		Fare fare = new Fare();
		fare.target = p.getUUID();
		fare.level = level;
		fare.circle = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
		fare.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("boss.extremedimensions.ferryman"), BossEvent.BossBarColor.YELLOW,
				BossEvent.BossBarOverlay.NOTCHED_10);
		fare.bar.addPlayer(p);
		FARES.put(f.getUUID(), fare);
		ElderFx.roar(f, 4f, 0.7f);
		WorldFx.soundIn(ElderFx.dim(f), "minecraft:block.bell.use", "hostile", f.getX(), f.getY() + 8, f.getZ(), 5f, 0.45f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.ferryman_comes"));
		Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.FERRYMAN);
		ElderFx.music(f, ElderFx.MUSIC_FERRYMAN);
		return f;
	}

	private static void drive(MinecraftServer s) {
		if (FARES.isEmpty()) {
			return;
		}
		List<Runnable> later = new ArrayList<>();
		Iterator<Map.Entry<UUID, Fare>> it = FARES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Fare> e = it.next();
			Fare fare = e.getValue();
			Entity ent = fare.level.getEntity(e.getKey());
			if (!(ent instanceof Ferryman f) || !f.isAlive()) {
				fare.bar.removeAllPlayers();
				it.remove();
				continue;
			}
			fare.ticks++;
			ServerPlayer p = s.getPlayerList().getPlayer(fare.target);
			if (p == null || !p.isAlive() || p.level() != f.level() || f.distanceToSqr(p) > 80 * 80) {
				// Gone home with him still sailing: he collects his fare on the way out
				if (p != null && p.isAlive() && p.level() != f.level() && !DataStore.get(p.getUUID()).inRun) {
					later.add(() -> collect(p));
				}
				later.add(() -> fade(f));
				continue;
			}
			sail(f, fare, p);
		}
		later.forEach(Runnable::run);
	}

	private static void sail(Ferryman f, Fare fare, ServerPlayer p) {
		String dim = ElderFx.dim(f);
		if (fare.ticks % 20 == 0) {
			f.setTarget(p);
		}
		if (fare.ticks % 40 == 1) {
			ElderFx.music(f, ElderFx.MUSIC_FERRYMAN);
		}
		fare.bar.setProgress(f.getHealth() / f.getMaxHealth());
		if (fare.ticks % 3 == 0) {
			WorldFx.particleIn(dim, "minecraft:soul_fire_flame", f.getX(), f.getY() + 0.3, f.getZ(), 2.5, 0.1, 6, 0.01, 2);
		}
		if (!fare.doubled && f.getHealth() <= f.getMaxHealth() / 2) {
			fare.doubled = true;
			ElderFx.roar(f, 4f, 0.6f);
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.ferryman_doubles"), true);
		}
		double dx = p.getX() - f.getX(), dz = p.getZ() - f.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (fare.action == ElderBoss.IDLE) {
			// Closing in, then circling: the boat points where it sails, the hooded head turns to its passenger
			double tx, tz;
			double speed;
			if (dist > 15) {
				tx = p.getX();
				tz = p.getZ();
				speed = 0.26;
			} else {
				double a = Math.atan2(f.getZ() - p.getZ(), f.getX() - p.getX()) + fare.circle * 0.35;
				tx = p.getX() + Math.cos(a) * 12;
				tz = p.getZ() + Math.sin(a) * 12;
				speed = 0.17;
			}
			ElderFx.turn(f, ElderFx.yawTo(f, tx, tz), 3f);
			ElderFx.glide(f, tx, tz, speed, 0.1);
			ElderFx.look(f, p);
			if (--fare.nextMove <= 0) {
				int roll = ThreadLocalRandom.current().nextInt(6);
				fare.action = dist <= 7 ? Ferryman.KNELL : dist <= 14 && roll < 3 ? Ferryman.SLAM : roll < 4 ? Ferryman.LANTERN : Ferryman.CHAIN;
				fare.actionAt = fare.ticks;
				f.setAction(fare.action);
			}
			return;
		}
		// Mid-blow the boat drifts to a stop, turned to its passenger
		ElderFx.turn(f, ElderFx.yawTo(f, p.getX(), p.getZ()), 4f);
		ElderFx.glide(f, f.getX(), f.getZ(), 0, 0.1);
		ElderFx.look(f, p);
		int t = fare.ticks - fare.actionAt;
		switch (fare.action) {
			case Ferryman.SLAM -> {
				if (t == 24) {
					slam(f, p);
				}
				if (t >= 42) {
					end(f, fare);
				}
			}
			case Ferryman.CHAIN -> {
				if (t == 10) {
					ElderFx.line(dim, "minecraft:soul_fire_flame", f.getX(), f.getY() + 9, f.getZ(), p.getX(), p.getY() + 1, p.getZ(), 24);
					WorldFx.soundIn(dim, "minecraft:block.chain.break", "hostile", p.getX(), p.getY(), p.getZ(), 3f, 0.5f);
					ElderFx.pull(f, p, 7);
					WorldFx.effect(p, MobEffects.SLOWNESS, 2, 1);
				}
				if (t >= 24) {
					end(f, fare);
				}
			}
			case Ferryman.LANTERN -> {
				if (t == 16) {
					double yaw = Math.toRadians(f.getYRot());
					int n = 0;
					for (ServerPlayer q : fare.level.players()) {
						if (!q.isSpectator() && q.distanceToSqr(f) < 48 * 48 && n < 4) {
							for (int i = 0; i < (q == p ? 3 : 1); i++) {
								double side = (i - 1) * 3.0;
								ElderStrikes.wisp(f, f.getX() - Math.cos(yaw) * side, f.getY() + 15, f.getZ() - Math.sin(yaw) * side, q, 6f);
							}
							n++;
						}
					}
					WorldFx.soundIn(dim, "minecraft:entity.vex.charge", "hostile", f.getX(), f.getY() + 14, f.getZ(), 3f, 0.5f);
				}
				if (t >= 34) {
					end(f, fare);
				}
			}
			default -> {
				if (t == 8) {
					WorldFx.soundIn(dim, "minecraft:block.bell.use", "hostile", f.getX(), f.getY() + 8, f.getZ(), 5f, 0.55f);
					for (ServerPlayer q : fare.level.players()) {
						if (q.distanceToSqr(f) < 14 * 14) {
							WorldFx.effect(q, MobEffects.WEAKNESS, 4, 1);
							ElderFx.shove(f, q, 1.2, 0.35);
						}
					}
				}
				if (t >= 22) {
					end(f, fare);
				}
			}
		}
	}

	private static void end(Ferryman f, Fare fare) {
		fare.action = ElderBoss.IDLE;
		f.setAction(ElderBoss.IDLE);
		fare.nextMove = (fare.doubled ? 45 : 70) + ThreadLocalRandom.current().nextInt(40);
		if (ThreadLocalRandom.current().nextInt(3) == 0) {
			fare.circle = -fare.circle;
		}
	}

	/** The pole comes down: a line of fire runs out from the boat towards its passenger. */
	private static void slam(Ferryman f, ServerPlayer p) {
		double dx = p.getX() - f.getX(), dz = p.getZ() - f.getZ();
		double d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
		ServerLevel level = (ServerLevel) f.level();
		for (int i = 0; i < 6; i++) {
			double k = 3 + i * 3;
			double x = f.getX() + dx / d * k, z = f.getZ() + dz / d * k;
			double y = ElderFx.surface(level, x, p.getY() + 4, z, 4, 12);
			ElderStrikes.strike(f, x, Double.isNaN(y) ? p.getY() : y, z, 2.6, 10f, 6 + i * 3, ElderStrikes.SLAM);
		}
		WorldFx.soundIn(ElderFx.dim(f), "minecraft:entity.iron_golem.attack", "hostile", f.getX(), f.getY() + 4, f.getZ(), 4f, 0.4f);
	}

	/** One of his blows landed: it takes its toll in levels. */
	public static void toll(ServerPlayer p) {
		int levels = Math.min(p.experienceLevel, Math.max(0, Config.FERRYMAN_TOLL_LEVELS));
		if (levels <= 0) {
			return;
		}
		p.giveExperienceLevels(-levels);
		WorldFx.particles(p, "minecraft:wax_off", 1.0, 0.5, 0.05, 10);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.ferryman_toll", levels), true);
	}

	private static void collect(ServerPlayer p) {
		int levels = Math.min(p.experienceLevel, Math.max(0, Config.FERRYMAN_ESCAPE_LEVELS));
		if (levels > 0) {
			p.giveExperienceLevels(-levels);
		}
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.ferryman_collects", levels));
		WorldFx.soundSelf(p, "minecraft:block.bell.use", 1f, 0.5f);
	}

	/** Slain: fragments, perhaps a relic, and the Fee he was owed waived for his passenger and his slayer. */
	public static void onDefeat(Ferryman f, Entity killer) {
		Fare fare = FARES.remove(f.getUUID());
		ElderStrikes.forget(f);
		ElderFx.silence(f, ElderFx.MUSIC_FERRYMAN);
		if (fare == null) {
			return;
		}
		fare.bar.removeAllPlayers();
		ServerLevel level = (ServerLevel) f.level();
		ElderFx.burst(f, "minecraft:soul_fire_flame", 200);
		ElderFx.burst(f, "minecraft:soul", 80);
		f.spawnAtLocation(level, new ItemStack(ModItems.TETHER_FRAGMENT, 3));
		if (ThreadLocalRandom.current().nextDouble() < Config.FERRYMAN_RELIC_CHANCE) {
			f.spawnAtLocation(level, RelicService.random());
		}
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(f) >= 64 * 64) {
				continue;
			}
			PlayerData d = DataStore.get(p.getUUID());
			Advancements.grant(p, Advancements.FERRYMAN_SLAIN);
			Journal.unlock(p, d, JournalEntry.FERRYMAN);
			if ((p.getUUID().equals(fare.target) || p == killer) && d.owned(Penalty.FERRYMANS_FEE) > 0) {
				PenaltyService.lift(p, d, Penalty.FERRYMANS_FEE, "gui.extremedimensions.elder.ferryman_waived");
			}
		}
		DataStore.markDirty();
	}

	/** Gone: he poles back into the dark, whole. */
	public static void fade(Ferryman f) {
		Fare fare = FARES.remove(f.getUUID());
		if (fare != null) {
			fare.bar.removeAllPlayers();
		}
		ElderStrikes.forget(f);
		ElderFx.silence(f, ElderFx.MUSIC_FERRYMAN);
		ElderFx.burst(f, "minecraft:soul", 100);
		f.discard();
	}
}
