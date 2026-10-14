package net.battlehub.debug;

import static net.battlehub.debug.PartyTests.NAMES;
import static net.battlehub.debug.PartyTests.hit;
import static net.battlehub.debug.PartyTests.match;
import static net.battlehub.debug.SelfTest.VARS;
import static net.battlehub.debug.SelfTest.p;

import java.util.UUID;

import net.battlehub.config.BedWarsConfig;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.PropHuntConfig;
import net.battlehub.core.GameMode;
import net.battlehub.core.bedwars.Shop;
import net.battlehub.core.bedwars.TeamState;
import net.battlehub.games.Visibility;
import net.battlehub.games.bedwars.BedWarsMatch;
import net.battlehub.games.bedwars.BedWarsShop;
import net.battlehub.games.prophunt.Disguise;
import net.battlehub.games.prophunt.PropHuntMatch;
import net.battlehub.match.Match;
import net.battlehub.util.Icon;
import net.battlehub.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Self-test scenarios for Bed Wars and Prop Hunt, played by the four test players. */
final class ArcadeTests {
	private ArcadeTests() {}

	static void all(MinecraftServer server, Script s) {
		s.run("arcade: quick timers for the test", () -> {
			BedWarsConfig bw = ConfigManager.bedwars();
			bw.respawnSeconds = 1;
			bw.podiumSeconds = 2;
			bw.tntFuseSeconds = 1;
			PropHuntConfig ph = ConfigManager.prophunt();
			ph.rounds = 1;
			ph.hideSeconds = 2;
			ph.huntSeconds = 60;
			ph.roundEndSeconds = 1;
			ph.solidifySeconds = 1;
			ph.hunterRespawnSeconds = 1;
		});
		PartyTests.party(server, s, GameMode.BED_WARS, ArcadeTests::bedWars);
		PartyTests.party(server, s, GameMode.BED_WARS, ArcadeTests::bedWarsSolo, new String[] {"format:solo", "format:solo", "format:duo", ""});
		PartyTests.party(server, s, GameMode.PROP_HUNT, ArcadeTests::propHunt);
	}

	// ------------------------------------------------------------------ Bed Wars

	private static BedWarsMatch bw() {
		return (BedWarsMatch) match();
	}

	private static String var(String k) {
		return (String) VARS.get(k);
	}

	private static ServerPlayer pv(String k) {
		return p(var(k));
	}

