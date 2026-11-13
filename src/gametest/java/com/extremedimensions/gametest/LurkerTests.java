package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.config.Config;
import com.extremedimensions.config.Preset;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.expedition.ExpeditionService;
import com.extremedimensions.guide.Hints;
import com.extremedimensions.hollow.HollowService;
import com.extremedimensions.hollow.Hollowling;
import com.extremedimensions.hollow.Keeper;
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.lurker.LurkerBody;
import com.extremedimensions.lurker.LurkerService;
import com.extremedimensions.lurker.TribeMoves;
import com.extremedimensions.penalty.FleeService;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.server.Dims;
import com.extremedimensions.world.AmbientService;
import com.extremedimensions.world.WorldService;
import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Lurker as a threat (it claws as it reaches, takes several things and may leave a curse; come to hurt, it glows red
 * and strikes), the tribe's way of walking (out of lava and water, on the ground, never in a tree, unhurt by a fall or a
 * low roof, up a ledge with a leap), the swarm, a world on a difficulty keeping it through the upgrade, and what a new
 * player is told about the wound.
 */
public class LurkerTests {
	/**
	 * 3.31: every kind takes its health from its setting as it is made (half again its old health by default): the
	 * Lurker 90, the Keeper 210; a setting changed holds for the next one made; the Starved One keeps its own 500.
	 */
	@GameTest
	public void eachKindTakesItsHealthFromItsSetting(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		double magpie = Config.MAGPIE_HEALTH;
		try {
			check(helper, Config.LURKER_HEALTH == 90 && Config.KEEPER_HEALTH == 210 && Config.WICK_HEALTH == 6, "the defaults are not half again the old health");
			Lurker l = ModEntities.LURKER.create(level, EntitySpawnReason.EVENT);
			Keeper k = ModEntities.KEEPER.create(level, EntitySpawnReason.EVENT);
			check(helper, l != null && l.getMaxHealth() == 90 && l.getHealth() == 90, "a Lurker is made with %s of %s health", l == null ? 0 : l.getHealth(),
					l == null ? 0 : l.getMaxHealth());
			check(helper, k != null && k.getMaxHealth() == 210, "a Keeper is made with %s health", k == null ? 0 : k.getMaxHealth());
			Config.MAGPIE_HEALTH = 50;
			var m = ModEntities.MAGPIE.create(level, EntitySpawnReason.EVENT);
			check(helper, m != null && m.getMaxHealth() == 50 && m.getHealth() == 50, "a Magpie made after its setting changed has %s health",
					m == null ? 0 : m.getMaxHealth());
			var so = ModEntities.STARVED_ONE.create(level, EntitySpawnReason.EVENT);
			check(helper, so != null && so.getMaxHealth() == com.extremedimensions.mending.StarvedOne.baseHealth(), "the Starved One took the Keeper's health (%s)",
					so == null ? 0 : so.getMaxHealth());
		} finally {
			Config.MAGPIE_HEALTH = magpie;
		}
		helper.succeed();
	}

	private static void place(GameTestHelper helper, ServerPlayer p, double x, double y, double z) {
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.snapTo(at.x, at.y, at.z, 0f, 0f);
	}

