package com.extremedimensions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Alle spelregels aan serverkant: charge-drain, Dimensional Collapse, poort-blokkade,
 * het altaar en de penalty's.
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

	// ------------------------------------------------------------------ registratie

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> DataStore.save());
		ServerTickEvents.END_SERVER_TICK.register(ServerLogic::tick);

		ServerPlayerEvents.JOIN.register(ServerLogic::onJoin);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> DataStore.save());
		ServerPlayerEvents.AFTER_RESPAWN.register(ServerLogic::onRespawn);

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof ServerPlayer p)) {
				return;
			}
			if (isPaused(p)) {
				return;
			}
			PlayerData d = DataStore.get(p.getUUID());
			String dim = entity.level().dimension().identifier().getPath();
			// Normaal doodgaan in Nether/End (geen Collapse): je tank loopt leeg.
			if (isCharged(dim) && !d.collapsing) {
				setCharge(d, dim, 0);
				d.inRun = false;
				d.respawnNotice = "Je stierf in de " + dimName(dim) + ": je tank is leeggelopen.";
			}
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (hand != InteractionHand.MAIN_HAND || isPaused(sp)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			if (!level.getBlockState(pos).is(Blocks.LODESTONE)) {
				return InteractionResult.PASS;
			}
			return useAltar(sp);
		});
	}

	// ------------------------------------------------------------------ helpers

	/** In een wereld van BattleHub (of een andere mod uit Config.PAUSED_NAMESPACES)? */
	static boolean isPaused(ServerPlayer p) {
		return Config.PAUSED_NAMESPACES.contains(p.level().dimension().identifier().getNamespace());
	}

	/**
	 * Pauze aan of uit bij het wisselen van wereld. In de pauze telt de laatste echte dimensie gewoon door
	 * (d.dim verandert niet), dus terug in de Nether loopt dezelfde expeditie verder.
	 */
	private static boolean handlePause(ServerPlayer p, PlayerData d) {
		boolean paused = isPaused(p);
		if (paused && !d.paused) {
			d.paused = true;
			if (d.has(Penalty.SCAR)) {
				removeScar(p);
			}
		} else if (!paused && d.paused) {
			d.paused = false;
			if (d.has(Penalty.SCAR)) {
				applyScar(p);
			}
			sendState(p, d);
			sendOffer(p, d);
		}
		return paused;
	}

	static String dimOf(ServerPlayer p) {
		return p.level().dimension().identifier().getPath();
	}

	static boolean isCharged(String dim) {
		return NETHER.equals(dim) || END.equals(dim);
	}

	static String dimName(String dim) {
		return NETHER.equals(dim) ? "Nether" : "End";
	}

	static double chargeOf(PlayerData d, String dim) {
		return NETHER.equals(dim) ? d.nether : d.end;
	}

	static void setCharge(PlayerData d, String dim, double v) {
		if (NETHER.equals(dim)) {
			d.nether = Math.max(0, v);
		} else if (END.equals(dim)) {
			d.end = Math.max(0, v);
		}
	}

	static void run(String command) {
		if (server == null) {
			return;
		}
		try {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
		} catch (Exception e) {
			ExtremeDimensions.LOGGER.warn("Commando mislukt: {}", command, e);
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

	static void sound(ServerPlayer p, String id, float vol, float pitch) {
		at(p, String.format(Locale.ROOT, "playsound %s master @a ~ ~ ~ %.2f %.2f", id, vol, pitch));
	}

	static void notice(ServerPlayer p, String text, int color) {
		ServerPlayNetworking.send(p, new ModPackets.NoticePayload(text, color));
	}

	static void sendState(ServerPlayer p, PlayerData d) {
		int dimCode = NETHER.equals(d.dim) ? 1 : END.equals(d.dim) ? 2 : 0;
		StringBuilder pen = new StringBuilder();
		for (Map.Entry<String, Integer> e : d.penalties.entrySet()) {
			if (pen.length() > 0) {
				pen.append(';');
			}
			pen.append(e.getKey()).append(':').append(e.getValue());
		}
		ServerPlayNetworking.send(p, new ModPackets.StatePayload(
				(int) Math.ceil(d.nether), (int) Math.ceil(d.end), dimCode,
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
			if (handlePause(p, d)) {
				continue;
			}
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
				if (isCharged(dim)) {
					if (chargeOf(d, dim) <= 0 && !d.collapsing) {
						bounce(p, d, dim);
					} else {
						d.inRun = true;
						d.runTime = 0;
						d.lastWarn = Integer.MAX_VALUE;
						d.lastBeat = Integer.MAX_VALUE;
						notice(p, dimName(dim) + "-expeditie gestart: " + fmt(chargeOf(d, dim)), AQUA);
						fx(p, NETHER.equals(dim) ? Fx.START_NETHER : Fx.START_END, 0);
						if (NETHER.equals(dim)) {
							particles(p, "minecraft:flame", 1.0, 0.6, 0.06, 60);
							sound(p, "minecraft:block.respawn_anchor.charge", 1.0f, 0.8f);
						} else {
							particles(p, "minecraft:reverse_portal", 1.0, 0.6, 0.2, 80);
							sound(p, "minecraft:block.end_portal_frame.fill", 1.0f, 0.7f);
						}
					}
				} else if (OVERWORLD.equals(dim) && isCharged(from) && d.inRun && !d.collapsing) {
					completeRun(p, d);
				}
			}

			// 2) laatste Overworld-positie onthouden (voor de poort-blokkade)
			if (OVERWORLD.equals(dim) && !d.collapsing) {
				d.lastX = p.getX();
				d.lastY = p.getY();
				d.lastZ = p.getZ();
				d.hasLast = true;
			}

			// 3) charge laten leeglopen in Nether/End
			if (isCharged(dim) && d.inRun && !d.collapsing) {
				double cur = Math.max(0, chargeOf(d, dim) - d.drainMultiplier() / 20.0);
				setCharge(d, dim, cur);
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
	}

	private static void warn(ServerPlayer p, PlayerData d, int secs) {
		for (int t : new int[] {60, 30, 10}) {
			if (secs <= t && d.lastWarn > t) {
				notice(p, "Nog " + t + " seconden charge!", RED);
				sound(p, "minecraft:block.bell.use", 0.7f, 0.6f);
				particles(p, "minecraft:smoke", 1.0, 0.5, 0.02, 25);
				d.lastWarn = secs;
				return;
			}
		}
	}

	// ------------------------------------------------------------------ poort-blokkade

	/** Je ging een dimensie in zonder charge: de poort is slapend, we sturen je terug. */
	private static void bounce(ServerPlayer p, PlayerData d, String dim) {
		d.inRun = false;
		d.grace = Config.BOUNCE_GRACE_TICKS;
		notice(p, "De poort is slapend. Laad je " + dimName(dim) + " Charge op bij het altaar.", ORANGE);
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
			notice(p, "Veilig terug in de Overworld!", GREEN);
		} else {
			notice(p, "Veilig terug! " + expired.size() + " penalty verlopen.", GREEN);
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
		if (isPaused(newPlayer)) {
			return;
		}
		if (!alive) {
			// Altijd terug naar de Overworld, ook met een bed/anchor in de Nether of End.
			if (!OVERWORLD.equals(dimOf(newPlayer))) {
				sendToOverworldSpawn(newPlayer);
			}
			d.collapsing = false;
			d.collapseRetry = 0;
			d.inRun = false;
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
		d.collapsing = false;
		d.collapseRetry = 0;
		if (isPaused(p)) {
			// Binnenkomen in BattleHub: niets van Extreme Dimensions tot je eruit gaat
			d.paused = true;
			if (d.has(Penalty.SCAR)) {
				removeScar(p);
			}
			return;
		}
		d.paused = false;
		d.dim = dimOf(p);
		d.collapsing = false;
		d.collapseRetry = 0;
		d.inRun = isCharged(d.dim);
		d.lastWarn = Integer.MAX_VALUE;
		if (d.has(Penalty.SCAR)) {
			applyScar(p);
		}
		sendState(p, d);
		sendOffer(p, d);
	}

	// ------------------------------------------------------------------ altaar

	private static InteractionResult useAltar(ServerPlayer p) {
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

		// Ember Tether / Void Tether: grote lading in een keer
		if (item == ModItems.EMBER_TETHER || item == ModItems.VOID_TETHER) {
			boolean tn = item == ModItems.EMBER_TETHER;
			double cur = tn ? d.nether : d.end;
			double space2 = Config.MAX_CHARGE - cur;
			String tname = tn ? "Nether" : "End";
			if (space2 < 1) {
				notice(p, "Je " + tname + "-tank is vol.", ORANGE);
				return InteractionResult.SUCCESS;
			}
			double gain = Math.min(space2, Config.TETHER_ITEM_CHARGE * (d.has(Penalty.DEBT) ? Config.DEBT_FACTOR : 1.0));
			stack.shrink(1);
			if (tn) {
				d.nether += gain;
			} else {
				d.end += gain;
			}
			notice(p, tname + " Tether gebonden: +" + fmt(gain) + " Charge", AQUA);
			fx(p, tn ? Fx.DEPOSIT_NETHER : Fx.DEPOSIT_END, (int) Math.round(gain));
			particles(p, tn ? "minecraft:soul_fire_flame" : "minecraft:end_rod", 1.2, 0.7, 0.08, 60);
			particles(p, "minecraft:enchant", 1.5, 0.9, 0.5, 50);
			sound(p, "minecraft:item.lodestone_compass.lock", 1.0f, 0.8f);
			sound(p, tn ? "minecraft:block.respawn_anchor.charge" : "minecraft:block.end_portal_frame.fill", 1.0f, 1.0f);
			sendState(p, d);
			return InteractionResult.SUCCESS;
		}

		boolean nether = ChargeItems.NETHER.containsKey(item);
		boolean end = ChargeItems.END.containsKey(item);
		if (!nether && !end) {
			return InteractionResult.PASS; // gewone enchanting table
		}

		double per = nether ? ChargeItems.NETHER.get(item) : ChargeItems.END.get(item);
		if (d.has(Penalty.DEBT)) {
			per *= Config.DEBT_FACTOR;
		}
		double current = nether ? d.nether : d.end;
		double space = Config.MAX_CHARGE - current;
		String name = nether ? "Nether" : "End";
		if (space < 1) {
			notice(p, "Je " + name + "-tank is vol.", ORANGE);
			return InteractionResult.SUCCESS;
		}

		int n = (int) Math.min(stack.getCount(), Math.ceil(space / per));
		double added = Math.min(space, n * per);
		stack.shrink(n);
		if (nether) {
			d.nether += added;
		} else {
			d.end += added;
		}
		notice(p, "+" + fmt(added) + " " + name + " Charge  (tank: " + fmt(nether ? d.nether : d.end) + ")", AQUA);
		fx(p, nether ? Fx.DEPOSIT_NETHER : Fx.DEPOSIT_END, (int) Math.round(added));
		particles(p, nether ? "minecraft:flame" : "minecraft:portal", 1.2, 0.5, nether ? 0.04 : 0.6, 30);
		particles(p, "minecraft:enchant", 1.5, 0.8, 0.4, 25);
		sound(p, nether ? "minecraft:block.respawn_anchor.charge" : "minecraft:block.end_portal_frame.fill", 0.8f, 1.2f);
		sendState(p, d);
		return InteractionResult.SUCCESS;
	}

	// ------------------------------------------------------------------ penalty kiezen

	public static void onChoose(ServerPlayer p, String id) {
		PlayerData d = DataStore.get(p.getUUID());
		// In BattleHub geen penalty's; de keuze komt terug als je eruit gaat
		if (isPaused(p) || !d.pendingOffer.contains(id)) {
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
			case LEAKY_TANK -> {
				d.nether *= (1.0 - Config.LEAK_FRACTION);
				d.end *= (1.0 - Config.LEAK_FRACTION);
			}
			case MOURNING -> {
				String u = p.getStringUUID();
				run("effect give " + u + " minecraft:weakness 300 0 true");
				run("effect give " + u + " minecraft:hunger 300 1 true");
			}
		}
		notice(p, "Penalty gekozen: " + pen.title, ORANGE);
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
