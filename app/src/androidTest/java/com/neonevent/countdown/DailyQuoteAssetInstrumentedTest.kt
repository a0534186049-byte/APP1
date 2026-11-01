package com.neonevent.countdown

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

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
}
