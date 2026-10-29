package com.neonevent.countdown

import android.content.Context
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Loads bundled quotes once; no network, account, or background service is used.
 * The normal quote index is derived from a fixed local-date reference so reopening
 * the app on the same local date never changes the quote.
 */
object DailyQuoteProvider {
    private data class QuoteData(
        val normal: List<String>,
        val special: Map<Int, String>
    )

    @Volatile private var cache: QuoteData? = null
    @Volatile private var loadAttempted = false
    private val referenceDate = LocalDate.of(2025, 1, 1)

    fun quote(context: Context, nowMillis: Long, eventDate: LocalDate?): String {
        val data = load(context) ?: return ""
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

        if (eventDate != null) {
            val remaining = ChronoUnit.DAYS.between(today, eventDate).toInt()
            if (remaining >= 0) data.special[remaining]?.let { return it }
        }

        if (data.normal.isEmpty()) return ""
        val elapsed = ChronoUnit.DAYS.between(referenceDate, today)
        val index = Math.floorMod(elapsed, data.normal.size.toLong()).toInt()
        return data.normal[index]
    }

    @Synchronized
    private fun load(context: Context): QuoteData? {
        if (loadAttempted) return cache
        loadAttempted = true
        cache = runCatching {
            val raw = context.assets.open("daily_quotes_200_he.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(raw)
            val quotesJson = root.optJSONArray("daily_quotes")
            val normal = buildList {
                if (quotesJson != null) {
                    for (i in 0 until quotesJson.length()) {
                        val value = quotesJson.optString(i, "").trim()
                        if (value.isNotEmpty()) add(value)
                    }
                }
            }
            val specialJson = root.optJSONObject("special_days_remaining")
            val special = mutableMapOf<Int, String>()
            if (specialJson != null) {
                val keys = specialJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = specialJson.optString(key, "").trim()
                    val day = key.toIntOrNull()
                    if (day != null && day >= 0 && value.isNotEmpty()) special[day] = value
                }
            }
            QuoteData(normal, special)
        }.getOrNull()
        return cache
    }
}
