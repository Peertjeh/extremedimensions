package com.extremedimensions.expedition;

import java.util.Locale;

import com.extremedimensions.data.PlayerData;
import com.extremedimensions.seam.SeamService;
import com.extremedimensions.server.Dims;
import com.extremedimensions.server.WorldFx;
import com.extremedimensions.tether.TetherService;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the time left on an expedition, or on a thread in the Seam, really comes to (3.55.0): how long it lasts at the
 * pace it runs out now, and what walking back to the way home would cost of it. The timer counts seconds of charge,
 * which a drain above one spends faster than the clock; these say it in the clock's seconds and in the walk's. And the
 * warnings as it runs low, in sounds of the mod's own and in words, when the player chose. Nothing here changes how
 * fast anything drains: it only says so.
 */
public final class TimeLeft {
	private TimeLeft() {}

	/** Blocks a second a player is reckoned to walk home at: walking, not sprinting, for the way is seldom straight. */
	public static final double WALK_SPEED = 4.3;
	/** The timer turns amber once what is left is less than this many times what the walk back costs. */
	public static final double TIGHT = 1.5;

	/**
	 * Seconds of charge (or of thread) the walk back to the way home would cost from where the player stands: to the
	 * arrival portal on an expedition, to the rift in the Seam; -1 where there is no way home to walk to. Beyond the wound
	 * the distance's share of the drain falls away evenly on the way in, so the walk costs what half way out costs, and
	 * the anchors' leaks run on beside it.
	 */
	public static double walkBack(ServerPlayer p, PlayerData d) {
		if (d.inSeam && Dims.SEAM.equals(d.dim)) {
			double dx = p.getX() - d.riftX, dz = p.getZ() - d.riftZ;
			return Math.sqrt(dx * dx + dz * dz) / WALK_SPEED * SeamService.drain(p);
		}
		if (!d.inRun || d.collapsing || d.tetherAnchor == null || !(Dims.NETHER.equals(d.dim) || Dims.END.equals(d.dim))) {
			return -1;
		}
		double far = TetherService.arrivalDistance(p, d);
		double factor = Math.max(1, TetherService.distanceFactor(p, d));
		// the drain without the distance's share, then the distance's share as it is on average on the way in
		double rest = d.currentDrain / factor;
		double[] leak = DrainCauses.leaks(p, d);
		return far / WALK_SPEED * (rest * (1 + factor) / 2 + leak[0] + leak[1]);
	}

	/** How long what is left lasts by the clock at this drain; -1 when it does not run out at all. */
	public static double pace(double left, double drain) {
		return drain <= 0 ? -1 : left / drain;
	}

	/** Whether what is left is getting short of the walk back: under {@link #TIGHT} times what it costs. */
	public static boolean tight(double left, double walkBack) {
		return walkBack > 0 && left < TIGHT * walkBack;
	}

	// ------------------------------------------------------------------ warnings

	/** The "Warn at" choices, as the client's option sends them: two minutes left, one, or once the walk back is tight. */
	public static final int WARN_TWO_MINUTES = 0, WARN_ONE_MINUTE = 1, WARN_WALK_BACK = 2;
	/** The bells, at these seconds left (as before 3.55.0), and the title. */
	private static final int[] BELLS = {60, 30, 10};
	public static final int TITLE_AT = 30;
	/** How far back above a warning's mark the time must climb (a thread given time back) before it may warn again. */
	private static final int REARM = 10;
	/** What of the warnings an expedition or a visit has had. */
	private static final int WORDS = 1, TITLE = 2;
	/** The warnings' own sounds (the bell and the warden's heart as before, with subtitles of their own). */
	public static final String TETHER_BELL = "extremedimensions:tether.warning", TETHER_BEAT = "extremedimensions:tether.heartbeat",
			THREAD_BELL = "extremedimensions:thread.warning", THREAD_BEAT = "extremedimensions:thread.heartbeat";

