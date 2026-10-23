package net.battlehub.bot;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import net.battlehub.core.BotLevel;
import net.battlehub.core.BotMath;
import net.battlehub.core.ParkourCourse;
import net.battlehub.core.ai.Archetype;
import net.battlehub.core.ai.Bridging;
import net.battlehub.core.ai.CombatMind;
import net.battlehub.core.ai.Difficulty;
import net.battlehub.core.ai.Intent;
import net.battlehub.core.ai.JumpPlanner;
import net.battlehub.core.ai.Situation;
import net.battlehub.core.ai.SpleefPlanner;
import net.battlehub.core.ai.Tactic;
import net.battlehub.core.ml.ModelReport;
import net.battlehub.games.ParkourMatch;
import net.battlehub.games.prophunt.Disguise;
import net.battlehub.games.prophunt.PropHuntMatch;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.session.Sessions;
import net.battlehub.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What a bot thinks, twenty times a second: it picks a target or a goal for the game it is in and sets the
 * inputs a client would (walk, strafe, sprint, jump, look, hit, draw a bow). Its level decides how quickly it
 * reacts, how well it aims and which tricks it knows.
 */
final class BotBrain {
	private static final double BOW_SPEED = 3.0;
	private static final double CROSSBOW_SPEED = 3.15;

	private final BotPlayer bot;
	private final BotLevel skill;
	private final RandomSource rnd = RandomSource.create();
	private int ticks;
	private UUID target;
	private int seen;
	private int strafe = 1;
	private float yawError;
	private float pitchError;
	private Vec3 wander;
	private int wanderTicks;
	private Vec3 lastPos = Vec3.ZERO;
	private int stuck;
	/** Ticks left of a sidestep round something the bot ran into (a wall, cover). */
	private int detour;
	// The route to where the bot is going, worked out again now and then
	private List<Vec3> path;
	private int pathIndex;
	private int pathAge;
	private Vec3 pathGoal;
	private int eating;
	private float runYaw = Float.NaN;
	// Parkour: the platform the bot last stood on and the pause before the next jump
	private int pkWait;
	// Prop Hunt
	private Vec3 hideSpot;
	private int hideTicks;
	// The fighting mind (see core.ai): perception goes in, one intent per tick comes out
	private CombatMind mind;
	private double[] edges;
	private int edgesAt;
	private int lastSwing = -1000;
	private float lastCharge = 1f;
	private boolean fighting;
	/** Players whose remembered habits were given to this fight's models already. */
	private final java.util.Set<UUID> seeded = new java.util.HashSet<>();
	/** The hand on the mouse: where the crosshair really points while fighting (see core.ai.AimModel). */
	private net.battlehub.core.ai.AimModel aim;
	private UUID aimingAt;
	/** Where each player was last tick (to hear who walks or runs). */
	private final java.util.Map<UUID, Vec3> heardAt = new java.util.HashMap<>();
	private UUID chargeOf;
	private int lookingAround;
	// Edges: the last place with ground under the bot, and what it does in the air after a knock
	private Vec3 lastGround;
	private int airTicks;
	private int recoverChoice;
	// How this match's fight kept to the learned model (bot/ModelReports), and what changed the model's choice this tick
	private ModelReport report = new ModelReport();
	private String reportSpec = "";
	private boolean fightTick, heldBackNow, jumpRefused, airTook, unstuck, ateByRules, broke;

	BotBrain(BotPlayer bot) {
		this.bot = bot;
		this.skill = bot.skill;
		this.mind = Bots.newMind(bot, null);
	}

	CombatMind mind() {
		return mind;
	}

	/** A new fighting style (the profile command); the level stays. */
	void profile(Archetype archetype) {
		CombatMind old = mind;
		mind = Bots.newMind(bot, archetype);
		mind.inherit(old);
	}

	void dealt(double hp) {
		mind.dealt(hp);
	}

	void taken(double hp) {
		mind.taken(hp);
		// a hit throws the aim off a little, as for anyone
		if (aim != null) aim.disturb(1.5 + hp * 0.8);
	}

	/** Who last hurt this bot, and when (for choosing whom to fight). */
	private final java.util.Map<UUID, Integer> hitBy = new java.util.HashMap<>();

	void hitBy(UUID attacker) {
		hitBy.put(attacker, ticks);
		mind.hitBy(attacker);
	}

	int hitAgo(UUID id) {
		Integer t = hitBy.get(id);
		return t == null ? 100_000 : ticks - t;
	}

	void tick() {
		ticks++;
		if (detour > 0) {
			detour--;
		}
		bot.zza = 0;
		bot.xxa = 0;
		bot.setJumping(false);
		building = false;
		fightTick = heldBackNow = jumpRefused = airTook = unstuck = ateByRules = broke = false;
		accounted = "";
		if (bot.isShiftKeyDown()) bot.setShiftKeyDown(false);
		Match m = Matches.of(bot.getUUID());
		if (m == null || Sessions.state(bot.getUUID()) != Sessions.State.MATCH) {
			forget();
			return;
		}
		if (!m.inPlay(bot.getUUID()) || bot.isSpectator()) {
			Looting.stop(bot, 0);
			stopUsing();
			bot.setSprinting(false);
			pkStage = 0;
			return;
		}
		m.botTick(bot);
		boolean threatened = bot.hurtTime > 0 || enemyNear(m, 5);
		Looting.tick(bot, !threatened);
		if (Looting.busy(bot)) {
			if (threatened) {
				// a hit or someone close: the chest can wait
				Looting.stop(bot, Looting.PAUSE);
			} else {
				// in the chest's screen: standing still, eyes on it
				BlockPos chest = Looting.chest(bot);
				if (chest != null) lookAt(Vec3.atCenterOf(chest), false);
				bot.setSprinting(false);
				stopUsing();
				return;
			}
		}
		// a busy server: everyone thinks a little less often
		mind.stretch(Bots.thinkStretch());
		if (ticks % 5 == 0) show(m);
		if (ticks % 30 == 0) {
			double err = mind.difficulty().aimError;
			yawError = (float) ((rnd.nextDouble() * 2 - 1) * err);
			pitchError = (float) ((rnd.nextDouble() * 2 - 1) * err * 0.5);
		}
		switch (m.botStyle()) {
			case MELEE -> melee(m);
			// the mind picks bow or blade by distance and what the bot carries
			case RANGED -> melee(m);
			case RUNNER -> runner(m, false);
			case PARKOUR -> parkour(m);
			case BOW_SPLEEF -> runner(m, true);
			case PROP_HUNT -> propHunt(m);
			case SPLEEF -> spleef(m);
		}
		if (m.botStyle() != net.battlehub.core.BotStyle.PARKOUR && !building) {
			// What the bot wanted, before an edge held it back: standing still at an edge is being stuck too
			boolean wanted = bot.zza != 0 || bot.xxa != 0;
			boolean heldBack = keepSafe();
			heldBackNow = heldBack;
			unstick(wanted, heldBack);
			recover();
		}
		account();
	}

	/**
	 * This tick into the report on the learned model: kept (the model chose and the bot did it), changed (the bot's own
	 * rules changed the model's choice) or replaced (the scripted mind fought instead).
	 */
	private void account() {
		accounted = "";
		if (!fightTick) return;
		if (mind.learnedNow()) {
			ModelReport.Why why = airTook ? ModelReport.Why.AIR : jumpRefused ? ModelReport.Why.JUMP : heldBackNow ? ModelReport.Why.EDGE
					: unstuck ? ModelReport.Why.STUCK : ateByRules ? ModelReport.Why.HEAL : broke ? ModelReport.Why.OBJECTIVE : null;
			if (why == null) report.kept();
			else report.changed(why);
			accounted = why == null ? "kept" : why.id();
		} else {
			ModelReport.Why why = mind.reactingNow() ? ModelReport.Why.REACT : ModelReport.Why.of(mind.tactic());
			report.replaced(why);
			accounted = why.id();
		}
	}

	/** A decision of a learned model: which of the values it got were new to it, and what it chose (the fight logs). */
	void decided(CombatModels.Family fam, float[] obs, int[] choice) {
		report.decision(fam.spec(), obs, ModelReports.ranges(fam), ModelReports.lessons());
		reportSpec = fam.id();
		modelChoice = choice;
		modelChoiceTick = ticks;
	}

	private int[] modelChoice;
	private int modelChoiceTick = -100;
	/** This tick in the report: "kept", a reason, or "" when it was no tick of a learned fight. */
	private String accounted = "";

	/** The learned model's last choice (its action heads) while it is recent (this decision), else null. */
	int[] modelChoice() {
		return ticks - modelChoiceTick <= 2 ? modelChoice : null;
	}

	/** How the last tick went against the model: "kept", why not, or "" (FightRecorder). */
	String accounted() {
		return accounted;
	}

