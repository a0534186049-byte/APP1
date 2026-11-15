package com.neonevent.countdown

import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CountdownWidgetRemoteViewsInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun countdownWidgetLayoutCanBeAppliedByRemoteViewsWithFourSeparateCubes() {
        val remoteViews = RemoteViews(context.packageName, R.layout.countdown_widget)
        val inflated = remoteViews.apply(context, FrameLayout(context))

        assertNotNull(inflated)
        assertNotNull(inflated.findViewById<View>(R.id.widget_background))
        assertNotNull(inflated.findViewById<View>(R.id.widget_background_overlay))

        val cubes = listOf(
            Triple(R.id.widget_cube_days, R.id.widget_days_value, R.id.widget_days_label),
            Triple(R.id.widget_cube_hours, R.id.widget_hours_value, R.id.widget_hours_label),
            Triple(R.id.widget_cube_minutes, R.id.widget_minutes_value, R.id.widget_minutes_label),
            Triple(R.id.widget_cube_seconds, R.id.widget_seconds_value, R.id.widget_seconds_label)
        )
        cubes.forEach { (cubeId, valueId, labelId) ->
            val cube = inflated.findViewById<LinearLayout>(cubeId)
            assertNotNull("Countdown cube $cubeId must exist", cube)
            assertEquals("Each cube must contain a number and its label", 2, cube.childCount)
            assertNotNull(inflated.findViewById<View>(valueId))
            assertNotNull(inflated.findViewById<View>(labelId))
        }
        assertFalse("The merged HH:MM:SS Chronometer must not be present",
            containsChronometer(inflated))
    }

    @Test fun remoteViewsDisplaysDaysHoursMinutesAndSecondsAsSeparateValues() {
        val remoteViews = RemoteViews(context.packageName, R.layout.countdown_widget).apply {
            setTextViewText(R.id.widget_days_value, "30")
            setTextViewText(R.id.widget_hours_value, "05")
            setTextViewText(R.id.widget_minutes_value, "42")
            setTextViewText(R.id.widget_seconds_value, "09")
        }
        val inflated = remoteViews.apply(context, FrameLayout(context))

        assertEquals("30", inflated.findViewById<TextView>(R.id.widget_days_value).text.toString())
        assertEquals("05", inflated.findViewById<TextView>(R.id.widget_hours_value).text.toString())
        assertEquals("42", inflated.findViewById<TextView>(R.id.widget_minutes_value).text.toString())
        assertEquals("09", inflated.findViewById<TextView>(R.id.widget_seconds_value).text.toString())
    }

    @Test fun titleCanBeHiddenForTheNoTitleVariant() {
        val remoteViews = RemoteViews(context.packageName, R.layout.countdown_widget).apply {
            setViewVisibility(R.id.widget_title, View.GONE)
        }
        val inflated = remoteViews.apply(context, FrameLayout(context))
        assertEquals(View.GONE, inflated.findViewById<View>(R.id.widget_title).visibility)
    }

    @Test fun eachCountdownCubeKeepsItsHebrewLabelOnASingleLine() {
        val remoteViews = RemoteViews(context.packageName, R.layout.countdown_widget)
        val inflated = remoteViews.apply(context, FrameLayout(context))
        val labelIds = listOf(
            R.id.widget_days_label,
            R.id.widget_hours_label,
            R.id.widget_minutes_label,
            R.id.widget_seconds_label
        )
        labelIds.forEach { id ->
            val label = inflated.findViewById<TextView>(id)
            assertNotNull(label)
            assertTrue("Label $id must be configured for one line", label.maxLines == 1)
        }
    }

    private fun containsChronometer(view: View): Boolean = when (view) {
        is Chronometer -> true
        is ViewGroup -> (0 until view.childCount).any { containsChronometer(view.getChildAt(it)) }
        else -> false
    }
}
