package com.neonevent.countdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundEdgeFillerInstrumentedTest {

    @Test fun wideDisplayFillsSideBandsFromTheMatchingImageEdges() {
        val source = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888)
        val leftEdge = Color.rgb(12, 34, 56)
        val rightEdge = Color.rgb(78, 90, 123)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                source.setPixel(x, y, when {
                    x < 5 -> leftEdge
                    else -> rightEdge
                })
            }
        }

        val output = Bitmap.createBitmap(30, 40, Bitmap.Config.ARGB_8888)
        BackgroundEdgeFiller.draw(Canvas(output), source, 30f, 40f, Paint(Paint.ANTI_ALIAS_FLAG))

        assertEquals(leftEdge, output.getPixel(1, 20))
        assertEquals(rightEdge, output.getPixel(28, 20))
        // The original image stays centered and is not stretched to the screen width.
        assertEquals(leftEdge, output.getPixel(5, 20))
    }

    @Test fun tallDisplayFillsTopAndBottomBandsFromTheMatchingImageEdges() {
        val source = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888)
        val topEdge = Color.rgb(18, 40, 62)
        val bottomEdge = Color.rgb(84, 106, 128)
        for (y in 0 until source.height) {
            val color = if (y < 5) topEdge else if (y >= 15) bottomEdge else Color.WHITE
            for (x in 0 until source.width) source.setPixel(x, y, color)
        }

        val output = Bitmap.createBitmap(10, 30, Bitmap.Config.ARGB_8888)
        BackgroundEdgeFiller.draw(Canvas(output), source, 10f, 30f, Paint(Paint.ANTI_ALIAS_FLAG))

        assertEquals(topEdge, output.getPixel(5, 1))
        assertEquals(bottomEdge, output.getPixel(5, 28))
        assertEquals(Color.WHITE, output.getPixel(5, 15))
    }
}
