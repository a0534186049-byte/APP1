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
import android.graphics.RectF
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import kotlin.math.roundToInt

abstract class CountdownWidgetBase : AppWidgetProvider() {
    protected abstract fun displaysTitle(): Boolean

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_TICK, WidgetClockBoundary.TEN_HOURS.action,
            WidgetClockBoundary.ONE_HOUR.action, WidgetClockBoundary.MIDNIGHT.action,
            ACTION_EXACT_ALARM_PERMISSION_CHANGED, Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                updateAllWidgets(context)
                scheduleBoundaries(context)
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateProvider(context, manager, ids, displaysTitle())
        if (ids.isNotEmpty()) scheduleBoundaries(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        super.onAppWidgetOptionsChanged(context, manager, id, options)
        updateProvider(context, manager, intArrayOf(id), displaysTitle())
    }

    override fun onEnabled(context: Context) { super.onEnabled(context); scheduleBoundaries(context) }
    override fun onDisabled(context: Context) {
        if (!hasWidgets(context)) cancelBoundaries(context)
        super.onDisabled(context)
    }

    companion object {
        const val ACTION_WIDGET_TICK = "com.neonevent.countdown.WIDGET_TICK"
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        private const val TAG = "CountdownWidget"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            updateProvider(context, manager, manager.getAppWidgetIds(ComponentName(context, CountdownWidgetWithTitleProvider::class.java)), true)
            updateProvider(context, manager, manager.getAppWidgetIds(ComponentName(context, CountdownWidgetNoTitleProvider::class.java)), false)
        }

        private fun hasWidgets(context: Context): Boolean {
            val m = AppWidgetManager.getInstance(context)
            return m.getAppWidgetIds(ComponentName(context, CountdownWidgetWithTitleProvider::class.java)).isNotEmpty() ||
                m.getAppWidgetIds(ComponentName(context, CountdownWidgetNoTitleProvider::class.java)).isNotEmpty()
        }

