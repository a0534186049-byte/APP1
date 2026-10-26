package com.neonevent.countdown

import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

class CountdownWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = CountdownEngine()

    private inner class CountdownEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val prefs by lazy { CountdownPrefs(this@CountdownWallpaperService) }
        private var visible = false
        private val drawTask = object : Runnable {
            override fun run() {
                if (!visible) return
                drawFrame()
                handler.postDelayed(this, if (prefs.animationSpeed == 0) 1000L else 34L)
            }
        }

        override fun onVisibilityChanged(visibleNow: Boolean) {
            visible = visibleNow
            handler.removeCallbacks(drawTask)
            if (visible) handler.post(drawTask)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (visible) drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(drawTask)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(drawTask)
            super.onDestroy()
        }

        private fun drawFrame() {
            if (!visible) return
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas()
                if (canvas != null) {
                    NeonCountdownRenderer.draw(
                        canvas, canvas.width.toFloat(), canvas.height.toFloat(),
                        prefs, System.currentTimeMillis()
                    )
                }
            } catch (_: Exception) {
                // Surface can disappear while the launcher changes wallpaper.
            } finally {
                if (canvas != null) {
                    runCatching { surfaceHolder.unlockCanvasAndPost(canvas) }
                }
            }
        }
    }
}
