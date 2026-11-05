package com.neonevent.countdown

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WallpaperThemeInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun everyBuiltInThemeHasItsOwnPortraitImage() {
        val prefs = CountdownPrefs(context)
        assertEquals(9, CountdownPrefs.builtInThemeIndices.size)
        assertEquals(9, CountdownPrefs.themeNames.size)
        assertTrue(CountdownPrefs.THEME_PERSONAL !in CountdownPrefs.builtInThemeIndices)
        CountdownPrefs.builtInThemeIndices.forEach { theme ->
            val bitmap = prefs.backgroundBitmapForTheme(theme)
            assertNotNull("Missing image for theme $theme", bitmap)
            assertTrue("Theme $theme must be portrait-oriented", bitmap!!.height > bitmap.width)
        }
    }

    @Test fun widgetBackgroundDoesNotDependOnAppTheme() {
        val prefs = CountdownPrefs(context)
        val previousTheme = prefs.theme
        try {
            prefs.theme = CountdownPrefs.THEME_GOLD
            val first = prefs.widgetBackgroundBitmap()
            assertNotNull(first)
            prefs.theme = CountdownPrefs.THEME_PERSONAL
            val second = prefs.widgetBackgroundBitmap()
            assertNotNull(second)
            assertTrue("Widget background must not change with app theme", first!!.sameAs(second!!))
            assertTrue("Widget background should be landscape", first.width > first.height)
        } finally {
            prefs.theme = previousTheme
        }
    }
}
