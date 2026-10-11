package com.extremedimensions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * rond dat portaal je expeditie.
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

	private static MinecraftServer server;
	private static int saveCounter = 0;
	private static int altarTick = 0;

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
			// Normaal doodgaan in Nether/End (geen Collapse): de altaren van je expeditie lopen leeg.
			if (isCharged(dim) && d.inRun && !d.collapsing) {
				for (Altar a : linkedAltars(d)) {
					a.charge = 0;
				}
				d.inRun = false;
				d.linked.clear();
				d.respawnNotice = "You died in the " + dimName(dim) + ": the altars feeding your expedition ran dry.";
			}
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			if (!isAltarBlock(level.getBlockState(pos))) {
				return InteractionResult.PASS;
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

	static void notice(ServerPlayer p, String text, int color) {
		ServerPlayNetworking.send(p, new ModPackets.NoticePayload(text, color));
	}

	static void sendState(ServerPlayer p, PlayerData d) {
		int dimCode = NETHER.equals(d.dim) ? 1 : END.equals(d.dim) ? 2 : 0;
		boolean overworld = OVERWORLD.equals(d.dim);
		StringBuilder pen = new StringBuilder();
		for (Map.Entry<String, Integer> e : d.penalties.entrySet()) {
			if (pen.length() > 0) {
				pen.append(';');
			}
			pen.append(e.getKey()).append(':').append(e.getValue());
		}
		ServerPlayNetworking.send(p, new ModPackets.StatePayload(
				overworld ? nearCharge(p, NETHER) : -1, overworld ? nearCharge(p, END) : -1,
				d.inRun ? (int) Math.ceil(linkedCharge(d)) : 0, (int) Math.ceil(d.runMax), dimCode,
				(int) Math.round(d.drainMultiplier() * 100), d.expeditions, d.collapses,
				(int) d.longestRun, (int) d.runTime, pen.toString()));
	}

	static void sendOffer(ServerPlayer p, PlayerData d) {
		if (!d.pendingOffer.isEmpty()) {
			ServerPlayNetworking.send(p, new ModPackets.OfferPayload(String.join(",", d.pendingOffer)));
		}
	}

	// ------------------------------------------------------------------ tick

	private static void tick(MinecraftServer s) {
		if (++saveCounter >= 6000) { // elke 5 minuten
			saveCounter = 0;
			DataStore.save();
		}

		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			PlayerData d = DataStore.get(p.getUUID());
			String dim = dimOf(p);
			boolean forceSync = false;

			if (d.grace > 0) {
				d.grace--;
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

			// 4) status naar de client
			if (forceSync || ++d.tickCounter % Config.SYNC_INTERVAL_TICKS == 0) {
				sendState(p, d);
			}
		}

		if (++altarTick >= 10) {
			altarTick = 0;
			tickAltars(s);
		}
	}

	private static void startRun(ServerPlayer p, PlayerData d, String dim) {
		// Altaren van dit type rond het portaal waar je doorheen ging
		d.linked.clear();
		if (d.hasLast) {
			double r2 = Config.LINK_RANGE * Config.LINK_RANGE;
			for (Altar a : DataStore.altars().values()) {
				if (dim.equals(a.type) && a.charge > 0 && a.distSq(d.lastX, d.lastY, d.lastZ) <= r2) {
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
		if (d.leakPending) {
			d.leakPending = false;
			for (Altar a : linkedAltars(d)) {
				a.charge *= (1.0 - Config.LEAK_FRACTION);
			}
			total = linkedCharge(d);
			notice(p, "Leaky Tank: your altars lost " + Math.round(Config.LEAK_FRACTION * 100) + "% of their charge.", ORANGE);
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

	// ------------------------------------------------------------------ altaren: onderhoud en deeltjes

	private static void tickAltars(MinecraftServer s) {
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
			// Weggeblazen of anders verdwenen? Dan is het altaar weg.
			if (!isAltarBlock(ow.getBlockState(pos))) {
				it.remove();
				continue;
			}
			if (--a.portalCheck <= 0) {
				a.portalCheck = 4; // elke 2 seconden
				BlockPos portal = findPortal(ow, pos, a.type);
				a.hasPortal = portal != null;
				if (portal != null) {
					a.px = portal.getX();
					a.py = portal.getY();
					a.pz = portal.getZ();
				}
			}
			altarParticles(a);
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

		// Lek naar het portaal toe
		if (a.hasPortal) {
			double tx = a.px + 0.5 - cx;
			double ty = a.py + 0.5 - cy;
			double tz = a.pz + 0.5 - cz;
			int streams = 1 + (int) Math.round(fill * 3);
			for (int i = 0; i < streams; i++) {
				double ox = (rnd.nextDouble() - 0.5) * 0.4;
				double oz = (rnd.nextDouble() - 0.5) * 0.4;
				if (nether) {
					// count 0: (tx, ty, tz) is de richting, speed bepaalt hoe ver het deeltje vliegt
					particleAt("minecraft:flame", cx + ox, cy + 0.5, cz + oz, tx, ty, tz, 0.11, 0);
				} else {
					particleAt("minecraft:end_rod", cx + ox, cy + 0.5, cz + oz, tx, ty, tz, 0.08, 0);
				}
				// stilstaand spoor langs de lijn
				double t = rnd.nextDouble();
				particleAt(nether ? "minecraft:small_flame" : "minecraft:reverse_portal",
						cx + tx * t, cy + 0.3 + ty * t, cz + tz * t, 0.05, 0.05, 0.05, 0.0, 1);
			}
		}
	}

	// ------------------------------------------------------------------ poort-blokkade

	/** Je ging een dimensie in zonder geladen altaar bij het portaal: de poort is slapend, we sturen je terug. */
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

	// ------------------------------------------------------------------ expeditie klaar / Collapse

	private static void completeRun(ServerPlayer p, PlayerData d) {
		d.inRun = false;
		d.linked.clear();
		d.expeditions++;
		d.longestRun = Math.max(d.longestRun, d.runTime);
		d.runTime = 0;

		List<String> expired = new ArrayList<>();
		for (Map.Entry<String, Integer> e : new ArrayList<>(d.penalties.entrySet())) {
			int left = e.getValue() - 1;
			if (left <= 0) {
				expired.add(e.getKey());
			} else {
				d.penalties.put(e.getKey(), left);
			}
		}
		for (String id : expired) {
			d.penalties.remove(id);
			if (Penalty.SCAR.id.equals(id)) {
				removeScar(p);
			}
		}
		fx(p, Fx.SAFE, expired.size());
		particles(p, "minecraft:totem_of_undying", 1.0, 0.5, 0.35, 40);
		sound(p, "minecraft:entity.player.levelup", 0.9f, 0.9f);
		if (expired.isEmpty()) {
			notice(p, "Safely back in the Overworld!", GREEN);
		} else {
			notice(p, "Safely back! " + expired.size() + (expired.size() == 1 ? " penalty expired." : " penalties expired."), GREEN);
		}
		DataStore.save();
	}

	private static void collapse(ServerPlayer p, PlayerData d) {
		d.collapsing = true;
		d.collapseRetry = 0;
		d.collapses++;
		d.pendingOffer = Penalty.rollOffer();
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
			if (d.has(Penalty.SCAR)) {
				applyScar(newPlayer);
			}
			if (d.respawnNotice != null) {
				notice(newPlayer, d.respawnNotice, ORANGE);
				d.respawnNotice = null;
			}
			sendOffer(newPlayer, d);
			particles(newPlayer, "minecraft:reverse_portal", 1.0, 0.6, 0.15, 60);
			sound(newPlayer, "minecraft:block.beacon.deactivate", 1.0f, 0.8f);
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
		if (d.has(Penalty.SCAR)) {
			applyScar(p);
		}
		sendState(p, d);
		sendOffer(p, d);
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
			added = Math.min(space, Config.TETHER_ITEM_CHARGE * (d.has(Penalty.DEBT) ? Config.DEBT_FACTOR : 1.0));
			stack.shrink(1);
		} else {
			double per = (NETHER.equals(type) ? ChargeItems.NETHER : ChargeItems.END).get(item);
			if (d.has(Penalty.DEBT)) {
				per *= Config.DEBT_FACTOR;
			}
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

		switch (pen) {
			case ACCELERATED_DRAIN, DEBT -> d.penalties.put(pen.id, Config.PENALTY_RUNS);
			case SCAR -> {
				d.penalties.put(pen.id, Config.PENALTY_RUNS);
				applyScar(p);
			}
			case LEAKY_TANK -> d.leakPending = true;
			case MOURNING -> {
				String u = p.getStringUUID();
				run("effect give " + u + " minecraft:weakness 300 0 true");
				run("effect give " + u + " minecraft:hunger 300 1 true");
			}
		}
		notice(p, "Penalty chosen: " + pen.title, ORANGE);
		fx(p, Fx.PENALTY, 0);
		particles(p, "minecraft:soul", 1.0, 0.5, 0.05, 25);
		sound(p, "minecraft:block.anvil.use", 0.8f, 0.7f);
		sendState(p, d);
		DataStore.save();
	}

	private static void applyScar(ServerPlayer p) {
		String u = p.getStringUUID();
		run("attribute " + u + " minecraft:max_health modifier remove extremedimensions:scar");
		run("attribute " + u + " minecraft:max_health modifier add extremedimensions:scar -" + Config.SCAR_HEALTH + " add_value");
	}

	private static void removeScar(ServerPlayer p) {
		run("attribute " + p.getStringUUID() + " minecraft:max_health modifier remove extremedimensions:scar");
	}
}