	private static void bedWars(Script s) {
		s.run("bedwars: find the teams", () -> {
			BedWarsMatch m = bw();
			int a = m.teamOf(p("BHTestA").getUUID());
			int e = 0;
			for (String n : NAMES) {
				if (n.equals("BHTestA")) continue;
				if (m.teamOf(p(n).getUUID()) == a) {
					VARS.put("T", n);
				} else {
					VARS.put(e++ == 0 ? "E1" : "E2", n);
				}
			}
		});
		s.check("bedwars: two teams of two, in survival with the start kit and locked team armour", () -> {
			BedWarsMatch m = bw();
			if (!"duo".equals(m.format)) return false;
			ServerPlayer a = p("BHTestA");
			ItemStack legs = a.getItemBySlot(EquipmentSlot.LEGS);
			return m.teams().size() == 2 && m.teams().get(0).members().size() == 2 && VARS.get("T") != null && VARS.get("E2") != null
					&& a.gameMode() == GameType.SURVIVAL && a.getInventory().countItem(Items.WOODEN_SWORD) == 1
					&& legs.is(Items.LEATHER_LEGGINGS) && BedWarsShop.ARMOR_TAG.equals(Icon.tagOf(legs))
					&& m.stage() == BedWarsMatch.Stage.ACTIVE;
		});
		s.waitFor("bedwars: the island generator drops iron", 120, () -> {
			BlockPos gen = bw().arena().mark("gen_0");
			return !bw().arena().level().getEntitiesOfClass(ItemEntity.class, new AABB(gen).inflate(2), e -> e.getItem().is(Items.IRON_INGOT)).isEmpty();
		});
		// The shop: too expensive, bought, no room, upgrades
		s.run("bedwars: A has 3 iron", () -> {
			ServerPlayer a = p("BHTestA");
			a.getInventory().clearContent();
			a.getInventory().setItem(0, new ItemStack(Items.WOODEN_SWORD));
			a.getInventory().setItem(1, new ItemStack(Items.IRON_INGOT, 3));
		});
		s.check("bedwars: wool for 4 iron is too expensive and nothing is taken", () ->
				bw().buy(p("BHTestA"), "wool") == Shop.Result.TOO_EXPENSIVE && p("BHTestA").getInventory().countItem(Items.IRON_INGOT) == 3);
		s.check("bedwars: with 4 iron the wool is bought in the team colour, the iron is gone", () -> {
			ServerPlayer a = p("BHTestA");
			a.getInventory().add(new ItemStack(Items.IRON_INGOT, 1));
			String wool = "minecraft:" + bw().color(bw().teamOf(a.getUUID())) + "_wool";
			return bw().buy(a, "wool") == Shop.Result.BOUGHT && a.getInventory().countItem(Items.IRON_INGOT) == 0
					&& a.getInventory().countItem(net.battlehub.util.Ids.item(wool)) == 16;
		});
		s.check("bedwars: a full inventory buys nothing and keeps its iron", () -> {
			ServerPlayer a = p("BHTestA");
			var inv = a.getInventory();
			inv.clearContent();
			inv.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
			for (int i = 1; i < 36; i++) inv.setItem(i, new ItemStack(Items.DIRT, 64));
			return bw().buy(a, "wool") == Shop.Result.NO_SPACE && inv.countItem(Items.IRON_INGOT) == 64;
		});
		s.check("bedwars: chainmail armour replaces the leggings, once", () -> {
			ServerPlayer a = p("BHTestA");
			var inv = a.getInventory();
			inv.clearContent();
			inv.setItem(0, new ItemStack(Items.WOODEN_SWORD));
			inv.setItem(1, new ItemStack(Items.IRON_INGOT, 48));
			return bw().buy(a, "chainmail_armor") == Shop.Result.BOUGHT && a.getItemBySlot(EquipmentSlot.LEGS).is(Items.CHAINMAIL_LEGGINGS)
					&& a.getItemBySlot(EquipmentSlot.FEET).is(Items.CHAINMAIL_BOOTS) && inv.countItem(Items.IRON_INGOT) == 24
					&& bw().buy(a, "chainmail_armor") == Shop.Result.ALREADY_OWNED && inv.countItem(Items.IRON_INGOT) == 24;
		});
		s.check("bedwars: a better sword replaces the wooden one", () -> {
			ServerPlayer a = p("BHTestA");
			return bw().buy(a, "stone_sword") == Shop.Result.BOUGHT && a.getInventory().countItem(Items.WOODEN_SWORD) == 0
					&& a.getInventory().countItem(Items.STONE_SWORD) == 1;
		});
		s.check("bedwars: sharpness for the team sharpens the swords of both players", () -> {
			ServerPlayer a = p("BHTestA");
			a.getInventory().add(new ItemStack(Items.DIAMOND, 4));
			return bw().buyUpgrade(a, "sharpness") == Shop.Result.BOUGHT && a.getInventory().countItem(Items.DIAMOND) == 0
					&& !a.getInventory().getItem(0).getEnchantments().isEmpty()
					&& !pv("T").getInventory().getItem(0).getEnchantments().isEmpty();
		});
		s.check("bedwars: swords cannot be dropped, resources can", () -> {
			ServerPlayer a = p("BHTestA");
			return !bw().allowsDrop(a, new ItemStack(Items.STONE_SWORD)) && bw().allowsDrop(a, new ItemStack(Items.GOLD_INGOT));
		});
		// Building
		s.check("bedwars: no building next to a spawn, above the limit or with blocks the shop does not sell", () -> {
			BedWarsMatch m = bw();
			ServerPlayer a = p("BHTestA");
			BlockPos o = m.arena().origin();
			ItemStack wool = new ItemStack(Ids.item("minecraft:red_wool"), 4);
			return !m.onPlaceBlock(a, m.arena().mark("spawn_0").above(), wool)
					&& !m.onPlaceBlock(a, o.offset(0, m.settings().buildHeight + 4, 0), wool)
					&& !m.onPlaceBlock(a, o.offset(0, 6, 0), new ItemStack(Items.CRAFTING_TABLE));
		});
		s.run("bedwars: A places wool in the open (the world places it right after the check)", () -> {
			BlockPos at = bw().arena().origin().offset(0, 6, 0);
			boolean ok = bw().onPlaceBlock(p("BHTestA"), at, new ItemStack(Ids.item("minecraft:red_wool"), 4));
			VARS.put("placeOk", ok);
			if (ok) bw().arena().level().setBlock(at, Ids.state("minecraft:red_wool"), 3);
		});
		s.pause(2);
		s.check("bedwars: placed blocks break, the map does not", () -> {
			BedWarsMatch m = bw();
			ServerPlayer a = p("BHTestA");
			BlockPos placed = m.arena().origin().offset(0, 6, 0);
			BlockPos floor = m.arena().mark("spawn_0").below();
			return Boolean.TRUE.equals(VARS.get("placeOk")) && m.buildRules().isPlaced(placed) && !m.onBreakBlock(a, floor, m.arena().level().getBlockState(floor))
					&& m.onBreakBlock(a, placed, m.arena().level().getBlockState(placed)) && !m.buildRules().isPlaced(placed);
		});
		s.run("bedwars: wool on the middle island, TNT on top of it", () -> {
			BedWarsMatch m = bw();
			ServerLevel level = m.arena().level();
			BlockPos wool = m.arena().origin().offset(3, 1, 3);
			level.setBlock(wool, Ids.state("minecraft:white_wool"), 3);
			m.buildRules().markPlaced(wool);
			VARS.put("tntOk", !m.onPlaceBlock(p("BHTestA"), wool.above(), new ItemStack(Items.TNT, 2)));
		});
		s.check("bedwars: the TNT is lit, not placed", () -> Boolean.TRUE.equals(VARS.get("tntOk")) && bw().tntCount() == 1
				&& !bw().arena().level().getEntitiesOfClass(PrimedTnt.class, bw().arena().bounds(), e -> true).isEmpty());
		s.waitFor("bedwars: the explosion breaks the wool but not the island", 60, () -> {
			BedWarsMatch m = bw();
			BlockPos wool = m.arena().origin().offset(3, 1, 3);
			return m.tntCount() == 0 && m.arena().level().getBlockState(wool).isAir() && !m.arena().level().getBlockState(wool.below()).isAir();
		});
		// Beds, respawns and final kills
		s.check("bedwars: nobody breaks their own bed", () -> {
			BedWarsMatch m = bw();
			ServerPlayer a = p("BHTestA");
			int t = m.teamOf(a.getUUID());
			BlockPos bed = m.bed(t);
			return !m.onBreakBlock(a, bed, m.arena().level().getBlockState(bed)) && m.teams().get(t).bedAlive();
		});
		s.run("bedwars: E1 carries 5 gold and is killed by A while the bed stands", () -> {
			pv("E1").getInventory().add(new ItemStack(Items.GOLD_INGOT, 5));
			VARS.put("goldA", p("BHTestA").getInventory().countItem(Items.GOLD_INGOT));
			hit(var("E1"), "BHTestA", 1000f);
		});
		s.check("bedwars: A took the gold, E1 waits to respawn", () -> p("BHTestA").getInventory().countItem(Items.GOLD_INGOT) == (int) VARS.get("goldA") + 5
				&& pv("E1").gameMode() == GameType.SPECTATOR && bw().isWaitingToRespawn(pv("E1").getUUID()));
		s.waitFor("bedwars: E1 respawns on the island with a wooden sword", 80, () -> pv("E1").gameMode() == GameType.SURVIVAL
				&& pv("E1").getInventory().countItem(Items.WOODEN_SWORD) == 1 && bw().isActive(pv("E1").getUUID()));
		s.check("bedwars: E1 breaks the bed of A's team", () -> {
			BedWarsMatch m = bw();
			int t = m.teamOf(p("BHTestA").getUUID());
			BlockPos bed = m.bed(t);
			m.onBreakBlock(pv("E1"), bed, m.arena().level().getBlockState(bed));
			return !m.teams().get(t).bedAlive() && m.arena().level().getBlockState(bed).isAir() && m.bedsBroken(pv("E1").getUUID()) == 1;
		});
		s.check("bedwars: A breaks the bed of the other team", () -> {
			BedWarsMatch m = bw();
			int t = m.teamOf(pv("E1").getUUID());
			BlockPos bed = m.bed(t);
			m.onBreakBlock(p("BHTestA"), bed, m.arena().level().getBlockState(bed));
			return !m.teams().get(t).bedAlive();
		});
		s.run("bedwars: E2 disconnects", () -> p(var("E2")).leave());
		s.check("bedwars: E2 is out, the game goes on", () -> {
			TeamState t = bw().team(pv("E1").getUUID());
			return t.playing().size() == 1 && bw().phase() == Match.Phase.RUNNING;
		});
		s.run("bedwars: A kills E1, now without a bed", () -> hit(var("E1"), "BHTestA", 1000f));
		s.check("bedwars: a final kill ends the game", () -> bw().finalKills(p("BHTestA").getUUID()) == 1
				&& !bw().alive().contains(pv("E1").getUUID()) && bw().phase() == Match.Phase.ENDING);
		s.check("bedwars: the winners stand on the podium", () -> {
			ServerPlayer a = p("BHTestA");
			return bw().stage() == BedWarsMatch.Stage.PODIUM && a.gameMode() == GameType.ADVENTURE
					&& a.level().getBlockState(a.blockPosition().below()).is(Blocks.GOLD_BLOCK);
		});
		PartyTests.expectWinner(s, GameMode.BED_WARS, "BHTestA");
		s.check("bedwars: the teammate wins too", () -> bw().outcome(pv("T").getUUID()) == net.battlehub.core.Outcome.WIN);
	}

