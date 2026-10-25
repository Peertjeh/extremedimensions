package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.extremedimensions.config.Config;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.seam.SeamArchitect;
import com.extremedimensions.seam.SeamDepth;
import com.extremedimensions.seam.Terrain;
import com.extremedimensions.tetherite.SpindleBlock;
import com.extremedimensions.tetherite.Tethered;
import com.extremedimensions.tetherite.Tetherite;
import com.extremedimensions.tetherite.TetheriteTrims;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Tetherite (3.3): the ore deep in the Seam, the Spindle that spins Tether Fragments into cord, the ingot, the upgrade of
 * netherite gear, what the gear does (tethered, and the full set's ease on the drain), and the Seam's armor trims.
 */
public class TetheriteTests {
	private static double attack(Item item) {
		double sum = 0;
		ItemAttributeModifiers mods = new ItemStack(item).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
			if (e.attribute().value() == Attributes.ATTACK_DAMAGE.value()) {
				sum += e.modifier().amount();
			}
		}
		return sum;
	}

	private static double toughness(Item item) {
		double sum = 0;
		ItemAttributeModifiers mods = new ItemStack(item).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
			if (e.attribute().value() == Attributes.ARMOR_TOUGHNESS.value()) {
				sum += e.modifier().amount();
			}
		}
		return sum;
	}

	private static Set<Identifier> recipes(GameTestHelper helper) {
		Set<Identifier> out = new HashSet<>();
		for (RecipeHolder<?> r : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
			out.add(r.id().identifier());
		}
		return out;
	}

	/** Tetherite is a step past netherite: a point more damage, longer-lived, tougher, fire-proof, and in vanilla's tags. */
	@GameTest
	public void tetheriteIsAStepPastNetherite(GameTestHelper helper) {
		Object[][] pairs = {
			{ModItems.TETHERITE_SWORD, Items.NETHERITE_SWORD}, {ModItems.TETHERITE_AXE, Items.NETHERITE_AXE},
			{ModItems.TETHERITE_PICKAXE, Items.NETHERITE_PICKAXE}, {ModItems.TETHERITE_SHOVEL, Items.NETHERITE_SHOVEL},
			{ModItems.TETHERITE_SPEAR, Items.NETHERITE_SPEAR},
		};
		for (Object[] pair : pairs) {
			double ours = attack((Item) pair[0]), theirs = attack((Item) pair[1]);
			check(helper, ours > theirs, "%s hits for %s, netherite's for %s", pair[0], ours, theirs);
		}
		check(helper, new ItemStack(ModItems.TETHERITE_SWORD).getMaxDamage() > new ItemStack(Items.NETHERITE_SWORD).getMaxDamage(),
				"the Tetherite sword wears out no later than netherite's");
		check(helper, new ItemStack(ModItems.TETHERITE_CHESTPLATE).getMaxDamage() > new ItemStack(Items.NETHERITE_CHESTPLATE).getMaxDamage(),
				"the Tetherite chestplate wears out no later than netherite's");
		check(helper, toughness(ModItems.TETHERITE_CHESTPLATE) > toughness(Items.NETHERITE_CHESTPLATE), "Tetherite armor is no tougher than netherite");
		for (Item gear : ModItems.TETHERITE_GEAR) {
			ItemStack s = new ItemStack(gear);
			check(helper, s.has(DataComponents.DAMAGE_RESISTANT), "%s burns", gear);
			check(helper, Tetherite.isGear(s), "%s is not in the Tetherite gear tag", gear);
		}
		check(helper, new ItemStack(ModItems.TETHERITE_SWORD).is(ItemTags.SWORDS), "the Tetherite sword is no sword to the game");
		check(helper, new ItemStack(ModItems.TETHERITE_SPEAR).is(ItemTags.SPEARS), "the Tetherite spear is no spear to the game");
		check(helper, new ItemStack(ModItems.TETHERITE_HELMET).is(ItemTags.TRIMMABLE_ARMOR), "Tetherite armor cannot be trimmed");
		check(helper, new ItemStack(ModItems.TETHERITE_INGOT).has(DataComponents.DAMAGE_RESISTANT), "the ingot burns");
		helper.succeed();
	}

	/** The ore wants a diamond pick or better, keeps through blasts, and smelts (or blasts) to scrap; scrap and cord make ingots. */
	@GameTest
	public void theOreSmeltsToScrapAndScrapToIngots(GameTestHelper helper) {
		BlockState ore = ModBlocks.TETHERITE_ORE.defaultBlockState();
		check(helper, ore.requiresCorrectToolForDrops(), "the ore drops for any hand");
		check(helper, ore.is(BlockTags.NEEDS_DIAMOND_TOOL), "the ore does not need a diamond pick");
		check(helper, ore.is(BlockTags.MINEABLE_WITH_PICKAXE), "the ore is not mined with a pickaxe");
		check(helper, ModBlocks.TETHERITE_ORE.getExplosionResistance() >= 1000, "a blast breaks the ore");
		Set<Identifier> have = recipes(helper);
		for (String id : List.of("tetherite_scrap", "tetherite_scrap_from_blasting", "tetherite_ingot", "spindle",
				"tetherite_upgrade_smithing_template", "weaver_armor_trim_smithing_template", "fray_armor_trim_smithing_template")) {
			check(helper, have.contains(Identifier.fromNamespaceAndPath("extremedimensions", id)), "no recipe %s", id);
		}
		for (String piece : List.of("sword", "shovel", "pickaxe", "axe", "hoe", "spear", "helmet", "chestplate", "leggings", "boots")) {
			check(helper, have.contains(Identifier.fromNamespaceAndPath("extremedimensions", "tetherite_" + piece + "_smithing")),
					"netherite's %s cannot be taken to Tetherite", piece);
		}
		helper.succeed();
	}

	/** The Spindle: fragments and silk go on, every turn winds them, the last turn gives a cord; what it holds drops with it. */
	@GameTest
	public void theSpindleSpinsFragmentsIntoCord(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "test-spindle");
		BlockPos rel = new BlockPos(1, 1, 1), at = helper.absolutePos(rel);
		helper.setBlock(rel, ModBlocks.SPINDLE.defaultBlockState());
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false);
		// turning an empty spindle does nothing
		level.getBlockState(at).useWithoutItem(level, p, hit);
		check(helper, level.getBlockState(at).getValue(SpindleBlock.SPUN) == 0, "an empty spindle turned");
		ItemStack fragments = new ItemStack(ModItems.TETHER_FRAGMENT, 5), silk = new ItemStack(ModItems.SEAM_SILK, 3);
		for (int i = 0; i < SpindleBlock.fragmentsPerCord(); i++) {
			level.getBlockState(at).useItemOn(fragments, level, p, InteractionHand.MAIN_HAND, hit);
		}
		for (int i = 0; i < SpindleBlock.silkPerCord(); i++) {
			level.getBlockState(at).useItemOn(silk, level, p, InteractionHand.MAIN_HAND, hit);
		}
		BlockState loaded = level.getBlockState(at);
		check(helper, loaded.getValue(SpindleBlock.FRAGMENTS) == SpindleBlock.fragmentsPerCord(), "%s fragments on the spindle", loaded.getValue(SpindleBlock.FRAGMENTS));
		check(helper, fragments.getCount() == 5 - SpindleBlock.fragmentsPerCord(), "loading took %s fragments", 5 - fragments.getCount());
		for (int i = 0; i < SpindleBlock.turnsPerCord() - 1; i++) {
			level.getBlockState(at).useWithoutItem(level, p, hit);
		}
		check(helper, level.getBlockState(at).getValue(SpindleBlock.SPUN) == SpindleBlock.turnsPerCord() - 1, "the turns were not counted");
		AABB above = new AABB(at).inflate(1.5);
		check(helper, level.getEntitiesOfClass(ItemEntity.class, above, e -> e.getItem().is(ModItems.TETHER_CORD)).isEmpty(), "a cord came before the last turn");
		level.getBlockState(at).useWithoutItem(level, p, hit);
		check(helper, !level.getEntitiesOfClass(ItemEntity.class, above, e -> e.getItem().is(ModItems.TETHER_CORD)).isEmpty(), "the last turn gave no cord");
		BlockState after = level.getBlockState(at);
		check(helper, after.getValue(SpindleBlock.FRAGMENTS) == 0 && after.getValue(SpindleBlock.SILK) == 0 && after.getValue(SpindleBlock.SPUN) == 0,
				"the spindle kept %s", after);
		// a loaded spindle gives back what it holds when it is broken
		BlockState full = ModBlocks.SPINDLE.defaultBlockState().setValue(SpindleBlock.FRAGMENTS, 5).setValue(SpindleBlock.SILK, 2);
		int frag = 0, sk = 0, spindles = 0;
		for (ItemStack s : Block.getDrops(full, level, at, null)) {
			frag += s.is(ModItems.TETHER_FRAGMENT) ? s.getCount() : 0;
			sk += s.is(ModItems.SEAM_SILK) ? s.getCount() : 0;
			spindles += s.is(ModItems.SPINDLE) ? s.getCount() : 0;
		}
		check(helper, frag == 5 && sk == 2 && spindles == 1, "a broken spindle gave %s fragments, %s silk, %s spindles", frag, sk, spindles);
		helper.succeed();
	}

	/** The full set eases the drain; three pieces do not. */
	@GameTest
	public void theFullSetEasesTheDrain(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-tetherite-set");
		check(helper, Tetherite.drainEase(p) == 1.0, "bare, the drain was eased");
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.TETHERITE_HELMET));
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.TETHERITE_CHESTPLATE));
		p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.TETHERITE_LEGGINGS));
		check(helper, Tetherite.drainEase(p) == 1.0, "three pieces eased the drain");
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.TETHERITE_BOOTS));
		CurseTests.near(helper, Tetherite.drainEase(p), 1.0 - Config.TETHERITE_SET_DRAIN, "the full set's ease");
		helper.succeed();
	}

	/** Tetherite that falls out of the bottom of the world comes back to whoever dropped it; anything else is left to fall. */
	@GameTest
	public void tetheriteComesBackFromTheVoid(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "test-tethered");
		Vec3 home = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
		p.snapTo(home.x, home.y, home.z, 0f, 0f);
		double deep = level.getMinY() - Config.TETHERITE_VOID_DEPTH - 4;
		ItemEntity sword = new ItemEntity(level, home.x, deep, home.z, new ItemStack(ModItems.TETHERITE_SWORD));
		sword.setThrower(p);
		ItemEntity plain = new ItemEntity(level, home.x, deep, home.z, new ItemStack(Items.NETHERITE_SWORD));
		plain.setThrower(p);
		level.addFreshEntity(sword);
		level.addFreshEntity(plain);
		Tethered.tick(sword);
		Tethered.tick(plain);
		check(helper, sword.isRemoved(), "the Tetherite sword stayed in the void");
		check(helper, !plain.isRemoved(), "a netherite sword was drawn back too");
		List<ItemEntity> back = level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(3), e -> e.getItem().is(ModItems.TETHERITE_SWORD));
		check(helper, !back.isEmpty(), "the Tetherite sword did not come back to its owner");
		back.forEach(ItemEntity::discard);
		plain.discard();
		helper.succeed();
	}

	/** The ore lies at the hearts of islands and in the stone of the Fray's knots (here with its chances raised). */
	@GameTest
	public void theOreLiesDeepInTheSeam(GameTestHelper helper) {
		double island = Config.TETHERITE_ORE_ISLAND_CHANCE, fray = Config.TETHERITE_ORE_FRAY_CHANCE;
		try {
			Config.TETHERITE_ORE_ISLAND_CHANCE = 0.5;
			Config.TETHERITE_ORE_FRAY_CHANCE = 0.5;
			long seed = 12345L;
			SeamArchitect.Shape shape = new SeamArchitect.Shape(1050, 90, 1050, 14, 0.3, 1.1, 2.0);
			SeamArchitect.Plan ground = new SeamArchitect.Plan();
			Terrain.of(new Random(seed), shape, 0).emit(ground);
			int inIsland = ground.count(ModBlocks.TETHERITE_ORE);
			check(helper, inIsland > 0, "no ore in the island's heart");
			// and only deep: none in the top half of its rock
			int shallow = 0;
			for (long key : ground.positions()) {
				BlockPos pos = BlockPos.of(key);
				if (ground.at(pos.getX(), pos.getY(), pos.getZ()).is(ModBlocks.TETHERITE_ORE) && pos.getY() > shape.top() - 4) {
					shallow++;
				}
			}
			check(helper, shallow == 0, "%s ore lay near the island's top", shallow);
			SeamArchitect.Shape east = new SeamArchitect.Shape(1150, 86, 1046, 12, 1.0, 0.2, 2.5);
			SeamArchitect.Plan knot = SeamDepth.plan(10, 10, shape, east, null, seed);
			check(helper, knot != null && knot.count(ModBlocks.TETHERITE_ORE) > 0, "no ore in the Fray's knot");
			Config.TETHERITE_ORE_ISLAND_CHANCE = 0;
			Config.TETHERITE_ORE_FRAY_CHANCE = 0;
			SeamArchitect.Plan none = new SeamArchitect.Plan();
			Terrain.of(new Random(seed), shape, 0).emit(none);
			check(helper, none.count(ModBlocks.TETHERITE_ORE) == 0, "with its chance at 0 the ore was still laid");
		} finally {
			Config.TETHERITE_ORE_ISLAND_CHANCE = island;
			Config.TETHERITE_ORE_FRAY_CHANCE = fray;
		}
		helper.succeed();
	}

	/**
	 * The Seam's trims: two materials and two patterns in the game's registries, the ingot and the silk providing the
	 * materials, recipes for both patterns, and palettes as long as vanilla's (each material colours a pattern by index).
	 */
	@GameTest
	public void theSeamsTrimsAreThere(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		var materials = server.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL);
		var patterns = server.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN);
		check(helper, materials.get(TetheriteTrims.TETHERITE).isPresent() && materials.get(TetheriteTrims.SEAM_SILK).isPresent(), "a trim material is missing");
		check(helper, patterns.get(TetheriteTrims.WEAVER).isPresent() && patterns.get(TetheriteTrims.FRAY).isPresent(), "a trim pattern is missing");
		check(helper, new ItemStack(ModItems.TETHERITE_INGOT).has(DataComponents.PROVIDES_TRIM_MATERIAL), "the ingot is no trim material");
		check(helper, new ItemStack(ModItems.SEAM_SILK).has(DataComponents.PROVIDES_TRIM_MATERIAL), "Seam Silk is no trim material");
		check(helper, new ItemStack(ModItems.TETHERITE_INGOT).is(ItemTags.TRIM_MATERIALS), "the ingot cannot go in a trim");
		Set<Identifier> have = recipes(helper);
		for (String p : List.of("weaver", "fray")) {
			check(helper, have.contains(Identifier.fromNamespaceAndPath("extremedimensions", p + "_armor_trim_smithing_template_smithing_trim")), "no trim recipe for %s", p);
		}
		// the palettes: as wide as vanilla's (logged, so they can be matched to vanilla's key)
		int vanilla = palette(helper, "assets/minecraft/textures/palettes/trim/amethyst.png");
		palette(helper, "assets/minecraft/textures/palettes/trim_base.png");
		for (String own : List.of("trim/tetherite", "trim/tetherite_darker", "trim/seam_silk")) {
			int width = palette(helper, "assets/extremedimensions/textures/palettes/" + own + ".png");
			check(helper, vanilla < 0 || width < 0 || width == vanilla, "the palette %s is %s wide, vanilla's %s", own, width, vanilla);
		}
		// the patterns are drawn in the key's own greys, read raw (a colour-managed read brightens a grey PNG): any other
		// colour is left as it is, grey, on every material
		Set<Integer> key = rawGreys("assets/minecraft/textures/palettes/trim_base.png");
		for (String layer : List.of("humanoid", "humanoid_leggings")) {
			for (String p : List.of("weaver", "fray")) {
				String path = "assets/extremedimensions/textures/trims/entity/" + layer + "/" + p + ".png";
				Set<Integer> used = rawGreys(path);
				used.removeAll(key);
				check(helper, key.isEmpty() || used.isEmpty(), "%s uses %s, not the trim key's greys %s", path, used, key);
			}
		}
		helper.succeed();
	}

	/** The raw grey values of a picture's visible pixels (-1 for a pixel that is not grey); empty if it cannot be read here. */
	private static Set<Integer> rawGreys(String path) {
		Set<Integer> out = new HashSet<>();
		try (InputStream in = TetheriteTests.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				return out;
			}
			java.awt.image.BufferedImage im = javax.imageio.ImageIO.read(in);
			java.awt.image.Raster raster = im.getRaster();
			int bands = raster.getNumBands();
			boolean alpha = im.getColorModel().hasAlpha();
			for (int y = 0; y < im.getHeight(); y++) {
				for (int x = 0; x < im.getWidth(); x++) {
					int[] s = raster.getPixel(x, y, (int[]) null);
					if (alpha && s[bands - 1] == 0) {
						continue;
					}
					boolean grey = bands < 3 || s[0] == s[1] && s[1] == s[2];
					out.add(grey ? s[0] : -1);
				}
			}
		} catch (Exception e) {
			System.out.println("PALETTE|" + path + "|" + e);
		}
		return out;
	}

	/** A palette's width, its colours printed for the log (-1 if it cannot be read here). */
	private static int palette(GameTestHelper helper, String path) {
		try (InputStream in = TetheriteTests.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				System.out.println("PALETTE|" + path + "|missing");
				return -1;
			}
			java.awt.image.BufferedImage im = javax.imageio.ImageIO.read(in);
			StringBuilder sb = new StringBuilder();
			for (int x = 0; x < im.getWidth(); x++) {
				int c = im.getRGB(x, 0);
				sb.append(x == 0 ? "" : ";").append((c >> 16) & 255).append(',').append((c >> 8) & 255).append(',').append(c & 255);
			}
			System.out.println("PALETTE|" + path + "|" + im.getWidth() + "x" + im.getHeight() + "|" + sb);
			return im.getWidth();
		} catch (Exception e) {
			System.out.println("PALETTE|" + path + "|" + e);
			return -1;
		}
	}

	/** The mixins behind Tetherite are applied (a missing target fails silently otherwise). */
	@GameTest
	public void everyTetheriteMixinIsApplied(GameTestHelper helper) {
		boolean found = false;
		for (java.lang.reflect.Method m : ItemEntity.class.getDeclaredMethods()) {
			found |= m.getName().contains("extremedimensions$tethered");
		}
		check(helper, found, "the tethered mixin did not apply to ItemEntity");
		helper.succeed();
	}
}
