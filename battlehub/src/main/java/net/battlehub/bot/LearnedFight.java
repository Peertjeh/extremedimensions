package net.battlehub.bot;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

import net.battlehub.config.ConfigManager;
import net.battlehub.core.ai.Intent;
import net.battlehub.core.ai.MeleeController;
import net.battlehub.core.ai.Situation;
import net.battlehub.core.ai.Tactic;
import net.battlehub.core.ai.Track;
import net.battlehub.core.ai.Weapon;
import net.battlehub.core.ml.CombatAction;
import net.battlehub.core.ml.CombatLevels;
import net.battlehub.core.ml.CombatObservation;
import net.battlehub.core.ml.CombatSpec;
import net.battlehub.core.ml.CombatState;
import net.battlehub.core.ml.Gear;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.util.Ids;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The learned fight of spec v3 (every melee mode): every decision (2 ticks) it builds the observation the v3
 * simulation (ml/pvp_sim2) was trained on from what the bot knows of the world (its kit and effects, what it carries,
 * what it sees of its target, two more enemies, its nearest team mate, what it remembers, a projectile coming at it,
 * the ground round it and behind its target) and from its last decisions' observations (the history, which starts
 * again after a pause: core.ml.ObsHistory), lets the model of the bot's level choose, and holds the choice: movement
 * and jump, the click on the decision's first tick, the turn spread over its ticks, and (with bots.learnedItems, for a
 * model that learned its items) what to hold and whether to keep the right mouse button down (BotBrain carries those
 * out: drawing and loosing the bow, eating, blocking, throwing a pearl or a wind charge). Otherwise the bot holds its
 * weapon and eats, shoots and throws by its own rules, as the scripted fight does. The model chooses with the action
 * masks (core.ml.CombatMasks): never something the bot does not carry.
 * <p>
 * The scripted mind still decides when to fight. With an objective (a bed, a goal, a hill) the model only fights
 * enemies within {@link #ENGAGE} blocks, and an enemy out of sight only within that distance (farther, the mind's
 * paths lead round cover); the objective, healing at a distance, edge safety and building stay scripted.
 */
final class LearnedFight implements MeleeController {
	/** With an objective the model fights only enemies this close. */
	static final double ENGAGE = 8.0;

	private final BotPlayer bot;
	private final Random rng;
	private CombatAction current = CombatAction.NOTHING;
	private int step;
	/** What it perceived of its target each tick, newest first (for the level's delay). */
	private final ArrayDeque<FightState.Seen> seen = new ArrayDeque<>();
	private UUID seenOf;
	/** The melee weapon last seen in each player's hand (what it will hit with). */
	private final Map<UUID, ItemStack> weaponOf = new HashMap<>();
	/** The observations of its last decisions (spec v3's history). */
	private net.battlehub.core.ml.ObsHistory history;
	private int decisions;
	/** Decisions with a team mate in the observation, and with earlier decisions in the history. */
	private int withAlly, withHistory;

	LearnedFight(BotPlayer bot) {
		this.bot = bot;
		this.rng = new Random(bot.getUUID().getMostSignificantBits() ^ System.nanoTime());
	}

	/** Decisions taken by the model. */
	int decisions() {
		return decisions;
	}

	/** Decisions with a team mate in the observation (inspect, self-test). */
	int withAlly() {
		return withAlly;
	}

	/** Decisions whose history came from earlier decisions, not the start of a fight (inspect, self-test). */
	int withHistory() {
		return withHistory;
	}

	/** The velocity of the target as the bot last took it in (per tick, as the game keeps it), or null (self-test). */
	double[] seenVelocity() {
		FightState.Seen o = seen.peekFirst();
		return o == null ? null : new double[] {o.vx(), o.vy(), o.vz()};
	}

