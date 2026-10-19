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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Owns the brew state and keeps everything else in step with it: the stored state,
 * the alarm, the running notification and the widget. Shared by the app, the widget
 * and the alarm receiver. Also owns the tea list and its updates.
 */
class BrewController private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("brew", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var lastTapAt = 0L
    private val downloadedTeas = File(context.filesDir, "teas.json")

    private val _library = MutableStateFlow(loadLibrary())
    val libraryFlow: StateFlow<TeaLibrary> = _library.asStateFlow()
    val library: TeaLibrary get() = _library.value

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
     * Downloads the tea list from the tea list repo and keeps it for next time.
     * A brew whose sequence no longer exists goes back to choosing.
     */
    suspend fun updateTeas(): Result<TeaLibrary> = runCatching {
        val text = withContext(Dispatchers.IO) { download(TEAS_URL) }
        val library = TeaLibrary.parse(text)
        withContext(Dispatchers.IO) {
            val partial = File(context.filesDir, "teas.json.part")
            partial.writeText(text)
            check(partial.renameTo(downloadedTeas)) { "Couldn't save the tea list" }
        }
        _library.value = library
        update { current ->
            val sequence = sequenceOf(current)
            if (!current.isChoosing && (sequence == null || current.step !in sequence.steps.indices)) BrewState() else current
        }
        TeaTimerWidget().updateAll(context)
        library
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
            val next = current.finishInfusion(sequence.steps.size)
            val title = "${sequence.tea}: infusion ${current.step + 1} done"
            alert = if (next.isChoosing) {
                Alert(title, "That was the last infusion.")
            } else {
                val step = sequence.steps[next.step]
                val temp = step.temp?.let { " at $it" }.orEmpty()
                val optional = if (step.optional) " (optional)" else ""
                Alert(title, "Next: ${formatSteep(step.seconds)}$temp$optional. Tap the widget to start.")
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

    /** The downloaded tea list if there is a valid one, else the list bundled with the app. */
    private fun loadLibrary(): TeaLibrary =
        downloadedTeas.takeIf { it.exists() }
            ?.let { runCatching { TeaLibrary.parse(it.readText()) }.getOrNull() }
            ?: TeaLibrary.parse(context.assets.open("teas.json").bufferedReader().use { it.readText() })

    /** Restores the saved state, dropping anything that no longer makes sense. */
    private fun loadState(): BrewState {
        val saved = prefs.getString(KEY_STATE, null)
            ?.let { runCatching { json.decodeFromString(BrewState.serializer(), it) }.getOrNull() }
            ?: return BrewState()
        val sequence = sequenceOf(saved) ?: return BrewState()
        if (saved.step !in sequence.steps.indices) return BrewState()
        if (saved.phase == BrewState.Phase.RUNNING) {
            // After a reboot the clock restarts and the alarm is gone: start the infusion over.
            val left = saved.endsAt - SystemClock.elapsedRealtime()
            if (left > stepMs(saved) || left < -STALE_TIMER_MS) return saved.resetInfusion()
        }
        return saved
    }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Cache-Control", "no-cache")
            val code = connection.responseCode
            check(code == HttpURLConnection.HTTP_OK) { "Server answered HTTP $code" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** The tea list repo's teas.json (github.com/GoodGuyTuring/TeaTimerTimes). */
        const val TEAS_URL = "https://raw.githubusercontent.com/GoodGuyTuring/TeaTimerTimes/main/teas.json"

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
