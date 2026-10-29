package com.neonevent.countdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    @Test fun dateOnlyUsesLocalCalendarDaysNearMidnight() {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val target = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = today.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()

        val result = CountdownMath.calculateDateOnly(target, now)
        assertFalse(result.complete)
        assertEquals(1L, result.days)
    }

    @Test fun dateOnlyTargetDateShowsZeroWithoutCompleting() {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val target = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val now = today.atTime(12, 30).atZone(zone).toInstant().toEpochMilli()

        val result = CountdownMath.calculateDateOnly(target, now)
        assertFalse(result.complete)
        assertEquals(0L, result.days)
        assertEquals(0L, result.hours)
        assertEquals(0L, result.minutes)
        assertEquals(0L, result.seconds)
    }

    @Test fun dateOnlyPastEventIsComplete() {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val target = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = today.atStartOfDay(zone).toInstant().toEpochMilli()

        assertTrue(CountdownMath.calculateDateOnly(target, now).complete)
    }

}
