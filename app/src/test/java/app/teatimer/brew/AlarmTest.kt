package app.teatimer.brew

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import app.teatimer.R
import app.teatimer.model.BrewingSequence
import app.teatimer.model.RingDuration
import app.teatimer.widget.TeaTimerWidgetReceiver
import app.teatimer.widget.redrawWidgetsNow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** What happens when an infusion's time is up: ring length, and the widget redrawn at once. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AlarmTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val controller = BrewController.get(context)

    @Before
    fun setUp() {
        // Glance updates real widgets through WorkManager, which Robolectric doesn't start.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @After
    fun tearDown() = runBlocking {
        controller.stop()
        controller.setRingSetting(RingDuration.AUTO)
    }

    /** Starts [sequence]'s first infusion and lets its time run out. */
    private suspend fun finishFirstInfusion(sequence: BrewingSequence): BrewController.Alert? {
        controller.select(sequence.id)
        controller.toggle()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(sequence.steps[0].seconds.toLong()))
        return controller.onTimerFinished()
    }

    private fun sequenceStartingWith(range: IntRange) =
        controller.library.sequences.first { it.steps.size > 1 && it.steps[0].seconds in range }

    @Test
    fun `auto rings 5 s after a short gongfu steep`() = runBlocking {
        val alert = finishFirstInfusion(sequenceStartingWith(1..30))
        assertEquals(5, alert?.ringSeconds)
    }

    @Test
    fun `auto rings until stopped after a long western steep`() = runBlocking {
        val alert = finishFirstInfusion(sequenceStartingWith(121..600))
        assertEquals(RingDuration.UNTIL_STOPPED, alert?.ringSeconds)
    }

    @Test
    fun `a fixed ring setting wins over auto`() = runBlocking {
        controller.setRingSetting(3)
        val alert = finishFirstInfusion(sequenceStartingWith(121..600))
        assertEquals(3, alert?.ringSeconds)
    }

    @Test
    fun `the widget is redrawn right away when the time is up`() = runBlocking {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        // A widget on the simulated home screen, with the provider info a real launcher has.
        val shadowManager = shadowOf(appWidgetManager)
        shadowManager.setAlwaysRecreateViewsDuringUpdate(true)
        val widgetId = shadowManager.createWidget(
            TeaTimerWidgetReceiver::class.java,
            androidx.glance.appwidget.R.layout.glance_default_loading_layout,
        )
        @Suppress("DEPRECATION") // The test-only way to give the widget its provider info.
        shadowManager.putWidgetInfo(
            widgetId,
            AppWidgetProviderInfo().apply { provider = ComponentName(context, TeaTimerWidgetReceiver::class.java) },
        )

        val sequence = sequenceStartingWith(1..30)
        controller.select(sequence.id)
        controller.toggle()
        redrawWidgetsNow(context)
        assertNotNull("countdown while running", shadowOf(appWidgetManager).getViewFor(widgetId).findViewById<View>(R.id.countdown))

        ShadowSystemClock.advanceBy(Duration.ofSeconds(sequence.steps[0].seconds.toLong()))
        assertNotNull(controller.onTimerFinished())
        redrawWidgetsNow(context)

        val view = shadowOf(appWidgetManager).getViewFor(widgetId)
        assertNull("no running countdown left", view.findViewById<View>(R.id.countdown))
        val texts = allTexts(view)
        assertTrue(texts.toString(), "Infusion 2 of ${sequence.steps.size}" in texts.joinToString(" "))
    }

    private fun allTexts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { allTexts(view.getChildAt(it)) }
        else -> emptyList()
    }
}
