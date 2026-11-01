package com.neonevent.countdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class BackgroundEdgeFillerInstrumentedTest {
    private fun assertColorNear(expected: Int, actual: Int, tolerance: Int = 8) {
        assertTrue("red channel differs: $expected vs $actual",
            abs(Color.red(expected) - Color.red(actual)) <= tolerance)
        assertTrue("green channel differs: $expected vs $actual",
            abs(Color.green(expected) - Color.green(actual)) <= tolerance)
        assertTrue("blue channel differs: $expected vs $actual",
            abs(Color.blue(expected) - Color.blue(actual)) <= tolerance)
    }

    @Test fun wide640x1136DisplayFillsSideBandsFromMatchingImageEdges() {
        val source = Bitmap.createBitmap(497, 1025, Bitmap.Config.ARGB_8888)
        val leftEdge = Color.rgb(12, 34, 56)
        val rightEdge = Color.rgb(78, 90, 123)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                source.setPixel(x, y, when {
                    x < 5 -> leftEdge
                    x >= source.width - 5 -> rightEdge
                    else -> Color.WHITE
                })
            }
        }

        val output = Bitmap.createBitmap(640, 1136, Bitmap.Config.ARGB_8888)
        BackgroundEdgeFiller.draw(Canvas(output), source, 640f, 1136f, Paint(Paint.ANTI_ALIAS_FLAG))

        assertColorNear(leftEdge, output.getPixel(10, 568))
        assertColorNear(rightEdge, output.getPixel(630, 568))
        // The image is FIT_CENTER: its actual image area begins about 45px from the edge.
        assertEquals(Color.WHITE, output.getPixel(55, 568))
    }

    @Test fun tall1080x2400DisplayFillsTopAndBottomBandsFromMatchingImageEdges() {
        val source = Bitmap.createBitmap(497, 1025, Bitmap.Config.ARGB_8888)
        val topEdge = Color.rgb(18, 40, 62)
        val bottomEdge = Color.rgb(84, 106, 128)
        for (y in 0 until source.height) {
            val color = when {
                y < 5 -> topEdge
                y >= source.height - 5 -> bottomEdge
                else -> Color.WHITE
            }
            for (x in 0 until source.width) source.setPixel(x, y, color)
        }

        val output = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        BackgroundEdgeFiller.draw(Canvas(output), source, 1080f, 2400f, Paint(Paint.ANTI_ALIAS_FLAG))

        assertColorNear(topEdge, output.getPixel(540, 10))
        assertColorNear(bottomEdge, output.getPixel(540, 2390))
        // The complete source image remains centered between the top and bottom bands.
        assertEquals(Color.WHITE, output.getPixel(540, 1200))
    }
}
