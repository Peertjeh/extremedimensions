package net.battlehub.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The mind in a small duel simulation on a line: the NPC fights a scripted opponent that walks in and swings every
 * twelve ticks when in reach. Movement, reach, the attack charge, knockback, aim (a hit can miss, more often with a
 * larger aim error) and strafing (a strafing target is hit less often) are simplified versions of the real ones;
 * the point is to see the decisions and techniques of the whole mind, not to model Minecraft exactly.
 */
class MindTest {
	static final class Duel {
		final java.util.Random luck;
		final Difficulty level;
		double me = 0, foe = 8;
		double myHp = 20, foeHp = 20;
		double ready = 1;
		int foeCooldown;
		final Map<Tactic, Integer> time = new EnumMap<>(Tactic.class);
		double dealt, taken;
		int swingsBeforeReaction = -1;

		Duel(Difficulty level, long seed) {
			this.level = level;
			this.luck = new java.util.Random(seed * 31 + 7);
		}

		void run(CombatMind mind, int ticks) {
			for (int tick = 0; tick < ticks && myHp > 0 && foeHp > 0; tick++) {
				V3 mePos = new V3(me, 64, 0), foePos = new V3(foe, 64, 0);
				double dist = Math.abs(foe - me);
				mind.see(tick, Fixtures.FOE, foePos, dist, false);
				Situation s = new Situation(tick, new Situation.Self(mePos, V3.ZERO, myHp, 20, true, ready, false, false, 3.0),
						new Situation.Target(Fixtures.FOE, foePos, V3.ZERO, foeHp, 20, true, 1, foeCooldown > 6 ? 12 - foeCooldown : 100, true),
						null, Fixtures.open(), Situation.Threat.NONE);
				Intent in = mind.tick(s);
				time.merge(mind.tactic(), 1, Integer::sum);
				double dir = Math.signum(foe - me);
				if (in.moveTo() != null) {
					me += Math.signum(in.moveTo().x() - me) * Math.min(0.28, Math.abs(in.moveTo().x() - me));
				} else {
					me += dir * in.forward() * (in.sprint() ? 0.28 : 0.21);
				}
				ready = Math.min(1, ready + 0.08);
				boolean strafing = Math.abs(in.strafe()) > 0.3;
				if (in.attack()) {
					if (swingsBeforeReaction < 0) swingsBeforeReaction = tick;
					// the aim wobble of the level decides whether the swing connects
					boolean lands = luck.nextDouble() < 1 - level.aimError / 25;
					if (Math.abs(foe - me) <= 3.0 && lands) {
						double dmg = 4 * ready * ready;
						foeHp -= dmg;
						dealt += dmg;
						mind.dealt(dmg);
						foe += dir * 0.8;
					}
					ready = 0;
				}
				// the opponent: walks in, swings when it can
				double d = Math.abs(foe - me);
				if (d > 2.6) foe -= dir * 0.2;
				if (foeCooldown > 0) foeCooldown--;
				if (d <= 3.0 && foeCooldown == 0) {
					foeCooldown = 12;
					// a strafing NPC is harder to hit
					if (luck.nextDouble() < (strafing ? 0.55 : 0.9)) {
						myHp -= 3;
						taken += 3;
						mind.taken(3);
						me -= dir * 0.8;
					}
				}
			}
		}

		double share(Tactic... ts) {
			int all = time.values().stream().mapToInt(Integer::intValue).sum();
			int n = 0;
			for (Tactic t : ts) n += time.getOrDefault(t, 0);
			return all == 0 ? 0 : n / (double) all;
		}
	}

	private static Duel duel(Archetype a, Difficulty d, long seed) {
		Duel duel = new Duel(d, seed);
		duel.run(new CombatMind(a, a.base, d, seed), 600);
		return duel;
	}

	@Test
	void archetypesReallyFightDifferently() {
		double rushPress = 0, counterPress = 0, rushSpace = 0, counterSpace = 0;
		for (long seed = 0; seed < 20; seed++) {
			Duel r = duel(Archetype.RUSHER, Difficulty.EXPERT, seed);
			Duel c = duel(Archetype.COUNTER, Difficulty.EXPERT, seed);
			rushPress += r.share(Tactic.STRIKE, Tactic.APPROACH);
			counterPress += c.share(Tactic.STRIKE, Tactic.APPROACH);
			rushSpace += r.share(Tactic.SPACE);
			counterSpace += c.share(Tactic.SPACE);
		}
		assertTrue(rushPress > counterPress + 2, "rusher presses " + rushPress / 20 + ", counter " + counterPress / 20);
		assertTrue(counterSpace > rushSpace + 2, "counter spaces " + counterSpace / 20 + ", rusher " + rushSpace / 20);
	}

