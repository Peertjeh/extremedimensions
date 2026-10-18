package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;

import java.util.List;

import com.extremedimensions.config.Config;
import com.extremedimensions.elder.ElderGaol;
import com.extremedimensions.elder.ElderPower;
import com.extremedimensions.elder.ElderSpells;
import com.extremedimensions.elder.ElderStrikes;
import com.extremedimensions.elder.Ferryman;
import com.extremedimensions.elder.FerrymanService;
import com.extremedimensions.elder.Weaver;
import com.extremedimensions.elder.WeaverService;
import com.extremedimensions.registry.ModEffects;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * How hard the Elders fight (and how that grows with those who come against them), the spells they lay, how they keep
 * flyers down, the Ferryman's gaol and the Weaver's longer Lance. No chunks are forced here: the test players keep
 * theirs loaded, and letting go of forced chunks could stop the tests round these.
 */
public class ElderFightTests {
	/** No arena in these tests: it would reach into the other tests round them. */
	private static Ferryman ferryman(GameTestHelper helper, ServerPlayer p) {
		int arena = Config.ELDER_ARENA;
		Config.ELDER_ARENA = 0;
		try {
			return FerrymanService.summonAt(p, helper.absolutePos(new BlockPos(5, 2, 5)));
		} finally {
			Config.ELDER_ARENA = arena;
		}
	}

	private static void stand(GameTestHelper helper, ServerPlayer p, int x, int z) {
		BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
	}

	@GameTest
	public void eldersGrowWithThoseWhoFightThem(GameTestHelper helper) {
		double hs = Config.ELDER_HEALTH_SCALE, hp = Config.ELDER_HEALTH_PER_PLAYER, ds = Config.ELDER_DAMAGE_SCALE, dp = Config.ELDER_DAMAGE_PER_PLAYER;
		double rp = Config.ELDER_REST_PER_PLAYER, rm = Config.ELDER_REST_MIN;
		int split = Config.ELDER_SPLIT_MAX;
		try {
			Config.ELDER_HEALTH_SCALE = 1.5;
			Config.ELDER_HEALTH_PER_PLAYER = 0.5;
			Config.ELDER_DAMAGE_SCALE = 1.5;
			Config.ELDER_DAMAGE_PER_PLAYER = 0.1;
			Config.ELDER_REST_PER_PLAYER = 0.1;
			Config.ELDER_REST_MIN = 0.6;
			Config.ELDER_SPLIT_MAX = 3;
			check(helper, Math.abs(ElderPower.health(1) - 1.5) < 1e-9, "alone, its health is x%s", ElderPower.health(1));
			check(helper, Math.abs(ElderPower.health(3) - 3.0) < 1e-9, "against three, its health is x%s", ElderPower.health(3));
			check(helper, Math.abs(ElderPower.damage(1) - 1.5) < 1e-9, "alone, its blows deal x%s", ElderPower.damage(1));
			check(helper, Math.abs(ElderPower.damage(2) - 1.65) < 1e-9, "against two, its blows deal x%s", ElderPower.damage(2));
			check(helper, Math.abs(ElderPower.pace(1) - 1.0) < 1e-9, "alone, its pause runs out x%s faster", ElderPower.pace(1));
			check(helper, Math.abs(ElderPower.pace(9) - 1 / 0.6) < 1e-9, "against nine, its pause is not held at its shortest (x%s)", ElderPower.pace(9));
			check(helper, ElderPower.splits(1) == 1 && ElderPower.splits(2) == 2 && ElderPower.splits(5) == 3, "its great blows fall on %s/%s/%s",
					ElderPower.splits(1), ElderPower.splits(2), ElderPower.splits(5));
		} finally {
			Config.ELDER_HEALTH_SCALE = hs;
			Config.ELDER_HEALTH_PER_PLAYER = hp;
			Config.ELDER_DAMAGE_SCALE = ds;
			Config.ELDER_DAMAGE_PER_PLAYER = dp;
			Config.ELDER_REST_PER_PLAYER = rp;
			Config.ELDER_REST_MIN = rm;
			Config.ELDER_SPLIT_MAX = split;
		}
		helper.succeed();
	}

	/** Players near enough to count as fighting it (the range is narrowed in these tests, see below). */
	private static int near(Entity boss) {
		int n = 0;
		for (ServerPlayer q : ((ServerLevel) boss.level()).players()) {
			if (q.isAlive() && !q.isSpectator() && !q.isCreative() && q.distanceTo(boss) <= Config.ELDER_FIGHTERS_RANGE) {
				n++;
			}
		}
		return Math.max(1, n);
	}