	/** Whether the model fights in this match: on, the mode in bots.learnedModes, a melee kit, the models there. */
	static boolean active(Match m) {
		var cfg = ConfigManager.config().bots;
		if (m == null || !cfg.learnedCombat || !cfg.learnedModes.contains(m.mode.id())) return false;
		Weapon w = m.botWeapon();
		if (w == Weapon.CROSSBOW) return false;
		if (w == Weapon.MACE && !cfg.learnedModes.contains("mace")) return false;
		if (w == Weapon.BOW && !cfg.learnedModes.contains("bow")) return false;
		return CombatModels.V3.ready();
	}

	@Override
	public Intent act(Tactic tactic, Situation s, Track track) {
		Match m = Matches.of(bot.getUUID());
		if (!active(m) || s.target() == null) return null;
		if (s.goal() != null && s.distance() > ENGAGE) return null;
		// a smash it noticed coming down on it: the model never saw one (the simulation has no flight), the scripted
		// mind runs from under it (or raises the shield)
		if (s.threat() != null && s.threat().smash() != null) return null;
		// an enemy it does not see, and not near: the scripted mind finds the way round cover (the model learned on
		// open ground and walks into the wall); in sight or near, the model fights
		if (!s.target().visible() && s.distance() > ENGAGE) return null;
		CombatModels.Family fam = CombatModels.V3;
		CombatLevels.Level lv = fam.level(bot.difficulty);
		CombatModels.Model model = lv == null ? null : fam.model(lv.model());
		if (model == null) return null;
		java.util.Set<String> hands = handsOf(model);
		boolean items = !hands.isEmpty();
		// a bow the model did not learn stays the bot's own (the mind's shooting)
		if (tactic == Tactic.SHOOT && !hands.contains("bow")) return null;
		ServerPlayer opp = bot.level().getServer().getPlayerList().getPlayer(s.target().id());
		if (opp == null) return null;
		CombatSpec spec = fam.spec();
		remember(s.target(), opp);
		if (step % spec.decisionTicks == 0) {
			decide(spec, model, lv, m, s, opp, hands);
			step = 0;
		}
		boolean first = step == 0;
		step++;
		turn(spec, lv);
		// the model's hand and right button only with bots.learnedItems; else the bot keeps to its weapon and its own
		// rules for eating, the bow and wind charges, and the model only raises the shield (the right button with the
		// weapon in hand, as in the simulation, where choosing something it does not carry keeps the weapon)
		boolean block = !items && current.use() && blocksWith(handName(current.hand()));
		return new Intent(current.forward(), current.strafe(), current.sprint(), current.jump(), null, null, first && current.attack(), block,
				false, false, false, items ? current.hand() : -1, items && current.use());
	}

	// ------------------------------------------------------------------ what it knows

	private void remember(Situation.Target t, ServerPlayer opp) {
		if (!t.id().equals(seenOf)) {
			seen.clear();
			seenOf = t.id();
		}
		ItemStack held = opp.getMainHandItem();
		if (Gear.isWeapon(Ids.key(held.getItem())) && t.visible()) weaponOf.put(opp.getUUID(), held.copy());
		boolean using = opp.isUsingItem();
		seen.addFirst(new FightState.Seen(t.pos().x(), t.pos().y(), t.pos().z(), t.vel().x(), t.vel().y(), t.vel().z(), opp.getYRot(), opp.onGround(),
				opp.isSprinting(), t.swung(), opp.hurtTime, t.health(), t.maxHealth(), opp.getAbsorptionAmount(), t.visible(), handOf(held),
				using, using ? opp.getTicksUsingItem() : 0, opp.isBlocking()));
		while (seen.size() > 12) seen.removeLast();
	}

