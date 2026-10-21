package app.teatimer.model

import app.teatimer.model.RingDuration.AUTO
import app.teatimer.model.RingDuration.UNTIL_STOPPED
import org.junit.Assert.assertEquals
import org.junit.Test

class RingDurationTest {
    @Test
    fun `auto rings longer for longer steeps`() {
        assertEquals(5, RingDuration.forSteep(10))
        assertEquals(5, RingDuration.forSteep(30))
        assertEquals(10, RingDuration.forSteep(31))
        assertEquals(10, RingDuration.forSteep(60))
        assertEquals(15, RingDuration.forSteep(61))
        assertEquals(15, RingDuration.forSteep(120))
        assertEquals(UNTIL_STOPPED, RingDuration.forSteep(121))
        assertEquals(UNTIL_STOPPED, RingDuration.forSteep(420))
    }

    @Test
    fun `a fixed setting ignores the steep time`() {
        assertEquals(3, RingDuration.resolve(3, steepSeconds = 420))
        assertEquals(15, RingDuration.resolve(15, steepSeconds = 10))
        assertEquals(UNTIL_STOPPED, RingDuration.resolve(AUTO, steepSeconds = 300))
    }

    @Test
    fun `widget chip cycles auto, 3, 5, 10, 15 and back`() {
        assertEquals(3, RingDuration.next(AUTO))
        assertEquals(15, RingDuration.next(10))
        assertEquals(AUTO, RingDuration.next(15))
        assertEquals(AUTO, RingDuration.next(7))
    }

    @Test
    fun labels() {
        assertEquals("Auto", RingDuration.label(AUTO))
        assertEquals("10 s", RingDuration.label(10))
    }
}