	/**
	 * In a fight its health and the damage of its blows follow the players near it: one, then one more. Only players
	 * very near count here, so the players of the tests round this one do not.
	 */
	@GameTest(maxTicks = 80)
	public void anEldersHealthFollowsThoseNearIt(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		stand(helper, p, 2, 2);
		int range = Config.ELDER_FIGHTERS_RANGE;
		Config.ELDER_FIGHTERS_RANGE = 8;
		Ferryman f = ferryman(helper, p);
		Runnable done = () -> {
			Config.ELDER_FIGHTERS_RANGE = range;
			if (f != null && f.isAlive()) {
				FerrymanService.fade(f);
			}
		};
		if (f == null) {
			done.run();
		}
		check(helper, f != null, "the Ferryman could not come");
		FerrymanService.force(f, Ferryman.KNELL);
		int[] counted = {0};
		ServerPlayer[] second = {null};
		helper.runAfterDelay(5, () -> {
			counted[0] = near(f);
			boolean one = Math.abs(f.toughness() - ElderPower.health(counted[0])) < 0.001;
			float dealt = ElderPower.scale(helper.getLevel().damageSources().mobAttack(f), 10f);
			boolean hits = Math.abs(dealt - 10 * ElderPower.damage(counted[0])) < 0.01;
			if (!one || !hits) {
				done.run();
			}
			check(helper, one, "against %s it has x%s health, not x%s", counted[0], f.toughness(), ElderPower.health(counted[0]));
			check(helper, hits, "against %s a blow of 10 deals %s", counted[0], dealt);
			second[0] = TestPlayers.survivor(helper, "test-second");
		});
		// Both stay at his side (he sails about) so both are counted when he next counts, once a second
		for (int t = 6; t <= 34; t += 2) {
			helper.runAfterDelay(t, () -> {
				if (f.isAlive() && second[0] != null) {
					p.teleportTo(f.getX() + 2, f.getY(), f.getZ());
					second[0].teleportTo(f.getX() - 2, f.getY(), f.getZ());
				}
			});
		}
		helper.runAfterDelay(35, () -> {
			int now = near(f);
			boolean grew = now > counted[0] && Math.abs(f.toughness() - ElderPower.health(now)) < 0.001;
			boolean share = Math.abs(f.getHealth() / f.getMaxHealth() - 1.0) < 0.01;
			done.run();
			check(helper, grew, "against %s (from %s) it has x%s health, not x%s", now, counted[0], f.toughness(), ElderPower.health(now));
			check(helper, share, "its health bar did not keep its share (%s of %s)", f.getHealth(), f.getMaxHealth());
			helper.succeed();
		});
	}

	@GameTest
	public void theMatriarchsHexSlowsAndSoursMilk(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		Identifier id = Identifier.fromNamespaceAndPath("extremedimensions", "matriarch_hex");
		p.addEffect(new MobEffectInstance(ModEffects.HEX, 40, 0, true, false, true));
		ElderSpells.sweep(helper.getLevel().getServer());
		AttributeModifier slow = p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(id);
		check(helper, slow != null && Math.abs(slow.amount() + Config.MATRIARCH_HEX_SLOW) < 1e-6, "the hex slows by %s", slow == null ? 0 : slow.amount());
		InteractionResult milk = ElderSpells.refuse(p, new ItemStack(Items.MILK_BUCKET));
		check(helper, milk == InteractionResult.FAIL, "milk could be drunk under the hex (%s)", milk);
		p.removeEffect(ModEffects.HEX);
		ElderSpells.sweep(helper.getLevel().getServer());
		check(helper, p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(id) == null, "the hex's slowness stayed after the hex");
		check(helper, ElderSpells.refuse(p, new ItemStack(Items.MILK_BUCKET)) == InteractionResult.PASS, "milk stayed sour after the hex");
		helper.succeed();
	}

