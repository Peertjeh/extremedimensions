package com.extremedimensions.elder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.network.ModPackets;
import com.extremedimensions.server.WorldFx;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * What every Elder's fight keeps track of: its prey, its health bar (which darkens the sky for everyone under it), the
 * action under way and its clock, the pause before the next blow, and whether its ultimate has been spent. The pace
 * comes from the settings: {@code elder_speed} for moving, {@code elder_attack_speed} for how soon the next blow comes,
 * {@code elder_windup_speed} for how quickly a blow lands once it starts; an enraged Elder is quicker still.
 */
abstract class ElderFight {
	UUID target;
	ServerBossEvent bar;
	ServerLevel level;
	int ticks, action, lastAction;
	double cooldown = 70;
	boolean enraged, ultimateSpent;
	/** Distance walked since the last heavy step. */
	double stride;
	final Beat beat = new Beat();

	void makeBar(String key, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay, ServerPlayer first) {
		bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable(key), color, overlay);
		bar.setDarkenScreen(true);
		bar.addPlayer(first);
	}

	/** Its bar follows its health (filling while it forms), and everyone who comes near sees it. */
	void keepBar(ElderBoss b) {
		bar.setProgress(action == ElderBoss.SPAWN ? (float) Math.min(1, beat.t / ElderSpawn.length()) : b.getHealth() / b.getMaxHealth());
		if (ticks % 20 == 0) {
			for (ServerPlayer q : level.players()) {
				if (q.distanceToSqr(b) < 64 * 64) {
					bar.addPlayer(q);
				}
			}
		}
	}

	/** How quickly a blow plays out, as a multiple of its own pace. */
	double tempo() {
		return Math.max(0.2, Config.ELDER_WINDUP_SPEED) * (enraged ? 1.15 : 1.0);
	}

	/** How fast it moves: a base pace in blocks a tick, times the setting. */
	static double speed(double base) {
		return base * Math.max(0, Config.ELDER_SPEED);
	}

	/** Counts the pause down; true when the next blow may come. */
	boolean ready() {
		cooldown -= Math.max(0.1, Config.ELDER_ATTACK_SPEED) * (enraged ? 1.25 : 1.0);
		return cooldown <= 0;
	}

	void start(ElderBoss b, int a) {
		action = a;
		beat.reset();
		b.setAction(a);
		// The client plays the ultimate on a 200-tick timeline and the forming on a 140-tick one, whatever their length
		b.setTempo(a == ElderBoss.ULTIMATE ? 200.0F / ElderUltimate.length() : a == ElderBoss.SPAWN ? 140.0F / ElderSpawn.length() : (float) tempo());
	}

	/** Advances the action's clock (the ultimate and the forming always take their full time). */
	void step() {
		beat.step(action == ElderBoss.ULTIMATE || action == ElderBoss.SPAWN ? 1.0 : tempo());
	}

	void end(ElderBoss b, double pause) {
		lastAction = action;
		action = ElderBoss.IDLE;
		b.setAction(ElderBoss.IDLE);
		cooldown = pause + ThreadLocalRandom.current().nextInt(25);
	}

	/** A warning ring's delay at the fight's pace (never so quick it cannot be read). */
	int delay(int ticks) {
		return Math.max(8, (int) Math.round(ticks / tempo()));
	}

	/**
	 * Picks the next blow by weight ({action, weight, action, weight, ...}), never the same blow twice in a row when
	 * another can be had.
	 */
	int pick(int... weighted) {
		int total = 0;
		for (int i = 0; i < weighted.length; i += 2) {
			total += weighted[i] == lastAction ? 0 : weighted[i + 1];
		}
		if (total <= 0) {
			return weighted[0];
		}
		int roll = ThreadLocalRandom.current().nextInt(total);
		for (int i = 0; i < weighted.length; i += 2) {
			int w = weighted[i] == lastAction ? 0 : weighted[i + 1];
			if (roll < w) {
				return weighted[i];
			}
			roll -= w;
		}
		return weighted[0];
	}

	/** The ultimate begins: black bars and its name for everyone in reach, the world fog closing in. */
	void beginUltimate(ElderBoss b, String nameKey) {
		ultimateSpent = true;
		start(b, ElderBoss.ULTIMATE);
		b.motion.halt();
		bar.setCreateWorldFog(true);
		ElderFx.cinema(b, Math.max(64, Config.ELDER_ULTIMATE_RADIUS + 16), ModPackets.BossFxPayload.ULTIMATE, ElderUltimate.length(), 1, nameKey);
		for (ServerPlayer q : level.players()) {
			if (q.distanceToSqr(b) < Math.pow(Math.max(64, Config.ELDER_ULTIMATE_RADIUS), 2)) {
				q.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.ultimate_warn", Component.translatable(nameKey),
						Math.max(0, Config.ELDER_ULTIMATE_RADIUS)), true);
			}
		}
	}

	/** The ultimate breaks: its curse, a flash, and the bars lift. */
	void breakUltimate(ElderBoss b, String bossKey) {
		ElderFx.cinema(b, Math.max(64, Config.ELDER_ULTIMATE_RADIUS + 16), ModPackets.BossFxPayload.FLASH, 0, 1, "");
		ElderUltimate.curse(b, bossKey);
		ElderFx.shake(b, b.getX(), b.getZ(), Math.max(64, Config.ELDER_ULTIMATE_RADIUS), 1.6f, 40);
	}

	void closeUltimate(ElderBoss b) {
		bar.setCreateWorldFog(false);
		ElderFx.cinema(b, Math.max(64, Config.ELDER_ULTIMATE_RADIUS + 16), ModPackets.BossFxPayload.ULTIMATE_END, 0, 0, "");
		end(b, 50);
	}

	/** A heavy step every few blocks it walks. */
	void walk(ElderBoss b, double every, float volume) {
		stride += b.motion.speed();
		if (stride >= every) {
			stride = 0;
			ElderFx.step(b, volume);
		}
	}

	/** Light gathering on everyone it marks (pillars of particles over their heads). */
	static void markPlayers(ElderBoss b, List<ServerPlayer> players, String particle) {
		for (ServerPlayer q : players) {
			for (int i = 0; i < 12; i++) {
				WorldFx.particleIn(b.level(), particle, q.getX(), q.getY() + 2 + i, q.getZ(), 0.15, 0.3, 0.15, 0, 1);
			}
		}
	}
}
