package com.neonevent.countdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenGeometryCalculatorTest {
    @Test fun threeLowerCardsStayEqualAndInsideMarginsOnPhoneAndTablet() {
        val widths = listOf(320f, 411f, 800f)
        widths.forEach { widthDp ->
            val layout = HomeScreenGeometryCalculator.calculate(
                widthPx = widthDp,
                heightPx = 900f,
                density = 1f,
                scaledDensity = 1f,
                showDays = true,
                lowerCount = 3
            )
            assertEquals(3, layout.lowerCards.size)
            val cards = layout.lowerCards
            assertEquals(cards[0].width, cards[1].width, 0.01f)
            assertEquals(cards[1].width, cards[2].width, 0.01f)
            assertEquals(cards[0].height, cards[1].height, 0.01f)
            assertEquals(cards[1].height, cards[2].height, 0.01f)
            assertTrue(cards[0].left >= 15f)
            assertTrue(widthDp - cards.last().right >= 15f)
            assertTrue(cards[0].right <= cards[1].left)
            assertTrue(cards[1].right <= cards[2].left)
            val day = layout.dayCard!!
            assertTrue(day.width <= widthDp * 0.50f)
            assertTrue(day.width <= 240f)
            assertTrue(day.left >= 0f)
            assertTrue(day.right <= widthDp)
            assertTrue(day.bottom < cards.first().top)
        }
    }

    @Test fun largeSystemFontExpandsCardHeightInsteadOfReducingBaseText() {
        val normal = HomeScreenGeometryCalculator.calculate(320f, 900f, 1f, 1f, true, 3)
        val largeFont = HomeScreenGeometryCalculator.calculate(320f, 900f, 1f, 1.6f, true, 3)
        assertTrue(largeFont.lowerCards.first().height > normal.lowerCards.first().height)
        assertTrue(largeFont.dayCard!!.height > normal.dayCard!!.height)
        assertTrue(largeFont.lowerCards.first().width == normal.lowerCards.first().width)
    }

    @Test fun hidingDayDoesNotLeaveAnOverlapOrNegativeLayout() {
        val layout = HomeScreenGeometryCalculator.calculate(320f, 640f, 1f, 1f, false, 3)
        assertEquals(null, layout.dayCard)
        assertEquals(3, layout.lowerCards.size)
        assertTrue(layout.lowerCards.all { it.left >= 0f && it.right <= 320f && it.height > 0f })
    }

    @Test fun landscapeStillFitsTheTwoCountdownRows() {
        val layout = HomeScreenGeometryCalculator.calculate(411f, 320f, 1f, 1f, true, 3)
        val day = layout.dayCard!!
        assertTrue(day.top >= layout.titleBaseline)
        assertTrue(day.bottom < layout.lowerCards.first().top)
        assertTrue(layout.lowerCards.last().bottom < layout.dateBaseline)
        assertTrue(layout.quoteBaseline + layout.quoteMaxHeight <= 320f)
        assertTrue(layout.quoteMaxHeight >= 18f)
    }

    @Test fun landscapeWithLargeSystemFontKeepsBoxesInsideScreen() {
        val layout = HomeScreenGeometryCalculator.calculate(411f, 320f, 1f, 1.6f, true, 3)
        assertTrue(layout.dayCard!!.bottom < layout.lowerCards.first().top)
        assertTrue(layout.lowerCards.last().bottom <= layout.dateBaseline)
        assertTrue(layout.quoteBaseline + layout.quoteMaxHeight <= 320f)
    }

    @Test fun allUnitsStillHaveOneLowerCardWhenSettingsHideMostUnits() {
        val layout = HomeScreenGeometryCalculator.calculate(320f, 640f, 1f, 1f, true, 1)
        assertEquals(1, layout.lowerCards.size)
        assertTrue(layout.lowerCards.single().width > 0f)
    }

    @Test fun dateTextHasMoreAirBelowTheCountdownCardsInPortraitAndLandscape() {
        val portraitSizes = listOf(
            320f to 640f,
            360f to 780f,
            411f to 915f,
            800f to 1280f
        )
        portraitSizes.forEach { (width, height) ->
            val layout = HomeScreenGeometryCalculator.calculate(
                widthPx = width,
                heightPx = height,
                density = 1f,
                scaledDensity = 1f,
                showDays = true,
                lowerCount = 3
            )
            assertTrue(
                "Portrait gap was too small for ${width}x${height}",
                layout.dateBaseline - layout.lowerCards.last().bottom >= 28f
            )
            assertTrue(layout.quoteBaseline + layout.quoteMaxHeight <= height + 0.01f)
            assertTrue(layout.quoteMaxHeight >= 0f)
        }

        val landscapeSizes = listOf(411f to 320f, 800f to 600f)
        landscapeSizes.forEach { (width, height) ->
            val layout = HomeScreenGeometryCalculator.calculate(
                widthPx = width,
                heightPx = height,
                density = 1f,
                scaledDensity = 1f,
                showDays = true,
                lowerCount = 3
            )
            assertTrue(
                "Landscape gap was too small for ${width}x${height}",
                layout.dateBaseline - layout.lowerCards.last().bottom >= 12f
            )
            assertTrue(layout.quoteBaseline + layout.quoteMaxHeight <= height + 0.01f)
            assertTrue(layout.quoteMaxHeight >= 0f)
        }
    }

    @Test fun increasedDateGapKeepsTextAreaWithinBoundsAtLargerSystemFontScale() {
        listOf(1.0f, 1.3f, 1.6f).forEach { fontScale ->
            val layout = HomeScreenGeometryCalculator.calculate(
                widthPx = 360f,
                heightPx = 780f,
                density = 1f,
                scaledDensity = fontScale,
                showDays = true,
                lowerCount = 3
            )
            assertTrue(layout.dateBaseline >= layout.lowerCards.last().bottom + 28f)
            assertTrue(layout.quoteBaseline + layout.quoteMaxHeight <= 780f + 0.01f)
            assertTrue(layout.quoteMaxHeight >= 0f)
        }
    }
}
