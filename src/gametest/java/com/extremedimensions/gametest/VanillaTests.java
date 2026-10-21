package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.HashSet;
import java.util.Set;

import com.extremedimensions.config.Config;
import com.extremedimensions.elder.ElderSpells;
import com.extremedimensions.elder.ElderTerrain;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.expedition.ExpeditionBar;
import com.extremedimensions.lore.Loot;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModEffects;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.relic.Relic;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.Rules;
import com.extremedimensions.server.WorldFx;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * The mod as part of Minecraft: its timer is a boss bar, its sounds sit on the game's volume sliders, its recipes come
 * into the recipe book the way vanilla's do.
 */
public class VanillaTests {
	/** An expedition shows a boss bar named for where the player is; it goes when the expedition ends or the setting is off. */
	@GameTest
	public void theExpeditionTimerIsABossBar(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-bar");
		PlayerData d = DataStore.get(p.getUUID());
		boolean inRun = d.inRun;
		String dim = d.dim;
		double max = d.runMax;
		int setting = Config.HUD_BOSS_BAR;
		try {
			d.inRun = true;
			d.dim = Dims.NETHER;
			d.runMax = 300;
			Config.HUD_BOSS_BAR = 1;
			ExpeditionBar.update(p, d);
			check(helper, ExpeditionBar.showing(p.getUUID()), "an expedition in the Nether shows no boss bar");
			check(helper, ExpeditionBar.label(p.getUUID()).toLowerCase(java.util.Locale.ROOT).contains("nether"),
					"the boss bar is not named for the Nether: %s", ExpeditionBar.label(p.getUUID()));
			Config.HUD_BOSS_BAR = 0;
			ExpeditionBar.update(p, d);
			check(helper, !ExpeditionBar.showing(p.getUUID()), "with the setting off the boss bar stays");
			Config.HUD_BOSS_BAR = 1;
			d.inRun = false;
			ExpeditionBar.update(p, d);
			check(helper, !ExpeditionBar.showing(p.getUUID()), "the boss bar stays after the expedition");
		} finally {
			d.inRun = inRun;
			d.dim = dim;
			d.runMax = max;
			Config.HUD_BOSS_BAR = setting;
			ExpeditionBar.forget(p.getUUID());
		}
		helper.succeed();
	}

	/** The arrow in the timer points the way home from where the player looks. */
	@GameTest
	public void theWayHomeArrowTurnsWithThePlayer(GameTestHelper helper) {
		// looking south (yaw 0): south is ahead, west to the right, north behind
		check(helper, ExpeditionBar.arrow(0, 0, 10).equals("↑"), "ahead is not up");
		check(helper, ExpeditionBar.arrow(0, -10, 0).equals("→"), "west, looking south, is not to the right");
		check(helper, ExpeditionBar.arrow(0, 0, -10).equals("↓"), "behind is not down");
		check(helper, ExpeditionBar.arrow(0, 10, 10).equals("↖"), "south-east, looking south, is not ahead and to the left");
		// looking east (yaw -90) and west (yaw 90) at a rift to the east
		check(helper, ExpeditionBar.arrow(-90, 10, 0).equals("↑"), "looking east, east is not ahead");
		check(helper, ExpeditionBar.arrow(90, 10, 0).equals("↓"), "looking west, east is not behind");
		helper.succeed();
	}

	/** Every sound of the mod plays on the volume slider Minecraft would put it on, never on Master alone. */
	@GameTest
	public void soundsSitOnTheGamesSliders(GameTestHelper helper) {
		for (Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
			if (id.getNamespace().equals("extremedimensions")) {
				check(helper, WorldFx.category(id.toString()) != SoundSource.MASTER, "%s plays on Master", id);
			}
		}
		check(helper, WorldFx.category("extremedimensions:seam_ambient") == SoundSource.AMBIENT, "the hum of the Seam is not ambient");
		check(helper, WorldFx.category("extremedimensions:lurker_whisper") == SoundSource.HOSTILE, "a Lurker's whisper is not hostile");
		check(helper, WorldFx.category("extremedimensions:music.boss_weaver") == SoundSource.MUSIC, "boss music is not music");
		check(helper, WorldFx.category("minecraft:block.wool.break") == SoundSource.BLOCKS, "a block's sound is not on Blocks");
		check(helper, WorldFx.category("extremedimensions:portal_home") == SoundSource.PLAYERS, "going home is not on Players");
		helper.succeed();
	}