	/**
	 * 3.27.5: a companion told to stay (sneak, an empty hand) waits where it was left while its player walks off, and is
	 * kept even when no one drives it; told again, it comes along.
	 */
	@GameTest(maxTicks = 260)
	public void aCompanionToldToStayWaitsAndFollowsWhenCalled(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "stay-owner");
		PlayerData d = DataStore.get(p.getUUID());
		place(helper, p, 7.5, 0, 7.5);
		Hollowling h = HollowService.befriend(p, helper.absolutePos(new BlockPos(1, 0, 1)));
		check(helper, h != null, "no companion came");
		Vec3 left = h.position();
		check(helper, HollowService.stay(p, h, true).consumesAction(), "the companion would not stay");
		check(helper, HollowService.waiting(d) && d.companionStayId.equals(h.getStringUUID()), "the player's data does not say where it waits");
		check(helper, HollowService.isDriven(h), "a companion told to stay is not kept");
		helper.runAfterDelay(80, () -> {
			check(helper, h.isAlive() && h.position().distanceTo(left) < 1.0, "the companion told to stay moved %.1f blocks", h.position().distanceTo(left));
			check(helper, HollowService.stay(p, h, false).consumesAction() && !HollowService.waiting(d), "the companion would not come along again");
		});
		helper.runAfterDelay(240, () -> {
			try {
				check(helper, h.isAlive() && h.distanceTo(p) < 6, "called again, the companion stayed %.1f blocks off", h.distanceTo(p));
			} finally {
				d.companion = false;
				d.companionStay = null;
				d.companionStayId = null;
				h.discard();
			}
			helper.succeed();
		});
	}

	private static int curses(PlayerData d) {
		int n = 0;
		for (Penalty pen : Penalty.values()) {
			n += d.owned(pen);
		}
		return n;
	}

	private static int filled(ServerPlayer p) {
		int n = 0;
		for (int i = 0; i < 36; i++) {
			n += p.getInventory().getItem(i).isEmpty() ? 0 : 1;
		}
		return n;
	}

	/**
	 * Looking for room or for the ground never reads a chunk that is not loaded now: a body at a chunk's edge reaches into
	 * the next one, and reading a block of a chunk on its way in makes the server wait (the performance test caught a
	 * Lurker's vantage doing so, 44 ms). In loaded ground it still finds room.
	 */
	@GameTest
	public void roomIsSoughtOnlyInLoadedChunks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// Far out, where nothing is loaded, on the east edge of a chunk: a body there reaches into the next
		int x = 1_600_015, z = 1_600_008;
		var chunks = level.getChunkSource();
		check(helper, chunks.getChunkNow(x >> 4, z >> 4) == null && chunks.getChunkNow((x + 1) >> 4, z >> 4) == null, "the far chunks were loaded already");
		check(helper, !TribeMoves.roomFor(level, new BlockPos(x, 64, z), 1.4, 3.6), "room was found in chunks that are not loaded");
		check(helper, TribeMoves.surface(level, x, z, 80, 1.4, 3.6) == null, "ground was found in a chunk that is not loaded");
		check(helper, chunks.getChunkNow(x >> 4, z >> 4) == null && chunks.getChunkNow((x + 1) >> 4, z >> 4) == null, "looking for room loaded a chunk");
		// Here, on a floor of stone, there is room as before
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE.defaultBlockState());
		check(helper, TribeMoves.roomFor(level, helper.absolutePos(new BlockPos(1, 2, 1)), 0.6, 1.8), "no room on a floor of stone in loaded ground");
		helper.succeed();
	}

	/** Its reach: a claw that hurts, two things taken, and (sure to here) a curse left behind. Its glow pulses blue. */
	@GameTest(environment = Alone.LURKER_TAKE, maxTicks = 200)
	public void aLurkersReachClawsTakesAndCurses(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Difficulty was = level.getDifficulty();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer p = TestPlayers.survivor(helper, "lurker-prey");
		place(helper, p, 2.5, 2, 2.5);
		Item[] things = {Items.DIRT, Items.COBBLESTONE, Items.OAK_LOG, Items.SAND, Items.GRAVEL};
		PlayerData d = DataStore.get(p.getUUID());
		double xp = Config.LURKER_XP_WEIGHT, armor = Config.LURKER_ARMOR_WEIGHT, curseWeight = Config.LURKER_CURSE_WEIGHT;
		double curseChance = Config.LURKER_CURSE_CHANCE, claw = Config.LURKER_CLAW_DAMAGE;
		int thefts = Config.LURKER_THEFTS, most = Config.LURKER_CURSES_MAX;
		// past the grace a player has on joining (three seconds)
		helper.runAfterDelay(70, () -> {
			Config.LURKER_XP_WEIGHT = 0;
			Config.LURKER_ARMOR_WEIGHT = 0;
			Config.LURKER_CURSE_WEIGHT = 0;
			Config.LURKER_CURSE_CHANCE = 1;
			Config.LURKER_CLAW_DAMAGE = 4;
			Config.LURKER_THEFTS = 2;
			Config.LURKER_CURSES_MAX = 1;
			// (filled only now: the journal a new player is handed two seconds in would be a sixth stack)
			p.getInventory().clearContent();
			for (int i = 0; i < things.length; i++) {
				p.getInventory().setItem(i, new ItemStack(things[i], 8));
			}
			p.setHealth(20f);
			int before = curses(d);
			Lurker l = ModEntities.LURKER.spawn(level, helper.absolutePos(new BlockPos(4, 2, 2)), EntitySpawnReason.COMMAND);
			check(helper, l != null, "no Lurker came");
			LurkerService.adopt(level, l, p, "take");
			check(helper, l.getLurkerMood() == LurkerBody.MOOD_THIEF, "a Lurker come to steal glows in mood %s, not blue", l.getLurkerMood());
			helper.runAfterDelay(20, () -> {
				try {
					check(helper, filled(p) == things.length - 2, "the reach left %s of %s stacks, not two fewer", filled(p), things.length);
					check(helper, LurkerService.carried(l) == 2, "the Lurker carries %s things, not 2", LurkerService.carried(l));
					check(helper, p.getHealth() < 20f, "its claw did no harm (health %s)", p.getHealth());
					check(helper, curses(d) == before + 1, "it left %s curses, not one", curses(d) - before);
				} finally {
					Config.LURKER_XP_WEIGHT = xp;
					Config.LURKER_ARMOR_WEIGHT = armor;
					Config.LURKER_CURSE_WEIGHT = curseWeight;
					Config.LURKER_CURSE_CHANCE = curseChance;
					Config.LURKER_CLAW_DAMAGE = claw;
					Config.LURKER_THEFTS = thefts;
					Config.LURKER_CURSES_MAX = most;
					// (gone without its loot, which would otherwise be carried to a hoard of its own)
					l.discard();
					level.getServer().setDifficulty(was, true);
				}
				helper.succeed();
			});
		});
	}

	/** Come to hurt: it glows red, rears up and strikes. */
	@GameTest(environment = Alone.LURKER_HUNT, maxTicks = 200)
	public void aHuntingLurkerGlowsRedAndStrikes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Difficulty was = level.getDifficulty();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer p = TestPlayers.survivor(helper, "lurker-hunted");
		place(helper, p, 2.5, 2, 2.5);
		double curseChance = Config.LURKER_CURSE_CHANCE, damage = Config.LURKER_STRIKE_DAMAGE;
		helper.runAfterDelay(70, () -> {
			Config.LURKER_CURSE_CHANCE = 0;
			Config.LURKER_STRIKE_DAMAGE = 6;
			p.setHealth(20f);
			Lurker l = ModEntities.LURKER.spawn(level, helper.absolutePos(new BlockPos(4, 2, 2)), EntitySpawnReason.COMMAND);
			check(helper, l != null, "no Lurker came");
			LurkerService.adopt(level, l, p, "hunt");
			check(helper, l.getLurkerMood() == LurkerBody.MOOD_HUNTER, "a Lurker come to hurt glows in mood %s, not red", l.getLurkerMood());
			check(helper, "strike".equals(LurkerService.mode(l)), "a Lurker come to hurt is in mode %s", LurkerService.mode(l));
			helper.runAfterDelay(45, () -> {
				try {
					check(helper, LurkerService.blows(l) >= 1, "the hunting Lurker landed no blow");
					check(helper, p.getHealth() < 20f, "its blow did no harm (health %s)", p.getHealth());
				} finally {
					Config.LURKER_CURSE_CHANCE = curseChance;
					Config.LURKER_STRIKE_DAMAGE = damage;
					l.discard();
					level.getServer().setDifficulty(was, true);
				}
				helper.succeed();
			});
		});
	}

	/** The tribe keeps out of lava and fire and goes round water; its moods show in its glow. */
	@GameTest
	public void theTribeIsWaryAndShowsWhatItCameFor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "lurker-watcher");
		Lurker l = ModEntities.LURKER.spawn(level, helper.absolutePos(new BlockPos(3, 2, 3)), EntitySpawnReason.COMMAND);
		Hollowling h = ModEntities.HOLLOWLING.spawn(level, helper.absolutePos(new BlockPos(5, 2, 5)), EntitySpawnReason.COMMAND);
		try {
			check(helper, l != null && h != null, "the tribe did not come");
			for (net.minecraft.world.entity.Mob m : new net.minecraft.world.entity.Mob[] {l, h}) {
				check(helper, m.getPathfindingMalus(PathType.LAVA) < 0 && m.getPathfindingMalus(PathType.FIRE) < 0,
						"%s would walk into lava or fire", m.getType());
				check(helper, m.getPathfindingMalus(PathType.WATER) >= 16, "%s does not go round water (malus %s)", m.getType(),
						m.getPathfindingMalus(PathType.WATER));
			}
			check(helper, l.getLurkerMood() == LurkerBody.MOOD_CALM, "a new Lurker is not calm");
			LurkerService.adopt(level, l, p, "stalk");
			check(helper, l.getLurkerMood() == LurkerBody.MOOD_THIEF, "a stalking Lurker does not pulse blue");
			LurkerService.adopt(level, l, p, "flee");
			check(helper, l.getLurkerMood() == LurkerBody.MOOD_CALM, "a fleeing Lurker keeps its mood %s", l.getLurkerMood());
		} finally {
			if (l != null) {
				l.discard();
			}
			if (h != null) {
				h.discard();
			}
		}
		helper.succeed();
	}

	/** Where the tribe may stand: on the ground with room for its height, never on a tree, in water or in fire. */
	@GameTest
	public void theTribeStandsOnTheGroundNotInTrees(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 1; x <= 6; x++) {
			for (int z = 1; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
			}
		}
		// A little tree: a trunk, and leaves four blocks up
		for (int y = 2; y <= 5; y++) {
			helper.setBlock(new BlockPos(3, y, 3), Blocks.OAK_LOG.defaultBlockState());
		}
		for (int x = 2; x <= 4; x++) {
			for (int z = 2; z <= 4; z++) {
				if (x != 3 || z != 3) {
					helper.setBlock(new BlockPos(x, 5, z), Blocks.OAK_LEAVES.defaultBlockState());
				}
			}
		}
		helper.setBlock(new BlockPos(6, 1, 6), Blocks.WATER.defaultBlockState());
		helper.setBlock(new BlockPos(1, 1, 6), Blocks.MAGMA_BLOCK.defaultBlockState());
		check(helper, TribeMoves.ground(Blocks.GRASS_BLOCK.defaultBlockState()) && TribeMoves.ground(Blocks.NETHERRACK.defaultBlockState()),
				"grass or netherrack is no ground");
		check(helper, !TribeMoves.ground(Blocks.OAK_LEAVES.defaultBlockState()) && !TribeMoves.ground(Blocks.OAK_LOG.defaultBlockState())
				&& !TribeMoves.ground(Blocks.NETHER_WART_BLOCK.defaultBlockState()), "leaves, a trunk or a wart block count as ground");
		check(helper, TribeMoves.hazard(Blocks.LAVA.defaultBlockState()) && TribeMoves.hazard(Blocks.WATER.defaultBlockState())
				&& TribeMoves.hazard(Blocks.FIRE.defaultBlockState()), "lava, water or fire is no hazard");
		check(helper, TribeMoves.standable(level, helper.absolutePos(new BlockPos(1, 2, 1)), 4), "open ground is not standable");
		check(helper, TribeMoves.standable(level, helper.absolutePos(new BlockPos(2, 2, 2)), 2), "a Hollowling cannot stand under the leaves");
		check(helper, !TribeMoves.standable(level, helper.absolutePos(new BlockPos(2, 2, 2)), 4), "a Lurker stands where the leaves cut through it");
		check(helper, !TribeMoves.standable(level, helper.absolutePos(new BlockPos(3, 6, 3)), 2), "the top of a trunk is standable");
		check(helper, !TribeMoves.standable(level, helper.absolutePos(new BlockPos(2, 6, 2)), 2), "the top of the leaves is standable");
		check(helper, !TribeMoves.standable(level, helper.absolutePos(new BlockPos(6, 2, 6)), 2), "water is standable");
		check(helper, !TribeMoves.standable(level, helper.absolutePos(new BlockPos(1, 2, 6)), 2), "magma is standable");
		helper.succeed();
	}

	/** A world on Hard before 3.7.0 stays on Hard: it takes Hard's values of the Lurker settings it gained. */
	@GameTest
	public void aWorldOnADifficultyKeepsItThroughTheUpgrade(GameTestHelper helper) {
		JsonObject hard = new JsonObject();
		hard.addProperty("config_version", 3);
		JsonObject custom = new JsonObject();
		custom.addProperty("config_version", 3);
		for (var e : Preset.HARD.values.entrySet()) {
			String k = e.getKey();
			if (!k.startsWith("lurker_") || k.equals("lurker_spawn_chance") || k.equals("lurker_cooldown_seconds")) {
				hard.addProperty(k, Double.parseDouble(e.getValue()));
				custom.addProperty(k, Double.parseDouble(e.getValue()));
			}
		}
		hard.addProperty("lurker_stalk_chance", 0.15);
		custom.addProperty("lurker_stalk_chance", 0.15);
		custom.addProperty("atone_base", 999);
		Config.migrate(hard);
		Config.migrate(custom);
		check(helper, hard.get("lurker_stalk_chance").getAsDouble() == 0.4 && hard.get("lurker_thefts").getAsInt() == 2
				&& hard.get("lurker_strike_damage").getAsDouble() == 8, "a Hard world did not take Hard's Lurker settings: %s", hard);
		check(helper, custom.get("lurker_stalk_chance").getAsDouble() == 0.3 && !custom.has("lurker_thefts"),
				"a world of its own rules was given a difficulty's Lurker settings: %s", custom);
		helper.succeed();
	}

	/** A fall or a wall does the tribe no harm and makes none of it run; only a blow does. The big ones step a block. */
	@GameTest(maxTicks = 60)
	public void theTribeShrugsOffFallsAndOnlyABlowMakesItRun(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "lurker-faller");
		place(helper, p, 1.5, 2, 1.5);
		Lurker l = ModEntities.LURKER.spawn(level, helper.absolutePos(new BlockPos(5, 2, 5)), EntitySpawnReason.COMMAND);
		Hollowling h = ModEntities.HOLLOWLING.spawn(level, helper.absolutePos(new BlockPos(2, 2, 6)), EntitySpawnReason.COMMAND);
		Keeper k = ModEntities.KEEPER.spawn(level, helper.absolutePos(new BlockPos(6, 2, 1)), EntitySpawnReason.COMMAND);
		try {
			check(helper, l != null && h != null && k != null, "the tribe did not come");
			LurkerService.adopt(level, l, p, "stalk");
			float full = l.getHealth();
			check(helper, !l.hurtServer(level, level.damageSources().fall(), 12f) && l.getHealth() == full, "a fall hurt the Lurker");
			check(helper, !l.hurtServer(level, level.damageSources().inWall(), 4f) && l.getHealth() == full, "a wall hurt the Lurker");
			check(helper, "stalk".equals(LurkerService.mode(l)), "a fall made the Lurker %s", LurkerService.mode(l));
			check(helper, !h.hurtServer(level, level.damageSources().fall(), 6f) && h.getHealth() == h.getMaxHealth(), "a fall hurt the Hollowling");
			check(helper, !k.hurtServer(level, level.damageSources().inWall(), 6f) && k.getHealth() == k.getMaxHealth(), "a wall hurt the Keeper");
			check(helper, l.getAttributeValue(Attributes.STEP_HEIGHT) >= 1.0 && k.getAttributeValue(Attributes.STEP_HEIGHT) >= 1.0,
					"the big ones do not step a block (%s, %s)", l.getAttributeValue(Attributes.STEP_HEIGHT), k.getAttributeValue(Attributes.STEP_HEIGHT));
			check(helper, l.getMaxFallDistance() >= 8 && h.getMaxFallDistance() >= 6, "the tribe's routes take no longer drops (%s, %s)",
					l.getMaxFallDistance(), h.getMaxFallDistance());
			l.hurtServer(level, level.damageSources().playerAttack(p), 2f);
			check(helper, !"stalk".equals(LurkerService.mode(l)), "a blow left the Lurker stalking");
		} finally {
			for (Mob m : new Mob[] {l, h, k}) {
				if (m != null) {
					m.discard();
				}
			}
		}
		helper.succeed();
	}

	/** Only where its whole body fits: a Keeper fits in no low tunnel, and one pressed into a roof steps out of it unhurt. */
	@GameTest(maxTicks = 80)
	public void aKeeperFitsWhereItStandsAndStepsOutOfARoof(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// A roof four blocks thick over a tunnel two blocks high (the floor of a test lies just under y 0)
		for (int x = 1; x <= 4; x++) {
			for (int z = 1; z <= 4; z++) {
				for (int y = 2; y <= 5; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		BlockPos tunnel = helper.absolutePos(new BlockPos(2, 0, 2));
		check(helper, !TribeMoves.roomFor(level, tunnel, 1.6, 4.2), "a Keeper fits in a tunnel two blocks high");
		check(helper, TribeMoves.roomFor(level, tunnel, 0.6, 1.1), "a Hollowling does not fit in a tunnel two blocks high");
		check(helper, TribeMoves.roomFor(level, helper.absolutePos(new BlockPos(6, 0, 6)), 1.6, 4.2), "a Keeper does not fit in the open");
		Keeper k = ModEntities.KEEPER.spawn(level, tunnel, EntitySpawnReason.COMMAND);
		helper.runAfterDelay(25, () -> {
			try {
				check(helper, k != null && k.isAlive(), "the Keeper died under a low roof");
				check(helper, k.getHealth() == k.getMaxHealth(), "a roof hurt the Keeper (%s)", k.getHealth());
				check(helper, !k.isInWall(), "the Keeper is still pressed into the roof at %s", k.blockPosition());
			} finally {
				if (k != null) {
					k.discard();
				}
			}
			helper.succeed();
		});
	}

	/** A ledge too high to step or jump, with no way round: a hunting Lurker leaps up onto it after its prey. */
	@GameTest(environment = Alone.LURKER_LEAP, maxTicks = 220)
	public void aHuntingLurkerLeapsUpALedge(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// A block of stone three high on the floor, its prey on top, too far in to strike from below
		for (int x = 4; x <= 7; x++) {
			for (int z = 1; z <= 3; z++) {
				for (int y = 0; y <= 2; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		ServerPlayer p = TestPlayers.survivor(helper, "lurker-ledge");
		place(helper, p, 6.5, 3, 2.5);
		Lurker l = ModEntities.LURKER.spawn(level, helper.absolutePos(new BlockPos(1, 0, 2)), EntitySpawnReason.COMMAND);
		double top = helper.absolutePos(new BlockPos(0, 3, 0)).getY();
		helper.runAfterDelay(20, () -> {
			check(helper, l != null, "no Lurker came");
			LurkerService.adopt(level, l, p, "hunt");
		});
		// Watched every tick: once up beside its prey it strikes and may hop, so one tick's height says little
		int[] upAt = {-1};
		for (int t = 21; t <= 200; t++) {
			int tick = t;
			helper.runAfterDelay(t, () -> {
				if (upAt[0] < 0 && l != null && l.isAlive() && l.onGround() && l.getY() >= top - 0.01) {
					upAt[0] = tick;
				}
			});
		}
		helper.runAfterDelay(201, () -> {
			try {
				check(helper, upAt[0] >= 0, "the Lurker never stood on the ledge (at %s, the top at %s)", l.getY(), top);
				check(helper, l.getHealth() == l.getMaxHealth(), "the leap hurt the Lurker (%s)", l.getHealth());
			} finally {
				l.discard();
			}
			helper.succeed();
		});
	}

	/**
	 * Round a wall and up a step two blocks high: the big Lurker goes round the wall without turning where it stands
	 * (never more than a few tens of degrees in a tick, never round and round on the spot) and leaps up onto the step its
	 * prey stands on, the leap never taken through the wall.
	 */
	@GameTest(environment = Alone.LURKER_STRIDE, maxTicks = 360)
	public void theBigLurkerGoesRoundAWallAndLeapsAStepWithoutSpinning(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// A wall five high across most of the floor, a gap of two at its end; the prey on a step two high beyond it
		for (int z = 0; z <= 5; z++) {
			for (int y = 0; y <= 4; y++) {
				helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
			}
		}
		for (int x = 5; x <= 7; x++) {
			for (int z = 0; z <= 2; z++) {
				for (int y = 0; y <= 1; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		ServerPlayer p = TestPlayers.survivor(helper, "lurker-stride");
		place(helper, p, 6.5, 2, 1.5);
		Lurker[] in = {null};
		double top = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
		helper.runAfterDelay(20, () -> {
			in[0] = ModEntities.LURKER.spawn(level, helper.absolutePos(new BlockPos(1, 0, 1)), EntitySpawnReason.COMMAND);
			check(helper, in[0] != null, "no Lurker came");
			LurkerService.adopt(level, in[0], p, "hunt");
		});
		int first = 21, last = 330;
		float[] yaw = new float[last + 1];
		Vec3[] at = new Vec3[last + 1];
		int[] upAt = {-1};
		for (int t = first; t <= last; t++) {
			int tick = t;
			helper.runAfterDelay(t, () -> {
				if (in[0] == null || upAt[0] >= 0 || !in[0].isAlive()) {
					return;
				}
				yaw[tick] = in[0].getYRot();
				at[tick] = in[0].position();
				if (in[0].onGround() && in[0].getY() >= top - 0.01) {
					upAt[0] = tick;
				}
			});
		}
		helper.runAfterDelay(last + 1, () -> {
			try {
				int end = upAt[0] >= 0 ? upAt[0] : last;
				double most = 0, worstSpot = 0;
				// (from a few ticks in: its first look round to the way it goes is no spin)
				for (int t = first + 6; t <= end; t++) {
					if (at[t] != null && at[t - 1] != null) {
						most = Math.max(most, Math.abs(Mth.wrapDegrees(yaw[t] - yaw[t - 1])));
					}
				}
				for (int t = first + 6; t + 20 <= end; t++) {
					if (at[t] == null || at[t + 20] == null || at[t].distanceTo(at[t + 20]) >= 0.5) {
						continue;
					}
					double turned = 0;
					for (int k = t + 1; k <= t + 20; k++) {
						turned += Math.abs(Mth.wrapDegrees(yaw[k] - yaw[k - 1]));
					}
					worstSpot = Math.max(worstSpot, turned);
				}
				String where = in[0] == null ? "gone" : in[0].blockPosition().toString();
				check(helper, upAt[0] >= 0, "the Lurker never got up onto its prey's step (it is at %s)", where);
				check(helper, most <= 40, "the Lurker turned %.0f degrees in one tick", most);
				check(helper, worstSpot <= 150, "the Lurker turned %.0f degrees on the spot within a second", worstSpot);
				check(helper, in[0].getHealth() == in[0].getMaxHealth(), "its way up hurt the Lurker (%s)", in[0].getHealth());
			} finally {
				if (in[0] != null) {
					in[0].discard();
				}
			}
			helper.succeed();
		});
	}

	/**
	 * {@code /tether lurker <kind> <player>} (3.26.1): every kind takes a player (or a selector) after it, and calls its
	 * Lurker on them rather than on whoever typed it. A kind that finds no place there fails without an error.
	 */
	@GameTest(environment = Alone.LURKER_NAMED, maxTicks = 80)
	public void theLurkerCommandTakesAPlayerAfterEveryKind(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "lurker-named");
		place(helper, p, 3.5, 0, 3.5);
		helper.runAfterDelay(10, () -> {
			var server = level.getServer();
			var source = server.createCommandSourceStack().withSuppressedOutput();
			var dispatcher = server.getCommands().getDispatcher();
			try {
				for (String kind : java.util.List.of("watch", "stalk", "hunt", "drain", "swarm", "cave", "sculk", "magpie", "wick", "tidewalker",
						"gatebreaker", "lockwarden", "threadbiter", "edgecrawler", "cragback")) {
					var parsed = dispatcher.parse("tether lurker " + kind + " " + p.getScoreboardName(), source);
					check(helper, !parsed.getReader().canRead() && parsed.getExceptions().isEmpty(), "/tether lurker %s takes no player", kind);
				}
				dispatcher.execute("tether lurker watch " + p.getScoreboardName(), source);
				dispatcher.execute("tether lurker cragback @a[name=" + p.getScoreboardName() + "]", source);
			} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
				throw helper.assertionException("the command did not take a player: %s", e.getMessage());
			} finally {
				for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(96), e -> e instanceof Lurker)) {
					m.discard();
				}
			}
			helper.succeed();
		});
	}

	/** Extreme: the whole tribe at once, the young glowing red and biting. */
	@GameTest(environment = Alone.LURKER_SWARM, maxTicks = 260)
	public void aSwarmComesAllAtOnceAndBites(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Difficulty was = level.getDifficulty();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer p = TestPlayers.survivor(helper, "swarm-prey");
		// on the floor (a test player does not fall), where the young can get at them
		place(helper, p, 3.5, 0, 3.5);
		int lurkers = Config.LURKER_SWARM_LURKERS, young = Config.LURKER_SWARM_HOLLOWLINGS, secs = Config.LURKER_SWARM_SECONDS;
		double bite = Config.LURKER_SWARM_BITE, youngCurse = Config.HOLLOWLING_SWARM_CURSE_CHANCE, lurkerCurse = Config.LURKER_CURSE_CHANCE;
		int youngCurses = Config.HOLLOWLING_SWARM_CURSES_MAX;
		Runnable restore = () -> {
			Config.LURKER_SWARM_LURKERS = lurkers;
			Config.LURKER_SWARM_HOLLOWLINGS = young;
			Config.LURKER_SWARM_SECONDS = secs;
			Config.LURKER_SWARM_BITE = bite;
			Config.HOLLOWLING_SWARM_CURSE_CHANCE = youngCurse;
			Config.HOLLOWLING_SWARM_CURSES_MAX = youngCurses;
			Config.LURKER_CURSE_CHANCE = lurkerCurse;
			DataStore.get(p.getUUID()).curses.clear();
			for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(48), e -> e instanceof Lurker || e instanceof Hollowling)) {
				m.discard();
			}
			level.getServer().setDifficulty(was, true);
		};
		// past the grace a player has on joining (three seconds)
		helper.runAfterDelay(70, () -> {
			Config.LURKER_SWARM_LURKERS = 2;
			Config.LURKER_SWARM_HOLLOWLINGS = 4;
			Config.LURKER_SWARM_SECONDS = 30;
			Config.LURKER_SWARM_BITE = 3;
			// every bite that lands may curse, one curse for the whole swarm (and none from the Lurkers here)
			Config.HOLLOWLING_SWARM_CURSE_CHANCE = 1;
			Config.HOLLOWLING_SWARM_CURSES_MAX = 1;
			Config.LURKER_CURSE_CHANCE = 0;
			p.setHealth(20f);
			try {
				check(helper, LurkerService.swarm(p), "no swarm came");
				check(helper, HollowService.biting(p.getUUID()) >= 1, "no young came with the swarm");
				// (only this swarm's young: a stray one from another test may stand near)
				var hs = level.getEntitiesOfClass(Hollowling.class, new AABB(p.blockPosition()).inflate(40), e -> HollowService.bitingAt(e, p.getUUID()));
				check(helper, !hs.isEmpty() && hs.stream().allMatch(x -> x.getLurkerMood() == LurkerBody.MOOD_HUNTER), "a swarm's young do not glow red");
				// (brought to their prey at once, so the test waits on the bite, not on how far they spawned)
				for (Hollowling x : hs) {
					x.teleportTo(p.getX() + 1.2, p.getY(), p.getZ());
				}
			} catch (RuntimeException e) {
				restore.run();
				throw e;
			}
		});
		helper.runAfterDelay(170, () -> {
			try {
				check(helper, p.getHealth() < 20f, "the swarm's young did not bite (health %s)", p.getHealth());
				int left = HollowService.swarmCurses(p.getUUID());
				check(helper, left == 1, "the swarm's young left %s curses with their bites, not the one they may", left);
				check(helper, DataStore.get(p.getUUID()).curses.values().stream().mapToInt(Integer::intValue).sum() >= 1, "a curse from a bite was not laid");
			} finally {
				restore.run();
			}
			helper.succeed();
		});
	}

	/** Close to its prey it stands and faces it; a little further off it aims a step short of it, never at its middle. */
	@GameTest
	public void aLurkerStandsAtItsPreyInsteadOfCircling(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "lurker-circled");
		Lurker l = ModEntities.LURKER.spawn(level, helper.absolutePos(new BlockPos(2, 0, 2)), EntitySpawnReason.COMMAND);
		try {
			check(helper, l != null, "no Lurker came");
			place(helper, p, 2.5 + 1.3, 0, 2.5);
			TribeMoves.follow(l, p, 1.0);
			check(helper, !l.getMoveControl().hasWanted(), "a Lurker beside its prey still steps at it, and turns on the spot");
			place(helper, p, 2.5 + 4.0, 0, 2.5);
			TribeMoves.follow(l, p, 1.0);
			check(helper, l.getMoveControl().hasWanted(), "a Lurker four blocks from its prey does not step towards it");
			double dx = l.getMoveControl().getWantedX() - p.getX(), dz = l.getMoveControl().getWantedZ() - p.getZ();
			double gap = Math.sqrt(dx * dx + dz * dz);
			check(helper, gap > 1.2 && gap < 1.8, "a Lurker aims %s from its prey's middle, not a step short of it", gap);
		} finally {
			if (l != null) {
				l.discard();
			}
		}
		helper.succeed();
	}

	/** A house three blocks high around (4..6, 2..4), its door on the west side at x 3, z 3. */
	private static void hut(GameTestHelper helper) {
		for (int x = 3; x <= 7; x++) {
			for (int z = 1; z <= 5; z++) {
				helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
				for (int y = 0; y <= 2; y++) {
					boolean wall = x == 3 || x == 7 || z == 1 || z == 5;
					boolean door = x == 3 && z == 3;
					helper.setBlock(new BlockPos(x, y, z), wall && !door ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	/** Come to steal from a prey hiding in a house too low for it, a Lurker stands outside and sends its young in. */
	@GameTest(environment = Alone.LURKER_HIDE, maxTicks = 260)
	public void aLurkerSendsItsYoungAfterAPreyInAHouse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		hut(helper);
		ServerPlayer p = TestPlayers.survivor(helper, "lurker-hider");
		place(helper, p, 5.5, 0, 3.5);
		// (it comes once the player's grace on joining is over, on the test's own floor round the corner from the door;
		// one nobody drives dissolves, so it is taken in hand at once)
		Lurker[] in = {null};
		helper.runAfterDelay(70, () -> {
			in[0] = ModEntities.LURKER.spawn(level, helper.absolutePos(new BlockPos(1, 0, 6)), EntitySpawnReason.COMMAND);
			check(helper, in[0] != null, "no Lurker came");
			p.getInventory().setItem(0, new ItemStack(Items.DIRT, 16));
			LurkerService.adopt(level, in[0], p, "stalk");
		});
		// What it does is watched every tick: the young may bring it the dirt, and then it runs off with it
		boolean[] heldOut = {false};
		int[] young = {0};
		for (int t = 71; t <= 200; t++) {
			helper.runAfterDelay(t, () -> {
				if (in[0] != null) {
					heldOut[0] |= LurkerService.holdingOut(in[0]);
					young[0] = Math.max(young[0], LurkerService.sentYoung(in[0]) ? HollowService.errands(p.getUUID()) : 0);
				}
			});
		}
		helper.runAfterDelay(201, () -> {
			try {
				check(helper, heldOut[0], "the Lurker never gave up the way into a house too low for it (it is %s at %s)", LurkerService.mode(in[0]), in[0].blockPosition());
				check(helper, young[0] >= 1, "the Lurker sent no young in after its prey");
			} finally {
				for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(32), e -> e instanceof Lurker || e instanceof Hollowling)) {
					m.discard();
				}
			}
			helper.succeed();
		});
	}

	/** Come to hurt a prey it can see under a roof it cannot get under, a Lurker throws an ink bolt through the door. */
	@GameTest(environment = Alone.LURKER_BOLT, maxTicks = 420)
	public void aHuntingLurkerThrowsInkAtAPreyUnderARoof(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Difficulty was = level.getDifficulty();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		hut(helper);
		ServerPlayer p = TestPlayers.survivor(helper, "lurker-sheltered");
		place(helper, p, 5.5, 0, 3.5);
		// (on the test's own floor before the door, too far from the prey for its arms)
		Lurker[] in = {null};
		double curse = Config.LURKER_CURSE_CHANCE;
		int young = Config.LURKER_YOUNG;
		helper.runAfterDelay(70, () -> {
			in[0] = ModEntities.LURKER.spawn(level, helper.absolutePos(new BlockPos(1, 0, 3)), EntitySpawnReason.COMMAND);
			check(helper, in[0] != null, "no Lurker came");
			Config.LURKER_CURSE_CHANCE = 0;
			Config.LURKER_YOUNG = 0;
			p.setHealth(20f);
			LurkerService.adopt(level, in[0], p, "hunt");
		});
		// Watched every tick: a player heals back what one bolt took well before the test ends (the Lurker may first go
		// round the house, and back to the door to see its prey)
		boolean[] heldOut = {false};
		float[] lowest = {20f};
		for (int t = 71; t <= 390; t++) {
			helper.runAfterDelay(t, () -> {
				if (in[0] != null) {
					heldOut[0] |= LurkerService.holdingOut(in[0]);
				}
				lowest[0] = Math.min(lowest[0], p.getHealth());
			});
		}
		helper.runAfterDelay(391, () -> {
			try {
				check(helper, heldOut[0], "the Lurker never gave up the way under a roof too low for it (it is %s at %s)", LurkerService.mode(in[0]), in[0].blockPosition());
				check(helper, lowest[0] < 20f, "no ink bolt reached the prey through the door (its blows: %s; %s)", LurkerService.blows(in[0]),
						LurkerService.holdOutState(in[0]));
			} finally {
				Config.LURKER_CURSE_CHANCE = curse;
				Config.LURKER_YOUNG = young;
				for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(32), e -> e instanceof Lurker || e instanceof Hollowling)) {
					m.discard();
				}
				level.getServer().setDifficulty(was, true);
			}
			helper.succeed();
		});
	}

	/** The dread before a swarm: it swells first, the swarm waits for it, and leaving while it swells is a flight too. */
	@GameTest(environment = Alone.SWARM_DREAD, maxTicks = 300)
	public void theSwarmWaitsForItsDread(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Difficulty was = level.getDifficulty();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer p = TestPlayers.survivor(helper, "dread-prey");
		place(helper, p, 3.5, 0, 3.5);
		int lurkers = Config.LURKER_SWARM_LURKERS, young = Config.LURKER_SWARM_HOLLOWLINGS, dread = Config.AMBIENT_DREAD;
		Runnable restore = () -> {
			Config.LURKER_SWARM_LURKERS = lurkers;
			Config.LURKER_SWARM_HOLLOWLINGS = young;
			Config.AMBIENT_DREAD = dread;
			for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(48), e -> e instanceof Lurker || e instanceof Hollowling)) {
				m.discard();
			}
			level.getServer().setDifficulty(was, true);
		};
		helper.runAfterDelay(70, () -> {
			try {
				Config.LURKER_SWARM_LURKERS = 1;
				Config.LURKER_SWARM_HOLLOWLINGS = 0;
				Config.AMBIENT_DREAD = 1;
				check(helper, LurkerService.swarmComes(p), "no swarm set out");
				check(helper, LurkerService.swarmComing(p) && LurkerService.swarming(p) == 0, "the swarm did not wait for its dread (%s came at once)",
						LurkerService.swarming(p));
				check(helper, "swarm".equals(FleeService.danger(p)), "the dread of a swarm left the player in danger %s", FleeService.danger(p));
			} catch (RuntimeException e) {
				restore.run();
				throw e;
			}
		});
		helper.runAfterDelay(70 + AmbientService.DREAD_TICKS + 5, () -> {
			try {
				check(helper, !LurkerService.swarmComing(p) && LurkerService.swarming(p) >= 1, "the swarm did not come when its dread broke off (%s came)",
						LurkerService.swarming(p));
			} finally {
				restore.run();
			}
			helper.succeed();
		});
	}

	/** The wound's sounds fit where a player is: deep down, by a hoard at night, in the Nether's biomes, the End, the Fray. */
	@GameTest
	public void theWoundSoundsFitThePlace(GameTestHelper helper) {
		java.util.function.Function<AmbientService.Place, java.util.Set<String>> fits = pl -> new java.util.HashSet<>(AmbientService.choices(pl));
		AmbientService.Place deep = new AmbientService.Place("overworld", "other", -30, false, true, false, false, false, false, false, false);
		check(helper, fits.apply(deep).equals(java.util.Set.of("deep_groan", "far_steps")), "under the deepslate: %s", fits.apply(deep));
		AmbientService.Place hoard = new AmbientService.Place("overworld", "other", 70, true, false, true, true, false, false, false, false);
		check(helper, fits.apply(hoard).contains("hollow_far") && fits.apply(hoard).contains("far_shriek") && !fits.apply(hoard).contains("storm_tear"),
				"by a hoard at night: %s", fits.apply(hoard));
		AmbientService.Place day = new AmbientService.Place("overworld", "other", 70, true, false, false, false, false, false, false, false);
		check(helper, fits.apply(day).isEmpty(), "out in the day: %s", fits.apply(day));
		AmbientService.Place torn = new AmbientService.Place("overworld", "other", 70, true, false, false, false, true, false, false, false);
		check(helper, fits.apply(torn).equals(java.util.Set.of("storm_tear")), "under a torn sky: %s", fits.apply(torn));
		AmbientService.Place deltas = new AmbientService.Place("the_nether", "basalt_deltas", 30, false, false, false, false, false, false, false, false);
		check(helper, fits.apply(deltas).equals(java.util.Set.of("basalt_deep", "undertow_nether", "ferryman_bell")), "in the deltas: %s", fits.apply(deltas));
		AmbientService.Place wastes = new AmbientService.Place("the_nether", "nether_wastes", 80, false, false, false, false, false, false, false, false);
		check(helper, fits.apply(wastes).equals(java.util.Set.of("ember_pulse")), "high in the wastes: %s", fits.apply(wastes));
		AmbientService.Place outer = new AmbientService.Place("the_end", "other", 60, true, false, false, false, false, false, false, true);
		check(helper, fits.apply(outer).equals(java.util.Set.of("undertow_end", "abyss_call")), "on the outer islands: %s", fits.apply(outer));
		AmbientService.Place knot = new AmbientService.Place("the_seam", "other", 20, false, true, false, false, false, true, true, false);
		java.util.List<String> byKnot = AmbientService.choices(knot);
		check(helper, java.util.Collections.frequency(byKnot, "knot_heartbeat") >= 4 && byKnot.contains("undertow_fray"), "by a knot in the Fray: %s", byKnot);
		AmbientService.Place seam = new AmbientService.Place("the_seam", "other", 120, true, false, false, false, false, false, false, false);
		check(helper, fits.apply(seam).isEmpty(), "on the Seam's islands: %s", fits.apply(seam));
		helper.succeed();
	}

	/**
	 * No way out by logging out: a blow from the tribe, then a swarm, put a player in danger; leaving then is noted, and
	 * back they carry a curse and the swarm comes again. One who leaves in peace pays nothing.
	 */
	@GameTest(environment = Alone.FLEE, maxTicks = 200)
	public void leavingWhileTheTribeHuntsCostsACurse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Difficulty was = level.getDifficulty();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer p = TestPlayers.survivor(helper, "flee-prey");
		ServerPlayer calm = TestPlayers.survivor(helper, "flee-calm");
		place(helper, p, 3.5, 0, 3.5);
		place(helper, calm, 6.5, 0, 6.5);
		int lurkers = Config.LURKER_SWARM_LURKERS, young = Config.LURKER_SWARM_HOLLOWLINGS, curses = Config.FLEE_CURSES, returns = Config.FLEE_RETURNS;
		Runnable restore = () -> {
			Config.LURKER_SWARM_LURKERS = lurkers;
			Config.LURKER_SWARM_HOLLOWLINGS = young;
			Config.FLEE_CURSES = curses;
			Config.FLEE_RETURNS = returns;
			for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(48), e -> e instanceof Lurker || e instanceof Hollowling)) {
				m.discard();
			}
			level.getServer().setDifficulty(was, true);
		};
		// past the grace a player has on joining (three seconds)
		helper.runAfterDelay(70, () -> {
			try {
				Config.LURKER_SWARM_LURKERS = 1;
				Config.LURKER_SWARM_HOLLOWLINGS = 2;
				Config.FLEE_CURSES = 1;
				Config.FLEE_RETURNS = 1;
				PlayerData d = DataStore.get(p.getUUID());
				d.fled = null;
				// A blow from one of the tribe: in danger for a while
				Lurker striker = ModEntities.LURKER.spawn(level, helper.absolutePos(new BlockPos(1, 0, 1)), EntitySpawnReason.COMMAND);
				check(helper, striker != null, "no Lurker came");
				p.hurtServer(level, striker.damageSources().mobAttack(striker), 1f);
				striker.discard();
				check(helper, "blow".equals(FleeService.danger(p)), "a blow from the tribe left the player in danger %s", FleeService.danger(p));
				check(helper, FleeService.danger(calm) == null, "a player nothing touched is in danger %s", FleeService.danger(calm));
				// The lock is known (3.25.1): to the player in the pause menu (never during play), to a tablist that asks
				check(helper, FleeService.held(p) && !FleeService.held(calm), "the lock would not be shown rightly (held %s, calm %s)", FleeService.held(p),
						FleeService.held(calm));
				check(helper, "Hunted".equals(com.extremedimensions.compat.TabPlaceholders.values(p).get("danger").getString())
						&& com.extremedimensions.compat.TabPlaceholders.values(calm).get("danger").getString().isEmpty(), "the danger placeholder is wrong");
				// A swarm: leaving now is a flight from it
				check(helper, LurkerService.swarm(p), "no swarm came");
				check(helper, "swarm".equals(FleeService.danger(p)), "a swarm left the player in danger %s", FleeService.danger(p));
				FleeService.leaving(p, level.getServer());
				FleeService.leaving(calm, level.getServer());
				check(helper, "swarm".equals(d.fled), "leaving in a swarm was noted as %s", d.fled);
				check(helper, DataStore.get(calm.getUUID()).fled == null, "leaving in peace was noted as a flight");
				for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(48), e -> e instanceof Lurker || e instanceof Hollowling)) {
					m.discard();
				}
				// Back: a curse, and the swarm again
				int before = curses(d);
				FleeService.collect(p, d);
				check(helper, curses(d) == before + 1, "coming back after a flight left %s curses, not one", curses(d) - before);
				check(helper, d.fled == null, "the flight is still owed after it was paid");
				if (WorldService.tribeSteals()) {
					check(helper, LurkerService.threat(p) != null, "the swarm did not come back for the one who left");
				}
			} finally {
				restore.run();
			}
			helper.succeed();
		});
	}

	/** A world from before 3.8 on a difficulty takes its swarm and its standing settings; one of its own rules keeps them. */
	@GameTest
	public void aWorldKeepsItsDifficultyThroughTheStandingUpgrade(GameTestHelper helper) {
		JsonObject extreme = new JsonObject();
		extreme.addProperty("config_version", 4);
		JsonObject custom = new JsonObject();
		custom.addProperty("config_version", 4);
		for (var e : Preset.EXTREME.values.entrySet()) {
			String k = e.getKey();
			if (!k.startsWith("tribe_") && !k.equals("lurker_swarm_chance")) {
				extreme.addProperty(k, Double.parseDouble(e.getValue()));
				custom.addProperty(k, Double.parseDouble(e.getValue()));
			}
		}
		// as a 3.7 file holds them: the old defaults
		for (JsonObject j : new JsonObject[] {extreme, custom}) {
			j.addProperty("tribe_rep_gift", 3);
			j.addProperty("tribe_rep_bond", 10);
			j.addProperty("tribe_rep_barter", 1);
		}
		custom.addProperty("atone_base", 999);
		Config.migrate(extreme);
		Config.migrate(custom);
		check(helper, extreme.get("lurker_swarm_chance").getAsDouble() == 0.15 && extreme.get("tribe_rep_gift").getAsInt() == 1
				&& extreme.get("tribe_gift_minutes").getAsInt() == 30 && extreme.get("tribe_gift_cost").getAsInt() == 3
				&& extreme.get("tribe_rep_bond").getAsInt() == 3 && extreme.get("tribe_rep_barter").getAsInt() == 0,
				"an Extreme world did not take Extreme's swarm and standing: %s", extreme);
		check(helper, custom.get("tribe_rep_gift").getAsInt() == 2 && custom.get("tribe_rep_bond").getAsInt() == 6 && !custom.has("lurker_swarm_chance"),
				"a world of its own rules did not follow the new defaults alone: %s", custom);
		helper.succeed();
	}

	/** A player bounced by the wound is told what is missing, and the hints come where the portal and the lodestone are. */
	@GameTest
	public void aNewPlayerIsToldWhatOpensTheWound(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "wound-learner");
		PlayerData d = DataStore.get(p.getUUID());
		// Far off, where no other test's anchor stands
		d.hasLast = true;
		d.lastX = 70000.5;
		d.lastY = 70;
		d.lastZ = 70000.5;
		check(helper, "no_anchor.nether".equals(ExpeditionService.dormant(p, d, Dims.NETHER, 0)), "with no anchor: %s",
				ExpeditionService.dormant(p, d, Dims.NETHER, 0));
		check(helper, "no_anchor.end".equals(ExpeditionService.dormant(p, d, Dims.END, 0)), "with no anchor by an End portal: %s",
				ExpeditionService.dormant(p, d, Dims.END, 0));
		Anchor a = new Anchor(70002, 70, 70000, Dims.NETHER);
		DataStore.anchors().put(a.key(), a);
		String bound = d.tetherAnchor;
		try {
			a.blocked = true;
			check(helper, "blocked".equals(ExpeditionService.dormant(p, d, Dims.NETHER, 0)), "with the way blocked: %s",
					ExpeditionService.dormant(p, d, Dims.NETHER, 0));
			a.blocked = false;
			check(helper, "no_charge".equals(ExpeditionService.dormant(p, d, Dims.NETHER, 0)), "with none of their time: %s",
					ExpeditionService.dormant(p, d, Dims.NETHER, 0));
			d.tetherAnchor = null;
			check(helper, "no_tether".equals(ExpeditionService.dormant(p, d, Dims.NETHER, 30)), "not bound: %s",
					ExpeditionService.dormant(p, d, Dims.NETHER, 30));
			d.tetherAnchor = "1|2|3";
			check(helper, "elsewhere".equals(ExpeditionService.dormant(p, d, Dims.NETHER, 30)), "bound elsewhere: %s",
					ExpeditionService.dormant(p, d, Dims.NETHER, 30));
		} finally {
			DataStore.anchors().remove(a.key());
			d.tetherAnchor = bound;
		}
		// Beside an End portal frame, a Nether portal and with a lodestone in hand: a word on each, once
		place(helper, p, 2.5, 2, 2.5);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(4, 2, 2), Blocks.END_PORTAL_FRAME.defaultBlockState());
		Hints.look(p, d);
		check(helper, Hints.offered(p, Hints.Hint.END_PORTAL), "no word beside an End portal");
		level.setBlock(helper.absolutePos(new BlockPos(2, 2, 4)), Blocks.NETHER_PORTAL.defaultBlockState(), 2 | 16);
		Hints.look(p, d);
		check(helper, Hints.offered(p, Hints.Hint.PORTAL), "no word beside a Nether portal before any anchor");
		p.getInventory().add(new ItemStack(Items.LODESTONE));
		Hints.look(p, d);
		check(helper, Hints.offered(p, Hints.Hint.LODESTONE), "no word on a lodestone in hand");
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.AIR.defaultBlockState());
		helper.succeed();
	}

	/**
	 * The tribe swims with only its head out of the water: each body floats with the water up to a little under its
	 * eyes, not with vanilla's 0.4 blocks at its feet (which left a Lurker three-quarters out of the water).
	 */
	@GameTest
	public void theTribeSwimsWithOnlyItsHeadOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (net.minecraft.world.entity.EntityType<? extends Mob> type : java.util.List.of(ModEntities.LURKER, ModEntities.CAVE_LURKER,
				ModEntities.SCULK_LURKER, ModEntities.HOLLOWLING, ModEntities.KEEPER, ModEntities.THINNED)) {
			Mob m = type.create(level, EntitySpawnReason.EVENT);
			check(helper, m != null, "no %s could be made", type);
			double depth = m.getFluidJumpThreshold(), eyes = m.getEyeHeight(), height = m.getBbHeight();
			check(helper, Math.abs(depth - Math.max(0.4, eyes - 0.3)) < 1e-6, "%s floats with %s of water at its feet, not just under its eyes (%s)",
					type, depth, eyes);
			check(helper, height < 1.2 || depth >= height * 0.6, "%s floats with most of it out of the water (%s of %s under)", type, depth, height);
			m.discard();
		}
		helper.succeed();
	}
}
