package com.extremedimensions.trial;

import java.util.ArrayList;
import java.util.List;

import com.extremedimensions.config.Config;
import com.extremedimensions.data.DataStore;
import com.extremedimensions.data.PlayerData;
import com.extremedimensions.penalty.PenaltyService;
import com.extremedimensions.progression.Advancements;
import com.extremedimensions.registry.ModItems;
import com.extremedimensions.relic.RelicService;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Rift Trials: every week (by the real calendar, so every world shares it) asks one thing of a visit to the Seam,
 * with a twist that makes it harder. A player takes the trial up at home; the next rift they step through starts it,
 * and only a safe return through the rift counts. Each player's best run goes on the world's scoreboard, and each
 * medal pays the first time it is reached in a week, so a trial can be run again for the board but not farmed.
 */
public final class TrialService {
	private TrialService() {}

	/** Tests pin the week here; null = the calendar. */
	public static Integer weekOverride = null;

	/** The current trial's number: weeks (of {@code trial_rotation_days}) since 1970, starting on Mondays, UTC. */
	public static int week() {
		if (weekOverride != null) {
			return weekOverride;
		}
		long day = Math.floorDiv(System.currentTimeMillis(), 86_400_000L);
		return (int) Math.floorDiv(day + 3, Math.max(1, Config.TRIAL_ROTATION_DAYS));
	}

	/** Seconds until the next trial begins. */
	public static long secondsLeft() {
		long nextDay = (week() + 1L) * Math.max(1, Config.TRIAL_ROTATION_DAYS) - 3;
		return Math.max(0, nextDay * 86_400L - System.currentTimeMillis() / 1000);
	}

	/** Every goal comes round once in five weeks; the twists shift against them, so pairs vary. */
	public static Trial.Kind kind(int week) {
		Trial.Kind[] all = Trial.Kind.values();
		return all[Math.floorMod(week * 3 + 1, all.length)];
	}

	public static Trial.Twist twist(int week) {
		Trial.Twist[] all = Trial.Twist.values();
		return all[Math.floorMod(week * 2 + Math.floorDiv(week, all.length), all.length)];
	}

	public static boolean unlocked() {
		return switch (Config.TRIAL_UNLOCK) {
			case 0 -> true;
			case 1 -> DataStore.sealBroken();
			default -> DataStore.starvedSlain();
		};
	}

