package com.neonevent.countdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import java.util.WeakHashMap
import kotlin.math.min

/**
 * Draws a complete, undistorted image using FIT_CENTER geometry and extends
 * the colors of its nearest edges into any unused display area.
 */
object BackgroundEdgeFiller {
    private const val PROFILE_SEGMENTS = 256
    private const val EDGE_SAMPLE_DEPTH = 5

    private data class EdgeProfiles(
        val left: IntArray,
        val right: IntArray,
        val top: IntArray,
        val bottom: IntArray
    )

    private val profileCache = WeakHashMap<Bitmap, EdgeProfiles>()

    @Synchronized
    private fun profilesFor(bitmap: Bitmap): EdgeProfiles =
        profileCache.getOrPut(bitmap) { createProfiles(bitmap) }

    private fun createProfiles(bitmap: Bitmap): EdgeProfiles {
        val left = IntArray(PROFILE_SEGMENTS)
        val right = IntArray(PROFILE_SEGMENTS)
        val top = IntArray(PROFILE_SEGMENTS)
        val bottom = IntArray(PROFILE_SEGMENTS)

        for (i in 0 until PROFILE_SEGMENTS) {
            val y = ((i + 0.5f) * bitmap.height / PROFILE_SEGMENTS).toInt()
                .coerceIn(0, bitmap.height - 1)
            left[i] = averageColumn(bitmap, 0, EDGE_SAMPLE_DEPTH, y)
            right[i] = averageColumn(bitmap, bitmap.width - EDGE_SAMPLE_DEPTH, EDGE_SAMPLE_DEPTH, y)

            val x = ((i + 0.5f) * bitmap.width / PROFILE_SEGMENTS).toInt()
                .coerceIn(0, bitmap.width - 1)
            top[i] = averageRow(bitmap, 0, EDGE_SAMPLE_DEPTH, x)
            bottom[i] = averageRow(bitmap, bitmap.height - EDGE_SAMPLE_DEPTH, EDGE_SAMPLE_DEPTH, x)
        }
        return EdgeProfiles(left, right, top, bottom)
    }

    private fun averageColumn(bitmap: Bitmap, startX: Int, count: Int, y: Int): Int {
        var red = 0L
        var green = 0L
        var blue = 0L
        val end = (startX + count).coerceAtMost(bitmap.width)
        for (x in startX.coerceAtLeast(0) until end) {
            val color = bitmap.getPixel(x, y)
            red += Color.red(color)
            green += Color.green(color)
            blue += Color.blue(color)
        }
        val samples = (end - startX.coerceAtLeast(0)).coerceAtLeast(1)
        return Color.rgb((red / samples).toInt(), (green / samples).toInt(), (blue / samples).toInt())
    }

    private fun averageRow(bitmap: Bitmap, startY: Int, count: Int, x: Int): Int {
        var red = 0L
        var green = 0L
        var blue = 0L
        val end = (startY + count).coerceAtMost(bitmap.height)
        for (y in startY.coerceAtLeast(0) until end) {
            val color = bitmap.getPixel(x, y)
            red += Color.red(color)
            green += Color.green(color)
            blue += Color.blue(color)
        }
        val samples = (end - startY.coerceAtLeast(0)).coerceAtLeast(1)
        return Color.rgb((red / samples).toInt(), (green / samples).toInt(), (blue / samples).toInt())
    }

    fun draw(canvas: Canvas, bitmap: Bitmap, width: Float, height: Float, paint: Paint) {
        if (width <= 0f || height <= 0f || bitmap.width <= 0 || bitmap.height <= 0) return

        val scale = min(width / bitmap.width.toFloat(), height / bitmap.height.toFloat())
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f
        val right = left + drawWidth
        val bottom = top + drawHeight
        val destination = RectF(left, top, right, bottom)
        val profiles = profilesFor(bitmap)

        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.isFilterBitmap = true

        val hasSideBands = left > 0.5f
        val hasTopBands = top > 0.5f
        if (hasSideBands) {
            drawVerticalBands(canvas, 0f, left, top, bottom, profiles.left, paint)
            drawVerticalBands(canvas, right, width, top, bottom, profiles.right, paint)
        } else if (hasTopBands) {
            drawHorizontalBands(canvas, left, right, 0f, top, profiles.top, paint)
            drawHorizontalBands(canvas, left, right, bottom, height, profiles.bottom, paint)
        }

        paint.color = Color.WHITE
        canvas.drawBitmap(bitmap, null, destination, paint)
        if (hasSideBands) {
            drawVerticalFeather(canvas, destination, profiles.left[PROFILE_SEGMENTS / 2],
                profiles.right[PROFILE_SEGMENTS / 2], paint)
        } else if (hasTopBands) {
            drawHorizontalFeather(canvas, destination, profiles.top[PROFILE_SEGMENTS / 2],
                profiles.bottom[PROFILE_SEGMENTS / 2], paint)
        }
        paint.shader = null
        paint.alpha = 255
        paint.isFilterBitmap = true
    }

    private fun drawVerticalBands(
        canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float,
        colors: IntArray, paint: Paint
    ) {
        if (right <= left || bottom <= top) return
        val segmentHeight = (bottom - top) / colors.size
        colors.forEachIndexed { index, color ->
            paint.color = color
            val y0 = top + index * segmentHeight
            val y1 = if (index == colors.lastIndex) bottom else y0 + segmentHeight + 0.5f
            canvas.drawRect(left, y0, right, y1, paint)
        }
    }

    private fun drawHorizontalBands(
        canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float,
        colors: IntArray, paint: Paint
    ) {
        if (right <= left || bottom <= top) return
        val segmentWidth = (right - left) / colors.size
        colors.forEachIndexed { index, color ->
            paint.color = color
            val x0 = left + index * segmentWidth
            val x1 = if (index == colors.lastIndex) right else x0 + segmentWidth + 0.5f
            canvas.drawRect(x0, top, x1, bottom, paint)
        }
    }

    private fun drawVerticalFeather(
        canvas: Canvas, destination: RectF, leftColor: Int, rightColor: Int, paint: Paint
    ) {
        val feather = 3f
        paint.shader = LinearGradient(
            destination.left - feather, 0f, destination.left + feather, 0f,
            intArrayOf(withAlpha(leftColor, 34), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(destination.left - feather, destination.top, destination.left + feather,
            destination.bottom, paint)
        paint.shader = LinearGradient(
            destination.right - feather, 0f, destination.right + feather, 0f,
            intArrayOf(Color.TRANSPARENT, withAlpha(rightColor, 34)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(destination.right - feather, destination.top, destination.right + feather,
            destination.bottom, paint)
        paint.shader = null
    }

    private fun drawHorizontalFeather(
        canvas: Canvas, destination: RectF, topColor: Int, bottomColor: Int, paint: Paint
    ) {
        val feather = 3f
        paint.shader = LinearGradient(
            0f, destination.top - feather, 0f, destination.top + feather,
            intArrayOf(withAlpha(topColor, 34), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(destination.left, destination.top - feather, destination.right,
            destination.top + feather, paint)
        paint.shader = LinearGradient(
            0f, destination.bottom - feather, 0f, destination.bottom + feather,
            intArrayOf(Color.TRANSPARENT, withAlpha(bottomColor, 34)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(destination.left, destination.bottom - feather, destination.right,
            destination.bottom + feather, paint)
        paint.shader = null
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
