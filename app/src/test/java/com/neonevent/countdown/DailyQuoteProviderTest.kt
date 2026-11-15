package com.neonevent.countdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyQuoteProviderTest {
    private val regular = List(210) { index -> "quote-$index" }
    private val specialDays = listOf(0, 1, 2, 3, 7, 10, 14, 30)
        .associateWith { day -> "special-$day" }

    @Test fun noEventDateUsesTheLocalEpochDayIndex() {
        val today = LocalDate.ofEpochDay(20_000L)
        val expected = regular[Math.floorMod(today.toEpochDay(), 210L).toInt()]
        assertEquals(expected, DailyQuoteProvider.selectQuote(today, null, regular, specialDays))
    }

    @Test fun allEightSpecialDaysOverrideTheRegularQuoteIncludingZero() {
        val today = LocalDate.of(2026, 11, 1)
        specialDays.forEach { (daysRemaining, expected) ->
            assertEquals(
                expected,
                DailyQuoteProvider.selectQuote(today, today.plusDays(daysRemaining.toLong()), regular, specialDays)
            )
        }
    }

    @Test fun nonspecialDaysUseTheRegularEpochDayQuote() {
        val today = LocalDate.of(2026, 11, 1)
        listOf(29L, 15L, 5L, 100L).forEach { daysRemaining ->
            val expected = regular[Math.floorMod(today.toEpochDay(), 210L).toInt()]
            assertEquals(
                expected,
                DailyQuoteProvider.selectQuote(today, today.plusDays(daysRemaining), regular, specialDays)
            )
        }
    }

    @Test fun anEventThatHasPassedUsesTheRegularQuote() {
        val today = LocalDate.of(2026, 11, 1)
        val expected = regular[Math.floorMod(today.toEpochDay(), 210L).toInt()]
        assertEquals(
            expected,
            DailyQuoteProvider.selectQuote(today, today.minusDays(1), regular, specialDays)
        )
    }

    @Test fun theSameLocalDateAlwaysUsesTheSameQuoteAndNextDateAdvances() {
        val today = LocalDate.ofEpochDay(20_001L)
        val first = DailyQuoteProvider.selectQuote(today, null, regular, emptyMap())
        val second = DailyQuoteProvider.selectQuote(today, null, regular, emptyMap())
        val nextDay = DailyQuoteProvider.selectQuote(today.plusDays(1), null, regular, emptyMap())

        assertEquals(first, second)
        assertTrue(first != nextDay)
        assertEquals(regular[Math.floorMod(today.toEpochDay(), 210L).toInt()], first)
        assertEquals(regular[Math.floorMod(today.plusDays(1).toEpochDay(), 210L).toInt()], nextDay)
    }

    @Test fun theListWrapsAfterExactlyTwoHundredAndTenDays() {
        val today = LocalDate.ofEpochDay(20_001L)
        assertEquals(
            DailyQuoteProvider.selectQuote(today, null, regular, emptyMap()),
            DailyQuoteProvider.selectQuote(today.plusDays(210), null, regular, emptyMap())
        )
    }

    @Test fun missingSpecialDaysKeepsNormalQuotesAvailable() {
        val today = LocalDate.ofEpochDay(20_001L)
        val expected = regular[Math.floorMod(today.toEpochDay(), 210L).toInt()]
        assertEquals(expected, DailyQuoteProvider.selectQuote(today, today.plusDays(30), regular, emptyMap()))
    }

    @Test fun emptyNormalListDisplaysNoQuote() {
        assertEquals("", DailyQuoteProvider.selectQuote(LocalDate.now(), null, emptyList(), emptyMap()))
    }


}
