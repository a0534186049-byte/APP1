package com.neonevent.countdown

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class CountdownParts(
    val complete: Boolean,
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long
)

object CountdownMath {
    /**
     * The event is a Hebrew-calendar date converted by ICU to the device's local
     * LocalDate. There is intentionally no target hour or time-of-day calculation.
     */
    fun calculateDateOnly(
        eventLocalDate: LocalDate,
        today: LocalDate = LocalDate.now()
    ): CountdownParts {
        val days = ChronoUnit.DAYS.between(today, eventLocalDate)
        if (days < 0L) return CountdownParts(true, 0, 0, 0, 0)
        return CountdownParts(
            complete = false,
            days = days,
            hours = 0,
            minutes = 0,
            seconds = 0
        )
    }
}
