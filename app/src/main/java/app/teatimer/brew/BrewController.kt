package app.teatimer.brew

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import androidx.glance.appwidget.updateAll
import app.teatimer.model.BrewState
import app.teatimer.model.BrewingSequence
import app.teatimer.model.TeaLibrary
import app.teatimer.model.formatSteep
import app.teatimer.widget.TeaTimerWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Owns the brew state and keeps everything else in step with it: the stored state,
 * the alarm, the running notification and the widget. Shared by the app, the widget
 * and the alarm receiver.
 */
class BrewController private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("brew", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var lastTapAt = 0L

    val library: TeaLibrary =
        TeaLibrary.parse(context.assets.open("teas.json").bufferedReader().use { it.readText() })

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<BrewState> = _state.asStateFlow()

    private val _ringSeconds = MutableStateFlow(prefs.getInt(KEY_RING_SECONDS, DEFAULT_RING_SECONDS))
    val ringSeconds: StateFlow<Int> = _ringSeconds.asStateFlow()

    init {
        Notifications.createChannels(context)
    }

    fun sequenceOf(state: BrewState): BrewingSequence? = state.sequenceId?.let { library[it] }

    /** Steep time of the current infusion. */
    fun stepMs(state: BrewState): Long =
        (sequenceOf(state)?.steepSeconds?.getOrNull(state.step) ?: 0) * 1000L

    suspend fun select(sequenceId: String) = update { BrewState.select(sequenceId) }

    /** Start, pause or resume (the app's main button). */
    suspend fun toggle() = update { it.tap(SystemClock.elapsedRealtime(), stepMs(it)) }

    /** Widget tap: like [toggle], but a second tap within [DOUBLE_TAP_MS] resets the infusion. */
    suspend fun tap() = update { current ->
        val now = SystemClock.elapsedRealtime()
        val isDoubleTap = now - lastTapAt < DOUBLE_TAP_MS
        lastTapAt = if (isDoubleTap) 0 else now
        when {
            current.isChoosing -> current
            isDoubleTap -> current.resetInfusion()
            else -> current.tap(now, stepMs(current))
        }
    }

    suspend fun resetInfusion() = update { it.resetInfusion() }

    /** Back to the starting "choose a tea" state. */
    suspend fun stop() = update { BrewState() }

    fun setRingSeconds(seconds: Int) {
        prefs.edit { putInt(KEY_RING_SECONDS, seconds) }
        _ringSeconds.value = seconds
    }

    /**
     * Called when the alarm fires. Moves on to the next infusion and returns what the
     * alert should say, or null if the alarm was stale (timer paused or reset meanwhile).
     */
    suspend fun onTimerFinished(): Alert? {
        var alert: Alert? = null
        update { current ->
            val sequence = sequenceOf(current)
            val now = SystemClock.elapsedRealtime()
            if (sequence == null || current.phase != BrewState.Phase.RUNNING || current.endsAt > now + ALARM_TOLERANCE_MS) {
                return@update current
            }
            val next = current.finishInfusion(sequence.steepSeconds.size)
            val title = "${sequence.name}: infusion ${current.step + 1} done"
            alert = if (next.isChoosing) {
                Alert(title, "That was the last infusion.")
            } else {
                Alert(title, "Next: ${formatSteep(sequence.steepSeconds[next.step])}. Tap the widget to start.")
            }
            next
        }
        return alert
    }

    data class Alert(val title: String, val text: String)

    private suspend fun update(transform: (BrewState) -> BrewState) {
        val changed = mutex.withLock {
            val old = _state.value
            val new = transform(old)
            if (new != old) {
                prefs.edit { putString(KEY_STATE, json.encodeToString(BrewState.serializer(), new)) }
                _state.value = new
                applySideEffects(new)
            }
            new != old
        }
        if (changed) TeaTimerWidget().updateAll(context)
    }

    private fun applySideEffects(state: BrewState) {
        val sequence = sequenceOf(state)
        if (state.phase == BrewState.Phase.RUNNING && sequence != null) {
            AlarmScheduler.schedule(context, state.endsAt)
            Notifications.showRunning(context, sequence, state)
        } else {
            AlarmScheduler.cancel(context)
            Notifications.cancelRunning(context)
        }
    }

    /** Restores the saved state, dropping anything that no longer makes sense. */
    private fun loadState(): BrewState {
        val saved = prefs.getString(KEY_STATE, null)
            ?.let { runCatching { json.decodeFromString(BrewState.serializer(), it) }.getOrNull() }
            ?: return BrewState()
        val sequence = sequenceOf(saved) ?: return BrewState()
        if (saved.step !in sequence.steepSeconds.indices) return BrewState()
        if (saved.phase == BrewState.Phase.RUNNING) {
            // After a reboot the clock restarts and the alarm is gone: start the infusion over.
            val left = saved.endsAt - SystemClock.elapsedRealtime()
            if (left > stepMs(saved) || left < -STALE_TIMER_MS) return saved.resetInfusion()
        }
        return saved
    }

    companion object {
        private const val KEY_STATE = "state"
        private const val KEY_RING_SECONDS = "ring_seconds"
        const val DEFAULT_RING_SECONDS = 5
        val RING_SECONDS_OPTIONS = listOf(3, 5, 10, 15)

        private const val DOUBLE_TAP_MS = 600L
        private const val ALARM_TOLERANCE_MS = 2_000L
        private const val STALE_TIMER_MS = 60_000L

        private val json = Json { ignoreUnknownKeys = true }

        @Volatile
        private var instance: BrewController? = null

        fun get(context: Context): BrewController =
            instance ?: synchronized(this) {
                instance ?: BrewController(context.applicationContext).also { instance = it }
            }
    }
}
