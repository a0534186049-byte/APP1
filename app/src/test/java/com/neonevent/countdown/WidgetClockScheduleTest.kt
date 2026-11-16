package com.neonevent.countdown

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetClockScheduleTest {
    @Test fun formatUsesTheRequestedHourTransitions() {
        assertEquals("%s", WidgetClockSchedule.formatForRemaining(10L * 60L * 60L * 1000L))
        assertEquals("0%s", WidgetClockSchedule.formatForRemaining(9L * 60L * 60L * 1000L))
        assertEquals("0%s", WidgetClockSchedule.formatForRemaining(60L * 60L * 1000L))
        assertEquals("00:%s", WidgetClockSchedule.formatForRemaining(59L * 60L * 1000L))
        assertEquals("00:%s", WidgetClockSchedule.formatForRemaining(1_000L))
    }

    @Test fun eventDayAndPastDatesHideTheClockWithoutNegativeTime() {
        val zone = ZoneId.of("Asia/Jerusalem")
        val eventDate = LocalDate.of(2026, 11, 3)
        val eventDay = ZonedDateTime.of(2026, 11, 3, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        val pastDay = eventDay + 24L * 60L * 60L * 1000L

        val onEventDay = WidgetClockSchedule.evaluate(eventDate, eventDay, zone)
        val afterEventDay = WidgetClockSchedule.evaluate(eventDate, pastDay, zone)

        assertFalse(onEventDay.visible)
        assertFalse(afterEventDay.visible)
        assertEquals(0L, onEventDay.remainingMillis)
        assertTrue(onEventDay.boundaries.isEmpty())
    }

    @Test fun regularDaySchedulesAtMostTwoFormatChangesAndMidnight() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 5, 2, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        val snapshot = WidgetClockSchedule.evaluate(LocalDate.of(2026, 5, 4), now, zone)

        assertTrue(snapshot.visible)
        assertEquals(24L * 60L * 60L * 1000L, snapshot.remainingMillis)
        assertEquals(
            listOf(
                WidgetClockBoundary.TEN_HOURS,
                WidgetClockBoundary.ONE_HOUR,
                WidgetClockBoundary.MIDNIGHT
            ),
            snapshot.boundaries.map { it.boundary }
        )
        assertEquals(14L * 60L * 60L * 1000L + 1_000L, snapshot.boundaries[0].triggerAtMillis - now)
        assertEquals(23L * 60L * 60L * 1000L + 1_000L, snapshot.boundaries[1].triggerAtMillis - now)
        assertEquals(24L * 60L * 60L * 1000L, snapshot.boundaries[2].triggerAtMillis - now)
    }

    @Test fun alreadyCrossedThresholdsAreNotScheduledAgain() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 5, 2, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        val snapshot = WidgetClockSchedule.evaluate(LocalDate.of(2026, 5, 5), now, zone)

        assertEquals("00:%s", snapshot.chronometerFormat)
        assertEquals(listOf(WidgetClockBoundary.MIDNIGHT), snapshot.boundaries.map { it.boundary })
    }

    @Test fun localMidnightFollowsSpringAndAutumnDstDayLengths() {
        val zone = ZoneId.of("America/New_York")

        val springStart = ZonedDateTime.of(2025, 3, 9, 0, 0, 0, 0, zone)
        val spring = WidgetClockSchedule.evaluate(
            LocalDate.of(2025, 3, 11),
            springStart.toInstant().toEpochMilli(),
            zone
        )
        assertEquals(23L * 60L * 60L * 1000L, spring.remainingMillis)

        val autumnStart = ZonedDateTime.of(2025, 11, 2, 0, 0, 0, 0, zone)
        val autumn = WidgetClockSchedule.evaluate(
            LocalDate.of(2025, 11, 4),
            autumnStart.toInstant().toEpochMilli(),
            zone
        )
        assertEquals(25L * 60L * 60L * 1000L, autumn.remainingMillis)
    }
}
