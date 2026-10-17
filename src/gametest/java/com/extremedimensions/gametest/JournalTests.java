package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.journal.Bestiary;
import com.extremedimensions.journal.Ledger;
import com.extremedimensions.registry.ModEntities;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;

/** The journal's bestiary (seen, slain, great ones for all who fought) and its ledger. */
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
		check(helper, data.startsWith("lurker:0:0;hollowling:1:1;") && data.contains("starved_one:1:1"), "the bestiary reads %s", data);
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
}
