package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.elder.ElderSpells;
import com.extremedimensions.elder.KnotMother;
import com.extremedimensions.elder.WeaverService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;

/**
 * The Knot Mother's own (3.23): Draw In hauls whoever keeps away from her in to the knot and bites them; Snarl's knots
 * hold a player fast (no step, no jump) and pick Seam time off their thread; and her cords hold her near where she woke.
 */
public class KnotMotherTests {
	private static void floor(GameTestHelper helper, int x0, int x1, int z0, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
	}

	private static void forceAround(GameTestHelper helper, int r) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				level.setChunkForced((o.getX() >> 4) + dx, (o.getZ() >> 4) + dz, true);
			}
		}
	}

	private static double flat(ServerPlayer p, KnotMother m) {
		double dx = p.getX() - m.getX(), dz = p.getZ() - m.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static KnotMother wake(GameTestHelper helper, ServerPlayer p, BlockPos heart) {
		check(helper, WeaverService.summonKnotMother(p, heart), "the Knot Mother did not come");
		KnotMother m = helper.getLevel().getEntitiesOfClass(KnotMother.class, p.getBoundingBox().inflate(64)).stream().findFirst().orElse(null);
		check(helper, m != null, "the Knot Mother did not come");
		return m;
	}

	private static void rest(KnotMother m) {
		if (m != null && m.isAlive()) {
			WeaverService.fade(m);
			m.discard();
		}
		DataStore.seamCells().removeIf(k -> k.startsWith("km@"));
	}

	/**
	 * Draw In: from sixteen blocks off, her threads catch the player and haul them towards her (a test player is moved by
	 * its client, so the test reads the yank and then sets the player down by her), and she bites them when they arrive.
	 * Then, carried past her leash with the player further off still, her cords draw her back rather than after them.
	 */
	@GameTest(environment = Alone.KNOT_DRAW, maxTicks = 300)
	public void theKnotMotherDrawsInBitesAndKeepsToHerKnot(GameTestHelper helper) {
		forceAround(helper, 4);
		floor(helper, -2, 24, -3, 9);
		ServerPlayer p = TestPlayers.survivor(helper, "knot-drawn");
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 3));
		BlockPos heart = at.offset(16, 0, 0);
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		KnotMother[] m = {null};
		double[] yank = {0}, strayed = {0};
		float[] health = {0};
		boolean[] arrived = {false};
		double attacks = Config.ELDER_ATTACK_SPEED;
		Runnable restore = () -> {
			Config.ELDER_ATTACK_SPEED = attacks;
			rest(m[0]);
		};
		helper.runAfterDelay(5, () -> {
			// No blow but the one forced here (the test watches her cords afterwards, not another blow)
			Config.ELDER_ATTACK_SPEED = 0.1;
			m[0] = wake(helper, p, heart);
			p.setHealth(p.getMaxHealth());
			health[0] = p.getHealth();
			WeaverService.force(m[0], KnotMother.DRAW);
		});
		for (int t = 6; t <= 70; t++) {
			helper.runAfterDelay(t, () -> {
				if (m[0] == null || arrived[0] || WeaverService.drawn(m[0]) < 1) {
					return;
				}
				// The yank: how fast the player is thrown towards her
				double dx = m[0].getX() - p.getX(), dz = m[0].getZ() - p.getZ();
				double d = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
				yank[0] = (p.getDeltaMovement().x * dx + p.getDeltaMovement().z * dz) / d;
				// and where it carries them: in by her, for the bite
				arrived[0] = true;
				p.teleportTo(m[0].getX() + 3, at.getY(), m[0].getZ());
			});
		}
		helper.runAfterDelay(72, () -> {
			try {
				check(helper, arrived[0], "her threads drew no one in");
				check(helper, yank[0] > 1.0, "her threads did not haul the player towards her (%s blocks a tick)", yank[0]);
				check(helper, p.getHealth() < health[0], "she did not bite the player she drew in (health %s)", p.getHealth());
				check(helper, m[0].getAction() != KnotMother.DRAW, "her Draw In never ended");
			} catch (RuntimeException e) {
				restore.run();
				throw e;
			}
			// Carried off past her leash, the player further off still: her cords draw her back, not after them
			p.setHealth(p.getMaxHealth());
			m[0].teleportTo(heart.getX() + 0.5 + 26, m[0].getY(), heart.getZ() + 0.5);
			p.teleportTo(heart.getX() + 0.5 + 40, at.getY(), heart.getZ() + 0.5);
			strayed[0] = WeaverService.strayed(m[0]);
		});
		helper.runAfterDelay(280, () -> {
			double now = WeaverService.strayed(m[0]);
			restore.run();
			check(helper, strayed[0] > Config.KNOT_MOTHER_LEASH, "she was not carried past her leash (%s blocks)", strayed[0]);
			check(helper, now < strayed[0] - 2, "her cords did not draw her back (from %s to %s blocks off)", strayed[0], now);
			helper.succeed();
		});
	}

	/**
	 * Snarl: a knot of hers holds a player fast, no step and no jump, and picks its Seam time off their thread; it gives
	 * after its time. Then, in a fight, her Snarl's knots land under the player and catch them.
	 */
	@GameTest(environment = Alone.KNOT_SNARL, maxTicks = 200)
	public void theKnotMothersSnarlHoldsFastAndTakesTime(GameTestHelper helper) {
		forceAround(helper, 3);
		floor(helper, -2, 16, -3, 9);
		ServerPlayer p = TestPlayers.survivor(helper, "knot-snarled");
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 3));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		PlayerData d = DataStore.get(p.getUUID());
		double seconds = Config.KNOT_MOTHER_SNARL_SECONDS;
		int time = Config.KNOT_MOTHER_SNARL_TIME;
		double attacks = Config.ELDER_ATTACK_SPEED;
		KnotMother[] m = {null};
		boolean[] caught = {false};
		Runnable restore = () -> {
			Config.KNOT_MOTHER_SNARL_SECONDS = seconds;
			Config.KNOT_MOTHER_SNARL_TIME = time;
			Config.ELDER_ATTACK_SPEED = attacks;
			p.getAbilities().invulnerable = false;
			d.inSeam = false;
			d.seamLeft = 0;
			d.seamMax = 0;
			rest(m[0]);
		};
		helper.runAfterDelay(5, () -> {
			Config.KNOT_MOTHER_SNARL_SECONDS = 1.0;
			Config.KNOT_MOTHER_SNARL_TIME = 10;
			d.inSeam = true;
			d.seamMax = 100;
			d.seamLeft = 100;
			WeaverService.snarled(p);
			check(helper, ElderSpells.snarled(p), "the knot did not hold the player");
			check(helper, Math.abs(d.seamLeft - 90) < 1e-6, "the knot left %s seconds of thread, not 90", d.seamLeft);
			d.inSeam = false;
		});
		helper.runAfterDelay(8, () -> {
			double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED), jump = p.getAttributeValue(Attributes.JUMP_STRENGTH);
			if (speed > 1e-6 || jump > 1e-6) {
				restore.run();
			}
			check(helper, speed <= 1e-6, "held fast, the player can still walk (speed %s)", speed);
			check(helper, jump <= 1e-6, "held fast, the player can still jump (jump %s)", jump);
		});
		helper.runAfterDelay(40, () -> {
			double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
			if (ElderSpells.snarled(p) || speed <= 1e-6) {
				restore.run();
			}
			check(helper, !ElderSpells.snarled(p), "the knot never gave");
			check(helper, speed > 1e-6, "the knot gave, but the player still cannot walk");
			// In a fight: her Snarl's knots fall under the player, who stands still in one (kept from harm, and from any
			// blow of hers but this one)
			Config.ELDER_ATTACK_SPEED = 0.1;
			p.getAbilities().invulnerable = true;
			m[0] = wake(helper, p, at.offset(10, 0, 0));
			WeaverService.force(m[0], KnotMother.SNARL);
		});
		for (int t = 41; t <= 180; t++) {
			helper.runAfterDelay(t, () -> {
				caught[0] |= ElderSpells.snarled(p);
			});
		}
		helper.runAfterDelay(182, () -> {
			boolean ended = m[0] != null && m[0].getAction() != KnotMother.SNARL;
			restore.run();
			check(helper, caught[0], "her Snarl's knots never caught the player who stood in them");
			check(helper, ended, "her Snarl never ended");
			helper.succeed();
		});
	}
}
