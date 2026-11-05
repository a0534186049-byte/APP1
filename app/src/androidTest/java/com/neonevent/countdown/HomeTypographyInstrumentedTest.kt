package com.neonevent.countdown

import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeTypographyInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun optionAFontsLoadOfflineAndRenderHebrewAndDigits() {
        val prefs = CountdownPrefs(context)
        val heading = prefs.homeHeadingTypeface()
        val body = prefs.homeBodyTypeface()

        assertNotEquals(Typeface.DEFAULT, heading)
        assertNotEquals(Typeface.DEFAULT, body)
        assertEquals(400, heading.weight)
        assertEquals(500, body.weight)

        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = heading
            textSize = 44f
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = body
            textSize = 18f
        }
        assertTrue(headingPaint.measureText("הרגע הגדול מתקרב") > 0f)
        assertTrue(headingPaint.measureText("1234567890") > 0f)
        assertTrue(bodyPaint.measureText("ימים שעות דקות שניות") > 0f)
        assertTrue(bodyPaint.measureText("ט״ו בחשוון תשפ״ז") > 0f)
    }

    @Test fun bothFontLicensesAreBundledInTheApk() {
        context.assets.open("licenses/OFL-Suez-One.txt").use { assertTrue(it.read() >= 0) }
        context.assets.open("licenses/OFL-Frank-Ruhl-Libre.txt").use { assertTrue(it.read() >= 0) }
    }
}
