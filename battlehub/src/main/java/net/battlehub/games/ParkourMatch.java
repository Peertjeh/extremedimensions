package net.battlehub.games;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.battlehub.arena.Arena;
import net.battlehub.arena.Blueprint;
import net.battlehub.arena.Theme;
import net.battlehub.arena.layout.PartyLayouts;
import net.battlehub.core.GameMode;
import net.battlehub.core.ParkourCourse;
import net.battlehub.core.ParkourCourse.Difficulty;
import net.battlehub.core.ParkourRace;
import net.battlehub.core.PlayerProfile;
import net.battlehub.data.PlayerStore;
import net.battlehub.lang.Lang;
import net.battlehub.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/**
 * Parkour Race: everyone runs the same course, generated new for every race, in the difficulty the lobby voted for
 * (easy, medium, hard, extreme or random). Gold platforms are checkpoints; falling puts you back on your last one.
 * Ranked by finish time; the first to finish wins. Alone it is practice against your own record.
 */
public final class ParkourMatch extends PartyMatch {
	public final Difficulty difficulty;
	private List<ParkourCourse.Platform> course;
	private ParkourRace race;
	private int shift;
	private int raceStart;
	private final Map<UUID, Integer> lastPlatform = new HashMap<>();

	public ParkourMatch(int id, MinecraftServer server, List<ServerPlayer> players, String vote) {
		super(id, GameMode.PARKOUR, server, players);
		this.difficulty = Difficulty.parse(vote, new java.util.Random(random.nextLong()));
	}

	public ParkourMatch(int id, MinecraftServer server, List<ServerPlayer> players) {
		this(id, server, players, "");
	}

	private static String cap(Difficulty d) {
		String id = d.id();
		return Character.toUpperCase(id.charAt(0)) + id.substring(1);
	}

	@Override
	protected Blueprint design(Theme theme) {
		int jumps = (int) Math.max(8, Math.min(200, game().setting("jumps" + cap(difficulty), difficulty.defaultJumps)));
		course = ParkourCourse.generate(random.nextLong(), difficulty, jumps, difficulty.checkpointEvery, 14);
		shift = -(course.get(course.size() - 1).x() / 2);
		return PartyLayouts.parkour(theme, random, course);
	}

	@Override
	protected int minimumToStart() {
		return 1;
	}

	/** Coins only for the harder courses (settings coinsEasy, coinsMedium, ...: share of the reward, 0 = none). */
	@Override
	public double coinFactor() {
		double fallback = difficulty == Difficulty.EASY || difficulty == Difficulty.MEDIUM ? 0 : 1;
		return Math.max(0, game().setting("coins" + cap(difficulty), fallback));
	}

	@Override
	public String noCoinsKey() {
		return "parkour.no_coins";
	}

	/** Longer courses get more time: easy x0.75, medium x1, hard x1.4, extreme x1.8 of the configured limit. */
	@Override
	protected int timeLimitTicks() {
		double factor = switch (difficulty) {
			case EASY -> 0.75;
			case MEDIUM -> 1.0;
			case HARD -> 1.4;
			case EXTREME -> 1.8;
		};
		return (int) (super.timeLimitTicks() * factor);
	}

	@Override
	protected boolean damageAllowed(ServerPlayer victim, DamageSource source, ServerPlayer attacker) {
		return false;
	}

	@Override
	protected void onGo() {
		raceStart = runTicks;
		race = new ParkourRace(course.get(course.size() - 1).checkpoint(), runTicks);
		int jumps = course.size() - 1;
		forEveryone(p -> Msg.send(p, "parkour.start", Lang.tr(p, "parkour.difficulty." + difficulty.id()), jumps, race.finishIndex() - 1));
	}

	@Override
	public net.battlehub.core.BotStyle botStyle() {
		return net.battlehub.core.BotStyle.PARKOUR;
	}

	/** The platform a player stands on (index in the course), or -1. */
	public int platformIndex(ServerPlayer p) {
		return platformUnder(p);
	}

	/** The last checkpoint platform a player reached, or -1 (still at the start). */
	public int lastPlatform(UUID id) {
		return lastPlatform.getOrDefault(id, -1);
	}

	/** Index in {@link #course} of the platform the player stands on, or -1. */
	private int platformUnder(ServerPlayer p) {
		if (!p.onGround()) {
			return -1;
		}
		int ox = arena.origin().getX();
		int oy = arena.origin().getY();
		int oz = arena.origin().getZ();
		for (int i = 0; i < course.size(); i++) {
			ParkourCourse.Platform pl = course.get(i);
			double cx = ox + pl.x() + shift + 0.5;
			double cz = oz + pl.z() + 0.5;
			double h = pl.half() + 0.8;
			if (Math.abs(p.getX() - cx) <= h && Math.abs(p.getZ() - cz) <= h && Math.abs(p.getY() - (oy + pl.standY())) < 0.6) {
				return i;
			}
		}
		return -1;
	}

