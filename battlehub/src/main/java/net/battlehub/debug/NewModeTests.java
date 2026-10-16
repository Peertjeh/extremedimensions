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
			c.arenas.customMapChance = 0.0;
		});
		PartyTests.party(server, s, GameMode.SPLEEF, NewModeTests::spleef);
		PartyTests.party(server, s, GameMode.SKYWARS, NewModeTests::skywars);
		PartyTests.party(server, s, GameMode.MURDER_MYSTERY, NewModeTests::murder);
		PartyTests.party(server, s, GameMode.SUMO, NewModeTests::votedIce,
				new String[] {"map:theme:ice", "map:theme:ice", "map:theme:ice", "map:theme:desert"});
		bridge(server, s);
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
		String tag = mode.id();
		SelfTest.reset(s, tag);
		s.run(tag + ": A challenges B (best of 1)", () -> {
			SelfTest.join(server, A);
			SelfTest.join(server, B);
			net.battlehub.data.PlayerStore.get(p(A)).duelFormat = "bo1";
			net.battlehub.data.PlayerStore.get(p(B)).duelFormat = "bo1";
			net.battlehub.queue.Challenges.send(p(A), p(B), mode);
			net.battlehub.queue.Challenges.accept(p(B), p(A));
		});
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
		duel(server, s, GameMode.BUILD_UHC);
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
			return m != null && m.spawns() == 2 && MapRules.maps(GameMode.SWORD).contains(m) && MapRules.maps(GameMode.SUMO).isEmpty()
					&& MapRules.maps(GameMode.BRIDGE).isEmpty();
		});
		duel(server, s, GameMode.SWORD);
		s.check(tag + ": the duel is on the hand-built map, players on its spawns, markers gone", () -> {
			Match m = match();
			if (m.customMap() == null || !m.customMap().id.equals("selftest_map")) return false;
			var a = m.arena();
			BlockPos floor = BlockPos.containing(a.spawn(0).pos()).below();
			BlockPos marker = BlockPos.containing(a.spawn(0).pos());
			return a.level().getBlockState(floor).is(Blocks.STONE) && a.level().getBlockState(marker).isAir()
					&& p(A).position().distanceTo(a.spawn(0).pos()) < 1.5 && p(B).position().distanceTo(a.spawn(1).pos()) < 1.5;
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