	/** Every recipe of the mod has an advancement that puts it in the recipe book, as vanilla's own recipes do. */
	@GameTest
	public void everyRecipeFindsItsWayIntoTheBook(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Set<ResourceKey<Recipe<?>>> rewarded = new HashSet<>();
		for (AdvancementHolder a : server.getAdvancements().getAllAdvancements()) {
			rewarded.addAll(a.value().rewards().recipes());
		}
		int found = 0;
		for (RecipeHolder<?> r : server.getRecipeManager().getRecipes()) {
			Identifier id = r.id().identifier();
			if (id.getNamespace().equals("extremedimensions") || id.equals(Identifier.withDefaultNamespace("lodestone"))) {
				check(helper, rewarded.contains(r.id()), "no advancement puts %s in the recipe book", id);
				found++;
			}
		}
		check(helper, found >= 121, "only %s of the mod's recipes were found", found);
		helper.succeed();
	}

	/** Seam Silk in the inventory opens what is woven from it. */
	@GameTest
	public void silkInHandOpensItsRecipes(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-book");
		ResourceKey<Recipe<?>> woven = ResourceKey.create(Registries.RECIPE, WorldFx.id("extremedimensions:soul_woven_tether"));
		check(helper, !p.getRecipeBook().contains(woven), "the Soul-Woven Tether was in the book before any silk");
		p.getInventory().add(new ItemStack(ModItems.SEAM_SILK));
		p.inventoryMenu.broadcastChanges();
		helper.runAfterDelay(2, () -> {
			check(helper, p.getRecipeBook().contains(woven), "holding Seam Silk did not put the Soul-Woven Tether in the book");
			helper.succeed();
		});
	}

	/** Binding a relic earns its advancement. */
	@GameTest
	public void bindingARelicIsMarked(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-relic-mark");
		PlayerData d = DataStore.get(p.getUUID());
		String before = d.relic;
		try {
			d.relic = null;
			RelicService.bind(p, new ItemStack(RelicService.item(Relic.FEATHERBONE)));
			AdvancementHolder a = helper.getLevel().getServer().getAdvancements().get(WorldFx.id("extremedimensions:" + Advancements.RELIC_BOUND));
			check(helper, a != null, "the relic advancement did not load");
			check(helper, p.getAdvancements().getOrStartProgress(a).isDone(), "binding a relic did not earn its advancement");
		} finally {
			d.relic = before;
		}
		helper.succeed();
	}

