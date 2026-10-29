package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import com.extremedimensions.config.Config;
import com.extremedimensions.config.Preset;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.expedition.ExpeditionService;
import com.extremedimensions.guide.Hints;
import com.extremedimensions.hollow.Hollowling;
import com.extremedimensions.lurker.Lurker;
import com.extremedimensions.lurker.LurkerBody;
import com.extremedimensions.lurker.LurkerService;
import com.extremedimensions.lurker.TribeMoves;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.server.Dims;
import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * The Lurker as a threat (it claws as it reaches, takes several things and may leave a curse; come to hurt, it glows red
 * and strikes), the tribe's way of walking (out of lava and water, on the ground, never in a tree), a world on a
 * difficulty keeping it through the upgrade, and what a new player is told about the wound.
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
