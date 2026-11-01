package com.neonevent.countdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import java.util.Locale
import kotlin.math.floor
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
        if (parts.complete) lastCompletionKey = key else lastCompletionKey = ""
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
            paint.alpha = 255
            paint.isFilterBitmap = true
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
        paint.color = if (theme == CountdownPrefs.THEME_GRADIENT) 0x15050A18 else 0x22040A14
        canvas.drawRect(0f, 0f, w, h, paint)

        val centerX = w / 2f
        if (prefs.showTitle) {
            paint.color = if (isPersonal) textColor else accent
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = prefs.homeHeadingTypeface()
            val title = prefs.eventTitle.ifBlank { "הרגע הגדול מתקרב" }
            val titleText = fitCenteredText(paint, title, prefs.sp(34f), prefs.sp(18f), w * 0.86f)
            if (theme == CountdownPrefs.THEME_GOLD || theme == CountdownPrefs.THEME_PREMIUM || isPersonal) {
                paint.setShadowLayer(w * 0.008f, 0f, 2f, 0xA0000000.toInt())
            }
            canvas.drawText(titleText, centerX, h * 0.205f, paint)
            paint.clearShadowLayer()
        }

        val countdown = CountdownMath.calculateDateOnly(prefs.targetMillis(nowMillis), nowMillis)
        val values = listOf(countdown.days, countdown.hours, countdown.minutes, countdown.seconds)
        val labels = listOf("ימים", "שעות", "דקות", "שניות")
        val selected = (0..3).filter { prefs.isUnitVisible(it) }
        val side = w * 0.045f
        val gap = w * 0.025f
        val dayHeight = min(h * 0.115f, w * 0.31f).coerceAtLeast(64f)
        val otherHeight = min(h * 0.105f, w * 0.235f).coerceAtLeast(58f)
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
            paint.typeface = Typeface.create(prefs.homeBodyTypeface(), Typeface.BOLD)
            val digits = if (index == 0) valueAsHebrewDigits(values[index]) else String.format(Locale.ROOT, "%02d", values[index])
            val numberText = fitCenteredText(paint, digits,
                min(prefs.sp(if (index == 0) 44f else 39f), boxHeight * 0.44f),
                prefs.sp(20f), boxWidth * 0.88f)
            paint.color = numberColor
            paint.setShadowLayer(if (!isPersonal && (theme == CountdownPrefs.THEME_GOLD || theme == CountdownPrefs.THEME_PREMIUM)) 2f else 0f,
                0f, 1f, 0x66000000)
            canvas.drawText(numberText, x + boxWidth / 2f, top + boxHeight * 0.55f, paint)
            paint.clearShadowLayer()

            paint.typeface = prefs.homeBodyTypeface()
            paint.color = labelColor
            val labelText = fitCenteredText(paint, labels[index],
                min(prefs.sp(17f), boxHeight * 0.24f), prefs.sp(13f), boxWidth * 0.90f)
            canvas.drawText(labelText, x + boxWidth / 2f, top + boxHeight * 0.82f, paint)
        }

        val dayTop = h * 0.275f
        if (selected.contains(0)) {
            val dayWidth = min(w * 0.42f, dayHeight * 1.42f)
            drawBox(0, centerX - dayWidth / 2f, dayTop, dayWidth, dayHeight)
        }
        val lower = selected.filter { it in 1..3 }.asReversed()
        val lowerTop = if (selected.contains(0)) dayTop + dayHeight + h * 0.025f else h * 0.34f
        if (lower.isNotEmpty()) {
            val lowerWidth = (w - side * 2f - gap * (lower.size - 1)).coerceAtLeast(80f) / lower.size
            val totalWidth = lowerWidth * lower.size + gap * (lower.size - 1)
            val left = (centerX - totalWidth / 2f).coerceAtLeast(0f)
            lower.forEachIndexed { i, index -> drawBox(index, left + i * (lowerWidth + gap), lowerTop, lowerWidth, otherHeight) }
        }
        if (countdown.complete) {
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(prefs.homeBodyTypeface(), Typeface.BOLD)
            val completeText = fitCenteredText(paint, "האירוע כבר הגיע", prefs.sp(25f), prefs.sp(17f), w * 0.88f)
            paint.color = accent
            paint.setShadowLayer(3f, 0f, 2f, 0xA0000000.toInt())
            canvas.drawText(completeText, centerX, h * 0.25f, paint)
            paint.clearShadowLayer()
        }

        val maxLowerBottom = if (lower.isNotEmpty()) lowerTop + otherHeight else dayTop + dayHeight
        val targetY = maxOf(maxLowerBottom + h * 0.045f, h * 0.60f).coerceAtMost(h * 0.72f)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = prefs.homeBodyTypeface()
        paint.color = accent
        val dateText = fitCenteredText(paint, prefs.formattedTargetDate(), prefs.sp(20f), prefs.sp(15f), w * 0.90f)
        paint.setShadowLayer(2f, 0f, 1f, 0x99000000.toInt())
        canvas.drawText(dateText, centerX, targetY, paint)
        paint.clearShadowLayer()

        val quote = prefs.dailyQuote(nowMillis).ifBlank {
            if (countdown.complete) "הספירה הסתיימה" else "כל שנייה מקרבת את הרגע"
        }
        paint.typeface = prefs.homeBodyTypeface()
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.setShadowLayer(3f, 0f, 1f, 0xC0000000.toInt())
        val quoteY = targetY + prefs.dp(28f)
        val availableQuoteHeight = (h - prefs.dp(16f) - quoteY).coerceAtLeast(prefs.dp(18f))
        drawWrappedCenteredText(canvas, quote, centerX, quoteY, w * 0.90f, paint,
            prefs.sp(18f), prefs.sp(14f), availableQuoteHeight)
        paint.clearShadowLayer()
    }

    private fun fitCenteredText(
        textPaint: Paint, text: String, preferredSize: Float, minimumSize: Float, maxWidth: Float
    ): String {
        textPaint.textSize = preferredSize
        val step = maxOf(0.5f, preferredSize * 0.025f)
        while (textPaint.measureText(text) > maxWidth && textPaint.textSize > minimumSize) {
            textPaint.textSize = (textPaint.textSize - step).coerceAtLeast(minimumSize)
        }
        if (textPaint.measureText(text) <= maxWidth) return text
        var fitted = text
        while (fitted.isNotEmpty() && textPaint.measureText("$fitted…") > maxWidth) fitted = fitted.dropLast(1)
        return if (fitted.isEmpty()) "" else "$fitted…"
    }

    private fun valueAsHebrewDigits(value: Long): String {
        var remaining = value.coerceAtLeast(0L).coerceAtMost(999L).toInt()
        if (remaining == 0) return "0"
        val out = StringBuilder()
        while (remaining >= 400) { out.append('ת'); remaining -= 400 }
        when {
            remaining >= 300 -> { out.append('ש'); remaining -= 300 }
            remaining >= 200 -> { out.append('ר'); remaining -= 200 }
            remaining >= 100 -> { out.append('ק'); remaining -= 100 }
        }
        if (remaining == 15) { out.append("טו"); remaining = 0 }
        else if (remaining == 16) { out.append("טז"); remaining = 0 }
        else {
            val tens = when {
                remaining >= 90 -> 90; remaining >= 80 -> 80; remaining >= 70 -> 70
                remaining >= 60 -> 60; remaining >= 50 -> 50; remaining >= 40 -> 40
                remaining >= 30 -> 30; remaining >= 20 -> 20; remaining >= 10 -> 10
                else -> 0
            }
            if (tens > 0) {
                out.append(when (tens) {
                    90 -> 'צ'; 80 -> 'פ'; 70 -> 'ע'; 60 -> 'ס'; 50 -> 'נ'
                    40 -> 'מ'; 30 -> 'ל'; 20 -> 'כ'; else -> 'י'
                })
                remaining -= tens
            }
            if (remaining > 0) out.append(when (remaining) {
                9 -> 'ט'; 8 -> 'ח'; 7 -> 'ז'; 6 -> 'ו'; 5 -> 'ה'
                4 -> 'ד'; 3 -> 'ג'; 2 -> 'ב'; else -> 'א'
            })
        }
        if (out.length == 1) out.append('׳') else out.insert(out.length - 1, '״')
        return out.toString()
    }

    private fun wrapToWidth(text: String, paint: Paint, maxWidth: Float): MutableList<String> {
        val lines = mutableListOf<String>()
        var current = ""
        for (word in text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                lines += current
                current = word
            } else current = candidate
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun drawWrappedCenteredText(
        canvas: Canvas, text: String, centerX: Float, firstBaseline: Float, maxWidth: Float,
        textPaint: Paint, preferredSize: Float, minimumSize: Float, maxHeight: Float
    ) {
        if (text.isBlank() || maxWidth <= 0f || maxHeight <= 0f) return
        var size = preferredSize
        var lines = mutableListOf<String>()
        var lineHeight = size * 1.22f
        var maxLines = 1
        while (true) {
            textPaint.textSize = size
            lines = wrapToWidth(text, textPaint, maxWidth)
            val metrics = textPaint.fontMetrics
            lineHeight = maxOf(size * 1.22f, metrics.descent - metrics.ascent)
            maxLines = floor(maxHeight / lineHeight).toInt().coerceIn(1, 4)
            if (lines.size <= maxLines || size <= minimumSize) break
            size = (size - maxOf(0.75f, size * 0.04f)).coerceAtLeast(minimumSize)
        }
        if (lines.size > maxLines) {
            lines = lines.take(maxLines).toMutableList()
            var last = lines.last()
            while (last.isNotEmpty() && textPaint.measureText("$last…") > maxWidth) last = last.dropLast(1)
            lines[lines.lastIndex] = if (last.isEmpty()) "…" else "$last…"
        }
        lines.forEachIndexed { index, line -> canvas.drawText(line, centerX, firstBaseline + index * lineHeight, textPaint) }
    }
}
