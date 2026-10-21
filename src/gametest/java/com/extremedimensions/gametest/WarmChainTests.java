package com.extremedimensions.gametest;

import static com.extremedimensions.gametest.CurseTests.check;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.extremedimensions.anchor.AnchorService;
import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.data.WarmGate;
import com.extremedimensions.expedition.ExpeditionService;
import com.extremedimensions.journal.Journal;
import com.extremedimensions.journal.JournalEntry;
import com.extremedimensions.journal.MarginNote;
import com.extremedimensions.lore.HeldChamber;
import com.extremedimensions.lore.WarmChains;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.relic.Relic;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.server.Dims;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The Warm Chain (2.34): ruined portals with a warm chain, the Held's chamber at the other end, the Held's Chain's
 * grace and price, the Loose Leaf and the notes in another hand.
 */
public class WarmChainTests {
	/**
	 * A ruined portal is looked at once: a warm one gets its chain, lying on the ground in front of the frame and leading
	 * to its foot, never in the opening; looked at again, nothing more happens. Coming to the chain writes the page and
	 * the note beside it, once.
	 */
	@GameTest(maxTicks = 100)
	public void aRuinedPortalIsLookedAtOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		int cx = (origin.getX() >> 4 << 4) + 15008, cz = (origin.getZ() >> 4 << 4) + 8;
		force(level, cx, cz, 1, true);
		int y0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz) + 1;
		// Level ground and open air, then a frame four wide and five tall, its opening facing north and south
		for (BlockPos q : BlockPos.betweenClosed(cx - 9, y0 - 1, cz - 9, cx + 9, y0 + 7, cz + 9)) {
			level.setBlock(q, q.getY() == y0 - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
		}
		for (int x = 0; x < 4; x++) {
			for (int y = 0; y < 5; y++) {
				if (x == 0 || x == 3 || y == 0 || y == 4) {
					level.setBlock(new BlockPos(cx - 1 + x, y0 + y, cz), Blocks.OBSIDIAN.defaultBlockState(), 2);
				}
			}
		}
		String key = "test-" + UUID.randomUUID();
		BoundingBox box = new BoundingBox(cx - 6, y0 - 1, cz - 6, cx + 6, y0 + 6, cz + 6);
		double chance = Config.WARM_CHAIN_CHANCE;
		Config.WARM_CHAIN_CHANCE = 1.0;
		WarmGate g = null;
		try {
			g = WarmChains.consider(level, key, box);
			check(helper, g != null, "a ruined portal with every chance of a warm chain had none");
			check(helper, WarmChains.consider(level, key, box) == null, "the same ruined portal was looked at twice");
			Direction f = Direction.byName(g.facing);
			check(helper, f.getAxis() == Direction.Axis.Z, "the chain lies along the frame (%s), not out from its opening", g.facing);
			check(helper, g.links.size() >= 5, "only %s links of chain were laid", g.links.size());
			boolean touches = false;
			for (int[] l : g.links) {
				BlockPos q = new BlockPos(l[0], l[1], l[2]);
				check(helper, HeldChamber.isChain(level.getBlockState(q)), "no chain where a link was laid at %s", q);
				check(helper, l[2] != cz, "a link lies in the frame's opening at %s", q);
				check(helper, level.getBlockState(q.below()).is(Blocks.STONE), "a link at %s does not lie on the ground", q);
				touches |= Math.abs(l[2] - cz) == 1 && Math.abs(l[0] - g.x) <= 1;
			}
			check(helper, touches, "the chain does not reach the foot of the frame");

			ServerPlayer p = TestPlayers.survivor(helper, "test-warm-finder");
			PlayerData d = DataStore.get(p.getUUID());
			WarmChains.found(p, d, g);
			check(helper, d.journal.contains(JournalEntry.RUINED_GATE.id), "the warm chain did not write the page on the ruined gates");
			check(helper, d.notes.contains(MarginNote.RUINED_GATE.id), "the warm chain left no note beside the page");
			int notes = d.notes.size(), pages = d.journal.size();
			WarmChains.found(p, d, g);
			check(helper, d.notes.size() == notes && d.journal.size() == pages, "coming to the chain again wrote more");
		} finally {
			Config.WARM_CHAIN_CHANCE = chance;
			DataStore.gatesLooked().remove(key);
			if (g != null) {
				DataStore.warmGates().remove(g);
			}
			force(level, cx, cz, 1, false);
		}
		helper.succeed();
	}

	/**
	 * Crossing through a warm gate raises the Held's chamber near the arrival in the Nether: walls round a hollow, the
	 * Held at the back, one barrel with the Held's letter and chain, a door towards the arrival. A second crossing
	 * through the same gate raises nothing more.
	 */
	@GameTest(maxTicks = 200)
	public void aWarmGateLeadsToTheHeldOnce(GameTestHelper helper) {
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		int ax = (origin.getX() >> 4 << 4) + 12008, az = (origin.getZ() >> 4 << 4) + 8;
		force(nether, ax, az, 2, true);
		BlockPos arrival = new BlockPos(ax, 70, az);
		WarmGate gate = new WarmGate("test-" + UUID.randomUUID(), 1_234_567 + origin.getX(), 64, 7_654_321 + origin.getZ(), "south");
		DataStore.warmGates().add(gate);
		ServerPlayer p = TestPlayers.survivor(helper, "test-warm-crosser"), q = TestPlayers.survivor(helper, "test-warm-follower");
		try {
			PlayerData d = DataStore.get(p.getUUID()), e = DataStore.get(q.getUUID());
			for (PlayerData x : new PlayerData[] {d, e}) {
				x.hasLast = true;
				x.lastX = gate.x + 0.5;
				x.lastY = gate.y + 1;
				x.lastZ = gate.z + 1.5;
			}
			check(helper, WarmChains.crossed(p, d, nether, arrival) == gate, "the crossing did not come through the warm gate");
			check(helper, gate.chamber, "no chamber was raised at the other end");
			BlockPos c = new BlockPos(gate.cx, gate.cy, gate.cz);
			double far = Math.sqrt(Math.pow(c.getX() - ax, 2) + Math.pow(c.getZ() - az, 2));
			check(helper, far >= HeldChamber.NEAR && far <= HeldChamber.FAR, "the chamber is %s blocks from the arrival", far);
			List<BlockPos> barrels = find(nether, c, Blocks.BARREL);
			check(helper, barrels.size() == 1, "the chamber holds %s barrels, not one", barrels.size());
			check(helper, !find(nether, c, Blocks.SKELETON_SKULL).isEmpty(), "the Held is not in the chamber");
			check(helper, !find(nether, c, Blocks.LODESTONE).isEmpty(), "the lodestone is missing");
			check(helper, nether.getBlockState(c.above()).isAir() || HeldChamber.isChain(nether.getBlockState(c)), "the chamber is not hollow");
			Container box = (Container) nether.getBlockEntity(barrels.get(0));
			check(helper, holds(box, ModItems.LOOSE_LEAF), "the first chamber's barrel holds no Loose Leaf");
			check(helper, holds(box, RelicService.item(Relic.HELDS_CHAIN)), "the first chamber's barrel holds no Held's Chain");
			// The door: open on the side the arrival lies
			int dx = ax - c.getX(), dz = az - c.getZ();
			Direction f = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
			BlockPos door = c.relative(f, HeldChamber.HALF);
			check(helper, !nether.getBlockState(door).isSolid() && nether.getBlockState(door.above()).isAir(), "the chamber has no door towards the arrival");

			check(helper, WarmChains.crossed(q, e, nether, arrival) == gate, "the second crossing did not come through the warm gate");
			check(helper, gate.cx == c.getX() && gate.cz == c.getZ(), "a second crossing moved the chamber");
			check(helper, find(nether, c, Blocks.BARREL).size() == 1, "a second crossing raised another chamber");
		} finally {
			DataStore.warmGates().remove(gate);
			force(nether, ax, az, 2, false);
		}
		helper.succeed();
	}

	/**
	 * The Held's Chain holds the player when the charge runs out: home within the grace, no Collapse and no curse; the
	 * grace run out, the Collapse. Once an expedition, and not without the relic.
	 */
	@GameTest
	public void theHeldsChainHoldsAMomentLonger(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-held");
		PlayerData d = DataStore.get(p.getUUID());
		String relic = d.relic;
		int pending = d.pendingPenalties, collapses = d.collapses;
		List<String> owed = new ArrayList<>(d.owed);
		try {
			d.relic = Relic.HELDS_CHAIN.id;
			begin(d);
			ExpeditionService.tick(p, d, Dims.NETHER);
			check(helper, d.heldTicks > 0 && !d.collapsing, "at no charge the chain did not hold (%s ticks, collapsing %s)", d.heldTicks, d.collapsing);
			ExpeditionService.onDimensionChange(p, d, Dims.NETHER, Dims.OVERWORLD);
			check(helper, !d.inRun && !d.collapsing && d.heldTicks == 0 && d.pendingPenalties == pending,
					"home within the grace, the player still collapsed or owes a curse (%s owed, was %s)", d.pendingPenalties, pending);

			begin(d);
			ExpeditionService.tick(p, d, Dims.NETHER);
			check(helper, d.heldTicks > 0, "a new expedition had no grace");
			d.heldTicks = 1;
			ExpeditionService.tick(p, d, Dims.NETHER);
			check(helper, d.collapsing, "the grace ran out without a Collapse");
			reset(d, pending, owed);

			// Leaving the game while it holds is no way out: the Collapse comes there and then, owed for the return
			begin(d);
			ExpeditionService.tick(p, d, Dims.NETHER);
			check(helper, d.heldTicks > 0, "the chain did not hold before the player left");
			ExpeditionService.leaving(p, d);
			check(helper, d.collapsing && d.collapseOwed && d.heldTicks == 0, "leaving during the grace did not collapse the player");
			reset(d, pending, owed);

			// Once an expedition: logging out and back in during the grace does not grant it again
			begin(d);
			d.heldSpent = true;
			ExpeditionService.tick(p, d, Dims.NETHER);
			check(helper, d.collapsing, "a spent grace held the player again");
			reset(d, pending, owed);

			d.relic = null;
			begin(d);
			ExpeditionService.tick(p, d, Dims.NETHER);
			check(helper, d.collapsing && d.heldTicks == 0, "without the relic the chain still held");
		} finally {
			reset(d, pending, owed);
			d.inRun = false;
			d.relic = relic;
			d.collapses = collapses;
		}
		helper.succeed();
	}

	/** Bound, the Held's Chain keeps no more than its share in an anchor; unbound, the anchor holds its full charge. */
	@GameTest
	public void theHeldsChainCapsEveryAnchor(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-held-cap");
		PlayerData d = DataStore.get(p.getUUID());
		String relic = d.relic;
		try {
			d.relic = null;
			double full = AnchorService.cap(d);
			check(helper, full == Config.MAX_CHARGE, "an anchor holds %s, not %s, without the chain", full, Config.MAX_CHARGE);
			d.relic = Relic.HELDS_CHAIN.id;
			check(helper, AnchorService.cap(d) == Config.RELIC_HELDS_CHAIN_CAP, "with the chain an anchor holds %s, not %s", AnchorService.cap(d), Config.RELIC_HELDS_CHAIN_CAP);
			for (int i = 0; i < 200; i++) {
				check(helper, RelicService.random().getItem() != RelicService.item(Relic.HELDS_CHAIN), "a relic found at random was the Held's Chain");
			}
		} finally {
			d.relic = relic;
		}
		helper.succeed();
	}

	/**
	 * The Loose Leaf writes the Held's letter and the two notes in the same hand, once; it survives a restart; a
	 * Wanderer's Page never turns out to be the Held's letter.
	 */
	@GameTest
	public void theLooseLeafIsReadOnce(GameTestHelper helper) {
		ServerPlayer p = TestPlayers.survivor(helper, "test-leaf");
		PlayerData d = DataStore.get(p.getUUID());
		Journal.readLeaf(p, d);
		check(helper, d.journal.contains(JournalEntry.HELD.id), "the leaf did not write the Held's letter");
		check(helper, d.notes.contains(MarginNote.WANDERER.id) && d.notes.contains(MarginNote.FIRST_WOUND.id), "the leaf left no notes: %s", d.notes);
		int pages = d.journal.size(), notes = d.notes.size();
		Journal.readLeaf(p, d);
		check(helper, d.journal.size() == pages && d.notes.size() == notes, "reading the leaf again wrote more");
		PlayerData back = DataStore.reread(d);
		check(helper, back.journal.contains(JournalEntry.HELD.id) && back.notes.containsAll(d.notes), "the letter or the notes did not survive a restart: %s", back.notes);

		ServerPlayer w = TestPlayers.survivor(helper, "test-pages");
		PlayerData e = DataStore.get(w.getUUID());
		for (JournalEntry j : JournalEntry.values()) {
			if (j.wanderer) {
				e.journal.add(j.id);
			}
		}
		Journal.readPage(w, e);
		check(helper, !e.journal.contains(JournalEntry.HELD.id), "a Wanderer's Page turned out to be the Held's letter");
		helper.succeed();
	}

	// ------------------------------------------------------------------ helpers

	/** On an expedition in the Nether with no charge left, not yet held. */
	/** The Held's chamber is carved only into the Nether's own ground: never through walls, chests or a fortress. */
	@GameTest
	public void theHeldDigsOnlyTheNether(GameTestHelper helper) {
		for (net.minecraft.world.level.block.Block b : List.of(net.minecraft.world.level.block.Blocks.NETHERRACK, net.minecraft.world.level.block.Blocks.BASALT,
				net.minecraft.world.level.block.Blocks.SOUL_SAND, net.minecraft.world.level.block.Blocks.LAVA, net.minecraft.world.level.block.Blocks.AIR,
				net.minecraft.world.level.block.Blocks.CRIMSON_NYLIUM, net.minecraft.world.level.block.Blocks.NETHER_QUARTZ_ORE)) {
			check(helper, HeldChamber.natural(b.defaultBlockState()), "%s is not counted as the Nether's own", b);
		}
		for (net.minecraft.world.level.block.Block b : List.of(net.minecraft.world.level.block.Blocks.OAK_PLANKS, net.minecraft.world.level.block.Blocks.CHEST,
				net.minecraft.world.level.block.Blocks.NETHER_BRICKS, net.minecraft.world.level.block.Blocks.COBBLESTONE, net.minecraft.world.level.block.Blocks.GLASS)) {
			check(helper, !HeldChamber.natural(b.defaultBlockState()), "%s would be carved through for a chamber", b);
		}
		helper.succeed();
	}

	private static void begin(PlayerData d) {
		d.inRun = true;
		d.collapsing = false;
		d.dim = Dims.NETHER;
		d.linked.clear();
		d.tetherAnchor = null;
		d.tetherTier = 0;
		d.heldSpent = false;
		d.heldTicks = 0;
		d.runMax = 100;
	}

	private static void reset(PlayerData d, int pending, List<String> owed) {
		d.collapsing = false;
		d.collapseOwed = false;
		d.heldTicks = 0;
		d.pendingPenalties = pending;
		d.owed.clear();
		d.owed.addAll(owed);
	}

	private static void force(ServerLevel level, int x, int z, int r, boolean on) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (on) {
					level.getChunk((x >> 4) + dx, (z >> 4) + dz);
				}
				level.setChunkForced((x >> 4) + dx, (z >> 4) + dz, on);
			}
		}
	}

	private static List<BlockPos> find(ServerLevel level, BlockPos c, net.minecraft.world.level.block.Block b) {
		List<BlockPos> out = new ArrayList<>();
		int h = HeldChamber.HALF;
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-h, -1, -h), c.offset(h, HeldChamber.TALL, h))) {
			if (level.getBlockState(q).is(b)) {
				out.add(q.immutable());
			}
		}
		return out;
	}

	private static boolean holds(Container box, Item item) {
		for (int i = 0; i < box.getContainerSize(); i++) {
			if (box.getItem(i).is(item)) {
				return true;
			}
		}
		return false;
	}
}
