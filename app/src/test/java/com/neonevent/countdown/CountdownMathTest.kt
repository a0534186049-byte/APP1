package com.neonevent.countdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CountdownMathTest {
    @Test fun futureTargetIsSplitIntoUnits() {
        val result = CountdownMath.calculate(90_061_000L, 0L)
        assertFalse(result.complete)
        assertEquals(1L, result.days)
        assertEquals(1L, result.hours)
        assertEquals(1L, result.minutes)
        assertEquals(1L, result.seconds)
    }

    @Test fun targetAtNowIsComplete() {
        assertTrue(CountdownMath.calculate(100L, 100L).complete)
    }

    @Test fun pastTargetIsComplete() {
        assertTrue(CountdownMath.calculate(99L, 100L).complete)
    }

    @Test fun fractionalSecondRoundsUp() {
        val result = CountdownMath.calculate(1_500L, 1_001L)
        assertEquals(1L, result.seconds)
        assertEquals(1L, result.totalSeconds)
    }

    @Test fun dateCountStaysUntilLocalMidnightAndThenChanges() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 11, 1)
        val target = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val justBeforeMidnight = today.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()

        val before = CountdownMath.calculateDateOnly(target, justBeforeMidnight, zone)
        assertFalse(before.complete)
        assertEquals(1L, before.days)
        assertEquals(0L, before.hours)
        assertEquals(0L, before.minutes)
        assertEquals(1L, before.seconds)

        val atMidnight = CountdownMath.calculateDateOnly(target, target, zone)
        assertFalse(atMidnight.complete)
        assertEquals(0L, atMidnight.days)
        assertEquals(0L, atMidnight.hours)
        assertEquals(0L, atMidnight.minutes)
        assertEquals(0L, atMidnight.seconds)
    }

    @Test fun clockUnitsShowTimeUntilNextLocalMidnight() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 11, 1)
        val target = today.plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = today.atTime(10, 15, 30).atZone(zone).toInstant().toEpochMilli()

        val result = CountdownMath.calculateDateOnly(target, now, zone)
        assertEquals(3L, result.days)
        assertEquals(13L, result.hours)
        assertEquals(44L, result.minutes)
        assertEquals(30L, result.seconds)
    }

    @Test fun nextMidnightRespectsDaylightSavingTransition() {
        val zone = ZoneId.of("Europe/Berlin")
        val today = LocalDate.of(2026, 3, 29)
        val target = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = today.atTime(0, 30).atZone(zone).toInstant().toEpochMilli()

        val result = CountdownMath.calculateDateOnly(target, now, zone)
        assertEquals(2L, result.days)
        // The spring DST change makes this local calendar day 23 elapsed hours.
        assertEquals(22L, result.hours)
        assertEquals(30L, result.minutes)
        assertEquals(0L, result.seconds)
    }

    @Test fun eventDateShowsZeroButIsNotCompleteUntilNextLocalDate() {
        val zone = ZoneId.of("Asia/Jerusalem")
        val eventDate = LocalDate.of(2026, 11, 1)
        val target = eventDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val now = eventDate.atTime(12, 30).atZone(zone).toInstant().toEpochMilli()

        val result = CountdownMath.calculateDateOnly(target, now, zone)
        assertFalse(result.complete)
        assertEquals(0L, result.days)
        assertEquals(0L, result.hours)
        assertEquals(0L, result.minutes)
        assertEquals(0L, result.seconds)

        val nextDay = eventDate.plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(zone).toInstant().toEpochMilli()
        assertTrue(CountdownMath.calculateDateOnly(target, nextDay, zone).complete)
    }
}
