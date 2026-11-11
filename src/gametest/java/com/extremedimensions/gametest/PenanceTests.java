package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;
import static com.extremedimensions.gametest.CurseTests.near;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonObject;

import com.extremedimensions.config.Config;
import com.extremedimensions.config.Preset;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.expedition.ExpeditionService;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.penalty.Wound;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.ServerEvents;
import com.extremedimensions.tether.TetherService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 3.49: penance is harder won. A Mending Cloth pays a share of a stack's atonement rather than lifting it, one in a
 * while, not on the Extreme set, and the stack it helped with leaves no scar; a death leaves a Fresh Wound and undoes
 * more, and the difficulties (and the worlds on them) follow.
 */
public class PenanceTests {
	/** The cloth's rules for a test: its share, its wait (minutes), whether it works on the Extreme set and leaves scars. */
	private static void rules(double share, int wait, int extreme, int scars) {
		Config.CLOTH_SHARE = share;
		Config.CLOTH_COOLDOWN_MINUTES = wait;
		Config.CLOTH_EXTREME = extreme;
		Config.CLOTH_SCARS = scars;
	}

	/** Seconds of play in the mod's worlds, as the server counts them once a second. */
	private static void play(ServerPlayer p, PlayerData d, int seconds) {
		for (int i = 0; i < seconds; i++) {
			PenaltyService.second(p, d);
		}
	}

	/** Atonement for the rest of what the next stack of a curse asks, given back as an echo gives it (no relic adds to it). */
	private static void atoneRest(ServerPlayer p, PlayerData d, Penalty pen) {
		PenaltyService.restore(p, d, false, PenaltyService.cost(d, pen) - d.atonement.getOrDefault(pen.id, 0));
	}

	/** What the tests here leave on a player: curses, atonement, the cloth's wait and marks, a wound and what a death owes. */
	private static void settle(ServerPlayer p, PlayerData d, int scars) {
		d.curses.clear();
		d.atonement.clear();
		d.clothed.clear();
		d.fragmentsOffered.clear();
		d.clothIn = 0;
		d.atoning = null;
		d.scars = scars;
		d.woundIn = 0;
		d.woundBindIn = 0;
		d.owed.clear();
		d.pendingOffer.clear();
		d.pendingMult.clear();
		d.pendingPenalties = 0;
		d.offerDelay = -1;
		CurseEffects.applyEffects(p, d);
	}

	// ------------------------------------------------------------------ the Mending Cloth

