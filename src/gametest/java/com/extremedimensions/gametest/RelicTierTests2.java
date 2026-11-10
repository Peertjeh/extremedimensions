package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;
import static com.extremedimensions.gametest.CurseTests.cursed;
import static com.extremedimensions.gametest.CurseTests.near;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.anchor.AnchorService;
import com.extremedimensions.binding.Charms;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.expedition.ExpeditionService;
import com.extremedimensions.lurker.CaveLurkerService;
import com.extremedimensions.lurker.LurkerService;
import com.extremedimensions.lurker.MagpieService;
import com.extremedimensions.lurker.SculkLurkerService;
import com.extremedimensions.penalty.CurseEffects;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.relic.Relic;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.server.Dims;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The eight relics that came after the tiers give what they promise and take their price, and they sleep in another
 * mod's world like the rest; and relics serve once (relic_single_use: bound until another takes their place), or come
 * back as before with it off. Every setting
 * a test changes is put back.
 */
public class RelicTierTests2 {
	private static void place(GameTestHelper helper, ServerPlayer p, double x, double y, double z) {
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.snapTo(at.x, at.y, at.z, 0f, 0f);
	}

	/** A plain hurt of the game's own kind, by its id (minecraft:lava, minecraft:freeze, ...). */
	private static DamageSource hurt(ServerLevel level, String id) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
				.getOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("minecraft", id))), (Entity) null, (Entity) null);
	}

	/** Nothing owed any more (a test that owes curses leaves no offer waiting for its player). */
	private static void settle(PlayerData d) {
		d.pendingOffer = new ArrayList<>();
		d.pendingMult = new ArrayList<>();
		d.owed = new ArrayList<>();
		d.pendingPenalties = 0;
	}

	private static void empty(ServerPlayer p) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			p.getInventory().setItem(i, ItemStack.EMPTY);
		}
	}

	/** How many of an item a player carries. */
	private static int carried(ServerPlayer p, Item item) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).is(item)) {
				n += p.getInventory().getItem(i).getCount();
			}
		}
		return n;
	}

	/** How many of an item lie on the ground close by this entity (taken up as they are counted). */
	private static int dropped(Entity at, Item item) {
		int n = 0;
		for (ItemEntity e : at.level().getEntitiesOfClass(ItemEntity.class, at.getBoundingBox().inflate(3), i -> i.getItem().is(item))) {
			n += e.getItem().getCount();
			e.discard();
		}
		return n;
	}

	private static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> nightVision() {
		return BuiltInRegistries.MOB_EFFECT.get(Identifier.fromNamespaceAndPath("minecraft", "night_vision")).orElseThrow();
	}

	// ------------------------------------------------------------------ common

	@GameTest
	public void theAshenLocketShieldsFromFireAndBaresToTheCold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		try {
			d.relic = Relic.ASHEN_LOCKET.id;
			for (String fire : new String[] {"in_fire", "on_fire", "lava"}) {
				near(helper, CurseEffects.damageTaken(p, hurt(level, fire), 10f), 10 * Config.RELIC_ASHEN_LOCKET_FIRE, fire + " with the Ashen Locket");
			}
			near(helper, CurseEffects.damageTaken(p, hurt(level, "freeze"), 10f), 10 * Config.RELIC_ASHEN_LOCKET_COLD, "freezing with the Ashen Locket");
			near(helper, CurseEffects.damageTaken(p, hurt(level, "drown"), 10f), 10 * Config.RELIC_ASHEN_LOCKET_COLD, "drowning with the Ashen Locket");
			near(helper, CurseEffects.damageTaken(p, level.damageSources().fall(), 10f), 10, "a fall with the Ashen Locket");
			near(helper, CurseEffects.damageTaken(p, level.damageSources().generic(), 10f), 10, "a plain hurt with the Ashen Locket");
			d.relic = null;
			near(helper, CurseEffects.damageTaken(p, hurt(level, "lava"), 10f), 10, "lava without the Ashen Locket");
			near(helper, CurseEffects.damageTaken(p, hurt(level, "drown"), 10f), 10, "drowning without the Ashen Locket");
		} finally {
			d.relic = null;
		}
		helper.succeed();
	}

	@GameTest
	public void theMinersCandleLightsTheDeepAndCallsTheCaveLurkers(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		place(helper, p, 3.5, 0, 3.5);
		var nv = nightVision();
		int depth = Config.RELIC_MINERS_CANDLE_DEPTH;
		double chance = Config.CAVE_LURKER_SPAWN_CHANCE;
		try {
			d.relic = Relic.MINERS_CANDLE.id;
			p.removeEffect(nv);
			// Below its depth, but under the open sky: no light
			Config.RELIC_MINERS_CANDLE_DEPTH = (int) Math.floor(p.getY()) + 10;
			d.tickCounter = 0;
			RelicService.tick(p, d);
			check(helper, !p.hasEffect(nv), "the Miner's Candle gave Night Vision under the open sky");
			// Under rock: Night Vision, long enough never to flicker, and refreshed every second
			for (int x = 1; x <= 5; x++) {
				for (int z = 1; z <= 5; z++) {
					helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
				}
			}
			d.tickCounter = 0;
			RelicService.tick(p, d);
			MobEffectInstance lit = p.getEffect(nv);
			check(helper, lit != null && lit.getDuration() > 200 && lit.isAmbient(), "under rock the Miner's Candle gave %s", lit);
			// Above its depth: the light goes out
			Config.RELIC_MINERS_CANDLE_DEPTH = (int) Math.floor(p.getY()) - 1;
			d.tickCounter = 0;
			RelicService.tick(p, d);
			check(helper, !p.hasEffect(nv), "the Miner's Candle's light stayed above its depth");
			// A potion's Night Vision is never touched
			p.addEffect(new MobEffectInstance(nv, 3600, 0));
			d.tickCounter = 0;
			RelicService.tick(p, d);
			MobEffectInstance potion = p.getEffect(nv);
			check(helper, potion != null && potion.getDuration() > 3000 && !potion.isAmbient(), "the Miner's Candle took a potion's Night Vision: %s", potion);
			p.removeEffect(nv);
			// Taken off, its light goes out with it
			Config.RELIC_MINERS_CANDLE_DEPTH = (int) Math.floor(p.getY()) + 10;
			d.tickCounter = 0;
			RelicService.tick(p, d);
			check(helper, p.hasEffect(nv), "the Miner's Candle gave no Night Vision under rock again");
			d.relic = null;
			CurseEffects.applyEffects(p, d);
			check(helper, !p.hasEffect(nv), "the Miner's Candle's light outlasted it on the Tether");
			// Its price: the Cave Lurkers come half again as often
			Config.CAVE_LURKER_SPAWN_CHANCE = 0.2;
			near(helper, CaveLurkerService.chance(p), 0.2, "a Cave Lurker's chance without the Miner's Candle");
			d.relic = Relic.MINERS_CANDLE.id;
			near(helper, CaveLurkerService.chance(p), 0.2 * Config.RELIC_MINERS_CANDLE_CAVE_LURKER, "a Cave Lurker's chance with the Miner's Candle");
		} finally {
			Config.RELIC_MINERS_CANDLE_DEPTH = depth;
			Config.CAVE_LURKER_SPAWN_CHANCE = chance;
			d.relic = null;
			p.removeEffect(nv);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ uncommon

	/** A chicken always leaves one raw chicken: twice the loot is two. */
	private static Mob chicken(GameTestHelper helper, int x, int z, EntitySpawnReason reason) {
		return (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("minecraft", "chicken"))
				.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(x, 2, z)), reason);
	}

	/** The player slays the creature with a blow; the raw chicken it left. */
	private static int slay(ServerPlayer p, Mob m) {
		m.hurtServer((ServerLevel) m.level(), p.damageSources().playerAttack(p), 1000f);
		return dropped(m, Items.CHICKEN);
	}

	@GameTest(environment = Alone.MAGPIES_GLINT)
	public void theMagpiesGlintDoublesWildLootAndCallsTheMagpies(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		place(helper, p, 1.5, 2, 1.5);
		double glint = Config.RELIC_MAGPIES_GLINT_CHANCE, magpie = Config.MAGPIE_SPAWN_CHANCE;
		List<Mob> mobs = new ArrayList<>();
		try {
			Config.RELIC_MAGPIES_GLINT_CHANCE = 1.0;
			d.relic = Relic.MAGPIES_GLINT.id;
			// A wild creature: its loot twice
			Mob wild = chicken(helper, 4, 2, EntitySpawnReason.NATURAL);
			mobs.add(wild);
			check(helper, wild != null && com.extremedimensions.penalty.AtonementGuard.natural(wild), "a natural chicken was not taken for wild");
			check(helper, RelicService.lootTwice(wild, p.damageSources().playerAttack(p)), "the Magpie's Glint would not double a wild chicken's loot");
			int twice = slay(p, wild);
			check(helper, twice == 2, "a wild chicken slain by the Magpie's Glint's bearer left %s raw chicken, not 2", twice);
			// One from a spawner: once, as ever
			Mob farmed = chicken(helper, 4, 5, EntitySpawnReason.SPAWNER);
			mobs.add(farmed);
			check(helper, !RelicService.lootTwice(farmed, p.damageSources().playerAttack(p)), "the Magpie's Glint would double a spawner's chicken's loot");
			int once = slay(p, farmed);
			check(helper, once == 1, "a spawner's chicken slain by the Magpie's Glint's bearer left %s raw chicken, not 1", once);
			// Never a player's loot, nor anything without the relic bound
			check(helper, !RelicService.lootTwice(cursed(helper), p.damageSources().playerAttack(p)), "the Magpie's Glint would double a player's things");
			d.relic = null;
			Mob plain = chicken(helper, 6, 2, EntitySpawnReason.NATURAL);
			mobs.add(plain);
			check(helper, !RelicService.lootTwice(plain, p.damageSources().playerAttack(p)), "a wild chicken's loot was doubled without the Magpie's Glint");
			int alone = slay(p, plain);
			check(helper, alone == 1, "a wild chicken slain without the Magpie's Glint left %s raw chicken, not 1", alone);
			// Its price: Magpies come for the bearer's chests twice as often
			Config.MAGPIE_SPAWN_CHANCE = 0.1;
			near(helper, MagpieService.chance(p), 0.1, "a Magpie's chance without the Magpie's Glint");
			d.relic = Relic.MAGPIES_GLINT.id;
			near(helper, MagpieService.chance(p), 0.1 * Config.RELIC_MAGPIES_GLINT_MAGPIE, "a Magpie's chance with the Magpie's Glint");
		} finally {
			Config.RELIC_MAGPIES_GLINT_CHANCE = glint;
			Config.MAGPIE_SPAWN_CHANCE = magpie;
			d.relic = null;
			for (Mob m : mobs) {
				if (m != null) {
					dropped(m, Items.FEATHER);
					dropped(m, Items.CHICKEN);
					m.discard();
				}
			}
		}
		helper.succeed();
	}

	@GameTest
	public void theAnchorRivetFeedsAnchorsAndWhetsTheLurkersThirst(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		try {
			double plain = AnchorService.worth(d);
			d.relic = Relic.ANCHOR_RIVET.id;
			near(helper, AnchorService.worth(d), plain * Config.RELIC_ANCHOR_RIVET_FUEL, "what fuel is worth with the Anchor Rivet");
			// (anchors of the test's own, never put in the world's store)
			Anchor mine = new Anchor(0, -1000, 0, Dims.NETHER), theirs = new Anchor(0, -1000, 1, Dims.NETHER);
			mine.add(p.getStringUUID(), 100);
			theirs.add("someone-else", 100);
			double sip = Config.LURKER_DRAIN_PER_SECOND / 20.0;
			near(helper, LurkerService.sip(p, mine), sip * Config.RELIC_ANCHOR_RIVET_DRINK, "a Lurker's drink from the Anchor Rivet's bearer's anchor");
			near(helper, LurkerService.sip(p, theirs), sip, "a Lurker's drink from another's anchor near the Anchor Rivet's bearer");
			d.relic = null;
			near(helper, AnchorService.worth(d), plain, "what fuel is worth without the Anchor Rivet");
			near(helper, LurkerService.sip(p, mine), sip, "a Lurker's drink without the Anchor Rivet");
		} finally {
			d.relic = null;
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ rare

	@GameTest
	public void theMuffledBellHushesTheSculkAndCallsItsLurkers(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		double chance = Config.SCULK_LURKER_SPAWN_CHANCE;
		try {
			check(helper, !p.dampensVibrations() && !CaveLurkerService.quiet(p), "a player without the Muffled Bell is hushed");
			d.relic = Relic.MUFFLED_BELL.id;
			// The sculk does not hear its bearer (as if they wore wool), nor a Cave Lurker their steps (as if they sneaked)
			check(helper, p.dampensVibrations(), "the sculk hears the Muffled Bell's bearer");
			check(helper, CaveLurkerService.quiet(p), "a Cave Lurker hears the Muffled Bell's bearer's steps");
			// ... but what a Sculk Lurker does in their name, it hears
			boolean[] hushed = {true};
			RelicService.inTheirName(() -> hushed[0] = p.dampensVibrations());
			check(helper, !hushed[0], "the sculk did not hear what a Sculk Lurker set off in the Muffled Bell's bearer's name");
			check(helper, p.dampensVibrations(), "the Muffled Bell stopped hushing once a Sculk Lurker had set the sculk off");
			// Its price: Sculk Lurkers come twice as often
			Config.SCULK_LURKER_SPAWN_CHANCE = 0.1;
			near(helper, SculkLurkerService.chance(p), 0.1 * Config.RELIC_MUFFLED_BELL_SCULK_LURKER, "a Sculk Lurker's chance with the Muffled Bell");
			d.relic = null;
			near(helper, SculkLurkerService.chance(p), 0.1, "a Sculk Lurker's chance without the Muffled Bell");
			check(helper, !p.dampensVibrations(), "the Muffled Bell hushed its bearer after it came off");
		} finally {
			Config.SCULK_LURKER_SPAWN_CHANCE = chance;
			d.relic = null;
		}
		helper.succeed();
	}

	@GameTest
	public void theVeilScaleDriftsDownTheSeamAndShortensIt(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		try {
			d.relic = Relic.VEIL_SCALE.id;
			p.removeEffect(MobEffects.SLOW_FALLING);
			p.setOnGround(false);
			// A long fall in the Overworld: nothing
			p.fallDistance = Config.RELIC_VEIL_SCALE_FALL + 2;
			d.tickCounter = 1;
			RelicService.tick(p, d);
			check(helper, !p.hasEffect(MobEffects.SLOW_FALLING), "the Veil Scale caught a fall in the Overworld");
			// In the Seam, a short fall: nothing yet; past its blocks: it drifts
			d.inSeam = true;
			p.fallDistance = Math.max(0, Config.RELIC_VEIL_SCALE_FALL - 1);
			RelicService.tick(p, d);
			check(helper, !p.hasEffect(MobEffects.SLOW_FALLING), "the Veil Scale caught a fall shorter than its blocks");
			p.fallDistance = Config.RELIC_VEIL_SCALE_FALL + 2;
			RelicService.tick(p, d);
			check(helper, p.hasEffect(MobEffects.SLOW_FALLING), "the Veil Scale did not catch a long fall in the Seam");
			// Held on for the rest of the way down, though the slow fall took the fall it counted away
			p.removeEffect(MobEffects.SLOW_FALLING);
			p.fallDistance = 0;
			d.tickCounter = 10;
			RelicService.tick(p, d);
			check(helper, p.hasEffect(MobEffects.SLOW_FALLING), "the Veil Scale let go of its bearer halfway down");
			// Landed, it lets go
			p.removeEffect(MobEffects.SLOW_FALLING);
			p.setOnGround(true);
			RelicService.tick(p, d);
			check(helper, !p.hasEffect(MobEffects.SLOW_FALLING) && !d.veilDrift, "the Veil Scale kept its bearer drifting on the ground");
			// Its price: time in the Seam, never below a minute
			near(helper, RelicService.seamTime(d, 300), 300 - Config.RELIC_VEIL_SCALE_SECONDS, "Seam time of 300 s with the Veil Scale");
			near(helper, RelicService.seamTime(d, 70), Math.max(60, 70 - Config.RELIC_VEIL_SCALE_SECONDS), "Seam time of 70 s with the Veil Scale");
			near(helper, RelicService.seamTime(d, 45), 45, "Seam time of 45 s with the Veil Scale");
			d.relic = null;
			near(helper, RelicService.seamTime(d, 300), 300, "Seam time without the Veil Scale");
		} finally {
			d.relic = null;
			d.inSeam = false;
			d.veilDrift = false;
			p.fallDistance = 0;
			p.removeEffect(MobEffects.SLOW_FALLING);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ epic

	@GameTest
	public void theFerrymansObolKeepsAllBeyondTheWoundAndTakesItsToll(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		ServerPlayer risen = TestPlayers.survivor(helper, "obol-risen");
		PlayerData d = DataStore.get(p.getUUID());
		try {
			d.relic = Relic.FERRYMANS_OBOL.id;
			check(helper, RelicService.keepsOnDeath(d, Dims.NETHER) && RelicService.keepsOnDeath(d, Dims.END) && RelicService.keepsOnDeath(d, Dims.SEAM)
					&& !RelicService.keepsOnDeath(d, Dims.OVERWORLD), "the Ferryman's Obol keeps the wrong worlds' deaths");
			// A death at home drops as ever (the hook asks for the world the player is in: the Overworld here)
			check(helper, !RelicService.spareDrops(p) && !RelicService.spareDrops(p, Dims.OVERWORLD) && !d.obolKept, "the Ferryman's Obol kept a death at home");
			check(helper, RelicService.restore(p, risen) == 0, "a death at home came back on respawn");
			// A death beyond the wound: nothing falls, and on respawn it is all back, each in its slot
			p.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
			p.getInventory().setItem(9, new ItemStack(Items.TORCH, 12));
			p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			check(helper, RelicService.spareDrops(p, Dims.NETHER) && d.obolKept, "the Ferryman's Obol let a death in the Nether drop");
			int back = RelicService.restore(p, risen);
			check(helper, back == 4, "%s stacks came back on respawn, not 4", back);
			check(helper, risen.getInventory().getItem(0).is(Items.IRON_SWORD) && risen.getInventory().getItem(9).getCount() == 12
					&& risen.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && risen.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD),
					"what came back is not where it was carried");
			check(helper, !d.obolKept && carried(p, Items.IRON_SWORD) == 0, "what came back stayed with the dead body too");
			check(helper, RelicService.restore(p, risen) == 0, "what the Ferryman's Obol kept came back twice");
			// Its toll: a curse more for every death beyond the wound (a Collapse too), none for one at home; more drain
			int owed = d.pendingPenalties;
			ExpeditionService.oweDeathBeyond(d, false, false);
			check(helper, d.pendingPenalties == owed + 1 + Config.RELIC_FERRYMANS_OBOL_CURSES, "a death beyond the wound owed %s curses with the Ferryman's Obol",
					d.pendingPenalties - owed);
			owed = d.pendingPenalties;
			ExpeditionService.oweCollapse(d, false);
			check(helper, d.pendingPenalties == owed + 1 + Config.RELIC_FERRYMANS_OBOL_CURSES, "a Collapse owed %s curses with the Ferryman's Obol",
					d.pendingPenalties - owed);
			owed = d.pendingPenalties;
			PenaltyService.oweDeath(d, true);
			check(helper, d.pendingPenalties == owed + 1, "a death at home owed %s curses with the Ferryman's Obol", d.pendingPenalties - owed);
			for (String dim : new String[] {Dims.NETHER, Dims.END, Dims.SEAM}) {
				near(helper, RelicService.drainFactor(d, dim), Config.RELIC_FERRYMANS_OBOL_DRAIN, "drain in " + dim + " with the Ferryman's Obol");
			}
			d.relic = null;
			owed = d.pendingPenalties;
			ExpeditionService.oweDeathBeyond(d, false, false);
			check(helper, d.pendingPenalties == owed + 1, "a death beyond the wound owed %s curses without the Ferryman's Obol", d.pendingPenalties - owed);
			near(helper, RelicService.drainFactor(d, Dims.NETHER), 1.0, "drain without the Ferryman's Obol");
			check(helper, !RelicService.spareDrops(p, Dims.NETHER), "a death in the Nether was kept without the Ferryman's Obol");
		} finally {
			d.relic = null;
			d.obolKept = false;
			settle(d);
			empty(p);
			empty(risen);
		}
		helper.succeed();
	}

	@GameTest
	public void theHollowCrownSatesButNeverHeals(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = cursed(helper);
		ServerPlayer clean = TestPlayers.survivor(helper, "crown-control");
		PlayerData d = DataStore.get(p.getUUID());
		double regen = Config.RELIC_HOLLOW_CROWN_REGEN;
		try {
			d.relic = Relic.HOLLOW_CROWN.id;
			near(helper, Charms.exhaustion(p), 0, "hunger with the Hollow Crown");
			// Its stomach is kept full, whatever empties it
			p.getFoodData().setFoodLevel(3);
			p.getFoodData().setSaturation(0f);
			p.causeFoodExhaustion(40f);
			RelicService.tick(p, d);
			check(helper, p.getFoodData().getFoodLevel() == 20 && p.getFoodData().getSaturationLevel() >= 19.99f,
					"the Hollow Crown's bearer is left at %s food and %s saturation", p.getFoodData().getFoodLevel(), p.getFoodData().getSaturationLevel());
			// A full stomach heals them not at all, while it heals another
			Config.RELIC_HOLLOW_CROWN_REGEN = 0;
			for (ServerPlayer s : List.of(p, clean)) {
				s.setHealth(10f);
				s.getFoodData().setFoodLevel(20);
				s.getFoodData().setSaturation(5f);
			}
			for (int i = 0; i < 40; i++) {
				p.getFoodData().tick(p);
				clean.getFoodData().tick(clean);
			}
			check(helper, clean.getHealth() > 10f, "the control did not heal from a full stomach (%s)", clean.getHealth());
			near(helper, p.getHealth(), 10f, "health of the Hollow Crown's bearer after a well-fed while");
			// With a share of natural healing left, that share
			Config.RELIC_HOLLOW_CROWN_REGEN = 0.5;
			for (ServerPlayer s : List.of(p, clean)) {
				s.setHealth(10f);
				s.getFoodData().setFoodLevel(20);
				s.getFoodData().setSaturation(5f);
			}
			for (int i = 0; i < 40; i++) {
				p.getFoodData().tick(p);
				clean.getFoodData().tick(clean);
			}
			near(helper, p.getHealth() - 10f, (clean.getHealth() - 10f) * 0.5, "half the natural healing with the Hollow Crown at 0.5");
			Config.RELIC_HOLLOW_CROWN_REGEN = 0;
			// A Regeneration potion still heals
			p.setHealth(10f);
			MobEffects.REGENERATION.value().applyEffectTick(level, p, 0);
			check(helper, p.getHealth() > 10f, "Regeneration did not heal the Hollow Crown's bearer (%s)", p.getHealth());
			d.relic = null;
			near(helper, Charms.exhaustion(p), 1.0, "hunger without the Hollow Crown");
			check(helper, RelicService.regenerates(p, 1f), "a full stomach may not heal once the Hollow Crown is off");
		} finally {
			Config.RELIC_HOLLOW_CROWN_REGEN = regen;
			d.relic = null;
			p.setHealth(p.getMaxHealth());
		}
		helper.succeed();
	}

	/** In another mod's world these relics sleep with the curses too: neither gift nor price acts there. */
	@GameTest
	public void theEightLaterRelicsSleepInAnotherModsWorld(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		double cave = Config.CAVE_LURKER_SPAWN_CHANCE, magpie = Config.MAGPIE_SPAWN_CHANCE, sculk = Config.SCULK_LURKER_SPAWN_CHANCE;
		try {
			Config.CAVE_LURKER_SPAWN_CHANCE = 0.1;
			Config.MAGPIE_SPAWN_CHANCE = 0.1;
			Config.SCULK_LURKER_SPAWN_CHANCE = 0.1;
			double worth = AnchorService.worth(d);
			d.away = true;
			d.relic = Relic.ASHEN_LOCKET.id;
			near(helper, CurseEffects.damageTaken(p, hurt(level, "lava"), 10f), 10, "lava with the Ashen Locket asleep");
			d.relic = Relic.MINERS_CANDLE.id;
			near(helper, CaveLurkerService.chance(p), 0.1, "a Cave Lurker's chance with the Miner's Candle asleep");
			d.relic = Relic.MAGPIES_GLINT.id;
			near(helper, MagpieService.chance(p), 0.1, "a Magpie's chance with the Magpie's Glint asleep");
			d.relic = Relic.ANCHOR_RIVET.id;
			near(helper, AnchorService.worth(d), worth, "fuel with the Anchor Rivet asleep");
			d.relic = Relic.MUFFLED_BELL.id;
			check(helper, !p.dampensVibrations() && !CaveLurkerService.quiet(p), "the Muffled Bell hushed its bearer asleep");
			near(helper, SculkLurkerService.chance(p), 0.1, "a Sculk Lurker's chance with the Muffled Bell asleep");
			d.relic = Relic.VEIL_SCALE.id;
			near(helper, RelicService.seamTime(d, 300), 300, "Seam time with the Veil Scale asleep");
			d.relic = Relic.FERRYMANS_OBOL.id;
			check(helper, !RelicService.keepsOnDeath(d, Dims.NETHER) && RelicService.passage(d) == 0, "the Ferryman's Obol acted asleep");
			near(helper, RelicService.drainFactor(d, Dims.NETHER), 1.0, "drain with the Ferryman's Obol asleep");
			d.relic = Relic.HOLLOW_CROWN.id;
			near(helper, Charms.exhaustion(p), 1.0, "hunger with the Hollow Crown asleep");
			check(helper, RelicService.regenerates(p, 1f), "the Hollow Crown kept a full stomach from healing asleep");
		} finally {
			Config.CAVE_LURKER_SPAWN_CHANCE = cave;
			Config.MAGPIE_SPAWN_CHANCE = magpie;
			Config.SCULK_LURKER_SPAWN_CHANCE = sculk;
			d.away = false;
			d.relic = null;
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ serving once

	private static ItemStack relic(Relic r) {
		return new ItemStack(RelicService.item(r));
	}

	/**
	 * With relic_single_use on (the default): a bound relic cannot be taken off (the request is refused and it stays
	 * bound); binding over it first only warns (and a use button held down does not pass the warning), and a second use
	 * binds the new one while the old one is gone for good. Nothing comes back to the hand or falls.
	 */
	@GameTest(maxTicks = 80)
	public void relicsServeOnce(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		int single = Config.RELIC_SINGLE_USE;
		Runnable restore = () -> {
			Config.RELIC_SINGLE_USE = single;
			d.relic = null;
			empty(p);
		};
		ItemStack shard;
		try {
			Config.RELIC_SINGLE_USE = 1;
			empty(p);
			p.getInventory().setItem(0, relic(Relic.EMBER_HEART));
			check(helper, RelicService.bind(p, p.getInventory().getItem(0)).equals(InteractionResult.SUCCESS) && RelicService.has(d, Relic.EMBER_HEART),
					"binding a relic with none bound failed");
			// No taking it off: the request leaves it bound, and gives nothing
			RelicService.unbind(p);
			check(helper, RelicService.has(d, Relic.EMBER_HEART), "a relic serving once was taken off the Tether");
			check(helper, carried(p, RelicService.item(Relic.EMBER_HEART)) == 0 && dropped(p, RelicService.item(Relic.EMBER_HEART)) == 0,
					"a request to take off a relic serving once gave one back");
			p.getInventory().setItem(1, relic(Relic.PALE_SHARD));
			shard = p.getInventory().getItem(1);
			// The first use over a bound relic only warns, and so does the same press held down
			check(helper, !RelicService.bind(p, shard).equals(InteractionResult.SUCCESS), "a relic was bound over another without a warning");
			check(helper, !RelicService.bind(p, shard).equals(InteractionResult.SUCCESS), "a held use button passed the warning at once");
			check(helper, RelicService.has(d, Relic.EMBER_HEART) && shard.getCount() == 1, "the warning bound the relic or used it up");
		} catch (RuntimeException e) {
			restore.run();
			throw e;
		}
		ItemStack used = shard;
		helper.runAfterDelay(20, () -> {
			try {
				// A second use binds it, and the one bound before is gone
				check(helper, RelicService.bind(p, used).equals(InteractionResult.SUCCESS) && RelicService.has(d, Relic.PALE_SHARD) && used.isEmpty(),
						"a second use did not bind the relic");
				check(helper, carried(p, RelicService.item(Relic.EMBER_HEART)) == 0 && dropped(p, RelicService.item(Relic.EMBER_HEART)) == 0,
						"the relic bound before came back instead of breaking");
				// A warning for one relic is no leave to bind another
				p.getInventory().setItem(2, relic(Relic.TITHE_BELL));
				p.getInventory().setItem(3, relic(Relic.LODESTONE_CHIP));
				RelicService.bind(p, p.getInventory().getItem(2));
				check(helper, !RelicService.bind(p, p.getInventory().getItem(3)).equals(InteractionResult.SUCCESS) && RelicService.has(d, Relic.PALE_SHARD),
						"a warning for one relic let another be bound");
				// Still no taking it off
				RelicService.unbind(p);
				check(helper, RelicService.has(d, Relic.PALE_SHARD) && carried(p, RelicService.item(Relic.PALE_SHARD)) == 0,
						"the relic bound in the other's place came off the Tether");
			} finally {
				restore.run();
			}
			helper.succeed();
		});
	}

	/** With relic_single_use off: the relic bound before comes back at once, and so does one taken off. */
	@GameTest
	public void relicsComeBackWhenTheyServeMoreThanOnce(GameTestHelper helper) {
		ServerPlayer p = cursed(helper);
		PlayerData d = DataStore.get(p.getUUID());
		int single = Config.RELIC_SINGLE_USE;
		try {
			Config.RELIC_SINGLE_USE = 0;
			empty(p);
			p.getInventory().setItem(0, relic(Relic.EMBER_HEART));
			RelicService.bind(p, p.getInventory().getItem(0));
			p.getInventory().setItem(1, relic(Relic.PALE_SHARD));
			check(helper, RelicService.bind(p, p.getInventory().getItem(1)).equals(InteractionResult.SUCCESS) && RelicService.has(d, Relic.PALE_SHARD),
					"with relics serving more than once, the first use over a bound relic did not bind it");
			check(helper, carried(p, RelicService.item(Relic.EMBER_HEART)) == 1, "the relic bound before did not come back");
			RelicService.unbind(p);
			check(helper, RelicService.bound(d) == null && carried(p, RelicService.item(Relic.PALE_SHARD)) == 1, "a relic taken off did not come back");
		} finally {
			Config.RELIC_SINGLE_USE = single;
			d.relic = null;
			empty(p);
		}
		helper.succeed();
	}
}
