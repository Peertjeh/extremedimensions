package net.battlehub.core;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/** When the weekly tournament starts: a day of the week and a time, in the server's time zone. */
public final class WeeklySchedule {
	private WeeklySchedule() {}

	/** The first start at or after {@code now}. */
	public static ZonedDateTime nextStart(ZonedDateTime now, DayOfWeek day, LocalTime time) {
		ZonedDateTime candidate = now.with(TemporalAdjusters.nextOrSame(day)).with(time).withSecond(0).withNano(0);
		if (candidate.isBefore(now)) {
			candidate = candidate.plusWeeks(1);
		}
		return candidate;
	}

	/** Seconds from {@code now} to {@code start} (negative once it has passed). */
	public static long secondsUntil(ZonedDateTime now, ZonedDateTime start) {
		return Duration.between(now, start).getSeconds();
	}

	/** True when sign-ups for the start at {@code start} should be open at {@code now}. */
	public static boolean signupOpen(ZonedDateTime now, ZonedDateTime start, int signupMinutes) {
		long s = secondsUntil(now, start);
		return s >= 0 && s <= signupMinutes * 60L;
	}

	/** "2026-W42": one tournament per week, whatever the server restarts. */
	public static String weekKey(ZonedDateTime start) {
		return String.format(Locale.ROOT, "%d-W%02d", start.get(IsoFields.WEEK_BASED_YEAR), start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
	}

	/** A day name from the config ("saturday", "SAT", "zaterdag"), Saturday when unknown. */
	public static DayOfWeek day(String text) {
		if (text != null) {
			String t = text.trim().toUpperCase(Locale.ROOT);
			for (DayOfWeek d : DayOfWeek.values()) {
				if (d.name().equals(t) || (t.length() >= 3 && d.name().startsWith(t))) return d;
			}
			String[] dutch = {"MAANDAG", "DINSDAG", "WOENSDAG", "DONDERDAG", "VRIJDAG", "ZATERDAG", "ZONDAG"};
			for (int i = 0; i < 7; i++) {
				if (dutch[i].equals(t)) return DayOfWeek.of(i + 1);
			}
		}
		return DayOfWeek.SATURDAY;
	}

	/** "20:00" or "8:30", 20:00 when it cannot be read. */
	public static LocalTime time(String text) {
		try {
			String[] p = text.trim().split(":");
			return LocalTime.of(Integer.parseInt(p[0]), p.length > 1 ? Integer.parseInt(p[1]) : 0);
		} catch (RuntimeException e) {
			return LocalTime.of(20, 0);
		}
	}
}
