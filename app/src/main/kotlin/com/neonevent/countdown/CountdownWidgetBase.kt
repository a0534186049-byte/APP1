package com.neonevent.countdown

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import java.util.Locale
import kotlin.math.roundToInt

abstract class CountdownWidgetBase : AppWidgetProvider() {
    protected abstract fun displaysTitle(): Boolean

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_TICK -> updateThisProvider(context)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                updateThisProvider(context)
                scheduleTicksIfNeeded(context)
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateProvider(context, manager, ids, displaysTitle())
        if (ids.isNotEmpty()) scheduleTicks(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // Rebuild the bitmap at the new widget dimensions after the launcher resizes it.
        updateProvider(context, appWidgetManager, intArrayOf(appWidgetId), displaysTitle())
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleTicksIfNeeded(context)
    }

    override fun onDisabled(context: Context) {
        cancelTicks(context)
        super.onDisabled(context)
    }

    private fun updateThisProvider(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
        updateProvider(context, manager, ids, displaysTitle())
    }

    private fun scheduleTicksIfNeeded(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
        if (ids.isNotEmpty()) scheduleTicks(context) else cancelTicks(context)
    }

    /**
     * A non-wakeup, inexact one-minute alarm keeps the displayed countdown reasonably fresh.
     * Every delivery recalculates from the saved event date; it never decrements old values.
     */
    private fun scheduleTicks(context: Context) {
        val requestCode = if (displaysTitle()) REQUEST_WITH_TITLE else REQUEST_NO_TITLE
        val intent = Intent(context, javaClass).setAction(ACTION_WIDGET_TICK)
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching {
            alarm.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + REFRESH_INTERVAL_MS,
                REFRESH_INTERVAL_MS,
                pending
            )
        }.onFailure { Log.e(TAG, "Could not schedule widget refresh", it) }
    }

    private fun cancelTicks(context: Context) {
        val requestCode = if (displaysTitle()) REQUEST_WITH_TITLE else REQUEST_NO_TITLE
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, javaClass).setAction(ACTION_WIDGET_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pending)
        pending.cancel()
    }

    companion object {
        const val ACTION_WIDGET_TICK = "com.neonevent.countdown.WIDGET_TICK"
        private const val REQUEST_WITH_TITLE = 9911
        private const val REQUEST_NO_TITLE = 9912
        private const val REFRESH_INTERVAL_MS = 60_000L
        private const val TAG = "CountdownWidget"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            updateProvider(
                context, manager,
                manager.getAppWidgetIds(ComponentName(context, CountdownWidgetWithTitleProvider::class.java)),
                true
            )
            updateProvider(
                context, manager,
                manager.getAppWidgetIds(ComponentName(context, CountdownWidgetNoTitleProvider::class.java)),
                false
            )
        }

        private fun updateProvider(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
            showTitle: Boolean
        ) {
            if (ids.isEmpty()) return
            try {
                val prefs = CountdownPrefs(context)
                val parts = CountdownMath.calculateDateOnly(
                    prefs.eventLocalDate(), System.currentTimeMillis()
                )

                // Build a separate RemoteViews per widget because launchers can resize each
                // instance independently and each one needs a bitmap matching its own bounds.
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.countdown_widget)
                    views.setViewVisibility(
                        R.id.widget_title,
                        if (showTitle && prefs.showTitle) View.VISIBLE else View.GONE
                    )
                    views.setTextViewText(R.id.widget_title, prefs.eventTitle)
                    views.setTextViewText(R.id.widget_days_value, parts.days.toString())
                    views.setTextViewText(R.id.widget_hours_value, String.format(Locale.ROOT, "%02d", parts.hours))
                    views.setTextViewText(R.id.widget_minutes_value, String.format(Locale.ROOT, "%02d", parts.minutes))
                    views.setTextViewText(R.id.widget_seconds_value, String.format(Locale.ROOT, "%02d", parts.seconds))
                    views.setTextViewText(R.id.widget_days_label, "ימים")
                    views.setTextViewText(R.id.widget_hours_label, "שעות")
                    views.setTextViewText(R.id.widget_minutes_label, "דקות")
                    views.setTextViewText(R.id.widget_seconds_label, "שניות")
                    views.setTextViewText(R.id.widget_target_date, prefs.formattedTargetDate())

                    widgetBackgroundBitmap(context, manager.getAppWidgetOptions(id), prefs.backgroundBitmap(), showTitle)
                        ?.let { views.setImageViewBitmap(R.id.widget_background, it) }

                    val textColor = when {
                        prefs.theme == CountdownPrefs.THEME_PERSONAL && prefs.cubeStyle != CountdownPrefs.CUBES_DARK -> 0xFF182033.toInt()
                        prefs.theme == CountdownPrefs.THEME_GOLD -> 0xFFFFE8B2.toInt()
                        prefs.theme == CountdownPrefs.THEME_PREMIUM -> 0xFFFFF0D8.toInt()
                        else -> Color.WHITE
                    }
                    listOf(
                        R.id.widget_title, R.id.widget_days_value, R.id.widget_hours_value,
                        R.id.widget_minutes_value, R.id.widget_seconds_value,
                        R.id.widget_days_label, R.id.widget_hours_label,
                        R.id.widget_minutes_label, R.id.widget_seconds_label, R.id.widget_target_date
                    ).forEach { views.setTextColor(it, textColor) }

                    val cubeDrawable = when {
                        prefs.theme == CountdownPrefs.THEME_PERSONAL && prefs.cubeStyle == CountdownPrefs.CUBES_LIGHT -> R.drawable.widget_cube_light
                        prefs.theme == CountdownPrefs.THEME_PERSONAL && prefs.cubeStyle == CountdownPrefs.CUBES_FROSTED -> R.drawable.widget_cube_frosted
                        prefs.theme == CountdownPrefs.THEME_GOLD -> R.drawable.widget_cube_gold
                        prefs.theme == CountdownPrefs.THEME_GRADIENT -> R.drawable.widget_cube_gradient
                        else -> R.drawable.widget_cube_premium
                    }
                    listOf(
                        R.id.widget_cube_days, R.id.widget_cube_hours,
                        R.id.widget_cube_minutes, R.id.widget_cube_seconds
                    ).forEach { views.setInt(it, "setBackgroundResource", cubeDrawable) }

                    manager.updateAppWidget(id, views)
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to build or update countdown widget RemoteViews", exception)
                val fallback = RemoteViews(context.packageName, R.layout.countdown_widget_error)
                ids.forEach { id ->
                    runCatching { manager.updateAppWidget(id, fallback) }
                        .onFailure { Log.e(TAG, "Failed to display widget fallback for id=$id", it) }
                }
            }
        }

        /**
         * RemoteViews transports bitmaps to the launcher process. Render the background at
         * the widget's actual bounds instead of parceling the full-resolution source image.
         * FIT_CENTER and the edge-color fill are shared with the main screen renderer.
         */
        private fun widgetBackgroundBitmap(
            context: Context,
            options: Bundle,
            source: Bitmap?,
            showTitle: Boolean
        ): Bitmap? {
            if (source == null || source.isRecycled || source.width <= 0 || source.height <= 0) return null

            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            val widthDp = maxOf(minWidthDp, maxWidthDp, 250).coerceAtLeast(180)
            val fallbackHeightDp = if (showTitle) 70 else 58
            val heightDp = maxOf(minHeightDp, maxHeightDp, fallbackHeightDp)

            val density = context.resources.displayMetrics.density
            val widthPx = (widthDp * density).roundToInt().coerceIn(160, 900)
            val heightPx = (heightDp * density).roundToInt().coerceIn(56, 900)
            val output = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.RGB_565)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            BackgroundEdgeFiller.draw(canvas, source, widthPx.toFloat(), heightPx.toFloat(), paint)
            return output
        }
    }
}
