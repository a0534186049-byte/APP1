package com.neonevent.countdown

data class CountdownParts(
    val complete: Boolean,
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    val totalSeconds: Long
)

object CountdownMath {
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
