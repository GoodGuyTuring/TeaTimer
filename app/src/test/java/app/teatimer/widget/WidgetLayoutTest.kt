package app.teatimer.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.teatimer.R
import app.teatimer.brew.BrewController
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Renders the widget to real Android views (Robolectric) and checks where the time sits:
 * the running countdown must be in the same place as the time shown before starting.
 */
@OptIn(ExperimentalGlanceApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class WidgetLayoutTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val controller = BrewController.get(context)

    @After
    fun tearDown() = runBlocking { controller.stop() }

    @Test
    fun `running countdown sits where the ready time sits`() = runBlocking {
        controller.select(controller.library.sequences.first().id)
        val ready = render()
        val readyTime = findAll(ready).filterIsInstance<TextView>().single { TIME.matches(it.text) }

        controller.toggle()
        val running = render()
        val countdown = running.findViewById<View>(R.id.countdown)
        assertNotNull("countdown view", countdown)

        assertEquals("vertical centre of the time", centreY(ready, readyTime).toDouble(), centreY(running, countdown).toDouble(), 4.0)
        assertEquals("horizontal centre of the time", centreX(ready, readyTime).toDouble(), centreX(running, countdown).toDouble(), 4.0)

        // The countdown mustn't push the hint line below the widget.
        val hint = findAll(running).filterIsInstance<TextView>().single { it.text.startsWith("Tap to pause") }
        assertTrue("hint inside the widget", offset(running, hint) { it.top } + hint.height <= running.height)
    }

    private suspend fun render(): View {
        val remoteViews = TeaTimerWidget().compose(context, size = SIZE)
        val view = remoteViews.apply(context, FrameLayout(context))
        val density = context.resources.displayMetrics.density
        val width = (SIZE.width.value * density).toInt()
        val height = (SIZE.height.value * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        return view
    }

    private fun findAll(view: View): List<View> =
        listOf(view) + ((view as? ViewGroup)?.let { group -> (0 until group.childCount).flatMap { findAll(group.getChildAt(it)) } } ?: emptyList())

    private fun offset(root: View, view: View, axis: (View) -> Int): Int {
        var total = 0
        var current = view
        while (current !== root) {
            total += axis(current)
            current = current.parent as View
        }
        return total
    }

    private fun centreY(root: View, view: View) = offset(root, view) { it.top } + view.height / 2
    private fun centreX(root: View, view: View) = offset(root, view) { it.left } + view.width / 2

    companion object {
        /** Tall enough that the content fits: Robolectric measures every text line about 41 px high. */
        private val SIZE = DpSize(260.dp, 480.dp)
        private val TIME = Regex("""\d+:\d{2}""")
    }
}