	/** Solo (the vote won 2 to 1): an island and a bed each; the last player in wins. */
	private static void bedWarsSolo(Script s) {
		s.check("bedwars solo: four islands with a bed and one player each", () -> {
			BedWarsMatch m = bw();
			if (!"solo".equals(m.format) || m.teams().size() != 4) return false;
			java.util.Set<Integer> teams = new java.util.HashSet<>();
			for (int t = 0; t < 4; t++) {
				if (m.teams().get(t).members().size() != 1) return false;
				if (!(m.arena().level().getBlockState(m.bed(t)).getBlock() instanceof net.minecraft.world.level.block.BedBlock)) return false;
			}
			for (String n : NAMES) teams.add(m.teamOf(p(n).getUUID()));
			return teams.size() == 4;
		});
		s.run("bedwars solo: B, C and D give up", () -> {
			for (String n : new String[] {"BHTestB", "BHTestC", "BHTestD"}) bw().forfeit(p(n));
		});
		s.check("bedwars solo: the last one in wins", () -> bw().phase() == Match.Phase.ENDING && bw().stage() == BedWarsMatch.Stage.PODIUM);
		PartyTests.expectWinner(s, GameMode.BED_WARS, "BHTestA");
	}

	// ------------------------------------------------------------------ Prop Hunt

