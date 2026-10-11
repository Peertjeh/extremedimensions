package com.extremedimensions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Alle spelregels aan serverkant: altaren (tanks), charge-drain, Dimensional Collapse,
 * poort-blokkade en de penalty's.
 *
 * Een altaar is een Lodestone in de Overworld binnen Config.PORTAL_RANGE blokken van een portaal.
 * De eerste lading bepaalt het type (Nether of End) en verandert het blok in een
 * Ember Lodestone of Void Lodestone. Ga je door het portaal, dan voeden de altaren
 * rond dat portaal je expeditie. Een altaar heeft een vrije weg door de lucht naar het
 * portaal nodig; is die geblokkeerd, dan lekt het charge.
 */
public final class ServerLogic {
	private ServerLogic() {}

	public static final String OVERWORLD = "overworld";
	public static final String NETHER = "the_nether";
	public static final String END = "the_end";

	// ARGB-kleuren voor meldingen
	private static final int RED = 0xFFFF5555;
	private static final int ORANGE = 0xFFFFAA00;
	private static final int GREEN = 0xFF55FF55;
	private static final int AQUA = 0xFF55FFFF;

	private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	private static MinecraftServer server;
	private static int saveCounter = 0;
	private static int altarTick = 0;
	private static int effectTick = 0;

