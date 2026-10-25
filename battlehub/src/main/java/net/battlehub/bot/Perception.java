package net.battlehub.bot;

import java.util.List;
import java.util.UUID;

import net.battlehub.core.ai.CombatMind;
import net.battlehub.core.ai.Senses;
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
 * What a bot can know, turned into a {@link Situation}. It only knows what a player in its place could know
 * ({@link Senses}):
 * <ul>
 * <li>an enemy in front of it (within the field of view), in plain sight (line of sight, within {@link #SIGHT}
 * blocks, not invisible, or invisible with armor or an item in hand that shows) is seen exactly; glowing ones show
 * through walls, but also only on screen;</li>
 * <li>an enemy on screen behind a wall, not sneaking and not invisible, is located by their name tag, which vanilla
 * draws through walls (position only, never health or what they hold); in a match only as close as the match shows tags
 * (1.49, {@link net.battlehub.match.NameTags});</li>
 * <li>an enemy close by who walks or runs is heard, and one who just hit the bot is known to be there;</li>
 * <li>anything else is memory: where they were last seen, with fading certainty. Turning its back loses you.</li>
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
	static Known pickTarget(BotPlayer bot, Match m, CombatMind mind, int tick, UUID current, java.util.function.ToIntFunction<UUID> hitAgo,
			java.util.Map<UUID, Vec3> lastPositions) {
		java.util.List<Targeting.Candidate> candidates = new java.util.ArrayList<>();
		java.util.Map<UUID, Known> known = new java.util.HashMap<>();
		V3 eye = v(bot.getEyePosition());
		for (UUID id : m.participants()) {
			if (id.equals(bot.getUUID()) || !m.inPlay(id) || !m.isEnemy(bot.getUUID(), id)) continue;
			ServerPlayer p = m.player(id);
			if (p == null || p.level() != bot.level() || p.isSpectator()) continue;
			double d = p.distanceTo(bot);
			if (d > SIGHT) continue;
			boolean glowing = p.hasGlowingTag() || Ids.effect("minecraft:glowing").map(p::hasEffect).orElse(false);
			// invisible, but their armor (or what they hold) shows: seen, the farther the more they wear
			boolean hidden = p.isInvisible() && !glowing && !givenAway(p, d);
			// on screen: the head, the middle or the feet within the field of view
			boolean onScreen = Senses.inView(eye, bot.getYRot(), bot.getXRot(), v(p.getEyePosition()))
					|| Senses.inView(eye, bot.getYRot(), bot.getXRot(), v(p.position().add(0, 0.9, 0)))
					|| Senses.inView(eye, bot.getYRot(), bot.getXRot(), v(p.position()));
			boolean seen = !hidden && onScreen && (glowing || bot.hasLineOfSight(p));
			if (seen) CombatMemory.saw(bot.getUUID(), id);
			Vec3 before = lastPositions.put(id, p.position());
			double speed = before == null ? 0 : Math.hypot(p.getX() - before.x, p.getZ() - before.z);
			boolean heard = Senses.heard(d, p.isShiftKeyDown(), speed, hitAgo.applyAsInt(id));
			// the name tag through walls: not of someone invisible (the game does not draw it), and since 1.49 not
			// beyond the match's name tag range (net.battlehub.match.NameTags: up close, in plain sight only)
			boolean tag = (!p.isInvisible() || glowing) && !p.isShiftKeyDown() && onScreen && !net.battlehub.match.NameTags.hides(m, d);
			boolean located = seen || tag || heard;
			if (located) {
				mind.see(tick, id, new V3(p.getX(), p.getY(), p.getZ()), d, Motion.velocity(p).y > 0.1);
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

	/**
	 * At the start of a fight a bot knows where its rivals stand, as a player does who saw the arena and where the
	 * others spawned (1.49). Name tags no longer give everyone away through walls, so without this a bot behind cover
	 * would not know where to start looking. It is memory like any other: it fades if they are not found.
	 */
	static void knowStart(BotPlayer bot, Match m, CombatMind mind, int tick) {
		for (UUID id : m.participants()) {
			if (id.equals(bot.getUUID()) || !m.inPlay(id) || !m.isRival(bot.getUUID(), id)) continue;
			ServerPlayer p = m.player(id);
			if (p == null || p.level() != bot.level() || p.isSpectator()) continue;
			mind.see(tick, id, v(p.position()), p.distanceTo(bot), false);
		}
	}

	/** Someone invisible whose armor or held item shows at this distance ({@link Senses#invisibleSight}). */
	static boolean givenAway(ServerPlayer p, double dist) {
		int armor = 0;
		for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {net.minecraft.world.entity.EquipmentSlot.HEAD,
				net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
			if (!p.getItemBySlot(slot).isEmpty()) armor++;
		}
		boolean holding = !p.getMainHandItem().isEmpty() || !p.getOffhandItem().isEmpty();
		return dist <= SIGHT * Senses.invisibleSight(armor, holding);
	}

	/** The whole situation for the mind. {@code edges} may be reused from the last think (it is costly). */
	static Situation situation(BotPlayer bot, Match m, CombatMind mind, Known k, int tick, int lastSwing, double[] edges) {
		ServerLevel level = bot.level();
		boolean bow = (has(bot, Items.BOW) || has(bot, Items.CROSSBOW)) && (bot.getInventory().countItem(Items.ARROW) > 0
				|| bot.getInventory().countItem(Items.SPECTRAL_ARROW) > 0 || bot.getInventory().countItem(Items.TIPPED_ARROW) > 0);
		boolean drawing = bot.isUsingItem() && (bot.getUseItem().is(Items.BOW) || bot.getUseItem().is(Items.CROSSBOW));
		Situation.Self self = new Situation.Self(v(bot.position()), v(bot.getDeltaMovement()), bot.getHealth(), bot.getMaxHealth(), bot.onGround(),
				bot.getAttackStrengthScale(0.5f), has(bot, Items.GOLDEN_APPLE) || has(bot, Items.ENCHANTED_GOLDEN_APPLE),
				bot.getOffhandItem().is(Items.SHIELD), reach(bot), hasMelee(bot), bow, drawing ? bot.getTicksUsingItem() : 0, m.botWeapon(),
				has(bot, Ids.item("minecraft:wind_charge")), bot.fallDistance);
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
			target = new Situation.Target(p.getUUID(), pos, k.seen() ? v(Motion.velocity(p)) : V3.ZERO, hp, p.getMaxHealth(), k.seen(), sure,
					k.seen() ? Math.max(0, tick - lastSwing) : 100, k.seen() && facing);
		}
		Situation.Threat arrows = threat(bot, level);
		Smashing smash = smash(bot, m, k, tick);
		Situation.Threat th = smash == null ? arrows
				: new Situation.Threat(arrows.incoming(), arrows.from(), smash.smash(), v(smash.attacker().position()));
		return new Situation(tick, self, target, m.botGoal(bot), edges, th);
	}

	// ------------------------------------------------------------------ a smash coming down (1.49)

	/** How far round (sideways) and up a bot watches for someone coming down on it with a mace. */
	static final double SMASH_WATCH = 10, SMASH_HIGH = 40;
	/** How close the burst of a wind charge and the rush of someone falling are heard. */
	static final double SMASH_HEARD = 16;
	/** How long a smash the bot saw stays in mind when it loses it for a moment (a tick it was behind something). */
	static final int SMASH_KEPT = 10;

	/** A smash a bot has seen coming: from whom, since which tick, its roll for noticing it, when it last saw it. */
	record Spotted(UUID attacker, int since, double roll, int last) {}

	private static final java.util.Map<UUID, Spotted> SPOTTED = new java.util.HashMap<>();

	/** A smash coming down on the bot that it noticed: from whom, and where and when it lands. */
	record Smashing(ServerPlayer attacker, net.battlehub.core.ai.MaceFight.Smash smash) {}

	/**
	 * Someone coming down on the bot with a mace (in hand, or high over it in a mace fight, where the mace comes out at
	 * the last moment) whom it could know about: seen (in view, in plain sight), heard (the burst of the wind charge, the
	 * rush of the fall, within {@link #SMASH_HEARD}), or the one it is fighting. Where their flight comes down follows the
	 * game ({@link net.battlehub.core.ai.MaceFight#incoming}); a bot reacts after its reaction time, and only if its
	 * level notices at all ({@link net.battlehub.core.ai.MaceFight#reacts}, one roll per smash). Null when there is none,
	 * or when it has not (yet) noticed.
	 */
	static Smashing smash(BotPlayer bot, Match m, Known k, int tick) {
		V3 me = v(bot.position()), eye = v(bot.getEyePosition());
		ServerPlayer from = null;
		net.battlehub.core.ai.MaceFight.Smash soonest = null;
		for (UUID id : m.participants()) {
			if (id.equals(bot.getUUID()) || !m.inPlay(id) || !m.isEnemy(bot.getUUID(), id)) continue;
			ServerPlayer p = m.player(id);
			if (p == null || p.level() != bot.level() || p.isSpectator() || p.onGround()) continue;
			double up = p.getY() - bot.getY(), side = Math.hypot(p.getX() - bot.getX(), p.getZ() - bot.getZ());
			if (up < 1 || up > SMASH_HIGH || side > SMASH_WATCH) continue;
			boolean mace = p.getMainHandItem().is(Items.MACE) || (m.botWeapon() == net.battlehub.core.ai.Weapon.MACE && up > 4);
			if (!mace) continue;
			boolean glowing = p.hasGlowingTag();
			boolean seen = (!p.isInvisible() || glowing || givenAway(p, p.distanceTo(bot)))
					&& (Senses.inView(eye, bot.getYRot(), bot.getXRot(), v(p.getEyePosition())) || Senses.inView(eye, bot.getYRot(), bot.getXRot(), v(p.position())))
					&& bot.hasLineOfSight(p);
			boolean known = seen || p.distanceTo(bot) <= SMASH_HEARD || (k != null && k.player() == p && k.located());
			if (!known) continue;
			net.battlehub.core.ai.MaceFight.Smash s = net.battlehub.core.ai.MaceFight.incoming(me, v(p.position()), v(Motion.velocity(p)),
					p.fallDistance, net.battlehub.core.ai.MaceFight.SMASH_HORIZON);
			if (s != null && (soonest == null || s.in() < soonest.in())) {
				soonest = s;
				from = p;
			}
		}
		Spotted was = SPOTTED.get(bot.getUUID());
		if (soonest == null) {
			if (was != null && tick - was.last() > SMASH_KEPT) SPOTTED.remove(bot.getUUID());
			return null;
		}
		Spotted now = was != null && was.attacker().equals(from.getUUID()) && tick - was.last() <= SMASH_KEPT
				? new Spotted(was.attacker(), was.since(), was.roll(), tick)
				: new Spotted(from.getUUID(), tick, bot.getRandom().nextDouble(), tick);
		SPOTTED.put(bot.getUUID(), now);
		return net.battlehub.core.ai.MaceFight.reacts(bot.difficulty, tick - now.since(), now.roll()) ? new Smashing(from, soonest) : null;
	}

	/** Forgets what a bot saw coming (it left, or its fight ended). */
	static void forget(UUID bot) {
		SPOTTED.remove(bot);
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

	/** In the whole inventory: what is not in the hotbar the bot brings there when it needs it (1.49). */
	private static boolean hasMelee(BotPlayer bot) {
		for (int i = 0; i < 36; i++) {
			net.minecraft.world.item.ItemStack s = bot.getInventory().getItem(i);
			if (s.is(net.minecraft.tags.ItemTags.SWORDS) || s.is(net.minecraft.tags.ItemTags.AXES) || s.is(Items.MACE)) return true;
		}
		return false;
	}

	private static boolean has(BotPlayer bot, net.minecraft.world.item.Item item) {
		for (int i = 0; i < 36; i++) {
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