	@Test
	void itReactsAfterItsReactionTimeNotBefore() {
		Duel fast = duel(Archetype.RUSHER, Difficulty.MASTER, 1);
		Duel slow = duel(Archetype.RUSHER, Difficulty.BEGINNER, 1);
		assertTrue(fast.swingsBeforeReaction >= Difficulty.MASTER.reactionTicks);
		assertTrue(slow.swingsBeforeReaction >= Difficulty.BEGINNER.reactionTicks);
	}

	@Test
	void higherLevelsTradeBetter() {
		double expert = 0, beginner = 0;
		for (long seed = 0; seed < 20; seed++) {
			Duel e = duel(Archetype.BALANCED, Difficulty.EXPERT, seed);
			Duel b = duel(Archetype.BALANCED, Difficulty.BEGINNER, seed);
			expert += e.dealt - e.taken;
			beginner += b.dealt - b.taken;
		}
		assertTrue(expert > beginner, "expert " + expert / 20 + " vs beginner " + beginner / 20);
	}

	@Test
	void theSameSeedPlaysTheSameFight() {
		Duel a = duel(Archetype.IMPROVISER, Difficulty.ADVANCED, 9);
		Duel b = duel(Archetype.IMPROVISER, Difficulty.ADVANCED, 9);
		assertEquals(a.time, b.time);
		assertEquals(a.dealt, b.dealt, 1e-9);
	}

	@Test
	void itExplainsItselfAndStaysCheap() {
		CombatMind mind = new CombatMind(Archetype.DUELIST, Archetype.DUELIST.base, Difficulty.MASTER, 4);
		Duel d = new Duel(Difficulty.MASTER, 4);
		long t0 = System.nanoTime();
		d.run(mind, 600);
		long ms = (System.nanoTime() - t0) / 1_000_000;
		CombatMind.Report rep = mind.report();
		assertFalse(rep.top().isEmpty());
		assertFalse(rep.transitions().isEmpty());
		assertFalse(rep.lines().isEmpty());
		assertTrue(rep.lines().get(0).startsWith("duelist, master"), rep.lines().get(0));
		assertTrue(rep.thinks() > 20, rep.thinks() + " thinks");
		assertTrue(ms < 2000, ms + " ms for 600 ticks");
	}

	@Test
	void warmThinkingCostsMicroseconds() {
		// warm up the JIT, then measure: a server tick is 50 ms, so even 20 NPCs thinking at once must stay far below
		for (long seed = 0; seed < 60; seed++) duel(Archetype.values()[(int) (seed % 8)], Difficulty.MASTER, seed);
		long thinks = 0;
		double micros = 0;
		for (long seed = 100; seed < 160; seed++) {
			CombatMind mind = new CombatMind(Archetype.values()[(int) (seed % 8)], Archetype.BALANCED.base, Difficulty.MASTER, seed);
			new Duel(Difficulty.MASTER, seed).run(mind, 600);
			thinks += mind.report().thinks();
			micros += mind.report().avgMicros() * mind.report().thinks();
		}
		double avg = micros / thinks;
		assertTrue(avg < 100, String.format(java.util.Locale.ROOT, "%.1f us per think over %d thinks", avg, thinks));
	}

	@Test
	void aResetForgetsTheFightButNotThePersonality() {
		CombatMind mind = new CombatMind(Archetype.RUSHER, Archetype.RUSHER.base, Difficulty.EXPERT, 2);
		new Duel(Difficulty.EXPERT, 2).run(mind, 200);
		mind.reset(500);
		assertEquals(Tactic.IDLE, mind.tactic());
		assertTrue(mind.track(Fixtures.FOE).empty());
		assertEquals(Archetype.RUSHER.base, mind.personality());
	}

	@Test
	void anotherStyleMidFightKeepsWhatTheMindSawAndHowTheFightWent() {
		CombatMind old = new CombatMind(Archetype.COUNTER, Archetype.COUNTER.base, Difficulty.EXPERT, 3);
		new Duel(Difficulty.EXPERT, 3).run(old, 300);
		old.dealt(4);
		old.taken(2);
		CombatMind rusher = new CombatMind(Archetype.RUSHER, Archetype.RUSHER.base, Difficulty.EXPERT, 4);
		rusher.inherit(old);
		assertEquals(old.totalDealt(), rusher.totalDealt(), 1e-9);
		assertEquals(old.totalTaken(), rusher.totalTaken(), 1e-9);
		assertFalse(rusher.track(Fixtures.FOE).empty());
		assertEquals(old.model(Fixtures.FOE).samples(), rusher.model(Fixtures.FOE).samples());
		assertEquals(Archetype.RUSHER, rusher.archetype());
		assertFalse(rusher.debrief("Bot_Test", Fixtures.FOE).isEmpty());
	}
}
