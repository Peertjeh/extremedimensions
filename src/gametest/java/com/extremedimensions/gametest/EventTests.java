package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.UUID;

import com.extremedimensions.anchor.SoulWards;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.Anchor;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.Echo;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.events.Echoes;
import com.extremedimensions.events.FrayedSeam;
import com.extremedimensions.events.Unanchored;
import com.extremedimensions.events.UnstableAnchors;
import com.extremedimensions.events.WorldEvents;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.journal.MarginNote;
import com.extremedimensions.penalty.Penalty;
import com.extremedimensions.registry.ModBlocks;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.server.Dims;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * The Wounds Remember (2.36): the event framework's spacing, Echoes of Collapse, Unstable Anchors, the Unanchored
 * Build, Frayed Seam and the Veil Lantern.
 */
public class EventTests {
	/** One event at a time for a player, and the next only after the spacing. */
	@GameTest
	public void eventsKeepTheirDistance(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-spacing");
		long now = 1_000_000L + helper.getLevel().getServer().getTickCount();
		check(helper, WorldEvents.free(p, now), "a player with no event yet is not free for one");
		WorldEvents.begin(p, "test", now);
		check(helper, !WorldEvents.free(p, now + 100_000), "an event began while another was under way");
		WorldEvents.end(p.getUUID(), "test");
		check(helper, !WorldEvents.free(p, now + 20), "an event began a second after the last");
		check(helper, WorldEvents.free(p, now + Config.EVENT_SPACING * 20L), "after the spacing a player is still not free");
		helper.succeed();
	}

