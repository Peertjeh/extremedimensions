package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.config.Config;
import com.extremedimensions.coop.BurdenService;
import com.extremedimensions.coop.FallenService;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.server.Dims;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Co-op: a player struck down beside an ally falls and is lifted up again (or gives in), an ally carries a stack of a
 * curse with consent, and allies draw on an anchor opened to them. While the tests run only players on one team are
 * allies, so no other test's player ever falls instead of dying.
 */
public class CoopTests {
	/** Two allies (one team of their own) standing side by side in the test's space. */
	private static ServerPlayer[] allies(GameTestHelper helper, String name) {
		Config.COOP_ALLIES = 1;
		ServerPlayer a = TestPlayers.survivor(helper, name + "-a"), b = TestPlayers.survivor(helper, name + "-b");
		Scoreboard board = helper.getLevel().getServer().getScoreboard();
		String teamName = name + Integer.toHexString(a.getUUID().hashCode() & 0xFFFF);
		PlayerTeam team = board.getPlayerTeam(teamName) != null ? board.getPlayerTeam(teamName) : board.addPlayerTeam(teamName);
		board.addPlayerToTeam(a.getScoreboardName(), team);
		board.addPlayerToTeam(b.getScoreboardName(), team);
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		a.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		b.teleportTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5);
		return new ServerPlayer[] {a, b};
	}

	@GameTest(maxTicks = 200)
	public void aFallenPlayerIsLiftedUpByAnAlly(GameTestHelper helper) {
		ServerPlayer[] pair = allies(helper, "lift");
		ServerPlayer a = pair[0], b = pair[1];
		ServerLevel level = helper.getLevel();
		var blow = level.damageSources().generic();
		boolean dies = FallenService.allowDeath(a, blow, 1000f);
		check(helper, !dies && FallenService.fallen(a) && a.getHealth() == 1.0F, "struck down beside an ally, the player did not fall");
		check(helper, !FallenService.mayHurt(a, blow), "a fallen player could still be hurt");
		b.setShiftKeyDown(true);
		helper.runAfterDelay((int) Math.round(Config.COOP_LIFT_SECONDS * 20) + 10, () -> {
			b.setShiftKeyDown(false);
			check(helper, !FallenService.fallen(a), "crouching beside them, the ally did not lift them up");
			check(helper, a.getHealth() > 1.0F, "lifted up, they got no health back (%s)", a.getHealth());
			check(helper, DataStore.get(b.getUUID()).liftsGiven == 1, "the lift was not counted");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void aFallenPlayerWhoGivesInDies(GameTestHelper helper) {
		ServerPlayer[] pair = allies(helper, "givein");
		ServerPlayer a = pair[0];
		FallenService.allowDeath(a, helper.getLevel().damageSources().generic(), 1000f);
		check(helper, FallenService.fallen(a), "the player did not fall");
		a.setShiftKeyDown(true);
		helper.runAfterDelay(80, () -> {
			check(helper, !a.isAlive(), "holding sneak, the fallen player did not give in");
			check(helper, !FallenService.fallen(a), "a dead player is still counted as fallen");
			helper.succeed();
		});
	}

	@GameTest
	public void anAllyCarriesAStackOfACurse(GameTestHelper helper) {
		ServerPlayer[] pair = allies(helper, "burden");
		ServerPlayer a = pair[0], b = pair[1];
		Penalty pen = Penalty.ACCELERATED_DRAIN;
		DataStore.get(b.getUUID()).addStacks(pen, 2);
		int toll = Math.max(0, Config.COOP_BURDEN_TOLL);
		BurdenService.offer(a, b.getUUID(), pen.id);
		BurdenService.answer(b, true);
		check(helper, DataStore.get(b.getUUID()).owned(pen) == 1, "the ally still has %s stacks", DataStore.get(b.getUUID()).owned(pen));
		check(helper, DataStore.get(a.getUUID()).owned(pen) == 1 + toll, "the carrier has %s stacks", DataStore.get(a.getUUID()).owned(pen));
		// The same two again, right away: not yet
		BurdenService.offer(a, b.getUUID(), pen.id);
		BurdenService.answer(b, true);
		check(helper, DataStore.get(b.getUUID()).owned(pen) == 1, "a second stack went over before the wait was over");
		// Refused, nothing moves
		ServerPlayer c = TestPlayers.survivor(helper, "burden-c");
		helper.getLevel().getServer().getScoreboard().addPlayerToTeam(c.getScoreboardName(), helper.getLevel().getServer().getScoreboard().getPlayersTeam(a.getScoreboardName()));
		c.teleportTo(a.getX(), a.getY(), a.getZ() + 1);
		BurdenService.offer(c, b.getUUID(), pen.id);
		BurdenService.answer(b, false);
		check(helper, DataStore.get(b.getUUID()).owned(pen) == 1 && DataStore.get(c.getUUID()).owned(pen) == 0, "a refused offer still moved the curse");
		helper.succeed();
	}

	@GameTest
	public void alliesDrawOnAnAnchorOpenedToThem(GameTestHelper helper) {
		ServerPlayer[] pair = allies(helper, "share");
		ServerPlayer a = pair[0], b = pair[1];
		Anchor anchor = new Anchor(0, 0, 0, Dims.NETHER);
		anchor.add(a.getStringUUID(), 100);
		check(helper, anchor.of(b.getStringUUID()) == 0, "a closed anchor lent %s charge", anchor.of(b.getStringUUID()));
		DataStore.get(a.getUUID()).shareAnchors = true;
		check(helper, anchor.of(b.getStringUUID()) == 100, "an opened anchor lent %s of 100", anchor.of(b.getStringUUID()));
		double taken = anchor.take(b.getStringUUID(), 30);
		check(helper, taken == 30 && Math.abs(anchor.of(a.getStringUUID()) - 70) < 1e-6, "drawing 30 took %s and left the owner %s", taken, anchor.of(a.getStringUUID()));
		ServerPlayer stranger = TestPlayers.survivor(helper, "share-x");
		check(helper, anchor.of(stranger.getStringUUID()) == 0, "someone not on the team drew on it");
		DataStore.get(a.getUUID()).shareAnchors = false;
		helper.succeed();
	}
}
