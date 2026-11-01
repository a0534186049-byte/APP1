package com.neonevent.countdown

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class CountdownParts(
    val complete: Boolean,
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    val totalSeconds: Long
)

object CountdownMath {
    /**
     * Date-only countdown.
     *
     * Days are the difference between local calendar dates, so they change only
     * at local midnight. The smaller clock units show the actual time remaining
     * until the next local midnight. Using ZonedDateTime also respects daylight
     * saving transitions (a local day is not always exactly 24 elapsed hours).
     * On the event date all units are zero; the event becomes complete the next day.
     */
    fun calculateDateOnly(
        targetMillis: Long,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): CountdownParts {
        val targetDate = Instant.ofEpochMilli(targetMillis).atZone(zone).toLocalDate()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val today = now.toLocalDate()
        val dateDays = ChronoUnit.DAYS.between(today, targetDate)

        if (dateDays < 0L) return CountdownParts(true, 0, 0, 0, 0, 0)
        if (dateDays == 0L) return CountdownParts(false, 0, 0, 0, 0, 0)

        val nextLocalMidnight = today.plusDays(1).atStartOfDay(zone)
        val millisUntilMidnight = Duration.between(now, nextLocalMidnight)
            .toMillis()
            .coerceAtLeast(0L)
        // Ceiling prevents showing 00:00:00 while a fraction of a second remains.
        val total = ((millisUntilMidnight + 999L) / 1000L).coerceAtLeast(0L)

        return CountdownParts(
            complete = false,
            days = dateDays,
            hours = total / 3_600L,
            minutes = (total % 3_600L) / 60L,
            seconds = total % 60L,
            totalSeconds = total
        )
    }

    fun calculate(targetMillis: Long, nowMillis: Long): CountdownParts {
        val delta = targetMillis - nowMillis
        if (delta <= 0L) return CountdownParts(true, 0, 0, 0, 0, 0)
        // Ceiling avoids showing zero one second before the target instant.
        val total = ((delta + 999L) / 1000L).coerceAtLeast(0L)
        return CountdownParts(
            complete = false,
            days = total / 86_400L,
            hours = (total % 86_400L) / 3_600L,
            minutes = (total % 3_600L) / 60L,
            seconds = total % 60L,
            totalSeconds = total
        )
    }
}
