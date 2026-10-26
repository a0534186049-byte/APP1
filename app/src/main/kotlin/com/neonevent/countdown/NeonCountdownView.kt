package com.neonevent.countdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import java.util.Locale
import kotlin.math.min
import kotlin.math.sin

class NeonCountdownView(context: Context) : View(context) {
    val settings = CountdownPrefs(context)
    private var active = true
    private var lastCompletionKey = ""
    var onCompleted: (() -> Unit)? = null

    init {
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        contentDescription = "תצוגת ספירה לאחור לאירוע"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        val parts = CountdownMath.calculate(settings.targetMillis(now), now)
        val key = settings.targetKey()
        if (parts.complete && lastCompletionKey != key) {
            lastCompletionKey = key
            onCompleted?.invoke()
        } else if (!parts.complete) {
            lastCompletionKey = ""
        }
        NeonCountdownRenderer.draw(canvas, width.toFloat(), height.toFloat(), settings, now)
        if (active) postInvalidateDelayed(if (settings.animationSpeed == 0) 1000L else 34L)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        active = isVisible
        if (active) invalidate()
        // No new frames are posted while the view is hidden.
    }
}

object NeonCountdownRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rect = RectF()

    fun draw(canvas: Canvas, width: Float, height: Float, prefs: CountdownPrefs, nowMillis: Long) {
        if (width <= 0f || height <= 0f) return
        val w = width
        val h = height
        val palette = CountdownPrefs.themeColors[prefs.theme]
        val accent = CountdownPrefs.accentColors[prefs.accent]
        val secondary = palette[1]
        val tertiary = palette[2]
        val phase = if (prefs.animationSpeed == 0) 0.0 else {
            val multiplier = when (prefs.animationSpeed) { 1 -> 0.35; 2 -> 1.0; else -> 1.7 }
            (nowMillis / 1000.0 * multiplier) % (Math.PI * 2)
        }

        paint.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(Color.rgb(5, 8, 24), Color.rgb(8, 12, 35), Color.rgb(15, 9, 31)),
            null, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        for (i in 0 until 32) {
            val x = ((i * 127 + 19) % 997) / 997f * w
            val y = ((i * 211 + 71) % 991) / 991f * h
            val twinkle = if (prefs.animationSpeed == 0) 0.55 else 0.32 + 0.5 * (0.5 + 0.5 * sin(phase + i))
            paint.color = withAlpha(if (i % 3 == 0) accent else Color.WHITE, (twinkle * 125).toInt())
            canvas.drawCircle(x, y, if (i % 5 == 0) w * 0.0032f else w * 0.0018f, paint)
        }

        val centerX = w / 2f
        val centerY = h * 0.56f
        val radius = min(w * 0.47f, h * 0.43f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1f, w * 0.0025f)
        paint.color = withAlpha(secondary, 34)
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.strokeWidth = maxOf(1f, w * 0.0018f)
        paint.color = withAlpha(tertiary, 42)
        canvas.drawCircle(centerX, centerY, radius * 0.83f, paint)
        if (prefs.animationSpeed != 0 && prefs.glow > 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = maxOf(1f, w * 0.004f)
            paint.color = withAlpha(accent, (prefs.glow * 0.48).toInt())
            val sweep = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
            canvas.drawArc(sweep, (phase * 40).toFloat(), 55f, false, paint)
            canvas.drawArc(sweep, (160 + phase * 27).toFloat(), 28f, false, paint)
        }
        paint.style = Paint.Style.FILL

        val titleY = h * 0.19f
        if (prefs.showTitle) {
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            paint.textSize = min(w * 0.065f, h * 0.075f).coerceAtLeast(13f)
            val title = prefs.eventTitle.ifBlank { "הרגע הגדול מתקרב" }
            while (paint.measureText(title) > w * 0.88f && paint.textSize > 12f) paint.textSize -= 1f
            paint.setShadowLayer(prefs.glow / 12f, 0f, 0f, withAlpha(accent, prefs.glow * 2))
            canvas.drawText(title, centerX, titleY, paint)
            paint.clearShadowLayer()
        }

        val countdown = CountdownMath.calculate(prefs.targetMillis(nowMillis), nowMillis)
        if (countdown.complete && prefs.completionMode == CountdownPrefs.COMPLETION_MESSAGE) {
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textSize = min(w * 0.09f, h * 0.12f).coerceAtLeast(18f)
            paint.color = accent
            paint.setShadowLayer(prefs.glow / 7f, 0f, 0f, withAlpha(accent, prefs.glow * 2))
            canvas.drawText("האירוע הגיע!", centerX, h * 0.56f, paint)
            paint.clearShadowLayer()
            paint.textSize = min(w * 0.038f, 18f).coerceAtLeast(10f)
            paint.color = withAlpha(Color.WHITE, 190)
            canvas.drawText("זה הרגע שחיכיתם לו", centerX, h * 0.56f + h * 0.09f, paint)
            return
        }
        if (countdown.complete && prefs.completionMode == CountdownPrefs.COMPLETION_GLOW) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = w * 0.006f
            paint.color = withAlpha(accent, (prefs.glow * 2).coerceAtMost(220))
            canvas.drawCircle(centerX, centerY, radius * (0.55f + 0.08f * sin(phase).toFloat()), paint)
            paint.style = Paint.Style.FILL
        }

        val unitValues = listOf(countdown.days, countdown.hours, countdown.minutes, countdown.seconds)
        val unitNames = listOf("ימים", "שעות", "דקות", "שניות")
        val selected = (0..3).filter { prefs.isUnitVisible(it) }
        if (selected.isEmpty()) {
            paint.color = withAlpha(Color.WHITE, 180)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = min(w * 0.045f, 20f).coerceAtLeast(12f)
            canvas.drawText("בחרו יחידות להצגה בהגדרות", centerX, centerY, paint)
            return
        }

        val sideMargin = w * 0.045f
        val gap = w * 0.018f
        val boxWidth = ((w - sideMargin * 2f - gap * (selected.size - 1)) / selected.size).coerceAtLeast(36f)
        val boxHeight = min(h * 0.31f, w * 0.25f).coerceAtLeast(66f)
        val rowWidth = boxWidth * selected.size + gap * (selected.size - 1)
        val left = centerX - rowWidth / 2f
        val top = h * 0.39f
        val radiusCorner = min(w * 0.025f, 18f)

        selected.forEachIndexed { column, index ->
            val x = left + column * (boxWidth + gap)
            rect.set(x, top, x + boxWidth, top + boxHeight)
            if (prefs.glow > 0) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = maxOf(1f, w * 0.006f)
                paint.color = withAlpha(accent, (prefs.glow * 0.32f).toInt())
                canvas.drawRoundRect(rect, radiusCorner, radiusCorner, paint)
            }
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(x, top, x + boxWidth, top + boxHeight,
                intArrayOf(withAlpha(accent, 29), withAlpha(secondary, 17)), null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, radiusCorner, radiusCorner, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = maxOf(1f, w * 0.0018f)
            paint.color = withAlpha(accent, (125 + prefs.glow).coerceAtMost(220))
            canvas.drawRoundRect(rect, radiusCorner, radiusCorner, paint)
            paint.style = Paint.Style.FILL

            val displayValue = if (countdown.complete) 0L else unitValues[index]
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textSize = min(boxWidth * 0.40f, boxHeight * 0.43f).coerceAtLeast(17f)
            paint.color = Color.WHITE
            if (prefs.glow > 0) paint.setShadowLayer(prefs.glow / 18f, 0f, 0f, withAlpha(accent, prefs.glow * 2))
            val digits = if (index == 0) displayValue.toString() else String.format(Locale.ROOT, "%02d", displayValue)
            canvas.drawText(digits, x + boxWidth / 2f, top + boxHeight * 0.59f, paint)
            paint.clearShadowLayer()
            paint.textSize = min(boxWidth * 0.19f, 18f).coerceAtLeast(10f)
            paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            paint.color = withAlpha(Color.WHITE, 200)
            canvas.drawText(unitNames[index], x + boxWidth / 2f, top + boxHeight * 0.84f, paint)
        }

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        paint.textSize = min(w * 0.034f, 16f).coerceAtLeast(10f)
        paint.color = withAlpha(accent, 220)
        val footer = if (countdown.complete) "הספירה הסתיימה" else if (prefs.ignoreTime) "סופרים עד לתאריך האירוע" else "כל שנייה מקרבת את הרגע"
        canvas.drawText(footer, centerX, min(h * 0.86f, top + boxHeight + h * 0.13f), paint)
        paint.color = withAlpha(Color.WHITE, 110)
        paint.textSize = min(w * 0.027f, 13f).coerceAtLeast(9f)
        canvas.drawText("NEON EVENT COUNTDOWN", centerX, h * 0.95f, paint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
