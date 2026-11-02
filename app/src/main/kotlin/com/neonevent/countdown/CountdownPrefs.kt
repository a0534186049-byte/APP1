package com.neonevent.countdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.icu.util.Calendar
import android.icu.util.HebrewCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import android.icu.util.TimeZone

class CountdownPrefs(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("event_countdown_settings", Context.MODE_PRIVATE)
    private var cachedBitmapKey: String? = null
    private var cachedBitmap: Bitmap? = null
    private var cachedAutoContrastKey: String? = null
    private var cachedAutoContrastColor: Int? = null
    private val headingTypeface by lazy(LazyThreadSafetyMode.NONE) {
        appContext.resources.getFont(R.font.secular_one)
    }
    private val bodyTypeface by lazy(LazyThreadSafetyMode.NONE) {
        appContext.resources.getFont(R.font.heebo)
    }

    fun homeHeadingTypeface(): android.graphics.Typeface = headingTypeface
    fun homeBodyTypeface(): android.graphics.Typeface = bodyTypeface
    fun sp(value: Float): Float = value * appContext.resources.displayMetrics.scaledDensity
    fun dp(value: Float): Float = value * appContext.resources.displayMetrics.density

    companion object {
        const val THEME_GOLD = 0
        const val THEME_GRADIENT = 1
        const val THEME_PREMIUM = 2
        const val THEME_PERSONAL = 3

        const val CUBES_DARK = 0
        const val CUBES_LIGHT = 1
        const val CUBES_FROSTED = 2
        const val CUBES_AUTO = 3

        // ICU HebrewCalendar month field values, in civil-year order.
        const val TISHRI = 0
        const val HESHVAN = 1
        const val KISLEV = 2
        const val TEVET = 3
        const val SHEVAT = 4
        const val ADAR_1 = 5
        const val ADAR = 6
        const val NISAN = 7
        const val IYAR = 8
        const val SIVAN = 9
        const val TAMMUZ = 10
        const val AV = 11
        const val ELUL = 12

        val themeNames = listOf("יוקרה בזהב", "מעבר צבעים מודרני", "תלת־ממד פרימיום")
        val cubeNames = listOf("כהה", "בהיר", "זכוכית", "ניגודי אוטומטי")
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
        get() = prefs.getInt("personal_cube_style", CUBES_DARK).coerceIn(CUBES_DARK, CUBES_AUTO)
        set(value) { prefs.edit().putInt("personal_cube_style", value.coerceIn(CUBES_DARK, CUBES_AUTO)).apply() }

    var targetHebrewYear: Int
        get() = prefs.getInt("hebrew_year", defaultHebrewDate().get(Calendar.YEAR))
        private set(value) { prefs.edit().putInt("hebrew_year", value).apply() }

    var targetHebrewMonth: Int
        get() = prefs.getInt("hebrew_month", defaultHebrewDate().get(Calendar.MONTH))
        private set(value) { prefs.edit().putInt("hebrew_month", value).apply() }

    var targetHebrewDay: Int
        get() = prefs.getInt("hebrew_day", defaultHebrewDate().get(Calendar.DAY_OF_MONTH)).coerceAtLeast(1)
        private set(value) { prefs.edit().putInt("hebrew_day", value).apply() }

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
        val normalizedMonth = normalizeMonth(year, month)
        val cal = hebrewCalendar(year, normalizedMonth, day)
        prefs.edit()
            .putInt("hebrew_year", cal.get(Calendar.YEAR))
            .putInt("hebrew_month", cal.get(Calendar.MONTH))
            .putInt("hebrew_day", cal.get(Calendar.DAY_OF_MONTH))
            .putBoolean("hebrew_date_migrated", true)
            .apply()
    }

    fun targetCalendar(): HebrewCalendar =
        hebrewCalendar(targetHebrewYear, normalizeMonth(targetHebrewYear, targetHebrewMonth), targetHebrewDay)

    /** Convert the selected Hebrew date to the device's local civil date. */
    fun eventLocalDate(): LocalDate = Instant.ofEpochMilli(targetCalendar().timeInMillis)
        .atZone(ZoneId.systemDefault()).toLocalDate()

    fun dailyQuote(nowMillis: Long): String =
        DailyQuoteProvider.quote(appContext, nowMillis, runCatching { eventLocalDate() }.getOrNull())

    fun targetKey(): String = "$targetHebrewYear|$targetHebrewMonth|$targetHebrewDay"

    fun formattedTargetDate(): String =
        formatHebrewDate(targetHebrewYear, targetHebrewMonth, targetHebrewDay)

    fun formatHebrewDate(year: Int, month: Int, day: Int): String {
        val safeMonth = normalizeMonth(year, month)
        return "${hebrewNumber(day)} ב${monthName(year, safeMonth)} ${hebrewYearLabel(year)}"
    }

    fun currentHebrewYear(): Int = HebrewCalendar(TimeZone.getDefault(), hebrewLocale).get(Calendar.YEAR)

    fun hebrewYearLabel(year: Int): String = hebrewNumber(year % 1000)

    fun hebrewNumber(value: Int): String {
        var rest = value.coerceAtLeast(0) % 1000
        if (rest == 0) return "תתק״ס"
        val letters = StringBuilder()
        while (rest >= 400) { letters.append('ת'); rest -= 400 }
        when {
            rest >= 300 -> { letters.append('ש'); rest -= 300 }
            rest >= 200 -> { letters.append('ר'); rest -= 200 }
            rest >= 100 -> { letters.append('ק'); rest -= 100 }
        }
        when (rest) {
            15 -> { letters.append("טו"); rest = 0 }
            16 -> { letters.append("טז"); rest = 0 }
        }
        if (rest > 0) {
            val tens = when {
                rest >= 90 -> 90; rest >= 80 -> 80; rest >= 70 -> 70
                rest >= 60 -> 60; rest >= 50 -> 50; rest >= 40 -> 40
                rest >= 30 -> 30; rest >= 20 -> 20; rest >= 10 -> 10
                else -> 0
            }
            if (tens > 0) {
                letters.append(when (tens) {
                    90 -> 'צ'; 80 -> 'פ'; 70 -> 'ע'; 60 -> 'ס'; 50 -> 'נ'
                    40 -> 'מ'; 30 -> 'ל'; 20 -> 'כ'; else -> 'י'
                })
                rest -= tens
            }
            if (rest > 0) letters.append(when (rest) {
                9 -> 'ט'; 8 -> 'ח'; 7 -> 'ז'; 6 -> 'ו'; 5 -> 'ה'
                4 -> 'ד'; 3 -> 'ג'; 2 -> 'ב'; else -> 'א'
            })
        }
        if (letters.length == 1) letters.append('׳')
        else if (letters.length > 1) letters.insert(letters.length - 1, '״')
        return letters.toString()
    }

    /**
     * Android's public SDK hides HebrewCalendar.isLeapYear(int) as ICU-internal.
     * Use ICU's own calculated year length instead of duplicating any calendar rule.
     */
    fun isHebrewLeapYear(year: Int): Boolean {
        val cal = newHebrewCalendar()
        cal.clear()
        cal.set(Calendar.YEAR, year.coerceAtLeast(1))
        cal.set(Calendar.MONTH, TISHRI)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return cal.getActualMaximum(Calendar.DAY_OF_YEAR) > 355
    }

    fun monthOptions(year: Int): List<Pair<Int, String>> {
        val months = (TISHRI..ELUL).filter { it != ADAR_1 || isHebrewLeapYear(year) }
        return months.map { month -> month to monthName(year, month) }
    }

    fun daysInHebrewMonth(year: Int, month: Int): Int {
        val cal = hebrewCalendar(year, normalizeMonth(year, month), 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
    }

    fun hebrewDayOptions(year: Int, month: Int): List<Int> =
        (1..daysInHebrewMonth(year, month)).toList()

    private fun monthName(year: Int, month: Int): String = when (month) {
        TISHRI -> "תשרי"
        HESHVAN -> "חשוון"
        KISLEV -> "כסלו"
        TEVET -> "טבת"
        SHEVAT -> "שבט"
        ADAR_1 -> "אדר א׳"
        ADAR -> if (isHebrewLeapYear(year)) "אדר ב׳" else "אדר"
        NISAN -> "ניסן"
        IYAR -> "אייר"
        SIVAN -> "סיוון"
        TAMMUZ -> "תמוז"
        AV -> "אב"
        ELUL -> "אלול"
        else -> "חודש"
    }

    private fun normalizeMonth(year: Int, month: Int): Int {
        val bounded = month.coerceIn(TISHRI, ELUL)
        return if (bounded == ADAR_1 && !isHebrewLeapYear(year)) ADAR else bounded
    }

    private fun newHebrewCalendar(): HebrewCalendar =
        HebrewCalendar(TimeZone.getDefault(), hebrewLocale)

    private fun hebrewCalendar(year: Int, month: Int, day: Int): HebrewCalendar {
        val cal = newHebrewCalendar()
        cal.clear()
        cal.set(Calendar.YEAR, year.coerceAtLeast(1))
        cal.set(Calendar.MONTH, normalizeMonth(year, month))
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        cal.set(Calendar.DAY_OF_MONTH, day.coerceIn(1, maxDay))
        return cal
    }

    private fun defaultHebrewDate(): HebrewCalendar =
        newHebrewCalendar().apply { add(Calendar.DAY_OF_MONTH, 30) }

    private fun migrateLegacySettings() {
        val hasCompleteHebrewDate = prefs.contains("hebrew_year") &&
            prefs.contains("hebrew_month") && prefs.contains("hebrew_day")
        if (hasCompleteHebrewDate) return

        // One-time compatibility import for installations that still only have the
        // former date key. Convert it immediately with ICU and persist Hebrew fields.
        val imported = prefs.getString("target_date", null)?.let { legacy ->
            runCatching {
                val localDate = LocalDate.parse(legacy)
                val instant = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                newHebrewCalendar().apply { timeInMillis = instant }
            }.getOrNull()
        }
        val date = imported ?: defaultHebrewDate()
        prefs.edit()
            .putInt("hebrew_year", date.get(Calendar.YEAR))
            .putInt("hebrew_month", date.get(Calendar.MONTH))
            .putInt("hebrew_day", date.get(Calendar.DAY_OF_MONTH))
            .putBoolean("hebrew_date_migrated", true)
            .apply()
    }

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


    /**
     * Cache the 65-pixel image analysis for the selected image. Changing the image URI
     * changes the key automatically, so the next draw performs a fresh analysis.
     */
    fun personalAutoContrastTextColor(): Int {
        val key = personalImageUri ?: "missing-personal-image"
        if (cachedAutoContrastKey == key) {
            cachedAutoContrastColor?.let { return it }
        }
        val selectedColor = PersonalTextContrast.recommendedTextColor(backgroundBitmap())
        cachedAutoContrastKey = key
        cachedAutoContrastColor = selectedColor
        return selectedColor
    }

    fun personalAutoContrastIsDark(): Boolean =
        personalAutoContrastTextColor() == PersonalTextContrast.AUTO_DARK_TEXT

    fun resetEventSettings() {
        eventTitle = "הרגע הגדול מתקרב"
        showTitle = true
        unitsMask = 15
    }
}
