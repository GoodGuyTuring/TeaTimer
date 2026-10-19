package app.teatimer.model

/**
 * One gongfu brewing sequence: a tea at a given intensity, with the steep time
 * of every infusion in order.
 */
data class BrewingSequence(
    val tea: String,
    val intensity: String,
    val steepSeconds: List<Int>,
) {
    val name: String get() = "$tea · $intensity"
}

/** Formats a steep time as m:ss, e.g. 75 -> "1:15". */
fun formatSteep(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** Placeholder data until the real list is imported. */
object SampleSequences {
    val all = listOf(
        BrewingSequence(
            tea = "Example Oolong",
            intensity = "Medium",
            steepSeconds = listOf(15, 20, 25, 35, 45, 60, 90, 120, 180, 300, 420),
        ),
    )
}