	/** Takes the week's trial up for the next visit to the Seam (or lets it go). Returns the refusal key, or null. */
	public static String arm(ServerPlayer p, boolean arm) {
		PlayerData d = DataStore.get(p.getUUID());
		if (arm && !unlocked()) {
			return "gui.extremedimensions.trial.locked";
		}
		if (d.inSeam) {
			return "gui.extremedimensions.trial.in_seam";
		}
		d.trialArmed = arm;
		DataStore.markDirty();
		if (arm) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.trial.armed", Component.translatable(kind(week()).nameKey())), true);
			WorldFx.soundSelf(p, "minecraft:block.amethyst_block.resonate", 0.8f, 0.8f);
		} else {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.trial.unarmed"), true);
		}
		return null;
	}

	/** Through a rift with the trial taken up: it begins. Called once the visit's time has been set. */
	public static void begin(ServerPlayer p, PlayerData d, int x, int z) {
		d.trial = null;
		if (!d.trialArmed || !unlocked()) {
			return;
		}
		d.trialArmed = false;
		roll();
		int w = week();
		TrialRun run = new TrialRun();
		run.week = w;
		run.kind = kind(w).id;
		run.twist = twist(w).id;
		run.fromX = x + 0.5;
		run.fromZ = z + 0.5;
		d.trial = run;
		Trial.Twist twist = twist(w);
		if (twist == Trial.Twist.SHORT) {
			d.seamMax = Math.max(30, Math.floor(d.seamMax * Math.max(0.1, Math.min(1, Config.TRIAL_SHORT_FACTOR))));
			d.seamLeft = d.seamMax;
		}
		WorldFx.title(p, "gui.extremedimensions.trial.title", "gold", kind(w).nameKey(), null);
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.trial.begins", Component.translatable(kind(w).goalKey()),
				Component.translatable(twist.nameKey()), Component.translatable(twist.descKey())));
		WorldFx.soundSelf(p, "minecraft:event.raid.horn", 0.5f, 1.4f);
		DataStore.markDirty();
	}

	/** Progress on the trial under way, if it is that kind of trial. */
	public static void progress(ServerPlayer p, Trial.Kind kind, int n) {
		PlayerData d = DataStore.get(p.getUUID());
		TrialRun run = d.trial;
		if (run == null || !d.inSeam || d.seamPull > 0 || p.isCreative() || !kind.id.equals(run.kind)) {
			return;
		}
		set(p, run, run.score + n);
	}

	private static void set(ServerPlayer p, TrialRun run, int score) {
		Trial.Kind kind = Trial.Kind.byId(run.kind);
		if (kind == null) {
			return;
		}
		int before = kind.medal(run.score);
		run.score = score;
		int after = kind.medal(score);
		if (after > before) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.trial.reached", Component.translatable(Trial.medalKey(after))), true);
			WorldFx.soundSelf(p, "minecraft:block.amethyst_block.chime", 1.0f, 0.7f + 0.2f * after);
		}
		DataStore.markDirty();
	}

	/** Every second in the Seam: the wandering and far-walking trials measure; the heavy twist weighs. */
	public static void tick(ServerPlayer p, PlayerData d) {
		TrialRun run = d.trial;
		if (run == null || d.seamPull > 0 || p.isCreative()) {
			return;
		}
		if (Trial.Kind.WANDER.id.equals(run.kind) && p.onGround() && SeamService.overIsland(p)) {
			String cell = Math.floorDiv(p.getBlockX(), SeamService.CELL) + "," + Math.floorDiv(p.getBlockZ(), SeamService.CELL);
			if (run.cells.add(cell)) {
				set(p, run, run.cells.size());
			}
		} else if (Trial.Kind.FAR.id.equals(run.kind)) {
			int far = (int) Math.floor(Math.hypot(p.getX() - run.fromX, p.getZ() - run.fromZ));
			if (far > run.score) {
				set(p, run, far);
			}
		}
		if (Trial.Twist.HEAVY.id.equals(run.twist)) {
			WorldFx.effect(p, MobEffects.SLOWNESS, 2, 0);
		}
	}

	/** Hunted twist: the Thinned hunt from the first second, not only when the thread runs thin. */
	public static boolean hunted(PlayerData d) {
		return d.trial != null && Trial.Twist.HUNTED.id.equals(d.trial.twist);
	}

	/** Restless twist: the return rift drifts twice as often. */
	public static boolean restless(PlayerData d) {
		return d.trial != null && Trial.Twist.RESTLESS.id.equals(d.trial.twist);
	}

	/** A barrel of the Weavers is filled: it counts once for the plundering trial, whoever opens it first. */
	public static void stocked(BlockPos pos) {
		DataStore.seamCells().add(barrelKey(pos));
	}

	/** A barrel in the Seam is opened or broken: if it was never touched, it is plunder. */
	public static void plundered(ServerPlayer p, Level level, BlockPos pos) {
		plundered(p, Dims.of(level), pos);
	}

	/** As above, by the name of the dimension the barrel stands in. */
	public static void plundered(ServerPlayer p, String dim, BlockPos pos) {
		if (!Dims.SEAM.equals(dim) || p.isCreative()) {
			return;
		}
		if (DataStore.seamCells().remove(barrelKey(pos))) {
			DataStore.markDirty();
			progress(p, Trial.Kind.PLUNDER, 1);
		}
	}

	private static String barrelKey(BlockPos pos) {
		return "b@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	/** Back through the rift: the score stands, and each medal reached for the first time this week pays. */
	public static void finish(ServerPlayer p, PlayerData d) {
		TrialRun run = d.trial;
		d.trial = null;
		Trial.Kind kind = run == null ? null : Trial.Kind.byId(run.kind);
		if (kind == null) {
			return;
		}
		roll();
		int medal = kind.medal(run.score);
		int left = (int) Math.max(0, Math.ceil(d.seamLeft));
		TrialBoard board = DataStore.trials();
		boolean best = false;
		if (run.week == board.week) {
			TrialBoard.Entry entry = new TrialBoard.Entry(p.getName().getString(), run.score, left, medal);
			String key = p.getUUID().toString();
			if (entry.beats(board.scores.get(key))) {
				board.scores.put(key, entry);
				best = true;
			}
		}
		if (d.trialClaimWeek != run.week) {
			d.trialClaimWeek = run.week;
			d.trialClaimMedal = 0;
			d.trialClaimThreads = 0;
		}
		int fragments = 0, points = 0, relics = 0;
		boolean gold = false;
		for (int m = d.trialClaimMedal + 1; m <= medal; m++) {
			fragments += Math.max(0, m == 1 ? Config.TRIAL_BRONZE_FRAGMENTS : m == 2 ? Config.TRIAL_SILVER_FRAGMENTS : Config.TRIAL_GOLD_FRAGMENTS);
			points += Math.max(0, m == 1 ? Config.TRIAL_BRONZE_ATONE : m == 2 ? Config.TRIAL_SILVER_ATONE : Config.TRIAL_GOLD_ATONE);
			if (m == 3) {
				relics += Math.max(0, Config.TRIAL_GOLD_RELICS);
				gold = true;
			}
		}
		d.trialClaimMedal = Math.max(d.trialClaimMedal, medal);
		// New Thread+: no top. Every further quarter of gold's mark is a thread, each paid once a week
		int threads = medal == 3 ? threads(kind, run.score) : 0;
		if (threads > d.trialClaimThreads) {
			fragments += (threads - d.trialClaimThreads) * Math.max(0, Config.NEW_THREAD_TRIAL_FRAGMENTS);
			d.trialClaimThreads = threads;
		}
		if (threads > 0) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.trial.threads", threads));
		}
		if (fragments > 0) {
			WorldFx.give(p, new ItemStack(ModItems.TETHER_FRAGMENT, fragments));
		}
		for (int i = 0; i < relics; i++) {
			WorldFx.give(p, RelicService.random());
		}
		if (gold) {
			for (ItemStack extra : com.extremedimensions.lore.Loot.roll((net.minecraft.server.level.ServerLevel) p.level(), com.extremedimensions.lore.Loot.TRIAL_GOLD, p.position())) {
				WorldFx.give(p, extra);
			}
		}
		if (points > 0) {
			PenaltyService.atone(p, d, false, points);
		}
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.trial.done", Component.translatable(kind.nameKey()), run.score,
				Component.translatable(Trial.medalKey(medal))));
		if (fragments > 0 || points > 0 || relics > 0) {
			p.sendSystemMessage(Component.translatable(relics > 0 ? "gui.extremedimensions.trial.paid_relic" : "gui.extremedimensions.trial.paid",
					points, fragments, relics));
		} else if (medal > 0) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.trial.paid_before"));
		}
		if (best) {
			p.sendSystemMessage(Component.translatable("gui.extremedimensions.trial.best", rank(p)));
		}
		if (medal > 0) {
			Advancements.grant(p, Advancements.TRIAL_MEDAL);
		}
		WorldFx.soundSelf(p, medal > 0 ? "minecraft:ui.toast.challenge_complete" : "minecraft:block.beacon.deactivate", 0.6f, 1.0f);
		DataStore.markDirty();
	}

	/**
	 * New Thread+: how many threads a score reaches beyond gold (every further quarter of gold's mark is one); none
	 * before the world takes up a new thread.
	 */
	public static int threads(Trial.Kind kind, int score) {
		int gold = kind.thresholds()[2];
		if (DataStore.newThread() <= 0 || gold <= 0 || score <= gold) {
			return 0;
		}
		return (score - gold) / Math.max(1, gold / 4);
	}

	/** Pulled out, fallen or gone some other way: the trial is lost (the curse for a Collapse stands as ever). */
	public static void fail(ServerPlayer p, PlayerData d) {
		if (d.trial == null) {
			return;
		}
		d.trial = null;
		p.sendSystemMessage(Component.translatable("gui.extremedimensions.trial.failed"));
		DataStore.markDirty();
	}

	/** A new week: the last trial's best three are kept, and the board starts over. */
	public static void roll() {
		TrialBoard board = DataStore.trials();
		int w = week();
		if (board.week == w) {
			return;
		}
		if (board.week != Integer.MIN_VALUE && !board.scores.isEmpty()) {
			List<TrialBoard.Entry> ranked = board.ranked();
			board.lastWeek = board.week;
			board.lastKind = kind(board.week).id;
			board.lastTop = new ArrayList<>(ranked.subList(0, Math.min(3, ranked.size())));
		}
		board.week = w;
		board.scores.clear();
		DataStore.markDirty();
	}

	/** The player's place on this week's board (1 = first), or 0 if not on it. */
	public static int rank(ServerPlayer p) {
		TrialBoard board = DataStore.trials();
		TrialBoard.Entry mine = board.scores.get(p.getUUID().toString());
		if (mine == null) {
			return 0;
		}
		return board.ranked().indexOf(mine) + 1;
	}

	/** The trial under way for the HUD: "kind;twist;score;bronze;silver;gold", or "" without one. */
	public static String hud(PlayerData d) {
		TrialRun run = d.trial;
		Trial.Kind kind = run == null ? null : Trial.Kind.byId(run.kind);
		if (kind == null) {
			return "";
		}
		int[] t = kind.thresholds();
		return kind.id + ";" + run.twist + ";" + run.score + ";" + t[0] + ";" + t[1] + ";" + t[2];
	}

	/**
	 * Everything the Trials screen shows:
	 * "unlocked;inSeam;armed;active;week;kind;twist;bronze;silver;gold;secondsLeft;best;bestMedal;claimed;rank"
	 * then "|" the top ten "name,score,left,medal;..." and "|" the last trial "kind;name,score,medal;...".
	 */
	public static String data(ServerPlayer p) {
		roll();
		PlayerData d = DataStore.get(p.getUUID());
		int w = week();
		Trial.Kind kind = kind(w);
		int[] t = kind.thresholds();
		TrialBoard board = DataStore.trials();
		TrialBoard.Entry mine = board.scores.get(p.getUUID().toString());
		StringBuilder out = new StringBuilder();
		out.append(unlocked() ? 1 : 0).append(';').append(d.inSeam ? 1 : 0).append(';').append(d.trialArmed ? 1 : 0).append(';')
				.append(d.trial != null ? 1 : 0).append(';').append(w).append(';').append(kind.id).append(';').append(twist(w).id).append(';')
				.append(t[0]).append(';').append(t[1]).append(';').append(t[2]).append(';').append(secondsLeft()).append(';')
				.append(mine == null ? -1 : mine.score).append(';').append(mine == null ? 0 : mine.medal).append(';')
				.append(d.trialClaimWeek == w ? d.trialClaimMedal : 0).append(';').append(rank(p));
		out.append('|');
		List<TrialBoard.Entry> ranked = board.ranked();
		for (int i = 0; i < Math.min(10, ranked.size()); i++) {
			TrialBoard.Entry e = ranked.get(i);
			if (i > 0) {
				out.append(';');
			}
			out.append(e.name).append(',').append(e.score).append(',').append(e.left).append(',').append(e.medal);
		}
		out.append('|').append(board.lastKind == null ? "" : board.lastKind);
		for (TrialBoard.Entry e : board.lastTop) {
			out.append(';').append(e.name).append(',').append(e.score).append(',').append(e.medal);
		}
		return out.toString();
	}
}
