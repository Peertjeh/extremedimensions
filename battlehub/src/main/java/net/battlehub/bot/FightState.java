package net.battlehub.bot;

import java.util.UUID;
import java.util.function.Predicate;

import net.battlehub.core.GameMode;
import net.battlehub.core.ai.Senses;
import net.battlehub.core.ai.V3;
import net.battlehub.core.ai.Weapon;
import net.battlehub.core.ml.CombatSpec;
import net.battlehub.core.ml.CombatState;
import net.battlehub.core.ml.Gear;
import net.battlehub.core.ml.TerrainRays;
import net.battlehub.match.Match;
import net.battlehub.util.Ids;
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
 * The combat state of spec v3 (core.ml.CombatState, the simulation's state) of one fighter against its target, from
 * the world: its kit and effects, what it carries, cooldowns and what it holds; what it saw of its target; the two
 * nearest other enemies it sees or hears, its nearest team mate and a projectile coming at it; what it remembers
 * (CombatMemory: since it saw its target, since it was hurt, since it hurt its target); the ground round it and behind
 * its target. The learned fight decides on it (LearnedFight), the fight logs record it (FightRecorder): the same for a
 * bot as for a person.
 */
final class FightState {
	private FightState() {}

	/** What a fighter saw of its target at one moment (position and velocity as it located it; the rest as it saw it). */
	record Seen(double x, double y, double z, double vx, double vy, double vz, double yaw, boolean ground, boolean sprint, int swung,
			int hurt, double health, double maxHealth, double absorption, boolean visible, String hand, boolean using, int useTicks,
			boolean blocking) {
		/** The target as it is: where it is, how fast (per tick), ticks since it swung, whether it is seen. */
		static Seen of(ServerPlayer opp, Vec3 pos, Vec3 vel, int swung, boolean visible) {
			boolean using = opp.isUsingItem();
			return new Seen(pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, opp.getYRot(), opp.onGround(), opp.isSprinting(), swung, opp.hurtTime,
					opp.getHealth(), opp.getMaxHealth(), opp.getAbsorptionAmount(), visible, LearnedFight.handOf(opp.getMainHandItem()), using,
					using ? opp.getTicksUsingItem() : 0, opp.isBlocking());
		}
	}

	/**
	 * The state of {@code self} against {@code opp} as seen in {@code o}.
	 *
	 * @param selfVel    its velocity (per tick)
	 * @param selfCharge its attack charge, 0..1
	 * @param oppWeapon  the melee weapon last seen in the target's hand (what it will hit with)
	 */
	static CombatState build(CombatSpec spec, Match m, ServerPlayer self, Vec3 selfVel, double selfCharge, ServerPlayer opp, Seen o,
			ItemStack oppWeapon) {
		CombatState st = new CombatState();
		self(st, spec, m, self, selfVel, selfCharge);
		st.oppX = o.x();
		st.oppY = o.y();
		st.oppZ = o.z();
		st.oppVx = o.vx();
		st.oppVy = o.vy();
		st.oppVz = o.vz();
		st.oppYaw = o.yaw();
		st.oppOnGround = o.ground();
		st.oppSprinting = o.sprint();
		st.oppSinceSwing = o.swung();
		st.oppHurtTicks = o.hurt();
		st.oppHealth = o.health();
		st.oppMaxHealth = o.maxHealth();
		st.oppAbsorption = o.absorption();
		st.oppVisible = o.visible();
		st.oppBlocking = o.blocking();
		st.oppDraw = o.using() && o.hand().equals("bow") ? o.useTicks() : 0;
		st.oppEating = o.using() && (o.hand().equals("food") || o.hand().equals("gapple"));
		st.oppHoldsBow = o.hand().equals("bow");
		st.oppHoldsFood = o.hand().equals("food") || o.hand().equals("gapple");
		st.oppHoldsThrowable = o.hand().equals("pearl") || o.hand().equals("wind");
		Gear.Melee om = Gear.melee(Ids.key(oppWeapon.getItem()));
		st.oppMeleeDamage = Gear.fullDamage(om, level(opp, "minecraft:strength"), level(opp, "minecraft:weakness"));
		st.oppMeleeBonus = Gear.sharpnessBonus(enchant(self, oppWeapon, "minecraft:sharpness"));
		st.oppCooldown = om.cooldown();
		st.oppKb = enchant(self, oppWeapon, "minecraft:knockback");
		st.oppArmor = opp.getArmorValue();
		st.oppToughness = opp.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		st.oppResistance = level(opp, "minecraft:resistance");
		st.oppScale = opp.getScale();
		st.damageOn = m.mode != GameMode.SUMO && m.mode != GameMode.BOXING && st.oppResistance < 5 && st.selfResistance < 5;
		others(st, spec, m, self, opp);
		allies(st, spec, m, self, opp);
		st.oppUnseen = o.visible() ? 0 : CombatMemory.unseen(self.getUUID(), opp.getUUID());
		st.selfSinceHit = CombatMemory.sinceHurt(self.getUUID());
		st.oppSinceHit = CombatMemory.sinceHit(self.getUUID(), opp.getUUID());
		threat(st, spec, m, self);
		WorldColumns cols = new WorldColumns(self.level(), spec);
		double[][] rays = TerrainRays.rays(spec, cols, self.getX(), self.getY(), self.getZ(), self.getYRot());
		System.arraycopy(rays[0], 0, st.voids, 0, 8);
		System.arraycopy(rays[1], 0, st.drops, 0, 8);
		System.arraycopy(rays[2], 0, st.steps, 0, 8);
		System.arraycopy(rays[3], 0, st.walls, 0, 8);
		st.selfBelow = TerrainRays.below(spec, cols, self.getX(), self.getY(), self.getZ());
		st.oppBelow = TerrainRays.below(spec, cols, o.x(), o.y(), o.z());
		double dx = o.x() - self.getX(), dz = o.z() - self.getZ(), ln = Math.max(1e-9, Math.hypot(dx, dz));
		double[] behind = TerrainRays.behind(spec, cols, o.x(), o.y(), o.z(), dx / ln, dz / ln);
		st.oppDropBehind = behind[0];
		st.oppVoidBehind = behind[1];
		return st;
	}

	private static void self(CombatState st, CombatSpec spec, Match m, ServerPlayer p, Vec3 v, double charge) {
		st.selfX = p.getX();
		st.selfY = p.getY();
		st.selfZ = p.getZ();
		st.selfVx = v.x;
		st.selfVy = v.y;
		st.selfVz = v.z;
		st.selfYaw = p.getYRot();
		st.selfPitch = p.getXRot();
		st.selfOnGround = p.onGround();
		st.selfSprinting = p.isSprinting();
		st.selfCharge = charge;
		st.selfHurtTicks = p.hurtTime;
		st.selfHealth = p.getHealth();
		st.selfMaxHealth = p.getMaxHealth();
		st.selfAbsorption = p.getAbsorptionAmount();
		st.selfFood = p.getFoodData().getFoodLevel();
		st.selfSaturation = p.getFoodData().getSaturationLevel();
		st.selfRegen = Gear.regenRate(p.getHealth(), p.getMaxHealth(), p.getFoodData().getFoodLevel(), p.getFoodData().getSaturationLevel(),
				level(p, "minecraft:regeneration"));
		st.selfFall = p.fallDistance;
		st.selfScale = p.getScale();
		st.selfReach = p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		ItemStack w = weapon(p, m.botWeapon());
		Gear.Melee g = Gear.melee(Ids.key(w.getItem()));
		st.selfMeleeDamage = Gear.fullDamage(g, level(p, "minecraft:strength"), level(p, "minecraft:weakness"));
		st.selfMeleeBonus = Gear.sharpnessBonus(enchant(p, w, "minecraft:sharpness"));
		st.selfCooldown = g.cooldown();
		st.selfKb = enchant(p, w, "minecraft:knockback");
		st.selfArmor = p.getArmorValue();
		st.selfToughness = p.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		st.selfResistance = level(p, "minecraft:resistance");
		st.selfWalkSpeed = p.getAttributeValue(Attributes.MOVEMENT_SPEED) / (p.isSprinting() ? 1.3 : 1.0);
		st.selfJumpPower = p.getAttributeValue(Attributes.JUMP_STRENGTH) + 0.1 * level(p, "minecraft:jump_boost");
		st.selfGravity = p.getAttributeValue(Attributes.GRAVITY);
		Inventory inv = p.getInventory();
		// what it can switch to: the hotbar (the arrows of a bow in the hotbar may be anywhere), so that the action masks
		// allow only what the bot can take in hand
		boolean bow = hotbar(inv, s -> s.is(Items.BOW)) > 0;
		st.selfArrows = bow ? count(inv, s -> s.is(Items.ARROW) || s.is(Items.SPECTRAL_ARROW) || s.is(Items.TIPPED_ARROW)) : 0;
		st.selfFoodItems = hotbar(inv, s -> LearnedFight.handOf(s).equals("food"));
		st.selfGapples = hotbar(inv, s -> LearnedFight.handOf(s).equals("gapple"));
		st.selfPearls = hotbar(inv, s -> s.is(Items.ENDER_PEARL));
		st.selfWinds = hotbar(inv, s -> s.is(LearnedFight.WIND));
		st.selfShield = p.getOffhandItem().is(Items.SHIELD);
		st.selfPearlCd = p.getCooldowns().getCooldownPercent(new ItemStack(Items.ENDER_PEARL), 0f) * 20;
		st.selfWindCd = p.getCooldowns().getCooldownPercent(new ItemStack(LearnedFight.WIND), 0f) * 10;
		st.selfShieldCd = p.getCooldowns().getCooldownPercent(new ItemStack(Items.SHIELD), 0f) * 100;
		st.selfHand = Math.max(0, spec.hands.indexOf(LearnedFight.handOf(p.getMainHandItem())));
		st.selfUseTicks = p.isUsingItem() ? p.getTicksUsingItem() : 0;
		st.selfBlocking = p.isBlocking();
	}

	/** The two nearest other enemies it sees or hears (when it sees them: when they swung), and how many enemies are near. */
	private static void others(CombatState st, CombatSpec spec, Match m, ServerPlayer self, ServerPlayer target) {
		V3 eye = new V3(self.getX(), self.getEyeY(), self.getZ());
		ServerPlayer best = null, next = null;
		double bestD = Double.MAX_VALUE, nextD = Double.MAX_VALUE;
		boolean bestSeen = false, nextSeen = false;
		int near = 0;
		for (UUID id : m.participants()) {
			if (id.equals(self.getUUID()) || !m.inPlay(id) || !m.isEnemy(self.getUUID(), id)) continue;
			ServerPlayer p = m.player(id);
			if (p == null || p.level() != self.level() || p.isSpectator()) continue;
			double d = Math.hypot(p.getX() - self.getX(), p.getZ() - self.getZ());
			boolean visible = sees(self, eye, p);
			Vec3 pv = Motion.velocity(p);
			boolean heard = d <= 12 && Math.hypot(pv.x, pv.z) > 0.01 && !p.isShiftKeyDown();
			if (!visible && !heard && !id.equals(target.getUUID())) continue;
			if (d <= spec.nearEnemies) near++;
			if (id.equals(target.getUUID())) continue;
			if (d < bestD) {
				next = best;
				nextD = bestD;
				nextSeen = bestSeen;
				best = p;
				bestD = d;
				bestSeen = visible;
			} else if (d < nextD) {
				next = p;
				nextD = d;
				nextSeen = visible;
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
			st.e2SinceSwing = CombatMemory.sinceSwing(best.getUUID());
			st.e2HoldsBow = best.getMainHandItem().is(Items.BOW);
		}
		if (next != null) {
			st.e3Present = true;
			st.e3X = next.getX();
			st.e3Y = next.getY();
			st.e3Z = next.getZ();
			st.e3Yaw = next.getYRot();
			st.e3Visible = nextSeen;
			st.e3SinceSwing = CombatMemory.sinceSwing(next.getUUID());
			st.e3HoldsBow = next.getMainHandItem().is(Items.BOW);
		}
	}

	/**
	 * Its nearest team mate (always known where it is: team mates' name tags show through walls; its health only when
	 * seen) and how many are near.
	 */
	private static void allies(CombatState st, CombatSpec spec, Match m, ServerPlayer self, ServerPlayer target) {
		ServerPlayer best = null;
		double bestD = Double.MAX_VALUE;
		int near = 0;
		for (UUID id : m.participants()) {
			if (id.equals(self.getUUID()) || !m.inPlay(id) || m.isEnemy(self.getUUID(), id)) continue;
			ServerPlayer p = m.player(id);
			if (p == null || p.level() != self.level() || p.isSpectator()) continue;
			double d = Math.hypot(p.getX() - self.getX(), p.getZ() - self.getZ());
			if (d <= spec.nearAllies) near++;
			if (d < bestD) {
				best = p;
				bestD = d;
			}
		}
		st.alliesNear = near;
		if (best == null) return;
		st.allyPresent = true;
		st.allyX = best.getX();
		st.allyY = best.getY();
		st.allyZ = best.getZ();
		st.allyVisible = sees(self, new V3(self.getX(), self.getEyeY(), self.getZ()), best);
		st.allyHealth = best.getHealth();
		st.allyMaxHealth = best.getMaxHealth();
	}

	/** On screen (feet, chest or eyes in view), not invisible (or their armor shows), nothing in between. */
	static boolean sees(ServerPlayer self, V3 eye, ServerPlayer p) {
		boolean onScreen = Senses.inView(eye, self.getYRot(), self.getXRot(), new V3(p.getX(), p.getEyeY(), p.getZ()))
				|| Senses.inView(eye, self.getYRot(), self.getXRot(), new V3(p.getX(), p.getY() + 0.9, p.getZ()))
				|| Senses.inView(eye, self.getYRot(), self.getXRot(), new V3(p.getX(), p.getY(), p.getZ()));
		return onScreen && (!p.isInvisible() || Perception.givenAway(p, p.distanceTo(self))) && self.hasLineOfSight(p);
	}

	/** The enemy projectile that comes closest soonest (passing within 1.5 blocks of the chest within 2 seconds). */
	private static void threat(CombatState st, CombatSpec spec, Match m, ServerPlayer self) {
		double cy = self.getY() + spec.chestHeight * self.getScale();
		double bestT = Double.MAX_VALUE;
		for (Projectile p : self.level().getEntitiesOfClass(Projectile.class, self.getBoundingBox().inflate(40))) {
			if (!(p.getOwner() instanceof ServerPlayer owner) || owner == self || !m.isEnemy(self.getUUID(), owner.getUUID())) continue;
			Vec3 v = p.getDeltaMovement();
			double px = p.getX() - self.getX(), py = p.getY() - cy, pz = p.getZ() - self.getZ();
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

	/** The melee weapon it would hold: the fight's weapon first, else the first weapon in the hotbar. */
	static ItemStack weapon(ServerPlayer p, Weapon focus) {
		Inventory inv = p.getInventory();
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

	/** How many of something are in the hotbar (what can be taken in hand without opening the inventory). */
	private static int hotbar(Inventory inv, Predicate<ItemStack> p) {
		int n = 0;
		for (int i = 0; i < 9; i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && p.test(s)) n += s.getCount();
		}
		return n;
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

	private static int enchant(ServerPlayer any, ItemStack s, String enchantment) {
		if (s.isEmpty()) return 0;
		return Ids.enchantment(any.level().getServer(), enchantment).map(h -> s.getEnchantments().getLevel(h)).orElse(0);
	}
}
