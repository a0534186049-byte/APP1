package com.neonevent.countdown

import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CountdownWidgetRemoteViewsInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun countdownWidgetLayoutCanBeAppliedByRemoteViews() {
        val remoteViews = RemoteViews(context.packageName, R.layout.countdown_widget)
        val inflated = remoteViews.apply(context, FrameLayout(context))

        assertNotNull(inflated)
        assertNotNull(inflated.findViewById<View>(R.id.widget_background))
        assertNotNull(inflated.findViewById<View>(R.id.widget_days_value))
        assertNotNull(inflated.findViewById<View>(R.id.widget_time_value))
        assertNotNull(inflated.findViewById<View>(R.id.widget_time_label))
    }

    @Test fun attachedRemoteViewsChronometerUsesFixedHourFormatsWhileCountingDown() {
        val observed = mutableListOf<String>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = FrameLayout(activity)
                activity.setContentView(host)
                val remoteViews = RemoteViews(context.packageName, R.layout.countdown_widget).apply {
                    setChronometer(
                        R.id.widget_time_value,
                        SystemClock.elapsedRealtime() + 12L * 60L * 60L * 1000L + 42L * 60L * 1000L + 19_000L,
                        "%s",
                        true
                    )
                    setChronometerCountDown(R.id.widget_time_value, true)
                }
                val rendered = remoteViews.apply(activity, host)
                host.addView(rendered, FrameLayout.LayoutParams(-1, -1))
                val chronometer = rendered.findViewById<Chronometer>(R.id.widget_time_value)

                fun capture(remainingMillis: Long, format: String): String {
                    chronometer.stop()
                    chronometer.setCountDown(true)
                    chronometer.setBase(SystemClock.elapsedRealtime() + remainingMillis)
                    chronometer.setFormat(format)
                    chronometer.start()
                    return chronometer.text.toString()
                }

                observed += capture(
                    12L * 60L * 60L * 1000L + 42L * 60L * 1000L + 19_000L,
                    "%s"
                )
                observed += capture(
                    9L * 60L * 60L * 1000L + 42L * 60L * 1000L + 19_000L,
                    "0%s"
                )
                observed += capture(
                    1L * 60L * 60L * 1000L + 42L * 60L * 1000L + 19_000L,
                    "0%s"
                )
                observed += capture(42L * 60L * 1000L + 19_000L, "00:%s")
                chronometer.stop()
            }
        }
        assertEquals(4, observed.size)
        assertTrue(observed[0].matches(Regex("^12:42:1[0-9]$")))
        assertTrue(observed[1].matches(Regex("^09:42:1[0-9]$")))
        assertTrue(observed[2].matches(Regex("^01:42:1[0-9]$")))
        assertTrue(observed[3].matches(Regex("^00:42:1[0-9]$")))
    }

    @Test fun titleCanBeHiddenForTheNoTitleVariant() {
        val remoteViews = RemoteViews(context.packageName, R.layout.countdown_widget).apply {
            setViewVisibility(R.id.widget_title, View.GONE)
        }
        val inflated = remoteViews.apply(context, FrameLayout(context))
        assertEquals(View.GONE, inflated.findViewById<View>(R.id.widget_title).visibility)
    }
}
