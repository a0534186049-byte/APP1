package com.neonevent.countdown

import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        assertNotNull(inflated.findViewById<View>(R.id.widget_seconds_label))
    }

    @Test fun titleCanBeHiddenForTheNoTitleVariant() {
        val remoteViews = RemoteViews(context.packageName, R.layout.countdown_widget).apply {
            setViewVisibility(R.id.widget_title, View.GONE)
        }
        val inflated = remoteViews.apply(context, FrameLayout(context))
        assertEquals(View.GONE, inflated.findViewById<View>(R.id.widget_title).visibility)
    }
}
