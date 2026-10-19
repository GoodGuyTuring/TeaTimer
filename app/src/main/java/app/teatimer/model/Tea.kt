package app.teatimer.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The tea list file format (assets/teas.json, later also the tea-list repo on GitHub).
 *
 * Steep times are written as "m:ss" (or plain seconds) so the files are easy to edit by hand.
 */
@Serializable
data class TeaFile(
    val version: Int = 1,
    val teas: List<Tea>,
)

@Serializable
data class Tea(
    val type: String,
    val name: String,
    val intensities: List<Intensity>,
)

@Serializable
data class Intensity(
    val name: String,
    val steeps: List<String>,
)

/** One tea at one intensity: the sequence of steep times the timer steps through. */
data class BrewingSequence(
    val type: String,
    val tea: String,
    val intensity: String,
    val steepSeconds: List<Int>,
) {
    val id: String get() = "$type/$tea/$intensity"
    val name: String get() = "$tea · $intensity"
}

/** All sequences from a tea file, grouped type → tea → intensity in file order. */
class TeaLibrary(val sequences: List<BrewingSequence>) {
    private val byId = sequences.associateBy { it.id }

    operator fun get(id: String): BrewingSequence? = byId[id]

    val types: List<String> get() = sequences.map { it.type }.distinct()

    /** Teas of one type, each with its intensities. */
    fun teasOf(type: String): Map<String, List<BrewingSequence>> =
        sequences.filter { it.type == type }.groupBy { it.tea }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): TeaLibrary {
            val file = json.decodeFromString<TeaFile>(text)
            return TeaLibrary(
                file.teas.flatMap { tea ->
                    tea.intensities.map { intensity ->
                        BrewingSequence(
                            type = tea.type,
                            tea = tea.name,
                            intensity = intensity.name,
                            steepSeconds = intensity.steeps.map(::parseSteep),
                        )
                    }
                },
            )
        }
    }
}

/** Parses "m:ss" (e.g. "1:15") or plain seconds (e.g. "45") into seconds. */
fun parseSteep(text: String): Int {
    val parts = text.trim().split(":")
    val seconds = when (parts.size) {
        1 -> parts[0].toInt()
        2 -> {
            val secs = parts[1].toInt()
            require(secs in 0..59) { "Seconds must be 0–59 in \"$text\"" }
            parts[0].toInt() * 60 + secs
        }
        else -> throw IllegalArgumentException("Not a steep time: \"$text\"")
    }
    require(seconds > 0) { "Steep time must be positive: \"$text\"" }
    return seconds
}

/** Formats seconds as m:ss, e.g. 75 -> "1:15". */
fun formatSteep(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** Formats a remaining time in milliseconds as m:ss, rounding up so 0:00 means done. */
fun formatRemaining(millis: Long): String = formatSteep(((millis.coerceAtLeast(0) + 999) / 1000).toInt())
