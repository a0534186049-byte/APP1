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
import kotlin.math.roundToInt

abstract class CountdownWidgetBase : AppWidgetProvider() {
    protected abstract fun displaysTitle(): Boolean

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_TICK,
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
        manager: AppWidgetManager,
        id: Int,
        options: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, manager, id, options)
        updateProvider(context, manager, intArrayOf(id), displaysTitle())
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
     * Restore the previous four-field widget refresh. Android batches this inexact
     * non-wakeup alarm; every delivery recalculates from the saved Hebrew target date.
     */
    private fun scheduleTicks(context: Context) {
        val pending = tickPendingIntent(context)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching {
            alarm.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + REFRESH_INTERVAL_MS,
                REFRESH_INTERVAL_MS,
                pending
            )
        }.onFailure { Log.e(TAG, "Unable to schedule the widget refresh", it) }
    }

    private fun cancelTicks(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = tickPendingIntent(context)
        alarm.cancel(pending)
        pending.cancel()
    }

    private fun tickPendingIntent(context: Context): PendingIntent {
        val requestCode = if (displaysTitle()) REQUEST_WITH_TITLE else REQUEST_NO_TITLE
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, javaClass).setAction(ACTION_WIDGET_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_WIDGET_TICK = "com.neonevent.countdown.WIDGET_TICK"
        private const val TAG = "CountdownWidget"
        private const val REQUEST_WITH_TITLE = 9911
        private const val REQUEST_NO_TITLE = 9912
        private const val REFRESH_INTERVAL_MS = 60_000L

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            updateProvider(
                context,
                manager,
                manager.getAppWidgetIds(
                    ComponentName(context, CountdownWidgetWithTitleProvider::class.java)
                ),
                true
            )
            updateProvider(
                context,
                manager,
                manager.getAppWidgetIds(
                    ComponentName(context, CountdownWidgetNoTitleProvider::class.java)
                ),
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
                val now = System.currentTimeMillis()
                val parts = CountdownMath.calculateDateOnly(prefs.eventLocalDate(), now)

                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.countdown_widget)
                    views.setViewVisibility(
                        R.id.widget_title,
                        if (showTitle && prefs.showTitle) View.VISIBLE else View.GONE
                    )
                    views.setTextViewText(R.id.widget_title, prefs.eventTitle)
                    views.setTextViewText(R.id.widget_days_value, parts.days.toString())
                    views.setTextViewText(R.id.widget_hours_value, parts.hours.toString().padStart(2, '0'))
                    views.setTextViewText(R.id.widget_minutes_value, parts.minutes.toString().padStart(2, '0'))
                    views.setTextViewText(R.id.widget_seconds_value, parts.seconds.toString().padStart(2, '0'))
                    views.setTextViewText(R.id.widget_target_date, prefs.formattedTargetDate())

                    val background = widgetBackgroundBitmap(
                        context,
                        manager.getAppWidgetOptions(id),
                        prefs.widgetBackgroundBitmap(),
                        showTitle
                    )
                    background?.let { views.setImageViewBitmap(R.id.widget_background, it) }
                    views.setViewVisibility(R.id.widget_background_overlay, View.VISIBLE)

                    val goldText = 0xFFFFE8B2.toInt()
                    views.setTextColor(R.id.widget_title, 0xFFFFF2D7.toInt())
                    views.setTextColor(R.id.widget_target_date, goldText)
                    listOf(
                        R.id.widget_days_value,
                        R.id.widget_days_label,
                        R.id.widget_hours_value,
                        R.id.widget_hours_label,
                        R.id.widget_minutes_value,
                        R.id.widget_minutes_label,
                        R.id.widget_seconds_value,
                        R.id.widget_seconds_label
                    ).forEach { views.setTextColor(it, goldText) }

                    listOf(
                        R.id.widget_cube_days,
                        R.id.widget_cube_hours,
                        R.id.widget_cube_minutes,
                        R.id.widget_cube_seconds
                    ).forEach {
                        views.setInt(it, "setBackgroundResource", R.drawable.widget_cube_gold)
                    }

                    manager.updateAppWidget(id, views)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to update countdown widget", error)
                val fallback = RemoteViews(context.packageName, R.layout.countdown_widget_error)
                ids.forEach { id ->
                    runCatching { manager.updateAppWidget(id, fallback) }
                        .onFailure { Log.e(TAG, "Unable to apply the widget fallback", it) }
                }
            }
        }

        private fun widgetBackgroundBitmap(
            context: Context,
            options: Bundle,
            source: Bitmap?,
            showTitle: Boolean
        ): Bitmap? {
            if (source == null || source.isRecycled || source.width <= 0 || source.height <= 0) {
                return null
            }

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
            BackgroundEdgeFiller.drawCenterCrop(
                canvas,
                source,
                widthPx.toFloat(),
                heightPx.toFloat(),
                paint
            )
            return output
        }
    }
}
