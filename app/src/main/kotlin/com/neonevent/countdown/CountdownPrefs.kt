package com.neonevent.countdown

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CountdownPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("event_countdown_settings", Context.MODE_PRIVATE)

    companion object {
        const val COMPLETION_ZERO = 0
        const val COMPLETION_MESSAGE = 1
        const val COMPLETION_GLOW = 2

        val themeNames = listOf("כחול ניאון", "סגול עתידני", "ורוד חשמלי", "טורקיז ואור")
        val themeColors = listOf(
            intArrayOf(0xFF25D9FF.toInt(), 0xFF4A77FF.toInt(), 0xFFB55CFF.toInt()),
            intArrayOf(0xFFB47CFF.toInt(), 0xFF6D5BFF.toInt(), 0xFFFF67D9.toInt()),
            intArrayOf(0xFFFF63C8.toInt(), 0xFFFF8A68.toInt(), 0xFF9D72FF.toInt()),
            intArrayOf(0xFF47FFE0.toInt(), 0xFF35CFFF.toInt(), 0xFFB8FF70.toInt())
        )
        val accentNames = listOf("כחול", "טורקיז", "סגול", "ורוד", "ליים", "לבן")
        val accentColors = listOf(
            0xFF25D9FF.toInt(), 0xFF38F2D2.toInt(), 0xFFB47CFF.toInt(),
            0xFFFF63C8.toInt(), 0xFFB8FF70.toInt(), 0xFFEAF7FF.toInt()
        )
    }

    var targetDate: String
        get() = prefs.getString("target_date", LocalDate.now().plusDays(30).toString()) ?: LocalDate.now().plusDays(30).toString()
        set(value) { prefs.edit().putString("target_date", value).apply() }

    var targetHour: Int
        get() = prefs.getInt("target_hour", 20).coerceIn(0, 23)
        set(value) { prefs.edit().putInt("target_hour", value.coerceIn(0, 23)).apply() }

    var targetMinute: Int
        get() = prefs.getInt("target_minute", 0).coerceIn(0, 59)
        set(value) { prefs.edit().putInt("target_minute", value.coerceIn(0, 59)).apply() }

    var ignoreTime: Boolean
        get() = prefs.getBoolean("ignore_time", false)
        set(value) { prefs.edit().putBoolean("ignore_time", value).apply() }

    var eventTitle: String
        get() = prefs.getString("event_title", "הרגע הגדול מתקרב") ?: "הרגע הגדול מתקרב"
        set(value) { prefs.edit().putString("event_title", value.take(70)).apply() }

    var showTitle: Boolean
        get() = prefs.getBoolean("show_title", true)
        set(value) { prefs.edit().putBoolean("show_title", value).apply() }

    var theme: Int
        get() = prefs.getInt("theme", 0).coerceIn(themeNames.indices)
        set(value) { prefs.edit().putInt("theme", value.coerceIn(themeNames.indices)).apply() }

    var accent: Int
        get() = prefs.getInt("accent", 0).coerceIn(accentColors.indices)
        set(value) { prefs.edit().putInt("accent", value.coerceIn(accentColors.indices)).apply() }

    var glow: Int
        get() = prefs.getInt("glow", 75).coerceIn(0, 100)
        set(value) { prefs.edit().putInt("glow", value.coerceIn(0, 100)).apply() }

    var animationSpeed: Int
        get() = prefs.getInt("animation_speed", 2).coerceIn(0, 3)
        set(value) { prefs.edit().putInt("animation_speed", value.coerceIn(0, 3)).apply() }

    var completionMode: Int
        get() = prefs.getInt("completion_mode", COMPLETION_MESSAGE).coerceIn(0, 2)
        set(value) { prefs.edit().putInt("completion_mode", value.coerceIn(0, 2)).apply() }

    var soundEnabled: Boolean
        get() = prefs.getBoolean("sound_enabled", false)
        set(value) { prefs.edit().putBoolean("sound_enabled", value).apply() }

    var fullScreen: Boolean
        get() = prefs.getBoolean("full_screen", false)
        set(value) { prefs.edit().putBoolean("full_screen", value).apply() }

    var unitsMask: Int
        get() = prefs.getInt("units_mask", 15).coerceIn(0, 15)
        set(value) { prefs.edit().putInt("units_mask", value.coerceIn(0, 15)).apply() }

    fun isUnitVisible(index: Int): Boolean = (unitsMask and (1 shl index)) != 0

    fun targetMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        return runCatching {
            val date = LocalDate.parse(targetDate)
            val zone = ZoneId.systemDefault()
            val target = if (ignoreTime) {
                date.atStartOfDay(zone)
            } else {
                date.atTime(LocalTime.of(targetHour, targetMinute)).atZone(zone)
            }
            target.toInstant().toEpochMilli()
        }.getOrElse { nowMillis }
    }

    fun targetKey(): String = "$targetDate|$ignoreTime|$targetHour|$targetMinute"

    fun reset() {
        prefs.edit().clear().apply()
    }
}
