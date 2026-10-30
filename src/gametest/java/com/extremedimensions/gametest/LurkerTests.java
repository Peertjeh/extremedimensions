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
	private static void place(GameTestHelper helper, ServerPlayer p, double x, double y, double z) {
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.snapTo(at.x, at.y, at.z, 0f, 0f);
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
		helper.runAfterDelay(140, () -> {
			try {
				check(helper, l.isAlive() && l.getY() >= top - 0.01, "the Lurker stayed below the ledge (at %s, the top at %s)", l.getY(), top);
				check(helper, l.getHealth() == l.getMaxHealth(), "the leap hurt the Lurker (%s)", l.getHealth());
			} finally {
				l.discard();
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
		double bite = Config.LURKER_SWARM_BITE;
		Runnable restore = () -> {
			Config.LURKER_SWARM_LURKERS = lurkers;
			Config.LURKER_SWARM_HOLLOWLINGS = young;
			Config.LURKER_SWARM_SECONDS = secs;
			Config.LURKER_SWARM_BITE = bite;
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
			p.setHealth(20f);
			try {
				check(helper, LurkerService.swarm(p), "no swarm came");
				check(helper, HollowService.biting(p.getUUID()) >= 1, "no young came with the swarm");
				var hs = level.getEntitiesOfClass(Hollowling.class, new AABB(p.blockPosition()).inflate(40), e -> true);
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
			} finally {
				restore.run();
			}
			helper.succeed();
		});
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
		check(helper, d.hints.contains(Hints.Hint.END_PORTAL.key()), "no word beside an End portal");
		level.setBlock(helper.absolutePos(new BlockPos(2, 2, 4)), Blocks.NETHER_PORTAL.defaultBlockState(), 2 | 16);
		Hints.look(p, d);
		check(helper, d.hints.contains(Hints.Hint.PORTAL.key()), "no word beside a Nether portal before any anchor");
		p.getInventory().add(new ItemStack(Items.LODESTONE));
		Hints.look(p, d);
		check(helper, d.hints.contains(Hints.Hint.LODESTONE.key()), "no word on a lodestone in hand");
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.AIR.defaultBlockState());
		helper.succeed();
	}
}
