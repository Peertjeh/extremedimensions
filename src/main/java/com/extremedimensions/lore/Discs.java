package com.extremedimensions.lore;

import java.util.concurrent.ThreadLocalRandom;

import com.extremedimensions.config.Config;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.WorldFx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * Where the later music discs come from. "What Listens" is found whole: on a Cave or Sculk Lurker slain, and in the
 * Ancient Cities' chests (that one through its loot table). The other two are pressed from nine fragments each, and
 * those are hard won: "Elegy of the Elders" from the Elders, as each one falls, to everyone who fought it; "The Abyss
 * Answers" from the Abyssal curses as they break, and very rarely from the Fray (a tangle, a Frayed Ray).
 */
public final class Discs {
	private Discs() {}

	/** How near a fallen Elder a player must stand to take its fragments, as for the Abyssal curses' Elder deeds. */
	static final double ELDER_RANGE = 96;

	/** A creature slain: a Cave or Sculk Lurker may leave "What Listens", a Frayed Ray a fragment of the abyss's song. */
	public static void slain(LivingEntity dead, Entity killer) {
		if (!(dead.level() instanceof ServerLevel level) || !(killer instanceof ServerPlayer)) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		if (dead instanceof com.extremedimensions.lurker.CaveLurker || dead instanceof com.extremedimensions.lurker.SculkLurker) {
			if (rnd.nextDouble() < Config.MUSIC_DISC_LISTENS_CHANCE) {
				Block.popResource(level, dead.blockPosition(), new ItemStack(ModItems.MUSIC_DISC_WHAT_LISTENS));
			}
		} else if (dead instanceof com.extremedimensions.seam.VeilRay ray && ray.isFrayed()
				&& !com.extremedimensions.tapestry.Weaving.drives(dead.getUUID())) {
			// (what the Weaving brings to the loom carries nothing, as with the rest of what the fauna leave)
			if (rnd.nextDouble() < Config.DISC_FRAGMENT_ABYSS_RAY_CHANCE) {
				Block.popResource(level, dead.blockPosition(), new ItemStack(ModItems.DISC_FRAGMENT_ABYSS));
			}
		}
	}

	/** An Elder fell: everyone near who fought it takes fragments of its elegy. */
	public static void elderFell(LivingEntity elder) {
		if (elder.level() instanceof ServerLevel level) {
			elderFell(level, elder.position());
		}
	}

	/** An Elder fell here: everyone near (not watching as a spectator) takes fragments of its elegy. */
	public static void elderFell(ServerLevel level, Vec3 at) {
		int n = Math.max(0, Config.DISC_FRAGMENT_ELEGY_COUNT);
		if (n <= 0) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.position().distanceToSqr(at) <= ELDER_RANGE * ELDER_RANGE) {
				WorldFx.give(p, new ItemStack(ModItems.DISC_FRAGMENT_ELEGY, n));
			}
		}
	}

	/** An Abyssal curse broke: it leaves a fragment of the abyss's song behind. */
	public static void abyssalBroken(ServerPlayer p) {
		int n = Math.max(0, Config.DISC_FRAGMENT_ABYSS_BROKEN);
		if (n > 0) {
			WorldFx.give(p, new ItemStack(ModItems.DISC_FRAGMENT_ABYSS, n));
		}
	}

	/** A tangle in the Fray is filled: now and then a fragment of the abyss's song lies in it. */
	public static void tangle(Container c) {
		if (ThreadLocalRandom.current().nextDouble() >= Config.DISC_FRAGMENT_ABYSS_TANGLE_CHANCE) {
			return;
		}
		Loot.put(c, new ItemStack(ModItems.DISC_FRAGMENT_ABYSS));
	}
}
