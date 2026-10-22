package net.battlehub.bot;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.core.ai.Intent;
import net.battlehub.core.ai.MeleeController;
import net.battlehub.core.ai.Senses;
import net.battlehub.core.ai.Situation;
import net.battlehub.core.ai.Tactic;
import net.battlehub.core.ai.Track;
import net.battlehub.core.ai.V3;
import net.battlehub.core.ai.Weapon;
import net.battlehub.core.ml.CombatAction;
import net.battlehub.core.ml.CombatLevels;
import net.battlehub.core.ml.CombatObservation;
import net.battlehub.core.ml.CombatSpec;
import net.battlehub.core.ml.CombatState;
import net.battlehub.core.ml.Gear;
import net.battlehub.core.ml.TerrainRays;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.util.Ids;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The learned fight of spec v2 (every melee mode): every decision (2 ticks) it builds the observation the v2
 * simulation (ml/pvp_sim2) was trained on from what the bot knows of the world (its kit and effects, what it carries,
 * what it sees of its target, a second enemy, a projectile coming at it, the ground round it and behind its target),
 * lets the model of the bot's level choose, and holds the choice: movement and jump, the click on the decision's first
 * tick, the turn spread over its ticks, and (with bots.learnedItems) what to hold and whether to keep the right mouse
 * button down (BotBrain carries those out: drawing and loosing the bow, eating, blocking, throwing a pearl or a wind
 * charge). Without it the bot holds its weapon and eats, shoots and throws by its own rules, as the scripted fight
 * does.
 * <p>
 * The scripted mind still decides when to fight. With an objective (a bed, a goal, a hill) the model only fights
 * enemies within {@link #ENGAGE} blocks; the objective, healing at a distance, edge safety and building stay scripted.
 */
final class LearnedFight implements MeleeController {
	/** With an objective the model fights only enemies this close. */
	static final double ENGAGE = 8.0;

	private final BotPlayer bot;
	private final Random rng;
	private CombatAction current = CombatAction.NOTHING;
	private int step;
	/** What it perceived of its target each tick, newest first (for the level's delay). */
	private final ArrayDeque<Seen> seen = new ArrayDeque<>();
	private UUID seenOf;
	/** The melee weapon last seen in each player's hand (what it will hit with). */
	private final Map<UUID, ItemStack> weaponOf = new HashMap<>();
	private int decisions;

	private record Seen(double x, double y, double z, double vx, double vy, double vz, double yaw, boolean ground, boolean sprint, int swung,
			int hurt, double health, double maxHealth, double absorption, boolean visible, String hand, boolean using, int useTicks,
			boolean blocking) {}

	LearnedFight(BotPlayer bot) {
		this.bot = bot;
		this.rng = new Random(bot.getUUID().getMostSignificantBits() ^ System.nanoTime());
	}

	/** Decisions taken by the model. */
	int decisions() {
		return decisions;
	}

	/**
	 * Whether the v2 model fights in this match: on, the mode in bots.learnedModes and not left to v1
	 * (bots.learnedV1Modes), a melee kit, the models there.
	 */
	static boolean active(Match m) {
		var cfg = ConfigManager.config().bots;
		if (m == null || !cfg.learnedCombat || cfg.learnedSpec < 2 || !cfg.learnedModes.contains(m.mode.id())) return false;
		if (cfg.learnedV1Modes.contains(m.mode.id()) && LearnedMelee.MODES.contains(m.mode.id())) return false;
		Weapon w = m.botWeapon();
		if (w == Weapon.CROSSBOW) return false;
		if (w == Weapon.MACE && !cfg.learnedModes.contains("mace")) return false;
		if (w == Weapon.BOW && !cfg.learnedModes.contains("bow")) return false;
		return CombatModels.V2.ready();
	}

	@Override
	public Intent act(Tactic tactic, Situation s, Track track) {
		Match m = Matches.of(bot.getUUID());
		if (!active(m) || s.target() == null) return null;
		if (s.goal() != null && s.distance() > ENGAGE) return null;
		boolean items = ConfigManager.config().bots.learnedItems;
		// without the model's items the bow stays the bot's own (the mind's shooting)
		if (tactic == Tactic.SHOOT && !items) return null;
		ServerPlayer opp = bot.level().getServer().getPlayerList().getPlayer(s.target().id());
		if (opp == null) return null;
		CombatModels.Family fam = CombatModels.V2;
		CombatLevels.Level lv = fam.level(bot.difficulty);
		CombatModels.Model model = lv == null ? null : fam.model(lv.model());
		if (model == null) return null;
		CombatSpec spec = fam.spec();
		remember(s.target(), opp);
		if (step % spec.decisionTicks == 0) {
			decide(spec, model, lv, m, s, opp);
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
		seen.addFirst(new Seen(t.pos().x(), t.pos().y(), t.pos().z(), t.vel().x(), t.vel().y(), t.vel().z(), opp.getYRot(), opp.onGround(),
				opp.isSprinting(), t.swung(), opp.hurtTime, t.health(), t.maxHealth(), opp.getAbsorptionAmount(), t.visible(), handOf(held),
				using, using ? opp.getTicksUsingItem() : 0, opp.isBlocking()));
		while (seen.size() > 12) seen.removeLast();
	}

	private void decide(CombatSpec spec, CombatModels.Model model, CombatLevels.Level lv, Match m, Situation sit, ServerPlayer opp) {
		CombatState st = new CombatState();
		self(st, m);
		Seen o = (Seen) seen.toArray()[Math.min(seen.size() - 1, Math.max(0, lv.extraDelay()))];
		st.oppX = o.x;
		st.oppY = o.y;
		st.oppZ = o.z;
		st.oppVx = o.vx;
		st.oppVy = o.vy;
		st.oppVz = o.vz;
		st.oppYaw = o.yaw;
		st.oppOnGround = o.ground;
		st.oppSprinting = o.sprint;
		st.oppSinceSwing = o.swung;
		st.oppHurtTicks = o.hurt;
		st.oppHealth = o.health;
		st.oppMaxHealth = o.maxHealth;
		st.oppAbsorption = o.absorption;
		st.oppVisible = o.visible;
		st.oppBlocking = o.blocking;
		st.oppDraw = o.using && o.hand.equals("bow") ? o.useTicks : 0;
		st.oppEating = o.using && (o.hand.equals("food") || o.hand.equals("gapple"));
		st.oppHoldsBow = o.hand.equals("bow");
		st.oppHoldsFood = o.hand.equals("food") || o.hand.equals("gapple");
		st.oppHoldsThrowable = o.hand.equals("pearl") || o.hand.equals("wind");
		ItemStack ow = weaponOf.getOrDefault(opp.getUUID(), ItemStack.EMPTY);
		Gear.Melee om = Gear.melee(Ids.key(ow.getItem()));
		st.oppMeleeDamage = Gear.fullDamage(om, level(opp, "minecraft:strength"), level(opp, "minecraft:weakness"));
		st.oppMeleeBonus = Gear.sharpnessBonus(enchant(ow, "minecraft:sharpness"));
		st.oppCooldown = om.cooldown();
		st.oppKb = enchant(ow, "minecraft:knockback");
		st.oppArmor = opp.getArmorValue();
		st.oppToughness = opp.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		st.oppResistance = level(opp, "minecraft:resistance");
		st.oppScale = opp.getScale();
		st.damageOn = m.mode != GameMode.SUMO && m.mode != GameMode.BOXING && st.oppResistance < 5 && st.selfResistance < 5;
		others(st, spec, m, opp);
		threat(st, spec, m);
		WorldColumns cols = new WorldColumns(bot.level(), spec);
		double[][] rays = TerrainRays.rays(spec, cols, bot.getX(), bot.getY(), bot.getZ(), bot.getYRot());
		System.arraycopy(rays[0], 0, st.voids, 0, 8);
		System.arraycopy(rays[1], 0, st.drops, 0, 8);
		System.arraycopy(rays[2], 0, st.steps, 0, 8);
		System.arraycopy(rays[3], 0, st.walls, 0, 8);
		st.selfBelow = TerrainRays.below(spec, cols, bot.getX(), bot.getY(), bot.getZ());
		st.oppBelow = TerrainRays.below(spec, cols, o.x, o.y, o.z);
		double dx = o.x - bot.getX(), dz = o.z - bot.getZ(), ln = Math.max(1e-9, Math.hypot(dx, dz));
		double[] behind = TerrainRays.behind(spec, cols, o.x, o.y, o.z, dx / ln, dz / ln);
		st.oppDropBehind = behind[0];
		st.oppVoidBehind = behind[1];
		float[] logits = model.logits(CombatObservation.build(spec, st));
		int[] idx = lv.sample() ? CombatAction.sample(spec, logits, 1.0, rng) : CombatAction.best(spec, logits);
		current = CombatAction.decode(spec, idx);
		decisions++;
	}

	private void self(CombatState st, Match m) {
		Vec3 v = bot.getDeltaMovement();
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
		st.selfAbsorption = bot.getAbsorptionAmount();
		st.selfFood = bot.getFoodData().getFoodLevel();
		st.selfSaturation = bot.getFoodData().getSaturationLevel();
		st.selfRegen = Gear.regenRate(bot.getHealth(), bot.getMaxHealth(), bot.getFoodData().getFoodLevel(), bot.getFoodData().getSaturationLevel(),
				level(bot, "minecraft:regeneration"));
		st.selfFall = bot.fallDistance;
		st.selfScale = bot.getScale();
		st.selfReach = bot.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		ItemStack w = weapon(m.botWeapon());
		Gear.Melee g = Gear.melee(Ids.key(w.getItem()));
		st.selfMeleeDamage = Gear.fullDamage(g, level(bot, "minecraft:strength"), level(bot, "minecraft:weakness"));
		st.selfMeleeBonus = Gear.sharpnessBonus(enchant(w, "minecraft:sharpness"));
		st.selfCooldown = g.cooldown();
		st.selfKb = enchant(w, "minecraft:knockback");
		st.selfArmor = bot.getArmorValue();
		st.selfToughness = bot.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		st.selfResistance = level(bot, "minecraft:resistance");
		st.selfWalkSpeed = bot.getAttributeValue(Attributes.MOVEMENT_SPEED) / (bot.isSprinting() ? 1.3 : 1.0);
		st.selfJumpPower = bot.getAttributeValue(Attributes.JUMP_STRENGTH) + 0.1 * level(bot, "minecraft:jump_boost");
		st.selfGravity = bot.getAttributeValue(Attributes.GRAVITY);
		Inventory inv = bot.getInventory();
		boolean bow = count(inv, s -> s.is(Items.BOW)) > 0;
		st.selfArrows = bow ? count(inv, s -> s.is(Items.ARROW) || s.is(Items.SPECTRAL_ARROW) || s.is(Items.TIPPED_ARROW)) : 0;
		st.selfFoodItems = count(inv, s -> handOf(s).equals("food"));
		st.selfGapples = count(inv, s -> handOf(s).equals("gapple"));
		st.selfPearls = count(inv, s -> s.is(Items.ENDER_PEARL));
		st.selfWinds = count(inv, s -> s.is(WIND));
		st.selfShield = bot.getOffhandItem().is(Items.SHIELD);
		st.selfPearlCd = bot.getCooldowns().getCooldownPercent(new ItemStack(Items.ENDER_PEARL), 0f) * 20;
		st.selfWindCd = bot.getCooldowns().getCooldownPercent(new ItemStack(WIND), 0f) * 10;
		st.selfShieldCd = bot.getCooldowns().getCooldownPercent(new ItemStack(Items.SHIELD), 0f) * 100;
		CombatSpec spec = CombatModels.V2.spec();
		st.selfHand = Math.max(0, spec.hands.indexOf(handOf(bot.getMainHandItem())));
		st.selfUseTicks = bot.isUsingItem() ? bot.getTicksUsingItem() : 0;
		st.selfBlocking = bot.isBlocking();
	}

	/** The nearest other enemy it sees or hears, and how many enemies are near. */
	private void others(CombatState st, CombatSpec spec, Match m, ServerPlayer target) {
		V3 eye = new V3(bot.getX(), bot.getEyeY(), bot.getZ());
		ServerPlayer best = null;
		double bestD = Double.MAX_VALUE;
		boolean bestSeen = false;
		int near = 0;
		for (UUID id : m.participants()) {
			if (id.equals(bot.getUUID()) || !m.inPlay(id) || !m.isEnemy(bot.getUUID(), id)) continue;
			ServerPlayer p = m.player(id);
			if (p == null || p.level() != bot.level() || p.isSpectator()) continue;
			double d = Math.hypot(p.getX() - bot.getX(), p.getZ() - bot.getZ());
			boolean onScreen = Senses.inView(eye, bot.getYRot(), bot.getXRot(), new V3(p.getX(), p.getEyeY(), p.getZ()))
					|| Senses.inView(eye, bot.getYRot(), bot.getXRot(), new V3(p.getX(), p.getY() + 0.9, p.getZ()))
					|| Senses.inView(eye, bot.getYRot(), bot.getXRot(), new V3(p.getX(), p.getY(), p.getZ()));
			boolean visible = onScreen && !p.isInvisible() && bot.hasLineOfSight(p);
			boolean heard = d <= 12 && Math.hypot(p.getDeltaMovement().x, p.getDeltaMovement().z) > 0.01 && !p.isShiftKeyDown();
			if (!visible && !heard && !id.equals(target.getUUID())) continue;
			if (d <= spec.nearEnemies) near++;
			if (id.equals(target.getUUID())) continue;
			if (d < bestD) {
				best = p;
				bestD = d;
				bestSeen = visible;
			}
		}
		st.enemiesNear = near;
		if (best != null) {
			st.e2Present = true;
			st.e2X = best.getX();
			st.e2Y = best.getY();
			st.e2Z = best.getZ();
			st.e2Yaw = best.getYRot();
			st.e2Visible = bestSeen;
			st.e2SinceSwing = 100;
			st.e2HoldsBow = best.getMainHandItem().is(Items.BOW);
		}
	}

	/** The enemy projectile that comes closest soonest (passing within 1.5 blocks of the chest within 2 seconds). */
	private void threat(CombatState st, CombatSpec spec, Match m) {
		double cy = bot.getY() + spec.chestHeight * bot.getScale();
		double bestT = Double.MAX_VALUE;
		for (Projectile p : bot.level().getEntitiesOfClass(Projectile.class, bot.getBoundingBox().inflate(40))) {
			if (!(p.getOwner() instanceof ServerPlayer owner) || owner == bot || !m.isEnemy(bot.getUUID(), owner.getUUID())) continue;
			Vec3 v = p.getDeltaMovement();
			double px = p.getX() - bot.getX(), py = p.getY() - cy, pz = p.getZ() - bot.getZ();
			double vv = v.lengthSqr();
			if (vv < 1e-9) continue;
			double t = -(px * v.x + py * v.y + pz * v.z) / vv;
			if (t <= 0 || t > 40) continue;
			double cx = px + v.x * t, cyy = py + v.y * t, cz = pz + v.z * t;
			if (Math.sqrt(cx * cx + cyy * cyy + cz * cz) >= 1.5 || t >= bestT) continue;
			bestT = t;
			st.threatPresent = true;
			st.threatX = p.getX();
			st.threatY = p.getY();
			st.threatZ = p.getZ();
			st.threatVx = v.x;
			st.threatVy = v.y;
			st.threatVz = v.z;
		}
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
		CombatSpec spec = CombatModels.V2.spec();
		return spec == null || index < 0 || index >= spec.hands.size() ? "weapon" : spec.hands.get(index);
	}

	/** What to select in the hotbar for a hand; null for the weapon (the bot's own choice of melee weapon). */
	static Predicate<ItemStack> wants(String hand) {
		return switch (hand) {
			case "weapon" -> null;
			default -> s -> !s.isEmpty() && handOf(s).equals(hand);
		};
	}

	/** The melee weapon it would hold: the fight's weapon first, else the first weapon in the hotbar. */
	private ItemStack weapon(Weapon focus) {
		Inventory inv = bot.getInventory();
		ItemStack any = ItemStack.EMPTY;
		for (int i = 0; i < 9; i++) {
			ItemStack s = inv.getItem(i);
			String id = Ids.key(s.getItem());
			if (!Gear.isWeapon(id)) continue;
			Gear.Melee g = Gear.melee(id);
			boolean fits = switch (focus) {
				case MACE -> g.mace();
				case AXE -> g.axe();
				case SWORD -> id.endsWith("_sword");
				default -> false;
			};
			if (fits) return s;
			if (any.isEmpty()) any = s;
		}
		return any;
	}

	private static int count(Inventory inv, Predicate<ItemStack> p) {
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && p.test(s)) n += s.getCount();
		}
		return n;
	}

	/** The level (amplifier + 1) of an effect, 0 without it. */
	private static int level(LivingEntity e, String effect) {
		return Ids.effect(effect).map(h -> {
			MobEffectInstance i = e.getEffect(h);
			return i == null ? 0 : i.getAmplifier() + 1;
		}).orElse(0);
	}

	private int enchant(ItemStack s, String enchantment) {
		if (s.isEmpty()) return 0;
		return Ids.enchantment(bot.level().getServer(), enchantment).map(h -> s.getEnchantments().getLevel(h)).orElse(0);
	}
}
