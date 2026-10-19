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
import app.teatimer.model.WidgetBackground
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
    fun tearDown() = runBlocking {
        controller.stop()
        controller.setWidgetBackground(WidgetBackground.WALLPAPER)
    }

    @Test
    fun `bottom row has previous, next and the ring duration`() = runBlocking {
        controller.select(controller.library.sequences.first().id)
        val texts = findAll(render()).filterIsInstance<TextView>().map { it.text.toString().trim() }
        assertTrue(texts.toString(), "Prev" in texts && "Next" in texts && "${controller.ringSeconds.value} s" in texts)
    }

    @Test
    fun `the time grows with the widget, the same size ready and running`() = runBlocking {
        controller.select(controller.library.sequences.first().id)
        fun readyTime(view: View) = findAll(view).filterIsInstance<TextView>().single { TIME.matches(it.text) }
        val small = readyTime(render(DpSize(260.dp, 480.dp))).textSize
        val large = readyTime(render(DpSize(400.dp, 480.dp))).textSize
        assertTrue("ready time $small px at 260 dp wide, $large px at 400 dp", large > small * 1.3f)

        controller.toggle()
        val running = render(DpSize(400.dp, 480.dp)).findViewById<TextView>(R.id.countdown).textSize
        assertEquals("running countdown size", large.toDouble(), running.toDouble(), 1.0)
    }

    @Test
    fun `short widget leaves out the hint line`() = runBlocking {
        controller.select(controller.library.sequences.first().id)
        val texts = findAll(render(DpSize(260.dp, 150.dp))).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(texts.toString(), texts.none { it.startsWith("Tap to") })
    }

    @Test
    fun `chosen background colours the running countdown too`() = runBlocking {
        controller.setWidgetBackground(WidgetBackground.TEA_GREEN)
        controller.select(controller.library.sequences.first().id)
        controller.toggle()
        val countdown = render().findViewById<TextView>(R.id.countdown)
        assertEquals(WidgetBackground.TEA_GREEN.text!!.toInt(), countdown.currentTextColor)
    }

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

    private suspend fun render(size: DpSize = SIZE): View {
        val remoteViews = TeaTimerWidget().compose(context, size = size)
        val view = remoteViews.apply(context, FrameLayout(context))
        val density = context.resources.displayMetrics.density
        val width = (size.width.value * density).toInt()
        val height = (size.height.value * density).toInt()
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
