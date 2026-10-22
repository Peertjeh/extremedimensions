package net.battlehub.bot;

import java.util.ArrayDeque;
import java.util.Random;
import java.util.UUID;

import net.battlehub.config.ConfigManager;
import net.battlehub.core.ai.Intent;
import net.battlehub.core.ai.MeleeController;
import net.battlehub.core.ai.Situation;
import net.battlehub.core.ai.Tactic;
import net.battlehub.core.ai.Track;
import net.battlehub.core.ml.CombatAction;
import net.battlehub.core.ml.CombatLevels;
import net.battlehub.core.ml.CombatObservation;
import net.battlehub.core.ml.CombatSpec;
import net.battlehub.core.ml.CombatState;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The learned melee model fighting for a bot (in the modes of {@code bots.learnedModes}). Every decision (2 ticks) it
 * builds the same observation as the simulation it was trained in (ml/pvp_sim), lets the model of the bot's level
 * choose, and holds that choice: movement and jump for the decision, the click on its first tick, the turn spread over
 * its ticks. The level adds what difficulties.json says: the opponent seen a few ticks late, aim noise, a turn limit,
 * and sampling instead of the best action. The scripted mind still decides when to fight; healing, edges, objectives
 * and the crosshair check of every click stay as they are.
 */
final class LearnedMelee implements MeleeController {
	private final BotPlayer bot;
	private final Random rng;
	private CombatAction current = CombatAction.NOTHING;
	private int step;
	/** What it perceived of the opponent each tick, newest first (for the level's delay). */
	private final ArrayDeque<double[]> seen = new ArrayDeque<>();
	private UUID seenOf;
	private int decisions;

	LearnedMelee(BotPlayer bot) {
		this.bot = bot;
		this.rng = new Random(bot.getUUID().getLeastSignificantBits() ^ System.nanoTime());
	}

	/** Decisions taken by the model (for the self-test and /bots inspect). */
	int decisions() {
		return decisions;
	}

	/** The modes v1 was trained for: a duel on flat ground. */
	static final java.util.Set<String> MODES = java.util.Set.of("sword", "boxing");

	static boolean enabledFor(Match m) {
		var cfg = ConfigManager.config().bots;
		return m != null && cfg.learnedCombat && MODES.contains(m.mode.id()) && cfg.learnedModes.contains(m.mode.id()) && !LearnedFight.active(m);
	}

	@Override
	public Intent act(Tactic tactic, Situation s, Track track) {
		Match m = Matches.of(bot.getUUID());
		// v1 fights without an objective and not with the bow
		if (s.goal() != null || tactic == Tactic.SHOOT) return null;
		if (!enabledFor(m) || !CombatModels.ready() || s.target() == null) return null;
		ServerPlayer opp = bot.level().getServer().getPlayerList().getPlayer(s.target().id());
		if (opp == null) return null;
		CombatLevels.Level lv = CombatModels.level(bot.difficulty);
		CombatModels.Model model = lv == null ? null : CombatModels.model(lv.model());
		if (model == null) return null;
		CombatSpec spec = CombatModels.spec();
		remember(s.target(), opp);
		if (step % spec.decisionTicks == 0) {
			decide(spec, model, lv);
			step = 0;
		}
		boolean first = step == 0;
		step++;
		turn(spec, lv);
		return new Intent(current.forward(), current.strafe(), current.sprint(), current.jump(), null, null, first && current.attack(), false, false);
	}

	private void remember(Situation.Target t, ServerPlayer opp) {
		if (!t.id().equals(seenOf)) {
			seen.clear();
			seenOf = t.id();
		}
		seen.addFirst(new double[] {t.pos().x(), t.pos().y(), t.pos().z(), t.vel().x(), t.vel().y(), t.vel().z(), opp.getYRot(),
				opp.onGround() ? 1 : 0, opp.isSprinting() ? 1 : 0, t.swung(), opp.hurtTime, t.health(), t.maxHealth(), t.visible() ? 1 : 0});
		while (seen.size() > 12) seen.removeLast();
	}

