package net.battlehub.hub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.BattleHub;
import net.battlehub.bot.Bots;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.BotMath;
import net.battlehub.core.DuelFormat;
import net.battlehub.core.GameMode;
import net.battlehub.gui.Icons;
import net.battlehub.gui.ModeMenu;
import net.battlehub.gui.PartyGameMenu;
import net.battlehub.gui.PartyGamesMenu;
import net.battlehub.gui.PracticeMenu;
import net.battlehub.gui.PvpKitMenu;
import net.battlehub.gui.PvpMenu;
import net.battlehub.gui.RankedMenu;
import net.battlehub.lang.Lang;
import net.battlehub.match.Match;
import net.battlehub.match.Matches;
import net.battlehub.mixin.InteractionAccessor;
import net.battlehub.progression.FeaturedMode;
import net.battlehub.queue.Parties;
import net.battlehub.queue.Queues;
import net.battlehub.queue.TeamFights;
import net.battlehub.util.Ids;
import net.battlehub.util.Msg;
import net.battlehub.util.Tags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

/**
 * Characters on the lobby plaza, one per game or menu, with a floating label: the name, how many people play
 * it right now and a star on the mode of the day. Body and label exist only on the clients (in each player's
 * language, the head turning towards whoever comes close); an invisible click box in the world catches the clicks.
 */
public final class HubNpcs {
	private HubNpcs() {}

	public static final String TAG = "battlehub_npc";
	private static final double LABEL_HEIGHT = 2.05;
	private static final int LOOK_RANGE = 8;

	private record Npc(String id, String action, ItemStack item, Vec3 pos, float yaw, ClientEntities.Handle body,
			ClientEntities.Handle label) {}

	private static final List<Npc> NPCS = new ArrayList<>();
	private static final Map<Npc, Interaction> HITBOXES = new HashMap<>();
	private static final HubViewers VIEWERS = new HubViewers();
	private static final Map<UUID, Integer> LAST_CLICK = new HashMap<>();
	private static EntityType<?> bodyType;
	private static HubConfig builtFor;
	private static int ticks;

	public static void tick(MinecraftServer server) {
		ticks++;
		HubConfig cfg = ConfigManager.config();
		ServerLevel hub = server.getLevel(HubWorld.KEY);
		if (cfg != builtFor) {
			VIEWERS.clear(HubNpcs::remove);
			removeHitboxes();
			build(cfg, hub);
		}
		boolean active = hub != null && HubWorld.ready() && !NPCS.isEmpty();
		List<HubViewers.Viewer> fresh = VIEWERS.update(server, hub, active, HubNpcs::remove);
		if (hub != null && ticks % 100 == 0) {
			sweep(hub, active);
		}
		if (!active || VIEWERS.isEmpty()) {
			return;
		}
		boolean second = ticks % 20 == 0;
		for (HubViewers.Viewer v : VIEWERS.all()) {
			boolean isNew = fresh.contains(v);
			if (isNew) {
				spawn(v.player);
			}
			if (isNew || second) {
				labels(v);
			}
			if (ticks % 4 == 0) {
				lookAt(v.player);
			}
		}
	}

	public static void rebuild() {
		builtFor = null;
	}

	public static void clear() {
		NPCS.clear();
		HITBOXES.clear();
		VIEWERS.forget();
		LAST_CLICK.clear();
		builtFor = null;
	}

