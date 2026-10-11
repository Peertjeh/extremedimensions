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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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

		ServerPlayerEvents.JOIN.register(ServerLogic::onJoin);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> DataStore.save());
		ServerPlayerEvents.AFTER_RESPAWN.register(ServerLogic::onRespawn);

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof ServerPlayer p)) {
				return;
			}
			PlayerData d = DataStore.get(p.getUUID());
			String dim = entity.level().dimension().identifier().getPath();
			// Gewoon doodgaan in Nether/End (geen Collapse): expeditie voorbij, altaren leeg en een penalty.
			// In de Overworld (nog) geen straf.
			if (isCharged(dim) && !d.collapsing) {
				if (d.inRun) {
					for (Altar a : linkedAltars(d)) {
						a.charge = 0;
					}
					d.respawnNotice = "The altars feeding your expedition ran dry.";
				}
				d.inRun = false;
				d.linked.clear();
				d.endedIn = dimName(dim);
				owePenalty(d);
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

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (OVERWORLD.equals(level.dimension().identifier().getPath())) {
				DataStore.altars().remove(Altar.key(pos.getX(), pos.getY(), pos.getZ()));
			}
		});
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

	static boolean isFuel(Item item) {
		return item == ModItems.EMBER_TETHER || item == ModItems.VOID_TETHER
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
		if (d.leakStacks > 0) {
			if (pen.length() > 0) {
				pen.append(';');
			}
			pen.append(Penalty.LEAKY_TANK.id).append(':').append(d.leakStacks).append(":-1");
		}
		ServerPlayNetworking.send(p, new ModPackets.StatePayload(
				overworld ? nearCharge(p, NETHER) : -1, overworld ? nearCharge(p, END) : -1,
				d.inRun ? (int) Math.ceil(linkedCharge(d)) : 0, (int) Math.ceil(d.runMax), dimCode,
				(int) Math.round(d.drainMultiplier() * 100), d.expeditions, d.collapses,
				(int) d.longestRun, (int) d.runTime, pen.toString(), d.penaltyClock));
	}

	static void sendOffer(ServerPlayer p, PlayerData d) {
		if (!d.pendingOffer.isEmpty()) {
			ServerPlayNetworking.send(p, new ModPackets.OfferPayload(String.join(",", d.pendingOffer)));
		}
	}

	/** De speler moet (nog) een penalty kiezen. */
	static void owePenalty(PlayerData d) {
		d.pendingPenalties++;
		if (d.pendingOffer.isEmpty()) {
			d.pendingOffer = Penalty.rollOffer();
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
				refreshEffects(p, d);
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
				double cur = drainLinked(d, d.drainMultiplier() / 20.0);
				d.runTime += 1.0 / 20.0;
				if (cur <= 0) {
					collapse(p, d);
					forceSync = true;
				} else {
					int secsLeft = (int) Math.ceil(cur);
					warn(p, d, secsLeft);
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

			// 4) penalty-klok: loopt alleen tijdens een expeditie in Nether/End en als je niet AFK bent
			trackActivity(p, d);
			if (tickPenalties(p, d, isCharged(dim) && d.inRun && !d.collapsing)) {
				forceSync = true;
			}

			// 5) blijvende penalty-effecten bijhouden
			if (refreshEffects) {
				refreshEffects(p, d);
			}

			// 6) status naar de client
			if (forceSync || ++d.tickCounter % Config.SYNC_INTERVAL_TICKS == 0) {
				sendState(p, d);
			}
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
	private static boolean tickPenalties(ServerPlayer p, PlayerData d, boolean onExpedition) {
		int before = d.penaltyClock;
		if (d.penaltyTime.isEmpty() || !onExpedition) {
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
		if (d.leakStacks > 0) {
			double keep = Math.pow(1.0 - Config.LEAK_FRACTION, d.leakStacks);
			for (Altar a : linkedAltars(d)) {
				a.charge *= keep;
			}
			total = linkedCharge(d);
			notice(p, "Leaky Tank: your altars lost " + Math.round((1 - keep) * 100) + "% of their charge.", ORANGE);
			d.leakStacks = 0;
		}
		d.inRun = true;
		d.runTime = 0;
		d.runMax = total;
		d.lastWarn = Integer.MAX_VALUE;
		d.lastBeat = Integer.MAX_VALUE;
		fx(p, NETHER.equals(dim) ? Fx.START_NETHER : Fx.START_END, 0);
		if (NETHER.equals(dim)) {
			particles(p, "minecraft:flame", 1.0, 0.6, 0.06, 60);
			sound(p, "minecraft:block.respawn_anchor.charge", 1.0f, 0.8f);
		} else {
			particles(p, "minecraft:reverse_portal", 1.0, 0.6, 0.2, 80);
			sound(p, "minecraft:block.end_portal_frame.fill", 1.0f, 0.7f);
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
				notice(p, "A " + dimName(a.type) + " altar lost its path to the portal. It is leaking charge!", RED);
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
		notice(p, "The portal is dormant. Charge a " + dimName(dim) + " altar next to this " + portal + ".", ORANGE);
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
		d.inRun = false;
		d.linked.clear();
		d.expeditions++;
		d.longestRun = Math.max(d.longestRun, d.runTime);
		d.runTime = 0;

		fx(p, Fx.SAFE, 0);
		particles(p, "minecraft:totem_of_undying", 1.0, 0.5, 0.35, 40);
		sound(p, "minecraft:entity.player.levelup", 0.9f, 0.9f);
		notice(p, "Safely back in the Overworld!", GREEN);
		DataStore.save();
	}

	private static void collapse(ServerPlayer p, PlayerData d) {
		d.collapsing = true;
		d.collapseRetry = 0;
		d.collapses++;
		owePenalty(d);
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
			if (d.endedIn != null) {
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
			d.offerDelay = 40;
		}
		applyPenaltyEffects(p, d);
		sendState(p, d);
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
			return InteractionResult.PASS;
		}

		Item item = stack.getItem();
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
			notice(p, "Altars only work in the Overworld.", ORANGE);
			return InteractionResult.SUCCESS;
		}
		ServerLevel level = (ServerLevel) p.level();

		String key = Altar.key(pos.getX(), pos.getY(), pos.getZ());
		Altar altar = DataStore.altars().get(key);
		if (altar != null && !type.equals(altar.type)) {
			notice(p, "This altar is bound to the " + dimName(altar.type) + ". It only accepts " + dimName(altar.type) + " fuel.", ORANGE);
			return InteractionResult.SUCCESS;
		}
		if (findPortal(level, pos, type) == null) {
			String portal = NETHER.equals(type) ? "a Nether Portal" : "an End Portal";
			notice(p, "An altar must be within " + Config.PORTAL_RANGE + " blocks of " + portal + ".", ORANGE);
			return InteractionResult.SUCCESS;
		}

		double current = altar != null ? altar.charge : 0;
		double space = Config.MAX_CHARGE - current;
		if (space < 1) {
			notice(p, "This " + name + " altar is full.", ORANGE);
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
		}
		altar.charge += added;
		altar.portalCheck = 0;

		notice(p, "+" + fmt(added) + " " + name + " Charge  (altar: " + fmt(altar.charge) + ")", AQUA);
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
				case TOLL -> {
					int lose = (p.experienceLevel + 1) / 2;
					if (lose > 0) {
						p.giveExperienceLevels(-lose);
					}
				}
				default -> { }
			}
		}

		int stacks = pen.timed ? d.stacks(pen) : pen == Penalty.LEAKY_TANK ? d.leakStacks : 1;
		notice(p, "Penalty: " + pen.title + (stacks > 1 ? " x" + stacks : ""), ORANGE);
		fx(p, Fx.PENALTY, 0);
		particles(p, "minecraft:soul", 1.0, 0.5, 0.05, 25);
		particles(p, "minecraft:sculk_soul", 1.2, 0.6, 0.03, 12);
		soundSelf(p, "extremedimensions:penalty", 1.0f, 1.0f);

		// Nog meer penalty's tegoed (meerdere keren dood)? Dan volgt het volgende scherm.
		if (d.pendingPenalties > 0) {
			d.pendingOffer = Penalty.rollOffer();
			d.offerDelay = 40;
		}
		sendState(p, d);
		DataStore.save();
	}

	// ------------------------------------------------------------------ penalty-effecten

	/** Zet alle attribuut-penalty's volgens het aantal stacks en ververst de effecten. */
	static void applyPenaltyEffects(ServerPlayer p, PlayerData d) {
		int scar = d.stacks(Penalty.SCAR);
		setModifier(p, "minecraft:max_health", "scar", -Math.min(16.0, Config.SCAR_HEALTH * scar), "add_value");
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
		int shadowed = d.stacks(Penalty.SHADOWED);
		if (shadowed > 0 && isCharged(dimOf(p))) {
			run("effect give " + u + " minecraft:darkness 8 0 true");
			if (shadowed >= 2) {
				run("effect give " + u + " minecraft:slowness 8 " + (shadowed - 2) + " true");
			}
		}
	}
}
