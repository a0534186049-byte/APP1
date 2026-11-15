package com.neonevent.countdown

import android.icu.util.Calendar
import android.icu.util.HebrewCalendar
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import android.icu.util.TimeZone as IcuTimeZone
import java.util.TimeZone as JavaTimeZone

@RunWith(AndroidJUnit4::class)
class HebrewCalendarInstrumentedTest {
    private val locale = Locale("he", "IL")
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun makeHebrewCalendar(year: Int, month: Int, day: Int, zone: IcuTimeZone): HebrewCalendar {
        return HebrewCalendar(zone, locale).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
        }
    }

    @Test fun testLeapYearsExposeAdarOneAndAdarTwo() {
        val prefs = CountdownPrefs(context)
        assertTrue(prefs.isHebrewLeapYear(5784))
        assertTrue(prefs.isHebrewLeapYear(5787))
        assertFalse(prefs.isHebrewLeapYear(5785))

        val leap5784 = prefs.monthOptions(5784)
        assertTrue(leap5784.any { it.first == CountdownPrefs.ADAR_1 && it.second == "אדר א׳" })
        assertTrue(leap5784.any { it.first == CountdownPrefs.ADAR && it.second == "אדר ב׳" })

        val leap5787 = prefs.monthOptions(5787)
        assertTrue(leap5787.any { it.first == CountdownPrefs.ADAR_1 })
        assertTrue(leap5787.any { it.first == CountdownPrefs.ADAR })

        val common5785 = prefs.monthOptions(5785)
        assertFalse(common5785.any { it.first == CountdownPrefs.ADAR_1 })
        assertTrue(common5785.any { it.first == CountdownPrefs.ADAR && it.second == "אדר" })
    }

    @Test fun testHeshvanAndKislevDayThirtyOnlyWhenIcuAllowsIt() {
        val prefs = CountdownPrefs(context)
        val zone = IcuTimeZone.getDefault()
        for (year in 5770..5800) {
            for (month in listOf(CountdownPrefs.HESHVAN, CountdownPrefs.KISLEV)) {
                val calendar = makeHebrewCalendar(year, month, 1, zone)
                val icuMaximum = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                val days = prefs.hebrewDayOptions(year, month)
                assertTrue("Unexpected month length for $year/$month: $icuMaximum", icuMaximum == 29 || icuMaximum == 30)
                assertEquals(icuMaximum, days.size)
                assertEquals(icuMaximum, days.last())
                assertEquals(icuMaximum == 30, days.contains(30))
            }
        }
    }

    @Test fun testTenHebrewDatesRoundTripThroughLocalCivilDate() {
        val previousZone = JavaTimeZone.getDefault()
        val javaZone = JavaTimeZone.getTimeZone("Asia/Jerusalem")
        val zone = IcuTimeZone.getTimeZone("Asia/Jerusalem")
        JavaTimeZone.setDefault(javaZone)
        try {
        val zoneId = ZoneId.of("Asia/Jerusalem")
        val samples = listOf(
            Triple(5784, CountdownPrefs.TISHRI, 1),   // Rosh Hashanah
            Triple(5784, CountdownPrefs.HESHVAN, 1),  // Rosh Chodesh
            Triple(5784, CountdownPrefs.KISLEV, 1),   // Rosh Chodesh
            Triple(5784, CountdownPrefs.SHEVAT, 1),   // Rosh Chodesh
            Triple(5784, CountdownPrefs.ADAR_1, 29),
            Triple(5784, CountdownPrefs.ADAR, 29),    // 29 Adar II
            Triple(5784, CountdownPrefs.NISAN, 1),    // Rosh Chodesh
            Triple(5785, CountdownPrefs.ELUL, 29),    // Eve of Rosh Hashanah
            Triple(5785, CountdownPrefs.TISHRI, 1),
            Triple(5785, CountdownPrefs.ADAR, 29)     // 29 Adar in a regular year
        )

        samples.forEach { (year, month, day) ->
            val source = makeHebrewCalendar(year, month, day, zone)
            val localDate = Instant.ofEpochMilli(source.timeInMillis).atZone(zoneId).toLocalDate()
            val reverseMillis = localDate.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
            val roundTrip = HebrewCalendar(zone, locale).apply { timeInMillis = reverseMillis }

            assertEquals("year after round trip for $year/$month/$day", year, roundTrip.get(Calendar.YEAR))
            assertEquals("month after round trip for $year/$month/$day", month, roundTrip.get(Calendar.MONTH))
            assertEquals("day after round trip for $year/$month/$day", day, roundTrip.get(Calendar.DAY_OF_MONTH))
        }
        } finally {
            JavaTimeZone.setDefault(previousZone)
        }
    }

    @Test fun testHebrewDateSurvivesPreferencesReload() {
        val context = context
        val store = context.getSharedPreferences("event_countdown_settings", android.content.Context.MODE_PRIVATE)
        store.edit().clear().commit()
        try {
            CountdownPrefs(context).setHebrewDate(5784, CountdownPrefs.ADAR_1, 29)
            val reopened = CountdownPrefs(context)
            assertEquals(5784, reopened.targetHebrewYear)
            assertEquals(CountdownPrefs.ADAR_1, reopened.targetHebrewMonth)
            assertEquals(29, reopened.targetHebrewDay)
            assertEquals("כ״ט באדר א׳ תשפ״ד", reopened.formattedTargetDate())
        } finally {
            store.edit().clear().commit()
        }
    }

    @Test fun testDefaultDateIsPersistedWhenNoDateWasSaved() {
        val context = context
        val store = context.getSharedPreferences("event_countdown_settings", android.content.Context.MODE_PRIVATE)
        store.edit().clear().commit()
        try {
            val firstLoad = CountdownPrefs(context)
            val values = Triple(firstLoad.targetHebrewYear, firstLoad.targetHebrewMonth, firstLoad.targetHebrewDay)
            val secondLoad = CountdownPrefs(context)
            assertEquals(values.first, secondLoad.targetHebrewYear)
            assertEquals(values.second, secondLoad.targetHebrewMonth)
            assertEquals(values.third, secondLoad.targetHebrewDay)
        } finally {
            store.edit().clear().commit()
        }
    }

    @Test fun testHebrewDateFormattingUsesHebrewDayAndYearNumerals() {
        val prefs = CountdownPrefs(context)
        assertEquals("ט״ו", prefs.hebrewNumber(15))
        assertEquals("ט״ז", prefs.hebrewNumber(16))
        assertEquals("ל׳", prefs.hebrewNumber(30))
        assertEquals("תשפ״ז", prefs.hebrewYearLabel(5787))
    }
}