	private static void build(HubConfig cfg, ServerLevel hub) {
		NPCS.clear();
		builtFor = cfg;
		if (hub == null || !cfg.npcs.enabled) {
			return;
		}
		bodyType = BuiltInRegistries.ENTITY_TYPE.containsKey(Ids.id("minecraft:mannequin"))
				? ClientEntities.type("minecraft:mannequin") : ClientEntities.type("minecraft:armor_stand");
		EntityType<?> text = ClientEntities.type("minecraft:text_display");
		BlockPos o = HubWorld.origin();
		Vec3 spawn = HubWorld.defaultSpawn();
		for (HubConfig.Npc n : cfg.npcs.list) {
			double a = Math.toRadians(n.angle);
			double x = o.getX() + 0.5 + Math.cos(a) * cfg.npcs.radius;
			double z = o.getZ() + 0.5 + Math.sin(a) * cfg.npcs.radius;
			Vec3 pos = new Vec3(x, o.getY() + 1, z);
			float yaw = BotMath.yaw(spawn.x - x, spawn.z - z);
			ClientEntities.Handle body = ClientEntities.handle(hub, bodyType);
			ClientEntities.Handle label = ClientEntities.handle(hub, text);
			if (body == null || label == null) {
				BattleHub.LOGGER.warn("Lobby characters: could not create entities");
				return;
			}
			ItemStack item = Ids.isItem(n.item) ? new ItemStack(Ids.item(n.item)) : ItemStack.EMPTY;
			NPCS.add(new Npc(n.id, n.action, item, pos, yaw, body, label));
		}
	}

	// ------------------------------------------------------------------ click boxes (real entities)

	/** Click boxes are real (clicks need them); old ones from before a restart are removed. */
	private static void sweep(ServerLevel hub, boolean active) {
		List<Entity> ours = new ArrayList<>(HITBOXES.values());
		for (Interaction e : hub.getEntities(EntityTypeTest.forClass(Interaction.class), HubNpcs::stray)) {
			if (!ours.contains(e)) {
				e.discard();
			}
		}
		if (!active || VIEWERS.isEmpty()) {
			return;
		}
		for (Npc n : NPCS) {
			Interaction box = HITBOXES.get(n);
			if (box != null && !box.isRemoved()) continue;
			if (!hub.isLoaded(BlockPos.containing(n.pos))) continue;
			EntityType<?> type = ClientEntities.type("minecraft:interaction");
			Entity e = type == null ? null : type.create(hub, EntitySpawnReason.COMMAND);
			if (!(e instanceof Interaction i)) continue;
			((InteractionAccessor) i).battlehub$setWidth(1.0f);
			((InteractionAccessor) i).battlehub$setHeight(2.1f);
			i.snapTo(n.pos.x, n.pos.y, n.pos.z);
			i.addTag(TAG);
			hub.addFreshEntity(i);
			HITBOXES.put(n, i);
		}
	}

	/** A click box left from before a restart: it has our tag (or, if tags cannot be read, stands where a character stands). */
	private static boolean stray(Interaction e) {
		Boolean tagged = Tags.has(e, TAG);
		if (tagged != null) {
			return tagged;
		}
		for (Npc n : NPCS) {
			if (e.position().distanceToSqr(n.pos) < 0.25) return true;
		}
		return false;
	}

	private static void removeHitboxes() {
		for (Interaction i : HITBOXES.values()) {
			i.discard();
		}
		HITBOXES.clear();
	}

	/** A click on a lobby character; true when it was one (the click is used up). */
	public static boolean click(ServerPlayer p, Entity e) {
		if (!(e instanceof Interaction)) {
			return false;
		}
		Npc npc = null;
		for (Map.Entry<Npc, Interaction> en : HITBOXES.entrySet()) {
			if (en.getValue() == e) npc = en.getKey();
		}
		if (npc == null && !stray((Interaction) e)) {
			return false;
		}
		// A right-click sends both a use and an attack sometimes: one action per click
		Integer last = LAST_CLICK.get(p.getUUID());
		if (npc == null || (last != null && ticks - last < 6)) {
			return true;
		}
		LAST_CLICK.put(p.getUUID(), ticks);
		Msg.sound(p, SoundEvents.UI_BUTTON_CLICK, 0.6f, 1.2f);
		run(p, npc.action);
		return true;
	}

