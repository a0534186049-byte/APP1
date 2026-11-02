package com.neonevent.countdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Computes a local contrast scrim for text drawn directly over a user-selected image.
 * It never modifies the bitmap and never draws an opaque text container.
 */
object PersonalTextContrast {
    private const val TARGET_BACKGROUND_LUMINANCE = 0.18f
    private const val MAX_SCRIM_ALPHA = 0.82f
    private const val SAMPLE_COLUMNS = 13
    private const val SAMPLE_ROWS = 5

    fun scrimAlphaForAverageLuminance(averageLuminance: Float): Int {
        if (!averageLuminance.isFinite() || averageLuminance <= TARGET_BACKGROUND_LUMINANCE) return 0
        val alpha = ((averageLuminance - TARGET_BACKGROUND_LUMINANCE) / averageLuminance)
            .coerceIn(0f, MAX_SCRIM_ALPHA)
        return (alpha * 255f).roundToInt().coerceIn(0, 255)
    }

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

    /**
     * Use the regional average plus a 90th-percentile safety check. This keeps
     * mixed bright/dark photographs from leaving bright text patches unreadable.
     */
    val AUTO_DARK_TEXT = 0xFF182033.toInt()
    val AUTO_LIGHT_TEXT = Color.WHITE
    val AUTO_GOLD_TEXT = 0xFFFFE8B2.toInt()

    fun recommendedTextColorForLuminanceSamples(samples: FloatArray): Int {
        if (samples.isEmpty()) return AUTO_LIGHT_TEXT
        val sorted = samples.copyOf().apply { sort() }
        val average = samples.average().toFloat()
        val p90 = sorted[((sorted.size - 1) * 0.90f).roundToInt().coerceIn(0, sorted.lastIndex)]
        return when {
            average >= 0.58f && p90 >= 0.68f -> AUTO_DARK_TEXT
            average <= 0.16f && p90 <= 0.28f -> AUTO_GOLD_TEXT
            else -> AUTO_LIGHT_TEXT
        }
    }

    fun recommendedTextColor(bitmap: Bitmap?): Int {
        if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return AUTO_LIGHT_TEXT
        }
        val samples = FloatArray(SAMPLE_COLUMNS * SAMPLE_ROWS)
        var index = 0
        for (row in 0 until SAMPLE_ROWS) {
            val y = ((row + 0.5f) * bitmap.height / SAMPLE_ROWS).toInt().coerceIn(0, bitmap.height - 1)
            for (column in 0 until SAMPLE_COLUMNS) {
                val x = ((column + 0.5f) * bitmap.width / SAMPLE_COLUMNS).toInt().coerceIn(0, bitmap.width - 1)
                samples[index++] = relativeLuminance(bitmap.getPixel(x, y))
            }
        }
        return recommendedTextColorForLuminanceSamples(samples)
    }

    fun scrimAlphaForLuminanceSamples(samples: FloatArray): Int {
        if (samples.isEmpty()) return 0
        val average = samples.average().toFloat()
        val sorted = samples.copyOf().apply { sort() }
        val percentileIndex = ((sorted.size - 1) * 0.90f).roundToInt().coerceIn(0, sorted.lastIndex)
        val conservativeLuminance = maxOf(average, sorted[percentileIndex])
        return scrimAlphaForAverageLuminance(conservativeLuminance)
    }

    fun drawForRegion(
        canvas: Canvas,
        bitmap: Bitmap?,
        viewWidth: Float,
        viewHeight: Float,
        regionBounds: RectF,
        paint: Paint
    ) {
        if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0 ||
            viewWidth <= 0f || viewHeight <= 0f
        ) return

        val region = RectF(regionBounds)
        if (!region.intersect(0f, 0f, viewWidth, viewHeight)) return

        val scale = min(viewWidth / bitmap.width.toFloat(), viewHeight / bitmap.height.toFloat())
        val imageWidth = bitmap.width * scale
        val imageHeight = bitmap.height * scale
        val imageLeft = (viewWidth - imageWidth) / 2f
        val imageTop = (viewHeight - imageHeight) / 2f

        val luminanceSamples = FloatArray(SAMPLE_COLUMNS * SAMPLE_ROWS)
        var sampleIndex = 0
        for (row in 0 until SAMPLE_ROWS) {
            val y = region.top + (row + 0.5f) * region.height() / SAMPLE_ROWS
            val sourceY = ((y - imageTop) / scale).toInt().coerceIn(0, bitmap.height - 1)
            for (column in 0 until SAMPLE_COLUMNS) {
                val x = region.left + (column + 0.5f) * region.width() / SAMPLE_COLUMNS
                val sourceX = ((x - imageLeft) / scale).toInt().coerceIn(0, bitmap.width - 1)
                luminanceSamples[sampleIndex++] = relativeLuminance(bitmap.getPixel(sourceX, sourceY))
            }
        }

        val alpha = scrimAlphaForLuminanceSamples(luminanceSamples)
        if (alpha == 0) return

        val dark = Color.argb(alpha, 0, 0, 0)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.alpha = 255
        paint.shader = LinearGradient(
            0f, region.top, 0f, region.bottom,
            intArrayOf(Color.TRANSPARENT, dark, dark, Color.TRANSPARENT),
            floatArrayOf(0f, 0.16f, 0.84f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(region, paint)
        paint.shader = null
        paint.alpha = 255
        paint.color = Color.WHITE
    }
}
