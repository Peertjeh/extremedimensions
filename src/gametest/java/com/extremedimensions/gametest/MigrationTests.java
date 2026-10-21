package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;

import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.guide.Guide;
import com.extremedimensions.guide.Hints;
import com.extremedimensions.guide.Path;
import com.extremedimensions.lore.FirstWeaver;
import com.extremedimensions.registry.ModItems;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * A world saved by 2.32.1 plays on: its data file, as that version wrote it, is read in place of this world's for the
 * length of one tick (nothing else runs meanwhile, and this world's data is put back before the tick ends).
 */
public class MigrationTests {
	/**
	 * One player well on their way (seven expeditions, three visits to the Seam, a woven Tether, a Hollowling, two curses,
	 * a relic, the Matriarch slain), the seal broken, a Seam half known, a trial claimed: written as 2.32.1 wrote it,
	 * without a single field of the versions after it. {@code PLAYER} stands for the test player's UUID.
	 */
	private static final String WORLD_232 = """
			{
			  "players": {
			    "PLAYER": {
			      "expeditions": 7,
			      "seen": ["lurker", "hollowling", "keeper", "thinned", "veil_ray", "matriarch"],
			      "kills": {"thinned": 4, "matriarch": 1},
			      "deaths": 2, "seamVisits": 3, "frayDescents": 1, "lifted": 0, "wellHints": 1,
			      "tribeRep": 12, "tribeDay": 41, "tribeGifts": 1, "barterUsed": {"2": 1},
			      "tribeForgetIn": 0, "tribeHuntIn": 0, "raidsWon": 1, "bartered": 3, "bondRepDay": -1,
			      "name": "old-wanderer", "shareAnchors": false, "liftsGiven": 0, "liftsTaken": 1, "burdensCarried": 0,
			      "fallenAt": -1, "fallenKnot": false, "guide": 4, "relic": "lifeline", "collapses": 1, "longestRun": 612.5,
			      "curses": {"kindling": 2, "brittle_bones": 1}, "pact": {}, "keepersSlain": 1, "matriarchSlain": true,
			      "trialArmed": false, "trialClaimWeek": 2901, "trialClaimMedal": 2,
			      "atonement": {"kindling": 40}, "atoning": "kindling", "penaltyTime": {},
			      "pendingOffer": [], "pendingMult": [], "diedToday": false, "xpForfeit": false, "pendingPenalties": 0,
			      "owed": [], "linked": [], "runMax": 900.0,
			      "journal": ["tethered", "anchor", "first_wound", "hollows", "seam", "loom", "weavers", "matriarch", "fragment"],
			      "journalDelivered": true, "inSeam": false, "seamLeft": 0.0, "seamMax": 300.0, "homeDim": "overworld",
			      "seamLost": false, "tetherTier": 1, "scars": 2, "companion": true, "companionGiftIn": 300,
			      "tetherAnchor": "overworld|12|64|-30", "furthest": 1480.0, "collapseOwed": false, "fareOwed": 0,
			      "ultimatesOwed": [], "carriedFor": {}, "reliefDay": -1, "reliefs": 0, "runTime": 0.0, "runActive": 0
			    }
			  },
			  "altars": {},
			  "overworldPenalties": false,
			  "hoards": [],
			  "seamCells": ["c@1,0", "c@0,1", "g@3,97,4"],
			  "sealBroken": true,
			  "ending": "",
			  "heartBuilt": false,
			  "starvedSlain": false,
			  "seamSealed": true,
			  "wrecks": [],
			  "trials": {"scores": {}, "lastTop": []}
			}
			""";

	@GameTest
	public void aWorldFrom232PlaysOn(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "old-wanderer");
		Object ours = DataStore.adopt(WORLD_232.replace("PLAYER", p.getUUID().toString()));
		try {
			PlayerData d = DataStore.get(p.getUUID());
			check(helper, d.expeditions == 7 && d.tetherTier == 1 && d.companion && "lifeline".equals(d.relic),
					"the 2.32 player did not come through (%s expeditions, tier %s)", d.expeditions, d.tetherTier);
			check(helper, d.curses.getOrDefault("kindling", 0) == 2 && d.journal.contains("weavers"), "the 2.32 player's curses or pages were lost");
			check(helper, DataStore.sealBroken() && DataStore.seamSealed() && DataStore.seamCells().contains("c@1,0"),
					"the 2.32 world's seal or Seam was lost");
			// Everything the versions since have added is there, empty rather than missing
			for (Field f : PlayerData.class.getFields()) {
				if (Modifier.isTransient(f.getModifiers()) || Modifier.isStatic(f.getModifiers())) {
					continue;
				}
				if (Collection.class.isAssignableFrom(f.getType()) || Map.class.isAssignableFrom(f.getType())) {
					check(helper, f.get(d) != null, "the 2.32 player has no %s", f.getName());
				}
			}
			check(helper, !d.frameStrung && !d.knotReached && !d.heldFound, "the 2.32 player is somewhere they never went");
			// It plays on: the guide is done, the journal's path stands where they are, and they are not told what they know
			check(helper, Guide.progress(d) >= Guide.STEPS, "the 2.32 player's first expedition is not done (%s)", Guide.progress(d));
			check(helper, Path.current(p, d) == Path.Step.SEAM_HOME, "the 2.32 player's next step is %s, not home from the Seam",
					Path.current(p, d));
			Path.encode(p, d);
			Hints.known(p, d);
			check(helper, d.hints.contains(Hints.Hint.CURSE.key()) && d.hints.contains(Hints.Hint.NEEDLE.key())
					&& d.hints.contains(Hints.Hint.ELDER.key()) && !d.hints.contains(Hints.Hint.STORM.key()),
					"the 2.32 player is told %s already", d.hints);
			check(helper, !FirstWeaver.needs(p, d, ModItems.EMBER_THREAD), "the 2.32 player is on a trail they never began");
			// And it is written again as this version writes it, all of it
			check(helper, DataStore.reread(d).expeditions == 7, "the 2.32 player does not survive a save");
		} catch (IllegalAccessException e) {
			throw new AssertionError(e);
		} finally {
			DataStore.putBack(ours);
		}
		helper.succeed();
	}
}
