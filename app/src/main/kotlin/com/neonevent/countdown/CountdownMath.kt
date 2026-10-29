package com.neonevent.countdown

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
     * Date-only event countdown. The day count follows local calendar boundaries,
     * rather than dropping a day at the same clock time every 24 hours. On the
     * event's local date the visible units stay at zero; a past date is complete.
     */
    fun calculateDateOnly(targetMillis: Long, nowMillis: Long): CountdownParts {
        val zone = ZoneId.systemDefault()
        val targetDate = Instant.ofEpochMilli(targetMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val dateDays = ChronoUnit.DAYS.between(today, targetDate)
        if (dateDays < 0L) return CountdownParts(true, 0, 0, 0, 0, 0)
        if (dateDays == 0L) return CountdownParts(false, 0, 0, 0, 0, 0)

        val delta = targetMillis - nowMillis
        val total = ((delta + 999L) / 1000L).coerceAtLeast(0L)
        return CountdownParts(
            complete = false,
            days = dateDays,
            hours = (total % 86_400L) / 3_600L,
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
