package com.neonevent.countdown

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class WidgetClockBoundary(val requestCode: Int, val action: String) {
    TEN_HOURS(9921, "com.neonevent.countdown.WIDGET_BOUNDARY_TEN_HOURS"),
    ONE_HOUR(9922, "com.neonevent.countdown.WIDGET_BOUNDARY_ONE_HOUR"),
    MIDNIGHT(9923, "com.neonevent.countdown.WIDGET_BOUNDARY_MIDNIGHT")
}

data class WidgetClockBoundaryTime(
    val boundary: WidgetClockBoundary,
    val triggerAtMillis: Long
)

data class WidgetClockSnapshot(
    val visible: Boolean,
    val nextMidnightMillis: Long?,
    val remainingMillis: Long,
    val chronometerFormat: String,
    val boundaries: List<WidgetClockBoundaryTime>
)

/**
 * Pure local-time calculations for the home-screen widget clock.
 *
 * Each new deadline is derived from the next local calendar midnight, not from
 * an assumed 24-hour duration. This naturally follows DST and timezone rules.
 */
object WidgetClockSchedule {
    const val TEN_HOURS_MS = 10L * 60L * 60L * 1000L
    const val ONE_HOUR_MS = 60L * 60L * 1000L
    const val MIDNIGHT_SAFETY_BUFFER_MS = 5_000L

    fun evaluate(
        eventLocalDate: LocalDate,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): WidgetClockSnapshot {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val today = now.toLocalDate()
        if (!today.isBefore(eventLocalDate)) {
            return WidgetClockSnapshot(
                visible = false,
                nextMidnightMillis = null,
                remainingMillis = 0L,
                chronometerFormat = "00:%s",
                boundaries = emptyList()
            )
        }

        val midnightMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val remaining = (midnightMillis - nowMillis).coerceAtLeast(0L)
        // Hide a few seconds before local midnight so a delayed alarm cannot leave
        // the running Chronometer showing a negative duration.
        val clockVisible = remaining > MIDNIGHT_SAFETY_BUFFER_MS
        val boundaries = buildList {
            addBoundaryIfFuture(
                WidgetClockBoundary.TEN_HOURS,
                midnightMillis - TEN_HOURS_MS + 1_000L,
                nowMillis
            )
            addBoundaryIfFuture(
                WidgetClockBoundary.ONE_HOUR,
                midnightMillis - ONE_HOUR_MS + 1_000L,
                nowMillis
            )
            addBoundaryIfFuture(
                WidgetClockBoundary.MIDNIGHT,
                midnightMillis - MIDNIGHT_SAFETY_BUFFER_MS,
                nowMillis
            )
        }

        return WidgetClockSnapshot(
            visible = clockVisible,
            nextMidnightMillis = midnightMillis,
            remainingMillis = if (clockVisible) remaining else 0L,
            chronometerFormat = formatForRemaining(remaining),
            boundaries = boundaries
        )
    }

    fun formatForRemaining(remainingMillis: Long): String = when {
        remainingMillis >= TEN_HOURS_MS -> "%s"
        remainingMillis >= ONE_HOUR_MS -> "0%s"
        else -> "00:%s"
    }

    private fun MutableList<WidgetClockBoundaryTime>.addBoundaryIfFuture(
        boundary: WidgetClockBoundary,
        triggerAtMillis: Long,
        nowMillis: Long
    ) {
        if (triggerAtMillis > nowMillis) {
            add(WidgetClockBoundaryTime(boundary, triggerAtMillis))
        }
    }
}
