package net.battlehub.social;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.reflect.TypeToken;

import net.battlehub.BattleHub;
import net.battlehub.config.ConfigManager;
import net.battlehub.config.HubConfig;
import net.battlehub.core.ClanRules;
import net.battlehub.core.PlayerProfile;
import net.battlehub.core.Progression;
import net.battlehub.core.Quests;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.progression.TitleService;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Clans: a name, a tag before every member's name, an owner, members, points from wins, and a weekly clan war
 * (the clan with the most points that week wins coins for every member). Stored in
 * {@code <world>/battlehub/clans.json}.
 */
public final class Clans {
	private Clans() {}

	public static final class Clan {
		public String name = "";
		public String tag = "";
		public String owner = "";
		public Set<String> members = new LinkedHashSet<>();
		public long created;
		public long points;
		public long weeklyPoints;
		public int warsWon;
	}

	private static final Map<String, Clan> CLANS = new LinkedHashMap<>();
	private static Path file;
	private static long week = -1;

	private static HubConfig.ClanSettings cfg() {
		return ConfigManager.config().clans;
	}

	public static void load(MinecraftServer server) {
		CLANS.clear();
		file = PlayerStore.worldDir(server).resolve("clans.json");
		week = Quests.week(LocalDate.now().toEpochDay());
		if (!Files.exists(file)) {
			return;
		}
		try {
			Map<String, Object> raw = ConfigManager.GSON.fromJson(Files.readString(file), new TypeToken<Map<String, Object>>() {}.getType());
			Object w = raw == null ? null : raw.get("week");
			if (w instanceof Number n) week = n.longValue();
			List<Clan> list = ConfigManager.GSON.fromJson(ConfigManager.GSON.toJson(raw == null ? List.of() : raw.get("clans")),
					new TypeToken<List<Clan>>() {}.getType());
			if (list != null) {
				for (Clan c : list) {
					if (c != null && c.name != null && !c.name.isBlank()) {
						if (c.members == null) c.members = new LinkedHashSet<>();
						CLANS.put(c.name.toLowerCase(Locale.ROOT), c);
					}
				}
			}
		} catch (Exception e) {
			BattleHub.LOGGER.error("Could not read {}: {}", file, e.getMessage());
		}
	}

	public static void save() {
		if (file == null) return;
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("week", week);
		out.put("clans", new ArrayList<>(CLANS.values()));
		ConfigManager.writeJson(file, ConfigManager.GSON.toJson(out));
	}

	public static Clan byName(String name) {
		return name == null ? null : CLANS.get(name.toLowerCase(Locale.ROOT));
	}

	public static Clan of(UUID id) {
		Clan c = byName(PlayerStore.get(id).clan);
		return c != null && c.members.contains(id.toString()) ? c : null;
	}

	public static List<Clan> all() {
		return new ArrayList<>(CLANS.values());
	}

	/** Clans by points (weekly or all time), highest first. */
	public static List<Clan> top(boolean weekly) {
		List<Clan> l = all();
		l.sort((a, b) -> Long.compare(weekly ? b.weeklyPoints : b.points, weekly ? a.weeklyPoints : a.points));
		return l;
	}

