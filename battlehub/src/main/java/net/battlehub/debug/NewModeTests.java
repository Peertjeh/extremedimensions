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
			c.arenas.customMapChance = 0.0;
		});
		PartyTests.party(server, s, GameMode.SPLEEF, NewModeTests::spleef);
		PartyTests.party(server, s, GameMode.SKYWARS, NewModeTests::skywars);
		PartyTests.party(server, s, GameMode.MURDER_MYSTERY, NewModeTests::murder);
		PartyTests.party(server, s, GameMode.SUMO, NewModeTests::votedIce,
				new String[] {"map:theme:ice", "map:theme:ice", "map:theme:ice", "map:theme:desert"});
		PartyTests.party(server, s, GameMode.BRIDGE_DUOS, NewModeTests::bridgeDuos);
		PartyTests.party(server, s, GameMode.BRIDGE_FOUR, NewModeTests::bridgeFour);
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
		PartyTests.fallOff(s);
	}

	private static void skywars(Script s) {
		s.check("skywars: the chests are filled", () -> {
			SkyWarsMatch m = (SkyWarsMatch) match();
			if (m.chestsFilled() < 4) return false;
			BlockPos chest = m.arena().marks("center_chest").get(0);
			if (!(m.arena().level().getBlockEntity(chest) instanceof Container c)) return false;
			for (int i = 0; i < c.getContainerSize(); i++) {
				if (!c.getItem(i).isEmpty()) return true;
			}
			return false;
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
		s.run("skywars: C and D fall", () -> {
			for (String n : new String[] {"BHTestC", "BHTestD"}) {
				ServerPlayer pl = p(n);
				PartyTests.tp(pl, pl.getX(), match().arena().voidY() - 3, pl.getZ());
			}
		});
		s.waitFor("skywars: A is the last one and wins", 60, () -> match().phase() != Match.Phase.RUNNING
				&& match().outcome(p(A).getUUID()) == Outcome.WIN);
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
		s.run(tag + ": the next goal counts double", () -> ((net.battlehub.games.duel.BridgeChaosMatch) match()).event("double_goal"));
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
			BlockPos far = a.origin().offset(a.blueprint().maxX(), 0, a.blueprint().maxZ());
			long key = (far.getX() >> 4 & 0xFFFFFFFFL) | ((long) (far.getZ() >> 4) << 32);
			return net.battlehub.arena.ArenaChunks.held() > 0 && a.level().getForceLoadedChunks().contains(key);
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
