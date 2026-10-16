package net.battlehub.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

class WeeklyScheduleTest {
	private static final ZoneId Z = ZoneId.of("Europe/Amsterdam");

	private static ZonedDateTime at(int y, int m, int d, int h, int min) {
		return ZonedDateTime.of(y, m, d, h, min, 0, 0, Z);
	}

	@Test
	void nextStartThisWeekOrNext() {
		// Thursday 15 October 2026
		ZonedDateTime thu = at(2026, 10, 15, 12, 0);
		assertEquals(at(2026, 10, 17, 20, 0), WeeklySchedule.nextStart(thu, DayOfWeek.SATURDAY, LocalTime.of(20, 0)));
		// Saturday after the start: next Saturday
		assertEquals(at(2026, 10, 24, 20, 0), WeeklySchedule.nextStart(at(2026, 10, 17, 20, 1), DayOfWeek.SATURDAY, LocalTime.of(20, 0)));
		// Exactly at the start: now
		assertEquals(at(2026, 10, 17, 20, 0), WeeklySchedule.nextStart(at(2026, 10, 17, 20, 0), DayOfWeek.SATURDAY, LocalTime.of(20, 0)));
	}

	@Test
	void signupWindow() {
		ZonedDateTime start = at(2026, 10, 17, 20, 0);
		assertTrue(WeeklySchedule.signupOpen(at(2026, 10, 17, 19, 50), start, 15));
		assertFalse(WeeklySchedule.signupOpen(at(2026, 10, 17, 19, 40), start, 15));
		assertFalse(WeeklySchedule.signupOpen(at(2026, 10, 17, 20, 1), start, 15));
		assertEquals(600, WeeklySchedule.secondsUntil(at(2026, 10, 17, 19, 50), start));
	}

	@Test
	void weekKeysAndParsing() {
		assertEquals("2026-W42", WeeklySchedule.weekKey(at(2026, 10, 17, 20, 0)));
		assertEquals("2027-W01", WeeklySchedule.weekKey(at(2027, 1, 4, 20, 0)));
		assertEquals(DayOfWeek.SUNDAY, WeeklySchedule.day("sunday"));
		assertEquals(DayOfWeek.FRIDAY, WeeklySchedule.day("fri"));
		assertEquals(DayOfWeek.WEDNESDAY, WeeklySchedule.day("woensdag"));
		assertEquals(DayOfWeek.SATURDAY, WeeklySchedule.day("someday"));
		assertEquals(LocalTime.of(8, 30), WeeklySchedule.time("8:30"));
		assertEquals(LocalTime.of(20, 0), WeeklySchedule.time("late"));
	}
}