	private static void run(ServerPlayer p, String action) {
		if (action.startsWith("menu:")) {
			switch (action.substring(5)) {
				case "pvp" -> new PvpMenu(p).open();
				case "ranked" -> new RankedMenu(p).open();
				case "party" -> new PartyGamesMenu(p).open();
				case "practice" -> new PracticeMenu(p).open();
				default -> new net.battlehub.gui.MainMenu(p).open();
			}
			return;
		}
		if (action.startsWith("game:")) {
			GameMode m = GameMode.byId(action.substring(5));
			if (m == null) {
				return;
			}
			if (m == GameMode.FFA || m.isTeamFight()) {
				new PvpKitMenu(p, m).open();
			} else if (m.isParty()) {
				new PartyGameMenu(p, m).open();
			} else {
				new ModeMenu(p, m, new net.battlehub.gui.ModeListMenu(p, m.isRanged() ? net.battlehub.gui.ModeListMenu.Kind.RANGED
						: net.battlehub.gui.ModeListMenu.Kind.MELEE)).open();
			}
		}
	}

	// ------------------------------------------------------------------ what the clients see

	private static void spawn(ServerPlayer p) {
		EntityType<?> text = ClientEntities.type("minecraft:text_display");
		for (Npc n : NPCS) {
			ClientEntities.add(p, n.body, bodyType, n.pos, n.yaw, n.yaw);
			if (!n.item.isEmpty()) {
				ClientEntities.holding(p, n.body.id(), n.item);
			}
			ClientEntities.add(p, n.label, text, n.pos.add(0, LABEL_HEIGHT, 0), 0f, 0f);
			ClientEntities.textStyle(p, n.label.id(), 200, 0x40000000, ClientEntities.SHADOW, 1.0f, ClientEntities.BILLBOARD_CENTER);
		}
	}

	private static void remove(ServerPlayer p) {
		int[] ids = new int[NPCS.size() * 2];
		int i = 0;
		for (Npc n : NPCS) {
			ids[i++] = n.body.id();
			ids[i++] = n.label.id();
		}
		ClientEntities.remove(p, ids);
	}

	private static void labels(HubViewers.Viewer v) {
		GameMode featured = FeaturedMode.today();
		for (Npc n : NPCS) {
			Component text = label(v.player, n, featured);
			if (!text.equals(v.sent.get(n.label.id()))) {
				v.sent.put(n.label.id(), text);
				ClientEntities.text(v.player, n.label.id(), text);
			}
		}
	}

	private static Component label(ServerPlayer p, Npc n, GameMode featured) {
		MutableComponent out = Component.empty();
		String key = "npc." + n.id;
		GameMode mode = n.action.startsWith("game:") ? GameMode.byId(n.action.substring(5)) : null;
		if (Lang.has(key)) {
			out.append(Lang.tr(p, key));
		} else if (mode != null) {
			out.append(Lang.tr(p, Icons.modeKey(mode)));
		} else {
			out.append(Component.literal(n.id));
		}
		int count = count(n.action);
		out.append("\n").append(count < 0 ? Lang.tr(p, "npc.practice_line") : Lang.tr(p, "npc.playing", count));
		out.append("\n").append(Lang.tr(p, "npc.click"));
		if (featured != null && covers(n, featured)) {
			out.append("\n").append(Lang.tr(p, "npc.featured", Lang.tr(p, Icons.modeKey(featured)), FeaturedMode.multiplierText()));
		}
		return out;
	}

	/** Does this character lead to the mode of the day (and no other character shows that mode itself)? */
	private static boolean covers(Npc n, GameMode featured) {
		if (n.action.equals("game:" + featured.id())) {
			return true;
		}
		for (Npc o : NPCS) {
			if (o.action.equals("game:" + featured.id())) return false;
		}
		return switch (n.action) {
			case "menu:pvp" -> featured == GameMode.FFA || featured.isTeamFight() || featured.isDuel();
			case "menu:party" -> featured.isParty();
			default -> false;
		};
	}

