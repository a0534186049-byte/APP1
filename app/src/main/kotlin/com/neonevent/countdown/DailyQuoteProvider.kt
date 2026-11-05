package com.neonevent.countdown

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Loads the bundled, offline quote list once. The regular quote is stable for
 * the entire local calendar date and is recalculated when that date changes.
 */
object DailyQuoteProvider {
    internal data class QuoteData(
        val normal: List<String>,
        val special: Map<Int, String>
    )

    @Volatile private var cache: QuoteData? = null
    @Volatile private var loadAttempted = false

    private const val TAG = "DailyQuoteProvider"
    private const val ASSET_NAME = "daily_quotes_200_he.json"

    fun quote(context: Context, nowMillis: Long, eventDate: LocalDate?): String {
        val data = load(context) ?: return ""
        val today = Instant.ofEpochMilli(nowMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return selectQuote(today, eventDate, data.normal, data.special)
    }

    /**
     * Pure selection logic shared by unit tests and the app.
     * Special event-day quotes take precedence, including the event day (0).
     * Otherwise the index is based only on today's local epoch day.
     */
    internal fun selectQuote(
        today: LocalDate,
        eventDate: LocalDate?,
        normalQuotes: List<String>,
        specialQuotes: Map<Int, String>
    ): String {
        if (normalQuotes.isEmpty()) return ""

        if (eventDate != null) {
            val remaining = ChronoUnit.DAYS.between(today, eventDate)
            if (remaining in 0L..Int.MAX_VALUE.toLong()) {
                specialQuotes[remaining.toInt()]?.let { special ->
                    if (special.isNotBlank()) return special
                }
            }
        }

        val index = Math.floorMod(today.toEpochDay(), normalQuotes.size.toLong()).toInt()
        return normalQuotes[index]
    }

    @Synchronized
    private fun load(context: Context): QuoteData? {
        if (loadAttempted) return cache
        loadAttempted = true

        cache = try {
            val raw = context.assets.open(ASSET_NAME)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            parseJson(raw)
        } catch (error: Exception) {
            Log.w(TAG, "Could not read bundled quote asset '$ASSET_NAME'; quote hidden", error)
            null
        }
        return cache
    }

    /**
     * Parsing is isolated so malformed/missing data can be tested without
     * changing the selection logic or inventing any fallback sentence.
     */
    internal fun parseJson(raw: String): QuoteData? {
        return try {
            val root = JSONObject(raw)
            val quotesJson = root.optJSONArray("daily_quotes")
            if (quotesJson == null) {
                Log.w(TAG, "daily_quotes is missing or is not an array; quote hidden")
                return null
            }

            val normal = buildList {
                for (index in 0 until quotesJson.length()) {
                    val value = quotesJson.opt(index) as? String ?: continue
                    if (value.isNotBlank()) add(value)
                }
            }
            if (normal.isEmpty()) {
                Log.w(TAG, "daily_quotes is empty; quote hidden")
                return null
            }

            // Parse optional special days independently: malformed special data must
            // never discard an otherwise valid list of normal quotes.
            val special = runCatching {
                val specialJson = root.opt("special_days_remaining") as? JSONObject
                    ?: return@runCatching emptyMap()
                buildMap {
                    val keys = specialJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val day = key.toIntOrNull() ?: continue
                        if (day < 0) continue
                        val value = specialJson.opt(key) as? String ?: continue
                        if (value.isNotBlank()) put(day, value)
                    }
                }
            }.onFailure { error ->
                Log.w(TAG, "special_days_remaining is invalid; using normal quotes only", error)
            }.getOrElse { emptyMap() }

            if (root.opt("special_days_remaining") !is JSONObject) {
                Log.w(TAG, "special_days_remaining is missing or invalid; using normal quotes only")
            }

            QuoteData(normal, special)
        } catch (error: Exception) {
            Log.w(TAG, "Bundled quote JSON is invalid; quote hidden", error)
            null
        }
    }
}