	/** The player this bot fights now, or null. */
	UUID targetId() {
		return target;
	}

	/** How this match's fight kept to the learned model so far, and with which spec ("" before its first decision). */
	ModelReport report() {
		return report;
	}

	String reportSpec() {
		return reportSpec;
	}

	/** Told: the next report starts from nothing (a series goes on in the same match). */
	void resetReport() {
		report = new ModelReport();
	}

	/** An enemy in play within {@code r} blocks. */
	private boolean enemyNear(Match m, double r) {
		for (UUID id : m.participants()) {
			if (id.equals(bot.getUUID()) || !m.inPlay(id) || !m.isEnemy(bot.getUUID(), id)) continue;
			ServerPlayer p = m.player(id);
			if (p != null && p.level() == bot.level() && p.distanceToSqr(bot) < r * r) return true;
		}
		return false;
	}

	private void forget() {
		Looting.forget(bot);
		if (fighting) {
			// a new match starts with a clear head (the personality stays)
			mind.reset(ticks);
			fighting = false;
			edges = null;
			seeded.clear();
		}
		target = null;
		wander = null;
		path = null;
		hideSpot = null;
		pkPlan = null;
		pkStage = 0;
		pkLadder = -1;
		pkFailed.clear();
		heardAt.clear();
		aimingAt = null;
		placedBlocks = 0;
		mining = null;
		bridgeAt = null;
		lastIntent = null;
		eating = 0;
		report = new ModelReport();
		reportSpec = "";
		runYaw = Float.NaN;
		stopUsing();
		bot.setSprinting(false);
	}

	private ServerLevel level() {
		return bot.level();
	}

	// ------------------------------------------------------------------ targets

	/** The nearest enemy in play (the current one a little preferred), or null. */
	private ServerPlayer target(Match m, double range) {
		ServerPlayer best = null;
		double bestD = range;
		for (UUID id : m.participants()) {
			if (id.equals(bot.getUUID()) || !m.inPlay(id) || !m.isEnemy(bot.getUUID(), id)) continue;
			ServerPlayer p = m.player(id);
			if (p == null || p.level() != bot.level() || p.isSpectator()) continue;
			double d = p.distanceTo(bot) - (id.equals(target) ? 3 : 0);
			if (d < bestD) {
				best = p;
				bestD = d;
			}
		}
		if (best == null) {
			target = null;
			return null;
		}
		if (!best.getUUID().equals(target)) {
			target = best.getUUID();
			seen = 0;
		} else {
			seen++;
		}
		return best;
	}

	// ------------------------------------------------------------------ looking and moving

	private float turnSpeed() {
		return mind.difficulty().turnSpeed;
	}

	private void look(float yaw, float pitch) {
		float y = BotMath.turn(bot.getYRot(), yaw, turnSpeed());
		float p = BotMath.turn(bot.getXRot(), pitch, turnSpeed());
		bot.setYRot(y);
		bot.setYHeadRot(y);
		bot.yBodyRot = y;
		bot.setXRot(Math.max(-90f, Math.min(90f, p)));
	}

	private void lookAt(Vec3 at, boolean withError) {
		Vec3 eye = bot.getEyePosition();
		double dx = at.x - eye.x;
		double dz = at.z - eye.z;
		float yaw = BotMath.yaw(dx, dz) + (withError ? yawError : 0);
		float pitch = BotMath.pitch(Math.sqrt(dx * dx + dz * dz), at.y - eye.y) + (withError ? pitchError : 0);
		look(yaw, pitch);
	}

	/**
	 * Aims at a point the way a hand on a mouse does ({@link net.battlehub.core.ai.AimModel}): with a moment's lag,
	 * a capped turn, a wobble and an overshoot on big turns. Where the crosshair ends up decides whether a swing lands.
	 */
	private void aimAt(Vec3 at) {
		if (aim == null) {
			aim = new net.battlehub.core.ai.AimModel(net.battlehub.core.ai.AimModel.Params.of(mind.difficulty()), bot.getUUID().getLeastSignificantBits());
		}
		Vec3 eye = bot.getEyePosition();
		aim.sync(bot.getYRot(), bot.getXRot());
		// from positions: the target's movement is followed with a lag, the bot's own is known at once
		float[] a = aim.stepAt(eye.x, eye.y, eye.z, at.x, at.y, at.z);
		bot.setYRot(a[0]);
		bot.setYHeadRot(a[0]);
		bot.yBodyRot = a[0];
		bot.setXRot(a[1]);
	}

	/** Is the crosshair on this player: the look ray hits their hitbox within reach, with no block in front? */
	private boolean onCrosshair(ServerPlayer t) {
		double range = bot.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
		Vec3 eye = bot.getEyePosition();
		net.minecraft.world.phys.AABB box = t.getBoundingBox().inflate(t.getPickRadius());
		if (box.contains(eye)) return true;
		java.util.Optional<Vec3> hit = box.clip(eye, eye.add(bot.getLookAngle().scale(range)));
		if (hit.isEmpty()) return false;
		net.minecraft.world.phys.BlockHitResult wall = level().clip(new net.minecraft.world.level.ClipContext(eye, hit.get(),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, bot));
		return wall.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
	}

	/**
	 * The next point on the way to {@code dest}: a corner of the route round walls and cover, or {@code dest}
	 * itself when the way is straight or no route was found. The route is worked out again every second, or
	 * sooner when the goal moved.
	 */
	private Vec3 waypoint(Vec3 dest) {
		pathAge++;
		refreshRoute(dest);
		if (path == null) {
			return dest;
		}
		Vec3 pos = bot.position();
		while (pathIndex < path.size()) {
			Vec3 w = path.get(pathIndex);
			double dx = w.x - pos.x;
			double dz = w.z - pos.z;
			if (dx * dx + dz * dz > 0.5 * 0.5 || Math.abs(w.y - pos.y) > 1.2) break;
			pathIndex++;
		}
		// Look a little further along a straight stretch, so the bot walks smoothly
		if (pathIndex + 1 < path.size()) {
			Vec3 a = path.get(pathIndex);
			Vec3 b = path.get(pathIndex + 1);
			if (Math.abs(a.y - b.y) < 0.1 && a.distanceToSqr(pos) < 1.2) return b;
		}
		return pathIndex < path.size() ? path.get(pathIndex) : dest;
	}

	/** When the last route search ran: one that found no way is not tried again every tick. */
	private int routeAt = -100;

	/**
	 * Works the route out again when it is old (a second) or the goal moved; when there was no way, it tries again
	 * after a second (a quarter of a second when the goal moved), since a search that finds nothing is the costly one.
	 */
	private void refreshRoute(Vec3 dest) {
		boolean moved = pathGoal == null || pathGoal.distanceToSqr(dest) > 4;
		boolean due = path != null ? pathAge > 20 || moved : ticks - routeAt >= (moved ? 5 : 20);
		if (due) {
			path = BotPath.find(level(), bot.position(), dest);
			pathIndex = 0;
			pathAge = 0;
			pathGoal = dest;
			routeAt = ticks;
		}
	}