	/**
	 * A Collapse's echo shows once it is old enough; heard out it lets go: the listener gets a fragment, the page and
	 * the advancement, the collapsed player half the toll back (waiting for them when they are away); while it is
	 * listened to, everyone near drains twice as fast.
	 */
	@GameTest(maxTicks = 100)
	public void anEchoIsHeardOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer listener = TestPlayers.survivor(helper, "test-listener"), owner = TestPlayers.survivor(helper, "test-collapsed");
		BlockPos o = helper.absolutePos(new BlockPos(2, 1, 2));
		listener.teleportTo(o.getX() + 0.5, o.getY(), o.getZ() + 1.5);
		owner.teleportTo(o.getX() + 0.5, o.getY(), o.getZ() + 6.5);
		PlayerData od = DataStore.get(owner.getUUID()), ld = DataStore.get(listener.getUUID());
		od.curses.put(Penalty.DEBT.id, 1);
		int before = od.atonement.getOrDefault(Penalty.DEBT.id, 0);
		String ending = DataStore.ending();
		DataStore.setEnding(null);
		Echo e = new Echo(Dims.of(level), o.getX(), o.getY(), o.getZ(), owner.getStringUUID(), "collapsed", 0L, 40);
		Echo away = new Echo(Dims.of(level), o.getX() + 40, o.getY(), o.getZ(), UUID.randomUUID().toString(), "away", 0L, 30);
		Echo own = null;
		try {
			DataStore.echoes().add(e);
			Echoes.step(level.getServer());
			check(helper, e.shown, "an old echo did not show to a player beside it");
			check(helper, Echoes.drainFactor(owner) == Math.max(1, Config.ECHO_DRAIN), "while the echo was listened to, a player near drained %s times",
					Echoes.drainFactor(owner));
			for (int i = 0; i < Config.ECHO_LISTEN_SECONDS * 2 + 2 && DataStore.echoes().contains(e); i++) {
				Echoes.step(level.getServer());
			}
			check(helper, !DataStore.echoes().contains(e), "the echo did not let go after %s seconds of listening", Config.ECHO_LISTEN_SECONDS);
			check(helper, ld.journal.contains(JournalEntry.ECHOES.id), "the listener did not get the page on echoes");
			check(helper, carries(listener, ModItems.TETHER_FRAGMENT), "the listener got no Tether Fragment");
			int back = od.atonement.getOrDefault(Penalty.DEBT.id, 0) - before;
			check(helper, back == Math.round(40 * Config.ECHO_RETURN), "the collapsed player got %s atonement back, not half the toll", back);
			check(helper, Echoes.drainFactor(owner) == 1.0, "the drain stayed raised after the echo let go");

			// The collapsed player away: what comes back waits for them
			DataStore.echoes().add(away);
			listener.teleportTo(away.x + 0.5, away.y, away.z + 1.5);
			for (int i = 0; i < Config.ECHO_LISTEN_SECONDS * 2 + 2 && DataStore.echoes().contains(away); i++) {
				Echoes.step(level.getServer());
			}
			check(helper, DataStore.get(UUID.fromString(away.owner)).echoOwed == Math.round(30 * Config.ECHO_RETURN),
					"an away player's atonement from their echo did not wait for them");

			// One's own echo pays back the toll, never the listener's reward
			own = new Echo(Dims.of(level), o.getX() - 40, o.getY(), o.getZ(), listener.getStringUUID(), "own", 0L, 0);
			DataStore.echoes().add(own);
			listener.teleportTo(own.x + 0.5, own.y, own.z + 1.5);
			int fragments = count(listener, ModItems.TETHER_FRAGMENT);
			for (int i = 0; i < Config.ECHO_LISTEN_SECONDS * 2 + 2 && DataStore.echoes().contains(own); i++) {
				Echoes.step(level.getServer());
			}
			check(helper, !DataStore.echoes().contains(own) && count(listener, ModItems.TETHER_FRAGMENT) == fragments,
					"hearing one's own echo paid the listener's reward");
		} finally {
			DataStore.echoes().remove(e);
			DataStore.echoes().remove(away);
			if (own != null) {
				DataStore.echoes().remove(own);
			}
			od.curses.remove(Penalty.DEBT.id);
			od.atonement.remove(Penalty.DEBT.id);
			DataStore.setEnding(ending);
		}
		helper.succeed();
	}

	/** A Collapse that ends in death beyond the wound is remembered where it fell, with what it cost. */
	@GameTest
	public void aCollapseIsRemembered(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-remembered");
		BlockPos o = helper.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(o.getX() + 0.5, o.getY() + 3, o.getZ() + 0.5);
		int n = DataStore.echoes().size();
		Echo e = Echoes.record(p, Dims.NETHER, 24);
		try {
			check(helper, e != null && DataStore.echoes().size() == n + 1, "the Collapse left no echo");
			check(helper, e.toll == 24 && Dims.NETHER.equals(e.dim) && p.getStringUUID().equals(e.owner), "the echo remembers the wrong things");
			check(helper, e.y <= o.getY() + 3, "the echo hangs in the air at %s, not on the ground", e.y);
		} finally {
			DataStore.echoes().remove(e);
		}
		helper.succeed();
	}

	/**
	 * Once the seal is broken an unwarded anchor cracks, leaks everyone's charge, and an Amethyst Shard steadies it; left
	 * alone it breaks and loses a quarter. A soul ward keeps it whole; a mended world has no cracking.
	 */
	@GameTest
	public void anAnchorCracksAndSteadies(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "test-cracked");
		BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
		String key = Anchor.key(at.getX(), at.getY(), at.getZ());
		Anchor a = new Anchor(at.getX(), at.getY(), at.getZ(), Dims.NETHER);
		String other = UUID.randomUUID().toString();
		a.add(p.getStringUUID(), 100);
		a.add(other, 100);
		DataStore.anchors().put(key, a);
		boolean seal = DataStore.sealBroken();
		String ending = DataStore.ending();
		try {
			DataStore.setSealBroken(false);
			check(helper, !UnstableAnchors.unsettle(level, a), "an anchor cracked before the seal broke");
			DataStore.setSealBroken(true);
			DataStore.setEnding(null);
			check(helper, UnstableAnchors.unsettle(level, a) && a.unstable == Config.UNSTABLE_SECONDS, "an unwarded anchor did not crack");
			for (int i = 0; i < 3; i++) {
				UnstableAnchors.step(a);
			}
			double leaked = 100 - 3 * Config.UNSTABLE_LEAK;
			check(helper, Math.abs(a.of(p.getStringUUID()) - leaked) < 1e-6 && Math.abs(a.owners.get(other) - leaked) < 1e-6,
					"a cracked anchor did not leak everyone's charge alike (%s)", a.owners);
			ItemStack shard = new ItemStack(Items.AMETHYST_SHARD, 2);
			check(helper, UnstableAnchors.steady(p, a, shard) && a.unstable == 0 && shard.getCount() == 1, "the shard did not steady the anchor");
			check(helper, !UnstableAnchors.steady(p, a, shard) && shard.getCount() == 1, "a steady anchor took a shard");

			// Left alone it breaks
			UnstableAnchors.unsettle(level, a);
			a.unstable = 1;
			double had = a.charge;
			UnstableAnchors.step(a);
			check(helper, a.unstable == 0 && Math.abs(a.charge - (had - 2 * Config.UNSTABLE_LEAK) * (1 - Config.UNSTABLE_LOSS)) < 1e-6,
					"a broken anchor kept %s of %s", a.charge, had);

			// A soul ward keeps it whole; a mended world has no storms to crack it
			level.setBlock(at.offset(2, 0, 0), ModBlocks.VEIL_LANTERN.defaultBlockState(), 3);
			check(helper, !UnstableAnchors.unsettle(level, a), "a warded anchor cracked");
			level.setBlock(at.offset(2, 0, 0), Blocks.AIR.defaultBlockState(), 3);
			DataStore.setEnding("mended");
			check(helper, !UnstableAnchors.unsettle(level, a), "an anchor cracked in a mended world");
		} finally {
			DataStore.setSealBroken(seal);
			DataStore.setEnding(ending);
			DataStore.anchors().remove(key);
		}
		helper.succeed();
	}

	/**
	 * Three Endermen lay a ring round a pearl. Unwatched they finish it, step in and are gone, leaving pearls and a note
	 * beside XIII; looked at, they turn on the one looking and the ring stays unfinished.
	 */
	@GameTest(maxTicks = 100)
	public void theUnanchoredBuild(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "test-unanchored");
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		int cx = (origin.getX() >> 4 << 4) + 8, cz = (origin.getZ() >> 4 << 4) + 9008, y0 = 210;
		for (BlockPos q : BlockPos.betweenClosed(cx - 24, y0 - 1, cz - 24, cx + 24, y0 + 6, cz + 24)) {
			level.setBlock(q, q.getY() == y0 - 1 ? Blocks.END_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
		}
		p.teleportTo(cx + 0.5, y0, cz + 0.5);
		Unanchored.Build b = null;
		try {
			check(helper, Unanchored.start(p, 1000) == null, "the Endermen found no room to build on level ground");
			b = Unanchored.of(p.getUUID());
			check(helper, b != null && b.builders.size() == 3, "not three builders came");
			// Facing away from them, the build runs its course
			p.lookAt(EntityAnchorArgument.Anchor.EYES, new net.minecraft.world.phys.Vec3(2 * cx - b.centre.getX(), y0 + 1, 2 * cz - b.centre.getZ()));
			String end = Unanchored.step(b, b.started + Config.UNANCHORED_SECONDS * 20L + 1);
			check(helper, "finished".equals(end), "an unwatched build ended as %s", end);
			int laid = 0;
			for (BlockPos q : b.ring) {
				laid += level.getBlockState(q).is(Blocks.END_STONE_BRICKS) ? 1 : 0;
			}
			check(helper, laid == b.ring.size(), "%s of %s stones of the ring were laid", laid, b.ring.size());
			for (java.util.UUID id : b.builders) {
				check(helper, level.getEntity(id) == null || !level.getEntity(id).isAlive(), "a builder stayed after the ring was finished");
			}
			boolean pearls = !level.getEntitiesOfClass(ItemEntity.class, new AABB(b.centre).inflate(4), i -> i.getItem().is(Items.ENDER_PEARL)).isEmpty();
			check(helper, pearls, "the finished ring left no pearls");
			check(helper, DataStore.get(p.getUUID()).notes.contains(MarginNote.UNANCHORED.id), "no note beside XIII after the ring was finished");
			level.getEntitiesOfClass(ItemEntity.class, new AABB(b.centre).inflate(6), i -> true).forEach(ItemEntity::discard);
			Unanchored.clear();

			// Looked at, they turn
			check(helper, Unanchored.start(p, 2000) == null, "a second build found no room");
			b = Unanchored.of(p.getUUID());
			Mob first = (Mob) level.getEntity(b.builders.get(0));
			p.lookAt(EntityAnchorArgument.Anchor.EYES, first.getEyePosition());
			check(helper, Unanchored.staring(p, first), "looking a builder in the eye did not count as staring");
			String seen = Unanchored.step(b, b.started + 20);
			check(helper, "angered".equals(seen) && !first.isNoAi(), "a builder looked at did not turn (%s)", seen);
		} finally {
			if (b != null) {
				for (java.util.UUID id : b.builders) {
					if (level.getEntity(id) != null) {
						level.getEntity(id).discard();
					}
				}
			}
			Unanchored.clear();
			WorldEvents.end(p.getUUID(), "unanchored");
		}
		helper.succeed();
	}

	/** A frayed island: Frayed Rays rise to it, and when it is over a tangle surfaces on it and the rays sink back. */
	@GameTest(maxTicks = 100)
	public void anIslandFrays(GameTestHelper helper) {
		// (the test server has no Seam: a fraying works on whatever world it is given, so it is tried here)
		ServerLevel seam = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		int cx = (origin.getX() >> 4 << 4) + 40008, cz = (origin.getZ() >> 4 << 4) + 40008, y0 = 150;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				seam.getChunk((cx >> 4) + dx, (cz >> 4) + dz);
				seam.setChunkForced((cx >> 4) + dx, (cz >> 4) + dz, true);
			}
		}
		for (BlockPos q : BlockPos.betweenClosed(cx - 10, y0 - 1, cz - 10, cx + 10, y0 + 30, cz + 10)) {
			seam.setBlock(q, q.getY() == y0 - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
		}
		FrayedSeam.Fraying f = FrayedSeam.start(seam, UUID.randomUUID(), cx + 0.5, y0, cz + 0.5, 0);
		try {
			check(helper, f.rays() > 0, "no Frayed Ray rose to the frayed island");
			// (a ray the Seam does not drive fades after two seconds: these must stay over the island)
			// (by their ids: in a chunk loaded this very tick they are not to be looked up yet)
			check(helper, f.rayIds().stream().allMatch(com.extremedimensions.seam.SeamFauna::isDriven),
					"the frayed island's rays are not held, and fade after two seconds");
			check(helper, f.holds(cx + 20, cz) && !f.holds(cx + Config.FRAYED_SEAM_RADIUS + 9, cz), "the island's reach is wrong");
			FrayedSeam.finishNow(seam, f);
			boolean tangle = false;
			for (BlockPos q : BlockPos.betweenClosed(cx - 7, y0, cz - 7, cx + 7, y0, cz + 7)) {
				tangle |= seam.getBlockState(q).is(Blocks.BARREL);
			}
			check(helper, tangle, "no tangle surfaced when the fraying was over");
		} finally {
			FrayedSeam.finishNow(seam, f);
			for (BlockPos q : BlockPos.betweenClosed(cx - 10, y0 - 1, cz - 10, cx + 10, y0 + 1, cz + 10)) {
				seam.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
			}
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					seam.setChunkForced((cx >> 4) + dx, (cz >> 4) + dz, false);
				}
			}
		}
		helper.succeed();
	}

	/** A Veil Lantern is a soul ward, and no storm breaks through near it. */
	@GameTest
	public void theVeilLanternWards(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
		check(helper, SoulWards.isWard(ModBlocks.VEIL_LANTERN), "the Veil Lantern is no soul ward");
		check(helper, !SoulWards.veiled(level, at.offset(3, 0, 0)), "a storm is held off with no lantern near");
		level.setBlock(at, ModBlocks.VEIL_LANTERN.defaultBlockState(), 3);
		check(helper, SoulWards.veiled(level, at.offset(3, 0, 0)), "a storm breaks through beside a Veil Lantern");
		check(helper, !SoulWards.veiled(level, at.offset(Config.VEIL_LANTERN_RANGE + 6, 0, 0)), "a Veil Lantern holds off storms too far away");
		check(helper, SoulWards.warded(level, at.getX() + 2, at.getY(), at.getZ()), "a Veil Lantern does not ward an anchor beside it");
		level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
		helper.succeed();
	}

	private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).is(item)) {
				n += p.getInventory().getItem(i).getCount();
			}
		}
		return n;
	}

	private static boolean carries(ServerPlayer p, net.minecraft.world.item.Item item) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).is(item)) {
				return true;
			}
		}
		return false;
	}
}
