package com.neonevent.countdown

import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.math.floor

@RunWith(AndroidJUnit4::class)
class DailyQuoteAssetInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun bundledUtf8AssetContainsAllTwoHundredAndTenRegularAndEightSpecialQuotes() {
        val raw = context.assets.open("daily_quotes_200_he.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(raw)
        val normal = root.getJSONArray("daily_quotes")
        val special = root.getJSONObject("special_days_remaining")

        assertEquals(210, normal.length())
        assertEquals(8, special.length())
        assertEquals(
            setOf("0", "1", "2", "3", "7", "10", "14", "30"),
            special.keys().asSequence().toSet()
        )
        assertEquals("he", root.getString("language"))
        assertTrue(root.getBoolean("offline"))
        assertTrue(normal.getString(0).any { it in '\u0590'..'\u05FF' })
    }

    @Test fun theLongestBundledQuoteIsFortySevenCharactersAndPreservesPunctuation() {
        val raw = context.assets.open("daily_quotes_200_he.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val normal = JSONObject(raw).getJSONArray("daily_quotes")
        val longest = (0 until normal.length()).map { normal.getString(it) }.maxBy { it.length() }

        assertEquals(47, longest.length())
        assertTrue(longest.contains("—") || longest.contains(",") || longest.contains("."))
    }

    @Test fun theExactBundledQuoteIsReturnedWithoutTrimmingOrRewriting() {
        val raw = context.assets.open("daily_quotes_200_he.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val first = JSONObject(raw).getJSONArray("daily_quotes").getString(0)
        val parsed = DailyQuoteProvider.parseJson(raw) ?: error("Valid bundled quote asset was rejected")
        val today = LocalDate.ofEpochDay(0)

        assertEquals(first, DailyQuoteProvider.selectQuote(today, null, parsed.normal, parsed.special))
    }

    @Test fun specialAndRegularSelectionUsesOnlyLocalDates() {
        val raw = context.assets.open("daily_quotes_200_he.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val parsed = DailyQuoteProvider.parseJson(raw) ?: error("Valid bundled quote asset was rejected")
        val today = LocalDate.of(2026, 11, 1)

        listOf(30, 14, 10, 7, 3, 2, 1, 0).forEach { days ->
            val expected = parsed.special[days] ?: error("Missing special day $days")
            assertEquals(expected, DailyQuoteProvider.selectQuote(today, today.plusDays(days.toLong()), parsed.normal, parsed.special))
        }
        listOf(29, 15, 5, 100).forEach { days ->
            val expected = parsed.normal[Math.floorMod(today.toEpochDay(), parsed.normal.size.toLong()).toInt()]
            assertEquals(expected, DailyQuoteProvider.selectQuote(today, today.plusDays(days.toLong()), parsed.normal, parsed.special))
        }
        val pastExpected = parsed.normal[Math.floorMod(today.toEpochDay(), parsed.normal.size.toLong()).toInt()]
        assertEquals(pastExpected, DailyQuoteProvider.selectQuote(today, today.minusDays(1), parsed.normal, parsed.special))
    }

    @Test fun aBlankResultIsNotReplacedWithAnInventedFallback() {
        assertEquals("", DailyQuoteProvider.selectQuote(LocalDate.of(2026, 11, 1), null, emptyList(), emptyMap()))
        assertFalse(DailyQuoteProvider.parseJson("{") != null)
    }

    @Test fun theLongestQuoteFitsWithoutDroppingWordsOnPortraitPhoneGeometry() {
        val raw = context.assets.open("daily_quotes_200_he.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val normal = JSONObject(raw).getJSONArray("daily_quotes")
        val longest = (0 until normal.length()).map { normal.getString(it) }.maxBy { it.length() }

        val metrics = context.resources.displayMetrics
        val geometry = HomeScreenGeometryCalculator.calculate(
            widthPx = 1080f,
            heightPx = 2400f,
            density = metrics.density,
            scaledDensity = metrics.scaledDensity,
            showDays = true,
            lowerCount = 3
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = context.resources.getFont(R.font.heebo)
            textAlign = Paint.Align.CENTER
        }
        var size = 18f * metrics.scaledDensity
        val minimumSize = 14f * metrics.scaledDensity
        var lines = emptyList<String>()
        var maxLines = 1
        while (true) {
            paint.textSize = size
            lines = NeonCountdownRenderer.wrapToWidth(longest, paint, 1080f * 0.90f)
            val fontMetrics = paint.fontMetrics
            val lineHeight = maxOf(size * 1.22f, fontMetrics.descent - fontMetrics.ascent)
            maxLines = floor(geometry.quoteMaxHeight / lineHeight).toInt().coerceIn(1, 5)
            if (lines.size <= maxLines || size <= minimumSize) break
            size = (size - maxOf(0.75f, size * 0.04f)).coerceAtLeast(minimumSize)
        }

        assertEquals(47, longest.length)
        assertTrue("Quote requires ${lines.size} lines but only ${maxLines} fit", lines.size <= maxLines)
        assertEquals("The full quote must be preserved across wrapped lines", longest, lines.joinToString(" "))
    }

    @Test fun missingOrBrokenJsonDoesNotCrashAndDoesNotShowInventedText() {
        assertEquals(null, DailyQuoteProvider.parseJson("{"))
        assertEquals(null, DailyQuoteProvider.parseJson("{\"daily_quotes\":[]}"))
        assertEquals(null, DailyQuoteProvider.parseJson("{\"special_days_remaining\":{}}"))
    }

    @Test fun invalidSpecialDaysKeepsTheValidNormalQuoteList() {
        val parsed = DailyQuoteProvider.parseJson(
            "{\"daily_quotes\":[\"original\"],\"special_days_remaining\":[]}"
        )
        assertEquals(listOf("original"), parsed?.normal)
        assertTrue(parsed?.special.isNullOrEmpty())
    }
}