	/**
	 * Swings the arm the way the server does for a client's swing packet. LivingEntity.swing takes the hand, the
	 * swing animation and a flag; the animation's default is looked up by reflection to stay independent of it.
	 */
	private static void clientSwing(ServerPlayer p) {
		for (java.lang.reflect.Method m : net.minecraft.world.entity.LivingEntity.class.getDeclaredMethods()) {
			if (!m.getName().equals("swing")) continue;
			Class<?>[] types = m.getParameterTypes();
			Object[] args = new Object[types.length];
			for (int i = 0; i < types.length; i++) {
				if (types[i] == net.minecraft.world.InteractionHand.class) {
					args[i] = net.minecraft.world.InteractionHand.MAIN_HAND;
				} else if (types[i] == boolean.class) {
					args[i] = false;
				} else {
					args[i] = defaultOf(types[i]);
				}
			}
			try {
				m.setAccessible(true);
				m.invoke(p, args);
			} catch (ReflectiveOperationException | RuntimeException e) {
				net.battlehub.BattleHub.LOGGER.info("SELFTEST swing call ended with {}", e.toString());
			}
			return;
		}
	}

	private static Object defaultOf(Class<?> type) {
		for (java.lang.reflect.Field f : type.getDeclaredFields()) {
			if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == type) {
				try {
					f.setAccessible(true);
					return f.get(null);
				} catch (ReflectiveOperationException e) {
					return null;
				}
			}
		}
		return type.isEnum() && type.getEnumConstants().length > 0 ? type.getEnumConstants()[0] : null;
	}

	private static PropHuntMatch ph() {
		return (PropHuntMatch) match();
	}

	private static void propHunt(Script s) {
		s.run("prophunt: find the hunter and the props", () -> {
			PropHuntMatch m = ph();
			int i = 1;
			for (String n : NAMES) {
				if (m.hunters().contains(p(n).getUUID())) VARS.put("H", n);
				else VARS.put("P" + i++, n);
			}
		});
		s.check("prophunt: one hunter, three props, hiding has started", () -> ph().hunters().size() == 1 && ph().props().size() == 3
				&& ph().stage() == PropHuntMatch.Stage.HIDING && VARS.get("H") != null);
		s.check("prophunt: the hiding mixin is active", Visibility::active);
		s.check("prophunt: props are weaker and slower than normal", () -> {
			ServerPlayer p1 = pv("P1");
			var speed = net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
			return p1.getMaxHealth() == ConfigManager.prophunt().propHealth && p1.getMaxHealth() < 20
					&& p1.getAttributeValue(speed) < p1.getAttributeBaseValue(speed);
		});
		s.check("prophunt: the hunter waits blind in survival, the props are hidden blocks", () -> {
			ServerPlayer h = pv("H");
			ServerPlayer p1 = pv("P1");
			return h.gameMode() == GameType.SURVIVAL && h.hasEffect(MobEffects.BLINDNESS)
					&& Visibility.isHidden(p1.getUUID()) && Visibility.hidden(p1, h) && !Visibility.hidden(p1, p1)
					&& ph().disguise(p1.getUUID()) != null;
		});
		s.check("prophunt: P1 becomes a hay bale, not plain stone", () -> {
			PropHuntMatch m = ph();
			ServerPlayer p1 = pv("P1");
			return m.disguise(p1, Blocks.HAY_BLOCK.defaultBlockState()) && m.disguise(p1.getUUID()).state().is(Blocks.HAY_BLOCK)
					&& !m.disguise(p1, Blocks.STONE.defaultBlockState());
		});
		s.check("prophunt: a taunt works once, then waits for its cooldown", () -> ph().taunt(pv("P1")) && !ph().taunt(pv("P1")));
		s.waitFor("prophunt: the hunt starts, the hunter is released", 80, () -> ph().stage() == PropHuntMatch.Stage.HUNTING
				&& !pv("H").hasEffect(MobEffects.BLINDNESS));
		s.run("prophunt: P1 stands still on the ground", () -> pv("P1").setOnGround(true));
		s.waitFor("prophunt: P1 turns solid", 60, () -> ph().disguise(pv("P1").getUUID()).isSolid());
		s.check("prophunt: hitting the solid block hurts P1 and scores", () -> {
			PropHuntMatch m = ph();
			ServerPlayer h = pv("H");
			ServerPlayer p1 = pv("P1");
			int before = m.points(h.getUUID());
			BlockPos at = m.disguise(p1.getUUID()).solidPos();
			InteractionResult r = m.onAttackBlock(h, at);
			return r == InteractionResult.FAIL && p1.getHealth() < p1.getMaxHealth() && m.points(h.getUUID()) > before
					&& !m.disguise(p1.getUUID()).isSolid();
		});
		s.pause(12);
		s.run("prophunt: find a real object in the square", () -> {
			PropHuntMatch m = ph();
			ServerLevel level = m.arena().level();
			BlockPos o = m.arena().origin();
			for (BlockPos pos : BlockPos.betweenClosed(o.offset(-20, 1, -20), o.offset(20, 2, 20))) {
				if (m.isObject(level.getBlockState(pos))) {
					VARS.put("object", pos.immutable());
					break;
				}
			}
			VARS.put("hpH", pv("H").getHealth());
			VARS.put("ptsH", m.points(pv("H").getUUID()));
		});
		s.check("prophunt: hitting a wrong object costs health and points", () -> {
			PropHuntMatch m = ph();
			ServerPlayer h = pv("H");
			BlockPos obj = (BlockPos) VARS.get("object");
			return obj != null && m.onAttackBlock(h, obj) == InteractionResult.FAIL && h.getHealth() < (float) VARS.get("hpH")
					&& m.points(h.getUUID()) < (int) VARS.get("ptsH");
		});
		s.pause(12);
		s.run("prophunt: the hunter swings at the sky", () -> {
			ServerPlayer h = pv("H");
			h.setXRot(-90f);
			VARS.put("hpH", h.getHealth());
			VARS.put("ptsH", ph().points(h.getUUID()));
			// The same call the server makes for a swing packet from the client
			clientSwing(h);
		});
		s.check("prophunt: a swing at thin air costs points but no health", () -> pv("H").getHealth() == (float) VARS.get("hpH")
				&& ph().points(pv("H").getUUID()) < (int) VARS.get("ptsH"));
		s.run("prophunt: P2 leaves a decoy", () -> {
			VARS.put("ptsP2", ph().points(pv("P2").getUUID()));
			VARS.put("decoy", ph().decoy(pv("P2")));
		});
		s.pause(12);
		s.check("prophunt: the hunter falls for the decoy, P2 scores", () -> {
			PropHuntMatch m = ph();
			if (!Boolean.TRUE.equals(VARS.get("decoy")) || m.decoyCount() != 1) return false;
			Disguise d = m.decoyDisguises().get(0);
			return m.onAttackEntity(pv("H"), d.hitbox()) == InteractionResult.FAIL && m.decoyCount() == 0
					&& m.points(pv("P2").getUUID()) > (int) VARS.get("ptsP2");
		});
		s.check("prophunt: hitting a prop's hitbox hurts it", () -> {
			PropHuntMatch m = ph();
			ServerPlayer p2 = pv("P2");
			float before = p2.getHealth();
			m.onAttackEntity(pv("H"), m.disguise(p2.getUUID()).hitbox());
			return p2.getHealth() < before;
		});
		s.run("prophunt: the hunter finds P1", () -> {
			VARS.put("arrows", pv("H").getInventory().countItem(Items.ARROW));
			hit(var("P1"), var("H"), 1000f);
		});
		s.check("prophunt: P1 is found, the hunter got arrows", () -> !ph().props().contains(pv("P1").getUUID())
				&& ph().disguise(pv("P1").getUUID()) == null && !Visibility.isHidden(pv("P1").getUUID())
				&& pv("H").getInventory().countItem(Items.ARROW) > (int) VARS.get("arrows"));
		s.waitFor("prophunt: P1 comes back as a hunter", 60, () -> ph().role(pv("P1").getUUID()) == PropHuntMatch.Role.HUNTER
				&& pv("P1").gameMode() == GameType.SURVIVAL);
		s.check("prophunt: as a hunter P1 has normal health and speed again", () -> {
			var speed = net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
			return pv("P1").getMaxHealth() == 20 && pv("P1").getAttributeValue(speed) == pv("P1").getAttributeBaseValue(speed);
		});
		s.run("prophunt: P3 disconnects", () -> p(var("P3")).leave());
		s.check("prophunt: P3's disguise is cleaned up", () -> {
			UUID id = net.battlehub.debug.TestPlayer.uuid(var("P3"));
			return ph().disguise(id) == null && !Visibility.isHidden(id) && ph().stage() == PropHuntMatch.Stage.HUNTING;
		});
		s.run("prophunt: the hunter finds P2, the last prop", () -> hit(var("P2"), var("H"), 1000f));
		s.check("prophunt: every prop found: the hunters win the round", () -> ph().huntersWonRound()
				&& (ph().stage() == PropHuntMatch.Stage.ROUND_END || ph().phase() != Match.Phase.RUNNING));
		s.waitFor("prophunt: after the last round the game is over", 80, () -> ph().phase() != Match.Phase.RUNNING);
		s.check("prophunt: the hunter has the most points and wins", () -> ph().outcome(pv("H").getUUID()) == net.battlehub.core.Outcome.WIN);
	}
}