	/** People (not bots) waiting for or playing what this character leads to; -1 for practice. */
	private static int count(String action) {
		return switch (action) {
			case "menu:practice" -> -1;
			case "menu:pvp" -> {
				int n = people(GameMode.FFA);
				for (GameMode m : GameMode.meleeKits()) n += people(m);
				for (GameMode m : GameMode.teamFights()) n += people(m);
				yield n;
			}
			case "menu:ranked" -> {
				int n = 0;
				for (GameMode m : GameMode.duels()) {
					for (DuelFormat f : DuelFormat.values()) {
						for (UUID id : Queues.waitingIds(m, true, f)) if (!Bots.isBot(id)) n++;
					}
				}
				for (Match m : Matches.all()) {
					if (m.ranked && m.phase() != Match.Phase.CLOSED) n += playing(m);
				}
				yield n;
			}
			case "menu:party" -> {
				int n = 0;
				for (GameMode m : GameMode.partyMenu()) n += people(m);
				yield n;
			}
			default -> {
				GameMode m = action.startsWith("game:") ? GameMode.byId(action.substring(5)) : null;
				yield m == null ? 0 : people(m);
			}
		};
	}

	/** People waiting for a mode plus people playing it. */
	private static int people(GameMode mode) {
		int n = 0;
		if (mode.isParty()) {
			for (UUID id : Parties.members(mode)) if (!Bots.isBot(id)) n++;
		} else if (mode.isTeamFight()) {
			for (GameMode kit : GameMode.meleeKits()) {
				for (UUID id : TeamFights.waitingIds(mode, kit)) if (!Bots.isBot(id)) n++;
			}
		} else {
			for (DuelFormat f : DuelFormat.values()) {
				for (UUID id : Queues.waitingIds(mode, false, f)) if (!Bots.isBot(id)) n++;
			}
		}
		for (Match m : Matches.all()) {
			if (m.mode == mode && !m.ranked && m.phase() != Match.Phase.CLOSED) n += playing(m);
		}
		return n;
	}

	private static int playing(Match m) {
		int n = 0;
		for (UUID id : m.participants()) {
			if (!Bots.isBot(id) && !m.leftPlayers().contains(id)) n++;
		}
		return n;
	}

	/** The characters turn their head towards a player close by. */
	private static void lookAt(ServerPlayer p) {
		for (Npc n : NPCS) {
			double dx = p.getX() - n.pos.x;
			double dz = p.getZ() - n.pos.z;
			float yaw = dx * dx + dz * dz < LOOK_RANGE * LOOK_RANGE ? BotMath.yaw(dx, dz) : n.yaw;
			// Not further round than a neck can turn
			yaw = n.yaw + Math.max(-75f, Math.min(75f, BotMath.wrap(yaw - n.yaw)));
			if (n.body.entity() != null) {
				p.connection.send(new ClientboundRotateHeadPacket(n.body.entity(), (byte) Math.floor(yaw * 256f / 360f)));
			}
		}
	}

	// For the self-test

	public static int count() {
		return NPCS.size();
	}

	public static int hitboxes() {
		int n = 0;
		for (Interaction i : HITBOXES.values()) {
			if (!i.isRemoved()) n++;
		}
		return n;
	}

	/** The click box of a character by id, or null. */
	public static Interaction hitbox(String id) {
		for (Map.Entry<Npc, Interaction> e : HITBOXES.entrySet()) {
			if (e.getKey().id.equals(id)) return e.getValue();
		}
		return null;
	}

	public static boolean showing(UUID viewer) {
		return VIEWERS.get(viewer) != null;
	}

	/** The label a player was sent for a character, or "". */
	public static String label(UUID viewer, String id) {
		HubViewers.Viewer v = VIEWERS.get(viewer);
		if (v == null) return "";
		for (Npc n : NPCS) {
			if (n.id.equals(id)) {
				Component c = v.sent.get(n.label.id());
				return c == null ? "" : c.getString();
			}
		}
		return "";
	}

	public static java.util.Set<Integer> entityIds() {
		java.util.Set<Integer> s = new java.util.HashSet<>();
		for (Npc n : NPCS) {
			s.add(n.body.id());
			s.add(n.label.id());
		}
		return s;
	}
}
