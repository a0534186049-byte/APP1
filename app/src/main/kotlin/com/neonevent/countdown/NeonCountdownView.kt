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
        val parts = CountdownMath.calculateDateOnly(settings.eventLocalDate(), now)
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
        val density = prefs.dp(1f)
        val theme = prefs.theme
        val isPersonal = theme == CountdownPrefs.THEME_PERSONAL
        val accent = when (theme) {
            CountdownPrefs.THEME_GOLD -> Color.rgb(224, 185, 108)
            CountdownPrefs.THEME_GRADIENT -> Color.rgb(247, 241, 255)
            CountdownPrefs.THEME_PREMIUM -> Color.rgb(244, 210, 158)
            else -> Color.WHITE
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
            BackgroundEdgeFiller.draw(canvas, bitmap, w, h, paint)
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
        val countdown = CountdownMath.calculateDateOnly(prefs.eventLocalDate(), nowMillis)
        val selected = (0..3).filter { prefs.isUnitVisible(it) }
        val lower = selected.filter { it in 1..3 } // Physical order: hours left, minutes center, seconds right.
        val geometry = HomeScreenGeometryCalculator.calculate(
            widthPx = w,
            heightPx = h,
            density = density,
            scaledDensity = prefs.sp(1f),
            showDays = selected.contains(0),
            lowerCount = lower.size
        )

        if (prefs.showTitle) {
            if (isPersonal) {
                PersonalTextContrast.drawForRegion(
                    canvas, bitmap, w, h,
                    RectF(w * 0.06f, geometry.titleBaseline - prefs.sp(41f),
                        w * 0.94f, geometry.titleBaseline + prefs.sp(8f)),
                    paint
                )
            }
            paint.color = if (isPersonal) textColor else accent
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = prefs.homeHeadingTypeface()
            val title = prefs.eventTitle.ifBlank { "הרגע הגדול מתקרב" }
            val fittedTitle = fitCenteredText(paint, title, prefs.sp(34f), prefs.sp(18f), w * 0.86f)
            if (theme == CountdownPrefs.THEME_GOLD || theme == CountdownPrefs.THEME_PREMIUM || isPersonal) {
                paint.setShadowLayer(w * 0.008f, 0f, prefs.dp(2f), 0xA0000000.toInt())
            }
            canvas.drawText(fittedTitle, centerX, geometry.titleBaseline, paint)
            paint.clearShadowLayer()
        }

        fun drawBox(index: Int, bounds: HomeCardRect) {
            box.set(bounds.left, bounds.top, bounds.right, bounds.bottom)
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
            canvas.drawRoundRect(box, prefs.dp(16f), prefs.dp(16f), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = prefs.dp(1.5f)
            paint.color = stroke
            canvas.drawRoundRect(box, prefs.dp(16f), prefs.dp(16f), paint)
            paint.style = Paint.Style.FILL

            val isDays = index == 0
            val rawValue = if (isDays) countdown.days else when (index) {
                1 -> countdown.hours
                2 -> countdown.minutes
                else -> countdown.seconds
            }
            val numberText = rawValue.toString()
            val label = when (index) {
                0 -> "ימים"
                1 -> "שעות"
                2 -> "דקות"
                else -> "שניות"
            }
            drawCenteredPair(
                canvas = canvas,
                centerX = bounds.centerX,
                centerY = bounds.centerY,
                number = numberText,
                label = label,
                bodyTypeface = prefs.homeBodyTypeface(),
                numberSize = prefs.sp(if (isDays) 44f else 39f),
                labelSize = prefs.sp(17f),
                maxWidth = bounds.width * 0.90f,
                maxHeight = bounds.height * 0.90f,
                numberColor = numberColor,
                labelColor = labelColor,
                shadow = !isPersonal && (theme == CountdownPrefs.THEME_GOLD || theme == CountdownPrefs.THEME_PREMIUM)
            )
        }

        geometry.dayCard?.let { drawBox(0, it) }
        lower.forEachIndexed { index, unit ->
            geometry.lowerCards.getOrNull(index)?.let { drawBox(unit, it) }
        }

        if (countdown.complete) {
            if (isPersonal) {
                PersonalTextContrast.drawForRegion(
                    canvas, bitmap, w, h,
                    RectF(w * 0.06f, h * 0.245f - prefs.sp(25f),
                        w * 0.94f, h * 0.245f + prefs.sp(8f)),
                    paint
                )
            }
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(prefs.homeBodyTypeface(), Typeface.BOLD)
            val completeText = fitCenteredText(paint, "האירוע כבר הגיע", prefs.sp(25f), prefs.sp(17f), w * 0.88f)
            paint.color = accent
            paint.setShadowLayer(prefs.dp(3f), 0f, prefs.dp(2f), 0xA0000000.toInt())
            canvas.drawText(completeText, centerX, h * 0.245f, paint)
            paint.clearShadowLayer()
        }

        if (isPersonal) {
            PersonalTextContrast.drawForRegion(
                canvas, bitmap, w, h,
                RectF(w * 0.06f, geometry.dateBaseline - prefs.sp(23f),
                    w * 0.94f, geometry.dateBaseline + prefs.sp(7f)),
                paint
            )
        }
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = prefs.homeBodyTypeface()
        paint.color = accent
        val dateText = fitCenteredText(
            paint, prefs.formattedTargetDate(), prefs.sp(20f), prefs.sp(15f), w * 0.90f
        )
        paint.setShadowLayer(prefs.dp(2f), 0f, prefs.dp(1f), 0x99000000.toInt())
        canvas.drawText(dateText, centerX, geometry.dateBaseline, paint)
        paint.clearShadowLayer()

        val quote = prefs.dailyQuote(nowMillis).ifBlank {
            if (countdown.complete) "הספירה הסתיימה" else "כל שנייה מקרבת את הרגע"
        }
        if (isPersonal && quote.isNotBlank()) {
            PersonalTextContrast.drawForRegion(
                canvas, bitmap, w, h,
                RectF(w * 0.05f, geometry.quoteBaseline - prefs.sp(22f),
                    w * 0.95f, geometry.quoteBaseline + geometry.quoteMaxHeight + prefs.sp(5f)),
                paint
            )
        }
        paint.typeface = prefs.homeBodyTypeface()
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.setShadowLayer(prefs.dp(3f), 0f, prefs.dp(1f), 0xC0000000.toInt())
        drawWrappedCenteredText(
            canvas = canvas,
            text = quote,
            centerX = centerX,
            firstBaseline = geometry.quoteBaseline,
            maxWidth = w * 0.90f,
            textPaint = paint,
            preferredSize = prefs.sp(18f),
            minimumSize = prefs.sp(14f),
            maxHeight = geometry.quoteMaxHeight
        )
        paint.clearShadowLayer()
    }

    private fun drawCenteredPair(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        number: String,
        label: String,
        bodyTypeface: Typeface,
        numberSize: Float,
        labelSize: Float,
        maxWidth: Float,
        maxHeight: Float,
        numberColor: Int,
        labelColor: Int,
        shadow: Boolean
    ) {
        paint.textAlign = Paint.Align.CENTER
        var chosenNumberSize = numberSize
        var chosenLabelSize = labelSize
        val minimumNumberSize = numberSize * 0.72f
        val minimumLabelSize = labelSize * 0.75f

        fun combinedTextHeight(): Float {
            paint.typeface = Typeface.create(bodyTypeface, Typeface.BOLD)
            paint.textSize = chosenNumberSize
            val numberMetrics = paint.fontMetrics
            paint.typeface = bodyTypeface
            paint.textSize = chosenLabelSize
            val labelMetrics = paint.fontMetrics
            return (numberMetrics.descent - numberMetrics.ascent) +
                maxOf(2f, chosenNumberSize * 0.07f) +
                (labelMetrics.descent - labelMetrics.ascent)
        }

        // Emergency-only font reduction: first let the responsive card grow; shrink
        // only when even that card cannot hold the selected system font scale.
        while (combinedTextHeight() > maxHeight &&
            (chosenNumberSize > minimumNumberSize || chosenLabelSize > minimumLabelSize)
        ) {
            chosenNumberSize = (chosenNumberSize * 0.96f).coerceAtLeast(minimumNumberSize)
            chosenLabelSize = (chosenLabelSize * 0.96f).coerceAtLeast(minimumLabelSize)
        }

        paint.typeface = Typeface.create(bodyTypeface, Typeface.BOLD)
        val fittedNumber = fitCenteredText(
            paint, number, chosenNumberSize, chosenNumberSize * 0.82f, maxWidth
        )
        val numberTextSize = paint.textSize
        val numberMetrics = paint.fontMetrics
        val numberHeight = numberMetrics.descent - numberMetrics.ascent

        paint.typeface = bodyTypeface
        val fittedLabel = fitCenteredText(
            paint, label, chosenLabelSize, chosenLabelSize * 0.88f, maxWidth
        )
        val labelTextSize = paint.textSize
        val labelMetrics = paint.fontMetrics
        val labelHeight = labelMetrics.descent - labelMetrics.ascent

        val gap = maxOf(2f, chosenNumberSize * 0.07f)
        val totalHeight = numberHeight + gap + labelHeight
        val firstTop = centerY - totalHeight / 2f
        val numberBaseline = firstTop - numberMetrics.ascent
        val labelBaseline = firstTop + numberHeight + gap - labelMetrics.ascent

        paint.typeface = Typeface.create(bodyTypeface, Typeface.BOLD)
        paint.textSize = numberTextSize
        paint.color = numberColor
        if (shadow) paint.setShadowLayer(2f, 0f, 1f, 0x66000000)
        canvas.drawText(fittedNumber, centerX, numberBaseline, paint)
        paint.clearShadowLayer()

        paint.typeface = bodyTypeface
        paint.textSize = labelTextSize
        paint.color = labelColor
        canvas.drawText(fittedLabel, centerX, labelBaseline, paint)
    }

    private fun fitCenteredText(
        textPaint: Paint,
        text: String,
        preferredSize: Float,
        minimumSize: Float,
        maxWidth: Float
    ): String {
        textPaint.textSize = preferredSize
        val step = maxOf(0.5f, preferredSize * 0.025f)
        while (textPaint.measureText(text) > maxWidth && textPaint.textSize > minimumSize) {
            textPaint.textSize = (textPaint.textSize - step).coerceAtLeast(minimumSize)
        }
        if (textPaint.measureText(text) <= maxWidth) return text
        var fitted = text
        while (fitted.isNotEmpty() && textPaint.measureText("$fitted…") > maxWidth) {
            fitted = fitted.dropLast(1)
        }
        return if (fitted.isEmpty()) "…" else "$fitted…"
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
        canvas: Canvas,
        text: String,
        centerX: Float,
        firstBaseline: Float,
        maxWidth: Float,
        textPaint: Paint,
        preferredSize: Float,
        minimumSize: Float,
        maxHeight: Float
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
            maxLines = floor(maxHeight / lineHeight).toInt().coerceIn(1, 5)
            if (lines.size <= maxLines || size <= minimumSize) break
            size = (size - maxOf(0.75f, size * 0.04f)).coerceAtLeast(minimumSize)
        }
        if (lines.size > maxLines) {
            lines = lines.take(maxLines).toMutableList()
            var last = lines.last()
            while (last.isNotEmpty() && textPaint.measureText("$last…") > maxWidth) {
                last = last.dropLast(1)
            }
            lines[lines.lastIndex] = if (last.isEmpty()) "…" else "$last…"
        }
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, centerX, firstBaseline + index * lineHeight, textPaint)
        }
    }
}
