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
    @Test fun builtInCenterCropFillsPortraitViewportAndUsesTheImageCenter() {
        val source = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val color = when {
                    x < 25 -> Color.RED
                    x < 75 -> Color.GREEN
                    else -> Color.BLUE
                }
                source.setPixel(x, y, color)
            }
        }
        val target = Bitmap.createBitmap(50, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        BackgroundEdgeFiller.drawCenterCrop(
            canvas, source, target.width.toFloat(), target.height.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )

        assertEquals(Color.GREEN, target.getPixel(25, 0))
        assertEquals(Color.GREEN, target.getPixel(25, 50))
        assertEquals(Color.GREEN, target.getPixel(25, 99))
    }
}
