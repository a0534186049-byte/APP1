package com.neonevent.countdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalImageContrastInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val textRegion = RectF(10f, 80f, 230f, 160f)

    private fun renderScrim(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        PersonalTextContrast.drawForRegion(
            canvas, source, source.width.toFloat(), source.height.toFloat(),
            textRegion, Paint(Paint.ANTI_ALIAS_FLAG)
        )
        return output
    }

    @Test fun darkPersonalImageReceivesNoExtraDarkening() {
        val dark = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(24, 28, 36))
        }
        val output = renderScrim(dark)
        assertEquals(dark.getPixel(120, 120), output.getPixel(120, 120))
        assertEquals(dark.getPixel(120, 40), output.getPixel(120, 40))
    }

    @Test fun brightPersonalImageIsDarkenedOnlyBehindText() {
        val bright = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        val output = renderScrim(bright)
        assertTrue(Color.red(output.getPixel(120, 120)) < 80)
        assertEquals(Color.WHITE, output.getPixel(120, 40))
    }

    @Test fun mixedBrightAndDarkImageKeepsWhiteTextAtTargetContrast() {
        val mixed = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
        for (y in 0 until mixed.height) {
            for (x in 0 until mixed.width) {
                val isBright = ((x / 20) + (y / 20)) % 2 == 0
                mixed.setPixel(x, y, if (isBright) Color.WHITE else Color.rgb(18, 22, 30))
            }
        }

        val output = renderScrim(mixed)
        val sampledLuminance = PersonalTextContrast.relativeLuminance(output.getPixel(120, 120))
        val contrast = 1.05f / (sampledLuminance + 0.05f)
        assertTrue("White text contrast was $contrast", contrast >= 4.5f)
    }

    @Test fun personalBoxStyleDefaultsToDarkAndPersistsSelections() {
        val store = context.getSharedPreferences("event_countdown_settings", Context.MODE_PRIVATE)
        val hadValue = store.contains("personal_cube_style")
        val previous = store.getInt("personal_cube_style", CountdownPrefs.CUBES_DARK)
        try {
            store.edit().remove("personal_cube_style").commit()
            assertEquals(CountdownPrefs.CUBES_DARK, CountdownPrefs(context).cubeStyle)

            CountdownPrefs(context).cubeStyle = CountdownPrefs.CUBES_LIGHT
            assertEquals(CountdownPrefs.CUBES_LIGHT, CountdownPrefs(context).cubeStyle)

            CountdownPrefs(context).cubeStyle = CountdownPrefs.CUBES_FROSTED
            assertEquals(CountdownPrefs.CUBES_FROSTED, CountdownPrefs(context).cubeStyle)
        } finally {
            if (hadValue) store.edit().putInt("personal_cube_style", previous).commit()
            else store.edit().remove("personal_cube_style").commit()
        }
    }
}