	@GameTest
	public void theFerrymansWeightLowersJumps(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		Identifier id = Identifier.fromNamespaceAndPath("extremedimensions", "styx_weight");
		p.addEffect(new MobEffectInstance(ModEffects.WEIGHT, 40, 0, true, false, true));
		ElderSpells.sweep(helper.getLevel().getServer());
		AttributeModifier heavy = p.getAttribute(Attributes.JUMP_STRENGTH).getModifier(id);
		check(helper, heavy != null && Math.abs(heavy.amount() + Config.FERRYMAN_WEIGHT_JUMP) < 1e-6, "the weight lowers jumps by %s", heavy == null ? 0 : heavy.amount());
		p.removeEffect(ModEffects.WEIGHT);
		ElderSpells.sweep(helper.getLevel().getServer());
		check(helper, p.getAttribute(Attributes.JUMP_STRENGTH).getModifier(id) == null, "the weight stayed after it lifted");
		helper.succeed();
	}

	/**
	 * A flyer pulled down: wings bound (no gliding, no rockets), and free to fly again once they unbind. (Its blow is not
	 * looked at: a test player, whose client never finishes loading, takes no damage.)
	 */
	@GameTest(maxTicks = 60)
	public void anElderPullsFlyersDownAndBindsTheirWings(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		stand(helper, p, 2, 2);
		Ferryman f = ferryman(helper, p);
		check(helper, f != null, "the Ferryman could not come");
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
		ElderSpells.snare(f, p);
		boolean bound = p.hasEffect(ModEffects.BOUND_WINGS) && ElderSpells.grounded(p);
		p.setOnGround(false);
		boolean glides = p.tryToStartFallFlying();
		InteractionResult rocket = ElderSpells.refuse(p, new ItemStack(Items.FIREWORK_ROCKET));
		p.removeEffect(ModEffects.BOUND_WINGS);
		p.setOnGround(false);
		boolean free = p.tryToStartFallFlying();
		p.stopFallFlying();
		p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		FerrymanService.fade(f);
		check(helper, bound, "the flyer's wings were not bound");
		check(helper, !glides, "bound wings still glided");
		check(helper, rocket == InteractionResult.FAIL, "a rocket could still be used with bound wings (%s)", rocket);
		check(helper, free, "unbound wings would not glide again");
		helper.succeed();
	}

	/**
	 * The gaol shuts its passenger in: walking out is undone, a bar struck often enough breaks, and through that gap
	 * they get out.
	 */
	@GameTest(maxTicks = 120)
	public void theFerrymansGaolHoldsUntilABarBreaks(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		stand(helper, p, 2, 2);
		p.getAbilities().invulnerable = true;
		double hold = Config.FERRYMAN_GAOL_SECONDS;
		int hits = Config.FERRYMAN_GAOL_HITS, range = Config.ELDER_FIGHTERS_RANGE;
		Config.FERRYMAN_GAOL_SECONDS = 4;
		Config.FERRYMAN_GAOL_HITS = 3;
		// Only its own passenger counts as fighting him, so only they are shut in (not the players of other tests)
		Config.ELDER_FIGHTERS_RANGE = 8;
		Ferryman f = ferryman(helper, p);
		Runnable restore = () -> {
			Config.FERRYMAN_GAOL_SECONDS = hold;
			Config.FERRYMAN_GAOL_HITS = hits;
			Config.ELDER_FIGHTERS_RANGE = range;
			p.getAbilities().invulnerable = false;
			if (f != null && f.isAlive()) {
				FerrymanService.fade(f);
			}
		};
		if (f == null) {
			restore.run();
		}
		check(helper, f != null, "the Ferryman could not come");
		FerrymanService.force(f, Ferryman.GAOL);
		double[] centre = new double[3];
		Entity[] bar = {null};
		helper.runAfterDelay(20, () -> {
			List<Entity> posts = ElderGaol.posts(p);
			boolean shut = ElderGaol.shutIn(p) && posts.size() >= 12;
			if (!shut) {
				restore.run();
			}
			check(helper, shut, "the gaol did not shut its passenger in (%s bars)", posts.size());
			for (Entity e : posts) {
				centre[0] += e.getX() / posts.size();
				centre[1] = e.getY();
				centre[2] += e.getZ() / posts.size();
			}
			bar[0] = posts.get(0);
			// Walking out between the bars
			double ax = p.getX() - bar[0].getX(), az = p.getZ() - bar[0].getZ();
			double d = Math.max(0.1, Math.sqrt(ax * ax + az * az));
			p.teleportTo(centre[0] + ax / d * 7, centre[1], centre[2] + az / d * 7);
		});
		helper.runAfterDelay(24, () -> {
			double dx = p.getX() - centre[0], dz = p.getZ() - centre[2];
			boolean held = dx * dx + dz * dz < 3.2 * 3.2;
			if (!held) {
				restore.run();
			}
			check(helper, held, "the passenger walked out of the gaol (%s from its middle)", Math.sqrt(dx * dx + dz * dz));
			int standing = ElderGaol.posts(p).size();
			for (int i = 0; i < 3; i++) {
				ElderGaol.struck(p, bar[0]);
			}
			boolean broke = !bar[0].isAlive() && ElderGaol.posts(p).size() == standing - 1;
			if (!broke) {
				restore.run();
			}
			check(helper, broke, "three blows did not break the bar");
			// Out through the gap it left
			double bx = bar[0].getX() - centre[0], bz = bar[0].getZ() - centre[2];
			double d = Math.max(0.1, Math.sqrt(bx * bx + bz * bz));
			p.teleportTo(centre[0] + bx / d * 5, centre[1], centre[2] + bz / d * 5);
		});
		helper.runAfterDelay(28, () -> {
			boolean out = !ElderGaol.shutIn(p);
			double dx = p.getX() - centre[0], dz = p.getZ() - centre[2];
			boolean away = dx * dx + dz * dz > 4 * 4;
			restore.run();
			check(helper, out && away, "the passenger did not get out through the broken bar (%s from its middle)", Math.sqrt(dx * dx + dz * dz));
			helper.succeed();
		});
	}

