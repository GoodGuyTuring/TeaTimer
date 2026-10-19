package app.teatimer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class TeaTest {
    @Test
    fun `parses steep times`() {
        assertEquals(15, parseSteep("0:15"))
        assertEquals(75, parseSteep("1:15"))
        assertEquals(420, parseSteep("7:00"))
        assertEquals(45, parseSteep("45"))
        assertEquals(30, parseSteep(" 0:30 "))
    }

    @Test
    fun `rejects bad steep times`() {
        assertThrows(IllegalArgumentException::class.java) { parseSteep("0:75") }
        assertThrows(IllegalArgumentException::class.java) { parseSteep("0:00") }
        assertThrows(IllegalArgumentException::class.java) { parseSteep("1:2:3") }
        assertThrows(NumberFormatException::class.java) { parseSteep("abc") }
    }

    @Test
    fun `formats times`() {
        assertEquals("0:15", formatSteep(15))
        assertEquals("7:00", formatSteep(420))
        assertEquals("0:01", formatRemaining(1))
        assertEquals("0:00", formatRemaining(-500))
        assertEquals("1:00", formatRemaining(60_000))
    }

    private val sample = """
        {"version": 2, "updated": "Oct 17, 2026", "teas": [
          {"type": "Oolong", "name": "Da Hong Pao", "methods": [
            {"name": "Gongfu 90 ml", "amount": "2.25 tsp", "infusions": [
              {"temp": "100°C", "light": "0:10", "medium": "0:15", "strong": "0:20"},
              {"temp": "95°C", "light": "3:00", "medium": "4:00", "strong": "4:30", "optional": true}
            ]},
            {"name": "Western 1 L", "infusions": [
              {"temp": "95°C", "medium": "4:00"}
            ]}
          ]},
          {"type": "Green", "name": "Long Jing", "methods": [
            {"name": "Gongfu 90 ml", "infusions": [{"medium": "0:30"}]}
          ]}
        ]}
    """

    @Test
    fun `groups a tea file by type, tea, method and strength`() {
        val library = TeaLibrary.parse(sample)
        assertEquals(listOf("Oolong", "Green"), library.types)
        assertEquals(2, library.teaCount)
        assertEquals("Oct 17, 2026", library.updated)
        assertEquals(
            listOf("Gongfu 90 ml · Light", "Gongfu 90 ml · Medium", "Gongfu 90 ml · Strong", "Western 1 L · Medium"),
            library.teasOf("Oolong")["Da Hong Pao"]!!.map { it.detail },
        )
    }

    @Test
    fun `sequence steps carry time, temperature and optional`() {
        val strong = TeaLibrary.parse(sample)["Oolong/Da Hong Pao/Gongfu 90 ml/Strong"]!!
        assertEquals(listOf(Step(20, "100°C", false), Step(270, "95°C", true)), strong.steps)
        assertEquals("2.25 tsp", strong.amount)
    }

    @Test
    fun `rejects an empty tea list`() {
        assertThrows(IllegalArgumentException::class.java) { TeaLibrary.parse("""{"teas": []}""") }
    }

    @Test
    fun `bundled tea list is valid`() {
        val library = TeaLibrary.parse(File("src/main/assets/teas.json").readText())
        assertEquals(true, library.sequences.isNotEmpty())
    }
}
