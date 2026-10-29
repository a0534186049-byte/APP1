package com.neonevent.countdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.icu.util.HebrewCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

class CountdownPrefs(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("event_countdown_settings", Context.MODE_PRIVATE)
    private var cachedBitmapKey: String? = null
    private var cachedBitmap: Bitmap? = null
    private var cachedBackdropKey: String? = null
    private var cachedBackdropBitmap: Bitmap? = null

    companion object {
        const val THEME_GOLD = 0
        const val THEME_GRADIENT = 1
        const val THEME_PREMIUM = 2
        const val THEME_PERSONAL = 3

        const val CUBES_DARK = 0
        const val CUBES_LIGHT = 1
        const val CUBES_FROSTED = 2

        val themeNames = listOf("יוקרה בזהב", "מעבר צבעים מודרני", "תלת־ממד פרימיום")
        val cubeNames = listOf("כהה", "בהיר", "זכוכית חלבית")
        private val hebrewLocale = Locale("he", "IL")
    }

    init {
        migrateLegacySettings()
    }

    var theme: Int
        get() = prefs.getInt("design_theme", THEME_GOLD).coerceIn(THEME_GOLD, THEME_PERSONAL)
        set(value) { prefs.edit().putInt("design_theme", value.coerceIn(THEME_GOLD, THEME_PERSONAL)).apply() }

    var personalImageUri: String?
        get() = prefs.getString("personal_image_uri", null)
        set(value) { prefs.edit().putString("personal_image_uri", value).apply() }

    var cubeStyle: Int
        get() = prefs.getInt("personal_cube_style", CUBES_FROSTED).coerceIn(CUBES_DARK, CUBES_FROSTED)
        set(value) { prefs.edit().putInt("personal_cube_style", value.coerceIn(CUBES_DARK, CUBES_FROSTED)).apply() }

    var targetHebrewYear: Int
        get() = prefs.getInt("hebrew_year", defaultHebrewDate().get(Calendar.YEAR))
        private set(value) { prefs.edit().putInt("hebrew_year", value).apply() }

    var targetHebrewMonth: Int
        get() = prefs.getInt("hebrew_month", defaultHebrewDate().get(Calendar.MONTH)).coerceAtLeast(0)
        private set(value) { prefs.edit().putInt("hebrew_month", value).apply() }

    var targetHebrewDay: Int
        get() = prefs.getInt("hebrew_day", defaultHebrewDate().get(Calendar.DAY_OF_MONTH)).coerceAtLeast(1)
        private set(value) { prefs.edit().putInt("hebrew_day", value).apply() }

    var targetHour: Int
        get() = prefs.getInt("target_hour", 20).coerceIn(0, 23)
        set(value) { prefs.edit().putInt("target_hour", value.coerceIn(0, 23)).apply() }

    var targetMinute: Int
        get() = prefs.getInt("target_minute", 0).coerceIn(0, 59)
        set(value) { prefs.edit().putInt("target_minute", value.coerceIn(0, 59)).apply() }

    var eventTitle: String
        get() = prefs.getString("event_title", "הרגע הגדול מתקרב") ?: "הרגע הגדול מתקרב"
        set(value) { prefs.edit().putString("event_title", value.take(70)).apply() }

    var showTitle: Boolean
        get() = prefs.getBoolean("show_title", true)
        set(value) { prefs.edit().putBoolean("show_title", value).apply() }

    var unitsMask: Int
        get() = prefs.getInt("units_mask", 15).coerceIn(0, 15)
        set(value) { prefs.edit().putInt("units_mask", value.coerceIn(0, 15)).apply() }

    fun isUnitVisible(index: Int): Boolean = (unitsMask and (1 shl index)) != 0

    fun setHebrewDate(year: Int, month: Int, day: Int) {
        val cal = hebrewCalendar(year, month, day, 0, 0)
        prefs.edit()
            .putInt("hebrew_year", cal.get(Calendar.YEAR))
            .putInt("hebrew_month", cal.get(Calendar.MONTH))
            .putInt("hebrew_day", cal.get(Calendar.DAY_OF_MONTH))
            .putInt("target_hour", 0)
            .putInt("target_minute", 0)
            .putBoolean("hebrew_date_migrated", true)
            .apply()
    }

    fun targetCalendar(): HebrewCalendar = hebrewCalendar(
        targetHebrewYear, targetHebrewMonth, targetHebrewDay, 0, 0
    )

    fun eventLocalDate(): LocalDate = Instant.ofEpochMilli(targetCalendar().timeInMillis)
        .atZone(ZoneId.systemDefault()).toLocalDate()

    fun dailyQuote(nowMillis: Long): String =
        DailyQuoteProvider.quote(appContext, nowMillis, runCatching { eventLocalDate() }.getOrNull())

    fun targetMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        runCatching { targetCalendar().timeInMillis }.getOrElse { nowMillis }

    fun targetKey(): String = "$targetHebrewYear|$targetHebrewMonth|$targetHebrewDay"

    fun formattedTargetDate(): String {
        val cal = targetCalendar()
        val monthName = monthOptions(cal.get(Calendar.YEAR))
            .firstOrNull { it.first == cal.get(Calendar.MONTH) }?.second ?: "חודש"
        val day = hebrewYearNumeral(cal.get(Calendar.DAY_OF_MONTH))
        val year = hebrewYearNumeral(cal.get(Calendar.YEAR))
        return "$day ב$monthName $year"
    }

    fun formattedTargetDateWithTime(): String = formattedTargetDate()

    fun backgroundBitmap(): Bitmap? {
        val key = if (theme == THEME_PERSONAL) "personal:" + (personalImageUri ?: "") else "theme:" + theme
        if (key == cachedBitmapKey) return cachedBitmap
        cachedBitmapKey = key
        cachedBitmap = if (theme == THEME_PERSONAL) {
            runCatching {
                val uri = personalImageUri?.let(Uri::parse) ?: return@runCatching null
                appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        } else {
            backgroundBitmapForTheme(theme)
        }
        return cachedBitmap
    }

    /**
     * A tiny version of the selected artwork used only to fill empty screen edges.
     * The full-resolution source remains sharp and proportionally fitted above it.
     */
    fun backgroundBackdropBitmap(): Bitmap? {
        val key = if (theme == THEME_PERSONAL) "personal:" + (personalImageUri ?: "") else "theme:$theme"
        if (key == cachedBackdropKey) return cachedBackdropBitmap
        cachedBackdropKey = key
        cachedBackdropBitmap = runCatching {
            val source = backgroundBitmap() ?: return@runCatching null
            val maxDimension = maxOf(source.width, source.height).coerceAtLeast(1)
            val ratio = minOf(1f, 96f / maxDimension.toFloat())
            val width = (source.width * ratio).toInt().coerceAtLeast(1)
            val height = (source.height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(source, width, height, true)
        }.getOrNull()
        return cachedBackdropBitmap
    }

    fun backgroundBitmapForTheme(themeIndex: Int): Bitmap? {
        val name = when (themeIndex) {
            THEME_GRADIENT -> "backgrounds/gradient.webp"
            THEME_PREMIUM -> "backgrounds/premium.webp"
            else -> "backgrounds/gold.webp"
        }
        return runCatching {
            appContext.assets.open(name).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    fun resetEventSettings() {
        eventTitle = "הרגע הגדול מתקרב"
        showTitle = true
        unitsMask = 15
    }

    private fun migrateLegacySettings() {
        if (prefs.getBoolean("hebrew_date_migrated", false)) return
        val legacyDate = prefs.getString("target_date", null)
        val cal = if (legacyDate != null) {
            runCatching {
                val d = LocalDate.parse(legacyDate)
                val greg = java.util.GregorianCalendar(d.year, d.monthValue - 1, d.dayOfMonth)
                HebrewCalendar(hebrewLocale).apply { timeInMillis = greg.timeInMillis }
            }.getOrElse { defaultHebrewDate() }
        } else {
            defaultHebrewDate()
        }
        val oldIgnoreTime = prefs.getBoolean("ignore_time", false)
        prefs.edit()
            .putInt("hebrew_year", cal.get(Calendar.YEAR))
            .putInt("hebrew_month", cal.get(Calendar.MONTH))
            .putInt("hebrew_day", cal.get(Calendar.DAY_OF_MONTH))
            .putInt("target_hour", if (oldIgnoreTime) 0 else prefs.getInt("target_hour", 20))
            .putInt("target_minute", if (oldIgnoreTime) 0 else prefs.getInt("target_minute", 0))
            .putBoolean("hebrew_date_migrated", true)
            .apply()
    }

    private fun defaultHebrewDate(): HebrewCalendar =
        HebrewCalendar(hebrewLocale).apply { add(Calendar.DAY_OF_MONTH, 30) }

    private fun hebrewCalendar(year: Int, month: Int, day: Int, hour: Int, minute: Int): HebrewCalendar {
        val cal = HebrewCalendar(hebrewLocale)
        cal.clear()
        cal.set(Calendar.YEAR, year.coerceAtLeast(1))
        cal.set(Calendar.MONTH, month.coerceAtLeast(0))
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        cal.set(Calendar.DAY_OF_MONTH, day.coerceIn(1, maxDay))
        cal.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
        cal.set(Calendar.MINUTE, minute.coerceIn(0, 59))
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    fun monthOptions(year: Int): List<Pair<Int, String>> {
        val safeYear = year.coerceAtLeast(1)
        // ICU keeps the actual month field IDs stable: ADAR_1 is field 5 and is
        // absent in non-leap years, where the calendar jumps directly to ADAR (6).
        val isLeapYear = ((7L * safeYear + 1L) % 19L) < 7L
        val namesByMonth = mapOf(
            0 to "תשרי",
            1 to "חשוון",
            2 to "כסלו",
            3 to "טבת",
            4 to "שבט",
            5 to "אדר א׳",
            6 to if (isLeapYear) "אדר ב׳" else "אדר",
            7 to "ניסן",
            8 to "אייר",
            9 to "סיוון",
            10 to "תמוז",
            11 to "אב",
            12 to "אלול"
        )
        val monthIds = if (isLeapYear) (0..12).toList() else (0..4).toList() + (6..12).toList()
        return monthIds.map { month -> month to namesByMonth.getValue(month) }
    }

    fun daysInHebrewMonth(year: Int, month: Int): Int {
        val cal = hebrewCalendar(year, month, 1, 0, 0)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
    }

    fun firstWeekdayOfHebrewMonth(year: Int, month: Int): Int {
        val cal = hebrewCalendar(year, month, 1, 0, 0)
        return cal.get(Calendar.DAY_OF_WEEK) - 1
    }

    private fun hebrewYearNumeral(year: Int): String {
        val n = year % 1000
        if (n <= 0) return year.toString()
        var rest = n
        val letters = StringBuilder()
        while (rest >= 400) { letters.append('ת'); rest -= 400 }
        when {
            rest >= 300 -> { letters.append('ש'); rest -= 300 }
            rest >= 200 -> { letters.append('ר'); rest -= 200 }
            rest >= 100 -> { letters.append('ק'); rest -= 100 }
        }
        if (rest == 15) {
            letters.append("טו")
            rest = 0
        } else if (rest == 16) {
            letters.append("טז")
            rest = 0
        } else {
            val tens = when {
                rest >= 90 -> 90
                rest >= 80 -> 80
                rest >= 70 -> 70
                rest >= 60 -> 60
                rest >= 50 -> 50
                rest >= 40 -> 40
                rest >= 30 -> 30
                rest >= 20 -> 20
                rest >= 10 -> 10
                else -> 0
            }
            if (tens > 0) {
                letters.append(when (tens) {
                    90 -> 'צ'; 80 -> 'פ'; 70 -> 'ע'; 60 -> 'ס'; 50 -> 'נ'; 40 -> 'מ'
                    30 -> 'ל'; 20 -> 'כ'; else -> 'י'
                })
                rest -= tens
            }
            if (rest > 0) letters.append(when (rest) {
                9 -> 'ט'; 8 -> 'ח'; 7 -> 'ז'; 6 -> 'ו'; 5 -> 'ה'; 4 -> 'ד'
                3 -> 'ג'; 2 -> 'ב'; else -> 'א'
            })
        }
        if (letters.length == 1) letters.append('׳')
        else if (letters.length > 1) letters.insert(letters.length - 1, '״')
        return letters.toString()
    }
}