        fun scheduleBoundaries(context: Context) {
            if (!hasWidgets(context)) { cancelBoundaries(context); return }
            val clock = WidgetClockSchedule.evaluate(CountdownPrefs(context).eventLocalDate(), System.currentTimeMillis())
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val exact = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
            WidgetClockBoundary.values().forEach { b ->
                val pi = boundaryPendingIntent(context, b)
                alarm.cancel(pi)
                clock.boundaries.firstOrNull { it.boundary == b }?.let { event ->
                    val exactSet = exact && runCatching {
                        alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.triggerAtMillis, pi)
                    }.onFailure { Log.w(TAG, "Exact alarm unavailable for ${b.name}; falling back to inexact", it) }.isSuccess
                    if (!exactSet) {
                        runCatching {
                            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.triggerAtMillis, pi)
                        }.onFailure { Log.e(TAG, "Unable to schedule fallback alarm for ${b.name}", it) }
                    }
                }
            }
        }

        private fun cancelBoundaries(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            WidgetClockBoundary.values().forEach { b ->
                val pi = boundaryPendingIntent(context, b)
                alarm.cancel(pi); pi.cancel()
            }
        }

        private fun boundaryPendingIntent(context: Context, b: WidgetClockBoundary) =
            PendingIntent.getBroadcast(context, b.requestCode,
                Intent(context, CountdownWidgetWithTitleProvider::class.java).setAction(b.action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        private fun updateProvider(context: Context, manager: AppWidgetManager, ids: IntArray, showTitle: Boolean) {
            if (ids.isEmpty()) return
            try {
                val prefs = CountdownPrefs(context)
                val now = System.currentTimeMillis()
                val parts = CountdownMath.calculateDateOnly(prefs.eventLocalDate(), now)
                val clock = WidgetClockSchedule.evaluate(prefs.eventLocalDate(), now)
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.countdown_widget)
                    views.setViewVisibility(R.id.widget_title, if (showTitle && prefs.showTitle) View.VISIBLE else View.GONE)
                    views.setTextViewText(R.id.widget_title, prefs.eventTitle)
                    views.setTextViewText(R.id.widget_days_value, parts.days.toString())
                    views.setTextViewText(R.id.widget_target_date, prefs.formattedTargetDate())
                    views.setViewVisibility(R.id.widget_cube_time, if (clock.visible) View.VISIBLE else View.GONE)
                    if (clock.visible) {
                        views.setChronometerCountDown(R.id.widget_time_value, true)
                        views.setChronometer(
                            R.id.widget_time_value,
                            SystemClock.elapsedRealtime() + clock.remainingMillis,
                            clock.chronometerFormat,
                            true
                        )
                    }

                    val personal = prefs.theme == CountdownPrefs.THEME_PERSONAL
                    val widgetBackground = widgetBackgroundBitmap(
                        context, manager.getAppWidgetOptions(id), prefs.backgroundBitmap(), showTitle, personal
                    )
                    widgetBackground?.let { views.setImageViewBitmap(R.id.widget_background, it) }
                    views.setViewVisibility(
                        R.id.widget_background_overlay,
                        if (personal) View.GONE else View.VISIBLE
                    )
                    val auto = personal && prefs.cubeStyle == CountdownPrefs.CUBES_AUTO
                    val autoTextColor = if (auto) prefs.personalAutoContrastTextColor() else Color.WHITE
                    val autoDark = auto && autoTextColor == PersonalTextContrast.AUTO_DARK_TEXT
                    val textColor = when {
                        auto -> autoTextColor
                        personal && prefs.cubeStyle != CountdownPrefs.CUBES_DARK -> 0xFF182033.toInt()
                        prefs.theme == CountdownPrefs.THEME_GOLD -> 0xFFFFE8B2.toInt()
                        prefs.theme == CountdownPrefs.THEME_PREMIUM -> 0xFFFFF0D8.toInt()
                        else -> Color.WHITE
                    }
                    val titleColor = if (personal && widgetBackground != null) {
                        PersonalTextContrast.recommendedTextColorForRegion(
                            widgetBackground, widgetBackground.width.toFloat(), widgetBackground.height.toFloat(),
                            RectF(0f, 0f, widgetBackground.width.toFloat(), widgetBackground.height * 0.24f)
                        )
                    } else textColor
                    val targetDateColor = if (personal && widgetBackground != null) {
                        PersonalTextContrast.recommendedTextColorForRegion(
                            widgetBackground, widgetBackground.width.toFloat(), widgetBackground.height.toFloat(),
                            RectF(0f, widgetBackground.height * 0.76f,
                                widgetBackground.width.toFloat(), widgetBackground.height.toFloat())
                        )
                    } else textColor
                    views.setTextColor(R.id.widget_title, titleColor)
                    views.setTextColor(R.id.widget_target_date, targetDateColor)
                    listOf(R.id.widget_days_value, R.id.widget_time_value, R.id.widget_days_label)
                        .forEach { views.setTextColor(it, textColor) }
                    val drawable = when {
                        personal && prefs.cubeStyle == CountdownPrefs.CUBES_LIGHT -> R.drawable.widget_cube_light
                        personal && prefs.cubeStyle == CountdownPrefs.CUBES_FROSTED -> R.drawable.widget_cube_frosted
                        auto -> if (autoDark) R.drawable.widget_cube_light else R.drawable.widget_cube_premium
                        prefs.theme == CountdownPrefs.THEME_GOLD -> R.drawable.widget_cube_gold
                        prefs.theme == CountdownPrefs.THEME_GRADIENT -> R.drawable.widget_cube_gradient
                        else -> R.drawable.widget_cube_premium
                    }
                    listOf(R.id.widget_cube_days, R.id.widget_cube_time).forEach { views.setInt(it, "setBackgroundResource", drawable) }
                    manager.updateAppWidget(id, views)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update countdown widget", e)
                val fallback = RemoteViews(context.packageName, R.layout.countdown_widget_error)
                ids.forEach { runCatching { manager.updateAppWidget(it, fallback) } }
            }
        }

        private fun widgetBackgroundBitmap(
            context: Context,
            options: Bundle,
            source: Bitmap?,
            showTitle: Boolean,
            personal: Boolean
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
            if (personal) {
                BackgroundEdgeFiller.draw(canvas, source, widthPx.toFloat(), heightPx.toFloat(), paint)
            } else {
                BackgroundEdgeFiller.drawCenterCrop(canvas, source, widthPx.toFloat(), heightPx.toFloat(), paint)
            }
            return output
        }
    }
}
