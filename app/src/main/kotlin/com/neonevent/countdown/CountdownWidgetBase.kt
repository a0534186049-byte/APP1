package com.neonevent.countdown

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import java.util.Locale

abstract class CountdownWidgetBase : AppWidgetProvider() {
    protected abstract fun displaysTitle(): Boolean

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_TICK) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
            updateProvider(context, manager, ids, displaysTitle())
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateProvider(context, manager, ids, displaysTitle())
        scheduleTicks(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleTicks(context)
    }

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
                System.currentTimeMillis() + 60_000L,
                60_000L,
                pending
            )
        }
    }

    companion object {
        const val ACTION_WIDGET_TICK = "com.neonevent.countdown.WIDGET_TICK"
        private const val REQUEST_WITH_TITLE = 9911
        private const val REQUEST_NO_TITLE = 9912

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
            val prefs = CountdownPrefs(context)
            val now = System.currentTimeMillis()
            val parts = CountdownMath.calculate(prefs.targetMillis(now), now)
            val views = RemoteViews(context.packageName, R.layout.countdown_widget)
            views.setViewVisibility(R.id.widget_title, if (showTitle && prefs.showTitle) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextViewText(R.id.widget_title, prefs.eventTitle)
            views.setTextViewText(R.id.widget_days_value, parts.days.toString())
            views.setTextViewText(R.id.widget_hours_value, String.format(Locale.ROOT, "%02d", parts.hours))
            views.setTextViewText(R.id.widget_minutes_value, String.format(Locale.ROOT, "%02d", parts.minutes))
            views.setTextViewText(R.id.widget_seconds_value, String.format(Locale.ROOT, "%02d", parts.seconds))
            views.setTextViewText(R.id.widget_days_label, "ימים")
            views.setTextViewText(R.id.widget_hours_label, "שעות")
            views.setTextViewText(R.id.widget_minutes_label, "דקות")
            views.setTextViewText(R.id.widget_seconds_label, "שניות")
            views.setTextViewText(R.id.widget_target_date, prefs.formattedTargetDateWithTime())
            prefs.backgroundBitmap()?.let { views.setImageViewBitmap(R.id.widget_background, it) }

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
            listOf(R.id.widget_cube_days, R.id.widget_cube_hours, R.id.widget_cube_minutes, R.id.widget_cube_seconds)
                .forEach { views.setInt(it, "setBackgroundResource", cubeDrawable) }

            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }
    }
}
