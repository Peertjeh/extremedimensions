package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.extremedimensions.config.Config;
import com.extremedimensions.lore.Discs;
import com.extremedimensions.lurker.CaveLurker;
import com.extremedimensions.registry.ModEntities;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.WorldFx;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The later records: three that play in a jukebox like Minecraft's own; two pressed from nine fragments each; and where
 * the record and the fragments come from (a Lurker of the deep, an Elder falling, an Abyssal curse breaking, a tangle).
 */
public class DiscTests {
	private static int held(ServerPlayer p, Item item) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).is(item)) {
				n += p.getInventory().getItem(i).getCount();
			}
		}
		return n;
	}

	/** Each new record carries its song, which plays on the Jukebox/Note Blocks slider. */
	@GameTest
	public void theLaterRecordsPlayInAJukebox(GameTestHelper helper) {
		Object[][] discs = {
			{ModItems.MUSIC_DISC_WHAT_LISTENS, ModItems.WHAT_LISTENS, "what_listens"},
			{ModItems.MUSIC_DISC_ELEGY_OF_THE_ELDERS, ModItems.ELEGY_OF_THE_ELDERS, "elegy_of_the_elders"},
			{ModItems.MUSIC_DISC_THE_ABYSS_ANSWERS, ModItems.THE_ABYSS_ANSWERS, "the_abyss_answers"},
		};
		for (Object[] row : discs) {
			@SuppressWarnings("unchecked")
			ResourceKey<JukeboxSong> song = (ResourceKey<JukeboxSong>) row[1];
			java.util.Optional<net.minecraft.core.Holder<JukeboxSong>> on = JukeboxSong.fromStack(new ItemStack((Item) row[0]));
			check(helper, on.isPresent() && on.get().is(song), "the record %s carries the song %s", row[2], on);
			check(helper, WorldFx.category("extremedimensions:music_disc." + row[2]) == SoundSource.RECORDS, "%s is not on Jukebox/Note Blocks", row[2]);
		}
		helper.succeed();
	}

	/** Nine fragments press each of the two hard-won records: a shapeless recipe each, in the recipe book once one is held. */
	@GameTest
	public void nineFragmentsPressEachRecord(GameTestHelper helper) {
		Set<Identifier> have = new HashSet<>();
		for (RecipeHolder<?> r : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
			have.add(r.id().identifier());
		}
		for (String id : List.of("music_disc_elegy_of_the_elders", "music_disc_the_abyss_answers")) {
			check(helper, have.contains(Identifier.fromNamespaceAndPath("extremedimensions", id)), "no recipe presses %s", id);
		}
		helper.succeed();
	}

	/** An Elder falls: everyone near takes its elegy's fragments, as many as the world gives. */
	@GameTest
	public void aFallenElderLeavesItsElegyToThoseWhoFoughtIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "disc-elder");
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 0, 4.5));
		p.snapTo(at.x, at.y, at.z, 0f, 0f);
		int before = held(p, ModItems.DISC_FRAGMENT_ELEGY);
		Discs.elderFell(level, at.add(10, 0, 0));
		check(helper, held(p, ModItems.DISC_FRAGMENT_ELEGY) - before == Config.DISC_FRAGMENT_ELEGY_COUNT,
				"a fighter took %s fragments of the elegy, not %s", held(p, ModItems.DISC_FRAGMENT_ELEGY) - before, Config.DISC_FRAGMENT_ELEGY_COUNT);
		int count = Config.DISC_FRAGMENT_ELEGY_COUNT;
		try {
			Config.DISC_FRAGMENT_ELEGY_COUNT = 0;
			int now = held(p, ModItems.DISC_FRAGMENT_ELEGY);
			Discs.elderFell(level, at);
			check(helper, held(p, ModItems.DISC_FRAGMENT_ELEGY) == now, "an Elder left fragments with the count at 0");
		} finally {
			Config.DISC_FRAGMENT_ELEGY_COUNT = count;
			p.getInventory().clearContent();
		}
		helper.succeed();
	}

	/** A Cave Lurker slain by a player leaves What Listens at a chance of 1, and never at 0. */
	@GameTest
	public void aSlainLurkerOfTheDeepMayLeaveWhatListens(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "disc-listens");
		CaveLurker cl = ModEntities.CAVE_LURKER.spawn(level, helper.absolutePos(new BlockPos(3, 0, 3)), EntitySpawnReason.COMMAND);
		double chance = Config.MUSIC_DISC_LISTENS_CHANCE;
		AABB box = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).inflate(8);
		try {
			check(helper, cl != null, "no Cave Lurker came");
			Config.MUSIC_DISC_LISTENS_CHANCE = 0;
			Discs.slain(cl, p);
			check(helper, level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(ModItems.MUSIC_DISC_WHAT_LISTENS)).isEmpty(),
					"a Cave Lurker left the record at a chance of 0");
			Config.MUSIC_DISC_LISTENS_CHANCE = 1;
			Discs.slain(cl, p);
			List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(ModItems.MUSIC_DISC_WHAT_LISTENS));
			check(helper, found.size() == 1, "a Cave Lurker slain at a chance of 1 left %s records, not one", found.size());
			found.forEach(ItemEntity::discard);
		} finally {
			Config.MUSIC_DISC_LISTENS_CHANCE = chance;
			if (cl != null) {
				cl.discard();
			}
		}
		helper.succeed();
	}

	/** A tangle in the Fray holds a fragment of the abyss's song at a chance of 1, and never at 0. */
	@GameTest
	public void aTangleMayHoldAFragmentOfTheAbyss(GameTestHelper helper) {
		double chance = Config.DISC_FRAGMENT_ABYSS_TANGLE_CHANCE;
		try {
			Config.DISC_FRAGMENT_ABYSS_TANGLE_CHANCE = 1;
			SimpleContainer full = new SimpleContainer(27);
			Discs.tangle(full);
			check(helper, full.countItem(ModItems.DISC_FRAGMENT_ABYSS) == 1, "a tangle at a chance of 1 held %s fragments", full.countItem(ModItems.DISC_FRAGMENT_ABYSS));
			Config.DISC_FRAGMENT_ABYSS_TANGLE_CHANCE = 0;
			SimpleContainer none = new SimpleContainer(27);
			Discs.tangle(none);
			check(helper, none.countItem(ModItems.DISC_FRAGMENT_ABYSS) == 0, "a tangle held a fragment at a chance of 0");
		} finally {
			Config.DISC_FRAGMENT_ABYSS_TANGLE_CHANCE = chance;
		}
		helper.succeed();
	}
}