	/** Walks (and sprints) towards a spot along a route round obstacles; jumps over steps. */
	private void goTo(Vec3 dest, boolean sprint) {
		Vec3 pos = bot.position();
		double dx = dest.x - pos.x;
		double dz = dest.z - pos.z;
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d < 0.7) {
			bot.setSprinting(false);
			return;
		}
		Vec3 next = waypoint(dest);
		double nx = next.x - pos.x;
		double nz = next.z - pos.z;
		look(BotMath.yaw(nx, nz), 0f);
		bot.zza = 1f;
		bot.setSprinting(sprint && skill.sprints && d > 3);
		sidestep();
		if (bot.onGround() && (bot.horizontalCollision || (next.y - pos.y > 0.5 && Math.sqrt(nx * nx + nz * nz) < 2.0)
				|| (dest.y - pos.y > 0.6 && d < 2.5))) {
			jump();
		}
	}

	/** Somewhere random nearby with ground under it. */
	private void wanderAbout(Vec3 around, double radius) {
		if (wander == null || wanderTicks-- <= 0 || bot.position().distanceTo(wander) < 1.2) {
			wander = null;
			for (int i = 0; i < 12 && wander == null; i++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				double r = 3 + rnd.nextDouble() * radius;
				Vec3 c = around.add(Math.cos(a) * r, 0, Math.sin(a) * r);
				if (BotActions.groundBelow(level(), c.x, around.y + 1, c.z, 4)) {
					wander = c;
				}
			}
			wanderTicks = 120;
			if (wander == null) {
				return;
			}
		}
		goTo(wander, false);
	}

	/**
	 * Never walks off an edge into the void or a hole: it looks further ahead the faster it goes, and when it slides
	 * towards an edge (after a knock) it holds the keys against the slide.
	 */
	private boolean keepSafe() {
		if (!bot.onGround()) {
			return false;
		}
		Vec3 v = bot.getDeltaMovement();
		double speed = v.horizontalDistance();
		// its own walk or sprint may keep a corner of the feet over the edge; a knock (or anything faster) brakes as soon
		// as the middle of the feet would leave the ground
		boolean ownPace = bot.hurtTime == 0 && speed < 0.3;
		if (speed > 0.12 && !safe(new Vec3(v.x, 0, v.z).normalize(), 0.6 + speed * 3, ownPace)) {
			brake();
			bot.setSprinting(false);
			return true;
		}
		if (bot.zza == 0 && bot.xxa == 0) {
			return false;
		}
		Vec3 fwd = BotActions.dir(bot.getYRot());
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		Vec3 move = fwd.scale(bot.zza).add(right.scale(-bot.xxa));
		if (move.lengthSqr() < 1e-4 || safe(move.normalize())) {
			return false;
		}
		// Try going only straight on, or only sideways
		if (bot.zza != 0 && safe(fwd.scale(Math.signum(bot.zza)))) {
			bot.xxa = 0;
			strafe = -strafe;
		} else if (bot.xxa != 0 && safe(right.scale(-Math.signum(bot.xxa)))) {
			bot.zza = 0;
		} else {
			bot.zza = 0;
			bot.xxa = 0;
			bot.setSprinting(false);
			strafe = -strafe;
		}
		return true;
	}

	private boolean safe(Vec3 dir) {
		double speed = bot.getDeltaMovement().horizontalDistance();
		return safe(dir, 1.1 + Math.max(0, speed - 0.15) * 3, true);
	}

	/**
	 * There is ground under the feet {@code distance} out this way: under their middle, or (with {@code corners}) under
	 * a corner of them ({@link net.battlehub.core.ai.Footing}: a narrow bridge walked at an angle, the corner of a built
	 * staircase).
	 */
	private boolean safe(Vec3 dir, double distance, boolean corners) {
		Vec3 p = bot.position().add(dir.scale(distance));
		net.battlehub.core.ai.Footing.Ground ground = (x, z) -> BotActions.groundBelow(level(), x, p.y, z, 4);
		return corners ? net.battlehub.core.ai.Footing.holds(ground, p.x, p.z) : ground.at(p.x, p.z);
	}

	// ------------------------------------------------------------------ jumping and falling

	/**
	 * Jumps, unless the jump would end in the void: the player's movement is carried on from here (with the keys
	 * pressed now) to where it comes down. Lower levels do not always look first.
	 */
	private void jump() {
		if (!bot.onGround()) return;
		if (rnd.nextDouble() < mind.difficulty().edgeCare) {
			Vec3 v = bot.getDeltaMovement();
			double[] dir = keysDirection();
			if (!net.battlehub.core.ai.Falls.landsSafely(bot.getX(), bot.getY(), bot.getZ(), v.x, 0, v.z, dir[0], dir[1], bot.isSprinting(), true,
					this::surface, 1.5)) {
				jumpRefused = true;
				return;
			}
		}
		bot.setJumping(true);
	}

	/** A jump only when its landing is on ground (with the keys pressed now). */
	private void jumpLooking() {
		if (!bot.onGround()) return;
		Vec3 v = bot.getDeltaMovement();
		double[] dir = keysDirection();
		if (net.battlehub.core.ai.Falls.landsSafely(bot.getX(), bot.getY(), bot.getZ(), v.x, 0, v.z, dir[0], dir[1], bot.isSprinting(), true,
				this::surface, 1.5)) {
			bot.setJumping(true);
		}
	}

	/**
	 * In the air, knocked or fallen off: when the flight would end in the void, it holds the keys back towards the
	 * last ground (a player does, after a moment to notice; lower levels not always).
	 */
	/** This flight began with a hit (knockback), not with a jump of its own. */
	private boolean knocked;

	private void recover() {
		if (bot.onGround()) {
			airTicks = 0;
			recoverChoice = 0;
			BlockPos under = support();
			if (under != null) lastGround = Vec3.atBottomCenterOf(under).add(0, 1, 0);
			return;
		}
		airTicks++;
		// knocked into the air, a player may freeze; off its own jump it always steers back
		if (airTicks == 1) knocked = bot.hurtTime > 0;
		if (lastGround == null || airTicks < 1 + (knocked ? mind.difficulty().reactionTicks / 3 : 0)) return;
		if (recoverChoice == 0) recoverChoice = !knocked || rnd.nextDouble() < mind.difficulty().edgeCare ? 1 : -1;
		if (recoverChoice < 0) return;
		Vec3 v = bot.getDeltaMovement();
		if (net.battlehub.core.ai.Falls.landsSafely(bot.getX(), bot.getY(), bot.getZ(), v.x, v.y, v.z, 0, 0, false, false, this::surface, 3)) {
			return;
		}
		moveToward(lastGround, 1f);
		bot.setSprinting(false);
		airTook = true;
	}

	/** The world direction of the keys pressed now (forward and strafe, relative to where the bot faces). */
	private double[] keysDirection() {
		double yaw = Math.toRadians(bot.getYRot());
		double x = -Math.sin(yaw) * bot.zza + Math.cos(yaw) * bot.xxa;
		double z = Math.cos(yaw) * bot.zza + Math.sin(yaw) * bot.xxa;
		double len = Math.hypot(x, z);
		return len < 1e-6 ? new double[] {0, 0} : new double[] {x / len, z / len};
	}

	/** The top of the highest block under (x, z) at or below y, within a few blocks (water is a soft landing); NaN for a drop. */
	private double surface(double x, double y, double z) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Math.floor(x), Math.floor(y), Math.floor(z));
		for (int i = 0; i < 7; i++) {
			BlockState st = level().getBlockState(p);
			if (!st.getFluidState().isEmpty()) {
				return st.is(net.minecraft.world.level.block.Blocks.WATER) ? p.getY() + 0.9 : Double.NaN;
			}
			net.minecraft.world.phys.shapes.VoxelShape shape = st.getCollisionShape(level(), p);
			if (!shape.isEmpty()) return p.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
			p.move(0, -1, 0);
		}
		return Double.NaN;
	}

	/** While going round something: mostly sideways, a little forward. */
	private void sidestep() {
		if (detour > 0) {
			bot.zza = 0.35f;
			bot.xxa = strafe;
			bot.setSprinting(false);
		}
	}

	/**
	 * Pushing against something (or held back at an edge) for a second: jump and change direction. At an edge the
	 * jump always looks first (never one that ends in the void, whatever the level), and in the air it steers back.
	 */
	private void unstick(boolean trying, boolean atEdge) {
		if (ticks % 20 != 0) {
			return;
		}
		Vec3 pos = bot.position();
		if (trying && pos.distanceToSqr(lastPos) < 0.04) {
			stuck++;
			if (stuck >= 2) {
				unstuck = true;
				if (atEdge) jumpLooking();
				else jump();
				strafe = -strafe;
				wander = null;
				// The route may be out of date (a block placed, a player in the way): work it out again
				path = null;
				// Go round it: a sidestep, to the other side each time it does not help
				detour = 25 + rnd.nextInt(20);
			}
		} else {
			stuck = 0;
		}
		lastPos = pos;
	}

	// ------------------------------------------------------------------ items

	private int find(Predicate<ItemStack> what) {
		Inventory inv = bot.getInventory();
		for (int i = 0; i < 9; i++) {
			if (what.test(inv.getItem(i))) return i;
		}
		return -1;
	}

	/** The melee weapon of this fight first (the mace in a mace fight, the axe in an axe fight), else any. */
	private void selectMelee(net.battlehub.core.ai.Weapon focus) {
		Predicate<ItemStack> want = switch (focus) {
			case MACE -> s -> s.is(Items.MACE);
			case AXE -> s -> s.is(ItemTags.AXES);
			case SWORD -> s -> s.is(ItemTags.SWORDS);
			default -> null;
		};
		if (want != null && select(want)) return;
		select(BotBrain::isMelee);
	}

	/**
	 * A wind charge thrown straight down at its own feet: the burst sends the bot up, and the mace comes down on the
	 * target with the smash bonus (the steering keeps it moving to the target and swings on the way down).
	 */
	private void launch() {
		if (!bot.onGround()) return;
		int slot = find(s -> s.is(Ids.item("minecraft:wind_charge")));
		if (slot < 0) return;
		stopUsing();
		// as a player does it: standing, jump and throw it straight down; the burst comes under the rising feet
		bot.zza = 0;
		bot.xxa = 0;
		bot.setSprinting(false);
		bot.setJumping(true);
		bot.getInventory().setSelectedSlot(slot);
		bot.setXRot(90f);
		BotActions.use(bot, InteractionHand.MAIN_HAND);
		BotActions.swing(bot);
		launches++;
	}

	private int launches;

	/** Wind charges thrown to fly up (mace fights). */
	int launches() {
		return launches;
	}

	private static boolean isMelee(ItemStack s) {
		return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.MACE);
	}

	private boolean select(Predicate<ItemStack> what) {
		if (what.test(bot.getMainHandItem())) {
			return true;
		}
		int slot = find(what);
		if (slot < 0) {
			return false;
		}
		stopUsing();
		bot.getInventory().setSelectedSlot(slot);
		return true;
	}

	private void stopUsing() {
		if (bot.isUsingItem()) {
			bot.stopUsingItem();
		}
	}

	/** Golden apple when low on health (normal and hard bots). */
	private boolean eat() {
		if (eating > 0) {
			eating--;
			if (!bot.isUsingItem()) {
				eating = 0;
			}
			return eating > 0;
		}
		double below = mind.difficulty().healBelow;
		if (below <= 0 || bot.getHealth() >= below) {
			return false;
		}
		int slot = find(s -> s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE));
		if (slot < 0) {
			return false;
		}
		stopUsing();
		bot.getInventory().setSelectedSlot(slot);
		BotActions.use(bot, InteractionHand.MAIN_HAND);
		eating = 40;
		return true;
	}

	// ------------------------------------------------------------------ fighting

	/**
	 * Fighting through the mind: what the bot can perceive becomes a situation, the mind turns it into one intent,
	 * and the intent is carried out with the same inputs a client sends. The edge check afterwards
	 * ({@link #keepSafe()}) still has the last word.
	 */
	private void melee(Match m) {
		fighting = true;
		Perception.Known k = Perception.pickTarget(bot, m, mind, ticks, target, this::hitAgo, heardAt);
		ServerPlayer t = k == null ? null : k.player();
		target = t == null ? null : t.getUUID();
		if (t != null && !Bots.isBot(t.getUUID()) && AiMemory.learning() && seeded.add(t.getUUID())) {
			// what bots learned about this player before gives this fight a head start
			AiMemory.get(bot.level().getServer(), t.getUUID()).seed(mind.model(t.getUUID()), mind.outcomes());
		}
		// a swing everyone can see resets the swinger's attack charge: a sudden drop means they just attacked
		float charge = t != null && k.seen() ? t.getAttackStrengthScale(0.5f) : 1f;
		UUID of = t != null && k.seen() ? t.getUUID() : null;
		if (of == null || !of.equals(chargeOf)) lastCharge = charge;
		chargeOf = of;
		if (charge < lastCharge - 0.3f) {
			lastSwing = ticks;
			mind.opponentSwing(t.getUUID(), ticks);
		}
		lastCharge = charge;
		if (t != null) {
			if (aim == null) aim = new net.battlehub.core.ai.AimModel(net.battlehub.core.ai.AimModel.Params.of(mind.difficulty()),
					bot.getUUID().getLeastSignificantBits());
			if (!t.getUUID().equals(aimingAt)) {
				aim.retarget();
				aimingAt = t.getUUID();
			}
			// how predictable this player has proved: steady strafers are led all the way, erratic ones less
			net.battlehub.core.ai.OpponentModel om = mind.model(t.getUUID());
			aim.adapt(om.known() ? om.predictability() : -1);
		}
		// near an edge the ground round the bot is measured every tick (it changes with every step)
		boolean nearEdge = edges != null && java.util.Arrays.stream(edges).min().orElse(Situation.SAFE) < 2.5;
		if (edges == null || nearEdge || ticks - edgesAt >= mind.difficulty().thinkTicks * Bots.thinkStretch()) {
			edges = Perception.edges(bot);
			edgesAt = ticks;
		}
		Situation s = Perception.situation(bot, m, mind, k, ticks, lastSwing, edges);
		Intent in = mind.tick(s);
		lastIntent = in;
		// a tick of a fight a learned model is in on: the model steered, or the target is close enough that it would
		fightTick = t != null && ModelReports.inPlay(m) && (mind.learnedNow() || s.distance() <= LearnedFight.ENGAGE);
		if (t == null && in.moveTo() == null && mind.tactic() == Tactic.IDLE) {
			// nobody to be seen and nothing to do: look round first (someone may be behind), then walk about
			stopUsing();
			if (lookingAround++ % 120 < 50) {
				look(bot.getYRot() + Math.min(12f, turnSpeed() * 0.5f), 0f);
			} else {
				wanderAbout(bot.position(), 8);
			}
			return;
		}
		lookingAround = 0;
		execute(m, in, k);
	}

	/** Carries out one intent of the mind. */
	private void execute(Match m, Intent in, Perception.Known k) {
		ServerPlayer t = k == null ? null : k.player();
		if (in.handChosen()) {
			learnedHand(m, in);
		} else if (in.heal() || eating > 0) {
			eat();
			if (mind.learnedNow() && eating > 0) ateByRules = true;
		} else if (in.bow() && t != null) {
			// shoot() selects the bow or crossbow, draws, aims along the arc and lets go when charged
			shoot(Perception.v(in.look()), Vec3.ZERO);
		} else {
			if (bot.isUsingItem() && bot.getUsedItemHand() == InteractionHand.MAIN_HAND) stopUsing();
			selectMelee(m.botWeapon());
		}
		if (in.moveTo() != null) {
			if (!bridge(m, Perception.v(in.moveTo()))) {
				Vec3 dest = Perception.v(in.moveTo());
				if (in.look() != null && t != null && k.seen() && t.distanceTo(bot) < 6) {
					// close by and in sight: the eyes stay on them, the keys take the bot where it wants to go
					aimAt(Perception.v(in.look()));
					moveToward(waypoint(dest), 1f);
					bot.setSprinting(in.sprint() && skill.sprints && bot.zza > 0.5f);
					if (bot.onGround() && bot.horizontalCollision) jump();
				} else {
					goTo(dest, in.sprint());
				}
			}
		} else {
			if (in.look() != null && !in.bow()) {
				aimAt(Perception.v(in.look()));
			}
			bot.zza = (float) in.forward();
			bot.xxa = (float) in.strafe();
			if (in.handChosen() && bot.isUsingItem()) {
				// drawing, eating or blocking walks at a fifth, as for a player (the client does that for them)
				bot.zza *= 0.2f;
				bot.xxa *= 0.2f;
			}
			bot.setSprinting(in.sprint() && in.forward() > 0 && !bot.isUsingItem() && mind.difficulty() != Difficulty.BEGINNER);
			if (bot.onGround() && bot.horizontalCollision && in.forward() > 0) {
				jump();
			}
		}
		if (in.jump() && bot.onGround()) {
			jump();
		}
		if (in.launch()) {
			launch();
		}
		if (!in.handChosen()) shield(in.block());
		// an enemy bed (or what the game wants broken) within reach goes first: a fight can wait a second, the chance not
		BlockPos breakable = building ? null : m.botBreakTarget(bot);
		if (breakable != null && bot.getEyePosition().distanceTo(Vec3.atCenterOf(breakable))
				<= bot.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE) + 0.5) {
			if (in.attack() && mind.learnedNow()) broke = true;
			breakThrough(m);
		} else if (in.attack() && t != null && k.seen()) {
			// the click: it lands only when the crosshair is really on them, within reach, with nothing in between
			shield(false);
			boolean landed = onCrosshair(t);
			if (landed) {
				BotActions.attack(bot, t);
			} else {
				BotActions.swing(bot);
			}
			mind.swung(landed);
			// a swing at the air costs the charge as well (the attack meter starts again for a player too)
			bot.resetAttackStrengthTicker();
			mining = null;
		} else if (!building && (mind.tactic() == Tactic.OBJECTIVE || t == null)) {
			breakThrough(m);
		}
	}

	// ------------------------------------------------------------------ building across gaps, breaking through

	private int lastPlace = -100;
	private int placedBlocks;
	/** The next block of a bridge (shown to admins watching). */
	private BlockPos bridgeAt;
	private Intent lastIntent;
	/** This tick the bot builds: keeping it from edges is its own job then. */
	private boolean building;
	private BlockPos mining;
	private int mineTicks;

	/** Blocks placed in the current match. */
	int placed() {
		return placedBlocks;
	}

	/** A block to bridge with: something to stand on (no TNT, and no cobweb or flower in a bridge). */
	static boolean isBuildBlock(ItemStack s) {
		if (!(s.getItem() instanceof net.minecraft.world.item.BlockItem bi) || s.is(Items.TNT)) return false;
		BlockState st = bi.getBlock().defaultBlockState();
		return !st.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	/**
	 * Building across a gap on the way to {@code to} ({@link Bridging}): onto the last floor block before the gap,
	 * sneaking to its edge (the edge holds a sneaking player, its middle a little past it, so the side below can be
	 * seen), looking at that side and placing a block against it through the normal rules, no faster than the level
	 * allows; then onto the new block and on. True while it builds (it moves the bot itself then).
	 */
	private boolean bridge(Match m, Vec3 to) {
		if (!bot.onGround() || find(BotBrain::isBuildBlock) < 0) return false;
		// a few blocks from where it is going, the gap is the destination itself (a goal to drop into), not a crossing
		if (Math.hypot(to.x - bot.getX(), to.z - bot.getZ()) < 4.5) return false;
		// a way to walk there (the path, a bridge someone built): walk it, building is for where there is none
		refreshRoute(to);
		if (path != null) return false;
		BlockPos stand = support();
		if (stand == null) return false;
		int y = stand.getY();
		ServerLevel level = level();
		Bridging.Floor floor = new Bridging.Floor() {
			@Override
			public boolean solid(int x, int z) {
				return BotActions.groundBelow(level, x + 0.5, y + 1, z + 0.5, 4);
			}

			@Override
			public boolean buildable(int x, int z) {
				return m.botMayBuild(new BlockPos(x, y, z));
			}
		};
		Bridging.Step st = Bridging.next(Bridging.line(stand.getX(), stand.getZ(), (int) Math.floor(to.x), (int) Math.floor(to.z), 48), floor);
		if (st == null || st.index() > 2) return false;
		building = true;
		bot.setSprinting(false);
		bot.setShiftKeyDown(true);
		BlockPos from = new BlockPos(st.fromX(), y, st.fromZ());
		bridgeAt = new BlockPos(st.x(), y, st.z());
		Vec3 face = new Vec3(st.fromX() + 0.5 + st.dx() * 0.5, y + 0.5, st.fromZ() + 0.5 + st.dz() * 0.5);
		lookAt(face, false);
		if (!from.equals(stand)) {
			// first onto the last floor block before the gap
			moveToward(new Vec3(st.fromX() + 0.5, bot.getY(), st.fromZ() + 0.5), SNEAK);
			return true;
		}
		double past = (bot.getX() - (st.fromX() + 0.5)) * st.dx() + (bot.getZ() - (st.fromZ() + 0.5)) * st.dz() - 0.5;
		// Out over the edge only as far as its feet still stand (0.3 each side; it stops at 0.18), and slowly enough
		// that the slide does not carry it further: then it never slips off, even where sneaking would not hold it
		Vec3 v = bot.getDeltaMovement();
		double out = v.x * st.dx() + v.z * st.dz();
		double coast = Math.max(0, out) * 1.2;
		if (past + coast < 0.18) {
			moveToward(new Vec3(face.x + st.dx() * 0.4, bot.getY(), face.z + st.dz() * 0.4), SNEAK * (float) Math.min(1, 0.3 + (0.18 - past - coast) * 3));
		} else if (out > 0.005) {
			brake();
		}
		if (ticks - lastPlace < Math.max(4, mind.difficulty().reactionTicks)) return true;
		select(BotBrain::isBuildBlock);
		// the click: what the bot really looks at, within reach
		double reach = bot.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE);
		Vec3 eye = bot.getEyePosition();
		net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(bot.getLookAngle().scale(reach)),
				net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, bot));
		net.minecraft.core.Direction side = st.dx() > 0 ? net.minecraft.core.Direction.EAST : st.dx() < 0 ? net.minecraft.core.Direction.WEST
				: st.dz() > 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH;
		if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || !hit.getBlockPos().equals(from) || hit.getDirection() != side) {
			return true;
		}
		BlockPos target = from.relative(side);
		bot.gameMode.useItemOn(bot, level, bot.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
		BotActions.swing(bot);
		lastPlace = ticks;
		if (!level.getBlockState(target).isAir()) placedBlocks++;
		return true;
	}

	/** How fast a sneaking player walks (the client slows the keys; for a bot that is done here). */
	private static final float SNEAK = 0.3f;

	/** The block holding the bot up: under its middle, or (hanging over an edge) under one of its corners. */
	private BlockPos support() {
		double y = bot.getY() - 0.5;
		BlockPos c = BlockPos.containing(bot.getX(), y, bot.getZ());
		if (!level().getBlockState(c).getCollisionShape(level(), c).isEmpty()) return c;
		for (double[] o : new double[][] {{0.29, 0.29}, {-0.29, 0.29}, {0.29, -0.29}, {-0.29, -0.29}}) {
			BlockPos p = BlockPos.containing(bot.getX() + o[0], y, bot.getZ() + o[1]);
			if (!level().getBlockState(p).getCollisionShape(level(), p).isEmpty()) return p;
		}
		return null;
	}

	/** Walks towards a point whichever way the bot looks (the keys, relative to its facing). */
	private void moveToward(Vec3 at, float speed) {
		double dx = at.x - bot.getX(), dz = at.z - bot.getZ();
		double d = Math.hypot(dx, dz);
		if (d < 0.05) return;
		double yaw = Math.toRadians(bot.getYRot());
		// forward is (-sin, cos) of the yaw, left (cos, sin)
		bot.zza = (float) ((-Math.sin(yaw) * dx + Math.cos(yaw) * dz) / d * speed);
		bot.xxa = (float) ((Math.cos(yaw) * dx + Math.sin(yaw) * dz) / d * speed);
	}

	/**
	 * Breaking through to the game's target block (an enemy bed): looking at what is really in the way (the bed, or a
	 * block a player put round it), within reach, for as long as mining it takes with what the bot holds (the
	 * player's formula); then the game's own rule for breaking decides.
	 */
	private void breakThrough(Match m) {
		BlockPos goal = m.botBreakTarget(bot);
		double reach = bot.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE);
		Vec3 eye = bot.getEyePosition();
		if (goal == null || eye.distanceTo(Vec3.atCenterOf(goal)) > reach + 0.5) {
			mining = null;
			return;
		}
		ServerLevel level = level();
		net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(eye, Vec3.atCenterOf(goal),
				net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, bot));
		BlockPos at = hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? hit.getBlockPos() : goal;
		BlockState st = level.getBlockState(at);
		boolean bed = st.getBlock() instanceof net.minecraft.world.level.block.BedBlock;
		if (!at.equals(goal) && !bed && !m.botMayBreak(at)) {
			// something of the map in the way: walking round it is the way
			mining = null;
			return;
		}
		lookAt(Vec3.atCenterOf(at), false);
		if (!at.equals(mining)) {
			mining = at;
			mineTicks = 0;
		}
		net.minecraft.world.phys.BlockHitResult aimed = level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(bot.getLookAngle().scale(reach)),
				net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, bot));
		if (aimed.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || !aimed.getBlockPos().equals(at)) return;
		mineTicks++;
		if (mineTicks % 4 == 1) BotActions.swing(bot);
		if (mineTicks < breakTicks(at, st)) return;
		if (m.onBreakBlock(bot, at, st)) level.destroyBlock(at, false, bot);
		mining = null;
	}

	// ------------------------------------------------------------------ showing an admin what the bot does

	/**
	 * For admins watching this bot ({@code /battlehub bots show}), as particles only they see: the route (end rods),
	 * where it is heading (green sparks), what it acts on (crits: the target, the block to dig, mine or build) and the
	 * objective (flames round it); its tactic and main reason above the hotbar.
	 */
	private void show(Match m) {
		List<ServerPlayer> watchers = Bots.watchers(bot);
		if (watchers.isEmpty()) return;
		List<Vec3> route = new java.util.ArrayList<>();
		List<Vec3> heading = new java.util.ArrayList<>();
		List<Vec3> acts = new java.util.ArrayList<>();
		List<Vec3> goal = new java.util.ArrayList<>();
		if (path != null) {
			for (int i = 0; i < path.size() && i < 16; i++) route.add(path.get(i).add(0, 0.2, 0));
		}
		if (lastIntent != null && lastIntent.moveTo() != null) heading.add(Perception.v(lastIntent.moveTo()).add(0, 0.3, 0));
		ServerPlayer t = target == null ? null : m.player(target);
		if (t != null) {
			Vec3 from = bot.getEyePosition(), to = t.getEyePosition();
			for (int i = 1; i <= 6; i++) acts.add(from.lerp(to, i / 7.0));
		}
		if (pkPlan != null) {
			heading.add(Perception.v(pkPlan.landing()).add(0, 0.2, 0));
			acts.add(Perception.v(pkPlan.takeoff()).add(0, 0.2, 0));
		}
		if (spleefPlan != null && spleefPlan.dig() != null) acts.add(new Vec3(spleefPlan.dig()[0] + 0.5, bot.getY() + 0.1, spleefPlan.dig()[1] + 0.5));
		if (spleefPlan != null && spleefPlan.stand() != null) heading.add(new Vec3(spleefPlan.stand()[0] + 0.5, bot.getY() + 0.3, spleefPlan.stand()[1] + 0.5));
		if (building && bridgeAt != null) acts.add(Vec3.atCenterOf(bridgeAt));
		if (mining != null) acts.add(Vec3.atCenterOf(mining));
		net.battlehub.core.ai.Objective g = m.botGoal(bot);
		if (g != null) {
			double r = Math.max(0.6, Math.min(4, g.radius()));
			for (int i = 0; i < 8; i++) {
				double a = i * Math.PI / 4;
				goal.add(new Vec3(g.point().x() + Math.cos(a) * r, g.point().y() + 0.3, g.point().z() + Math.sin(a) * r));
			}
		}
		CombatMind.Report r = mind.report();
		for (ServerPlayer w : watchers) {
			for (Vec3 p : route) BotActions.particle(w, net.minecraft.core.particles.ParticleTypes.END_ROD, p);
			for (Vec3 p : heading) BotActions.particle(w, net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, p);
			for (Vec3 p : acts) BotActions.particle(w, net.minecraft.core.particles.ParticleTypes.CRIT, p);
			for (Vec3 p : goal) BotActions.particle(w, net.minecraft.core.particles.ParticleTypes.FLAME, p);
			net.battlehub.util.Msg.actionBar(w, "bots.show.bar", bot.getGameProfile().name(), r.tactic().name().toLowerCase(java.util.Locale.ROOT),
					r.reason().isEmpty() ? "-" : r.reason());
		}
	}

	/** Ticks it takes a player to mine this block with what the bot holds (the game's formula). */
	private int breakTicks(BlockPos pos, BlockState st) {
		float hardness = st.getDestroySpeed(level(), pos);
		if (hardness < 0) return Integer.MAX_VALUE;
		if (hardness == 0) return 1;
		float speed = bot.getDestroySpeed(st);
		return (int) Math.ceil(hardness * (bot.hasCorrectToolForDrops(st) ? 30 : 100) / Math.max(0.01f, speed));
	}

	private boolean learnedUse;
	/** Items the model started to use (a bow drawn, something eaten, a throw), and hands it chose the bot could not take. */
	private int learnedUses, learnedMissing;

	int learnedUses() {
		return learnedUses;
	}

	int learnedMissing() {
		return learnedMissing;
	}

	/**
	 * What the learned model holds and does with the right mouse button: the weapon (with the shield up while held),
	 * the bow (drawn while held, loosed on letting go), food and golden apples (one press eats one), pearls and wind
	 * charges (thrown on pressing). Something it does not carry in the hotbar leaves the hand as it is.
	 */
	private void learnedHand(Match m, Intent in) {
		String hand = LearnedFight.handName(in.hand());
		boolean press = in.use() && !learnedUse;
		learnedUse = in.use();
		if (!hand.equals("weapon")) shield(false);
		boolean held;
		if (hand.equals("weapon")) {
			if (bot.isUsingItem() && bot.getUsedItemHand() == InteractionHand.MAIN_HAND) stopUsing();
			selectMelee(m.botWeapon());
			held = true;
		} else {
			held = select(LearnedFight.wants(hand));
			if (!held) learnedMissing++;
		}
		if (!held) return;
		boolean usingMain = bot.isUsingItem() && bot.getUsedItemHand() == InteractionHand.MAIN_HAND;
		switch (hand) {
			case "weapon" -> shield(in.use());
			case "bow" -> {
				if (in.use()) {
					if (!bot.isUsingItem()) {
						BotActions.use(bot, InteractionHand.MAIN_HAND);
						learnedUses++;
					}
				} else if (usingMain) {
					bot.releaseUsingItem();
					shots++;
				}
			}
			// one press eats the whole thing (as in the simulation); switching the hand stops it
			case "food", "gapple" -> {
				if (in.use() && !bot.isUsingItem()) {
					BotActions.use(bot, InteractionHand.MAIN_HAND);
					learnedUses++;
				}
			}
			default -> {
				if (press) {
					BotActions.use(bot, InteractionHand.MAIN_HAND);
					BotActions.swing(bot);
					learnedUses++;
				}
			}
		}
	}

	/** Raise or lower the shield in the off hand (the mind decides when). */
	private void shield(boolean up) {
		if (!bot.getOffhandItem().is(Items.SHIELD)) {
			return;
		}
		boolean blocking = bot.isUsingItem() && bot.getUsedItemHand() == InteractionHand.OFF_HAND;
		if (up && !blocking && !bot.isUsingItem()) {
			BotActions.use(bot, InteractionHand.OFF_HAND);
		} else if (!up && blocking) {
			bot.stopUsingItem();
		}
	}

	/** Draws and lets go of the bow (or charges and fires the crossbow) at a point, leading a moving target. */
	private void shoot(Vec3 at, Vec3 motion) {
		boolean crossbow;
		if (select(s -> s.is(Items.BOW))) {
			crossbow = false;
		} else if (select(s -> s.is(Items.CROSSBOW))) {
			crossbow = true;
		} else {
			return;
		}
		double speed = crossbow ? CROSSBOW_SPEED : BOW_SPEED;
		Vec3 eye = bot.getEyePosition();
		double h = Math.hypot(at.x - eye.x, at.z - eye.z);
		Vec3 aim = at.add(motion.x * BotMath.arrowTicks(h, speed), 0, motion.z * BotMath.arrowTicks(h, speed));
		double dx = aim.x - eye.x;
		double dz = aim.z - eye.z;
		float err = (float) skill.bowError;
		float yaw = BotMath.yaw(dx, dz) + (float) ((rnd.nextDouble() * 2 - 1) * err);
		float pitch = BotMath.arrowPitch(Math.sqrt(dx * dx + dz * dz), aim.y - eye.y, speed) + (float) ((rnd.nextDouble() * 2 - 1) * err);
		look(yaw, pitch);
		boolean aimed = Math.abs(BotMath.wrap(bot.getYRot() - yaw)) < 3 && Math.abs(bot.getXRot() - pitch) < 3;
		ItemStack held = bot.getMainHandItem();
		if (crossbow) {
			if (CrossbowItem.isCharged(held)) {
				if (aimed) {
					BotActions.use(bot, InteractionHand.MAIN_HAND);
					shots++;
				}
			} else if (!bot.isUsingItem()) {
				BotActions.use(bot, InteractionHand.MAIN_HAND);
			} else if (bot.getTicksUsingItem() >= 26) {
				bot.releaseUsingItem();
			}
			return;
		}
		if (!bot.isUsingItem()) {
			BotActions.use(bot, InteractionHand.MAIN_HAND);
		} else if (bot.getTicksUsingItem() >= 20 + (skill == BotLevel.EASY ? 10 : 0) && aimed) {
			bot.releaseUsingItem();
			shots++;
		}
	}

	private int shots;

	/** Arrows and bolts fired. */
	int shots() {
		return shots;
	}

	// ------------------------------------------------------------------ Spleef

	private SpleefPlanner.Plan spleefPlan;
	private int spleefAt = -100;
	private int lastDig = -100;
	private int lastThrow = -100;

	/**
	 * Spleef through the planner: the floor under the bot as a grid, the opponents it can see or locate on that
	 * floor; dig what the planner picks (looking at it, in reach and in sight, as fast as the level allows; the
	 * kit's shovel breaks snow at once, as for a player), stand where it says, and throw snowballs at someone near
	 * a hole.
	 */
	private void spleef(Match m) {
		fighting = true;
		BlockPos under = BlockPos.containing(bot.getX(), bot.getY() - 0.5, bot.getZ());
		ServerLevel level = level();
		if (spleefPlan == null || ticks - spleefAt >= mind.difficulty().thinkTicks * Bots.thinkStretch()) {
			int size = 25, x0 = under.getX() - 12, z0 = under.getZ() - 12;
			boolean[] present = new boolean[size * size];
			for (int k = 0; k < size; k++) {
				for (int i = 0; i < size; i++) {
					present[k * size + i] = level.getBlockState(new BlockPos(x0 + i, under.getY(), z0 + k)).is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK);
				}
			}
			List<SpleefPlanner.Foe> foes = new java.util.ArrayList<>();
			for (UUID id : m.participants()) {
				if (id.equals(bot.getUUID()) || !m.inPlay(id) || !m.isEnemy(bot.getUUID(), id)) continue;
				ServerPlayer p = m.player(id);
				if (p == null || p.level() != level || Math.abs(p.getY() - (under.getY() + 1)) > 1.5) continue;
				if (!bot.hasLineOfSight(p) && (p.isShiftKeyDown() || p.isInvisible())) continue;
				foes.add(new SpleefPlanner.Foe(p.getX(), p.getZ(), p.getDeltaMovement().x, p.getDeltaMovement().z));
			}
			double reach = bot.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE);
			spleefPlan = SpleefPlanner.plan(new SpleefPlanner.Floor(x0, z0, size, present), bot.getX(), bot.getZ(), foes, reach,
					mind.personality(), mind.difficulty());
			spleefAt = ticks;
			throwSnowball(m, foes);
		}
		SpleefPlanner.Plan plan = spleefPlan;
		if (plan.stand() != null) {
			goTo(new Vec3(plan.stand()[0] + 0.5, under.getY() + 1, plan.stand()[1] + 0.5), false);
		}
		if (plan.dig() != null && bot.isUsingItem() == false) {
			select(s -> s.is(ItemTags.SHOVELS));
			BlockPos target = new BlockPos(plan.dig()[0], under.getY(), plan.dig()[1]);
			Vec3 top = new Vec3(target.getX() + 0.5, target.getY() + 0.95, target.getZ() + 0.5);
			lookAt(top, false);
			Vec3 eye = bot.getEyePosition();
			float want = BotMath.yaw(top.x - eye.x, top.z - eye.z);
			boolean aimed = Math.abs(BotMath.wrap(bot.getYRot() - want)) < 12;
			BlockState st = level.getBlockState(target);
			if (aimed && st.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK) && ticks - lastDig >= Math.max(2, mind.difficulty().reactionTicks)
					&& eye.distanceTo(top) <= bot.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE) + 0.5
					&& sees(target, eye, top)) {
				m.onBreakBlock(bot, target, st);
				BotActions.swing(bot);
				lastDig = ticks;
				spleefPlan = null;
			}
		}
	}

	/** Nothing between the eye and the top of this block (the same check a player's click passes). */
	private boolean sees(BlockPos target, Vec3 eye, Vec3 point) {
		net.minecraft.world.phys.BlockHitResult hit = level().clip(new net.minecraft.world.level.ClipContext(eye, point,
				net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, bot));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
	}

	/** A snowball at someone standing near a hole (a push can finish them), now and then. */
	private void throwSnowball(Match m, List<SpleefPlanner.Foe> foes) {
		if (ticks - lastThrow < 30 - mind.difficulty().thinkTicks * 2 || foes.isEmpty() || find(s -> s.is(Items.SNOWBALL)) < 0) return;
		for (UUID id : m.participants()) {
			ServerPlayer p = m.player(id);
			if (p == null || id.equals(bot.getUUID()) || !m.inPlay(id) || !m.isEnemy(bot.getUUID(), id) || !bot.hasLineOfSight(p)) continue;
			double d = p.distanceTo(bot);
			if (d < 3 || d > 12) continue;
			// standing near a hole: a block within a step of them is gone
			BlockPos feet = BlockPos.containing(p.getX(), p.getY() - 0.5, p.getZ());
			boolean edge = false;
			for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				edge |= level().getBlockState(feet.relative(dir)).isAir();
			}
			if (!edge) continue;
			int slot = find(s -> s.is(Items.SNOWBALL));
			bot.getInventory().setSelectedSlot(slot);
			Vec3 at = p.position().add(p.getDeltaMovement().scale(Math.min(8, d / 1.5))).add(0, 1.0, 0);
			lookAt(at.add(0, d * 0.06, 0), true);
			BotActions.use(bot, InteractionHand.MAIN_HAND);
			lastThrow = ticks;
			return;
		}
	}

	// ------------------------------------------------------------------ TNT Run and Bow Spleef

	/** Keeps running over the floor that is left, now and then turning to where there is most of it. */
	private void runner(Match m, boolean shootFloor) {
		bot.setSprinting(skill.sprints || !shootFloor);
		float yaw = Float.isNaN(runYaw) ? bot.getYRot() : runYaw;
		if (ticks % 6 == 0 || !floorAhead(yaw, 1.2)) {
			yaw = bestRunDirection(yaw);
			runYaw = yaw;
		}
		if (shootFloor) {
			ServerPlayer t = target(m, 40);
			if (t != null && seen >= skill.reactionTicks && find(s -> s.is(Items.BOW)) >= 0) {
				// Shoot the floor under the nearest other player, moving slowly while aiming
				shoot(t.position().add(0, -0.6, 0), t.getDeltaMovement());
				bot.zza = 0.5f;
				bot.xxa = 0;
				if (!floorAhead(bot.getYRot(), 1.0)) {
					bot.zza = 0;
				}
				return;
			}
		}
		look(yaw, 10f);
		bot.zza = 1f;
		if (bot.onGround() && !floorAhead(yaw, 0.9) && floorAhead(yaw, 2.4)) {
			bot.setJumping(true);
		}
	}

	private boolean floorAhead(float yaw, double distance) {
		Vec3 p = bot.position().add(BotActions.dir(yaw).scale(distance));
		return BotActions.floorAt(level(), p.x, p.y, p.z);
	}

	/** Of sixteen directions, the one with the most floor ahead (a small preference for keeping course). */
	private float bestRunDirection(float current) {
		float best = current;
		double bestScore = -1;
		for (int i = 0; i < 16; i++) {
			float yaw = current + i * 22.5f;
			double score = 0;
			for (int d = 1; d <= 7; d++) {
				if (floorAhead(yaw, d)) {
					score += 1.0 / d + 0.2;
				} else if (d <= 2) {
					score -= 1.5;
				}
			}
			score -= Math.abs(BotMath.wrap(yaw - current)) / 360.0;
			score += rnd.nextDouble() * 0.2;
			if (score > bestScore) {
				bestScore = score;
				best = yaw;
			}
		}
		return BotMath.wrap(best);
	}

	// ------------------------------------------------------------------ parkour

	// Parkour: the planned jump and what failed before
	private JumpPlanner.Plan pkPlan;
	private int pkFrom = -2;
	private int pkTo = -2;
	/** 0: walking to the start of the run-up, 1: running up, 2: in the air. */
	private int pkStage;
	private int pkJumpLate;
	private final java.util.Map<String, java.util.Set<String>> pkFailed = new java.util.HashMap<>();
	private String pkNote = "";
	/** The ladder platform it is going up to, or -1. */
	private int pkLadder = -1;

	/**
	 * From platform to platform with player movement only: the planner ({@link JumpPlanner}) picks how to make the
	 * jump (walk or sprint, run-up length) by simulating it; the bot walks to the start of the run-up, runs, jumps
	 * on the tick the planner says (a little early or late at lower levels) and keeps pushing towards the landing
	 * in the air. A missed jump is remembered with the way it was tried, and the next try uses another way.
	 */
	private void parkour(Match m) {
		ParkourMatch pm = (ParkourMatch) m;
		List<ParkourCourse.Platform> course = pm.course();
		if (course == null || course.isEmpty()) {
			return;
		}
		int on = pm.platformIndex(bot);
		if (!bot.onGround()) {
			if (pkLadder >= 0 && pkLadder < course.size() && climb(pm.platformTop(pkLadder))) {
				return;
			}
			if (pkStage == 2 && pkPlan != null) {
				// steer in the air towards the landing (a player can, a little)
				look(BotMath.yaw(pkPlan.landing().x() - bot.getX(), pkPlan.landing().z() - bot.getZ()), 15f);
				bot.zza = 1f;
				bot.setSprinting(pkPlan.sprint());
			}
			return;
		}
		if (pkStage == 2) {
			// down again: on the target, or back where it came from (the course puts a fallen player on a checkpoint)
			if (on != pkTo && pkPlan != null) {
				pkFailed.computeIfAbsent(pkFrom + ">" + pkTo, k -> new java.util.HashSet<>()).add(pkPlan.variant());
				pkNote = "missed " + pkFrom + ">" + pkTo + " (" + pkPlan.variant() + ")";
			}
			pkStage = 0;
			pkPlan = null;
			pkWait = Math.max(2, mind.difficulty().reactionTicks + rnd.nextInt(Math.max(1, mind.difficulty().thinkTicks * 2)));
		}
		int from = on >= 0 ? on : Math.max(-1, pm.lastPlatform(bot.getUUID()));
		int next = from + 1;
		if (next >= course.size()) {
			bot.setSprinting(false);
			return;
		}
		boolean ladder = course.get(next).style() == ParkourCourse.Style.LADDER;
		pkLadder = ladder ? next : -1;
		if (from < 0 || ladder) {
			// the start, or a ladder: walk to it (into a ladder; on it, climb() takes over)
			Vec3 to = pm.platformTop(Math.max(0, next));
			if (ladder) pkNote = "to the ladder " + next;
			goTo(to, false);
			if (bot.horizontalCollision) bot.setJumping(true);
			return;
		}
		if (pkPlan == null || pkFrom != from || pkTo != next) {
			pkFrom = from;
			pkTo = next;
			pkStage = 0;
			String key = from + ">" + next;
			java.util.Set<String> failed = pkFailed.computeIfAbsent(key, k -> new java.util.HashSet<>());
			pkPlan = JumpPlanner.plan(pad(pm, from), pad(pm, next), failed);
			if (pkPlan == null && !failed.isEmpty()) {
				// every way failed once: start over rather than give up
				failed.clear();
				pkPlan = JumpPlanner.plan(pad(pm, from), pad(pm, next), failed);
			}
			pkNote = pkPlan == null ? "no way to make " + key : String.format(java.util.Locale.ROOT, "jump %s: %s, %.2f to spare", key, pkPlan.variant(), pkPlan.margin());
			// lower levels sometimes jump a tick early or late
			pkJumpLate = rnd.nextDouble() < mind.difficulty().mistakeRate ? (rnd.nextBoolean() ? 1 : -1) : 0;
		}
		if (pkPlan == null) {
			// not possible with a straight jump (should not happen on a generated course): walk towards it
			goTo(pm.platformTop(next), true);
			return;
		}
		if (pkWait-- > 0) {
			bot.setSprinting(false);
			brake();
			return;
		}
		Vec3 start = Perception.v(pkPlan.start());
		float yaw = BotMath.yaw(pkPlan.dirX(), pkPlan.dirZ()) + (float) ((rnd.nextDouble() * 2 - 1) * mind.difficulty().aimError * 0.2);
		if (pkStage == 0) {
			double d = Math.hypot(start.x - bot.getX(), start.z - bot.getZ());
			if (d > 0.3) {
				float toStart = BotMath.yaw(start.x - bot.getX(), start.z - bot.getZ());
				look(toStart, 0f);
				// turn before walking: on a one-block platform a step sideways is a fall
				bot.zza = Math.abs(BotMath.wrap(bot.getYRot() - toStart)) > 30 ? 0f : d > 0.8 ? 1f : 0.3f;
				bot.setSprinting(false);
				return;
			}
			if (bot.getDeltaMovement().horizontalDistanceSqr() > 0.002) {
				// stand still first: the plan starts from a stop
				brake();
				return;
			}
			pkStage = 1;
		}
		// running up: face the jump first, then jump on the tick whose step would carry the bot past the take-off point
		look(yaw, 0f);
		if (pkStage == 1 && bot.getDeltaMovement().horizontalDistanceSqr() < 0.002 && Math.abs(BotMath.wrap(bot.getYRot() - yaw)) > 5) {
			return;
		}
		bot.zza = 1f;
		bot.setSprinting(pkPlan.sprint());
		double along = (bot.getX() - start.x) * pkPlan.dirX() + (bot.getZ() - start.z) * pkPlan.dirZ();
		double takeoffAlong = (pkPlan.takeoff().x() - start.x) * pkPlan.dirX() + (pkPlan.takeoff().z() - start.z) * pkPlan.dirZ();
		Vec3 v = bot.getDeltaMovement();
		double step = (v.x * pkPlan.dirX() + v.z * pkPlan.dirZ()) + (pkPlan.sprint() ? 0.13 : 0.1) * 0.98;
		double late = pkJumpLate * step;
		if (along + step - late >= takeoffAlong) {
			bot.setJumping(true);
			pkStage = 2;
		}
	}

	/**
	 * On a ladder: pushing into it and holding jump climbs it (letting go slides down, and below a parkour ladder
	 * is the void), and at the top, still pushing forward steps onto the platform. False when not at the ladder.
	 */
	private boolean climb(Vec3 top) {
		double dx = top.x - bot.getX(), dz = top.z - bot.getZ();
		boolean near = dx * dx + dz * dz < 1.8 * 1.8 && bot.getY() < top.y + 0.5;
		if (!bot.onClimbable() && !near) return false;
		look(BotMath.yaw(dx, dz), 0f);
		bot.zza = 1f;
		bot.setSprinting(false);
		if (bot.onClimbable()) {
			bot.setJumping(true);
			pkNote = "climbing to " + pkLadder;
		}
		return true;
	}

	/**
	 * Holds the movement keys against the bot's own sliding (a player stops on ice the same way), only as hard as
	 * the slide needs: full keys against a slow slide would push it the other way, and back, and never let it stop.
	 */
	private void brake() {
		Vec3 v = bot.getDeltaMovement();
		double speed = v.horizontalDistance();
		if (speed < 0.03) return;
		double yaw = Math.toRadians(bot.getYRot());
		// forward is (-sin, cos) of the yaw, left (cos, sin); a walking key adds about 0.13 a tick on normal ground
		double k = Math.min(1, speed / 0.13) / speed;
		double forward = (-Math.sin(yaw) * v.x + Math.cos(yaw) * v.z) * k;
		double left = (Math.cos(yaw) * v.x + Math.sin(yaw) * v.z) * k;
		bot.zza = (float) -forward;
		bot.xxa = (float) -left;
	}

	/** A course platform as the planner sees it: its top in world blocks, how high one stands on it, its friction. */
	private static JumpPlanner.Pad pad(ParkourMatch pm, int index) {
		ParkourCourse.Platform pl = pm.course().get(index);
		Vec3 top = pm.platformTop(index);
		double r = pl.style() == ParkourCourse.Style.FENCE ? 0.1875 : 0.5 + pl.half();
		double friction = pl.style() == ParkourCourse.Style.ICE ? 0.98 : 0.6;
		return new JumpPlanner.Pad(top.x - r, top.z - r, top.x + r, top.z + r, top.y, friction);
	}

	/** What the parkour planner is doing (inspect). */
	String parkourNote() {
		return pkNote;
	}

	private void propHunt(Match m) {
		PropHuntMatch ph = (PropHuntMatch) m;
		UUID id = bot.getUUID();
		PropHuntMatch.Role role = ph.role(id);
		if (role == PropHuntMatch.Role.PROP) {
			hide(ph);
		} else if (role == PropHuntMatch.Role.HUNTER) {
			hunt(ph);
		}
	}

	private void hide(PropHuntMatch ph) {
		UUID id = bot.getUUID();
		if (ph.disguise(id) == null) {
			List<String> blocks = ph.disguiseList();
			if (!blocks.isEmpty()) {
				BlockState st = Ids.state(blocks.get(rnd.nextInt(blocks.size())));
				if (st != null) {
					ph.disguise(bot, st);
				}
			}
			hideSpot = null;
		}
		if (hideSpot == null) {
			Vec3 from = bot.position();
			for (int i = 0; i < 16 && hideSpot == null; i++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				double r = 5 + rnd.nextDouble() * 14;
				Vec3 c = from.add(Math.cos(a) * r, 0, Math.sin(a) * r);
				if (BotActions.groundBelow(level(), c.x, from.y + 1, c.z, 3)) {
					hideSpot = c;
				}
			}
			hideTicks = 160;
		}
		if (hideSpot != null && hideTicks-- > 0 && bot.position().distanceTo(hideSpot) > 0.8) {
			goTo(hideSpot, false);
			return;
		}
		// Standing still turns a prop solid; a hard bot runs when a hunter comes very close
		if (skill == BotLevel.HARD && ph.stage() == PropHuntMatch.Stage.HUNTING) {
			ServerPlayer hunter = target(ph, 3);
			if (hunter != null) {
				Vec3 away = bot.position().subtract(hunter.position()).normalize().scale(6);
				hideSpot = bot.position().add(away);
				hideTicks = 40;
			}
		}
	}

	private void hunt(PropHuntMatch ph) {
		if (ph.stage() != PropHuntMatch.Stage.HUNTING) {
			return;
		}
		select(BotBrain::isMelee);
		ServerPlayer prop = null;
		Vec3 propAt = null;
		double best = skill.noticeRange;
		for (UUID id : ph.props()) {
			Disguise d = ph.disguise(id);
			ServerPlayer p = ph.player(id);
			if (d == null || p == null || !ph.inPlay(id)) continue;
			double dist = d.position().distanceTo(bot.position());
			if (dist < best) {
				best = dist;
				prop = p;
				propAt = d.position();
			}
		}
		if (prop == null) {
			wanderAbout(bot.position(), 10);
			return;
		}
		Vec3 center = propAt.add(0, 0.5, 0);
		if (best > 2.6) {
			goTo(propAt, true);
			return;
		}
		lookAt(center, false);
		if (bot.getAttackStrengthScale(0.5f) >= mind.difficulty().readyToHit) {
			ph.hitProp(bot, prop);
			BotActions.swing(bot);
		}
	}
}