	private static void tell(MinecraftServer server, Clan c, String key, Object... args) {
		for (String m : c.members) {
			try {
				ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(m));
				if (p != null) Msg.send(p, key, args);
			} catch (IllegalArgumentException ignored) {
				// skip broken ids
			}
		}
	}

	public static void create(ServerPlayer p, String name, String tag) {
		PlayerProfile prof = PlayerStore.get(p);
		if (of(p.getUUID()) != null) {
			Msg.send(p, "clan.already_in");
			return;
		}
		if (!ClanRules.validName(name) || !ClanRules.validTag(tag)) {
			Msg.send(p, "clan.invalid");
			return;
		}
		if (byName(name) != null || CLANS.values().stream().anyMatch(c -> c.tag.equalsIgnoreCase(tag))) {
			Msg.send(p, "clan.taken");
			return;
		}
		if (prof.coins < cfg().createCost) {
			Msg.send(p, "clan.too_expensive", cfg().createCost);
			return;
		}
		prof.coins -= cfg().createCost;
		Clan c = new Clan();
		c.name = name;
		c.tag = tag.toUpperCase(Locale.ROOT);
		c.owner = p.getUUID().toString();
		c.members.add(p.getUUID().toString());
		c.created = System.currentTimeMillis();
		CLANS.put(name.toLowerCase(Locale.ROOT), c);
		prof.clan = name.toLowerCase(Locale.ROOT);
		PlayerStore.save(p.getUUID());
		save();
		Msg.send(p, "clan.created", c.name, c.tag, cfg().createCost);
		TitleService.apply(p);
	}

	public static void invite(ServerPlayer p, String target) {
		Clan c = of(p.getUUID());
		if (c == null || !c.owner.equals(p.getUUID().toString())) {
			Msg.send(p, "clan.not_owner");
			return;
		}
		PlayerProfile t = PlayerStore.find(target);
		if (t == null || net.battlehub.bot.Bots.isBot(UUID.fromString(t.uuid))) {
			Msg.send(p, "friend.unknown", target);
			return;
		}
		if (c.members.size() >= cfg().maxMembers) {
			Msg.send(p, "clan.full", cfg().maxMembers);
			return;
		}
		t.clanInvites.add(c.name.toLowerCase(Locale.ROOT));
		PlayerStore.save(UUID.fromString(t.uuid));
		Msg.send(p, "clan.invited", t.name);
		ServerPlayer o = Friends.online(p.level().getServer(), t);
		if (o != null) {
			Msg.send(o, "clan.invite", c.name, c.tag);
			Msg.raw(o, Component.empty().append(Component.literal("  ")).append(Lang.tr(o, "clan.join_button").withStyle(s -> s
					.withClickEvent(new ClickEvent.RunCommand("/clan join " + c.name)))));
			Msg.sound(o, SoundEvents.NOTE_BLOCK_BELL, 1f, 1f);
		}
	}

	public static void join(ServerPlayer p, String name) {
		PlayerProfile prof = PlayerStore.get(p);
		Clan c = byName(name);
		if (c == null || !prof.clanInvites.remove(c.name.toLowerCase(Locale.ROOT))) {
			Msg.send(p, "clan.no_invite", name);
			return;
		}
		if (of(p.getUUID()) != null) {
			Msg.send(p, "clan.already_in");
			return;
		}
		if (c.members.size() >= cfg().maxMembers) {
			Msg.send(p, "clan.full", cfg().maxMembers);
			return;
		}
		c.members.add(p.getUUID().toString());
		prof.clan = c.name.toLowerCase(Locale.ROOT);
		PlayerStore.save(p.getUUID());
		save();
		tell(p.level().getServer(), c, "clan.joined", p.getGameProfile().name(), c.name);
		TitleService.apply(p);
	}

	/** Leaves the clan; when the owner leaves, the oldest member takes over; an empty clan is removed. */
	public static void leave(ServerPlayer p) {
		Clan c = of(p.getUUID());
		if (c == null) {
			Msg.send(p, "clan.none");
			return;
		}
		removeMember(p.level().getServer(), c, p.getUUID());
		Msg.send(p, "clan.left", c.name);
		TitleService.apply(p);
	}

	private static void removeMember(MinecraftServer server, Clan c, UUID id) {
		c.members.remove(id.toString());
		PlayerProfile prof = PlayerStore.get(id);
		prof.clan = "";
		PlayerStore.save(id);
		if (c.members.isEmpty()) {
			CLANS.remove(c.name.toLowerCase(Locale.ROOT));
		} else if (c.owner.equals(id.toString())) {
			c.owner = c.members.iterator().next();
			tell(server, c, "clan.new_owner", PlayerStore.get(UUID.fromString(c.owner)).name);
		}
		save();
	}

	public static void kick(ServerPlayer owner, String target) {
		Clan c = of(owner.getUUID());
		if (c == null || !c.owner.equals(owner.getUUID().toString())) {
			Msg.send(owner, "clan.not_owner");
			return;
		}
		PlayerProfile t = PlayerStore.find(target);
		if (t == null || !c.members.contains(t.uuid) || t.uuid.equals(c.owner)) {
			Msg.send(owner, "clan.not_member", target);
			return;
		}
		MinecraftServer server = owner.level().getServer();
		removeMember(server, c, UUID.fromString(t.uuid));
		tell(server, c, "clan.kicked", t.name);
		ServerPlayer o = Friends.online(server, t);
		if (o != null) {
			Msg.send(o, "clan.you_kicked", c.name);
			TitleService.apply(o);
		}
	}

	public static void disband(ServerPlayer owner) {
		Clan c = of(owner.getUUID());
		if (c == null || !c.owner.equals(owner.getUUID().toString())) {
			Msg.send(owner, "clan.not_owner");
			return;
		}
		MinecraftServer server = owner.level().getServer();
		tell(server, c, "clan.disbanded", c.name);
		List<String> members = new ArrayList<>(c.members);
		CLANS.remove(c.name.toLowerCase(Locale.ROOT));
		for (String m : members) {
			UUID id = UUID.fromString(m);
			PlayerStore.get(id).clan = "";
			PlayerStore.save(id);
			ServerPlayer o = server.getPlayerList().getPlayer(id);
			if (o != null) TitleService.apply(o);
		}
		save();
	}

	public static void chat(ServerPlayer from, String message) {
		Clan c = of(from.getUUID());
		if (c == null) {
			Msg.send(from, "clan.none");
			return;
		}
		MinecraftServer server = from.level().getServer();
		for (String m : c.members) {
			ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(m));
			if (p != null) Msg.raw(p, Lang.tr(p, "clan.chat", c.tag, from.getGameProfile().name(), message));
		}
	}

	/** A member won a match: points for the clan (more for ranked). */
	public static void won(UUID id, boolean ranked) {
		Clan c = of(id);
		if (c == null) return;
		int pts = ClanRules.points(true, ranked);
		c.points += pts;
		c.weeklyPoints += pts;
	}

	/** Once a minute: a new week ends the clan war of the last one. */
	public static void tick(MinecraftServer server) {
		long now = Quests.week(LocalDate.now().toEpochDay());
		if (week < 0) {
			week = now;
			return;
		}
		if (now == week) return;
		week = now;
		Clan best = null;
		for (Clan c : CLANS.values()) {
			if (c.weeklyPoints > 0 && (best == null || c.weeklyPoints > best.weeklyPoints)) best = c;
		}
		if (best != null) {
			best.warsWon++;
			for (String m : best.members) {
				UUID id = UUID.fromString(m);
				Progression.addCoins(PlayerStore.get(id), cfg().warReward);
				PlayerStore.save(id);
			}
			Msg.broadcast(server, "clan.war_won", best.name, best.tag, best.weeklyPoints, cfg().warReward);
			net.battlehub.compat.Discord.post("discord.clan_war_won", best.name, best.tag, best.weeklyPoints);
		}
		for (Clan c : CLANS.values()) c.weeklyPoints = 0;
		save();
	}

	/** Self-test: pretend the week turned over and settle the clan war now. */
	public static void endWeekForTest(MinecraftServer server) {
		week = Quests.week(LocalDate.now().toEpochDay()) - 1;
		tick(server);
	}

	public static void clear() {
		CLANS.clear();
		file = null;
	}
}
