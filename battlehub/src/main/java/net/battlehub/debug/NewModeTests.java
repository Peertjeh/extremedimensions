package net.battlehub.debug;

import static net.battlehub.debug.SelfTest.VARS;
import static net.battlehub.debug.SelfTest.p;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.battlehub.arena.CustomMaps;
import net.battlehub.arena.MapRules;
import net.battlehub.arena.Theme;
import net.battlehub.config.ConfigManager;
import net.battlehub.core.GameMode;
import net.battlehub.core.MurderRoles;
import net.battlehub.core.Outcome;
import net.battlehub.games.HungerGamesMatch;
import net.battlehub.games.MurderMysteryMatch;
import net.battlehub.games.SkyWarsMatch;
import net.battlehub.games.duel.BoxingMatch;
import net.battlehub.games.duel.BridgeMatch;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.session.Sessions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** The Bridge, Boxing, Build UHC, Spleef, SkyWars, Murder Mystery, the map vote and a hand-built map. */
final class NewModeTests {
	private NewModeTests() {}

	private static final String A = "BHTestA";
	private static final String B = "BHTestB";

	private static Match match() {
		return (Match) VARS.get("match");
	}

	static void all(MinecraftServer server, Script s) {
		s.run("new modes: quick settings", () -> {
			var c = ConfigManager.config();
			c.game(GameMode.BRIDGE).settings.put("goalsToWin", 2.0);
			c.game(GameMode.BOXING).settings.put("hitsToWin", 4.0);
			c.game(GameMode.MURDER_MYSTERY).settings.put("swordAfterSeconds", 1.0);
			c.game(GameMode.BRIDGE_DUOS).settings.put("goalsToWin", 2.0);
			c.game(GameMode.BRIDGE_FOUR).settings.put("goalsToWin", 2.0);
			c.game(GameMode.BRIDGE_CHAOS).settings.put("goalsToWin", 3.0);
			c.game(GameMode.BRIDGE_CHAOS).settings.put("powerupSeconds", 600.0);
			c.game(GameMode.BRIDGE_CHAOS).settings.put("eventSeconds", 600.0);
			c.game(GameMode.BRIDGE_CHAOS).settings.put("firstEventSeconds", 600.0);
			c.game(GameMode.BRIDGE_FOUR_CHAOS).settings.put("goalsToWin", 2.0);
			c.game(GameMode.BRIDGE_FOUR_CHAOS).settings.put("powerupSeconds", 600.0);
			c.game(GameMode.BRIDGE_FOUR_CHAOS).settings.put("eventSeconds", 600.0);
			c.game(GameMode.BRIDGE_FOUR_CHAOS).settings.put("firstEventSeconds", 600.0);
			c.arenas.customMapChance = 0.0;
			c.game(GameMode.HUNGER_GAMES).settings.put("graceSeconds", 4.0);
			c.game(GameMode.HUNGER_GAMES).settings.put("borderDelaySeconds", 6.0);
			c.game(GameMode.HUNGER_GAMES).settings.put("borderShrinkSeconds", 12.0);
			c.game(GameMode.HUNGER_GAMES).settings.put("borderEnd", 40.0);
			c.game(GameMode.HUNGER_GAMES).settings.put("borderDamagePerSecond", 2.0);
		});
		PartyTests.party(server, s, GameMode.SPLEEF, NewModeTests::spleef);
		PartyTests.party(server, s, GameMode.SKYWARS, NewModeTests::skywars);
		PartyTests.party(server, s, GameMode.MURDER_MYSTERY, NewModeTests::murder);
		PartyTests.party(server, s, GameMode.SUMO, NewModeTests::votedIce,
				new String[] {"map:theme:ice", "map:theme:ice", "map:theme:ice", "map:theme:desert"});
		PartyTests.party(server, s, GameMode.BRIDGE_DUOS, NewModeTests::bridgeDuos);
		PartyTests.party(server, s, GameMode.BRIDGE_FOUR, NewModeTests::bridgeFour);
		PartyTests.party(server, s, GameMode.BRIDGE_FOUR_CHAOS, NewModeTests::bridgeFourChaos);
		PartyTests.party(server, s, GameMode.HUNGER_GAMES, NewModeTests::hungerGames,
				new String[] {"map:theme:nether", "map:theme:nether", "map:theme:nether", "map:theme:desert"});
		PartyTests.party(server, s, GameMode.HUNGER_GAMES, NewModeTests::realHungerGames);
		bridge(server, s);
		bridgeChaos(server, s);
		boxing(server, s);
		buildUhc(server, s);
		customMap(server, s);
	}

	// ------------------------------------------------------------------ party games

	private static void spleef(Script s) {
		s.run("spleef: A digs the snow next to the middle", () -> {
			BlockPos pos = match().arena().origin().offset(2, 0, 2);
			VARS.put("snow", pos);
			match().arena().level().setBlock(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
			VARS.put("dug", match().onBreakBlock(p(A), pos, Blocks.SNOW_BLOCK.defaultBlockState()));
		});
		s.check("spleef: the snow is gone without drops, A got a snowball, players dig in survival", () ->
				match().arena().level().getBlockState((BlockPos) VARS.get("snow")).isAir() && !(boolean) VARS.get("dug")
						&& p(A).getInventory().countItem(Items.SNOWBALL) == 1
						&& p(A).gameMode() == net.minecraft.world.level.GameType.SURVIVAL
						&& p(A).getInventory().getItem(0).is(Items.DIAMOND_SHOVEL));
		s.check("spleef: other blocks cannot be dug", () -> !match().onBreakBlock(p(A), match().arena().origin().offset(0, -1, 0),
				Blocks.STONE.defaultBlockState()));
		s.run("spleef: B's snowball flies into A from the west", () -> {
			p(A).setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
			net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) p(A).level();
			net.minecraft.world.entity.Entity ball = net.battlehub.hub.ClientEntities.type("minecraft:snowball").create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			((net.minecraft.world.entity.projectile.Projectile) ball).setOwner(p(B));
			ball.snapTo(p(A).getX() - 1.6, p(A).getY() + 1.2, p(A).getZ());
			ball.setDeltaMovement(1.2, 0, 0);
			level.addFreshEntity(ball);
			VARS.put("spleefX", p(A).getX());
		});
		s.waitFor("spleef: the snowball pushes A east and does no damage", 20, () -> (p(A).getX() > (double) VARS.get("spleefX") + 0.15
				|| p(A).getDeltaMovement().x > 0.1) && p(A).getHealth() == p(A).getMaxHealth());
		PartyTests.fallOff(s);
	}

