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
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

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
 * The Ferryman: on a long Nether expedition he may come to collect. While he walks the Tether drains faster (faster
 * still once he is wounded below half), each of his blows takes levels, and he pulls his passenger back to him. Slain,
 * he leaves fragments and perhaps a relic, and waives a stack of the Ferryman's Fee; outrun home, he collects his fare.
 */
public final class FerrymanService {
	private FerrymanService() {}

	private static final class Fare {
		UUID target;
		ServerBossEvent bar;
		ServerLevel level;
		int ticks;
		int nextTrick = 160;
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

	/** The Tether's drain for this player while the Ferryman walks (1 without him). */
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

	/** He comes for this player (also the operator command); false if he is already after them or cannot stand. */
	public static boolean summon(ServerPlayer p) {
		if (hunting(p)) {
			return false;
		}
		ServerLevel level = (ServerLevel) p.level();
		Ferryman f = ModEntities.FERRYMAN.spawn(level, ElderFx.near(level, p, 12, 18), EntitySpawnReason.EVENT);
		if (f == null) {
			return false;
		}
		f.setPersistenceRequired();
		f.setTarget(p);
		Fare fare = new Fare();
		fare.target = p.getUUID();
		fare.level = level;
		fare.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("boss.extremedimensions.ferryman"), BossEvent.BossBarColor.YELLOW,
				BossEvent.BossBarOverlay.NOTCHED_10);
		fare.bar.addPlayer(p);
		FARES.put(f.getUUID(), fare);
		ElderFx.roar(f, 3f, 0.8f);
		WorldFx.soundIn(ElderFx.dim(f), "minecraft:block.bell.use", "hostile", f.getX(), f.getY() + 3, f.getZ(), 3f, 0.5f);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.ferryman_comes"));
		Journal.unlock(p, DataStore.get(p.getUUID()), JournalEntry.FERRYMAN);
		return true;
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
			if (p == null || !p.isAlive() || p.level() != f.level() || f.distanceToSqr(p) > 64 * 64) {
				// Gone home with him still walking: he collects his fare on the way out
				if (p != null && p.isAlive() && p.level() != f.level() && !DataStore.get(p.getUUID()).inRun) {
					later.add(() -> collect(p));
				}
				later.add(() -> fade(f));
				continue;
			}
			if (fare.ticks % 20 == 0) {
				f.setTarget(p);
			}
			fare.bar.setProgress(f.getHealth() / f.getMaxHealth());
			double d2 = f.distanceToSqr(p);
			f.setLurkerPose(d2 < 4.5 * 4.5 ? Lurker.POSE_TAKE : f.getDeltaMovement().horizontalDistanceSqr() > 0.002 ? Lurker.POSE_RUN : Lurker.POSE_WATCH);
			if (fare.ticks % 4 == 0) {
				WorldFx.particleIn(ElderFx.dim(f), "minecraft:soul_fire_flame", f.getX(), f.getY() + 3.2, f.getZ(), 0.3, 0.3, 0.3, 0.0, 1);
			}
			if (!fare.doubled && f.getHealth() <= f.getMaxHealth() / 2) {
				fare.doubled = true;
				ElderFx.roar(f, 3f, 0.7f);
				p.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.ferryman_doubles"), true);
			}
			if (--fare.nextTrick <= 0) {
				ThreadLocalRandom rnd = ThreadLocalRandom.current();
				fare.nextTrick = 160 + rnd.nextInt(80);
				if (d2 > 6 * 6) {
					// The river's pull: back to the boat
					ElderFx.pull(f, p, 6);
					WorldFx.effect(p, MobEffects.SLOWNESS, 2, 1);
					WorldFx.soundIn(ElderFx.dim(f), "minecraft:entity.fishing_bobber.retrieve", "hostile", p.getX(), p.getY(), p.getZ(), 2f, 0.5f);
				} else {
					// The knell: the toll is due
					WorldFx.soundIn(ElderFx.dim(f), "minecraft:block.bell.use", "hostile", f.getX(), f.getY() + 3, f.getZ(), 3f, 0.6f);
					WorldFx.effect(p, MobEffects.WEAKNESS, 4, 1);
				}
			}
		}
		later.forEach(Runnable::run);
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
		if (fare == null) {
			return;
		}
		fare.bar.removeAllPlayers();
		ServerLevel level = (ServerLevel) f.level();
		f.spawnAtLocation(level, new ItemStack(ModItems.TETHER_FRAGMENT, 3));
		if (ThreadLocalRandom.current().nextDouble() < Config.FERRYMAN_RELIC_CHANCE) {
			f.spawnAtLocation(level, RelicService.random());
		}
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(f) >= 48 * 48) {
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
		WorldFx.particleIn(ElderFx.dim(f), "extremedimensions:lurker_wisp", f.getX(), f.getY() + 2, f.getZ(), 0.6, 1.4, 0.6, 0.0, 30);
		f.discard();
	}
}
