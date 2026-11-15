package com.neonevent.countdown

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Text contrast helpers for user-selected photos.
 * Text color is selected from the actual pixels behind each text region.
 * The selected bitmap is never dimmed, overlaid, or modified.
 */
object PersonalTextContrast {
    private const val SAMPLE_COLUMNS = 13
    private const val SAMPLE_ROWS = 5

    val AUTO_DARK_TEXT = 0xFF182033.toInt()
    val AUTO_LIGHT_TEXT = Color.WHITE
    val AUTO_GOLD_TEXT = 0xFFFFE8B2.toInt()

    /** Perceptual brightness requested for personal photo text: (R*299+G*587+B*114)/1000. */
    fun weightedBrightness(color: Int): Int {
        val red = (color ushr 16) and 0xFF
        val green = (color ushr 8) and 0xFF
        val blue = color and 0xFF
        return (red * 299 + green * 587 + blue * 114) / 1000
    }

    fun textColorForAverageBrightness(averageBrightness: Double): Int =
        if (averageBrightness > 128.0) AUTO_DARK_TEXT else AUTO_LIGHT_TEXT

    /**
     * Samples each text region on the displayed image using the same FIT_CENTER mapping
     * as BackgroundEdgeFiller. Pixels in edge-extended margins map to the nearest image edge.
     */
    fun recommendedTextColorForRegion(
        bitmap: Bitmap?,
        viewWidth: Float,
        viewHeight: Float,
        regionBounds: RectF
    ): Int {
        if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0 ||
            viewWidth <= 0f || viewHeight <= 0f
        ) return AUTO_LIGHT_TEXT

        val region = RectF(regionBounds)
        if (!region.intersect(0f, 0f, viewWidth, viewHeight)) return AUTO_LIGHT_TEXT

        val scale = min(viewWidth / bitmap.width.toFloat(), viewHeight / bitmap.height.toFloat())
        if (scale <= 0f) return AUTO_LIGHT_TEXT
        val imageLeft = (viewWidth - bitmap.width * scale) / 2f
        val imageTop = (viewHeight - bitmap.height * scale) / 2f

        var totalBrightness = 0L
        var samples = 0
        for (row in 0 until SAMPLE_ROWS) {
            val y = region.top + (row + 0.5f) * region.height() / SAMPLE_ROWS
            val sourceY = ((y - imageTop) / scale).toInt().coerceIn(0, bitmap.height - 1)
            for (column in 0 until SAMPLE_COLUMNS) {
                val x = region.left + (column + 0.5f) * region.width() / SAMPLE_COLUMNS
                val sourceX = ((x - imageLeft) / scale).toInt().coerceIn(0, bitmap.width - 1)
                totalBrightness += weightedBrightness(bitmap.getPixel(sourceX, sourceY))
                samples++
            }
        }
        return if (samples == 0) AUTO_LIGHT_TEXT
        else textColorForAverageBrightness(totalBrightness.toDouble() / samples)
    }

    /** Linear-light luminance is retained for the existing automatic cube style. */
    fun relativeLuminance(color: Int): Float {
        fun linear(channel: Int): Float {
            val value = channel / 255f
            return if (value <= 0.04045f) value / 12.92f
            else ((value + 0.055f) / 1.055f).pow(2.4f)
        }
        val red = (color ushr 16) and 0xFF
        val green = (color ushr 8) and 0xFF
        val blue = color and 0xFF
        return 0.2126f * linear(red) +
            0.7152f * linear(green) +
            0.0722f * linear(blue)
    }

    fun recommendedTextColorForLuminanceSamples(samples: FloatArray): Int {
        if (samples.isEmpty()) return AUTO_LIGHT_TEXT
        val sorted = samples.copyOf().apply { sort() }
        val average = samples.average().toFloat()
        val p10 = sorted[((sorted.size - 1) * 0.10f).roundToInt().coerceIn(0, sorted.lastIndex)]
        val p90 = sorted[((sorted.size - 1) * 0.90f).roundToInt().coerceIn(0, sorted.lastIndex)]
        return when {
            average >= 0.62f && p10 >= 0.46f -> AUTO_DARK_TEXT
            average <= 0.16f && p90 <= 0.28f -> AUTO_GOLD_TEXT
            else -> AUTO_LIGHT_TEXT
        }
    }

    fun recommendedTextColor(bitmap: Bitmap?): Int {
        if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return AUTO_LIGHT_TEXT
        }
        val values = FloatArray(SAMPLE_COLUMNS * SAMPLE_ROWS)
        var index = 0
        for (row in 0 until SAMPLE_ROWS) {
            val y = ((row + 0.5f) * bitmap.height / SAMPLE_ROWS).toInt().coerceIn(0, bitmap.height - 1)
            for (column in 0 until SAMPLE_COLUMNS) {
                val x = ((column + 0.5f) * bitmap.width / SAMPLE_COLUMNS).toInt().coerceIn(0, bitmap.width - 1)
                values[index++] = relativeLuminance(bitmap.getPixel(x, y))
            }
        }
        return recommendedTextColorForLuminanceSamples(values)
    }
}
