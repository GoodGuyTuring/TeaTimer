package app.teatimer.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The tea list file format: assets/teas.json, and the same file in the tea list repo
 * (github.com/GoodGuyTuring/TeaTimerTimes), which "Update teas" downloads.
 *
 * Steep times are written as "m:ss" (or plain seconds) so the files are easy to edit by hand.
 */
@Serializable
data class TeaFile(
    val version: Int = 2,
    val updated: String? = null,
    val teas: List<Tea>,
)

@Serializable
data class Tea(
    val type: String,
    val name: String,
    val note: String? = null,
    val methods: List<Method>,
)

/** A way of brewing the tea, e.g. "Gongfu 90 ml" or "Western 1 L". */
@Serializable
data class Method(
    val name: String,
    val amount: String? = null,
    val note: String? = null,
    val infusions: List<Infusion>,
)

/** One infusion: its temperature and the time for each strength. */
@Serializable
data class Infusion(
    val temp: String? = null,
    val light: String? = null,
    val medium: String,
    val strong: String? = null,
    val optional: Boolean = false,
)

enum class Strength(val label: String) {
    LIGHT("Light"),
    MEDIUM("Medium"),
    STRONG("Strong"),
}

data class Step(val seconds: Int, val temp: String?, val optional: Boolean)

/** One tea, method and strength: the sequence of steeps the timer steps through. */
data class BrewingSequence(
    val type: String,
    val tea: String,
    val method: String,
    val strength: Strength,
    val steps: List<Step>,
    val amount: String? = null,
    val note: String? = null,
) {
    val id: String get() = "$type/$tea/$method/${strength.label}"
    val detail: String get() = "$method · ${strength.label}"
    val steepSeconds: List<Int> get() = steps.map { it.seconds }
}

/** All sequences from a tea file, grouped type → tea → method → strength in file order. */
class TeaLibrary(
    val sequences: List<BrewingSequence>,
    val teaCount: Int,
    val updated: String? = null,
) {
    private val byId = sequences.associateBy { it.id }

    operator fun get(id: String): BrewingSequence? = byId[id]

    val types: List<String> get() = sequences.map { it.type }.distinct()

    /** Teas of one type, each with its sequences (all methods and strengths). */
    fun teasOf(type: String): Map<String, List<BrewingSequence>> =
        sequences.filter { it.type == type }.groupBy { it.tea }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Parses and checks a tea file; throws if it isn't a usable tea list. */
        fun parse(text: String): TeaLibrary {
            val file = json.decodeFromString<TeaFile>(text)
            require(file.teas.isNotEmpty()) { "The tea list is empty" }
            val sequences = file.teas.flatMap { tea ->
                tea.methods.flatMap { method -> sequencesOf(tea, method) }
            }
            return TeaLibrary(sequences, teaCount = file.teas.size, updated = file.updated)
        }

        /** Medium always; Light and Strong when every infusion has a time for them. */
        private fun sequencesOf(tea: Tea, method: Method): List<BrewingSequence> {
            if (method.infusions.isEmpty()) return emptyList()
            return Strength.entries.mapNotNull { strength ->
                val times = method.infusions.map { infusion ->
                    when (strength) {
                        Strength.LIGHT -> infusion.light
                        Strength.MEDIUM -> infusion.medium
                        Strength.STRONG -> infusion.strong
                    }
                }
                if (times.any { it == null }) return@mapNotNull null
                BrewingSequence(
                    type = tea.type,
                    tea = tea.name,
                    method = method.name,
                    strength = strength,
                    steps = method.infusions.zip(times) { infusion, time ->
                        Step(parseSteep(time!!), infusion.temp, infusion.optional)
                    },
                    amount = method.amount,
                    note = method.note,
                )
            }
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