	private void decide(CombatSpec spec, CombatModels.Model model, CombatLevels.Level lv, Match m, Situation sit, ServerPlayer opp,
			java.util.Set<String> hands) {
		long t0 = System.nanoTime();
		FightState.Seen o = (FightState.Seen) seen.toArray()[Math.min(seen.size() - 1, Math.max(0, lv.extraDelay()))];
		CombatState st = FightState.build(spec, m, bot, bot.getDeltaMovement(), bot.getAttackStrengthScale(0.5f), opp, o,
				weaponOf.getOrDefault(opp.getUUID(), ItemStack.EMPTY));
		if (history == null) history = new net.battlehub.core.ml.ObsHistory(spec);
		long now = bot.level().getServer().getTickCount();
		float[][] past = history.past(now);
		float[] obs = CombatObservation.build(spec, st, past);
		history.add(now, obs);
		if (st.allyPresent) withAlly++;
		if (past != null) withHistory++;
		float[] logits = model.logits(obs);
		long tm = System.nanoTime();
		boolean[] mask = CombatModels.V3.masks() == null ? null : CombatModels.V3.masks().mask(obs);
		if (mask != null && !hands.isEmpty()) {
			// a hand the model did not learn is left to the bot's own rules: the model does not choose it
			int at = handOffset(spec);
			for (int k = 1; k < spec.hands.size(); k++) {
				if (!hands.contains(spec.hands.get(k))) mask[at + k] = false;
			}
		}
		long maskNanos = System.nanoTime() - tm;
		int[] idx = lv.sample() ? CombatAction.sample(spec, logits, 1.0, rng, mask) : CombatAction.best(spec, logits, mask);
		CombatModels.V3.timer.record(System.nanoTime() - t0, maskNanos);
		bot.brain.decided(CombatModels.V3, obs, idx);
		current = CombatAction.decode(spec, idx);
		decisions++;
	}

	/** The hands the bot uses as its model chooses: with bots.learnedItems, those the model learned (else none). */
	static java.util.Set<String> handsOf(CombatModels.Model model) {
		if (!ConfigManager.config().bots.learnedItems || model == null) return java.util.Set.of();
		return model.items(CombatModels.V3.spec());
	}

	/** Where the hand head's logits start. */
	private static int handOffset(CombatSpec spec) {
		int[] heads = spec.actionHeads();
		int at = 0;
		for (int h = 0; h < spec.actionNames.indexOf("hand"); h++) at += heads[h];
		return at;
	}

	/** What a learned decision costs, over all bots (inspect, self-test). */
	static String timing() {
		return CombatModels.V3.timer.summary();
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

	// ------------------------------------------------------------------ items

	static final net.minecraft.world.item.Item WIND = Ids.item("minecraft:wind_charge");

	/** What kind of thing a stack is, in the spec's hands: bow, gapple, pearl, wind, food, or weapon (anything else). */
	static String handOf(ItemStack s) {
		if (s.isEmpty()) return "weapon";
		if (s.is(Items.BOW)) return "bow";
		if (s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE)) return "gapple";
		if (s.is(Items.ENDER_PEARL)) return "pearl";
		if (s.is(WIND)) return "wind";
		if (s.has(DataComponents.FOOD)) return "food";
		return "weapon";
	}

	/** The right button with this hand chosen means the shield: the weapon, or something not in the hotbar. */
	private boolean blocksWith(String hand) {
		if (hand.equals("weapon")) return true;
		Predicate<ItemStack> p = wants(hand);
		for (int i = 0; i < 9; i++) {
			if (p.test(bot.getInventory().getItem(i))) return false;
		}
		return true;
	}

	/** The spec's name of a hand index (weapon when unknown). */
	static String handName(int index) {
		CombatSpec spec = CombatModels.V3.spec();
		return spec == null || index < 0 || index >= spec.hands.size() ? "weapon" : spec.hands.get(index);
	}

	/** What to select in the hotbar for a hand; null for the weapon (the bot's own choice of melee weapon). */
	static Predicate<ItemStack> wants(String hand) {
		return switch (hand) {
			case "weapon" -> null;
			default -> s -> !s.isEmpty() && handOf(s).equals(hand);
		};
	}
}
