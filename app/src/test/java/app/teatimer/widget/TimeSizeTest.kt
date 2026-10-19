package app.teatimer.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSizeTest {
    @Test
    fun `a tall widget sizes the time by its width, leaving a small gap`() {
        val size = timeSizeDp(widthDp = 350f, heightDp = 600f, showHint = true)
        // 350 dp wide, minus 12 dp padding and 8 dp gap each side, over 2.8 font sizes for "00:25".
        assertEquals((350f - 40f) / 2.8f, size, 0.01f)
    }

    @Test
    fun `a short, wide widget is limited by the room between the lines`() {
        val wide = timeSizeDp(widthDp = 500f, heightDp = 170f, showHint = false)
        assertTrue("height-limited: $wide", wide < (500f - 40f) / 2.8f)
        assertTrue("still readable: $wide", wide >= 28f)
    }

    @Test
    fun `wider widget, bigger time`() {
        assertTrue(timeSizeDp(400f, 600f, true) > timeSizeDp(260f, 600f, true))
    }
}
