package app.teatimer.model

/**
 * How long the alarm rings when an infusion is done. The setting is either [AUTO]
 * (by the infusion's steep time) or a fixed number of seconds.
 */
object RingDuration {
    /** Setting: ring length follows the steep time ([forSteep]). */
    const val AUTO = 0

    /** Ring length: until the alarm is stopped (notification, widget or app). */
    const val UNTIL_STOPPED = -1

    /** The choices in the app and, in this order, on the widget's bell chip. */
    val OPTIONS = listOf(AUTO, 3, 5, 10, 15)

    /** Auto: up to 0:30 → 5 s, up to 1:00 → 10 s, up to 2:00 → 15 s, longer → until stopped. */
    fun forSteep(steepSeconds: Int): Int = when {
        steepSeconds <= 30 -> 5
        steepSeconds <= 60 -> 10
        steepSeconds <= 120 -> 15
        else -> UNTIL_STOPPED
    }

    /** Ring length in seconds (or [UNTIL_STOPPED]) for a finished infusion of [steepSeconds]. */
    fun resolve(setting: Int, steepSeconds: Int): Int = if (setting == AUTO) forSteep(steepSeconds) else setting

    /** Widget bell chip: the next option, wrapping around. */
    fun next(setting: Int): Int = OPTIONS[(OPTIONS.indexOf(setting) + 1) % OPTIONS.size]

    fun label(setting: Int): String = if (setting == AUTO) "Auto" else "$setting s"
}