	@Override
	protected void tickGame() {
		if (race == null) {
			return;
		}
		for (UUID id : alive) {
			ServerPlayer p = player(id);
			if (p == null || race.hasFinished(id)) continue;
			int idx = platformUnder(p);
			if (idx < 0) continue;
			ParkourCourse.Platform pl = course.get(idx);
			if (pl.checkpoint() > 0) {
				ParkourRace.Reach r = race.reach(id, pl.checkpoint(), runTicks);
				if (r == ParkourRace.Reach.CHECKPOINT) {
					lastPlatform.put(id, idx);
					Msg.send(p, "parkour.checkpoint", pl.checkpoint(), race.finishIndex() - 1, time(runTicks - raceStart));
					Msg.sound(p, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
				} else if (r == ParkourRace.Reach.FINISH) {
					lastPlatform.put(id, idx);
					onFinish(p);
				}
			}
		}
		if (runTicks % 10 == 0) {
			for (UUID id : alive) {
				ServerPlayer p = player(id);
				if (p == null) continue;
				if (race.hasFinished(id)) {
					Msg.actionBar(p, "parkour.actionbar_done", time(race.time(id)));
				} else {
					Msg.actionBar(p, "parkour.actionbar", time(runTicks - raceStart), race.checkpoint(id), race.finishIndex() - 1);
				}
			}
		}
		List<UUID> stillHere = new ArrayList<>();
		for (UUID id : alive) {
			if (!left.contains(id)) stillHere.add(id);
		}
		if (race.allFinished(stillHere)) {
			finishRace();
		}
	}

	public String bestTimeKey() {
		return "parkour_" + difficulty.id();
	}

	private void onFinish(ServerPlayer p) {
		long ticks = race.time(p.getUUID());
		int place = race.finishers();
		forEveryone(o -> Msg.send(o, "parkour.finished", name(p.getUUID()), time(ticks), place));
		Msg.title(p, Lang.tr(p, "parkour.finish_title", place), Lang.tr(p, "parkour.finish_sub", time(ticks)), 5, 50, 10);
		Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 1f, 1.2f);
		PlayerProfile prof = PlayerStore.get(p.getUUID());
		long ms = ticks * 50;
		Long best = prof.bestTimes.get(bestTimeKey());
		if (best == null || ms < best) {
			prof.bestTimes.put(bestTimeKey(), ms);
			Msg.send(p, "parkour.best", time(ticks), Lang.tr(p, "parkour.difficulty." + difficulty.id()));
		}
	}

	@Override
	protected void fellOut(ServerPlayer p) {
		Integer idx = lastPlatform.get(p.getUUID());
		Arena.Point pt;
		if (idx == null) {
			pt = spawnOf.getOrDefault(p.getUUID(), arena.spawn(0));
		} else {
			Vec3 top = platformTop(idx);
			pt = new Arena.Point(top, -90, 0);
		}
		teleport(p, pt);
		Msg.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.5f);
	}

	@Override
	protected void checkEnd() {
		// Nobody is eliminated in a race; it ends when everybody finished, left, or the time is up.
		if (alive.isEmpty()) {
			finish(List.of());
		}
	}

	private void finishRace() {
		List<UUID> ranking = race.ranking(new ArrayList<>(participants));
		UUID first = ranking.isEmpty() ? null : ranking.get(0);
		// Alone, only finishing counts as a win; in a race the furthest player wins if nobody finished
		boolean solo = participants.size() == 1;
		boolean won = first != null && (race.hasFinished(first) || (!solo && race.checkpoint(first) > 0));
		for (int i = 0; i < ranking.size(); i++) {
			UUID id = ranking.get(i);
			int place = i + 1;
			String t = race.hasFinished(id) ? time(race.time(id)) : "-";
			forEveryone(o -> Msg.send(o, "parkour.rank", place, name(id), t, race.checkpoint(id)));
		}
		finish(won ? List.of(first) : List.of());
	}

	@Override
	protected void onTimeUp() {
		finishRace();
	}

	static String time(long ticks) {
		long ms = ticks * 50;
		long s = ms / 1000;
		return String.format(java.util.Locale.ROOT, "%d:%02d.%d", s / 60, s % 60, (ms % 1000) / 100);
	}

	public ParkourRace race() {
		return race;
	}

	public List<ParkourCourse.Platform> course() {
		return course;
	}

	/** Where a player stands on a course platform (world position). */
	public Vec3 platformTop(int index) {
		ParkourCourse.Platform pl = course.get(index);
		return new Vec3(arena.origin().getX() + pl.x() + shift + 0.5, arena.origin().getY() + pl.standY(), arena.origin().getZ() + pl.z() + 0.5);
	}

	@Override
	public Component describe(ServerPlayer viewer) {
		return Lang.tr(viewer, "parkour.describe", super.describe(viewer), Lang.tr(viewer, "parkour.difficulty." + difficulty.id()));
	}

	@Override
	public Component status(ServerPlayer viewer) {
		return Lang.tr(viewer, "parkour.status", race == null ? 0 : race.finishers(), participants.size());
	}
}
