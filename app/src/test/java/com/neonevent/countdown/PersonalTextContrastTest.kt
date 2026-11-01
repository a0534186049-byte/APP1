package com.neonevent.countdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalTextContrastTest {
    @Test fun darkImageGetsNoAdditionalScrim() {
        assertEquals(0, PersonalTextContrast.scrimAlphaForAverageLuminance(0.05f))
        assertEquals(0, PersonalTextContrast.scrimAlphaForAverageLuminance(0.18f))
    }

    @Test fun brighterImagesReceiveStrongerScrim() {
        val dark = PersonalTextContrast.scrimAlphaForAverageLuminance(0.22f)
        val medium = PersonalTextContrast.scrimAlphaForAverageLuminance(0.50f)
        val bright = PersonalTextContrast.scrimAlphaForAverageLuminance(1.00f)
        assertTrue(dark > 0)
        assertTrue(medium > dark)
        assertTrue(bright > medium)
    }

    @Test fun whiteTextContrastMeetsFourPointFiveToOneForSampledAverage() {
        listOf(0.19f, 0.25f, 0.50f, 0.75f, 1.00f).forEach { luminance ->
            val alpha = PersonalTextContrast.scrimAlphaForAverageLuminance(luminance) / 255f
            val resultingLuminance = luminance * (1f - alpha)
            val contrast = 1.05f / (resultingLuminance + 0.05f)
            assertTrue("Contrast $contrast for luminance $luminance", contrast >= 4.5f)
        }
    }

    @Test fun luminanceUsesTheRelativeSrgbFormula() {
        assertEquals(0f, PersonalTextContrast.relativeLuminance(0xFF000000.toInt()), 0.0001f)
        assertEquals(1f, PersonalTextContrast.relativeLuminance(0xFFFFFFFF.toInt()), 0.0001f)
    }

    @Test fun mixedBrightDarkRegionUsesBrightSampleAsSafetyMargin() {
        val samples = FloatArray(65) { index -> if (index < 32) 0f else 1f }
        val meanOnly = PersonalTextContrast.scrimAlphaForAverageLuminance(samples.average().toFloat())
        val protected = PersonalTextContrast.scrimAlphaForLuminanceSamples(samples)
        assertTrue(protected > meanOnly)
        assertTrue(protected >= 209)
    }
}
