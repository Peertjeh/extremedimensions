package net.battlehub.bot;

import java.util.List;
import java.util.UUID;

import net.battlehub.core.ai.CombatMind;
import net.battlehub.core.ai.Situation;
import net.battlehub.core.ai.Targeting;
import net.battlehub.core.ai.Track;
import net.battlehub.core.ai.V3;
import net.battlehub.match.Match;
import net.battlehub.util.Ids;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What a bot can know, turned into a {@link Situation}. It only knows what a player in its place could know:
 * <ul>
 * <li>an enemy in plain sight (line of sight, within {@link #SIGHT} blocks, not invisible) is seen exactly;</li>
 * <li>an enemy out of sight but not sneaking and not invisible is located by their name tag, which vanilla draws
 * through walls (position only, never health or what they hold);</li>
 * <li>sneaking or invisible enemies are only known from sight (glowing ones always show);</li>
 * <li>anything else is memory: where they were last seen, with fading certainty.</li>
 * </ul>
 */
final class Perception {
	private Perception() {}

	/** How far a bot can see or read a name tag. */
	static final double SIGHT = 48;

	/** What the bot knows about one enemy this tick. */
	record Known(ServerPlayer player, boolean seen, boolean located) {}

	/**
	 * Looks at every enemy in play: feeds the mind's memory and returns the target the core's {@link Targeting}
	 * prefers (seen, close, weak, the one who just hit the bot, the current one).
	 */
	static Known pickTarget(BotPlayer bot, Match m, CombatMind mind, int tick, UUID current, java.util.function.ToIntFunction<UUID> hitAgo) {
		java.util.List<Targeting.Candidate> candidates = new java.util.ArrayList<>();
		java.util.Map<UUID, Known> known = new java.util.HashMap<>();
		for (UUID id : m.participants()) {
			if (id.equals(bot.getUUID()) || !m.inPlay(id) || !m.isEnemy(bot.getUUID(), id)) continue;
			ServerPlayer p = m.player(id);
			if (p == null || p.level() != bot.level() || p.isSpectator()) continue;
			double d = p.distanceTo(bot);
			if (d > SIGHT) continue;
			boolean glowing = p.hasGlowingTag() || Ids.effect("minecraft:glowing").map(p::hasEffect).orElse(false);
			boolean hidden = p.isInvisible() && !glowing;
			boolean seen = !hidden && (glowing || bot.hasLineOfSight(p));
			boolean located = seen || (!hidden && !p.isShiftKeyDown());
			if (located) {
				mind.see(tick, id, new V3(p.getX(), p.getY(), p.getZ()), d, p.getDeltaMovement().y > 0.1);
			}
			Track t = mind.track(id);
			double sure = located ? 1 : t.confidence(tick, CombatMind.HALF_LIFE);
			if (!located && sure < 0.15) continue;
			candidates.add(new Targeting.Candidate(id, d, seen, located, sure, seen ? p.getHealth() / Math.max(1, p.getMaxHealth()) : 1,
					hitAgo.applyAsInt(id), id.equals(current)));
			known.put(id, new Known(p, seen, located));
		}
		Targeting.Candidate best = Targeting.choose(candidates, mind.personality());
		return best == null ? null : known.get(best.id());
	}

	/** The whole situation for the mind. {@code edges} may be reused from the last think (it is costly). */
	static Situation situation(BotPlayer bot, Match m, CombatMind mind, Known k, int tick, int lastSwing, double[] edges) {
		ServerLevel level = bot.level();
		boolean bow = (has(bot, Items.BOW) || has(bot, Items.CROSSBOW)) && (bot.getInventory().countItem(Items.ARROW) > 0
				|| bot.getInventory().countItem(Items.SPECTRAL_ARROW) > 0 || bot.getInventory().countItem(Items.TIPPED_ARROW) > 0);
		boolean drawing = bot.isUsingItem() && (bot.getUseItem().is(Items.BOW) || bot.getUseItem().is(Items.CROSSBOW));
		Situation.Self self = new Situation.Self(v(bot.position()), v(bot.getDeltaMovement()), bot.getHealth(), bot.getMaxHealth(), bot.onGround(),
				bot.getAttackStrengthScale(0.5f), has(bot, Items.GOLDEN_APPLE) || has(bot, Items.ENCHANTED_GOLDEN_APPLE),
				bot.getOffhandItem().is(Items.SHIELD), reach(bot), hasMelee(bot), bow, drawing ? bot.getTicksUsingItem() : 0);
		Situation.Target target = null;
		if (k != null) {
			ServerPlayer p = k.player();
			Track t = mind.track(p.getUUID());
			V3 pos = k.located() ? v(p.position()) : t.last();
			double sure = k.located() ? (k.seen() ? 1 : 0.8) : t.confidence(tick, CombatMind.HALF_LIFE);
			Vec3 look = p.getLookAngle();
			Vec3 toBot = bot.position().subtract(p.position()).normalize();
			boolean facing = look.x * toBot.x + look.z * toBot.z > 0.8;
			// health is only known from what can be seen: in sight, or the last time it was
			double hp = k.seen() ? p.getHealth() : p.getMaxHealth();
			target = new Situation.Target(p.getUUID(), pos, k.seen() ? v(p.getDeltaMovement()) : V3.ZERO, hp, p.getMaxHealth(), k.seen(), sure,
					k.seen() ? Math.max(0, tick - lastSwing) : 100, k.seen() && facing);
		}
		return new Situation(tick, self, target, m.botGoal(bot), edges, threat(bot, level));
	}

	/**
	 * How far the ground reaches in eight directions (0.6, 1.2, 2 and 3 blocks out), stopping at the first spot
	 * without ground within four blocks below. Water counts as ground (a pool is no drop), lava does not.
	 */
	static double[] edges(BotPlayer bot) {
		double[] out = new double[8];
		double[] steps = {0.6, 1.2, 2.0, 3.0};
		for (int i = 0; i < 8; i++) {
			V3 dir = Situation.direction(i);
			double reached = Situation.SAFE;
			for (double s : steps) {
				double x = bot.getX() + dir.x() * s, z = bot.getZ() + dir.z() * s;
				if (!BotActions.groundBelow(bot.level(), x, bot.getY(), z, 4)) {
					reached = Math.max(0, s - 0.6);
					break;
				}
			}
			out[i] = reached;
		}
		return out;
	}

	/** Arrows and other projectiles flying at the bot (passing within a block and a half). */
	static Situation.Threat threat(BotPlayer bot, ServerLevel level) {
		AABB box = bot.getBoundingBox().inflate(12);
		List<Projectile> near = level.getEntitiesOfClass(Projectile.class, box, e -> e.getOwner() != bot && !e.onGround());
		int n = 0;
		V3 from = V3.ZERO;
		Vec3 me = bot.position().add(0, 1, 0);
		for (Projectile p : near) {
			Vec3 v = p.getDeltaMovement();
			double speed = v.lengthSqr();
			if (speed < 0.04) continue;
			Vec3 rel = me.subtract(p.position());
			double along = rel.dot(v) / speed;
			if (along <= 0 || along > 30) continue;
			Vec3 closest = p.position().add(v.scale(along));
			if (closest.distanceToSqr(me) > 1.5 * 1.5) continue;
			n++;
			from = from.add(v(v.scale(-1)).unit());
		}
		return n == 0 ? Situation.Threat.NONE : new Situation.Threat(n, from.unit());
	}

	static double reach(BotPlayer bot) {
		return Math.max(2.5, Math.min(6, bot.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)));
	}

	private static boolean hasMelee(BotPlayer bot) {
		for (int i = 0; i < 9; i++) {
			net.minecraft.world.item.ItemStack s = bot.getInventory().getItem(i);
			if (s.is(net.minecraft.tags.ItemTags.SWORDS) || s.is(net.minecraft.tags.ItemTags.AXES) || s.is(Items.MACE)) return true;
		}
		return false;
	}

	private static boolean has(BotPlayer bot, net.minecraft.world.item.Item item) {
		for (int i = 0; i < 9; i++) {
			if (bot.getInventory().getItem(i).is(item)) return true;
		}
		return false;
	}

	static V3 v(Vec3 p) {
		return new V3(p.x, p.y, p.z);
	}

	static Vec3 v(V3 p) {
		return new Vec3(p.x(), p.y(), p.z());
	}
}