	/** The Seraphic Lance burns half as long again as it used to (alone, one beam). */
	@GameTest(maxTicks = 160)
	public void theWeaversLanceBurnsLonger(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		stand(helper, p, 2, 2);
		p.getAbilities().invulnerable = true;
		double back = Config.WEAVER_LANCE_RETURN;
		int range = Config.ELDER_FIGHTERS_RANGE;
		// Only its own player counts as fighting it (one beam), not those of the tests round this one
		Config.ELDER_FIGHTERS_RANGE = 8;
		Weaver[] weaver = {null};
		int[] first = {-1}, last = {-1}, most = {0};
		Runnable restore = () -> {
			Config.WEAVER_LANCE_RETURN = back;
			Config.ELDER_FIGHTERS_RANGE = range;
			p.getAbilities().invulnerable = false;
			if (weaver[0] != null && weaver[0].isAlive()) {
				WeaverService.fade(weaver[0]);
			}
		};
		helper.runAfterDelay(5, () -> {
			int arena = Config.ELDER_ARENA;
			Config.ELDER_ARENA = 0;
			Config.WEAVER_LANCE_RETURN = 0;
			BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
			// Its own Weaver, the one this summons: a test beside it may raise one in the same tick
			java.util.List<Weaver> had = helper.getLevel().getEntitiesOfClass(Weaver.class, p.getBoundingBox().inflate(64));
			boolean came = WeaverService.summon(p, at.offset(12, 0, 0));
			Config.ELDER_ARENA = arena;
			weaver[0] = helper.getLevel().getEntitiesOfClass(Weaver.class, p.getBoundingBox().inflate(64)).stream()
					.filter(w -> !had.contains(w)).findFirst().orElse(null);
			if (!came || weaver[0] == null) {
				restore.run();
			}
			check(helper, came && weaver[0] != null, "the Weaver did not come");
			WeaverService.force(weaver[0], Weaver.LANCE);
		});
		for (int t = 6; t <= 130; t++) {
			int now = t;
			helper.runAfterDelay(t, () -> {
				int beams = weaver[0] == null ? 0 : ElderStrikes.beamsOf(weaver[0]);
				most[0] = Math.max(most[0], beams);
				if (beams > 0) {
					if (first[0] < 0) {
						first[0] = now;
					}
					last[0] = now;
				}
			});
		}
		helper.runAfterDelay(132, () -> {
			restore.run();
			int burnt = last[0] - first[0] + 1;
			int expected = (int) Math.round(34 / Math.max(0.2, Config.ELDER_WINDUP_SPEED) * Math.max(0.5, Config.WEAVER_LANCE_LENGTH));
			check(helper, first[0] > 0, "the Lance never fired");
			check(helper, most[0] == 1, "alone, the Lance fired %s beams at once", most[0]);
			check(helper, burnt >= expected - 2, "the Lance burnt %s ticks, not %s", burnt, expected);
			helper.succeed();
		});
	}
}
