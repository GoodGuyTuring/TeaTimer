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

    @Test
    fun `groups a tea file by type, tea and intensity`() {
        val library = TeaLibrary.parse(
            """
            {"teas": [
              {"type": "Oolong", "name": "Da Hong Pao", "intensities": [
                {"name": "Light", "steeps": ["0:20", "0:30"]},
                {"name": "Strong", "steeps": ["0:15"]}
              ]},
              {"type": "Green", "name": "Long Jing", "intensities": [
                {"name": "Medium", "steeps": ["0:30"]}
              ]}
            ]}
            """,
        )
        assertEquals(listOf("Oolong", "Green"), library.types)
        assertEquals(listOf("Light", "Strong"), library.teasOf("Oolong")["Da Hong Pao"]!!.map { it.intensity })
        assertEquals(listOf(20, 30), library["Oolong/Da Hong Pao/Light"]!!.steepSeconds)
    }

    @Test
    fun `bundled tea list is valid`() {
        val library = TeaLibrary.parse(File("src/main/assets/teas.json").readText())
        assertEquals(true, library.sequences.isNotEmpty())
    }
}
