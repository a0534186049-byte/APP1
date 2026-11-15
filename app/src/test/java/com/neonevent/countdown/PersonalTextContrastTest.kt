package com.neonevent.countdown

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalTextContrastTest {
    @Test fun weightedBrightnessUsesTheRequested299587114Formula() {
        assertEquals(76, PersonalTextContrast.weightedBrightness(0xFFFF0000.toInt()))
        assertEquals(149, PersonalTextContrast.weightedBrightness(0xFF00FF00.toInt()))
        assertEquals(29, PersonalTextContrast.weightedBrightness(0xFF0000FF.toInt()))
    }

    @Test fun averageBrightnessAbove128ChoosesDarkTextAnd128OrBelowChoosesWhite() {
        assertEquals(PersonalTextContrast.AUTO_DARK_TEXT, PersonalTextContrast.textColorForAverageBrightness(128.01))
        assertEquals(PersonalTextContrast.AUTO_LIGHT_TEXT, PersonalTextContrast.textColorForAverageBrightness(128.0))
        assertEquals(PersonalTextContrast.AUTO_LIGHT_TEXT, PersonalTextContrast.textColorForAverageBrightness(32.0))
    }

    @Test fun existingAutomaticCubeContrastStillSelectsItsEstablishedColors() {
        assertEquals(PersonalTextContrast.AUTO_DARK_TEXT,
            PersonalTextContrast.recommendedTextColorForLuminanceSamples(FloatArray(65) { 0.92f }))
        assertEquals(PersonalTextContrast.AUTO_GOLD_TEXT,
            PersonalTextContrast.recommendedTextColorForLuminanceSamples(FloatArray(65) { 0.04f }))
        assertEquals(PersonalTextContrast.AUTO_LIGHT_TEXT,
            PersonalTextContrast.recommendedTextColorForLuminanceSamples(FloatArray(65) { 0.34f }))
        assertEquals(PersonalTextContrast.AUTO_LIGHT_TEXT,
            PersonalTextContrast.recommendedTextColorForLuminanceSamples(
                FloatArray(65) { if (it % 2 == 0) 0.05f else 0.95f }
            ))
    }

    @Test fun relativeLuminanceStillUsesTheSrgbFormulaForAutomaticCubes() {
        assertEquals(0f, PersonalTextContrast.relativeLuminance(0xFF000000.toInt()), 0.0001f)
        assertEquals(1f, PersonalTextContrast.relativeLuminance(0xFFFFFFFF.toInt()), 0.0001f)
    }
}
