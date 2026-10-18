package com.extremedimensions.elder;

import java.util.List;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.config.Config;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/**
 * How hard an Elder fights, and how it grows with those who come against it. Its health and the damage of its blows
 * are scaled by the settings, and grow for every player fighting it beyond the first; so does its pace (the pause
 * between its blows shrinks) and the number of players its great blows fall on at once. Each Elder's great blow goes
 * through armour, as the Warden's sonic boom does.
 */
public final class ElderPower {
	private ElderPower() {}

	/** The damage type of the blows that go through armour. */
	public static final ResourceKey<DamageType> PIERCE = ResourceKey.create(Registries.DAMAGE_TYPE, ExtremeDimensions.id("elder_pierce"));

	/** Everyone fighting an Elder: players near it who can be hurt. */
	static List<ServerPlayer> fighters(ElderBoss b) {
		return ElderFx.targets(b, Math.max(8, Config.ELDER_FIGHTERS_RANGE));
	}

	/**
	 * Once a second: counts who fights it, and its health (as how much of every blow it shrugs off), damage and pace
	 * follow. Its health bar keeps its share whatever the count.
	 */
	static void tick(ElderBoss b, ElderFight f) {
		if (f.ticks % 20 != 0 && f.fighters > 0) {
			return;
		}
		f.fighters = Math.max(1, fighters(b).size());
		b.damageFactor = (float) damage(f.fighters);
		b.toughness = (float) health(f.fighters);
	}

	/** Its health, as a multiple of its own, with {@code n} players fighting it. */
	public static double health(int n) {
		return Math.max(0.1, Config.ELDER_HEALTH_SCALE) * (1 + Math.max(0, Config.ELDER_HEALTH_PER_PLAYER) * Math.max(0, n - 1));
	}

	/** The damage of its blows, as a multiple, with {@code n} players fighting it. */
	public static double damage(int n) {
		return Math.max(0, Config.ELDER_DAMAGE_SCALE) * (1 + Math.max(0, Config.ELDER_DAMAGE_PER_PLAYER) * Math.max(0, n - 1));
	}

	/** How much faster its pause between blows runs out, with {@code n} players fighting it. */
	public static double pace(int n) {
		double rest = Math.max(Math.max(0.1, Config.ELDER_REST_MIN), 1 - Math.max(0, Config.ELDER_REST_PER_PLAYER) * Math.max(0, n - 1));
		return 1 / rest;
	}

	/** How many players its great blows fall on at once, with {@code n} players fighting it. */
	public static int splits(int n) {
		return Math.max(1, Math.min(Math.max(1, Config.ELDER_SPLIT_MAX), n));
	}

	/** Extra brood it calls, with {@code n} players fighting it. */
	static int brood(int n) {
		return Math.max(0, Config.ELDER_BROOD_PER_PLAYER) * Math.max(0, n - 1);
	}

	/** How far its ranged blows reach: {@code r} blocks at its own size, further in the larger arenas. */
	static double reach(double r) {
		return r * Math.max(1, Config.ELDER_REACH);
	}

	/** A hit taken from an Elder, scaled by how hard it fights (see the player damage hook). */
	public static float scale(DamageSource source, float amount) {
		return source.getEntity() instanceof ElderBoss b ? amount * b.damageFactor : amount;
	}

	/** The source of a great blow: through armour when the setting allows, an ordinary blow otherwise. */
	static DamageSource great(ServerLevel level, ElderBoss b) {
		if (Config.ELDER_PIERCE <= 0) {
			return level.damageSources().mobAttack(b);
		}
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(PIERCE), b);
	}
}
