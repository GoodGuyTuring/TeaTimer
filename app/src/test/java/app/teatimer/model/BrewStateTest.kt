package app.teatimer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewStateTest {
    private val ready = BrewState.select("Oolong/Da Hong Pao/Medium")

    @Test
    fun `selecting a sequence starts at the first infusion, ready`() {
        assertEquals(0, ready.step)
        assertEquals(BrewState.Phase.READY, ready.phase)
        assertEquals(false, ready.isChoosing)
    }

    @Test
    fun `tap starts, pauses and resumes keeping the remaining time`() {
        val running = ready.tap(now = 1_000, stepMs = 15_000)
        assertEquals(BrewState.Phase.RUNNING, running.phase)
        assertEquals(16_000, running.endsAt)

        val paused = running.tap(now = 6_000, stepMs = 15_000)
        assertEquals(BrewState.Phase.PAUSED, paused.phase)
        assertEquals(10_000, paused.remainingMs)

        val resumed = paused.tap(now = 60_000, stepMs = 15_000)
        assertEquals(BrewState.Phase.RUNNING, resumed.phase)
        assertEquals(70_000, resumed.endsAt)
    }

    @Test
    fun `remaining time per phase`() {
        assertEquals(15_000, ready.remainingAt(now = 0, stepMs = 15_000))
        val running = ready.tap(now = 0, stepMs = 15_000)
        assertEquals(5_000, running.remainingAt(now = 10_000, stepMs = 15_000))
        assertEquals(0, running.remainingAt(now = 20_000, stepMs = 15_000))
    }

    @Test
    fun `reset infusion keeps the step and goes back to ready`() {
        val running = ready.copy(step = 3).tap(now = 0, stepMs = 15_000)
        val reset = running.resetInfusion()
        assertEquals(3, reset.step)
        assertEquals(BrewState.Phase.READY, reset.phase)
    }

    @Test
    fun `finishing an infusion lines up the next one`() {
        val next = ready.tap(now = 0, stepMs = 15_000).finishInfusion(stepCount = 3)
        assertEquals(1, next.step)
        assertEquals(BrewState.Phase.READY, next.phase)
        assertEquals(ready.sequenceId, next.sequenceId)
    }

    @Test
    fun `previous and next jump to another infusion, ready to start`() {
        val running = ready.copy(step = 2).tap(now = 0, stepMs = 15_000)
        val next = running.goTo(3, stepCount = 5)
        assertEquals(3, next.step)
        assertEquals(BrewState.Phase.READY, next.phase)
        assertEquals(1, running.goTo(1, stepCount = 5).step)
    }

    @Test
    fun `previous and next do nothing past either end`() {
        val first = ready.tap(now = 0, stepMs = 15_000)
        assertEquals(first, first.goTo(-1, stepCount = 5))
        val last = ready.copy(step = 4)
        assertEquals(last, last.goTo(5, stepCount = 5))
    }

    @Test
    fun `finishing the last infusion goes back to choosing`() {
        val last = ready.copy(step = 2).tap(now = 0, stepMs = 15_000)
        assertTrue(last.finishInfusion(stepCount = 3).isChoosing)
    }
}
