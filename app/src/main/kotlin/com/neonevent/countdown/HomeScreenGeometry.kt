package com.neonevent.countdown

/**
 * Pixel-space geometry for the home screen. Card dimensions are constrained by both
 * available width and height, so the date and quote remain on screen on compact devices.
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
        lowerCount: Int,
        stackVertically: Boolean = false
    ): HomeScreenGeometry {
        val safeDensity = density.coerceAtLeast(0.1f)
        val widthDp = (widthPx / safeDensity).coerceAtLeast(1f)
        val heightDp = (heightPx / safeDensity).coerceAtLeast(1f)
        val fontScale = (scaledDensity / safeDensity).coerceAtLeast(1f)

        val isLandscape = heightDp < widthDp * 0.85f
        val compactHeight = heightDp < 700f
        val sideDp = minOf(if (isLandscape) 14f else 18f, widthDp * 0.055f)
        val gapDp = minOf(if (isLandscape) 7f else 8f, widthDp * 0.025f)
        val count = lowerCount.coerceIn(0, if (stackVertically) 4 else 3)
        val lowerWidthDp = if (count == 0) 0f else {
            (widthDp - sideDp * 2f - gapDp * (count - 1)).coerceAtLeast(1f) / count
        }

        // Preserve the approved 44sp/39sp numbers and 17sp labels at normal scale.
        // Only reduce typography when a small screen or large system font makes it necessary.
        val lowerTextBlockDp = (39f + 17f) * fontScale * 1.05f + 2f
        val lowerPreferredHeightDp = lowerTextBlockDp + 4f
        var lowerHeightDp = if (stackVertically || count == 0) {
            lowerPreferredHeightDp
        } else {
            maxOf(minOf(lowerWidthDp / 1.7f, 100f), lowerPreferredHeightDp)
        }

        val dayWidthDp = if (showDays) {
            minOf(widthDp * 0.42f, 196f, widthDp - sideDp * 2f)
        } else 0f
        val dayTextBlockDp = (44f + 17f) * fontScale * 1.05f + 2f
        val dayPreferredHeightDp = dayTextBlockDp + 4f
        var dayHeightDp = if (showDays) {
            maxOf(
                dayWidthDp / if (isLandscape) 2.2f else 1.85f,
                dayPreferredHeightDp
            )
        } else 0f

        val titleBaselineDp = heightDp * when {
            isLandscape -> 0.10f
            compactHeight -> 0.15f
            else -> 0.17f
        }
        val titleToCardGapDp = 34f * fontScale * 0.68f + 8f
        val dayTopDp = maxOf(
            heightDp * if (isLandscape) 0.17f else if (compactHeight) 0.22f else 0.24f,
            titleBaselineDp + titleToCardGapDp
        )

        val verticalGapDp = minOf(if (isLandscape) 5f else 6f, gapDp)
        val dateGapDp = when {
            stackVertically -> if (isLandscape) 16f else 20f
            isLandscape -> 22f
            compactHeight -> 28f
            else -> 32f
        }
        val dateTextHeightDp = 22f
        val quoteGapDp = if (isLandscape) 12f else 16f
        val quoteReserveDp = if (isLandscape) 24f else 40f
        val bottomPaddingDp = 8f

        if (stackVertically && count > 0) {
            val gapsTotal = (count - 1) * verticalGapDp
            val reserved = dateGapDp + dateTextHeightDp + quoteGapDp +
                quoteReserveDp + bottomPaddingDp
            val availablePerCard = (
                (heightDp - dayTopDp - reserved - gapsTotal) / count
            ).coerceAtLeast(1f)
            lowerHeightDp = minOf(lowerPreferredHeightDp, availablePerCard)
        } else if (!stackVertically && count > 0) {
            // The two-row layout must leave room for the date and quote even in landscape.
            val dayToLowerGapDp = if (showDays) if (isLandscape) 8f else 10f else 0f
            val preferredGroupHeight = (if (showDays) dayHeightDp else 0f) +
                dayToLowerGapDp + lowerHeightDp
            val reserved = dateGapDp + dateTextHeightDp + quoteGapDp +
                quoteReserveDp + bottomPaddingDp
            val availableGroupHeight = (heightDp - dayTopDp - reserved).coerceAtLeast(1f)
            val fitScale = minOf(1f, availableGroupHeight / preferredGroupHeight.coerceAtLeast(1f))
            dayHeightDp *= fitScale
            lowerHeightDp *= fitScale
        }

        val lowerTopDp = if (showDays) {
            dayTopDp + dayHeightDp + if (isLandscape) 8f else 10f
        } else {
            dayTopDp
        }

        val lowerCards = if (stackVertically) {
            (0 until count).map { index ->
                HomeCardRect(
                    left = sideDp * safeDensity,
                    top = (lowerTopDp + index * (lowerHeightDp + verticalGapDp)) * safeDensity,
                    width = (widthDp - sideDp * 2f) * safeDensity,
                    height = lowerHeightDp * safeDensity
                )
            }
        } else {
            (0 until count).map { index ->
                val leftDp = sideDp + index * (lowerWidthDp + gapDp)
                HomeCardRect(
                    left = leftDp * safeDensity,
                    top = lowerTopDp * safeDensity,
                    width = lowerWidthDp * safeDensity,
                    height = lowerHeightDp * safeDensity
                )
            }
        }

        val dayCard = if (showDays) {
            HomeCardRect(
                left = (widthDp - dayWidthDp) / 2f * safeDensity,
                top = dayTopDp * safeDensity,
                width = dayWidthDp * safeDensity,
                height = dayHeightDp * safeDensity
            )
        } else null

        val lowerBottomDp = when {
            count > 0 && stackVertically ->
                lowerTopDp + count * lowerHeightDp + (count - 1) * verticalGapDp
            count > 0 -> lowerTopDp + lowerHeightDp
            showDays -> dayTopDp + dayHeightDp
            else -> dayTopDp
        }

        val dateBaselineDp = lowerBottomDp + dateGapDp
        val quoteBaselineDp = dateBaselineDp + dateTextHeightDp + quoteGapDp
        val quoteMaxHeightDp = (heightDp - bottomPaddingDp - quoteBaselineDp).coerceAtLeast(0f)

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
