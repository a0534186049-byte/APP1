package com.neonevent.countdown

/**
 * Pixel-space geometry for the home screen. Widths follow the available screen width;
 * font-dependent minimum heights prevent the text pair from being clipped.
 */
data class HomeCardRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
}

data class HomeScreenGeometry(
    val dayCard: HomeCardRect?,
    val lowerCards: List<HomeCardRect>,
    val titleBaseline: Float,
    val dateBaseline: Float,
    val quoteBaseline: Float,
    val quoteMaxHeight: Float
)

object HomeScreenGeometryCalculator {
    fun calculate(
        widthPx: Float,
        heightPx: Float,
        density: Float,
        scaledDensity: Float,
        showDays: Boolean,
        lowerCount: Int
    ): HomeScreenGeometry {
        val safeDensity = density.coerceAtLeast(0.1f)
        val widthDp = (widthPx / safeDensity).coerceAtLeast(1f)
        val heightDp = (heightPx / safeDensity).coerceAtLeast(1f)
        val fontScale = (scaledDensity / safeDensity).coerceAtLeast(1f)

        val isLandscape = heightDp < widthDp * 0.85f
        val sideDp = minOf(if (isLandscape) 16f else 20f, widthDp * 0.06f)
        val gapDp = minOf(if (isLandscape) 8f else 10f, widthDp * 0.03f)
        val count = lowerCount.coerceIn(0, 3)
        val lowerWidthDp = if (count == 0) 0f else
            (widthDp - sideDp * 2f - gapDp * (count - 1)).coerceAtLeast(1f) / count

        // Normal sizes keep a consistent ratio. Only enlarged system fonts cause height growth.
        val lowerTextBlockDp = (39f + 17f) * fontScale * 1.14f + 4f
        val lowerHeightDp = if (count == 0) 0f else if (isLandscape) {
            maxOf(lowerWidthDp / 1.50f, minOf(lowerTextBlockDp + 8f, 86f))
        } else {
            maxOf(lowerWidthDp / 1.36f, lowerTextBlockDp + 10f)
        }

        val dayWidthDp = if (showDays) minOf(widthDp * 0.46f, 220f, widthDp - sideDp * 2f) else 0f
        val dayTextBlockDp = (44f + 17f) * fontScale * 1.14f + 4f
        val dayHeightDp = if (showDays) {
            if (isLandscape) maxOf(dayWidthDp / 1.75f, minOf(dayTextBlockDp + 10f, 110f))
            else maxOf(dayWidthDp / 1.25f, dayTextBlockDp + 14f)
        } else 0f

        val titleBaselineDp = heightDp * (if (isLandscape) 0.10f else 0.205f)
        val dayTopDp = if (isLandscape) {
            maxOf(heightDp * 0.18f, titleBaselineDp + 34f * fontScale * 0.65f + 4f)
        } else {
            maxOf(heightDp * 0.275f, titleBaselineDp + 34f * fontScale * 0.78f + 15f)
        }
        val dayCard = if (showDays) {
            HomeCardRect(
                left = (widthDp - dayWidthDp) / 2f * safeDensity,
                top = dayTopDp * safeDensity,
                width = dayWidthDp * safeDensity,
                height = dayHeightDp * safeDensity
            )
        } else null

        val lowerTopDp = if (showDays) dayTopDp + dayHeightDp + (if (isLandscape) 8f else 18f) else dayTopDp
        val lowerCards = (0 until count).map { index ->
            val leftDp = sideDp + index * (lowerWidthDp + gapDp)
            HomeCardRect(
                left = leftDp * safeDensity,
                top = lowerTopDp * safeDensity,
                width = lowerWidthDp * safeDensity,
                height = lowerHeightDp * safeDensity
            )
        }

        val lowerBottomDp = if (count > 0) lowerTopDp + lowerHeightDp else
            if (showDays) dayTopDp + dayHeightDp else dayTopDp
        val dateBaselineDp = maxOf(lowerBottomDp + (if (isLandscape) 4f else 20f), heightDp * 0.60f)
        val quoteBaselineDp = dateBaselineDp + (if (isLandscape) 12f else 28f)
        val quoteMaxHeightDp = (heightDp - (if (isLandscape) 2f else 16f) - quoteBaselineDp).coerceAtLeast(0f)

        return HomeScreenGeometry(
            dayCard = dayCard,
            lowerCards = lowerCards,
            titleBaseline = titleBaselineDp * safeDensity,
            dateBaseline = dateBaselineDp * safeDensity,
            quoteBaseline = quoteBaselineDp * safeDensity,
            quoteMaxHeight = quoteMaxHeightDp * safeDensity
        )
    }
}