	/** A barrel of the Fray is filled from the mod's loot table: silk, and always a page. */
	@GameTest
	public void theSeamsBarrelsComeFromTheirTables(GameTestHelper helper) {
		double relic = Config.RELIC_SEAM_CHEST_CHANCE;
		try {
			Config.RELIC_SEAM_CHEST_CHANCE = 0;
			Container c = barrel(helper, new BlockPos(1, 1, 1));
			SeamService.fillLoot(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), 4);
			check(helper, count(c, ModItems.SEAM_SILK) >= 2, "a tangle held %s silk", count(c, ModItems.SEAM_SILK));
			check(helper, count(c, ModItems.WANDERERS_PAGE) == 1, "a tangle held %s pages, not one", count(c, ModItems.WANDERERS_PAGE));
		} finally {
			Config.RELIC_SEAM_CHEST_CHANCE = relic;
		}
		helper.succeed();
	}

	/** A table put in place of the mod's (as a datapack would) is what the barrel holds. */
	@GameTest
	public void aDatapackCanChangeWhatTheSeamHolds(GameTestHelper helper) {
		double relic = Config.RELIC_SEAM_CHEST_CHANCE;
		try {
			Config.RELIC_SEAM_CHEST_CHANCE = 0;
			Loot.standIn(Loot.SEAM_LOOM, Identifier.fromNamespaceAndPath("extremedimensions_test", "stand_in"));
			Container c = barrel(helper, new BlockPos(2, 1, 2));
			SeamService.fillLoot(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), 1);
			check(helper, count(c, Items.BONE) == 3, "the barrel held %s bones, not the stand-in's 3", count(c, Items.BONE));
			check(helper, count(c, ModItems.SEAM_SILK) == 0, "the barrel still held silk from the mod's own table");
		} finally {
			Loot.standIn(Loot.SEAM_LOOM, null);
			Config.RELIC_SEAM_CHEST_CHANCE = relic;
		}
		helper.succeed();
	}

	/** The Wanderer's pages turn up in ruined portals: the mod's table rides along in vanilla's. */
	@GameTest
	public void ruinedPortalsHoldTheWanderersPages(GameTestHelper helper) {
		var table = helper.getLevel().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.RUINED_PORTAL);
		LootParams params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
		int pages = 0;
		for (int i = 0; i < 80; i++) {
			for (ItemStack s : table.getRandomItems(params)) {
				if (s.is(ModItems.WANDERERS_PAGE)) {
					pages++;
				}
			}
		}
		check(helper, pages > 0, "eighty ruined portal chests held no page of the Wanderer");
		helper.succeed();
	}

	private static Container barrel(GameTestHelper helper, BlockPos at) {
		helper.getLevel().setBlock(helper.absolutePos(at), Blocks.BARREL.defaultBlockState(), 3);
		return (Container) helper.getLevel().getBlockEntity(helper.absolutePos(at));
	}

	private static int count(Container c, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).is(item)) {
				n += c.getItem(i).getCount();
			}
		}
		return n;
	}

	/**
	 * On peaceful nothing of the wound wakes to fight, and what is out goes, as vanilla's monsters do; the Starved One
	 * stays, as the Ender Dragon does. Difficulty is set and put back within the tick, so no other test sees it.
	 */
	@GameTest
	public void peacefulKeepsTheWoundQuiet(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Difficulty before = helper.getLevel().getDifficulty();
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		Mob lurker = ModEntities.LURKER.spawn(helper.getLevel(), at, EntitySpawnReason.COMMAND);
		Mob starved = ModEntities.STARVED_ONE.spawn(helper.getLevel(), at.offset(2, 0, 0), EntitySpawnReason.COMMAND);
		try {
			check(helper, lurker != null && starved != null, "the test creatures did not spawn");
			server.setDifficulty(Difficulty.PEACEFUL, true);
			check(helper, Rules.peaceful(helper.getLevel()), "the mod does not read peaceful");
			lurker.checkDespawn();
			starved.checkDespawn();
			check(helper, lurker.isRemoved(), "a Lurker stayed on peaceful");
			check(helper, !starved.isRemoved(), "the Starved One left on peaceful, and with it the end of the story");
		} finally {
			server.setDifficulty(before, true);
			if (lurker != null) {
				lurker.discard();
			}
			if (starved != null) {
				starved.discard();
			}
		}
		helper.succeed();
	}

	/** A raised shield stops a Keeper's blow, as it stops any monster's. */
	@GameTest
	public void aShieldStopsTheKeepersBlow(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-shield");
		BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		Mob keeper = ModEntities.KEEPER.spawn(helper.getLevel(), at.offset(0, 0, 3), EntitySpawnReason.COMMAND);
		try {
			check(helper, keeper != null, "the Keeper did not spawn");
			// facing south, at the Keeper, the shield raised for longer than it takes to come up
			p.setYRot(0);
			p.setYHeadRot(0);
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
			p.startUsingItem(InteractionHand.OFF_HAND);
			for (int i = 0; i < 10; i++) {
				p.doTick();
			}
			check(helper, p.isBlocking(), "the shield did not come up");
			float health = p.getHealth();
			boolean landed = p.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(keeper), 9f);
			check(helper, !landed && p.getHealth() == health, "a Keeper's blow went through a raised shield");
		} finally {
			p.stopUsingItem();
			if (keeper != null) {
				keeper.discard();
			}
		}
		helper.succeed();
	}

	/** Milk cures what a Frayed Ray's sting leaves; under the Matriarch's hex (by design) it will not go down. */
	@GameTest
	public void milkCuresButNotUnderTheHex(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-milk");
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
		new ItemStack(Items.MILK_BUCKET).finishUsingItem(helper.getLevel(), p);
		check(helper, !p.hasEffect(MobEffects.POISON), "milk did not cure the poison");
		int setting = Config.MATRIARCH_HEX_MILK;
		try {
			Config.MATRIARCH_HEX_MILK = 1;
			p.addEffect(new MobEffectInstance(ModEffects.HEX, 200));
			check(helper, ElderSpells.refuse(p, new ItemStack(Items.MILK_BUCKET)) == InteractionResult.FAIL, "milk went down under the hex");
			check(helper, ElderSpells.refuse(p, new ItemStack(Items.BREAD)) == InteractionResult.PASS, "the hex refused bread");
		} finally {
			Config.MATRIARCH_HEX_MILK = setting;
			p.removeEffect(ModEffects.HEX);
		}
		helper.succeed();
	}

	/** With mobGriefing off the Elders tear up nothing; set and put back within the tick. */
	@GameTest
	public void mobGriefingKeepsTheEldersFromTheGround(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		GameRules rules = helper.getLevel().getGameRules();
		boolean before = rules.get(GameRules.MOB_GRIEFING);
		int setting = Config.ELDER_GRIEFING;
		try {
			Config.ELDER_GRIEFING = 1;
			rules.set(GameRules.MOB_GRIEFING, false, server);
			check(helper, !ElderTerrain.griefing(helper.getLevel()), "the Elders tear up the ground with mobGriefing off");
			rules.set(GameRules.MOB_GRIEFING, true, server);
			check(helper, ElderTerrain.griefing(helper.getLevel()), "the Elders leave the ground alone with mobGriefing on");
		} finally {
			rules.set(GameRules.MOB_GRIEFING, before, server);
			Config.ELDER_GRIEFING = setting;
		}
		helper.succeed();
	}
}