	private static void skywars(Script s) {
		s.check("skywars: the chests are filled (spawn islands, middle islands and the big middle)", () -> {
			SkyWarsMatch m = (SkyWarsMatch) match();
			for (String mark : new String[] {"chest", "mid_chest", "center_chest"}) {
				if (m.arena().marks(mark).isEmpty() || !filled(m, m.arena().marks(mark).get(0))) return false;
			}
			int islands = m.arena().marks("chest").size(), mids = m.arena().marks("mid_chest").size(), centre = m.arena().marks("center_chest").size();
			net.battlehub.BattleHub.LOGGER.info("SELFTEST skywars chests: {} on the spawn islands, {} on the middle islands, {} in the middle", islands, mids, centre);
			return m.chestsFilled() == islands + mids + centre && centre >= 8 && mids >= 8;
		});
		s.check("skywars: building and breaking inside the arena", () -> {
			BlockPos in = match().arena().origin().offset(0, 2, 0);
			return match().onPlaceBlock(p(A), in, new ItemStack(Items.OAK_PLANKS))
					&& match().onBreakBlock(p(A), in, Blocks.OAK_PLANKS.defaultBlockState())
					&& !match().onPlaceBlock(p(A), match().arena().origin().offset(500, 0, 0), new ItemStack(Items.OAK_PLANKS));
		});
		s.run("skywars: B carries a diamond and is killed by A", () -> {
			p(B).getInventory().setItem(5, new ItemStack(Items.DIAMOND));
			PartyTests.hit(B, A, 1000f);
		});
		s.check("skywars: B is out and the diamond lies on the ground", () -> !match().alive().contains(p(B).getUUID())
				&& p(B).getInventory().countItem(Items.DIAMOND) == 0
				&& !match().arena().level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
						match().arena().bounds(), e -> e.getItem().is(Items.DIAMOND)).isEmpty());
		s.run("skywars: B, now a spectator, right-clicks a chest in the middle", () -> {
			BlockPos chest = match().arena().marks("center_chest").get(0);
			p(B).gameMode.useItemOn(p(B), match().arena().level(), ItemStack.EMPTY, net.minecraft.world.InteractionHand.MAIN_HAND,
					new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(chest), net.minecraft.core.Direction.UP, chest, false));
		});
		s.check("skywars: a spectator cannot open a chest", () -> p(B).isSpectator() && p(B).containerMenu == p(B).inventoryMenu);
		s.check("skywars: and in a chest opened anyway a spectator takes nothing out", () -> {
			BlockPos chest = match().arena().marks("center_chest").get(0);
			if (!(match().arena().level().getBlockEntity(chest) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity box)) return false;
			int before = count(box);
			p(B).openMenu(box);
			net.minecraft.world.inventory.AbstractContainerMenu menu = p(B).containerMenu;
			boolean opened = menu != p(B).inventoryMenu;
			for (int i = 0; i < box.getContainerSize(); i++) {
				menu.clicked(i, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, p(B));
				menu.clicked(i, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, p(B));
			}
			boolean kept = count(box) == before && menu.getCarried().isEmpty() && p(B).getInventory().isEmpty();
			p(B).closeContainer();
			net.battlehub.BattleHub.LOGGER.info("SELFTEST skywars spectator in a chest: opened {}, items {} before and {} after, carrying {}",
					opened, before, count(box), menu.getCarried());
			return before > 0 && kept;
		});
		s.run("skywars: C and D fall", () -> {
			for (String n : new String[] {"BHTestC", "BHTestD"}) {
				ServerPlayer pl = p(n);
				PartyTests.tp(pl, pl.getX(), match().arena().voidY() - 3, pl.getZ());
			}
		});
		s.waitFor("skywars: A is the last one and wins", 60, () -> match().phase() != Match.Phase.RUNNING
				&& match().outcome(p(A).getUUID()) == Outcome.WIN);
	}

	/**
	 * Hunger Games in nether dress: this week's city with nether materials in the same places, everyone on the
	 * plaza round the cornucopia, every chest filled, nobody hurt during the peace, the city cannot be broken, the
	 * border hurts, the last one alive wins.
	 */
	private static void hungerGames(Script s) {
		s.check("hunger games: this week's city in the voted nether theme, everyone round the cornucopia", () -> {
			HungerGamesMatch m = (HungerGamesMatch) match();
			long week = net.battlehub.arch.town.HungerMap.weekSeed(ConfigManager.config().lobby.seed, java.time.LocalDate.now());
			BlockPos o = m.arena().origin();
			for (String n : new String[] {A, B, "BHTestC", "BHTestD"}) {
				double d = Math.hypot(p(n).getX() - o.getX() - 0.5, p(n).getZ() - o.getZ() - 0.5);
				if (d < 13 || d > 20) return false;
			}
			net.battlehub.BattleHub.LOGGER.info("SELFTEST hunger games: week {} as {}, {} spawns", net.battlehub.hub.HungerMaps.week(), m.themeId(),
					m.arena().spawnCount());
			return m.map() != null && m.map().seed == week && m.themeId().equals("nether") && m.arena().spawnCount() == 16;
		});
		s.check("hunger games: the same blocks in the same places, in nether materials, and no doors", () -> {
			HungerGamesMatch m = (HungerGamesMatch) match();
			BlockPos o = m.arena().origin();
			int[][] found = new int[3][];
			m.map().voxels.forEach((x, y, z, id) -> {
				if (found[0] == null && id.equals("grass_block")) found[0] = new int[] {x, y, z};
				if (found[1] == null && id.equals("stone_bricks")) found[1] = new int[] {x, y, z};
				if (found[2] == null && id.startsWith("spruce_planks")) found[2] = new int[] {x, y, z};
			});
			if (found[0] == null || found[1] == null || found[2] == null) return false;
			net.minecraft.server.level.ServerLevel level = m.arena().level();
			// nylium under a block turns into netherrack on a random tick, as grass turns into dirt
			net.minecraft.world.level.block.state.BlockState g = level.getBlockState(o.offset(found[0][0], found[0][1], found[0][2]));
			boolean grass = g.is(Blocks.CRIMSON_NYLIUM) || g.is(Blocks.NETHERRACK);
			boolean bricks = level.getBlockState(o.offset(found[1][0], found[1][1], found[1][2])).is(Blocks.NETHER_BRICKS);
			boolean wood = level.getBlockState(o.offset(found[2][0], found[2][1], found[2][2])).is(Blocks.CRIMSON_PLANKS);
			int doors = 0;
			for (BlockPos c : m.arena().marks("chest")) {
				for (BlockPos q : BlockPos.betweenClosed(c.offset(-6, -1, -6), c.offset(6, 2, 6))) {
					if (level.getBlockState(q).getBlock() instanceof net.minecraft.world.level.block.DoorBlock) doors++;
				}
				break;
			}
			net.battlehub.BattleHub.LOGGER.info("SELFTEST hunger games blocks: grass -> nylium {}, stone bricks -> nether bricks {}, spruce -> crimson {}, doors near a house chest {}",
					grass, bricks, wood, doors);
			return grass && bricks && wood && doors == 0;
		});
		s.check("hunger games: chests in the houses, the landmarks and the cornucopia are filled", () -> {
			HungerGamesMatch m = (HungerGamesMatch) match();
			int houses = m.arena().marks("chest").size(), landmarks = m.arena().marks("landmark_chest").size(), corn = m.arena().marks("cornucopia").size();
			net.battlehub.BattleHub.LOGGER.info("SELFTEST hunger games chests: {} in the houses, {} in the landmarks, {} in the cornucopia, {} filled",
					houses, landmarks, corn, m.chestsFilled());
			return houses >= 60 && landmarks >= 8 && corn == 12 && m.chestsFilled() == houses + landmarks + corn
					&& filled(m, m.arena().marks("cornucopia").get(0)) && filled(m, m.arena().marks("landmark_chest").get(0));
		});
		s.run("hunger games: during the peace A hits B", () -> PartyTests.hit(B, A, 5f));
		s.check("hunger games: nobody is hurt during the peace", () -> ((HungerGamesMatch) match()).inGrace() && p(B).getHealth() == p(B).getMaxHealth());
		s.check("hunger games: the city cannot be broken, what you put down can", () -> {
			HungerGamesMatch m = (HungerGamesMatch) match();
			BlockPos floor = m.arena().origin().offset(14, 0, 3);
			BlockPos above = floor.above();
			return !m.onBreakBlock(p(A), floor, m.arena().level().getBlockState(floor))
					&& m.onPlaceBlock(p(A), above, new ItemStack(Items.OAK_PLANKS))
					&& m.onBreakBlock(p(A), above, Blocks.OAK_PLANKS.defaultBlockState());
		});
		s.waitFor("hunger games: the peace ends", 200, () -> !((HungerGamesMatch) match()).inGrace());
		s.run("hunger games: A hits B again", () -> PartyTests.hit(B, A, 5f));
		s.check("hunger games: now it hurts", () -> p(B).getHealth() < p(B).getMaxHealth());
		s.waitFor("hunger games: the border has closed in", 400, () -> ((HungerGamesMatch) match()).borderSize() < 120);
		s.run("hunger games: C stands far out in the city", () -> {
			HungerGamesMatch m = (HungerGamesMatch) match();
			int y = 70;
			while (y > -10 && !m.map().voxels.filled(0, y, -80)) y--;
			BlockPos o = m.arena().origin();
			PartyTests.tp(p("BHTestC"), o.getX() + 0.5, o.getY() + y + 1, o.getZ() - 79.5);
			VARS.put("hgHealthC", p("BHTestC").getHealth());
		});
		s.waitFor("hunger games: outside the border C gets hurt", 80, () -> p("BHTestC").getHealth() < (float) VARS.get("hgHealthC"));
		s.run("hunger games: D carries a diamond and is killed by A", () -> {
			p("BHTestD").getInventory().setItem(5, new ItemStack(Items.DIAMOND));
			PartyTests.hit("BHTestD", A, 1000f);
		});
		s.check("hunger games: D is out and the diamond lies on the ground", () -> !match().alive().contains(p("BHTestD").getUUID())
				&& !match().arena().level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, match().arena().bounds(),
						e -> e.getItem().is(Items.DIAMOND)).isEmpty());
		s.run("hunger games: B and C fall", () -> {
			for (String n : new String[] {B, "BHTestC"}) {
				ServerPlayer pl = p(n);
				PartyTests.tp(pl, pl.getX(), match().arena().voidY() - 3, pl.getZ());
			}
		});
		s.waitFor("hunger games: A is the last one and wins", 60, () -> match().phase() != Match.Phase.RUNNING
				&& match().outcome(p(A).getUUID()) == Outcome.WIN);
	}

	private static HungerGamesMatch hg() {
		return (HungerGamesMatch) match();
	}

	private static net.minecraft.world.InteractionResult click(ServerPlayer p, BlockPos pos, ItemStack held) {
		return hg().onUseBlock(p, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
				net.minecraft.core.Direction.UP, pos, false), held);
	}

	private static void shield(String... names) {
		for (String n : names) {
			net.battlehub.util.Ids.effect("minecraft:resistance").ifPresent(h -> p(n).addEffect(
					new net.minecraft.world.effect.MobEffectInstance(h, 20 * 120, 4, false, false, false)));
			p(n).setHealth(p(n).getMaxHealth());
			p(n).getFoodData().setFoodLevel(20);
		}
	}

	/**
	 * The real Hunger Games (1.43): the reaping, locked loot (a vault and its key, a sealed crate, the Capitol's chest),
	 * every Gamemaker event, the Feast, a parachute from the sponsors and one from a watcher, power-ups, a night with
	 * the fallen in the sky, and nothing left behind when it ends.
	 */
	private static void realHungerGames(Script s) {
		String t = "real hunger games";
		String C = "BHTestC", D = "BHTestD";
		s.check(t + ": the reaping: two tributes per district, training scores of 1 to 12, the games numbered", () -> {
			var tr = hg().capitol().tributes();
			java.util.Map<Integer, Integer> per = new java.util.HashMap<>();
			for (String n : new String[] {A, B, C, D}) {
				int d = tr.district(p(n).getUUID()), sc = tr.score(p(n).getUUID());
				if (d < 1 || sc < 1 || sc > 12) return false;
				per.merge(d, 1, Integer::sum);
			}
			net.battlehub.BattleHub.LOGGER.info("SELFTEST real hunger games: the {}th, districts {}, vaults {}, crates {}, Capitol's chest {}, plan {}",
					tr.number(), per, hg().capitol().vaults().vaults(), hg().capitol().vaults().crates(), hg().capitol().vaults().capitolChest(),
					hg().capitol().gamemakers().plan());
			return tr.number() >= 1 && per.size() == 2 && per.values().stream().allMatch(c -> c == 2);
		});
		s.check(t + ": vaults, sealed crates and the Capitol's chest are set up and locked", () -> {
			var v = hg().capitol().vaults();
			return v.vaults() >= 1 && v.crates() >= 3 && v.capitolChest() != null && v.locked(v.capitolChest());
		});
		s.run(t + ": everyone is shielded (resistance) for the Gamemakers' tests", () -> shield(A, B, C, D));
		s.check(t + ": a vault stays shut without a key and opens with one (the key is used up)", () -> {
			var v = hg().capitol().vaults();
			BlockPos vault = null;
			for (BlockPos pos : hg().arena().marks("landmark_chest")) {
				if (v.isVault(pos)) vault = pos;
			}
			if (vault == null) return false;
			boolean shut = click(p(A), vault, ItemStack.EMPTY) == net.minecraft.world.InteractionResult.FAIL;
			ItemStack key = net.battlehub.games.hunger.PowerUps.stack(p(A).level().getServer(), "key", 1);
			p(A).getInventory().setItem(0, key);
			boolean opened = click(p(A), vault, p(A).getInventory().getItem(0)) == net.minecraft.world.InteractionResult.PASS;
			return shut && opened && !v.locked(vault) && v.vaultsOpened() == 1 && p(A).getInventory().getItem(0).isEmpty();
		});
		s.check(t + ": the Capitol's chest stays shut until the Feast", () -> click(p(A), hg().capitol().vaults().capitolChest(), ItemStack.EMPTY)
				== net.minecraft.world.InteractionResult.FAIL);
		s.run(t + ": B stands beside a sealed crate, sneaking", () -> {
			var v = hg().capitol().vaults();
			BlockPos crate = v.nearestCrate(hg().plaza(), 500);
			VARS.put("hgCrate", crate);
			BlockPos at = beside(crate);
			PartyTests.tp(p(B), at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			// facing the crate
			p(B).setYRot((float) Math.toDegrees(Math.atan2(-(crate.getX() - at.getX()), crate.getZ() - at.getZ())));
			p(B).setXRot(35f);
			p(B).setShiftKeyDown(true);
			net.battlehub.BattleHub.LOGGER.info("SELFTEST real hunger games: crate {} ({}), B beside it at {}", crate,
					hg().level().getBlockState(crate).getBlock(), at);
		});
		s.check(t + ": a click on it says it is sealed", () -> click(p(B), (BlockPos) VARS.get("hgCrate"), ItemStack.EMPTY) == net.minecraft.world.InteractionResult.FAIL);
		s.waitFor(t + ": the seal is half broken", 80, () -> hg().capitol().vaults().crateShare((BlockPos) VARS.get("hgCrate")) >= 0.5);
		s.run(t + ": B is hurt", () -> p(B).hurtServer(p(B).level(), p(B).damageSources().magic(), 1f));
		s.check(t + ": the hurt starts it over", () -> hg().capitol().vaults().crateShare((BlockPos) VARS.get("hgCrate")) < 0.2);
		s.waitFor(t + ": after five seconds of sneaking the seal breaks", 160, () -> !hg().capitol().vaults().locked((BlockPos) VARS.get("hgCrate"))
				&& hg().capitol().vaults().cratesOpened() == 1);
		s.run(t + ": B stands up again", () -> p(B).setShiftKeyDown(false));
		// the Gamemakers: every event starts
		for (String ev : new String[] {"wildfire", "fog", "mutts", "tracker_jackers", "storm", "supplies", "revealed", "acid_rain", "jabberjays"}) {
			s.check(t + ": the Gamemakers send " + ev, () -> hg().capitol().gamemakers().start(ev) && hg().capitol().gamemakers().running().contains(ev));
		}
		s.check(t + ": mutts hunt, a swarm flies, A glows, supplies fall", () -> {
			long mutts = entities(hg().level(), hg().arena().bounds(), "minecraft:zoglin");
			boolean glows = p(A).hasEffect(net.battlehub.util.Ids.effect("minecraft:glowing").orElseThrow());
			net.battlehub.BattleHub.LOGGER.info("SELFTEST real hunger games events: running {}, mutts {}, swarms {}, parachutes falling {}",
					hg().capitol().gamemakers().running(), mutts, hg().capitol().gamemakers().swarms(), hg().capitol().sponsors().falling());
			return mutts >= 4 && hg().capitol().gamemakers().swarms() >= 1 && glows && hg().capitol().sponsors().falling() >= 5;
		});
		s.run(t + ": the Feast", () -> hg().capitol().gamemakers().start("feast"));
		s.check(t + ": tables of loot round the cornucopia, and the Capitol's chest is open", () -> !hg().capitol().vaults().feastChests().isEmpty()
				&& click(p(A), hg().capitol().vaults().capitolChest(), ItemStack.EMPTY) == net.minecraft.world.InteractionResult.PASS);
		s.run(t + ": the sponsors like A (enough favour for a parachute)", () -> {
			shield(A, B, C, D);
			VARS.put("hgChutes", hg().capitol().sponsors().parachutes());
			VARS.put("hgBarrels", new java.util.HashSet<>(hg().capitol().sponsors().landed().keySet()));
			hg().capitol().sponsors().favourToNext(p(A).getUUID());
		});
		s.waitFor(t + ": a silver parachute lands next to A, a barrel with a gift", 400, () -> {
			@SuppressWarnings("unchecked")
			java.util.Set<BlockPos> before = (java.util.Set<BlockPos>) VARS.get("hgBarrels");
			for (var e : hg().capitol().sponsors().landed().entrySet()) {
				if (before.contains(e.getKey()) || !p(A).getUUID().equals(e.getValue())) continue;
				double d = Math.sqrt(e.getKey().distToCenterSqr(p(A).position()));
				net.battlehub.BattleHub.LOGGER.info("SELFTEST real hunger games: A's parachute landed at {}, {} blocks away", e.getKey(),
						String.format(java.util.Locale.ROOT, "%.1f", d));
				return d < 9;
			}
			return false;
		});
		s.run(t + ": A uses a healing salve, camouflage and a tracker", () -> {
			p(A).setHealth(8f);
			var server = p(A).level().getServer();
			for (String id : new String[] {"salve", "camo", "tracker"}) {
				ItemStack st = net.battlehub.games.hunger.PowerUps.stack(server, id, 1);
				VARS.put("hgUsed_" + id, hg().onUseItem(p(A), st) == net.minecraft.world.InteractionResult.SUCCESS && st.isEmpty());
			}
		});
		s.waitFor(t + ": A heals, is invisible and has a tracker", 60, () -> Boolean.TRUE.equals(VARS.get("hgUsed_salve"))
				&& Boolean.TRUE.equals(VARS.get("hgUsed_camo")) && Boolean.TRUE.equals(VARS.get("hgUsed_tracker")) && p(A).getHealth() > 9f
				&& p(A).isInvisible() && hg().capitol().powers().tracking(p(A).getUUID()));
		s.run(t + ": A puts a landmine down; C steps on it", () -> {
			BlockPos at = BlockPos.containing(p(C).position());
			hg().level().setBlockAndUpdate(at, Blocks.STONE_PRESSURE_PLATE.defaultBlockState());
			hg().capitol().placed(p(A), at, net.battlehub.games.hunger.PowerUps.stack(p(A).level().getServer(), "mine", 1));
		});
		s.waitFor(t + ": the mine goes off (the plate is gone)", 40, () -> hg().capitol().powers().minesBlown() == 1);
		s.run(t + ": A throws nightlock at the ground before them", () -> {
			p(A).setXRot(70f);
			VARS.put("hgClouds", hg().capitol().gamemakers().clouds());
			ItemStack st = net.battlehub.games.hunger.PowerUps.stack(p(A).level().getServer(), "nightlock", 1);
			VARS.put("hgThrown", hg().onUseItem(p(A), st) == net.minecraft.world.InteractionResult.SUCCESS && st.isEmpty()
					&& hg().capitol().powers().flying() == 1);
		});
		s.waitFor(t + ": the berries burst into a cloud of poison where they land", 60, () -> Boolean.TRUE.equals(VARS.get("hgThrown"))
				&& hg().capitol().powers().thrownLanded() == 1 && hg().capitol().gamemakers().clouds() > (int) VARS.get("hgClouds"));
		s.waitFor(t + ": the peace ends", 700, () -> !hg().inGrace());
		s.run(t + ": A kills D", () -> {
			p(D).removeAllEffects();
			VARS.put("hgKeysA", countKeys(p(A)));
			PartyTests.hit(D, A, 1000f);
		});
		s.check(t + ": A took a vault key from D", () -> !hg().alive().contains(p(D).getUUID()) && countKeys(p(A)) == (int) VARS.get("hgKeysA") + 1);
		s.run(t + ": D, out of the game, sponsors B (with coins)", () -> {
			net.battlehub.data.PlayerStore.get(p(D)).coins += 500;
			VARS.put("hgCoinsD", net.battlehub.data.PlayerStore.get(p(D)).coins);
			VARS.put("hgSponsored", hg().capitol().sponsors().sponsor(p(D), p(B).getUUID(), net.battlehub.games.hunger.Sponsors.Gift.HEAL));
		});
		s.check(t + ": the gift is paid and on its way", () -> Boolean.TRUE.equals(VARS.get("hgSponsored"))
				&& net.battlehub.data.PlayerStore.get(p(D)).coins == (long) VARS.get("hgCoinsD") - net.battlehub.games.hunger.Sponsors.Gift.HEAL.cost
				&& hg().capitol().sponsors().giftsLeft(p(D)) == net.battlehub.games.hunger.Sponsors.WATCHER_GIFTS - 1);
		s.run(t + ": night falls", () -> hg().capitol().tributes().night());
		s.check(t + ": D shines in the sky", () -> hg().capitol().tributes().inSky() == 1);
		s.run(t + ": B and C fall", () -> {
			for (String n : new String[] {B, C}) {
				ServerPlayer pl = p(n);
				pl.removeAllEffects();
				PartyTests.tp(pl, pl.getX(), match().arena().voidY() - 3, pl.getZ());
			}
		});
		s.waitFor(t + ": A wins", 80, () -> match().phase() != Match.Phase.RUNNING && match().outcome(p(A).getUUID()) == Outcome.WIN);
		s.waitFor(t + ": nothing of the Capitol is left (texts, parachutes, bees, mutts)", 400, () -> {
			if (match().phase() != Match.Phase.CLOSED) return false;
			var level = match().arena().level();
			var box = match().arena().bounds();
			return level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box, e -> Boolean.TRUE.equals(net.battlehub.util.Tags.has(e, "battlehub_hg"))).isEmpty()
					&& entities(level, box, "minecraft:zoglin") == 0 && entities(level, box, "minecraft:bee") == 0;
		});
	}

	/** Entities of a type (its id) inside {@code box}. */
	private static long entities(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.AABB box, String type) {
		return level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box,
				e -> type.equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())).size();
	}

	/** A spot to stand on next to {@code pos} (a free block with a free one over it and floor under it), else on top. */
	private static BlockPos beside(BlockPos pos) {
		var level = hg().level();
		for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			BlockPos at = pos.relative(d);
			boolean free = level.getBlockState(at).getCollisionShape(level, at).isEmpty()
					&& level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty();
			if (free && !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()) return at;
		}
		return pos.above();
	}

	private static int countKeys(ServerPlayer p) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (net.battlehub.games.hunger.PowerUps.isKey(p.getInventory().getItem(i))) n += p.getInventory().getItem(i).getCount();
		}
		return n;
	}

	private static boolean filled(Match m, BlockPos pos) {
		return m.arena().level().getBlockEntity(pos) instanceof Container c && count(c) > 0;
	}

	private static int count(Container c) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) n += c.getItem(i).getCount();
		return n;
	}

	private static void murder(Script s) {
		s.check("murder: one murderer and one detective, the detective has a bow", () -> {
			MurderRoles.Roles r = ((MurderMysteryMatch) match()).roles();
			if (r == null || r.murderer().equals(r.detective())) return false;
			ServerPlayer d = match().player(r.detective());
			return d != null && d.getInventory().countItem(Items.BOW) == 1 && d.getInventory().countItem(Items.ARROW) == 1;
		});
		s.waitFor("murder: the murderer gets the sword", 60, () -> {
			ServerPlayer m = match().player(((MurderMysteryMatch) match()).roles().murderer());
			return m != null && m.getInventory().countItem(Items.IRON_SWORD) == 1;
		});
		s.check("murder: fists do nothing", () -> {
			MurderRoles.Roles r = ((MurderMysteryMatch) match()).roles();
			ServerPlayer d = match().player(r.detective());
			ServerPlayer m = match().player(r.murderer());
			d.hurtServer(d.level(), d.damageSources().playerAttack(findInnocent(r)), 5f);
			return match().alive().contains(d.getUUID()) && m != null;
		});
		s.run("murder: the murderer takes the sword and hits everyone", () -> {
			MurderRoles.Roles r = ((MurderMysteryMatch) match()).roles();
			ServerPlayer m = match().player(r.murderer());
			m.getInventory().setSelectedSlot(1);
			for (UUID id : new ArrayList<>(match().alive())) {
				if (id.equals(r.murderer())) continue;
				ServerPlayer v = match().player(id);
				v.hurtServer(v.level(), v.damageSources().playerAttack(m), 2f);
			}
		});
		s.waitFor("murder: the murderer won", 60, () -> {
			MurderRoles.Roles r = ((MurderMysteryMatch) match()).roles();
			return match().phase() != Match.Phase.RUNNING && match().outcome(r.murderer()) == Outcome.WIN
					&& match().outcome(r.detective()) == Outcome.LOSS;
		});
	}

	private static ServerPlayer findInnocent(MurderRoles.Roles r) {
		for (UUID id : match().alive()) {
			if (r.of(id) == MurderRoles.Role.INNOCENT) return match().player(id);
		}
		return null;
	}

	private static void votedIce(Script s) {
		s.check("map vote: three of four voted for ice, so it is ice", () -> match().arena().theme() == Theme.ICE);
		PartyTests.fallOff(s);
	}

	// ------------------------------------------------------------------ duel games

	private static void duel(MinecraftServer server, Script s, GameMode mode) {
		duel(server, s, mode, null);
	}

	/** {@code inCountdown}: runs once the players are in the arena, before the fight starts. */
	private static void duel(MinecraftServer server, Script s, GameMode mode, Runnable inCountdown) {
		String tag = mode.id();
		SelfTest.reset(s, tag);
		s.run(tag + ": A challenges B (best of 1)", () -> {
			SelfTest.join(server, A);
			SelfTest.join(server, B);
			// Both hold something else than the first slot, as after a fight with blocks in hand
			p(A).pickSlot(4);
			p(B).pickSlot(6);
			net.battlehub.data.PlayerStore.get(p(A)).duelFormat = "bo1";
			net.battlehub.data.PlayerStore.get(p(B)).duelFormat = "bo1";
			net.battlehub.queue.Challenges.send(p(A), p(B), mode);
			net.battlehub.queue.Challenges.accept(p(B), p(A));
		});
		if (inCountdown != null) {
			s.waitFor(tag + ": in the arena, counting down", 600, () -> {
				Match m = Matches.of(p(A).getUUID());
				return m != null && m.phase() == Match.Phase.COUNTDOWN;
			});
			s.run(tag + ": during the countdown", inCountdown);
		}
		s.waitFor(tag + ": fight running", 600, () -> {
			Match m = Matches.of(p(A).getUUID());
			if (m != null && m.phase() == Match.Phase.RUNNING) {
				VARS.put("match", m);
				return true;
			}
			return false;
		});
	}

	private static void closed(Script s, String tag) {
		s.waitFor(tag + ": back in the lobby", 400, () -> match().phase() == Match.Phase.CLOSED && Sessions.isFree(p(A).getUUID())
				&& Sessions.isFree(p(B).getUUID()));
	}

	private static void bridge(MinecraftServer server, Script s) {
		String tag = "bridge";
		duel(server, s, GameMode.BRIDGE);
		s.check(tag + ": a Bridge match with clay in the team colours", () -> match() instanceof BridgeMatch
				&& p(A).getInventory().countItem(net.battlehub.util.Ids.item("minecraft:red_terracotta")) == 128
				&& p(B).getInventory().countItem(net.battlehub.util.Ids.item("minecraft:blue_terracotta")) == 128);
		s.check(tag + ": A may build on the path but not in a goal", () -> {
			BlockPos path = match().arena().origin().offset(-10, 0, 1);
			BlockPos goal = match().arena().mark("goal_blue");
			return match().onPlaceBlock(p(A), path, new ItemStack(net.battlehub.util.Ids.item("minecraft:red_terracotta")))
					&& !match().onPlaceBlock(p(A), goal, new ItemStack(net.battlehub.util.Ids.item("minecraft:red_terracotta")));
		});
		s.run(tag + ": A jumps into the blue goal", () -> {
			BlockPos g = match().arena().mark("goal_blue");
			PartyTests.tp(p(A), g.getX() + 0.5, g.getY() - 1, g.getZ() + 0.5);
		});
		s.waitFor(tag + ": one goal for A, both back home", 10, () -> ((BridgeMatch) match()).goals(p(A).getUUID()) == 1
				&& match().phase() == Match.Phase.COUNTDOWN);
		s.waitFor(tag + ": the next point starts", 200, () -> match().phase() == Match.Phase.RUNNING);
		s.run(tag + ": B is knocked into the void", () -> PartyTests.tp(p(B), p(B).getX(), match().arena().voidY() - 3, p(B).getZ()));
		s.waitFor(tag + ": B is back on the island, still in the match", 20, () -> match().alive().contains(p(B).getUUID())
				&& p(B).getY() > match().arena().origin().getY());
		s.run(tag + ": A scores again", () -> {
			BlockPos g = match().arena().mark("goal_blue");
			PartyTests.tp(p(A), g.getX() + 0.5, g.getY() - 1, g.getZ() + 0.5);
		});
		s.waitFor(tag + ": two goals: A wins", 20, () -> match().phase() != Match.Phase.RUNNING
				&& match().outcome(p(A).getUUID()) == Outcome.WIN);
		closed(s, tag);
	}

	private static net.battlehub.games.BridgePartyMatch partyBridge() {
		return (net.battlehub.games.BridgePartyMatch) match();
	}

	/** A jumps into the goal of the first team that is not A's own. */
	private static void intoEnemyGoal(Script s, String tag) {
		s.run(tag + ": A jumps into an enemy goal", () -> {
			int own = partyBridge().team(p(A).getUUID());
			int enemy = own == 0 ? 1 : 0;
			BlockPos g = match().arena().mark("goal_" + enemy);
			PartyTests.tp(p(A), g.getX() + 0.5, g.getY() - 1, g.getZ() + 0.5);
		});
	}

	private static void bridgeDuos(Script s) {
		String tag = "bridge 2v2";
		s.check(tag + ": two teams of two, on two islands, with clay in their colour", () -> {
			net.battlehub.games.BridgePartyMatch m = partyBridge();
			int red = 0;
			for (UUID id : m.participants()) if (m.team(id) == 0) red++;
			return m.teamCount() == 2 && red == 2 && m.arena().marks("goal_1").size() == 1 && m.arena().marks("goal_2").isEmpty()
					&& p(A).getInventory().countItem(net.battlehub.util.Ids.item("minecraft:" + (m.team(p(A).getUUID()) == 0 ? "red" : "blue") + "_terracotta")) == 128;
		});
		s.check(tag + ": no friendly fire, enemies can be hit", () -> {
			net.battlehub.games.BridgePartyMatch m = partyBridge();
			UUID a = p(A).getUUID();
			UUID mate = null;
			UUID enemy = null;
			for (UUID id : m.participants()) {
				if (id.equals(a)) continue;
				if (m.team(id) == m.team(a)) mate = id; else enemy = id;
			}
			return mate != null && enemy != null && !m.isEnemy(a, mate) && m.isEnemy(a, enemy);
		});
		intoEnemyGoal(s, tag);
		s.waitFor(tag + ": a goal for A's team, everyone back home", 10, () -> partyBridge().goals(partyBridge().team(p(A).getUUID())) == 1
				&& match().phase() == Match.Phase.COUNTDOWN);
		s.waitFor(tag + ": the next point starts", 200, () -> match().phase() == Match.Phase.RUNNING);
		intoEnemyGoal(s, tag);
		s.waitFor(tag + ": two goals: A's team wins", 20, () -> match().phase() != Match.Phase.RUNNING
				&& match().outcome(p(A).getUUID()) == Outcome.WIN);
	}

	private static void bridgeFour(Script s) {
		String tag = "bridge 1v1v1v1";
		s.check(tag + ": four islands, everyone on their own", () -> {
			net.battlehub.games.BridgePartyMatch m = partyBridge();
			java.util.Set<Integer> seen = new java.util.HashSet<>();
			for (UUID id : m.participants()) seen.add(m.team(id));
			return m.teamCount() == 4 && seen.size() == 4 && m.arena().marks("goal_3").size() == 1 && m.isEnemy(p(A).getUUID(), p(B).getUUID());
		});
		s.check(tag + ": each one starts on their own island, near their own goal", () -> {
			net.battlehub.games.BridgePartyMatch m = partyBridge();
			for (UUID id : m.participants()) {
				ServerPlayer pl = m.player(id);
				BlockPos g = m.arena().mark("goal_" + m.team(id));
				if (pl == null || pl.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(g)) > 8) return false;
			}
			return true;
		});
		intoEnemyGoal(s, tag);
		s.waitFor(tag + ": a goal for A", 10, () -> partyBridge().goals(partyBridge().team(p(A).getUUID())) == 1
				&& match().phase() == Match.Phase.COUNTDOWN);
		s.waitFor(tag + ": the next point starts", 200, () -> match().phase() == Match.Phase.RUNNING);
		intoEnemyGoal(s, tag);
		s.waitFor(tag + ": two goals: A wins", 20, () -> match().phase() != Match.Phase.RUNNING
				&& match().outcome(p(A).getUUID()) == Outcome.WIN && match().outcome(p(B).getUUID()) != Outcome.WIN);
	}

	/**
	 * The Bridge 1v1v1v1: Chaos: four islands, a wind charge in the kit, a power-up on the middle; an event the
	 * second time round is twice as bad (tiny: a tenth of the size instead of half), and double points count.
	 */
	private static void bridgeFourChaos(Script s) {
		String tag = "bridge 1v1v1v1 chaos";
		s.check(tag + ": four islands, Chaos, a wind charge in the kit", () -> match() instanceof net.battlehub.games.BridgeChaosPartyMatch m
				&& m.teamCount() == 4 && !match().ranked && p(A).getInventory().countItem(net.battlehub.util.Ids.item("minecraft:wind_charge")) >= 1);
		s.run(tag + ": a power-up falls onto the middle", () -> ((net.battlehub.games.BridgeChaosPartyMatch) match()).dropPowerup("pearl"));
		s.check(tag + ": a glowing ender pearl lies in the middle", () -> {
			net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(match().arena().origin()).inflate(3, 5, 3);
			return !match().arena().level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box,
					e -> e.getItem().is(Items.ENDER_PEARL) && e.hasGlowingTag()).isEmpty();
		});
		s.run(tag + ": tiny, the second time round", () -> ((net.battlehub.games.BridgeChaosPartyMatch) match()).event("tiny", 1));
		s.check(tag + ": twice as bad: A shrinks to a tenth (not to half)", () -> {
			double scale = p(A).getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
			net.battlehub.BattleHub.LOGGER.info("SELFTEST chaos tiny x2: scale {}", scale);
			return Math.abs(scale - 0.1) < 1e-3;
		});
		s.run(tag + ": everything ends", () -> ((net.battlehub.games.BridgeChaosPartyMatch) match()).chaos().finishAll(true));
		s.check(tag + ": A has their own size again", () -> Math.abs(p(A).getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SCALE) - 1) < 1e-6
				&& !net.battlehub.games.duel.ChaosDirector.changed(p(A)));
		s.run(tag + ": the next goal counts double", () -> ((net.battlehub.games.BridgeChaosPartyMatch) match()).event("double_points", 0));
		intoEnemyGoal(s, tag);
		s.waitFor(tag + ": one double goal: A wins (two to win)", 20, () -> match().phase() != Match.Phase.RUNNING
				&& match().outcome(p(A).getUUID()) == Outcome.WIN);
	}

	private static net.battlehub.games.duel.BridgeChaosMatch chaos() {
		return (net.battlehub.games.duel.BridgeChaosMatch) match();
	}

	private static void bridgeChaos(MinecraftServer server, Script s) {
		String tag = "bridge chaos";
		duel(server, s, GameMode.BRIDGE_CHAOS);
		s.check(tag + ": a Chaos match (never ranked) with a wind charge in the kit", () -> match() instanceof net.battlehub.games.duel.BridgeChaosMatch
				&& !match().ranked && p(A).getInventory().countItem(net.battlehub.util.Ids.item("minecraft:wind_charge")) >= 1);
		s.run(tag + ": a power-up falls onto the middle", () -> ((net.battlehub.games.duel.BridgeChaosMatch) match()).dropPowerup("pearl"));
		s.check(tag + ": a glowing ender pearl lies in the middle", () -> {
			net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(match().arena().origin()).inflate(3, 5, 3);
			return !match().arena().level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box,
					e -> e.getItem().is(Items.ENDER_PEARL) && e.hasGlowingTag()).isEmpty();
		});
		s.run(tag + ": A builds a block over the middle", () -> {
			BlockPos at = match().arena().origin().offset(1, 0, 3);
			VARS.put("chaosBlock", at);
			if (match().onPlaceBlock(p(A), at, new ItemStack(net.battlehub.util.Ids.item("minecraft:red_terracotta")))) {
				match().arena().level().setBlock(at, net.battlehub.util.Ids.state("minecraft:red_terracotta"), 3);
			}
		});
		s.pause(3);
		s.run(tag + ": the bridge crumbles", () -> ((net.battlehub.games.duel.BridgeChaosMatch) match()).event("crumble"));
		s.check(tag + ": the block A built is gone", () -> match().arena().level().getBlockState((BlockPos) VARS.get("chaosBlock")).isAir());
		s.run(tag + ": low gravity for everyone", () -> ((net.battlehub.games.duel.BridgeChaosMatch) match()).event("low_gravity"));
		s.check(tag + ": A jumps higher and floats", () -> p(A).hasEffect(net.battlehub.util.Ids.effect("minecraft:jump_boost").orElseThrow())
				&& p(A).hasEffect(net.battlehub.util.Ids.effect("minecraft:slow_falling").orElseThrow()));
		s.check(tag + ": every effect, attribute, item, potion and special of the hundred events exists", () -> {
			java.util.List<String> unknown = net.battlehub.games.duel.ChaosDirector.unknown(server);
			if (!unknown.isEmpty()) net.battlehub.BattleHub.LOGGER.warn("SELFTEST chaos: unknown {}", unknown);
			return unknown.isEmpty() && net.battlehub.core.chaos.ChaosCatalog.problems().isEmpty();
		});
		s.check(tag + ": the match plans at least 25 events, the five fixed ones among the first 15", () -> {
			java.util.List<String> order = chaos().chaos().plan().order();
			for (String f : net.battlehub.core.chaos.ChaosCatalog.FIXED) {
				int at = order.indexOf(f);
				if (at < 0 || at >= net.battlehub.core.chaos.ChaosPlan.FIXED_WITHIN) return false;
			}
			return order.size() >= 25 && new java.util.HashSet<>(order).size() == order.size();
		});
		s.run(tag + ": tiny!", () -> chaos().event("tiny"));
		s.check(tag + ": A is half as big", () -> Math.abs(p(A).getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SCALE) - 0.5) < 1e-6);
		s.run(tag + ": hands off the clay", () -> chaos().event("hands_off"));
		s.check(tag + ": nobody can build", () -> !match().onPlaceBlock(p(A), match().arena().origin().offset(1, 0, 2),
				new ItemStack(net.battlehub.util.Ids.item("minecraft:red_terracotta"))));
		s.run(tag + ": all events at once", () -> {
			for (net.battlehub.core.chaos.ChaosEvent e : net.battlehub.core.chaos.ChaosCatalog.all()) chaos().event(e.id());
		});
		s.pause(40);
		s.check(tag + ": both still play, events are running", () -> match().phase() == Match.Phase.RUNNING
				&& match().alive().size() == 2 && chaos().chaos().running() > 0);
		s.run(tag + ": every event ends", () -> chaos().chaos().finishAll(true));
		s.check(tag + ": everything taken back: size, gravity, wings, chickens, no-build", () -> {
			for (ServerPlayer pl : java.util.List.of(p(A), p(B))) {
				if (net.battlehub.games.duel.ChaosDirector.changed(pl)) return false;
				if (pl.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(Items.ELYTRA)) return false;
				if (Math.abs(pl.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SCALE) - 1.0) > 1e-6) return false;
			}
			net.minecraft.world.phys.AABB box = match().arena().bounds().inflate(16);
			return chaos().chaos().running() == 0 && !chaos().doubleGoal()
					&& match().arena().level().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box,
							e -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("chicken")).isEmpty();
		});
		s.run(tag + ": A healed, standing still on their island", () -> {
			p(A).removeAllEffects();
			p(A).setHealth(p(A).getMaxHealth());
			p(A).setRemainingFireTicks(0);
			net.minecraft.world.phys.Vec3 home = match().arena().spawn(0).pos();
			PartyTests.tp(p(A), home.x, home.y, home.z);
			p(A).setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		});
		s.pause(20);
		s.run(tag + ": B's snowball falls onto A", () -> {
			net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) p(A).level();
			net.minecraft.world.entity.Entity ball = net.battlehub.hub.ClientEntities.type("minecraft:snowball").create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			((net.minecraft.world.entity.projectile.Projectile) ball).setOwner(p(B));
			ball.snapTo(p(A).getX(), p(A).getY() + 2.6, p(A).getZ());
			ball.setDeltaMovement(0, -0.8, 0);
			level.addFreshEntity(ball);
			VARS.put("snowBall", ball);
			VARS.put("snowHealth", p(A).getHealth());
			VARS.put("snowPolls", 0);
		});
		s.waitFor(tag + ": the snowball hurts A", 40, () -> {
			boolean hurt = p(A).getHealth() < (float) VARS.get("snowHealth");
			int polls = (int) VARS.get("snowPolls") + 1;
			VARS.put("snowPolls", polls);
			if (!hurt && polls % 10 == 0) {
				net.minecraft.world.entity.Entity ball = (net.minecraft.world.entity.Entity) VARS.get("snowBall");
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST snowball after {} ticks: A at {} health {}, ball {} at {}, phase {}", polls,
						p(A).position(), p(A).getHealth(), ball.isRemoved() ? "gone" : "flying", ball.position(), match().phase());
			}
			return hurt;
		});
		s.run(tag + ": the next goal counts double", () -> chaos().event("double_points"));
		s.run(tag + ": A jumps into the blue goal", () -> {
			BlockPos g = match().arena().mark("goal_blue");
			PartyTests.tp(p(A), g.getX() + 0.5, g.getY() - 1, g.getZ() + 0.5);
		});
		s.waitFor(tag + ": two goals for A at once, and the next one is normal again", 10, () ->
				((BridgeMatch) match()).goals(p(A).getUUID()) == 2 && !((net.battlehub.games.duel.BridgeChaosMatch) match()).doubleGoal());
		s.waitFor(tag + ": the next point starts", 200, () -> match().phase() == Match.Phase.RUNNING);
		s.run(tag + ": A scores once more", () -> {
			BlockPos g = match().arena().mark("goal_blue");
			PartyTests.tp(p(A), g.getX() + 0.5, g.getY() - 1, g.getZ() + 0.5);
		});
		s.waitFor(tag + ": three goals: A wins", 20, () -> match().phase() != Match.Phase.RUNNING && match().outcome(p(A).getUUID()) == Outcome.WIN);
		closed(s, tag);
	}

	private static void boxing(MinecraftServer server, Script s) {
		String tag = "boxing";
		duel(server, s, GameMode.BOXING);
		for (int i = 1; i <= 4; i++) {
			int n = i;
			s.run(tag + ": A lands hit " + n, () -> {
				ServerPlayer b = p(B);
				b.hurtServer(b.level(), b.damageSources().playerAttack(p(A)), 3f);
			});
			s.check(tag + ": " + n + " hits, no damage", () -> ((BoxingMatch) match()).hits(p(A).getUUID()) >= n
					|| match().phase() != Match.Phase.RUNNING);
			s.pause(11);
		}
		s.waitFor(tag + ": four hits win", 20, () -> match().phase() != Match.Phase.RUNNING && match().outcome(p(A).getUUID()) == Outcome.WIN);
		closed(s, tag);
	}

	private static void buildUhc(MinecraftServer server, Script s) {
		String tag = "build_uhc";
		duel(server, s, GameMode.BUILD_UHC, () -> {
			// Something left from an earlier game on this spot: an item and an arrow
			Match m = Matches.of(p(A).getUUID());
			net.minecraft.server.level.ServerLevel l = m.arena().level();
			BlockPos at = m.arena().origin().above(2);
			net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(l, at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
					new ItemStack(Items.DIAMOND));
			l.addFreshEntity(item);
			VARS.put("leftover", item);
		});
		s.check(tag + ": what lay in the arena before the start is gone", () ->
				((net.minecraft.world.entity.Entity) VARS.get("leftover")).isRemoved());
		s.check(tag + ": after the kit, client and server both hold the first slot", () ->
				p(A).getInventory().getSelectedSlot() == 0 && p(A).clientSlot() == 0 && p(B).getInventory().getSelectedSlot() == 0 && p(B).clientSlot() == 0);
		s.check(tag + ": the arena's chunks stay loaded while it is in use", () -> {
			net.battlehub.arena.Arena a = match().arena();
			// the far corner of where play happens (plus room for arrows); landmarks further out load as usual
			net.minecraft.world.phys.AABB held = a.held();
			BlockPos far = BlockPos.containing(held.maxX - 1, 0, held.maxZ - 1);
			int[] core = a.blueprint().core();
			BlockPos edge = core == null ? far : a.origin().offset(core[3], 0, core[5]);
			long key = (far.getX() >> 4 & 0xFFFFFFFFL) | ((long) (far.getZ() >> 4) << 32);
			long edgeKey = (edge.getX() >> 4 & 0xFFFFFFFFL) | ((long) (edge.getZ() >> 4) << 32);
			return net.battlehub.arena.ArenaChunks.held() > 0 && a.level().getForceLoadedChunks().contains(key)
					&& a.level().getForceLoadedChunks().contains(edgeKey);
		});
		s.check(tag + ": building stops at the walls and below the top", () -> {
			net.battlehub.arena.Arena a = match().arena();
			BlockPos o = a.origin();
			ItemStack cob = new ItemStack(Items.COBBLESTONE);
			BlockPos inside = BlockPos.containing(p(A).position()).above(3);
			BlockPos beyondWall = o.offset(a.blueprint().maxX() - 2, 3, 0);
			BlockPos tooHigh = o.offset(0, a.blueprint().playTop() + 1, 0);
			boolean barrier = a.blueprint().blocks().values().stream().anyMatch(st -> st.is(Blocks.BARRIER));
			return a.blueprint().hasPlayArea() && barrier && match().onPlaceBlock(p(A), inside, cob)
					&& !match().onPlaceBlock(p(A), beyondWall, cob) && !match().onPlaceBlock(p(A), tooHigh, cob);
		});
		s.check(tag + ": water flows on the field, not over the wall, into the void or between arenas", () -> {
			net.battlehub.arena.Arena a = match().arena();
			BlockPos o = a.origin();
			net.minecraft.server.level.ServerLevel l = a.level();
			int belowVoid = (int) Math.floor(a.voidY()) - o.getY() - 2;
			return net.battlehub.arena.FluidGuard.maySpread(l, o.above(2)) && !net.battlehub.arena.FluidGuard.maySpread(l, o.offset(a.blueprint().maxX() - 2, 3, 0))
					&& !net.battlehub.arena.FluidGuard.maySpread(l, o.offset(0, belowVoid, 0)) && !net.battlehub.arena.FluidGuard.maySpread(l, o.offset(500, 0, 0));
		});
		s.check(tag + ": a bucket is emptied on the field, not beyond the wall", () -> {
			net.battlehub.arena.Arena a = match().arena();
			ItemStack before = p(A).getMainHandItem().copy();
			p(A).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
			boolean in = net.battlehub.arena.FluidGuard.mayEmpty(p(A), a.level(), BlockPos.containing(p(A).position()).above(3));
			boolean out = net.battlehub.arena.FluidGuard.mayEmpty(p(A), a.level(), a.origin().offset(a.blueprint().maxX() - 2, 3, 0));
			p(A).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, before);
			return in && !out;
		});
		s.run(tag + ": a chest with loot and an arrow in the arena", () -> {
			net.battlehub.arena.Arena a = match().arena();
			BlockPos c = BlockPos.containing(p(B).position()).offset(2, 0, 0);
			a.level().setBlock(c, Blocks.CHEST.defaultBlockState(), 3);
			if (a.level().getBlockEntity(c) instanceof Container box) {
				box.setItem(0, new ItemStack(Items.GOLDEN_APPLE, 5));
				box.setItem(1, new ItemStack(Items.DIAMOND_SWORD));
			}
			net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(a.level(), c.getX() + 0.5, c.getY() + 2, c.getZ() + 0.5,
					new ItemStack(Items.ARROW, 3));
			a.level().addFreshEntity(item);
			VARS.put("uhcBox", a.bounds());
			VARS.put("uhcLevel", a.level());
		});
		s.check(tag + ": the kit has blocks, buckets and golden apples; survival mode", () ->
				p(A).getInventory().countItem(Items.COBBLESTONE) == 64 && p(A).getInventory().countItem(Items.LAVA_BUCKET) == 2
						&& p(A).getInventory().countItem(Items.GOLDEN_APPLE) == 6 && p(A).gameMode() == net.minecraft.world.level.GameType.SURVIVAL);
		s.check(tag + ": a placed block may go, the arena may not", () -> {
			BlockPos up = net.minecraft.core.BlockPos.containing(p(A).position()).above(3);
			boolean placed = match().onPlaceBlock(p(A), up, new ItemStack(Items.COBBLESTONE));
			return placed && !match().onBreakBlock(p(A), match().arena().origin().below(), Blocks.STONE.defaultBlockState());
		});
		s.waitFor(tag + ": no natural healing (hunger held at 17)", 20, () -> p(A).getFoodData().getFoodLevel() <= 17);
		s.pause(12);
		s.run(tag + ": A kills B", () -> PartyTests.hit(B, A, 1000f));
		s.waitFor(tag + ": A wins", 20, () -> match().phase() != Match.Phase.RUNNING && match().outcome(p(A).getUUID()) == Outcome.WIN);
		closed(s, tag);
		s.check(tag + ": back in the lobby, client and server agree on the hotbar slot", () ->
				p(A).getInventory().getSelectedSlot() == p(A).clientSlot() && p(B).getInventory().getSelectedSlot() == p(B).clientSlot());
		s.waitFor(tag + ": arena cleared", 600, () -> net.battlehub.arena.ArenaSlots.busy() == 0);
		s.check(tag + ": nothing left lying where the arena was (the chest did not spill), chunks let go", () -> {
			net.minecraft.world.phys.AABB box = (net.minecraft.world.phys.AABB) VARS.get("uhcBox");
			net.minecraft.server.level.ServerLevel l = (net.minecraft.server.level.ServerLevel) VARS.get("uhcLevel");
			return net.battlehub.arena.Loose.count(l, box.inflate(16)) == 0 && net.battlehub.arena.ArenaChunks.held() == 0;
		});
		chestClear(s);
	}

	/** Clearing a chest full of loot leaves no items behind (in a spot no arena uses). */
	private static void chestClear(Script s) {
		String tag = "arena clearing";
		s.run(tag + ": a barrel full of loot in a spot no arena uses (its chunk is held, as an arena's is)", () -> {
			net.minecraft.server.level.ServerLevel l = net.battlehub.arena.ArenaWorld.level(p(A).level().getServer());
			BlockPos o = net.battlehub.arena.ArenaWorld.origin(net.battlehub.arena.ArenaSlots.capacity() - 1).above(40);
			net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(o).inflate(4);
			net.battlehub.arena.ArenaChunks.hold(l, box);
			l.setBlock(o.below(), Blocks.STONE.defaultBlockState(), 3);
			l.setBlock(o, Blocks.BARREL.defaultBlockState(), 3);
			if (l.getBlockEntity(o) instanceof Container c) {
				for (int i = 0; i < 9; i++) c.setItem(i, new ItemStack(Items.GOLD_INGOT, 16));
			}
			VARS.put("clearBox", box);
			VARS.put("clearDone", false);
		});
		// A freshly loaded chunk shows its entities only after a moment; in a game the arena has long been loaded
		s.pause(40);
		s.run(tag + ": an item on the ground, then the area is cleared", () -> {
			net.minecraft.server.level.ServerLevel l = net.battlehub.arena.ArenaWorld.level(p(A).level().getServer());
			net.minecraft.world.phys.AABB box = (net.minecraft.world.phys.AABB) VARS.get("clearBox");
			BlockPos o = BlockPos.containing(box.getCenter());
			l.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(l, o.getX() + 0.5, o.getY() + 2, o.getZ() + 0.5, new ItemStack(Items.EMERALD)));
			VARS.put("clearSeen", net.battlehub.arena.Loose.count(l, box.inflate(16)));
			net.battlehub.arena.ArenaBuilder.clear(l, box, () -> VARS.put("clearDone", true));
		});
		s.waitFor(tag + ": done", 200, () -> Boolean.TRUE.equals(VARS.get("clearDone")));
		s.check(tag + ": the barrel is gone and nothing lies on the ground", () -> {
			net.minecraft.server.level.ServerLevel l = net.battlehub.arena.ArenaWorld.level(p(A).level().getServer());
			net.minecraft.world.phys.AABB box = (net.minecraft.world.phys.AABB) VARS.get("clearBox");
			BlockPos o = BlockPos.containing(box.getCenter());
			int left = net.battlehub.arena.Loose.count(l, box.inflate(16));
			boolean ok = l.getBlockState(o).isAir() && left == 0 && (int) VARS.get("clearSeen") == 1;
			if (!ok) {
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST arena clearing: block {}, {} loose entities left, {} seen before", l.getBlockState(o), left, VARS.get("clearSeen"));
			}
			net.battlehub.arena.ArenaChunks.release(l, box);
			return ok;
		});
	}

	// ------------------------------------------------------------------ a hand-built map

	private static Path mapFile() {
		return CustomMaps.dir().resolve("sword").resolve("selftest_map.nbt");
	}

	private static ListTag ints(int... v) {
		ListTag l = new ListTag();
		for (int i : v) l.add(IntTag.valueOf(i));
		return l;
	}

	/** A 9 x 3 x 5 stone platform with two spawn markers, written as a structure file. */
	private static void writeMap() throws java.io.IOException {
		CompoundTag root = new CompoundTag();
		root.put("size", ints(9, 3, 5));
		ListTag palette = new ListTag();
		CompoundTag stone = new CompoundTag();
		stone.putString("Name", "minecraft:stone");
		palette.add(stone);
		CompoundTag sb = new CompoundTag();
		sb.putString("Name", "minecraft:structure_block");
		CompoundTag props = new CompoundTag();
		props.putString("mode", "data");
		sb.put("Properties", props);
		palette.add(sb);
		root.put("palette", palette);
		ListTag blocks = new ListTag();
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 5; z++) {
				CompoundTag b = new CompoundTag();
				b.put("pos", ints(x, 0, z));
				b.putInt("state", 0);
				blocks.add(b);
			}
		}
		int[][] spawns = {{1, 1, 2, -90}, {7, 1, 2, 90}};
		for (int[] sp : spawns) {
			CompoundTag b = new CompoundTag();
			b.put("pos", ints(sp[0], sp[1], sp[2]));
			b.putInt("state", 1);
			CompoundTag nbt = new CompoundTag();
			nbt.putString("id", "minecraft:structure_block");
			nbt.putString("mode", "DATA");
			nbt.putString("metadata", "spawn:" + sp[3]);
			b.put("nbt", nbt);
			blocks.add(b);
		}
		root.put("blocks", blocks);
		root.put("entities", new ListTag());
		NbtUtils.addCurrentDataVersion(root);
		Files.createDirectories(mapFile().getParent());
		NbtIo.writeCompressed(root, mapFile());
	}

	private static void customMap(MinecraftServer server, Script s) {
		String tag = "custom map";
		SelfTest.reset(s, tag);
		s.run(tag + ": a structure file in maps/sword, maps reloaded", () -> {
			try {
				writeMap();
			} catch (java.io.IOException e) {
				throw new IllegalStateException(e);
			}
			CustomMaps.load(server);
			ConfigManager.config().arenas.customMapChance = 1.0;
		});
		s.check(tag + ": read with two spawns, offered to sword duels only", () -> {
			CustomMaps.Map m = CustomMaps.byId("selftest_map");
			if (m == null || m.spawns() != 2 || m.blockCount() != 45) {
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST custom maps: {} loaded; file there: {}; selftest_map: {}",
						CustomMaps.all().size(), Files.exists(mapFile()),
						m == null ? "missing" : m.spawns() + " spawns, " + m.blockCount() + " blocks, markers " + m.markers + ", size " + m.size);
			}
			return m != null && m.spawns() == 2 && m.blockCount() == 45 && MapRules.maps(GameMode.SWORD).contains(m) && MapRules.maps(GameMode.SUMO).isEmpty()
					&& MapRules.maps(GameMode.BRIDGE).isEmpty();
		});
		duel(server, s, GameMode.SWORD);
		s.check(tag + ": the duel is on the hand-built map, players on its spawns, markers gone", () -> {
			Match m = match();
			if (m.customMap() == null || !m.customMap().id.equals("selftest_map")) return false;
			var a = m.arena();
			BlockPos floor = BlockPos.containing(a.spawn(0).pos()).below();
			BlockPos marker = BlockPos.containing(a.spawn(0).pos());
			boolean ok = a.level().getBlockState(floor).is(Blocks.STONE) && a.level().getBlockState(marker).isAir()
					&& p(A).position().distanceTo(a.spawn(0).pos()) < 1.5 && p(B).position().distanceTo(a.spawn(1).pos()) < 1.5;
			if (!ok) {
				net.battlehub.BattleHub.LOGGER.warn("SELFTEST custom map placed: floor {} marker {} spawn0 {} A at {} spawn1 {} B at {}",
						a.level().getBlockState(floor), a.level().getBlockState(marker), a.spawn(0).pos(), p(A).position(),
						a.spawn(1).pos(), p(B).position());
			}
			return ok;
		});
		s.run(tag + ": end it; the map file goes again", () -> {
			Matches.end(match().id);
			ConfigManager.config().arenas.customMapChance = 0.0;
			try {
				Files.deleteIfExists(mapFile());
			} catch (java.io.IOException ignored) {
				// the next start finds it again; harmless
			}
			CustomMaps.load(server);
		});
		s.check(tag + ": no custom maps left", () -> CustomMaps.all().isEmpty());
	}
}
