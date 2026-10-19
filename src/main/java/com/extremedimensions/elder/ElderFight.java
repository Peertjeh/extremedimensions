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
	/** Who stood within the ultimate's reach as it began: none escapes its curse by leaving, logging out or dying. */
	final java.util.Set<UUID> doomed = new java.util.HashSet<>();
	/** Distance walked since the last heavy step. */
	double stride;
	final Beat beat = new Beat();
	/** Players fighting it (counted once a second; at least one). See {@link ElderPower}. */
	int fighters;
	/** A great blow coming again at the next player: how many more times, and at whom this time (null = its prey). */
	int repeats;
	UUID aim;
	/** Who a great blow has fallen on since it began, so the next one goes to someone else. */
	final java.util.Set<UUID> aimed = new java.util.HashSet<>();
	/** Its next blow repeats the one under way at the next player, rather than starting afresh. */
	private boolean chaining;
	/** Ticks it stands dazed (an island fell on it, a core of its arena broke). */
	int dazed;

	/** The fight of an Elder, whichever it is, or null if it is not fighting. */
	static ElderFight of(net.minecraft.world.entity.Entity e) {
		ElderFight f = MatriarchService.fight(e);
		if (f == null) {
			f = FerrymanService.fight(e);
		}
		return f != null ? f : WeaverService.fight(e);
	}

	/** Dazes an Elder for a while (it shrugs it off in its ultimate and as it forms). */
	static void daze(net.minecraft.world.entity.Entity e, int ticks) {
		ElderFight f = of(e);
		if (f != null) {
			f.dazed = Math.max(f.dazed, ticks);
		}
	}

	/** Dazed: it stands still, its blow cut short, stars about its head; false once it has its wits again. */
	boolean dazed(ElderBoss b) {
		if (dazed <= 0 || action == ElderBoss.ULTIMATE || action == ElderBoss.SPAWN) {
			return false;
		}
		dazed--;
		if (action != ElderBoss.IDLE) {
			action = ElderBoss.IDLE;
			beat.reset();
			b.setAction(ElderBoss.IDLE);
		}
		cooldown = Math.max(cooldown, 10);
		if (ticks % 4 == 0) {
			WorldFx.particleIn(level, "minecraft:crit", b.getX(), b.getY() + b.getBbHeight() * 0.9, b.getZ(), 1.5, 0.4, 1.5, 0.05, 6);
		}
		return true;
	}

	void makeBar(String key, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay, ServerPlayer first) {
		bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable(key), color, overlay);
		bar.setDarkenScreen(true);
		bar.addPlayer(first);
	}

	/**
	 * Every tick of its fight: its bar follows its health (filling while it forms), and everyone who comes near sees it;
	 * its health, damage and pace follow those fighting it; its spell lies on everyone in its reach, and it pulls down
	 * anyone gliding near it.
	 */
	void keepBar(ElderBoss b) {
		ElderPower.tick(b, this);
		bar.setProgress(action == ElderBoss.SPAWN ? (float) Math.min(1, beat.t / ElderSpawn.length()) : b.getHealth() / b.getMaxHealth());
		if (ticks % 20 == 0) {
			double near = ElderPower.reach(64), far = ElderPower.reach(96);
			for (ServerPlayer q : level.players()) {
				if (q.distanceToSqr(b) < near * near) {
					bar.addPlayer(q);
				}
			}
			// ... and lets go of those who left (another world, far off): the sky lifts for them
			for (ServerPlayer q : List.copyOf(bar.getPlayers())) {
				if (q.level() != level || q.distanceToSqr(b) > far * far) {
					bar.removePlayer(q);
				}
			}
		}
		if (action != ElderBoss.SPAWN) {
			ElderSpells.tick(b, this);
		}
	}

	/** Its new prey sees its bar at once. */
	void makeBarFor(ServerPlayer p) {
		bar.addPlayer(p);
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
		cooldown -= Math.max(0.1, Config.ELDER_ATTACK_SPEED) * (enraged ? 1.25 : 1.0) * ElderPower.pace(Math.max(1, fighters));
		return cooldown <= 0;
	}

	void start(ElderBoss b, int a) {
		if (!chaining) {
			repeats = ElderPower.splits(Math.max(1, fighters)) - 1;
			aim = null;
			aimed.clear();
		}
		chaining = false;
		action = a;
		beat.reset();
		b.setAction(a);
		// No name is called: the Elder gives the blow away by its voice, the sound of the blow, its body and its light
		ElderTells.begin(b, a);
		// The client plays the ultimate on a 200-tick timeline and the forming on a 140-tick one, whatever their length
		b.setTempo(a == ElderBoss.ULTIMATE ? 200.0F / ElderUltimate.length() : a == ElderBoss.SPAWN ? 140.0F / ElderSpawn.length() : (float) tempo());
	}

	/** Advances the action's clock (the ultimate and the forming always take their full time). */
	void step() {
		beat.step(action == ElderBoss.ULTIMATE || action == ElderBoss.SPAWN ? 1.0 : tempo());
	}

	/** Who a great blow falls on this time: the player it came again for, or its prey. */
	ServerPlayer aimed(ServerPlayer prey) {
		if (aim != null && level.getPlayerByUUID(aim) instanceof ServerPlayer q && q.isAlive() && !q.isSpectator() && !q.isCreative()) {
			return q;
		}
		return prey;
	}

	/**
	 * At the end of a great blow: when more players fight it, the blow comes again at the nearest one it has not yet
	 * fallen on (true), or the blow is over (false).
	 */
	boolean again(ElderBoss b, ServerPlayer prey) {
		aimed.add(aimed(prey).getUUID());
		if (repeats <= 0) {
			return false;
		}
		ServerPlayer next = null;
		double best = Double.MAX_VALUE;
		for (ServerPlayer q : ElderFx.targets(b, ElderPower.reach(48))) {
			double d = q.distanceToSqr(b);
			if (!aimed.contains(q.getUUID()) && d < best) {
				best = d;
				next = q;
			}
		}
		if (next == null) {
			return false;
		}
		repeats--;
		aim = next.getUUID();
		chaining = true;
		start(b, action);
		return true;
	}

	/** The players a great blow falls on at once: its prey first, then the nearest others, as many as it splits over. */
	List<ServerPlayer> split(ElderBoss b, ServerPlayer prey, double radius) {
		java.util.List<ServerPlayer> out = new java.util.ArrayList<>();
		out.add(prey);
		java.util.List<ServerPlayer> rest = ElderFx.targets(b, radius);
		rest.remove(prey);
		rest.sort(java.util.Comparator.comparingDouble(q -> q.distanceToSqr(b)));
		for (ServerPlayer q : rest) {
			if (out.size() >= ElderPower.splits(Math.max(1, fighters))) {
				break;
			}
			out.add(q);
		}
		return out;
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

	/** The ultimate begins: black bars for everyone in reach, the world fog closing in. */
	void beginUltimate(ElderBoss b, String nameKey) {
		ultimateSpent = true;
		doomed.clear();
		double r = Math.max(0, Config.ELDER_ULTIMATE_RADIUS);
		for (ServerPlayer q : level.players()) {
			if (!q.isSpectator() && !q.isCreative() && q.isAlive() && q.distanceToSqr(b) <= r * r) {
				doomed.add(q.getUUID());
			}
		}
		start(b, ElderBoss.ULTIMATE);
		b.motion.halt();
		bar.setCreateWorldFog(true);
		ElderFx.cinema(b, Math.max(64, Config.ELDER_ULTIMATE_RADIUS + 16), ModPackets.BossFxPayload.ULTIMATE, ElderUltimate.length(), 1, nameKey);
		for (ServerPlayer q : level.players()) {
			if (q.distanceToSqr(b) < Math.pow(Math.max(64, Config.ELDER_ULTIMATE_RADIUS), 2)) {
				// Unnamed: what it is, is read from the dark, the fog, its music and the Elder swelling
				q.sendSystemMessage(Component.translatable("gui.extremedimensions.elder.ultimate_warn", Math.max(0, Config.ELDER_ULTIMATE_RADIUS)), true);
			}
		}
	}

	/** The ultimate breaks: its curse, a flash, and the bars lift. */
	void breakUltimate(ElderBoss b, String bossKey) {
		ElderFx.cinema(b, Math.max(64, Config.ELDER_ULTIMATE_RADIUS + 16), ModPackets.BossFxPayload.FLASH, 0, 1, "");
		java.util.Set<UUID> struck = ElderUltimate.curseAll(b, bossKey);
		// Those it held who slipped away (left, logged out, died) since it began are cursed all the same
		for (UUID id : doomed) {
			if (!struck.contains(id)) {
				ElderUltimate.owe(id, bossKey);
			}
		}
		doomed.clear();
		ElderFx.shake(b, b.getX(), b.getZ(), Math.max(64, Config.ELDER_ULTIMATE_RADIUS), 1.6f, 40);
	}

	/** The ultimate is cut short (the Elder leaves): those it held take its curse all the same. */
	void abandonUltimate(String bossKey) {
		if (action == ElderBoss.ULTIMATE) {
			for (UUID id : doomed) {
				ElderUltimate.owe(id, bossKey);
			}
		}
		doomed.clear();
	}

	/**
	 * Someone else to fight when its prey is gone (left, dead, too far): the nearest player near it who can be fought,
	 * or null.
	 */
	ServerPlayer successor(ElderBoss b, double radius) {
		ServerPlayer best = null;
		double bestD = radius * radius;
		for (ServerPlayer q : level.players()) {
			if (q.getUUID().equals(target) || q.isSpectator() || q.isCreative() || !q.isAlive()) {
				continue;
			}
			double d = q.distanceToSqr(b);
			if (d < bestD) {
				bestD = d;
				best = q;
			}
		}
		return best;
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
