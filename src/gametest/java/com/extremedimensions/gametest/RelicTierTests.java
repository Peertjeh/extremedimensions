package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;
import static com.extremedimensions.gametest.CurseTests.near;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

import com.extremedimensions.binding.Charms;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.elder.ElderSpells;
import com.extremedimensions.elder.ElderUltimate;
import com.extremedimensions.elder.Ferryman;
import com.extremedimensions.elder.FerrymanService;
import com.extremedimensions.expedition.ExpeditionService;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.penalty.CurseLog;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.registry.ModEffects;
import com.extremedimensions.relic.Relic;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.server.Dims;
import com.extremedimensions.tribe.BarterService;
import com.extremedimensions.tribe.Standing;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Relics come in four tiers, the rarer found the more rarely (and more often from great foes), and the eight relics that
 * came with the tiers give what they promise and take their price. Every setting a test changes is put back.
 */
public class RelicTierTests {
	/** A zombie at a position relative to the test, standing still. */
	private static Mob zombie(GameTestHelper helper, double x, double y, double z) {
		Mob m = (Mob) helper.spawn(BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("minecraft", "zombie")), new Vec3(x, y, z));
		m.setNoAi(true);
		return m;
	}

	private static void place(GameTestHelper helper, ServerPlayer p, double x, double y, double z) {
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.snapTo(at.x, at.y, at.z, 0f, 0f);
	}

	private static Holder<DamageType> arrow(ServerLevel level) {
		return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.ARROW);
	}

	/** The tier weights: ordinary common, uncommon, rare, epic, then the great foes' in the same order. */
	private static int[] weights() {
		return new int[] {Config.RELIC_WEIGHT_COMMON, Config.RELIC_WEIGHT_UNCOMMON, Config.RELIC_WEIGHT_RARE, Config.RELIC_WEIGHT_EPIC,
				Config.RELIC_GREAT_WEIGHT_COMMON, Config.RELIC_GREAT_WEIGHT_UNCOMMON, Config.RELIC_GREAT_WEIGHT_RARE, Config.RELIC_GREAT_WEIGHT_EPIC};
	}

	private static void weights(int... w) {
		Config.RELIC_WEIGHT_COMMON = w[0];
		Config.RELIC_WEIGHT_UNCOMMON = w[1];
		Config.RELIC_WEIGHT_RARE = w[2];
		Config.RELIC_WEIGHT_EPIC = w[3];
		Config.RELIC_GREAT_WEIGHT_COMMON = w[4];
		Config.RELIC_GREAT_WEIGHT_UNCOMMON = w[5];
		Config.RELIC_GREAT_WEIGHT_RARE = w[6];
		Config.RELIC_GREAT_WEIGHT_EPIC = w[7];
	}

	/** Every stack of the Extreme curses (or of all the others) a player carries. */
	private static int stacks(PlayerData d, boolean extreme) {
		int n = 0;
		for (Penalty pen : Penalty.values()) {
			if (pen.extreme == extreme) {
				n += d.owned(pen);
			}
		}
		return n;
	}

	/** Nothing owed any more (a test that owes curses leaves no offer waiting for its player). */
	private static void settle(PlayerData d) {
		d.pendingOffer = new ArrayList<>();
		d.pendingMult = new ArrayList<>();
		d.owed = new ArrayList<>();
		d.pendingPenalties = 0;
	}

	/** Every curse taken off again (an Extreme one left on a test player would go on acting round the other tests). */
	private static void cleanse(ServerPlayer p, PlayerData d) {
		for (Penalty pen : Penalty.values()) {
			while (d.removeStack(pen)) {
				// one stack at a time, until none is left
			}
		}
		CurseEffects.applyEffects(p, d);
	}

	// ------------------------------------------------------------------ tiers

	@GameTest
	public void relicsComeInTiersAndTheRarerAreFoundMoreRarely(GameTestHelper helper) {
		int[] was = weights();
		try {
			int[] per = new int[4];
			for (Relic r : Relic.values()) {
				check(helper, r.tier != null, "%s has no tier", r.id);
				per[r.tier.ordinal()]++;
				ItemStack s = new ItemStack(RelicService.item(r));
				check(helper, s.getRarity().name().equals(r.tier.name()), "%s is named in the colour of %s, not of its tier %s", r.id, s.getRarity(), r.tier);
			}
			check(helper, per[0] == 5 && per[1] == 5 && per[2] == 5 && per[3] == 3, "the tiers hold %s relics, not 5, 5, 5 and 3", java.util.Arrays.toString(per));
			check(helper, Relic.HELDS_CHAIN.tier == Relic.Tier.EPIC && Relic.DEBTORS_SEAL.tier == Relic.Tier.EPIC && Relic.UNBOUND_PINION.tier == Relic.Tier.EPIC
					&& Relic.LAST_STITCH.tier == Relic.Tier.RARE && Relic.HUNGERS_FANG.tier == Relic.Tier.RARE && Relic.KEEPERS_EYE.tier == Relic.Tier.RARE
					&& Relic.THORNWARD_BARK.tier == Relic.Tier.UNCOMMON && Relic.HOLLOW_WHISTLE.tier == Relic.Tier.UNCOMMON
					&& Relic.PILGRIMS_SANDAL.tier == Relic.Tier.COMMON && Relic.LODESTONE_CHIP.tier == Relic.Tier.COMMON, "a relic is of the wrong tier");
			// Only the epic tier weighs among ordinary finds, only the common among the great foes'
			weights(0, 0, 0, 1, 1, 0, 0, 0);
			for (int i = 0; i < 200; i++) {
				Relic r = RelicService.draw(false);
				check(helper, r.tier == Relic.Tier.EPIC && r != Relic.HELDS_CHAIN, "with only the epic tier weighed, %s was found", r.id);
				Relic g = RelicService.draw(true);
				check(helper, g.tier == Relic.Tier.COMMON, "with only the common tier weighed for great foes, a great foe left %s", g.id);
				check(helper, RelicService.random().getItem() != RelicService.item(Relic.HELDS_CHAIN)
						&& RelicService.random(true).getItem() != RelicService.item(Relic.HELDS_CHAIN), "a relic found at random was the Held's Chain");
			}
			// Every weight 0: every relic that can be found comes, all as likely, and still never the Held's Chain
			weights(0, 0, 0, 0, 0, 0, 0, 0);
			Set<Relic> seen = EnumSet.noneOf(Relic.class);
			for (int i = 0; i < 1000; i++) {
				seen.add(RelicService.draw(i % 2 == 0));
			}
			check(helper, !seen.contains(Relic.HELDS_CHAIN) && seen.size() == Relic.values().length - 1,
					"with every weight 0, %s of %s relics were found (the Held's Chain among them: %s)", seen.size(), Relic.values().length - 1,
					seen.contains(Relic.HELDS_CHAIN));
			// The default odds: commons most, epics least, and a great foe's epics far more often than an ordinary find's
			weights(50, 30, 15, 5, 20, 35, 30, 15);
			int[] plain = new int[4], great = new int[4];
			for (int i = 0; i < 4000; i++) {
				plain[RelicService.draw(false).tier.ordinal()]++;
				great[RelicService.draw(true).tier.ordinal()]++;
			}
			check(helper, plain[0] > plain[1] && plain[1] > plain[2] && plain[2] > plain[3], "ordinary finds by tier: %s", java.util.Arrays.toString(plain));
			check(helper, great[3] > plain[3] * 2 && great[0] < plain[0], "great foes' finds by tier %s against ordinary ones %s", java.util.Arrays.toString(great),
					java.util.Arrays.toString(plain));
		} finally {
			weights(was);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the eight

	@GameTest
	public void thePilgrimsSandalHastensAndHungersOnTheRoad(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		try {
			d.relic = Relic.PILGRIMS_SANDAL.id;
			p.removeEffect(MobEffects.SPEED);
			d.tickCounter = 0;
			RelicService.tick(p, d);
			check(helper, !p.hasEffect(MobEffects.SPEED), "the Pilgrim's Sandal gave speed at home");
			near(helper, Charms.exhaustion(p), 1.0, "hunger at home with the Pilgrim's Sandal");
			d.inRun = true;
			d.tickCounter = 0;
			RelicService.tick(p, d);
			MobEffectInstance speed = p.getEffect(MobEffects.SPEED);
			check(helper, speed != null && speed.getAmplifier() == Config.RELIC_PILGRIMS_SANDAL_SPEED - 1, "on an expedition the Pilgrim's Sandal gave %s",
					speed);
			near(helper, Charms.exhaustion(p), Config.RELIC_PILGRIMS_SANDAL_HUNGER, "hunger on an expedition with the Pilgrim's Sandal");
			d.inRun = false;
			d.inSeam = true;
			near(helper, Charms.exhaustion(p), Config.RELIC_PILGRIMS_SANDAL_HUNGER, "hunger in the Seam with the Pilgrim's Sandal");
		} finally {
			d.inRun = false;
			d.inSeam = false;
			d.relic = null;
			p.removeEffect(MobEffects.SPEED);
		}
		helper.succeed();
	}

	@GameTest
	public void theLodestoneChipEasesTheDrainAndBurdensTheCollapse(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		try {
			d.relic = Relic.LODESTONE_CHIP.id;
			for (String dim : new String[] {Dims.NETHER, Dims.END, Dims.SEAM}) {
				near(helper, RelicService.drainFactor(d, dim), Config.RELIC_LODESTONE_CHIP_DRAIN, "drain in " + dim + " with the Lodestone Chip");
			}
			int before = d.pendingPenalties;
			ExpeditionService.oweCollapse(d, false);
			check(helper, d.pendingPenalties == before + 1 + Config.RELIC_LODESTONE_CHIP_COLLAPSE, "a Collapse with the Lodestone Chip cost %s curses",
					d.pendingPenalties - before);
			d.relic = null;
			near(helper, RelicService.drainFactor(d, Dims.NETHER), 1.0, "drain without the Lodestone Chip");
			before = d.pendingPenalties;
			ExpeditionService.oweCollapse(d, false);
			check(helper, d.pendingPenalties == before + 1, "a Collapse without a relic cost %s curses", d.pendingPenalties - before);
		} finally {
			d.relic = null;
			settle(d);
		}
		helper.succeed();
	}

	@GameTest
	public void theThornwardBarkStingsBackAndBluntsItsBearersBlows(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		place(helper, p, 1.5, 2, 1.5);
		Mob zombie = zombie(helper, 3.5, 2, 1.5);
		try {
			d.relic = Relic.THORNWARD_BARK.id;
			float full = zombie.getHealth();
			RelicService.afterDamage(p, level.damageSources().mobAttack(zombie), 4f, 4f, false);
			// (a zombie's own armour takes a little of it)
			float lost = full - zombie.getHealth();
			check(helper, lost > Config.RELIC_THORNWARD_BARK_THORNS * 0.9 && lost <= Config.RELIC_THORNWARD_BARK_THORNS + 0.01,
					"a zombie striking the Thornward Bark's bearer lost %s health, not about %s", lost, Config.RELIC_THORNWARD_BARK_THORNS);
			// Not from an arrow, a blow that did no harm, one the shield caught, or a thorn (so two bearers never answer forever)
			float now = zombie.getHealth();
			for (DamageSource s : new DamageSource[] {new DamageSource(arrow(level), zombie, zombie), level.damageSources().thorns(zombie)}) {
				zombie.invulnerableTime = 0;
				RelicService.afterDamage(p, s, 4f, 4f, false);
			}
			zombie.invulnerableTime = 0;
			RelicService.afterDamage(p, level.damageSources().mobAttack(zombie), 0f, 0f, false);
			zombie.invulnerableTime = 0;
			RelicService.afterDamage(p, level.damageSources().mobAttack(zombie), 4f, 0f, true);
			near(helper, zombie.getHealth(), now, "the zombie's health after an arrow, a thorn, a harmless and a blocked blow");
			// The bearer's own blows by hand hit softer; their arrows and another's blows do not
			near(helper, RelicService.dealt(level.damageSources().playerAttack(p), 10f), 10 * Config.RELIC_THORNWARD_BARK_MELEE, "a blow by the bearer's hand");
			near(helper, RelicService.dealt(new DamageSource(arrow(level), p, p), 10f), 10, "the bearer's arrow");
			near(helper, RelicService.dealt(level.damageSources().mobAttack(zombie), 10f), 10, "a zombie's blow");
			d.relic = null;
			near(helper, RelicService.dealt(level.damageSources().playerAttack(p), 10f), 10, "a blow by hand without the Thornward Bark");
			now = zombie.getHealth();
			zombie.invulnerableTime = 0;
			RelicService.afterDamage(p, level.damageSources().mobAttack(zombie), 4f, 4f, false);
			near(helper, zombie.getHealth(), now, "the zombie's health after striking a player without the Thornward Bark");
		} finally {
			d.relic = null;
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest
	public void theHollowWhistleBartersCheapAndWinsStandingSlowly(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		int tribe = Config.TRIBE_ENABLED, rep = d.tribeRep;
		double whistle = Config.RELIC_HOLLOW_WHISTLE_PRICE;
		try {
			Config.TRIBE_ENABLED = 1;
			d.tribeRep = 0;
			BarterService.Ware w = BarterService.ware(BarterService.FRAGMENT);
			int full = BarterService.price(p, w);
			d.relic = Relic.HOLLOW_WHISTLE.id;
			int cheap = BarterService.price(p, w);
			int want = Math.max(1, (int) Math.ceil(w.price() * (Math.max(0, Config.TRIBE_BARTER_PRICE) * Config.RELIC_HOLLOW_WHISTLE_PRICE)));
			check(helper, cheap == want && cheap < full, "with the Hollow Whistle a fragment costs %s (without %s), not %s", cheap, full, want);
			Config.RELIC_HOLLOW_WHISTLE_PRICE = 0.001;
			check(helper, BarterService.price(p, w) == 1, "a ware cost %s with almost all of its price taken off, not 1", BarterService.price(p, w));
			Config.RELIC_HOLLOW_WHISTLE_PRICE = whistle;
			// Standing won comes at a share, standing lost in full
			Standing.change(p, 6, "gui.extremedimensions.tribe.why.gift");
			check(helper, d.tribeRep == (int) Math.round(6 * Config.RELIC_HOLLOW_WHISTLE_STANDING), "a gift of 6 standing gave %s with the Hollow Whistle",
					d.tribeRep);
			int before = d.tribeRep;
			Standing.change(p, -4, "gui.extremedimensions.tribe.why.hoard");
			check(helper, d.tribeRep == before - 4, "a loss of 4 standing took %s with the Hollow Whistle", before - d.tribeRep);
			d.relic = null;
			d.tribeRep = 0;
			Standing.change(p, 6, "gui.extremedimensions.tribe.why.gift");
			check(helper, d.tribeRep == 6, "a gift of 6 standing gave %s without the Hollow Whistle", d.tribeRep);
		} finally {
			Config.TRIBE_ENABLED = tribe;
			Config.RELIC_HOLLOW_WHISTLE_PRICE = whistle;
			d.tribeRep = rep;
			d.relic = null;
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void theLastStitchHoldsOneKillingBlowAnExpedition(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		Runnable restore = () -> {
			d.inRun = false;
			d.stitchSpent = false;
			d.relic = null;
			CurseEffects.applyEffects(p, d);
			p.removeEffect(MobEffects.RESISTANCE);
			p.setHealth(p.getMaxHealth());
		};
		try {
			d.relic = Relic.LAST_STITCH.id;
			CurseEffects.applyEffects(p, d);
			near(helper, p.getMaxHealth(), 20 - Config.RELIC_LAST_STITCH_MAX_HEALTH, "max health with the Last Stitch");
			DamageSource blow = level.damageSources().generic();
			check(helper, RelicService.stitch(p, blow, 100f), "the Last Stitch held a killing blow at home");
			d.inRun = true;
			d.stitchSpent = false;
			check(helper, !RelicService.stitch(p, blow, 100f), "the Last Stitch did not hold the expedition's first killing blow");
			near(helper, p.getHealth(), Config.RELIC_LAST_STITCH_HEALTH, "health the Last Stitch left");
			check(helper, p.hasEffect(MobEffects.RESISTANCE), "no Resistance after the Last Stitch held");
			check(helper, RelicService.stitch(p, blow, 100f), "the Last Stitch held a second killing blow in one expedition");
			// The void (and /kill, a Collapse) goes straight through it, and does not spend it
			d.stitchSpent = false;
			check(helper, RelicService.stitch(p, level.damageSources().fellOutOfWorld(), 100f), "the Last Stitch held against the void");
			check(helper, !d.stitchSpent, "the void spent the Last Stitch");
		} catch (RuntimeException e) {
			restore.run();
			throw e;
		}
		// A real killing blow, past the grace a player has on joining: the player lives, at the Last Stitch's health
		helper.runAfterDelay(70, () -> {
			try {
				p.removeEffect(MobEffects.RESISTANCE);
				p.setHealth(p.getMaxHealth());
				d.stitchSpent = false;
				p.hurtServer(level, level.damageSources().generic(), 1000f);
				check(helper, p.isAlive() && Math.abs(p.getHealth() - Config.RELIC_LAST_STITCH_HEALTH) < 0.01,
						"after a real killing blow the bearer of the Last Stitch is %s with %s health", p.isAlive() ? "alive" : "dead", p.getHealth());
				d.relic = null;
				CurseEffects.applyEffects(p, d);
				near(helper, p.getMaxHealth(), 20, "max health once the Last Stitch came off");
			} finally {
				restore.run();
			}
			helper.succeed();
		});
	}

	@GameTest
	public void hungersFangFeedsOnBlowsAndHungersAlways(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		place(helper, p, 1.5, 2, 1.5);
		Mob zombie = zombie(helper, 3.5, 2, 1.5);
		try {
			near(helper, Charms.exhaustion(p), 1.0, "hunger without Hunger's Fang");
			d.relic = Relic.HUNGERS_FANG.id;
			near(helper, Charms.exhaustion(p), Config.RELIC_HUNGERS_FANG_HUNGER, "hunger at home with Hunger's Fang");
			p.setHealth(10f);
			RelicService.afterDamage(zombie, p.damageSources().playerAttack(p), 10f, 10f, false);
			near(helper, p.getHealth(), 10 + 10 * Config.RELIC_HUNGERS_FANG_LEECH, "health after a blow of 10 with Hunger's Fang");
			p.setHealth(10f);
			RelicService.afterDamage(zombie, new DamageSource(arrow(level), p, p), 10f, 10f, false);
			near(helper, p.getHealth(), 10, "health after an arrow of 10 with Hunger's Fang");
			// A killing blow (which the game reports only as a death) feeds on the health it took
			float left = zombie.getHealth();
			RelicService.striking(zombie, p.damageSources().playerAttack(p), 100f);
			RelicService.slain(zombie, p.damageSources().playerAttack(p));
			near(helper, p.getHealth(), 10 + left * Config.RELIC_HUNGERS_FANG_LEECH, "health after a killing blow with Hunger's Fang");
			p.setHealth(10f);
			d.relic = null;
			RelicService.afterDamage(zombie, p.damageSources().playerAttack(p), 10f, 10f, false);
			near(helper, p.getHealth(), 10, "health after a blow of 10 without Hunger's Fang");
		} finally {
			d.relic = null;
			p.setHealth(p.getMaxHealth());
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest
	public void theDebtorsSealDoublesAtonementAndTheCurses(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.SCAR, 1);
		PlayerData d = DataStore.get(p.getUUID());
		int ultimate = Config.ELDER_ULTIMATE_CURSES;
		try {
			Config.ELDER_ULTIMATE_CURSES = 2;
			d.atoning = Penalty.SCAR.id;
			// Atonement earned: twice as much; given back by an echo: as it was
			d.atonement.remove(Penalty.SCAR.id);
			PenaltyService.atone(p, d, false, 5);
			int plain = d.atonement.getOrDefault(Penalty.SCAR.id, 0);
			d.relic = Relic.DEBTORS_SEAL.id;
			d.atonement.remove(Penalty.SCAR.id);
			PenaltyService.atone(p, d, false, 5);
			int sealed = d.atonement.getOrDefault(Penalty.SCAR.id, 0);
			check(helper, plain == 5 && sealed == (int) Math.round(5 * Config.RELIC_DEBTORS_SEAL_ATONEMENT),
					"5 points of atonement came to %s without the Debtor's Seal and %s with it", plain, sealed);
			d.atonement.remove(Penalty.SCAR.id);
			PenaltyService.restore(p, d, false, 5);
			check(helper, d.atonement.getOrDefault(Penalty.SCAR.id, 0) == 5, "an echo gave back %s of 5 points with the Debtor's Seal",
					d.atonement.getOrDefault(Penalty.SCAR.id, 0));
			d.atonement.remove(Penalty.SCAR.id);
			// A death's curse comes twice (two offers, one after the other), and so does a Collapse's
			int owed = d.pendingPenalties;
			PenaltyService.oweDeath(d, false);
			check(helper, d.pendingPenalties == owed + Config.RELIC_DEBTORS_SEAL_CURSES, "a death owed %s curses with the Debtor's Seal",
					d.pendingPenalties - owed);
			owed = d.pendingPenalties;
			ExpeditionService.oweCollapse(d, false);
			check(helper, d.pendingPenalties == owed + Config.RELIC_DEBTORS_SEAL_CURSES, "a Collapse owed %s curses with the Debtor's Seal",
					d.pendingPenalties - owed);
			// A Lurker's curse twice; a barter's (no Lurker's) once
			int before = stacks(d, false);
			PenaltyService.curse(p, false, 0, CurseLog.Source.LURKER);
			check(helper, stacks(d, false) == before + Config.RELIC_DEBTORS_SEAL_CURSES, "a Lurker laid %s curses with the Debtor's Seal",
					stacks(d, false) - before);
			before = stacks(d, false);
			PenaltyService.curse(p, false, 0, CurseLog.Source.CAVE_LURKER);
			check(helper, stacks(d, false) == before + Config.RELIC_DEBTORS_SEAL_CURSES, "a Cave Lurker laid %s curses with the Debtor's Seal",
					stacks(d, false) - before);
			before = stacks(d, false);
			PenaltyService.curse(p, false, 0, CurseLog.Source.BARTER);
			check(helper, stacks(d, false) == before + 1, "a barter laid %s curses with the Debtor's Seal", stacks(d, false) - before);
			// An Elder's ultimate: its two curses three times over
			int extreme = stacks(d, true);
			ElderUltimate.strike(p, "boss.extremedimensions.matriarch");
			check(helper, stacks(d, true) == extreme + 2 * Config.RELIC_DEBTORS_SEAL_BOSS_CURSES, "an ultimate laid %s extreme curses with the Debtor's Seal",
					stacks(d, true) - extreme);
			// Without it, once
			d.relic = null;
			owed = d.pendingPenalties;
			PenaltyService.oweDeath(d, false);
			check(helper, d.pendingPenalties == owed + 1, "a death owed %s curses without the Debtor's Seal", d.pendingPenalties - owed);
			before = stacks(d, false);
			PenaltyService.curse(p, false, 0, CurseLog.Source.LURKER);
			check(helper, stacks(d, false) == before + 1, "a Lurker laid %s curses without the Debtor's Seal", stacks(d, false) - before);
		} finally {
			Config.ELDER_ULTIMATE_CURSES = ultimate;
			d.relic = null;
			d.atoning = null;
			d.atonement.remove(Penalty.SCAR.id);
			settle(d);
			cleanse(p, d);
		}
		helper.succeed();
	}

	/** No arena: it would reach into the other tests round this one. */
	private static Ferryman ferryman(GameTestHelper helper, ServerPlayer p) {
		int arena = Config.ELDER_ARENA;
		Config.ELDER_ARENA = 0;
		try {
			return FerrymanService.summonAt(p, helper.absolutePos(new BlockPos(5, 2, 5)));
		} finally {
			Config.ELDER_ARENA = arena;
		}
	}

	@GameTest(environment = Alone.PINION_UNBOUND, maxTicks = 60)
	public void theUnboundPinionFliesFreeButPaysInBloodAndWings(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		d.relic = Relic.UNBOUND_PINION.id;
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
		Ferryman f = ferryman(helper, p);
		try {
			check(helper, f != null, "the Ferryman could not come");
			// The Elders cannot pull its bearer down or bind their wings
			check(helper, !RelicService.grounds(p), "the Elders may still pull the Unbound Pinion's bearer down");
			ElderSpells.snare(f, p);
			check(helper, !p.hasEffect(ModEffects.BOUND_WINGS) && !ElderSpells.grounded(p), "an Elder bound the wings of the Unbound Pinion's bearer");
			// ... who takes more from everything
			near(helper, CurseEffects.damageTaken(p, level.damageSources().generic(), 10f), 10 * Config.RELIC_UNBOUND_PINION_DAMAGE,
					"damage taken with the Unbound Pinion");
			// ... and whose elytra wears many times as fast: the rest of it every second of gliding (the game's own point comes
			// with its own gliding, which a test player does not do)
			p.setOnGround(false);
			check(helper, p.tryToStartFallFlying(), "the elytra would not glide");
			d.pinionGlide = 0;
			for (int i = 0; i < 19; i++) {
				RelicService.tick(p, d);
			}
			check(helper, p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == 0, "the elytra wore before a second of gliding");
			RelicService.tick(p, d);
			int worn = p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();
			check(helper, worn == Config.RELIC_UNBOUND_PINION_WEAR - 1, "a second of gliding wore the elytra by %s, not %s", worn,
					Config.RELIC_UNBOUND_PINION_WEAR - 1);
			// Worn to its last point it stops there: it never breaks
			ItemStack wings = p.getItemBySlot(EquipmentSlot.CHEST);
			int max = wings.getMaxDamage();
			wings.setDamageValue(max - 5);
			RelicService.wear(p);
			RelicService.wear(p);
			ItemStack after = p.getItemBySlot(EquipmentSlot.CHEST);
			check(helper, after.is(Items.ELYTRA) && after.getDamageValue() == max - 1, "a worn elytra was left as %s with %s of %s worn", after,
					after.getDamageValue(), max);
			// Unbound, the elytra wears as ever and the Elders pull its wearer down again
			after.setDamageValue(0);
			d.relic = null;
			d.pinionGlide = 0;
			for (int i = 0; i < 20; i++) {
				RelicService.tick(p, d);
			}
			check(helper, p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == 0, "the elytra wore faster without the Unbound Pinion");
			check(helper, RelicService.grounds(p), "the Elders may not pull a player down without the Unbound Pinion");
		} finally {
			p.stopFallFlying();
			p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			d.relic = null;
			d.pinionGlide = 0;
			if (f != null) {
				FerrymanService.fade(f);
			}
		}
		helper.succeed();
	}

	/** In another mod's world a relic sleeps with the curses: neither its gift nor its price acts there. */
	@GameTest
	public void theNewRelicsSleepInAnotherModsWorld(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		try {
			d.away = true;
			d.inRun = true;
			d.relic = Relic.HUNGERS_FANG.id;
			near(helper, Charms.exhaustion(p), 1.0, "hunger with Hunger's Fang asleep");
			d.relic = Relic.PILGRIMS_SANDAL.id;
			near(helper, Charms.exhaustion(p), 1.0, "hunger with the Pilgrim's Sandal asleep");
			d.relic = Relic.UNBOUND_PINION.id;
			near(helper, CurseEffects.damageTaken(p, level.damageSources().generic(), 10f), 10, "damage with the Unbound Pinion asleep");
			check(helper, RelicService.grounds(p), "the Unbound Pinion kept the Elders off while asleep");
			d.relic = Relic.LODESTONE_CHIP.id;
			near(helper, RelicService.drainFactor(d, Dims.NETHER), 1.0, "drain with the Lodestone Chip asleep");
			d.relic = Relic.DEBTORS_SEAL.id;
			check(helper, RelicService.curses(d, false) == 1 && RelicService.curses(d, true) == 1 && RelicService.atonement(d, 5) == 5,
					"the Debtor's Seal acted asleep");
			d.relic = Relic.HOLLOW_WHISTLE.id;
			near(helper, RelicService.barter(p), 1.0, "barter prices with the Hollow Whistle asleep");
			d.relic = Relic.THORNWARD_BARK.id;
			near(helper, RelicService.dealt(level.damageSources().playerAttack(p), 10f), 10, "a blow by hand with the Thornward Bark asleep");
			d.relic = Relic.LAST_STITCH.id;
			check(helper, RelicService.stitch(p, level.damageSources().generic(), 100f), "the Last Stitch held a blow while asleep");
		} finally {
			d.away = false;
			d.inRun = false;
			d.relic = null;
		}
		helper.succeed();
	}
}