	private void decide(CombatSpec spec, CombatModels.Model model, CombatLevels.Level lv) {
		CombatState st = new CombatState();
		var v = bot.getDeltaMovement();
		st.selfX = bot.getX();
		st.selfY = bot.getY();
		st.selfZ = bot.getZ();
		st.selfVx = v.x;
		st.selfVy = v.y;
		st.selfVz = v.z;
		st.selfYaw = bot.getYRot();
		st.selfPitch = bot.getXRot();
		st.selfOnGround = bot.onGround();
		st.selfSprinting = bot.isSprinting();
		st.selfCharge = bot.getAttackStrengthScale(0.5f);
		st.selfHurtTicks = bot.hurtTime;
		st.selfHealth = bot.getHealth();
		st.selfMaxHealth = bot.getMaxHealth();
		// the opponent as the bot perceived it, a few ticks late on the lower levels
		double[] o = (double[]) seen.toArray()[Math.min(seen.size() - 1, Math.max(0, lv.extraDelay()))];
		st.oppX = o[0];
		st.oppY = o[1];
		st.oppZ = o[2];
		st.oppVx = o[3];
		st.oppVy = o[4];
		st.oppVz = o[5];
		st.oppYaw = o[6];
		st.oppOnGround = o[7] > 0.5;
		st.oppSprinting = o[8] > 0.5;
		st.oppSinceSwing = o[9];
		st.oppHurtTicks = o[10];
		st.oppHealth = o[11];
		st.oppMaxHealth = o[12];
		st.oppVisible = o[13] > 0.5;
		rays(st, spec);
		float[] logits = model.logits(CombatObservation.build(spec, st));
		int[] idx = lv.sample() ? CombatAction.sample(spec, logits, 1.0, rng) : CombatAction.best(spec, logits);
		current = CombatAction.decode(spec, idx);
		decisions++;
	}

	/** This tick's share of the decision's turn, within the level's turn limit, with its aim noise. */
	private void turn(CombatSpec spec, CombatLevels.Level lv) {
		double yaw = Math.max(-lv.turnCap(), Math.min(lv.turnCap(), current.yawLeft())) / spec.decisionTicks + rng.nextGaussian() * lv.aimNoise();
		double pitch = current.pitchDown() / spec.decisionTicks + rng.nextGaussian() * lv.aimNoise();
		// turning left lowers Minecraft's yaw
		float y = (float) (bot.getYRot() - yaw);
		bot.setYRot(y);
		bot.setYHeadRot(y);
		bot.yBodyRot = y;
		bot.setXRot((float) Math.max(-90, Math.min(90, bot.getXRot() + pitch)));
	}

	/**
	 * Ground before a drop and room before a wall, in the bot's own directions (0 ahead, then every 45 degrees to the
	 * left), up to the spec's ray length.
	 */
	private void rays(CombatState st, CombatSpec spec) {
		Level level = bot.level();
		double ry = Math.toRadians(bot.getYRot());
		double fx = -Math.sin(ry), fz = Math.cos(ry), lx = Math.cos(ry), lz = Math.sin(ry);
		double max = spec.rayMax, hw = spec.hitboxWidth / 2;
		for (int k = 0; k < 8; k++) {
			double a = Math.toRadians(k * 45.0);
			double dx = fx * Math.cos(a) + lx * Math.sin(a), dz = fz * Math.cos(a) + lz * Math.sin(a);
			double edge = max;
			for (double t = 0.25; t <= max; t += 0.25) {
				BlockPos below = BlockPos.containing(bot.getX() + dx * t, bot.getY() - 0.5, bot.getZ() + dz * t);
				if (!solid(level, below) && !solid(level, below.below())) {
					edge = t - 0.25;
					break;
				}
			}
			double wall = max;
			for (double t = 0; t <= max; t += 0.25) {
				double x = bot.getX() + dx * (t + hw), z = bot.getZ() + dz * (t + hw);
				if (solid(level, BlockPos.containing(x, bot.getY() + 0.1, z)) || solid(level, BlockPos.containing(x, bot.getY() + 1.5, z))) {
					wall = t;
					break;
				}
			}
			st.edges[k] = edge;
			st.walls[k] = wall;
		}
	}

	private static boolean solid(Level level, BlockPos p) {
		return !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
	}
}
