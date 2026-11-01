package com.neonevent.countdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CountdownMathTest {
    @Test fun futureEventUsesDateDifferenceOnly() {
        val result = CountdownMath.calculateDateOnly(
            LocalDate.of(2026, 11, 4),
            LocalDate.of(2026, 11, 1)
        )
        assertFalse(result.complete)
        assertEquals(3L, result.days)
        assertEquals(0L, result.hours)
        assertEquals(0L, result.minutes)
        assertEquals(0L, result.seconds)
    }

    @Test fun oneSecondBeforeLocalMidnightIsStillPreviousDate() {
        val zone = ZoneId.of("Asia/Jerusalem")
        val targetDate = LocalDate.of(2026, 11, 2)
        val beforeMidnight = Instant.parse("2026-11-01T21:59:59Z").atZone(zone).toLocalDate()
        val atMidnight = Instant.parse("2026-11-01T22:00:00Z").atZone(zone).toLocalDate()

        assertEquals(1L, CountdownMath.calculateDateOnly(targetDate, beforeMidnight).days)
        assertEquals(0L, CountdownMath.calculateDateOnly(targetDate, atMidnight).days)
    }

    @Test fun eventDateIsZeroAndNextLocalDateIsComplete() {
        val eventDate = LocalDate.of(2026, 11, 1)
        val today = CountdownMath.calculateDateOnly(eventDate, eventDate)
        assertFalse(today.complete)
        assertEquals(0L, today.days)
        assertTrue(CountdownMath.calculateDateOnly(eventDate, eventDate.plusDays(1)).complete)
    }

    @Test fun daylightSavingSpringChangeDoesNotChangeDateDifference() {
        val zone = ZoneId.of("Europe/Berlin")
        val eventDate = LocalDate.of(2026, 3, 29)
        val beforeClockJump = Instant.parse("2026-03-28T22:59:59Z").atZone(zone).toLocalDate()
        val afterClockJump = Instant.parse("2026-03-29T01:00:00Z").atZone(zone).toLocalDate()

        assertEquals(1L, CountdownMath.calculateDateOnly(eventDate, beforeClockJump).days)
        assertEquals(0L, CountdownMath.calculateDateOnly(eventDate, afterClockJump).days)
    }

    @Test fun daylightSavingAutumnChangeDoesNotAddADay() {
        val zone = ZoneId.of("Europe/Berlin")
        val eventDate = LocalDate.of(2026, 10, 25)
        val beforeRepeatedHour = Instant.parse("2026-10-24T21:59:59Z").atZone(zone).toLocalDate()
        val afterRepeatedHour = Instant.parse("2026-10-25T02:30:00Z").atZone(zone).toLocalDate()

        assertEquals(1L, CountdownMath.calculateDateOnly(eventDate, beforeRepeatedHour).days)
        assertEquals(0L, CountdownMath.calculateDateOnly(eventDate, afterRepeatedHour).days)
    }
}
