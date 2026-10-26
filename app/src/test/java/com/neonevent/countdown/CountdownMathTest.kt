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
}
