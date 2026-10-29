package com.neonevent.countdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
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
        val parts = CountdownMath.calculateDateOnly(settings.targetMillis(now), now)
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
        // Cube colors and surrounding text colors must be controlled independently.
        val textColor = Color.WHITE
        val dayNumberColor = if (isPersonal && prefs.cubeStyle != CountdownPrefs.CUBES_DARK) Color.rgb(24, 29, 42) else Color.WHITE

        paint.shader = null
        paint.style = Paint.Style.FILL
        val bitmap = prefs.backgroundBitmap()
        val backdrop = when (theme) {
            CountdownPrefs.THEME_GRADIENT -> Color.rgb(20, 14, 47)
            CountdownPrefs.THEME_PREMIUM -> Color.rgb(5, 5, 32)
            else -> Color.rgb(4, 10, 18)
        }
        canvas.drawColor(backdrop)
        if (bitmap != null && !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) {
            paint.color = Color.WHITE
            paint.isFilterBitmap = true

            // Fill the whole display with a low-detail color extension of the selected image.
            // Then draw the complete original artwork, sharply and proportionally, on top.
            // This avoids both a narrow strip in the center and the excessive crop caused by cover scaling.
            prefs.backgroundBackdropBitmap()?.let { edgeFill ->
                paint.alpha = 180
                imageRect.set(0f, 0f, w, h)
                canvas.drawBitmap(edgeFill, null, imageRect, paint)
            }
            paint.alpha = 255
            val scale = min(w / bitmap.width.toFloat(), h / bitmap.height.toFloat())
            val drawW = bitmap.width * scale
            val drawH = bitmap.height * scale
            imageRect.set((w - drawW) / 2f, (h - drawH) / 2f,
                (w + drawW) / 2f, (h + drawH) / 2f)
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
            paint.textSize = min(w * 0.072f, h * 0.039f).coerceAtLeast(24f)
            val title = prefs.eventTitle.ifBlank { "הרגע הגדול מתקרב" }
            while (paint.measureText(title) > w * 0.88f && paint.textSize > 14f) paint.textSize -= 1f
            if (theme == CountdownPrefs.THEME_GOLD || theme == CountdownPrefs.THEME_PREMIUM || isPersonal) {
                paint.setShadowLayer(w * 0.008f, 0f, 2f, 0xA0000000.toInt())
            }
            drawCenteredRtlText(canvas, title, centerX, h * 0.205f, w * 0.88f, paint, 1)
            paint.clearShadowLayer()
        }

        val countdown = CountdownMath.calculateDateOnly(prefs.targetMillis(nowMillis), nowMillis)
        val values = listOf(countdown.days, countdown.hours, countdown.minutes, countdown.seconds)
        val labels = listOf("ימים", "שעות", "דקות", "שניות")
        val selected = (0..3).filter { prefs.isUnitVisible(it) }
        val side = w * 0.045f
        val gap = w * 0.025f
        val dayHeight = min(h * 0.09f, w * 0.24f).coerceAtLeast(48f)
        val otherHeight = min(h * 0.075f, w * 0.19f).coerceAtLeast(44f)
        val corner = min(w * 0.035f, 24f)

        fun drawBox(index: Int, x: Float, top: Float, boxWidth: Float, boxHeight: Float) {
            box.set(x, top, x + boxWidth, top + boxHeight)
            val fill: Int
            val stroke: Int
            val numberColor: Int
            val labelColor: Int
            when {
                isPersonal && prefs.cubeStyle == CountdownPrefs.CUBES_LIGHT -> {
                    fill = 0xE8FFFFFF.toInt(); stroke = 0xD0FFFFFF.toInt()
                    numberColor = dayNumberColor; labelColor = 0xFF31394B.toInt()
                }
                isPersonal && prefs.cubeStyle == CountdownPrefs.CUBES_FROSTED -> {
                    fill = 0xA9F7F8FC.toInt(); stroke = 0xDDFEFFFF.toInt()
                    numberColor = dayNumberColor; labelColor = 0xFF303849.toInt()
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

            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textSize = min(boxWidth * if (index == 0) 0.36f else 0.39f, boxHeight * 0.42f).coerceAtLeast(22f)
            paint.color = numberColor
            paint.setShadowLayer(if (!isPersonal && (theme == CountdownPrefs.THEME_GOLD || theme == CountdownPrefs.THEME_PREMIUM)) 2f else 0f,
                0f, 1f, 0x66000000)
            val digits = if (index == 0) values[index].toString() else String.format(Locale.ROOT, "%02d", values[index])
            if (index == 0) canvas.drawText(digits, x + boxWidth / 2f, top + boxHeight * 0.55f, paint)
            else canvas.drawText(digits, x + boxWidth / 2f, top + boxHeight * 0.55f, paint)
            paint.clearShadowLayer()
            paint.textSize = min(boxWidth * 0.19f, 21f).coerceAtLeast(14f)
            paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            paint.color = labelColor
            drawCenteredRtlText(canvas, labels[index], x + boxWidth / 2f, top + boxHeight * 0.82f, boxWidth * 0.94f, paint, 1)
        }

        val dayTop = h * 0.265f
        if (selected.contains(0)) {
            val dayWidth = min(w * 0.34f, dayHeight * 1.34f)
            drawBox(0, centerX - dayWidth / 2f, dayTop, dayWidth, dayHeight)
        }
        val lower = selected.filter { it in 1..3 }.asReversed() // seconds left, hours right in RTL
        val lowerTop = if (selected.contains(0)) dayTop + dayHeight + h * 0.018f else h * 0.32f
        if (lower.isNotEmpty()) {
            val lowerWidth = (w - side * 2f - gap * (lower.size - 1)).coerceAtLeast(80f) / lower.size
            val totalWidth = lowerWidth * lower.size + gap * (lower.size - 1)
            val left = (centerX - totalWidth / 2f).coerceAtLeast(0f)
            lower.forEachIndexed { i, index -> drawBox(index, left + i * (lowerWidth + gap), lowerTop, lowerWidth, otherHeight) }
        }
        if (countdown.complete) {
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textSize = min(w * 0.064f, h * 0.032f).coerceAtLeast(22f)
            paint.color = accent
            paint.setShadowLayer(3f, 0f, 2f, 0xA0000000.toInt())
            drawCenteredRtlText(canvas, "האירוע כבר הגיע", centerX, h * 0.25f, w * 0.9f, paint, 1)
            paint.clearShadowLayer()
        }

        val maxLowerBottom = if (lower.isNotEmpty()) lowerTop + otherHeight else dayTop + dayHeight
        val targetY = maxOf(maxLowerBottom + h * 0.045f, h * 0.60f).coerceAtMost(h * 0.72f)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        paint.textSize = min(w * 0.046f, h * 0.026f).coerceAtLeast(17f)
        paint.color = accent
        paint.setShadowLayer(2f, 0f, 1f, 0x99000000.toInt())
        val eventDateLabel = "תאריך האירוע: ${prefs.formattedTargetDate()}"
        while (paint.measureText(eventDateLabel) > w * 0.92f && paint.textSize > 12f) paint.textSize -= 1f
        drawCenteredRtlText(canvas, eventDateLabel, centerX, targetY, w * 0.94f, paint, 1)
        paint.clearShadowLayer()

        val quote = prefs.dailyQuote(nowMillis).ifBlank {
            if (countdown.complete) "הספירה הסתיימה" else "כל שנייה מקרבת את הרגע"
        }
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = min(w * 0.038f, h * 0.021f).coerceAtLeast(14f)
        paint.setShadowLayer(3f, 0f, 1f, 0xC0000000.toInt())
        val quoteY = targetY + h * 0.055f
        drawWrappedCenteredText(canvas, quote, centerX, quoteY, w * 0.86f, paint, 2, h * 0.027f)
        paint.clearShadowLayer()
    }
    private fun drawWrappedCenteredText(
        canvas: Canvas, text: String, centerX: Float, firstBaseline: Float,
        maxWidth: Float, textPaint: Paint, maxLines: Int, lineHeight: Float
    ) {
        drawCenteredRtlText(canvas, text, centerX, firstBaseline, maxWidth, textPaint, maxLines)
    }

    /**
     * Canvas.drawText does not perform full paragraph bidi layout. StaticLayout does, so Hebrew
     * dates, titles, labels and quotes remain in the correct RTL order on every supported device.
     */
    private fun drawCenteredRtlText(
        canvas: Canvas,
        text: String,
        centerX: Float,
        baseline: Float,
        maxWidth: Float,
        sourcePaint: Paint,
        maxLines: Int,
        lineSpacing: Float = 0f
    ) {
        if (text.isEmpty() || maxWidth <= 1f) return
        val textPaint = TextPaint(sourcePaint)
        val layout = StaticLayout.Builder.obtain(
            text, 0, text.length, textPaint, maxWidth.toInt().coerceAtLeast(1)
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .setIncludePad(false)
            .setMaxLines(maxLines.coerceAtLeast(1))
            .setLineSpacing(lineSpacing.coerceAtLeast(0f), 1f)
            .build()
        canvas.save()
        canvas.translate(centerX - maxWidth / 2f, baseline + textPaint.ascent())
        layout.draw(canvas)
        canvas.restore()
    }

}
