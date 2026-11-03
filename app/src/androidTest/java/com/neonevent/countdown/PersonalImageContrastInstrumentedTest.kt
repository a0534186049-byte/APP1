package com.neonevent.countdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalImageContrastInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun brightPhotoSelectsDarkTextWithoutChangingPhotoPixels() {
        val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val before = bitmap.getPixel(120, 120)
        val color = PersonalTextContrast.recommendedTextColorForRegion(
            bitmap, 240f, 320f, RectF(10f, 80f, 230f, 160f)
        )
        assertEquals(PersonalTextContrast.AUTO_DARK_TEXT, color)
        assertEquals(before, bitmap.getPixel(120, 120))
    }

    @Test fun darkPhotoSelectsWhiteTextWithoutAddingAScrim() {
        val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(24, 28, 36))
        }
        val before = bitmap.getPixel(120, 120)
        val color = PersonalTextContrast.recommendedTextColorForRegion(
            bitmap, 240f, 320f, RectF(10f, 80f, 230f, 160f)
        )
        assertEquals(PersonalTextContrast.AUTO_LIGHT_TEXT, color)
        assertEquals(before, bitmap.getPixel(120, 120))
    }

    @Test fun mixedPhotoSelectsColorIndependentlyForEachTextRegion() {
        val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) {
            val color = if (y < 160) Color.WHITE else Color.rgb(18, 22, 30)
            for (x in 0 until bitmap.width) bitmap.setPixel(x, y, color)
        }
        val upper = PersonalTextContrast.recommendedTextColorForRegion(
            bitmap, 240f, 320f, RectF(10f, 20f, 230f, 100f)
        )
        val lower = PersonalTextContrast.recommendedTextColorForRegion(
            bitmap, 240f, 320f, RectF(10f, 220f, 230f, 300f)
        )
        assertEquals(PersonalTextContrast.AUTO_DARK_TEXT, upper)
        assertEquals(PersonalTextContrast.AUTO_LIGHT_TEXT, lower)
    }

    @Test fun personalBoxStyleStillDefaultsAndPersistsSelections() {
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
