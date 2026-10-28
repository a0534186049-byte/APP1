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

class NeonCountdownView(context: Context) : View(context) {
    val settings = CountdownPrefs(context)
    private var active = true
    private var lastCompletionKey = ""

    init {
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        contentDescription = "תצוגת ספירה לאחור לחתונה"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        val parts = CountdownMath.calculate(settings.targetMillis(now), now)
        val key = settings.targetKey()
        if (parts.complete) lastCompletionKey = key
        else lastCompletionKey = ""
        NeonCountdownRenderer.draw(canvas, width.toFloat(), height.toFloat(), settings, now)
        if (active) postInvalidateDelayed(1000L)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        active = isVisible
        if (active) invalidate()
    }
}

object NeonCountdownRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val box = RectF()
    private val imageRect = RectF()
    private val locale = Locale("he", "IL")

    @Synchronized
    fun draw(canvas: Canvas, width: Float, height: Float, prefs: CountdownPrefs, nowMillis: Long) {
        if (width <= 0f || height <= 0f) return
        val w = width
        val h = height
        val theme = prefs.theme
        val isPersonal = theme == CountdownPrefs.THEME_PERSONAL
        val accent = when (theme) {
            CountdownPrefs.THEME_GOLD -> Color.rgb(224, 185, 108)
            CountdownPrefs.THEME_GRADIENT -> Color.rgb(247, 241, 255)
            CountdownPrefs.THEME_PREMIUM -> Color.rgb(244, 210, 158)
            else -> when (prefs.cubeStyle) {
                CountdownPrefs.CUBES_LIGHT -> Color.rgb(28, 34, 48)
                else -> Color.rgb(255, 255, 255)
            }
        }
        val textColor = if (isPersonal && prefs.cubeStyle == CountdownPrefs.CUBES_LIGHT) Color.rgb(24, 28, 40) else
            if (isPersonal && prefs.cubeStyle == CountdownPrefs.CUBES_FROSTED) Color.rgb(25, 30, 42) else Color.WHITE

        paint.shader = null
        paint.style = Paint.Style.FILL
        val bitmap = prefs.backgroundBitmap()
        if (bitmap != null && !bitmap.isRecycled) {
            paint.color = Color.WHITE
            paint.alpha = 255
            paint.isFilterBitmap = true
            imageRect.set(0f, 0f, w, h)
            canvas.drawBitmap(bitmap, null, imageRect, paint)
        } else {
            paint.shader = LinearGradient(0f, 0f, w, h,
                intArrayOf(Color.rgb(5, 9, 22), Color.rgb(18, 20, 48), Color.rgb(6, 8, 20)),
                null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        }

        // A very subtle veil improves text contrast without replacing or materially changing the selected background.
        paint.color = if (theme == CountdownPrefs.THEME_GRADIENT) 0x15050A18 else 0x22040A14
        canvas.drawRect(0f, 0f, w, h, paint)

        val centerX = w / 2f
        if (prefs.showTitle) {
            paint.color = if (isPersonal) textColor else accent
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            paint.textSize = min(w * 0.065f, h * 0.036f).coerceAtLeast(22f)
            val title = prefs.eventTitle.ifBlank { "הרגע הגדול מתקרב" }
            while (paint.measureText(title) > w * 0.88f && paint.textSize > 14f) paint.textSize -= 1f
            if (theme == CountdownPrefs.THEME_GOLD || theme == CountdownPrefs.THEME_PREMIUM ||
                (isPersonal && prefs.cubeStyle == CountdownPrefs.CUBES_DARK)) {
                paint.setShadowLayer(w * 0.008f, 0f, 2f, 0xA0000000.toInt())
            }
            canvas.drawText(title, centerX, h * 0.205f, paint)
            paint.clearShadowLayer()
        }

        val countdown = CountdownMath.calculate(prefs.targetMillis(nowMillis), nowMillis)
        if (countdown.complete) {
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textSize = min(w * 0.085f, h * 0.045f).coerceAtLeast(30f)
            paint.color = accent
            canvas.drawText("האירוע הגיע!", centerX, h * 0.42f, paint)
            paint.textSize = min(w * 0.04f, h * 0.022f).coerceAtLeast(18f)
            paint.color = textColor
            canvas.drawText("זה הרגע שחיכיתם לו", centerX, h * 0.47f, paint)
        } else {
            val values = listOf(countdown.days, countdown.hours, countdown.minutes, countdown.seconds)
            val labels = listOf("ימים", "שעות", "דקות", "שניות")
            val selected = (0..3).filter { prefs.isUnitVisible(it) }.asReversed()
            val side = w * 0.035f
            val gap = w * 0.014f
            val boxWidth = ((w - side * 2f - gap * (selected.size - 1).coerceAtLeast(0)) / selected.size.coerceAtLeast(1)).coerceAtLeast(48f)
            val boxHeight = min(h * 0.145f, w * 0.215f).coerceAtLeast(82f)
            val rowWidth = boxWidth * selected.size + gap * (selected.size - 1).coerceAtLeast(0)
            val left = centerX - rowWidth / 2f
            val top = h * 0.315f
            val corner = min(w * 0.026f, 24f)

            selected.forEachIndexed { column, index ->
                val x = left + column * (boxWidth + gap)
                box.set(x, top, x + boxWidth, top + boxHeight)
                val fill: Int
                val stroke: Int
                val numberColor: Int
                val labelColor: Int
                when {
                    isPersonal && prefs.cubeStyle == CountdownPrefs.CUBES_LIGHT -> {
                        fill = 0xE8FFFFFF.toInt(); stroke = 0xD0FFFFFF.toInt()
                        numberColor = Color.rgb(24, 29, 42); labelColor = 0xFF31394B.toInt()
                    }
                    isPersonal && prefs.cubeStyle == CountdownPrefs.CUBES_FROSTED -> {
                        fill = 0xA9F7F8FC.toInt(); stroke = 0xDDFEFFFF.toInt()
                        numberColor = Color.rgb(22, 29, 42); labelColor = 0xFF303849.toInt()
                    }
                    isPersonal -> {
                        fill = 0xD9151D2B.toInt(); stroke = 0xB8FFFFFF.toInt()
                        numberColor = Color.WHITE; labelColor = 0xFFE8EDF7.toInt()
                    }
                    theme == CountdownPrefs.THEME_GOLD -> {
                        fill = 0xD20A111B.toInt(); stroke = 0xE0D9B06B.toInt()
                        numberColor = 0xFFFFE8B2.toInt(); labelColor = 0xFFF1D8A4.toInt()
                    }
                    theme == CountdownPrefs.THEME_GRADIENT -> {
                        fill = 0xB52D2550.toInt(); stroke = 0xB8FFFFFF.toInt()
                        numberColor = Color.WHITE; labelColor = 0xFFF7F1FF.toInt()
                    }
                    else -> {
                        fill = 0xC9101D2B.toInt(); stroke = 0xE2E4C29C.toInt()
                        numberColor = 0xFFFFF3DA.toInt(); labelColor = 0xFFF0D9BC.toInt()
                    }
                }
                paint.style = Paint.Style.FILL
                paint.color = fill
                canvas.drawRoundRect(box, corner, corner, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = maxOf(1.5f, w * 0.0018f)
                paint.color = stroke
                canvas.drawRoundRect(box, corner, corner, paint)
                paint.style = Paint.Style.FILL

                val value = values[index]
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                paint.textSize = min(boxWidth * if (index == 0) 0.36f else 0.39f, boxHeight * 0.42f).coerceAtLeast(24f)
                paint.color = numberColor
                paint.setShadowLayer(if (theme == CountdownPrefs.THEME_GOLD || theme == CountdownPrefs.THEME_PREMIUM) 2f else 0f,
                    0f, 1f, 0x66000000)
                val digits = if (index == 0) value.toString() else String.format(Locale.ROOT, "%02d", value)
                canvas.drawText(digits, x + boxWidth / 2f, top + boxHeight * 0.56f, paint)
                paint.clearShadowLayer()
                paint.textSize = min(boxWidth * 0.19f, 21f).coerceAtLeast(15f)
                paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                paint.color = labelColor
                canvas.drawText(labels[index], x + boxWidth / 2f, top + boxHeight * 0.82f, paint)
            }
        }

        val targetY = if (countdown.complete) h * 0.55f else h * 0.505f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        paint.textSize = min(w * 0.043f, h * 0.023f).coerceAtLeast(19f)
        paint.color = if (isPersonal) textColor else accent
        paint.setShadowLayer(2f, 0f, 1f, 0x77000000)
        canvas.drawText(prefs.formattedTargetDateWithTime(), centerX, targetY, paint)
        paint.clearShadowLayer()

        paint.textSize = min(w * 0.033f, h * 0.018f).coerceAtLeast(15f)
        paint.color = if (isPersonal) textColor else 0xF3FFFFFF.toInt()
        val footer = if (countdown.complete) "הספירה הסתיימה" else "כל שנייה מקרבת את הרגע"
        canvas.drawText(footer, centerX, if (countdown.complete) targetY + h * 0.035f else targetY + h * 0.04f, paint)
    }
}
