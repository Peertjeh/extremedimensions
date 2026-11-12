package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.AnchorList;
import com.extremedimensions.journal.Bestiary;
import com.extremedimensions.journal.Ledger;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.server.Dims;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** The journal's bestiary (seen, slain, great ones for all who fought), its ledger and its anchors. */
public class JournalTests {
	@GameTest
	public void theBestiaryWritesInWhatWasSlain(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "journal-hunter");
		PlayerData d = DataStore.get(p.getUUID());
		LivingEntity young = ModEntities.HOLLOWLING.create(helper.getLevel(), EntitySpawnReason.EVENT);
		check(helper, young != null && Bestiary.of(young) == Bestiary.Beast.HOLLOWLING, "a Hollowling is not in the bestiary");
		Bestiary.slain(young, p);
		check(helper, d.seen.contains("hollowling") && d.kills.getOrDefault("hollowling", 0) == 1, "the slain Hollowling was not written in (%s, %s)", d.seen, d.kills);
		LivingEntity keeper = ModEntities.KEEPER.create(helper.getLevel(), EntitySpawnReason.EVENT);
		LivingEntity starved = ModEntities.STARVED_ONE.create(helper.getLevel(), EntitySpawnReason.EVENT);
		check(helper, Bestiary.of(keeper) == Bestiary.Beast.KEEPER && Bestiary.of(starved) == Bestiary.Beast.STARVED_ONE,
				"a Keeper or the Starved One passes for something else (%s, %s)", Bestiary.of(keeper), Bestiary.of(starved));
		// A great one counts for everyone near who fought it, whoever struck last
		starved.setPos(p.getX() + 3, p.getY(), p.getZ());
		Bestiary.slain(starved, null);
		check(helper, d.kills.getOrDefault("starved_one", 0) == 1, "the Starved One slain beside the player was not counted for them");
		String data = Bestiary.encode(d);
		check(helper, data.startsWith("lurker:0:0;cave_lurker:0:0;sculk_lurker:0:0;magpie:0:0;wick:0:0;tidewalker:0:0;gatebreaker:0:0;lockwarden:0:0;threadbiter:0:0;edgecrawler:0:0;cragback:0:0;hollowling:1:1;") && data.contains("starved_one:1:1"), "the bestiary reads %s", data);
		helper.succeed();
	}

	@GameTest
	public void theLedgerCountsTheRoad(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "journal-ledger");
		PlayerData d = DataStore.get(p.getUUID());
		d.expeditions = 7;
		d.seamVisits = 3;
		d.frayDescents = 1;
		d.tribeRep = 45;
		String data = Ledger.encode(d);
		check(helper, data.contains("road:expeditions=7") && data.contains("road:seam_visits=3") && data.contains("road:fray_descents=1"),
				"the ledger's road reads %s", data);
		check(helper, data.contains("others:standing=trusted/45"), "the ledger's standing reads %s", data);
		helper.succeed();
	}

	/**
	 * The journal's anchors: the player's own, cracked, with its charge; one an ally opened to them, with the ally's name;
	 * and one that keeps only someone else's charge, left out.
	 */
	@GameTest(environment = Alone.JOURNAL_ANCHORS)
	public void theJournalListsTheAnchorsThatKeepCharge(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "journal-anchors"), ally = TestPlayers.survivor(helper, "journal-lender");
		// (on one team, so the two are allies whatever coop_allies says)
		Scoreboard board = helper.getLevel().getServer().getScoreboard();
		String teamName = "journal" + Integer.toHexString(p.getUUID().hashCode() & 0xFFFF);
		PlayerTeam team = board.getPlayerTeam(teamName) != null ? board.getPlayerTeam(teamName) : board.addPlayerTeam(teamName);
		board.addPlayerToTeam(p.getScoreboardName(), team);
		board.addPlayerToTeam(ally.getScoreboardName(), team);
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		Anchor own = new Anchor(at.getX(), at.getY(), at.getZ(), Dims.NETHER);
		Anchor lent = new Anchor(at.getX() + 2, at.getY(), at.getZ(), Dims.END);
		Anchor other = new Anchor(at.getX() + 4, at.getY(), at.getZ(), Dims.NETHER);
		PlayerData lender = DataStore.get(ally.getUUID());
		boolean opened = lender.shareAnchors;
		int sharing = Config.COOP_SHARED_ANCHORS;
		for (Anchor a : new Anchor[] {own, lent, other}) {
			DataStore.anchors().put(a.key(), a);
		}
		try {
			Config.COOP_SHARED_ANCHORS = 1;
			own.add(p.getStringUUID(), 754);
			own.unstable = 90;
			lent.add(ally.getStringUUID(), 300);
			lender.shareAnchors = true;
			other.add(java.util.UUID.randomUUID().toString(), 500);
			String data = AnchorList.encode(p);
			String[] f = data.split("\\|", -1);
			check(helper, f.length == 4 && "0".equals(f[0]) && Integer.parseInt(f[2]) >= 2, "the journal's anchors read %s", data);
			java.util.List<String> entries = java.util.Arrays.asList(f[3].split(";"));
			String where = "," + at.getY() + "," + at.getZ() + ",";
			check(helper, entries.contains("nether," + at.getX() + where + "754,754,90,,0"), "the player's own cracked anchor, 12:34 in it, is not in %s", data);
			check(helper, entries.contains("end," + (at.getX() + 2) + where + "300,0,0," + ally.getScoreboardName() + ",0"),
					"the anchor an ally opened to the player, with the ally's name, is not in %s", data);
			check(helper, entries.stream().noneMatch(e -> e.startsWith("nether," + (at.getX() + 4) + where)),
					"an anchor that keeps only someone else's charge is listed: %s", data);
		} finally {
			lender.shareAnchors = opened;
			Config.COOP_SHARED_ANCHORS = sharing;
			for (Anchor a : new Anchor[] {own, lent, other}) {
				DataStore.anchors().remove(a.key());
			}
		}
		helper.succeed();
	}
}
