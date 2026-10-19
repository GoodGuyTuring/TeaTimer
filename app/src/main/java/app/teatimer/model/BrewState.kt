package app.teatimer.model

import kotlinx.serialization.Serializable

/**
 * Where the current brew stands. Pure data and transitions: no Android calls,
 * so it can be unit-tested. Times are `SystemClock.elapsedRealtime()` millis.
 *
 * No sequence selected means the widget is in its starting "choose a tea" state.
 */
@Serializable
data class BrewState(
    val sequenceId: String? = null,
    /** Index of the current infusion (0-based). */
    val step: Int = 0,
    val phase: Phase = Phase.READY,
    /** When the running timer ends (only in [Phase.RUNNING]). */
    val endsAt: Long = 0,
    /** Time left on a paused timer (only in [Phase.PAUSED]). */
    val remainingMs: Long = 0,
) {
    enum class Phase { READY, RUNNING, PAUSED }

    val isChoosing: Boolean get() = sequenceId == null

    fun remainingAt(now: Long, stepMs: Long): Long = when (phase) {
        Phase.READY -> stepMs
        Phase.RUNNING -> (endsAt - now).coerceAtLeast(0)
        Phase.PAUSED -> remainingMs
    }

    /** A single tap: start when ready, pause when running, resume when paused. */
    fun tap(now: Long, stepMs: Long): BrewState = when (phase) {
        Phase.READY -> copy(phase = Phase.RUNNING, endsAt = now + stepMs)
        Phase.RUNNING -> copy(phase = Phase.PAUSED, remainingMs = (endsAt - now).coerceAtLeast(0))
        Phase.PAUSED -> copy(phase = Phase.RUNNING, endsAt = now + remainingMs)
    }

    /**
     * Previous / Next: jump to another infusion, ready to start. Outside the sequence
     * nothing changes, so a stray tap at the first or last infusion can't stop a running timer.
     */
    fun goTo(step: Int, stepCount: Int): BrewState =
        if (step in 0 until stepCount) BrewState(sequenceId = sequenceId, step = step) else this

    /** Back to the start of the current infusion. */
    fun resetInfusion(): BrewState = copy(phase = Phase.READY, endsAt = 0, remainingMs = 0)

    /** The current infusion finished: line up the next one, or go back to choosing after the last. */
    fun finishInfusion(stepCount: Int): BrewState =
        if (step + 1 < stepCount) BrewState(sequenceId = sequenceId, step = step + 1) else BrewState()

    companion object {
        fun select(sequenceId: String) = BrewState(sequenceId = sequenceId)
    }
}
