package com.neonevent.countdown

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class CountdownParts(
    val complete: Boolean,
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long
)

object CountdownMath {
    /** Calendar-date calculation for tests and date-only callers. */
    fun calculateDateOnly(
        eventLocalDate: LocalDate,
        today: LocalDate
    ): CountdownParts {
        val days = ChronoUnit.DAYS.between(today, eventLocalDate)
        if (days < 0L) return CountdownParts(true, 0, 0, 0, 0)
        return CountdownParts(false, days, 0, 0, 0)
    }

    /**
     * Counts the full duration until the selected event date begins in the device's
     * local timezone. Each field is the total remaining amount in that unit.
     * Seconds are rounded up so a positive remaining duration never shows zero.
     */
    fun calculateCumulative(
        eventLocalDate: LocalDate,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): CountdownParts {
        val target = eventLocalDate.atStartOfDay(zone).toInstant()
        val remainingMillis = Duration.between(Instant.ofEpochMilli(nowMillis), target).toMillis()
        if (remainingMillis <= 0L) return CountdownParts(true, 0, 0, 0, 0)

        val totalSeconds = ((remainingMillis + 999L) / 1000L).coerceAtLeast(1L)
        return CountdownParts(
            complete = false,
            days = totalSeconds / 86_400L,
            hours = totalSeconds / 3_600L,
            minutes = totalSeconds / 60L,
            seconds = totalSeconds
        )
    }

    /**
     * Counts calendar days to the selected local event date and, until that date,
     * shows the time remaining to the next local midnight. This preserves the
     * original home-screen clock units without inventing an event time.
     */
    fun calculateDateOnly(
        eventLocalDate: LocalDate,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): CountdownParts {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val today = now.toLocalDate()
        val days = ChronoUnit.DAYS.between(today, eventLocalDate)

        if (days < 0L) return CountdownParts(true, 0, 0, 0, 0)
        if (days == 0L) return CountdownParts(false, 0, 0, 0, 0)

        val nextLocalMidnight = today.plusDays(1).atStartOfDay(zone)
        val millisUntilMidnight = Duration.between(now, nextLocalMidnight)
            .toMillis()
            .coerceAtLeast(0L)
        val totalSeconds = ((millisUntilMidnight + 999L) / 1000L).coerceAtLeast(0L)

        return CountdownParts(
            complete = false,
            days = days,
            hours = totalSeconds / 3_600L,
            minutes = (totalSeconds % 3_600L) / 60L,
            seconds = totalSeconds % 60L
        )
    }
}