	/**
	 * A cloth pays two fifths of what the stack asks and lifts nothing by itself; a second within the hour is refused and
	 * kept; after an hour of play one that completes the stack lifts it, and leaves no scar.
	 */
	@GameTest
	public void aClothPaysTwoFifthsAndWaitsAnHour(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.GLASS_JAW, 2);
		PlayerData d = DataStore.get(p.getUUID());
		double share = Config.CLOTH_SHARE;
		int wait = Config.CLOTH_COOLDOWN_MINUTES, extreme = Config.CLOTH_EXTREME, scarsOn = Config.CLOTH_SCARS, scars = d.scars;
		String id = Penalty.GLASS_JAW.id;
		try {
			rules(0.4, 60, 0, 0);
			d.atoning = id;
			int need = PenaltyService.cost(d, Penalty.GLASS_JAW), part = PenaltyService.clothGain(d, Penalty.GLASS_JAW);
			check(helper, part >= need * 0.4 - 1e-9 && part < need * 0.4 + 1 && part < need, "a cloth pays %s of the %s a stack asks, not two fifths",
					part, need);
			ItemStack cloths = new ItemStack(ModItems.MENDING_CLOTH, 2);
			PenaltyService.cloth(p, cloths);
			check(helper, d.owned(Penalty.GLASS_JAW) == 2 && d.atonement.getOrDefault(id, 0) == part && cloths.getCount() == 1,
					"the cloth left %s stacks and %s atonement (not 2 and %s), %s cloths", d.owned(Penalty.GLASS_JAW), d.atonement.get(id), part,
					cloths.getCount());
			check(helper, d.scars == scars && d.clothed.contains(id), "the cloth gave a scar (%s) or forgot it paid in (%s)", d.scars, d.clothed);
			check(helper, d.clothIn == 60 * 60, "the next cloth waits %s s of play, not an hour", d.clothIn);
			PlayerData back = DataStore.reread(d);
			check(helper, back.clothIn == d.clothIn && back.clothed.contains(id), "the cloth's wait or mark did not survive a restart");

			// A second within the hour: refused, and nothing spent
			PenaltyService.cloth(p, cloths);
			check(helper, cloths.getCount() == 1 && d.atonement.getOrDefault(id, 0) == part && d.clothIn == 60 * 60,
					"a second cloth within the hour was taken (%s cloths, %s atonement, waits %s s)", cloths.getCount(), d.atonement.get(id), d.clothIn);

			// An hour of play later, a cloth that completes the stack takes it off, without a scar
			play(p, d, 60 * 60 - 1);
			check(helper, d.clothIn == 1, "the wait did not count down with play (%s s left)", d.clothIn);
			play(p, d, 1);
			d.atonement.put(id, need - part);
			PenaltyService.cloth(p, cloths);
			check(helper, d.owned(Penalty.GLASS_JAW) == 1 && cloths.isEmpty(), "a cloth completing the stack left %s stacks", d.owned(Penalty.GLASS_JAW));
			check(helper, d.scars == scars, "a stack the cloth helped to lift left a scar (%s, was %s)", d.scars, scars);
			check(helper, !d.clothed.contains(id) && !d.atonement.containsKey(id), "the cloth's mark or its atonement outlived the stack");
		} finally {
			rules(share, wait, extreme, scarsOn);
			settle(p, d, scars);
		}
		helper.succeed();
	}

	/**
	 * Scars are earned by atonement: a stack a cloth paid into leaves none however it comes off, unless cloth_scars; a death
	 * that takes all its atonement takes the cloth's mark too.
	 */
	@GameTest
	public void scarsAreEarnedByAtonement(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.GLASS_JAW, 3);
		PlayerData d = DataStore.get(p.getUUID());
		double share = Config.CLOTH_SHARE, loss = Config.ATONE_DEATH_LOSS;
		int wait = Config.CLOTH_COOLDOWN_MINUTES, extreme = Config.CLOTH_EXTREME, scarsOn = Config.CLOTH_SCARS, scars = d.scars;
		String id = Penalty.GLASS_JAW.id;
		try {
			rules(0.4, 0, 0, 0);
			d.atoning = id;
			ItemStack cloths = new ItemStack(ModItems.MENDING_CLOTH, 4);
			// A cloth's share, the rest atoned for: the stack comes off without a scar
			PenaltyService.cloth(p, cloths);
			atoneRest(p, d, Penalty.GLASS_JAW);
			check(helper, d.owned(Penalty.GLASS_JAW) == 2 && d.scars == scars, "atonement finishing a stack the cloth paid into left %s stacks, %s scars",
					d.owned(Penalty.GLASS_JAW), d.scars);
			// A cloth's share a death takes all of: the next stack, atoned for alone, leaves its scar
			PenaltyService.cloth(p, cloths);
			Config.ATONE_DEATH_LOSS = 1;
			PenaltyService.deathToll(d);
			check(helper, !d.atonement.containsKey(id) && !d.clothed.contains(id), "a death that took all the atonement left the cloth's mark");
			atoneRest(p, d, Penalty.GLASS_JAW);
			check(helper, d.owned(Penalty.GLASS_JAW) == 1 && d.scars == scars + 1, "a stack atoned for alone left %s stacks, %s scars (was %s)",
					d.owned(Penalty.GLASS_JAW), d.scars, scars);
			// With cloth_scars on, a stack the cloth helped to lift leaves its scar too
			Config.CLOTH_SCARS = 1;
			d.atonement.put(id, PenaltyService.cost(d, Penalty.GLASS_JAW) - PenaltyService.clothGain(d, Penalty.GLASS_JAW));
			PenaltyService.cloth(p, cloths);
			check(helper, d.owned(Penalty.GLASS_JAW) == 0 && d.scars == scars + 2, "with cloth_scars on the cloth's stack left %s stacks, %s scars",
					d.owned(Penalty.GLASS_JAW), d.scars);
		} finally {
			rules(share, wait, extreme, scarsOn);
			Config.ATONE_DEATH_LOSS = loss;
			settle(p, d, scars);
		}
		helper.succeed();
	}

	/** An Extreme curse takes no cloth (and the cloth is kept, its wait not begun), unless cloth_extreme is on. */
	@GameTest
	public void theExtremeSetTakesNoClothUnlessAllowed(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.UNHEALING, 1);
		PlayerData d = DataStore.get(p.getUUID());
		double share = Config.CLOTH_SHARE;
		int wait = Config.CLOTH_COOLDOWN_MINUTES, extreme = Config.CLOTH_EXTREME, scarsOn = Config.CLOTH_SCARS, scars = d.scars;
		String id = Penalty.UNHEALING.id;
		try {
			rules(0.4, 60, 0, 0);
			d.atoning = id;
			ItemStack cloths = new ItemStack(ModItems.MENDING_CLOTH, 2);
			PenaltyService.cloth(p, cloths);
			check(helper, cloths.getCount() == 2 && d.atonement.getOrDefault(id, 0) == 0 && d.clothIn == 0,
					"an Extreme curse took a cloth (%s left, %s atonement, waits %s s)", cloths.getCount(), d.atonement.get(id), d.clothIn);
			Config.CLOTH_EXTREME = 1;
			PenaltyService.cloth(p, cloths);
			check(helper, cloths.getCount() == 1 && d.owned(Penalty.UNHEALING) == 1
					&& d.atonement.getOrDefault(id, 0) == PenaltyService.clothGain(d, Penalty.UNHEALING),
					"with cloth_extreme on the cloth paid %s into an Extreme curse, not %s", d.atonement.get(id), PenaltyService.clothGain(d, Penalty.UNHEALING));
		} finally {
			rules(share, wait, extreme, scarsOn);
			settle(p, d, scars);
		}
		helper.succeed();
	}

	/** With a share of 1, no wait and both switches on, a cloth lifts a stack at once with its scar, as it used to. */
	@GameTest
	public void theOldClothLiftsAStackAtOnce(GameTestHelper helper) {
		ServerPlayer p = cursed(helper, Penalty.GLASS_JAW, 2, Penalty.UNHEALING, 1, Penalty.SUNSCORN, 1);
		PlayerData d = DataStore.get(p.getUUID());
		double share = Config.CLOTH_SHARE;
		int wait = Config.CLOTH_COOLDOWN_MINUTES, extreme = Config.CLOTH_EXTREME, scarsOn = Config.CLOTH_SCARS, scars = d.scars;
		int abyssal = Config.ABYSSAL_MENDING_CLOTH;
		try {
			rules(1, 0, 1, 1);
			Config.ABYSSAL_MENDING_CLOTH = 0;
			ItemStack cloths = new ItemStack(ModItems.MENDING_CLOTH, 5);
			d.atoning = Penalty.GLASS_JAW.id;
			PenaltyService.cloth(p, cloths);
			check(helper, d.owned(Penalty.GLASS_JAW) == 1 && d.scars == scars + 1 && d.clothIn == 0, "the old cloth left %s stacks, %s scars, a wait of %s s",
					d.owned(Penalty.GLASS_JAW), d.scars, d.clothIn);
			PenaltyService.cloth(p, cloths);
			check(helper, d.owned(Penalty.GLASS_JAW) == 0 && d.scars == scars + 2, "a second old cloth at once left %s stacks, %s scars",
					d.owned(Penalty.GLASS_JAW), d.scars);
			d.atoning = Penalty.UNHEALING.id;
			PenaltyService.cloth(p, cloths);
			check(helper, d.owned(Penalty.UNHEALING) == 0, "the old cloth did not lift an Extreme curse");
			// The Abyssal rule stays as it was
			d.atoning = Penalty.SUNSCORN.id;
			PenaltyService.cloth(p, cloths);
			check(helper, d.owned(Penalty.SUNSCORN) == 1 && cloths.getCount() == 2, "the old cloth took an Abyssal curse");
		} finally {
			rules(share, wait, extreme, scarsOn);
			Config.ABYSSAL_MENDING_CLOTH = abyssal;
			settle(p, d, scars);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the Fresh Wound

	/** The drain an expedition in the Nether runs at now, fed by the given anchor, from where it began. */
	private static double drainNow(ServerPlayer p, PlayerData d, String key) {
		d.inRun = true;
		d.collapsing = false;
		d.linked.clear();
		d.linked.add(key);
		d.arrivalX = p.getX();
		d.arrivalZ = p.getZ();
		ExpeditionService.tick(p, d, Dims.NETHER);
		d.inRun = false;
		d.linked.clear();
		return d.currentDrain;
	}

	/**
	 * A death leaves a Fresh Wound: two hearts less, the Tether draining a quarter faster, no Tether bound for three
	 * minutes; a second death starts it over without cutting deeper, and ten minutes of play close it.
	 */
	@GameTest
	public void aDeathLeavesAFreshWound(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-fresh-wound");
		PlayerData d = DataStore.get(p.getUUID());
		int minutes = Config.DEATH_WOUND_MINUTES, bind = Config.DEATH_WOUND_BIND_MINUTES, scars = d.scars;
		double health = Config.DEATH_WOUND_HEALTH, drain = Config.DEATH_WOUND_DRAIN;
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		String key = Anchor.key(at.getX(), at.getY(), at.getZ());
		Anchor anchor = new Anchor(at.getX(), at.getY(), at.getZ(), Dims.NETHER);
		anchor.add(p.getStringUUID(), 1000);
		DataStore.anchors().put(key, anchor);
		try {
			Config.DEATH_WOUND_MINUTES = 10;
			Config.DEATH_WOUND_HEALTH = 4;
			Config.DEATH_WOUND_DRAIN = 1.25;
			Config.DEATH_WOUND_BIND_MINUTES = 3;
			float full = p.getMaxHealth();
			check(helper, !Wound.active(d) && Wound.drainFactor(d) == 1.0, "a player who never died is wounded");
			ExpeditionService.onDeath(p);
			ExpeditionService.onRespawn(p, d);
			check(helper, d.woundIn == 600 && d.woundBindIn == 180, "a death left a wound of %s s (no binding for %s s), not 600 (180)", d.woundIn,
					d.woundBindIn);
			near(helper, p.getMaxHealth(), full - 4, "maximum health with a fresh wound");
			near(helper, Wound.drainFactor(d), 1.25, "the drain multiplier of a fresh wound");
			double wounded = drainNow(p, d, key);
			d.woundIn = 0;
			double plain = drainNow(p, d, key);
			d.woundIn = 600;
			check(helper, Math.abs(wounded - plain * 1.25) < 1e-6, "an expedition drained %s wounded and %s without, not a quarter faster", wounded, plain);
			PlayerData back = DataStore.reread(d);
			check(helper, back.woundIn == 600 && back.woundBindIn == 180, "the wound did not survive a restart (%s, %s)", back.woundIn, back.woundBindIn);

			// No Tether binds to an anchor for its first three minutes
			ItemStack tether = new ItemStack(ModItems.TETHER);
			TetherService.tie(p, d, at, tether);
			check(helper, d.tetherAnchor == null && tether.getCount() == 1, "a Tether bound to an anchor right after a death");
			play(p, d, 179);
			TetherService.tie(p, d, at, tether);
			check(helper, d.tetherAnchor == null && tether.getCount() == 1, "a Tether bound a second before the three minutes were up");
			play(p, d, 1);
			TetherService.tie(p, d, at, tether);
			check(helper, key.equals(d.tetherAnchor) && tether.isEmpty(), "three minutes after the death the Tether still did not bind");
			TetherService.clear(p, d, false);

			// A death while wounded starts it over, and cuts no deeper
			play(p, d, 100);
			ExpeditionService.onDeath(p);
			ExpeditionService.onRespawn(p, d);
			check(helper, d.woundIn == 600 && d.woundBindIn == 180, "a second death left a wound of %s s, not a fresh 600", d.woundIn);
			near(helper, p.getMaxHealth(), full - 4, "maximum health after a second death while wounded");

			// Ten minutes of play close it
			play(p, d, 599);
			check(helper, Wound.active(d), "the wound closed a second early");
			play(p, d, 1);
			check(helper, !Wound.active(d) && Wound.drainFactor(d) == 1.0, "ten minutes of play did not close the wound (%s s left)", d.woundIn);
			near(helper, p.getMaxHealth(), full, "maximum health once the wound closed");
		} finally {
			Config.DEATH_WOUND_MINUTES = minutes;
			Config.DEATH_WOUND_HEALTH = health;
			Config.DEATH_WOUND_DRAIN = drain;
			Config.DEATH_WOUND_BIND_MINUTES = bind;
			TetherService.clear(p, d, false);
			DataStore.anchors().remove(key);
			d.inRun = false;
			d.linked.clear();
			settle(p, d, scars);
		}
		helper.succeed();
	}

	/** A death in another mod's world leaves no wound; one already open sleeps there, as the curses do. */
	@GameTest
	public void aDeathInAnotherModsWorldLeavesNoWound(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-wound-away");
		PlayerData d = DataStore.get(p.getUUID());
		double health = Config.DEATH_WOUND_HEALTH;
		int minutes = Config.DEATH_WOUND_MINUTES, scars = d.scars;
		float full = p.getMaxHealth();
		ServerEvents.setAway(p, d, true);
		try {
			Config.DEATH_WOUND_HEALTH = 4;
			Config.DEATH_WOUND_MINUTES = 10;
			ExpeditionService.onDeath(p);
			check(helper, !Wound.active(d) && d.woundIn == 0, "a death in another mod's world left a wound of %s s", d.woundIn);
			Wound.open(d);
			CurseEffects.applyEffects(p, d);
			near(helper, p.getMaxHealth(), full, "maximum health with a wound asleep in another mod's world");
			ServerEvents.setAway(p, d, false);
			near(helper, p.getMaxHealth(), full - 4, "maximum health with the wound awake again");
		} finally {
			d.diedAway = false;
			ServerEvents.setAway(p, d, false);
			Config.DEATH_WOUND_HEALTH = health;
			Config.DEATH_WOUND_MINUTES = minutes;
			settle(p, d, scars);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the difficulties and the upgrade

	/** What every difficulty asks of a death and of atonement (3.49); the cloth's rules are the same on all of them. */
	@GameTest
	public void theDifficultiesPriceADeath(GameTestHelper helper) {
		String[] keys = {"atone_base", "atone_per_stack", "atone_death_loss", "multiplier_chance", "death_wound_minutes", "death_wound_health",
				"death_wound_drain", "death_wound_bind_minutes"};
		Map<Preset, String[]> want = Map.of(Preset.EASY, new String[] {"80", "35", "0.25", "0.05", "5", "2", "1.1", "0"},
				Preset.NORMAL, new String[] {"160", "80", "0.75", "0.25", "10", "4", "1.25", "3"},
				Preset.HARD, new String[] {"200", "100", "0.85", "0.32", "15", "6", "1.35", "5"},
				Preset.EXTREME, new String[] {"260", "130", "0.95", "0.4", "20", "8", "1.5", "8"});
		Map<String, String> defaults = new LinkedHashMap<>();
		for (Config.Setting st : Config.settings()) {
			defaults.put(st.key(), st.fallback());
		}
		for (Preset p : Preset.values()) {
			for (int i = 0; i < keys.length; i++) {
				String have = p.values.get(keys[i]);
				check(helper, have != null && Double.parseDouble(have) == Double.parseDouble(want.get(p)[i]), "%s has %s = %s, not %s", p.id, keys[i], have,
						want.get(p)[i]);
				if (p == Preset.NORMAL) {
					check(helper, Double.parseDouble(defaults.get(keys[i])) == Double.parseDouble(want.get(p)[i]), "the default of %s is %s, not Normal's %s",
							keys[i], defaults.get(keys[i]), want.get(p)[i]);
				}
			}
			for (String cloth : new String[] {"cloth_share", "cloth_cooldown_minutes", "cloth_extreme", "cloth_scars"}) {
				check(helper, !p.values.containsKey(cloth), "%s sets %s, which is the same on every difficulty", p.id, cloth);
			}
		}
		check(helper, Config.isSwitch("cloth_extreme") && Config.isSwitch("cloth_scars") && !Config.isSwitch("cloth_share"),
				"the cloth's switches are not switches");
		helper.succeed();
	}

	/** The values a settings file holds, over the defaults for any it lacks, as text by key (as the difficulties read them). */
	private static Map<String, String> held(JsonObject json) {
		Map<String, String> out = new LinkedHashMap<>();
		for (Config.Setting st : Config.settings()) {
			out.put(st.key(), json.has(st.key()) ? json.get(st.key()).getAsString() : st.fallback());
		}
		return out;
	}

	/**
	 * A file from before 3.49 still holding the old defaults takes the new ones and stays on Normal; a value a world chose
	 * stays; a world on Hard takes Hard's new numbers and its wound; an up-to-date file is left as it is.
	 */
	@GameTest
	public void olderWorldsTakeTheDearerDeath(GameTestHelper helper) {
		JsonObject normal = new JsonObject(), chosen = new JsonObject(), current = new JsonObject();
		for (JsonObject j : new JsonObject[] {normal, chosen, current}) {
			j.addProperty("config_version", 10);
			j.addProperty("atone_base", 120);
			j.addProperty("atone_per_stack", 60);
			j.addProperty("atone_death_loss", 0.5);
			j.addProperty("multiplier_chance", 0.15);
		}
		chosen.addProperty("atone_base", 130);
		current.addProperty("config_version", 11);
		Config.migrate(normal);
		Config.migrate(chosen);
		Config.migrate(current);
		check(helper, normal.get("atone_base").getAsInt() == 160 && normal.get("atone_per_stack").getAsInt() == 80
				&& normal.get("atone_death_loss").getAsDouble() == 0.75 && normal.get("multiplier_chance").getAsDouble() == 0.25,
				"the old defaults did not follow the new ones: %s", normal);
		check(helper, Preset.matching(held(normal)) == Preset.NORMAL, "a Normal world is no longer Normal after the update: %s", normal);
		check(helper, chosen.get("atone_base").getAsInt() == 130 && chosen.get("atone_per_stack").getAsInt() == 80
				&& chosen.get("atone_death_loss").getAsDouble() == 0.75 && !chosen.has("death_wound_minutes"),
				"a world's own atonement was overwritten, or its other old defaults kept: %s", chosen);
		check(helper, current.get("atone_base").getAsInt() == 120 && current.get("atone_death_loss").getAsDouble() == 0.5,
				"values chosen in an up-to-date file were changed: %s", current);

		// A world on Hard before 3.49 takes Hard's new numbers and its wound, and stays on Hard
		Map<String, String> before = Map.of("atone_base", "150", "atone_per_stack", "75", "atone_death_loss", "0.65", "multiplier_chance", "0.22");
		JsonObject hard = new JsonObject();
		hard.addProperty("config_version", 10);
		for (var e : Preset.HARD.values.entrySet()) {
			if (!e.getKey().startsWith("death_wound_")) {
				hard.addProperty(e.getKey(), Double.parseDouble(before.getOrDefault(e.getKey(), e.getValue())));
			}
		}
		Config.migrate(hard);
		check(helper, hard.get("atone_base").getAsInt() == 200 && hard.get("atone_per_stack").getAsInt() == 100
				&& hard.get("atone_death_loss").getAsDouble() == 0.85 && hard.get("multiplier_chance").getAsDouble() == 0.32
				&& hard.get("death_wound_minutes").getAsInt() == 15 && hard.get("death_wound_health").getAsDouble() == 6
				&& hard.get("death_wound_drain").getAsDouble() == 1.35 && hard.get("death_wound_bind_minutes").getAsInt() == 5,
				"a Hard world did not take Hard's new numbers: %s", hard);
		check(helper, Preset.matching(held(hard)) == Preset.HARD, "a Hard world is no longer Hard after the update: %s", hard);
		helper.succeed();
	}
}