	// ------------------------------------------------------------------ registratie

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			DataStore.load(s);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> DataStore.save());
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			DataStore.unload();
			server = null;
		});
		ServerTickEvents.END_SERVER_TICK.register(ServerLogic::tick);

		// /tether overworld_penalties [on|off]  (alleen ops / cheats aan)
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(
				Commands.literal("tether")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("overworld_penalties")
								.executes(c -> showOverworldPenalties(c.getSource()))
								.then(Commands.literal("on").executes(c -> setOverworldPenalties(c.getSource(), true)))
								.then(Commands.literal("off").executes(c -> setOverworldPenalties(c.getSource(), false))))));

		ServerPlayerEvents.JOIN.register(ServerLogic::onJoin);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> DataStore.save());
		ServerPlayerEvents.AFTER_RESPAWN.register(ServerLogic::onRespawn);

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof ServerPlayer p)) {
				if (source.getEntity() instanceof ServerPlayer killer) {
					onMobKilled(killer, entity);
				}
				return;
			}
			PlayerData d = DataStore.get(p.getUUID());
			String dim = entity.level().dimension().identifier().getPath();
			// Gewoon doodgaan in Nether/End (geen Collapse): expeditie voorbij, altaren leeg en een penalty.
			// In de Overworld (nog) geen straf.
			if (OVERWORLD.equals(dim) && DataStore.overworldPenalties() && !d.collapsing) {
				d.endedIn = "Overworld";
				owePenalty(d, true);
				DataStore.save();
				return;
			}
			if (isCharged(dim) && !d.collapsing) {
				if (d.inRun) {
					for (Altar a : linkedAltars(d)) {
						a.charge = 0;
					}
					d.respawnNotice = "The anchors feeding your expedition ran dry.";
				}
				d.inRun = false;
				d.linked.clear();
				clearRope(p, d, false);
				d.endedIn = dimName(dim);
				owePenalty(d, false);
				unlock(p, d, JournalEntry.ENDED);
				DataStore.save();
			}
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			if (!isAltarBlock(level.getBlockState(pos))) {
				return InteractionResult.PASS;
			}
			if (!(player instanceof ServerPlayer sp)) {
				// Client: niet het blok in je hand tegen het altaar plaatsen, de server handelt het af.
				ItemStack held = player.getMainHandItem();
				boolean status = held.isEmpty() && player.isShiftKeyDown();
				return status || isFuel(held.getItem()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			return useAltar(sp, pos);
		});

		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (player instanceof ServerPlayer sp && player.getItemInHand(hand).is(ModItems.TETHER_JOURNAL)) {
				PlayerData d = DataStore.get(sp.getUUID());
				ServerPlayNetworking.send(sp, new ModPackets.OpenJournalPayload(String.join(",", d.journal)));
				soundSelf(sp, "minecraft:item.book.page_turn", 1.0f, 0.9f);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (OVERWORLD.equals(level.dimension().identifier().getPath())) {
				DataStore.altars().remove(Altar.key(pos.getX(), pos.getY(), pos.getZ()));
			}
		});
	}

	// ------------------------------------------------------------------ commando

	private static int showOverworldPenalties(CommandSourceStack src) {
		boolean on = DataStore.overworldPenalties();
		src.sendSuccess(() -> Component.literal("Overworld penalties are " + (on ? "ON" : "OFF")
				+ " in this world. Use /tether overworld_penalties on|off to change."), false);
		return on ? 1 : 0;
	}

	private static int setOverworldPenalties(CommandSourceStack src, boolean on) {
		DataStore.setOverworldPenalties(on);
		src.sendSuccess(() -> Component.literal(on
				? "Overworld penalties ON: dying in the Overworld now costs an Overworld penalty."
				: "Overworld penalties OFF: dying in the Overworld is free again."), true);
		if (server != null) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				applyPenaltyEffects(p, DataStore.get(p.getUUID()));
			}
		}
		return 1;
	}

	// ------------------------------------------------------------------ helpers

	static String dimOf(ServerPlayer p) {
		return p.level().dimension().identifier().getPath();
	}

	static boolean isCharged(String dim) {
		return NETHER.equals(dim) || END.equals(dim);
	}

	static String dimName(String dim) {
		return NETHER.equals(dim) ? "Nether" : "End";
	}

	static boolean isAltarBlock(BlockState s) {
		return s.is(Blocks.LODESTONE) || s.is(ModBlocks.EMBER_LODESTONE) || s.is(ModBlocks.VOID_LODESTONE);
	}

	/** Het Tether-item (Iron Chain + 2 String): daarmee maak je je fysieke tether vast aan een anker. */
	static boolean isTetherItem(Item item) {
		return item == ModItems.TETHER;
	}

	static boolean isFuel(Item item) {
		return isTetherItem(item) || item == ModItems.EMBER_TETHER || item == ModItems.VOID_TETHER || item == ModItems.TETHER_FRAGMENT
				|| ChargeItems.NETHER.containsKey(item) || ChargeItems.END.containsKey(item);
	}

	static boolean isPortalFor(BlockState s, String type) {
		if (NETHER.equals(type)) {
			return s.is(Blocks.NETHER_PORTAL);
		}
		return s.is(Blocks.END_PORTAL) || s.is(Blocks.END_PORTAL_FRAME);
	}

	/** Dichtstbijzijnde portaalblok binnen Config.PORTAL_RANGE, of null. */
	static BlockPos findPortal(ServerLevel level, BlockPos center, String type) {
		int r = Config.PORTAL_RANGE;
		BlockPos best = null;
		int bestDist = Integer.MAX_VALUE;
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					int dist = dx * dx + dy * dy + dz * dz;
					if (dist >= bestDist) {
						continue;
					}
					BlockPos q = center.offset(dx, dy, dz);
					if (isPortalFor(level.getBlockState(q), type)) {
						best = q;
						bestDist = dist;
					}
				}
			}
		}
		return best;
	}

	/** Kunnen deeltjes door dit blok heen? Lucht en blokken zonder botsvorm (fakkels, gras), geen vloeistof. */
	static boolean passable(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (s.isAir()) {
			return true;
		}
		return !s.liquid() && s.getCollisionShape(level, pos).isEmpty();
	}

	/**
	 * Zoekt een vrije weg door de lucht van het altaar naar het midden van zijn portaal.
	 * Geeft de punten van de weg terug, of een lege lijst als er geen portaal of geen vrije weg is.
	 */
	static List<double[]> findPath(ServerLevel level, Altar a) {
		List<double[]> out = new ArrayList<>();
		BlockPos start = new BlockPos(a.x, a.y, a.z);
		BlockPos portal = findPortal(level, start, a.type);
		if (portal == null) {
			return out;
		}

		// Alle portaalblokken die bij dit portaal horen (ook schuin, voor de ring van End Portal Frames)
		Set<Long> portalSet = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		portalSet.add(portal.asLong());
		queue.add(portal);
		double sx = 0, sy = 0, sz = 0;
		int count = 0;
		while (!queue.isEmpty() && count < 160) {
			BlockPos c = queue.poll();
			sx += c.getX();
			sy += c.getY();
			sz += c.getZ();
			count++;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos n = c.offset(dx, dy, dz);
						if (!portalSet.contains(n.asLong()) && isPortalFor(level.getBlockState(n), a.type)) {
							portalSet.add(n.asLong());
							queue.add(n);
						}
					}
				}
			}
		}
		double cx = sx / count + 0.5;
		double cy = sy / count + 0.5;
		double cz = sz / count + 0.5;

		// Breadth-first search door vrije blokken, tot naast (of in) het portaal
		int limit = Config.PORTAL_RANGE + 3;
		Map<Long, Long> parent = new HashMap<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		parent.put(start.asLong(), Long.MIN_VALUE);
		open.add(start);
		BlockPos goal = null;
		while (!open.isEmpty() && goal == null) {
			BlockPos c = open.poll();
			for (int[] dir : DIRS) {
				BlockPos n = c.offset(dir[0], dir[1], dir[2]);
				long key = n.asLong();
				if (parent.containsKey(key)
						|| Math.abs(n.getX() - a.x) > limit || Math.abs(n.getY() - a.y) > limit || Math.abs(n.getZ() - a.z) > limit) {
					continue;
				}
				boolean inPortal = portalSet.contains(key);
				if (!inPortal && !passable(level, n)) {
					continue;
				}
				parent.put(key, c.asLong());
				if (inPortal || touchesPortal(n, portalSet)) {
					goal = n;
					break;
				}
				open.add(n);
			}
		}
		if (goal == null) {
			return out;
		}

		List<double[]> rev = new ArrayList<>();
		long k = goal.asLong();
		while (k != Long.MIN_VALUE) {
			BlockPos b = BlockPos.of(k);
			rev.add(new double[] {b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5});
			k = parent.get(k);
		}
		for (int i = rev.size() - 1; i >= 0; i--) {
			out.add(rev.get(i));
		}
		out.add(new double[] {cx, cy, cz});
		return out;
	}

	private static boolean touchesPortal(BlockPos p, Set<Long> portalSet) {
		for (int[] dir : DIRS) {
			if (portalSet.contains(p.offset(dir[0], dir[1], dir[2]).asLong())) {
				return true;
			}
		}
		return false;
	}

	static List<Altar> linkedAltars(PlayerData d) {
		List<Altar> out = new ArrayList<>();
		for (String k : d.linked) {
			Altar a = DataStore.altars().get(k);
			if (a != null) {
				out.add(a);
			}
		}
		return out;
	}

	static double linkedCharge(PlayerData d) {
		double sum = 0;
		for (Altar a : linkedAltars(d)) {
			sum += a.charge;
		}
		return sum;
	}

	/** Haalt amount charge uit de altaren van de expeditie; geeft de rest terug. */
	static double drainLinked(PlayerData d, double amount) {
		double left = 0;
		for (Altar a : linkedAltars(d)) {
			if (amount > 0 && a.charge > 0) {
				double take = Math.min(a.charge, amount);
				a.charge -= take;
				amount -= take;
			}
			left += a.charge;
		}
		return left;
	}

	/** Totale charge van geladen altaren van dit type rond de speler; -1 als er geen in de buurt is. */
	static int nearCharge(ServerPlayer p, String type) {
		double r2 = Config.HUD_RANGE * Config.HUD_RANGE;
		double sum = -1;
		for (Altar a : DataStore.altars().values()) {
			if (type.equals(a.type) && a.distSq(p.getX(), p.getY(), p.getZ()) <= r2) {
				sum = Math.max(0, sum) + a.charge;
			}
		}
		return sum < 0 ? -1 : (int) Math.ceil(sum);
	}

	static void run(String command) {
		if (server == null) {
			return;
		}
		try {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
		} catch (Exception e) {
			ExtremeDimensions.LOGGER.warn("Command failed: {}", command, e);
		}
	}

	static String fmt(double seconds) {
		int s = (int) Math.ceil(seconds);
		return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}

	// ------------------------------------------------------------------ animaties (server-kant)

	static void fx(ServerPlayer p, int type, int value) {
		ServerPlayNetworking.send(p, new ModPackets.FxPayload(type, value));
	}

	private static void at(ServerPlayer p, String cmd) {
		run("execute at " + p.getStringUUID() + " run " + cmd);
	}

	static void particles(ServerPlayer p, String name, double dy, double spread, double speed, int count) {
		at(p, String.format(Locale.ROOT, "particle %s ~ ~%.2f ~ %.2f %.2f %.2f %.3f %d normal", name, dy, spread, spread, spread, speed, count));
	}

	/** Deeltjes op een vaste plek in de Overworld. Bij count 0 is (dx, dy, dz) de bewegingsrichting. */
	static void particleAt(String name, double x, double y, double z, double dx, double dy, double dz, double speed, int count) {
		run(String.format(Locale.ROOT, "execute in minecraft:overworld run particle %s %.2f %.2f %.2f %.3f %.3f %.3f %.3f %d normal",
				name, x, y, z, dx, dy, dz, speed, count));
	}

	static void sound(ServerPlayer p, String id, float vol, float pitch) {
		at(p, String.format(Locale.ROOT, "playsound %s master @a ~ ~ ~ %.2f %.2f", id, vol, pitch));
	}

	/** Alleen voor deze speler hoorbaar. */
	static void soundSelf(ServerPlayer p, String id, float vol, float pitch) {
		at(p, String.format(Locale.ROOT, "playsound %s master %s ~ ~ ~ %.2f %.2f", id, p.getStringUUID(), vol, pitch));
	}

	static void notice(ServerPlayer p, String text, int color) {
		ServerPlayNetworking.send(p, new ModPackets.NoticePayload(text, color));
	}

	/** Grote tekst midden in beeld (vanilla /title). */
	static void title(ServerPlayer p, String title, String color, String subtitle) {
		String u = p.getStringUUID();
		run("title " + u + " times 10 70 25");
		run("title " + u + " subtitle {\"text\":\"" + subtitle + "\",\"color\":\"gray\"}");
		run("title " + u + " title {\"text\":\"" + title + "\",\"color\":\"" + color + "\",\"bold\":true}");
	}

	static void sendState(ServerPlayer p, PlayerData d) {
		int dimCode = NETHER.equals(d.dim) ? 1 : END.equals(d.dim) ? 2 : 0;
		boolean overworld = OVERWORLD.equals(d.dim);
		// id:stacks:seconden (seconden tot de eerstvolgende stack verloopt, -1 = volgende expeditie)
		StringBuilder pen = new StringBuilder();
		for (Map.Entry<String, List<Double>> e : d.penaltyTime.entrySet()) {
			if (e.getValue().isEmpty()) {
				continue;
			}
			double minSecs = Double.MAX_VALUE;
			for (double r : e.getValue()) {
				minSecs = Math.min(minSecs, r);
			}
			int min = (int) Math.ceil(minSecs);
			if (pen.length() > 0) {
				pen.append(';');
			}
			pen.append(e.getKey()).append(':').append(e.getValue().size()).append(':').append(min);
		}
		if (d.xpDebt > 0) {
			if (pen.length() > 0) {
				pen.append(';');
			}
			pen.append(Penalty.TOLL.id).append(':').append(d.xpDebt).append(":-2");
		}
		if (d.leakStacks > 0) {
			if (pen.length() > 0) {
				pen.append(';');
			}
			pen.append(Penalty.LEAKY_TANK.id).append(':').append(d.leakStacks).append(":-1");
		}
		ServerPlayNetworking.send(p, new ModPackets.StatePayload(
				overworld ? nearCharge(p, NETHER) : -1, overworld ? nearCharge(p, END) : -1,
				d.inRun ? (int) Math.ceil(linkedCharge(d)) : 0, (int) Math.ceil(d.runMax), dimCode,
				(int) Math.round(d.currentDrain * 100), d.expeditions, d.collapses,
				(int) d.longestRun, (int) d.runTime, pen.toString(), d.penaltyClock, tetherDistance(p, d)));
	}

	static void sendOffer(ServerPlayer p, PlayerData d) {
		if (!d.pendingOffer.isEmpty()) {
			ServerPlayNetworking.send(p, new ModPackets.OfferPayload(String.join(",", d.pendingOffer)));
		}
	}

	/** Ontgrendelt een pagina in het Tether Journal (een keer per speler). */
	static void unlock(ServerPlayer p, PlayerData d, JournalEntry e) {
		if (!d.journal.add(e.id)) {
			return;
		}
		run("title " + p.getStringUUID() + " actionbar {\"text\":\"New journal page: " + e.title + "\",\"color\":\"light_purple\",\"italic\":true}");
		soundSelf(p, "minecraft:item.book.page_turn", 1.0f, 0.7f);
		soundSelf(p, "minecraft:block.sculk_shrieker.shriek", 0.15f, 1.6f);
	}

	/** Een speler doodde een mob: kans op een Tether Fragment in Nether/End, en lore voor Endermen. */
	private static void onMobKilled(ServerPlayer killer, net.minecraft.world.entity.LivingEntity mob) {
		PlayerData d = DataStore.get(killer.getUUID());
		String type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
		if ("enderman".equals(type)) {
			unlock(killer, d, JournalEntry.UNANCHORED);
		}
		String dim = mob.level().dimension().identifier().getPath();
		if (!isCharged(dim) || !(mob instanceof Enemy)) {
			return;
		}
		boolean elite = Set.of("wither_skeleton", "enderman", "blaze", "ghast", "shulker").contains(type);
		double chance = elite ? Config.FRAGMENT_CHANCE_ELITE : Config.FRAGMENT_CHANCE;
		if (ThreadLocalRandom.current().nextDouble() >= chance) {
			return;
		}
		String where = mob.level().dimension().identifier().toString();
		String pos = String.format(Locale.ROOT, "%.2f %.2f %.2f", mob.getX(), mob.getY() + 0.5, mob.getZ());
		run("execute in " + where + " run summon minecraft:item " + pos + " {Item:{id:\"extremedimensions:tether_fragment\",count:1}}");
		run("execute in " + where + " run particle minecraft:soul " + pos + " 0.3 0.4 0.3 0.04 25 normal");
		run("execute in " + where + " run playsound minecraft:particle.soul_escape master @a " + pos + " 1.5 0.7");
		unlock(killer, d, JournalEntry.FRAGMENT);
	}

	/** De Leeching: onder Config.LEECH_SECONDS zuigen de Starved zichtbaar zielen uit je. */
	private static void leech(ServerPlayer p, double secsLeft) {
		double k = 1.0 - secsLeft / Config.LEECH_SECONDS; // 0 -> 1
		int n = 2 + (int) Math.round(k * 10);
		particles(p, "minecraft:sculk_soul", 1.0, 0.5, 0.03, n);
		if (k > 0.5) {
			particles(p, "minecraft:soul", 0.8, 0.8, 0.02, n / 2);
		}
		if (ThreadLocalRandom.current().nextDouble() < 0.15 + k * 0.35) {
			soundSelf(p, "minecraft:particle.soul_escape", 0.6f + (float) k * 0.6f, 0.6f + (float) k * 0.3f);
		}
	}

	/** De speler moet (nog) een penalty kiezen. */
	static void owePenalty(PlayerData d, boolean overworld) {
		d.pendingPenalties++;
		d.owed.add(overworld ? "overworld" : "beyond");
		if (d.pendingOffer.isEmpty()) {
			d.pendingOffer = Penalty.rollOffer("overworld".equals(d.owed.get(0)));
		}
	}

	// ------------------------------------------------------------------ tick

	private static void tick(MinecraftServer s) {
		if (++saveCounter >= 6000) { // elke 5 minuten
			saveCounter = 0;
			DataStore.save();
		}
		boolean refreshEffects = ++effectTick >= 100; // elke 5 seconden
		if (refreshEffects) {
			effectTick = 0;
		}

		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			PlayerData d = DataStore.get(p.getUUID());
			String dim = dimOf(p);
			boolean forceSync = false;

			if (d.grace > 0) {
				d.grace--;
			}
			if (!d.joinDone && ++d.ticksOnline >= Config.JOIN_DELAY_TICKS) {
				d.joinDone = true;
				joinActions(p, d);
			}
			// XP-schuld (Toll/Tithe): elk level dat je haalt neemt de Tether, tot de schuld betaald is
			if (d.xpDebt > 0 && p.experienceLevel > 0 && d.tickCounter % 20 == 0) {
				int take = Math.min(d.xpDebt, p.experienceLevel);
				p.giveExperienceLevels(-take);
				d.xpDebt -= take;
				notice(p, "The Tether takes " + take + (take == 1 ? " level" : " levels")
						+ (d.xpDebt > 0 ? " (" + d.xpDebt + " still owed)" : ". Your debt is paid."), 0xFFAA88FF);
				particles(p, "minecraft:sculk_soul", 1.0, 0.4, 0.03, 8);
				soundSelf(p, "minecraft:particle.soul_escape", 1.0f, 0.8f);
				forceSync = true;
			}
			if (d.offerDelay > 0 && --d.offerDelay == 0) {
				d.offerDelay = -1;
				sendOffer(p, d);
			}

			// 1) dimensiewissel afhandelen
			if (!dim.equals(d.dim)) {
				String from = d.dim;
				d.dim = dim;
				forceSync = true;
				if (isCharged(dim) && !d.collapsing) {
					startRun(p, d, dim);
				} else if (OVERWORLD.equals(dim) && isCharged(from) && d.inRun && !d.collapsing) {
					completeRun(p, d);
				}
				applyPenaltyEffects(p, d);
			}

			// 2) laatste Overworld-positie onthouden (poort-blokkade en welke altaren meedoen)
			if (OVERWORLD.equals(dim) && !d.collapsing) {
				d.lastX = p.getX();
				d.lastY = p.getY();
				d.lastZ = p.getZ();
				d.hasLast = true;
			}

			// 3) charge laten leeglopen in Nether/End
			if (isCharged(dim) && d.inRun && !d.collapsing) {
				d.currentDrain = d.drainMultiplier() * distanceFactor(p, d);
				double cur = drainLinked(d, d.currentDrain / 20.0);
				d.runTime += 1.0 / 20.0;
				if (cur <= 0) {
					collapse(p, d);
					forceSync = true;
				} else {
					int secsLeft = (int) Math.ceil(cur);
					warn(p, d, secsLeft);
					if (cur < Config.LEECH_SECONDS && d.tickCounter % 10 == 0) {
						leech(p, cur);
					}
					if (secsLeft <= 10 && secsLeft != d.lastBeat) {
						d.lastBeat = secsLeft;
						sound(p, "minecraft:entity.warden.heartbeat", 1.3f, 1.0f + (10 - secsLeft) * 0.04f);
					}
				}
			} else if (d.collapsing && isCharged(dim)) {
				// Noodrem: als de kill niet gelukt is, probeer elke seconde opnieuw.
				if (++d.collapseRetry % 20 == 0) {
					run("kill " + p.getStringUUID());
				}
			}

			if (!(isCharged(dim) && d.inRun)) {
				d.currentDrain = d.drainMultiplier();
			}

			// 3b) de fysieke tether volgt je over de grond
			if (d.tetherAnchor != null && !d.collapsing && OVERWORLD.equals(dim) && !d.inRun) {
				Altar anchor = DataStore.altars().get(d.tetherAnchor);
				if (anchor == null || tetherTooFar(p, anchor)) {
					// De tether breekt: de ketting verdwijnt, de Tether valt terug in je handen
					particles(p, "minecraft:crit", 0.6, 0.3, 0.3, 14);
					clearRope(p, d, true);
					notice(p, anchor == null ? "Your anchor is gone. The tether falls slack in your hands."
							: "Your tether snapped. Stay close to the anchor until you step into the wound.", ORANGE);
					sound(p, "minecraft:entity.leash_knot.break", 1.0f, 0.9f);
					sound(p, "minecraft:block.chain.break", 1.0f, 0.7f);
					forceSync = true;
				}
			}
			if (d.tetherAnchor != null && !d.collapsing && (d.inRun || OVERWORLD.equals(dim))) {
				layRope(p, d);
			}

			// 4) penalty-klok: loopt alleen tijdens een expeditie in Nether/End en als je niet AFK bent
			trackActivity(p, d);
			if (tickPenalties(p, d, isCharged(dim) && d.inRun && !d.collapsing, OVERWORLD.equals(dim) && !d.collapsing)) {
				forceSync = true;
			}

			// 5) blijvende penalty-effecten bijhouden (en af en toe kijken of je in de Deep Dark staat)
			if (refreshEffects) {
				refreshEffects(p, d);
				if (p.level().getBiome(p.blockPosition()).is(Biomes.DEEP_DARK)) {
					unlock(p, d, JournalEntry.WEAVERS);
				}
				// Bij een portaal in de Overworld zonder anker? Dan leert het journal je over ankers.
				if (OVERWORLD.equals(dim) && !d.journal.contains(JournalEntry.ANCHOR.id)) {
					ServerLevel lvl = (ServerLevel) p.level();
					if (findPortal(lvl, p.blockPosition(), NETHER) != null || findPortal(lvl, p.blockPosition(), END) != null) {
						unlock(p, d, JournalEntry.ANCHOR);
						notice(p, "The wound is hungry. It wants an anchor.", 0xFFAA88FF);
					}
				}
			}

			// 6) status naar de client
			if (forceSync || ++d.tickCounter % Config.SYNC_INTERVAL_TICKS == 0) {
				sendState(p, d);
			}
		}

		if (s.getTickCount() % 200 == 0) {
			sweepRopes(s);
		}

		if (++altarTick >= 10) {
			altarTick = 0;
			tickAltars(s);
		}
	}

	/** Houdt bij wanneer de speler voor het laatst rondkeek en een stukje liep. */
	private static void trackActivity(ServerPlayer p, PlayerData d) {
		d.ticksSinceLook++;
		d.ticksSinceMove++;
		float yaw = p.getYRot();
		float pitch = p.getXRot();
		if (Math.abs(yaw - d.lastYaw) > 2.0f || Math.abs(pitch - d.lastPitch) > 2.0f) {
			d.ticksSinceLook = 0;
			d.lastYaw = yaw;
			d.lastPitch = pitch;
		}
		double dx = p.getX() - d.anchorX;
		double dz = p.getZ() - d.anchorZ;
		if (dx * dx + dz * dz >= Config.ACTIVE_MOVE * Config.ACTIVE_MOVE) {
			d.ticksSinceMove = 0;
			d.anchorX = p.getX();
			d.anchorZ = p.getZ();
		}
	}

	/**
	 * Laat de penalty-stacks aftellen als de speler actief op expeditie is.
	 * Rondkijken alleen (muis-macro) of alleen bewegen (waterstroom) is niet genoeg: het moet allebei.
	 * Geeft true terug als er iets veranderde dat de client meteen moet weten.
	 */
	private static boolean tickPenalties(ServerPlayer p, PlayerData d, boolean onExpedition, boolean inOverworld) {
		int before = d.penaltyClock;
		// Welke set telt hier af? Nether/End-penalty's op expeditie, Overworld-penalty's in de Overworld.
		boolean any = false;
		for (String id : d.penaltyTime.keySet()) {
			Penalty pen = Penalty.byId(id);
			boolean ow = pen != null && pen.overworld;
			if ((ow && inOverworld) || (!ow && onExpedition)) {
				any = true;
				break;
			}
		}
		if (!any) {
			d.penaltyClock = 0;
			return before != d.penaltyClock;
		}
		boolean active = d.ticksSinceLook < Config.IDLE_TICKS && d.ticksSinceMove < Config.IDLE_TICKS;
		d.penaltyClock = active ? 1 : 2;
		if (!active) {
			if (before == 1) {
				notice(p, "Penalties paused: you are idle.", ORANGE);
			}
			return before != d.penaltyClock;
		}

		List<String> worn = new ArrayList<>();
		Iterator<Map.Entry<String, List<Double>>> it = d.penaltyTime.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, List<Double>> e = it.next();
			Penalty kind = Penalty.byId(e.getKey());
			boolean owKind = kind != null && kind.overworld;
			if (owKind ? !inOverworld : !onExpedition) {
				continue;
			}
			List<Double> left = new ArrayList<>();
			for (double secs : e.getValue()) {
				double rest = secs - 1.0 / 20.0;
				if (rest > 0) {
					left.add(rest);
				} else {
					worn.add(e.getKey());
				}
			}
			e.setValue(left);
			if (left.isEmpty()) {
				it.remove();
			}
		}
		if (worn.isEmpty()) {
			return before != d.penaltyClock;
		}
		applyPenaltyEffects(p, d);
		for (String id : worn) {
			Penalty pen = Penalty.byId(id);
			String name = pen != null ? pen.title : id;
			int still = pen != null ? d.stacks(pen) : 0;
			notice(p, still > 0 ? name + " weakened (x" + still + " left)" : name + " wore off!", GREEN);
		}
		sound(p, "minecraft:block.amethyst_block.chime", 1.0f, 1.2f);
		particles(p, "minecraft:happy_villager", 1.0, 0.5, 0.1, 15);
		return true;
	}

	private static void startRun(ServerPlayer p, PlayerData d, String dim) {
		// Werkende altaren van dit type rond het portaal waar je doorheen ging
		d.linked.clear();
		if (d.hasLast) {
			double r2 = Config.LINK_RANGE * Config.LINK_RANGE;
			for (Altar a : DataStore.altars().values()) {
				if (dim.equals(a.type) && a.charge > 0 && !a.blocked && a.distSq(d.lastX, d.lastY, d.lastZ) <= r2) {
					d.linked.add(a.key());
				}
			}
		}
		double total = linkedCharge(d);
		if (total <= 0) {
			d.linked.clear();
			bounce(p, d, dim);
			return;
		}
		if (d.tetherAnchor == null || !d.linked.contains(d.tetherAnchor)) {
			d.linked.clear();
			d.inRun = false;
			d.grace = Config.BOUNCE_GRACE_TICKS;
			notice(p, "You are not tethered. Bind a Tether (iron chain + 2 string) to an anchor at this wound.", ORANGE);
			unlock(p, d, JournalEntry.CORD);
			fx(p, Fx.BOUNCE, 0);
			sound(p, "minecraft:entity.villager.no", 1.0f, 0.7f);
			if (d.hasLast) {
				run(String.format(Locale.ROOT, "execute in minecraft:overworld run tp %s %.2f %.2f %.2f", p.getStringUUID(), d.lastX, d.lastY, d.lastZ));
			}
			return;
		}
		d.arrivalX = p.getX();
		d.arrivalZ = p.getZ();
		d.hasRopeEnd = false;
		if (d.leakStacks > 0) {
			double keep = Math.pow(1.0 - Config.LEAK_FRACTION, d.leakStacks);
			for (Altar a : linkedAltars(d)) {
				a.charge *= keep;
			}
			total = linkedCharge(d);
			notice(p, "Leaky Tank: your anchors lost " + Math.round((1 - keep) * 100) + "% of their charge.", ORANGE);
			d.leakStacks = 0;
		}
		d.inRun = true;
		d.runTime = 0;
		d.runMax = total;
		d.lastWarn = Integer.MAX_VALUE;
		d.lastBeat = Integer.MAX_VALUE;
		fx(p, NETHER.equals(dim) ? Fx.START_NETHER : Fx.START_END, 0);
		unlock(p, d, NETHER.equals(dim) ? JournalEntry.EMBER_DEEP : JournalEntry.PALE_VOID);
		sound(p, "extremedimensions:portal_beyond", 1.0f, NETHER.equals(dim) ? 0.9f : 1.1f);
		if (NETHER.equals(dim)) {
			particles(p, "minecraft:flame", 1.0, 0.6, 0.06, 60);
			particles(p, "minecraft:soul_fire_flame", 1.0, 1.2, 0.02, 30);
		} else {
			particles(p, "minecraft:reverse_portal", 1.0, 0.6, 0.2, 80);
			particles(p, "minecraft:end_rod", 1.0, 1.2, 0.03, 20);
		}
	}

	private static void warn(ServerPlayer p, PlayerData d, int secs) {
		for (int t : new int[] {60, 30, 10}) {
			if (secs <= t && d.lastWarn > t) {
				notice(p, t + " seconds of charge left!", RED);
				sound(p, "minecraft:block.bell.use", 0.7f, 0.6f);
				particles(p, "minecraft:smoke", 1.0, 0.5, 0.02, 25);
				d.lastWarn = secs;
				return;
			}
		}
	}

	// ------------------------------------------------------------------ altaren: onderhoud, lekken en deeltjes

	private static void tickAltars(MinecraftServer s) {
		// Geblokkeerde altaren lekken charge, ook als er niemand in de buurt is
		double leak = Config.BLOCKED_LEAK_PER_SECOND * 0.5; // elke 10 ticks
		for (Altar a : DataStore.altars().values()) {
			if (a.blocked && a.charge > 0) {
				a.charge = Math.max(0, a.charge - leak);
			}
		}

		ServerLevel ow = s.overworld();
		List<ServerPlayer> watchers = new ArrayList<>();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (OVERWORLD.equals(dimOf(p))) {
				watchers.add(p);
			}
		}
		if (watchers.isEmpty()) {
			return;
		}
		double r2 = Config.PARTICLE_RANGE * Config.PARTICLE_RANGE;
		Iterator<Altar> it = DataStore.altars().values().iterator();
		while (it.hasNext()) {
			Altar a = it.next();
			boolean watched = false;
			for (ServerPlayer p : watchers) {
				if (a.distSq(p.getX(), p.getY(), p.getZ()) <= r2) {
					watched = true;
					break;
				}
			}
			if (!watched) {
				continue;
			}
			BlockPos pos = new BlockPos(a.x, a.y, a.z);
			if (!ow.isLoaded(pos)) {
				continue;
			}
			// Weggeblazen of anders verdwenen? Dan is het altaar weg.
			BlockState state = ow.getBlockState(pos);
			if (!isAltarBlock(state)) {
				it.remove();
				continue;
			}
			// Leeg: weer een gewone Lodestone, klaar om opnieuw aan een dimensie gebonden te worden.
			if (a.charge <= 0) {
				if (!state.is(Blocks.LODESTONE)) {
					ow.setBlock(pos, Blocks.LODESTONE.defaultBlockState(), 3, 512);
				}
				particleAt("minecraft:large_smoke", a.x + 0.5, a.y + 1.0, a.z + 0.5, 0.3, 0.2, 0.3, 0.02, 12);
				particleAt("minecraft:ash", a.x + 0.5, a.y + 1.0, a.z + 0.5, 0.4, 0.4, 0.4, 0.0, 20);
				run(String.format(Locale.ROOT, "execute in minecraft:overworld run playsound minecraft:block.respawn_anchor.deplete block @a %d %d %d 1 0.8",
						a.x, a.y, a.z));
				it.remove();
				continue;
			}
			if (--a.portalCheck <= 0) {
				a.portalCheck = 4; // elke 2 seconden
				a.path = findPath(ow, a);
				boolean nowBlocked = a.path.isEmpty();
				if (nowBlocked && !a.blocked && a.checked && a.charge > 0) {
					warnBlocked(watchers, a);
				}
				a.blocked = nowBlocked;
				a.checked = true;
			}
			altarParticles(a);
		}
	}

	private static void warnBlocked(List<ServerPlayer> players, Altar a) {
		double r2 = (Config.HUD_RANGE * 2) * (Config.HUD_RANGE * 2);
		for (ServerPlayer p : players) {
			if (a.distSq(p.getX(), p.getY(), p.getZ()) <= r2) {
				notice(p, "A " + dimName(a.type) + " anchor lost its path to the portal. It is leaking charge!", RED);
			}
		}
	}

	private static void altarParticles(Altar a) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double fill = Math.min(1.0, a.charge / Config.MAX_CHARGE);
		double cx = a.x + 0.5;
		double cy = a.y + 0.5;
		double cz = a.z + 0.5;
		boolean nether = a.isNether();

		if (a.charge <= 0) {
			// Leeg altaar: af en toe een rookpluimpje
			if (rnd.nextInt(4) == 0) {
				particleAt("minecraft:smoke", cx, cy + 0.6, cz, 0.2, 0.1, 0.2, 0.01, 1);
			}
			return;
		}

		// Gloed rond het blok: hoe voller, hoe meer
		int n = 1 + (int) Math.round(fill * 9);
		if (nether) {
			particleAt("minecraft:flame", cx, cy + 0.2, cz, 0.45, 0.45, 0.45, 0.01, n);
			if (fill > 0.25) {
				particleAt("minecraft:small_flame", cx, cy + 0.7, cz, 0.3, 0.2, 0.3, 0.02, n);
			}
			if (fill > 0.5 && rnd.nextInt(3) == 0) {
				particleAt("minecraft:lava", cx, cy + 0.6, cz, 0.2, 0.1, 0.2, 0.0, 1);
			}
			if (fill > 0.8) {
				particleAt("minecraft:soul_fire_flame", cx, cy + 0.3, cz, 0.5, 0.5, 0.5, 0.01, 2);
			}
		} else {
			particleAt("minecraft:portal", cx, cy + 0.2, cz, 0.5, 0.5, 0.5, 0.6, n * 2);
			if (fill > 0.25) {
				particleAt("minecraft:reverse_portal", cx, cy + 0.7, cz, 0.3, 0.3, 0.3, 0.02, n);
			}
			if (fill > 0.5 && rnd.nextInt(2) == 0) {
				particleAt("minecraft:end_rod", cx, cy + 0.8, cz, 0.3, 0.3, 0.3, 0.02, 1);
			}
			if (fill > 0.8) {
				particleAt("minecraft:dragon_breath", cx, cy + 0.4, cz, 0.4, 0.3, 0.4, 0.01, 2);
			}
		}

		if (a.blocked) {
			// Geen weg naar het portaal: geen stroom, wel rook omdat het altaar leegloopt
			particleAt("minecraft:large_smoke", cx, cy + 0.7, cz, 0.15, 0.1, 0.15, 0.01, 1 + (int) Math.round(fill * 2));
			return;
		}

		// Stroom langs de vrije weg naar het midden van het portaal
		List<double[]> path = a.path;
		if (path.size() < 2) {
			return;
		}
		int streams = 2 + (int) Math.round(fill * 6);
		for (int i = 0; i < streams; i++) {
			int seg = rnd.nextInt(path.size() - 1);
			double[] p0 = path.get(seg);
			double[] p1 = path.get(seg + 1);
			double dx = p1[0] - p0[0];
			double dy = p1[1] - p0[1];
			double dz = p1[2] - p0[2];
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (len < 1e-6) {
				continue;
			}
			double jx = (rnd.nextDouble() - 0.5) * 0.2;
			double jy = (rnd.nextDouble() - 0.5) * 0.2;
			double jz = (rnd.nextDouble() - 0.5) * 0.2;
			// count 0: (dx, dy, dz) is de richting; de snelheid is zo gekozen dat een deeltje ongeveer een blok vliegt
			particleAt(nether ? "minecraft:flame" : "minecraft:end_rod", p0[0] + jx, p0[1] + jy, p0[2] + jz,
					dx / len, dy / len, dz / len, nether ? 0.06 : 0.08, 0);
			if (rnd.nextInt(2) == 0) {
				double t = rnd.nextDouble();
				particleAt(nether ? "minecraft:small_flame" : "minecraft:reverse_portal",
						p0[0] + dx * t, p0[1] + dy * t, p0[2] + dz * t, 0.04, 0.04, 0.04, 0.0, 1);
			}
		}
	}

	// ------------------------------------------------------------------ poort-blokkade

	/** Je ging een dimensie in zonder werkend altaar bij het portaal: de poort is slapend, we sturen je terug. */
	private static void bounce(ServerPlayer p, PlayerData d, String dim) {
		d.inRun = false;
		d.grace = Config.BOUNCE_GRACE_TICKS;
		String portal = NETHER.equals(dim) ? "Nether Portal" : "End Portal";
		notice(p, "The portal is dormant. Charge a " + dimName(dim) + " anchor next to this " + portal + ".", ORANGE);
		fx(p, Fx.BOUNCE, 0);
		particles(p, "minecraft:poof", 1.0, 0.4, 0.05, 30);
		sound(p, "minecraft:entity.villager.no", 1.0f, 0.7f);
		String u = p.getStringUUID();
		if (d.hasLast) {
			run(String.format(Locale.ROOT, "execute in minecraft:overworld run tp %s %.2f %.2f %.2f", u, d.lastX, d.lastY, d.lastZ));
		} else {
			sendToOverworldSpawn(p);
		}
	}

	private static void sendToOverworldSpawn(ServerPlayer p) {
		if (server == null) {
			return;
		}
		BlockPos sp = server.overworld().getRespawnData().pos();
		run(String.format(Locale.ROOT, "execute in minecraft:overworld run spreadplayers %d %d 0 2 false %s",
				sp.getX(), sp.getZ(), p.getStringUUID()));
	}

	// ------------------------------------------------------------------ expeditie klaar / Collapse / dood

	private static void completeRun(ServerPlayer p, PlayerData d) {
		clearBeyondRope(p, d);
		d.inRun = false;
		d.linked.clear();
		d.expeditions++;
		d.longestRun = Math.max(d.longestRun, d.runTime);
		d.runTime = 0;

		fx(p, Fx.SAFE, 0);
		particles(p, "minecraft:totem_of_undying", 1.0, 0.5, 0.35, 40);
		particles(p, "minecraft:soul", 1.0, 0.8, 0.03, 20);
		sound(p, "extremedimensions:portal_home", 1.0f, 1.0f);
		unlock(p, d, JournalEntry.WOUND);
		if (d.expeditions >= 5) {
			unlock(p, d, JournalEntry.HABIT);
		}
		DataStore.save();
	}

	private static void collapse(ServerPlayer p, PlayerData d) {
		clearRope(p, d, false);
		d.collapsing = true;
		d.collapseRetry = 0;
		d.collapses++;
		owePenalty(d, false);
		unlock(p, d, JournalEntry.STARVED);
		notice(p, "DIMENSIONAL COLLAPSE", RED);
		fx(p, Fx.COLLAPSE, 0);
		particles(p, "minecraft:explosion", 1.0, 0.8, 0.0, 6);
		particles(p, "minecraft:lava", 1.0, 0.8, 0.1, 40);
		particles(p, "minecraft:electric_spark", 1.0, 1.0, 0.4, 60);
		sound(p, "minecraft:entity.lightning_bolt.thunder", 1.2f, 0.7f);
		sound(p, "minecraft:entity.generic.explode", 0.8f, 0.6f);
		DataStore.save();
		run("kill " + p.getStringUUID());
	}

	private static void onRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		PlayerData d = DataStore.get(newPlayer.getUUID());
		if (!alive) {
			// Altijd terug naar de Overworld, ook met een bed/anchor in de Nether of End.
			if (!OVERWORLD.equals(dimOf(newPlayer))) {
				sendToOverworldSpawn(newPlayer);
			}
			d.collapsing = false;
			d.collapseRetry = 0;
			d.inRun = false;
			d.linked.clear();
			d.runTime = 0;
			applyPenaltyEffects(newPlayer, d);
			if ("Overworld".equals(d.endedIn)) {
				title(newPlayer, "THE TETHER TIGHTENS", "dark_purple", "You died in the Overworld");
				fx(newPlayer, Fx.ENDED, 0);
				soundSelf(newPlayer, "extremedimensions:expedition_ended", 1.0f, 0.8f);
				d.endedIn = null;
			} else if (d.endedIn != null) {
				title(newPlayer, "EXPEDITION ENDED", "dark_red", "You died in the " + d.endedIn);
				fx(newPlayer, Fx.ENDED, 0);
				soundSelf(newPlayer, "extremedimensions:expedition_ended", 1.0f, 1.0f);
				d.endedIn = null;
			} else {
				sound(newPlayer, "minecraft:block.beacon.deactivate", 1.0f, 0.8f);
			}
			if (d.respawnNotice != null) {
				notice(newPlayer, d.respawnNotice, ORANGE);
				d.respawnNotice = null;
			}
			if (!d.pendingOffer.isEmpty()) {
				d.offerDelay = Config.OFFER_DELAY_TICKS;
			}
			particles(newPlayer, "minecraft:reverse_portal", 1.0, 0.6, 0.15, 60);
		}
		sendState(newPlayer, d);
	}

	private static void onJoin(ServerPlayer p) {
		PlayerData d = DataStore.get(p.getUUID());
		d.dim = dimOf(p);
		d.collapsing = false;
		d.collapseRetry = 0;
		d.inRun = isCharged(d.dim) && !d.linked.isEmpty();
		d.lastWarn = Integer.MAX_VALUE;
		if (!d.pendingOffer.isEmpty()) {
			d.pendingPenalties = Math.max(1, d.pendingPenalties);
			while (d.owed.size() < d.pendingPenalties) {
				d.owed.add("beyond");
			}
			d.offerDelay = 40;
		}
		d.joinDone = false;
		d.ticksOnline = 0;
		d.hasRopeEnd = false;
		sendState(p, d);
	}

	/** Even na het inloggen: nu staat de speler echt in de wereld en werken commando's op hem. */
	private static void joinActions(ServerPlayer p, PlayerData d) {
		String u = p.getStringUUID();
		applyPenaltyEffects(p, d);
		run("recipe give " + u + " minecraft:lodestone");
		run("recipe give " + u + " extremedimensions:tether_journal");
		run("recipe give " + u + " extremedimensions:tether");
		run("recipe give " + u + " extremedimensions:ember_tether");
		run("recipe give " + u + " extremedimensions:void_tether");
		if (!d.journalDelivered) {
			d.journalDelivered = true;
			ItemStack book = new ItemStack(ModItems.TETHER_JOURNAL);
			if (!p.getInventory().add(book)) {
				run("give " + u + " extremedimensions:tether_journal");
			}
			d.journal.add(JournalEntry.TETHERED.id);
			run("title " + u + " actionbar {\"text\":\"Something old is holding you. Read the journal.\",\"color\":\"light_purple\",\"italic\":true}");
			soundSelf(p, "minecraft:item.book.page_turn", 1.0f, 0.7f);
			DataStore.save();
		}
	}

	// ------------------------------------------------------------------ altaar

	private static InteractionResult useAltar(ServerPlayer p, BlockPos pos) {
		PlayerData d = DataStore.get(p.getUUID());
		ItemStack stack = p.getMainHandItem();

		// Sneak + lege hand = statusscherm
		if (stack.isEmpty()) {
			if (p.isShiftKeyDown()) {
				sendState(p, d);
				if (!d.pendingOffer.isEmpty()) {
					// Je moet nog een penalty kiezen: eerst dat scherm.
					sendOffer(p, d);
				} else {
					ServerPlayNetworking.send(p, new ModPackets.OpenStatusPayload());
				}
				return InteractionResult.SUCCESS;
			}
			if (Altar.key(pos.getX(), pos.getY(), pos.getZ()).equals(d.tetherAnchor) && !d.inRun) {
				clearRope(p, d, true);
				notice(p, "You untie your tether and coil it up.", ORANGE);
				sound(p, "minecraft:entity.leash_knot.break", 1.0f, 0.8f);
				sendState(p, d);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}

		Item item = stack.getItem();
		if (item == ModItems.TETHER_FRAGMENT) {
			return mend(p, d, pos, stack);
		}
		if (isTetherItem(item)) {
			return tie(p, d, pos, stack);
		}
		boolean tether = item == ModItems.EMBER_TETHER || item == ModItems.VOID_TETHER;
		String type;
		if (item == ModItems.EMBER_TETHER || ChargeItems.NETHER.containsKey(item)) {
			type = NETHER;
		} else if (item == ModItems.VOID_TETHER || ChargeItems.END.containsKey(item)) {
			type = END;
		} else {
			return InteractionResult.PASS; // gewone Lodestone (kompas e.d.)
		}
		String name = dimName(type);

		if (!OVERWORLD.equals(dimOf(p))) {
			notice(p, "Anchors only work in the Overworld.", ORANGE);
			return InteractionResult.SUCCESS;
		}
		ServerLevel level = (ServerLevel) p.level();

		String key = Altar.key(pos.getX(), pos.getY(), pos.getZ());
		Altar altar = DataStore.altars().get(key);
		if (altar != null && !type.equals(altar.type)) {
			notice(p, "This anchor is bound to the " + dimName(altar.type) + ". It only accepts " + dimName(altar.type) + " fuel.", ORANGE);
			return InteractionResult.SUCCESS;
		}
		if (findPortal(level, pos, type) == null) {
			String portal = NETHER.equals(type) ? "a Nether Portal" : "an End Portal";
			notice(p, "An anchor must be within " + Config.PORTAL_RANGE + " blocks of " + portal + ".", ORANGE);
			return InteractionResult.SUCCESS;
		}

		double current = altar != null ? altar.charge : 0;
		double space = Config.MAX_CHARGE - current;
		if (space < 1) {
			notice(p, "This " + name + " anchor is full.", ORANGE);
			return InteractionResult.SUCCESS;
		}

		double added;
		if (tether) {
			added = Math.min(space, Config.TETHER_ITEM_CHARGE * d.debtFactor());
			stack.shrink(1);
		} else {
			double per = (NETHER.equals(type) ? ChargeItems.NETHER : ChargeItems.END).get(item) * d.debtFactor();
			int n = (int) Math.min(stack.getCount(), Math.ceil(space / per));
			added = Math.min(space, n * per);
			stack.shrink(n);
		}

		boolean nether = NETHER.equals(type);
		if (altar == null) {
			// Eerste lading: het altaar kiest zijn dimensie en verandert van uiterlijk.
			altar = new Altar(pos.getX(), pos.getY(), pos.getZ(), type);
			DataStore.altars().put(key, altar);
			level.setBlock(pos, (nether ? ModBlocks.EMBER_LODESTONE : ModBlocks.VOID_LODESTONE).defaultBlockState(), 3, 512);
			sound(p, "minecraft:item.lodestone_compass.lock", 1.0f, 0.8f);
			sound(p, "minecraft:block.beacon.activate", 0.8f, nether ? 0.8f : 1.2f);
			unlock(p, d, JournalEntry.ANCHOR);
		}
		altar.charge += added;
		if (Config.MAX_CHARGE - altar.charge < 1) {
			unlock(p, d, JournalEntry.SILENCE);
		}
		altar.portalCheck = 0;

		fx(p, nether ? Fx.DEPOSIT_NETHER : Fx.DEPOSIT_END, (int) Math.round(added));
		double ax = pos.getX() + 0.5;
		double ay = pos.getY() + 1.0;
		double az = pos.getZ() + 0.5;
		particleAt(nether ? "minecraft:flame" : "minecraft:portal", ax, ay, az, 0.4, 0.4, 0.4, nether ? 0.04 : 0.6, tether ? 60 : 30);
		particleAt("minecraft:enchant", ax, ay + 0.5, az, 0.6, 0.6, 0.6, 0.4, tether ? 50 : 25);
		sound(p, nether ? "minecraft:block.respawn_anchor.charge" : "minecraft:block.end_portal_frame.fill", 0.8f, tether ? 1.0f : 1.2f);
		sendState(p, d);
		return InteractionResult.SUCCESS;
	}

	// ------------------------------------------------------------------ de fysieke tether

	/** Rechtsklik met een ketting of lead op een anker: vastmaken (of weer losmaken). */
	private static InteractionResult tie(ServerPlayer p, PlayerData d, BlockPos pos, ItemStack stack) {
		String key = Altar.key(pos.getX(), pos.getY(), pos.getZ());
		Altar altar = DataStore.altars().get(key);
		if (altar == null || !OVERWORLD.equals(dimOf(p))) {
			notice(p, "This stone is not an anchor yet. Feed it first.", ORANGE);
			return InteractionResult.SUCCESS;
		}
		if (d.inRun) {
			return InteractionResult.SUCCESS;
		}
		if (key.equals(d.tetherAnchor)) {
			notice(p, "You are already bound to this anchor. Empty hand to untie.", ORANGE);
			return InteractionResult.SUCCESS;
		}
		// Al ergens anders vast? Die tether krijg je terug.
		clearRope(p, d, d.tetherAnchor != null);
		stack.shrink(1);
		d.tetherAnchor = key;
		d.hasRopeEnd = true;
		d.ropeDim = "minecraft:overworld";
		d.ropeX = pos.getX() + 0.5;
		d.ropeY = pos.getY() + 1.02;
		d.ropeZ = pos.getZ() + 0.5;
		notice(p, "Your tether is bound to this " + dimName(altar.type) + " anchor.", AQUA);
		unlock(p, d, JournalEntry.CORD);
		sound(p, "minecraft:entity.leash_knot.place", 1.0f, 0.7f);
		particleAt("minecraft:soul", pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0.3, 0.2, 0.3, 0.02, 12);
		sendState(p, d);
		return InteractionResult.SUCCESS;
	}

	/** Korte tag per speler voor zijn kettingstukken. */
	private static String ropeTag(ServerPlayer p) {
		return "edt_" + p.getStringUUID().replace("-", "").substring(0, 12);
	}

	/** Is de speler te ver van zijn anker? Bij het portaal van het anker staan mag altijd. */
	private static boolean tetherTooFar(ServerPlayer p, Altar anchor) {
		double max = Config.TETHER_BREAK_RANGE + 0.5;
		double dx = anchor.x + 0.5 - p.getX();
		double dz = anchor.z + 0.5 - p.getZ();
		if (dx * dx + dz * dz <= max * max && Math.abs(anchor.y - p.getY()) <= max) {
			return false;
		}
		if (!anchor.path.isEmpty()) {
			double[] portal = anchor.path.get(anchor.path.size() - 1);
			double px = portal[0] - p.getX();
			double py = portal[1] - p.getY() - 1.0;
			double pz = portal[2] - p.getZ();
			double slack = Config.TETHER_PORTAL_SLACK;
			if (px * px + py * py + pz * pz <= slack * slack) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Hoe de ketting eruitziet: een ijzeren ketting in de Overworld en de Nether,
	 * een dun gloeiend paars koord in de End. {blockstate, dikte}
	 */
	private static String[] ropeLook(String dim) {
		if ("minecraft:the_end".equals(dim)) {
			return new String[] {"{Name:\"minecraft:amethyst_block\"}", "0.09"};
		}
		String chain = BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "iron_chain"))
				? "minecraft:iron_chain" : "minecraft:chain";
		return new String[] {"{Name:\"" + chain + "\",Properties:{axis:\"y\"}}", "1"};
	}

	/**
	 * Legt een nieuw kettingstuk op de grond als de speler ver genoeg is gelopen sinds het vorige.
	 * Zo ligt er een spoor precies waar je gelopen hebt.
	 */
	private static void layRope(ServerPlayer p, PlayerData d) {
		String dim = p.level().dimension().identifier().toString();
		double x = p.getX();
		double y = p.getY() + 0.1;
		double z = p.getZ();
		if (!d.hasRopeEnd || !dim.equals(d.ropeDim)) {
			// Net door een portaal: de ketting begint opnieuw bij je aankomstpunt
			d.hasRopeEnd = true;
			d.ropeDim = dim;
			d.ropeX = x;
			d.ropeY = y;
			d.ropeZ = z;
			return;
		}
		double dx = x - d.ropeX;
		double dy = y - d.ropeY;
		double dz = z - d.ropeZ;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len > 8) {
			// Teleport of een lange val: niet door de lucht spannen, gewoon verder vanaf hier
			d.ropeX = x;
			d.ropeY = y;
			d.ropeZ = z;
			return;
		}
		// Alleen op de grond (of zwemmend) neerleggen: springen laat geen ketting in de lucht hangen
		if (len < Config.ROPE_SEGMENT || !(p.onGround() || p.isInWater())) {
			return;
		}
		// Rotatie (quaternion) die de y-as van het blok langs de richting van het stuk legt
		double ux = dx / len, uy = dy / len, uz = dz / len;
		double qx = uz, qy = 0, qz = -ux, qw = 1 + uy;
		double n = Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
		if (n < 1e-6) {
			qx = 1;
			qy = 0;
			qz = 0;
			qw = 0;
		} else {
			qx /= n;
			qy /= n;
			qz /= n;
			qw /= n;
		}
		String[] look = ropeLook(dim);
		double thick = Double.parseDouble(look[1]);
		// Het midden van de onderkant van het (geschaalde) blok moet op het beginpunt liggen
		double[] c = rotate(qx, qy, qz, qw, 0.5 * thick, 0, 0.5 * thick);
		int index = d.ropeIndex++;
		String nbt = String.format(Locale.ROOT,
				"{Tags:[\"ed_tether\",\"%s\",\"%s\",\"%s_s%d\"],block_state:%s,brightness:{sky:15,block:15},"
				+ "transformation:{left_rotation:[%.5ff,%.5ff,%.5ff,%.5ff],right_rotation:[0f,0f,0f,1f],"
				+ "translation:[%.5ff,%.5ff,%.5ff],scale:[%.3ff,%.5ff,%.3ff]}}",
				ropeTag(p), ropeTag(p) + ("minecraft:overworld".equals(dim) ? "_o" : "_b"), ropeTag(p), index, look[0],
				qx, qy, qz, qw, -c[0], -c[1], -c[2], thick, len, thick);
		run(String.format(Locale.ROOT, "execute in %s run summon minecraft:block_display %.3f %.3f %.3f %s",
				dim, d.ropeX, d.ropeY, d.ropeZ, nbt));
		// Te lang spoor: het oudste stuk verdwijnt
		int old = index - Config.ROPE_MAX_SEGMENTS;
		if (old >= 0) {
			run("kill @e[type=minecraft:block_display,tag=" + ropeTag(p) + "_s" + old + "]");
		}
		d.ropeX = x;
		d.ropeY = y;
		d.ropeZ = z;
	}

	/** Draait vector v met quaternion q. */
	private static double[] rotate(double qx, double qy, double qz, double qw, double vx, double vy, double vz) {
		double tx = 2 * (qy * vz - qz * vy);
		double ty = 2 * (qz * vx - qx * vz);
		double tz = 2 * (qx * vy - qy * vx);
		return new double[] {
				vx + qw * tx + (qy * tz - qz * ty),
				vy + qw * ty + (qz * tx - qx * tz),
				vz + qw * tz + (qx * ty - qy * tx)};
	}

	/** Haalt de ketting van deze speler weg en maakt hem los. */
	static void clearRope(ServerPlayer p, PlayerData d, boolean returnItem) {
		run("kill @e[type=minecraft:block_display,tag=" + ropeTag(p) + "]");
		if (returnItem && d.tetherAnchor != null) {
			ItemStack back = new ItemStack(ModItems.TETHER);
			if (!p.getInventory().add(back)) {
				run("give " + p.getStringUUID() + " extremedimensions:tether");
			}
		}
		d.tetherAnchor = null;
		d.hasRopeEnd = false;
	}

	/** Na een geslaagde terugkeer: de ketting aan de overkant verdwijnt, het stuk in de Overworld blijft liggen. */
	static void clearBeyondRope(ServerPlayer p, PlayerData d) {
		run("kill @e[type=minecraft:block_display,tag=" + ropeTag(p) + "_b]");
		d.hasRopeEnd = false;
	}

	/** Ruimt kettingstukken op van spelers die niet (meer) vastzitten, ook achtergebleven stukken. */
	private static void sweepRopes(MinecraftServer s) {
		StringBuilder sel = new StringBuilder("kill @e[type=minecraft:block_display,tag=ed_tether");
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (DataStore.get(p.getUUID()).tetherAnchor != null) {
				sel.append(",tag=!").append(ropeTag(p));
			}
		}
		run(sel.append("]").toString());
	}

	/** Horizontale afstand tot je aankomstportaal (of in de Overworld tot je anker); -1 als je niet vastzit. */
	static int tetherDistance(ServerPlayer p, PlayerData d) {
		if (d.tetherAnchor == null) {
			return -1;
		}
		if (!d.inRun) {
			// In de Overworld: afstand tot je anker (de tether breekt na Config.TETHER_BREAK_RANGE)
			Altar anchor = DataStore.altars().get(d.tetherAnchor);
			if (anchor == null) {
				return 0;
			}
			double ax = p.getX() - anchor.x - 0.5;
			double az = p.getZ() - anchor.z - 0.5;
			return (int) Math.sqrt(ax * ax + az * az);
		}
		double dx = p.getX() - d.arrivalX;
		double dz = p.getZ() - d.arrivalZ;
		return (int) Math.sqrt(dx * dx + dz * dz);
	}

	/** Hoe verder van het portaal, hoe sneller de Starved drinken. */
	static double distanceFactor(ServerPlayer p, PlayerData d) {
		double dx = p.getX() - d.arrivalX;
		double dz = p.getZ() - d.arrivalZ;
		return 1.0 + Math.sqrt(dx * dx + dz * dz) / Config.TETHER_DISTANCE_STEP;
	}

	/** Een Tether Fragment offeren aan een anker: de langst lopende penalty-stack verdwijnt. */
	private static InteractionResult mend(ServerPlayer p, PlayerData d, BlockPos pos, ItemStack stack) {
		String bestId = null;
		int bestIdx = -1;
		double bestSecs = -1;
		for (Map.Entry<String, List<Double>> e : d.penaltyTime.entrySet()) {
			for (int i = 0; i < e.getValue().size(); i++) {
				if (e.getValue().get(i) > bestSecs) {
					bestSecs = e.getValue().get(i);
					bestId = e.getKey();
					bestIdx = i;
				}
			}
		}
		String mended;
		if (bestId != null) {
			List<Double> l = d.penaltyTime.get(bestId);
			l.remove(bestIdx);
			if (l.isEmpty()) {
				d.penaltyTime.remove(bestId);
			}
			Penalty pen = Penalty.byId(bestId);
			mended = pen != null ? pen.title : bestId;
			applyPenaltyEffects(p, d);
		} else if (d.leakStacks > 0) {
			d.leakStacks--;
			mended = Penalty.LEAKY_TANK.title;
		} else if (d.xpDebt > 0) {
			d.xpDebt = 0;
			mended = "Your XP debt";
		} else {
			notice(p, "The Tether has nothing to mend in you.", ORANGE);
			return InteractionResult.SUCCESS;
		}
		stack.shrink(1);
		notice(p, "The fragment mends you: " + mended + " fades.", AQUA);
		double ax = pos.getX() + 0.5;
		double ay = pos.getY() + 1.0;
		double az = pos.getZ() + 0.5;
		particleAt("minecraft:soul_fire_flame", ax, ay, az, 0.3, 0.5, 0.3, 0.03, 30);
		particleAt("minecraft:sculk_soul", ax, ay + 0.5, az, 0.5, 0.5, 0.5, 0.05, 20);
		particles(p, "minecraft:soul", 1.0, 0.4, 0.05, 20);
		sound(p, "minecraft:block.sculk_catalyst.bloom", 1.0f, 0.8f);
		sound(p, "minecraft:block.beacon.power_select", 0.7f, 1.4f);
		unlock(p, d, JournalEntry.MENDING);
		sendState(p, d);
		DataStore.save();
		return InteractionResult.SUCCESS;
	}

	// ------------------------------------------------------------------ penalty kiezen

	public static void onChoose(ServerPlayer p, String id) {
		PlayerData d = DataStore.get(p.getUUID());
		if (!d.pendingOffer.contains(id)) {
			return;
		}
		Penalty pen = Penalty.byId(id);
		if (pen == null) {
			return;
		}
		d.pendingOffer.clear();
		d.pendingPenalties = Math.max(0, d.pendingPenalties - 1);
		if (!d.owed.isEmpty()) {
			d.owed.remove(0);
		}

		String u = p.getStringUUID();
		if (pen.timed) {
			d.addStack(pen);
			applyPenaltyEffects(p, d);
		} else {
			switch (pen) {
				case LEAKY_TANK -> d.leakStacks++;
				case MOURNING -> {
					int secs = Config.MOURNING_SECONDS;
					run("effect give " + u + " minecraft:weakness " + secs + " 1 true");
					run("effect give " + u + " minecraft:hunger " + secs + " 1 true");
					run("effect give " + u + " minecraft:mining_fatigue " + secs + " 0 true");
				}
				case TOLL -> d.xpDebt += Config.TOLL_LEVELS;
				case TITHE -> d.xpDebt += Config.TITHE_LEVELS;
				default -> { }
			}
		}

		int stacks = pen.timed ? d.stacks(pen) : pen == Penalty.LEAKY_TANK ? d.leakStacks : 1;
		notice(p, "Penalty: " + pen.title + (stacks > 1 ? " x" + stacks : ""), ORANGE);
		fx(p, Fx.PENALTY, 0);
		particles(p, "minecraft:soul", 1.0, 0.5, 0.05, 25);
		particles(p, "minecraft:sculk_soul", 1.2, 0.6, 0.03, 12);
		soundSelf(p, "extremedimensions:penalty", 1.0f, 1.0f);
		unlock(p, d, JournalEntry.SCARS);

		// Nog meer penalty's tegoed (meerdere keren dood)? Dan volgt het volgende scherm.
		if (d.pendingPenalties > 0) {
			if (d.owed.isEmpty()) {
				d.owed.add("beyond");
			}
			d.pendingOffer = Penalty.rollOffer("overworld".equals(d.owed.get(0)));
			d.offerDelay = 40;
		}
		sendState(p, d);
		DataStore.save();
	}

	// ------------------------------------------------------------------ penalty-effecten

	/** Zet alle attribuut-penalty's volgens het aantal stacks en ververst de effecten. */
	static void applyPenaltyEffects(ServerPlayer p, PlayerData d) {
		boolean ow = OVERWORLD.equals(dimOf(p));
		int scar = d.stacks(Penalty.SCAR);
		double scarHp = Math.min(16.0, Config.SCAR_HEALTH * scar);
		setModifier(p, "minecraft:max_health", "scar", -scarHp, "add_value");
		double hollowHp = ow ? Math.min(16.0 - scarHp, Config.HOLLOW_HEART * d.stacks(Penalty.HOLLOW_HEART)) : 0;
		setModifier(p, "minecraft:max_health", "hollow_heart", -hollowHp, "add_value");
		setModifier(p, "minecraft:movement_speed", "leaden_steps",
				ow ? -(1 - Math.pow(1 - Config.LEADEN_STEPS, d.stacks(Penalty.LEADEN_STEPS))) : 0, "add_multiplied_total");
		setModifier(p, "minecraft:block_break_speed", "dull_pick",
				ow ? -(1 - Math.pow(1 - Config.DULL_PICK, d.stacks(Penalty.DULL_PICK))) : 0, "add_multiplied_total");
		setModifier(p, "minecraft:armor", "cracked_armor", -Config.CRACKED_ARMOR * d.stacks(Penalty.CRACKED_ARMOR), "add_value");
		setModifier(p, "minecraft:movement_speed", "heavy_legs",
				-(1 - Math.pow(1 - Config.HEAVY_LEGS, d.stacks(Penalty.HEAVY_LEGS))), "add_multiplied_total");
		setModifier(p, "minecraft:block_break_speed", "clumsy_hands",
				-(1 - Math.pow(1 - Config.CLUMSY_HANDS, d.stacks(Penalty.CLUMSY_HANDS))), "add_multiplied_total");
		refreshEffects(p, d);
	}

	private static void setModifier(ServerPlayer p, String attribute, String name, double value, String operation) {
		String u = p.getStringUUID();
		run("attribute " + u + " " + attribute + " modifier remove extremedimensions:" + name);
		if (value != 0) {
			run(String.format(Locale.ROOT, "attribute %s %s modifier add extremedimensions:%s %.4f %s", u, attribute, name, value, operation));
		}
	}

	/** Blijvende effecten (Frailty, Famine, Shadowed): steeds kort opnieuw geven, zodat ze vanzelf stoppen als de penalty verloopt. */
	static void refreshEffects(ServerPlayer p, PlayerData d) {
		String u = p.getStringUUID();
		int frailty = d.stacks(Penalty.FRAILTY);
		if (frailty > 0) {
			run("effect give " + u + " minecraft:weakness 8 " + (frailty - 1) + " true");
		}
		int famine = d.stacks(Penalty.FAMINE);
		if (famine > 0) {
			run("effect give " + u + " minecraft:hunger 8 " + (famine - 1) + " true");
		}
		if (OVERWORLD.equals(dimOf(p))) {
			int gnawing = d.stacks(Penalty.GNAWING);
			if (gnawing > 0) {
				run("effect give " + u + " minecraft:hunger 8 " + (gnawing - 1) + " true");
			}
			int luck = d.stacks(Penalty.ILL_LUCK);
			if (luck > 0) {
				run("effect give " + u + " minecraft:unluck 8 " + (luck - 1) + " true");
			}
			if (d.stacks(Penalty.NIGHT_TERROR) > 0 && p.getY() < 62) {
				run("effect give " + u + " minecraft:darkness 8 0 true");
			}
		}
		int shadowed = d.stacks(Penalty.SHADOWED);
		if (shadowed > 0 && isCharged(dimOf(p))) {
			run("effect give " + u + " minecraft:darkness 8 0 true");
			if (shadowed >= 2) {
				run("effect give " + u + " minecraft:slowness 8 " + (shadowed - 2) + " true");
			}
		}
	}
}