	/**
	 * Each tick of an expedition ({@code seam} false: the charge) or of a visit to the Seam (true: the thread), with what is
	 * left and what the walk back costs: a bell at a minute, half a minute and ten seconds left, the beat of the last ten
	 * seconds, the warning in words on the action bar when the player's "Warn at" says, and a title at half a minute.
	 */
	public static void warn(ServerPlayer p, PlayerData d, double left, double walkBack, boolean seam) {
		int secs = (int) Math.ceil(left);
		String bell = seam ? THREAD_BELL : TETHER_BELL;
		boolean rang = false;
		int last = seam ? d.seamLastWarn : d.lastWarn;
		for (int t : BELLS) {
			if (secs <= t && last > t) {
				WorldFx.sound(p, bell, 0.7f, 0.6f);
				WorldFx.particles(p, "minecraft:smoke", 1.0, 0.5, 0.02, 25);
				if (seam) {
					d.seamLastWarn = secs;
				} else {
					d.lastWarn = secs;
				}
				rang = true;
				break;
			}
		}
		int beat = seam ? d.seamLastBeat : d.lastBeat;
		if (secs <= 10 && secs != beat) {
			if (seam) {
				d.seamLastBeat = secs;
			} else {
				d.lastBeat = secs;
			}
			WorldFx.sound(p, seam ? THREAD_BEAT : TETHER_BEAT, 1.3f, 1.0f + (10 - secs) * 0.04f);
		}
		int warned = seam ? d.seamWarned : d.runWarned;
		if (due(d.warnAt, left, walkBack)) {
			if ((warned & WORDS) == 0) {
				warned |= WORDS;
				String key = "gui.extremedimensions.warn." + (seam ? "thread" : "tether") + (walkBack >= 1 ? "" : ".near");
				p.sendSystemMessage(Component.translatable(key, clock(left), clock(walkBack))
						.withStyle(tight(left, walkBack) ? ChatFormatting.RED : ChatFormatting.GOLD), true);
				d.lastWarnWords = key;
				if (!rang) {
					WorldFx.sound(p, bell, 0.7f, 0.8f);
				}
			}
		} else if (!due(d.warnAt, left - REARM, walkBack)) {
			warned &= ~WORDS;
		}
		if (secs <= TITLE_AT) {
			if ((warned & TITLE) == 0) {
				warned |= TITLE;
				title(p, d, left, walkBack, seam);
			}
		} else if (secs > TITLE_AT + REARM) {
			warned &= ~TITLE;
		}
		if (seam) {
			d.seamWarned = warned;
		} else {
			d.runWarned = warned;
		}
	}

	/** Whether the words are due by the player's choice: at two minutes left, at one, or once the walk back is tight. */
	static boolean due(int mode, double left, double walkBack) {
		return switch (mode) {
			case WARN_TWO_MINUTES -> left <= 120;
			case WARN_WALK_BACK -> tight(left, walkBack);
			default -> left <= 60;
		};
	}

	/** Half a minute left: "Turn back" (or "Back to the rift") over what is left and the walk back. */
	private static void title(ServerPlayer p, PlayerData d, double left, double walkBack, boolean seam) {
		String key = "gui.extremedimensions.warn.title." + (seam ? "thread" : "tether");
		Component sub = walkBack >= 1 ? Component.translatable("gui.extremedimensions.warn.subtitle", clock(left), clock(walkBack))
				: Component.translatable("gui.extremedimensions.warn.subtitle.near", clock(left));
		p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
		p.connection.send(new ClientboundSetSubtitleTextPacket(sub.copy().withStyle(ChatFormatting.GRAY)));
		p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(key).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
		d.lastWarnTitle = key;
	}

	/** Seconds as the timer writes them, "1:42". */
	public static String clock(double secs) {
		int s = (int) Math.ceil(Math.max(0, secs));
		return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}
}
